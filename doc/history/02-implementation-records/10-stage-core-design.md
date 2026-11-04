# 10. 단계별 핵심 기능 설계

작성일: 2026-11-04

> 단계별 핵심 기능 구현과 소개페이지 개편의 설계 기록이다. 최종 지원 범위와 검증은 [진행 상황](../../00-overview/01-progress.md)을 참고한다.

사용자 요청: 단계별 서브에이전트에게 별도 워크트리를 배정하고, 문서 수정/생성 → 테스트 수정/생성 → JORM 소개문서 수정 순서로 진행한다. 실제 기술과의 차이를 줄이는 기능을 구현하되 각 단계의 핵심만 구현한다. 구현은 테스트 이후, 소개문서 이전에 수행한다.

## 접근과 기준

전체 Spring/MyBatis/JPA 호환을 목표로 삼는 방식 대신 각 단계의 핵심 동작 하나씩을 보강한다. JDK/JDBC 기반 학습 프로젝트의 자체 API와 의존성을 유지한다. 기존 `codex/persistence-context-docs`의 객체 동일성·세션 수명 구현을 재사용하고 최신 main의 학습 HTML을 함께 포함한다. 이 범위 선택은 사용자의 '핵심만' 지시에 따른 구현자의 결정이다.

## 단계별 계약

1. JDBC: snake_case 컬럼명, JavaBean setter 우선/필드 fallback, 안전한 기본 타입 변환. 기존 단건 조회와 SQLException 계약은 유지한다.
2. Mapper: 자체 `@Param`과 `#{name}`의 PreparedStatement 바인딩, 기존 `?` 호환, 인터페이스 default 메서드 실행. XML/동적 SQL/캐시는 제외한다.
3. 트랜잭션: 선언적 rollbackFor/noRollbackFor와 RuntimeException·Error 기본 롤백. 명시적 execute는 기존 계약 유지. 영속성 컨텍스트 연동용 TransactionResource.beforeCommit 기본 메서드를 추가한다.
4. 엔티티: 부모 클래스 필드·ID 매핑과 자체 `@Transient`/Java transient 필드 제외. 중복·모호한 매핑은 SQL 실행 전에 거부한다. 관계·복합키는 제외한다.
5. 영속성 컨텍스트: 기존 객체 동일성에 스냅샷·변경 감지·명시적 flush·커밋 직전 자동 flush 추가. persist/update/remove의 기존 즉시 SQL API는 유지하고 변경 후 스냅샷을 동기화한다. 쓰기 지연 INSERT/DELETE는 제외한다.

## 통합과 책임

추가 사용자 지시: 소개페이지는 별도 서브에이전트가 맡아 JORM 언급과 자체 구현 비교를 제거하고 실제 Spring JDBC·MyBatis·Spring 트랜잭션·JPA/Hibernate의 동작을 설명한다. `doc/index.html`과 관련 페이지 테스트의 최종본은 소개페이지 전담자의 작업을 우선한다. 라이브러리 README와 단계별 구현 문서는 실제 저장소의 지원 범위를 계속 설명한다.

각 담당자는 자기 단계 코드·테스트·단계 문서를 수정한다. 01~03은 자기 단계 소개 영역 갱신까지 완료했고, 04~05의 소개 수정은 추가 사용자 지시에 따라 소개페이지 전담자에게 위임한다. 전역 README/진행 상황/대응표는 통합 시 실제 검증 결과에 맞춘다. 모든 구현을 별도 브랜치에 커밋하여 호출자에게 결과를 보고한다. 원본 main의 미커밋 동일성 테스트는 보존한다. main으로 병합하거나 원격으로 push하지 않는다.
