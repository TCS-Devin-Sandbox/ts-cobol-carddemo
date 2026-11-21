# Run and restart the Java batch chains

Trigger each chain's executable from the scheduler. There is no cross-chain
ordering or calendar schedule inferred here; the chain definitions and handoffs
are in [the generated graph](chain-graph.md). The Java order is:

| Chain | Ordered steps (after any input staging) |
| --- | --- |
| posting | POSTTRAN STEP15 → TRANREPT REPRO (PRC001) → SORT (STEP05R) → report (STEP10R) |
| read-masters | READACCT PREDEL → READACCT STEP05 → READCARD STEP05 → READXREF STEP05 → READCUST STEP05 |
| interest | INTCALC STEP15 |
| trantype-update | MNTTRDB2 STEP1 |
| trantype-dynamic | MNTTRDYN STEP1, including its configured follow-up call |

## Fixture-backed chains: posting, interest, read-masters

Build with `mvn -f java/pom.xml -pl posting,interest,read-masters -am package`.
Use PostgreSQL 15 and a role that owns its run schemas. Set the module's JDBC
URL (`POSTING_JDBC_URL`, `INTCALC_JDBC_URL`, or `READ_MASTERS_JDBC_URL`)
and `PGUSER` / `PGPASSWORD` for that database. For example:

```sh
java -jar java/posting/target/carddemo-posting.jar \
  --chain posting --case p01-baseline-mixed --run-id nightly_20220705 \
  --input fixtures/posting/p01-baseline-mixed/input --out work/posting/nightly_20220705 \
  --clock '2022-07-05 10:00:00.00'
```

Use the same command shape with `intcalc.jar` or `read-masters.jar` and
their corresponding chain, case and input. A new run ID gets a private schema
(`posting_<id>`, `intcalc_<id>` or `read_masters_<id>`) with the staged
masters and Spring Batch repository. Use a private, durable output directory
per run. A database advisory lock prevents overlapping launches of the same
chain and run ID. If a step fails, fix the operational error and rerun with the
same ID, database, input files, output path and clock. Input fingerprints
detect changed `.txt` files; keep the inputs read-only throughout the run.
Completed steps are skipped. If only final export failed after the job
completed, the command retries the export without executing the steps again.
Without `--run-id`, the fixture runner still uses a disposable schema.

## Transaction-type maintenance

Point the existing `trantype-batch.jar` launcher at the persistent PostgreSQL
database (via `SPRING_DATASOURCE_*`). Install Spring Batch's PostgreSQL metadata
tables once, e.g. with `--spring.batch.jdbc.initialize-schema=always` on the
first launch in a new database. Pass a stable identifying `run.id=...` and the
same absolute `input=...` and `out=...` parameters on retry. For dynamic
maintenance use `--spring.batch.job.name=MNTTRDYN`, `dynsql=...` and the same
`parm=...` when supplied. Keep both input files immutable; this launcher
identifies them by path, not by fingerprint. The parity wrapper still uses a
disposable database.

## Recovery boundary

Checkpoints are **whole steps**, not records or chunks. Mutating steps commit
PostgreSQL business changes and their Spring Batch checkpoint in the same
transaction. Legacy return codes are recorded as artifacts and retain the
existing JCL behavior; an infrastructure exception fails the step.

Files are not part of the database transaction. A retried step rewrites its
private files at fixed names (including the same GDG generation). Preserve all
intermediate artifacts and consume outputs only after the launcher succeeds.
This does not provide atomic publication or recovery from storage loss. A
process killed mid-execution can leave the repository in `STARTED`; the
launcher does not automatically reclaim it. Fence the old process and
reconcile its database and files before manual recovery. Retire completed
run schemas and private outputs per your retention policy.
