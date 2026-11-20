# VSAM master schema

Flyway V1 creates nine tables in the connection's default schema (normally public).
Names retain copybook field names lowercased with hyphens replaced by underscores,
including the legacy EXPIRAION spelling. Group items are flattened, not duplicated.

## Representation and export contract

* Every elementary field is NOT NULL: a VSAM record has no SQL null. No defaults
  hide omitted data. Empty tables are legal; insertions must supply a complete record.
* PIC X uses CHAR(n), including one explicitly retained `filler` column per record.
  Preserve filler contents rather than regenerate spaces. PostgreSQL pads short
  strings; B4 must not trim text and must encode exactly n bytes in the legacy
  character set. CHAR compares ignoring trailing spaces; no locale/case folding
  or business validation is added. Character length is not encoded byte length.
* Dates and timestamps remain CHAR text, accepting blanks and non-calendar values.
* All fields in these nine copybooks are DISPLAY (implicit usage). There are **no
  COMP or COMP-3 fields** here; do not infer packed/binary storage from SQL NUMERIC.
  B2/B4 must still handle COMP/COMP-3 in other layouts; V1 makes no mapping for them.
* PIC 9 uses exact NUMERIC(p,0) with a nonnegative CHECK; PIC S9 uses signed
  NUMERIC(p,s), allowing the entire negative range. Implied V contributes scale,
  not a stored character. Leading zeroes and zoned EBCDIC overpunch signs belong
  to the codec; SQL stores their value, not their physical representation.
  Negative zero and alternative positive sign encodings are not distinguished by
  NUMERIC: byte-identical export of noncanonical encodings needs raw-byte provenance
  in B4, not an assumption that NUMERIC retains sign nibbles.
* No arithmetic/truncation is performed by this migration. PostgreSQL rounds excess
  fractional digits on NUMERIC assignment; callers must enforce PIC scale before
  insert (e.g. setScale(s, UNNECESSARY)). Legacy COMPUTE without ROUNDED must use
  truncation toward zero (RoundingMode.DOWN), not PostgreSQL rounding, in chain code.
* No foreign keys, status enums, date checks or FICO/rate limits: they could reject
  representable legacy data, or prevent loading masters independently.
* Existing Db2 translations in db/postgres define carddemo.transaction_type and
  carddemo.transaction_type_categor, not tran_type. They remain untouched. The
  SQL keyword-like table name transaction is consistently double-quoted.

## Keys and source evidence

Primary keys follow app/jcl/{ACCTFILE,CARDFILE,CUSTFILE,XREFFILE,TCATBALF,
DISCGRP,TRANFILE,TRANTYPE,TRANCATG}.jcl KEYS declarations respectively:
11,16,9,16,17,16,16,2,6 bytes at offset zero. Composite groups preserve
copybook order. CBCUS01C FD-CUST-ID also confirms 9 digits; the separate older
DEFCUST.jcl says KEYS(10 0), inconsistent with CUSTFILE and CVCUS01Y, and is
not used as the customer layout authority. Alternate indexes are not unique
constraints here (e.g. multiple cards per account remain legal).

## Field mapping

Every row below is an elementary item in source order. Usage is explicit in the
mapping even though omitted (DISPLAY default) in the source. FILLER is retained.

### account — CVACT01Y.cpy

Primary key: `acct_id`.

| Field | PIC | Usage | Column | SQL type |
|---|---|---|---|---|
| ACCT-ID | 9(11) | DISPLAY | acct_id | NUMERIC(11,0) |
| ACCT-ACTIVE-STATUS | X(01) | DISPLAY | acct_active_status | CHAR(1) |
| ACCT-CURR-BAL | S9(10)V99 | DISPLAY | acct_curr_bal | NUMERIC(12,2) |
| ACCT-CREDIT-LIMIT | S9(10)V99 | DISPLAY | acct_credit_limit | NUMERIC(12,2) |
| ACCT-CASH-CREDIT-LIMIT | S9(10)V99 | DISPLAY | acct_cash_credit_limit | NUMERIC(12,2) |
| ACCT-OPEN-DATE | X(10) | DISPLAY | acct_open_date | CHAR(10) |
| ACCT-EXPIRAION-DATE | X(10) | DISPLAY | acct_expiraion_date | CHAR(10) |
| ACCT-REISSUE-DATE | X(10) | DISPLAY | acct_reissue_date | CHAR(10) |
| ACCT-CURR-CYC-CREDIT | S9(10)V99 | DISPLAY | acct_curr_cyc_credit | NUMERIC(12,2) |
| ACCT-CURR-CYC-DEBIT | S9(10)V99 | DISPLAY | acct_curr_cyc_debit | NUMERIC(12,2) |
| ACCT-ADDR-ZIP | X(10) | DISPLAY | acct_addr_zip | CHAR(10) |
| ACCT-GROUP-ID | X(10) | DISPLAY | acct_group_id | CHAR(10) |
| FILLER | X(178) | DISPLAY | filler | CHAR(178) |

