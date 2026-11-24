# READCARD — Validation Report (Stage 5)

| Item | Value |
|---|---|
| Target | `pyspark_generation/pyspark_application.py` + `config.json` (approved Stage 4) |
| Specification | Stage 3 target design (approved, OQ-01..OQ-04 defaults) |
| Runtime | PySpark 3.5.1, OpenJDK 21, `local[*]`, pytest 8.3.3, pytest-bdd 8.1.0 |
| Overall | **PASS WITH ONE DEFECT** — golden parity passes; 1 of 25 scenarios fails (close-error path) |

## Implementation validation
Every function in the processing spec exists and is wired S0 → S5 → E. Nothing in the code lacks a trace to a rule.

## Requirement validation
| Category | Implemented | Partial | Missing | Incorrect |
|---|---|---|---|---|
| Functional (BR) | 5 | 1 (BR-006) | 0 | 0 |
| Technical (TR) | 6 | 0 | 0 | 0 |
| Data validation (V-01, V-02) | 2 | 0 | 0 | 0 |
| Design decisions (OQ-01..04) | 4 | 0 | 0 | 0 |
| NFR: configurable, logged, atomic output, metrics | yes | — | — | — |

## Business rules
15 PASS, 1 PARTIAL — see `business_rule_validation.md`.

## Processing flow
| Expected | Implemented | Result |
|---|---|---|
| Read data | S1 `read_source` | PASS |
| Validate records | S2 `validate_records` | PASS |
| Apply transformations | S2 `apply_transformations` | PASS |
| Apply business rules | S3 `apply_business_rules` | PASS |
| Aggregations | none in design | N/A |
| Generate outputs | S4 `generate_output`, S5 `finalize` | PARTIAL (S5 failure order, D-01) |

## Data validation
Mandatory/null/range/type/reference checks: N/A — the legacy program validates nothing, and the design passes fields through unchanged (A-01, A-02). Structural checks (length, unique key) are present and tested.

## Error handling
| Condition | Expected | Result |
|---|---|---|
| Input missing / unreadable | open-error lines, RC 12, abend 999 | PASS |
| Validation failure | read-error lines, no detail lines (OQ-02) | PASS |
| Transformation/processing exception | mapped to read error | Present (code review); not separately exercised |
| Output close failure | details, close-error lines, no END banner | **FAIL** (D-01) |

## Defects
| ID | Severity | Finding | Evidence | Fix (Stage 4 rework) |
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
Functionally equivalent to legacy on every golden case and every success and input-error path. D-01 is the one deviation from approved behaviour, on the close-failure path. Recommendation: fix D-01 in a targeted Stage 4 rework and re-run this suite; otherwise accept it as a documented risk.
