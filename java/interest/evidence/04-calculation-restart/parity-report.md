# INT-04 – per-case parity report (open this first)

Code revision: `aee3e1e9d6b7a0091b4daa0d3fb6728d53c1345e` on `demo/05-g2-logic`.
Base: `origin/demo/05b-integration` = `c43bcf11a11a4814cfa9a5d07b53b093be79e30d`.
Fresh Maven, seven-case Java comparison, SQL and diagnostics evidence was captured after the
status-adapter fix, from the identical code before its commit. No production/test code changed afterward.
Java 21.0.13.1, Maven 3.9.9, Spring Boot 3.3.5 / Spring Batch 5.1.2;
PostgreSQL 15 (`postgres:15` compose, `postgres:15-alpine` Testcontainers).
Retained legacy baseline: `ESTATE_BASE_IMAGE=mirror.gcr.io/library/ubuntu:22.04 make parity`:
24/24 PASS, zero FAIL (posting 7, interest 7, trantype-update 7, trantype-dynamic 3).
The legacy and wrapper/driver inputs did not change in the status fix, so their passing
`make-parity.txt`, `parity-driver-unittest.txt` and `job-wrapper-unittest.txt` evidence is
retained from the preceding evidence commit, not represented as freshly rerun tests.

**Candidate:** `com.carddemo.interest.job.InterestCloseApplication`, launched through
`src/test/resources/job/parity/close_java.py`. The jar Start-Class remains
`com.carddemo.interest.pilot.InterestPilotApplication`; `pom.xml` is frozen and unchanged.
The wrapper translates the unchanged driver's `--interest.pilot.*` arguments into
`--interest.close.*`, then execs Java 21 with `PropertiesLauncher` and `-Dloader.main`.
See `launcher-manifest.txt` and `job-wrapper-unittest.txt`.

## Exact evidence commands

From the repository root, Bash, with pipeline failure propagation. Maven and the two loops
were rerun after the status fix; unchanged Python and legacy checks retain the preceding
capture as noted above. The output-only
redirection after tee avoids flooding the terminal; the complete streams are retained.

```sh
set -o pipefail
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
T=04-calculation-restart
P=java/interest/interest-batch/src/test/resources/pilot
mkdir -p java/interest/evidence/$T
mvn -B -f java/interest/interest-batch/pom.xml clean verify 2>&1 |
  tee java/interest/evidence/$T/test-output.txt > /dev/null
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s "$P/parity" -v 2>&1 |
  tee java/interest/evidence/$T/parity-driver-unittest.txt
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover \
  -s java/interest/interest-batch/src/test/resources/job/parity -v 2>&1 |
  tee java/interest/evidence/$T/job-wrapper-unittest.txt

rm -f java/interest/evidence/$T/compare-report.md
for c in i01-baseline i02-tie-half-cent i03-zero-rate-skipped i04-default-group-fallback \
         i05-multi-category i06-negative-balance i07-nontie-rounding; do
  docker compose exec -T \
    -e INTEREST_PILOT_JDBC_URL=jdbc:postgresql://db:5432/carddemo \
    -e INTEREST_PILOT_DB_USER=carddemo -e INTEREST_PILOT_DB_PASSWORD=carddemo \
    estate python3 "$P/parity/run_case.py" "$c" --out work/$T/$c \
    --java /estate/java/interest/interest-batch/src/test/resources/job/parity/close_java.py || exit "$?"
done 2>&1 | tee java/interest/evidence/$T/e2e-run.log > /dev/null || exit "$?"
for c in i01-baseline i02-tie-half-cent i03-zero-rate-skipped i04-default-group-fallback \
         i05-multi-category i06-negative-balance i07-nontie-rounding; do
  python3 tools/parity/compare.py interest "$c" --actual work/$T/$c --label java \
    --report java/interest/evidence/$T/compare-report.md || exit "$?"
done | tee java/interest/evidence/$T/compare-stdout.txt
grep -c '^## ' java/interest/evidence/$T/compare-report.md

run_id=$(grep -m1 -o 'event=run-finished run=[^ ]*' java/interest/evidence/$T/e2e-run.log | cut -d= -f3)
test -n "$run_id" && docker compose exec -T db psql -U carddemo -d interest_close \
  -v ON_ERROR_STOP=1 -v run_id="$run_id" -f - < java/interest/evidence/$T/i01-sql.sql |
  tee java/interest/evidence/$T/i01-sql-output.txt

export ESTATE_BASE_IMAGE=mirror.gcr.io/library/ubuntu:22.04
make parity 2>&1 | tee java/interest/evidence/$T/make-parity.txt > /dev/null
```

The new job emits `event=run-finished` rather than the pilot's `event=run-completed`.
The first run in the sequential log is i01; no extra rerun or inferred MAX(run_id) was used.
The skill's SQL database is deliberately changed to the candidate's private `interest_close`.
All commands completed with exit 0. Report heading count: **7**.
The real setup jobs populate/unload the KSDS inputs; only INTCALC STEP15 is replaced.
Setup RCs are observed by the frozen driver, and STEP15 RC is the wrapper's actual process exit.

