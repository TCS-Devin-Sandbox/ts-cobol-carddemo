# Test plan and results — CTLI / CTTU modernisation

Expected results are taken from the COBOL literals and SQLCODE branches (`docs/discovery-ctli-cttu.md` §5–6). Seed data = `V2__seed_data.sql` (types 01–07, every type has categories).

## Suites and results (run 2026-11-18)

| Suite | Command | Tool | Result |
|---|---|---|---|
| API unit | `cd carddemo-trantype-api && ./mvnw verify` (surefire) | JUnit 5 + Mockito, `@WebMvcTest` | 46 run, 0 failed |
| API integration | same (failsafe, `*IT`) | `@SpringBootTest` + Testcontainers `postgres:15-alpine`, Flyway V1+V2 | 14 run, 0 failed |
| UI component | `cd carddemo-trantype-ui && npm test` | Vitest + React Testing Library (jsdom) | 40 run, 0 failed |
| UI type-check / build | `npm run build` | tsc + Vite | success |
| Migration smoke | `psql` on PostgreSQL 15: V1, V2, `DELETE … WHERE tr_type='01'` | PostgreSQL 15 | 7 + 18 rows; delete rejected by `fk_trc_type_code` |
| Stack smoke | `docker compose up -d`, `./mvnw spring-boot:run`, `npm run dev`, curl via Vite proxy (csrf → login → list → delete 01 → get 99) | curl | 200 page of 7; 409 `CHILD_RECORDS_EXIST`; 404 'No record found for this key in database' |

Test names: `UT:` = `TransactionTypeServiceTest` / `TranTypeCodesTest`, `MVC:` = `TransactionTypeControllerTest`, `IT:` = `TransactionTypeApiIT` / `AuthSessionIT`, `UI:` = Vitest test titles (`TransactionTypeListPage.test.tsx`, `TransactionTypeEditPage.test.tsx`, `AdminAndAuth.test.tsx`, `messages.test.ts`).

## Test matrix

