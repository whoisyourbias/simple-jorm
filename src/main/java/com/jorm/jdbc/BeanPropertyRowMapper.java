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
 * prepare가 반환하는 조회 전용 매퍼는 컬럼 순서에 따른 필드 매핑을 재사용한다.
 *
 * @param <T> 매핑할 객체 타입
 */
public final class BeanPropertyRowMapper<T> implements RowMapper<T> {
    private final Constructor<T> constructor;
    private final Map<String, Field> fields;

    /** 기본 생성자와 쓰기 가능한 필드를 찾아 자동 행 매핑의 메타데이터를 준비한다. */
    public BeanPropertyRowMapper(Class<T> type) {
        try {
            // 행 객체 생성에 사용할 인자 없는 생성자를 찾는다.
            constructor = type.getDeclaredConstructor();
            // 비공개 생성자에도 접근할 수 있는지 확인한다.
            if (!constructor.trySetAccessible()) {
                // 잘못된 선언·입력 또는 실행 상태를 예외로 알린다.
                throw new IllegalArgumentException("Cannot access default constructor: " + type.getName());
            }
            // 필드명을 키로 저장할 임시 필드 목록을 만든다.
            Map<String, Field> discovered = new HashMap<>();
            // 대상 클래스 자체의 선언 필드를 조사한다. 상속 필드는 포함하지 않는다.
            for (Field field : type.getDeclaredFields()) {
                // 컴파일러 생성 필드와 static·final 필드를 제외한다.
                if (field.isSynthetic() || Modifier.isStatic(field.getModifiers())
                        || Modifier.isFinal(field.getModifiers())) {
                    // 현재 필드를 제외하고 다음 필드를 조사한다.
                    continue;
                }
                // 리플렉션으로 필드에 값을 쓸 수 있는지 확인한다.
                if (!field.trySetAccessible()) {
                    // 잘못된 선언·입력 또는 실행 상태를 예외로 알린다.
                    throw new IllegalArgumentException("Cannot access field: " + field.getName());
                }
                // 시스템 로케일과 무관하게 필드명을 소문자로 정규화한다.
                String key = field.getName().toLowerCase(Locale.ROOT);
                // 대소문자를 무시하면 이름이 겹치는 모호한 필드를 거부한다.
                if (discovered.putIfAbsent(key, field) != null) {
                    // 잘못된 선언·입력 또는 실행 상태를 예외로 알린다.
                    throw new IllegalArgumentException("Ambiguous field: " + field.getName());
                }
            }
            // 필드 목록을 변경 불가능한 맵으로 보관한다.
            fields = Map.copyOf(discovered);
            // 기본 생성자가 없는 타입을 자동 매핑 대상으로 거부한다.
        } catch (NoSuchMethodException e) {
            // 잘못된 선언·입력 또는 실행 상태를 예외로 알린다.
            throw new IllegalArgumentException("Default constructor required: " + type.getName(), e);
        }
    }

    /** 현재 행을 Java 객체로 변환한다. 커서 이동은 호출자가 담당하고 rowNum은 0부터 시작한다. */
    @Override
    public T mapRow(ResultSet rs, int rowNum) throws SQLException {
        // 직접 호출도 지원한다. 여러 행을 처리할 때는 prepare의 반환값을 재사용해야
        // 아래의 컬럼 분석이 행마다 반복되지 않는다.
        // 직접 호출도 컬럼 준비 후 현재 행을 변환하도록 연결한다.
        return prepare(rs).mapRow(rs, rowNum);
    }

    /**
     * 조회 결과의 컬럼 순서에 맞춰 필드 배열을 한 번 만들고, 이를 사용하는 매퍼를 반환한다.
     * 원본 매퍼의 상태를 변경하지 않고 조회마다 별도 배열을 만들어 컬럼 순서가 다른
     * 조회끼리 영향을 주지 않게 한다.
     */
    @Override
    public RowMapper<T> prepare(ResultSet rs) throws SQLException {
        // 이번 조회의 컬럼 순서에 맞는 필드 배열을 한 번 만든다.
        Field[] mapping = columnMapping(rs);
        // 람다가 배열을 캡처하므로 각 행에서는 컬럼 라벨 검색 없이 필드에 값을 넣는다.
        // 필드 배열을 캡처해 행마다 컬럼 분석을 반복하지 않는 매퍼를 반환한다.
        return (row, rowNum) -> mapRow(row, rowNum, mapping);
    }

    /** 현재 행을 Java 객체로 변환한다. 커서 이동은 호출자가 담당하고 rowNum은 0부터 시작한다. */
    private T mapRow(ResultSet rs, int rowNum, Field[] mapping) throws SQLException {
        try {
            // 현재 행을 담을 객체를 기본 생성자로 만든다.
            T target = constructor.newInstance();
            // 컬럼 순서의 필드 배열을 처음부터 끝까지 순회한다.
            for (int index = 0; index < mapping.length; index++) {
                // 1부터 시작하는 JDBC 컬럼 값을 읽어 대응 필드에 저장한다.
                mapping[index].set(target, rs.getObject(index + 1));
            }
            // 현재 행의 컬럼 값을 모두 채운 객체를 반환한다.
            return target;
            // 객체 생성·필드 접근·타입 불일치 오류를 매핑 실패로 변환한다.
        } catch (ReflectiveOperationException | IllegalArgumentException e) {
            // 매핑 실패를 JDBC 예외로 보고하고 원인이 있으면 함께 전달한다.
            throw new SQLException("Cannot map row " + rowNum, e);
        }
    }

    /** 컬럼 라벨과 필드명을 대소문자 구분 없이 비교해 컬럼 순서의 Field 배열을 만든다. */
    private Field[] columnMapping(ResultSet rs) throws SQLException {
        // 컬럼 개수와 라벨을 제공하는 메타데이터를 읽는다.
        var metadata = rs.getMetaData();
        // 조회 컬럼 수만큼 Field 배열을 만든다.
        Field[] mapping = new Field[metadata.getColumnCount()];
        // 컬럼 순서의 필드 배열을 처음부터 끝까지 순회한다.
        for (int index = 0; index < mapping.length; index++) {
            // AS 별칭을 포함하는 컬럼 라벨을 읽는다.
            String label = metadata.getColumnLabel(index + 1);
            // 라벨과 이름이 일치하는 쓰기 가능한 필드를 찾는다.
            Field field = fields.get(label.toLowerCase(Locale.ROOT));
            // 대응 필드가 없는 컬럼은 오류로 처리한다.
            if (field == null) {
                // 매핑 실패를 JDBC 예외로 보고하고 원인이 있으면 함께 전달한다.
                throw new SQLException("No writable field for column: " + label);
            }
            // 해당 컬럼 위치에서 사용할 필드를 저장한다.
            mapping[index] = field;
        }
        // 현재 조회의 컬럼 순서에 맞춘 Field 배열을 반환한다.
        return mapping;
    }
}
