"""pytest-bdd steps for features/READCARD.feature (Stage 5 validation of READCARD).

Runs the generated PySpark job in-process (pyspark_application.run) against the golden
fixtures and the synthetic data in test_data/, and checks the listing, rc.json and
run_metrics.json.  The generated application and its config are used unmodified.
"""
from __future__ import annotations

import codecs
import hashlib
import json
import shutil
import sys
from pathlib import Path

import pytest
from pytest_bdd import given, parsers, scenarios, then, when

VALIDATION = Path(__file__).resolve().parents[1]
WORKBENCH = VALIDATION.parent
ROOT = WORKBENCH.parent
GENERATION = WORKBENCH / "pyspark_generation"
FIXTURES = ROOT / "fixtures" / "read-masters"
TEST_DATA = VALIDATION / "test_data"
CLOCK = "2022-07-05 10:00:00.00"

sys.path.insert(0, str(GENERATION))
sys.path.insert(0, str(TEST_DATA))
import pyspark_application as app  # noqa: E402
import generate_test_data as gen  # noqa: E402

scenarios("../features/READCARD.feature")

START = "START OF EXECUTION OF PROGRAM CBACT02C"
END = "END OF EXECUTION OF PROGRAM CBACT02C"


def ebcdic_key(line: str) -> bytes:
    return codecs.encode(line[:16], "cp037")


def read_lines(path: Path) -> list[str]:
    return path.read_text(encoding="latin-1").splitlines()


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


@pytest.fixture(scope="session")
def spark():
    config = app.load_config(str(GENERATION / "config.json"))
    session = app.create_spark(config)
    yield session
    session.stop()


@pytest.fixture
def state(tmp_path):
    return {"tmp": tmp_path, "out": tmp_path / "out", "config_path": GENERATION / "config.json"}


# ---------------------------------------------------------------- Given

@given("the approved READCARD PySpark configuration")
def approved_config(state):
    state["config"] = app.load_config(str(state["config_path"]))
    assert state["config"]["rules"]["collation"] == "ebcdic"
    assert state["config"]["rules"]["abend_rc"] == 12 and state["config"]["rules"]["abend_code"] == 999


@given(parsers.parse('the golden fixture case "{case}"'))
def golden_case(state, case):
    state["input_dir"] = FIXTURES / case / "input"
    state["golden"] = FIXTURES / case / "expected" / "sysout" / "READCARD.STEP05.txt"
    assert state["golden"].exists()


@given(parsers.parse('the test data set "{name}"'))
def given_test_data_set(state, name):
    state["input_dir"] = TEST_DATA / name
    assert (state["input_dir"] / gen.DATASET).exists(), "run test_data/generate_test_data.py"


@given(parsers.parse("a generated card master of {count:d} unordered records"))
def generated_volume(state, count):
    state["input_dir"] = state["tmp"] / "volume"
    gen.write(state["input_dir"], gen.large_volume(count))


@given("a card master directory with no card dataset")
def missing_dataset(state):
    state["input_dir"] = state["tmp"] / "empty_dir"
    state["input_dir"].mkdir()


@given("closing the listing fails")
def closing_fails(monkeypatch):
    def fail(*_args, **_kwargs):
        raise OSError("simulated close failure")
    monkeypatch.setattr(app.os, "replace", fail)


@given(parsers.parse('the golden fixture case "{case}" converted to Parquet'))
def golden_as_parquet(state, spark, case):
    golden_case(state, case)
    layout = state["config"]["layout"]["fields"]
    rows = [tuple(line.ljust(150)[f["offset"]:f["offset"] + f["length"]].rstrip() for f in layout)
            for line in read_lines(state["input_dir"] / gen.DATASET)]
    parquet_dir = state["tmp"] / "parquet_in"
    spark.createDataFrame(rows, [f["name"] for f in layout]).write.parquet(str(parquet_dir / "card_master.parquet"))
    config = dict(state["config"], input=dict(state["config"]["input"], format="parquet",
                                               dataset="card_master.parquet"))
    state["config_path"] = state["tmp"] / "config_parquet.json"
    state["config_path"].write_text(json.dumps(config))
    state["input_dir"] = parquet_dir


# ---------------------------------------------------------------- When

@when("the PySpark application processes the card master")
def run_job(state, spark):
    source = Path(state["input_dir"]) / gen.DATASET
    state["source"] = source
    state["source_hash"] = sha256(source) if source.is_file() else None
    state["input_lines"] = read_lines(source) if source.is_file() else []
    if state["out"].exists():
        shutil.rmtree(state["out"])
    ctx = app.build_context(["--config", str(state["config_path"]), "--input", str(state["input_dir"]),
                             "--out", str(state["out"]), "--chain", "read-masters", "--clock", CLOCK])
    state["rc"] = app.run(ctx, spark)
    state["listing"] = read_lines(state["out"] / "sysout" / "READCARD.STEP05.txt")
    state["rc_json"] = json.loads((state["out"] / "rc.json").read_text())
    state["metrics"] = json.loads((state["out"] / "run_metrics.json").read_text())
    state["details"] = [line for line in state["listing"][1:]
                        if line not in (END,) and not line.startswith(("ERROR ", "FILE STATUS", "ABENDING", "CEE3ABD"))]


