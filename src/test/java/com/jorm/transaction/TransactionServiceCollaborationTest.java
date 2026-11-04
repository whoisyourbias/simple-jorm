package com.jorm.transaction;

import com.jorm.jdbc.JdbcTemplate;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertSame;

/** 같은 관리자를 쓰는 두 Service 프록시가 협력할 때 REQUIRED 참여와 전체 롤백을 확인한다. */
class TransactionServiceCollaborationTest {
    private Connection keepAlive;
    private JdbcTemplate jdbc;
    private TransferService transfer;
    private DepositService deposit;
    private DepositServiceImpl depositTarget;

    public interface DepositService {
        /** 다른 Service에서 호출할 입금 업무다. */
        void deposit() throws SQLException;

        /** 입금 후 실패해 안쪽 변경까지 취소되는지 확인한다. */
        void depositThenFail() throws SQLException;
    }

    public interface TransferService {
        /** 바깥 트랜잭션 없이 입금 Service 프록시를 호출한다. */
        void withoutOuterTransaction() throws SQLException;

        /** 바깥 트랜잭션을 시작한 뒤 입금 Service 프록시를 참여시킨다. */
        void withOuterTransaction() throws SQLException;

        /** 안쪽 실패를 밖으로 전파한다. */
        void withInnerFailure() throws SQLException;

        /** 안쪽 실패를 잡아도 rollbackOnly가 유지되는지 확인한다. */
        void catchingInnerFailure() throws SQLException;
    }

    public static class DepositServiceImpl implements DepositService {
        private final JdbcTemplate jdbc;

        /** 다른 Service와 같은 트랜잭션 관리자를 사용하는 SQL 실행기를 받는다. */
        public DepositServiceImpl(JdbcTemplate jdbc) {
            this.jdbc = jdbc;
        }

        /** 원래 예외가 프록시를 통과해 그대로 전달되는지 비교할 실패 객체다. */
        final SQLException failure = new SQLException("deposit failed");

        /** 입금까지 실행한 뒤 업무 실패를 발생시킨다. */
        @Override
        @Transactional(rollbackFor = SQLException.class)
        public void depositThenFail() throws SQLException {
            jdbc.update("UPDATE accounts SET balance = balance + 30 WHERE id = 2");
            throw failure;
        }

        /** 프록시가 진입 시 트랜잭션을 시작하고 받는 계좌에 30을 입금한다. */
        @Override
        @Transactional
        public void deposit() throws SQLException {
            jdbc.update("UPDATE accounts SET balance = balance + 30 WHERE id = 2");
        }
    }

    public static class TransferServiceImpl implements TransferService {
        private final JdbcTemplate jdbc;
        private final DepositService deposit;

        /** 실제 입금 객체가 아닌 입금 프록시를 주입해 내부에서도 프록시를 통과하게 한다. */
        public TransferServiceImpl(JdbcTemplate jdbc, DepositService deposit) {
            this.jdbc = jdbc;
            this.deposit = deposit;
        }

        /** 안쪽 프록시의 실패가 바깥 트랜잭션까지 전파된다. */
        @Override
        @Transactional(rollbackFor = SQLException.class)
        public void withInnerFailure() throws SQLException {
            jdbc.update("UPDATE accounts SET balance = balance - 30 WHERE id = 1");
            deposit.depositThenFail();
        }

        /** 실패를 잡고 추가 SQL까지 실행해도 안쪽에서 남긴 롤백 표시를 지울 수 없다. */
        @Override
        @Transactional
        public void catchingInnerFailure() throws SQLException {
            jdbc.update("UPDATE accounts SET balance = balance - 30 WHERE id = 1");
            try {
                deposit.depositThenFail();
            } catch (SQLException expected) {
                // 업무가 계속되어도 가장 바깥 경계에서 전체 롤백해야 한다.
                jdbc.update("UPDATE accounts SET balance = balance + 5 WHERE id = 1");
            }
        }

        /** 출금은 자동 커밋되고 입금 프록시는 자신의 트랜잭션을 새로 시작한다. */
        @Override
        public void withoutOuterTransaction() throws SQLException {
            jdbc.update("UPDATE accounts SET balance = balance - 30 WHERE id = 1");
            deposit.deposit();
        }

