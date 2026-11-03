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

    /** 생성 키가 null이면 SQLException을 전달하고 같은 트랜잭션의 INSERT를 롤백하는지 검증한다. */
    @Test
    void rejectsNullKeyAndRollsBackInsertedRow() throws SQLException {
        assertThrows(SQLException.class, () -> transactions.execute(() ->
                jdbc.insertReturningKey("INSERT INTO keys_test (name) VALUES (?)", "id", Long.class, "Alice")));
        assertEquals(0L, count());
    }

    /** 삽입 행 수가 0이면 생성 키 INSERT를 실패로 처리하고 테이블을 비워 둔 채 유지하는지 검증한다. */
    @Test
    void rejectsInsertThatDidNotInsertAnyRow() throws SQLException {
        assertThrows(SQLException.class, () -> jdbc.insertReturningKey(
                "INSERT INTO keys_test (id) SELECT id FROM keys_test WHERE 1 = 0", "id", Long.class));
        assertEquals(0L, count());
    }

    /** Integer 범위를 넘는 생성 키를 잘라 반환하지 않고 SQLException과 함께 INSERT를 롤백하는지 검증한다. */
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
