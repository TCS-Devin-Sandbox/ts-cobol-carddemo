"""READCARD - PySpark conversion of app/jcl/READCARD.jcl.

Source:  app/jcl/READCARD.jcl  (STEP05 EXEC PGM=CBACT02C)
         app/cbl/CBACT02C.cbl, app/cpy/CVACT02Y.cpy
Spec:    testRepo/analysis/TargetSpec/READCARD_target_spec.json
Rules:   testRepo/analysis/BRE/CBACT02C.json (CBACT02C-BR-001..003)

Usage:
  spark-submit --jars <cobrix jars> READCARD.py \
      --cardfile <path> [--cardfile-format ebcdic|text] \
      [--copybook app/cpy/CVACT02Y.cpy] --output-dir <dir>
"""

import argparse
import json
import sys
from pathlib import Path

from pyspark.sql import DataFrame, SparkSession
from pyspark.sql import functions as F
from pyspark.sql.types import IntegerType, LongType, StringType, StructField, StructType

JOB_NAME = "READCARD"
REPO_ROOT = Path(__file__).resolve().parents[4]
DEFAULT_COPYBOOK = REPO_ROOT / "app" / "cpy" / "CVACT02Y.cpy"

# CVACT02Y CARD-RECORD (RECLN 150) -- spec T-001
CARD_RECORD_LENGTH = 150
CARD_RECORD_LAYOUT = [
    # (target field, offset, length)
    ("CARD_NUM", 0, 16),             # PIC X(16)
    ("CARD_ACCT_ID", 16, 11),        # PIC 9(11)
    ("CARD_CVV_CD", 27, 3),          # PIC 9(03)
    ("CARD_EMBOSSED_NAME", 30, 50),  # PIC X(50)
    ("CARD_EXPIRAION_DATE", 80, 10), # PIC X(10)
    ("CARD_ACTIVE_STATUS", 90, 1),   # PIC X(01)
    ("FILLER", 91, 59),              # PIC X(59)
]
CARD_RECORD_SCHEMA = StructType([
    StructField("CARD_NUM", StringType()),
    StructField("CARD_ACCT_ID", LongType()),
    StructField("CARD_CVV_CD", IntegerType()),
    StructField("CARD_EMBOSSED_NAME", StringType()),
    StructField("CARD_EXPIRAION_DATE", StringType()),
    StructField("CARD_ACTIVE_STATUS", StringType()),
    StructField("FILLER", StringType()),
])
_NUMERIC_TYPES = {"CARD_ACCT_ID": LongType(), "CARD_CVV_CD": IntegerType()}

START_BANNER = "START OF EXECUTION OF PROGRAM CBACT02C"
END_BANNER = "END OF EXECUTION OF PROGRAM CBACT02C"
ABEND_CODE = 999  # 9999-ABEND-PROGRAM: MOVE 999 TO ABCODE


class CardFileError(Exception):
    """Open/read failure on CARDFILE (CBACT02C 0000-CARDFILE-OPEN / 1000-CARDFILE-GET-NEXT)."""

    def __init__(self, message: str, detail: str = ""):
        super().__init__(message)
        self.message = message
        self.detail = detail


class Sysout:
    """SYSOUT DD: every DISPLAY is printed and kept in order for the sysout file."""

    def __init__(self):
        self.lines = []

    def display(self, line: str) -> None:
        print(line, flush=True)
        self.lines.append(line)


def _read_copybook(path: Path) -> str:
    # Copybooks in app/cpy are tab-indented (AGENTS.md); Cobrix needs spaces.
    return path.read_text().expandtabs(8)


