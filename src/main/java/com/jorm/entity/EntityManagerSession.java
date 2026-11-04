package com.jorm.entity;

import com.jorm.jdbc.JdbcTemplate;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

final class EntityManagerSession implements AutoCloseable {

    private final JdbcTemplate jdbcTemplate;

    private final Function<Class<?>, EntityMetadata> metadataLookup;
    private final PersistenceContext context = new PersistenceContext();
    private boolean closed;

    EntityManagerSession(JdbcTemplate jdbcTemplate, Function<Class<?>, EntityMetadata> metadataLookup) {
        this.jdbcTemplate = jdbcTemplate;
        this.metadataLookup = metadataLookup;
    }

    @Override
    public void close() {
        context.clear();
        closed = true;
    }

    private void checkOpen() {
        if (closed) {
            throw new IllegalStateException("종료한 EntityManager 세션입니다.");
        }
    }

    public int persist(Object entity) throws SQLException {
        checkOpen();
        EntityMetadata metadata = getMetadata(entity.getClass());
        context.validateId(entity, metadata);
        if (!metadata.isGeneratedId()) {
            context.requireUnmanagedKey(entity, metadata);
        }
        try {
            int rows = insert(entity, metadata);
            if (rows > 0) {
                try {
                    Object managed = context.manage(entity, metadata);
                    if (managed != entity) {
                        throw new SQLException("생성 ID에 이미 다른 관리 객체가 있습니다.");
                    }
                } catch (RuntimeException registrationFailure) {
                    throw new SQLException("INSERT 후 관리 객체 등록에 실패했습니다.", registrationFailure);
                }
            }
            return rows;
        } catch (SQLException failure) {
            jdbcTemplate.getTransactionManager().markRollbackOnly(failure);
            throw failure;
        }
    }

    /** 객체의 필드 값으로 INSERT를 생성하고 실행한다. */
    private int insert(Object entity, EntityMetadata metadata) throws SQLException {
        Object id = readField(metadata.getIdField(), entity);
        boolean generated = metadata.isGeneratedId();
        if (generated && id != null) {
            throw new IllegalArgumentException("생성 ID는 저장 전에 null이어야 합니다.");
        }
        if (!generated && id == null) {
            throw new IllegalArgumentException("ID를 먼저 지정해야 합니다.");
        }

        GenerationType strategy = metadata.getGenerationType();
        boolean identity = strategy == GenerationType.IDENTITY;
        if (strategy == GenerationType.UUID) {
            // UUID는 DB가 만들지 않는다. INSERT 전에 객체에 넣고 일반 컬럼처럼 바인딩한다.
            UUID uuid = UUID.randomUUID();
            writeId(metadata, entity, metadata.getIdField().getType() == String.class
                    ? uuid.toString() : uuid);
        }

        // IDENTITY만 ID를 제외한다. UUID·직접 지정 ID는 컬럼과 값을 함께 수집한다.
        List<String> columns = new ArrayList<>();
        List<Object> values = new ArrayList<>();
        for (Map.Entry<String, Field> column : metadata.getColumns().entrySet()) {
            if (identity && column.getValue().equals(metadata.getIdField())) {
                continue;
            }
            columns.add(column.getKey());
            values.add(readField(column.getValue(), entity));
        }
        String sql = "INSERT INTO " + metadata.getTableName();
        if (columns.isEmpty()) {
            // ID만 선언한 엔티티도 DB의 기본값으로 한 행을 생성한다.
            sql += " DEFAULT VALUES";
        } else {
            sql += " (" + String.join(", ", columns) + ") VALUES ("
                    + String.join(", ", Collections.nCopies(columns.size(), "?")) + ")";
        }
        if (!identity) {
            return jdbcTemplate.update(sql, values.toArray());
        }

        // 키 변환과 객체 반영까지 같은 JDBC 연결의 콜백 안에서 수행한다.
        return jdbcTemplate.updateWithGeneratedKey(sql, metadata.getIdColumnName(), (keys, row) -> {
            long key = keys.getLong(1);
            if (keys.wasNull()) {
                throw new SQLException("생성 ID가 null입니다.");
            }
            Object value;
            try {
                if (metadata.getIdField().getType() == Integer.class) {
                    value = Math.toIntExact(key);
                } else {
                    value = key;
                }
            } catch (ArithmeticException e) {
                throw new SQLException("생성 ID가 Integer 범위를 벗어났습니다.", e);
            }
            writeId(metadata, entity, value);
            return null;
        }, values.toArray());
    }

