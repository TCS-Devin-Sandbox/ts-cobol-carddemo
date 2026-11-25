#!/usr/bin/env python3
"""READCARD / STEP05 / CBACT02C as a PySpark batch job.

Lists every card master record (CVACT02Y, 150 bytes) in card-number order
between the CBACT02C start/end banners and writes rc.json and run_metrics.json.
Design: workbench/target_design (stages S0-S5, error path E).  Rule IDs refer
to workbench/technical_summary/business_rules.md.

Usage:
    pyspark_application.py --config config.json [--input DIR] [--out DIR]
                           [--chain CHAIN] [--case CASE] [--clock TS]
"""
from __future__ import annotations

import argparse
import codecs
import hashlib
import json
import logging
import os
import sys
import tempfile
from dataclasses import dataclass, field
from datetime import datetime, timezone
from pathlib import Path

from pyspark.sql import DataFrame, SparkSession
from pyspark.sql import functions as F
from pyspark.sql.types import IntegerType, LongType, StringType, StructField, StructType

LOG = logging.getLogger("readcard")

RAW_SCHEMA = StructType([
    StructField("source_line_no", LongType(), False),
    StructField("raw_line", StringType(), False),
    StructField("raw_length", IntegerType(), False),
])


class CardFileError(Exception):
    """A CARDFILE open/read/close failure (BR-006); carries the legacy file status."""

    def __init__(self, stage: str, status: str, detail: str):
        super().__init__(f"{stage}: status {status}: {detail}")
        self.stage = stage
        self.status = status
        self.detail = detail


@dataclass
class RunContext:
    config: dict
    input_dir: str
    out_dir: Path
    chain: str | None = None
    case: str | None = None
    clock: str | None = None
    listing: list[str] = field(default_factory=list)
    metrics: dict = field(default_factory=dict)


# ---------------------------------------------------------------- S0 init

def load_config(path: str) -> dict:
    with open(path, encoding="utf-8") as handle:
        return json.load(handle)


def build_context(argv: list[str] | None = None) -> RunContext:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--config", default=str(Path(__file__).with_name("config.json")))
    parser.add_argument("--input", help="directory holding the card master dataset (overrides input.path)")
    parser.add_argument("--out", help="output directory (overrides output.path)")
    parser.add_argument("--chain")
    parser.add_argument("--case")
    parser.add_argument("--clock", help="frozen run clock, recorded in run_metrics.json")
    args = parser.parse_args(argv)
    config = load_config(args.config)
    input_dir = args.input or config["input"]["path"]
    out_dir = args.out or config["output"]["path"]
    if not input_dir or not out_dir:
        parser.error("input and output paths must be set in config.json or with --input/--out")
    ctx = RunContext(config, input_dir, Path(out_dir), args.chain, args.case, args.clock)
    ctx.metrics = {
        "job": config["job_name"], "step": config["step_name"], "program": config["program_name"],
        "chain": args.chain, "case": args.case, "clock": args.clock,
        "config_sha256": hashlib.sha256(json.dumps(config, sort_keys=True).encode()).hexdigest(),
        "started_at": datetime.now(timezone.utc).isoformat(),
    }
    return ctx


def create_spark(config: dict) -> SparkSession:
    spark_cfg = config["spark"]
    builder = SparkSession.builder.appName(spark_cfg["app_name"]).master(spark_cfg["master"])
    for key, value in spark_cfg.get("conf", {}).items():
        builder = builder.config(key, value)
    spark = builder.getOrCreate()
    spark.sparkContext.setLogLevel(spark_cfg.get("log_level", "WARN"))
    return spark


# ---------------------------------------------------------------- S1 open / ingest

def source_path(ctx: RunContext) -> str:
    return os.path.join(ctx.input_dir, ctx.config["input"]["dataset"])


