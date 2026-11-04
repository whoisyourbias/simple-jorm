package com.jorm.entity;

import com.jorm.jdbc.JdbcTemplate;
import com.jorm.jdbc.RowMapper;
import com.jorm.transaction.TransactionManager;
import com.jorm.transaction.UnexpectedRollbackException;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

class EntityManagerPersistenceContextTest {
    private JdbcDataSource source;
    private Connection keeper;
    private TransactionManager transactions;
    private CountingJdbcTemplate jdbc;
    private EntityManager entities;

    @Table("users")
    private static class User {
        @Id private Long id;
        private String name;
        private User() {}
        private User(Long id, String name) { this.id = id; this.name = name; }
    }

    @Table("users")
    private static class OtherUser {
        @Id private Long id;
        private String name;
    }

    @Table("generated_users")
    private static class IdentityUser {
        @Id @GeneratedValue private Long id;
        private String name;
    }

    @Table("integer_users")
    private static class IntegerUser {
        @Id @GeneratedValue private Integer id;
        private String name;
    }

    @Table("collision_users")
    private static class CollisionUser {
        @Id @GeneratedValue private Long id;
        private String name;
    }

    @Table("uuid_users")
    private static class UuidUser {
        @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
        private String name;
    }

    @Table("string_users")
    private static class StringUser {
        @Id @GeneratedValue(strategy = GenerationType.UUID) private String id;
        private String name;
    }

    @Table("binary_users")
    private static class BinaryUser {
        @Id private byte[] id;
        private String name;
    }

    /** 실제 SQL 실행을 유지하면서 조회 횟수만 기록한다. */
    private static class CountingJdbcTemplate extends JdbcTemplate {
        private int selects;
        private CountingJdbcTemplate(TransactionManager transactions) { super(transactions); }
        @Override
        public <T> T queryForObject(String query, RowMapper<T> mapper, Object... args) throws SQLException {
            selects++;
            return super.queryForObject(query, mapper, args);
        }
    }

    @BeforeEach
    void setUp() throws SQLException {
        source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:context-" + UUID.randomUUID());
        keeper = source.getConnection();
        transactions = new TransactionManager(source);
        jdbc = new CountingJdbcTemplate(transactions);
        entities = new EntityManager(jdbc);
        jdbc.update("CREATE TABLE users(id BIGINT PRIMARY KEY, name VARCHAR(100) NOT NULL)");
        jdbc.update("INSERT INTO users VALUES(1, 'Alice'), (2, 'Bob')");
        jdbc.update("CREATE TABLE generated_users(id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY, name VARCHAR(100))");
        jdbc.update("CREATE TABLE integer_users(id INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY, name VARCHAR(100))");
        jdbc.update("CREATE TABLE uuid_users(id UUID PRIMARY KEY, name VARCHAR(100))");
        jdbc.update("CREATE TABLE string_users(id VARCHAR(36) PRIMARY KEY, name VARCHAR(100))");
        jdbc.update("CREATE TABLE binary_users(id VARBINARY PRIMARY KEY, name VARCHAR(100))");
    }

    @AfterEach
    void tearDown() throws SQLException { keeper.close(); }

    /** 참조 동일성과 SELECT 생략을 함께 검증한다. */
    @Test
    void sharesObjectAndSkipsRepeatedSelectAcrossFacadesAndRequiredCalls() throws SQLException {
        EntityManager other = new EntityManager(new JdbcTemplate(transactions));
        transactions.execute(() -> {
            User user = entities.find(User.class, 1L);
            user.name = "in memory";
            transactions.execute(() -> {
                assertSame(user, other.find(User.class, 1L));
                assertEquals("in memory", other.find(User.class, 1L).name);
                return null;
            });
            assertSame(user, entities.find(User.class, 1L));
            assertEquals(1, jdbc.selects);
            return null;
        });
        assertEquals("in memory", entities.find(User.class, 1L).name);
    }

    /** 서로 다른 타입·ID는 같은 관리 객체를 공유하면 안 된다. */
    @Test
    void separatesEntityTypesAndIds() throws SQLException {
        transactions.execute(() -> {
            User one = entities.find(User.class, 1L);
            assertNotSame(one, entities.find(User.class, 2L));
            assertNotSame(one, entities.find(OtherUser.class, 1L));
            assertEquals(3, jdbc.selects);
            return null;
        });
    }

    /** JDBC가 허용하는 다른 숫자 인자도 실제 ID를 기준으로 기존 객체를 찾아야 한다. */
    @Test
    void canonicalizesResultIdWithoutChangingJdbcInputCompatibility() throws SQLException {
        transactions.execute(() -> {
            User one = entities.find(User.class, 1L);
            assertSame(one, entities.find(User.class, 1));
            assertSame(one, entities.find(User.class, "1"));
            assertSame(one, entities.find(User.class, 1L));
            assertEquals(3, jdbc.selects);
            return null;
        });
    }

