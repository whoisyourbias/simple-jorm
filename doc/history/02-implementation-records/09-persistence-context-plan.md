# 09. 영속성 컨텍스트 구현 계획

작성일: 2026-11-03

> 객체 동일성과 세션 수명을 처음 구현할 당시의 계획 기록이다. 이후 추가된 변경 감지·flush를 포함한 최신 계약은 [영속성 컨텍스트 문서](../../05-persistence-context/02-implementation-checkpoints.md)를 참고한다.

**목표:** 같은 트랜잭션의 실제 매니저 세션을 재사용하고 객체 동일성을 보장한다.

**구조:** 기존 EntityManager는 위임 진입점이다. EntityManagerSession이 컨텍스트를 소유하고 TransactionManager는 범용 자원으로 holder를 연결한다.

**사용 기술:** Java 21, JDBC, JUnit 5, H2, Gradle.

**설계:** [트랜잭션별 EntityManager 세션·영속성 컨텍스트](08-persistence-context-design.md)

## 공통 제약

- 기존 생성자·CRUD 반환 계약과 트랜잭션 밖의 동작을 유지한다.
- 트랜잭션 계층에서 엔티티 계층을 참조하지 않는다.
- 캐싱 대상은 Long·Integer·String·UUID와 long·int ID다.
- 즉시 SQL 실행을 유지하며 변경 감지·flush는 후속 단계다.

## 검토 항목

- 정리 실패가 원래 예외를 덮거나 다른 자원 정리를 막는 상황.
- 종료 콜백이 이미 종료된 트랜잭션에서 SQL을 실행하는 상황.
- 같은 관리자의 여러 진입점·템플릿에서 세션이 분리되는 상황.
- 생성 ID 실패·명시적 수정·삭제와 캐시의 불일치.
- 가변 ID·지원 범위 밖 ID가 캐시 키를 손상하는 상황.

## 작업 1. 트랜잭션 자원 수명

Files: TransactionManager.java, TransactionResource.java, TransactionResourceTest.java.
Interfaces: getOrCreateResource(Object, Supplier<R>), markRollbackOnly(Throwable), TransactionResource.afterCompletion().

- [x] 자원 재사용·REQUIRED·종료·종료 재진입·원래 실패 보존 테스트를 작성하고 실패 확인.
- [x] ACTIVE·COMPLETING 상태와 범용 자원 저장소, 모든 종료 경로의 정리 구현.
- [x] 해당 테스트와 기존 TransactionManagerTest 실행.

## 작업 2. 세션과 객체 관리

Files: EntityManager.java, EntityManagerSession.java, EntityManagerHolder.java, PersistenceContext.java, EntityKey.java, JdbcTemplate.java, EntityManagerFindTest.java, EntityManagerPersistenceContextTest.java.
Interfaces: 기존 CRUD, JdbcTemplate.getTransactionManager(), 세션 close와 컨텍스트 등록·조회·제거.

- [x] 사용자의 동일성 테스트를 가져와 현재 실패 확인.
- [x] 조회·키 변환·다중 진입점·수명 격리·CRUD 캐시 일관성 테스트 작성.
- [x] 기존 CRUD를 세션으로 옮기고 공유 진입점의 세션 조회·위임 구현.
- [x] 키별 관리와 객체 참조별 ID 변경 검사 구현.
- [x] 엔티티 테스트 실행.

## 작업 3. 검증과 문서

- [x] 전체 Gradle 테스트 실행과 실패 경로·스레드 격리 검증.
- [x] 실제 구현에 맞춰 학습 문서와 진행 상황 갱신.
- [x] 새 검토자에게 전체 변경 검토를 맡기고 중요한 발견 수정 후 검증.
- [x] git diff --check와 최종 변경 범위 확인.

## Execution ledger

- 작업 위치는 기존 codex/persistence-context-docs worktree다.
- 기본 작업 폴더의 사용자 테스트는 복사하여 보존한다.
- 설계에서 승인한 순서대로 직접 구현한다. 커밋·병합은 별도 완료 작업으로 남긴다.

- 검증 결과는 전체 177개 테스트 통과, 실패·오류·스킵 0개다.
- 검토에서 발견한 INSERT 후 등록 실패의 rollbackOnly 누락은 회귀 테스트로 재현 후 수정했다.
