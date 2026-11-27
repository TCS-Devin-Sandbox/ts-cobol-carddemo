"""READACCT - PySpark conversion of app/jcl/READACCT.jcl.

Source:  app/jcl/READACCT.jcl  (PREDEL EXEC PGM=IEFBR14, STEP05 EXEC PGM=CBACT01C)
         app/cbl/CBACT01C.cbl, app/cpy/CVACT01Y.cpy, app/cpy/CODATECN.cpy, app/asm/COBDATFT.asm
Spec:    testRepo/analysis/TargetSpec/READACCT_target_spec.json
Rules:   testRepo/analysis/BRE/CBACT01C.json (CBACT01C-BR-001..007)

Usage:
  spark-submit --jars <cobrix jars> READACCT.py \
      --acctfile <path> [--acctfile-format ebcdic|text] \
      [--copybook app/cpy/CVACT01Y.cpy] --output-dir <dir>

Outputs (<output-dir>): datasets/AWS.M2.CARDDEMO.ACCTDATA.{PSCOMP,ARRYPS}.txt (one hex line per
record), datasets/AWS.M2.CARDDEMO.ACCTDATA.VBPS.txt (one line per variable record),
sysout/READACCT.STEP05.txt and rc.json - the layout tools/parity/compare.py reads.
"""

import argparse
import json
import sys
from pathlib import Path

from pyspark.sql import DataFrame, SparkSession
from pyspark.sql import functions as F

JOB_NAME = "READACCT"
REPO_ROOT = Path(__file__).resolve().parents[4]
DEFAULT_COPYBOOK = REPO_ROOT / "app" / "cpy" / "CVACT01Y.cpy"

PSCOMP = "AWS.M2.CARDDEMO.ACCTDATA.PSCOMP"   # OUTFILE  DD, LRECL=107 RECFM=FB
ARRYPS = "AWS.M2.CARDDEMO.ACCTDATA.ARRYPS"   # ARRYFILE DD, LRECL=110 RECFM=FB
VBPS = "AWS.M2.CARDDEMO.ACCTDATA.VBPS"       # VBRCFILE DD, LRECL=84  RECFM=VB

# CVACT01Y ACCOUNT-RECORD (RECLN 300) -- spec T-001: (field, offset, length)
ACCOUNT_RECORD_LENGTH = 300
ACCOUNT_RECORD_LAYOUT = [
    ("ACCT_ID", 0, 11),                 # PIC 9(11)
    ("ACCT_ACTIVE_STATUS", 11, 1),      # PIC X(01)
    ("ACCT_CURR_BAL", 12, 12),          # PIC S9(10)V99
    ("ACCT_CREDIT_LIMIT", 24, 12),      # PIC S9(10)V99
    ("ACCT_CASH_CREDIT_LIMIT", 36, 12), # PIC S9(10)V99
    ("ACCT_OPEN_DATE", 48, 10),         # PIC X(10)
    ("ACCT_EXPIRAION_DATE", 58, 10),    # PIC X(10)
    ("ACCT_REISSUE_DATE", 68, 10),      # PIC X(10)
    ("ACCT_CURR_CYC_CREDIT", 78, 12),   # PIC S9(10)V99
    ("ACCT_CURR_CYC_DEBIT", 90, 12),    # PIC S9(10)V99
    ("ACCT_ADDR_ZIP", 102, 10),         # PIC X(10)
    ("ACCT_GROUP_ID", 112, 10),         # PIC X(10)
    ("FILLER", 122, 178),               # PIC X(178)
]
_FIELDS = {name: (offset, length) for name, offset, length in ACCOUNT_RECORD_LAYOUT}

START_BANNER = "START OF EXECUTION OF PROGRAM CBACT01C"
END_BANNER = "END OF EXECUTION OF PROGRAM CBACT01C"
ABEND_CODE = 999  # 9999-ABEND-PROGRAM: MOVE 999 TO ABCODE
# CBACT01C never CLOSEs OUT-FILE, ARRY-FILE or VBRC-FILE; the GnuCOBOL runtime of the legacy
# harness reports this on stderr, which the JCL runner appends to SYSOUT after a blank line.
IMPLICIT_CLOSE_WARNINGS = [
    "libcob: warning: implicit CLOSE of VBRC-FILE ('VBRCFILE')",
    "libcob: warning: implicit CLOSE of ARRY-FILE ('ARRYFILE')",
    "libcob: warning: implicit CLOSE of OUT-FILE ('OUTFILE')",
]

_POS_OVERPUNCH = "{ABCDEFGHI"  # zoned sign nibble C (+), -fsign=EBCDIC
_NEG_OVERPUNCH = "}JKLMNOPQR"  # zoned sign nibble D (-)


