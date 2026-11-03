# Generated ID Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 명시한 DB 생성 ID를 엔티티에 반영한다.
**Architecture:** EntityMetadata가 생성 전략을 검증하고 EntityManager가 ID를 제외한 INSERT를 만든다. JdbcTemplate은 생성 키를 같은 연결 안에서 RowMapper로 전달한다.
**Tech Stack:** Java 21, JDBC, JUnit 5, H2, Gradle.
**Spec:** ../specs/2026-11-03-generated-id-design.md

## Global Constraints
일반 @Id와 persist의 int 반환 유지. Long/Integer 생성 ID만 지원. 새 의존성 없음.

## Review Focus
키 컬럼 이름 지정, ID만 있는 엔티티, Integer 범위, SQL 실패 후 ID, 롤백 후 객체 상태를 검증한다.

## Task 1: 생성 ID 지원
- [x] EntityManagerGeneratedIdTest에 실제 H2 기반 저장·조회·검증·롤백 테스트 작성.
- [x] `./gradlew.bat test --tests com.jorm.entity.EntityManagerGeneratedIdTest`에서 미구현 실패 확인.
- [x] GeneratedValue, EntityMetadata.isGeneratedId(), JdbcTemplate.updateWithGeneratedKey(String, String, RowMapper<Void>, Object...), EntityManager.persist 경로 구현.
- [x] 대상 테스트와 전체 `./gradlew.bat test` 실행.
- [x] 학습 문서와 진행 현황 갱신 후 diff 검토.
