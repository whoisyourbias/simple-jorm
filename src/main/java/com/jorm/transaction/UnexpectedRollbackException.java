package com.jorm.transaction;

/** 업무가 정상 반환했지만 참여자의 실패로 전체 트랜잭션이 롤백되었음을 알린다. */
public final class UnexpectedRollbackException extends IllegalStateException {
    /** rollbackOnly를 남긴 최초 실패를 원인으로 보존한다. */
    public UnexpectedRollbackException(Throwable cause) {
        super("Transaction rolled back because a participating operation failed", cause);
    }
}
