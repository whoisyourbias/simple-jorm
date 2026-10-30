package com.jorm.jdbc;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class BeanPropertyRowMapperTest {
    static class User {
        private Long id;
        private String name;
        User() {}
        User(Long id, String name) { this.id = id; this.name = name; }
    }

    @Test
    void manualAndReflectionMappersReturnSameValues() throws Exception {
        var ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:" + UUID.randomUUID());
        try (var connection = ds.getConnection(); var stmt = connection.createStatement()) {
            stmt.execute("CREATE TABLE users(id BIGINT, name VARCHAR(100))");
            stmt.execute("INSERT INTO users VALUES (1, 'Alice'), (2, 'Bob'), (3, NULL)");
            var template = new JdbcTemplate(ds);
            
            
            RowMapper<User> manual = (rs, row) -> new User(
                    rs.getObject("id", Long.class), 
                    rs.getString("name")
            );
            var expected = template.query("SELECT id, name FROM users ORDER BY id", manual);
            
            RowMapper<User> automatic = new BeanPropertyRowMapper<>(User.class);
            var actual = template.query("SELECT id, name FROM users ORDER BY id", automatic);
            
            
            assertEquals(3, actual.size());
            for (int i = 0; i < expected.size(); i++) {
                assertEquals(expected.get(i).id, actual.get(i).id);
                assertEquals(expected.get(i).name, actual.get(i).name);
            }
            assertNull(actual.get(2).name);
            assertEquals(List.of(), template.query("SELECT * FROM users WHERE id = 99", automatic));
            User partial = template.queryForObject("SELECT name AS name FROM users WHERE id = 1", automatic);
            assertEquals("Alice", partial.name);
            assertNull(partial.id);
        }
    }

    @Test
    void rejectsUnsupportedColumnsAndTypes() throws Exception {
        var ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:" + UUID.randomUUID());
        var template = new JdbcTemplate(ds);
        var mapper = new BeanPropertyRowMapper<>(User.class);
        assertThrows(SQLException.class, () -> template.query("SELECT 1 AS unknown", mapper));
        assertThrows(SQLException.class, () -> template.query("SELECT 'wrong' AS id", mapper));
    }

    @Test
    void resolvesColumnMappingOnlyOncePerResultSet() throws Exception {
        var ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:" + UUID.randomUUID());
        var mapper = new BeanPropertyRowMapper<>(User.class);
        var metadataReads = new AtomicInteger();
        try (var connection = ds.getConnection(); var statement = connection.createStatement();
             var result = statement.executeQuery(
                     "SELECT CAST(1 AS BIGINT) AS id, 'Alice' AS name UNION ALL SELECT 2, 'Bob'")) {
            ResultSet observed = (ResultSet) Proxy.newProxyInstance(
                    ResultSet.class.getClassLoader(), new Class<?>[]{ResultSet.class},
                    (proxy, method, args) -> {
                        if (method.getName().equals("equals")) return proxy == args[0];
                        if (method.getName().equals("hashCode")) return System.identityHashCode(proxy);
                        if (method.getName().equals("getMetaData")) metadataReads.incrementAndGet();
                        try {
                            return method.invoke(result, args);
                        } catch (InvocationTargetException e) {
                            throw e.getCause();
                        }
                    });
            assertTrue(observed.next());
            RowMapper<User> prepared = mapper.prepare(observed);
            User first = prepared.mapRow(observed, 0);
            assertEquals(1L, first.id);
            assertEquals("Alice", first.name);
            assertTrue(observed.next());
            User second = prepared.mapRow(observed, 1);
            assertEquals(2L, second.id);
            assertEquals("Bob", second.name);
            assertEquals(1, metadataReads.get());
        }
    }

    @Test
    void reusesMapperAcrossDifferentColumnOrdersAndPartialQueries() throws Exception {
        var ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:" + UUID.randomUUID());
        var template = new JdbcTemplate(ds);
        var mapper = new BeanPropertyRowMapper<>(User.class);
        User first = template.queryForObject("SELECT CAST(1 AS BIGINT) AS id, 'Alice' AS name", mapper);
        User reordered = template.queryForObject("SELECT 'Bob' AS name, CAST(2 AS BIGINT) AS id", mapper);
        User partial = template.queryForObject("SELECT 'Carol' AS \"NaMe\"", mapper);
        assertEquals(1L, first.id);
        assertEquals("Alice", first.name);
        assertEquals(2L, reordered.id);
        assertEquals("Bob", reordered.name);
        assertNull(partial.id);
        assertEquals("Carol", partial.name);
        assertThrows(SQLException.class, () -> template.query("SELECT 1 AS unknown", mapper));
    }

    @Test
    void templateUsesPreparedMapperOnceForEachNonEmptyQuery() throws Exception {
        var ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:" + UUID.randomUUID());
        var template = new JdbcTemplate(ds);
        var preparations = new AtomicInteger();
        RowMapper<String> mapper = new RowMapper<>() {
            @Override
            public RowMapper<String> prepare(ResultSet rs) {
                preparations.incrementAndGet();
                return (row, number) -> row.getString(1);
            }

            @Override
            public String mapRow(ResultSet rs, int rowNum) {
                fail("Template must use the prepared mapper");
                return null;
            }
        };
        assertEquals(List.of("Alice", "Bob"), template.query("SELECT 'Alice' UNION ALL SELECT 'Bob'", mapper));
        assertEquals(1, preparations.get());
        assertEquals("Carol", template.queryForObject("SELECT 'Carol'", mapper));
        assertEquals(2, preparations.get());
        assertTrue(template.query("SELECT 1 WHERE FALSE", mapper).isEmpty());
        assertNull(template.queryForObject("SELECT 1 WHERE FALSE", mapper));
        assertEquals(2, preparations.get());
    }

    @Test
    void preparedMappingsRemainIndependentWhenResultsAreInterleaved() throws Exception {
        var ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:" + UUID.randomUUID());
        var mapper = new BeanPropertyRowMapper<>(User.class);
        try (var connection = ds.getConnection();
             var firstStatement = connection.createStatement();
             var secondStatement = connection.createStatement();
             var first = firstStatement.executeQuery("SELECT CAST(1 AS BIGINT) AS id, 'Alice' AS name");
             var second = secondStatement.executeQuery("SELECT 'Bob' AS name, CAST(2 AS BIGINT) AS id")) {
            assertTrue(first.next());
            assertTrue(second.next());
            var firstMapper = mapper.prepare(first);
            var secondMapper = mapper.prepare(second);
            User bob = secondMapper.mapRow(second, 0);
            User alice = firstMapper.mapRow(first, 0);
            assertEquals(2L, bob.id);
            assertEquals("Bob", bob.name);
            assertEquals(1L, alice.id);
            assertEquals("Alice", alice.name);
        }
    }

    @Test
    void rejectsClassesWithoutDefaultConstructor() {
        assertThrows(IllegalArgumentException.class,
                () -> new BeanPropertyRowMapper<>(Integer.class));
    }
}
