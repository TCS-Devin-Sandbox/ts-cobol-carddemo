# Step 1 fan-out: one agent per module

Baseline `922b1c23fb361b768d03f2b2c4d1e429e2fbbef0` (2026-11-19T13:53:48+05:30). Tool: mftb 0.2.1 (plugin `mftb`, entry point `estate-analysis`).

- Workflow run id: `wfr-b31f74d2275e4670b06c7d1fba25d560`
- Script: [fan-out-workflow.py](fan-out-workflow.py), started with `run_workflow` (workflow name `step1-module-fan-out`).
- Module ids read from `store/modules.json` on `demo/01-intake`: AUTH, CARDDEMO, TRANTYPE, VSAMMQ.
- Concurrency limit: 4. Retry policy: one retry per failed agent, then the module is reported as not analyzed.
- Result: 4 of 4 modules analyzed, all on the first attempt; no retry was needed.
- **Modules not analyzed: none.**

## Per module

Counts returned by the child are what its agent reported. Counts in the combined store are recomputed from `store/<MODULE>/` on `demo/01-intake` (programs.json, traces.json, open rows of findings.json).

| Module | Child session | Branch (tip) | PR | Programs (returned) | Edges (returned) | Open asks (returned) | Programs / edges / open asks in combined store | Result |
|---|---|---|---|---|---|---|---|---|
| AUTH | [devin-d79412d470dd4315ab579df8b1453ba3](https://tcs-demo.devinenterprise.com/sessions/d79412d470dd4315ab579df8b1453ba3) | `demo/01-intake-AUTH` (`0e8e7c4`) | [#8](https://github.com/TCS-Devin-Sandbox/ts-cobol-carddemo/pull/8) (open, base `demo/01-intake`) | 8 | 24 | 61 | 8 / 24 / 61 | analyzed (attempt 1) |
| CARDDEMO | [devin-bc23b96d5e244b1089f0830359ae0bf6](https://tcs-demo.devinenterprise.com/sessions/bc23b96d5e244b1089f0830359ae0bf6) | `demo/01-intake-CARDDEMO` (`14a4fb8`) | [#9](https://github.com/TCS-Devin-Sandbox/ts-cobol-carddemo/pull/9) (open, base `demo/01-intake`) | 32 | 229 | 296 | 32 / 229 / 296 | analyzed (attempt 1) |
| TRANTYPE | [devin-e164253d885e412bae49c45d07392145](https://tcs-demo.devinenterprise.com/sessions/e164253d885e412bae49c45d07392145) | `demo/01-intake-TRANTYPE` (`59d63af`) | [#7](https://github.com/TCS-Devin-Sandbox/ts-cobol-carddemo/pull/7) (open, base `demo/01-intake`) | 4 | 22 | 43 | 4 / 22 / 42 | analyzed (attempt 1) |
| VSAMMQ | [devin-47325dc420e74b0a977d69d0ed2af6c0](https://tcs-demo.devinenterprise.com/sessions/47325dc420e74b0a977d69d0ed2af6c0) | `demo/01-intake-VSAMMQ` (`62fbd6d`) | [#6](https://github.com/TCS-Devin-Sandbox/ts-cobol-carddemo/pull/6) (open, base `demo/01-intake`) | 2 | 18 | 34 | 2 / 18 / 34 | analyzed (attempt 1) |

### Differences between returned counts and the store

- TRANTYPE: child returned programs/edges/open asks 4/22/43; the combined store has 4/22/42. The store value is the one used in INTAKE.md and the call graph; the difference is reported, not corrected.

## What each child wrote

| Module | Store files | Report pages | Paths outside its two directories |
|---|---|---|---|
| AUTH | 13 | 4 | none |
| CARDDEMO | 18 | 25 | none |
| TRANTYPE | 13 | 3 | none |
| VSAMMQ | 11 | 3 | none |

Checked with `git diff --name-only origin/demo/01-intake...origin/demo/01-intake-<MODULE>`: every changed path is under `store/<MODULE>/` or `store/reports/<MODULE>/`. No child changed `store/catalog.json`, `store/modules.json`, `app/` or `fixtures/`.

## How the branches were combined

The parent copied each child's two directories onto `demo/01-intake` with `git checkout origin/demo/01-intake-<MODULE> -- store/<MODULE> store/reports/<MODULE>` and committed once. No merge commit was made and no pull request was merged, so the four child PRs stay open with base `demo/01-intake`. `mftb modules --check` still passes after the combine, and `mftb report-estate` then wrote `store/estate.json` and `store/reports/estate.html` (4 modules, 4 scanned, 23 connections between modules, 47 open asks).

## Notes

- The parent did none of the children's analysis. The parent-only work is the cross-module join in [call-graph.md](call-graph.md).
- Children closed some scanner findings with their own cited judgment (annotations, chain notes, online notes in `store/<MODULE>/`); closed findings are not counted as open asks above.
