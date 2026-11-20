# Trantype-update stream hand-off — Track B, part 1

**Status: PROPOSED hand-off, not started.** Chain `trantype-update` in [chains.json](../../../tools/runjcl/chains.json): [MNTTRDB2.jcl](../../../app/app-transaction-type-db2/jcl/MNTTRDB2.jcl) STEP1 → IKJEFT01 `RUN PROGRAM(COBTUPDT) PLAN(CARDDEMO)` → [COBTUPDT](../../../app/app-transaction-type-db2/cbl/COBTUPDT.cbl), against PostgreSQL via Open COBOL ESQL. Base `demo/06-lab @ 1341a98`. Plan: [tracks.md](../../design/tracks.md). Frozen interfaces: [shared-layouts §2 and §3](../../design/shared-layouts.md#2--tables-shared-by-the-trantype-streams).

## Reused unchanged from interest

| Asset | Path | Use |
|---|---|---|
| Toolchain and build skill | `.agents/skills/carddemo-ticket-build` | Java 21, Maven 3.9.9, Spring Boot 3.3.5, Testcontainers 1.21.4, PostgreSQL 15; estate start and legacy baseline. |
| Verify and parity skills | `.agents/skills/carddemo-ticket-verify`, `carddemo-parity` | `compare.py trantype-update <case>`: SYSOUT, `rc.json` and DB snapshot of both tables. |
| Standards and pitfalls | `.agents/skills/carddemo-conversion-standards`, `carddemo-pitfalls` | S01–S19 as written; Docker/mirror pitfalls. |
| Codec | `FixedRecordCodec` X code | INPFILE is three `PIC X` fields (1/2/50), 53 bytes; pad trimmed lines. No D/Z/B fields. |
| Schema | **Not reused**: the target tables are the frozen `carddemo.transaction_type` / `transaction_type_categor` in [db/postgres](../../../db/postgres/01-transaction-type.sql) and `db/seed.sql`, not an `interest` schema. Spring Batch metadata in the stream's own schema. |
| Lab | `lab/` | Pattern only; no trantype lane exists. |

There is no trantype annex under `.agents/skills`; the pilot writes it.

## New for trantype-update

| Item | Source | Recorded oracle |
|---|---|---|
| Line-by-line dispatch on column 1: `A`/`U`/`D`/`*`/other | U 110–129 | t01 add, t02 update, t03 delete, t04 comments, t07 mixed |
| INSERT/UPDATE/DELETE with host variables `:INPUT-REC-NUMBER`, `:INPUT-REC-DESC` | U 137–147, 171–175, 201–204 | DB snapshots |
| SQLCODE evaluation per statement (0, `+100`, `< 0`) and `9999-ABEND` that sets RC 4 and **continues** | U 149–162, 176–193, 205–224, 230–233 | t06 duplicate add: `SQLCODE: -403` in SYSOUT, RC 4, table unchanged |
| `+100` path: source says `No records found.` + RC 4; lab golden says success + RC 0 | U 210; t05 | t05 (golden governs; SH-03) |
| Implicit unit of work: no COMMIT/ROLLBACK; `DSNRUN: COMMIT AT END OF TASK` | `tools/runjcl/runjcl.py` IKJEFT01 | all SYSOUT |
| `STOP RUN` at end (U 99) | U 99 | also binds trantype-dynamic (C4) |
| Exact SYSOUT lines (`OPEN FILE OK`, `PROCESSING   <53 bytes>`, `ADDING RECORD`, …, `WS-VAR-SQLCODE PIC ----9`) | U 85–105, 149–224 | all seven cases |

## Gaps the Track B pilot must close

No standard or skill covers these. **This hand-off writes no standard**; the pilot closes each gap with a proposal and evidence, and the result becomes the trantype annex and, where general, a proposed shared standard via the skills owner.

| Gap | What is missing |
|---|---|
| G1 · Embedded SQL | Mapping of static `EXEC SQL` with host variables to JDBC: CHAR(2) vs `PIC X(2)` binding, VARCHAR(50) from a space-padded `PIC X(50)` (does the description keep trailing spaces? compare the t01/t02 snapshots), schema-qualified names, Db2 vs PostgreSQL identifier `transaction_type_categor`. |
| G2 · SQLCODE handling | A typed mapping from JDBC/SQLSTATE to the legacy SQLCODE values printed in SYSOUT (e.g. `-403` for a PK violation under ocesql where Db2 would give `-803`), the `+100` row-count rule (SH-03), and `----9` formatting. |
| G3 · Unit of work | Commit at end of task with no explicit COMMIT; what a failed statement does to later statements on PostgreSQL (the cause of d03's `-400`); savepoints or not (SH-05). |
| G4 · Callable program boundary | How the Java COBTUPDT runs as a step **and** is callable by trantype-dynamic with C1–C5 (SH-04). Owned by this stream; consumed by the dynamic stream. |
| G5 · Run-time harness | IKJEFT01/DSN `RUN PROGRAM` emulation and the `DSNRUN:` lines are harness output in SYSOUT; decide how the Java adapter emits them for parity. |

Dynamic SQL and the dynamic call are gaps of the [trantype-dynamic hand-off](../trantype-dynamic/handoff.md).

## Open asks

| ID | Ask | Owner |
|---|---|---|
| TU-A1 | SH-03: on Db2, does deleting/updating a missing type end RC 4 with `No records found.`? Which behaviour is the target? | Transaction-reference owner |
| TU-A2 | Production SQLCODE for duplicate add (Db2 `-803`) vs the lab golden `-403`. | Db2 DBA |
| TU-A3 | Control-M runs MNTTRDB2 weekly (`MAXRERUN=5`); is a rerun after RC 4 safe given partial commits at end of task? | Scheduler owner / Operations |
| TU-A4 | The FK `ON DELETE RESTRICT`: no case deletes a type that has categories; a directed golden is needed before that path is claimed. | Fixture owner (record on request) |
| TU-A5 | Online COTRTLIC/COTRTUPC also update `transaction_type`; writer exclusion with the batch job. | CICS owner |
