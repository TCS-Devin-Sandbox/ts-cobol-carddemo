#!/usr/bin/env python3
"""Scan the CardDemo ``app/`` tree and write a self-contained HTML blueprint.

The report covers the component inventory (Batch / Online / Shared), lines of
code per file and per component type, a Low/Medium/High complexity band per
program, the data resources the application touches (DB2, IMS, GDGs and
datasets by access method) and a full file listing.

Python 3 standard library only.  Usage::

    python3 tools/blueprint/generate_blueprint.py            # -> docs/blueprint.html
    python3 tools/blueprint/generate_blueprint.py --app-dir app --output out.html
"""

from __future__ import annotations

import argparse
import datetime as dt
import html
import math
import os
import re
import sys
from collections import Counter, defaultdict
from dataclasses import dataclass, field

# --------------------------------------------------------------------------
# Component types
# --------------------------------------------------------------------------

EXT_TYPES = {
    ".cbl": "COBOL",
    ".pli": "PL/I",
    ".jcl": "JCL",
    ".prc": "PROC",
    ".cpy": "Copybook",
    ".bms": "BMS",
    ".asm": "ASM",
    ".mac": "ASM Macro",
    ".csd": "CSD",
    ".ctl": "Control Card",
    ".dcl": "DCLGEN",
    ".ddl": "DDL",
    ".dbd": "IMS DBD",
    ".psb": "IMS PSB",
    ".ca7": "Scheduler",
    ".controlm": "Scheduler",
}

TYPE_ORDER = [
    "COBOL", "PL/I", "ASM", "JCL", "PROC", "Copybook", "BMS Copybook", "BMS",
    "CSD", "ASM Macro", "Control Card", "DCLGEN", "DDL", "IMS DBD", "IMS PSB",
    "Scheduler", "Other",
]

TYPE_COLORS = {
    "COBOL": "#1f77b4", "PL/I": "#ff7f0e", "ASM": "#8c564b", "JCL": "#2ca02c",
    "PROC": "#98df8a", "Copybook": "#9467bd", "BMS Copybook": "#c5b0d5",
    "BMS": "#d62728", "CSD": "#ff9896", "ASM Macro": "#c49c94",
    "Control Card": "#bcbd22", "DCLGEN": "#17becf", "DDL": "#9edae5",
    "IMS DBD": "#e377c2", "IMS PSB": "#f7b6d2", "Scheduler": "#7f7f7f",
    "Other": "#c7c7c7",
}

PROGRAM_TYPES = {"COBOL", "PL/I", "ASM"}

SKIP_DIRS = {"data", "catlg"}
SKIP_EXTS = {".md", ".gitkeep", ".txt", ".dat"}

# --------------------------------------------------------------------------
# Complexity heuristic (documented in the report footer)
# --------------------------------------------------------------------------

LOC_LOW, LOC_HIGH = 500, 1500          # <500 Low, 500-1500 Medium, >1500 High
DP_LOW, DP_HIGH = 50, 150              # <50 Low, 50-150 Medium, >150 High
BAND_BY_SCORE = {2: "Low", 3: "Low", 4: "Medium", 5: "High", 6: "High"}
BANDS = ["Low", "Medium", "High"]

DP_KEYS = ["IF", "EVALUATE", "WHEN", "PERFORM", "GO TO", "EXEC CICS", "EXEC SQL", "EXEC DLI"]


def kw(word: str) -> re.Pattern:
    word = r"\s+".join(word.split())
    return re.compile(r"(?<![\w-])" + word + r"(?![\w-])")


COBOL_DP = {k: kw(k) for k in DP_KEYS}
PLI_DP = {
    "IF": kw("IF"), "SELECT": kw("SELECT"), "WHEN": kw("WHEN"),
    "DO WHILE/UNTIL": re.compile(r"(?<![\w-])DO\s+(WHILE|UNTIL)(?![\w-])"),
    "GO TO": re.compile(r"(?<![\w-])GO\s*TO(?![\w-])"), "ON": kw("ON"),
    "EXEC CICS": kw("EXEC CICS"), "EXEC SQL": kw("EXEC SQL"),
}
ASM_BRANCHES = {
    "BC", "BCR", "BE", "BNE", "BH", "BL", "BNH", "BNL", "BZ", "BNZ", "BM", "BNM",
    "BO", "BNO", "BCT", "BCTR", "BXH", "BXLE", "BRC", "BRCT", "JE", "JNE", "JH",
    "JL", "JZ", "JNZ", "JNH", "JNL", "JM", "JNM", "JO", "JNO", "JCT", "AIF", "AGO",
}

MQ_RE = re.compile(r"CALL\s+'(MQ[A-Z0-9]+)'|(?<![\w-])(MQOPEN|MQPUT1?|MQGET|MQCONN|MQCLOSE|MQDISC)(?![\w-])")
IMS_CALL_RE = re.compile(r"CALL\s+'(CBLTDLI|PLITDLI|AIBTDLI|AERTDLI)'", re.I)
STATIC_CALL_RE = re.compile(r"(?<![\w-])CALL\s+'([A-Z0-9#@$-]+)'")
CICS_PGM_RE = re.compile(r"EXEC\s+CICS\s+(?:LINK|XCTL|LOAD)\b[^.]*?PROGRAM\s*\(\s*'([A-Z0-9#@$]+)'")
COPY_RE = re.compile(r"(?<![\w-])COPY\s+'?([A-Z0-9#@$-]+)'?")
SQL_INCLUDE_RE = re.compile(r"EXEC\s+SQL\s+INCLUDE\s+([A-Z0-9#@$-]+)")
LITERAL_RE = re.compile(r"'[^'\n]*'|\"[^\"\n]*\"")


@dataclass
class FileInfo:
    path: str              # relative to repo root
    name: str              # member name, upper case, no extension
    ctype: str
    feature: str
    physical: int = 0
    blank: int = 0
    comment: int = 0
    loc: int = 0
    code: list = field(default_factory=list)   # code lines (content area)
    raw: str = ""
    flags: set = field(default_factory=set)
    dp: dict = field(default_factory=dict)
    dp_total: int = 0
    loc_pts: int = 0
    dp_pts: int = 0
    score: int = 0
    band: str = ""
    category: str = ""
    reason: str = ""
    calls: set = field(default_factory=set)
    copies: set = field(default_factory=set)
    selects: list = field(default_factory=list)

    @property
    def is_program(self) -> bool:
        return self.ctype in PROGRAM_TYPES


# --------------------------------------------------------------------------
# LOC counting per language
# --------------------------------------------------------------------------

def count_cobol(fi: FileInfo, lines: list) -> None:
    for line in lines:
        line = line.expandtabs(8).rstrip("\r\n")
        indicator = line[6] if len(line) > 6 else " "
        content = line[7:72]
        if indicator in "*/":
            fi.comment += 1
        elif not content.strip():
            fi.blank += 1
        elif content.lstrip().startswith("*>"):
            fi.comment += 1
        else:
            fi.loc += 1
            fi.code.append(content.upper())


def count_jcl(fi: FileInfo, lines: list) -> None:
    for line in lines:
        line = line.rstrip("\r\n")
        if not line.strip():
            fi.blank += 1
        elif line.startswith("//*"):
            fi.comment += 1
        else:
            fi.loc += 1
            fi.code.append(line[:72].upper())


def count_c_style(fi: FileInfo, lines: list, dash_comments: bool = False) -> None:
    in_comment = False
    for line in lines:
        line = line.rstrip("\r\n")
        if not line.strip():
            fi.blank += 1
            continue
        out, i = [], 0
        while i < len(line):
            if in_comment:
                end = line.find("*/", i)
                if end < 0:
                    i = len(line)
                else:
                    in_comment, i = False, end + 2
            else:
                start = line.find("/*", i)
                seg = line[i:] if start < 0 else line[i:start]
                if dash_comments and "--" in seg:
                    out.append(seg[: seg.index("--")])
                    break
                out.append(seg)
                if start < 0:
                    break
                in_comment, i = True, start + 2
        text = "".join(out)
        if text.strip():
            fi.loc += 1
            fi.code.append(text.upper())
        else:
            fi.comment += 1


def count_asm(fi: FileInfo, lines: list) -> None:
    for line in lines:
        line = line.rstrip("\r\n")
        if not line.strip():
            fi.blank += 1
        elif line.startswith("*") or line.startswith(".*"):
            fi.comment += 1
        else:
            fi.loc += 1
            fi.code.append(line[:71].upper())


def count_star(fi: FileInfo, lines: list) -> None:
    for line in lines:
        line = line.rstrip("\r\n")
        if not line.strip():
            fi.blank += 1
        elif line.lstrip().startswith("*") or line.lstrip().startswith("<!--"):
            fi.comment += 1
        else:
            fi.loc += 1
            fi.code.append(line.upper())


COUNTERS = {
    "COBOL": count_cobol, "Copybook": count_cobol, "BMS Copybook": count_cobol,
    "DCLGEN": count_cobol, "JCL": count_jcl, "PROC": count_jcl,
    "PL/I": count_c_style,
    "DDL": lambda f, l: count_c_style(f, l, True),
    "Control Card": lambda f, l: count_c_style(f, l, True),
    "ASM": count_asm, "ASM Macro": count_asm, "BMS": count_asm,
    "IMS DBD": count_asm, "IMS PSB": count_asm,
    "CSD": count_star, "Scheduler": count_star, "Other": count_star,
}

# --------------------------------------------------------------------------
# Discovery
# --------------------------------------------------------------------------


def classify_type(rel_parts: list, ext: str) -> str | None:
    if ext in SKIP_EXTS or rel_parts[-1].lower() in SKIP_EXTS or any(p in SKIP_DIRS for p in rel_parts[:-1]):
        return None
    ctype = EXT_TYPES.get(ext)
    if ctype == "Copybook" and "cpy-bms" in rel_parts:
        return "BMS Copybook"
    return ctype or "Other"


