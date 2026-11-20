"""Replay the interest estate, rulebook, and joined Java candidate."""
import argparse
import collections
import importlib.util
import json
import os
import re
import shutil
import subprocess
import sys
import uuid
from decimal import Decimal
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
from lab.classify import INTAKE_REASON, classify, equal, intake_hold, is_open, required, register, termination_verdicts, verdicts
from tools.parity import compare
from tools.parity.copybook import decode_record, parse_copybook, split_records

CHAIN = json.loads((ROOT / 'tools/runjcl/chains.json').read_text())['interest']
RULE_DIR = ROOT / 'modernization/rules/interest'
DEFAULT_JAR = 'java/interest/interest-batch/target/interest-batch-0.1.0-PILOT.jar'
SPECS = {'ACCTDATA': 'CVACT01Y', 'CARDXREF': 'CVACT03Y', 'TCATBALF': 'CVTRA01Y', 'DISCGRP': 'CVTRA02Y'}
NO_TX = '(no transaction)'


def evaluator():
    spec = importlib.util.spec_from_file_location('interest_rulebook', RULE_DIR / 'tools/evaluate.py')
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def decoded(path, layout, length):
    if not path.exists():
        return []
    # Harness exports are line framed and may omit trailing spaces.
    data = b''.join(line.ljust(length, b' ') for line in path.read_bytes().splitlines())
    return [decode_record(layout, record) for record in split_records(data, length)]


def validate(directory):
    if not directory.is_dir():
        raise ValueError(f'input directory not found: {directory}')
    expected = {dsn + '.txt' for dsn in CHAIN['inputs']}
    names = {p.name for p in directory.iterdir()}
    if names != expected:
        raise ValueError(f'input datasets: missing {sorted(expected - names)}; unexpected {sorted(names - expected)}')
    for dsn, length in CHAIN['inputs'].items():
        path = directory / (dsn + '.txt')
        stem = dsn.split('.')[-2]
        layout = SPECS[stem]
        previous = None
        for number, line in enumerate(path.read_bytes().splitlines(), 1):
            if len(line) > length:
                raise ValueError(f'{path.name} line {number}: length {len(line)} exceeds LRECL {length}')
            try:
                row = decode_record(layout, line.ljust(length, b' '))
            except (ValueError, ArithmeticError, IndexError) as e:
                raise ValueError(f'{path.name} line {number}: cannot decode {layout}: {e}') from e
            for field in parse_copybook(layout):
                if not field.is_filler and field.kind != 'text' and not isinstance(row[field.name], Decimal):
                    raise ValueError(f'{path.name} line {number}: {field.name} must be numeric')
            if stem == 'TCATBALF':
                key = (row['TRANCAT-ACCT-ID'], row['TRANCAT-TYPE-CD'], row['TRANCAT-CD'])
                if previous is not None and key < previous:
                    raise ValueError(f'{path.name} line {number}: TCATBALF keys must be ascending')
                previous = key


def intake(directory):
    return {stem: decoded(directory / f'AWS.M2.CARDDEMO.{stem}.PS.txt', layout,
                          CHAIN['inputs'][f'AWS.M2.CARDDEMO.{stem}.PS']) for stem, layout in SPECS.items()}


