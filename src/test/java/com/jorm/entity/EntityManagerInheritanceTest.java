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

/** 부모 ID·컬럼과 제외 필드가 실제 CRUD의 모든 SQL에 적용되는지 검증한다. */
class EntityManagerInheritanceTest {
    private Connection keepAlive;
    private JdbcTemplate jdbc;
    private EntityManager entities;

    private static class BaseRecord {
        @Id @Column("record_id") Long id;
        @Column("created_by") String creator;
        transient String requestToken = "new request";
        @Transient final String displayHint = "local hint";
    }

    @Table("records")
    private static class Record extends BaseRecord {
        private String title;
        @Transient private String calculatedLabel = "initial label";
    }

    private static class IdentityBase {
        @Id @GeneratedValue @Column("generated_id") Long id;
        @Column("owner_name") String owner;
    }

    @Table("generated_records")
    private static class GeneratedRecord extends IdentityBase {
        private String title;
    }

    private static class UuidBase {
        @Id @GeneratedValue(strategy = GenerationType.UUID) @Column("record_id") UUID id;
    }

    @Table("uuid_records")
    private static class UuidRecord extends UuidBase {
        private String title;
    }

    private static class NamedBase { private String title; }

    @Table("missing_table")
    private static class AmbiguousRecord extends NamedBase {
        @Id private Long id = 1L;
        @Column("other_title") private String title;
    }

    @BeforeEach
    void setUp() throws SQLException {
        JdbcDataSource source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:inheritance-" + UUID.randomUUID());
        keepAlive = source.getConnection();
        jdbc = new JdbcTemplate(new TransactionManager(source));
        entities = new EntityManager(jdbc);
        // requestToken에 대응하는 DB 컬럼은 의도적으로 없다.
        jdbc.update("CREATE TABLE records (record_id BIGINT PRIMARY KEY, created_by VARCHAR(100), title VARCHAR(100))");
        jdbc.update("CREATE TABLE generated_records (generated_id BIGINT GENERATED ALWAYS AS IDENTITY "
                + "(START WITH 41) PRIMARY KEY, owner_name VARCHAR(100), title VARCHAR(100))");
        jdbc.update("CREATE TABLE uuid_records (record_id UUID PRIMARY KEY, title VARCHAR(100))");
    }

    @AfterEach
    void tearDown() throws SQLException {
        if (keepAlive != null) keepAlive.close();
    }

    @Test
    void performsCrudUsingParentIdAndAliasesWithoutTransientDatabaseColumn() throws SQLException {
        Record record = new Record();
        record.id = 7L;
        record.creator = "parent";
        record.title = "first";
        record.requestToken = "write only";
        record.calculatedLabel = "write label";
        assertEquals(1, entities.persist(record));
        assertEquals("parent", jdbc.queryForObject("SELECT created_by FROM records WHERE record_id = 7",
                (rs, row) -> rs.getString(1)));

        Record loaded = entities.find(Record.class, 7L);
        assertEquals(7L, loaded.id);
        assertEquals("parent", loaded.creator);
        assertEquals("first", loaded.title);
        assertEquals("new request", loaded.requestToken);
        assertEquals("local hint", loaded.displayHint);
        assertEquals("initial label", loaded.calculatedLabel);
        loaded.creator = "updated parent";
        loaded.title = "updated child";
        loaded.requestToken = "still local";
        loaded.calculatedLabel = "update label";
        assertEquals(1, entities.update(loaded));
        Record updated = entities.find(Record.class, 7L);
        assertEquals("updated parent", updated.creator);
        assertEquals("updated child", updated.title);
        assertEquals("new request", updated.requestToken);
        assertEquals("initial label", updated.calculatedLabel);
        assertEquals(1, entities.remove(updated));
        assertNull(entities.find(Record.class, 7L));
    }

    @Test
    void assignsIdentityToParentFieldAndFindsUsingParentAlias() throws SQLException {
        GeneratedRecord record = new GeneratedRecord();
        record.owner = "owner";
        record.title = "generated";
        assertEquals(1, entities.persist(record));
        assertEquals(41L, record.id);
        GeneratedRecord loaded = entities.find(GeneratedRecord.class, 41L);
        assertEquals(41L, loaded.id);
        assertEquals("owner", loaded.owner);
        assertEquals("generated", loaded.title);
    }

    @Test
    void assignsUuidToInheritedIdentifierAndRestoresItFromDatabase() throws SQLException {
        UuidRecord record = new UuidRecord();
        record.title = "uuid";
        assertEquals(1, entities.persist(record));
        assertNotNull(record.id);
        assertEquals(record.id, jdbc.queryForObject("SELECT record_id FROM uuid_records",
                (rs, row) -> rs.getObject(1, UUID.class)));
        UuidRecord loaded = entities.find(UuidRecord.class, record.id);
        assertEquals(record.id, loaded.id);
        assertEquals("uuid", loaded.title);
    }

    @Test
    void rejectsAmbiguousHierarchyBeforeCrudCanReachAMissingDatabaseTable() {
        AmbiguousRecord record = new AmbiguousRecord();
        // SQL까지 진행하면 missing_table 때문에 SQLException이 발생한다.
        assertThrows(IllegalArgumentException.class, () -> entities.persist(record));
        assertThrows(IllegalArgumentException.class, () -> entities.find(AmbiguousRecord.class, 1L));
        assertThrows(IllegalArgumentException.class, () -> entities.update(record));
        assertThrows(IllegalArgumentException.class, () -> entities.remove(record));
    }
}
