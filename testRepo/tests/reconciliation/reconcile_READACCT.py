"""Reconcile READACCT.py against the legacy golden files, the legacy-recorded synthetic case and
the EBCDIC master (legacy outputs recorded with the GnuCOBOL harness in tests/data/readacct_ebcdic).

  python reconcile_READACCT.py   (needs pyspark; Cobrix jars via COBRIX_JARS for the EBCDIC case)
"""

import json
import os
import subprocess
import sys
import tempfile
from pathlib import Path

REPO = Path(__file__).resolve().parents[3]
JOB = REPO / "testRepo/pyspark/code/READACCT/READACCT.py"
FIXTURES = REPO / "fixtures/read-masters"
DATA = REPO / "testRepo/tests/data"
EBCDIC = REPO / "app/data/EBCDIC/AWS.M2.CARDDEMO.ACCTDATA.PS"
DATASETS = ["AWS.M2.CARDDEMO.ACCTDATA.PSCOMP", "AWS.M2.CARDDEMO.ACCTDATA.ARRYPS", "AWS.M2.CARDDEMO.ACCTDATA.VBPS"]


def run(acctfile, fmt, out):
    cmd = [os.environ.get("SPARK_SUBMIT", "spark-submit"), "--master", "local[2]"]
    if os.environ.get("COBRIX_JARS"):
        cmd += ["--jars", os.environ["COBRIX_JARS"]]
    cmd += [str(JOB), "--acctfile", str(acctfile), "--acctfile-format", fmt, "--output-dir", str(out)]
    subprocess.run(cmd, check=False, capture_output=True)
    return json.loads((out / "rc.json").read_text())["READACCT"]


def same_files(actual: Path, expected: Path) -> bool:
    names = [f"datasets/{d}.txt" for d in DATASETS] + ["sysout/READACCT.STEP05.txt"]
    return all((actual / n).read_bytes() == (expected / n).read_bytes() for n in names)


def records(out: Path, dsn: str) -> int:
    return len((out / f"datasets/{dsn}.txt").read_text().splitlines())


def main() -> int:
    results = []
    with tempfile.TemporaryDirectory() as tmp:
        for case in sorted(p.name for p in FIXTURES.iterdir() if p.is_dir()):
            out = Path(tmp) / case
            rc = run(FIXTURES / case / "input/AWS.M2.CARDDEMO.ACCTDATA.PS.txt", "text", out)
            proc = subprocess.run([sys.executable, str(REPO / "tools/parity/compare.py"), "read-masters", case, "--actual", str(out),
                                   "--steps", "READACCT.PREDEL", "READACCT.STEP05", "--report", str(out / "report.md")],
                                  cwd=REPO, capture_output=True, text=True)
            results.append((f"fixture {case} (compare.py)", proc.returncode == 0, proc.stdout.strip() or proc.stderr.strip()))
        out = Path(tmp) / "quirks"
        rc = run(DATA / "readacct_quirks/input/AWS.M2.CARDDEMO.ACCTDATA.PS.txt", "text", out)
        ok = same_files(out, DATA / "readacct_quirks/expected") and rc["maxrc"] == 0
        results.append(("synthetic legacy case readacct_quirks", ok, f"{records(out, DATASETS[0])} accounts, byte-identical={ok}"))
        if os.environ.get("COBRIX_JARS"):
            out = Path(tmp) / "ebcdic"
            rc = run(EBCDIC, "ebcdic", out)
            ok = same_files(out, DATA / "readacct_ebcdic/expected") and rc["maxrc"] == 0
            results.append(("EBCDIC AWS.M2.CARDDEMO.ACCTDATA.PS", ok,
                            f"{records(out, DATASETS[0])} PSCOMP / {records(out, DATASETS[1])} ARRYPS / {records(out, DATASETS[2])} VBPS records"))
    for name, ok, detail in results:
        print(f"{'PASS' if ok else 'FAIL'}  {name}: {detail}")
    return 0 if all(ok for _, ok, _ in results) else 1


if __name__ == "__main__":
    sys.exit(main())
