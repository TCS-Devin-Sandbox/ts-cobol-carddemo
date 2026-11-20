# Step 5b build run - interest tickets 02-06 in four groups

Status: **all four groups passed; review rerun combined check passed** on `demo/05b-integration` @ `731c551` (original workflow check: `9f78de6`).
Workflow run `wfr-a2207d5a0fb04ea68deeca07ca05b2c6` (script `build-workflow.py` @ `ef8d77f`), 4 child
sessions, 0 retries, 0 skipped, 0 needs-decision, 3335 s wall time, 35.04 ACUs.

**Open this file first.** Read with it: `../../java/interest/evidence/combined-check-final.txt` (review rerun output; original workflow output remains `evidence/final-combined-check.txt`) and
the four group PRs.

## Regrouping (decided by the presenter - to be confirmed by a person)

A previous run of this step blocked because the step prompt named tickets 02-10 and the queue
has 01-06 (see git history of this file, commit aa05c03). The presenter replaced the groups with
the existing queue, unchanged and not renumbered:

| Group | Tickets (build order) | Starts | Ticket dependencies (Dependencies line) | Check |
|---|---|---|---|---|
| g1-groundwork | 02-schema, 03-record-codecs | at once | 02 -> 01; 03 -> 01 (no schema dependency) | OK: 01 is on the starting branch; 03 does not need 02 |
| g2-logic | 04-calculation-restart | after g1 | 04 -> 02, 03 | OK: g1 is earlier |
| g3-data-flow | 05-shadow-handoff | after g1, beside g2 | 05 -> 02, 03 ("does not wait for 04") | OK: g1 is earlier; no need of g2 |
| g4-gate | 06-parity-gate | after g2 and g3 | 06 -> 04, 05 | OK: g2 and g3 are earlier |

Grants restated against these tickets (used only where the ticket needs them):
- 04-calculation-restart: `codec/AccountRecord.java` may pass ACCT-ACTIVE-STATUS to the domain, the
  `RecordLayoutTest` assertions this breaks may be updated; policy switches incl. `multiCard` and
  `overflow` in `policy/InterestPolicy.java` and `application.yml`.
- 06-parity-gate: `batch/InterestJobConfig.java` and new `batch/` classes that only join the load,
  calculation and export steps into one job, with their tests.
None of these files exist on `demo/05b-integration` before the run (the pilot has only `pilot/`);
a grant lets the ticket create them.

## Workflow script

`modernization/design/build-workflow.py`: one child session per group, no other agent.

Dry run with stand-in agents (`evidence/build-workflow-dryrun.txt`):

| Case | Result |
|---|---|
| all pass | g1 runs alone; g2 and g3 start together at t=1.0s after g1 is combined; g4 starts at t=2.5s after g3 (the later of g2/g3) is combined; all passed |
| g2 needs-decision | g1 passed; g2 needs-decision; g3 passed; g4 skipped (depends on g2=needs-decision); g2 not retried |
| g1 fails twice | g1 attempt 2 carries "stub failure 1" quoted; g1 failed; g2, g3 skipped; g4 skipped |

Prompt check (g2 printed in `evidence/build-workflow-prompt-g2.txt`; g3 differs only in group, branch,
ticket, evidence folder, grants and squash message):

| Item | g2 | g3 |
|---|---|---|
| Ticket files in order | 04-calculation-restart.md | 05-shadow-handoff.md |
| Starting branch / branch / PR base | demo/05b-integration / demo/05-g2-logic / demo/05b-integration | same with demo/05-g3-data-flow |
| Four skills, read first, never edit | yes | yes |
| Forbidden: app/, fixtures/, tools/parity/, modernization/ | yes | yes |
| Evidence folder per ticket | java/interest/evidence/04-calculation-restart/ | java/interest/evidence/05-shadow-handoff/ |
| Standards §9 checklist per ticket | yes | yes |
| No-merge rule word for word | yes | yes |
| Grants only where granted | ticket 04 grants only | "none" |
| Self-contained (repo, commands, combined check, squash/push loop, return fields) | yes | yes |

Prompt check and flowchart review: done by the coordinating session (unattended run, no person
waited for); both met every item of the step prompt's list above. To be confirmed by a person.

## Results per ticket

