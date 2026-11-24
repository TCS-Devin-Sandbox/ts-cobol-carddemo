# READCARD — Reconciliation Framework

Every run writes `<out>/run_metrics.json` (SCH-07). These fields support parallel-run reconciliation against legacy SYSOUT.

## Count checks
| Metric | Source | Legacy equivalent | Check |
|---|---|---|---|
| input_records | `raw.count()` | records in CARDDATA KSDS | = output_detail_lines on success |
| output_detail_lines | lines written in S4 | SYSOUT lines − 2 banners | = legacy detail lines |
| rejected | always 0 (no per-record rejection) | none | 0 |
| validation_record_too_long / _duplicate_keys | S2 | n/a | 0 on success |

## Control totals
| Metric | Definition |
|---|---|
| min_card_num / max_card_num | first / last emitted card_num (collation order) |
| records_sha256 | SHA-256 of ordered `record_image + "\n"` (latin-1) — recompute over legacy SYSOUT lines 2..n−1 for equality |
| config_sha256 | hash of effective config (audit) |

## Business totals
None — CBACT02C computes no amounts (TR-006). Field-level matching replaces totals: decode each listing line with CVACT02Y offsets and compare per field.

## Verification metrics
| Metric | Meaning |
|---|---|
| status | SUCCESS / ABEND |
| rc, abend_code, legacy_file_status, error_stage | outcome (BR-005, BR-006, TR-004) |
| started_at / ended_at | duration |
| chain, case, clock | parity context |

## Procedure
1. Run legacy (`make run CHAIN=read-masters CASE=<case>`) or use golden `fixtures/read-masters/<case>/expected`.
2. Run target with the same input directory.
3. Compare SYSOUT and rc.json with `tools/parity/compare.py read-masters <case> --actual <out> --steps READCARD`.
4. Recompute counts / sha256 from legacy SYSOUT and compare to `run_metrics.json`.
