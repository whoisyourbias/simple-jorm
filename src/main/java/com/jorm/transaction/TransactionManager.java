package com.jorm.transaction;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import javax.sql.DataSource;

/** 같은 스레드의 JDBC 작업을 하나의 트랜잭션으로 묶는다. JdbcTemplate과 이 인스턴스를 공유해야 한다. */
public final class TransactionManager {
    // 트랜잭션 시작 시 실제 DB 연결을 빌릴 공급자다.
    private final DataSource dataSource;
    // 매니저 전체에 연결 하나가 아니라, 이 매니저를 사용하는 스레드마다 활성 연결 하나를 보관한다.
    // 요청 처리 스레드가 재사용되므로 트랜잭션 종료 시 반드시 remove()한다.
    private final ThreadLocal<TransactionState> current = new ThreadLocal<>();

    /** 물리 연결 하나와 참여한 업무에서 발생한 최초 실패를 함께 보관한다. */
    private static final class TransactionState {
        private final Connection connection;
        // null이 아니면 rollbackOnly다. 바깥에서 예외를 잡더라도 이 표시는 유지된다.
        private Throwable rollbackCause;
        private boolean preparingCommit;
        private boolean completing;
        private final Map<Object, TransactionResource> resources = new LinkedHashMap<>();

        /** 가장 바깥 호출이 확보한 연결을 모든 참여자에게 공유한다. */
        private TransactionState(Connection connection) {
            this.connection = connection;
        }
    }

    /** DataSource를 보관하고 현재 스레드의 트랜잭션 연결을 관리할 준비를 한다. */
    public TransactionManager(DataSource dataSource) {
        // 필수 의존성이 null인지 검사하고 필요한 경우 필드에 보관한다.
        this.dataSource = Objects.requireNonNull(dataSource);
    }

    /** 같은 트랜잭션의 자원을 재사용한다. 트랜잭션 밖에서는 생성하지 않는다. */
    @SuppressWarnings("unchecked")
    public <R extends TransactionResource> R getOrCreateResource(Object key, Supplier<R> factory) {
        Objects.requireNonNull(key);
        Objects.requireNonNull(factory);
        TransactionState state = activeState();
        if (state == null) {
            return null;
        }
        TransactionResource resource = state.resources.get(key);
        if (resource == null) {
            if (state.preparingCommit) {
                throw new IllegalStateException("커밋 전 콜백 중에는 새 자원을 등록할 수 없습니다.");
            }
            resource = Objects.requireNonNull(factory.get());
            state.resources.put(key, resource);
        }
        return (R) resource;
    }

    /** 내부에서 잡은 SQL 실패도 가장 바깥 경계에서 전체 롤백하게 한다. */
    public void markRollbackOnly(Throwable cause) {
        Objects.requireNonNull(cause);
        TransactionState state = activeState();
        if (state != null && state.rollbackCause == null) {
            state.rollbackCause = cause;
        }
    }

    private TransactionState activeState() {
        TransactionState state = current.get();
        if (state != null && state.completing) {
            throw new IllegalStateException("종료 중인 트랜잭션을 다시 사용할 수 없습니다.");
        }
        return state;
    }

    @FunctionalInterface
    public interface TransactionCallback<T> {
        /** 트랜잭션에 포함할 업무를 정의한다. 커밋·롤백은 이 콜백을 실행하는 관리자가 처리한다. */
        T execute() throws SQLException;
    }

    @FunctionalInterface
    public interface ConnectionCallback<T> {
        /** 관리자가 제공한 연결로 SQL 작업을 수행한다. 콜백은 공유 연결을 직접 닫거나 커밋하지 않는다. */
        T execute(Connection connection) throws SQLException;
    }

    /** 활성 연결을 빌려 쓰거나 임시 연결을 열어 작업한다. 임시 연결만 이 메서드가 닫는다. */
    public <T> T withConnection(ConnectionCallback<T> callback) throws SQLException {
        // 이 관리자에서 현재 스레드에 묶인 트랜잭션 연결을 찾는다.
        TransactionState active = activeState();
        // 활성 트랜잭션 연결이 있는지 확인한다.
        if (active != null) {
            // 빌려 쓰는 연결로 실행한다. 이 메서드는 공유 연결을 닫지 않는다.
            return callback.execute(active.connection);
        }
        // 연결·Statement·파일 자원을 열고 try-with-resources로 사용 후 자동 정리한다.
        try (Connection connection = dataSource.getConnection()) {
            // 임시 연결로 실행하고 try 블록 종료 시 연결을 닫는다.
            return callback.execute(connection);
        }
    }

