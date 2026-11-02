package com.jorm.mapper;

import java.lang.reflect.Proxy;

import com.jorm.jdbc.JdbcTemplate;

public class MapperFactory {

    /** 인터페이스를 구현하는 JDK 동적 프록시를 만들고 전용 InvocationHandler에 연결한다. */
    public static <T> T create(Class<T> mapperType, JdbcTemplate jdbcTemplate) {
        // JDK 프록시로 구현 가능한 인터페이스인지 검사한다.
        if (!mapperType.isInterface()) {
            // 잘못된 선언·입력 또는 실행 상태를 예외로 알린다.
            throw new IllegalArgumentException(
                "Mapper는 인터페이스여야 합니다: " + mapperType.getName()
            );
        }

        // Mapper 인터페이스를 구현하고 호출을 핸들러에 전달하는 프록시를 만든다.
        Object proxy = Proxy.newProxyInstance(
            mapperType.getClassLoader(),
            new Class<?>[]{mapperType},
            new MapperInvocationHandler(jdbcTemplate)
        );

        // 생성한 프록시를 지정 Mapper 인터페이스 타입으로 반환한다.
        return mapperType.cast(proxy);
    }
}
