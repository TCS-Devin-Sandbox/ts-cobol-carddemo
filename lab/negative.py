"""Build a private rounding mutation and check that the gate detects only ties."""
import json
import os
import shutil
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def main():
    source = ROOT / 'java/interest/interest-batch'
    copy = ROOT / 'work/lab-negative'
    # Refresh only the private mutation workspace, never the estate module.
    if copy.exists():
        shutil.rmtree(copy)
    shutil.copytree(source, copy, ignore=shutil.ignore_patterns('target'))
    rule = copy / 'src/main/java/com/carddemo/interest/calc/InterestRules.java'
    text = rule.read_text()
    if text.count('RoundingMode.DOWN') != 1:
        raise ValueError('expected exactly one truncation site in InterestRules')
    rule.write_text(text.replace('RoundingMode.DOWN', 'RoundingMode.HALF_EVEN'))
    env = dict(os.environ, JAVA_HOME=os.environ.get('JAVA21_HOME', '/usr/lib/jvm/java-21-openjdk-amd64'))
    env.pop('JDK_JAVA_OPTIONS', None)
    subprocess.run([os.environ.get('MVN', 'mvn'), '-q', 'package', '-DskipTests'], cwd=copy, env=env, check=True)
    result = subprocess.run([sys.executable, str(ROOT / 'lab/replay.py'), 'gate', '--jar',
                             'work/lab-negative/target/interest-batch-0.1.0-PILOT.jar',
                             '--json', 'work/lab/negative.json', '--report', 'work/lab/negative.md'], cwd=ROOT)
    report = json.loads((ROOT / 'work/lab/negative.json').read_text())
    expected = {'i02-tie-half-cent'} | {r['case'] for r in report['cases'] if r['metadata'].get('flags', {}).get('tie')}
    actual = {(v['engine'], v['rule'], v['case']) for v in report['violations']}
    ok = result.returncode == 1 and actual == {('target', 'INT-Q01', case) for case in expected}
    print(f'NEGATIVE CONTROL: {"OK" if ok else "NOT OK"} (expected target/INT-Q01 on {", ".join(sorted(expected))} only)', flush=True)
    return 0 if ok else 1


if __name__ == '__main__':
    sys.exit(main())
