#!/usr/bin/env python3
"""Run a candidate over the chain fixtures and compare its output to the goldens."""
from __future__ import annotations

import argparse
import json
import os
import shlex
import shutil
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT))
from tools.parity import compare  # noqa: E402

CHAINS = json.loads((ROOT / "tools" / "runjcl" / "chains.json").read_text())
WORK = ROOT / "work" / "parity-java"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--chain", choices=sorted(CHAINS))
    parser.add_argument("--case", help="run just this fixture of the selected chain")
    parser.add_argument("--steps", nargs="+", metavar="JOB[.STEP]")
    args = parser.parse_args()
    if args.case and not args.chain:
        parser.error("--case requires --chain")
    try:
        command = shlex.split(os.environ.get("CANDIDATE", ""))
    except ValueError as exc:
        parser.error(f"invalid CANDIDATE command: {exc}")
    if not command:
        parser.error("set CANDIDATE to the command that runs one fixture")

    fixtures = []
    for chain in [args.chain] if args.chain else sorted(CHAINS):
        cases = sorted(path.name for path in (compare.FIXTURES / chain).iterdir() if path.is_dir())
        if args.case and args.case not in cases:
            parser.error(f"no fixture {chain}/{args.case}")
        if not cases:
            parser.error(f"no fixtures for {chain}")
        fixtures.extend((chain, case) for case in ([args.case] if args.case else cases))

    report = WORK / "report.md"
    report.unlink(missing_ok=True)
    failed = False
    for chain, case in fixtures:
        actual = WORK / chain / case / "actual"
        if actual.exists():
            shutil.rmtree(actual)
        actual.mkdir(parents=True)
        inputs = compare.FIXTURES / chain / case / "input"
        try:
            subprocess.run([*command, "--chain", chain, "--case", case, "--input", str(inputs),
                            "--out", str(actual), "--clock", CHAINS[chain]["clock"]], cwd=ROOT, check=True)
        except (OSError, subprocess.CalledProcessError) as exc:
            detail = f"exit {exc.returncode}" if isinstance(exc, subprocess.CalledProcessError) else exc
            print(f"{chain}/{case} [java]: candidate command failed: {detail}", file=sys.stderr)
            failed = True
            continue
        try:
            failed |= bool(compare.run_comparison(chain, case, actual, report, "java", steps=args.steps))
        except ValueError as exc:
            parser.error(str(exc))
    if report.is_file():
        print(f"report: {report.relative_to(ROOT)}")
    return int(failed)


if __name__ == "__main__":
    sys.exit(main())
