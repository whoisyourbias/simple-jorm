package com.jorm.jdbc.lab;

/** Fixed-size beans and explicit mappers for the controlled experiment. */
public final class ScalingRows {
    /** 실험용 타입과 정적 매핑 함수만 제공하므로 인스턴스 생성을 막는다. */
    private ScalingRows() {}
    /** 1부터 시작하는 컬럼 번호에 대응하는 필드 값을 반환하고 범위 밖 번호는 거부한다. */
    public interface Row { Object value(int column); }

    public static class Row3 implements Row {
        private Long id;
        private String f2;
        private String f3;
        /** 자동 매핑 라이브러리가 호출하는 인자 없는 기본 생성자다. */
        public Row3() {}
        /** Id 필드 값을 반환하는 JavaBean 접근자다. */
        public Long getId() { return id; }
        /** 전달된 값을 Id 필드에 저장하는 JavaBean 설정자다. */
        public void setId(Long value) { id = value; }
        /** F2 필드 값을 반환하는 JavaBean 접근자다. */
        public String getF2() { return f2; }
        /** 전달된 값을 F2 필드에 저장하는 JavaBean 설정자다. */
        public void setF2(String value) { f2 = value; }
        /** F3 필드 값을 반환하는 JavaBean 접근자다. */
        public String getF3() { return f3; }
        /** 전달된 값을 F3 필드에 저장하는 JavaBean 설정자다. */
        public void setF3(String value) { f3 = value; }
        /** 1부터 시작하는 컬럼 번호에 대응하는 필드 값을 반환하고 범위 밖 번호는 거부한다. */
        public Object value(int column) {
            // 1부터 시작하는 컬럼 번호에 대응하는 값을 선택한다.
            return switch (column) {
                // 이 컬럼 번호에 해당하는 필드 값을 선택한다.
                case 1 -> id;
                // 이 컬럼 번호에 해당하는 필드 값을 선택한다.
                case 2 -> f2;
                // 이 컬럼 번호에 해당하는 필드 값을 선택한다.
                case 3 -> f3;
                // 지원하지 않는 메서드 또는 컬럼 번호를 예외로 알린다.
                default -> throw new IllegalArgumentException("Invalid column " + column);
            };
        }
    }

