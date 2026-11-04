# 08. 트랜잭션별 EntityManager 세션·영속성 컨텍스트 설계

작성일: 2026-11-03

> 객체 동일성과 세션 수명을 처음 구현할 당시의 설계 기록이다. 이후 추가된 변경 감지·flush를 포함한 최신 계약은 [영속성 컨텍스트 문서](../../05-persistence-context/02-implementation-checkpoints.md)를 참고한다.

2026-11-03. 구현을 위한 설계 초안이다. Stage 5의 첫 구현은 객체 동일성과 세션 수명 관리까지 다룬다. 사용자 선택에 따라 실제 매니저가 영속성 컨텍스트를 소유하고, 트랜잭션 관리자가 매니저를 연결·재사용하도록 구성한다.

## 목적과 현재 상태

같은 트랜잭션에 참여하는 여러 서비스가 같은 엔티티를 조회하면 하나의 관리 객체를 사용하게 한다. 객체를 변경한 내용을 다른 서비스에서도 같은 참조로 읽고, 반복 ID 조회의 SELECT를 줄이는 것이 첫 목표다. 이후 관리 객체에 스냅샷과 flush를 추가할 수 있게 수명 경계를 마련한다.

현재 `EntityManager`는 `JdbcTemplate`과 클래스별 `metadataCache`를 보관하며, 조회마다 SELECT 후 새 객체를 만든다. `TransactionManager`는 스레드별 `TransactionState`에 Connection과 rollbackCause를 보관한다. REQUIRED 호출은 기존 상태에 참여하고 가장 바깥 호출의 finally에서 상태를 제거한다.

기존 `new EntityManager(jdbc)`와 `persist`·`find`·`update`·`remove` 진입점을 유지한다. 기본 작업 폴더에 사용자가 추가한 `returnsSameInstanceWithinTransaction` 테스트도 이 진입점을 사용한다.

## 검토한 구조와 선택

| 구조 | 특징 | 결정 |
|---|---|---|
| `TransactionState`에 엔티티 Map 직접 저장 | 연결 수명과 구현을 바로 묶을 수 있으나 트랜잭션 계층이 ORM 상태를 직접 관리 | 앞선 제안에서 변경 |
| 트랜잭션별 실제 매니저가 컨텍스트 소유, 트랜잭션에는 매니저 holder 연결 | ORM 상태의 소유자와 트랜잭션 자원 연결 역할을 구분하고 같은 매니저를 재사용 | 사용자가 선택한 방향 |
| 호출자가 실제 매니저 생성·종료를 직접 담당 | 명시적 세션 수명을 지원하지만 기존 호출부마다 관리 코드가 필요 | 후속 확장 가능성으로 남김 |

공유 중인 기존 `EntityManager`에 하나의 엔티티 Map을 추가하면 다른 트랜잭션·스레드의 객체가 섞인다. 따라서 기존 클래스는 공유 가능한 위임 진입점으로 유지하고, 내부의 `EntityManagerSession`을 실제 트랜잭션별 매니저로 둔다. 이 명명과 호환성 방식은 이번 설계에서 제안하는 구현 선택이다.

## 소유 관계

```text
서비스 A / 서비스 B
  → EntityManager                      공유 위임 진입점
      → JdbcTemplate의 TransactionManager 확인
      → 현재 트랜잭션 자원에서 같은 holder 획득
          → EntityManagerHolder
              → EntityManagerSession   트랜잭션별 실제 매니저
                  → PersistenceContext
                      → Map<EntityKey, Object>

TransactionManager
  → ThreadLocal<TransactionState>
      → Connection
      → rollbackCause
      → Map<Object, TransactionResource>  holder를 연결하는 범용 저장소
```

트랜잭션 계층은 `com.jorm.entity`를 참조하지 않는다. `EntityManagerHolder`가 범용 자원 종료 계약을 구현하고 세션 종료를 위임한다. 컨텍스트에는 Connection·ThreadLocal을 두지 않는다.

## 클래스별 책임과 접점

