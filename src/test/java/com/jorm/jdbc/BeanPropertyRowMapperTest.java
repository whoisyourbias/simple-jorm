package com.jorm.jdbc;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class BeanPropertyRowMapperTest {
    static class User {
        private Long id;
        private String name;
        /** 조회 결과를 담을 객체를 생성한다. 인자가 있으면 전달받은 필드 값을 저장한다. */
        User() {}
        /** 조회 결과를 담을 객체를 생성한다. 인자가 있으면 전달받은 필드 값을 저장한다. */
        User(Long id, String name) { this.id = id; this.name = name; }
    }

    /** 수동·자동 매핑의 값, null, 빈 결과와 부분 조회 결과를 검증한다. */
    @Test
    void manualAndReflectionMappersReturnSameValues() throws Exception {
        // H2 연결 공급자를 만든다.
        var ds = new JdbcDataSource();
        // 고유 이름의 H2 인메모리 DB URL을 지정해 테스트를 격리한다.
        ds.setURL("jdbc:h2:mem:" + UUID.randomUUID());
        // 연결·Statement·파일 자원을 열고 try-with-resources로 사용 후 자동 정리한다.
        try (var connection = ds.getConnection(); var stmt = connection.createStatement()) {
            // 준비한 DDL·변경 SQL 또는 업무 콜백을 실행한다.
            stmt.execute("CREATE TABLE users(id BIGINT, name VARCHAR(100))");
            // 준비한 DDL·변경 SQL 또는 업무 콜백을 실행한다.
            stmt.execute("INSERT INTO users VALUES (1, 'Alice'), (2, 'Bob'), (3, NULL)");
            // 지정 연결 공급자 또는 관리자로 SQL 실행기를 만든다.
            var template = new JdbcTemplate(ds);
            
            
            // 컬럼을 직접 읽어 객체를 만드는 수동 매퍼를 준비한다.
            RowMapper<User> manual = (rs, row) -> new User(
                    rs.getObject("id", Long.class), 
                    rs.getString("name")
            );
            // 목록 조회의 매핑 결과를 보관한다.
            var expected = template.query("SELECT id, name FROM users ORDER BY id", manual);
            
            // 대상 클래스의 필드에 값을 저장할 자동 매퍼를 만든다.
            RowMapper<User> automatic = new BeanPropertyRowMapper<>(User.class);
            // 목록 조회의 매핑 결과를 보관한다.
            var actual = template.query("SELECT id, name FROM users ORDER BY id", automatic);
            
            
            // 기대값과 실제 반환값·저장 데이터·호출 횟수가 같은지 검증한다.
            assertEquals(3, actual.size());
            // 수동·자동 매핑 결과를 같은 행 위치끼리 비교한다.
            for (int i = 0; i < expected.size(); i++) {
                // 기대값과 실제 반환값·저장 데이터·호출 횟수가 같은지 검증한다.
                assertEquals(expected.get(i).id, actual.get(i).id);
                // 기대값과 실제 반환값·저장 데이터·호출 횟수가 같은지 검증한다.
                assertEquals(expected.get(i).name, actual.get(i).name);
            }
            // 조회 결과 또는 조회하지 않은 필드가 null인지 확인한다.
            assertNull(actual.get(2).name);
            // 기대값과 실제 반환값·저장 데이터·호출 횟수가 같은지 검증한다.
            assertEquals(List.of(), template.query("SELECT * FROM users WHERE id = 99", automatic));
            // 단건 조회 결과를 변환해 보관한다.
            User partial = template.queryForObject("SELECT name AS name FROM users WHERE id = 1", automatic);
            // 기대값과 실제 반환값·저장 데이터·호출 횟수가 같은지 검증한다.
            assertEquals("Alice", partial.name);
            // 조회 결과 또는 조회하지 않은 필드가 null인지 확인한다.
            assertNull(partial.id);
        }
    }

    /** 필드가 없는 컬럼과 타입 불일치 값을 거부하는지 검증한다. */
    @Test
    void rejectsUnsupportedColumnsAndTypes() throws Exception {
        // H2 연결 공급자를 만든다.
        var ds = new JdbcDataSource();
        // 고유 이름의 H2 인메모리 DB URL을 지정해 테스트를 격리한다.
        ds.setURL("jdbc:h2:mem:" + UUID.randomUUID());
        // 지정 연결 공급자 또는 관리자로 SQL 실행기를 만든다.
        var template = new JdbcTemplate(ds);
        // 대상 클래스의 필드에 값을 저장할 자동 매퍼를 만든다.
        var mapper = new BeanPropertyRowMapper<>(User.class);
        // 호출이 지정 예외를 던지는지 검증하고 필요하면 예외 객체를 보관한다.
        assertThrows(SQLException.class, () -> template.query("SELECT 1 AS unknown", mapper));
        // 호출이 지정 예외를 던지는지 검증하고 필요하면 예외 객체를 보관한다.
        assertThrows(SQLException.class, () -> template.query("SELECT 'wrong' AS id", mapper));
    }

    /** 준비된 매퍼가 메타데이터를 조회당 한 번만 읽는지 검증한다. */
    @Test
    void resolvesColumnMappingOnlyOncePerResultSet() throws Exception {
        // H2 연결 공급자를 만든다.
        var ds = new JdbcDataSource();
        // 고유 이름의 H2 인메모리 DB URL을 지정해 테스트를 격리한다.
        ds.setURL("jdbc:h2:mem:" + UUID.randomUUID());
        // 대상 클래스의 필드에 값을 저장할 자동 매퍼를 만든다.
        var mapper = new BeanPropertyRowMapper<>(User.class);
        // 호출 횟수를 기록할 카운터를 만든다.
        var metadataReads = new AtomicInteger();
        // 연결·Statement·파일 자원을 열고 try-with-resources로 사용 후 자동 정리한다.
        try (var connection = ds.getConnection(); var statement = connection.createStatement();
             // SELECT를 실행해 커서와 컬럼 메타데이터를 제공하는 ResultSet을 얻는다.
             var result = statement.executeQuery(
                     "SELECT CAST(1 AS BIGINT) AS id, 'Alice' AS name UNION ALL SELECT 2, 'Bob'")) {
            // 실제 ResultSet에 위임하면서 메타데이터 조회 횟수를 기록하는 관찰용 프록시를 만든다.
            ResultSet observed = (ResultSet) Proxy.newProxyInstance(
                    ResultSet.class.getClassLoader(), new Class<?>[]{ResultSet.class},
                    (proxy, method, args) -> {
                        // 호출 메서드 이름으로 동일성 처리와 메타데이터 횟수 기록을 구분한다.
                        if (method.getName().equals("equals")) return proxy == args[0];
                        // 호출 메서드 이름으로 동일성 처리와 메타데이터 횟수 기록을 구분한다.
                        if (method.getName().equals("hashCode")) return System.identityHashCode(proxy);
                        // 호출 메서드 이름으로 동일성 처리와 메타데이터 횟수 기록을 구분한다.
                        if (method.getName().equals("getMetaData")) metadataReads.incrementAndGet();
                        try {
                            // 관찰용 프록시가 실제 ResultSet의 메서드를 호출하고 결과를 돌려준다.
                            return method.invoke(result, args);
                            // 리플렉션이 실제 메서드 예외를 감쌌을 때 원인을 꺼낸다.
                        } catch (InvocationTargetException e) {
                            // 리플렉션의 포장 예외에서 실제 Service·매퍼 실패를 꺼내 던진다.
                            throw e.getCause();
                        }
                    });
            // 행 존재·프록시 여부·자동 커밋 등 지정 조건이 참인지 확인한다.
            assertTrue(observed.next());
            // 현재 ResultSet 전용으로 준비한 매퍼를 보관한다.
            RowMapper<User> prepared = mapper.prepare(observed);
            // 현재 행을 변환한 객체를 보관한다.
            User first = prepared.mapRow(observed, 0);
            // 기대값과 실제 반환값·저장 데이터·호출 횟수가 같은지 검증한다.
            assertEquals(1L, first.id);
            // 기대값과 실제 반환값·저장 데이터·호출 횟수가 같은지 검증한다.
            assertEquals("Alice", first.name);
            // 행 존재·프록시 여부·자동 커밋 등 지정 조건이 참인지 확인한다.
            assertTrue(observed.next());
            // 현재 행을 변환한 객체를 보관한다.
            User second = prepared.mapRow(observed, 1);
            // 기대값과 실제 반환값·저장 데이터·호출 횟수가 같은지 검증한다.
            assertEquals(2L, second.id);
            // 기대값과 실제 반환값·저장 데이터·호출 횟수가 같은지 검증한다.
            assertEquals("Bob", second.name);
            // 기대값과 실제 반환값·저장 데이터·호출 횟수가 같은지 검증한다.
            assertEquals(1, metadataReads.get());
        }
    }

    /** 매퍼를 재사용해도 컬럼 순서와 부분 조회가 독립적인지 검증한다. */
    @Test
    void reusesMapperAcrossDifferentColumnOrdersAndPartialQueries() throws Exception {
        // H2 연결 공급자를 만든다.
        var ds = new JdbcDataSource();
        // 고유 이름의 H2 인메모리 DB URL을 지정해 테스트를 격리한다.
        ds.setURL("jdbc:h2:mem:" + UUID.randomUUID());
        // 지정 연결 공급자 또는 관리자로 SQL 실행기를 만든다.
        var template = new JdbcTemplate(ds);
        // 대상 클래스의 필드에 값을 저장할 자동 매퍼를 만든다.
        var mapper = new BeanPropertyRowMapper<>(User.class);
        // 단건 조회 결과를 변환해 보관한다.
        User first = template.queryForObject("SELECT CAST(1 AS BIGINT) AS id, 'Alice' AS name", mapper);
        // 단건 조회 결과를 변환해 보관한다.
        User reordered = template.queryForObject("SELECT 'Bob' AS name, CAST(2 AS BIGINT) AS id", mapper);
        // 단건 조회 결과를 변환해 보관한다.
        User partial = template.queryForObject("SELECT 'Carol' AS \"NaMe\"", mapper);
        // 기대값과 실제 반환값·저장 데이터·호출 횟수가 같은지 검증한다.
        assertEquals(1L, first.id);
        // 기대값과 실제 반환값·저장 데이터·호출 횟수가 같은지 검증한다.
        assertEquals("Alice", first.name);
        // 기대값과 실제 반환값·저장 데이터·호출 횟수가 같은지 검증한다.
        assertEquals(2L, reordered.id);
        // 기대값과 실제 반환값·저장 데이터·호출 횟수가 같은지 검증한다.
        assertEquals("Bob", reordered.name);
        // 조회 결과 또는 조회하지 않은 필드가 null인지 확인한다.
        assertNull(partial.id);
        // 기대값과 실제 반환값·저장 데이터·호출 횟수가 같은지 검증한다.
        assertEquals("Carol", partial.name);
        // 호출이 지정 예외를 던지는지 검증하고 필요하면 예외 객체를 보관한다.
        assertThrows(SQLException.class, () -> template.query("SELECT 1 AS unknown", mapper));
    }

    /** 비어 있지 않은 조회마다 준비된 매퍼를 한 번 만들고 사용하는지 검증한다. */
    @Test
    void templateUsesPreparedMapperOnceForEachNonEmptyQuery() throws Exception {
        // H2 연결 공급자를 만든다.
        var ds = new JdbcDataSource();
        // 고유 이름의 H2 인메모리 DB URL을 지정해 테스트를 격리한다.
        ds.setURL("jdbc:h2:mem:" + UUID.randomUUID());
        // 지정 연결 공급자 또는 관리자로 SQL 실행기를 만든다.
        var template = new JdbcTemplate(ds);
        // 호출 횟수를 기록할 카운터를 만든다.
        var preparations = new AtomicInteger();
        // 현재 행의 모든 컬럼 값을 담을 지정 크기의 JavaBean 객체를 만든다.
        RowMapper<String> mapper = new RowMapper<>() {
            /** 현재 조회 전용 매퍼를 준비한다. 기본 구현은 자신을 반환하고 자동 매퍼는 컬럼별 필드를 준비한다. */
            @Override
            public RowMapper<String> prepare(ResultSet rs) {
                // 호출 횟수를 증가시켜 준비·매핑 횟수를 검증한다.
                preparations.incrementAndGet();
                // 첫 컬럼을 문자열로 읽는 준비된 매퍼를 반환한다.
                return (row, number) -> row.getString(1);
            }

            /** 현재 행을 Java 객체로 변환한다. 커서 이동은 호출자가 담당하고 rowNum은 0부터 시작한다. */
            @Override
            public String mapRow(ResultSet rs, int rowNum) {
                // 실행되면 안 되는 매핑 경로에 들어왔으므로 테스트를 실패시킨다.
                fail("Template must use the prepared mapper");
                // 단건 결과가 없거나 테스트용 반환값이 없음을 null로 나타낸다.
                return null;
            }
        };
        // 기대값과 실제 반환값·저장 데이터·호출 횟수가 같은지 검증한다.
        assertEquals(List.of("Alice", "Bob"), template.query("SELECT 'Alice' UNION ALL SELECT 'Bob'", mapper));
        // 기대값과 실제 반환값·저장 데이터·호출 횟수가 같은지 검증한다.
        assertEquals(1, preparations.get());
        // 기대값과 실제 반환값·저장 데이터·호출 횟수가 같은지 검증한다.
        assertEquals("Carol", template.queryForObject("SELECT 'Carol'", mapper));
        // 기대값과 실제 반환값·저장 데이터·호출 횟수가 같은지 검증한다.
        assertEquals(2, preparations.get());
        // 행 존재·프록시 여부·자동 커밋 등 지정 조건이 참인지 확인한다.
        assertTrue(template.query("SELECT 1 WHERE FALSE", mapper).isEmpty());
        // 조회 결과 또는 조회하지 않은 필드가 null인지 확인한다.
        assertNull(template.queryForObject("SELECT 1 WHERE FALSE", mapper));
        // 기대값과 실제 반환값·저장 데이터·호출 횟수가 같은지 검증한다.
        assertEquals(2, preparations.get());
    }

    /** 준비된 매퍼를 번갈아 사용해도 조회 간 필드 매핑이 섞이지 않는지 검증한다. */
    @Test
    void preparedMappingsRemainIndependentWhenResultsAreInterleaved() throws Exception {
        // H2 연결 공급자를 만든다.
        var ds = new JdbcDataSource();
        // 고유 이름의 H2 인메모리 DB URL을 지정해 테스트를 격리한다.
        ds.setURL("jdbc:h2:mem:" + UUID.randomUUID());
        // 대상 클래스의 필드에 값을 저장할 자동 매퍼를 만든다.
        var mapper = new BeanPropertyRowMapper<>(User.class);
        // 연결·Statement·파일 자원을 열고 try-with-resources로 사용 후 자동 정리한다.
        try (var connection = ds.getConnection();
             // 같은 연결에서 별도 조회를 실행할 Statement를 만든다.
             var firstStatement = connection.createStatement();
             // 같은 연결에서 별도 조회를 실행할 Statement를 만든다.
             var secondStatement = connection.createStatement();
             // SELECT를 실행해 커서와 컬럼 메타데이터를 제공하는 ResultSet을 얻는다.
             var first = firstStatement.executeQuery("SELECT CAST(1 AS BIGINT) AS id, 'Alice' AS name");
             // SELECT를 실행해 커서와 컬럼 메타데이터를 제공하는 ResultSet을 얻는다.
             var second = secondStatement.executeQuery("SELECT 'Bob' AS name, CAST(2 AS BIGINT) AS id")) {
            // 행 존재·프록시 여부·자동 커밋 등 지정 조건이 참인지 확인한다.
            assertTrue(first.next());
            // 행 존재·프록시 여부·자동 커밋 등 지정 조건이 참인지 확인한다.
            assertTrue(second.next());
            // 현재 ResultSet 전용으로 준비한 매퍼를 보관한다.
            var firstMapper = mapper.prepare(first);
            // 현재 ResultSet 전용으로 준비한 매퍼를 보관한다.
            var secondMapper = mapper.prepare(second);
            // 현재 행을 변환한 객체를 보관한다.
            User bob = secondMapper.mapRow(second, 0);
            // 현재 행을 변환한 객체를 보관한다.
            User alice = firstMapper.mapRow(first, 0);
            // 기대값과 실제 반환값·저장 데이터·호출 횟수가 같은지 검증한다.
            assertEquals(2L, bob.id);
            // 기대값과 실제 반환값·저장 데이터·호출 횟수가 같은지 검증한다.
            assertEquals("Bob", bob.name);
            // 기대값과 실제 반환값·저장 데이터·호출 횟수가 같은지 검증한다.
            assertEquals(1L, alice.id);
            // 기대값과 실제 반환값·저장 데이터·호출 횟수가 같은지 검증한다.
            assertEquals("Alice", alice.name);
        }
    }

    /** 기본 생성자가 없는 타입을 거부하는지 검증한다. */
    @Test
    void rejectsClassesWithoutDefaultConstructor() {
        // 호출이 지정 예외를 던지는지 검증하고 필요하면 예외 객체를 보관한다.
        assertThrows(IllegalArgumentException.class,
                () -> new BeanPropertyRowMapper<>(Integer.class));
    }
}