class AcctFileError(Exception):
    """Open/read/write failure (CBACT01C 0000/2000/3000/4000-OPEN, 1000-GET-NEXT, 1350/1450/1550/1575-WRITE)."""

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


# ---------------------------------------------------------------- numeric encoders
def zoned_to_cents(text: str) -> int:
    """PIC S9(10)V99 zoned decimal with trailing overpunch sign -> integer hundredths."""
    last = text[-1]
    if last in _POS_OVERPUNCH:
        digit, sign = _POS_OVERPUNCH.index(last), 1
    elif last in _NEG_OVERPUNCH:
        digit, sign = _NEG_OVERPUNCH.index(last), -1
    elif last.isdigit():
        digit, sign = int(last), 1
    else:
        raise ValueError(f"invalid zoned decimal {text!r}")
    head = text[:-1]
    if not head.isdigit():
        raise ValueError(f"invalid zoned decimal {text!r}")
    return sign * (int(head) * 10 + digit)


def zoned(cents: int, digits: int = 12) -> str:
    """MOVE to PIC S9(10)V99 DISPLAY: digits with the sign overpunched on the last one."""
    text = f"{abs(cents):0{digits}d}"[-digits:]
    table = _NEG_OVERPUNCH if cents < 0 else _POS_OVERPUNCH
    return text[:-1] + table[int(text[-1])]


def zoned_initialized(digits: int = 12) -> str:
    """INITIALIZE of a signed DISPLAY item: plain zeros (as the legacy run writes them)."""
    return "0" * digits


def packed(cents: int, digits: int = 12) -> bytes:
    """PIC S9(10)V99 COMP-3: 7 bytes, sign nibble C/D."""
    nibbles = f"{abs(cents):0{digits + (1 - digits % 2)}d}" + ("d" if cents < 0 else "c")
    return bytes.fromhex(nibbles)


def display_signed(cents: int, digits: int = 12) -> str:
    """DISPLAY of PIC S9(10)V99 in the legacy SYSOUT: 12 digits and a trailing '+'/'-'."""
    return f"{abs(cents):0{digits}d}" + ("-" if cents < 0 else "+")


# ---------------------------------------------------------------- COBDATFT (HLASM -> Python)
def cobdatft(in_type: str, inp_date: str, out_type: str, out_date: str = " " * 20):
    """app/asm/COBDATFT.asm over the CODATECN-REC block. Returns (CODATECN-0UT-DATE, CODATECN-ERROR-MSG).

    type '1' (YYYYMMDD) -> YYYY-MM-DD unless input has '-' at position 5 or OUTTYPE = '2';
    type '2' (YYYY-MM-DD) -> YYYYMMDD unless OUTTYPE = '1'; otherwise 'INVALID INPUT'.
    Bytes of CODATECN-0UT-DATE the routine does not move keep their previous content.
    """
    inp = inp_date.ljust(20)[:20]
    out = list(out_date.ljust(20)[:20])
    if in_type == "1" and inp[4] != "-" and out_type != "2":
        out[0:4], out[4], out[5:7], out[7], out[8:10] = inp[0:4], "-", inp[4:6], "-", inp[6:8]
        return "".join(out), None
    if in_type == "2" and out_type != "1":
        out[0:4], out[4:6], out[6:8] = inp[0:4], inp[5:7], inp[8:10]
        return "".join(out), None
    return "".join(out), "INVALID INPUT"


# ---------------------------------------------------------------- ACCTFILE (READ FILE -> spark.read())
def _read_copybook(path: Path) -> str:
    return path.read_text().expandtabs(8)  # app/cpy copybooks are tab-indented (AGENTS.md)


def read_acctfile_ebcdic(spark: SparkSession, acctfile: str, copybook: Path, file_end_offset: int = 0) -> DataFrame:
    """ACCTFILE as an EBCDIC fixed-length 300-byte unload of the KSDS (Cobrix, copybook CVACT01Y)."""
    df = (
        spark.read.format("cobol")
        .option("copybook_contents", _read_copybook(copybook))
        .option("record_format", "F")
        .option("encoding", "ebcdic")
        .option("ebcdic_code_page", "cp037")
        .option("file_end_offset", str(file_end_offset))
        .option("generate_record_bytes", "true")
        .load(acctfile)
    )
    raw = F.decode(F.col("Record_Bytes"), "cp037")
    return df.select(F.substring(raw, 1, 11).alias("ACCT_ID"), raw.alias("record"), F.length("Record_Bytes").alias("record_length"))


