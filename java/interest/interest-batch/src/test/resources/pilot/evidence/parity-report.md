# INT-01 pilot – per-case parity report (open this first)

Final code revision: `2109615405fe658bd4d9b2b55af43c48e0a704f1` (branch `demo/05-pilot`).
Java 21, Spring Boot 3.3.5 / Spring Batch 5.1.2, PostgreSQL 15 (compose db).
Legacy baseline rerun in this session: `make up && make build && make parity CHAIN=interest` -> 7/7 PASS.

Command per case (repo root, comparator unchanged):

```sh
P=java/interest/interest-batch/src/test/resources/pilot
docker compose exec -T \
  -e INTEREST_PILOT_JDBC_URL=jdbc:postgresql://db:5432/carddemo \
  -e INTEREST_PILOT_DB_USER=carddemo -e INTEREST_PILOT_DB_PASSWORD=carddemo \
  estate python3 "$P/parity/run_case.py" <case> --out work/java-pilot/<case>
python3 tools/parity/compare.py interest <case> --actual work/java-pilot/<case> --label java \
  --report work/java-pilot/compare-report.md
```

## Result per case

The ticket names one case, **i01-baseline**. The other six were run for information only.

| Case | Ticket case | compare.py | Datasets (records / decoded fields) | SYSOUT STEP15 | rc.json |
|---|---|---|---|---|---|
| i01-baseline | yes | **PASS** | 4 / 50, 0 differences | identical | identical |
| i02-tie-half-cent | no | PASS | 4 / 50, 0 differences | identical | identical |
| i03-zero-rate-skipped | no | PASS | 2 / 25, 0 differences | identical | identical |
| i04-default-group-fallback | no | PASS | 2 / 25, 0 differences | identical | identical |
| i05-multi-category | no | PASS | 3 / 38, 0 differences | identical | identical |
| i06-negative-balance | no | PASS | 2 / 25, 0 differences | identical | identical |
| i07-nontie-rounding | no | PASS | 4 / 50, 0 differences | identical | identical |

All seven checks now PASS with the owner-authorized real-setup driver. It runs DEFGDGB, ACCTFILE,
XREFFILE, TCATBALF and DISCGRP on one Runner, then unloads their actual KSDS datasets for Java intake.
Only STEP15 is replaced by Java. Every RC comes from an executed setup step or the Java process exit code;
no code is hardcoded. The previous direct-jar RC-only failure is superseded. Exact commands and faithful
input/framing explanation: `../parity/README.md`. Raw outputs: `compare-report.md`, `compare-stdout.txt`,
`e2e-run.log`. Manifest includes layout/lrecl/records; SYSOUT comes from Java's committed SYSOUT rows.

Recorder bug remains OPEN for a person: scope/reset Runner.rcs in record.py and explicitly re-record goldens.
INTCALC has only STEP15, but the current recorder attributes accumulated setup keys to it. No forbidden files
were edited. Standards §9 lacks a separate line for non-converted setup RC provenance (skill not edited).
S16 normal-path ticket parity is PASS; S12 remains FAIL (partial), not proved by these normal-path cases.
Maven: 36 unit + 10 integration tests, no failures/errors/skips, BUILD SUCCESS (`test-output.txt`).
Driver unit check: 1 test OK, including normal/empty/overlength records. Docker Hub 429 required the existing
ESTATE_BASE_IMAGE=mirror.gcr.io/library/ubuntu:22.04 build argument. Java 21 is already in estate;
Maven runs on host Java 21. Runtime setup logs/unloads remain under work/java-pilot/<case>.

## Additional i01 field slice (historical SQL evidence, final Maven ITs rerun)

From `PilotI01IT` (Testcontainers PostgreSQL 15) and `i01-field-slice.md`:
- SYSTRAN T01–T13: 2 of 2 rows match the decoded golden. ACCTDATA A03/A09/A10 and whole records: 2 of 2 match,
  including the untouched account `00000002002`.
- Interest cents matched: 4700 (10.00 + 37.00 = 47.00).
- SQL on the Docker PostgreSQL 15 run `ec2f6b8c-…` (`i01-sql-assertions.txt`): 2 transaction rows
  `2022071800000001` / `…02`, amounts 10.00 / 37.00, sum 47.00, cards `4000000000002001` / `…2002`, both timestamps
  `2022-07-05-10.00.00.000000`. 1 delta: `00000002001` 1000.00 -> 1010.00 with cycle credit/debit 0.00.
  `00000002002` stays 2400.00 with 0 deltas (INT-Q02).
- Rollback (`PilotRollbackIT`): unreadable TCATBALF sign byte, missing DISCGRP dataset, missing account,
  missing XREF and missing DEFAULT rate each give a FAILED job, exit 12, 0 committed `interest_tx` /
  `account_delta` / `sysout_line` / `run_checkpoint` rows for the run and no `datasets/` export. For the
  missing account, group 1 had already been computed and was rolled back.

Scope of this evidence: field slice plus normal-path SYSOUT for the lab ASCII profile only. Not covered:
error-path SYSOUT/abend RC, EBCDIC/host profiles and restart. Normal-path RC parity is now covered above.
