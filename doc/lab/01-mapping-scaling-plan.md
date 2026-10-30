# Mapping scaling experiment plan

**Goal:** Separate row-count and column-count effects on JDBC mapping and record reproducible MySQL measurements.

**Design:** 100/1,000/10,000/100,000 rows × 3/10/30 columns; BIGINT id plus deterministic VARCHAR values. Reuse one connection and session-only temporary tables in the existing benchmark database. Compare handwritten mapping, simple-jorm reflection, Spring BeanPropertyRowMapper and Commons DbUtils BeanProcessor.toBeanList. Do not infer whole-framework performance from mapping-component measurements.

**Execution:** 3 independent JVM processes; 2 warmup rounds, 5 measured rounds, shuffled mapper order with recorded deterministic seed. Measure buffered mapping and SQL+fetch+mapping separately. Reuse mapper instances. Validate every mapped field before measurement; retain result objects. Report per-fork medians and aggregate median, preserve raw samples and environment. No credentials in files.

- [x] Generate 3/10/30-field JavaBeans and handwritten mappers; verify all implementations return identical values.
- [x] Run full matrix, preserve 1,440 CSV samples and hardware/JVM/DB settings.
- [x] Explain library strategies using official docs/source, including libraries supporting both manual and automatic mapping. Added 60 paired Spring/DbUtils query-API samples at 10,000 rows / 30 columns.
- [x] Write report and repeatability instructions in doc/lab; run existing tests (BUILD SUCCESSFUL).

Ruling: abort preliminary run retaining an unused ResultSet during SQL+fetch timing; preserve it as excluded data and rerun all 3 JVMs with this reference removed. Record GC count/time deltas in final samples. No product mapping behavior was changed.
