# Batch G: authorization / IMS / Db2 / MQ and VSAM / MQ factual inventory

Static source inventory of `app/app-authorization-ims-db2-mq` and `app/app-vsam-mq` (main @ `bf29547`). No program was compiled or run: no IMS, MQ or CICS runtime exists off-host. Source is authoritative over the sub-app READMEs. MQ object definitions, IMS translator output and live database contents were not supplied.

## Citation convention and coverage

`A/` means `app/app-authorization-ims-db2-mq/`; `V/` means `app/app-vsam-mq/`. All other paths are repository-relative. A citation such as `A/cbl/COPAUA0C.cbl:620-624` expands literally to that file and physical line range (not the COBOL sequence numbers). Binary evidence uses byte offsets because binary files have no meaningful source lines. All 43 text files in the two sub-apps and the one binary file were inspected, including both symbolic map copybooks, both BMS sources, all five JCL members, all eight IMS definitions, all nine application/PCB/function copybooks, the DDL/DCL and both READMEs. Cross-checked against the mftb module scans on branches `demo/01-intake-AUTH` and `demo/01-intake-VSAMMQ` (`store/AUTH`, `store/VSAMMQ`).

## 1. Entry points and runtime boundaries

| Program | Actual entry / launcher | Runtime dependencies / externally visible outputs |
|---|---|---|
| COPAUA0C | CP00 → COPAUA0C (`A/csd/CRDDEMO2.csd:59-68`) | CICS-triggered MQ consumer; CICS VSAM reads, EXEC DLI IMS writes, MQ response, CSSL transient-data logging (`A/cbl/COPAUA0C.cbl:233-246,438-465,983-1006`). |
| COPAUS0C | CPVS → COPAUS0C (`A/csd/CRDDEMO2.csd:49-58`) | CICS summary/list terminal flow, IMS reads, VSAM reads, mapset COPAU00 and shared COMMAREA (`A/cbl/COPAUS0C.cbl:116-165,178-257,681-721,812-997`). |
| COPAUS1C | CPVD → COPAUS1C (`A/csd/CRDDEMO2.csd:39-48`) | CICS detail/fraud terminal flow, IMS read/replace, LINK COPAUS2C, mapset COPAU01 (`A/cbl/COPAUS1C.cbl:33-36,156-265,373-405,431-568`). |
| COPAUS2C | LINK from COPAUS1C, **not** direct CPVD entry (`A/cbl/COPAUS1C.cbl:248-252`; `A/csd/CRDDEMO2.csd:32-40`) | CICS clock/RETURN and Db2 INSERT/UPDATE of CARDDEMO.AUTHFRDS (`A/cbl/COPAUS2C.cbl:89-101,141-244`). PROGRAM's TRANSID(CPVD) attribute does not change TRANSACTION(CPVD)'s initial program. |
| CBPAUP0C | CBPAUP0J STEP01: DFSRRC00 `BMP,CBPAUP0C,PSBPAUTB` (`A/jcl/CBPAUP0J.jcl:24-37`) | EXEC DLI purge; SYSIN control, clock, SYSOUT counts, checkpoints (`A/cbl/CBPAUP0C.cbl:136-210,223-226,255-258,310-356`). |
| PAUDBLOD | LOADPADB STEP01: DFSRRC00 `BMP,PAUDBLOD,PSBPAUTB` (`A/jcl/LOADPADB.JCL:26-38`) | Sequential INFILE1/INFILE2 → CBLTDLI IMS inserts (`A/cbl/PAUDBLOD.CBL:26-48,169-187,242-336`). |
| PAUDBUNL | UNLDPADB STEP01: DFSRRC00 `DLI,PAUDBUNL,PAUTBUNL,,,,,,,,,,,N` (`A/jcl/UNLDPADB.JCL:38-59`) | CBLTDLI IMS reads → fixed sequential OUTFIL1/OUTFIL2 (`A/cbl/PAUDBUNL.CBL:26-48,207-284`). |
| DBUNLDGS | UNLDGSAM STEP01: DFSRRC00 `DLI,DBUNLDGS,DLIGSAMP,,,,,,,,,,,N` (`A/jcl/UNLDGSAM.JCL:26-43`) | CBLTDLI IMS reads → GSAM PCBs, no active ordinary file I/O (`A/cbl/DBUNLDGS.CBL:24-49,159-179,216-334`). |
| COACCT01 | CDRA → COACCT01 (`V/csd/CRDDEMOM.csd:17-26`) | CICS-triggered MQ consumer, ACCTDAT CICS READ, fixed-length MQ reply/error (`V/cbl/COACCT01.cbl:178-218,334-388,390-485,501-523`). |
| CODATE01 | CDRD → CODATE01 (`V/csd/CRDDEMOM.csd:27-36`) | CICS-triggered MQ consumer, CICS system date/time, fixed-length MQ reply/error; no active VSAM read (`V/cbl/CODATE01.cbl:127-167,283-403`). |

The authorization CSD also defines both mapsets and all four online programs, plus DB2ENTRY AWS01PLN with PLAN(AWS01PLN), THREADLIMIT(1), THREADWAIT(YES), and DB2TRAN CPVDTRAN for CPVD (`A/csd/CRDDEMO2.csd:1-38,69-79`). The VSAM/MQ CSD defines LIBRARY CARDDLIB at AWS.M2.CARDDEMO.LOADLIB; its program descriptions misleadingly say LIST CARDS (`V/csd/CRDDEMOM.csd:1-16,37-41`). Neither CSD contains actual MQ queue/process/trigger definitions; their complete resource lists are at `A/csd/CRDDEMO2.csd:1-79` and `V/csd/CRDDEMOM.csd:1-41`.

## 2. Paragraph-level program behavior and all interfaces

### COPAUA0C — authorization decision service

1. MAIN calls 1000-INITIALIZE, 2000-MAIN-PROCESS, 9000-TERMINATE, then CICS RETURN (`A/cbl/COPAUA0C.cbl:220-227`). 1000 RETRIEVEs MQTM; on NORMAL copies MQTM-QNAME into request queue and MQTM-TRIGGERDATA into a 64-byte work field, sets wait 5000, opens queue, gets first request (`A/cbl/COPAUA0C.cbl:233-246`). No alternate request queue is initialized if RETRIEVE fails.
2. 1100 opens request queue INPUT-SHARED; connection handle starts zero, object type MQOT-Q, name from trigger (`A/cbl/COPAUA0C.cbl:99-108,255-268`). 3100 GETs up to 500 bytes with NO-SYNCPOINT + WAIT + CONVERT + FAIL-IF-QUIESCING, resets MsgId/CorrelId to NONE, requests STRING format, uses wait 5000 ms. On success saves **incoming CorrelId**, not MsgId, and ReplyToQ; on MQRC-NO-MSG-AVAILABLE ends loop; other failures log critical (`A/cbl/COPAUA0C.cbl:386-431`). No MQCONN/MQDISC is called in this member; CICS MQ attachment is assumed by the zero handle and CICS entry (`A/cbl/COPAUA0C.cbl:99-105,220-268,386-409,738-780,953-977`).
3. 2000 loops: 2100 CSV extraction → 5000 authorization → increment processed → CICS SYNCPOINT → mark PSB unscheduled → GET next. Limit test is `processed > 500`, so a continuously populated queue permits **501** requests per invocation (`A/cbl/COPAUA0C.cbl:40,323-344`).
4. 2100 UNSTRINGs the actual received length on comma into 18 fields; amount goes through X(13) staging and NUMVAL. No quoting/escaping/field-count check appears (`A/cbl/COPAUA0C.cbl:63-66,351-379`). Full wire schema is in §5.
5. 5000 sets APPROVE-AUTH and schedules PSBPAUTB; 1200 SCHD NODHABEND, on TC TERM then retry SCHD; PCB number 1 (`A/cbl/COPAUA0C.cbl:81-97,292-317,438-465`). 5100 reads CCXREF by 16-byte card number → CVACT03Y; if found 5200 reads ACCTDAT by 11-byte account ID → CVACT01Y and 5300 reads CUSTDAT by 9-byte customer ID → CVCUS01Y. NOTFND sets flags and warning log; other failures critical log (`A/cbl/COPAUA0C.cbl:35-39,202-209,472-609`). CARDDAT and CARDAIX names are declared but never read by these paragraphs.
6. 5500 IMS GU PCB(1), segment PAUTSUM0, INTO CIPAUSMY, WHERE ACCNTID = packed account ID. GE means new summary. 5600-READ-PROFILE-DATA is only CONTINUE (`A/cbl/COPAUA0C.cbl:616-654`).
7. 6000 compares amount with summary credit-limit minus credit-balance when summary exists; otherwise ACCT-CREDIT-LIMIT minus ACCT-CURR-BAL if account found; missing account declines. Builds approval/decline fields and CSV response (`A/cbl/COPAUA0C.cbl:657-731`). Rules and gaps are in §6.
8. 7100 replies **before** storing IMS changes: MQPUT1, object name incoming ReplyToQ, message type REPLY, CorrelId copied from request CorrelId, MsgId NONE, ReplyToQ/QMgr blank, nonpersistent, expiry 50 (MQ tenths-of-seconds = 5 seconds), STRING; NO-SYNCPOINT + DEFAULT-CONTEXT. Buffer length comes from STRING pointer, not a constant (`A/cbl/COPAUA0C.cbl:461-465,722-765`). It ignores incoming ReplyToQMgr; there is no separate reply MQOPEN/GET/CLOSE.
9. 8000 runs 8400 summary then 8500 detail only if card exists in xref. 8400 initializes new CIPAUSMY, takes account/customer IDs from xref, refreshes limits from account; approval increments count/amount and credit balance, sets cash balance zero; decline increments declined count and adds **PA-TRANSACTION-AMT** (detail work area, not current request amount). Existing summary REPL PCB(1) PAUTSUM0 FROM summary; otherwise ISRT same segment (`A/cbl/COPAUA0C.cbl:786-846`).
10. 8500 timestamps using CICS ASKTIME/FORMATTIME YYDDD, TIME, MILLISECONDS; computes inverse date/time key, moves request/reply fields to CIPAUDTY, sets match P or D, blanks fraud fields, then ISRT PCB(1) with parent PAUTSUM0 WHERE ACCNTID = account ID and child PAUTDTL1 FROM detail, explicit SEGLENGTH (`A/cbl/COPAUA0C.cbl:854-931`).
11. 9000 TERM if scheduled, 9100 MQCLOSE request MQCO-NONE. 9500 writes error record CCPAUERY to CICS TD queue **CSSL**, timestamps YYMMDD/TIME, application CP00/program COPAUA0C; critical invokes 9990 terminate/RETURN, warnings continue (`A/cbl/COPAUA0C.cbl:940-1025`). Error record fields: date/time X(6) each, application/program X(8) each, location X(4), level/subsystem X(1), codes X(9) each, message X(50), event key X(20); levels L/I/W/C, subsystems A/C/I/D/M/F (`A/cpy/CCPAUERY.cpy:19-40`).

