# E4 — CAVW account view

[COACTVWC](../../app/cbl/COACTVWC.cbl) sends and receives
[CACTVWA in COACTVW](../../app/bms/COACTVW.bms). CM00 option 1 opens
`/accounts/view`; F3 returns to CM00 without signing out. The first
screen has an empty, focused account number and the legacy prompt.
Enter submits the lookup; other function keys act as Enter, as in 0000.

## Data and HTTP contract

Authenticated operators (USER or ADMIN) post `{"accountId":"00000000042"}`
to `/api/accounts/view`. The ID is 11 ASCII digits, not all zeros;
keep leading zeroes by sending a string. The request does not put the
ID in the URL. Every lookup is read-only and uses one PostgreSQL snapshot.
No COMMAREA or lookup payload is persisted in the browser.

`{account, customer, error}` is the redraw returned with HTTP 200, including
validation, not-found and duplicate-key states. `error`, when present,
contains `code`, `message` and `accountNumberInvalid`. Each data section
is null until found. Authentication failure returns 401. A database read
failure returns a generic 503 and no details. Responses carrying data are
`Cache-Control: no-store`. Use synthetic data only: the legacy display
includes identifying fields and does not apply customer-level authorization.
Before production cutover, review access to those fields and index
`card_xref(account_id)` for production volumes.

| Read order | CICS file / copybook | PostgreSQL and screen behavior |
|---|---|---|
| 1 | CXACAIX / CVACT03Y | `card_xref.account_id` selects the linked customer. No match stops the lookup. The [non-unique alternate index](../../app/jcl/XREFFILE.jcl) returns [DUPKEY (15/140)](https://www.ibm.com/docs/en/cics-ts/6.x?topic=summary-read) for multiple matches; 9200 takes WHEN OTHER and stops, even if both cards reference the same customer. |
| 2 | ACCTDAT / CVACT01Y | `account.account_id` gives status, dates, group and five signed amounts. Even on not-found, continue to the customer read: 9300 does not set the flag that 9000 checks. |
| 3 | CUSTDAT / CVCUS01Y | `customer.customer_id` gives the fields used by 1200, including address line 3 as City. A missing customer leaves found account details intact; if both masters are missing, the account error wins. |

Column-to-copybook mappings are in [the shared VSAM schema guide](../schema-mapping-vsam.md).
The API returns business names, retains decimal values and fixed-width numeric IDs,
and excludes unused fields, including the cross-reference card number.

## Preserved behavior and deviations

* Blank or `*` submits as `No input received`: 2200 overwrites 2210's
  `Account number not provided`. On redraw a red invalid field shows `*`
  as a placeholder, leaving the browser input editable. Invalid nonblank
  values remain visible. Customer-not-found alone does not mark this field invalid.
* The BMS customer section formats SSN with hyphens and displays only the
  first five ZIP and first thirteen phone characters. The API keeps the full values.
* The browser displays both sections as read-only, leaves absent fields blank
  rather than rendering an uninitialized account record, and keeps full signed
  amount precision instead of clipping to the 3270 picture. It uses the shared
  localized browser timestamp and a responsive layout instead of terminal positions.
* File-not-found messages omit terminal RESP/RESP2 padding and diagnostics; DB
  failures use one safe error instead of fabricating CICS response codes or returning
  partial data. The confirmed DUPKEY message retains its legacy code.

## Paragraph-to-test trace

API tests use PostgreSQL 15 Testcontainers and a real USER JWT.
UI tests exercise the React route and driver against mapped HTTP responses.

| COACTVWC paragraph(s) | Test evidence |
|---|---|
| 0000-MAIN, COMMON-RETURN, CSSTRPFY | `SignonMenuTest` routes CM00 option 1 to CAVW. `AccountViewIntegrationTest` denies anonymous entry. `AccountViewPage.test.tsx` covers first entry, re-entry, F3 (key and button), and F8 as Enter. |
| 1000-SEND-MAP, 1100-SCREEN-INIT | UI first-entry test checks program/transaction header, prompt, blank sections and focused input. |
| 1200-SETUP-SCREEN-VARS | API exact-field test and UI protected-field display test; both cover partial output when the customer is missing, and the API covers the missing-account case. |
| 1300-SETUP-SCREEN-ATTRS, 1400-SEND-SCREEN | UI validation/resubmission test covers the blank marker, invalid field, refocus, and clearing stale values. |
| 2000-PROCESS-INPUTS, 2100-RECEIVE-MAP | UI Enter and submit tests; API receives the unchanged fixed-width ID. |
| 2200-EDIT-MAP-INPUTS, 2210-EDIT-ACCOUNT | API parameterized blank, asterisk, all-zero, nonnumeric and wrong-width cases; UI checks blank/invalid messages. |
| 9000-READ-ACCT, 9200-GETCARDXREF-BYACCT | API tests for success, missing and duplicate cross-references, including an account present without an xref. |
| 9300-GETACCTDATA-BYACCT, 9400-GETCUSTDATA-BYCUST | API tests for present/missing masters, partial data and first-error precedence. Locked-table test exercises a non-NOTFND read failure. |
| SEND-PLAIN-TEXT, SEND-LONG-TEXT, ABEND-ROUTINE | No invalid terminal conversation state or reachable long-text call is carried over; API 503 and UI failure tests cover the web error equivalent. EXIT paragraphs are no-ops covered by their callers. |
