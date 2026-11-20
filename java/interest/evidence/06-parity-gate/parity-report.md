# INT-06 – per-case parity report (open this first)

Code revision: `77e37b12c3c032d929ab6452f67ae040832e935f`,
branch `demo/05-g4-gate`; base `origin/demo/05b-integration`.
The evidence-only commit follows this code commit.

Verification chronology: commands executed against uncommitted code subsequently
recorded by the code revision above, not after its commit. `gate-revision.txt`
honestly records the then-HEAD `4f687969ff2b05d5a13b7ac7c9df0a637dffafef`
and untracked paths. The final Maven log includes the additional partial-apply
restoration test; the gate script ran before that test-only addition. Candidate
production code, parity scripts and golden inputs did not change between these
checks and the code commit. No checks are represented as post-commit reruns.

Versions: Java 21.0.13.1, Spring Boot 3.3.5 / Spring Batch 5.1.2;
PostgreSQL 15.20 (compose `postgres:15`), PG15 Testcontainers
(`postgres:15-alpine`, Testcontainers 1.21.4).

Legacy baseline: `make up && make build` completed before candidate verification
(estate base `mirror.gcr.io/library/ubuntu:22.04`). Final baseline command
`make parity` exited 0: **24/24 comparisons PASS**, zero FAIL, across posting 7,
interest 7, trantype-update 7 and trantype-dynamic 3. Its log has 25 total lines:
24 comparison lines and one report-location line. This is legacy baseline
evidence, not candidate proof.

## Exact commands

From repository root (Java 21 exported in each Maven shell):

```sh
set -o pipefail
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
E=java/interest/evidence/06-parity-gate
mvn -B -f java/interest/interest-batch/pom.xml clean verify 2>&1 |
  tee "$E/test-output.txt"
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover \
  -s java/interest/interest-batch/scripts/parity -v 2>&1 |
  tee "$E/parity-unittest.txt"
java/interest/interest-batch/scripts/parity/run_gate.sh "$E" 2>&1 |
  tee "$E/gate-output.txt"
make parity 2>&1 | tee "$E/make-parity.txt"
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover \
  -s java/interest/interest-batch/src/test/resources/pilot/parity -v 2>&1 |
  tee "$E/parity-driver-unittest.txt"
```

Long command logs were redirected to `/dev/null` after tee during execution.
The gate expands the unchanged driver/comparator steps as follows, for all seven
case names listed below:

```sh
P=java/interest/interest-batch/src/test/resources/pilot
S=java/interest/interest-batch/scripts/parity
for c in i01-baseline i02-tie-half-cent i03-zero-rate-skipped i04-default-group-fallback \
         i05-multi-category i06-negative-balance i07-nontie-rounding; do
  docker compose exec -T \
    -e INTEREST_PILOT_JDBC_URL=jdbc:postgresql://db:5432/carddemo \
    -e INTEREST_PILOT_DB_USER=carddemo -e INTEREST_PILOT_DB_PASSWORD=carddemo \
    estate python3 "$P/parity/run_case.py" "$c" --out "work/06-parity-gate/$c" \
    --java "/estate/$S/intcalc_java.py"
done
for c in i01-baseline i02-tie-half-cent i03-zero-rate-skipped i04-default-group-fallback \
         i05-multi-category i06-negative-balance i07-nontie-rounding; do
  python3 tools/parity/compare.py interest "$c" --actual "work/06-parity-gate/$c" \
    --label java --report "$E/compare-report.md"
done
python3 "$S/byte_check.py"
# Per case, run_gate.sh derives expected SQL values from golden files:
python3 "$S/byte_check.py" --case "$c" --facts
# It passes those values and the corresponding package-shadow run ID to:
docker compose exec -T db psql -U carddemo -d interest_close -v ON_ERROR_STOP=1 \
  -v case="$c" -v run_id="$run_id" -v tx_count="$tx_count" -v delta_count="$delta_count" \
  -v tx_sum="$tx_sum" -v delta_sum="$delta_sum" -v last_account="$last_account" \
  -f - < "$S/gate-sql.sql"
```

The executable gate supplies fail-fast handling and tees the raw outputs. Its
compare report is freshly truncated per run, not appended to historical results.

## Result per case

All seven cases are INT-06 gates, not informational-only runs.

| Case | Ticket case | compare.py | Datasets (records / decoded fields) | SYSOUT STEP15 | rc.json |
| --- | --- | --- | --- | --- | --- |
| i01-baseline | yes | **PASS** | 4 / 50, 0 differences | identical | identical |
| i02-tie-half-cent | yes | **PASS** | 4 / 50, 0 differences | identical | identical |
| i03-zero-rate-skipped | yes | **PASS** | 2 / 25, 0 differences | identical | identical |
| i04-default-group-fallback | yes | **PASS** | 2 / 25, 0 differences | identical | identical |
| i05-multi-category | yes | **PASS** | 3 / 38, 0 differences | identical | identical |
| i06-negative-balance | yes | **PASS** | 2 / 25, 0 differences | identical | identical |
| i07-nontie-rounding | yes | **PASS** | 4 / 50, 0 differences | identical | identical |

The frozen driver executes real setup jobs and substitutes only STEP15 with
`com.carddemo.interest.batch.InterestBatchApplication` through PropertiesLauncher.
The jar's frozen pilot Start-Class and pom are untouched. Every candidate
process returned 0. Ordered byte checks cover both datasets in all seven cases,
including filler and padding restored to LRECL 300/350 under fixture LF framing:

```text
BYTE CHECK PASS cases=7 files=14 framing=LF-space-padded
GATE PASS cases=7 byte_files=14 sql_runs=7
```

