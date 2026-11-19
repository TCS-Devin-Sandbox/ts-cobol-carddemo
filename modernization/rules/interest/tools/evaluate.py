#!/usr/bin/env python3
"""Rulebook evaluator for the interest stream.

Derives expected/<case>.json for every case under fixtures/interest from
rules.yaml and the case's input/ files only.  It never reads
fixtures/*/expected, never runs the COBOL and does not use
tools/parity/naive_ref.py.  Every value cites the rule ids it comes from.
Values that depend on a QUIRK or HOLE are OPEN and list one candidate per
decision option (all other decisions held at the legacy option A).

    evaluate.py            write expected/*.json
    evaluate.py --check    fail when expected/*.json is out of date
"""
from __future__ import annotations

import argparse
import json
import sys
from decimal import ROUND_DOWN, ROUND_HALF_EVEN, ROUND_HALF_UP, Decimal
from pathlib import Path

import yaml

HERE = Path(__file__).resolve().parents[1]
ROOT = HERE.parents[2]
sys.path.insert(0, str(ROOT))
from tools.parity.copybook import decode_record  # noqa: E402  (codec only)

FIXTURES = ROOT / "fixtures" / "interest"
OUT = HERE / "expected"
CENT = Decimal("0.01")
ROUNDING = {"A": ROUND_DOWN, "B": ROUND_HALF_UP, "C": ROUND_HALF_EVEN}

# value -> rules it comes from (QUIRK/HOLE ids make the value OPEN)
FIELD_RULES = {
    "exists": ["INT-R04", "INT-R05", "INT-Q03", "INT-Q04"],
    "TRAN-ID": ["INT-R08", "INT-R09", "INT-Q03", "INT-Q04"],
    "TRAN-TYPE-CD": ["INT-R05"], "TRAN-CAT-CD": ["INT-R05"], "TRAN-SOURCE": ["INT-R05"],
    "TRAN-DESC": ["INT-R05"], "TRAN-MERCHANT-ID": ["INT-R05"], "TRAN-MERCHANT-NAME": ["INT-R05"],
    "TRAN-MERCHANT-CITY": ["INT-R05"], "TRAN-MERCHANT-ZIP": ["INT-R05"],
    "TRAN-CARD-NUM": ["INT-R05", "INT-R09"],
    "TRAN-AMT": ["INT-R01", "INT-R02", "INT-Q01", "INT-Q03"],
    "TRAN-ORIG-TS": ["INT-Q05", "TN-02"], "TRAN-PROC-TS": ["INT-Q05", "TN-02"],
    "ACCT-CURR-BAL": ["INT-R06", "INT-R01", "INT-Q01", "INT-Q02", "INT-Q03"],
    "ACCT-CURR-CYC-CREDIT": ["INT-R07", "INT-Q02", "INT-Q06"],
    "ACCT-CURR-CYC-DEBIT": ["INT-R07", "INT-Q02", "INT-Q06"],
    "updated": ["INT-R06", "INT-Q02", "INT-Q06"],
    "return_code": ["INT-R10"],
    "fee_transactions": ["INT-H01"],
}


def load_rules() -> dict:
    return yaml.safe_load((HERE / "rules.yaml").read_text())


def read_records(case: Path, spec: dict) -> list[dict]:
    src = case / "input" / f"{spec['dataset']}.txt"
    lines = src.read_text(encoding="latin-1").splitlines() if src.exists() else []
    recs = []
    for line in lines:
        raw = line.ljust(spec["lrecl"])[:spec["lrecl"]]
        rec = decode_record(spec["layout"], raw.encode("latin-1"))
        rec["_raw"] = raw
        recs.append(rec)
    return recs


def money(d: Decimal) -> str:
    return f"{d.quantize(CENT):.2f}"