| 파일 | 책임과 설계할 인터페이스 |
|---|---|
| `transaction/TransactionResource.java` | `void afterCompletion()`으로 메모리 자원 정리 계약 정의. 정상·실패 모두 호출하며 checked 예외는 선언하지 않음 |
| `transaction/TransactionManager.java` | 트랜잭션 자원 연결·재사용·종료. `<R extends TransactionResource> R getOrCreateResource(Object key, Supplier<R> factory)`와 `void markRollbackOnly(Throwable cause)` 추가 |
| `jdbc/JdbcTemplate.java` | 기존 실행·바인딩 유지. 같은 관리자를 참조하도록 `TransactionManager getTransactionManager()` 제공 |
| `entity/EntityManager.java` | 기존 public 생성자·CRUD 메서드를 유지하는 위임 진입점. 클래스별 메타데이터 캐시와 세션 획득 담당 |
| `entity/EntityManagerHolder.java` | package-private. 세션 하나를 감싸며 `TransactionResource` 구현. `getSession()` 제공, 종료 콜백에서 세션 close |
| `entity/EntityManagerSession.java` | package-private. 실제 CRUD·컨텍스트 접근 담당. `JdbcTemplate`, 메타데이터 조회 함수, 자체 `PersistenceContext` 보관 |
| `entity/PersistenceContext.java` | package-private. 키별 객체 조회·등록·제거와 전체 정리. SQL·트랜잭션 시작을 수행하지 않음 |
| `entity/EntityKey.java` | package-private record. `Class<?> entityType`, `Object id`로 구성 |

`getOrCreateResource`는 활성 트랜잭션이 없으면 factory를 실행하지 않고 null을 반환한다. 활성 상태에서는 같은 키의 기존 자원을 반환하고, 없으면 factory가 성공한 뒤 등록한다. 키와 factory는 null을 거부한다. 하나의 키는 항상 같은 자원 타입에 대응하도록 호출자가 관리한다.

`markRollbackOnly`는 cause가 null이면 거부하고, 활성 트랜잭션의 최초 rollbackCause를 보존한다. 활성 트랜잭션이 없으면 상태를 만들지 않는다. 이 메서드는 이미 자동 커밋으로 확정된 작업을 취소하지 않는다.

TransactionState에는 ACTIVE·COMPLETING 단계를 둔다. 업무 콜백 종료 후 commit·rollback을 시도하기 전에 COMPLETING으로 전환한다. COMPLETING 상태에서 `execute`, `withConnection`, `getOrCreateResource`, `markRollbackOnly`를 다시 호출하면 IllegalStateException으로 거부한다. afterCompletion은 메모리 정리만 수행하며 추가 자원을 등록하거나 종료된 트랜잭션으로 SQL을 실행할 수 없다. 기존 rollbackOnly 판정은 ACTIVE 단계의 업무 종료 때 수행하고, 그 뒤 종료 단계로 전환한다. 업무 실패로 catch에 진입하는 경우도 rollback 전에 종료 단계로 전환한다.

엔티티 계층은 private static final 자원 키 하나를 사용한다. 저장소 자체가 `TransactionManager`의 트랜잭션별 상태에 있으므로, 같은 관리자를 사용하는 여러 `EntityManager`·`JdbcTemplate`은 한 세션을 공유한다. 다른 관리자는 같은 스레드에서도 독립적이다. 현재 프로젝트는 관리자 하나가 DataSource 하나를 소유한다. 여러 영속성 단위를 지원할 때에는 키를 factory·영속성 단위별로 분리한다.

세션은 최초 접근한 위임 매니저의 `JdbcTemplate`과 메타데이터 조회 함수를 사용한다. 같은 관리자에 연결된 템플릿은 같은 DB·Connection을 사용한다. 메타데이터는 읽기 전용이며 기존 ConcurrentHashMap을 재사용한다.

세션은 `AutoCloseable`의 `void close()`를 구현한다. close는 컨텍스트를 비우고 종료 표시를 남긴다. 반복 close는 허용하고, 종료한 세션의 CRUD 호출은 `IllegalStateException`으로 거부한다. public 위임 매니저는 특정 세션을 필드에 보관하지 않는다.

## 생성·재사용·종료 흐름

1. 가장 바깥 `execute`가 Connection과 `TransactionState`를 만든다. 세션은 엔티티 작업의 첫 호출까지 생성하지 않는다.
2. `EntityManager`가 현재 트랜잭션 자원에서 holder를 조회한다. 첫 호출이면 세션을 생성해 holder에 넣고 연결한다.
3. 다른 서비스나 다른 위임 매니저의 호출도 같은 관리자·트랜잭션이면 같은 holder를 받는다.
4. 안쪽 REQUIRED 종료는 세션을 닫지 않는다. 바깥 트랜잭션은 같은 세션으로 작업을 계속한다.
5. 바깥 commit 또는 rollback 시도 이후 finally에서 모든 자원에 `afterCompletion()`을 호출한다. 세션이 컨텍스트를 비운다.
6. 정리 콜백이 실패해도 나머지 자원 정리, `current.remove()`, autoCommit 복원, Connection 종료를 진행한다.

