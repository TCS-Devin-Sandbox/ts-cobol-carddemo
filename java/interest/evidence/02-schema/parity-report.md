# INT-02 – per-case parity report (open this first)

## Candidate and boundary
Branch: demo/05-g1-groundwork, based on origin/demo/05b-integration.
The candidate revision is the INT-02 commit containing this report; migration-inventory.txt
pins the exact tested SQL and test source SHA-256 values (avoids a self-referential commit hash).
Resolve its commit SHA with `git log -1 --format=%H -- java/interest/evidence/02-schema/parity-report.md`.

Java 21.0.13.1, Maven 3.9.9, Spring Boot 3.3.5 / Batch 5.1.2;
PostgreSQL 15.20 (Alpine Testcontainers; Debian compose).
Environment: installed openjdk-21-jdk-headless, pulled mirror.gcr.io/library/postgres:15
and :15-alpine and retagged to postgres:15 and postgres:15-alpine.
`ESTATE_BASE_IMAGE=mirror.gcr.io/library/ubuntu:22.04 make up && make build && make parity CHAIN=interest`
completed with 7/7 interest PASS before implementation. Containers remain running.
`make parity` subsequently passed 24/24 cases across all four chains (make-parity.txt).

INT-02 introduces versioned SQL, applied explicitly by JDBC ScriptUtils in an isolated clean
PG15 database. No Flyway or runtime autoload configuration/dependency is required or added.
V001 owns ten interest tables, role grants, layout checks and ordered keys.
V002 owns six Spring Batch tables, three sequences and isolated repository grants.
The existing pilot runtime remains unchanged: the seven-case driver is a regression
proof, not proof the pilot has adopted the new tables. New schema acceptance is proved by SchemaIT.

## Trace and assumptions
INT-ARCH A1/A3 and INT-MAP A01–A13, X01–X04, B00–B05, D00–D05, T01–T14
govern the columns. INT-R09 uses snapshot/BYTEA order, INT-R10 permits missing
cross-layout records, INT-H03 preserves nonunique account xrefs with one flagged first,
INT-H05 retains repeated transaction IDs and INT-H06 retains private run/checkpoint metadata.
Relevant legacy reads/rewrite: CBACT04C 350–415; frozen copybook definitions and map cited above.
All decision-register rows remain OPEN. No host CCSID, multi-card first-return semantics,
live recovery, source-byte consistency, sealing immutability or lifecycle transitions
are certified by DDL: those remain intake/job/handoff responsibilities.
Metadata fields beyond INT-MAP are additive scaffold contracts: JSON manifests and receipts,
explicit lifecycle state, destination/generation identifiers and build/rule/clock identity.
Roles are NOLOGIN group roles created by the migration owner; deployment assigns memberships.
Migrations are intentionally apply-once on a clean database and require schema/role creation privileges.
SQL typmods cannot detect source overscale before coercion: intake must reject it before INSERT.

## Commands
From repository root, Bash:
```sh
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
set -o pipefail
mvn -B -f java/interest/interest-batch/pom.xml clean verify 2>&1 |
  tee java/interest/evidence/02-schema/test-output.txt
python3 -m unittest discover -s java/interest/interest-batch/src/test/resources/pilot/parity -v
T=02-schema
P=java/interest/interest-batch/src/test/resources/pilot
for c in i01-baseline i02-tie-half-cent i03-zero-rate-skipped i04-default-group-fallback \
         i05-multi-category i06-negative-balance i07-nontie-rounding; do
  docker compose exec -T \
    -e INTEREST_PILOT_JDBC_URL=jdbc:postgresql://db:5432/carddemo \
    -e INTEREST_PILOT_DB_USER=carddemo -e INTEREST_PILOT_DB_PASSWORD=carddemo \
    estate python3 "$P/parity/run_case.py" "$c" --out "work/$T/$c" || exit "$?"
done 2>&1 | tee java/interest/evidence/$T/e2e-run.log
for c in i01-baseline i02-tie-half-cent i03-zero-rate-skipped i04-default-group-fallback \
         i05-multi-category i06-negative-balance i07-nontie-rounding; do
  python3 tools/parity/compare.py interest "$c" --actual "work/$T/$c" --label java \
    --report java/interest/evidence/$T/compare-report.md || exit "$?"
done | tee java/interest/evidence/$T/compare-stdout.txt
run_id=$(rg -m1 -o 'event=run-completed run=[^ ]*' java/interest/evidence/$T/e2e-run.log | cut -d= -f3)
docker compose exec -T db psql -U carddemo -d carddemo -v ON_ERROR_STOP=1 \
  -v run_id="$run_id" -f - < java/interest/evidence/$T/i01-sql.sql |
  tee java/interest/evidence/$T/i01-sql-output.txt
make parity 2>&1 | tee java/interest/evidence/02-schema/make-parity.txt
```

## Result per case
| Case | Ticket case | compare.py | Datasets (records / decoded fields) | SYSOUT STEP15 | rc.json |
|---|---|---|---|---|---|
| i01-baseline | yes | **PASS** | 4 / 50, 0 differences | identical | identical |
| i02-tie-half-cent | regression | **PASS** | 4 / 50, 0 differences | identical | identical |
| i03-zero-rate-skipped | regression | **PASS** | 2 / 25, 0 differences | identical | identical |
| i04-default-group-fallback | regression | **PASS** | 2 / 25, 0 differences | identical | identical |
| i05-multi-category | regression | **PASS** | 3 / 38, 0 differences | identical | identical |
| i06-negative-balance | regression | **PASS** | 2 / 25, 0 differences | identical | identical |
| i07-nontie-rounding | regression | **PASS** | 4 / 50, 0 differences | identical | identical |

## SQL and tests
i01 run d8316dbb-7114-455a-b7f5-d4a28b09a272:
transactions_pass=t; delta_pass=t; final_account_unchanged_pass=t.
Surefire: Tests run: 36, Failures: 0, Errors: 0, Skipped: 0.
Failsafe: Tests run: 16, Failures: 0, Errors: 0, Skipped: 0 (6 schema, 10 existing).
Driver unittest: Ran 1 test; OK.
SchemaIT covers clean migration inventory, numeric typmods, every mapped NOT NULL,
short-width rejection, raw/filler/key lengths, duplicate PK, positive per-run ordinals,
repeated TRAN-ID, same-account nonunique xrefs/partial unique first flag,
missing cross-layout dependencies, unsigned BYTEA ordering/index,
separate schema privileges and absence of defaults/cascades.
Before/after carddemo sentinel objects, rows and ACL comparison is in schema-assertions.txt.
Migrations contain no carddemo references. Synthetic data only.

Initial test-authoring issue fixed before final run: width-negative test used 'x',
which is valid for VARCHAR(1). Using empty text rejects every exact-width field.
No production SQL failure was hidden or worked around.
Existing Spring BeanPostProcessorChecker and Byte Buddy dynamic-agent warnings are
from the frozen pilot dependencies, not a missing environment setup; Java 21 and
PostgreSQL 15 are loaded and all tests finish successfully. No skill fixes were necessary.
Proposed standards clarification: distinguish apply-once schema testing from automatic
runtime migration activation, and distinguish pilot regression parity from adoption of INT-02.

## Raw evidence
- test-output.txt: complete final Maven output
- parity-driver-unittest.txt
- migration-inventory.txt: SQL and test source digests
- schema-assertions.txt: DB version/metadata, positive/negative assertions and TN-08 proof
- e2e-run.log
- compare-stdout.txt
- compare-report.md
- i01-sql.sql / i01-sql-output.txt
- make-parity.txt
- checklist.md
