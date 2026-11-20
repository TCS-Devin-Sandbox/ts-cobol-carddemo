"""Pure value classification and independent rule verdicts."""
import re
from decimal import Decimal, InvalidOperation

LEGACY = 'legacy (every decision A)'
HOST_REASON = 'needs host evidence: first-return card of the AIX (Card operations / VSAM owner)'
INTAKE_REASON = 'INT-H03 open: target holds intake pending host first-return-card evidence; required outcome needs host evidence (Card operations / VSAM owner)'


def intake_hold(target, data):
    accounts = [str(row['XREF-ACCT-ID']) for row in data['CARDXREF']]
    return (target.get('failure') or {}).get('category') == 'TARGET_INTAKE_HELD' and len(accounts) != len(set(accounts))


def termination_verdicts(cell, value, held=False):
    if held:
        return {rule: {'verdict': 'NOT JUDGED', 'reason': INTAKE_REASON}
                for rule in [*cell.get('rules', []), 'INT-H03'] if rule.startswith('INT-')}
    return verdicts(cell, value)


def equal(a, b):
    if isinstance(a, Decimal) or isinstance(b, Decimal):
        try:
            return Decimal(str(a)) == Decimal(str(b))
        except InvalidOperation:
            return False
    return a == b


def required(cell):
    if 'value' in cell:
        return cell['value']
    return next(c['value'] for c in cell['candidates'] if c['decision'] == LEGACY)


def alternatives(cell):
    base = required(cell)
    return [c for c in cell.get('candidates', []) if c['decision'] != LEGACY
            and (c.get('value') is None or c.get('status') == 'UNRESOLVED' or not equal(c['value'], base))]


def register(text):
    """Read ID and Status by header, never infer approval from prose/effects."""
    header, result = None, {}
    for line in text.splitlines():
        if not line.startswith('|'):
            continue
        columns = [v.strip() for v in line.strip('|').split('|')]
        if 'ID' in columns and 'Status' in columns:
            header = columns
        elif header and re.fullmatch(r'INT-[QH]\d+', columns[0]):
            status = columns[header.index('Status')]
            state = re.search(r'\b(OPEN|APPROVED|DECIDED)\b', status, re.I)
            option = re.search(r'\b(?:option\s*|=\s*|:\s*)([ABC])\b', status, re.I)
            if not option:
                option = re.search(r'\b([ABC])\b', status)
            result[columns[0]] = {'status': state.group(1).upper() if state else status,
                                  'option': option.group(1).upper() if option else None}
    return result


def classify(cell, observed, target, decisions, host_hole=None):
    alts = alternatives(cell)
    ids = sorted({c['decision'].split('=')[0] for c in alts})
    if equal(observed, target):
        return {'class': 'UNRESOLVED' if ids else 'MATCH', 'agreement': True, 'decisions': ids}
    matched = [c for c in alts if c.get('value') is not None and equal(target, c['value'])]
    approved, opened = [], []
    for candidate in matched:
        decision, option = candidate['decision'].split('=')
        entry = decisions.get(decision, {'status': 'OPEN', 'option': None})
        if entry['status'] in ('APPROVED', 'DECIDED') and entry['option'] == option:
            approved.append(decision)
        elif entry['status'] == 'OPEN':
            opened.append(decision)
    if approved:
        return {'class': 'INTENDED_CHANGE', 'agreement': False, 'decisions': sorted(set(approved))}
    if opened or host_hole:
        return {'class': 'UNRESOLVED', 'agreement': False, 'decisions': sorted(set(opened + ([host_hole] if host_hole else [])))}
    return {'class': 'UNCLASSIFIED', 'agreement': False, 'decisions': []}


def verdicts(cell, value, host_unknown=False):
    rules = [r for r in cell.get('rules', []) if re.fullmatch(r'INT-[RQH]\d+', r)]
    if host_unknown:
        return {r: {'verdict': 'NOT JUDGED', 'reason': HOST_REASON} for r in rules}
    if equal(value, required(cell)):
        return {r: {'verdict': 'CONFORMS'} for r in rules}
    matched = {c['decision'].split('=')[0] for c in alternatives(cell)
               if c.get('value') is not None and equal(value, c['value'])}
    return {r: {'verdict': 'VIOLATES'} for r in (sorted(matched) if matched else rules)}
