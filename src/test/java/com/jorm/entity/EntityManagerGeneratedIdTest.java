package com.jorm.entity;

import com.jorm.jdbc.JdbcTemplate;
import com.jorm.transaction.TransactionManager;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.lang.reflect.Method;
import javax.sql.DataSource;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/** 생성 키 반영을 실제 DB 저장 결과와 비교한다. */
class EntityManagerGeneratedIdTest {
    private Connection keepAlive;
    private JdbcTemplate jdbc;
    private EntityManager entities;
    private TransactionManager transactions;
    private JdbcDataSource source;

    @Table("users")
    private static class User {
        private String name;
        @Id @GeneratedValue @Column("user_id")
        private Long id;
    }

    @Table("users")
    private static class IntegerUser {
        @Id @GeneratedValue @Column("user_id")
        private Integer id;
    }

    @Table("users")
    private static class Misplaced {
        @Id private Long id;
        @GeneratedValue private Long name;
    }

    @Table("users")
    private static class Unsupported {
        @Id @GeneratedValue private String id;
    }

    @Table("users")
    private static class Primitive {
        @Id @GeneratedValue private long id;
    }

    @Table("users")
    private static class StaticGenerated {
        @Id @GeneratedValue private static Long generated;
        @Id private Long id;
    }

    @BeforeEach
    void setUp() throws SQLException {
        source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:generated-" + UUID.randomUUID());
        keepAlive = source.getConnection();
        transactions = new TransactionManager(source);
        jdbc = new JdbcTemplate(transactions);
        entities = new EntityManager(jdbc);
        jdbc.update("CREATE TABLE users (user_id BIGINT GENERATED ALWAYS AS IDENTITY "
                + "(START WITH 41) PRIMARY KEY, name VARCHAR(100) UNIQUE)");
    }

    @AfterEach
    void tearDown() throws SQLException {
        if (keepAlive != null) keepAlive.close();
    }

    @Test
    void reflectsGeneratedLongIdAndCanFindStoredEntity() throws SQLException {
        User user = new User();
        user.name = "O'Brien";
        assertEquals(1, entities.persist(user));
        assertEquals(41L, user.id);
        assertEquals("O'Brien", entities.find(User.class, user.id).name);
        Long storedId = jdbc.queryForObject("SELECT user_id FROM users",
                (rs, row) -> rs.getLong(1));
        assertEquals(41L, storedId);
    }

    @Test
    void repeatedInsertsReflectIndependentKeys() throws SQLException {
        User first = new User();
        User second = new User();
        entities.persist(first);
        entities.persist(second);
        assertEquals(41L, first.id);
        assertEquals(42L, second.id);
    }

    @Test
    void supportsIntegerIdAndDefaultValuesInsert() throws SQLException {
        IntegerUser user = new IntegerUser();
        assertEquals(1, entities.persist(user));
        assertEquals(41, user.id);
        assertEquals(1L, count());
    }

    @Test
    void rejectsPresetGeneratedIdBeforeInsert() throws SQLException {
        User user = new User();
        user.id = 7L;
        assertThrows(IllegalArgumentException.class, () -> entities.persist(user));
        assertEquals(7L, user.id);
        assertEquals(0L, count());
    }

    @Test
    void rejectsGeneratedValueOnNonIdField() {
        assertThrows(IllegalArgumentException.class, () -> new EntityMetadata(Misplaced.class));
    }

    @Test
    void rejectsUnsupportedGeneratedIdType() {
        assertThrows(IllegalArgumentException.class, () -> new EntityMetadata(Unsupported.class));
    }

    @Test
    void rejectsPrimitiveGeneratedId() {
        assertThrows(IllegalArgumentException.class, () -> new EntityMetadata(Primitive.class));
    }

    @Test
    void rejectsStaticGeneratedIdInsteadOfIgnoringIt() {
        assertThrows(IllegalArgumentException.class, () -> new EntityMetadata(StaticGenerated.class));
    }

