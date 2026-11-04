# 05-01. 영속성 컨텍스트와 변경 감지

기준일 2026-11-04. Stage 5는 객체 동일성, 트랜잭션별 세션, 스냅샷, 변경 감지, 명시적 flush와 commit 직전 자동 flush를 제공한다. [구현 계약과 검증](02-implementation-checkpoints.md)에 지원 범위와 실패 정책을 정리했다.

## 같은 행을 같은 객체로 다룬다

같은 TransactionManager·스레드의 트랜잭션에서 같은 엔티티 타입과 ID를 조회하면 같은 관리 객체를 반환한다. 여러 서비스가 각자 ID로 조회해도 앞선 서비스가 메모리에서 변경한 값을 공유한다. `(엔티티 클래스, ID) → 관리 객체` 저장소가 1차 캐시이며, 클래스별 매핑 정보를 저장하는 metadataCache와 역할이 다르다.

```java
transactions.execute(() -> {
    User first = entities.find(User.class, 1L);
    User second = entities.find(User.class, 1L);
    assertSame(first, second); // 두 번째 SELECT 생략
    first.setName("변경한 이름");
    return null; // 가장 바깥 commit 직전 변경 감지 → UPDATE → commit
});
```

Long·Integer·String·UUID와 long·int ID를 관리한다. 트랜잭션 밖에서는 호출마다 임시 세션을 닫으므로 객체 동일성이나 이후 변경 감지가 이어지지 않는다. 다른 관리자·스레드·트랜잭션의 관리 객체는 분리된다. null 결과는 캐싱하지 않는다. 같은 키의 다른 객체로 persist/update하면 거부하며 관리 객체의 ID도 바꿀 수 없다.

## 상태를 기억해 필요한 UPDATE만 실행한다

find 또는 성공한 persist는 객체와 함께 매핑 필드의 스냅샷을 보관한다. flush는 현재 값과 스냅샷을 비교하고 변경된 엔티티에만 ID 외 모든 매핑 컬럼 UPDATE를 실행한다. 변경 없는 객체, 원래 값으로 되돌린 객체에는 UPDATE하지 않는다. 성공한 flush와 명시적 update는 스냅샷을 갱신하므로 다음 flush가 중복 UPDATE하지 않는다.

```java
transactions.execute(() -> {
    User user = entities.find(User.class, 1L);
    user.setName("동기화할 이름");
    entities.flush(); // 현재 트랜잭션 연결에서 UPDATE; 아직 commit 아님
    entities.flush(); // 추가 변경이 없으므로 UPDATE 없음
    return null;
});
```

명시적 flush는 활성 트랜잭션이 필요하다. 트랜잭션 밖에서는 IllegalStateException이다. 일반 조회나 내부 REQUIRED 반환 때 자동 flush하지 않으며 가장 바깥 commit 직전에 자동 flush한다. 객체가 없어도 활성 트랜잭션의 flush는 허용한다.

byte[]와 java.sql.Date·Time·Timestamp, java.util.Date는 스냅샷에서 복사하므로 내부 변경도 감지한다. 임의 mutable 객체·컬렉션·Blob/Clob·관계 등은 깊게 복사하지 않는다. 지원하는 JDBC 기본 값 안에서 동작하는 최소 변경 감지다.

## flush와 commit의 차이

flush는 관리 객체의 변경을 현재 DB 트랜잭션으로 보낸다. commit은 그 트랜잭션을 확정한다. 따라서 성공한 flush 이후에도 예외가 발생하면 앞선 SQL을 모두 롤백한다. SQL 실패는 rollbackOnly로 남기므로 업무가 예외를 잡아도 부분 성공을 commit하지 않는다. 여러 객체 중 일부 UPDATE가 성공했더라도 전체 롤백 대상이다.

세션은 commit·rollback 시도 후 afterCompletion에서 닫히며 객체 참조와 스냅샷을 비운다. Java 객체 필드·생성 ID는 DB 롤백으로 되돌아가지 않는다. 실패한 트랜잭션의 관리 정보를 새 트랜잭션에서 재사용하지 않는다.

직접 JDBC·Mapper로 실행한 SQL은 관리 객체나 스냅샷을 자동 갱신하지 않는다. 변경 없는 관리 객체는 DB의 외부 변경을 덮어쓰지 않지만, 이후 객체를 수정하면 전체 컬럼 UPDATE가 실행되어 해당 DB 값을 덮어쓸 수 있다. 최신 상태가 필요하면 새 트랜잭션에서 조회한다. 동시성 제어와 낙관적 잠금은 별도 학습 범위다.

## 실제 JPA와 남는 차이

JPA는 관리 객체의 변경을 동기화하는 표준이며 EntityManager.find는 컨텍스트에 이미 있는 엔티티를 반환한다. JPA의 flush도 트랜잭션이 필요하며 commit과 구별된다. [Jakarta Persistence EntityManager API](https://jakarta.ee/specifications/persistence/3.2/apidocs/jakarta.persistence/jakarta/persistence/EntityManager.html)

simple-jorm의 persist/update/remove는 즉시 SQL을 실행하고 int 행 수를 반환한다. JPA의 persist/remove는 void이고 표준 update(entity)는 없다. INSERT·DELETE 쓰기 지연, 쿼리 전 AUTO flush, merge·clear·detach·refresh, 관계·cascade·복합 ID·상속·지연 로딩·낙관적 잠금·여러 트랜잭션에 걸친 확장 컨텍스트는 구현하지 않았다.

공유 가능한 EntityManager 진입점과 트랜잭션별 실제 세션을 분리했지만, JPA EntityManager의 스레드 안전성을 뜻하지 않는다. JPA의 전체 생명주기와 provider 동작은 [Jakarta Persistence 3.2 표준](https://jakarta.ee/specifications/persistence/3.2/jakarta-persistence-spec-3.2.html)에서 확인한다.
