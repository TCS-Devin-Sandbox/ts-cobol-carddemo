#!/usr/bin/env python3
"""Nightly F1 -> B4 export -> F2 -> field-level comparison."""
import argparse
import datetime
import hashlib
import json
import os
from pathlib import Path
import re
import shlex
import shutil
import subprocess
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT))
from tools.parity import compare

CHAINS = ("posting", "interest", "read-masters", "trantype-update", "trantype-dynamic")
JARS = {"posting": "posting/target/carddemo-posting.jar", "interest": "interest/target/intcalc.jar",
        "read-masters": "read-masters/target/read-masters.jar"}
COMPOSE = shlex.split(os.environ.get("COMPOSE", "docker compose"))


def command(args, env=None, **kwargs):
    return subprocess.run([str(a) for a in args], cwd=ROOT, env=env, check=True, **kwargs)


def transfer(mode, source, target, env):
    command(["java", "-cp", str(ROOT / "work/shadow/runtime/BOOT-INF/classes") + ":" +
             str(ROOT / "work/shadow/runtime/BOOT-INF/lib/*"),
             "com.carddemo.batch.dataload.ShadowTransfer", mode, source, target], env)


def synthetic(drop, env):
    for chain in CHAINS:
        for case in sorted((ROOT / "fixtures" / chain).iterdir()):
            dest = drop / chain / case.name
            shutil.copytree(case / "input", dest / "unload", dirs_exist_ok=True)
            transfer("encode", case / "input", dest / "unload", env)
            shutil.copytree(case / "expected", dest / "mainframe", dirs_exist_ok=True)


