package com.jorm.transaction;

import com.jorm.jdbc.JdbcTemplate;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** 실제 DB 변경과 예외 원본으로 선언적 기본 정책과 REQUIRED 참여를 검증한다. */
class TransactionRollbackRulesTest {
    private Connection keeper;
    private TransactionManager transactions;
    private JdbcTemplate jdbc;
    private RulesTarget target;
    private RulesService service;

    public interface RulesService {
        void checked() throws Exception;
        void sql() throws SQLException;
        void runtime() throws SQLException;
        void error() throws SQLException;
        void rollbackChecked() throws Exception;
        void noRollbackRuntime() throws SQLException;
        void specificNoRollback() throws SQLException;
        void specificRollback() throws SQLException;
        void conflictingRules() throws SQLException;
        void unmatchedRule() throws SQLException;
    }

    public static class RulesTarget implements RulesService {
        private final JdbcTemplate jdbc;
        final Exception checkedFailure = new Exception("business");
        final SQLException sqlFailure = new SQLException("business SQL exception");
        final IllegalArgumentException runtimeFailure = new IllegalArgumentException("business runtime");
        final AssertionError errorFailure = new AssertionError("business error");

        RulesTarget(JdbcTemplate jdbc) { this.jdbc = jdbc; }

        @Transactional
        public void checked() throws Exception { write(); throw checkedFailure; }

        @Transactional
        public void sql() throws SQLException { write(); throw sqlFailure; }

        @Transactional
        public void runtime() throws SQLException { write(); throw runtimeFailure; }

        @Transactional
        public void error() throws SQLException { write(); throw errorFailure; }

        @Transactional(rollbackFor = Exception.class)
        public void rollbackChecked() throws Exception { write(); throw checkedFailure; }

        @Transactional(noRollbackFor = IllegalArgumentException.class)
        public void noRollbackRuntime() throws SQLException { write(); throw runtimeFailure; }

        @Transactional(rollbackFor = Exception.class, noRollbackFor = IllegalArgumentException.class)
        public void specificNoRollback() throws SQLException { write(); throw runtimeFailure; }

        @Transactional(rollbackFor = IllegalArgumentException.class, noRollbackFor = Exception.class)
        public void specificRollback() throws SQLException { write(); throw runtimeFailure; }

        @Transactional(rollbackFor = IllegalArgumentException.class, noRollbackFor = IllegalArgumentException.class)
        public void conflictingRules() throws SQLException { write(); throw runtimeFailure; }

        @Transactional(noRollbackFor = SQLException.class)
        public void unmatchedRule() throws SQLException { write(); throw runtimeFailure; }

        private void write() throws SQLException { jdbc.update("INSERT INTO items VALUES(1)"); }
    }

    @BeforeEach
    void setUp() throws SQLException {
        JdbcDataSource source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:rules-" + UUID.randomUUID());
        keeper = source.getConnection();
        transactions = new TransactionManager(source);
        jdbc = new JdbcTemplate(transactions);
        jdbc.update("CREATE TABLE items(id INT)");
        target = new RulesTarget(jdbc);
        service = TransactionProxyFactory.create(RulesService.class, target, transactions);
    }

    @AfterEach
    void tearDown() throws SQLException { keeper.close(); }

    @Test
    void defaultCheckedExceptionCommitsAndPreservesOriginal() throws SQLException {
        assertSame(target.checkedFailure, assertThrows(Exception.class, service::checked));
        assertEquals(1, count());
    }

    @Test
    void defaultSqlBusinessExceptionCommitsAndPreservesOriginal() throws SQLException {
        assertSame(target.sqlFailure, assertThrows(SQLException.class, service::sql));
        assertEquals(1, count());
    }

    @Test
    void defaultRuntimeExceptionRollsBackAndPreservesOriginal() throws SQLException {
        assertSame(target.runtimeFailure, assertThrows(IllegalArgumentException.class, service::runtime));
        assertEquals(0, count());
    }

    @Test
    void defaultErrorRollsBackAndPreservesOriginal() throws SQLException {
        assertSame(target.errorFailure, assertThrows(AssertionError.class, service::error));
        assertEquals(0, count());
    }

    @Test
    void caughtCheckedExceptionDoesNotMarkRequiredParticipantRollbackOnly() throws SQLException {
        transactions.execute(() -> {
            assertSame(target.checkedFailure, assertThrows(Exception.class, service::checked));
            jdbc.update("INSERT INTO items VALUES(2)");
            return null;
        });
        assertEquals(2, count());
    }

    private int count() throws SQLException {
        return jdbc.queryForObject("SELECT COUNT(*) FROM items", (rows, index) -> rows.getInt(1));
    }

    @Test
    void rollbackForIncludesCheckedSubtypeAndPreservesOriginal() throws SQLException {
        assertSame(target.checkedFailure, assertThrows(Exception.class, service::rollbackChecked));
        assertEquals(0, count());
    }

    @Test
    void noRollbackForRuntimeCommitsAndPreservesOriginal() throws SQLException {
        assertSame(target.runtimeFailure, assertThrows(IllegalArgumentException.class, service::noRollbackRuntime));
        assertEquals(1, count());
    }

    @Test
    void moreSpecificNoRollbackRuleWins() throws SQLException {
        assertSame(target.runtimeFailure, assertThrows(IllegalArgumentException.class, service::specificNoRollback));
        assertEquals(1, count());
    }

    @Test
    void moreSpecificRollbackRuleWins() throws SQLException {
        assertSame(target.runtimeFailure, assertThrows(IllegalArgumentException.class, service::specificRollback));
        assertEquals(0, count());
    }

    @Test
    void rollbackRuleWinsWhenBothSpecifySameType() throws SQLException {
        assertSame(target.runtimeFailure, assertThrows(IllegalArgumentException.class, service::conflictingRules));
        assertEquals(0, count());
    }

    @Test
    void unmatchedRulesFallBackToRuntimeRollback() throws SQLException {
        assertSame(target.runtimeFailure, assertThrows(IllegalArgumentException.class, service::unmatchedRule));
        assertEquals(0, count());
    }

    @Test
    void caughtNoRollbackExceptionDoesNotMarkRequiredParticipantRollbackOnly() throws SQLException {
        transactions.execute(() -> {
            assertThrows(IllegalArgumentException.class, service::noRollbackRuntime);
            jdbc.update("INSERT INTO items VALUES(2)");
            return null;
        });
        assertEquals(2, count());
    }

    @Test
    void noRollbackRuleCannotClearExistingRollbackOnly() throws SQLException {
        SQLException first = new SQLException("first");
        UnexpectedRollbackException failure = assertThrows(UnexpectedRollbackException.class,
                () -> transactions.execute(() -> {
                    transactions.markRollbackOnly(first);
                    assertThrows(IllegalArgumentException.class, service::noRollbackRuntime);
                    return null;
                }));
        assertSame(first, failure.getCause());
        assertEquals(0, count());
    }
}
