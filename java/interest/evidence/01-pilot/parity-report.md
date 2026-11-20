# INT-01 – per-case parity report (open this first)

Step 5a review replay: `demo/05a-skills` at `9590642c55f79970da483327a126ea4e778cb717`.
The pilot code is unchanged from [PR #15](https://github.com/TCS-Devin-Sandbox/ts-cobol-carddemo/pull/15).
Java 21.0.13.1, Maven 3.9.9, Spring Boot 3.3.5 / Spring Batch 5.1.2, PostgreSQL 15.20.
With the estate base image set to the Ubuntu 22.04 mirror, `make up && make build && make parity CHAIN=interest`
compiled CBACT04C and returned 7/7 legacy PASS.

## Commands

Ran from the repository root in Bash, after verify step 1 initialized `T=01-pilot`,
enabled `pipefail` and cleared the previous comparator report in the replay checkout:

```sh
P=java/interest/interest-batch/src/test/resources/pilot
for c in i01-baseline i02-tie-half-cent i03-zero-rate-skipped i04-default-group-fallback \
         i05-multi-category i06-negative-balance i07-nontie-rounding; do
  docker compose exec -T \
    -e INTEREST_PILOT_JDBC_URL=jdbc:postgresql://db:5432/carddemo \
    -e INTEREST_PILOT_DB_USER=carddemo -e INTEREST_PILOT_DB_PASSWORD=carddemo \
    estate python3 "$P/parity/run_case.py" $c --out work/$T/$c || exit "$?"
done 2>&1 | tee java/interest/evidence/$T/e2e-run.log

for c in i01-baseline i02-tie-half-cent i03-zero-rate-skipped i04-default-group-fallback \
         i05-multi-category i06-negative-balance i07-nontie-rounding; do
  python3 tools/parity/compare.py interest $c --actual work/$T/$c --label java \
    --report java/interest/evidence/$T/compare-report.md || exit "$?"
done | tee java/interest/evidence/$T/compare-stdout.txt
```

## Result per case

The driver printed `Java STEP15 RC=0` for each of seven cases; the comparator report had
seven headings. Only i01 is required by the pilot ticket; the other six are informational.

| Case | Ticket case | compare.py | Datasets (records / decoded fields) | SYSOUT STEP15 | rc.json |
|---|---|---|---|---|---|
| i01-baseline | yes | **PASS** | 4 / 50, 0 differences | identical | identical |
| i02-tie-half-cent | no | PASS | 4 / 50, 0 differences | identical | identical |
| i03-zero-rate-skipped | no | PASS | 2 / 25, 0 differences | identical | identical |
| i04-default-group-fallback | no | PASS | 2 / 25, 0 differences | identical | identical |
| i05-multi-category | no | PASS | 3 / 38, 0 differences | identical | identical |
| i06-negative-balance | no | PASS | 2 / 25, 0 differences | identical | identical |
| i07-nontie-rounding | no | PASS | 4 / 50, 0 differences | identical | identical |

This matches the [pilot parity report](../../interest-batch/src/test/resources/pilot/evidence/parity-report.md)
case by case. It does not prove the error path, restart, or legacy host bytes.

## SQL and test results

Run `7e2e8206-52f2-4725-b1e5-581d45f9dc8f`: two transactions (10.00 and 37.00)
totaled 47.00. Account `00000002001` had the only delta, 1000.00 → 1010.00, with both
cycle totals 0.00; account `00000002002` stayed at 2400.00 with no delta.
`transactions_pass`, `delta_pass` and `final_account_unchanged_pass` each printed `t`.

```text
[INFO] Tests run: 36, Failures: 0, Errors: 0, Skipped: 0
[INFO] Tests run: 10, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

The Python driver unit test printed `Ran 1 test` and `OK`.

The clean checkout regenerated `test-output.txt`, `e2e-run.log`, `compare-stdout.txt`,
`compare-report.md` and `i01-sql-output.txt` using [i01-sql.sql](i01-sql.sql). The existing
raw outputs and transcript beside this report are retained from the earlier replay; they
are not logs for the run ID above.
