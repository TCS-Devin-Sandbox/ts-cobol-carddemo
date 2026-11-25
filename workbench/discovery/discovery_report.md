# READCARD — Discovery Report

Stage 1 of the Agentic Modernization Workbench. Evidence-only; every finding cites a repository artifact. Machine-readable form: `discovery.json`.

## 1. Workload Overview

READCARD is a single-step batch job that runs COBOL program **CBACT02C** to read the card master VSAM KSDS (`AWS.M2.CARDDEMO.CARDDATA.VSAM.KSDS`) sequentially and print every 150-byte card record to SYSOUT. It is read-only. It belongs to the off-host `read-masters` chain (`tools/runjcl/chains.json`) with READACCT, READXREF and READCUST, and has two golden fixtures (`r01-populated`, `r02-empty`).

## 2. Job Inventory

| Job | Step | Program | Type | Inputs | Outputs | PROC | COND | Restart |
|---|---|---|---|---|---|---|---|---|
| READCARD | STEP05 | CBACT02C | Batch COBOL | CARDFILE → CARDDATA.VSAM.KSDS (SHR) | SYSOUT, SYSPRINT (unused) | none | none | none |

Related job (producer, not executed by READCARD): `CARDFILE.jcl` — CICS file close (SDSF), IDCAMS DELETE/DEFINE KSDS, REPRO from CARDDATA.PS, DEFINE AIX/PATH, BLDINDEX, CICS file reopen.

## 3. Program Inventory

| Program | Purpose | Copybooks | Calls | Files | SQL/CICS |
|---|---|---|---|---|---|
| CBACT02C | Read and print card data file | CVACT02Y | CEE3ABD (static, LE, abend only) | CARDFILE-FILE: INDEXED, SEQUENTIAL, INPUT, key FD-CARD-NUM X(16), status CARDFILE-STATUS | none |

Record layout CVACT02Y `CARD-RECORD` (150 bytes): CARD-NUM X(16) key · CARD-ACCT-ID 9(11) · CARD-CVV-CD 9(03) · CARD-EMBOSSED-NAME X(50) · CARD-EXPIRAION-DATE X(10) · CARD-ACTIVE-STATUS X(01) · FILLER X(59).

## 4. Dataset Inventory

| Dataset | Type | Role | Attributes |
|---|---|---|---|
| AWS.M2.CARDDEMO.CARDDATA.VSAM.KSDS | VSAM KSDS | Input | KEYS(16 0), RECORDSIZE(150 150), SHAREOPTIONS(2 3) — CARDFILE.jcl STEP10 |
| AWS.M2.CARDDEMO.LOADLIB | Load library | STEPLIB | — |
| SYSOUT / SYSPRINT | Spool | Output | SYSOUT=* |
| AWS.M2.CARDDEMO.CARDDATA.PS | PS | Indirect upstream (REPRO source) | 150 bytes |
| AWS.M2.CARDDEMO.CARDDATA.VSAM.AIX(.PATH) | VSAM AIX | Not used by READCARD | KEYS(11 16) NONUNIQUEKEY |

No GDGs, temporary datasets, DB2 objects, IMS, MQ or external interfaces.

## 5. Business Functions (observed)

| ID | Function | Evidence |
|---|---|---|
| BF-01 | List every card master record in ascending card-number order, verbatim, between start/end banners | CBACT02C.cbl:71-87, 93 |
| BF-02 | Abend (CEE3ABD, code 999) on OPEN/CLOSE status ≠ '00' or READ status ∉ {'00','10'} with diagnostic text | :93-158 |
| BF-03 | Format file status as `FILE STATUS IS: NNNN` + 4 chars (numeric: `00`+status; non-numeric or stat1='9': stat1 + 3-digit binary of stat2) | :161-174 |

Observed logic categories: transformation none · validation file-status only · filtering none · enrichment none · calculation none · sorting implicit key order · aggregation none (no counts) · reporting plain SYSOUT listing, no headers.

## 6. Dependency Map

```
READCARD ──STEP05──▶ CBACT02C ──COPY──▶ CVACT02Y
                         │──CALL──▶ CEE3ABD (LE)
                         └──CARDFILE──▶ CARDDATA.VSAM.KSDS ◀── CARDFILE.jcl (define/load)
                                                    ◀──▶ CICS FILE CARDDAT (online)
```

All dependencies resolved. No dynamic calls.

## 7. Data Lineage

`CARDDATA.PS` → (CARDFILE REPRO) → `CARDDATA.VSAM.KSDS` → (CBACT02C sequential READ INTO, no change) → SYSOUT. Details in `lineage.md`.

## 8. Verified Behaviour (golden fixtures)

| Case | Input | SYSOUT | RC |
|---|---|---|---|
| r01-populated | 2 cards (4000000000001001, 4000000000001002) | banners + 2 lines in key order | 0 |
| r02-empty | empty KSDS | banners only | 0 |

## 9. Migration Complexity Assessment

| Factor | Value |
|---|---|
| Programs | 1 |
| Copybooks | 1 |
| Datasets touched | 1 (VSAM KSDS input) |
| SQL | none |
| VSAM | 1 KSDS, sequential read only |
| Utilities | none |
| External dependencies | none |
| Integration | shares KSDS with CICS online |
| **Rating** | **LOW** |

## 10. Risk Assessment

| ID | Severity | Risk |
|---|---|---|
| R-01 | Medium | Full card number and CVV printed in clear to SYSOUT |
| R-02 | Low | CEE3ABD U999 abend needs a target failure/exit-code convention |
| R-03 | Low | KSDS shared with CICS CARDDAT (SHAREOPTIONS 2 3); batch may read during online updates |
| R-04 | Low | Hardcoded AWS.M2.CARDDEMO.* DSNs |
| R-05 | Low | No scheduler definition in repo: frequency, predecessors, restart policy unknown |
| R-06 | Info | EBCDIC on-host vs ASCII fixtures; fixture inputs trimmed, SYSOUT lines are full 150 bytes |
| R-07 | Info | SYSPRINT allocated but unused |
| R-08 | Info | Existing Java read-masters port in `java/read-masters`; PySpark would be parallel |

Unresolved / missing information: production schedule and restart policy (R-05); CEE3ABD runtime behaviour off-host depends on harness (not in scope of the COBOL source).

## 11. Modernization Observations

- Behaviour is fully observable from one program and one copybook; golden fixtures exist for populated and empty cases, with field-level parity tooling in `tools/parity`.
- The output is the record image itself, so output parity depends on exact 150-byte fixed-width reproduction (including FILLER spaces) and key ordering.
