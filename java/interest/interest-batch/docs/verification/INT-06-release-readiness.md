# INT-06 candidate parity and technical release-readiness

## What the gate proves

The single candidate entry point is
`com.carddemo.interest.batch.InterestBatchApplication`. Its job joins existing
intake/seal, per-account-group calculation and private export with shadow packaging.
The frozen pilot driver substitutes only INTCALC STEP15; real setup jobs supply the
other step return codes. Neither goldens nor the field comparator are changed.

The seven committed interest fixtures gate ordered SYSTRAN (350 bytes) and
ACCTDATA (300 bytes), dataset counts, decoded fields, exact SYSOUT and RC. Byte
comparison restores trailing spaces to LRECL under LF-delimited fixture framing;
it is not a claim about production RDWs or host encodings. Diagnostics identify
case, file, zero-based record and byte offset without dumping records.

`CandidateParityGateIT` runs the candidate against PostgreSQL 15 for every case.
Golden TRAN-AMT and input/golden account differences independently determine
expected transaction/delta counts and exact signed sums, not candidate output.
The final TCATBALF account must have no delta and unchanged input/golden account
bytes (INT-Q02). Staged transaction order/bytes also match the golden. Every
successful shadow run has exactly one PRIVATE publication and no live release.
Missing DEFAULT/account/xref, malformed TCATBALF and missing input produce RC 12,
no output datasets, no publication and no published account effects.

`ReleaseRehearsalIT` uses the real packet builder, ledger and coordinator with a
gate-owned in-memory host. Each computed candidate run is converted by the test
owner to a unique live cycle after deleting only its PRIVATE shadow claim.
Rehearsals cover an unsealed intake/no packet, changed account preimage, fence
replacement after preflight, uncertain applied patch/no receipt, manual journaled
restoration and exact generation quarantine, and repeated handoff/resolve after
simulated downstream consumption. They assert no unreceipted RELEASED run,
one publication per run and at most one live claim per destination/cycle.
Restoration checks all captured account before-images/counts and previous pointer,
keeps HELD, and does not catalog a second generation.
An additional combined-input rehearsal stops after two of three account patches;
only those two journaled changes are restored, all capture bytes/counts match,
and no generation exists to quarantine. The ambiguous-catalog rehearsal separately
proves quarantine of the exact generation that was actually cataloged.

Post-consumption containment does **not** fabricate a new HELD state for an
already verified RELEASED publication: the frozen coordinator returns the
acknowledged release without mutation or a second signal. The double refuses
restoration once consumed. No candidate job is relaunched. Real downstream
TRANSACT/consumer reconciliation remains a manual Operations responsibility
(INT-ARCH A4 / INT-H06); this is not a deployed incident hold mechanism.

## Reproduce from repository root

Prerequisites: Java 21, Maven, Docker, tagged PostgreSQL 15 and 15-alpine images,
and the built estate (`make up && make build`; mirror base image if required).

```sh
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
set -o pipefail
mvn -B -f java/interest/interest-batch/pom.xml clean verify 2>&1 |
  tee java/interest/evidence/06-parity-gate/test-output.txt
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover \
  -s java/interest/interest-batch/scripts/parity -v 2>&1 |
  tee java/interest/evidence/06-parity-gate/parity-unittest.txt
java/interest/interest-batch/scripts/parity/run_gate.sh \
  java/interest/evidence/06-parity-gate
```

The script fails immediately on driver, unchanged comparator, padded-byte or SQL
failure. It records seven run IDs, uses golden-derived SQL expectations, and
requires every per-run SQL boolean to be true. Outputs: `e2e-run.log`,
`compare-stdout.txt`, `compare-report.md`, `byte-check.txt`,
`sql-output.txt`, and `gate-revision.txt`. Maven output contains `GATE` and
`REHEARSAL` summary lines. The revision file records HEAD and dirty paths:
uncommitted verification is working-tree evidence, not final-commit evidence.
Rerun/archive at the final candidate revision before release review.

## Outstanding live release sign-offs

All references below remain OPEN in
`modernization/rules/interest/decisions.md`; technical parity closes none:

| Register IDs | Required owner decision/sign-off |
| --- | --- |
| INT-Q01 | Finance/interest policy: truncation versus rounding |
| INT-Q02 | Finance/card operations: final-account EOF omission |
| INT-Q03 | Finance/interest policy: negative interest |
| INT-Q04 | Finance/card operations: zero-value transactions |
| INT-Q05 | Finance/statement production: timestamp basis |
| INT-Q06 | Card operations: accounts without category balances |
| INT-H01 | Finance/product: monthly fee scope |
| INT-H02 | Scheduler/application: cycle date source and format |
| INT-H03 | Card operations: multiple-card association / AIX first return |
| INT-H04 | Finance/card operations: inactive/expired accounts |
| INT-H05 | Application: amount/counter overflow, width and truncation policy |
| INT-H06 | Operations/application: approved restore, checkpoint, duplicate prevention and downstream incident recovery |
| INT-H07 | Finance/interest policy: negative rates |
| INT-H08 | Finance/operations: missing-rate failure policy |
| INT-H09 | Card operations: category-balance clearing responsibility |

Manifest references: `modernization/streams/interest/manifest.json`:

- **interest-1**, Operations/application: restore/checkpoint/GDG cleanup and
  duplicate prevention; MAXRERUN is not recovery proof.
- **interest-2**, Scheduler/application: cycle-date substitution policy/format.
- **interest-3**, Scheduler/CICS: production ordering, quiescence, cross-folder
  exclusion and validated folder identifiers.
- **interest-4**, Build/release: deployed binder/load-library provenance.

## Gaps and unverified assumptions

No real host bridge, writer/read fence, scheduler signal, GDS recovery procedure,
production binder proof or compiler-option/arithmetic certification is supplied.
Host atomic conditional writes, durable journals/receipts, GDG catalog/pointer
verification and quarantine are simulated, not production evidence. Real CICS,
CA7 and other writers/readers must be excluded through the whole release window;
DISP=SHR and folder edges alone do not prove this (INT-ARCH A3/A4).
Lab ASCII goldens are not certification of deployed EBCDIC/initialization bytes,
host timestamp/clock settings or production AIX selection. No live cutover,
approval, deployment, external system change or automatic recovery is performed.