## Result per case

All seven cases gate INT-04, not merely informational replays.

| Case | Ticket case | compare.py | Datasets (records / decoded fields) | SYSOUT STEP15 | rc.json |
|---|---|---|---|---|---|
| i01-baseline | yes | **PASS** | 4 / 50, 0 differences | identical | identical |
| i02-tie-half-cent | yes | **PASS** | 4 / 50, 0 differences | identical | identical |
| i03-zero-rate-skipped | yes | **PASS** | 2 / 25, 0 differences | identical | identical |
| i04-default-group-fallback | yes | **PASS** | 2 / 25, 0 differences | identical | identical |
| i05-multi-category | yes | **PASS** | 3 / 38, 0 differences | identical | identical |
| i06-negative-balance | yes | **PASS** | 2 / 25, 0 differences | identical | identical |
| i07-nontie-rounding | yes | **PASS** | 4 / 50, 0 differences | identical | identical |

## SQL and automated assertions

i01 run: `f034f47c-81ab-432e-bfd2-3459ad3a4f02`, database `interest_close`.
All ten SQL assertions in `i01-sql-output.txt` are **t**:
private database, COMPUTED run/SEALED snapshot, 2 transactions totaling 47.00 with exact
ordinals/IDs/cards/timestamps, one delta for `00000002001` (1000.00 -> 1010.00, cycle
credit/debit zero), no delta and unchanged 2400.00 input for final account `00000002002`,
one terminal checkpoint (NULL next key, last ordinal 2), and each of the four input-table
row counts matching its snapshot manifest.

Fresh Maven totals (`test-output.txt`):

