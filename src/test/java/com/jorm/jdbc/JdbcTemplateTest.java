package com.jorm.jdbc;

import org.h2.jdbcx.JdbcDataSource;
import org.h2.tools.RunScript;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class JdbcTemplateTest {

    private Connection keepAlive;
    private JdbcTemplate jdbcTemplate;

    private record User(long id, String name, String email) {}

    private final RowMapper<User> userMapper = (rs, rowNum) -> new User(
            rs.getLong("id"), rs.getString("name"), rs.getString("email"));

    @BeforeEach
    void setUp() throws Exception {
        var dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID());
        // Keep the in-memory database alive between template calls.
        keepAlive = dataSource.getConnection();
        for (String resource : new String[]{"/schema.sql", "/data.sql"}) {
            var stream = getClass().getResourceAsStream(resource);
            assertNotNull(stream, "Missing test SQL resource: " + resource);
            try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                RunScript.execute(keepAlive, reader);
            }
        }
        jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @AfterEach
    void tearDown() throws SQLException {
        if (keepAlive != null) {
            keepAlive.close();
        }
    }

    @Test
    void updateInsertsUserAndReturnsAffectedRowCount() throws SQLException {
        int count = jdbcTemplate.update(
                "INSERT INTO users (name, email) VALUES (?, ?)",
                "David", "david@example.com");

        assertEquals(1, count);
        try (var stmt = keepAlive.prepareStatement(
                "SELECT name FROM users WHERE email = ?")) {
            stmt.setString(1, "david@example.com");
            try (var rs = stmt.executeQuery()) {
                assertTrue(rs.next());
                assertEquals("David", rs.getString("name"));
                assertFalse(rs.next());
            }
        }
    }

    @Test
    void updateChangesOnlyMatchingUser() throws SQLException {
        int count = jdbcTemplate.update(
                "UPDATE users SET name = ? WHERE id = ?", "Updated", 2L);

        assertEquals(1, count);
        try (var stmt = keepAlive.createStatement();
             var rs = stmt.executeQuery("SELECT name FROM users ORDER BY id")) {
            var names = new ArrayList<String>();
            while (rs.next()) {
                names.add(rs.getString("name"));
            }
            assertEquals(List.of("Alice", "Updated", "Charlie"), names);
        }
    }

    @Test
    void updateDeletesMatchingUser() throws SQLException {
        int count = jdbcTemplate.update("DELETE FROM users WHERE id = ?", 2L);

        assertEquals(1, count);
        try (var stmt = keepAlive.createStatement();
             var rs = stmt.executeQuery("SELECT id FROM users ORDER BY id")) {
            var ids = new ArrayList<Long>();
            while (rs.next()) {
                ids.add(rs.getLong("id"));
            }
            assertEquals(List.of(1L, 3L), ids);
        }
    }

    @Test
    void updateReturnsZeroWhenNoRowsMatch() throws SQLException {
        int count = jdbcTemplate.update(
                "UPDATE users SET name = ? WHERE id = ?", "Updated", 999L);

        assertEquals(0, count);
        try (var stmt = keepAlive.createStatement();
             var rs = stmt.executeQuery("SELECT name FROM users ORDER BY id")) {
            var names = new ArrayList<String>();
            while (rs.next()) {
                names.add(rs.getString("name"));
            }
            assertEquals(List.of("Alice", "Bob", "Charlie"), names);
        }
    }

    @Test
    void updateReturnsCountOfAllModifiedRows() throws SQLException {
        int count = jdbcTemplate.update(
                "UPDATE users SET name = ? WHERE id >= ?", "Updated", 2L);

        assertEquals(2, count);
        try (var stmt = keepAlive.createStatement();
             var rs = stmt.executeQuery("SELECT name FROM users ORDER BY id")) {
            var names = new ArrayList<String>();
            while (rs.next()) {
                names.add(rs.getString("name"));
            }
            assertEquals(List.of("Alice", "Updated", "Updated"), names);
        }
    }

    @Test
    void updatePropagatesInvalidSqlFailure() {
        assertThrows(SQLException.class,
                () -> jdbcTemplate.update("UPDATE users SET WHERE id = ?", 1L));
    }

    @Test
    void queryReturnsEmptyListWithoutCallingMapperWhenNoRowsMatch() throws SQLException {
        List<User> result = jdbcTemplate.query(
                "SELECT id, name, email FROM users WHERE id = ?",
                (rs, rowNum) -> {
                    fail("An empty result must not invoke the mapper");
                    return null;
                }, 999L);

        assertEquals(List.of(), result);
    }

    @Test
    void queryReturnsSingleMappedObjectInList() throws SQLException {
        List<User> result = jdbcTemplate.query(
                "SELECT id, name, email FROM users WHERE id = ?", userMapper, 2L);

        assertEquals(List.of(new User(2L, "Bob", "bob@example.com")), result);
    }

    @Test
    void queryMapsAllRowsInQueryOrderWithIncreasingRowNumbers() throws SQLException {
        List<Integer> rowNumbers = new ArrayList<>();

        List<User> result = jdbcTemplate.query(
                "SELECT id, name, email FROM users ORDER BY id DESC",
                (rs, rowNum) -> {
                    rowNumbers.add(rowNum);
                    return userMapper.mapRow(rs, rowNum);
                });

        assertEquals(List.of(
                new User(3L, "Charlie", "charlie@example.com"),
                new User(2L, "Bob", "bob@example.com"),
                new User(1L, "Alice", "alice@example.com")
        ), result);
        assertEquals(List.of(0, 1, 2), rowNumbers);
    }

    @Test
    void queryBindsMultipleParametersInOrder() throws SQLException {
        List<User> result = jdbcTemplate.query(
                "SELECT id, name, email FROM users WHERE id >= ? AND name <> ? ORDER BY id",
                userMapper, 2L, "Charlie");

        assertEquals(List.of(new User(2L, "Bob", "bob@example.com")), result);
    }

    @Test
    void queryPropagatesSqlExecutionFailure() {
        assertThrows(SQLException.class, () -> jdbcTemplate.query(
                "SELECT id FROM missing_table", userMapper));
    }

    @Test
    void queryPropagatesMapperFailureOnLaterRow() {
        var failure = new SQLException("Mapping failed on second row");
        List<Integer> rowNumbers = new ArrayList<>();

        SQLException actual = assertThrows(SQLException.class,
                () -> jdbcTemplate.query(
                        "SELECT id, name, email FROM users ORDER BY id",
                        (rs, rowNum) -> {
                            rowNumbers.add(rowNum);
                            if (rowNum == 1) {
                                throw failure;
                            }
                            return userMapper.mapRow(rs, rowNum);
                        }));

        assertSame(failure, actual);
        assertEquals(List.of(0, 1), rowNumbers);
    }

    @Test
    void returnsMappedObjectForOneRow() throws SQLException {
        User result = jdbcTemplate.queryForObject(
                "SELECT id, name, email FROM users WHERE id = ?", userMapper, 2L);

        assertEquals(new User(2L, "Bob", "bob@example.com"), result);
    }

    @Test
    void returnsNullWithoutCallingMapperWhenNoRowsMatch() throws SQLException {
        User result = jdbcTemplate.queryForObject(
                "SELECT id, name, email FROM users WHERE id = ?",
                (rs, rowNum) -> {
                    fail("An empty result must not invoke the mapper");
                    return null;
                }, 999L);

        assertNull(result);
    }

    @Test
    void mapsOnlyFirstRowWithRowNumberZeroWhenMultipleRowsMatch() throws SQLException {
        var calls = new AtomicInteger();

        User result = jdbcTemplate.queryForObject(
                "SELECT id, name, email FROM users ORDER BY id DESC",
                (rs, rowNum) -> {
                    calls.incrementAndGet();
                    assertEquals(0, rowNum);
                    return userMapper.mapRow(rs, rowNum);
                });

        assertEquals(new User(3L, "Charlie", "charlie@example.com"), result);
        assertEquals(1, calls.get());
    }

    @Test
    void bindsMultipleParametersInOrder() throws SQLException {
        User result = jdbcTemplate.queryForObject(
                "SELECT id, name, email FROM users WHERE id = ? AND name = ?",
                userMapper, 2L, "Bob");

        assertEquals(new User(2L, "Bob", "bob@example.com"), result);
    }

    @Test
    void propagatesSqlExecutionFailure() {
        assertThrows(SQLException.class, () -> jdbcTemplate.queryForObject(
                "SELECT id FROM missing_table", userMapper));
    }

    @Test
    void propagatesMapperFailure() {
        var failure = new SQLException("Mapping failed");

        SQLException actual = assertThrows(SQLException.class,
                () -> jdbcTemplate.queryForObject(
                        "SELECT id FROM users WHERE id = ?",
                        (rs, rowNum) -> { throw failure; }, 1L));

        assertSame(failure, actual);
    }
}
