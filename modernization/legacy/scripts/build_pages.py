#!/usr/bin/env python3
"""Renders modernization/legacy/graph.json (see build_graph.py) into
call-graph.md / call-graph.html and architecture-as-is.md / architecture-as-is.html.
The HTML pages are self-contained: no external script, style, font or image.
Run from the repo root after build_graph.py.
"""
import collections, html, json, os

D = "modernization/legacy"
g = json.load(open(os.path.join(D, "graph.json")))
M, SUM, E = g["meta"], g["summary"], g["edges"]
P, C = "PROVEN", "CANDIDATE"
PL = "PROVEN (static, cited file and line)"
CL = "CANDIDATE (dynamic or inferred)"
SHA = M["baseline_sha"]
prog = {p["id"]: p for p in g["programs"]}
leaf = {p["id"]: p for p in g["leaves"]}
MODS = M["modules"]
esc = html.escape


def rows(cat, cert=None):
    return [e for e in E if e["cat"] == cat and (cert is None or e["certainty"] == cert)]


def md_table(head, body):
    out = ["| " + " | ".join(head) + " |", "|" + "---|" * len(head)]
    out += ["| " + " | ".join(str(c if c not in (None, "") else "-").replace("|", "\\|").replace("\n", " ") for c in r) + " |" for r in body]
    return "\n".join(out)


def c(x):
    return "`%s`" % x if x else "-"


# ------------------------------------------------------------------ table specs (shared by md and html)
def spec_program(cert):
    return (["From", "Module", "Edge", "To", "Target is", "To module", "Cross-module", "Cited at"] + (["Basis"] if cert == C else []),
            [[e["from"], e["module"], e["kind"], e["to"], e["to_class"], e.get("to_module") or "-", "yes" if e.get("cross") else "no", e["cite"]] + ([e.get("basis", "")] if cert == C else [])
             for e in sorted(rows("program", cert), key=lambda e: (e["from"], e["to"], e["cite"]))])


def spec_job(cert):
    return (["Job", "Module", "Step", "EXEC", "Program run", "Kind", "PROC", "Cited at", "Note"],
            [[e["from"], e["module"], e.get("step") or "-", e.get("pgm"), e["to"], e.get("to_class"), e.get("proc") or "-", e["cite"], e.get("via", "")]
             for e in sorted(rows("job", cert), key=lambda e: (e["from"], e["cite"]))])


def spec_data(cert):
    return (["Program", "Module", "Direction", "Store", "Store kind", "Via", "DD / file / verb", "Job.step", "Cited at", "Binding cited at"] + (["Basis"] if cert == C else []),
            [[e["from"], e["module"], e["direction"], e["to"], e["store_type"], e["kind"], e.get("dd") or "-",
              ("%s.%s" % (e["job"], e["step"])) if e.get("job") else "-", e["cite"], e.get("cite2") or "-"] + ([e.get("basis", "")] if cert == C else [])
             for e in sorted(rows("data", cert), key=lambda e: (e["from"], e["to"], e["direction"]))])


def spec_tx(cert):
    return (["Transaction", "Initial program", "Module", "Program module", "Role (scanner)", "Cited at"],
            [[e["from"], e["to"], e["module"], e.get("to_module") or "-", e.get("role") or "-", e["cite"]]
             for e in sorted(rows("transaction", cert), key=lambda e: e["from"])])


def spec_sched(cert):
    return (["Table / predecessor", "Edge", "Job / successor", "Job module", "Scheduler table", "Dialect", "Cited at"] + (["Basis"] if cert == C else []),
            [[e["from"], e["kind"], e["to"], e.get("to_module") or "-", e.get("table"), e.get("dialect"), e["cite"]] + ([e.get("basis", "")] if cert == C else [])
             for e in rows("schedule", cert)])


def spec_copy():
    return (["Copybook", "Source", "Owner", "Programs", "Modules", "Used by (program at file:line)"],
            [[cb["name"], cb["path"] or "not in estate (system or generated)", cb["owner"], len(cb["programs"]), ", ".join(cb["modules"]),
              "; ".join("%s at %s" % (u["program"], u["cite"]) for u in cb["programs"])]
             for cb in g["copybooks"] if len(cb["programs"]) > 1])


def spec_asks():
    return (["#", "Kind", "Module", "Program", "Construct", "Data item", "Cited at", "Open question", "Owner"],
            [["Q%02d" % (i + 1), a["kind"], a["module"], a["program"], a["construct"], a.get("variable") or "-", a["cite"], a["question"], "<owner: TBD>"]
             for i, a in enumerate(g["asks"])])


def spec_uncalled():
    return (["Program", "Language", "Module", "Source", "Candidate inbound edges only"],
            [[u["program"], u["lang"], u["module"], u["path"], "; ".join(u["candidate_inbound"]) or "none"] for u in g["uncalled"]])


def spec_unref():
    return (["Member", "Kind", "Module", "Source", "Catalogued at", "Name found elsewhere in app/ (plain text search, first hit)"],
            [[u["member"], u["kind"], u["module"], u["path"], u["cite"], u.get("grep") or "not found"] for u in g["unreferenced"]])


def spec_unsched():
    jm = {j["name"]: j for j in g["jobs"]}
    return (["Job", "Module", "Source"], [[j, jm[j]["module"], jm[j]["path"]] for j in g["unscheduled"]])


def spec_dis():
    return (["Topic", "Subject", "docs/chain-graph.md says", "Scan says", "Doc line"],
            [[d["topic"], d["subject"], d["doc"], d["scan"], d["cite"]] for d in g["disagreements"]])


SECTIONS = [  # id, title, has certainty split, spec function, blurb
    ("program", "Program to program", True, spec_program, "COBOL CALL, CICS LINK and CICS XCTL. HLASM and PL/I members are leaf nodes (target is leaf-HLASM / leaf-PL/I). Targets marked runtime or not-in-estate are system routines (LE, MQ, DL/I) the repository does not supply."),
    ("job", "Job to step to program", True, spec_job, "Every EXEC of every scanned JCL job, with launcher steps (IKJEFT01, DFSRRC00) unwrapped to the program they run where a card names it."),
    ("data", "Program to dataset, table, queue or map", True, spec_data, "Direction is reads, writes or updates (REWRITE, DELETE, SQL UPDATE/DELETE, DL/I REPL/DLET). For maps, writes = SEND MAP and reads = RECEIVE MAP."),
    ("copybook", "Programs sharing copybook layouts", False, spec_copy, "Copybooks copied by two or more programs. Every COPY statement is a static, cited fact, so all rows are " + PL + ". Known gap: the scanner does not read COPY with a quoted member name (COPY 'CSSTRPFY', COPY 'CSUTLDWY'); those copies are not in this table, see Unreferenced members and INTAKE.md."),
    ("transaction", "Transaction id to initial program", True, spec_tx, "From the DEFINE TRANSACTION entries of the CSD files under app/csd and app/app-*/csd."),
    ("schedule", "Scheduler order", True, spec_sched, "From app/scheduler (CA 7 and Control-M exports): which table orders which job, and which job runs before which."),
]


def n_rows(sid, cert=None):
    return len(dict(program=spec_program, job=spec_job, data=spec_data, transaction=spec_tx, schedule=spec_sched)[sid](cert)[1]) if sid != "copybook" else len(spec_copy()[1])