def db2_ts(clock: str) -> str:
    date, time = clock.split(" ")
    y, m, d = date.replace("/", "-").split("-")
    hh, mi, rest = time.split(":")
    ss, hund = (rest.split(".") + ["00"])[:2]
    return f"{y}-{m}-{d}-{hh}.{mi}.{ss}.{hund[:2]:0<2}0000"


def simulate(data: dict, ctx: dict, opt: dict) -> dict:
    """Run the rulebook for one combination of decision options."""
    acct_by_id = {int(a["ACCT-ID"]): a for a in data["ACCTFILE"]}
    cards: dict[int, list[str]] = {}
    for x in sorted(data["XREFFILE"], key=lambda r: r["XREF-CARD-NUM"]):
        cards.setdefault(int(x["XREF-ACCT-ID"]), []).append(x["XREF-CARD-NUM"])
    rates = {(r["DIS-ACCT-GROUP-ID"], r["DIS-TRAN-TYPE-CD"], int(r["DIS-TRAN-CAT-CD"])): Decimal(r["DIS-INT-RATE"])
             for r in data["DISCGRP"]}
    balances = sorted(data["TCATBALF"], key=lambda r: r["_raw"][:17])          # INT-R09 key order
    parm = ctx["parm_date"]["value"]
    ts_run = db2_ts(ctx["clock"]["value"])
    txns, accounts, sysout, unresolved = [], {}, ["START OF EXECUTION OF PROGRAM CBACT04C"], []
    seq = 0
    order = []
    for b in balances:
        sysout.append(b["_raw"])                                               # TN-01
        acct = int(b["TRANCAT-ACCT-ID"])
        if acct not in accounts:
            if acct not in acct_by_id or acct not in cards:
                return {"return_code": "ABEND U999 (harness RC 12)", "abend": True}   # INT-R10
            a = acct_by_id[acct]
            accounts[acct] = {"total": Decimal(0), "rec": a}
            order.append(acct)
            if len(cards[acct]) > 1:
                unresolved.append({"value": f"TRAN-CARD-NUM for account {acct}", "rule": "INT-H03",
                                   "owner": "<owner: Card operations>"})
        a = accounts[acct]["rec"]
        group = a["ACCT-GROUP-ID"]
        key = (group, b["TRANCAT-TYPE-CD"], int(b["TRANCAT-CD"]))
        rate_rules = ["INT-R02"]
        if key not in rates:                                                   # INT-R03
            sysout += ["DISCLOSURE GROUP RECORD MISSING", "TRY WITH DEFAULT GROUP CODE"]
            key = ("DEFAULT",) + key[1:]
            rate_rules = ["INT-R03"]
            if key not in rates:
                return {"return_code": "ABEND U999 (harness RC 12)", "abend": True}   # INT-H08
        rate = rates[key]
        if rate == 0:                                                          # INT-R04
            continue
        bal = Decimal(b["TRAN-CAT-BAL"])
        if bal < 0 and opt["INT-Q03"] == "B":
            continue
        interest = (bal * rate / Decimal(1200)).quantize(CENT, rounding=ROUNDING[opt["INT-Q01"]])  # INT-R01/Q01
        if interest == 0 and opt["INT-Q04"] == "B":
            continue
        accounts[acct]["total"] += interest
        seq += 1                                                               # INT-R08
        ts_orig = ts_run if opt["INT-Q05"] == "A" else None
        txns.append({
            "key": f"{acct:011d}/{b['TRANCAT-TYPE-CD']}/{int(b['TRANCAT-CD']):04d}",
            "rate_rules": rate_rules,
            "TRAN-ID": f"{parm}{seq:06d}", "TRAN-TYPE-CD": "01", "TRAN-CAT-CD": "0005",
            "TRAN-SOURCE": "System", "TRAN-DESC": f"Int. for a/c {acct:011d}",
            "TRAN-AMT": money(interest), "TRAN-MERCHANT-ID": "0", "TRAN-MERCHANT-NAME": "",
            "TRAN-MERCHANT-CITY": "", "TRAN-MERCHANT-ZIP": "", "TRAN-CARD-NUM": cards[acct][0],
            "TRAN-ORIG-TS": ts_orig, "TRAN-PROC-TS": ts_run,
        })
    out_accts = {}
    for i, acct in enumerate(order):
        rec = accounts[acct]["rec"]
        last = i == len(order) - 1
        updated = not last or opt["INT-Q02"] == "B"                            # INT-R06 / INT-Q02
        bal = Decimal(rec["ACCT-CURR-BAL"]) + (accounts[acct]["total"] if updated else 0)
        out_accts[f"{acct:011d}"] = {
            "updated": updated, "ACCT-CURR-BAL": money(bal),
            "ACCT-CURR-CYC-CREDIT": money(Decimal(0) if updated else Decimal(rec["ACCT-CURR-CYC-CREDIT"])),
            "ACCT-CURR-CYC-DEBIT": money(Decimal(0) if updated else Decimal(rec["ACCT-CURR-CYC-DEBIT"])),
        }
    for acct, rec in acct_by_id.items():                                       # INT-Q06
        k = f"{acct:011d}"
        if k not in out_accts:
            reset = opt["INT-Q06"] == "B"
            out_accts[k] = {"updated": reset, "ACCT-CURR-BAL": money(Decimal(rec["ACCT-CURR-BAL"])),
                            "ACCT-CURR-CYC-CREDIT": money(Decimal(0) if reset else Decimal(rec["ACCT-CURR-CYC-CREDIT"])),
                            "ACCT-CURR-CYC-DEBIT": money(Decimal(0) if reset else Decimal(rec["ACCT-CURR-CYC-DEBIT"]))}
    sysout.append("END OF EXECUTION OF PROGRAM CBACT04C")
    return {"return_code": 0, "transactions": txns, "accounts": out_accts, "sysout": sysout,
            "fee_transactions": [] if opt["INT-H01"] == "A" else None, "unresolved": unresolved}


