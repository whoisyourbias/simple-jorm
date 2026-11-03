package com.jorm.transaction;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.sql.Connection;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class TransactionResourceTest {
    private TransactionManager transactions;

    @BeforeEach
    void setUp() {
        JdbcDataSource source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:resource-" + UUID.randomUUID());
        transactions = new TransactionManager(source);
    }

    /** 안쪽 REQUIRED가 자원을 재사용하고 바깥 종료까지 정리를 미뤄야 한다. */
    @Test
    void reusesResourceUntilOutermostCompletion() throws SQLException {
        Object key = new Object();
        AtomicInteger created = new AtomicInteger();
        AtomicInteger closed = new AtomicInteger();
        transactions.execute(() -> {
            TransactionResource first = transactions.getOrCreateResource(key, () -> {
                created.incrementAndGet();
                return closed::incrementAndGet;
            });
            transactions.execute(() -> {
                assertSame(first, transactions.getOrCreateResource(key, () -> fail("duplicate")));
                return null;
            });
            assertEquals(0, closed.get());
            return null;
        });
        assertEquals(1, created.get());
        assertEquals(1, closed.get());
        transactions.execute(() -> {
            transactions.getOrCreateResource(key, () -> closed::incrementAndGet);
            return null;
        });
        assertEquals(2, closed.get());
    }

    /** 트랜잭션 밖에서는 자원 factory를 실행하지 않아야 한다. */
    @Test
    void doesNotCreateResourceOutsideTransaction() {
        assertNull(transactions.getOrCreateResource(new Object(), () -> fail("outside")));
    }

    /** 생성 실패한 자원을 등록하지 않아야 이후 정상 생성이 가능하다. */
    @Test
    void factoryFailureDoesNotRegisterResource() throws SQLException {
        Object key = new Object();
        AtomicInteger closed = new AtomicInteger();
        transactions.execute(() -> {
            assertThrows(IllegalArgumentException.class,
                    () -> transactions.getOrCreateResource(key, () -> { throw new IllegalArgumentException(); }));
            transactions.getOrCreateResource(key, () -> closed::incrementAndGet);
            return null;
        });
        assertEquals(1, closed.get());
    }

    /** 정리 중에는 끝난 트랜잭션의 재사용과 자원 추가를 거부해야 한다. */
    @Test
    void rejectsReentryDuringCompletion() throws SQLException {
        AtomicInteger closed = new AtomicInteger();
        transactions.execute(() -> {
            transactions.getOrCreateResource(new Object(), () -> () -> {
                assertThrows(IllegalStateException.class, () -> transactions.execute(() -> null));
                assertThrows(IllegalStateException.class, () -> transactions.withConnection(c -> null));
                assertThrows(IllegalStateException.class,
                        () -> transactions.getOrCreateResource(new Object(), () -> () -> {}));
                assertThrows(IllegalStateException.class,
                        () -> transactions.markRollbackOnly(new SQLException("too late")));
                closed.incrementAndGet();
            });
            return null;
        });
        assertEquals(1, closed.get());
    }

    /** 원래 업무 실패를 보존하면서 모든 자원을 정리해야 한다. */
    @Test
    void cleansAllResourcesAndSuppressesCleanupFailures() {
        SQLException original = new SQLException("business");
        IllegalStateException cleanup = new IllegalStateException("cleanup");
        List<String> calls = new ArrayList<>();
        assertSame(original, assertThrows(SQLException.class, () -> transactions.execute(() -> {
            transactions.getOrCreateResource(new Object(), () -> () -> {
                calls.add("first");
                throw cleanup;
            });
            transactions.getOrCreateResource(new Object(), () -> () -> calls.add("second"));
            throw original;
        })));
        assertEquals(List.of("first", "second"), calls);
        assertArrayEquals(new Throwable[]{cleanup}, original.getSuppressed());
        assertNull(transactions.getOrCreateResource(new Object(), () -> fail("leaked state")));
    }

    /** 정상 커밋 뒤 정리 실패가 나도 DB 확정은 유지되고 다음 트랜잭션은 독립적이다. */
    @Test
    void cleanupFailureAfterCommitDoesNotRollback() throws SQLException {
        IllegalStateException cleanup = new IllegalStateException("cleanup");
        String url = transactions.withConnection(c -> c.getMetaData().getURL());
        try (var keeper = java.sql.DriverManager.getConnection(url)) {
            transactions.withConnection(c -> { c.createStatement().execute("CREATE TABLE items(id INT)"); return null; });
            assertSame(cleanup, assertThrows(IllegalStateException.class, () -> transactions.execute(() -> {
                transactions.withConnection(c -> { c.createStatement().execute("INSERT INTO items VALUES(1)"); return null; });
                transactions.getOrCreateResource(new Object(), () -> () -> { throw cleanup; });
                return null;
            })));
            transactions.execute(() -> {
                transactions.withConnection(c -> {
                    try (var statement = c.createStatement(); var rows = statement.executeQuery("SELECT COUNT(*) FROM items")) {
                        assertTrue(rows.next());
                        assertEquals(1, rows.getInt(1));
                    }
                    return null;
                });
                return null;
            });
        }
    }

    /** 직접 지정한 rollbackOnly도 최초 원인을 보존해야 한다. */
    @Test
    void explicitRollbackOnlyPreservesFirstCause() {
        SQLException first = new SQLException("first");
        UnexpectedRollbackException failure = assertThrows(UnexpectedRollbackException.class,
                () -> transactions.execute(() -> {
                    transactions.markRollbackOnly(first);
                    transactions.markRollbackOnly(new SQLException("second"));
                    return null;
                }));
        assertSame(first, failure.getCause());
    }

    /** 커밋 실패 시 롤백하고 메모리 자원과 연결을 모두 종료해야 한다. */
    @Test
    void cleansResourcesOnCommitFailure() {
        SQLException commit = new SQLException("commit failed");
        AtomicInteger closed = new AtomicInteger();
        List<String> calls = new ArrayList<>();
        transactions = failingManager(commit, null, calls);
        assertSame(commit, assertThrows(SQLException.class, () -> transactions.execute(() -> {
            transactions.getOrCreateResource(new Object(), () -> closed::incrementAndGet);
            return null;
        })));
        assertEquals(1, closed.get());
        assertEquals(List.of("commit", "rollback", "reset", "close"), calls);
        assertNull(transactions.getOrCreateResource(new Object(), () -> fail("leaked")));
    }

    /** 롤백 실패 후 autoCommit 복원으로 부분 작업이 확정되지 않게 한다. */
    @Test
    void cleansResourcesOnRollbackFailureWithoutResettingAutoCommit() {
        SQLException business = new SQLException("business failed");
        SQLException rollback = new SQLException("rollback failed");
        AtomicInteger closed = new AtomicInteger();
        List<String> calls = new ArrayList<>();
        transactions = failingManager(null, rollback, calls);
        assertSame(business, assertThrows(SQLException.class, () -> transactions.execute(() -> {
            transactions.getOrCreateResource(new Object(), () -> closed::incrementAndGet);
            throw business;
        })));
        assertArrayEquals(new Throwable[]{rollback}, business.getSuppressed());
        assertEquals(1, closed.get());
        assertEquals(List.of("rollback", "close"), calls);
        assertNull(transactions.getOrCreateResource(new Object(), () -> fail("leaked")));
    }

    /** 정상 종료 뒤 여러 정리 실패도 모두 전달하고 다음 자원을 정리한다. */
    @Test
    void preservesAllCleanupFailuresAfterCommit() {
        RuntimeException first = new IllegalStateException("first");
        AssertionError second = new AssertionError("second");
        AtomicInteger closed = new AtomicInteger();
        assertSame(first, assertThrows(IllegalStateException.class, () -> transactions.execute(() -> {
            transactions.getOrCreateResource(new Object(), () -> () -> { throw first; });
            transactions.getOrCreateResource(new Object(), () -> () -> { throw second; });
            transactions.getOrCreateResource(new Object(), () -> closed::incrementAndGet);
            return null;
        })));
        assertEquals(1, closed.get());
        assertArrayEquals(new Throwable[]{second}, first.getSuppressed());
    }

    /** 실제 H2 연결의 commit·rollback 호출 경계에만 실패를 주입한다. */
    private TransactionManager failingManager(SQLException commitFailure, SQLException rollbackFailure,
                                               List<String> calls) {
        JdbcDataSource source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:fault-" + UUID.randomUUID());
        DataSource decorated = (DataSource) Proxy.newProxyInstance(DataSource.class.getClassLoader(),
                new Class<?>[]{DataSource.class}, (proxy, method, args) -> {
                    Object value;
                    try {
                        value = method.invoke(source, args);
                    } catch (InvocationTargetException failure) {
                        throw failure.getCause();
                    }
                    if (!(value instanceof Connection connection)) {
                        return value;
                    }
                    AtomicBoolean transactionStarted = new AtomicBoolean();
                    return Proxy.newProxyInstance(Connection.class.getClassLoader(),
                            new Class<?>[]{Connection.class}, (connectionProxy, operation, parameters) -> {
                                String name = operation.getName();
                                if (name.equals("commit")) {
                                    calls.add("commit");
                                    if (commitFailure != null) throw commitFailure;
                                }
                                if (name.equals("rollback")) {
                                    calls.add("rollback");
                                    if (rollbackFailure != null) throw rollbackFailure;
                                }
                                if (name.equals("setAutoCommit")) {
                                    if (transactionStarted.getAndSet(true)) calls.add("reset");
                                }
                                if (name.equals("close")) calls.add("close");
                                try {
                                    return operation.invoke(connection, parameters);
                                } catch (InvocationTargetException failure) {
                                    throw failure.getCause();
                                }
                            });
                });
        return new TransactionManager(decorated);
    }
}