def read_acctfile_text(spark: SparkSession, acctfile: str) -> DataFrame:
    """ACCTFILE as ASCII lines (fixtures/read-masters inputs, trailing spaces trimmed)."""
    raw = spark.read.text(acctfile).filter(F.length("value") > 0)
    rec = F.rpad(F.col("value"), ACCOUNT_RECORD_LENGTH, " ")
    return raw.select(F.substring(rec, 1, 11).alias("ACCT_ID"), rec.alias("record"), F.length("value").alias("record_length"))


def acctfile_length(spark: SparkSession, acctfile: str):
    """Byte length via the Hadoop FileSystem (paths and URIs); None when absent."""
    path = spark._jvm.org.apache.hadoop.fs.Path(acctfile)
    fs = path.getFileSystem(spark._jsc.hadoopConfiguration())
    if not fs.exists(path):
        return None
    status = fs.getFileStatus(path)
    if status.isDirectory():
        raise AcctFileError("ERROR OPENING ACCTFILE", f"ACCTFILE is a directory: {acctfile}")
    return status.getLen()


def read_acctfile(spark: SparkSession, acctfile: str, fmt: str, copybook: Path):
    """0000-ACCTFILE-OPEN + 1000-ACCTFILE-GET-NEXT. Returns (DataFrame or None, trailing_bytes)."""
    try:
        length = acctfile_length(spark, acctfile)
    except AcctFileError:
        raise
    except Exception as exc:  # noqa: BLE001 - unusable path maps to the COBOL open error
        raise AcctFileError("ERROR OPENING ACCTFILE", str(exc)) from exc
    if length is None:
        raise AcctFileError("ERROR OPENING ACCTFILE", f"ACCTFILE not found: {acctfile}")
    if fmt not in ("ebcdic", "text"):
        raise ValueError(f"unsupported --acctfile-format {fmt}")
    try:
        if fmt == "ebcdic":
            trailing = length % ACCOUNT_RECORD_LENGTH
            if length == trailing:
                return None, trailing
            return read_acctfile_ebcdic(spark, acctfile, copybook, trailing), trailing
        return read_acctfile_text(spark, acctfile), 0
    except Exception as exc:  # noqa: BLE001
        raise AcctFileError("ERROR OPENING ACCTFILE", str(exc)) from exc


def parse_account(record: str) -> dict:
    """READ ACCTFILE-FILE INTO ACCOUNT-RECORD (CVACT01Y)."""
    acct = {name: record[o:o + n] for name, (o, n) in _FIELDS.items()}
    for name in ("ACCT_CURR_BAL", "ACCT_CREDIT_LIMIT", "ACCT_CASH_CREDIT_LIMIT", "ACCT_CURR_CYC_CREDIT", "ACCT_CURR_CYC_DEBIT"):
        acct[name] = zoned_to_cents(acct[name])
    return acct


# ---------------------------------------------------------------- STEP05 record builders
def account_listing(acct: dict, record: str, vb1: str, vb2: str) -> list:
    """CBACT01C-BR-001: 1100-DISPLAY-ACCT-RECORD, 1500 DISPLAYs, then DISPLAY ACCOUNT-RECORD."""
    return [
        "ACCT-ID                 :" + acct["ACCT_ID"],
        "ACCT-ACTIVE-STATUS      :" + acct["ACCT_ACTIVE_STATUS"],
        "ACCT-CURR-BAL           :" + display_signed(acct["ACCT_CURR_BAL"]),
        "ACCT-CREDIT-LIMIT       :" + display_signed(acct["ACCT_CREDIT_LIMIT"]),
        "ACCT-CASH-CREDIT-LIMIT  :" + display_signed(acct["ACCT_CASH_CREDIT_LIMIT"]),
        "ACCT-OPEN-DATE          :" + acct["ACCT_OPEN_DATE"],
        "ACCT-EXPIRAION-DATE     :" + acct["ACCT_EXPIRAION_DATE"],
        "ACCT-REISSUE-DATE       :" + acct["ACCT_REISSUE_DATE"],
        "ACCT-CURR-CYC-CREDIT    :" + display_signed(acct["ACCT_CURR_CYC_CREDIT"]),
        "ACCT-CURR-CYC-DEBIT     :" + display_signed(acct["ACCT_CURR_CYC_DEBIT"]),
        "ACCT-GROUP-ID           :" + acct["ACCT_GROUP_ID"],
        "-" * 49,
        "VBRC-REC1:" + vb1,
        "VBRC-REC2:" + vb2,
        record,
    ]


