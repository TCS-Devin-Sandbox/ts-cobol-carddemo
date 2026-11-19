#!/usr/bin/env python3
"""Whole-estate call graph from the mftb store (Step 1, Part D).

Reads store/catalog.json, store/modules.json and every store/<MODULE>/*.json
written by the module scans, joins them across modules (the edges no single
module scan could see) and writes modernization/legacy/graph.json - the data
behind call-graph.md, call-graph.html and the architecture pages.

Every edge is PROVEN (static, cited file:line) or CANDIDATE (dynamic or
inferred).  A dynamic site with no settled target is an open ask, never an edge.
Run from the repo root:  python3 modernization/legacy/scripts/build_graph.py
"""
import collections, json, os, re, subprocess, sys

STORE = os.environ.get("MFTB_STORE", "store")
OUT = "modernization/legacy/graph.json"
P, C = "PROVEN", "CANDIDATE"


def load(*parts):
    path = os.path.join(STORE, *parts)
    return json.load(open(path)) if os.path.exists(path) else None


def git(*a):
    return subprocess.run(["git", *a], capture_output=True, text=True).stdout.strip()


cat = load("catalog.json")
mods = load("modules.json")
MODULES = [m["id"] for m in mods["modules"]]
S = {m: {f[:-5]: load(m, f) for f in os.listdir(os.path.join(STORE, m)) if f.endswith(".json")}
     for m in MODULES if os.path.isdir(os.path.join(STORE, m))}
ANALYZED = sorted(S)
NOT_ANALYZED = [m for m in MODULES if m not in S]

path_mod = {}
for m in mods["modules"]:
    for x in m["members"]:
        path_mod[x["path"]] = m["id"]
for s in mods["shared"]:
    path_mod[s["path"]] = "shared"
by_path = {e["path"]: e for e in cat["entries"]}

# ---- nodes: programs of every module, plus HLASM / PL/I leaves from the catalog
programs = {}
for m in ANALYZED:
    for p in S[m]["programs"]["programs"]:
        if p["kind"] == "program":
            programs[p["id"]] = {"id": p["id"], "module": m, "path": p["path"], "lang": "COBOL",
                                 "cics": False, "sql": bool(p.get("uses_sql"))}
LEAF_KIND = {"assembler-program": "HLASM", "unsupported-source": "PL/I"}
leaves = {}
for e in cat["entries"]:
    if e["kind"] in LEAF_KIND:
        leaves[e["member"].upper()] = {"id": e["member"].upper(), "module": path_mod.get(e["path"], "?"),
                                       "path": e["path"], "lang": LEAF_KIND[e["kind"]],
                                       "cite": (e.get("evidence") or [e["path"]])[0]}


def mod_of_program(name):
    if name in programs: return programs[name]["module"]
    if name in leaves: return leaves[name]["module"]
    return None


def node_class(name, m):
    if name in programs: return "program"
    if name in leaves: return "leaf-" + leaves[name]["lang"]
    for n in S[m]["traces"]["nodes"]:
        if n["id"] == name and n["kind"] == "boundary":
            return {"runtime-routine": "runtime", "utility": "utility", "launcher": "launcher"}.get(n["class"], "not-in-estate")
    return "not-in-estate"


edges = []      # every edge of every category
asks = []       # dynamic sites with no settled target, and other open items


def edge(cat_, frm, to, kind, cert, cite, **kw):
    e = {"cat": cat_, "from": frm, "to": to, "kind": kind, "certainty": cert, "cite": cite}
    e.update(kw)
    edges.append(e)
    return e