@when(parsers.parse('the file status "{status}" is formatted'))
def format_status(state, status):
    status = status.replace("<x04>", "\x04").replace("<x22>", "\x22")
    state["display"] = app.format_file_status(status)


# ---------------------------------------------------------------- Then

@then(parsers.parse("the return code is {rc:d}"))
def return_code(state, rc):
    assert state["rc"] == rc
    assert state["rc_json"] == {"READCARD": {"maxrc": rc, "steps": {"STEP05": rc}}}


@then("the listing is identical to the legacy golden SYSOUT")
def identical_to_golden(state):
    actual = (state["out"] / "sysout" / "READCARD.STEP05.txt").read_bytes()
    assert actual == state["golden"].read_bytes()


@then("the listing has one detail line per input record")
def one_line_per_record(state):
    assert len(state["details"]) == len(state["input_lines"]) == state["metrics"]["input_records"]


@then(parsers.parse("the listing has {count:d} detail lines"))
def detail_count(state, count):
    assert len(state["details"]) == count == state["metrics"]["output_detail_lines"]


@then("the detail lines are in ascending EBCDIC card-number order")
def ebcdic_order(state):
    keys = [ebcdic_key(line) for line in state["details"]]
    assert all(a < b for a, b in zip(keys, keys[1:]))


@then(parsers.parse('the first detail card number is "{card}"'))
def first_card(state, card):
    assert state["details"][0][:16] == card


@then("the detail card numbers are in this order:")
def card_order(state, datatable):
    assert [line[:16] for line in state["details"]] == [row[0] for row in datatable[1:]]


@then("every detail line is 150 characters long")
def length_150(state):
    assert state["details"] and all(len(line) == 150 for line in state["details"])


@then("every detail line equals its input record padded to 150 characters")
def padded_images(state):
    expected = sorted((line.ljust(150) for line in state["input_lines"]), key=ebcdic_key)
    assert state["details"] == expected


@then("every CVACT02Y field of every detail line equals the legacy SYSOUT field")
def field_level(state):
    golden = read_lines(state["golden"])[1:-1]
    assert len(golden) == len(state["details"])
    for exp, act in zip(golden, state["details"]):
        for fld in state["config"]["layout"]["fields"]:
            sl = slice(fld["offset"], fld["offset"] + fld["length"])
            assert act[sl] == exp[sl], fld["source"]


@then(parsers.parse('the first listing line is "{text}"'))
def first_line(state, text):
    assert state["listing"][0] == text


@then(parsers.parse('the last listing line is "{text}"'))
def last_line(state, text):
    assert state["listing"][-1] == text


@then("the listing is exactly:")
def listing_exact(state, datatable):
    assert state["listing"] == [row[0] for row in datatable[1:]]


@then("the last listing lines are:")
def listing_tail(state, datatable):
    tail = [row[0] for row in datatable[1:]]
    assert state["listing"][-len(tail):] == tail
    assert END not in state["listing"]


@then(parsers.parse('the run metrics record abend code {code:d} and status "{status}"'))
def metrics_abend(state, code, status):
    assert state["metrics"]["abend_code"] == code and state["metrics"]["status"] == status
    assert state["metrics"]["rc"] == state["config"]["rules"]["abend_rc"]


@then(parsers.parse('the run metrics record status "{status}" with no abend code'))
def metrics_success(state, status):
    assert state["metrics"]["status"] == status and state["metrics"]["abend_code"] is None
    assert state["listing"][-1] == END


@then(parsers.parse('the displayed status is "{display}"'))
def displayed(state, display):
    assert state["display"] == display


@then("the card master file is unchanged")
def unchanged(state):
    assert sha256(state["source"]) == state["source_hash"]


@then("the listing line count is the input record count plus 2")
def plus_two(state):
    assert len(state["listing"]) == len(state["input_lines"]) + 2


@then("input_records equals output_detail_lines equals the legacy detail line count")
def reconcile_counts(state):
    legacy = len(read_lines(state["golden"])) - 2
    assert state["metrics"]["input_records"] == state["metrics"]["output_detail_lines"] == legacy


@then("records_sha256 equals the SHA-256 of the legacy detail lines")
def reconcile_hash(state):
    digest = hashlib.sha256()
    for line in read_lines(state["golden"])[1:-1]:
        digest.update(line.encode("latin-1") + b"\n")
    assert state["metrics"]["records_sha256"] == digest.hexdigest()


@then("min_card_num and max_card_num equal the first and last legacy card numbers")
def reconcile_keys(state):
    golden = read_lines(state["golden"])[1:-1]
    assert (state["metrics"]["min_card_num"], state["metrics"]["max_card_num"]) == (golden[0][:16], golden[-1][:16])
