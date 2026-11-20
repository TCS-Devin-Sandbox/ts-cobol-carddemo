# Phase 1 — Discovery: CTLI (COTRTLIC) and CTTU (COTRTUPC)

Source module: `app/app-transaction-type-db2/`. Authoritative artifacts read for this document:

| Artifact | Path | Lines |
|---|---|---|
| COTRTLIC (CTLI list/update/delete) | `cbl/COTRTLIC.cbl` | 2098 |
| COTRTUPC (CTTU add/edit/delete) | `cbl/COTRTUPC.cbl` | 1702 |
| BMS COTRTLI / map CTRTLIA | `bms/COTRTLI.bms` | 338 |
| BMS COTRTUP / map CTRTUPA | `bms/COTRTUP.bms` | 137 |
| DDL | `ddl/TRNTYPE.ddl`, `ddl/TRNTYCAT.ddl`, `ctl/DB2CREAT.ctl` | |
| Seed | `jcl/CREADB21.jcl` → `ctl/DB2LTTYP.ctl`, `ctl/DB2LTCAT.ctl` | |
| Shared Db2 code | `cpy/CSDB2RPY.cpy` (9998/9999 paragraphs), `cpy/CSDB2RWY.cpy` | |
| Shared CICS code | `app/cpy/CSSTRPFY.cpy` (PF-key mapping), `app/cpy/COCOM01Y.cpy` (COMMAREA), `app/cpy/COADM02Y.cpy` (admin menu options), `app/cpy/CSSETATY.cpy` | |
| Batch/extract | `jcl/TRANEXTR.jcl`, `jcl/MNTTRDB2.jcl` (COBTUPDT) — context only | |

Conventions: "¶" = paragraph. Line references are to the paragraph name (paragraph names are unique within each program).

---

## 1. Program COTRTLIC (transaction CTLI)

### 1.1 Constants (`WS-CONSTANTS`)

| Literal | Value |
|---|---|
| LIT-THISPGM / LIT-THISTRANID | `COTRTLIC` / `CTLI` |
| LIT-THISMAPSET / LIT-THISMAP | `COTRTLI` / `CTRTLIA` |
| LIT-ADMINPGM / LIT-ADMINTRANID | `COADM01C` / `CA00` |
| LIT-ADDTPGM / LIT-ADDTTRANID | `COTRTUPC` / `CTTU` |
| LIT-DELETE-FLAG / LIT-UPDATE-FLAG | `D` / `U` |
| WS-MAX-SCREEN-LINES | 7 |
| LIT-ASTERISK | `*` |

### 1.2 Paragraph inventory

| Paragraph | Purpose |
|---|---|
| 0000-MAIN | Entry; COMMAREA restore/initialize; PF-key mapping/validation; PF3 XCTL; PF2 XCTL; F10 downgrade; priming query; main EVALUATE dispatch; falls into COMMON-RETURN |
| COMMON-RETURN | Sets FROM-TRANID/PROGRAM, LAST-MAP(SET); copies CARDDEMO-COMMAREA + WS-THIS-PROGCOMMAREA into WS-COMMAREA; `EXEC CICS RETURN TRANSID(CTLI) COMMAREA LENGTH(2000)` |
| 0000-MAIN-EXIT | Exit |
| 1000-RECEIVE-MAP | PERFORM 1100 then 1200 |
| 1100-RECEIVE-SCREEN | `RECEIVE MAP(CTRTLIA)`; moves TRTYPEI→WS-IN-TYPE-CD, TRDESCI→WS-IN-TYPE-DESC; per row: TRTSELI→WS-EDIT-SELECT, TRTTYPI→WS-ROW-TR-CODE-IN, TRTYPDI→WS-ROW-TR-DESC-IN (LOW-VALUES if `*` or spaces, else TRIM) |
| 1200-EDIT-INPUTS | Sets INPUT-OK, protect-rows NO; PERFORM 1210, 1230, 1220, 1290 (note order: array, desc filter, type filter, cross edits) |
| 1210-EDIT-ARRAY | Counts actions (INSPECT TALLYING), detects >1 action / invalid code, calls 1211 for `U` rows, records selected row |
| 1211-EDIT-ARRAY-DESC | Compares new vs. old description (upper-case, trimmed, same length); "no change" message; otherwise edits via 1240 |
| 1220-EDIT-TYPECD / -EXIT | Type filter edit; exit paragraph detects filter change and resets paging |
| 1230-EDIT-DESC / -EXIT | Description filter edit, builds `%desc%`; exit detects change and resets paging |
| 1240-EDIT-ALPHANUM-REQD | Generic required + alphanumeric/space-only edit |
| 1290-CROSS-EDITS | If any filter valid: PERFORM 9100-CHECK-FILTERS; COUNT=0 → error |
| 2000-SEND-MAP | PERFORM 2100, 2200, 2300, 2400, 2500, 2600 |
| 2100-SCREEN-INIT | Titles, tran/pgm name, date (mm/dd/yy), time (hh:mm:ss), PAGENO ← WS-CA-SCREEN-NUM, INFOMSG dark |
| 2200-SETUP-ARRAY-ATTRIBS | Row-level attributes: protect empty rows/when filter errors, red/cursor on bad select, neutral highlight for selected D/U row, unprotect description for U row |
| 2300-SCREEN-ARRAY-INIT | Moves COMMAREA rows to map; sets CA-DELETE-REQUESTED / CA-UPDATE-REQUESTED; clears select after success; shows `*` for blank edited desc |
| 2400-SETUP-SCREEN-ATTRS | Filter field redisplay/attributes and cursor positioning |
| 2500-SETUP-MESSAGE | Chooses INFOMSG / ERRMSG; centers info text |
| 2600-SEND-SCREEN | `SEND MAP(CTRTLIA) CURSOR ERASE FREEKB` |
| 8000-READ-FORWARD | Opens forward cursor, fetches ≤7 rows + 1 look-ahead, sets first/last key, page number, next-page flag, messages |
| 8100-READ-BACKWARDS | Opens backward cursor, fetches 7 rows filling rows 7→1, sets first key |
| 9100-CHECK-FILTERS | `SELECT COUNT(1)` with filter predicates |
| 9200-UPDATE-RECORD | `UPDATE … SET TR_DESCRIPTION` for selected row; SYNCPOINT on success |
| 9300-DELETE-RECORD | `DELETE … WHERE TR_TYPE` for selected row; SYNCPOINT on success |
| 9400-OPEN-FORWARD-CURSOR / 9450-CLOSE-FORWARD-CURSOR | OPEN/CLOSE C-TR-TYPE-FORWARD |
| 9500-OPEN-BACKWARD-CURSOR / 9550-CLOSE-BACK-CURSOR | OPEN/CLOSE C-TR-TYPE-BACKWARD |
| 9998-PRIMING-QUERY (CSDB2RPY) | `SELECT 1 FROM SYSIBM.SYSDUMMY1` connectivity check |
| 9999-FORMAT-DB2-MESSAGE (CSDB2RPY) | `CALL DSNTIAC`; builds `TRIM(action) ' SQLCODE:' sqlcode ' ' dsntiac-text` into WS-LONG-MSG → WS-RETURN-MSG (75 chars) |
| YYYY-STORE-PFKEY (CSSTRPFY) | EIBAID → CCARD-AID-*; PF13–PF24 fold to PF1–PF12 |
| SEND-PLAIN-TEXT | Debug: SEND TEXT WS-RETURN-MSG, RETURN |
| SEND-LONG-TEXT | SEND TEXT WS-LONG-MSG, RETURN (used when priming query fails) |

### 1.3 Perform graph (COTRTLIC)

