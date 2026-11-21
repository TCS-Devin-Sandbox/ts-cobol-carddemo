# CardDemo batch foundation

Run `mvn -B verify` from `java/` with JDK 21. The reactor parent uses Spring Boot
dependency management for Spring Batch, Flyway, PostgreSQL and Testcontainers.
The shared APIs provide codecs, JDBC master transport and batch artifact writers.
They do not perform business logic or automatically migrate a database.

Spring Boot modules that depend on `carddemo-batch-common` can inject a `Clock`
fixed at 2022-07-05 10:00:00 UTC, matching the legacy parity fixtures.

Activate the Spring `docker` profile with `SPRING_PROFILES_ACTIVE=docker` when
running a batch application inside the `estate` service or another container
on its Docker Compose network. The profile uses `db:5432/carddemo`, the same
database as the legacy harness, and reads `PGUSER` and `PGPASSWORD` with the
existing Compose defaults. This profile is not for host-side connections.

## No-op transport proof

Build with JDK 21, then run the candidate **on the host**, from the repository root:

```sh
make up
cd java && mvn -B verify && mvn -B -pl common dependency:copy-dependencies -DincludeScope=runtime
cd ..
# Linux: connect to the harness db without exposing another port.
export B4_JDBC_URL="jdbc:postgresql://$(docker inspect -f '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' "$(docker compose ps -q db)"):5432/carddemo"
make parity-java CHAIN=interest CANDIDATE='java -cp java/common/target/classes:java/common/target/dependency/* com.carddemo.batch.common.NoOpCandidate'
make parity-java CHAIN=posting CANDIDATE='java -cp java/common/target/classes:java/common/target/dependency/* com.carddemo.batch.common.NoOpCandidate'
```

Both comparisons intentionally fail: no interest, posting, rejects or reports are
calculated, SYSOUT is empty, and every step RC is zero. Inputs used to seed the
transaction master are `DALYTRAN.PS.INIT`; `DALYTRAN.PS` is the unprocessed daily
stream, not a second master. Each invocation resets **only** `b4_candidate` in
the selected database. Do not run invocations concurrently against that schema.
`PGUSER` / `PGPASSWORD` default to the local synthetic harness credentials.
Alternatively use the port-published Compose `postgres` service (the default
URL is `jdbc:postgresql://localhost:5432/carddemo`), including on Docker Desktop.

`VsamDataset` is the single copybook-field-order to business-column mapping.
`VsamTransfer.load` replaces one master in an explicit caller-owned transaction
and rolls back that load on failure; `export` orders by its primary key (C
collation for text). PostgreSQL is the only source of export values: loading and
exporting may use separate connections, instances or processes. Fixture lines use
canonical codec numeric representations, omit trailing ASCII spaces, and end in
LF; empty tables produce empty files. All 98 interest/posting master input files
follow this convention. Filler is stored in PostgreSQL, not regenerated; only
trailing spaces are omitted from the serialized line and restored by fixed-width
readers. Arbitrary source formatting (noncanonical signs, negative zero, padded
lines or missing final LF) is normalized, not retained. Lossless reproduction of
such non-fixture representations would require persisted provenance, outside
this fixture transport contract. Writers use the golden
one-space JSON indentation, no final JSON newline, and Latin-1 SYSOUT text.

## HLASM replacements

`com.carddemo.batch.common.legacy` replaces the two HLASM routines without an
assembler: `CobDatFt` is `COBDATFT` on its 80-byte `CODATECN-REC`
(`CodatecnRecord`), byte for byte including the quirks listed in
`docs/LEGACY-LANGUAGES.md`; `MvsWait` is `MVSWAIT`/`COBSWAIT`, a no-op by
default because a wait has no functional output. Neither routine runs in a
parity chain, so their proof is the unit tests against the documented formats.