class OutRecordArea:
    """OUT-ACCT-REC record area. OUT-ACCT-CURR-CYC-DEBIT is only moved when the input debit is
    zero (CBACT01C-BR-003), so it keeps the previous record's bytes; binary zeros at first."""

    def __init__(self):
        self.curr_cyc_debit = bytes(7)
        self.codatecn_out = " " * 20

    def pscomp_record(self, acct: dict) -> bytes:
        """CBACT01C-BR-002: 1300-POPUL-ACCT-RECORD (+ COBDATFT) -> OUT-ACCT-REC (107 bytes)."""
        self.codatecn_out, _ = cobdatft("2", acct["ACCT_REISSUE_DATE"], "2", self.codatecn_out)
        if acct["ACCT_CURR_CYC_DEBIT"] == 0:
            self.curr_cyc_debit = packed(252500)  # MOVE 2525.00 TO OUT-ACCT-CURR-CYC-DEBIT
        head = (acct["ACCT_ID"] + acct["ACCT_ACTIVE_STATUS"] + zoned(acct["ACCT_CURR_BAL"])
                + zoned(acct["ACCT_CREDIT_LIMIT"]) + zoned(acct["ACCT_CASH_CREDIT_LIMIT"])
                + acct["ACCT_OPEN_DATE"] + acct["ACCT_EXPIRAION_DATE"] + self.codatecn_out[:10]
                + zoned(acct["ACCT_CURR_CYC_CREDIT"]))
        return head.encode("latin-1") + self.curr_cyc_debit + acct["ACCT_GROUP_ID"].encode("latin-1")


def arryps_record(acct: dict) -> bytes:
    """CBACT01C-BR-004: INITIALIZE ARR-ARRAY-REC + 1400-POPUL-ARRAY-RECORD (110 bytes, OCCURS 5)."""
    bal = acct["ACCT_CURR_BAL"]
    entries = [(zoned(bal), 100500), (zoned(bal), 152500), (zoned(-102500), -250000)]
    entries += [(zoned_initialized(), 0)] * 2
    body = b"".join(b.encode("latin-1") + packed(d) for b, d in entries)
    return acct["ACCT_ID"].encode("latin-1") + body + b"    "


def vbrc_records(acct: dict):
    """CBACT01C-BR-005: VBRC-REC1 (12 bytes) and VBRC-REC2 (39 bytes)."""
    vb1 = acct["ACCT_ID"] + acct["ACCT_ACTIVE_STATUS"]
    vb2 = acct["ACCT_ID"] + zoned(acct["ACCT_CURR_BAL"]) + zoned(acct["ACCT_CREDIT_LIMIT"]) + acct["ACCT_REISSUE_DATE"][:4]
    return vb1, vb2


class Datasets:
    """OUTFILE / ARRYFILE / VBRCFILE (DISP=(NEW,CATLG,DELETE)) in the compare.py text serialisation."""

    def __init__(self, output_dir: Path):
        self.dir = output_dir / "datasets"
        self.dir.mkdir(parents=True, exist_ok=True)
        self.files = {}
        for dsn, label in ((PSCOMP, "OUTFILE"), (ARRYPS, "ARRAYFILE"), (VBPS, "VBRC FILE")):
            try:
                self.files[dsn] = open(self.dir / f"{dsn}.txt", "w", encoding="latin-1")
            except OSError as exc:  # 2000/3000/4000-*-OPEN
                self.discard()
                raise AcctFileError(f"ERROR OPENING {label}", str(exc)) from exc

    def write(self, dsn: str, line: str) -> None:
        try:
            self.files[dsn].write(line + "\n")
        except OSError as exc:  # 1350/1450/1550/1575-WRITE-*
            raise AcctFileError("ACCOUNT FILE WRITE STATUS IS:", str(exc)) from exc

    def close(self) -> None:
        for fh in self.files.values():
            fh.close()

    def discard(self) -> None:
        """Abnormal end of STEP05: DISP=(NEW,CATLG,DELETE) deletes the three extracts."""
        for dsn, fh in self.files.items():
            try:
                fh.close()
            except OSError:
                pass
            (self.dir / f"{dsn}.txt").unlink(missing_ok=True)


def abend_program(sysout: Sysout, error: AcctFileError) -> str:
    """9910-DISPLAY-IO-STATUS + 9999-ABEND-PROGRAM (CALL 'CEE3ABD' ABCODE 999)."""
    sysout.display(error.message)
    if error.detail:
        print(f"DETAIL: {error.detail}", file=sys.stderr)
    sysout.display("ABENDING PROGRAM")
    return f"U{ABEND_CODE:04d}"


