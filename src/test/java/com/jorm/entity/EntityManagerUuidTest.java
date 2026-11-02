package com.jorm.entity;

import com.jorm.jdbc.JdbcTemplate;
import com.jorm.transaction.TransactionManager;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** UUID는 DB 생성 키 회수 없이 INSERT 전에 생성하고 바인딩한다. */
class EntityManagerUuidTest {
    private Connection keepAlive;
    private JdbcTemplate jdbc;
    private TransactionManager transactions;
    private EntityManager entities;

    @Table("uuid_users")
    private static class User {
        @Column("user_name") private String name;
        @Id @GeneratedValue(strategy = GenerationType.UUID)
        @Column("user_id") private UUID id;
    }

    @Table("string_users")
    private static class StringUser {
        @Id @GeneratedValue(strategy = GenerationType.UUID)
        @Column("user_id") private String id;
        private String name;
    }

    @Table("only_ids")
    private static class OnlyId {
        @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    }

    @Table("uuid_users")
    private static class NumericId {
        @Id @GeneratedValue(strategy = GenerationType.UUID) private Long id;
    }

    @Table("uuid_users")
    private static class NonIdGeneration {
        @Id private Long id;
        @GeneratedValue(strategy = GenerationType.UUID) private UUID other;
    }

    @Table("uuid_users")
    private static class AssignedUuid {
        @Id @Column("user_id") private UUID id;
        @Column("user_name") private String name;
    }

    @BeforeEach
    void setUp() throws SQLException {
        JdbcDataSource source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:uuid-" + UUID.randomUUID());
        keepAlive = source.getConnection();
        transactions = new TransactionManager(source);
        jdbc = new JdbcTemplate(transactions);
        entities = new EntityManager(jdbc);
        // DB 기본값 없이도 라이브러리가 ID를 생성해 전달해야 한다.
        jdbc.update("CREATE TABLE uuid_users (user_name VARCHAR(100) NOT NULL, user_id UUID PRIMARY KEY)");
        jdbc.update("CREATE TABLE string_users (user_id VARCHAR(36) PRIMARY KEY, name VARCHAR(100))");
        jdbc.update("CREATE TABLE only_ids (id UUID PRIMARY KEY)");
    }

    @AfterEach
    void tearDown() throws SQLException {
        if (keepAlive != null) keepAlive.close();
    }

    @Test
    void generatesV4UuidAndBindsMappedIdColumn() throws SQLException {
        User user = new User(); user.name = "O'Brien";
        assertEquals(1, entities.persist(user));
        assertNotNull(user.id);
        assertEquals(4, user.id.version());
        assertEquals(2, user.id.variant());
        assertEquals(user.id, jdbc.queryForObject("SELECT user_id FROM uuid_users",
                (rs, row) -> rs.getObject(1, UUID.class)));
        User found = entities.find(User.class, user.id);
        assertEquals(user.id, found.id);
        assertEquals(user.name, found.name);
    }

    @Test
    void generatesCanonicalUuidStringAndFindsEntity() throws SQLException {
        StringUser user = new StringUser(); user.name = "Alice";
        assertEquals(1, entities.persist(user));
        UUID parsed = UUID.fromString(user.id);
        assertEquals(4, parsed.version());
        assertEquals(parsed.toString(), user.id);
        assertEquals(user.id, jdbc.queryForObject("SELECT user_id FROM string_users", (rs, row) -> rs.getString(1)));
        assertEquals(user.name, entities.find(StringUser.class, user.id).name);
    }

    @Test
    void insertsEntityWithOnlyUuidId() throws SQLException {
        OnlyId entity = new OnlyId();
        assertEquals(1, entities.persist(entity));
        assertNotNull(entity.id);
        assertEquals(entity.id, entities.find(OnlyId.class, entity.id).id);
    }

    @Test
    void generatesIndependentIdsForDifferentEntities() throws SQLException {
        User first = new User(); first.name = "Alice";
        User second = new User(); second.name = "Bob";
        entities.persist(first);
        entities.persist(second);
        assertNotEquals(first.id, second.id);
        assertEquals("Alice", entities.find(User.class, first.id).name);
        assertEquals("Bob", entities.find(User.class, second.id).name);
        assertEquals(2L, countUsers());
    }

    @Test
    void rejectsAssignedGeneratedUuidWithoutReplacingIt() throws SQLException {
        User user = new User(); user.name = "Alice";
        UUID assigned = UUID.fromString("d1790d6e-f4cc-4a88-b723-1ed179a04640");
        user.id = assigned;
        assertThrows(IllegalArgumentException.class, () -> entities.persist(user));
        assertEquals(assigned, user.id);
        assertEquals(0L, countUsers());
    }

    @Test
    void rejectsPersistingSameGeneratedEntityTwice() throws SQLException {
        User user = new User(); user.name = "Alice";
        entities.persist(user);
        UUID firstId = user.id;
        assertThrows(IllegalArgumentException.class, () -> entities.persist(user));
        assertEquals(firstId, user.id);
        assertEquals(1L, countUsers());
    }

    @Test
    void keepsPreInsertUuidWhenSqlFails() throws SQLException {
        User user = new User(); // name NOT NULL 위반
        assertThrows(SQLException.class, () -> entities.persist(user));
        assertNotNull(user.id);
        assertEquals(4, user.id.version());
        assertEquals(0L, countUsers());
    }

    @Test
    void rollsBackRowButKeepsGeneratedUuid() throws SQLException {
        User user = new User(); user.name = "Alice";
        SQLException expected = new SQLException("service failure");
        assertSame(expected, assertThrows(SQLException.class, () -> transactions.execute(() -> {
            entities.persist(user);
            assertEquals(user.name, entities.find(User.class, user.id).name);
            throw expected;
        })));
        assertNotNull(user.id);
        assertNull(entities.find(User.class, user.id));
        assertEquals(0L, countUsers());
    }

    @Test
    void rejectsNumericUuidField() {
        assertThrows(IllegalArgumentException.class, () -> new EntityMetadata(NumericId.class));
    }

    @Test
    void rejectsUuidGenerationOnNonIdField() {
        assertThrows(IllegalArgumentException.class, () -> new EntityMetadata(NonIdGeneration.class));
    }

    @Test
    void preservesManuallyAssignedUuidWithoutGenerationAnnotation() throws SQLException {
        AssignedUuid user = new AssignedUuid(); user.name = "Assigned";
        user.id = UUID.fromString("d1790d6e-f4cc-4a88-b723-1ed179a04640");
        assertEquals(1, entities.persist(user));
        assertEquals(user.id, entities.find(AssignedUuid.class, user.id).id);
    }

    private long countUsers() throws SQLException {
        return jdbc.queryForObject("SELECT COUNT(*) FROM uuid_users", (rs, row) -> rs.getLong(1));
    }
}