| TC | REQ | Precondition | Input | Expected (source) | Actual | Automated by | Pass/Fail |
|---|---|---|---|---|---|---|---|
| TC-001 | REQ-CTLI-001, -007, -023 | seed data | open list (no cursor) | rows 01–07 ascending, Page 1, info 'Type U to update, D to delete any record' | as expected | IT firstPageOfSeedData; UT firstPageReadsSevenRowsWithLookAhead; UI "loads first page of 7 rows…" | Pass |
| TC-002 | REQ-CTLI-002, -003, -007 | 16 types (01–16) | F8 from page 1, then F8 | page 2 = 08–14 (`>=` look-ahead key 08), page 3 = 15–16, page numbers 2/3 | as expected | IT pagesForwardAndBackwardSevenAtATime; UI "pages forward…" | Pass |
| TC-003 | REQ-CTLI-004 | 16 types, on page 3 / 2 | F7 | page 2 = 08–14 (`< 15 DESC` reversed), page 1 = 01–07 | as expected | IT pagesForwardAndBackwardSevenAtATime; UT backwardReadsDescendingAndReverses; UI "pages forward…backward" | Pass |
| TC-004 | REQ-CTLI-005, REQ-EDGE-002 | page 1 | F7 | 'No previous pages to display' | as expected | UI "F7 on first page shows no previous pages" | Pass |
| TC-005 | REQ-CTLI-006, REQ-EDGE-003 | exactly 7 rows / last page | F8, F8 | hasNext false (look-ahead +100); 'No more pages for these search conditions' then 'No more pages to display' | as expected | UT exactlySevenRowsHasNoNextPage, forwardFromCursorIsInclusive; UI "physical F8 key is bound", "pages forward…" | Pass |
| TC-006 | REQ-CTLI-008, REQ-EDGE-001 | empty table | open list | 0 rows, 'No records found for this search condition.', no next/prev | as expected | IT emptyTable; UT emptyTableReturnsSourceMessage; UI "empty table shows source message" | Pass |
| TC-007 | REQ-CTLI-009, REQ-EDGE-007 | seed | type filter `5`, `5x`, `ab`, `123` | 400 'TYPE CODE FILTER,IF SUPPLIED MUST BE A 2 DIGIT NUMBER' | as expected | IT filters; UT invalidTypeFilterUsesSourceLiteral; UI "invalid type filter rejected…" | Pass |
| TC-008 | REQ-CTLI-009 | seed | type filter `03`; `00`/blank | only 03; `00`/blank = no filter | as expected | IT filters; UT typeFilterExactMatch, zeroOrBlankTypeFilterMeansNoFilter | Pass |
| TC-009 | REQ-CTLI-010 | seed | desc filter `RE`; `re` | CREDIT, REFUND, REVERAL (`LIKE '%RE%'`); lower case no match (case-sensitive) | as expected | IT filters; UT descFilterWrappedInPercent | Pass |
| TC-010 | REQ-CTLI-011 | seed | filter with no match | 'No Records found for these filter conditions' | as expected | IT filters; UT filterWithNoMatchReturnsFilterMessage | Pass |
| TC-011 | REQ-CTLI-012 | page loaded | change filters + ENTER | reload page 1 (no cursor) with filters | as expected | UI "filter change reloads page 1 with filters" | Pass |
| TC-012 | REQ-CTLI-013 | page loaded | select `X` + ENTER | 'Action code selected is invalid' | as expected | UI "rejects invalid action code and more than one action" | Pass |
| TC-013 | REQ-CTLI-014 | page loaded | `U` row 1 + `D` row 2 | 'Please select only 1 action' | as expected | UI same test | Pass |
| TC-014 | REQ-CTLI-015 | page loaded | `D` row 7 + ENTER | info 'Delete HIGHLIGHTED row ? Press F10 to confirm', filters disabled, no delete yet | as expected | UI "delete requires F10 confirmation…" | Pass |
| TC-015 | REQ-CTLI-016 | type without categories | F10 / `DELETE` | 204; 'HIGHLIGHTED row deleted.Hit Enter to continue'; page 1; second delete 404 'Delete failed with message:' | as expected | IT deleteWithoutChildren; UT deleteExistingRow, deleteMissingRowFails; UI "delete requires F10…" | Pass |
| TC-016 | REQ-CTLI-017, REQ-EDGE-004 | type 01 has 5 categories | delete 01 | 409 −532 'Please delete associated child records first:'; CTLI line 'Please delete associated child records first: SQLCODE: -532'; row and children kept | as expected | IT deleteWithChildrenRejectedAndRowKept; MVC foreignKeyViolationMapsToChildRecordsMessage; UI "delete with children shows -532 message"; UI messages test | Pass |
| TC-017 | REQ-CTLI-018 | page loaded | `U` row 2 + ENTER | only row 2 description editable; 'Update HIGHLIGHTED row. Press F10 to save' | as expected | UI "inline update validates then saves…" | Pass |
| TC-018 | REQ-CTLI-019, REQ-EDGE-006 | pending update | blank description + F10 | 'Transaction Desc must be supplied.' (no SQL) | as expected | UI inline update test; IT invalidInputsRejectedWithSourceText; MVC blankDescriptionUsesSourceLiteral | Pass |
| TC-019 | REQ-CTLI-019, REQ-EDGE-007 | pending update | unchanged / `BAD-DESC` + F10 | 'No change detected with respect to database values.' / 'Transaction Desc can have numbers or alphabets only.' | as expected | UI inline update test; MVC nonAlphanumericDescriptionUsesSourceLiteral | Pass |
| TC-020 | REQ-CTLI-020 | pending update | valid description + F10 | `PUT ?upsert=false` 200; 'HIGHLIGHTED row was updated' | as expected | UI inline update test; IT upsertUpdatesThenInserts | Pass |
| TC-021 | REQ-CTLI-020 | row deleted by others | F10 update | 404; 'Record not found. Deleted by others ? SQLCODE:  100'; no insert | as expected | IT ctliUpdateDoesNotInsert; UT ctliUpdateWithoutRowIsDeletedByOthers; UI "update of a row deleted by others…" | Pass |
| TC-022 | REQ-CTLI-021 | list | F2 | maintenance page (CTTU initial state) | as expected | UI "F2 goes to CTTU…" | Pass |
| TC-023 | REQ-CTLI-022 | list | F3 | admin menu `/admin` (CA00) | as expected | UI "F3 exits to admin menu" | Pass |
| TC-024 | REQ-CTLI-024 | list | other F-key (F5) | treated as ENTER (refresh from first key) | as expected | UI "keys other than ENTER/F2/F3/F7/F8/F10 act as ENTER" | Pass |
| TC-025 | REQ-CTLI-025 | DB unavailable | any call | 500 'Error accessing: TRANSACTION_TYPE table. SQLCODE:' on error line (D-09) | as expected | MVC databaseUnavailableMapsTo500WithSourceText, unexpectedErrorIs500; UI "database failure is shown…" | Pass |
| TC-030 | REQ-CTTU-001, -017, -018 | open CTTU | — | 'Enter transaction type to be maintained'; ENTER/F3 only; key editable, desc protected, maxlength 2 | as expected | UI "initial state prompts for key…" | Pass |
| TC-031 | REQ-CTTU-002, REQ-EDGE-006/007 | initial | blank, `ab`, `0` | 'Tran Type code must be supplied.' / '…must be numeric.' / '…must not be zero.' | as expected | UI "type code edits use source literals"; UT TranTypeCodesTest; MVC invalidPathKeyUsesSourceLiterals; IT invalidInputsRejectedWithSourceText | Pass |
| TC-032 | REQ-CTTU-002 | initial | `1` / `5` | zero-padded `01` / `05` | as expected | UT validCodesAreZeroPadded, getPadsKeyAndReturnsRow; IT getExistingAndMissing; UI "existing key shows details, zero-padded…" | Pass |
| TC-033 | REQ-CTTU-003 | seed | key `01` + ENTER | PURCHASE shown; key protected, desc editable; ENTER/F3/F4/F12 | as expected | UI "existing key shows details…"; IT getExistingAndMissing | Pass |
| TC-034 | REQ-CTTU-004, REQ-EDGE-009 | seed | key `42`/`99` + ENTER | 404 'No record found for this key in database'; info 'Press F05 to add. F12 to cancel'; ENTER/F3/F5/F12 | as expected | IT getExistingAndMissing; UT getMissingKeyIsNotFound; UI "non-existent key then add via F5" | Pass |
| TC-035 | REQ-CTTU-005 | not-found state | F5 | 'Enter new transaction type details.'; key protected | as expected | UI "non-existent key then add via F5" | Pass |
| TC-036 | REQ-CTTU-006 | show / create | blank, `PURCHASE*` | 'Transaction Desc must be supplied.' / '…can have numbers or alphabets only.'; info 'Update transaction type details shown.' | as expected | UI "edit: no change, invalid…", "non-existent key then add…" | Pass |
| TC-037 | REQ-CTTU-007 | show 01 | `purchase ` + ENTER | 'No change detected with respect to values fetched.' | as expected | UI "edit: no change…" | Pass |
| TC-038 | REQ-CTTU-008 | show 01 | `PURCHASES` + ENTER | 'Changes validated.Press F5 to save'; all protected; ENTER/F3/F5/F12 | as expected | UI "edit: no change…" | Pass |
| TC-039 | REQ-CTTU-009 | validated | F5 | `PUT upsert=true` → UPDATE 200; 'Changes committed to database'; next ENTER → blank search screen | as expected | UI "edit: …saved (upsert)"; IT upsertUpdatesThenInserts; UT upsertUpdatesExistingRow | Pass |
| TC-040 | REQ-CTTU-009 | key 42/50 absent | F5 (create) / POST | UPDATE +100 → INSERT, 201 | as expected | IT upsertUpdatesThenInserts, addThenDuplicate; UT upsertInsertsWhenUpdateFindsNoRow, createInsertsTrimmedValue; UI "non-existent key then add via F5" | Pass |
| TC-041 | REQ-CTTU-010, REQ-EDGE-005 | key 08 exists | POST 08 / insert race | 409 −803; 'Error inserting record into: TRANSACTION_TYPE Table. SQLCODE: -803:'; info 'Changes unsuccessful'; original row unchanged | as expected | IT addThenDuplicate; MVC duplicateKeyMapsToInsertMessage; UI "duplicate key on insert shows -803 message" | Pass |
| TC-042 | REQ-CTTU-011 | lock timeout | F5 | 409 −911 'Could not lock record for update' | as expected | MVC lockFailureMapsToMinus911 | Pass |
| TC-043 | REQ-CTTU-012 | show 01 | F4, F4 | 'Delete this record ? Press F4 to confirm' (F3/F4/F12, ENTER hidden), then 'Delete successful.' | as expected | UI "delete confirm with F4, success" | Pass |
| TC-044 | REQ-CTTU-013, REQ-EDGE-004 | 01 has children | F4, F4 | 'Please delete associated child records first:SQLCODE : -532:' | as expected | UI "delete with children shows -532 message"; IT deleteWithChildrenRejectedAndRowKept | Pass |
| TC-045 | REQ-CTTU-014 | validated / confirm-delete | F12 | 'Update was cancelled' + original values / 'Delete was cancelled' + blank screen | as expected | UI "F12 after validation backs out the update", "F12 on delete confirm cancels" | Pass |
| TC-046 | REQ-CTTU-015, -017, -018 | initial | F4 key | 'Invalid key pressed'; button sets and read-only states per state | as expected | UI "key invalid for the state is rejected" + button/readonly assertions in TC-030/033/038/043 tests | Pass |
| TC-047 | REQ-CTTU-016 | entered from list / menu | F3 | `/transaction-types` / `/admin` | as expected | UI "F3 returns to the list…", "F3 returns to the admin menu otherwise" | Pass |
| TC-050 | REQ-NAV-001 | admin signed on | option 5 / 9 | list page / 'Please enter a valid option number...' | as expected | UI "lists options 5 and 6…", "rejects invalid option" | Pass |
| TC-051 | REQ-SEC-001, REQ-EDGE-008 | user role USER | list call / page | 403 'Access restricted to administrators'; UI shows access denied | as expected | IT nonAdminAndAnonymousRejected, AuthSessionIT; MVC nonAdminIsForbidden; UI "non-admin user is rejected" | Pass |
| TC-052 | REQ-SEC-001 | anonymous | list call / page; wrong password | 401; redirect to `/login`; 'Wrong Password. Try again ...'; CSRF required | as expected | AuthSessionIT sessionLoginFlow; MVC anonymousIsUnauthorized, missingCsrfTokenIsForbidden; UI "anonymous user is redirected…", "sign-on shows wrong password message" | Pass |
| TC-060 | REQ-NFR-008 | — | `./mvnw verify`, `npm test`, `npm run build` | all green | all green | CI-equivalent local run | Pass |
| TC-061 | REQ-NFR-007 | empty DB | Flyway start | 7 types, 18 categories, FK RESTRICT | as expected | IT seedDataLoadedByFlyway; migration smoke | Pass |
| TC-062 | REQ-NFR-003 | — | size=8 | 400 (max 7); no OFFSET queries | as expected | UT sizeAboveSevenRejected | Pass |
| TC-063 | REQ-NFR-001, -002, -004 | full stack | curl through Vite proxy | session login, JSON contract, status codes per §3.3 | as expected | stack smoke (manual) | Pass |

Return-to-menu flow (CTLI F3 → admin, CTTU F3 → caller) is TC-023 / TC-047; non-admin rejection is TC-051.

## Not covered by automation

- Real −911 lock contention against PostgreSQL (mapped and unit-tested via `CannotAcquireLockException`).
- Browser-driven end-to-end run of the React pages against the live API (component tests use a mocked API client; the API contract is covered by integration tests).
- REQ-NFR-006 response-time target (p95 < 500 ms) was not load-tested.
