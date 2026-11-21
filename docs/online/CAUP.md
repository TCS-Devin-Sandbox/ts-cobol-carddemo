# E7 — CAUP account update

[COACTUPC](../../app/cbl/COACTUPC.cbl) sends/receives CACTUPA in
[COACTUP](../../app/bms/COACTUP.bms). CM00 option 2 opens
`/accounts/update`. USER and ADMIN use the existing JWT authentication.
F3 returns to CM00 without signing off. Only synthetic customer data is permitted.

## Data and HTTP contract

* `POST /api/accounts/update/read`, body `{"accountId":"00000000042"}`:
  reads cross-reference, account and customer in one repeatable-read snapshot.
  Returns `{accountId, customerId, version, fields, state, message, invalidField}`.
  IDs retain leading zeroes. Business-named `fields` are strings so blank, invalid
  edits, fixed-width numbers and exact monetary input survive the review round trip.
* `POST /api/accounts/update`, body `{accountId, version, fields, confirmed:false}`:
  Enter validates the edits in legacy order. The first error wins. No writes occur.
  Valid changes produce `state:"CONFIRM"`; unchanged input produces the legacy
  `No change detected with respect to values fetched.` message before validation.
* The same request with `confirmed:true` is F5 Save. The UI freezes reviewed fields;
  the server always revalidates the submitted values, even with confirmation.
  Success returns `state:"SAVED"` and `Changes committed to database`.
* F12 Cancel discards edits and issues another read, also refreshing the version.
  Enter during confirmation does not save. F5 outside confirmation does nothing.
  New account clears the form; no identifying payload is stored in browser storage.

Edit/search errors return HTTP 200 redraws. Authentication returns 401. Unexpected
database failures return a safe 503 (`Changes unsuccessful. Please try again`).
Data and conflict responses use `Cache-Control: no-store`.
Like E4, not-found messages omit terminal RESP/RESP2 padding/diagnostics, and
DUPKEY retains the established CICS-equivalent 15/140 message. Missing data does
not produce an editable incomplete record. The existing CAVW read service and
business-named schema are reused; no migration or shared schema change is needed.

### Optimistic concurrency and atomicity

The version combines account/customer identity and both PostgreSQL `xmin` row
versions. This covers **any SQL writer**, not only this API, including changes to
non-screen fields. These are short-lived screen tokens, not durable identifiers
to retain across database restore or transaction-ID wraparound.

The update transaction locks account then customer using `SELECT ... FOR UPDATE`,
compares both locked versions against the read token, validates, and writes both
rows. A concurrent request waits and then compares against the winner's new row
versions. A stale/missing token or deleted row returns **409** with
`Record changed by some one else. Please review`. The UI preserves the edits but
requires F12 reload before another attempt. Failure on the second write rolls back
the first write. Locks never span browser requests. The real HTTP concurrency test
launches two independent requests behind a latch from the same read token, checks
exactly one 200 and one 409, and checks both persisted winner fields.

## Preserved quirks and adaptations

* Source edit order: status, open date, credit limit, expiry, cash limit, reissue,
  balance, cycle credit/debit, SSN, DOB, FICO, names, address 1, state, ZIP, city,
  country, phones, EFT, primary-holder, then state/ZIP cross-edit. No new positive
  limit, limit-order, date-order or account-group checks are introduced.
* Change detection ignores case and surrounding spaces in character fields and
  compares monetary values numerically. Unchanged invalid legacy records are not
  forced through validation. Lowercase status alone is therefore not a change;
  lowercase status accompanying a real change is invalid. State lookup is case-sensitive.
