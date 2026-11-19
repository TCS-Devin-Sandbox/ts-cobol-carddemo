"""One test per row of modernization/rules/interest/ui-test-plan.md.

Interaction is mouse and keyboard only (click, fill, press, reload)."""
import csv
import io
import re
from collections import Counter

from playwright.sync_api import expect

from conftest import ROOT

REVIEWER = "Demo Reviewer"


def card(page, rid):
    return page.locator(f"article[data-id='{rid}']")


def decide_three(page):
    page.get_by_label("Reviewer name").click()
    page.keyboard.type(REVIEWER)
    card(page, "INT-R01").get_by_role("button", name="Approve INT-R01").click()
    card(page, "INT-Q02").get_by_role("button", name="Reject INT-Q02").click()
    card(page, "INT-H01").get_by_label("Comment on INT-H01", exact=True).click()
    page.keyboard.type("needs finance view")
    card(page, "INT-H01").get_by_role("button", name="Save comment on INT-H01").click()


def test_01_loads_without_console_errors(board, rules):
    expect(board.get_by_test_id("summary")).to_contain_text("Once a month the scheduler runs job INTCALC")
    expect(board.get_by_test_id("card")).to_have_count(len(rules))
    for r in rules:
        expect(card(board, r["id"]).get_by_test_id("statement")).to_have_text(r["statement"])
    board.mouse.wheel(0, 20000)
    assert board.errors == []


def test_02_card_count_per_type_matches_rules_yaml(board, rules):
    want = Counter(r["type"] for r in rules)
    assert want == {"RULE": 10, "QUIRK": 6, "HOLE": 9}
    for t, n in want.items():
        expect(board.locator(f"section[data-section='{t}'] article")).to_have_count(n)
        expect(board.locator(f"section[data-section='{t}'] h2")).to_contain_text(f"({n})")


def test_03_source_excerpt_collapsed_then_matches_cited_lines(board):
    ex = card(board, "INT-Q01").locator("details[data-cite='app/cbl/CBACT04C.cbl:464-465']")
    pre = ex.locator("pre")
    expect(pre).to_be_hidden()
    ex.locator("summary").click()
    expect(pre).to_be_visible()
    src = (ROOT / "app/cbl/CBACT04C.cbl").read_text(encoding="latin-1").splitlines()
    shown = pre.inner_text().splitlines()
    assert [re.sub(r"^\s*\d+ ", "", s, count=1) for s in shown] == [l.rstrip().expandtabs(8) for l in src[463:465]]
    assert shown[0].strip().startswith("464")


def test_04_approve_reject_comment_show_on_card_and_totals(board):
    decide_three(board)
    expect(card(board, "INT-R01").get_by_test_id("state")).to_have_text(f"APPROVED by {REVIEWER}")
    expect(card(board, "INT-Q02").get_by_test_id("state")).to_have_text(f"REJECTED by {REVIEWER}")
    expect(card(board, "INT-H01").get_by_test_id("state")).to_have_text(f'UNDECIDED by {REVIEWER} - comment: "needs finance view"')
    expect(board.get_by_test_id("total-all")).to_have_text("All 25: 1 approved, 1 rejected, 1 commented, 23 undecided")
    expect(board.get_by_test_id("total-RULE")).to_contain_text("RULE (10): 1 approved, 0 rejected, 0 commented, 9 undecided")
    expect(board.get_by_test_id("total-QUIRK")).to_contain_text("QUIRK (6): 0 approved, 1 rejected, 0 commented, 5 undecided")
    expect(board.get_by_test_id("total-HOLE")).to_contain_text("HOLE (9): 0 approved, 0 rejected, 1 commented, 9 undecided")


def test_05_decision_without_reviewer_is_refused(board):
    rv = board.get_by_label("Reviewer name")
    rv.click()
    board.keyboard.press("Control+A")
    board.keyboard.press("Delete")
    card(board, "INT-R02").get_by_role("button", name="Approve INT-R02").click()
    expect(card(board, "INT-R02").get_by_test_id("card-error")).to_have_text("Enter a reviewer name first - decision not recorded.")
    expect(card(board, "INT-R02")).to_have_class(re.compile(r"\bflag\b"))
    expect(card(board, "INT-R02").get_by_test_id("state")).to_have_text("UNDECIDED")
    expect(board.get_by_test_id("total-all")).to_have_text("All 25: 0 approved, 0 rejected, 0 commented, 25 undecided")


def test_06_decisions_persist_after_reload(board):
    decide_three(board)
    board.reload()
    expect(card(board, "INT-R01").get_by_test_id("state")).to_have_text(f"APPROVED by {REVIEWER}")
    expect(card(board, "INT-Q02").get_by_test_id("state")).to_have_text(f"REJECTED by {REVIEWER}")
    expect(card(board, "INT-H01").get_by_label("Comment on INT-H01", exact=True)).to_have_value("needs finance view")
    expect(board.get_by_test_id("total-all")).to_have_text("All 25: 1 approved, 1 rejected, 1 commented, 23 undecided")
    expect(board.get_by_label("Reviewer name")).to_have_value(REVIEWER)


def test_07_export_csv_one_row_per_card(board, rules, tmp_path):
    decide_three(board)
    with board.expect_download() as dl:
        board.get_by_role("button", name="Export CSV").click()
    assert dl.value.suggested_filename == "rules-INTCALC-decisions.csv"
    path = tmp_path / dl.value.suggested_filename
    dl.value.save_as(path)
    rows = list(csv.DictReader(io.StringIO(path.read_text())))
    assert list(rows[0]) == ["id", "type", "title", "decision", "comment", "reviewer", "decided_at"]
    assert [r["id"] for r in rows] == [r["id"] for r in rules]
    by = {r["id"]: r for r in rows}
    assert (by["INT-R01"]["decision"], by["INT-R01"]["reviewer"]) == ("APPROVED", REVIEWER)
    assert (by["INT-Q02"]["decision"], by["INT-Q02"]["reviewer"]) == ("REJECTED", REVIEWER)
    assert (by["INT-H01"]["comment"], by["INT-H01"]["decision"]) == ("needs finance view", "UNDECIDED")
    others = [r for i, r in by.items() if i not in ("INT-R01", "INT-Q02", "INT-H01")]
    assert all(r["decision"] == "UNDECIDED" and not r["comment"] and not r["reviewer"] for r in others)


def test_08_clear_leaves_every_card_undecided_after_reload(board):
    decide_three(board)
    board.get_by_role("button", name="Clear decisions").click()
    board.get_by_role("button", name="Confirm: clear all decisions").click()
    for _ in range(2):
        states = board.get_by_test_id("state").all_inner_texts()
        assert len(states) == 25 and set(states) == {"UNDECIDED"}
        expect(board.get_by_test_id("total-all")).to_have_text("All 25: 0 approved, 0 rejected, 0 commented, 25 undecided")
        board.reload()


def test_09_not_sme_signoff_notice_visible_without_scrolling(board):
    notice = board.get_by_test_id("notice")
    expect(notice).to_contain_text("NOT SME SIGN-OFF")
    expect(notice).to_be_in_viewport(ratio=1)
    board.mouse.wheel(0, 3000)
    expect(notice).to_be_in_viewport(ratio=1)
