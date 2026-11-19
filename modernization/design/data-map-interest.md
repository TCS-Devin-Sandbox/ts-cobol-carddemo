# Interest data map — INT-MAP v1.0

**PROPOSED.** Companion to [INT-ARCH v1.0](architecture.md). Scope is the five layouts in the [stream manifest](../streams/interest/manifest.json). Offsets are zero-based, inclusive, in the original unblocked fixed record. All mapped columns are `NOT NULL`; COBOL spaces/zeros are data, not SQL NULL. Named group rows map to generated binary keys; the top-level record maps to the corresponding table row. Retain auxiliary `raw_record BYTEA` (exact LRECL) alongside each imported record, and the complete emitted raw record alongside staged output. Auxiliary metadata does not change the layouts.

| Code | Source/storage | Decode and encode rule |
|---|---|---|
| X | `PIC X(n)` → `VARCHAR(n) COLLATE "C"` | Retain spaces incl. trailing padding; require exactly n decoded characters and exactly n bytes in the source profile on export. Do not trim, normalize Unicode, parse dates, coerce flags or numerically convert cards/IDs. SQL UTF-8 is not the wire format. |
| D | Unsigned `PIC 9(n) DISPLAY` → `VARCHAR(n) COLLATE "C"` | Digits only, exactly n, zero-padded; implied scale 0, unsigned. Text preserves width and leading zeroes. No overpunch; merchant zero is `000000000`. |
| Z | Signed `DISPLAY S9(...)V99` → `NUMERIC(p,2)` | Validate zones/digits on bytes; decode trailing embedded sign/digit (no separate sign byte), decimal before final two digits. EBCDIC canonical C positive/D negative; accept F unsigned positive, or other zones, only under an evidenced profile. Harness text uses positive `{ABCDEFGHI`, negative `}JKLMNOPQR` for 0–9. Use BigDecimal; reject malformed/over-scale source values before SQL can round them. Keep original bytes for signed zero/alternate zones and unchanged records; re-encode changed fields per profile. |
| B | `PIC X(n)` FILLER → `BYTEA` | Preserve exactly n raw bytes with `octet_length` check; no character conversion or filler semantics assumed. |

**COMP-3 is not present** in these five layouts. Do not treat signed DISPLAY as packed. A later, verified COMP-3 profile would be a separate layout version: validate digit nibbles, pad and trailing sign nibble (canonical C/F positive, D negative); apply declared scale without text translation. Linkage `PARM-LENGTH ... COMP` is not a record/table field.

**Transfer profiles.** Take byte-preserving exports with explicit CCSID, RECFM/LRECL, delimiting/blocking, hashes and source IDs. cp037 is the lab codec supported by [the comparator](../../tools/parity/copybook.py), not an asserted production CCSID. Fixture `.PS.txt` and golden datasets use ASCII-compatible text/overpunch; line delimiters and padding are a separate harness framing profile, not part of the host record. Preserve full fixed-width host payloads (no LF/RDW in RECFM=F records). Hold on unexpected framing, unmappable text, invalid fields or inconsistent manifests; do not silently load/quasi-repair malformed records just because the parity helper is tolerant. Restrict access to raw/quarantine evidence.

## CVACT01Y · ACCTDATA / ACCTFILE · 300 bytes

[ACCOUNT-RECORD](../../app/cpy/CVACT01Y.cpy) → `interest.account_input`.

| Row | COBOL field | Bytes | PIC · codec | Column · exact SQL type | Key/use |
|---|---|---:|---|---|---|
| A01 | `ACCT-ID` | 0–10 | `9(11)` · D | `acct_id` · `VARCHAR(11)` | PK |
| A02 | `ACCT-ACTIVE-STATUS` | 11–11 | `X(01)` · X | `active_status` · `VARCHAR(1)` | — |
| A03 | `ACCT-CURR-BAL` | 12–23 | `S9(10)V99` · Z | `current_balance` · `NUMERIC(12,2)` | delta |
| A04 | `ACCT-CREDIT-LIMIT` | 24–35 | `S9(10)V99` · Z | `credit_limit` · `NUMERIC(12,2)` | — |
| A05 | `ACCT-CASH-CREDIT-LIMIT` | 36–47 | `S9(10)V99` · Z | `cash_credit_limit` · `NUMERIC(12,2)` | — |
| A06 | `ACCT-OPEN-DATE` | 48–57 | `X(10)` · X | `open_date` · `VARCHAR(10)` | — |
| A07 | `ACCT-EXPIRAION-DATE` | 58–67 | `X(10)` · X | `expiration_date` · `VARCHAR(10)` | — |
| A08 | `ACCT-REISSUE-DATE` | 68–77 | `X(10)` · X | `reissue_date` · `VARCHAR(10)` | — |
| A09 | `ACCT-CURR-CYC-CREDIT` | 78–89 | `S9(10)V99` · Z | `cycle_credit` · `NUMERIC(12,2)` | delta |
| A10 | `ACCT-CURR-CYC-DEBIT` | 90–101 | `S9(10)V99` · Z | `cycle_debit` · `NUMERIC(12,2)` | delta |
| A11 | `ACCT-ADDR-ZIP` | 102–111 | `X(10)` · X | `addr_zip` · `VARCHAR(10)` | — |
| A12 | `ACCT-GROUP-ID` | 112–121 | `X(10)` · X | `group_id` · `VARCHAR(10)` | — |
| A13 | `FILLER` | 122–299 | `X(178)` · B | `filler_raw` · `BYTEA` | — |

