# 전체 측정 결과

단위: ms/조회. 각 JVM의 5개 표본 중앙값을 구한 뒤, 3개 JVM 중앙값의 중앙값을 표시한다.

## buffered-mapping

| 행 수 | 필드 수 | 수동 | jorm 자동 | Spring 자동 | DbUtils 자동 | jorm/수동 | Spring/수동 | DbUtils/수동 |
|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| 100 | 3 | 0.020 | 0.024 | 0.047 | 0.055 | 1.22x | 2.39x | 2.79x |
| 100 | 10 | 0.063 | 0.086 | 0.188 | 0.191 | 1.37x | 2.99x | 3.05x |
| 100 | 30 | 0.164 | 0.234 | 0.501 | 0.499 | 1.42x | 3.05x | 3.03x |
| 1,000 | 3 | 0.205 | 0.241 | 0.479 | 0.536 | 1.18x | 2.34x | 2.61x |
| 1,000 | 10 | 0.556 | 0.735 | 1.454 | 1.568 | 1.32x | 2.62x | 2.82x |
| 1,000 | 30 | 1.504 | 2.059 | 4.889 | 5.055 | 1.37x | 3.25x | 3.36x |
| 10,000 | 3 | 1.959 | 2.452 | 4.825 | 5.527 | 1.25x | 2.46x | 2.82x |
| 10,000 | 10 | 5.573 | 7.054 | 14.960 | 15.795 | 1.27x | 2.68x | 2.83x |
| 10,000 | 30 | 16.055 | 22.462 | 52.570 | 51.371 | 1.40x | 3.27x | 3.20x |
| 100,000 | 3 | 21.550 | 26.109 | 52.251 | 58.474 | 1.21x | 2.42x | 2.71x |
| 100,000 | 10 | 65.174 | 76.365 | 174.033 | 175.680 | 1.17x | 2.67x | 2.70x |
| 100,000 | 30 | 227.691 | 292.121 | 606.749 | 600.224 | 1.28x | 2.66x | 2.64x |

## sql-fetch-mapping

| 행 수 | 필드 수 | 수동 | jorm 자동 | Spring 자동 | DbUtils 자동 | jorm/수동 | Spring/수동 | DbUtils/수동 |
|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| 100 | 3 | 0.334 | 0.333 | 0.374 | 0.368 | 1.00x | 1.12x | 1.10x |
| 100 | 10 | 0.380 | 0.406 | 0.549 | 0.541 | 1.07x | 1.44x | 1.42x |
| 100 | 30 | 0.728 | 0.811 | 1.131 | 1.147 | 1.11x | 1.55x | 1.57x |
| 1,000 | 3 | 1.266 | 1.275 | 1.483 | 1.623 | 1.01x | 1.17x | 1.28x |
| 1,000 | 10 | 2.012 | 2.160 | 3.285 | 3.290 | 1.07x | 1.63x | 1.64x |
| 1,000 | 30 | 4.779 | 5.716 | 7.788 | 7.876 | 1.20x | 1.63x | 1.65x |
| 10,000 | 3 | 6.711 | 7.463 | 10.016 | 11.372 | 1.11x | 1.49x | 1.69x |
| 10,000 | 10 | 13.805 | 15.769 | 24.663 | 24.303 | 1.14x | 1.79x | 1.76x |
| 10,000 | 30 | 35.272 | 40.664 | 69.944 | 69.794 | 1.15x | 1.98x | 1.98x |
| 100,000 | 3 | 57.836 | 65.194 | 82.832 | 101.037 | 1.13x | 1.43x | 1.75x |
| 100,000 | 10 | 142.136 | 153.791 | 265.026 | 258.743 | 1.08x | 1.86x | 1.82x |
| 100,000 | 30 | 383.606 | 458.303 | 809.088 | 772.589 | 1.19x | 2.11x | 2.01x |

## JVM별 중앙값과 GC

GC 값은 해당 조건의 15개 측정 표본에 기록된 GC 누계다. 동시 GC 시간은 조회 시간에 단순 가산할 수 없다.

