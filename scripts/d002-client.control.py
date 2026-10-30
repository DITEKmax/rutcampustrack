"""Pure D002 exact selector and partial receipt controls. No runtime resources."""
import importlib.util
import copy
from pathlib import Path
import re
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('lane', ROOT / 'scripts/local_e2e_async.py')
lane = importlib.util.module_from_spec(spec)
spec.loader.exec_module(lane)
spec = importlib.util.spec_from_file_location('replay', ROOT / 'scripts/local-release/browser-replay.py')
replay = importlib.util.module_from_spec(spec)
spec.loader.exec_module(replay)
lane = replay.lane


class D002Controls(unittest.TestCase):
    def test_exact_selection_is_opt_in_and_default_arguments_unchanged(self):
        self.assertEqual(lane.client_case_arguments(None), [])
        args = lane.client_case_arguments(lane.D002_CASE)
        self.assertEqual(args[0], '--grep')
        self.assertTrue(re.search(args[1], lane.D002_TITLE))
        self.assertFalse(re.search(args[1], lane.D002_TITLE + ' altered'))
        with self.assertRaises(lane.AdmissionError):
            lane.client_case_arguments('HEADMAN_TEACHER_READ')
        for mode, release in [('client', None), ('ui-batch', 'ui-batch'), ('client', 'webkit')]:
            with self.assertRaises(lane.AdmissionError):
                lane.Runner(mode, release_lane=release, client_case=lane.D002_CASE)
        self.assertNotIn('RCT_LOCAL_CLIENT_CASE', lane.Runner('client').env)

    def test_manifest_and_environment_must_agree(self):
        runner = lane.Runner('client', release_lane='client', client_case=lane.D002_CASE)
        value = {'mode': 'client', 'release_lane': 'client', 'client_case': lane.D002_CASE,
                 'run': str(runner.run), 'nonce': runner.nonce, 'startup_diagnostic': False}
        lane.validate_lane_environment(value, runner.env, expected_mode='client')
        for field, bad in [('client_case', None), ('client_case', 'unknown'), ('release_lane', 'webkit')]:
            changed = {**value, field: bad}
            with self.assertRaises(lane.AdmissionError):
                lane.validate_lane_environment(changed, runner.env, expected_mode='client')
        changed = {**runner.env, 'RCT_LOCAL_CLIENT_CASE': ''}
        with self.assertRaises(lane.AdmissionError):
            lane.validate_lane_environment(value, changed)

    def report(self, status='failed'):
        return {'status': 'failed' if status != 'passed' else 'passed', 'runnerErrors': 0,
                'unsafeArtifact': False, 'suppressedStdoutBytes': 0, 'suppressedStderrBytes': 0,
                'results': [{'caseId': lane.D002_CASE, 'project': 'chromium', 'file': 'mobile-real-server.spec.ts',
                             'expectedStatus': 'passed', 'status': status}]}

    def test_only_one_exact_case_can_supply_partial_counts(self):
        for state, counts in [('passed', (1, 0, 0)), ('failed', (0, 1, 0)), ('skipped', (0, 0, 1))]:
            actual = replay.d002_counts(self.report(state))
            self.assertEqual((actual['passed'], actual['failed'], actual['notRun']), counts)
            self.assertEqual(actual['selected'], 1)
            self.assertIs(actual['fullAcceptance'], False)
        for mutate in [lambda r: r.update(runnerErrors=1), lambda r: r.update(unsafeArtifact=True),
                       lambda r: r['results'].append(copy.deepcopy(r['results'][0])),
                       lambda r: r['results'][0].update(caseId='HEADMAN_TEACHER_READ'),
                       lambda r: r['results'][0].update(project='webkit')]:
            report = self.report(); mutate(report)
            with self.assertRaises(lane.AdmissionError): replay.d002_counts(report)


if __name__ == '__main__':
    unittest.main()

