"""Completeness controls for the Attendance native result gate; no JVM."""
import importlib.util
import os
from pathlib import Path
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location('attendance_native_results', ROOT / 'scripts/attendance-memory/native_results.py')
native_results = importlib.util.module_from_spec(spec)
spec.loader.exec_module(native_results)


def report(rows):
    return {'collection': 'RESULTS', 'cases': rows,
            'summary': {status: sum(row['status'] == status for row in rows)
                        for status in ('PASSED', 'FAILED', 'ERROR', 'SKIPPED', 'NOT_EXECUTED')}}


class AttendanceNativeResultControls(unittest.TestCase):
    def test_ordinal_only_and_parameterized_invocations_pass(self):
        unnamed = {'classname': None, 'method': None, 'identity': 'ORDINAL_ONLY', 'status': 'PASSED'}
        named = {'classname': 'SuiteTest', 'method': 'parameterized', 'identity': 'SOURCE_METHOD', 'status': 'PASSED'}
        for rows in ([unnamed], [named, unnamed], [named, unnamed] * 3):
            with self.subTest(rows=len(rows)):
                self.assertTrue(native_results.successful_results(report(rows)))

    def test_native_failure_error_skip_and_nonexecution_are_rejected(self):
        passed = {'status': 'PASSED'}
        for status in ('FAILED', 'ERROR', 'SKIPPED', 'NOT_EXECUTED'):
            with self.subTest(status=status):
                self.assertFalse(native_results.successful_results(report([passed, {'status': status}])))

    def test_empty_incomplete_or_inconsistent_results_are_rejected(self):
        self.assertFalse(native_results.successful_results(report([])))
        result = report([{'status': 'PASSED'}])
        result['collection'] = 'INCOMPLETE'
        self.assertFalse(native_results.successful_results(result))
        result['collection'] = 'RESULTS'
        result['summary']['PASSED'] += 1
        self.assertFalse(native_results.successful_results(result))

    def test_artifact_must_be_nonempty_and_from_current_run(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            path = root / 'test.xml'
            self.assertFalse(native_results.current_artifact(path, 1))
            self.assertFalse(native_results.current_artifact(root, 1))
            path.touch()
            self.assertFalse(native_results.current_artifact(path, 1))
            path.write_bytes(b'<testsuite/>')
            os.utime(path, ns=(1_000_000_000, 1_000_000_000))
            written = path.stat().st_mtime_ns
            self.assertTrue(native_results.current_artifact(path, written))
            self.assertFalse(native_results.current_artifact(path, written + 1))
            self.assertFalse(native_results.current_artifact(path, None))


if __name__ == '__main__':
    unittest.main()
