# Manual Chrome UI test plan

Prepare the estate and jar using [README.md](README.md), then run `make lab`.
Open Chrome at http://localhost:8090. CLI commands supplement the mouse and
keyboard actions below. Leave Result empty until executed.

| # | Check | Action | Expected result | Result |
| --- | --- | --- | --- | --- |
| 1 | Initial load and metadata | Press F12, select Console, type the URL in the address bar and press Enter. Read the header. | No console errors. Header shows GnuCOBOL, container Java, Python, PostgreSQL, Maven, Spring Boot and Batch versions; baseline SHA/dirty state; clock 2022-07-05 10:00:00.00 and PARM 2022071800. | PASS — `lab/evidence/ui/row-01.png`; hard reload, baseline b2962225b345, required metadata and clean Console verified |
| 2 | All golden fixtures | Click Run all golden, wait, then click each of seven case tabs. Run `make lab-gate` in a terminal for comparison. | Every case shows both lanes, classified values and rule verdicts. Counts equal CLI golden: MATCH 273, INTENDED_CHANGE 0, UNRESOLVED 46 (engines agree 46, engines differ 0), UNCLASSIFIED 0; golden parity 7/7. GATE: PASS. Coverage is labeled for this run: golden, 17 of 21, not full gate 21 of 21. | PASS — `lab/evidence/ui/row-02.png`; saved `lab/evidence/golden-run.html`; CLI exit 0 |
| 3 | i02 rounding tie | Select i02-tie-half-cent, click Run selected golden, select UNRESOLVED in Filter. Inspect account 2003's transaction/account values; compare fixtures/interest/i02-tie-half-cent/README.md. | Both lanes: TRAN-AMT 12.35, ACCT-CURR-BAL 1247.85. Both are UNRESOLVED (engines agree), INT-Q01 OPEN; half-even alternatives 12.36 and 1247.86. i02 counts: MATCH 50, INTENDED_CHANGE 0, UNRESOLVED 9 (agree 9, differ 0), UNCLASSIFIED 0. | PASS — `lab/evidence/ui/row-03.png`; alternatives and OPEN status confirmed in register |
| 4 | Register deep link | Click INT-Q01 in an UNRESOLVED row; switch to the new tab. | The register row exists, is anchor-highlighted, and the visible Status column says OPEN. | PASS (attribute + headless popup verified; desktop Chrome folds new tabs) — `lab/evidence/ui/row-04.png`; INT-Q01 highlighted and OPEN; see popup diagnostic below |
| 5 | Every edge probe and fixture immutability | Run `git status --porcelain fixtures/` in a terminal. Click Run all edge probes, wait, then click E01–E11 tabs. Repeat the status command. | Every probe returns a result; fixture status empty before/after. Edge: MATCH 438, INTENDED_CHANGE 0, UNRESOLVED 67 (agree 65, differ 2), UNCLASSIFIED 0. E05–E07: both RC 12. E09: legacy RC 0 / target RC 12 TARGET_INTAKE_HELD; INT-H03 UNRESOLVED, target termination NOT JUDGED. E08 retains both runs. | PASS — `lab/evidence/ui/row-05.png`; E01–E11 inspected, counts matched, fixture status empty before/after; E05–E07 RC 12, E08 both runs retained, E09 documented unresolved host hold verified |
| 6 | Custom validation and recovery | Click Edit a golden input copy; choose i01 and click Load input copy, then Validate & run custom inputs. After completion focus ACCTDATA, press Ctrl+A and type a 301-character line. Click Validate & run custom inputs, then restore via Load input copy and run again. Keep Console open. | Valid inputs run both lanes. Invalid input shows readable LRECL 300 error inline, no queued run and no network/console error. Page stays usable; restored valid inputs run again. | PASS — `lab/evidence/ui/row-06.png`; initial/restored valid runs both RC 0; 301-character line rejected inline with length 301 exceeds LRECL 300, no queued run or Console errors |
| 7 | Page gate versus CLI gate | Run `make lab-gate` and note exit status. Run golden and edge sets on the page; inspect gate/count sections. | Full CLI exit 0 agrees with page PASS for each selected set; scope is explicit. Full CLI: MATCH 711, INTENDED_CHANGE 0, UNRESOLVED 113 (agree 111, differ 2), UNCLASSIFIED 0; golden parity 7/7, zero VIOLATES for both engines; coverage 21 of 21 and four questions no case can settle. | PASS — `lab/evidence/ui/row-07.png`; both UI scopes PASS and match CLI scope counts; CLI exit 0, full 711/0/113/0 (111 agree/2 differ), parity 7/7, zero violations, coverage 21/21, four unsettled questions |
| 8 | Rules board | Click Rules board in the header; switch tabs. | /board/ opens the interest rules board in a new tab. | PASS (attribute + headless popup verified; desktop Chrome folds new tabs) — `lab/evidence/ui/row-08.png`; /board/ rendered the interest rules board |
| 9 | Reload during run | Start an edge run. While queued/running press Ctrl+R. Immediately start i01 and wait. | No old result appears after reload; only the new run ID/result is displayed even if the earlier server job completes later. | PASS — `lab/evidence/ui/row-09.png`; running edge 31092c5a cleared on reload; new i01 451aad29 queued behind it and alone displayed after completion |
| 10 | Evidence links | Expand both lanes' output files/job logs. Click a dataset and INTCALC STEP15 SYSOUT link; switch tabs. | Evidence opens in new tabs; original results remain. Job log shows program transcript; stopped outputs are shown, not compared. Inspect links: target=_blank, rel=noopener. | PASS (attribute + headless popup verified; desktop Chrome folds new tabs) — `lab/evidence/ui/row-10.png`; both lists expanded; dataset and STEP15 transcript opened; Back retained 451aad29; E05 warning says stopped outputs shown, not compared; see popup diagnostic below |
| 11 | Back keeps results | With completed results visible, type http://localhost:8090/register in the same tab and press Enter, then Alt+Left. | Back restores the same run and selected case without starting a run or clearing results. | PASS — `lab/evidence/ui/row-11.png`; e79319ce/E05 restored without a run starting (input picker resets to E01, selected result remains E05) |
| 12 | Reload clears results | With completed results visible, press Ctrl+R. | Old results disappear; picker stays usable. No previous run is silently redisplayed. | PASS — `lab/evidence/ui/row-12.png`; e79319ce cleared; empty state persists, picker changed to i02 without redisplaying old results |

