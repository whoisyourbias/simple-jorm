# 00-01. 현재 진행 상황

기준일: 2026-10-30

**현재: Mapper 기본 CRUD 구현·테스트 완료. 다음: 트랜잭션 관리.**

## 단계별 현황

| 단계 | 구현 범위 | 상태 |
|---|---|---|
| 01 JDBC | SQL 실행·바인딩·자원 정리, 수동·자동 행 매핑 | 완료 |
| 02 Mapper | @Select, 프록시 생성, 핸들러, Object 메서드 처리 | 완료 |
| 02 Mapper | 단건·목록 조회, INSERT·UPDATE·DELETE, 인자 전달 | 완료 |
| 03 트랜잭션 | Connection 공유, commit·rollback | 미구현 |
| 04 엔티티 CRUD | 테이블·ID 매핑과 SQL 생성 | 미구현 |
| 05 영속성 컨텍스트 | 객체 동일성, 변경 감지 | 미구현 |

## 현재 동작과 검증

- JDBC: 단건·목록 조회와 데이터 변경 지원. 단건 조회는 결과가 없으면 null, 여러 행이면 첫 행 반환.
- Mapper: @Select로 단건·목록 조회, @Insert·@Update·@Delete로 변경 후 영향받은 행 수 반환.
- 검증: Mapper 테스트 20개 추가 후 `gradlew.bat test` 전체 통과.
- 제약: 트랜잭션 공유, 생성 ID 반환·default 메서드 실행은 미지원. 변경 메서드는 int만 지원.

## 다음 작업

1. 트랜잭션 관리자로 Connection 공유와 commit·rollback 구현.
2. 여러 Mapper 호출의 일괄 커밋·롤백 검증.
3. Service 트랜잭션 프록시 연결.

## 상세 문서

- [01 JDBC 실행과 매핑](../01-jdbc/01-jdbc-and-row-mapping.md)
- [02 Mapper 실행 흐름](../02-mapper/01-proxy-execution.md) · [테스트와 다음 작업](../02-mapper/02-tests-and-next-steps.md)
- [별도 성능 실험](../lab/README.md)

## 상세 기록

- [구현 상세와 제약](../reference/02-implementation-status.md)
- [Mapper 구현 전 진행 기록](../history/01-before-mapper.md): 이전 검증·실험·저장 기록 보존.
