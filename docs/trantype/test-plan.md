# Phase 6 — Test plan and results

Expected results come from the COBOL source (see `discovery-ctli-cttu.md`) and the documented deviations (`deviations.md`). Test layers:

| Layer | Tooling | Location |
|---|---|---|
| API unit | JUnit 5 + Mockito, AssertJ | `carddemo-trantype-api/src/test/java/**/{service,validation,web}` |
| API slice | `@WebMvcTest` + spring-security-test (JWT) | `TransactionTypeControllerTest` |
| API integration | `@SpringBootTest` + Testcontainers `postgres:15-alpine` + Flyway | `it/TransactionTypeApiIT` |
| UI component | Vitest + React Testing Library + user-event (API mocked) | `carddemo-trantype-ui/src/**/*.test.ts(x)` |

## Matrix

| Test | REQ | Precondition | Input | Expected (from source) | Actual (automated test) | Result |
|---|---|---|---|---|---|---|
| TP-01 | REQ-CTLI-001, -031, -033 | Seed (7 rows) | GET first page / open list | 01..07 ascending, page 1, info `Type U to update, D to delete any record`, header CTLI/COTRTLIC | IT firstPageOfSeedIsSingleFullPage; Unit firstPageUsesLookAheadForHasNext; UI shows the first 7 rows | Pass |
| TP-02 | REQ-CTLI-002 | 8+ rows | first page | hasNext=true, nextCursor = 8th key | Unit firstPageUsesLookAheadForHasNext; IT pageForwardAndBackwardAcrossBoundaries | Pass |
| TP-03 | REQ-CTLI-003, -008 | 16 rows | next with cursor=nextCursor | rows 08..14, page 2; then 15..16, page 3, hasNext=false | IT pageForwardAndBackwardAcrossBoundaries; Unit forwardFromCursorComputesPageNumber; UI pages forward | Pass |
| TP-04 | REQ-CTLI-004, -008 | 16 rows, on page 3 | prev with cursor=firstKey | rows 08..14 (page 2), then 01..07 (page 1, hasPrev=false) | IT pageForwardAndBackwardAcrossBoundaries; Unit backwardReadsDescendingAndReverses; UI pages back | Pass |
| TP-05 | REQ-CTLI-005 | Page 1 | F7 / prev cursor=01 | `No previous pages to display`, page 1 redisplayed | IT prevOnFirstPage; Unit backwardWithNothingBeforeReturnsFirstPageWithMessage; UI page boundaries | Pass |
| TP-06 | REQ-CTLI-006 | Last page | F8 | `No more pages to display` | UI reports page boundaries | Pass |
| TP-07 | REQ-CTLI-007, REQ-EDGE-001 | Empty table | GET first page | rows=[], hasNext=false, hasPrev=false, `No records found for this search condition.` | IT emptyTable; Unit emptyTable; UI empty-table message | Pass |
| TP-08 | REQ-CTLI-009 | Seed | typeFilter=03 | only 03 | IT typeFilterExactMatch; Unit filtersArePassedToRepository | Pass |
| TP-09 | REQ-CTLI-010 | Seed | typeFilter=3X / 3 | 400 `TYPE CODE FILTER,IF SUPPLIED MUST BE A 2 DIGIT NUMBER` | IT invalidTypeFilter; Unit typeFilterMustBeTwoDigits; UI validates the type filter | Pass |
| TP-10 | REQ-CTLI-011 | Seed | descFilter=RE | 03 CREDIT, 05 REFUND, 06 REVERAL | IT descriptionFilterLike | Pass |
| TP-11 | REQ-CTLI-012 | Seed | typeFilter=01&descFilter=PAY | rows=[], `No Records found for these filter conditions` | IT filtersWithNoMatches; Unit filtersWithNoMatches | Pass |
| TP-12 | REQ-CTLI-013 | Row D staged | change filter + Enter | first page reloaded with filter, selections cleared | UI applies filters on Enter | Pass |
| TP-13 | REQ-CTLI-014 | Seed | select `X` | `Action code selected is invalid` | UI enforces valid action codes; Unit (edits.test) rejects invalid codes | Pass |
| TP-14 | REQ-CTLI-015 | Seed | U on row 1 + D on row 2 | `Please select only 1 action` | UI single-action rule; edits.test | Pass |
| TP-15 | REQ-CTLI-016 | Seed | D + Enter | info `Delete HIGHLIGHTED row ? Press F10 to confirm`, no delete issued | UI deletes after D + Enter + F10 | Pass |
| TP-16 | REQ-CTLI-017, REQ-EDGE-006, REQ-CTTU-018 | Type 09 without children | F10 / DELETE /9 | 204; row gone; UI info `HIGHLIGHTED row deleted.Hit Enter to continue`, page 1 reloaded | IT deleteWithoutChildren; Unit deleteExisting; UI deletes after D + Enter + F10 | Pass |
| TP-17 | REQ-CTLI-018, REQ-CTTU-019, REQ-EDGE-005, REQ-NFR-002/003/006 | Type 01 with 5 categories | DELETE /01 | 409 `{code:FK_RESTRICT, message:"Please delete associated child records first: SQLCODE: -532"}`; parent + 5 children remain | IT deleteWithChildrenIsRestricted; Unit GlobalExceptionHandlerTest.foreignKeyViolation…; UI FK restrict message | Pass |
| TP-18 | REQ-CTLI-019, REQ-CTTU-020, REQ-EDGE-014 | Key 42 absent / DB failure | DELETE | 404 `Delete failed with message: SQLCODE: 100`; DB failure 500 `Delete failed with message: SQLCODE: -999` | IT deleteWithoutChildren (second delete); Unit deleteMissing, deleteOtherDbError | Pass |
| TP-19 | REQ-CTLI-020 | Seed | U + Enter | description of that row editable, info `Update HIGHLIGHTED row. Press F10 to save` | UI updates inline | Pass |
| TP-20 | REQ-CTLI-021 | U staged | desc `BAD-VALUE` / blank / 51 chars | `Transaction Desc can have numbers or alphabets only.` / `Transaction Desc must be supplied.` / `…must not exceed 50 characters.` | UI updates inline (invalid); IT inlineUpdateValidAndInvalid; Unit TransactionTypeEditsTest | Pass |
| TP-21 | REQ-CTLI-022 | U staged, unchanged / case-only change | Enter / PUT `reversal` | `No change detected with respect to database values.` (400 NO_CHANGE) | IT inlineUpdateValidAndInvalid; Unit updateNoChange; UI updates inline | Pass |
| TP-22 | REQ-CTLI-023, REQ-EDGE-015 | Type 06 | PUT `REVERSAL  ` | 200, stored `REVERSAL` (trimmed); UI info `HIGHLIGHTED row was updated`, page re-read | IT inlineUpdateValidAndInvalid; Unit updateExisting; UI updates inline | Pass |
| TP-23 | REQ-CTLI-024, REQ-EDGE-014 | Key 42 absent | PUT /42 | 404 `Record not found. Deleted by others ? SQLCODE: 100` | IT updateMissingKey; Unit updateMissingWithoutUpsert | Pass |
| TP-24 | REQ-CTLI-025, REQ-CTTU-015 | Row 05 locked FOR UPDATE by another tx, lock_timeout 1s | PUT /05 and PUT /05?upsert=true | 409 LOCK_CONFLICT `Deadlock. Someone else updating ? SQLCODE: -911` / `Could not lock record for update` | IT updateBlockedByRowLock; Unit updateLockConflictMessages | Pass |
| TP-25 | REQ-CTLI-026, -032, REQ-CTTU-008, -016 | DB failure (mock) | list / get / PUT | 500 DB_ERROR with `Error reading TRANSACTION_TYPE table…`, `Error accessing: TRANSACTION_TYPE table. SQLCODE:…`, `Update failed with…`, `Error updating: TRANSACTION_TYPE Table. SQLCODE:…` | Unit listDbErrorIsReported, getDbError, updateOtherDbErrorMessages | Pass |
| TP-26 | REQ-CTLI-027 | No pending action | F10 | behaves as Enter, no API write | UI treats F10 without a pending action as Enter | Pass |
| TP-27 | REQ-CTLI-028 | List | F2 | navigate to CTTU page | UI navigates to add (F2) | Pass |
| TP-28 | REQ-CTLI-029, -030, REQ-NFR-010 | List | F3 / keyboard F7, F10, Enter | navigate to admin menu; only bound keys act | UI exits on F3; keyboard tests in list/edit suites | Pass |
| TP-30 | REQ-CTTU-001 | Entry | open page | info `Enter transaction type to be maintained`, only key editable, F4/F5 disabled, header CTTU/COTRTUPC | UI starts in search state | Pass |
| TP-31 | REQ-CTTU-002/003/004, REQ-EDGE-008, REQ-NFR-009 | Entry | key blank / `A1` / `00` | `Tran Type code must be supplied.` / `…must be numeric.` / `…must not be zero.` | UI validates the key; IT getInvalidKeys; Unit TransactionTypeEditsTest; WebMvc createValidatesKeyBeforeDescription | Pass |
| TP-32 | REQ-CTTU-005, REQ-EDGE-011 | Seed | key `1` / ` 5` / `7 ` | normalized `01` / `05` / `07` | IT getExistingNormalizesKey; Unit typeCodeZeroPadded; UI shows an existing record | Pass |
| TP-33 | REQ-CTTU-006, -027 | Seed | Enter key 1 | SHOW: description PURCHASE, key disabled, F4 enabled | UI shows an existing record; IT getExistingNormalizesKey | Pass |
| TP-34 | REQ-CTTU-007 | Key 42 absent | Enter / GET /42 | 404 `No record found for this key in database`; info `Press F05 to add. F12 to cancel` | IT getMissingKey; Unit getMissing; UI adds a new type | Pass |
| TP-35 | REQ-CTTU-009 | NOT_FOUND | F5 | info `Enter new transaction type details.`, description editable | UI adds a new type | Pass |
| TP-36 | REQ-CTTU-010, REQ-EDGE-009/010 | Create/Show | desc `FEE-ASSESSMENT`, blank, 51 chars | 400 `Transaction Desc can have numbers or alphabets only.` etc. | IT addInvalidDescription; Unit descriptionAlphanumericOnly/descriptionMaxFifty; UI edits | Pass |
| TP-37 | REQ-CTTU-011 | SHOW | Enter with unchanged description | `No change detected with respect to values fetched.` | UI edits: no change; Unit updateNoChange (upsert=true message) | Pass |
| TP-38 | REQ-CTTU-012 | SHOW | valid change + Enter | info `Changes validated.Press F5 to save`, fields read-only | UI edits: confirm | Pass |
| TP-39 | REQ-CTTU-013, -028 | CONFIRM_SAVE (edit) | F5 | PUT ?upsert=true → `Changes committed to database`; next Enter restarts at key entry | UI edits … save with upsert; IT upsertInsertsMissingKey; Unit upsertInsertsWhenUpdateFindsNothing | Pass |
| TP-40 | REQ-CTTU-013 | CONFIRM_SAVE (add, key 08/42) | F5 / POST | 201 + Location `/api/transaction-types/08`, stored trimmed | IT addValidThenDuplicate; WebMvc createReturns201WithLocation; UI adds a new type | Pass |
| TP-41 | REQ-CTTU-014, REQ-EDGE-007 | Key 08 exists | POST 08 | 409 DUPLICATE_KEY `Error inserting record into: TRANSACTION_TYPE Table. SQLCODE: -803`; UI info `Changes unsuccessful` | IT addValidThenDuplicate; Unit uniqueViolationMapsToInsertMessage; UI duplicate-key message | Pass |
| TP-42 | REQ-CTTU-017 | SHOW | F4 | info `Delete this record ? Press F4 to confirm`; Enter → `Invalid key pressed` | UI deletes after F4 confirmation | Pass |
| TP-43 | REQ-CTTU-018 | CONFIRM_DELETE (type 09) | F4 | DELETE 204 → info `Delete successful.` | UI deletes after F4 confirmation; IT deleteWithoutChildren | Pass |
| TP-44 | REQ-CTTU-019 | CONFIRM_DELETE (type 01) | F4 | error `Please delete associated child records first: SQLCODE: -532` | UI reports delete with children; IT deleteWithChildrenIsRestricted | Pass |
| TP-45 | REQ-CTTU-021 | CONFIRM_DELETE | F12 | `Delete was cancelled`, back to key entry | UI cancels delete and update | Pass |
| TP-46 | REQ-CTTU-022 | CONFIRM_SAVE | F12 | `Update was cancelled`, original description shown, info `Update transaction type details shown.` | UI cancels delete and update | Pass |
| TP-47 | REQ-CTTU-023 | SHOW | F12 | key entry, fields cleared | UI F12 from show | Pass |
| TP-48 | REQ-CTTU-024, -025 | per state | invalid key | `Invalid key pressed`; buttons enabled per 0001 table | UI deletes after F4 confirmation (Enter disabled/invalid); starts in search state (F4/F5 disabled) | Pass |
| TP-49 | REQ-CTTU-026 | CTTU | F3 | admin menu | UI F3 returns to the admin menu | Pass |
| TP-50 | REQ-EDGE-002 | Exactly 7 rows | first page | hasNext=false | Unit exactlySevenRowsHasNoNextPage; IT firstPageOfSeedIsSingleFullPage | Pass |
| TP-51 | REQ-EDGE-003 | 8+ rows | page 1 → 2 → back | boundary rows correct, no skips/repeats | IT pageForwardAndBackwardAcrossBoundaries | Pass |
| TP-52 | REQ-EDGE-004 | 9 rows, 02/03 deleted after paging | prev cursor=08 | page 1 (01,04..09) instead of an error | IT shortBackwardPageAfterDeletesReturnsFirstPage; Unit shortBackwardPageFallsBackToFirstPage | Pass |
| TP-53 | REQ-EDGE-012, REQ-NFR-005 | USER0001 (type U) | any /api/transaction-types call; open UI page | 403 `{code:FORBIDDEN, message:"No access - Admin Only option... "}`; UI shows the same message | IT nonAdminRejected; WebMvc nonAdminIsForbidden; UI RequireAdmin rejects non-admin users | Pass |
| TP-54 | REQ-EDGE-013 | No / bad token | GET list | 401 UNAUTHORIZED; UI redirects to login | IT unauthenticatedRejected; WebMvc unauthenticatedIsRejected; UI redirects anonymous users | Pass |
| TP-55 | REQ-NFR-001 | — | all endpoints | match OpenAPI in target-spec.md | WebMvc TransactionTypeControllerTest (9 tests) + IT suite | Pass |
| TP-56 | REQ-NFR-007 | Fresh PostgreSQL 15 container | Flyway V1+V2 | 7 types, 18 categories, `06 REVERAL` | IT flywaySeedMatchesCreadb21 (+ manual psql apply in schema-mapping.md) | Pass |
| TP-57 | REQ-NFR-008 | — | `mvn verify`, `npm test`, `npm run build` | all green | see Results | Pass |

## Results (run 2026-11-18, Java 17.0.20, Maven 3.9.9, Node 24, Docker 29)

| Suite | Command | Tests | Failures | Result |
|---|---|---|---|---|
| API unit + WebMvc (surefire) | `mvn verify` | 85 (Edits 43, Service 28, Controller 9, ExceptionHandler 5) | 0 | Pass |
| API integration (failsafe, Testcontainers PG 15) | `mvn verify` | 24 | 0 | Pass |
| UI component (Vitest) | `npm test` | 38 (edits 12, list 12, edit 11, RequireAdmin 3)* | 0 | Pass |
| UI type check + bundle | `npm run build` | — | 0 | Pass |

\* counts per file as reported by Vitest: 4 files, 38 tests.

## Not covered by automation

- Real DB2 -911 deadlock between two CICS tasks: simulated with a PostgreSQL row lock + `lock_timeout` (TP-24).
- Generic DB failure paths (TP-25) are unit-tested with mocked repository exceptions, not against a failing database.
- Manual browser walkthrough of the running UI against the API is not part of this matrix; component tests drive the same flows with a mocked API.
