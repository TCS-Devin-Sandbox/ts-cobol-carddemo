# Phase 2 — Requirements and Requirements Traceability Matrix (RTM)

Derived from `discovery-ctli-cttu.md`. Source paragraphs anchor the original intent; requirements below describe the retained implementation, with differences recorded in `deviations.md`. "Shall" statements describe the modernized system (Spring Boot API `carddemo-trantype-api` + React UI `carddemo-trantype-ui`). Exact message strings are normative unless marked *(new)*.

Priority: **M** = must (source behaviour), **S** = should (UX parity), **N** = new (no source equivalent).

## 1. CTLI — Transaction Type list / update / delete (COTRTLIC)

| ID | Pri | Requirement | Source |
|---|---|---|---|
| REQ-CTLI-001 | M | On entry the list shall show up to 7 transaction types in ascending `tr_type` order, starting from the lowest key, page number 1. | 0000-MAIN (EIBCALEN=0), 8000-READ-FORWARD |
| REQ-CTLI-002 | M | Reading a page shall fetch one look-ahead row beyond the 7 displayed; if it exists the page reports "next page exists" and its key becomes the forward cursor. | 8000-READ-FORWARD (look-ahead FETCH) |
| REQ-CTLI-003 | M | Next page (F8) shall return rows with `tr_type >= cursor` (cursor = look-ahead key), ascending, max 7, and increment the page number. | 0000 WHEN PFK08, 8000 |
| REQ-CTLI-004 | M | Previous page (F7) shall return the 7 rows with `tr_type < firstKey` read in descending order and displayed ascending, and decrement the page number. | 0000 WHEN PFK07, 8100-READ-BACKWARDS |
| REQ-CTLI-005 | M | F7 on page 1 shall redisplay the current page with error `No previous pages to display`. | 0000 (WHEN PFK07 AND CA-FIRST-PAGE), 2500 |
| REQ-CTLI-006 | M | F8 when no next page exists shall redisplay the current page with error `No more pages to display`. | 2500-SETUP-MESSAGE |
| REQ-CTLI-007 | M | When no rows exist for page 1 (no filters) the error `No records found for this search condition.` shall be shown. | 8000 (+100, screen 1, 0 rows) |
| REQ-CTLI-008 | M | The current page label shall be displayed. API requests use `direction=FORWARD/BACKWARD` and `page` (default 1); the service returns 1 without earlier rows, otherwise max(page,2). It is not derived from row counts. | 2100-SCREEN-INIT (PAGENO) |
| REQ-CTLI-009 | M | Type filter: blank or numeric zero = no filter; otherwise trimmed 1–2 digits with optional leading plus are normalized to two digits and matched exactly. | 1220-EDIT-TYPECD, C-TR-TYPE-FORWARD |
| REQ-CTLI-010 | M | Invalid type filter shall show `TYPE CODE FILTER,IF SUPPLIED MUST BE A 2 DIGIT NUMBER`; Enter makes no request and leaves existing rows/selections in place. | 1220-EDIT-TYPECD |
| REQ-CTLI-011 | M | Description filter: blank = no filter; otherwise rows where `tr_description LIKE '%' ‖ trim(filter) ‖ '%'` (case-sensitive). | 1230-EDIT-DESC |
| REQ-CTLI-012 | M | If a supplied filter combination matches zero rows, error `No Records found for these filter conditions` shall be shown. | 1290-CROSS-EDITS, 9100-CHECK-FILTERS |
| REQ-CTLI-013 | M | Enter after changing either filter shall reset paging to page 1 and clear selections on a successful reload. | 1220/1230 exit ¶, 1210-EDIT-ARRAY |
| REQ-CTLI-014 | M | Row action codes are `U` (update) and `D` (delete); any other non-blank value shall produce `Action code selected is invalid`. | 1210-EDIT-ARRAY |
| REQ-CTLI-015 | M | Only one row action may be selected; more than one shall produce `Please select only 1 action`. | 1210-EDIT-ARRAY |
| REQ-CTLI-016 | M | Selecting `D` and pressing Enter shall highlight the row and show info `Delete HIGHLIGHTED row ? Press F10 to confirm`. | 0000, 2200, 2300, 2500 |
| REQ-CTLI-017 | M | F10 with a pending delete shall delete the row and show info `HIGHLIGHTED row deleted.Hit Enter to continue`; the list then restarts at page 1. | 9300-DELETE-RECORD, 0000 |
| REQ-CTLI-018 | M | Deleting a type that has category children shall be rejected with error starting `Please delete associated child records first:`. | 9300 (SQLCODE -532) |
| REQ-CTLI-019 | M | Missing-row delete shall show `Delete failed with message:SQLCODE :+100`; generic database failures use `Error accessing: TRANSACTION_TYPE table. SQLCODE: SQLSTATE <state>`. | 9300 (OTHER) |
| REQ-CTLI-020 | M | Selecting U shall enable that row's description immediately; Enter stages/highlights the row and shows `Update HIGHLIGHTED row. Press F10 to save`. | 2200, 2300, 2500 |
| REQ-CTLI-021 | M | An updated description shall be required (`Transaction Desc must be supplied.`), contain only letters, digits and spaces (`Transaction Desc can have numbers or alphabets only.`), max 50 characters. | 1211-EDIT-ARRAY-DESC, 1240-EDIT-ALPHANUM-REQD, BMS TRTYPDn LENGTH=50 |
| REQ-CTLI-022 | M | A description equal to the stored one (case-insensitive, trimmed) shall show `No change detected with respect to database values.` | 1211-EDIT-ARRAY-DESC |
| REQ-CTLI-023 | M | F10 with a pending valid update shall update the description and show info `HIGHLIGHTED row was updated`; the current page is re-read. | 9200-UPDATE-RECORD, 0000 |
| REQ-CTLI-024 | M | Updating a row that no longer exists shall show error starting `Record not found. Deleted by others ?`. | 9200 (+100) |
| REQ-CTLI-025 | M | A lock/deadlock on update shall show error starting `Deadlock. Someone else updating ?`. | 9200 (-911) |
| REQ-CTLI-026 | M | Generic update database failures shall show `Error accessing: TRANSACTION_TYPE table. SQLCODE: SQLSTATE <state>` (null if unavailable). | 9200 (<0) |
| REQ-CTLI-027 | M | F10 with pending action shall execute it; without one, the keyboard shortcut behaves as Enter (the button is disabled). Action edits clear pending state; filter edits take effect on Enter, not automatically on F10. | 0000 (PF10 downgrade) |
| REQ-CTLI-028 | M | Add (F2) shall navigate to the CTTU add/edit page. | 0000 (PF2 XCTL COTRTUPC) |
| REQ-CTLI-029 | M | Exit (F3) shall return to the admin menu. | 0000 (PF3 XCTL COADM01C) |
| REQ-CTLI-030 | S | Unbound keys shall be ignored, not treated as Enter. Bound auto-repeat shall be suppressed; a single Enter on native interactive controls keeps its native behavior. | 0000 (PFK-INVALID) |
| REQ-CTLI-031 | M | When rows are shown and no other message applies, info `Type U to update, D to delete any record` shall be shown. | 2500-SETUP-MESSAGE |
| REQ-CTLI-032 | M | Database errors on read shall be reported in the error line. | 8000/8100/9400–9550, 9999-FORMAT-DB2-MESSAGE |
| REQ-CTLI-033 | S | The screen shall show transaction id, program name, titles, current date and time. | 2100-SCREEN-INIT |

