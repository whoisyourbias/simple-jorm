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
        @Select("SELECT id, name, email FROM users WHERE id = ?")
        User findById(Long id) throws SQLException;

        @Select("SELECT id, name, email FROM users WHERE id = 1")
        User findFirst() throws SQLException;

        @Select("SELECT id, name, email FROM users WHERE id = ? AND name = ?")
        User findByIdAndName(Long id, String name) throws SQLException;

        @Select("SELECT id, name, email FROM users ORDER BY id")
        List<User> findAll() throws SQLException;

        @Select("SELECT id, name, email FROM users WHERE id > ? ORDER BY id")
        List<User> findAfterId(Long id) throws SQLException;

        User findByName(String name);

        @Insert("INSERT INTO users (name, email) VALUES (?, ?)")
        int insert(String name, String email) throws SQLException;

        @Insert("INSERT INTO users (name, email) VALUES ('Diana', 'diana@example.com')")
        int insertWithoutArguments() throws SQLException;

        @Insert("INSERT INTO users (name, email) VALUES (?, ?)")
        void insertWithInvalidReturnType(String name, String email);

        @Select("SELECT id, name, email FROM users")
        @Insert("INSERT INTO users (name, email) VALUES (?, ?)")
        int insertWithConflictingAnnotations(String name, String email);

        @Update("UPDATE users SET name = ? WHERE id = ?")
        int updateName(String name, Long id) throws SQLException;

        @Delete("DELETE FROM users WHERE id = ?")
        int deleteById(Long id) throws SQLException;

        @Update("UPDATE users SET name = 'Updated'")
        int updateAllNames() throws SQLException;

        @Delete("DELETE FROM users")
        int deleteAll() throws SQLException;

        @Update("UPDATE users SET name = 'Updated'")
        void updateWithInvalidReturnType();

        @Delete("DELETE FROM users")
        void deleteWithInvalidReturnType();
    }

    @BeforeEach
    void setUp() throws Exception {
        var dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID());
        keepAlive = dataSource.getConnection();
        for (String resource : new String[]{"/schema.sql", "/data.sql"}) {
            var stream = getClass().getResourceAsStream(resource);
            assertNotNull(stream, "Missing test SQL resource: " + resource);
            try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                RunScript.execute(keepAlive, reader);
            }
        }
        jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @AfterEach
    void tearDown() throws SQLException {
        if (keepAlive != null) {
            keepAlive.close();
        }
    }

    @Test
    void createsProxyImplementingMapperInterface() {
        UserMapper mapper = MapperFactory.create(UserMapper.class, jdbcTemplate);

        assertInstanceOf(UserMapper.class, mapper);
        assertTrue(Proxy.isProxyClass(mapper.getClass()));
    }

    @Test
    void rejectsConcreteClasses() {
        var exception = assertThrows(IllegalArgumentException.class,
                () -> MapperFactory.create(String.class, jdbcTemplate));

        assertTrue(exception.getMessage().contains(String.class.getName()));
    }

    @Test
    void bindsArgumentAndReturnsMappedUser() throws SQLException {
        UserMapper mapper = MapperFactory.create(UserMapper.class, jdbcTemplate);

        User user = mapper.findById(2L);

        assertNotNull(user);
        assertEquals(2L, user.id);
        assertEquals("Bob", user.name);
        assertEquals("bob@example.com", user.email);
    }

    @Test
    void returnsNullWhenNoRowsMatch() throws SQLException {
        UserMapper mapper = MapperFactory.create(UserMapper.class, jdbcTemplate);

        assertNull(mapper.findById(999L));
    }

    @Test
    void supportsAnnotatedMethodsWithoutArguments() throws SQLException {
        UserMapper mapper = MapperFactory.create(UserMapper.class, jdbcTemplate);

        User user = mapper.findFirst();

        assertNotNull(user);
        assertEquals(1L, user.id);
        assertEquals("Alice", user.name);
        assertEquals("alice@example.com", user.email);
    }

    @Test
    void bindsMultipleArgumentsInOrder() throws SQLException {
        UserMapper mapper = MapperFactory.create(UserMapper.class, jdbcTemplate);

        User user = mapper.findByIdAndName(2L, "Bob");

        assertNotNull(user);
        assertEquals(2L, user.id);
        assertEquals("Bob", user.name);
        assertEquals("bob@example.com", user.email);
        assertNull(mapper.findByIdAndName(2L, "Alice"));
    }

    @Test
    void returnsAllRowsAsMappedListWithoutArguments() throws SQLException {
        UserMapper mapper = MapperFactory.create(UserMapper.class, jdbcTemplate);

        List<User> users = mapper.findAll();

        assertEquals(List.of(1L, 2L, 3L), users.stream().map(user -> user.id).toList());
        assertEquals(List.of("Alice", "Bob", "Charlie"),
                users.stream().map(user -> user.name).toList());
        assertEquals(List.of("alice@example.com", "bob@example.com", "charlie@example.com"),
                users.stream().map(user -> user.email).toList());
    }

    @Test
    void bindsArgumentForListQuery() throws SQLException {
        UserMapper mapper = MapperFactory.create(UserMapper.class, jdbcTemplate);

        List<User> users = mapper.findAfterId(1L);

        assertEquals(List.of(2L, 3L), users.stream().map(user -> user.id).toList());
        assertEquals(List.of("Bob", "Charlie"),
                users.stream().map(user -> user.name).toList());
    }

    @Test
    void returnsEmptyListWhenNoRowsMatch() throws SQLException {
        UserMapper mapper = MapperFactory.create(UserMapper.class, jdbcTemplate);

        List<User> users = mapper.findAfterId(999L);

        assertNotNull(users);
        assertTrue(users.isEmpty());
    }

    @Test
    void insertsUserAndReturnsAffectedRowCount() throws SQLException {
        UserMapper mapper = MapperFactory.create(UserMapper.class, jdbcTemplate);

        assertEquals(1, mapper.insert("Diana", "diana@example.com"));

        List<User> users = mapper.findAll();
        assertEquals(4, users.size());
        assertEquals("Diana", users.get(3).name);
        assertEquals("diana@example.com", users.get(3).email);
    }

    @Test
    void insertsWithoutArguments() throws SQLException {
        UserMapper mapper = MapperFactory.create(UserMapper.class, jdbcTemplate);

        assertEquals(1, mapper.insertWithoutArguments());
        assertEquals("diana@example.com", mapper.findAll().get(3).email);
    }

    @Test
    void rejectsInvalidInsertReturnTypeBeforeChangingDatabase() throws SQLException {
        UserMapper mapper = MapperFactory.create(UserMapper.class, jdbcTemplate);

        assertThrows(IllegalArgumentException.class,
                () -> mapper.insertWithInvalidReturnType("Diana", "diana@example.com"));
        assertEquals(3, mapper.findAll().size());
    }

    @Test
    void rejectsConflictingSqlAnnotationsBeforeChangingDatabase() throws SQLException {
        UserMapper mapper = MapperFactory.create(UserMapper.class, jdbcTemplate);

        assertThrows(IllegalArgumentException.class,
                () -> mapper.insertWithConflictingAnnotations("Diana", "diana@example.com"));
        assertEquals(3, mapper.findAll().size());
    }

    @Test
    void propagatesInsertSqlErrors() throws SQLException {
        UserMapper mapper = MapperFactory.create(UserMapper.class, jdbcTemplate);

        assertThrows(SQLException.class, () -> mapper.insert("Another Alice", "alice@example.com"));
        assertEquals(3, mapper.findAll().size());
    }

    @Test
    void updatesMatchingUserAndReturnsAffectedRowCount() throws SQLException {
        UserMapper mapper = MapperFactory.create(UserMapper.class, jdbcTemplate);

        assertEquals(1, mapper.updateName("Robert", 2L));
        assertEquals("Robert", mapper.findById(2L).name);
        assertEquals("bob@example.com", mapper.findById(2L).email);
        assertEquals("Alice", mapper.findById(1L).name);
        assertEquals(0, mapper.updateName("Missing", 999L));
    }

    @Test
    void deletesMatchingUserAndReturnsAffectedRowCount() throws SQLException {
        UserMapper mapper = MapperFactory.create(UserMapper.class, jdbcTemplate);

        assertEquals(1, mapper.deleteById(2L));
        assertNull(mapper.findById(2L));
        assertEquals(List.of(1L, 3L), mapper.findAll().stream().map(user -> user.id).toList());
        assertEquals(0, mapper.deleteById(2L));
    }

    @Test
    void supportsUpdateAndDeleteWithoutArguments() throws SQLException {
        UserMapper mapper = MapperFactory.create(UserMapper.class, jdbcTemplate);

        assertEquals(3, mapper.updateAllNames());
        assertEquals(List.of("Updated", "Updated", "Updated"),
                mapper.findAll().stream().map(user -> user.name).toList());
        assertEquals(3, mapper.deleteAll());
        assertTrue(mapper.findAll().isEmpty());
        assertEquals(0, mapper.deleteAll());
    }

    @Test
    void rejectsInvalidUpdateAndDeleteReturnTypesBeforeChangingDatabase() throws SQLException {
        UserMapper mapper = MapperFactory.create(UserMapper.class, jdbcTemplate);

        assertThrows(IllegalArgumentException.class, mapper::updateWithInvalidReturnType);
        assertThrows(IllegalArgumentException.class, mapper::deleteWithInvalidReturnType);
        assertEquals(List.of("Alice", "Bob", "Charlie"),
                mapper.findAll().stream().map(user -> user.name).toList());
    }

    @Test
    void rejectsMethodsWithoutSelect() {
        UserMapper mapper = MapperFactory.create(UserMapper.class, jdbcTemplate);

        var exception = assertThrows(UnsupportedOperationException.class,
                () -> mapper.findByName("Alice"));

        assertTrue(exception.getMessage().contains("findByName"));
    }

    @Test
    void handlesObjectMethodsWithoutSelect() {
        UserMapper mapper = MapperFactory.create(UserMapper.class, jdbcTemplate);
        UserMapper other = MapperFactory.create(UserMapper.class, jdbcTemplate);

        assertEquals("MapperProxy", mapper.toString());
        assertEquals(System.identityHashCode(mapper), mapper.hashCode());
        assertTrue(mapper.equals(mapper));
        assertFalse(mapper.equals(other));
        assertFalse(mapper.equals(null));
        assertFalse(mapper.equals(new Object()));
    }
}
