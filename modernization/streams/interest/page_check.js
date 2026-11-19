const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const {chromium} = require(process.env.PLAYWRIGHT_CORE || 'playwright-core');
(async () => {
  const browser = await chromium.launch({executablePath: process.env.CHROME || '/home/ubuntu/.local/bin/google-chrome', headless:true, args:['--no-sandbox']});
  const page = await browser.newPage({viewport:{width:1440,height:1100}});
  const errors=[], requests=[];
  page.on('pageerror',e=>errors.push(String(e)));
  page.on('console',m=>{if(m.type()==='error') errors.push(m.text());});
  page.on('request',r=>{if(!r.url().startsWith('file:'))requests.push(r.url());});
  const dir=__dirname;
  await page.goto('file://'+path.join(dir,'stream-INTCALC.html'));
  const m=JSON.parse(fs.readFileSync(path.join(dir,'manifest.json'),'utf8'));
  assert.deepEqual(await page.$eval('#manifest',e=>JSON.parse(e.textContent)),m);
  for(const c of await page.$$eval('[data-count-key]',es=>es.map(e=>[e.dataset.countKey,Number(e.dataset.count)]))) assert.equal(c[1],m.counts[c[0]]);
  const tabs=await page.$$('[role=tab]');
  const initialScroll=await page.evaluate(()=>window.scrollY);
  async function assertTabBarVisible(){assert.ok(await page.$eval('[role=tablist]',e=>{const r=e.getBoundingClientRect();return r.top>=0&&r.bottom<=innerHeight;}));}
  for(const t of tabs){await t.click();assert.equal(await t.getAttribute('aria-selected'),'true');assert.equal(await page.evaluate(()=>location.hash),'#'+await t.getAttribute('aria-controls'));assert.equal(await page.evaluate(()=>window.scrollY),initialScroll);await assertTabBarVisible();}
  await tabs[0].focus();await page.keyboard.press('ArrowRight');assert.equal(await tabs[1].getAttribute('aria-selected'),'true');
  await page.keyboard.press('Enter');
  assert.equal(await page.evaluate(()=>location.hash),'#in_scope');
  assert.equal(await page.evaluate(()=>window.scrollY),initialScroll);await assertTabBarVisible();
  for(const key of ['edges','outside_dependencies']){
    await page.click('#tab-'+key);
    for(const status of ['PROVEN','CANDIDATE','ALL']){
      await page.selectOption('#'+key+'-status',status);
      assert.equal(await page.locator('#'+key+'-table tbody tr').count(),m[key].filter(r=>status==='ALL'||r.status===status).length);
    }
    await page.fill('#'+key+'-search','NO_MATCH_923849');assert.equal(await page.locator('#'+key+'-table tbody tr').count(),0);
    await page.fill('#'+key+'-search','CBACT04C');assert.equal(await page.locator('#'+key+'-table tbody tr').count(),m[key].filter(r=>JSON.stringify(r).toLowerCase().includes('cbact04c')).length);
    await page.fill('#'+key+'-search','');
  }
  await page.click('#tab-overview');await page.click('summary');assert.equal(await page.$eval('details',e=>e.open),true);
  await page.click('a[href="#open_asks"]');assert.equal(await page.$eval('#open_asks',e=>e.hidden),false);
  await page.reload();
  assert.equal(await page.$eval('#tab-open_asks',e=>e.getAttribute('aria-selected')),'true');
  assert.equal(await page.$eval('#open_asks',e=>e.hidden),false);
  await page.click('#tab-edges');
  await page.goBack();
  await page.waitForFunction(()=>document.querySelector('#tab-open_asks').getAttribute('aria-selected')==='true');
  assert.equal(await page.evaluate(()=>location.hash),'#open_asks');
  await page.goForward();
  await page.waitForFunction(()=>document.querySelector('#tab-edges').getAttribute('aria-selected')==='true');
  await page.evaluate(()=>{location.hash='outside_dependencies-search';});
  await page.waitForFunction(()=>!document.querySelector('#outside_dependencies').hidden);
  await page.reload();
  assert.equal(await page.$eval('#tab-outside_dependencies',e=>e.getAttribute('aria-selected')),'true');
  assert.ok(Math.abs(await page.$eval('#outside_dependencies-search',e=>e.getBoundingClientRect().top))<2);
  await page.evaluate(()=>{location.hash='';});
  await page.waitForFunction(()=>!document.querySelector('#overview').hidden);
  await page.evaluate(()=>{location.hash='missing-panel';});
  await page.waitForFunction(()=>location.hash==='#missing-panel'&&!document.querySelector('#overview').hidden);
  await page.evaluate(()=>{location.hash='%E0%A4%A';});
  await page.waitForFunction(()=>location.hash==='#%E0%A4%A'&&!document.querySelector('#overview').hidden);
  await page.click('#tab-overview');assert.equal(await page.locator('svg').count(),8);
  assert.deepEqual(errors,[]);assert.deepEqual(requests,[]);
  await page.screenshot({path:path.join(dir,'verified-overview.png'),fullPage:true});
  await browser.close();
  console.log('PASS: embedded manifest, counts, 8 tabs, tab clicks/keyboard preserve scrollY and visible tab bar, keyboard/hash sync, reload #open_asks, back/forward navigation, nested-element hash/reload/scroll, cleared/unknown/malformed hashes, status/text/empty filters, details, anchors, 8 SVG diagrams; 0 console errors; 0 external requests.');
})().catch(e=>{console.error(e);process.exit(1);});
