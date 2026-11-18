# Requirements — CTLI / CTTU modernisation

Derived from `docs/discovery-ctli-cttu.md`. Every observable behaviour of COTRTLIC (CTLI) and COTRTUPC (CTTU) has one requirement. Message texts are verbatim COBOL literals (trailing blanks trimmed). "Source" cites paragraph (lines) and BMS field.

## 1. Functional requirements — CTLI (list / inline update / delete)

| ID | Requirement | Source | BMS field |
|---|---|---|---|
| REQ-CTLI-001 | Initial entry shows page 1: up to 7 rows ordered by type code ascending, page number 1, info message 'Type U to update, D to delete any record'. | 0000-MAIN (REENTER branch), 8000-READ-FORWARD, 2500 (1544/1552) | TRTTYP1-7, TRTYPD1-7, PAGENO, INFOMSG |
| REQ-CTLI-002 | A page holds at most 7 rows (WS-MAX-SCREEN-LINES). | WS-MAX-SCREEN-LINES, 8000 | rows 12-18 |
| REQ-CTLI-003 | Forward paging (F8) when a next page exists starts at the key found by the lookahead read (`TR_TYPE >= key`) and increments page number. | 0000-MAIN F8 branch, 8000 lookahead | PAGENO |
| REQ-CTLI-004 | Next-page existence is determined by reading one row beyond the 7th (lookahead). | 8000 (1660-1690) | — |
| REQ-CTLI-005 | F8 when no next page: rows unchanged, ERRMSG 'No more pages to display' (or info 'Type U to update, D to delete any record' first time the last page is reached); reading past the end gives 'No more pages for these search conditions'. | 2500 (1539-1544), 8000 (1679) | ERRMSG, INFOMSG |
| REQ-CTLI-006 | Backward paging (F7) reads `TR_TYPE < first key ORDER BY TR_TYPE DESC`, shows the rows in ascending order, decrements page number, and a next page exists afterwards. | 0000-MAIN F7 branch, 8100-READ-BACKWARDS | PAGENO |
| REQ-CTLI-007 | F7 on page 1: page 1 is re-shown with ERRMSG 'No previous pages to display'. | 0000-MAIN, 2500 (1534) | ERRMSG |
| REQ-CTLI-008 | Empty result on page 1: ERRMSG 'No records found for this search condition.'. | 8000 (1704) | ERRMSG |
| REQ-CTLI-009 | Type filter: blank or zero = no filter; numeric = exact match on 2-digit code; otherwise ERRMSG 'TYPE CODE FILTER,IF SUPPLIED MUST BE A 2 DIGIT NUMBER' and no rows editable. | 1220-EDIT-TYPECD | TRTYPE |
| REQ-CTLI-010 | Description filter: rows whose description contains the trimmed text (`LIKE '%x%'`, case-sensitive). | 1230-EDIT-DESC, cursor SQL | TRDESC |
| REQ-CTLI-011 | Filters that match nothing: ERRMSG 'No Records found for these filter conditions'. | 1290-CROSS-EDITS, 9100-CHECK-FILTERS | ERRMSG |
| REQ-CTLI-012 | Changing a filter restarts paging at page 1. | 1220 (1134), 1230 (1172) | PAGENO |
| REQ-CTLI-013 | Row action codes: 'U' update, 'D' delete; any other non-blank → ERRMSG 'Action code selected is invalid'. | 1210-EDIT-ARRAY (1038) | TRTSEL1-7 |
| REQ-CTLI-014 | Only one row action at a time; more → ERRMSG 'Please select only 1 action'. | 1210 (1051) | TRTSEL1-7 |
| REQ-CTLI-015 | Selecting U (ENTER) makes only that row's description editable and shows info 'Update HIGHLIGHTED row. Press F10 to save'. | 2200, 2500 (1530) | TRTYPDn, INFOMSG |
| REQ-CTLI-016 | Inline description edit: blank → 'Transaction Desc must be supplied.'; non letters/digits/spaces → 'Transaction Desc can have numbers or alphabets only.'; unchanged (trimmed, case-insensitive) → 'No change detected with respect to database values.'. | 1211, 1240 | TRTYPDn, ERRMSG |
| REQ-CTLI-017 | F10 on valid update: `UPDATE ... SET TR_DESCRIPTION WHERE TR_TYPE`, info 'HIGHLIGHTED row was updated', current page re-read. | 9200-UPDATE-RECORD (0) | INFOMSG |
| REQ-CTLI-018 | Update SQLCODE +100 → 'Record not found. Deleted by others ? '; −911 → 'Deadlock. Someone else updating ?'; other negative → 'Update failed with' + SQL text. | 9200 | ERRMSG |
| REQ-CTLI-019 | Selecting D (ENTER) shows info 'Delete HIGHLIGHTED row ? Press F10 to confirm'. | 2500 (1521) | INFOMSG |
| REQ-CTLI-020 | F10 on delete: row deleted, info 'HIGHLIGHTED row deleted.Hit Enter to continue', list restarts at page 1. | 9300-DELETE-RECORD (0), 0000-MAIN | INFOMSG |
| REQ-CTLI-021 | Delete of a type with category children (SQLCODE −532) is rejected: 'Please delete associated child records first:' + SQL text; row kept. | 9300 (−532) | ERRMSG |
| REQ-CTLI-022 | Other delete failure → 'Delete failed with message:' + SQL text. | 9300 | ERRMSG |
| REQ-CTLI-023 | F10 is only honoured while an update/delete confirmation is pending; otherwise treated as ENTER. | 0000-MAIN (574-587, 666-678) | — |
| REQ-CTLI-024 | F2 transfers to CTTU (add/edit screen) in fresh state. | 0000-MAIN (630) | BUTNF02 |
| REQ-CTLI-025 | F3 exits to the admin menu CA00/COADM01C (or calling program). | 0000-MAIN (591) | BUTNF03 |
| REQ-CTLI-026 | Messages: info line ≤45 chars centred, error line ≤78 chars, error shown in red. | 2500, BMS | INFOMSG, ERRMSG |
| REQ-CTLI-027 | Only administrators can use the function (reached from admin menu only). | COADM01C menu, CDEMO-USRTYP-ADMIN | — |
| REQ-CTLI-028 | Database-unavailable condition reported ('Db2 access failure. '). | 9998-PRIMING-QUERY | — |

