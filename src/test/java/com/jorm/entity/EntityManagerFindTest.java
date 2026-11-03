package com.jorm.entity;

import com.jorm.jdbc.JdbcTemplate;
import com.jorm.transaction.TransactionManager;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** INSERT 기능과 독립된 초기 데이터로 ID 조회와 엔티티 필드 매핑을 확인한다. */
class EntityManagerFindTest {
    private Connection keepAlive;
    private JdbcTemplate jdbc;
    private TransactionManager transactions;
    private EntityManager entities;

    @Table("users")
    private static class User {
        @Column("user_name")
        private String name;
        @Id
        @Column("user_id")
        private Long id;
        private String email;

        /** 조회 시 private 기본 생성자도 사용할 수 있어야 한다. */
        private User() {}

        /** 트랜잭션 안에서 persist와 find를 함께 사용하는 테스트에 필요한 생성자다. */
        private User(Long id, String name, String email) {
            this.id = id;
            this.name = name;
            this.email = email;
        }
    }

    @Table("users")
    private static class NoDefaultConstructor {
        @Id @Column("user_id")
        private Long id;

        /** 이 타입은 persist할 수 있지만 find할 수는 없도록 선언한다. */
        private NoDefaultConstructor(Long id) { this.id = id; }
    }

    @Table("users")
    private static class WrongFieldType {
        @Id @Column("user_id")
        private String id;
    }

    @Table("missing_table")
    private static class MissingTable {
        @Id private Long id;
    }

    @Table("simple_entities")
    private static class SimpleEntity {
        @Id private Long id;
    }

    @Table("users")
    private abstract static class AbstractEntity {
        @Id @Column("user_id") private Long id;
    }

    @Table("users")
    private static class BrokenConstructor {
        @Id @Column("user_id")
        private Long id;

        /** 조회 중 객체 생성 실패를 재현한다. */
        private BrokenConstructor() { throw new IllegalStateException("constructor failed"); }
    }

    /** 테스트마다 독립 DB를 만들고 직접 SQL로 서로 다른 두 사용자를 저장한다. */
    @BeforeEach
    void setUp() throws SQLException {
        JdbcDataSource source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:entity-find-" + UUID.randomUUID());
        keepAlive = source.getConnection();
        transactions = new TransactionManager(source);
        jdbc = new JdbcTemplate(transactions);
        entities = new EntityManager(jdbc);
        jdbc.update("CREATE TABLE users (user_id BIGINT PRIMARY KEY, "
                + "user_name VARCHAR(100), email VARCHAR(150))");
        // persist의 매핑 오류가 find의 오류와 상쇄되지 않도록 독립 SQL로 준비한다.
        jdbc.update("INSERT INTO users VALUES (1, 'Alice', 'alice@example.com'), (2, 'Bob', NULL)");
    }

    /** 유지 연결을 닫아 테스트 DB를 정리한다. */
    @AfterEach
    void tearDown() throws SQLException {
        if (keepAlive != null) keepAlive.close();
    }

    /** ID가 첫 필드가 아니고 별칭이 있어도 지정 ID의 전체 필드를 조회해야 한다. */
    @Test
    void findsRequestedIdUsingColumnAliasesAndPrivateConstructor() throws SQLException {
        User user = entities.find(User.class, 1L);
        assertNotNull(user);
        assertEquals(1L, user.id);
        assertEquals("Alice", user.name);
        assertEquals("alice@example.com", user.email);
    }

    /** 다른 ID의 값을 섞지 않고 SQL NULL을 Java null로 보존해야 한다. */
    @Test
    void mapsNullAndSelectsOnlyMatchingId() throws SQLException {
        User user = entities.find(User.class, 2L);
        assertNotNull(user);
        assertEquals(2L, user.id);
        assertEquals("Bob", user.name);
        assertNull(user.email);
    }

    /** 존재하지 않는 ID의 단건 결과는 null이다. */
    @Test
    void returnsNullWhenIdDoesNotExist() throws SQLException {
        assertNull(entities.find(User.class, 999L));
    }

