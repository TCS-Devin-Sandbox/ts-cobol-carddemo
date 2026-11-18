# Discovery — CTLI (COTRTLIC) and CTTU (COTRTUPC) Transaction Type Maintenance

Source module: `app/app-transaction-type-db2/`. All line references are to the COBOL sequence area (cols 7–72) of the authoritative sources. Paragraph names, literals and SQLCODE branches are quoted verbatim.

| Artifact | Path | Role |
|---|---|---|
| COTRTLIC | `cbl/COTRTLIC.cbl` | CTLI — list / inline update / delete (7-row DB2 cursor paging) |
| COTRTUPC | `cbl/COTRTUPC.cbl` | CTTU — single-record search / add / edit / delete |
| COTRTLI / CTRTLIA | `bms/COTRTLI.bms`, `cpy-bms/COTRTLI.cpy` | CTLI 24x80 map |
| COTRTUP / CTRTUPA | `bms/COTRTUP.bms`, `cpy-bms/COTRTUP.cpy` | CTTU 24x80 map |
| TRANSACTION_TYPE | `ddl/TRNTYPE.ddl`, `ddl/XTRNTYPE.ddl`, `dcl/DCLTRTYP.dcl` | parent table |
| TRANSACTION_TYPE_CATEGORY | `ddl/TRNTYCAT.ddl`, `ddl/XTRNTYCAT.ddl`, `dcl/DCLTRCAT.dcl` | child table (FK ON DELETE RESTRICT) |
| Seed data | `ctl/DB2LTTYP.ctl`, `ctl/DB2LTCAT.ctl` via `jcl/CREADB21.jcl` | 7 types, 18 categories |
| CICS defs | `csd/CRDDEMOD.csd` | PROGRAM/TRANSACTION/MAPSET/DB2ENTRY/DB2TRAN |
| Shared copybooks | `cpy/CSDB2RPY.cpy` (9998-PRIMING-QUERY, 9999-FORMAT-DB2-MESSAGE), `cpy/CSDB2RWY.cpy`; base `app/cpy/COCOM01Y.cpy`, `CSSTRPFY.cpy`, `CSSETATY.cpy`, `COADM02Y.cpy` | |

## 1. CICS resource definitions (CRDDEMOD.csd)

| Resource | Definition |
|---|---|
| TRANSACTION(CTLI) | PROGRAM(COTRTLIC), PROFILE(DFHCICST), RESSEC(NO), CMDSEC(NO) |
| TRANSACTION(CTTU) | PROGRAM(COTRTUPC), same attributes |
| PROGRAM(COTRTLIC/COTRTUPC) | LANGUAGE(COBOL), CONCURRENCY(QUASIRENT) |
| MAPSET(COTRTLI) | "CREDIT CARD TRAN TYPE INQ MAP" |
| MAPSET(COTRTUP) | "CREDIT CARD TRAN TYPE MAINT MAP" |
| DB2ENTRY(CARDDEMO) | PLAN(CARDDEMO), AUTHTYPE(USERID), THREADLIMIT(1), THREADWAIT(YES), DROLLBACK(YES) |
| DB2TRAN(CTLITRAN/CTTUTRAN) | ENTRY(CARDDEMO) for CTLI / CTTU |

Security: RESSEC(NO)/CMDSEC(NO) — no resource-level security; access control is purely by menu reachability (admin menu CA00/COADM01C, options 5 and 6 in `COADM02Y.cpy`: `5 'Transaction Type List/Update (Db2) ' COTRTLIC`, `6 'Transaction Type Maintenance (Db2) ' COTRTUPC`). Both programs `SET CDEMO-USRTYP-ADMIN TO TRUE` on exit/entry.

## 2. COTRTLIC (CTLI) — paragraph inventory

| Paragraph | Purpose |
|---|---|
| 0000-MAIN | Init storage; restore COMMAREA (CARDDEMO-COMMAREA + WS-THIS-PROGCOMMAREA); map AID; reset on fresh entry; validate AID; F3 XCTL; F2 XCTL; DB2 priming; main EVALUATE dispatcher |
| COMMON-RETURN | Persist COMMAREA, `EXEC CICS RETURN TRANSID('CTLI') COMMAREA(WS-COMMAREA) LENGTH(2000)` |
| 1000-RECEIVE-MAP | PERFORM 1100 then 1200 |
| 1100-RECEIVE-SCREEN | `RECEIVE MAP('CTRTLIA') MAPSET('COTRTLI')`; copy TRTYPEI→WS-IN-TYPE-CD, TRDESCI→WS-IN-TYPE-DESC; per row: TRTSELI(I)→WS-EDIT-SELECT(I), TRTTYPI(I)→WS-ROW-TR-CODE-IN(I), TRTYPDI(I) (unless `*` or spaces) trimmed → WS-ROW-TR-DESC-IN(I) |
| 1200-EDIT-INPUTS | INPUT-OK; PERFORM 1210, 1230, 1220, 1290 (note: desc filter edited before type filter) |
| 1210-EDIT-ARRAY | Count actions (INSPECT TALLYING spaces/low-values, 'D', 'U'); validate each row select code; single-action rule; detect row-selection change vs WS-CA-ROW-SELECTED. Skipped (flags cleared) when either filter changed |
| 1211-EDIT-ARRAY-DESC | For a 'U' row: compare trimmed upper-case new vs old desc (and lengths) → 'No change detected with respect to database values.'; else edit via 1240 with name 'Transaction Desc', length 50 |
| 1220-EDIT-TYPECD | Type filter edit; exit section detects filter change and resets paging |
| 1230-EDIT-DESC | Desc filter edit → `'%' || TRIM(x) || '%'`; exit section detects change and resets paging |
| 1240-EDIT-ALPHANUM-REQD | Required + only A-Z a-z 0-9 space |
| 1290-CROSS-EDITS | If any filter valid → 9100-CHECK-FILTERS; COUNT=0 → error 'No Records found for these filter conditions', protect rows |
| 2000-SEND-MAP | PERFORM 2100, 2200, 2300, 2400, 2500, 2600 |
| 2100-SCREEN-INIT | Titles, TRNNAME='CTLI', PGMNAME='COTRTLIC', date mm/dd/yy, time hh:mm:ss, PAGENO=WS-CA-SCREEN-NUM, INFOMSG dark |
| 2200-SETUP-ARRAY-ATTRIBS | Per row attributes: desc protected by default; select protected for empty rows or when filters bad; red select on error; neutral highlight on the actioned row; desc unprotected+cursor for pending 'U'; red desc if invalid |
| 2300-SCREEN-ARRAY-INIT | Fill TRTTYPO/TRTYPDO/TRTSELO from COMMAREA rows; set CA-DELETE-REQUESTED / CA-UPDATE-REQUESTED; echo `*` for blank edited desc |
| 2400-SETUP-SCREEN-ATTRS | Filter field echo/protection (filters protected ASKIP+blue while an action is pending); red + cursor on bad filter |
| 2500-SETUP-MESSAGE | Choose INFOMSG / ERRMSG (see §6); centre INFOMSG in 45 chars |
| 2600-SEND-SCREEN | `SEND MAP ... CURSOR ERASE FREEKB` |
| 8000-READ-FORWARD | Open forward cursor, fetch up to 7 rows into COMMAREA, look-ahead fetch #8 for next-page flag, set first/last keys, screen number |
| 8100-READ-BACKWARDS | Open backward cursor, fill rows 7→1, set first key |
| 9100-CHECK-FILTERS | `SELECT COUNT(1)` with filter predicates |
| 9200-UPDATE-RECORD | `UPDATE ... SET TR_DESCRIPTION` |
| 9300-DELETE-RECORD | `DELETE FROM TRANSACTION_TYPE` |
| 9400/9450 | OPEN / CLOSE C-TR-TYPE-FORWARD |
| 9500/9550 | OPEN / CLOSE C-TR-TYPE-BACKWARD |
| 9998-PRIMING-QUERY (CSDB2RPY) | `SELECT 1 FROM SYSIBM.SYSDUMMY1` — 'Db2 access failure. ' on error |
| 9999-FORMAT-DB2-MESSAGE (CSDB2RPY) | `STRING TRIM(WS-DB2-CURRENT-ACTION) ' SQLCODE:' WS-DISP-SQLCODE(PIC ----9) ' ' DSNTIAC-text INTO WS-RETURN-MSG` (75 chars) |
| YYYY-STORE-PFKEY (CSSTRPFY) | EIBAID→CCARD-AID-*; PF13–24 fold to PF1–12 |
| SEND-PLAIN-TEXT / SEND-LONG-TEXT | Debug exits; SEND-LONG-TEXT used when priming query fails |

