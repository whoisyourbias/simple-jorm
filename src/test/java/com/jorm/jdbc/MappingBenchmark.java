package com.jorm.jdbc;

import java.sql.DriverManager;
import java.sql.ResultSet;
import java.util.Arrays;
import java.util.Locale;

/** Exploratory benchmark; SQL execution and mapper construction are outside timing. */
public class MappingBenchmark {
    private static volatile Object sink;
    private static final int ROWS = 10_000;
    private static final int PASSES = 20;

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
        RowMapper<User> manual = (rs, row) -> new User(
                rs.getObject("id", Long.class), rs.getString("name"), rs.getString("email"));
        RowMapper<User> reflective = new BeanPropertyRowMapper<>(User.class);
        try (var connection = DriverManager.getConnection("jdbc:h2:mem:benchmark");
             var statement = connection.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE,
                     ResultSet.CONCUR_READ_ONLY);
             var rs = statement.executeQuery("SELECT X AS id, 'User' || X AS name, "
                     + "'user' || X || '@example.com' AS email FROM SYSTEM_RANGE(1, " + ROWS + ")")) {
            // Materialize the result before timing either mapper.
            rs.last();
            for (int i = 0; i < 5; i++) {
                measure(rs, manual);
                measure(rs, reflective);
            }
            double[] direct = new double[9];
            double[] reflection = new double[9];
            for (int i = 0; i < direct.length; i++) {
                // Alternate order to reduce bias from warmup and GC.
                if (i % 2 == 0) {
                    direct[i] = measure(rs, manual);
                    reflection[i] = measure(rs, reflective);
                } else {
                    reflection[i] = measure(rs, reflective);
                    direct[i] = measure(rs, manual);
                }
            }
            Arrays.sort(direct);
            Arrays.sort(reflection);
            System.out.println("Java: " + System.getProperty("java.version"));
            System.out.printf(Locale.ROOT,
                    "%,d rows/sample, 5 warmups, 9 samples; median [min,max] ns/row%n",
                    ROWS * PASSES);
            System.out.printf(Locale.ROOT, "manual: %.1f [%.1f, %.1f]%n", direct[4], direct[0], direct[8]);
            System.out.printf(Locale.ROOT, "reflection: %.1f [%.1f, %.1f]%n",
                    reflection[4], reflection[0], reflection[8]);
            System.out.printf(Locale.ROOT, "reflection/manual: %.2fx%n", reflection[4] / direct[4]);
        }
    }

    private static double measure(ResultSet rs, RowMapper<User> mapper) throws Exception {
        // Retain all objects until the batch ends so allocations cannot be eliminated.
        User[] batch = new User[ROWS * PASSES];
        int index = 0;
        long start = System.nanoTime();
        for (int pass = 0; pass < PASSES; pass++) {
            rs.beforeFirst();
            int rowNum = 0;
            while (rs.next()) {
                batch[index++] = mapper.mapRow(rs, rowNum++);
            }
        }
        long elapsed = System.nanoTime() - start;
        if (index != batch.length || batch[0].id != 1L
                || batch[index - 1].id != ROWS) {
            throw new AssertionError("Unexpected mapped rows");
        }
        sink = batch;
        return (double) elapsed / index;
    }
}
