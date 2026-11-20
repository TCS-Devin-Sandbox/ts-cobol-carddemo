# INT-03 – per-case parity report (open this first)

## Candidate
Branch demo/05-g1-groundwork; INT-03 parent fb5001878fa6c16ab5fe48c987a25fe9b8f8c6c8.
The final candidate is the INT-03 commit containing this report. Resolve with
`git log -1 --format=%H -- java/interest/evidence/03-record-codecs/parity-report.md`.
source-inventory.txt pins exact tested source SHA-256 digests without a self-referential hash.

Java 21.0.13.1; Maven 3.9.9; Spring Boot 3.3.5; Spring Batch 5.1.2;
PostgreSQL 15.20 (Alpine Testcontainers and Debian compose).
Existing environment from INT-02 remains loaded; containers left running.
Legacy baseline: `make parity` → 24/24 PASS, including all seven interest cases.

## Implementation and trace
FixedRecordCodec is independent of intake, pilot and SQL.
- decode(Layout, Profile, byte[]) or decode(Layout, String, byte[]) returns Decoded:
  a sealed typed Account/CardXref/CategoryBalance/DisclosureRate/InterestTransaction
  business record plus defensively copied original bytes.
- Decoded.legacyKey() returns exact base/composite bytes; alternateKey() returns XREF
  offset 25/length 11. compareKeys uses unsigned byte order. No AIX-first selection.
- encode(Decoded) is byte-exact, including negative zero. encode(Decoded, Map) validates
  replacement fields and copies all omitted bytes unchanged.
- encode(Layout, Profile, BusinessRecord) validates complete records, including exact
  widths and scale. Newly encoded signed decimals use canonical positive/negative zones;
  retain Decoded for round-trip preservation of signed zero.
- Rejection carries layout, INT-MAP row, zero-based offset and reason; no raw data in message.
  Character conversion failures identify the mapped field start; digit/sign errors identify
  the exact bad digit byte. Framing errors use RECORD and the first missing/excess offset.

Layouts checked against CVACT01Y/CVACT03Y/CVTRA01Y/CVTRA02Y/CVTRA05Y:
ACCOUNT=300, XREF=50, TCATBAL=50, DISCGRP=50, TRAN=350.
Bindings follow all INT-MAP X/D/Z/B rows. B00/D00 are original composite bytes,
not reconstructed SQL strings. INT-ARCH A2 / INT-R09 encoded ordering is proved with
A1 versus 01: ASCII and synthetic cp037 have opposite expected orders.
INT-H03 preserves XREF base/AIX keys, but cannot establish authoritative first-card evidence.
INT-R08 / INT-Q05 / TN-02 preserve opaque IDs, dates and 26-byte timestamp strings;
no date parsing, clock sampling, ID generation or business arithmetic is added.
TN-01/06 fixed records and filler survive round trips and three-field account patches.
Legacy source inspected: CBACT04C 350–415, 415–500; copybook field widths/offsets;
architecture A2, rule/decision register and standards S08 / §4 d,f,g,h,j govern this scope.

## Profiles and framing
Only LAB_ASCII and SYNTHETIC_CP037 are admitted; cp037 is not CCSID certification.
Signed DISPLAY accepts canonical ASCII {ABCDEFGHI / }JKLMNOPQR and cp037 C/D zones;
unproven F-positive and other zones are rejected. Unsigned fields admit only ASCII/profile
digits, keeping leading zero width. Filler is opaque, including non-text bytes.
Text conversion reports unmappable input; control characters in text are rejected.

Important fixture observation: committed input text lines omit trailing storage spaces
(e.g. XREF lines are 36 bytes, not 50). The test-only framing adapter explicitly restores
spaces to copybook LRECL before calling the codec. It does not trim or repair codec input.
The codec itself strictly rejects short, long and empty records, with no LF/RDW acceptance.
“Wrong padding” is tested as missing/excess fixed-width field storage; text inside a valid
PIC X field is not assigned an invented semantic padding rule.

All decisions remain OPEN. No COMP-3, production sign/CCSID certification, AIX evidence
generation, host connection, schema edits or application integration is included.
Only synthetic fixtures and synthetic byte patterns are used.

## Commands
From repository root, Bash:
```sh
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
set -o pipefail
mvn -B -f java/interest/interest-batch/pom.xml clean verify 2>&1 |
  tee java/interest/evidence/03-record-codecs/test-output.txt
python3 -m unittest discover -s java/interest/interest-batch/src/test/resources/pilot/parity -v
T=03-record-codecs
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
make parity 2>&1 | tee java/interest/evidence/03-record-codecs/make-parity.txt
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
**Ticket SQL assertion: N/A — codecs perform no database writes.**
The requested i01 SQL file/output is pilot regression evidence only:
run 7e0b5b21-8fd8-4bf9-b157-4eadb0175c89, transactions_pass=t, delta_pass=t,
final_account_unchanged_pass=t. Existing schema IT metadata/TN-08 sentinel proof is
captured in db-metadata-regression.txt; INT-03 contains no SQL migrations.

Surefire: Tests run: 44, Failures: 0, Errors: 0, Skipped: 0 (8 codec, 36 existing).
Failsafe: Tests run: 16, Failures: 0, Errors: 0, Skipped: 0 (existing tests).
Driver unittest: Ran 1 test; OK.
Legacy make parity: 24/24 PASS. Checklist: 38 PASS, 0 FAIL.
codec-cases.txt lists the eight byte/key test methods and actual Surefire XML outcomes.

Independent expectations include literal -3.00 / -300.00, leading-zero IDs,
fixed transaction strings and timestamps, unsigned 7f<80, profile-specific literal key
strings, literal account postimage values, plus original fixture bytes and synthetic filler.
Byte preservation outside the changed account ranges is asserted separately from decoded
field comparison. Both profiles round-trip all five layouts, i01 and i06.

The first build found a test compile error passing Runnable to JUnit Executable;
fixed with action::run. Final clean verify passed. Existing Byte Buddy dynamic-agent and
Spring BeanPostProcessorChecker warnings are from the frozen pilot dependency stack;
Java 21 and PostgreSQL 15 are loaded. No environment workaround or shared config edit.
No incorrect skill command found. Proposed clarification: fixture input text can omit
trailing spaces; document its framing adapter explicitly, never relax the fixed-record codec.
Raw captured logs retain tool-generated whitespace.

## Raw evidence
- test-output.txt
- parity-driver-unittest.txt
- codec-cases.txt
- source-inventory.txt
- db-metadata-regression.txt
- e2e-run.log
- compare-stdout.txt / compare-report.md
- i01-sql.sql / i01-sql-output.txt
- make-parity.txt
- checklist.md
