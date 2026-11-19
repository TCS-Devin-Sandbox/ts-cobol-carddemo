#!/usr/bin/env python3
"""Generate rules-INTCALC.html (review board) and decisions.md from rules.yaml.

    build_board.py           write both files
    build_board.py --check   fail when either file is out of date with rules.yaml
"""
from __future__ import annotations

import argparse
import hashlib
import html
import json
import sys
from pathlib import Path

import yaml

HERE = Path(__file__).resolve().parents[1]
ROOT = HERE.parents[2]
sys.path.insert(0, str(Path(__file__).resolve().parent))
import evaluate  # noqa: E402

BOARD = HERE / "rules-INTCALC.html"
REGISTER = HERE / "decisions.md"


def excerpt(cite: str) -> dict:
    path, _, span = cite.partition(":")
    lo, _, hi = span.partition("-")
    lo, hi = int(lo), int(hi or lo)
    lines = (ROOT / path).read_text(encoding="latin-1").splitlines()
    return {"cite": cite, "start": lo,
            "lines": [lines[i - 1].rstrip().expandtabs(8) for i in range(lo, min(hi, len(lines)) + 1)]}


def effects(rules: dict, cases: dict) -> dict[tuple[str, str], list[str]]:
    """(rule id, option) -> list of fixture values that differ from legacy."""
    out: dict[tuple[str, str], list[str]] = {}
    for name, doc in cases.items():
        for node, where in _cells(doc, name):
            for c in node.get("candidates", [])[1:]:
                rid, opt = c["decision"].split("=")
                legacy = node["candidates"][0]["value"]
                if c.get("status") == "UNRESOLVED":
                    out.setdefault((rid, opt), []).append(f"{where}: UNRESOLVED")
                elif c["value"] != legacy:
                    out.setdefault((rid, opt), []).append(f"{where}: {legacy} -> {c['value']}")
    return out


def _cells(doc: dict, case: str):
    for t in doc.get("transactions", []):
        for f, v in t.items():
            if isinstance(v, dict) and "rules" in v:
                yield v, f"{case} txn {t['balance_key']} {f}"
    for k, a in doc.get("accounts", {}).items():
        for f, v in a.items():
            if isinstance(v, dict) and "candidates" in v:
                yield v, f"{case} acct {k} {f}"
    if "fee_transactions" in doc:
        yield doc["fee_transactions"], f"{case} fee transactions"


def build_register(rules: dict, eff: dict) -> str:
    out = ["# Decision register - interest stream (INTCALC / CBACT04C)", "",
           "> **NOT SME SIGN-OFF.** Generated from `rules.yaml` by an agent. Nothing here has been approved by a business owner; "
           "every row is OPEN until the named owner decides. Regenerate with `make rules-interest`; `make rules-check` fails when this file is stale.", "",
           "Effects are computed by `tools/evaluate.py` on the seven `fixtures/interest` cases, changing one decision at a time from the legacy option A. "
           "\"none\" means no fixture value changes.", "",
           "| ID | Type | Question for the business | Options | Effect of each option on the fixtures | Owner | Status |",
           "|---|---|---|---|---|---|---|"]
    for r in rules["rules"]:
        if r["type"] == "RULE":
            continue
        d = r["decision"]
        opts = "<br>".join(f"**{k}** {v}" for k, v in d["options"].items())
        effs = []
        for k in d["options"]:
            if k == "A":
                effs.append("**A** legacy: matches recorded behaviour")
            else:
                lst = eff.get((r["id"], k), [])
                effs.append(f"**{k}** " + ("none" if not lst else f"{len(lst)} value(s): " + "; ".join(lst)))
        out.append(f"| {r['id']} | {r['type']} | {d['question']} | {opts} | {'<br>'.join(effs)} | {d['owner']} | OPEN |")
    out += ["", "## UNRESOLVED", "",
            "Values the rules do not determine (an option whose outcome is not defined in the estate). Owner placeholders must be replaced by a named person.", "",
            "| Case | Value | Rule | Decision | Owner | Status |", "|---|---|---|---|---|---|"]
    for name, doc in evaluate_cases().items():
        for u in doc.get("unresolved", []):
            out.append(f"| {name} | {u['value']} | {u['rule']} | {u.get('decision', '-')} | {u['owner']} | OPEN |")
    return "\n".join(out) + "\n"


_CASES: dict | None = None


