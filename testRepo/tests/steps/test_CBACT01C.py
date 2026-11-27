"""Step definitions and unit tests for READACCT / CBACT01C (CBACT01C-BR-001..007)."""

import subprocess
import sys

import pytest
from pytest_bdd import given, parsers, scenarios, then, when

from conftest import DATA, FIXTURES, REPO_ROOT, cobrix_available

scenarios("../features/CBACT01C.feature")

START = "START OF EXECUTION OF PROGRAM CBACT01C"
END = "END OF EXECUTION OF PROGRAM CBACT01C"
BLOCK = 15  # SYSOUT lines per account (11 fields, separator, VBRC-REC1/2, ACCOUNT-RECORD)
PSCOMP = "AWS.M2.CARDDEMO.ACCTDATA.PSCOMP"
ARRYPS = "AWS.M2.CARDDEMO.ACCTDATA.ARRYPS"
VBPS = "AWS.M2.CARDDEMO.ACCTDATA.VBPS"


def unpack(raw: bytes) -> float:
    nibbles = raw.hex()
    value = int(nibbles[:-1]) / 100
    return -value if nibbles[-1] == "d" else value


@pytest.fixture
def ctx():
    return {}


@given(parsers.parse('the account file "{name}" in "{fmt}" format'))
def given_acctfile(ctx, spark, name, fmt):
    if fmt == "ebcdic" and not cobrix_available(spark):
        pytest.skip("Cobrix jars not on the Spark classpath (set COBRIX_JARS)")
    ctx.update(acctfile=DATA / name, fmt=fmt)


@given(parsers.parse('the synthetic legacy case "{case}"'))
def given_quirks(ctx, case):
    ctx.update(acctfile=DATA / case / "input/AWS.M2.CARDDEMO.ACCTDATA.PS.txt", fmt="text", expected=DATA / case / "expected")


@given(parsers.parse('the read-masters account fixture "{case}"'))
def given_fixture(ctx, case):
    ctx.update(acctfile=FIXTURES / case / "input/AWS.M2.CARDDEMO.ACCTDATA.PS.txt", fmt="text", case=case)


@when("READACCT runs")
def when_runs(ctx, run_acct):
    ctx["result"] = run_acct(ctx["acctfile"], ctx["fmt"])
    out = ctx["result"]["output_dir"]
    ctx["sysout"] = (out / "sysout/READACCT.STEP05.txt").read_text(encoding="latin-1").splitlines()
    ctx["datasets"] = {dsn: (out / f"datasets/{dsn}.txt").read_text(encoding="latin-1").splitlines()
                       for dsn in (PSCOMP, ARRYPS, VBPS) if (out / f"datasets/{dsn}.txt").exists()}


def _program_lines(ctx):
    lines = ctx["sysout"]
    return lines[: lines.index(END) + 1] if END in lines else lines


def _blocks(ctx):
    body = [line for line in _program_lines(ctx)[1:] if line != END]
    if body and body[-1] == "ABENDING PROGRAM":
        body = body[:-2]
    assert len(body) % BLOCK == 0
    return [body[i:i + BLOCK] for i in range(0, len(body), BLOCK)]


def _records(ctx, dsn):
    return [bytes.fromhex(line) for line in ctx["datasets"][dsn]]


@then(parsers.re(r'STEP05 ends with return code "?(?P<rc>[^"]+)"?$'))
def then_rc(ctx, rc):
    assert ctx["result"]["steps"]["STEP05"] == (int(rc) if rc.isdigit() else rc)


@then(parsers.parse("SYSOUT holds {n:d} account blocks in ascending ACCT-ID order"))
def then_ordered(ctx, n):
    ids = [b[0].split(":", 1)[1] for b in _blocks(ctx)]
    assert len(ids) == n and ids == sorted(ids)


@then("every account block ends with its 300-byte account record")
def then_record(ctx):
    for block in _blocks(ctx):
        assert len(block[-1]) == 300 and block[-1][:11] == block[0].split(":", 1)[1]


@then(parsers.re(r'SYSOUT starts with "(?P<line>[^"]+)"$'))
def then_starts(ctx, line):
    assert ctx["sysout"][0] == line


