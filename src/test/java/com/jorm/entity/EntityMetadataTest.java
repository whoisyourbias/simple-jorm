package com.jorm.entity;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** SQL 생성 전에 엔티티 매핑을 분석하고 잘못된 선언을 거부하는지 검증한다. */
class EntityMetadataTest {
    @Table("users")
    private static class User {
        // static 필드는 컬럼으로 취급하면 안 된다.
        private static final String TYPE = "user";
        @Id
        @Column("user_id")
        private Long id = 7L;
        @Column("user_name")
        private String name = "Alice";
        private String email = "alice@example.com";
    }

    /** 테이블명, ID 별칭, 기본 컬럼명과 비공개 필드 접근을 함께 검증한다. */
    @Test
    void resolvesTableIdAndColumnsAndCanReadPrivateFields() throws Exception {
        EntityMetadata metadata = new EntityMetadata(User.class);
        User user = new User();
        assertEquals("users", metadata.getTableName());
        assertEquals(Set.of("user_id", "user_name", "email"), metadata.getColumns().keySet());
        // ID 필드도 컬럼 목록에 포함되고 @Column의 별칭이 적용된다.
        assertEquals(metadata.getIdField(), metadata.getColumns().get("user_id"));
        assertEquals(7L, metadata.getIdField().get(user));
        assertEquals("Alice", metadata.getColumns().get("user_name").get(user));
        assertEquals("alice@example.com", metadata.getColumns().get("email").get(user));
    }

    /** SQL 생성 도중 외부 코드가 컬럼 구성을 바꾸지 못하는지 검증한다. */
    @Test
    void exposesUnmodifiableColumns() {
        EntityMetadata metadata = new EntityMetadata(User.class);
        assertThrows(UnsupportedOperationException.class, () -> metadata.getColumns().clear());
        assertEquals(3, metadata.getColumns().size());
    }

    private static class MissingTable { @Id Long id; }
    @Table(" ")
    private static class BlankTable { @Id Long id; }

    /** 매핑 대상 테이블을 결정할 수 없는 선언을 거부한다. */
    @Test
    void rejectsMissingOrBlankTable() {
        assertThrows(IllegalArgumentException.class, () -> new EntityMetadata(MissingTable.class));
        assertThrows(IllegalArgumentException.class, () -> new EntityMetadata(BlankTable.class));
    }

    @Table("users")
    private static class MissingId { String name; }
    @Table("users")
    private static class MultipleIds { @Id Long first; @Id Long second; }

    /** 식별자는 정확히 한 개여야 한다는 계약을 검증한다. */
    @Test
    void rejectsMissingOrMultipleIds() {
        assertThrows(IllegalArgumentException.class, () -> new EntityMetadata(MissingId.class));
        assertThrows(IllegalArgumentException.class, () -> new EntityMetadata(MultipleIds.class));
    }

    @Table("users")
    private static class BlankColumn { @Id Long id; @Column(" ") String name; }
    @Table("users")
    private static class DuplicateColumn { @Id Long id; @Column("id") String name; }

    /** 명시 컬럼명과 기본 필드명이 겹치는 경우도 중복 매핑으로 거부한다. */
    @Test
    void rejectsBlankOrDuplicateColumns() {
        assertThrows(IllegalArgumentException.class, () -> new EntityMetadata(BlankColumn.class));
        assertThrows(IllegalArgumentException.class, () -> new EntityMetadata(DuplicateColumn.class));
    }

    @Table("users")
    private static class FinalField { @Id Long id; final String name = "fixed"; }

    /** 조회 결과를 다시 쓸 수 없는 final 인스턴스 필드는 현재 지원 범위에서 제외한다. */
    @Test
    void rejectsFinalInstanceField() {
        assertThrows(IllegalArgumentException.class, () -> new EntityMetadata(FinalField.class));
    }
}
