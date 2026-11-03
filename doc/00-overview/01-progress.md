# 00-01. 현재 진행 상황

기준일: 2026-11-03

**현재: 영속성 컨텍스트의 객체 동일성·트랜잭션별 세션 수명 관리 구현 완료. 다음: 스냅샷·변경 감지·flush.**

## 단계별 현황

| 단계 | 구현 범위 | 상태 |
|---|---|---|
| 01 JDBC | SQL 실행·바인딩·자원 정리, 수동·자동 행 매핑 | 완료 |
| 02 Mapper | @Select, 프록시 생성, 핸들러, Object 메서드 처리 | 완료 |
| 02 Mapper | 단건·목록 조회, INSERT·UPDATE·DELETE, 인자 전달 | 완료 |
| 03 트랜잭션 | Connection 공유, commit·rollback, Service 프록시, REQUIRED·rollbackOnly | 완료 |
| 04 엔티티 CRUD | @Table·@Id·@Column, 메타데이터 캐시, INSERT·persist, ID 조회·find | 완료 |
| 04 엔티티 CRUD | IDENTITY 키 회수·UUID 생성 및 객체 반영 | 완료 |
| 04 엔티티 CRUD | 엔티티 UPDATE·DELETE | 완료 |
| 05 영속성 컨텍스트 | 객체 동일성, 세션 연결·재사용·정리, CRUD 캐시 일관성 | 완료 |
| 05 영속성 컨텍스트 | 스냅샷, 변경 감지, flush | 미구현 |

## 현재 동작과 검증

- JDBC: 단건·목록 조회와 데이터 변경 지원. 단건 조회는 결과가 없으면 null, 여러 행이면 첫 행 반환.
- Mapper: @Select로 단건·목록 조회, @Insert·@Update·@Delete로 변경 후 영향받은 행 수 반환.
- 트랜잭션: 같은 스레드·관리자의 연결 공유, Service 프록시, REQUIRED 참여와 rollbackOnly 지원.
- 엔티티: 직접 정의한 어노테이션을 분석해 INSERT를 즉시 실행한다. ID는 직접 지정하거나 IDENTITY·UUID로 생성하며 변경 행 수를 반환한다. find는 트랜잭션별 세션의 관리 객체를 우선 반환한다. 없으면 ID 조건으로 조회해 등록하고, 결과가 없으면 null을 반환한다. Long·Integer·String·UUID와 long·int ID를 캐싱한다.
- 엔티티 변경·삭제: update는 ID 외 모든 필드를, remove는 ID 조건의 행을 즉시 변경·삭제한다. 변경 행 수를 반환하며 대상이 없으면 0이다. null 객체·null ID와 ID 전용 타입의 update는 거부한다.
- 검증: 엔티티 테스트 97개를 포함한 전체 177개 테스트 통과 (`.\gradlew.bat test`).
- 제약: Mapper의 생성 ID 반환·default 메서드 실행은 미지원. 엔티티 생성 ID는 IDENTITY·UUID 지원. 변경 메서드는 int만 지원.

## 다음 작업

1. [현재 구현과 검증 항목](../05-persistence-context/02-implementation-checkpoints.md)을 바탕으로 스냅샷·변경 감지·flush를 설계한다.

## 구현 단계가 대응하는 기술

01은 Spring JDBC식 실행·행 매핑, 02는 MyBatis식 SQL Mapper, 03은 Spring식 트랜잭션 관리, 04는 Hibernate 같은 ORM의 엔티티 매핑·SQL 생성 개념을 학습한다. 05는 영속성 컨텍스트의 객체 동일성과 세션 수명을 구현했고, 변경 감지·flush를 이어서 다룬다.

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
