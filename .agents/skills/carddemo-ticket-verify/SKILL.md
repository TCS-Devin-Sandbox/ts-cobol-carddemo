---
name: carddemo-ticket-verify
description: Compare a CardDemo batch conversion with legacy files and capture ticket parity evidence.
---

# CardDemo ticket verify

Use after [carddemo-ticket-build](../carddemo-ticket-build/SKILL.md). Read S15/S16 and the review checklist in the [standards](../carddemo-conversion-standards/SKILL.md). The annex supplies the adapter, slot being converted, cases, expected datasets, SQL assertions and sample evidence. Treat generated files as restricted synthetic evidence.

## Step 1 · Evidence and work directories

Run in the same Bash session as the build. Stop on a nonzero block. Set `WORK_ROOT` per ticket or replay, under `work/`. Use independent ticket output directories. Start a fresh report because the comparator appends.

```sh
set -o pipefail
mkdir -p "$EVIDENCE" "$WORK_ROOT"
rm -f "$EVIDENCE/compare-report.md"
```

All commands exited 0 in the proof. The new report had one heading per case.

## Step 2 · Candidate outputs

Define `run_candidate` using the annex. It runs the real legacy setup and substitutes only the converted step or steps. Preserve the recorded artifact structure and observed setup RCs. Use the process exit code for each substituted step. Execute one case at a time when the estate shares a dataset directory. Give each case a distinct output location below `work/`. A ticket may call out gating cases, while a combined stream proof runs every recorded case.

```sh
for case_dir in "fixtures/$STREAM"/*/; do
  c=${case_dir%/}
  c=${c##*/}
  run_candidate "$c" "$WORK_ROOT/$c" || exit "$?"
done 2>&1 | tee "$EVIDENCE/e2e-run.log"
```

The proof printed one `RC=0, outputs in` message per case. If using a new Java entry point under a frozen build, use the wrapper contract in the annex.

## Step 3 · Field-level comparison and estate regression

Keep the comparator unchanged. Match dataset fields, SYSOUT and RC, and DB snapshots where specified. Check byte framing separately when required by the ticket.

```sh
for case_dir in "fixtures/$STREAM"/*/; do
  c=${case_dir%/}
  c=${c##*/}
  python3 tools/parity/compare.py "$STREAM" "$c" --actual "$WORK_ROOT/$c" --label java \
    --report "$EVIDENCE/compare-report.md" || exit "$?"
done | tee "$EVIDENCE/compare-stdout.txt"
grep -c '^## ' "$EVIDENCE/compare-report.md"
make parity
```

With `pipefail`, a nonzero case ends the loop and makes its pipeline nonzero even if `tee` succeeds. Stop at a failed block. The proof printed `PASS` and `0 differences` for every candidate case, `7` headings and 24 passing legacy regressions. See [full output](../../../modernization/design/evidence/skills-shared-check.txt).

## Step 4 · Differences

A failing comparison exits 1. In its report section, read the file, field, expected and actual columns and the `Fields differing:` summary. A `(text)` row can expose differences in SYSOUT or `rc.json`. Reproduce recorded legacy behaviour, including documented quirks, and rerun the affected case. For an RC-only failure, compare the full observed step map. Keep fixtures, source and comparator frozen (S17).

## Step 5 · SQL assertions

Where the ticket requires it, check native PostgreSQL rows with independent assertions against the case README. Use the run ID from the applicable driver or job output. Save the SQL and query output or the relevant PostgreSQL 15 integration-test output. The annex documents which schema and assertion method apply.

## Step 6 · Report

Record the ticket ID, code revision, Java/Spring/PostgreSQL versions, legacy baseline and exact run and compare commands. In the per-case table list case, ticket case, comparator result, dataset record/field counts, SYSOUT and RC. Include independent SQL results with run ID where applicable, Maven totals, and links to the raw build, run, compare and SQL artifacts. Put the report beside its evidence. See the stream annex for the original report format and sample.

## Step 7 · Commit

Commit new ticket evidence under the annex's evidence root and keep runtime outputs untracked. Follow the annex's replay exception when replaying a historical ticket.