# ---- 1. program to program
KIND_LABEL = {"static-call": "CALL", "dynamic-call": "CALL (dynamic)", "cics-xctl": "CICS XCTL", "cics-link": "CICS LINK"}
seen = set()
for m in ANALYZED:
    for e in S[m]["traces"]["edges"]:
        if e["kind"] in ("jcl-exec", "schedule-job"):
            continue
        frm, to = e["from"], e["to"]
        programs.get(frm, {}).update(cics=programs.get(frm, {}).get("cics") or e["kind"].startswith("cics"))
        if to.startswith("?"):
            asks.append({"kind": "dynamic-target", "module": m, "program": frm, "construct": KIND_LABEL.get(e["kind"], e["kind"]),
                         "variable": e.get("variable") or to[1:], "cite": e["source"], "detail": e.get("detail", ""),
                         "question": "Which program(s) can %s hold when %s runs this %s?" % (e.get("variable") or to[1:], frm, KIND_LABEL.get(e["kind"], e["kind"]))})
            continue
        cls = node_class(to, m)
        tm = mod_of_program(to)
        cert = P if e["certainty"] == "proven" else C
        key = (frm, to, e["kind"], e["source"], cert)
        if key in seen: continue
        seen.add(key)
        edge("program", frm, to, KIND_LABEL.get(e["kind"], e["kind"]), cert, e["source"],
             module=m, to_module=tm, to_class=cls, cross=bool(tm and tm != m),
             basis=("" if cert == P else "dynamic operand %s; value cited at %s" % (e.get("variable"), ", ".join(e.get("evidence") or []) or "n/a")),
             detail=e.get("detail", ""))

# dynamic CALL whose operand is fed by a DSN RUN ... PARMS('X') card: the JCL names the value (inferred, cited)
for m in ANALYZED:
    for jb in S[m]["jobs"]["jobs"]:
        for st in jb["steps"]:
            for dd in st.get("dds") or []:
                txt = dd.get("inline_text") or ""
                for i, line in enumerate(txt.split("\n")):
                    mm = re.search(r"RUN\s+PROGRAM\((\w+)\).*PARMS\('(\w+)'\)", line)
                    if mm and mm.group(1) in programs and (mm.group(2) in programs or mm.group(2) in leaves):
                        src = "%s:%d" % (jb["path"], dd["line"] + 1 + i)
                        has_dyn = any(a["program"] == mm.group(1) and a["construct"].startswith("CALL") for a in asks)
                        if has_dyn:
                            edge("program", mm.group(1), mm.group(2), "CALL (dynamic)", C, src, module=m,
                                 to_module=mod_of_program(mm.group(2)), to_class="program",
                                 cross=mod_of_program(mm.group(2)) != m,
                                 basis="inferred by the parent: job %s passes PARMS('%s') to %s, which has a dynamic CALL; the scan itself leaves the target open" % (jb["name"], mm.group(2), mm.group(1)),
                                 detail="")

# ---- 2. job to step to program
jobs = {}
for m in ANALYZED:
    for jb in S[m]["jobs"]["jobs"]:
        jobs[jb["name"]] = {"name": jb["name"], "module": m, "path": jb["path"], "steps": len(jb["steps"])}
        for st in jb["steps"]:
            run = st.get("program") or st["pgm"]
            cls = "program" if run in programs else ("leaf" if run in leaves else st["kind"])
            edge("job", jb["name"], run, "EXEC", P, st["source"], module=m, step=st["step"], pgm=st["pgm"],
                 proc=st.get("proc"), proc_step=st.get("proc_step"), to_class=cls, to_module=mod_of_program(run),
                 cross=bool(mod_of_program(run) and mod_of_program(run) != m),
                 via=("%s -> %s (%s)" % (st["pgm"], run, st.get("resolution")) if st.get("program") and st["pgm"] != run else ""))
    for sub in (S[m]["connections"]["cics"].get("submits") or []):
        for ex in sub.get("executes") or []:
            edge("job", "%s (submitted by %s)" % (sub.get("job"), sub["program"]), ex["name"], "CICS submit", C, sub["source"],
                 module=m, step=ex.get("step"), pgm=ex["name"], proc=ex["name"] if ex.get("kind") == "proc" else None,
                 to_class=ex.get("kind"), to_module=None, cross=False,
                 via="JCL cards written to TD queue %s (internal reader); scanner status: %s" % (sub["queue"], sub.get("status")))

# ---- 3. program to dataset / table / queue / map
def file_dir(f):
    ops, modes = set(f.get("ops") or {}), set(f.get("open_modes") or [])
    d = set()
    if ops & {"REWRITE", "DELETE"}: d.add("updates")
    if ops & {"WRITE"} or modes & {"OUTPUT", "EXTEND"}: d.add("writes")
    if ops & {"READ", "START"} or modes & {"INPUT", "I-O"}: d.add("reads")
    return sorted(d) or ["reads"] if not d else sorted(d)


csd_files = {}      # CICS file name -> (dsn, cite, module) from whichever module's CSD defines it
for m in ANALYZED:
    for f in S[m]["connections"]["cics"]["files"]:
        if f.get("csd"):
            csd_files[f["name"]] = (f["csd"]["attrs"].get("DSNAME"), f["csd"]["source"], m)