| Ticket | Group | State | Session | PR | Tests (at the ticket) | Parity | Checklist | Skills used |
|---|---|---|---|---|---|---|---|---|
| 02-schema | g1-groundwork | passed | devin-4822d57e768940809d2423c7720bd650 | [#18](https://github.com/TCS-Devin-Sandbox/ts-cobol-carddemo/pull/18) | Surefire 36, Failsafe 16 (6 SchemaIT PG15), 0 fail/err/skip | compare.py 7/7 PASS; make parity 24/24 | 38 PASS / 0 FAIL | standards, build, verify, pitfalls, carddemo-parity |
| 03-record-codecs | g1-groundwork | passed | devin-4822d57e768940809d2423c7720bd650 | [#18](https://github.com/TCS-Devin-Sandbox/ts-cobol-carddemo/pull/18) | Surefire 44 (8 FixedRecordCodecTest), Failsafe 16, 0 fail/err/skip | compare.py 7/7 PASS; make parity 24/24; SQL N/A | 38 PASS / 0 FAIL | standards, build, verify, pitfalls, carddemo-parity |
| 04-calculation-restart | g2-logic | passed (review rerun) | devin-c454fcaca7244cd1b0d65cc9ee0beeb7 | [#21](https://github.com/TCS-Devin-Sandbox/ts-cobol-carddemo/pull/21) | Integration rerun: 70 unit + 88 IT (InterestCloseIT 21; packaging failure IT 1), 0 fail/err/skip; real process halt before/after commit | compare.py 7/7 PASS; make parity 24/24 | 38 PASS / 0 FAIL | standards, build, verify, pitfalls |
| 05-shadow-handoff | g3-data-flow | passed | devin-4c499227fc654dbba3867a5e93d7dd32 | [#20](https://github.com/TCS-Devin-Sandbox/ts-cobol-carddemo/pull/20) | 51 unit + 43 IT (HandoffIT 27, HandoffRulesTest 7), 0 fail/err/skip | compare.py 7/7 PASS; make parity 24/24 | 38 PASS / 0 FAIL | standards, build, verify, pitfalls |
| 06-parity-gate | g4-gate | passed | devin-65427584a9c247fab4eb91875519949d | [#22](https://github.com/TCS-Devin-Sandbox/ts-cobol-carddemo/pull/22) | 70 unit + 80 IT, 0 fail/err/skip; byte check 7 cases/14 files; SQL 7 runs; 7 rehearsal scenarios | compare.py 7/7 PASS (joined job); make parity 24/24 | 38 PASS / 0 FAIL | standards, build, verify, pitfalls |

Combined into `demo/05b-integration` (squash, PRs left open): g1 `c43bcf1`, g3 `7bb786d`, g2 `4f68796`,
g4 `55f3afc`, then a second g4 squash `40f6cc2` (INT-06 review fixes: failed candidate job status persisted, stored-status assertion, refreshed evidence) pushed by the g4 session after it had reported. Order of state changes: g1 running -> passed; g2 and g3 running side by side; g3 passed;
g2 passed; g4 running -> passed.

Grants used: 04-calculation-restart and 06-parity-gate grants were offered; whether each grant
file was touched is visible in PR #21 and #22 (`policy/`, `codec/AccountRecord.java`, `batch/`).
06's joined job lives in `batch/InterestJobConfig.java` / `InterestBatchApplication`.

## Final combined check (coordinating session, on `9f78de6` - includes `40f6cc2`)

| Check | Result |
|---|---|
| `mvn clean verify` | Surefire 70, Failsafe 80, 0 failures/errors/skipped, BUILD SUCCESS |
| parity driver unittest | Ran 1 test, OK |
| compare.py, 7 cases, final joined Java job (`scripts/parity/intcalc_java.py`) | 7/7 PASS, 0 differences |
| `make parity` | 24/24 PASS |

Full output: `evidence/final-combined-check.txt`.

## Failures

None. No group failed, was retried, skipped or returned needs-decision.

## Review findings #4/#5 (rerun)

Code revision checked: `731c55106b3cd1ffbac1aed4c9c52d1de9eb8a6d` (the subsequent evidence-only commit records these results).
Cherry-picked exactly `c0447ed`, `320594a`, `c78c66c`, `8d700ce` from g2 as
`c9554de`, `8cbd12c`, `05553e0`, `eee4e2e`: explicit abandoned-COMPUTING resume,
external first-return evidence, reached-group failure transcript bounds, and preservation of
completed SYSOUT on packaging failure. No other g2 branch changes were incorporated;
integration's g4 code, gate SQL and INT-06 evidence are unchanged.

Two new PostgreSQL integration tests halt a child JVM before/after a checkpoint group commit,
not a handled exception. Each leaves COMPUTING; an unconfirmed resume returns RC12 without
changing state/checkpoint; `--interest.close.abandoned=true` returns RC0 with the same sealed
snapshot, byte-identical prior committed rows and golden output. No automatic restart was added.
The independent estate rehearsal injects the halt with a test-only entry point and resumes with
the real `InterestCloseApplication` CLI. Transcript with commands, RCs, SQL totals and checkpoint
rows: `../../java/interest/evidence/04-calculation-restart/crash-resume-cli.txt`.
Before commit: 0 transactions / 0 deltas / no checkpoint; after commit: 1 transaction (10.00),
1 delta (new balance 1010.00), checkpoint ordinal 1. Both recover to 2 transactions (47.00),
1 delta (1010.00), terminal checkpoint ordinal 2. Halt RC77, refusal RC12, confirmed resume RC0
in both scenarios.

Combined rerun: **70 unit + 88 integration tests, 0 failures/errors/skips; BUILD SUCCESS**;
parity-driver unittest **1 test, OK**; joined Java driver plus unchanged `compare.py`
**7/7 PASS, 0 differences**; `make parity` **24/24 PASS**.
Full output: `../../java/interest/evidence/combined-check-final.txt`. Refreshed INT-04
`test-output.txt`, `restart-and-failure-cases.txt`, and checklist restart entry identify this revision.
An initial test-harness run failed with two primary CommitHook beans (2 test failures); the
child harness now makes its crash hook the sole primary. Final rerun has zero failures.

## Skill corrections (applied, one commit per skill)

| Skill | Correction | From | Commit |
|---|---|---|---|
| interest-ticket-verify | Run a new entry point under the frozen pom with `run_case.py --java <wrapper>` (PropertiesLauncher, `-Dloader.main`), e.g. `scripts/parity/intcalc_java.py` | g2 (04) | 5fa518d |
| interest-ticket-verify | Ticket SQL against `interest`/`interest_batch` may be asserted inside the ticket's PG15 IT, printed output as evidence | g3 (05) | 5fa518d |
| interest-ticket-build | Step 4: Testcontainers also needs the mirrored `postgres:15-alpine` tag and Java 21 `JAVA_HOME` (already in steps 2-3; made explicit) | g2 (04), g4 (06) | ec6d60a |
| interest-pitfalls | New pitfall 8: Spring Batch custom exit statuses need explicit `on("*")` transitions | g4 (06) | 5ca87f1 |

## Proposals for the standards (not applied - interest-conversion-standards is unchanged)

1. 02: distinguish apply-once schema verification (tests apply SQL via JDBC) from runtime migration activation, and pilot regression parity from new-schema adoption.
2. 03: document that fixture text files trim trailing storage spaces; a test-only framing adapter expands to LRECL, separate from strict fixed-record decoding.
3. 04: standard for a second Spring entry point without touching `pom.xml` (`@SpringBootConfiguration` + scoped `@ComponentScan`, PropertiesLauncher).
4. 04: S12 - a shared file-status adapter (23, extended 9x, operation-specific DISPLAY texts) for every legacy DD; synthetic-status unit tests acceptable without host evidence.
5. 04: state that restart resume seeks `checkpoint.next_legacy_key` inclusively, and that "Ticket writes: Prohibited" means live host datasets, not Scope paths.
6. 05: one mapping point (§4/S08) between `interest.snapshot.codec_profile` `lab-ascii` and `FixedRecordCodec.Profile.LAB_ASCII`.
7. 05: S13 - release checks the host's current (0) generation equals the receipt's; the INTCALC success condition is acknowledged and redelivered at-least-once, never republished.
8. 06: shadow/rehearsal wiring uses HostBridge/ReleaseSignal adapters that throw if invoked.
9. 06: byte-level fixed-width check (LF framing, space padding) as a required gate artifact beside compare.py.

## For a person to confirm

- The regrouping onto tickets 02-06 and the restated grants (top of this file).
- The prompt check and flowchart review done without a person (above).
- g4's second squash `40f6cc2`, pushed after the workflow had ended (not in the child's structured result; final check covers it).
- The four group PRs #18, #20, #21, #22 (open, base `demo/05b-integration`) and this PR (base `demo/05a-skills`).
- The nine standards proposals.
- Devin Review on PR #24, left unfixed (child code; for a person to decide):
  - 05 `handoff/PacketBuilder.java`: `txn_ids` in the manifest are not JSON-escaped; a PARM holding `"` or `\` gives invalid JSON. Fix on `demo/05-g3-data-flow`.
  - 04 `job/CloseRunService.java`: resolved in the review rerun above; COMPUTING resumes require explicit operator abandonment, covered by process-halt tests and estate CLI evidence.
