# 04-06. 부모 필드와 저장하지 않는 필드

기준일: 2026-11-04. 이번 단계는 기존 SQL 생성 경로를 유지하면서 필드 매핑의 범위를 넓힌다.

## 구현 계약

- 실제로 저장·조회하는 구체 클래스에는 자체 `@Table`이 필요하다. 부모에는 `@Table`이나 별도 매핑 어노테이션을 요구하지 않는다.
- `EntityMetadata`는 구체 클래스부터 `Object` 직전까지 부모의 private 인스턴스 필드를 분석한다. 계층 전체에서 매핑되는 `@Id`는 정확히 하나다. 부모의 `@Column`과 기존 IDENTITY·UUID 선언도 적용한다.
- 자체 `com.jorm.entity.Transient` 또는 Java `transient` 필드는 매핑에서 제외한다. 제외 필드는 ID·컬럼·생성 전략·final 여부를 검사하지 않는다. static·synthetic 필드의 기존 제외 규칙과 static 생성 ID 거부 계약은 유지한다.
- 계층 전체의 중복 ID, 중복 컬럼명, 같은 이름의 매핑 필드를 숨기는 선언은 SQL 실행 전에 거부한다. 숨긴 필드는 컬럼 별칭이 달라도 거부한다. 제외된 필드는 이런 충돌의 대상이 아니다.
- INSERT·SELECT·UPDATE·DELETE는 모두 같은 메타데이터를 사용한다. 제외 필드는 DB에 컬럼이 없어도 저장·조회·수정에 영향을 주지 않으며 조회 객체에는 생성자의 초기값이 남는다.
- 매핑된 final 필드는 계속 거부한다. SQL 컬럼 순서와 값 순서는 같은 메타데이터 순서를 공유하며 Java 소스의 선언 순서를 보장하지 않는다.

## 예제

```java
class BaseRecord {
    @Id @GeneratedValue @Column("record_id")
    private Long id;
    @Column("created_by") private String creator;
    @Transient private final String displayHint = "local";
}

@Table("records")
class Record extends BaseRecord {
    private String title;
    private transient String requestToken;
}
```

DB 테이블에는 `record_id`, `created_by`, `title`만 필요하다. 기본 생성자가 있는 `Record`를 조회하면 부모 필드도 복원한다.

## 실제 기술과 남는 차이

부모의 공통 필드와 저장 제외 선언을 SQL 생성에 반영하는 핵심만 구현한다. JPA의 `@Entity`, `@MappedSuperclass`, 상속 전략과 다형적 조회를 구현한 것은 아니다. 관계 매핑, 복합 키와 새로운 ID 전략도 이번 범위에 포함하지 않는다. JORM은 구체 클래스의 한 테이블에 부모·자식 필드를 평평하게 매핑한다.

## 검증

`EntityMetadataInheritanceTest` 9개는 다단계 부모 필드·ID, 제외 필드와 잘못된 계층 선언을 검증한다. `EntityManagerInheritanceTest` 4개는 제외 필드의 컬럼이 없는 실제 H2 테이블에서 부모 ID·컬럼 별칭을 사용해 저장·조회·수정·삭제하고 IDENTITY·UUID 생성 ID를 부모 필드에 반영하는지 확인한다. 모호한 매핑을 존재하지 않는 테이블에 SQL을 보내기 전에 거부하는지도 확인한다.

```powershell
.\gradlew.bat test --tests 'com.jorm.entity.*'
.\gradlew.bat test
node --test doc/tests/learning-page.test.cjs
```
