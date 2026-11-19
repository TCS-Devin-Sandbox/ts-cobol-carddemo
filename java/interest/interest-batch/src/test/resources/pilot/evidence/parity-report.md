# INT-01 pilot – per-case parity report (open this first)

Candidate revision: `59ffbc2aef036c1756288de1daaa0c911f343ac5` (branch `demo/05-pilot`).
Java 21 (OpenJDK 21), Spring Boot 3.3.5 / Spring Batch 5.1.2, PostgreSQL 15.20 (Docker `postgres:15`, port 5433).
Legacy baseline rerun in this session: `make up && make build && make parity CHAIN=interest` -> 7/7 PASS.

Command per case (repo root, comparator unchanged):

```sh
java -jar java/interest/interest-batch/target/interest-batch-0.1.0-PILOT.jar \
  --interest.pilot.run-on-startup=true \
  --interest.pilot.input-dir=fixtures/interest/<case>/input \
  --interest.pilot.output-dir=work/java-pilot/<case>
python3 tools/parity/compare.py interest <case> --actual work/java-pilot/<case> --label java \
  --report work/java-pilot/compare-report.md
```

## Result per case

The ticket names one case, **i01-baseline**. The other six were run for information only.

| Case | Ticket case | compare.py | Datasets (records / decoded fields) | SYSOUT STEP15 | rc.json |
|---|---|---|---|---|---|
| i01-baseline | yes | **FAIL (1 difference)** | 4 / 50, 0 differences | identical | differs |
| i02-tie-half-cent | no | FAIL (1) | 4 / 50, 0 differences | identical | differs |
| i03-zero-rate-skipped | no | FAIL (1) | 2 / 25, 0 differences | identical | differs |
| i04-default-group-fallback | no | FAIL (1) | 2 / 25, 0 differences | identical | differs |
| i05-multi-category | no | FAIL (1) | 3 / 38, 0 differences | identical | differs |
| i06-negative-balance | no | FAIL (1) | 2 / 25, 0 differences | identical | differs |
| i07-nontie-rounding | no | FAIL (1) | 4 / 50, 0 differences | identical | differs |

The only difference in every case is `rc.json`. The golden file lists `STEP05`, `STEP10`, `STEP15`, `STEP20`,
`STEP25` and `STEP30` under `INTCALC`, but `app/jcl/INTCALC.jcl` has only `STEP15`. The other five codes come
from the setup jobs (DEFGDGB, ACCTFILE, XREFFILE, TCATBALF, DISCGRP); `tools/parity/record.py:131` keeps every
step key without a `.` under the job. The pilot runs only STEP15 and writes `{"INTCALC": {"maxrc": 0, "steps": {"STEP15": 0}}}`.
To match, it would have to report codes for steps it never ran, or someone would have to change the golden or
the comparator. Both are prohibited, so the comparator check stays FAIL. Raw output: `compare-report.md`, `compare-stdout.txt`.

## Ticket verification: i01 field slice (not full parity)

From `PilotI01IT` (Testcontainers PostgreSQL 15) and `i01-field-slice.md`:
- SYSTRAN T01–T13: 2 of 2 rows match the decoded golden. ACCTDATA A03/A09/A10 and whole records: 2 of 2 match,
  including the untouched account `00000002002`.
- Interest cents matched: 4700 (10.00 + 37.00 = 47.00).
- SQL on the Docker PostgreSQL 15 run `13197b52-…` (`i01-sql-assertions.txt`): 2 transaction rows
  `2022071800000001` / `…02`, amounts 10.00 / 37.00, sum 47.00, cards `4000000000002001` / `…2002`, both timestamps
  `2022-07-05-10.00.00.000000`. 1 delta: `00000002001` 1000.00 -> 1010.00 with cycle credit/debit 0.00.
  `00000002002` stays 2400.00 with 0 deltas (INT-Q02).
- Rollback (`PilotRollbackIT`): unreadable TCATBALF sign byte, missing DISCGRP dataset, missing account,
  missing XREF and missing DEFAULT rate each give a FAILED job, exit 12, 0 committed `interest_tx` /
  `account_delta` / `sysout_line` / `run_checkpoint` rows for the run and no `datasets/` export. For the
  missing account, group 1 had already been computed and was rolled back.

Scope of this evidence: field slice plus normal-path SYSOUT for the lab ASCII profile only. Not covered:
full RC parity, error-path SYSOUT/abend RC, EBCDIC/host profiles and restart.
