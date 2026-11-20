"""One UI-only browser test for each row of lab/ui-test-plan.md."""
import re
import subprocess
from pathlib import Path

from playwright.sync_api import expect

ROOT = Path(__file__).resolve().parents[2]


def wait_for_result(page, count=1):
    expect(page.locator('#results .summary')).to_be_visible(timeout=120000)
    expect(page.locator('#progress')).to_be_hidden()
    expect(page.locator('[data-case-index]')).to_have_count(count)
    expect(page.locator('#error')).to_be_hidden()


def run_case(page, case='i01-baseline'):
    golden = case.startswith('i')
    page.locator('#golden' if golden else '#probe').select_option(case)
    page.locator('#run-golden' if golden else '#run-probe').click()
    wait_for_result(page)
    expect(page.locator('.case-tab.active')).to_have_text(case)


def run_set(page, name):
    page.locator('#all-golden' if name == 'golden' else '#all-edge').click()
    wait_for_result(page, 7 if name == 'golden' else 11)


def run_label(page):
    return page.locator('.summary .section-label').inner_text()


def assert_case_surface(page):
    lanes = page.locator('#case-result > .lanes > .lane')
    expect(lanes).to_have_count(2)
    expect(lanes.nth(0)).to_contain_text('Legacy estate')
    expect(lanes.nth(1)).to_contain_text('Joined interest job')
    expect(page.get_by_role('heading', name='Classified values')).to_be_visible()
    expect(page.get_by_role('heading', name='Independent rule verdicts')).to_be_visible()
    expect(page.locator('.values-table tbody tr').first).to_be_visible()
    expect(page.locator('.verdict-table')).not_to_contain_text('VIOLATES')


def assert_scope(page, gate_report, scope):
    expected = next(line for line in gate_report['summary'] if line.startswith(scope + ':'))
    expect(page.locator('.gate-lines')).to_contain_text(expected)
    expect(page.locator('.gate-lines')).to_contain_text(f'(for this run: {scope})')
    expect(page.locator('.gate-lines')).to_contain_text('GATE: PASS')


def assert_external_links(page):
    for link in page.locator('a[href]').all():
        expect(link).to_have_attribute('target', '_blank')
        assert 'noopener' in link.get_attribute('rel').split()


def open_popup(page, link):
    label = run_label(page)
    url = page.url
    with page.expect_popup() as opened:
        link.click()
    popup = opened.value
    popup.wait_for_load_state()
    assert page.url == url
    assert run_label(page) == label
    expect(page.locator('#results .summary')).to_be_visible()
    return popup


def test_row_01_initial_load_and_metadata(workbench, gate_report):
    page = workbench
    page.reload()
    expect(page.locator('#provenance')).to_contain_text(gate_report['baseline_sha'][:12])
    expect(page.locator('#provenance')).to_contain_text(re.compile(r'(clean|dirty) working tree'))
    expect(page.locator('#provenance')).to_contain_text('2022-07-05 10:00:00.00')
    expect(page.locator('#provenance')).to_contain_text('2022071800')
    for name in ['GnuCOBOL', 'Java (estate)', 'Python (host)', 'PostgreSQL server',
                 'Maven (host)', 'Spring Boot (pom)', 'Spring Batch (inherited pom)']:
        expect(page.locator('#toolchain')).to_contain_text(name)
    expect(page.locator('#toolchain')).not_to_contain_text('unavailable')
    expect(page.locator('#toolchain')).to_contain_text(re.compile(r'GnuCOBOL.*\d+\.\d+'))
    expect(page.locator('#empty')).to_be_visible()


def test_row_02_all_golden_fixtures(workbench, gate_report, tmp_path):
    page = workbench
    run_set(page, 'golden')
    assert_scope(page, gate_report, 'golden')
    expect(page.locator('.gate-lines')).to_contain_text('golden parity 7/7')
    expect(page.locator('.gate-lines')).to_contain_text('Rules exercised: 17 of 21')
    assert [x.inner_text() for x in page.locator('.stat b').all()] == ['273', '0', '46', '0']
    for tab in page.locator('.case-tab').all():
        tab.click()
        assert_case_surface(page)
    with page.expect_download() as saved:
        page.get_by_role('button', name='Save page').click()
    snapshot = tmp_path / 'golden.html'
    saved.value.save_as(snapshot)
    html = snapshot.read_text()
    assert '<script' not in html and '<style>' in html
    for case in gate_report['cases']:
        if case['set'] == 'golden':
            assert case['case'] in html