```
0000-MAIN
 ├─ YYYY-STORE-PFKEY
 ├─ 1000-RECEIVE-MAP            (only if EIBCALEN>0 and FROM-PROGRAM = COTRTLIC)
 │   ├─ 1100-RECEIVE-SCREEN
 │   └─ 1200-EDIT-INPUTS
 │       ├─ 1210-EDIT-ARRAY ── 1211-EDIT-ARRAY-DESC ── 1240-EDIT-ALPHANUM-REQD
 │       ├─ 1230-EDIT-DESC  (→ 1230-EDIT-DESC-EXIT change detection)
 │       ├─ 1220-EDIT-TYPECD (→ 1220-EDIT-TYPECD-EXIT change detection)
 │       └─ 1290-CROSS-EDITS ── 9100-CHECK-FILTERS ── 9999-FORMAT-DB2-MESSAGE
 ├─ [PF3]  SYNCPOINT, XCTL CDEMO-TO-PROGRAM (COADM01C)
 ├─ [PF2]  XCTL COTRTUPC
 ├─ 9998-PRIMING-QUERY ── 9999-FORMAT-DB2-MESSAGE ; on error SEND-LONG-TEXT
 ├─ EVALUATE branch:
 │   ├─ 8000-READ-FORWARD ── 9400-OPEN-FORWARD-CURSOR, FETCH loop, 9450-CLOSE-FORWARD-CURSOR (each → 9999 on error)
 │   ├─ 8100-READ-BACKWARDS ── 9500-OPEN-BACKWARD-CURSOR, FETCH loop, 9550-CLOSE-BACK-CURSOR
 │   ├─ 9200-UPDATE-RECORD ── 9999 ; then 8000-READ-FORWARD
 │   ├─ 9300-DELETE-RECORD ── 9999
 │   └─ 2000-SEND-MAP ── 2100, 2200, 2300, 2400, 2500, 2600
 └─ COMMON-RETURN (CICS RETURN TRANSID CTLI)
```

### 1.4 0000-MAIN dispatch (EVALUATE TRUE, in order)

| # | Condition | Action |
|---|---|---|
| pre | EIBCALEN=0 | Init COMMAREA; FROM=CTLI/COTRTLIC; USRTYP-ADMIN; PGM-ENTER; CA-FIRST-PAGE; LAST-PAGE-NOT-SHOWN |
| pre | (PGM-ENTER and FROM-PROGRAM≠COTRTLIC) or (PF3 and FROM-TRANID=CTTU) | Re-init program COMMAREA, treat as ENTER, page 1 |
| pre | PF key not in {ENTER, PF2, PF3, PF7, PF8, PF10 with CA-DELETE-REQUESTED, PF10 with CA-UPDATE-REQUESTED} | Treat as ENTER |
| pre | PF3 | XCTL to COADM01C/CA00 (or caller if FROM-PROGRAM is not blank and not self — after the first pseudo-conversation FROM-PROGRAM is always self, so effectively COADM01C) |
| pre | PF2 and FROM-PROGRAM=COTRTLIC | XCTL COTRTUPC (sets CDEMO-USRTYP-USER — quirk) |
| pre | not PF8 | CA-LAST-PAGE-NOT-SHOWN |
| pre | PF10 but no pending D/U, or a filter changed, or selected row changed | Treat as ENTER |
| pre | 9998-PRIMING-QUERY fails | SEND-LONG-TEXT and return |
| 1 | INPUT-ERROR | start key = first key; if neither filter NOT-OK → 8000; send map |
| 2/3 | PF7 and CA-FIRST-PAGE | (WHEN duplicated in source; first WHEN has no statements and falls to second) start key = first key; 8000; send map |
| 4 | PF3, or (PGM-REENTER and FROM-PROGRAM≠self) | Re-init, page 1, 8000, send |
| 5 | PF8 and CA-NEXT-PAGE-EXISTS | start key = WS-CA-LAST-TR-CODE (look-ahead key); screen num +1; 8000; clear selects; send |
| 6 | PF7 and not first page | start key = first key; screen num −1; 8100; clear selects; send |
| 7 | ENTER and deletes>0 | re-read current page (8000 from first key) and send → shows "Delete HIGHLIGHTED row ? Press F10 to confirm" |
| 8 | PF10 and deletes>0 | 9300-DELETE-RECORD; send map; on success re-initialize COMMAREA (page 1 on next Enter) |
| 9 | ENTER and updates>0 | re-read current page and send → shows "Update HIGHLIGHTED row. Press F10 to save" |
| 10 | PF10 and updates>0 | 9200-UPDATE-RECORD; re-read from first key; send |
| 11 | OTHER | 8000 from first key; send |

### 1.5 COMMAREA (pseudo-conversational state)

`CARDDEMO-COMMAREA` (COCOM01Y): FROM-TRANID X4, FROM-PROGRAM X8, TO-TRANID X4, TO-PROGRAM X8, USER-ID X8, USER-TYPE X1 (`A` admin / `U` user), PGM-CONTEXT 9 (0 enter / 1 re-enter), customer/account/card info, LAST-MAP X7, LAST-MAPSET X7.

`WS-THIS-PROGCOMMAREA` (CTLI-specific, appended after CARDDEMO-COMMAREA in a 2000-byte area):

| Field | PIC | Meaning |
|---|---|---|
| WS-CA-TYPE-CD | X(02) | Last type filter entered (change detection) |
| WS-CA-TYPE-DESC | X(50) | Last description filter entered |
| WS-CA-SCREEN-ROWS-OUT(7) | X(2)+X(50) | Rows last displayed (code, description) — old values for change detection |
| WS-CA-ROW-SELECTED | S9(4) COMP | Index of row last selected |
| WS-CA-LAST-TR-CODE | X(02) | Last key (= look-ahead key when a next page exists) |
| WS-CA-FIRST-TR-CODE | X(02) | First key on current page |
| WS-CA-SCREEN-NUM | 9(1) | Page number (1 = CA-FIRST-PAGE). Single digit: page 10 wraps to 0 |
| WS-CA-LAST-PAGE-DISPLAYED | 9(1) | 0 = last page shown, 9 = not shown |
| WS-CA-NEXT-PAGE-IND | X | `Y` = next page exists |
| WS-CA-DELETE-FLAG | X | `Y` = delete pending F10 confirm |
| WS-CA-UPDATE-FLAG | X | `Y` = update pending F10 confirm |

### 1.6 Keyset paging (7 rows)

- **Forward** (8000-READ-FORWARD, cursor C-TR-TYPE-FORWARD): `WHERE TR_TYPE >= :WS-START-KEY … ORDER BY TR_TYPE`. Fetch up to 7 rows into WS-CA-SCREEN-ROWS-OUT. Row 1 key → WS-CA-FIRST-TR-CODE; if screen num = 0 it becomes 1. On the 7th row: LAST key = 7th key, then a **look-ahead FETCH**: SQLCODE 0 → CA-NEXT-PAGE-EXISTS and LAST key ← look-ahead (8th) row key; +100 → CA-NEXT-PAGE-NOT-EXISTS (and if PF8 pressed, "No more pages for these search conditions"). Fewer than 7 rows → +100 → NEXT-PAGE-NOT-EXISTS; if screen 1 and 0 rows → "No records found for this search condition.". Next page start key is therefore the 8th row of the previous scan with `>=`, so no row is skipped or repeated.
- **Initial start key**: WS-CA-FIRST-TR-CODE is LOW-VALUES/spaces after initialization, so `>=` starts from the lowest key.
- **Backward** (8100-READ-BACKWARDS, cursor C-TR-TYPE-BACKWARD): LAST key ← current FIRST key; `WHERE TR_TYPE < :WS-START-KEY … ORDER BY TR_TYPE DESC`; fetches fill rows 7,6,…,1 (so the page is displayed ascending); when row 1 is filled, FIRST key ← that key. NEXT-PAGE-EXISTS is set TRUE. Any non-zero SQLCODE — **including +100 when fewer than 7 earlier rows exist** — goes to WHEN OTHER: DB2 error "Error on fetch Cursor C-TR-TYPE-BACKWARD SQLCODE: 100 …". In normal use pages are 7-aligned from the first key so 7 rows always exist; it can occur after concurrent deletes.
- **Page number**: +1 on PF8 (only when next page exists), −1 on PF7 (only when not first page).
- **PF7 on first page**: re-read current page; ERRMSG "No previous pages to display".
- **PF8 with no next page**: falls to WHEN OTHER (re-read current page). 2500: if last page already shown → "No more pages to display"; else INFOMSG "Type U to update, D to delete any record" and mark last page shown.
- **Filters reset paging**: 1220/1230 exit paragraphs `INITIALIZE WS-CA-PAGING-VARIABLES` whenever the filter value differs from the COMMAREA copy, and 1210 clears all row selections.

### 1.7 Filters

