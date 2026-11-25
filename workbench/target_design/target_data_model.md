# READCARD — Target Data Model

## Entities

| Entity | Description | Key | Source |
|---|---|---|---|
| Card | One payment card | card_num | CVACT02Y CARD-RECORD |
| Account (reference only) | Owning account, identified by card_acct_id; not read by this job | card_acct_id | CVACT01Y (not used) |

Relationship: Card *—1 Account via `card_acct_id` (legacy AIX KEYS(11 16), NONUNIQUEKEY). Not used by READCARD; documented for context.

## Datasets

| dataset_name | dataset_type | business_purpose | schema_reference |
|---|---|---|---|
| card_master_source | input (fixed-width text, lrecl 150, ASCII; or Parquet) | Card master system of record | SCH-01 / SCH-02 |
| card_master_raw | intermediate DataFrame | Raw record images with source order | SCH-03 |
| card_master | intermediate DataFrame | Parsed card fields + record image | SCH-04 |
| card_master_ordered | intermediate DataFrame | card_master ordered by collation key | SCH-04 + sort_key |
| readcard_listing (`sysout/READCARD.STEP05.txt`) | output text | Card master listing (SYSOUT equivalent) | SCH-05 |
| rc (`rc.json`) | output JSON | Step outcome | SCH-06 |
| run_metrics (`run_metrics.json`) | output JSON (audit) | Reconciliation and audit metrics | SCH-07 |

## Schemas

### SCH-01 card_master_source (fixed-width)
One record per line, 150 characters (short lines padded, see AS-01). Layout = CVACT02Y (below).

### SCH-02 card_master_source (Parquet, optional)
Columns as SCH-04 minus `record_image`, `source_line_no`, plus `filler string(59)`; record image rebuilt by fixed-width concatenation of untrimmed values.

### SCH-03 card_master_raw
| column | type | null | notes |
|---|---|---|---|
| source_line_no | long | no | 1-based input order |
| raw_line | string | no | as read, line terminator removed |
| record_image | string | no | raw_line rpad to 150 |
| raw_length | int | no | length of raw_line |

### SCH-04 card_master
| column | type | len | offset | null | from |
|---|---|---|---|---|---|
| card_num | string | 16 | 0 | no | CARD-NUM X(16) |
| card_acct_id | string (digits) | 11 | 16 | no | CARD-ACCT-ID 9(11) — kept as string to preserve leading zeros |
| card_cvv_cd | string (digits) | 3 | 27 | no | CARD-CVV-CD 9(03) — kept as string |
| card_embossed_name | string | 50 | 30 | no | CARD-EMBOSSED-NAME X(50), untrimmed |
| card_expiration_date | string | 10 | 80 | no | CARD-EXPIRAION-DATE X(10) (no date parse, A-02) |
| card_active_status | string | 1 | 90 | no | CARD-ACTIVE-STATUS X(01) (no interpretation, A-01) |
| filler | string | 59 | 91 | no | FILLER X(59) |
| record_image | string | 150 | 0 | no | full record |
| source_line_no | long | — | — | no | lineage |
| sort_key | binary | 16 | — | no | encode(card_num, cp037) or raw bytes |

### SCH-05 readcard_listing
| line | content |
|---|---|
| 1 | `START OF EXECUTION OF PROGRAM CBACT02C` |
| 2..n+1 | record_image (150 chars) per card, ascending sort_key |
| n+2 | `END OF EXECUTION OF PROGRAM CBACT02C` (success only) |
Error variants per target_design §6. Encoding latin-1/ASCII, `\n` terminators.

### SCH-06 rc.json
`{"READCARD": {"maxrc": <int>, "steps": {"STEP05": <int>}}}` — same shape as `fixtures/read-masters/*/expected/rc.json`.

### SCH-07 run_metrics.json
`job, step, status (SUCCESS|ABEND), abend_code (null|999), legacy_file_status, input_records, output_detail_lines, min_card_num, max_card_num, records_sha256, started_at, ended_at, config_hash`.
