# READCARD — Business Rule Validation

Evidence: `pytest_output.txt`, `junit_results.xml` (pytest-bdd 8.1.0, PySpark 3.5.1), `tools/parity/run_candidate.py` (report copied to `parity_report.md`).
Run 2, against Stage 4 v2. Run 1 (Stage 4 v1) is in `history/run-1/`. The validation stage did not modify the generated code or the approved specs.

| Rule ID | Rule Description | Implementation | Validation Result | Scenarios | Comments |
|---|---|---|---|---|---|
| BR-001 | List all records | Found (`apply_business_rules`, `generate_output`) | PASS | r01 parity; single record; 20 000-record volume | counts = input |
| BR-002 | Card-number order (KSDS) | Found (`ebcdic_translation`, `apply_business_rules`) | PASS | unordered; EBCDIC mixed keys; volume order | letters before digits (cp037); see VR-12 erratum |
| BR-003 | Verbatim 150-byte image, clear PAN/CVV | Found (`apply_transformations`, `generate_output`) | PASS | trimmed; exact 150; field-level vs legacy | all 7 CVACT02Y fields equal |
| BR-004 | Start/end banners | Found (`run`, `finalize`) | PASS | banners | |
| BR-005 | Empty input → RC 0 | Found (`run`, `write_outcome`) | PASS | r02 parity | |
| BR-006 | I/O error → abend 999 | Found (`read_source`, `validate_records`, `finalize`, `handle_abend`) | PASS | missing; over-length; duplicate; close failure | Run 1 failed on the close path (D-01: END banner printed before `ERROR CLOSING CARDFILE`; legacy closes at CBACT02C.cbl:83 before the END display at :85). Fixed in Stage 4 v2 and passes in run 2. |
| TR-001 | Status 10 = EOF | Found (iterator end) | PASS | EOF scenario | |
| TR-002 | Exact diagnostics | Found (`error_lines`, config.messages) | PASS | open/read/close message lines exact | close-path ordering covered under BR-006 |
| TR-003 | File status format | Found (`format_file_status`) | PASS | 6 outline examples | `'90'` → `9048` off-host (ASCII); would be `9240` on z/OS EBCDIC |
| TR-004 | CEE3ABD 999 / RC 12 (OQ-01) | Found (`handle_abend`) | PASS | missing-input metrics, rc.json | |
| TR-005 | Read-only | Found | PASS | hash before = after | |
| TR-006 | No counts in listing | Found | PASS | line count = n + 2 | |
| V-01 | lrecl ≤ 150 | Found | PASS | over-length | |
| V-02 | Unique key (OQ-03) | Found | PASS | duplicate keys | |
| OQ-02 | No detail lines before read error | Found | PASS | over-length, duplicate | |
| OQ-04 | Parquet option | Found (`read_parquet`) | PASS | Parquet = golden SYSOUT | |

Totals: 16 items — 16 PASS, 0 PARTIAL, 0 NOT IMPLEMENTED.
