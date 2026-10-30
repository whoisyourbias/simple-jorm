# 구현 상세와 현재 제약

기준일: 2026-10-30. 단계별 요약은 [진행 상황](../00-overview/01-progress.md)을 참고한다.

## JDBC와 자동 매핑

- Java 21 / Gradle 기반 프로젝트다.
- `JdbcTemplate`은 파라미터 바인딩, JDBC 자원 정리, 단건 조회, 목록 조회와 데이터 변경을 담당한다.
- `queryForObject`는 결과가 없으면 null, 여러 행이면 첫 번째 행만 반환한다.
- `RowMapper<T>`는 조회 결과 한 행을 객체로 변환하는 콜백이다.
- `BeanPropertyRowMapper<T>`는 기본 생성자와 필드 리플렉션을 사용한다. 컬럼 라벨과 필드명을 대소문자 구분 없이 연결한다.
- 상속 필드, final 필드, record, 별도 타입 변환은 지원하지 않는다.
- 각 JDBC 호출이 Connection을 개별 획득·반환한다. 여러 호출이 공유하는 트랜잭션 관리자는 없다.
- H2 기반 실행·매핑 테스트가 있다. [JDBC 상세](../01-jdbc/01-jdbc-and-row-mapping.md).

## Mapper 프록시

- `@Select`는 METHOD 대상, RUNTIME 보존 정책으로 SQL 문자열을 보관한다.
- `MapperFactory`는 인터페이스를 검사하고 JDK 동적 프록시를 생성한다.
- `MapperInvocationHandler`는 Object 메서드 처리, 인자 바인딩, SQL 실행과 결과 매핑을 담당한다.
- @Select는 단건 객체와 List<T>를 반환한다. 결과가 없으면 각각 null과 빈 리스트다.
- @Insert·@Update·@Delete는 영향받은 행 수를 int로 반환한다. SQL 어노테이션 중복과 잘못된 변경 반환 타입은 실행 전에 거부한다.
- 생성 ID 반환, default 메서드 실행, 컴파일 타임 어노테이션 검사는 지원하지 않는다. 조회는 자동 매핑 가능한 객체 또는 List<T>만 지원한다.
- `MapperFactoryTest`의 20개 테스트를 포함한 전체 테스트를 검증한다.
- [실행 흐름](../02-mapper/01-proxy-execution.md)과 [테스트 목록](../02-mapper/02-tests-and-next-steps.md).

## 다음 작업

1. 트랜잭션 관리자로 현재 스레드의 Connection을 공유한다.
2. 여러 Mapper 호출을 하나의 commit·rollback 범위로 묶고 H2로 검증한다.
3. Service 프록시와 연결한 뒤 엔티티 CRUD·영속성 컨텍스트로 확장한다.

## 실험과 검증 기록

- 수동 매핑, simple-jorm, Spring JDBC, Commons DbUtils 비교 실험을 완료했다.
- 최종 원시 데이터와 환경 정보는 `doc/lab/results/2026-10-30-scaling-v2/`에 보관하며 예비 데이터와 구분한다.
- 이전 원격 저장 전 전체 테스트 재실행과 저장 관련 기록은 [당시 진행 기록](../history/01-before-mapper.md)에 보존한다.
- [실험 목록](../lab/README.md)에서 계획, 보고서, 재현 방법을 확인한다.
- 문서 정리 과정에서는 성능 실험을 재실행하지 않았다.
