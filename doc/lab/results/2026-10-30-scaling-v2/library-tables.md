# 실제 라이브러리 조회 API 비교

10,000행 × 30필드, SQL·결과 전송·매핑·statement 정리 포함, 연결 생성 제외.

| 라이브러리 | 수동 ms | 자동 ms | 자동/수동 |
|---|---:|---:|---:|
| Spring JDBC | 37.013 | 67.524 | 1.82x |
| Commons DbUtils | 35.570 | 70.108 | 1.97x |

| 실험군 | JVM1 ms | JVM2 ms | JVM3 ms |
|---|---:|---:|---:|
| spring-manual | 34.919 | 37.281 | 37.013 |
| spring-bean | 66.578 | 67.524 | 68.784 |
| dbutils-manual | 36.781 | 34.701 | 35.570 |
| dbutils-bean-list | 69.667 | 70.108 | 72.471 |