@then("SYSOUT contains the end banner")
def then_end(ctx):
    assert END in ctx["sysout"]


@then("SYSOUT does not contain the end banner")
def then_no_end(ctx):
    assert END not in ctx["sysout"]


@then(parsers.parse('the outputs equal those of "{name}" in "{fmt}" format'))
def then_same_as(ctx, run_acct, tmp_path_factory, spark, readacct, name, fmt):
    out = tmp_path_factory.mktemp("ref")
    readacct.run(spark, readacct.parse_args(["--acctfile", str(DATA / name), "--acctfile-format", fmt, "--output-dir", str(out)]))
    assert (out / "sysout/READACCT.STEP05.txt").read_text(encoding="latin-1").splitlines() == ctx["sysout"]
    for dsn, lines in ctx["datasets"].items():
        assert (out / f"datasets/{dsn}.txt").read_text(encoding="latin-1").splitlines() == lines


@then(parsers.parse("PSCOMP holds {n:d} records of 107 bytes"))
def then_pscomp_len(ctx, n):
    recs = _records(ctx, PSCOMP)
    assert len(recs) == n and all(len(r) == 107 for r in recs)


@then(parsers.parse('PSCOMP record {i:d} has reissue date "{date}" and group id "{group}"'))
def then_pscomp_date(ctx, i, date, group):
    rec = _records(ctx, PSCOMP)[i - 1]
    assert rec[68:78].decode() == date.ljust(10) and rec[97:107].decode().rstrip() == group


@then(parsers.parse("PSCOMP record {i:d} has current balance {bal:g}"))
def then_pscomp_bal(ctx, readacct, i, bal):
    rec = _records(ctx, PSCOMP)[i - 1]
    assert readacct.zoned_to_cents(rec[12:24].decode()) == round(bal * 100)


@then(parsers.parse('the PSCOMP cycle debits are "{values}"'))
def then_debits(ctx, values):
    actual = ["binary zeros" if r[90:97] == bytes(7) else f"{unpack(r[90:97]):.2f}" for r in _records(ctx, PSCOMP)]
    assert actual == [v.strip() for v in values.split(",")]


@then("datasets and SYSOUT equal the legacy-recorded case")
def then_legacy(ctx):
    exp = ctx["expected"]
    assert ctx["sysout"] == (exp / "sysout/READACCT.STEP05.txt").read_text(encoding="latin-1").splitlines()
    for dsn, lines in ctx["datasets"].items():
        assert lines == (exp / f"datasets/{dsn}.txt").read_text(encoding="latin-1").splitlines()


@then(parsers.parse("ARRYPS holds {n:d} records of 110 bytes"))
def then_arryps_len(ctx, n):
    recs = _records(ctx, ARRYPS)
    assert len(recs) == n and all(len(r) == 110 and r[106:] == b"    " for r in recs)


@then(parsers.parse('ARRYPS record {i:d} entries are "{entries}"'))
def then_arryps(ctx, readacct, i, entries):
    rec = _records(ctx, ARRYPS)[i - 1]
    actual = []
    for k in range(5):
        e = rec[11 + 19 * k: 30 + 19 * k]
        bal, deb = readacct.zoned_to_cents(e[:12].decode()) / 100, unpack(e[12:])
        actual.append(f"{bal:g}/{deb:g}")
    expected = [f"{float(b):g}/{float(d):g}" for b, d in (x.strip().split("/") for x in entries.split(","))]
    assert actual == expected


