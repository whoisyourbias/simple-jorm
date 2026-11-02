package com.jorm.transaction;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Service 구현 메서드에 붙이는 트랜잭션 표시다. 실제 동작은 프록시가 적용한다. */
// 실행 중 리플렉션으로 어노테이션을 읽을 수 있게 유지한다.
@Retention(RetentionPolicy.RUNTIME)
// 클래스나 필드가 아닌 메서드에만 붙일 수 있다.
@Target(ElementType.METHOD)
public @interface Transactional {

}
