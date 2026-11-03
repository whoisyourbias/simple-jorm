# 04-01. 엔티티 메타데이터와 INSERT 자동 생성

기준일: 2026-11-03. 메타데이터 분석, 직접 지정·IDENTITY·UUID ID를 지원하는 `persist()`, ID 조회 `find()`와 명시적 `update()`·`remove()`를 구현했다. 이 문서는 INSERT 경로를 설명한다.

## 실제 기술과의 연결

이 단계는 Hibernate 등 ORM이 클래스 매핑 정보에서 SQL을 생성하는 구조를 학습한다. API 이름은 JPA의 `EntityManager.persist()`와 비슷하지만 영속성 컨텍스트나 JPA 표준을 구현하지는 않았다. 이전 Mapper 단계는 MyBatis처럼 SQL을 직접 작성하는 방식이다. [전체 단계 대응표](../reference/03-learning-stage-mapping.md)에 실제 기술과의 공통점·차이·공식 근거를 정리했다.

## 실행 흐름

```text
persist(user)
  → user.getClass()로 클래스 확인
  → 캐시에 없으면 EntityMetadata 생성·저장
  → @Table, @Id, @Column 정보로 INSERT 생성
  → 같은 컬럼 순서로 객체 필드 값을 추출
  → JdbcTemplate.update()로 바인딩·실행
  → 변경 행 수 반환
```

예를 들어 `@Column("user_name")`이 붙은 `name` 필드는 `user_name` 컬럼에 저장한다. 표시가 없는 필드는 Java 필드명을 컬럼명으로 사용한다. ID도 INSERT 컬럼에 포함한다.

## 두 Map의 역할

| 위치 | 자료구조 | 목적 |
|---|---|---|
| EntityMetadata.columns | LinkedHashMap | 분석 시 넣은 컬럼 순서를 유지해 SQL 컬럼과 값 바인딩 순서를 맞춤 |
| EntityManager.metadataCache | ConcurrentHashMap | 같은 EntityManager를 여러 스레드가 사용할 때 클래스별 메타데이터 조회·생성·저장을 처리 |

`getDeclaredFields()`가 필드 선언 순서를 보장하는 것은 아니다. 한 번 분석한 순서를 저장하고 SQL과 값 추출이 그 순서를 함께 사용한다는 점이 중요하다.

캐시는 실제 User 객체나 DB 조회 결과를 담지 않는다. 클래스 구조 정보를 재사용하는 캐시이며 JPA의 영속성 컨텍스트·1차 캐시와 다르다.

## 현재 제약

- `@Table`과 비어 있지 않은 테이블명이 필요하다. 별도의 `@Entity`는 없다.
- 직접 선언된 인스턴스 필드만 분석하며 static·synthetic 필드는 제외한다.
- ID 필드는 정확히 하나여야 하고 null ID는 저장 전에 거부한다. 이 규칙은 직접 지정 ID 기준이며, [IDENTITY](03-identity-generated-id.md)·[UUID 생성 ID](04-uuid-generated-id.md)는 null 상태로 저장을 요청한다.
- 비어 있거나 중복된 컬럼명, final 인스턴스 필드를 거부한다.
- 상속 필드, 관계 매핑, 복합 ID, SQL 방언별 식별자 인용·정규화는 지원하지 않는다.
- persist는 전달된 객체에서 값을 읽기만 하므로 기본 생성자가 없어도 된다. find는 구체적인 클래스와 접근 가능한 기본 생성자가 필요하다.
- persist는 호출 중 INSERT를 실행한다. 같은 관리자의 활성 트랜잭션 안에서는 그 연결을 쓰고, 밖에서는 임시 연결의 자동 커밋으로 저장한다.
- 여러 스레드 테스트는 독립 객체를 전달하는 현재 경로만 검증한다. 실제 JPA EntityManager의 동시 공유를 허용한다는 뜻은 아니다.

## 검증과 다음 작업

[EntityMetadataTest](../../src/test/java/com/jorm/entity/EntityMetadataTest.java) 6개는 테이블·ID·컬럼 분석, private 필드 접근, 변경 불가능한 컬럼 맵, 잘못된 선언 거부를 검증한다.

[EntityManagerTest](../../src/test/java/com/jorm/entity/EntityManagerTest.java) 8개는 INSERT 값 대응, null·따옴표 문자열 바인딩, null ID 거부, 중복 키 오류, 여러 객체 저장, 일괄 커밋·롤백, 동일 EntityManager의 동시 사용을 검증한다.

```powershell
.\gradlew.bat test --tests 'com.jorm.entity.*'
.\gradlew.bat test
```

`find(Class<T>, id)`는 [ID 조회와 객체 생성](02-find-by-id.md)에서 설명한다. 후속 구현은 [IDENTITY 생성 ID](03-identity-generated-id.md), [UUID 생성 ID](04-uuid-generated-id.md), [엔티티 UPDATE·DELETE](05-update-and-remove.md)를 참고한다.
