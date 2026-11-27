"""Synthetic ACCTFILE records (CVACT01Y, 300 bytes) for the READACCT / CBACT01C tests. No real data.

  python gen_synthetic_accounts.py   -> accts_unsorted.{txt,ebcdic}, accts_bad_record.txt, accts_truncated.ebcdic
"""

from pathlib import Path

HERE = Path(__file__).resolve().parent


def zoned(cents: int) -> str:
    text = f"{abs(cents):012d}"
    return text[:-1] + ("}JKLMNOPQR" if cents < 0 else "{ABCDEFGHI")[int(text[-1])]


def account(acct_id, status, bal, limit, cash, open_d, exp_d, reissue_d, credit, debit, zip_, group):
    rec = (f"{acct_id:011d}" + status + zoned(bal) + zoned(limit) + zoned(cash) + open_d + exp_d + reissue_d
           + zoned(credit) + zoned(debit) + zip_.ljust(10) + group.ljust(10))
    return rec.ljust(300)


ACCOUNTS = [
    account(30000000003, "N", -4250, 100000, 50000, "2020-02-29", "2028-02-29", "2024-06-30", 1000, 0, "99001", "SILVER"),
    account(10000000001, "Y", 123456, 500000, 200000, "2018-01-01", "2027-01-31", "2025-01-31", 0, 0, "99002", "GOLD"),
    account(20000000002, "Y", 0, 250000, 100000, "2019-07-15", "2026-07-31", "2023-07-31", 2500, 9900, "99003", "STANDARD"),
]


def main() -> None:
    (HERE / "accts_unsorted.txt").write_text("".join(r.rstrip() + "\n" for r in ACCOUNTS))
    ebcdic = b"".join(r.encode("cp037") for r in ACCOUNTS)
    (HERE / "accts_unsorted.ebcdic").write_bytes(ebcdic)
    (HERE / "accts_truncated.ebcdic").write_bytes(ebcdic + ACCOUNTS[0][:100].encode("cp037"))
    # second record is 301 bytes - longer than the 300-byte ACCTFILE record
    (HERE / "accts_bad_record.txt").write_text(ACCOUNTS[1].rstrip() + "\n" + ACCOUNTS[0] + "X\n")


if __name__ == "__main__":
    main()
