package com.jorm.entity;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** 부모 필드가 누락되거나 모호한 매핑이 SQL까지 전달되는 회귀를 막는다. */
class EntityMetadataInheritanceTest {
    private static class Root {
        @Id @Column("record_id") private Long id = 8L;
    }

    private static class Middle extends Root {
        @Column("created_by") private String creator = "parent";
        private transient final String localHint = "ignored";
    }

    @Table("records")
    private static class Record extends Middle {
        private String title = "child";
    }

    @Test
    void mapsPrivateFieldsAcrossMultipleParentsWithoutParentTable() throws Exception {
        EntityMetadata metadata = new EntityMetadata(Record.class);
        Record record = new Record();
        assertEquals(Set.of("record_id", "created_by", "title"), metadata.getColumns().keySet());
        assertEquals(8L, metadata.getIdField().get(record));
        assertEquals("parent", metadata.getColumns().get("created_by").get(record));
        assertEquals("child", metadata.getColumns().get("title").get(record));
    }

    @Table("records")
    private static class JavaTransientFields {
        @Id private Long id;
        @Column(" ") @GeneratedValue private transient final String ignored = "local";
    }

    @Test
    void skipsJavaTransientBeforeValidatingFinalColumnAndGeneration() {
        assertEquals(Set.of("id"), new EntityMetadata(JavaTransientFields.class).getColumns().keySet());
    }

    @Table("records")
    private static class AnotherId extends Root { @Id private Long otherId; }

    @Test
    void rejectsAnAdditionalIdInTheChild() {
        assertThrows(IllegalArgumentException.class, () -> new EntityMetadata(AnotherId.class));
    }

    private static class ColumnParent { @Column("title") private String shared; }

    @Table("records")
    private static class DuplicateColumn extends ColumnParent {
        @Id private Long id;
        private String title;
    }

    @Test
    void rejectsDuplicateColumnAcrossParents() {
        assertThrows(IllegalArgumentException.class, () -> new EntityMetadata(DuplicateColumn.class));
    }

    private static class NamedParent {
        private String label;
    }

    @Table("records")
    private static class HiddenField extends NamedParent {
        @Id private Long id;
        @Column("other_label") private String label;
    }

    @Test
    void rejectsHiddenMappedFieldEvenWithDifferentColumnAliases() {
        assertThrows(IllegalArgumentException.class, () -> new EntityMetadata(HiddenField.class));
    }

    @Table("parents")
    private static class TableParent { @Id private Long id; }
    private static class MissingOwnTable extends TableParent { private String title; }

    @Test
    void stillRequiresTableOnConcreteEntity() {
        assertThrows(IllegalArgumentException.class, () -> new EntityMetadata(MissingOwnTable.class));
    }

    @Table("records")
    private static class ExcludedFields extends Root {
        @Transient @Id @GeneratedValue @Column(" ") private final String ignored = "local";
        @Transient @Column("record_id") private String collidingAlias;
    }

    @Test
    void skipsAnnotationExcludedFieldsBeforeAllMappingValidation() {
        assertEquals(Set.of("record_id"), new EntityMetadata(ExcludedFields.class).getColumns().keySet());
    }

    @Table("records")
    private static class ExcludedHiddenField extends NamedParent {
        @Id private Long id;
        @Transient private final String label = "local";
    }

    @Test
    void excludedHiddenFieldDoesNotConflictWithMappedParentField() throws Exception {
        EntityMetadata metadata = new EntityMetadata(ExcludedHiddenField.class);
        assertEquals(Set.of("id", "label"), metadata.getColumns().keySet());
        assertEquals(NamedParent.class, metadata.getColumns().get("label").getDeclaringClass());
    }

    @Table("records")
    private static class ExcludedOnlyId {
        @Transient @Id private Long id;
        private String title;
    }

    @Test
    void excludedIdCannotServeAsEntityIdentifier() {
        assertThrows(IllegalArgumentException.class, () -> new EntityMetadata(ExcludedOnlyId.class));
    }
}
