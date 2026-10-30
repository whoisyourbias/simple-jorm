package com.jorm.jdbc.lab;

import org.apache.commons.dbutils.QueryRunner;
import org.apache.commons.dbutils.handlers.BeanListHandler;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.*;

/** Paired library API comparison at 10,000 rows and 30 columns. */
public final class LibraryMappingBenchmark {
    @FunctionalInterface
    interface Query { List<ScalingRows.Row30> run() throws Exception; }
    record Group(String name, Query query) {}
    private static volatile Object sink;

    public static void main(String[] args) throws Exception {
        String fork = Objects.requireNonNull(System.getenv("JORM_BENCH_FORK"));
        Path output = Path.of(Objects.requireNonNull(System.getenv("JORM_BENCH_OUTPUT")));
        Files.createDirectories(output);
        var properties = new Properties();
        properties.setProperty("user", Objects.requireNonNull(System.getenv("JORM_DB_USER")));
        properties.setProperty("password", Objects.requireNonNull(System.getenv("JORM_DB_PASSWORD")));
        try (var connection = DriverManager.getConnection(System.getenv("JORM_DB_URL"), properties);
             var csv = new PrintWriter(Files.newBufferedWriter(output.resolve("library-fork-" + fork + ".csv")))) {
            connection.setCatalog("simple_jorm_mapping_benchmark");
            StringBuilder ddl = new StringBuilder("CREATE TEMPORARY TABLE library_rows (id BIGINT PRIMARY KEY");
            for (int col = 2; col <= 30; col++) ddl.append(", f").append(col).append(" VARCHAR(40)");
            try (var stmt = connection.createStatement()) { stmt.execute(ddl.append(')').toString()); }
            connection.setAutoCommit(false);
            try (var stmt = connection.prepareStatement("INSERT INTO library_rows VALUES ("
                    + String.join(",", Collections.nCopies(30, "?")) + ")")) {
                for (int row = 1; row <= 10000; row++) {
                    stmt.setLong(1, row);
                    for (int col = 2; col <= 30; col++) stmt.setString(col, MappingScalingBenchmark.expected(row, col));
                    stmt.addBatch();
                    if (row % 1000 == 0) stmt.executeBatch();
                }
                connection.commit();
            }
            connection.setAutoCommit(true);
            String sql = "SELECT * FROM library_rows ORDER BY id";
            // close() on Spring's proxy returns without closing the shared physical connection.
            var spring = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
            var springMapper = new org.springframework.jdbc.core.BeanPropertyRowMapper<>(ScalingRows.Row30.class);
            var dbutils = new QueryRunner();
            var beanList = new BeanListHandler<>(ScalingRows.Row30.class);
            var groups = List.of(
                    new Group("spring-manual", () -> spring.query(sql, ScalingRows::manual30)),
                    new Group("spring-bean", () -> spring.query(sql, springMapper)),
                    new Group("dbutils-manual", () -> dbutils.query(connection, sql,
                            rs -> MappingScalingBenchmark.readRows(rs, ScalingRows::manual30))),
                    new Group("dbutils-bean-list", () -> dbutils.query(connection, sql, beanList)));
            for (var group : groups) MappingScalingBenchmark.validate(group.query.run(), 10000, 30);
            var random = new Random(20261030L + Integer.parseInt(fork));
            csv.println("fork,group,sample,repetitions,ms_per_query");
            for (int round = -2; round < 5; round++) {
                var order = new ArrayList<>(groups);
                Collections.shuffle(order, random);
                for (var group : order) {
                    sink = null;
                    long start = System.nanoTime();
                    for (int repeat = 0; repeat < 2; repeat++) sink = group.query.run();
                    double elapsed = (System.nanoTime() - start) / 1_000_000.0 / 2;
                    if (round >= 0) {
                        csv.printf(Locale.ROOT, "%s,%s,%d,2,%.6f%n", fork, group.name, round + 1, elapsed);
                        csv.flush();
                    }
                }
            }
            System.out.println("Library comparison fork " + fork + " validated and measured");
        }
    }
}
