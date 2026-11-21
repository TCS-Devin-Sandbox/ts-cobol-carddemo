#!/usr/bin/env python3
"""Prepare statement scratch sources; see docs/BASELINE.md for oracle limits."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def prepare() -> None:
    out = ROOT / "work" / "statements"
    out.mkdir(parents=True, exist_ok=True)
    source = (ROOT / "app/cbl/CBSTM03A.CBL").read_text()
    start = source.index("           SET ADDRESS OF PSA-BLOCK")
    end = source.index("           OPEN OUTPUT STMT-FILE", start)
    (out / "CBSTM03A.cbl").write_text(source[:start] + source[end:])
    (out / "CBSTM03B.cbl").write_bytes((ROOT / "app/cbl/CBSTM03B.CBL").read_bytes())
    cpy = ROOT / "work/cpy"
    cpy.mkdir(parents=True, exist_ok=True)
    (cpy / "COSTM01.cpy").write_bytes((ROOT / "app/cpy/COSTM01.CPY").read_bytes())
    (cpy / "CUSTREC.cpy").write_text((ROOT / "app/cpy/CUSTREC.cpy").read_text().expandtabs(1))


if __name__ == "__main__":
    prepare()
