#!/usr/bin/env node
/* Page check for every generated HTML page (Step 1).
 * Drives a real Chrome over CDP with mouse clicks and key presses (no reading of the HTML file):
 *   node page_check.js            (needs `npm i playwright-core`, Chrome listening on CDP_URL)
 * Writes modernization/legacy/page-check.md, page-check.json and one screenshot per page. */
const fs = require('fs'), path = require('path');
const { chromium } = require(process.env.PLAYWRIGHT_CORE || 'playwright-core');
const ROOT = process.cwd(), OUT = 'modernization/legacy/page-check';
const G = JSON.parse(fs.readFileSync('modernization/legacy/graph.json'));
const SHA = G.meta.baseline_sha;
const J = p => JSON.parse(fs.readFileSync(p));
const walk = d => fs.readdirSync(d, { withFileTypes: true }).flatMap(e => e.isDirectory() ? walk(path.join(d, e.name)) : [path.join(d, e.name)]);
const pages = ['modernization/legacy/architecture-as-is.html', 'modernization/legacy/call-graph.html', 'store/reports/estate.html',
  ...walk('store/reports').filter(f => f.endsWith('.html') && f !== 'store/reports/estate.html').sort()];
const CHECKS = ['no console errors', 'no missing assets', 'links work', 'tabs work', 'filters work', 'collapsibles work', 'diagrams render', 'counts equal data', 'baseline SHA shown'];

let LAST = '';
async function visible(h) { try { return await h.isVisible(); } catch { return false; } }
async function mclick(page, h) {           // real mouse click at the element's centre
  await h.scrollIntoViewIfNeeded(); const b = await h.boundingBox(); if (!b) return false;
  await page.mouse.click(b.x + Math.min(b.width / 2, 40), b.y + b.height / 2); return true;
}

async function testCollapsibles(page, st) {
  for (let guard = 0; guard < 4000; guard++) {
    const hs = await page.$$('details:not([data-pc])'); let did = false;
    for (const d of hs) {
      const s = await d.$(':scope > summary'); if (!s || !(await visible(s))) continue;
      const before = await d.evaluate(e => e.open); LAST = 'collapsible ' + await s.evaluate(e => e.textContent.trim().slice(0, 40));
      await mclick(page, s); const mid = await d.evaluate(e => e.open);
      await d.evaluate(e => e.setAttribute('data-pc', '1'));
      st.n++; if (mid === before) { st.bad.push(await s.evaluate(e => e.textContent.trim().slice(0, 60))); }
      if (!mid) { await mclick(page, s); if (!(await d.evaluate(e => e.open))) st.bad.push('reopen: ' + await s.evaluate(e => e.textContent.trim().slice(0, 60))); }
      did = true; break;          // DOM may have changed (children now visible): rescan; opened ones stay open so their children get tested
    }
    if (!did) break;
  }
}
const rowsShown = h => h.evaluate(inp => { const host = inp.closest('.panel,section,details,div.body,main,body'); let t = inp.closest('.toolbar,.bar'); let tbl = null;
  for (let n = t && t.nextElementSibling; n && !tbl; n = n.nextElementSibling) tbl = n.matches('table') ? n : n.querySelector('table');
  tbl = tbl || (host && host.querySelector('table')); const cnt = t && t.querySelector('.count');
  return { rows: tbl ? [...tbl.querySelectorAll('tbody tr')].filter(r => r.offsetParent !== null).length : -1, count: cnt ? cnt.textContent : '' }; });