    /** 현재 행의 각 컬럼을 이름으로 읽어 대응하는 JavaBean 필드에 저장한다. */
    public static Row3 manual3(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        // 현재 행의 모든 컬럼 값을 담을 지정 크기의 JavaBean 객체를 만든다.
        var target = new Row3();
        // 이름이 대응하는 컬럼 값을 읽어 결과 객체의 필드에 저장한다.
        target.setId(rs.getObject("id", Long.class));
        // 이름이 대응하는 컬럼 값을 읽어 결과 객체의 필드에 저장한다.
        target.setF2(rs.getString("f2"));
        // 이름이 대응하는 컬럼 값을 읽어 결과 객체의 필드에 저장한다.
        target.setF3(rs.getString("f3"));
        // 현재 행의 컬럼 값을 모두 채운 객체를 반환한다.
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
        /** 자동 매핑 라이브러리가 호출하는 인자 없는 기본 생성자다. */
        public Row10() {}
        /** Id 필드 값을 반환하는 JavaBean 접근자다. */
        public Long getId() { return id; }
        /** 전달된 값을 Id 필드에 저장하는 JavaBean 설정자다. */
        public void setId(Long value) { id = value; }
        /** F2 필드 값을 반환하는 JavaBean 접근자다. */
        public String getF2() { return f2; }
        /** 전달된 값을 F2 필드에 저장하는 JavaBean 설정자다. */
        public void setF2(String value) { f2 = value; }
        /** F3 필드 값을 반환하는 JavaBean 접근자다. */
        public String getF3() { return f3; }
        /** 전달된 값을 F3 필드에 저장하는 JavaBean 설정자다. */
        public void setF3(String value) { f3 = value; }
        /** F4 필드 값을 반환하는 JavaBean 접근자다. */
        public String getF4() { return f4; }
        /** 전달된 값을 F4 필드에 저장하는 JavaBean 설정자다. */
        public void setF4(String value) { f4 = value; }
        /** F5 필드 값을 반환하는 JavaBean 접근자다. */
        public String getF5() { return f5; }
        /** 전달된 값을 F5 필드에 저장하는 JavaBean 설정자다. */
        public void setF5(String value) { f5 = value; }
        /** F6 필드 값을 반환하는 JavaBean 접근자다. */
        public String getF6() { return f6; }
        /** 전달된 값을 F6 필드에 저장하는 JavaBean 설정자다. */
        public void setF6(String value) { f6 = value; }
        /** F7 필드 값을 반환하는 JavaBean 접근자다. */
        public String getF7() { return f7; }
        /** 전달된 값을 F7 필드에 저장하는 JavaBean 설정자다. */
        public void setF7(String value) { f7 = value; }
        /** F8 필드 값을 반환하는 JavaBean 접근자다. */
        public String getF8() { return f8; }
        /** 전달된 값을 F8 필드에 저장하는 JavaBean 설정자다. */
        public void setF8(String value) { f8 = value; }
        /** F9 필드 값을 반환하는 JavaBean 접근자다. */
        public String getF9() { return f9; }
        /** 전달된 값을 F9 필드에 저장하는 JavaBean 설정자다. */
        public void setF9(String value) { f9 = value; }
        /** F10 필드 값을 반환하는 JavaBean 접근자다. */
        public String getF10() { return f10; }
        /** 전달된 값을 F10 필드에 저장하는 JavaBean 설정자다. */
        public void setF10(String value) { f10 = value; }
        /** 1부터 시작하는 컬럼 번호에 대응하는 필드 값을 반환하고 범위 밖 번호는 거부한다. */
        public Object value(int column) {
            // 1부터 시작하는 컬럼 번호에 대응하는 값을 선택한다.
            return switch (column) {
                // 이 컬럼 번호에 해당하는 필드 값을 선택한다.
                case 1 -> id;
                // 이 컬럼 번호에 해당하는 필드 값을 선택한다.
                case 2 -> f2;
                // 이 컬럼 번호에 해당하는 필드 값을 선택한다.
                case 3 -> f3;
                // 이 컬럼 번호에 해당하는 필드 값을 선택한다.
                case 4 -> f4;
                // 이 컬럼 번호에 해당하는 필드 값을 선택한다.
                case 5 -> f5;
                // 이 컬럼 번호에 해당하는 필드 값을 선택한다.
                case 6 -> f6;
                // 이 컬럼 번호에 해당하는 필드 값을 선택한다.
                case 7 -> f7;
                // 이 컬럼 번호에 해당하는 필드 값을 선택한다.
                case 8 -> f8;
                // 이 컬럼 번호에 해당하는 필드 값을 선택한다.
                case 9 -> f9;
                // 이 컬럼 번호에 해당하는 필드 값을 선택한다.
                case 10 -> f10;
                // 지원하지 않는 메서드 또는 컬럼 번호를 예외로 알린다.
                default -> throw new IllegalArgumentException("Invalid column " + column);
            };
        }
    }

