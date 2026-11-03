# 00-01. 현재 진행 상황

기준일: 2026-11-03

**현재: 엔티티 메타데이터, ID 직접 지정 INSERT, ID 기반 find, IDENTITY·UUID 생성 ID 반영 구현 완료. 다음: 엔티티 UPDATE·DELETE.**

## 단계별 현황

| 단계 | 구현 범위 | 상태 |
|---|---|---|
| 01 JDBC | SQL 실행·바인딩·자원 정리, 수동·자동 행 매핑 | 완료 |
| 02 Mapper | @Select, 프록시 생성, 핸들러, Object 메서드 처리 | 완료 |
| 02 Mapper | 단건·목록 조회, INSERT·UPDATE·DELETE, 인자 전달 | 완료 |
| 03 트랜잭션 | Connection 공유, commit·rollback, Service 프록시, REQUIRED·rollbackOnly | 완료 |
| 04 엔티티 CRUD | @Table·@Id·@Column, 메타데이터 캐시, INSERT·persist, ID 조회·find | 완료 |
| 04 엔티티 CRUD | IDENTITY 키 회수·UUID 생성 및 객체 반영 | 완료 |
| 04 엔티티 CRUD | 엔티티 UPDATE·DELETE | 미구현 |
| 05 영속성 컨텍스트 | 객체 동일성, 변경 감지 | 미구현 |

## 현재 동작과 검증

- JDBC: 단건·목록 조회와 데이터 변경 지원. 단건 조회는 결과가 없으면 null, 여러 행이면 첫 행 반환.
- Mapper: @Select로 단건·목록 조회, @Insert·@Update·@Delete로 변경 후 영향받은 행 수 반환.
- 트랜잭션: 같은 스레드·관리자의 연결 공유, Service 프록시, REQUIRED 참여와 rollbackOnly 지원.
- 엔티티: 직접 정의한 어노테이션을 분석해 INSERT를 즉시 실행한다. ID는 직접 지정하거나 IDENTITY·UUID로 생성하며 변경 행 수를 반환한다. find는 ID 조건으로 조회해 새 객체에 필드를 채우고, 결과가 없으면 null을 반환한다.
- 검증: 엔티티 테스트 61개를 포함한 전체 131개 테스트 통과 (`.\gradlew.bat test --rerun-tasks`).
- 제약: Mapper의 생성 ID 반환·default 메서드 실행은 미지원. 엔티티 생성 ID는 IDENTITY·UUID 지원. 변경 메서드는 int만 지원.

## 다음 작업

1. 엔티티 UPDATE·DELETE 확장.

## 구현 단계가 대응하는 기술

01은 Spring JDBC식 실행·행 매핑, 02는 MyBatis식 SQL Mapper, 03은 Spring식 트랜잭션 관리, 04는 Hibernate 같은 ORM의 엔티티 매핑·SQL 생성 개념을 학습한다. 05는 JPA 영속성 컨텍스트를 다룰 예정이며 미구현이다.

[단계별 대응표와 실제 기술과의 차이](../reference/03-learning-stage-mapping.md)를 참고한다. JPA는 명세이고, 현재 simple-jorm은 그 표준 구현체가 아니다.

[Connection 공유와 트랜잭션](../03-transaction/01-connection-and-transaction.md)에 사용 예제와 제약을 정리했다.

## 상세 문서

- [01 JDBC 실행과 매핑](../01-jdbc/01-jdbc-and-row-mapping.md)
- [02 Mapper 실행 흐름](../02-mapper/01-proxy-execution.md) · [테스트와 다음 작업](../02-mapper/02-tests-and-next-steps.md)
- [04 엔티티 매핑과 INSERT](../04-entity-crud/01-metadata-and-insert.md) · [ID 조회](../04-entity-crud/02-find-by-id.md)
- [별도 성능 실험](../lab/README.md)

## 상세 기록

- [구현 상세와 제약](../reference/02-implementation-status.md)
- [Mapper 구현 전 진행 기록](../history/01-before-mapper.md): 이전 검증·실험·저장 기록 보존.

- [IDENTITY 생성 ID](../04-entity-crud/03-identity-generated-id.md)
- [UUID 생성 ID](../04-entity-crud/04-uuid-generated-id.md)