| Filter | Edit (¶) | Valid → predicate | Messages |
|---|---|---|---|
| Type (TRTYPE, 2) | 1220-EDIT-TYPECD: blank/LOW-VALUES/`00` → no filter; NOT NUMERIC → error, protect rows | `WS-EDIT-TYPE-FLAG='1' AND TR_TYPE = :WS-TYPE-CD-FILTER` (exact 2-char match, e.g. `1 ` is not numeric) | `TYPE CODE FILTER,IF SUPPLIED MUST BE A 2 DIGIT NUMBER` |
| Description (TRDESC, 50) | 1230-EDIT-DESC: blank → no filter; else `'%' ‖ TRIM(desc) ‖ '%'` | `TR_DESCRIPTION LIKE TRIM(:WS-TYPE-DESC-FILTER)` (case-sensitive, no escaping of `%`/`_`) | — |
| Cross edit | 1290-CROSS-EDITS → 9100-CHECK-FILTERS: COUNT(1)=0 → both supplied filters flagged NOT-OK, rows protected | | `No Records found for these filter conditions` |

### 1.8 Row actions (U / D) and F10 confirm

- 1210-EDIT-ARRAY: skipped (and selections cleared) if either filter changed. Tallies blanks, `D`, `U`. actions = 7 − blanks; valid = D+U.
- Any value other than blank/`D`/`U` → INPUT-ERROR, row flagged red, `Action code selected is invalid`.
- More than one action (2..7) → INPUT-ERROR, each selected row red, `Please select only 1 action` (**single-action rule**; overrides invalid-code message because it is set last).
- I-SELECTED = lowest-numbered row with a valid code; compared to WS-CA-ROW-SELECTED to detect selection change (change → F10 treated as ENTER).
- `U` → 1211-EDIT-ARRAY-DESC: if UPPER(TRIM(new)) = UPPER(TRIM(old)) and same trimmed length → `No change detected with respect to database values.` (message only, not INPUT-ERROR; case-only changes are therefore "no change"). Else edit via 1240 with name `Transaction Desc`: blank → `Transaction Desc must be supplied.` (row shows `*`); non alphanumeric/space → `Transaction Desc can have numbers or alphabets only.`.
- ENTER with exactly one valid D → highlighted row (neutral colour), INFOMSG `Delete HIGHLIGHTED row ? Press F10 to confirm`, CA-DELETE-REQUESTED. ENTER with one U → description unprotected, INFOMSG `Update HIGHLIGHTED row. Press F10 to save`, CA-UPDATE-REQUESTED. Both messages only if filters unchanged.
- F10 accepted only when a delete/update is pending **and** filters and the selected row are unchanged; otherwise it is ENTER.
- F10 delete → 9300; success: INFOMSG `HIGHLIGHTED row deleted.Hit Enter to continue`, COMMAREA re-initialized (next Enter goes back to page 1).
- F10 update → 9200; success: INFOMSG `HIGHLIGHTED row was updated`, selection cleared, current page re-read.

### 1.9 Screen attribute rules (2200/2400)

- Description column always protected (DFHBMPRF) except the U-selected row (DFHBMFSE, cursor, red if invalid).
- Select column protected for empty rows or when FLG-PROTECT-SELECT-ROWS-YES (filter error / no records).
- When an action is pending, filter fields become ASKIP blue (read-only) until the action is completed.
- Filter fields with errors: red, cursor placed there.

---

## 2. Program COTRTUPC (transaction CTTU)

### 2.1 Constants

LIT-THISPGM `COTRTUPC`, LIT-THISTRANID `CTTU`, LIT-THISMAPSET `COTRTUP ` (8 chars), LIT-THISMAP `CTRTUPA`, LIT-ADMINPGM `COADM01C`, LIT-ADMINTRANID `CA00`, LIT-LISTTPGM `COTRTLIC`, LIT-LISTTTRANID `CTLI`.

### 2.2 Paragraph inventory

| Paragraph | Purpose |
|---|---|
| 0000-MAIN | HANDLE ABEND; COMMAREA restore or initialize (EIBCALEN=0, or first entry from COADM01C or COTRTLIC); PF-key map/check; "simulate initial entry" reset EVALUATE; main dispatch EVALUATE |
| COMMON-RETURN | WS-RETURN-MSG→CCARD-ERROR-MSG; RETURN TRANSID(CTTU) COMMAREA |
| 0001-CHECK-PFKEYS | Valid-key table (see 2.6); invalid → `Invalid key pressed` |
| 1000-PROCESS-INPUTS | 1100, 1150, 1200 |
| 1100-RECEIVE-MAP | RECEIVE MAP(CTRTUPA) |
| 1150-STORE-MAP-IN-NEW | Copies TRTYPCDI/TRTYDSCI to TTUP-NEW-* (`*`/spaces → LOW-VALUES; else TRIM); skipped when NOT-FOUND, not F5, and key unchanged |
| 1200-EDIT-MAP-INPUTS | Key edit (unless creating/confirming), compare old/new, description edit, sets CHANGES-NOT-OK / CHANGES-OK-NOT-CONFIRMED |
| 1205-COMPARE-OLD-NEW | Upper-case/trimmed/length compare → `No change detected with respect to values fetched.` |
| 1210-EDIT-TRANTYPE | Key edit via 1245 (`Tran Type code`); on success NUMVAL → PIC 99 → zero-padded 2 chars (e.g. `1` → `01`) |
| 1230-EDIT-ALPHANUM-REQD | Required + alphanumeric/space-only |
| 1245-EDIT-NUM-REQD | Required, TEST-NUMVAL=0, NUMVAL≠0 |
| 2000-DECIDE-ACTION | State transition table (see 2.4) |
| 3000-SEND-MAP | 3100, 3200, 3250, 3300, 3390, 3391, 3400 |
| 3100-SCREEN-INIT | Titles/date/time |
| 3200-SETUP-SCREEN-VARS | Chooses 3201 (blank), 3202 (old values), 3203 (new values) by state |
| 3201/3202/3203 | Show initial / original / updated values |
| 3250-SETUP-INFOMSG | Info message by state (centered) + ERRMSG |
| 3300-SETUP-SCREEN-ATTRS | Protect all (3310) then unprotect by state (key FSET or 3320 desc only); cursor; red/`*` for blank key; CSSETATY for description |
| 3310-PROTECT-ALL-ATTRS / 3320-UNPROTECT-FEW-ATTRS | Attribute helpers |
| 3390-SETUP-INFOMSG-ATTRS | INFOMSG dark when empty else bright |
| 3391-SETUP-PFKEY-ATTRS | Shows F4/F5/F12 labels only when valid (mirrors 0001) |
| 3400-SEND-SCREEN | SEND MAP(CTRTUPA) CURSOR ERASE FREEKB |
| 9000-READ-TRANTYPE | 9100 + 9500 |
| 9100-GET-TRANSACTION-TYPE | Singleton SELECT by key |
| 9500-STORE-FETCHED-DATA | DCL → TTUP-OLD-* |
| 9600-WRITE-PROCESSING | UPDATE; +100 → 9700 INSERT; sets outcome state |
| 9700-INSERT-RECORD | INSERT |
| 9800-DELETE-PROCESSING | DELETE by TTUP-OLD-TTYP-TYPE |
| YYYY-STORE-PFKEY (CSSTRPFY) | PF mapping |
| ABEND-ROUTINE | SEND ABEND-DATA, ABEND ABCODE('9999' / '0001' "UNEXPECTED DATA SCENARIO") |

### 2.3 Perform graph (COTRTUPC)

```
0000-MAIN
 ├─ YYYY-STORE-PFKEY
 ├─ 0001-CHECK-PFKEYS
 ├─ EVALUATE dispatch
 │   ├─ [PF3]  SYNCPOINT, XCTL CDEMO-TO-PROGRAM
 │   ├─ [first entry]            3000-SEND-MAP
 │   ├─ [PF4 & CONFIRM-DELETE]   9800-DELETE-PROCESSING, 3000-SEND-MAP
 │   ├─ [PF4 & SHOW-DETAILS]     3000-SEND-MAP
 │   ├─ [PF5 & DETAILS-NOT-FOUND] 3000-SEND-MAP
 │   ├─ [PF5 & CHANGES-OK-NOT-CONFIRMED] 9600-WRITE-PROCESSING ── 9700-INSERT-RECORD ; 3000-SEND-MAP
 │   ├─ [PF12 & (N|9|S)]         2000-DECIDE-ACTION ── 9000-READ-TRANTYPE ── 9100, 9500 ; 3000-SEND-MAP
 │   ├─ [invalid key]            3000-SEND-MAP
 │   └─ [OTHER]                  1000-PROCESS-INPUTS ── 1100, 1150, 1200 ── 1205, 1210 ── 1245, 1230
 │                               2000-DECIDE-ACTION ── 9000-READ-TRANTYPE ; 3000-SEND-MAP
 │                                                   └─ ABEND-ROUTINE (WHEN OTHER)
 └─ COMMON-RETURN
3000-SEND-MAP ── 3100, 3200 (3201|3202|3203), 3250, 3300 (3310, 3320), 3390, 3391, 3400
```