def test_row_03_i02_rounding_tie(workbench):
    page = workbench
    run_case(page, 'i02-tie-half-cent')
    page.locator('#class-filter').select_option('UNRESOLVED')
    for field, amount in [('transaction 00000002003/01/0001 TRAN-AMT', '12.35'),
                          ('account 00000002003 ACCT-CURR-BAL', '1247.85')]:
        row = page.locator('.values-table tbody tr').filter(has=page.get_by_text(field, exact=True))
        expect(row.locator('td').nth(2)).to_have_text(amount)
        expect(row.locator('td').nth(3)).to_have_text(amount)
        expect(row).to_contain_text('UNRESOLVED')
        expect(row).to_contain_text('engines agree')
        expect(row.get_by_role('link', name='INT-Q01')).to_be_visible()
    expect(page.locator('.gate-lines')).to_contain_text('UNRESOLVED 9 (engines agree 9, engines differ 0)')
    popup = open_popup(page, page.locator('.values-table a[href="/register#INT-Q01"]').first)
    expect(popup.locator('#INT-Q01')).to_contain_text('OPEN')
    expect(popup.locator('#INT-Q01')).to_contain_text('Round half to even')
    expect(popup.locator('#INT-Q01')).to_contain_text('12.35 -> 12.36')
    expect(popup.locator('#INT-Q01')).to_contain_text('1247.85 -> 1247.86')
    popup.close()


def test_row_04_register_deep_link(workbench):
    page = workbench
    run_case(page, 'i02-tie-half-cent')
    assert_external_links(page)
    popup = open_popup(page, page.locator('.values-table a[href="/register#INT-Q01"]').first)
    expect(popup).to_have_url(re.compile(r'/register#INT-Q01$'))
    expect(popup.locator('tr:target')).to_have_attribute('id', 'INT-Q01')
    headers = popup.locator('th').all_text_contents()
    expect(popup.locator('#INT-Q01 td').nth(headers.index('Status'))).to_have_text('OPEN')
    popup.close()


def test_row_05_every_edge_probe_and_immutable_fixtures(workbench, gate_report):
    page = workbench
    def fixture_status():
        return subprocess.check_output(['git', 'status', '--porcelain', 'fixtures/'], cwd=ROOT, text=True)
    assert fixture_status() == ''
    try:
        run_set(page, 'edge')
        assert_scope(page, gate_report, 'edge')
        assert [x.inner_text() for x in page.locator('.stat b').all()] == ['438', '0', '67', '0']
        for index in range(1, 12):
            case = f'E{index:02d}'
            page.get_by_role('button', name=case, exact=True).click()
            assert_case_surface(page)
            page.get_by_text('Probe design & host-evidence limits', exact=True).click()
            expect(page.locator('#case-result details.card pre').first).not_to_be_empty()
            if case in ('E05', 'E06', 'E07'):
                for lane in page.locator('.lane').all():
                    expect(lane).to_contain_text('RC 12')
                expect(page.locator('#case-result')).to_contain_text('Output files are shown, not compared')
            if case == 'E08':
                page.get_by_text('E08 · Run 1 evidence (before second replay)', exact=True).click()
                expect(page.locator('.lane')).to_have_count(4)
                expect(page.locator('#case-result')).to_contain_text('1236.50')
                expect(page.locator('#case-result')).to_contain_text('1237.50')
            if case == 'E09':
                expect(page.locator('.lane').nth(0)).to_contain_text('RC 0')
                expect(page.locator('.lane').nth(1)).to_contain_text('RC 12')
                expect(page.locator('.lane').nth(1)).to_contain_text('TARGET_INTAKE_HELD')
                expect(page.locator('.verdict-table')).to_contain_text('NOT JUDGED')
                expect(page.locator('.values-table')).to_contain_text('UNRESOLVED')
                expect(page.locator('#case-result')).to_contain_text('INT-H03 is open')
                assert_external_links(page)
    finally:
        assert fixture_status() == ''


def test_row_06_custom_validation_and_recovery(workbench):
    page = workbench
    page.get_by_role('button', name='Edit a golden input copy').click()
    page.locator('#custom-source').select_option('i01-baseline')
    page.get_by_role('button', name='Load input copy').click()
    page.get_by_role('button', name='Validate & run custom inputs').click()
    wait_for_result(page)
    assert_case_surface(page)
    for lane in page.locator('.lane').all():
        expect(lane).to_contain_text('RC 0')
    initial = run_label(page)
    editor = page.locator('[data-dataset$="ACCTDATA.PS.txt"]')
    editor.click()
    editor.press('ControlOrMeta+A')
    editor.press_sequentially('x' * 301)
    page.get_by_role('button', name='Validate & run custom inputs').click()
    expect(page.locator('#error')).to_contain_text('length 301 exceeds LRECL 300')
    expect(page.locator('#progress')).to_be_hidden()
    expect(page.locator('#results')).to_be_empty()
    expect(page.get_by_role('button', name='Load input copy')).to_be_enabled()
    page.get_by_role('button', name='Load input copy').click()
    expect(editor).not_to_have_value('x' * 301)
    page.get_by_role('button', name='Validate & run custom inputs').click()
    wait_for_result(page)
    assert run_label(page) != initial
    for lane in page.locator('.lane').all():
        expect(lane).to_contain_text('RC 0')


