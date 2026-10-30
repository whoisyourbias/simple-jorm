package com.jorm.jdbc.lab;

import com.jorm.jdbc.BeanPropertyRowMapper;
import com.jorm.jdbc.RowMapper;
import org.apache.commons.dbutils.BeanProcessor;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.time.Instant;
import java.util.*;

/** Controlled component comparison, not a whole-framework benchmark or JMH benchmark. */
public final class MappingScalingBenchmark {
    private static volatile Object sink;
    private static final String DATABASE = "simple_jorm_mapping_benchmark";
    private static final int[] ROW_COUNTS = {100, 1_000, 10_000, 100_000};
    private static final int WARMUPS = 2;
    private static final int SAMPLES = 5;
    @FunctionalInterface
    interface Reader { List<? extends ScalingRows.Row> read(ResultSet rs) throws SQLException; }
    record Group(String name, Reader reader) {}

    public static void main(String[] args) throws Exception {
        int fork = Integer.parseInt(System.getenv().getOrDefault("JORM_BENCH_FORK", "1"));
        Path output = Path.of(System.getenv().getOrDefault("JORM_BENCH_OUTPUT", "doc/lab/results"));
        Files.createDirectories(output);
        Properties credentials = new Properties();
        credentials.setProperty("user", required("JORM_DB_USER"));
        credentials.setProperty("password", required("JORM_DB_PASSWORD"));
        try (Connection connection = DriverManager.getConnection(required("JORM_DB_URL"), credentials);
             PrintWriter csv = new PrintWriter(Files.newBufferedWriter(output.resolve("scaling-fork-" + fork + ".csv")));
             PrintWriter env = new PrintWriter(Files.newBufferedWriter(output.resolve("environment-fork-" + fork + ".txt")))) {
            connection.setCatalog(DATABASE);
            environment(connection, env, fork);
            csv.println("fork,scope,rows,columns,group,sample,repetitions,total_ns,ms_per_query,ns_per_row,gc_count,gc_ms");
            runWidth(connection, csv, fork, 3, ScalingRows.Row3.class, ScalingRows::manual3);
            runWidth(connection, csv, fork, 10, ScalingRows.Row10.class, ScalingRows::manual10);
            runWidth(connection, csv, fork, 30, ScalingRows.Row30.class, ScalingRows::manual30);
        }
    }

