# READCARD — Target Design

Stage 3 (Target Design & Modernization Specification). Inputs: approved Stage 1 (discovery) and Stage 2 (technical summary, BR-001..BR-006, TR-001..TR-006, A-01..A-04). Target platform: **PySpark batch application** (per orchestrator Stage 4). No executable code in this document.

## 1. Solution Overview

| Item | Legacy | Target |
|---|---|---|
| Unit of work | JCL READCARD / STEP05 / CBACT02C | PySpark job `readcard`, step `STEP05` |
| Input | VSAM KSDS AWS.M2.CARDDEMO.CARDDATA.VSAM.KSDS (150 B, key 16@0) | Card master source configured in `config.json`: fixed-width 150-byte text (unload of the KSDS / CARDDATA.PS, ASCII), or a Parquet `card_master` table (optional) |
| Output | SYSOUT listing | Text file `sysout/READCARD.STEP05.txt` (same lines) + `rc.json` + `run_metrics.json` |
| Completion | RC 0 / abend U999 | `rc.json` step RC 0 / failure with abend code 999 (see §6) and non-zero process exit |
| Clear-text card number / CVV | printed | **printed unchanged** (Stage 1 reviewer decision) |

Equivalence target: SYSOUT and `rc.json` must match the golden fixtures in `fixtures/read-masters/*/expected` line-for-line using the existing comparator `tools/parity/compare.py --steps READCARD` (via `tools/parity/run_candidate.py`).

## 2. Functional Design

| capability | description | inputs | outputs | business_rules |
|---|---|---|---|---|
| CAP-01 Card master listing | List every card record, ascending card number, verbatim 150-byte image, framed by start/end banners | card master | listing lines | BR-001, BR-002, BR-003, BR-004, TR-006 |
| CAP-02 Run outcome signalling | RC 0 on success (incl. empty master); fail with abend 999 and diagnostics on I/O failure | card master availability / readability | rc.json, diagnostics in listing, process exit code | BR-005, BR-006, TR-001, TR-002, TR-003, TR-004 |
| CAP-03 Read-only access | Never modify the card master | card master | — | TR-005 |

Functional requirements:
- FR-01 Read all records of the configured card master (BR-001).
- FR-02 Order by card number using the legacy KSDS collation (BR-002; §5 note on EBCDIC).
- FR-03 Emit each record as exactly 150 characters, no trimming, no field reformatting (BR-003).
- FR-04 Emit banner lines with exact legacy text (BR-004).
- FR-05 Empty input → banners only, RC 0 (BR-005).
- FR-06 Unreadable/missing input → diagnostic lines (TR-002, TR-003), `ABENDING PROGRAM`, failure outcome (BR-006, TR-004).
- FR-07 No count/total lines in the listing (TR-006); counts go only to `run_metrics.json` for reconciliation.
- FR-08 Open the source read-only (TR-005).

## 3. Processing Architecture

Single-driver PySpark batch job, configuration-driven, five stages (details in `processing_specification.md`):

```
S0 Init & config ─▶ S1 Open/Ingest ─▶ S2 Parse & validate structure ─▶ S3 Order ─▶ S4 Emit listing ─▶ S5 Close/Outcome & metrics
                         │ fail                │ fail                                                │
                         └────────────── E: Error path (diagnostics + abend 999) ◀───────────────────┘
```

- Execution model: `spark-submit pyspark_application.py --config config.json [--chain read-masters --case <case> --input <dir> --out <dir> --clock <ts>]`; the second argument form matches the `run_candidate.py` candidate contract.
- Framework: PySpark DataFrame API; local mode for parity; cluster mode for volume.
- Component interactions: Spark reads source → DataFrame `card_master_raw` → `card_master` → ordered → collected/written by a single writer to preserve order (listing is one file).

## 4. Data Flow Design

| # | Object | Type | Produced by | Consumed by |
|---|---|---|---|---|
| 1 | Card master source (`AWS.M2.CARDDEMO.CARDDATA.PS.txt` or Parquet) | input | upstream load (CARDFILE equivalent) | S1 |
| 2 | `card_master_raw` (record_image string, source_line_no) | intermediate DF | S1 | S2 |
| 3 | `card_master` (typed fields + record_image) | intermediate DF | S2 | S3 |
| 4 | `card_master_ordered` | intermediate DF | S3 | S4 |
| 5 | `sysout/READCARD.STEP05.txt` | output | S4/S5/E | operators, parity |
| 6 | `rc.json` | output | S5/E | scheduler, parity |
| 7 | `run_metrics.json` | output (audit) | S5/E | reconciliation |

Transformation points: S1 padding to 150 (input normalisation), S2 field extraction (no value change), S3 ordering. No business transformation of values (BR-003).

## 5. Business Rule Mapping

