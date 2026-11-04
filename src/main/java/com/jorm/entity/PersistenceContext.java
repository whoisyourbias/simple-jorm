package com.jorm.entity;

import java.lang.reflect.Field;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** 세션 하나가 소유하는 관리 객체와 스냅샷. SQL과 트랜잭션 수명은 관리하지 않는다. */
final class PersistenceContext {
    private final Map<EntityKey, Object> entities = new HashMap<>();
    private final Map<Object, EntityKey> originalKeys = new IdentityHashMap<>();
    private final Map<Object, Object[]> snapshots = new IdentityHashMap<>();

    <T> T find(Class<T> entityType, Object id, EntityMetadata metadata) throws SQLException {
        if (!supports(metadata)) {
            return null;
        }
        Object entity = entities.get(new EntityKey(entityType, id));
        if (entity != null) {
            validateId(entity, metadata);
        }
        return entityType.cast(entity);
    }

    /** 다른 인자로 같은 행을 조회했어도 기존 관리 객체를 유지한다. */
    @SuppressWarnings("unchecked")
    <T> T manage(T entity, EntityMetadata metadata) throws SQLException {
        if (!supports(metadata)) {
            return entity;
        }
        EntityKey key = key(entity, metadata);
        Object existing = entities.get(key);
        if (existing != null) {
            validateId(existing, metadata);
            return (T) existing;
        }
        Object[] snapshot = capture(entity, metadata);
        entities.put(key, entity);
        originalKeys.put(entity, key);
        snapshots.put(entity, snapshot);
        return entity;
    }

    void requireUnmanagedKey(Object entity, EntityMetadata metadata) throws SQLException {
        if (supports(metadata) && entities.containsKey(key(entity, metadata))) {
            throw new IllegalArgumentException("이미 관리 중인 ID를 다시 저장할 수 없습니다.");
        }
    }

    void requireSameInstance(Object entity, EntityMetadata metadata) throws SQLException {
        if (supports(metadata)) {
            Object existing = entities.get(key(entity, metadata));
            if (existing != null && existing != entity) {
                throw new IllegalArgumentException("같은 ID의 다른 객체가 이미 관리 중입니다.");
            }
        }
    }

    void validateId(Object entity, EntityMetadata metadata) throws SQLException {
        EntityKey original = originalKeys.get(entity);
        if (original != null && !Objects.equals(original.id(), id(entity, metadata))) {
            throw new IllegalArgumentException("관리 객체의 ID를 변경할 수 없습니다.");
        }
    }

    void remove(Object entity, EntityMetadata metadata) throws SQLException {
        if (supports(metadata)) {
            Object removed = entities.remove(key(entity, metadata));
            if (removed != null) {
                originalKeys.remove(removed);
                snapshots.remove(removed);
            }
        }
    }

    void clear() {
        entities.clear();
        originalKeys.clear();
        snapshots.clear();
    }

    /** flush 중 0행 UPDATE로 제거되어도 순회가 안전하도록 복사한다. */
    List<Object> managedEntities() {
        return new ArrayList<>(entities.values());
    }

    boolean isDirty(Object entity, EntityMetadata metadata) throws SQLException {
        return !Arrays.deepEquals(snapshots.get(entity), capture(entity, metadata));
    }

    /** 명시적 update는 기존 관리 객체만 갱신하며 새 객체를 관리하지 않는다. */
    void refreshSnapshot(Object entity, EntityMetadata metadata) throws SQLException {
        if (originalKeys.containsKey(entity)) {
            snapshots.put(entity, capture(entity, metadata));
        }
    }

    private Object[] capture(Object entity, EntityMetadata metadata) throws SQLException {
        List<Object> values = new ArrayList<>();
        for (Field field : metadata.getColumns().values()) {
            if (field.equals(metadata.getIdField())) {
                continue;
            }
            try {
                Object value = field.get(entity);
                if (value instanceof byte[] bytes) {
                    value = bytes.clone();
                } else if (value instanceof java.util.Date date) {
                    // clone은 JDBC Date/Time의 타입과 Timestamp의 nanos까지 보존한다.
                    value = date.clone();
                }
                values.add(value);
            } catch (IllegalAccessException failure) {
                throw new SQLException("스냅샷 필드를 읽을 수 없습니다: " + field.getName(), failure);
            }
        }
        return values.toArray();
    }

    private boolean supports(EntityMetadata metadata) {
        Class<?> type = metadata.getIdField().getType();
        return type == Long.class || type == long.class || type == Integer.class || type == int.class
                || type == String.class || type == UUID.class;
    }

    private EntityKey key(Object entity, EntityMetadata metadata) throws SQLException {
        return new EntityKey(entity.getClass(), id(entity, metadata));
    }

    private Object id(Object entity, EntityMetadata metadata) throws SQLException {
        try {
            return metadata.getIdField().get(entity);
        } catch (IllegalAccessException failure) {
            throw new SQLException("관리 객체의 ID를 읽을 수 없습니다.", failure);
        }
    }
}
