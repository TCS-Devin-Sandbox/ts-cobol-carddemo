# Test plan and results — CTLI / CTTU modernisation

Suites: backend `mvn test` in `carddemo-trantype-api` (JUnit 5 / Mockito / MockMvc + `@SpringBootTest` with Testcontainers PostgreSQL 15); frontend `npm test` in `carddemo-trantype-ui` (Vitest + React Testing Library). Expected values are COBOL literals / SQLCODE branches from `docs/discovery-ctli-cttu.md`.

Abbreviations: **IT** = `TransactionTypeApiIntegrationTest`, **ST** = `TransactionTypeServiceTest`, **CT** = `TransactionTypeControllerTest`, **RT** = `TranTypeRulesTest`, **LP** = `TransactionTypeListPage.test.tsx`, **EP** = `TransactionTypeEditPage.test.tsx`, **AP** = `App.test.tsx`, **VT** = `validation.test.ts`.

## 1. Results summary (2026-11-18)

| Suite | Tests | Passed | Failed |
|---|---|---|---|
| Backend unit (RT, ST, CT) | 43 | 43 | 0 |
| Backend integration (IT, PostgreSQL 15 container) | 8 | 8 | 0 |
| Frontend (VT, MessageLine, LP, EP, AP) | 34 | 34 | 0 |
| Frontend lint / typecheck / build | — | pass | — |
| Manual smoke (docker compose + `mvn spring-boot:run` + curl) | 3 | 3 | 0 |
| Browser E2E (testing agent, Chrome, real API + PostgreSQL) | 13 scenarios | 13 | 0 |

## 2. Scenario matrix

