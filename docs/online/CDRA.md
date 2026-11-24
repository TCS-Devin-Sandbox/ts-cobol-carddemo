# G9 — CDRA account inquiry over REST

[COACCT01](../../app/app-vsam-mq/cbl/COACCT01.cbl) (transaction CDRA) is
triggered by MQ, reads 1000-byte requests from the request queue, reads
ACCTDAT and puts a 1000-byte reply on `CARD.DEMO.REPLY.ACCT`. Per
[decision 2 in the Batch G scope](../batch-g/scope.md), the queues are
replaced by a synchronous REST call; the reply body is the same 1000 bytes.

## HTTP contract

Both endpoints require an authenticated JWT (USER or ADMIN), are read-only and
return `text/plain;charset=ISO-8859-1`, `Cache-Control: no-store`. Like the
record codecs (ISO-8859-1 or IBM037), the reply is one byte per character, so
account text outside ASCII is kept and the body stays 1000 bytes.

| Request | Meaning |
|---|---|
| `POST /api/accounts/inquiry` with a `text/plain` body | The MQ request message as-is: `WS-FUNC` X(4), `WS-KEY` 9(11), filler. At most 1000 printable ASCII bytes (at most 1001 are read); shorter messages are space-padded to 1000 as the MQ buffer would be. |
| `GET /api/accounts/{accountId}/inquiry` | Shorthand for `INQA` + an 11-digit key. A path that is not 11 digits returns 400 `INVALID_ACCOUNT_ID`. |

Every legacy reply, including not-found and invalid-request, is HTTP 200
with exactly 1000 characters. The `X-Inquiry-Outcome` header (`FOUND`,
`NOT_FOUND`, `INVALID_REQUEST`) saves clients from parsing the text.

| COACCT01 (4000-PROCESS-REQUEST-REPLY) | Reply body |
|---|---|
| `WS-FUNC = 'INQA' AND WS-KEY > ZEROES`, DFHRESP(NORMAL) | `WS-ACCT-RESPONSE`, 252 bytes, then spaces: `ACCOUNT ID : ` 9(11), `ACCOUNT STATUS : ` X(1), `BALANCE : `, `CREDIT LIMIT : `, `CASH LIMIT : ` S9(10)V99, `OPEN DATE : `, `EXPR DATE : `, `REIS DATE : ` X(10), `CREDIT BAL : `, `DEBIT BAL : ` S9(10)V99, `GROUP ID : ` X(10). |
| DFHRESP(NOTFND) | `INVALID REQUEST PARAMETERS ACCT ID : ` + key, then spaces. |
| Any other function, or a zero key | `INVALID REQUEST PARAMETERS ACCT ID : ` + key + `FUNCTION : ` + function, then spaces. Key and function bytes are echoed as received. |
| Other READ responses (9999-ERROR-PROCESSING writes the error queue; no reply) | 503 JSON `ACCOUNT_INQUIRY_UNAVAILABLE`, no details. |

Signed amounts are zoned display with the sign overpunched on the last digit,
in the ASCII rendering of EBCDIC used by the fixtures and the parity codec:
`{`/`A`–`I` for +0..+9 and `}`/`J`–`R` for −0..−9 (for example `-1234.56` is
`00000012345O`). Columns come from the `account` table ([schema guide](../schema-mapping-vsam.md)).

## Deviations

* No queues, message ID or correlation ID: the HTTP response is the reply.
  MQ open/get/put failures and the error queue have no equivalent.
* The legacy program has no user check (anyone who can put on the request
  queue gets a reply); the REST endpoints require a signed-on user.
* A request longer than the 1000-byte buffer (MQRC 2080 in MQGET) or with
  non-ASCII characters returns 400 `INVALID_MESSAGE` instead of an error-queue
  message. `INQA` with a non-numeric key is treated as an invalid request; the
  COBOL `WS-KEY > ZEROES` test on non-numeric data is compiler-dependent.
* 1000-CONTROL loops 4000-MAIN-PROCESS until no message arrives within the
  5-second GET wait, then 8000-TERMINATION closes the queues. Over REST one
  request is one call, so the trigger, wait loop and queue open/close are dropped.

## Proof (off-host, weaker)

CICS and MQ do not run off-host, so the proof is unit and API tests, not
`make parity`:

* [AccountInquiryReplyTest](../../carddemo-online-api/src/test/java/com/carddemo/vsammq/AccountInquiryReplyTest.java):
  overpunch for every sign/digit edge, 252-byte labelled layout with negative
  amounts and short text fields, 1000-byte padding, request layout, NOTFND and
  bad-function replies.
* [AccountInquiryIntegrationTest](../../carddemo-online-api/src/test/java/com/carddemo/vsammq/AccountInquiryIntegrationTest.java):
  loads every ACCTDATA record from `fixtures/posting/*/input` into PostgreSQL
  and checks each reply byte for byte against labels plus the raw CVACT01Y
  field bytes of the fixture record; also NOTFND, bad function, zero key,
  400s, 401, non-ASCII account text and a lock-timeout 503. Runs in the `trantype-tests` CI job
  (`mvn -B verify` in `carddemo-online-api`).
