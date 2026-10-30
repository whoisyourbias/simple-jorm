# 02-02. Mapper 기능, 테스트와 다음 작업

## 현재 기능

| 어노테이션 | 실행 경로 | 반환값 |
|---|---|---|
| @Select | queryForObject / query | 객체 또는 List<T> |
| @Insert | update | 삽입된 행 수(int) |
| @Update | update | 수정된 행 수(int) |
| @Delete | update | 삭제된 행 수(int) |

팩토리에 Mapper 인터페이스와 JdbcTemplate을 전달하면 프록시를 생성한다. 메서드 인자는 SQL의 ?에 순서대로 전달하며, 인자가 없으면 빈 배열로 처리한다. 조회 결과가 없으면 단건은 null, 목록은 빈 리스트다.

```java
UserMapper mapper = MapperFactory.create(UserMapper.class, jdbcTemplate);
```

SQL 어노테이션은 메서드당 하나만 허용한다. 없으면 UnsupportedOperationException, 여러 개이거나 변경 메서드 반환 타입이 int가 아니면 IllegalArgumentException이다. 검사는 SQL 실행 전에 수행한다. SQL 예외를 직접 전달받으려면 Mapper 메서드에 throws SQLException을 선언한다. 선언하지 않은 checked 예외는 JDK 프록시가 UndeclaredThrowableException으로 감쌀 수 있다.

## 테스트

[MapperFactoryTest.java](../../src/test/java/com/jorm/mapper/MapperFactoryTest.java)의 20개 테스트는 H2를 사용한다. 테스트마다 별도 DB를 만들고 연결을 유지한 뒤 종료 시 닫는다.

| 범위 | 검증 내용 |
|---|---|
| 프록시 | 인터페이스 구현, 일반 클래스 거부, Object 메서드 처리 |
| 조회 | 단건 매핑, 결과 없음, 인자 없는 호출, 다중 인자 순서 |
| 목록 | 전체 행과 필드 매핑, 조건 인자, 빈 리스트 |
| 삽입 | 영향받은 행 수와 저장된 값, 인자 없는 호출, SQL 오류 전파 |
| 수정·삭제 | 대상 행 변경·삭제, 다른 행 유지, 미일치 시 0, 인자 없는 다중 행 작업 |
| 선언 검증 | 어노테이션 미지정·중복, 잘못된 변경 반환 타입, 거부 시 DB 유지 |

## 실행

프로젝트 루트에서 실행한다.

```powershell
.\gradlew.bat test --tests com.jorm.mapper.MapperFactoryTest
.\gradlew.bat test
```

## 다음 작업

1. 트랜잭션 관리자에서 Connection 공유와 commit·rollback을 구현한다.
2. 여러 Mapper 작업 중 하나가 실패하면 전체가 롤백되는지 검증한다.
3. Service 프록시로 트랜잭션 경계를 적용한다.

생성 ID 반환과 컴파일 타임 어노테이션 검사는 별도 확장 사항이다.

[초기 프록시 동작 학습](01-proxy-execution.md) · [현재 진행 상황](../00-overview/01-progress.md)
