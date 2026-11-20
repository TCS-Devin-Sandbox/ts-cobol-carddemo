---
name: interest-ticket-verify
description: Prove one INTCALC interest ticket (INT-01 to INT-06) in TCS-Devin-Sandbox/ts-cobol-carddemo against the legacy CBACT04C run - the commands that turn the Java job's output into the files tools/parity/compare.py reads, the compare.py command per case, how to read a difference, the SQL check, and the report format the INT-01 pilot used. Use after interest-ticket-build has passed.
---

# Interest ticket verify

Every command below was run from the repository root on a clean checkout after
[interest-ticket-build](../interest-ticket-build/SKILL.md) and succeeded; the quoted line is
the output that shows it. Which behaviour a ticket must reproduce, and which tests prove it,
is in [interest-conversion-standards](../interest-conversion-standards/SKILL.md) §5, §7 and
§9; field-level parity in general is [carddemo-parity](../carddemo-parity/SKILL.md).

`<ticket>` below is the ticket file name without `.md` (`01-pilot`, `02-schema`,
...). Each ticket writes its evidence only under `java/interest/evidence/<ticket>/` and its
runtime output only under `work/<ticket>/`, so tickets built in parallel do not overwrite each
other. The pilot's own evidence stays under
`java/interest/interest-batch/src/test/resources/pilot/evidence/`.

## Steps

1. In Bash, enable pipeline error propagation, make the evidence directory and start a fresh
   report (`--report` appends; a rerun into an old report repeats every heading):
   ```sh
   set -o pipefail
   T=01-pilot
   mkdir -p java/interest/evidence/$T
   rm -f java/interest/evidence/$T/compare-report.md
   ```
2. Turn each case's Java job output into comparator files. The driver runs the real legacy
   setup jobs (DEFGDGB, ACCTFILE, XREFFILE, TCATBALF, DISCGRP), unloads their KSDS datasets for
   Java intake, runs the jar in place of INTCALC STEP15 against compose PostgreSQL, and writes
   `datasets/`, `sysout/` and `rc.json` (setup RCs as observed plus the Java exit code) under
   `--out`. It must run inside `estate`, one case at a time (it recreates the shared
   `datasets/`), and `--out` must be a case directory below `work/`:
   ```sh
   P=java/interest/interest-batch/src/test/resources/pilot
   for c in i01-baseline i02-tie-half-cent i03-zero-rate-skipped i04-default-group-fallback \
            i05-multi-category i06-negative-balance i07-nontie-rounding; do
     docker compose exec -T \
       -e INTEREST_PILOT_JDBC_URL=jdbc:postgresql://db:5432/carddemo \
       -e INTEREST_PILOT_DB_USER=carddemo -e INTEREST_PILOT_DB_PASSWORD=carddemo \
       estate python3 "$P/parity/run_case.py" $c --out work/$T/$c || exit "$?"
   done 2>&1 | tee java/interest/evidence/$T/e2e-run.log
   ```
   Success, once per case: `interest/<case>: Java STEP15 RC=0, outputs in /estate/work/01-pilot/<case>`.
   The cases are the directories under `fixtures/interest/`; the ticket's **Verification**
   line names the ones that gate it.
3. Compare each case field by field against the golden files (host Python, comparator
   unchanged):
   ```sh
   for c in i01-baseline i02-tie-half-cent i03-zero-rate-skipped i04-default-group-fallback \
            i05-multi-category i06-negative-balance i07-nontie-rounding; do
     python3 tools/parity/compare.py interest $c --actual work/$T/$c --label java \
       --report java/interest/evidence/$T/compare-report.md || exit "$?"
   done | tee java/interest/evidence/$T/compare-stdout.txt
   ```
   Success, each case with exit 0:
   ```text
   interest/i01-baseline [java]: PASS (4 records, 50 fields, 0 differences)
   interest/i02-tie-half-cent [java]: PASS (4 records, 50 fields, 0 differences)
   interest/i03-zero-rate-skipped [java]: PASS (2 records, 25 fields, 0 differences)
   interest/i04-default-group-fallback [java]: PASS (2 records, 25 fields, 0 differences)
   interest/i05-multi-category [java]: PASS (3 records, 38 fields, 0 differences)
   interest/i06-negative-balance [java]: PASS (2 records, 25 fields, 0 differences)
   interest/i07-nontie-rounding [java]: PASS (4 records, 50 fields, 0 differences)
   ```
   `grep -c '^## ' java/interest/evidence/$T/compare-report.md` prints `7`.
   A nonzero case exits the loop immediately and makes the pipeline nonzero even when
   `tee` succeeds. Stop on a nonzero exit; do not treat a partial log as parity evidence.
4. Read a difference. A failing case prints
   `interest/<case> [java]: FAIL (<records> records, <fields> fields, <n> differences)` and
   exits 1. Its section in `compare-report.md` is headed
   `## interest / <case> — java: **FAIL (<n> difference)**` and has a table
   `| <file> | <field> | expected | actual |` (decoded copybook field, or `(text)` with a line
   diff for SYSOUT and `rc.json`) followed by `Fields differing: ...`. Read it as: the golden
   (legacy) value is right, the Java value is wrong, unless the field is a documented quirk
   (standards §5) the candidate has not reproduced. Fix the Java code, rerun steps 2-3 for that
   case. Never edit `fixtures/`, `tools/parity` or `app/` to make it pass (AGENTS.md). A
   difference in `rc.json` alone means the actual file does not list every INTCALC step code
   the golden lists; see [interest-pitfalls](../interest-pitfalls/SKILL.md).
5. Check the PostgreSQL rows of the ticket case against the case README. Take the first run's
   id from the driver log and run the ticket's SQL assertions (the pilot's are
   `java/interest/evidence/01-pilot/i01-sql.sql`; every assertion selects one boolean):
   ```sh
   run_id=$(grep -m1 -o 'event=run-completed run=[^ ]*' java/interest/evidence/$T/e2e-run.log | cut -d= -f3)
   docker compose exec -T db psql -U carddemo -d carddemo -v ON_ERROR_STOP=1 -v run_id="$run_id" -f - \
     < java/interest/evidence/$T/i01-sql.sql | tee java/interest/evidence/$T/i01-sql-output.txt
   ```
   Success: exit 0 and ` t` under `transactions_pass`, `delta_pass` and
   `final_account_unchanged_pass`. The run id differs on every run.
6. Write `java/interest/evidence/<ticket>/parity-report.md` in the pilot's format (its example:
   `java/interest/interest-batch/src/test/resources/pilot/evidence/parity-report.md`):
   - title line `# <ticket id> – per-case parity report (open this first)`, then the code
     revision, Java / Spring / PostgreSQL versions and the legacy baseline command and result;
   - the exact commands of steps 2-3;
   - `## Result per case`: one row per case,
     `| Case | Ticket case | compare.py | Datasets (records / decoded fields) | SYSOUT STEP15 | rc.json |`,
     e.g. `| i01-baseline | yes | **PASS** | 4 / 50, 0 differences | identical | identical |`;
   - the SQL result of step 5 with the run id, and the Maven totals from interest-ticket-build
     step 4;
   - a list of the raw files beside it: `test-output.txt`, `e2e-run.log`,
     `compare-stdout.txt`, `compare-report.md`, the SQL script and its output.
7. Commit `java/interest/evidence/<ticket>/`; `work/` is runtime output and is not committed.
