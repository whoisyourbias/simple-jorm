package com.jorm.transaction;

import com.jorm.jdbc.JdbcTemplate;
import com.jorm.mapper.MapperFactory;
import com.jorm.mapper.Update;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 프록시 진입 위치에 따라 자기 호출의 트랜잭션 적용 결과가 달라지는지 비교한다. */
class TransactionSelfInvocationTest {
    private Connection keepAlive;
    private JdbcTemplate jdbc;
    private TransferService service;

    public interface AccountMapper {
        /** 금액을 양수로 전달해 출금하며 변경 행 수를 반환한다. */
        @Update("UPDATE accounts SET balance = balance - ? WHERE id = ?")
        int withdraw(int amount, long id) throws SQLException;

        /** 입금 후 잔액이 테이블의 상한을 넘으면 DB가 SQLException을 발생시킨다. */
        @Update("UPDATE accounts SET balance = balance + ? WHERE id = ?")
        int deposit(int amount, long id) throws SQLException;
    }

    public interface TransferService {
        /** 외부에서 직접 호출할 트랜잭션 업무다. */
        void transfer() throws SQLException;

        /** 트랜잭션이 없는 진입점에서 this로 transfer를 호출한다. */
        void transferViaSelf() throws SQLException;

        /** 바깥 메서드가 트랜잭션을 시작한 상태에서 this로 transfer를 호출한다. */
        void transferViaTransactionalSelf() throws SQLException;
    }

    public static class TransferServiceImpl implements TransferService {
        private final AccountMapper accounts;

        /** 실제 SQL 호출을 위임할 Mapper를 보관한다. */
        public TransferServiceImpl(AccountMapper accounts) {
            this.accounts = accounts;
        }

        /** 출금은 성공하지만 입금이 상한 제약을 위반하도록 동일한 업무를 실행한다. */
        @Override
        @Transactional
        public void transfer() throws SQLException {
            // 보내는 계좌의 잔액은 100에서 70으로 내려가므로 성공한다.
            accounts.withdraw(30, 1);
            // 받는 계좌는 130이 되어 상한 120을 넘으므로 실패한다.
            accounts.deposit(30, 2);
        }

        /** this는 프록시가 아닌 실제 구현체이므로 내부 호출은 핸들러를 다시 통과하지 않는다. */
        @Override
        public void transferViaSelf() throws SQLException {
            // 안쪽 메서드의 @Transactional을 검사할 프록시 진입이 없다.
            this.transfer();
        }

        /** 자기 호출이 새 트랜잭션을 만들지는 않지만 이미 시작된 바깥 트랜잭션은 유지된다. */
        @Override
        @Transactional
        public void transferViaTransactionalSelf() throws SQLException {
            // 바깥 프록시가 등록한 Connection을 내부 SQL도 사용한다.
            this.transfer();
        }
    }

    /** 각 비교가 같은 잔액과 실패 조건에서 시작하도록 독립적인 H2 DB를 준비한다. */
    @BeforeEach
    void setUp() throws SQLException {
        // 테스트마다 DB 이름을 달리해 다른 테스트의 잔액이 섞이지 않게 한다.
        JdbcDataSource source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:self-invocation-" + UUID.randomUUID());
        // 템플릿이 연결을 닫아도 테스트가 끝날 때까지 인메모리 DB를 유지한다.
        keepAlive = source.getConnection();
        // SQL 실행기와 Service 프록시에 반드시 같은 관리자를 전달한다.
        TransactionManager transactions = new TransactionManager(source);
        jdbc = new JdbcTemplate(transactions);
        // 입금 실패를 재현하기 위한 테스트 전용 잔액 상한이다.
        jdbc.update("CREATE TABLE accounts (id BIGINT PRIMARY KEY, "
                + "balance INT CHECK (balance BETWEEN 0 AND 120))");
        jdbc.update("INSERT INTO accounts VALUES (1, 100), (2, 100)");
        // 실제 Service를 만든 뒤 프록시로 감싸 외부 호출의 진입점으로 사용한다.
        AccountMapper accounts = MapperFactory.create(AccountMapper.class, jdbc);
        TransferService target = new TransferServiceImpl(accounts);
        service = TransactionProxyFactory.create(TransferService.class, target, transactions);
    }

    /** 유지 연결을 닫으면 다른 연결이 없는 인메모리 DB도 제거된다. */
    @AfterEach
    void tearDown() throws SQLException {
        if (keepAlive != null) {
            keepAlive.close();
        }
    }

    /** 프록시에서 @Transactional 메서드로 진입하면 입금 실패 시 출금도 롤백된다. */
    @Test
    void externalCallRollsBackWithdrawalWhenDepositFails() throws SQLException {
        // 프록시 → 트랜잭션 시작 → target.transfer() → 입금 실패 → 전체 롤백.
        assertThrows(SQLException.class, service::transfer);
        assertBalances(100, 100);
    }

    /** 어노테이션 없는 바깥 메서드에서 자기 호출하면 출금이 자동 커밋되어 남는다. */
    @Test
    void selfInvocationBypassesTransactionAndLeavesWithdrawalCommitted() throws SQLException {
        // 프록시 → 비트랜잭션 메서드 → this.transfer(): 안쪽 어노테이션은 검사되지 않는다.
        assertThrows(SQLException.class, service::transferViaSelf);
        // 출금은 이미 커밋되었고 실패한 입금만 반영되지 않아 금액이 일치하지 않는다.
        assertBalances(70, 100);
    }

    /** 바깥 메서드에 트랜잭션이 있으면 자기 호출의 SQL도 그 연결에 포함된다. */
    @Test
    void selfInvocationUsesExistingOuterTransaction() throws SQLException {
        // 바깥 프록시에서 시작한 트랜잭션으로 두 SQL을 함께 롤백한다.
        assertThrows(SQLException.class, service::transferViaTransactionalSelf);
        assertBalances(100, 100);
    }

    /** 업무 호출이 끝난 뒤 새 조회로 두 계좌의 실제 저장 잔액을 확인한다. */
    private void assertBalances(int sender, int receiver) throws SQLException {
        // 결과를 int로 받아 JUnit의 오버로드와 제네릭 타입 추론이 충돌하지 않게 한다.
        int senderBalance = jdbc.queryForObject("SELECT balance FROM accounts WHERE id = 1",
                (rs, row) -> rs.getInt(1));
        int receiverBalance = jdbc.queryForObject("SELECT balance FROM accounts WHERE id = 2",
                (rs, row) -> rs.getInt(1));
        assertEquals(sender, senderBalance);
        assertEquals(receiver, receiverBalance);
    }
}