def trantype(chain, inputs, actual, run_id, database):
    def sql(args, **kwargs):
        return command([*COMPOSE, "exec", "-T", "postgres", "psql", "-Xq", "-v", "ON_ERROR_STOP=1",
                        "-U", "carddemo", "-d", database, *args], **kwargs)
    command([*COMPOSE, "exec", "-T", "postgres", "createdb", "-U", "carddemo", database])
    with (ROOT / "db/postgres/01-transaction-type.sql").open() as f:
        sql([], stdin=f)
    if (inputs / "db/seed.sql").exists():
        with (inputs / "db/seed.sql").open() as f:
            sql([], stdin=f)
    env = dict(os.environ, SPRING_DATASOURCE_URL=f"jdbc:postgresql://localhost:5432/{database}",
               SPRING_DATASOURCE_USERNAME="carddemo", SPRING_DATASOURCE_PASSWORD="carddemo")
    args = ["java", "-jar", "java/trantype-batch/target/trantype-batch.jar",
            "--spring.batch.jdbc.initialize-schema=always", f"run.id={run_id}",
            f"input={inputs}/INPFILE.txt", f"out={actual}"]
    if chain == "trantype-dynamic":
        args += ["--spring.batch.job.name=MNTTRDYN", f"dynsql={inputs}/DYNSQL.txt", "parm=COBTUPDT"]
    result = subprocess.run(args, cwd=ROOT, env=env)
    (actual / "db").mkdir(parents=True, exist_ok=True)
    for table, order in (("transaction_type", "tr_type"), ("transaction_type_categor", "trc_type_code, trc_type_category")):
        with (actual / "db" / f"carddemo.{table}.csv").open("w") as f:
            sql(["-c", f"COPY (SELECT * FROM carddemo.{table} ORDER BY {order}) TO STDOUT WITH CSV HEADER"], stdout=f)
    return result.returncode


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--drop", type=Path)
    parser.add_argument("--run-id", default="shadow_" + datetime.datetime.now(datetime.timezone.utc).strftime("%Y%m%d"))
    parser.add_argument("--negative-proof", action="store_true")
    args = parser.parse_args()
    if not re.fullmatch(r"[a-z][a-z0-9_]{0,30}", args.run_id):
        parser.error("run id must be a lowercase SQL-safe identifier, at most 31 characters")
    work = ROOT / "work/shadow" / args.run_id
    work.mkdir(parents=True, exist_ok=True)
    env = dict(os.environ, JDBC_URL=os.environ.get("SHADOW_JDBC_URL", "jdbc:postgresql://localhost:5432/carddemo"),
               JDBC_USER=os.environ.get("PGUSER", "carddemo"), JDBC_PASSWORD=os.environ.get("PGPASSWORD", "carddemo"))
    runtime = ROOT / "work/shadow/runtime"
    runtime.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(ROOT / "java/data-load/target/data-load.jar") as jar:
        jar.extractall(runtime)
    drop = args.drop.resolve() if args.drop else work / "drop"
    if not args.drop:
        synthetic(drop, env)
    if args.negative_proof:
        copied = work / "negative-drop"
        shutil.copytree(drop, copied, dirs_exist_ok=True)
        victim = next(copied.glob("*/*/mainframe/sysout/*.txt"))
        victim.write_bytes(victim.read_bytes() + b"\nSYNTHETIC DRIFT\n")
        drop = copied
    rows = []
    specs = json.loads((ROOT / "tools/runjcl/chains.json").read_text())
    for chain in CHAINS:
        for case in sorted((drop / chain).iterdir()) if (drop / chain).is_dir() else []:
            row = dict(chain=chain, case=case.name, reconciliation="ERROR", chain_exit=None, verdict="ERROR", differences=[])
            rows.append(row)
            try:
                stage = work / chain / case.name
                inputs, actual = stage / "input", stage / "actual"
                if stage.exists():
                    raise ValueError("run directory already exists; preserve it and use a new run id")
                shutil.copytree(case / "unload", inputs)
                token = hashlib.sha256(f"{args.run_id}/{chain}/{case.name}".encode()).hexdigest()[:20]
                case_env = dict(env, JDBC_SCHEMA="shadow_" + token)
                entries = json.loads((inputs / "manifest.json").read_text())
                if entries:
                    result = subprocess.run(["java", "-jar", "java/data-load/target/data-load.jar", str(inputs),
                                             str(inputs / "manifest.json"), str(stage / "reconciliation")], cwd=ROOT, env=case_env)
                    row["reconciliation"] = json.loads((stage / "reconciliation/reconciliation.json").read_text())["status"]
                    if result.returncode:
                        raise ValueError("F1 load failed")
                    transfer("export", inputs, inputs, case_env)
                else:
                    row["reconciliation"] = "NOT_APPLICABLE"
                    if chain in JARS:
                        raise ValueError("VSAM chain requires a nonempty F1 manifest")
                if chain in JARS:
                    for key in ("POSTING_JDBC_URL", "INTCALC_JDBC_URL", "READ_MASTERS_JDBC_URL"):
                        case_env[key] = env["JDBC_URL"]
                    case_env.update(PGUSER=env["JDBC_USER"], PGPASSWORD=env["JDBC_PASSWORD"])
                    row["chain_exit"] = subprocess.run(["java", "-jar", "java/" + JARS[chain], "--chain", chain,
                        "--case", case.name, "--input", str(inputs), "--out", str(actual), "--run-id", args.run_id + "_" + token[:8],
                        "--clock", specs[chain]["clock"]], cwd=ROOT, env=case_env).returncode
                else:
                    row["chain_exit"] = trantype(chain, inputs, actual, args.run_id, "shadow_" + token)
                expected = case / "mainframe"
                if not (expected / "rc.json").is_file():
                    raise ValueError("mainframe rc.json is required")
                if chain in JARS and not (expected / "manifest.json").is_file():
                    raise ValueError("VSAM mainframe output manifest.json is required")
                diffs, checked = compare.compare_case(chain, case.name, actual, expected=expected)
                row["differences"] = diffs
                compare.write_report(work / "comparisons.md", chain, case.name, "shadow", diffs, checked)
                row["verdict"] = "MATCH" if not diffs and row["chain_exit"] == 0 else "DRIFT"
            except (Exception, SystemExit) as exc:
                row["error"] = str(exc)
            print(f"{chain}/{case.name}: F1={row['reconciliation']} exit={row['chain_exit']} {row['verdict']}")
    status = "MATCH" if rows and all(r["verdict"] == "MATCH" for r in rows) else "DRIFT"
    (work / "drift.json").write_text(json.dumps(dict(run=args.run_id, status=status, cases=rows), indent=2) + "\n")
    lines = [f"# Shadow {args.run_id}: {status}", "", "| Chain/case | F1 | Exit | Compare |", "|---|---|---|---|"]
    for row in rows:
        lines.append(f"| {row['chain']}/{row['case']} | {row['reconciliation']} | {row['chain_exit']} | {row['verdict']} |")
        if "error" in row:
            lines.append("\nError: " + row["error"] + "\n")
        if row["differences"]:
            lines.append("\n```json\n" + json.dumps(row["differences"], indent=2) + "\n```\n")
    (work / "drift.md").write_text("\n".join(lines) + "\n")
    print(f"SHADOW {status} cases={len(rows)} report={work / 'drift.md'}")
    return int(status != "MATCH")


if __name__ == "__main__":
    sys.exit(main())