### 2.1 CTLI perform / call graph

```
0000-MAIN
 ├─ YYYY-STORE-PFKEY
 ├─ 1000-RECEIVE-MAP            (only if EIBCALEN>0 and FROM-PROGRAM='COTRTLIC')
 │   ├─ 1100-RECEIVE-SCREEN     (EXEC CICS RECEIVE MAP)
 │   └─ 1200-EDIT-INPUTS
 │       ├─ 1210-EDIT-ARRAY ── 1211-EDIT-ARRAY-DESC ── 1240-EDIT-ALPHANUM-REQD
 │       ├─ 1230-EDIT-DESC
 │       ├─ 1220-EDIT-TYPECD
 │       └─ 1290-CROSS-EDITS ── 9100-CHECK-FILTERS (SELECT COUNT)
 ├─ [PF3]  SYNCPOINT; XCTL PROGRAM(CDEMO-TO-PROGRAM → 'COADM01C')
 ├─ [PF2]  XCTL PROGRAM('COTRTUPC')
 ├─ 9998-PRIMING-QUERY ── 9999-FORMAT-DB2-MESSAGE ; on error SEND-LONG-TEXT
 ├─ EVALUATE dispatcher
 │   ├─ 8000-READ-FORWARD ── 9400-OPEN-FORWARD-CURSOR, FETCH×≤8, 9450-CLOSE-FORWARD-CURSOR
 │   ├─ 8100-READ-BACKWARDS ── 9500-OPEN-BACKWARD-CURSOR, FETCH×≤7, 9550-CLOSE-BACK-CURSOR
 │   ├─ 9200-UPDATE-RECORD (SYNCPOINT on 0)
 │   ├─ 9300-DELETE-RECORD (SYNCPOINT on 0)
 │   └─ 2000-SEND-MAP ── 2100,2200,2300,2400,2500,2600
 └─ COMMON-RETURN (RETURN TRANSID CTLI COMMAREA)
```

### 2.2 CTLI COMMAREA (WS-THIS-PROGCOMMAREA, appended after CARDDEMO-COMMAREA)

| Field | PIC | Meaning |
|---|---|---|
| WS-CA-TYPE-CD | X(2) | last type filter seen (change detection) |
| WS-CA-TYPE-DESC | X(50) | last desc filter seen |
| WS-CA-SCREEN-ROWS-OUT(7) | X(2)+X(50) | rows currently displayed (old values for change detection / update/delete keys) |
| WS-CA-ROW-SELECTED | S9(4) COMP | row index of last selection |
| WS-CA-LAST-TR-CODE | X(2) | last key on page (or look-ahead key, see §5.1) |
| WS-CA-FIRST-TR-CODE | X(2) | first key on page |
| WS-CA-SCREEN-NUM | 9(1) | page number (CA-FIRST-PAGE = 1) |
| WS-CA-LAST-PAGE-DISPLAYED | 9(1) | 0 = CA-LAST-PAGE-SHOWN, 9 = CA-LAST-PAGE-NOT-SHOWN |
| WS-CA-NEXT-PAGE-IND | X | 'Y' = CA-NEXT-PAGE-EXISTS, LOW-VALUES = not exists |
| WS-CA-DELETE-FLAG / WS-CA-UPDATE-FLAG | X | 'Y' = action pending F10 confirm |

## 3. COTRTUPC (CTTU) — paragraph inventory

| Paragraph | Purpose |
|---|---|
| 0000-MAIN | HANDLE ABEND; restore/initialise COMMAREA (fresh when EIBCALEN=0 or entered from COADM01C / COTRTLIC with PGM-ENTER); YYYY-STORE-PFKEY; 0001-CHECK-PFKEYS; reset-to-initial EVALUATE; main dispatcher |
| COMMON-RETURN | `RETURN TRANSID('CTTU') COMMAREA(WS-COMMAREA)` |
| 0001-CHECK-PFKEYS | Valid AIDs per state (see §5.3); else 'Invalid key pressed' |
| 1000-PROCESS-INPUTS | 1100, 1150, 1200 |
| 1100-RECEIVE-MAP | `RECEIVE MAP('CTRTUPA') MAPSET('COTRTUP ')` |
| 1150-STORE-MAP-IN-NEW | Move trimmed TRTYPCDI/TRTYDSCI into TTUP-NEW-*; `*` or spaces → LOW-VALUES; skipped if DETAILS-NOT-FOUND and same key and not F5 |
| 1200-EDIT-MAP-INPUTS | Key edit (1210) unless CREATE-NEW-RECORD / CHANGES-OK-NOT-CONFIRMED; then 1205 compare; then desc edit 1230; success → TTUP-CHANGES-OK-NOT-CONFIRMED |
| 1205-COMPARE-OLD-NEW | Upper-case/trim compare of type and desc → 'No change detected with respect to values fetched.' |
| 1210-EDIT-TRANTYPE | Name 'Tran Type code', length 2 → 1245; valid → NUMVAL, zero-pad to 2 digits |
| 1230-EDIT-ALPHANUM-REQD | Required, A-Z a-z 0-9 space only (name 'Transaction Desc', length 50) |
| 1245-EDIT-NUM-REQD | Required, TEST-NUMVAL, not zero |
| 2000-DECIDE-ACTION | State transitions after input (see §5.2); reads via 9000; OTHER → ABEND '0001' 'UNEXPECTED DATA SCENARIO' |
| 3000-SEND-MAP | 3100, 3200, 3250, 3300, 3390, 3391, 3400 |
| 3100-SCREEN-INIT | Titles, TRNNAME 'CTTU', PGMNAME 'COTRTUPC', date/time |
| 3200-SETUP-SCREEN-VARS | 3201 initial (blank) / 3202 original (old) / 3203 updated (new) values by state |
| 3250-SETUP-INFOMSG | INFOMSG by state (§6.2), centred in 40; ERRMSG = WS-RETURN-MSG |
| 3300-SETUP-SCREEN-ATTRS | 3310 protect all, 3320 unprotect desc; cursor position; red key on error/delete failed; `*` + red on blank key; CSSETATY for desc (`*` + red when blank) |
| 3390-SETUP-INFOMSG-ATTRS | dark if no message, else ASKIP+BRT |
| 3391-SETUP-PFKEY-ATTRS | Show/hide ENTER, F4, F5, F12 labels by state |
| 3400-SEND-SCREEN | `SEND MAP ... CURSOR ERASE FREEKB` |
| 9000-READ-TRANTYPE | Init old; 9100; on success 9500 |
| 9100-GET-TRANSACTION-TYPE | `SELECT TR_TYPE, TR_DESCRIPTION ... WHERE TR_TYPE = :DCL-TR-TYPE` |
| 9500-STORE-FETCHED-DATA | DCL → TTUP-OLD-* |
| 9600-WRITE-PROCESSING | `UPDATE`; +100 → 9700-INSERT-RECORD (upsert); state CHANGES-OKAYED-AND-DONE / -LOCK-ERROR / -BUT-FAILED |
| 9700-INSERT-RECORD | `INSERT INTO TRANSACTION_TYPE` |
| 9800-DELETE-PROCESSING | `DELETE` by TTUP-OLD-TTYP-TYPE |
| YYYY-STORE-PFKEY | CSSTRPFY |
| ABEND-ROUTINE | SEND ABEND-DATA, ABEND ABCODE |

