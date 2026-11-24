# READCARD — Test Coverage Report

## Business rule coverage
| Rule | Scenarios | Covered |
|---|---|---|
| BR-001 | 3 | yes |
| BR-002 | 3 (incl. volume order) | yes |
| BR-003 | 3 | yes |
| BR-004 | 1 | yes |
| BR-005 | 1 | yes |
| BR-006 | 4 (open, read ×2, close) | yes |
| TR-001 | 1 | yes |
| TR-002 | 4 (exact error listings) | yes |
| TR-003 | 1 outline × 6 examples + close | yes |
| TR-004 | 4 | yes |
| TR-005 | 1 | yes |
| TR-006 | 1 | yes |
| V-01 / V-02 | 1 / 1 | yes |
BRE coverage: 12/12 rules (100%) + V-01, V-02, OQ-02, OQ-04.

## Scenario coverage
| Metric | Value |
|---|---|
| Scenarios in `features/READCARD.feature` | 20 (1 outline) |
| Executed test cases | 25 |
| With step implementations | 25 / 25 (100%) |
| Passed | 24 |
| Failed | 1 (close-error listing, M-01) |

## Test types
Positive 12 · Negative 4 · Boundary 9 (incl. outline) · Volume 1 · Parity 2 · Reconciliation 1.

## Validation requirement coverage (testing_requirements.md)
VR-01..VR-11 covered and passing. VR-12 is covered with the corrected expectation (letters sort before digits under cp037); the approved text has the direction reversed.

## Coverage gaps
| Gap | Reason |
|---|---|
| Legacy error-path goldens | No fixture records CBACT02C open/read/close failures; error expectations come from the code (CBACT02C.cbl) and the approved design |
| Live legacy re-run | Estate image build blocked by Docker Hub rate limit (429) |
| Mid-file read error with partial output | Not reproducible with flat-file input; OQ-02 default chosen instead |
| Cluster / object-store run | Only `local[*]` was run |
