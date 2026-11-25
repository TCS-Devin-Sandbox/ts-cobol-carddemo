# Pending authorization schema (G2)

Flyway V2 (`java/common/src/main/resources/db/migration/V2__pending_authorization.sql`)
adds the relational targets for the IMS database `DBPAUTP0` and the Db2 table
`CARDDEMO.AUTHFRDS`, in the same schema and history table as V1 (the online API
records both in `flyway_vsam_schema_history`). It creates tables only: codecs,
unload/reload and the programs that use them are later Batch G rows.

## IMS `DBPAUTP0` -> two tables

| IMS | SQL | Key | Notes |
|---|---|---|---|
| Root `PAUTSUM0` (100 bytes, `CIPAUSMY`) | `pending_auth_summary` | `account_id` (IMS unique `ACCNTID`) | Secondary index `DBPAUTX0` is not modelled: the PK covers the same key |
| Child `PAUTDTL1` (200 bytes, `CIPAUDTY`) | `pending_auth_detail` | `(account_id, auth_date_inv, auth_time_inv)` | `account_id` is the parent's key (IMS concatenated key); FK to the summary, `ON DELETE RESTRICT` |

* Hierarchical access becomes a foreign key plus explicit ordering. GU on the
  root is a PK lookup; GNP under one root is
  `WHERE account_id = ? ORDER BY auth_date_inv, auth_time_inv`.
* The child sequence field `PAUT9CTS` (8 bytes, packed date + packed time) is
  kept as two signed numeric columns holding the **inverted** values that
  COPAUA0C writes (`99999 - YYDDD`, `999999999 - HHMMSSmmm`). Ascending order on
  them is newest first, as IMS returns it. Natural timestamps are not stored in
  the key: the original `auth_original_date`/`auth_original_time` are kept as
  the copybook's text, and converting would lose the legacy key bytes.
* Deleting a root with children is rejected rather than cascaded: IMS DLET of a
  root deletes its dependants, so purge code (a later row) must delete children
  explicitly, which keeps that behaviour visible.
* Each `OCCURS 5` `PA-ACCOUNT-STATUS` becomes `account_status_1..5` in order.
* Every copybook byte has a column, including `filler` (34 and 17 bytes); the
  test sums the storage size of each PIC to 100 and 200 bytes.

## Field types (summary and detail)

Same contract as `docs/schema-mapping-vsam.md`: every field NOT NULL, no
defaults, `PIC X(n)` -> `CHAR(n)` (dates/times stay text), `PIC 9` ->
`NUMERIC(p,0)` with a nonnegative CHECK, `PIC S9` -> signed `NUMERIC`.
Usage decides only the codec, not the value range, except for COMP:

| Usage | Fields | SQL | Why |
|---|---|---|---|
| COMP-3 `S9(11)` | account id | `NUMERIC(11,0)` | max packed value, either sign |
| COMP-3 `S9(09)V99` | limits, balances, approved/declined amounts | `NUMERIC(11,2)` | |
| COMP-3 `S9(10)V99` | transaction/approved amount | `NUMERIC(12,2)` | |
| COMP-3 `S9(05)`, `S9(09)` | `auth_date_inv`, `auth_time_inv` | `NUMERIC(5,0)`, `NUMERIC(9,0)` | signed per PIC; COPAUA0C only writes positives |
| COMP `S9(04)` | approved/declined auth count | `SMALLINT` | see below |
| DISPLAY `9(n)` | customer id, processing code, POS entry mode | `NUMERIC(n,0)` + CHECK >= 0 | |

