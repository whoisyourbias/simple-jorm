package com.jorm.mapper;

import com.jorm.jdbc.JdbcTemplate;
import org.h2.jdbcx.JdbcDataSource;
import org.h2.tools.RunScript;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MapperFactoryTest {

    private Connection keepAlive;
    private JdbcTemplate jdbcTemplate;

    static class User {
        Long id;
        String name;
        String email;
    }

    interface UserMapper {
        /** ID 조건으로 사용자 한 명을 조회하도록 선언한다. */
        @Select("SELECT id, name, email FROM users WHERE id = ?")
        User findById(Long id) throws SQLException;

        /** 인자 없이 ID가 1인 사용자를 조회하도록 선언한다. */
        @Select("SELECT id, name, email FROM users WHERE id = 1")
        User findFirst() throws SQLException;

        /** ID와 이름을 순서대로 바인딩해 사용자를 조회하도록 선언한다. */
        @Select("SELECT id, name, email FROM users WHERE id = ? AND name = ?")
        User findByIdAndName(Long id, String name) throws SQLException;

        /** 모든 사용자를 ID 순서의 리스트로 조회하도록 선언한다. */
        @Select("SELECT id, name, email FROM users ORDER BY id")
        List<User> findAll() throws SQLException;

        /** 지정 ID보다 큰 사용자를 ID 순서로 조회하도록 선언한다. */
        @Select("SELECT id, name, email FROM users WHERE id > ? ORDER BY id")
        List<User> findAfterId(Long id) throws SQLException;

        /** SQL 어노테이션이 없는 메서드를 거부하는 테스트용 선언이다. */
        User findByName(String name);

        /** 이름과 이메일로 사용자를 추가하고 삽입 행 수를 반환하도록 선언한다. */
        @Insert("INSERT INTO users (name, email) VALUES (?, ?)")
        int insert(String name, String email) throws SQLException;

        /** 고정 SQL로 사용자를 추가하는 인자 없는 메서드다. */
        @Insert("INSERT INTO users (name, email) VALUES ('Diana', 'diana@example.com')")
        int insertWithoutArguments() throws SQLException;

        /** 잘못된 INSERT 반환 타입을 실행 전에 거부하는 테스트용 선언이다. */
        @Insert("INSERT INTO users (name, email) VALUES (?, ?)")
        void insertWithInvalidReturnType(String name, String email);

        /** 중복 SQL 어노테이션을 실행 전에 거부하는 테스트용 선언이다. */
        @Select("SELECT id, name, email FROM users")
        @Insert("INSERT INTO users (name, email) VALUES (?, ?)")
        int insertWithConflictingAnnotations(String name, String email);

        /** 이름과 ID를 바인딩해 대상 사용자의 이름만 수정하도록 선언한다. */
        @Update("UPDATE users SET name = ? WHERE id = ?")
        int updateName(String name, Long id) throws SQLException;

        /** 지정 ID의 사용자를 삭제하고 삭제 행 수를 반환하도록 선언한다. */
        @Delete("DELETE FROM users WHERE id = ?")
        int deleteById(Long id) throws SQLException;

        /** 모든 사용자의 이름을 고정 값으로 수정하도록 선언한다. */
        @Update("UPDATE users SET name = 'Updated'")
        int updateAllNames() throws SQLException;

        /** 모든 사용자를 삭제하고 삭제 행 수를 반환하도록 선언한다. */
        @Delete("DELETE FROM users")
        int deleteAll() throws SQLException;

        /** 잘못된 UPDATE 반환 타입을 실행 전에 거부하는 테스트용 선언이다. */
        @Update("UPDATE users SET name = 'Updated'")
        void updateWithInvalidReturnType();

        /** 잘못된 DELETE 반환 타입을 실행 전에 거부하는 테스트용 선언이다. */
        @Delete("DELETE FROM users")
        void deleteWithInvalidReturnType();
    }

    /** 전달된 값을 Up 필드에 저장하는 JavaBean 설정자다. */
    @BeforeEach
    void setUp() throws Exception {
        // H2 연결 공급자를 만든다.
        var dataSource = new JdbcDataSource();
        // 고유 이름의 H2 인메모리 DB URL을 지정해 테스트를 격리한다.
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID());
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

    /** 지정 Mapper 인터페이스의 JDK 프록시를 생성하는지 검증한다. */
    @Test
    void createsProxyImplementingMapperInterface() {
        // SQL 실행을 위임할 Mapper 프록시를 만든다.
        UserMapper mapper = MapperFactory.create(UserMapper.class, jdbcTemplate);

        // 프록시가 요청한 Mapper 인터페이스 타입인지 확인한다.
        assertInstanceOf(UserMapper.class, mapper);
        // 행 존재·프록시 여부·자동 커밋 등 지정 조건이 참인지 확인한다.
        assertTrue(Proxy.isProxyClass(mapper.getClass()));
    }

    /** 일반 클래스를 Mapper로 지정하면 거부하는지 검증한다. */
    @Test
    void rejectsConcreteClasses() {
        // 호출이 지정 예외를 던지는지 검증하고 필요하면 예외 객체를 보관한다.
        var exception = assertThrows(IllegalArgumentException.class,
                () -> MapperFactory.create(String.class, jdbcTemplate));

        // 행 존재·프록시 여부·자동 커밋 등 지정 조건이 참인지 확인한다.
        assertTrue(exception.getMessage().contains(String.class.getName()));
    }

    /** Mapper 인자의 조회 조건 적용과 필드 매핑을 검증한다. */
    @Test
    void bindsArgumentAndReturnsMappedUser() throws SQLException {
        // SQL 실행을 위임할 Mapper 프록시를 만든다.
        UserMapper mapper = MapperFactory.create(UserMapper.class, jdbcTemplate);

        // Mapper 프록시를 통해 조건에 맞는 사용자를 조회하고 결과를 보관한다.
        User user = mapper.findById(2L);

        // 조회 객체 또는 SQL 리소스가 실제로 존재하는지 확인한다.
        assertNotNull(user);
        // 기대값과 실제 반환값·저장 데이터·호출 횟수가 같은지 검증한다.
        assertEquals(2L, user.id);
        // 기대값과 실제 반환값·저장 데이터·호출 횟수가 같은지 검증한다.
        assertEquals("Bob", user.name);
        // 기대값과 실제 반환값·저장 데이터·호출 횟수가 같은지 검증한다.
        assertEquals("bob@example.com", user.email);
    }

    /** 빈 단건 조회에서 null을 반환하는지 검증한다. */
    @Test
    void returnsNullWhenNoRowsMatch() throws SQLException {
        // SQL 실행을 위임할 Mapper 프록시를 만든다.
        UserMapper mapper = MapperFactory.create(UserMapper.class, jdbcTemplate);

        // 조회 결과 또는 조회하지 않은 필드가 null인지 확인한다.
        assertNull(mapper.findById(999L));
    }

    /** 인자 없는 Mapper 조회를 검증한다. */
    @Test
    void supportsAnnotatedMethodsWithoutArguments() throws SQLException {
        // SQL 실행을 위임할 Mapper 프록시를 만든다.
        UserMapper mapper = MapperFactory.create(UserMapper.class, jdbcTemplate);

        // Mapper 프록시를 통해 조건에 맞는 사용자를 조회하고 결과를 보관한다.
        User user = mapper.findFirst();

        // 조회 객체 또는 SQL 리소스가 실제로 존재하는지 확인한다.
        assertNotNull(user);
        // 기대값과 실제 반환값·저장 데이터·호출 횟수가 같은지 검증한다.
        assertEquals(1L, user.id);
        // 기대값과 실제 반환값·저장 데이터·호출 횟수가 같은지 검증한다.
        assertEquals("Alice", user.name);
        // 기대값과 실제 반환값·저장 데이터·호출 횟수가 같은지 검증한다.
        assertEquals("alice@example.com", user.email);
    }

    /** Mapper의 여러 인자를 선언 순서대로 바인딩하는지 검증한다. */
    @Test
    void bindsMultipleArgumentsInOrder() throws SQLException {
        // SQL 실행을 위임할 Mapper 프록시를 만든다.
        UserMapper mapper = MapperFactory.create(UserMapper.class, jdbcTemplate);

        // Mapper 프록시를 통해 조건에 맞는 사용자를 조회하고 결과를 보관한다.
        User user = mapper.findByIdAndName(2L, "Bob");

        // 조회 객체 또는 SQL 리소스가 실제로 존재하는지 확인한다.
        assertNotNull(user);
        // 기대값과 실제 반환값·저장 데이터·호출 횟수가 같은지 검증한다.
        assertEquals(2L, user.id);
        // 기대값과 실제 반환값·저장 데이터·호출 횟수가 같은지 검증한다.
        assertEquals("Bob", user.name);
        // 기대값과 실제 반환값·저장 데이터·호출 횟수가 같은지 검증한다.
        assertEquals("bob@example.com", user.email);
        // 조회 결과 또는 조회하지 않은 필드가 null인지 확인한다.
        assertNull(mapper.findByIdAndName(2L, "Alice"));
    }

    /** 인자 없는 목록 조회의 전체 행과 필드를 검증한다. */
    @Test
    void returnsAllRowsAsMappedListWithoutArguments() throws SQLException {
        // SQL 실행을 위임할 Mapper 프록시를 만든다.
        UserMapper mapper = MapperFactory.create(UserMapper.class, jdbcTemplate);

        // Mapper 프록시를 통해 조건에 맞는 사용자를 조회하고 결과를 보관한다.
        List<User> users = mapper.findAll();

        // 기대값과 실제 반환값·저장 데이터·호출 횟수가 같은지 검증한다.
        assertEquals(List.of(1L, 2L, 3L), users.stream().map(user -> user.id).toList());
        // 기대값과 실제 반환값·저장 데이터·호출 횟수가 같은지 검증한다.
        assertEquals(List.of("Alice", "Bob", "Charlie"),
                users.stream().map(user -> user.name).toList());
        // 기대값과 실제 반환값·저장 데이터·호출 횟수가 같은지 검증한다.
        assertEquals(List.of("alice@example.com", "bob@example.com", "charlie@example.com"),
                users.stream().map(user -> user.email).toList());
    }

    /** Mapper 목록 조회의 조건 인자를 검증한다. */
    @Test
    void bindsArgumentForListQuery() throws SQLException {
        // SQL 실행을 위임할 Mapper 프록시를 만든다.
        UserMapper mapper = MapperFactory.create(UserMapper.class, jdbcTemplate);

        // Mapper 프록시를 통해 조건에 맞는 사용자를 조회하고 결과를 보관한다.
        List<User> users = mapper.findAfterId(1L);

        // 기대값과 실제 반환값·저장 데이터·호출 횟수가 같은지 검증한다.
        assertEquals(List.of(2L, 3L), users.stream().map(user -> user.id).toList());
        // 기대값과 실제 반환값·저장 데이터·호출 횟수가 같은지 검증한다.
        assertEquals(List.of("Bob", "Charlie"),
                users.stream().map(user -> user.name).toList());
    }

    /** 빈 목록 조회가 null 대신 빈 리스트를 반환하는지 검증한다. */
    @Test
    void returnsEmptyListWhenNoRowsMatch() throws SQLException {
        // SQL 실행을 위임할 Mapper 프록시를 만든다.
        UserMapper mapper = MapperFactory.create(UserMapper.class, jdbcTemplate);

        // Mapper 프록시를 통해 조건에 맞는 사용자를 조회하고 결과를 보관한다.
        List<User> users = mapper.findAfterId(999L);

        // 조회 객체 또는 SQL 리소스가 실제로 존재하는지 확인한다.
        assertNotNull(users);
        // 행 존재·프록시 여부·자동 커밋 등 지정 조건이 참인지 확인한다.
        assertTrue(users.isEmpty());
    }

    /** Mapper 삽입 행 수와 저장 데이터를 검증한다. */
    @Test
    void insertsUserAndReturnsAffectedRowCount() throws SQLException {
        // SQL 실행을 위임할 Mapper 프록시를 만든다.
        UserMapper mapper = MapperFactory.create(UserMapper.class, jdbcTemplate);

        // 기대값과 실제 반환값·저장 데이터·호출 횟수가 같은지 검증한다.
        assertEquals(1, mapper.insert("Diana", "diana@example.com"));

        // Mapper 프록시를 통해 조건에 맞는 사용자를 조회하고 결과를 보관한다.
        List<User> users = mapper.findAll();
        // 기대값과 실제 반환값·저장 데이터·호출 횟수가 같은지 검증한다.
        assertEquals(4, users.size());
        // 기대값과 실제 반환값·저장 데이터·호출 횟수가 같은지 검증한다.
        assertEquals("Diana", users.get(3).name);
        // 기대값과 실제 반환값·저장 데이터·호출 횟수가 같은지 검증한다.
        assertEquals("diana@example.com", users.get(3).email);
    }

    /** 인자 없는 고정 SQL 삽입을 검증한다. */
    @Test
    void insertsWithoutArguments() throws SQLException {
        // SQL 실행을 위임할 Mapper 프록시를 만든다.
        UserMapper mapper = MapperFactory.create(UserMapper.class, jdbcTemplate);

        // 기대값과 실제 반환값·저장 데이터·호출 횟수가 같은지 검증한다.
        assertEquals(1, mapper.insertWithoutArguments());
        // 기대값과 실제 반환값·저장 데이터·호출 횟수가 같은지 검증한다.
        assertEquals("diana@example.com", mapper.findAll().get(3).email);
    }

    /** 잘못된 INSERT 반환 타입을 거부하고 DB 변경을 막는지 검증한다. */
    @Test
    void rejectsInvalidInsertReturnTypeBeforeChangingDatabase() throws SQLException {
        // SQL 실행을 위임할 Mapper 프록시를 만든다.
        UserMapper mapper = MapperFactory.create(UserMapper.class, jdbcTemplate);

        // 호출이 지정 예외를 던지는지 검증하고 필요하면 예외 객체를 보관한다.
        assertThrows(IllegalArgumentException.class,
                () -> mapper.insertWithInvalidReturnType("Diana", "diana@example.com"));
        // 기대값과 실제 반환값·저장 데이터·호출 횟수가 같은지 검증한다.
        assertEquals(3, mapper.findAll().size());
    }

    /** 중복 SQL 선언을 거부하고 DB 변경을 막는지 검증한다. */
    @Test
    void rejectsConflictingSqlAnnotationsBeforeChangingDatabase() throws SQLException {
        // SQL 실행을 위임할 Mapper 프록시를 만든다.
        UserMapper mapper = MapperFactory.create(UserMapper.class, jdbcTemplate);

        // 호출이 지정 예외를 던지는지 검증하고 필요하면 예외 객체를 보관한다.
        assertThrows(IllegalArgumentException.class,
                () -> mapper.insertWithConflictingAnnotations("Diana", "diana@example.com"));
        // 기대값과 실제 반환값·저장 데이터·호출 횟수가 같은지 검증한다.
        assertEquals(3, mapper.findAll().size());
    }

    /** 유일성 제약 위반 INSERT의 예외 전파와 데이터 유지를 검증한다. */
    @Test
    void propagatesInsertSqlErrors() throws SQLException {
        // SQL 실행을 위임할 Mapper 프록시를 만든다.
        UserMapper mapper = MapperFactory.create(UserMapper.class, jdbcTemplate);

        // 호출이 지정 예외를 던지는지 검증하고 필요하면 예외 객체를 보관한다.
        assertThrows(SQLException.class, () -> mapper.insert("Another Alice", "alice@example.com"));
        // 기대값과 실제 반환값·저장 데이터·호출 횟수가 같은지 검증한다.
        assertEquals(3, mapper.findAll().size());
    }

    /** 수정 행 수와 대상 변경·비대상 데이터 유지를 검증한다. */
    @Test
    void updatesMatchingUserAndReturnsAffectedRowCount() throws SQLException {
        // SQL 실행을 위임할 Mapper 프록시를 만든다.
        UserMapper mapper = MapperFactory.create(UserMapper.class, jdbcTemplate);

        // 기대값과 실제 반환값·저장 데이터·호출 횟수가 같은지 검증한다.
        assertEquals(1, mapper.updateName("Robert", 2L));
        // 기대값과 실제 반환값·저장 데이터·호출 횟수가 같은지 검증한다.
        assertEquals("Robert", mapper.findById(2L).name);
        // 기대값과 실제 반환값·저장 데이터·호출 횟수가 같은지 검증한다.
        assertEquals("bob@example.com", mapper.findById(2L).email);
        // 기대값과 실제 반환값·저장 데이터·호출 횟수가 같은지 검증한다.
        assertEquals("Alice", mapper.findById(1L).name);
        // 기대값과 실제 반환값·저장 데이터·호출 횟수가 같은지 검증한다.
        assertEquals(0, mapper.updateName("Missing", 999L));
    }

    /** 삭제 행 수와 대상 삭제·비대상 데이터 유지를 검증한다. */
    @Test
    void deletesMatchingUserAndReturnsAffectedRowCount() throws SQLException {
        // SQL 실행을 위임할 Mapper 프록시를 만든다.
        UserMapper mapper = MapperFactory.create(UserMapper.class, jdbcTemplate);

        // 기대값과 실제 반환값·저장 데이터·호출 횟수가 같은지 검증한다.
        assertEquals(1, mapper.deleteById(2L));
        // 조회 결과 또는 조회하지 않은 필드가 null인지 확인한다.
        assertNull(mapper.findById(2L));
        // 기대값과 실제 반환값·저장 데이터·호출 횟수가 같은지 검증한다.
        assertEquals(List.of(1L, 3L), mapper.findAll().stream().map(user -> user.id).toList());
        // 기대값과 실제 반환값·저장 데이터·호출 횟수가 같은지 검증한다.
        assertEquals(0, mapper.deleteById(2L));
    }

    /** 인자 없는 전체 수정·삭제를 검증한다. */
    @Test
    void supportsUpdateAndDeleteWithoutArguments() throws SQLException {
        // SQL 실행을 위임할 Mapper 프록시를 만든다.
        UserMapper mapper = MapperFactory.create(UserMapper.class, jdbcTemplate);

        // 기대값과 실제 반환값·저장 데이터·호출 횟수가 같은지 검증한다.
        assertEquals(3, mapper.updateAllNames());
        // 기대값과 실제 반환값·저장 데이터·호출 횟수가 같은지 검증한다.
        assertEquals(List.of("Updated", "Updated", "Updated"),
                mapper.findAll().stream().map(user -> user.name).toList());
        // 기대값과 실제 반환값·저장 데이터·호출 횟수가 같은지 검증한다.
        assertEquals(3, mapper.deleteAll());
        // 행 존재·프록시 여부·자동 커밋 등 지정 조건이 참인지 확인한다.
        assertTrue(mapper.findAll().isEmpty());
        // 기대값과 실제 반환값·저장 데이터·호출 횟수가 같은지 검증한다.
        assertEquals(0, mapper.deleteAll());
    }

    /** 잘못된 수정·삭제 반환 타입을 실행 전에 거부하는지 검증한다. */
    @Test
    void rejectsInvalidUpdateAndDeleteReturnTypesBeforeChangingDatabase() throws SQLException {
        // SQL 실행을 위임할 Mapper 프록시를 만든다.
        UserMapper mapper = MapperFactory.create(UserMapper.class, jdbcTemplate);

        // 호출이 지정 예외를 던지는지 검증하고 필요하면 예외 객체를 보관한다.
        assertThrows(IllegalArgumentException.class, mapper::updateWithInvalidReturnType);
        // 호출이 지정 예외를 던지는지 검증하고 필요하면 예외 객체를 보관한다.
        assertThrows(IllegalArgumentException.class, mapper::deleteWithInvalidReturnType);
        // 기대값과 실제 반환값·저장 데이터·호출 횟수가 같은지 검증한다.
        assertEquals(List.of("Alice", "Bob", "Charlie"),
                mapper.findAll().stream().map(user -> user.name).toList());
    }

    /** SQL 어노테이션이 없는 메서드를 거부하는지 검증한다. */
    @Test
    void rejectsMethodsWithoutSelect() {
        // SQL 실행을 위임할 Mapper 프록시를 만든다.
        UserMapper mapper = MapperFactory.create(UserMapper.class, jdbcTemplate);

        // 호출이 지정 예외를 던지는지 검증하고 필요하면 예외 객체를 보관한다.
        var exception = assertThrows(UnsupportedOperationException.class,
                () -> mapper.findByName("Alice"));

        // 행 존재·프록시 여부·자동 커밋 등 지정 조건이 참인지 확인한다.
        assertTrue(exception.getMessage().contains("findByName"));
    }

    /** toString·hashCode·equals를 SQL 없이 프록시 기준으로 처리하는지 검증한다. */
    @Test
    void handlesObjectMethodsWithoutSelect() {
        // SQL 실행을 위임할 Mapper 프록시를 만든다.
        UserMapper mapper = MapperFactory.create(UserMapper.class, jdbcTemplate);
        // SQL 실행을 위임할 Mapper 프록시를 만든다.
        UserMapper other = MapperFactory.create(UserMapper.class, jdbcTemplate);

        // 기대값과 실제 반환값·저장 데이터·호출 횟수가 같은지 검증한다.
        assertEquals("MapperProxy", mapper.toString());
        // 기대값과 실제 반환값·저장 데이터·호출 횟수가 같은지 검증한다.
        assertEquals(System.identityHashCode(mapper), mapper.hashCode());
        // 행 존재·프록시 여부·자동 커밋 등 지정 조건이 참인지 확인한다.
        assertTrue(mapper.equals(mapper));
        // 추가 행 또는 동등성 비교 조건이 거짓인지 확인한다.
        assertFalse(mapper.equals(other));
        // 추가 행 또는 동등성 비교 조건이 거짓인지 확인한다.
        assertFalse(mapper.equals(null));
        // 추가 행 또는 동등성 비교 조건이 거짓인지 확인한다.
        assertFalse(mapper.equals(new Object()));
    }
}
