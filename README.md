# simple-jorm

JDBC에서 SQL Mapper, 트랜잭션, 엔티티 매핑으로 확장하며 Java 데이터 접근 기술의 내부 동작을 학습하는 라이브러리다. AI 에이전트가 저장소를 탐색하고 수정·검증할 때 사용할 진입 문서이기도 하다.

Spring Boot 애플리케이션이나 JPA 구현체가 아니다. 서버 실행 진입점 없이 테스트로 동작을 확인한다. 프로덕션 코드는 JDK의 JDBC·리플렉션·동적 프록시를 사용하며, Spring JDBC와 Commons DbUtils는 테스트·비교 실험용 의존성이다.

## 에이전트가 먼저 읽을 것

1. 사용자 지시와 적용되는 `AGENTS.md`를 확인한다. 이 README는 해당 지시를 대체하지 않는다.
2. [현재 진행 상황](doc/00-overview/01-progress.md)과 [구현 상세·제약](doc/reference/02-implementation-status.md)을 읽는다.
3. 수정할 계층의 `src/main/java/com/jorm/` 구현과 같은 패키지의 `src/test/java/com/jorm/` 테스트를 함께 읽는다.
4. [학습 문서 목차](doc/README.md)에서 해당 단계의 설명을 확인한다. 문서와 코드가 다르면 현재 코드·테스트를 대조하고 차이를 보고한다.

## 현재 지원 범위

기준: 2026-11-03. 엔티티 CRUD·생성 ID와 트랜잭션별 영속성 컨텍스트의 객체 동일성을 구현했다.

| 계층 | 지원 기능 | 주요 제약 |
|---|---|---|
| JDBC | 파라미터 바인딩, 단건·목록 조회, 변경 SQL, 자원 정리, 수동·자동 행 매핑 | 단건 조회는 결과가 없으면 `null`, 여러 행이면 첫 행 반환 |
| Mapper | `@Select`, `@Insert`, `@Update`, `@Delete`, JDK 프록시 | 조회는 매핑 가능한 객체 또는 `List<T>`, 변경은 `int` 반환. default 메서드 실행 미지원 |
| 트랜잭션 | 같은 스레드·관리자의 Connection 공유, commit·rollback, REQUIRED 참여, rollbackOnly, Service 프록시 | 자기 호출은 프록시를 통과하지 않음. 다른 관리자·스레드와 트랜잭션 공유 안 됨 |
| 엔티티 | `@Table`, `@Id`, `@Column`, 클래스별 메타데이터 캐시, `persist`, `find`, `update`, `remove` | 직접 지정 또는 IDENTITY·UUID 단일 ID 사용. `persist`는 즉시 INSERT하고 변경 행 수 반환 |
| 영속성 컨텍스트 | 트랜잭션별 세션, 관리 객체 동일성, REQUIRED 재사용, 종료 시 정리 | Long·Integer·String·UUID와 long·int ID 캐싱. 명시적 수정·삭제 유지 |

아직 구현하지 않은 기능:

- SEQUENCE·TABLE·AUTO ID 생성 전략.
- 스냅샷, 자동 변경 감지, flush, 쓰기 지연.
- 관계 매핑, 복합 ID, 상속 필드 매핑, 별도 값 타입 변환.

`find`는 같은 관리자·트랜잭션 안에서 관리 객체를 재사용한다. 트랜잭션 밖이나 캐싱 대상 외의 ID 타입은 매번 SELECT하고 새 객체를 만든다. 조회 타입에는 접근 가능한 기본 생성자가 필요하다. `persist`에는 기본 생성자가 필요 없다. 같은 이름의 JPA API와 동작이 같다고 가정하지 않는다.

엔티티 update·remove는 ID 조건으로 즉시 SQL을 실행하고 변경 행 수를 반환한다. 대상이 없으면 0이며, null 객체·null ID와 ID 전용 타입의 update는 거부한다. [UPDATE·DELETE 사용법](doc/04-entity-crud/05-update-and-remove.md)을 참고한다.