```text
[INFO] Tests run: 63, Failures: 0, Errors: 0, Skipped: 0
[INFO] Tests run: 30, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

This retains the existing 44 unit / 16 integration tests and adds 19 unit / 14 integration
tests. Pilot parity driver: 1 test OK. New wrapper: 2 tests OK.
Expected injected crashes and required-lookup exceptions appear in the successful Maven
log; these are negative-path assertions, not test failures.
`restart-and-failure-cases.txt` extracts fresh Surefire/Failsafe testcase/suite lines and
the corresponding diagnostic lines from `test-output.txt`; it is not a separate test run.
It covers rollback before commit, no replay after commit, explicit resume, changed identity
and snapshot refusal, required account/XREF/DEFAULT failure, held malformed/duplicate/missing
input, held unevidenced multi-card input, and receiver/suffix overflow without lost rows.
The status fix adds six `LegacyStatusTest` unit tests and one integration test containing
six injected operation-failure scenarios. Unit expectations are literal strings from
CBACT04C 234–648, including all 17 supported operation/diagnostic combinations, the
misleading DISCGRP open text and XREF open's status concatenation. Numeric 23 renders
NNNN0023; ASCII 99 renders NNNN9057 (second storage byte 0x39 = 57, not digit 9).
CP037 and synthetic little-endian probes control both character decoding and the binary
right-byte reinterpretation/PIC-999 receiver. This is synthetic adapter evidence, not a
new recorded host status specimen; no general formatter existed in the frozen pilot to import.

`LegacyStatus.check` returns typed SUCCESS, END_OF_INPUT (TCATBALF READ 10 only), or
DEFAULT_RATE (first exact DISCGRP READ 23 only), otherwise a typed terminal failure.
`GroupCalculation.MissingLookup` now uses this shared failure/formatter contract.
`CloseRunService` unwraps typed failures from Spring Batch, marks the run FAILED, emits
RC12/U999 diagnostics and exports no datasets. Restricted ordered DISPLAY prefixes are
carried separately from the masked exception/log text; the exporter appends the exact
operation/status/abend lines without replaying future input. The IT injects OPEN/READ/
WRITE/REWRITE/CLOSE and DEFAULT-read failures at the transaction hook to prove this
coordinator/exporter path, not to claim those physical legacy operations execute in Java.
In particular it tests a rewrite's preceding lookahead DISPLAY, non-TCATBALF status 10,
extended write/rewrite statuses and DISCGRP's open wording. Target/PostgreSQL failures
remain target-category failures, never invented COBOL statuses.
`InterestCloseIT.unchanged` independently compares raw input bytes, source digests,
per-field manifest totals and counts. `TN_06_INT_R06_INT_R07_exportPatchesOnlyThreeFieldsAndPreservesDistinctiveFiller`
checks distinctive filler and untouched account bytes, beyond field-level comparator evidence.

## Traceability and boundaries

| Source / design | Named implementation | Independent tests/evidence |
|---|---|---|
| INT-R01/R04, INT-Q01/Q03/Q04, INT-H05/H07; CBACT04C calculation and receivers | `InterestRules.monthly`, `receiver`, `emits` | `InterestRulesTest`, `GroupCalculationTest`, i02/i06/i07 |
| INT-R02/R03/R10, INT-H08; CBACT04C keyed and DEFAULT reads | `GroupCalculation.rate`, `MissingLookup` | all-seven IT, required-lookup IT, i04 SYSOUT |
| INT-R05/R08, INT-Q05, TN-02/TN-06; CBACT04C transaction/timestamp construction | `InterestRules.transaction`, `transactionId`, `timestamp` | constants, advancing-clock, suffix/overflow tests |
| INT-R06/R07/R09, INT-Q02/Q06, INT-H09; CBACT04C 188–222, 325–356 | `AccountGroups.read`, `GroupWriter.write`, `CloseStore.delta` | EOF/empty/no-balance, golden accounts, SQL checkpoint/delta |
| INT-H02/H03, A1/A2, INT-MAP A/X/B/D input bindings | `validateParm`, `SnapshotIntake.load`, `CloseStore.card` | invalid PARM, held intake, supplied AIX evidence tests |
| A3, INT-H06 private recovery only | `CloseRunService.run`, `GroupWriter.write`, `CloseStore.checkpoint` | before/after commit crash and changed identity/snapshot tests |
| INT-R10, INT-H08, TN-03/TN-04/TN-07/TN-09; CBACT04C 234–648 | `LegacyStatus.check`, `format`, `Failure.diagnostics`; coordinator/exporter typed-failure path | `LegacyStatusTest`, `INT_R10_TN_03_TN_04_TN_07_TN_09_typedOperationFailuresRollbackAndPreserveRestrictedSourceOrder` |

Frozen `FixedRecordCodec` and V001/V002 contracts are consumed without edits. No pilot
imports or new dependencies. Dedicated-database migration initialization avoids the shared
pilot schema and tolerates existing cluster roles. `scope.txt` records the exact base diff
and pre-evidence-commit porcelain status; generated `target/` was removed after extracting
its reports/manifest so it is neither in that status nor committed. `work/` remains runtime-only.

## Gaps and unverified assumptions

**This is lab/private-compute parity, not host/live-release approval.** The checklist now has
38 PASS / 0 FAIL criteria. Its four verbatim continuation lines under l each have a separate
PASS reference, making 42 PASS / 0 FAIL result annotations in total. The prior S10 failure
is closed by single-point INT-H06/INT-H04 implementation citations and existing ID-named
tests. Prior S12/i/q/S15 failures are closed by the profile-controlled adapter and independent
operation/formatting/job-failure tests described above, not by merely relabelling fixture parity.

- The adapter's LAB_ASCII profile models the lab's big-endian two-byte binary overlay;
  CP037 and little-endian profiles are explicitly synthetic probes. The caller supplies
  the observed restricted DISPLAY prefix for a generic typed failure. The job does not
  invent legacy statuses from target I/O exceptions. Physical host fault injection and
  actual production binary/CCSID behavior are not verified by these synthetic tests.
- All standards §4 **“Confirm on the mainframe”** items remain unverified: production
  compiler/ARITH/NUMPROC/TRUNC and arithmetic/overflow evidence, sign/transfer/CCSID and
  key ordering specimens, numeric/extended binary status overlays and operation statuses,
  record initialization/filler, AIX first-return order, timezone/DST, scheduler PARM,
  catalog/DISP/absolute generation and downstream conditions. Relevant owners remain
  COBOL/LE, VSAM/card operations, application, Finance/interest policy and Operations.
  LAB_ASCII fixture and synthetic probes do not settle host behavior.
- **INT-H06 stays OPEN.** Explicit technical recovery is for FAILED private target work;
  no automatic retry/live relaunch, host reconciliation, fence/receipt, live GDG advance
  or COMBTRAN release is implemented/approved by this ticket. Package/release guarantees
  outside INT-04 must not be inferred from private rc.json staging.
- Multi-card first-return evidence has an **external source**. Intake records the supplied
  reference and rejects missing evidence; the source/provenance cannot be validated against
  a host in this environment. Single-card fixtures use `single-card` evidence.
- Policy switches and **AccountRecord grants are not used**; shared policy/reference/schema
  contracts and grants were not changed. The lab superuser is used; least-privilege deployment
  is not proved. Snapshot immutability is enforced by application flow and resume checks,
  not an additional DB trigger or changed grant.
- INT-Q03 and fixture README wording implying final-account increases/decreases do not
  supersede CBACT04C 188–222 and the golden accounts. Finance/card operations/interest policy
  remain the register owners; decisions and frozen artifacts are untouched.
- The existing recorder's setup-RC attribution issue remains OPEN for its owner; the frozen
  driver supplies observed setup codes. No recorder/comparator/golden edits or re-recording.

## Raw files beside this report

- `test-output.txt`
- `parity-driver-unittest.txt`
- `job-wrapper-unittest.txt`
- `e2e-run.log`
- `compare-stdout.txt`
- `compare-report.md`
- `i01-sql.sql`
- `i01-sql-output.txt`
- `restart-and-failure-cases.txt`
- `make-parity.txt`
- `launcher-manifest.txt`
- `scope.txt`
- `checklist.md`