def path_exists(spark: SparkSession, path: str) -> bool:
    jvm = spark._jvm  # Hadoop FS so local, HDFS and object stores behave the same
    hpath = jvm.org.apache.hadoop.fs.Path(path)
    return hpath.getFileSystem(spark._jsc.hadoopConfiguration()).exists(hpath)


def read_source(spark: SparkSession, ctx: RunContext) -> DataFrame:
    """S1: open the card master read-only (TR-005); missing/unreadable -> open error (BR-006)."""
    cfg = ctx.config["input"]
    path = source_path(ctx)
    status = ctx.config["legacy_file_status"]["open_error"]
    try:
        if not path_exists(spark, path):
            raise CardFileError("open", status, f"{path} not found")
        if cfg["format"] == "fixed_width":
            return read_fixed_width(spark, path, cfg["encoding"])
        if cfg["format"] == "parquet":
            return read_parquet(spark, path, ctx.config["layout"])
        raise CardFileError("open", status, f"unsupported input format {cfg['format']}")
    except CardFileError:
        raise
    except Exception as exc:  # noqa: BLE001 - any open failure is ERROR OPENING CARDFILE
        raise CardFileError("open", status, str(exc)) from exc


def read_fixed_width(spark: SparkSession, path: str, encoding: str) -> DataFrame:
    lines = spark.sparkContext.textFile(path, use_unicode=False)
    rows = lines.zipWithIndex().map(
        lambda pair: (pair[1] + 1, pair[0].decode(encoding), len(pair[0].decode(encoding))))
    return spark.createDataFrame(rows, RAW_SCHEMA)


def read_parquet(spark: SparkSession, path: str, layout: dict) -> DataFrame:
    """OQ-04 option: typed card_master table; record image rebuilt fixed-width, untrimmed."""
    frame = spark.read.parquet(path)
    image = F.concat(*[F.rpad(F.coalesce(F.col(f["name"]).cast("string"), F.lit("")), f["length"], " ")
                       for f in layout["fields"]])
    return (frame.select(image.alias("raw_line"))
            .withColumn("source_line_no", F.monotonically_increasing_id() + 1)
            .withColumn("raw_length", F.length("raw_line"))
            .select("source_line_no", "raw_line", "raw_length"))


# ---------------------------------------------------------------- S2 parse & validate

def validate_records(raw: DataFrame, ctx: RunContext) -> None:
    """V-01 record length <= lrecl; V-02 unique card_num (OQ-03). Failures -> read error."""
    lrecl = ctx.config["input"]["lrecl"]
    statuses = ctx.config["legacy_file_status"]
    too_long = raw.filter(F.col("raw_length") > lrecl)
    long_count = too_long.count()
    ctx.metrics["validation_record_too_long"] = long_count
    if long_count:
        first = too_long.orderBy("source_line_no").first()
        raise CardFileError("read", statuses["record_too_long"],
                            f"{long_count} record(s) longer than {lrecl}; first at line {first['source_line_no']}")
    if not ctx.config["input"]["pad_short_records"]:
        short = raw.filter(F.col("raw_length") < lrecl).count()
        ctx.metrics["validation_record_too_short"] = short
        if short:
            raise CardFileError("read", statuses["record_too_long"], f"{short} record(s) shorter than {lrecl}")
    if ctx.config["rules"]["enforce_unique_key"]:
        key = ctx.config["layout"]["fields"][0]
        dupes = (raw.select(F.substring(F.rpad("raw_line", lrecl, " "), key["offset"] + 1, key["length"]).alias("k"))
                 .groupBy("k").count().filter(F.col("count") > 1))
        dupe_count = dupes.count()
        ctx.metrics["validation_duplicate_keys"] = dupe_count
        if dupe_count:
            raise CardFileError("read", statuses["duplicate_key"], f"{dupe_count} duplicate card number(s)")