### card — CVACT02Y.cpy

Primary key: `card_num`.

| Field | PIC | Usage | Column | SQL type |
|---|---|---|---|---|
| CARD-NUM | X(16) | DISPLAY | card_num | CHAR(16) |
| CARD-ACCT-ID | 9(11) | DISPLAY | card_acct_id | NUMERIC(11,0) |
| CARD-CVV-CD | 9(03) | DISPLAY | card_cvv_cd | NUMERIC(3,0) |
| CARD-EMBOSSED-NAME | X(50) | DISPLAY | card_embossed_name | CHAR(50) |
| CARD-EXPIRAION-DATE | X(10) | DISPLAY | card_expiraion_date | CHAR(10) |
| CARD-ACTIVE-STATUS | X(01) | DISPLAY | card_active_status | CHAR(1) |
| FILLER | X(59) | DISPLAY | filler | CHAR(59) |

### customer — CVCUS01Y.cpy

Primary key: `cust_id`.

| Field | PIC | Usage | Column | SQL type |
|---|---|---|---|---|
| CUST-ID | 9(09) | DISPLAY | cust_id | NUMERIC(9,0) |
| CUST-FIRST-NAME | X(25) | DISPLAY | cust_first_name | CHAR(25) |
| CUST-MIDDLE-NAME | X(25) | DISPLAY | cust_middle_name | CHAR(25) |
| CUST-LAST-NAME | X(25) | DISPLAY | cust_last_name | CHAR(25) |
| CUST-ADDR-LINE-1 | X(50) | DISPLAY | cust_addr_line_1 | CHAR(50) |
| CUST-ADDR-LINE-2 | X(50) | DISPLAY | cust_addr_line_2 | CHAR(50) |
| CUST-ADDR-LINE-3 | X(50) | DISPLAY | cust_addr_line_3 | CHAR(50) |
| CUST-ADDR-STATE-CD | X(02) | DISPLAY | cust_addr_state_cd | CHAR(2) |
| CUST-ADDR-COUNTRY-CD | X(03) | DISPLAY | cust_addr_country_cd | CHAR(3) |
| CUST-ADDR-ZIP | X(10) | DISPLAY | cust_addr_zip | CHAR(10) |
| CUST-PHONE-NUM-1 | X(15) | DISPLAY | cust_phone_num_1 | CHAR(15) |
| CUST-PHONE-NUM-2 | X(15) | DISPLAY | cust_phone_num_2 | CHAR(15) |
| CUST-SSN | 9(09) | DISPLAY | cust_ssn | NUMERIC(9,0) |
| CUST-GOVT-ISSUED-ID | X(20) | DISPLAY | cust_govt_issued_id | CHAR(20) |
| CUST-DOB-YYYY-MM-DD | X(10) | DISPLAY | cust_dob_yyyy_mm_dd | CHAR(10) |
| CUST-EFT-ACCOUNT-ID | X(10) | DISPLAY | cust_eft_account_id | CHAR(10) |
| CUST-PRI-CARD-HOLDER-IND | X(01) | DISPLAY | cust_pri_card_holder_ind | CHAR(1) |
| CUST-FICO-CREDIT-SCORE | 9(03) | DISPLAY | cust_fico_credit_score | NUMERIC(3,0) |
| FILLER | X(168) | DISPLAY | filler | CHAR(168) |

### card_xref — CVACT03Y.cpy

Primary key: `xref_card_num`.

| Field | PIC | Usage | Column | SQL type |
|---|---|---|---|---|
| XREF-CARD-NUM | X(16) | DISPLAY | xref_card_num | CHAR(16) |
| XREF-CUST-ID | 9(09) | DISPLAY | xref_cust_id | NUMERIC(9,0) |
| XREF-ACCT-ID | 9(11) | DISPLAY | xref_acct_id | NUMERIC(11,0) |
| FILLER | X(14) | DISPLAY | filler | CHAR(14) |

