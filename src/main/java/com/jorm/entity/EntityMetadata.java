package com.jorm.entity;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class EntityMetadata {

    private final String tableName;
    private final Field idField;
    private final boolean generatedId;

    // 키: DB 컬럼명, 값: 값을 읽거나 쓸 Java 필드
    private final Map<String, Field> columns;

    /** 엔티티 클래스의 어노테이션과 필드를 분석한다. */
    public EntityMetadata(Class<?> entityType) {
        // 클래스에 붙은 @Table을 읽는다.
        Table table = entityType.getAnnotation(Table.class);

        if (table == null || table.value().isBlank()) {
            throw new IllegalArgumentException("@Table과 테이블명이 필요합니다.");
        }

        this.tableName = table.value();

        Field discoveredId = null;
        // 필드 추가 순서를 저장하기 위해 LinkedHashMap을 사용한다.
        Map<String, Field> discoveredColumns = new LinkedHashMap<>();

        // 해당 클래스에 직접 선언된 필드를 조사한다.
        for (Field field : entityType.getDeclaredFields()) {
            // 생성 전략은 인스턴스의 ID 필드에서만 의미가 있다.
            if (field.isAnnotationPresent(GeneratedValue.class)
                    && (!field.isAnnotationPresent(Id.class)
                    || Modifier.isStatic(field.getModifiers()) || field.isSynthetic())) {
                throw new IllegalArgumentException("@GeneratedValue는 인스턴스 @Id 필드에만 지정합니다.");
            }
            // 클래스 공용 필드와 컴파일러가 만든 필드는 제외한다.
            if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) {
                continue;
            }

            // 조회 결과를 필드에 저장할 수 있도록 final 필드는 거부한다.
            if (Modifier.isFinal(field.getModifiers())) {
                throw new IllegalArgumentException(
                        "final 필드는 지원하지 않습니다: " + field.getName());
            }

            // private 필드도 리플렉션으로 읽고 쓸 수 있게 한다.
            if (!field.trySetAccessible()) {
                throw new IllegalArgumentException(
                        "접근할 수 없는 필드입니다: " + field.getName());
            }

            // @Column이 없으면 Java 필드명을 컬럼명으로 사용한다.
            Column column = field.getAnnotation(Column.class);
            String columnName =
                    column == null ? field.getName() : column.value();

            // 빈 이름이나 중복된 컬럼 매핑은 거부한다.
            if (columnName.isBlank()
                    || discoveredColumns.putIfAbsent(columnName, field) != null) {
                throw new IllegalArgumentException(
                        "비어 있거나 중복된 컬럼명입니다: " + columnName);
            }

            // @Id 필드는 정확히 하나만 허용한다.
            if (field.isAnnotationPresent(Id.class)) {
                if (discoveredId != null) {
                    throw new IllegalArgumentException("@Id는 하나만 지정해야 합니다.");
                }
                discoveredId = field;
            }
        }

        if (discoveredId == null) {
            throw new IllegalArgumentException("@Id 필드가 필요합니다.");
        }

        this.idField = discoveredId;
        this.generatedId = discoveredId.isAnnotationPresent(GeneratedValue.class);
        if (generatedId && discoveredId.getType() != Long.class
                && discoveredId.getType() != Integer.class) {
            throw new IllegalArgumentException("생성 ID는 Long 또는 Integer 필드여야 합니다.");
        }

        // 분석 이후 컬럼 구성이 바뀌지 않도록 변경을 막는다.
        this.columns = Collections.unmodifiableMap(discoveredColumns);
    }

    /** SQL 생성에 사용할 테이블명을 반환한다. */
    public String getTableName() {
        return tableName;
    }

    /** 식별자 값을 읽거나 저장할 필드를 반환한다. */
    public Field getIdField() {
        return idField;
    }

    /** DB identity 컬럼이 ID를 생성하는지 반환한다. */
    public boolean isGeneratedId() {
        return generatedId;
    }

    /** ID를 포함한 컬럼명과 Java 필드의 대응 관계를 반환한다. */
    public Map<String, Field> getColumns() {
        return columns;
    }
}