| 범위 | 행 | 필드 | 실험군 | JVM1 ms | JVM2 ms | JVM3 ms | GC 횟수 | GC ms |
|---|---:|---:|---|---:|---:|---:|---:|---:|
| buffered-mapping | 100 | 3 | manual | 0.021 | 0.019 | 0.020 | 0 | 0 |
| buffered-mapping | 100 | 3 | jorm-reflection | 0.024 | 0.023 | 0.024 | 0 | 0 |
| buffered-mapping | 100 | 3 | spring-bean | 0.047 | 0.062 | 0.043 | 1 | 2 |
| buffered-mapping | 100 | 3 | dbutils-bean-list | 0.063 | 0.052 | 0.055 | 2 | 3 |
| buffered-mapping | 100 | 10 | manual | 0.061 | 0.063 | 0.063 | 1 | 1 |
| buffered-mapping | 100 | 10 | jorm-reflection | 0.088 | 0.074 | 0.086 | 0 | 0 |
| buffered-mapping | 100 | 10 | spring-bean | 0.223 | 0.188 | 0.174 | 3 | 4 |
| buffered-mapping | 100 | 10 | dbutils-bean-list | 0.182 | 0.201 | 0.191 | 1 | 1 |
| buffered-mapping | 100 | 30 | manual | 0.164 | 0.170 | 0.151 | 5 | 9 |
| buffered-mapping | 100 | 30 | jorm-reflection | 0.212 | 0.234 | 0.241 | 1 | 1 |
| buffered-mapping | 100 | 30 | spring-bean | 0.501 | 0.504 | 0.489 | 7 | 10 |
| buffered-mapping | 100 | 30 | dbutils-bean-list | 0.499 | 0.484 | 0.500 | 5 | 10 |
| buffered-mapping | 1,000 | 3 | manual | 0.205 | 0.224 | 0.203 | 0 | 0 |
| buffered-mapping | 1,000 | 3 | jorm-reflection | 0.241 | 0.275 | 0.230 | 0 | 0 |
| buffered-mapping | 1,000 | 3 | spring-bean | 0.458 | 0.617 | 0.479 | 0 | 0 |
| buffered-mapping | 1,000 | 3 | dbutils-bean-list | 0.586 | 0.536 | 0.513 | 0 | 0 |
| buffered-mapping | 1,000 | 10 | manual | 0.556 | 0.528 | 0.597 | 0 | 0 |
| buffered-mapping | 1,000 | 10 | jorm-reflection | 0.666 | 0.749 | 0.735 | 1 | 2 |
| buffered-mapping | 1,000 | 10 | spring-bean | 1.454 | 1.676 | 1.430 | 4 | 6 |
| buffered-mapping | 1,000 | 10 | dbutils-bean-list | 1.568 | 1.678 | 1.512 | 1 | 1 |
| buffered-mapping | 1,000 | 30 | manual | 1.533 | 1.391 | 1.504 | 3 | 4 |
| buffered-mapping | 1,000 | 30 | jorm-reflection | 2.033 | 2.059 | 2.181 | 3 | 6 |
| buffered-mapping | 1,000 | 30 | spring-bean | 4.889 | 5.437 | 4.574 | 4 | 7 |
| buffered-mapping | 1,000 | 30 | dbutils-bean-list | 5.055 | 5.607 | 4.697 | 7 | 12 |
| buffered-mapping | 10,000 | 3 | manual | 1.931 | 1.959 | 2.217 | 0 | 0 |
| buffered-mapping | 10,000 | 3 | jorm-reflection | 2.501 | 2.452 | 2.407 | 0 | 0 |
| buffered-mapping | 10,000 | 3 | spring-bean | 4.315 | 6.085 | 4.825 | 3 | 8 |
| buffered-mapping | 10,000 | 3 | dbutils-bean-list | 5.527 | 5.618 | 5.502 | 0 | 0 |
| buffered-mapping | 10,000 | 10 | manual | 5.514 | 5.573 | 5.836 | 1 | 3 |
| buffered-mapping | 10,000 | 10 | jorm-reflection | 6.912 | 7.054 | 7.251 | 1 | 2 |
| buffered-mapping | 10,000 | 10 | spring-bean | 14.960 | 17.979 | 14.627 | 1 | 4 |
| buffered-mapping | 10,000 | 10 | dbutils-bean-list | 15.795 | 17.916 | 15.563 | 2 | 4 |
| buffered-mapping | 10,000 | 30 | manual | 16.421 | 15.177 | 16.055 | 1 | 5 |
| buffered-mapping | 10,000 | 30 | jorm-reflection | 22.017 | 22.462 | 23.167 | 1 | 6 |
| buffered-mapping | 10,000 | 30 | spring-bean | 50.432 | 52.570 | 53.130 | 9 | 56 |
| buffered-mapping | 10,000 | 30 | dbutils-bean-list | 51.371 | 50.802 | 53.155 | 6 | 25 |
| buffered-mapping | 100,000 | 3 | manual | 22.462 | 21.444 | 21.550 | 1 | 2 |
| buffered-mapping | 100,000 | 3 | jorm-reflection | 26.109 | 25.296 | 29.087 | 0 | 0 |
| buffered-mapping | 100,000 | 3 | spring-bean | 51.736 | 53.105 | 52.251 | 8 | 47 |
| buffered-mapping | 100,000 | 3 | dbutils-bean-list | 58.474 | 60.509 | 57.437 | 3 | 15 |
| buffered-mapping | 100,000 | 10 | manual | 60.833 | 78.294 | 65.174 | 6 | 47 |
| buffered-mapping | 100,000 | 10 | jorm-reflection | 76.365 | 83.006 | 74.816 | 0 | 0 |
| buffered-mapping | 100,000 | 10 | spring-bean | 163.003 | 226.910 | 174.033 | 16 | 159 |
| buffered-mapping | 100,000 | 10 | dbutils-bean-list | 173.021 | 204.301 | 175.680 | 16 | 140 |
| buffered-mapping | 100,000 | 30 | manual | 227.691 | 258.317 | 207.529 | 72 | 713 |
| buffered-mapping | 100,000 | 30 | jorm-reflection | 293.034 | 292.121 | 280.046 | 71 | 666 |
| buffered-mapping | 100,000 | 30 | spring-bean | 606.749 | 645.889 | 564.081 | 158 | 1137 |
| buffered-mapping | 100,000 | 30 | dbutils-bean-list | 628.745 | 600.224 | 587.789 | 151 | 960 |
| sql-fetch-mapping | 100 | 3 | manual | 0.303 | 0.334 | 0.337 | 1 | 1 |
| sql-fetch-mapping | 100 | 3 | jorm-reflection | 0.333 | 0.323 | 0.365 | 1 | 1 |
| sql-fetch-mapping | 100 | 3 | spring-bean | 0.374 | 0.374 | 0.397 | 1 | 1 |
| sql-fetch-mapping | 100 | 3 | dbutils-bean-list | 0.368 | 0.386 | 0.355 | 0 | 0 |
| sql-fetch-mapping | 100 | 10 | manual | 0.398 | 0.378 | 0.380 | 1 | 1 |
| sql-fetch-mapping | 100 | 10 | jorm-reflection | 0.436 | 0.406 | 0.402 | 0 | 0 |
| sql-fetch-mapping | 100 | 10 | spring-bean | 0.592 | 0.549 | 0.509 | 3 | 5 |
| sql-fetch-mapping | 100 | 10 | dbutils-bean-list | 0.594 | 0.541 | 0.516 | 3 | 4 |
| sql-fetch-mapping | 100 | 30 | manual | 0.721 | 0.749 | 0.728 | 1 | 2 |
| sql-fetch-mapping | 100 | 30 | jorm-reflection | 0.841 | 0.798 | 0.811 | 6 | 11 |
| sql-fetch-mapping | 100 | 30 | spring-bean | 1.174 | 1.121 | 1.131 | 9 | 10 |
| sql-fetch-mapping | 100 | 30 | dbutils-bean-list | 1.165 | 1.147 | 1.107 | 7 | 10 |
| sql-fetch-mapping | 1,000 | 3 | manual | 1.266 | 1.262 | 1.409 | 0 | 0 |
| sql-fetch-mapping | 1,000 | 3 | jorm-reflection | 1.243 | 1.347 | 1.275 | 1 | 2 |
| sql-fetch-mapping | 1,000 | 3 | spring-bean | 1.353 | 1.599 | 1.483 | 0 | 0 |
| sql-fetch-mapping | 1,000 | 3 | dbutils-bean-list | 1.515 | 1.623 | 1.674 | 2 | 4 |
| sql-fetch-mapping | 1,000 | 10 | manual | 2.179 | 1.964 | 2.012 | 0 | 0 |
| sql-fetch-mapping | 1,000 | 10 | jorm-reflection | 2.396 | 2.146 | 2.160 | 3 | 5 |
| sql-fetch-mapping | 1,000 | 10 | spring-bean | 3.326 | 3.285 | 2.995 | 3 | 4 |
| sql-fetch-mapping | 1,000 | 10 | dbutils-bean-list | 3.370 | 3.290 | 3.063 | 2 | 3 |
| sql-fetch-mapping | 1,000 | 30 | manual | 4.767 | 4.816 | 4.779 | 6 | 8 |
| sql-fetch-mapping | 1,000 | 30 | jorm-reflection | 6.449 | 4.960 | 5.716 | 1 | 2 |
| sql-fetch-mapping | 1,000 | 30 | spring-bean | 8.181 | 7.698 | 7.788 | 11 | 21 |
| sql-fetch-mapping | 1,000 | 30 | dbutils-bean-list | 7.709 | 8.204 | 7.876 | 4 | 6 |
| sql-fetch-mapping | 10,000 | 3 | manual | 6.497 | 8.018 | 6.711 | 2 | 4 |
| sql-fetch-mapping | 10,000 | 3 | jorm-reflection | 6.658 | 8.670 | 7.463 | 1 | 2 |
| sql-fetch-mapping | 10,000 | 3 | spring-bean | 10.016 | 11.952 | 9.273 | 0 | 0 |
| sql-fetch-mapping | 10,000 | 3 | dbutils-bean-list | 9.858 | 12.670 | 11.372 | 1 | 2 |
| sql-fetch-mapping | 10,000 | 10 | manual | 13.587 | 13.805 | 13.898 | 1 | 2 |
| sql-fetch-mapping | 10,000 | 10 | jorm-reflection | 17.568 | 15.738 | 15.769 | 0 | 0 |
| sql-fetch-mapping | 10,000 | 10 | spring-bean | 22.524 | 24.663 | 24.884 | 3 | 10 |
| sql-fetch-mapping | 10,000 | 10 | dbutils-bean-list | 25.749 | 24.303 | 23.732 | 3 | 6 |
| sql-fetch-mapping | 10,000 | 30 | manual | 35.811 | 32.976 | 35.272 | 4 | 14 |
| sql-fetch-mapping | 10,000 | 30 | jorm-reflection | 40.664 | 39.317 | 42.031 | 4 | 16 |
| sql-fetch-mapping | 10,000 | 30 | spring-bean | 69.594 | 70.871 | 69.944 | 8 | 47 |
| sql-fetch-mapping | 10,000 | 30 | dbutils-bean-list | 69.794 | 67.683 | 71.083 | 7 | 30 |
| sql-fetch-mapping | 100,000 | 3 | manual | 55.946 | 68.535 | 57.836 | 5 | 18 |
| sql-fetch-mapping | 100,000 | 3 | jorm-reflection | 65.194 | 77.388 | 63.100 | 4 | 16 |
| sql-fetch-mapping | 100,000 | 3 | spring-bean | 80.225 | 110.020 | 82.832 | 4 | 28 |
| sql-fetch-mapping | 100,000 | 3 | dbutils-bean-list | 97.693 | 112.070 | 101.037 | 5 | 33 |
| sql-fetch-mapping | 100,000 | 10 | manual | 129.744 | 142.136 | 151.345 | 11 | 105 |
| sql-fetch-mapping | 100,000 | 10 | jorm-reflection | 148.829 | 170.381 | 153.791 | 14 | 115 |
| sql-fetch-mapping | 100,000 | 10 | spring-bean | 243.806 | 267.280 | 265.026 | 32 | 336 |
| sql-fetch-mapping | 100,000 | 10 | dbutils-bean-list | 236.975 | 260.780 | 258.743 | 17 | 193 |
| sql-fetch-mapping | 100,000 | 30 | manual | 401.288 | 383.606 | 374.530 | 99 | 918 |
| sql-fetch-mapping | 100,000 | 30 | jorm-reflection | 465.264 | 458.303 | 449.376 | 106 | 883 |
| sql-fetch-mapping | 100,000 | 30 | spring-bean | 823.596 | 809.088 | 745.905 | 184 | 1545 |
| sql-fetch-mapping | 100,000 | 30 | dbutils-bean-list | 791.510 | 772.589 | 758.952 | 167 | 1265 |
