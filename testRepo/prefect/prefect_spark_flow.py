"""Prefect flow for the PySpark jobs in testRepo/pyspark/code.

Job sequence (orchestration rule default table, confirmed for READACCT -> READCARD
by app/scheduler/CardDemo.ca7 TRIGGERED JOBS):

| Order | Job      | Depends On |
|-------|----------|------------|
| 1     | READACCT | -          |
| 2     | READCARD | READACCT   |
| 3     | INTCALC  | READCARD   |

Jobs without a generated module under pyspark/code are reported as NOT_GENERATED
and do not block their dependants.

Environment:
  SPARK_SUBMIT   spark-submit executable (default: spark-submit on PATH)
  SPARK_MASTER   Spark master URL (default: local[*])
  COBRIX_JARS    comma-separated Cobrix jars (spark-cobol, cobol-parser, scodec, shapeless)
  READACCT_ACCTFILE, READACCT_ACCTFILE_FORMAT, READACCT_OUTPUT_DIR
  READCARD_CARDFILE, READCARD_CARDFILE_FORMAT, READCARD_OUTPUT_DIR
"""

import os
import subprocess
from pathlib import Path

from prefect import flow, get_run_logger, task

CODE_DIR = Path(__file__).resolve().parents[1] / "pyspark" / "code"
REPO_ROOT = Path(__file__).resolve().parents[2]

JOB_SEQUENCE = [
    {"order": 1, "job": "READACCT", "depends_on": []},
    {"order": 2, "job": "READCARD", "depends_on": ["READACCT"]},
    {"order": 3, "job": "INTCALC", "depends_on": ["READCARD"]},
]

JOB_ARGS = {
    "READACCT": lambda: [
        "--acctfile", os.environ.get("READACCT_ACCTFILE", str(REPO_ROOT / "app/data/EBCDIC/AWS.M2.CARDDEMO.ACCTDATA.PS")),
        "--acctfile-format", os.environ.get("READACCT_ACCTFILE_FORMAT", "ebcdic"),
        "--output-dir", os.environ.get("READACCT_OUTPUT_DIR", str(REPO_ROOT / "testRepo/run_output/READACCT")),
    ],
    "READCARD": lambda: [
        "--cardfile", os.environ.get("READCARD_CARDFILE", str(REPO_ROOT / "app/data/EBCDIC/AWS.M2.CARDDEMO.CARDDATA.PS")),
        "--cardfile-format", os.environ.get("READCARD_CARDFILE_FORMAT", "ebcdic"),
        "--output-dir", os.environ.get("READCARD_OUTPUT_DIR", str(REPO_ROOT / "testRepo/run_output/READCARD")),
    ],
}


def spark_submit_cmd(job: str) -> list:
    cmd = [os.environ.get("SPARK_SUBMIT", "spark-submit"), "--master", os.environ.get("SPARK_MASTER", "local[*]"),
           "--name", job]
    jars = os.environ.get("COBRIX_JARS")
    if jars:
        cmd += ["--jars", jars]
    cmd.append(str(CODE_DIR / job / f"{job}.py"))
    return cmd + JOB_ARGS.get(job, lambda: [])()


@task(name="spark-job")
def run_spark_job(job: str) -> str:
    logger = get_run_logger()
    if not (CODE_DIR / job / f"{job}.py").exists():
        logger.warning("%s: no module under %s - NOT_GENERATED", job, CODE_DIR)
        return "NOT_GENERATED"
    cmd = spark_submit_cmd(job)
    logger.info("%s: %s", job, " ".join(cmd))
    proc = subprocess.run(cmd, capture_output=True, text=True)
    logger.info(proc.stdout)
    if proc.returncode != 0:
        logger.error(proc.stderr[-4000:])
        raise RuntimeError(f"{job} failed with exit code {proc.returncode}")
    return "COMPLETED"


@flow(name="carddemo-pyspark-batch")
def carddemo_pyspark_flow() -> dict:
    futures = {}
    for entry in sorted(JOB_SEQUENCE, key=lambda e: e["order"]):
        upstream = [futures[d] for d in entry["depends_on"] if d in futures]
        futures[entry["job"]] = run_spark_job.with_options(name=entry["job"]).submit(entry["job"], wait_for=upstream)
    return {job: f.result() for job, f in futures.items()}


if __name__ == "__main__":
    print(carddemo_pyspark_flow())
