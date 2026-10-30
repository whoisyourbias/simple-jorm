package com.jorm.jdbc;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Arrays;
import java.util.Locale;
import java.util.Properties;

/** Local MySQL experiment: connection acquisition and fixture setup are not timed. */
public class DatabaseMappingBenchmark {
    private static volatile Object sink;
    private static final int ROWS = 10_000;
    private static final int QUERIES = 20;
    private static final String DATABASE = "simple_jorm_mapping_benchmark";

    static class User {
        private Long id;
        private String name;
        private String email;
        User() {}
        User(Long id, String name, String email) {
            this.id = id;
            this.name = name;
            this.email = email;
        }
    }

    public static void main(String[] args) throws Exception {
        String url = System.getenv("JORM_DB_URL");
        if (url == null) {
            throw new IllegalArgumentException("Set JORM_DB_URL to a MySQL server URL without a database");
        }
        Properties credentials = new Properties();
        credentials.setProperty("user", required("JORM_DB_USER"));
        credentials.setProperty("password", required("JORM_DB_PASSWORD"));
        try (Connection connection = DriverManager.getConnection(url, credentials)) {
            try (var stmt = connection.createStatement()) {
                stmt.execute("CREATE DATABASE IF NOT EXISTS " + DATABASE);
            }
            connection.setCatalog(DATABASE);
            // Session-scoped table disappears on disconnect; existing tables are untouched.
            try (var stmt = connection.createStatement()) {
                stmt.execute("CREATE TEMPORARY TABLE mapping_benchmark_users "
                        + "(id BIGINT PRIMARY KEY, name VARCHAR(100), email VARCHAR(150))");
            }
            connection.setAutoCommit(false);
            try (var insert = connection.prepareStatement(
                    "INSERT INTO mapping_benchmark_users VALUES (?, ?, ?)")) {
                for (long i = 1; i <= ROWS; i++) {
                    insert.setLong(1, i);
                    insert.setString(2, "User" + i);
                    insert.setString(3, "user" + i + "@example.com");
                    insert.addBatch();
                    if (i % 1000 == 0) insert.executeBatch();
                }
            }
            connection.commit();
            connection.setAutoCommit(true);
            RowMapper<User> manual = (rs, row) -> new User(
                    rs.getObject("id", Long.class), rs.getString("name"), rs.getString("email"));
            RowMapper<User> reflection = new BeanPropertyRowMapper<>(User.class);
            for (int i = 0; i < 5; i++) {
                measure(connection, manual);
                measure(connection, reflection);
            }
            double[] direct = new double[9];
            double[] automatic = new double[9];
            for (int i = 0; i < 9; i++) {
                if (i % 2 == 0) {
                    direct[i] = measure(connection, manual);
                    automatic[i] = measure(connection, reflection);
                } else {
                    automatic[i] = measure(connection, reflection);
                    direct[i] = measure(connection, manual);
                }
            }
            Arrays.sort(direct);
            Arrays.sort(automatic);
            System.out.println("Server: " + connection.getMetaData().getDatabaseProductVersion());
            System.out.println("Java: " + System.getProperty("java.version"));
            System.out.println("10,000 rows/query, 20 queries/sample, 5 warmups, 9 samples");
            System.out.printf(Locale.ROOT, "manual median [min,max]: %.3f [%.3f,%.3f] ms/query%n",
                    direct[4], direct[0], direct[8]);
            System.out.printf(Locale.ROOT, "reflection median [min,max]: %.3f [%.3f,%.3f] ms/query%n",
                    automatic[4], automatic[0], automatic[8]);
            System.out.printf(Locale.ROOT, "reflection/manual: %.2fx%n", automatic[4] / direct[4]);
        }
    }

    private static double measure(Connection connection, RowMapper<User> mapper) throws Exception {
        long start = System.nanoTime();
        for (int query = 0; query < QUERIES; query++) {
            User[] users = new User[ROWS];
            try (var stmt = connection.prepareStatement(
                    "SELECT id, name, email FROM mapping_benchmark_users ORDER BY id");
                 var rs = stmt.executeQuery()) {
                int row = 0;
                while (rs.next()) {
                    users[row] = mapper.mapRow(rs, row);
                    row++;
                }
                if (row != ROWS || users[0].id != 1L || users[ROWS - 1].id != ROWS
                        || !"User1".equals(users[0].name)
                        || !"user10000@example.com".equals(users[ROWS - 1].email)) {
                    throw new AssertionError("Unexpected mapped data");
                }
            }
            sink = users;
        }
        return (System.nanoTime() - start) / 1_000_000.0 / QUERIES;
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null) throw new IllegalArgumentException("Set " + name);
        return value;
    }
}