# ------------------------------------------------------------------ call-graph.md
def write_call_graph_md():
    o = ["# Call graph of the whole estate (as scanned)", "",
         "Baseline: `%s` (%s), repository `%s`. Tool: %s. Built by `modernization/legacy/scripts/build_graph.py` from the module scans in `store/<MODULE>/` and the estate catalogue; data in `modernization/legacy/graph.json`. Interactive page: [call-graph.html](call-graph.html)." % (SHA, M["baseline_date"], M["repo"], M["tool"]),
         "", "Modules analyzed: %s. Modules not analyzed: %s." % (", ".join(M["analyzed"]), ", ".join(M["not_analyzed"]) or "none"), "",
         "Every edge below is in exactly one of two lists, never both:", "",
         "- **%s**: the source states the edge literally; the file and line are given." % PL,
         "- **%s**: the target is held in a data item or is inferred by joining two sources; the basis is given. A dynamic site with no settled target is not an edge at all: it is in [Open asks](#open-asks)." % CL, "",
         "## Counts", "",
         md_table(["Edge category", "PROVEN", "CANDIDATE"], [[t, n_rows(s, P) if split else n_rows(s), n_rows(s, C) if split else 0] for s, t, split, _, _ in SECTIONS]), "",
         md_table(["Item", "Count"], [["Programs (COBOL)", SUM["programs"]], ["Leaf members (HLASM, PL/I)", SUM["leaves"]], ["JCL jobs", SUM["jobs"]],
                                      ["Edges that cross a module boundary", SUM["cross_module_edges"]], ["Open asks (dynamic targets, never guessed)", SUM["open_asks"]],
                                      ["Uncalled programs", SUM["uncalled"]], ["Unreferenced members", SUM["unreferenced"]],
                                      ["Unscheduled JCL jobs", SUM["unscheduled_jobs"]], ["Disagreements with docs/chain-graph.md", SUM["disagreements"]]]), ""]
    # mermaid: proven program-to-program between estate members
    o += ["## Diagram: proven program-to-program edges", "", "Only %s edges between estate members. Candidate edges are drawn separately below." % PL, "", "```mermaid", "flowchart LR"]
    def mer(cert):
        out, seen = [], set()
        for m in MODS:
            names = sorted({x for e in rows("program", cert) if e["to_class"] in ("program", "leaf-HLASM", "leaf-PL/I") for x in (e["from"], e["to"]) if (prog.get(x) or leaf.get(x))["module"] == m})
            if names:
                out.append("  subgraph %s" % m)
                out += ["    %s%s" % (n, '(["%s<br/>%s"])' % (n, leaf[n]["lang"]) if n in leaf else "") for n in names]
                out.append("  end")
        for e in rows("program", cert):
            if e["to_class"] in ("program", "leaf-HLASM", "leaf-PL/I") and (e["from"], e["to"], e["kind"]) not in seen and e["from"] != e["to"]:
                seen.add((e["from"], e["to"], e["kind"]))
                out.append("  %s %s|%s| %s" % (e["from"], "-->" if cert == P else "-.->", e["kind"], e["to"]))
        return out
    o += mer(P) + ["```", "", "## Diagram: candidate program-to-program edges", "", "Only %s edges. None of these is proven." % CL, "", "```mermaid", "flowchart LR"] + mer(C) + ["```", ""]
    for sid, title, split, fn, blurb in SECTIONS:
        o += ["## " + title, "", blurb, ""]
        if split:
            for cert, lab in ((P, PL), (C, CL)):
                h, b = fn(cert)
                o += ["### %s - %s (%d)" % (title, lab, len(b)), "", md_table(h, [[c(x) if i == h.index("Cited at") or h[i] == "Binding cited at" else x for i, x in enumerate(r)] for r in b]) if b else "None.", ""]
        else:
            h, b = fn()
            o += ["All %d rows are %s." % (len(b), PL), "", md_table(h, b), ""]
    h, b = spec_unsched()
    o += ["## Unscheduled JCL jobs", "", "Jobs that no table in `app/scheduler` orders (%d of %d jobs). Whether they are run by hand, on request or not at all is not in the repository." % (len(b), SUM["jobs"]), "", md_table(h, b), ""]
    h, b = spec_asks()
    o += ["## Open asks", "", "Dynamic sites whose target the sources do not settle. They are questions, not edges; nothing here was guessed.", "", md_table(h, b), ""]
    h, b = spec_uncalled()
    o += ["## Uncalled programs", "", "Programs with no PROVEN inbound edge from a job step, a transaction or another program.", "", md_table(h, b) if b else "None.", ""]
    h, b = spec_unref()
    o += ["## Unreferenced members", "", "Catalogued members (copybooks, PROCs, control cards, IMS and SQL members, macros) that no scan result names. The last column is a plain text search of app/ for the member name: a hit means the source mentions the name where the scanner does not read it (for example assembler macro calls), not that the member is unused.", "", md_table(h, b) if b else "None.", ""]
    h, b = spec_dis()
    o += ["## Disagreements with docs/chain-graph.md", "", "`docs/chain-graph.md` is generated by `make chain-graph` (`tools/chaingraph/gen_chain_graph.py`). Its Programs and Jobs tables were compared row by row with the scan. %d differences; where the two agree nothing is listed." % len(b), "", md_table(h, b) if b else "None.", ""]
    open(os.path.join(D, "call-graph.md"), "w").write("\n".join(o))


# ------------------------------------------------------------------ shared HTML shell
CSS = """
:root{--bg:#f7f8fa;--fg:#1c2330;--mut:#5b6677;--line:#d5dae2;--card:#fff;--acc:#1f5fbf;--ok:#17703b;--cand:#a05a00}
*{box-sizing:border-box}body{margin:0;font:14px/1.45 system-ui,-apple-system,"Segoe UI",Roboto,Arial,sans-serif;background:var(--bg);color:var(--fg)}
header{background:#14213a;color:#fff;padding:16px 24px}header h1{margin:0 0 6px;font-size:20px}header p{margin:2px 0;color:#c9d4ea;font-size:13px}
header code{background:#24365c;color:#fff;padding:1px 5px;border-radius:3px}header a{color:#9fc4ff}
main{padding:16px 24px;max-width:1500px;margin:auto}
.cards{display:flex;flex-wrap:wrap;gap:10px;margin:12px 0}.card{background:var(--card);border:1px solid var(--line);border-radius:6px;padding:8px 12px;min-width:120px}
.card b{display:block;font-size:20px}.card span{color:var(--mut);font-size:12px}
.tabs{display:flex;flex-wrap:wrap;gap:4px;border-bottom:2px solid var(--line);margin-top:14px}
.tabs button{border:1px solid var(--line);border-bottom:none;background:#e9edf3;padding:7px 12px;border-radius:6px 6px 0 0;cursor:pointer;font:inherit;color:var(--fg)}
.tabs button[aria-selected=true]{background:var(--acc);color:#fff;border-color:var(--acc)}
.panel{display:none;background:var(--card);border:1px solid var(--line);border-top:none;padding:14px}.panel.on{display:block}
.bar{display:flex;flex-wrap:wrap;gap:10px;align-items:center;margin:8px 0}.bar input[type=search]{padding:5px 8px;min-width:260px;font:inherit;border:1px solid var(--line);border-radius:4px}
.bar select{padding:5px;font:inherit}.seg label{border:1px solid var(--line);padding:4px 10px;cursor:pointer;background:#eef1f5}.seg input{display:none}
.seg input:checked+span{font-weight:700}.seg label.sel{background:#dfe9fa;border-color:var(--acc)}
.count{color:var(--mut)}table{border-collapse:collapse;width:100%;font-size:12.5px}th,td{border:1px solid var(--line);padding:4px 6px;text-align:left;vertical-align:top}
th{background:#eef1f5;position:sticky;top:0}td code,.cite{font-family:ui-monospace,Menlo,Consolas,monospace;font-size:11.5px;color:#33415c;word-break:break-all}
.tw{max-height:560px;overflow:auto;border:1px solid var(--line)}.P{color:var(--ok);font-weight:700}.C{color:var(--cand);font-weight:700}
details{border:1px solid var(--line);border-radius:5px;margin:6px 0;background:#fbfcfd}summary{cursor:pointer;padding:6px 10px;font-weight:600}details>div{padding:6px 12px}
svg{background:#fff;border:1px solid var(--line);max-width:100%;height:auto}svg text{font:11px system-ui,Arial,sans-serif}
.legend span{display:inline-block;margin-right:14px}.sw{display:inline-block;width:12px;height:12px;border-radius:2px;margin-right:4px;vertical-align:-1px}
.note{color:var(--mut)}h2{font-size:16px;margin:14px 0 6px}h3{font-size:14px;margin:12px 0 4px}ul{margin:4px 0 8px 20px;padding:0}
"""
JS = r"""
const $=(s,r=document)=>r.querySelector(s),$$=(s,r=document)=>[...r.querySelectorAll(s)];
function el(t,a={},...k){const e=document.createElement(t);for(const[x,v]of Object.entries(a)){if(x==='text')e.textContent=v;else if(x==='html')e.innerHTML=v;else e.setAttribute(x,v)}k.forEach(c=>e.append(c));return e}
function showTab(id){$$('.tabs button').forEach(b=>b.setAttribute('aria-selected',b.dataset.tab===id));$$('.panel').forEach(p=>p.classList.toggle('on',p.id==='p-'+id));if(location.hash!=='#'+id)history.replaceState(null,'','#'+id)}
function initTabs(){$$('.tabs button').forEach(b=>b.addEventListener('click',()=>showTab(b.dataset.tab)));
 $$('.tabs').forEach(t=>t.addEventListener('keydown',e=>{if(e.key!=='ArrowRight'&&e.key!=='ArrowLeft')return;const bs=$$('button',t),i=bs.indexOf(document.activeElement);if(i<0)return;const n=bs[(i+(e.key==='ArrowRight'?1:bs.length-1))%bs.length];n.focus();showTab(n.dataset.tab)}));
 const h=location.hash.slice(1);showTab($$('.tabs button').some(b=>b.dataset.tab===h)?h:$$('.tabs button')[0].dataset.tab);
 window.addEventListener('hashchange',()=>{const h=location.hash.slice(1);if($$('.tabs button').some(b=>b.dataset.tab===h))showTab(h)})}
/* a filterable table: {id, head, sets:{label:[rows]}, modcol} */
function mkTable(host,t){const labels=Object.keys(t.sets);let cur=labels[0];const bar=el('div',{class:'bar'});
 let seg=null;if(labels.length>1){seg=el('span',{class:'seg',role:'radiogroup','aria-label':'certainty'});labels.forEach((l,i)=>{const inp=el('input',{type:'radio',name:'c-'+t.id,value:l});if(!i)inp.checked=true;const lab=el('label',{},inp,el('span',{text:l+' ('+t.sets[l].length+')'}));if(!i)lab.classList.add('sel');lab.dataset.set=l;seg.append(lab);inp.addEventListener('change',()=>{cur=l;$$('label',seg).forEach(x=>x.classList.toggle('sel',x===lab));draw()})});bar.append(seg)}
 const q=el('input',{type:'search',placeholder:'Filter rows (any column)','aria-label':'filter '+t.id});bar.append(q);
 let sel=null;if(t.modcol!=null){sel=el('select',{'aria-label':'module '+t.id},el('option',{value:'',text:'All modules'}));[...new Set(Object.values(t.sets).flat().map(r=>String(r[t.modcol])))].sort().forEach(m=>sel.append(el('option',{value:m,text:m})));bar.append(sel);sel.addEventListener('change',draw)}
 const cnt=el('span',{class:'count',id:'cnt-'+t.id});bar.append(cnt);host.append(bar);
 const tb=el('tbody'),thr=el('tr');const table=el('table',{id:'tbl-'+t.id},el('thead',{},thr),tb);host.append(el('div',{class:'tw'},table));
 function draw(){const all=t.sets[cur],heads=t.heads?t.heads[cur]:t.head;thr.replaceChildren(...heads.map(h=>el('th',{text:h})));const s=q.value.trim().toLowerCase(),m=sel?sel.value:'';
  const rows=all.filter(r=>(!m||String(r[t.modcol])===m)&&(!s||r.join(' \u0001 ').toLowerCase().includes(s)));
  tb.replaceChildren(...rows.map(r=>el('tr',{},...r.map((c,i)=>{const td=el('td');const h=heads[i]||'';if(/cited|line|source|catalogued/i.test(h)&&c&&c!=='-')td.append(el('code',{text:String(c)}));else td.textContent=(c===null||c==='')?'-':String(c);return td}))));
  cnt.textContent='showing '+rows.length+' of '+all.length+(labels.length>1?' '+cur+' rows':' rows');table.dataset.total=all.length;table.dataset.shown=rows.length}
 q.addEventListener('input',draw);draw()}
"""


