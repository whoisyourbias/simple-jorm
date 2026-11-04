package com.jorm.entity;

import com.jorm.jdbc.JdbcTemplate;
import com.jorm.transaction.TransactionManager;
import com.jorm.transaction.UnexpectedRollbackException;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class EntityManagerFlushTest {
    private Connection keeper;
    private TransactionManager transactions;
    private CountingJdbcTemplate jdbc;
    private EntityManager entities;

    @Table("users")
    private static class User {
        @Id private Long id;
        private String name;
        private Integer score;
        private User() {}
        private User(Long id, String name) { this.id = id; this.name = name; this.score = 10; }
    }

    @Table("values_table")
    private static class MutableValues {
        @Id private Long id;
        private byte[] bytes;
        @Column("record_day") private java.sql.Date day;
        private Time moment;
        private Timestamp stamp;
    }

    private static class CountingJdbcTemplate extends JdbcTemplate {
        private int updates;
        private CountingJdbcTemplate(TransactionManager transactions) { super(transactions); }
        @Override
        public int update(String sql, Object... args) throws SQLException {
            if (sql.startsWith("UPDATE")) updates++;
            return super.update(sql, args);
        }
    }

    @BeforeEach
    void setUp() throws SQLException {
        JdbcDataSource source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:flush-" + UUID.randomUUID());
        keeper = source.getConnection();
        transactions = new TransactionManager(source);
        jdbc = new CountingJdbcTemplate(transactions);
        entities = new EntityManager(jdbc);
        jdbc.update("CREATE TABLE users(id BIGINT PRIMARY KEY, name VARCHAR(100) NOT NULL, score INT)");
        jdbc.update("INSERT INTO users VALUES(1, 'Alice', 10), (2, 'Bob', 20)");
        jdbc.update("CREATE TABLE values_table(id BIGINT PRIMARY KEY, bytes VARBINARY, record_day DATE, moment TIME, stamp TIMESTAMP(9))");
    }

    @AfterEach
    void tearDown() throws SQLException { keeper.close(); }

    private String name(long id) throws SQLException {
        return jdbc.queryForObject("SELECT name FROM users WHERE id = ?", (rs, n) -> rs.getString(1), id);
    }

    @Test
    void automaticallyFlushesDirtyFieldsOnlyAtOutermostCommit() throws SQLException {
        transactions.execute(() -> {
            User user = entities.find(User.class, 1L);
            transactions.execute(() -> { user.name = "changed"; user.score = null; return null; });
            assertEquals("Alice", name(1L));
            assertEquals(0, jdbc.updates);
            assertSame(user, entities.find(User.class, 1L));
            return null;
        });
        assertEquals("changed", name(1L));
        assertNull(entities.find(User.class, 1L).score);
        assertEquals(1, jdbc.updates);
    }

    @Test
    void skipsUnchangedAndRestoredEntities() throws SQLException {
        transactions.execute(() -> {
            User user = entities.find(User.class, 1L);
            entities.find(User.class, 2L);
            user.name = "temporary";
            user.name = "Alice";
            return null;
        });
        assertEquals(0, jdbc.updates);
    }

    @Test
    void snapshotsPersistedEntityAndTracksLaterChanges() throws SQLException {
        transactions.execute(() -> {
            User inserted = new User(3L, "inserted");
            assertEquals(1, entities.persist(inserted));
            assertEquals("inserted", name(3L));
            inserted.name = "after insert";
            return null;
        });
        assertEquals("after insert", name(3L));
        assertEquals(1, jdbc.updates);
    }

    @Test
    void explicitUpdateRefreshesSnapshotWithoutDuplicateUpdate() throws SQLException {
        transactions.execute(() -> {
            User user = entities.find(User.class, 1L);
            user.name = "explicit";
            assertEquals(1, entities.update(user));
            return null;
        });
        assertEquals("explicit", name(1L));
        assertEquals(1, jdbc.updates);
    }

    @Test
    void deletionRemovesSnapshotSoFurtherObjectMutationIsIgnored() throws SQLException {
        transactions.execute(() -> {
            User user = entities.find(User.class, 1L);
            assertEquals(1, entities.remove(user));
            user.name = "removed";
            return null;
        });
        assertNull(name(1L));
        assertEquals(0, jdbc.updates);
    }

    @Test
    void commitFlushFailureRollsBackEarlierSqlAndClosesContext() throws SQLException {
        assertThrows(SQLException.class, () -> transactions.execute(() -> {
            entities.persist(new User(3L, "must roll back"));
            entities.find(User.class, 1L).name = null;
            return null;
        }));
        assertNull(name(3L));
        assertEquals("Alice", entities.find(User.class, 1L).name);
    }

    @Test
    void rejectsIdMutationAtCommitBeforeAnyDirtyUpdate() throws SQLException {
        assertThrows(IllegalArgumentException.class, () -> transactions.execute(() -> {
            entities.find(User.class, 1L).name = "must roll back";
            entities.find(User.class, 2L).id = 3L;
            return null;
        }));
        assertEquals(0, jdbc.updates);
        assertEquals("Alice", name(1L));
        assertEquals("Bob", name(2L));
    }

    @Test
    void detectsInPlaceChangesOfBasicMutableJdbcValues() throws SQLException {
        MutableValues value = new MutableValues();
        value.id = 1L;
        value.bytes = new byte[]{1, 2};
        value.day = java.sql.Date.valueOf("2026-11-01");
        value.moment = Time.valueOf("12:00:00");
        value.stamp = Timestamp.valueOf("2026-11-01 12:00:00.123456789");
        transactions.execute(() -> {
            entities.persist(value);
            value.bytes[0] = 9;
            value.day.setTime(java.sql.Date.valueOf("2026-11-02").getTime());
            value.moment.setTime(Time.valueOf("13:00:00").getTime());
            value.stamp.setNanos(987654321);
            return null;
        });
        MutableValues loaded = entities.find(MutableValues.class, 1L);
        assertArrayEquals(new byte[]{9, 2}, loaded.bytes);
        assertEquals(value.day, loaded.day);
        assertEquals(value.moment, loaded.moment);
        assertEquals(value.stamp, loaded.stamp);
        assertEquals(1, jdbc.updates);
    }

    @Test
    void explicitFlushUsesSameConnectionAndRefreshesSnapshot() throws SQLException {
        transactions.execute(() -> {
            User user = entities.find(User.class, 1L);
            user.name = "first flush";
            entities.flush();
            assertEquals("first flush", name(1L));
            entities.flush();
            assertEquals(1, jdbc.updates);
            user.name = "second flush";
            entities.flush();
            assertEquals("second flush", name(1L));
            assertEquals(2, jdbc.updates);
            return null;
        });
        assertEquals(2, jdbc.updates);
    }

    @Test
    void flushOutsideTransactionFailsEvenAfterAnEarlierTransaction() throws SQLException {
        assertThrows(IllegalStateException.class, entities::flush);
        transactions.execute(() -> { entities.find(User.class, 1L); return null; });
        assertThrows(IllegalStateException.class, entities::flush);
    }

    @Test
    void emptyTransactionCanFlushWithoutSql() throws SQLException {
        transactions.execute(() -> { entities.flush(); return null; });
        assertEquals(0, jdbc.updates);
    }

    @Test
    void successfulFlushCanStillBeRolledBack() throws SQLException {
        User[] previous = new User[1];
        assertThrows(SQLException.class, () -> transactions.execute(() -> {
            previous[0] = entities.find(User.class, 1L);
            previous[0].name = "flushed";
            entities.flush();
            assertEquals("flushed", name(1L));
            throw new SQLException("later failure");
        }));
        assertEquals("flushed", previous[0].name);
        User next = transactions.execute(() -> entities.find(User.class, 1L));
        assertNotSame(previous[0], next);
        assertEquals("Alice", next.name);
    }

    @Test
    void caughtFlushSqlFailureStillRollsBackEarlierUpdatesAndInsert() throws SQLException {
        SQLException[] original = new SQLException[1];
        UnexpectedRollbackException failure = assertThrows(UnexpectedRollbackException.class,
                () -> transactions.execute(() -> {
                    entities.persist(new User(3L, "insert"));
                    entities.find(User.class, 1L).name = "first updated";
                    entities.flush();
                    User second = entities.find(User.class, 2L);
                    second.name = null;
                    original[0] = assertThrows(SQLException.class, entities::flush);
                    second.name = "repaired";
                    return null;
                }));
        assertSame(original[0], failure.getCause());
        assertEquals("Alice", name(1L));
        assertEquals("Bob", name(2L));
        assertNull(name(3L));
    }

    @Test
    void zeroRowFlushEvictsEntityAndSnapshotRatherThanRetrying() throws SQLException {
        transactions.execute(() -> {
            User old = entities.find(User.class, 1L);
            jdbc.update("DELETE FROM users WHERE id = 1");
            old.name = "missing row";
            entities.flush();
            assertEquals(1, jdbc.updates);
            old.name = "ignored after eviction";
            entities.flush();
            assertEquals(1, jdbc.updates);
            User replacement = new User(1L, "replacement");
            assertEquals(1, entities.persist(replacement));
            assertSame(replacement, entities.find(User.class, 1L));
            return null;
        });
        assertEquals("replacement", name(1L));
        assertEquals(1, jdbc.updates);
    }

    @Test
    void explicitUpdateRefreshesSnapshotButLaterMutationIsStillDetected() throws SQLException {
        transactions.execute(() -> {
            User user = entities.find(User.class, 1L);
            user.name = "explicit";
            entities.update(user);
            entities.flush();
            assertEquals(1, jdbc.updates);
            user.name = "after explicit";
            return null;
        });
        assertEquals("after explicit", name(1L));
        assertEquals(2, jdbc.updates);
    }

    @Test
    void snapshotUsesBinaryContentsAndPreservesTimestampNanos() throws SQLException {
        MutableValues value = new MutableValues();
        value.id = 1L;
        value.bytes = new byte[]{1, 2};
        value.stamp = Timestamp.valueOf("2026-11-01 12:00:00.123456789");
        transactions.execute(() -> {
            entities.persist(value);
            value.bytes = new byte[]{1, 2};
            entities.flush();
            assertEquals(0, jdbc.updates);
            value.stamp.setNanos(123456790); // 밀리초가 같아도 nanos 차이를 감지한다.
            entities.flush();
            assertEquals(1, jdbc.updates);
            entities.flush();
            assertEquals(1, jdbc.updates);
            return null;
        });
        assertEquals(value.stamp, entities.find(MutableValues.class, 1L).stamp);
    }

    @Test
    void changesOutsideTransactionAreNotTracked() throws SQLException {
        User detached = entities.find(User.class, 1L);
        detached.name = "detached";
        transactions.execute(() -> { entities.flush(); return null; });
        assertEquals("Alice", name(1L));
        assertEquals(0, jdbc.updates);
    }
}
