"""Finite prepared-probe projection controls; no Docker, Rabbit, bot or provider execution."""
import ast
import asyncio
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import re
import stat
import subprocess
import sys
import tempfile
import time
from types import SimpleNamespace
import unittest
from unittest.mock import patch
import xml.etree.ElementTree as ET

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


class PreparedCloseBarrierControls(unittest.IsolatedAsyncioTestCase):
    @classmethod
    def setUpClass(cls):
        # Load only the two protocol helpers; no bot, aio-pika or broker imports.
        tree = ast.parse((ROOT / 'services/notification-bot/tests/prepared_broker_probe.py').read_bytes())
        selected = [node for node in tree.body if isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef))
                    and node.name in {'close_ok_observer', 'close_with_barrier'}]
        class Close:
            def __init__(self, **fields):
                self.fields = fields
        class CloseOk:
            pass
        cls.frames = SimpleNamespace(Close=Close, CloseOk=CloseOk)
        cls.helpers = {'asyncio': asyncio, 'ConnectionFrames': cls.frames,
                       'ChannelFrame': SimpleNamespace(marshall=lambda channel, frames: (channel, frames))}
        exec(compile(ast.Module(body=selected, type_ignores=[]), '<pure-close-barrier>', 'exec'), cls.helpers)

    async def asyncSetUp(self):
        class Native:
            def __init__(self):
                self.write_queue = asyncio.Queue()
        self.native = Native()
        self.foreign = Native()
        self.connection = SimpleNamespace(transport=SimpleNamespace(connection=self.native))
        self.confirmed = asyncio.Event()
        self.waiters = {}
        self.handled = []
        async def original(native, frame):
            self.handled.append((native, frame))
        self.handler = self.helpers['close_ok_observer'](original, self.waiters)

    async def test_deferred_close_ok_requires_own_underlay_and_exact_frame(self):
        task = asyncio.create_task(self.helpers['close_with_barrier'](
            self.connection, self.confirmed, self.waiters))
        try:
            channel, frames = await self.native.write_queue.get()
            self.assertEqual(channel, 0)
            self.assertEqual(len(frames), 1)
            self.assertIs(type(frames[0]), self.frames.Close)
            self.assertEqual(frames[0].fields, {'reply_code': 200, 'reply_text': 'normally closed',
                                               'class_id': 0, 'method_id': 0})
            self.assertFalse(self.confirmed.is_set())
            self.assertFalse(task.done())
            await self.handler(self.foreign, self.frames.CloseOk())
            await self.handler(self.native, self.frames.Close(reply_code=200))
            self.assertFalse(self.confirmed.is_set())
            self.assertFalse(task.done())
            await self.handler(self.native, self.frames.CloseOk())
            await task
            self.assertTrue(self.confirmed.is_set())
            self.assertEqual(self.waiters, {})
            self.assertEqual(len(self.handled), 3)
        finally:
            task.cancel()
            await asyncio.gather(task, return_exceptions=True)

    async def test_reader_confirmation_survives_waiting_processor_cancellation(self):
        task = asyncio.create_task(self.helpers['close_with_barrier'](
            self.connection, self.confirmed, self.waiters))
        await self.native.write_queue.get()
        await self.handler(self.native, self.frames.CloseOk())
        task.cancel()  # Model EOF cleanup before the processor resumes from Event.wait().
        await asyncio.gather(task, return_exceptions=True)
        self.assertTrue(self.confirmed.is_set())
        self.assertEqual(self.waiters, {})

    async def test_missing_close_ok_and_preset_signal_fail_closed(self):
        original_timeout = asyncio.timeout
        with patch.object(asyncio, 'timeout', side_effect=lambda seconds: original_timeout(0)) as bounded:
            with self.assertRaises(TimeoutError):
                await self.helpers['close_with_barrier'](self.connection, self.confirmed, self.waiters)
        bounded.assert_called_once_with(2)
        self.assertFalse(self.confirmed.is_set())
        self.assertEqual(self.waiters, {})
        self.confirmed.set()
        with self.assertRaises(AssertionError):
            await self.helpers['close_with_barrier'](self.connection, self.confirmed, self.waiters)
        self.assertEqual(self.waiters, {})


