package com.jorm.jdbc;

import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 기본 생성자와 public JavaBean setter를 이용한 자동 행 매핑.
 * setter가 없으면 선언 필드에 대입하며, 이름은 대소문자를 무시하고 snake_case도 허용한다.
 * 조회별 prepare 결과는 컬럼 연결을 재사용한다. 매핑·변환 실패는 SQLException으로 보고한다.
 *
 * @param <T> 매핑할 객체 타입
 */
public final class BeanPropertyRowMapper<T> implements RowMapper<T> {
    private final Constructor<T> constructor;
    private final Map<String, Property> properties;

    public BeanPropertyRowMapper(Class<T> type) {
        try {
            constructor = type.getDeclaredConstructor();
            if (!constructor.trySetAccessible()) {
                throw new IllegalArgumentException("Cannot access default constructor: " + type.getName());
            }
            Map<String, Property> discovered = new HashMap<>();
            for (Field field : type.getDeclaredFields()) {
                if (field.isSynthetic() || Modifier.isStatic(field.getModifiers())
                        || Modifier.isFinal(field.getModifiers())) continue;
                var property = new Property(field.getName(), field.getType(), null, field);
                if (discovered.putIfAbsent(lower(property.name()), property) != null) {
                    throw new IllegalArgumentException("Ambiguous field: " + field.getName());
                }
            }
            // JavaBeans 표준 분석을 사용하므로 상속된 public setter도 포함된다.
            for (var descriptor : Introspector.getBeanInfo(type).getPropertyDescriptors()) {
                Method setter = descriptor.getWriteMethod();
                if (setter != null && Modifier.isPublic(setter.getModifiers())
                        && !Modifier.isStatic(setter.getModifiers())) {
                    discovered.put(lower(descriptor.getName()), new Property(descriptor.getName(),
                            setter.getParameterTypes()[0], setter, null));
                }
            }
            Map<String, Property> names = new HashMap<>();
            for (Property property : discovered.values()) {
                if (!(property.setter() != null ? property.setter().trySetAccessible()
                        : property.field().trySetAccessible())) {
                    throw new IllegalArgumentException("Cannot access property: " + property.name());
                }
                register(names, lower(property.name()), property);
                register(names, underscore(property.name()), property);
            }
            properties = Map.copyOf(names);
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException("Default constructor required: " + type.getName(), e);
        } catch (IntrospectionException e) {
            throw new IllegalArgumentException("Cannot inspect bean: " + type.getName(), e);
        }
    }

    @Override
    public T mapRow(ResultSet rs, int rowNum) throws SQLException {
        return prepare(rs).mapRow(rs, rowNum);
    }

    /** 원본 매퍼의 상태를 바꾸지 않고 현재 조회의 컬럼 순서만 준비한다. */
    @Override
    public RowMapper<T> prepare(ResultSet rs) throws SQLException {
        var metadata = rs.getMetaData();
        Property[] mapping = new Property[metadata.getColumnCount()];
        for (int index = 0; index < mapping.length; index++) {
            String label = metadata.getColumnLabel(index + 1);
            mapping[index] = properties.get(lower(label));
            if (mapping[index] == null) {
                throw new SQLException("No writable property for column: " + label);
            }
        }
        return (row, rowNum) -> mapRow(row, rowNum, mapping);
    }

    private T mapRow(ResultSet rs, int rowNum, Property[] mapping) throws SQLException {
        try {
            T target = constructor.newInstance();
            for (int index = 0; index < mapping.length; index++) {
                Property property = mapping[index];
                Object value = JdbcValueConverter.convert(rs.getObject(index + 1), property.type());
                if (property.setter() != null) property.setter().invoke(target, value);
                else property.field().set(target, value);
            }
            return target;
        } catch (InvocationTargetException e) {
            // setter의 업무 예외를 리플렉션 포장 예외 대신 직접 원인으로 보존한다.
            throw new SQLException("Cannot map row " + rowNum, e.getCause());
        } catch (ReflectiveOperationException | IllegalArgumentException e) {
            throw new SQLException("Cannot map row " + rowNum, e);
        }
    }

    private static void register(Map<String, Property> names, String name, Property property) {
        Property previous = names.putIfAbsent(name, property);
        if (previous != null && previous != property) {
            throw new IllegalArgumentException("Ambiguous property mapping: " + name);
        }
    }

    private static String lower(String name) { return name.toLowerCase(Locale.ROOT); }

    private static String underscore(String name) {
        var result = new StringBuilder();
        for (int index = 0; index < name.length(); index++) {
            char character = name.charAt(index);
            if (index > 0 && Character.isUpperCase(character)) result.append('_');
            result.append(Character.toLowerCase(character));
        }
        return result.toString();
    }

    private record Property(String name, Class<?> type, Method setter, Field field) {}
}