def read_cardfile_ebcdic(spark: SparkSession, cardfile: str, copybook: Path, file_end_offset: int = 0) -> DataFrame:
    """CARDFILE as an EBCDIC fixed-length 150-byte unload of the KSDS (Cobrix)."""
    df = (
        spark.read.format("cobol")
        .option("file_end_offset", str(file_end_offset))
        .option("copybook_contents", _read_copybook(copybook))
        .option("record_format", "F")
        .option("encoding", "ebcdic")
        .option("ebcdic_code_page", "cp037")
        .option("drop_value_fillers", "false")
        .option("generate_record_bytes", "true")
        .load(cardfile)
    )
    # DISPLAY CARD-RECORD prints the raw group bytes; keep them as display_line.
    return df.select(
        F.col("CARD_NUM"),
        F.col("CARD_ACCT_ID"),
        F.col("CARD_CVV_CD"),
        F.col("CARD_EMBOSSED_NAME"),
        F.col("CARD_EXPIRAION_DATE"),
        F.col("CARD_ACTIVE_STATUS"),
        F.decode(F.col("Record_Bytes"), "cp037").alias("display_line"),
        F.length("Record_Bytes").alias("record_length"),
    )


def read_cardfile_text(spark: SparkSession, cardfile: str) -> DataFrame:
    """CARDFILE as ASCII lines (fixtures/read-masters inputs, trailing spaces trimmed)."""
    raw = spark.read.text(cardfile).filter(F.length("value") > 0)
    rec = F.rpad(F.col("value"), CARD_RECORD_LENGTH, " ")
    cols = []
    for name, offset, length in CARD_RECORD_LAYOUT:
        if name == "FILLER":
            continue
        col = F.substring(rec, offset + 1, length)
        if name in _NUMERIC_TYPES:
            col = col.cast(_NUMERIC_TYPES[name])
        else:
            col = F.rtrim(col)
        cols.append(col.alias(name))
    return raw.select(*cols, rec.alias("display_line"), F.length(F.col("value")).alias("record_length"))


def cardfile_length(spark: SparkSession, cardfile: str):
    """Byte length of CARDFILE via the Hadoop FileSystem (paths and URIs), None when absent."""
    jvm = spark._jvm
    path = jvm.org.apache.hadoop.fs.Path(cardfile)
    fs = path.getFileSystem(spark._jsc.hadoopConfiguration())
    if not fs.exists(path):
        return None
    status = fs.getFileStatus(path)
    if status.isDirectory():
        # CARDFILE DD names one dataset (the KSDS unload), not a directory of files
        raise CardFileError("ERROR OPENING CARDFILE", f"CARDFILE is a directory: {cardfile}")
    return status.getLen()


def read_cardfile(spark: SparkSession, cardfile: str, fmt: str, copybook: Path):
    """0000-CARDFILE-OPEN + 1000-CARDFILE-GET-NEXT (READ FILE -> spark.read()).

    Returns (DataFrame or None, trailing_bytes). trailing_bytes > 0 means the EBCDIC
    unload ends inside a record: the complete records are still listed, then the read fails.
    """
    try:
        length = cardfile_length(spark, cardfile)
    except CardFileError:
        raise
    except Exception as exc:  # noqa: BLE001 - unusable path maps to the COBOL open error
        raise CardFileError("ERROR OPENING CARDFILE", str(exc)) from exc
    if length is None:
        raise CardFileError("ERROR OPENING CARDFILE", f"CARDFILE not found: {cardfile}")
    try:
        if fmt == "ebcdic":
            trailing = length % CARD_RECORD_LENGTH
            if length - trailing == 0:
                return None, trailing
            return read_cardfile_ebcdic(spark, cardfile, copybook, trailing), trailing
        if fmt == "text":
            return read_cardfile_text(spark, cardfile), 0
    except CardFileError:
        raise
    except Exception as exc:  # noqa: BLE001 - any open failure maps to the COBOL open error
        raise CardFileError("ERROR OPENING CARDFILE", str(exc)) from exc
    raise ValueError(f"unsupported --cardfile-format {fmt}")


