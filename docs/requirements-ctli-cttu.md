# Requirements — Transaction Type Maintenance (CTLI / CTTU modernisation)

Derived from `docs/discovery-ctli-cttu.md`. Every functional requirement cites the authoritative COBOL paragraph and/or BMS field. Message literals are verbatim from source (quotes delimit, they are not part of the text). The acceptance criterion is functional equivalence to `COTRTLIC.cbl` / `COTRTUPC.cbl`; intentional differences are listed in `docs/deviations.md`.

Conventions: **API** = `carddemo-trantype-api`; **UI** = `carddemo-trantype-ui`; "list page" = CTLI equivalent (`TransactionTypeListPage`); "maintenance page" = CTTU equivalent (`TransactionTypeEditPage`).

## 1. CTLI — Transaction Type List / Update / Delete

| ID | Requirement | Source |
|---|---|---|
| REQ-CTLI-001 | On entry the list shows the first page: at most **7** rows of `TR_TYPE`, `TR_DESCRIPTION` ordered ascending by `TR_TYPE`, starting at the lowest key. Page number = 1. | 0000-MAIN (reset, `MOVE LOW-VALUES TO WS-START-KEY`), 8000-READ-FORWARD, `WS-MAX-SCREEN-LINES VALUE 7`, TRTTYP1-7/TRTYPD1-7 |
| REQ-CTLI-002 | After filling 7 rows one extra row is read (look-ahead) to decide whether a next page exists; the look-ahead row is not displayed. | 8000-READ-FORWARD look-ahead FETCH, WS-CA-NEXT-PAGE-IND |
| REQ-CTLI-003 | Page forward (F8) when a next page exists shows the 7 rows starting at the look-ahead key (`TR_TYPE >= key`), page number + 1. | 0000-MAIN `WHEN CCARD-AID-PFK08 AND CA-NEXT-PAGE-EXISTS`, cursor C-TR-TYPE-FORWARD |
| REQ-CTLI-004 | Page backward (F7) when not on page 1 shows the 7 rows with `TR_TYPE < first key` read descending and displayed ascending; page number − 1. | 8100-READ-BACKWARDS, cursor C-TR-TYPE-BACKWARD |
| REQ-CTLI-005 | F7 on page 1 re-displays page 1 with error line 'No previous pages to display'. | 0000-MAIN `WHEN CCARD-AID-PFK07 AND CA-FIRST-PAGE`, 2500-SETUP-MESSAGE |
| REQ-CTLI-006 | F8 when the displayed page is the last one: the first F8 re-displays the page with error 'No more pages for these search conditions' (look-ahead FETCH +100 with PF8) and marks the last page as shown; arriving on the last page via F8 does the same; any further F8 shows 'No more pages to display'. | 8000-READ-FORWARD +100 branch, 2500-SETUP-MESSAGE (`CA-LAST-PAGE-SHOWN`) |
| REQ-CTLI-007 | The current page number is displayed (`Page` label, PAGENO). | 2100-SCREEN-INIT, PAGENO |
| REQ-CTLI-008 | When no rows exist for the (unfiltered) first page the error line shows 'No records found for this search condition.' and no rows are shown. | 8000-READ-FORWARD (`IF WS-CA-SCREEN-NUM = 1 AND WS-SCRN-COUNTER = 0`) |
| REQ-CTLI-009 | Type filter (2 characters): blank or `00` = no filter; otherwise both characters must be digits, else error 'TYPE CODE FILTER,IF SUPPLIED MUST BE A 2 DIGIT NUMBER' (row selection disabled, filter highlighted). A valid filter matches `TR_TYPE` exactly. | 1220-EDIT-TYPECD, TRTYPE (6,44 len 2) |
| REQ-CTLI-010 | Description filter (≤50 characters): blank = no filter; otherwise rows whose description contains the trimmed input (`LIKE '%x%'`, case-sensitive). | 1230-EDIT-DESC, TRDESC (8,25 len 50), cursor predicates |
| REQ-CTLI-011 | If a valid filter is supplied and no rows match, error 'No Records found for these filter conditions', no rows shown, row selection disabled. | 1290-CROSS-EDITS, 9100-CHECK-FILTERS |
| REQ-CTLI-012 | Changing either filter value restarts paging at page 1 and discards any pending row selection. | 1220-EDIT-TYPECD-EXIT, 1230-EDIT-DESC-EXIT, 1210-EDIT-ARRAY |
| REQ-CTLI-013 | Each displayed row has a 1-character action field accepting `U` (update) or `D` (delete) only; any other non-blank value → 'Action code selected is invalid' (field highlighted). Empty rows have no action field. | 1210-EDIT-ARRAY, 2200-SETUP-ARRAY-ATTRIBS, TRTSEL1-7 |
| REQ-CTLI-014 | Only one row action may be entered at a time; more than one → 'Please select only 1 action'. | 1210-EDIT-ARRAY (`WHEN WS-ACTIONS-REQUESTED > 1`) |
| REQ-CTLI-015 | `D` on a row (ENTER) highlights the row and shows info 'Delete HIGHLIGHTED row ? Press F10 to confirm'; filters become read-only; nothing is deleted until F10 (Save) is pressed. | 2500-SETUP-MESSAGE, 2400-SETUP-SCREEN-ATTRS, BUTNF10 |
| REQ-CTLI-016 | F10 with a pending delete deletes the row; on success info 'HIGHLIGHTED row deleted.Hit Enter to continue' and the list restarts at page 1. | 0000-MAIN F10 branch, 9300-DELETE-RECORD SQLCODE 0 |
| REQ-CTLI-017 | Delete of a type that has category rows (FK RESTRICT, SQLCODE −532) fails with error 'Please delete associated child records first:' followed by the SQLCODE (`SQLCODE: -532`). Any other delete failure → 'Delete failed with message:' + SQLCODE. | 9300-DELETE-RECORD, 9999-FORMAT-DB2-MESSAGE |
| REQ-CTLI-018 | `U` on a row (ENTER) makes only that row's description editable and shows info 'Update HIGHLIGHTED row. Press F10 to save'. | 2200-SETUP-ARRAY-ATTRIBS, 2300-SCREEN-ARRAY-INIT, TRTYPD1-7 |
| REQ-CTLI-019 | The edited description must be: non-blank ('Transaction Desc must be supplied.'), only letters/digits/spaces ('Transaction Desc can have numbers or alphabets only.'), and different from the displayed value ignoring case and surrounding spaces ('No change detected with respect to database values.'). Max length 50. | 1211-EDIT-ARRAY-DESC, 1240-EDIT-ALPHANUM-REQD, TRTYPD (len 50) |
| REQ-CTLI-020 | F10 with a valid pending update updates `TR_DESCRIPTION` (no insert) and shows 'HIGHLIGHTED row was updated'. Row not found (+100) → 'Record not found. Deleted by others ? ' + SQLCODE; lock timeout/deadlock (−911) → 'Deadlock. Someone else updating ?' + SQLCODE; other → 'Update failed with' + SQLCODE. | 9200-UPDATE-RECORD |
| REQ-CTLI-021 | F2 (Add) navigates to the CTTU maintenance screen in its initial (search) state. | 0000-MAIN `WHEN CCARD-AID-PFK02`, BUTNF02 |
| REQ-CTLI-022 | F3 (Exit) returns to the admin menu (CA00 / COADM01C). | 0000-MAIN `WHEN CCARD-AID-PFK03`, BUTNF03 |
| REQ-CTLI-023 | When rows are shown and no other message applies the info line shows 'Type U to update, D to delete any record'. | 2500-SETUP-MESSAGE |
| REQ-CTLI-024 | Keys other than ENTER/F2/F3/F7/F8/F10 behave as ENTER (refresh current page); F10 without a pending action behaves as ENTER. | 0000-MAIN PFK-INVALID handling |
| REQ-CTLI-025 | If the database is unreachable the user sees 'Db2 access failure. ' + SQLCODE text. | 9998-PRIMING-QUERY |

