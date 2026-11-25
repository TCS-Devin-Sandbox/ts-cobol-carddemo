# READCARD — Data Lineage

## Source datasets

| Dataset | Org | LRECL | Key | Producer | Evidence |
|---|---|---|---|---|---|
| AWS.M2.CARDDEMO.CARDDATA.PS | PS (seed) | 150 | — | external / data load | CARDFILE.jcl STEP15 |
| AWS.M2.CARDDEMO.CARDDATA.VSAM.KSDS | VSAM KSDS | 150 (fixed) | 16 @ 0 (CARD-NUM) | CARDFILE.jcl STEP10 DEFINE, STEP15 REPRO; also updated online by CICS FILE CARDDAT | CARDFILE.jcl, CARDDEMO.CSD:25-26 |

## End-to-end lineage

```
CARDDATA.PS ──REPRO (CARDFILE STEP15)──▶ CARDDATA.VSAM.KSDS ──BLDINDEX──▶ CARDDATA.VSAM.AIX (not used here)
                                              │   ▲
                                              │   └── CICS CARDDAT online updates (COCRDUPC, COACTUPC)
                                              ▼
                         READCARD STEP05 / CBACT02C  (READ … INTO CARD-RECORD, sequential)
                                              ▼
                                   SYSOUT listing (spool)
```

## Transformations

| Point | Transformation | Evidence |
|---|---|---|
| READ INTO | FD-CARDFILE-REC (16 + 134) moved to CARD-RECORD (CVACT02Y, 150) — byte copy, no conversion | CBACT02C.cbl:93 |
| DISPLAY | CARD-RECORD written as one 150-byte line | CBACT02C.cbl:78 |

No filtering, derivation, aggregation or sorting beyond KSDS key order.

## Intermediate datasets

None (no temporary `&&` datasets, no GDGs, no work files).

## Target datasets

| Target | Content | Format |
|---|---|---|
| SYSOUT (STEP05) | `START OF EXECUTION OF PROGRAM CBACT02C`, one line per card record, `END OF EXECUTION OF PROGRAM CBACT02C` | text, record lines 150 bytes |
| SYSPRINT | nothing written | — |

## Field-level lineage (CARD-RECORD → SYSOUT line)

| Field | Offset | Len | Output columns |
|---|---|---|---|
| CARD-NUM | 0 | 16 | 1-16 |
| CARD-ACCT-ID | 16 | 11 | 17-27 |
| CARD-CVV-CD | 27 | 3 | 28-30 |
| CARD-EMBOSSED-NAME | 30 | 50 | 31-80 |
| CARD-EXPIRAION-DATE | 80 | 10 | 81-90 |
| CARD-ACTIVE-STATUS | 90 | 1 | 91 |
| FILLER | 91 | 59 | 92-150 |

## Downstream consumers of the same KSDS (not part of READCARD)

CBEXPORT.jcl; CICS programs COACTUPC, COACTVWC, COCRDLIC, COCRDSLC, COCRDUPC (FILE CARDDAT).
