package com.jorm.transaction;

import java.lang.reflect.Proxy;
import java.util.Objects;

public final class TransactionProxyFactory {
    /** 정적 팩토리 전용이므로 외부의 인스턴스 생성을 막는다. */
    private TransactionProxyFactory() {}

    /** 인터페이스를 구현하는 JDK 동적 프록시를 만들고 전용 InvocationHandler에 연결한다. */
    public static <T> T create(Class<T> serviceType, T target, TransactionManager transactions) {
        // 필수 의존성이 null인지 검사하고 필요한 경우 필드에 보관한다.
        Objects.requireNonNull(serviceType);
        // 필수 의존성이 null인지 검사하고 필요한 경우 필드에 보관한다.
        Objects.requireNonNull(target);
        // 대상이 지정한 서비스 인터페이스를 구현하는지 검사한다.
        if (!serviceType.isInterface() || !serviceType.isInstance(target)) {
            // 잘못된 선언·입력 또는 실행 상태를 예외로 알린다.
            throw new IllegalArgumentException("Target must implement the service interface");
        }
        // 인터페이스 프록시를 생성하고 지정된 Service 타입으로 반환한다.
        return serviceType.cast(Proxy.newProxyInstance(serviceType.getClassLoader(),
                new Class<?>[]{serviceType}, new TransactionInvocationHandler(target, transactions)));
    }
}
