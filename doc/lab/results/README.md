# Raw experiment data

The top-level `scaling-fork-1.csv` is an **aborted preliminary run**, excluded from the report. Its end-to-end measurement retained an unused buffered ResultSet, adding avoidable memory pressure. It is preserved for audit only.

Final measurements are in `2026-10-30-scaling-v2/`: SQL+fetch measurements do not retain an extra buffered ResultSet. GC count/time deltas are included for each sample. See the report for scope and interpretation.