CICS_DIR = {"READ": "reads", "STARTBR": "reads", "READNEXT": "reads", "READPREV": "reads", "ENDBR": None,
            "WRITE": "writes", "REWRITE": "updates", "DELETE": "updates", "WRITEQ TD": "writes", "WRITEQ TS": "writes",
            "READQ TS": "reads", "READQ TD": "reads", "DELETEQ TS": "updates",
            "SEND MAP": "writes", "RECEIVE MAP": "reads", "SEND": "writes", "RECEIVE": "reads"}
for m in ANALYZED:
    cn = S[m]["connections"]
    for f in cn["files"]:
        for d in file_dir(f):
            if f.get("bindings"):
                for b in f["bindings"]:
                    edge("data", f["program"], b.get("dsn") or "(DD %s: no DSN)" % f["dd"], "batch file", P, f["source"], module=m,
                         store_type="dataset", direction=d, dd=f["dd"], job=b.get("job"), step=b.get("step"), cite2=b.get("source"),
                         org=f.get("organization"))
            else:
                edge("data", f["program"], "(DD %s: not allocated by any scanned JCL)" % f["dd"], "batch file", P, f["source"], module=m,
                     store_type="dataset", direction=d, dd=f["dd"], job=None, step=None, cite2=None, org=f.get("organization"))
    for kind, lst, st in (("CICS file", cn["cics"]["files"], "dataset"), ("CICS TD queue", cn["cics"].get("td_queues") or [], "queue"),
                          ("CICS TS queue", cn["cics"].get("ts_queues") or [], "queue")):
        for f in lst:
            first = {}
            for u in f["uses"]:
                d = CICS_DIR.get(u["command"])
                if d: first.setdefault((u["program"], d, u.get("resolution")), u)
            for (prog, d, res), u in sorted(first.items()):
                dsn, csrc, cm = csd_files.get(f["name"], (None, None, None)) if kind == "CICS file" else (None, None, None)
                static = res in ("constant", "literal")
                edge("data", prog, dsn or f["name"], kind, P if static else C, u["source"], module=m, store_type=st, direction=d,
                     dd=f["name"], cite2=csrc, csd_module=cm, cross=bool(cm and cm != m),
                     basis="" if static else "resource named through a data item (%s)" % (u.get("resolution_detail") or res))
    for mp in cn["cics"]["maps"]:
        first = {}
        for u in mp["uses"]:
            d = CICS_DIR.get(u["command"])
            if d: first.setdefault((u["program"], d, u.get("resolution"), u.get("map")), u)
        for (prog, d, res, mapname), u in sorted(first.items(), key=str):
            if not mp.get("mapset"):
                asks.append({"kind": "dynamic-map", "module": m, "program": prog, "construct": u["command"], "variable": mapname or "",
                             "cite": u["source"], "detail": u.get("resolution_detail") or "",
                             "question": "Which map(s) does %s name when it runs %s through data item %s?" % (prog, u["command"], mapname)})
                continue
            static = res in ("constant", "literal")
            edge("data", prog, mp["mapset"], "BMS map", P if static else C, u["source"], module=m, store_type="map", direction=d,
                 dd=mapname, cite2=(mp.get("csd") or {}).get("source"),
                 basis="" if static else "mapset named through a data item (%s)" % (u.get("resolution_detail") or res))
    for st_ in cn["db2"]["statements"]:
        verb = st_["verb"]
        d = {"SELECT": "reads", "FETCH": "reads", "OPEN": "reads", "DECLARE": None, "INSERT": "writes", "UPDATE": "updates", "DELETE": "updates"}.get(verb)
        if st_.get("dynamic") or verb in ("PREPARE", "EXECUTE"):
            asks.append({"kind": "dynamic-sql", "module": m, "program": st_["program"], "construct": "EXEC SQL " + verb, "variable": ",".join(st_.get("host_vars") or []),
                         "cite": st_["source"], "detail": "statement text is run-time data",
                         "question": "Which tables can the dynamic statement of %s touch? The text is not in the source." % st_["program"]})
            continue
        if not d: continue
        for t in st_.get("tables") or []:
            edge("data", st_["program"], t, "Db2 table", P, st_["source"], module=m, store_type="table", direction=d, dd=verb, cite2=None)
    for ip in cn["ims"]["programs"]:
        for c in ip.get("commands") or []:
            if not c.get("segment"): continue
            d = {"GU": "reads", "GN": "reads", "GNP": "reads", "GHU": "reads", "GHN": "reads", "GHNP": "reads",
                 "ISRT": "writes", "REPL": "updates", "DLET": "updates"}.get(c["command"])
            if d: edge("data", ip["program"], "IMS segment " + c["segment"], "IMS (EXEC DLI)", P, c["source"], module=m,
                       store_type="ims", direction=d, dd=c["command"], cite2=None)
        for c in ip.get("calls") or []:
            fn = (c.get("function") or (c.get("function_operand") or "").replace("FUNC-", "")).upper()
            d = {"GU": "reads", "GN": "reads", "GNP": "reads", "GHU": "reads", "GHN": "reads", "GHNP": "reads",
                 "ISRT": "writes", "REPL": "updates", "DLET": "updates"}.get(fn)
            if d: edge("data", ip["program"], "IMS PCB " + (c.get("pcb") or "?"), "IMS (CALL %s)" % c["routine"], C, c["source"], module=m,
                       store_type="ims", direction=d, dd=fn, cite2=None,
                       basis="DL/I function read from the operand name %s, not from its value" % c.get("function_operand"))
    for q in cn["external"].get("mq") or []:
        d = {"MQGET": "reads", "MQPUT": "writes", "MQPUT1": "writes"}.get(q["api"])
        if d: edge("data", q["program"], "MQ queue (name is run-time data)", "MQ " + q["api"], P, q["source"], module=m,
                   store_type="queue", direction=d, dd=q["api"], cite2=None,
                   basis="the call is static; the queue name is not a scan result (open ask)")
