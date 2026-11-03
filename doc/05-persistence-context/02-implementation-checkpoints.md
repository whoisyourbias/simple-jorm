# 05-02. 영속성 컨텍스트 구현과 검증 항목

기준일 2026-11-03. Stage 5의 객체 동일성과 트랜잭션별 세션 수명 관리를 구현했다. [세션 소유와 트랜잭션 연결 설계](../../docs/superpowers/specs/2026-11-03-persistence-context-design.md)에 API·수명·CRUD 계약을 정리했다. 스냅샷·변경 감지·flush는 후속 단계다.

## 현재 소유 구조와 호출 흐름

기존 `EntityManager`는 공유 가능한 위임 진입점이다. 트랜잭션별 실제 매니저인 `EntityManagerSession`이 `PersistenceContext`를 소유하고, 트랜잭션 관리자는 범용 자원 저장소에 `EntityManagerHolder`를 연결한다. 같은 관리자의 같은 트랜잭션은 세션을 재사용하며 가장 바깥 트랜잭션이 종료되면 세션을 정리한다.

트랜잭션 밖에서는 호출별 임시 세션을 사용한다. 종료 콜백 중 자원 등록이나 DB 작업 재진입은 거부한다. 정리 실패가 나더라도 나머지 자원 정리와 ThreadLocal 제거·연결 반환을 수행한다.

```text
EntityManager.find
  → JdbcTemplate.getTransactionManager
  → getOrCreateResource → EntityManagerHolder → EntityManagerSession
  → PersistenceContext 조회
    → 있음 → 기존 객체 반환
    → 없음 → SELECT → 실제 ID 기준 등록 → 관리 객체 반환
바깥 트랜잭션 종료 → afterCompletion → session.close → 컨텍스트 비우기
```

## 현재 계약

- Long·Integer·String·UUID와 long·int ID를 캐싱한다. 다른 ID 타입은 기존 JDBC CRUD를 사용한다.
- 다른 타입의 조회 인자도 JDBC가 허용하면 조회한다. 결과의 실제 ID로 기존 객체를 찾지만 SELECT 생략은 선언 타입에 맞는 인자에서 보장한다.
- null 조회 결과는 캐싱하지 않는다. 조회 타입의 기본 생성자 검증은 캐시 적중에도 적용한다.
- persist 성공 후 전달된 객체를 등록한다. 직접 지정 ID가 이미 관리 중이면 재저장을 거부한다.
- update는 같은 ID의 다른 객체가 관리 중이면 거부한다. 관리 객체 또는 해당 키가 미관리인 객체는 즉시 수정한다. 0행 수정은 해당 관리 키를 제거한다.
- remove는 ID로 즉시 삭제하고 해당 키를 제거한다. 다른 객체 참조로 호출하거나 삭제 행 수가 0이어도 제거한다.
- 관리 객체의 ID 변경은 find 반환 또는 update·remove 등 접근 시 거부한다. 등록 당시 ID는 객체 참조별로 기억한다.
- 변경 메서드의 SQL 실패는 rollbackOnly로 표시한다. 호출자가 예외를 잡아도 바깥 트랜잭션은 롤백한다.
- 직접 JDBC·Mapper 변경은 캐시를 자동 갱신하지 않는다. 최신 DB 상태가 필요하면 다음 트랜잭션에서 조회한다.

검증은 `EntityManagerPersistenceContextTest`, `EntityManagerSessionTest`, `TransactionResourceTest`와 기존 조회·CRUD·생성 ID·트랜잭션 테스트로 수행한다.

## Stage 4의 출발점과 연결 지점

| 작업 | 현재 동작 | Stage 5에서 연결할 지점 |
|---|---|---|
| `find` | SELECT 후 새 객체 반환, 결과가 없으면 null | 같은 키의 관리 객체 우선 반환 |
| `persist` | 즉시 INSERT, IDENTITY·UUID 반영, 변경 행 수 반환 | INSERT 성공 이후 객체 등록 시점 |
| `update` | ID 외 모든 필드를 즉시 UPDATE | 관리 객체·스냅샷과 명시적 변경의 일관성 |
| `remove` | 즉시 DELETE, 변경 행 수 반환 | 삭제한 객체의 캐시·상태 처리 |
| 트랜잭션 | 같은 관리자·스레드의 Connection 공유, REQUIRED 참여 | 컨텍스트 수명·종료·실패 정리 |

현재 계약과 검증 기록은 [Stage 4 UPDATE·DELETE](../04-entity-crud/05-update-and-remove.md), [트랜잭션](../03-transaction/01-connection-and-transaction.md)에 있다. 기존 호출자의 동작을 바꾸려면 변경 범위를 먼저 명시해야 한다.

## 1. 객체 동일성부터 구현

첫 학습 목표인 같은 컨텍스트 안의 객체 동일성과 반복 SELECT 생략을 구현했다. 구현 시 검토한 계약은 다음과 같다. 현재 결정은 위 계약과 설계 문서에 정리했다.

