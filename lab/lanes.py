"""Container-side adapters. One lock protects the estate's shared datasets."""
import argparse
import contextlib
import fcntl
import importlib.util
import json
import os
import shutil
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
from tools.parity import record
from tools.runjcl import runjcl

MODULE = ROOT / 'java/interest/interest-batch'
DEFAULT_JAR = MODULE / 'target/interest-batch-0.1.0-PILOT.jar'
CHAIN = record.CHAINS['interest']


def driver():
    spec = importlib.util.spec_from_file_location('lab_pilot_driver', MODULE / 'src/test/resources/pilot/parity/run_case.py')
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def capture(runner, out, rc):
    out.mkdir(parents=True, exist_ok=True)
    (out / 'rc.json').write_text(json.dumps({'INTCALC': {'maxrc': rc, 'steps': {'STEP15': rc}}}))
    (out / 'sysout').mkdir(exist_ok=True)
    src = runner.joblog / 'INTCALC/STEP15/SYSOUT.txt'
    shutil.copyfile(src, out / 'sysout/INTCALC.STEP15.txt')
    shutil.copytree(runner.joblog, out / 'joblog', dirs_exist_ok=True)
    for dsn, layout in CHAIN['outputs'].items():
        record.unload(runner, dsn, layout, out)


def run(input_dir, out, jar, repeat=False):
    out = out.resolve()
    if not out.is_relative_to(ROOT / 'work/lab/runs'):
        raise ValueError('lane output must be below work/lab/runs')
    if out.exists():
        raise ValueError(f'refusing to overwrite run: {out}')
    out.mkdir(parents=True)
    timings = {}
    scratch = out / 'scratch'
    case = out.name
    fixture = scratch / 'interest' / case
    shutil.copytree(input_dir, fixture / 'input')
    original = record.FIXTURES
    record.FIXTURES = scratch
    java = driver()
    try:
        start = time.monotonic()
        with (out / 'legacy.log').open('w') as log, contextlib.redirect_stdout(log), contextlib.redirect_stderr(log):
            record.run_case('interest', case, out / 'legacy')
            shutil.copytree(runjcl.WORK / 'joblog/interest' / case, out / 'legacy/joblog')
        timings['legacy'] = time.monotonic() - start
        if repeat:
            runner = runjcl.Runner(clock=CHAIN['clock'], joblog=out / 'legacy-second-joblog')
            next_case = scratch / 'interest' / (case + '-second')
            (next_case / 'input').mkdir(parents=True)
            for stem, layout in [('ACCTDATA', 'CVACT01Y'), ('CARDXREF', 'CVACT03Y'), ('TCATBALF', 'CVTRA01Y'), ('DISCGRP', 'CVTRA02Y')]:
                dsn = f'AWS.M2.CARDDEMO.{stem}.VSAM.KSDS'
                record.unload(runner, dsn, layout, out / 'host-state')
                shutil.copyfile(out / 'host-state/datasets' / (dsn + '.txt'), next_case / 'input' / f'AWS.M2.CARDDEMO.{stem}.PS.txt')
            start = time.monotonic()
            rc = runner.run_job(runjcl.find_jcl('INTCALC'))
            capture(runner, out / 'legacy-second', rc)
            timings['legacy-second'] = time.monotonic() - start
        for name, input_case in [('target', case)] + ([('target-second', case + '-second')] if repeat else []):
            start = time.monotonic()
            # Capture subprocess output at the file descriptor level as well as Python prints.
            with (out / (name + '.log')).open('w') as log:
                sys.stdout.flush()
                saved = os.dup(1), os.dup(2)
                try:
                    os.dup2(log.fileno(), 1)
                    os.dup2(log.fileno(), 2)
                    java.run_case(input_case, out / name, jar, str(MODULE / 'scripts/parity/intcalc_java.py'))
                    sys.stdout.flush()
                finally:
                    os.dup2(saved[0], 1)
                    os.dup2(saved[1], 2)
                    os.close(saved[0]); os.close(saved[1])
            timings[name] = time.monotonic() - start
    finally:
        record.FIXTURES = original
    (out / 'timings.json').write_text(json.dumps(timings, indent=2))


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--input-dir', type=Path, required=True)
    p.add_argument('--out', type=Path, required=True)
    p.add_argument('--jar', type=Path, default=DEFAULT_JAR)
    p.add_argument('--repeat', action='store_true')
    a = p.parse_args()
    (ROOT / 'work/lab').mkdir(parents=True, exist_ok=True)
    with (ROOT / 'work/lab/estate.lock').open('a') as lock:
        fcntl.flock(lock, fcntl.LOCK_EX)
        run(a.input_dir, a.out, a.jar, a.repeat)


if __name__ == '__main__':
    main()