## 2. CTTU — Transaction Type add / edit / delete (COTRTUPC)

| ID | Pri | Requirement | Source |
|---|---|---|---|
| REQ-CTTU-001 | M | On entry the page shall ask for a key with info `Enter transaction type to be maintained`; only the key is editable. | 0000, 3250, 3300 |
| REQ-CTTU-002 | M | Blank search key in the UI → `No input received`; blank API key → `Tran Type code must be supplied.`. | 1245-EDIT-NUM-REQD |
| REQ-CTTU-003 | M | Non-numeric key → `Tran Type code must be numeric.` | 1245-EDIT-NUM-REQD |
| REQ-CTTU-004 | M | Zero key → `Tran Type code must not be zero.` | 1245-EDIT-NUM-REQD |
| REQ-CTTU-005 | M | A valid key shall be zero-padded to 2 digits (`1` → `01`). | 1210-EDIT-TRANTYPE |
| REQ-CTTU-006 | M | A found key shall display the stored description, make only the description editable, and offer Delete (F4) and Cancel (F12). | 9100 (0), 2000, 3202, 3320, 3391 |
| REQ-CTTU-007 | M | A key not found shall show error `No record found for this key in database` and info `Press F05 to add. F12 to cancel`. | 9100 (+100), 3250 |
| REQ-CTTU-008 | M | A read DB error shall show error starting `Error accessing: TRANSACTION_TYPE table. SQLCODE:`. | 9100 (<0) |
| REQ-CTTU-009 | M | F5 in not-found state shall enter add mode with info `Enter new transaction type details.` | 0000, 2000 |
| REQ-CTTU-010 | M | The description shall be required (`Transaction Desc must be supplied.`), letters/digits/spaces only (`Transaction Desc can have numbers or alphabets only.`), max 50. | 1230-EDIT-ALPHANUM-REQD, BMS TRTYDSC LENGTH=50 |
| REQ-CTTU-011 | M | A description unchanged from the fetched value (case-insensitive) shall show `No change detected with respect to values fetched.` and not proceed. | 1205-COMPARE-OLD-NEW |
| REQ-CTTU-012 | M | Valid changes shall show info `Changes validated.Press F5 to save` with all fields read-only. | 1200, 3250, 3300 |
| REQ-CTTU-013 | M | F5 to save shall use PUT for both add and edit: insert when lookup finds no row (201), otherwise update (200); success shows `Changes committed to database`. There is no `upsert` query parameter. CTLI strict updates use PATCH. | 9600-WRITE-PROCESSING, 9700-INSERT-RECORD |
| REQ-CTTU-014 | M | An insert failure (incl. duplicate key) shall show error starting `Error inserting record into: TRANSACTION_TYPE Table. SQLCODE:`. | 9700 |
| REQ-CTTU-015 | M | A lock conflict on save shall show error `Could not lock record for update`. | 9600 (-911) |
| REQ-CTTU-016 | M | Generic save failures shall show `Error accessing: TRANSACTION_TYPE table. SQLCODE: SQLSTATE <state>`. | 9600 (<0) |
| REQ-CTTU-017 | M | F4 on a shown record shall ask `Delete this record ? Press F4 to confirm`; page-level Enter is unbound and its button hidden. A discrete Enter on a focused F4 button still activates that button natively. | 0000, 3250, 0001 |
| REQ-CTTU-018 | M | F4 again shall delete the record and show info `Delete successful.` | 9800 (0) |
| REQ-CTTU-019 | M | Delete of a type with category children shall be rejected with error starting `Please delete associated child records first:`. | 9800 (-532) |
| REQ-CTTU-020 | M | Missing-row delete shall show `Delete failed with message:SQLCODE :+100`; other DB failures use the generic DB_ERROR message. Both set info `Changes unsuccessful`. | 9800 (OTHER), 3250 |
| REQ-CTTU-021 | M | F12 during delete confirmation shall cancel with error `Delete was cancelled` and return to key entry. | 2000-DECIDE-ACTION |
| REQ-CTTU-022 | M | F12 during save confirmation shall show `Update was cancelled`, restoring original values in edit mode or returning to new-description entry in add mode. | 2000-DECIDE-ACTION, 3202 |
| REQ-CTTU-023 | M | F12 while showing a record, adding, or after not-found shall return to key entry. | 0000 (simulate initial entry) |
| REQ-CTTU-024 | M | Keys without a handler in the current state shall be ignored; no `Invalid key pressed` message is emitted. Mapped repeats are prevented without dispatch; repeated Enter on interactive controls is also prevented. | 0001-CHECK-PFKEYS |
| REQ-CTTU-025 | S | Action buttons (F4/F5/F12) shall be visible/enabled only in the states where they are valid. | 3391-SETUP-PFKEY-ATTRS |
| REQ-CTTU-026 | M | Exit (F3) shall return to `/transaction-types` if launched with `?from=list`, otherwise `/admin` (D-17). | 0000 (PF3), discovery Q10 |
| REQ-CTTU-027 | M | The key shall be read-only while a record is shown or being edited. | 3300, 3320 |
| REQ-CTTU-028 | M | Completed/failed save/delete states shall enable key entry immediately. Enter looks up the current key; typing a changed key resets to NOT_FETCHED. | 0000 (simulate initial entry) |

