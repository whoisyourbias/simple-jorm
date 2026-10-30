package com.jorm.jdbc.lab;

/** Fixed-size beans and explicit mappers for the controlled experiment. */
public final class ScalingRows {
    private ScalingRows() {}
    public interface Row { Object value(int column); }

    public static class Row3 implements Row {
        private Long id;
        private String f2;
        private String f3;
        public Row3() {}
        public Long getId() { return id; }
        public void setId(Long value) { id = value; }
        public String getF2() { return f2; }
        public void setF2(String value) { f2 = value; }
        public String getF3() { return f3; }
        public void setF3(String value) { f3 = value; }
        public Object value(int column) {
            return switch (column) {
                case 1 -> id;
                case 2 -> f2;
                case 3 -> f3;
                default -> throw new IllegalArgumentException("Invalid column " + column);
            };
        }
    }

    public static Row3 manual3(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        var target = new Row3();
        target.setId(rs.getObject("id", Long.class));
        target.setF2(rs.getString("f2"));
        target.setF3(rs.getString("f3"));
        return target;
    }

    public static class Row10 implements Row {
        private Long id;
        private String f2;
        private String f3;
        private String f4;
        private String f5;
        private String f6;
        private String f7;
        private String f8;
        private String f9;
        private String f10;
        public Row10() {}
        public Long getId() { return id; }
        public void setId(Long value) { id = value; }
        public String getF2() { return f2; }
        public void setF2(String value) { f2 = value; }
        public String getF3() { return f3; }
        public void setF3(String value) { f3 = value; }
        public String getF4() { return f4; }
        public void setF4(String value) { f4 = value; }
        public String getF5() { return f5; }
        public void setF5(String value) { f5 = value; }
        public String getF6() { return f6; }
        public void setF6(String value) { f6 = value; }
        public String getF7() { return f7; }
        public void setF7(String value) { f7 = value; }
        public String getF8() { return f8; }
        public void setF8(String value) { f8 = value; }
        public String getF9() { return f9; }
        public void setF9(String value) { f9 = value; }
        public String getF10() { return f10; }
        public void setF10(String value) { f10 = value; }
        public Object value(int column) {
            return switch (column) {
                case 1 -> id;
                case 2 -> f2;
                case 3 -> f3;
                case 4 -> f4;
                case 5 -> f5;
                case 6 -> f6;
                case 7 -> f7;
                case 8 -> f8;
                case 9 -> f9;
                case 10 -> f10;
                default -> throw new IllegalArgumentException("Invalid column " + column);
            };
        }
    }

    public static Row10 manual10(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        var target = new Row10();
        target.setId(rs.getObject("id", Long.class));
        target.setF2(rs.getString("f2"));
        target.setF3(rs.getString("f3"));
        target.setF4(rs.getString("f4"));
        target.setF5(rs.getString("f5"));
        target.setF6(rs.getString("f6"));
        target.setF7(rs.getString("f7"));
        target.setF8(rs.getString("f8"));
        target.setF9(rs.getString("f9"));
        target.setF10(rs.getString("f10"));
        return target;
    }

