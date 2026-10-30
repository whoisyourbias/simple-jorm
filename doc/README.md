# simple-jorm 학습 문서

학습 순서대로 번호를 붙였다. 현재는 **02 Mapper 단계의 기본 CRUD 구현·검증까지 완료**했으며, 다음 작업은 트랜잭션 관리다.

## 폴더 구조

```text
doc/
├─ README.md
├─ 00-overview/                    # 전체 방향과 현재 진행 상황
│  ├─ 01-progress.md
│  └─ 02-java-orm-ecosystem.md
├─ 01-jdbc/                        # JDBC 실행과 결과 매핑
│  └─ 01-jdbc-and-row-mapping.md
├─ 02-mapper/                      # 현재 단계: 어노테이션과 프록시
│  ├─ 01-proxy-execution.md
│  └─ 02-tests-and-next-steps.md
├─ reference/                     # 기술·구현 상세 자료
│  ├─ 01-java-orm-ecosystem.md
│  └─ 02-implementation-status.md
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
| 02 | [JVM부터 프록시 호출까지](02-mapper/01-proxy-execution.md), [테스트와 다음 작업](02-mapper/02-tests-and-next-steps.md) | 기본 CRUD 완료 |
| 실험 | [실험 목록과 재현 안내](lab/README.md) | 학습 순서와 별도로 관리 |

## 번호와 관리 규칙

- 학습 폴더는 `00`, `01`, `02`처럼 단계 순서로 번호를 붙인다. 파일 앞 번호는 해당 폴더 안에서 읽는 순서다.
- 이후 트랜잭션, 엔티티 CRUD, 영속성 컨텍스트를 시작하면 각각 `03-transaction`, `04-entity-crud`, `05-persistence-context`를 만든다. 아직 구현하지 않은 단계의 빈 폴더는 만들지 않는다.
- `lab`의 실험 계획·보고서는 번호로 짝을 구분한다. 실행 스크립트와 원시 결과 이름은 재현 경로를 유지한다.
- 단계 완료 여부는 `00-overview/01-progress.md`에 갱신한다. 실험 기록은 `lab`에 둔다.

## 상세 자료와 이전 기록

- [Java 데이터 접근 생태계 상세](reference/01-java-orm-ecosystem.md)
- [구현 상세·제약·검증 기록](reference/02-implementation-status.md)
- [Mapper 구현 전 진행 기록](history/01-before-mapper.md)

`overview`에는 요약과 링크를 두고, 상세 자료는 `reference`, 당시 진행 상태는 `history`에 보존한다.
