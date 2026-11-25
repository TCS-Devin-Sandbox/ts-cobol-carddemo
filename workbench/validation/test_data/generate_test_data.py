#!/usr/bin/env python3
"""Regenerate the synthetic READCARD test data sets (CVACT02Y, one record per line).

Synthetic values only (AGENTS.md): card numbers follow the fixture pattern 4000..., names
are "TEST CUSTOMER nnnn".  Each data set is a directory holding the card master under the
dataset name READCARD reads (config.json input.dataset).
"""
from __future__ import annotations

import random
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
DATASET = "AWS.M2.CARDDEMO.CARDDATA.PS.txt"


def record(card: str, acct: int, cvv: int, name: str, expiry="2026-12-31", status="Y", filler="") -> str:
    line = f"{card:<16.16}{acct:011d}{cvv:03d}{name:<50.50}{expiry:<10.10}{status:1.1}{filler}"
    return line.rstrip()  # legacy PS inputs are stored trimmed (see fixtures/read-masters/r01-populated)


def card(n: int) -> str:
    return f"4{n:015d}"


DATA_SETS = {
    "single_record": [record(card(1001), 1001, 101, "TEST CUSTOMER 1001")],
    "unordered": [record(card(n), n, n % 1000, f"TEST CUSTOMER {n}") for n in (3003, 1, 2002, 1001, 999)],
    "ebcdic_mixed_keys": [record(k, i, 100 + i, f"TEST CUSTOMER {i}") for i, k in enumerate(
        ["9000000000000001", "A000000000000001", "4000000000000001", "Z000000000000001", "0000000000000001"], 1)],
    "trimmed_records": [record(card(n), n, 7, f"TEST CUSTOMER {n}", status=s) for n, s in ((5, "Y"), (6, "N"), (7, ""))],
    "exact_150": [record(card(1), 1, 1, "TEST CUSTOMER 1", filler="F" * 59)],
    "over_length": [record(card(1), 1, 1, "TEST CUSTOMER 1"),
                    record(card(2), 2, 2, "TEST CUSTOMER 2", filler="X" * 60)],
    "duplicate_keys": [record(card(1), 1, 1, "TEST CUSTOMER 1"), record(card(2), 2, 2, "TEST CUSTOMER 2"),
                       record(card(1), 3, 3, "TEST CUSTOMER 3")],
}


def large_volume(count: int, seed: int = 20221105) -> list[str]:
    numbers = list(range(1, count + 1))
    random.Random(seed).shuffle(numbers)
    return [record(card(n), n, n % 1000, f"TEST CUSTOMER {n}") for n in numbers]


def write(directory: Path, lines: list[str]) -> Path:
    directory.mkdir(parents=True, exist_ok=True)
    path = directory / DATASET
    path.write_text("".join(line + "\n" for line in lines), encoding="latin-1")
    return path


def main() -> int:
    for name, lines in DATA_SETS.items():
        write(HERE / name, lines)
    print(f"wrote {len(DATA_SETS)} data sets under {HERE}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
