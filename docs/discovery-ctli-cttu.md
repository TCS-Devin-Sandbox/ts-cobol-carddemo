# Discovery — CTLI / CTTU (Transaction Type maintenance, DB2)

Source module: `app/app-transaction-type-db2/`. Authoritative programs: `cbl/COTRTLIC.cbl` (CTLI, list/update/delete) and `cbl/COTRTUPC.cbl` (CTTU, add/edit/delete). Line numbers below refer to the physical source lines.

## 1. Online inventory

| TRANID | Program | Mapset / Map | DB2 plan (CSD) | Entered from |
|---|---|---|---|---|
| CTLI | COTRTLIC | COTRTLI / CTRTLIA | DB2ENTRY CARDDEMO, DB2TRAN CTLITRAN | Admin menu CA00/COADM01C option 5 |
| CTTU | COTRTUPC | COTRTUP / CTRTUPA | DB2ENTRY CARDDEMO, DB2TRAN CTTUTRAN | Admin menu option 6, or CTLI F2 (XCTL) |

Both are pseudo-conversational: `EXEC CICS RETURN TRANSID(own) COMMAREA(WS-COMMAREA) LENGTH(2000)` where the commarea = `CARDDEMO-COMMAREA` (COCOM01Y) + program-private `WS-THIS-PROGCOMMAREA`.

## 2. COTRTLIC (CTLI) — paragraph inventory and PERFORM graph

| Paragraph | Lines | Purpose |
|---|---|---|
| 0000-MAIN | 498-897 | Restore commarea, map PF key, route by EVALUATE |
| COMMON-RETURN | 899-915 | Save commarea, RETURN TRANSID(CTLI) |
| 1000-RECEIVE-MAP | 919-928 | → 1100, 1200 |
| 1100-RECEIVE-SCREEN | 930-958 | RECEIVE MAP; copy filters and 7 rows (select flag, type, desc; desc `*`/spaces → LOW-VALUES, else TRIM) |
| 1200-EDIT-INPUTS | 960-980 | → 1210, 1230, 1220, 1290 |
| 1210-EDIT-ARRAY | 982-1057 | Count actions (D/U), per-row validity, single-action rule, row-selection-changed |
| 1211-EDIT-ARRAY-DESC | 1060-1094 | For row with U: change detection (case-insensitive, trimmed) then 1240 |
| 1220-EDIT-TYPECD | 1096-1140 | Type filter: blank/0 → no filter; non-numeric → error; else exact filter; detect change (reset paging) |
| 1230-EDIT-DESC | 1142-1178 | Desc filter: build `'%'||TRIM(x)||'%'`; detect change (reset paging) |
| 1240-EDIT-ALPHANUM-REQD | 1181-1237 | Required alphanumeric check (letters, digits, spaces) |
| 1290-CROSS-EDITS | 1239-1271 | If any filter valid: 9100 COUNT; 0 rows → error |
| 2000-SEND-MAP | 1274-1292 | → 2100, 2200, 2300, 2400, 2500, 2600 |
| 2100-SCREEN-INIT | 1293-1327 | Titles, tran/pgm, date/time, PAGENO = WS-CA-SCREEN-NUM |
| 2200-SETUP-ARRAY-ATTRIBS | 1329-1379 | Row attributes: protect empty rows / all rows on filter error; red select on bad action; neutral highlight of selected row; unprotect desc for U |
| 2300-SCREEN-ARRAY-INIT | 1383-1435 | Move rows to map; keep CA-DELETE/UPDATE-REQUESTED until success |
| 2400-SETUP-SCREEN-ATTRS | 1438-1501 | Filter field echo/attributes; cursor |
| 2500-SETUP-MESSAGE | 1504-1584 | Choose INFOMSG / ERRMSG; centre info message |
| 2600-SEND-SCREEN | 1587-1599 | SEND MAP ERASE CURSOR FREEKB |
| 8000-READ-FORWARD | 1603-1726 | Fill up to 7 rows from forward cursor + 1-row lookahead |
| 8100-READ-BACKWARDS | 1727-1799 | Fill rows 7→1 from backward cursor |
| 9100-CHECK-FILTERS | 1801-1836 | SELECT COUNT(1) with filters |
| 9200-UPDATE-RECORD | 1837-1894 | UPDATE description |
| 9300-DELETE-RECORD | 1896-1940 | DELETE row |
| 9400/9450 | 1942-1995 | OPEN/CLOSE C-TR-TYPE-FORWARD |
| 9500/9550 | 1997-2051 | OPEN/CLOSE C-TR-TYPE-BACKWARD |
| 9998-PRIMING-QUERY | CSDB2RPY | `SELECT 1 FROM SYSIBM.SYSDUMMY1`; error → 'Db2 access failure. ' + SEND-LONG-TEXT |
| 9999-FORMAT-DB2-MESSAGE | CSDB2RPY | `TRIM(action) ' SQLCODE:' sqlcode ' ' DSNTIAC-text` → WS-RETURN-MSG (75 chars) |
| YYYY-STORE-PFKEY | CSSTRPFY | Map EIBAID → CCARD-AID-* |
| SEND-PLAIN-TEXT / SEND-LONG-TEXT | 2066-2098 | SEND TEXT + RETURN |

