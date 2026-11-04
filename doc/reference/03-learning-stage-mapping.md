# 구현 단계와 실제 데이터 접근 기술의 대응

기준일: 2026-11-04

simple-jorm은 JDBC 실행, SQL Mapper, 트랜잭션, 엔티티 매핑을 차례로 직접 만드는 학습 프로젝트다. 아래 대응은 **비슷한 역할을 배우고 있다는 뜻**이다. 해당 라이브러리의 전체 기능이나 표준 호환성을 구현했다는 뜻은 아니다.

## 단계별 대응표

| 학습 단계 | 직접 구현한 코드 | 가까운 실제 기술·기능 | 현재 상태 |
|---|---|---|---|
| 01 JDBC 실행 | `JdbcTemplate`, `RowMapper` | Spring JDBC의 템플릿·행 매퍼 방식 | 단건·목록 조회, 변경 SQL, 바인딩·자원 정리 완료 |
| 01 자동 행 매핑 | `BeanPropertyRowMapper.prepare()`, `JdbcValueConverter` | Spring JDBC의 자동 객체 매핑 | setter 우선·필드 fallback, snake_case 연결, 기본 타입 변환 |
| 02 SQL Mapper | SQL 어노테이션, `@Param`, `MapperFactory` | MyBatis의 SQL 어노테이션·Mapper 인터페이스 방식 | 기본 CRUD, 이름 파라미터 바인딩, default 메서드 실행 |
| 03 명시적 트랜잭션 | `TransactionManager.execute()`, `withConnection()` | JDBC 로컬 트랜잭션, Spring의 프로그램 방식 트랜잭션 관리 | 같은 관리자·스레드의 연결 공유와 커밋·롤백 완료 |
| 03 선언적 트랜잭션 | `@Transactional`, `TransactionInvocationHandler`, `TransactionResource` | Spring의 프록시 기반 선언적 트랜잭션 | rollbackFor/noRollbackFor, checked 기본 커밋, 커밋 전 자원 동기화 |
| 03 REQUIRED | `TransactionState`, `participate()`, `UnexpectedRollbackException` | Spring의 REQUIRED 참여·rollback-only 의미 | 기존 트랜잭션 참여, 바깥 종료, 실패를 잡아도 전체 롤백 완료 |
| 04 엔티티 메타데이터 | `@Table`, `@Id`, `@Column`, `@Transient`, `EntityMetadata` | Hibernate 등 JPA 구현체의 객체·테이블 매핑 분석 | 부모 필드·단일 ID, 저장 제외 필드, 메타데이터 캐싱 |
| 04 엔티티 INSERT | `EntityManager.persist()` | JPA `EntityManager.persist()`의 저장 진입 형태와 ORM의 INSERT 생성 | ID 직접 지정·IDENTITY·UUID 생성, INSERT 즉시 실행, 변경 행 수 반환 완료 |
| 04 엔티티 ID 조회 | `EntityManager.find()` | JPA EntityManager.find의 ID 조회 진입 형태 | SELECT·컬럼 매핑 구현. Stage 5에서 관리 객체 재사용 추가 |
| 04 엔티티 변경·삭제 | EntityManager.update·remove | ORM의 CRUD SQL 생성 | ID 조건의 즉시 UPDATE·DELETE, 변경 행 수 반환 완료 |
| 05 영속성 컨텍스트 | 세션·동일성·스냅샷·변경 감지·flush | JPA의 영속성 컨텍스트, Hibernate의 엔티티 상태 관리 | [명시적·커밋 직전 flush](../05-persistence-context/02-implementation-checkpoints.md), 변경 객체만 UPDATE |

