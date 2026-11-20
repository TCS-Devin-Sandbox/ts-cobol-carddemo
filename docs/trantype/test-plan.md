# Phase 6 — Test plan and results

Expected results come from the COBOL source (see `discovery-ctli-cttu.md`) and the documented deviations (`deviations.md`). Test layers:

| Layer | Tooling | Location |
|---|---|---|
| API unit | JUnit 5 + Mockito, AssertJ | `carddemo-trantype-api/src/test/java/**/{service,validation,web}` |
| API slice | `@WebMvcTest` + spring-security-test (JWT) | `TransactionTypeControllerTest` |
| API integration | `@SpringBootTest` + Testcontainers `postgres:15-alpine` + Flyway | `it/TransactionTypeApiIntegrationTest` (Surefire, runs under `mvn test`) |
| UI component | Vitest + React Testing Library + user-event (API mocked) | `carddemo-trantype-ui/src/**/*.test.ts(x)` |

## Matrix

Stable TP IDs are retained for the RTM. `IT` = `TransactionTypeApiIntegrationTest`, `Service` = `TransactionTypeServiceTest`, `Edits` = `TransactionTypeEditsTest`, `Rules` = `TranTypeRulesTest`, `Handler` = `GlobalExceptionHandlerTest`, `Controller` = `TransactionTypeControllerTest`. UI files are under `src/`. `Mapped` identifies existing coverage relevant to the scenario, **not a new per-scenario run/result**. `Partial` / `Gap` explicitly identify missing assertions or scenarios. Only aggregate previously observed results are reported below.

