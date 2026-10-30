"""Validate raw samples and generate tables/plots. Requires matplotlib for plots."""
import csv
import statistics
import sys
from collections import defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parent
DATA = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / "results/2026-10-30-scaling-v2"
GROUPS = ["manual", "jorm-reflection", "spring-bean", "dbutils-bean-list"]
ROWS = [100, 1000, 10000, 100000]
COLUMNS = [3, 10, 30]
SCOPES = ["buffered-mapping", "sql-fetch-mapping"]


def load():
    samples = defaultdict(list)
    raw = []
    for fork in (1, 2, 3):
        with (DATA / f"scaling-fork-{fork}.csv").open(newline="") as file:
            entries = list(csv.DictReader(file))
        assert len(entries) == 480, (fork, len(entries))
        for entry in entries:
            assert int(entry["fork"]) == fork
            key = (entry["scope"], int(entry["rows"]), int(entry["columns"]), entry["group"], fork)
            samples[key].append(entry)
            raw.append(entry)
    assert len(samples) == 288
    values = {}
    for scope in SCOPES:
        for rows in ROWS:
            for columns in COLUMNS:
                for group in GROUPS:
                    medians = []
                    for fork in (1, 2, 3):
                        entries = samples[(scope, rows, columns, group, fork)]
                        assert sorted(int(e["sample"]) for e in entries) == [1, 2, 3, 4, 5]
                        medians.append(statistics.median(float(e["ms_per_query"]) for e in entries))
                    values[(scope, rows, columns, group)] = medians
    return values, raw


def aggregate(values, scope, rows, columns, group):
    return statistics.median(values[(scope, rows, columns, group)])


def write_tables(values, raw):
    lines = ["# 전체 측정 결과", "", "단위: ms/조회. 각 JVM의 5개 표본 중앙값을 구한 뒤, 3개 JVM 중앙값의 중앙값을 표시한다.", ""]
    for scope in SCOPES:
        lines += [f"## {scope}", "", "| 행 수 | 필드 수 | 수동 | jorm 자동 | Spring 자동 | DbUtils 자동 | jorm/수동 | Spring/수동 | DbUtils/수동 |", "|---:|---:|---:|---:|---:|---:|---:|---:|---:|"]
        for rows in ROWS:
            for columns in COLUMNS:
                v = [aggregate(values, scope, rows, columns, g) for g in GROUPS]
                lines.append(f"| {rows:,} | {columns} | " + " | ".join(f"{x:.3f}" for x in v) + " | " + " | ".join(f"{x/v[0]:.2f}x" for x in v[1:]) + " |")
        lines.append("")
    lines += ["## JVM별 중앙값과 GC", "", "GC 값은 해당 조건의 15개 측정 표본에 기록된 GC 누계다. 동시 GC 시간은 조회 시간에 단순 가산할 수 없다.", "", "| 범위 | 행 | 필드 | 실험군 | JVM1 ms | JVM2 ms | JVM3 ms | GC 횟수 | GC ms |", "|---|---:|---:|---|---:|---:|---:|---:|---:|"]
    for key, medians in values.items():
        scope, rows, columns, group = key
        entries = [e for e in raw if (e["scope"], int(e["rows"]), int(e["columns"]), e["group"]) == key]
        lines.append(f"| {scope} | {rows:,} | {columns} | {group} | " + " | ".join(f"{v:.3f}" for v in medians) + f" | {sum(int(e['gc_count']) for e in entries)} | {sum(int(e['gc_ms']) for e in entries)} |")
    (DATA / "tables.md").write_text("\n".join(lines) + "\n", encoding="utf-8")
    with (DATA / "summary.csv").open("w", newline="", encoding="utf-8") as file:
        writer = csv.writer(file)
        writer.writerow(["scope", "rows", "columns", "group", "fork1_ms", "fork2_ms", "fork3_ms", "median_ms"])
        for key, medians in values.items():
            writer.writerow([*key, *medians, statistics.median(medians)])


def plot(values):
    import matplotlib
    matplotlib.use("Agg")
    import matplotlib.pyplot as plt
    for scope in SCOPES:
        fig, axes = plt.subplots(2, 2, figsize=(12, 8), constrained_layout=True)
        for ax, columns in zip(axes[0], (3, 30)):
            for group in GROUPS:
                ax.plot(ROWS, [aggregate(values, scope, r, columns, group) for r in ROWS], marker="o", label=group)
            ax.set(xscale="log", yscale="log", xlabel="Rows", ylabel="ms / query", title=f"Row scaling ({columns} columns)")
        for ax, rows in zip(axes[1], (10000, 100000)):
            for group in GROUPS:
                ax.plot(COLUMNS, [aggregate(values, scope, rows, c, group) for c in COLUMNS], marker="o", label=group)
            ax.set(xlabel="Columns", ylabel="ms / query", title=f"Column scaling ({rows:,} rows)", xticks=COLUMNS)
        for ax in axes.flat:
            ax.grid(alpha=0.25)
        axes[0, 0].legend(fontsize=8)
        fig.suptitle(f"MySQL mapping experiment: {scope}\nMedian of 3 JVM medians; SQL/fetch excluded only in buffered scope")
        fig.savefig(DATA / f"{scope}.png", dpi=160)
        fig.savefig(DATA / f"{scope}.svg")
        plt.close(fig)


def library_tables():
    samples = defaultdict(list)
    for fork in (1, 2, 3):
        with (DATA / f"library-fork-{fork}.csv").open(newline="") as file:
            entries = list(csv.DictReader(file))
        assert len(entries) == 20
        for entry in entries:
            assert int(entry["fork"]) == fork
            samples[(entry["group"], fork)].append(entry)
    medians = {}
    for group in ("spring-manual", "spring-bean", "dbutils-manual", "dbutils-bean-list"):
        medians[group] = []
        for fork in (1, 2, 3):
            entries = samples[(group, fork)]
            assert sorted(int(e["sample"]) for e in entries) == [1, 2, 3, 4, 5]
            medians[group].append(statistics.median(float(e["ms_per_query"]) for e in entries))
    lines = ["# 실제 라이브러리 조회 API 비교", "", "10,000행 × 30필드, SQL·결과 전송·매핑·statement 정리 포함, 연결 생성 제외.", "", "| 라이브러리 | 수동 ms | 자동 ms | 자동/수동 |", "|---|---:|---:|---:|"]
    for library, manual, auto in (("Spring JDBC", "spring-manual", "spring-bean"), ("Commons DbUtils", "dbutils-manual", "dbutils-bean-list")):
        m, a = statistics.median(medians[manual]), statistics.median(medians[auto])
        lines.append(f"| {library} | {m:.3f} | {a:.3f} | {a/m:.2f}x |")
    lines += ["", "| 실험군 | JVM1 ms | JVM2 ms | JVM3 ms |", "|---|---:|---:|---:|"]
    for group, values in medians.items():
        lines.append(f"| {group} | " + " | ".join(f"{x:.3f}" for x in values) + " |")
    (DATA / "library-tables.md").write_text("\n".join(lines) + "\n", encoding="utf-8")
    print("Verified 60 library API samples")


if __name__ == "__main__":
    values, raw = load()
    write_tables(values, raw)
    plot(values)
    library_tables()
    print(f"Verified {len(raw)} samples; wrote tables, summary CSV and plots to {DATA}")
