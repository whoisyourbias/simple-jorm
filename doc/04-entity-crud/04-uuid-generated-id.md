# 04-04. UUID 생성 ID

기준일: 2026-11-02

UUID 전략은 라이브러리가 INSERT 전에 ID를 생성한다. DB가 INSERT 후 키를 반환하는 [IDENTITY](03-identity-generated-id.md)와 생성 시점이 다르다.

```java
import com.jorm.entity.*;
import java.util.UUID;

@Table("users")
class User {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column("user_id")
    private UUID id;
    private String name;
}
```

예제 스키마(H2):

```sql
CREATE TABLE users (user_id UUID PRIMARY KEY, name VARCHAR(100));
```

ID 필드를 `String`으로 선언할 수도 있다. 이 경우 DB 컬럼은 `VARCHAR(36)`으로 준비하고 표준 UUID 문자열을 저장한다. 스키마 자동 생성이나 DB별 UUID 타입 변환은 제공하지 않는다.

## 저장 순서

1. 메타데이터는 UUID 전략이 `@Id`인 UUID·String 필드에만 붙었는지 검증한다. Long·Integer와 다른 타입은 거부한다.
2. 기존 생성 ID 규칙대로 ID가 이미 있으면 저장 전에 거부한다. `@GeneratedValue`가 없으면 직접 지정한 UUID도 기존 방식으로 저장할 수 있다.
3. `UUID.randomUUID()`로 버전 4 UUID를 생성한다. String 필드면 `toString()`으로 변환해 INSERT 전에 객체에 넣는다.
4. ID를 포함한 모든 컬럼을 일반 INSERT에 바인딩한다. ID만 있는 엔티티도 `INSERT INTO only_ids (id) VALUES (?)`로 저장한다. 생성 키 회수 API는 사용하지 않는다.
5. `persist`는 생성 ID 대신 변경 행 수를 반환한다. 객체의 ID로 `find`할 수 있다.

## 실패와 롤백

ID는 SQL 실행 전에 객체에 저장되므로, INSERT가 실패해도 생성 ID가 남는다. 트랜잭션 롤백 후에도 객체 ID는 유지되지만 DB 행은 사라진다. 같은 객체를 다시 persist하면 이미 ID가 있어 거부되므로, 새 객체로 다시 시도한다. 현재 영속성 컨텍스트도 롤백 시 Java 객체의 값과 생성 ID를 자동 복원하지 않는다.

기본 `@GeneratedValue`는 여전히 IDENTITY다. UUID를 사용하려면 전략을 명시해야 한다.

## 검증

[EntityManagerUuidTest](../../src/test/java/com/jorm/entity/EntityManagerUuidTest.java)에서 UUID·String 저장, v4 형식, 독립 SQL로 ID 확인, 컬럼 별칭, find, ID만 있는 엔티티, 서로 다른 ID 생성, 지정 ID와 반복 저장 거부, INSERT 실패와 트랜잭션 롤백, 타입·어노테이션 검증, 직접 지정 UUID 보존을 확인한다. H2에서 검증했으며 다른 DB의 UUID 컬럼 동작은 실서버에서 검증하지 않았다.
