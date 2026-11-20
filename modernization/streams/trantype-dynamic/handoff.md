# Trantype-dynamic stream hand-off — Track B, part 2

**Status: PROPOSED hand-off, not started. Starts only after COBTUPDT is converted** (trantype-update passes 7/7). Chain `trantype-dynamic` in [chains.json](../../../tools/runjcl/chains.json): [MNTTRDYN.jcl](../../../app/app-transaction-type-db2/jcl/MNTTRDYN.jcl) STEP1 → IKJEFT01 `RUN PROGRAM(CBTRNDYN) PLAN(CARDDEMO) PARMS('COBTUPDT')` → [CBTRNDYN](../../../app/app-transaction-type-db2/cbl/CBTRNDYN.cbl), which runs one SQL statement from DYNSQL with PREPARE/EXECUTE and then `CALL WS-PGM-NAME` with the name taken from the PARM. Base `demo/06-lab @ 1341a98`. Plan: [tracks.md](../../design/tracks.md). Frozen: [shared-layouts §2 and §3](../../design/shared-layouts.md#3--program-cobtupdt--run-by-trantype-update-called-by-trantype-dynamic).

## Reused unchanged

| Asset | Source |
|---|---|
| Shared skills, toolchain, comparator | As in the [trantype-update hand-off](../trantype-update/handoff.md#reused-unchanged-from-interest); `compare.py trantype-dynamic <case>`. |
| Interest codec | X code only: DYNSQL is 80-byte `PIC X(80)` lines; INPFILE is the frozen 53-byte COBTUPDT record. |
| **Converted COBTUPDT** | From trantype-update, called as is through its frozen boundary (C1–C7, SH-04). This stream must not change it; a needed change is a register row. |
| Trantype annex and pilot skills | Written by the Track B pilot; this stream extends the annex with a dynamic section. |

## New for trantype-dynamic

| Item | Source | Recorded oracle |
|---|---|---|
| Statement assembly: skip lines starting `--`; concatenate columns 1–72 of each other line plus one space into an 800-byte VARCHAR with a pointer; count lines | Y 67–85 | `CBTRNDYN: 03 SQL LINES READ` (d01), `01` (d02, d03) |
| PREPARE failure → `PREPARE FAILED SQLCODE`, RC 8, no EXECUTE | Y 88–93 | not recorded |
| EXECUTE: print SQLCODE (`+0000000000` / `-0000000400` format); `< 0` → RC 8; continue | Y 95–101 | d01, d02, d03 |
| Dynamic call: `PARM-LENGTH > 0` → move `PARM-VALUE(1:PARM-LENGTH)` to 8-byte name; `CALLING <name>`; `CALL WS-PGM-NAME` | Y 58–63 | `CBTRNDYN: CALLING COBTUPDT` |
| After the call: COBTUPDT's STOP RUN ends the run unit; `CBTRNDYN: END` is never printed; final RC is COBTUPDT's last setting | U 99; Y 64 | d01–d03 SYSOUT, d03 RC 4 |
| PostgreSQL aborted transaction: COBTUPDT's statement fails `-400` after a failed EXECUTE | d03 README | d03 table unchanged |

## Gaps the Track B pilot (and this stream) must close

No standard or skill covers these; **no standard is written here**.

| Gap | What is missing |
|---|---|
| G6 · Dynamic SQL | Executing operator-supplied SQL text: the target must run the same text (Db2 dialect, unqualified `TRANSACTION_TYPE` resolved by the plan's qualifier vs the PostgreSQL `search_path`), with the same statement boundaries, and must not widen what may run. Security review of arbitrary SQL is required; today any table, including `transaction_type_categor`, can be changed. |
| G7 · Dynamic call | Resolving a program **name held in a variable** (from the PARM) to a Java implementation: a closed registry of callable programs, behaviour for an unknown name or `PARM-LENGTH = 0` (not recorded), and the 8-byte truncation. The only proven target is COBTUPDT (MNTTRDYN.jcl). |
| G3/G4 (from trantype-update) | Shared unit of work across the caller and the callee; STOP RUN and RETURN-CODE propagation (SH-04, SH-05). |

## Open asks

| ID | Ask | Owner |
|---|---|---|
| TD-A1 | Which program names may the PARM name in production, and who supplies DYNSQL? | Operations / Security |
| TD-A2 | SH-05: keep whole-transaction abort after a failed dynamic statement (d03 golden) or Db2 statement rollback? | Transaction-reference owner |
| TD-A3 | Final RC when EXECUTE fails (RC 8) and COBTUPDT then succeeds: not recorded. | Fixture owner (record on request) |
| TD-A4 | PREPARE failure, empty DYNSQL, statement longer than 800 bytes, `PARM-LENGTH = 0`: no goldens. | Fixture owner |
| TD-A5 | Default schema/qualifier for unqualified table names in DYNSQL on the host plan `CARDDEMO`. | Db2 DBA |
