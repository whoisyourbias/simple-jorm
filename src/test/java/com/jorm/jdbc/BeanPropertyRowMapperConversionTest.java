package com.jorm.jdbc;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class BeanPropertyRowMapperConversionTest {
    static class SetterBean {
        private String firstName;
        private int calls;
        public void setFirstName(String name) { firstName = name.trim(); calls++; }
    }

    static class FieldBean { private String firstName; }
    static class SetterOnlyBean {
        private String stored;
        public void setFirstName(String value) { stored = value; }
    }
    static class InheritedBean extends SetterOnlyBean {}
    static class Numbers {
        private Byte byteValue;
        private Short shortValue;
        private Integer intValue;
        private Long longValue;
        private Float floatValue;
        private Double doubleValue;
        private BigDecimal decimalValue;
        private BigInteger integerValue;
    }
    static class BasicTypes {
        private Boolean enabled;
        private Character initial;
        private LocalDate dateValue;
        private LocalTime timeValue;
        private LocalDateTime timestampValue;
    }
    static class NullableBean {
        private Integer boxed = 9;
        private int primitive = 9;
        public void setBoxed(Integer value) { boxed = value; }
        public void setPrimitive(int value) { primitive = value; }
    }
    static class RejectingBean {
        static final IllegalStateException FAILURE = new IllegalStateException("rejected by setter");
        public void setValue(String value) { throw FAILURE; }
    }

    private final JdbcTemplate jdbc = template();

    private static JdbcTemplate template() {
        var source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:mapper_conversion");
        return new JdbcTemplate(source);
    }

    @Test
    void snakeCaseLabelUsesSetterBeforeBackingField() throws Exception {
        var bean = jdbc.queryForObject("SELECT ' Alice ' AS \"FIRST_NAME\"",
                new BeanPropertyRowMapper<>(SetterBean.class));
        assertEquals("Alice", bean.firstName);
        assertEquals(1, bean.calls);
    }

    @Test
    void snakeCaseLabelFallsBackToDeclaredField() throws Exception {
        var bean = jdbc.queryForObject("SELECT 'Alice' AS first_name",
                new BeanPropertyRowMapper<>(FieldBean.class));
        assertEquals("Alice", bean.firstName);
    }

    @Test
    void mapsPublicSetterWithoutMatchingField() throws Exception {
        var bean = jdbc.queryForObject("SELECT 'Alice' AS first_name",
                new BeanPropertyRowMapper<>(SetterOnlyBean.class));
        assertEquals("Alice", bean.stored);
    }

    @Test
    void mapsInheritedPublicSetter() throws Exception {
        var bean = jdbc.queryForObject("SELECT 'Alice' AS first_name",
                new BeanPropertyRowMapper<>(InheritedBean.class));
        assertEquals("Alice", ((SetterOnlyBean) bean).stored);
    }

    @Test
    void convertsExactJdbcNumericValues() throws Exception {
        var bean = jdbc.queryForObject("""
                SELECT CAST(127 AS INTEGER) AS byte_value, CAST(32000 AS BIGINT) AS short_value,
                    CAST(42 AS DECIMAL) AS int_value, CAST(43 AS INTEGER) AS long_value,
                    CAST(1.5 AS DECIMAL(4,1)) AS float_value, CAST(2.5 AS DECIMAL(4,1)) AS double_value,
                    CAST(44 AS INTEGER) AS decimal_value, CAST(45 AS DECIMAL) AS integer_value
                """, new BeanPropertyRowMapper<>(Numbers.class));
        assertAll(() -> assertEquals((byte) 127, bean.byteValue),
                () -> assertEquals((short) 32000, bean.shortValue),
                () -> assertEquals(42, bean.intValue), () -> assertEquals(43L, bean.longValue),
                () -> assertEquals(1.5f, bean.floatValue), () -> assertEquals(2.5d, bean.doubleValue),
                () -> assertEquals(new BigDecimal("44"), bean.decimalValue),
                () -> assertEquals(BigInteger.valueOf(45), bean.integerValue));
    }

    static Stream<String> lossyNumericQueries() {
        return Stream.of("SELECT 128 AS byte_value", "SELECT 32768 AS short_value",
                "SELECT CAST(2147483648 AS BIGINT) AS int_value",
                "SELECT CAST(9223372036854775808 AS DECIMAL(20)) AS long_value",
                "SELECT 1.25 AS int_value", "SELECT 1.25 AS integer_value",
                "SELECT 16777217 AS float_value", "SELECT 9007199254740993 AS double_value",
                "SELECT 0.1 AS double_value", "SELECT 0.1 AS float_value",
                "SELECT CAST('NaN' AS DOUBLE) AS int_value",
                "SELECT CAST('Infinity' AS DOUBLE) AS decimal_value");
    }

    @ParameterizedTest
    @MethodSource("lossyNumericQueries")
    void rejectsNumericOverflowAndLoss(String sql) {
        SQLException failure = assertThrows(SQLException.class,
                () -> jdbc.queryForObject(sql, new BeanPropertyRowMapper<>(Numbers.class)));
        assertNotNull(failure.getCause());
        assertTrue(failure.getMessage().contains("row 0"));
    }

    @Test
    void convertsJdbcDateTimeAndSimpleScalarValues() throws Exception {
        var bean = jdbc.queryForObject("""
                SELECT 1 AS enabled, 'A' AS initial, DATE '2026-11-04' AS date_value,
                    TIME '12:34:56' AS time_value, TIMESTAMP '2026-11-04 12:34:56.123456789' AS timestamp_value
                """, new BeanPropertyRowMapper<>(BasicTypes.class));
        assertAll(() -> assertTrue(bean.enabled), () -> assertEquals('A', bean.initial),
                () -> assertEquals(LocalDate.of(2026, 11, 4), bean.dateValue),
                () -> assertEquals(LocalTime.of(12, 34, 56), bean.timeValue),
                () -> assertEquals(LocalDateTime.of(2026, 11, 4, 12, 34, 56, 123456789), bean.timestampValue));
        var disabled = jdbc.queryForObject("SELECT 0 AS enabled", new BeanPropertyRowMapper<>(BasicTypes.class));
        assertFalse(disabled.enabled);
    }

    @Test
    void rejectsNonBooleanNumberAndMultiCharacterString() {
        assertThrows(SQLException.class, () -> jdbc.queryForObject("SELECT 2 AS enabled",
                new BeanPropertyRowMapper<>(BasicTypes.class)));
        assertThrows(SQLException.class, () -> jdbc.queryForObject("SELECT 'AB' AS initial",
                new BeanPropertyRowMapper<>(BasicTypes.class)));
    }

    @Test
    void passesNullToReferenceSetter() throws Exception {
        var bean = jdbc.queryForObject("SELECT NULL AS boxed", new BeanPropertyRowMapper<>(NullableBean.class));
        assertNull(bean.boxed);
        assertEquals(9, bean.primitive);
    }

    @Test
    void rejectsNullForPrimitiveSetter() {
        assertThrows(SQLException.class, () -> jdbc.queryForObject("SELECT NULL AS primitive",
                new BeanPropertyRowMapper<>(NullableBean.class)));
    }

    @Test
    void preservesActualSetterFailureAsSqlExceptionCause() {
        var failure = assertThrows(SQLException.class, () -> jdbc.queryForObject("SELECT 'x' AS \"value\"",
                new BeanPropertyRowMapper<>(RejectingBean.class)));
        assertSame(RejectingBean.FAILURE, failure.getCause());
    }

    @Test
    void keepsZeroAndMultipleRowQueryForObjectContract() throws Exception {
        var mapper = new BeanPropertyRowMapper<>(FieldBean.class);
        assertNull(jdbc.queryForObject("SELECT 'Alice' AS first_name WHERE FALSE", mapper));
        var bean = jdbc.queryForObject("SELECT 'Alice' AS first_name UNION ALL SELECT 'Bob'", mapper);
        assertEquals("Alice", bean.firstName);
    }
}
