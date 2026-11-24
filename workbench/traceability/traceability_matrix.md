# READCARD — End-to-End Traceability Matrix

Chain: Legacy source → BRE rule (Stage 2) → Target design (Stage 3) → PySpark v2 (Stage 4) → BDD scenario (Stage 5) → Result (run 2).

| Rule | Legacy source | Target design | PySpark v2 | BDD scenario(s) (`validation/features/READCARD.feature`) | Result |
|---|---|---|---|---|---|
| BR-001 List all records | CBACT02C.cbl 1000-CARDFILE-GET-NEXT loop; READCARD.jcl STEP05 | target_design §5; processing_spec S3/S4 | `apply_business_rules`, `generate_output` | Every card in populated golden master; Single-card master; Large card master | PASS |
| BR-002 Card-number order | FD CARDFILE KSDS, RECORD KEY CARD-NUM; CARDFILE.jcl | §5; mapping `sort_key`; VR-02/VR-12 | `ebcdic_translation`, `apply_business_rules` | Unordered input; EBCDIC collation; Large card master | PASS |
| BR-003 Verbatim 150-byte image (clear PAN/CVV) | DISPLAY CARD-RECORD; CVACT02Y | mapping T-01/T-02; Stage 1 decision | `apply_transformations`, `generate_output` | Trimmed records; Full 150-char record; Clear-text PAN/CVV | PASS |
| BR-004 Banners | CBACT02C.cbl start DISPLAY, :85 END DISPLAY | O-01, O-03 | `run`, `finalize` | Framed by banners | PASS |
| BR-005 Empty input → RC 0 | EOF on first read; fixture r02-empty | O-04 | `run`, `write_outcome` | Empty card master | PASS |
| BR-006 I/O error → abend 999 | 0000-CARDFILE-OPEN, 1000-…-GET-NEXT, 9000-CARDFILE-CLOSE, 9999-ABEND-PROGRAM | §6; error path E; OQ-01 | `read_source`, `validate_records`, `finalize`, `handle_abend` (v2) | Missing master; Over-length; Duplicate keys; Close failure | PASS (run 1: close path FAIL, D-01, fixed in v2) |
| TR-001 Status 10 = EOF | APPL-EOF handling | §5 | iterator end in `generate_output` | End of file ends loop normally | PASS |
| TR-002 Diagnostic text | DISPLAY 'ERROR OPENING/READING/CLOSING CARDFILE', 'ABENDING PROGRAM' | §5; E | `error_lines`, config.messages | Missing / Over-length / Duplicate / Close failure | PASS |
| TR-003 File status format | 9910-DISPLAY-IO-STATUS (:161-173) | T-03 | `format_file_status` | File status outline (6 examples) | PASS |
| TR-004 Abend parameters | CALL 'CEE3ABD' ABCODE 999; stub RC 12 | OQ-01 | `handle_abend`, config.rules | Missing master (metrics), all error scenarios (RC 12) | PASS |
| TR-005 Read-only | OPEN INPUT; DISP=SHR | §5 | `read_source`, `read_fixed_width` | Card master opened read-only | PASS |
| TR-006 No counts/trailer | no count DISPLAY | O-05 | `generate_output` | No count or trailer lines | PASS |
| V-01 lrecl ≤ 150 | FD record 150 | processing_spec S2 | `validate_records` | Over-length record | PASS |
| V-02 Unique key | KSDS unique key | S2; OQ-03 | `validate_records` | Duplicate card numbers | PASS |
| OQ-02 No details before read error | design decision | §6 | validation before S4 | Over-length; Duplicate | PASS |
| OQ-04 Fixed-width default, Parquet option | design decision | §1 | `read_source`, `read_parquet` | Parquet card master | PASS |
| A-04 Close-error omits END banner | CBACT02C.cbl:83 before :85 | target_design §5 (reproduced) | `handle_abend` v2 | Close failure | PASS |

## Field mappings
All 7 CVACT02Y fields + record_image, sort_key, source_line_no: `target_design/source_to_target_mapping.md` → `config.json` layout → `apply_transformations` → scenario "Clear-text PAN/CVV" (field-level vs legacy) → PASS. Detail: `validation/mapping_validation.md`.

## Artifact lineage
| Stage | Artifacts | Approval |
|---|---|---|
| 1 Discovery | discovery/* | approvals/discovery_review_report.md |
| 2 BRE | technical_summary/* | approvals/bre_review_report.md |
| 3 Target design | target_design/* | approvals/target_design_review_report.md |
| 4 PySpark (v1 → v2) | pyspark_generation/*, history/v1/ | approvals/implementation_review_report.md |
| 5 Validation (run 1 → run 2) | validation/*, history/run-1/ | approvals/validation_review_report.md |
| Audit | audit/approval_audit.json | — |