## 2. CTTU — Transaction Type Maintenance (search / add / edit / delete)

| ID | Requirement | Source |
|---|---|---|
| REQ-CTTU-001 | Initial state: blank type code (editable, focused) and protected description; info 'Enter transaction type to be maintained'; keys ENTER and F3 offered. | 0000-MAIN PGM-ENTER branch, 3250-SETUP-INFOMSG, 3391, TRTYPCD (IC) |
| REQ-CTTU-002 | Type code edit: blank → 'Tran Type code must be supplied.'; non-numeric → 'Tran Type code must be numeric.'; zero → 'Tran Type code must not be zero.'; valid value is zero-padded to 2 digits (e.g. `5` → `05`). | 1210-EDIT-TRANTYPE, 1245-EDIT-NUM-REQD, TRTYPCD (len 2) |
| REQ-CTTU-003 | ENTER with a valid existing key fetches and shows the description, makes the description editable, protects the key; keys ENTER, F3, F4, F12 offered; info 'Enter transaction type to be maintained'. | 2000-DECIDE-ACTION, 9100-GET-TRANSACTION-TYPE, 3202, 3320, 3250 (`WHEN TTUP-SHOW-DETAILS` shares PROMPT-FOR-SEARCH-KEYS) |
| REQ-CTTU-004 | ENTER with a valid key not in the table → error 'No record found for this key in database', info 'Press F05 to add. F12 to cancel', keys ENTER, F3, F5, F12. | 9100 SQLCODE +100, 3250, 3391 |
| REQ-CTTU-005 | F5 in the not-found state starts creation: key protected, description editable and blank, info 'Enter new transaction type details.', keys ENTER, F3, F12. | 0000-MAIN `WHEN CCARD-AID-PFK05 AND TTUP-DETAILS-NOT-FOUND`, 3250, 3391 |
| REQ-CTTU-006 | Description edit: blank → 'Transaction Desc must be supplied.'; characters other than letters, digits, spaces → 'Transaction Desc can have numbers or alphabets only.'; max 50. While invalid the screen stays editable with info 'Update transaction type details shown.'. | 1230-EDIT-ALPHANUM-REQD, 3250 (CHANGES-NOT-OK), TRTYDSC (len 50) |
| REQ-CTTU-007 | ENTER with a description equal (ignoring case/surrounding spaces) to the fetched one → 'No change detected with respect to values fetched.' and the state is unchanged. | 1205-COMPARE-OLD-NEW |
| REQ-CTTU-008 | ENTER with a valid changed description → all fields protected, info 'Changes validated.Press F5 to save', keys ENTER, F3, F5, F12. | 1200-EDIT-MAP-INPUTS, 3250, 3391 |
| REQ-CTTU-009 | F5 after validation saves with upsert semantics: UPDATE the description; if the row no longer exists (+100) INSERT it. Success → info 'Changes committed to database'; the next interaction returns to the initial state. | 9600-WRITE-PROCESSING, 9700-INSERT-RECORD, 0000-MAIN reset EVALUATE |
| REQ-CTTU-010 | Insert failure (e.g. duplicate key −803 on a concurrent insert) → error 'Error inserting record into: TRANSACTION_TYPE Table. SQLCODE:' + SQLCODE, info 'Changes unsuccessful'. Update failure → 'Error updating: TRANSACTION_TYPE Table. SQLCODE:' + SQLCODE. | 9700-INSERT-RECORD, 9600-WRITE-PROCESSING |
| REQ-CTTU-011 | Save hitting a lock conflict (−911) → error 'Could not lock record for update', info 'Changes unsuccessful'. | 9600-WRITE-PROCESSING |
| REQ-CTTU-012 | F4 while details are shown → info 'Delete this record ? Press F4 to confirm', ENTER hidden, keys F3, F4, F12. F4 again deletes; success → info 'Delete successful.'. | 0000-MAIN PF4 branches, 9800-DELETE-PROCESSING, 3250, 3391 |
| REQ-CTTU-013 | Delete with child categories (−532) → error 'Please delete associated child records first:SQLCODE :' + SQLCODE + ':' (state START-DELETE; ENTER/F5 then re-edit with a blank description because the protected field is not transmitted); other failure → 'Delete failed with message:SQLCODE :' + SQLCODE + ':', info 'Changes unsuccessful'. | 9800-DELETE-PROCESSING, 1150-STORE-MAP-IN-NEW |
| REQ-CTTU-014 | F12 (Cancel): from validated changes → error 'Update was cancelled', original values redisplayed with the description editable (CHANGES-BACKED-OUT, info 'Update transaction type details shown.'); from delete-confirm → error 'Delete was cancelled' and the blank search screen; from show-details, not-found or create-new → the blank search screen. | 0000-MAIN reset EVALUATE and `WHEN CCARD-AID-PFK12` branch, 2000-DECIDE-ACTION |
| REQ-CTTU-015 | A key not valid for the current state → error 'Invalid key pressed', screen unchanged. | 0001-CHECK-PFKEYS |
| REQ-CTTU-016 | F3 returns to the caller: the list (CTLI) when entered from it via F2, otherwise the admin menu (CA00/COADM01C). | 0000-MAIN `WHEN CCARD-AID-PFK03` |
| REQ-CTTU-017 | Function-key labels shown per state as in REQ-CTTU-001/003/004/005/008/012 (F4 only in show/confirm-delete; F5 only in not-found/validated; F12 in validated/show/not-found/confirm-delete/create-new; ENTER hidden in confirm-delete; F6 never). | 3391-SETUP-PFKEY-ATTRS, FKEYS/FKEY04/FKEY05/FKEY06/FKEY12 |
| REQ-CTTU-018 | Type code editable only in initial / not-found states; description editable only in show-details / create-new / changes-not-ok states. | 3300-SETUP-SCREEN-ATTRS, 3310, 3320 |

