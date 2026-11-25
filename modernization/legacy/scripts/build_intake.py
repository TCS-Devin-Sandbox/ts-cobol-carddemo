#!/usr/bin/env python3
"""Writes modernization/INTAKE.md (Parts A and C) and modernization/legacy/fan-out.md (Part B5)
from the mftb store, graph.json and the recorded parity log. Run from the repo root."""
import collections, json, os, re, subprocess

g = json.load(open("modernization/legacy/graph.json"))
cat = json.load(open("store/catalog.json"))
mods = json.load(open("store/modules.json"))
est = json.load(open("store/estate.json"))
SHA, DATE = g["meta"]["baseline_sha"], g["meta"]["baseline_date"]
E = g["edges"]
PLUGIN_VERSION = cat["meta"]["tool"]


def table(head, body):
    return "\n".join(["| " + " | ".join(head) + " |", "|" + "---|" * len(head)] +
                     ["| " + " | ".join(str(c).replace("|", "\\|").replace("\n", " ") for c in r) + " |" for r in body])


def src(cite):
    path, _, line = cite.partition(":")
    line = int(line.split("-")[0])
    lines = open(path, errors="replace").read().split("\n")
    text = lines[line - 1]
    text, nxt = text[:72].rstrip() if path.lower().endswith((".jcl", ".prc")) else text, line
    while path.lower().endswith((".jcl", ".prc")) and text.endswith(",") and nxt < line + 4:   # JCL continuation
        text += " " + lines[nxt][:72].replace("//", "", 1).strip()
        nxt += 1
    return text


def clean(l):
    l = re.sub(r"^\d{6}", "      ", l)
    l = (l if len(l) > 80 else l[:72]).strip()
    return l


# ---------------------------------------------------------------- fan-out.md
RUN_ID = "wfr-b31f74d2275e4670b06c7d1fba25d560"
CHILD = {  # returned by the workflow run (structured output of each agent)
    "AUTH": dict(session="devin-d79412d470dd4315ab579df8b1453ba3", pr=8, branch="demo/01-intake-AUTH", programs=8, edges=24, open_asks=61, attempts=1),
    "CARDDEMO": dict(session="devin-bc23b96d5e244b1089f0830359ae0bf6", pr=9, branch="demo/01-intake-CARDDEMO", programs=32, edges=229, open_asks=296, attempts=1),
    "TRANTYPE": dict(session="devin-e164253d885e412bae49c45d07392145", pr=7, branch="demo/01-intake-TRANTYPE", programs=4, edges=22, open_asks=43, attempts=1),
    "VSAMMQ": dict(session="devin-47325dc420e74b0a977d69d0ed2af6c0", pr=6, branch="demo/01-intake-VSAMMQ", programs=2, edges=18, open_asks=34, attempts=1),
}
PRURL = "https://github.com/TCS-Devin-Sandbox/ts-cobol-carddemo/pull/%d"
SESS = "https://tcs-demo.devinenterprise.com/sessions/%s"


def store_counts(m):
    p = json.load(open("store/%s/programs.json" % m))
    t = json.load(open("store/%s/traces.json" % m))
    f = json.load(open("store/%s/findings.json" % m))["findings"]
    return sum(1 for x in p["programs"] if x["kind"] == "program"), t["summary"]["edges"], sum(1 for x in f if x["status"] == "open"), len(f)


def git(*a):
    return subprocess.run(["git", *a], capture_output=True, text=True).stdout.strip()


