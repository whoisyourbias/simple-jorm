package com.jorm.jdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

//SQL 실행, 파라미터 바인딩, JDBC 자원 정리
public class JdbcTemplate {

	private final DataSource dataSource;
	
	public JdbcTemplate(DataSource datasource) {
		this.dataSource = datasource;
	}
	
	/**
	 * SQL을 실행하고 조회 결과의 첫 번째 행을 객체로 변환하여 반환한다.
	 *
	 * <p>조회 결과가 없으면 {@code null}을 반환한다.
	 * 한 건 이상이면 첫 번째 행만 매핑하며, 나머지 행은 무시한다.
	 *
	 * @param <T> 반환할 객체의 타입
	 * @param query 실행할 조회 SQL
	 * @param rowMapper 첫 번째 행을 객체로 변환하는 매퍼
	 * @param args SQL의 물음표(?) 순서대로 바인딩할 파라미터
	 * @return 첫 번째 행을 매핑한 객체. 조회 결과가 없으면 {@code null}
	 * @throws SQLException 연결 획득, SQL 실행 또는 결과 매핑 중 오류가 발생한 경우
	 */
	public <T> T queryForObject(
			String query,
			RowMapper<T> rowMapper,
			Object... args
			) throws SQLException {
		try (Connection conn = this.dataSource.getConnection();
			PreparedStatement stmt = conn.prepareStatement(query);
			) {
			
			bindParameters(stmt, args);
			
			try (ResultSet st = stmt.executeQuery()) {
				if (!st.next())
					return null;
				
				return rowMapper.prepare(st).mapRow(st, 0);
			}
		}
	}
	
	/**
	 * SQL을 실행하고 조회 결과의 모든 행을 객체로 변환하여 리스트로 반환한다.
	 *
	 * <p>조회 결과가 없으면 빈 리스트를 반환한다.
	 * 각 행은 조회 순서대로 매핑하며, 매퍼에 전달하는 행 번호는 0부터 시작한다.
	 *
	 * @param <T> 리스트 요소의 타입
	 * @param query 실행할 조회 SQL
	 * @param rowMapper 각 행을 객체로 변환하는 매퍼
	 * @param args SQL의 물음표(?) 순서대로 바인딩할 파라미터
	 * @return 조회 순서대로 매핑한 객체 리스트. 조회 결과가 없으면 빈 리스트
	 * @throws SQLException 연결 획득, SQL 실행 또는 결과 매핑 중 오류가 발생한 경우
	 */
	public <T> List<T> query(
			String query,
			RowMapper<T> rowMapper,
			Object... args
			) throws SQLException {
		try (Connection conn = this.dataSource.getConnection();
			PreparedStatement stmt = conn.prepareStatement(query);) {
			
			bindParameters(stmt, args);
			
			try (ResultSet st = stmt.executeQuery()) {
				List<T> results = new ArrayList<>();
				int rowNum = 0;
				if (!st.next()) {
					return results;
				}
				RowMapper<T> prepared = rowMapper.prepare(st);
				do {
					results.add(prepared.mapRow(st, rowNum++));
				} while (st.next());
				
				return results;			
			}
		}
	}
	
	/**
	 * INSERT, UPDATE 또는 DELETE SQL을 실행하고 영향받은 행 수를 반환한다.
	 *
	 * <p>영향받은 행이 없으면 0을 반환한다.
	 *
	 * @param query 실행할 데이터 변경 SQL
	 * @param args SQL의 물음표(?) 순서대로 바인딩할 파라미터
	 * @return SQL 실행으로 영향받은 행 수
	 * @throws SQLException 연결 획득, 파라미터 바인딩 또는 SQL 실행 중
	 *                      오류가 발생한 경우
	 */
	public int update(
			String query,
			Object... args
			) throws SQLException {
		try (Connection conn = this.dataSource.getConnection();
			PreparedStatement stmt = conn.prepareStatement(query);) {
			
			bindParameters(stmt, args);
			return stmt.executeUpdate();	
		}
	}
	
	/**
	 * 파라미터를 SQL의 물음표(?) 순서대로 바인딩한다.
	 *
	 * @param stmt 파라미터를 설정할 PreparedStatement
	 * @param args 순서대로 바인딩할 값
	 * @throws SQLException 파라미터 바인딩 중 오류가 발생한 경우
	 */
	private void bindParameters(
	        PreparedStatement stmt,
	        Object... args
	) throws SQLException {
	    for (int i = 0; i < args.length; i++) {
	        stmt.setObject(i + 1, args[i]);
	    }
	}
}
