"""Run against make lab: ~/envs/lab/bin/python lab/tests/check_links.py."""
from playwright.sync_api import sync_playwright


def check_links(page, label):
    links = page.locator('a[href]')
    invalid = links.evaluate_all("""links => links.filter(a =>
        a.getAttribute('target') !== '_blank' ||
        !a.relList.contains('noopener')).map(a => a.outerHTML)""")
    assert not invalid, invalid
    assert links.count() > 25
    assert page.locator('a[href^="/evidence/"]').count()
    print(f'{label}: {links.count()} links; every target=_blank rel=noopener')


def main():
    with sync_playwright() as playwright:
        browser = playwright.chromium.launch(headless=True)
        page = browser.new_page()
        errors = []
        page.on('pageerror', lambda error: errors.append(str(error)))
        page.goto('http://localhost:8090')
        page.wait_for_function("document.querySelectorAll('#golden option').length === 7")
        for case, picker, button in [
            ('i02-tie-half-cent', '#golden', 'Run selected golden'),
            ('E09', '#probe', 'Run selected probe'),
        ]:
            page.locator(picker).select_option(case)
            page.get_by_role('button', name=button).click()
            page.wait_for_function("id => state.report?.cases[0].case === id", arg=case, timeout=120000)
            check_links(page, case)
            page.locator('#class-filter').select_option('UNRESOLVED')
            check_links(page, case + ' filtered')
            decision = page.locator('.values-table a[href^="/register#"]').first
            with page.expect_popup() as opened:
                decision.click()
            opened.value.wait_for_load_state()
            assert '/register#INT-' in opened.value.url
            opened.value.close()
            print(f'{case}: classified decision click opens a new tab')
        assert not errors, errors
        print('LINK CHECK: PASS; page errors: []')
        browser.close()


if __name__ == '__main__':
    main()