def page(title, sub, tabs, body, data, extra_js=""):
    return ("<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
            "<title>%s</title><style>%s</style></head><body><header><h1>%s</h1>%s</header><main>%s<div class=\"tabs\" role=\"tablist\">%s</div>%s</main>"
            "<script id=\"data\" type=\"application/json\">%s</script><script>%s\nconst DATA=JSON.parse(document.getElementById('data').textContent);\n%s\ninitTabs();</script></body></html>") % (
        esc(title), CSS, esc(title), sub, body[0],
        "".join('<button role="tab" data-tab="%s" id="t-%s">%s</button>' % (i, i, esc(t)) for i, t in tabs), body[1],
        json.dumps(data).replace("</", "<\\/"), JS, extra_js)


def head_html(other, other_label, md):
    return ("<p>Baseline <code id=\"sha\">%s</code> (%s) &middot; repository %s &middot; %s &middot; modules analyzed: %s &middot; not analyzed: %s</p>"
            "<p>Companion pages: <a href=\"%s\">%s</a> &middot; <a href=\"%s\">%s (Markdown)</a> &middot; data: <a href=\"graph.json\">graph.json</a></p>") % (
        SHA, esc(M["baseline_date"]), esc(M["repo"]), esc(M["tool"]), ", ".join(M["analyzed"]), ", ".join(M["not_analyzed"]) or "none", other, other_label, md, md)


def cards(items):
    return '<div class="cards">' + "".join('<div class="card"><b id="card-%s">%s</b><span>%s</span></div>' % (i, v, esc(l)) for i, l, v in items) + "</div>"


MODCOL = {"program": 1, "job": 1, "data": 1, "transaction": 2, "schedule": 3}


