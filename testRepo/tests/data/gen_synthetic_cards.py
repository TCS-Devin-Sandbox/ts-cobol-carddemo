"""Synthetic CVACT02Y card records (no real card numbers or names)."""

from pathlib import Path

HERE = Path(__file__).resolve().parent


def card(num, acct, cvv, name, exp, status):
    rec = f"{num:016d}{acct:011d}{cvv:03d}{name:<50}{exp:<10}{status:1}"
    return rec + " " * (150 - len(rec))


CARDS = [  # deliberately not in CARD-NUM order
    card(9000000000000003, 30, 333, "SYNTHETIC HOLDER C", "2027-03-31", "N"),
    card(9000000000000001, 10, 111, "SYNTHETIC HOLDER A", "2027-01-31", "Y"),
    card(9000000000000002, 20, 222, "SYNTHETIC HOLDER B", "2027-02-28", "Y"),
]


def main():
    (HERE / "cards_unsorted.txt").write_text("".join(c.rstrip() + "\n" for c in CARDS))
    (HERE / "cards_unsorted.ebcdic").write_bytes(b"".join(c.encode("cp037") for c in CARDS))
    (HERE / "cards_truncated.ebcdic").write_bytes(b"".join(c.encode("cp037") for c in CARDS) + CARDS[0][:40].encode("cp037"))
    bad = CARDS[:2] + ["9" * 151]
    (HERE / "cards_bad_record.txt").write_text("".join(c.rstrip() + "\n" for c in bad))


if __name__ == "__main__":
    main()