## 3. Edge cases

| ID | Pri | Requirement | Source |
|---|---|---|---|
| REQ-EDGE-001 | M | Empty table: page 1 has no rows, hasNext=false, hasPrev=false, message `No records found for this search condition.` | 8000 (+100) |
| REQ-EDGE-002 | M | Exactly 7 rows: one page, hasNext=false (look-ahead returns nothing). | 8000 look-ahead |
| REQ-EDGE-003 | M | 8 rows: page 1 hasNext=true; page 2 has 1 row, hasNext=false; prev from page 2 returns page 1 unchanged. | 8000, 8100 |
| REQ-EDGE-004 | M | BACKWARD with fewer than 7 earlier rows shall return those rows ascending, without a forward fallback. No earlier rows → empty response with `No more pages for these search conditions`; UI F7 at a known first-page boundary reports `No previous pages to display` without a request (D-07). | 8100 (Q4) |
| REQ-EDGE-005 | M | Delete of a parent with `transaction_type_category` rows is rejected (FK RESTRICT); the parent and children remain. | 9300/9800 -532, DDL |
| REQ-EDGE-006 | M | Delete of a parent without children succeeds. | 9300/9800 (0) |
| REQ-EDGE-007 | M | Insert of an existing key is rejected as duplicate. | 9700 (-803) |
| REQ-EDGE-008 | M | Blank/whitespace inputs are missing; literal `*` fails numeric/alphanumeric syntax instead of being treated as missing. UI blank search uses `No input received` (D-06/D-13). | 1100, 1150, 1240, 1245 |
| REQ-EDGE-009 | M | Descriptions with punctuation (e.g. `-`, `.`, `%`) are rejected. | 1240/1230 |
| REQ-EDGE-010 | M | A description longer than 50 characters is rejected *(new message `Transaction Desc must not exceed 50 characters.`; 3270 field truncates)*. | BMS LENGTH=50 |
| REQ-EDGE-011 | M | Key `1`, ` 1`, `01` all address `01`; `123`, `1A`, `-1` are invalid (`Tran Type code must be numeric.`). | 1245/1210 |
| REQ-EDGE-012 | M | Non-admin API calls shall return HTTP 403 and `Access restricted to administrators`; login UI rejects non-admin sessions with that message, and the route guard redirects missing/non-admin sessions to `/login`. | COADM01C reachability, COMEN01C |
| REQ-EDGE-013 | M | Unauthenticated calls are rejected (HTTP 401). | sign-on precondition (COSGN00C) |
| REQ-EDGE-014 | M | PATCH and DELETE of absent keys shall return not-found. PUT saves shall insert absent keys instead. | 9200 (+100), 9300 (OTHER) |
| REQ-EDGE-015 | S | Descriptions are stored trimmed (no 50-char right padding). | 9200/9600 (Q5) — deviation D-08 |

