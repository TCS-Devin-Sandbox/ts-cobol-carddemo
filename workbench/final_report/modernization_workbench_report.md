# READCARD Modernization Workbench Report

Job `READCARD.jcl` · program `CBACT02C` · repository `TCS-Devin-Sandbox/ts-cobol-carddemo` · target PySpark 3.5 · 2026-11-24 · requested and approved by aruna.vs

## 1. Executive Summary
READCARD has been converted to a configurable PySpark batch job. On both golden fixtures it is byte-identical to the legacy SYSOUT and return code, measured with the repo's comparator. It passes 25 of 25 BDD scenarios, which cover all 12 approved rules. All five stages were approved. One defect (D-01, close-error banner) was found in validation, fixed in Stage 4 v2, and re-validated. The job is ready for SIT, UAT and a parallel run; production is conditional (§11).

## 2. Workload Overview
| Item | Value |
|---|---|
| JCL | `app/jcl/READCARD.jcl`, one step STEP05 `PGM=CBACT02C` |
| Input | `AWS.M2.CARDDEMO.CARDDATA.VSAM.KSDS` (DD CARDFILE, DISP=SHR), shared with CICS file CARDDAT |
| Layout | `CVACT02Y`, 150 bytes, key CARD-NUM X(16) |
| Output | SYSOUT listing: start banner, each record, end banner |
| Chain | `read-masters` (READACCT, READCARD, READXREF, READCUST) |
| Complexity | LOW: 1 program, 1 copybook, no SQL, no utilities |

## 3. Discovery Summary
Every dependency is resolved. Eight risks were raised (R-01..R-08); R-01, clear-text PAN/CVV, was decided as "keep exactly as legacy". The lineage is CARDDATA.PS → (CARDFILE.jcl IDCAMS REPRO) → KSDS → CBACT02C → SYSOUT. See `discovery/`.

## 4. Business Rule Summary
- **Business rules (6):** BR-001 list all records, BR-002 card-number order, BR-003 verbatim 150-byte image, BR-004 banners, BR-005 empty input gives RC 0, BR-006 I/O error gives abend 999.
- **Technical rules (6):** TR-001 to TR-006.
- **Ambiguities (4):** A-01 to A-04; none was implemented as a rule except reproducing A-04.

See `technical_summary/business_rules.md`.

## 5. Target Design Summary
Stages S0–S5 plus error path E. Input is fixed-width 150-character text by default, with Parquet as an option. Ordering uses EBCDIC (cp037) collation. Outputs are `sysout/READCARD.STEP05.txt`, `rc.json` and `run_metrics.json`. Decisions OQ-01 to OQ-04 took the defaults:

| Decision | Default taken |
|---|---|
| OQ-01 | RC 12 plus abend code 999 |
| OQ-02 | No detail lines before a read error |
| OQ-03 | Reject duplicate keys |
| OQ-04 | Fixed-width input, Parquet optional |

## 6. Implementation Summary
`pyspark_generation/pyspark_application.py` (v2) and `config.json`. The code is split into reusable functions; paths, encoding, lrecl, collation, abend codes, messages and layout all come from configuration. It logs and writes the listing with an atomic rename. v2 changes two lines in `handle_abend` (D-01); v1 is kept in `history/v1/`.

## 7. Validation Summary
| Check | Result |
|---|---|
| Business rules | 12 / 12 PASS (plus V-01, V-02, OQ-02, OQ-04) |
| Mappings | 10 / 10 PASS |
| Processing flow | PASS (no aggregation stage by design) |
| Error handling (open / read / close) | PASS |
| Code quality | 2 minor findings (Q-01: temp file left on a mid-write failure; Q-02: generic S2 exception uses status 34) |

## 8. BDD Coverage Summary
`validation/features/READCARD.feature` has 20 scenarios (25 test cases), with step definitions in `validation/steps/READCARD_steps.py` (pytest-bdd 8.1). Results are 25/25 in run 2 and 24/25 in run 1. BRE coverage is 100%. Synthetic test data is in `validation/test_data/`.

## 9. Reconciliation Summary
| Case | SYSOUT | RC | input = output count | min/max key | SHA-256 of details |
|---|---|---|---|---|---|
| r01-populated | PASS | PASS | 2 = 2 | PASS | PASS |
| r02-empty | PASS | PASS | 0 = 0 | n/a | n/a |

There are no business totals: the program computes none. The 20,000-record synthetic run listed every record in order.

## 10. Approval History
| Stage | Decision | By | Notes |
|---|---|---|---|
| 1 Discovery | Approved | aruna.vs | Keep card number/CVV output exactly as legacy |
| 2 BRE | Approved | aruna.vs | |
| 3 Target design | Approved | aruna.vs | Default answers to OQ-01..OQ-04 |
| 4 PySpark v1 | Approved, then Rejected | aruna.vs | Rejected for D-01 after validation run 1 |
| 4 PySpark v2 | Approved | aruna.vs | D-01 fix |
| 5 Validation run 2 | Approved | aruna.vs | Assemble final workbench |

Full event log: `audit/approval_audit.json`.

## 11. Open Risks
| ID | Risk | Severity |
|---|---|---|
| OR-01 | Clear-text PAN/CVV in output (kept by decision); needs a security sign-off for the target platform | Medium |
| OR-02 | No legacy golden fixtures for the error paths; expectations come from the COBOL source | Medium |
| OR-03 | A live legacy re-run was not done in this session (Docker Hub rate limit, HTTP 429) | Low |
| OR-04 | File-status display for the binary branch differs by platform (`9048` off-host vs `9240` on z/OS) | Low |
| OR-05 | Production schedule and restart policy are unknown (R-05) | Low |
| OR-06 | Batch read of a KSDS that CICS shares (R-03); the target reads a snapshot | Low |
| OR-07 | Validated on `local[*]` only; no cluster or object-store run yet | Low |
| OR-08 | An existing Java port of read-masters (R-08) means two parallel implementations | Info |
| OR-09 | Design errata: the VR-12 example direction is reversed (the code is correct) | Info |

## 12. Migration Readiness Assessment
| Gate | Status |
|---|---|
| SIT | READY |
| UAT | READY |
| Parallel run | READY (`run_metrics.json` gives counts and hashes) |
| Production | CONDITIONAL on OR-01, OR-02, OR-05 and OR-07 |

## 13. Final Recommendation
Proceed to SIT and a parallel run against the production card master. Before production cut-over:
- record the legacy error-path goldens with `make record` once the user requests it
- get a security sign-off on clear-text card output
- confirm the schedule and restart policy
- run on the target Spark cluster
