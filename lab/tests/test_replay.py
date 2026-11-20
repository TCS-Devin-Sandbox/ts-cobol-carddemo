import copy
import tempfile
import unittest
from decimal import Decimal
from pathlib import Path

from lab.classify import required
from lab.replay import ROOT, SPECS, compare_values, evaluator, intake, validate
from tools.parity.copybook import encode_record


class ReplayTests(unittest.TestCase):
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