### 3.1 CTTU perform / call graph

```
0000-MAIN
 ├─ YYYY-STORE-PFKEY
 ├─ 0001-CHECK-PFKEYS
 ├─ [PF3]   SYNCPOINT; XCTL PROGRAM(CDEMO-FROM-PROGRAM | 'COADM01C')
 ├─ [fresh] 3000-SEND-MAP (prompt for key)
 ├─ [PF4 & CONFIRM-DELETE]   9800-DELETE-PROCESSING ; 3000-SEND-MAP
 ├─ [PF4 & SHOW-DETAILS]     → CONFIRM-DELETE ; 3000-SEND-MAP
 ├─ [PF5 & DETAILS-NOT-FOUND]→ CREATE-NEW-RECORD ; 3000-SEND-MAP
 ├─ [PF5 & CHANGES-OK-NOT-CONFIRMED] 9600-WRITE-PROCESSING ── 9700-INSERT-RECORD ; 3000-SEND-MAP
 ├─ [PF12 & (OK-NOT-CONFIRMED|CONFIRM-DELETE|SHOW-DETAILS)] 2000-DECIDE-ACTION ── 9000-READ-TRANTYPE ; 3000-SEND-MAP
 ├─ [invalid key] 3000-SEND-MAP
 └─ [OTHER] 1000-PROCESS-INPUTS (1100,1150,1200 ── 1210 ── 1245, 1205, 1230)
            2000-DECIDE-ACTION ── 9000-READ-TRANTYPE ── 9100-GET-TRANSACTION-TYPE, 9500-STORE-FETCHED-DATA
            3000-SEND-MAP ── 3100,3200(3201|3202|3203),3250,3300(3310,3320,CSSETATY),3390,3391,3400
 COMMON-RETURN
```

### 3.2 CTTU COMMAREA (WS-THIS-PROGCOMMAREA)

| Field | PIC | Meaning |
|---|---|---|
| TTUP-CHANGE-ACTION | X | state (88-levels below) |
| TTUP-OLD-TTYP-TYPE / -DESC | X(2) / X(50) | values fetched from DB |
| TTUP-NEW-TTYP-TYPE / -DESC | X(2) / X(50) | values keyed by user |

TTUP-CHANGE-ACTION 88-levels: LOW-VALUES/SPACES = TTUP-DETAILS-NOT-FETCHED; 'K' INVALID-SEARCH-KEYS; 'X' DETAILS-NOT-FOUND; 'S' SHOW-DETAILS; 'R' CREATE-NEW-RECORD; 'V' REVIEW-NEW-RECORD (unused); '9' CONFIRM-DELETE; '8' START-DELETE; '7' DELETE-DONE; '6' DELETE-FAILED ('9','8','7','6' = DELETE-IN-PROGRESS); 'E' CHANGES-NOT-OK; 'N' CHANGES-OK-NOT-CONFIRMED; 'L' CHANGES-OKAYED-LOCK-ERROR; 'F' CHANGES-OKAYED-BUT-FAILED ('L','F' = CHANGES-FAILED; 'E','N','L','F' = CHANGES-MADE); 'C' CHANGES-OKAYED-AND-DONE; 'B' CHANGES-BACKED-OUT.

## 4. BMS field inventory

Attribute key: ASKIP = autoskip/protected, PROT = protected, UNPROT = unprotected (input), NUM = numeric-only (none of these maps use NUM), IC = initial cursor, FSET = modified data tag on, BRT/NORM/DRK = intensity. I/O: O = output only, I/O = input and output. Program-time overrides are listed where the COBOL changes the attribute.

### 4.1 COTRTLI / CTRTLIA (CTLI, 24x80, CTRL=FREEKB)

| Field | Pos (row,col) | Len | Attributes (BMS) | Initial | I/O | Program override |
|---|---|---|---|---|---|---|
| (label) | 1,1 | 5 | ASKIP,NORM blue | `Tran:` | O | |
| TRNNAME | 1,7 | 4 | ASKIP,FSET,NORM blue | | O | 'CTLI' |
| TITLE01 | 1,21 | 40 | ASKIP,NORM yellow | | O | CCDA-TITLE01 |
| (label) | 1,65 | 5 | ASKIP | `Date:` | O | |
| CURDATE | 1,71 | 8 | ASKIP blue | `mm/dd/yy` | O | current date |
| (label) | 2,1 | 5 | ASKIP | `Prog:` | O | |
| PGMNAME | 2,7 | 8 | ASKIP blue | | O | 'COTRTLIC' |
| TITLE02 | 2,21 | 40 | ASKIP yellow | | O | CCDA-TITLE02 |
| (label) | 2,65 | 5 | ASKIP | `Time:` | O | |
| CURTIME | 2,71 | 8 | ASKIP blue | `hh:mm:ss` | O | current time |
| (label) | 4,28 | 25 | neutral | `Maintain Transaction Type` | O | |
| (label) | 4,70 | 5 | | `Page ` | O | |
| PAGENO | 4,76 | 3 | (default ASKIP) | | O | WS-CA-SCREEN-NUM |
| (label) | 6,30 | 12 | ASKIP turquoise | `Type Filter:` | O | |
| TRTYPE | 6,44 | 2 | FSET,IC,NORM,UNPROT green underline | | I/O | ASKIP+FSET blue while action pending; red + cursor if invalid |
| (label) | 8,4 | 19 | ASKIP turquoise | `Description Filter:` | O | |
| TRDESC | 8,25 | 50 | FSET,NORM,UNPROT green underline | | I/O | ASKIP+FSET blue while action pending; red + cursor if no rows |
| (headers) | 10,4 / 10,16 / 10,42 | 10/4/11 | neutral | `Select ` / `Type` / `Description` | O | |
| (rules) | 11,4 / 11,15 / 11,25 | 6/5/50 | neutral | dashes | O | |
| TRTSEL1..7 | 12..18,6 | 1 | FSET,NORM,PROT underline | | I/O | unprotected (DFHBMFSE) when the row has data and filters OK; PROT (DFHBMPRO) for empty rows; red + cursor on invalid action |
| TRTTYP1..7 | 12..18,17 | 2 | FSET,NORM,PROT | | O | neutral colour on actioned row |
| TRTYPD1..7 | 12..18,25 | 50 | FSET,NORM,UNPROT (BMS) | | I/O | set to DFHBMPRF (protected) every send; unprotected + cursor only on the row with pending 'U'; red if invalid; `*` shown when edited value blank |
| TRTSELA/TRTTYPA/TRTDSCA | 19,6/17/25 | 1/2/50 | FSET,NORM,PROT | | O | not referenced by the program (8th template row) |
| INFOMSG | 21,19 | 45 | PROT neutral | | O | centred info text; DFHBMDAR (dark) unless a message is set |
| ERRMSG | 23,1 | 78 | ASKIP,BRT,FSET red | | O | WS-RETURN-MSG |
| BUTNF02 | 24,1 | 7 | ASKIP turquoise | `F2=Add` | O | |
| BUTNF03 | 24,10 | 7 | ASKIP | `F3=Exit` | O | |
| BUTNF07 | 24,19 | 10 | ASKIP | `F7=Page Up` | O | |
| BUTNF08 | 24,32 | 10 | ASKIP | `F8=Page Dn` | O | |
| BUTNF10 | 24,44 | 8 | ASKIP | `F10=Save` | O | |