    /** ID 외 모든 매핑 필드를 즉시 수정하고 변경 행 수를 반환한다. 대상이 없으면 0이다. */
    public int update(Object entity) throws SQLException {
        checkOpen();
        if (entity == null) {
            throw new IllegalArgumentException("수정할 엔티티가 필요합니다.");
        }
        EntityMetadata metadata = getMetadata(entity.getClass());
        Object id = requiredId(metadata, entity);
        context.validateId(entity, metadata);
        context.requireSameInstance(entity, metadata);
        List<String> assignments = new ArrayList<>();
        List<Object> values = new ArrayList<>();
        for (Map.Entry<String, Field> column : metadata.getColumns().entrySet()) {
            // ID는 SET에 넣지 않고 마지막 WHERE 바인딩에만 사용한다.
            if (column.getValue().equals(metadata.getIdField())) {
                continue;
            }
            assignments.add(column.getKey() + " = ?");
            values.add(readField(column.getValue(), entity));
        }
        if (assignments.isEmpty()) {
            throw new IllegalArgumentException("수정할 ID 외 필드가 필요합니다.");
        }
        values.add(id);
        String sql = "UPDATE " + metadata.getTableName()
                + " SET " + String.join(", ", assignments)
                + " WHERE " + metadata.getIdColumnName() + " = ?";
        try {
            int rows = jdbcTemplate.update(sql, values.toArray());
            if (rows == 0) {
                context.remove(entity, metadata);
            } else {
                context.refreshSnapshot(entity, metadata);
            }
            return rows;
        } catch (SQLException failure) {
            jdbcTemplate.getTransactionManager().markRollbackOnly(failure);
            throw failure;
        }
    }

    /** 같은 트랜잭션의 관리 객체 변경만 동기화하며 commit하지 않는다. */
    public void flush() throws SQLException {
        checkOpen();
        try {
            List<Object> managed = context.managedEntities();
            // 잘못된 ID로 일부 UPDATE가 먼저 실행되지 않도록 전체를 먼저 검사한다.
            for (Object entity : managed) {
                context.validateId(entity, getMetadata(entity.getClass()));
            }
            for (Object entity : managed) {
                EntityMetadata metadata = getMetadata(entity.getClass());
                if (context.isDirty(entity, metadata)) {
                    update(entity);
                }
            }
        } catch (SQLException failure) {
            jdbcTemplate.getTransactionManager().markRollbackOnly(failure);
            throw failure;
        }
    }

    /** ID 조건으로 즉시 삭제하고 변경 행 수를 반환한다. 객체의 필드 값은 유지한다. */
    public int remove(Object entity) throws SQLException {
        checkOpen();
        if (entity == null) {
            throw new IllegalArgumentException("삭제할 엔티티가 필요합니다.");
        }
        EntityMetadata metadata = getMetadata(entity.getClass());
        Object id = requiredId(metadata, entity);
        context.validateId(entity, metadata);
        String sql = "DELETE FROM " + metadata.getTableName()
                + " WHERE " + metadata.getIdColumnName() + " = ?";
        try {
            int rows = jdbcTemplate.update(sql, id);
            context.remove(entity, metadata);
            return rows;
        } catch (SQLException failure) {
            jdbcTemplate.getTransactionManager().markRollbackOnly(failure);
            throw failure;
        }
    }

    /** 변경·삭제는 생성 전략에 관계없이 이미 지정된 ID를 조건으로 사용한다. */
    private Object requiredId(EntityMetadata metadata, Object entity) throws SQLException {
        Object id = readField(metadata.getIdField(), entity);
        if (id == null) {
            throw new IllegalArgumentException("수정·삭제할 ID가 필요합니다.");
        }
        return id;
    }