* Only 1900–2099 dates are accepted. Gregorian leap years are retained; DOB must
  be strictly **before** today (today is rejected by the source's future-date message).
* Names/city/country allow ASCII letters and spaces only. Middle name and address
  line 2 are optional; address line 1 is merely mandatory. Government-issued ID
  and account group have no content edits. Country and customer ID remain protected
  as in 3320; the API ignores attempts to change the protected country.
* Numeric EFT must be nonzero even though stored as text. SSN's three components
  must be nonzero; the first cannot be 666 or 900–999. FICO is 300–850 inclusive.
* US state, ZIP-prefix and general-purpose North American area-code sets are copied
  exactly from CSLKPCDY, including its surprising ZIP prefixes and exclusion of
  toll-free area codes. They are not replaced by modern postal/phone libraries.
* Phone punctuation is not validated; only the fixed-position components are.
  The source's repeated area-component blank check permits a nonblank line number
  when area and prefix are both blank. Both phones are optional; rewrites reconstruct
  punctuation, including `(   )   -    ` for a blank phone. ZIP edits/rewrite
  retain only the five screen characters; phone display/input is thirteen characters.
* Monetary input supports signs, currency/group separators and CR/DB; cents truncate
  without rounding. Assignment to the legacy S9(10)V99 picture drops higher integer
  digits. Blank/asterisk fields and BMS field widths are normalized before edits.
  Error strings retain the source's 75-character message buffer limit.
* **Account rewrite layout quirk:** COACTUPC's private ACCT-UPDATE-RECORD omits
  ACCT-ADDR-ZIP, unlike CVACT01Y. Its group bytes therefore overwrite the account
  ZIP slot, and the actual account group slot becomes blank. The SQL rewrite
  deliberately puts the submitted `accountGroupId` into `account.zip_code` and
  clears `account.account_group_id`; customer ZIP is separate. This is not silently
  corrected. Unmapped fillers are retained rather than persisting unspecified
  working-storage filler bytes. The integration test asserts the group/ZIP effect.
* 9700's field-by-field CICS comparison (including its date offsets) is explicitly
  replaced by the requested row-version check, which is stricter and detects changes
  to either whole row. Browser responsive layout, timestamp and focus replace BMS
  terminal placement; dates/SSN/phones are single business fields rather than COMMAREA
  fragments. No raw CICS buffers, transaction state or copybook names enter the API.

## Paragraph → code → test trace

API code is under `carddemo-online-api/src/main/java/com/carddemo/account/`;
tests under the matching test package. UI code/test is
`carddemo-online-ui/src/pages/AccountUpdatePage{,.test}.tsx`.
EXIT paragraphs are no-op returns covered by their callers.

| COACTUPC paragraph(s) | Ported code | Test evidence |
|---|---|---|
| 0000-MAIN, COMMON-RETURN, CSSTRPFY | `AccountUpdateController`, `MenuController.target`, React route/F3 | `p0000RequiresSessionAndP3100StartsWithSearchValidation`, `SignonMenuTest` option 2; UI initial/session/menu/F3 tests |
| 1000-PROCESS-INPUTS, 1100-RECEIVE-MAP | `change`, `normalize`, UI submit/fields | `p1100NormalizesStarMissingAndBmsFieldWidths`; UI fetch/edit/save request assertions |
| 1200-EDIT-MAP-INPUTS | `AccountUpdateEdits.validate` | `p1200AcceptsAllFieldsAndOptionalBlankMiddleNameAndPhones`, `p1200KeepsFirstErrorInSourceEditOrder`, integration `p1200RejectsInvalidChangesEvenWithConfirmation` |
| 1205-COMPARE-OLD-NEW | `same`, `change` | `p1205IgnoresCaseWhitespaceAndMoneyFormattingAndSkipsLegacyInvalidUnchangedData`, integration no-change/confirmation test; UI no-change test |
| 1210-EDIT-ACCOUNT | `keyError` | `p1210BlankKey`, `p1210InvalidKey`; integration/UI search error tests |
| 1215-EDIT-MANDATORY | `required` | `paragraphRejectsInvalidField` 1215; full valid-input test |
| 1220-EDIT-YESNO | `yesNo` | parameter cases 1220 (blank/invalid status and holder), valid-input test |
| 1225-EDIT-ALPHA-REQD | `alpha` | parameter cases 1225 (blank/punctuation), valid-input test |
| 1235-EDIT-ALPHA-OPT | `alpha(optional)` | parameter case 1235, optional-blank test |
| 1230-EDIT-ALPHANUM-REQD, 1240-EDIT-ALPHANUM-OPT | Not called by 1200 or any other source paragraph | No artificial validations added |
| 1245-EDIT-NUM-REQD | `numeric` | parameter cases 1245 (blank/nonnumeric/zero), valid-input test |
| 1250-EDIT-SIGNED-9V2 | `money`, `amount` | parameter cases 1250, `p1250PreservesSignedCurrencyTruncationAndPictureOverflow` |
| 1260-EDIT-US-PHONE-NUM, EDIT-AREA-CODE, EDIT-US-PHONE-PREFIX, EDIT-US-PHONE-LINENUM, EDIT-US-PHONE-EXIT | `phone`, `phonePart` | phone parameter cases for every component's blank/nonnumeric/zero branches and area membership; `p1260BlankAreaAndPrefixIgnoreLineAndPunctuationIsNotEdited` |
| 1265-EDIT-US-SSN | `validate` SSN components | parameter cases 1265 (zero/666/900/second/third); valid-input test |
| 1270-EDIT-US-STATE-CD | `validate`, `AccountUpdateLookups.STATES` | parameter cases 1270 including lowercase; valid-input test |
| 1275-EDIT-FICO-SCORE | `validate` FICO range | parameter cases 1275 (299/851); valid-input test |
| 1280-EDIT-US-STATE-ZIP-CD | `validate`, `STATE_ZIPS` | parameter case 1280; valid CA/90210 input |
| CSUTLDPY EDIT-DATE-CCYYMMDD, EDIT-YEAR-CCYY, EDIT-MONTH, EDIT-DAY, EDIT-DAY-MONTH-YEAR, EDIT-DATE-LE, EDIT-DATE-OF-BIRTH | `date`; `LocalDate` replaces LE date service | parameter cases EDIT-* (all error messages), `editDateAcceptsCenturyAndLeapBoundaries`; valid past DOB |
| 2000-DECIDE-ACTION | `change`, UI review/F5/F12 | integration no-change/confirmation/no-write test; UI review/freeze/save and F12 key/button tests |
| 3000-SEND-MAP, 3100-SCREEN-INIT, 3200-SETUP-SCREEN-VARS, 3201-SHOW-INITIAL-VALUES, 3202-SHOW-ORIGINAL-VALUES, 3203-SHOW-UPDATED-VALUES | `readDetails`, React screen | UI initial/fetch/review/save/F12 tests |
| 3250-SETUP-INFOMSG, 3300-SETUP-SCREEN-ATTRS, 3310-PROTECT-ALL-ATTRS, 3320-UNPROTECT-FEW-ATTRS, 3390-SETUP-INFOMSG-ATTRS, 3400-SEND-SCREEN | screen state/message, React disabled/error controls | UI no-change/error/protection/save tests |
| 9000-READ-ACCT, 9200-GETCARDXREF-BYACCT, 9300-GETACCTDATA-BYACCT, 9400-GETCUSTDATA-BYCUST, 9500-STORE-FETCHED-DATA | `readDetails`, reused `AccountViewService.lookup` | integration `p9000p9200p9300p9400p9500ReadBothMastersAndHandleMissingAndDuplicateXref`, `p9400MissingCustomer` |
| 9600-WRITE-PROCESSING | `change`, `write`, Spring transaction | integration confirmation/save/layout assertion, `p9600RollsBackAccountWhenCustomerRewriteFails` |
| 9700-CHECK-CHANGE-IN-REC | `token`, locked version comparison | integration any-writer/either-row test and `p9600p9700ConcurrentUpdatesFromSameReadExactlyOneWinsAndDatabaseHoldsWinner`; UI 409/reload test |
| ABEND-ROUTINE, CSSTRPFY error path | controller safe exception handler, UI error handling | integration `abendRoutineDoesNotExposeDatabaseDiagnostics`; UI 503/401 test |

## Verification

```sh
docker pull mirror.gcr.io/library/postgres:15-alpine
docker tag mirror.gcr.io/library/postgres:15-alpine postgres:15-alpine
(cd carddemo-online-api && mvn -B verify)
(cd carddemo-online-ui && npm run lint && npm run typecheck && npm test && npm run build)
make up && make build && make parity
```

The plan tracker uses a standalone E7 row, leaving other in-flight E rows untouched.
