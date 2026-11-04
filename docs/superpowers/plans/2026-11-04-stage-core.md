# JORM Stage Core Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 단계별 핵심 기능을 별도 워크트리의 담당 서브에이전트가 구현하고 호출자가 통합·검증한다.

**Architecture:** 공통 기준은 최신 main과 기존 영속성 컨텍스트 브랜치를 합친 0e802c3이다. 01·02·03을 병렬 진행하고 빈 슬롯에 04를 배정한다. 05는 03의 beforeCommit 계약 위에서 진행한다.

**Tech Stack:** Java 21, Gradle 8.9, JUnit 5, H2, JDK reflection/proxy, standalone HTML/Node tests.

**Spec:** `docs/superpowers/specs/2026-11-04-stage-core-design.md`

## Global Constraints

- 각 단계는 할당받은 독립 워크트리에서 담당 서브에이전트가 수정한다.
- 문서 → 테스트 → 구현 → 소개문서 → 검증/커밋/호출자 보고 순서를 지킨다.
- 핵심만 구현하고 JDK/JDBC 기반 자체 API를 유지한다.
- 원본 main의 미커밋 변경을 보존하며 main 병합/push를 수행하지 않는다.

## Review Focus

- 자동 매핑의 null primitive, 숫자 범위 초과, setter 실패를 JDBC 테스트에서 검증한다.
- 이름 바인딩의 반복·순서·누락과 SQL 인용문을 Mapper 테스트에서 검증한다.
- REQUIRED 안쪽의 checked 예외와 noRollbackFor가 rollbackOnly를 잘못 남기지 않는지 검증한다.
- 상속 ID, 저장 제외 필드, 숨겨진 중복 필드를 엔티티 테스트에서 검증한다.
- flush 실패 전체 롤백과 명시적 update/remove 이후 중복 UPDATE 방지를 검증한다.

## Task 01 — JDBC (stage_01_jdbc)

Files: `com/jorm/jdbc/BeanPropertyRowMapper.java`, JDBC tests, `doc/01-jdbc/`, HTML jdbc section.

- [ ] 문서에 setter 우선/필드 fallback·이름 규칙·변환 계약 기록.
- [ ] snake_case/setter/타입 변환/실패 경계 테스트를 추가하고 실패 확인.
- [ ] 최소 구현 후 JDBC 테스트 실행.
- [ ] 해당 HTML 소개 갱신 후 커밋과 검증 결과 보고.

## Task 02 — Mapper (stage_02_mapper)

Files: `com/jorm/mapper/`, mapper tests, `doc/02-mapper/`, HTML mapper section.

- [ ] @Param/이름 바인딩/default 메서드 계약 문서화.
- [ ] 인자 순서·반복·누락·default 내부 SQL 호출 테스트 작성/실패 확인.
- [ ] 최소 구현 후 Mapper 테스트 실행.
- [ ] 해당 HTML 소개 갱신 후 커밋과 검증 결과 보고.

## Task 03 — Transactions (stage_03_transaction)

Files: `com/jorm/transaction/`, transaction tests, `doc/03-transaction/`, HTML transaction section.

Produces: `TransactionResource.default void beforeCommit() throws SQLException`.

- [ ] 선언적/명시적 예외 정책 구분과 beforeCommit 수명 계약 문서화.
- [ ] 예외 규칙·REQUIRED 참여·커밋 콜백 실패 롤백 테스트 작성/실패 확인.
- [ ] 최소 구현 후 transaction 테스트 실행.
- [ ] 해당 HTML 소개 갱신 후 커밋과 검증 결과 보고.

## Task 04 — Entity metadata (stage_04_entity)

Files: `EntityMetadata.java`, new `Transient.java`, entity metadata/CRUD tests, `doc/04-entity-crud/`, HTML entity section.

- [ ] 부모 필드/ID와 저장 제외 규칙 문서화.
- [ ] 상속 ID·transient 제외·이름 충돌 테스트 작성/실패 확인.
- [ ] 메타데이터 계층만 최소 수정 후 CRUD 회귀 검증.
- [ ] 해당 HTML 소개 갱신 후 커밋과 검증 결과 보고.

## Task 05 — Persistence context (stage_05_context)

Files: `EntityManager.java`, `EntityManagerSession.java`, `PersistenceContext.java`, `EntityManagerHolder.java`, entity context tests, `doc/05-persistence-context/`, HTML context section.

Consumes: 03 beforeCommit contract. Avoid changing EntityMetadata owned by 04.

- [ ] 스냅샷·변경 감지·flush와 즉시 CRUD 공존 계약 문서화.
- [ ] 동일성/자동·명시적 flush/스냅샷 갱신/롤백 테스트 작성·실패 확인.
- [ ] 최소 구현, `EntityManager.flush()` 노출 및 beforeCommit 연결.
- [ ] 해당 HTML 소개 갱신 후 커밋과 검증 결과 보고.

## Integration

- [ ] 추가 사용자 지시에 따라 전담 `orm_explainer_page`가 소개 HTML의 JORM 언급·자체 비교를 제거하고 실제 기술의 동작을 설명한다. 관련 Node/브라우저 테스트도 맞춘다.

- [ ] 각 담당 보고/커밋 검토 후 통합 워크트리에 순서대로 반영.
- [ ] 전역 README·진행 상황·기술 대응 문서를 실제 결과에 맞춘다.
- [ ] `.\gradlew.bat test`, `node --test doc/tests/learning-page.test.cjs`, HTML browser smoke 검증.
- [ ] 워크트리·브랜치·테스트 결과·남은 기술 차이를 사용자에게 보고.
