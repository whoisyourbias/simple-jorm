package com.jorm.entity;

import com.jorm.jdbc.JdbcTemplate;
import com.jorm.transaction.TransactionManager;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** 실제 IDENTITY 테이블로 생성 키 반영과 트랜잭션 경계를 확인한다. */
class EntityManagerIdentityTest {
    private Connection keepAlive;
    private JdbcTemplate jdbc;
    private TransactionManager transactions;
    private EntityManager entities;

    @Table("users")
    private static class User {
        @Column("user_name") private String name;
        @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
        @Column("user_id") private Long id;
    }

    @Table("only_ids")
    private static class OnlyId {
        @Id @GeneratedValue private Long id;
    }

    @Table("integer_ids")
    private static class IntegerId {
        @Id @GeneratedValue private Integer id;
    }

    @Table("users")
    private static class NonIdGeneration {
        @Id private Long id;
        @GeneratedValue private Long other;
    }

    @Table("users")
    private static class StringId {
        @Id @GeneratedValue private String id;
    }

    @Table("users")
    private static class PrimitiveId {
        @Id @GeneratedValue private long id;
    }

    @BeforeEach
    void setUp() throws SQLException {
        JdbcDataSource source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:identity-" + UUID.randomUUID());
        keepAlive = source.getConnection();
        transactions = new TransactionManager(source);
        jdbc = new JdbcTemplate(transactions);
        entities = new EntityManager(jdbc);
        // 비ID 컬럼을 먼저 선언해도 지정한 키 컬럼을 회수해야 한다.
        jdbc.update("CREATE TABLE users (user_name VARCHAR(100) NOT NULL, "
                + "user_id BIGINT GENERATED ALWAYS AS IDENTITY (START WITH 41) PRIMARY KEY)");
        jdbc.update("CREATE TABLE only_ids (id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY)");
        jdbc.update("CREATE TABLE integer_ids (id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY)");
    }

    @AfterEach
    void tearDown() throws SQLException {
        if (keepAlive != null) keepAlive.close();
    }

    @Test
    void assignsDatabaseKeyAndFindsInsertedEntity() throws SQLException {
        User user = new User();
        user.name = "O'Brien";
        assertEquals(1, entities.persist(user));
        assertEquals(41L, user.id);
        assertEquals("O'Brien", jdbc.queryForObject(
                "SELECT user_name FROM users WHERE user_id = ?", (rs, row) -> rs.getString(1), user.id));
        assertEquals(user.name, entities.find(User.class, user.id).name);
    }

    @Test
    void assignsDifferentKeysToEachEntity() throws SQLException {
        User first = new User(); first.name = "Alice";
        User second = new User(); second.name = "Bob";
        entities.persist(first);
        entities.persist(second);
        assertEquals(41L, first.id);
        assertEquals(42L, second.id);
        assertEquals("Alice", entities.find(User.class, first.id).name);
        assertEquals("Bob", entities.find(User.class, second.id).name);
    }

    @Test
    void insertsEntityWithOnlyGeneratedId() throws SQLException {
        OnlyId entity = new OnlyId();
        assertEquals(1, entities.persist(entity));
        assertEquals(1L, entity.id);
        assertNotNull(entities.find(OnlyId.class, entity.id));
    }

    @Test
    void supportsIntegerIdentity() throws SQLException {
        IntegerId entity = new IntegerId();
        assertEquals(1, entities.persist(entity));
        assertEquals(Integer.valueOf(1), entity.id);
        assertEquals(entity.id, entities.find(IntegerId.class, entity.id).id);
    }

    @Test
    void rejectsAlreadyAssignedGeneratedIdWithoutInserting() throws SQLException {
        User user = new User(); user.id = 99L; user.name = "Alice";
        assertThrows(IllegalArgumentException.class, () -> entities.persist(user));
        assertEquals(99L, user.id);
        assertEquals(0L, countUsers());
    }

    @Test
    void doesNotSetIdWhenInsertFails() throws SQLException {
        User user = new User(); // NOT NULL 위반
        assertThrows(SQLException.class, () -> entities.persist(user));
        assertNull(user.id);
        assertEquals(0L, countUsers());
    }

    @Test
    void rollsBackRowButKeepsAssignedObjectId() throws SQLException {
        User user = new User(); user.name = "Alice";
        SQLException expected = new SQLException("service failure");
        assertSame(expected, assertThrows(SQLException.class, () -> transactions.execute(() -> {
            entities.persist(user);
            assertNotNull(entities.find(User.class, user.id));
            throw expected;
        })));
        assertEquals(41L, user.id);
        assertNull(entities.find(User.class, user.id));
        assertEquals(0L, countUsers());
    }

    @Test
    void rejectsGeneratedValueOnNonIdField() {
        assertThrows(IllegalArgumentException.class, () -> new EntityMetadata(NonIdGeneration.class));
    }

    @Test
    void rejectsUnsupportedGeneratedIdType() {
        assertThrows(IllegalArgumentException.class, () -> new EntityMetadata(StringId.class));
    }

    @Test
    void rejectsPrimitiveGeneratedIdBecauseNullMarksUnsavedState() {
        assertThrows(IllegalArgumentException.class, () -> new EntityMetadata(PrimitiveId.class));
    }

    private long countUsers() throws SQLException {
        return jdbc.queryForObject("SELECT COUNT(*) FROM users", (rs, row) -> rs.getLong(1));
    }
}