### 4.2 COTRTUP / CTRTUPA (CTTU, 24x80, CTRL=FREEKB)

| Field | Pos | Len | Attributes (BMS) | Initial | I/O | Program override |
|---|---|---|---|---|---|---|
| (label) / TRNNAME | 1,1 / 1,7 | 5 / 4 | ASKIP | `Tran:` / – | O | 'CTTU' |
| TITLE01 | 1,21 | 40 | ASKIP yellow | | O | |
| CURDATE | 1,71 | 8 | ASKIP | `mm/dd/yy` | O | |
| PGMNAME | 2,7 | 8 | ASKIP | | O | 'COTRTUPC' |
| TITLE02 | 2,21 | 40 | ASKIP | | O | |
| CURTIME | 2,71 | 8 | ASKIP | `hh:mm:ss` | O | |
| (label) | 7,28 | 25 | neutral | `Maintain Transaction Type` | O | |
| (label) | 12,4 | 19 | ASKIP turquoise | `Transaction Type  :` | O | |
| TRTYPCD | 12,26 | 2 | IC,UNPROT underline | | I/O | protected (DFHBMPRF) unless state ∈ {NOT-FETCHED, INVALID-SEARCH-KEYS, DETAILS-NOT-FOUND, BACKED-OUT with no old key}; red on invalid/delete-failed; `*` + red when blank on re-entry |
| (label) | 14,4 | 19 | turquoise | `Description       :` | O | |
| TRTYDSC | 14,26 | 50 | UNPROT underline | | I/O | protected unless state ∈ {SHOW-DETAILS, CHANGES-NOT-OK, CREATE-NEW-RECORD, CHANGES-BACKED-OUT}; red (+`*` if blank) on error |
| INFOMSG | 22,23 | 45 | ASKIP neutral | | O | centred (in 40) info text; dark when none |
| ERRMSG | 23,1 | 78 | ASKIP,BRT,FSET red | | O | WS-RETURN-MSG |
| FKEYS | 24,1 | 21 | ASKIP,NORM yellow | `ENTER=Process F3=Exit` | O | dark when CONFIRM-DELETE |
| FKEY04 | 24,23 | 9 | ASKIP,DRK | `F4=Delete` | O | bright when SHOW-DETAILS or CONFIRM-DELETE |
| FKEY05 | 24,33 | 8 | ASKIP,DRK | `F5=Save` | O | bright when CHANGES-OK-NOT-CONFIRMED or DETAILS-NOT-FOUND |
| FKEY06 | 24,43 | 6 | ASKIP,DRK | `F6=Add` | O | never shown (no program reference) |
| FKEY12 | 24,69 | 10 | ASKIP,DRK | `F12=Cancel` | O | bright when OK-NOT-CONFIRMED, SHOW-DETAILS, DETAILS-NOT-FOUND, CONFIRM-DELETE, CREATE-NEW-RECORD |

## 5. SQL inventory

Host structures (DCLGEN): `DCL-TR-TYPE PIC X(2)`, `DCL-TR-DESCRIPTION` = `DCL-TR-DESCRIPTION-LEN S9(4) COMP` + `DCL-TR-DESCRIPTION-TEXT X(50)` (VARCHAR). WS-DISP-SQLCODE `PIC ----9`.

### 5.1 CTLI

| # | Statement | Paragraph | Host vars | SQLCODE branches |
|---|---|---|---|---|
| L1 | `DECLARE C-TR-TYPE-FORWARD CURSOR FOR SELECT TR_TYPE, TR_DESCRIPTION FROM CARDDEMO.TRANSACTION_TYPE WHERE TR_TYPE >= :WS-START-KEY AND ((:WS-EDIT-TYPE-FLAG='1' AND TR_TYPE=:WS-TYPE-CD-FILTER) OR (:WS-EDIT-TYPE-FLAG<>'1')) AND ((:WS-EDIT-DESC-FLAG='1' AND TR_DESCRIPTION LIKE TRIM(:WS-TYPE-DESC-FILTER)) OR (:WS-EDIT-DESC-FLAG<>'1')) ORDER BY TR_TYPE` | 9400 OPEN / 8000 FETCH / 9450 CLOSE | in: WS-START-KEY, WS-EDIT-TYPE-FLAG, WS-TYPE-CD-FILTER, WS-EDIT-DESC-FLAG, WS-TYPE-DESC-FILTER; out: DCL-TR-TYPE, DCL-TR-DESCRIPTION | OPEN ≠0 → WS-DB2-ERROR, 'C-TR-TYPE-FORWARD Open'. FETCH 0 → row; at row 7 look-ahead FETCH: 0 → CA-NEXT-PAGE-EXISTS and **WS-CA-LAST-TR-CODE = 8th key**; +100 → CA-NEXT-PAGE-NOT-EXISTS (+ 'No more pages for these search conditions' if PF8); other → 'C-TR-TYPE-FORWARD fetch'. FETCH +100 → next page not exists, msg as above; if screen 1 and 0 rows → 'No records found for this search condition.'. FETCH other <0 → WS-DB2-ERROR, 'C-TR-TYPE-FORWARD close'. CLOSE ≠0 → 'C-TR-TYPE-FORWARD close' |
| L2 | `DECLARE C-TR-TYPE-BACKWARD CURSOR FOR SELECT ... WHERE TR_TYPE < :WS-START-KEY AND (same filters) ORDER BY TR_TYPE DESC` | 9500 / 8100 / 9550 | same | OPEN ≠0 → 'C-TR-TYPE-BACKWARD Open'. FETCH 0 → row placed at position 7..1, first key = row 1; FETCH other (incl. +100) → READ-LOOP-EXIT, WS-DB2-ERROR, 'Error on fetch Cursor C-TR-TYPE-BACKWARD'. CLOSE ≠0 → 'C-TR-TYPE-BACKWARD close' |
| L3 | `SELECT COUNT(1) INTO :WS-RECORDS-COUNT FROM CARDDEMO.TRANSACTION_TYPE WHERE (same filter predicates)` | 9100-CHECK-FILTERS | WS-RECORDS-COUNT | 0 → continue (count=0 → 'No Records found for these filter conditions' in 1290); other → INPUT-ERROR, 'Error reading TRANSACTION_TYPE table ' |
| L4 | `UPDATE CARDDEMO.TRANSACTION_TYPE SET TR_DESCRIPTION = :DCL-TR-DESCRIPTION WHERE TR_TYPE = :DCL-TR-TYPE` | 9200-UPDATE-RECORD | DCL-TR-TYPE ← WS-ROW-TR-CODE-IN(I-SELECTED); DESC-TEXT ← TRIM(row desc) | 0 → SYNCPOINT, CA-UPDATE-SUCCEEDED, 'HIGHLIGHTED row was updated'; +100 → 'Record not found. Deleted by others ? '; **-911** → INPUT-ERROR, 'Deadlock. Someone else updating ?'; <0 → 'Update failed with' |
| L5 | `DELETE FROM CARDDEMO.TRANSACTION_TYPE WHERE TR_TYPE = :DCL-TR-TYPE` | 9300-DELETE-RECORD | DCL-TR-TYPE ← WS-ROW-TR-CODE-IN(I-SELECTED) | 0 → SYNCPOINT, CA-DELETE-SUCCEEDED, 'HIGHLIGHTED row deleted.Hit Enter to continue'; **-532** → 'Please delete associated child records first:'; other → 'Delete failed with message:' |
| L6 | `SELECT 1 INTO :WS-DUMMY-DB2-INT FROM SYSIBM.SYSDUMMY1 FETCH FIRST 1 ROW ONLY` | 9998-PRIMING-QUERY | | ≠0 → 'Db2 access failure. ' then SEND-LONG-TEXT + RETURN |