def scenarios(rules: dict) -> list[tuple[str, dict]]:
    undecided = [r for r in rules["rules"] if r["type"] in ("QUIRK", "HOLE")]
    legacy = {r["id"]: "A" for r in undecided}
    out = [("legacy (every decision A)", legacy)]
    for r in undecided:
        for o in r["decision"]["options"]:
            if o != "A":
                out.append((f"{r['id']}={o}", {**legacy, r["id"]: o}))
    return out


def cell(field: str, runs: dict[str, object], extra_rules: list[str] | None = None, unresolved_owner=None) -> dict:
    rules = list(FIELD_RULES[field]) + (extra_rules or [])
    undecided = [r for r in rules if r.startswith(("INT-Q", "INT-H"))]
    legacy = runs["legacy (every decision A)"]
    if not undecided:
        return {"value": legacy, "rules": rules}
    cands = [{"decision": "legacy (every decision A)", "value": legacy}]
    for name, v in runs.items():
        if name.split("=")[0] in undecided:
            c = {"decision": name, "value": v}
            if v is None:
                c.update(status="UNRESOLVED", owner=unresolved_owner or "<owner: TBD>")
            cands.append(c)
    return {"status": "OPEN", "rules": rules, "candidates": cands}


def evaluate_case(rules: dict, case: Path) -> dict:
    ctx = rules["run_context"]
    data = {dd: read_records(case, spec) for dd, spec in ctx["inputs"].items()}
    runs = {name: simulate(data, ctx, opt) for name, opt in scenarios(rules)}
    legacy = runs["legacy (every decision A)"]
    owners = {r["id"]: r["decision"]["owner"] for r in rules["rules"] if "decision" in r}
    unresolved = list(legacy.get("unresolved", []))
    result: dict = {
        "case": case.name,
        "derived_from": ["modernization/rules/interest/rules.yaml"] +
                        sorted(f"fixtures/interest/{case.name}/input/{p.name}" for p in (case / "input").iterdir()),
        "not_sme_signoff": True,
        "return_code": cell("return_code", {n: r["return_code"] for n, r in runs.items()}),
    }
    if legacy.get("abend"):
        result["status"] = "UNRESOLVED"
        return result
    keys = []
    for r in runs.values():
        for t in r["transactions"]:
            if t["key"] not in keys:
                keys.append(t["key"])
    txns = []
    for k in keys:
        per = {n: next((t for t in r["transactions"] if t["key"] == k), None) for n, r in runs.items()}
        base = per["legacy (every decision A)"] or next(t for t in per.values() if t)
        entry = {"balance_key": k, "exists": cell("exists", {n: t is not None for n, t in per.items()})}
        for f in FIELD_RULES:
            if f.startswith("TRAN-"):
                extra = [x for x in base["rate_rules"] if x not in FIELD_RULES[f]] if f == "TRAN-AMT" else None
                vals = {n: (t[f] if t else "(no transaction)") for n, t in per.items()}
                entry[f] = cell(f, vals, extra, owners.get("INT-Q05") if "TS" in f else None)
                if f == "TRAN-ORIG-TS":
                    for c in entry[f].get("candidates", []):
                        if c.get("status") == "UNRESOLVED":
                            unresolved.append({"value": f"TRAN-ORIG-TS of {k}", "rule": "INT-Q05",
                                               "decision": c["decision"], "owner": c["owner"]})
        txns.append(entry)
    accts = {}
    for k in legacy["accounts"]:
        accts[k] = {f: cell(f, {n: r["accounts"][k][f] for n, r in runs.items()})
                    for f in ("updated", "ACCT-CURR-BAL", "ACCT-CURR-CYC-CREDIT", "ACCT-CURR-CYC-DEBIT")}
        accts[k]["other_fields"] = {"value": "unchanged from input", "rules": ["TN-06"]}
    fee = cell("fee_transactions", {n: r["fee_transactions"] for n, r in runs.items()}, None, owners["INT-H01"])
    unresolved.append({"value": "fee transactions", "rule": "INT-H01", "decision": "INT-H01=B",
                       "owner": owners["INT-H01"]})
    sysout_rules = ["TN-01"] + (["INT-R03"] if any("DISCLOSURE GROUP RECORD MISSING" in s for s in legacy["sysout"]) else [])
    result.update({
        "transactions": txns,
        "accounts": accts,
        "fee_transactions": fee,
        "sysout": {"value": legacy["sysout"], "rules": sysout_rules},
        "unresolved": unresolved,
    })
    open_rules = sorted({r for v in _walk(result) if v.get("status") == "OPEN"
                         for r in v["rules"] if r.startswith(("INT-Q", "INT-H"))})
    result["status"] = "OPEN" if open_rules else "DETERMINED"
    result["open_rules"] = open_rules
    return result


def _walk(node):
    if isinstance(node, dict):
        if "rules" in node:
            yield node
        for v in node.values():
            yield from _walk(v)
    elif isinstance(node, list):
        for v in node:
            yield from _walk(v)


def render_all() -> dict[str, str]:
    rules = load_rules()
    return {f"{c.name}.json": json.dumps(evaluate_case(rules, c), indent=1) + "\n"
            for c in sorted(p for p in FIXTURES.iterdir() if (p / "input").is_dir())}


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--check", action="store_true")
    args = ap.parse_args()
    files = render_all()
    if args.check:
        stale = [n for n, t in files.items() if not (OUT / n).exists() or (OUT / n).read_text() != t]
        extra = [p.name for p in OUT.glob("*.json") if p.name not in files]
        if stale or extra:
            print(f"expected/ out of date: {stale + extra}; run make rules-interest")
            return 1
        print(f"expected/ up to date ({len(files)} cases)")
        return 0
    OUT.mkdir(exist_ok=True)
    for n, t in files.items():
        (OUT / n).write_text(t)
    print(f"wrote {len(files)} expectations to {OUT.relative_to(ROOT)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