mq_progs = sorted({e["from"] for e in edges if e["kind"].startswith("MQ ")})
for p_ in mq_progs:
    asks.append({"kind": "mq-queue-name", "module": programs[p_]["module"], "program": p_, "construct": "MQOPEN/MQGET/MQPUT", "variable": "",
                 "cite": next(e["cite"] for e in edges if e["from"] == p_ and e["kind"].startswith("MQ ")), "detail": "",
                 "question": "Which queue names does %s open? The scan records the MQ calls, not the queue names." % p_})

# ---- 4. copybook sharing
cb_decl = {}
for e in cat["entries"]:
    if e["kind"] == "cobol-copybook":
        cb_decl.setdefault(e["member"].upper(), e["path"])
copy_use = collections.defaultdict(list)
for m in ANALYZED:
    for p in S[m]["programs"]["programs"]:
        if p["kind"] != "program": continue
        for inc in p.get("includes") or []:
            copy_use[inc["name"].upper()].append({"program": p["id"], "module": m, "cite": "%s:%s" % (p["path"], inc["line"])})
copybooks = []
for name, uses in sorted(copy_use.items()):
    uses = sorted({u["program"]: u for u in uses}.values(), key=lambda u: u["program"])
    path = cb_decl.get(name)
    copybooks.append({"name": name, "path": path, "owner": path_mod.get(path, "not in estate") if path else "not in estate",
                      "programs": uses, "modules": sorted({u["module"] for u in uses})})
    for u in uses:
        edge("copybook", u["program"], name, "COPY", P, u["cite"], module=u["module"], path=path,
             cross=len({x["module"] for x in uses}) > 1)

# ---- 5. transaction id to initial program
for m in ANALYZED:
    on = S[m].get("online")
    if not on: continue
    for t in on["transactions"]:
        src = (t.get("definition") or {}).get("source")
        prog = t.get("initial_program")
        if prog in programs: programs[prog]["cics"] = True
        edge("transaction", t["transid"], prog or "(no program named)", "CSD DEFINE TRANSACTION" if t["basis"] == "csd" else t["basis"],
             P if (t["basis"] == "csd" and src and prog) else C, src or "", module=m, to_module=mod_of_program(prog),
             in_estate=prog in programs, role=t.get("role"))
    for pd in (S[m]["connections"]["cics"]["csd"].get("PROGRAM") or []):
        if pd["name"] in programs: programs[pd["name"]]["cics"] = True

# ---- 6. scheduler order, and unscheduled jobs
def line_of(path, needle):
    try:
        for i, l in enumerate(open(path, errors="replace"), 1):
            if needle in l: return "%s:%d" % (path, i)
    except OSError:
        pass
    return path