PERFORM graph:
```
0000-MAIN
 ├─ YYYY-STORE-PFKEY
 ├─ 1000-RECEIVE-MAP ─ 1100-RECEIVE-SCREEN
 │                   └ 1200-EDIT-INPUTS ─ 1210-EDIT-ARRAY ─ 1211-EDIT-ARRAY-DESC ─ 1240-EDIT-ALPHANUM-REQD
 │                                      ├ 1230-EDIT-DESC
 │                                      ├ 1220-EDIT-TYPECD
 │                                      └ 1290-CROSS-EDITS ─ 9100-CHECK-FILTERS ─ 9999-FORMAT-DB2-MESSAGE
 ├─ 9998-PRIMING-QUERY ─ 9999 ; on error SEND-LONG-TEXT
 ├─ 8000-READ-FORWARD ─ 9400-OPEN-FORWARD-CURSOR, 9450-CLOSE-FORWARD-CURSOR, 9999
 ├─ 8100-READ-BACKWARDS ─ 9500-OPEN-BACKWARD-CURSOR, 9550-CLOSE-BACK-CURSOR, 9999
 ├─ 9200-UPDATE-RECORD ─ 9999 ; 9300-DELETE-RECORD ─ 9999
 ├─ 2000-SEND-MAP ─ 2100, 2200, 2300, 2400, 2500, 2600
 ├─ XCTL COADM01C (or CDEMO-FROM-PROGRAM) on F3 ; XCTL COTRTUPC on F2
 └─ COMMON-RETURN (RETURN TRANSID CTLI)
```

### CTLI main routing (0000-MAIN, EVALUATE at 698)
Valid keys: ENTER, F2, F3, F7, F8, F10 (only when CA-DELETE-REQUESTED or CA-UPDATE-REQUESTED); any other key is treated as ENTER (574-587). F10 when a filter or the selected row changed since the confirm prompt is treated as ENTER (666-678). Any key other than F8 resets CA-LAST-PAGE-NOT-SHOWN (657-661).

| Order | Condition | Action |
|---|---|---|
| — | F3 (591) | to = CDEMO-FROM-TRANID/PROGRAM unless blank or CTLI → CA00/COADM01C; SYNCPOINT; XCTL |
| — | F2 and from CTLI (630) | XCTL COTRTUPC with CDEMO-PGM-ENTER |
| 1 | INPUT-ERROR | re-read page from first key (unless filter error) and send with ERRMSG |
| 2 | F7 and CA-FIRST-PAGE | re-read first page; message 'No previous pages to display' |
| 3 | REENTER from another program | reset, page 1 |
| 4 | F8 and CA-NEXT-PAGE-EXISTS | start = last key, screen+1, forward read |
| 5 | F7 and not first page | start = first key, screen−1, backward read |
| 6 | ENTER + delete selected | confirm prompt 'Delete HIGHLIGHTED row ? Press F10 to confirm' |
| 7 | F10 + delete selected | 9300 DELETE; on success re-initialise to page 1 |
| 8 | ENTER + update selected | prompt 'Update HIGHLIGHTED row. Press F10 to save' |
| 9 | F10 + update selected | 9200 UPDATE; re-read current page |
| 10 | OTHER | read forward from first key (refresh) |