    @Test
    void missingGeneratedKeyPropagatesAndRollsBackInsert() throws SQLException {
        assertInvalidKeyRollsBack(false);
    }

    @Test
    void nullGeneratedKeyPropagatesAndRollsBackInsert() throws SQLException {
        assertInvalidKeyRollsBack(true);
    }

    @Test
    void sqlFailureDoesNotAssignId() throws SQLException {
        User first = new User();
        first.name = "same";
        entities.persist(first);
        User duplicate = new User();
        duplicate.name = "same";
        assertThrows(SQLException.class, () -> entities.persist(duplicate));
        assertNull(duplicate.id);
        assertEquals(1L, count());
    }

    @Test
    void rollsBackDatabaseRowButRetainsReflectedId() throws SQLException {
        User user = new User();
        assertThrows(SQLException.class, () -> transactions.execute(() -> {
            entities.persist(user);
            assertEquals(41L, user.id);
            assertNotNull(entities.find(User.class, user.id));
            throw new SQLException("rollback");
        }));
        assertEquals(41L, user.id);
        assertEquals(0L, count());
    }

    @Test
    void commitsGeneratedIdInsert() throws SQLException {
        User user = new User();
        transactions.execute(() -> entities.persist(user));
        assertNotNull(entities.find(User.class, user.id));
    }

    @Test
    void rejectsOverflowInsteadOfTruncatingIntegerId() throws SQLException {
        jdbc.update("ALTER TABLE users ALTER COLUMN user_id RESTART WITH 2147483648");
        IntegerUser user = new IntegerUser();
        assertThrows(SQLException.class, () -> transactions.execute(() -> entities.persist(user)));
        assertNull(user.id);
        assertEquals(0L, count());
    }

    private long count() throws SQLException {
        return jdbc.queryForObject("SELECT COUNT(*) FROM users", (rs, row) -> rs.getLong(1));
    }

    /** H2의 생성 키 응답만 바꾸고 INSERT와 롤백은 실제 DB로 검증한다. */
    private void assertInvalidKeyRollsBack(boolean nullKey) throws SQLException {
        DataSource keySource = (DataSource) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] {DataSource.class}, (proxy, method, args) -> {
                    Object result = invoke(source, method, args);
                    if (!method.getName().equals("getConnection")) return result;
                    Connection connection = (Connection) result;
                    return Proxy.newProxyInstance(getClass().getClassLoader(),
                            new Class<?>[] {Connection.class}, (p, m, a) -> {
                                Object value = invoke(connection, m, a);
                                if (!m.getName().equals("prepareStatement")) return value;
                                PreparedStatement statement = (PreparedStatement) value;
                                return Proxy.newProxyInstance(getClass().getClassLoader(),
                                        new Class<?>[] {PreparedStatement.class}, (sp, sm, sa) -> {
                                            Object response = invoke(statement, sm, sa);
                                            if (!sm.getName().equals("getGeneratedKeys")) return response;
                                            ResultSet keys = (ResultSet) response;
                                            return Proxy.newProxyInstance(getClass().getClassLoader(),
                                                    new Class<?>[] {ResultSet.class}, (rp, rm, ra) -> {
                                                        if (!nullKey && rm.getName().equals("next")) return false;
                                                        if (nullKey && rm.getName().equals("getLong")) return 0L;
                                                        if (nullKey && rm.getName().equals("wasNull")) return true;
                                                        return invoke(keys, rm, ra);
                                                    });
                                        });
                            });
                });
        TransactionManager keyTransactions = new TransactionManager(keySource);
        EntityManager keyEntities = new EntityManager(new JdbcTemplate(keyTransactions));
        User user = new User();
        SQLException failure = assertThrows(SQLException.class,
                () -> keyTransactions.execute(() -> keyEntities.persist(user)));
        assertTrue(failure.getMessage().contains(nullKey ? "null" : "반환되지"));
        assertNull(user.id);
        assertEquals(0L, count());
    }

    private static Object invoke(Object target, Method method, Object[] args) throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }
}
