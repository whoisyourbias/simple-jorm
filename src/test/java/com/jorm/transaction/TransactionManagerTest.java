package com.jorm.transaction;

import com.jorm.jdbc.JdbcTemplate;
import com.jorm.mapper.*;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.sql.SQLException;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class TransactionManagerTest {
    private TransactionManager transactions;
    private JdbcTemplate jdbc;
    private AccountMapper accounts;

    public interface AccountMapper {
        /** 금액과 출금 계좌 ID를 바인딩해 잔액을 차감하고 변경 행 수를 반환하도록 선언한다. */
        @Update("UPDATE accounts SET balance = balance - ? WHERE id = ?")
        int withdraw(int amount, long fromId) throws SQLException;

        /** 금액과 입금 계좌 ID를 바인딩해 잔액을 증가시키고 변경 행 수를 반환하도록 선언한다. */
        @Update("UPDATE accounts SET balance = balance + ? WHERE id = ?")
        int deposit(int amount, long toId) throws SQLException;

        /** 계좌 잔액에 금액을 더한다. 음수 금액이면 잔액이 줄어든다. */
        @Update("UPDATE accounts SET balance = balance + ? WHERE id = ?")
        int change(int amount, int id) throws SQLException;
    }

    public interface TransferService {
    	/** 출금 후 입금을 수행한다. 구현 메서드의 어노테이션은 Service 프록시가 검사한다. */
    	void transfer(long fromId, long toId, int amount) throws SQLException;
    }

    public static class TransferServiceImpl implements TransferService {
        private final AccountMapper accounts;

        /** 출금과 입금을 위임할 AccountMapper를 생성자로 전달받는다. */
        public TransferServiceImpl(AccountMapper accounts) {
            // 전달받은 값을 인스턴스 필드에 저장한다.
            this.accounts = accounts;
        }

        /** 출금 후 입금을 수행한다. 구현 메서드의 어노테이션은 Service 프록시가 검사한다. */
        @Override
        @Transactional
        public void transfer(long fromId, long toId, int amount) throws SQLException {
            // 출금 계좌의 잔액을 차감하도록 Mapper를 호출한다.
            accounts.withdraw(amount, fromId);
            // 입금 계좌의 잔액을 증가시키도록 Mapper를 호출한다.
            accounts.deposit(amount, toId);
        }
    }

    /** 실제 Service의 출금·입금 위임 결과를 검증한다. 직접 호출에는 자동 트랜잭션이 적용되지 않는다. */
    @Test
    void transferServiceMovesMoneyBetweenAccounts() throws SQLException {
        // Mapper를 주입해 실제 Service를 만든다. 이 객체 자체는 프록시가 아니다.
        TransferService service = new TransferServiceImpl(accounts);
        // Service로 이체를 요청한다. 프록시이면 호출 앞뒤에 트랜잭션을 적용한다.
        service.transfer(1L, 2L, 30);
        // 기대값과 실제 반환값·저장 데이터·호출 횟수가 같은지 검증한다.
        assertEquals(70, balance(1));
        // 기대값과 실제 반환값·저장 데이터·호출 횟수가 같은지 검증한다.
        assertEquals(130, balance(2));
    }
    
    /** 프록시를 통한 정상 이체가 두 변경을 함께 커밋하는지 검증한다. */
    @Test
    void proxyCommitsSuccessfulTransfer() throws SQLException {
        // 실제 Service 객체를 만들고 create()로 감싸는 이 지점에서 프록시를 생성한다.
        // 별도의 등록 저장소는 없으며, 반환된 프록시를 service 변수에 담아 사용한다.
        // 실제 Service를 감싸는 트랜잭션 프록시를 만든다.
        TransferService service = TransactionProxyFactory.create(TransferService.class,
                new TransferServiceImpl(accounts), transactions);
        // 프록시가 트랜잭션 경계를 적용하고 실제 Service 객체(target)에 업무를 위임한다.
        // target을 직접 호출하면 @Transactional이 있어도 트랜잭션이 자동 적용되지 않는다.
        // Controller가 있다면 이 service 프록시를 생성자로 전달한다.
        // Spring에서는 컨테이너가 프록시 생성과 주입을 처리하지만 여기서는 직접 연결한다.
        // Service로 이체를 요청한다. 프록시이면 호출 앞뒤에 트랜잭션을 적용한다.
        service.transfer(1, 2, 30);
        // 기대값과 실제 반환값·저장 데이터·호출 횟수가 같은지 검증한다.
        assertEquals(70, balance(1));
        // 기대값과 실제 반환값·저장 데이터·호출 횟수가 같은지 검증한다.
        assertEquals(130, balance(2));
    }

    /** 음수 금액으로 입금 제약 위반을 유도해 앞선 잔액 변경도 롤백하는지 검증한다. */
    @Test
    void proxyRollsBackWithdrawalWhenDepositFails() throws SQLException {
        // 실제 Service를 감싸는 트랜잭션 프록시를 만든다.
        TransferService service = TransactionProxyFactory.create(TransferService.class,
                new TransferServiceImpl(accounts), transactions);
        // 호출이 지정 예외를 던지는지 검증하고 필요하면 예외 객체를 보관한다.
        assertThrows(SQLException.class, () -> service.transfer(1, 2, -200));
        // 기대값과 실제 반환값·저장 데이터·호출 횟수가 같은지 검증한다.
        assertEquals(100, balance(1));
    }

    public interface ExampleService {
        /** 어노테이션 없는 호출에서 자동 커밋이 유지되는지 확인하고 조회 정수를 반환한다. */
        int plain() throws SQLException;
        /** 잔액 변경 후 checked 예외를 던져 롤백과 원래 예외 전달을 검증하게 한다. */
        void fail() throws Exception;
    }

    public static class ExampleServiceImpl implements ExampleService {
        private final JdbcTemplate jdbc;
        // 검증할 checked 업무 예외 객체를 미리 만든다.
        final Exception failure = new Exception("business failure");
        /** 트랜잭션 경계와 예외 전파 검증에 사용할 JdbcTemplate을 전달받는다. */
        ExampleServiceImpl(JdbcTemplate jdbc) { this.jdbc = jdbc; }
        /** 어노테이션 없는 호출에서 자동 커밋이 유지되는지 확인하고 조회 정수를 반환한다. */
        public int plain() throws SQLException {
            // SQL을 실행하고 첫 컬럼을 정수로 읽는 매퍼로 결과를 반환한다.
            return jdbc.queryForObject("SELECT 1", (rs, n) -> {
                // 행 존재·프록시 여부·자동 커밋 등 지정 조건이 참인지 확인한다.
                assertTrue(rs.getStatement().getConnection().getAutoCommit());
                // 첫 번째 조회 컬럼을 정수로 읽어 반환한다.
                return rs.getInt(1);
            });
        }
        /** 잔액 변경 후 checked 예외를 던져 롤백과 원래 예외 전달을 검증하게 한다. */
        @Transactional
        public void fail() throws Exception {
            // 지정 SQL과 인자로 데이터를 생성·변경·삭제한다.
            jdbc.update("UPDATE accounts SET balance = 50 WHERE id = 1");
            // 원래 실패를 호출자에게 전달한다. 트랜잭션 안이면 롤백 처리로 이어진다.
            throw failure;
        }
    }

    /** 어노테이션 없는 호출의 자동 커밋과 반환값을 검증한다. */
    @Test
    void plainMethodReturnsValueWithoutTransaction() throws SQLException {
        // 실제 Service를 감싸는 트랜잭션 프록시를 만든다.
        ExampleService service = TransactionProxyFactory.create(ExampleService.class,
                new ExampleServiceImpl(jdbc), transactions);
        // 기대값과 실제 반환값·저장 데이터·호출 횟수가 같은지 검증한다.
        assertEquals(1, service.plain());
    }

    /** checked 업무 예외의 원본 전달과 롤백을 검증한다. */
    @Test
    void checkedBusinessExceptionIsPreservedAndRollsBack() throws SQLException {
        // 업무 예외의 원본을 확인할 수 있도록 실제 Service 객체를 따로 보관한다.
        ExampleServiceImpl target = new ExampleServiceImpl(jdbc);
        // 실제 Service를 감싸는 트랜잭션 프록시를 만든다.
        ExampleService service = TransactionProxyFactory.create(ExampleService.class, target, transactions);
        // 원래 예외 객체가 포장되거나 교체되지 않고 그대로 전달되는지 확인한다.
        assertSame(target.failure, assertThrows(Exception.class, service::fail));
        // 기대값과 실제 반환값·저장 데이터·호출 횟수가 같은지 검증한다.
        assertEquals(100, balance(1));
    }

    /** 전달된 값을 up 필드에 저장하는 JavaBean 설정자다. */
    @BeforeEach
    void setup() throws SQLException {
        // H2 연결 공급자를 만든다.
        JdbcDataSource source = new JdbcDataSource();
        // 고유 이름의 H2 인메모리 DB URL을 지정해 테스트를 격리한다.
        source.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        // 같은 스레드의 연결 공유와 트랜잭션 경계를 관리할 객체를 만든다.
        transactions = new TransactionManager(source);
        // 지정 연결 공급자 또는 관리자로 SQL 실행기를 만든다.
        jdbc = new JdbcTemplate(transactions);
        // SQL 실행을 위임할 Mapper 프록시를 만든다.
        accounts = MapperFactory.create(AccountMapper.class, jdbc);
        // 지정 SQL과 인자로 데이터를 생성·변경·삭제한다.
        jdbc.update("CREATE TABLE accounts (id INT PRIMARY KEY, balance INT CHECK (balance >= 0))");
        // 지정 SQL과 인자로 데이터를 생성·변경·삭제한다.
        jdbc.update("INSERT INTO accounts VALUES (1, 100), (2, 100)");
    }

    /** DB_CLOSE_DELAY로 유지된 H2 데이터베이스도 SHUTDOWN으로 정리한다. */
    @AfterEach
    void cleanup() throws SQLException {
        // 지정 SQL과 인자로 데이터를 생성·변경·삭제한다.
        jdbc.update("SHUTDOWN");
    }

    /** 서로 다른 템플릿이 동일 관리자의 미커밋 값을 공유하고 함께 롤백하는지 검증한다. */
    @Test
    void separateTemplatesShareTransactionWhenUsingSameManager() throws SQLException {
        // 지정 연결 공급자 또는 관리자로 SQL 실행기를 만든다.
        JdbcTemplate other = new JdbcTemplate(transactions);
        // 호출이 지정 예외를 던지는지 검증하고 필요하면 예외 객체를 보관한다.
        assertThrows(SQLException.class, () -> transactions.execute(() -> {
            // 지정 계좌의 잔액에 증감액을 적용한다.
            accounts.change(-20, 1);
            // 기대값과 실제 반환값·저장 데이터·호출 횟수가 같은지 검증한다.
            assertEquals(Integer.valueOf(80), other.<Integer>queryForObject("SELECT balance FROM accounts WHERE id = 1",
                    (rs, n) -> rs.getInt(1)));
            // 지정 SQL과 인자로 데이터를 생성·변경·삭제한다.
            other.update("UPDATE accounts SET balance = -1 WHERE id = 2");
            // 단건 결과가 없거나 테스트용 반환값이 없음을 null로 나타낸다.
            return null;
        }));
        // 기대값과 실제 반환값·저장 데이터·호출 횟수가 같은지 검증한다.
        assertEquals(100, balance(1));
    }

    /** Error 발생 시에도 원래 예외를 전달하고 롤백하는지 검증한다. */
    @Test
    void errorRollsBackAndPreservesOriginalFailure() throws SQLException {
        // 검증할 Error 객체를 미리 만든다.
        AssertionError failure = new AssertionError("failure");
        // 원래 예외 객체가 포장되거나 교체되지 않고 그대로 전달되는지 확인한다.
        assertSame(failure, assertThrows(AssertionError.class, () -> transactions.execute(() -> {
            // 지정 계좌의 잔액에 증감액을 적용한다.
            accounts.change(-10, 1);
            // 원래 실패를 호출자에게 전달한다. 트랜잭션 안이면 롤백 처리로 이어진다.
            throw failure;
        })));
        // 기대값과 실제 반환값·저장 데이터·호출 횟수가 같은지 검증한다.
        assertEquals(100, balance(1));
    }

    /** 지정 계좌의 잔액을 단건 조회해 검증에 사용할 정수로 반환한다. */
    private int balance(int id) throws SQLException {
        // SQL을 실행하고 첫 컬럼을 정수로 읽는 매퍼로 결과를 반환한다.
        return jdbc.queryForObject("SELECT balance FROM accounts WHERE id = ?", (rs, n) -> rs.getInt(1), id);
    }

    /** 여러 Mapper의 일괄 커밋, 트랜잭션 내 조회와 반환값을 검증한다. */
    @Test
    void commitsMultipleMapperCallsAndReturnsResult() throws SQLException {
        // 준비한 DDL·변경 SQL 또는 업무 콜백을 실행한다.
        int result = transactions.execute(() -> {
            // 지정 계좌의 잔액에 증감액을 적용한다.
            accounts.change(-30, 1);
            // 지정 계좌의 잔액에 증감액을 적용한다.
            accounts.change(30, 2);
            // 기대값과 실제 반환값·저장 데이터·호출 횟수가 같은지 검증한다.
            assertEquals(70, balance(1));
            // 기대값과 실제 반환값·저장 데이터·호출 횟수가 같은지 검증한다.
            assertEquals(2, jdbc.query("SELECT * FROM accounts", (rs, n) -> rs.getInt(1)).size());
            // 트랜잭션 실행기가 콜백 반환값도 전달하는지 확인할 고정 값을 반환한다.
            return 42;
        });
        // 기대값과 실제 반환값·저장 데이터·호출 횟수가 같은지 검증한다.
        assertEquals(42, result);
        // 기대값과 실제 반환값·저장 데이터·호출 횟수가 같은지 검증한다.
        assertEquals(70, balance(1));
        // 기대값과 실제 반환값·저장 데이터·호출 횟수가 같은지 검증한다.
        assertEquals(130, balance(2));
    }

    /** 뒤 SQL 실패 시 앞선 변경까지 롤백하는지 검증한다. */
    @Test
    void sqlFailureRollsBackEarlierMapperCall() throws SQLException {
        // 호출이 지정 예외를 던지는지 검증하고 필요하면 예외 객체를 보관한다.
        assertThrows(SQLException.class, () -> transactions.execute(() -> {
            // 지정 계좌의 잔액에 증감액을 적용한다.
            accounts.change(-30, 1);
            // 지정 계좌의 잔액에 증감액을 적용한다.
            accounts.change(-200, 2);
            // 단건 결과가 없거나 테스트용 반환값이 없음을 null로 나타낸다.
            return null;
        }));
        // 기대값과 실제 반환값·저장 데이터·호출 횟수가 같은지 검증한다.
        assertEquals(100, balance(1));
        // 기대값과 실제 반환값·저장 데이터·호출 횟수가 같은지 검증한다.
        assertEquals(100, balance(2));
    }

    /** 업무 예외 롤백 후 다음 트랜잭션이 정상 작동하는지 검증한다. */
    @Test
    void runtimeFailureRollsBackAndNextTransactionWorks() throws SQLException {
        // 검증할 업무 예외 객체를 미리 만든다.
        IllegalStateException failure = new IllegalStateException("business failure");
        // 원래 예외 객체가 포장되거나 교체되지 않고 그대로 전달되는지 확인한다.
        assertSame(failure, assertThrows(IllegalStateException.class, () -> transactions.execute(() -> {
            // 지정 계좌의 잔액에 증감액을 적용한다.
            accounts.change(-10, 1);
            // 원래 실패를 호출자에게 전달한다. 트랜잭션 안이면 롤백 처리로 이어진다.
            throw failure;
        })));
        // 기대값과 실제 반환값·저장 데이터·호출 횟수가 같은지 검증한다.
        assertEquals(100, balance(1));
        // 준비한 DDL·변경 SQL 또는 업무 콜백을 실행한다.
        transactions.execute(() -> { accounts.change(10, 1); return null; });
        // 기대값과 실제 반환값·저장 데이터·호출 횟수가 같은지 검증한다.
        assertEquals(110, balance(1));
    }

    /** 중첩 execute는 같은 연결을 공유하고 안쪽 반환 이후의 작업도 바깥 롤백에 포함된다. */
    @Test
    void nestedTransactionSharesConnectionAndDoesNotCommitEarly() throws SQLException {
        SQLException failure = new SQLException("outer failed after inner success");
        assertSame(failure, assertThrows(SQLException.class, () -> transactions.execute(() -> {
            accounts.change(-10, 1);
            transactions.withConnection(outer -> transactions.execute(() -> {
                transactions.withConnection(inner -> { assertSame(outer, inner); return null; });
                accounts.change(10, 2);
                return null;
            }));
            // 안쪽 호출이 연결을 닫거나 미리 커밋하면 이 검증이 실패한다.
            accounts.change(-5, 1);
            throw failure;
        })));
        assertEquals(100, balance(1));
        assertEquals(100, balance(2));
    }

    /** 여러 단계의 참여에서 SQL 예외·업무 예외·Error를 잡더라도 최초 롤백 표시를 유지한다. */
    @Test
    void caughtDeepFailureKeepsRollbackOnlyAcrossLaterSuccessfulParticipation() throws SQLException {
        // 실패 종류가 달라도 같은 정책을 적용해야 한다.
        Throwable[] failures = {new SQLException("sql"), new IllegalStateException("business"),
                new AssertionError("error")};
        for (Throwable original : failures) {
            UnexpectedRollbackException actual = assertThrows(UnexpectedRollbackException.class,
                    () -> transactions.execute(() -> {
                        accounts.change(-10, 1);
                        // 세 번째 깊이의 실패를 두 번째 호출에서 잡는다.
                        transactions.execute(() -> {
                            try {
                                transactions.execute(() -> {
                                    accounts.change(10, 2);
                                    if (original instanceof SQLException sql) throw sql;
                                    if (original instanceof RuntimeException runtime) throw runtime;
                                    throw (Error) original;
                                });
                            } catch (SQLException | RuntimeException | Error expected) {
                                assertSame(original, expected);
                            }
                            return null;
                        });
                        // 뒤의 성공한 참여가 rollbackOnly를 초기화하면 안 된다.
                        transactions.execute(() -> { accounts.change(5, 1); return null; });
                        return null;
                    }));
            assertSame(original, actual.getCause());
            assertEquals(100, balance(1));
            assertEquals(100, balance(2));
        }
    }

    /** 트랜잭션 밖 Mapper 변경의 자동 커밋을 검증한다. */
    @Test
    void outsideTransactionChangesStillAutoCommit() throws SQLException {
        // 지정 계좌의 잔액에 증감액을 적용한다.
        accounts.change(10, 1);
        // 기대값과 실제 반환값·저장 데이터·호출 횟수가 같은지 검증한다.
        assertEquals(110, balance(1));
    }
}