sched_tables, seen_t = [], set()
scheduled = set()
for m in ANALYZED:
    sc = S[m].get("schedule")
    if not sc: continue
    order_cite = {}
    for e in S[m]["traces"]["edges"]:
        if e["kind"] == "schedule-job": order_cite[(e["from"].replace("TABLE ", ""), e["job"])] = e["source"]
    for t in sc["tables"]:
        if (t["name"], t["path"]) in seen_t: continue
        seen_t.add((t["name"], t["path"]))
        names = []
        for j in t["jobs"]:
            scheduled.add(j["name"])
            names.append(j["name"])
            jm = jobs.get(j["name"], {}).get("module")
            cite = order_cite.get((t["name"], j["name"])) or line_of(t["path"], j["name"])
            edge("schedule", "TABLE " + t["name"], j["name"], "orders job", P if jm else C, cite, module=m, to_module=jm,
                 table=t["name"], dialect=t["dialect"], cross=bool(jm and jm != "CARDDEMO"),
                 basis="" if jm else "no JCL member of this name in the estate catalogue")
        for e in t["edges"]:
            edge("schedule", e["from"], e["to"], "runs before", P, line_of(t["path"], e["condition"]), module=m, table=t["name"],
                 dialect=t["dialect"], condition=e["condition"], to_module=jobs.get(e["to"], {}).get("module"))
        sched_tables.append({"name": t["name"], "path": t["path"], "dialect": t["dialect"], "jobs": names})
    for x in sc.get("cross_table") or []:
        for fj in x["from_jobs"]:
            k = ("x", x["from_table"], fj, x["to_table"], x["to_job"])
            if k in seen_t: continue
            seen_t.add(k)
            edge("schedule", fj, x["to_job"], "runs before (across tables)", P, line_of(sc["tables"][-1]["path"], x["condition"]), module=m,
                 table="%s -> %s" % (x["from_table"], x["to_table"]), dialect="controlm-xml", condition=x["condition"],
                 to_module=jobs.get(x["to_job"], {}).get("module"))
unscheduled = sorted(j for j in jobs if j not in scheduled)

# ---- 7. uncalled programs, unreferenced members
inbound = collections.defaultdict(lambda: {P: [], C: []})
for e in edges:
    if e["cat"] in ("program", "job", "transaction") and e["to"] in set(programs) | set(leaves) and e["from"] != e["to"]:
        inbound[e["to"]][e["certainty"]].append("%s %s (%s)" % (e["kind"], e["from"], e["cite"]))
uncalled = []
for name, p in sorted({**programs, **leaves}.items()):
    if not inbound[name][P]:
        uncalled.append({"program": name, "module": p["module"], "path": p["path"], "lang": p["lang"],
                         "candidate_inbound": sorted(set(inbound[name][C]))[:6]})

referenced = set()
for e in cat["entries"]:
    for r in e["references"]:
        for d in cat["index"].get(r["name"], []):
            if d["id"] != e["id"]: referenced.add(d["id"])
used_names = set(copy_use) | {e["to"] for e in edges if e["cat"] == "data" and e["store_type"] == "map"}
used_names |= {e.get("proc") for e in edges if e["cat"] == "job" and e.get("proc")}
blob = ""
for m in ANALYZED:      # names the module scans cite: JCL cards and facts, IMS regions and PSB/DBD cross-references
    blob += json.dumps(S[m]["jobs"]["jobs"])
    ims = S[m]["connections"]["ims"]
    blob += json.dumps([ims.get("regions"), [p_.get("psbs") for p_ in ims.get("programs") or []],
                        [[pc.get("dbdname") for pc in g_.get("pcbs") or []] for g_ in ims.get("psbgen") or []]])
    for t_ in (S[m].get("db2seed") or {}).get("tables") or []:
        blob += json.dumps(t_)
words = set(re.findall(r"[A-Z0-9$#@]+", blob.upper()))
NOT_PLACEABLE = {"document", "data", "tooling", "binary", "empty", "unclassified"}
SKIP = {"cobol-program", "jcl-job", "assembler-program", "unsupported-source", "scheduler-table", "cics-csd"}
unreferenced = []
for e in sorted(cat["entries"], key=lambda x: x["path"]):
    if e["kind"] in NOT_PLACEABLE or e["kind"] in SKIP: continue
    if e["id"] in referenced or e["member"].upper() in used_names or e["member"].upper() in words: continue
    unreferenced.append({"member": e["member"], "kind": e["kind"], "path": e["path"], "module": path_mod.get(e["path"], "?"),
                         "cite": (e.get("evidence") or [e["path"]])[0]})