### CTLI COMMAREA (WS-THIS-PROGCOMMAREA, 377-418)
WS-CA-TYPE-CD X(2) (last type filter), WS-CA-TYPE-DESC X(50) (last desc filter), WS-CA-ALL-ROWS-OUT 7×(code X2 + desc X50), WS-CA-ROW-SELECTED, WS-CA-LAST-TR-CODE, WS-CA-FIRST-TR-CODE, WS-CA-SCREEN-NUM 9(1) (88 CA-FIRST-PAGE=1), WS-CA-LAST-PAGE-DISPLAYED (0 shown / 9 not shown), WS-CA-NEXT-PAGE-IND ('Y' exists), delete/update requested flags. A filter change re-initialises the paging variables (1134, 1172).

### CTLI paging algorithm (8000/8100)
* Forward: open cursor `TR_TYPE >= :WS-START-KEY`, fetch until 7 rows; row 1 key → first key, screen 0→1. On the 7th row fetch one more (lookahead): found → CA-NEXT-PAGE-EXISTS and **WS-CA-LAST-TR-CODE = lookahead key** (so the next F8 starts *at* that key with `>=`); +100 → no next page (+ 'No more pages for these search conditions' if F8). Fewer than 7 rows → +100 → no next page; page 1 with 0 rows → 'No records found for this search condition.'.
* Backward: F7 uses start = first key of current page with `TR_TYPE < :key ORDER BY TR_TYPE DESC`, fills rows 7→1, row 1 key → first key; next-page flag set to exists.
* F8 with no next page: 'No more pages to display' if the last page was already shown, else info 'Type U to update, D to delete any record' and mark last page shown.
* Screen number is PIC 9(1) — wraps after page 9 (source defect; target uses an unbounded integer, see deviations).

### CTLI row actions (1210/1211, 2200/2300)
* Select flag per row: 'U' or 'D' valid; blank/LOW-VALUES ignored; anything else → 'Action code selected is invalid' (row select field red).
* More than one non-blank selection → 'Please select only 1 action' (all selected rows red).
* U: row description editable; unchanged (trim + upper-case comparison, same length) → 'No change detected with respect to database values.'; blank → 'Transaction Desc must be supplied.'; non alphanumeric/space → 'Transaction Desc can have numbers or alphabets only.'.
* F10 confirms; success messages 'HIGHLIGHTED row was updated' / 'HIGHLIGHTED row deleted.Hit Enter to continue'.
* Filter rows protected if filter error or empty row.

### CTLI filters
* Type filter (1220): blank, spaces or zeros → no filter; non-numeric → 'TYPE CODE FILTER,IF SUPPLIED MUST BE A 2 DIGIT NUMBER'; else exact `TR_TYPE = :filter`.
* Description filter (1230): `TR_DESCRIPTION LIKE TRIM('%' || TRIM(x) || '%')` (case-sensitive in DB2).
* Cross edit (1290): COUNT(1) = 0 → 'No Records found for these filter conditions'.

## 3. COTRTUPC (CTTU) — paragraph inventory and PERFORM graph