def discover(repo: str, app_dir: str) -> list:
    files = []
    for root, dirs, names in os.walk(app_dir):
        dirs.sort()
        for n in sorted(names):
            full = os.path.join(root, n)
            rel_app = os.path.relpath(full, app_dir)
            parts = rel_app.split(os.sep)
            ext = os.path.splitext(n)[1].lower()
            ctype = classify_type(parts, ext)
            if ctype is None:
                continue
            feature = parts[0] if parts[0].startswith("app-") and len(parts) > 1 else "core"
            with open(full, encoding="latin-1") as fh:
                raw = fh.read()
            lines = raw.splitlines()
            fi = FileInfo(
                path=os.path.relpath(full, repo).replace(os.sep, "/"),
                name=os.path.splitext(n)[0].upper(), ctype=ctype, feature=feature,
                physical=len(lines), raw=raw,
            )
            COUNTERS[ctype](fi, lines)
            files.append(fi)
    return files


# --------------------------------------------------------------------------
# Program analysis
# --------------------------------------------------------------------------


def analyse_program(fi: FileInfo) -> None:
    text = "\n".join(fi.code)
    stripped = LITERAL_RE.sub("''", text)
    if fi.ctype == "COBOL":
        fi.dp = {k: len(p.findall(stripped)) for k, p in COBOL_DP.items()}
    elif fi.ctype == "PL/I":
        fi.dp = {k: len(p.findall(stripped)) for k, p in PLI_DP.items()}
    else:  # ASM: conditional branches in the opcode field
        n = 0
        for line in fi.code:
            toks = line.split()
            if not toks:
                continue
            op = toks[0] if line[:1] == " " else (toks[1] if len(toks) > 1 else "")
            if op in ASM_BRANCHES:
                n += 1
        fi.dp = {"Branches": n, "EXEC CICS": len(COBOL_DP["EXEC CICS"].findall(stripped))}
    fi.dp_total = sum(fi.dp.values())
    fi.loc_pts = 1 if fi.loc < LOC_LOW else (2 if fi.loc <= LOC_HIGH else 3)
    fi.dp_pts = 1 if fi.dp_total < DP_LOW else (2 if fi.dp_total <= DP_HIGH else 3)
    fi.score = fi.loc_pts + fi.dp_pts
    fi.band = BAND_BY_SCORE[fi.score]

    if re.search(r"EXEC\s+CICS", stripped) or (fi.ctype == "ASM" and re.search(r"\bDFH[A-Z]+\b", stripped)):
        fi.flags.add("CICS")
    if re.search(r"EXEC\s+SQL", stripped):
        fi.flags.add("SQL")
    if re.search(r"EXEC\s+DLI", stripped) or IMS_CALL_RE.search(text):
        fi.flags.add("IMS")
    if MQ_RE.search(text):
        fi.flags.add("MQ")
    fi.calls = {m for m in STATIC_CALL_RE.findall(text)} | set(CICS_PGM_RE.findall(text))
    fi.calls -= {"CBLTDLI", "PLITDLI", "AIBTDLI", "AERTDLI", fi.name}
    fi.calls = {c for c in fi.calls if not c.startswith("MQ")}
    if fi.ctype == "PL/I":
        fi.calls |= set(re.findall(r"(?<![\w-])CALL\s+([A-Z0-9_#@$]+)", stripped)) - {"PLITDLI"}
    fi.copies = set(COPY_RE.findall(text)) | set(SQL_INCLUDE_RE.findall(text))
    fi.copies -= {"SQLCA", "SQLDA"}
    if fi.ctype == "COBOL":
        fi.selects = parse_selects(text)


def parse_selects(text: str) -> list:
    m = re.search(r"FILE-CONTROL\s*\.(.*?)(?:DATA\s+DIVISION|I-O-CONTROL)", text, re.S)
    if not m:
        return []
    out = []
    for clause in re.split(r"\.\s", " ".join(m.group(1).split())):
        sm = re.search(r"SELECT\s+(?:OPTIONAL\s+)?([\w-]+)\s+ASSIGN\s+(?:TO\s+)?([\w-]+)", clause)
        if not sm:
            continue
        dd = sm.group(2)
        pm = re.match(r"^(?:UT|DA|AS)-(?:S-)?(.+)$", dd)
        dd = pm.group(1) if pm else dd
        om = re.search(r"ORGANIZATION\s+(?:IS\s+)?(LINE\s+SEQUENTIAL|SEQUENTIAL|INDEXED|RELATIVE)", clause)
        org = om.group(1) if om else "SEQUENTIAL"
        am = re.search(r"ACCESS\s+(?:MODE\s+)?(?:IS\s+)?(SEQUENTIAL|RANDOM|DYNAMIC)", clause)
        method = {"INDEXED": "VSAM", "RELATIVE": "VSAM"}.get(org, "QSAM")
        detail = {"INDEXED": "KSDS", "RELATIVE": "RRDS"}.get(org, "PS")
        out.append({"file": sm.group(1), "dd": dd, "org": org,
                    "access": am.group(1) if am else "SEQUENTIAL",
                    "method": method, "detail": detail})
    return out


# --------------------------------------------------------------------------
# JCL parsing
# --------------------------------------------------------------------------


def split_operands(s: str) -> list:
    """Split a JCL operand string on top-level commas."""
    parts, depth, cur, q = [], 0, [], False
    for ch in s:
        if ch == "'":
            q = not q
        elif not q and ch == "(":
            depth += 1
        elif not q and ch == ")":
            depth -= 1
        if ch == "," and depth == 0 and not q:
            parts.append("".join(cur))
            cur = []
        else:
            cur.append(ch)
    if cur:
        parts.append("".join(cur))
    return parts


def operand_field(s: str) -> str:
    out, q, depth = [], False, 0
    for ch in s:
        if ch == "'":
            q = not q
        if ch == " " and not q:
            break
        out.append(ch)
    return "".join(out)


def kv(operands: list) -> dict:
    d = {}
    for p in operands:
        if "=" in p:
            k, v = p.split("=", 1)
            d[k.strip().upper()] = v.strip()
    return d


def substitute(value: str, symbols: dict) -> str:
    def rep(m):
        return symbols.get(m.group(1), m.group(0))
    return re.sub(r"(?<!&)&([A-Z0-9@#$]+)\.?", rep, value)


def parse_jcl(fi: FileInfo) -> dict:
    """Return steps with DDs and in-stream data for a JCL/PROC member."""
    lines = [l.rstrip("\r\n")[:72] for l in fi.raw.splitlines()]
    stmts, instream = [], None
    cur = None
    for line in lines:
        if instream is not None:
            if line.startswith("/*") or line.startswith("//"):
                instream = None
                if line.startswith("/*"):
                    continue
            else:
                instream.append(line)
                continue
        if line.startswith("//*") or not line.startswith("//"):
            continue
        body = line[2:]
        if body[:1] == " " and cur is not None and cur["cont"]:
            ops = operand_field(body.strip())
            cur["operands"] += ops
            cur["cont"] = ops.endswith(",")
            continue
        m = re.match(r"^(\S*)\s+(\S+)\s*(.*)$", body)
        if not m:
            continue
        name, op, rest = m.group(1), m.group(2).upper(), m.group(3)
        ops = operand_field(rest.strip())
        cur = {"name": name.upper(), "op": op, "operands": ops, "cont": ops.endswith(","), "instream": None}
        stmts.append(cur)
        if op == "DD" and (ops.split(",")[0] in ("*", "DATA") or ops.startswith("*")):
            cur["instream"] = instream = []

    symbols: dict = {}
    steps, step, last_dd, job_dds = [], None, "", []
    for s in stmts:
        ops = split_operands(s["operands"])
        params = kv(ops)
        if s["op"] in ("SET", "PROC"):
            for k, v in params.items():
                symbols[k] = substitute(v.strip("'"), symbols)
            continue
        if s["op"] == "EXEC":
            pgm = params.get("PGM", "").upper()
            proc = params.get("PROC", "") or (ops[0] if ops and "=" not in ops[0] else "")
            step = {"name": s["name"], "pgm": pgm, "proc": proc.upper(),
                    "parm": substitute(params.get("PARM", ""), symbols).strip("'()"),
                    "dds": []}
            steps.append(step)
            continue
        if s["op"] == "DD" and step is None:
            dsn = params.get("DSN") or params.get("DSNAME") or ""
            if dsn:
                job_dds.append({"dd": s["name"] or last_dd, "dsn": substitute(dsn, symbols).upper()})
            last_dd = s["name"] or last_dd
            continue
        if s["op"] == "DD" and step is not None:
            ddname = s["name"] or last_dd
            last_dd = ddname
            dsn = params.get("DSN") or params.get("DSNAME") or ""
            step["dds"].append({
                "dd": ddname, "dsn": substitute(dsn, symbols).upper(),
                "disp": params.get("DISP", ""), "instream": s["instream"] or [],
                "dummy": "DUMMY" in ops, "sysout": "SYSOUT" in params,
            })
    return {"steps": steps, "symbols": symbols, "job_dds": job_dds}


GEN_RE = re.compile(r"^(.*)\(([+-]?\d+)\)$")
MEMBER_RE = re.compile(r"^(.*)\(([A-Z@#$][A-Z0-9@#$]*)\)$")
LIB_DDS = {"STEPLIB", "JOBLIB", "SYSLIB", "DFSRESLB", "IMS", "SYSPROC", "SYSEXEC",
           "DFSVSAMP", "PROCLIB", "IMSDALIB", "DSNLOAD", "DBRMLIB"}
SYSTEM_VSAM_DDS = {"DFHCSD": "CICS CSD", "RECON1": "IMS RECON", "RECON2": "IMS RECON", "RECON3": "IMS RECON"}


def instream_text(lines: list) -> str:
    text = "\n".join(lines).upper()
    text = re.sub(r"/\*.*?\*/", " ", text, flags=re.S)
    return re.sub(r"-\s*\n", " ", text)


def parse_idcams_defines(text: str) -> list:
    out = []
    chunks = re.split(r"(?=(?<![\w-])(?:DEFINE|DELETE|REPRO|LISTCAT|PRINT|ALTER|VERIFY)(?![\w-]))", text)
    for c in chunks:
        m = re.match(r"DEFINE\s+(CLUSTER|ALTERNATEINDEX|AIX|PATH|GENERATIONDATAGROUP|GDG|NONVSAM)\s*\(\s*NAME\s*\(\s*([^)\s]+)\s*\)(.*)", c, re.S)
        if not m:
            continue
        kind, name, rest = m.group(1), m.group(2), m.group(3)
        if kind == "CLUSTER":
            detail = ("ESDS" if re.search(r"\bNONINDEXED\b", rest) else
                      "RRDS" if re.search(r"\bNUMBERED\b", rest) else
                      "LDS" if re.search(r"\bLINEAR\b", rest) else "KSDS")
        elif kind in ("ALTERNATEINDEX", "AIX"):
            detail = "AIX"
        elif kind == "PATH":
            detail = "PATH"
        elif kind in ("GENERATIONDATAGROUP", "GDG"):
            detail = "GDG"
            lm = re.search(r"LIMIT\s*\(\s*(\d+)\s*\)", rest)
            detail = "GDG" + (f" LIMIT({lm.group(1)})" if lm else "")
        else:
            detail = "NONVSAM"
        out.append((kind, name, detail))
    return out


