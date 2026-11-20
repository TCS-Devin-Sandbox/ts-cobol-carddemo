# INT-05 – per-case parity report (open this first)

## Candidate
Branch demo/05-g3-data-flow from origin/demo/05b-integration. Combined check ran on code revision
b933d3cb6edceed8bde34bf4f162e0fa8ad04c9a (revision.txt); the final candidate is the INT-05 commit containing this
report (`git log -1 --format=%H -- java/interest/evidence/05-shadow-handoff/parity-report.md`), which adds evidence only.

Java 21.0.13.1; Maven 3.9.9; Spring Boot 3.3.5; Spring Batch 5.1.2;
PostgreSQL 15.20 (Alpine Testcontainers and compose db).
Legacy baseline: `make parity` → 24/24 PASS, including all seven interest cases (make-parity.txt).

## Implementation and trace
Package `com.carddemo.interest.handoff` (production + tests only; no schema, codec, pilot or pom change).
- `PacketBuilder` (INT-ARCH A3 Package): reads a COMPUTED run of a SEALED snapshot; interest_tx in emit-ordinal
  order (INT-R08; gap → ORDINAL_GAP), each raw_record 350 bytes, decoded through FixedRecordCodec TRAN and
  checked against its columns (T01/T06/T14); account_delta joined to its account_input preimage, SHA-256 must equal
  source_fingerprint and old A03/A09/A10 values; post-image = `FixedRecordCodec.encode(decoded, patch)` so all
  other bytes stay. Canonical manifest (fixed key order, hex digests, scale-2 decimals).
- `ExportPacket`: immutable; payload = count × 350 bytes, RECFM=F, no LF/RDW; SHA-256 digest.
- `PublicationLedger` (A1 publication contract, runs as interest_publish): one row per run (PK), one live claim
  per destination/cycle under `pg_advisory_xact_lock`; HELD carries `{verified:false, reason}`; RECEIVED +
  run RELEASED only together, in one transaction, with the verified receipt.
- `HandoffCoordinator` (A2.4/A4): shadow → PRIVATE row, destination `shadow:…`, no host call, no success
  condition. Live → claim, fence observed/active/same cycle, every preimage and TRAN-ID preflighted before any
  mutation, fence rechecked before each mutation, (0) generation unchanged, guarded patches, one SYSTRAN(+1)
  catalog, receipt must match operation ID, generation, digest, count, account post-image digests and exactly one
  cataloged generation. Anything else → HELD with a typed HoldReason; `resolve` releases a held run only on a
  verified receipt and never reapplies. `ReleaseSignal` (INTCALC success condition) fires once.
- `HostBridge` is an interface only; tests use `SimulatedHost`. No host command, production config, console
  operation, fallback execution or publishing. INT-H05 and INT-H06 stay OPEN.

## Packet evidence (i01-shaped rows, HandoffIT)
records=2, bytes=700, payload SHA-256 4808df2ada1f74834f16ea24b136b50cf4e557118f24faad56d77cb3a856ea01, one delta
(00000002001: 1000.00 → 1010.00, cycle credit/debit → 0.00). Independent check: the golden
`SYSTRAN.G0001V00` records padded to LRECL 350 hash to the same digest (700 bytes). Both records equal the golden
bytes in order; the post-image equals the golden ACCTDATA record.

