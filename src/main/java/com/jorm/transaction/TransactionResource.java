package com.jorm.transaction;

import java.sql.SQLException;

/** 바깥 트랜잭션의 커밋 전 동기화와 종료 후 정리를 담당하는 자원. */
@FunctionalInterface
public interface TransactionResource {
    /** 같은 연결에서 SQL을 실행할 수 있다. 실패하면 전체 트랜잭션을 롤백한다. */
    default void beforeCommit() throws SQLException {}

    /** 성공·실패 모두에서 정리한다. 이 단계에서는 DB 작업과 트랜잭션 재진입을 금지한다. */
    void afterCompletion();
}