All DB2-error messages pass through 9999-FORMAT-DB2-MESSAGE: `<action trimmed> SQLCODE:<----9> <DSNTIAC text>`, e.g. `Please delete associated child records first: SQLCODE: -532 ...`.

Note: the type filter predicate uses `TR_TYPE = :WS-TYPE-CD-FILTER` (exact match); the description filter is `LIKE '%<trim(input)>%'` (case-sensitive, DB2 default collation). The keyset predicate (`>=` / `<`) is combined with the filters.

### 5.2 CTTU

| # | Statement | Paragraph | Host vars | SQLCODE branches |
|---|---|---|---|---|
| U1 | `SELECT TR_TYPE, TR_DESCRIPTION INTO :DCL-TR-TYPE, :DCL-TR-DESCRIPTION FROM CARDDEMO.TRANSACTION_TYPE WHERE TR_TYPE = :DCL-TR-TYPE` | 9100-GET-TRANSACTION-TYPE | DCL-TR-TYPE ← TTUP-NEW-TTYP-TYPE | 0 → FOUND-TRANTYPE-IN-TABLE; +100 → INPUT-ERROR, FLG-TRANFILTER-NOT-OK, 'No record found for this key in database'; <0 → `'Error accessing: TRANSACTION_TYPE table. SQLCODE:' WS-DISP-SQLCODE ':' SQLERRM` |
| U2 | `UPDATE CARDDEMO.TRANSACTION_TYPE SET TR_DESCRIPTION = :DCL-TR-DESCRIPTION WHERE TR_TYPE = :DCL-TR-TYPE` | 9600-WRITE-PROCESSING | from TTUP-NEW-* | 0 → SYNCPOINT → CHANGES-OKAYED-AND-DONE ('Changes committed to database'); **+100 → 9700 INSERT (upsert)**; **-911** → INPUT-ERROR, 'Could not lock record for update' → CHANGES-OKAYED-LOCK-ERROR; <0 → 'Update of record failed' then overwritten by `'Error updating: TRANSACTION_TYPE Table. SQLCODE:' code ':' SQLERRM` → CHANGES-OKAYED-BUT-FAILED |
| U3 | `INSERT INTO CARDDEMO.TRANSACTION_TYPE (TR_TYPE, TR_DESCRIPTION) VALUES (:DCL-TR-TYPE, :DCL-TR-DESCRIPTION)` | 9700-INSERT-RECORD | same | 0 → SYNCPOINT; other (incl. **-803 duplicate key**) → TABLE-UPDATE-FAILED, `'Error inserting record into: TRANSACTION_TYPE Table. SQLCODE:' code ':' SQLERRM` → CHANGES-OKAYED-BUT-FAILED ('Changes unsuccessful'). There is no dedicated -803 branch; -803 is only reachable on a race (row inserted between the +100 UPDATE and the INSERT). |
| U4 | `DELETE FROM CARDDEMO.TRANSACTION_TYPE WHERE TR_TYPE = :DCL-TR-TYPE` | 9800-DELETE-PROCESSING | DCL-TR-TYPE ← TTUP-OLD-TTYP-TYPE | 0 → DELETE-DONE + SYNCPOINT ('Delete successful.'); **-532** → RECORD-DELETE-FAILED, `'Please delete associated child records first:' 'SQLCODE :' code ':' SQLERRM SQLERRM` (state stays START-DELETE '8' → next ENTER resets); other → DELETE-FAILED ('Changes unsuccessful'), `'Delete failed with message:' 'SQLCODE :' code ':' SQLERRM` |

CTTU does not run the priming query.

## 6. Behavioral inventory

### 6.1 CTLI — list / update / delete

1. **Entry / reset.** EIBCALEN=0, or `CDEMO-PGM-ENTER` from a different program, or PF3 coming back from CTTU → initialise program COMMAREA, page 1, `CA-LAST-PAGE-NOT-SHOWN`, treat as ENTER, read forward from LOW-VALUES (first page).
2. **AID validation.** Valid: ENTER, PF2, PF3, PF7, PF8, PF10 only when a delete or update is pending. Any other key → treated as ENTER (no error message).
3. **PF3 Exit.** To-tranid = CDEMO-FROM-TRANID unless blank/low/'CTLI' → 'CA00'; to-program = CDEMO-FROM-PROGRAM unless blank/low/'COTRTLIC' → 'COADM01C'. Because COMMON-RETURN always stores FROM-PROGRAM='COTRTLIC', PF3 effectively always XCTLs to COADM01C. SYNCPOINT then XCTL.
4. **PF2 Add.** Only if FROM-PROGRAM='COTRTLIC' (i.e. re-entry): XCTL to COTRTUPC with CDEMO-PGM-ENTER (CTTU starts fresh, prompting for key).
5. **Paging — 7 rows (WS-MAX-SCREEN-LINES = 7), keyset on TR_TYPE.**
   - ENTER / OTHER: re-read forward from WS-CA-FIRST-TR-CODE (refresh current page).
   - PF8 with CA-NEXT-PAGE-EXISTS: start key = WS-CA-LAST-TR-CODE (which after look-ahead holds the 8th key, so `>=` starts exactly at the next unseen row); screen number +1.
   - PF8 without next page: falls to OTHER (refresh); message 'No more pages to display' if last page already shown, else info 'Type U to update, D to delete any record' and mark CA-LAST-PAGE-SHOWN. During 8000, a +100 with PF8 sets 'No more pages for these search conditions' when no other message.
   - PF7 on page 1: re-read forward from first key; message 'No previous pages to display'.
   - PF7 otherwise: start key = first key; backward cursor `< key DESC` fills 7 rows bottom-up; screen number −1. Fewer than 7 previous rows → +100 path sets WS-DB2-ERROR with 'Error on fetch Cursor C-TR-TYPE-BACKWARD' (only reachable if page numbering and data drift — e.g. rows deleted — since earlier pages were full).
   - Look-ahead: after the 7th row a further FETCH decides CA-NEXT-PAGE-EXISTS.
   - Page number PAGENO = WS-CA-SCREEN-NUM (set to 1 on first fetched row when 0).
