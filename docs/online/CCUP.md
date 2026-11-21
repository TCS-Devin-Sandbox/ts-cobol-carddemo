# E8 — card update (CCUP)

Port of `app/cbl/COCRDUPC.cbl`, `app/bms/COCRDUP.bms` and
`app/cpy-bms/COCRDUP.CPY`. No estate files or golden outputs change.

## Contract

Both E2 USER and ADMIN bearer tokens can update cards. Main-menu option 05
and the CCLI uppercase U action open `/cards/update`. PF3 returns to the
calling list (restoring filters/page), or to the main menu for direct entry.
Initial entry prompts `Please enter Account and Card Number` without a read.

* `GET /api/cards/update?accountId=10000000001&cardNumber=4000000000000001`
  fetches the screen: `token`, `accountId`, `cardNumber`, `fields` containing
  `embossedName`, `activeStatus`, `expirationMonth`, `expirationYear`, plus
  `message` and `confirm`. Keys become protected after retrieval. CVV, filler
  and expiry day are never editable or exposed.
* `POST /api/cards/update` accepts `token`, `fields` and `key` (`ENTER`, `PF5`,
  `PF12`). ENTER validates without writing, returning
  `Changes validated.Press F5 to save`. Confirmation protects editable fields.
  Further ENTER retains the pending values. PF5 commits **the server-held
  candidate**, not a replacement supplied in the confirmation request.
* PF12 discards changes and re-reads the selected record. PF5 before
  confirmation, and PF12 before a fetch, act as ENTER (never save). On success:
  `Changes committed to database`.
  The next process action resets direct-entry keys or returns to CCLI.
* No-change returns `No change detected with respect to values fetched.` and
  does not enable confirmation. Invalid inputs return HTTP 400 with the exact
  source message in `message`. Missing records use
  `Did not find cards for this search condition`. Infrastructure details are
  never returned; failed writes use `Update of record failed` and lock errors
  use `Could not lock record for update`.

The opaque random token is bound to the authenticated subject, expires after
30 minutes, and lives only in API memory (restart requires a new fetch).
There is no new authorization policy or account ownership claim. The shared
`card` table and business-name columns are reused with no migration.
The atomic conditional UPDATE compares the originally fetched normalized
name, CVV, entire expiry and status; zero affected rows returns
`Record changed by some one else. Please review` and refreshes the old values.
Account ID and filler are deliberately excluded from that comparison, just
as in 9300. The submitted account ID is written, not verified against a master.

## Source quirks and unresolved runtime evidence

Line numbers below refer to `COCRDUPC.cbl` unless stated otherwise.

* **Account is syntactic only**: 1376–1413 reads by card alone. No account,
  xref, or association lookup executes. 1462 writes the submitted account ID;
  a valid but different account can therefore reassign the card.
* **Defined messages are not necessarily used**: 189–194 define friendly
  key errors, but 721–800 actually use the uppercase CCLI-style filter errors.
  Blank/all-zero keys mean absent; both absent produce `No input received`
  (650–660). An asterisk is the BMS missing-value sentinel (589–610).
* **No calendar/future-date validation**: 99 permits 1950–2099, and
  877–945 checks month/year independently. The old hidden protected day
  (BMS EXPDAY, 142–148; COBOL 1120–1123) survives. February 31 is possible;
  `expiration_date` is intentionally CHAR(10), not SQL DATE.
* **Case-insensitive change detection**: 679–687 compares uppercased data.
  1356–1359 and 1499–1502 uppercase fetched names, while name validation
  allows both cases (806–839). A case-only edit is not a change. Status is
  Y/N, not arbitrary truthy input. Field protection follows 1168–1209.
* **No automatic save**: 948–1028 waits for PF5; 414–424 remaps invalid
  keys to ENTER. No literal `changes not saved` message occurs in this source:
  the unsaved state is represented by `Changes validated.Press F5 to save`.
* **Filler reset**: 1460 initializes the replacement record and never fills
  its filler. The SQL replacement blanks filler rather than preserving it.
* **CVV runtime evidence needed**: 587 initializes `CCUP-NEW-DETAILS`, whose
  CVV is PIC X(3) (306), to spaces; no reachable MOVE populates that CVV.
  1464–1465 reinterprets it as PIC 9(3), then writes a numeric field. This is
  invalid zoned numeric data, not an editable CVV. PostgreSQL's existing
  NUMERIC column cannot hold three spaces. **This implementation retains the
  old numeric CVV pending owner evidence/decision**, rather than guessing a
  zero conversion or changing the shared schema. Exact invalid-data runtime
  equivalence is not claimed. A mainframe before/after CARDDAT record for an
  actual CCUP save (and compiler numeric options) would resolve this.

## Paragraph → test traceability

`API` below is `CardUpdateIntegrationTest`, `HTTP` is
`CardUpdateControllerTest`, and `UI` is `CardUpdatePage.test.tsx`.
Exit-only paragraphs are covered by their parent paragraph. CICS terminal
attributes are represented by HTML field protection and shared screen chrome;
RESP/RESP2 codes, SYNCPOINT, SEND/RETURN and abends have no literal HTTP form.

| Paragraph | Tests / treatment |
|---|---|
| 0000-MAIN, COMMON-RETURN | HTTP authenticationAndValidation; UI initial/render and PF3 |
| 1000-PROCESS-INPUTS, 1100-RECEIVE-MAP | UI initial/render and validation/confirmation |
| 1200-EDIT-MAP-INPUTS | API validationAndNoChanges; UI validation/confirmation |
| 1210-EDIT-ACCOUNT, 1220-EDIT-CARD | API keysAndMissingRecord |
| 1230-EDIT-NAME, 1240-EDIT-CARDSTATUS | API validationAndNoChanges |
| 1250-EDIT-EXPIRY-MON, 1260-EDIT-EXPIRY-YEAR | API validationAndNoChanges, confirmationAndCancel |
| 2000-DECIDE-ACTION | API confirmationAndCancel; UI F5/F12 |
| 3000-SEND-MAP, 3100-SCREEN-INIT | UI initial/render (shared ScreenHeader) |
| 3200-SETUP-SCREEN-VARS | UI fetched fields/protected keys |
| 3250-SETUP-INFOMSG | UI validation/confirmation; API no-change/success/conflict messages |
| 3300-SETUP-SCREEN-ATTRS | UI protected fields before fetch and during confirmation |
| 3400-SEND-SCREEN | UI rendered screen and actions |
| 9000-READ-DATA, 9100-GETCARD-BYACCTCARD | API keysAndMissingRecord, confirmationAndCancel |
| 9200-WRITE-PROCESSING | API confirmationAndCancel; HTTP writeFailure; CVV caveat above |
| 9300-CHECK-CHANGE-IN-REC | API concurrentModification (external SQL mutation before F5) |
| YYYY-STORE-PFKEY (COPY CSSTRPFY) | UI physical Enter/F3/F5/F12 through shared useFunctionKeys |
| ABEND-ROUTINE | HTTP writeFailure sanitizes infrastructure failure, no fabricated terminal abend |

Existing E5 `CardApiIntegrationTest` and `CardPages.test.tsx` assert U now routes
to update rather than showing the uninstalled-option stub.

## Checks

Run `mvn -B verify` in `carddemo-online-api`, and
`npm run lint && npm run typecheck && npm test && npm run build` in
`carddemo-online-ui`. Root `make up && make build && make parity` exercises
the unchanged legacy batch estate; it does not prove online CICS runtime CVV
semantics. Testcontainers tests use synthetic records in isolated PostgreSQL.