    /** REQUIRED: 기존 트랜잭션에 참여하거나 새로 시작한다. 가장 바깥 호출만 연결을 정리한다. */
    public <T> T execute(TransactionCallback<T> callback) throws SQLException {
        // 필수 의존성이 null인지 검사하고 필요한 경우 필드에 보관한다.
        Objects.requireNonNull(callback);
        // 이미 트랜잭션이 있으면 새 연결을 만들지 않고 기존 상태에 참여한다.
        TransactionState active = activeState();
        if (active != null) {
            if (active.preparingCommit) {
                throw new IllegalStateException("커밋 전 콜백 중에는 트랜잭션 경계를 시작할 수 없습니다.");
            }
            return participate(active, callback);
        }
        // 연결·Statement·파일 자원을 열고 try-with-resources로 사용 후 자동 정리한다.
        try (Connection connection = dataSource.getConnection()) {
            // 종료 후 복원할 원래 자동 커밋 값을 기억한다.
            boolean autoCommit = connection.getAutoCommit();
            // SQL마다 확정하지 않고 이후 작업을 하나의 트랜잭션으로 묶는다.
            connection.setAutoCommit(false);
            // 원래 실패를 저장해 정리 실패가 이를 덮어쓰지 않게 한다.
            Throwable failure = null;
            // 롤백 실패 시 자동 커밋 복원을 막기 위한 상태를 둔다.
            boolean safeToReset = true;
            // 현재 스레드에 연결을 등록해 Mapper와 JdbcTemplate이 공유하게 한다.
            TransactionState state = new TransactionState(connection);
            current.set(state);
            try {
                // 같은 연결을 공유하는 업무를 실행하고 반환값을 보관한다.
                T result = callback.execute();
                // 안쪽 실패를 업무 코드에서 잡았더라도 부분 성공을 커밋하면 안 된다.
                if (state.rollbackCause != null) {
                    // 아래 catch에서 전체 롤백한 뒤 호출자에게 정상 커밋되지 않았음을 알린다.
                    throw new UnexpectedRollbackException(state.rollbackCause);
                }
                // 동기화 중에는 같은 연결 SQL을 허용하되 새 경계와 자원 등록을 금지한다.
                state.preparingCommit = true;
                try {
                    for (TransactionResource resource : state.resources.values()) {
                        resource.beforeCommit();
                    }
                } finally {
                    state.preparingCommit = false;
                }
                if (state.rollbackCause != null) {
                    throw new UnexpectedRollbackException(state.rollbackCause);
                }
                // 지금까지 실행한 변경을 확정한다.
                state.completing = true;
                connection.commit();
                // 커밋이 완료된 업무 콜백의 반환값을 전달한다.
                return result;
                // 해당 실패를 받아 롤백 또는 예외 변환·전파 경로로 처리한다.
            } catch (SQLException | RuntimeException | Error e) {
                // 정리 실패보다 우선 전달할 원래 실패를 기억한다.
                failure = e;
                state.completing = true;
                try {
                    // 커밋되지 않은 변경을 모두 취소한다.
                    connection.rollback();
                    // 롤백 자체의 실패도 원래 예외와 함께 보존한다.
                } catch (SQLException rollbackFailure) {
                    // 롤백 실패 후 자동 커밋 복원에 따른 의도치 않은 확정을 막는다.
                    safeToReset = false;
                    // 롤백 실패를 원래 예외에 부가 정보로 붙인다.
                    e.addSuppressed(rollbackFailure);
                }
                // 원래 실패를 호출자에게 전달한다. 트랜잭션 안이면 롤백 처리로 이어진다.
                throw e;
                // 성공·실패와 관계없이 연결 상태와 스레드 자원을 정리한다.
            } finally {
                state.completing = true;
                Throwable completionFailure = completeResources(state, failure);
                // 재사용되는 스레드에 이전 요청의 연결이 남지 않도록 제거한다.
                current.remove();
                try {
                    // 롤백이 실패하지 않았을 때만 연결 설정을 복원한다.
                    if (safeToReset) {
                        // 연결을 닫기 전에 원래 자동 커밋 값으로 복원한다.
                        connection.setAutoCommit(autoCommit);
                    }
                    // 자동 커밋 설정 복원 중 실패를 처리한다.
                } catch (SQLException resetFailure) {
                    // 이미 전달할 원래 실패가 있는지 확인한다.
                    if (completionFailure != null) {
                        // 설정 복원 실패를 원래 실패에 부가 정보로 보존한다.
                        completionFailure.addSuppressed(resetFailure);
                    } else {
                        // 원래 실패를 호출자에게 전달한다. 트랜잭션 안이면 롤백 처리로 이어진다.
                        completionFailure = resetFailure;
                    }
                }
                if (failure == null && completionFailure != null) {
                    if (completionFailure instanceof SQLException sql) {
                        throw sql;
                    }
                    if (completionFailure instanceof RuntimeException runtime) {
                        throw runtime;
                    }
                    throw (Error) completionFailure;
                }
            }
        }
    }

    /** 한 자원의 정리 실패가 나머지 자원의 종료를 막지 않게 한다. */
    private Throwable completeResources(TransactionState state, Throwable failure) {
        for (TransactionResource resource : state.resources.values()) {
            try {
                resource.afterCompletion();
            } catch (RuntimeException | Error cleanupFailure) {
                if (failure == null) {
                    failure = cleanupFailure;
                } else if (failure != cleanupFailure) {
                    failure.addSuppressed(cleanupFailure);
                }
            }
        }
        state.resources.clear();
        return failure;
    }

    /** 참여자는 커밋·롤백·연결 종료를 하지 않고 실패 시 rollbackOnly 표시만 남긴다. */
    private <T> T participate(TransactionState state, TransactionCallback<T> callback) throws SQLException {
        try {
            // 가장 바깥 호출이 등록한 Connection을 그대로 사용하는 업무를 실행한다.
            return callback.execute();
        } catch (SQLException | RuntimeException | Error e) {
            // 여러 단계가 실패해도 최초 실패 원인을 유지한다.
            if (state.rollbackCause == null) {
                state.rollbackCause = e;
            }
            // 바깥에서 잡을 수 있지만 위의 롤백 표시는 사라지지 않는다.
            throw e;
        }
    }
}