6. **Filters.**
   - Type filter (TRTYPE, 2 chars): blank/low-values/'00' → no filter. Otherwise must be NUMERIC (both chars digits) else 'TYPE CODE FILTER,IF SUPPLIED MUST BE A 2 DIGIT NUMBER' (rows protected, field red). Valid → exact match `TR_TYPE = filter`.
   - Desc filter (TRDESC, 50 chars): blank → none; else `LIKE '%'||TRIM(x)||'%'`.
   - If any valid filter → COUNT(*); 0 → 'No Records found for these filter conditions' (filters red, rows protected, no data read).
   - Changing a filter value vs COMMAREA resets paging (INITIALIZE WS-CA-PAGING-VARIABLES → first page) and discards any row selections.
7. **Row actions.** Select field per row accepts 'U' (update) or 'D' (delete); blank = none.
   - Any other character → 'Action code selected is invalid' (row select red).
   - More than one non-blank select field (valid or not) → 'Please select only 1 action' (every actioned row red); this message overrides the invalid-code message because it is set after the row loop.
   - Empty rows have protected select fields.
8. **Paging messages (2500).** First F8 with no next page (or arrival on the last page via F8) → ERRMSG 'No more pages for these search conditions' (8000 +100 with PF8) and CA-LAST-PAGE-SHOWN; subsequent F8 → 'No more pages to display'.
9. **Delete flow.** ENTER with one 'D' → row highlighted, info 'Delete HIGHLIGHTED row ? Press F10 to confirm', filters protected, CA-DELETE-REQUESTED. F10 (only valid if a delete is pending and filters/row selection unchanged — otherwise treated as ENTER) → 9300 DELETE. Success → info 'HIGHLIGHTED row deleted.Hit Enter to continue', COMMAREA reset to page 1. -532 → 'Please delete associated child records first: SQLCODE: -532 …'. Other → 'Delete failed with message: SQLCODE: …'.
10. **Update flow.** ENTER with one 'U' → description of that row becomes editable (cursor there), info 'Update HIGHLIGHTED row. Press F10 to save'. On subsequent ENTER/F10 the edited description is validated: unchanged (case-insensitive, trimmed) → 'No change detected with respect to database values.'; blank → 'Transaction Desc must be supplied.' (`*` echoed); non alphanumeric/space → 'Transaction Desc can have numbers or alphabets only.'. F10 with pending update → 9200 UPDATE; success → 'HIGHLIGHTED row was updated' and page refreshed; +100 → 'Record not found. Deleted by others ? SQLCODE:  100'; -911 → 'Deadlock. Someone else updating ? SQLCODE: -911'; other <0 → 'Update failed with SQLCODE: …'.
   - Note: the source's 9200 is reached on F10 even when 1211 found no change or an invalid description because INPUT-ERROR is evaluated first in the dispatcher (WHEN INPUT-ERROR re-displays with the error). Thus invalid input never reaches UPDATE.
11. **Info line default.** When no other message and data shown → 'Type U to update, D to delete any record'. Empty table first page → ERRMSG 'No records found for this search condition.' (info suppressed).
12. **DB2 down.** Priming query failure → full-screen text 'Db2 access failure.  SQLCODE: …' and RETURN (no map).

### 6.2 CTTU — add / edit / delete state machine

States (TTUP-CHANGE-ACTION) and INFOMSG (3250):

| State | INFOMSG | Editable | PF keys shown |
|---|---|---|---|
| (PGM-ENTER) / DETAILS-NOT-FETCHED / INVALID-SEARCH-KEYS | 'Enter transaction type to be maintained' | type | ENTER, F3 |
| DETAILS-NOT-FOUND ('X') | 'Press F05 to add. F12 to cancel' | type | ENTER, F3, F5, F12 |
| CREATE-NEW-RECORD ('R') | 'Enter new transaction type details.' | desc | ENTER, F3, F12 |
| SHOW-DETAILS ('S') | 'Enter transaction type to be maintained' (see note) | desc | ENTER, F3, F4, F12 |
| CHANGES-NOT-OK ('E') / CHANGES-BACKED-OUT ('B') | 'Update transaction type details shown.' | desc | ENTER, F3 |
| CHANGES-OK-NOT-CONFIRMED ('N') | 'Changes validated.Press F5 to save' | none | ENTER, F3, F5, F12 |
| CHANGES-OKAYED-AND-DONE ('C') | 'Changes committed to database' | none | ENTER, F3 |
| CHANGES-OKAYED-LOCK-ERROR ('L') / -BUT-FAILED ('F') | 'Changes unsuccessful' | none | ENTER, F3 |
| CONFIRM-DELETE ('9') | 'Delete this record ? Press F4 to confirm' | none | F3, F4, F12 (ENTER hidden) |
| DELETE-DONE ('7') | 'Delete successful.' | none | ENTER, F3 |
| DELETE-FAILED ('6') | 'Changes unsuccessful' | none | ENTER, F3 |

Note on 3250: the EVALUATE codes `WHEN TTUP-SHOW-DETAILS` immediately followed by `WHEN TTUP-CHANGES-BACKED-OUT AND (TTUP-OLD-TTYP-TYPE = LOW-VALUES OR SPACES)`. COBOL consecutive WHENs share the next imperative statement, so SHOW-DETAILS displays **'Enter transaction type to be maintained'** (PROMPT-FOR-SEARCH-KEYS). 'Update transaction type details shown.' (PROMPT-FOR-CHANGES) is shown for CHANGES-NOT-OK and CHANGES-BACKED-OUT with an old key. The target reproduces this exactly.

Transitions:

