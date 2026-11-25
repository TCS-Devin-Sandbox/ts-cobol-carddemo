# READCARD — Validation Report (Stage 5)

| Item | Value |
|---|---|
| Target | `pyspark_generation/pyspark_application.py` v2 + `config.json` (Stage 4 v2, D-01 fix) |
| Run | 2 (run 1 against v1 is in `history/run-1/`) |
| Specification | Stage 3 target design (approved, OQ-01..OQ-04 defaults) |
| Runtime | PySpark 3.5.1, OpenJDK 21, `local[*]`, pytest 8.3.3, pytest-bdd 8.1.0 |
| Overall | **PASS** — golden parity 2/2, 25/25 scenarios |

## Implementation validation
Every function in the processing spec exists and is wired S0 → S5 → E. Nothing in the code lacks a trace to a rule.

## Requirement validation
| Category | Implemented | Partial | Missing | Incorrect |
|---|---|---|---|---|
| Functional (BR) | 6 | 0 | 0 | 0 |
| Technical (TR) | 6 | 0 | 0 | 0 |
| Data validation (V-01, V-02) | 2 | 0 | 0 | 0 |
| Design decisions (OQ-01..04) | 4 | 0 | 0 | 0 |
| NFR: configurable, logged, atomic output, metrics | yes | — | — | — |

## Business rules
16 PASS — see `business_rule_validation.md`.

## Processing flow
| Expected | Implemented | Result |
|---|---|---|
| Read data | S1 `read_source` | PASS |
| Validate records | S2 `validate_records` | PASS |
| Apply transformations | S2 `apply_transformations` | PASS |
| Apply business rules | S3 `apply_business_rules` | PASS |
| Aggregations | none in design | N/A |
| Generate outputs | S4 `generate_output`, S5 `finalize` | PASS |

## Data validation
Mandatory/null/range/type/reference checks: N/A — the legacy program validates nothing, and the design passes fields through unchanged (A-01, A-02). Structural checks (length, unique key) are present and tested.

## Error handling
| Condition | Expected | Result |
|---|---|---|
| Input missing / unreadable | open-error lines, RC 12, abend 999 | PASS |
| Validation failure | read-error lines, no detail lines (OQ-02) | PASS |
| Transformation/processing exception | mapped to read error | Present (code review); not separately exercised |
| Output close failure | details, close-error lines, no END banner | PASS (v2; FAIL in run 1, D-01) |

## Defects
| ID | Severity | Finding | Evidence | Status |
|---|---|---|---|---|
| D-01 | Medium | On a close or rename failure, the listing contains `END OF EXECUTION OF PROGRAM CBACT02C` before `ERROR CLOSING CARDFILE`. The legacy program and design A-04 have no END banner on this path. | scenario "A failure while closing the listing…"; CBACT02C.cbl:83-85; `finalize` L237-245 | Write the END banner only after the close/rename succeeds, or have `handle_abend` drop it |

## Code quality findings
| ID | Type | Finding |
|---|---|---|
| Q-01 | Minor | If `generate_output` raises mid-write, its temp file `.readcard-*` stays in the sysout directory, because `emitted` is still unset |
| Q-02 | Minor | Unexpected exceptions during S2 reuse the `record_too_long` status (34); acceptable, but the status name is misleading |
| Q-03 | Info | Positives: modular functions, config-driven messages/codes/layout, structured logging, atomic rename, no hardcoded paths |

## Design errata (approved artifacts left unchanged)
- VR-12 example direction reversed: EBCDIC letters sort before digits.
- TR-003: the off-host display of the binary branch is ASCII-based (`'90'` → `9048`); on z/OS it would be `9240`.

## Overall assessment
Functionally equivalent to legacy on every golden case, on every success path and on every tested error path (open, read, close). D-01 is resolved. The remaining findings are minor (Q-01, Q-02) and the limitations are environmental (no live legacy re-run, no legacy error-path goldens).