No SQL, BMS SEND/RECEIVE, LINK or XCTL occurs in COPAUA0C. DFHCOMMAREA is declared X(4096) but not used by the procedure; entry data is CICS RETRIEVE MQTM (`A/cbl/COPAUA0C.cbl:214-246`; procedure `:220-1025`).

### COPAUS0C — account summary and five-row authorization browser

* MAIN resets flags, clears errors; first invocation EIBCALEN=0 initializes CARDDEMO-COMMAREA and sends blank screen; otherwise copies incoming COMMAREA, distinguishes initial entry from reentry, receives map and dispatches ENTER/PF3/PF7/PF8; returns TRANSID CPVS with COMMAREA (`A/cbl/COPAUS0C.cbl:178-257`).
* PROCESS-ENTER-KEY validates nonblank numeric 11-character account, inspects five selection fields, accepts S/s, stores selected 8-byte detail key and XCTLs COPAUS1C with CARDDEMO-COMMAREA; other selections display error. GATHER-DETAILS resets page count, gathers account, clears rows, fills page if summary found (`A/cbl/COPAUS0C.cbl:261-357`).
* GATHER-ACCOUNT-DETAILS calls GETCARDXREF-BYACCT / GETACCTDATA-BYACCT / GETCUSTDATA-BYCUST. Actual CICS READs: CXACAIX by 11-byte account → CVACT03Y, ACCTDAT by xref account → CVACT01Y, CUSTDAT by xref customer → CVCUS01Y; stores customer/card IDs in shared COMMAREA and populates name/address/phone/limits (`A/cbl/COPAUS0C.cbl:38-42,144-153,750-807,812-963`). Declared CARDDAT and CCXREF names do not create additional accesses.
* GET-AUTH-SUMMARY schedules PSBPAUTB and GU PCB(1), PAUTSUM0 WHERE ACCNTID = CDEMO-ACCT-ID; GE is absent-summary, other errors screen message. SCHEDULE-PSB uses SCHD NODHABEND; TC triggers TERM/SCHD retry (`A/cbl/COPAUS0C.cbl:74-90,966-1031`). Displays approved/declined counts, credit/cash balances and approved/declined amounts, zeros if absent (`A/cbl/COPAUS0C.cbl:785-807`).
* PROCESS-PAGE-FORWARD uses GET-AUTHORIZATIONS: GNP PCB(1) PAUTDTL1, unqualified, up to 5 displayed plus one lookahead. GE/GB ends details. REPOSITION-AUTHORIZATIONS uses GNP same PCB/segment WHERE PAUT9CTS = saved 8-byte authorization key. PF8 repositions at last displayed key; PF7 uses saved first key of prior page; 20 previous-page key slots are allocated (`A/cbl/COPAUS0C.cbl:117-126,362-519`). POPULATE-AUTH-LIST formats date/time, approved amount, A/D, match status, transaction ID and saves each row's key; INITIALIZE-AUTH-DATA clears/protects five rows (`A/cbl/COPAUS0C.cbl:522-662`).
* SEND-PAULST-SCREEN takes CICS SYNCPOINT if PSB scheduled; SEND MAP COPAU0A MAPSET COPAU00 FROM COPAU0AO, CURSOR, optionally ERASE. RECEIVE same map/mapset INTO COPAU0AI with RESP/RESP2. Header uses FUNCTION CURRENT-DATE. PF3 chooses COMEN01C; RETURN-TO-PREV-SCREEN XCTLs target COMMAREA with fallback COSGN00C if empty (`A/cbl/COPAUS0C.cbl:235-238,665-747`).
* Shared base layout is app/cpy/COCOM01Y.cpy:19-44 (routing IDs, user/type/context, customer names/ID, account ID/status, card number, last map/mapset). Appended CPVS layout is selection X(1), selected key X(8), 20 previous keys X(8), last key X(8), page S9(4) COMP, next-page flag X(1), five row keys X(8) (`A/cbl/COPAUS0C.cbl:116-126`). Incoming variable-length linkage depends on EIBCALEN (`:171-174`). No MQ, SQL, LINK or VSAM writes in this program (complete procedure `:178-1031`).

### COPAUS1C — detail browsing and explicit fraud toggle

* MAIN with no COMMAREA routes to summary; otherwise copies CARDDEMO-COMMAREA and blanks CPVD fraud scratch. Initial entry/ENTER reads selected detail and sends; PF3 XCTLs summary, PF5 MARK-AUTH-FRAUD, PF8 next authorization; invalid AID rereads then displays invalid-key message; RETURN CPVD with COMMAREA (`A/cbl/COPAUS1C.cbl:156-205`).
* PROCESS-ENTER-KEY validates account numeric and selected key nonblank, reads root/detail, syncpoints if scheduled, populates display. READ-AUTH-RECORD SCHDs PSBPAUTB, GU PCB(1) PAUTSUM0 WHERE ACCNTID = account then GNP PAUTDTL1 WHERE PAUT9CTS = selected key. READ-NEXT-AUTH-RECORD is unqualified GNP PAUTDTL1. GE/GB flags EOF; other statuses screen error. PF8 rereads selected then GNP next, syncpoints, updates selected key unless EOF (`A/cbl/COPAUS1C.cbl:76-91,208-228,268-288,431-518,574-603`).
* MARK-AUTH-FRAUD rereads; F toggles to R, anything else toggles to F; copies detail plus account/customer IDs into WS-FRAUD-DATA and LINKs COPAUS2C NOHANDLE. Normal LINK + returned status S → UPDATE-AUTH-DETAILS; otherwise CICS SYNCPOINT ROLLBACK (`A/cbl/COPAUS1C.cbl:230-265`). This is human flagging, not automatic scoring.
* UPDATE-AUTH-DETAILS moves returned 200-byte record into CIPAUDTY, REPL PCB(1) PAUTDTL1 FROM detail; success CICS SYNCPOINT, failure ROLLBACK and error message (`A/cbl/COPAUS1C.cbl:520-568`). COPAUS2C supplies fraud report date through the passed detail.
* POPULATE-AUTH-DETAILS formats original date/time, approved amount, A/D/color, reason from sorted ten-entry table, processing-code into AUTHCDO, POS/source/MCC/expiry/type/transaction/match/fraud/merchant fields. Unknown reason displays 9999-ERROR (`A/cbl/COPAUS1C.cbl:56-73,291-357`). The displayed “authorization code” is PA-PROCESSING-CODE, not PA-AUTH-ID-CODE (`:331`).
* SEND/RECEIVE MAP COPAU1A MAPSET COPAU01 with COPAU1AO/AI, cursor, optional ERASE; RECEIVE NOHANDLE. RETURN-TO-PREV-SCREEN XCTLs CDEMO-TO-PROGRAM with shared COMMAREA; known target COPAUS0C. Clock header uses FUNCTION CURRENT-DATE (`A/cbl/COPAUS1C.cbl:33-36,360-428`). SCHEDULE-PSB SCHD/TC TERM/retry as summary (`:574-603`).
* CPVD COMMAREA appends the same navigation fields as CPVS plus X(100) CPVD-FRAUD-DATA; LINK argument is a separate 272-byte group: account 9(11), customer 9(9), detail X(200), action X(1) F/R, status X(1) S/F, message X(50) (`A/cbl/COPAUS1C.cbl:93-120`). COPAUS2C linkage maps it identically using COPY CIPAUDTY (`A/cbl/COPAUS2C.cbl:73-86`). No MQ, direct VSAM file I/O or direct SQL in COPAUS1C; Db2 access is through LINK (`A/cbl/COPAUS1C.cbl:156-603`).