## CVACT03Y · CARDXREF / XREFFILE / XREFFIL1 · 50 bytes

[CARD-XREF-RECORD](../../app/cpy/CVACT03Y.cpy) → `interest.card_xref_input`.

| Row | COBOL field | Bytes | PIC · codec | Column · exact SQL type | Key/use |
|---|---|---:|---|---|---|
| X01 | `XREF-CARD-NUM` | 0–15 | `X(16)` · X | `card_number` · `VARCHAR(16)` | PK |
| X02 | `XREF-CUST-ID` | 16–24 | `9(09)` · D | `customer_id` · `VARCHAR(9)` | — |
| X03 | `XREF-ACCT-ID` | 25–35 | `9(11)` · D | `acct_id` · `VARCHAR(11)` | AIX |
| X04 | `FILLER` | 36–49 | `X(14)` · B | `filler_raw` · `BYTEA` | — |

## CVTRA01Y · TCATBALF · 50 bytes

[TRAN-CAT-BAL-RECORD](../../app/cpy/CVTRA01Y.cpy) → `interest.category_balance_input`.

| Row | COBOL field | Bytes | PIC · codec | Column · exact SQL type | Key/use |
|---|---|---:|---|---|---|
| B00 | `TRAN-CAT-KEY` | 0–16 | group (B01–B03) | `legacy_key` · `BYTEA` (17) | sort/seek |
| B01 | `TRANCAT-ACCT-ID` | 0–10 | `9(11)` · D | `acct_id` · `VARCHAR(11)` | PK1 |
| B02 | `TRANCAT-TYPE-CD` | 11–12 | `X(02)` · X | `txn_type` · `VARCHAR(2)` | PK2 |
| B03 | `TRANCAT-CD` | 13–16 | `9(04)` · D | `category_code` · `VARCHAR(4)` | PK3 |
| B04 | `TRAN-CAT-BAL` | 17–27 | `S9(09)V99` · Z | `balance` · `NUMERIC(11,2)` | — |
| B05 | `FILLER` | 28–49 | `X(22)` · B | `filler_raw` · `BYTEA` | — |

## CVTRA02Y · DISCGRP · 50 bytes

[DIS-GROUP-RECORD](../../app/cpy/CVTRA02Y.cpy) → `interest.disclosure_rate_input`.

| Row | COBOL field | Bytes | PIC · codec | Column · exact SQL type | Key/use |
|---|---|---:|---|---|---|
| D00 | `DIS-GROUP-KEY` | 0–15 | group (D01–D03) | `legacy_key` · `BYTEA` (16) | exact lookup |
| D01 | `DIS-ACCT-GROUP-ID` | 0–9 | `X(10)` · X | `group_id` · `VARCHAR(10)` | PK1 |
| D02 | `DIS-TRAN-TYPE-CD` | 10–11 | `X(02)` · X | `txn_type` · `VARCHAR(2)` | PK2 |
| D03 | `DIS-TRAN-CAT-CD` | 12–15 | `9(04)` · D | `category_code` · `VARCHAR(4)` | PK3 |
| D04 | `DIS-INT-RATE` | 16–21 | `S9(04)V99` · Z | `annual_percent_rate` · `NUMERIC(6,2)` | — |
| D05 | `FILLER` | 22–49 | `X(28)` · B | `filler_raw` · `BYTEA` | — |

## CVTRA05Y · SYSTRAN / TRANSACT · 350 bytes

[TRAN-RECORD](../../app/cpy/CVTRA05Y.cpy) → `interest.interest_tx`.

