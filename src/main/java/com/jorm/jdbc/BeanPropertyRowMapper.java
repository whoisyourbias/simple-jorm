package com.jorm.jdbc;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 기본 생성자와 필드를 이용해 행을 매핑한다. 컬럼 라벨과 필드명은 대소문자를
 * 무시하고 비교한다. 조회하지 않은 필드는 기본값을 유지하며, 대응 필드가 없는
 * 컬럼이나 호환되지 않는 값은 SQLException으로 보고한다.
 * 상속 필드, final 필드, record 및 별도의 타입 변환은 지원하지 않는다.
 *
 * @param <T> 매핑할 객체 타입
 */
public final class BeanPropertyRowMapper<T> implements RowMapper<T> {
    private final Constructor<T> constructor;
    private final Map<String, Field> fields;

    public BeanPropertyRowMapper(Class<T> type) {
        try {
            constructor = type.getDeclaredConstructor();
            if (!constructor.trySetAccessible()) {
                throw new IllegalArgumentException("Cannot access default constructor: " + type.getName());
            }
            Map<String, Field> discovered = new HashMap<>();
            for (Field field : type.getDeclaredFields()) {
                if (field.isSynthetic() || Modifier.isStatic(field.getModifiers())
                        || Modifier.isFinal(field.getModifiers())) {
                    continue;
                }
                if (!field.trySetAccessible()) {
                    throw new IllegalArgumentException("Cannot access field: " + field.getName());
                }
                String key = field.getName().toLowerCase(Locale.ROOT);
                if (discovered.putIfAbsent(key, field) != null) {
                    throw new IllegalArgumentException("Ambiguous field: " + field.getName());
                }
            }
            fields = Map.copyOf(discovered);
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException("Default constructor required: " + type.getName(), e);
        }
    }

    @Override
    public T mapRow(ResultSet rs, int rowNum) throws SQLException {
        try {
            T target = constructor.newInstance();
            var metadata = rs.getMetaData();
            for (int column = 1; column <= metadata.getColumnCount(); column++) {
                String label = metadata.getColumnLabel(column);
                Field field = fields.get(label.toLowerCase(Locale.ROOT));
                if (field == null) {
                    throw new SQLException("No writable field for column: " + label);
                }
                field.set(target, rs.getObject(column));
            }
            return target;
        } catch (ReflectiveOperationException | IllegalArgumentException e) {
            throw new SQLException("Cannot map row " + rowNum, e);
        }
    }
}
