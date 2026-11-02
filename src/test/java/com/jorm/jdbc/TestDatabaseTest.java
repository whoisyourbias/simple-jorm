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

    /** 전달된 값을 Up 필드에 저장하는 JavaBean 설정자다. */
    @BeforeEach
    void setUp() throws Exception {
        // 테스트용 H2 DB 연결을 생성하고 테스트가 끝날 때까지 유지한다.
        connection = DriverManager.getConnection("jdbc:h2:mem:" + UUID.randomUUID(), "sa", "");
        // 초기화 SQL로 스키마 또는 테스트 데이터를 준비한다.
        executeScript("/schema.sql");
        // 초기화 SQL로 스키마 또는 테스트 데이터를 준비한다.
        executeScript("/data.sql");
    }

    /** 유지하던 연결을 닫아 테스트 데이터베이스 자원을 정리한다. */
    @AfterEach
    void tearDown() throws SQLException {
        // 정리할 연결 또는 사용할 조회 결과가 존재하는지 확인한다.
        if (connection != null) {
            // 유지 중인 테스트 연결을 닫아 자원을 반환한다.
            connection.close();
        }
    }

    /** 초기 SQL이 사용자 세 명과 모든 필드를 올바르게 저장하는지 검증한다. */
    @Test
    void loadsSampleUsers() throws SQLException {
        // 연결·Statement·파일 자원을 열고 try-with-resources로 사용 후 자동 정리한다.
        try (var statement = connection.createStatement();
             // SELECT를 실행해 커서와 컬럼 메타데이터를 제공하는 ResultSet을 얻는다.
             var resultSet = statement.executeQuery("SELECT id, name, email FROM users ORDER BY id")) {
            // 결과 또는 실행 순서를 담을 리스트를 준비한다.
            var users = new ArrayList<List<Object>>();
            // 다음 행으로 커서를 옮기며 조회 값 읽기 또는 매핑을 반복한다.
            while (resultSet.next()) {
                // 현재 조회 값 또는 행 번호를 검증용 리스트에 추가한다.
                users.add(List.of(resultSet.getLong("id"), resultSet.getString("name"),
                        resultSet.getString("email")));
            }

            // 기대값과 실제 반환값·저장 데이터·호출 횟수가 같은지 검증한다.
            assertEquals(List.of(
                    List.of(1L, "Alice", "alice@example.com"),
                    List.of(2L, "Bob", "bob@example.com"),
                    List.of(3L, "Charlie", "charlie@example.com")
            ), users);
        }
    }

    /** 직접 JDBC 삽입 후 자동 생성 키가 반환되는지 검증한다. */
    @Test
    void insertsUserWithGeneratedId() throws SQLException {
        // 물음표 바인딩용 PreparedStatement를 만들고 블록 종료 시 닫는다.
        try (var statement = connection.prepareStatement(
                "INSERT INTO users (name, email) VALUES (?, ?)", Statement.RETURN_GENERATED_KEYS)) {
            // JDBC 파라미터 위치에 현재 행 또는 조건 값을 바인딩한다.
            statement.setString(1, "David");
            // JDBC 파라미터 위치에 현재 행 또는 조건 값을 바인딩한다.
            statement.setString(2, "david@example.com");

            // 기대값과 실제 반환값·저장 데이터·호출 횟수가 같은지 검증한다.
            assertEquals(1, statement.executeUpdate());
            // 연결·Statement·파일 자원을 열고 try-with-resources로 사용 후 자동 정리한다.
            try (var keys = statement.getGeneratedKeys()) {
                // 행 존재·프록시 여부·자동 커밋 등 지정 조건이 참인지 확인한다.
                assertTrue(keys.next());
                // 기대값과 실제 반환값·저장 데이터·호출 횟수가 같은지 검증한다.
                assertEquals(4L, keys.getLong(1));
                // 추가 행 또는 동등성 비교 조건이 거짓인지 확인한다.
                assertFalse(keys.next());
            }
        }
    }

    /** 클래스패스 SQL 리소스를 UTF-8로 읽어 테스트 연결에서 실행한다. */
    private void executeScript(String resource) throws Exception {
        // 클래스패스에서 SQL 리소스를 입력 스트림으로 연다.
        var stream = getClass().getResourceAsStream(resource);
        // 조회 객체 또는 SQL 리소스가 실제로 존재하는지 확인한다.
        assertNotNull(stream, "Missing test SQL resource: " + resource);
        // SQL 리소스를 UTF-8로 읽고 사용 후 리더를 닫는다.
        try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            // 초기화 SQL로 스키마 또는 테스트 데이터를 준비한다.
            RunScript.execute(connection, reader);
        }
    }
}
