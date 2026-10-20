"""Finite prepared-probe projection controls; no Docker, Rabbit, bot or provider execution."""
import importlib.util
import json
from pathlib import Path
import subprocess
import sys
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


if __name__ == '__main__':
    unittest.main()