## 3. Navigation / security

| ID | Requirement | Source |
|---|---|---|
| REQ-NAV-001 | The admin menu offers option 5 'Transaction Type List/Update (Db2)' → list page and option 6 'Transaction Type Maintenance (Db2)' → maintenance page. | `app/cpy/COADM02Y.cpy`, COADM01C |
| REQ-SEC-001 | Only administrators (CardDemo user type `A`) may use the functions; non-admin requests are rejected (HTTP 403) and unauthenticated requests are rejected (HTTP 401). | Sign-on routing to COADM01C (admin), `SET CDEMO-USRTYP-ADMIN` in both programs |

## 4. Edge cases

| ID | Edge case | Expected (from source) | Covers |
|---|---|---|---|
| REQ-EDGE-001 | Empty table | 0 rows, 'No records found for this search condition.', page 1, no next/prev | REQ-CTLI-008 |
| REQ-EDGE-002 | First page boundary | F7 on page 1 → 'No previous pages to display' | REQ-CTLI-005 |
| REQ-EDGE-003 | Last page boundary (exactly 7, and <7 rows) | exactly 7 rows → look-ahead +100, hasNext false; F8 → 'No more pages to display' | REQ-CTLI-002, -006 |
| REQ-EDGE-004 | Delete with children | −532 → 'Please delete associated child records first:' (HTTP 409); row retained | REQ-CTLI-017, REQ-CTTU-013 |
| REQ-EDGE-005 | Duplicate key on add | INSERT −803 → 'Error inserting record into: TRANSACTION_TYPE Table. SQLCODE:' (HTTP 409) | REQ-CTTU-010 |
| REQ-EDGE-006 | Blank inputs | 'Tran Type code must be supplied.' / 'Transaction Desc must be supplied.' (HTTP 400) | REQ-CTTU-002, -006, REQ-CTLI-019 |
| REQ-EDGE-007 | Invalid inputs | 'Tran Type code must be numeric.', 'Tran Type code must not be zero.', 'Transaction Desc can have numbers or alphabets only.', 'TYPE CODE FILTER,IF SUPPLIED MUST BE A 2 DIGIT NUMBER' (HTTP 400) | REQ-CTTU-002, -006, REQ-CTLI-009, -019 |
| REQ-EDGE-008 | Non-admin user | HTTP 403; UI shows access denied and does not render the pages | REQ-SEC-001 |
| REQ-EDGE-009 | Non-existent key | 'No record found for this key in database' (HTTP 404) | REQ-CTTU-004 |
| REQ-EDGE-010 | Description longer than 50 | rejected (HTML maxLength 50; API 400 'Transaction Desc can have numbers or alphabets only.') | REQ-CTTU-006 |