### tran_cat_bal — CVTRA01Y.cpy

Primary key: `trancat_acct_id`, `trancat_type_cd`, `trancat_cd`.

| Field | PIC | Usage | Column | SQL type |
|---|---|---|---|---|
| TRANCAT-ACCT-ID | 9(11) | DISPLAY | trancat_acct_id | NUMERIC(11,0) |
| TRANCAT-TYPE-CD | X(02) | DISPLAY | trancat_type_cd | CHAR(2) |
| TRANCAT-CD | 9(04) | DISPLAY | trancat_cd | NUMERIC(4,0) |
| TRAN-CAT-BAL | S9(09)V99 | DISPLAY | tran_cat_bal | NUMERIC(11,2) |
| FILLER | X(22) | DISPLAY | filler | CHAR(22) |

### disclosure_group — CVTRA02Y.cpy

Primary key: `dis_acct_group_id`, `dis_tran_type_cd`, `dis_tran_cat_cd`.

| Field | PIC | Usage | Column | SQL type |
|---|---|---|---|---|
| DIS-ACCT-GROUP-ID | X(10) | DISPLAY | dis_acct_group_id | CHAR(10) |
| DIS-TRAN-TYPE-CD | X(02) | DISPLAY | dis_tran_type_cd | CHAR(2) |
| DIS-TRAN-CAT-CD | 9(04) | DISPLAY | dis_tran_cat_cd | NUMERIC(4,0) |
| DIS-INT-RATE | S9(04)V99 | DISPLAY | dis_int_rate | NUMERIC(6,2) |
| FILLER | X(28) | DISPLAY | filler | CHAR(28) |

### transaction — CVTRA05Y.cpy

Primary key: `tran_id`.

| Field | PIC | Usage | Column | SQL type |
|---|---|---|---|---|
| TRAN-ID | X(16) | DISPLAY | tran_id | CHAR(16) |
| TRAN-TYPE-CD | X(02) | DISPLAY | tran_type_cd | CHAR(2) |
| TRAN-CAT-CD | 9(04) | DISPLAY | tran_cat_cd | NUMERIC(4,0) |
| TRAN-SOURCE | X(10) | DISPLAY | tran_source | CHAR(10) |
| TRAN-DESC | X(100) | DISPLAY | tran_desc | CHAR(100) |
| TRAN-AMT | S9(09)V99 | DISPLAY | tran_amt | NUMERIC(11,2) |
| TRAN-MERCHANT-ID | 9(09) | DISPLAY | tran_merchant_id | NUMERIC(9,0) |
| TRAN-MERCHANT-NAME | X(50) | DISPLAY | tran_merchant_name | CHAR(50) |
| TRAN-MERCHANT-CITY | X(50) | DISPLAY | tran_merchant_city | CHAR(50) |
| TRAN-MERCHANT-ZIP | X(10) | DISPLAY | tran_merchant_zip | CHAR(10) |
| TRAN-CARD-NUM | X(16) | DISPLAY | tran_card_num | CHAR(16) |
| TRAN-ORIG-TS | X(26) | DISPLAY | tran_orig_ts | CHAR(26) |
| TRAN-PROC-TS | X(26) | DISPLAY | tran_proc_ts | CHAR(26) |
| FILLER | X(20) | DISPLAY | filler | CHAR(20) |

### tran_type — CVTRA03Y.cpy

Primary key: `tran_type`.

| Field | PIC | Usage | Column | SQL type |
|---|---|---|---|---|
| TRAN-TYPE | X(02) | DISPLAY | tran_type | CHAR(2) |
| TRAN-TYPE-DESC | X(50) | DISPLAY | tran_type_desc | CHAR(50) |
| FILLER | X(08) | DISPLAY | filler | CHAR(8) |

### tran_category — CVTRA04Y.cpy

Primary key: `tran_type_cd`, `tran_cat_cd`.

| Field | PIC | Usage | Column | SQL type |
|---|---|---|---|---|
| TRAN-TYPE-CD | X(02) | DISPLAY | tran_type_cd | CHAR(2) |
| TRAN-CAT-CD | 9(04) | DISPLAY | tran_cat_cd | NUMERIC(4,0) |
| TRAN-CAT-TYPE-DESC | X(50) | DISPLAY | tran_cat_type_desc | CHAR(50) |
| FILLER | X(04) | DISPLAY | filler | CHAR(4) |
