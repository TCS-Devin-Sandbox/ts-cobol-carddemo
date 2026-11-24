# READCARD — Business Rule Validation

Evidence: `pytest_output.txt`, `junit_results.xml` (pytest-bdd 8.1.0, PySpark 3.5.1), `tools/parity/run_candidate.py` (report copied to `parity_report.md`).
Generated code and approved specs were not modified.

| Rule ID | Rule Description | Implementation | Validation Result | Scenarios | Comments |
|---|---|---|---|---|---|
| BR-001 | List all records | Found (`apply_business_rules`, `generate_output`) | PASS | r01 parity; single record; 20 000-record volume | counts = input |
| BR-002 | Card-number order (KSDS) | Found (`ebcdic_translation`, `apply_business_rules`) | PASS | unordered; EBCDIC mixed keys; volume order | letters before digits (cp037); see VR-12 erratum |
| BR-003 | Verbatim 150-byte image, clear PAN/CVV | Found (`apply_transformations`, `generate_output`) | PASS | trimmed; exact 150; field-level vs legacy | all 7 CVACT02Y fields equal |
| BR-004 | Start/end banners | Found (`run`, `finalize`) | PASS | banners | |
| BR-005 | Empty input → RC 0 | Found (`run`, `write_outcome`) | PASS | r02 parity | |
| BR-006 | I/O error → abend 999 | Found (`read_source`, `validate_records`, `finalize`, `handle_abend`) | **PARTIAL** | missing (PASS); over-length (PASS); duplicate (PASS); close failure (**FAIL**) | Close path emits `END OF EXECUTION…` before `ERROR CLOSING CARDFILE`. Legacy performs 9000-CARDFILE-CLOSE (CBACT02C.cbl:83) before the END display (:85), so the banner must be absent (design A-04 "reproduced"). Cause: `finalize` appends the END banner to the temp listing before `os.replace`; `handle_abend` replays that file. |
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

Totals: 16 items — 15 PASS, 1 PARTIAL (BR-006), 0 NOT IMPLEMENTED.
