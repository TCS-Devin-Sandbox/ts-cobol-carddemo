# Step 5b build run - interest tickets 02-06 in four groups

Status: **workflow started** (results below are filled in after the run).

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