## 5. Non-functional requirements

| ID | Requirement |
|---|---|
| REQ-NFR-001 | **REST contract**: resource `/api/transaction-types`, JSON bodies, camelCase DTOs, OpenAPI 3 contract in `docs/target-spec.md`. Path key `{trType}` is the 1–2 digit type code (normalised to 2 digits). |
| REQ-NFR-002 | **HTTP status semantics**: 200 read/update, 201 created, 204 deleted, 400 validation (source edit literal), 401 unauthenticated, 403 non-admin, 404 not found, 409 duplicate key / FK-restrict / concurrent conflict, 500 other. Error body `{code, message, sqlcode?}`. |
| REQ-NFR-003 | **Keyset pagination**: fixed default page size 7 (max 7 accepted to keep page-at-a-time UX), stateless `cursor` + `direction` parameters reproducing the COMMAREA first/last keys; forward `tr_type >= cursor ORDER BY tr_type LIMIT size+1`; backward `tr_type < cursor ORDER BY tr_type DESC LIMIT size` reversed. No OFFSET paging. |
| REQ-NFR-004 | **Authentication / authorisation**: Spring Security, server-side session (JSESSIONID) after `POST /api/auth/login`; role `ADMIN` required for all `/api/transaction-types/**`. |
| REQ-NFR-005 | **Concurrency**: each request is one DB transaction (CICS SYNCPOINT equivalent). No pessimistic locks are held between requests (pseudo-conversational equivalence). Lock timeouts/serialisation failures map to the −911 messages (HTTP 409). Lost updates follow source (last writer wins; source has no version check). |
| REQ-NFR-006 | **Response**: p95 < 500 ms for list/get against the 7-row seed data set on a developer workstation; list query uses the primary-key index. |
| REQ-NFR-007 | **Data**: PostgreSQL 15 managed by Flyway (`db/migration`), CHAR(2)/CHAR(4) codes preserved, FK `ON DELETE RESTRICT`. |
| REQ-NFR-008 | **Build/test**: `mvn verify` (unit + Testcontainers integration) and `npm test` / `npm run build` (Vitest + tsc) pass. |

