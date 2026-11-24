# READCARD — Processing Specification

## Stage-by-stage design

### S0 Init & config
- Purpose: load `config.json`, resolve CLI overrides (`--input`, `--out`, `--clock`, `--chain`, `--case`), start SparkSession (local by default).
- Inputs: config.json, CLI. Outputs: run context. Rules: none.
- Writes START banner to the listing buffer (BR-004).

### S1 Open / Ingest (≈ 0000-CARDFILE-OPEN)
- Input: `<input>/AWS.M2.CARDDEMO.CARDDATA.PS.txt` (configurable `input_dataset`), encoding `latin-1`.
- Missing/unreadable → E with status `35` and message `ERROR OPENING CARDFILE` (TR-002, BR-006).
- Output: `card_master_raw` (SCH-03). Empty lines at end of file are ignored only if they are a trailing newline artefact; blank interior lines are records of 150 spaces (passed through).
- Rule TR-005: read-only.

### S2 Parse & validate structure (≈ READ … INTO)
- `record_image = rpad(raw_line, lrecl=150, ' ')` when `pad_short_records=true`.
- Validation (structural only; no field validation exists in legacy):
  - V-01 `raw_length > 150` → E status `34`, `ERROR READING CARDFILE`.
  - V-02 duplicate `card_num` when `enforce_unique_key=true` → E with status `22` (printed `FILE STATUS IS: NNNN0022`), `ERROR READING CARDFILE` (OQ-03).
- Field extraction per `source_to_target_mapping.md` (no value change).
- Output: `card_master` (SCH-04).

### S3 Order (≈ KSDS sequential key order)
- `sort_key` per collation; `orderBy(sort_key)` ascending (BR-002). No filtering (BR-001).

### S4 Emit listing (≈ DISPLAY CARD-RECORD)
- For each ordered row write `record_image` as one line (BR-003). No headers, counts or page breaks (TR-006).
- Single ordered writer (driver iterates `toLocalIterator()` over the sorted DataFrame) to a temp file.

### S5 Close / Outcome (≈ 9000-CARDFILE-CLOSE + GOBACK)
- Append END banner (BR-004), atomically rename listing to `<out>/sysout/READCARD.STEP05.txt`.
- Write `rc.json` = `{"READCARD":{"maxrc":0,"steps":{"STEP05":0}}}` (BR-005).
- Write `run_metrics.json` (SCH-07). Exit 0.
- Write/rename failure → E with `ERROR CLOSING CARDFILE`, status `90`.

### E Error path (≈ 9910 + 9999)
Lines appended after whatever has been emitted (START banner always; S4 output only if failure is in S5):
1. stage message (`ERROR OPENING|READING|CLOSING CARDFILE`)
2. `FILE STATUS IS: NNNN` + IO-STATUS-04 (TR-003)
3. `ABENDING PROGRAM`
4. if `emulate_harness_abend_line`: `CEE3ABD: USER ABEND U+000000999`
Then `rc.json` STEP05 = maxrc = `abend_rc` (default 12), `run_metrics.status=ABEND`, `abend_code=999`, process exit code = `abend_rc`. No END banner.

## Validation rules
| ID | Rule | Source |
|---|---|---|
| V-01 | record length ≤ 150 | lrecl 150 (CARDFILE.jcl RECORDSIZE) |
| V-02 | unique card_num (configurable) | KSDS unique primary key |
No field-level content validation (legacy has none).

## Transformation rules
| ID | Rule | Ref |
|---|---|---|
| T-01 | rpad to 150 with spaces | BR-003, AS-01 |
| T-02 | identity projection of fields | BR-003 |
| T-03 | TR-003 status formatting: if status is 2 numeric chars and first ≠ '9' → `'00' + status`; else → `status[0] + format(ord(status[1]), '03d')` | TR-003 |

## Calculation rules
None (no calculations in CBACT02C).

## Output generation rules
| ID | Rule | Ref |
|---|---|---|
| O-01 | Line 1 = START banner | BR-004 |
| O-02 | Detail lines in ascending sort_key, one per input record | BR-001, BR-002, BR-003 |
| O-03 | Last line = END banner on success only | BR-004, A-04 |
| O-04 | rc.json shape SCH-06 | BR-005, BR-006 |
| O-05 | No count/trailer lines in listing | TR-006 |
| O-06 | Output encoding latin-1, LF terminators, no trailing-space trimming | BR-003 |
