package com.jorm.jdbc;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
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
                    rs.getObject("id", Long.class), rs.getString("name"));
            RowMapper<User> automatic = new BeanPropertyRowMapper<>(User.class);
            var expected = template.query("SELECT id, name FROM users ORDER BY id", manual);
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
    void rejectsClassesWithoutDefaultConstructor() {
        assertThrows(IllegalArgumentException.class,
                () -> new BeanPropertyRowMapper<>(Integer.class));
    }
}
