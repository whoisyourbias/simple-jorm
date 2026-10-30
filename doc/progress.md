# simple-jorm 진행 상황

저장일: 2026-10-30 (한국 시간)

## 현재까지 구현한 내용

- Java 21 / Gradle 기반 프로젝트 구성.
- `RowMapper<T>`: 조회 결과 한 행을 객체로 변환하는 콜백.
- `JdbcTemplate`: 파라미터 바인딩, JDBC 자원 정리, 단건 조회(`queryForObject`), 목록 조회(`query`), 데이터 변경(`update`).
- `queryForObject`는 결과가 없으면 `null`, 여러 행이면 첫 번째 행을 반환하도록 구현되어 있음.
- `BeanPropertyRowMapper<T>`: 기본 생성자와 필드를 사용한 리플렉션 자동 매핑. 컬럼 라벨과 필드명을 대소문자 구분 없이 연결.
- H2 기반 JDBC 실행·매핑 테스트 작성.

## 완료된 학습 및 실험 기록

- [Java ORM과 데이터 접근 생태계](java-orm-ecosystem.md): 기술별 역할과 프로젝트 학습 순서 정리.
- [행 수·필드 수에 따른 JDBC 매핑 비용 실험](lab/2026-10-30-mapping-scaling.md): 수동 매핑, simple-jorm, Spring JDBC, Commons DbUtils 비교.
- 실험 계획서에는 전체 측정과 기존 테스트 실행이 완료된 것으로 기록되어 있음. 원시 데이터와 환경 정보는 `doc/lab/results/2026-10-30-scaling-v2/`에 보관.
- 원격 저장 전 `gradlew.bat test --rerun-tasks`를 실행하여 BUILD SUCCESSFUL을 확인함. 성능 실험은 다시 실행하지 않음.

## 다음 작업 제안

다음 학습 단계는 `@Select` 애너테이션과 Mapper 프록시 구현. 아직 구현을 시작하지 않았으며, 다음 순서는 제안 상태임.

1. 메서드에 SQL을 지정하는 `@Select` 정의.
2. Mapper 인터페이스의 프록시 생성.
3. 메서드 인자를 SQL의 `?`에 순서대로 바인딩.
4. 기존 `JdbcTemplate`과 자동 매퍼를 연결해 단건 객체 반환.
5. H2 테스트로 호출부터 조회 결과 반환까지 검증.
6. 이후 `List<T>` 반환 지원으로 확장.

이후 학습 순서: 트랜잭션 관리자 → 엔티티 기반 CRUD → 영속성 컨텍스트와 변경 감지.

## 현재 제약 및 저장 상태

- 자동 매퍼는 상속 필드, final 필드, record, 별도 타입 변환을 지원하지 않음.
- 각 `JdbcTemplate` 호출은 연결을 개별 획득·반환하며, 여러 호출이 공유하는 트랜잭션 관리자는 아직 없음.
- 최초 진행 상황 저장 시 프로젝트 파일들은 미추적 상태였음. 이후 사용자 요청에 따라 구현 코드, 테스트, 실험 결과 및 이 문서를 첫 커밋에 포함하여 `origin/main`에 푸시하는 대상으로 정리함.
