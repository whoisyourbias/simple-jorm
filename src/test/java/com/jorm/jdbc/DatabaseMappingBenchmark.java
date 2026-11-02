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
        /** 조회 결과를 담을 객체를 생성한다. 인자가 있으면 전달받은 필드 값을 저장한다. */
        User() {}
        /** 조회 결과를 담을 객체를 생성한다. 인자가 있으면 전달받은 필드 값을 저장한다. */
        User(Long id, String name, String email) {
            // 전달받은 값을 인스턴스 필드에 저장한다.
            this.id = id;
            // 전달받은 값을 인스턴스 필드에 저장한다.
            this.name = name;
            // 전달받은 값을 인스턴스 필드에 저장한다.
            this.email = email;
        }
    }

    /** MySQL 임시 테이블에서 SQL 실행·조회·매핑 시간을 비교한다. */
    public static void main(String[] args) throws Exception {
        // 환경 변수에서 실행 설정을 읽는다.
        String url = System.getenv("JORM_DB_URL");
        // 필수 입력이나 환경 변수가 누락되었는지 확인한다.
        if (url == null) {
            // 잘못된 선언·입력 또는 실행 상태를 예외로 알린다.
            throw new IllegalArgumentException("Set JORM_DB_URL to a MySQL server URL without a database");
        }
        // DB 연결 속성을 담을 객체를 만든다.
        Properties credentials = new Properties();
        // DB 접속 사용자·비밀번호를 연결 속성에 넣는다. 화면에는 출력하지 않는다.
        credentials.setProperty("user", required("JORM_DB_USER"));
        // DB 접속 사용자·비밀번호를 연결 속성에 넣는다. 화면에는 출력하지 않는다.
        credentials.setProperty("password", required("JORM_DB_PASSWORD"));
        // 연결·Statement·파일 자원을 열고 try-with-resources로 사용 후 자동 정리한다.
        try (Connection connection = DriverManager.getConnection(url, credentials)) {
            // 연결·Statement·파일 자원을 열고 try-with-resources로 사용 후 자동 정리한다.
            try (var stmt = connection.createStatement()) {
                // 준비한 DDL·변경 SQL 또는 업무 콜백을 실행한다.
                stmt.execute("CREATE DATABASE IF NOT EXISTS " + DATABASE);
            }
            // 현재 연결에서 실험용 데이터베이스를 선택한다.
            connection.setCatalog(DATABASE);
            // Session-scoped table disappears on disconnect; existing tables are untouched.
            // 연결·Statement·파일 자원을 열고 try-with-resources로 사용 후 자동 정리한다.
            try (var stmt = connection.createStatement()) {
                // 준비한 DDL·변경 SQL 또는 업무 콜백을 실행한다.
                stmt.execute("CREATE TEMPORARY TABLE mapping_benchmark_users "
                        + "(id BIGINT PRIMARY KEY, name VARCHAR(100), email VARCHAR(150))");
            }
            // SQL마다 확정하지 않고 이후 작업을 하나의 트랜잭션으로 묶는다.
            connection.setAutoCommit(false);
            // 물음표 바인딩용 PreparedStatement를 만들고 블록 종료 시 닫는다.
            try (var insert = connection.prepareStatement(
                    "INSERT INTO mapping_benchmark_users VALUES (?, ?, ?)")) {
                // 1부터 지정 행 수까지 데이터 생성 또는 결과 검증을 반복한다.
                for (long i = 1; i <= ROWS; i++) {
                    // JDBC 파라미터 위치에 현재 행 또는 조건 값을 바인딩한다.
                    insert.setLong(1, i);
                    // JDBC 파라미터 위치에 현재 행 또는 조건 값을 바인딩한다.
                    insert.setString(2, "User" + i);
                    // JDBC 파라미터 위치에 현재 행 또는 조건 값을 바인딩한다.
                    insert.setString(3, "user" + i + "@example.com");
                    // 현재 행의 파라미터를 삽입 배치에 추가한다.
                    insert.addBatch();
                    // 1,000행마다 모아 둔 삽입 배치를 실행한다.
                    if (i % 1000 == 0) insert.executeBatch();
                }
            }
            // 지금까지 실행한 변경을 확정한다.
            connection.commit();
            // 배치 데이터 준비를 위한 자동 커밋 설정을 변경한다.
            connection.setAutoCommit(true);
            // 컬럼을 직접 읽어 객체를 만드는 수동 매퍼를 준비한다.
            RowMapper<User> manual = (rs, row) -> new User(
                    rs.getObject("id", Long.class), rs.getString("name"), rs.getString("email"));
            // 대상 클래스의 필드에 값을 저장할 자동 매퍼를 만든다.
            RowMapper<User> reflection = new BeanPropertyRowMapper<>(User.class);
            // 본 측정 전에 두 매퍼를 다섯 번 실행해 JVM을 워밍업한다.
            for (int i = 0; i < 5; i++) {
                // 지정 매퍼의 시간을 측정한다. 워밍업 호출의 결과는 기록하지 않는다.
                measure(connection, manual);
                // 지정 매퍼의 시간을 측정한다. 워밍업 호출의 결과는 기록하지 않는다.
                measure(connection, reflection);
            }
            // 측정 회차별 시간을 저장할 배열을 만든다.
            double[] direct = new double[9];
            // 측정 회차별 시간을 저장할 배열을 만든다.
            double[] automatic = new double[9];
            // 중앙값을 계산할 아홉 개의 본 측정 표본을 수집한다.
            for (int i = 0; i < 9; i++) {
                // 회차에 따라 실행 순서를 교대해 순서 편향을 줄인다.
                if (i % 2 == 0) {
                    // 지정 매퍼의 시간을 측정한다. 워밍업 호출의 결과는 기록하지 않는다.
                    direct[i] = measure(connection, manual);
                    // 지정 매퍼의 시간을 측정한다. 워밍업 호출의 결과는 기록하지 않는다.
                    automatic[i] = measure(connection, reflection);
                } else {
                    // 지정 매퍼의 시간을 측정한다. 워밍업 호출의 결과는 기록하지 않는다.
                    automatic[i] = measure(connection, reflection);
                    // 지정 매퍼의 시간을 측정한다. 워밍업 호출의 결과는 기록하지 않는다.
                    direct[i] = measure(connection, manual);
                }
            }
            // 측정값을 정렬해 중앙값과 최솟값·최댓값을 읽을 수 있게 한다.
            Arrays.sort(direct);
            // 측정값을 정렬해 중앙값과 최솟값·최댓값을 읽을 수 있게 한다.
            Arrays.sort(automatic);
            // 실행 환경·진행 상황 또는 비교 결과를 출력한다.
            System.out.println("Server: " + connection.getMetaData().getDatabaseProductVersion());
            // 실행 환경·진행 상황 또는 비교 결과를 출력한다.
            System.out.println("Java: " + System.getProperty("java.version"));
            // 실행 환경·진행 상황 또는 비교 결과를 출력한다.
            System.out.println("10,000 rows/query, 20 queries/sample, 5 warmups, 9 samples");
            // 실행 환경·진행 상황 또는 비교 결과를 출력한다.
            System.out.printf(Locale.ROOT, "manual median [min,max]: %.3f [%.3f,%.3f] ms/query%n",
                    direct[4], direct[0], direct[8]);
            // 실행 환경·진행 상황 또는 비교 결과를 출력한다.
            System.out.printf(Locale.ROOT, "reflection median [min,max]: %.3f [%.3f,%.3f] ms/query%n",
                    automatic[4], automatic[0], automatic[8]);
            // 실행 환경·진행 상황 또는 비교 결과를 출력한다.
            System.out.printf(Locale.ROOT, "reflection/manual: %.2fx%n", automatic[4] / direct[4]);
        }
    }

    /** 반복 조회 또는 버퍼 매핑 시간을 측정하고 결과를 보존한다. 경과 시간을 쿼리 수 또는 행 수로 나눠 반환한다. */
    private static double measure(Connection connection, RowMapper<User> mapper) throws Exception {
        // 단조 증가 시계로 시작 시각 또는 경과 나노초를 계산한다.
        long start = System.nanoTime();
        // 같은 조회 또는 매핑을 반복해 한 표본의 시간을 측정한다.
        for (int query = 0; query < QUERIES; query++) {
            // 결과 객체를 보존해 매핑 작업이 최적화로 제거되지 않도록 한다.
            User[] users = new User[ROWS];
            // 물음표 바인딩용 PreparedStatement를 만들고 블록 종료 시 닫는다.
            try (var stmt = connection.prepareStatement(
                    "SELECT id, name, email FROM mapping_benchmark_users ORDER BY id");
                 // SELECT를 실행해 커서와 컬럼 메타데이터를 제공하는 ResultSet을 얻는다.
                 var rs = stmt.executeQuery()) {
                // 행 번호 또는 처리 행 수를 기록할 카운터를 0으로 초기화한다.
                int row = 0;
                // 다음 행으로 커서를 옮기며 조회 값 읽기 또는 매핑을 반복한다.
                while (rs.next()) {
                    users[row] = mapper.mapRow(rs, row);
                    // 처리한 행 수를 증가시킨다.
                    row++;
                }
                // 전체 행 수와 첫·마지막 객체의 값이 올바른지 확인한다.
                if (row != ROWS || users[0].id != 1L || users[ROWS - 1].id != ROWS
                        || !"User1".equals(users[0].name)
                        || !"user10000@example.com".equals(users[ROWS - 1].email)) {
                    // 실험 데이터가 기대값과 달라 측정을 중단한다.
                    throw new AssertionError("Unexpected mapped data");
                }
            }
            // 결과 참조를 보존하거나 해제해 실험 객체의 생존 조건을 설정한다.
            sink = users;
        }
        // 총 시간을 쿼리 수 또는 행 수로 나눠 비교 가능한 단위로 반환한다.
        return (System.nanoTime() - start) / 1_000_000.0 / QUERIES;
    }

    /** 필수 환경 변수 값을 읽고 누락되었으면 실행을 중단한다. */
    private static String required(String name) {
        // 환경 변수에서 실행 설정을 읽는다.
        String value = System.getenv(name);
        // 필수 입력이나 환경 변수가 누락되었는지 확인한다.
        if (value == null) throw new IllegalArgumentException("Set " + name);
        // 검증한 환경 변수 값을 반환한다.
        return value;
    }
}
