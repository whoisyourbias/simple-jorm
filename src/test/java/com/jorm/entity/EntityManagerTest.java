package com.jorm.entity;

import com.jorm.jdbc.JdbcTemplate;
import com.jorm.transaction.TransactionManager;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** 자동 생성한 INSERT를 실제 H2에서 실행해 매핑·바인딩·트랜잭션을 검증한다. */
class EntityManagerTest {
    private Connection keepAlive;
    private JdbcTemplate jdbc;
    private TransactionManager transactions;
    private EntityManager entities;

    @Table("users")
    private static class User {
        private static final String TYPE = "user";
        @Column("user_name")
        private String name;
        // ID를 첫 필드로 선언하지 않아도 컬럼과 값의 대응이 맞아야 한다.
        @Id
        @Column("user_id")
        private Long id;
        private String email;

        /** 기본 생성자 없이도 persist가 전달된 객체의 값을 읽을 수 있게 구성한다. */
        private User(Long id, String name, String email) {
            this.id = id;
            this.name = name;
            this.email = email;
        }
    }

    // 테스트 결과 비교에만 사용하는 값 객체이며 자동 매핑 대상은 아니다.
    private record StoredUser(long id, String name, String email) {}

    /** 테스트마다 독립 DB와 같은 관리자를 공유하는 SQL 실행기·EntityManager를 만든다. */
    @BeforeEach
    void setUp() throws SQLException {
        JdbcDataSource source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:entity-" + UUID.randomUUID());
        // SQL마다 임시 연결을 닫아도 테스트 DB가 사라지지 않게 한다.
        keepAlive = source.getConnection();
        transactions = new TransactionManager(source);
        jdbc = new JdbcTemplate(transactions);
        entities = new EntityManager(jdbc);
        jdbc.update("CREATE TABLE users (user_id BIGINT PRIMARY KEY, "
                + "user_name VARCHAR(100), email VARCHAR(150))");
    }

    /** 유지 연결을 닫아 인메모리 DB를 해제한다. */
    @AfterEach
    void tearDown() throws SQLException {
        if (keepAlive != null) {
            keepAlive.close();
        }
    }

    /** ID·명시 컬럼명·기본 컬럼명·private 필드·static 제외를 실제 INSERT로 검증한다. */
    @Test
    void persistsAssignedIdAndMappedColumnsInMatchingOrder() throws SQLException {
        assertEquals(1, entities.persist(new User(7L, "Alice", "alice@example.com")));
        assertEquals(List.of(new StoredUser(7L, "Alice", "alice@example.com")), storedUsers());
    }

    /** 문자열을 SQL로 연결하지 않고 바인딩하며 null도 그대로 저장하는지 확인한다. */
    @Test
    void bindsQuotedTextAndNullAsValues() throws SQLException {
        String name = "O'Brien'); DROP TABLE users; --";
        assertEquals(1, entities.persist(new User(1L, name, null)));
        // 테이블이 남아 있고 문자열 전체가 데이터로 저장되어야 한다.
        assertEquals(List.of(new StoredUser(1L, name, null)), storedUsers());
    }

    /** ID 미지정 시 SQL 실행 없이 입력을 거부해야 한다. */
    @Test
    void rejectsNullIdWithoutInserting() throws SQLException {
        assertThrows(IllegalArgumentException.class,
                () -> entities.persist(new User(null, "Alice", "alice@example.com")));
        assertEquals(List.of(), storedUsers());
    }

    /** 중복 키 SQL 오류를 전달하고 앞서 저장한 행을 덮어쓰지 않아야 한다. */
    @Test
    void propagatesDuplicateIdFailureAndPreservesExistingRow() throws SQLException {
        entities.persist(new User(1L, "Alice", "alice@example.com"));
        assertThrows(SQLException.class,
                () -> entities.persist(new User(1L, "Bob", "bob@example.com")));
        assertEquals(List.of(new StoredUser(1L, "Alice", "alice@example.com")), storedUsers());
    }

    /** 메타데이터를 재사용해도 매 INSERT의 값은 현재 객체에서 새로 읽어야 한다. */
    @Test
    void repeatedPersistsReadEachEntityValues() throws SQLException {
        entities.persist(new User(1L, "Alice", "alice@example.com"));
        entities.persist(new User(2L, "Bob", "bob@example.com"));
        assertEquals(List.of(new StoredUser(1L, "Alice", "alice@example.com"),
                new StoredUser(2L, "Bob", "bob@example.com")), storedUsers());
    }

    /** 같은 ID의 관리 객체 충돌로 두 번째 저장이 거부되면 첫 삽입도 롤백되어야 한다. */
    @Test
    void participatesInTransactionAndRollsBackEarlierInsert() throws SQLException {
        assertThrows(IllegalArgumentException.class, () -> transactions.execute(() -> {
            entities.persist(new User(1L, "Alice", "alice@example.com"));
            entities.persist(new User(1L, "Bob", "bob@example.com"));
            return null;
        }));
        assertEquals(List.of(), storedUsers());
    }

    /** 정상 종료한 트랜잭션에서는 모든 삽입이 확정되어야 한다. */
    @Test
    void commitsMultiplePersistsTogether() throws SQLException {
        transactions.execute(() -> {
            entities.persist(new User(1L, "Alice", null));
            entities.persist(new User(2L, "Bob", null));
            return null;
        });
        assertEquals(List.of(new StoredUser(1L, "Alice", null),
                new StoredUser(2L, "Bob", null)), storedUsers());
    }

    /** 캐시가 비어 있는 상태에서 같은 EntityManager를 여러 스레드가 사용할 수 있는지 확인한다. */
    @Test
    void supportsConcurrentFirstUseWithIndependentEntities() throws Exception {
        List<Callable<Integer>> inserts = new ArrayList<>();
        for (long id = 1; id <= 16; id++) {
            // 스레드마다 다른 객체를 전달해 엔티티 자체를 동시에 수정하지 않는다.
            User user = new User(id, "User" + id, null);
            inserts.add(() -> entities.persist(user));
        }
        try (var executor = Executors.newFixedThreadPool(4)) {
            // 시간 제한을 두어 작업이 끝나지 않으면 테스트가 실패하도록 한다.
            for (var result : executor.invokeAll(inserts, 10, TimeUnit.SECONDS)) {
                assertEquals(1, result.get().intValue());
            }
        }
        List<StoredUser> expected = new ArrayList<>();
        for (long id = 1; id <= 16; id++) {
            expected.add(new StoredUser(id, "User" + id, null));
        }
        assertEquals(expected, storedUsers());
    }

    /** EntityManager와 별개로 직접 SELECT해 실제 저장 결과를 독립적으로 확인한다. */
    private List<StoredUser> storedUsers() throws SQLException {
        return jdbc.query("SELECT user_id, user_name, email FROM users ORDER BY user_id",
                (rs, row) -> new StoredUser(rs.getLong("user_id"),
                        rs.getString("user_name"), rs.getString("email")));
    }
}