현재 트랜잭션 종료의 원래 실패를 우선 보존한다. 콜백 정리 실패는 원래 실패에 suppressed로 붙이고, 원래 실패가 없으면 첫 정리 실패를 전달한다. 추가 정리 실패는 첫 정리 실패에 붙인다. 정리 실패가 이미 성공한 commit을 되돌릴 수는 없으므로 catch를 통한 rollback 경로로 다시 들어가지 않게 배치한다. rollback 자체가 실패해도 메모리 자원을 정리하며 기존 safeToReset 정책을 유지한다.

트랜잭션 밖에서는 메서드 호출별 임시 세션을 만들고 finally에서 닫는다. Connection은 기존 `JdbcTemplate`이 확보·반환한다. 밖에서 두 번 조회하면 서로 다른 객체가 반환되며, 기존 다중 스레드의 독립 INSERT 사용도 계속 동작한다.

## find와 키 정책

기존 null ID·매핑·기본 생성자 검증 순서를 유지한다. 캐시 적중에서도 조회 불가능한 타입을 허용하지 않는다.

첫 구현의 객체 동일성 보장 대상은 ID 선언 타입이 Long·Integer·String·UUID인 엔티티와 원시 타입 long·int인 엔티티다. 이 타입들은 현재 생성 ID와 대표적인 직접 지정 ID를 포함하며 키 값이 불변이다. byte[]·Date 등 다른 직접 지정 ID 타입은 기존 JDBC CRUD를 계속 지원하되 컨텍스트 등록·조회·ID 변경 추적을 건너뛴다. 해당 엔티티에는 참조 동일성이나 SELECT 생략을 보장하지 않는다. 이 범위 제한은 이번 설계의 선택이며 문서와 테스트에 명시한다. 지원을 확장할 때에는 배열의 값 동등성·가변 ID의 방어적 복사·JDBC 타입 정규화를 함께 설계한다.

```text
타입·ID·생성자 검증
  → ID 필드의 선언 타입과 호환되는 조회 ID이면 캐시 키 계산
  → 등록된 객체가 있으면 ID 불변 조건 확인 후 반환
  → SELECT
  → 결과가 있으면 실제 매핑된 @Id 값으로 키 계산
  → 기존 관리 객체가 있으면 그 객체 반환
  → 없으면 새 객체 등록 후 반환
```

캐시 키는 필드에서 읽은 ID 값을 기준으로 한다. 첫 구현에서는 별도 숫자·문자열 변환을 추가하지 않는다. 원시 타입 ID는 wrapper 타입을 대응시킨다. 예를 들어 Long 필드에 Integer 조회 인자를 넘겼을 때는 기존 JDBC 조회를 실행하며, 결과의 Long ID가 이미 등록되어 있으면 기존 객체를 반환한다. 따라서 참조 동일성은 유지하되 이런 입력의 SELECT 생략은 보장하지 않는다. 기존 JDBC가 허용하던 입력을 캐시 때문에 새로 거부하지 않는다.

null 조회 결과는 저장하지 않는다. 같은 트랜잭션의 후속 INSERT를 다시 조회할 수 있어야 한다. 관리 객체의 필드를 재조회 결과로 덮어쓰지 않는다. 동일성 보장 범위는 현재 컨텍스트에서 관리 중인 객체다.

## 기존 변경 메서드와의 일관성

즉시 SQL 실행과 int 변경 행 수 반환을 유지한다. 첫 구현에서는 조회로 얻은 객체와 성공한 persist 객체를 관리한다. 명시적 update로 전달한 미관리 객체를 자동 등록하지 않는다.

- `persist`는 기존 생성 ID 검증을 먼저 수행한다. 직접 지정 ID가 이미 관리 중이면 INSERT 전에 충돌을 거부한다. SQL 성공과 ID 반영 이후에만 전달된 객체를 등록한다. 생성 키 충돌로 등록할 수 없으면 SQLException으로 실패를 전달하고 트랜잭션을 rollbackOnly로 표시한다.
- `update`는 같은 키의 관리 객체가 있으면 전달한 객체가 그 관리 객체인지 검사한다. 다른 참조이면 SQL 전에 IllegalArgumentException으로 충돌을 거부한다. 동일 객체나 기존 관리 객체가 없는 입력은 기존 SQL 경로를 사용한다.
- `update` 결과가 0이면 해당 키의 등록을 해제한다. 이미 사라진 행의 객체를 계속 반환하지 않게 한다.
- `remove`는 기존대로 ID 조건으로 실행한다. 정상 반환하면 변경 행 수가 0이어도 해당 키를 제거한다. 전달한 참조와 관리 참조가 달라도 같은 ID의 삭제를 허용한다.
- `persist`·`update`·`remove`의 SQL 실패 시, 해당 호출이 미완료 상태를 만든 트랜잭션을 rollbackOnly로 표시한다. 이 설계에서 자원 획득이나 find의 입력 검증 실패까지 자동으로 rollbackOnly로 바꾸지는 않는다.
- 관리 객체의 ID 변경은 지원하지 않는다. 컨텍스트는 등록 당시 키를 객체 참조와 연결해 기억하며, 재조회로 관리 객체를 반환하거나 관리 객체에 update·remove를 실행하기 전에 ID가 그대로인지 검사한다. 참조 역조회에는 IdentityHashMap을 사용한다.

