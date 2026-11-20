# Step 5b build run - BLOCKED before the workflow

Branch `demo/05b-integration` was created from `demo/05a-skills` (bcc92be) and pushed.
The four skills are present under `.agents/skills/`. No workflow was written or run,
no child session was started and no ticket was built.

## Why: the group plan does not match the ticket queue

The step prompt assigns nine tickets (02-10) to four groups. The queue in
`modernization/design/tickets/` on `demo/05-pilot`, `demo/05a-skills`, `demo/04-design`
and `demo/04a-standards` has six (01-06); no branch or commit in the repository history
has a ticket 07-10.

| Prompt ticket | Prompt group | Ticket file in the repo | Its dependencies (ticket file) |
|---|---|---|---|
| 02 schema | g1 | 02-schema.md | 01 |
| 03 record codec | g1 | 03-record-codecs.md | 01 |
| 04 rules | g2 | 04-calculation-restart.md (calculation + restart, not "rules") | 02, 03 |
| 05 loader | g3 | 05-shadow-handoff.md (export packet + handoff, not "loader") | 02, 03 |
| 06 calculation step | g2 | 06-parity-gate.md (parity gate, not "calculation step") | 04, 05 |
| 07 export | g3 | missing | - |
| 08 error paths | g2 | missing | - |
| 09 parity gate | g4 | missing | - |
| 10 data-move kit | g3 | missing | - |

Dependency check (step 2) fails with the files that exist:

- g2 would hold 06-parity-gate.md, which needs 05 from g3; g3 starts at the same time as g2.
- Tickets 07, 08, 09 and 10 have no file, so there is no contract (Allowed files,
  acceptance, verification) for g2, g3 or g4 to build.
- The pre-granted file permissions are tied to the prompt's numbering (04 rules, 08 error
  paths, 09 parity gate) and do not map to the existing tickets 04-06.

## Options for a person to decide (none taken)

1. Supply ticket files 07-10 (and renumber 04-06 to match the prompt), then rerun step 5b unchanged.
2. Regroup onto the existing queue: g1 = 02, 03; g2 = 04; g3 = 05 (beside g2); g4 = 06;
   re-state the grants against 04-06.
3. Keep the queue and the prompt as they are: not possible, the dependency rule fails.

## Not produced

- `modernization/design/build-workflow.py`, its dry runs and printed prompts.
- Workflow run id, group sessions, PRs, per-ticket results, skill corrections, standards proposals.
- Final combined-check output on `demo/05b-integration` (nothing was built; the branch equals `demo/05a-skills` plus this file).
- PR from `demo/05b-integration` to `demo/05a-skills`.
