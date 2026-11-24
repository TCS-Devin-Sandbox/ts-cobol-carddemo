# READCARD — Reconciliation Report

Oracle: golden files under `fixtures/read-masters/<case>/expected`, recorded from a real off-host run of the READCARD JCL (GnuCOBOL).
Comparator: `tools/parity/run_candidate.py --chain read-masters --steps READCARD`, which wraps `compare.py` (SYSOUT + rc.json); see `parity_report.md`.
A live legacy re-run was not possible: building the estate failed because Docker Hub rate-limited the image pulls (HTTP 429 for `ubuntu:22.04` and `postgres:15-alpine`).

## Parity
| Case | SYSOUT | rc.json | Differences |
|---|---|---|---|
| r01-populated | PASS | PASS (READCARD maxrc 0, STEP05 0) | 0 |
| r02-empty | PASS | PASS | 0 |

## Record counts
| Case | Legacy detail lines | input_records | output_detail_lines | rejected | Result |
|---|---|---|---|---|---|
| r01-populated | 2 | 2 | 2 | 0 | PASS |
| r02-empty | 0 | 0 | 0 | 0 | PASS |
| volume (synthetic) | n/a | 20 000 | 20 000 | 0 | PASS |

## Control totals (r01-populated)
| Control | Legacy (from golden SYSOUT) | PySpark (run_metrics.json) | Result |
|---|---|---|---|
| min card_num | 4000000000001001 | 4000000000001001 | PASS |
| max card_num | 4000000000001002 | 4000000000001002 | PASS |
| SHA-256 of detail lines | 6966bdf3…caa8 | 6966bdf331ea5795eaaf387c1395140102002d0d017812125e76f814ef17caa8 | PASS |

## Business totals
Not applicable: CBACT02C computes no amounts or aggregates (TR-006). Field-level comparison of all 7 CVACT02Y fields passed instead.

## Sorting / schema
Sorting: matches golden order; ascending EBCDIC order on synthetic sets. Schema: every line is 150 characters; banners are exact.

## Mismatch analysis
| # | Area | Expected (legacy/design) | Actual | Severity |
|---|---|---|---|---|
| M-01 | Close-error SYSOUT | START, details, ERROR CLOSING CARDFILE, status, ABENDING (no END banner; A-04) | END banner printed before ERROR CLOSING CARDFILE | Medium — only reachable if the final write/rename fails; RC 12 and abend 999 are still correct |

No mismatches on any golden case.