def evaluate_cases() -> dict:
    global _CASES
    if _CASES is None:
        _CASES = {k[:-5]: json.loads(v) for k, v in evaluate.render_all().items()}
    return _CASES


def build_board(rules: dict, digest: str) -> str:
    cards = []
    for r in rules["rules"]:
        cards.append({"id": r["id"], "type": r["type"], "title": r["title"], "statement": r["statement"],
                      "call_path": r["call_path"], "cases": r.get("cases", []),
                      "decision": r.get("decision"), "excerpts": [excerpt(c) for c in r["source"]]})
    data = json.dumps({"digest": digest, "cards": cards}, indent=None).replace("</", "<\\/")
    counts = {t: sum(1 for r in rules["rules"] if r["type"] == t) for t in ("RULE", "QUIRK", "HOLE")}
    return TEMPLATE.replace("@@NOTICE@@", html.escape(rules["notice"])) \
        .replace("@@SUMMARY@@", html.escape(rules["summary"])) \
        .replace("@@COUNTS@@", " / ".join(f"{v} {k}" for k, v in counts.items())) \
        .replace("@@DIGEST@@", digest).replace("@@DATA@@", data)


def render() -> dict[Path, str]:
    text = (HERE / "rules.yaml").read_text()
    rules = yaml.safe_load(text)
    digest = hashlib.sha256(text.encode()).hexdigest()[:16]
    eff = effects(rules, evaluate_cases())
    return {BOARD: build_board(rules, digest), REGISTER: build_register(rules, eff)}


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--check", action="store_true")
    args = ap.parse_args()
    files = render()
    if args.check:
        stale = [p.name for p, t in files.items() if not p.exists() or p.read_text() != t]
        if stale:
            print(f"out of date with rules.yaml: {', '.join(stale)}; run make rules-interest")
            return 1
        print("rules-INTCALC.html and decisions.md are up to date with rules.yaml")
        return 0
    for p, t in files.items():
        p.write_text(t)
        print(f"wrote {p.relative_to(ROOT)}")
    return 0


