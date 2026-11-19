# UI test plan - rules-INTCALC.html

Board under test: `modernization/rules/interest/rules-INTCALC.html`, opened over
`http://localhost:8765/rules-INTCALC.html` (`python3 -m http.server 8765` in
`modernization/rules/interest/`). Driven with mouse and keyboard only; no calls into
page functions. Browser: Chrome, fresh storage for the board (clear the
`intcalc-board:*` keys by using the board's own Clear button). Automated copy of the
same rows: `e2e/test_board.py`, run with `make board-e2e`.

Expected counts come from `rules.yaml`: 25 entries = 10 RULE, 6 QUIRK, 9 HOLE.

| # | Check | Action | Expected result |
|---|---|---|---|
| 1 | Loads cleanly | Open the board URL with DevTools console open; scroll the page top to bottom. | No console errors or warnings from the page. The business summary is shown under the title; all 25 cards are rendered with id, statement, call path and fixture cases. |
| 2 | Counts match rules.yaml | Read the section headings RULE / QUIRK / HOLE and count the cards in each; compare with `grep -c "type: RULE\|QUIRK\|HOLE"` per type in `rules.yaml`. | Headings and visible cards show RULE 10, QUIRK 6, HOLE 9; each equals the number of entries of that type in `rules.yaml`. |
| 3 | Source excerpt | On card INT-Q01 look at "Source app/cbl/CBACT04C.cbl:464-465"; click it; compare the shown lines with lines 464-465 of `app/cbl/CBACT04C.cbl`. | Before the click the excerpt body is hidden. After the click it shows line numbers 464-465 and the text `= ( TRAN-CAT-BAL * DIS-INT-RATE) / 1200` exactly as in the source file. |
| 4 | Decide three cards | Type reviewer name "Demo Reviewer". Click Approve on INT-R01, Reject on INT-Q02, type "needs finance view" in the comment box of INT-H01 and click Save comment. | INT-R01 shows "APPROVED by Demo Reviewer"; INT-Q02 shows "REJECTED by Demo Reviewer"; INT-H01 shows "UNDECIDED by Demo Reviewer - comment: \"needs finance view\"". Totals show All 25: 1 approved, 1 rejected, 1 commented, 23 undecided; RULE, QUIRK and HOLE totals show the same split per type. |
| 5 | Reviewer required | Select the reviewer field, clear it with the keyboard (Ctrl+A, Delete); click Approve on INT-R02. | The card is outlined red and shows "Enter a reviewer name first - decision not recorded."; INT-R02 stays UNDECIDED; totals unchanged. |
| 6 | Persistence | Re-type the reviewer name, reload the page (F5). | INT-R01 APPROVED, INT-Q02 REJECTED and the INT-H01 comment are still shown on the cards and in the totals; the reviewer field still holds the name. |
| 7 | Export CSV | Click Export CSV; open the downloaded `rules-INTCALC-decisions.csv`. | Header `id,type,title,decision,comment,reviewer,decided_at`, then exactly 25 rows, one per card; INT-R01 APPROVED / Demo Reviewer, INT-Q02 REJECTED / Demo Reviewer, INT-H01 comment "needs finance view"; all others UNDECIDED with empty comment and reviewer. |
| 8 | Clear decisions | Click Clear decisions, then Confirm: clear all decisions; reload the page. | Every card shows UNDECIDED with no comment, before and after the reload; totals show All 25: 0 approved, 0 rejected, 0 commented, 25 undecided. |
| 9 | Notice above the fold | Reload at a 1280x720 window, without scrolling; then scroll down. | The banner "NOT SME SIGN-OFF ..." is fully visible at the top of the viewport on load and stays pinned at the top while scrolling. |

Clean-up: after row 8 the demo decisions are cleared; delete the downloaded CSV, which is
never committed (`.gitignore` excludes `rules-INTCALC-decisions*.csv`).