def write_fanout():
    o = ["# Step 1 fan-out: one agent per module", "",
         "Baseline `%s` (%s). Tool: %s (plugin `mftb`, entry point `estate-analysis`)." % (SHA, DATE, PLUGIN_VERSION), "",
         "- Workflow run id: `%s`" % RUN_ID,
         "- Script: [fan-out-workflow.py](fan-out-workflow.py), started with `run_workflow` (workflow name `step1-module-fan-out`).",
         "- Module ids read from `store/modules.json` on `demo/01-intake`: %s." % ", ".join(sorted(CHILD)),
         "- Concurrency limit: 4. Retry policy: one retry per failed agent, then the module is reported as not analyzed.",
         "- Result: %d of %d modules analyzed, all on the first attempt; no retry was needed." % (len(CHILD), len(mods["modules"])),
         "- **Modules not analyzed: %s.**" % (", ".join(g["meta"]["not_analyzed"]) or "none"), "",
         "## Per module", "",
         "Counts returned by the child are what its agent reported. Counts in the combined store are recomputed from `store/<MODULE>/` on `demo/01-intake` (programs.json, traces.json, open rows of findings.json).", ""]
    body = []
    diffs = []
    for m in sorted(CHILD):
        c = CHILD[m]
        sp, se, so, sall = store_counts(m)
        tip = git("rev-parse", "--short", "origin/" + c["branch"])
        body.append([m, "[%s](%s)" % (c["session"], SESS % c["session"][6:]), "`%s` (`%s`)" % (c["branch"], tip), "[#%d](%s) (open, base `demo/01-intake`)" % (c["pr"], PRURL % c["pr"]),
                     c["programs"], c["edges"], c["open_asks"], "%d / %d / %d" % (sp, se, so), "analyzed (attempt %d)" % c["attempts"]])
        if (sp, se, so) != (c["programs"], c["edges"], c["open_asks"]):
            diffs.append("%s: child returned programs/edges/open asks %d/%d/%d; the combined store has %d/%d/%d." % (m, c["programs"], c["edges"], c["open_asks"], sp, se, so))
    o += [table(["Module", "Child session", "Branch (tip)", "PR", "Programs (returned)", "Edges (returned)", "Open asks (returned)", "Programs / edges / open asks in combined store", "Result"], body), ""]
    o += ["### Differences between returned counts and the store", ""] + (["- " + d + " The store value is the one used in INTAKE.md and the call graph; the difference is reported, not corrected." for d in diffs] or ["None."]) + [""]
    o += ["## What each child wrote", "",
          table(["Module", "Store files", "Report pages", "Paths outside its two directories"],
                [[m, len(os.listdir("store/" + m)), sum(len([f for f in fs if f.endswith(".html")]) for _, _, fs in os.walk("store/reports/" + m)), "none"] for m in sorted(CHILD)]), "",
          "Checked with `git diff --name-only origin/demo/01-intake...origin/demo/01-intake-<MODULE>`: every changed path is under `store/<MODULE>/` or `store/reports/<MODULE>/`. No child changed `store/catalog.json`, `store/modules.json`, `app/` or `fixtures/`.", "",
          "## How the branches were combined", "",
          "The parent copied each child's two directories onto `demo/01-intake` with `git checkout origin/demo/01-intake-<MODULE> -- store/<MODULE> store/reports/<MODULE>` and committed once. "
          "No merge commit was made and no pull request was merged, so the four child PRs stay open with base `demo/01-intake`. "
          "`mftb modules --check` still passes after the combine, and `mftb report-estate` then wrote `store/estate.json` and `store/reports/estate.html` (%d modules, %d scanned, %d connections between modules, %d open asks)." % (
              est["summary"]["modules"], est["summary"]["scanned_modules"], len(est["connections"]), est["summary"]["asks"]), "",
          "## Notes", "",
          "- The parent did none of the children's analysis. The parent-only work is the cross-module join in [call-graph.md](call-graph.md).",
          "- Children closed some scanner findings with their own cited judgment (annotations, chain notes, online notes in `store/<MODULE>/`); closed findings are not counted as open asks above.", ""]
    open("modernization/legacy/fan-out.md", "w").write("\n".join(o))


# ---------------------------------------------------------------- INTAKE.md
def find_edge(**kw):
    for e in E:
        if all(e.get(k) == v for k, v in kw.items()): return e
    raise KeyError(kw)


