# Step 5a – Skills from the INT-01 pilot (open this first)

Source: pilot branch `demo/05-pilot` at `921dff2`,
[PR #15](https://github.com/TCS-Devin-Sandbox/ts-cobol-carddemo/pull/15), its report
`java/interest/interest-batch/src/test/resources/pilot/evidence/PILOT-REPORT.md`, the code under
`java/interest/`, AGENTS.md and `modernization/design/tickets/`. No application code, ticket,
fixture, golden, `tools/parity` or `carddemo-parity` file was changed.

## 1. The three skills

| Skill | One line |
|---|---|
| [interest-ticket-build](../../.agents/skills/interest-ticket-build/SKILL.md) | Module layout, Java 21 / Maven 3.9.9 / Spring Boot 3.3.5 / Testcontainers 1.21.4 / PostgreSQL 15, and the commands to start the estate and PostgreSQL, build and run the tests. |
| [interest-ticket-verify](../../.agents/skills/interest-ticket-verify/SKILL.md) | Driver command per case into `work/<ticket>/<case>`, `compare.py` per case, reading a difference, SQL check, and the pilot's report format; evidence under `java/interest/evidence/<ticket>/`. |
| [interest-pitfalls](../../.agents/skills/interest-pitfalls/SKILL.md) | The ten failures the pilot hit, as symptom, cause, fix; six reproduced in Step 5a, four marked "reported by the pilot, not reproduced". |

## 2. Replay result

Clean clone of `demo/05a-skills`, no `target/`, `work/`, `datasets/` or `loadlib/`; only the
build and verify skills followed, ticket `01-pilot`. Raw output:
`java/interest/evidence/01-pilot/` (`test-output.txt`, `e2e-run.log`, `compare-stdout.txt`,
`compare-report.md`, `i01-sql.sql`, `i01-sql-output.txt`, `replay-transcript.txt`).

| Step | Command | Result |
|---|---|---|
| build 2 | `sudo apt-get install -y -q openjdk-21-jdk-headless`; `export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64`; `mvn -v` | REPLAY_JAVA |
| build 3 | `make up && make build && make parity CHAIN=interest` | REPLAY_LEGACY |
| build 4 | `mvn -B -f java/interest/interest-batch/pom.xml clean verify` | REPLAY_MVN |
| build 5 | `python3 -m unittest discover -s java/interest/interest-batch/src/test/resources/pilot/parity -v` | REPLAY_UNIT |
| verify 2 | `docker compose exec -T ... estate python3 .../run_case.py <case> --out work/01-pilot/<case>` | REPLAY_DRIVER |
| verify 3 | `python3 tools/parity/compare.py interest <case> --actual work/01-pilot/<case> --label java --report java/interest/evidence/01-pilot/compare-report.md` | see table |
| verify 5 | `docker compose exec -T db psql ... -v run_id=<id> -f - < java/interest/evidence/01-pilot/i01-sql.sql` | REPLAY_SQL |

Parity per case (pilot result in `parity-report.md` → replay):

| Case | Ticket case | Pilot | Replay |
|---|---|---|---|
| i01-baseline | yes | PASS 4 / 50, 0 differences | REPLAY_i01 |
| i02-tie-half-cent | no | PASS 4 / 50, 0 differences | REPLAY_i02 |
| i03-zero-rate-skipped | no | PASS 2 / 25, 0 differences | REPLAY_i03 |
| i04-default-group-fallback | no | PASS 2 / 25, 0 differences | REPLAY_i04 |
| i05-multi-category | no | PASS 3 / 38, 0 differences | REPLAY_i05 |
| i06-negative-balance | no | PASS 2 / 25, 0 differences | REPLAY_i06 |
| i07-nontie-rounding | no | PASS 4 / 50, 0 differences | REPLAY_i07 |

Tests: pilot 36 unit + 10 integration, 0 failures/errors/skips → replay REPLAY_TESTS.

## 3. Pitfall reproduction

Reproduced in Step 5a (commands and output in the pitfalls skill): Java 17 enforcer, Testcontainers
1.20.3 vs Docker API, zoned overpunch decode, `rc.json`-only difference, driver `--out`/missing-jar
refusals, appended report. Reported by the pilot, not reproduced: abstract-base `@Container`
lifecycle, Docker Hub HTTP 429 (pulls succeeded), `mvn -o` without a cached enforcer (the
build ran online), and the PR #15 review findings (already fixed in the pilot code).

## 4. Proposed corrections to interest-conversion-standards (target architect decides)

The standard stays **PROPOSED**. Applied rows are one commit each on this branch; the banner
now says the pilot applied the standards once (commit `734ab68`).

| # | Standard | What the pilot's code shows | Proposed change | Found by | Status | Later ticket that must change code to match |
|---|---|---|---|---|---|---|
| 1 | §6 S14 rate-fallback log example | `InterestGroupProcessor` logs `event=rate-fallback run=… dd=DISCGRP op=READ status=23` (`INT_R03_defaultFallbackUsesPaddedDefaultRow`) | Example names that event and fields | pilot (review fix) | (applied) `02c7cb4` | INT-04 (`calc`/`job` re-home the processor; must log the same event) |
| 2 | §5 S10 INT-Q02 implementation point | EOF disposition is in `InterestGroupProcessor.process` → `AccountDelta.closing`, test `INT_Q02_finalAccountAtEofHasNoDelta`; there is no separate `acc…` type | Name the pilot's path and test as the example, keep "one named point" | Step 5a | (applied) `8662613` | INT-04 (its `calc` package must keep one named EOF point and an `INT_Q02_*` test) |
| 3 | §2 S05 commit boundary | `PilotJobConfig.WHOLE_RUN` = one chunk; a failure commits nothing (`INT_R10_missingAccountRollsBackEarlierGroupOutputs`) | Allow a whole-run transaction when the ticket excludes restart; restart tickets commit per group | pilot (review fix) | (applied) `82131a2` | INT-04 (adds restart, so must replace `WHOLE_RUN` with per-group commits and checkpoint) |
| 4 | §3 S09 PARM check | `TransactionIdSource` holds a PARM that is not 10 printable lab-profile bytes (`INT_H02_parmThatIsNotTenLabProfileBytesIsHeld`) | Say such a PARM is held, not re-encoded or substituted | pilot (review fix) | (applied) `fcb1959` | INT-04 (`intake`/`job` must carry the same check out of `pilot`) |
| 5 | §7 S16 RC evidence | `run_case.py` executes the real setup jobs and uses their observed RCs plus the Java exit code; nothing hardcoded | RC evidence comes from executed setup steps plus the converted step's exit code | pilot (finding 6) | (applied) `a196b48` | INT-06 (its `scripts/parity` gate must take RCs the same way) |
| 6 | §9 checklist | No checklist line separates converted-step RCs from setup-job RC provenance (pilot finding 6) | Add a checklist line for RC provenance | pilot (finding 6) | (open) – new review content, not wording; architect decides | – |
| 7 | §9 checklist l (filler/unassigned bytes) | TRAN filler and DESC tail written as spaces; legacy bytes are unassigned (pilot finding 4) | Decide spaces vs. proven host bytes | pilot (finding 4) | (open) – a choice the code makes by assumption; legacy evidence missing | – |
| 8 | §6 S12 abend RC/SYSOUT | Pilot produces FAILED + RC 12 + `pilot-failure.json` but not the legacy abend SYSOUT lines (ticket excluded them) | None to the wording; S12 stays as written and must be met by INT-04 | pilot (S12 FAIL partial) | (open) – scope, not a correction | – |

Also open, outside the standard (pilot finding 1, owner comparator/fixtures): golden `rc.json`
lists setup steps STEP05–STEP30 under INTCALC (`tools/parity/record.py`); not changed here.

## 5. What the skills do not cover

- What the code must be: naming, field-to-type mapping, numbers, encodings, quirks, rule ids, tests – `interest-conversion-standards`.
- Error-path, abend SYSOUT, restart, host (EBCDIC/z/OS) and handoff evidence: the pilot ran only normal-path cases, so no command for them is proven.
- INT-02 schema migrations, INT-03 codec specimens, INT-05 packet/handoff and INT-06 gate scripts: their own commands do not exist yet; each ticket adds its verification on top of these skills.
- The Docker Hub 429 and `mvn -o` workarounds: listed as reported by the pilot, not reproduced.
- Re-recording goldens (`make record`) and recorder finding 1: person action only.
- Running tickets in parallel on one estate: evidence and `work/<ticket>/` paths are separate, but `run_case.py` recreates the shared `datasets/`, so driver runs must still be serial per estate.
