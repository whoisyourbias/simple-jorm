# 01. IDENTITY 생성 ID 구현 계획

작성일: 2026-11-02

> 구현 당시의 계획 기록이다. 범위·체크리스트·검증 수치는 당시 기준이며 최신 상태는 [진행 상황](../../00-overview/01-progress.md)을 참고한다.

**목표:** DB가 생성한 IDENTITY 키를 persist 호출 중 엔티티에 반영한다.
**구조:** EntityMetadata가 생성 ID 선언을 검증하고, EntityManager가 ID를 제외한 INSERT를 생성한다. JdbcTemplate이 같은 트랜잭션 연결에서 지정 ID 컬럼의 생성 키를 읽는다.
**사용 기술:** Java 21, JDBC, JUnit 5, H2. 추가 의존성 없음.

## 계약과 범위
- 자체 @GeneratedValue(strategy = GenerationType.IDENTITY), 기본 전략도 IDENTITY. 다른 전략은 제공하지 않는다.
- @Id인 Long 또는 Integer 필드에만 사용하며 초기값은 null이어야 한다.
- persist는 기존과 같이 성공 시 변경 행 수 1을 반환한다. 직접 지정 ID는 기존 동작 유지.
- ID만 있는 엔티티는 INSERT DEFAULT VALUES를 사용한다.
- 롤백은 DB 행을 제거하지만 이미 객체에 반영된 ID를 초기화하지 않는다.
- DB 스키마의 IDENTITY 설정은 호출자 책임이다. H2로 검증하고 MySQL 실서버 검증은 하지 않는다.

## 실행
- [x] EntityManagerIdentityTest에 키 반영, 서로 다른 키, ID만 있는 엔티티, Integer, 롤백, 실패·잘못된 선언 검증을 작성하고 미구현 실패 확인.
- [x] GeneratedValue.java / GenerationType.java, EntityMetadata.java, JdbcTemplate.java, EntityManager.java 구현.
- [x] 엔티티 테스트 및 전체 build 실행. 생성 키 미반환·반환 타입 실패와 기존 직접 지정 ID 경로 검토.
- [x] README, 진행 현황, 구현 상세와 학습 문서 갱신 후 diff 확인.

## 검증 결과
- 새 엔티티 테스트 10개가 미구현 상태에서 실패함을 확인한 뒤 구현했다.
- 엔티티 테스트 통과 후 JDBC 키 오류 테스트 3개를 추가했다.
- `bash ./gradlew clean build`: 전체 106개, 실패·오류·스킵 0.
- 독립 코드 리뷰: 중요 오류 없음. 키 ResultSet 빈 결과·복수 결과 및 자원 종료를 직접 검증하는 테스트는 추가하지 않았다.
- 기본 main의 .idea 미커밋 추가 5개는 보존했다. 기존 내부 worktree를 재사용했다.
