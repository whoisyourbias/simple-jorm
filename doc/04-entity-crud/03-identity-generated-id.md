# 04-03. IDENTITY 생성 ID

기준일: 2026-11-02

DB가 INSERT할 때 생성하는 ID를 같은 JDBC 연결에서 회수해 엔티티에 넣는다. 자체 어노테이션이며 JPA 표준 구현체는 아니다.

```java
@Table("users")
class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column("user_id")
    private Long id;
    @Column("user_name")
    private String name;
}
```

예제 스키마(H2):

```sql
CREATE TABLE users (
    user_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_name VARCHAR(100)
);
```

## 실행 순서

1. 메타데이터를 검증한다. `@GeneratedValue`는 `@Id`인 `Long`·`Integer` 필드에만 허용한다. 기본 전략은 IDENTITY다. [UUID 전략](04-uuid-generated-id.md)은 UUID·String 필드를 지원한다.
2. 저장 전 생성 ID는 null이어야 한다. 직접 값이 있거나 저장했던 객체를 다시 persist하면 SQL 실행 전에 거부한다. `@GeneratedValue`가 없는 ID는 기존처럼 직접 지정해야 한다.
3. ID를 제외한 INSERT를 생성하고 값을 바인딩한다. ID만 있는 엔티티에는 `INSERT INTO table DEFAULT VALUES`를 사용한다.
4. `JdbcTemplate.updateWithGeneratedKey`가 ID 컬럼명을 지정한 PreparedStatement로 정확히 한 행을 삽입한다. 같은 연결의 `getGeneratedKeys()`에서 키를 읽고, Long·Integer 변환과 객체 반영까지 처리한 뒤 자원을 닫는다. 별도 JDBC API인 `insertReturningKey`도 유지한다.
5. 키를 객체에 반영한 뒤 `persist`는 기존과 같이 변경 행 수 1을 반환한다. 반환값 자체가 ID는 아니다. ID로 `find`할 수 있다.

생성 키가 없거나 null이거나 여러 행이 반환되면 SQLException이다. Integer 범위를 넘는 키를 조용히 잘라 저장하지 않고 JDBC 변환 오류를 전달한다.

## 트랜잭션과 실패

`JdbcTemplate`과 같은 TransactionManager를 공유하면 키 회수도 기존 트랜잭션에 참여한다. 서비스 실패로 롤백하면 INSERT 행이 사라진다. **객체에 이미 반영된 ID는 초기화하지 않는다.** DB의 생성 번호에도 빈 번호가 생길 수 있으므로 연속 번호로 가정하지 않는다.

INSERT 자체가 실패하면 ID는 null로 남는다. INSERT 성공 후 키 회수에서 실패한 경우까지 DB를 되돌려야 한다면 호출을 `transactions.execute`로 묶어 예외가 트랜잭션 경계로 전파되게 한다. 트랜잭션 밖에서는 자동 커밋된 행이 키 회수 실패만으로 취소되지 않는다.

## 범위와 검증

스키마 자동 생성, SEQUENCE·TABLE·AUTO, primitive ID, 복합 ID는 지원하지 않는다. IDENTITY 컬럼 정의는 호출자가 준비한다. ID만 있는 엔티티의 `DEFAULT VALUES` 문법을 포함해 H2에서 검증했다. MySQL 등 다른 DB의 문법·드라이버별 생성 키 동작은 실서버에서 검증하지 않았다.

[EntityManagerIdentityTest](../../src/test/java/com/jorm/entity/EntityManagerIdentityTest.java)는 생성 ID와 컬럼 별칭, 바인딩, 연속 저장, ID만 있는 엔티티, Integer, 기존 ID 거부, SQL 실패, 롤백과 잘못된 선언을 검증한다. [JdbcTemplateGeneratedKeyTest](../../src/test/java/com/jorm/jdbc/JdbcTemplateGeneratedKeyTest.java)는 null 키·0행 INSERT·범위 초과 변환의 오류 전달을 확인한다.

다음 단계는 엔티티 UPDATE·DELETE다.