## 2. Functional requirements — CTTU (add / edit / delete)

| ID | Requirement | Source | BMS field |
|---|---|---|---|
| REQ-CTTU-001 | Entry shows empty form, info 'Enter transaction type to be maintained', cursor on type code. | 3250, 3201 | TRTYPCD, INFOMSG |
| REQ-CTTU-002 | Blank key on ENTER → 'No input received'. | 1200 | ERRMSG |
| REQ-CTTU-003 | Type code edits: required ('Tran Type code must be supplied.'), numeric ('Tran Type code must be numeric.'), non-zero ('Tran Type code must not be zero.'); valid value zero-padded to 2 digits. | 1210, 1245 | TRTYPCD |
| REQ-CTTU-004 | Existing key: details shown, info 'Update transaction type details shown.', key protected, description editable, F4/F12 offered. | 9100 (0), 3250, 3300, 3391 | TRTYDSC, FKEY04, FKEY12 |
| REQ-CTTU-005 | Non-existent key: ERRMSG 'No record found for this key in database', info 'Press F05 to add. F12 to cancel'. | 9100 (+100), 3250 | ERRMSG, INFOMSG, FKEY05 |
| REQ-CTTU-006 | F5 in not-found state enters create mode, info 'Enter new transaction type details.'. | 2000 (1053), 3250 | TRTYDSC |
| REQ-CTTU-007 | Description edits: required ('Transaction Desc must be supplied.'), letters/digits/spaces only ('Transaction Desc can have numbers or alphabets only.'), max 50. | 1230 | TRTYDSC |
| REQ-CTTU-008 | Unchanged description on ENTER → 'No change detected with respect to values fetched.'. | 1205 | ERRMSG |
| REQ-CTTU-009 | Valid change on ENTER → info 'Changes validated.Press F5 to save' (confirm step, fields protected). | 2000 (1029), 3250 | INFOMSG, FKEY05 |
| REQ-CTTU-010 | F5 confirm: UPDATE description; if row absent (+100) INSERT (upsert); success info 'Changes committed to database'. | 9600, 9700 | INFOMSG |
| REQ-CTTU-011 | Write failures: −911 → 'Could not lock record for update'; update <0 → 'Error updating: TRANSACTION_TYPE Table. SQLCODE:…'; insert failure (incl. duplicate −803) → 'Error inserting record into: TRANSACTION_TYPE Table. SQLCODE:…'; info 'Changes unsuccessful'. | 9600, 9700 | ERRMSG, INFOMSG |
| REQ-CTTU-012 | F4 on shown record → info 'Delete this record ? Press F4 to confirm'; second F4 deletes; info 'Delete successful.'. | 0000-MAIN, 9800 (0) | FKEY04, INFOMSG |
| REQ-CTTU-013 | Delete with children (−532) → 'Please delete associated child records first:SQLCODE :…'; other failure → 'Delete failed with message:SQLCODE :…'. | 9800 | ERRMSG |
| REQ-CTTU-014 | F12 cancels: during confirm-save → 'Update was cancelled' (original values restored); during confirm-delete → 'Delete was cancelled'; otherwise returns to key entry. | 2000 (988-1010) | ERRMSG |
| REQ-CTTU-015 | Keys not valid for the current state → 'Invalid key pressed'. | 0001-CHECK-PFKEYS | ERRMSG |
| REQ-CTTU-016 | F3 returns to caller (CTLI if launched from list, else admin menu CA00/COADM01C). | 0000-MAIN F3 | FKEYS |
| REQ-CTTU-017 | Read failure → 'Error accessing: TRANSACTION_TYPE table. SQLCODE:…'. | 9100 (<0) | ERRMSG |
| REQ-CTTU-018 | Only administrators can use the function. | admin menu | — |