### COPAUS2C — Db2 fraud record insert-or-update

* MAIN uses CICS ASKTIME/FORMATTIME MMDDYY DATESEP to set PA-FRAUD-RPT-DATE. Builds timestamp from **original request date** YYMMDD plus **inverse-key processing time** `999999999 - PA-AUTH-TIME-9C`, with milliseconds and trailing 000 (`A/cbl/COPAUS2C.cbl:35-51,89-111`). This deliberately mixes two source clocks; no assertion that their dates/times match.
* Moves every detail field to DCLAUTHFRDS, sets merchant VARCHAR length to LENGTH OF PA-MERCHANT-NAME (22), AUTH-FRAUD to passed action F/R, account/customer from linkage (`A/cbl/COPAUS2C.cbl:113-139`). SQLCA and AUTHFRDS are SQL INCLUDEs (`:65-70`).
* INSERT INTO CARDDEMO.AUTHFRDS explicitly names all 26 columns; values correspond to host fields except AUTH_TS uses TIMESTAMP_FORMAT(:AUTH-TS,'YY-MM-DD HH24.MI.SSNNNNNN') and FRAUD_RPT_DATE uses CURRENT DATE. SQLCODE 0 returns S/ADD SUCCESS; -803 runs FRAUD-UPDATE; other values F with formatted SQLCODE/SQLSTATE message (`A/cbl/COPAUS2C.cbl:141-216`).
* FRAUD-UPDATE: UPDATE CARDDEMO.AUTHFRDS SET AUTH_FRAUD=:AUTH-FRAUD, FRAUD_RPT_DATE=CURRENT DATE WHERE CARD_NUM=:CARD-NUM AND AUTH_TS=TIMESTAMP_FORMAT(...same format...). SQLCODE 0 → S/UPDT SUCCESS, else F/error (`A/cbl/COPAUS2C.cbl:221-244`). No SELECT, DELETE, MQ, IMS or VSAM calls. No explicit COMMIT/ROLLBACK here; caller coordinates outcome. CICS RETURN returns control to LINK caller (`:218-219`).

### CBPAUP0C — expiry purge

* MAIN: initialize → GN summary → for each root GNP details, check expiry, DLET qualifying detail → conditionally DLET root → checkpoint by root counter → next root → final checkpoint → DISPLAY counts → GOBACK (`A/cbl/CBPAUP0C.cbl:136-180`).
* 1000 accepts DATE and DAY (YYDDD), **ACCEPT PRM-INFO FROM SYSIN**. Controls are fixed-position `99,x(5),x(5),x(1)` separated by one-byte fillers: expiry days, checkpoint frequency, checkpoint-display frequency, debug Y/N. Invalid/non-numeric expiry defaults 5; blank/zero/low-value checkpoint defaults 5, display defaults 10; non-Y debug becomes N (`A/cbl/CBPAUP0C.cbl:43-47,98-108,183-210`). This is not an application linkage PARM: DFSRRC00 PARM selects BMP/program/PSB; SYSIN supplies purge controls. Supplied JCL SYSIN **00,00001,00001,Y** requests zero-day expiry, checkpoint/display controls 1, debug on (`A/jcl/CBPAUP0J.jcl:24-37`).
* PCB number is **2** (BMP I/O PCB precedes database PCB), unlike online PCB(1); PROCEDURE USING IO-PCB-MASK, PGM-PCB-MASK (`A/cbl/CBPAUP0C.cbl:79-95,125-133`). 2000 EXEC DLI GN PCB(2) PAUTSUM0 INTO CIPAUSMY; blank success, GB EOF, other status returns 16. 3000 GNP PCB(2) PAUTDTL1 INTO CIPAUDTY; blank success, GE/GB no more, other error (`:216-272`). No qualified SSAs are used.
* 4000 decodes date as 99999 - PA-AUTH-DATE-9C; day-difference = current YYDDD minus authorization YYDDD, deletes if >= expiry days. For response 00 subtracts approved count and approved amount; otherwise declined count and transaction amount (`A/cbl/CBPAUP0C.cbl:277-298`). No test of match status, fraud status or approved/declined age distinction. Arithmetic is not calendar-day arithmetic across years.
* 5000 DLET PCB(2) PAUTDTL1 FROM detail; 6000 DLET PCB(2) PAUTSUM0 FROM summary. Root test repeats `PA-APPROVED-AUTH-CNT <= 0` twice, **never tests declined count** (`A/cbl/CBPAUP0C.cbl:156-158,303-347`). These are EXEC DLI commands, not explicit CBLTDLI calls; required translator/runtime hold semantics were not tested.
* 9000 EXEC DLI CHKP ID(WK-CHKPT-ID), ID initialized RMAD0000 and never incremented; success increments display counter, displays after >= P-CHKP-DIS-FREQ. Root checkpoint test is **>**, not >= frequency, then zeroes root counter; final checkpoint unconditional (`A/cbl/CBPAUP0C.cbl:75-77,160-169,352-370`). 9999 DISPLAY and RETURN-CODE 16/GOBACK (`:377-386`).
* **No REPL persists summary decrements**, and no statement reduces PA-CREDIT-BALANCE; surviving roots' decremented counters only change work storage. README's claim “Adjustment of available credit” is not implemented here (`A/cbl/CBPAUP0C.cbl:277-370`; `A/README.md:84-86`). No MQ, SQL, maps, LINK/XCTL, VSAM or ordinary data files (file sections empty `A/cbl/CBPAUP0C.cbl:26-38`).

### PAUDBLOD — flat extracts to IMS

* INFILE1 sequential record X(100); INFILE2 sequential record = packed S9(11) root key (6 bytes) + X(200) child (206 total). DDs bind AWS.M2.CARDDEMO.PAUTDB.ROOT.FILEO and .CHILD.FILEO (`A/cbl/PAUDBLOD.CBL:26-48`; `A/jcl/LOADPADB.JCL:36-38`). Main linkage IO-PCB-MASK + PAUTBPCB; alternate ENTRY DLITCBL uses only PAUTBPCB (`A/cbl/PAUDBLOD.CBL:157-171`).
* MAIN 1000 accepts date/day, opens both inputs, errors → 9999 RC16; exhausts **all roots first**, then all children, then 4000 closes (`A/cbl/PAUDBLOD.CBL:175-215,341-366`). 2000 READ root → summary → 2100 CBLTDLI ISRT PAUTBPCB + unqualified SSA `PAUTSUM0 `; II duplicate tolerated, other errors RC16 (`:222-262`).
* 3000 READ child, if root packed key NUMERIC copies key into ROOT-QUAL-SSA and detail payload. SSA is `PAUTSUM0(ACCNTID EQ<packed S9(11)>)` (8-character field name is `ACCNTID `, 2-character operator EQ). 3100 CBLTDLI GU PAUTBPCB summary ROOT-QUAL-SSA; only successful GU PERFORMs 3200 CBLTDLI ISRT PAUTBPCB detail CHILD-UNQUAL-SSA `PAUTDTL1 `. Duplicate child II tolerated (`A/cbl/PAUDBLOD.CBL:113-127,269-336`). The nested error test is within the successful-GU IF; failed parent GU has no independent ELSE/error branch in this paragraph (`:295-314`).
* No MQ, SQL, EXEC CICS, EXEC DLI, BMS or checkpoints; this is **not IMS-free** because of CBLTDLI (`A/cbl/PAUDBLOD.CBL:169-369`). Non-EOF read errors display but do not set EOF/abort (`:222-237,269-289`), a possible persistent-error loop, not observed execution.

### PAUDBUNL — IMS to flat extracts

* PAUTBUNL PSB passes one PAUTBPCB; PROCEDURE and alternate ENTRY DLITCBL use it (`A/cbl/PAUDBUNL.CBL:147-170`). 1000 date/day/display, opens OPFILE1 assigned OUTFIL1, OPFILE2 assigned OUTFIL2; 4000 closes; open/DL/I error → 9999 RC16 (`:26-48,173-200,289-317`).
* MAIN repeats 2000 until root EOF. CBLTDLI GN, PAUTBPCB, summary, unqualified SSA `PAUTSUM0 `; numeric PA-ACCT-ID writes 100-byte root, saves root key, loops 3000. Root GB ends; other status error (`A/cbl/PAUDBUNL.CBL:111-117,157-170,207-246`).
* 3000 CBLTDLI GNP, same PCB, detail, `PAUTDTL1 `; success writes **6-byte packed root key + 200-byte detail** to OPFILE2; GE ends child loop; other status error. It increments **summary** read/process counters on child success (misnamed counters) (`A/cbl/PAUDBUNL.CBL:253-284`).
* UNLDPADB STEP0 deletes previous root/child files via IEFBR14 DD dispositions; STEP01 creates OUTFIL1 FB LRECL100 and OUTFIL2 FB LRECL206; IMS DDPAUTP0 OEM.IMS.IMSP.PAUTHDB / DDPAUTX0 OEM.IMS.IMSP.PAUTHDBX (`A/jcl/UNLDPADB.JCL:25-59`). No SQL, MQ, CICS, EXEC DLI or active checkpoints (complete procedure `A/cbl/PAUDBUNL.CBL:157-317`).