def lane_output(path):
    rc = json.loads((path / 'rc.json').read_text())['INTCALC']['steps']['STEP15']
    manifest_path = path / 'manifest.json'
    manifest = json.loads(manifest_path.read_text()) if manifest_path.exists() else {}
    datasets = {}
    for file in sorted((path / 'datasets').glob('*.txt')):
        dsn = file.name[:-4]
        meta = manifest.get(dsn)
        if meta is None:
            layout = next((v for k, v in CHAIN['outputs'].items() if k.split('(')[0] in dsn), None)
            if layout is None:
                raise ValueError(f'unknown output layout: {dsn}')
            meta = {'layout': layout, 'lrecl': sum(f.length for f in parse_copybook(layout))}
        datasets[dsn] = {'layout': meta['layout'], 'records': decoded(file, meta['layout'], meta['lrecl'])}
    def rows(layout):
        return [r for dataset in datasets.values() if dataset['layout'] == layout for r in dataset['records']]
    sysout = (path / 'sysout/INTCALC.STEP15.txt').read_text(encoding='latin-1').splitlines()
    failure = path / 'pilot-failure.json'
    return {'rc': rc, 'sysout': sysout, 'datasets': datasets, 'transactions': rows('CVTRA05Y'),
            'accounts': {f"{int(r['ACCT-ID']):011d}": r for r in rows('CVACT01Y')},
            'failure': json.loads(failure.read_text()) if failure.exists() else None,
            'files': [str(f.relative_to(path)) for f in sorted(path.rglob('*')) if f.is_file() and 'intake' not in f.parts]}


def until_abend(lines):
    return lines[:lines.index('ABENDING PROGRAM') + 1] if 'ABENDING PROGRAM' in lines else lines


def stop_expectation(data, directory):
    """Rule INT-R10's restricted transcript for the three required missing lookups."""
    accounts = {int(a['ACCT-ID']): a for a in data['ACCTDATA']}
    cards = {int(x['XREF-ACCT-ID']) for x in data['CARDXREF']}
    rates = {(r['DIS-ACCT-GROUP-ID'], r['DIS-TRAN-TYPE-CD'], r['DIS-TRAN-CAT-CD']) for r in data['DISCGRP']}
    lines = ['START OF EXECUTION OF PROGRAM CBACT04C']
    raw = (directory / 'AWS.M2.CARDDEMO.TCATBALF.PS.txt').read_text(encoding='latin-1').splitlines()
    for balance, text in zip(data['TCATBALF'], raw):
        lines.append(text.ljust(50))
        account = int(balance['TRANCAT-ACCT-ID'])
        if account not in accounts or account not in cards:
            lines += [f'ACCOUNT NOT FOUND: {account:011d}', 'ERROR READING ACCOUNT FILE' if account not in accounts else 'ERROR READING XREF FILE']
            break
        suffix = balance['TRANCAT-TYPE-CD'], balance['TRANCAT-CD']
        if (accounts[account]['ACCT-GROUP-ID'], *suffix) not in rates:
            lines += ['DISCLOSURE GROUP RECORD MISSING', 'TRY WITH DEFAULT GROUP CODE']
            if ('DEFAULT', *suffix) not in rates:
                lines.append('ERROR READING DEFAULT DISCLOSURE GROUP')
                break
    return lines + ['FILE STATUS IS: NNNN0023', 'ABENDING PROGRAM']


