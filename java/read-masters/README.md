# Read masters (D3)

Java 21 file-to-file port of READACCT / CBACT01C, READCARD / CBACT02C,
READXREF / CBACT03C and READCUST / CBCUS01C. Uses `java/common`'s
`FixedRecordCodec`, copybook layouts, `CobDatFt` and `BatchArtifacts`.
Like the interest/posting candidates, each invocation creates a fresh owned
PostgreSQL schema, runs the shared Flyway migrations, and loads all four masters
with `VsamTransfer.load`. Four Spring Batch jobs each execute a STEP05 tasklet
reading its PostgreSQL master in primary-key order (text keys use C collation).
`ReadMasterStore` maps the business-named columns back to copybook field names
for the codec and displays. The schema, including the Batch repository, is dropped
in a finally block on success or failure. No shared tables or DDL are changed.

```sh
mvn -B -f java/pom.xml -pl read-masters -am test
make parity-java CHAIN=read-masters
```

Tests need Docker and use Testcontainers PostgreSQL 15. The Make target packages
`target/read-masters.jar`, starts the Compose `postgres` service, and runs every
fixture via the standard candidate seam. A custom `CANDIDATE` bypasses that setup.
Connection settings follow posting's pattern: `READ_MASTERS_JDBC_URL` (default
`jdbc:postgresql://localhost:5432/carddemo`), `PGUSER` and `PGPASSWORD` (both
default `carddemo`). For the estate network / shared `docker` profile, set
`SPRING_PROFILES_ACTIVE=docker` and
`READ_MASTERS_JDBC_URL=jdbc:postgresql://db:5432/carddemo`.
The CLI accepts `--chain read-masters --case NAME --input DIR --out DIR
--clock '2022-07-05 10:00:00.00'`; case and clock are harness metadata (these
programs do not use the clock). Outputs are `datasets/`, `sysout/`, `rc.json`.

## Preserved quirks

* READACCT displays individual numbers as unsigned magnitude plus trailing
  sign, but group DISPLAY preserves the raw EBCDIC-overpunch characters.
* READCARD displays each record once; READXREF and READCUST display it twice.
* READACCT leaves its output files open, so SYSOUT includes the three GnuCOBOL
  implicit-CLOSE warnings, including the separating blank line, even when empty.
* PSCOMP contains packed decimal and ARRYPS contains five expanded occurrences;
  both use the oracle's lowercase hex-per-record serialization, not text decoding
  of binary bytes. VBPS contains alternating 12- and 39-byte text records.
* Output reissue dates are `YYYYMMDD` plus two spaces, using the shared legacy
  date routine. VBPS retains the input reissue year.
* PSCOMP debit is assigned 2525.00 only when the input debit is zero; otherwise
  output storage is retained. Array entries 1/2 use fixed debit values, entry 3
  is negative, and entries 4/5 are initialized zero (unsigned DISPLAY zeros).
* The shared B4 loader space-pads trimmed fixture lines. As with other PG ports,
  invalid/oversize records and duplicate primary keys fail loading rather than
  silently truncating or ignoring them.
* READACCT's PREDEL step deletes its three prior exports before STEP05, including
  on a missing-input rerun into an existing output directory, and returns zero.
  Missing inputs produce file-status
  diagnostics and the off-host CEE3ABD U+000000999 message with step RC 12,
  omit the success trailer, and do not prevent later independent jobs running.
  The candidate process returns zero once artifacts are written, leaving the
  comparator to assess step return codes. Host output-write failures propagate.

The populated and empty fixtures are the parity proof; synthetic unit tests
also cover ordering, retained debit, missing inputs, loader errors, schema cleanup,
invalid CLI arguments, and all four programs observing changed PostgreSQL business
columns after the source files have been deleted. Error cases have no approved golden fixtures, so
their proof is source/stub-based rather than golden parity.
