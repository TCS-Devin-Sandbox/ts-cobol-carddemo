#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../../.." && pwd)"
cd "$ROOT"
EVIDENCE="${1:-java/interest/evidence/06-parity-gate}"
mkdir -p "$EVIDENCE"
SCRIPTS=java/interest/interest-batch/scripts/parity
DRIVER=java/interest/interest-batch/src/test/resources/pilot/parity/run_case.py
CASES=(i01-baseline i02-tie-half-cent i03-zero-rate-skipped i04-default-group-fallback
       i05-multi-category i06-negative-balance i07-nontie-rounding)
{
  printf 'revision=%s\n' "$(git rev-parse HEAD)"
  git status --short
  printf 'command=%q %q\n' "$0" "$EVIDENCE"
} > "$EVIDENCE/gate-revision.txt"
: > "$EVIDENCE/e2e-run.log"
: > "$EVIDENCE/compare-report.md"
: > "$EVIDENCE/sql-output.txt"

for c in "${CASES[@]}"; do
  docker compose exec -T \
    -e INTEREST_PILOT_JDBC_URL=jdbc:postgresql://db:5432/carddemo \
    -e INTEREST_PILOT_DB_USER=carddemo -e INTEREST_PILOT_DB_PASSWORD=carddemo \
    estate python3 "$DRIVER" "$c" --out "work/06-parity-gate/$c" \
    --java "/estate/$SCRIPTS/intcalc_java.py" 2>&1 | tee -a "$EVIDENCE/e2e-run.log"
done
for c in "${CASES[@]}"; do
  python3 tools/parity/compare.py interest "$c" --actual "work/06-parity-gate/$c" --label java \
    --report "$EVIDENCE/compare-report.md"
done | tee "$EVIDENCE/compare-stdout.txt"
python3 "$SCRIPTS/byte_check.py" | tee "$EVIDENCE/byte-check.txt"
mapfile -t RUNS < <(sed -n 's/.*event=package-shadow run=\([^ ]*\).*/\1/p' "$EVIDENCE/e2e-run.log")
if [[ ${#RUNS[@]} -ne 7 || $(printf '%s\n' "${RUNS[@]}" | sort -u | wc -l) -ne 7 ]]; then
  echo "GATE FAIL: expected seven distinct shadow run IDs" >&2
  exit 1
fi
for i in "${!CASES[@]}"; do
  c="${CASES[$i]}"
  facts="$(python3 "$SCRIPTS/byte_check.py" --case "$c" --facts)"
  IFS=$'\t' read -r tx_count delta_count tx_sum delta_sum last_account <<< "$facts"
  docker compose exec -T db psql -U carddemo -d interest_close -v ON_ERROR_STOP=1 \
    -v case="$c" -v run_id="${RUNS[$i]}" -v tx_count="$tx_count" -v delta_count="$delta_count" \
    -v tx_sum="$tx_sum" -v delta_sum="$delta_sum" -v last_account="$last_account" \
    -f - < "$SCRIPTS/gate-sql.sql" | tee -a "$EVIDENCE/sql-output.txt"
done
echo "GATE PASS cases=7 byte_files=14 sql_runs=7"
