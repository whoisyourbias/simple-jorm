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

    /** 버퍼링된 H2 결과를 반복 변환해 수동·자동 매핑 시간을 비교한다. */
    public static void main(String[] args) throws Exception {
        // 컬럼을 직접 읽어 객체를 만드는 수동 매퍼를 준비한다.
        RowMapper<User> manual = (rs, row) -> new User(
                rs.getObject("id", Long.class), rs.getString("name"), rs.getString("email"));
        // 대상 클래스의 필드에 값을 저장할 자동 매퍼를 만든다.
        RowMapper<User> reflective = new BeanPropertyRowMapper<>(User.class);
        // 연결·Statement·파일 자원을 열고 try-with-resources로 사용 후 자동 정리한다.
        try (var connection = DriverManager.getConnection("jdbc:h2:mem:benchmark");
             // 같은 연결에서 별도 조회를 실행할 Statement를 만든다.
             var statement = connection.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE,
                     ResultSet.CONCUR_READ_ONLY);
             // SELECT를 실행해 커서와 컬럼 메타데이터를 제공하는 ResultSet을 얻는다.
             var rs = statement.executeQuery("SELECT X AS id, 'User' || X AS name, "
                     + "'user' || X || '@example.com' AS email FROM SYSTEM_RANGE(1, " + ROWS + ")")) {
            // Materialize the result before timing either mapper.
            // 측정 전에 마지막 행까지 접근해 결과 데이터를 준비한다.
            rs.last();
            // 본 측정 전에 두 매퍼를 다섯 번 실행해 JVM을 워밍업한다.
            for (int i = 0; i < 5; i++) {
                // 지정 매퍼의 시간을 측정한다. 워밍업 호출의 결과는 기록하지 않는다.
                measure(rs, manual);
                // 지정 매퍼의 시간을 측정한다. 워밍업 호출의 결과는 기록하지 않는다.
                measure(rs, reflective);
            }
            // 측정 회차별 시간을 저장할 배열을 만든다.
            double[] direct = new double[9];
            // 측정 회차별 시간을 저장할 배열을 만든다.
            double[] reflection = new double[9];
            // 중앙값을 계산할 아홉 개의 본 측정 표본을 수집한다.
            for (int i = 0; i < direct.length; i++) {
                // Alternate order to reduce bias from warmup and GC.
                // 회차에 따라 실행 순서를 교대해 순서 편향을 줄인다.
                if (i % 2 == 0) {
                    // 지정 매퍼의 시간을 측정한다. 워밍업 호출의 결과는 기록하지 않는다.
                    direct[i] = measure(rs, manual);
                    // 지정 매퍼의 시간을 측정한다. 워밍업 호출의 결과는 기록하지 않는다.
                    reflection[i] = measure(rs, reflective);
                } else {
                    // 지정 매퍼의 시간을 측정한다. 워밍업 호출의 결과는 기록하지 않는다.
                    reflection[i] = measure(rs, reflective);
                    // 지정 매퍼의 시간을 측정한다. 워밍업 호출의 결과는 기록하지 않는다.
                    direct[i] = measure(rs, manual);
                }
            }
            // 측정값을 정렬해 중앙값과 최솟값·최댓값을 읽을 수 있게 한다.
            Arrays.sort(direct);
            // 측정값을 정렬해 중앙값과 최솟값·최댓값을 읽을 수 있게 한다.
            Arrays.sort(reflection);
            // 실행 환경·진행 상황 또는 비교 결과를 출력한다.
            System.out.println("Java: " + System.getProperty("java.version"));
            // 실행 환경·진행 상황 또는 비교 결과를 출력한다.
            System.out.printf(Locale.ROOT,
                    "%,d rows/sample, 5 warmups, 9 samples; median [min,max] ns/row%n",
                    ROWS * PASSES);
            // 실행 환경·진행 상황 또는 비교 결과를 출력한다.
            System.out.printf(Locale.ROOT, "manual: %.1f [%.1f, %.1f]%n", direct[4], direct[0], direct[8]);
            // 실행 환경·진행 상황 또는 비교 결과를 출력한다.
            System.out.printf(Locale.ROOT, "reflection: %.1f [%.1f, %.1f]%n",
                    reflection[4], reflection[0], reflection[8]);
            // 실행 환경·진행 상황 또는 비교 결과를 출력한다.
            System.out.printf(Locale.ROOT, "reflection/manual: %.2fx%n", reflection[4] / direct[4]);
        }
    }

    /** 반복 조회 또는 버퍼 매핑 시간을 측정하고 결과를 보존한다. 경과 시간을 쿼리 수 또는 행 수로 나눠 반환한다. */
    private static double measure(ResultSet rs, RowMapper<User> mapper) throws Exception {
        // Retain all objects until the batch ends so allocations cannot be eliminated.
        // 결과 객체를 보존해 매핑 작업이 최적화로 제거되지 않도록 한다.
        User[] batch = new User[ROWS * PASSES];
        // index에 이후 처리에서 사용할 값 또는 객체를 준비한다.
        int index = 0;
        // 단조 증가 시계로 시작 시각 또는 경과 나노초를 계산한다.
        long start = System.nanoTime();
        // 같은 조회 또는 매핑을 반복해 한 표본의 시간을 측정한다.
        for (int pass = 0; pass < PASSES; pass++) {
            // 결과를 다시 순회하도록 커서를 첫 행 이전으로 되돌린다.
            rs.beforeFirst();
            // 행 매핑에 전달할 번호를 0으로 초기화한다.
            int rowNum = 0;
            // 다음 행으로 커서를 옮기며 조회 값 읽기 또는 매핑을 반복한다.
            while (rs.next()) {
                batch[index++] = mapper.mapRow(rs, rowNum++);
            }
        }
        // 단조 증가 시계로 시작 시각 또는 경과 나노초를 계산한다.
        long elapsed = System.nanoTime() - start;
        // 입력 또는 실행 결과가 다음 처리 조건에 해당하는지 검사한다.
        if (index != batch.length || batch[0].id != 1L
                || batch[index - 1].id != ROWS) {
            // 실험 데이터가 기대값과 달라 측정을 중단한다.
            throw new AssertionError("Unexpected mapped rows");
        }
        // 결과 참조를 보존하거나 해제해 실험 객체의 생존 조건을 설정한다.
        sink = batch;
        // 총 시간을 쿼리 수 또는 행 수로 나눠 비교 가능한 단위로 반환한다.
        return (double) elapsed / index;
    }
}
