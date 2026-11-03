# Entity update/remove Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans for inline implementation.

**Goal:** 엔티티 ID 조건으로 즉시 UPDATE·DELETE를 실행한다.
**Architecture:** EntityManager가 캐시된 EntityMetadata의 컬럼과 필드를 사용해 SQL과 바인딩 값을 구성한다. JdbcTemplate.update가 기존 연결·트랜잭션 규칙을 적용한다.
**Tech Stack:** Java 21, JDBC, H2, JUnit 5, Gradle.
**Spec:** ../specs/2026-11-03-entity-update-remove-design.md

## Global Constraints
기존 persist·find API 유지, 새 의존성 없음, ID를 SET에서 제외, int 반환.

## Review Focus
다른 행 보호, 별칭 ID 컬럼, null 값 바인딩, 생성 ID, ID 전용 타입, SQL 오류와 롤백을 실제 H2로 검증한다.

## Task 1: 엔티티 변경·삭제
- [x] EntityManagerMutationTest 작성: update/remove의 저장 결과, 없는 ID, null 입력, ID 전용 타입, 기본 생성자 없는 타입, IDENTITY·UUID, SQL 오류, 커밋·롤백.
- [x] `./gradlew.bat test --tests com.jorm.entity.EntityManagerMutationTest`로 미구현 실패 확인.
- [x] EntityManager.update(Object), remove(Object), 공통 ID 검증 구현.
- [x] 대상 테스트와 전체 테스트 실행, 독립 코드 리뷰.
- [x] 학습 문서·현재 현황 갱신 및 diff 확인.
