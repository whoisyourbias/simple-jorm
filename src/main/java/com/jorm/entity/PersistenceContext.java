package com.jorm.entity;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** 세션 하나가 소유하는 관리 객체 저장소. SQL과 트랜잭션 수명은 관리하지 않는다. */
final class PersistenceContext {
    private final Map<EntityKey, Object> entities = new HashMap<>();
    private final Map<Object, EntityKey> originalKeys = new IdentityHashMap<>();

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
        entities.put(key, entity);
        originalKeys.put(entity, key);
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
            }
        }
    }

    void clear() {
        entities.clear();
        originalKeys.clear();
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