def display_card_records(cardfile_df: DataFrame, sysout: Sysout) -> int:
    """CBACT02C-BR-001: DISPLAY CARD-RECORD for every record, in CARD-NUM (KSDS key) order."""
    count = 0
    try:
        ordered = cardfile_df.orderBy("CARD_NUM").select("display_line", "record_length")
        for row in ordered.toLocalIterator():
            if row["record_length"] > CARD_RECORD_LENGTH:
                # CBACT02C-BR-002: record cannot be read as a 150-byte CARD-RECORD
                raise CardFileError("ERROR READING CARDFILE", f"record length {row['record_length']}")
            sysout.display(row["display_line"])
            count += 1
    except CardFileError:
        raise
    except Exception as exc:  # noqa: BLE001 - any read failure maps to the COBOL read error
        raise CardFileError("ERROR READING CARDFILE", str(exc)) from exc
    return count


def abend_program(sysout: Sysout, error: CardFileError) -> str:
    """9910-DISPLAY-IO-STATUS + 9999-ABEND-PROGRAM (CALL 'CEE3ABD' ABCODE 999)."""
    sysout.display(error.message)
    if error.detail:
        print(f"DETAIL: {error.detail}", file=sys.stderr)
    sysout.display("ABENDING PROGRAM")
    return f"U{ABEND_CODE:04d}"


def step05_cbact02c(spark: SparkSession, args, sysout: Sysout):
    """STEP05 EXEC PGM=CBACT02C.

    //STEPLIB  DD DSN=AWS.M2.CARDDEMO.LOADLIB  -> no PySpark equivalent (module path)
    //CARDFILE DD DSN=AWS.M2.CARDDEMO.CARDDATA.VSAM.KSDS -> --cardfile
    //SYSOUT   DD SYSOUT=*  -> print() + <output-dir>/sysout/READCARD.STEP05.txt
    //SYSPRINT DD SYSOUT=*  -> not written by CBACT02C
    """
    sysout.display(START_BANNER)  # CBACT02C-BR-003
    try:
        cardfile_df, trailing = read_cardfile(spark, args.cardfile, args.cardfile_format, Path(args.copybook))
        records = display_card_records(cardfile_df, sysout) if cardfile_df is not None else 0
        if trailing:
            # CBACT02C-BR-002: last record is shorter than the 150-byte CARD-RECORD
            raise CardFileError("ERROR READING CARDFILE", f"{trailing} trailing bytes after {records} records")
    except CardFileError as err:
        return abend_program(sysout, err), 0
    sysout.display(END_BANNER)  # CBACT02C-BR-003
    return 0, records


def write_outputs(output_dir: Path, sysout: Sysout, steps: dict) -> None:
    (output_dir / "sysout").mkdir(parents=True, exist_ok=True)
    (output_dir / "sysout" / f"{JOB_NAME}.STEP05.txt").write_text(
        "".join(line + "\n" for line in sysout.lines)
    )
    numeric = [rc for rc in steps.values() if isinstance(rc, int)]
    abends = [rc for rc in steps.values() if isinstance(rc, str)]
    maxrc = abends[0] if abends else max(numeric, default=0)
    (output_dir / "rc.json").write_text(
        json.dumps({JOB_NAME: {"maxrc": maxrc, "steps": steps}}, indent=1, sort_keys=True) + "\n"
    )


def parse_args(argv=None):
    p = argparse.ArgumentParser(description="READCARD (CBACT02C) PySpark job")
    p.add_argument("--cardfile", required=True, help="CARDFILE DD: AWS.M2.CARDDEMO.CARDDATA.VSAM.KSDS unload")
    p.add_argument("--cardfile-format", choices=["ebcdic", "text"], default="ebcdic")
    p.add_argument("--copybook", default=str(DEFAULT_COPYBOOK))
    p.add_argument("--output-dir", required=True)
    return p.parse_args(argv)


def run(spark: SparkSession, args) -> dict:
    sysout = Sysout()
    rc, records = step05_cbact02c(spark, args, sysout)
    steps = {"STEP05": rc}
    write_outputs(Path(args.output_dir), sysout, steps)
    return {"steps": steps, "records": records, "sysout": sysout.lines}


def main(argv=None) -> int:
    args = parse_args(argv)
    spark = SparkSession.builder.appName(JOB_NAME).getOrCreate()
    try:
        result = run(spark, args)
    finally:
        spark.stop()
    return 0 if result["steps"]["STEP05"] == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
