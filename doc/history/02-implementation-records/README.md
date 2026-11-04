# 구현 설계·계획 기록

완료한 작업의 설계, 구현 계획과 당시 검증 결과를 보존한다. 현재 사용법은 단계별 학습 문서, 지원 범위와 최신 검증은 [진행 상황](../../00-overview/01-progress.md)에서 확인한다. 기록에 남은 미완료 항목이나 제외 범위는 해당 작성 시점의 내용이다.

| 순서 | 작성일 | 기록 | 관련 학습 단계 |
|---|---|---|---|
| 01 | 2026-11-02 | [IDENTITY 생성 ID 계획](01-entity-identity-plan.md) | [04 엔티티 CRUD](../../04-entity-crud/03-identity-generated-id.md) |
| 02 | 2026-11-02 | [UUID 생성 ID 계획](02-entity-uuid-plan.md) | [04 엔티티 CRUD](../../04-entity-crud/04-uuid-generated-id.md) |
| 03 | 2026-11-03 | [생성 ID 설계](03-generated-id-design.md) | [04 엔티티 CRUD](../../04-entity-crud/03-generated-id.md) |
| 04 | 2026-11-03 | [생성 ID 계획](04-generated-id-plan.md) | 04 엔티티 CRUD |
| 05 | 2026-11-03 | [update·remove 설계](05-entity-update-remove-design.md) | [04 엔티티 CRUD](../../04-entity-crud/05-update-and-remove.md) |
| 06 | 2026-11-03 | [update·remove 계획](06-entity-update-remove-plan.md) | 04 엔티티 CRUD |
| 07 | 2026-11-03 | [학습 페이지 계획](07-learning-page-plan.md) | [학습 웹페이지](../../index.html) |
| 08 | 2026-11-03 | [영속성 컨텍스트 설계](08-persistence-context-design.md) | [05 영속성 컨텍스트](../../05-persistence-context/01-concepts-and-lifecycle.md) |
| 09 | 2026-11-03 | [영속성 컨텍스트 계획](09-persistence-context-plan.md) | 05 영속성 컨텍스트 |
| 10 | 2026-11-04 | [단계별 핵심 기능 설계](10-stage-core-design.md) | 01~05 전체 |
| 11 | 2026-11-04 | [단계별 핵심 기능 계획·완료 기록](11-stage-core-plan.md) | 01~05 전체 |

## 관리 규칙

- 파일 번호는 기록을 읽는 순서다. 같은 작업의 설계와 구현 계획은 인접하게 둔다.
- 날짜는 파일명 대신 문서 첫머리에 기록한다. 기존 범위·체크리스트·검증 수치는 당시 기록으로 보존한다.
- 새로운 설계·계획도 프로젝트의 `doc/` 구조를 따른다. 완료된 기록은 이 폴더에 추가하고 목차를 갱신한다.
- 도구 전용 실행 지시 대신 목표·구조·제약·검토 항목을 기록하며, 현재 상태를 설명하는 문서와 연결한다.
