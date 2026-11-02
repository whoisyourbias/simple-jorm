package com.jorm.jdbc;

import com.jorm.transaction.TransactionManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

//SQL 실행, 파라미터 바인딩, JDBC 자원 정리
public class JdbcTemplate {

	private final TransactionManager transactions;

	/** SQL 실행에 사용할 관리자를 연결한다. DataSource 생성자는 독립적인 트랜잭션 관리자를 만든다. */
	public JdbcTemplate(DataSource datasource) {
		// 다른 생성자로 위임해 초기화 경로를 통일한다.
		this(new TransactionManager(datasource));
	}

	/** SQL 실행에 사용할 관리자를 연결한다. DataSource 생성자는 독립적인 트랜잭션 관리자를 만든다. */
	public JdbcTemplate(TransactionManager transactions) {
		// 필수 의존성이 null인지 검사하고 필요한 경우 필드에 보관한다.
		this.transactions = java.util.Objects.requireNonNull(transactions);
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
		// 활성 트랜잭션 연결 또는 임시 연결을 받아 SQL을 실행한다.
		return transactions.withConnection(conn -> {
			// 물음표 바인딩용 PreparedStatement를 만들고 블록 종료 시 닫는다.
			try (PreparedStatement stmt = conn.prepareStatement(query)) {

				// SQL 물음표 순서대로 인자를 설정한다.
				bindParameters(stmt, args);

				// SQL 결과를 열고 블록 종료 시 ResultSet을 닫는다.
				try (ResultSet st = stmt.executeQuery()) {
					// 첫 행으로 이동한다. 행이 없으면 아래에서 null을 반환한다.
					if (!st.next())
						// 단건 결과가 없거나 테스트용 반환값이 없음을 null로 나타낸다.
						return null;

					// 매퍼를 준비하고 첫 행만 행 번호 0으로 변환한다.
					return rowMapper.prepare(st).mapRow(st, 0);
				}
			}
		});
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
		// 활성 트랜잭션 연결 또는 임시 연결을 받아 SQL을 실행한다.
		return transactions.withConnection(conn -> {
			// 물음표 바인딩용 PreparedStatement를 만들고 블록 종료 시 닫는다.
			try (PreparedStatement stmt = conn.prepareStatement(query)) {

				// SQL 물음표 순서대로 인자를 설정한다.
				bindParameters(stmt, args);

				// SQL 결과를 열고 블록 종료 시 ResultSet을 닫는다.
				try (ResultSet st = stmt.executeQuery()) {
					// 결과 또는 실행 순서를 담을 리스트를 준비한다.
					List<T> results = new ArrayList<>();
					// 행 매핑에 전달할 번호를 0으로 초기화한다.
					int rowNum = 0;
					// 첫 행으로 이동하고 결과가 비었는지 확인한다.
					if (!st.next()) {
						// 조회 순서대로 매핑한 리스트를 반환한다. 행이 없으면 빈 리스트다.
						return results;
					}
					// 조회 전용 매퍼를 한 번 준비해 모든 행에서 재사용한다.
					RowMapper<T> prepared = rowMapper.prepare(st);
					// 첫 행에 이미 위치했으므로 현재 행부터 변환한다.
					do {
						// 현재 행을 변환해 리스트에 추가하고 행 번호를 증가시킨다.
						results.add(prepared.mapRow(st, rowNum++));
					    // 다음 행이 있으면 같은 준비된 매퍼로 반복한다.
					} while (st.next());

					// 조회 순서대로 매핑한 리스트를 반환한다. 행이 없으면 빈 리스트다.
					return results;
				}
			}
		});
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
		// 활성 트랜잭션 연결 또는 임시 연결을 받아 SQL을 실행한다.
		return transactions.withConnection(conn -> {
			// 물음표 바인딩용 PreparedStatement를 만들고 블록 종료 시 닫는다.
			try (PreparedStatement stmt = conn.prepareStatement(query)) {

				// SQL 물음표 순서대로 인자를 설정한다.
				bindParameters(stmt, args);
				// 변경 SQL을 실행하고 영향받은 행 수를 반환한다.
				return stmt.executeUpdate();
			}
		});
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
	    // SQL 인자를 전달받은 순서대로 처리한다.
	    for (int i = 0; i < args.length; i++) {
	        // 배열 인덱스에 1을 더한 JDBC 위치에 값을 바인딩한다.
	        stmt.setObject(i + 1, args[i]);
	    }
	}
}
