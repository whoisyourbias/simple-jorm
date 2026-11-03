package com.jorm.entity;

import com.jorm.jdbc.JdbcTemplate;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import java.sql.SQLException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class EntityManagerSessionTest {
    @Table("users")
    private static class User {
        @Id private long id;
        private String name;
    }

    /** 실제 세션이 종료되면 어떤 CRUD도 실행할 수 없어야 한다. */
    @Test
    void closedSessionRejectsOperationsAndAllowsRepeatedClose() {
        JdbcDataSource source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:closed-" + UUID.randomUUID());
        EntityManagerSession session = new EntityManagerSession(new JdbcTemplate(source), EntityMetadata::new);
        session.close();
        session.close();
        assertThrows(IllegalStateException.class, () -> session.find(User.class, 1L));
        assertThrows(IllegalStateException.class, () -> session.persist(new User()));
        assertThrows(IllegalStateException.class, () -> session.update(new User()));
        assertThrows(IllegalStateException.class, () -> session.remove(new User()));
    }

    /** long 원시 타입도 같은 세션에서 wrapper ID와 같은 키로 조회해야 한다. */
    @Test
    void supportsPrimitiveIdIdentity() throws SQLException {
        JdbcDataSource source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:primitive-" + UUID.randomUUID());
        try (var keeper = source.getConnection()) {
            JdbcTemplate jdbc = new JdbcTemplate(source);
            jdbc.update("CREATE TABLE users(id BIGINT PRIMARY KEY, name VARCHAR(100))");
            jdbc.update("INSERT INTO users VALUES(1, 'Alice')");
            try (EntityManagerSession session = new EntityManagerSession(jdbc, EntityMetadata::new)) {
                User user = session.find(User.class, 1L);
                assertSame(user, session.find(User.class, 1L));
            }
        }
    }
}
