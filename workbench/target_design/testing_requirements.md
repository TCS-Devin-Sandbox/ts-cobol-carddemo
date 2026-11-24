# READCARD — Testing Requirements

## Validation requirements
| ID | Requirement | Rules |
|---|---|---|
| VR-01 | Listing line count = input count + 2 on success | BR-001, BR-004 |
| VR-02 | Detail lines strictly ascending by sort_key | BR-002 |
| VR-03 | Each detail line length 150 and equal to padded input image | BR-003 |
| VR-04 | First/last lines are exact banners | BR-004 |
| VR-05 | Empty input → 2 lines, RC 0 | BR-005 |
| VR-06 | Missing input → open-error lines, abend_code 999, RC = abend_rc | BR-006, TR-002, TR-003, TR-004 |
| VR-07 | Over-length record → read-error lines | BR-006, V-01 |
| VR-08 | Duplicate key with enforce_unique_key → read-error lines | V-02 |
| VR-09 | Status formatting cases: '35'→`0035`, '9'+chr(4)→`9004`, '34'→`0034` | TR-003 |
| VR-10 | Source file unchanged after run (hash before = after) | TR-005 |
| VR-11 | No count/trailer lines | TR-006 |
| VR-12 | EBCDIC collation: keys with letters sort after digits (e.g. `A…` after `9…` under cp037) | BR-002 |

## Unit testing
- Transformation: T-01 padding, T-02 identity, T-03 formatting.
- Rule validation: VR-02, VR-03, VR-09, VR-12 on in-memory DataFrames.

## Integration testing
- End-to-end CLI run per fixture case via the `run_candidate.py` contract; verify files `sysout/READCARD.STEP05.txt`, `rc.json`, `run_metrics.json`.

## Parallel run validation (legacy vs target)
- Oracle: `fixtures/read-masters/{r01-populated,r02-empty}/expected` (produced by running real READCARD JCL off-host under GnuCOBOL).
- Comparator: `tools/parity/compare.py read-masters <case> --actual <out> --steps READCARD` — SYSOUT line-by-line + rc.json scoped.

## Data reconciliation testing
| Check | Method |
|---|---|
| Record counts | input_records = output_detail_lines = legacy detail lines |
| Field-level matching | decode each listing line with CVACT02Y offsets, compare field by field to legacy SYSOUT lines |
| Business totals | none exist; use key min/max and SHA-256 of ordered record images as control totals |

## Test scenarios (BDD-ready)
| ID | Scenario | Expected |
|---|---|---|
| TS-01 | Populated master (r01) | parity pass |
| TS-02 | Empty master (r02) | parity pass |
| TS-03 | Unordered input | output sorted |
| TS-04 | Trimmed input lines | padded to 150 |
| TS-05 | Missing input | open-error, abend |
| TS-06 | Over-length record | read-error, abend |
| TS-07 | Duplicate key | read-error, abend |
| TS-08 | Clear-text PAN/CVV | unchanged in output |

## Success criteria
- TS-01, TS-02 pass `compare.py` with zero differences.
- All VR-01..VR-12 pass.
- Reconciliation counts and hashes match legacy for every golden case.