### 2.4 State machine — `TTUP-CHANGE-ACTION`

| Value | 88-level | Meaning | Info message (3250) | Editable (3300) | Valid keys (0001) |
|---|---|---|---|---|---|
| LOW-VALUES / space | TTUP-DETAILS-NOT-FETCHED | Ask for key | `Enter transaction type to be maintained` | key | Enter, F3 |
| `K` | TTUP-INVALID-SEARCH-KEYS | Key failed edit | `Enter transaction type to be maintained` | key | Enter, F3 |
| `X` | TTUP-DETAILS-NOT-FOUND | Key valid, row absent | `Press F05 to add. F12 to cancel` (+ ERRMSG `No record found for this key in database`) | key | Enter, F3, F5, F12 |
| `S` | TTUP-SHOW-DETAILS | Row shown | `Enter transaction type to be maintained` (WHEN shares statement with BACKED-OUT/no old key — source quirk; `Selected transaction type shown above` literal is never used) | description | Enter, F3, F4, F12 |
| `R` | TTUP-CREATE-NEW-RECORD | Adding | `Enter new transaction type details.` | description | Enter, F3, F12 |
| `V` | TTUP-REVIEW-NEW-RECORD | Declared, unused | — | — | — |
| `9` | TTUP-CONFIRM-DELETE | Awaiting F4 | `Delete this record ? Press F4 to confirm` | none | F3, F4, F5(*), F12 — Enter invalid |
| `8` | TTUP-START-DELETE | Delete issued | (falls to no-info → search prompt) | none | |
| `7` | TTUP-DELETE-DONE | Deleted | `Delete successful.` | none | |
| `6` | TTUP-DELETE-FAILED | Delete failed (non -532) | `Changes unsuccessful` (key red) | none | |
| `E` | TTUP-CHANGES-NOT-OK | Edits failed | `Update transaction type details shown.` | description | Enter, F3 |
| `N` | TTUP-CHANGES-OK-NOT-CONFIRMED | Edits passed | `Changes validated.Press F5 to save` | none | Enter, F3, F5, F12 |
| `L` | TTUP-CHANGES-OKAYED-LOCK-ERROR | -911 | `Changes unsuccessful` | | |
| `F` | TTUP-CHANGES-OKAYED-BUT-FAILED | other <0 | `Changes unsuccessful` | | |
| `C` | TTUP-CHANGES-OKAYED-AND-DONE | Saved | `Changes committed to database` | none (key cursor) | |
| `B` | TTUP-CHANGES-BACKED-OUT | F12 after validate | `Update transaction type details shown.` (or search prompt if no old key) | description | |

(*) F5 is listed as valid while TTUP-DELETE-IN-PROGRESS (`6`–`9`) in 0001 but no dispatch branch handles it, so it falls to OTHER.

Reset to initial entry (0000 "simulate initial entry"): F12 in S/R/X; or state C, L/F, B (with no old details), 7, 6 → PGM-ENTER + NOT-FETCHED.

Transitions (2000-DECIDE-ACTION + 0000 dispatch):

```
(entry) ──Enter key──▶ edit key ─blank─▶ NOT-FETCHED  ERR "No input received"
                              ─bad──▶ K              ERR "Tran Type code must be numeric." / "... must not be zero."
                              ─ok──▶ 9000 SELECT ─found─▶ S
                                                 ─+100──▶ X  ERR "No record found for this key in database"
X ──F5──▶ R ──Enter (desc)──▶ edit desc ─ok─▶ N ─F5─▶ 9600 UPDATE ─+100─▶ 9700 INSERT ─▶ C
                                       ─bad▶ E
S ──Enter (desc changed)──▶ N | E ; unchanged → S + ERR "No change detected with respect to values fetched."
N ──F5──▶ 9600 UPDATE (rows>0) ─▶ C   (‑911 ▶ L "Could not lock record for update"; <0 ▶ F "Error updating: …")
N ──F12─▶ B  ERR "Update was cancelled"  ──Enter──▶ E
S ──F4──▶ 9 ──F4──▶ 8 → 9800 DELETE ─0─▶ 7 ; ‑532 ▶ stays 8, ERR "Please delete associated child records first:SQLCODE :…"; other ▶ 6
9 ──F12─▶ NOT-FETCHED  ERR "Delete was cancelled"
S|R|X ──F12──▶ NOT-FETCHED (initial)
C|L|F|7|6 ──any──▶ initial entry
```

### 2.5 Add vs. edit (9600/9700)

There is no separate "add" SQL path: F5 from `N` always issues `UPDATE … SET TR_DESCRIPTION WHERE TR_TYPE = :key`; SQLCODE +100 → `INSERT (TR_TYPE, TR_DESCRIPTION)`. Add therefore equals "update, or insert if absent" (upsert). Notes:
- DCL-TR-DESCRIPTION-LEN = `FUNCTION LENGTH(TTUP-NEW-TTYP-TYPE-DESC)` = 50 always, so DB2 stores the description right-padded to 50 (same in CTLI 9200).
- 9700 does not `MOVE SQLCODE TO WS-DISP-SQLCODE`, so an INSERT failure message shows the stale `100` from the UPDATE, e.g. `Error inserting record into: TRANSACTION_TYPE Table. SQLCODE:  100:<SQLERRM>`. A duplicate key (-803) is only possible under a race (another task inserts between UPDATE and INSERT).
- In S the key field is protected (3320 unprotects only TRTYDSC), so a key can't be changed into a new key during edit.

### 2.6 Validation (CTTU)

| Field | ¶ | Rule | Message |
|---|---|---|---|
| Type code | 1245 | blank/LOW-VALUES | `Tran Type code must be supplied.` — but 1200 overrides to `No input received` only if no message yet; since 1245 sets the message first, the observed message is `Tran Type code must be supplied.` when blank. Screen shows `*` red. |
| Type code | 1245 | TEST-NUMVAL≠0 | `Tran Type code must be numeric.` |
| Type code | 1245 | NUMVAL=0 | `Tran Type code must not be zero.` |
| Type code | 1210 | normalize | zero-pad to 2 (`1`→`01`, ` 5`→`05`) |
| Description | 1230 | blank | `Transaction Desc must be supplied.` |
| Description | 1230 | chars other than A–Z a–z 0–9 space | `Transaction Desc can have numbers or alphabets only.` |
| Description | 1205 | case-insensitive equal to fetched | `No change detected with respect to values fetched.` |
| Length | BMS | TRTYDSC length 50 | (field length) |

(Blank key check: in 1245 `WS-RETURN-MSG-OFF` is true, so `Tran Type code must be supplied.` is set; 1200 then sees FLG-TRANFILTER-BLANK and only sets `No input received` if WS-RETURN-MSG-OFF — which is now false. The literal `No input received` is therefore effectively unreachable.)

### 2.7 Navigation