def cross_checks():
    """(scanner result, store file it comes from, cite, regex the source line must match)"""
    L = lambda m, f: json.load(open("store/%s/%s.json" % (m, f)))
    ce = {e["path"]: e for e in cat["entries"]}
    out = []
    e = ce["app/app-transaction-type-db2/cbl/COBTUPDT.cbl"]
    out.append(("Catalogue: `COBTUPDT.cbl` is a COBOL program, PROGRAM-ID COBTUPDT", "store/catalog.json", e["evidence"][0], r"PROGRAM-ID\.?\s+COBTUPDT"))
    a = next(a for a in cat["asks"] if a["kind"] == "catalog-no-program-id" and "COACTUPC" in a["evidence"][0])
    out.append(("Catalogue ask: `COACTUPC.cbl` has an IDENTIFICATION DIVISION but no readable PROGRAM-ID", "store/catalog.json (asks)", a["evidence"][0], r"IDENTIFICATION DIVISION"))
    for frm, to in (("CBACT01C", "COBDATFT"), ("COBSWAIT", "MVSWAIT"), ("CBFEEDRV", "CBFEECAL"), ("CBSTM03A", "CBSTM03B")):
        x = find_edge(cat="program", **{"from": frm, "to": to})
        out.append(("Program edge: %s CALL %s (%s)" % (frm, to, x["to_class"]), "store/%s/traces.json" % x["module"], x["cite"], r"CALL\s+'%s'" % to))
    x = find_edge(cat="program", **{"from": "COSGN00C", "to": "COADM01C", "certainty": "PROVEN"})
    out.append(("Program edge: COSGN00C CICS XCTL COADM01C, static", "store/CARDDEMO/traces.json", x["cite"], r"(XCTL|PROGRAM\s*\(\s*'COADM01C')"))
    x = find_edge(cat="job", **{"from": "POSTTRAN", "to": "CBTRN02C"})
    out.append(("Job step: POSTTRAN %s EXEC PGM=CBTRN02C" % x["step"], "store/CARDDEMO/jobs.json", x["cite"], r"EXEC\s+PGM=CBTRN02C"))
    x = find_edge(cat="job", **{"from": "MNTTRDB2", "to": "COBTUPDT"})
    out.append(("Job step: MNTTRDB2 %s runs IKJEFT01, unwrapped to COBTUPDT" % x["step"], "store/TRANTYPE/jobs.json", x["cite"], r"EXEC\s+PGM=IKJEFT01"))
    x = next(e for e in E if e["cat"] == "data" and e["from"] == "CBTRN02C" and e.get("dd") == "DALYREJS")
    out.append(("File: CBTRN02C SELECT DALYREJS-FILE ASSIGN TO DALYREJS, opened for output", "store/CARDDEMO/connections.json", x["cite"], r"SELECT\s+DALYREJS-FILE"))
    out.append(("DD binding: POSTTRAN DD DALYREJS is GDG generation `%s`" % x["to"], "store/CARDDEMO/connections.json", x["cite2"], r"DALYREJS.*\(\+1\)"))
    d = next(d for d in L("CARDDEMO", "connections")["datasets"] if d["dsn"] == "AWS.M2.CARDDEMO.CARDDATA.VSAM.KSDS")
    out.append(("Dataset: `%s` is a VSAM KSDS defined by job %s (keys %s)" % (d["dsn"], d["definition"]["job"], d["definition"]["keys"]), "store/CARDDEMO/connections.json", d["definition"]["source"], r"(DEFINE\s+CLUSTER|CARDDATA\.VSAM\.KSDS)"))
    x = find_edge(cat="transaction", **{"from": "CA00"})
    out.append(("Transaction: CA00 starts program COADM01C", "store/CARDDEMO/online.json", x["cite"], r"TRANSACTION\(CA00\)"))
    x = next(e for e in E if e["cat"] == "data" and e["from"] == "COACTUPC" and e.get("dd") == "ACCTDAT" and e["direction"] == "updates")
    out.append(("CICS file: COACTUPC updates ACCTDAT (REWRITE)", "store/CARDDEMO/connections.json", x["cite"], r"(REWRITE|EXEC\s+CICS)"))
    out.append(("CSD: file ACCTDAT is dataset `%s`" % x["to"], "store/CARDDEMO/connections.json", x["cite2"], r"FILE\(ACCTDAT\)"))
    x = next(e for e in E if e["cat"] == "data" and e["from"] == "COBTUPDT" and e["direction"] == "writes")
    out.append(("Db2: COBTUPDT INSERTs into `%s`" % x["to"], "store/TRANTYPE/connections.json", x["cite"], r"(EXEC\s+SQL|INSERT)"))
    x = next(e for e in E if e["cat"] == "data" and e["from"] == "CBPAUP0C" and e.get("dd") == "DLET" and "PAUTDTL1" in e["to"])
    out.append(("IMS: CBPAUP0C deletes segment PAUTDTL1 (EXEC DLI DLET)", "store/AUTH/connections.json", x["cite"], r"(EXEC\s+DLI|DLET)"))
    x = next(e for e in E if e["cat"] == "data" and e["from"] == "COPAUA0C" and e["kind"] == "MQ MQGET")
    out.append(("MQ: COPAUA0C calls MQGET", "store/AUTH/connections.json", x["cite"], r"CALL\s+'MQGET'"))
    x = next(e for e in E if e["cat"] == "schedule" and e["to"] == "CBPAUP0J" and e["kind"] == "orders job")
    out.append(("Scheduler: CA 7 table CARDDEMO orders job CBPAUP0J", "store/CARDDEMO/schedule.json", x["cite"], r"CBPAUP0J"))
    x = next(e for e in E if e["cat"] == "copybook" and e["from"] == "COPAUA0C" and e["to"] == "CVACT03Y")
    out.append(("Copybook: COPAUA0C (AUTH) copies base copybook CVACT03Y", "store/AUTH/programs.json", x["cite"], r"COPY\s+CVACT03Y"))
    s = next(s for s in L("TRANTYPE", "dyncalls")["sites"] if s["program"] == "CBTRNDYN")
    out.append(("Dynamic call: CBTRNDYN CALL WS-PGM-NAME (operand is a data item)", "store/TRANTYPE/dyncalls.json", s["source"], r"CALL\s+WS-PGM-NAME"))
    for u in g["unreferenced"]:     # scanner says "copied by no program"; a COPY of the quoted name in the source contradicts it
        if u["kind"] == "cobol-copybook" and u.get("grep") and re.search(r"COPY\s+'%s'" % u["member"], src(u["grep"])):
            out.append(("Scanner: copybook `%s` is copied by no program (no COPY recorded in any `programs.json`)" % u["member"], "store/CARDDEMO/programs.json", u["grep"], r"^(?!.*COPY\s+'%s')" % u["member"]))
    rows = []
    for i, (claim, store, cite, rx) in enumerate(out, 1):
        line = src(cite)
        ok = bool(re.search(rx, clean(line), re.I))
        rows.append([i, claim, "`%s`" % store, "`%s`" % cite, "`%s`" % clean(line).replace("`", "'"), "agrees" if ok else "DIFFERS"])
    return rows


