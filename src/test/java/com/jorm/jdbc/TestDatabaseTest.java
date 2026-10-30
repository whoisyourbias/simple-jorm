package com.jorm.jdbc;

import org.h2.tools.RunScript;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestDatabaseTest {

    private Connection connection;

    @BeforeEach
    void setUp() throws Exception {
        connection = DriverManager.getConnection("jdbc:h2:mem:" + UUID.randomUUID(), "sa", "");
        executeScript("/schema.sql");
        executeScript("/data.sql");
    }

    @AfterEach
    void tearDown() throws SQLException {
        if (connection != null) {
            connection.close();
        }
    }

    @Test
    void loadsSampleUsers() throws SQLException {
        try (var statement = connection.createStatement();
             var resultSet = statement.executeQuery("SELECT id, name, email FROM users ORDER BY id")) {
            var users = new ArrayList<List<Object>>();
            while (resultSet.next()) {
                users.add(List.of(resultSet.getLong("id"), resultSet.getString("name"),
                        resultSet.getString("email")));
            }

            assertEquals(List.of(
                    List.of(1L, "Alice", "alice@example.com"),
                    List.of(2L, "Bob", "bob@example.com"),
                    List.of(3L, "Charlie", "charlie@example.com")
            ), users);
        }
    }

    @Test
    void insertsUserWithGeneratedId() throws SQLException {
        try (var statement = connection.prepareStatement(
                "INSERT INTO users (name, email) VALUES (?, ?)", Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, "David");
            statement.setString(2, "david@example.com");

            assertEquals(1, statement.executeUpdate());
            try (var keys = statement.getGeneratedKeys()) {
                assertTrue(keys.next());
                assertEquals(4L, keys.getLong(1));
                assertFalse(keys.next());
            }
        }
    }

    private void executeScript(String resource) throws Exception {
        var stream = getClass().getResourceAsStream(resource);
        assertNotNull(stream, "Missing test SQL resource: " + resource);
        try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            RunScript.execute(connection, reader);
        }
    }
}
