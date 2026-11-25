"""Step definitions and unit tests for READCARD / CBACT02C (CBACT02C-BR-001..003)."""

import json

import pytest
from pytest_bdd import given, parsers, scenarios, then, when

from conftest import DATA, FIXTURES, cobrix_available

scenarios("../features/CBACT02C.feature")

START = "START OF EXECUTION OF PROGRAM CBACT02C"
END = "END OF EXECUTION OF PROGRAM CBACT02C"


@pytest.fixture
def ctx():
    return {}


@given(parsers.parse('the card data file "{name}" in "{fmt}" format'))
def given_cardfile(ctx, spark, name, fmt):
    if fmt == "ebcdic" and not cobrix_available(spark):
        pytest.skip("Cobrix jars not on the Spark classpath (set COBRIX_JARS)")
    ctx.update(cardfile=DATA / name, fmt=fmt)


@given(parsers.parse('the read-masters fixture "{case}"'))
def given_fixture(ctx, case):
    ctx.update(cardfile=FIXTURES / case / "input/AWS.M2.CARDDEMO.CARDDATA.PS.txt", fmt="text", case=case)


@when("READCARD runs")
def when_runs(ctx, run_job):
    ctx["result"] = run_job(ctx["cardfile"], ctx["fmt"])
    ctx["sysout"] = (ctx["result"]["output_dir"] / "sysout/READCARD.STEP05.txt").read_text().splitlines()


def _cards(ctx):
    return [line for line in ctx["sysout"] if line not in (START, END)]


@then(parsers.re(r'STEP05 ends with return code "?(?P<rc>[^"]+)"?$'))
def then_rc(ctx, rc):
    expected = int(rc) if rc.isdigit() else rc
    assert ctx["result"]["steps"]["STEP05"] == expected


@then(parsers.parse("SYSOUT holds {n:d} card lines in ascending CARD-NUM order"))
def then_ordered(ctx, n):
    cards = _cards(ctx)
    assert len(cards) == n
    assert [c[:16] for c in cards] == sorted(c[:16] for c in cards)


@then("every card line is 150 characters long")
def then_len(ctx):
    assert all(len(c) == 150 for c in _cards(ctx))


@then("every card line equals the cp037 decode of its input record")
def then_cp037(ctx):
    raw = ctx["cardfile"].read_bytes()
    expected = sorted(raw[i:i + 150].decode("cp037") for i in range(0, len(raw), 150))
    assert _cards(ctx) == expected


@then(parsers.parse('SYSOUT ends with "{a}" then "{b}"'))
def then_ends_two(ctx, a, b):
    assert ctx["sysout"][-2:] == [a, b]


@then("SYSOUT does not contain the end banner")
def then_no_end(ctx):
    assert END not in ctx["sysout"]


@then(parsers.re(r'SYSOUT starts with "(?P<line>[^"]+)"$'))
def then_starts(ctx, line):
    assert ctx["sysout"][0] == line


@then(parsers.re(r'SYSOUT ends with "(?P<line>[^"]+)"$'))
def then_ends(ctx, line):
    assert ctx["sysout"][-1] == line


@then("SYSOUT equals the fixture's expected READCARD.STEP05 sysout")
def then_fixture_sysout(ctx):
    expected = (FIXTURES / ctx["case"] / "expected/sysout/READCARD.STEP05.txt").read_text().splitlines()
    assert ctx["sysout"] == expected


@then("the return codes equal the fixture's expected READCARD rc")
def then_fixture_rc(ctx):
    expected = json.loads((FIXTURES / ctx["case"] / "expected/rc.json").read_text())["READCARD"]
    actual = json.loads((ctx["result"]["output_dir"] / "rc.json").read_text())["READCARD"]
    assert actual == expected


# ---- unit tests ----

def test_schema_matches_cvact02y(readcard):
    assert sum(length for _, _, length in readcard.CARD_RECORD_LAYOUT) == readcard.CARD_RECORD_LENGTH == 150
    assert [f.name for f in readcard.CARD_RECORD_SCHEMA.fields] == [n for n, _, _ in readcard.CARD_RECORD_LAYOUT]


def test_text_reader_parses_fields(spark, readcard):
    df = readcard.read_cardfile_text(spark, str(DATA / "cards_unsorted.txt"))
    row = df.orderBy("CARD_NUM").first()
    assert row["CARD_NUM"] == "9000000000000001"
    assert row["CARD_ACCT_ID"] == 10 and row["CARD_CVV_CD"] == 111
    assert row["CARD_EMBOSSED_NAME"] == "SYNTHETIC HOLDER A"
    assert row["CARD_EXPIRAION_DATE"] == "2027-01-31" and row["CARD_ACTIVE_STATUS"] == "Y"


def test_ebcdic_and_text_readers_agree(spark, readcard):
    if not cobrix_available(spark):
        pytest.skip("Cobrix jars not on the Spark classpath")
    cols = ["CARD_NUM", "CARD_ACCT_ID", "CARD_CVV_CD", "CARD_EMBOSSED_NAME", "CARD_EXPIRAION_DATE", "CARD_ACTIVE_STATUS", "display_line"]
    text = readcard.read_cardfile_text(spark, str(DATA / "cards_unsorted.txt")).select(cols).orderBy("CARD_NUM").collect()
    ebc = readcard.read_cardfile_ebcdic(spark, str(DATA / "cards_unsorted.ebcdic"), readcard.DEFAULT_COPYBOOK).select(cols).orderBy("CARD_NUM").collect()
    assert text == ebc
