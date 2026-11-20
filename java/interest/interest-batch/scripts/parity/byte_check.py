#!/usr/bin/env python3
"""Check ordered fixed records under the fixtures' LF/trimmed-space framing."""
import argparse
from decimal import Decimal
import json
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[5]
CASES = ("i01-baseline", "i02-tie-half-cent", "i03-zero-rate-skipped",
         "i04-default-group-fallback", "i05-multi-category", "i06-negative-balance",
         "i07-nontie-rounding")
ACCOUNT = "datasets/AWS.M2.CARDDEMO.ACCTDATA.VSAM.KSDS.txt"
TRAN = "datasets/AWS.M2.CARDDEMO.SYSTRAN.G0001V00.txt"
DATASETS = {ACCOUNT: 300, TRAN: 350}


def records(path, lrecl):
    data = Path(path).read_bytes()
    lines = data.split(b"\n") if data else []
    if lines and lines[-1] == b"":
        lines.pop()
    for index, line in enumerate(lines):
        if len(line) > lrecl:
            raise ValueError(f"file={path} record={index} byte_offset={lrecl} overlength={len(line)}")
    return [line.ljust(lrecl, b" ") for line in lines]


def check_pair(expected, actual, lrecl, label):
    before, after = records(expected, lrecl), records(actual, lrecl)
    for index, (left, right) in enumerate(zip(before, after)):
        for offset, (a, b) in enumerate(zip(left, right)):
            if a != b:
                raise ValueError(f"{label} record={index} byte_offset={offset} expected=0x{a:02x} actual=0x{b:02x}")
    if len(before) != len(after):
        raise ValueError(f"{label} record={min(len(before), len(after))} byte_offset=0 "
                         f"record_count expected={len(before)} actual={len(after)}")
    return len(before)


def zoned(raw):
    # CVTRA05Y TRAN-AMT / CVACT01Y ACCT-CURR-BAL: ASCII signed zoned cents.
    last = chr(raw[-1])
    positive, negative = "{ABCDEFGHI", "}JKLMNOPQR"
    if last in positive:
        digit, sign = positive.index(last), 1
    elif last in negative:
        digit, sign = negative.index(last), -1
    elif last.isascii() and last.isdigit():
        digit, sign = int(last), 1
    else:
        raise ValueError("invalid golden zoned sign")
    return Decimal(sign * (int(raw[:-1]) * 10 + digit)).scaleb(-2)


def golden_facts(case, root=ROOT):
    fixture = root / "fixtures/interest" / case
    tx = records(fixture / "expected" / TRAN, 350)
    before = records(fixture / "input/AWS.M2.CARDDEMO.ACCTDATA.PS.txt", 300)
    after = records(fixture / "expected" / ACCOUNT, 300)
    if [r[:11] for r in before] != [r[:11] for r in after]:
        raise ValueError(f"case={case} golden account order/count changed")
    balances = records(fixture / "input/AWS.M2.CARDDEMO.TCATBALF.PS.txt", 50)
    last = balances[-1][:11]
    old = {r[:11]: r for r in before}
    new = {r[:11]: r for r in after}
    if old[last] != new[last]:
        raise ValueError(f"case={case} final account golden differs from input")
    return (len(tx), sum(a != b for a, b in zip(before, after)),
            sum((zoned(r[132:143]) for r in tx), Decimal("0.00")),
            sum((zoned(b[12:24]) - zoned(a[12:24]) for a, b in zip(before, after)), Decimal("0.00")),
            last.decode("ascii"))


def main(arguments=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=ROOT)
    parser.add_argument("--actual-root", type=Path, default=ROOT / "work/06-parity-gate")
    parser.add_argument("--case", choices=CASES)
    parser.add_argument("--facts", action="store_true")
    args = parser.parse_args(arguments)
    try:
        cases = (args.case,) if args.case else CASES
        if args.facts:
            for case in cases:
                print("\t".join(map(str, golden_facts(case, args.root))))
            return 0
        files = 0
        for case in cases:
            fixture = args.root / "fixtures/interest" / case / "expected"
            manifest = json.loads((fixture / "manifest.json").read_text())
            for filename, lrecl in DATASETS.items():
                label = f"case={case} file={filename}"
                count = check_pair(fixture / filename, args.actual_root / case / filename, lrecl, label)
                expected_count = manifest[Path(filename).name[:-4]]["records"]
                if count != expected_count:
                    raise ValueError(f"{label} record=0 byte_offset=0 manifest_count={expected_count} actual={count}")
                print(f"BYTE {label} records={count} lrecl={lrecl} identical")
                files += 1
        print(f"BYTE CHECK PASS cases={len(cases)} files={files} framing=LF-space-padded")
        return 0
    except (OSError, ValueError, KeyError) as error:
        print(f"BYTE CHECK FAIL {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