    /** 현재 행의 각 컬럼을 이름으로 읽어 대응하는 JavaBean 필드에 저장한다. */
    public static Row10 manual10(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        // 현재 행의 모든 컬럼 값을 담을 지정 크기의 JavaBean 객체를 만든다.
        var target = new Row10();
        // 이름이 대응하는 컬럼 값을 읽어 결과 객체의 필드에 저장한다.
        target.setId(rs.getObject("id", Long.class));
        // 이름이 대응하는 컬럼 값을 읽어 결과 객체의 필드에 저장한다.
        target.setF2(rs.getString("f2"));
        // 이름이 대응하는 컬럼 값을 읽어 결과 객체의 필드에 저장한다.
        target.setF3(rs.getString("f3"));
        // 이름이 대응하는 컬럼 값을 읽어 결과 객체의 필드에 저장한다.
        target.setF4(rs.getString("f4"));
        // 이름이 대응하는 컬럼 값을 읽어 결과 객체의 필드에 저장한다.
        target.setF5(rs.getString("f5"));
        // 이름이 대응하는 컬럼 값을 읽어 결과 객체의 필드에 저장한다.
        target.setF6(rs.getString("f6"));
        // 이름이 대응하는 컬럼 값을 읽어 결과 객체의 필드에 저장한다.
        target.setF7(rs.getString("f7"));
        // 이름이 대응하는 컬럼 값을 읽어 결과 객체의 필드에 저장한다.
        target.setF8(rs.getString("f8"));
        // 이름이 대응하는 컬럼 값을 읽어 결과 객체의 필드에 저장한다.
        target.setF9(rs.getString("f9"));
        // 이름이 대응하는 컬럼 값을 읽어 결과 객체의 필드에 저장한다.
        target.setF10(rs.getString("f10"));
        // 현재 행의 컬럼 값을 모두 채운 객체를 반환한다.
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
        /** 자동 매핑 라이브러리가 호출하는 인자 없는 기본 생성자다. */
        public Row30() {}
        /** Id 필드 값을 반환하는 JavaBean 접근자다. */
        public Long getId() { return id; }
        /** 전달된 값을 Id 필드에 저장하는 JavaBean 설정자다. */
        public void setId(Long value) { id = value; }
        /** F2 필드 값을 반환하는 JavaBean 접근자다. */
        public String getF2() { return f2; }
        /** 전달된 값을 F2 필드에 저장하는 JavaBean 설정자다. */
        public void setF2(String value) { f2 = value; }
        /** F3 필드 값을 반환하는 JavaBean 접근자다. */
        public String getF3() { return f3; }
        /** 전달된 값을 F3 필드에 저장하는 JavaBean 설정자다. */
        public void setF3(String value) { f3 = value; }
        /** F4 필드 값을 반환하는 JavaBean 접근자다. */
        public String getF4() { return f4; }
        /** 전달된 값을 F4 필드에 저장하는 JavaBean 설정자다. */
        public void setF4(String value) { f4 = value; }
        /** F5 필드 값을 반환하는 JavaBean 접근자다. */
        public String getF5() { return f5; }
        /** 전달된 값을 F5 필드에 저장하는 JavaBean 설정자다. */
        public void setF5(String value) { f5 = value; }
        /** F6 필드 값을 반환하는 JavaBean 접근자다. */
        public String getF6() { return f6; }
        /** 전달된 값을 F6 필드에 저장하는 JavaBean 설정자다. */
        public void setF6(String value) { f6 = value; }
        /** F7 필드 값을 반환하는 JavaBean 접근자다. */
        public String getF7() { return f7; }
        /** 전달된 값을 F7 필드에 저장하는 JavaBean 설정자다. */
        public void setF7(String value) { f7 = value; }
        /** F8 필드 값을 반환하는 JavaBean 접근자다. */
        public String getF8() { return f8; }
        /** 전달된 값을 F8 필드에 저장하는 JavaBean 설정자다. */
        public void setF8(String value) { f8 = value; }
        /** F9 필드 값을 반환하는 JavaBean 접근자다. */
        public String getF9() { return f9; }
        /** 전달된 값을 F9 필드에 저장하는 JavaBean 설정자다. */
        public void setF9(String value) { f9 = value; }
        /** F10 필드 값을 반환하는 JavaBean 접근자다. */
        public String getF10() { return f10; }
        /** 전달된 값을 F10 필드에 저장하는 JavaBean 설정자다. */
        public void setF10(String value) { f10 = value; }
        /** F11 필드 값을 반환하는 JavaBean 접근자다. */
        public String getF11() { return f11; }
        /** 전달된 값을 F11 필드에 저장하는 JavaBean 설정자다. */
        public void setF11(String value) { f11 = value; }
        /** F12 필드 값을 반환하는 JavaBean 접근자다. */
        public String getF12() { return f12; }
        /** 전달된 값을 F12 필드에 저장하는 JavaBean 설정자다. */
        public void setF12(String value) { f12 = value; }
        /** F13 필드 값을 반환하는 JavaBean 접근자다. */
        public String getF13() { return f13; }
        /** 전달된 값을 F13 필드에 저장하는 JavaBean 설정자다. */
        public void setF13(String value) { f13 = value; }
        /** F14 필드 값을 반환하는 JavaBean 접근자다. */
        public String getF14() { return f14; }
        /** 전달된 값을 F14 필드에 저장하는 JavaBean 설정자다. */
        public void setF14(String value) { f14 = value; }
        /** F15 필드 값을 반환하는 JavaBean 접근자다. */
        public String getF15() { return f15; }
        /** 전달된 값을 F15 필드에 저장하는 JavaBean 설정자다. */
        public void setF15(String value) { f15 = value; }
        /** F16 필드 값을 반환하는 JavaBean 접근자다. */
        public String getF16() { return f16; }
        /** 전달된 값을 F16 필드에 저장하는 JavaBean 설정자다. */
        public void setF16(String value) { f16 = value; }
        /** F17 필드 값을 반환하는 JavaBean 접근자다. */
        public String getF17() { return f17; }
        /** 전달된 값을 F17 필드에 저장하는 JavaBean 설정자다. */
        public void setF17(String value) { f17 = value; }
        /** F18 필드 값을 반환하는 JavaBean 접근자다. */
        public String getF18() { return f18; }
        /** 전달된 값을 F18 필드에 저장하는 JavaBean 설정자다. */
        public void setF18(String value) { f18 = value; }
        /** F19 필드 값을 반환하는 JavaBean 접근자다. */
        public String getF19() { return f19; }
        /** 전달된 값을 F19 필드에 저장하는 JavaBean 설정자다. */
        public void setF19(String value) { f19 = value; }
        /** F20 필드 값을 반환하는 JavaBean 접근자다. */
        public String getF20() { return f20; }
        /** 전달된 값을 F20 필드에 저장하는 JavaBean 설정자다. */
        public void setF20(String value) { f20 = value; }
        /** F21 필드 값을 반환하는 JavaBean 접근자다. */
        public String getF21() { return f21; }
        /** 전달된 값을 F21 필드에 저장하는 JavaBean 설정자다. */
        public void setF21(String value) { f21 = value; }
        /** F22 필드 값을 반환하는 JavaBean 접근자다. */
        public String getF22() { return f22; }
        /** 전달된 값을 F22 필드에 저장하는 JavaBean 설정자다. */
        public void setF22(String value) { f22 = value; }
        /** F23 필드 값을 반환하는 JavaBean 접근자다. */
        public String getF23() { return f23; }
        /** 전달된 값을 F23 필드에 저장하는 JavaBean 설정자다. */
        public void setF23(String value) { f23 = value; }
        /** F24 필드 값을 반환하는 JavaBean 접근자다. */
        public String getF24() { return f24; }
        /** 전달된 값을 F24 필드에 저장하는 JavaBean 설정자다. */
        public void setF24(String value) { f24 = value; }
        /** F25 필드 값을 반환하는 JavaBean 접근자다. */
        public String getF25() { return f25; }
        /** 전달된 값을 F25 필드에 저장하는 JavaBean 설정자다. */
        public void setF25(String value) { f25 = value; }
        /** F26 필드 값을 반환하는 JavaBean 접근자다. */
        public String getF26() { return f26; }
        /** 전달된 값을 F26 필드에 저장하는 JavaBean 설정자다. */
        public void setF26(String value) { f26 = value; }
        /** F27 필드 값을 반환하는 JavaBean 접근자다. */
        public String getF27() { return f27; }
        /** 전달된 값을 F27 필드에 저장하는 JavaBean 설정자다. */
        public void setF27(String value) { f27 = value; }
        /** F28 필드 값을 반환하는 JavaBean 접근자다. */
        public String getF28() { return f28; }
        /** 전달된 값을 F28 필드에 저장하는 JavaBean 설정자다. */
        public void setF28(String value) { f28 = value; }
        /** F29 필드 값을 반환하는 JavaBean 접근자다. */
        public String getF29() { return f29; }
        /** 전달된 값을 F29 필드에 저장하는 JavaBean 설정자다. */
        public void setF29(String value) { f29 = value; }
        /** F30 필드 값을 반환하는 JavaBean 접근자다. */
        public String getF30() { return f30; }
        /** 전달된 값을 F30 필드에 저장하는 JavaBean 설정자다. */
        public void setF30(String value) { f30 = value; }
        /** 1부터 시작하는 컬럼 번호에 대응하는 필드 값을 반환하고 범위 밖 번호는 거부한다. */
        public Object value(int column) {
            // 1부터 시작하는 컬럼 번호에 대응하는 값을 선택한다.
            return switch (column) {
                // 이 컬럼 번호에 해당하는 필드 값을 선택한다.
                case 1 -> id;
                // 이 컬럼 번호에 해당하는 필드 값을 선택한다.
                case 2 -> f2;
                // 이 컬럼 번호에 해당하는 필드 값을 선택한다.
                case 3 -> f3;
                // 이 컬럼 번호에 해당하는 필드 값을 선택한다.
                case 4 -> f4;
                // 이 컬럼 번호에 해당하는 필드 값을 선택한다.
                case 5 -> f5;
                // 이 컬럼 번호에 해당하는 필드 값을 선택한다.
                case 6 -> f6;
                // 이 컬럼 번호에 해당하는 필드 값을 선택한다.
                case 7 -> f7;
                // 이 컬럼 번호에 해당하는 필드 값을 선택한다.
                case 8 -> f8;
                // 이 컬럼 번호에 해당하는 필드 값을 선택한다.
                case 9 -> f9;
                // 이 컬럼 번호에 해당하는 필드 값을 선택한다.
                case 10 -> f10;
                // 이 컬럼 번호에 해당하는 필드 값을 선택한다.
                case 11 -> f11;
                // 이 컬럼 번호에 해당하는 필드 값을 선택한다.
                case 12 -> f12;
                // 이 컬럼 번호에 해당하는 필드 값을 선택한다.
                case 13 -> f13;
                // 이 컬럼 번호에 해당하는 필드 값을 선택한다.
                case 14 -> f14;
                // 이 컬럼 번호에 해당하는 필드 값을 선택한다.
                case 15 -> f15;
                // 이 컬럼 번호에 해당하는 필드 값을 선택한다.
                case 16 -> f16;
                // 이 컬럼 번호에 해당하는 필드 값을 선택한다.
                case 17 -> f17;
                // 이 컬럼 번호에 해당하는 필드 값을 선택한다.
                case 18 -> f18;
                // 이 컬럼 번호에 해당하는 필드 값을 선택한다.
                case 19 -> f19;
                // 이 컬럼 번호에 해당하는 필드 값을 선택한다.
                case 20 -> f20;
                // 이 컬럼 번호에 해당하는 필드 값을 선택한다.
                case 21 -> f21;
                // 이 컬럼 번호에 해당하는 필드 값을 선택한다.
                case 22 -> f22;
                // 이 컬럼 번호에 해당하는 필드 값을 선택한다.
                case 23 -> f23;
                // 이 컬럼 번호에 해당하는 필드 값을 선택한다.
                case 24 -> f24;
                // 이 컬럼 번호에 해당하는 필드 값을 선택한다.
                case 25 -> f25;
                // 이 컬럼 번호에 해당하는 필드 값을 선택한다.
                case 26 -> f26;
                // 이 컬럼 번호에 해당하는 필드 값을 선택한다.
                case 27 -> f27;
                // 이 컬럼 번호에 해당하는 필드 값을 선택한다.
                case 28 -> f28;
                // 이 컬럼 번호에 해당하는 필드 값을 선택한다.
                case 29 -> f29;
                // 이 컬럼 번호에 해당하는 필드 값을 선택한다.
                case 30 -> f30;
                // 지원하지 않는 메서드 또는 컬럼 번호를 예외로 알린다.
                default -> throw new IllegalArgumentException("Invalid column " + column);
            };
        }
    }

