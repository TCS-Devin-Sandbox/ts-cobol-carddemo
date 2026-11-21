# E5 — credit card list and view (CCLI / CCDL)

Read-only migration of `app/cbl/COCRDLIC.cbl` and `COCRDSLC.cbl`, with
`app/bms/COCRDLI.bms`, `COCRDSL.bms` and their `app/cpy-bms` maps as the
presentation contract. No estate sources, copybooks or golden files change.

## Contract

All endpoints require the existing E2 bearer token; both ADMIN and USER can
use them. Main-menu options 03 and 04 route to `/cards` and `/cards/view`.
PF3 from list/direct view returns to the main menu; PF3 from a selected detail
restores the list's page and filters. Detail keys are read-only when selected
from list. There is no account claim and no new authorization policy.

* `GET /api/cards?accountId=&cardNumber=&key=ENTER`: initial/search page,
  seven rows ordered by card number, exact optional filters combined with AND.
  All-zero full-width filters are absent. Account validation precedes card
  validation. Response: `rows` (accountId, cardNumber, activeStatus), `firstKey`,
  `nextCursor`, `pageNumber`, `lastPageShown`, `message`, `info`.
* PF8: send `key=PF8`, the current `pageNumber`, `nextCursor` as `cursor`,
  `firstKey`, `lastPageShown`, and the filters. PF7: send `key=PF7`, current
  `pageNumber`, current `firstKey` as `cursor`, and filters. The boundary
  requests retain the current rows and return the COBOL top/bottom messages.
  Clients must carry this paging state, as the terminal COMMAREA did.
* `POST /api/cards/select` with `{"selections":[{"action":"S",
  "accountId":"10000000001","cardNumber":"4000000000000001"}]}` accepts
  up to seven row actions. Blank means no action; uppercase S views, U reports
  `This option is not installed ...` (E2 convention, pending E8). Multiple S/U
  actions take precedence over invalid-action errors. Lowercase is invalid.
  The UI validates filters before submitting row selections, in source order.
* `GET /api/cards/view?accountId=10000000001&cardNumber=4000000000000001`:
  both inputs required, exact source validation/not-found messages, returning
  accountId (the submitted search value), cardNumber, embossedName,
  expirationMonth, expirationYear and activeStatus. No CVV/filler is exposed.
  An initial direct-entry screen prompts without issuing a lookup.

Validation errors are HTTP 400, a missing detail is 404, database failures
are 500. Message strings preserve COBOL case, punctuation and spelling.
Database failures use the source's `Error reading Card Data File` text;
CICS RESP/RESP2 diagnostics and terminal abends have no PostgreSQL/HTTP
equivalent and are not fabricated or sent to the browser. Empty search
results are successful pages with the source's no-records message.

## Data and parallel-stream coordination

Reuses the existing shared VSAM `card` table, not a duplicate `cards` table.
Its business-name mapping already exists in [schema-mapping-vsam.md](../schema-mapping-vsam.md).
No schema migration, numbering reservation, or changes to shared schema types
are necessary. Neither reachable program reads `card_xref` or `account`.
`9150-GETCARD-BYACCT` is defined but never performed; `9000-READ-DATA` performs
only `9100-GETCARD-BYACCTCARD` (COCRDSLC 726–730).

The dev-only `CardDevData` ApplicationRunner follows E2's insert-if-absent
approach: sixteen synthetic cards, two synthetic account IDs, one inactive
card, text expiry `2030-12-31`. It never overwrites loaded records and does
not invent account/xref rows that these programs do not read. It does not run
without the dev profile. Integration tests use isolated Testcontainers data.
`git fetch origin` and `gh pr list --state open` were checked before work;
no open E3/E4/E6/E11 PR was visible then. Reusing the master schema avoids
competing with those streams regardless.

## Reproduced quirks / later deviation candidates

Following MIGRATION-PLAN's default, reproduce and log rather than fix:

1. **Security-relevant: unrestricted filters for every user type.**
   `COCRDLIC` `2210-EDIT-ACCOUNT` (1003–1032) copies caller input to COMMAREA,
   including clearing it when absent. `9500-FILTER-RECORDS` (1382–1409) tests
   only those input filters, not identity/user type. ADMIN and USER therefore
   see the same cards. COMMAREA account is navigation state, not an account
   ownership claim. Candidate for a later **owner-approved security deviation**;
   do not treat this migration as an account-ownership security boundary.
2. **Security-relevant: detail lookup ignores a valid supplied account.**
   `COCRDSLC` `9100-GETCARD-BYACCTCARD` (735–775), especially the commented
   account-key assignment at 739 and card-number READ at 740–749, looks up only
   card number. `1200-SETUP-SCREEN-VARS` (462–486) displays the input account,
   not the stored card account. A valid-but-mismatched account still returns
   card details. Candidate for a later **owner-approved security deviation**.
   This was explicitly confirmed as the intended migration behavior.
3. **Unfiltered forward lookahead.** `9000-READ-FORWARD` (1192–1220) reads
   one physical record beyond the seventh displayed row without applying
   `9500-FILTER-RECORDS`. `nextCursor` may therefore exist even though no
   further rows match; the next page can be empty. Do not replace this with
   a filtered count/offset paginator.
