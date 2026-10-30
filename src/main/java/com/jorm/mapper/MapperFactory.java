package com.jorm.mapper;

import java.lang.reflect.Proxy;

import com.jorm.jdbc.JdbcTemplate;

public class MapperFactory {

    public static <T> T create(Class<T> mapperType, JdbcTemplate jdbcTemplate) {
        if (!mapperType.isInterface()) {
            throw new IllegalArgumentException(
                "Mapper는 인터페이스여야 합니다: " + mapperType.getName()
            );
        }

        Object proxy = Proxy.newProxyInstance(
            mapperType.getClassLoader(),
            new Class<?>[]{mapperType},
            new MapperInvocationHandler(jdbcTemplate)
        );

        return mapperType.cast(proxy);
    }
}