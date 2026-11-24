# READCARD — Source-to-Target Mapping

Source: CVACT02Y `CARD-RECORD` (150 bytes) via `AWS.M2.CARDDEMO.CARDDATA.VSAM.KSDS` / CARDDATA.PS unload.

## Field mapping (direct)

| source_field | source_type | offset/len | target_field | target_type | transformation_rule | rule refs |
|---|---|---|---|---|---|---|
| CARD-NUM | PIC X(16) | 0/16 | card_num | string(16) | substring(1,16), no trim | BR-002, BR-003 |
| CARD-ACCT-ID | PIC 9(11) | 16/11 | card_acct_id | string(11) | substring(17,11), no numeric cast | BR-003 |
| CARD-CVV-CD | PIC 9(03) | 27/3 | card_cvv_cd | string(3) | substring(28,3), unmasked | BR-003 |
| CARD-EMBOSSED-NAME | PIC X(50) | 30/50 | card_embossed_name | string(50) | substring(31,50), no trim | BR-003 |
| CARD-EXPIRAION-DATE | PIC X(10) | 80/10 | card_expiration_date | string(10) | substring(81,10), no date parse | BR-003, A-02 |
| CARD-ACTIVE-STATUS | PIC X(01) | 90/1 | card_active_status | string(1) | substring(91,1), no decode | BR-003, A-01 |
| FILLER | PIC X(59) | 91/59 | filler | string(59) | substring(92,59) | BR-003 |
| CARD-RECORD (group) | X(150) | 0/150 | record_image | string(150) | rpad(raw_line,150,' ') | BR-003 |

## Derived fields

| target_field | derivation | purpose |
|---|---|---|
| sort_key | `card_num` encoded cp037 (collation=ebcdic) or raw bytes (binary) | BR-002 |
| source_line_no | monotonically increasing input line number | lineage |
| raw_length | length(raw_line) | structural validation (> 150 → error) |

Calculated fields: none. Enriched fields: none.

## Output mapping (record → listing line)

| target_field | listing columns |
|---|---|
| record_image | 1-150 (whole line) |

Legacy columns: CARD-NUM 1-16, CARD-ACCT-ID 17-27, CARD-CVV-CD 28-30, CARD-EMBOSSED-NAME 31-80, CARD-EXPIRAION-DATE 81-90, CARD-ACTIVE-STATUS 91, FILLER 92-150 (identical).

## Control/status mapping

| Legacy | Target |
|---|---|
| CARDFILE-STATUS '00' | stage success |
| CARDFILE-STATUS '10' | end of ordered iteration |
| OPEN failure (e.g. '35') | S1 exception → legacy_file_status '35' |
| READ structural failure | S2 exception → legacy_file_status '34' |
| CLOSE failure | S5 exception → legacy_file_status '90' |
| RETURN-CODE 0 | rc.json STEP05 = 0 |
| CEE3ABD 999 | abend_code 999; rc.json STEP05 = abend_rc (default 12) |
