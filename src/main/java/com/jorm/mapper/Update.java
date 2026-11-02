package com.jorm.mapper;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// Mapper의 메서드에만 SQL을 지정할 수 있다.
@Target(ElementType.METHOD)
// 프록시가 실행 중 리플렉션으로 읽을 수 있도록 보존한다.
@Retention(RetentionPolicy.RUNTIME)
public @interface Update {
	/** 이 Mapper 메서드가 실행할 SQL 문자열을 지정하는 어노테이션 속성이다. */
	String value();
}