    /** 현재 행의 각 컬럼을 이름으로 읽어 대응하는 JavaBean 필드에 저장한다. */
    public static Row30 manual30(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        // 현재 행의 모든 컬럼 값을 담을 지정 크기의 JavaBean 객체를 만든다.
        var target = new Row30();
        // 이름이 대응하는 컬럼 값을 읽어 결과 객체의 필드에 저장한다.
        target.setId(rs.getObject("id", Long.class));
        // 이름이 대응하는 컬럼 값을 읽어 결과 객체의 필드에 저장한다.
        target.setF2(rs.getString("f2"));
        // 이름이 대응하는 컬럼 값을 읽어 결과 객체의 필드에 저장한다.
        target.setF3(rs.getString("f3"));
        // 이름이 대응하는 컬럼 값을 읽어 결과 객체의 필드에 저장한다.
        target.setF4(rs.getString("f4"));
        // 이름이 대응하는 컬럼 값을 읽어 결과 객체의 필드에 저장한다.
        target.setF5(rs.getString("f5"));
        // 이름이 대응하는 컬럼 값을 읽어 결과 객체의 필드에 저장한다.
        target.setF6(rs.getString("f6"));
        // 이름이 대응하는 컬럼 값을 읽어 결과 객체의 필드에 저장한다.
        target.setF7(rs.getString("f7"));
        // 이름이 대응하는 컬럼 값을 읽어 결과 객체의 필드에 저장한다.
        target.setF8(rs.getString("f8"));
        // 이름이 대응하는 컬럼 값을 읽어 결과 객체의 필드에 저장한다.
        target.setF9(rs.getString("f9"));
        // 이름이 대응하는 컬럼 값을 읽어 결과 객체의 필드에 저장한다.
        target.setF10(rs.getString("f10"));
        // 이름이 대응하는 컬럼 값을 읽어 결과 객체의 필드에 저장한다.
        target.setF11(rs.getString("f11"));
        // 이름이 대응하는 컬럼 값을 읽어 결과 객체의 필드에 저장한다.
        target.setF12(rs.getString("f12"));
        // 이름이 대응하는 컬럼 값을 읽어 결과 객체의 필드에 저장한다.
        target.setF13(rs.getString("f13"));
        // 이름이 대응하는 컬럼 값을 읽어 결과 객체의 필드에 저장한다.
        target.setF14(rs.getString("f14"));
        // 이름이 대응하는 컬럼 값을 읽어 결과 객체의 필드에 저장한다.
        target.setF15(rs.getString("f15"));
        // 이름이 대응하는 컬럼 값을 읽어 결과 객체의 필드에 저장한다.
        target.setF16(rs.getString("f16"));
        // 이름이 대응하는 컬럼 값을 읽어 결과 객체의 필드에 저장한다.
        target.setF17(rs.getString("f17"));
        // 이름이 대응하는 컬럼 값을 읽어 결과 객체의 필드에 저장한다.
        target.setF18(rs.getString("f18"));
        // 이름이 대응하는 컬럼 값을 읽어 결과 객체의 필드에 저장한다.
        target.setF19(rs.getString("f19"));
        // 이름이 대응하는 컬럼 값을 읽어 결과 객체의 필드에 저장한다.
        target.setF20(rs.getString("f20"));
        // 이름이 대응하는 컬럼 값을 읽어 결과 객체의 필드에 저장한다.
        target.setF21(rs.getString("f21"));
        // 이름이 대응하는 컬럼 값을 읽어 결과 객체의 필드에 저장한다.
        target.setF22(rs.getString("f22"));
        // 이름이 대응하는 컬럼 값을 읽어 결과 객체의 필드에 저장한다.
        target.setF23(rs.getString("f23"));
        // 이름이 대응하는 컬럼 값을 읽어 결과 객체의 필드에 저장한다.
        target.setF24(rs.getString("f24"));
        // 이름이 대응하는 컬럼 값을 읽어 결과 객체의 필드에 저장한다.
        target.setF25(rs.getString("f25"));
        // 이름이 대응하는 컬럼 값을 읽어 결과 객체의 필드에 저장한다.
        target.setF26(rs.getString("f26"));
        // 이름이 대응하는 컬럼 값을 읽어 결과 객체의 필드에 저장한다.
        target.setF27(rs.getString("f27"));
        // 이름이 대응하는 컬럼 값을 읽어 결과 객체의 필드에 저장한다.
        target.setF28(rs.getString("f28"));
        // 이름이 대응하는 컬럼 값을 읽어 결과 객체의 필드에 저장한다.
        target.setF29(rs.getString("f29"));
        // 이름이 대응하는 컬럼 값을 읽어 결과 객체의 필드에 저장한다.
        target.setF30(rs.getString("f30"));
        // 현재 행의 컬럼 값을 모두 채운 객체를 반환한다.
        return target;
    }
}

