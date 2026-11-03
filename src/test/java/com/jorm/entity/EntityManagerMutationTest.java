package com.jorm.entity;

import com.jorm.jdbc.JdbcTemplate;
import com.jorm.transaction.TransactionManager;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** 실제 DB 결과로 UPDATE·DELETE의 대상, 바인딩, 트랜잭션 경계를 확인한다. */
class EntityManagerMutationTest {
    private Connection keepAlive;
    private JdbcTemplate jdbc;
    private TransactionManager transactions;
    private EntityManager entities;

    @Table("users")
    private static class User {
        private static final String TYPE = "ignored";
        @Column("user_name") private String name;
        @Id @Column("user_id") private Long id;
        private String email;

        // update·remove도 기존 객체를 쓰므로 기본 생성자가 필요 없다.
        private User(Long id, String name, String email) {
            this.id = id;
            this.name = name;
            this.email = email;
        }
    }

    @Table("users")
    private static class OnlyId {
        @Id @Column("user_id") private Long id = 1L;
    }

    @Table("identity_users")
    private static class IdentityUser {
        @Id @GeneratedValue @Column("user_id") private Long id;
        private String name;
    }

    @Table("uuid_users")
    private static class UuidUser {
        @Id @GeneratedValue(strategy = GenerationType.UUID)
        @Column("user_id") private UUID id;
        private String name;
    }

    @Table("missing_table")
    private static class MissingTable {
        @Id private Long id = 1L;
        private String name = "new";
    }

    private record StoredUser(long id, String name, String email) {}

    @BeforeEach
    void setUp() throws SQLException {
        JdbcDataSource source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:mutation-" + UUID.randomUUID());
        keepAlive = source.getConnection();
        transactions = new TransactionManager(source);
        jdbc = new JdbcTemplate(transactions);
        entities = new EntityManager(jdbc);
        jdbc.update("CREATE TABLE users (user_id BIGINT PRIMARY KEY, "
                + "user_name VARCHAR(100) NOT NULL, email VARCHAR(150) UNIQUE)");
        jdbc.update("INSERT INTO users VALUES (1, 'Alice', 'alice@example.com'), "
                + "(2, 'Bob', 'bob@example.com')");
        jdbc.update("CREATE TABLE identity_users (user_id BIGINT GENERATED ALWAYS AS IDENTITY "
                + "PRIMARY KEY, name VARCHAR(100))");
        jdbc.update("CREATE TABLE uuid_users (user_id UUID PRIMARY KEY, name VARCHAR(100))");
    }

    @AfterEach
    void tearDown() throws SQLException {
        if (keepAlive != null) keepAlive.close();
    }

    /** 별칭 컬럼과 현재 객체 값으로 지정 ID의 행만 수정하고 반복 수정 시 새 값을 읽으며 ID는 유지하는지 검증한다. */
    @Test
    void updatesOnlyTargetRowUsingMappedColumnsAndCurrentValues() throws SQLException {
        User user = new User(1L, "Carol", "carol@example.com");
        assertEquals(1, entities.update(user));
        assertEquals(List.of(new StoredUser(1L, "Carol", "carol@example.com"),
                new StoredUser(2L, "Bob", "bob@example.com")), storedUsers());
        user.name = "Dave";
        assertEquals(1, entities.update(user));
        assertEquals("Dave", storedUsers().getFirst().name());
        assertEquals(1L, user.id);
    }

    /** UPDATE에서 따옴표가 포함된 문자열과 null을 값으로 바인딩하고 다른 행은 변경하지 않는지 검증한다. */
    @Test
    void bindsQuotedTextAndNullOnUpdate() throws SQLException {
        String name = "O'Brien'); DELETE FROM users; --";
        assertEquals(1, entities.update(new User(1L, name, null)));
        assertEquals(List.of(new StoredUser(1L, name, null),
                new StoredUser(2L, "Bob", "bob@example.com")), storedUsers());
    }

    /** 존재하지 않는 ID를 수정하면 0을 반환하고 새 행을 삽입하거나 기존 행을 바꾸지 않는지 검증한다. */
    @Test
    void updateMissingIdReturnsZeroWithoutInserting() throws SQLException {
        assertEquals(0, entities.update(new User(99L, "missing", null)));
        assertOriginalRows();
    }

    /** null 객체의 UPDATE를 잘못된 입력으로 거부하고 기존 데이터를 유지하는지 검증한다. */
    @Test
    void rejectsNullEntityOnUpdate() throws SQLException {
        assertThrows(IllegalArgumentException.class, () -> entities.update(null));
        assertOriginalRows();
    }

    /** ID가 null인 객체의 UPDATE를 거부하고 기존 데이터를 유지하는지 검증한다. */
    @Test
    void rejectsNullIdOnUpdate() throws SQLException {
        assertThrows(IllegalArgumentException.class,
                () -> entities.update(new User(null, "changed", null)));
        assertOriginalRows();
    }

    /** ID 외에 수정할 필드가 없는 엔티티는 UPDATE를 거부하고 기존 행을 유지하는지 검증한다. */
    @Test
    void rejectsUpdateWhenNoNonIdFieldsExist() throws SQLException {
        assertThrows(IllegalArgumentException.class, () -> entities.update(new OnlyId()));
        assertOriginalRows();
    }

    /** UPDATE의 유일성 제약 위반을 SQLException으로 전달하고 기존 행을 변경하지 않는지 검증한다. */
    @Test
    void propagatesUpdateConstraintFailureWithoutChangingStoredRows() throws SQLException {
        assertThrows(SQLException.class,
                () -> entities.update(new User(1L, "changed", "bob@example.com")));
        assertOriginalRows();
    }