### DBUNLDGS — IMS to separate GSAM streams

* PROCEDURE/ENTRY DLITCBL USING PAUTBPCB, PASFLPCB, PADFLPCB. MAIN initializes date/day/display, iterates 2000 root until GB, then 4000 only displays “CLOSING THE FILE” (`A/cbl/DBUNLDGS.CBL:159-212,338-355`). Ordinary SELECT/FD/OPEN/CLOSE code is commented out (`:24-49,194-211,338-354`).
* 2000 CBLTDLI GN PAUTBPCB summary SSA `PAUTSUM0 `; numeric account → 3100 CBLTDLI ISRT **PASFLPCB**, **PENDING-AUTH-SUMMARY**, **no SSA**; loops 3000 CBLTDLI GNP PAUTBPCB detail SSA `PAUTDTL1 ` until GE; 3200 CBLTDLI ISRT **PADFLPCB**, **PENDING-AUTH-DETAILS**, **no SSA** (`A/cbl/DBUNLDGS.CBL:115-121,216-334`). Unexpected PCB status → 9999 RC16 (`:357-366`).
* A 206-byte OPFIL2 work area/root key exists, but GSAM ISRT passes **200-byte detail only**, unlike PAUDBUNL output. Therefore CHILD.GSAM does **not** carry a parent account key and is not the CHILD.FILEO format accepted by PAUDBLOD (`A/cbl/DBUNLDGS.CBL:53-56,237-239,280-282,319-334`; `A/cbl/PAUDBLOD.CBL:43-48`).
* JCL GSAM outputs PASFILOP AWS.M2.CARDDEMO.PAUTDB.ROOT.GSAM, PADFILOP ...CHILD.GSAM, both DISP=(OLD,KEEP,KEEP); database DDs PAUTHDB/PAUTHDBX (`A/jcl/UNLDGSAM.JCL:36-43`). No MQ, SQL, CICS, EXEC DLI or active checkpoint in procedure (`A/cbl/DBUNLDGS.CBL:164-366`).

### COACCT01 — MQ account inquiry, no terminal presentation

* 1000 clears work names, initializes error display, 2100 opens CARD.DEMO.ERROR, RETRIEVEs MQTM, takes INPUT-QUEUE-NAME from trigger and fixes reply to CARD.DEMO.REPLY.ACCT; opens input/output, GETs first, loops 4000-MAIN-PROCESS until timeout, terminates. RETRIEVE errors queue an error and terminate (`V/cbl/COACCT01.cbl:178-218,289-322`).
* 2300 MQOPEN input: INPUT-SHARED + SAVE-ALL-CONTEXT + FAIL-IF-QUIESCING; object-QMgr blank. 2400 MQOPEN reply and 2100 MQOPEN error: OUTPUT + PASS-ALL-CONTEXT + FAIL-IF-QUIESCING, QMgr blank. Handles stored independently; no MQCONN or MQDISC; handle variables initialized zero (`V/cbl/COACCT01.cbl:44-48,98-104,222-322`).
* 3000 MQGET buffer 1000, WAITINTERVAL 5000 ms, SYNCPOINT + FAIL-IF-QUIESCING + CONVERT + WAIT, MsgId/CorrelId NONE; copies returned MQMD MsgId, CorrelId, ReplyToQ to saved fields. Success moves entire buffer to inline request and processes; timeout ends; other error queue/terminate (`V/cbl/COACCT01.cbl:334-388`). Saved ReplyToQ is **not** used as output destination (`:255-287,366-374,479-485`).
* 4000-PROCESS-REQUEST-REPLY accepts only WS-FUNC='INQA' AND key > 0; CICS READ ACCTDAT by 11-byte account → ACCOUNT-RECORD COPY CVACT01Y. NORMAL moves 11 account fields into labeled response; NOTFND returns INVALID REQUEST PARAMETERS ACCT ID; invalid function/key includes FUNCTION; other CICS error → error MQ and terminate (`V/cbl/COACCT01.cbl:109-171,390-457`). No update of account.
* 4100 MQPUT reply length **1000**, restores **same incoming MsgId and CorrelId**, STRING / MQCCSI-Q-MGR; SYNCPOINT + DEFAULT-CONTEXT + FAIL-IF-QUIESCING. 9000 uses same options/1000-byte format for error queue, payload error display (`V/cbl/COACCT01.cbl:462-537`). 4000-MAIN-PROCESS CICS SYNCPOINTs then gets/processes next request, so the first processed request commits at the beginning of this loop (`:325-330`).
* 8000 closes input/reply/error if open, each MQCLOSE MQCO-NONE, CICS RETURN/GOBACK. Close failures may PERFORM termination recursively; error-close failure invokes 9000 again (`V/cbl/COACCT01.cbl:539-619`). No COMMAREA contents, BMS, LINK/XCTL, SQL or IMS calls; LINKAGE SECTION empty (`:174-176`, complete procedure `:178-619`).

### CODATE01 — MQ system-clock responder

* Same paragraph pipeline/error handling/queue mechanics as account service, with shifted source lines: 1000 opens error, RETRIEVE trigger, fixes reply CARD.DEMO.REPLY.DATE, opens input/reply, gets, loops; 2300 input MQOPEN INPUT-SHARED + SAVE-ALL-CONTEXT + FAIL-IF-QUIESCING; 2400/2100 reply/error MQOPEN OUTPUT + PASS-ALL-CONTEXT + FAIL-IF-QUIESCING, object-QMgr blank (`V/cbl/CODATE01.cbl:127-271`).
* 3000 MQGET 1000 bytes, WAIT 5000ms, SYNCPOINT + FAIL-IF-QUIESCING + CONVERT + WAIT; MsgId/CorrelId NONE then saves IDs and ReplyToQ, processes, counts; timeout ends (`V/cbl/CODATE01.cbl:283-337`). 4000-MAIN-PROCESS CICS SYNCPOINT then next GET (`:274-280`).
* 4000-PROCESS-REQUEST-REPLY **ignores request fields**: CICS ASKTIME → FORMATTIME MMDDYYYY DATESEP('-'), TIME TIMESEP → STRING `SYSTEM DATE : ` + X(10) date + `SYSTEM TIME : ` + X(8) time into 1000-byte blanked reply → 4100 (`V/cbl/CODATE01.cbl:339-364`).
* 4100 MQPUT fixed reply queue, same incoming MsgId/CorrelId, 1000 bytes, STRING/MQCCSI-Q-MGR; SYNCPOINT + DEFAULT-CONTEXT + FAIL-IF-QUIESCING. 9000 error MQPUT same options/size to CARD.DEMO.ERROR; 8000 closes three handles MQCO-NONE and RETURN/GOBACK (`V/cbl/CODATE01.cbl:366-523`). Error display layout matches account (`V/cbl/CODATE01.cbl:58-67`; `V/cbl/COACCT01.cbl:58-67`).
* Inline request is still X(4) function + 9(11) key + X(985) filler, but unused by date processing; LIT-ACCTFILENAME ACCTDAT and response-code work fields are unused. LINKAGE is empty; no CICS file READ, maps, COMMAREA payload, LINK/XCTL, SQL or IMS (`V/cbl/CODATE01.cbl:109-125,339-364`, complete procedure `:127-523`).

## 3. IMS physical/logical definitions and copybook mappings

### DBDs, hierarchy, fields and indexes

| DBD | Access / dataset | Segments / fields / relationships |
|---|---|---|
| DBPAUTP0 | ACCESS=(HIDAM,VSAM), PASSWD=NO; EXIT=(*,KEY,DATA,NOPATH,(NOCASCADE),LOG); DDPAUTP0 SIZE4096 SCAN3 | Root PAUTSUM0 BYTES100 PARENT0 RULES=(,HERE) POINTER=TWINBWD; unique sequence ACCNTID START1 BYTES6 TYPE=P; LCHILD=(PAUTINDX,DBPAUTX0) POINTER=INDX. Child PAUTDTL1 PARENT PAUTSUM0 BYTES200, unique sequence PAUT9CTS START1 BYTES8 TYPE=C (`A/ims/DBPAUTP0.dbd:18-37`). |
| DBPAUTX0 | ACCESS=(INDEX,VSAM,PROT), PASSWD=NO; DDPAUTX0 SIZE4096 | Root PAUTINDX BYTES6 FREQ100000; unique sequence INDXSEQ START1 BYTES6 TYPE=P; LCHILD=(PAUTSUM0,DBPAUTP0), INDEX=ACCNTID (`A/ims/DBPAUTX0.dbd:18-31`). |
| PASFLDBD | ACCESS=(GSAM,BSAM), PASSWD=NO | No SEGM/FIELD hierarchy; DD1=PASFILIP, DD2=PASFILOP, RECORD100, RECFM=F, summary CIPAUSMY passed by DBUNLDGS (`A/ims/PASFLDBD.DBD:22-30`; `A/cbl/DBUNLDGS.CBL:300-304`). |
| PADFLDBD | ACCESS=(GSAM,BSAM), PASSWD=NO | No SEGM/FIELD hierarchy; DD1=PADFILIP, DD2=PADFILOP, RECORD200, RECFM=F, detail CIPAUDTY passed by DBUNLDGS (`A/ims/PADFLDBD.DBD:22-30`; `A/cbl/DBUNLDGS.CBL:319-323`). |