SQL_TABLE_RE = re.compile(
    r"(?<![\w-])(?:FROM|INTO|UPDATE|JOIN|LOCK\s+TABLE)\s+([A-Z][A-Z0-9_#@$]*(?:\.[A-Z][A-Z0-9_#@$]*)?)")
SQL_DECLARE_TABLE_RE = re.compile(r"DECLARE\s+([A-Z][A-Z0-9_#@$]*(?:\.[A-Z][A-Z0-9_#@$]*)?)\s+TABLE")
SQL_CREATE_RE = re.compile(
    r"CREATE\s+(?:UNIQUE\s+)?(TABLE|VIEW|INDEX|TABLESPACE|DATABASE|ALIAS|SYNONYM)\s+([A-Z][A-Z0-9_#@$]*(?:\.[A-Z][A-Z0-9_#@$]*)?)"
    r"(?:\s+ON\s+([A-Z][A-Z0-9_#@$]*(?:\.[A-Z][A-Z0-9_#@$]*)?))?")
SQL_NOT_TABLES = {"SQLCA", "SQLDA", "TABLE", "FINAL", "SELECT", "VALUES", "DUAL", "ON"}


def sql_verb(stmt: str) -> str:
    m = re.match(r"\s*(DECLARE\s+\S+\s+CURSOR|SELECT|INSERT|UPDATE|DELETE|MERGE|FETCH|DECLARE|CREATE|LOCK)", stmt)
    if not m:
        return "OTHER"
    v = m.group(1)
    return "SELECT" if v.startswith("DECLARE") and "CURSOR" in v else v.split()[0]


# --------------------------------------------------------------------------
# Model build
# --------------------------------------------------------------------------


