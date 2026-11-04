# 04-05. 엔티티 UPDATE·DELETE

기준일: 2026-11-03

```java
User user = entityManager.find(User.class, id);
user.name = "changed";
int updated = entityManager.update(user);
int removed = entityManager.remove(user);
```

두 메서드는 호출 중 SQL을 실행하고 DB가 보고한 변경 행 수를 int로 반환한다. 대상 ID가 없으면 0이다. 객체를 변경하는 것만으로는 DB에 반영되지 않는다.

## UPDATE 생성

1. null 객체를 거부하고 클래스의 EntityMetadata를 가져온다.
2. @Id 값을 읽는다. null ID는 SQL 전에 IllegalArgumentException이다.
3. ID 외 모든 매핑 필드를 SET 목록에 넣고 현재 객체 값을 같은 순서로 수집한다. null 값도 바인딩한다.
4. @Column을 반영한 실제 ID 컬럼을 WHERE에 사용하고 ID 값을 마지막에 바인딩한다.
5. JdbcTemplate.update로 SQL을 실행한다.

```sql
UPDATE users SET user_name = ?, email = ? WHERE user_id = ?
```

변경 여부를 추적하지 않으므로 ID 외 모든 필드를 덮어쓴다. 일부 필드만 채운 객체를 전달하면 나머지 필드의 null도 저장된다. ID만 있는 엔티티는 SET 대상이 없어 IllegalArgumentException이다. 대상 행이 없다고 INSERT하지 않는다.

ID는 SET에 포함하지 않는다. 영속성 컨텍스트가 없으므로 원래 ID를 기억하거나 ID 변경을 탐지하지 않으며, 호출 시점의 ID가 대상 행을 결정한다.

## DELETE 생성

remove도 null 객체와 null ID를 거부한다. 다른 필드 값은 읽지 않고 아래 SQL을 실행한다. ID만 선언한 엔티티도 삭제할 수 있다.

```sql
DELETE FROM users WHERE user_id = ?
```

이미 삭제한 객체로 다시 호출하면 0을 반환한다. 삭제 후에도 객체의 ID와 나머지 필드 값은 유지한다.

## 생성 ID와 트랜잭션

직접 지정 ID, IDENTITY, UUID 모두 지정된 ID를 조건에 사용한다. update·remove가 새 ID를 생성하지 않는다. 두 메서드는 전달된 객체를 사용하므로 기본 생성자가 필요 없다.

JdbcTemplate과 같은 TransactionManager의 활성 연결을 공유한다. 트랜잭션 밖에서는 임시 연결을 사용한다. SQL 오류는 SQLException으로 전파된다. 같은 트랜잭션 안에서 앞선 UPDATE·DELETE가 성공해도 뒤의 오류가 밖으로 전파되면 모두 롤백된다. DB 롤백은 이미 바꾼 Java 객체 값을 복원하지 않는다.

JPA의 관리 상태 검사, 자동 변경 감지, flush, cascade, 낙관적 잠금은 이 단계의 범위가 아니다. 관계 매핑·복합 ID·JPA 상속 전략은 지원하지 않는다. 부모의 필드·단일 ID와 저장 제외 선언은 [공통 메타데이터](06-inherited-and-transient-fields.md)를 통해 UPDATE·DELETE에도 반영한다.

## 검증

EntityManagerMutationTest의 17개 테스트로 실제 H2 저장 결과를 확인한다. 별칭 컬럼, private 필드, 기본 생성자 없는 타입, 대상 행 보호, 반복 수정·삭제, null·따옴표 바인딩, 입력 오류, ID 전용 엔티티, 제약 위반, 생성 ID 유지, 커밋·롤백을 포함한다.

```powershell
.\gradlew.bat test --tests com.jorm.entity.EntityManagerMutationTest
.\gradlew.bat test
```

다음은 05단계 영속성 컨텍스트의 객체 동일성·변경 감지·flush다.