    /** 지정 ID의 행만 삭제하고 객체 필드는 유지하며 같은 객체를 다시 삭제하면 0을 반환하는지 검증한다. */
    @Test
    void removesOnlyTargetRowAndKeepsObjectValues() throws SQLException {
        User user = new User(1L, "irrelevant", null);
        assertEquals(1, entities.remove(user));
        assertEquals(List.of(new StoredUser(2L, "Bob", "bob@example.com")), storedUsers());
        assertEquals(1L, user.id);
        assertEquals("irrelevant", user.name);
        assertEquals(0, entities.remove(user));
    }

    /** 존재하지 않는 ID를 삭제하면 0을 반환하고 기존 데이터를 유지하는지 검증한다. */
    @Test
    void removeMissingIdReturnsZero() throws SQLException {
        assertEquals(0, entities.remove(new User(99L, null, null)));
        assertOriginalRows();
    }

    /** null 객체의 DELETE를 잘못된 입력으로 거부하고 기존 데이터를 유지하는지 검증한다. */
    @Test
    void rejectsNullEntityOnRemove() throws SQLException {
        assertThrows(IllegalArgumentException.class, () -> entities.remove(null));
        assertOriginalRows();
    }

    /** ID가 null인 객체의 DELETE를 거부하고 기존 데이터를 유지하는지 검증한다. */
    @Test
    void rejectsNullIdOnRemove() throws SQLException {
        assertThrows(IllegalArgumentException.class,
                () -> entities.remove(new User(null, null, null)));
        assertOriginalRows();
    }

    /** ID 필드만 있는 엔티티도 별칭 ID 컬럼을 조건으로 해당 행만 삭제할 수 있는지 검증한다. */
    @Test
    void removesEntityWithOnlyId() throws SQLException {
        assertEquals(1, entities.remove(new OnlyId()));
        assertEquals(List.of(new StoredUser(2L, "Bob", "bob@example.com")), storedUsers());
    }

    /** 존재하지 않는 테이블을 삭제할 때 SQL 실행 오류를 SQLException으로 전파하는지 검증한다. */
    @Test
    void propagatesRemoveSqlFailure() {
        assertThrows(SQLException.class, () -> entities.remove(new MissingTable()));
    }

    /** 같은 트랜잭션에서 서로 다른 행을 수정하고 삭제한 결과가 정상 종료 후 함께 커밋되는지 검증한다. */
    @Test
    void commitsUpdateAndRemoveInSameTransaction() throws SQLException {
        transactions.execute(() -> {
            assertEquals(1, entities.update(new User(1L, "changed", null)));
            assertEquals(1, entities.remove(new User(2L, null, null)));
            assertEquals(List.of(new StoredUser(1L, "changed", null)), storedUsers());
            return null;
        });
        assertEquals(List.of(new StoredUser(1L, "changed", null)), storedUsers());
    }

    /** 후속 SQL 실패 시 앞선 UPDATE와 DELETE를 함께 롤백하되 Java 객체의 변경 값은 유지하는지 검증한다. */
    @Test
    void rollsBackBothMutationsAfterLaterSqlFailure() throws SQLException {
        User changed = new User(1L, "changed", null);
        assertThrows(SQLException.class, () -> transactions.execute(() -> {
            assertEquals(1, entities.update(changed));
            assertEquals(1, entities.remove(new User(2L, null, null)));
            // 같은 연결에서는 아직 커밋하지 않은 변경이 보여야 한다.
            assertEquals(List.of(new StoredUser(1L, "changed", null)), storedUsers());
            entities.remove(new MissingTable());
            return null;
        }));
        assertOriginalRows();
        assertEquals("changed", changed.name); // 롤백이 Java 객체를 복구하지 않는다.
    }

    /** IDENTITY 엔티티의 기존 ID를 재생성하지 않고 수정과 삭제의 조건으로 사용하며 객체 ID는 유지하는지 검증한다. */
    @Test
    void updatesAndRemovesIdentityEntityWithoutRegeneratingId() throws SQLException {
        IdentityUser user = new IdentityUser();
        user.name = "before";
        entities.persist(user);
        Long id = user.id;
        user.name = "after";
        assertEquals(1, entities.update(user));
        assertEquals("after", entities.find(IdentityUser.class, id).name);
        assertEquals(id, user.id);
        assertEquals(1, entities.remove(user));
        assertNull(entities.find(IdentityUser.class, id));
        assertEquals(id, user.id);
    }

    /** UUID 엔티티의 기존 ID를 재생성하지 않고 수정과 삭제의 조건으로 사용하며 객체 ID는 유지하는지 검증한다. */
    @Test
    void updatesAndRemovesUuidEntityWithoutRegeneratingId() throws SQLException {
        UuidUser user = new UuidUser();
        user.name = "before";
        entities.persist(user);
        UUID id = user.id;
        user.name = "after";
        assertEquals(1, entities.update(user));
        assertEquals("after", entities.find(UuidUser.class, id).name);
        assertEquals(id, user.id);
        assertEquals(1, entities.remove(user));
        assertNull(entities.find(UuidUser.class, id));
        assertEquals(id, user.id);
    }

    private List<StoredUser> storedUsers() throws SQLException {
        return jdbc.query("SELECT user_id, user_name, email FROM users ORDER BY user_id",
                (rs, row) -> new StoredUser(rs.getLong(1), rs.getString(2), rs.getString(3)));
    }

    private void assertOriginalRows() throws SQLException {
        assertEquals(List.of(new StoredUser(1L, "Alice", "alice@example.com"),
                new StoredUser(2L, "Bob", "bob@example.com")), storedUsers());
    }
}