## PostgreSQL results

Eight assertions per run in `sql-output.txt` are all `t`: `run_pass`,
`tx_count_pass`, `delta_count_pass`, `tx_sum_pass`, `delta_sum_pass`,
`last_account_pass`, `publication_pass`, `no_release_pass`.
Expected signed sums/counts come independently from golden TRAN-AMT and
input/golden account differences, never candidate output.

| Case | Run ID | Transactions | Deltas | Signed tx sum | Signed delta sum |
| --- | --- | ---: | ---: | ---: | ---: |
| i01-baseline | f9f6d804-8521-41ed-8ac7-3b1d6439f29d | 2 | 1 | 47.00 | 10.00 |
| i02-tie-half-cent | d3d68f0f-37ab-4479-a854-2ca6069515a4 | 2 | 1 | 24.69 | 12.35 |
| i03-zero-rate-skipped | c26640b0-7f37-41dd-a076-9b0f9c467c92 | 1 | 0 | 10.00 | 0.00 |
| i04-default-group-fallback | 4fec44e8-45d5-4acc-8224-100d219ed432 | 1 | 0 | 17.50 | 0.00 |
| i05-multi-category | a002b13a-5369-4445-8707-d164b09ad23c | 2 | 0 | 17.50 | 0.00 |
| i06-negative-balance | 9aa88da1-ba57-4bdf-b2eb-13081491b2d4 | 1 | 0 | -3.00 | 0.00 |
| i07-nontie-rounding | 0238c712-2c3a-48e5-99a1-62853aef7e04 | 2 | 1 | 29.03 | 15.42 |

All have one PRIVATE shadow publication and no received/released effects.
The final TCATBALF account has no delta and its golden account bytes equal input
(INT-Q02); SQL checks exclusion while Java/byte facts checks establish unchanged
golden bytes. Fixture README statements implying final-account updates remain
inconsistent with recorded goldens (INT-Q02/Q03); Finance/card operations own
that open decision, and no README, golden or register was edited.

## Failure and release rehearsals

Gate tests run the actual candidate job on PostgreSQL 15. Missing DEFAULT,
account, xref, malformed TCATBALF and missing input each return 12, expose no
SYSTRAN/ACCTDATA artifacts, have zero publications/published deltas and no
COMPUTED/RELEASED run. Packaging column/raw disagreement also returns 12 and
removes private success artifacts (`InterestBatchIT`).

| Scenario | Outcome / verified boundary |
| --- | --- |
| Unstaged malformed intake | RC 12; no computed run/publication; PacketBuilder RUN_MISSING |
| Host preimage changed | HELD PREIMAGE_MISMATCH; zero applies/catalogs/signals |
| Fence replaced after preflight | HELD FENCE_STALE; zero applies/catalogs/signals |
| Unknown apply / no receipt | HELD APPLY_UNCERTAIN then RECEIPT_MISSING; one apply, zero catalogs/signals; resolve never reapplies |
| Partial-account restoration | Two of three deltas attempted/applied; owner restores exactly two journaled before-images; all capture bytes/counts restored; HELD; no generation/signals |
| Ambiguous generation restoration | One journaled account restored; exact cataloged generation quarantined; previous pointer NONE; HELD; one catalog total, zero signals |
| Post-consumption containment | Existing verified RELEASED remains final; repeated handoff/resolve produce no additional apply/catalog/signal/job launch; one signal total; simulated restore prohibited |

SQL invariants after every rehearsal: no RELEASED without verified receipt,
at most one publication per run and at most one live claim per destination/cycle.
These are in-memory host rehearsals, not host runtime proof or authorization.

## Build and test totals

```text
[INFO] Tests run: 70, Failures: 0, Errors: 0, Skipped: 0
[INFO] Tests run: 80, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

Surefire 70 + Failsafe 80 = 150. Gate additions: 12 candidate parity/failure
invocations and 7 release rehearsals. Script Python tests: 7, OK, including
nonzero CLI mismatch diagnostics. Frozen driver: 1 test, OK.

## Outstanding live release sign-offs

See [INT-06-release-readiness.md](../../interest-batch/docs/verification/INT-06-release-readiness.md)
for all OPEN INT-Q01–Q06, INT-H01–H09 (especially H05/H06), manifest asks
interest-1–interest-4 and explicit gaps: no real host bridge/fence/GDS recovery,
binder provenance, compiler options or approval. No live cutover or deployment.

## Raw evidence and scope

- `test-output.txt`: final Maven suite, GATE per-case and REHEARSAL summaries.
- `e2e-run.log`, `gate-output.txt`: seven real-setup candidate runs and full gate.
- `compare-stdout.txt`, `compare-report.md`: unchanged field/RC/SYSOUT comparison.
- `byte-check.txt`: 14 ordered, padded dataset comparisons.
- `sql-output.txt`: eight SQL booleans per run plus expected totals.
- `gate-revision.txt`: original then-HEAD, dirty paths and gate command.
- `parity-unittest.txt`: final seven wrapper/byte tests.
- `wrapper-unittest.txt`: earlier three adapter tests, retained as historical.
- `parity-driver-unittest.txt`: frozen driver's one test.
- `make-parity.txt`: final full legacy 24/24 baseline.
- `scope.txt`: diff statistics, untracked listing and allowed-path audit.
- `checklist.md`: complete conversion standards §9 review.

Scope includes only ticket-owned gate/scripts/docs/evidence plus explicitly
granted batch orchestration and its tests. No build/shared config or frozen
intake/calc/job/handoff/codec/pilot source changed. `target/` is generated,
untracked and deliberately excluded from commits; runtime artifacts stay in
ignored `work/`.