## Fault injection (handoff-fault-injection.txt)
| Case | Outcome | Host mutations | Success condition |
|---|---|---|---|
| shadow | ShadowRehearsed, PRIVATE, run COMPUTED | 0 (0 host calls) | none |
| verified receipt | Released, RECEIVED/RELEASED, 1 generation | 2 | once |
| repeated verified receipt | Released(alreadyReleased) | no new | still once |
| wrong preimage | HELD PREIMAGE_MISMATCH | 0 | none |
| missing fence | HELD FENCE_MISSING | 0 | none |
| stale / inactive / changed fence | HELD FENCE_STALE | 0 | none |
| backup TRAN-ID collision | HELD TXN_ID_COLLISION | 0 | none |
| (0) generation moved | HELD GENERATION_MOVED | apply only, no catalog | none |
| missing receipt after apply/catalog | HELD RECEIPT_MISSING → released only after receipt verified | no reapply | once |
| mismatched receipt | HELD RECEIPT_MISMATCH | — | none |
| partial account apply (2 deltas, 2nd rejected) | HELD APPLY_UNCERTAIN, no catalog, no reapply | 2 | none |
| unknown apply | HELD APPLY_UNCERTAIN, no catalog | 1 | none |
| ambiguous catalog | HELD CATALOG_AMBIGUOUS → released on verified receipt, 1 generation | no recatalog | once |
| failed catalog | HELD, resolve → RECEIPT_MISSING | — | none |
| second live run same destination/cycle | refused CLAIM_CONFLICT, no row | 0 | none |

SQL after every test (handoff-sql.sql): released_without_verified_receipt=0,
live_claims_per_destination_cycle_gt1=0, publication_rows_for_run ≤ 1 — all 22 HandoffIT runs.

## Commands
```sh
mvn -B -f java/interest/interest-batch/pom.xml clean verify 2>&1 | tee java/interest/evidence/05-shadow-handoff/test-output.txt
python3 -m unittest discover -s java/interest/interest-batch/src/test/resources/pilot/parity -v
P=java/interest/interest-batch/src/test/resources/pilot
for c in i01-baseline i02-tie-half-cent i03-zero-rate-skipped i04-default-group-fallback \
         i05-multi-category i06-negative-balance i07-nontie-rounding; do
  docker compose exec -T -e INTEREST_PILOT_JDBC_URL=jdbc:postgresql://db:5432/carddemo \
    -e INTEREST_PILOT_DB_USER=carddemo -e INTEREST_PILOT_DB_PASSWORD=carddemo \
    estate python3 "$P/parity/run_case.py" $c --out work/05-shadow-handoff/$c || exit "$?"
done 2>&1 | tee java/interest/evidence/05-shadow-handoff/e2e-run.log
for c in <same seven>; do
  python3 tools/parity/compare.py interest $c --actual work/05-shadow-handoff/$c --label java \
    --report java/interest/evidence/05-shadow-handoff/compare-report.md || exit "$?"
done | tee java/interest/evidence/05-shadow-handoff/compare-stdout.txt
make parity 2>&1 | tee java/interest/evidence/05-shadow-handoff/make-parity.txt
```

## Result per case
| Case | Ticket case | compare.py | Datasets (records / decoded fields) | SYSOUT STEP15 | rc.json |
|---|---|---|---|---|---|
| i01-baseline | yes | **PASS** | 4 / 50, 0 differences | identical | identical |
| i02-tie-half-cent | no | **PASS** | 4 / 50, 0 differences | identical | identical |
| i03-zero-rate-skipped | no | **PASS** | 2 / 25, 0 differences | identical | identical |
| i04-default-group-fallback | no | **PASS** | 2 / 25, 0 differences | identical | identical |
| i05-multi-category | no | **PASS** | 3 / 38, 0 differences | identical | identical |
| i06-negative-balance | no | **PASS** | 2 / 25, 0 differences | identical | identical |
| i07-nontie-rounding | no | **PASS** | 4 / 50, 0 differences | identical | identical |

SQL (i01-sql.sql, run 86a73104-38ab-43af-9030-ba1e3ead3b6b): transactions_pass t, delta_pass t, final_account_unchanged_pass t.
Maven: unit 51/0/0/0, integration 38/0/0/0 (handoff: HandoffRulesTest 7, HandoffIT 22); BUILD SUCCESS.
Parity driver unittest: OK. make parity: 24/24 PASS.

## Raw files
test-output.txt, parity-driver-unittest.txt, e2e-run.log, compare-stdout.txt, compare-report.md, make-parity.txt,
i01-sql.sql, i01-sql-output.txt, handoff-sql.sql, handoff-fault-injection.txt, revision.txt, checklist.md.
