# Intake: CardDemo legacy estate

Step 1 of the modernization lab. This file records what the repository supplies, what it does not, and what runs today. Nothing here is inferred beyond the cited sources.

## Baseline

| Item | Value |
|---|---|
| Repository | `TCS-Devin-Sandbox/ts-cobol-carddemo` |
| Baseline branch | `main` |
| Baseline SHA | `922b1c23fb361b768d03f2b2c4d1e429e2fbbef0` |
| Baseline date | 2026-11-19T13:53:48+05:30 |
| How the baseline got there | PR [#5](https://github.com/TCS-Devin-Sandbox/ts-cobol-carddemo/pull/5) (merged by a person) added the legacy estate harness and five estate members from `Cognition-Partner-Workshops/ts-cobol-carddemo` at `a5da4ee6bb4261392ac0a44a0756b82914b97fe8` |
| Working branch | `demo/01-intake`, created from the baseline SHA |
| Estate root analyzed | `app` (the only root) |
| Scanner | mftb 0.2.1, plugin `mftb`, entry point `estate-analysis`; store in `./store` |
| Rules followed | `AGENTS.md` read first; nothing under `app/` or `fixtures/` was edited |

## What the estate supplies, by category

Counts are from `store/catalog.json` (245 files under `app/`, 0 excluded). The catalogue types each file by its content.

| Category | Files | Where | Status |
|---|---|---|---|
| BMS mapsets | 21 | `app/app-authorization-ims-db2-mq/bms/` 2, `app/app-transaction-type-db2/bms/` 2, `app/bms/` 17 | supplied |
| Binary files (EBCDIC data, images) | 14 | `app/app-authorization-ims-db2-mq/data/EBCDIC/` 1, `app/data/EBCDIC/` 13 | supplied |
| CICS resource definitions (CSD) | 4 | `app/app-authorization-ims-db2-mq/csd/` 1, `app/app-transaction-type-db2/csd/` 1, `app/app-vsam-mq/csd/` 1, `app/csd/` 1 | supplied |
| COBOL copybooks (incl. BMS symbolic maps and DCLGEN) | 59 | `app/app-authorization-ims-db2-mq/cpy/` 9, `app/app-authorization-ims-db2-mq/cpy-bms/` 2, `app/app-authorization-ims-db2-mq/dcl/` 1, `app/app-transaction-type-db2/cpy/` 1, `app/app-transaction-type-db2/cpy-bms/` 2, `app/app-transaction-type-db2/dcl/` 2, `app/cpy/` 25, `app/cpy-bms/` 17 | supplied |
| COBOL programs | 46 | `app/app-authorization-ims-db2-mq/cbl/` 8, `app/app-transaction-type-db2/cbl/` 4, `app/app-vsam-mq/cbl/` 2, `app/cbl/` 32 | supplied |
| Control cards | 5 | `app/app-transaction-type-db2/ctl/` 4, `app/ctl/` 1 | supplied |
| Db2 DDL | 4 | `app/app-authorization-ims-db2-mq/ddl/` 1, `app/app-transaction-type-db2/ctl/` 1, `app/app-transaction-type-db2/ddl/` 2 | supplied |
| Db2 seed data | 2 | `app/app-transaction-type-db2/ctl/` 2 | supplied |
| Documents | 12 | `app/app-authorization-ims-db2-mq/` 1, `app/app-transaction-type-db2/` 1, `app/app-vsam-mq/` 1, `app/data/ASCII/` 9 | supplied |
| Empty files | 3 | `app/cpy-bms/` 1, `app/csd/` 1, `app/data/EBCDIC/` 1 | supplied |
| HLASM DSECT | 1 | `app/maclib/` 1 | supplied |
| HLASM macros | 1 | `app/maclib/` 1 | supplied |
| HLASM programs | 2 | `app/asm/` 2 | supplied |
| IMS DBD | 4 | `app/app-authorization-ims-db2-mq/ims/` 4 | supplied |
| IMS PSB | 4 | `app/app-authorization-ims-db2-mq/ims/` 4 | supplied |
| JCL jobs | 48 | `app/app-authorization-ims-db2-mq/jcl/` 5, `app/app-transaction-type-db2/jcl/` 4, `app/jcl/` 39 | supplied |
| JCL procedures | 2 | `app/proc/` 2 | supplied |
| PL/I source | 1 | `app/pli/` 1 | supplied |
| SQL / catalogue listings | 2 | `app/app-transaction-type-db2/cpy/` 1, `app/catlg/` 1 | supplied |
| Scheduler exports | 2 | `app/scheduler/` 2 | supplied |
| Unclassified by the scanner | 8 | `app/app-authorization-ims-db2-mq/ddl/` 1, `app/app-transaction-type-db2/ddl/` 2, `app/cpy/` 5 | supplied |

Module map (`store/modules.json`, frozen; `mftb modules --check` passes: 208 placeable members, 8 shared, 0 unplaced):

| Module | Name | Members | Evidence |
|---|---|---|---|
| CARDDEMO | CardDemo base application | 134 | `app/cbl/`, `app/jcl/`, `app/csd/CARDDEMO.CSD:1`, `app/scheduler/CardDemo.controlm:4`, `app/scheduler/CardDemo.ca7:18` |
| AUTH | Pending authorization (IMS, Db2, MQ) | 37 | `app/app-authorization-ims-db2-mq/`, `app/app-authorization-ims-db2-mq/csd/CRDDEMO2.csd:11`, `app/app-authorization-ims-db2-mq/README.md:1`, `app/scheduler/CardDemo.ca7:43` |
| TRANTYPE | Transaction type maintenance (Db2) | 26 | `app/app-transaction-type-db2/`, `app/app-transaction-type-db2/csd/CRDDEMOD.csd:11`, `app/app-transaction-type-db2/README.md:1`, `app/scheduler/CardDemo.controlm:27` |
| VSAMMQ | Account and date inquiry over MQ (VSAM) | 3 | `app/app-vsam-mq/`, `app/app-vsam-mq/csd/CRDDEMOM.csd:1`, `app/app-vsam-mq/README.md:1` |

Shared copybooks (base layouts that sub-application programs also copy): `COCOM01Y`, `CSDAT01Y`, `CSUSR01Y`, `CVACT01Y`, `CVACT02Y`, `CVACT03Y`, `CVCRD01Y`, `CVCUS01Y`.

Outside `app/` the repository also supplies the off-host harness: `Makefile`, `Dockerfile`, `docker-compose.yml`, `tools/` (build, JCL runner, parity, chain-graph generator, stubs), `fixtures/` (golden cases for four chains), `db/postgres/` and `docs/`. It also carries modernized transaction-type services (`carddemo-trantype-api`, `carddemo-trantype-ui`, `modernized-trantype`, `db/migration`) that were on `main` before this step; they are not part of the legacy estate and were not analyzed.

## What is absent or cannot run off-host

Each row was checked against the source, not only against the documents.

| Category | Status | Evidence in the source | What the documents say |
|---|---|---|---|
| CICS-supplied copybooks `DFHAID`, `DFHBMSCA` | **MISSING** | No file of either name anywhere in the repository (`find`); 21 online programs copy them, e.g. `app/cbl/COSGN00C.cbl:57`, `:58` | `docs/BASELINE.md`: programs fail with `DFHBMSCA: No such file or directory`. Agrees. |
| CICS region, BMS macro assembler | **MISSING** (cannot run off-host) | No CICS runtime in `Dockerfile` or `tools/`; `tools/build.sh` compiles batch programs only | `docs/BASELINE.md`, `AGENTS.md`: CICS programs are analysis-only. Agrees. |
| Symbolic maps for the base BMS mapsets | supplied in `app/cpy-bms/` (17 members) but not used by the build | `tools/build.sh:12` copies only `app/cpy/*.cpy` into the compile path | `docs/BASELINE.md` says the symbolic maps are missing. **Differs:** they are in the repository; the build does not put them on the copy path. |
| HLASM assembler | **MISSING** (cannot run off-host) | `app/asm/COBDATFT.asm`, `app/asm/MVSWAIT.asm` are source only; callers at `app/cbl/CBACT01C.cbl:231`, `app/cbl/COBSWAIT.cbl:38` | `docs/LEGACY-LANGUAGES.md`: analysis-only. Agrees. |
| PL/I compiler | **MISSING** (cannot run off-host) | `app/pli/CBFEECAL.pli` is source only; caller at `app/cbl/CBFEEDRV.cbl:101`; job `app/jcl/FEECALC.jcl:24` | `docs/LEGACY-LANGUAGES.md`: analysis-only, `CBFEEDRV` compiles but cannot run. Agrees. |
| Copybook declaring `EXPORT-SEQUENCE-NUM` | **MISSING** | Referenced at `app/cbl/CBEXPORT.cbl:68` and `app/cbl/CBIMPORT.cbl:40`; declared in no member under `app/` (`grep`) | `docs/BASELINE.md`: CBEXPORT and CBIMPORT do not compile. Agrees; the build log below shows both skipped. |
| Db2 for z/OS | **MISSING**; replaced by PostgreSQL + Open COBOL ESQL for two batch programs | `docker-compose.yml` services `db` and `estate`; `tools/esql_prep.py` | `docs/BASELINE.md`. Agrees. |
| IMS (DB/DC), DL/I | **MISSING** (cannot run off-host) | Source only: 4 DBD, 4 PSB, 8 programs under `app/app-authorization-ims-db2-mq/`; no IMS stand-in in `tools/` | Not covered by `docs/BASELINE.md` (the document lists no AUTH program). |
| IBM MQ | **MISSING** (cannot run off-host) | MQ calls in `app/app-vsam-mq/cbl/COACCT01.cbl:352` and `app/app-authorization-ims-db2-mq/cbl/COPAUA0C.cbl:400`; no queue manager or queue definitions in the repository | Not covered by `docs/BASELINE.md`. |
| Scheduler runtime (CA 7, Control-M) | **MISSING**; exports only | `app/scheduler/CardDemo.ca7`, `app/scheduler/CardDemo.controlm` | Not covered. |
| Load modules, DBRMs, bind / plan definitions, compile and link JCL | **MISSING** | Jobs name `AWS.M2.CARDDEMO.LOADLIB` (e.g. `app/jcl/POSTTRAN.jcl:24`) and plan `CARDDEMO` (`app/app-transaction-type-db2/jcl/MNTTRDB2.jcl`); neither is in the repository | Not covered. |
| Security definitions (RACF or other), CICS SIT, region setup | **MISSING** | Nothing of this kind under `app/` | Not covered. |
| Production data and volumes | **MISSING** | `app/data/ASCII` (9 files) and `app/data/EBCDIC` (13 files) are sample data; `AGENTS.md` states synthetic data only | `AGENTS.md`. Agrees. |
| Natural / NatStar / Adabas | not present | No such artefact under `app/` (`grep -ri natstar\|natural\|adabas` finds none) | `docs/LEGACY-LANGUAGES.md`: none. Agrees. |

### Where the documents and the source disagree

| Document statement | Source | Finding |
|---|---|---|
| `docs/BASELINE.md`: "All 19 online programs fail" | 17 members under `app/cbl` contain `EXEC CICS`; the document's own table names 17. `app/cbl/CO*` matches 18 files because `COBSWAIT.cbl` is a batch program | Count differs: 17, not 19 |
| `docs/BASELINE.md`: DCLGEN members `DCLTRTYP`, `DCLTRTCT` | `app/app-transaction-type-db2/dcl/` holds `DCLTRTYP.dcl` and `DCLTRCAT.dcl` | Name differs: `DCLTRCAT`, not `DCLTRTCT` |
| `docs/BASELINE.md`: CBSTM03A / CBSTM03B "needs `COBSWAIT`"; `docs/LEGACY-LANGUAGES.md`: COBSWAIT is "run from `CBSTM03A/B` JCL" | The only reference to `COBSWAIT` is `app/jcl/WAITSTEP.jcl:22` (`EXEC PGM=COBSWAIT`); the scan finds no CALL from CBSTM03A or CBSTM03B to it, and `app/jcl/CREASTMT.JCL` does not run it | Not supported by the source |
| `docs/BASELINE.md`: CBSTM03A / CBSTM03B compile ("yes") | `tools/build.sh:31` builds `app/cbl/CB*.cbl`; the two members are `CBSTM03A.CBL` and `CBSTM03B.CBL` (upper-case extension) and do not appear in the build log below | Not proven by the build: neither member is compiled by `make build` |
| `docs/BASELINE.md`: symbolic maps missing | `app/cpy-bms/` holds 17 symbolic map copybooks | Present in the repository; not on the build's copy path |
| `docs/chain-graph.md` | See [call-graph.md](legacy/call-graph.md#disagreements-with-docschain-graphmd) | 26 differences |

## What runs today

Command run on the baseline (`922b1c23fb36`), from the repository root: `make up && make build && make parity`. Exit code 0. Full cleaned log: [evidence/parity-baseline.log](evidence/parity-baseline.log) (terminal control codes and Docker progress lines removed, nothing else changed).

```text
$ make up && make build && make parity
docker compose exec -T estate tools/build.sh
CBACT01C: compiled
CBACT02C: compiled
CBACT03C: compiled
CBACT04C: compiled
CBCUS01C: compiled
CBEXPORT: skipped (known GnuCOBOL failure, docs/BASELINE.md)
CBFEEDRV: compiled
CBIMPORT: skipped (known GnuCOBOL failure, docs/BASELINE.md)
CBTRN01C: compiled
CBTRN02C: compiled
CBTRN03C: compiled
CSUTLDTC: compiled
COBSWAIT: compiled
PARMDRV: compiled
CEE3ABD: compiled
esql_prep: app/app-transaction-type-db2/dcl/DCLTRCAT.dcl: removed EXEC SQL DECLARE ... TABLE
esql_prep: app/app-transaction-type-db2/dcl/DCLTRTYP.dcl: removed EXEC SQL DECLARE ... TABLE
esql_prep: app/app-transaction-type-db2/cbl/CBTRNDYN.cbl: columns 73-80 only
esql_prep: app/app-transaction-type-db2/cbl/COBTUPDT.cbl: joined multi-line INCLUDE SQLCA
CBTRNDYN: precompiled (ocesql) and compiled
COBTUPDT: precompiled (ocesql) and compiled
DSNEXIT: precompiled (ocesql) and compiled
DSNRUN: precompiled (ocesql) and compiled
posting/p01-baseline-mixed [legacy rerun]: PASS (23 records, 92 fields, 0 differences)
posting/p02-invalid-card [legacy rerun]: PASS (12 records, 44 fields, 0 differences)
posting/p03-account-missing [legacy rerun]: PASS (12 records, 44 fields, 0 differences)
posting/p04-overlimit-reject [legacy rerun]: PASS (12 records, 44 fields, 0 differences)
posting/p05-expired-account [legacy rerun]: PASS (14 records, 60 fields, 0 differences)
posting/p06-credits-new-category [legacy rerun]: PASS (18 records, 67 fields, 0 differences)
posting/p07-empty-day [legacy rerun]: PASS (2 records, 16 fields, 0 differences)
interest/i01-baseline [legacy rerun]: PASS (4 records, 50 fields, 0 differences)
interest/i02-tie-half-cent [legacy rerun]: PASS (4 records, 50 fields, 0 differences)
interest/i03-zero-rate-skipped [legacy rerun]: PASS (2 records, 25 fields, 0 differences)
interest/i04-default-group-fallback [legacy rerun]: PASS (2 records, 25 fields, 0 differences)
interest/i05-multi-category [legacy rerun]: PASS (3 records, 38 fields, 0 differences)
interest/i06-negative-balance [legacy rerun]: PASS (2 records, 25 fields, 0 differences)
interest/i07-nontie-rounding [legacy rerun]: PASS (4 records, 50 fields, 0 differences)
trantype-update/t01-add [legacy rerun]: PASS (0 records, 0 fields, 0 differences)
trantype-update/t02-update [legacy rerun]: PASS (0 records, 0 fields, 0 differences)
trantype-update/t03-delete [legacy rerun]: PASS (0 records, 0 fields, 0 differences)
trantype-update/t04-comments [legacy rerun]: PASS (0 records, 0 fields, 0 differences)
trantype-update/t05-delete-missing [legacy rerun]: PASS (0 records, 0 fields, 0 differences)
trantype-update/t06-duplicate-add [legacy rerun]: PASS (0 records, 0 fields, 0 differences)
trantype-update/t07-mixed-batch [legacy rerun]: PASS (0 records, 0 fields, 0 differences)
trantype-dynamic/d01-update-then-apply [legacy rerun]: PASS (0 records, 0 fields, 0 differences)
trantype-dynamic/d02-delete-statement [legacy rerun]: PASS (0 records, 0 fields, 0 differences)
trantype-dynamic/d03-bad-statement [legacy rerun]: PASS (0 records, 0 fields, 0 differences)
report: work/parity/report.md
EXIT=0

```

What this proves, and no more:

- 11 members of `app/cbl` compile with GnuCOBOL 3.1.2 (CBACT01C, CBACT02C, CBACT03C, CBACT04C, CBCUS01C, CBFEEDRV, CBTRN01C, CBTRN02C, CBTRN03C, CSUTLDTC, COBSWAIT); CBEXPORT and CBIMPORT are skipped; CBSTM03A and CBSTM03B are not attempted (see above).
- 2 Db2 batch programs (CBTRNDYN, COBTUPDT) precompile with Open COBOL ESQL and compile; 4 harness stubs (PARMDRV, CEE3ABD, DSNEXIT, DSNRUN) compile. The stubs are test harness, not estate code.
- Four chains run and match their golden files: `posting` (7 cases), `interest` (7), `trantype-update` (7), `trantype-dynamic` (3): 24 cases, 0 differences.
- Nothing else is proven to run: no CICS program, no IMS or MQ program, no HLASM or PL/I member, and none of the other JCL jobs.

## Scanner cross-check (Part C)

Scanner results compared with the source line they cite. The source line is quoted as it stands at the baseline.

| # | Scanner result | From | File and line | Source line | Check |
|---|---|---|---|---|---|
| 1 | Catalogue: `COBTUPDT.cbl` is a COBOL program, PROGRAM-ID COBTUPDT | `store/catalog.json` | `app/app-transaction-type-db2/cbl/COBTUPDT.cbl:23` | `PROGRAM-ID. COBTUPDT.` | agrees |
| 2 | Catalogue ask: `COACTUPC.cbl` has an IDENTIFICATION DIVISION but no readable PROGRAM-ID | `store/catalog.json (asks)` | `app/cbl/COACTUPC.cbl:21` | `IDENTIFICATION DIVISION.` | agrees |
| 3 | Program edge: CBACT01C CALL COBDATFT (leaf-HLASM) | `store/CARDDEMO/traces.json` | `app/cbl/CBACT01C.cbl:231` | `CALL 'COBDATFT'       USING CODATECN-REC.` | agrees |
| 4 | Program edge: COBSWAIT CALL MVSWAIT (leaf-HLASM) | `store/CARDDEMO/traces.json` | `app/cbl/COBSWAIT.cbl:38` | `CALL 'MVSWAIT'       USING MVSWAIT-TIME.` | agrees |
| 5 | Program edge: CBFEEDRV CALL CBFEECAL (leaf-PL/I) | `store/CARDDEMO/traces.json` | `app/cbl/CBFEEDRV.cbl:101` | `CALL 'CBFEECAL' USING BY REFERENCE FEE-REQ` | agrees |
| 6 | Program edge: CBSTM03A CALL CBSTM03B (program) | `store/CARDDEMO/traces.json` | `app/cbl/CBSTM03A.CBL:351` | `CALL 'CBSTM03B' USING WS-M03B-AREA.` | agrees |
| 7 | Program edge: COSGN00C CICS XCTL COADM01C, static | `store/CARDDEMO/traces.json` | `app/cbl/COSGN00C.cbl:231` | `EXEC CICS XCTL` | agrees |
| 8 | Job step: POSTTRAN STEP15 EXEC PGM=CBTRN02C | `store/CARDDEMO/jobs.json` | `app/jcl/POSTTRAN.jcl:23` | `//STEP15 EXEC PGM=CBTRN02C` | agrees |
| 9 | Job step: MNTTRDB2 STEP1 runs IKJEFT01, unwrapped to COBTUPDT | `store/TRANTYPE/jobs.json` | `app/app-transaction-type-db2/jcl/MNTTRDB2.jcl:21` | `//STEP1   EXEC PGM=IKJEFT01,REGION=0M` | agrees |
| 10 | File: CBTRN02C SELECT DALYREJS-FILE ASSIGN TO DALYREJS, opened for output | `store/CARDDEMO/connections.json` | `app/cbl/CBTRN02C.cbl:46` | `SELECT DALYREJS-FILE ASSIGN TO DALYREJS` | agrees |
| 11 | DD binding: POSTTRAN DD DALYREJS is GDG generation `AWS.M2.CARDDEMO.DALYREJS(+1)` | `store/CARDDEMO/connections.json` | `app/jcl/POSTTRAN.jcl:34` | `//DALYREJS DD DISP=(NEW,CATLG,DELETE), UNIT=SYSDA, DCB=(RECFM=F,LRECL=430,BLKSIZE=0), SPACE=(CYL,(1,1),RLSE), DSN=AWS.M2.CARDDEMO.DALYREJS(+1)` | agrees |
| 12 | Dataset: `AWS.M2.CARDDEMO.CARDDATA.VSAM.KSDS` is a VSAM KSDS defined by job CARDFILE (keys 16 0) | `store/CARDDEMO/connections.json` | `app/jcl/CARDFILE.jcl:50` | `DEFINE CLUSTER (NAME(AWS.M2.CARDDEMO.CARDDATA.VSAM.KSDS) -` | agrees |
| 13 | Transaction: CA00 starts program COADM01C | `store/CARDDEMO/online.json` | `app/csd/CARDDEMO.CSD:327` | `DEFINE TRANSACTION(CA00) GROUP(CARDDEMO)` | agrees |
| 14 | CICS file: COACTUPC updates ACCTDAT (REWRITE) | `store/CARDDEMO/connections.json` | `app/cbl/COACTUPC.cbl:4065` | `EXEC CICS` | agrees |
| 15 | CSD: file ACCTDAT is dataset `AWS.M2.CARDDEMO.ACCTDATA.VSAM.KSDS` | `store/CARDDEMO/connections.json` | `app/csd/CARDDEMO.CSD:1` | `DEFINE FILE(ACCTDAT) GROUP(CARDDEMO)` | agrees |
| 16 | Db2: COBTUPDT INSERTs into `CARDDEMO.TRANSACTION_TYPE` | `store/TRANTYPE/connections.json` | `app/app-transaction-type-db2/cbl/COBTUPDT.cbl:137-148` | `EXEC SQL` | agrees |
| 17 | IMS: CBPAUP0C deletes segment PAUTDTL1 (EXEC DLI DLET) | `store/AUTH/connections.json` | `app/app-authorization-ims-db2-mq/cbl/CBPAUP0C.cbl:310` | `EXEC DLI DLET USING PCB(PAUT-PCB-NUM)` | agrees |
| 18 | MQ: COPAUA0C calls MQGET | `store/AUTH/connections.json` | `app/app-authorization-ims-db2-mq/cbl/COPAUA0C.cbl:400` | `CALL 'MQGET' USING W01-HCONN-REQUEST` | agrees |
| 19 | Scheduler: CA 7 table CARDDEMO orders job CBPAUP0J | `store/CARDDEMO/schedule.json` | `app/scheduler/CardDemo.ca7:43` | `JOB=CBPAUP0J SCHID=030      QTM=0100 LEADTM=0000 SUBMTM=` | agrees |
| 20 | Copybook: COPAUA0C (AUTH) copies base copybook CVACT03Y | `store/AUTH/programs.json` | `app/app-authorization-ims-db2-mq/cbl/COPAUA0C.cbl:203` | `COPY CVACT03Y.` | agrees |
| 21 | Dynamic call: CBTRNDYN CALL WS-PGM-NAME (operand is a data item) | `store/TRANTYPE/dyncalls.json` | `app/app-transaction-type-db2/cbl/CBTRNDYN.cbl:62` | `CALL WS-PGM-NAME` | agrees |
| 22 | Scanner: copybook `CSSTRPFY` is copied by no program (no COPY recorded in any `programs.json`) | `store/CARDDEMO/programs.json` | `app/cbl/COACTVWC.cbl:913` | `COPY 'CSSTRPFY'` | DIFFERS |
| 23 | Scanner: copybook `CSUTLDWY` is copied by no program (no COPY recorded in any `programs.json`) | `store/CARDDEMO/programs.json` | `app/cbl/COACTUPC.cbl:166` | `COPY 'CSUTLDWY'.` | DIFFERS |

Rows marked DIFFERS: the scanner does not read `COPY 'NAME'` with the member name in quotes. `CSSTRPFY` is copied that way at `app/cbl/COACTUPC.cbl:4199`, `app/cbl/COACTVWC.cbl:913`, `app/cbl/COCRDLIC.cbl:1416`, `app/cbl/COCRDSLC.cbl:855` and `app/cbl/COCRDUPC.cbl:1528`, and `CSUTLDWY` at `app/cbl/COACTUPC.cbl:166`. These COPY edges are therefore missing from the scan results and from the copybook table of the call graph; the call graph lists both members as unreferenced with the text-search hit beside them. Nothing was added by hand.

Row 2 needs a note: the scanner is right that line 21 is the IDENTIFICATION DIVISION header, but its ask says the member has no readable PROGRAM-ID. In the source, `PROGRAM-ID.` is on `app/cbl/COACTUPC.cbl:22` and the name `COACTUPC.` is on line 23. The same two-line form is in the other five members with this ask. The ask stays open below; this observation is for whoever owns it.

## Open questions (every catalogue ask)

All 30 asks of `store/catalog.json`, unchanged and unresolved. Owner is a placeholder until a person is named.

| # | Kind | Severity | Evidence | What the scanner says | Question | Owner | Status |
|---|---|---|---|---|---|---|---|
| A01 | catalog-unclassified | high | `app/app-authorization-ims-db2-mq/ddl/XAUTHFRD.ddl` | app/app-authorization-ims-db2-mq/ddl/XAUTHFRD.ddl matches no artifact signature: the sweep cannot say what it is | What is this member (language / artifact type), or may it be excluded from the estate? | <owner: TBD> | open |
| A02 | catalog-no-program-id | medium | `app/app-transaction-type-db2/cbl/COTRTLIC.cbl:24` | app/app-transaction-type-db2/cbl/COTRTLIC.cbl has an IDENTIFICATION DIVISION but no readable PROGRAM-ID | Is the member complete? The sweep catalogues it under its file name. | <owner: TBD> | open |
| A03 | catalog-unclassified | high | `app/app-transaction-type-db2/ddl/XTRNTYCAT.ddl` | app/app-transaction-type-db2/ddl/XTRNTYCAT.ddl matches no artifact signature: the sweep cannot say what it is | What is this member (language / artifact type), or may it be excluded from the estate? | <owner: TBD> | open |
| A04 | catalog-unclassified | high | `app/app-transaction-type-db2/ddl/XTRNTYPE.ddl` | app/app-transaction-type-db2/ddl/XTRNTYPE.ddl matches no artifact signature: the sweep cannot say what it is | What is this member (language / artifact type), or may it be excluded from the estate? | <owner: TBD> | open |
| A05 | catalog-assembler | medium | `app/asm/COBDATFT.asm:17` | app/asm/COBDATFT.asm is HLASM: the sweep has its sections, its entry points and the names it reaches, but mftb has no assembler analyser, so its logic is not inventoried | Is this member in scope for the migration, and who can read it? Its behaviour (and any transfer the sweep listed as unresolved) needs a human. | <owner: TBD> | open |
| A06 | catalog-assembler | medium | `app/asm/MVSWAIT.asm:17` | app/asm/MVSWAIT.asm is HLASM: the sweep has its sections, its entry points and the names it reaches, but mftb has no assembler analyser, so its logic is not inventoried | Is this member in scope for the migration, and who can read it? Its behaviour (and any transfer the sweep listed as unresolved) needs a human. | <owner: TBD> | open |
| A07 | catalog-no-program-id | medium | `app/cbl/COACTUPC.cbl:21` | app/cbl/COACTUPC.cbl has an IDENTIFICATION DIVISION but no readable PROGRAM-ID | Is the member complete? The sweep catalogues it under its file name. | <owner: TBD> | open |
| A08 | catalog-no-program-id | medium | `app/cbl/COACTVWC.cbl:21` | app/cbl/COACTVWC.cbl has an IDENTIFICATION DIVISION but no readable PROGRAM-ID | Is the member complete? The sweep catalogues it under its file name. | <owner: TBD> | open |
| A09 | catalog-no-program-id | medium | `app/cbl/COCRDLIC.cbl:25` | app/cbl/COCRDLIC.cbl has an IDENTIFICATION DIVISION but no readable PROGRAM-ID | Is the member complete? The sweep catalogues it under its file name. | <owner: TBD> | open |
| A10 | catalog-no-program-id | medium | `app/cbl/COCRDSLC.cbl:22` | app/cbl/COCRDSLC.cbl has an IDENTIFICATION DIVISION but no readable PROGRAM-ID | Is the member complete? The sweep catalogues it under its file name. | <owner: TBD> | open |
| A11 | catalog-no-program-id | medium | `app/cbl/COCRDUPC.cbl:22` | app/cbl/COCRDUPC.cbl has an IDENTIFICATION DIVISION but no readable PROGRAM-ID | Is the member complete? The sweep catalogues it under its file name. | <owner: TBD> | open |
| A12 | catalog-unclassified | high | `app/cpy/COTTL01Y.cpy` | app/cpy/COTTL01Y.cpy matches no artifact signature: the sweep cannot say what it is | What is this member (language / artifact type), or may it be excluded from the estate? | <owner: TBD> | open |
| A13 | catalog-unclassified | high | `app/cpy/CSLKPCDY.cpy` | app/cpy/CSLKPCDY.cpy matches no artifact signature: the sweep cannot say what it is | What is this member (language / artifact type), or may it be excluded from the estate? | <owner: TBD> | open |
| A14 | catalog-unclassified | high | `app/cpy/CSMSG01Y.cpy` | app/cpy/CSMSG01Y.cpy matches no artifact signature: the sweep cannot say what it is | What is this member (language / artifact type), or may it be excluded from the estate? | <owner: TBD> | open |
| A15 | catalog-unclassified | high | `app/cpy/CSMSG02Y.cpy` | app/cpy/CSMSG02Y.cpy matches no artifact signature: the sweep cannot say what it is | What is this member (language / artifact type), or may it be excluded from the estate? | <owner: TBD> | open |
| A16 | catalog-unclassified | high | `app/cpy/CSSETATY.cpy` | app/cpy/CSSETATY.cpy matches no artifact signature: the sweep cannot say what it is | What is this member (language / artifact type), or may it be excluded from the estate? | <owner: TBD> | open |
| A17 | catalog-untranslated | medium | `app/data/EBCDIC/AWS.M2.CARDDEMO.ACCDATA.PS` | app/data/EBCDIC/AWS.M2.CARDDEMO.ACCDATA.PS does not decode as UTF-8: EBCDIC data or a binary carried in the estate | Is this data in scope? If so, supply it translated (or its record layout) — the sweep lists it but cannot read it. | <owner: TBD> | open |
| A18 | catalog-untranslated | medium | `app/data/EBCDIC/AWS.M2.CARDDEMO.ACCTDATA.PS` | app/data/EBCDIC/AWS.M2.CARDDEMO.ACCTDATA.PS does not decode as UTF-8: EBCDIC data or a binary carried in the estate | Is this data in scope? If so, supply it translated (or its record layout) — the sweep lists it but cannot read it. | <owner: TBD> | open |
| A19 | catalog-untranslated | medium | `app/data/EBCDIC/AWS.M2.CARDDEMO.CARDDATA.PS` | app/data/EBCDIC/AWS.M2.CARDDEMO.CARDDATA.PS does not decode as UTF-8: EBCDIC data or a binary carried in the estate | Is this data in scope? If so, supply it translated (or its record layout) — the sweep lists it but cannot read it. | <owner: TBD> | open |
| A20 | catalog-untranslated | medium | `app/data/EBCDIC/AWS.M2.CARDDEMO.CARDXREF.PS` | app/data/EBCDIC/AWS.M2.CARDDEMO.CARDXREF.PS does not decode as UTF-8: EBCDIC data or a binary carried in the estate | Is this data in scope? If so, supply it translated (or its record layout) — the sweep lists it but cannot read it. | <owner: TBD> | open |
| A21 | catalog-untranslated | medium | `app/data/EBCDIC/AWS.M2.CARDDEMO.CUSTDATA.PS` | app/data/EBCDIC/AWS.M2.CARDDEMO.CUSTDATA.PS does not decode as UTF-8: EBCDIC data or a binary carried in the estate | Is this data in scope? If so, supply it translated (or its record layout) — the sweep lists it but cannot read it. | <owner: TBD> | open |
| A22 | catalog-untranslated | medium | `app/data/EBCDIC/AWS.M2.CARDDEMO.DALYTRAN.PS` | app/data/EBCDIC/AWS.M2.CARDDEMO.DALYTRAN.PS does not decode as UTF-8: EBCDIC data or a binary carried in the estate | Is this data in scope? If so, supply it translated (or its record layout) — the sweep lists it but cannot read it. | <owner: TBD> | open |
| A23 | catalog-untranslated | medium | `app/data/EBCDIC/AWS.M2.CARDDEMO.DISCGRP.PS` | app/data/EBCDIC/AWS.M2.CARDDEMO.DISCGRP.PS does not decode as UTF-8: EBCDIC data or a binary carried in the estate | Is this data in scope? If so, supply it translated (or its record layout) — the sweep lists it but cannot read it. | <owner: TBD> | open |
| A24 | catalog-untranslated | medium | `app/data/EBCDIC/AWS.M2.CARDDEMO.TCATBALF.PS` | app/data/EBCDIC/AWS.M2.CARDDEMO.TCATBALF.PS does not decode as UTF-8: EBCDIC data or a binary carried in the estate | Is this data in scope? If so, supply it translated (or its record layout) — the sweep lists it but cannot read it. | <owner: TBD> | open |
| A25 | catalog-untranslated | medium | `app/data/EBCDIC/AWS.M2.CARDDEMO.TRANCATG.PS` | app/data/EBCDIC/AWS.M2.CARDDEMO.TRANCATG.PS does not decode as UTF-8: EBCDIC data or a binary carried in the estate | Is this data in scope? If so, supply it translated (or its record layout) — the sweep lists it but cannot read it. | <owner: TBD> | open |
| A26 | catalog-untranslated | medium | `app/data/EBCDIC/AWS.M2.CARDDEMO.TRANTYPE.PS` | app/data/EBCDIC/AWS.M2.CARDDEMO.TRANTYPE.PS does not decode as UTF-8: EBCDIC data or a binary carried in the estate | Is this data in scope? If so, supply it translated (or its record layout) — the sweep lists it but cannot read it. | <owner: TBD> | open |
| A27 | catalog-untranslated | medium | `app/data/EBCDIC/AWS.M2.CARDDEMO.USRSEC.PS` | app/data/EBCDIC/AWS.M2.CARDDEMO.USRSEC.PS does not decode as UTF-8: EBCDIC data or a binary carried in the estate | Is this data in scope? If so, supply it translated (or its record layout) — the sweep lists it but cannot read it. | <owner: TBD> | open |
| A28 | catalog-assembler | medium | `app/maclib/ASMWAIT.mac:17` | app/maclib/ASMWAIT.mac is HLASM: the sweep has its sections, its entry points and the names it reaches, but mftb has no assembler analyser, so its logic is not inventoried | Is this member in scope for the migration, and who can read it? Its behaviour (and any transfer the sweep listed as unresolved) needs a human. | <owner: TBD> | open |
| A29 | catalog-assembler | medium | `app/maclib/COCDATFT.mac:17` | app/maclib/COCDATFT.mac is HLASM: the sweep has its sections, its entry points and the names it reaches, but mftb has no assembler analyser, so its logic is not inventoried | Is this member in scope for the migration, and who can read it? Its behaviour (and any transfer the sweep listed as unresolved) needs a human. | <owner: TBD> | open |
| A30 | catalog-unsupported-language | medium | `app/pli/CBFEECAL.pli:21` | app/pli/CBFEECAL.pli is IBM Enterprise PL/I source, which mftb does not parse: it is catalogued, not analysed | Is this member in scope for the migration? If so its analysis needs a parser mftb does not have, or a human reading. | <owner: TBD> | open |

Further open asks, also unresolved:

- Estate level: `store/estate.json` lists 47 open asks (the 30 above plus site and environment questions); see `store/reports/estate.html`.
- Module level: open findings per module are in `store/<MODULE>/findings.json` and counted in [legacy/fan-out.md](legacy/fan-out.md).
- Dynamic targets: 31 open asks in [legacy/call-graph.md](legacy/call-graph.md#open-asks), each with an owner placeholder.

## Related outputs

- [legacy/fan-out.md](legacy/fan-out.md): the module fan-out (Part B).
- [legacy/call-graph.md](legacy/call-graph.md), [legacy/call-graph.html](legacy/call-graph.html): the whole-estate call graph (Part D).
- [legacy/architecture-as-is.md](legacy/architecture-as-is.md), [legacy/architecture-as-is.html](legacy/architecture-as-is.html): the as-is architecture (Part E).
- `store/reports/estate.html` and `store/reports/<MODULE>/<MODULE>-report.html`: the scanner's own reports.