    /** 커밋·롤백 후에는 새 컨텍스트를 사용한다. */
    @Test
    void isolatesTransactionsAndClearsAfterRollback() throws SQLException {
        User committed = transactions.execute(() -> entities.find(User.class, 1L));
        User[] rolledBack = new User[1];
        assertThrows(SQLException.class, () -> transactions.execute(() -> {
            rolledBack[0] = entities.find(User.class, 1L);
            assertNotSame(committed, rolledBack[0]);
            rolledBack[0].name = "rolled back";
            entities.update(rolledBack[0]);
            throw new SQLException("rollback");
        }));
        User next = transactions.execute(() -> entities.find(User.class, 1L));
        assertNotSame(rolledBack[0], next);
        assertEquals("Alice", next.name);
    }

    /** 같은 DB라도 다른 관리자와 다른 스레드의 컨텍스트는 분리한다. */
    @Test
    void isolatesManagersAndThreads() throws Exception {
        TransactionManager otherTransactions = new TransactionManager(source);
        EntityManager other = new EntityManager(new JdbcTemplate(otherTransactions));
        try (var executor = Executors.newSingleThreadExecutor()) {
            transactions.execute(() -> {
                User user = entities.find(User.class, 1L);
                assertNotSame(user, otherTransactions.execute(() -> other.find(User.class, 1L)));
                try {
                    assertNotSame(user, executor.submit(() -> transactions.execute(
                            () -> entities.find(User.class, 1L))).get());
                } catch (Exception e) {
                    throw new SQLException(e);
                }
                assertSame(user, entities.find(User.class, 1L));
                return null;
            });
        }
    }

    /** null 결과를 저장하면 이후 생성된 행을 가릴 수 있다. */
    @Test
    void doesNotCacheMissingRows() throws SQLException {
        transactions.execute(() -> {
            assertNull(entities.find(User.class, 3L));
            jdbc.update("INSERT INTO users VALUES(3, 'Carol')");
            assertEquals("Carol", entities.find(User.class, 3L).name);
            assertEquals(2, jdbc.selects);
            return null;
        });
    }

    /** 직접 지정 ID와 각 생성 ID의 저장 객체를 바로 재사용한다. */
    @Test
    void registersPersistedEntitiesForEverySupportedIdStrategy() throws SQLException {
        transactions.execute(() -> {
            User direct = new User(3L, "Carol");
            assertEquals(1, entities.persist(direct));
            assertSame(direct, entities.find(User.class, 3L));
            IdentityUser identity = new IdentityUser();
            assertEquals(1, entities.persist(identity));
            assertSame(identity, entities.find(IdentityUser.class, identity.id));
            IntegerUser integer = new IntegerUser();
            assertEquals(1, entities.persist(integer));
            assertSame(integer, entities.find(IntegerUser.class, integer.id));
            UuidUser uuid = new UuidUser();
            assertEquals(1, entities.persist(uuid));
            assertSame(uuid, entities.find(UuidUser.class, uuid.id));
            StringUser string = new StringUser();
            assertEquals(1, entities.persist(string));
            assertSame(string, entities.find(StringUser.class, string.id));
            assertEquals(0, jdbc.selects);
            return null;
        });
    }

    /** 동일 ID의 별도 객체로 관리 객체를 덮는 저장·수정을 거부한다. */
    @Test
    void rejectsConflictingInstancesBeforeMutation() throws SQLException {
        transactions.execute(() -> {
            User managed = entities.find(User.class, 1L);
            User other = new User(1L, "replacement");
            assertThrows(IllegalArgumentException.class, () -> entities.persist(other));
            assertThrows(IllegalArgumentException.class, () -> entities.update(other));
            assertSame(managed, entities.find(User.class, 1L));
            managed.name = "updated";
            assertEquals(1, entities.update(managed));
            assertSame(managed, entities.find(User.class, 1L));
            return null;
        });
        assertEquals("updated", entities.find(User.class, 1L).name);
    }

    /** 삭제는 별도 참조로 호출해도 해당 ID의 관리 객체를 제거한다. */
    @Test
    void evictsRemovedEntityAndAllowsReinsert() throws SQLException {
        transactions.execute(() -> {
            User original = entities.find(User.class, 1L);
            assertEquals(1, entities.remove(new User(1L, "ignored")));
            assertNull(entities.find(User.class, 1L));
            User replacement = new User(1L, "new");
            entities.persist(replacement);
            assertSame(replacement, entities.find(User.class, 1L));
            assertNotSame(original, replacement);
            return null;
        });
    }

