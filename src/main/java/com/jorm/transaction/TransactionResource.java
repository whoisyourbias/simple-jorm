package com.jorm.transaction;

/** 바깥 트랜잭션 종료 후 정리할 자원. 종료 콜백에서는 새 DB 작업을 시작하지 않는다. */
@FunctionalInterface
public interface TransactionResource {
    void afterCompletion();
}