def compare_values(expectation, observed, target, data, directory, metadata, decisions):
    values = []
    card_accounts = [str(row['XREF-ACCT-ID']) for row in data['CARDXREF']]
    multi = len(card_accounts) != len(set(card_accounts))
    held = is_open(decisions, 'INT-H03') and intake_hold(target, data)

    def add(name, cell, o, t, host_unknown=False, hole=None):
        values.append({'value': name, 'required': required(cell), 'observed': o, 'target': t,
                       'rules': cell.get('rules', []), **classify(cell, o, t, decisions, hole),
                       'verdicts': {'observed': verdicts(cell, o, host_unknown),
                                    'target': termination_verdicts(cell, t, held) if hole and held else
                                    verdicts(cell, t, host_unknown, decisions=decisions)}})

    stopped = observed['rc'] != 0 or target['rc'] != 0
    rc_cell = dict(expectation['return_code'])
    if isinstance(required(rc_cell), str) and 'ABEND' in required(rc_cell):
        rc_cell = {'value': 12, 'rules': ['INT-R10'] + (['INT-H08'] if 'INT-H08' in metadata.get('rules', []) else [])}
    add('STEP15 return_code', rc_cell, observed['rc'], target['rc'], hole='INT-H03' if held else None)
    if stopped:
        expected_lines = stop_expectation(data, directory) if required(rc_cell) == 12 else expectation['sysout']['value']
        add('SYSOUT through ABENDING PROGRAM', {'value': until_abend(expected_lines), 'rules': rc_cell['rules']},
            until_abend(observed['sysout']), until_abend(target['sysout']), hole='INT-H03' if held else None)
        return values

    expected_tx = expectation['transactions']
    # Position plus the rulebook balance key identify each emission; all fields are decoded.
    positions = {'observed': 0, 'target': 0}
    for entry in expected_tx:
        key = entry['balance_key']
        tx = {}
        for engine, output in [('observed', observed), ('target', target)]:
            pos = positions[engine]
            tx[engine] = output['transactions'][pos] if pos < len(output['transactions']) else None
            if required(entry['exists']):
                positions[engine] += 1
        add(f'transaction {key} exists', entry['exists'], tx['observed'] is not None, tx['target'] is not None)
        for field in [f.name for f in parse_copybook('CVTRA05Y') if not f.is_filler]:
            cell = entry[field]
            add(f'transaction {key} {field}', cell,
                tx['observed'].get(field) if tx['observed'] else NO_TX,
                tx['target'].get(field) if tx['target'] else NO_TX,
                host_unknown=multi and field == 'TRAN-CARD-NUM')
    add('transaction count', {'value': sum(bool(required(t['exists'])) for t in expected_tx), 'rules': ['INT-R05']},
        len(observed['transactions']), len(target['transactions']))
    original = {f"{int(a['ACCT-ID']):011d}": a for a in data['ACCTDATA']}
    changed = ['ACCT-CURR-BAL', 'ACCT-CURR-CYC-CREDIT', 'ACCT-CURR-CYC-DEBIT']
    def amounts(row):
        return {f: Decimal(str(row[f])) if row.get(f) is not None else None for f in changed[1:]}

    for account, fields in expectation['accounts'].items():
        o = observed['accounts'].get(account, {})
        t = target['accounts'].get(account, {})
        for field in changed:
            add(f'account {account} {field}', fields[field], o.get(field), t.get(field))
        def rewrite_effect(updated, decision=None):
            if not updated:
                return amounts(original[account])
            return amounts({f: next((c['value'] for c in fields[f].get('candidates', [])
                                     if c['decision'] == decision), required(fields[f])) for f in changed})

        update_cell = dict(fields['updated'])
        if 'candidates' in update_cell:
            update_cell['candidates'] = [dict(c, value=rewrite_effect(c['value'], c['decision']))
                                         for c in update_cell['candidates']]
        else:
            update_cell['value'] = rewrite_effect(update_cell['value'])
        add(f'account {account} updated (rewrite effects)', update_cell,
            amounts(o), amounts(t))
        for field, value in original[account].items():
            if field not in changed:
                add(f'account {account} unchanged {field}', {'value': value, 'rules': ['TN-06']}, o.get(field), t.get(field))
    add('account count', {'value': len(original), 'rules': ['INT-R06']}, len(observed['accounts']), len(target['accounts']))
    add('SYSOUT lines', expectation['sysout'], observed['sysout'], target['sysout'])
    add('fee transactions', expectation['fee_transactions'],
        [r for r in observed['transactions'] if r['TRAN-TYPE-CD'] != '01' or r['TRAN-CAT-CD'] != 5],
        [r for r in target['transactions'] if r['TRAN-TYPE-CD'] != '01' or r['TRAN-CAT-CD'] != 5])
    # These probes explicitly exercise holes not attached to model cells.
    for rule in metadata.get('rules', []):
        if rule in ('INT-H04', 'INT-H06', 'INT-H07'):
            add(f'{rule} replay outcome', {'value': True, 'rules': [rule]},
                all(equal(v['observed'], v['required']) for v in values),
                all(equal(v['target'], v['required']) for v in values))
    return values


