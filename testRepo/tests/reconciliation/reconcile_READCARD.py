"""Reconcile READCARD.py against the legacy golden files and the EBCDIC master.

  python reconcile_READCARD.py   (needs pyspark; Cobrix jars via COBRIX_JARS for the EBCDIC case)
"""

import json
import os
import subprocess
import sys
import tempfile
from pathlib import Path

REPO = Path(__file__).resolve().parents[3]
JOB = REPO / "testRepo/pyspark/code/READCARD/READCARD.py"
FIXTURES = REPO / "fixtures/read-masters"
EBCDIC = REPO / "app/data/EBCDIC/AWS.M2.CARDDEMO.CARDDATA.PS"


def run(cardfile, fmt, out):
    cmd = [os.environ.get("SPARK_SUBMIT", "spark-submit"), "--master", "local[2]"]
    if os.environ.get("COBRIX_JARS"):
        cmd += ["--jars", os.environ["COBRIX_JARS"]]
    cmd += [str(JOB), "--cardfile", str(cardfile), "--cardfile-format", fmt, "--output-dir", str(out)]
    subprocess.run(cmd, check=False, capture_output=True)
    return (out / "sysout/READCARD.STEP05.txt").read_text().splitlines(), json.loads((out / "rc.json").read_text())


def main() -> int:
    results = []
    with tempfile.TemporaryDirectory() as tmp:
        for case in sorted(p.name for p in FIXTURES.iterdir() if p.is_dir()):
            out = Path(tmp) / case
            sysout, rc = run(FIXTURES / case / "input/AWS.M2.CARDDEMO.CARDDATA.PS.txt", "text", out)
            exp_sysout = (FIXTURES / case / "expected/sysout/READCARD.STEP05.txt").read_text().splitlines()
            exp_rc = json.loads((FIXTURES / case / "expected/rc.json").read_text())["READCARD"]
            ok = sysout == exp_sysout and rc["READCARD"] == exp_rc
            results.append((f"fixture {case}", ok, f"{len(sysout) - 2} card lines, rc {rc['READCARD']['maxrc']}"))
        if os.environ.get("COBRIX_JARS"):
            out = Path(tmp) / "ebcdic"
            sysout, rc = run(EBCDIC, "ebcdic", out)
            raw = EBCDIC.read_bytes()
            expected = sorted(raw[i:i + 150].decode("cp037") for i in range(0, len(raw), 150))
            ok = sysout[1:-1] == expected and rc["READCARD"]["maxrc"] == 0
            results.append(("EBCDIC AWS.M2.CARDDEMO.CARDDATA.PS", ok, f"{len(sysout) - 2}/{len(expected)} card lines"))
    for name, ok, detail in results:
        print(f"{'PASS' if ok else 'FAIL'}  {name}: {detail}")
    return 0 if all(ok for _, ok, _ in results) else 1


if __name__ == "__main__":
    sys.exit(main())
