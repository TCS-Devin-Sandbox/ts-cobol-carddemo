# Fixed-record VSAM unload loader (F1)

Build: `mvn -B -f java/pom.xml -pl data-load -am package` (JDK 21).

Manifest JSON maps relative files to the existing B4 `VsamDataset` enum. The enum
selects the copybook/layout and the business-column mapping from
[schema-mapping-vsam.md](../../docs/schema-mapping-vsam.md); no new schema or codec.

```json
[
  {"file": "accounts.bin", "dataset": "ACCOUNT"},
  {"file": "transactions.bin", "dataset": "TRANSACTION"}
]
```

Supported dataset values: ACCOUNT, CARD, CUSTOMER, CARD_XREF, CATEGORY_BALANCE,
DISCLOSURE, TRANSACTION, TYPE, CATEGORY. See `VsamDataset` for exact layout names.
One file per dataset per manifest; empty manifests and duplicate datasets fail.
Paths must resolve inside the input directory (including symlinks).

Inputs are **unblocked fixed-width IBM037/EBCDIC**, including binary COMP/COMP-3
bytes, with no RDWs, BDWs, separators or newlines. Record length comes from the
copybook. Partial final records are errors; empty files intentionally clear the
dataset. Do not feed the Latin-1, trimmed, LF-delimited fixtures directly.

```sh
# Files must be repository-relative for the bind-mounted estate container.
make data-load INPUT=work/unloads MANIFEST=work/unloads/manifest.json REPORT=work/reconciliation
# Reconcile only, without replacing data:
make data-load INPUT=work/unloads MANIFEST=work/unloads/manifest.json VERIFY_ONLY=1
```

The Make target uses compose's `db` service and isolates VSAM tables in schema
`vsam` (the legacy Db2 tables are untouched). For another PostgreSQL deployment:

```sh
export JDBC_URL=jdbc:postgresql://localhost:5432/carddemo
export JDBC_USER=carddemo
# Provision JDBC_PASSWORD securely in the process environment, not the command line.
export JDBC_SCHEMA=vsam  # optional; default public
java -jar java/data-load/target/data-load.jar work/unloads work/unloads/manifest.json work/reconciliation
```

Flyway applies the existing B4 schema migration in the selected schema. Use an
empty schema or the already Flyway-managed B4 schema; do not baseline unrelated
tables. The database user needs DDL rights for initial provisioning and SELECT,
INSERT and DELETE rights thereafter. Schedule offline with **no concurrent writers**
and immutable source files. This is replacement loading, not merge/upsert.

All manifest loads and reconciliation run in one repeatable-read transaction.
Each source record is streamed through `FixedRecordCodec(IBM037)` and B4's shared
`VsamTransfer.loadRecords`; DB rows are streamed through B4's `readRecords` (the
same projection used by its exporter). Loading/decoding/SQL errors and mismatches
roll back all datasets; MATCH commits. Verification-only always rolls back.
Unlisted tables are untouched. Initial Flyway DDL is a separate transaction.
Report I/O happens after commit: an output error returns 2 but does not undo an
already committed load. Ensure output storage is writable before starting.

`reconciliation.json` and `reconciliation.md` contain counts, copybook field
names (including filler), source/database checksums, field MATCH/MISMATCH and
overall status. Exit codes: **0 MATCH, 1 MISMATCH, 2 ERROR/usage**. Errors produce
an ERROR report when the output directory is writable. Reports contain hashes,
not decoded field values; still protect production reconciliation artifacts.

Checksum definition: normalize each decoded record through the existing codec
(declared numeric scale, padded text/filler), UTF-8 encode each field value's
canonical string, SHA-256 hash it, and sum each field's hashes modulo 2^256.
Reports use zero-padded 64-digit hex. This multiset checksum includes duplicate
values, ignores source record order, and uses bounded memory. Record counts are
compared separately. It detects field-value differences, not reassignment of
unchanged values between keys; it is not a key-correlated row-equivalence proof.

## Reproducible fixture and negative proof

`mvn -B -f java/pom.xml -pl data-load -am verify` runs the existing common tests
and PostgreSQL Testcontainers tests. Every B4-supported file under
`fixtures/*/*/input` is decoded with the B2 Latin-1 codec, encoded into an IBM037
fixed unload, loaded, and reconciled. Each untouched B4 export must additionally
be byte-identical to the original fixture. Test output prints each dataset's
counts, field count and status plus `FIXTURE SUMMARY`; JSON/Markdown evidence is
under `java/data-load/target/fixture-reports/`.

The negative test alters PostgreSQL `tran_type.description`, then calls the CLI
in `--verify-only` mode: exactly `TRAN-TYPE-DESC` must mismatch, with exit 1.
Empty files, partial records, rollback, empty/duplicate manifests and unsafe paths
are also covered. Docker and the `postgres:15-alpine` image are required.