Spring JDBC는 자원 생성·반환과 SQL 실행의 공통 작업을 처리하고 SQL·결과 추출을 호출자에게 맡긴다. MyBatis는 직접 작성한 SQL과 Mapper 인터페이스를 연결한다. 이 두 설명이 각각 01·02 단계의 비교 근거다. [Spring JDBC 공식 문서](https://docs.spring.io/spring-framework/reference/data-access/jdbc/core.html), [MyBatis Java API](https://mybatis.org/mybatis-3/java-api.html)

03 단계는 특정 ORM에 속하는 기능이 아니라 데이터 접근 기술과 함께 쓰는 트랜잭션 계층이다. REQUIRED가 기존 물리 트랜잭션에 참여하고, 안쪽의 롤백 표시로 바깥 정상 반환도 커밋할 수 없게 되는 개념을 학습했다. [Spring 트랜잭션 전파](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/tx-propagation.html)

JPA는 표준 명세이고 Hibernate는 그 구현체다. JPA는 저장·ID 조회·삭제뿐 아니라 엔티티의 관리 상태와 영속성 컨텍스트도 정의하므로, SQL 생성만으로 JPA 구현체가 되는 것은 아니다. [Hibernate 소개](https://hibernate.org/orm/), [Jakarta Persistence 3.2 명세](https://jakarta.ee/specifications/persistence/3.2/jakarta-persistence-spec-3.2.html)

## 01: SQL 실행과 한 행 매핑

```java
jdbc.queryForObject(sql, rowMapper, args);
```

호출자가 SQL을 제공하고 `JdbcTemplate`이 바인딩·실행·자원 정리를 맡는다. `RowMapper`는 현재 행을 객체로 바꾸며, 결과 커서를 다음 행으로 옮기는 것은 템플릿의 책임이다.

우리 자동 매퍼는 기본 생성자로 객체를 만들고 public JavaBean setter를 우선 사용하며, setter가 없으면 직접 선언 필드에 값을 쓴다. 대소문자와 snake_case 별칭을 지원하고, `prepare()`가 조회별 컬럼 연결을 준비한다. 숫자 손실을 거부하는 기본 변환과 JDBC 날짜·시간 변환을 지원한다. Spring의 ConversionService·record 매핑·전체 프로퍼티 검사까지 구현한 것은 아니다. 엔티티 `@Column`도 해석하지 않는다. 단건 조회는 0행 null·여러 행 첫 행을 유지하고 SQLException을 전달하므로 Spring의 엄격한 단건 계약·예외 변환과는 다르다.

코드: [JdbcTemplate](../../src/main/java/com/jorm/jdbc/JdbcTemplate.java), [BeanPropertyRowMapper](../../src/main/java/com/jorm/jdbc/BeanPropertyRowMapper.java).

## 02: 메서드를 직접 작성한 SQL에 연결

```java
@Select("SELECT id, name FROM users WHERE id = ?")
User findById(Long id);
```

이 단계는 MyBatis식 SQL Mapper를 이해하기 위한 구현이다. SQL은 개발자가 작성하고 프록시는 메서드의 어노테이션을 읽어 실행한다. 기존 `?` 외에 자체 `@Param("id")`와 `#{id}`를 연결한다. 값은 문자열에 합치지 않고 PreparedStatement에 바인딩한다. default 메서드는 본문을 실행하며 내부 Mapper 호출도 프록시를 통한다. XML·동적 SQL·복잡한 관계 매핑·SqlSession 캐시와 bean 속성 경로는 지원하지 않는다.

여기서 **Mapper**는 SQL 호출 인터페이스이고, **RowMapper**는 결과 한 행을 변환하는 콜백이다. 두 역할을 구분해야 한다.

코드: [MapperInvocationHandler](../../src/main/java/com/jorm/mapper/MapperInvocationHandler.java).

## 03: 여러 작업을 하나의 트랜잭션으로 연결

```text
Service A 프록시 → 트랜잭션 시작
  → Service B 프록시 → 기존 트랜잭션 참여
  → A 종료 → 전체 commit 또는 rollback
```

같은 `TransactionManager`의 ThreadLocal 상태를 이용해 현재 스레드의 Connection을 공유한다. 프록시에서 구현 메서드의 어노테이션을 읽고 업무를 `execute()`로 감싼다. 자기 호출은 새 프록시 경계를 만들지 않지만, 이미 시작한 바깥 트랜잭션의 연결은 사용할 수 있다.

우리 선언적 경계의 기본 롤백 정책은 Spring처럼 RuntimeException·Error이며, checked 업무 예외는 커밋 후 원본을 다시 던진다. `rollbackFor`·`noRollbackFor`로 변경하며 가장 구체적인 예외 타입이 우선한다. 명시적 `TransactionManager.execute()`는 기존 SQLException 롤백 계약을 유지한다. 우리 프록시는 구현 메서드의 표시만 읽고 수동 생성·주입하며 클래스 프록시는 없다. 자원의 `beforeCommit()` 실패는 전체 롤백하고 종료 콜백으로 정리한다. [Spring @Transactional](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/annotations.html)

REQUIRED는 하나의 트랜잭션에 참여하는 방식이다. savepoint로 안쪽만 취소하는 NESTED나 독립 트랜잭션을 시작하는 REQUIRES_NEW는 구현하지 않았다.

코드: [TransactionManager](../../src/main/java/com/jorm/transaction/TransactionManager.java). 학습·테스트: [트랜잭션 문서](../03-transaction/01-connection-and-transaction.md).

## 04: 객체 구조에서 SQL 생성

```java
@Table("users")
class User {
    @Id Long id;
    @Column("user_name") String name;
}

// 객체 필드 값을 읽어 INSERT를 생성한다.
entityManager.persist(user);
```

02 단계와의 차이는 SQL의 작성 주체다. Mapper는 SQL 문자열을 직접 받고, EntityManager는 메타데이터에서 테이블·컬럼을 알아내 SQL을 만든다. 두 방식은 현재 프로젝트에 함께 존재하고 SQL 실행·트랜잭션 계층을 공유한다.

| 비교 항목 | 현재 simple-jorm | JPA / Hibernate |
|---|---|---|
| API·어노테이션 | 직접 정의한 `com.jorm.entity` 타입 | `jakarta.persistence` 표준 API와 provider 구현 |
| 엔티티 선언 | `@Table`만 검사. `@Entity`는 없음 | JPA 엔티티 선언·매핑 규칙 적용 |
| 상속·저장 제외 | 부모 필드까지 구체 클래스의 한 테이블에 매핑. transient·자체 @Transient 제외 | MappedSuperclass·상속 전략·표준 Transient 규칙 제공 |
| `persist()` 의미 | 즉시 INSERT, `int` 변경 행 수 반환 | 엔티티를 영속성 컨텍스트의 관리 상태로 만들며 반환 타입은 void |
| INSERT 시점 | persist 호출 중 SQL 실행 | flush·식별자 전략 등 조건에 따라 SQL 실행 시점 결정 |
| ID | 직접 지정, IDENTITY(Long·Integer), UUID(UUID·String) | 직접 지정 또는 생성 전략 사용 가능 |
| 저장 후 객체 변경 | 관리 객체의 변경을 명시적 flush·커밋 직전에 UPDATE. 명시적 update도 유지 | 관리 상태 엔티티의 변경을 flush 규칙에 따라 DB와 동기화 |
| 캐시 | 클래스별 매핑 정보와 트랜잭션별 세션의 관리 객체 저장 | 영속성 컨텍스트에서 엔티티 인스턴스·생명주기 관리 |
| 여러 스레드 사용 | 현재 공유 캐시·독립 객체의 동시 INSERT를 테스트함 | 실제 EntityManager·영속성 컨텍스트를 여러 스레드가 동시에 공유해서는 안 됨 |

JPA의 persist·flush·영속성 컨텍스트·스레드 사용 규칙은 [Jakarta Persistence 3.2 명세](https://jakarta.ee/specifications/persistence/3.2/jakarta-persistence-spec-3.2.html), 메서드 계약은 [EntityManager API](https://jakarta.ee/specifications/persistence/3.2/apidocs/jakarta.persistence/jakarta/persistence/EntityManager.html)를 참고한다. 우리 캐시의 ConcurrentHashMap 사용을 JPA EntityManager의 스레드 안전성으로 일반화하면 안 된다.

코드: [EntityMetadata](../../src/main/java/com/jorm/entity/EntityMetadata.java), [EntityManager](../../src/main/java/com/jorm/entity/EntityManager.java). 구현·제약·테스트: [엔티티 매핑과 INSERT](../04-entity-crud/01-metadata-and-insert.md), [ID 조회](../04-entity-crud/02-find-by-id.md).

## 아직 구현하지 않은 계층

- 객체 동일성·변경 감지·flush는 구현했다. INSERT·DELETE 쓰기 지연, 조회 전 AUTO flush, 관계·merge/clear·낙관적 잠금은 남는 차이다.
- Spring Data JPA식 Repository 자동 생성: 현재 EntityManager 위에 별도로 구성할 수 있는 계층이며 아직 구현하지 않았다.
- jOOQ식 SQL DSL, Spring Data JDBC의 Aggregate 저장 모델, R2DBC 실행: 현재 구현 경로에 포함하지 않는다. 기술별 관계는 [생태계 상세 문서](01-java-orm-ecosystem.md)를 참고한다.