다음 학습 순서는 스냅샷·변경 감지·flush다. [개념과 생명주기](doc/05-persistence-context/01-concepts-and-lifecycle.md), [현재 구현과 검증 항목](doc/05-persistence-context/02-implementation-checkpoints.md)을 먼저 읽는다. 현재 트랜잭션에 같은 ID의 관리 객체가 있으면 별도 객체의 persist·update를 거부한다. 직접 JDBC·Mapper 변경은 관리 객체를 자동 갱신하지 않는다.

## 환경과 검증 명령

- Java 21: `build.gradle.kts`의 Java toolchain 기준. 로컬에 JDK 21을 설치하고 Gradle에서 탐지할 수 있어야 한다. 자동 다운로드 설정은 없다.
- Gradle 8.9: 저장소에 포함된 Wrapper를 사용한다.
- JUnit 5, H2 인메모리 DB: 일반 테스트에 사용한다. 일반 테스트에 별도 MySQL 서버는 필요 없다.
- 최초 실행 시 Gradle 배포판과 Maven 의존성을 내려받을 네트워크가 필요하다.

모든 명령은 작업용 worktree 루트에서 실행한다. Unix에서는 현재 `gradlew`에 실행 권한이 없으므로 `bash ./gradlew`를 사용한다.

```bash
# 실행 환경 확인
java -version
bash ./gradlew --version

# 전체 테스트
bash ./gradlew test

# 수정한 계층의 테스트만 실행: 패키지는 작업 범위에 맞춰 변경
bash ./gradlew test --tests 'com.jorm.entity.*'

# ID 조회 테스트만 실행
bash ./gradlew test --tests com.jorm.entity.EntityManagerFindTest

# 테스트를 포함한 전체 빌드
bash ./gradlew build
```

Windows PowerShell에서는 `bash ./gradlew` 대신 `.\gradlew.bat`을 사용한다.

테스트 HTML 보고서는 `build/reports/tests/test/index.html`, XML 결과는 `build/test-results/test/`에 생성된다. 기존 진행 문서에는 전체 148개 테스트 통과가 기록되어 있다. 이 수치를 현재 실행 결과로 간주하지 말고, 검증 시 실행한 명령·결과를 별도로 보고한다. JDK 21을 찾지 못하면 먼저 로컬 설치와 Gradle toolchain 탐지를 확인한다.

## 코드 구조와 탐색 지점

```text
src/main/java/com/jorm/
├── jdbc/          # JdbcTemplate, RowMapper, BeanPropertyRowMapper
├── mapper/        # SQL 어노테이션, MapperFactory, MapperInvocationHandler
├── transaction/   # TransactionManager, @Transactional, Service 프록시
└── entity/        # 매핑 어노테이션, EntityMetadata, EntityManager
src/test/java/com/jorm/
├── jdbc/          # JDBC·매핑 테스트와 성능 실험 코드(lab/ 포함)
├── mapper/        # Mapper CRUD·반환 타입·인자 검증
├── transaction/   # 롤백·REQUIRED·Service 협업·자기 호출 테스트
└── entity/        # 메타데이터·INSERT·ID 조회 테스트
src/test/resources/ # 테스트 스키마·데이터
doc/                # 단계별 학습 문서, 참고 자료, 실험 기록
```