        /** 출금 후 입금 프록시가 같은 트랜잭션에 참여하게 한다. */
        @Override
        @Transactional
        public void withOuterTransaction() throws SQLException {
            jdbc.update("UPDATE accounts SET balance = balance - 30 WHERE id = 1");
            // 자기 호출과 달리 입금 프록시의 핸들러가 실행되므로 execute()에 다시 진입한다.
            deposit.deposit();
        }
    }

    /** 독립적인 H2 DB와 동일 관리자를 공유하는 두 Service 프록시를 만든다. */
    @BeforeEach
    void setUp() throws SQLException {
        JdbcDataSource source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:service-collaboration-" + UUID.randomUUID());
        // 템플릿의 임시 연결이 닫혀도 테스트 동안 DB를 유지한다.
        keepAlive = source.getConnection();
        TransactionManager transactions = new TransactionManager(source);
        jdbc = new JdbcTemplate(transactions);
        jdbc.update("CREATE TABLE accounts (id INT PRIMARY KEY, balance INT)");
        jdbc.update("INSERT INTO accounts VALUES (1, 100), (2, 100)");
        // 입금 프록시를 먼저 만들고 이체 구현체에 전달한다.
        depositTarget = new DepositServiceImpl(jdbc);
        deposit = TransactionProxyFactory.create(DepositService.class,
                depositTarget, transactions);
        transfer = TransactionProxyFactory.create(TransferService.class,
                new TransferServiceImpl(jdbc, deposit), transactions);
    }

    /** 테스트의 유지 연결을 닫아 인메모리 DB를 정리한다. */
    @AfterEach
    void tearDown() throws SQLException {
        if (keepAlive != null) {
            keepAlive.close();
        }
    }

    /** 바깥 트랜잭션이 없으면 입금 프록시는 정상적으로 트랜잭션을 시작한다. */
    @Test
    void innerProxyCanStartTransactionWhenOuterMethodIsNotTransactional() throws SQLException {
        transfer.withoutOuterTransaction();
        assertBalances(70, 130);
    }

    /** 안쪽 메서드가 기존 트랜잭션에 참여해 두 변경을 함께 확정한다. */
    @Test
    void innerProxyJoinsOuterTransactionAndBothChangesCommit() throws SQLException {
        transfer.withOuterTransaction();
        assertBalances(70, 130);
    }

    /** 안쪽 실패를 전파하면 출금과 입금 모두 취소되고 원래 예외가 전달된다. */
    @Test
    void propagatedInnerFailureRollsBackBothServices() throws SQLException {
        assertSame(depositTarget.failure, assertThrows(SQLException.class, transfer::withInnerFailure));
        assertBalances(100, 100);
        // 실패한 트랜잭션 상태가 제거되어 다음 독립 호출은 성공한다.
        deposit.deposit();
        assertBalances(100, 130);
    }

    /** 안쪽 실패를 삼켜도 정상 커밋으로 오인하지 않도록 롤백 사실을 예외로 알린다. */
    @Test
    void caughtInnerFailureStillRollsBackAndReportsFailure() throws SQLException {
        UnexpectedRollbackException failure = assertThrows(UnexpectedRollbackException.class,
                transfer::catchingInnerFailure);
        assertSame(depositTarget.failure, failure.getCause());
        assertBalances(100, 100);
        deposit.deposit();
        assertBalances(100, 130);
    }

    /** Service 호출 후 실제 저장된 두 잔액을 읽어 검증한다. */
    private void assertBalances(int expectedSender, int expectedReceiver) throws SQLException {
        int sender = jdbc.queryForObject("SELECT balance FROM accounts WHERE id = 1",
                (rs, row) -> rs.getInt(1));
        int receiver = jdbc.queryForObject("SELECT balance FROM accounts WHERE id = 2",
                (rs, row) -> rs.getInt(1));
        assertEquals(expectedSender, sender);
        assertEquals(expectedReceiver, receiver);
    }
}
