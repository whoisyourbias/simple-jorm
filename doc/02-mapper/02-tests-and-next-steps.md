# 02-02. Mapper 기능, 테스트와 다음 작업

이 단계는 MyBatis처럼 직접 작성한 SQL을 Mapper 메서드에 연결하는 기능을 학습한다. Hibernate식 엔티티 메타데이터 기반 SQL 생성과는 별도 경로다. [단계별 실제 기술 대응과 차이](../reference/03-learning-stage-mapping.md)를 함께 참고한다.

## 현재 기능

| 어노테이션 | 실행 경로 | 반환값 |
|---|---|---|
| @Select | queryForObject / query | 객체 또는 List<T> |
| @Insert | update | 삽입된 행 수(int) |
| @Update | update | 수정된 행 수(int) |
| @Delete | update | 삭제된 행 수(int) |

팩토리에 Mapper 인터페이스와 JdbcTemplate을 전달하면 프록시를 생성한다. 기존 SQL의 `?`는 메서드 인자 순서대로 전달하며, 인자가 없으면 빈 배열로 처리한다. 조회 결과가 없으면 단건은 null, 목록은 빈 리스트다.

## 이름 바인딩과 default 메서드의 구현 계약

```java
@Select("SELECT id, name, email FROM users WHERE name = #{name} AND id = #{id}")
User find(@Param("id") Long id, @Param("name") String name) throws SQLException;

default User first() throws SQLException {
    return find(1L, "Alice");
}
```

- `com.jorm.mapper.Param`은 런타임에 인자 이름을 제공하는 자체 어노테이션이다. 이름 SQL에서는 모든 인자에 고유한 `@Param`을 명시한다. Java 컴파일 옵션이나 실제 매개변수 이름에 의존하지 않는다.
- `#{name}`을 SQL 등장 순서대로 `?`로 변환하고 대응하는 값을 별도 배열에 담는다. 인자 선언 순서가 달라도 되고 같은 이름을 반복할 수 있으며, null도 값으로 전달한다. 값은 문자열로 SQL에 합치지 않고 `JdbcTemplate`의 PreparedStatement 바인딩을 사용한다.
- 이름 누락·중복·잘못된 이름, 닫히지 않은 토큰, `?`와 이름 토큰 혼용은 JDBC 실행 전에 `IllegalArgumentException`으로 거부한다. 이름은 `[A-Za-z_][A-Za-z0-9_]*` 형식이다. 문자열 리터럴·인용 식별자·SQL 주석 안의 토큰은 변환하지 않는다.
- `${...}` 원문 치환은 지원하지 않고 SQL 코드 영역에서 발견하면 거부한다. 단일 bean/record의 속성 경로, Map, 자동 `param1` 이름, `jdbcType` 같은 토큰 옵션도 이번 범위에서 제외한다. 명시적 `@Param`과 단순 값으로 동작을 좁힌다.
- 인터페이스 default 메서드는 SQL 어노테이션 검사 전에 JDK `InvocationHandler.invokeDefault`로 실행한다. default 본문의 `this.find(...)`는 같은 Mapper 프록시를 거쳐 SQL로 연결된다. 본문 예외는 그대로 전파한다.
- `toString`, `equals`, `hashCode`는 기존 프록시 동일성 계약을 유지한다. SQL 없는 추상 메서드는 계속 거부한다.

[MyBatis Java API](https://mybatis.org/mybatis-3/java-api.html)의 `@Param` 이름 지정과 [Mapper Parameters](https://mybatis.org/mybatis-3/sqlmap-xml.html#Parameters)의 PreparedStatement 바인딩이 비교 기준이다. JORM은 이 핵심 경로만 구현하며 XML·동적 SQL·SqlSession·메서드 캐시는 추가하지 않는다.

```java
UserMapper mapper = MapperFactory.create(UserMapper.class, jdbcTemplate);
```

SQL 어노테이션은 메서드당 하나만 허용한다. 없으면 UnsupportedOperationException, 여러 개이거나 변경 메서드 반환 타입이 int가 아니면 IllegalArgumentException이다. 검사는 SQL 실행 전에 수행한다. SQL 예외를 직접 전달받으려면 Mapper 메서드에 throws SQLException을 선언한다. 선언하지 않은 checked 예외는 JDK 프록시가 UndeclaredThrowableException으로 감쌀 수 있다.

## 테스트

[MapperFactoryTest.java](../../src/test/java/com/jorm/mapper/MapperFactoryTest.java)의 기존 20개와 [MapperNamedParametersTest.java](../../src/test/java/com/jorm/mapper/MapperNamedParametersTest.java)의 16개, 총 36개 테스트로 검증한다. 테스트마다 별도 H2 DB를 만들고 연결을 유지한 뒤 종료 시 닫는다. JDBC 전 검증 테스트는 실행기가 없는 프록시를 사용해 DB에 도달하기 전에 잘못된 선언을 거부하는지도 확인한다.

| 범위 | 검증 내용 |
|---|---|
| 프록시 | 인터페이스 구현, 일반 클래스 거부, Object 메서드 처리 |
| 조회 | 단건 매핑, 결과 없음, 인자 없는 호출, 다중 인자 순서 |
| 목록 | 전체 행과 필드 매핑, 조건 인자, 빈 리스트 |
| 삽입 | 영향받은 행 수와 저장된 값, 인자 없는 호출, SQL 오류 전파 |
| 수정·삭제 | 대상 행 변경·삭제, 다른 행 유지, 미일치 시 0, 인자 없는 다중 행 작업 |
| 선언 검증 | 어노테이션 미지정·중복, 잘못된 변경 반환 타입, 거부 시 DB 유지 |
| 이름 바인딩 | SQL 순서와 반복 이름, null 값, SELECT·INSERT·UPDATE·DELETE, SQL처럼 보이는 값의 데이터 보존 |
| 토큰 검증 | 이름 누락·중복·잘못된 이름, 닫히지 않은 토큰, 혼용·원문 치환·옵션 거부, 리터럴·주석 보존 |
| default 메서드 | 본문 실행, 내부 Mapper 호출의 SQL 실행, 본문 예외의 동일 객체 전파 |

## 실행

프로젝트 루트에서 실행한다.

```powershell
.\gradlew.bat test --tests 'com.jorm.mapper.*'
.\gradlew.bat test
```

## 다음 작업

트랜잭션 관리자의 연결 공유·일괄 롤백, Service 프록시, REQUIRED 참여까지 구현·검증을 완료했다. [트랜잭션 학습](../03-transaction/01-connection-and-transaction.md)으로 이어진다.

SQL을 직접 작성하는 Mapper 이후에는 [엔티티 메타데이터·INSERT](../04-entity-crud/01-metadata-and-insert.md)와 [ID 조회](../04-entity-crud/02-find-by-id.md)를 읽으며 객체 구조로 SQL을 만드는 경로와 비교한다.

Mapper 생성 ID 반환, bean/record 속성 경로, XML·동적 SQL·결과 매핑 설정, SqlSession 캐시와 컴파일 타임 어노테이션 검사는 별도 확장 사항이다. 기존 위치 바인딩은 그대로 두며, 이름 바인딩의 문자열·식별자 인용은 작은따옴표·큰따옴표·백틱과 같은 인용문자 두 번으로 이스케이프하는 형태만 인식한다. 방언별 dollar quoting·백슬래시 이스케이프·중첩 주석까지 해석하는 SQL 파서는 아니다.

[초기 프록시 동작 학습](01-proxy-execution.md) · [현재 진행 상황](../00-overview/01-progress.md)
