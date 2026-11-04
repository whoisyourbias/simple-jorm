package com.jorm.mapper;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** SQL 코드 영역의 이름 토큰만 JDBC 위치 파라미터로 바꾼다. 값은 SQL에 합치지 않는다. */
final class SqlParameterBinder {
    private static final Pattern NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private SqlParameterBinder() {}

    record BoundSql(String sql, Object[] parameters) {}

    static BoundSql bind(Method method, String sql, Object[] arguments) {
        StringBuilder prepared = new StringBuilder();
        List<String> names = new ArrayList<>();
        boolean positional = false;
        char quote = 0;
        boolean lineComment = false;
        boolean blockComment = false;

        for (int i = 0; i < sql.length(); i++) {
            char current = sql.charAt(i);
            char next = i + 1 < sql.length() ? sql.charAt(i + 1) : 0;
            if (lineComment) {
                prepared.append(current);
                if (current == '\n' || current == '\r') lineComment = false;
            } else if (blockComment) {
                prepared.append(current);
                if (current == '*' && next == '/') {
                    prepared.append(next);
                    i++;
                    blockComment = false;
                }
            } else if (quote != 0) {
                prepared.append(current);
                if (current == quote) {
                    if (next == quote) {
                        prepared.append(next);
                        i++;
                    } else {
                        quote = 0;
                    }
                }
            } else if (current == '\'' || current == '"' || current == '`') {
                quote = current;
                prepared.append(current);
            } else if (current == '-' && next == '-') {
                lineComment = true;
                prepared.append(current).append(next);
                i++;
            } else if (current == '/' && next == '*') {
                blockComment = true;
                prepared.append(current).append(next);
                i++;
            } else if (current == '$' && next == '{') {
                throw invalid(method, "${...} SQL 문자열 치환은 지원하지 않습니다");
            } else if (current == '#' && next == '{') {
                int end = sql.indexOf('}', i + 2);
                if (end < 0) throw invalid(method, "닫히지 않은 이름 토큰");
                String name = sql.substring(i + 2, end);
                if (!NAME.matcher(name).matches()) throw invalid(method, "잘못된 이름 토큰: " + name);
                names.add(name);
                prepared.append('?');
                i = end;
            } else {
                if (current == '?') positional = true;
                prepared.append(current);
            }
        }

        // 이름 토큰이 없는 기존 SQL은 인자 배열을 그대로 사용한다.
        if (names.isEmpty()) return new BoundSql(sql, arguments);
        if (positional) throw invalid(method, "?와 #{name}은 혼용할 수 없습니다");

        Map<String, Object> values = new HashMap<>();
        var parameters = method.getParameters();
        for (int i = 0; i < parameters.length; i++) {
            Param param = parameters[i].getAnnotation(Param.class);
            if (param == null) throw invalid(method, "이름 SQL의 모든 인자에 @Param이 필요합니다");
            String name = param.value();
            if (!NAME.matcher(name).matches()) throw invalid(method, "잘못된 @Param 이름: " + name);
            if (values.containsKey(name)) throw invalid(method, "중복 @Param 이름: " + name);
            values.put(name, arguments[i]);
        }

        Object[] ordered = new Object[names.size()];
        for (int i = 0; i < names.size(); i++) {
            String name = names.get(i);
            // null 값도 유효한 바인딩이므로 get() 결과가 아닌 키 존재 여부를 검사한다.
            if (!values.containsKey(name)) throw invalid(method, "@Param이 없는 이름: " + name);
            ordered[i] = values.get(name);
        }
        return new BoundSql(prepared.toString(), ordered);
    }

    private static IllegalArgumentException invalid(Method method, String reason) {
        return new IllegalArgumentException(reason + ": " + method.getName());
    }
}
