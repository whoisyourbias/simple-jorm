# 05. 엔티티 update·remove 설계

작성일: 2026-11-03

> 구현 당시의 설계 기록이다. 당시 선택과 제외 범위를 보존하며 최신 상태는 [진행 상황](../../00-overview/01-progress.md)을 참고한다.

요청은 EntityManager.update(Object)와 remove(Object) 구현이다. 기존 메타데이터와 JdbcTemplate.update를 사용해 호출 즉시 SQL을 실행하고 int 변경 행 수를 반환한다. 대상이 없으면 0이다.

update는 @Id 외 모든 매핑 필드를 SET에 넣고 실제 ID 컬럼을 WHERE에 사용한다. remove는 ID 조건의 DELETE다. null 엔티티·null ID는 IllegalArgumentException이며, ID만 있는 엔티티의 update도 거부한다. remove는 ID만 있어도 가능하다. 객체의 ID·필드는 두 메서드가 바꾸지 않는다.

직접 지정 ID와 IDENTITY·UUID 생성 ID에 같은 규칙을 적용한다. 기본 생성자는 필요 없다. SQL 오류는 SQLException으로 전달하고 기존 관리자의 트랜잭션을 공유한다. 롤백은 객체 값을 복원하지 않는다. ID 변경 추적, 부분 수정, 변경 감지, 영속성 컨텍스트는 범위 밖이다.

SQL을 직접 받는 별도 Mapper 경로 대신 기존 엔티티 메타데이터를 사용한다. 자동 변경 감지는 아직 상태 관리 계층이 없어 후속 단계로 유지한다.
