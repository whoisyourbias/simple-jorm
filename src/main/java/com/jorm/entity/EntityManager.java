package com.jorm.entity;

import com.jorm.jdbc.JdbcTemplate;
import java.sql.SQLException;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/** 공유 가능한 진입점. 실제 관리 객체는 트랜잭션별 세션이 소유한다. */
public class EntityManager {
    private static final Object SESSION_RESOURCE_KEY = new Object();
    private final JdbcTemplate jdbcTemplate;
    private final Map<Class<?>, EntityMetadata> metadataCache = new ConcurrentHashMap<>();

    public EntityManager(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate);
    }

    /** 즉시 INSERT하고 성공한 객체를 현재 세션에 등록한다. */
    public int persist(Object entity) throws SQLException {
        return withSession(session -> session.persist(entity));
    }

    /** 같은 ID의 관리 객체를 우선 반환한다. 트랜잭션 밖에서는 새로 조회한다. */
    public <T> T find(Class<T> entityType, Object id) throws SQLException {
        return withSession(session -> session.find(entityType, id));
    }

    /** ID 외 필드를 즉시 수정한다. 같은 ID의 다른 관리 객체가 있으면 거부한다. */
    public int update(Object entity) throws SQLException {
        return withSession(session -> session.update(entity));
    }

    /** ID 조건으로 즉시 삭제한 뒤 해당 키의 관리 객체를 제거한다. */
    public int remove(Object entity) throws SQLException {
        return withSession(session -> session.remove(entity));
    }

    private <T> T withSession(SessionCallback<T> callback) throws SQLException {
        EntityManagerHolder holder = jdbcTemplate.getTransactionManager().getOrCreateResource(
                SESSION_RESOURCE_KEY, () -> new EntityManagerHolder(newSession()));
        if (holder != null) {
            return callback.execute(holder.getSession());
        }
        try (EntityManagerSession session = newSession()) {
            return callback.execute(session);
        }
    }

    private EntityManagerSession newSession() {
        return new EntityManagerSession(jdbcTemplate,
                entityType -> metadataCache.computeIfAbsent(entityType, EntityMetadata::new));
    }

    @FunctionalInterface
    private interface SessionCallback<T> {
        T execute(EntityManagerSession session) throws SQLException;
    }
}