def apply_transformations(raw: DataFrame, ctx: RunContext) -> DataFrame:
    """T-01 pad to lrecl (BR-003, AS-01); T-02 identity field projection (source_to_target_mapping.md)."""
    lrecl = ctx.config["input"]["lrecl"]
    frame = raw.withColumn("record_image", F.rpad("raw_line", lrecl, " "))
    for fld in ctx.config["layout"]["fields"]:
        frame = frame.withColumn(fld["name"], F.substring("record_image", fld["offset"] + 1, fld["length"]))
    return frame


# ---------------------------------------------------------------- S3 order

def ebcdic_translation() -> tuple[str, str]:
    """Map each Latin-1 char to the char whose code point is its cp037 byte (BR-002 KSDS collation)."""
    src = "".join(chr(code) for code in range(256))
    dst = "".join(chr(codecs.encode(ch, "cp037")[0]) for ch in src)
    return src, dst


def apply_business_rules(frame: DataFrame, ctx: RunContext) -> DataFrame:
    """BR-001 no filter; BR-002 ascending card number in legacy collation."""
    key = ctx.config["layout"]["key_field"]
    if ctx.config["rules"]["collation"] == "ebcdic":
        src, dst = ebcdic_translation()
        frame = frame.withColumn("sort_key", F.translate(F.col(key), src, dst))
    else:
        frame = frame.withColumn("sort_key", F.col(key))
    # Spark compares strings by UTF-8 bytes, which preserves code-point (= EBCDIC byte) order.
    return frame.orderBy(F.col("sort_key").asc(), F.col("source_line_no").asc())


# ---------------------------------------------------------------- S4 emit / S5 close

def generate_output(ordered: DataFrame, ctx: RunContext) -> Path:
    """S4: one 150-char line per record, ordered, single writer (BR-003, TR-006). Returns temp listing."""
    out_cfg = ctx.config["output"]
    target = ctx.out_dir / out_cfg["sysout_file"]
    target.parent.mkdir(parents=True, exist_ok=True)
    digest = hashlib.sha256()
    count = 0
    first_key = last_key = None
    handle = tempfile.NamedTemporaryFile("w", encoding=out_cfg["encoding"], newline="\n",
                                         dir=target.parent, prefix=".readcard-", delete=False)
    with handle:
        for line in ctx.listing:
            handle.write(line + "\n")
        for row in ordered.select("record_image", "card_num").toLocalIterator():
            handle.write(row["record_image"] + "\n")
            digest.update(row["record_image"].encode(out_cfg["encoding"]) + b"\n")
            first_key = first_key if first_key is not None else row["card_num"]
            last_key = row["card_num"]
            count += 1
    ctx.metrics.update(output_detail_lines=count, min_card_num=first_key, max_card_num=last_key,
                       records_sha256=digest.hexdigest())
    LOG.info("records processed=%d", count)
    return Path(handle.name)


def finalize(temp_listing: Path, ctx: RunContext) -> None:
    """S5: END banner, atomic rename; failure -> close error (BR-006)."""
    target = ctx.out_dir / ctx.config["output"]["sysout_file"]
    try:
        with open(temp_listing, "a", encoding=ctx.config["output"]["encoding"], newline="\n") as handle:
            handle.write(ctx.config["messages"]["end_banner"] + "\n")
        os.replace(temp_listing, target)
    except OSError as exc:
        raise CardFileError("close", ctx.config["legacy_file_status"]["close_error"], str(exc)) from exc


def write_outcome(ctx: RunContext, rc: int, status: str) -> None:
    out_cfg = ctx.config["output"]
    job, step = ctx.config["job_name"], ctx.config["step_name"]
    ctx.out_dir.mkdir(parents=True, exist_ok=True)
    (ctx.out_dir / out_cfg["rc_file"]).write_text(json.dumps({job: {"maxrc": rc, "steps": {step: rc}}}, indent=1) + "\n")
    ctx.metrics.update(status=status, rc=rc, ended_at=datetime.now(timezone.utc).isoformat())
    (ctx.out_dir / out_cfg["metrics_file"]).write_text(json.dumps(ctx.metrics, indent=1) + "\n")


# ---------------------------------------------------------------- E error path