    /** null ID는 조회 전에 잘못된 입력으로 거부한다. */
    @Test
    void rejectsNullId() {
        assertThrows(IllegalArgumentException.class, () -> entities.find(User.class, null));
    }

    /** @Column 없는 ID의 기본 필드명을 사용하고 엔티티에 없는 DB 컬럼은 조회하지 않는다. */
    @Test
    void usesDefaultIdColumnAndSelectsOnlyMappedColumns() throws SQLException {
        jdbc.update("CREATE TABLE simple_entities (id BIGINT PRIMARY KEY, extra VARCHAR(20))");
        jdbc.update("INSERT INTO simple_entities VALUES (5, 'ignored')");
        SimpleEntity entity = entities.find(SimpleEntity.class, 5L);
        assertNotNull(entity);
        assertEquals(5L, entity.id);
    }

    /** 생성자가 있어도 직접 객체를 만들 수 없는 추상 타입은 조회 전에 거부한다. */
    @Test
    void rejectsAbstractEntityType() {
        assertThrows(IllegalArgumentException.class, () -> entities.find(AbstractEntity.class, 999L));
    }

    /** 기본 생성자가 없으면 행 유무와 관계없이 조회용 타입으로 사용할 수 없다. */
    @Test
    void rejectsMissingDefaultConstructorButStillAllowsPersist() throws SQLException {
        assertThrows(IllegalArgumentException.class,
                () -> entities.find(NoDefaultConstructor.class, 999L));
        assertEquals(1, entities.persist(new NoDefaultConstructor(3L)));
        assertNotNull(entities.find(User.class, 3L));
    }

    /** DB 값과 필드 타입이 맞지 않으면 묵시적으로 잘못 변환하지 않고 매핑 실패를 알린다. */
    @Test
    void reportsIncompatibleFieldTypeAsSqlException() {
        assertThrows(SQLException.class, () -> entities.find(WrongFieldType.class, 1L));
    }

    /** 존재하지 않는 테이블의 SQL 오류가 null 결과로 숨겨지지 않아야 한다. */
    @Test
    void propagatesSqlExecutionFailure() {
        assertThrows(SQLException.class, () -> entities.find(MissingTable.class, 1L));
    }

    /** 기본 생성자 실행 실패의 원인을 보존해야 한다. */
    @Test
    void preservesConstructorFailureCause() {
        SQLException failure = assertThrows(SQLException.class,
                () -> entities.find(BrokenConstructor.class, 1L));
        assertInstanceOf(IllegalStateException.class, failure.getCause().getCause());
    }

    /** 메타데이터 캐시는 엔티티 캐시가 아니므로 매 조회는 DB에서 새 객체를 만든다. */
    @Test
    void readsFreshStateInsteadOfCachingEntityInstances() throws SQLException {
        User first = entities.find(User.class, 1L);
        jdbc.update("UPDATE users SET user_name = 'Updated' WHERE user_id = 1");
        User second = entities.find(User.class, 1L);
        assertNotSame(first, second);
        assertEquals("Alice", first.name);
        assertEquals("Updated", second.name);
    }

    /** 같은 트랜잭션의 미커밋 INSERT를 조회하고 실패 후에는 조회되지 않아야 한다. */
    @Test
    void sharesTransactionWithPersistAndObservesRollback() throws SQLException {
        SQLException expected = new SQLException("rollback after find");
        assertSame(expected, assertThrows(SQLException.class, () -> transactions.execute(() -> {
            entities.persist(new User(3L, "Carol", null));
            User user = entities.find(User.class, 3L);
            assertNotNull(user);
            assertEquals("Carol", user.name);
            throw expected;
        })));
        assertNull(entities.find(User.class, 3L));
    }

    /*** 영속성 컨텍스트 **/
    /** 같은 트랜잭션에서 같은 엔티티를 조회하면 같은 객체를 반환해야한다.**/
    @Test
    void returnsSameInstanceWithinTransaction() throws SQLException {
        transactions.execute(() -> {
            User first = entities.find(User.class, 1L);
            User second = entities.find(User.class, 1L);

            // 같은 행을 표현하는 관리 객체는 하나여야 한다.
            assertSame(first, second);
            return null;
        });
    }
}
