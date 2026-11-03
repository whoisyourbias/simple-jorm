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

        Object id = readField(metadata.getIdField(), entity);
        if (metadata.isGeneratedId() && id != null) {
            throw new IllegalArgumentException("생성 ID는 지정하지 않아야 합니다.");
        }
        if (!metadata.isGeneratedId() && id == null) {
            throw new IllegalArgumentException("ID를 먼저 지정해야 합니다.");
        }

        // 생성 ID는 DB 기본값을 사용하도록 컬럼과 바인딩 값 양쪽에서 제외한다.
        List<Map.Entry<String, Field>> insertColumns = metadata.getColumns().entrySet().stream()
                .filter(entry -> !metadata.isGeneratedId()
                        || !entry.getValue().equals(metadata.getIdField()))
                .toList();

        // LinkedHashMap의 순서대로 컬럼 이름을 연결한다.
        String columnNames = String.join(
                ", ", insertColumns.stream().map(Map.Entry::getKey).toList()
        );

        // 컬럼 개수만큼 물음표를 만든다.
        String placeholders = String.join(
                ", ",
                Collections.nCopies(insertColumns.size(), "?")
        );

        // 예: INSERT INTO users (id, user_name, email) VALUES (?, ?, ?)
        String sql = "INSERT INTO " + metadata.getTableName()
                + (insertColumns.isEmpty() ? " DEFAULT VALUES"
                : " (" + columnNames + ") VALUES (" + placeholders + ")");

        // SQL 컬럼과 같은 순서로 객체에서 값을 읽는다.
        List<Object> values = new ArrayList<>();

        for (Map.Entry<String, Field> column : insertColumns) {
            values.add(readField(column.getValue(), entity));
        }

        if (metadata.isGeneratedId()) {
            String idColumn = metadata.getColumns().entrySet().stream()
                    .filter(entry -> entry.getValue().equals(metadata.getIdField()))
                    .map(Map.Entry::getKey).findFirst().orElseThrow();
            return jdbcTemplate.updateWithGeneratedKey(sql, idColumn, (keys, row) -> {
                long generated = keys.getLong(1);
                if (keys.wasNull()) {
                    throw new SQLException("생성 ID가 null입니다.");
                }
                try {
                    // Integer 범위를 벗어난 키를 잘라서 저장하지 않는다.
                    Object value;
                    if (metadata.getIdField().getType() == Integer.class) {
                        value = Math.toIntExact(generated);
                    } else {
                        value = generated;
                    }
                    metadata.getIdField().set(entity, value);
                } catch (IllegalAccessException | IllegalArgumentException | ArithmeticException e) {
                    throw new SQLException("생성 ID를 필드에 반영할 수 없습니다.", e);
                }
                return null;
            }, values.toArray());
        }

        // 값은 SQL 문자열에 합치지 않고 파라미터로 바인딩한다.
        return jdbcTemplate.update(sql, values.toArray());
    }

    /**
     * ID에 일치하는 행을 새 엔티티 객체로 변환한다.
     * 결과가 없으면 null을 반환한다. 기본 생성자가 필요하며 엔티티 인스턴스는 캐시하지 않는다.
     */
    public <T> T find(Class<T> entityType, Object id) throws SQLException {
        // null을 SQL의 비교값으로 보내지 않고 잘못된 ID 입력으로 거부한다.
        if (id == null) {
            throw new IllegalArgumentException("조회할 ID가 필요합니다.");
        }
        // persist와 같은 클래스별 매핑 정보를 사용한다.
        EntityMetadata metadata = getMetadata(entityType);
        // 행이 없더라도 조회할 수 없는 타입은 일관되게 거부하도록 SQL 실행 전에 확인한다.
        Constructor<T> constructor = defaultConstructor(entityType);

        // @Id 필드명 대신 해당 필드에 연결된 실제 DB 컬럼명을 찾는다.
        // 예: Java 필드가 id이고 @Column("user_id")이면 WHERE에는 user_id를 사용한다.
        String idColumn = metadata.getColumns().entrySet().stream()
                .filter(entry -> entry.getValue().equals(metadata.getIdField()))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("ID 컬럼 매핑이 없습니다."));

        // SELECT 컬럼과 필드 목록을 같은 순서로 구성해 위치별 값이 서로 대응하게 한다.
        String columnNames = String.join(", ", metadata.getColumns().keySet());
        List<Field> fields = new ArrayList<>(metadata.getColumns().values());
        String sql = "SELECT " + columnNames + " FROM " + metadata.getTableName()
                + " WHERE " + idColumn + " = ?";

        // JdbcTemplate이 현재 트랜잭션 연결을 사용하고 ID를 물음표에 바인딩한다.
        // 빈 결과에서는 아래 매퍼를 호출하지 않고 null을 반환한다.
        return jdbcTemplate.queryForObject(sql, (rs, rowNum) -> {
            try {
                // 매 조회마다 별도의 객체를 생성한다. JPA의 영속성 컨텍스트는 아직 없다.
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
