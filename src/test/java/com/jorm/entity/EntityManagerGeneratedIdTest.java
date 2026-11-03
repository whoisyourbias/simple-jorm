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

    /** DB가 생성한 Long ID를 객체에 반영하고 그 ID로 저장된 엔티티를 조회할 수 있는지 검증한다. */
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

    /** 서로 다른 객체를 연속 저장하면 각 객체에 해당 INSERT의 생성 ID가 따로 반영되는지 검증한다. */
    @Test
    void repeatedInsertsReflectIndependentKeys() throws SQLException {
        User first = new User();
        User second = new User();
        entities.persist(first);
        entities.persist(second);
        assertEquals(41L, first.id);
        assertEquals(42L, second.id);
    }

    /** ID만 있는 엔티티를 DEFAULT VALUES로 저장하고 생성 키를 Integer 필드에 반영하는지 검증한다. */
    @Test
    void supportsIntegerIdAndDefaultValuesInsert() throws SQLException {
        IntegerUser user = new IntegerUser();
        assertEquals(1, entities.persist(user));
        assertEquals(41, user.id);
        assertEquals(1L, count());
    }

    /** 생성 ID에 값이 이미 있으면 SQL 실행 전에 거부하고 객체 ID와 DB를 변경하지 않는지 검증한다. */
    @Test
    void rejectsPresetGeneratedIdBeforeInsert() throws SQLException {
        User user = new User();
        user.id = 7L;
        assertThrows(IllegalArgumentException.class, () -> entities.persist(user));
        assertEquals(7L, user.id);
        assertEquals(0L, count());
    }

    /** @Id가 아닌 필드에 @GeneratedValue를 붙이면 메타데이터 분석에서 거부하는지 검증한다. */
    @Test
    void rejectsGeneratedValueOnNonIdField() {
        assertThrows(IllegalArgumentException.class, () -> new EntityMetadata(Misplaced.class));
    }

    /** IDENTITY 생성 ID에 지원하지 않는 필드 타입을 선언하면 메타데이터 분석에서 거부하는지 검증한다. */
    @Test
    void rejectsUnsupportedGeneratedIdType() {
        assertThrows(IllegalArgumentException.class, () -> new EntityMetadata(Unsupported.class));
    }

    /** 미지정 상태를 null로 표현할 수 없는 기본형 생성 ID를 거부하는지 검증한다. */
    @Test
    void rejectsPrimitiveGeneratedId() {
        assertThrows(IllegalArgumentException.class, () -> new EntityMetadata(Primitive.class));
    }

    /** static 필드의 생성 ID 선언을 무시하지 않고 잘못된 메타데이터로 거부하는지 검증한다. */
    @Test
    void rejectsStaticGeneratedIdInsteadOfIgnoringIt() {
        assertThrows(IllegalArgumentException.class, () -> new EntityMetadata(StaticGenerated.class));
    }

    /** 드라이버가 생성 키를 반환하지 않으면 예외를 전파하고 INSERT를 롤백하며 객체 ID는 null로 유지하는지 검증한다. */
    @Test
    void missingGeneratedKeyPropagatesAndRollsBackInsert() throws SQLException {
        assertInvalidKeyRollsBack(false);
    }

    /** 드라이버가 null 생성 키를 반환하면 예외를 전파하고 INSERT를 롤백하며 객체 ID는 null로 유지하는지 검증한다. */
    @Test
    void nullGeneratedKeyPropagatesAndRollsBackInsert() throws SQLException {
        assertInvalidKeyRollsBack(true);
    }

    /** 유일성 제약 위반으로 INSERT가 실패하면 실패한 객체의 ID는 null이고 기존 행은 유지되는지 검증한다. */
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

    /** 저장 후 트랜잭션이 실패하면 DB 행은 롤백되지만 이미 객체에 반영한 생성 ID는 남는지 검증한다. */
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

    /** 트랜잭션이 정상 종료되면 생성 ID로 저장한 행이 커밋되어 이후 조회되는지 검증한다. */
    @Test
    void commitsGeneratedIdInsert() throws SQLException {
        User user = new User();
        transactions.execute(() -> entities.persist(user));
        assertNotNull(entities.find(User.class, user.id));
    }

    /** 생성 키가 Integer 범위를 넘으면 값을 잘라 저장하지 않고 예외와 함께 INSERT를 롤백하는지 검증한다. */
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
