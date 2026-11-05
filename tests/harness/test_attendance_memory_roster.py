"""Completeness controls for the source-derived Attendance memory gate; no JVM."""
import importlib.util
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location('attendance_roster', ROOT / 'scripts/attendance-memory/roster.py')
roster = importlib.util.module_from_spec(spec)
spec.loader.exec_module(roster)


def report(identities):
    rows = [{'classname': cls, 'method': method, 'identity': 'SOURCE_METHOD', 'status': 'PASSED'}
            for cls, method in identities]
    return {'collection': 'RESULTS', 'cases': rows,
            'summary': {'PASSED': len(rows), 'FAILED': 0, 'ERROR': 0, 'SKIPPED': 0, 'NOT_EXECUTED': 0}}


class AttendanceRosterControls(unittest.TestCase):
    def test_current_source_selects_whole_tasks_and_nested_methods(self):
        expected = roster.expected_roster(ROOT)
        for task, identities in expected.items():
            self.assertTrue(identities)
            self.assertTrue(all(cls.split('$')[0].endswith('IT') == (task == 'integrationTest')
                                for cls, method in identities))
            self.assertTrue(roster.confirm_roster(report(identities), identities))

    def test_new_method_and_equal_total_cannot_hide_missing_method(self):
        expected = {('ClassTest', 'first'), ('ClassTest', 'addedLater')}
        self.assertFalse(roster.confirm_roster(report([('ClassTest', 'first')] * 2), expected))

    def test_parameterized_invocations_do_not_pin_a_total(self):
        expected = {('ClassTest', 'parameterized')}
        self.assertTrue(roster.confirm_roster(report(list(expected) * 3), expected))

    def test_unknown_foreign_skipped_and_partial_xml_are_rejected(self):
        expected = {('ClassTest', 'first')}
        for field, value in (('method', None), ('classname', 'ForeignTest'),
                             ('identity', 'ORDINAL_ONLY'), ('status', 'SKIPPED')):
            with self.subTest(field=field):
                result = report(expected)
                result['cases'][0][field] = value
                self.assertFalse(roster.confirm_roster(result, expected))
        result = report(expected)
        result['collection'] = 'INCOMPLETE'
        self.assertFalse(roster.confirm_roster(result, expected))
        result = report(expected)
        result['summary']['PASSED'] += 1
        self.assertFalse(roster.confirm_roster(result, expected))
        self.assertFalse(roster.confirm_roster(report([]), expected))


if __name__ == '__main__':
    unittest.main()
