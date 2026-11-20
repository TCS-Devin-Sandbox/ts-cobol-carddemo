import copy
import shutil
import tempfile
import unittest
from decimal import Decimal
from pathlib import Path

from lab.classify import register, required
from lab.replay import ROOT, SPECS, compare_values, evaluator, intake, validate
from tools.parity.copybook import encode_record, parse_copybook


class ReplayTests(unittest.TestCase):
    def test_malformed_numeric_fields_are_rejected(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary) / 'input'
            shutil.copytree(ROOT / 'lab/probes/E10/input', directory)
            for stem, layout in SPECS.items():
                path = directory / f'AWS.M2.CARDDEMO.{stem}.PS.txt'
                original = path.read_bytes()
                for field in parse_copybook(layout):
                    if field.is_filler or field.kind == 'text':
                        continue
                    with self.subTest(field=field.name):
                        malformed = bytearray(original)
                        malformed[field.offset] = ord('x')
                        path.write_bytes(malformed)
                        with self.assertRaisesRegex(ValueError, f'line 1:.*{field.name}.*numeric'):
                            validate(directory)
                    path.write_bytes(original)

    def test_valid_and_empty_inputs(self):
        validate(ROOT / 'lab/probes/E10/input')
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            for stem in SPECS:
                (directory / f'AWS.M2.CARDDEMO.{stem}.PS.txt').touch()
            validate(directory)

    def test_only_target_uses_approved_rounding(self):
        directory = ROOT / 'fixtures/interest/i02-tie-half-cent/input'
        data = intake(directory)
        model = evaluator()
        expectation = model.evaluate_case(model.load_rules(), directory.parent)
        observed = {'rc': 0, 'failure': None, 'transactions': [
            {field: required(cell) for field, cell in entry.items() if field.startswith('TRAN-')}
            for entry in expectation['transactions'] if required(entry['exists'])],
            'accounts': {}, 'sysout': expectation['sysout']['value']}
        target = copy.deepcopy(observed)
        target['transactions'][0]['TRAN-AMT'] = Decimal('12.36')
        decisions = register('| ID | Status |\n| INT-Q01 | APPROVED option C |')
        values = compare_values(expectation, observed, target, data, directory, {}, decisions)
        amount = next(v for v in values if v['value'] == 'transaction 00000002003/01/0001 TRAN-AMT')
        self.assertEqual(amount['class'], 'INTENDED_CHANGE')
        self.assertEqual(amount['verdicts']['observed']['INT-Q01']['verdict'], 'CONFORMS')
        self.assertEqual(amount['verdicts']['target']['INT-Q01']['verdict'], 'CONFORMS')

    def test_zero_interest_rewrite_with_no_value_change(self):
        data = intake(ROOT / 'fixtures/interest/i02-tie-half-cent/input')
        first = data['ACCTDATA'][0]
        first['ACCT-CURR-CYC-CREDIT'] = Decimal(0)
        first['ACCT-CURR-CYC-DEBIT'] = Decimal(0)
        data['TCATBALF'][0]['TRAN-CAT-BAL'] = Decimal(0)
        account = f"{int(first['ACCT-ID']):011d}"
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary) / 'input'
            directory.mkdir()
            for stem, layout in SPECS.items():
                (directory / f'AWS.M2.CARDDEMO.{stem}.PS.txt').write_bytes(
                    b''.join(encode_record(layout, row) + b'\n' for row in data[stem]))
            model = evaluator()
            expectation = model.evaluate_case(model.load_rules(), directory.parent)
            self.assertTrue(required(expectation['accounts'][account]['updated']))
            output = {'rc': 0, 'failure': None, 'transactions': [],
                      'accounts': {f"{int(a['ACCT-ID']):011d}": a for a in data['ACCTDATA']},
                      'sysout': []}
            values = compare_values(expectation, output, copy.deepcopy(output), data, directory, {}, {})
        updated = next(v for v in values if v['value'].startswith(f'account {account} updated'))
        self.assertTrue(all(j['verdict'] == 'CONFORMS' for j in updated['verdicts']['observed'].values()))


if __name__ == '__main__':
    unittest.main()