DBPAUTX0 is the HIDAM root **primary index**, not evidence of an additional application secondary index. These DBDs have the paired LCHILD declarations above, but no XDFLD or other secondary-index definition (`A/ims/DBPAUTP0.dbd:18-41`; `A/ims/DBPAUTX0.dbd:18-34`). No separate PAUTINDX application segment copybook is supplied; its 6-byte packed index key corresponds to PA-ACCT-ID (`A/ims/DBPAUTX0.dbd:27-31`; `A/cpy/CIPAUSMY.cpy:19`).

### Every PSB

| PSB | Ordered database PCBs and sensitivities | Generation / usage |
|---|---|---|
| PSBPAUTB | PAUTBPCB TYPE=DB DBDNAME=DBPAUTP0 PROCOPT=AP KEYLEN14; SENSEG PAUTSUM0 PARENT0; PAUTDTL1 PARENT PAUTSUM0 | LANG=COBOL CMPAT=YES (`A/ims/PSBPAUTB.psb:17-21`). Used by online programs, BMP purge and loader (citations in §1/§2). |
| PSBPAUTL | PAUTLPCB TYPE=DB DBPAUTP0 PROCOPT=L KEYLEN14; same root/child SENSEG | LANG=ASSEM; load PSB, not the PSB in LOADPADB JCL (`A/ims/PSBPAUTL.psb:17-21`; `A/jcl/LOADPADB.JCL:27`). |
| PAUTBUNL | PAUTBPCB TYPE=DB DBPAUTP0 PROCOPT=GOTP KEYLEN14; same root/child SENSEG | LANG=COBOL CMPAT=NO (`A/ims/PAUTBUNL.PSB:18-22`). |
| DLIGSAMP | 1 PAUTBPCB TYPE=DB DBPAUTP0 PROCOPT=GOTP KEYLEN14, same root/child SENSEG; 2 TYPE=GSAM PASFLDBD PROCOPT=LS; 3 TYPE=GSAM PADFLDBD PROCOPT=LS | LANG=COBOL CMPAT=NO. GSAM PCBs have no SENSEG statements (`A/ims/DLIGSAMP.PSB:18-24`). |

PCB mask copybooks PAUTBPCB, PASFLPCB, PADFLPCB all contain DBDNAME X(8), SEG-LEVEL X(2), PCB-STATUS X(2), PCB-PROCOPT X(4), filler S9(5) COMP, SEG-NAME X(8), KEYFB-NAME S9(5) COMP, NUM-SENSEGS S9(5) COMP; key-feedback areas respectively X(255), X(100), X(255) (`A/cpy/PAUTBPCB.CPY:17-26`; `A/cpy/PASFLPCB.CPY:17-26`; `A/cpy/PADFLPCB.CPY:17-26`). IMSFUNCS defines four-character GU/GHU/GN/GHN/GNP/GHNP/REPL/ISRT/DLET constants and PARMCOUNT S9(5) COMP-5 value4; not every available constant is called (`A/cpy/IMSFUNCS.cpy:17-27`).

### PAUTSUM0 — CIPAUSMY, complete ordered layout (100 bytes)

| Field | PIC / storage | Source |
|---|---|---|
| PA-ACCT-ID | S9(11) COMP-3 (6 bytes; ACCNTID key) | `A/cpy/CIPAUSMY.cpy:19` |
| PA-CUST-ID | 9(9) display | `A/cpy/CIPAUSMY.cpy:20` |
| PA-AUTH-STATUS | X(1) | `A/cpy/CIPAUSMY.cpy:21` |
| PA-ACCOUNT-STATUS | X(2) OCCURS5 (10 bytes) | `A/cpy/CIPAUSMY.cpy:22` |
| PA-CREDIT-LIMIT; PA-CASH-LIMIT; PA-CREDIT-BALANCE; PA-CASH-BALANCE | Each S9(9)V99 COMP-3 (6 bytes) | `A/cpy/CIPAUSMY.cpy:23-26` |
| PA-APPROVED-AUTH-CNT; PA-DECLINED-AUTH-CNT | Each S9(4) COMP (2 bytes on IBM layout) | `A/cpy/CIPAUSMY.cpy:27-28` |
| PA-APPROVED-AUTH-AMT; PA-DECLINED-AUTH-AMT | Each S9(9)V99 COMP-3 (6 bytes) | `A/cpy/CIPAUSMY.cpy:29-30` |
| FILLER | X(34) | `A/cpy/CIPAUSMY.cpy:31` |

### PAUTDTL1 — CIPAUDTY, complete ordered layout (200 bytes)

| Field(s), in order | PIC / meaning | Source |
|---|---|---|
| PA-AUTHORIZATION-KEY → PA-AUTH-DATE-9C, PA-AUTH-TIME-9C | S9(5) COMP-3 (3 bytes), S9(9) COMP-3 (5 bytes); 8-byte PAUT9CTS key | `A/cpy/CIPAUDTY.cpy:19-21` |
| PA-AUTH-ORIG-DATE, PA-AUTH-ORIG-TIME | X(6), X(6) | `A/cpy/CIPAUDTY.cpy:22-23` |
| PA-CARD-NUM, PA-AUTH-TYPE, PA-CARD-EXPIRY-DATE | X(16), X(4), X(4) | `A/cpy/CIPAUDTY.cpy:24-26` |
| PA-MESSAGE-TYPE, PA-MESSAGE-SOURCE, PA-AUTH-ID-CODE | X(6) each | `A/cpy/CIPAUDTY.cpy:27-29` |
| PA-AUTH-RESP-CODE, PA-AUTH-RESP-REASON | X(2), X(4); response00 = approved | `A/cpy/CIPAUDTY.cpy:30-32` |
| PA-PROCESSING-CODE | 9(6) display | `A/cpy/CIPAUDTY.cpy:33` |
| PA-TRANSACTION-AMT, PA-APPROVED-AMT | S9(10)V99 COMP-3 each (7 bytes) | `A/cpy/CIPAUDTY.cpy:34-35` |
| PA-MERCHANT-CATAGORY-CODE, PA-ACQR-COUNTRY-CODE, PA-POS-ENTRY-MODE | X(4), X(3), 9(2) | `A/cpy/CIPAUDTY.cpy:36-38` |
| PA-MERCHANT-ID, PA-MERCHANT-NAME, PA-MERCHANT-CITY, PA-MERCHANT-STATE, PA-MERCHANT-ZIP | X(15), X(22), X(13), X(2), X(9) | `A/cpy/CIPAUDTY.cpy:39-43` |
| PA-TRANSACTION-ID | X(15) | `A/cpy/CIPAUDTY.cpy:44` |
| PA-MATCH-STATUS | X(1): P pending, D auth declined, E pending expired, M matched transaction | `A/cpy/CIPAUDTY.cpy:45-49` |
| PA-AUTH-FRAUD | X(1): F confirmed, R removed | `A/cpy/CIPAUDTY.cpy:50-52` |
| PA-FRAUD-RPT-DATE; FILLER | X(8); X(17) | `A/cpy/CIPAUDTY.cpy:53-54` |

The child key is described TYPE=C in DBD although its 8 bytes are two packed-decimal inverse time fields in COBOL; preserve the byte ordering, not a textual date assumption (`A/ims/DBPAUTP0.dbd:36-37`; `A/cpy/CIPAUDTY.cpy:19-21`; `A/cbl/COPAUA0C.cbl:868-875`).

## 4. Db2 AUTHFRDS

DDL creates CARDDEMO.AUTHFRDS with primary key (CARD_NUM,AUTH_TS); only these two columns explicitly NOT NULL. Everything below is in DDL order (`A/ddl/AUTHFRDS.ddl:1-28`).

