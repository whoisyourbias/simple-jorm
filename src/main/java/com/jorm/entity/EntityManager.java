package com.jorm.entity;

import com.jorm.jdbc.JdbcTemplate;

import java.lang.reflect.Field;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class EntityManager {

    private final JdbcTemplate jdbcTemplate;

    // 클래스마다 분석 결과를 한 번 저장하고 재사용한다.
    // 하나의 EntityManager를 여러 요청 스레드가 공유하면 캐시 조회·추가가 동시에 발생할 수 있다.
    // 일반 HashMap 대신 ConcurrentHashMap을 사용해 이러한 동시 접근을 안전하게 처리한다.
    // 클래스별 캐시에는 순서가 필요 없다. 컬럼 순서 보존을 위한 EntityMetadata의 LinkedHashMap과 목적이 다르다.
    // 캐시의 동시 접근 보장이 EntityManager 전체의 스레드 안전성을 보장하는 것은 아니다.
    private final Map<Class<?>, EntityMetadata> metadataCache =
            new ConcurrentHashMap<>();

    /** SQL 실행기를 전달받는다. */
    public EntityManager(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 객체의 필드 값으로 INSERT를 생성하고 실행한다. */
    public int persist(Object entity) throws SQLException {
        // 실제 객체의 클래스에 해당하는 매핑 정보를 가져온다.
        EntityMetadata metadata = getMetadata(entity.getClass());

        // 현재 단계에서는 ID를 직접 지정하도록 한다.
        if (readField(metadata.getIdField(), entity) == null) {
            throw new IllegalArgumentException("ID를 먼저 지정해야 합니다.");
        }

        // LinkedHashMap의 순서대로 컬럼 이름을 연결한다.
        String columnNames = String.join(
                ", ", metadata.getColumns().keySet()
        );

        // 컬럼 개수만큼 물음표를 만든다.
        String placeholders = String.join(
                ", ",
                Collections.nCopies(metadata.getColumns().size(), "?")
        );

        // 예: INSERT INTO users (id, user_name, email) VALUES (?, ?, ?)
        String sql = "INSERT INTO " + metadata.getTableName()
                + " (" + columnNames + ")"
                + " VALUES (" + placeholders + ")";

        // SQL 컬럼과 같은 순서로 객체에서 값을 읽는다.
        List<Object> values = new ArrayList<>();

        for (Field field : metadata.getColumns().values()) {
            values.add(readField(field, entity));
        }

        // 값은 SQL 문자열에 합치지 않고 파라미터로 바인딩한다.
        return jdbcTemplate.update(sql, values.toArray());
    }

    /** 처음 사용하는 클래스만 분석하고 이후에는 캐시를 사용한다. */
    private EntityMetadata getMetadata(Class<?> entityType) {
        // 같은 클래스에 대한 조회·생성·저장을 원자적으로 처리한다.
        // 여러 스레드가 동시에 처음 요청해도 정상적으로 생성·저장된 메타데이터를 공유한다.
        // 생성된 메타데이터는 이후 변경하지 않고 재사용한다.
        return metadataCache.computeIfAbsent(
                entityType,
                EntityMetadata::new
        );
    }

    /** 메타데이터에서 접근을 허용한 필드의 실제 값을 읽는다. */
    private Object readField(Field field, Object entity) throws SQLException {
        try {
            return field.get(entity);
        } catch (IllegalAccessException e) {
            throw new SQLException(
                    "필드 값을 읽을 수 없습니다: " + field.getName(), e
            );
        }
    }
}