class Model:
    def __init__(self, repo: str, app_dir: str):
        self.repo, self.app_dir = repo, app_dir
        self.files = discover(repo, app_dir)
        self.by_name = defaultdict(list)
        for f in self.files:
            self.by_name[f.name].append(f)
        self.programs = [f for f in self.files if f.is_program]
        for p in self.programs:
            analyse_program(p)
        for f in self.files:
            if f.ctype == "BMS":
                f.flags.add("CICS")
            if f.ctype in ("DCLGEN", "DDL"):
                f.flags.add("SQL")
            if f.ctype in ("IMS DBD", "IMS PSB"):
                f.flags.add("IMS")
            if f.ctype in ("Copybook", "DCLGEN") and re.search(r"EXEC\s+SQL", "\n".join(f.code)):
                f.flags.add("SQL")
        self.jcl = {f.path: parse_jcl(f) for f in self.files if f.ctype in ("JCL", "PROC")}
        self.prog_names = {p.name for p in self.programs}
        self._invocations()
        self._classify()
        self._data()

    # ---- invocations ------------------------------------------------------
    def _invocations(self):
        self.invoked_by = defaultdict(set)     # program -> {jcl paths}
        self.utility_use = defaultdict(set)    # utility -> {jcl paths}
        self.step_programs = []                # (jcl path, step, app program or None, psb)
        self.psb_jcl = defaultdict(set)
        for path, j in self.jcl.items():
            for st in j["steps"]:
                pgm, targets, psb = st["pgm"], [], ""
                if pgm in ("IKJEFT01", "IKJEFT1A", "IKJEFT1B"):
                    for dd in st["dds"]:
                        for prog in re.findall(r"RUN\s+PROGRAM\s*\(\s*([A-Z0-9#@$]+)\s*\)", instream_text(dd["instream"])):
                            targets.append(prog)
                elif pgm in ("DFSRRC00", "DFSRRC0"):
                    parts = [x.strip().upper() for x in st["parm"].split(",")]
                    if len(parts) >= 2 and parts[0] in ("DLI", "BMP", "DBB"):
                        targets.append(parts[1])
                        psb = parts[2] if len(parts) > 2 else ""
                        if psb:
                            self.psb_jcl[psb].add(path)
                if pgm:
                    if pgm in self.prog_names:
                        self.invoked_by[pgm].add(path)
                    else:
                        self.utility_use[pgm].add(path)
                for t in targets:
                    if t in self.prog_names:
                        self.invoked_by[t].add(path)
                    else:
                        self.utility_use[t + f" (via {pgm})"].add(path)
                app_pgms = [x for x in ([pgm] + targets) if x in self.prog_names]
                st["app_programs"] = app_pgms
                st["psb"] = psb
        self.callers = defaultdict(set)
        for p in self.programs:
            for c in p.calls:
                self.callers[c].add(p.name)

    # ---- batch / online / shared -----------------------------------------
    def _classify(self):
        cat = {}
        for p in self.programs:
            if "CICS" in p.flags:
                cat[p.name] = ("Online", "contains EXEC CICS")
            elif self.invoked_by.get(p.name):
                jobs = ", ".join(sorted(os.path.basename(x) for x in self.invoked_by[p.name]))
                cat[p.name] = ("Batch", f"invoked from JCL: {jobs}")
        changed = True
        while changed:
            changed = False
            for p in self.programs:
                if p.name in cat:
                    continue
                cs = self.callers.get(p.name, set())
                known = {c: cat[c][0] for c in cs if c in cat}
                if cs and len(known) == len(cs):
                    if any(v == "Batch" for v in known.values()):
                        cat[p.name] = ("Batch", "called by batch program(s): " + ", ".join(sorted(c for c, v in known.items() if v == "Batch")))
                    else:
                        cat[p.name] = ("Online", "subprogram called by online program(s): " + ", ".join(sorted(known)))
                    changed = True
        for p in self.programs:
            if p.name not in cat:
                cat[p.name] = ("Batch", "no EXEC CICS and no online caller (default batch)")
            p.category, p.reason = cat[p.name]

        prog_cat = {p.name: p.category for p in self.programs}
        includers = defaultdict(set)
        for p in self.programs:
            for c in p.copies:
                includers[c].add(p.name)
        asm_text = {p.name: "\n".join(p.code) for p in self.programs if p.ctype == "ASM"}
        online_psbs = set()
        for p in self.programs:
            if "CICS" in p.flags:
                online_psbs |= set(re.findall(r"PSB\s*\(\s*'?([A-Z0-9#@$]+)'?\s*\)", "\n".join(p.code)))
                online_psbs |= set(re.findall(r"VALUE\s+'(PSB[A-Z0-9#@$]+)'", "\n".join(p.code)))
        for f in self.files:
            if f.is_program:
                continue
            t = f.ctype
            if t in ("JCL", "PROC", "Control Card", "Scheduler"):
                f.category, f.reason = "Batch", f"{t} is batch infrastructure"
            elif t in ("BMS", "BMS Copybook", "CSD"):
                f.category, f.reason = "Online", f"{t} is a CICS artifact"
            elif t in ("Copybook", "DCLGEN"):
                users = includers.get(f.name, set())
                cats = {prog_cat[u] for u in users}
                if not users:
                    f.category, f.reason = "Shared", "not COPY'd by any scanned program"
                elif cats == {"Online"}:
                    f.category, f.reason = "Online", "used only by online programs"
                elif cats == {"Batch"}:
                    f.category, f.reason = "Batch", "used only by batch programs"
                else:
                    f.category, f.reason = "Shared", "used by batch and online programs"
                f.calls = users
            elif t == "ASM Macro":
                users = {n for n, txt in asm_text.items() if re.search(r"(?<![\w-])" + re.escape(f.name) + r"(?![\w-])", txt)}
                cats = {prog_cat[u] for u in users}
                f.calls = users
                if len(cats) == 1:
                    f.category, f.reason = cats.pop(), "macro used by " + ", ".join(sorted(users))
                else:
                    f.category, f.reason = "Shared", "macro used by " + (", ".join(sorted(users)) or "no scanned program")
            elif t == "IMS PSB":
                psbname = self._psb_name(f)
                b, o = psbname in self.psb_jcl, psbname in online_psbs
                f.category = "Shared" if b == o else ("Batch" if b else "Online")
                f.reason = ("scheduled by JCL (DFSRRC00)" if b else "") + (" and " if b and o else "") + ("scheduled by CICS program" if o else "")
                f.reason = f.reason or "not referenced by JCL or CICS program"
            else:
                f.category, f.reason = "Shared", f"{t} is a data definition"

    @staticmethod
    def _psb_name(f):
        m = re.search(r"PSBNAME=([A-Z0-9#@$]+)", "\n".join(f.code))
        return m.group(1) if m else f.name

    # ---- data resources ---------------------------------------------------
    def _data(self):
        self._db2()
        self._ims()
        self._datasets()

    def _db2(self):
        tables = defaultdict(lambda: {"kind": "TABLE", "defined_in": set(), "dclgen": set(),
                                      "programs": set(), "ops": set(), "other_refs": set()})
        self.db2_other = []  # (kind, name, on, file)
        for p in self.programs:
            text = "\n".join(p.code)
            for blk in re.findall(r"EXEC\s+SQL(.*?)END-EXEC", text, re.S):
                stmt = " ".join(blk.split())
                verb = sql_verb(stmt)
                if stmt.startswith("INCLUDE"):
                    continue
                for name in SQL_TABLE_RE.findall(LITERAL_RE.sub("''", stmt)):
                    if name in SQL_NOT_TABLES or name.startswith("SYSIBM."):
                        continue
                    if verb in ("FETCH",) or (verb == "OTHER" and re.match(r"\s*PREPARE", stmt)):
                        continue
                    tables[name]["programs"].add(p.name)
                    tables[name]["ops"].add(verb)
                for name in SQL_DECLARE_TABLE_RE.findall(stmt):
                    tables[name]["dclgen"].add(p.name)
        for f in self.files:
            text = "\n".join(f.code)
            if f.ctype in ("DCLGEN", "Copybook"):
                for blk in re.findall(r"EXEC\s+SQL(.*?)END-EXEC", text, re.S):
                    for name in SQL_DECLARE_TABLE_RE.findall(" ".join(blk.split())):
                        tables[name]["dclgen"].add(f.name)
            sql_sources = []
            if f.ctype in ("DDL", "Control Card"):
                sql_sources.append(text)
            elif f.ctype in ("JCL", "PROC"):
                for st in self.jcl[f.path]["steps"]:
                    if st["pgm"].startswith("IKJEFT") or st["pgm"] in ("DSNTEP2", "DSNTEP4", "DSNTIAD", "DSNTIAUL"):
                        for dd in st["dds"]:
                            if dd["dd"] in ("SYSIN", "SYSREC", "SYSPUNCH") and dd["instream"]:
                                sql_sources.append(instream_text(dd["instream"]))
            for src in sql_sources:
                for stmt in src.split(";"):
                    stmt = " ".join(stmt.split())
                    for kind, name, on in SQL_CREATE_RE.findall(stmt):
                        if kind in ("TABLE", "VIEW"):
                            tables[name]["kind"] = kind
                            tables[name]["defined_in"].add(f.name)
                        else:
                            self.db2_other.append((kind, name, on, f.path))
                    if not re.match(r"(SELECT|INSERT|UPDATE|DELETE|MERGE|WITH)\s", stmt):
                        continue
                    ctes = set(re.findall(r"(?:WITH|,)\s*([A-Z][A-Z0-9_]*)\s+AS\s*\(", stmt))
                    verb = sql_verb(re.sub(r"^WITH\s.*?\)\s*(?=SELECT|INSERT|UPDATE|DELETE)", "", stmt))
                    for name in SQL_TABLE_RE.findall(LITERAL_RE.sub("''", stmt)):
                        if name in SQL_NOT_TABLES or name in ctes or name.startswith("SYSIBM."):
                            continue
                        tables[name]["other_refs"].add(f.name)
                        tables[name]["ops"].add(verb)
        # fold unqualified names onto a unique qualified name with the same suffix
        for name in [n for n in tables if "." not in n]:
            q = [n for n in tables if n.endswith("." + name)]
            if len(q) == 1:
                for k, v in tables.pop(name).items():
                    if isinstance(v, set):
                        tables[q[0]][k] |= v
        self.db2_tables = dict(sorted(tables.items()))
        seen = set()
        self.db2_other = [x for x in sorted(set(self.db2_other)) if not (x[:3] in seen or seen.add(x[:3]))]

    def _ims(self):
        self.dbds, self.psbs = [], []
        self.gsam_dds = {}
        self.imsdb_dds = {}
        for f in self.files:
            text = "\n".join(f.code)
            if f.ctype == "IMS DBD":
                m = re.search(r"DBD\s+NAME=([A-Z0-9#@$]+)\s*,\s*ACCESS=\(?([A-Z,]+?)\)?[,\s]", text)
                name = m.group(1) if m else f.name
                access = m.group(2) if m else "?"
                dds = []
                for dm in re.finditer(r"DATASET\s+DD1=([A-Z0-9#@$]+)(?:,DD2=([A-Z0-9#@$]+))?", text):
                    dds += [d for d in dm.groups() if d]
                segs = re.findall(r"SEGM\s+NAME=([A-Z0-9#@$]+)", text)
                self.dbds.append({"name": name, "access": access, "dds": dds, "segments": segs, "file": f.path})
                for d in dds:
                    (self.gsam_dds if access.startswith("GSAM") else self.imsdb_dds)[d] = name
            elif f.ctype == "IMS PSB":
                pcbs = []
                for pm in re.finditer(r"PCB\s+TYPE=([A-Z]+)\s*,\s*(?:DBDNAME|NAME)=([A-Z0-9#@$]+)(?:\s*,\s*PROCOPT=([A-Z]+))?", text):
                    pcbs.append({"type": pm.group(1), "dbd": pm.group(2), "procopt": pm.group(3) or ""})
                lm = re.search(r"LANG=([A-Z]+)", text)
                self.psbs.append({"name": self._psb_name(f), "lang": lm.group(1) if lm else "", "pcbs": pcbs,
                                  "jcl": sorted(os.path.basename(x) for x in self.psb_jcl.get(self._psb_name(f), [])),
                                  "file": f.path, "category": f.category})
        self.ims_programs = []
        for p in self.programs:
            if "IMS" not in p.flags:
                continue
            text = "\n".join(p.code)
            funcs = Counter(re.findall(r"EXEC\s+DLI\s+([A-Z]+)", text))
            for fn in re.findall(r"CALL\s+'(?:CBLTDLI|PLITDLI|AIBTDLI)'\s+USING\s+([A-Z0-9-]+)", text):
                funcs[fn.replace("FUNC-", "").replace("DLI-", "")] += 1
            api = ("EXEC DLI" if re.search(r"EXEC\s+DLI", text) else "") + (" / " if re.search(r"EXEC\s+DLI", text) and IMS_CALL_RE.search(text) else "") + ("CBLTDLI" if IMS_CALL_RE.search(text) else "")
            psbs = sorted({st.get("psb") for j in self.jcl.values() for st in j["steps"] if p.name in st.get("app_programs", []) and st.get("psb")})
            self.ims_programs.append({"name": p.name, "category": p.category, "api": api,
                                      "calls": ", ".join(f"{k}×{v}" for k, v in sorted(funcs.items())),
                                      "psbs": ", ".join(psbs), "path": p.path})

    def _listcat(self):
        info = {}
        path = os.path.join(self.app_dir, "catlg", "LISTCAT.txt")
        if not os.path.exists(path):
            return info
        with open(path, encoding="latin-1") as fh:
            for line in fh:
                m = re.match(r"^0(CLUSTER|AIX|PATH|NONVSAM|GDG BASE)\s+-+\s+(\S+)", line)
                if m:
                    info[m.group(2).upper()] = m.group(1)
        return info

    def _datasets(self):
        listcat = self._listcat()
        self.listcat_count = len(listcat)
        ds = {}

        def entry(name):
            return ds.setdefault(name, {"name": name, "method": "", "detail": "", "gdg": False,
                                        "gens": set(), "evidence": set(), "jcl": set(), "dds": set(),
                                        "programs": set()})

        self.gdgs = {}

        def gdg(name):
            return self.gdgs.setdefault(name, {"name": name, "defined_in": set(), "limits": set(),
                                               "gens": set(), "jcl": set(), "listcat": False})

        self.libraries = defaultdict(set)
        cobol_selects = {p.name: {s["dd"]: s for s in p.selects} for p in self.programs}
        prog_calls = {p.name: p.calls for p in self.programs}

        def with_callees(names):
            seen, stack = [], list(names)
            while stack:
                n = stack.pop(0)
                if n in prog_calls and n not in seen:
                    seen.append(n)
                    stack.extend(sorted(prog_calls[n]))
            return seen

        for path, j in self.jcl.items():
            base = os.path.basename(path)
            for dd in j.get("job_dds", []):
                self.libraries[dd["dsn"]].add(base)
            for st in j["steps"]:
                for dd in st["dds"]:
                    dsn = dd["dsn"]
                    if dd["instream"] and st["pgm"] == "IDCAMS":
                        for kind, name, detail in parse_idcams_defines(instream_text(dd["instream"])):
                            name = substitute(name, j["symbols"]).upper()
                            if kind in ("GENERATIONDATAGROUP", "GDG"):
                                g = gdg(name)
                                g["defined_in"].add(base)
                                g["limits"].add(detail.replace("GDG", "").strip())
                            else:
                                e = entry(name)
                                if kind != "NONVSAM":
                                    e["method"], e["detail"] = "VSAM", detail
                                    e["evidence"].add(f"IDCAMS DEFINE {kind}")
                                e["jcl"].add(base)
                    if not dsn or dsn.startswith("&&") or dd["dummy"] or dsn in ("NULLFILE", "*"):
                        continue
                    mg, mm = GEN_RE.match(dsn), MEMBER_RE.match(dsn)
                    if mm and not mg:
                        self.libraries[mm.group(1)].add(base)
                        continue
                    if dd["dd"] in LIB_DDS or re.search(r"\.(LOADLIB|SDFSRESL[\w.]*|PSBLIB|DBDLIB|PROCLIB|CNTL|SDSNLOAD|SDSNEXIT|RUNLIB\.LOAD|SCEERUN\w*)$", dsn):
                        self.libraries[dsn].add(base)
                        continue
                    name = mg.group(1) if mg else dsn
                    e = entry(name)
                    e["jcl"].add(base)
                    e["dds"].add(dd["dd"])
                    if mg:
                        e["gdg"] = True
                        e["gens"].add(mg.group(2) if mg.group(2).startswith(("+", "-")) or mg.group(2) == "0" else mg.group(2))
                        g = gdg(name)
                        g["gens"].add(mg.group(2))
                        g["jcl"].add(base)
                    if dd["dd"] in SYSTEM_VSAM_DDS:
                        e["method"], e["detail"] = "VSAM", "KSDS"
                        e["evidence"].add(f"{SYSTEM_VSAM_DDS[dd['dd']]} dataset (VSAM KSDS by definition)")
                    is_ims = st["pgm"] in ("DFSRRC00", "DFSRRC0")
                    if is_ims and dd["dd"] in self.gsam_dds:
                        e["method"], e["detail"] = "GSAM", "GSAM (DBD " + self.gsam_dds[dd["dd"]] + ")"
                        e["evidence"].add(f"DD {dd['dd']} matches GSAM DBD DATASET")
                    elif is_ims and dd["dd"] in self.imsdb_dds:
                        e["method"] = e["method"] or "VSAM"
                        e["detail"] = e["detail"] or "IMS DB (DBD " + self.imsdb_dds[dd["dd"]] + ")"
                        e["evidence"].add(f"DD {dd['dd']} matches IMS DBD DATASET")
                    for prog in with_callees(st.get("app_programs", [])):
                        sel = cobol_selects.get(prog, {}).get(dd["dd"])
                        if sel:
                            e["programs"].add(prog)
                            e["evidence"].add(f"{prog} SELECT {sel['file']} ORGANIZATION {sel['org']}")
                            if sel["method"] == "VSAM" and e["method"] != "GSAM":
                                e["method"] = "VSAM"
                                e["detail"] = e["detail"] if e["detail"] in ("KSDS", "ESDS", "RRDS", "AIX", "PATH") else sel["detail"]
        # CICS FILE definitions -> VSAM
        self.cics_files = []
        for f in self.files:
            if f.ctype != "CSD":
                continue
            text = "\n".join(f.code)
            for m in re.finditer(r"DEFINE\s+FILE\(([A-Z0-9#@$]+)\)\s+GROUP\(([A-Z0-9#@$]+)\)(.*?)(?=DEFINE\s|\Z)", text, re.S):
                dm = re.search(r"DSNAME\(([^)]+)\)", m.group(3))
                dsn = dm.group(1).strip().upper() if dm else ""
                users = sorted(p.name for p in self.programs if "CICS" in p.flags and
                               re.search(r"'" + re.escape(m.group(1)) + r"\s*'", "\n".join(p.code)))
                self.cics_files.append({"file": m.group(1), "group": m.group(2), "dsn": dsn, "csd": f.path, "programs": users})
                if dsn:
                    e = entry(dsn)
                    e["method"] = e["method"] or "VSAM"
                    e["evidence"].add(f"CICS DEFINE FILE({m.group(1)})")
                    e["programs"] |= set(users)
        # enrichment from LISTCAT and naming, default QSAM
        for name, e in ds.items():
            lc = listcat.get(name)
            if lc:
                e["evidence"].add(f"LISTCAT {lc}")
            if not e["method"]:
                if lc in ("CLUSTER", "AIX", "PATH"):
                    e["method"], e["detail"] = "VSAM", {"AIX": "AIX", "PATH": "PATH"}.get(lc, "")
                elif re.search(r"\.(VSAM|KSDS|ESDS|RRDS)(\.|$)", name):
                    e["method"] = "VSAM"
                    e["evidence"].add("dataset name qualifier")
                else:
                    e["method"] = "QSAM"
            if e["method"] == "VSAM" and not e["detail"]:
                m = re.search(r"\.(KSDS|ESDS|RRDS)(\.|$)", name)
                e["detail"] = m.group(1) if m else ("AIX" if name.endswith(".AIX") else "PATH" if name.endswith(".PATH") else "KSDS")
            if e["method"] == "QSAM" and not e["detail"]:
                e["detail"] = "PS (GDG generation)" if e["gdg"] else "PS"
                if not e["evidence"]:
                    e["evidence"].add("JCL DD, no VSAM/GSAM evidence")
        for name, g in self.gdgs.items():
            g["listcat"] = listcat.get(name) == "GDG BASE"
        self.datasets = dict(sorted(ds.items()))
        self.cobol_files = [(p, s) for p in self.programs for s in p.selects]