| 변경 대상 | 먼저 볼 구현 | 대응 테스트 |
|---|---|---|
| SQL 실행·연결·바인딩 | [JdbcTemplate](src/main/java/com/jorm/jdbc/JdbcTemplate.java) | `JdbcTemplateTest` |
| 자동 행 매핑 | [BeanPropertyRowMapper](src/main/java/com/jorm/jdbc/BeanPropertyRowMapper.java) | `BeanPropertyRowMapperTest` |
| Mapper 호출·반환 타입 | [MapperInvocationHandler](src/main/java/com/jorm/mapper/MapperInvocationHandler.java) | `MapperFactoryTest` |
| 트랜잭션 경계·참여 | [TransactionManager](src/main/java/com/jorm/transaction/TransactionManager.java), [Service 핸들러](src/main/java/com/jorm/transaction/TransactionInvocationHandler.java) | `TransactionManagerTest`, `TransactionServiceCollaborationTest`, `TransactionSelfInvocationTest` |
| 엔티티 매핑 규칙 | [EntityMetadata](src/main/java/com/jorm/entity/EntityMetadata.java) | `EntityMetadataTest` |
| 엔티티 저장·조회 | [EntityManager](src/main/java/com/jorm/entity/EntityManager.java) | `EntityManagerTest`, `EntityManagerFindTest` |

## 사용 예제: 엔티티 저장·조회

아래는 테스트 의존성인 H2로 실행할 수 있는 클래스 예제다. 실제 사용 시에는 호출자가 JDBC 드라이버와 `DataSource`를 제공하고 테이블을 준비한다. 자동 스키마 생성 기능은 없다.

```java
import com.jorm.entity.Column;
import com.jorm.entity.EntityManager;
import com.jorm.entity.Id;
import com.jorm.entity.Table;
import com.jorm.jdbc.JdbcTemplate;
import com.jorm.transaction.TransactionManager;
import org.h2.jdbcx.JdbcDataSource;

public class EntityExample {
    @Table("users")
    static class User {
        @Id
        Long id;
        @Column("user_name")
        String name;

        User() {}

        User(Long id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    public static void main(String[] args) throws Exception {
        JdbcDataSource source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:example");

        // SQL 실행 후 연결을 반환해도 인메모리 DB가 사라지지 않게 연결을 유지한다.
        try (var keepAlive = source.getConnection()) {
            TransactionManager transactions = new TransactionManager(source);
            JdbcTemplate jdbc = new JdbcTemplate(transactions);
            EntityManager entities = new EntityManager(jdbc);
            jdbc.update("CREATE TABLE users (id BIGINT PRIMARY KEY, user_name VARCHAR(100))");

            User found = transactions.execute(() -> {
                int affectedRows = entities.persist(new User(1L, "Alice"));
                if (affectedRows != 1) {
                    throw new IllegalStateException("INSERT 결과 확인 필요");
                }
                return entities.find(User.class, 1L);
            });
            System.out.println(found.name); // Alice
        }
    }
}
```

트랜잭션을 공유할 `JdbcTemplate`과 Service 프록시에는 **같은 `TransactionManager` 인스턴스**를 전달한다. `new JdbcTemplate(dataSource)`는 자체 관리자를 만들므로, 별도로 만든 관리자의 `execute`와 연결을 공유하지 않는다.

Mapper는 `MapperFactory.create(MapperInterface.class, jdbc)`로 생성한다. Service 프록시는 `TransactionProxyFactory.create(ServiceInterface.class, target, transactions)`로 생성하며 구현 메서드의 `@Transactional`을 검사한다. 실제 선언·호출 예제는 [Mapper 문서](doc/02-mapper/01-proxy-execution.md)와 [트랜잭션 문서](doc/03-transaction/01-connection-and-transaction.md)를 참고한다.

## IDENTITY 생성 ID

```java
@Id
@GeneratedValue(strategy = GenerationType.IDENTITY)
@Column("user_id")
Long id; // persist 전에 null, 성공 후 DB가 생성한 값
```

`com.jorm.entity`의 자체 어노테이션과 enum을 사용한다. `Long`·`Integer`만 지원하며 기본 전략도 IDENTITY다. DB 컬럼은 별도로 IDENTITY/자동 증가 컬럼으로 준비해야 한다. 생성 ID를 INSERT에서 제외한 뒤 같은 연결에서 생성 키를 회수한다. 이미 ID가 있는 객체의 재저장은 거부한다. 롤백 후 DB 행은 사라지지만 객체에 반영된 ID는 남는다. [동작과 제약](doc/04-entity-crud/03-identity-generated-id.md).

