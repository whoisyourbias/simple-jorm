package com.jorm.mapper;

import com.jorm.jdbc.JdbcTemplate;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MapperNamedParametersTest {
    private Connection keepAlive;
    private NamedMapper mapper;

    static class User {
        Long id;
        String name;
        String email;
    }

    interface NamedMapper {
        @Select("SELECT * FROM users WHERE name = #{name} AND id = #{id}")
        User find(@Param("id") Long id, @Param("name") String name) throws SQLException;

        @Select("SELECT * FROM users WHERE id = #{id} OR id = #{id} ORDER BY id")
        List<User> repeated(@Param("id") Long id) throws SQLException;

        @Insert("INSERT INTO users VALUES (#{id}, #{name}, #{email})")
        int insert(@Param("email") String email, @Param("id") Long id,
                   @Param("name") String name) throws SQLException;

        @Update("UPDATE users SET name = #{name} WHERE id = #{id}")
        int rename(@Param("id") Long id, @Param("name") String name) throws SQLException;

        @Delete("DELETE FROM users WHERE id = #{id}")
        int delete(@Param("id") Long id) throws SQLException;

        @Select("SELECT * FROM users WHERE id = ?")
        User positional(Long id) throws SQLException;

        @Select("SELECT * FROM users WHERE name = '#{literal}' OR id = #{id} /* #{ignored} ? ${ignored} */ -- #{ignored}\n")
        User quotedAndCommented(@Param("id") Long id) throws SQLException;

        @Select("SELECT id, name AS \"name\", email FROM users WHERE name = 'it''s #{literal}' OR id = #{id}")
        User escapedQuote(@Param("id") Long id) throws SQLException;

        @Select("SELECT * FROM users WHERE id = #{unknown}")
        User unknown(@Param("id") Long id) throws SQLException;

        @Select("SELECT * FROM users WHERE id = #{id}")
        User unnamed(Long id) throws SQLException;

        @Select("SELECT * FROM users WHERE id = #{id} AND name = #{name}")
        User partlyNamed(@Param("id") Long id, String name) throws SQLException;

        @Select("SELECT * FROM users WHERE id = #{id}")
        User duplicate(@Param("id") Long id, @Param("id") Long other) throws SQLException;

        @Select("SELECT * FROM users WHERE id = #{id}")
        User invalidName(@Param(" ") Long id) throws SQLException;

        @Select("SELECT * FROM users WHERE id = #{id} AND name = ?")
        User mixed(@Param("id") Long id, @Param("name") String name) throws SQLException;

        @Select("SELECT * FROM users WHERE id = #{id")
        User unclosed(@Param("id") Long id) throws SQLException;

        @Select("SELECT * FROM users WHERE id = #{}")
        User empty(@Param("id") Long id) throws SQLException;

        @Select("SELECT * FROM users WHERE id = #{id,jdbcType=BIGINT}")
        User unsupportedOption(@Param("id") Long id) throws SQLException;

        @Select("SELECT * FROM users WHERE id = ${id}")
        User raw(@Param("id") Long id) throws SQLException;

        @Update("UPDATE users SET name = #{missing} WHERE id = #{id}")
        int invalidUpdate(@Param("id") Long id) throws SQLException;

        default User first() throws SQLException {
            return find(1L, "Alice");
        }

        default String greeting(String name) {
            return "Hello " + name;
        }

        default void fail(IllegalStateException failure) {
            throw failure;
        }
    }

    @BeforeEach
    void setUp() throws SQLException {
        var dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID());
        keepAlive = dataSource.getConnection();
        try (var statement = keepAlive.createStatement()) {
            statement.execute("CREATE TABLE users (id BIGINT PRIMARY KEY, name VARCHAR(255), email VARCHAR(255))");
            statement.execute("INSERT INTO users VALUES (1, 'Alice', 'alice@example.com'), (2, 'Bob', 'bob@example.com')");
        }
        mapper = MapperFactory.create(NamedMapper.class, new JdbcTemplate(dataSource));
    }

    @AfterEach
    void tearDown() throws SQLException {
        if (keepAlive != null) keepAlive.close();
    }

    @Test
    void bindsBySqlOrderRatherThanArgumentOrder() throws SQLException {
        assertEquals(2L, mapper.find(2L, "Bob").id);
        assertNull(mapper.find(2L, "Alice"));
    }

    @Test
    void bindsRepeatedNameAtEveryOccurrence() throws SQLException {
        assertEquals(List.of(2L), mapper.repeated(2L).stream().map(user -> user.id).toList());
    }

    @Test
    void bindsNamedInsertUpdateAndDelete() throws SQLException {
        assertEquals(1, mapper.insert("charlie@example.com", 3L, "Charlie"));
        assertEquals("charlie@example.com", mapper.positional(3L).email);
        assertEquals(1, mapper.rename(3L, "Charles"));
        assertEquals("Charles", mapper.positional(3L).name);
        assertEquals(1, mapper.delete(3L));
        assertNull(mapper.positional(3L));
        assertEquals("Alice", mapper.positional(1L).name);
    }

    @Test
    void bindsNullValueWithoutTreatingItAsMissing() throws SQLException {
        assertEquals(1, mapper.insert(null, 3L, "Charlie"));
        assertNull(mapper.positional(3L).email);
    }

    @Test
    void preservesSqlLookingInputAsData() throws SQLException {
        String input = "Bob' OR 1=1 -- #{id} ${id}";
        assertNull(mapper.find(2L, input));
        assertEquals(1, mapper.rename(2L, input));
        assertEquals(input, mapper.positional(2L).name);
        assertEquals("Alice", mapper.positional(1L).name);
    }

    @Test
    void skipsTokensInLiteralsAndComments() throws SQLException {
        assertEquals("Bob", mapper.quotedAndCommented(2L).name);
        assertEquals("Alice", mapper.escapedQuote(1L).name);
    }

    @Test
    void rejectsUnknownNameBeforeJdbcExecution() {
        NamedMapper noJdbc = MapperFactory.create(NamedMapper.class, null);
        var failure = assertThrows(IllegalArgumentException.class, () -> noJdbc.unknown(1L));
        assertTrue(failure.getMessage().contains("unknown"));
    }

    @Test
    void requiresExplicitNamesForAllNamedSqlArguments() {
        NamedMapper noJdbc = MapperFactory.create(NamedMapper.class, null);
        assertThrows(IllegalArgumentException.class, () -> noJdbc.unnamed(1L));
        assertThrows(IllegalArgumentException.class, () -> noJdbc.partlyNamed(1L, "Alice"));
    }

    @Test
    void rejectsDuplicateAndInvalidParameterNames() {
        NamedMapper noJdbc = MapperFactory.create(NamedMapper.class, null);
        assertThrows(IllegalArgumentException.class, () -> noJdbc.duplicate(1L, 2L));
        assertThrows(IllegalArgumentException.class, () -> noJdbc.invalidName(1L));
    }

    @Test
    void rejectsMixingPositionalAndNamedPlaceholders() {
        NamedMapper noJdbc = MapperFactory.create(NamedMapper.class, null);
        assertThrows(IllegalArgumentException.class, () -> noJdbc.mixed(1L, "Alice"));
    }

    @Test
    void rejectsMalformedAndUnsupportedTokens() {
        NamedMapper noJdbc = MapperFactory.create(NamedMapper.class, null);
        assertThrows(IllegalArgumentException.class, () -> noJdbc.unclosed(1L));
        assertThrows(IllegalArgumentException.class, () -> noJdbc.empty(1L));
        assertThrows(IllegalArgumentException.class, () -> noJdbc.unsupportedOption(1L));
    }

    @Test
    void rejectsRawSqlInterpolationBeforeJdbcExecution() {
        NamedMapper noJdbc = MapperFactory.create(NamedMapper.class, null);
        assertThrows(IllegalArgumentException.class, () -> noJdbc.raw(1L));
    }

    @Test
    void invalidNamedUpdateLeavesDatabaseUntouched() throws SQLException {
        assertThrows(IllegalArgumentException.class, () -> mapper.invalidUpdate(1L));
        assertEquals("Alice", mapper.positional(1L).name);
        assertEquals("Bob", mapper.positional(2L).name);
    }

    @Test
    void executesDefaultMethodWithoutSqlAnnotation() {
        assertEquals("Hello JORM", mapper.greeting("JORM"));
    }

    @Test
    void defaultMethodCallsAnnotatedMethodThroughProxy() throws SQLException {
        assertEquals("Alice", mapper.first().name);
    }

    @Test
    void propagatesDefaultMethodExceptionUnchanged() {
        var failure = new IllegalStateException("default failure");
        assertSame(failure, assertThrows(IllegalStateException.class, () -> mapper.fail(failure)));
    }
}