    private static <T extends ScalingRows.Row> void runWidth(Connection c, PrintWriter csv, int fork,
            int columns, Class<T> type, RowMapper<T> manual) throws Exception {
        String table = "scaling_rows_" + columns;
        StringBuilder ddl = new StringBuilder("CREATE TEMPORARY TABLE " + table + " (id BIGINT PRIMARY KEY");
        for (int col = 2; col <= columns; col++) ddl.append(", f").append(col).append(" VARCHAR(40)");
        try (var stmt = c.createStatement()) { stmt.execute(ddl.append(')').toString()); }
        String placeholders = String.join(",", Collections.nCopies(columns, "?"));
        c.setAutoCommit(false);
        try (var insert = c.prepareStatement("INSERT INTO " + table + " VALUES (" + placeholders + ")")) {
            for (int row = 1; row <= 100_000; row++) {
                insert.setLong(1, row);
                for (int col = 2; col <= columns; col++) insert.setString(col, expected(row, col));
                insert.addBatch();
                if (row % 1000 == 0) insert.executeBatch();
            }
            c.commit();
        } finally { c.setAutoCommit(true); }
        var reflection = new BeanPropertyRowMapper<>(type);
        var spring = new org.springframework.jdbc.core.BeanPropertyRowMapper<>(type);
        var dbutils = new BeanProcessor();
        List<Group> groups = List.of(
                new Group("manual", rs -> readRows(rs, manual)),
                new Group("jorm-reflection", rs -> readRows(rs, reflection)),
                new Group("spring-bean", rs -> readRows(rs, spring::mapRow)),
                new Group("dbutils-bean-list", rs -> dbutils.toBeanList(rs, type)));
        for (int rows : ROW_COUNTS) {
            String sql = "SELECT * FROM " + table + " WHERE id <= " + rows + " ORDER BY id";
            // Validate every field of every row, outside the timing window.
            for (Group group : groups) {
                try (var stmt = c.prepareStatement(sql); var rs = stmt.executeQuery()) {
                    validate(group.reader.read(rs), rows, columns);
                }
            }
            for (String scope : List.of("buffered-mapping", "sql-fetch-mapping")) {
                try (var stmt = scope.equals("buffered-mapping")
                            ? c.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY) : null;
                     var buffered = stmt == null ? null : stmt.executeQuery(sql)) {
                    if (buffered != null) buffered.last();
                    Random random = new Random(20261030L + fork * 1000L + columns * 10L + rows);
                    int repetitions = Math.max(1, 20_000 / rows);
                    for (int round = -WARMUPS; round < SAMPLES; round++) {
                        var order = new ArrayList<>(groups);
                        Collections.shuffle(order, random);
                        for (Group group : order) {
                            sink = null;
                            long gcCount = gcCount();
                            long gcTime = gcTime();
                            long start = System.nanoTime();
                            for (int repeat = 0; repeat < repetitions; repeat++) {
                                if (scope.equals("buffered-mapping")) {
                                    buffered.beforeFirst();
                                    sink = group.reader.read(buffered);
                                } else {
                                    try (var query = c.prepareStatement(sql); var rs = query.executeQuery()) {
                                        sink = group.reader.read(rs);
                                    }
                                }
                            }
                            long elapsed = System.nanoTime() - start;
                            if (round >= 0) {
                                double ms = elapsed / 1_000_000.0 / repetitions;
                                csv.printf(Locale.ROOT, "%d,%s,%d,%d,%s,%d,%d,%d,%.6f,%.3f,%d,%d%n",
                                        fork, scope, rows, columns, group.name, round + 1, repetitions,
                                        elapsed, ms, (double) elapsed / repetitions / rows,
                                        gcCount() - gcCount, gcTime() - gcTime);
                                csv.flush();
                            }
                        }
                    }
                }
            }
            System.out.printf("fork=%d columns=%d rows=%d validated and measured%n", fork, columns, rows);
        }
    }

    static String expected(int row, int column) {
        return (row + column) % 17 == 0 ? null : "v" + row + "_" + column;
    }

    static void validate(List<? extends ScalingRows.Row> result, int rows, int columns) {
        if (result.size() != rows) throw new AssertionError("Wrong row count");
        for (int row = 1; row <= rows; row++) {
            var value = result.get(row - 1);
            if (!Long.valueOf(row).equals(value.value(1))) throw new AssertionError("Wrong id");
            for (int col = 2; col <= columns; col++) {
                if (!Objects.equals(expected(row, col), value.value(col))) {
                    throw new AssertionError("Wrong value at row=" + row + " column=" + col);
                }
            }
        }
    }

    static <T extends ScalingRows.Row> List<T> readRows(ResultSet rs, RowMapper<T> mapper) throws SQLException {
        List<T> values = new ArrayList<>();
        int row = 0;
        while (rs.next()) values.add(mapper.mapRow(rs, row++));
        return values;
    }

    private static void environment(Connection c, PrintWriter out, int fork) throws SQLException {
        out.println("timestamp_utc=" + Instant.now());
        out.println("fork=" + fork);
        out.println("java=" + System.getProperty("java.runtime.version"));
        out.println("vm=" + System.getProperty("java.vm.name"));
        out.println("vendor=" + System.getProperty("java.vendor"));
        out.println("os=" + System.getProperty("os.name") + " " + System.getProperty("os.version")
                + " " + System.getProperty("os.arch"));
        out.println("jvm_args=" + java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments());
        out.println("processors=" + Runtime.getRuntime().availableProcessors());
        out.println("db=" + c.getMetaData().getDatabaseProductVersion());
        out.println("driver=" + c.getMetaData().getDriverVersion());
        out.println("connection=existing localhost MySQL TCP connection; no pool; autoCommit=true");
        out.println("warmups=" + WARMUPS + "; samples=" + SAMPLES + "; seed=20261030+fork*1000+columns*10+rows");
        out.println("spring=6.2.12; dbutils=1.8.1; result mode=buffered, no streaming/cursor fetch");
        try (var stmt = c.createStatement(); var rs = stmt.executeQuery(
                "SHOW VARIABLES WHERE Variable_name IN ('version_compile_os','version_compile_machine',"
                + "'innodb_buffer_pool_size','max_connections','character_set_server','collation_server')")) {
            while (rs.next()) out.println(rs.getString(1) + "=" + rs.getString(2));
        }
        out.flush();
    }

    private static String required(String key) {
        String value = System.getenv(key);
        if (value == null) throw new IllegalArgumentException("Set " + key);
        return value;
    }

    private static long gcCount() {
        return java.lang.management.ManagementFactory.getGarbageCollectorMXBeans().stream()
                .mapToLong(bean -> Math.max(0, bean.getCollectionCount())).sum();
    }

    private static long gcTime() {
        return java.lang.management.ManagementFactory.getGarbageCollectorMXBeans().stream()
                .mapToLong(bean -> Math.max(0, bean.getCollectionTime())).sum();
    }
}