| Paragraph | Lines | Purpose |
|---|---|---|
| 0000-MAIN | 345-557 | HANDLE ABEND, restore commarea, check PF keys, route |
| COMMON-RETURN | 559-575 | RETURN TRANSID(CTTU) |
| 0001-CHECK-PFKEYS | 577-623 | Validate key against state; else 'Invalid key pressed' |
| 1000-PROCESS-INPUTS | 625-640 | → 1100, 1150, 1200 |
| 1100-RECEIVE-MAP | 641-650 | RECEIVE MAP CTRTUPA |
| 1150-STORE-MAP-IN-NEW | 652-688 | Store screen into TTUP-NEW-* (`*`/spaces → LOW-VALUES; TRIM) |
| 1200-EDIT-MAP-INPUTS | 689-779 | Edit key, compare old/new, edit description |
| 1205-COMPARE-OLD-NEW | 783-814 | Upper-case/trim compare → 'No change detected with respect to values fetched.' |
| 1210-EDIT-TRANTYPE | 820-845 | 1245 on type; valid → NUMVAL and zero-pad to 2 digits |
| 1230-EDIT-ALPHANUM-REQD | 849-905 | Required letters/digits/spaces |
| 1245-EDIT-NUM-REQD | 907-976 | Required, numeric (TEST-NUMVAL), non-zero |
| 2000-DECIDE-ACTION | 978-1085 | State machine transitions |
| 3000-SEND-MAP | 1089-1108 | → 3100, 3200, 3250, 3300, 3390, 3391, 3400 |
| 3100-SCREEN-INIT | 1110-1138 | Header |
| 3200-SETUP-SCREEN-VARS / 3201 / 3202 / 3203 | 1140-1205 | Show blank / original (OLD) / updated (NEW) values |
| 3250-SETUP-INFOMSG | 1210-1268 | Info message per state; ERRMSG = WS-RETURN-MSG |
| 3300-SETUP-SCREEN-ATTRS / 3310 / 3320 | 1269-1384 | Protect/unprotect, cursor, red on error, `*` for blank key, CSSETATY for description |
| 3390-SETUP-INFOMSG-ATTRS | 1386-1395 | Info message dark when empty |
| 3391-SETUP-PFKEY-ATTRS | 1397-1426 | Show F4 (show/confirm-delete), F5 (changes-ok / not-found), F12 (most states) |
| 3400-SEND-SCREEN | 1428-1444 | SEND MAP |
| 9000-READ-TRANTYPE | 1447-1468 | → 9100, 9500 |
| 9100-GET-TRANSACTION-TYPE | 1469-1514 | SELECT by key |
| 9500-STORE-FETCHED-DATA | 1517-1530 | DCL → TTUP-OLD-* |
| 9600-WRITE-PROCESSING | 1531-1595 | UPDATE; +100 → 9700 INSERT (upsert) |
| 9700-INSERT-RECORD | 1596-1623 | INSERT |
| 9800-DELETE-PROCESSING | 1624-1666 | DELETE by old key |
| ABEND-ROUTINE | 1675-1700 | SEND abend data, ABEND ABCODE |

PERFORM graph:
```
0000-MAIN ─ YYYY-STORE-PFKEY ─ 0001-CHECK-PFKEYS
 ├─ F3: SYNCPOINT, XCTL CDEMO-FROM-PROGRAM (default COADM01C / CA00)
 ├─ first entry: 3000-SEND-MAP
 ├─ F4 & CONFIRM-DELETE: 9800-DELETE-PROCESSING, 3000
 ├─ F4 & SHOW-DETAILS: → CONFIRM-DELETE, 3000
 ├─ F5 & DETAILS-NOT-FOUND: → CREATE-NEW-RECORD, 3000
 ├─ F5 & CHANGES-OK-NOT-CONFIRMED: 9600-WRITE-PROCESSING (─ 9700-INSERT-RECORD), 3000
 ├─ F12 & (CHANGES-OK / CONFIRM-DELETE / SHOW-DETAILS): 2000-DECIDE-ACTION (─ 9000 ─ 9100, 9500), 3000
 ├─ invalid key: 3000
 └─ OTHER: 1000-PROCESS-INPUTS (─1100 ─1150 ─1200 ─1205 ─1210 ─1245 ─1230), 2000-DECIDE-ACTION, 3000
3000-SEND-MAP ─ 3100, 3200(─3201/3202/3203), 3250, 3300(─3310, 3320), 3390, 3391, 3400
```

