package com.jorm.jdbc;

import com.jorm.transaction.TransactionManager;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** 생성 키 회수 중 실패도 트랜잭션에 전달되어야 한다. */
class JdbcTemplateGeneratedKeyTest {
    private Connection keepAlive;
    private JdbcTemplate jdbc;
    private TransactionManager transactions;

    @BeforeEach
    void setUp() throws SQLException {
        JdbcDataSource source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:generated-key-" + UUID.randomUUID());
        keepAlive = source.getConnection();
        transactions = new TransactionManager(source);
        jdbc = new JdbcTemplate(transactions);
        jdbc.update("CREATE TABLE keys_test (id BIGINT, name VARCHAR(50))");
    }

    @AfterEach
    void tearDown() throws SQLException {
        if (keepAlive != null) keepAlive.close();
    }

    @Test
    void rejectsNullKeyAndRollsBackInsertedRow() throws SQLException {
        assertThrows(SQLException.class, () -> transactions.execute(() ->
                jdbc.insertReturningKey("INSERT INTO keys_test (name) VALUES (?)", "id", Long.class, "Alice")));
        assertEquals(0L, count());
    }

    @Test
    void rejectsInsertThatDidNotInsertAnyRow() throws SQLException {
        assertThrows(SQLException.class, () -> jdbc.insertReturningKey(
                "INSERT INTO keys_test (id) SELECT id FROM keys_test WHERE 1 = 0", "id", Long.class));
        assertEquals(0L, count());
    }

    @Test
    void rejectsOutOfRangeKeyAndRollsBackInsteadOfTruncating() throws SQLException {
        assertThrows(SQLException.class, () -> transactions.execute(() ->
                jdbc.insertReturningKey("INSERT INTO keys_test (id) VALUES (?)", "id", Integer.class, 2147483648L)));
        assertEquals(0L, count());
    }

    private long count() throws SQLException {
        return jdbc.queryForObject("SELECT COUNT(*) FROM keys_test", (rs, row) -> rs.getLong(1));
    }
}
