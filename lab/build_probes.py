"""Reproduce synthetic edge inputs from i02 templates, using the shared codec."""
import json
import sys
from decimal import Decimal
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
from tools.parity.copybook import decode_record, encode_record

SPECS = {'ACCTDATA': ('CVACT01Y', 300), 'CARDXREF': ('CVACT03Y', 50),
         'TCATBALF': ('CVTRA01Y', 50), 'DISCGRP': ('CVTRA02Y', 50)}
DESIGNS = [
    ('E01', 'Positive and negative cent ties', ['INT-Q01', 'INT-Q03'], {'tie': True}, None),
    ('E02', 'Final multi-category account; untouched no-balance account; inactive expired account charged', ['INT-Q02', 'INT-Q06', 'INT-H04'], {}, None),
    ('E03', 'Blank and unknown account groups use DEFAULT', ['INT-R03'], {}, None),
    ('E04', 'Negative balance before EOF is rewritten', ['INT-Q03'], {}, None),
    ('E05', 'Missing account stops the run', ['INT-R10'], {'stop': True}, None),
    ('E06', 'Missing card cross-reference stops the run', ['INT-R10'], {'stop': True}, None),
    ('E07', 'Missing exact and DEFAULT rates stop the run', ['INT-R10', 'INT-H08'], {'stop': True}, None),
    ('E08', 'Second run reads legacy run-one host file state', ['INT-H06'], {'repeat': True}, 'INT-H06'),
    ('E09', 'Multiple cards require host AIX first-return evidence', ['INT-H03'], {'multi_card': True}, 'INT-H03'),
    ('E10', 'Nonzero rate emits zero and sub-cent interest', ['INT-Q04'], {}, None),
    ('E11', 'Negative annual rate is used as supplied', ['INT-H07'], {}, None),
]


def main():
    source = ROOT / 'fixtures/interest/i02-tie-half-cent/input'
    original = {}
    for stem, (layout, length) in SPECS.items():
        original[stem] = [decode_record(layout, line.ljust(length, b' '))
                          for line in (source / f'AWS.M2.CARDDEMO.{stem}.PS.txt').read_bytes().splitlines()]
    for ident, title, rules, flags, question in DESIGNS:
        data = {k: [dict(row) for row in rows] for k, rows in original.items()}
        for b in data['TCATBALF']:
            b['TRAN-CAT-BAL'] = Decimal('100')
        if ident == 'E01':
            data['ACCTDATA'] = []
            data['CARDXREF'] = []
            data['TCATBALF'] = []
            for i, balance in enumerate(['1235.50', '-1235.50', '1234.50', '100']):
                acct = 2003 + i
                a = dict(original['ACCTDATA'][0], **{'ACCT-ID': acct})
                x = dict(original['CARDXREF'][0], **{'XREF-ACCT-ID': acct, 'XREF-CARD-NUM': f'400000000000{acct:04d}'})
                b = dict(original['TCATBALF'][0], **{'TRANCAT-ACCT-ID': acct, 'TRAN-CAT-BAL': Decimal(balance)})
                data['ACCTDATA'].append(a); data['CARDXREF'].append(x); data['TCATBALF'].append(b)
        if ident == 'E02':
            data['ACCTDATA'][0].update({'ACCT-ACTIVE-STATUS': 'N', 'ACCT-EXPIRAION-DATE': '2000-01-01'})
            data['ACCTDATA'].append(dict(data['ACCTDATA'][0], **{'ACCT-ID': 2005}))
            data['TCATBALF'].append(dict(data['TCATBALF'][1], **{'TRANCAT-CD': 2}))
        if ident == 'E03':
            data['ACCTDATA'][0]['ACCT-GROUP-ID'] = ''
            data['ACCTDATA'][1]['ACCT-GROUP-ID'] = 'UNKNOWN'
        if ident == 'E04':
            data['TCATBALF'][0]['TRAN-CAT-BAL'] = Decimal('-300')
        if ident == 'E05':
            data['ACCTDATA'] = data['ACCTDATA'][1:]
        if ident == 'E06':
            data['CARDXREF'] = data['CARDXREF'][1:]
        if ident == 'E07':
            data['DISCGRP'] = [r for r in data['DISCGRP'] if not (r['DIS-TRAN-TYPE-CD'] == '01' and r['DIS-TRAN-CAT-CD'] == 1)]
        if ident == 'E09':
            data['CARDXREF'].append(dict(data['CARDXREF'][0], **{'XREF-CARD-NUM': '4999999999999999'}))
        if ident == 'E10':
            data['TCATBALF'][0]['TRAN-CAT-BAL'] = Decimal('0')
            data['TCATBALF'][1]['TRAN-CAT-BAL'] = Decimal('0.10')
        if ident == 'E11':
            for rate in data['DISCGRP']:
                if rate['DIS-INT-RATE'] != 0:
                    rate['DIS-INT-RATE'] = -rate['DIS-INT-RATE']
        path = ROOT / 'lab/probes' / ident
        (path / 'input').mkdir(parents=True, exist_ok=True)
        for stem, rows in data.items():
            layout, length = SPECS[stem]
            records = [encode_record(layout, row).ljust(length, b' ') for row in rows]
            if stem == 'TCATBALF':
                records.sort(key=lambda r: r[:17])
            (path / 'input' / f'AWS.M2.CARDDEMO.{stem}.PS.txt').write_bytes(b''.join(r + b'\n' for r in records))
        note = 'GnuCOBOL lab observation only; not Enterprise COBOL arithmetic, LE abend, or VSAM AIX first-return proof.'
        metadata = {'id': ident, 'title': title, 'stresses': title, 'rules': rules, 'flags': flags,
                    'gnucobol_vs_host': note, 'open_question': question,
                    'derived_from': 'fixtures/interest/i02-tie-half-cent/input'}
        (path / 'probe.json').write_text(json.dumps(metadata, indent=2) + '\n')
        (path / 'README.md').write_text(f'# {ident}: {title}\n\nDerived from synthetic i02 fixture records with the shared copybook codec.\n\nRules: {", ".join(rules)}.\n\n{note}\n' + (f'\nOpen question: {question}.\n' if question else ''))


if __name__ == '__main__':
    main()
