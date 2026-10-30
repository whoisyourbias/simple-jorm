package com.jorm.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;

@FunctionalInterface
public interface RowMapper<T> {
	T mapRow(ResultSet rs, int rowNum) throws SQLException;

    /**
     * 현재 조회에서 사용할 매퍼를 준비한다. 커서를 이동시키지 않는다.
     * 반환한 매퍼는 전달한 ResultSet에만 사용하며 조회가 끝나면 버린다.
     * 기본 구현은 기존 매퍼를 그대로 사용한다.
     */
    default RowMapper<T> prepare(ResultSet rs) throws SQLException {
        return this;
    }
}