TEMPLATE = r"""<!DOCTYPE html>
<html lang="en"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>INTCALC interest rules - review board</title>
<link rel="icon" href="data:,">
<style>
:root{--rule:#1f6f43;--quirk:#9a5b00;--hole:#a32020;--bg:#f6f7f9;--line:#d6d9de}
*{box-sizing:border-box}body{margin:0;font:14px/1.45 system-ui,Segoe UI,Arial,sans-serif;background:var(--bg);color:#1b1e23}
#notice{position:sticky;top:0;z-index:10;background:#fff3cd;border-bottom:2px solid #c9a227;padding:8px 16px;font-weight:600}
header{background:#fff;border-bottom:1px solid var(--line);padding:12px 16px}
h1{font-size:20px;margin:0 0 6px}#summary{max-width:1100px;margin:0}
#toolbar{display:flex;flex-wrap:wrap;gap:10px;align-items:center;padding:10px 16px;background:#fff;border-bottom:1px solid var(--line)}
#toolbar input{padding:5px 8px;border:1px solid #999;border-radius:4px;min-width:220px}
button{cursor:pointer;padding:5px 10px;border:1px solid #888;border-radius:4px;background:#fff}
button:focus-visible,input:focus-visible,textarea:focus-visible,summary:focus-visible{outline:3px solid #2563eb;outline-offset:1px}
#totals{display:flex;gap:14px;flex-wrap:wrap;padding:8px 16px;font-size:13px}
#totals span b{font-variant-numeric:tabular-nums}
main{padding:8px 16px 40px}h2{font-size:16px;margin:18px 0 8px}
.grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(440px,1fr));gap:12px}
.card{background:#fff;border:1px solid var(--line);border-left:6px solid;border-radius:6px;padding:10px 12px}
.card[data-type=RULE]{border-left-color:var(--rule)}.card[data-type=QUIRK]{border-left-color:var(--quirk)}.card[data-type=HOLE]{border-left-color:var(--hole)}
.card h3{margin:0 0 4px;font-size:15px}.badge{font-size:11px;padding:1px 6px;border-radius:9px;color:#fff;margin-right:6px}
.RULE .badge,.badge.RULE{background:var(--rule)}.badge.QUIRK{background:var(--quirk)}.badge.HOLE{background:var(--hole)}
.meta{font-size:12px;color:#555}.meta code{font-size:11px}
details{margin:6px 0}summary{cursor:pointer;font-size:12px;color:#174ea6}
pre{background:#0f172a;color:#e2e8f0;padding:6px 8px;border-radius:4px;overflow:auto;font-size:11.5px;margin:4px 0}
.ln{color:#94a3b8;user-select:none}
.controls{display:flex;gap:6px;align-items:flex-start;flex-wrap:wrap;margin-top:6px}
.controls textarea{flex:1 1 220px;min-height:34px;font:inherit;font-size:12px}
.state{font-size:12px;margin-top:4px}.state.approved{color:var(--rule)}.state.rejected{color:var(--hole)}
.err{color:#b00020;font-size:12px;font-weight:600}
.card.flag{outline:2px solid #b00020}
</style></head><body>
<div id="notice" role="note" data-testid="notice">@@NOTICE@@ Decisions you make here stay in this browser only.</div>
<header><h1>INTCALC monthly interest - business rules review board</h1>
<p id="summary" data-testid="summary">@@SUMMARY@@</p>
<p class="meta">Rules: @@COUNTS@@ &middot; generated from <code>modernization/rules/interest/rules.yaml</code> (sha256 @@DIGEST@@) &middot; register: <code>decisions.md</code></p></header>
<div id="toolbar">
<label for="reviewer">Reviewer name</label><input id="reviewer" data-testid="reviewer" autocomplete="off" placeholder="your name (required to decide)">
<button id="export" data-testid="export">Export CSV</button>
<button id="clear" data-testid="clear">Clear decisions</button>
<button id="clear-confirm" data-testid="clear-confirm" hidden>Confirm: clear all decisions</button>
<span id="toolbar-msg" class="err" role="alert"></span>
</div>
<div id="totals" data-testid="totals" aria-live="polite"></div>
<main id="main"></main>
<script id="board-data" type="application/json">@@DATA@@</script>
<script>
(function(){
const DATA=JSON.parse(document.getElementById('board-data').textContent);
const KEY='intcalc-board:'+DATA.digest, RKEY='intcalc-board:reviewer';
let state=load();
function load(){try{return JSON.parse(localStorage.getItem(KEY))||{}}catch(e){return {}}}
function save(){localStorage.setItem(KEY,JSON.stringify(state))}
const rev=document.getElementById('reviewer');rev.value=localStorage.getItem(RKEY)||'';
rev.addEventListener('input',()=>{localStorage.setItem(RKEY,rev.value.trim());document.getElementById('toolbar-msg').textContent=''});
const esc=s=>String(s).replace(/[&<>"]/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;'}[c]));
const main=document.getElementById('main');
for(const t of ['RULE','QUIRK','HOLE']){
  const cs=DATA.cards.filter(c=>c.type===t);
  const sec=document.createElement('section');sec.dataset.section=t;
  sec.innerHTML=`<h2>${t} <span class="meta">(${cs.length}) ${t==='RULE'?'policy implemented on purpose':t==='QUIRK'?'behaviour of the code, not confirmed as policy':'case the code does not handle or the estate does not show'}</span></h2><div class="grid"></div>`;
  const g=sec.querySelector('.grid');
  for(const c of cs){
    const el=document.createElement('article');el.className='card';el.dataset.type=c.type;el.dataset.id=c.id;el.id='card-'+c.id;el.setAttribute('data-testid','card');
    const ex=c.excerpts.map(e=>`<details data-testid="excerpt" data-cite="${esc(e.cite)}"><summary>Source ${esc(e.cite)}</summary><pre>${e.lines.map((l,i)=>`<span class="ln">${String(e.start+i).padStart(4)} </span>${esc(l)}`).join('\n')}</pre></details>`).join('');
    const dec=c.decision?`<div class="meta"><b>Question:</b> ${esc(c.decision.question)}<br>${Object.entries(c.decision.options).map(([k,v])=>`<b>${k}</b> ${esc(v)}`).join('<br>')}<br><b>Owner:</b> ${esc(c.decision.owner)}</div>`:'';
    el.innerHTML=`<h3><span class="badge ${c.type}">${c.type}</span>${esc(c.id)} - ${esc(c.title)}</h3>
<p data-testid="statement">${esc(c.statement)}</p>
<div class="meta"><b>Call path:</b> <code>${esc(c.call_path)}</code></div>
<div class="meta"><b>Fixture cases:</b> ${c.cases.length?c.cases.map(esc).join(', '):'none'}</div>${dec}${ex}
<div class="controls"><button data-act="approve" aria-label="Approve ${esc(c.id)}">Approve</button><button data-act="reject" aria-label="Reject ${esc(c.id)}">Reject</button>
<textarea data-testid="comment" aria-label="Comment on ${esc(c.id)}" placeholder="comment"></textarea><button data-act="comment" aria-label="Save comment on ${esc(c.id)}">Save comment</button></div>
<div class="state" data-testid="state"></div><div class="err" role="alert" data-testid="card-error"></div>`;
    el.querySelectorAll('button[data-act]').forEach(b=>b.addEventListener('click',()=>act(c.id,b.dataset.act,el)));
    g.appendChild(el);
  }
  main.appendChild(sec);
}
function act(id,what,el){
  const who=rev.value.trim(), err=el.querySelector('[data-testid=card-error]');
  if(!who){err.textContent='Enter a reviewer name first - decision not recorded.';el.classList.add('flag');rev.focus();return}
  err.textContent='';el.classList.remove('flag');
  const s=state[id]||{decision:'',comment:'',reviewer:'',at:''};
  if(what==='comment'){s.comment=el.querySelector('textarea').value.trim()}else{s.decision=what==='approve'?'APPROVED':'REJECTED'}
  s.reviewer=who;s.at=new Date().toISOString();state[id]=s;save();paint();
}
function paint(){
  const tot={RULE:{},QUIRK:{},HOLE:{}};let all={APPROVED:0,REJECTED:0,UNDECIDED:0,COMMENTED:0};
  for(const c of DATA.cards){
    const el=document.getElementById('card-'+c.id),s=state[c.id]||{};
    const d=s.decision||'UNDECIDED';
    el.dataset.decision=d;
    el.querySelector('textarea').value=s.comment||'';
    const st=el.querySelector('[data-testid=state]');
    st.className='state '+(d==='APPROVED'?'approved':d==='REJECTED'?'rejected':'');
    st.textContent=d+(s.reviewer?` by ${s.reviewer}`:'')+(s.comment?` - comment: "${s.comment}"`:'');
    const t=tot[c.type];t[d]=(t[d]||0)+1;all[d]++;if(s.comment){all.COMMENTED++;t.COMMENTED=(t.COMMENTED||0)+1}
  }
  document.getElementById('totals').innerHTML=`<span data-testid="total-all">All ${DATA.cards.length}: <b data-k="APPROVED">${all.APPROVED}</b> approved, <b data-k="REJECTED">${all.REJECTED}</b> rejected, <b data-k="COMMENTED">${all.COMMENTED}</b> commented, <b data-k="UNDECIDED">${all.UNDECIDED}</b> undecided</span>`+
   Object.entries(tot).map(([t,v])=>`<span data-testid="total-${t}">${t} (${DATA.cards.filter(c=>c.type===t).length}): ${v.APPROVED||0} approved, ${v.REJECTED||0} rejected, ${v.COMMENTED||0} commented, ${v.UNDECIDED||0} undecided</span>`).join('');
}
function csvCell(v){v=String(v??'');return /[",\n]/.test(v)?'"'+v.replace(/"/g,'""')+'"':v}
document.getElementById('export').addEventListener('click',()=>{
  const rows=[['id','type','title','decision','comment','reviewer','decided_at']];
  for(const c of DATA.cards){const s=state[c.id]||{};rows.push([c.id,c.type,c.title,s.decision||'UNDECIDED',s.comment||'',s.reviewer||'',s.at||''])}
  const blob=new Blob([rows.map(r=>r.map(csvCell).join(',')).join('\n')+'\n'],{type:'text/csv'});
  const a=document.createElement('a');a.href=URL.createObjectURL(blob);a.download='rules-INTCALC-decisions.csv';document.body.appendChild(a);a.click();a.remove();
});
const clr=document.getElementById('clear'),cfm=document.getElementById('clear-confirm');
clr.addEventListener('click',()=>{cfm.hidden=false;cfm.focus()});
cfm.addEventListener('click',()=>{state={};localStorage.removeItem(KEY);cfm.hidden=true;paint()});
paint();
})();
</script></body></html>
"""

if __name__ == "__main__":
    sys.exit(main())