# ------------------------------------------------------------------ call-graph.html
def write_call_graph_html():
    tables = {}
    for sid, title, split, fn, blurb in SECTIONS:
        if split:
            hp, bp = fn(P); hc, bc = fn(C)
            tables[sid] = {"id": sid, "heads": {P: hp, C: hc}, "sets": {P: bp, C: bc}, "modcol": MODCOL.get(sid)}
        else:
            h, b = fn(); tables[sid] = {"id": sid, "head": h, "sets": {P: b}, "modcol": None}
    for sid, fn, mc in (("asks", spec_asks, 2), ("uncalled", spec_uncalled, 2), ("unref", spec_unref, 2), ("unsched", spec_unsched, 1), ("dis", spec_dis, None)):
        h, b = fn(); tables[sid] = {"id": sid, "head": h, "sets": {"all": b}, "modcol": mc}
    # diagram data: estate members only
    nodes = [{"id": p["id"], "module": p["module"], "lang": p["lang"], "cics": p.get("cics", False)} for p in g["programs"] + g["leaves"]]
    dedges = []
    seen = set()
    for e in rows("program"):
        if e["to_class"] in ("program", "leaf-HLASM", "leaf-PL/I") and e["from"] != e["to"]:
            k = (e["from"], e["to"], e["kind"], e["certainty"])
            if k not in seen:
                seen.add(k); dedges.append({"f": e["from"], "t": e["to"], "k": e["kind"], "c": e["certainty"], "cite": e["cite"]})
    data = {"tables": tables, "nodes": nodes, "dedges": dedges, "modules": MODS, "copybooks": [cb for cb in g["copybooks"] if len(cb["programs"]) > 1]}
    tabs = [("diagram", "Diagram")] + [(s, t) for s, t, *_ in SECTIONS] + [("unsched", "Unscheduled jobs"), ("asks", "Open asks"), ("uncalled", "Uncalled & unreferenced"), ("dis", "vs chain-graph.md")]
    top = cards([("programs", "COBOL programs", SUM["programs"]), ("leaves", "HLASM / PL/I leaves", SUM["leaves"]), ("jobs", "JCL jobs", SUM["jobs"]),
                 ("pp", "program edges PROVEN", n_rows("program", P)), ("pc", "program edges CANDIDATE", n_rows("program", C)),
                 ("cross", "edges crossing modules", SUM["cross_module_edges"]), ("asks", "open asks", SUM["open_asks"]),
                 ("uncalled", "uncalled programs", SUM["uncalled"]), ("unref", "unreferenced members", SUM["unreferenced"]),
                 ("unsched", "unscheduled jobs", SUM["unscheduled_jobs"]), ("dis", "disagreements with chain-graph.md", SUM["disagreements"])])
    top += ('<p class="note">Every edge is either <span class="P">%s</span> or <span class="C">%s</span>. The two are never shown in one list: each table and the diagram show one certainty at a time. '
            'A dynamic site with no settled target is an open ask, not an edge.</p>') % (PL, CL)
    panels = ('<section class="panel" id="p-diagram"><h2>Program-to-program diagram</h2><p class="note">Estate members only (system routines omitted; they are in the Program to program tab). '
              'Click a program to highlight its edges and list them below; click it again or press Escape to clear.</p><div class="bar" id="dbar"></div>'
              '<div class="legend" id="legend"></div><div id="svgwrap"></div><div id="dsel"></div></section>')
    for sid, title, split, fn, blurb in SECTIONS:
        panels += '<section class="panel" id="p-%s"><h2>%s</h2><p class="note">%s</p><div id="h-%s"></div>%s</section>' % (
            sid, esc(title), esc(blurb), sid, '<h3>By copybook</h3><div id="cbd"></div>' if sid == "copybook" else "")
    panels += '<section class="panel" id="p-unsched"><h2>Unscheduled JCL jobs</h2><p class="note">Jobs that no table in app/scheduler orders. How they are run is not in the repository.</p><div id="h-unsched"></div></section>'
    panels += '<section class="panel" id="p-asks"><h2>Open asks</h2><p class="note">Dynamic sites whose target the sources do not settle. Questions with an owner placeholder; nothing was guessed.</p><div id="h-asks"></div></section>'
    panels += ('<section class="panel" id="p-uncalled"><h2>Uncalled programs</h2><p class="note">No PROVEN inbound edge from a job step, a transaction or another program.</p><div id="h-uncalled"></div>'
               '<h2>Unreferenced members</h2><p class="note">Catalogued members that no scan result names. The last column is a plain text search of app/ for the name: a hit means the source mentions it where the scanner does not read it (for example assembler macro calls).</p><div id="h-unref"></div></section>')
    panels += '<section class="panel" id="p-dis"><h2>Disagreements with docs/chain-graph.md</h2><p class="note">Row-by-row comparison of the generated chain graph\'s Programs and Jobs tables with the scan.</p><div id="h-dis"></div></section>'
    js = r"""
for(const id of Object.keys(DATA.tables)){const h=$('#h-'+id);if(h)mkTable(h,DATA.tables[id])}
/* copybook collapsibles */
(function(){const host=$('#cbd');for(const cb of DATA.copybooks){const d=el('details',{},el('summary',{text:cb.name+' - '+cb.programs.length+' programs, modules: '+cb.modules.join(', ')}));const ul=el('ul');cb.programs.forEach(u=>ul.append(el('li',{},document.createTextNode(u.program+' ('+u.module+') at '),el('code',{text:u.cite}))));d.append(el('div',{},el('p',{class:'note',text:'Source: '+(cb.path||'not in estate (system or generated)')}),ul));host.append(d)}})();
/* diagram */
(function(){const NS='http://www.w3.org/2000/svg',COL={CARDDEMO:'#dbe8fb',AUTH:'#fde6cf',TRANTYPE:'#dcf3e1',VSAMMQ:'#efe1f7'},S=(t,a)=>{const e=document.createElementNS(NS,t);for(const k in a)e.setAttribute(k,a[k]);return e};
 let cert='PROVEN',pick=null;const bar=$('#dbar'),seg=el('span',{class:'seg'});
 ['PROVEN','CANDIDATE'].forEach((l,i)=>{const n=DATA.dedges.filter(e=>e.c===l).length,inp=el('input',{type:'radio',name:'dc',value:l});if(!i)inp.checked=true;const lab=el('label',{},inp,el('span',{text:l+' ('+n+')'}));if(!i)lab.classList.add('sel');seg.append(lab);inp.addEventListener('change',()=>{cert=l;pick=null;$$('label',seg).forEach(x=>x.classList.toggle('sel',x===lab));draw()})});
 bar.append(seg,el('span',{class:'count',id:'dcount'}));
 $('#legend').append(...DATA.modules.map(m=>el('span',{},el('i',{class:'sw',style:'background:'+COL[m]}),document.createTextNode(m))),el('span',{text:'rounded = HLASM / PL/I leaf'}),el('span',{text:'bold border = CICS program'}));
 function draw(){const edges=DATA.dedges.filter(e=>e.c===cert),used=new Set(edges.flatMap(e=>[e.f,e.t])),W=1400,cw=132,rh=30,pos={};let y=10;const svg=S('svg',{viewBox:'0 0 '+W+' 10',id:'graph',role:'img','aria-label':cert+' program-to-program edges'});
  svg.append(S('defs',{}));svg.firstChild.innerHTML='<marker id="ah" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse"><path d="M0 0L10 5L0 10z" fill="#55607a"/></marker><marker id="ahh" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse"><path d="M0 0L10 5L0 10z" fill="#c2410c"/></marker>';
  const gE=S('g',{}),gN=S('g',{});
  for(const m of DATA.modules){const ns=DATA.nodes.filter(n=>n.module===m&&used.has(n.id));if(!ns.length)continue;const per=Math.floor((W-40)/(cw+14)),rowsN=Math.ceil(ns.length/per),h=rowsN*(rh+70)+40;
   svg.append(S('rect',{x:6,y:y,width:W-12,height:h,fill:'none',stroke:'#aab3c2','stroke-dasharray':'4 3',rx:6}));const t=S('text',{x:14,y:y+15,'font-weight':'700'});t.textContent=m+' ('+ns.length+' programs with '+cert+' edges)';svg.append(t);
   ns.forEach((n,i)=>{const x=20+(i%per)*(cw+14),yy=y+62+Math.floor(i/per)*(rh+70)+((i%per)%2)*14;pos[n.id]={x:x+cw/2,y:yy+rh/2,x0:x,y0:yy};
    const r=S('rect',{x:x,y:yy,width:cw,height:rh,rx:n.lang==='COBOL'?3:14,fill:COL[m],stroke:'#44506a','stroke-width':n.cics?2.2:1,class:'node','data-id':n.id,tabindex:0,style:'cursor:pointer'});
    const tx=S('text',{x:x+cw/2,y:yy+19,'text-anchor':'middle','pointer-events':'none'});tx.textContent=n.id+(n.lang==='COBOL'?'':' ['+n.lang+']');gN.append(r,tx);
    const go=()=>{pick=pick===n.id?null:n.id;hl()};r.addEventListener('click',go);r.addEventListener('keydown',e=>{if(e.key==='Enter'||e.key===' '){e.preventDefault();go()}})});y+=h+12}
  const seenP={};for(const e of edges){const a=pos[e.f],b=pos[e.t];if(!a||!b)continue;const k=[e.f,e.t].sort().join('|');seenP[k]=(seenP[k]||0)+1;const off=(seenP[k]-1)*10,dx=b.x-a.x,dy=b.y-a.y,down=dy>=0;
   const sy=a.y+(Math.abs(dy)<1?-15:(down?15:-15)),ty=b.y+(Math.abs(dy)<1?-15:(down?-15:15)),bend=Math.abs(dy)<1?-(14+Math.min(26,Math.abs(dx)/30)+off):0;
   const p=S('path',{d:'M'+a.x+' '+sy+' C'+(a.x+off)+' '+(sy+(bend||(ty-sy)/2))+','+(b.x+off)+' '+(ty+(bend||-(ty-sy)/2))+','+b.x+' '+ty,fill:'none',stroke:'#55607a','stroke-width':1.1,'stroke-dasharray':cert==='CANDIDATE'?'5 4':'','marker-end':'url(#ah)',class:'edge','data-f':e.f,'data-t':e.t});
   const ti=S('title',{});ti.textContent=e.f+' '+e.k+' '+e.t+' ['+e.c+'] '+e.cite;p.append(ti);gE.append(p)}
  svg.append(gE,gN);svg.setAttribute('viewBox','0 0 '+W+' '+(y+4));$('#svgwrap').replaceChildren(svg);$('#dcount').textContent=edges.length+' '+cert+' edges between '+used.size+' estate members';hl()}
 function hl(){$$('#graph .edge').forEach(p=>{const on=pick&&(p.dataset.f===pick||p.dataset.t===pick);p.setAttribute('stroke',on?'#c2410c':'#55607a');p.setAttribute('stroke-width',on?2.4:1.1);p.setAttribute('marker-end',on?'url(#ahh)':'url(#ah)');p.setAttribute('opacity',pick&&!on?0.15:1)});
  $$('#graph .node').forEach(r=>r.setAttribute('opacity',!pick||r.dataset.id===pick||DATA.dedges.some(e=>e.c===cert&&((e.f===pick&&e.t===r.dataset.id)||(e.t===pick&&e.f===r.dataset.id)))?1:0.3));
  const host=$('#dsel');host.replaceChildren();if(!pick)return;const es=DATA.dedges.filter(e=>e.c===cert&&(e.f===pick||e.t===pick));
  host.append(el('h3',{id:'dsel-title',text:pick+': '+es.length+' '+cert+' edges'}));const ul=el('ul');es.forEach(e=>ul.append(el('li',{},document.createTextNode(e.f+' - '+e.k+' -> '+e.t+' at '),el('code',{text:e.cite}))));host.append(ul)}
 document.addEventListener('keydown',e=>{if(e.key==='Escape'&&pick){pick=null;hl()}});draw()})();
"""
    open(os.path.join(D, "call-graph.html"), "w").write(page(
        "CardDemo estate - call graph (as scanned)", head_html("architecture-as-is.html", "As-is architecture", "call-graph.md"), tabs, (top, panels), data, js))



# ================================================================== architecture (as is)
cics_progs = sorted(p["id"] for p in g["programs"] if p.get("cics"))
batch_progs = sorted(p["id"] for p in g["programs"] if not p.get("cics"))
by_mod = lambda names, m: [n for n in names if (prog.get(n) or leaf.get(n))["module"] == m]
tx = rows("transaction")
jobs_by_mod = collections.Counter(j["module"] for j in g["jobs"])
ST = g["stores"]


def tech(s):
    t, n = s["type"], s["name"]
    if t in ("KSDS", "ESDS", "RRDS"): return "VSAM " + t
    if t == "AIX": return "VSAM alternate index"
    if t == "PATH": return "VSAM path"
    if t == "GDG": return "GDG"
    if t == "Db2 table": return "Db2 table"
    if t == "IMS": return "IMS"
    if t == "MQ": return "MQ"
    if t.startswith("CICS T"): return "CICS TD queue"
    if t.startswith("sequential"): return "Sequential"
    if t == "PDS member": return "PDS member"
    return "Other / unresolved"


def owner(s):
    """Owner = the layer whose programs write or update the store. Stated rule, not an inference about operations."""
    w = lambda layer: sorted({x[0] for d in ("writes", "updates") for x in s[layer].get(d, [])})
    r = lambda layer: sorted({x[0] for x in s[layer].get("reads", [])})
    ow, bw = w("online"), w("batch")
    if ow and bw: o = "shared (online and batch write)"
    elif ow: o = "online"
    elif bw: o = "batch"
    elif s["utility"] or s["definition"]: o = "batch (utility steps only: no program writes it)"
    elif r("online") or r("batch"): o = "unknown (read only in the scan; writer not in the repository)"
    else: o = "unknown"
    return o, ow, bw, r("online"), r("batch")


