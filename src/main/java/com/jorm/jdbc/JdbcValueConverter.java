package com.jorm.jdbc;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;

/** 자동 행 매핑에서 사용하는 값 손실 없는 작은 JDBC 타입 변환기. */
final class JdbcValueConverter {
    private static final Map<Class<?>, Class<?>> BOXED = Map.of(
            byte.class, Byte.class, short.class, Short.class, int.class, Integer.class,
            long.class, Long.class, float.class, Float.class, double.class, Double.class,
            boolean.class, Boolean.class, char.class, Character.class);

    private JdbcValueConverter() {}

    static Object convert(Object value, Class<?> type) {
        if (value == null) {
            if (type.isPrimitive()) throw new IllegalArgumentException("NULL cannot be assigned to " + type.getName());
            return null;
        }
        Class<?> target = BOXED.getOrDefault(type, type);
        if (target.isInstance(value)) return value;
        try {
            if (value instanceof Number number) {
                BigDecimal decimal = decimal(number);
                if (target == Byte.class) return decimal.byteValueExact();
                if (target == Short.class) return decimal.shortValueExact();
                if (target == Integer.class) return decimal.intValueExact();
                if (target == Long.class) return decimal.longValueExact();
                if (target == BigInteger.class) return decimal.toBigIntegerExact();
                if (target == BigDecimal.class) return decimal;
                if (target == Float.class) {
                    float converted = decimal.floatValue();
                    if (Float.isFinite(converted) && new BigDecimal((double) converted).compareTo(decimal) == 0) {
                        return converted;
                    }
                }
                if (target == Double.class) {
                    double converted = decimal.doubleValue();
                    if (Double.isFinite(converted) && new BigDecimal(converted).compareTo(decimal) == 0) {
                        return converted;
                    }
                }
                if (target == Boolean.class) {
                    if (decimal.compareTo(BigDecimal.ZERO) == 0) return false;
                    if (decimal.compareTo(BigDecimal.ONE) == 0) return true;
                }
            }
            if (target == Character.class && value instanceof String text && text.length() == 1) return text.charAt(0);
            if (target == LocalDate.class && value instanceof java.sql.Date date) return date.toLocalDate();
            if (target == LocalTime.class && value instanceof java.sql.Time time) return time.toLocalTime();
            if (target == LocalDateTime.class && value instanceof java.sql.Timestamp timestamp) return timestamp.toLocalDateTime();
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("Numeric value cannot be represented exactly as " + type.getName(), e);
        }
        throw new IllegalArgumentException("Cannot convert " + value.getClass().getName() + " to " + type.getName());
    }

    private static BigDecimal decimal(Number number) {
        if (number instanceof BigDecimal value) return value;
        if (number instanceof BigInteger value) return new BigDecimal(value);
        if (number instanceof Byte || number instanceof Short || number instanceof Integer || number instanceof Long) {
            return BigDecimal.valueOf(number.longValue());
        }
        // 실제 이진 부동소수점 값을 사용하여 정밀도 손실을 숨기지 않는다.
        if (number instanceof Float || number instanceof Double) return new BigDecimal(number.doubleValue());
        throw new IllegalArgumentException("Unsupported numeric type: " + number.getClass().getName());
    }
}