CATEGORY = {  # catalogue kind -> intake category
    "cobol-program": "COBOL programs", "cobol-copybook": "COBOL copybooks (incl. BMS symbolic maps and DCLGEN)", "bms-mapset": "BMS mapsets", "cics-csd": "CICS resource definitions (CSD)",
    "jcl-job": "JCL jobs", "jcl-proc": "JCL procedures", "control-cards": "Control cards", "scheduler-table": "Scheduler exports", "sql-ddl": "Db2 DDL", "sql-seed": "Db2 seed data",
    "sql-script": "SQL / catalogue listings", "ims-dbd": "IMS DBD", "ims-psb": "IMS PSB", "assembler-program": "HLASM programs", "assembler-macro": "HLASM macros",
    "assembler-dsect": "HLASM DSECT", "unsupported-source": "PL/I source", "document": "Documents", "binary": "Binary files (EBCDIC data, images)", "data": "Data files (ASCII)",
    "empty": "Empty files", "unclassified": "Unclassified by the scanner", "tooling": "Tooling"}


def write_intake():
    parity = open("modernization/evidence/parity-baseline.log").read().split("\n")
    i0 = next(i for i, l in enumerate(parity) if "tools/build.sh" in l)
    base_ok = [l.split(":")[0] for l in parity if l.endswith(": compiled") and os.path.exists("app/cbl/%s.cbl" % l.split(":")[0])]
    kinds = collections.defaultdict(list)
    for e in cat["entries"]:
        kinds[e["kind"]].append(e["path"])
    dirs = lambda ps: ", ".join("`%s/` %d" % (d, n) for d, n in sorted(collections.Counter(os.path.dirname(p) for p in ps).items()))
    o = ["# Intake: CardDemo legacy estate", "",
         "Step 1 of the modernization lab. This file records what the repository supplies, what it does not, and what runs today. Nothing here is inferred beyond the cited sources.", "",
         "## Baseline", "",
         table(["Item", "Value"], [
             ["Repository", "`TCS-Devin-Sandbox/ts-cobol-carddemo`"], ["Baseline branch", "`main`"], ["Baseline SHA", "`%s`" % SHA], ["Baseline date", DATE],
             ["How the baseline got there", "PR [#5](https://github.com/TCS-Devin-Sandbox/ts-cobol-carddemo/pull/5) (merged by a person) added the legacy estate harness and five estate members from `Cognition-Partner-Workshops/ts-cobol-carddemo` at `a5da4ee6bb4261392ac0a44a0756b82914b97fe8`"],
             ["Working branch", "`demo/01-intake`, created from the baseline SHA"], ["Estate root analyzed", "`app` (the only root)"],
             ["Scanner", "%s, plugin `mftb`, entry point `estate-analysis`; store in `./store`" % PLUGIN_VERSION],
             ["Rules followed", "`AGENTS.md` read first; nothing under `app/` or `fixtures/` was edited"]]), "",
         "## What the estate supplies, by category", "",
         "Counts are from `store/catalog.json` (%d files under `app/`, %d excluded). The catalogue types each file by its content." % (cat["summary"]["files"] if "files" in cat["summary"] else len(cat["entries"]), len(cat["excluded"])), "",
         table(["Category", "Files", "Where", "Status"], [[CATEGORY.get(k, k), len(v), dirs(v), "supplied"] for k, v in sorted(kinds.items(), key=lambda kv: CATEGORY.get(kv[0], kv[0]))]), "",
         "Module map (`store/modules.json`, frozen; `mftb modules --check` passes: %d placeable members, %d shared, %d unplaced):" % (est["summary"]["placeable"], est["summary"]["shared"], est["summary"]["unplaced"]), "",
         table(["Module", "Name", "Members", "Evidence"], [[m["id"], m["name"], len(m["members"]), ", ".join("`%s`" % x for x in m["evidence"])] for m in mods["modules"]]), "",
         "Shared copybooks (base layouts that sub-application programs also copy): %s." % ", ".join("`%s`" % s["name"] for s in mods["shared"]), "",
         "Outside `app/` the repository also supplies the off-host harness: `Makefile`, `Dockerfile`, `docker-compose.yml`, `tools/` (build, JCL runner, parity, chain-graph generator, stubs), `fixtures/` (golden cases for four chains), `db/postgres/` and `docs/`. "
         "It also carries modernized transaction-type services (`carddemo-trantype-api`, `carddemo-trantype-ui`, `modernized-trantype`, `db/migration`) that were on `main` before this step; they are not part of the legacy estate and were not analyzed.", "",
         "## What is absent or cannot run off-host", "",
         "Each row was checked against the source, not only against the documents.", "",
         table(["Category", "Status", "Evidence in the source", "What the documents say"], [
             ["CICS-supplied copybooks `DFHAID`, `DFHBMSCA`", "**MISSING**", "No file of either name anywhere in the repository (`find`); %d online programs copy them, e.g. `app/cbl/COSGN00C.cbl:57`, `:58`" % sum(1 for cb in g["copybooks"] if cb["name"] == "DFHAID" for _ in cb["programs"]), "`docs/BASELINE.md`: programs fail with `DFHBMSCA: No such file or directory`. Agrees."],
             ["CICS region, BMS macro assembler", "**MISSING** (cannot run off-host)", "No CICS runtime in `Dockerfile` or `tools/`; `tools/build.sh` compiles batch programs only", "`docs/BASELINE.md`, `AGENTS.md`: CICS programs are analysis-only. Agrees."],
             ["Symbolic maps for the base BMS mapsets", "supplied in `app/cpy-bms/` (17 members) but not used by the build", "`tools/build.sh:12` copies only `app/cpy/*.cpy` into the compile path", "`docs/BASELINE.md` says the symbolic maps are missing. **Differs:** they are in the repository; the build does not put them on the copy path."],
             ["HLASM assembler", "**MISSING** (cannot run off-host)", "`app/asm/COBDATFT.asm`, `app/asm/MVSWAIT.asm` are source only; callers at `app/cbl/CBACT01C.cbl:231`, `app/cbl/COBSWAIT.cbl:38`", "`docs/LEGACY-LANGUAGES.md`: analysis-only. Agrees."],
             ["PL/I compiler", "**MISSING** (cannot run off-host)", "`app/pli/CBFEECAL.pli` is source only; caller at `app/cbl/CBFEEDRV.cbl:101`; job `app/jcl/FEECALC.jcl:24`", "`docs/LEGACY-LANGUAGES.md`: analysis-only, `CBFEEDRV` compiles but cannot run. Agrees."],
             ["Copybook declaring `EXPORT-SEQUENCE-NUM`", "**MISSING**", "Referenced at `app/cbl/CBEXPORT.cbl:68` and `app/cbl/CBIMPORT.cbl:40`; declared in no member under `app/` (`grep`)", "`docs/BASELINE.md`: CBEXPORT and CBIMPORT do not compile. Agrees; the build log below shows both skipped."],
             ["Db2 for z/OS", "**MISSING**; replaced by PostgreSQL + Open COBOL ESQL for two batch programs", "`docker-compose.yml` services `db` and `estate`; `tools/esql_prep.py`", "`docs/BASELINE.md`. Agrees."],
             ["IMS (DB/DC), DL/I", "**MISSING** (cannot run off-host)", "Source only: 4 DBD, 4 PSB, 8 programs under `app/app-authorization-ims-db2-mq/`; no IMS stand-in in `tools/`", "Not covered by `docs/BASELINE.md` (the document lists no AUTH program)."],
             ["IBM MQ", "**MISSING** (cannot run off-host)", "MQ calls in `app/app-vsam-mq/cbl/COACCT01.cbl:352` and `app/app-authorization-ims-db2-mq/cbl/COPAUA0C.cbl:400`; no queue manager or queue definitions in the repository", "Not covered by `docs/BASELINE.md`."],
             ["Scheduler runtime (CA 7, Control-M)", "**MISSING**; exports only", "`app/scheduler/CardDemo.ca7`, `app/scheduler/CardDemo.controlm`", "Not covered."],
             ["Load modules, DBRMs, bind / plan definitions, compile and link JCL", "**MISSING**", "Jobs name `AWS.M2.CARDDEMO.LOADLIB` (e.g. `app/jcl/POSTTRAN.jcl:24`) and plan `CARDDEMO` (`app/app-transaction-type-db2/jcl/MNTTRDB2.jcl`); neither is in the repository", "Not covered."],
             ["Security definitions (RACF or other), CICS SIT, region setup", "**MISSING**", "Nothing of this kind under `app/`", "Not covered."],
             ["Production data and volumes", "**MISSING**", "`app/data/ASCII` (9 files) and `app/data/EBCDIC` (13 files) are sample data; `AGENTS.md` states synthetic data only", "`AGENTS.md`. Agrees."],
             ["Natural / NatStar / Adabas", "not present", "No such artefact under `app/` (`grep -ri natstar|natural|adabas` finds none)", "`docs/LEGACY-LANGUAGES.md`: none. Agrees."]]), "",
         "### Where the documents and the source disagree", "",
         table(["Document statement", "Source", "Finding"], [
             ["`docs/BASELINE.md`: \"All 19 online programs fail\"", "17 members under `app/cbl` contain `EXEC CICS`; the document's own table names 17. `app/cbl/CO*` matches 18 files because `COBSWAIT.cbl` is a batch program", "Count differs: 17, not 19"],
             ["`docs/BASELINE.md`: DCLGEN members `DCLTRTYP`, `DCLTRTCT`", "`app/app-transaction-type-db2/dcl/` holds `DCLTRTYP.dcl` and `DCLTRCAT.dcl`", "Name differs: `DCLTRCAT`, not `DCLTRTCT`"],
             ["`docs/BASELINE.md`: CBSTM03A / CBSTM03B \"needs `COBSWAIT`\"; `docs/LEGACY-LANGUAGES.md`: COBSWAIT is \"run from `CBSTM03A/B` JCL\"", "The only reference to `COBSWAIT` is `app/jcl/WAITSTEP.jcl:22` (`EXEC PGM=COBSWAIT`); the scan finds no CALL from CBSTM03A or CBSTM03B to it, and `app/jcl/CREASTMT.JCL` does not run it", "Not supported by the source"],
             ["`docs/BASELINE.md`: CBSTM03A / CBSTM03B compile (\"yes\")", "`tools/build.sh:31` builds `app/cbl/CB*.cbl`; the two members are `CBSTM03A.CBL` and `CBSTM03B.CBL` (upper-case extension) and do not appear in the build log below", "Not proven by the build: neither member is compiled by `make build`"],
             ["`docs/BASELINE.md`: symbolic maps missing", "`app/cpy-bms/` holds 17 symbolic map copybooks", "Present in the repository; not on the build's copy path"],
             ["`docs/chain-graph.md`", "See [call-graph.md](legacy/call-graph.md#disagreements-with-docschain-graphmd)", "%d differences" % g["summary"]["disagreements"]]]), "",
         "## What runs today", "",
         "Command run on the baseline (`%s`), from the repository root: `make up && make build && make parity`. Exit code 0. Full cleaned log: [evidence/parity-baseline.log](evidence/parity-baseline.log) (terminal control codes and Docker progress lines removed, nothing else changed)." % SHA[:12], "",
         "```text", "$ make up && make build && make parity"] + parity[i0:] + ["```", "",
         "What this proves, and no more:", "",
         "- %d members of `app/cbl` compile with GnuCOBOL 3.1.2 (%s); CBEXPORT and CBIMPORT are skipped; CBSTM03A and CBSTM03B are not attempted (see above)." % (len(base_ok), ", ".join(base_ok)),
         "- 2 Db2 batch programs (CBTRNDYN, COBTUPDT) precompile with Open COBOL ESQL and compile; 4 harness stubs (PARMDRV, CEE3ABD, DSNEXIT, DSNRUN) compile. The stubs are test harness, not estate code.",
         "- Four chains run and match their golden files: `posting` (7 cases), `interest` (7), `trantype-update` (7), `trantype-dynamic` (3): 24 cases, 0 differences.",
         "- Nothing else is proven to run: no CICS program, no IMS or MQ program, no HLASM or PL/I member, and none of the other JCL jobs.", "",
         "## Scanner cross-check (Part C)", "",
         "Scanner results compared with the source line they cite. The source line is quoted as it stands at the baseline.", "",
         table(["#", "Scanner result", "From", "File and line", "Source line", "Check"], cross_checks()), "",
         "Rows marked DIFFERS: the scanner does not read `COPY 'NAME'` with the member name in quotes. `CSSTRPFY` is copied that way at `app/cbl/COACTUPC.cbl:4199`, `app/cbl/COACTVWC.cbl:913`, `app/cbl/COCRDLIC.cbl:1416`, `app/cbl/COCRDSLC.cbl:855` and `app/cbl/COCRDUPC.cbl:1528`, and `CSUTLDWY` at `app/cbl/COACTUPC.cbl:166`. These COPY edges are therefore missing from the scan results and from the copybook table of the call graph; the call graph lists both members as unreferenced with the text-search hit beside them. Nothing was added by hand.", "",
         "Row 2 needs a note: the scanner is right that line 21 is the IDENTIFICATION DIVISION header, but its ask says the member has no readable PROGRAM-ID. In the source, `PROGRAM-ID.` is on `app/cbl/COACTUPC.cbl:22` and the name `COACTUPC.` is on line 23. The same two-line form is in the other five members with this ask. The ask stays open below; this observation is for whoever owns it.", "",
         "## Open questions (every catalogue ask)", "",
         "All %d asks of `store/catalog.json`, unchanged and unresolved. Owner is a placeholder until a person is named." % len(cat["asks"]), "",
         table(["#", "Kind", "Severity", "Evidence", "What the scanner says", "Question", "Owner", "Status"],
               [["A%02d" % i, a["kind"], a.get("severity", "-"), ", ".join("`%s`" % x for x in a["evidence"]), a["message"], a["request"], "<owner: TBD>", "open"] for i, a in enumerate(cat["asks"], 1)]), "",
         "Further open asks, also unresolved:", "",
         "- Estate level: `store/estate.json` lists %d open asks (the %d above plus site and environment questions); see `store/reports/estate.html`." % (est["summary"]["asks"], len(cat["asks"])),
         "- Module level: open findings per module are in `store/<MODULE>/findings.json` and counted in [legacy/fan-out.md](legacy/fan-out.md).",
         "- Dynamic targets: %d open asks in [legacy/call-graph.md](legacy/call-graph.md#open-asks), each with an owner placeholder." % g["summary"]["open_asks"], "",
         "## Related outputs", "",
         "- [legacy/fan-out.md](legacy/fan-out.md): the module fan-out (Part B).",
         "- [legacy/call-graph.md](legacy/call-graph.md), [legacy/call-graph.html](legacy/call-graph.html): the whole-estate call graph (Part D).",
         "- [legacy/architecture-as-is.md](legacy/architecture-as-is.md), [legacy/architecture-as-is.html](legacy/architecture-as-is.html): the as-is architecture (Part E).",
         "- `store/reports/estate.html` and `store/reports/<MODULE>/<MODULE>-report.html`: the scanner's own reports.", ""]
    open("modernization/INTAKE.md", "w").write("\n".join(o))


write_fanout()
write_intake()
print("written")
