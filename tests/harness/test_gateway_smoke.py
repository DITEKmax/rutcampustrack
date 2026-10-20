"""Independent bounded HTTP consumer controls. Never contact a server or print response data."""
import importlib.util
from pathlib import Path
from types import SimpleNamespace
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('gateway_smoke', Path(__file__).parents[2] / 'scripts/gateway-smoke.py')
smoke = importlib.util.module_from_spec(spec)
spec.loader.exec_module(smoke)


class HttpConsumerControls(unittest.TestCase):
    def test_exact_status_json_and_body_read_fail_closed(self):
        gateway = smoke.Gateway('https://synthetic.invalid')
        gateway.token = 'SYNTHETIC-ONLY'
        class Reply:
            status = 500
            body = b'{"status":"UP"}'
            def __enter__(self): return self
            def __exit__(self, *args): pass
            def read(self, limit): return self.body
        reply = Reply()
        gateway.client = SimpleNamespace(open=lambda *_args, **_kwargs: reply)
        with self.assertRaises(smoke.SmokeFailure):
            gateway.call('GET', '/synthetic', 200)
        reply.status = 200
        self.assertEqual(gateway.call('GET', '/synthetic', 200), {'status': 'UP'})
        reply.body = b'not JSON'
        with self.assertRaises(ValueError):
            gateway.call('GET', '/synthetic', 200)
        reply.body = b'x' * (2 * 1024 * 1024 + 1)
        with self.assertRaises(smoke.SmokeFailure):
            gateway.call('GET', '/synthetic', 200)

    def test_current_public_key_json_owned_group_and_independent_exact_denial(self):
        actor = {'groupId': 7, 'groupName': 'Synthetic', 'forbiddenActor': {}}
        calls = []
        class Gateway:
            def __init__(self, _base): pass
            def login(self, _actor): pass
            def call(self, method, path, status, **options):
                calls.append((method, path, status))
                if path == '/api/auth/public-key':
                    return {'publicKey': '-----BEGIN PUBLIC KEY-----\nU1lOVEhFVElD\n-----END PUBLIC KEY-----\n'}
                return {'id': 7, 'name': 'Synthetic'}
        with patch.object(smoke, 'Gateway', Gateway):
            smoke.run('gateway', 'https://synthetic.invalid', actor)
        self.assertEqual(calls, [('GET', '/api/auth/public-key', 200),
                                ('GET', '/api/academic/groups/7', 200),
                                ('GET', '/api/academic/groups/7', 401),
                                ('GET', '/api/academic/groups/7', 403)])

    def test_owned_lesson_date_and_logout_revocation_are_required(self):
        actor = {'groupId': 7, 'lessonId': 11, 'date': '2026-10-20'}
        calls = []
        class Gateway:
            def __init__(self, _base): pass
            def login(self, _actor): pass
            def call(self, method, path, status, **options):
                calls.append((method, status))
                if path == '/actuator/health': return {'status': 'UP'}
                return {'_embedded': {'lessonResponseList': [{'id': 11, 'groupId': 7, 'date': '2026-10-20'}]}}
        with patch.object(smoke, 'Gateway', Gateway):
            smoke.run('schedule', 'https://synthetic.invalid', actor)
            self.assertEqual(calls, [('GET', 200), ('GET', 200), ('POST', 204), ('GET', 401)])
            actor['date'] = '2026-10-19'
            with self.assertRaises(smoke.SmokeFailure):
                smoke.run('schedule', 'https://synthetic.invalid', actor)


if __name__ == '__main__':
    unittest.main()