def run_case(case, directory, metadata, jar, run_id, model, decisions, golden=False):
    validate(directory)
    out = ROOT / 'work/lab/runs' / run_id / case
    staging = ROOT / 'work/lab/inputs' / run_id / case / 'input'
    shutil.copytree(directory, staging)
    command = ['docker', 'compose', 'exec', '-T', '-e', 'INTEREST_PILOT_JDBC_URL=jdbc:postgresql://db:5432/carddemo',
               '-e', 'INTEREST_PILOT_DB_USER=carddemo', '-e', 'INTEREST_PILOT_DB_PASSWORD=carddemo',
               'estate', 'python3', 'lab/lanes.py', '--input-dir', str(staging.relative_to(ROOT)),
               '--out', str(out.relative_to(ROOT)), '--jar', jar]
    if metadata.get('flags', {}).get('repeat'):
        command.append('--repeat')
    process = subprocess.run(command, cwd=ROOT, capture_output=True, text=True)
    if process.returncode:
        raise RuntimeError(f'{case}: lane infrastructure failed: {process.stdout}\n{process.stderr}')
    expectation = json.loads((RULE_DIR / 'expected' / (case + '.json')).read_text()) if golden else model.evaluate_case(model.load_rules(), staging.parent)
    if not golden:
        expectation['derived_from'] = [str(p.relative_to(ROOT)) for p in sorted(staging.glob('*.txt'))]
    observed, target = lane_output(out / 'legacy'), lane_output(out / 'target')
    if is_open(decisions, 'INT-H03') and intake_hold(target, intake(staging)):
        target['outputs_judgment'] = {'verdict': 'NOT JUDGED', 'decision': 'INT-H03', 'reason': INTAKE_REASON}
    parity = compare.compare_case('interest', case, out / 'legacy')[0] if golden else None
    result = {'case': case, 'set': 'golden' if golden else 'edge', 'metadata': metadata, 'expectation': expectation,
              'observed': observed, 'target': target, 'golden_parity': None if parity is None else not parity,
              'golden_differences': parity, 'output_dir': str(out.relative_to(ROOT)),
              'timings': json.loads((out / 'timings.json').read_text()),
              'values': compare_values(expectation, observed, target, intake(staging), staging, metadata, decisions)}
    if metadata.get('flags', {}).get('repeat'):
        next_dir = out / 'scratch/interest' / (case + '-second') / 'input'
        expected2 = model.evaluate_case(model.load_rules(), next_dir.parent)
        expected2['derived_from'] = [str(p.relative_to(ROOT)) for p in sorted(next_dir.glob('*.txt'))]
        o2, t2 = lane_output(out / 'legacy-second'), lane_output(out / 'target-second')
        result['run1'] = {k: result[k] for k in ('expectation', 'observed', 'target', 'values')}
        result.update(expectation=expected2, observed=o2, target=t2,
                      values=compare_values(expected2, o2, t2, intake(next_dir), next_dir, metadata, decisions))
    return result


def counts(results):
    values = [v for result in results for v in result['values']]
    count = collections.Counter(v['class'] for v in values)
    agree = sum(v['class'] == 'UNRESOLVED' and v['agreement'] for v in values)
    return f"MATCH {count['MATCH']}, INTENDED_CHANGE {count['INTENDED_CHANGE']}, UNRESOLVED {count['UNRESOLVED']} (engines agree {agree}, engines differ {count['UNRESOLVED'] - agree}), UNCLASSIFIED {count['UNCLASSIFIED']}"


