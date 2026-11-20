# Shared layouts, tables and program interface — SHARED v1.0 (FROZEN)

**Status: FROZEN for Step 7 hand-off.** Base: `demo/06-lab @ 1341a98`. Read with [INT-ARCH v1.0](architecture.md), [INT-MAP v1.0](data-map-interest.md) and [tracks.md](tracks.md). Everything on this page is frozen: **a change is a row in the [change register](#change-register) below, not a ticket.** No stream may edit a frozen item in its own branch; it proposes a row and waits for the owner named in [tracks.md](tracks.md#file-owners). Every row in the register is OPEN until a person decides it.

Line references are inclusive, against the files on this branch. `P` = [CBTRN02C](../../app/cbl/CBTRN02C.cbl), `R` = [CBTRN03C](../../app/cbl/CBTRN03C.cbl), `I` = [CBACT04C](../../app/cbl/CBACT04C.cbl), `U` = [COBTUPDT](../../app/app-transaction-type-db2/cbl/COBTUPDT.cbl), `Y` = [CBTRNDYN](../../app/app-transaction-type-db2/cbl/CBTRNDYN.cbl), `X` = [TRANEXTR.jcl](../../app/app-transaction-type-db2/jcl/TRANEXTR.jcl).

## 1 · Files shared by interest and posting

The prompt listed four layouts. Checking every `COPY` in CBACT04C, CBTRN02C and CBTRN03C confirms all four and adds nothing new to the *shared* list: CVTRA02Y (DISCGRP) is interest-only; CVTRA06Y, CVTRA03Y, CVTRA04Y, CVTRA07Y and REJTRAN are posting-only (see [posting hand-off](../streams/posting/handoff.md)). Two points the list misses are added below: **CVTRA05Y is shared by layout but not by dataset**, and **CVACT03Y is shared through the base cluster only** (posting never reads the AIX).

| Layout · LRECL | Dataset (prefix `AWS.M2.CARDDEMO.`) | Interest (CBACT04C) | Posting (CBTRN02C / CBTRN03C) | Frozen mapping |
|---|---|---|---|---|
| [CVACT01Y](../../app/cpy/CVACT01Y.cpy) ACCOUNT-RECORD · 300 | `ACCTDATA.VSAM.KSDS` (DD ACCTFILE) | **Updates**: `OPEN I-O` (I 291), keyed READ (I 373), REWRITE (I 356): adds interest to ACCT-CURR-BAL, sets both cycle totals to 0 (I 352–354) | **CBTRN02C updates**: `OPEN I-O` (P 311), keyed READ (P 395), REWRITE (P 554): adds DALYTRAN-AMT to ACCT-CURR-BAL and to CYC-CREDIT (amount ≥ 0) or CYC-DEBIT (P 547–552). CBTRN03C does not open it. | INT-MAP rows A01–A13 |
| [CVTRA01Y](../../app/cpy/CVTRA01Y.cpy) TRAN-CAT-BAL-RECORD · 50 | `TCATBALF.VSAM.KSDS` (DD TCATBALF) | **Reads** only: `OPEN INPUT` (I 236), sequential READ (I 326) | **CBTRN02C updates**: `OPEN I-O` (P 329), keyed READ (P 474), WRITE of a new key (P 510), REWRITE (P 528) | INT-MAP rows B00–B05 |
| [CVACT03Y](../../app/cpy/CVACT03Y.cpy) CARD-XREF-RECORD · 50 | `CARDXREF.VSAM.KSDS` (DD XREFFILE / CARDXREF); interest also `CARDXREF.VSAM.AIX.PATH` (XREFFIL1) | **Reads** only: `OPEN INPUT` (I 254), alternate-key READ (I 394) | **Reads** only: CBTRN02C `OPEN INPUT` (P 275), keyed READ by card (P 383); CBTRN03C `OPEN INPUT` (R 414), keyed READ (R 485). No AIX use. | INT-MAP rows X01–X04 |
| [CVTRA05Y](../../app/cpy/CVTRA05Y.cpy) TRAN-RECORD · 350 | Interest: `SYSTRAN(+1)` (sequential GDG). Posting: `TRANSACT.VSAM.KSDS`, then `TRANSACT.BKUP(+1)` and `TRANSACT.DALY(+1)` | **Writes** `SYSTRAN(+1)`: `OPEN OUTPUT` (I 309) | **CBTRN02C writes** TRANSACT: `OPEN OUTPUT` (P 256), WRITE (P 564). **CBTRN03C reads** `TRANSACT.DALY(+1)` after REPRO and SORT (R 378, 249) | INT-MAP rows T01–T14 |

Neither stream reads a dataset the other writes **inside the lab**: each chain starts from fresh fixtures (`tools/runjcl/chains.json` `setup`). In production, ACCTDATA has two writers (interest and posting) and TRANSACT receives interest output through COMBTRAN ([architecture A4](architecture.md#a4--mainframe-coexistence-and-cutover-contract)). The order of the two writers is **not** proven by `app/scheduler`; see the [posting hand-off](../streams/posting/handoff.md#which-stream-updates-the-balances-first).

### What is frozen for these four layouts

1. **Field rows and codec codes** of INT-MAP v1.0 for A01–A13, B00–B05, X01–X04 and T01–T14: offsets, PIC, codec code (X, D, Z, B), SQL type, `COLLATE "C"`, `NOT NULL`, raw-record and filler retention.
2. **The codec contract**: the X/D/Z/B rules of INT-MAP, the `lab-ascii` harness profile and its overpunch table, and the Java implementation `com.carddemo.interest.codec.FixedRecordCodec` in `java/interest/interest-batch`. Posting reuses it unchanged (by dependency or a verbatim copy whose tests are the interest tests), never a fork.
3. **Key and order rules** of INT-MAP "Keys, indexes and derived columns": TCATBALF composite key in encoded byte order, xref base key by card, account key by ACCT-ID.
4. **The comparator layouts**: `tools/parity/compare.py` decodes these four datasets through the same copybooks for both chains (`chains.json` `outputs`).

### What is not shared, and therefore not frozen here

* The interest tables `interest.account_input`, `interest.card_xref_input`, `interest.category_balance_input`, `interest.interest_tx` and the `interest_batch` schema belong to interest ([A1](architecture.md#a1--target-boundary-and-ownership)). Posting reuses the **column mapping**, not the tables: it must not write any `interest.*` table. Where posting stores these layouts (its own schema with the same column rows is the default) is change-register row SH-01.
* Interest-specific semantics (truncation, last-account quirk, DEFAULT group fallback) are not part of the layout contract.

## 2 · Tables shared by the trantype streams

Confirmed against [db/postgres/01-transaction-type.sql](../../db/postgres/01-transaction-type.sql), the Db2 DDL ([TRNTYPE.ddl](../../app/app-transaction-type-db2/ddl/TRNTYPE.ddl), [TRNTYCAT.ddl](../../app/app-transaction-type-db2/ddl/TRNTYCAT.ddl)) and `chains.json` `database.tables`.

| Table (PostgreSQL · Db2) | Columns | trantype-update (COBTUPDT) | trantype-dynamic (CBTRNDYN → COBTUPDT) | Other readers / writers outside the tracks |
|---|---|---|---|---|
| `carddemo.transaction_type` · `CARDDEMO.TRANSACTION_TYPE` | `tr_type CHAR(2)` PK, `tr_description VARCHAR(50)` | **Updates**: INSERT (U 137–147), UPDATE (U 171–175), DELETE (U 201–204) | **Updates** twice: the statement read from DYNSQL (Y 88–97; the d01–d03 cases target this table) and then COBTUPDT's statements | Read by TRANEXTR STEP40 (X, `SELECT ... FROM CARDDEMO.TRANSACTION_TYPE ORDER BY TR_TYPE`). Online COTRTLIC/COTRTUPC also SELECT, UPDATE, INSERT and DELETE it (analysis-only). |
| `carddemo.transaction_type_categor` · `CARDDEMO.TRANSACTION_TYPE_CATEGORY` | `trc_type_code CHAR(2)`, `trc_type_category CHAR(4)` (PK both), `trc_cat_data VARCHAR(50)`; FK `trc_type_code` → `transaction_type.tr_type` **ON DELETE RESTRICT** | **Reads** only, implicitly: no statement names it, but the FK makes a DELETE of a referenced type fail | Neither program names it; **the dynamic statement could update it** (any SQL text in DYNSQL). None of d01–d03 does. | Read by TRANEXTR STEP50 |

Added to the list: (a) the FK with `ON DELETE RESTRICT` is part of the frozen contract — it is how the category table constrains trantype-update; (b) the PostgreSQL name is `transaction_type_categor`, the Db2 name `TRANSACTION_TYPE_CATEGORY` (TRANEXTR uses the Db2 name). Both parity chains snapshot both tables after every case (`chains.json`), so both are oracles for both streams even where only one is written.

**Frozen:** table and column names, types, PK and FK, the seed `db/seed.sql` used by the fixtures, and the snapshot columns/order in `chains.json`. Owned by the transaction-reference maintenance owner ([A1](architecture.md#a1--target-boundary-and-ownership)); interest takes no dependency on them.

## 3 · Program COBTUPDT — run by trantype-update, called by trantype-dynamic

MNTTRDB2 STEP1 runs it as the main program (`RUN PROGRAM(COBTUPDT) PLAN(CARDDEMO)`); MNTTRDYN STEP1 runs CBTRNDYN with `PARMS('COBTUPDT')`, and CBTRNDYN executes `CALL WS-PGM-NAME` with the name moved from the PARM (Y 59–62).

### Input record (INPFILE, RECFM F, LRECL 53)

| Bytes (1-based) | Field (U 40–46) | PIC | Meaning (MNTTRDB2 header) |
|---|---|---|---|
| 1 | `INPUT-TYPE` | X(1) | `A` add, `U` update, `D` delete, `*` comment; any other value → `ERROR: TYPE NOT VALID`, RC 4, processing continues (U 110–129) |
| 2–3 | `INPUT-TR-NUMBER` | X(2) | `tr_type` |
| 4–53 | `INPUT-TR-DESC` | X(50) | `tr_description` (INSERT and UPDATE only) |

Records are read with `READ ... INTO WS-INPUT-REC` (U 101), the same three fields at the same widths (U 71–76). Fixture text lines are trimmed; pad to 53 bytes before decoding.

### What the caller relies on

| # | Behaviour | Evidence |
|---|---|---|
| C1 | **No parameters.** COBTUPDT has `PROCEDURE DIVISION.` without `USING` and no LINKAGE SECTION; CBTRNDYN calls it with no `USING`. | U 80; Y 62 |
| C2 | **COBTUPDT opens INPFILE itself.** The DD must exist in the caller's step (MNTTRDYN STEP1 `INPFILE DD`). An empty file is valid (d02). | U 31, 83; MNTTRDYN.jcl; d02 README |
| C3 | **Same connection and unit of work.** Neither program issues CONNECT, COMMIT or ROLLBACK; the DSN attachment (harness: `DSNRUN ... COMMIT AT END OF TASK`) commits both programs' changes together at end of task. | grep of U and Y; d01 SYSOUT; `tools/runjcl/runjcl.py` IKJEFT01 handling |
| C4 | **STOP RUN ends the run unit.** COBTUPDT ends with `STOP RUN` (U 99), so control never returns to CBTRNDYN: `CBTRNDYN: END` (Y 64) is absent from every recorded MNTTRDYN SYSOUT. | U 99; d01/d03 SYSOUT |
| C5 | **RETURN-CODE is shared.** COBTUPDT's `9999-ABEND` only DISPLAYs and sets `RETURN-CODE` to 4 (U 230–233), then continues with the next record. In d03 CBTRNDYN set 8 (Y 99–101) and the step ended RC 4. Whether RC 8 survives when COBTUPDT reports no error is **not recorded** (open ask TD-A3). | U 230–233; d03 `rc.json` |
| C6 | **SQLCODE handling.** INSERT: 0 → `RECORD INSERTED SUCCESSFULLY`; < 0 → message and RC 4; no `+100` branch. UPDATE/DELETE: 0 → success message; `+100` → `No records found.` and RC 4; < 0 → message and RC 4. `WS-VAR-SQLCODE PIC ----9` formats the code. | U 149–162, 176–193, 205–224 |
| C7 | **Error after an aborted unit of work.** Under PostgreSQL a failed dynamic statement aborts the transaction, so COBTUPDT's statements then fail with SQLCODE −400 (d03); Db2 would roll back only the failed statement. | d03 README and SYSOUT |

**Discrepancy found (not a change):** the t05 README and AGENTS.md say COBTUPDT "ignores +100". The source has a `+100` branch on DELETE (U 210) that sets RC 4. The recorded t05 golden shows `RECORD DELETED SUCCESSFULLY` and RC 0, i.e. the lab's Open COBOL ESQL/PostgreSQL path returned SQLCODE 0 for a zero-row DELETE. The golden governs parity (S02); the Db2 behaviour is register row SH-03.

**Frozen:** the 53-byte record, C1–C7, the program name `COBTUPDT` as the call target, and its SYSOUT lines as recorded. The trantype-update conversion must expose COBTUPDT so that trantype-dynamic can call it in-process within one unit of work, without parameters, and with the STOP RUN/RC semantics of C4–C5; how is a Track B pilot decision (register row SH-04).

## Change register

All rows OPEN. A stream proposes a row; only the owner in [tracks.md](tracks.md#file-owners) and the business owner decide. Option A preserves legacy behaviour and is the default the hand-offs assume.

| ID | Item | Question | Options | Owner | Status |
|---|---|---|---|---|---|
| SH-01 | CVACT01Y/CVTRA01Y/CVACT03Y/CVTRA05Y storage for posting | Where does posting store the shared layouts? | **A** Own `posting` schema with the INT-MAP column rows unchanged. **B** Shared `carddemo_files` schema used by both streams (needs an interest migration). **C** Reuse `interest.*` tables (breaks A1 single writer). | Data architect | OPEN |
| SH-02 | Writer order on ACCTDATA | Which of posting and interest updates ACCT-CURR-BAL and the cycle totals first in a cycle? | **A** Keep each chain independent as in the lab; production order stays with the scheduler owner. **B** Prove an order from host scheduler evidence and add a combined fixture. | Scheduler owner / Card operations | OPEN |
| SH-03 | COBTUPDT `+100` on UPDATE/DELETE | Reproduce the lab golden (success, RC 0) or the Db2 source path (`No records found.`, RC 4)? | **A** Lab golden as recorded (t05). **B** Db2 source behaviour; needs re-recorded goldens on request. | Transaction-reference owner | OPEN |
| SH-04 | COBTUPDT call boundary | How does the Java COBTUPDT serve both a job step and an in-process call? | **A** One service, no parameters, shared transaction, terminates the step (STOP RUN). **B** Return to the caller (changes C4; d01 SYSOUT would gain `CBTRNDYN: END`). | Track B pilot / architect | OPEN |
| SH-05 | Dynamic-statement failure | Keep PostgreSQL whole-transaction abort (d03 golden) or Db2 statement-level rollback? | **A** Abort as recorded. **B** Savepoint per statement. **C** Fail the job before calling COBTUPDT. | Transaction-reference owner | OPEN |
