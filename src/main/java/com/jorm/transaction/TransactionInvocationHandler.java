package com.jorm.transaction;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.SQLException;
import java.util.Objects;

public final class TransactionInvocationHandler implements InvocationHandler {
    // 프록시가 대신 호출할 실제 Service 구현체다. 프록시 자신과는 다른 객체다.
    private final Object target;
    private final TransactionManager transactions;

    /** 호출할 실제 Service 객체(target)와 트랜잭션 관리자를 보관한다. */
    public TransactionInvocationHandler(Object target, TransactionManager transactions) {
        // 필수 의존성이 null인지 검사하고 필요한 경우 필드에 보관한다.
        this.target = Objects.requireNonNull(target);
        // 필수 의존성이 null인지 검사하고 필요한 경우 필드에 보관한다.
        this.transactions = Objects.requireNonNull(transactions);
    }

    /** 인터페이스 호출에서 실제 구현 메서드를 찾아 @Transactional 유무로 분기한다. */
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        // 인터페이스 Method 대신 실제 target 클래스의 같은 이름·파라미터 메서드를 찾는다.
        Method implementationMethod = target.getClass().getMethod(
                method.getName(), method.getParameterTypes());
        // 어노테이션이 붙은 구현 메서드에서 트랜잭션 적용 여부를 검사한다.
        if (!implementationMethod.isAnnotationPresent(Transactional.class)) {
            // 실제 Service 메서드를 호출하고 반환값을 전달한다.
            return invokeTarget(implementationMethod, args);
        }
        try {
            // 실제 Service 실행 전체를 트랜잭션 콜백으로 감싼다.
            return transactions.execute(() -> {
                try {
                    // 실제 Service 메서드를 호출하고 반환값을 전달한다.
                    return invokeTarget(implementationMethod, args);
                    // 해당 실패를 받아 롤백 또는 예외 변환·전파 경로로 처리한다.
                } catch (SQLException | RuntimeException | Error e) {
                    // 원래 실패를 호출자에게 전달한다. 트랜잭션 안이면 롤백 처리로 이어진다.
                    throw e;
                    // 해당 실패를 받아 롤백 또는 예외 변환·전파 경로로 처리한다.
                } catch (Throwable e) {
                    // 현재 TransactionCallback은 checked 예외로 SQLException만 허용한다.
                    // 나머지 checked 예외는 임시 포장해 롤백한 후 바깥 catch에서 원래 예외로 돌린다.
                    // checked 업무 예외를 임시 포장해 관리자의 롤백 경로로 전달한다.
                    throw new ServiceFailure(e);
                }
            });
            // 롤백 완료 후 임시 포장한 checked 예외를 원본으로 복원한다.
        } catch (ServiceFailure e) {
            // 임시로 포장한 checked 업무 예외의 원본을 꺼낸다.
            Throwable original = e.getCause();
            // 롤백·연결 정리 과정에서 추가된 예외를 순회한다.
            for (Throwable suppressed : e.getSuppressed()) {
                // 원래 예외를 돌려줄 때 정리 실패 정보도 보존한다.
                original.addSuppressed(suppressed);
            }
            // 원래 실패를 호출자에게 전달한다. 트랜잭션 안이면 롤백 처리로 이어진다.
            throw original;
        }
    }

    /** 실제 객체를 리플렉션으로 호출하고 InvocationTargetException에서 원래 예외를 꺼낸다. */
    private Object invokeTarget(Method method, Object[] args) throws Throwable {
        try {
            // 실제 Service 객체(target)의 메서드를 전달된 인자로 호출한다.
            return method.invoke(target, args);
            // 리플렉션이 실제 메서드 예외를 감쌌을 때 원인을 꺼낸다.
        } catch (InvocationTargetException e) {
            // 리플렉션의 포장 예외에서 실제 Service·매퍼 실패를 꺼내 던진다.
            throw e.getCause();
        }
    }

    private static final class ServiceFailure extends RuntimeException {
        /** checked 업무 예외를 롤백 가능한 RuntimeException으로 임시 포장한다. */
        ServiceFailure(Throwable cause) { super(cause); }
    }
}