4. **Two-stage last-page indication.** `1400-SETUP-MESSAGE` (895–922): a
   short initial page reports `NO MORE RECORDS TO SHOW`; first PF8 at that
   boundary marks last-page-shown, subsequent PF8 reports
   `NO MORE PAGES TO DISPLAY`. PF7 at page 1 reports
   `NO PREVIOUS PAGES TO DISPLAY`. Arrival at the final page via PF8 already
   marks it shown. A non-PF8 key clears that flag (406–416).
5. **Unsupported function keys act as Enter**, not a new validation message:
   CCLI 370–380 and CCDL 291–299 remap invalid AID keys to Enter.
6. **Text expiry and zero filters.** No calendar validation or active-status
   filtering is added. CCDL splits the stored text into month/year
   (`1200-SETUP-SCREEN-VARS`, 477–484); CVV/day are not displayed.
   Full-width zero list filters are absent, but required detail zeros are
   missing input (`2210-EDIT-ACCOUNT`, `2220-EDIT-CARD`).

Web adaptations: HTTP authentication replaces CICS session existence, fixed
fields become accessible labelled controls, red error/message lines replace
terminal attributes, and navigation state replaces COMMAREA. No terminal
buffer, CICS ABEND or unreachable alternate-index lookup is emulated.

## Paragraph → test traceability

API tests: `carddemo-online-api/src/test/java/com/carddemo/card/`.
UI tests: `carddemo-online-ui/src/pages/CardPages.test.tsx`.
Display names include source paragraph names; `*-EXIT` belongs to its parent
paragraph, not a separate behavior. Program-ID/date declarations are not
executable paragraphs. Shared E2 header/function-key component tests still run.

| Program / paragraph | Tests (method or describe/it name prefix) |
|---|---|
| CCLI `0000-MAIN`, `COMMON-RETURN` | API `authenticationAndMenuRoutes`, `selections`; UI `0000-MAIN / COMMON-RETURN` (S → detail → PF3); guard/menu tests |
| CCLI `1000-SEND-MAP`, `1100-SCREEN-INIT` | UI `0000-MAIN / 1000-SEND-MAP...`: header, initial focus, 7 rows, both types |
| CCLI `1200-SCREEN-ARRAY-INIT`, `1250-SETUP-ARRAY-ATTRIBS`, `1300-SETUP-SCREEN-ATTRS`, `1500-SEND-SCREEN` | Same UI test: editable filters and row controls; filter-error/empty-results UI test |
| CCLI `1400-SETUP-MESSAGE` | API `browseForwardBackwardAndBoundaries`, `shortInitialPageBottomRequiresSecondPf8`; UI `9000-READ-FORWARD...` |
| CCLI `2000-RECEIVE-MAP`, `2100-RECEIVE-SCREEN`, `2200-EDIT-INPUTS`, `2210-EDIT-ACCOUNT`, `2220-EDIT-CARD` | API `optionalFiltersAndValidationPriority`; UI `2000-RECEIVE-MAP...` |
| CCLI `2250-EDIT-ARRAY` | API `selections` (S/U/blank/multiple/invalid/lowercase); UI `2250-EDIT-ARRAY...` |
| CCLI `9000-READ-FORWARD`, `9100-READ-BACKWARDS` | API `browseForwardBackwardAndBoundaries`, `filtersAndRawLookahead`, empty-table validation test; UI paging test; controller file-failure test |
| CCLI `9500-FILTER-RECORDS` | API `filtersAndRawLookahead` (AND, mismatch, both user types, raw lookahead) |
| CCDL `0000-MAIN`, `COMMON-RETURN` | API authentication/menu test; UI direct entry, selected entry, PF3/menu/guard tests |
| CCDL `1000-SEND-MAP`, `1100-SCREEN-INIT`, `1200-SETUP-SCREEN-VARS`, `1300-SETUP-SCREEN-ATTRS`, `1400-SEND-SCREEN` | UI `0000-MAIN / 1000-SEND-MAP...` and selected detail test: editable vs protected keys, prompt, fields; API `detailFieldsAndMismatchedAccountQuirk` |
| CCDL `2000-PROCESS-INPUTS`, `2100-RECEIVE-MAP`, `2200-EDIT-MAP-INPUTS`, `2210-EDIT-ACCOUNT`, `2220-EDIT-CARD` | API `detailValidation`; UI `2000-PROCESS-INPUTS...` |
| CCDL `9000-READ-DATA`, `9100-GETCARD-BYACCTCARD` | API `detailFieldsAndMismatchedAccountQuirk`, `detailValidation`; UI `9000-READ-DATA...`: fields, mismatch, stale-field clearing; controller file-failure test |
| CCDL `9150-GETCARD-BYACCT` | `CardSourceReachabilityTest`: verifies it is uncalled, so no invented xref/account lookup |
| Both `SEND-PLAIN-TEXT`, `SEND-LONG-TEXT`; CCDL `ABEND-ROUTINE` | `CardSourceReachabilityTest` distinguishes uncalled helpers from reachable diagnostic paths; `CardControllerTest.databaseFailureDoesNotLeakInfrastructure` and UI file-failure test cover HTTP diagnostic adaptation, not a literal CICS abend |

Run `mvn -B verify` in the API and `npm run lint && npm run typecheck &&
npm test && npm run build` in the UI. Root `make up && make build && make
parity` checks the unchanged batch baseline, not online CICS runtime parity.