```
          ENTER(valid key, found)                 ENTER(changed desc, valid)        F5
 NOT-FETCHED ───────────────────────► SHOW-DETAILS ──────────────────────► CHANGES-OK-NOT-CONFIRMED ───► 9600 UPDATE
   ▲  │ ENTER(key not found)               │  │ ENTER(invalid desc)                    │ F12 → 'Update was cancelled' → BACKED-OUT ├─0 / +100→INSERT ok → CHANGES-OKAYED-AND-DONE → (next key) → SHOW-DETAILS…
   │  ▼                                    │  ▼                                        │                                  ├─ -911 → LOCK-ERROR → reset NOT-FETCHED
   │ DETAILS-NOT-FOUND ──F5──► CREATE-NEW-RECORD ──ENTER(valid desc)──► CHANGES-OK-NOT-CONFIRMED            └─ <0 → BUT-FAILED → reset NOT-FETCHED
   │  │ F12 → re-read (not found) stays   │ CHANGES-NOT-OK (stay until valid)
   │  │                                    │ F4
   │  │                                    ▼
   │  │                               CONFIRM-DELETE ──F4──► START-DELETE → 9800 DELETE ─0→ DELETE-DONE
   │  │                                    │ F12 → 'Delete was cancelled', blank screen ├─ -532 → stays '8' (message), next ENTER re-edits with blank desc
   │  │                                    │                                          └─ other → DELETE-FAILED
   └──┴── reset at top of 0000-MAIN when: PF12 & (SHOW|CREATE-NEW|NOT-FOUND), CHANGES-OKAYED-AND-DONE, CHANGES-FAILED, BACKED-OUT with no old data, DELETE-DONE, DELETE-FAILED
```

Reset rule (0000-MAIN, first EVALUATE): on the interaction *after* PF12 in SHOW-DETAILS/CREATE-NEW-RECORD/DETAILS-NOT-FOUND, or after CHANGES-OKAYED-AND-DONE, CHANGES-FAILED, DELETE-DONE, DELETE-FAILED (or BACKED-OUT with no old data), the program sets CDEMO-PGM-ENTER + DETAILS-NOT-FETCHED, initialises its COMMAREA and redisplays the blank search screen ('Enter transaction type to be maintained'). Re-entering the same not-found key with ENTER re-reads it (1200: DETAILS-NOT-FOUND + same key → NOT-FETCHED). In CREATE-NEW-RECORD the key is not re-edited (1200 skips 1210); an invalid description moves to CHANGES-NOT-OK and stays there until valid.

Key handling (0001-CHECK-PFKEYS — valid combinations; others → 'Invalid key pressed' and screen redisplayed):

| Key | Valid when | Effect |
|---|---|---|
| PF3 | always | SYNCPOINT; XCTL to CDEMO-FROM-PROGRAM (COTRTLIC when reached via CTLI F2, COADM01C when via admin menu; blank → COADM01C/CA00) |
| ENTER | not CONFIRM-DELETE | process inputs (1000 → 2000) |
| PF4 | SHOW-DETAILS, CONFIRM-DELETE | SHOW-DETAILS → CONFIRM-DELETE; CONFIRM-DELETE → START-DELETE → 9800 |
| PF5 | DETAILS-NOT-FOUND, CHANGES-OK-NOT-CONFIRMED, DELETE-IN-PROGRESS ('9','8','7','6') | DETAILS-NOT-FOUND → CREATE-NEW-RECORD; CHANGES-OK-NOT-CONFIRMED → save (9600); DELETE-IN-PROGRESS → OTHER (processed like ENTER) |
| PF12 | OK-NOT-CONFIRMED, CONFIRM-DELETE, SHOW-DETAILS, DETAILS-NOT-FOUND, CREATE-NEW-RECORD | SHOW-DETAILS / DETAILS-NOT-FOUND / CREATE-NEW-RECORD → reset (blank search screen). OK-NOT-CONFIRMED / CONFIRM-DELETE → 2000-DECIDE-ACTION with FLG-TRANFILTER-ISVALID not set (1000 not performed) → CONFIRM-DELETE: 'Delete was cancelled' + DETAILS-NOT-FETCHED; OK-NOT-CONFIRMED: 'Update was cancelled' + CHANGES-BACKED-OUT (original values shown, description editable) |

Field edits:

| Field | Rule (paragraph) | Message literal |
|---|---|---|
| Type code (TRTYPCD) | blank / `*` / spaces (1245) | 'Tran Type code must be supplied.' — note 1200 overrides with 'No input received' only if no message set; since 1245 sets the message first, the displayed text is **'Tran Type code must be supplied.'** |
| | TEST-NUMVAL ≠ 0 (1245) | 'Tran Type code must be numeric.' |
| | NUMVAL = 0 (1245) | 'Tran Type code must not be zero.' |
| | valid → NUMVAL, zero-padded to 2 digits (1210), e.g. `5` → `05` | |
| | not in DB (9100 +100) | 'No record found for this key in database' |
| Description (TRTYDSC) | blank (1230) | 'Transaction Desc must be supplied.' |
| | chars outside A-Z a-z 0-9 space (1230) | 'Transaction Desc can have numbers or alphabets only.' |
| | unchanged vs fetched (1205, upper/trim) | 'No change detected with respect to values fetched.' |
| | max 50 (BMS LENGTH=50) | (truncation by terminal) |

### 6.3 Complete message literal catalogue

CTLI (COTRTLIC): 'Type U to update, D to delete any record'; 'Delete HIGHLIGHTED row ? Press F10 to confirm'; 'Update HIGHLIGHTED row. Press F10 to save'; 'HIGHLIGHTED row deleted.Hit Enter to continue'; 'HIGHLIGHTED row was updated'; 'PF03 pressed. Exiting'; 'No records found for this search condition.'; 'No more pages for these search conditions'; 'Please select only 1 action'; 'Action code selected is invalid'; 'No change detected with respect to database values.'; 'TYPE CODE FILTER,IF SUPPLIED MUST BE A 2 DIGIT NUMBER'; 'No Records found for these filter conditions'; 'No previous pages to display'; 'No more pages to display'; 'Transaction Desc must be supplied.'; 'Transaction Desc can have numbers or alphabets only.'; DB2 actions: 'Db2 access failure. ', 'C-TR-TYPE-FORWARD Open', 'C-TR-TYPE-FORWARD fetch', 'C-TR-TYPE-FORWARD close', 'C-TR-TYPE-BACKWARD Open', 'C-TR-TYPE-BACKWARD close', 'Error on fetch Cursor C-TR-TYPE-BACKWARD', 'Error reading TRANSACTION_TYPE table ', 'Record not found. Deleted by others ? ', 'Deadlock. Someone else updating ?', 'Update failed with', 'Please delete associated child records first:', 'Delete failed with message:'.

CTTU (COTRTUPC): info — 'Selected transaction type shown above' (unused), 'Enter transaction type to be maintained', 'Press F05 to add. F12 to cancel', 'Delete this record ? Press F4 to confirm', 'Delete successful.', 'Update transaction type details shown.', 'Enter new transaction type details.', 'Changes validated.Press F5 to save', 'Changes committed to database', 'Changes unsuccessful'. Return — 'PF03 pressed.Exiting', 'Invalid Key pressed. ' (unused), 'Name can only contain alphabets and spaces' (unused), 'No record found for this key in database', 'No input received', 'No change detected with respect to values fetched.', 'Could not lock record for update', 'Record changed by some one else. Please review' (never set), 'Update was cancelled', 'Update of record failed', 'Delete of record failed', 'Delete was cancelled', 'Invalid key pressed', 'Looks Good.... so far' (unused); edits — 'Tran Type code must be supplied.', 'Tran Type code must be numeric.', 'Tran Type code must not be zero.', 'Transaction Desc must be supplied.', 'Transaction Desc can have numbers or alphabets only.'; SQL — 'Error accessing: TRANSACTION_TYPE table. SQLCODE:', 'Error updating: TRANSACTION_TYPE Table. SQLCODE:', 'Error inserting record into: TRANSACTION_TYPE Table. SQLCODE:', 'Please delete associated child records first:SQLCODE :', 'Delete failed with message:SQLCODE :'; abend — 'UNEXPECTED DATA SCENARIO', 'UNEXPECTED ABEND OCCURRED.'.