def format_file_status(status: str) -> str:
    """TR-003 / T-03: CBACT02C 9910-DISPLAY-IO-STATUS."""
    stat1, stat2 = status[0], status[1]
    if status.isdigit() and stat1 != "9":
        return "00" + status
    return stat1 + f"{ord(stat2):03d}"


def error_lines(err: CardFileError, config: dict) -> list[str]:
    msgs = config["messages"]
    lines = [msgs[f"{err.stage}_error"], msgs["file_status_prefix"] + format_file_status(err.status), msgs["abend"]]
    if config["rules"]["emulate_harness_abend_line"]:
        lines.append(f"{msgs['harness_abend_prefix']}+{config['rules']['abend_code']:09d}")
    return lines


def handle_abend(err: CardFileError, ctx: RunContext, emitted: Path | None) -> int:
    """TR-002/TR-004: diagnostics after what was emitted, no END banner, abend outcome."""
    out_cfg = ctx.config["output"]
    target = ctx.out_dir / out_cfg["sysout_file"]
    target.parent.mkdir(parents=True, exist_ok=True)
    prior = emitted.read_text(encoding=out_cfg["encoding"]).splitlines() if emitted and emitted.exists() \
        else list(ctx.listing)
    if err.stage == "close" and prior and prior[-1] == ctx.config["messages"]["end_banner"]:
        prior.pop()  # D-01 / A-04: CBACT02C abends in 9000-CARDFILE-CLOSE before the END display
    with open(target, "w", encoding=out_cfg["encoding"], newline="\n") as handle:
        for line in prior + error_lines(err, ctx.config):
            handle.write(line + "\n")
    if emitted and emitted.exists() and emitted != target:
        emitted.unlink()
    rc = ctx.config["rules"]["abend_rc"]
    ctx.metrics.update(abend_code=ctx.config["rules"]["abend_code"], legacy_file_status=err.status,
                       error_stage=err.stage, error_detail=err.detail)
    write_outcome(ctx, rc, "ABEND")
    LOG.error("ABEND U%04d stage=%s status=%s: %s", ctx.config["rules"]["abend_code"], err.stage, err.status, err.detail)
    return rc


# ---------------------------------------------------------------- pipeline

def run(ctx: RunContext, spark: SparkSession) -> int:
    msgs = ctx.config["messages"]
    ctx.listing.append(msgs["start_banner"])  # BR-004
    ctx.metrics.update(abend_code=None, legacy_file_status="00", input_records=0, output_detail_lines=0,
                       input_path=source_path(ctx))
    LOG.info("job start %s/%s input=%s", ctx.config["job_name"], ctx.config["step_name"], source_path(ctx))
    emitted = None
    try:
        raw = read_source(spark, ctx).cache()                       # S1
        try:
            ctx.metrics["input_records"] = raw.count()
            LOG.info("records read=%d", ctx.metrics["input_records"])
            validate_records(raw, ctx)                               # S2
            card_master = apply_transformations(raw, ctx)
        except CardFileError:
            raise
        except Exception as exc:  # noqa: BLE001
            raise CardFileError("read", ctx.config["legacy_file_status"]["record_too_long"], str(exc)) from exc
        ordered = apply_business_rules(card_master, ctx)            # S3
        emitted = generate_output(ordered, ctx)                      # S4
        finalize(emitted, ctx)                                       # S5
    except CardFileError as err:
        LOG.error("validation/IO failure: %s", err)
        return handle_abend(err, ctx, emitted)
    write_outcome(ctx, 0, "SUCCESS")                                 # BR-005
    LOG.info("job end rc=0 records=%d rejected=0", ctx.metrics["output_detail_lines"])
    return 0


def main(argv: list[str] | None = None) -> int:
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s %(message)s")
    ctx = build_context(argv)
    spark = create_spark(ctx.config)
    try:
        return run(ctx, spark)
    finally:
        spark.stop()


if __name__ == "__main__":
    sys.exit(main())