async function testFilters(page, st) {
  for (const inp of await page.$$('input[type=search]:not([data-pc]),input[type=text]:not([data-pc]),input:not([type]):not([data-pc])')) {
    if (!(await visible(inp))) continue; await inp.evaluate(e => e.setAttribute('data-pc', '1'));
    const a = await rowsShown(inp); LAST = 'text filter in ' + await inp.evaluate(e => (e.closest('[id]') || {}).id || '?'); await mclick(page, inp); await page.keyboard.type('zzqqxx', { delay: 5 }); await page.waitForTimeout(60);
    const b = await rowsShown(inp); await page.keyboard.press('Control+A'); await page.keyboard.press('Backspace'); await page.waitForTimeout(60);
    const c = await rowsShown(inp); st.n++;
    const narrowed = a.rows <= 0 ? true : (b.rows < a.rows || b.count !== a.count), restored = c.rows === a.rows && c.count === a.count;
    if (!narrowed || !restored) st.bad.push(`filter rows ${a.rows}->${b.rows}->${c.rows} count "${a.count}"->"${b.count}"->"${c.count}"`);
  }
  for (const sel of await page.$$('select:not([data-pc])')) {
    if (!(await visible(sel))) continue; await sel.evaluate(e => e.setAttribute('data-pc', '1'));
    const n = await sel.evaluate(e => e.options.length); if (n < 2) continue;
    const v0 = await sel.evaluate(e => e.value); LAST = 'select in ' + await sel.evaluate(e => ((e.closest('[id]') || {}).id || '?') + ' [' + [...e.options].slice(0, 2).map(o => o.text).join(' / ') + ']'); await sel.focus(); await page.keyboard.press('ArrowDown'); await page.waitForTimeout(40);
    const v1 = await sel.evaluate(e => e.value); await page.keyboard.press('ArrowUp'); await page.waitForTimeout(40);
    const v2 = await sel.evaluate(e => e.value); st.ns++; if (v1 === v0 || v2 !== v0) st.bad.push(`select ${v0}->${v1}->${v2}`);
  }
  for (const lab of await page.$$('.seg label')) {           // certainty toggles on the two modernization pages
    if (!(await visible(lab))) continue; if (await lab.evaluate(e => e.dataset.pc)) continue; await lab.evaluate(e => e.dataset.pc = '1');
    await mclick(page, lab); st.nr++; if (!(await lab.evaluate(e => e.querySelector('input').checked))) st.bad.push('radio not selected: ' + await lab.textContent());
  }
  for (const lab of await page.$$('.seg label:first-child')) { if (await visible(lab)) await mclick(page, lab); }
}