### CTTU state machine (TTUP-CHANGE-ACTION, 296-327)
Values: LOW/space NOT-FETCHED, 'K' INVALID-SEARCH-KEYS, 'X' DETAILS-NOT-FOUND, 'S' SHOW-DETAILS, 'R' CREATE-NEW-RECORD, 'V' REVIEW-NEW-RECORD (unused), '9' CONFIRM-DELETE, '8' START-DELETE, '7' DELETE-DONE, '6' DELETE-FAILED, 'E' CHANGES-NOT-OK, 'N' CHANGES-OK-NOT-CONFIRMED, 'L' CHANGES-OKAYED-LOCK-ERROR, 'F' CHANGES-OKAYED-BUT-FAILED, 'C' CHANGES-OKAYED-AND-DONE, 'B' CHANGES-BACKED-OUT.

| From | Event | To | Info message (3250) |
|---|---|---|---|
| (entry) | first entry / from menu or CTLI | NOT-FETCHED | 'Enter transaction type to be maintained' |
| NOT-FETCHED | ENTER, key blank | NOT-FETCHED, ERR 'No input received' | same |
| NOT-FETCHED | ENTER, key invalid | INVALID-SEARCH-KEYS, ERR edit text | same |
| NOT-FETCHED | ENTER, key valid, row found | SHOW-DETAILS | 'Update transaction type details shown.' |
| NOT-FETCHED | ENTER, key valid, +100 | DETAILS-NOT-FOUND, ERR 'No record found for this key in database' | 'Press F05 to add. F12 to cancel' |
| DETAILS-NOT-FOUND | F5 | CREATE-NEW-RECORD | 'Enter new transaction type details.' |
| CREATE-NEW-RECORD / SHOW-DETAILS | ENTER, valid changed desc | CHANGES-OK-NOT-CONFIRMED | 'Changes validated.Press F5 to save' |
| SHOW-DETAILS | ENTER, unchanged | SHOW-DETAILS, ERR 'No change detected with respect to values fetched.' | — |
| any edit state | ENTER, invalid desc | CHANGES-NOT-OK, ERR edit text | 'Update transaction type details shown.' |
| CHANGES-OK-NOT-CONFIRMED | F5 | UPDATE→(+100 INSERT): CHANGES-OKAYED-AND-DONE / LOCK-ERROR (-911) / BUT-FAILED (<0) | 'Changes committed to database' / 'Changes unsuccessful' |
| CHANGES-OK-NOT-CONFIRMED | F12 | CHANGES-BACKED-OUT, ERR 'Update was cancelled' | — |
| SHOW-DETAILS | F4 | CONFIRM-DELETE | 'Delete this record ? Press F4 to confirm' |
| CONFIRM-DELETE | F4 | DELETE-DONE / DELETE-FAILED / (−532 keeps CONFIRM-DELETE→message) | 'Delete successful.' / 'Changes unsuccessful' |
| CONFIRM-DELETE | F12 | NOT-FETCHED, ERR 'Delete was cancelled' | — |
| SHOW-DETAILS / CREATE-NEW / NOT-FOUND | F12 | NOT-FETCHED (re-entry) | prompt for key |
| DONE / FAILED / DELETE-DONE / DELETE-FAILED | next key | NOT-FETCHED (re-entry, 410-418) | prompt for key |
| any | F3 | XCTL back to caller (CTLI or COADM01C) | — |

Valid keys per state (0001-CHECK-PFKEYS): F3 always; ENTER unless CONFIRM-DELETE; F4 in SHOW-DETAILS/CONFIRM-DELETE; F5 in CHANGES-OK-NOT-CONFIRMED/DETAILS-NOT-FOUND/DELETE-IN-PROGRESS; F12 in CHANGES-OK-NOT-CONFIRMED/SHOW-DETAILS/DETAILS-NOT-FOUND/CONFIRM-DELETE/CREATE-NEW-RECORD. Otherwise ERR 'Invalid key pressed'.

