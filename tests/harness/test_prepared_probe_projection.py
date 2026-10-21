"""Finite prepared-probe projection controls; no Docker, Rabbit, bot or provider execution."""
import ast
import hashlib
import importlib.util
import json
from pathlib import Path
import re
import subprocess
import sys
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'infra/prometheus/tests'))
spec = importlib.util.spec_from_file_location('exporter_contract', ROOT / 'infra/prometheus/tests/check_exporters.py')
contract = importlib.util.module_from_spec(spec)
spec.loader.exec_module(contract)


def receipt():
    return {'prepared_transaction_probe': [
        {'boundary': boundary, 'durable_outcomes': 1, 'projection_after_loss': boundary == 'commit_after',
         'completion_before_replay': False, 'replay_provider_deliveries': 1}
        for boundary in ('publish', 'ack', 'commit_before', 'commit_after')
    ]}


class PreparedProjectionControls(unittest.TestCase):
    def exercise(self, raw, status=0, cleanup=0):
        calls = []
        owned = []
        def docker(*args, **options):
            calls.append(args)
            if args[0] == 'port':
                return subprocess.CompletedProcess([], 0, stdout='127.0.0.1:56789\n')
            if 'add_vhost' in args:
                owned.append(args[-1])
            if 'delete_vhost' in args:
                self.assertEqual(args[-1], owned[0])
                return subprocess.CompletedProcess([], cleanup)
            return subprocess.CompletedProcess([], 0)
        def child(command, **options):
            self.assertEqual(command, [sys.executable, '-B', '-m', 'tests.prepared_broker_probe'])
            self.assertEqual(options['timeout'], 120)
            self.assertTrue(options['env']['SCHEDULE_BOT_AUDIT_RABBIT_URL'].endswith('/' + owned[0]))
            self.assertEqual(options['env']['OTEL_SDK_DISABLED'], 'true')
            options['stdout'].write(raw)
            return subprocess.CompletedProcess(command, status)
        try:
            with patch.object(contract.subprocess, 'run', side_effect=child):
                return contract.prepared_transaction_contract(docker)
        finally:
            self.assertEqual(len([args for args in calls if 'delete_vhost' in args]), 1)
            self.assertTrue(owned[0].startswith('audit.prepared.'))

    def test_exact_closed_four_phase_result_and_cleanup(self):
        projected = self.exercise(json.dumps(receipt()).encode())
        self.assertTrue(projected['broker_only'])
        self.assertTrue(projected['owned_vhost_removed'])
        self.assertEqual(len(projected['phases']), 4)

    def test_missing_extra_wrong_types_duplicates_overflow_and_failures_stay_failed(self):
        unknown = receipt()
        unknown['private'] = 'synthetic-private-marker'
        wrong = receipt()
        wrong['prepared_transaction_probe'][0]['durable_outcomes'] = True
        early = receipt()
        early['prepared_transaction_probe'][0]['completion_before_replay'] = True
        missing = receipt()
        missing['prepared_transaction_probe'].pop()
        duplicate = b'{"prepared_transaction_probe":[],"prepared_transaction_probe":[]}'
        for raw in [b'not-json', json.dumps(unknown).encode(), json.dumps(wrong).encode(),
                    json.dumps(early).encode(), json.dumps(missing).encode(), duplicate, b'x' * 4097]:
            with self.assertRaises((AssertionError, ValueError)):
                self.exercise(raw)
        with self.assertRaises(AssertionError):
            self.exercise(json.dumps(receipt()).encode(), status=1)
        with self.assertRaises(AssertionError):
            self.exercise(json.dumps(receipt()).encode(), cleanup=1)


