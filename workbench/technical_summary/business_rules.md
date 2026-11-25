# READCARD — Business Rules Catalogue

Inputs: Stage 1 discovery outputs (approved 2026-11-24) and `app/cbl/CBACT02C.cbl`, `app/cpy/CVACT02Y.cpy`, `app/jcl/READCARD.jcl`.
Reviewer decision carried forward: output reproduces card number and CVV in clear, as legacy (Stage 1, R-01).

Legend: **BR** = business-visible rule (affects listing content or job outcome). **TR** = technical rule (runtime/I-O mechanics that must still be preserved for parity).

## Rule catalogue

| Rule ID | Rule Name | Type | Rule Description | Source Reference | Business Impact |
|---|---|---|---|---|---|
| BR-001 | Complete card listing | Filtering (none) | Every record in the card master is listed; no record is skipped, filtered or de-duplicated. | CBACT02C.cbl:74-81 | Listing is a full snapshot of the card master. |
| BR-002 | Card-number order | Sorting | Records are listed in ascending primary-key order (CARD-NUM, 16 bytes, key offset 0), from KSDS sequential access. | CBACT02C.cbl:29-32; CARDFILE.jcl KEYS(16 0) | Consumers can rely on ascending card-number order. |
| BR-003 | Verbatim record image | Transformation (none) | Each listed line is the full 150-byte CARD-RECORD, unchanged — all fields including CARD-CVV-CD and the 59-byte FILLER. | CBACT02C.cbl:78, 93; CVACT02Y.cpy | Exact-field output; clear-text card number/CVV preserved per reviewer decision. |
| BR-004 | Start/end banners | Reporting | The listing begins with `START OF EXECUTION OF PROGRAM CBACT02C` and, on normal end, ends with `END OF EXECUTION OF PROGRAM CBACT02C`. | CBACT02C.cbl:71, 85 | Operational marker of a complete run. |
| BR-005 | Empty master is success | Validation | A card master with zero records produces only the two banners and ends with RC 0 (no error). | CBACT02C.cbl:98-99, 106-107; fixture r02-empty | Empty file is not a failure condition. |
| BR-006 | Fail on I/O error | Validation / Routing | Open status ≠ '00', read status ∉ {'00','10'} or close status ≠ '00' stops the job abnormally (abend code 999) after a diagnostic message; no partial-success RC. | CBACT02C.cbl:94-114, 121-133, 139-151, 154-158 | Downstream must treat the job as failed; partial listing may already be on SYSOUT. |
| TR-001 | EOF detection | Validation | File status '10' on READ = end of file (APPL-RESULT 16 → END-OF-FILE 'Y'). | CBACT02C.cbl:98-99, 105-107 | Normal loop termination. |
| TR-002 | Diagnostic message text | Reporting | Error messages are exactly `ERROR OPENING CARDFILE` / `ERROR READING CARDFILE` / `ERROR CLOSING CARDFILE`, then `FILE STATUS IS: NNNN<4 chars>`, then `ABENDING PROGRAM`. | CBACT02C.cbl:110, 129, 147, 155, 168, 172 | Operator diagnostics; required for SYSOUT parity on failure paths. |
| TR-003 | File status formatting | Transformation | If IO-STATUS is numeric and stat1 ≠ '9': output `00` + 2-char status (e.g. `0035`). Else: stat1 + 3-digit decimal of the binary value of stat2 (e.g. `9` + `035`). | CBACT02C.cbl:161-174 | Diagnostic accuracy for VSAM/implementor-specific statuses. |
| TR-004 | Abend parameters | Routing | Abend via LE `CEE3ABD` with ABCODE 999, TIMING 0 (immediate, no dump cleanup). | CBACT02C.cbl:156-158 | Job ends abnormally (U999); scheduler sees abend, not an RC. |
| TR-005 | Read-only, shared access | Technical | File opened INPUT, DD DISP=SHR; the program never writes, updates or deletes. | CBACT02C.cbl:120; READCARD.jcl:25-26 | Safe to run alongside CICS online access; no data change. |
| TR-006 | No record count / control totals | Aggregation (none) | The program prints no count, totals or trailer. | CBACT02C.cbl (no counters) | Reconciliation must count listed lines externally. |

## Rule categorization

| Category | Rules |
|---|---|
| Validation | BR-005, BR-006, TR-001 |
| Transformation | BR-003 (identity), TR-003 |
| Filtering | BR-001 (none applied) |
| Calculation | none observed |
| Routing | BR-006, TR-004 (normal vs abend path) |
| Sorting | BR-002 |
| Reporting | BR-004, TR-002 |
| Aggregation | TR-006 (none) |

## Ambiguous / inferred items (flagged, not rules)

| ID | Item | Why flagged |
|---|---|---|
| A-01 | CARD-ACTIVE-STATUS values ('Y' observed) | No logic in CBACT02C interprets it; meaning ('Y' = active) is inferred from the field name and fixture data only. |
| A-02 | CARD-EXPIRAION-DATE format YYYY-MM-DD | PIC X(10); format observed in fixtures, not enforced by code. |
| A-03 | Business purpose beyond "read and print" | Source states only "Read and print card data file"; any audit/report use is not evidenced. |
| A-04 | Close-error path omits end banner | Observable from code order (9000 abends before :85); not covered by a fixture. |
