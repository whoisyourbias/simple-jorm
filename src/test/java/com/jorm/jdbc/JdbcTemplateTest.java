package com.jorm.jdbc;

import org.h2.jdbcx.JdbcDataSource;
import org.h2.tools.RunScript;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class JdbcTemplateTest {

    private Connection keepAlive;
    private JdbcTemplate jdbcTemplate;

    /** 조회 결과를 담을 객체를 생성한다. 인자가 있으면 전달받은 필드 값을 저장한다. */
    private record User(long id, String name, String email) {}

    private final RowMapper<User> userMapper = (rs, rowNum) -> new User(
            rs.getLong("id"), rs.getString("name"), rs.getString("email"));

    /** 전달된 값을 Up 필드에 저장하는 JavaBean 설정자다. */
    @BeforeEach
    void setUp() throws Exception {
        // H2 연결 공급자를 만든다.
        var dataSource = new JdbcDataSource();
        // 고유 이름의 H2 인메모리 DB URL을 지정해 테스트를 격리한다.
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID());
        // Keep the in-memory database alive between template calls.
        // H2 데이터베이스가 호출 사이에도 유지되도록 연결을 보관한다.
        keepAlive = dataSource.getConnection();
        // 스키마와 초기 데이터 SQL 파일을 순서대로 처리한다.
        for (String resource : new String[]{"/schema.sql", "/data.sql"}) {
            // 클래스패스에서 SQL 리소스를 입력 스트림으로 연다.
            var stream = getClass().getResourceAsStream(resource);
            // 조회 객체 또는 SQL 리소스가 실제로 존재하는지 확인한다.
            assertNotNull(stream, "Missing test SQL resource: " + resource);
            // SQL 리소스를 UTF-8로 읽고 사용 후 리더를 닫는다.
            try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                // 초기화 SQL로 스키마 또는 테스트 데이터를 준비한다.
                RunScript.execute(keepAlive, reader);
            }
        }
        // 지정 연결 공급자 또는 관리자로 SQL 실행기를 만든다.
        jdbcTemplate = new JdbcTemplate(dataSource);
    }

    /** 유지하던 연결을 닫아 테스트 데이터베이스 자원을 정리한다. */
    @AfterEach
    void tearDown() throws SQLException {
        // 정리할 연결 또는 사용할 조회 결과가 존재하는지 확인한다.
        if (keepAlive != null) {
            // 유지 중인 테스트 연결을 닫아 자원을 반환한다.
            keepAlive.close();
        }
    }

    /** 삽입 행 수와 실제 저장된 값을 검증한다. */
    @Test
    void updateInsertsUserAndReturnsAffectedRowCount() throws SQLException {
        // 지정 SQL과 인자로 데이터를 생성·변경·삭제한다.
        int count = jdbcTemplate.update(
                "INSERT INTO users (name, email) VALUES (?, ?)",
                "David", "david@example.com");

        // 기대값과 실제 반환값·저장 데이터·호출 횟수가 같은지 검증한다.
        assertEquals(1, count);
        // 물음표 바인딩용 PreparedStatement를 만들고 블록 종료 시 닫는다.
        try (var stmt = keepAlive.prepareStatement(
                "SELECT name FROM users WHERE email = ?")) {
            // JDBC 파라미터 위치에 현재 행 또는 조건 값을 바인딩한다.
            stmt.setString(1, "david@example.com");
            // SQL 결과를 열고 블록 종료 시 ResultSet을 닫는다.
            try (var rs = stmt.executeQuery()) {
                // 행 존재·프록시 여부·자동 커밋 등 지정 조건이 참인지 확인한다.
                assertTrue(rs.next());
                // 기대값과 실제 반환값·저장 데이터·호출 횟수가 같은지 검증한다.
                assertEquals("David", rs.getString("name"));
                // 추가 행 또는 동등성 비교 조건이 거짓인지 확인한다.
                assertFalse(rs.next());
            }
        }
    }

    /** 조건에 맞는 사용자의 이름만 변경되는지 검증한다. */
    @Test
    void updateChangesOnlyMatchingUser() throws SQLException {
        // 지정 SQL과 인자로 데이터를 생성·변경·삭제한다.
        int count = jdbcTemplate.update(
                "UPDATE users SET name = ? WHERE id = ?", "Updated", 2L);

        // 기대값과 실제 반환값·저장 데이터·호출 횟수가 같은지 검증한다.
        assertEquals(1, count);
        // 연결·Statement·파일 자원을 열고 try-with-resources로 사용 후 자동 정리한다.
        try (var stmt = keepAlive.createStatement();
             // SELECT를 실행해 커서와 컬럼 메타데이터를 제공하는 ResultSet을 얻는다.
             var rs = stmt.executeQuery("SELECT name FROM users ORDER BY id")) {
            // 결과 또는 실행 순서를 담을 리스트를 준비한다.
            var names = new ArrayList<String>();
            // 다음 행으로 커서를 옮기며 조회 값 읽기 또는 매핑을 반복한다.
            while (rs.next()) {
                // 현재 조회 값 또는 행 번호를 검증용 리스트에 추가한다.
                names.add(rs.getString("name"));
            }
            // 기대값과 실제 반환값·저장 데이터·호출 횟수가 같은지 검증한다.
            assertEquals(List.of("Alice", "Updated", "Charlie"), names);
        }
    }

    /** 대상 사용자만 삭제되고 나머지 ID가 유지되는지 검증한다. */
    @Test
    void updateDeletesMatchingUser() throws SQLException {
        // 지정 SQL과 인자로 데이터를 생성·변경·삭제한다.
        int count = jdbcTemplate.update("DELETE FROM users WHERE id = ?", 2L);

        // 기대값과 실제 반환값·저장 데이터·호출 횟수가 같은지 검증한다.
        assertEquals(1, count);
        // 연결·Statement·파일 자원을 열고 try-with-resources로 사용 후 자동 정리한다.
        try (var stmt = keepAlive.createStatement();
             // SELECT를 실행해 커서와 컬럼 메타데이터를 제공하는 ResultSet을 얻는다.
             var rs = stmt.executeQuery("SELECT id FROM users ORDER BY id")) {
            // 결과 또는 실행 순서를 담을 리스트를 준비한다.
            var ids = new ArrayList<Long>();
            // 다음 행으로 커서를 옮기며 조회 값 읽기 또는 매핑을 반복한다.
            while (rs.next()) {
                // 현재 조회 값 또는 행 번호를 검증용 리스트에 추가한다.
                ids.add(rs.getLong("id"));
            }
            // 기대값과 실제 반환값·저장 데이터·호출 횟수가 같은지 검증한다.
            assertEquals(List.of(1L, 3L), ids);
        }
    }

    /** 수정 대상이 없으면 0을 반환하고 데이터가 유지되는지 검증한다. */
    @Test
    void updateReturnsZeroWhenNoRowsMatch() throws SQLException {
        // 지정 SQL과 인자로 데이터를 생성·변경·삭제한다.
        int count = jdbcTemplate.update(
                "UPDATE users SET name = ? WHERE id = ?", "Updated", 999L);

        // 기대값과 실제 반환값·저장 데이터·호출 횟수가 같은지 검증한다.
        assertEquals(0, count);
        // 연결·Statement·파일 자원을 열고 try-with-resources로 사용 후 자동 정리한다.
        try (var stmt = keepAlive.createStatement();
             // SELECT를 실행해 커서와 컬럼 메타데이터를 제공하는 ResultSet을 얻는다.
             var rs = stmt.executeQuery("SELECT name FROM users ORDER BY id")) {
            // 결과 또는 실행 순서를 담을 리스트를 준비한다.
            var names = new ArrayList<String>();
            // 다음 행으로 커서를 옮기며 조회 값 읽기 또는 매핑을 반복한다.
            while (rs.next()) {
                // 현재 조회 값 또는 행 번호를 검증용 리스트에 추가한다.
                names.add(rs.getString("name"));
            }
            // 기대값과 실제 반환값·저장 데이터·호출 횟수가 같은지 검증한다.
            assertEquals(List.of("Alice", "Bob", "Charlie"), names);
        }
    }

    /** 여러 행 수정 후 실제 변경 행 수와 값을 검증한다. */
    @Test
    void updateReturnsCountOfAllModifiedRows() throws SQLException {
        // 지정 SQL과 인자로 데이터를 생성·변경·삭제한다.
        int count = jdbcTemplate.update(
                "UPDATE users SET name = ? WHERE id >= ?", "Updated", 2L);

        // 기대값과 실제 반환값·저장 데이터·호출 횟수가 같은지 검증한다.
        assertEquals(2, count);
        // 연결·Statement·파일 자원을 열고 try-with-resources로 사용 후 자동 정리한다.
        try (var stmt = keepAlive.createStatement();
             // SELECT를 실행해 커서와 컬럼 메타데이터를 제공하는 ResultSet을 얻는다.
             var rs = stmt.executeQuery("SELECT name FROM users ORDER BY id")) {
            // 결과 또는 실행 순서를 담을 리스트를 준비한다.
            var names = new ArrayList<String>();
            // 다음 행으로 커서를 옮기며 조회 값 읽기 또는 매핑을 반복한다.
            while (rs.next()) {
                // 현재 조회 값 또는 행 번호를 검증용 리스트에 추가한다.
                names.add(rs.getString("name"));
            }
            // 기대값과 실제 반환값·저장 데이터·호출 횟수가 같은지 검증한다.
            assertEquals(List.of("Alice", "Updated", "Updated"), names);
        }
    }

    /** 잘못된 변경 SQL의 SQLException 전파를 검증한다. */
    @Test
    void updatePropagatesInvalidSqlFailure() {
        // 호출이 지정 예외를 던지는지 검증하고 필요하면 예외 객체를 보관한다.
        assertThrows(SQLException.class,
                // 지정 SQL과 인자로 데이터를 생성·변경·삭제한다.
                () -> jdbcTemplate.update("UPDATE users SET WHERE id = ?", 1L));
    }

    /** 빈 조회에서 매퍼를 호출하지 않고 빈 리스트를 반환하는지 검증한다. */
    @Test
    void queryReturnsEmptyListWithoutCallingMapperWhenNoRowsMatch() throws SQLException {
        // 목록 조회의 매핑 결과를 보관한다.
        List<User> result = jdbcTemplate.query(
                "SELECT id, name, email FROM users WHERE id = ?",
                (rs, rowNum) -> {
                    // 실행되면 안 되는 매핑 경로에 들어왔으므로 테스트를 실패시킨다.
                    fail("An empty result must not invoke the mapper");
                    // 단건 결과가 없거나 테스트용 반환값이 없음을 null로 나타낸다.
                    return null;
                }, 999L);

        // 기대값과 실제 반환값·저장 데이터·호출 횟수가 같은지 검증한다.
        assertEquals(List.of(), result);
    }

    /** 한 행을 객체 하나를 담은 리스트로 반환하는지 검증한다. */
    @Test
    void queryReturnsSingleMappedObjectInList() throws SQLException {
        // 목록 조회의 매핑 결과를 보관한다.
        List<User> result = jdbcTemplate.query(
                "SELECT id, name, email FROM users WHERE id = ?", userMapper, 2L);

        // 기대값과 실제 반환값·저장 데이터·호출 횟수가 같은지 검증한다.
        assertEquals(List.of(new User(2L, "Bob", "bob@example.com")), result);
    }

    /** 결과 순서와 0부터 증가하는 매핑 행 번호를 검증한다. */
    @Test
    void queryMapsAllRowsInQueryOrderWithIncreasingRowNumbers() throws SQLException {
        // 결과 또는 실행 순서를 담을 리스트를 준비한다.
        List<Integer> rowNumbers = new ArrayList<>();

        // 목록 조회의 매핑 결과를 보관한다.
        List<User> result = jdbcTemplate.query(
                "SELECT id, name, email FROM users ORDER BY id DESC",
                (rs, rowNum) -> {
                    // 현재 조회 값 또는 행 번호를 검증용 리스트에 추가한다.
                    rowNumbers.add(rowNum);
                    // 현재 행을 지정 매퍼로 변환해 반환한다.
                    return userMapper.mapRow(rs, rowNum);
                });

        // 기대값과 실제 반환값·저장 데이터·호출 횟수가 같은지 검증한다.
        assertEquals(List.of(
                new User(3L, "Charlie", "charlie@example.com"),
                new User(2L, "Bob", "bob@example.com"),
                new User(1L, "Alice", "alice@example.com")
        ), result);
        // 기대값과 실제 반환값·저장 데이터·호출 횟수가 같은지 검증한다.
        assertEquals(List.of(0, 1, 2), rowNumbers);
    }

    /** 목록 조회 인자가 물음표 순서대로 적용되는지 검증한다. */
    @Test
    void queryBindsMultipleParametersInOrder() throws SQLException {
        // 목록 조회의 매핑 결과를 보관한다.
        List<User> result = jdbcTemplate.query(
                "SELECT id, name, email FROM users WHERE id >= ? AND name <> ? ORDER BY id",
                userMapper, 2L, "Charlie");

        // 기대값과 실제 반환값·저장 데이터·호출 횟수가 같은지 검증한다.
        assertEquals(List.of(new User(2L, "Bob", "bob@example.com")), result);
    }

    /** 목록 조회 SQL 오류의 전파를 검증한다. */
    @Test
    void queryPropagatesSqlExecutionFailure() {
        // 호출이 지정 예외를 던지는지 검증하고 필요하면 예외 객체를 보관한다.
        assertThrows(SQLException.class, () -> jdbcTemplate.query(
                "SELECT id FROM missing_table", userMapper));
    }

    /** 두 번째 행 매핑 실패가 원래 예외로 전달되고 처리가 중단되는지 검증한다. */
    @Test
    void queryPropagatesMapperFailureOnLaterRow() {
        // 원래 예외 객체가 전달되는지 검증할 실패 객체를 미리 만든다.
        var failure = new SQLException("Mapping failed on second row");
        // 결과 또는 실행 순서를 담을 리스트를 준비한다.
        List<Integer> rowNumbers = new ArrayList<>();

        // 호출이 지정 예외를 던지는지 검증하고 필요하면 예외 객체를 보관한다.
        SQLException actual = assertThrows(SQLException.class,
                () -> jdbcTemplate.query(
                        "SELECT id, name, email FROM users ORDER BY id",
                        (rs, rowNum) -> {
                            // 현재 조회 값 또는 행 번호를 검증용 리스트에 추가한다.
                            rowNumbers.add(rowNum);
                            // 두 번째 행에서 의도적으로 매핑 실패를 발생시킨다.
                            if (rowNum == 1) {
                                // 원래 실패를 호출자에게 전달한다. 트랜잭션 안이면 롤백 처리로 이어진다.
                                throw failure;
                            }
                            // 현재 행을 지정 매퍼로 변환해 반환한다.
                            return userMapper.mapRow(rs, rowNum);
                        }));

        // 원래 예외 객체가 포장되거나 교체되지 않고 그대로 전달되는지 확인한다.
        assertSame(failure, actual);
        // 기대값과 실제 반환값·저장 데이터·호출 횟수가 같은지 검증한다.
        assertEquals(List.of(0, 1), rowNumbers);
    }

    /** 단건 조회의 매핑 결과를 검증한다. */
    @Test
    void returnsMappedObjectForOneRow() throws SQLException {
        // 단건 조회 결과를 변환해 보관한다.
        User result = jdbcTemplate.queryForObject(
                "SELECT id, name, email FROM users WHERE id = ?", userMapper, 2L);

        // 기대값과 실제 반환값·저장 데이터·호출 횟수가 같은지 검증한다.
        assertEquals(new User(2L, "Bob", "bob@example.com"), result);
    }

    /** 빈 단건 조회가 매퍼 호출 없이 null을 반환하는지 검증한다. */
    @Test
    void returnsNullWithoutCallingMapperWhenNoRowsMatch() throws SQLException {
        // 단건 조회 결과를 변환해 보관한다.
        User result = jdbcTemplate.queryForObject(
                "SELECT id, name, email FROM users WHERE id = ?",
                (rs, rowNum) -> {
                    // 실행되면 안 되는 매핑 경로에 들어왔으므로 테스트를 실패시킨다.
                    fail("An empty result must not invoke the mapper");
                    // 단건 결과가 없거나 테스트용 반환값이 없음을 null로 나타낸다.
                    return null;
                }, 999L);

        // 조회 결과 또는 조회하지 않은 필드가 null인지 확인한다.
        assertNull(result);
    }

    /** 단건 조회가 첫 행만 행 번호 0으로 매핑하는지 검증한다. */
    @Test
    void mapsOnlyFirstRowWithRowNumberZeroWhenMultipleRowsMatch() throws SQLException {
        // 호출 횟수를 기록할 카운터를 만든다.
        var calls = new AtomicInteger();

        // 단건 조회 결과를 변환해 보관한다.
        User result = jdbcTemplate.queryForObject(
                "SELECT id, name, email FROM users ORDER BY id DESC",
                (rs, rowNum) -> {
                    // 호출 횟수를 증가시켜 준비·매핑 횟수를 검증한다.
                    calls.incrementAndGet();
                    // 기대값과 실제 반환값·저장 데이터·호출 횟수가 같은지 검증한다.
                    assertEquals(0, rowNum);
                    // 현재 행을 지정 매퍼로 변환해 반환한다.
                    return userMapper.mapRow(rs, rowNum);
                });

        // 기대값과 실제 반환값·저장 데이터·호출 횟수가 같은지 검증한다.
        assertEquals(new User(3L, "Charlie", "charlie@example.com"), result);
        // 기대값과 실제 반환값·저장 데이터·호출 횟수가 같은지 검증한다.
        assertEquals(1, calls.get());
    }

    /** 단건 조회의 여러 파라미터 바인딩 순서를 검증한다. */
    @Test
    void bindsMultipleParametersInOrder() throws SQLException {
        // 단건 조회 결과를 변환해 보관한다.
        User result = jdbcTemplate.queryForObject(
                "SELECT id, name, email FROM users WHERE id = ? AND name = ?",
                userMapper, 2L, "Bob");

        // 기대값과 실제 반환값·저장 데이터·호출 횟수가 같은지 검증한다.
        assertEquals(new User(2L, "Bob", "bob@example.com"), result);
    }

    /** 단건 조회 SQL 오류의 전파를 검증한다. */
    @Test
    void propagatesSqlExecutionFailure() {
        // 호출이 지정 예외를 던지는지 검증하고 필요하면 예외 객체를 보관한다.
        assertThrows(SQLException.class, () -> jdbcTemplate.queryForObject(
                "SELECT id FROM missing_table", userMapper));
    }

    /** 행 매퍼가 던진 예외 객체를 그대로 전달하는지 검증한다. */
    @Test
    void propagatesMapperFailure() {
        // 원래 예외 객체가 전달되는지 검증할 실패 객체를 미리 만든다.
        var failure = new SQLException("Mapping failed");

        // 호출이 지정 예외를 던지는지 검증하고 필요하면 예외 객체를 보관한다.
        SQLException actual = assertThrows(SQLException.class,
                () -> jdbcTemplate.queryForObject(
                        "SELECT id FROM users WHERE id = ?",
                        (rs, rowNum) -> { throw failure; }, 1L));

        // 원래 예외 객체가 포장되거나 교체되지 않고 그대로 전달되는지 확인한다.
        assertSame(failure, actual);
    }
}
