# 별도 실험 기록

학습 단계의 구현 설명과 성능 실험을 분리한다. 이 폴더는 계획, 보고서, 실행 스크립트와 원시 결과를 함께 보관한다.

## 실험 01. 행 수·필드 수에 따른 JDBC 매핑 비용

- [01. 실험 계획과 완료 체크리스트](01-mapping-scaling-plan.md)
- [02. 결과 보고서와 재현 방법](02-mapping-scaling.md)
- [실행 스크립트](run-mapping-scaling.ps1)
- [집계 스크립트](summarize_mapping_scaling.py)
- [최종 결과 표](results/2026-10-30-scaling-v2/tables.md)
- [비교 라이브러리 결과](results/2026-10-30-scaling-v2/library-tables.md)
- [예비·폐기 데이터 설명](results/README.md)

보고서에는 수동 매핑, simple-jorm, Spring JDBC, Commons DbUtils의 비교와 측정 한계를 기록한다. 최종 분석에는 `results/2026-10-30-scaling-v2/`를 사용한다.

## 관리 규칙

- 새로운 실험의 문서는 다음 번호부터 추가한다. 실험 날짜와 환경은 문서 본문과 결과 폴더에 기록한다.
- 실행 코드가 참조하는 스크립트와 `results` 경로는 번호를 붙이기 위해 변경하지 않는다.
- 원시 CSV, 환경 기록, 해시와 생성된 표·그래프를 보관한다.
- 문서 정리만으로 실험을 재실행하지 않는다.

[학습 문서로 돌아가기](../README.md)