| Row | COBOL field | Bytes | PIC · codec | Column · exact SQL type | Key/use |
|---|---|---:|---|---|---|
| T01 | `TRAN-ID` | 0–15 | `X(16)` · X | `txn_id` · `VARCHAR(16)` | legacy ID |
| T02 | `TRAN-TYPE-CD` | 16–17 | `X(02)` · X | `txn_type` · `VARCHAR(2)` | — |
| T03 | `TRAN-CAT-CD` | 18–21 | `9(04)` · D | `category_code` · `VARCHAR(4)` | — |
| T04 | `TRAN-SOURCE` | 22–31 | `X(10)` · X | `source` · `VARCHAR(10)` | — |
| T05 | `TRAN-DESC` | 32–131 | `X(100)` · X | `description` · `VARCHAR(100)` | — |
| T06 | `TRAN-AMT` | 132–142 | `S9(09)V99` · Z | `amount` · `NUMERIC(11,2)` | — |
| T07 | `TRAN-MERCHANT-ID` | 143–151 | `9(09)` · D | `merchant_id` · `VARCHAR(9)` | — |
| T08 | `TRAN-MERCHANT-NAME` | 152–201 | `X(50)` · X | `merchant_name` · `VARCHAR(50)` | — |
| T09 | `TRAN-MERCHANT-CITY` | 202–251 | `X(50)` · X | `merchant_city` · `VARCHAR(50)` | — |
| T10 | `TRAN-MERCHANT-ZIP` | 252–261 | `X(10)` · X | `merchant_zip` · `VARCHAR(10)` | — |
| T11 | `TRAN-CARD-NUM` | 262–277 | `X(16)` · X | `card_number` · `VARCHAR(16)` | — |
| T12 | `TRAN-ORIG-TS` | 278–303 | `X(26)` · X | `orig_ts` · `VARCHAR(26)` | — |
| T13 | `TRAN-PROC-TS` | 304–329 | `X(26)` · X | `proc_ts` · `VARCHAR(26)` | downstream AIX |
| T14 | `FILLER` | 330–349 | `X(20)` · B | `filler_raw` · `BYTEA` | — |

## Keys, indexes and derived columns

| Rows | Legacy definition and evidence | PostgreSQL 15 contract |
|---|---|---|
| A01 | ACCTDATA [KEYS(11 0)](../../app/jcl/ACCTFILE.jcl), no AIX identified | PK `(snapshot_id, acct_id)`. Deltas PK `(run_id, acct_id)`; A03, A09, A10 have exact old/new numerics and source record fingerprint; project other fields unchanged. |
| X01/X03 | [XREFFILE](../../app/jcl/XREFFILE.jcl) KEYS(16 0); AIX KEYS(11,25) NONUNIQUEKEY, UPGRADE/PATH | PK `(snapshot_id, card_number)`, nonunique index `(snapshot_id, acct_id)`. Intake records `aix_first BOOLEAN NOT NULL` plus evidence pointer; partial unique index on `(snapshot_id, acct_id) WHERE aix_first`. Resolve the sole row for single-card keys; require host lookup proof for duplicate keys, not MIN(card_number). Absent card is diagnosed by the job. |
| B00–B03 | [TCATBALF](../../app/jcl/TCATBALF.jcl) KEYS(17 0), no AIX identified | PK `(snapshot_id, acct_id, txn_type, category_code)` and unique `(snapshot_id, legacy_key)`; scan exact concatenated source bytes (SQL text collation is not EBCDIC). |
| D00–D03 | [DISCGRP](../../app/jcl/DISCGRP.jcl) KEYS(16 0), no AIX identified | PK `(snapshot_id, group_id, txn_type, category_code)` and unique `(snapshot_id, legacy_key)`; exact first 16 bytes. Encode space-padded `DEFAULT   ` using the same profile. |
| T01/T13 | [SYSTRAN(+1)](../../app/jcl/INTCALC.jcl) is sequential, not KSDS. COMBTRAN loads [TRANSACT](../../app/jcl/COMBTRAN.jcl); [TRANFILE](../../app/jcl/TRANFILE.jcl) defines master KEYS(16 0), nonunique AIX KEYS(26 304) over TRAN-PROC-TS (also [TRANIDX](../../app/jcl/TRANIDX.jcl)). | PK `(run_id, emit_ordinal BIGINT)` (positive ordinal); nonunique `(run_id, txn_id)` for collision preflight. Keep T13 unchanged for the host AIX. No target master/AIX DDL. Preserve suffix wraps in the staged sequential file without silently allowing a downstream conflict. |

`snapshot_id` and `run_id` use UUID. Key byte lengths are enforced with CHECK, not BYTEA modifiers. Metadata FKs are allowed; do not put cross-layout referential constraints on card/account/rate presence (ordered legacy reads raise those errors).

## Unclosed mapping gaps (not inferred)

| Affected rows | Needed evidence and owner | Safe boundary |
|---|---|---|
| All; especially Z/B and encoded sort | Host data/VSAM: production CCSID, transfer framing, zone/sign variants incl. signed zero, and sort specimens | Admit only verified profiles. Lab golden output does not certify host encoding. |
| X01–X03 | Card Operations / VSAM: actual alternate READ on multi-card accounts (INT-H03) | Use observed first-return semantics; hold unresolved input. |
| A06–A08, T01, T12–T13 | Application/Scheduler: date domains, live PARM source/format and region time zone/DST (INT-H02/INT-Q05) | Keep fixed text. A07 is spelled `ACCT-EXPIRAION-DATE` in source. Timestamp columns use the same reading per transaction. |
| T14 / all filler | Host/Application: production initial transaction filler bytes (TN-06) | Match measured lab filler; no assumed fill for live. |
| T01 | Operations/downstream: collision handling against backup and downstream KSDS and production upper bound (INT-H05) | Preserve IDs and fail preflight on conflict; never drop a row. |
