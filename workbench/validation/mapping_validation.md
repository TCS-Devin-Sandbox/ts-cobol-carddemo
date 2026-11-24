# READCARD — Source-to-Target Mapping Validation

Source of truth: `target_design/source_to_target_mapping.md`; implementation: `config.json` layout + `apply_transformations`.

| Source (CVACT02Y) | Offset/Len | Target | Transformation | Config match | Field-level test vs legacy | Result |
|---|---|---|---|---|---|---|
| CARD-NUM X(16) | 0/16 | card_num | identity (sort key) | yes | equal | PASS |
| CARD-ACCT-ID 9(11) | 16/11 | card_acct_id | identity (string, zeros kept) | yes | equal | PASS |
| CARD-CVV-CD 9(03) | 27/3 | card_cvv_cd | identity, clear text | yes | equal | PASS |
| CARD-EMBOSSED-NAME X(50) | 30/50 | card_embossed_name | identity | yes | equal | PASS |
| CARD-EXPIRAION-DATE X(10) | 80/10 | card_expiration_date | identity (A-02, no parse) | yes | equal | PASS |
| CARD-ACTIVE-STATUS X(01) | 90/1 | card_active_status | identity (A-01) | yes | equal | PASS |
| FILLER X(59) | 91/59 | filler | identity, space-padded | yes | equal | PASS |
| (record) | 0/150 | record_image | T-01 rpad 150 | yes | byte-identical SYSOUT | PASS |
| (derived) | — | sort_key | cp037 translation | yes | EBCDIC order tests | PASS |
| (derived) | — | source_line_no | tie-break only | yes | not emitted | PASS |

Output schema: SYSOUT (SCH-05), rc.json (SCH-06), run_metrics.json (SCH-07) all produced.
Missing mappings: 0. Unauthorised mappings: 0. Transformation deviations: 0.
Parquet path rebuilds the image from typed columns; equal to golden for r01.
