package com.jorm.entity;

import com.jorm.transaction.TransactionResource;

/** 트랜잭션의 범용 자원 계약과 실제 매니저 세션을 연결한다. */
final class EntityManagerHolder implements TransactionResource {
    private final EntityManagerSession session;

    EntityManagerHolder(EntityManagerSession session) {
        this.session = session;
    }

    EntityManagerSession getSession() {
        return session;
    }

    @Override
    public void afterCompletion() {
        session.close();
    }
}