## 3. Edge cases

| Case | Requirement(s) | Expected |
|---|---|---|
| Empty table | CTLI-008 | No rows, 'No records found for this search condition.' |
| First-page boundary | CTLI-007 | 'No previous pages to display' |
| Last-page boundary | CTLI-004/005 | hasNext=false, 'No more pages to display' |
| Delete with children | CTLI-021, CTTU-013 | 409, 'Please delete associated child records first:' |
| Duplicate key on add | CTTU-011 | 409, 'Error inserting record into: TRANSACTION_TYPE Table. SQLCODE:-803' |
| Blank inputs | CTLI-016, CTTU-002/003/007 | 400 with the 'must be supplied.' / 'No input received' text |
| Invalid inputs | CTLI-009/013/016, CTTU-003/007 | 400 with source text |
| Non-admin | CTLI-027, CTTU-018 | 401 unauthenticated / 403 non-admin |

## 4. Non-functional requirements

| ID | Requirement |
|---|---|
| NFR-001 | REST/JSON API under `/api/transaction-types`; resource-oriented verbs (GET/POST/PUT/PATCH/DELETE). |
| NFR-002 | HTTP status: 200/201/204 success, 400 validation, 401/403 auth, 404 not found, 409 duplicate or FK-restricted delete, 500 other; body `{"code","message"}` carrying the source literal. |
| NFR-003 | Stateless keyset pagination (cursor + direction) reproducing the 7-row page, lookahead `hasNext`, `hasPrev`, page number; default size 7. |
| NFR-004 | Admin-only authorisation (role ADMIN) on every endpoint; JWT bearer tokens. |
| NFR-005 | Concurrency: last-writer-wins like the source (no optimistic lock; source compares only against values read in the same pseudo-conversation); DB unique/FK constraints are the arbiter; lock/serialisation failures map to the −911 messages (409). |
| NFR-006 | Single-row operations complete in <500 ms on the reference dataset; page queries use the primary-key index. |
| NFR-007 | PostgreSQL 15 schema managed by Flyway; CHAR(2)/CHAR(4) keys preserved. |
| NFR-008 | UI is a React SPA reproducing both screens, PF keys as buttons, info/error lines. |

## 5. Requirements traceability matrix (completed in Phase 6)

| Requirement | Design (target-spec) | Implementation | Test(s) | Result |
|---|---|---|---|---|
| See `docs/test-plan.md` §3 (completed) | | | | |