async function checkPage(browser, rel) {
  const page = await browser.contexts()[0].newPage(); await page.setViewportSize({ width: 1500, height: 1000 });
  const errs = [], failed = [], ext = [];
  page.on('console', m => { if (m.type() === 'error') errs.push(m.text().slice(0, 200)); });
  page.on('pageerror', e => errs.push((String(e) + ' [after: ' + LAST + ']' + (process.env.DEBUG ? (e.stack || '') : '')).slice(0, 900)));
  page.on('requestfailed', r => failed.push(r.url()));
  page.on('request', r => { if (!r.url().startsWith('file://') && !r.url().startsWith('data:')) ext.push(r.url()); });
  const url = 'file://' + path.join(ROOT, rel); await page.goto(url); await page.waitForTimeout(300);
  const r = {}, note = {};
  // tabs (mouse), and inside every tab state: filters (keyboard), selects (keyboard), collapsibles (mouse)
  const tabs = { n: 0, bad: [] }, fil = { n: 0, ns: 0, nr: 0, bad: [] }, col = { n: 0, bad: [] };
  await testFilters(page, fil); await testCollapsibles(page, col);
  const groups = await page.$$('.tabs');
  for (const gph of groups) for (const b of await gph.$$('button')) {
    if (!(await visible(b))) continue; LAST = 'tab ' + await b.evaluate(e => e.textContent.trim().slice(0, 30)); await mclick(page, b); await page.waitForTimeout(40); tabs.n++;
    const ok = await b.evaluate(e => e.classList.contains('active') || e.getAttribute('aria-selected') === 'true');
    const panels = await page.evaluate(() => [...document.querySelectorAll('.panel')].filter(p => p.offsetParent !== null).length);
    if (!ok || panels < 1) tabs.bad.push(await b.evaluate(e => e.textContent.trim().slice(0, 30)));
    await testFilters(page, fil); await testCollapsibles(page, col);
  }
  if (groups.length && rel.startsWith('modernization')) {       // keyboard: arrow keys move between tabs
    const first = await groups[0].$('button'); await mclick(page, first); await page.keyboard.press('ArrowRight'); tabs.n++;
    const sel = await page.evaluate(() => [...document.querySelectorAll('.tabs button')].findIndex(b => b.getAttribute('aria-selected') === 'true'));
    if (sel !== 1) tabs.bad.push('ArrowRight did not move to tab 2'); await page.keyboard.press('ArrowLeft');
  }
  r['tabs work'] = tabs.n === 0 ? 'PASS' : (tabs.bad.length ? 'FAIL' : 'PASS'); note['tabs work'] = tabs.n ? `${tabs.n} tab clicks` + (tabs.bad.length ? '; failed: ' + tabs.bad.join(', ') : '') : 'page has no tabs';
  r['filters work'] = fil.bad.length ? 'FAIL' : 'PASS'; note['filters work'] = `${fil.n} text filters typed, ${fil.ns} selects keyed, ${fil.nr} toggles clicked` + (fil.bad.length ? '; failed: ' + fil.bad.slice(0, 4).join(' | ') : '');
  r['collapsibles work'] = col.bad.length ? 'FAIL' : 'PASS'; note['collapsibles work'] = `${col.n} collapsibles toggled` + (col.bad.length ? '; failed: ' + col.bad.slice(0, 4).join(' | ') : '');
  // diagrams
  const svgs = await page.evaluate(() => [...document.querySelectorAll('svg')].map(s => { const b = s.getBBox ? s.getBBox() : { width: 0, height: 0 }; return { id: s.id || s.getAttribute('class') || '', w: b.width, h: b.height, kids: s.querySelectorAll('*').length }; }));
  let dOk = svgs.every(s => s.kids > 0 && s.w > 0 && s.h > 0), dNote = `${svgs.length} SVG rendered with non-zero size`;
  if (rel.endsWith('call-graph.html')) {
    await mclick(page, await page.$('#t-diagram')); const node = await page.$('#graph .node[data-id="COSGN00C"]'); await mclick(page, node);
    const t = await page.textContent('#dsel-title').catch(() => ''); await page.keyboard.press('Escape'); const cleared = !(await page.$('#dsel-title'));
    const nn = await page.$$eval('#graph .node', x => x.length), ne = await page.$$eval('#graph .edge', x => x.length), want = G.edges.filter(e => 0).length;
    dOk = dOk && /COSGN00C: \d+ PROVEN edges/.test(t) && cleared && nn > 0 && ne > 0; dNote += `; program diagram ${nn} nodes, ${ne} edges; node click listed "${t}", Escape cleared it`;
  }
  if (rel.endsWith('architecture-as-is.html')) { dOk = true; const seen = [];
    for (const t of ['estate', 'online', 'batch', 'data']) { await mclick(page, await page.$('#t-' + t)); const m = await page.$eval('#dg-' + t, s => { const b = s.getBBox(); return { w: b.width, h: b.height, boxes: s.querySelectorAll('.box').length, arrows: s.querySelectorAll('.arrow').length }; });
      seen.push(`${t}: ${m.boxes} boxes, ${m.arrows} arrows`); if (!(m.w > 0 && m.h > 0 && m.boxes > 0 && m.arrows > 0)) dOk = false; }
    dNote = '4 diagrams, each measured in its own tab - ' + seen.join('; '); await mclick(page, await page.$('#t-estate')); }
  else if (svgs.some(s => !(s.w > 0 && s.h > 0))) {     // an SVG inside a closed section has no size: measure again with every section open
    const again = await page.evaluate(() => { document.querySelectorAll('details').forEach(d => d.open = true); return [...document.querySelectorAll('svg')].filter(s => s.offsetParent !== null || s.getBBox().width > 0).map(s => { const b = s.getBBox(); return b.width > 0 && b.height > 0; }); });
    dOk = again.length > 0 && again.every(Boolean); dNote = `${again.length} visible SVG rendered with non-zero size`; }
  if (!svgs.length) dNote = 'page has no diagram';
  r['diagrams render'] = dOk ? 'PASS' : 'FAIL'; note['diagrams render'] = dNote;
  // counts equal the data behind them
  const cnt = []; const eq = (label, shown, want) => cnt.push({ label, shown: String(shown), want: String(want), ok: String(shown) === String(want) });
  const card = id => page.textContent('#card-' + id).catch(() => null);
  const mm = rel.match(/^store\/reports\/([A-Z0-9]+)\//), M = mm && mm[1];
  if (rel.endsWith('call-graph.html')) {
    const S = G.summary, E = G.edges, n = (c, k) => E.filter(e => e.cat === c && e.certainty === k).length;
    for (const [id, want] of [['programs', G.programs.length], ['leaves', G.leaves.length], ['jobs', G.jobs.length], ['pp', n('program', 'PROVEN')], ['pc', n('program', 'CANDIDATE')], ['cross', E.filter(e => e.cross).length],
      ['asks', G.asks.length], ['uncalled', G.uncalled.length], ['unref', G.unreferenced.length], ['unsched', G.unscheduled.length], ['dis', G.disagreements.length]]) eq('card ' + id, await card(id), want);
    for (const [tab, c] of [['program', 'program'], ['job', 'job'], ['data', 'data'], ['transaction', 'transaction'], ['schedule', 'schedule']]) {
      await mclick(page, await page.$('#t-' + tab));
      for (const k of ['PROVEN', 'CANDIDATE']) { await mclick(page, await page.$(`#p-${tab} .seg input[value=${k}] >> xpath=..`)); eq(`${tab} ${k} rows`, await page.$$eval(`#tbl-${tab} tbody tr`, x => x.length), n(c, k)); }
      await mclick(page, await page.$(`#p-${tab} .seg label`));
    }
    for (const [tab, tbl, want] of [['copybook', 'copybook', G.copybooks.filter(c => c.programs.length > 1).length], ['unsched', 'unsched', G.unscheduled.length], ['asks', 'asks', G.asks.length], ['uncalled', 'uncalled', G.uncalled.length], ['uncalled', 'unref', G.unreferenced.length], ['dis', 'dis', G.disagreements.length]]) {
      await mclick(page, await page.$('#t-' + tab)); eq(`${tbl} rows`, await page.$$eval(`#tbl-${tbl} tbody tr`, x => x.length), want); }
    await mclick(page, await page.$('#t-diagram'));
  } else if (rel.endsWith('architecture-as-is.html')) {
    const cics = G.programs.filter(p => p.cics).length, tx = G.edges.filter(e => e.cat === 'transaction').length;
    for (const [id, want] of [['modules', G.meta.modules.length], ['programs', G.programs.length], ['cics', cics], ['batchp', G.programs.length - cics], ['tx', tx], ['jobs', G.jobs.length], ['unsched', G.unscheduled.length], ['stores', G.stores.length], ['leaves', G.legacy_members.length]]) eq('card ' + id, await card(id), want);
    await mclick(page, await page.$('#t-online')); eq('transactions rows', await page.$$eval('#tbl-tx tbody tr', x => x.length), tx);
    await mclick(page, await page.$('#t-data')); eq('stores rows', await page.$$eval('#tbl-stores tbody tr', x => x.length), G.stores.length);
    await mclick(page, await page.$('#t-shared')); eq('shared stores rows = card', await page.$$eval('#tbl-sharedstores tbody tr', x => x.length), await card('shared'));
    await mclick(page, await page.$('#t-unknown')); eq('unknown rows = card', await page.$$eval('#p-unknown tbody tr', x => x.length), await card('unknown'));
    await mclick(page, await page.$('#t-estate'));
  } else if (rel === 'store/reports/estate.html') {
    const s = J('store/estate.json').summary, cards = await page.$$eval('.cards .card', cs => Object.fromEntries(cs.map(c => [c.querySelector('.k').textContent.trim().toLowerCase(), c.querySelector('.n').textContent.trim()])));
    eq('Files', cards.files, s.files); eq('Artifacts', cards.artifacts, s.placeable); eq('Modules', cards.modules, s.modules); eq('Shared', cards.shared, s.shared); eq('Unplaced', cards.unplaced, s.unplaced); eq('Unclassified', cards.unclassified, s.unclassified + s.unsupported);
  } else if (M) {
    const badge = async name => page.$$eval('#inv-tabs button', (bs, name) => { const b = bs.find(b => b.childNodes[0].textContent.trim() === name); return b ? b.querySelector('.n').textContent.trim() : null; }, name);
    const P = J(`store/${M}/programs.json`).programs, jobs = J(`store/${M}/jobs.json`).jobs;
    if (rel.endsWith('-report.html')) { eq('Programs tab', await badge('Programs'), P.filter(p => p.kind === 'program').length); eq('Copybooks tab', await badge('Copybooks'), P.filter(p => p.kind === 'copybook').length); eq('Jobs tab', await badge('Jobs'), jobs.length); }
    else if (rel.includes('/online/')) { const id = rel.match(/online-(\w+)\.html/)[1], t = J(`store/${M}/online.json`).transactions.find(t => t.transid === id);
      eq('Programs tab = programs the transaction reaches (in inventory)', await badge('Programs'), t ? t.programs.filter(p => p.in_inventory).length : 'transaction not in online.json'); }
    else if (rel.includes('/chains/')) { const id = rel.match(/chain-(.+)\.html/)[1], c = J(`store/${M}/schedule.json`).tables.find(t => t.name === id);
      eq('Schedules tab = jobs of the scheduler table', await badge('Schedules'), c ? c.jobs.length : 'table not in schedule.json'); }
  }
  r['counts equal data'] = cnt.length && cnt.every(c => c.ok) ? 'PASS' : 'FAIL';
  note['counts equal data'] = `${cnt.filter(c => c.ok).length} of ${cnt.length} counts equal` + (cnt.some(c => !c.ok) ? '; differ: ' + cnt.filter(c => !c.ok).map(c => `${c.label} shows ${c.shown}, data ${c.want}`).join('; ') : '');
  // baseline SHA
  const body = await page.evaluate(() => document.body.innerText);
  r['baseline SHA shown'] = body.includes(SHA) ? 'PASS' : 'FAIL'; note['baseline SHA shown'] = body.includes(SHA) ? SHA : 'the page does not print the baseline SHA (generated by mftb, which has no such field; not hand-edited)';
  // links: every in-page anchor is clicked; every link to another file is clicked, checked and navigated back
  const lk = { n: 0, bad: [] };
  const hrefs = await page.$$eval('a[href]', as => [...new Set(as.map(a => a.getAttribute('href')))]);
  for (const h of hrefs) {
    lk.n++; LAST = 'link ' + h;
    if (h.startsWith('#')) { const ok = await page.evaluate(h => !!document.getElementById(decodeURIComponent(h.slice(1)).split('?')[0]), h); if (!ok) { lk.bad.push(h + ' (no target)'); continue; } }
    const a = await page.$(`a[href="${h.replace(/"/g, '\\"')}"]`);
    if (!(await visible(a))) {       // inside a closed section or inactive tab: open what hides it, by setting state only for reachability
      await a.evaluate(e => { for (let p = e.parentElement; p; p = p.parentElement) { if (p.tagName === 'DETAILS') p.open = true; if (p.classList.contains('panel')) p.classList.add('on', 'active'), p.style.display = 'block'; if (p.hidden) p.hidden = false; } }); }
    if (h.startsWith('#')) { if (await visible(a)) { await mclick(page, a); const now = await page.evaluate(() => location.hash); if (decodeURIComponent(now) !== decodeURIComponent(h)) lk.bad.push(h + ' (click did not navigate)'); } else note.hidden = (note.hidden || 0) + 1; continue; }
    const target = path.resolve(path.dirname(path.join(ROOT, rel)), decodeURIComponent(h.split('#')[0]));
    if (!fs.existsSync(target)) { lk.bad.push(h + ' (file missing)'); continue; }
    if (await visible(a)) { await mclick(page, a); await page.waitForTimeout(150); const u = decodeURIComponent(page.url().split('#')[0]);
      if (u !== 'file://' + target) lk.bad.push(`${h} (went to ${u})`); if (page.url().split('#')[0] !== url) { await page.goBack(); await page.waitForTimeout(150); } }
    else lk.bad.push(h + ' (link not clickable)');
  }
  r['links work'] = lk.bad.length ? 'FAIL' : 'PASS'; note['links work'] = `${lk.n} distinct links` + (note.hidden ? `, ${note.hidden} in-page anchors target-checked only (hidden in the current view)` : '') + (lk.bad.length ? '; failed: ' + lk.bad.slice(0, 5).join(' | ') : '');
  r['no console errors'] = errs.length ? 'FAIL' : 'PASS'; note['no console errors'] = errs.length ? errs.slice(0, 3).join(' | ') : '0 errors after all interactions';
  r['no missing assets'] = failed.length || ext.length ? 'FAIL' : 'PASS'; note['no missing assets'] = failed.length || ext.length ? `failed: ${failed.join(', ')}; external: ${ext.join(', ')}` : 'self-contained: 0 failed requests, 0 external requests';
  // screenshot of the page as first opened
  await page.goto(url); await page.waitForTimeout(300);
  const shot = path.join(OUT, rel.replace(/^store\/reports\//, '').replace(/^modernization\/legacy\//, '').replace(/[\/]/g, '__').replace(/\.html$/, '.png'));
  await page.screenshot({ path: shot }); await page.close();
  return { page: rel, screenshot: shot, result: r, note, counts: cnt };
}

(async () => {
  fs.mkdirSync(OUT, { recursive: true });
  const browser = await chromium.connectOverCDP(process.env.CDP_URL || 'http://localhost:29229');
  const only = process.argv[2], res = [];
  for (const p of pages) { if (only && !p.includes(only)) continue; const x = await checkPage(browser, p); res.push(x); console.log(p, CHECKS.map(c => x.result[c] === 'PASS' ? 'P' : 'F').join(''), CHECKS.filter(c => x.result[c] !== 'PASS').map(c => c + ': ' + x.note[c]).join(' || ')); }
  await browser.close();
  if (only) {          // partial rerun: replace those pages in the recorded results
    const prev = J('modernization/legacy/page-check.json').pages; for (const x of res) { const i = prev.findIndex(p => p.page === x.page); if (i >= 0) prev[i] = x; else prev.push(x); }
    res.length = 0; res.push(...pages.map(p => prev.find(x => x.page === p)).filter(Boolean)); }
  fs.writeFileSync('modernization/legacy/page-check.json', JSON.stringify({ baseline_sha: SHA, checked_at: new Date().toISOString(), pages: res }, null, 1));
  const md = ['# Page check', '', `Baseline \`${SHA}\`. ${res.length} generated HTML pages were opened in Chrome and driven with mouse clicks and key presses by \`modernization/legacy/scripts/page_check.js\` (Chrome DevTools protocol; the HTML files were not parsed). Raw results: [page-check.json](page-check.json).`, '',
    `Checks per page: ${CHECKS.join('; ')}.`, '', '## Summary', '', '| Page | ' + CHECKS.join(' | ') + ' | Screenshot |', '|---|' + CHECKS.map(() => '---|').join('') + '---|',
    ...res.map(x => `| \`${x.page}\` | ${CHECKS.map(c => x.result[c]).join(' | ')} | [png](${path.relative('modernization/legacy', x.screenshot)}) |`), '',
    `Totals: ${CHECKS.map(c => `${c}: ${res.filter(x => x.result[c] === 'PASS').length} PASS, ${res.filter(x => x.result[c] !== 'PASS').length} FAIL`).join('; ')}.`, '', '## Detail', ''];
  for (const x of res) { md.push(`### \`${x.page}\``, '', '| Check | Result | What was done / seen |', '|---|---|---|', ...CHECKS.map(c => `| ${c} | **${x.result[c]}** | ${String(x.note[c]).replace(/\|/g, '\\|')} |`), ''); }
  fs.writeFileSync('modernization/legacy/page-check.md', md.join('\n'));
})();