@then(parsers.parse("VBPS holds {n:d} records alternating 12 and 39 bytes"))
def then_vbps(ctx, n):
    recs = ctx["datasets"][VBPS]
    assert len(recs) == n and [len(r) for r in recs] == [12, 39] * (n // 2)


@then(parsers.parse('VBPS record {i:d} is "{value}"'))
def then_vbps_rec(ctx, i, value):
    assert ctx["datasets"][VBPS][i - 1] == value


@then(parsers.parse('SYSOUT holds {n:d} account blocks before "{a}" then "{b}"'))
def then_blocks_then_abend(ctx, n, a, b):
    assert ctx["sysout"][0] == START and ctx["sysout"][-2:] == [a, b]
    assert len(_blocks(ctx)) == n


@then(parsers.parse('SYSOUT ends with "{a}" then "{b}"'))
def then_ends_two(ctx, a, b):
    assert ctx["sysout"][-2:] == [a, b]


@then("tools/parity/compare.py passes for READACCT.PREDEL and READACCT.STEP05")
def then_compare(ctx):
    proc = subprocess.run([sys.executable, str(REPO_ROOT / "tools/parity/compare.py"), "read-masters", ctx["case"],
                           "--actual", str(ctx["result"]["output_dir"]), "--steps", "READACCT.PREDEL", "READACCT.STEP05",
                           "--report", str(ctx["result"]["output_dir"] / "report.md")],
                          cwd=REPO_ROOT, capture_output=True, text=True)
    assert proc.returncode == 0 and "PASS" in proc.stdout, proc.stdout + proc.stderr


# ---- unit tests ----

def test_layout_matches_cvact01y(readacct):
    assert sum(n for _, _, n in readacct.ACCOUNT_RECORD_LAYOUT) == readacct.ACCOUNT_RECORD_LENGTH == 300


@pytest.mark.parametrize("cents,text", [(0, "00000000000{"), (5000, "00000000500{"), (-102500, "00000010250}"), (123456, "00000012345F"), (-1, "00000000000J")])
def test_zoned_round_trip(readacct, cents, text):
    assert readacct.zoned(cents) == text and readacct.zoned_to_cents(text) == cents


@pytest.mark.parametrize("cents,hexval", [(252500, "0000000252500c"), (-250000, "0000000250000d"), (0, "0000000000000c")])
def test_packed(readacct, cents, hexval):
    assert readacct.packed(cents).hex() == hexval


@pytest.mark.parametrize("in_type,inp,out_type,out,err", [
    ("2", "2026-12-31", "2", "20261231", None),
    ("1", "20261231", "1", "2026-12-31", None),
    ("1", "2026-12-31", "1", "", "INVALID INPUT"),
    ("2", "2026-12-31", "1", "", "INVALID INPUT"),
    ("3", "2026-12-31", "2", "", "INVALID INPUT"),
])
def test_cobdatft(readacct, in_type, inp, out_type, out, err):
    result, msg = readacct.cobdatft(in_type, inp, out_type)
    assert result.rstrip() == out and msg == err


def test_display_signed(readacct):
    assert readacct.display_signed(50000) == "000000050000+" and readacct.display_signed(-4250) == "000000004250-"


def test_acctfile_accepts_file_uri(run_acct):
    result = run_acct((DATA / "accts_unsorted.txt").as_uri(), "text")
    assert result["steps"]["STEP05"] == 0 and result["records"] == 3


def test_acctfile_directory_is_open_error(run_acct):
    result = run_acct(DATA, "ebcdic")
    assert result["steps"]["STEP05"] == "U0999"
    assert result["sysout"][-2:] == ["ERROR OPENING ACCTFILE", "ABENDING PROGRAM"]


def test_predel_removes_previous_outputs(readacct, tmp_path):
    (tmp_path / "datasets").mkdir()
    stale = tmp_path / f"datasets/{PSCOMP}.txt"
    stale.write_text("old\n")
    assert readacct.step_predel(tmp_path) == 0 and not stale.exists()


def test_abend_deletes_partial_extracts(run_acct):
    result = run_acct(DATA / "accts_bad_record.txt", "text")
    assert result["steps"]["STEP05"] == "U0999"
    assert not list((result["output_dir"] / "datasets").glob("*.txt"))


def test_flush_failure_on_close_abends(run_acct, readacct, monkeypatch):
    def failing_close(self):
        raise OSError("No space left on device")
    monkeypatch.setattr(readacct.Datasets, "close", failing_close)
    result = run_acct(DATA / "accts_unsorted.txt", "text")
    assert result["steps"]["STEP05"] == "U0999"
    assert result["sysout"][-2:] == ["ACCOUNT FILE WRITE STATUS IS:", "ABENDING PROGRAM"]
    assert '"STEP05": "U0999"' in (result["output_dir"] / "rc.json").read_text()