### CTTU field edits
* Type (1210/1245, name 'Tran Type code'): blank → 'Tran Type code must be supplied.'; not numeric → 'Tran Type code must be numeric.'; zero → 'Tran Type code must not be zero.'; valid → NUMVAL, zero-padded to 2 digits (`1` → `01`).
* Description (1230, name 'Transaction Desc', length 50): blank → 'Transaction Desc must be supplied.'; characters other than A-Z a-z 0-9 space → 'Transaction Desc can have numbers or alphabets only.'.
* Only the first error message is kept (`IF WS-RETURN-MSG-OFF`).

## 4. SQL inventory

| Program / paragraph | Statement | Host variables | SQLCODE branches |
|---|---|---|---|
| both / 9998 | `SELECT 1 INTO :WS-DUMMY-DB2-INT FROM SYSIBM.SYSDUMMY1 FETCH FIRST 1 ROW ONLY` | WS-DUMMY-DB2-INT | 0 ok; other → 'Db2 access failure. ' + SEND TEXT |
| CTLI 9400/8000/9450 | `DECLARE C-TR-TYPE-FORWARD: SELECT TR_TYPE, TR_DESCRIPTION FROM TRANSACTION_TYPE WHERE TR_TYPE >= :WS-START-KEY AND ((:WS-EDIT-TYPE-FLAG='1' AND TR_TYPE=:WS-TYPE-CD-FILTER) OR :WS-EDIT-TYPE-FLAG<>'1') AND ((:WS-EDIT-DESC-FLAG='1' AND TR_DESCRIPTION LIKE TRIM(:WS-TYPE-DESC-FILTER)) OR :WS-EDIT-DESC-FLAG<>'1') ORDER BY TR_TYPE` | WS-START-KEY, WS-EDIT-TYPE-FLAG, WS-TYPE-CD-FILTER, WS-EDIT-DESC-FLAG, WS-TYPE-DESC-FILTER → DCL-TR-TYPE, DCL-TR-DESCRIPTION | OPEN other → 'C-TR-TYPE-FORWARD Open'; FETCH 0 row, +100 end, other → 'C-TR-TYPE-FORWARD close' / 'C-TR-TYPE-FORWARD fetch' (lookahead); CLOSE other → 'C-TR-TYPE-FORWARD close' |
| CTLI 9500/8100/9550 | `DECLARE C-TR-TYPE-BACKWARD ... WHERE TR_TYPE < :WS-START-KEY ... ORDER BY TR_TYPE DESC` | same | OPEN other → 'C-TR-TYPE-BACKWARD Open'; FETCH other → 'Error on fetch Cursor C-TR-TYPE-BACKWARD'; CLOSE → 'C-TR-TYPE-BACKWARD close' |
| CTLI 9100 | `SELECT COUNT(1) INTO :WS-RECORDS-COUNT FROM TRANSACTION_TYPE WHERE <same filters>` | WS-RECORDS-COUNT | 0 ok (count 0 → 'No Records found for these filter conditions'); other → 'Error reading TRANSACTION_TYPE table ' |
| CTLI 9200 | `UPDATE TRANSACTION_TYPE SET TR_DESCRIPTION=:DCL-TR-DESCRIPTION WHERE TR_TYPE=:DCL-TR-TYPE` | DCL-TR-TYPE, DCL-TR-DESCRIPTION | 0 SYNCPOINT + 'HIGHLIGHTED row was updated'; +100 'Record not found. Deleted by others ? '; −911 'Deadlock. Someone else updating ?'; <0 'Update failed with' |
| CTLI 9300 | `DELETE FROM TRANSACTION_TYPE WHERE TR_TYPE=:DCL-TR-TYPE` | DCL-TR-TYPE | 0 SYNCPOINT + 'HIGHLIGHTED row deleted.Hit Enter to continue'; −532 'Please delete associated child records first:'; other 'Delete failed with message:' |
| CTTU 9100 | `SELECT TR_TYPE, TR_DESCRIPTION INTO :DCL-TR-TYPE, :DCL-TR-DESCRIPTION FROM TRANSACTION_TYPE WHERE TR_TYPE=:DCL-TR-TYPE` | DCL-TR-TYPE | 0 found; +100 'No record found for this key in database'; <0 'Error accessing: TRANSACTION_TYPE table. SQLCODE:…' |
| CTTU 9600 | `UPDATE TRANSACTION_TYPE SET TR_DESCRIPTION=:DCL-TR-DESCRIPTION WHERE TR_TYPE=:DCL-TR-TYPE` | DCL-TR-TYPE, DCL-TR-DESCRIPTION | 0 SYNCPOINT; +100 → 9700 INSERT; −911 'Could not lock record for update'; <0 'Error updating: TRANSACTION_TYPE Table. SQLCODE:…' |
| CTTU 9700 | `INSERT INTO TRANSACTION_TYPE (TR_TYPE, TR_DESCRIPTION) VALUES (:DCL-TR-TYPE, :DCL-TR-DESCRIPTION)` | same | 0 SYNCPOINT; **any other (incl. −803 duplicate key)** → 'Error inserting record into: TRANSACTION_TYPE Table. SQLCODE:…' (no dedicated −803 branch in source) |
| CTTU 9800 | `DELETE FROM TRANSACTION_TYPE WHERE TR_TYPE=:DCL-TR-TYPE` | DCL-TR-TYPE (= OLD key) | 0 DELETE-DONE + SYNCPOINT; −532 'Please delete associated child records first:SQLCODE :…'; other 'Delete failed with message:SQLCODE :…' + DELETE-FAILED |

