#!/usr/bin/env python3
"""Run real interest setup JCL, substituting the Java pilot for STEP15 only."""
import argparse
import json
import shutil
import subprocess
import sys
from pathlib import Path

ROOT = next(p for p in Path(__file__).resolve().parents if (p / "tools/runjcl/chains.json").is_file())
sys.path.insert(0, str(ROOT))
from tools.parity import record
from tools.runjcl import runjcl


def write_manifest(runner, out):
    manifest = {}
    for dsn, layout in record.CHAINS["interest"]["outputs"].items():
        name = runner.resolve_dsn(dsn)[0] if "(" in dsn else dsn
        entry = runner.catalog.entry(name) or {}
        path = out / "datasets" / f"{name}.txt"
        if not path.exists():
            lrecl = entry.get("lrecl") or entry.get("recsize") or 80
        elif entry.get("type") == "ksds":
            lrecl = entry["recsize"]
        else:
            lrecl = entry.get("lrecl") or 80
        rows = path.read_bytes().splitlines()
        if any(len(row) > lrecl for row in rows):
            raise ValueError(f"overlength output record: {name}")
        path.write_bytes(b"".join(row.rstrip(b" ") + b"\n" for row in rows))
        manifest[name] = {"layout": layout, "lrecl": lrecl, "records": len(rows)}
    (out / "manifest.json").write_text(json.dumps(manifest, indent=1, sort_keys=True))


def run_case(case, out, jar, java):
    chain = record.CHAINS["interest"]
    case_dir = record.FIXTURES / "interest" / case
    if not case_dir.is_dir():
        raise ValueError(f"no fixture {case_dir}")
    # Keep destructive reset targets confined to the harness work directory.
    out = out.resolve()
    if not out.is_relative_to(runjcl.WORK.resolve()) or out == runjcl.WORK.resolve():
        raise ValueError("--out must be a case directory below work/")
    if not jar.is_file():
        raise ValueError(f"no pilot jar {jar}")
    if runjcl.DATASETS.exists():
        shutil.rmtree(runjcl.DATASETS)
    runjcl.DATASETS.mkdir(parents=True)
    if out.exists():
        shutil.rmtree(out)
    out.mkdir(parents=True)
    runner = runjcl.Runner(clock=chain["clock"], joblog=out / "setup-joblog", verbose=True)
    record.materialise_inputs(chain, case_dir, runner.catalog)
    for job in chain["setup"]:
        rc = runner.run_job(runjcl.find_jcl(job))
        if rc > 8:
            raise RuntimeError(f"setup job {job} failed with RC {rc}")
    inputs = out / "intake"
    for stem, layout in (("ACCTDATA", "CVACT01Y"), ("CARDXREF", "CVACT03Y"),
                         ("TCATBALF", "CVTRA01Y"), ("DISCGRP", "CVTRA02Y")):
        dsn = f"AWS.M2.CARDDEMO.{stem}.VSAM.KSDS"
        record.unload(runner, dsn, layout, inputs)
        (inputs / "datasets" / f"{dsn}.txt").rename(
            inputs / "datasets" / f"AWS.M2.CARDDEMO.{stem}.PS.txt")
    step = runjcl.Job(runjcl.find_jcl("INTCALC")).steps[0]
    runner.allocate(step, step.dds["TRANSACT"], out)
    runner.catalog.commit_gdg(runner.pending_gdg)
    runner.catalog.save()
    result = subprocess.run([
        java, "-jar", str(jar.resolve()), "--interest.pilot.run-on-startup=true",
        f"--interest.pilot.input-dir={inputs / 'datasets'}",
        f"--interest.pilot.output-dir={out}",
        f"--interest.pilot.clock={chain['clock'].replace(' ', 'T')}",
    ])
    if result.returncode < 0:
        raise RuntimeError(f"Java terminated by signal {-result.returncode}")
    steps = {k: v for k, v in runner.rcs.items() if "." not in k or k.startswith("INTCALC")}
    steps["STEP15"] = result.returncode
    (out / "rc.json").write_text(json.dumps(
        {"INTCALC": {"maxrc": result.returncode, "steps": steps}}, indent=1, sort_keys=True))
    if result.returncode == 0:
        write_manifest(runner, out)
    print(f"interest/{case}: Java STEP15 RC={result.returncode}, outputs in {out}")
    return result.returncode


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("case")
    parser.add_argument("--out", required=True, type=Path)
    parser.add_argument("--jar", type=Path, default=ROOT / "java/interest/interest-batch/target/interest-batch-0.1.0-PILOT.jar")
    parser.add_argument("--java", default="/usr/lib/jvm/java-21-openjdk-amd64/bin/java")
    args = parser.parse_args()
    return run_case(args.case, args.out, args.jar, args.java)


if __name__ == "__main__":
    sys.exit(main())