# ---- 8. disagreements with docs/chain-graph.md
disagreements = []
DOC = "docs/chain-graph.md"
if os.path.exists(DOC):
    sec, drows, dprogs, djobs = None, [], {}, collections.defaultdict(list)
    for n, l in enumerate(open(DOC), 1):
        if l.startswith("## "): sec = l[3:].strip()
        if not l.startswith("| [") : continue
        cells = [c.strip() for c in l.strip().strip("|").split("|")]
        name = re.match(r"\[(\w+)\]", cells[0]).group(1)
        sp = lambda c: set() if c == "-" else {x.strip() for x in c.split(",")}
        if sec == "Programs": dprogs[name] = {"line": n, "lang": cells[1], "copy": sp(cells[2]), "dd": sp(cells[3]), "static": sp(cells[4]), "dyn": sp(cells[5]), "sql": sp(cells[6])}
        if sec == "Jobs": djobs[name].append({"line": n, "step": cells[1], "program": cells[2]})
    scan_names = set(programs) | set(leaves)
    def dis(topic, subject, doc_says, scan_says, cite):
        disagreements.append({"topic": topic, "subject": subject, "doc": doc_says, "scan": scan_says, "cite": cite})
    for n_ in sorted(scan_names - set(dprogs)):
        dis("program missing from doc", n_, "not listed", "program in module %s (%s)" % (mod_of_program(n_), ({**programs, **leaves})[n_]["path"]), DOC + ":250")
    for n_ in sorted(set(dprogs) - scan_names):
        dis("program only in doc", n_, "listed", "not in any module scan", "%s:%d" % (DOC, dprogs[n_]["line"]))
    scan_p = {}
    for m in ANALYZED:
        for p in S[m]["programs"]["programs"]:
            if p["kind"] == "program":
                scan_p[p["id"]] = {"copy": {i["name"].upper() for i in p["includes"]}, "dd": {f["assign"] for f in p["files"]},
                                   "static": {c["target"] for c in p["calls"]["static"]}, "dyn": {c.get("variable") or c.get("target") or "?" for c in p["calls"]["dynamic"]}}
    sql_by = collections.defaultdict(set)
    for e in edges:
        if e["cat"] == "data" and e["store_type"] == "table": sql_by[e["from"]].add(e["to"])
    for n_ in sorted(set(dprogs) & set(scan_p)):
        d, s = dprogs[n_], scan_p[n_]
        for k, lab in (("copy", "copybooks"), ("dd", "DD names"), ("static", "static CALLs"), ("dyn", "dynamic CALLs")):
            if d[k] != s[k]:
                dis(lab, n_, "only in doc: %s" % (", ".join(sorted(d[k] - s[k])) or "-"), "only in scan: %s" % (", ".join(sorted(s[k] - d[k])) or "-"), "%s:%d" % (DOC, d["line"]))
        if d["sql"] != sql_by[n_]:
            dis("SQL tables", n_, "only in doc: %s" % (", ".join(sorted(d["sql"] - sql_by[n_])) or "-"), "only in scan: %s" % (", ".join(sorted(sql_by[n_] - d["sql"])) or "-"), "%s:%d" % (DOC, d["line"]))
    scan_steps = collections.defaultdict(set)
    for e in edges:
        if e["cat"] == "job" and e["kind"] == "EXEC":
            for sname in (e["step"], "%s.%s" % (e["step"], e.get("proc_step"))):
                scan_steps[e["from"]].add((sname, e["via"].split(" (")[0].replace(" -> ", "->") if e["via"] else e["to"]))
                scan_steps[e["from"]].add((sname, e["pgm"]) if not e["via"] else (sname, None))
    for j in sorted(set(jobs) - set(djobs)):
        dis("job missing from doc", j, "not listed", "job in module %s (%s)" % (jobs[j]["module"], jobs[j]["path"]), DOC + ":141")
    for j in sorted(set(djobs) - set(jobs)):
        dis("job only in doc", j, "listed", "not in any module scan", "%s:%d" % (DOC, djobs[j][0]["line"]))
    for j in sorted(set(djobs) & set(jobs)):
        for r in djobs[j]:
            if (r["step"], r["program"].replace(" -> ", "->")) not in scan_steps[j]:
                got = sorted(p for s_, p in scan_steps[j] if s_ == r["step"] and p)
                dis("job step", "%s.%s" % (j, r["step"]), "runs %s" % r["program"], "runs %s" % (", ".join(got) or "step not in scan"), "%s:%d" % (DOC, r["line"]))