    public static class Row30 implements Row {
        private Long id;
        private String f2;
        private String f3;
        private String f4;
        private String f5;
        private String f6;
        private String f7;
        private String f8;
        private String f9;
        private String f10;
        private String f11;
        private String f12;
        private String f13;
        private String f14;
        private String f15;
        private String f16;
        private String f17;
        private String f18;
        private String f19;
        private String f20;
        private String f21;
        private String f22;
        private String f23;
        private String f24;
        private String f25;
        private String f26;
        private String f27;
        private String f28;
        private String f29;
        private String f30;
        public Row30() {}
        public Long getId() { return id; }
        public void setId(Long value) { id = value; }
        public String getF2() { return f2; }
        public void setF2(String value) { f2 = value; }
        public String getF3() { return f3; }
        public void setF3(String value) { f3 = value; }
        public String getF4() { return f4; }
        public void setF4(String value) { f4 = value; }
        public String getF5() { return f5; }
        public void setF5(String value) { f5 = value; }
        public String getF6() { return f6; }
        public void setF6(String value) { f6 = value; }
        public String getF7() { return f7; }
        public void setF7(String value) { f7 = value; }
        public String getF8() { return f8; }
        public void setF8(String value) { f8 = value; }
        public String getF9() { return f9; }
        public void setF9(String value) { f9 = value; }
        public String getF10() { return f10; }
        public void setF10(String value) { f10 = value; }
        public String getF11() { return f11; }
        public void setF11(String value) { f11 = value; }
        public String getF12() { return f12; }
        public void setF12(String value) { f12 = value; }
        public String getF13() { return f13; }
        public void setF13(String value) { f13 = value; }
        public String getF14() { return f14; }
        public void setF14(String value) { f14 = value; }
        public String getF15() { return f15; }
        public void setF15(String value) { f15 = value; }
        public String getF16() { return f16; }
        public void setF16(String value) { f16 = value; }
        public String getF17() { return f17; }
        public void setF17(String value) { f17 = value; }
        public String getF18() { return f18; }
        public void setF18(String value) { f18 = value; }
        public String getF19() { return f19; }
        public void setF19(String value) { f19 = value; }
        public String getF20() { return f20; }
        public void setF20(String value) { f20 = value; }
        public String getF21() { return f21; }
        public void setF21(String value) { f21 = value; }
        public String getF22() { return f22; }
        public void setF22(String value) { f22 = value; }
        public String getF23() { return f23; }
        public void setF23(String value) { f23 = value; }
        public String getF24() { return f24; }
        public void setF24(String value) { f24 = value; }
        public String getF25() { return f25; }
        public void setF25(String value) { f25 = value; }
        public String getF26() { return f26; }
        public void setF26(String value) { f26 = value; }
        public String getF27() { return f27; }
        public void setF27(String value) { f27 = value; }
        public String getF28() { return f28; }
        public void setF28(String value) { f28 = value; }
        public String getF29() { return f29; }
        public void setF29(String value) { f29 = value; }
        public String getF30() { return f30; }
        public void setF30(String value) { f30 = value; }
        public Object value(int column) {
            return switch (column) {
                case 1 -> id;
                case 2 -> f2;
                case 3 -> f3;
                case 4 -> f4;
                case 5 -> f5;
                case 6 -> f6;
                case 7 -> f7;
                case 8 -> f8;
                case 9 -> f9;
                case 10 -> f10;
                case 11 -> f11;
                case 12 -> f12;
                case 13 -> f13;
                case 14 -> f14;
                case 15 -> f15;
                case 16 -> f16;
                case 17 -> f17;
                case 18 -> f18;
                case 19 -> f19;
                case 20 -> f20;
                case 21 -> f21;
                case 22 -> f22;
                case 23 -> f23;
                case 24 -> f24;
                case 25 -> f25;
                case 26 -> f26;
                case 27 -> f27;
                case 28 -> f28;
                case 29 -> f29;
                case 30 -> f30;
                default -> throw new IllegalArgumentException("Invalid column " + column);
            };
        }
    }

    public static Row30 manual30(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        var target = new Row30();
        target.setId(rs.getObject("id", Long.class));
        target.setF2(rs.getString("f2"));
        target.setF3(rs.getString("f3"));
        target.setF4(rs.getString("f4"));
        target.setF5(rs.getString("f5"));
        target.setF6(rs.getString("f6"));
        target.setF7(rs.getString("f7"));
        target.setF8(rs.getString("f8"));
        target.setF9(rs.getString("f9"));
        target.setF10(rs.getString("f10"));
        target.setF11(rs.getString("f11"));
        target.setF12(rs.getString("f12"));
        target.setF13(rs.getString("f13"));
        target.setF14(rs.getString("f14"));
        target.setF15(rs.getString("f15"));
        target.setF16(rs.getString("f16"));
        target.setF17(rs.getString("f17"));
        target.setF18(rs.getString("f18"));
        target.setF19(rs.getString("f19"));
        target.setF20(rs.getString("f20"));
        target.setF21(rs.getString("f21"));
        target.setF22(rs.getString("f22"));
        target.setF23(rs.getString("f23"));
        target.setF24(rs.getString("f24"));
        target.setF25(rs.getString("f25"));
        target.setF26(rs.getString("f26"));
        target.setF27(rs.getString("f27"));
        target.setF28(rs.getString("f28"));
        target.setF29(rs.getString("f29"));
        target.setF30(rs.getString("f30"));
        return target;
    }
}

