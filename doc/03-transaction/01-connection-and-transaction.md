# 03-01. Connection 공유와 트랜잭션

이 단계는 특정 ORM이 아니라 Spring의 프로그램·프록시 기반 트랜잭션 관리와 REQUIRED 참여 개념에 대응한다. 실제 구현은 Spring을 호출하지 않고 JDBC 위에 직접 작성했다. [단계별 실제 기술 대응과 차이](../reference/03-learning-stage-mapping.md)를 함께 참고한다.

트랜잭션은 같은 Connection에서 실행한 변경을 함께 확정하거나 취소하는 단위다.

```java
TransactionManager transactions = new TransactionManager(dataSource);
JdbcTemplate jdbc = new JdbcTemplate(transactions);
AccountMapper accounts = MapperFactory.create(AccountMapper.class, jdbc);

transactions.execute(() -> {
    accounts.change(-10_000, senderId);
    accounts.change(10_000, receiverId);
    return null;
});
```

가장 바깥 `execute`는 Connection을 확보하고 autoCommit을 끈다. 안쪽 `execute`는 기존 트랜잭션에 참여한다(REQUIRED). 바깥 콜백이 정상 반환하고 rollbackOnly 표시가 없으면 commit하고 반환값을 전달한다. SQLException, RuntimeException, Error가 밖으로 전파되면 rollback 후 원래 예외를 다시 던진다. rollback 실패는 원래 예외의 suppressed 예외로 보존한다.

JdbcTemplate은 `withConnection`으로 SQL을 실행한다. 트랜잭션 안에서는 같은 관리자의 현재 스레드 Connection을 재사용하고, Statement와 ResultSet만 닫는다. 밖에서는 호출마다 연결을 열고 닫는다. 트랜잭션 관리자가 마지막에 스레드 상태를 제거하고 연결의 autoCommit을 복원한 뒤 닫는다. rollback에 실패하면 autoCommit 복원에 따른 의도치 않은 커밋을 피하기 위해 복원하지 않고 연결을 닫는다.

## 범위와 주의점

- 여러 JdbcTemplate이 참여하려면 **같은 TransactionManager 인스턴스**를 주입해야 한다. 기존 `JdbcTemplate(dataSource)` 생성자는 독립적인 관리자를 만든다.
- ThreadLocal로 연결을 보관하므로 다른 스레드나 비동기 작업으로 트랜잭션이 전파되지 않는다.
- 중첩 execute는 기존 Connection과 트랜잭션 상태에 참여한다. 안쪽 호출은 커밋·롤백·연결 종료를 하지 않는다. REQUIRES_NEW·NESTED·savepoint는 아직 지원하지 않는다.
- 참여한 execute 밖으로 SQLException·RuntimeException·Error가 나오면 rollbackOnly와 최초 실패 원인을 남긴다. 바깥 업무가 이를 잡고 정상 반환해도 전체 롤백한 뒤 UnexpectedRollbackException을 던진다. 원래 실패가 바깥까지 전파되면 그 실패 자체를 전달한다.
- 같은 execute 콜백 내부에서 예외를 잡아 경계 밖으로 전달하지 않으면 관리자는 실패를 알 수 없다. 자기 호출 역시 별도의 참여 경계를 만들지 않는다.
- 콜백의 checked 예외는 SQLException을 지원한다.
- Service 구현 메서드의 `@Transactional`은 TransactionProxyFactory로 만든 프록시를 통해 호출할 때 적용된다. 선언적 경계는 기본적으로 RuntimeException·Error를 롤백하고, checked 예외(SQLException 포함)는 커밋 후 같은 예외 객체를 다시 던진다. 명시적 `execute`의 SQLException 롤백 계약은 유지한다.

## 선언적 롤백 규칙

```java
@Transactional(rollbackFor = Exception.class,
               noRollbackFor = RecoverableBusinessException.class)
public void transfer() throws Exception { /* ... */ }
```

