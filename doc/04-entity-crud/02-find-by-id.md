# 04-02. ID 조회와 객체 생성

기준일: 2026-11-02

이 문서는 Stage 4 당시의 SQL 조회·객체 생성 과정을 설명한다. 현재 Stage 5에서는 트랜잭션별 관리 객체 재사용을 추가했다. 최신 호출 흐름과 제약은 [영속성 컨텍스트 구현](../05-persistence-context/02-implementation-checkpoints.md)을 참고한다.

```java
User user = entityManager.find(User.class, 1L);
```

JPA EntityManager.find처럼 클래스와 ID로 조회하는 진입 형태를 학습한다. 아래 실행 순서는 관리 객체가 없을 때의 SQL 조회 경로다. 현재는 같은 관리자·트랜잭션의 관리 객체를 우선 반환하며, 트랜잭션 밖과 캐싱 대상 외 ID 타입은 새로 조회한다. [단계별 기술 대응](../reference/03-learning-stage-mapping.md)을 참고한다.

## 실행 순서

1. null ID를 거부하고 캐시에서 EntityMetadata를 가져온다.
2. 구체적인 클래스인지, 접근할 수 있는 인자 없는 생성자가 있는지 검사한다. 결과 행이 없어도 잘못된 조회 타입은 거부한다.
3. @Id 필드에 연결된 실제 DB 컬럼명을 찾는다. `id`에 `@Column("user_id")`가 있으면 조건은 `WHERE user_id = ?`다.
4. 메타데이터 컬럼 순서대로 SELECT를 만들고 같은 순서의 Field 목록을 준비한다.
5. JdbcTemplate.queryForObject가 ID를 바인딩하고 현재 트랜잭션 연결 또는 임시 연결로 SQL을 실행한다.
6. 결과가 없으면 null, 있으면 기본 생성자로 객체를 만든다. private 생성자도 접근 권한을 확보할 수 있으면 사용한다.
7. 조회 컬럼 위치별 값을 대응 필드에 저장한다. 생성자·필드 매핑 실패는 원인을 담은 SQLException으로 전달한다.

## persist와의 차이

persist는 이미 존재하는 객체를 읽으므로 기본 생성자가 필요 없다. find는 새 객체를 만들어야 하므로 기본 생성자가 필요하다. 이 검사는 find에서만 수행해 기존 persist 지원 범위를 유지한다.

BeanPropertyRowMapper는 컬럼 라벨을 setter·필드에 연결하지만 @Column을 해석하지 않는다. find는 EntityMetadata를 사용하므로 DB 컬럼명과 필드명이 달라도 매핑할 수 있다. DB에 엔티티로 매핑하지 않은 컬럼이 있어도 명시한 SELECT 컬럼만 읽는다.

## 지원 범위와 검증

- null ID·추상 타입·기본 생성자 없는 타입은 IllegalArgumentException이다.
- SQL 실행 오류, 생성자 실행 오류, 필드 타입 불일치는 SQLException으로 전달한다.
- 부모의 private 필드와 ID도 같은 메타데이터로 조회한다. Java `transient`·자체 `@Transient` 필드는 SELECT에 포함하지 않으므로 새 객체의 초기값을 유지한다. [부모 필드와 저장 제외 선언](06-inherited-and-transient-fields.md)을 참고한다.
- 별도 값 타입 변환, 복합 ID, 관계 매핑, JPA 상속 전략은 지원하지 않는다.
- 같은 관리자의 트랜잭션 안에서 persist한 미커밋 행을 find할 수 있다. 롤백 후에는 해당 행이 없다.
- 클래스 구조와 트랜잭션별 관리 객체를 캐시한다. 같은 트랜잭션의 지원 ID는 같은 객체를 반환하므로 직접 JDBC·Mapper로 수정한 DB 값을 자동으로 다시 읽지는 않는다.

[EntityManagerFindTest](../../src/test/java/com/jorm/entity/EntityManagerFindTest.java)의 13개 테스트와 영속성 컨텍스트 테스트로 검증한다. find 추가 당시 전체 테스트는 93개였으며, 최신 통합 검증 결과는 [진행 상황](../00-overview/01-progress.md)을 참고한다.

```powershell
.\gradlew.bat test --tests com.jorm.entity.EntityManagerFindTest
.\gradlew.bat test
```

코드: [EntityManager.find](../../src/main/java/com/jorm/entity/EntityManager.java). 후속 구현은 [IDENTITY 생성 ID](03-identity-generated-id.md), [엔티티 UPDATE·DELETE](05-update-and-remove.md)를 참고한다.