## UUID 생성 ID

```java
@Id
@GeneratedValue(strategy = GenerationType.UUID)
UUID id; // java.util.UUID. String 타입도 지원
```

INSERT 전에 `UUID.randomUUID()`로 v4 UUID를 생성해 객체에 넣고 ID 컬럼도 함께 저장한다. DB의 자동 증가 설정은 필요 없다. UUID 필드는 H2의 UUID 컬럼, String 필드는 VARCHAR(36) 컬럼으로 검증했다. SQL 실패나 롤백 후에도 생성된 ID는 객체에 남는다. 기본 전략은 계속 IDENTITY다. [사용법과 실패 동작](doc/04-entity-crud/04-uuid-generated-id.md).

## 에이전트의 변경 작업 절차

이 저장소에 적용되는 worktree 규칙에 따라 다음 순서를 지킨다.

1. 기본 worktree 상태·worktree 목록·기본 브랜치를 확인한다. 미커밋 변경이 있으면 알리고 보존한다.
2. 기본 worktree를 기본 브랜치로 전환한다. `master`가 있으면 `master`, 없고 원격 기본 브랜치가 `main`이면 `main`, 그 외에는 확인된 기본 브랜치를 사용하고 보고한다.
3. 원격 정보를 fetch하고 해당 원격 추적 브랜치 기준으로 최신화한다. 로컬 변경을 덮어쓰거나 이력을 강제로 되돌리지 않는다.
4. 기본 저장소의 `.git/info/exclude`에 `.worktrees/`를 등록한다. 최신화한 기본 브랜치에서 작업 브랜치와 `<기본 저장소>/.worktrees/<작업명>`을 만든다.
5. 기존 작업용 worktree가 내부에 있으면 재사용한다. 외부에 있으면 변경사항 유실 여부를 확인한 뒤 `git worktree move`로 내부에 이동한다.
6. 파일 수정·테스트·포맷팅·커밋은 작업용 worktree에서 수행한다. 단순 조회·분석에는 worktree 생성이 필요 없다.
7. 요청 범위의 구현과 대응 테스트를 확인하고 검증한다. 기능의 지원 범위가 바뀌면 진행 상황·해당 단계 문서도 갱신한다.
8. 완료 시 worktree 경로, 변경 파일, 실행한 검증과 결과, 실행하지 못한 검증의 이유를 보고한다.

현재 기본 브랜치는 `main`이다. 작업 시작 시 실제 상태를 다시 확인한다. 브랜치 이름은 별도 지시가 없으면 `codex/<작업명>`을 사용한다.

## 성능 실험과 상세 문서

성능 실험은 일반 테스트와 별도로 관리한다. `mappingBenchmark`, `databaseMappingBenchmark`, `mappingScalingBenchmark`, `libraryMappingBenchmark`는 명시적으로 실행하는 Gradle 태스크다. MySQL을 사용하는 실험은 [재현 방법과 환경 설정](doc/lab/02-mapping-scaling.md)을 먼저 읽는다. 문서 변경만으로 실험을 재실행하거나 원시 결과를 덮어쓰지 않는다.

- [학습 문서 목차](doc/README.md)
- [현재 진행 상황](doc/00-overview/01-progress.md)
- [실제 기술과 학습 단계의 대응](doc/reference/03-learning-stage-mapping.md)
- [엔티티 매핑·INSERT](doc/04-entity-crud/01-metadata-and-insert.md) · [ID 조회](doc/04-entity-crud/02-find-by-id.md)
- [성능 실험 목록](doc/lab/README.md) · [최종 실험 결과](doc/lab/results/2026-10-30-scaling-v2/tables.md)