`rollbackFor`와 `noRollbackFor`는 `Class<? extends Throwable>[]`이며 지정 타입의 하위 타입에도 적용된다. 실제 예외에서 가장 가까운 상위 타입 규칙이 우선한다. 같은 타입을 두 속성에 모두 지정하면 `rollbackFor`가 우선한다. 일치하는 규칙이 없으면 기본 RuntimeException·Error 롤백 규칙을 따른다. 예외의 cause 체인이나 이름 패턴은 검사하지 않는다.

REQUIRED 참여자는 자기 경계의 규칙이 롤백을 요구할 때만 rollbackOnly를 남긴다. 따라서 안쪽 `noRollbackFor` 예외를 바깥 업무가 잡으면 정상 커밋할 수 있다. 예외가 바깥 선언적 경계까지 전파되면 바깥 경계의 규칙도 독립적으로 적용된다. 이미 설정된 rollbackOnly를 `noRollbackFor`가 취소하지는 않는다. 실제 SQL 실패를 세션 등이 `markRollbackOnly`로 표시했다면 checked 예외 규칙과 관계없이 롤백된다.

이는 [Spring 공식 선언적 롤백 문서](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/rolling-back.html)의 기본 예외 정책과 가장 강한 규칙 우선 원리를 따른다. Spring의 전역 기본값 변경, 이름 패턴, 클래스 어노테이션, 클래스 프록시, 격리 수준과 REQUIRED 외 전파 옵션은 구현하지 않는다.

## 커밋 전 자원 동기화

`TransactionResource`는 기본 메서드 `beforeCommit() throws SQLException`과 추상 메서드 `afterCompletion()`을 제공한다. 기존 정리 전용 람다도 그대로 사용할 수 있다. 가장 바깥 경계에서 rollbackOnly가 없고 커밋할 때만 등록 순서대로 `beforeCommit`을 한 번 호출한다. 이때 `withConnection`으로 같은 연결에서 SQL을 실행하거나 기존 자원을 가져올 수 있다. 콜백 중 새 자원 등록과 중첩 `execute`는 거부한다.

콜백 완료 후에도 rollbackOnly를 다시 확인한 뒤 JDBC commit을 실행한다. `beforeCommit` 실패(SQLException·RuntimeException·Error)는 전체 롤백하며 이후 콜백은 실행하지 않는다. 업무 실패·rollbackOnly 등 롤백 경로에서는 `beforeCommit`을 호출하지 않는다. `afterCompletion`은 성공·실패 모두에서 모든 자원을 정리하며, 이 종료 단계에서는 SQL과 트랜잭션 재진입을 거부한다. 정리 실패는 원래 실패의 suppressed 예외로 남긴다.

## 검증

`./gradlew.bat test`로 전체 테스트를 실행한다. TransactionManagerTest는 Mapper 일괄 커밋, SQL 오류·업무 예외·Error 롤백, 다음 트랜잭션 재사용, 여러 JdbcTemplate의 공유, 중첩 참여와 연결 동일성, 안쪽 성공 이후 바깥 실패 롤백, 트랜잭션 밖 자동 커밋을 검증한다.

## Service 간 REQUIRED 참여

```text
Service A 프록시 → 새 트랜잭션 시작
  → Service B 프록시 → 같은 트랜잭션 참여
  → B 종료: 연결을 유지하고 A로 복귀
  → A 종료: 전체 commit 또는 rollback 후 연결 정리
```

`ThreadLocal<TransactionState>`는 연결과 rollbackOnly 원인을 함께 보관한다. 최초 실패 원인을 유지하므로 뒤의 참여가 성공해도 롤백 표시는 사라지지 않는다.

TransactionServiceCollaborationTest는 두 Service의 일괄 커밋, 실패 전파 시 전체 롤백, 실패를 잡았을 때 UnexpectedRollbackException 발생, 실패 후 다음 독립 호출 성공을 검증한다. TransactionSelfInvocationTest는 프록시를 우회하는 자기 호출의 차이를 별도로 확인한다.
