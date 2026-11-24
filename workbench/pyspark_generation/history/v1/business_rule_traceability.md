# READCARD — Business Rule Traceability (Implementation)

Code: `pyspark_application.py` (line numbers at generation). Config: `config.json`.

| Rule ID | Rule Description | Target Spec Reference | Code Reference | Validation Approach |
|---|---|---|---|---|
| BR-001 | All records listed, no filter | target_design §5; processing_spec S3 | `apply_business_rules` (L198) — no filter; `generate_output` (L212) writes every row | count input = detail lines (run_metrics) |
| BR-002 | Ascending card number, KSDS collation | §5; S3; mapping `sort_key` | `ebcdic_translation` (L191), `apply_business_rules` (L198) `rules.collation` | order check, EBCDIC mixed-key test |
| BR-003 | Verbatim 150-byte image, PAN/CVV clear | §5; mapping record_image; T-01/T-02 | `apply_transformations` (L180) rpad; `generate_output` writes `record_image` | line length 150, byte equality with golden |
| BR-004 | Start/end banners | O-01, O-03 | `run` (L297) start; `finalize` (L237) end; `config.messages` | first/last line equality |
| BR-005 | Empty master = RC 0 | O-04 | `run` → `write_outcome(ctx, 0, "SUCCESS")` (L248) | r02-empty parity |
| BR-006 | I/O failure → abend | §6; E | `CardFileError` (L40), `read_source` (L115), `validate_records` (L154), `finalize`, `handle_abend` (L275) | missing / over-length / duplicate tests |
| TR-001 | Status '10' = EOF | §5 | end of `toLocalIterator` in `generate_output` | implicit (all paths) |
| TR-002 | Diagnostic text | §5 TR-002; E | `error_lines` (L267), `config.messages` | exact line equality |
| TR-003 | File status format | T-03 | `format_file_status` (L259); `config.legacy_file_status` | unit cases 35→0035, 9+x'04'→9004 |
| TR-004 | CEE3ABD 999 | §5; OQ-01 | `handle_abend`: `rules.abend_rc`=12, `rules.abend_code`=999, harness line | rc.json, exit code, metrics |
| TR-005 | Read-only | §5 | `read_source`/`read_fixed_width` (L134) read only; no write to input path | input hash before/after |
| TR-006 | No counts in listing | O-05 | `generate_output` writes only banners+records; counts → `run_metrics.json` | no extra lines |
| V-01 | lrecl ≤ 150 | processing_spec S2 | `validate_records` | over-length test |
| V-02 | unique card_num (OQ-03) | S2 | `validate_records` `rules.enforce_unique_key` | duplicate test |
| OQ-02 | fail before emitting details | §6 | validation runs before `generate_output` | read-error listing has no detail lines |
| OQ-04 | fixed-width default, Parquet optional | §1 | `read_source` → `read_fixed_width` / `read_parquet` (L141) | fixed-width via fixtures |

No rules added beyond the approved design. Ambiguities A-01/A-02 are passed through untouched (no parse/decoding of status or date).

## Generation-time verification (smoke)
| Run | Result |
|---|---|
| r01-populated | SYSOUT identical to golden (`diff`), rc.json `{READCARD: maxrc 0, STEP05 0}` |
| r02-empty | SYSOUT identical, RC 0 |
| missing input | open-error lines, `FILE STATUS IS: NNNN0035`, RC/exit 12 |
| unordered mixed keys `A…`, `4…` | `A…` first (EBCDIC letters 0xC1 < digits 0xF4), lines 150 chars |

## Design erratum found during implementation
`testing_requirements.md` VR-12 example says letters sort *after* digits under cp037. That is reversed: in EBCDIC letters (0xC1–0xE9) sort **before** digits (0xF0–0xF9). The code implements the design rule (BR-002, cp037 byte order) correctly; only the VR-12 example text is wrong. The approved artifact is left unchanged; Stage 5 tests use the correct expectation.