def summary(results, rules, sha, selected_gate=False):
    lines = [f'Interest replay | baseline {sha} | clock {CHAIN["clock"]} | PARM 2022071800']
    for result in results:
        lines.append(f"{result['case']}: {counts([result])}; RC observed={result['observed']['rc']} target={result['target']['rc']}")
    for group in ('golden', 'edge'):
        lines.append(f'{group}: {counts([r for r in results if r["set"] == group])}')
    lines.append(f'total: {counts(results)}')
    parity = sum(r['golden_parity'] is True for r in results)
    lines.append(f'golden parity {parity}/7')
    violations = []
    exercised = set()
    all_rules = [r['id'] for r in rules['rules'] if re.fullmatch(r'INT-[RQH]\d+', r['id'])]
    for engine in ('observed', 'target'):
        tally = collections.Counter()
        for rule in all_rules:
            evidence = [(r, v, v['verdicts'][engine][rule]) for r in results for v in r['values'] if rule in v['verdicts'][engine]]
            statuses = {x[2]['verdict'] for x in evidence}
            status = next((s for s in ('VIOLATES', 'NOT JUDGED', 'CONFORMS') if s in statuses), 'NOT EXERCISED')
            tally[status] += 1
            if evidence:
                exercised.add(rule)
            for result, value, verdict in evidence:
                if verdict['verdict'] == 'VIOLATES':
                    violation = {'engine': engine, 'rule': rule, 'case': result['case'], 'value': value['value']}
                    violations.append(violation)
                    lines.append('VIOLATES ' + '/'.join(violation.values()))
        lines.append(f'{engine} rule verdicts: ' + ', '.join(f'{s} {tally[s]}' for s in ('CONFORMS', 'VIOLATES', 'NOT JUDGED', 'NOT EXERCISED')))
    coverage = json.loads((ROOT / 'lab/coverage.json').read_text())
    possible = {r for r, entry in coverage.items() if entry['exercisable']}
    missing = sorted(possible - exercised)
    lines.append(f'Rules exercised: {len(possible & exercised)} of {len(possible)} a replay can exercise; missing: {", ".join(missing) or "none"}')
    holes = {r: entry for r, entry in coverage.items() if not entry['exercisable']}
    owners = {r['id']: r.get('decision', {}).get('owner', '') for r in rules['rules']}
    lines.append(f'Open questions no case can settle: {len(holes)}; ' + '; '.join(f'{r}: {entry["waits_for"]} ({owners[r]})' for r, entry in holes.items()))
    unclassified = any(v['class'] == 'UNCLASSIFIED' for r in results for v in r['values'])
    needed = sum(r['set'] == 'golden' for r in results) if selected_gate else 7
    reasons = ([f'{len(violations)} rule violations'] if violations else []) + (['unclassified differences'] if unclassified else []) + ([f'golden parity {parity}/{needed} required'] if parity != needed else [])
    passed = not reasons
    lines.append(f'GATE: {"PASS" if passed else "FAIL"} ({"; ".join(reasons) or ("no unclassified differences or violations; selected golden parity complete" if selected_gate else "no unclassified differences or violations; golden parity 7/7")})')
    return lines, violations, passed


