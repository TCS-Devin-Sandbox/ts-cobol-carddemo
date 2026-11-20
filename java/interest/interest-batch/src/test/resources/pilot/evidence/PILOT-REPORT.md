# INT-01 pilot report

Ticket: `modernization/design/tickets/01-pilot.md`. `modernization/design/tickets/README.md` names it as the Wave 1 pilot.
Start: `origin/demo/04a-standards` @ `84ffae7`. Branch: `demo/05-pilot`. Final code revision: `2109615405fe658bd4d9b2b55af43c48e0a704f1` (scoped parity driver; Java unchanged from `d99d984`).
Module: `java/interest/interest-batch` (the ticket's `interest-batch/` scope placed under `java/interest` as the prompt asks).
Skills used: `interest-conversion-standards` (read before coding) and `carddemo-parity`. `interest-ticket-build`,
`interest-ticket-verify` and `interest-pitfalls` are not on the starting branch, so this run is the pilot for the method below.

## What was built
- One Spring Batch job `interestPilot`. Step 1 `interestPilotIntake` decodes the real `AWS.M2.CARDDEMO.{ACCTDATA,CARDXREF,TCATBALF,DISCGRP}.PS.txt`
  files into run-scoped `interest_pilot.*_input` snapshots. Step 2 `interestPilotStep` runs reader -> processor -> writer,
  one group per account break; `PilotJobConfig.WHOLE_RUN` makes the whole run one chunk (not configurable), so a failure commits nothing.
- Private tables: `src/main/resources/pilot/schema-pilot.sql`. Batch metadata lives in schema `interest_batch`.
- Calculation: `InterestCalculator` (BigDecimal, `RoundingMode.DOWN`, receiver widths in `Receiver`).
  Timestamps: `LegacyTimestamp`, from an injected `Clock`. IDs: `TransactionIdSource`, from the opaque PARM. The clock and the PARM are injected separately.
- Export to the comparator layout: `PilotExporter` writes SYSTRAN G0001V00, the ACCTDATA image, the STEP15 SYSOUT and `rc.json`.
- Scoped parity driver: `../parity/run_case.py` runs real setup jobs on one Runner, unloads their resulting KSDS files for Java intake, then substitutes only STEP15. Combined rc.json uses observed setup codes and the Java process exit code. Exact commands: `../parity/README.md`.

## Final-revision checks
| Check | Result |
|---|---|
| `mvn -B clean verify` (Java 21, Testcontainers `postgres:15-alpine`) | BUILD SUCCESS: unit 36/36, integration 10/10 (`test-output.txt`) |
| Historical direct-jar PostgreSQL 15 SQL assertions, i01 | exit 0; SQL assertions pass (`i01-sql-assertions.txt`); not re-queried in this driver revision |
| compare.py i01-baseline + six informational cases | PASS: 7/7 including datasets, SYSOUT and setup/Java RCs (`compare-report.md`, `compare-stdout.txt`, `e2e-run.log`) |
| Legacy baseline `make parity CHAIN=interest` | 7/7 PASS |
| Driver framing unit check | PASS: 1 test OK (normal, empty, overlength records), `python3 -m unittest discover -s src/test/resources/pilot/parity -v` |

## OPEN rules: where legacy behaviour is kept, and the tests that prove it
| Rule | Code path | Test |
|---|---|---|
| INT-Q01 truncation | `InterestCalculator.monthlyInterest`, `Receiver` | `INT_Q01_i02_*`, `INT_Q01_i07_*`, `INT_Q01_INT_Q03_*` |
| INT-Q02 last account not updated | `InterestGroupProcessor.process` | `INT_Q02_finalAccountAtEofHasNoDelta`, `PilotI01IT.INT_R01_R05_R08_Q05_Q02_*` |
| INT-Q03 negative interest | `InterestCalculator.monthlyInterest` | `INT_Q03_i06_*` |
| INT-Q04 zero amount still emitted | `InterestGroupProcessor.process` | `INT_Q04_*` |
| INT-Q05 wall-clock timestamp | `LegacyTimestamp` | `INT_Q05_*` |
| INT-Q06 accounts without balances untouched | `InterestGroupProcessor.process` / `AccountGroupReader` | `m_INT_Q06_*`, `PilotI01IT` whole-record ACCTDATA match |
| INT-H01 no fees | `InterestGroupProcessor.process` | none (the path does not exist) |
| INT-H02 opaque PARM | `TransactionIdSource` | `INT_H02_*` |
| INT-H03 AIX card choice | `InterestGroupProcessor.cardFor` (several matches are held) | `INT_H03_*` |
| INT-H05 receiver width / suffix wrap | `Receiver`, `TransactionIdSource` | `INT_H05_*` |
| INT-H07 negative rates not excluded | `InterestGroupProcessor.process` | `INT_H07_*` |
| INT-H08 missing DEFAULT fails | `InterestGroupProcessor.rateFor` | `INT_H08_*` (unit + IT) |
| INT-H09 balances not cleared | `AccountDelta.closing` | `PilotI01IT` (no TCATBALF output) |
| INT-H04, INT-H06 | not touched: no status check; restart is excluded | – |

## Standards review checklist (`.agents/skills/interest-conversion-standards/SKILL.md` §9)
| Line | Result | Evidence |
|---|---|---|
| S01 | PASS | this report; source ranges cited in Javadoc (`InterestGroupProcessor`, `InterestCalculator`, records) |
| S02 | PASS | `PilotI01IT` slices the i01 goldens; scoped driver now matches the whole normal-path comparator contract |
| S03 | PASS | `InterestGroupProcessor` (typed `PilotFailure`, `RateLookup`), no paragraph/flag emulation |
| S04 | PASS | `Account`, `CardXref`, `CategoryBalance`, `DisclosureRate`, `InterestTransaction` (records, defensive copies, raw bytes kept) |
| S05 | PASS | `PilotJobConfig` (reader/processor/writer, JDBC tx), `PilotStore` |
| S06 | PASS | constructor injection throughout; ordinal and SYSOUT counter are step-scoped in `InterestGroupProcessor` |
| S07 | PASS | `InterestCalculator`, `Receiver`; no float/double |
| S08 | PASS | `PilotFieldCodec`, `FixtureDatasets` only; non-lab sign bytes rejected |
| S09 | PASS | `PilotProperties`, `PilotJobConfig.pilotClock`, `TransactionIdSource` (10 printable lab-profile bytes, `Locale.ROOT`); `INT_H02_parmThatIsNotTenLabProfileBytesIsHeld` |
| S10 | PASS | OPEN-rule table above |
| S11 | PASS | register findings below; no decision changed; INT-H03/H02 hold paths |
| S12 | FAIL (partial, in scope) | `PilotRollbackIT`: FAILED + no commit + `pilot-failure.json` (dd/op/status); `event=rate-fallback dd=DISCGRP op=READ status=23` logged (`INT_R03_defaultFallbackUsesPaddedDefaultRow`). Legacy abend RC and SYSOUT lines are excluded by the ticket and not produced |
| S13 | PASS (untouched) | restart excluded; `run_checkpoint` written but never resumed |
| S14 | PASS | logs carry run id, counts and dd/op/status only; non-pilot exceptions log their type only (`SnapshotIntake`, `PilotRunService`, `InterestGroupProcessor`) |
| S15 | PASS | rule-named tests above; specimens `src/test/resources/pilot/specimens/README.md` |
| S16 | PASS (ticket normal path) | PG15; unchanged comparator 7/7 PASS, with actual setup and Java RCs (`parity-report.md`). Error-path/host/restart exclusions remain |
| S17 | PASS | `git diff demo/04a-standards --stat` touches only `java/` |
| S18 | PASS | new dependencies: Spring Boot batch/jdbc starters, PostgreSQL JDBC, Testcontainers 1.21.4 (1.20.3 cannot talk to Docker Engine API ≥1.44) |
| S19 | PASS | specimens are edited copies of synthetic i01 inputs |
| a | PASS | `INT_R06_accountTotalSumsStoredCentsNotRawQuotients`, `INT_Q01_INT_Q03_*` |
| b | PASS | `INT_H05_*` (both) |
| c | PASS | exact `multiply` before `divide`; host arithmetic options still unproven (finding 3) |
| d | PASS | `PilotRecordLayoutTest.d_*` |
| e | PASS (untouched) | PARM-LENGTH COMP not read; no COMP fields decoded |
| g | PASS | `INT_R05_g_transactionRecordLayoutAndConstants`, `DisclosureRate.DEFAULT_GROUP` |
| h | PASS | `PilotStore.categoryBalancesInKeyOrder` (bytea key order); lab profile only |
| i | FAIL (partial) | timestamp formatter present (`LegacyTimestamp`); status adapter only emits lab `23` |
| j | PASS | `j_accountRewriteChangesOnlyA03A09A10AndKeepsFiller`, CHECK octet_length in schema |
| l | FAIL (assumption) | TRAN filler and DESC tail are written as spaces; the legacy bytes are unassigned (finding 4) |
| m | PASS | `INT_Q02_*`, `m_INT_Q06_emptyCategoryBalances*`, account break tests |
| o | PASS | `PilotRunResult` typed; RC 0/12 only |
| p | PASS | `findAccount` / `findCardsByAccount` / `findRate`; source fingerprint stored with each delta |
| q | PASS | `INT_R03_*`, `INT_H08_*`, `INT_R10_*` |
| r | FAIL (out of scope) | fixed G0001V00 output only; no DISP/hold handoff (bridge excluded) |
| s | PASS | SYSOUT identical in all 7 cases, including the fallback lines (i04) |
| t | PASS | `INT_Q05_clockIsIndependentOfParm` |
| u | PASS | `INT_Q05_hundredthsTruncateAndFourZerosAppended`; one sample per row |
| w | PASS | no fee/filter/reset/clear path (`InterestGroupProcessor`) |

## What the ticket does not cover
Generic codecs (EBCDIC, COMP-3), production DDL, the full RC and error-path SYSOUT, the legacy abend RC, restart and resume,
the GDG/DISP bridge, non-unique AIX ordering, PARM shorter than 10 bytes, and the fixtures other than i01 (run for information only).

## Findings for the decision register (not edited)
1. OPEN — person action, owner comparator/fixtures: fix `tools/parity/record.py` to scope/reset `Runner.rcs`, then explicitly re-record goldens. Golden rc.json lists setup steps STEP05–STEP30 under INTCALC (`record.py:131`), though INTCALC has only STEP15. The owner-authorized scoped driver now executes those real setup jobs and preserves their observed codes; it does not fix the recorder defect or fabricate codes.
2. INT-H03: if XREFFIL1 returns more than one row, the host order is unproven. The pilot holds such runs.
3. INT-Q01/H05: the intermediate precision of `COMPUTE` under host ARITH options is still unproven. The pilot uses an exact product.
4. TRAN-RECORD FILLER(20) and the TRAN-DESC tail after the STRING are never assigned in CBACT04C (STRING does not pad).
   Goldens strip trailing spaces and the comparator skips filler, so the byte value is unproven. The pilot writes spaces.
5. INT-H02: PARM-LENGTH is never checked. A PARM shorter than 10 bytes reads unassigned linkage. The pilot rejects it.
6. Standards skill (not edited): §9 lacks a line distinguishing converted-step RCs from real setup-job provenance. S16 normal-path parity now passes through the scoped driver; S12 remains FAIL (partial), since excluded legacy abend/error SYSOUT is not produced. Normal-path parity does not prove S12.

## Final scoped-driver verification

Final code SHA: `2109615405fe658bd4d9b2b55af43c48e0a704f1`. Exact complete commands: `../parity/README.md`.
Docker Hub returned HTTP 429 resolving ubuntu:22.04; the successful baseline used the existing
`ESTATE_BASE_IMAGE=mirror.gcr.io/library/ubuntu:22.04` argument with `make up && make build && make parity CHAIN=interest`.
No tracked infrastructure changed. The existing estate Dockerfile installs Java 21, so Java and Runner run together in estate
against compose db (PostgreSQL 15). Host Java 21 was installed for Maven.
`test-output.txt` is full Maven stdout/stderr: 36 unit + 10 integration tests, zero failures/errors/skips, BUILD SUCCESS.
`e2e-run.log` records all seven real setup/Java executions. `compare-stdout.txt` and `compare-report.md` show 7/7 PASS.
Setup logs and post-setup unloads remain under `work/java-pilot/<case>/setup-joblog` and `intake/` (runtime artifacts).
The driver unit check proves normal/empty/overlength framing. No UI changed; visual verification is not applicable.

## Historical direct-jar method and failures (superseded by driver above)
```sh
git fetch origin && git checkout -b demo/05-pilot origin/demo/04a-standards
sudo apt-get install -y -q openjdk-21-jdk-headless          # image ships Java 17
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
docker pull mirror.gcr.io/library/postgres:15-alpine && docker tag mirror.gcr.io/library/postgres:15-alpine postgres:15-alpine
docker pull mirror.gcr.io/library/postgres:15 && docker tag mirror.gcr.io/library/postgres:15 postgres:15
docker pull mirror.gcr.io/testcontainers/ryuk:0.9.0 && docker tag mirror.gcr.io/testcontainers/ryuk:0.9.0 testcontainers/ryuk:0.9.0
make up && make build && make parity CHAIN=interest           # legacy baseline, 7/7 PASS
cd java/interest/interest-batch && mvn -B clean verify
docker run -d --name interest-pilot-pg -e POSTGRES_DB=interest -e POSTGRES_USER=interest -e POSTGRES_PASSWORD=interest -p 5433:5432 postgres:15
java -jar target/interest-batch-0.1.0-PILOT.jar --interest.pilot.run-on-startup=true --interest.pilot.input-dir=... --interest.pilot.output-dir=...
python3 tools/parity/compare.py interest i01-baseline --actual work/java-pilot/i01-baseline --label java
```
Failures hit:
- Docker Hub rate limit (HTTP 429) on image pulls. Fix: pull from `mirror.gcr.io`, then retag.
- `mvn -o` failed because the enforcer plugin was not cached. Fix: run online once.
- Testcontainers 1.20.3 reported "Could not find a valid Docker environment" against Docker API 1.56 (minimum 1.40). Fix: Testcontainers 1.21.4.
- A `@Container` static field in an abstract base stopped after the first IT class, and the cached Spring context then failed
  with "Could not open JDBC Connection" (30 s timeouts). Fix: a singleton container started in a static block.
- Test data mistake: zoned `00000012345{` is 1234.50, not 123.45, because the overpunch byte is the last digit.
- The comparator also checks `rc.json` and SYSOUT, not only datasets. The original direct-jar command failed on setup RCs only; the real-setup driver supersedes that failure. Recorder finding 1 remains open.

## Review fixes (Devin Review on PR #15), evidence rerun at `59ffbc2`
| Finding | Fix | Test |
|---|---|---|
| ACCT-ID decoded as X(11) | PIC 9(11) validated (`Account.decode`) | `d_accountIdIsPic9AndNonDigitKeyIsUnreadable` |
| Smaller chunk size could commit partial runs | `groups-per-transaction` removed; `WHOLE_RUN` chunk | `INT_R10_missingAccountRollsBackEarlierGroupOutputs` |
| Export `stripTrailing` dropped non-space filler | only trailing spaces dropped (`FixtureDatasets.writeFixed`) | `j_exportDropsOnlyTrailingSpacesSoOtherFillerBytesSurvive` |
| Locale-dependent digits | `Locale.ROOT` / `Integer.toString` (`TransactionIdSource`, `LegacyTimestamp`, `PilotExporter`) | `INT_R08_INT_Q05_idsAndTimestampsUseAsciiDigitsUnderAnyLocale` |
| PARM not byte-checked | rejects PARM not encodable as 10 printable lab-profile bytes | `INT_H02_parmThatIsNotTenLabProfileBytesIsHeld` |
| No ordinary fallback event | `event=rate-fallback dd=DISCGRP op=READ status=23` | `INT_R03_defaultFallbackUsesPaddedDefaultRow` |
| Raw exception text in logs | non-pilot failures log exception type only | – |
| Stale success artifacts on dir reuse / export failure | artifacts staged in `.staging-<run>`, old `rc.json` removed first, new one published last; export failure gives RC 12 | `INT_R10_failedRunIntoReusedOutputDirLeavesNoStaleSuccessArtifacts` |

## Review round 2 (Devin Review on `af12006`), evidence rerun at `d99d984`
| Finding | Decision | Test |
|---|---|---|
| `publish` deleted every file under `datasets/` and `sysout/` | fixed: only the three pilot files and `pilot-failure.json` are replaced | `INT_R10_failedRunIntoReusedOutputDirLeavesNoStaleSuccessArtifacts` (operator file kept) |
| A cleanup error could stop the failure RC from being written | fixed: if publishing fails, RC 12 is still written to the output directory | `INT_R10_failureRcIsPublishedEvenWhenOldArtifactsCannotBeRemoved` |
| An empty run does not validate the PARM | rejected: legacy CBACT04C reads PARM-DATE only when it builds a TRAN-ID, so with no balances it ends RC 0 whatever the PARM (INT-H02 OPEN) | – |