| Column | Type | DDL line |
|---|---|---|
| CARD_NUM | CHAR(16) NOT NULL | `A/ddl/AUTHFRDS.ddl:2` |
| AUTH_TS | TIMESTAMP NOT NULL | `A/ddl/AUTHFRDS.ddl:3` |
| AUTH_TYPE | CHAR(4) | `A/ddl/AUTHFRDS.ddl:4` |
| CARD_EXPIRY_DATE | CHAR(4) | `A/ddl/AUTHFRDS.ddl:5` |
| MESSAGE_TYPE | CHAR(6) | `A/ddl/AUTHFRDS.ddl:6` |
| MESSAGE_SOURCE | CHAR(6) | `A/ddl/AUTHFRDS.ddl:7` |
| AUTH_ID_CODE | CHAR(6) | `A/ddl/AUTHFRDS.ddl:8` |
| AUTH_RESP_CODE | CHAR(2) | `A/ddl/AUTHFRDS.ddl:9` |
| AUTH_RESP_REASON | CHAR(4) | `A/ddl/AUTHFRDS.ddl:10` |
| PROCESSING_CODE | CHAR(6) | `A/ddl/AUTHFRDS.ddl:11` |
| TRANSACTION_AMT | DECIMAL(12,2) | `A/ddl/AUTHFRDS.ddl:12` |
| APPROVED_AMT | DECIMAL(12,2) | `A/ddl/AUTHFRDS.ddl:13` |
| MERCHANT_CATAGORY_CODE | CHAR(4), spelling as supplied | `A/ddl/AUTHFRDS.ddl:14` |
| ACQR_COUNTRY_CODE | CHAR(3) | `A/ddl/AUTHFRDS.ddl:15` |
| POS_ENTRY_MODE | SMALLINT | `A/ddl/AUTHFRDS.ddl:16` |
| MERCHANT_ID | CHAR(15) | `A/ddl/AUTHFRDS.ddl:17` |
| MERCHANT_NAME | VARCHAR(22) | `A/ddl/AUTHFRDS.ddl:18` |
| MERCHANT_CITY | CHAR(13) | `A/ddl/AUTHFRDS.ddl:19` |
| MERCHANT_STATE | CHAR(2) | `A/ddl/AUTHFRDS.ddl:20` |
| MERCHANT_ZIP | CHAR(9) | `A/ddl/AUTHFRDS.ddl:21` |
| TRANSACTION_ID | CHAR(15) | `A/ddl/AUTHFRDS.ddl:22` |
| MATCH_STATUS | CHAR(1) | `A/ddl/AUTHFRDS.ddl:23` |
| AUTH_FRAUD | CHAR(1) | `A/ddl/AUTHFRDS.ddl:24` |
| FRAUD_RPT_DATE | DATE | `A/ddl/AUTHFRDS.ddl:25` |
| ACCT_ID | DECIMAL(11), scale0 | `A/ddl/AUTHFRDS.ddl:26` |
| CUST_ID | DECIMAL(9), scale0 | `A/ddl/AUTHFRDS.ddl:27` |

Unique index CARDDEMO.XAUTHFRD ON CARDDEMO.AUTHFRDS (CARD_NUM ASC, AUTH_TS DESC) COPY YES (`A/ddl/XAUTHFRD.ddl:1-4`). DCL SQL declaration agrees on types/26 columns; comments name **AWSTSSC** but actual SQL DECLARE names **CARDDEMO**. Host money S9(10)V9(2) COMP-3, POS S9(4) COMP, merchant VARCHAR group length S9(4) COMP + text X(22), timestamp X(26), date X(10), account S9(11)V COMP-3/customer S9(9)V COMP-3 (`A/dcl/AUTHFRDS.dcl:2,24-89`). No supplied SELECT/DELETE of AUTHFRDS; only COPAUS2C INSERT and duplicate-key UPDATE (§2).

## 5. MQ wire contracts and queue-name provenance

### Authorization request and reply

Request is comma-delimited, not the raw contiguous CCPAURQY group; parser order and destination widths below. X fields are fixed-width destinations, not a claim that senders must pad each CSV token. No CSV quote support (`A/cbl/COPAUA0C.cbl:351-379`; `A/cpy/CCPAURQY.cpy:19-36`).

| Order | Request field (PA-RQ- prefix) | Destination PIC | Copybook line |
|---|---|---|---|
| 1 | AUTH-DATE | X(6) | `A/cpy/CCPAURQY.cpy:19` |
| 2 | AUTH-TIME | X(6) | `A/cpy/CCPAURQY.cpy:20` |
| 3 | CARD-NUM | X(16) | `A/cpy/CCPAURQY.cpy:21` |
| 4 | AUTH-TYPE | X(4) | `A/cpy/CCPAURQY.cpy:22` |
| 5 | CARD-EXPIRY-DATE | X(4) | `A/cpy/CCPAURQY.cpy:23` |
| 6 | MESSAGE-TYPE | X(6) | `A/cpy/CCPAURQY.cpy:24` |
| 7 | MESSAGE-SOURCE | X(6) | `A/cpy/CCPAURQY.cpy:25` |
| 8 | PROCESSING-CODE | 9(6) display | `A/cpy/CCPAURQY.cpy:26` |
| 9 | TRANSACTION-AMT | +9(10).99 (14 characters), but parsed via X(13) then NUMVAL | `A/cpy/CCPAURQY.cpy:27`; `A/cbl/COPAUA0C.cbl:63,364,376-379` |
| 10 | MERCHANT-CATAGORY-CODE | X(4) | `A/cpy/CCPAURQY.cpy:28` |
| 11 | ACQR-COUNTRY-CODE | X(3) | `A/cpy/CCPAURQY.cpy:29` |
| 12 | POS-ENTRY-MODE | 9(2) | `A/cpy/CCPAURQY.cpy:30` |
| 13 | MERCHANT-ID | X(15) | `A/cpy/CCPAURQY.cpy:31` |
| 14 | MERCHANT-NAME | X(22) | `A/cpy/CCPAURQY.cpy:32` |
| 15 | MERCHANT-CITY | X(13) | `A/cpy/CCPAURQY.cpy:33` |
| 16 | MERCHANT-STATE | X(2) | `A/cpy/CCPAURQY.cpy:34` |
| 17 | MERCHANT-ZIP | X(9) | `A/cpy/CCPAURQY.cpy:35` |
| 18 | TRANSACTION-ID | X(15) | `A/cpy/CCPAURQY.cpy:36` |

Reply order: PA-RL-CARD-NUM X(16), TRANSACTION-ID X(15), AUTH-ID-CODE X(6) (request auth-time), AUTH-RESP-CODE X(2), AUTH-RESP-REASON X(4), amount. Copybook amount is +9(10).99, but wire uses WS-APPROVED-AMT-DIS **-zzzzzzzzz9.99**, 14 characters with suppressed digits. STRING DELIMITED BY SIZE inserts comma after **every** field, including last; 16+15+6+2+4+14+6 commas = 63 characters. Pointer starts1 and ends64 on first reply, and is used unchanged as MQPUT1 length, so first reply includes an extra buffer byte (`A/cpy/CCPAURLY.cpy:19-24`; `A/cbl/COPAUA0C.cbl:46,66,660-662,720-730,756-765`). **Pointer is not reset each request**; second response appends at current pointer into the same X(200) buffer; later calls can overflow. This is source-level behavior, not an observed MQ trace.

### VSAM/MQ inline contracts

Both consumers define REQUEST-MSG-COPY = function X(4), numeric display key 9(11), filler X(985) = 1000; MOVE from full MQ buffer, no delimiter parsing and no actual-length validation (`V/cbl/COACCT01.cbl:109-112,342-374`; `V/cbl/CODATE01.cbl:109-112,291-323`). COACCT01 expects **INQA**, key>0; CODATE01 ignores both. READMEs' proposed ACCT/DATE + X(8) request-ID layouts are **not these implementations** (`V/README.md:97-128`; `V/cbl/COACCT01.cbl:393`; `V/cbl/CODATE01.cbl:339-361`).

COACCT01 success payload is the ordered WS-ACCT-RESPONSE below, moved to a space-cleared X(1000) reply. Monetary fields are **signed display with implied decimal**, not formatted decimal strings or the 300-byte CVACT01Y input record (`V/cbl/COACCT01.cbl:130-169,390-427,467-468`).

| Order | Label literal / storage length | Value / storage length | Source |
|---|---|---|---|
| 1 | ACCOUNT ID : / X(13) | account 9(11) | `V/cbl/COACCT01.cbl:132-134` |
| 2 | ACCOUNT STATUS : / X(17) | status X(1) | `V/cbl/COACCT01.cbl:135-137` |
| 3 | BALANCE : / X(10) | current S9(10)V99 (12 display bytes) | `V/cbl/COACCT01.cbl:138-141` |
| 4 | CREDIT LIMIT : / X(15) | credit S9(10)V99 | `V/cbl/COACCT01.cbl:142-145` |
| 5 | CASH LIMIT : / X(13) | cash S9(10)V99 | `V/cbl/COACCT01.cbl:146-149` |
| 6 | OPEN DATE : / X(12) | open X(10) | `V/cbl/COACCT01.cbl:150-152` |
| 7 | EXPR DATE : / X(12) | expiry X(10) | `V/cbl/COACCT01.cbl:153-155` |
| 8 | REIS DATE : / X(12) | reissue X(10) | `V/cbl/COACCT01.cbl:156-158` |
| 9 | CREDIT BAL : / X(13) | cycle credit S9(10)V99 | `V/cbl/COACCT01.cbl:159-162` |
| 10 | DEBIT BAL : / X(12) | cycle debit S9(10)V99 | `V/cbl/COACCT01.cbl:163-166` |
| 11 | GROUP ID : / X(11) | group X(10) | `V/cbl/COACCT01.cbl:167-169` |

Total labeled account group = 252 display bytes, followed by padding to1000 (sum of listed PIC widths). Numeric sign representation depends on compiler/runtime; no explicit SIGN SEPARATE clause is present (`V/cbl/COACCT01.cbl:130-169`). Date success payload: literal `SYSTEM DATE : ` (14), date X(10), literal `SYSTEM TIME : ` (14), time X(8), then blanks to1000, no separator beyond those literals (`V/cbl/CODATE01.cbl:35-38,339-361,371-372`). Both error payloads are formatted MQ-ERR-DISPLAY: X(25) paragraph, X(2) spaces, X(25) return message, X(2), 9(2) completion, X(2), 9(5) reason, X(2), X(48) queue, then padded to1000 (`V/cbl/COACCT01.cbl:58-67,505-523`; `V/cbl/CODATE01.cbl:58-67,409-427`).