## Popup diagnostic qualification

Rows 4, 8 and 10 use the lead-approved qualification rather than claiming that
the desktop harness showed separate tabs. Desktop annotated DOM omitted target
on both the result link and static header link:
`<a href="/register#INT-Q01" rel="noopener">INT-Q01</a>` and
`<a href="/board/" rel="noopener">Rules board ↗</a>`.
Both plain clicks replaced the desktop tab; `curl http://localhost:29229/json`
showed one page target after the header click.

Fresh headless Playwright Chromium verified the unmodified link attributes:
`<a href="/register#INT-Q01" target="_blank" rel="noopener">INT-Q01</a>` and
`<a href="/board/" target="_blank" rel="noopener">Rules board ↗</a>`.
Clicking each produced a popup at the expected destination, with two pages and
the original lab page preserved. INT-Q01 was highlighted and OPEN.

The row-10 supplementary headless browser run 7626faee similarly verified:
`<a href="/evidence/work/lab/runs/7626faee020c448bb35ff1bb3e568644/i01-baseline/legacy/datasets/AWS.M2.CARDDEMO.SYSTRAN.G0001V00.txt" target="_blank" rel="noopener">datasets/AWS.M2.CARDDEMO.SYSTRAN.G0001V00.txt</a>`
and
`<a href="/evidence/work/lab/runs/7626faee020c448bb35ff1bb3e568644/i01-baseline/legacy/joblog/INTCALC/STEP15/SYSOUT.txt" target="_blank" rel="noopener">joblog/INTCALC/STEP15/SYSOUT.txt</a>`.
Each click produced a second page with the expected data/transcript while the
original lab URL and results remained. The desktop recording independently
shows those evidence contents and results restored with Back; the popup check
is supplementary scripted-browser evidence, not desktop-tab coverage.
