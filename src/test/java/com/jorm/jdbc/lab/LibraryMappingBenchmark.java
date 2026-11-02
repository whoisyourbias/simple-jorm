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
    /** 비교 대상 라이브러리의 조회를 실행하고 Row30 리스트를 반환하는 함수 계약이다. */
    @FunctionalInterface
    interface Query { List<ScalingRows.Row30> run() throws Exception; }
    /** 비교 그룹 이름과 해당 쿼리 실행 함수를 함께 보관한다. */
    record Group(String name, Query query) {}
    private static volatile Object sink;

    /** 같은 MySQL 연결에서 Spring·DbUtils의 수동·자동 매핑 API 시간을 비교한다. */
    public static void main(String[] args) throws Exception {
        // 필수 의존성이 null인지 검사하고 필요한 경우 필드에 보관한다.
        String fork = Objects.requireNonNull(System.getenv("JORM_BENCH_FORK"));
        // 필수 의존성이 null인지 검사하고 필요한 경우 필드에 보관한다.
        Path output = Path.of(Objects.requireNonNull(System.getenv("JORM_BENCH_OUTPUT")));
        // 결과 디렉터리가 없으면 만든다.
        Files.createDirectories(output);
        // DB 연결 속성을 담을 객체를 만든다.
        var properties = new Properties();
        // 필수 의존성이 null인지 검사하고 필요한 경우 필드에 보관한다.
        properties.setProperty("user", Objects.requireNonNull(System.getenv("JORM_DB_USER")));
        // 필수 의존성이 null인지 검사하고 필요한 경우 필드에 보관한다.
        properties.setProperty("password", Objects.requireNonNull(System.getenv("JORM_DB_PASSWORD")));
        // 연결·Statement·파일 자원을 열고 try-with-resources로 사용 후 자동 정리한다.
        try (var connection = DriverManager.getConnection(System.getenv("JORM_DB_URL"), properties);
             // 표본 CSV 또는 실행 환경을 기록할 출력 파일을 연다.
             var csv = new PrintWriter(Files.newBufferedWriter(output.resolve("library-fork-" + fork + ".csv")))) {
            // 현재 연결에서 실험용 데이터베이스를 선택한다.
            connection.setCatalog("simple_jorm_mapping_benchmark");
            // 컬럼 수에 맞는 테이블 생성 SQL을 조립한다.
            StringBuilder ddl = new StringBuilder("CREATE TEMPORARY TABLE library_rows (id BIGINT PRIMARY KEY");
            // 컬럼 번호 순서대로 스키마·값 바인딩·검증을 처리한다.
            for (int col = 2; col <= 30; col++) ddl.append(", f").append(col).append(" VARCHAR(40)");
            // 연결·Statement·파일 자원을 열고 try-with-resources로 사용 후 자동 정리한다.
            try (var stmt = connection.createStatement()) { stmt.execute(ddl.append(')').toString()); }
            // SQL마다 확정하지 않고 이후 작업을 하나의 트랜잭션으로 묶는다.
            connection.setAutoCommit(false);
            // 물음표 바인딩용 PreparedStatement를 만들고 블록 종료 시 닫는다.
            try (var stmt = connection.prepareStatement("INSERT INTO library_rows VALUES ("
                    + String.join(",", Collections.nCopies(30, "?")) + ")")) {
                // 1부터 지정 행 수까지 데이터 생성 또는 결과 검증을 반복한다.
                for (int row = 1; row <= 10000; row++) {
                    // JDBC 파라미터 위치에 현재 행 또는 조건 값을 바인딩한다.
                    stmt.setLong(1, row);
                    // 컬럼 번호 순서대로 스키마·값 바인딩·검증을 처리한다.
                    for (int col = 2; col <= 30; col++) stmt.setString(col, MappingScalingBenchmark.expected(row, col));
                    // 현재 행의 파라미터를 삽입 배치에 추가한다.
                    stmt.addBatch();
                    // 1,000행마다 모아 둔 삽입 배치를 실행한다.
                    if (row % 1000 == 0) stmt.executeBatch();
                }
                // 지금까지 실행한 변경을 확정한다.
                connection.commit();
            }
            // 배치 데이터 준비를 위한 자동 커밋 설정을 변경한다.
            connection.setAutoCommit(true);
            // 현재 처리할 조회 또는 변경 SQL을 결정한다.
            String sql = "SELECT * FROM library_rows ORDER BY id";
            // close() on Spring's proxy returns without closing the shared physical connection.
            // 지정 연결 공급자 또는 관리자로 SQL 실행기를 만든다.
            var spring = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
            // 동일 결과 타입에 사용할 Spring의 자동 필드 매퍼를 만든다.
            var springMapper = new org.springframework.jdbc.core.BeanPropertyRowMapper<>(ScalingRows.Row30.class);
            // 동일 연결에서 SQL을 실행할 DbUtils 쿼리 실행기를 만든다.
            var dbutils = new QueryRunner();
            // DbUtils 조회 결과를 Row30 객체의 리스트로 처리할 핸들러를 만든다.
            var beanList = new BeanListHandler<>(ScalingRows.Row30.class);
            // 비교 그룹 또는 기대값을 불변 리스트로 구성한다.
            var groups = List.of(
                    new Group("spring-manual", () -> spring.query(sql, ScalingRows::manual30)),
                    new Group("spring-bean", () -> spring.query(sql, springMapper)),
                    new Group("dbutils-manual", () -> dbutils.query(connection, sql,
                            rs -> MappingScalingBenchmark.readRows(rs, ScalingRows::manual30))),
                    new Group("dbutils-bean-list", () -> dbutils.query(connection, sql, beanList)));
            // 각 비교 그룹의 매퍼·쿼리를 정해진 순서로 실행한다.
            for (var group : groups) MappingScalingBenchmark.validate(group.query.run(), 10000, 30);
            // 실행 순서가 재현되도록 고정 규칙의 난수 시드를 사용한다.
            var random = new Random(20261030L + Integer.parseInt(fork));
            // CSV 측정 결과의 컬럼 이름을 기록한다.
            csv.println("fork,group,sample,repetitions,ms_per_query");
            // 음수 회차는 워밍업, 이후 회차는 본 측정으로 반복한다.
            for (int round = -2; round < 5; round++) {
                // 결과 또는 실행 순서를 담을 리스트를 준비한다.
                var order = new ArrayList<>(groups);
                // 재현 가능한 난수로 매퍼 실행 순서를 섞어 순서 편향을 줄인다.
                Collections.shuffle(order, random);
                // 각 비교 그룹의 매퍼·쿼리를 정해진 순서로 실행한다.
                for (var group : order) {
                    // 결과 참조를 보존하거나 해제해 실험 객체의 생존 조건을 설정한다.
                    sink = null;
                    // 단조 증가 시계로 시작 시각 또는 경과 나노초를 계산한다.
                    long start = System.nanoTime();
                    // 같은 조회 또는 매핑을 반복해 한 표본의 시간을 측정한다.
                    for (int repeat = 0; repeat < 2; repeat++) sink = group.query.run();
                    // 단조 증가 시계로 시작 시각 또는 경과 나노초를 계산한다.
                    double elapsed = (System.nanoTime() - start) / 1_000_000.0 / 2;
                    // 워밍업을 제외하고 본 측정 회차만 기록한다.
                    if (round >= 0) {
                        // 현재 측정의 조건과 소요 시간 등을 CSV 한 행으로 기록한다.
                        csv.printf(Locale.ROOT, "%s,%s,%d,2,%.6f%n", fork, group.name, round + 1, elapsed);
                        // 버퍼에 남은 기록을 출력 파일에 반영한다.
                        csv.flush();
                    }
                }
            }
            // 실행 환경·진행 상황 또는 비교 결과를 출력한다.
            System.out.println("Library comparison fork " + fork + " validated and measured");
        }
    }
}
