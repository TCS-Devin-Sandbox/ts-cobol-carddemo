#!/usr/bin/env python3
"""Writes store/modules.json from store/catalog.json (module-classification, Part 1).

The grouping judgment is recorded here so it is reviewable: each module is a
directory the repository itself separates, corroborated by its own CSD file,
README and (where present) scheduler entries.  Run from the repo root.
"""
import json, re, subprocess, datetime, collections, pathlib

cat = json.load(open("store/catalog.json"))
PLACEABLE_SKIP = {"document", "data", "tooling", "binary", "empty", "unclassified"}
ents = {e["id"]: e for e in cat["entries"]}
index = cat["index"]

def grep1(pattern, path):
    out = subprocess.run(["grep", "-n", "-m1", "-E", pattern, path], capture_output=True, text=True).stdout
    return "%s:%s" % (path, out.split(":", 1)[0]) if out else path

MODS = {
 "CARDDEMO": dict(dir=None, name="CardDemo base application",
   what="signs users on, maintains accounts, cards, customers and users online, and posts daily transactions, calculates interest and produces statements and reports in batch over VSAM files",
   evidence=["app/cbl/", "app/jcl/", "app/csd/CARDDEMO.CSD:1", "app/scheduler/CardDemo.controlm:4", "app/scheduler/CardDemo.ca7:18"],
   reasoning="the top-level source, JCL, copybook, BMS, PROC and CSD directories of the repository; its own CSD file defines its programs and transactions and both scheduler exports run its jobs"),
 "AUTH": dict(dir="app/app-authorization-ims-db2-mq", name="Pending authorization (IMS, Db2, MQ)",
   what="receives card authorization requests over MQ, keeps pending authorizations in an IMS database, shows them online, marks fraud in Db2 and purges expired ones in batch",
   evidence=["app/app-authorization-ims-db2-mq/", "app/app-authorization-ims-db2-mq/csd/CRDDEMO2.csd:11", "app/app-authorization-ims-db2-mq/README.md:1", "app/scheduler/CardDemo.ca7:43"],
   reasoning="its own directory tree with cbl, cpy, bms, jcl, ims, ddl and a CSD file that defines only its programs and transactions (CP00, CPVS, CPVD)"),
 "TRANTYPE": dict(dir="app/app-transaction-type-db2", name="Transaction type maintenance (Db2)",
   what="maintains the transaction type and category reference tables in Db2 online and in batch, and extracts them for the base application's VSAM files",
   evidence=["app/app-transaction-type-db2/", "app/app-transaction-type-db2/csd/CRDDEMOD.csd:11", "app/app-transaction-type-db2/README.md:1", "app/scheduler/CardDemo.controlm:27"],
   reasoning="its own directory tree with cbl, cpy, bms, jcl, ddl, ctl and a CSD file that defines only its programs and transactions (CTLI, CTTU)"),
 "VSAMMQ": dict(dir="app/app-vsam-mq", name="Account and date inquiry over MQ (VSAM)",
   what="answers account and system date inquiries received on MQ queues from the VSAM account file",
   evidence=["app/app-vsam-mq/", "app/app-vsam-mq/csd/CRDDEMOM.csd:1", "app/app-vsam-mq/README.md:1"],
   reasoning="its own directory tree with cbl and a CSD file that defines only its two programs and transactions (CDRA, CDRD)"),
}
def owner(path):
    for mid, m in MODS.items():
        if m["dir"] and path.startswith(m["dir"] + "/"):
            return mid
    return "CARDDEMO"

# who references each declared name, by module
users = collections.defaultdict(dict)   # target path -> {module: evidence}
for e in cat["entries"]:
    for r in e["references"]:
        for d in index.get(r["name"], []):
            if d["id"] != e["id"] and (d["as"] == r["as"] or r["as"] == "jcl-member"):
                users[d["id"]].setdefault(owner(e["id"]), r["evidence"])