| Test | REQ | Precondition | Input | Expected (retained implementation) | Existing coverage | Coverage |
|---|---|---|---|---|---|---|
| TP-01 | REQ-CTLI-001, -031, -033 | Seed (7 rows) | GET first page / open list | 01..07 ascending, page 1, info `Type U to update, D to delete any record`, header CTLI/COTRTLIC | IT `firstPageShowsSevenSeededRows`; UI `pages/TransactionTypeListPage.test.tsx` initial load (header values are not all asserted) | Partial |
| TP-02 | REQ-CTLI-002 | 8+ rows | first page | hasNext=true, nextCursor = 8th key | Service `firstPageUsesLookaheadForHasNext`; IT `forwardAndBackwardKeysetPaging` (size 3) | Mapped |
| TP-03 | REQ-CTLI-003, -008 | 7 seed rows, size=3 | FORWARD with nextCursor and page=2/3 | 04..06 then 07, pages 2/3; last hasNext=false | IT `forwardAndBackwardKeysetPaging` (size 3); Service `lastPageHasNoNext`; UI list paging | Partial |
| TP-04 | REQ-CTLI-004, -008 | 7 seed rows, size=3 | BACKWARD with firstKey=04 and page=1 | 01..03 ascending, page 1, hasPrev=false | IT `forwardAndBackwardKeysetPaging`; Service `backwardPageIsReversedToAscending`; UI list paging | Mapped |
| TP-05 | REQ-CTLI-005 | Page 1 | UI F7 / direct BACKWARD cursor=01 | UI F7 reports `No previous pages to display` without a request; direct empty BACKWARD response is described in target-spec §3.1 | UI list `F7 on first page shows no previous pages`; no direct API empty-backward test | Partial |
| TP-06 | REQ-CTLI-006 | Last page | F8 | `No more pages to display` | UI `pages/TransactionTypeKeyboard.test.tsx` CTLI page-boundary assertions | Mapped |
| TP-07 | REQ-CTLI-007, REQ-EDGE-001 | Empty table | GET first page | rows=[], hasNext=false, hasPrev=false, `No records found for this search condition.` | IT `emptyTable`; Service `emptyTableMessage`; UI list empty-table test | Mapped |
| TP-08 | REQ-CTLI-009 | Seed | typeFilter=03 | only 03 | IT `filters` accepts typeFilter=3 and returns 03; Service `filtersArePassedThrough` | Mapped |
| TP-09 | REQ-CTLI-010 | Seed | typeFilter=3X / 3 | 3X → 400 filter error; 3 → normalized 03 (accepted) | IT `filters`; Rules/Edits type-filter validation; UI list filter validation | Mapped |
| TP-10 | REQ-CTLI-011 | Seed | descFilter=RE | 03 CREDIT, 05 REFUND, 06 REVERAL | IT `filters` | Mapped |
| TP-11 | REQ-CTLI-012 | Seed | typeFilter=01&descFilter=PAY | rows=[], `No Records found for these filter conditions` | IT `filters` (description-only zero match); Service `filterWithNoMatchesMessage`; no combined-filter assertion | Partial |
| TP-12 | REQ-CTLI-013 | Row D staged | change filter + Enter | first page reloaded with filter, selections cleared | No dedicated retained test for pending action + changed filter + Enter | Gap |
| TP-13 | REQ-CTLI-014 | Seed | select `X` | `Action code selected is invalid` | UI list `enforces single valid action code` | Mapped |
| TP-14 | REQ-CTLI-015 | Seed | U on row 1 + D on row 2 | `Please select only 1 action` | UI list `enforces single valid action code` | Mapped |
| TP-15 | REQ-CTLI-016 | Seed | D + Enter | info `Delete HIGHLIGHTED row ? Press F10 to confirm`, no delete issued | UI list delete-success/FK tests stage D+Enter before F10 | Mapped |
| TP-16 | REQ-CTLI-017, REQ-EDGE-006, REQ-CTTU-018 | Type 09 without children | F10 / DELETE /9 | 204; row gone; UI info `HIGHLIGHTED row deleted.Hit Enter to continue`, page 1 reloaded | IT `deleteWithAndWithoutChildren`; Service `deleteExisting`; UI list delete success | Mapped |
| TP-17 | REQ-CTLI-018, REQ-CTTU-019, REQ-EDGE-005, REQ-NFR-002/003/006 | Type 01 with 5 categories | DELETE /01 | 409 FK_RESTRICT `Please delete associated child records first:SQLCODE :-532`; parent retained | IT `deleteWithAndWithoutChildren` asserts parent retained; Controller `foreignKeyRestrictIs409`; Handler `foreignKeyViolationMapsToChildRecordsMessage` (child count not asserted) | Partial |
| TP-18 | REQ-CTLI-019, REQ-CTTU-020, REQ-EDGE-014 | Key 42 absent / DB failure | DELETE | Missing: 404 `Delete failed with message:SQLCODE :+100`; other DB errors: generic DB_ERROR with SQLSTATE | Service `deleteMissingRow`; generic handler covered separately, no delete-specific generic-DB failure test | Partial |
| TP-19 | REQ-CTLI-020 | Seed | U + Enter | U enables description before Enter; Enter stages/highlights it with update prompt | UI list `updates a description inline after F10 confirmation` | Mapped |
| TP-20 | REQ-CTLI-021 | U staged | desc `BAD-VALUE` / blank / 51 chars | `Transaction Desc can have numbers or alphabets only.` / `Transaction Desc must be supplied.` / `…must not exceed 50 characters.` | IT `inlineUpdate` blank/punctuation; Edits required/alphanumeric/max length; UI list inline invalid edit | Mapped |
| TP-21 | REQ-CTLI-022 | U staged, unchanged / case-only change | F10 / PATCH reversal | 400 NO_CHANGE `No change detected with respect to database values.`; UI blocks unchanged write | IT `inlineUpdate`; Service `inlineUpdateUnchanged`; UI list inline no-change assertion | Mapped |
| TP-22 | REQ-CTLI-023, REQ-EDGE-015 | Type 06 | PATCH REVERSAL | PATCH 200, trimmed description; UI `HIGHLIGHTED row was updated` and current page re-read | IT `inlineUpdate`; Service `saveInsertsWhenMissing` verifies trim on save; UI list inline success | Partial |
| TP-23 | REQ-CTLI-024, REQ-EDGE-014 | Key 42 absent | PATCH /42 | PATCH 404 `Record not found. Deleted by others ?` (PUT would insert) | IT `inlineUpdate`; Service `inlineUpdateMissingRow` | Mapped |
| TP-24 | REQ-CTLI-025, REQ-CTTU-015 | Row 05 locked FOR UPDATE by another tx, lock_timeout 1s | PATCH /05 and PUT /05 | PATCH/PUT 409 LOCK_ERROR: `Deadlock. Someone else updating ?` / `Could not lock record for update`; REFUND unchanged after rollback | IT `updateBlockedByRowLock`; Service `inlineUpdateDeadlock` / `saveLockFailureMapsToCouldNotLock` | Mapped |
| TP-25 | REQ-CTLI-026, -032, REQ-CTTU-008, -016 | DB failure (mock) | DataAccessException handler | 500 DB_ERROR `Error accessing: TRANSACTION_TYPE table. SQLCODE: SQLSTATE <state>` (null when absent) | Handler `otherIntegrityViolation` checks 500/SQLSTATE; `missingSqlState` checks extraction only; no per-operation database-outage tests | Partial |
| TP-26 | REQ-CTLI-027 | No pending action | F10 | Keyboard F10 behaves as Enter; F10 button disabled | No retained assertion specifically exercises F10 without a pending action | Gap |
| TP-27 | REQ-CTLI-028 | List | F2 | navigate to CTTU page | UI list `F2 opens the add screen`; UI keyboard CTLI test | Mapped |
| TP-28 | REQ-CTLI-029, -030, REQ-NFR-010 | List | F3 / keyboard F7, F10, Enter | navigate to admin menu; only bound keys act | UI list filter/navigation test; `pages/TransactionTypeKeyboard.test.tsx`; `hooks/useFunctionKeys.test.ts` (including mapped repeats and native Enter) | Mapped |
| TP-30 | REQ-CTTU-001 | Entry / NOT_FETCHED | open page | NOT_FETCHED; key editable, description disabled; F4/F5 hidden, header CTTU/COTRTUPC | UI edit initial validation test; keyboard CTTU test (state-dependent keys) | Mapped |
| TP-31 | REQ-CTTU-002/003/004, REQ-EDGE-008, REQ-NFR-009 | Entry | key blank / `A1` / `00` | UI blank → `No input received`; API blank → required message; A1 → numeric error; 00 → zero error | UI edit `prompts for key and rejects blank / invalid input`; Rules/Edits key edits; Controller `createValidationUsesSourceMessageKeyFirst` | Mapped |
| TP-32 | REQ-CTTU-005, REQ-EDGE-011 | Seed | key `1` / ` 5` / `7 ` | normalized `01` / `05` / `07` | Rules/Edits normalization; Service `getNotFound` normalizes 5; IT `addEditAndDuplicate` normalizes create key 8; UI edit lookup | Partial |
| TP-33 | REQ-CTTU-006, -027 | Seed | Enter key 1 | SHOW_DETAILS: PURCHASE, key disabled, F4 visible | UI edit `edits an existing record, detects no change, and cancels with F12`; Controller `getReturnsDto` | Mapped |
| TP-34 | REQ-CTTU-007 | Key 42 absent | Enter / GET /42 | 404 `No record found for this key in database`; info `Press F05 to add. F12 to cancel` | IT `addEditAndDuplicate`; Service `getNotFound`; UI edit add flow | Mapped |
| TP-35 | REQ-CTTU-009 | DETAILS_NOT_FOUND | F5 | info `Enter new transaction type details.`, description editable | UI edit `adds a new record: not found -> F5 -> validate -> F5 save` | Mapped |
| TP-36 | REQ-CTTU-010, REQ-EDGE-009/010 | CREATE_NEW_RECORD / SHOW_DETAILS | desc `FEE-ASSESSMENT`, blank, 51 chars | 400 `Transaction Desc can have numbers or alphabets only.` etc. | Edits description validation; Controller `createDescriptionValidation`; UI edit add validation | Mapped |
| TP-37 | REQ-CTTU-011 | SHOW_DETAILS | Enter with unchanged description | `No change detected with respect to values fetched.` | Service `saveUnchangedIsRejected`; UI edit no-change/cancel test | Mapped |
| TP-38 | REQ-CTTU-012 | SHOW_DETAILS | valid change + Enter | info `Changes validated.Press F5 to save`, fields read-only | UI edit no-change/cancel test; keyboard CTTU validation/save flow | Mapped |
| TP-39 | REQ-CTTU-013, -028 | CHANGES_OK_NOT_CONFIRMED | F5 | PUT (no upsert param) updates → `Changes committed to database`; key entry enabled, next Enter looks up current key | IT `addEditAndDuplicate`; Service `saveUpdatesExisting` / `saveInsertsWhenMissing`; UI keyboard CTTU save and next-Enter lookup | Mapped |
| TP-40 | REQ-CTTU-013 | CHANGES_OK_NOT_CONFIRMED | F5 (PUT) / explicit API POST | PUT from CTTU or explicit POST API insert → 201 + body (no Location header); descriptions stored trimmed | IT `addEditAndDuplicate` covers POST 201 and PUT insert 201; UI edit add uses PUT; Service save insert verifies trim | Mapped |
| TP-41 | REQ-CTTU-014, REQ-EDGE-007 | Key 08 exists | POST 08 | POST duplicate → 409 DUPLICATE_KEY `Error inserting record into: TRANSACTION_TYPE Table. SQLCODE:-803`; UI save failures → `Changes unsuccessful` | IT `addEditAndDuplicate`; Controller `duplicateKeyIs409`; Handler `uniqueViolationMapsToInsertMessage`; UI edit generic save failure (no dedicated UI duplicate fixture) | Partial |
| TP-42 | REQ-CTTU-017 | SHOW_DETAILS | F4 | CONFIRM_DELETE prompt; page Enter ignored, Enter button hidden; native single Enter on a focused F4 button still activates it | UI edit delete-confirmation test; keyboard CTTU test checks Enter hidden | Mapped |
| TP-43 | REQ-CTTU-018 | CONFIRM_DELETE (type 09) | F4 | DELETE 204 → info `Delete successful.` | UI edit delete-confirmation test; keyboard CTTU delete; IT `deleteWithAndWithoutChildren` | Mapped |
| TP-44 | REQ-CTTU-019 | CONFIRM_DELETE (type 01) | F4 | 409 FK_RESTRICT `Please delete associated child records first:SQLCODE :-532` | UI edit `deletes with F4 confirmation, cancel with F12, and FK failure`; IT restricted delete | Mapped |
| TP-45 | REQ-CTTU-021 | CONFIRM_DELETE | F12 | `Delete was cancelled`, back to key entry | UI edit delete/cancel test; keyboard CTTU cancel | Mapped |
| TP-46 | REQ-CTTU-022 | CHANGES_OK_NOT_CONFIRMED | F12 | `Update was cancelled`; edit restores original in SHOW_DETAILS; add returns to CREATE_NEW_RECORD | UI edit no-change/cancel test; keyboard CTTU cancel | Mapped |
| TP-47 | REQ-CTTU-023 | SHOW_DETAILS | F12 | key entry, fields cleared | No dedicated F12-from-SHOW_DETAILS reset assertion | Gap |
| TP-48 | REQ-CTTU-024, -025 | per state | invalid key | Unbound keys ignored; state-invalid buttons hidden; mapped repeats suppressed; native single Enter preserved, repeated Enter prevented | `hooks/useFunctionKeys.test.ts` unbound-key / listener cleanup / latest handlers; UI keyboard CTTU state gating; `components/FunctionKeyBar.test.tsx` hidden/disabled/empty cases | Mapped |
| TP-49 | REQ-CTTU-026 | CTTU | F3 | `?from=list` → list; otherwise admin menu | UI edit F3 tests cover both caller contexts; keyboard CTTU F3 test | Mapped |
| TP-50 | REQ-EDGE-002 | Exactly 7 rows | first page | hasNext=false | IT `firstPageShowsSevenSeededRows` | Mapped |
| TP-51 | REQ-EDGE-003 | 8+ rows | page 1 → 2 → back | boundary rows correct, no skips/repeats | IT keyset paging covers seven rows at size 3; Service look-ahead/backward tests; no exact eight-row round-trip fixture | Partial |
| TP-52 | REQ-EDGE-004 | 9 rows, 02/03 deleted after paging | prev cursor=08 | Only earlier remaining rows (01,04..07), not forward refill with 08/09; empty earlier set → empty page / no-more-search-conditions message | No retained short-backward/concurrent-delete test; behavior read from `TransactionTypeService.page` | Gap |
| TP-53 | REQ-EDGE-012, REQ-NFR-005 | user / user123 (USER role) | any /api/transaction-types call; open UI page | API USER role → 403 FORBIDDEN `Access restricted to administrators`; UI guard redirects to login and login rejects non-admin session | IT `nonAdminRejected`; Controller `nonAdminIs403`; UI `App.test.tsx` non-admin route/login tests | Mapped |
| TP-54 | REQ-EDGE-013 | No / bad token | GET list | 401 UNAUTHORIZED; UI redirects to login | IT `nonAdminRejected`; Controller `unauthenticatedIs401`; UI App unauthenticated redirect (bad-token path not separately tested) | Partial |
| TP-55 | REQ-NFR-001 | — | all endpoints | Representative endpoint coverage consistent with target-spec; not an exhaustive schema-conformance assertion | Controller (8 tests) + IT (10 tests) cover representative routes; no generated OpenAPI conformance test | Partial |
| TP-56 | REQ-NFR-007 | Fresh PostgreSQL 15 container | Flyway V1+V2 | Flyway V1+V2 define seven types (06 REVERAL) and 18 categories | IT starts Flyway and checks seven type keys; no direct assertion of all 18 category rows or source spelling | Partial |
| TP-57 | REQ-NFR-008 | — | mvn test; npm test; npm run lint; npm run typecheck (prior runs) | Observed earlier: API 97 / UI 52 tests passed; lint/typecheck exit 0; not rerun for docs | Prior recorded `mvn test`, `npm test`, lint/typecheck; no new execution in this docs-only change | Mapped |