다른 참조를 update할 때 충돌을 거부하는 것은 활성 트랜잭션에서 추가되는 계약이다. 기존 자동 커밋의 detached 객체 수정 경로는 유지한다. 등록 후 ID가 바뀌어도 단순 필드 쓰기 자체를 가로채지는 않으며 위 접근 시점에 감지한다.

직접 JDBC·Mapper로 수행한 변경은 컨텍스트가 자동 감지하지 않는다. 관리 중인 객체를 find하면 메모리 값을 반환한다. 처음 구현에서는 이러한 경로를 혼합할 때 동일 객체가 최신 DB 값이라는 가정을 피하고, 독립된 트랜잭션에서 다시 조회한다. refresh·clear API는 후속 범위다.

## 첫 구현의 완료 조건

- 같은 타입·ID 조회에서 참조 동일성과 SELECT 1회를 검증한다. 값 비교만으로 통과시키지 않는다.
- 서로 다른 위임 매니저가 같은 JdbcTemplate 또는 같은 TransactionManager의 별도 템플릿을 사용해도 같은 객체를 얻는다.
- 다른 트랜잭션·관리자·스레드는 서로 다른 세션을 사용한다. 트랜잭션 밖의 기존 새 객체 조회 테스트를 유지한다.
- REQUIRED 참여가 바깥 세션을 재사용하며 안쪽 정상·실패 반환 후 바깥 세션이 조기에 닫히지 않는다.
- commit·rollback·commit 실패·rollback 실패·정리 콜백 실패 후 자원 정리와 다음 트랜잭션 격리를 검증한다.
- 조회 없음 이후 저장·재조회, 다른 타입의 동일 ID, JDBC가 허용한 서로 다른 인자 타입의 조회를 검증한다.
- byte[] 등 지원 범위 밖 ID의 CRUD가 컨텍스트 없이 기존처럼 실행되는지 확인한다. 생성한 불변 키에 가변 ID가 유입되지 않게 검증한다.
- 직접 ID·IDENTITY·UUID persist 후 같은 객체 조회, 실패한 persist의 미등록을 검증한다.
- 명시적 update의 참조 충돌, update 0행, remove 후 재조회, ID 변경을 검증한다.
- 직접 JDBC·Mapper 변경 후 캐시된 값이 유지되는 동작을 명시적으로 검증한다.
- 종료 콜백에서 자원 등록·execute·withConnection을 재호출하면 거부되고, 다른 자원의 정리·상태 제거·Connection 반환이 계속되는지 검증한다.
- 기존 전체 테스트로 CRUD·생성 ID·동시 INSERT·트랜잭션 예외 계약의 회귀를 확인한다.

## 이후 변경 감지·flush로 확장

후속 단계에서 세션의 PersistenceContext에 스냅샷과 상태를 추가하고 `EntityManagerSession.flush()`가 변경 SQL을 생성하도록 한다. public 진입점은 현재 세션에 위임한다. 트랜잭션 자원 계약에는 별도 beforeCommit 훅을 추가해 바깥 commit 직전에 세션 flush를 실행하고, 실패 시 rollback으로 이어지도록 설계한다.

첫 구현에는 스냅샷·flush·쓰기 지연·merge·OSIV·명시적 장기 세션 API를 추가하지 않는다. 향후 장기 세션을 지원할 때는 컨텍스트 소유 구조를 유지하면서 트랜잭션 종료 시 세션을 닫는 정책을 분리한다.

## 구현 순서

1. 범용 트랜잭션 자원 등록·재사용·종료와 실패 보존을 구현한다.
2. 기존 CRUD를 실제 세션으로 옮기고 기존 EntityManager를 위임 진입점으로 구성한다. 먼저 기존 동작을 검증한다.
3. 세션의 컨텍스트와 find 캐시를 연결하고 동일성·트랜잭션 격리를 검증한다.
4. persist·update·remove와 캐시의 계약, ID 변경·실패 경로를 연결한다.
5. Stage 5 학습 문서의 구현 상태와 호출 흐름을 실제 코드에 맞춰 갱신한다.

이 순서는 설계 수준의 작업 구분이다. 구체적인 구현 계획은 이 설계를 검토한 뒤 작성한다.
