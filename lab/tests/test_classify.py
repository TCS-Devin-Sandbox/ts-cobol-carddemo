import unittest
from decimal import Decimal

from lab.classify import LEGACY, HOST_REASON, INTAKE_REASON, alternatives, classify, intake_hold, register, required, termination_verdicts, verdicts


class ClassificationTests(unittest.TestCase):
    def setUp(self):
        self.cell = {'rules': ['INT-R01', 'INT-Q01', 'INT-Q03'], 'candidates': [
            {'decision': LEGACY, 'value': '12.35'},
            {'decision': 'INT-Q01=C', 'value': '12.36'},
            {'decision': 'INT-Q03=B', 'value': '12.35'}]}

    def test_agree_open_alternative(self):
        result = classify(self.cell, Decimal('12.35'), Decimal('12.35'), {})
        self.assertEqual((result['class'], result['agreement'], result['decisions']), ('UNRESOLVED', True, ['INT-Q01']))

    def test_open_difference_still_violates_only_rounding(self):
        self.assertEqual(classify(self.cell, '12.35', '12.36', {})['class'], 'UNRESOLVED')
        self.assertEqual(verdicts(self.cell, '12.36'), {'INT-Q01': {'verdict': 'VIOLATES'}})

    def test_approved_intended_change(self):
        decisions = register('| ID | Question | Status |\n|---|---|---|\n| INT-Q01 | OPEN prose | APPROVED option C |')
        self.assertEqual(classify(self.cell, '12.35', '12.36', decisions)['class'], 'INTENDED_CHANGE')
        self.assertEqual(verdicts(self.cell, '12.36'), {'INT-Q01': {'verdict': 'VIOLATES'}})

    def test_register_uses_status_column(self):
        decisions = register('| ID | Options | Status |\n| INT-Q01 | APPROVED C | OPEN |')
        self.assertEqual(decisions['INT-Q01']['status'], 'OPEN')

    def test_unclassified_and_all_rules_violate(self):
        self.assertEqual(classify(self.cell, '12.35', '99.00', {})['class'], 'UNCLASSIFIED')
        self.assertEqual(set(verdicts(self.cell, '99.00')), set(self.cell['rules']))

    def test_plain_match_and_empty_list(self):
        self.assertEqual(classify({'value': [], 'rules': ['INT-H01']}, [], [], {})['class'], 'MATCH')
        self.assertEqual(verdicts({'value': 0, 'rules': ['INT-R10']}, 0)['INT-R10']['verdict'], 'CONFORMS')

    def test_unknown_alternative_is_open(self):
        cell = {'rules': ['INT-Q05'], 'candidates': [{'decision': LEGACY, 'value': 'clock'}, {'decision': 'INT-Q05=B', 'value': None, 'status': 'UNRESOLVED'}]}
        self.assertEqual(len(alternatives(cell)), 1)
        self.assertEqual(classify(cell, 'clock', 'clock', {})['decisions'], ['INT-Q05'])
        self.assertEqual(classify(cell, 'clock', 'invented', {})['class'], 'UNCLASSIFIED')

    def test_host_card_not_judged(self):
        self.assertEqual(verdicts({'value': 'card', 'rules': ['INT-H03']}, 'other', True),
                         {'INT-H03': {'verdict': 'NOT JUDGED', 'reason': HOST_REASON}})
        self.assertEqual(classify({'value': 0}, 0, 12, {}, 'INT-H03')['decisions'], ['INT-H03'])

    def test_bad_expectation_has_no_silent_default(self):
        with self.assertRaises(StopIteration):
            required({'candidates': []})

    def test_multicard_intake_hold_exemption_is_narrow(self):
        data = {'CARDXREF': [{'XREF-ACCT-ID': 2003}, {'XREF-ACCT-ID': 2003}]}
        cell = {'value': 0, 'rules': ['INT-R10']}
        held = {'rc': 12, 'failure': {'category': 'TARGET_INTAKE_HELD'}}
        self.assertTrue(intake_hold(held, data))
        judgment = termination_verdicts(cell, 12, intake_hold(held, data))
        self.assertEqual(judgment['INT-R10'], {'verdict': 'NOT JUDGED', 'reason': INTAKE_REASON})
        self.assertEqual(judgment['INT-H03']['verdict'], 'NOT JUDGED')
        other = {'rc': 12, 'failure': {'category': 'REQUIRED_LOOKUP_MISSING'}}
        self.assertEqual(termination_verdicts(cell, 12, intake_hold(other, data)), {'INT-R10': {'verdict': 'VIOLATES'}})
        self.assertFalse(intake_hold(held, {'CARDXREF': [{'XREF-ACCT-ID': 2003}]}))


if __name__ == '__main__':
    unittest.main()