- 컨텍스트의 생성자·소유자·종료 시점. 트랜잭션 범위에 연결할 경우 바깥 REQUIRED 경계가 종료를 맡는지 정의한다.
- 트랜잭션 밖의 `find` 동작과 서로 다른 `EntityManager` 간 컨텍스트 공유 여부.
- 캐시 키의 ID 타입 정책. 현재 JDBC 바인딩에서 허용하던 입력을 확인하고 `1`, `1L` 같은 값의 변환·거부 여부를 정한다.
- 조회 결과가 없는 경우의 처리. null까지 저장하면 뒤에 생성된 행을 다시 조회할 수 있는지 결정해야 한다.
- 직접 지정 ID·IDENTITY·UUID 각각의 `persist` 성공 후 등록 시점. 실패한 INSERT가 관리 객체로 남지 않도록 한다.
- 같은 키에 서로 다른 객체를 전달했을 때 충돌 처리와 등록 후 ID 변경 처리.
- `update`·`remove` 및 직접 JDBC·Mapper 변경 이후 캐시를 유지·갱신·비우는 기준.

| 검증 상황 | 확인할 결과 |
|---|---|
| 같은 타입·ID 두 번 조회 | `assertSame`, SELECT 실행 횟수 1회 |
| 다른 ID 또는 다른 타입의 같은 ID | 서로 다른 객체가 저장되고 키가 충돌하지 않음 |
| 서로 다른 컨텍스트의 같은 ID | 서로 다른 참조와 독립된 관리 정보 |
| 행이 없는 조회, 잘못된 ID | 정한 null·입력 계약을 일관되게 적용 |
| 각 생성 전략의 저장 후 조회 | 생성된 ID와 관리 키가 일치 |
| 삭제 후 재조회와 실패한 저장 | 삭제·실패 전 객체가 잘못 반환되지 않음 |
| 커밋·롤백·예외 후 다음 작업 | 이전 컨텍스트의 객체가 새 작업으로 누출되지 않음 |
| 안쪽 REQUIRED 완료 | 바깥 작업에 필요한 관리 정보가 유지됨 |
| 독립된 스레드·트랜잭션 | 관리 객체가 의도치 않게 공유되지 않음 |

## 2. 스냅샷과 변경 감지

객체 동일성의 계약과 검증을 마친 뒤, 관리 시작 시점의 필드 값을 보관한다. 변경 비교 대상·값 복사 방식·SQL 생성 범위를 정한다. 현재 필드는 별도 타입 변환 없이 JDBC 값을 직접 대입하므로, 가변 값의 참조만 저장하면 원본과 현재 값이 함께 바뀌는 문제도 고려해야 한다.

검증은 변경 없는 객체, 필드 변경, null 전환, 여러 필드 변경, 원래 값으로 되돌린 객체를 포함한다. ID는 엔티티 식별에 쓰이므로 일반 수정 필드와 분리해 다룬다. 명시적 `update` 이후 중복 UPDATE가 생기지 않도록 스냅샷 처리도 검증한다.

## 3. flush와 트랜잭션 연결

먼저 명시적 `flush`의 동작을 정하고, 이후 커밋 전 자동 호출이 필요한지 결정한다. 관리 객체를 비교하고 필요한 SQL을 실행한 뒤 스냅샷을 갱신하는 순서를 정의한다. 여러 SQL 중 일부가 실패했을 때는 트랜잭션 롤백과 관리 정보의 정리를 함께 처리해야 한다.

| 검증 상황 | 확인할 결과 |
|---|---|
| 변경 후 flush | 같은 트랜잭션 연결에서 DB 값 변경 확인 |
| 성공한 flush를 다시 호출 | 추가 변경이 없으면 중복 UPDATE 생략 |
| flush 이후 rollback | DB 변경 취소와 Java 객체·관리 정보의 후속 상태 확인 |
| 여러 객체 처리 도중 SQL 실패 | 부분 실행 결과의 롤백, 이후 재사용 정책 준수 |
| 트랜잭션 밖의 flush | 정한 허용·거부 계약 적용 |
| REQUIRED 안쪽 호출 | 바깥 commit·rollback과 같은 단위로 처리 |

INSERT·DELETE 지연 실행까지 도입할 경우 현재의 즉시 실행·변경 행 수 반환과의 관계를 별도로 설계한다. IDENTITY는 DB에서 ID를 받아야 하므로 INSERT 시점도 함께 검토한다. 쿼리 전 자동 flush, `clear`·`detach`·`refresh`·`merge`, 관계 매핑·cascade·낙관적 잠금은 각각 후속 범위를 정한다.

## 구현을 시작할 때 확인할 파일

- [EntityManager](../../src/main/java/com/jorm/entity/EntityManager.java)와 [EntityMetadata](../../src/main/java/com/jorm/entity/EntityMetadata.java)
- [실제 매니저 세션](../../src/main/java/com/jorm/entity/EntityManagerSession.java), [holder](../../src/main/java/com/jorm/entity/EntityManagerHolder.java), [영속성 컨텍스트](../../src/main/java/com/jorm/entity/PersistenceContext.java)
- [TransactionManager](../../src/main/java/com/jorm/transaction/TransactionManager.java)와 [JdbcTemplate](../../src/main/java/com/jorm/jdbc/JdbcTemplate.java)
- [ID 조회 테스트](../../src/test/java/com/jorm/entity/EntityManagerFindTest.java), [변경·삭제 테스트](../../src/test/java/com/jorm/entity/EntityManagerMutationTest.java), [트랜잭션 테스트](../../src/test/java/com/jorm/transaction/TransactionManagerTest.java)

개념과 공식 자료는 [영속성 컨텍스트와 엔티티 생명주기](01-concepts-and-lifecycle.md)에 정리했다.
