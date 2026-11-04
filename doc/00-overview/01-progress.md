# 00-01. 현재 진행 상황

기준일: 2026-11-04

**현재: 단계별 핵심 기능과 영속성 컨텍스트의 객체 동일성·변경 감지·flush 구현 완료. 실제 기술과의 남는 차이는 단계별 문서에 명시했다.**

## 단계별 현황

| 단계 | 구현 범위 | 상태 |
|---|---|---|
| 01 JDBC | SQL 실행·바인딩·자원 정리, 수동·자동 행 매핑 | 완료 |
| 01 JDBC | setter 우선·필드 fallback, snake_case, 기본 타입 변환·모호성 거부 | 완료 |
| 02 Mapper | @Select, 프록시 생성, 핸들러, Object 메서드 처리 | 완료 |
| 02 Mapper | 단건·목록 조회, INSERT·UPDATE·DELETE, 인자 전달 | 완료 |
| 02 Mapper | @Param·이름 바인딩, default 메서드 실행 | 완료 |
| 03 트랜잭션 | Connection 공유, commit·rollback, Service 프록시, REQUIRED·rollbackOnly | 완료 |
| 03 트랜잭션 | 선언적 롤백 규칙, 커밋 전 동기화 콜백 | 완료 |
| 04 엔티티 CRUD | @Table·@Id·@Column, 메타데이터 캐시, INSERT·persist, ID 조회·find | 완료 |
| 04 엔티티 CRUD | IDENTITY 키 회수·UUID 생성 및 객체 반영 | 완료 |
| 04 엔티티 CRUD | 엔티티 UPDATE·DELETE | 완료 |
| 04 엔티티 CRUD | 부모 필드·ID, Java transient·@Transient 제외 | 완료 |
| 05 영속성 컨텍스트 | 객체 동일성, 세션 연결·재사용·정리, CRUD 캐시 일관성 | 완료 |
| 05 영속성 컨텍스트 | 스냅샷, 변경 감지, 명시적·커밋 직전 flush | 완료 |

## 현재 동작과 검증

- JDBC: setter 우선 자동 매핑·snake_case·기본 변환 지원. 단건 조회는 0행 null·여러 행 첫 행 반환을 유지한다.
- Mapper: 자체 @Param·#{name}과 기존 ? 바인딩을 지원한다. default 본문·내부 Mapper 호출도 실행한다. 변경 SQL은 int 행 수를 반환한다.
- 트랜잭션: REQUIRED·rollbackOnly·beforeCommit 지원. 선언적 기본은 RuntimeException·Error 롤백이며 checked는 커밋 후 재전파한다. rollbackFor/noRollbackFor로 조절하고 명시적 execute의 SQLException 롤백은 유지한다.
- 엔티티: 직접 정의한 어노테이션을 분석해 INSERT를 즉시 실행한다. ID는 직접 지정하거나 IDENTITY·UUID로 생성하며 변경 행 수를 반환한다. find는 트랜잭션별 세션의 관리 객체를 우선 반환한다. 없으면 ID 조건으로 조회해 등록하고, 결과가 없으면 null을 반환한다. Long·Integer·String·UUID와 long·int ID를 캐싱한다.
- 엔티티 변경·삭제: update는 ID 외 모든 필드를, remove는 ID 조건의 행을 즉시 변경·삭제한다. 변경 행 수를 반환하며 대상이 없으면 0이다. null 객체·null ID와 ID 전용 타입의 update는 거부한다.
- 매핑: 부모 필드·ID를 구체 클래스 한 테이블에 매핑하고 Java transient·자체 @Transient를 제외한다. 계층의 중복·숨김 매핑을 SQL 전에 거부한다.
- 변경 감지: 관리 시작 시 스냅샷을 만들고 명시적 flush·바깥 커밋 직전 변경 객체만 UPDATE한다. byte[]·JDBC 날짜/시간 내부 변경을 추적하며 실패한 SQL은 rollbackOnly를 유지한다. flush는 commit과 다르다.
- 통합 검증(2026-11-04): `.\gradlew.bat test`로 Java 265개 통과, 실패·스킵 0. 단계별 별도 워크트리의 커밋을 통합하고 독립 리뷰에서 발견한 setter 이름 충돌도 회귀 테스트와 함께 수정했다.
- 소개페이지: 전담 서브에이전트가 프로젝트명·자체 구현 비교를 제거하고 실제 기술의 동작으로 갱신했다. `node --test doc/tests/learning-page.test.cjs` 10개와 Chrome 브라우저 검증 15개 통과. 공식 자료와 대조한 독립 리뷰에서 중요한 설명 오류가 없음을 확인했다.
- 제약: Mapper 생성 ID 반환·XML·동적 SQL, JPA 상속 전략·관계·복합 키, 쓰기 지연·조회 전 자동 flush·낙관적 잠금은 제외한다.

## 다음 작업

이번 요청의 핵심 구현을 마쳤다. [구현 계약과 검증 항목](../05-persistence-context/02-implementation-checkpoints.md)과 각 단계의 남은 차이를 기준으로 후속 학습 범위를 정한다.

## 구현 단계가 대응하는 기술

01은 Spring JDBC식 실행·행 매핑, 02는 MyBatis식 SQL Mapper, 03은 Spring식 트랜잭션 관리, 04는 Hibernate 같은 ORM의 엔티티 매핑·SQL 생성 개념을 학습한다. 05는 객체 동일성·세션 수명·변경 감지·flush를 다룬다. 소개 HTML은 실제 라이브러리의 동작을 설명하며, 이 저장소 구현 범위는 단계별 문서로 구분한다.

[단계별 대응표와 실제 기술과의 차이](../reference/03-learning-stage-mapping.md)를 참고한다. JPA는 명세이고, 현재 simple-jorm은 그 표준 구현체가 아니다.

[Connection 공유와 트랜잭션](../03-transaction/01-connection-and-transaction.md)에 사용 예제와 제약을 정리했다.

## 상세 문서

- [01 JDBC 실행과 매핑](../01-jdbc/01-jdbc-and-row-mapping.md)
- [02 Mapper 실행 흐름](../02-mapper/01-proxy-execution.md) · [테스트와 다음 작업](../02-mapper/02-tests-and-next-steps.md)
- [04 엔티티 매핑과 INSERT](../04-entity-crud/01-metadata-and-insert.md) · [ID 조회](../04-entity-crud/02-find-by-id.md)
- [05 영속성 컨텍스트와 생명주기](../05-persistence-context/01-concepts-and-lifecycle.md) · [구현 준비와 검증 항목](../05-persistence-context/02-implementation-checkpoints.md)
- [별도 성능 실험](../lab/README.md)

## 상세 기록

- [구현 상세와 제약](../reference/02-implementation-status.md)
- [Mapper 구현 전 진행 기록](../history/01-before-mapper.md): 이전 검증·실험·저장 기록 보존.

- [IDENTITY 생성 ID](../04-entity-crud/03-identity-generated-id.md)
- [UUID 생성 ID](../04-entity-crud/04-uuid-generated-id.md)
- [엔티티 UPDATE·DELETE](../04-entity-crud/05-update-and-remove.md)