def step_predel(output_dir: Path) -> int:
    """PREDEL EXEC PGM=IEFBR14, DD01-DD03 DISP=(MOD,DELETE,DELETE): remove PSCOMP, ARRYPS, VBPS."""
    for dsn in (PSCOMP, ARRYPS, VBPS):
        target = output_dir / "datasets" / f"{dsn}.txt"
        if target.exists():
            target.unlink()
    return 0


def step05_cbact01c(spark: SparkSession, args, sysout: Sysout):
    """STEP05 EXEC PGM=CBACT01C.

    //STEPLIB  DD DSN=AWS.M2.CARDDEMO.LOADLIB -> no PySpark equivalent (module path)
    //ACCTFILE DD DSN=AWS.M2.CARDDEMO.ACCTDATA.VSAM.KSDS -> --acctfile
    //OUTFILE / ARRYFILE / VBRCFILE -> <output-dir>/datasets/<DSN>.txt
    //SYSOUT   DD SYSOUT=* -> print() + <output-dir>/sysout/READACCT.STEP05.txt
    """
    sysout.display(START_BANNER)  # CBACT01C-BR-007
    records = 0
    datasets = None
    try:
        acct_df, trailing = read_acctfile(spark, args.acctfile, args.acctfile_format, Path(args.copybook))
        datasets = Datasets(Path(args.output_dir))
        area = OutRecordArea()
        rows = acct_df.orderBy("ACCT_ID").select("record", "record_length").toLocalIterator() if acct_df is not None else []
        try:
            for row in rows:
                if row["record_length"] > ACCOUNT_RECORD_LENGTH:
                    raise AcctFileError("ERROR READING ACCOUNT FILE", f"record length {row['record_length']}")
                try:
                    acct = parse_account(row["record"])
                except ValueError as exc:
                    raise AcctFileError("ERROR READING ACCOUNT FILE", str(exc)) from exc
                vb1, vb2 = vbrc_records(acct)
                for line in account_listing(acct, row["record"], vb1, vb2):
                    sysout.display(line)
                datasets.write(PSCOMP, area.pscomp_record(acct).hex())
                datasets.write(ARRYPS, arryps_record(acct).hex())
                datasets.write(VBPS, vb1)
                datasets.write(VBPS, vb2)
                records += 1
        except AcctFileError:
            raise
        except Exception as exc:  # noqa: BLE001 - Spark read failure maps to the COBOL read error
            raise AcctFileError("ERROR READING ACCOUNT FILE", str(exc)) from exc
        if trailing:
            raise AcctFileError("ERROR READING ACCOUNT FILE", f"{trailing} trailing bytes after {records} records")
    except AcctFileError as err:
        if datasets is not None:
            datasets.discard()
        return abend_program(sysout, err), records
    try:
        datasets.close()  # buffered records are flushed here
    except OSError as exc:
        datasets.discard()
        return abend_program(sysout, AcctFileError("ACCOUNT FILE WRITE STATUS IS:", str(exc))), records
    sysout.display(END_BANNER)  # CBACT01C-BR-007
    sysout.lines += [""] + IMPLICIT_CLOSE_WARNINGS
    return 0, records


def write_outputs(output_dir: Path, sysout: Sysout, steps: dict) -> None:
    (output_dir / "sysout").mkdir(parents=True, exist_ok=True)
    (output_dir / "sysout" / f"{JOB_NAME}.STEP05.txt").write_text("".join(line + "\n" for line in sysout.lines), encoding="latin-1")
    abends = [rc for rc in steps.values() if isinstance(rc, str)]
    maxrc = abends[0] if abends else max(steps.values())
    (output_dir / "rc.json").write_text(json.dumps({JOB_NAME: {"maxrc": maxrc, "steps": steps}}, indent=1, sort_keys=True) + "\n")


def parse_args(argv=None):
    p = argparse.ArgumentParser(description="READACCT (IEFBR14 + CBACT01C) PySpark job")
    p.add_argument("--acctfile", required=True, help="ACCTFILE DD: AWS.M2.CARDDEMO.ACCTDATA.VSAM.KSDS unload")
    p.add_argument("--acctfile-format", choices=["ebcdic", "text"], default="ebcdic")
    p.add_argument("--copybook", default=str(DEFAULT_COPYBOOK))
    p.add_argument("--output-dir", required=True)
    return p.parse_args(argv)


def run(spark: SparkSession, args) -> dict:
    output_dir = Path(args.output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)
    sysout = Sysout()
    steps = {"PREDEL": step_predel(output_dir)}
    steps["STEP05"], records = step05_cbact01c(spark, args, sysout)
    write_outputs(output_dir, sysout, steps)
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
