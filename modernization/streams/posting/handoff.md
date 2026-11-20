# Posting stream hand-off — Track A

**Status: PROPOSED hand-off, not started.** Chain `posting` in [chains.json](../../../tools/runjcl/chains.json): [POSTTRAN.jcl](../../../app/jcl/POSTTRAN.jcl) STEP15 → CBTRN02C, then [TRANREPT.jcl](../../../app/jcl/TRANREPT.jcl) STEP05R (REPROC PRC001 unload, SORT) and STEP10R → CBTRN03C. Base `demo/06-lab @ 1341a98`. Plan: [tracks.md](../../design/tracks.md). Frozen interfaces: [shared-layouts.md](../../design/shared-layouts.md). Source audit already done: [carddemo-stream-posting annex](../../../.agents/skills/carddemo-stream-posting/SKILL.md) (**DRAFT - NOT PROVEN**); its P/R/JP/JR line references are reused here.

## Reused unchanged from interest

| Asset | Path | How posting uses it |
|---|---|---|
| Layout mapping | [INT-MAP](../../design/data-map-interest.md) rows A, B, X, T | Frozen by [shared-layouts §1](../../design/shared-layouts.md#1--files-shared-by-interest-and-posting). Column rows unchanged; tables in posting's own schema (SH-01, option A). |
| Schema conventions | `java/interest/interest-batch/src/main/resources/db/migration/V001__interest.sql`, `V002__batch_repository.sql` | Pattern only: `NOT NULL`, `COLLATE "C"`, raw record `BYTEA`, UUID run/snapshot IDs, separate Spring Batch metadata schema. Posting adds its own `V0xx` under its module; it does not edit the interest migrations or the `carddemo` trantype DDL. |
| Codec | `com.carddemo.interest.codec.FixedRecordCodec` (`lab-ascii` profile; normalise to `LAB_ASCII`) | X/D/Z/B for CVACT01Y, CVTRA01Y, CVACT03Y, CVTRA05Y and CVTRA06Y (same codes). Pad trimmed fixture lines to LRECL first. |
| Comparator | `tools/parity/compare.py posting <case>`, `tools/parity/layouts/REJTRAN.cpy`, `make parity CHAIN=posting` | Unchanged. Report is compared as text. |
| Lab | `lab/` (replay lab: `make lab`, `lab-gate`, `lab-gate-negative`) | Pattern for a posting lane later; lab code is interest-bound today (`lab/lanes.py`, `make lab-jar` builds the interest jar). Posting does not edit it in the pilot. |
| Shared skills | `.agents/skills/carddemo-conversion-standards`, `carddemo-ticket-build`, `carddemo-ticket-verify`, `carddemo-parity`, `carddemo-pitfalls` | Use as written; replace the interest bindings listed in the annex "Command availability" table. |
| Stream annex | `.agents/skills/carddemo-stream-posting/SKILL.md` | Starting inventory; the pilot promotes it from DRAFT to proven (Track A owns this file). |
| Toolchain | Java 21, Maven 3.9.9, Spring Boot 3.3.5, Spring Batch 5.1.2, Testcontainers 1.21.4, PostgreSQL 15 | Same versions as interest; the mirror/image pitfalls in carddemo-pitfalls apply. |

## New for posting

| Item | Layout · LRECL | Program use | What the stream must build |
|---|---|---|---|
| Daily transactions `DALYTRAN.PS` | [CVTRA06Y](../../../app/cpy/CVTRA06Y.cpy) DALYTRAN-RECORD · 350 | CBTRN02C `OPEN INPUT`, sequential READ (P 238, 346) | Mapping rows (same field shapes as CVTRA05Y with `DALYTRAN-` names); reader in input order. |
| Transaction types `TRANTYPE.VSAM.KSDS` | [CVTRA03Y](../../../app/cpy/CVTRA03Y.cpy) · 60: TYPE X(2), DESC X(50), FILLER X(8); KEYS(2 0) | CBTRN03C keyed READ (R 495) | Read-only reference input; loaded in the lab by TRANTYPE.jcl REPRO from `TRANTYPE.PS`. See [the link between tracks](#link-to-track-b-tranextr). |
| Type categories `TRANCATG.VSAM.KSDS` | [CVTRA04Y](../../../app/cpy/CVTRA04Y.cpy) · 60: TYPE-CD X(2), CAT-CD 9(4), DESC X(50), FILLER X(4); KEYS(6 0) | CBTRN03C keyed READ (R 505) | As above, from `TRANCATG.PS`. |
| Report `TRANREPT(+1)` | [CVTRA07Y](../../../app/cpy/CVTRA07Y.cpy) · 133 FB: name header, headers 1–2, detail with edited `-ZZZ,ZZZ,ZZZ.ZZ`, page/account/grand totals | CBTRN03C WRITE (R 345) | Line formatter with exact padding, literals, numeric editing, page break by line counter, account break by card, and the recorded EOF shape (no final account subtotal; p01 grand total −26.25). p07 golden is **zero bytes** although its README says headers. |
| Reject file `DALYREJS(+1)` | [REJTRAN.cpy](../../../tools/parity/layouts/REJTRAN.cpy) · 430 F: 350-byte input + 4-digit reason + 76-byte description | CBTRN02C WRITE (P 446–451) | Reasons 100, 101, 102, 103 (103 overwrites 102 when both fail, P 407–420); RC 4 when any reject. |
| Category balance create | CVTRA01Y | WRITE when key absent (P 510) | Keyed insert path (p06). |
| Date window | `DATEPARM` 80 bytes (R 221) and SORT `INCLUDE` on `TRAN-PROC-DT` 305,10,CH `GE 2022-01-01 AND LE 2022-07-06` (JR SYMNAMES) | Two independent gates | Both gates, as two filters, same bounds as recorded. |
| Sort | `SORT FIELDS=(TRAN-CARD-NUM,A)`, 263,16,**ZD** | TRANREPT STEP05R | Ascending ZD card order, input order kept on ties (SORT default stability needs evidence, annex proposal). |
| REPRO unload | `TRANSACT.VSAM.KSDS` → `TRANSACT.BKUP(+1)` | REPROC PRC001 | Key-order unload of the posted master as the sort input. |

Recorded cases: `p01-baseline-mixed` (RC 0/0), `p02-invalid-card` (4/0), `p03-account-missing` (4/0), `p04-overlimit-reject` (4/0), `p05-expired-account` (4/0), `p06-credits-new-category` (0/0), `p07-empty-day` (0/0). RC pitfall: `rc.json` lists the setup-job steps under POSTTRAN (`CLCIFIL`, `OPCIFIL`, `STEP05`–`STEP30`), as interest's golden did under INTCALC; the driver must run the real setup jobs and replace only STEP15 / STEP10R.

## Which stream updates the balances first

Both CBACT04C (interest) and CBTRN02C (posting) rewrite ACCTDATA, and they touch the same three fields in opposite directions: posting **adds** each amount to ACCT-CURR-BAL and to CYC-CREDIT or CYC-DEBIT (P 547–552); interest **adds** interest to ACCT-CURR-BAL and **sets both cycle totals to 0** (I 352–354). Interest reads TCATBALF; posting updates it (P 510, 528). The result of a cycle therefore depends on the order.

What `app/scheduler` proves:

* [CardDemo.ca7](../../../app/scheduler/CardDemo.ca7) has POSTTRAN as a triggered job (SCHID 030, lines 70–78) in a CLOSEFIL → … → WAITSTEP → OPENFIL sequence. INTCALC does not appear in it (0 matches). TRANREPT does not appear in either scheduler file.
* [CardDemo.controlm](../../../app/scheduler/CardDemo.controlm) has INTCALC in folder `MONTHLY-InterestCalculation` (line 69): CLOSEFIL → INTCALC → COMBTRAN → WAITSTEP → OPENFIL by IN/OUT conditions. POSTTRAN does not appear in it.
* No condition, trigger or resource links the two. **The order of posting and interest is not proven by app/scheduler.** The lab runs each chain alone from fresh fixtures, so no golden depends on the order.

Hand-off position (least change, legacy kept): convert each stream to reproduce its own chain; neither assumes the other ran first; no combined fixture. Open ask PA-1 and register row SH-02.

## Link to Track B (TRANEXTR)

[TRANEXTR.jcl](../../../app/app-transaction-type-db2/jcl/TRANEXTR.jcl) unloads the two tables to the files the TRANREPT chain reads, through the TRANTYPE/TRANCATG load jobs.

What the source proves:

| Step | Effect |
|---|---|
| STEP10 / STEP20 (IEBGENER) | Back up `TRANTYPE.PS` → `TRANTYPE.BKUP(+1)` and `TRANCATG.PS` → `TRANCATG.PS.BKUP(+1)`, LRECL 60 FB. |
| STEP30 (IEFBR14) | Delete `TRANTYPE.PS` and `TRANCATG.PS`; `COND=(0,NE)`. |
| STEP40 (DSNTIAUL) | `TRANTYPE.PS` ← `CAST(CONCAT(CONCAT(TR_TYPE, CAST(TR_DESCRIPTION AS CHAR(50))), REPEAT('0',8)) AS CHAR(60)) FROM CARDDEMO.TRANSACTION_TYPE ORDER BY TR_TYPE`: 2 + 50 + `00000000` = CVTRA03Y, FILLER = eight `0` characters. |
| STEP50 (DSNTIAUL), `COND=(4,LT)` | `TRANCATG.PS` ← `TRC_TYPE_CODE || TRC_TYPE_CATEGORY || CAST(TRC_CAT_DATA AS CHAR(50)) || REPEAT('0',4)`, ordered by code then category: 2 + 4 + 50 + `0000` = CVTRA04Y. |
| [TRANTYPE.jcl](../../../app/jcl/TRANTYPE.jcl) / [TRANCATG.jcl](../../../app/jcl/TRANCATG.jcl) | Define `TRANTYPE.VSAM.KSDS` KEYS(2 0) / `TRANCATG.VSAM.KSDS` KEYS(6 0), RECORDSIZE(60 60), and REPRO the `.PS` files into them; TRANREPT STEP10R reads these KSDS. |
| Control-M | TRANEXTR (folder `WEEKLY-DisclosureGroupsRefresh`, line 58) waits on `WEEKLY-TransactionTypesDBRefresh-MNTTRDB2`, the condition MNTTRDB2 sets. Its header says changes reach the report only after the daily batch. |

Not proven (open asks PA-2 to PA-4): TRANEXTR is in no lab chain and has no recorded output; nothing in either scheduler runs TRANTYPE/TRANCATG after TRANEXTR, or orders them before TRANREPT; how the 50-byte `VARCHAR` description is padded by `CAST AS CHAR(50)` on the host; DSNTIAUL output framing. Posting therefore treats CVTRA03Y/CVTRA04Y as **read-only fixture inputs** (`TRANTYPE.PS`, `TRANCATG.PS` in each case's `input/`) and takes no dependency on Track B code.

## Open asks

| ID | Ask | Owner |
|---|---|---|
| PA-1 | Production order of POSTTRAN and INTCALC on ACCTDATA/TCATBALF in a cycle, and the fence between them (SH-02). | Scheduler owner / Operations |
| PA-2 | Is TRANEXTR → TRANTYPE/TRANCATG → TRANREPT the production refresh path, and in what order and window? | Scheduler owner |
| PA-3 | A recorded TRANEXTR output (two 60-byte files) from known table content, to prove the CHAR(50) padding and `0` fillers. | Transaction-reference owner |
| PA-4 | Equal-card order after SORT (stability), and invalid/out-of-range dates at the two gates. | Host data / Operations |
| PA-5 | p07 README says headers are written; the golden report is zero bytes. The golden governs; fix the wording with the fixture owner. | Fixture owner |
| PA-6 | Recovery after partial writes: CBTRN02C writes TRANSACT, then category and account, with no checkpoint (P 440–442, 508–564). | Operations / architect |
| PA-7 | Rule-ID namespace and decision register for posting (none exists). | Business analyst |