### Queue names: code vs documentation

| Source | Request / response / error resolution |
|---|---|
| COPAUA0C code | Request = MQTM-QNAME; reply = incoming MQMD-REPLYTOQ; queue-manager destination not populated; no hardcoded authorization queue name (`A/cbl/COPAUA0C.cbl:233-240,411-414,741-748`). |
| Authorization README | AWS.M2.CARDDEMO.PAUTH.REQUEST and AWS.M2.CARDDEMO.PAUTH.REPLY are documented suggested names, **not proven installed MQ definitions** (`A/README.md:274-279`). |
| COACCT01 code | Request = trigger MQTM-QNAME; reply CARD.DEMO.REPLY.ACCT; error CARD.DEMO.ERROR (`V/cbl/COACCT01.cbl:191-198,294-296`). |
| CODATE01 code | Request = trigger MQTM-QNAME; reply CARD.DEMO.REPLY.DATE; error CARD.DEMO.ERROR (`V/cbl/CODATE01.cbl:140-147,243-245`). |
| VSAM/MQ README | QLOCAL CARDDEMO.REQUEST.QUEUE / CARDDEMO.RESPONSE.QUEUE; example MQCONN MQ01, MQQUEUE CARDREQ/CARDRES map to them. Different from code's fixed replies (`V/README.md:50-55,67-73`). |
| JCL/CSD | No queue names/trigger-process binding in supplied five JCL members or two CSDs; JCL is IMS-only and CSD resources are listed in §1 (`A/jcl/CBPAUP0J.jcl:24-47`; `A/jcl/DBPAUTP0.jcl:7-45`; `A/jcl/LOADPADB.JCL:26-50`; `A/jcl/UNLDGSAM.JCL:26-52`; `A/jcl/UNLDPADB.JCL:25-68`; `A/csd/CRDDEMO2.csd:1-79`; `V/csd/CRDDEMOM.csd:1-41`). |

## 6. Business rules and source-level quirks (not execution-tested defects)

1. **Approval is almost entirely a credit-limit comparison.** Existing summary limit/balance wins over account master; new summary fallback uses current balance. No expiry check, card status read, daily-limit calculation or automatic fraud-profile logic is implemented; 5600 profile paragraph is CONTINUE (`A/cbl/COPAUA0C.cbl:438-465,647-718`). Negative/zero amounts are not explicitly rejected in parser/decision (`:351-379,657-718`). Missing customer sets a warning flag but does not itself SET DECLINE-AUTH; reason3100 applies only if some other condition declines (`:583-609,665-717`).
2. Decline returns response05, zero approved amount; approval00/current amount. Reasons:0000 approval,3100 missing card/account/customer,4100 funds,4200 inactive card,4300 closed account,5100 card fraud,5200 merchant fraud,9000 other (`A/cbl/COPAUA0C.cbl:685-717`). Only funds flag is actually set by decision; inactive/closed/fraud flags have definitions and mapping but no setting in its procedure (`:137-145,438-718`). Detail display additionally knows4400 daily limit and5300 lost card, neither emitted by decision (`A/cbl/COPAUS1C.cbl:56-73`).
3. **Summary balance semantics differ for first vs subsequent requests.** New root initializes credit balance0 then adds approved amount, not account current balance. Next authorization compares limit against pending-only balance; existing root also overwrites stored limit after making decision using old limit (`A/cbl/COPAUA0C.cbl:665-679,801-821`). No card master read despite CARDDAT declaration (`:35-39,472-609`).
4. **Declined summary amount can use prior detail amount.** 8400 adds PA-TRANSACTION-AMT before 8500 fills that field with current request. There is no per-request detail initialization before this ordering (`A/cbl/COPAUA0C.cbl:790-791,819-821,854-885`). Leading explanation is direct source dataflow; actual first value depends on COBOL work-storage initialization and prior messages, not verified at runtime.
5. **MQ authorization request and reply are outside the CICS transaction.** GET/PUT1 NO-SYNCPOINT and reply precedes IMS write. Thus later IMS failure cannot atomically undo consumption/reply merely by CICS syncpoint (`A/cbl/COPAUA0C.cbl:334-336,389-390,461-465,753-765`). README's broad two-phase-commit claim should not be generalized to this MQ leg (`A/README.md:40-44`).
6. **Response pointer/length bug** and **amount staging width mismatch** are detailed in §5 (`A/cbl/COPAUA0C.cbl:46,63,722-730,756`; `A/cpy/CCPAURQY.cpy:27`). Parser does not clear all request/response fields per message or handle STRING ON OVERFLOW (`A/cbl/COPAUA0C.cbl:323-379,720-731`).
7. **Purge supplied configuration is aggressive:** zero-day cutoff; future dates survive but same-day and older qualify. YYDDD subtraction is wrong across year boundaries. Checkpoint threshold is >frequency, so control1 means after2 roots, not after1 (`A/jcl/CBPAUP0J.jcl:36-37`; `A/cbl/CBPAUP0C.cbl:160-169,280-293`). No match-state guard; duplicate-approved-count condition may delete a root with declined children. Summary decrements/credit-release are not persisted (§2) (`:156-158,277-370`).
8. **Fraud “removal” retains a Db2 row with R and updates report date**, not DELETE. Both F/R toggle from PF5; caller attempts Db2+IMS success with CICS SYNCPOINT, rolls back on returned SQL/IMS failure. This proves code intent, not installed distributed recovery configuration (`A/cbl/COPAUS1C.cbl:230-265,520-568`; `A/cbl/COPAUS2C.cbl:137,199-244`). Timestamp combines original date and processing time; retries/clock mismatch may identify a different key than original merchant timestamp (`A/cbl/COPAUS2C.cbl:103-111,171-172,226-228`).
9. **Navigation work-area asymmetry:** summary appends no100-byte fraud tail; detail appends one and both MOVE EIBCALEN bytes to their local group (COBOL MOVE truncates/pads to the receiving group; this alone is not an overrun). Separately, summary allocates only20 previous-page key slots without a visible >20 page bound; >100 authorizations risks subscripting past those slots (`A/cbl/COPAUS0C.cbl:117-126,200,365-440`; `A/cbl/COPAUS1C.cbl:109-120,171`). Array bounds handling is compiler/runtime-dependent, not tested.
10. **VSAM/MQ README describes clients, code implements triggered consumers.** No request sender/display receive flow, no REQUEST-ID handling, no use of ReplyToQ destination; fixed output queues, same request MsgId/CorrelId reused (`V/README.md:77-128`; `V/cbl/COACCT01.cbl:178-218,334-388,462-485`; `V/cbl/CODATE01.cbl:127-167,283-337,366-390`).
11. **Error reporting is not a substitute for rollback.** VSAM/MQ error MQPUT participates in SYNCPOINT, but error branches generally terminate via CICS RETURN, without explicit SYNCPOINT ROLLBACK; MQCLOSE failure can recurse through termination. No assertion is made about live CICS abnormal-end handling (`V/cbl/COACCT01.cbl:501-619`; `V/cbl/CODATE01.cbl:405-523`).
12. **Unload formats are three different things:** utility variable unload with headers (§8), flat100/206 files, GSAM100/200 files. They are not byte-interchangeable (§2; `A/jcl/DBPAUTP0.jcl:15-29`; `A/jcl/UNLDPADB.JCL:48-55`; `A/ims/PASFLDBD.DBD:27`; `A/ims/PADFLDBD.DBD:27`).
13. **Cross-message summary flag is not reset.** 5000 resets approval/card/account flags but not FOUND-PAUT-SMRY-SEG; if a prior request found a summary and the next card is absent from CCXREF, the guarded summary read is skipped but 6000 can still use the old summary branch. Thus “missing account declines” applies to the no-summary branch, not a guaranteed invariant over a multi-message invocation (`A/cbl/COPAUA0C.cbl:441-459,487-492,665-683`). No runtime request sequence was executed to demonstrate it.

## 7. Maps and terminal storage

Both BMS mapsets use CTRL=(ALARM,FREEKB), EXTATT=YES, LANG=COBOL, MODE=INOUT, STORAGE=AUTO, TIOAPFX=YES; map origin1,1 and size24x80 (`A/bms/COPAU00.bms:19-28`; `A/bms/COPAU01.bms:19-28`). Symbolic input/output groups overlay via REDEFINES; 12-byte prefix, per-field S9(4) COMP length, flag/attribute and extension bytes, data X(n). COPAU0AI/O and COPAU1AI/O are not MQ records (`A/cpy-bms/COPAU00.cpy:17-30,391-404`; `A/cpy-bms/COPAU01.cpy:17-30,181-194`).

