# Read masters (D3)

Java 21 file-to-file port of READACCT / CBACT01C, READCARD / CBACT02C,
READXREF / CBACT03C and READCUST / CBCUS01C. Uses `java/common`'s
`FixedRecordCodec`, copybook layouts, `CobDatFt` and `BatchArtifacts`.
These read-only utilities do not need PostgreSQL or a job repository: the
fixture unloads are read in indexed-key order, without changing the masters.
There are no new database columns; existing business-name schema mappings
remain unchanged, and file exports retain copybook names.

```sh
mvn -B -f java/pom.xml -pl read-masters -am test
make parity-java CHAIN=read-masters
```

The Make target packages `target/read-masters.jar` and runs every fixture via
the standard candidate seam. A custom `CANDIDATE` bypasses the default build.
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
* Fixture lines are space-padded or truncated to LRECL, just as in `record.py`.
* PREDEL returns zero. Missing inputs produce file-status
  diagnostics and the off-host CEE3ABD U+000000999 message with step RC 12,
  omit the success trailer, and do not prevent later independent jobs running.
  The candidate process returns zero once artifacts are written, leaving the
  comparator to assess step return codes. Host output-write failures propagate.

The populated and empty fixtures are the parity proof; synthetic unit tests
also cover ordering, duplicate keys, retained debit, missing inputs, padding/truncation
and invalid CLI arguments. Error cases have no approved golden fixtures, so
their proof is source/stub-based rather than golden parity.
