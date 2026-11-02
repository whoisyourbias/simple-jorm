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
    /** ResultSet 전체를 실험용 행 객체 리스트로 변환하는 함수 계약이다. */
    @FunctionalInterface
    interface Reader { List<? extends ScalingRows.Row> read(ResultSet rs) throws SQLException; }
    /** 비교 그룹의 이름과 실행할 읽기 함수를 함께 보관한다. */
    record Group(String name, Reader reader) {}

    /** 3·10·30컬럼과 여러 행 수의 매핑 검증·측정을 실행한다. */
    public static void main(String[] args) throws Exception {
        // 환경 변수에서 실행 설정을 읽는다.
        int fork = Integer.parseInt(System.getenv().getOrDefault("JORM_BENCH_FORK", "1"));
        // 환경 변수에서 실행 설정을 읽는다.
        Path output = Path.of(System.getenv().getOrDefault("JORM_BENCH_OUTPUT", "doc/lab/results"));
        // 결과 디렉터리가 없으면 만든다.
        Files.createDirectories(output);
        // DB 연결 속성을 담을 객체를 만든다.
        Properties credentials = new Properties();
        // DB 접속 사용자·비밀번호를 연결 속성에 넣는다. 화면에는 출력하지 않는다.
        credentials.setProperty("user", required("JORM_DB_USER"));
        // DB 접속 사용자·비밀번호를 연결 속성에 넣는다. 화면에는 출력하지 않는다.
        credentials.setProperty("password", required("JORM_DB_PASSWORD"));
        // 연결·Statement·파일 자원을 열고 try-with-resources로 사용 후 자동 정리한다.
        try (Connection connection = DriverManager.getConnection(required("JORM_DB_URL"), credentials);
             // 표본 CSV 또는 실행 환경을 기록할 출력 파일을 연다.
             PrintWriter csv = new PrintWriter(Files.newBufferedWriter(output.resolve("scaling-fork-" + fork + ".csv")));
             // 표본 CSV 또는 실행 환경을 기록할 출력 파일을 연다.
             PrintWriter env = new PrintWriter(Files.newBufferedWriter(output.resolve("environment-fork-" + fork + ".txt")))) {
            // 현재 연결에서 실험용 데이터베이스를 선택한다.
            connection.setCatalog(DATABASE);
            // 실험 재현을 위한 실행 환경을 파일에 기록한다.
            environment(connection, env, fork);
            // CSV 측정 결과의 컬럼 이름을 기록한다.
            csv.println("fork,scope,rows,columns,group,sample,repetitions,total_ns,ms_per_query,ns_per_row,gc_count,gc_ms");
            // 지정 컬럼 수와 매퍼로 행 수별 실험을 실행한다.
            runWidth(connection, csv, fork, 3, ScalingRows.Row3.class, ScalingRows::manual3);
            // 지정 컬럼 수와 매퍼로 행 수별 실험을 실행한다.
            runWidth(connection, csv, fork, 10, ScalingRows.Row10.class, ScalingRows::manual10);
            // 지정 컬럼 수와 매퍼로 행 수별 실험을 실행한다.
            runWidth(connection, csv, fork, 30, ScalingRows.Row30.class, ScalingRows::manual30);
        }
    }

    // 지정 컬럼 수와 매퍼로 행 수별 실험을 실행한다.
    private static <T extends ScalingRows.Row> void runWidth(Connection c, PrintWriter csv, int fork,
            int columns, Class<T> type, RowMapper<T> manual) throws Exception {
        // 컬럼 수를 포함하는 세션 전용 임시 테이블 이름을 정한다.
        String table = "scaling_rows_" + columns;
        // 컬럼 수에 맞는 테이블 생성 SQL을 조립한다.
        StringBuilder ddl = new StringBuilder("CREATE TEMPORARY TABLE " + table + " (id BIGINT PRIMARY KEY");
        // 컬럼 번호 순서대로 스키마·값 바인딩·검증을 처리한다.
        for (int col = 2; col <= columns; col++) ddl.append(", f").append(col).append(" VARCHAR(40)");
        // 연결·Statement·파일 자원을 열고 try-with-resources로 사용 후 자동 정리한다.
        try (var stmt = c.createStatement()) { stmt.execute(ddl.append(')').toString()); }
        // 컬럼 수만큼의 SQL 물음표를 쉼표로 연결한다.
        String placeholders = String.join(",", Collections.nCopies(columns, "?"));
        // 배치 데이터 준비를 위한 자동 커밋 설정을 변경한다.
        c.setAutoCommit(false);
        // 물음표 바인딩용 PreparedStatement를 만들고 블록 종료 시 닫는다.
        try (var insert = c.prepareStatement("INSERT INTO " + table + " VALUES (" + placeholders + ")")) {
            // 1부터 지정 행 수까지 데이터 생성 또는 결과 검증을 반복한다.
            for (int row = 1; row <= 100_000; row++) {
                // JDBC 파라미터 위치에 현재 행 또는 조건 값을 바인딩한다.
                insert.setLong(1, row);
                // 컬럼 번호 순서대로 스키마·값 바인딩·검증을 처리한다.
                for (int col = 2; col <= columns; col++) insert.setString(col, expected(row, col));
                // 현재 행의 파라미터를 삽입 배치에 추가한다.
                insert.addBatch();
                // 1,000행마다 모아 둔 삽입 배치를 실행한다.
                if (row % 1000 == 0) insert.executeBatch();
            }
            c.commit();
            // 성공·실패와 관계없이 연결 상태와 스레드 자원을 정리한다.
        } finally { c.setAutoCommit(true); }
        // 대상 클래스의 필드에 값을 저장할 자동 매퍼를 만든다.
        var reflection = new BeanPropertyRowMapper<>(type);
        // 동일 결과 타입에 사용할 Spring의 자동 필드 매퍼를 만든다.
        var spring = new org.springframework.jdbc.core.BeanPropertyRowMapper<>(type);
        // ResultSet 전체를 JavaBean 리스트로 변환할 DbUtils 도구를 만든다.
        var dbutils = new BeanProcessor();
        // 비교 그룹 또는 기대값을 불변 리스트로 구성한다.
        List<Group> groups = List.of(
                new Group("manual", rs -> readRows(rs, manual)),
                new Group("jorm-reflection", rs -> readRows(rs, reflection)),
                new Group("spring-bean", rs -> readRows(rs, spring::mapRow)),
                new Group("dbutils-bean-list", rs -> dbutils.toBeanList(rs, type)));
        // 행 수 조건을 바꿔 같은 실험을 반복한다.
        for (int rows : ROW_COUNTS) {
            // 현재 처리할 조회 또는 변경 SQL을 결정한다.
            String sql = "SELECT * FROM " + table + " WHERE id <= " + rows + " ORDER BY id";
            // Validate every field of every row, outside the timing window.
            // 각 비교 그룹의 매퍼·쿼리를 정해진 순서로 실행한다.
            for (Group group : groups) {
                // 물음표 바인딩용 PreparedStatement를 만들고 블록 종료 시 닫는다.
                try (var stmt = c.prepareStatement(sql); var rs = stmt.executeQuery()) {
                    // 측정 전에 모든 행과 컬럼 값이 올바른지 검증한다.
                    validate(group.reader.read(rs), rows, columns);
                }
            }
            // 버퍼 매핑과 SQL·조회 포함 측정 범위를 각각 처리한다.
            for (String scope : List.of("buffered-mapping", "sql-fetch-mapping")) {
                // 연결·Statement·파일 자원을 열고 try-with-resources로 사용 후 자동 정리한다.
                try (var stmt = scope.equals("buffered-mapping")
                            ? c.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY) : null;
                     // SELECT를 실행해 커서와 컬럼 메타데이터를 제공하는 ResultSet을 얻는다.
                     var buffered = stmt == null ? null : stmt.executeQuery(sql)) {
                    // 버퍼 결과를 재사용하는 매핑 측정인지 구분한다.
                    if (buffered != null) buffered.last();
                    // 실행 순서가 재현되도록 고정 규칙의 난수 시드를 사용한다.
                    Random random = new Random(20261030L + fork * 1000L + columns * 10L + rows);
                    // 작은 결과는 반복을 늘리고 최소 한 번 측정하도록 반복 수를 정한다.
                    int repetitions = Math.max(1, 20_000 / rows);
                    // 음수 회차는 워밍업, 이후 회차는 본 측정으로 반복한다.
                    for (int round = -WARMUPS; round < SAMPLES; round++) {
                        // 결과 또는 실행 순서를 담을 리스트를 준비한다.
                        var order = new ArrayList<>(groups);
                        // 재현 가능한 난수로 매퍼 실행 순서를 섞어 순서 편향을 줄인다.
                        Collections.shuffle(order, random);
                        // 각 비교 그룹의 매퍼·쿼리를 정해진 순서로 실행한다.
                        for (Group group : order) {
                            // 결과 참조를 보존하거나 해제해 실험 객체의 생존 조건을 설정한다.
                            sink = null;
                            // 측정 직전의 누적 GC 횟수를 기록한다.
                            long gcCount = gcCount();
                            // 측정 직전의 누적 GC 시간을 기록한다.
                            long gcTime = gcTime();
                            // 단조 증가 시계로 시작 시각 또는 경과 나노초를 계산한다.
                            long start = System.nanoTime();
                            // 같은 조회 또는 매핑을 반복해 한 표본의 시간을 측정한다.
                            for (int repeat = 0; repeat < repetitions; repeat++) {
                                // 버퍼 결과를 재사용하는 매핑 측정인지 구분한다.
                                if (scope.equals("buffered-mapping")) {
                                    // 결과를 다시 순회하도록 커서를 첫 행 이전으로 되돌린다.
                                    buffered.beforeFirst();
                                    // 결과 참조를 보존하거나 해제해 실험 객체의 생존 조건을 설정한다.
                                    sink = group.reader.read(buffered);
                                } else {
                                    // 물음표 바인딩용 PreparedStatement를 만들고 블록 종료 시 닫는다.
                                    try (var query = c.prepareStatement(sql); var rs = query.executeQuery()) {
                                        // 결과 참조를 보존하거나 해제해 실험 객체의 생존 조건을 설정한다.
                                        sink = group.reader.read(rs);
                                    }
                                }
                            }
                            // 단조 증가 시계로 시작 시각 또는 경과 나노초를 계산한다.
                            long elapsed = System.nanoTime() - start;
                            // 워밍업을 제외하고 본 측정 회차만 기록한다.
                            if (round >= 0) {
                                // 총 시간을 반복 수로 나누어 쿼리당 측정 시간을 계산한다.
                                double ms = elapsed / 1_000_000.0 / repetitions;
                                // 현재 측정의 조건과 소요 시간 등을 CSV 한 행으로 기록한다.
                                csv.printf(Locale.ROOT, "%d,%s,%d,%d,%s,%d,%d,%d,%.6f,%.3f,%d,%d%n",
                                        fork, scope, rows, columns, group.name, round + 1, repetitions,
                                        elapsed, ms, (double) elapsed / repetitions / rows,
                                        gcCount() - gcCount, gcTime() - gcTime);
                                // 버퍼에 남은 기록을 출력 파일에 반영한다.
                                csv.flush();
                            }
                        }
                    }
                }
            }
            // 실행 환경·진행 상황 또는 비교 결과를 출력한다.
            System.out.printf("fork=%d columns=%d rows=%d validated and measured%n", fork, columns, rows);
        }
    }

    /** 행·컬럼 번호로 재현 가능한 문자열 또는 null인 실험 데이터를 만든다. */
    static String expected(int row, int column) {
        // 행·컬럼 합이 17의 배수면 null, 그 외에는 고정 규칙의 문자열을 반환한다.
        return (row + column) % 17 == 0 ? null : "v" + row + "_" + column;
    }

    /** 측정과 별도로 결과 행 수, ID, 모든 컬럼 값을 검증한다. */
    static void validate(List<? extends ScalingRows.Row> result, int rows, int columns) {
        // 실제 결과 행 수가 요청한 행 수와 같은지 확인한다.
        if (result.size() != rows) throw new AssertionError("Wrong row count");
        // 1부터 지정 행 수까지 데이터 생성 또는 결과 검증을 반복한다.
        for (int row = 1; row <= rows; row++) {
            // 1부터 시작하는 행 번호를 리스트의 0 기반 인덱스로 바꾸어 객체를 꺼낸다.
            var value = result.get(row - 1);
            // 현재 셀 또는 ID가 데이터 생성 규칙의 기대값과 일치하는지 확인한다.
            if (!Long.valueOf(row).equals(value.value(1))) throw new AssertionError("Wrong id");
            // 컬럼 번호 순서대로 스키마·값 바인딩·검증을 처리한다.
            for (int col = 2; col <= columns; col++) {
                // 현재 셀 또는 ID가 데이터 생성 규칙의 기대값과 일치하는지 확인한다.
                if (!Objects.equals(expected(row, col), value.value(col))) {
                    // 실험 데이터가 기대값과 달라 측정을 중단한다.
                    throw new AssertionError("Wrong value at row=" + row + " column=" + col);
                }
            }
        }
    }

    static <T extends ScalingRows.Row> List<T> readRows(ResultSet rs, RowMapper<T> mapper) throws SQLException {
        // 결과 또는 실행 순서를 담을 리스트를 준비한다.
        List<T> values = new ArrayList<>();
        // 행 번호 또는 처리 행 수를 기록할 카운터를 0으로 초기화한다.
        int row = 0;
        // 다음 행으로 커서를 옮기며 조회 값 읽기 또는 매핑을 반복한다.
        while (rs.next()) values.add(mapper.mapRow(rs, row++));
        // ResultSet의 모든 행을 변환한 리스트를 반환한다.
        return values;
    }

    /** 실험 재현에 필요한 JVM·운영체제·DB·드라이버·측정 설정을 기록한다. */
    private static void environment(Connection c, PrintWriter out, int fork) throws SQLException {
        // 실행 환경·진행 상황 또는 비교 결과를 출력한다.
        out.println("timestamp_utc=" + Instant.now());
        // 실행 환경·진행 상황 또는 비교 결과를 출력한다.
        out.println("fork=" + fork);
        // 실행 환경·진행 상황 또는 비교 결과를 출력한다.
        out.println("java=" + System.getProperty("java.runtime.version"));
        // 실행 환경·진행 상황 또는 비교 결과를 출력한다.
        out.println("vm=" + System.getProperty("java.vm.name"));
        // 실행 환경·진행 상황 또는 비교 결과를 출력한다.
        out.println("vendor=" + System.getProperty("java.vendor"));
        // 실행 환경·진행 상황 또는 비교 결과를 출력한다.
        out.println("os=" + System.getProperty("os.name") + " " + System.getProperty("os.version")
                + " " + System.getProperty("os.arch"));
        // 실행 환경·진행 상황 또는 비교 결과를 출력한다.
        out.println("jvm_args=" + java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments());
        // 실행 환경·진행 상황 또는 비교 결과를 출력한다.
        out.println("processors=" + Runtime.getRuntime().availableProcessors());
        // 실행 환경·진행 상황 또는 비교 결과를 출력한다.
        out.println("db=" + c.getMetaData().getDatabaseProductVersion());
        // 실행 환경·진행 상황 또는 비교 결과를 출력한다.
        out.println("driver=" + c.getMetaData().getDriverVersion());
        // 실행 환경·진행 상황 또는 비교 결과를 출력한다.
        out.println("connection=existing localhost MySQL TCP connection; no pool; autoCommit=true");
        // 실행 환경·진행 상황 또는 비교 결과를 출력한다.
        out.println("warmups=" + WARMUPS + "; samples=" + SAMPLES + "; seed=20261030+fork*1000+columns*10+rows");
        // 실행 환경·진행 상황 또는 비교 결과를 출력한다.
        out.println("spring=6.2.12; dbutils=1.8.1; result mode=buffered, no streaming/cursor fetch");
        // SQL 결과를 열고 블록 종료 시 ResultSet을 닫는다.
        try (var stmt = c.createStatement(); var rs = stmt.executeQuery(
                "SHOW VARIABLES WHERE Variable_name IN ('version_compile_os','version_compile_machine',"
                + "'innodb_buffer_pool_size','max_connections','character_set_server','collation_server')")) {
            // 다음 행으로 커서를 옮기며 조회 값 읽기 또는 매핑을 반복한다.
            while (rs.next()) out.println(rs.getString(1) + "=" + rs.getString(2));
        }
        // 버퍼에 남은 기록을 출력 파일에 반영한다.
        out.flush();
    }

    /** 필수 환경 변수 값을 읽고 누락되었으면 실행을 중단한다. */
    private static String required(String key) {
        // 환경 변수에서 실행 설정을 읽는다.
        String value = System.getenv(key);
        // 필수 입력이나 환경 변수가 누락되었는지 확인한다.
        if (value == null) throw new IllegalArgumentException("Set " + key);
        // 검증한 환경 변수 값을 반환한다.
        return value;
    }

    /** 모든 GC 관리 빈의 누적 수집 횟수를 합산한다. 미지원 음수 값은 0으로 처리한다. */
    private static long gcCount() {
        // GC 관리 빈을 읽어 수집 횟수 또는 시간을 합산한다.
        return java.lang.management.ManagementFactory.getGarbageCollectorMXBeans().stream()
                .mapToLong(bean -> Math.max(0, bean.getCollectionCount())).sum();
    }

    /** 모든 GC 관리 빈의 누적 수집 시간을 밀리초로 합산한다. */
    private static long gcTime() {
        // GC 관리 빈을 읽어 수집 횟수 또는 시간을 합산한다.
        return java.lang.management.ManagementFactory.getGarbageCollectorMXBeans().stream()
                .mapToLong(bean -> Math.max(0, bean.getCollectionTime())).sum();
    }
}