# --------------------------------------------------------------------------
# HTML rendering
# --------------------------------------------------------------------------

E = html.escape


class H(str):
    """Trusted, already-escaped HTML fragment for table cells."""


def fmt(n) -> str:
    return f"{n:,}"


def pct(a, b) -> str:
    return f"{(100.0 * a / b) if b else 0:.1f}%"


def pie_svg(items: list, size: int = 240) -> str:
    total = sum(v for _, v, _ in items)
    r = size / 2 - 6
    cx = cy = size / 2
    parts = [f'<svg viewBox="0 0 {size} {size}" width="{size}" height="{size}" role="img">']
    angle = -math.pi / 2
    for label, value, color in items:
        if value <= 0:
            continue
        frac = value / total
        tip = f"<title>{E(label)}: {fmt(value)} ({pct(value, total)})</title>"
        if frac >= 0.9999:
            parts.append(f'<circle cx="{cx}" cy="{cy}" r="{r}" fill="{color}">{tip}</circle>')
            continue
        a2 = angle + frac * 2 * math.pi
        x1, y1 = cx + r * math.cos(angle), cy + r * math.sin(angle)
        x2, y2 = cx + r * math.cos(a2), cy + r * math.sin(a2)
        large = 1 if frac > 0.5 else 0
        parts.append(f'<path d="M{cx:.2f},{cy:.2f} L{x1:.2f},{y1:.2f} A{r:.2f},{r:.2f} 0 {large} 1 {x2:.2f},{y2:.2f} Z" '
                     f'fill="{color}" stroke="#fff" stroke-width="1.5">{tip}</path>')
        if frac >= 0.06:
            mid = (angle + a2) / 2
            tx, ty = cx + r * 0.65 * math.cos(mid), cy + r * 0.65 * math.sin(mid)
            parts.append(f'<text x="{tx:.1f}" y="{ty:.1f}" text-anchor="middle" dominant-baseline="middle" '
                         f'class="pielbl">{100 * frac:.0f}%</text>')
        angle = a2
    parts.append("</svg>")
    return "".join(parts)


def pie_block(title: str, items: list, unit: str) -> str:
    total = sum(v for _, v, _ in items)
    rows = "".join(
        f'<tr><td><span class="sw" style="background:{c}"></span>{E(l)}</td>'
        f'<td class="num">{fmt(v)}</td><td class="num">{pct(v, total)}</td></tr>'
        for l, v, c in items if v > 0)
    return (f'<div class="card pie"><h3>{E(title)}</h3><div class="pierow">{pie_svg(items)}'
            f'<table class="legend"><thead><tr><th>Type</th><th class="num">{E(unit)}</th><th class="num">%</th></tr></thead>'
            f'<tbody>{rows}</tbody><tfoot><tr><th>Total</th><th class="num">{fmt(total)}</th><th class="num">100%</th></tr></tfoot>'
            f'</table></div></div>')


def bar_svg(items: list, width: int = 520, bar_h: int = 18) -> str:
    if not items:
        return ""
    mx = max(v for _, v, _ in items) or 1
    label_w, gap = 110, 6
    h = len(items) * (bar_h + gap) + 4
    out = [f'<svg viewBox="0 0 {width} {h}" width="100%" style="max-width:{width}px" role="img">']
    for i, (label, v, color) in enumerate(items):
        y = i * (bar_h + gap) + 2
        w = (width - label_w - 70) * v / mx
        out.append(f'<text x="{label_w - 6}" y="{y + bar_h / 2}" text-anchor="end" dominant-baseline="middle" class="barlbl">{E(label)}</text>'
                   f'<rect x="{label_w}" y="{y}" width="{max(w, 1):.1f}" height="{bar_h}" fill="{color}" rx="2"><title>{E(label)}: {fmt(v)}</title></rect>'
                   f'<text x="{label_w + w + 6:.1f}" y="{y + bar_h / 2}" dominant-baseline="middle" class="barlbl">{fmt(v)}</text>')
    out.append("</svg>")
    return "".join(out)


def table(headers: list, rows: list, cls: str = "sortable", num_cols: set = frozenset(), tid: str = "") -> str:
    th = "".join(f'<th class="{"num" if i in num_cols else ""}">{E(h)}</th>' for i, h in enumerate(headers))
    body = []
    for r in rows:
        tds = []
        for i, c in enumerate(r):
            c = c if isinstance(c, H) else E(fmt(c) if isinstance(c, int) else str(c))
            tds.append(f'<td class="{"num" if i in num_cols else ""}">{c}</td>')
        body.append("<tr>" + "".join(tds) + "</tr>")
    idattr = f' id="{tid}"' if tid else ""
    return f'<div class="tw"><table class="{cls}"{idattr}><thead><tr>{th}</tr></thead><tbody>{"".join(body)}</tbody></table></div>'


def badge(text: str, kind: str = "") -> str:
    return H(f'<span class="b {kind}">{E(text)}</span>')


def flags_html(flags) -> str:
    return H(" ".join(badge(f, "f" + f.lower()) for f in sorted(flags)))


def band_html(band: str) -> str:
    return badge(band, "band" + band.lower()) if band else H("")


CSS = """
:root{--bg:#f5f7fa;--fg:#1d2733;--muted:#5b6b7b;--card:#fff;--line:#e2e8f0;--acc:#0b5cad}
*{box-sizing:border-box}body{margin:0;font:14px/1.45 -apple-system,Segoe UI,Roboto,Helvetica,Arial,sans-serif;background:var(--bg);color:var(--fg)}
header{background:linear-gradient(135deg,#0b2e59,#0b5cad);color:#fff;padding:28px 36px}
header h1{margin:0 0 6px;font-size:26px}header p{margin:2px 0;opacity:.9}
nav{background:#fff;border-bottom:1px solid var(--line);padding:8px 36px;position:sticky;top:0;z-index:5}
nav a{margin-right:18px;color:var(--acc);text-decoration:none;font-weight:600}
main{padding:20px 36px 40px;max-width:1500px}
section{margin-top:28px}h2{border-bottom:2px solid var(--acc);padding-bottom:4px}h3{margin:6px 0 10px}
.grid{display:grid;gap:16px;grid-template-columns:repeat(auto-fit,minmax(170px,1fr))}
.card{background:var(--card);border:1px solid var(--line);border-radius:8px;padding:14px 16px;box-shadow:0 1px 2px rgba(0,0,0,.04)}
.kpi .v{font-size:26px;font-weight:700;color:var(--acc)}.kpi .l{color:var(--muted);font-size:12px;text-transform:uppercase;letter-spacing:.04em}
.hero{background:linear-gradient(135deg,#0b5cad,#1f8ad6);color:#fff;text-align:center;padding:22px}
.hero .v{font-size:46px;font-weight:800;color:#fff}.hero .l{color:#e6f0fb;font-size:13px}
.charts{display:grid;gap:16px;grid-template-columns:repeat(auto-fit,minmax(460px,1fr));margin-top:16px}
.pierow{display:flex;gap:16px;align-items:flex-start;flex-wrap:wrap}.pielbl{font-size:11px;fill:#fff;font-weight:700}
.barlbl{font-size:11px;fill:#333}.legend{font-size:12.5px;border-collapse:collapse;min-width:220px}.legend td,.legend th{padding:3px 8px;border-bottom:1px solid var(--line)}
.sw{display:inline-block;width:11px;height:11px;border-radius:2px;margin-right:6px;vertical-align:-1px}
.tw{overflow-x:auto;background:#fff;border:1px solid var(--line);border-radius:8px;margin:8px 0 14px}
table{border-collapse:collapse;width:100%}th,td{padding:5px 9px;border-bottom:1px solid var(--line);text-align:left;vertical-align:top}
thead th{background:#eef3f9;position:sticky;top:0;font-size:12.5px;white-space:nowrap}
table.sortable thead th{cursor:pointer}table.sortable thead th:hover{background:#dde8f5}
td.num,th.num{text-align:right;font-variant-numeric:tabular-nums}tfoot th{background:#f7f9fc}
.b{display:inline-block;padding:0 6px;border-radius:9px;font-size:11px;font-weight:600;background:#e2e8f0;color:#333;margin:1px}
.fcics{background:#fde2e1;color:#9b1c1c}.fsql{background:#dbeafe;color:#1e40af}.fims{background:#fce7f3;color:#9d174d}.fmq{background:#fef3c7;color:#92400e}
.bandlow{background:#dcfce7;color:#166534}.bandmedium{background:#fef9c3;color:#854d0e}.bandhigh{background:#fee2e2;color:#991b1b}
.catbatch{background:#dcfce7;color:#14532d}.catonline{background:#dbeafe;color:#1e3a8a}.catshared{background:#ede9fe;color:#4c1d95}
.muted{color:var(--muted)}.small{font-size:12px}.cols2{display:grid;gap:16px;grid-template-columns:repeat(auto-fit,minmax(420px,1fr))}
.filter{margin:6px 0;display:flex;gap:8px;flex-wrap:wrap}.filter input,.filter select{padding:6px 8px;border:1px solid var(--line);border-radius:6px}
footer{border-top:1px solid var(--line);padding:18px 36px;color:var(--muted);font-size:12.5px;background:#fff}
footer code,td code{background:#f1f5f9;padding:0 4px;border-radius:3px}
details>summary{cursor:pointer;font-weight:600;margin:6px 0}
"""