| # | Scenario | Req | Preconditions | Input | Expected (source) | Actual | Test | P/F |
|---|---|---|---|---|---|---|---|---|
| T01 | First-page load | CTLI-001/002 | seed data (7 types) | GET, no cursor | rows 01-07, page 1, info 'Type U to update, D to delete any record' | as expected; hasNext=false | IT firstPageShowsSevenSeededRows, ST firstPageUsesLookaheadForHasNext, LP loads first page | P |
| T02 | Forward paging | CTLI-003/004 | seed, size 3 | F8 / cursor=nextCursor | 04-06, page 2, lookahead key = next cursor | as expected | IT forwardAndBackwardKeysetPaging, LP pages forward | P |
| T03 | Backward paging | CTLI-006 | on page 2 | F7 / cursor=firstKey BACKWARD | 01-03 ascending, page 1, next exists | as expected | IT forwardAndBackwardKeysetPaging, ST backwardPageIsReversedToAscending | P |
| T04 | First-page boundary | CTLI-007 | page 1 | F7 | 'No previous pages to display' | as expected | LP F7 on first page | P |
| T05 | Last-page boundary | CTLI-004/005 | last page | F8 | hasNext=false; 'No more pages to display' | as expected | IT (page 3 hasNext=false), ST lastPageHasNoNext, LP pages forward | P |
| T06 | Empty table | CTLI-008 | all rows deleted | GET | 'No records found for this search condition.' | as expected | IT emptyTable, ST emptyTableMessage, LP empty-table | P |
| T07 | Type / description filters | CTLI-009/010/011 | seed | typeFilter=3; descFilter=RE; ZZZ; X | 03; 03,05,06; 'No Records found for these filter conditions'; 'TYPE CODE FILTER,IF SUPPLIED MUST BE A 2 DIGIT NUMBER' | as expected | IT filters, RT typeFilterRules, ST filterWithNoMatchesMessage, LP validates the type filter | P |
| T08 | Invalid / multiple row actions | CTLI-013/014 | page shown | 'X'; U+D | 'Action code selected is invalid'; 'Please select only 1 action' | as expected | LP enforces single valid action code | P |
| T09 | Valid inline update | CTLI-015/017 | row 06 | U, desc REVERSAL, F10 | 'Update HIGHLIGHTED row. Press F10 to save' → 'HIGHLIGHTED row was updated', DB updated | as expected | IT inlineUpdate, LP updates a description inline | P |
| T10 | Blank inline update | CTLI-016 | row selected U | desc ' ' | 'Transaction Desc must be supplied.' | as expected | IT inlineUpdate, LP, RT | P |
| T11 | Invalid inline update | CTLI-016 | row selected U | 'R#V'; unchanged | 'Transaction Desc can have numbers or alphabets only.'; 'No change detected with respect to database values.' | as expected | IT inlineUpdate, ST inlineUpdateUnchanged, LP | P |
| T12 | Update of deleted row / deadlock | CTLI-018 | row missing; lock failure | PATCH | 404 'Record not found. Deleted by others ?'; 409 'Deadlock. Someone else updating ?' | as expected | IT inlineUpdate, ST inlineUpdateMissingRow / inlineUpdateDeadlock | P |
| T13 | Delete with no children | CTLI-019/020, CTTU-012 | children of 05 removed | D, F10 / F4, F4 | 'HIGHLIGHTED row deleted.Hit Enter to continue' / 'Delete successful.'; row gone | 204, row gone | IT deleteWithAndWithoutChildren, LP delete success, EP deletes | P |
| T14 | Delete with children (−532) | CTLI-021, CTTU-013 | 01 has categories | DELETE 01 | 409 'Please delete associated child records first:' | 409 FK_RESTRICT '…first:SQLCODE :-532'; row kept (also manual curl) | IT, CT foreignKeyRestrictIs409, LP, EP | P |
| T15 | Delete missing row (+100) | CTLI-022 | key absent | DELETE | 'Delete failed with message:SQLCODE :+100' | as expected | ST deleteMissingRow | P |
| T16 | Valid CTTU add | CTTU-005/006/009/010 | 08 absent | ENTER 8 → F5 → FEE → ENTER → F5 | 'No record found for this key in database', 'Press F05 to add. F12 to cancel', 'Enter new transaction type details.', 'Changes validated.Press F5 to save', 'Changes committed to database' | as expected; key padded 08; PUT 201 upsert | EP adds a new record, IT addEditAndDuplicate, ST saveInsertsWhenMissing | P |
| T17 | Duplicate-key add (−803) | CTTU-011 | 01 exists | POST 01 | 409 'Error inserting record into: TRANSACTION_TYPE Table. SQLCODE:-803' | as expected | IT addEditAndDuplicate, CT duplicateKeyIs409 | P |
| T18 | Edit existing key | CTTU-004/008/010 | 01 exists | ENTER 01, change desc, F5 | 'Update transaction type details shown.'; unchanged → 'No change detected with respect to values fetched.'; save 200 | as expected | EP edits an existing record, IT addEditAndDuplicate, ST saveUpdatesExisting / saveUnchangedIsRejected | P |
| T19 | Edit nonexistent key | CTTU-005 | 42 absent | GET 42 | 404 'No record found for this key in database' | as expected | IT addEditAndDuplicate, ST getNotFound, EP | P |
| T20 | Key edits | CTTU-002/003 | — | ''; AB; 00; 1 | 'No input received'; 'Tran Type code must be numeric.'; 'Tran Type code must not be zero.'; '01' | as expected | EP prompts for key, RT, VT, CT createValidationUsesSourceMessageKeyFirst | P |
| T21 | Description edits | CTTU-007 | create/edit mode | ''; 'F$E'; 51 chars | 'Transaction Desc must be supplied.'; '…can have numbers or alphabets only.'; length rule | as expected | RT, EP, CT createDescriptionValidation | P |
| T22 | Write lock failure (−911) | CTTU-011 | lock failure | F5 | 'Could not lock record for update', info 'Changes unsuccessful' | as expected | ST saveLockFailureMapsToCouldNotLock, EP save failure | P |
| T23 | F12 cancel | CTTU-014 | confirm-save / confirm-delete | F12 | 'Update was cancelled' (original restored); 'Delete was cancelled' | as expected | EP edits…F12, EP deletes…cancel | P |
| T24 | Non-admin rejection | CTLI-027, CTTU-018 | user role USER / no token | GET | 403 'Access restricted to administrators' / 401; UI redirects to login | as expected | IT nonAdminRejected, CT unauthenticatedIs401 / nonAdminIs403, AP | P |
| T25 | Return-to-menu flow | CTLI-025, CTTU-016 | list / maintain page | F3 | to admin menu (CA00) with 'PF03 pressed. Exiting'; CTTU from list → back to list | as expected | LP validates…F2/F3, EP F3 tests, AP admin menu options 5 and 6 | P |
| T26 | F2 Add | CTLI-024 | list | F2 | CTTU screen | as expected | LP F2 opens the add screen | P |
| T28 | Key changed after not-found lookup | CTTU-005/009 | 08 not found | change key to 01 | back to key entry; F5 not offered until 01 is fetched (prevents upsert overwrite) | as expected | EP changing the key after a failed lookup | P |
| T29 | Re-login with stale token | CTTU-018 | expired session stored | valid credentials | login request has no bearer; new session issued | as expected | AP login does not send a stale bearer token | P |
| T27 | Message lines | CTLI-026 | — | — | info centred, error red | as expected | MessageLine.test | P |