KIND2ART = {"cobol-program": "program", "cobol-copybook": "copybook", "jcl-job": "job", "jcl-proc": "proc"}
ASM_EVID = {
 "app/asm/COBDATFT.asm": [grep1(r"CALL +'COBDATFT'", "app/cbl/CBACT01C.cbl")],
 "app/asm/MVSWAIT.asm": [grep1(r"CALL +'MVSWAIT'", "app/cbl/COBSWAIT.cbl")],
 "app/maclib/ASMWAIT.mac": [grep1(r"ASMWAIT", "app/asm/MVSWAIT.asm")],
 "app/maclib/COCDATFT.mac": [grep1(r"COCDATFT", "app/asm/COBDATFT.asm")],
 "app/pli/CBFEECAL.pli": [grep1(r"CALL +'CBFEECAL'", "app/cbl/CBFEEDRV.cbl"), grep1(r"CBFEE", "app/jcl/FEECALC.jcl")],
}
members = collections.defaultdict(list); shared = []
for pid, e in sorted(ents.items()):
    if e["kind"] in PLACEABLE_SKIP:
        continue
    own = owner(pid)
    art = KIND2ART.get(e["kind"], e["kind"])
    ev = list(e.get("evidence") or [pid])
    others = {m: v for m, v in users.get(pid, {}).items() if m != own}
    if e["kind"] in ("cobol-copybook", "scheduler-table") and others:
        used = [own] + sorted(others)
        shared.append({"name": e["member"], "artifact": art, "path": pid, "used_by": used,
                       "evidence": sorted(others.values()),
                       "why": ("the scheduler export orders jobs of %s" if art == "scheduler-table"
                               else "a base-application layout that programs of %s also copy") % " and ".join(used)})
        continue
    reasoning = "in the module's own directory %s" % str(pathlib.PurePosixPath(pid).parent)
    if pid in ASM_EVID:
        ev += ASM_EVID[pid]
        reasoning += "; referenced by a base-application member (cited). Only its names are known: mftb does not read %s logic, the catalogue ask carries forward" % ("PL/I" if pid.endswith(".pli") else "assembler")
    members[own].append({"name": e["member"], "artifact": art, "path": pid, "confidence": "derived",
                         "evidence": ev, "reasoning": reasoning, "confirmed": None, "alternatives": []})

FLAG = {"cobol-program": "src", "cobol-copybook": "copybooks", "jcl-job": "jcl", "jcl-proc": "procs",
        "scheduler-table": "sched", "cics-csd": "csd", "sql-ddl": "sql", "sql-seed": "sql", "sql-script": "sql",
        "control-cards": "cntl", "ims-psb": "ims", "ims-dbd": "ims"}
modules = []
for mid, m in MODS.items():
    roots = collections.defaultdict(set)
    paths = [x["path"] for x in members[mid]] + [s["path"] for s in shared if mid in s["used_by"]]
    for p in paths:
        f = FLAG.get(ents[p]["kind"])
        if f: roots[f].add(str(pathlib.PurePosixPath(p).parent))
    modules.append({"id": mid, "name": m["name"], "naming": "proposed", "what_it_does": m["what"],
                    "confidence": "derived", "confirmed": None, "evidence": m["evidence"],
                    "reasoning": m["reasoning"], "roots": {k: sorted(v) for k, v in sorted(roots.items())},
                    "scan": "python3 -m mftb scan --module %s --from-catalog" % mid, "members": members[mid]})

cross = [
 {"from": "CARDDEMO", "to": "AUTH", "how": "CA7 schedule runs job CBPAUP0J", "evidence": ["app/scheduler/CardDemo.ca7:43"],
  "why": "the base application's scheduler export orders the authorization purge job"},
 {"from": "CARDDEMO", "to": "TRANTYPE", "how": "Control-M schedule runs jobs MNTTRDB2 and TRANEXTR",
  "evidence": ["app/scheduler/CardDemo.controlm:27", "app/scheduler/CardDemo.controlm:58"],
  "why": "the base application's scheduler export orders the Db2 transaction type jobs"},
 {"from": "CARDDEMO", "to": "AUTH", "how": "menu table COMEN02Y names program COPAUS0C", "evidence": ["app/cpy/COMEN02Y.cpy:89"],
  "why": "the base main menu carries the authorization summary program as an option; the transfer itself is a dynamic XCTL, so the edge is a candidate"},
 {"from": "CARDDEMO", "to": "TRANTYPE", "how": "admin menu table COADM02Y names programs COTRTLIC and COTRTUPC",
  "evidence": ["app/cpy/COADM02Y.cpy:49", "app/cpy/COADM02Y.cpy:53"],
  "why": "the base admin menu carries the transaction type programs as options; the transfer itself is a dynamic XCTL, so the edge is a candidate"},
]
for s in shared:
    if s["artifact"] == "copybook":
        for u in s["used_by"][1:]:
            cross.append({"from": u, "to": "CARDDEMO", "how": "COPY %s" % s["name"], "evidence": [x for x in s["evidence"] if MODS[u]["dir"] in x] or s["evidence"],
                          "why": "a sub-application program is compiled against a base-application record layout"})
doc = {"meta": {"estate": "TCS-Devin-Sandbox/ts-cobol-carddemo (app)", "skill": "module-classification",
                "updated_at": datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")},
       "modules": modules, "shared": shared, "unplaced": [], "cross_module": cross}
json.dump(doc, open("store/modules.json", "w"), indent=1)
print({m["id"]: len(m["members"]) for m in modules}, "shared", len(shared))
