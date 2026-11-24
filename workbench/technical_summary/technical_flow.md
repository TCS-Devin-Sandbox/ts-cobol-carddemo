# READCARD — Technical Flow

## End-to-end processing flow

```mermaid
flowchart TD
  A[JES: READCARD STEP05] --> B[CBACT02C: DISPLAY START banner]
  B --> C{OPEN INPUT CARDFILE status = 00?}
  C -- no --> E1[ERROR OPENING CARDFILE] --> X
  C -- yes --> D[READ NEXT INTO CARD-RECORD]
  D --> S{status}
  S -- 00 --> P[DISPLAY CARD-RECORD 150 bytes] --> D
  S -- 10 EOF --> F{CLOSE status = 00?}
  S -- other --> E2[ERROR READING CARDFILE] --> X
  F -- no --> E3[ERROR CLOSING CARDFILE] --> X
  F -- yes --> G[DISPLAY END banner] --> H[GOBACK RC 0]
  X[9910 FILE STATUS IS: NNNN... -> ABENDING PROGRAM -> CEE3ABD 999] --> Z[Abend U999]
```

## Program relationships

| Caller | Callee | Mechanism | When |
|---|---|---|---|
| JCL STEP05 | CBACT02C | EXEC PGM | always |
| CBACT02C | CVACT02Y | COPY | compile |
| CBACT02C | CEE3ABD | static CALL | error path only |

## Data movement flow

| # | From | To | Mechanism | Change |
|---|---|---|---|---|
| 1 | CARDDATA.VSAM.KSDS | FD-CARDFILE-REC | sequential READ, key order | none |
| 2 | FD-CARDFILE-REC | CARD-RECORD | READ … INTO | none (150 → 150) |
| 3 | CARD-RECORD | SYSOUT | DISPLAY | none |
| 4 | CARDFILE-STATUS | IO-STATUS → IO-STATUS-04 | MOVE / REDEFINES | TR-003 formatting |

## Execution dependencies

| Dependency | Type | Required state |
|---|---|---|
| CARDFILE.jcl (DEFINE + REPRO) | Upstream job (data) | KSDS defined and loaded |
| AWS.M2.CARDDEMO.LOADLIB(CBACT02C) | Load module | present |
| CICS CARDDAT | Concurrent user | may be open; DISP=SHR, SHAREOPTIONS(2 3) |
| read-masters chain order | Off-host harness | after READACCT, before READXREF; no data hand-off |