def test_row_07_page_gate_matches_cli(workbench, gate_report):
    page = workbench
    assert gate_report['passed'] and not gate_report['violations']
    assert 'total: MATCH 711, INTENDED_CHANGE 0, UNRESOLVED 113 (engines agree 111, engines differ 2), UNCLASSIFIED 0' in gate_report['summary']
    assert any(line.startswith('Rules exercised: 21 of 21') for line in gate_report['summary'])
    assert any(line.startswith('Open questions no case can settle: 4') for line in gate_report['summary'])
    for scope in ['golden', 'edge']:
        run_set(page, scope)
        assert_scope(page, gate_report, scope)
        expect(page.locator('.gate-lines')).to_contain_text('Open questions no case can settle: 4')


def test_row_08_rules_board_popup(workbench):
    page = workbench
    run_case(page)
    popup = open_popup(page, page.get_by_role('link', name='Rules board'))
    expect(popup).to_have_url(re.compile(r'/board/$'))
    expect(popup.get_by_role('heading', level=1)).to_have_text('INTCALC monthly interest - business rules review board')
    popup.close()


def test_row_09_reload_during_run(workbench):
    page = workbench
    page.locator('#all-edge').click()
    expect(page.locator('#progress')).to_contain_text(re.compile(r'(Queued|Running).*· [0-9a-f]{8}'), timeout=10000)
    old_id = page.locator('#progress').inner_text().split('·')[-1].strip()
    page.reload()
    expect(page.locator('#golden option')).to_have_count(7)
    expect(page.locator('#results')).to_be_empty()
    run_case(page)
    assert old_id.upper() not in run_label(page).upper()
    expect(page.locator('.case-tab')).to_have_text('i01-baseline')
    expect(page.locator('.case-header')).to_contain_text('i01-baseline')
    expect(page.locator('.case-header')).not_to_contain_text('E09')


def test_row_10_evidence_popups(workbench):
    page = workbench
    run_case(page)
    assert_external_links(page)
    for lane in page.locator('.lane').all():
        lane.get_by_text(re.compile(r'Output files & job logs')).click()
        for link in [lane.locator('a[href*="/datasets/"]').first,
                     lane.locator('a[href$="/joblog/INTCALC/STEP15/SYSOUT.txt"]').first
                     if lane.locator('a[href$="/joblog/INTCALC/STEP15/SYSOUT.txt"]').count()
                     else lane.locator('a[href$="/sysout/INTCALC.STEP15.txt"]').first]:
            is_sysout = 'SYSOUT' in link.inner_text().upper()
            popup = open_popup(page, link)
            expect(popup.locator('body')).not_to_be_empty()
            if is_sysout:
                expect(popup.locator('body')).to_contain_text('CBACT04C')
            popup.close()
    run_case(page, 'E05')
    expect(page.locator('#case-result')).to_contain_text('Output files are shown, not compared')


def test_row_11_back_keeps_results(workbench, lab_url):
    page = workbench
    run_set(page, 'golden')
    page.get_by_role('button', name='i02-tie-half-cent', exact=True).click()
    label = run_label(page)
    page.goto(lab_url + '/register')
    expect(page.get_by_role('heading', level=1)).to_have_text('Interest decision register')
    page.go_back()
    expect(page.locator('#results .summary')).to_be_visible()
    assert run_label(page) == label
    expect(page.locator('.case-tab.active')).to_have_text('i02-tie-half-cent')
    expect(page.locator('#progress')).to_be_hidden()


def test_row_12_reload_clears_results(workbench):
    page = workbench
    run_case(page)
    page.reload()
    expect(page.locator('#golden option')).to_have_count(7)
    expect(page.locator('#empty')).to_be_visible()
    expect(page.locator('#results')).to_be_empty()
    expect(page.get_by_role('button', name='Save page')).to_be_disabled()
    page.locator('#golden').select_option('i02-tie-half-cent')
    expect(page.locator('#results')).to_be_empty()