**COMP counts.** `S9(04) COMP` is a binary halfword; whether values above
9999 occur depends on the compiler `TRUNC` option, which the estate does not
state. `SMALLINT` (-32768..32767) holds every value the halfword can carry, so
the schema never rejects a decodable count (the unload's counts are 0..58).

Packed sign nibbles (C/D/F) and negative zero are codec concerns; NUMERIC keeps
the value only (as in V1). The unload uses C throughout.

## What the supplied unload holds

`AWS.M2.CARDDEMO.IMSDATA.DBPAUTP0.dat` has 22 roots and 202 children (its
trailer counts 22 and 202). Two root shapes need special handling by the
[G3 loader](../java/auth-data/README.md):

* The last root is blank-filled: its `PA-ACCT-ID` is X'404040404040', not a
  packed number, and it has no children. G3 stores it as an unkeyed frame in
  `pending_auth_unload_frame`, not as a summary row or an account-zero key.
* Accounts 7, 13 and 18 have X'00' (LOW-VALUES) in `PA-AUTH-STATUS` and
  `PA-ACCOUNT-STATUS`. PostgreSQL text cannot store NUL (SQLSTATE 22021).
  G3 stores it as U+2400 (`␀`), outside the IBM037 repertoire, so it fits
  the same `CHAR(n)` without conflating low-values and blanks; exports restore X'00'.

## Db2 `AUTHFRDS` -> `auth_fraud`

Column-for-column translation of `AUTHFRDS.ddl` with business names; nullability
is the Db2 one (only the key is NOT NULL). Types: `CHAR`/`VARCHAR` unchanged,
`DECIMAL(p,s)` -> `NUMERIC(p,s)`, `SMALLINT` unchanged, `DATE` unchanged,
`TIMESTAMP` -> `TIMESTAMP(6)` (Db2's default microsecond precision, no time zone).
Primary key `(card_number, auth_timestamp)`; the unique index `XAUTHFRD
(CARD_NUM ASC, AUTH_TS DESC)` becomes `auth_fraud_card_ts_desc` with the same
order. `processing_code` stays `CHAR(6)` as in Db2 (the IMS segment holds `9(06)`).
`MERCHANT_CATAGORY_CODE`'s misspelling is not carried over.

| Db2 | SQL |
|---|---|
| CARD_NUM, AUTH_TS | card_number, auth_timestamp |
| AUTH_RESP_CODE, AUTH_RESP_REASON | auth_response_code, auth_response_reason |
| TRANSACTION_AMT, APPROVED_AMT | transaction_amount, approved_amount |
| MERCHANT_CATAGORY_CODE, ACQR_COUNTRY_CODE | merchant_category_code, acquirer_country_code |
| AUTH_FRAUD, FRAUD_RPT_DATE | fraud_status, fraud_report_date |
| ACCT_ID, CUST_ID | account_id, customer_id |
| others | same name, lower case |

## Copybook -> column names

| CIPAUSMY | Column | CIPAUDTY | Column |
|---|---|---|---|
| PA-ACCT-ID | account_id | PA-AUTH-DATE-9C / PA-AUTH-TIME-9C | auth_date_inv / auth_time_inv |
| PA-CUST-ID | customer_id | PA-AUTH-ORIG-DATE / -TIME | auth_original_date / _time |
| PA-AUTH-STATUS | auth_status | PA-CARD-NUM, PA-AUTH-TYPE, PA-CARD-EXPIRY-DATE | card_number, auth_type, card_expiry_date |
| PA-ACCOUNT-STATUS (1..5) | account_status_1..5 | PA-MESSAGE-TYPE / -SOURCE, PA-AUTH-ID-CODE | message_type / _source, auth_id_code |
| PA-CREDIT-LIMIT, PA-CASH-LIMIT | credit_limit, cash_limit | PA-AUTH-RESP-CODE / -REASON | auth_response_code / _reason |
| PA-CREDIT-BALANCE, PA-CASH-BALANCE | credit_balance, cash_balance | PA-PROCESSING-CODE, PA-TRANSACTION-AMT, PA-APPROVED-AMT | processing_code, transaction_amount, approved_amount |
| PA-APPROVED-AUTH-CNT / -AMT | approved_auth_count / _amount | PA-MERCHANT-CATAGORY-CODE, PA-ACQR-COUNTRY-CODE, PA-POS-ENTRY-MODE | merchant_category_code, acquirer_country_code, pos_entry_mode |
| PA-DECLINED-AUTH-CNT / -AMT | declined_auth_count / _amount | PA-MERCHANT-ID / -NAME / -CITY / -STATE / -ZIP | merchant_id / _name / _city / _state / _zip |
| FILLER | filler | PA-TRANSACTION-ID, PA-MATCH-STATUS, PA-AUTH-FRAUD, PA-FRAUD-RPT-DATE, FILLER | transaction_id, match_status, fraud_status, fraud_report_date, filler |

## Proof

`PendingAuthorizationSchemaTest` (Testcontainers PostgreSQL 15, CI job
`auth-schema-tests`): V1+V2 migrate, a second run applies nothing; column names,
types and nullability match the copybooks and DDL; every numeric column takes
its max and (signed) negative max and rejects one more digit; COMP counts take
the halfword range; PIC X rejects n+1 characters; detail needs its summary and a
unique inverted key; the 22 roots and 202 children of
`AWS.M2.CARDDEMO.IMSDATA.DBPAUTP0.dat` (keys and original date/time only) read back
in unload (GNP) order, newest first in each account, and the two root shapes
above are detected; `auth_fraud` keeps
microseconds, Db2 nullability and the descending index.