    /** 0행 수정·삭제도 이미 사라진 행의 관리 객체를 제거한다. */
    @Test
    void evictsOnZeroRowUpdateAndRemove() throws SQLException {
        transactions.execute(() -> {
            User one = entities.find(User.class, 1L);
            User two = entities.find(User.class, 2L);
            jdbc.update("DELETE FROM users");
            assertEquals(0, entities.update(one));
            assertEquals(0, entities.remove(two));
            assertNull(entities.find(User.class, 1L));
            assertNull(entities.find(User.class, 2L));
            return null;
        });
    }

    /** ID가 바뀐 관리 객체로 다른 행을 수정하거나 기존 키를 재사용하면 안 된다. */
    @Test
    void rejectsManagedIdChanges() throws SQLException {
        transactions.execute(() -> {
            User one = entities.find(User.class, 1L);
            one.id = 2L;
            assertThrows(IllegalArgumentException.class, () -> entities.update(one));
            assertThrows(IllegalArgumentException.class, () -> entities.remove(one));
            assertThrows(IllegalArgumentException.class, () -> entities.find(User.class, 1L));
            one.id = 1L;
            assertSame(one, entities.find(User.class, 1L));
            return null;
        });
    }

    /** 직접 JDBC 변경이 관리 객체를 자동 갱신하지 않는 계약을 확인한다. */
    @Test
    void retainsManagedStateAfterDirectJdbcMutation() throws SQLException {
        transactions.execute(() -> {
            User one = entities.find(User.class, 1L);
            jdbc.update("UPDATE users SET name = 'DB' WHERE id = 1");
            assertSame(one, entities.find(User.class, 1L));
            assertEquals("Alice", one.name);
            return null;
        });
        assertEquals("DB", entities.find(User.class, 1L).name);
    }

    /** 잡힌 INSERT 오류도 rollbackOnly로 남기며 실패한 객체는 등록하지 않는다. */
    @Test
    void caughtSqlFailureRollsBackAndDoesNotRegisterFailedEntity() {
        UnexpectedRollbackException failure = assertThrows(UnexpectedRollbackException.class,
                () -> transactions.execute(() -> {
                    User invalid = new User(3L, null);
                    assertThrows(SQLException.class, () -> entities.persist(invalid));
                    assertNull(entities.find(User.class, 3L));
                    entities.persist(new User(4L, "also rolled back"));
                    return null;
                }));
        assertInstanceOf(SQLException.class, failure.getCause());
        assertNull(assertDoesNotThrow(() -> entities.find(User.class, 4L)));
    }

    /** 가변·배열 ID 타입의 기존 CRUD는 캐싱 없이 실행한다. */
    @Test
    void preservesUncachedCrudForBinaryIds() throws SQLException {
        transactions.execute(() -> {
            BinaryUser value = new BinaryUser();
            value.id = new byte[]{1, 2};
            value.name = "binary";
            assertEquals(1, entities.persist(value));
            BinaryUser first = entities.find(BinaryUser.class, new byte[]{1, 2});
            BinaryUser second = entities.find(BinaryUser.class, new byte[]{1, 2});
            assertNotSame(first, second);
            assertArrayEquals(value.id, first.id);
            second.name = "updated";
            assertEquals(1, entities.update(second));
            assertEquals(1, entities.remove(first));
            assertNull(entities.find(BinaryUser.class, value.id));
            return null;
        });
    }

    /** INSERT 후 기존 관리 객체와 생성 ID가 충돌하면 잡힌 예외도 롤백해야 한다. */
    @Test
    void rollsBackGeneratedKeyRegistrationCollision() throws SQLException {
        assertRegistrationFailureRollsBack(false);
    }

    /** 기존 객체의 ID가 변조돼도 INSERT 성공 이후 등록 실패를 롤백해야 한다. */
    @Test
    void rollsBackRegistrationFailureCausedByMutatedManagedId() throws SQLException {
        assertRegistrationFailureRollsBack(true);
    }

    private void assertRegistrationFailureRollsBack(boolean mutateManagedId) throws SQLException {
        jdbc.update("CREATE TABLE collision_users(id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY, name VARCHAR(100))");
        // 직접 넣은 ID는 H2 시퀀스를 증가시키지 않아 다음 생성 ID가 1이다.
        jdbc.update("INSERT INTO collision_users VALUES(1, 'original')");
        SQLException[] registrationFailure = new SQLException[1];
        UnexpectedRollbackException rollback = assertThrows(UnexpectedRollbackException.class,
                () -> transactions.execute(() -> {
                    CollisionUser old = entities.find(CollisionUser.class, 1L);
                    if (mutateManagedId) old.id = 2L;
                    jdbc.update("DELETE FROM collision_users WHERE id = 1");
                    CollisionUser fresh = new CollisionUser();
                    fresh.name = "must roll back";
                    registrationFailure[0] = assertThrows(SQLException.class, () -> entities.persist(fresh));
                    return null;
                }));
        assertSame(registrationFailure[0], rollback.getCause());
        assertEquals("original", entities.find(CollisionUser.class, 1L).name);
    }
}
