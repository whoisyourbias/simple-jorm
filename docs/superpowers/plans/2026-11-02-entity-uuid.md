# UUID 생성 전략 구현 계획

**Goal:** 자체 @GeneratedValue(strategy = GenerationType.UUID)로 UUID·String ID를 INSERT 전에 생성한다.
**Architecture:** 메타데이터가 전략별 타입을 검증한다. EntityManager는 UUID를 객체에 먼저 반영해 INSERT에 포함하고, IDENTITY만 ID 컬럼 제외·생성 키 회수 경로를 사용한다.
**Tech Stack:** Java 21, JDBC, JUnit 5, H2. 추가 의존성 없음.

## 계약
- UUID.randomUUID()로 v4 UUID 생성. String 필드는 표준 UUID 문자열을 저장한다.
- 기본 전략은 기존 IDENTITY 유지. 직접 지정 ID도 기존 동작 유지.
- UUID 전략은 UUID·String 타입만 허용하며 @Id가 아닌 필드와 이미 지정된 ID는 거부한다.
- INSERT 전에 ID를 반영하므로 SQL 실패·롤백 후에도 객체의 ID는 유지한다. 재시도 시 새 객체를 사용한다.
- H2 UUID·VARCHAR 컬럼으로 저장·독립 SELECT·find·트랜잭션 검증. 다른 DB에 대한 타입 변환 기능은 추가하지 않는다.

## 실행
- [x] UUID 생성·별칭·String·ID만 있는 엔티티·중복 저장·실패·롤백·타입 검증 테스트 추가, 실패 확인.
- [x] 전략별 메타데이터 검증과 EntityManager INSERT 분기 구현, 엔티티 테스트 확인.
- [x] 사용법·타입·실패 의미를 문서에 반영하고 전체 clean build 및 코드 리뷰.

## 검증 기록
- UUID 동작 구현 전 새 테스트 11개 중 8개가 미지원 타입·생성 로직 부족으로 실패했고, 기존 규칙 검증 3개는 통과했다.
- 엔티티 테스트 통과 후 `bash ./gradlew clean build`: 전체 117개, 실패·오류·스킵 0.
- 기본 main의 .idea 변경과 기존 worktree의 변경을 보존했다. 기존 내부 worktree와 codex/entity-identity 브랜치를 재사용했다.