# ---- stores (for the architecture pages)
stores = {}
def store(key, typ, **kw):
    s = stores.setdefault(key, {"name": key, "type": typ, "module": None, "definition": None, "online": {}, "batch": {}, "utility": [], "extra": {}})
    for k, v in kw.items():
        if v and not s.get(k): s[k] = v
    return s
for m in ANALYZED:
    for d in S[m]["connections"]["datasets"]:
        s = store(d["dsn"], d["type"], module=m, definition=(d.get("definition") or {}).get("source"))
        if d.get("definition"):
            s["extra"].update({k: v for k, v in d["definition"].items() if k in ("keys", "recordsize", "limit", "organization") and v})
        if d.get("alternate_indexes"): s["extra"]["alternate_indexes"] = d["alternate_indexes"]
        if d.get("paths"): s["extra"]["paths"] = d["paths"]
        tb = d.get("type_basis") or {}
        if tb.get("source") and not s["definition"]: s["extra"].setdefault("type_basis", tb["source"])
        for u in d.get("steps") or []:
            if u.get("step_kind") != "program": s["utility"].append("%s.%s %s (%s)" % (u["job"], u["step"], u["program"], u["source"]))
for e in edges:
    if e["cat"] != "data" or e["store_type"] == "map": continue
    typ = {"table": "Db2 table", "ims": "IMS", "queue": "queue"}.get(e["store_type"], "dataset")
    s = store(e["to"], typ if typ != "queue" else e["kind"].split(" MQ")[0], module=e["module"])
    layer = "online" if programs.get(e["from"], {}).get("cics") else "batch"
    s[layer].setdefault(e["direction"], [])
    if e["from"] not in [x[0] for x in s[layer][e["direction"]]]:
        s[layer][e["direction"]].append([e["from"], e["cite"], e["certainty"]])

summary = {
    "programs": len(programs), "leaves": len(leaves), "jobs": len(jobs),
    "edges_total": len(edges),
    "by_category": {c_: {P: sum(1 for e in edges if e["cat"] == c_ and e["certainty"] == P),
                         C: sum(1 for e in edges if e["cat"] == c_ and e["certainty"] == C)}
                    for c_ in ("program", "job", "data", "copybook", "transaction", "schedule")},
    "cross_module_edges": sum(1 for e in edges if e.get("cross")),
    "open_asks": len(asks), "uncalled": len(uncalled), "unreferenced": len(unreferenced),
    "unscheduled_jobs": len(unscheduled), "scheduled_jobs": len(scheduled), "disagreements": len(disagreements),
    "copybooks_shared": sum(1 for c_ in copybooks if len(c_["programs"]) > 1), "copybooks": len(copybooks),
    "stores": len(stores),
}
base = git("rev-parse", "origin/main")
graph = {"meta": {"baseline_sha": base, "baseline_date": git("log", "-1", "--format=%cI", base),
                  "repo": "TCS-Devin-Sandbox/ts-cobol-carddemo", "tool": cat["meta"]["tool"],
                  "modules": MODULES, "analyzed": ANALYZED, "not_analyzed": NOT_ANALYZED,
                  "module_names": {m["id"]: m["name"] for m in mods["modules"]}},
         "summary": summary, "programs": sorted(programs.values(), key=lambda p: p["id"]),
         "leaves": sorted(leaves.values(), key=lambda p: p["id"]), "jobs": sorted(jobs.values(), key=lambda j: j["name"]),
         "edges": edges, "asks": asks, "copybooks": copybooks, "schedule_tables": sched_tables, "unscheduled": unscheduled,
         "uncalled": uncalled, "unreferenced": unreferenced, "disagreements": disagreements,
         "stores": sorted(stores.values(), key=lambda s: (s["type"], s["name"]))}
os.makedirs(os.path.dirname(OUT), exist_ok=True)
json.dump(graph, open(OUT, "w"), indent=1, sort_keys=True)
print(json.dumps(summary, indent=1))