def main():
    p = argparse.ArgumentParser(description=__doc__)
    sub = p.add_subparsers(dest='command', required=True)
    for name in ('gate', 'run'):
        s = sub.add_parser(name)
        s.add_argument('--jar', default=DEFAULT_JAR)
        s.add_argument('--json', nargs='?', const='-', default='work/lab/report.json')
        s.add_argument('--report', default='work/lab/report.md')
        s.add_argument('--run-id', help=argparse.SUPPRESS)
        s.add_argument('--selected-gate', action='store_true', help=argparse.SUPPRESS)
        if name == 'gate':
            s.add_argument('--set', choices=['golden', 'edge', 'all'], default='all')
        else:
            group = s.add_mutually_exclusive_group(required=True)
            group.add_argument('--case')
            group.add_argument('--input-dir', type=Path)
    args = p.parse_args()
    model = evaluator()
    rules = model.load_rules()
    decisions = register((RULE_DIR / 'decisions.md').read_text())
    selections = []
    if args.command == 'gate':
        if args.set in ('golden', 'all'):
            selections += [(f.name, f / 'input', {}, True) for f in sorted((ROOT / 'fixtures/interest').iterdir()) if f.is_dir()]
        if args.set in ('edge', 'all'):
            selections += [(f.name, f / 'input', json.loads((f / 'probe.json').read_text()), False) for f in sorted((ROOT / 'lab/probes').iterdir()) if (f / 'probe.json').exists()]
    elif args.input_dir:
        selections = [('custom', args.input_dir.resolve(), {}, False)]
    else:
        golden = ROOT / 'fixtures/interest' / args.case
        probe = ROOT / 'lab/probes' / args.case
        if golden.is_dir():
            selections = [(args.case, golden / 'input', {}, True)]
        elif (probe / 'probe.json').is_file():
            selections = [(args.case, probe / 'input', json.loads((probe / 'probe.json').read_text()), False)]
        else:
            raise ValueError(f'unknown case: {args.case}')
    # Golden fixtures contain unrelated reference datasets; stage only the four chain inputs.
    run_id = args.run_id or uuid.uuid4().hex
    if not re.fullmatch(r'[a-f0-9]{32}', run_id):
        raise ValueError('run id must be a UUID hex string')
    prepared = []
    for case, directory, metadata, golden in selections:
        if golden:
            dest = ROOT / 'work/lab/selected' / run_id / case / 'input'
            dest.mkdir(parents=True)
            for dsn in CHAIN['inputs']:
                shutil.copyfile(directory / (dsn + '.txt'), dest / (dsn + '.txt'))
            directory = dest
        validate(directory)
        prepared.append((case, directory, metadata, golden))
    results = [run_case(case, directory, metadata, args.jar, run_id, model, decisions, golden)
               for case, directory, metadata, golden in prepared]
    sha = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip()
    lines, violations, passed = summary(results, rules, sha, args.selected_gate)
    report = {'baseline_sha': sha, 'clock': CHAIN['clock'], 'parm': '2022071800', 'cases': results,
              'violations': violations, 'passed': passed, 'summary': lines}
    encoded = json.dumps(report, indent=2, default=lambda v: str(v) if isinstance(v, Decimal) else v)
    json_path = ROOT / ('work/lab/report.json' if args.json == '-' else args.json)
    json_path.parent.mkdir(parents=True, exist_ok=True)
    json_path.write_text(encoded + '\n')
    md = ROOT / args.report
    md.parent.mkdir(parents=True, exist_ok=True)
    details = ['# Interest replay', '', '```text', *lines, '```', '', '## Classified values', '']
    for result in results:
        details += [f'### {result["case"]}', '', f'Artifacts: `{result["output_dir"]}`', '',
                    '| Value | Required | Observed | Target | Class | Decisions |', '|---|---|---|---|---|---|']
        for value in result['values']:
            cells = [value['value'], value['required'], value['observed'], value['target'], value['class'], ', '.join(value['decisions'])]
            details.append('| ' + ' | '.join(str(v).replace('|', '\\|').replace('\n', '<br>') for v in cells) + ' |')
    md.write_text('\n'.join(details) + '\n')
    print(encoded if args.json == '-' else '\n'.join(lines))
    return (0 if passed else 1) if args.command == 'gate' else 0


if __name__ == '__main__':
    # Use the provisioned lab environment when invoked as plain python3.
    try:
        import yaml
    except ModuleNotFoundError:
        python = Path.home() / 'envs/lab/bin/python'
        if python.exists() and Path(sys.prefix) != python.parent.parent:
            os.execv(str(python), [str(python), str(Path(__file__).resolve()), *sys.argv[1:]])
        raise SystemExit('PyYAML is required; install it in ~/envs/lab')
    try:
        sys.exit(main())
    except ValueError as error:
        print(f'Invalid replay input: {error}', file=sys.stderr)
        sys.exit(2)