    /**
     * 관리 객체를 우선 반환하고, 없으면 ID 조회 결과를 컨텍스트에 등록한다.
     * 결과가 없으면 null을 반환한다. 조회 타입은 기본 생성자가 필요하다.
     */
    public <T> T find(Class<T> entityType, Object id) throws SQLException {
        checkOpen();
        // null을 SQL의 비교값으로 보내지 않고 잘못된 ID 입력으로 거부한다.
        if (id == null) {
            throw new IllegalArgumentException("조회할 ID가 필요합니다.");
        }
        // persist와 같은 클래스별 매핑 정보를 사용한다.
        EntityMetadata metadata = getMetadata(entityType);
        // 행이 없더라도 조회할 수 없는 타입은 일관되게 거부하도록 SQL 실행 전에 확인한다.
        Constructor<T> constructor = defaultConstructor(entityType);
        T managed = context.find(entityType, id, metadata);
        if (managed != null) {
            return managed;
        }

        // @Id 필드명 대신 해당 필드에 연결된 실제 DB 컬럼명을 찾는다.
        // 예: Java 필드가 id이고 @Column("user_id")이면 WHERE에는 user_id를 사용한다.
        String idColumn = metadata.getIdColumnName();

        // SELECT 컬럼과 필드 목록을 같은 순서로 구성해 위치별 값이 서로 대응하게 한다.
        String columnNames = String.join(", ", metadata.getColumns().keySet());
        List<Field> fields = new ArrayList<>(metadata.getColumns().values());
        String sql = "SELECT " + columnNames + " FROM " + metadata.getTableName()
                + " WHERE " + idColumn + " = ?";

        // JdbcTemplate이 현재 트랜잭션 연결을 사용하고 ID를 물음표에 바인딩한다.
        // 빈 결과에서는 아래 매퍼를 호출하지 않고 null을 반환한다.
        T loaded = jdbcTemplate.queryForObject(sql, (rs, rowNum) -> {
            try {
                // SQL 결과를 객체로 만든 뒤 실제 ID를 기준으로 관리 객체를 결정한다.
                T entity = constructor.newInstance();
                for (int index = 0; index < fields.size(); index++) {
                    // JDBC 컬럼 위치는 1부터 시작한다. 별도 타입 변환 없이 필드에 직접 저장한다.
                    fields.get(index).set(entity, rs.getObject(index + 1));
                }
                return entity;
            } catch (ReflectiveOperationException | IllegalArgumentException e) {
                // 생성자 실행·필드 접근·타입 불일치의 원인을 JDBC 매핑 실패로 보존한다.
                throw new SQLException("엔티티를 매핑할 수 없습니다: " + entityType.getName(), e);
            }
        }, id);
        return loaded == null ? null : context.manage(loaded, metadata);
    }

    /** find에서만 기본 생성자를 요구해 기본 생성자 없는 객체의 persist는 계속 허용한다. */
    private <T> Constructor<T> defaultConstructor(Class<T> entityType) {
        // 추상 클래스는 생성자가 있어도 직접 인스턴스를 만들 수 없다.
        if (Modifier.isAbstract(entityType.getModifiers())) {
            throw new IllegalArgumentException("조회할 타입은 구체적인 클래스여야 합니다.");
        }
        try {
            // public뿐 아니라 private 기본 생성자도 찾고 접근 권한을 확보한다.
            Constructor<T> constructor = entityType.getDeclaredConstructor();
            if (!constructor.trySetAccessible()) {
                throw new IllegalArgumentException("기본 생성자에 접근할 수 없습니다: " + entityType.getName());
            }
            return constructor;
        } catch (NoSuchMethodException e) {
            // 매핑할 객체를 새로 만드는 방법이 없으므로 SQL 실행 전에 거부한다.
            throw new IllegalArgumentException("조회하려면 기본 생성자가 필요합니다: " + entityType.getName(), e);
        }
    }

    private EntityMetadata getMetadata(Class<?> entityType) {
        return metadataLookup.apply(entityType);
    }

    /** 생성 시점이 다른 IDENTITY·UUID 모두 검증된 ID 필드에 값을 기록한다. */
    private void writeId(EntityMetadata metadata, Object entity, Object id) throws SQLException {
        try {
            metadata.getIdField().set(entity, id);
        } catch (IllegalAccessException | IllegalArgumentException e) {
            throw new SQLException("생성 ID를 엔티티에 반영할 수 없습니다.", e);
        }
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