Not automated: REQ-CTLI-012 (filter change restarts at page 1) is covered by LP code path `enter()` → `load({page:1})`, verified by IT filters; REQ-CTLI-023 (F10 only when pending) is enforced by the disabled `F10=Save` button; REQ-CTLI-028 / CTTU-017 (DB unavailable) map to 500 `DB_ERROR` via `ApiExceptionHandler.dataAccess` (no automated test).

## 3. Requirements traceability matrix

| Requirement | Design (target-spec) | Implementation | Tests | Status |
|---|---|---|---|---|
| REQ-CTLI-001..008 | §2 GET paging contract | `TransactionTypeService.page`, `TransactionTypeRepositoryCustomImpl`, `TransactionTypeListPage` | T01-T06 | Pass |
| REQ-CTLI-009..012 | §2 validation table | `TranTypeRules.typeFilter/descFilter`, `countMatching` | T07 | Pass |
| REQ-CTLI-013..016 | §3 list page | `TransactionTypeListPage.enter/save`, `validation.ts` | T08-T11 | Pass |
| REQ-CTLI-017..018 | PATCH | `TransactionTypeService.updateDescription` | T09, T12 | Pass |
| REQ-CTLI-019..022 | DELETE | `TransactionTypeService.delete`, `ApiExceptionHandler.integrity` | T13-T15 | Pass |
| REQ-CTLI-023 | §3 list page | `F10=Save` disabled unless pending | manual / code | Pass (by design) |
| REQ-CTLI-024..026 | §3 | list page buttons, `MessageLine` | T25-T27 | Pass |
| REQ-CTLI-027, REQ-CTTU-018 | §1 | `SecurityConfig`, `RequireAdmin` | T24 | Pass |
| REQ-CTLI-028, REQ-CTTU-017 | §2 (500) | `ApiExceptionHandler.dataAccess` | — | Implemented, not automated |
| REQ-CTTU-001..003 | §3 edit page, §2 validation | `TransactionTypeEditPage`, `TranTypeRules`, `@TranTypeCode` | T20 | Pass |
| REQ-CTTU-004..006 | GET, §3 | `TransactionTypeService.get`, edit page states | T16, T18, T19 | Pass |
| REQ-CTTU-007..010 | PUT | `TransactionTypeService.save`, `@TranTypeDescription` | T16, T18, T21 | Pass |
| REQ-CTTU-011 | POST/PUT 409 | `create`, `ApiExceptionHandler`, lock mapping | T17, T22 | Pass |
| REQ-CTTU-012..013 | DELETE | `delete`, edit page F4 | T13, T14 | Pass |
| REQ-CTTU-014..016 | §3 | edit page F12/F3, buttons per state | T23, T25 | Pass (CTTU-015 see deviation D1) |
| NFR-001..004 | §1-2 | controller, security | T01-T24 | Pass |
| NFR-005 | §2 lock mapping | `PessimisticLockingFailureException` handling | T12, T22 | Pass |
| NFR-006 | — | PK-index keyset queries | not measured | Not measured |
| NFR-007 | schema-mapping | `db/migration` | IT (Flyway migrate each test) | Pass |
| NFR-008 | §3 | `carddemo-trantype-ui` | LP, EP, AP | Pass |