−532 is DB2's "delete restricted by RESTRICT FK" (TRNTYCAT.ddl `ON DELETE RESTRICT`); −803 is duplicate key; −911 deadlock/timeout.

## 5. BMS field inventory

### COTRTLI / CTRTLIA (24×80)
| Field | Pos (row,col) | Len | Attributes | Initial | I/O |
|---|---|---|---|---|---|
| TRNNAME | 1,7 | 4 | ASKIP,FSET,NORM blue | — | O (CTLI) |
| TITLE01 / TITLE02 | 1,21 / 2,21 | 40 | ASKIP yellow | — | O |
| CURDATE | 1,71 | 8 | ASKIP blue | mm/dd/yy | O |
| PGMNAME | 2,7 | 8 | ASKIP blue | — | O |
| CURTIME | 2,71 | 8 | ASKIP blue | hh:mm:ss | O |
| (label) | 4,28 | 25 | neutral | Maintain Transaction Type | — |
| PAGENO | 4,76 | 3 | default | — | O |
| TRTYPE | 6,44 | 2 | FSET,IC,NORM,UNPROT green underline | — | I/O type filter |
| TRDESC | 8,25 | 50 | FSET,NORM,UNPROT green underline | — | I/O desc filter |
| TRTSEL1-7 | 12-18,6 | 1 | FSET,NORM,PROT (unprotected by program for filled rows) underline | — | I/O action U/D |
| TRTTYP1-7 | 12-18,17 | 2 | FSET,NORM,PROT | — | O type code |
| TRTYPD1-7 | 12-18,25 | 50 | FSET,NORM,UNPROT (program sets PROT unless U selected) | — | I/O description |
| TRTSELA/TRTTYPA/TRTDSCA | 19 | 1/2/50 | PROT | — | unused 8th row |
| INFOMSG | 21,19 | 45 | PROT neutral | — | O info line (centred) |
| ERRMSG | 23,1 | 78 | ASKIP,BRT,FSET red | — | O error line |
| BUTNF02..BUTNF10 | 24 | — | ASKIP turquoise | F2=Add F3=Exit F7=Page Up F8=Page Dn F10=Save | — |