JS = """
document.querySelectorAll('table.sortable').forEach(function(t){
  t.querySelectorAll('thead th').forEach(function(th,i){
    th.addEventListener('click',function(){
      var tb=t.tBodies[0],rows=Array.prototype.slice.call(tb.rows),asc=th.dataset.asc!=='1';
      t.querySelectorAll('thead th').forEach(function(x){delete x.dataset.asc});th.dataset.asc=asc?'1':'0';
      rows.sort(function(a,b){var x=a.cells[i].innerText.trim(),y=b.cells[i].innerText.trim();
        var nx=parseFloat(x.replace(/[,%]/g,'')),ny=parseFloat(y.replace(/[,%]/g,''));
        var c=(!isNaN(nx)&&!isNaN(ny)&&/^[-\\d.,%]+$/.test(x)&&/^[-\\d.,%]+$/.test(y))?nx-ny:x.localeCompare(y);
        return asc?c:-c;});
      rows.forEach(function(r){tb.appendChild(r)});});});});
function filterFiles(){
  var q=document.getElementById('fq').value.toLowerCase(),c=document.getElementById('fc').value,t=document.getElementById('ft').value;
  var n=0;document.querySelectorAll('#filetable tbody tr').forEach(function(r){
    var ok=(!q||r.innerText.toLowerCase().indexOf(q)>=0)&&(!c||r.dataset.cat===c)&&(!t||r.dataset.type===t);
    r.style.display=ok?'':'none';if(ok)n++;});
  document.getElementById('fn').innerText=n+' shown';}
"""


