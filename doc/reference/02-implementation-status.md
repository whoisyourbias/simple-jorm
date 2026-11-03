# 구현 상세와 현재 제약

기준일: 2026-11-03. 단계별 요약은 [진행 상황](../00-overview/01-progress.md)을 참고한다.

## JDBC와 자동 매핑

- Java 21 / Gradle 기반 프로젝트다.
- `JdbcTemplate`은 파라미터 바인딩, JDBC 자원 정리, 단건 조회, 목록 조회와 데이터 변경을 담당한다.
- `queryForObject`는 결과가 없으면 null, 여러 행이면 첫 번째 행만 반환한다.
- `RowMapper<T>`는 조회 결과 한 행을 객체로 변환하는 콜백이다.
- `BeanPropertyRowMapper<T>`는 기본 생성자와 필드 리플렉션을 사용한다. 컬럼 라벨과 필드명을 대소문자 구분 없이 연결한다.
- 상속 필드, final 필드, record, 별도 타입 변환은 지원하지 않는다.
- 활성 트랜잭션 안에서는 같은 관리자의 연결을 공유하고 Statement·ResultSet만 닫는다. 밖에서는 호출별 연결을 획득·반환한다.
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

## 트랜잭션과 엔티티

- TransactionManager는 스레드별 연결과 rollbackOnly 원인을 관리한다. REQUIRED 참여자는 기존 연결을 사용하며 바깥 호출이 commit·rollback과 연결 정리를 담당한다.
- Service 프록시는 구현 메서드의 @Transactional을 검사한다. 자기 호출, 두 Service 간 참여, 예외 전파·삼킴 이후 전체 롤백을 테스트했다.
- EntityMetadata는 @Table·@Id·@Column, 단일 ID, 컬럼-필드 대응을 분석한다. static·synthetic 필드는 제외하고 final 필드·잘못된 선언은 거부한다.
- EntityManager는 클래스별 메타데이터를 캐시하고 persist에서 INSERT를 즉시 실행한다. 일반 ID는 직접 지정하고 @GeneratedValue ID는 INSERT에서 제외한 뒤 DB identity 키를 Long·Integer 필드에 반영한다. find는 메타데이터로 SELECT를 만들고 기본 생성자로 새 객체를 생성한다. 엔티티 UPDATE·DELETE·영속성 컨텍스트는 없다.
- 생성 ID 테스트 14개를 추가해 엔티티 테스트 40개를 포함한 전체 107개가 통과했다. [생성 ID 지원 범위와 실패·롤백 동작](../04-entity-crud/03-generated-id.md).

## 실제 기술과의 대응

JDBC·행 매핑은 Spring JDBC, Mapper 인터페이스·SQL 어노테이션은 MyBatis, 트랜잭션 계층은 Spring의 트랜잭션 관리, 엔티티 매핑·INSERT 생성은 Hibernate 같은 ORM의 일부 역할과 대응한다. JPA는 구현 라이브러리가 아닌 표준이며, 현재 EntityManager는 JPA 구현체가 아니다.

[단계별 비교·차이·공식 근거](03-learning-stage-mapping.md)와 [엔티티 구현 문서](../04-entity-crud/01-metadata-and-insert.md)를 참고한다.

## 다음 작업

1. 엔티티 UPDATE·DELETE를 추가한다.
2. 이후 영속성 컨텍스트의 객체 동일성·변경 감지·flush를 학습한다.

## 실험과 검증 기록

- 수동 매핑, simple-jorm, Spring JDBC, Commons DbUtils 비교 실험을 완료했다.
- 최종 원시 데이터와 환경 정보는 `doc/lab/results/2026-10-30-scaling-v2/`에 보관하며 예비 데이터와 구분한다.
- 이전 원격 저장 전 전체 테스트 재실행과 저장 관련 기록은 [당시 진행 기록](../history/01-before-mapper.md)에 보존한다.
- [실험 목록](../lab/README.md)에서 계획, 보고서, 재현 방법을 확인한다.
- 문서 정리 과정에서는 성능 실험을 재실행하지 않았다.