### COTRTUP / CTRTUPA (24×80)
| Field | Pos | Len | Attributes | Initial | I/O |
|---|---|---|---|---|---|
| TRNNAME, TITLE01/02, CURDATE, PGMNAME, CURTIME | as above | | | | O |
| (label) | 7,28 | 25 | neutral | Maintain Transaction Type | — |
| TRTYPCD | 12,26 | 2 | IC,UNPROT underline (program protects after fetch) | — | I/O type code |
| TRTYDSC | 14,26 | 50 | UNPROT underline (unprotected only in SHOW/NOT-OK/CREATE/BACKED-OUT) | — | I/O description |
| INFOMSG | 22,23 | 45 | ASKIP neutral | — | O |
| ERRMSG | 23,1 | 78 | ASKIP,BRT,FSET red | — | O |
| FKEYS | 24,1 | 21 | ASKIP yellow | ENTER=Process F3=Exit | O |
| FKEY04 / FKEY05 / FKEY06 / FKEY12 | 24,23/33/43/69 | 9/8/6/10 | ASKIP,DRK (shown per state) | F4=Delete / F5=Save / F6=Add / F12=Cancel | O |

## 6. Navigation
* Admin menu CA00 (COADM01C) option 5 → CTLI, option 6 → CTTU (README).
* CTLI F2 → XCTL COTRTUPC (CTTU starts fresh, since from-program = COTRTLIC and not re-enter).
* CTTU F3 → XCTL back to CDEMO-FROM-PROGRAM (CTLI when entered from list), else COADM01C. CTLI F3 → CDEMO-FROM-PROGRAM unless blank/self → COADM01C; when returning from CTTU with F3 the list restarts at page 1 (544-554).
* Both programs set CDEMO-USRTYP-ADMIN; access is restricted by the admin menu (admin users only).

## 7. Out of scope / divergence risk — VSAM dual storage
Batch job `jcl/TRANEXTR.jcl` unloads TRANSACTION_TYPE / TRANSACTION_TYPE_CATEGORY (DSNTIAUL) into the VSAM-compatible flat files `TRANTYPE.PS` / `TRANCATG.PS` (60-byte records) used by the base VSAM application, and `MNTTRDB2`/COBTUPDT does batch maintenance. Online changes made through CTLI/CTTU only reach VSAM consumers after TRANEXTR runs. The modernised API writes only PostgreSQL; the extract/sync is out of scope and remains a divergence risk until a replacement extract is built.

## 8. Traceability (discovery → source)
See `docs/requirements-ctli-cttu.md` for the full requirement list; each REQ-CTLI-### / REQ-CTTU-### row cites the paragraph or BMS field above.

| Requirement range | Source paragraphs | BMS fields |
|---|---|---|
| REQ-CTLI-001..008 | 0000-MAIN, 8000-READ-FORWARD, 8100-READ-BACKWARDS, 2500-SETUP-MESSAGE | TRTTYP1-7, TRTYPD1-7, PAGENO, INFOMSG, ERRMSG |
| REQ-CTLI-009..012 | 1220-EDIT-TYPECD, 1230-EDIT-DESC, 1290-CROSS-EDITS, 9100-CHECK-FILTERS | TRTYPE, TRDESC |
| REQ-CTLI-013..023 | 1210-EDIT-ARRAY, 1211-EDIT-ARRAY-DESC, 1240, 9200-UPDATE-RECORD, 9300-DELETE-RECORD | TRTSEL1-7, TRTYPD1-7 |
| REQ-CTLI-024..028 | 0000-MAIN (F2/F3), 2500, 9998-PRIMING-QUERY | BUTNF02/03, INFOMSG, ERRMSG |
| REQ-CTTU-001..008 | 1200-EDIT-MAP-INPUTS, 1205, 1210, 1230, 1245, 9100-GET-TRANSACTION-TYPE, 3250 | TRTYPCD, TRTYDSC, INFOMSG |
| REQ-CTTU-009..017 | 2000-DECIDE-ACTION, 9600-WRITE-PROCESSING, 9700-INSERT-RECORD, 9800-DELETE-PROCESSING, 0001-CHECK-PFKEYS | FKEY04/05/12, ERRMSG |