- Admin menu `COADM01C` (tran `CA00`), options from `COADM02Y`: **5** `Transaction Type List/Update (Db2)` → `COTRTLIC`; **6** `Transaction Type Maintenance (Db2)` → `COTRTUPC`. COADM01C XCTLs to `CDEMO-ADMIN-OPT-PGMNAME(option)`.
- CTLI PF2 → XCTL `COTRTUPC`. CTLI PF3 → SYNCPOINT, XCTL `COADM01C`.
- CTTU PF3 → SYNCPOINT, XCTL to CDEMO-FROM-PROGRAM, or `COADM01C`/`CA00` when blank. Because CTTU initializes CARDDEMO-COMMAREA on first entry from COADM01C or COTRTLIC, FROM-PROGRAM is blank → PF3 always returns to the **admin menu**, even when CTTU was reached from CTLI (CTLI's `PF3 AND FROM-TRANID = CTTU` re-init branch is therefore dormant).
- Admin-only access: neither program checks CDEMO-USER-TYPE; both set CDEMO-USRTYP-ADMIN themselves. Access control is implied by reachability: only the admin menu (COADM01C, reached after sign-on COSGN00C with user type `A`) offers options 5/6. CSD defines `CTLI`→COTRTLIC and `CTTU`→COTRTUPC (`csd/CRDDEMOD.csd`), so a user who knows the TRANID can start them directly — there is no in-program guard.

---

## 3. PF-key tables

YYYY-STORE-PFKEY maps PF13–PF24 to PF1–PF12 for both programs.

### CTLI

| Key | Label (BMS) | Condition | Effect |
|---|---|---|---|
| Enter | — | always | Apply filters / stage U or D action / refresh |
| F2 | `F2=Add` | FROM-PROGRAM=self | XCTL COTRTUPC |
| F3 | `F3=Exit` | always | XCTL COADM01C |
| F7 | `F7=Page Up` | always | Previous page (message on first page) |
| F8 | `F8=Page Dn` | always | Next page (message when none) |
| F10 | `F10=Save` | only with pending D/U | Confirm delete / update |
| other | — | — | Treated as Enter |

### CTTU

| Key | Label | Valid when (0001-CHECK-PFKEYS) | Effect |
|---|---|---|---|
| Enter | `ENTER=Process` | not CONFIRM-DELETE | Process inputs |
| F3 | `F3=Exit` | always | XCTL caller / COADM01C |
| F4 | `F4=Delete` | S or 9 | S→9 ask confirm; 9→delete |
| F5 | `F5=Save` | N, X, or 6–9 | X→R (add); N→save |
| F6 | `F6=Add` | never (label defined, always dark) | — |
| F12 | `F12=Cancel` | N, S, X, 9, R | Cancel |
| other | — | — | `Invalid key pressed` |

---

## 4. BMS field inventory

Attribute legend: ASKIP auto-skip/protected, PROT protected, UNPROT input, FSET modified-data-tag set, IC initial cursor, BRT bright, DRK dark, NORM normal.

### 4.1 Mapset COTRTLI, map CTRTLIA (24×80)

| Name | POS (r,c) | Len | ATTRB | Colour / HILIGHT | INITIAL | I/O |
|---|---|---|---|---|---|---|
| (label) | 1,1 | 5 | ASKIP,NORM | BLUE | `Tran:` | O |
| TRNNAME | 1,7 | 4 | ASKIP,FSET,NORM | BLUE | | O |
| TITLE01 | 1,21 | 40 | ASKIP,NORM | YELLOW | | O |
| (label) | 1,65 | 5 | ASKIP,NORM | BLUE | `Date:` | O |
| CURDATE | 1,71 | 8 | ASKIP,NORM | BLUE | `mm/dd/yy` | O |
| (label) | 2,1 | 5 | ASKIP,NORM | BLUE | `Prog:` | O |
| PGMNAME | 2,7 | 8 | ASKIP,NORM | BLUE | | O |
| TITLE02 | 2,21 | 40 | ASKIP,NORM | YELLOW | | O |
| (label) | 2,65 | 5 | ASKIP,NORM | BLUE | `Time:` | O |
| CURTIME | 2,71 | 8 | ASKIP,NORM | BLUE | `hh:mm:ss` | O |
| (label) | 4,28 | 25 | (default) | NEUTRAL | `Maintain Transaction Type` | O |
| (label) | 4,70 | 5 | (default) | | `Page ` | O |
| PAGENO | 4,76 | 3 | (default) | | | O |
| (label) | 6,30 | 12 | ASKIP,NORM | TURQUOISE | `Type Filter:` | O |
| TRTYPE | 6,44 | 2 | FSET,IC,NORM,UNPROT | GREEN / UNDERLINE | | I/O |
| (stopper) | 6,47 | 0 | | | | |
| (label) | 8,4 | 19 | ASKIP,NORM | TURQUOISE | `Description Filter:` | O |
| TRDESC | 8,25 | 50 | FSET,NORM,UNPROT | GREEN / UNDERLINE | | I/O |
| (stopper) | 8,76 | 0 | | | | |
| (header) | 10,4 | 10 | | NEUTRAL | `Select    ` | O |
| (header) | 10,16 | 4 | | NEUTRAL | `Type` | O |
| (header) | 10,42 | 11 | | NEUTRAL | `Description` | O |
| (rule) | 11,4 / 11,15 / 11,25 | 6 / 5 / 50 | | NEUTRAL | dashes | O |
| TRTSEL1..7 | (12..18),6 | 1 | FSET,NORM,PROT (program unprotects) | DEFAULT / UNDERLINE | | I/O |
| TRTTYP1..7 | (12..18),17 | 2 | FSET,NORM,PROT | DEFAULT | | O (received for key) |
| TRTYPD1..7 | (12..18),25 | 50 | FSET,NORM,UNPROT (program protects except U row) | DEFAULT | | I/O |
| TRTSELA | 19,6 | 1 | FSET,NORM,PROT | DEFAULT | | (8th row; not addressed by the 7-row REDEFINES — unused) |
| TRTTYPA | 19,17 | 2 | FSET,NORM,PROT | DEFAULT | | unused |
| TRTDSCA | 19,25 | 50 | FSET,NORM,PROT | DEFAULT | | unused |
| INFOMSG | 21,19 | 45 | PROT | NEUTRAL | | O |
| ERRMSG | 23,1 | 78 | ASKIP,BRT,FSET | RED | | O |
| BUTNF02 | 24,1 | 7 | ASKIP,NORM | TURQUOISE | `F2=Add` | O |
| BUTNF03 | 24,10 | 7 | ASKIP,NORM | TURQUOISE | `F3=Exit` | O |
| BUTNF07 | 24,19 | 10 | ASKIP,NORM | TURQUOISE | `F7=Page Up` | O |
| BUTNF08 | 24,32 | 10 | ASKIP,NORM | TURQUOISE | `F8=Page Dn` | O |
| BUTNF10 | 24,44 | 8 | ASKIP,NORM | TURQUOISE | `F10=Save` | O |

Program redefinition: `FILLER REDEFINES CTRTLIAI` skips 238 bytes then `EACH-ROWI OCCURS 7` (TRTSEL L/F/A/I, TRTTYP…, TRTYPD…), mirrored for output with C/P/H/V/O sub-fields.

### 4.2 Mapset COTRTUP, map CTRTUPA (24×80)

| Name | POS | Len | ATTRB | Colour / HILIGHT | INITIAL | I/O |
|---|---|---|---|---|---|---|
| (label) | 1,1 | 5 | ASKIP,NORM | BLUE | `Tran:` | O |
| TRNNAME | 1,7 | 4 | ASKIP,FSET,NORM | BLUE | | O |
| TITLE01 | 1,21 | 40 | ASKIP,NORM | YELLOW | | O |
| (label) | 1,65 | 5 | ASKIP,NORM | BLUE | `Date:` | O |
| CURDATE | 1,71 | 8 | ASKIP,NORM | BLUE | `mm/dd/yy` | O |
| (label) | 2,1 | 5 | ASKIP,NORM | BLUE | `Prog:` | O |
| PGMNAME | 2,7 | 8 | ASKIP,NORM | BLUE | | O |
| TITLE02 | 2,21 | 40 | ASKIP,NORM | YELLOW | | O |
| (label) | 2,65 | 5 | ASKIP,NORM | BLUE | `Time:` | O |
| CURTIME | 2,71 | 8 | ASKIP,NORM | BLUE | `hh:mm:ss` | O |
| (label) | 7,28 | 25 | | NEUTRAL | `Maintain Transaction Type` | O |
| (label) | 12,4 | 19 | ASKIP,NORM | TURQUOISE | `Transaction Type  :` | O |
| TRTYPCD | 12,26 | 2 | IC,UNPROT | UNDERLINE | | I/O |
| (stopper) | 12,29 | 0 | | | | |
| (label) | 14,4 | 19 | | TURQUOISE | `Description       :` | O |
| TRTYDSC | 14,26 | 50 | UNPROT | UNDERLINE | | I/O |
| (stopper) | 14,77 | 0 | | | | |
| INFOMSG | 22,23 | 45 | ASKIP | NEUTRAL | | O |
| ERRMSG | 23,1 | 78 | ASKIP,BRT,FSET | RED | | O |
| FKEYS | 24,1 | 21 | ASKIP,NORM | YELLOW | `ENTER=Process F3=Exit` | O |
| FKEY04 | 24,23 | 9 | ASKIP,DRK | YELLOW | `F4=Delete` | O |
| FKEY05 | 24,33 | 8 | ASKIP,DRK | YELLOW | `F5=Save` | O |
| FKEY06 | 24,43 | 6 | ASKIP,DRK | YELLOW | `F6=Add` | O |
| FKEY12 | 24,69 | 10 | ASKIP,DRK | YELLOW | `F12=Cancel` | O |

---

## 5. SQL inventory

Host structures: `DCLTRANSACTION-TYPE` (DCLTRTYP): `DCL-TR-TYPE` X(2), `DCL-TR-DESCRIPTION` = `DCL-TR-DESCRIPTION-LEN` S9(4) COMP + `DCL-TR-DESCRIPTION-TEXT` X(50) (VARCHAR). `DCLTRCAT` included in COTRTUPC but unused. Error text: 9999-FORMAT-DB2-MESSAGE (CTLI) → `<action> SQLCODE:<----9> <DSNTIAC text>`; CTTU builds its own STRINGs with `SQLERRM`.

| # | Program / ¶ | Statement | Host vars (in → out) | SQLCODE branches |
|---|---|---|---|---|
| S1 | CTLI 9400/8000/9450 | `DECLARE C-TR-TYPE-FORWARD CURSOR FOR SELECT TR_TYPE, TR_DESCRIPTION FROM CARDDEMO.TRANSACTION_TYPE WHERE TR_TYPE >= :WS-START-KEY AND ((:WS-EDIT-TYPE-FLAG='1' AND TR_TYPE=:WS-TYPE-CD-FILTER) OR :WS-EDIT-TYPE-FLAG<>'1') AND ((:WS-EDIT-DESC-FLAG='1' AND TR_DESCRIPTION LIKE TRIM(:WS-TYPE-DESC-FILTER)) OR :WS-EDIT-DESC-FLAG<>'1') ORDER BY TR_TYPE` | WS-START-KEY, WS-EDIT-TYPE-FLAG, WS-TYPE-CD-FILTER, WS-EDIT-DESC-FLAG, WS-TYPE-DESC-FILTER → DCL-TR-TYPE, DCL-TR-DESCRIPTION | OPEN ≠0: `C-TR-TYPE-FORWARD Open`; FETCH 0: row; +100: end (no next page; "No records found…" if page 1 empty; "No more pages for these search conditions" if PF8); look-ahead other: `C-TR-TYPE-FORWARD fetch`; FETCH other: `C-TR-TYPE-FORWARD close` (label as coded) + DB2-ERROR; CLOSE ≠0: `C-TR-TYPE-FORWARD close` |
| S2 | CTLI 9500/8100/9550 | `DECLARE C-TR-TYPE-BACKWARD CURSOR FOR SELECT … WHERE TR_TYPE < :WS-START-KEY AND <same filters> ORDER BY TR_TYPE DESC` | same | OPEN ≠0: `C-TR-TYPE-BACKWARD Open`; FETCH 0: row; FETCH other (incl. +100): `Error on fetch Cursor C-TR-TYPE-BACKWARD`; CLOSE ≠0: `C-TR-TYPE-BACKWARD close` |
| S3 | CTLI 9100-CHECK-FILTERS | `SELECT COUNT(1) INTO :WS-RECORDS-COUNT FROM CARDDEMO.TRANSACTION_TYPE WHERE <same filters>` | flags, filters → WS-RECORDS-COUNT | 0: continue (0 count → `No Records found for these filter conditions`); other: INPUT-ERROR, `Error reading TRANSACTION_TYPE table ` |
| S4 | CTLI 9200-UPDATE-RECORD | `UPDATE CARDDEMO.TRANSACTION_TYPE SET TR_DESCRIPTION = :DCL-TR-DESCRIPTION WHERE TR_TYPE = :DCL-TR-TYPE` | WS-ROW-TR-CODE-IN(I-SELECTED), TRIM(WS-ROW-TR-DESC-IN), len 50 | 0: SYNCPOINT, `HIGHLIGHTED row was updated`; +100: `Record not found. Deleted by others ? `; -911: INPUT-ERROR, `Deadlock. Someone else updating ?`; <0: `Update failed with` (all + ` SQLCODE:…`) |
| S5 | CTLI 9300-DELETE-RECORD | `DELETE FROM CARDDEMO.TRANSACTION_TYPE WHERE TR_TYPE = :DCL-TR-TYPE` | WS-ROW-TR-CODE-IN(I-SELECTED) | 0: SYNCPOINT, `HIGHLIGHTED row deleted.Hit Enter to continue`; -532: `Please delete associated child records first:` + ` SQLCODE: -532 <text>`; other (incl. +100): `Delete failed with message:` |
| S6 | CTTU 9100-GET-TRANSACTION-TYPE | `SELECT TR_TYPE, TR_DESCRIPTION INTO :DCL-TR-TYPE, :DCL-TR-DESCRIPTION FROM CARDDEMO.TRANSACTION_TYPE WHERE TR_TYPE = :DCL-TR-TYPE` | TTUP-NEW-TTYP-TYPE → DCL-* | 0: FOUND; +100: `No record found for this key in database` (state X); <0: `Error accessing: TRANSACTION_TYPE table. SQLCODE:<code>:<SQLERRM>` |
| S7 | CTTU 9600-WRITE-PROCESSING | `UPDATE CARDDEMO.TRANSACTION_TYPE SET TR_DESCRIPTION = :DCL-TR-DESCRIPTION WHERE TR_TYPE = :DCL-TR-TYPE` | TTUP-NEW-*, len 50 | 0: SYNCPOINT → C; +100: PERFORM 9700; -911: `Could not lock record for update` → L; <0: `Error updating: TRANSACTION_TYPE Table. SQLCODE:<code>:<SQLERRM>` → F |
| S8 | CTTU 9700-INSERT-RECORD | `INSERT INTO CARDDEMO.TRANSACTION_TYPE (TR_TYPE, TR_DESCRIPTION) VALUES (:DCL-TR-TYPE, :DCL-TR-DESCRIPTION)` | DCL-* | 0: SYNCPOINT → C; other (e.g. -803 duplicate): `Error inserting record into: TRANSACTION_TYPE Table. SQLCODE:<stale code>:<SQLERRM>` → F |
| S9 | CTTU 9800-DELETE-PROCESSING | `DELETE FROM CARDDEMO.TRANSACTION_TYPE WHERE TR_TYPE = :DCL-TR-TYPE` | TTUP-OLD-TTYP-TYPE | 0: → 7, SYNCPOINT; -532: `Please delete associated child records first:SQLCODE :<code>:<SQLERRM><SQLERRM>` (state stays 8); other: `Delete failed with message:SQLCODE :<code>:<SQLERRM>` → 6 |
| S10 | both, 9998-PRIMING-QUERY | `SELECT 1 INTO :WS-DUMMY-DB2-INT FROM SYSIBM.SYSDUMMY1 FETCH FIRST 1 ROW ONLY` | → WS-DUMMY-DB2-INT | 0: ok; other: `Db2 access failure. ` + SEND-LONG-TEXT (CTLI) |

-532 = DB2 "delete restricted by referential constraint" (FK `TRANSACTION_TYPE_CATEGORY.TRC_TYPE_CODE … ON DELETE RESTRICT`). -911 = deadlock/timeout rollback. -803 = duplicate key on unique index `XTRAN_TYPE`.

---

## 6. Message literal catalogue

| ID | Literal (exact) | Program / ¶ | Line |
|---|---|---|---|
| M-L01 | `Type U to update, D to delete any record` | CTLI 2500 | INFO |
| M-L02 | `Delete HIGHLIGHTED row ? Press F10 to confirm` | CTLI 2500 | INFO |
| M-L03 | `Update HIGHLIGHTED row. Press F10 to save` | CTLI 2500 | INFO |
| M-L04 | `HIGHLIGHTED row deleted.Hit Enter to continue` | CTLI 9300/2500 | INFO |
| M-L05 | `HIGHLIGHTED row was updated` | CTLI 9200/2500 | INFO |
| M-L06 | `PF03 pressed. Exiting` | CTLI 0000 (PF2 path, not displayed) | ERR |
| M-L07 | `No records found for this search condition.` | CTLI 8000 | ERR |
| M-L08 | `No more pages for these search conditions` | CTLI 8000 | ERR |
| M-L09 | `Please select only 1 action` | CTLI 1210 | ERR |
| M-L10 | `Action code selected is invalid` | CTLI 1210 | ERR |
| M-L11 | `No change detected with respect to database values.` | CTLI 1211 | ERR |
| M-L12 | `TYPE CODE FILTER,IF SUPPLIED MUST BE A 2 DIGIT NUMBER` | CTLI 1220 | ERR |
| M-L13 | `No Records found for these filter conditions` | CTLI 1290 | ERR |
| M-L14 | `Transaction Desc must be supplied.` | CTLI 1240 / CTTU 1230 | ERR |
| M-L15 | `Transaction Desc can have numbers or alphabets only.` | CTLI 1240 / CTTU 1230 | ERR |
| M-L16 | `No previous pages to display` | CTLI 2500 | ERR |
| M-L17 | `No more pages to display` | CTLI 2500 | ERR |
| M-L18 | `Record not found. Deleted by others ? ` + ` SQLCODE:` | CTLI 9200 | ERR |
| M-L19 | `Deadlock. Someone else updating ?` + ` SQLCODE:` | CTLI 9200 | ERR |
| M-L20 | `Update failed with` + ` SQLCODE:` | CTLI 9200 | ERR |
| M-L21 | `Please delete associated child records first:` + ` SQLCODE:` | CTLI 9300 | ERR |
| M-L22 | `Delete failed with message:` + ` SQLCODE:` | CTLI 9300 | ERR |
| M-L23 | `Error reading TRANSACTION_TYPE table ` | CTLI 9100 | ERR |
| M-L24 | `C-TR-TYPE-FORWARD Open` / `fetch` / `close`, `C-TR-TYPE-BACKWARD Open` / `close`, `Error on fetch Cursor C-TR-TYPE-BACKWARD` | CTLI 8000/8100/94xx/95xx | ERR |
| M-L25 | `Db2 access failure. ` | CSDB2RPY 9998 | ERR |
| M-U01 | `Enter transaction type to be maintained` | CTTU 3250 | INFO |
| M-U02 | `Press F05 to add. F12 to cancel` | CTTU 3250 | INFO |
| M-U03 | `Delete this record ? Press F4 to confirm` | CTTU 3250 | INFO |
| M-U04 | `Delete successful.` | CTTU 3250 | INFO |
| M-U05 | `Update transaction type details shown.` | CTTU 3250 | INFO |
| M-U06 | `Enter new transaction type details.` | CTTU 3250 | INFO |
| M-U07 | `Changes validated.Press F5 to save` | CTTU 3250 | INFO |
| M-U08 | `Changes committed to database` | CTTU 3250 | INFO |
| M-U09 | `Changes unsuccessful` | CTTU 3250 | INFO |
| M-U10 | `Selected transaction type shown above` | CTTU (declared, unused) | INFO |
| M-U11 | `PF03 pressed.Exiting` | CTTU (declared) | ERR |
| M-U12 | `Invalid Key pressed. ` / `Invalid key pressed` | CTTU 0001 | ERR |
| M-U13 | `No record found for this key in database` | CTTU 9100 | ERR |
| M-U14 | `No input received` | CTTU 1200 | ERR |
| M-U15 | `No change detected with respect to values fetched.` | CTTU 1205 | ERR |
| M-U16 | `Could not lock record for update` | CTTU 9600 | ERR |
| M-U17 | `Update was cancelled` | CTTU 2000 | ERR |
| M-U18 | `Delete was cancelled` | CTTU 2000 | ERR |
| M-U19 | `Tran Type code must be supplied.` | CTTU 1245 | ERR |
| M-U20 | `Tran Type code must be numeric.` | CTTU 1245 | ERR |
| M-U21 | `Tran Type code must not be zero.` | CTTU 1245 | ERR |
| M-U22 | `Error accessing: TRANSACTION_TYPE table. SQLCODE:` | CTTU 9100 | ERR |
| M-U23 | `Error updating: TRANSACTION_TYPE Table. SQLCODE:` | CTTU 9600 | ERR |
| M-U24 | `Error inserting record into: TRANSACTION_TYPE Table. SQLCODE:` | CTTU 9700 | ERR |
| M-U25 | `Please delete associated child records first:SQLCODE :` | CTTU 9800 | ERR |
| M-U26 | `Delete failed with message:SQLCODE :` | CTTU 9800 | ERR |
| M-U27 | Declared, unused: `Name can only contain alphabets and spaces`, `Record changed by some one else. Please review`, `Update of record failed`, `Delete of record failed`, `Looks Good.... so far` | CTTU | — |
| M-U28 | `UNEXPECTED DATA SCENARIO` / `UNEXPECTED ABEND OCCURRED.` | CTTU 2000 / ABEND-ROUTINE | abend |

---

## 7. Data model and seed

`CARDDEMO.TRANSACTION_TYPE` (TR_TYPE CHAR(2) NOT NULL PK [unique index XTRAN_TYPE], TR_DESCRIPTION VARCHAR(50) NOT NULL), EBCDIC CCSID.
`CARDDEMO.TRANSACTION_TYPE_CATEGORY` (TRC_TYPE_CODE CHAR(2), TRC_TYPE_CATEGORY CHAR(4), TRC_CAT_DATA VARCHAR(50), all NOT NULL; PK (code, category) [X_TRAN_TYPE_CATG]; FK TRC_TYPE_CODE → TRANSACTION_TYPE(TR_TYPE) ON DELETE RESTRICT).

CREADB21: step CRCRDDB (DSNTIAD, `DB2CREAT`), LDTTYPE/RUNTEP2 (DSNTEP4, `DB2LTTYP`: 7 rows `01 PURCHASE` … `06 REVERAL` [sic] … `07 ADJUSTMENT`), LDTCCAT (DSNTEP4, `DB2LTCAT`: 18 category rows for types 01–07). All seeded types have child categories, so any delete of a seeded type triggers -532.

## 8. Dual storage divergence risk (out of scope)

The base CardDemo batch/online flows read transaction types from VSAM/sequential files (`TRANTYPE.PS`, `TRANCATG.PS`, LRECL 60), not DB2. `TRANEXTR.jcl` backs up the PS files to GDGs, deletes them and re-extracts from DB2 via DSNTIAUL (`TR_TYPE ‖ CHAR(TR_DESCRIPTION,50) ‖ '00000000'`). Between a CTLI/CTTU change and the next TRANEXTR run (followed by the base app's VSAM reload), DB2 and VSAM disagree. `COBTUPDT` (MNTTRDB2) also updates DB2 in batch. Synchronisation is **out of scope** for this modernization; the new service owns only the PostgreSQL tables. See `deviations.md`.

## 9. Observed source quirks (behaviour preserved or documented)

| # | Quirk | Location |
|---|---|---|
| Q1 | Duplicate `WHEN CCARD-AID-PFK07 AND CA-FIRST-PAGE` | CTLI 0000 |
| Q2 | PF2 sets CDEMO-USRTYP-USER before XCTL to CTTU | CTLI 0000 |
| Q3 | Page number PIC 9(1): wraps after 9 | CTLI COMMAREA |
| Q4 | Backward read errors (SQLCODE 100) if <7 earlier rows | CTLI 8100 |
| Q5 | Descriptions stored padded to 50 (LEN = 50) | CTLI 9200, CTTU 9600 |
| Q6 | INSERT error shows stale SQLCODE | CTTU 9700 |
| Q7 | `No input received` unreachable; blank key shows `Tran Type code must be supplied.` | CTTU 1200/1245 |
| Q8 | SHOW-DETAILS info message = `Enter transaction type to be maintained` | CTTU 3250 |
| Q9 | -532 in CTTU leaves state `8`; SQLERRM appended twice | CTTU 9800 |
| Q10 | PF3 from CTTU always returns to admin menu | CTTU 0000 |
| Q11 | No user-type authorization check in either program | both |
| Q12 | Description filter LIKE is case-sensitive; `%`/`_` not escaped | CTLI 1230 |
| Q13 | Case-only description change = "no change" | CTLI 1211, CTTU 1205 |
| Q14 | 8th BMS row (TRTSELA/TRTTYPA/TRTDSCA) defined but never addressed | COTRTLI.bms |

---

## 10. Traceability (discovery → requirement IDs)

Requirement text is in `requirements-ctli-cttu.md`; this table anchors each ID to source.

| REQ ID | Behaviour | Source paragraph | BMS field(s) |
|---|---|---|---|
| REQ-CTLI-001 | First page = up to 7 rows ascending from lowest key | 0000 (EIBCALEN=0), 8000-READ-FORWARD | TRTTYP1-7, TRTYPD1-7 |
| REQ-CTLI-002 | Next-page lookahead sets hasNext and next start key | 8000-READ-FORWARD (look-ahead FETCH) | BUTNF08 |
| REQ-CTLI-003 | Page forward (F8) `TR_TYPE >= lastKey`, page +1 | 0000 WHEN PFK08, 8000 | BUTNF08, PAGENO |
| REQ-CTLI-004 | Page back (F7) `TR_TYPE < firstKey DESC`, reversed, page −1 | 0000 WHEN PFK07, 8100-READ-BACKWARDS | BUTNF07, PAGENO |
| REQ-CTLI-005 | F7 on first page → `No previous pages to display` | 0000, 2500 | ERRMSG |
| REQ-CTLI-006 | F8 on last page → `No more pages to display` / info | 2500, 8000 | ERRMSG, INFOMSG |
| REQ-CTLI-007 | Empty result page 1 → `No records found for this search condition.` | 8000 (+100) | ERRMSG |
| REQ-CTLI-008 | Page number display | 2100, 8000 | PAGENO |
| REQ-CTLI-009 | Type filter exact 2-digit numeric; blank/00 = none | 1220-EDIT-TYPECD | TRTYPE |
| REQ-CTLI-010 | Invalid type filter message | 1220 | TRTYPE, ERRMSG |
| REQ-CTLI-011 | Description filter `LIKE '%x%'` | 1230-EDIT-DESC | TRDESC |
| REQ-CTLI-012 | Filters with 0 matches → `No Records found for these filter conditions` | 1290, 9100-CHECK-FILTERS | ERRMSG |
| REQ-CTLI-013 | Filter change resets paging and selections | 1220/1230 exits, 1210 | TRTYPE, TRDESC |
| REQ-CTLI-014 | Row action codes U/D only; other → `Action code selected is invalid` | 1210-EDIT-ARRAY | TRTSEL1-7 |
| REQ-CTLI-015 | Single-action rule → `Please select only 1 action` | 1210 | TRTSEL1-7 |
| REQ-CTLI-016 | D + Enter → confirm prompt | 0000, 2300, 2500 | INFOMSG |
| REQ-CTLI-017 | D + F10 → delete; success message; return to page 1 | 9300, 0000 | INFOMSG, BUTNF10 |
| REQ-CTLI-018 | Delete with children → `Please delete associated child records first:` | 9300 (-532) | ERRMSG |
| REQ-CTLI-019 | Other delete failure → `Delete failed with message:` | 9300 (OTHER) | ERRMSG |
| REQ-CTLI-020 | U + Enter → description editable, confirm prompt | 2200, 2300, 2500 | TRTYPDn, INFOMSG |
| REQ-CTLI-021 | U description edits (required, alnum+space, ≤50) | 1211, 1240 | TRTYPDn, ERRMSG |
| REQ-CTLI-022 | U unchanged → `No change detected with respect to database values.` | 1211 | ERRMSG |
| REQ-CTLI-023 | U + F10 → update; `HIGHLIGHTED row was updated` | 9200 | INFOMSG |
| REQ-CTLI-024 | Update not found → `Record not found. Deleted by others ? ` | 9200 (+100) | ERRMSG |
| REQ-CTLI-025 | Update deadlock → `Deadlock. Someone else updating ?` | 9200 (-911) | ERRMSG |
| REQ-CTLI-026 | Update other failure → `Update failed with` | 9200 (<0) | ERRMSG |
| REQ-CTLI-027 | F10 only valid with pending action and unchanged context | 0000 PF10 checks | BUTNF10 |
| REQ-CTLI-028 | F2 → CTTU add/edit | 0000 PF2 XCTL | BUTNF02 |
| REQ-CTLI-029 | F3 → admin menu | 0000 PF3 XCTL | BUTNF03 |
| REQ-CTLI-030 | Invalid PF key treated as Enter | 0000 | — |
| REQ-CTLI-031 | Default info message `Type U to update, D to delete any record` | 2500 | INFOMSG |
| REQ-CTLI-032 | DB error on read → formatted message | 8000/8100/94xx/95xx, 9999 | ERRMSG |
| REQ-CTLI-033 | Header: tran, program, date, time, titles | 2100 | TRNNAME, PGMNAME, CURDATE, CURTIME, TITLE01/02 |
| REQ-CTTU-001 | Initial entry prompts for key | 0000, 3250 | TRTYPCD, INFOMSG |
| REQ-CTTU-002 | Key required | 1245 | TRTYPCD, ERRMSG |
| REQ-CTTU-003 | Key numeric | 1245 | TRTYPCD |
| REQ-CTTU-004 | Key non-zero | 1245 | TRTYPCD |
| REQ-CTTU-005 | Key zero-padded to 2 | 1210 | TRTYPCD |
| REQ-CTTU-006 | Found → show details (desc editable) | 9100 (0), 2000, 3202, 3320 | TRTYDSC |
| REQ-CTTU-007 | Not found → `No record found for this key in database` + add prompt | 9100 (+100), 3250 | ERRMSG, INFOMSG, FKEY05 |
| REQ-CTTU-008 | Read DB error → `Error accessing: TRANSACTION_TYPE table. SQLCODE:` | 9100 (<0) | ERRMSG |
| REQ-CTTU-009 | F5 from not-found → create-new state | 0000, 2000 | INFOMSG |
| REQ-CTTU-010 | Description edits (required, alnum+space, ≤50) | 1230 | TRTYDSC |
| REQ-CTTU-011 | Unchanged desc → `No change detected with respect to values fetched.` | 1205 | ERRMSG |
| REQ-CTTU-012 | Valid edits → confirm prompt `Changes validated.Press F5 to save` | 1200, 3250 | INFOMSG, FKEY05 |
| REQ-CTTU-013 | F5 save = UPDATE then INSERT on +100 → `Changes committed to database` | 9600, 9700 | INFOMSG |
| REQ-CTTU-014 | Insert failure / duplicate → `Error inserting record into: TRANSACTION_TYPE Table. SQLCODE:` | 9700 | ERRMSG |
| REQ-CTTU-015 | Update lock → `Could not lock record for update` | 9600 (-911) | ERRMSG |
| REQ-CTTU-016 | Update other failure → `Error updating: TRANSACTION_TYPE Table. SQLCODE:` | 9600 (<0) | ERRMSG |
| REQ-CTTU-017 | F4 from show → `Delete this record ? Press F4 to confirm` | 0000, 3250 | FKEY04, INFOMSG |
| REQ-CTTU-018 | F4 confirm → delete → `Delete successful.` | 9800 (0) | INFOMSG |
| REQ-CTTU-019 | Delete with children → `Please delete associated child records first:` | 9800 (-532) | ERRMSG |
| REQ-CTTU-020 | Delete other failure → `Delete failed with message:` | 9800 (OTHER) | ERRMSG |
| REQ-CTTU-021 | F12 in confirm-delete → `Delete was cancelled` | 2000 | ERRMSG, FKEY12 |
| REQ-CTTU-022 | F12 in confirm-save → `Update was cancelled`, original values | 2000, 3202 | ERRMSG, FKEY12 |
| REQ-CTTU-023 | F12 in show/create/not-found → initial state | 0000 | FKEY12 |
| REQ-CTTU-024 | Invalid key → `Invalid key pressed` | 0001 | ERRMSG |
| REQ-CTTU-025 | PF-key labels shown only when valid | 3391 | FKEY04/05/12 |
| REQ-CTTU-026 | F3 → admin menu | 0000 PF3 | FKEYS |
| REQ-CTTU-027 | Key protected while editing description | 3300/3320 | TRTYPCD |