| rule_id | legacy_rule | target_implementation | validation_required |
|---|---|---|---|
| BR-001 | All records listed, none filtered | No filter/dedupe/limit in pipeline; output detail count = input count | true |
| BR-002 | Ascending CARD-NUM (KSDS key order) | `orderBy` on collation key of `card_num`: EBCDIC (cp037) byte encoding of `card_num` when `collation=ebcdic` (default), raw bytes when `collation=binary` | true |
| BR-003 | Verbatim 150-byte record | Output line = `record_image` (rpad to 150 with spaces); never rebuilt from typed fields; CVV and card number unmasked | true |
| BR-004 | Start/end banners | Literal `START OF EXECUTION OF PROGRAM CBACT02C` first; `END OF EXECUTION OF PROGRAM CBACT02C` last on success only | true |
| BR-005 | Empty master = success | 0 records → banners only, RC 0 | true |
| BR-006 | I/O error → abend | Source missing/unreadable/invalid → error path E, outcome abend 999 | true |
| TR-001 | Status '10' = EOF | End of DataFrame iteration = EOF; no explicit status | true |
| TR-002 | Diagnostic message text | Exact literals: `ERROR OPENING CARDFILE` (S1), `ERROR READING CARDFILE` (S2), `ERROR CLOSING CARDFILE` (S5 write/close failure), `ABENDING PROGRAM` | true |
| TR-003 | File status formatting | `FILE STATUS IS: NNNN` + 4-char code built by the TR-003 algorithm from a mapped legacy status (open missing → `35`, read structural error → `34`… see `processing_specification.md` §E) | true |
| TR-004 | CEE3ABD 999 | Record `abend_code: 999` in metrics; step RC per `abend_rc` config (default 12 = off-host harness stub `tools/stubs/CEE3ABD.cbl`); append `CEE3ABD: USER ABEND U+000000999` line when `emulate_harness_abend_line=true` | true |
| TR-005 | Read-only, shared | Source opened read-only; no write path to the source location | true |
| TR-006 | No counts in listing | Counts only in `run_metrics.json` | true |

Ambiguities carried (no rule implemented): A-01, A-02 (fields passed through untouched), A-03 (no purpose added), A-04 (close-error path omits end banner — reproduced).

## 6. Error Handling Strategy

| error_condition | handling_strategy | expected_behavior |
|---|---|---|
| Source path missing / unreadable | Catch at S1 | Lines: START banner, `ERROR OPENING CARDFILE`, `FILE STATUS IS: NNNN0035`, `ABENDING PROGRAM`; abend 999; no END banner |
| Record longer than 150 bytes | Detect at S2 (structural), before any detail line is emitted | Target emits START banner, `ERROR READING CARDFILE`, `FILE STATUS IS: NNNN0034`, `ABENDING PROGRAM`; abend 999 (OQ-02) |
| Duplicate card_num in source | Detect at S2 when `enforce_unique_key=true` | Same as structural read error (KSDS cannot hold duplicate keys; OQ-03) |
| Output write/close failure | Catch at S5 | `ERROR CLOSING CARDFILE` semantics; abend 999 |
| Empty source | Not an error | Banners only, RC 0 |

Record rejection strategy: none — legacy has no per-record rejection; any structural failure fails the step. Logging: Spark/driver log at INFO for stage boundaries, counts and durations; never log record contents beyond the listing itself. Recovery: rerun from start (read-only, idempotent).

## 7. Non-Functional Requirements

| Area | Requirement |
|---|---|
| Performance | Single-file ordered output; volume target ≥ 1 M cards in local[*] within normal batch window; global sort is O(n log n) |
| Data volume | No collect of full dataset to driver beyond ordered streaming write (`toLocalIterator` or single-partition write) |
| Restartability | Full rerun; no checkpoint needed (read-only) |
| Fault tolerance | Any stage exception → error path E; output file written atomically (temp then rename) |
| Modularity | One function per stage; rules referenced by ID in code comments/docstrings |
| Configuration | `config.json`: paths, encoding, lrecl, collation, abend_rc, emulate_harness_abend_line, enforce_unique_key, pad_short_records |
| Traceability | Every rule ID mapped to a function; `run_metrics.json` records rule-relevant counts |
| Reconciliation | input count, output detail count, key min/max, SHA-256 of ordered record images |
| Security | Output contains PAN and CVV in clear by decision; output path access must be restricted (operational control, not code) |

## 8. Migration Considerations

Assumptions:
- AS-01 Target input is an ASCII fixed-width unload of the KSDS (same layout as `AWS.M2.CARDDEMO.CARDDATA.PS`); trailing spaces may be trimmed and are restored by padding (fixtures trim them).
- AS-02 Golden fixtures r01/r02 are the acceptance oracle.

Constraints: AGENTS.md — no edits to `app/`, `fixtures/*/expected` or copybooks; new code lives in a new directory.

Open questions (require decision; defaults chosen are flagged):
| ID | Question | Default in design |
|---|---|---|
| OQ-01 | Abend representation: harness RC 12 vs mainframe U999 | `abend_rc=12`, `abend_code=999` recorded |
| OQ-02 | Partial listing before a read error: legacy prints records read so far; target fails before emitting details | Fail-before-emit (no legacy golden for this path) |
| OQ-03 | Enforce unique card numbers on non-KSDS input | `enforce_unique_key=true` |
| OQ-04 | Input source of record in production (unload file vs table) | fixed-width text; Parquet optional |

Dependencies: upstream card master load; PySpark 3.5 + Java 17 runtime. Risks: EBCDIC vs ASCII collation for non-numeric keys (mitigated by `collation=ebcdic`); clear-text PAN/CVV output (accepted by reviewer); error paths not covered by goldens.