def store_rows():
    out = []
    for s in ST:
        o, ow, bw, orr, br = owner(s)
        ex = s["extra"]
        detail = "; ".join("%s=%s" % (k, ", ".join(v) if isinstance(v, list) else v) for k, v in sorted(ex.items()) if k != "type_basis")
        out.append([s["name"], tech(s), s["module"] or "-", o, ", ".join(ow) or "-", ", ".join(orr) or "-", ", ".join(bw) or "-", ", ".join(br) or "-",
                    s["definition"] or ex.get("type_basis") or "-", detail or "-", len(s["utility"])])
    return (["Store", "Technology", "Module (first seen)", "Owner layer", "Online writers", "Online readers", "Batch writers", "Batch readers", "Defined / typed at", "Definition detail", "Utility steps"], out)


SH, SR = store_rows()
tech_count = collections.Counter(r[1] for r in SR)
shared_rows = [r for r in SR if (r[4] != "-" or r[5] != "-") and (r[6] != "-" or r[7] != "-" or r[10])]
shared_strict = [r for r in SR if (r[4] != "-" or r[5] != "-") and (r[6] != "-" or r[7] != "-")]


# ---- diagram spec -> SVG and mermaid
def svg_diagram(did, title, cols, arrows, width=1420):
    """cols: list of (column title, [(id, title, [lines], fill)]); arrows: (from id, to id, label, dashed).
    Each arrow gets its own port on the box edge, so labels (drawn at the source port) never overlap."""
    gap = 210
    cw = (width - 40 - gap * (len(cols) - 1)) // len(cols)
    col_of = {bid: ci for ci, (_, boxes) in enumerate(cols) for bid, *_ in boxes}
    arrows = [x for x in arrows if x[0] in col_of and x[1] in col_of]
    ports = collections.defaultdict(list)          # (box, side) -> arrow indexes
    for i, (a, b, label, dashed) in enumerate(arrows):
        if col_of[a] == col_of[b]:
            ports[(a, "R")].append(i); ports[(b, "R")].append(i)
        elif col_of[a] < col_of[b]:
            ports[(a, "R")].append(i); ports[(b, "L")].append(i)
        else:
            ports[(a, "L")].append(i); ports[(b, "R")].append(i)
    pos, parts, maxy, order = {}, [], 0, {}
    for ci, (ct, boxes) in enumerate(cols):
        x, y = 20 + ci * (cw + gap), 44
        parts.append('<text x="%d" y="28" font-weight="700" text-anchor="middle" style="font-size:13px">%s</text>' % (x + cw // 2, esc(ct)))
        for bi, (bid, bt, lines, fill) in enumerate(boxes):
            wrapped = []
            for ln in lines:
                while len(ln) > cw // 6:
                    cut = ln.rfind(" ", 0, cw // 6)
                    cut = cut if cut > 10 else cw // 6
                    wrapped.append(ln[:cut]); ln = ln[cut:].strip()
                wrapped.append(ln)
            h = max(26 + 14 * len(wrapped), 15 * max(len(ports[(bid, "L")]), len(ports[(bid, "R")])) + 14)
            pos[bid] = (x, y, cw, h); order[bid] = (ci, bi)
            parts.append('<g class="box" data-box="%s"><rect x="%d" y="%d" width="%d" height="%d" rx="5" fill="%s" stroke="#44506a"/><text x="%d" y="%d" font-weight="700">%s</text>%s</g>' % (
                esc(bid), x, y, cw, h, fill, x + 8, y + 17, esc(bt),
                "".join('<text x="%d" y="%d">%s</text>' % (x + 8, y + 33 + 14 * i, esc(l)) for i, l in enumerate(wrapped))))
            y += h + 18
        maxy = max(maxy, y)
    port_y = {}
    for (bid, side), idx in ports.items():
        other = lambda i: arrows[i][1] if arrows[i][0] == bid else arrows[i][0]
        idx.sort(key=lambda i: (pos[other(i)][1], i))
        x, y, w, h = pos[bid]
        for k, i in enumerate(idx):
            port_y[(i, bid)] = y + (k + 1) * h / (len(idx) + 1)
    ar = []
    for i, (a, b, label, dashed) in enumerate(arrows):
        ax, ay, aw, ah = pos[a]; bx, by, bw, bh = pos[b]
        y1, y2 = port_y[(i, a)], port_y[(i, b)]
        col = "#a05a00" if dashed else "#33415c"
        if ax == bx:
            x1 = x2 = ax + aw
            d = "M%d %d C%d %d,%d %d,%d %d" % (x1, y1, x1 + 70, y1, x2 + 70, y2, x2 + 2, y2)
            lx, anchor = x1 + 8, "start"
        elif ax < bx:
            x1, x2 = ax + aw, bx
            d = "M%d %d C%d %d,%d %d,%d %d" % (x1, y1, x1 + 120, y1, x2 - 60, y2, x2, y2)
            lx, anchor = x1 + 6, "start"
        else:
            x1, x2 = ax, bx + bw
            d = "M%d %d C%d %d,%d %d,%d %d" % (x1, y1, x1 - 120, y1, x2 + 60, y2, x2, y2)
            lx, anchor = x1 - 6, "end"
        ar.append('<path class="arrow" d="%s" fill="none" stroke="%s" stroke-width="1.3" %s marker-end="url(#a-%s%s)"><title>%s</title></path>' % (
            d, col, 'stroke-dasharray="5 4"' if dashed else "", did, "c" if dashed else "", esc("%s -> %s: %s%s" % (a, b, label, " [CANDIDATE]" if dashed else " [PROVEN]"))))
        if label:
            ar.append('<text x="%d" y="%d" text-anchor="%s" fill="%s" style="font-size:10px;paint-order:stroke;stroke:#fff;stroke-width:3px">%s</text>' % (lx, y1 - 2, anchor, col, esc(label)))
    mk = lambda mid, colr: '<marker id="%s" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto"><path d="M0 0L10 5L0 10z" fill="%s"/></marker>' % (mid, colr)
    return ('<svg id="%s" class="diagram" role="img" aria-label="%s" viewBox="0 0 %d %d" xmlns="http://www.w3.org/2000/svg"><defs>%s%s</defs>%s%s</svg>'
            '<p class="note">Solid arrow = PROVEN edges only; dashed amber arrow = CANDIDATE edges only. Each label sits where its arrow leaves a box; numbers are edge counts from the call graph. Hover an arrow for its two ends.</p>') % (
        did, esc(title), width, maxy + 10, mk("a-" + did, "#33415c"), mk("a-" + did + "c", "#a05a00"), "".join(parts), "".join(ar))


def mermaid(cols, arrows):
    out = ["```mermaid", "flowchart LR"]
    ids = {}
    for ci, (ct, boxes) in enumerate(cols):
        out.append('  subgraph c%d["%s"]' % (ci, ct))
        for bid, bt, lines, _ in boxes:
            ids[bid] = "n%d" % len(ids)
            out.append('    %s["%s"]' % (ids[bid], "<br/>".join([bt] + lines).replace('"', "'")))
        out.append("  end")
    for a, b, label, dashed in arrows:
        if a in ids and b in ids:
            out.append("  %s %s%s %s" % (ids[a], "-.->" if dashed else "-->", '|"%s"|' % label if label else "", ids[b]))
    return "\n".join(out + ["```"])


COLR = {"CARDDEMO": "#dbe8fb", "AUTH": "#fde6cf", "TRANTYPE": "#dcf3e1", "VSAMMQ": "#efe1f7"}
TECH_GROUPS = [("VSAM", ["VSAM KSDS", "VSAM ESDS", "VSAM RRDS", "VSAM alternate index", "VSAM path"]), ("GDG", ["GDG"]), ("Sequential / PDS", ["Sequential", "PDS member"]),
               ("Db2", ["Db2 table"]), ("IMS", ["IMS"]), ("MQ", ["MQ"]), ("CICS TD queue", ["CICS TD queue"])]
tgroup = {t: gname for gname, ts in TECH_GROUPS for t in ts}
store_tech = {s["name"]: tgroup.get(tech(s)) for s in ST}


def data_arrows(progs_filter, src_id):
    """Aggregated program->store-technology arrows: (src box, tech box, 'r/w/u counts', dashed)."""
    agg = collections.defaultdict(lambda: collections.Counter())
    for e in rows("data"):
        if e["store_type"] == "map" or not progs_filter(e["from"]): continue
        tg = store_tech.get(e["to"])
        if tg: agg[(src_id(e["from"]), "st:" + tg, e["certainty"] == C)][e["direction"]] += 1
    return [(a, b, " ".join("%s %d" % (k, v) for k, v in sorted(cn.items())), dashed) for (a, b, dashed), cn in sorted(agg.items())]


def store_boxes(used=None):
    out = []
    for gname, ts in TECH_GROUPS:
        n = sum(tech_count[t] for t in ts)
        if n and (used is None or "st:" + gname in used):
            out.append(("st:" + gname, "%s (%d)" % (gname, n), [", ".join("%s %d" % (t.replace("VSAM ", ""), tech_count[t]) for t in ts if tech_count[t])] if len(ts) > 1 else [], "#f3f4f6"))
    return out


def pp_arrows(filter_fn, box):
    agg = collections.Counter()
    for e in rows("program"):
        if e.get("to_module") and e["to_class"].startswith(("program", "leaf")) and filter_fn(e) and box(e["from"]) != box(e["to"]):
            agg[(box(e["from"]), box(e["to"]), e["certainty"] == C, e["kind"].split(" (")[0])] += 1
    return [(a, b, "%s %d" % (k, n), d) for (a, b, d, k), n in sorted(agg.items())]


def diagrams():
    mod_box = lambda m: ("m:" + m, "%s - %s" % (m, M["module_names"][m]),
                         ["%d programs (%d CICS, %d batch)" % (len(by_mod(list(prog), m)), len(by_mod(cics_progs, m)), len(by_mod(batch_progs, m))),
                          "%d transactions, %d JCL jobs" % (sum(1 for e in tx if e["module"] == m), jobs_by_mod[m])]
                         + (["leaf members: " + ", ".join("%s [%s]" % (l, leaf[l]["lang"]) for l in sorted(leaf) if leaf[l]["module"] == m)] if any(leaf[l]["module"] == m for l in leaf) else []), COLR[m])
    pm = lambda n: "m:" + (prog.get(n) or leaf.get(n))["module"]
    sched_n = collections.Counter((e["dialect"], e.get("to_module")) for e in rows("schedule") if e["kind"] == "orders job" and e.get("to_module"))
    est_cols = [("Entry points", [("in:tx", "CICS transactions (%d)" % len(tx), ["defined in app/csd and app/app-*/csd"], "#fff"),
                                  ("in:ca7", "CA 7 schedule", ["app/scheduler/CardDemo.ca7"], "#fff"), ("in:ctm", "Control-M schedule", ["app/scheduler/CardDemo.controlm"], "#fff"),
                                  ("in:man", "Unscheduled JCL (%d jobs)" % len(g["unscheduled"]), ["how they are run is unknown"], "#fff")]),
                ("Applications (modules)", [mod_box(m) for m in MODS]), ("Data stores (by technology)", store_boxes())]
    est_ar = [("in:tx", "m:" + m, "%d" % n, False) for m, n in sorted(collections.Counter(e["module"] for e in tx).items())]
    est_ar += [("in:ca7" if d.startswith("ca7") else "in:ctm", "m:" + m, "%d jobs" % n, False) for (d, m), n in sorted(sched_n.items())]
    est_ar += [("in:man", "m:" + m, "%d" % n, False) for m, n in sorted(collections.Counter(j["module"] for j in g["jobs"] if j["name"] in g["unscheduled"]).items())]
    est_ar += pp_arrows(lambda e: True, pm) + data_arrows(lambda p_: True, pm)
    on_cols = [("Transactions", [("tx:" + m, "%s (%d)" % (m, sum(1 for e in tx if e["module"] == m)), [", ".join(sorted(e["from"] for e in tx if e["module"] == m))], COLR[m]) for m in MODS if any(e["module"] == m for e in tx)]),
               ("CICS programs", [("op:" + m, "%s (%d)" % (m, len(by_mod(cics_progs, m))), [", ".join(by_mod(cics_progs, m))], COLR[m]) for m in MODS if by_mod(cics_progs, m)]),
               ("Screens and stores", [("st:maps", "BMS mapsets (%d)" % len({e["to"] for e in rows("data") if e["store_type"] == "map"}), ["SEND MAP / RECEIVE MAP"], "#f3f4f6")])]
    on = lambda p_: p_ in cics_progs
    oar = data_arrows(on, lambda p_: "op:" + prog[p_]["module"])
    on_cols[2] = ("Screens and stores", on_cols[2][1] + store_boxes({b for _, b, _, _ in oar}))
    mapagg = collections.Counter((prog[e["from"]]["module"], e["certainty"] == C) for e in rows("data") if e["store_type"] == "map")
    on_ar = [("tx:" + m, "op:" + m, "initial program %d" % sum(1 for e in tx if e["module"] == m), False) for m in MODS if any(e["module"] == m for e in tx)]
    on_ar += pp_arrows(lambda e: e["from"] in cics_progs and e["to"] in cics_progs, lambda n: "op:" + prog[n]["module"])
    on_ar += [("op:" + m, "st:maps", "send/receive %d" % n, d) for (m, d), n in sorted(mapagg.items())] + oar
    tabs_ = g["schedule_tables"]
    ba = lambda p_: p_ in batch_progs
    bar_ = data_arrows(ba, lambda p_: "bp:" + prog[p_]["module"])
    ba_cols = [("Scheduler tables", [("sc:" + t["name"], t["name"], [t["dialect"], "%d jobs: %s" % (len(t["jobs"]), ", ".join(t["jobs"]))], "#fff") for t in tabs_]
                + [("sc:none", "Unscheduled (%d jobs)" % len(g["unscheduled"]), [", ".join(g["unscheduled"])], "#fff")]),
               ("JCL jobs", [("jb:" + m, "%s (%d jobs)" % (m, jobs_by_mod[m]), ["%d steps" % sum(1 for e in rows("job") if e["module"] == m and e["kind"] == "EXEC")], COLR[m]) for m in MODS if jobs_by_mod[m]]),
               ("Batch programs and utilities", [("bp:" + m, "%s (%d programs)" % (m, len(by_mod(batch_progs, m))), [", ".join(by_mod(batch_progs, m))], COLR[m]) for m in MODS if by_mod(batch_progs, m)]
                + [("bp:util", "Utilities and launchers", [", ".join(sorted({e["pgm"] for e in rows("job") if e["to_class"] in ("utility", "launcher")}))], "#f3f4f6"),
                   ("bp:leaf", "HLASM / PL/I leaves", [", ".join("%s [%s]" % (l, leaf[l]["lang"]) for l in sorted(leaf))], "#fff7d6")]),
               ("Data stores", store_boxes({b for _, b, _, _ in bar_}))]
    jm = {j["name"]: j["module"] for j in g["jobs"]}
    ba_ar = []
    for t in tabs_:
        for m, n in sorted(collections.Counter(jm[j] for j in t["jobs"] if j in jm).items()):
            ba_ar.append(("sc:" + t["name"], "jb:" + m, "%d" % n, False))
    for m, n in sorted(collections.Counter(jm[j] for j in g["unscheduled"]).items()): ba_ar.append(("sc:none", "jb:" + m, "%d" % n, False))
    stepagg = collections.Counter()
    for e in rows("job"):
        if e["kind"] != "EXEC": continue
        tgt = "bp:" + e["to_module"] if e["to"] in batch_progs else ("bp:util" if e["to_class"] in ("utility", "launcher") else None)
        if tgt: stepagg[("jb:" + e["module"], tgt)] += 1
    ba_ar += [(a, b, "%d steps" % n, False) for (a, b), n in sorted(stepagg.items())]
    leafagg = collections.Counter(("bp:" + e["module"], e["certainty"] == C) for e in rows("program") if e["to"] in leaf)
    ba_ar += [(a, "bp:leaf", "CALL %d" % n, d) for (a, d), n in sorted(leafagg.items())] + bar_
    da_cols = [("Online layer (CICS programs)", [("op:" + m, "%s online" % m, ["%d CICS programs" % len(by_mod(cics_progs, m))], COLR[m]) for m in MODS if by_mod(cics_progs, m)]),
               ("Data stores (owner layer in the Data tab)", store_boxes()),
               ("Batch layer (batch programs)", [("bp:" + m, "%s batch" % m, ["%d batch programs" % len(by_mod(batch_progs, m))], COLR[m]) for m in MODS if by_mod(batch_progs, m)])]
    da_ar = data_arrows(on, lambda p_: "op:" + prog[p_]["module"]) + data_arrows(ba, lambda p_: "bp:" + prog[p_]["module"])
    return [("estate", "The estate", est_cols, est_ar), ("online", "Online layer", on_cols, on_ar), ("batch", "Batch layer", ba_cols, ba_ar), ("data", "Data layer", da_cols, da_ar)]


DG = diagrams()
UNKNOWN = [
    ("Operations", "Batch window, run frequency beyond what the two scheduler exports state, restart and rerun procedures, on-call and incident handling, monitoring, SLAs, backup and recovery, and how the %d unscheduled jobs are started." % len(g["unscheduled"])),
    ("Operations", "Which of the two scheduler exports (CA 7, Control-M) is in force, or whether both are. They order overlapping job names."),
    ("Security", "The external security manager and its rules (RACF/ACF2/Top Secret profiles, CICS transaction security, dataset and Db2 authorizations). The repository holds only the application's own user file and sign-on program."),
    ("Security", "Encryption at rest and in transit, key management, audit logging, data masking policy, and PCI scope."),
    ("Volumes", "Record counts, transaction rates, file sizes and growth, batch run times, online response times and concurrency. The sample data under app/data is synthetic and says nothing about production volumes."),
    ("External interfaces", "Who sends and receives the MQ messages (queue names are run-time data, see call-graph open asks), the partner for the FTP job, the consumer of statement and report output, and any card network or authorization switch."),
    ("External interfaces", "Any interface not expressed as source in app/: none can be ruled in or out from the repository."),
    ("Platform", "z/OS, CICS, Db2, IMS and MQ versions, LPAR and region topology, compiler and options, library manager, and the build and promotion path."),
]


def sub_apps():
    out = []
    for m in MODS:
        if m == "CARDDEMO": continue
        inx = sorted({"%s %s %s [%s] at %s" % (e["from"], e["kind"], e["to"], e["certainty"], e["cite"]) for e in rows("program") if e.get("cross") and e["to_module"] == m and e["module"] != m})
        outx = sorted({"%s %s %s [%s] at %s" % (e["from"], e["kind"], e["to"], e["certainty"], e["cite"]) for e in rows("program") if e.get("cross") and e["module"] == m})
        cps = sorted({"%s (%d programs of %s)" % (cb["name"], sum(1 for u in cb["programs"] if u["module"] == m), m) for cb in g["copybooks"] if cb["owner"] in ("shared", "CARDDEMO") and m in cb["modules"] and cb["path"]})
        files = sorted({"%s %s %s via CICS file %s [%s] at %s; file defined at %s" % (e["from"], e["direction"], e["to"], e["dd"], e["certainty"], e["cite"], e.get("cite2")) for e in rows("data") if e["module"] == m and e.get("cross")})
        sch = sorted({"%s orders %s [%s] at %s" % (e["from"], e["to"], e["certainty"], e["cite"]) for e in rows("schedule") if e["kind"] == "orders job" and e.get("to_module") == m})
        txs = sorted("%s -> %s at %s" % (e["from"], e["to"], e["cite"]) for e in tx if e["module"] == m)
        out.append({"id": m, "name": M["module_names"][m], "programs": by_mod(list(prog), m), "transactions": txs, "jobs": sorted(j["name"] for j in g["jobs"] if j["module"] == m),
                    "attach": [("Base program transfers control into it", inx), ("It transfers control back to the base", outx), ("It compiles against base copybooks", cps),
                               ("It reads or writes base files through CICS file definitions in the base CSD", files), ("The base scheduler exports order its jobs", sch)]})
    return out


SUBS = sub_apps()
LEAF_CALLERS = {l: sorted({"%s CALL at %s [%s]" % (e["from"], e["cite"], e["certainty"]) for e in rows("program") if e["to"] == l}) for l in leaf}


LEG_H = ["Member", "Kind", "Source", "Module", "Lines", "Called or referenced by"]
LEG_R = [[m_["member"], m_["kind"], m_["path"], m_["module"], m_["lines"] or "-",
          "; ".join(LEAF_CALLERS.get(m_["member"]) or m_["referenced_by"]) or next(("not in the scan; name appears at %s (text search)" % u["grep"] for u in g["unreferenced"] if u["member"].upper() == m_["member"] and u.get("grep")), "no reference found")] for m_ in g["legacy_members"]]


def write_arch_md():
    o = ["# As-is architecture of the CardDemo estate", "",
         "Baseline: `%s` (%s), repository `%s`. Tool: %s. Interactive page: [architecture-as-is.html](architecture-as-is.html)." % (SHA, M["baseline_date"], M["repo"], M["tool"]), "",
         "This document describes what exists in the repository. It proposes no target. Every statement is taken from, and cites:", "",
         "- **Part A** - [INTAKE.md](../INTAKE.md): what the estate supplies, what is absent, what runs.",
         "- **Part B** - [fan-out.md](fan-out.md) and the module scans under `store/<MODULE>/`; module map `store/modules.json`.",
         "- **Part C** - [INTAKE.md, scanner cross-check and open questions](../INTAKE.md#scanner-cross-check-part-c).",
         "- **Part D** - [call-graph.md](call-graph.md): every edge with its file and line, PROVEN or CANDIDATE.", "",
         "Counts in this document are counts of rows in call-graph.md (data: `graph.json`). Modules analyzed: %s. Not analyzed: %s." % (", ".join(M["analyzed"]), ", ".join(M["not_analyzed"]) or "none"), ""]
    o += ["## 1. The estate", "", "Four modules (Part B, `store/modules.json`): the base application and three sub-applications under `app/app-*`.", "",
          md_table(["Module", "Name", "Programs", "CICS programs", "Batch programs", "Transactions", "JCL jobs"],
                   [[m, M["module_names"][m], len(by_mod(list(prog), m)), len(by_mod(cics_progs, m)), len(by_mod(batch_progs, m)), sum(1 for e in tx if e["module"] == m), jobs_by_mod[m]] for m in MODS]), "",
          "Solid arrows are PROVEN edges only, dashed arrows CANDIDATE edges only (Part D).", "", mermaid(DG[0][2], DG[0][3]), ""]
    o += ["## 2. Online layer", "", "CICS pseudo-conversational programs entered through %d transaction ids (call-graph.md, *Transaction id to initial program*). A program is counted as online when a CSD defines it or it issues EXEC CICS commands. "
          "None of these programs runs off-host: the CICS-supplied copybooks and the generated symbolic maps are not available (Part A, INTAKE.md)." % len(tx), "",
          mermaid(DG[1][2], DG[1][3]), "",
          md_table(["Transaction", "Initial program", "Module", "Cited at"], [[e["from"], e["to"], e["module"], c(e["cite"])] for e in sorted(tx, key=lambda e: e["from"])]), "",
          "Program-to-program navigation is almost entirely dynamic: %d PROVEN and %d CANDIDATE XCTL/LINK edges, plus %d open asks where the target is run-time data (call-graph.md, *Program to program* and *Open asks*)." % (
              sum(1 for e in rows("program", P) if e["kind"].startswith("CICS")), sum(1 for e in rows("program", C) if e["kind"].startswith("CICS")), sum(1 for a in g["asks"] if a["kind"] == "dynamic-target")), ""]
    o += ["## 3. Batch layer", "", "%d JCL jobs; %d are ordered by a scheduler table, %d are not (call-graph.md, *Scheduler order* and *Unscheduled JCL jobs*). Two scheduler exports exist: CA 7 and Control-M; which is in force is unknown." % (
        SUM["jobs"], SUM["jobs"] - len(g["unscheduled"]), len(g["unscheduled"])), "", mermaid(DG[2][2], DG[2][3]), "",
          md_table(["Scheduler table", "Dialect", "Source", "Jobs in order of appearance"], [[t["name"], t["dialect"], c(t["path"]), ", ".join(t["jobs"])] for t in g["schedule_tables"]]), "",
          "What runs today off-host is four chains (posting, interest, trantype-update, trantype-dynamic), proven in Part A (INTAKE.md, *What runs today*).", ""]
    o += ["## 4. Data layer", "", "Owner layer rule used here: the layer whose programs write or update the store (call-graph.md, *Program to dataset, table, queue or map*). A store written by both layers is shared; a store only touched by utility steps (IDCAMS, SORT, IEBGENER) is batch; a store that is only read in the scan has an unknown owner.", "",
          mermaid(DG[3][2], DG[3][3]), "", md_table(["Technology", "Stores"], sorted(tech_count.items())), ""]
    for gname, ts in TECH_GROUPS + [("Other / unresolved", ["Other / unresolved"])]:
        b = [r for r in SR if r[1] in ts]
        if b: o += ["### %s (%d)" % (gname, len(b)), "", md_table(SH, [[c(x) if i == 8 and x != "-" else x for i, x in enumerate(r)] for r in b]), ""]
    o += ["MQ queue names are not in the scan results: the MQ calls are proven, the queue names are open asks (call-graph.md, *Open asks*).", ""]
    o += ["## 5. Where online and batch share data", "", "Stores touched by at least one online program and by at least one batch program or utility step (%d). There is no evidence in the repository of how the two are kept apart in time: the scheduler exports contain file close and open jobs around batch steps, but their effect on the CICS region is not provable from source (INTAKE.md, open questions)." % len(shared_rows), "",
          md_table(SH, [[c(x) if i == 8 and x != "-" else x for i, x in enumerate(r)] for r in shared_rows]), ""]
    o += ["## 6. Sub-applications under `app/app-*` and how they attach to the base", ""]
    for s_ in SUBS:
        o += ["### %s - %s" % (s_["id"], s_["name"]), "", "Programs: %s. Jobs: %s. Transactions: %s." % (", ".join(s_["programs"]), ", ".join(s_["jobs"]) or "none", "; ".join(s_["transactions"]) or "none"), ""]
        for lab, items in s_["attach"]:
            o += ["**%s** (%d)" % (lab, len(items)), ""] + (["- " + i for i in items] if items else ["- none found in the scan"]) + [""]
    o += ["## 7. HLASM and PL/I members", "", "Leaf nodes: the scanner records their names and callers, not their logic (INTAKE.md, open questions; docs/LEGACY-LANGUAGES.md). None runs off-host.", "",
          md_table(LEG_H, [[r[0], r[1], c(r[2]), r[3], r[4], r[5]] for r in LEG_R]), ""]
    o += ["## 8. What the repository does not show (unknown)", "", md_table(["Area", "Unknown"], UNKNOWN), ""]
    open(os.path.join(D, "architecture-as-is.md"), "w").write("\n".join(o))


def html_table(h, b, cite_cols=()):
    return '<div class="tw"><table><thead><tr>%s</tr></thead><tbody>%s</tbody></table></div>' % (
        "".join("<th>%s</th>" % esc(x) for x in h),
        "".join("<tr>%s</tr>" % "".join("<td>%s</td>" % ("<code>%s</code>" % esc(str(x)) if i in cite_cols and x not in ("-", None) else esc(str(x if x not in (None, "") else "-"))) for i, x in enumerate(r)) for r in b))


def write_arch_html():
    tabs = [("estate", "Estate"), ("online", "Online layer"), ("batch", "Batch layer"), ("data", "Data layer"), ("shared", "Online/batch shared data"),
            ("subapps", "Sub-applications"), ("legacy", "HLASM & PL/I"), ("unknown", "Unknowns")]
    top = cards([("modules", "modules", len(MODS)), ("programs", "COBOL programs", SUM["programs"]), ("cics", "CICS programs", len(cics_progs)), ("batchp", "batch programs", len(batch_progs)),
                 ("tx", "transactions", len(tx)), ("jobs", "JCL jobs", SUM["jobs"]), ("unsched", "unscheduled jobs", len(g["unscheduled"])), ("stores", "data stores", len(SR)),
                 ("shared", "stores shared online/batch", len(shared_rows)), ("leaves", "HLASM / PL/I members", len(LEG_R)), ("unknown", "unknowns listed", len(UNKNOWN))])
    top += ('<p class="note">Describes what exists; proposes no target. Sources: Part A <a href="../INTAKE.md">INTAKE.md</a>, Part B <a href="fan-out.md">fan-out.md</a>, '
            'Part C <a href="../INTAKE.md">INTAKE.md (cross-check, open questions)</a>, Part D <a href="call-graph.html">call-graph.html</a> / <a href="call-graph.md">call-graph.md</a>. Counts are row counts of the call graph data.</p>')
    dg = {d[0]: svg_diagram("dg-" + d[0], d[1], d[2], d[3]) for d in DG}
    pn = lambda i, body: '<section class="panel" id="p-%s">%s</section>' % (i, body)
    modt = html_table(["Module", "Name", "Programs", "CICS programs", "Batch programs", "Transactions", "JCL jobs"],
                      [[m, M["module_names"][m], len(by_mod(list(prog), m)), len(by_mod(cics_progs, m)), len(by_mod(batch_progs, m)), sum(1 for e in tx if e["module"] == m), jobs_by_mod[m]] for m in MODS])
    panels = pn("estate", "<h2>The estate</h2><p class=\"note\">Four modules from the frozen map store/modules.json (Part B): the base application and three sub-applications under app/app-*.</p>" + dg["estate"] + "<h3>Modules</h3>" + modt)
    panels += pn("online", "<h2>Online layer</h2><p class=\"note\">CICS programs entered through %d transaction ids (call-graph, Transaction id to initial program). None runs off-host (Part A).</p>" % len(tx) + dg["online"] +
                 "<h3>Transactions</h3><div id=\"h-tx\"></div>" +
                 "".join("<details><summary>%s CICS programs (%d)</summary><div>%s</div></details>" % (m, len(by_mod(cics_progs, m)), esc(", ".join(by_mod(cics_progs, m)))) for m in MODS if by_mod(cics_progs, m)))
    panels += pn("batch", "<h2>Batch layer</h2><p class=\"note\">%d JCL jobs: %d ordered by a scheduler table, %d not. Two scheduler exports (CA 7, Control-M); which is in force is unknown. Four chains run off-host today (Part A).</p>" % (SUM["jobs"], SUM["jobs"] - len(g["unscheduled"]), len(g["unscheduled"])) + dg["batch"] +
                 "<h3>Scheduler tables</h3>" + "".join("<details><summary>%s (%s, %d jobs)</summary><div><p class=\"note\">Source: <code>%s</code></p>%s</div></details>" % (esc(t["name"]), t["dialect"], len(t["jobs"]), t["path"], esc(", ".join(t["jobs"]))) for t in g["schedule_tables"]) +
                 "<details><summary>Unscheduled jobs (%d)</summary><div>%s</div></details>" % (len(g["unscheduled"]), esc(", ".join(g["unscheduled"]))) +
                 "".join("<details><summary>%s batch programs (%d)</summary><div>%s</div></details>" % (m, len(by_mod(batch_progs, m)), esc(", ".join(by_mod(batch_progs, m)))) for m in MODS if by_mod(batch_progs, m)))
    panels += pn("data", "<h2>Data layer</h2><p class=\"note\">Owner layer = the layer whose programs write or update the store. Written by both = shared; touched only by utility steps = batch; only read in the scan = unknown. MQ queue names are open asks.</p>" + dg["data"] + "<h3>Stores</h3><div id=\"h-stores\"></div>")
    panels += pn("shared", "<h2>Where online and batch share data</h2><p class=\"note\">Stores touched by at least one online program and by at least one batch program or utility step. How the two are kept apart in time is not provable from the repository.</p><div id=\"h-sharedstores\"></div>")
    sub = ""
    for s_ in SUBS:
        sub += "<h3>%s - %s</h3><p>Programs: %s. Jobs: %s.</p>" % (s_["id"], esc(s_["name"]), esc(", ".join(s_["programs"])), esc(", ".join(s_["jobs"]) or "none"))
        sub += "<details><summary>Transactions (%d)</summary><div><ul>%s</ul></div></details>" % (len(s_["transactions"]), "".join("<li><code>%s</code></li>" % esc(t) for t in s_["transactions"]) or "<li>none</li>")
        for lab, items in s_["attach"]:
            sub += "<details class=\"attach\" data-n=\"%d\"><summary>%s (%d)</summary><div><ul>%s</ul></div></details>" % (len(items), esc(lab), len(items), "".join("<li><code>%s</code></li>" % esc(i) for i in items) or "<li>none found in the scan</li>")
    panels += pn("subapps", "<h2>Sub-applications under app/app-* and how they attach to the base</h2><p class=\"note\">Each item is an edge of the call graph with its certainty and citation.</p>" + sub)
    panels += pn("legacy", "<h2>HLASM and PL/I members</h2><p class=\"note\">Leaf nodes: names and callers are scanned, logic is not (open questions in INTAKE.md). None runs off-host.</p>" +
                 html_table(LEG_H, LEG_R, (2,)))
    panels += pn("unknown", "<h2>What the repository does not show</h2><p class=\"note\">Listed as unknown. Nothing here is inferred.</p>" + html_table(["Area", "Unknown"], UNKNOWN))
    data = {"tables": {"tx": {"id": "tx", "head": ["Transaction", "Initial program", "Module", "Cited at"], "sets": {"all": [[e["from"], e["to"], e["module"], e["cite"]] for e in sorted(tx, key=lambda e: e["from"])]}, "modcol": 2},
                       "stores": {"id": "stores", "head": SH, "sets": {"all": SR}, "modcol": 1},
                       "sharedstores": {"id": "sharedstores", "head": SH, "sets": {"all": shared_rows}, "modcol": 1}}}
    js = "for(const id of Object.keys(DATA.tables)){const h=$('#h-'+id);if(h)mkTable(h,DATA.tables[id])}\n$$('select[aria-label^=module]').forEach(s=>{if(s.getAttribute('aria-label')!=='module tx')s.options[0].textContent='All technologies'});"
    open(os.path.join(D, "architecture-as-is.html"), "w").write(page(
        "CardDemo estate - as-is architecture", head_html("call-graph.html", "Call graph", "architecture-as-is.md"), tabs, (top, panels), data, js))


write_call_graph_md()
write_call_graph_html()
write_arch_md()
write_arch_html()
print({f: os.path.getsize(os.path.join(D, f)) for f in ("call-graph.md", "call-graph.html", "architecture-as-is.md", "architecture-as-is.html")})
