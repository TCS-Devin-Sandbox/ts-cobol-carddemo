import glob
import importlib.util
import os
from pathlib import Path

import pytest
from pyspark.sql import SparkSession

TESTS = Path(__file__).resolve().parent
REPO_ROOT = TESTS.parents[1]
DATA = TESTS / "data"
FIXTURES = REPO_ROOT / "fixtures" / "read-masters"


def _load_job(job):
    path = REPO_ROOT / f"testRepo/pyspark/code/{job}/{job}.py"
    spec = importlib.util.spec_from_file_location(job, path)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


@pytest.fixture(scope="session")
def readcard():
    return _load_job("READCARD")


@pytest.fixture(scope="session")
def readacct():
    return _load_job("READACCT")


@pytest.fixture(scope="session")
def spark():
    jars = os.environ.get("COBRIX_JARS") or ",".join(glob.glob(os.path.expanduser("~/jars/*.jar")))
    builder = SparkSession.builder.master("local[2]").appName("carddemo-tests").config("spark.ui.enabled", "false")
    if jars:
        builder = builder.config("spark.jars", jars)
    session = builder.getOrCreate()
    yield session
    session.stop()


@pytest.fixture
def run_job(spark, readcard, tmp_path):
    def _run(cardfile, fmt="text"):
        args = readcard.parse_args(["--cardfile", str(cardfile), "--cardfile-format", fmt, "--output-dir", str(tmp_path)])
        result = readcard.run(spark, args)
        result["output_dir"] = tmp_path
        return result
    return _run


@pytest.fixture
def run_acct(spark, readacct, tmp_path):
    def _run(acctfile, fmt="text"):
        args = readacct.parse_args(["--acctfile", str(acctfile), "--acctfile-format", fmt, "--output-dir", str(tmp_path)])
        result = readacct.run(spark, args)
        result["output_dir"] = tmp_path
        return result
    return _run


def cobrix_available(spark) -> bool:
    try:
        spark._jvm.org.apache.spark.util.Utils.classForName("za.co.absa.cobrix.spark.cobol.source.DefaultSource", True, False)
        return True
    except Exception:  # noqa: BLE001
        return False