class NativeFailureProjectionControls(unittest.TestCase):
    exercise = PreparedProjectionControls.exercise

    @classmethod
    def setUpClass(cls):
        cls.probe_source = (ROOT / 'services/notification-bot/tests/prepared_broker_probe.py').read_bytes()
        tree = ast.parse((ROOT / 'infra/prometheus/tests/check_pipeline.py').read_text())
        names = {'_BACKGROUND_NATIVE_SOURCE_HASHES', '_BACKGROUND_NATIVE_TASK_STAGES',
                 '_BACKGROUND_TEST_CLASS', '_BACKGROUND_TEST_METHOD', '_BACKGROUND_TEST_SOURCE',
                 '_BACKGROUND_TEST_SOURCE_HASH', '_BACKGROUND_TEST_REPORT'}
        selected = [node for node in tree.body if
                    (isinstance(node, ast.Assign) and any(isinstance(target, ast.Name) and target.id in names
                                                        for target in node.targets)) or
                    (isinstance(node, ast.FunctionDef) and node.name in
                     {'background_native_failure', 'background_test_failure', 'read_background_test_failure'})]
        cls.pipeline = {'hashlib': hashlib, 're': re, 'ET': ET, 'os': os, 'stat': stat, 'time': time, 'ROOT': ROOT}
        exec(compile(ast.Module(body=selected, type_ignores=[]), '<pure-native-projection>', 'exec'), cls.pipeline)
        cls.gradle_sources = {name: (ROOT / name).read_bytes()
                              for name in cls.pipeline['_BACKGROUND_NATIVE_SOURCE_HASHES']}
        cls.test_source = (ROOT / cls.pipeline['_BACKGROUND_TEST_SOURCE']).read_bytes()
        cls.gradle_parent = next(node for node in ast.walk(tree) if isinstance(node, ast.FunctionDef)
                                 and node.name == 'run_application_gradle')

    def trace(self, line=177, function='phase', category='TimeoutError', path=None):
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

    def assert_closed(self, result, admission_reason=None):
        keys = {'category', 'source_stage'}
        if admission_reason is not None:
            keys.add('admission_reason')
            self.assertEqual(result['admission_reason'], admission_reason)
        self.assertEqual(set(result), keys)
        serialized = json.dumps(result)
        for forbidden in ['private', str(ROOT), 'Traceback', 'Execution failed', 'first_cause',
                          self.pipeline['_BACKGROUND_TEST_CLASS'], self.pipeline['_BACKGROUND_TEST_METHOD']]:
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
        for line, function, stage in [(16, '<module>', 'MODULE_IMPORT'), (88, 'phase', 'OBSERVER_CONNECT'),
                                      (92, 'phase', 'OBSERVER_TOPOLOGY'), (101, 'phase', 'ORIGIN_PUBLISH'),
                                      (182, 'phase', 'DURABLE_READ'), (183, 'phase', 'DURABLE_ORIGINAL_PRESENT'),
                                      (184, 'phase', 'DURABLE_DECODE'), (185, 'phase', 'DURABLE_SINGLE_MESSAGE'),
                                      (187, 'phase', 'DURABLE_ENVELOPE'), (188, 'phase', 'DURABLE_PROJECTION_CUTPOINT'),
                                      (189, 'phase', 'DURABLE_NO_EARLY_COMPLETION'),
                                      (191, 'phase', 'DURABLE_QUEUE_PROVENANCE'), (192, 'phase', 'DURABLE_REQUEUE'),
                                      (204, 'phase', 'WAIT_REPLAY_COMMIT'),
                                      (210, 'phase', 'REPLAY_OUTCOME_CHECK'), (225, 'phase', 'PROBE_TEARDOWN'),
                                      (163, 'phase', 'BROKER_CLOSE_HOOK'), (178, 'phase', 'WORKER_CLOSE'),
                                      *[(line, 'close_with_barrier', 'BROKER_CLOSE_CONFIRMATION')
                                        for line in (55, 58, 61, 62, 65, 74)]]:
            result = contract.prepared_native_failure(self.trace(line, function), self.probe_source)
            self.assertEqual(result['source_stage'], stage)
            self.assert_closed(result)
        chain = self.trace() + b'\nDuring handling of the above exception, another exception occurred:\n\n' + self.trace(225)
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
        for forged in [self.trace(path='/unowned/prepared_broker_probe.py'), self.trace(179),
                       self.trace(63, 'close_with_barrier'), self.trace(function='main')]:
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
            def diagnostic_failure(started_ns):
                raise OSError('synthetic-private-io')
            namespace = dict(self.pipeline, td=Path(directory), ROOT=ROOT, _STATE=state, subprocess=subprocess, remaining_infra_only=False,
                             read_background_test_failure=diagnostic_failure)
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
            self.assertEqual(state['background_test_failure'],
                             {'category': 'UNKNOWN', 'source_stage': 'UNKNOWN', 'admission_reason': 'READ_ERROR'})
            self.assert_closed(state['background_test_failure'], 'READ_ERROR')

    def junit(self, function=None, line=408, category='java.lang.AssertionError'):
        function = function or self.pipeline['_BACKGROUND_TEST_METHOD']
        root = ET.Element('testsuite', name=self.pipeline['_BACKGROUND_TEST_CLASS'], tests='1', skipped='0',
                          failures='1', errors='0', time='1.0', timestamp='2026-10-21T00:00:00', hostname='private-host')
        ET.SubElement(root, 'properties')
        case = ET.SubElement(root, 'testcase', name=self.pipeline['_BACKGROUND_TEST_METHOD'] + '()',
                             classname=self.pipeline['_BACKGROUND_TEST_CLASS'], time='1.0')
        failure = ET.SubElement(case, 'failure', message='synthetic-private-message ID=private-marker', type=category)
        failure.text = (category + ': synthetic-private-message /private/path\n\tat '
                        + self.pipeline['_BACKGROUND_TEST_CLASS'] + '.' + function
                        + f'(RabbitDecisionRetryIT.java:{line})\n')
        ET.SubElement(root, 'system-out').text = 'synthetic-private-output'
        ET.SubElement(root, 'system-err').text = 'synthetic-private-error'
        return ET.tostring(root, encoding='utf-8', xml_declaration=True)

    def test_selected_junit_exact_identity_closed_categories_and_public_frames(self):
        classify = self.pipeline['background_test_failure']
        for category, expected in [('java.lang.AssertionError', 'ASSERTION'),
                                   ('org.opentest4j.AssertionFailedError', 'ASSERTION'),
                                   ('org.awaitility.core.ConditionTimeoutException', 'AWAIT_TIMEOUT'),
                                   ('java.lang.IllegalStateException', 'ILLEGAL_STATE'),
                                   ('java.lang.ExceptionInInitializerError', 'INITIALIZATION'),
                                   ('java.lang.NoClassDefFoundError', 'CLASS_LOADING'),
                                   ('java.lang.OutOfMemoryError', 'JAVA_MEMORY'), ('private.Error', 'UNKNOWN')]:
            result = classify(self.junit(category=category), self.test_source)
            reason = 'SIGNATURE_CLASSIFIED' if expected != 'UNKNOWN' else 'SIGNATURE_UNCLASSIFIED'
            self.assertEqual(result, {'category': expected, 'source_stage': 'SELECTED_TEST_BODY',
                                      'admission_reason': reason})
            self.assert_closed(result, reason)
        for function, line, stage in [('<clinit>', 70, 'CLASS_INITIALIZATION'),
                                      ('overrideRabbitVhost', 89, 'CONTEXT_PROPERTIES'),
                                      ('cleanQueues', 110, 'BEFORE_EACH'),
                                      ('acceptedOrigin', 566, 'TEST_ORIGIN'),
                                      ('assertExportedWorker', 601, 'TEST_WORKER_SPAN'),
                                      ('producer', 614, 'TEST_PRODUCER_SPAN'),
                                      ('exportedSpan', 628, 'TEST_EXPORTED_SPAN'),
                                      ('assertWorkerCorrelation', 637, 'TEST_WORKER_CORRELATION'),
                                      ('collectorEndpoint', 650, 'COLLECTOR_ENDPOINT'),
                                      ('writeCollectorReceipt', 675, 'COLLECTOR_RECEIPT')]:
            result = classify(self.junit(function, line), self.test_source)
            self.assertEqual(result['source_stage'], stage)
            self.assert_closed(result, 'SIGNATURE_CLASSIFIED')
        for function, line in [('privateMethod', 408), ('cleanQueues', 408),
                               ('lambda$exportedSpan$10', 628)]:
            result = classify(self.junit(function, line), self.test_source)
            self.assertEqual(result['source_stage'], 'UNKNOWN')
            self.assert_closed(result, 'SIGNATURE_UNCLASSIFIED')
        result = classify(self.junit(function='privateMethod', category='private.Error'), self.test_source)
        self.assertEqual(result, {'category': 'UNKNOWN', 'source_stage': 'UNKNOWN',
                                  'admission_reason': 'SIGNATURE_UNCLASSIFIED'})
        self.assert_closed(result, 'SIGNATURE_UNCLASSIFIED')
        root = ET.fromstring(self.junit())
        root[1][0].text = ('java.lang.AssertionError: synthetic-private\n    Caused by: private.Error\n'
                          + (root[1][0].text or '').split('\n', 1)[1])
        result = classify(ET.tostring(root), self.test_source)
        self.assertEqual(result['source_stage'], 'UNKNOWN')
        self.assert_closed(result, 'SIGNATURE_UNCLASSIFIED')
        for outputs in [(), ('system-out',), ('system-err',), ('system-out', 'system-err')]:
            root = ET.fromstring(self.junit())
            root[0].text = '\n  \t'
            for node in list(root)[2:]:
                if node.tag not in outputs:
                    root.remove(node)
            result = classify(ET.tostring(root), self.test_source)
            self.assertEqual(result, {'category': 'ASSERTION', 'source_stage': 'SELECTED_TEST_BODY',
                                      'admission_reason': 'SIGNATURE_CLASSIFIED'})
            self.assert_closed(result, 'SIGNATURE_CLASSIFIED')

    def test_selected_junit_rejects_wrong_identity_schema_xml_and_source(self):
        classify = self.pipeline['background_test_failure']
        unknown = {'category': 'UNKNOWN', 'source_stage': 'UNKNOWN'}
        raw = self.junit()
        cases = [(value, 'XML_REJECTED') for value in [b'', b'\xff', raw[:-4], b'x' * (1024**2 + 1),
                 b'<!DOCTYPE testsuite [<!ENTITY private "secret">]>' + raw,
                 raw.replace(b'UTF-8', b'UTF-16').replace(b'utf-8', b'utf-16'),
                 raw.replace(b'<testsuite ', b'<?private value?><testsuite '),
                 raw.replace(b'<testsuite ', b'<!--private--><testsuite ')]]
        for mutation in ['class', 'method', 'ordinal', 'count', 'duplicate', 'skipped', 'error', 'extra',
                         'case_attr', 'nested_failure', 'extra_failure', 'root_text', 'tail', 'properties',
                         'type_mismatch', 'missing_properties', 'properties_attr', 'properties_child',
                         'properties_text', 'misordered_properties', 'misordered_outputs',
                         'root_tag', 'root_missing_attribute', 'suite_identity', 'failure_count',
                         'case_missing_attribute', 'testcase_text', 'missing_failure',
                         'suite_shape_priority', 'suite_identity_priority', 'case_attr_priority',
                         'case_identity_priority']:
            root = ET.fromstring(raw)
            case = root[1]
            if mutation == 'class': case.set('classname', 'private.OtherIT')
            elif mutation == 'method': case.set('name', 'privateMethod()')
            elif mutation == 'ordinal': case.set('name', '[1]')
            elif mutation == 'count': root.set('tests', '2')
            elif mutation == 'duplicate': root.append(ET.fromstring(ET.tostring(case)))
            elif mutation == 'skipped': root.set('skipped', '1')
            elif mutation == 'error': case[0].tag = 'error'
            elif mutation == 'extra': root.set('private', 'value')
            elif mutation == 'case_attr': case.set('private', 'value')
            elif mutation == 'nested_failure': ET.SubElement(case[0], 'private')
            elif mutation == 'extra_failure': ET.SubElement(case, 'failure')
            elif mutation == 'root_text': root.text = 'private'
            elif mutation == 'tail': root[0].tail = 'private'
            elif mutation == 'properties': ET.SubElement(root, 'properties')
            elif mutation == 'type_mismatch': case[0].text = 'private.Error\n' + case[0].text
            elif mutation == 'missing_properties': root.remove(root[0])
            elif mutation == 'properties_attr': root[0].set('private', 'value')
            elif mutation == 'properties_child': ET.SubElement(root[0], 'property', name='private', value='secret')
            elif mutation == 'properties_text': root[0].text = 'private'
            elif mutation == 'misordered_properties':
                node = root[0]
                root.remove(node)
                root.insert(1, node)
            elif mutation == 'misordered_outputs':
                node = root[2]
                root.remove(node)
                root.insert(3, node)
            elif mutation == 'root_tag': root.tag = 'private'
            elif mutation == 'root_missing_attribute': del root.attrib['timestamp']
            elif mutation == 'suite_identity': root.set('name', 'private.OtherIT')
            elif mutation == 'failure_count': root.set('failures', '0')
            elif mutation == 'case_missing_attribute': del case.attrib['time']
            elif mutation == 'testcase_text': case.text = 'private'
            elif mutation == 'missing_failure': case.remove(case[0])
            elif mutation == 'suite_shape_priority':
                root.set('private', 'value')
                root.set('name', 'private.OtherIT')
                root.set('tests', '2')
            elif mutation == 'suite_identity_priority':
                root.set('name', 'private.OtherIT')
                root.set('tests', '2')
            elif mutation == 'case_attr_priority':
                case.set('private', 'value')
                case.set('name', 'privateMethod()')
                case.text = 'private'
            elif mutation == 'case_identity_priority':
                case.set('name', 'privateMethod()')
                case.text = 'private'
                ET.SubElement(case, 'failure')
            reason = 'XML_REJECTED'
            if mutation in {'class', 'method', 'ordinal', 'case_identity_priority'}:
                reason = 'FIXED_CASE_NOT_ADMITTED'
            elif mutation in {'count', 'skipped', 'failure_count'}:
                reason = 'FIXED_COUNTS_NOT_ADMITTED'
            elif mutation in {'extra', 'root_tag', 'root_missing_attribute', 'suite_shape_priority'}:
                reason = 'SUITE_SHAPE_NOT_ADMITTED'
            elif mutation in {'suite_identity', 'suite_identity_priority'}:
                reason = 'FIXED_SUITE_NOT_ADMITTED'
            elif mutation in {'case_attr', 'case_missing_attribute', 'case_attr_priority'}:
                reason = 'CASE_ATTRIBUTES_NOT_ADMITTED'
            elif mutation in {'extra_failure', 'testcase_text', 'missing_failure'}:
                reason = 'CASE_CONTENT_NOT_ADMITTED'
            elif mutation in {'error', 'nested_failure', 'type_mismatch'}:
                reason = 'SIGNATURE_REJECTED'
            cases.append((ET.tostring(root), reason))
        for malformed, reason in cases:
            result = classify(malformed, self.test_source)
            self.assertEqual(result, {**unknown, 'admission_reason': reason})
            self.assert_closed(result, reason)
        for source in [self.test_source + b' ', b'']:
            result = classify(raw, source)
            self.assertEqual(result, {**unknown, 'admission_reason': 'SOURCE_REJECTED'})
            self.assert_closed(result, 'SOURCE_REJECTED')

    def test_selected_junit_fixed_reader_freshness_symlink_bounds_and_stability(self):
        read = self.pipeline['read_background_test_failure']
        unknown = {'category': 'UNKNOWN', 'source_stage': 'UNKNOWN', 'admission_reason': 'REPORT_REJECTED'}
        absent = {'category': 'NO_CURRENT_RESULT', 'source_stage': 'UNKNOWN', 'admission_reason': 'NO_CURRENT_RESULT'}
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            report = root / self.pipeline['_BACKGROUND_TEST_REPORT']
            source = root / self.pipeline['_BACKGROUND_TEST_SOURCE']
            source.parent.mkdir(parents=True)
            source.write_bytes(self.test_source)
            report.parent.mkdir(parents=True)
            with patch.dict(self.pipeline, ROOT=root, time=SimpleNamespace(time_ns=lambda: ended)):
                started = ended = time.time_ns()
                self.assertEqual(read(started), absent)
                report.write_bytes(self.junit())
                # Bound the real fixture by its filesystem clock, including coarse timestamps.
                metadata = report.stat()
                started = min(metadata.st_mtime_ns, metadata.st_ctime_ns)
                ended = max(metadata.st_mtime_ns, metadata.st_ctime_ns)
                with patch.object(os, 'open', wraps=os.open) as opened:
                    self.assertEqual(read(started), {'category': 'ASSERTION', 'source_stage': 'SELECTED_TEST_BODY',
                                                    'admission_reason': 'SIGNATURE_CLASSIFIED'})
                opened.assert_called_once_with(report, os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK)
                ended += 1
                self.assertEqual(read(ended), absent)
                os.utime(report, ns=(1, 1))
                ended = max(ended, report.stat().st_ctime_ns)
                self.assertEqual(read(started), absent)
                os.utime(report, ns=(ended + 1_000_000_000,) * 2)
                metadata = report.stat()
                started = min(metadata.st_mtime_ns, metadata.st_ctime_ns) - 1
                ended = metadata.st_mtime_ns - 1
                self.assertEqual(read(started), absent)
                report.unlink()
                report.symlink_to(source)
                self.assertEqual(read(started), unknown)
                report.unlink()
                report.mkdir()
                self.assertEqual(read(started), {**unknown, 'admission_reason': 'READ_ERROR'})
                report.rmdir()
                os.mkfifo(report)
                self.assertEqual(read(started), unknown)
                report.unlink()
                report.write_bytes(b'x' * (1024**2 + 1))
                self.assertEqual(read(started), unknown)
                report.write_bytes(self.junit())
                metadata = report.stat()
                started = min(metadata.st_mtime_ns, metadata.st_ctime_ns)
                ended = max(metadata.st_mtime_ns, metadata.st_ctime_ns)
                real_fstat = os.fstat
                count = 0
                def changed(fd):
                    nonlocal count
                    result = real_fstat(fd)
                    count += 1
                    if count == 2:
                        return SimpleNamespace(**{name: getattr(result, name) + (name == 'st_size')
                                                  for name in ('st_dev', 'st_ino', 'st_mode', 'st_size',
                                                               'st_mtime_ns', 'st_ctime_ns')})
                    return result
                with patch.object(os, 'fstat', side_effect=changed):
                    self.assertEqual(read(started), unknown)
                with patch.object(os, 'open', side_effect=OSError('synthetic-private-io')):
                    result = read(started)
                    self.assertEqual(result, {**unknown, 'admission_reason': 'READ_ERROR'})
                    self.assert_closed(result, 'READ_ERROR')
                source.write_bytes(self.test_source + b' ')
                self.assertEqual(read(started), {**unknown, 'admission_reason': 'SOURCE_REJECTED'})
                source.unlink()
                self.assertEqual(read(started), {**unknown, 'admission_reason': 'READ_ERROR'})
                self.assert_closed(unknown, 'REPORT_REJECTED')
                self.assert_closed(absent, 'NO_CURRENT_RESULT')

    def test_junit_diagnostic_only_runs_for_background_nonzero_and_preserves_exit(self):
        with tempfile.TemporaryDirectory() as directory:
            for filename, code in [('background-gradle.private.log', 0), ('application-gradle.private.log', 1),
                                   ('background-gradle.private.log', 1)]:
                state = {'phase': 'ACTUAL_BACKGROUND_GRADLE'}
                calls = []
                def diagnostic(started):
                    calls.append(started)
                    return {'category': 'ASSERTION', 'source_stage': 'SELECTED_TEST_BODY',
                            'admission_reason': 'SIGNATURE_CLASSIFIED'}
                namespace = dict(self.pipeline, td=Path(directory), ROOT=ROOT, _STATE=state, subprocess=subprocess, remaining_infra_only=False,
                                 read_background_test_failure=diagnostic)
                exec(compile(ast.Module(body=[self.gradle_parent], type_ignores=[]), '<existing-parent-control>', 'exec'), namespace)
                process = SimpleNamespace(wait=lambda **kwargs: code, poll=lambda: code)
                with patch.object(subprocess, 'Popen', return_value=process):
                    if code:
                        with self.assertRaises(subprocess.CalledProcessError) as caught:
                            namespace['run_application_gradle'](['never-executed'], {}, filename, 600)
                        self.assertEqual(caught.exception.returncode, code)
                    else:
                        namespace['run_application_gradle'](['never-executed'], {}, filename, 600)
                self.assertEqual(len(calls), int(filename == 'background-gradle.private.log' and code != 0))
                if calls:
                    self.assertEqual(state['background_test_failure'],
                                     {'category': 'ASSERTION', 'source_stage': 'SELECTED_TEST_BODY',
                                      'admission_reason': 'SIGNATURE_CLASSIFIED'})
                    self.assert_closed(state['background_test_failure'], 'SIGNATURE_CLASSIFIED')
                else:
                    self.assertNotIn('background_test_failure', state)
            namespace['remaining_infra_only'] = True
            with patch.object(subprocess, 'Popen') as spawn, patch.object(Path, 'open') as log:
                with self.assertRaisesRegex(AssertionError, '^Java transport is excluded from remaining infrastructure scope$'):
                    namespace['run_application_gradle'](['never-executed'], {}, 'background-gradle.private.log', 600)
            spawn.assert_not_called()
            log.assert_not_called()


if __name__ == '__main__':
    unittest.main()
