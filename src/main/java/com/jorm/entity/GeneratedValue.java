package com.jorm.entity;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** @Id 필드의 생성 전략을 지정한다. 기본은 DB의 IDENTITY이며 UUID는 INSERT 전에 생성한다. 스키마 생성 기능은 제공하지 않는다. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface GeneratedValue {
    GenerationType strategy() default GenerationType.IDENTITY;
}
