package com.jorm.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;

@FunctionalInterface
public interface RowMapper<T> {
	/** 현재 행을 Java 객체로 변환한다. 커서 이동은 호출자가 담당하고 rowNum은 0부터 시작한다. */
	T mapRow(ResultSet rs, int rowNum) throws SQLException;

    /**
     * 현재 조회의 모든 행에 사용할 매퍼를 준비한다.
     *
     * <p>JdbcTemplate은 첫 행이 존재하는지 확인한 뒤 이 메서드를 한 번 호출하고,
     * 반환된 매퍼의 mapRow를 각 행에 호출한다. 결과가 비어 있으면 호출하지 않는다.
     * 구현체는 컬럼 메타데이터 분석처럼 행마다 반복할 필요가 없는 작업을 여기서
     * 수행할 수 있다. 이 메서드 자체는 커서를 이동시키거나 행의 값을 매핑하지 않는다.
     *
     * <p>반환한 매퍼는 전달한 ResultSet에만 사용하며 조회가 끝나면 버린다.
     * 다른 조회는 컬럼 구성이나 순서가 다를 수 있으므로 준비된 매퍼를 재사용하지 않는다.
     * 기본 구현은 this를 반환하므로, 별도 준비가 필요 없는 람다 매퍼도 그대로 동작한다.
     * default 메서드이므로 추상 메서드는 mapRow 하나로 유지된다.
     *
     * @param rs 현재 조회의 ResultSet. JdbcTemplate에서는 첫 행에 위치한 상태로 전달한다.
     * @return 현재 조회의 각 행을 매핑할 매퍼
     * @throws SQLException 메타데이터 조회 또는 매핑 준비 중 오류가 발생한 경우
     */
    default RowMapper<T> prepare(ResultSet rs) throws SQLException {
        // 준비 작업이 없는 매퍼는 현재 매퍼를 그대로 사용한다.
        return this;
    }
}
