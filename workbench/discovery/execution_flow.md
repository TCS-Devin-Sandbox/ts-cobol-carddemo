# READCARD — Execution Flow

Source: `app/jcl/READCARD.jcl`, `app/cbl/CBACT02C.cbl`, `app/cpy/CVACT02Y.cpy`

## Job-level flow

```
READCARD (JOB, CLASS=A)
  └─ STEP05  EXEC PGM=CBACT02C   STEPLIB=AWS.M2.CARDDEMO.LOADLIB
        CARDFILE ← AWS.M2.CARDDEMO.CARDDATA.VSAM.KSDS (DISP=SHR, read)
        SYSOUT   → spool (listing)
        SYSPRINT → spool (unused)
```

Single step. No PROC, no COND/IF-THEN-ELSE, no restart parameters, no temporary datasets, no GDGs.

## Program flow — CBACT02C

| # | Paragraph | Action | Evidence |
|---|---|---|---|
| 1 | mainline | `DISPLAY 'START OF EXECUTION OF PROGRAM CBACT02C'` | CBACT02C.cbl:71 |
| 2 | 0000-CARDFILE-OPEN | `OPEN INPUT CARDFILE-FILE`; status ≠ '00' → 'ERROR OPENING CARDFILE' → 9910 → 9999 | :118-134 |
| 3 | mainline loop | `PERFORM UNTIL END-OF-FILE = 'Y'` → 1000-CARDFILE-GET-NEXT; if not EOF `DISPLAY CARD-RECORD` | :74-81 |
| 4 | 1000-CARDFILE-GET-NEXT | `READ CARDFILE-FILE INTO CARD-RECORD`; '00' → OK; '10' → EOF ('Y'); other → 'ERROR READING CARDFILE' → 9910 → 9999 | :92-116 |
| 5 | 9000-CARDFILE-CLOSE | `CLOSE CARDFILE-FILE`; status ≠ '00' → 'ERROR CLOSING CARDFILE' → 9910 → 9999 | :136-153 |
| 6 | mainline | `DISPLAY 'END OF EXECUTION OF PROGRAM CBACT02C'`, `GOBACK` (RC 0) | :85-87 |
| E1 | 9910-DISPLAY-IO-STATUS | Formats `FILE STATUS IS: NNNN` + IO-STATUS-04 | :161-174 |
| E2 | 9999-ABEND-PROGRAM | `DISPLAY 'ABENDING PROGRAM'`, `CALL 'CEE3ABD' USING ABCODE(999), TIMING(0)` | :154-158 |

## Execution paths

| Path | Trigger | SYSOUT | Completion |
|---|---|---|---|
| Normal | records present | start banner, N × 150-byte record lines (key order), end banner | RC 0 (fixture r01) |
| Empty file | KSDS has 0 records | start + end banners only | RC 0 (fixture r02) |
| Open failure | OPEN status ≠ '00' | start banner, ERROR OPENING CARDFILE, FILE STATUS, ABENDING PROGRAM | abend U999 |
| Read failure | READ status ∉ {'00','10'} | records so far, ERROR READING CARDFILE, FILE STATUS, ABENDING PROGRAM | abend U999 |
| Close failure | CLOSE status ≠ '00' | all records, ERROR CLOSING CARDFILE, FILE STATUS, ABENDING PROGRAM | abend U999 (no end banner) |

## Program relationships

- CBACT02C → CEE3ABD (static CALL, LE service, abend path only)
- CBACT02C → CVACT02Y (COPY, compile time)
- No other CALLs, no dynamic calls, no SQL, no CICS.

## Dataset movement

`AWS.M2.CARDDEMO.CARDDATA.VSAM.KSDS` --(sequential READ, key order)--> `CARD-RECORD` (WS) --(DISPLAY, unchanged)--> `SYSOUT`

## Context (outside this job)

- Producer: `CARDFILE.jcl` (close CICS files → IDCAMS DELETE → DEFINE KSDS → REPRO from `CARDDATA.PS` → DEFINE/BLDINDEX AIX → reopen CICS files).
- Off-host chain `read-masters` (`tools/runjcl/chains.json`): setup CARDFILE, then READACCT → READCARD → READXREF → READCUST; no data passes between the readers.