## 6. Requirements Traceability Matrix (RTM)

Test-case IDs refer to `docs/test-plan.md`; status reflects the run recorded there (API 48 unit + 14 integration, UI 42 component tests, all passing).

| REQ ID | Source | API element | UI element | Test cases | Status |
|---|---|---|---|---|---|
| REQ-CTLI-001 | 8000-READ-FORWARD | `GET /api/transaction-types` (no cursor) | ListPage initial load | TC-001 | Pass |
| REQ-CTLI-002 | 8000 look-ahead | `hasNext`, `nextCursor` | Next button state | TC-002, TC-005 | Pass |
| REQ-CTLI-003 | PF8 branch | `direction=forward&cursor=nextCursor` | Next (F8) | TC-002 | Pass |
| REQ-CTLI-004 | 8100-READ-BACKWARDS | `direction=backward&cursor=firstKey` | Prev (F7) | TC-003 | Pass |
| REQ-CTLI-005 | 2500 | `hasPrev=false` | Prev on page 1 message | TC-004 | Pass |
| REQ-CTLI-006 | 2500 / 8000 | `hasNext=false` | Next on last page message | TC-005 | Pass |
| REQ-CTLI-007 | 2100 PAGENO | `pageNumber` | Page label | TC-001, TC-002, TC-003 | Pass |
| REQ-CTLI-008 | 8000 +100 | `rows=[]`, `message` | Error line | TC-006 | Pass |
| REQ-CTLI-009 | 1220-EDIT-TYPECD | `typeFilter` 400 | Type filter field | TC-007, TC-008 | Pass |
| REQ-CTLI-010 | 1230-EDIT-DESC | `descFilter` | Desc filter field | TC-009 | Pass |
| REQ-CTLI-011 | 1290 / 9100 | `rows=[]`, `message` | Error line | TC-010 | Pass |
| REQ-CTLI-012 | 1220/1230 exits | cursor omitted on filter change | filter change resets page | TC-011 | Pass |
| REQ-CTLI-013 | 1210-EDIT-ARRAY | — | Action select (U/D) | TC-012 | Pass |
| REQ-CTLI-014 | 1210-EDIT-ARRAY | — | single-action rule | TC-013 | Pass |
| REQ-CTLI-015 | 2500 | — | delete confirm (F10) | TC-014 | Pass |
| REQ-CTLI-016 | 9300 | `DELETE /{trType}` 204 | delete success | TC-015 | Pass |
| REQ-CTLI-017 | 9300 −532 | 409 `CHILD_RECORDS_EXIST` | error line | TC-016 | Pass |
| REQ-CTLI-018 | 2200/2300 | — | inline edit | TC-017 | Pass |
| REQ-CTLI-019 | 1211/1240 | 400 validation | inline validation | TC-018, TC-019 | Pass |
| REQ-CTLI-020 | 9200 | `PUT /{trType}?upsert=false` | update success / not found | TC-020, TC-021 | Pass |
| REQ-CTLI-021 | PF2 | — | Add (F2) → maintenance page | TC-022 | Pass |
| REQ-CTLI-022 | PF3 | — | Exit (F3) → admin menu | TC-023 | Pass |
| REQ-CTLI-023 | 2500 | — | default info line | TC-001 | Pass |
| REQ-CTLI-024 | PFK-INVALID | — | only defined buttons exist | TC-024 | Pass |
| REQ-CTLI-025 | 9998 | 500 `INTERNAL_ERROR` | error line | TC-025 | Pass |
| REQ-CTTU-001 | 3250/3391 | — | EditPage initial | TC-030 | Pass |
| REQ-CTTU-002 | 1210/1245 | 400 on `{trType}` | type validation | TC-031, TC-032 | Pass |
| REQ-CTTU-003 | 9100 | `GET /{trType}` 200 | show details | TC-033 | Pass |
| REQ-CTTU-004 | 9100 +100 | `GET /{trType}` 404 | not found state | TC-034 | Pass |
| REQ-CTTU-005 | PF5 not-found | — | create new state | TC-035 | Pass |
| REQ-CTTU-006 | 1230 | 400 | desc validation | TC-036 | Pass |
| REQ-CTTU-007 | 1205 | — | no change message | TC-037 | Pass |
| REQ-CTTU-008 | 1200/3250 | — | validated state | TC-038 | Pass |
| REQ-CTTU-009 | 9600/9700 | `PUT /{trType}` (upsert) 200/201; `POST` 201 | save F5 | TC-039, TC-040 | Pass |
| REQ-CTTU-010 | 9700 | 409 `DUPLICATE_KEY` | error line | TC-041 | Pass |
| REQ-CTTU-011 | 9600 −911 | 409 `LOCK_ERROR` | error line | TC-042 | Pass |
| REQ-CTTU-012 | 9800 | `DELETE /{trType}` 204 | delete confirm F4 | TC-043 | Pass |
| REQ-CTTU-013 | 9800 −532 | 409 `CHILD_RECORDS_EXIST` | error line | TC-044 | Pass |
| REQ-CTTU-014 | PF12 | — | Cancel (F12) | TC-045 | Pass |
| REQ-CTTU-015 | 0001 | — | buttons enabled per state | TC-046 | Pass |
| REQ-CTTU-016 | PF3 | — | Exit to caller | TC-047 | Pass |
| REQ-CTTU-017 | 3391 | — | button visibility | TC-046 | Pass |
| REQ-CTTU-018 | 3300 | — | field disabled/readOnly | TC-046 | Pass |
| REQ-NAV-001 | COADM02Y | — | AdminMenuPage options 5/6 | TC-050 | Pass |
| REQ-SEC-001 | sign-on | 401/403 | login / access denied | TC-051, TC-052 | Pass |
| REQ-NFR-001..005, -007, -008 | — | all | all | TC-060..TC-063 | Pass |
| REQ-NFR-006 | — | list/get | — | — | Not measured (no load test; single-row PK / 8-row keyset queries) |
