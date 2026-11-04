# simple-jorm 학습 문서

**[Java 데이터 접근 기술 · 실행 애니메이션](index.html)** — 실제 Spring JDBC·MyBatis·Spring 트랜잭션·JPA/Hibernate가 동작하는 과정을 설명하는 독립 HTML. 저장소 구현의 지원 범위는 아래 단계별 문서에서 확인한다.

페이지 상태 전환 검증: 저장소 루트에서 `node --test doc/tests/learning-page.test.cjs` (Node.js 기본 테스트 실행기 사용).

브라우저 검증: Node.js 22 이상과 설치된 Chrome으로 `node doc/tests/browser-smoke.cjs`. Windows 기본 설치 경로 외에는 `CHROME_PATH`로 실행 파일을 지정한다. 결과와 스크린샷은 `build/page-qa/`에 생성된다.

학습 순서대로 번호를 붙였다. 현재는 **각 단계의 핵심 기능과 05 영속성 컨텍스트의 객체 동일성·변경 감지·flush를 구현**했다.

## 폴더 구조

```text
doc/
├─ README.md
├─ 00-overview/                    # 전체 방향과 현재 진행 상황
│  ├─ 01-progress.md
│  └─ 02-java-orm-ecosystem.md
├─ 01-jdbc/                        # JDBC 실행과 결과 매핑
│  └─ 01-jdbc-and-row-mapping.md
├─ 02-mapper/                      # 어노테이션과 프록시
│  ├─ 01-proxy-execution.md
│  └─ 02-tests-and-next-steps.md
├─ 03-transaction/                # Connection 공유·Service 프록시·REQUIRED
│  └─ 01-connection-and-transaction.md
├─ 04-entity-crud/                # 메타데이터·CRUD·생성 ID
│  ├─ 01-metadata-and-insert.md
│  ├─ 02-find-by-id.md
│  ├─ 03-identity-generated-id.md
│  ├─ 04-uuid-generated-id.md
│  ├─ 05-update-and-remove.md
│  └─ 06-inherited-and-transient-fields.md
├─ 05-persistence-context/        # 객체 동일성·스냅샷·변경 감지·flush
│  ├─ 01-concepts-and-lifecycle.md
│  └─ 02-implementation-checkpoints.md
├─ reference/                     # 기술·구현 상세 자료
│  ├─ 01-java-orm-ecosystem.md
│  ├─ 02-implementation-status.md
│  └─ 03-learning-stage-mapping.md
├─ history/                       # 이전 진행 기록
│  └─ 01-before-mapper.md
└─ lab/                            # 학습 단계와 분리한 실험
   ├─ README.md
   ├─ 01-mapping-scaling-plan.md
   ├─ 02-mapping-scaling.md
   ├─ run-mapping-scaling.ps1
   ├─ summarize_mapping_scaling.py
   └─ results/
```

## 읽는 순서

| 단계 | 문서 | 상태 |
|---|---|---|
| 00 | [진행 상황](00-overview/01-progress.md), [Java 데이터 접근 생태계](00-overview/02-java-orm-ecosystem.md) | 전체 안내 |
| 01 | [JDBC 실행과 결과 매핑](01-jdbc/01-jdbc-and-row-mapping.md) | 구현 완료 |
| 02 | [JVM부터 프록시 호출까지](02-mapper/01-proxy-execution.md), [테스트와 다음 작업](02-mapper/02-tests-and-next-steps.md) | CRUD·이름 바인딩·default 메서드 |
| 03 | [Connection 공유와 트랜잭션](03-transaction/01-connection-and-transaction.md) | REQUIRED·선언적 롤백 규칙·커밋 전 콜백 |
| 04 | [엔티티 매핑과 INSERT](04-entity-crud/01-metadata-and-insert.md), [상속·저장 제외](04-entity-crud/06-inherited-and-transient-fields.md) | CRUD·생성 ID·부모 필드·@Transient |
| 05 | [영속성 컨텍스트와 생명주기](05-persistence-context/01-concepts-and-lifecycle.md), [구현 계약과 검증 항목](05-persistence-context/02-implementation-checkpoints.md) | 객체 동일성·스냅샷·변경 감지·flush |
| 실험 | [실험 목록과 재현 안내](lab/README.md) | 학습 순서와 별도로 관리 |

## 단계별 실제 기술과의 대응

Spring JDBC식 실행 → MyBatis식 SQL Mapper → Spring식 트랜잭션 → Hibernate/JPA의 매핑·영속성 관리 개념 순으로 학습한다. 같은 이름의 API라도 동작과 지원 범위는 다르다. [코드별 대응·현재 상태·공식 자료](reference/03-learning-stage-mapping.md)를 참고한다.

## 번호와 관리 규칙

- 학습 폴더는 `00`, `01`, `02`처럼 단계 순서로 번호를 붙인다. 파일 앞 번호는 해당 폴더 안에서 읽는 순서다.
- 트랜잭션은 `03-transaction`, 엔티티 CRUD는 `04-entity-crud`에서 관리한다. 영속성 컨텍스트의 개념과 구현 준비는 `05-persistence-context`에서 관리한다. 아직 문서나 코드가 없는 단계의 빈 폴더는 만들지 않는다.
- `lab`의 실험 계획·보고서는 번호로 짝을 구분한다. 실행 스크립트와 원시 결과 이름은 재현 경로를 유지한다.
- 단계 완료 여부는 `00-overview/01-progress.md`에 갱신한다. 실험 기록은 `lab`에 둔다.

## 상세 자료와 이전 기록

- [Java 데이터 접근 생태계 상세](reference/01-java-orm-ecosystem.md)
- [단계별 실제 기술 대응](reference/03-learning-stage-mapping.md)
- [구현 상세·제약·검증 기록](reference/02-implementation-status.md)
- [Mapper 구현 전 진행 기록](history/01-before-mapper.md)

`overview`에는 요약과 링크를 두고, 상세 자료는 `reference`, 당시 진행 상태는 `history`에 보존한다.

- [IDENTITY 생성 ID와 트랜잭션](04-entity-crud/03-identity-generated-id.md)
- [UUID 생성 ID](04-entity-crud/04-uuid-generated-id.md)