## 4. Non-functional requirements

| ID | Requirement |
|---|---|
| REQ-NFR-001 | REST/JSON contract per `target-spec.md` (OpenAPI 3.0): `GET/POST /api/transaction-types`, `GET/PUT/PATCH/DELETE /api/transaction-types/{trType}`, `POST /api/auth/login`; public `GET /actuator/health`. |
| REQ-NFR-002 | HTTP status mapping: 200/201/204 success; 400 validation (incl. no-change and invalid filter); 401 unauthenticated; 403 non-admin; 404 not found; 409 duplicate key, FK restrict, lock conflict; 500 other DB errors. |
| REQ-NFR-003 | Error body `{ "code": "<CODE>", "message": "<message>" }` (source-derived or documented platform/validation message). |
| REQ-NFR-004 | Keyset (not offset) pagination reproducing the 7-row page-at-a-time UX: stateless cursor params replace COMMAREA first/last keys, page size default 7 (max 50), requests use FORWARD/BACKWARD and a client-supplied page label; response has `rows, firstKey, lastKey, nextCursor, hasNext, hasPrev, pageNumber, message`. |
| REQ-NFR-005 | Admin-only authorization via Spring Security (JWT bearer, role `ADMIN`) on every `/api/transaction-types/**` endpoint. |
| REQ-NFR-006 | Each write is a single DB transaction (SYNCPOINT equivalent); failures roll back. |
| REQ-NFR-007 | PostgreSQL 15 schema managed by Flyway (`V1__create_tables.sql`, `V2__seed_data.sql`), seeded with CREADB21 data. |
| REQ-NFR-008 | Java 17, Spring Boot 3; React 18 + TypeScript; tests via `mvn test` (Surefire, including Testcontainers integration) and `npm test`; `npm run lint` / `npm run typecheck` check UI sources. Last observed totals: 97 API and 52 UI; see `test-plan.md` for provenance. |
| REQ-NFR-009 | API field messages are centralized in `SourceMessages` / `TransactionTypeEdits`, mirrored in UI validation. Source-derived messages and new/different UI/platform messages are distinguished in target-spec/deviations. |
| REQ-NFR-010 | Accessibility/UX: valid actions have clickable buttons and matching `useFunctionKeys` bindings. CTLI: Enter/F2/F3/F7/F8/F10; CTTU: state-valid Enter/F3/F4/F5/F12. Mapped repeats and repeated native Enter are suppressed; single native Enter on interactive controls is preserved. |