## Results (previous implementation checks; not rerun for this docs-only change)

These are the results already observed while porting/reviewing the retained implementation on the parent branch, through `21cda45`. They are not results from the retired copy. API totals are also present in local Surefire reports; UI totals are from the final review-fix run. No new timestamp or build result is asserted here.

| Suite | Command | Tests | Failures / errors | Result |
|---|---|---|---|---|
| API all tests (Surefire, including Testcontainers PostgreSQL 15) | `mvn test` | 97 | 0 / 0; skipped 0 | Previously passed |
| UI unit/component (Vitest, 8 files) | `npm test` | 52 | 0 | Previously passed |
| UI lint + type check | `npm run lint && npm run typecheck` | — | exit 0 | Previously passed |

API breakdown: `TranTypeRulesTest` 19; `TransactionTypeServiceTest` 16; `TransactionTypeControllerTest` 8; `TransactionTypeEditsTest` 38; `GlobalExceptionHandlerTest` 6; `TransactionTypeApiIntegrationTest` 10 = **97**. There is no Failsafe plugin or separate verify-only integration suite.

UI files: `App.test.tsx`, `validation.test.ts`, `pages/TransactionTypeListPage.test.tsx`, `pages/TransactionTypeEditPage.test.tsx`, `pages/TransactionTypeKeyboard.test.tsx`, `components/FunctionKeyBar.test.tsx`, `hooks/useFunctionKeys.test.ts`, and `components/MessageLine.test.tsx`.

Additional port-specific coverage: IT `healthIsPublic` checks unauthenticated 200/UP; IT `updateBlockedByRowLock` tests real contention with 1s lock_timeout for PATCH/PUT and checks no persisted change. Hook tests cover every mapped key, repeat F4 suppression, native single Enter and repeat Enter suppression on a focused button. FunctionKeyBar tests cover clickable, hidden, disabled and empty key lists.

## Not covered by automation

- Real DB2 -911 deadlocks between CICS tasks: PostgreSQL row-lock contention is tested instead (TP-24), not a PostgreSQL deadlock cycle.
- Short-backward/concurrent-delete scenarios (TP-52), pending-action/filter-change interaction (TP-12), F10 without pending action (TP-26), F12 reset directly from SHOW_DETAILS (TP-47), and exhaustive OpenAPI schema conformance are not covered by retained tests.
- Generic error mapping is unit-tested via an integrity exception and SQLSTATE extraction, not a failing live database for each operation (TP-25). Seed integration checks do not assert the full 18-category dataset.
- UI tests mock the API; no manual browser walkthrough or visual verification is claimed. No UI production-bundle result is claimed in this synchronization.