### 6.4 Navigation

```
 COSGN00C (sign-on, admin user) ─► CA00 / COADM01C (admin menu)
      option 5 ─XCTL─► COTRTLIC (CTLI)  ──F2 XCTL──► COTRTUPC (CTTU)
      option 6 ─XCTL─► COTRTUPC (CTTU)     ◄─F3 XCTL (FROM-PROGRAM=COTRTLIC)─┘
      COTRTLIC F3 ─XCTL─► COADM01C ;  COTRTUPC F3 ─XCTL─► CDEMO-FROM-PROGRAM (COADM01C or COTRTLIC)
```
COADM01C validates the option against CDEMO-ADMIN-OPT-COUNT (6): 'Please enter a valid option number...'. Admin-only access is enforced by the sign-on routing users of type 'A' to COADM01C; CTLI/CTTU themselves do not check CDEMO-USER-TYPE (CTLI sets ADMIN on direct entry).

### 6.5 Out-of-scope divergence risk — VSAM dual storage (TRANEXTR)

`jcl/TRANEXTR.jcl` backs up `&HLQ..TRANTYPE.PS` / `TRANCATG.PS` to GDGs and unloads both DB2 tables with DSNTIAUL into fixed-format PS files used to rebuild the VSAM TRANTYPE / TRANCATG KSDS files consumed by the base (VSAM) CardDemo batch (e.g. POSTTRAN / CBTRN03C reporting). Changes made via CTLI/CTTU are visible to VSAM consumers only after TRANEXTR runs. The modernised API writes only to PostgreSQL; synchronisation to any VSAM-equivalent store is **out of scope** and is a divergence risk if legacy batch still reads VSAM. Batch maintenance (`jcl/MNTTRDB2.jcl`, `cbl/COBTUPDT.cbl`) is likewise out of scope.

## 7. Traceability (discovery → source)

| REQ ID | Behavior | Source paragraph / BMS field |
|---|---|---|
| REQ-CTLI-001 | First page = 7 rows ordered by TR_TYPE from lowest key | 0000-MAIN reset, 8000-READ-FORWARD, L1, WS-MAX-SCREEN-LINES |
| REQ-CTLI-002 | Next-page lookahead (8th fetch) | 8000-READ-FORWARD look-ahead FETCH |
| REQ-CTLI-003 | F8 page forward using last/look-ahead key `>=` | 0000-MAIN WHEN PFK08, WS-CA-LAST-TR-CODE |
| REQ-CTLI-004 | F7 page backward `< first key DESC`, reversed | 8100-READ-BACKWARDS, L2 |
| REQ-CTLI-005 | F7 on first page → 'No previous pages to display' | 2500-SETUP-MESSAGE |
| REQ-CTLI-006 | F8 on last page → 'No more pages to display' / 'No more pages for these search conditions' | 2500, 8000 |
| REQ-CTLI-007 | Page number display | 2100 PAGENO, WS-CA-SCREEN-NUM |
| REQ-CTLI-008 | Empty result first page → 'No records found for this search condition.' | 8000 +100 branch |
| REQ-CTLI-009 | Type filter must be 2-digit numeric, exact match | 1220-EDIT-TYPECD, TRTYPE |
| REQ-CTLI-010 | Desc filter LIKE '%x%' | 1230-EDIT-DESC, TRDESC |
| REQ-CTLI-011 | Filters with no match → 'No Records found for these filter conditions' | 1290-CROSS-EDITS, L3 |
| REQ-CTLI-012 | Filter change resets to page 1 | 1220/1230 exit sections |
| REQ-CTLI-013 | Row action codes U/D only; else 'Action code selected is invalid' | 1210-EDIT-ARRAY, TRTSELn |
| REQ-CTLI-014 | Single action rule 'Please select only 1 action' | 1210-EDIT-ARRAY |
| REQ-CTLI-015 | Delete requires F10 confirm; prompt literal | 2500, 2300, BUTNF10 |
| REQ-CTLI-016 | Delete success literal; reset to page 1 | 9300, 0000-MAIN F10 branch |
| REQ-CTLI-017 | Delete with children (-532) literal | 9300 -532 |
| REQ-CTLI-018 | Update prompt and inline edit of description | 2200, 2300, TRTYPDn |
| REQ-CTLI-019 | Update validation: required / alphanumeric / no change literals | 1211, 1240 |
| REQ-CTLI-020 | Update success literal; +100 / -911 / other branches | 9200 |
| REQ-CTLI-021 | F2 → CTTU | 0000-MAIN PF2, BUTNF02 |
| REQ-CTLI-022 | F3 → CA00/COADM01C | 0000-MAIN PF3, BUTNF03 |
| REQ-CTLI-023 | Info default 'Type U to update, D to delete any record' | 2500 |
| REQ-CTLI-024 | Invalid AID treated as ENTER | 0000-MAIN PFK-INVALID |
| REQ-CTLI-025 | DB2 unavailable message | 9998-PRIMING-QUERY |
| REQ-CTTU-001 | Initial prompt 'Enter transaction type to be maintained' | 3250, TRTYPCD (IC) |
| REQ-CTTU-002 | Type required/numeric/non-zero, zero-pad | 1210, 1245, TRTYPCD |
| REQ-CTTU-003 | Existing key → show details editable desc | 2000, 9100 U1, 3202, 3320 |
| REQ-CTTU-004 | Not-found → 'No record found for this key in database' + 'Press F05 to add. F12 to cancel' | 9100 +100, 3250 |
| REQ-CTTU-005 | F5 on not-found → CREATE-NEW-RECORD 'Enter new transaction type details.' | 0000-MAIN PF5 |
| REQ-CTTU-006 | Desc required/alphanumeric/≤50 | 1230, TRTYDSC |
| REQ-CTTU-007 | No change → 'No change detected with respect to values fetched.' | 1205 |
| REQ-CTTU-008 | Valid changes → 'Changes validated.Press F5 to save' | 1200, 3250 |
| REQ-CTTU-009 | F5 save = UPDATE then INSERT on +100 (upsert); 'Changes committed to database' | 9600, 9700 |
| REQ-CTTU-010 | Insert failure (incl. -803) message | 9700 |
| REQ-CTTU-011 | -911 'Could not lock record for update' | 9600 |
| REQ-CTTU-012 | F4 → 'Delete this record ? Press F4 to confirm'; F4 again deletes; 'Delete successful.' | 0000-MAIN PF4, 9800 |
| REQ-CTTU-013 | Delete -532 / other messages | 9800 |
| REQ-CTTU-014 | F12 cancel semantics | 0000-MAIN PF12, 2000 |
| REQ-CTTU-015 | Invalid key 'Invalid key pressed' | 0001-CHECK-PFKEYS |
| REQ-CTTU-016 | F3 → caller (CTLI) or admin menu | 0000-MAIN PF3 |
| REQ-CTTU-017 | PF key label visibility per state | 3391-SETUP-PFKEY-ATTRS, FKEY04/05/12 |
| REQ-CTTU-018 | Field protection per state | 3300, 3310, 3320 |
