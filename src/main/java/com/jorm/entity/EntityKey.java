package com.jorm.entity;

/** 테이블을 공유하는 엔티티도 Java 타입별로 구분한다. */
record EntityKey(Class<?> entityType, Object id) {}