* **COPAU0A/COPAU00:** header TRNNAME4/TITLE01,02=40/CURDATE8/PGMNAME8/CURTIME8; editable ACCTID11; name25/customer9/address25+25/account-status1/phone13; approved/declined counts3; credit limit/balance12, cash limit/balance9, approved/declined amounts10. Five rows each selection1, transaction16, date8, time8, type4, A/D1, status1, amount12. ERRMSG78. Screen inputs ACCTID and selections are unprotected; other named data fields ASKIP (`A/bms/COPAU00.bms:34-196,277-506`; `A/cpy-bms/COPAU00.cpy:19-390`). Row5 selection is declared **after** row5 data in symbolic layout, matching BMS source declaration order, not screen position (`A/cpy-bms/COPAU00.cpy:342-384`; `A/bms/COPAU00.bms:453-500`).
* **COPAU1A/COPAU01:** same header; CARDNUM16, AUTHDT10, AUTHTM10, AUTHRSP1, AUTHRSN20, AUTHCD6, AUTHAMT12, POSEMD4, AUTHSRC10, MCCCD4, CRDEXP5, AUTHTYP14, TRNID15, AUTHMTC1, AUTHFRD10, MERNAME25, MERID15, MERCITY25, MERST2, MERZIP10, ERRMSG78 (`A/cpy-bms/COPAU01.cpy:19-180`; `A/bms/COPAU01.bms:34-285`). Program actions, not typed field updates, mark fraud (§2).

## 8. EBCDIC data file: measured header, framing and record counts

File: `A/data/EBCDIC/AWS.M2.CARDDEMO.IMSDATA.DBPAUTP0.dat`, **51,736 bytes**. It appears to be DFSURGU0 utility unload output, consistent with DBPAUTP0 JCL's DFSRRC00 ULU,DFSURGU0,DBPAUTP0 → DFSURGU1 DSN AWS.M2.CARDDEMO.IMSDATA.DBPAUTP0, RECFM=VB LRECL27990 (`A/jcl/DBPAUTP0.jcl:7-32`). It is not a fixed200 detail file or the loader's100/206 pair.

Binary header bytes0..47 (hex):

```text
00580000 00800050 d7c1e4e3e2e4d4f0
00000000000000000000000000000000
00000000000000000000000001010072
```

EBCDIC cp037 bytes8..15 decode **PAUTSUM0**; bytes48..55 decode **PAUTDTL1**. First physical record length is 0x0058=88 from leading four-byte RDW. A second88-byte record closes the file (offset51648). The intervening records have lengths140 or240; each starts with big-endian length and two zero bytes. At offsets88 and228, their segment names begin at +10 and their payload begins at +39, with one trailing byte after the payload: root `PAUTSUM0`, declared payload length0x0064=100 at +8; child `PAUTDTL1`, declared payload length0x00c8=200 at +8. Those payload sizes match DBD100/200 and CIPAUSMY/CIPAUDTY (`A/ims/DBPAUTP0.dbd:28-37`; `A/cpy/CIPAUSMY.cpy:19-31`; `A/cpy/CIPAUDTY.cpy:19-54`). Binary offsets are zero-based.

Walking `offset += int.from_bytes(data[offset:offset+2], 'big')`, requiring length>=4, bytes2:4 zero, and end<=filesize, consumes **all51,736 bytes**, yielding **226 framed records: 2×88 metadata/header/trailer, 22×140 summary records, 202×240 detail records**. Thus **224 data segments =22 roots +202 children**. Not226 authorization transactions; authorization details number202. Counts of raw EBCDIC segment-name occurrences (24 root,204 child) include the two metadata records and would overcount actual segments. Format identification is inferred from framing, JCL and segment/payload agreement, not an IMS utility manual or restore test.

Reproduction (read-only; no dataset materialization):

```python
from collections import Counter
from pathlib import Path
p = Path('app/app-authorization-ims-db2-mq/data/EBCDIC/AWS.M2.CARDDEMO.IMSDATA.DBPAUTP0.dat')
b = p.read_bytes()
counts = Counter()
offset = 0
while offset < len(b):
    n = int.from_bytes(b[offset:offset + 2], 'big')
    assert n >= 4 and offset + n <= len(b)
    assert b[offset + 2:offset + 4] == b'\x00\x00'
    if n in (140, 240):
        tag = b[offset + 10:offset + 18].decode('cp037')
        payload = int.from_bytes(b[offset + 8:offset + 10], 'big')
        assert (tag, payload, n) in [('PAUTSUM0', 100, 140), ('PAUTDTL1', 200, 240)]
        counts[tag] += 1
    else:
        assert n == 88
        counts['metadata'] += 1
    offset += n
assert offset == len(b)
print(len(b), dict(counts))
```

## 9. Off-host runnability and existing overlap

**None of these ten is currently a self-contained, IMS/MQ/CICS-free batch program runnable by this repo's normal GnuCOBOL harness.** Static distinction: PAUDBLOD/PAUDBUNL/DBUNLDGS are conventional COBOL with ordinary CALL 'CBLTDLI', so compilation to a module with deferred external binding may be possible after correct copybook/fixed-column setup; successful compilation would not provide IMS, PCB setup, GSAM or DFSRRC00. They cannot run as supplied without those services. CBPAUP0C additionally needs EXEC DLI translation. Six online members (COPAUA0C, COPAUS0C, COPAUS1C, COPAUS2C, COACCT01, CODATE01) need CICS translation/runtime; COPAUA0C/COPAUS0C/COPAUS1C also EXEC DLI; COPAUA0C/COACCT01/CODATE01 MQ APIs/copybooks; COPAUS2C SQL precompilation and Db2-specific SQL conversion. Even CODATE01 depends on CICS clock/MQ, not a pure date subroutine (all interface citations in §2).

The existing build compiles app/cbl CB* plus specific utilities/stubs, explicitly omits online CO* because no CICS translator, and separately precompiles a different Db2 sub-app via ocesql. It does not select either inventoried sub-app directory (`tools/build.sh:10-18,30-65`). Runner dispatch handles IDCAMS/SORT/IEFBR14/IEBGENER/IKJEFT01 then falls back to cobcrun; no DFSRRC00/IMS launcher emulation in that dispatch (`tools/runjcl/runjcl.py:681-716`). Existing chains include posting/read-masters/statements/interest/trantype-update/trantype-dynamic, not these IMS jobs (`tools/runjcl/chains.json:1-169`). No compile/parity test was attempted for these unsupported programs.

Existing reusable overlap is substantial but **not an implemented authorization migration**:

* `tools/runjcl/chains.json:44-75` already exercises ACCTDATA300, CARDDATA150, CARDXREF50, CUSTDATA500 via READACCT/READCARD/READXREF/READCUST, and posting/interest use account/xref too (`:6-15,100-118`). These are base master datasets, not pending authorization segment stores.
* Field-level parity infrastructure decodes packed decimal, zoned overpunch and EBCDIC and compares reports/SYSOUT/RC/DB snapshots (`tools/parity/compare.py:1-16`). It could support new fixtures/layouts, but none of the six chain definitions currently describes these IMS/MQ programs (`tools/runjcl/chains.json:1-169`). No new fixtures/goldens were written.
* Java common VsamDataset already maps ACCTDATA→account/CVACT01Y, CARDDATA→card/CVACT02Y, CUSTDATA→customer/CVCUS01Y, CARDXREF→card_xref/CVACT03Y (`java/common/src/main/java/com/carddemo/batch/common/VsamDataset.java:7-14`). Thus reusable encoding/schema/master-transfer pieces exist, not CICS FILE name emulation or an IMS adapter.
* PostgreSQL **master** tables live in `java/common/src/main/resources/db/migration/V1__vsam_masters.sql`, not `db/postgres`: account numeric11 PK with status, balances/limits numeric12,2, dates char10, group; card char16 PK + account numeric11/CVV/name/expiry/status; customer numeric9 PK + identity/address/phone/etc; card_xref char16 card PK + customer numeric9/account numeric11/filler (`:3-60`). Account-ID xref access is a query, not another primary key (`:54-60`).
* Online API already ports account/xref/customer lookup: SELECT customer_id FROM card_xref WHERE account_id LIMIT2 and duplicate handling explicitly naming CXACAIX, then account and customer SELECTs (`carddemo-online-api/src/main/java/com/carddemo/account/AccountViewService.java:40-71`). This overlaps COPAUS0C's CXACAIX/ACCTDAT/CUSTDAT path and COACCT01's account read. Card master read is SELECT from card by card_number (`carddemo-online-api/src/main/java/com/carddemo/card/CardService.java:89-103`), overlapping CARDDAT conceptually, though authorization decision never actually reads it.
* API Maven resources package both db/migration and Java common VSAM migrations (latter target db/vsam); API datasource defaults PostgreSQL and Flyway locations db/migration (`carddemo-online-api/pom.xml:41-51`; `carddemo-online-api/src/main/resources/application.yml:4-15`). Packaging is not proof that every resource is automatically migrated into a given live DB.
* `db/postgres/01-transaction-type.sql:1-17` translates **transaction_type / transaction_type_categor**, not AUTHFRDS or IMS segments. Search of tools/, java/, carddemo-online-api and db/ found no AUTHFRDS, PAUTSUM, CBLTDLI, MQGET/MQPUT or DFSRRC00 implementation. This is a bounded repository search finding, not a claim that no IBM integration exists elsewhere.

## 10. Remaining evidence needed for operational claims

Actual MQ QLOCAL/PROCESS/trigger bindings, queue manager and remote ReplyToQMgr policy; installed IMS/CICS/Db2 resource definitions and translator/compiler listings; checkpoint/restart operating procedures; desired handling of README/code discrepancies. Supplied artifacts prove the static paths above, not live availability, successful recovery, throughput or parity. No screen was rendered; this deliverable is a source/data inventory, not a visual verification.