def render(m: Model, date: str) -> str:
    files = m.files
    grand = sum(f.loc for f in files)
    types_present = [t for t in TYPE_ORDER if any(f.ctype == t for f in files)]
    by_type = {t: [f for f in files if f.ctype == t] for t in types_present}
    progs = sorted(m.programs, key=lambda p: (p.category, p.name))
    cats = ["Batch", "Online", "Shared"]

    # ---------- dashboard ----------
    n_batch = sum(1 for p in progs if p.category == "Batch")
    n_online = sum(1 for p in progs if p.category == "Online")
    vsam = [d for d in m.datasets.values() if d["method"] == "VSAM"]
    qsam = [d for d in m.datasets.values() if d["method"] == "QSAM"]
    gsam = [d for d in m.datasets.values() if d["method"] == "GSAM"]
    kpis = [
        ("Files scanned", fmt(len(files))), ("Programs", fmt(len(progs))),
        ("Batch programs", fmt(n_batch)), ("Online programs", fmt(n_online)),
        ("JCL members", fmt(len(by_type.get("JCL", [])))),
        ("DB2 tables / views", fmt(len(m.db2_tables))), ("IMS DBDs", fmt(len(m.dbds))),
        ("GDGs", fmt(len(m.gdgs))), ("Datasets (QSAM/VSAM/GSAM)", f"{len(qsam)}/{len(vsam)}/{len(gsam)}"),
    ]
    kpi_html = "".join(f'<div class="card kpi"><div class="v">{v}</div><div class="l">{E(l)}</div></div>' for l, v in kpis)
    pie1 = pie_block("Pie 1 — Components by type (file count)",
                     [(t, len(by_type[t]), TYPE_COLORS[t]) for t in types_present], "Files")
    pie2 = pie_block("Pie 2 — Components by LOC",
                     [(t, sum(f.loc for f in by_type[t]), TYPE_COLORS[t]) for t in types_present], "LOC")
    top = sorted(progs, key=lambda p: -p.loc)[:15]
    bar_top = bar_svg([(p.name, p.loc, TYPE_COLORS[p.ctype]) for p in top])
    band_colors = {"Low": "#22c55e", "Medium": "#eab308", "High": "#ef4444"}
    bar_band = bar_svg([(b, sum(1 for p in progs if p.band == b), band_colors[b]) for b in BANDS], width=420)
    cat_colors = {"Batch": "#16a34a", "Online": "#2563eb", "Shared": "#7c3aed"}
    bar_cat = bar_svg([(c, sum(f.loc for f in files if f.category == c), cat_colors[c]) for c in cats], width=420)

    # ---------- section 1 ----------
    cat_rows = []
    for c in cats:
        fs = [f for f in files if f.category == c]
        ps = [p for p in fs if p.is_program]
        cat_rows.append([badge(c, "cat" + c.lower()), len(fs), len(ps), sum(f.loc for f in fs),
                         sum(p.loc for p in ps), pct(sum(f.loc for f in fs), grand)])
    cat_rows.append([H("<b>Total</b>"), len(files), len(progs), grand, sum(p.loc for p in progs), "100.0%"])
    cat_table = table(["Category", "Files", "Programs", "Total LOC (all files)", "Program LOC", "% of LOC"],
                      cat_rows, cls="", num_cols={1, 2, 3, 4, 5})
    cat_type_rows = []
    for t in types_present:
        row = [t]
        for c in cats:
            fs = [f for f in by_type[t] if f.category == c]
            row += [len(fs), sum(f.loc for f in fs)]
        cat_type_rows.append(row)
    cat_type_table = table(["Type", "Batch files", "Batch LOC", "Online files", "Online LOC", "Shared files", "Shared LOC"],
                           cat_type_rows, num_cols={1, 2, 3, 4, 5, 6})
    cat_sections = []
    for c in cats:
        ps = [p for p in progs if p.category == c]
        others = defaultdict(list)
        for f in files:
            if f.category == c and not f.is_program:
                others[f.ctype].append(f.name)
        prog_tbl = table(["Program", "Type", "Feature", "LOC", "Complexity", "Flags", "Classification reason"],
                         [[p.name, p.ctype, p.feature, p.loc, band_html(p.band), flags_html(p.flags), p.reason] for p in ps],
                         num_cols={3}) if ps else '<p class="muted">No programs in this category.</p>'
        other_html = "".join(
            f"<tr><td>{E(t)}</td><td class='num'>{len(n)}</td><td class='small'>{E(', '.join(sorted(n)))}</td></tr>"
            for t, n in sorted(others.items(), key=lambda x: TYPE_ORDER.index(x[0])))
        cat_sections.append(
            f'<h3>{badge(c, "cat" + c.lower())} {c} — {len(ps)} programs</h3>{prog_tbl}'
            + (f'<details><summary>Other {c.lower()} artifacts ({sum(len(v) for v in others.values())})</summary>'
               f'<div class="tw"><table><thead><tr><th>Type</th><th class="num">Files</th><th>Members</th></tr></thead>'
               f'<tbody>{other_html}</tbody></table></div></details>' if others else ""))
    util_rows = sorted(([u, len(j), ", ".join(sorted(j))] for u, j in m.utility_use.items()), key=lambda r: (-r[1], r[0]))
    util_table = table(["Utility / system program", "JCL members", "Used in"], util_rows, num_cols={1})

    # ---------- section 2 ----------
    loc_rows = []
    for t in types_present:
        fs = by_type[t]
        loc = sum(f.loc for f in fs)
        loc_rows.append([H(f'<span class="sw" style="background:{TYPE_COLORS[t]}"></span>{E(t)}'), len(fs),
                         sum(f.physical for f in fs), sum(f.comment for f in fs), sum(f.blank for f in fs),
                         loc, pct(loc, grand), round(loc / len(fs)) if fs else 0])
    loc_rows.sort(key=lambda r: -r[5])
    loc_table = table(["Type", "Files", "Physical lines", "Comment lines", "Blank lines", "LOC", "% of total", "Avg LOC/file"],
                      loc_rows, num_cols={1, 2, 3, 4, 5, 6, 7})
    loc_foot = (f'<p class="small muted">Totals: {fmt(len(files))} files, {fmt(sum(f.physical for f in files))} physical lines, '
                f'{fmt(sum(f.comment for f in files))} comment lines, {fmt(sum(f.blank for f in files))} blank lines, '
                f'<b>{fmt(grand)} LOC</b>.</p>')
    prog_loc = sum(p.loc for p in progs)
    drill = table(["File", "Path", "Type", "Physical", "Comment", "Blank", "LOC", "% of total"],
                  [[f.name, H(f"<code>{E(f.path)}</code>"), f.ctype, f.physical, f.comment, f.blank, f.loc, pct(f.loc, grand)]
                   for f in sorted(files, key=lambda f: (TYPE_ORDER.index(f.ctype), -f.loc))],
                  num_cols={3, 4, 5, 6, 7})

    # ---------- section 3 ----------
    cx_rows = []
    for p in sorted(progs, key=lambda p: (-p.score, -p.loc)):
        d = p.dp
        g = lambda k: d.get(k, 0)
        branch = d.get("Branches", 0)
        cx_rows.append([p.name, p.ctype, badge(p.category, "cat" + p.category.lower()), p.loc,
                        g("IF"), g("EVALUATE") + g("SELECT"), g("WHEN"), g("PERFORM") + g("DO WHILE/UNTIL"),
                        g("GO TO"), g("EXEC CICS"), g("EXEC SQL"), g("EXEC DLI"), branch + g("ON"),
                        p.dp_total, p.dp_total + 1, p.loc_pts, p.dp_pts, p.score, band_html(p.band)])
    cx_table = table(["Program", "Type", "Category", "LOC", "IF", "EVALUATE/SELECT", "WHEN", "PERFORM/DO", "GO TO",
                      "EXEC CICS", "EXEC SQL", "EXEC DLI", "Other (ASM branch/ON)", "Decision points",
                      "Cyclomatic proxy", "LOC pts", "DP pts", "Score", "Band"],
                     cx_rows, num_cols=set(range(3, 18)))
    band_rows = []
    for t in [t for t in TYPE_ORDER if t in PROGRAM_TYPES and t in by_type]:
        ps = [p for p in progs if p.ctype == t]
        band_rows.append([t] + [sum(1 for p in ps if p.band == b) for b in BANDS] + [len(ps)])
    band_rows.append([H("<b>All programs</b>")] + [sum(1 for p in progs if p.band == b) for b in BANDS] + [len(progs)])
    band_cat_rows = [[c] + [sum(1 for p in progs if p.band == b and p.category == c) for b in BANDS]
                     + [sum(1 for p in progs if p.category == c)] for c in ("Batch", "Online")]
    band_table = table(["Type", "Low", "Medium", "High", "Total"], band_rows, cls="", num_cols={1, 2, 3, 4})
    band_cat_table = table(["Category", "Low", "Medium", "High", "Total"], band_cat_rows, cls="", num_cols={1, 2, 3, 4})

    # ---------- section 4 ----------
    db2_rows = []
    for name, t in m.db2_tables.items():
        src = []
        if t["defined_in"]:
            src.append("DDL: " + ", ".join(sorted(t["defined_in"])))
        if t["dclgen"]:
            src.append("DCLGEN: " + ", ".join(sorted(t["dclgen"])))
        if t["other_refs"]:
            src.append("SQL scripts: " + ", ".join(sorted(t["other_refs"])))
        db2_rows.append([name, t["kind"], ", ".join(sorted(t["programs"])) or "—",
                         ", ".join(sorted(t["ops"] - {"OTHER"})) or "—", "; ".join(src) or "EXEC SQL only"])
    n_views = sum(1 for t in m.db2_tables.values() if t["kind"] == "VIEW")
    db2_table = table(["Table / view", "Kind", "Programs (EXEC SQL)", "Operations", "Defined / declared in"], db2_rows)
    db2_other = table(["Object", "Name", "On table", "Defined in"],
                      [[k, n, o or "—", H(f"<code>{E(p)}</code>")] for k, n, o, p in m.db2_other]) if m.db2_other else ""
    dbd_table = table(["DBD", "Access", "Dataset DD names", "Segments", "Source"],
                      [[d["name"], d["access"], ", ".join(d["dds"]), ", ".join(d["segments"]), H(f"<code>{E(d['file'])}</code>")] for d in m.dbds])
    psb_table = table(["PSB", "Lang", "PCBs (type → DBD, PROCOPT)", "Scheduled by JCL", "Category", "Source"],
                      [[p["name"], p["lang"], "; ".join(f"{c['type']} → {c['dbd']} ({c['procopt']})" for c in p["pcbs"]),
                        ", ".join(p["jcl"]) or "—", badge(p["category"], "cat" + p["category"].lower()), H(f"<code>{E(p['file'])}</code>")] for p in m.psbs])
    imsprog_table = table(["Program", "Category", "Interface", "DL/I calls", "PSB (from JCL)"],
                          [[p["name"], badge(p["category"], "cat" + p["category"].lower()), p["api"], p["calls"], p["psbs"] or "—"] for p in m.ims_programs])
    n_pcbs = sum(len(p["pcbs"]) for p in m.psbs)
    gdg_table = table(["GDG base", "Defined by (IDCAMS)", "LIMIT", "Generations referenced", "Referenced in JCL", "In LISTCAT"],
                      [[g["name"], ", ".join(sorted(g["defined_in"])) or "—", ", ".join(sorted(g["limits"] - {""})) or "—",
                        ", ".join(sorted(g["gens"])) or "—", ", ".join(sorted(g["jcl"])) or "—", "yes" if g["listcat"] else "no"]
                       for g in sorted(m.gdgs.values(), key=lambda g: g["name"])])

    def ds_table(items):
        return table(["Dataset", "Organisation", "GDG", "DD names", "Programs", "JCL", "Evidence"],
                     [[d["name"], d["detail"], "yes" if d["gdg"] else "", ", ".join(sorted(d["dds"])),
                       ", ".join(sorted(d["programs"])), ", ".join(sorted(d["jcl"])), "; ".join(sorted(d["evidence"]))]
                      for d in items])
    am_rows = []
    for meth, items in (("QSAM", qsam), ("VSAM", vsam), ("GSAM", gsam)):
        det = Counter(d["detail"].split(" (")[0] for d in items)
        am_rows.append([meth, len(items), ", ".join(f"{k}: {v}" for k, v in sorted(det.items()))])
    am_rows.append([H("<b>Total</b>"), len(qsam) + len(vsam) + len(gsam), ""])
    am_table = table(["Access method", "Datasets", "Breakdown"], am_rows, cls="", num_cols={1})
    cobfile_table = table(["Program", "Category", "File (SELECT)", "DD / ASSIGN", "ORGANIZATION", "ACCESS", "Access method"],
                          [[p.name, badge(p.category, "cat" + p.category.lower()), s["file"], s["dd"], s["org"], s["access"],
                            f'{s["method"]} {s["detail"]}'] for p, s in m.cobol_files])
    cobfile_sum = Counter(s["method"] for _, s in m.cobol_files)
    cics_table = table(["CICS file", "Group", "Dataset (DSNAME)", "Online programs referencing", "CSD"],
                       [[c["file"], c["group"], c["dsn"] or "—", ", ".join(c["programs"]) or "—", H(f"<code>{E(c['csd'])}</code>")] for c in m.cics_files])
    lib_table = table(["Library", "JCL members"], [[k, ", ".join(sorted(v))] for k, v in sorted(m.libraries.items())])

    # ---------- section 5 ----------
    file_rows = []
    for f in sorted(files, key=lambda f: f.path):
        file_rows.append(
            f'<tr data-cat="{f.category}" data-type="{E(f.ctype)}"><td>{E(f.name)}</td><td><code>{E(f.path)}</code></td>'
            f'<td>{E(f.ctype)}</td><td>{E(f.feature)}</td><td>{badge(f.category, "cat" + f.category.lower())}</td>'
            f'<td class="num">{fmt(f.loc)}</td><td>{band_html(f.band) if f.is_program else "<span class=muted>n/a</span>"}</td>'
            f'<td>{flags_html(f.flags)}</td></tr>')
    type_opts = "".join(f'<option value="{E(t)}">{E(t)}</option>' for t in types_present)
    file_table = (
        '<div class="filter"><input id="fq" placeholder="Filter by text…" oninput="filterFiles()">'
        '<select id="fc" onchange="filterFiles()"><option value="">All categories</option><option>Batch</option><option>Online</option><option>Shared</option></select>'
        f'<select id="ft" onchange="filterFiles()"><option value="">All types</option>{type_opts}</select>'
        f'<span id="fn" class="muted small">{len(files)} shown</span></div>'
        '<div class="tw"><table class="sortable" id="filetable"><thead><tr><th>Name</th><th>Path</th><th>Type</th><th>Feature</th>'
        '<th>Batch/Online</th><th class="num">LOC</th><th>Complexity</th><th>Flags</th></tr></thead><tbody>'
        + "".join(file_rows) + "</tbody></table></div>")

    features = Counter(f.feature for f in files)
    feat_txt = ", ".join(f"{k} ({v} files)" for k, v in sorted(features.items()))

    return f"""<!DOCTYPE html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>CardDemo Application Blueprint</title><style>{CSS}</style></head><body>
<header><h1>CardDemo Application Blueprint</h1>
<p>Static inventory of the mainframe estate under <code>app/</code> — components, size, complexity and data resources.</p>
<p>Generated {E(date)} by <code>tools/blueprint/generate_blueprint.py</code> · {fmt(len(files))} source files · features: {E(feat_txt)}</p></header>
<nav><a href="#dashboard">Dashboard</a><a href="#batch-online">Batch vs Online</a><a href="#loc">LOC</a><a href="#complexity">Complexity</a>
<a href="#data">Data</a><a href="#files">File listing</a><a href="#method">Method</a></nav>
<main>
<section id="dashboard"><h2>Summary dashboard</h2>
<div class="grid"><div class="card hero" style="grid-column:span 2"><div class="v">{fmt(grand)}</div><div class="l">Grand total LOC — all {fmt(len(files))} components (non-blank, non-comment)</div></div>
<div class="card hero" style="grid-column:span 2;background:linear-gradient(135deg,#14532d,#16a34a)"><div class="v">{fmt(prog_loc)}</div><div class="l">LOC in {len(progs)} executable programs (COBOL + PL/I + ASM)</div></div></div>
<div class="grid" style="margin-top:16px">{kpi_html}</div>
<div class="charts">{pie1}{pie2}</div>
<div class="charts"><div class="card"><h3>Top 15 programs by LOC</h3>{bar_top}</div>
<div class="card"><h3>Programs by complexity band</h3>{bar_band}<h3 style="margin-top:18px">LOC by category</h3>{bar_cat}</div></div>
</section>

<section id="batch-online"><h2>1. Component inventory — Batch vs Online</h2>
<p class="muted">Programs are <b>Online</b> when they contain <code>EXEC CICS</code> (or are only called by online programs), <b>Batch</b> when invoked from JCL/PROCs or called by batch programs.
Non-program artifacts follow their role: JCL, PROCs, control cards and schedules are Batch; BMS maps, BMS copybooks and CSD definitions are Online;
copybooks/DCLGENs follow the programs that COPY them; DDL, DBDs and copybooks used by both sides are <b>Shared</b>.</p>
{cat_table}
<details><summary>Category × component type</summary>{cat_type_table}</details>
{''.join(cat_sections)}
<h3>Batch utilities and system programs invoked from JCL</h3>{util_table}
</section>

<section id="loc"><h2>2. Lines of code</h2>
<div class="grid"><div class="card hero"><div class="v">{fmt(grand)}</div><div class="l">Grand total LOC, all programs and artifacts</div></div>
{''.join(f'<div class="card kpi"><div class="v">{fmt(sum(f.loc for f in by_type[t]))}</div><div class="l">{E(t)} LOC · {len(by_type[t])} files</div></div>' for t in types_present if t in ("COBOL","PL/I","ASM","JCL","PROC","Copybook","BMS"))}</div>
<h3>LOC per component type</h3>{loc_table}{loc_foot}
<details><summary>Per-file drill-down ({len(files)} files)</summary>{drill}</details>
</section>

<section id="complexity"><h2>3. Complexity classification</h2>
<div class="cols2"><div><h3>Programs per band per type</h3>{band_table}</div><div><h3>Programs per band, Batch vs Online</h3>{band_cat_table}</div></div>
<h3>Per-program score</h3>{cx_table}
<p class="small muted">Thresholds and scoring are documented in the <a href="#method">method notes</a>.</p>
</section>

<section id="data"><h2>4. Data resources</h2>
<div class="grid">
<div class="card kpi"><div class="v">{len(m.db2_tables)}</div><div class="l">DB2 tables / views ({n_views} views)</div></div>
<div class="card kpi"><div class="v">{len(m.dbds)}</div><div class="l">IMS DBDs</div></div>
<div class="card kpi"><div class="v">{len(m.psbs)} / {n_pcbs}</div><div class="l">IMS PSBs / PCBs</div></div>
<div class="card kpi"><div class="v">{len(m.gdgs)}</div><div class="l">GDG bases</div></div>
<div class="card kpi"><div class="v">{len(qsam)}</div><div class="l">QSAM datasets</div></div>
<div class="card kpi"><div class="v">{len(vsam)}</div><div class="l">VSAM datasets</div></div>
<div class="card kpi"><div class="v">{len(gsam)}</div><div class="l">GSAM datasets</div></div></div>
<h3>4.1 DB2 tables and views — {len(m.db2_tables)}</h3>{db2_table}
{('<details><summary>Other DB2 objects (indexes, tablespaces, databases)</summary>' + db2_other + '</details>') if db2_other else ''}
<h3>4.2 IMS/DB — {len(m.dbds)} DBDs, {len(m.psbs)} PSBs, {n_pcbs} PCBs, {len(m.ims_programs)} DL/I programs</h3>
<div class="cols2"><div><h3 class="small">DBDs</h3>{dbd_table}</div><div><h3 class="small">PSBs / PCBs</h3>{psb_table}</div></div>
<h3 class="small">Programs issuing DL/I calls</h3>{imsprog_table}
<h3>4.3 Generation data groups — {len(m.gdgs)}</h3>{gdg_table}
<h3>4.4 Datasets by access method — {len(qsam) + len(vsam) + len(gsam)}</h3>{am_table}
<details open><summary>VSAM ({len(vsam)})</summary>{ds_table(vsam)}</details>
<details><summary>QSAM / sequential ({len(qsam)})</summary>{ds_table(qsam)}</details>
<details open><summary>GSAM ({len(gsam)})</summary>{ds_table(gsam) if gsam else '<p class="muted">None found.</p>'}</details>
<details><summary>COBOL file definitions (FILE-CONTROL SELECT) — {len(m.cobol_files)}: {', '.join(f'{k} {v}' for k, v in sorted(cobfile_sum.items()))}</summary>{cobfile_table}</details>
<details><summary>CICS file definitions (CSD) — {len(m.cics_files)}</summary>{cics_table}</details>
<details><summary>Libraries / PDS members referenced by JCL (excluded from access-method counts) — {len(m.libraries)}</summary>{lib_table}</details>
</section>

<section id="files"><h2>5. Full file listing — {len(files)} files</h2>{file_table}</section>
</main>
<footer id="method"><h3>Method and thresholds</h3>
<p><b>Scope.</b> Every file under <code>app/</code> except data files (<code>data/</code>, <code>*.dat</code>), the catalog listing (<code>catlg/</code>, used only to corroborate dataset types) and documentation (<code>*.md</code>, <code>*.txt</code>, <code>.gitkeep</code>). Component type comes from the file extension (<code>cpy-bms/</code> copybooks are typed “BMS Copybook”).</p>
<p><b>LOC</b> = non-blank, non-comment lines. COBOL, copybooks and DCLGEN: fixed format, <code>*</code> or <code>/</code> in column 7 (and <code>*&gt;</code>) are comments, a line whose columns 8–72 are blank is blank (tabs expanded to 8). JCL/PROC: <code>//*</code> is a comment. PL/I, DDL, control cards: <code>/* … */</code> (and <code>--</code> for SQL) are comments. Assembler, macros, BMS, DBD/PSB: <code>*</code> or <code>.*</code> in column 1. CSD/scheduler: lines starting with <code>*</code>.</p>
<p><b>Complexity.</b> Decision points (DP) are counted on code lines with literals removed: COBOL <code>IF</code>, <code>EVALUATE</code>, <code>WHEN</code>, <code>PERFORM</code>, <code>GO TO</code>, <code>EXEC CICS</code>, <code>EXEC SQL</code>, <code>EXEC DLI</code> (END-IF etc. excluded); PL/I <code>IF</code>, <code>SELECT</code>, <code>WHEN</code>, <code>DO WHILE/UNTIL</code>, <code>GO TO</code>, <code>ON</code>, <code>EXEC CICS/SQL</code>; Assembler conditional branch / <code>AIF</code>/<code>AGO</code> opcodes. Cyclomatic proxy = DP + 1.
LOC points: &lt;{LOC_LOW} = 1, {LOC_LOW}–{LOC_HIGH} = 2, &gt;{LOC_HIGH} = 3. DP points: &lt;{DP_LOW} = 1, {DP_LOW}–{DP_HIGH} = 2, &gt;{DP_HIGH} = 3.
Score = LOC points + DP points (2–6): <b>2–3 Low</b>, <b>4 Medium</b>, <b>5–6 High</b>.</p>
<p><b>Batch vs Online.</b> COBOL/PL/I/ASM with <code>EXEC CICS</code> → Online; else invoked by JCL (<code>EXEC PGM=</code>, TSO <code>RUN PROGRAM()</code> under IKJEFT01, IMS <code>DFSRRC00 PARM='DLI|BMP,pgm,psb'</code>) → Batch; else classified from static callers (<code>CALL 'X'</code>, CICS <code>LINK/XCTL PROGRAM('X')</code>): any batch caller → Batch, only online callers → Online; otherwise Batch.</p>
<p><b>Data.</b> DB2 tables: names after <code>FROM/INTO/UPDATE/JOIN</code> in <code>EXEC SQL</code> blocks, <code>CREATE TABLE/VIEW</code> in DDL, control cards and in-stream DB2 SYSIN, and <code>EXEC SQL DECLARE … TABLE</code> in DCLGENs; host variables, <code>SYSIBM.*</code> and SQLCA are excluded and unqualified names are folded onto their qualified form.
IMS: DBDs (<code>DBD NAME=,ACCESS=</code>), PSBs (<code>PCB TYPE=,DBDNAME=</code>), programs with <code>EXEC DLI</code> or <code>CALL 'CBLTDLI'/'PLITDLI'</code>.
GDGs: <code>IDCAMS DEFINE GDG/GENERATIONDATAGROUP</code> plus every <code>DSN=base(+n|0|-n)</code>.
Datasets: unique DSNs from JCL/PROC DD statements, IDCAMS <code>DEFINE CLUSTER/AIX/PATH</code> and CSD <code>DEFINE FILE … DSNAME()</code> (JCL symbols such as <code>&amp;HLQ</code> resolved from <code>SET</code>/<code>PROC</code>). GSAM = DD matches a GSAM DBD <code>DATASET DD1/DD2</code> in an IMS step; VSAM = IDCAMS cluster/AIX/PATH definition, CICS file, COBOL <code>ORGANIZATION INDEXED/RELATIVE</code> on the step's DD, LISTCAT cluster, IMS HIDAM/INDEX DBD dataset or a <code>.VSAM/.KSDS/.ESDS/.RRDS</code> qualifier; everything else is QSAM. Temporary (<code>&amp;&amp;</code>), DUMMY, SYSOUT, load/PDS libraries and member references are excluded.</p>
<p>All figures are produced by static pattern matching; dynamic CALL targets, PROC symbolic overrides and run-time dataset names are not resolved.</p>
</footer><script>{JS}</script></body></html>
"""