## 5. RTM

Columns: Design = retained implementation; Tests = stable scenario IDs from `test-plan.md`. Status `Mapped` is a design/scenario cross-reference, **not an individual passing test result**. The test plan distinguishes covered, partial and unautomated scenarios; only its suite totals are historical observed results.

| REQ | Design (API / UI) | Tests | Status |
|---|---|---|---|
| REQ-CTLI-001 | `TransactionTypeService.page` (FORWARD, no cursor); `TransactionTypeListPage` initial load | TP-01 | Mapped |
| REQ-CTLI-002 | `page` size+1 look-ahead → `hasNext`, `nextCursor` | TP-02 | Mapped |
| REQ-CTLI-003 | `page(direction=FORWARD, cursor, page)` → `findForward` | TP-03 | Mapped |
| REQ-CTLI-004 | `page(direction=BACKWARD, cursor, page)` → `findBackward` + reverse | TP-04 | Mapped |
| REQ-CTLI-005 | UI Prev on page 1 | TP-05 | Mapped |
| REQ-CTLI-006 | UI Next when !hasNext | TP-06 | Mapped |
| REQ-CTLI-007 | `page` → `message` | TP-07 | Mapped |
| REQ-CTLI-008 | `pageNumber` (1 without earlier rows, otherwise max(page,2)); UI "Page n" | TP-03, TP-04 | Mapped |
| REQ-CTLI-009 | `TranTypeRules.typeFilter` → `TransactionTypeEdits`; repository predicates | TP-08 | Mapped |
| REQ-CTLI-010 | 400 `VALIDATION_ERROR` | TP-09 | Mapped |
| REQ-CTLI-011 | `descFilter` → `LIKE %x%` | TP-10 | Mapped |
| REQ-CTLI-012 | `page` → `message` when filtered count = 0 | TP-11 | Mapped |
| REQ-CTLI-013 | UI Enter applies changed filters and reloads page 1 | TP-12 | Mapped |
| REQ-CTLI-014 | UI list `enter` action validation | TP-13 | Mapped |
| REQ-CTLI-015 | UI list `enter` action validation | TP-14 | Mapped |
| REQ-CTLI-016 | UI pending-delete highlight + info | TP-15 | Mapped |
| REQ-CTLI-017 | `DELETE /{trType}` 204; UI reload page 1 + info | TP-16 | Mapped |
| REQ-CTLI-018 | 409 `FK_RESTRICT` | TP-17 | Mapped |
| REQ-CTLI-019 | 500 `DB_ERROR` / 404 | TP-18 | Mapped |
| REQ-CTLI-020 | UI editable description + info | TP-19 | Mapped |
| REQ-CTLI-021 | `@TranTypeDescription`, UI `descriptionError` | TP-20 | Mapped |
| REQ-CTLI-022 | 400 `NO_CHANGE`, UI compare | TP-21 | Mapped |
| REQ-CTLI-023 | `PATCH /{trType}` 200; UI info | TP-22 | Mapped |
| REQ-CTLI-024 | 404 `NOT_FOUND` on PATCH | TP-23 | Mapped |
| REQ-CTLI-025 | 409 `LOCK_ERROR` | TP-24 | Mapped |
| REQ-CTLI-026 | 500 `DB_ERROR` | TP-25 | Mapped |
| REQ-CTLI-027 | UI Confirm (F10) handler | TP-26 | Mapped |
| REQ-CTLI-028 | UI Add (F2) → `/transaction-types/maintain?from=list` | TP-27 | Mapped |
| REQ-CTLI-029 | UI Exit (F3) → `/admin` | TP-28 | Mapped |
| REQ-CTLI-030 | UI only binds listed keys | TP-28 | Mapped |
| REQ-CTLI-031 | UI info default | TP-01 | Mapped |
| REQ-CTLI-032 | `GlobalExceptionHandler` DB_ERROR | TP-25 | Mapped |
| REQ-CTLI-033 | UI `ScreenHeader` | TP-01 | Mapped |
| REQ-CTTU-001 | UI `TransactionTypeEditPage` state `NOT_FETCHED` | TP-30 | Mapped |
| REQ-CTTU-002 | `TranTypeRules.normalizeTypeCode` → `TransactionTypeEdits`, UI | TP-31 | Mapped |
| REQ-CTTU-003 | same | TP-31 | Mapped |
| REQ-CTTU-004 | same | TP-31 | Mapped |
| REQ-CTTU-005 | same | TP-32 | Mapped |
| REQ-CTTU-006 | `GET /{trType}` 200; UI state `SHOW_DETAILS` | TP-33 | Mapped |
| REQ-CTTU-007 | `GET /{trType}` 404; UI state `DETAILS_NOT_FOUND` | TP-34 | Mapped |
| REQ-CTTU-008 | 500 `DB_ERROR` | TP-25 | Mapped |
| REQ-CTTU-009 | UI F5 in `DETAILS_NOT_FOUND` → `CREATE_NEW_RECORD` | TP-35 | Mapped |
| REQ-CTTU-010 | Bean Validation on `TransactionTypeCreateRequest` / `TransactionTypeUpdateRequest`, UI | TP-36 | Mapped |
| REQ-CTTU-011 | UI compare → message | TP-37 | Mapped |
| REQ-CTTU-012 | UI state `CHANGES_OK_NOT_CONFIRMED` | TP-38 | Mapped |
| REQ-CTTU-013 | `PUT /{trType}` for CTTU add/edit; PATCH for CTLI strict update; POST insert-only API | TP-39, TP-40 | Mapped |
| REQ-CTTU-014 | 409 `DUPLICATE_KEY` | TP-41 | Mapped |
| REQ-CTTU-015 | 409 `LOCK_ERROR` | TP-24 | Mapped |
| REQ-CTTU-016 | 500 `DB_ERROR` | TP-25 | Mapped |
| REQ-CTTU-017 | UI state `CONFIRM_DELETE` | TP-42 | Mapped |
| REQ-CTTU-018 | `DELETE /{trType}` 204; UI | TP-43 | Mapped |
| REQ-CTTU-019 | 409 `FK_RESTRICT` | TP-17, TP-44 | Mapped |
| REQ-CTTU-020 | 500 / 404 | TP-18 | Mapped |
| REQ-CTTU-021 | UI F12 in `CONFIRM_DELETE` | TP-45 | Mapped |
| REQ-CTTU-022 | UI F12 in `CHANGES_OK_NOT_CONFIRMED` | TP-46 | Mapped |
| REQ-CTTU-023 | UI F12 in SHOW_DETAILS/CREATE_NEW_RECORD/DETAILS_NOT_FOUND | TP-47 | Mapped |
| REQ-CTTU-024 | UI state-dependent handlers | TP-48 | Mapped |
| REQ-CTTU-025 | UI state-dependent button visibility | TP-48 | Mapped |
| REQ-CTTU-026 | UI Exit → list when from=list, otherwise `/admin` | TP-49 | Mapped |
| REQ-CTTU-027 | UI key field disabled outside KEY_ENTRY states | TP-33 | Mapped |
| REQ-CTTU-028 | UI KEY_ENTRY enabled in completed/failed states | TP-39 | Mapped |
| REQ-EDGE-001 | `page` empty | TP-07 | Mapped |
| REQ-EDGE-002 | `page` exactly 7 | TP-50 | Mapped |
| REQ-EDGE-003 | 8 rows | TP-51 | Mapped |
| REQ-EDGE-004 | backward short-page / empty behavior (no fallback) | TP-52 | Mapped |
| REQ-EDGE-005 | FK RESTRICT | TP-17 | Mapped |
| REQ-EDGE-006 | delete leaf | TP-16 | Mapped |
| REQ-EDGE-007 | duplicate | TP-41 | Mapped |
| REQ-EDGE-008 | blank inputs | TP-31, TP-36 | Mapped |
| REQ-EDGE-009 | punctuation | TP-36 | Mapped |
| REQ-EDGE-010 | >50 | TP-36 | Mapped |
| REQ-EDGE-011 | key normalization | TP-32 | Mapped |
| REQ-EDGE-012 | 403 / UI guard | TP-53 | Mapped |
| REQ-EDGE-013 | 401 | TP-54 | Mapped |
| REQ-EDGE-014 | PATCH/DELETE 404; PUT inserts | TP-23, TP-18 | Mapped |
| REQ-EDGE-015 | trimmed storage | TP-22 | Mapped |
| REQ-NFR-001 | OpenAPI in `target-spec.md`; controller | TP-55 | Mapped |
| REQ-NFR-002 | `GlobalExceptionHandler` | TP-17, TP-23, TP-41, TP-53 | Mapped |
| REQ-NFR-003 | `ErrorResponse` | TP-17 | Mapped |
| REQ-NFR-004 | keyset repository queries | TP-02–TP-04, TP-50–TP-52 | Mapped |
| REQ-NFR-005 | `SecurityConfig` | TP-53, TP-54 | Mapped |
| REQ-NFR-006 | `@Transactional` service | TP-17 | Mapped |
| REQ-NFR-007 | Flyway V1/V2 | TP-56 | Mapped |
| REQ-NFR-008 | build + test suites | TP-57 | Mapped |
| REQ-NFR-009 | API `SourceMessages`; UI page MSG/INFO and `validation.ts` | TP-31, TP-36 | Mapped |
| REQ-NFR-010 | UI `useFunctionKeys` | TP-28 | Mapped |