class NativeFailureProjectionControls(unittest.TestCase):
    exercise = PreparedProjectionControls.exercise

    @classmethod
    def setUpClass(cls):
        cls.probe_source = (ROOT / 'services/notification-bot/tests/prepared_broker_probe.py').read_bytes()
        tree = ast.parse((ROOT / 'infra/prometheus/tests/check_pipeline.py').read_text())
        names = {'_BACKGROUND_NATIVE_SOURCE_HASHES', '_BACKGROUND_NATIVE_TASK_STAGES'}
        selected = [node for node in tree.body if
                    (isinstance(node, ast.Assign) and any(isinstance(target, ast.Name) and target.id in names
                                                        for target in node.targets)) or
                    (isinstance(node, ast.FunctionDef) and node.name == 'background_native_failure')]
        cls.pipeline = {'hashlib': hashlib, 're': re}
        exec(compile(ast.Module(body=selected, type_ignores=[]), '<pure-native-projection>', 'exec'), cls.pipeline)
        cls.gradle_sources = {name: (ROOT / name).read_bytes()
                              for name in cls.pipeline['_BACKGROUND_NATIVE_SOURCE_HASHES']}
        cls.gradle_parent = next(node for node in ast.walk(tree) if isinstance(node, ast.FunctionDef)
                                 and node.name == 'run_application_gradle')

    def trace(self, line=128, function='phase', category='TimeoutError', path=None):
        source_line = self.probe_source.decode().splitlines()[line - 1].strip()
        path = path or str(ROOT / 'services/notification-bot/tests/prepared_broker_probe.py')
        return ('Traceback (most recent call last):\n'
                f'  File "{path}", line {line}, in {function}\n'
                f'    {source_line}\n{category}: synthetic-private-message /private/path ID=private-marker\n').encode()

    def gradle(self, task_line=None):
        task_line = task_line or next(iter(self.pipeline['_BACKGROUND_NATIVE_TASK_STAGES']))
        return ('synthetic-private-prefix\nFAILURE: Build failed with an exception.\n\n'
                f'* What went wrong:\n{task_line}\n> synthetic-private-message /private/path ID=private-marker\n'
                '\nBUILD FAILED in 2m 15s\n3 actionable tasks: 1 executed, 2 up-to-date\n').encode()

    def assert_closed(self, result):
        self.assertEqual(set(result), {'category', 'source_stage'})
        serialized = json.dumps(result)
        for forbidden in ['private', str(ROOT), 'Traceback', 'Execution failed', 'first_cause']:
            self.assertNotIn(forbidden, serialized)

    def test_prepared_positive_categories_stages_and_terminal_chain_privacy(self):
        for category in ['ModuleNotFoundError', 'ImportError', 'AssertionError', 'TimeoutError', 'ConnectionError',
                         'OSError', 'RuntimeError', 'ValueError', 'TypeError', 'KeyError']:
            result = contract.prepared_native_failure(self.trace(category=category), self.probe_source)
            self.assertEqual(result, {'category': category, 'source_stage': 'WAIT_FIRST_DISCONNECT'})
            self.assert_closed(result)
        frozen = self.trace().replace(b'Traceback (most recent call last):\n',
                                     b'Traceback (most recent call last):\n  File "<frozen runpy>", line 198, in _run_module_as_main\n')
        self.assertEqual(contract.prepared_native_failure(frozen, self.probe_source)['source_stage'], 'WAIT_FIRST_DISCONNECT')
        for line, function, stage in [(16, '<module>', 'MODULE_IMPORT'), (47, 'phase', 'OBSERVER_CONNECT'),
                                      (51, 'phase', 'OBSERVER_TOPOLOGY'), (60, 'phase', 'ORIGIN_PUBLISH'),
                                      (133, 'phase', 'DURABLE_OUTCOME_CHECK'), (155, 'phase', 'WAIT_REPLAY_COMMIT'),
                                      (159, 'phase', 'REPLAY_OUTCOME_CHECK'), (174, 'phase', 'PROBE_TEARDOWN')]:
            result = contract.prepared_native_failure(self.trace(line, function), self.probe_source)
            self.assertEqual(result['source_stage'], stage)
            self.assert_closed(result)
        chain = self.trace() + b'\nDuring handling of the above exception, another exception occurred:\n\n' + self.trace(174)
        result = contract.prepared_native_failure(chain, self.probe_source)
        self.assertEqual(result['source_stage'], 'PROBE_TEARDOWN')
        self.assert_closed(result)  # Terminal teardown signature does not assert the earlier first cause.

    def test_prepared_unknown_forged_malformed_hash_and_cutoff(self):
        unknown = {'category': 'UNKNOWN', 'source_stage': 'UNKNOWN'}
        raw = self.trace()
        for malformed in [b'', b'not a traceback\n', raw[:-1], raw + b'not a summary\n', b'\xff\n',
                          b'\x00' + raw, b'\x1b[31m' + raw, b'x' * 65537,
                          raw.replace(b'    await asyncio.wait_for(interrupted.wait(), 8)', b'    forged_private()')]:
            self.assertEqual(contract.prepared_native_failure(malformed, self.probe_source), unknown)
        self.assertEqual(contract.prepared_native_failure(raw, self.probe_source + b' '), unknown)
        self.assertEqual(contract.prepared_native_failure(raw, b''), unknown)
        for forged in [self.trace(path='/unowned/prepared_broker_probe.py'), self.trace(129), self.trace(function='main')]:
            result = contract.prepared_native_failure(forged, self.probe_source)
            self.assertEqual(result['source_stage'], 'UNKNOWN')
            self.assert_closed(result)
        result = contract.prepared_native_failure(self.trace(category='private.InventedError'), self.probe_source)
        self.assertEqual(result['category'], 'UNKNOWN')
        self.assert_closed(result)
        at_limit = b'x' * (65536 - len(raw) - 1) + b'\n' + raw
        self.assertEqual(len(at_limit), 65536)
        self.assertEqual(contract.prepared_native_failure(at_limit, self.probe_source)['category'], 'TimeoutError')
        self.assertEqual(contract.prepared_native_failure(b'x' + at_limit, self.probe_source), unknown)

    def test_gradle_positive_all_exact_tasks_and_private_messages(self):
        classify = self.pipeline['background_native_failure']
        for task, stage in self.pipeline['_BACKGROUND_NATIVE_TASK_STAGES'].items():
            result = classify(self.gradle(task), self.gradle_sources)
            self.assertEqual(result, {'category': 'FAILED_TASK', 'source_stage': stage})
            self.assert_closed(result)
        self.assertEqual(classify(self.gradle().replace(b'\n', b'\r\n'), self.gradle_sources)['category'], 'FAILED_TASK')

    def test_gradle_unknown_forged_malformed_hash_and_cutoff(self):
        classify = self.pipeline['background_native_failure']
        unknown = {'category': 'UNKNOWN', 'source_stage': 'UNKNOWN'}
        raw = self.gradle()
        task = next(iter(self.pipeline['_BACKGROUND_NATIVE_TASK_STAGES'])).encode()
        cases = [b'', b'> Task :private:invented FAILED\n', b'\xff\n', raw[:-1], b'\x00' + raw,
                 b'\x1b[31m' + raw, raw + b'private trailing text\n', b'x' * 65537,
                 self.gradle("Execution failed for task ':private:invented'."),
                 raw.replace(task, task + b'\n' + task), raw.replace(b'* What went wrong:\n', b''),
                 raw.replace(b'BUILD FAILED in 2m 15s', b'BUILD FAILED'),
                 raw.replace(b'FAILURE:', b'FAILURE: duplicate\nFAILURE:')]
        for malformed in cases:
            self.assertEqual(classify(malformed, self.gradle_sources), unknown)
        for name in self.gradle_sources:
            changed = dict(self.gradle_sources)
            changed[name] += b' '
            self.assertEqual(classify(raw, changed), unknown)
        self.assertEqual(classify(raw, {}), unknown)
        at_limit = b'x' * (65536 - len(raw) - 1) + b'\n' + raw
        self.assertEqual(classify(at_limit, self.gradle_sources)['category'], 'FAILED_TASK')
        self.assertEqual(classify(b'x' + at_limit, self.gradle_sources), unknown)

    def test_optional_diagnostic_io_failure_keeps_original_parent_failures(self):
        with patch.dict(contract._STATE, {'probe_returncode': 13}):
            with patch.object(Path, 'open', side_effect=OSError('synthetic-private-io')):
                with self.assertRaisesRegex(AssertionError, '^Prepared transaction native probe failed$'):
                    self.exercise(json.dumps(receipt()).encode(), status=1)
            self.assertEqual(contract._STATE['probe_returncode'], 13)
            self.assertEqual(contract._STATE['phase'], 'PREPARED_TRANSACTION_NATIVE_CHILD_EXIT')
            self.assertEqual(contract._STATE['native_failure'], {'category': 'UNKNOWN', 'source_stage': 'UNKNOWN'})
        with tempfile.TemporaryDirectory() as directory:
            state = {'phase': 'ACTUAL_BACKGROUND_GRADLE'}
            namespace = dict(self.pipeline, td=Path(directory), ROOT=ROOT, _STATE=state, subprocess=subprocess)
            exec(compile(ast.Module(body=[self.gradle_parent], type_ignores=[]), '<existing-parent-control>', 'exec'), namespace)
            process = SimpleNamespace(wait=lambda **kwargs: 1, poll=lambda: 1)
            with patch.object(subprocess, 'Popen', return_value=process):
                with patch.object(Path, 'open', side_effect=[open(Path(directory) / 'background-gradle.private.log', 'wb'),
                                                           OSError('synthetic-private-io')]):
                    with self.assertRaises(subprocess.CalledProcessError) as caught:
                        namespace['run_application_gradle'](['never-executed'], {}, 'background-gradle.private.log', 600)
            self.assertEqual(caught.exception.returncode, 1)
            self.assertEqual(state['phase'], 'ACTUAL_BACKGROUND_GRADLE')
            self.assertEqual(state['native_failure'], {'category': 'UNKNOWN', 'source_stage': 'UNKNOWN'})


if __name__ == '__main__':
    unittest.main()