def main(argv=None) -> int:
    here = os.path.dirname(os.path.abspath(__file__))
    repo_default = os.path.abspath(os.path.join(here, "..", ".."))
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--repo", default=repo_default, help="repository root (default: %(default)s)")
    ap.add_argument("--app-dir", default=None, help="application tree to scan (default: <repo>/app)")
    ap.add_argument("--output", default=None, help="output HTML (default: <repo>/docs/blueprint.html)")
    ap.add_argument("--date", default=None, help="generation date to print (default: today)")
    args = ap.parse_args(argv)
    repo = os.path.abspath(args.repo)
    app_dir = os.path.abspath(args.app_dir or os.path.join(repo, "app"))
    out = os.path.abspath(args.output or os.path.join(repo, "docs", "blueprint.html"))
    if not os.path.isdir(app_dir):
        print(f"error: {app_dir} is not a directory", file=sys.stderr)
        return 2
    model = Model(repo, app_dir)
    date = args.date or dt.date.today().isoformat()
    os.makedirs(os.path.dirname(out), exist_ok=True)
    with open(out, "w", encoding="utf-8") as fh:
        fh.write(render(model, date))
    grand = sum(f.loc for f in model.files)
    progs = model.programs
    print(f"wrote {os.path.relpath(out, repo)}: {len(model.files)} files, {grand:,} LOC, "
          f"{len(progs)} programs ({sum(p.category == 'Batch' for p in progs)} batch / "
          f"{sum(p.category == 'Online' for p in progs)} online), {len(model.db2_tables)} DB2 tables, "
          f"{len(model.dbds)} DBDs, {len(model.gdgs)} GDGs, {len(model.datasets)} datasets")
    return 0


if __name__ == "__main__":
    sys.exit(main())
