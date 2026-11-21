# Transaction-type batch jobs

## MNTTRDB2 / COBTUPDT

The Java 21 Spring Batch job reads the fixture/unloaded form of INPFILE
(one Latin-1 record per line). It pads or truncates each line to 53 bytes:
one operation (A/U/D/*), a two-byte type code, and a 50-byte description.
The job uses the established PostgreSQL transaction-type tables from
`db/postgres/01-transaction-type.sql`; it does not change their schema.

Run from the repository root with JDK 21, Maven and Docker Compose:

```sh
make parity-java CHAIN=trantype-update
```

The target builds the job, starts the Compose `postgres` service, and creates
and drops a scratch database per fixture without touching the API or legacy
database. It compares STEP1 SYSOUT and RC plus ordered PostgreSQL CSV snapshots
of both tables. Pass `CASE=...` to run just one fixture.

To run the packaged job separately, set `SPRING_DATASOURCE_URL`,
`SPRING_DATASOURCE_USERNAME`, and `SPRING_DATASOURCE_PASSWORD` for a database
with the transaction-type and Spring Batch metadata tables, then run:

```sh
java -jar java/trantype-batch/target/trantype-batch.jar input=/path/to/INPFILE.txt out=/path/to/output
```

Use a different `run.id` job parameter to submit the same paths as a new run.

## Legacy behavior kept

- INSERT/UPDATE keep the full space-padded 50-byte description in VARCHAR.
- Missing UPDATE/DELETE targets still produce success SYSOUT and RC 0
  (the legacy +100 quirk as observed through ocesql).
- A duplicate insert reports ocesql SQLCODE -403 and raises STEP1 RC to 4.
  The job continues reading; PostgreSQL rolls back the aborted transaction at
  end of task. An invalid operation also raises RC 4 but does not abort the
  transaction. The Spring Batch execution can complete with RC 4 in `rc.json`;
  a process failure instead indicates the job did not finish.

## MNTTRDYN / CBTRNDYN

```sh
make parity-java CHAIN=trantype-dynamic
```

To run with the same datasource setup as MNTTRDB2:

```sh
java -jar java/trantype-batch/target/trantype-batch.jar --spring.batch.job.name=MNTTRDYN \
  dynsql=/path/to/DYNSQL.txt parm=COBTUPDT input=/path/to/INPFILE.txt out=/path/to/output
```

DYNSQL is a trusted operator-supplied file, not application input. The job takes
columns 1–72 from each Latin-1 record, skipping records that start with `--`, and
runs one parameterless INSERT, UPDATE, or DELETE with the `carddemo` search path.
Use a dedicated database role with only the privileges the job needs.

PARM supports `COBTUPDT` (requires INPFILE), or blank to run only the SQL.
The SQL and COBTUPDT use the same PostgreSQL transaction. A failed statement
leaves the transaction aborted; COBTUPDT still runs and controls the step RC
when called. This preserves `d03-bad-statement` (RC 4, no committed writes).
