# 05-01. 영속성 컨텍스트와 엔티티 생명주기

기준일 2026-11-03. Stage 5의 객체 동일성·1차 캐시·세션 수명 관리를 구현했다. 변경 감지·flush와 JPA의 전체 상태 전환은 후속 학습 개념이다.

## 왜 영속성 컨텍스트를 사용하는가

객체를 조회하고 업무 규칙에 따라 수정한 뒤 DB에 반영하는 과정에서, ORM이 같은 데이터를 표현하는 객체와 그 변경 내역을 관리하도록 하기 위해 사용한다. 같은 컨텍스트의 객체 동일성, 관리 객체의 변경 추적, flush를 통한 동기화가 연결된다. [Jakarta Persistence EntityManager API](https://jakarta.ee/specifications/persistence/3.2/apidocs/jakarta.persistence/jakarta/persistence/EntityManager.html)

### 서로 다른 서비스에서 조회가 겹칠 수 있다

하나의 메서드에서 같은 ID를 연속 조회하는 예제는 동일성 검증에는 간단하지만 실제 사용 이유를 충분히 보여 주지 못한다. 업무를 나눈 여러 서비스가 각자 필요한 데이터를 조회하다 보면 같은 엔티티를 가져올 수 있다.

```java
// 설명용 의사 코드. 두 서비스가 같은 트랜잭션·컨텍스트에 참여한다고 가정한다.
void order(Long userId) {
    pointService.use(userId);       // 내부에서 User를 조회해 포인트 차감
    benefitService.evaluate(userId); // 내부에서 같은 User의 남은 포인트 확인
}
```

두 서비스가 별도 객체를 조회하고 첫 서비스의 변경을 아직 DB에 반영하지 않았다면, 두 번째 객체에는 차감 전 포인트가 들어갈 수 있다. 같은 컨텍스트에서 동일한 관리 객체를 돌려주면 두 번째 서비스도 메모리에서 차감된 값을 읽는다. 이 예제에서는 두 서비스 모두 해당 컨텍스트의 `find`를 사용한다고 가정한다.

처음 조회한 객체를 서비스에 인자로 전달하는 방식으로도 같은 객체를 사용할 수 있다. 영속성 컨텍스트를 사용하면 각 서비스가 ID로 조회하는 구조에서도 ORM이 동일한 관리 객체를 연결해 준다. 여러 서비스가 함께 실행되는 업무에서 이 역할이 드러난다.

### 객체 변경과 저장 호출을 함께 관리한다

현재 simple-jorm에서는 필드를 바꾼 뒤 `update()`를 직접 호출한다. 여러 분기에서 객체를 수정하면 각 경로가 저장 호출까지 수행하는지 개발자가 챙겨야 한다.

```java
// 현재 simple-jorm의 사용 방식
User user = em.find(User.class, userId);
user.setName("새 이름");
em.update(user);
```

변경 감지를 도입하면 ORM이 관리 시작 시점의 값과 현재 값을 비교한다. 업무 코드는 객체를 수정하고, 정해 둔 flush 시점에 ORM이 필요한 UPDATE를 실행할 수 있다. simple-jorm에서는 객체 동일성 구현 후 스냅샷과 변경 감지를 추가하며, 커밋 전 자동 flush 여부는 후속 설계에서 결정한다.

### 반복 조회 비용과 관리 비용을 함께 본다

같은 컨텍스트에 이미 있는 엔티티를 ID로 조회할 때는 등록된 객체를 재사용해 SELECT를 줄일 수 있다. 효과는 같은 엔티티에 다시 접근하는 업무에서 생긴다. 한 번 조회하고 종료하는 작업에서는 이 이점이 작다.

객체와 스냅샷을 유지하는 만큼 메모리를 사용하고, flush 때 변경 비교 비용이 든다. 컨텍스트를 오래 유지하면 DB의 외부 변경과 관리 객체의 값이 달라질 수도 있다. 따라서 업무 단위에 맞게 수명과 정리 시점을 정해야 한다.

## TypeORM과 비교하면

TypeORM은 `save()`·`update()`를 명시적으로 호출해 변경을 반영한다. 객체의 필드를 바꾸고 트랜잭션을 커밋하는 것만으로 JPA식 자동 변경 감지가 수행되지는 않는다. 같은 ID의 반복 조회에 대해 JPA식 영속성 컨텍스트의 참조 동일성을 전제로 코드를 작성해서도 안 된다. [TypeORM EntityManager API](https://typeorm.io/docs/working-with-entity-manager/entity-manager-api/)

```typescript
const user = await userRepo.findOneByOrFail({ id: userId });
user.name = '새 이름';
await userRepo.save(user);
```

단순 CRUD에서는 이러한 명시적 저장 방식으로도 업무를 구현할 수 있다. 영속성 컨텍스트는 ORM이 선택할 수 있는 객체 관리 방식이며, simple-jorm의 Stage 5는 JPA·Hibernate가 객체 동일성과 변경을 관리하는 원리를 학습하는 단계다.

NestJS에서도 사용하는 ORM에 따라 동작이 달라진다. MikroORM은 Identity Map과 Unit of Work를 제공하고, 관리 객체의 변경을 `flush()` 때 감지해 반영한다. [MikroORM Identity Map](https://mikro-orm.io/docs/identity-map), [Unit of Work](https://mikro-orm.io/docs/unit-of-work)

## 영속성 컨텍스트가 관리하는 것

영속성 컨텍스트는 엔티티의 식별자와 Java 객체를 연결해 관리하는 범위다. 같은 컨텍스트에서 같은 엔티티 타입과 ID에 대응하는 관리 객체는 하나다. `EntityManager`는 그 컨텍스트를 통해 엔티티를 조회하고 상태를 관리하는 진입점이다. [Jakarta Persistence EntityManager API](https://jakarta.ee/specifications/persistence/3.2/apidocs/jakarta.persistence/jakarta/persistence/EntityManager.html)

현재 `EntityManager`의 `metadataCache`는 클래스별 테이블명·컬럼·필드 정보를 보관한다. 현재 세션의 컨텍스트 저장소는 `(엔티티 클래스, ID) → 엔티티 인스턴스`다. 이후 변경 감지를 구현할 때는 조회 시점의 필드 값도 함께 보관한다.

| 구분 | 현재 메타데이터 캐시 | Stage 5에서 다룰 관리 정보 |
|---|---|---|
| 키 | 엔티티 클래스 | 엔티티 클래스와 ID |
| 값 | `EntityMetadata` | 관리 객체, 이후 스냅샷·상태 |
| 역할 | 매핑 분석 결과 재사용 | 객체 동일성 유지와 변경 추적 |
| 수명 | 현재 `EntityManager` 인스턴스의 수명 | 컨텍스트의 시작·종료 정책에 따라 결정 |

## 1차 캐시와 객체 동일성

```java
// 현재 지원하는 동작. 같은 트랜잭션·컨텍스트 안에서 실행한다.
User first = em.find(User.class, 1L);
User second = em.find(User.class, 1L);
assertSame(first, second);
```

첫 조회는 관리 객체 저장소를 확인하고, 없으면 SELECT 결과로 만든 객체를 등록한다. 두 번째 ID 조회는 등록된 객체를 돌려준다. 이 저장소를 흔히 1차 캐시라고 부른다. `assertSame`은 참조가 같은지를 검사한다. 필드 값이나 `equals()`의 결과가 같다는 것보다 강한 조건이다. JPA의 `find`도 이미 컨텍스트에 있는 엔티티를 반환하는 계약을 갖는다. [EntityManager.find](https://jakarta.ee/specifications/persistence/3.2/apidocs/jakarta.persistence/jakarta/persistence/EntityManager.html#find(java.lang.Class,java.lang.Object))

```text
find(User.class, 1L)
  → 현재 컨텍스트에서 키 조회
    → 있음 → 등록된 객체 반환
    → 없음 → SELECT → 객체 생성 → 등록 → 반환
```

현재 코드는 같은 관리자·트랜잭션의 세션에서 위 흐름을 사용한다. Long·Integer·String·UUID와 long·int ID를 캐싱하며, 트랜잭션 밖과 다른 ID 타입은 매번 조회한다. `User(1)`과 `Order(1)`은 다른 키로 관리해야 하며, 서로 다른 컨텍스트 사이에서는 참조 동일성을 보장하지 않는다.

1차 캐시에 있는 객체를 반환하면 다른 연결에서 바꾼 DB 값이 자동으로 덮어써지지 않는다. 동시 접근의 일관성은 DB 격리 수준과 잠금 정책도 함께 다뤄야 한다. 직접 JDBC·Mapper로 실행한 변경을 관리 객체에 반영할 정책도 필요하다.

## 엔티티 생명주기

| 상태 | 의미 | 개념 예시 |
|---|---|---|
| 비영속 / new | 컨텍스트에 등록하기 전 생성한 객체 | `new User()` |
| 영속 / managed | 현재 컨텍스트가 식별자와 객체를 연결해 관리 | `find`로 얻거나 `persist`로 관리한 객체 |
| 준영속 / detached | 영속 상태였다가 컨텍스트에서 분리된 객체 | `detach`, `clear`, 컨텍스트 종료 이후 |
| 삭제 / removed | 관리 중이며 DB 삭제가 예정된 객체 | 관리 객체에 `remove` 호출 |

직접 지정한 ID가 있는 새 객체도 `persist` 전에는 비영속일 수 있다. 상태를 판단할 때는 ID 존재 여부와 컨텍스트 등록 여부를 함께 봐야 한다. 위 표는 JPA 생명주기 전체를 설명한다. 현재 simple-jorm은 세션별 객체 등록·제거·종료를 지원하며 detach·clear와 삭제 예약 API는 후속 범위다. [Jakarta Persistence 3.2, 3.3 Entity Instance’s Life Cycle](https://jakarta.ee/specifications/persistence/3.2/jakarta-persistence-spec-3.2.html)

## 변경 감지와 flush

Stage 5에서 학습할 변경 감지 방식은 관리 시작 시점의 필드 값을 스냅샷으로 보관하고, `flush` 때 현재 값과 비교하는 것이다.

```java
// Stage 5 목표 예제. 활성 트랜잭션과 같은 컨텍스트 안에서 실행한다.
User user = em.find(User.class, 1L);
user.setName("변경한 이름");
em.flush(); // 스냅샷과 비교해 UPDATE 실행
// 이 메서드를 포함한 바깥 트랜잭션 경계에서 commit 또는 rollback
```

`flush`는 관리 중인 변경을 DB에 동기화한다. 트랜잭션의 확정은 `commit`이 맡으며, flush로 실행한 SQL도 같은 트랜잭션이 롤백되면 취소된다. 실제 JPA는 커밋 시 동기화하며, 기본 AUTO 모드에서는 쿼리 결과에 영향을 주는 변경의 가시성도 보장한다. simple-jorm의 자동 flush 시점은 구현 전에 별도로 정할 항목이다. [Jakarta Persistence 3.2, 3.3.4 Synchronization to the Database 및 3.11.2 Queries and Flush Mode](https://jakarta.ee/specifications/persistence/3.2/jakarta-persistence-spec-3.2.html)

DB 롤백이 Java 객체의 필드 값을 원래대로 복원해 주지는 않는다. 현재 프로젝트도 생성 ID·수정한 필드가 객체에 남는다. Stage 5에서는 실패 후 관리 정보와 스냅샷을 계속 사용할 수 있는지까지 정의해야 한다.

## 트랜잭션과 컨텍스트의 수명

트랜잭션은 DB 작업을 확정하거나 취소하는 범위다. 컨텍스트는 객체의 동일성과 상태를 유지하는 범위다. JPA는 트랜잭션 범위 컨텍스트와 여러 트랜잭션에 걸쳐 유지되는 확장 컨텍스트 등을 정의한다. 실제 `EntityManager`와 컨텍스트는 여러 스레드가 동시에 공유하지 않도록 사용해야 한다. [Jakarta Persistence 3.2, 7.2 및 7.7](https://jakarta.ee/specifications/persistence/3.2/jakarta-persistence-spec-3.2.html)

simple-jorm은 같은 `TransactionManager`·스레드에서 Connection을 공유하고 REQUIRED 호출은 바깥 트랜잭션에 참여한다. [설계 초안](../../docs/superpowers/specs/2026-11-03-persistence-context-design.md)은 트랜잭션별 실제 매니저인 `EntityManagerSession`이 컨텍스트를 소유하고, 트랜잭션 관리자가 holder를 통해 세션을 연결·재사용하는 구조를 사용한다. 바깥 트랜잭션의 commit·rollback 시도 후 세션을 닫는다. 현재 메타데이터 캐시의 `ConcurrentHashMap`만으로 향후 관리 객체의 동시 사용까지 보장할 수는 없다.

## 현재 코드와 연결해 읽기

- [EntityManager](../../src/main/java/com/jorm/entity/EntityManager.java)의 `find`, `persist`, `update`, `remove`에서 SQL 실행 시점을 확인한다.
- [TransactionManager](../../src/main/java/com/jorm/transaction/TransactionManager.java)의 `execute`, `participate`, `withConnection`에서 연결과 트랜잭션 수명을 확인한다.
- [구현 준비와 검증 항목](02-implementation-checkpoints.md)에서 Stage 5의 진행 순서를 확인한다.
