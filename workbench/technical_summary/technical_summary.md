# READCARD — Technical Summary

Stage 2 (Technical Summary & BRE). Inputs: approved Stage 1 discovery artifacts plus source. Rules referenced by ID from `business_rules.md`.

## 1. Workload Overview

| Item | Value |
|---|---|
| Job | READCARD (`app/jcl/READCARD.jcl`), CLASS=A, 1 step |
| Step / program | STEP05 → CBACT02C (batch COBOL, LE) |
| Input | AWS.M2.CARDDEMO.CARDDATA.VSAM.KSDS (DD CARDFILE, DISP=SHR) |
| Output | SYSOUT listing (DD SYSOUT); SYSPRINT allocated, unused |
| Record | CVACT02Y CARD-RECORD, 150 bytes, key CARD-NUM X(16) |
| Nature | Read-only, full-file sequential listing |

## 2. Business Purpose

Documented purpose (program header): "Read and print card data file"; JCL comment: "RUN THE PROGRAM THAT READS THE CARD MASTER VSAM FILE". The job produces a complete, key-ordered print of the card master (BR-001, BR-002, BR-003). Further business intent (audit, reconciliation feed) is not evidenced (A-03). Within the estate it is one of the four master readers in the `read-masters` chain (READACCT, READCARD, READXREF, READCUST) used to verify master data after load.

## 3. Technical Execution Flow

1. JES allocates STEPLIB, CARDFILE (SHR), SYSOUT, SYSPRINT.
2. CBACT02C displays start banner (BR-004).
3. OPEN INPUT CARDFILE (TR-005); non-'00' → error path (BR-006).
4. Loop: READ next INTO CARD-RECORD → '00' display record (BR-003) · '10' EOF (TR-001) · other → error path.
5. CLOSE; non-'00' → error path.
6. Display end banner, GOBACK RC 0.
Error path: message (TR-002) → formatted status (TR-003) → `ABENDING PROGRAM` → CEE3ABD 999 (TR-004).

## 4. Processing Stages

| Stage | Operation | Rules |
|---|---|---|
| Initialise | banner, OPEN INPUT | BR-004, BR-006 |
| Extract | sequential READ in key order | BR-001, BR-002, TR-001 |
| Emit | DISPLAY 150-byte record | BR-003 |
| Finalise | CLOSE, banner, RC 0 | BR-004, BR-005, BR-006 |

Data processing logic: Reads ✔ (VSAM sequential) · Writes ✔ (SYSOUT only) · Updates ✘ · Deletes ✘ · Lookups ✘ · Merges ✘ · Joins ✘ · Sorting implicit key order · Aggregations ✘ · Calculations ✘.

## 5. Dataset Usage

| Dataset | Class | Usage | R/W | Business significance |
|---|---|---|---|---|
| AWS.M2.CARDDEMO.CARDDATA.VSAM.KSDS | Input (master/reference) | VSAM sequential read | Read | System of record for cards, shared with CICS CARDDAT |
| SYSOUT (STEP05) | Output (report) | DISPLAY listing | Write | The job's only business output |
| SYSOUT (error lines) | Error output | Diagnostics on failure | Write | Operator diagnostics |
| SYSPRINT | Output (unused) | none | — | none |
| AWS.M2.CARDDEMO.LOADLIB | Technical | STEPLIB | Read | Program load |

Intermediate datasets: none.

### Fields (CVACT02Y CARD-RECORD)

| field_name | data_type | length | offset | business_meaning | used by logic |
|---|---|---|---|---|---|
| CARD-NUM | X (alphanumeric) | 16 | 0 | Card number, primary key | ordering (BR-002), output |
| CARD-ACCT-ID | 9 zoned unsigned | 11 | 16 | Owning account id (AIX key in CARDFILE.jcl) | output only |
| CARD-CVV-CD | 9 zoned unsigned | 3 | 27 | Card verification code | output only (clear, per decision) |
| CARD-EMBOSSED-NAME | X | 50 | 30 | Name embossed on card | output only |
| CARD-EXPIRAION-DATE | X | 10 | 80 | Expiry date (YYYY-MM-DD observed, A-02) | output only |
| CARD-ACTIVE-STATUS | X | 1 | 90 | Active flag ('Y' observed, A-01) | output only |
| FILLER | X | 59 | 91 | Unused | output only (spaces) |

## 6. Program Interactions

CBACT02C → CVACT02Y (COPY) · CBACT02C → CEE3ABD (static CALL, abend only). No subprograms, no dynamic calls.

## 7. Database Interactions

Database operations: none (no EXEC SQL, no DB2/IMS).

File operations:

| File | Operation | Purpose |
|---|---|---|
| CARDFILE (VSAM KSDS) | OPEN INPUT, sequential READ NEXT until status '10', CLOSE | Extract all card records |
| SYSOUT | DISPLAY (sequential write) | Listing and diagnostics |
| VSAM writes | none | — |

## 8. Error Handling

| Condition | Response | Impact |
|---|---|---|
| OPEN status ≠ '00' (e.g. 35 file missing, 93 resource unavailable) | `ERROR OPENING CARDFILE`, status, `ABENDING PROGRAM`, CEE3ABD 999 | Job abends U999; only start banner printed |
| READ status '10' | EOF, normal end | RC 0 |
| READ status ∉ {'00','10'} | `ERROR READING CARDFILE`, status, abend 999 | Partial listing on SYSOUT, job abends |
| CLOSE status ≠ '00' | `ERROR CLOSING CARDFILE`, status, abend 999 | Full listing, no end banner, job abends (A-04) |
| Empty file | none | RC 0 (BR-005) |
| Return codes | RC 0 on success; no RC 4/8 paths | Scheduler sees 0 or U999 |
| Restart logic | none (no checkpoint; rerun from start is idempotent because read-only) | Safe rerun |

## 9. Modernization Observations

- Behaviour is a pure ordered projection of the card master; there are no business calculations to re-implement.
- Parity hinges on: key order (BR-002), exact 150-byte fixed-width lines incl. trailing FILLER spaces (BR-003), banner text (BR-004), RC/abend semantics (BR-005/006), diagnostic text formats (TR-002/003).
- The output deliberately carries card number and CVV in clear (reviewer decision, Stage 1).
- Golden fixtures cover the normal and empty paths only; error paths (TR-002..TR-004, A-04) have no golden evidence.
- Rerun is idempotent (read-only, TR-005).
