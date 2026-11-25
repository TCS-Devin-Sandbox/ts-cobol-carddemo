# Pending authorization data transfer (G3)

Build with JDK 21: `mvn -B -f java/pom.xml -pl auth-data -am package`.
Set `JDBC_URL`, `JDBC_USER`, `JDBC_PASSWORD`, and optionally `JDBC_SCHEMA`
(default `public`). Use a dedicated PostgreSQL 15 migration/staging schema.
The CLI applies the shared Flyway migrations before accessing data.

```sh
JAR=java/auth-data/target/auth-data.jar
java -jar "$JAR" load-unload DBPAUTP0.dat
java -jar "$JAR" export-unload DBPAUTP0.export.dat
java -jar "$JAR" export-flat ROOT.FILEO CHILD.FILEO
java -jar "$JAR" export-gsam ROOT.GSAM CHILD.GSAM
java -jar "$JAR" load-flat ROOT.FILEO CHILD.FILEO
```

Both load commands **replace** the pending summary, detail and unload frames in
one transaction. They leave `auth_fraud` and VSAM tables alone. Input parse,
duplicate key or foreign key failures roll back the load. Exports read a
consistent snapshot, create new files only and may leave partial files if they
fail; discard those files. Exit 0 is success, 2 is an error.

* **DFSURGU0:** G1 reads the payload at byte +39 including the RDW. Before
  replacing rows, the loader checks the trailer's root and detail counts
  (unsigned 32-bit big-endian at record offsets +20 and +32, including the RDW).
  V3 keeps the ordinal position, prefix and suffix for each keyed segment, and the opaque
  metadata and blank-key root as unkeyed `BYTEA` frames. No account-zero
  substitute is inserted. Export rebuilds keyed payloads from the G2 rows, not
  an archived copy of the source file. The imported framing is required for
  unload export and cannot be regenerated from flat files or used after key
  additions/deletions.
* **X'00':** PostgreSQL can't store NUL in text. The transfer reserves U+2400
  (`␀`, outside IBM037) as a one-character representation of an EBCDIC
  low-value byte, including the affected statuses on accounts 7, 13 and 18.
  Export reverses it; ordinary space and non-NUL text are unchanged.
* **Flat / GSAM:** raw fixed-width IBM037 data with no record delimiter.
  Roots are 100 bytes; flat children have a six-byte packed parent key and
  200-byte payload, GSAM children have the payload only. Like PAUDBUNL/DBUNLDGS,
  neither export emits the unkeyed root. Output is ordered by account key and
  ascending inverted child date/time (newest first). Only the keyed flat pair
  is reloadable.

## Off-host proof

`mvn -B -f java/pom.xml -pl auth-data -am verify` runs the PostgreSQL 15
Testcontainers proof over the shipped 51,736-byte DBPAUTP0 unload: byte-identical
unload export, byte-identical flat export/reload/export, and GSAM payloads
matching the flat payloads. It checks the 21 keyed roots, one unkeyed root,
202 details, two metadata frames and low-value statuses, and verifies that a
truncated reload rolls back and SQL field changes appear in the export. The
`auth-data-proof` CI job uploads record counts and ordered byte checksums in
`target/fixture-reports/reconciliation.{json,md}`. There is no runnable IMS
off-host, so flat and GSAM checks are not comparisons to legacy runs.
