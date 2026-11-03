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

    /** v4 UUID를 생성해 별칭 ID 컬럼에 저장하고 직접 SQL과 find로 같은 UUID 및 필드 값을 읽는지 검증한다. */
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

    /** 표준 문자열 형식의 v4 UUID를 String ID에 기록하고 DB 저장 및 find 조회 결과가 일치하는지 검증한다. */
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

    /** UUID ID 필드만 있는 엔티티도 저장하고 생성한 ID로 조회할 수 있는지 검증한다. */
    @Test
    void insertsEntityWithOnlyUuidId() throws SQLException {
        OnlyId entity = new OnlyId();
        assertEquals(1, entities.persist(entity));
        assertNotNull(entity.id);
        assertEquals(entity.id, entities.find(OnlyId.class, entity.id).id);
    }

    /** 서로 다른 엔티티에 독립적인 UUID를 생성하고 각 ID의 저장 값과 전체 행 수가 올바른지 검증한다. */
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

    /** 이미 값이 지정된 자동 생성 UUID를 저장 전에 거부하고 기존 ID를 교체하거나 행을 삽입하지 않는지 검증한다. */
    @Test
    void rejectsAssignedGeneratedUuidWithoutReplacingIt() throws SQLException {
        User user = new User(); user.name = "Alice";
        UUID assigned = UUID.fromString("d1790d6e-f4cc-4a88-b723-1ed179a04640");
        user.id = assigned;
        assertThrows(IllegalArgumentException.class, () -> entities.persist(user));
        assertEquals(assigned, user.id);
        assertEquals(0L, countUsers());
    }

    /** UUID가 생성된 동일 객체를 다시 저장하면 거부하고 최초 ID와 저장된 한 행을 유지하는지 검증한다. */
    @Test
    void rejectsPersistingSameGeneratedEntityTwice() throws SQLException {
        User user = new User(); user.name = "Alice";
        entities.persist(user);
        UUID firstId = user.id;
        assertThrows(IllegalArgumentException.class, () -> entities.persist(user));
        assertEquals(firstId, user.id);
        assertEquals(1L, countUsers());
    }

    /** INSERT 전에 UUID를 생성하므로 SQL 실패 시 행은 없더라도 객체에는 생성한 UUID가 남는지 검증한다. */
    @Test
    void keepsPreInsertUuidWhenSqlFails() throws SQLException {
        User user = new User(); // name NOT NULL 위반
        assertThrows(SQLException.class, () -> entities.persist(user));
        assertNotNull(user.id);
        assertEquals(4, user.id.version());
        assertEquals(0L, countUsers());
    }

    /** 업무 예외를 원본 그대로 전파하고 DB 행은 롤백하되 객체의 생성 UUID는 유지하는지 검증한다. */
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

    /** UUID 생성 전략을 숫자 타입 ID 필드에 지정하면 메타데이터 분석에서 거부하는지 검증한다. */
    @Test
    void rejectsNumericUuidField() {
        assertThrows(IllegalArgumentException.class, () -> new EntityMetadata(NumericId.class));
    }

    /** ID가 아닌 필드에 UUID 생성 전략을 지정하면 메타데이터 분석에서 거부하는지 검증한다. */
    @Test
    void rejectsUuidGenerationOnNonIdField() {
        assertThrows(IllegalArgumentException.class, () -> new EntityMetadata(NonIdGeneration.class));
    }

    /** 생성 어노테이션이 없는 UUID ID는 직접 지정한 값을 그대로 저장하고 조회하는지 검증한다. */
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
