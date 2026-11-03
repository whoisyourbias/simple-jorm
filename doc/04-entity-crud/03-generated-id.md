# 04-03. DB 생성 ID 반영

기준일: 2026-11-03

```java
@Table("users")
class User {
    @Id
    @GeneratedValue
    @Column("user_id")
    private Long id;
    private String name;
}

// DB: user_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY
User user = new User();
int affected = entityManager.persist(user); // 1
// user.id에는 DB에서 생성한 키가 들어 있다.
```

`GeneratedValue`는 `com.jorm.entity`의 자체 어노테이션이다. DB identity 컬럼을 사용하는 단일 ID를 지원한다. 스키마 생성이나 JPA의 생성 전략 전체를 구현하지 않는다.

## 실행 순서

1. EntityMetadata는 @GeneratedValue가 인스턴스 @Id 필드에 붙어 있는지 확인한다. 타입은 Long 또는 Integer여야 한다. primitive 타입은 미지정 상태를 null로 표현할 수 없어 거부한다.
2. persist는 생성 ID가 null인지 확인한다. 이미 값이 있으면 SQL 전에 IllegalArgumentException이다. 일반 @Id는 기존대로 ID를 직접 지정해야 한다.
3. 생성 ID 컬럼을 제외한 INSERT를 만든다. ID만 매핑한 엔티티는 `INSERT INTO users DEFAULT VALUES`를 사용한다.
4. JdbcTemplate은 실제 ID 컬럼명으로 생성 키 반환을 요청하고 같은 연결에서 INSERT와 getGeneratedKeys를 실행한다. 활성 트랜잭션이 있으면 그 연결을 공유한다.
5. 한 행이 변경되고 키가 반환되면 엔티티 필드에 기록한다. Integer는 범위를 검사한다. persist는 기존대로 변경 행 수를 반환한다.

## 실패와 롤백

잘못된 어노테이션 위치·타입·초기 ID는 IllegalArgumentException이다. SQL 오류, 키 누락·null, Integer 범위 초과, 필드 반영 실패는 SQLException이다. INSERT가 실패하면 ID는 null로 남는다.

키를 반영한 뒤 트랜잭션이 롤백되면 DB 행은 없어지지만 객체 ID는 남는다. 현재 트랜잭션 관리자는 Java 객체의 상태를 복원하지 않는다. 같은 객체를 재시도하려면 ID를 비우거나 새 객체를 사용해야 한다. 생성 키가 연속적이라는 보장도 없다.

자동 커밋 상태에서는 INSERT 성공 후 키 처리 실패가 발생해도 행이 이미 저장됐을 수 있다. 키 처리까지 실패 시 롤백하려면 같은 TransactionManager의 execute 안에서 호출하고 예외를 전파해야 한다.

ID 컬럼의 DB 자동 생성 설정은 호출자가 준비한다. DB와 JDBC 드라이버가 지정 컬럼 생성 키 반환 및 사용한 INSERT 문법을 지원해야 한다. 이번 검증은 H2 기준이며 MySQL 등 다른 DB 호환성을 검증하지 않았다. 시퀀스·복합 ID와 Mapper 생성 키 반환은 지원하지 않는다. 원격 변경 병합으로 추가된 [UUID 전략](04-uuid-generated-id.md)은 별도 문서에서 다룬다.

## 검증

EntityManagerGeneratedIdTest의 테스트 14개로 Long·Integer 생성 키, 컬럼명 재정의, 반복 저장, ID만 있는 객체, 잘못된 설정, SQL 실패, 커밋·롤백, Integer 범위 초과를 검증한다. 생성 키 누락·null은 JDBC 응답만 대체하고 실제 H2 INSERT·롤백을 검증한다. 원격 IDENTITY·UUID 변경 병합 후 전체 131개 테스트가 통과했다.

```powershell
.\gradlew.bat test --tests com.jorm.entity.EntityManagerGeneratedIdTest
.\gradlew.bat test
```

후속 구현은 [엔티티 UPDATE·DELETE](05-update-and-remove.md)다. 다음 작업은 영속성 컨텍스트다.
