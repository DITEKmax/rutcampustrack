"""Synthetic bundle trust boundary and first-install invariants; no providers."""
import base64
import importlib.util
import json
from types import SimpleNamespace
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location('deployment_secrets', ROOT / 'scripts/deployment-secrets.py')
M = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(M)


class DeploymentSecrets(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temporary = tempfile.TemporaryDirectory(prefix='rct-deploy-prep-test-')
        cls.root = Path(cls.temporary.name)
        cls.local = cls.root / 'local'
        result = subprocess.run(['python3', str(ROOT / 'scripts/deployment-secrets.py'), 'prepare',
                                 '--mode', 'local', '--output', str(cls.local)], capture_output=True)
        if result.returncode:
            raise AssertionError(result.stderr.decode())
        cls.bundle = (cls.local / 'PROD_SECRET_FILES_B64').read_bytes()
        cls.values = M.parse_env((cls.local / '.env').read_bytes())
        cls.files = M.decode_bundle(cls.bundle)

    @classmethod
    def tearDownClass(cls):
        cls.temporary.cleanup()

    def test_complete_bundle_and_native_cert_validation(self):
        M.validate(self.values, self.files, self.local / 'files')
        self.assertLess(len(self.bundle), 48 * 1024)
        self.assertEqual(len(self.files), 41)

    def test_malicious_file_sets_and_duplicate_keys_are_rejected(self):
        original = json.loads(base64.b64decode(self.bundle))
        for name in ('../../escape', '/absolute', 'auth-tls/../escape', 'unknown'):
            obj = dict(original, files={**original['files'], name: 'eA=='})
            with self.subTest(name=name), self.assertRaises(ValueError):
                M.decode_bundle(base64.b64encode(json.dumps(obj).encode()))
        with self.assertRaises(ValueError):
            M.decode_bundle(base64.b64encode(b'{"version":1,"version":1,"files":{}}'))
        missing = dict(original, files=dict(original['files']))
        missing['files'].pop(next(iter(missing['files'])))
        with self.assertRaises(ValueError):
            M.decode_bundle(base64.b64encode(json.dumps(missing).encode()))
        with self.assertRaises(ValueError):
            M.decode_bundle(b'A' * (48 * 1024 + 1))

    def test_credential_independence_and_acl_agreement(self):
        files = dict(self.files)
        files[M.FILES['PG_SCHEDULE_RUNTIME_PASSWORD_FILE']] = files[M.FILES['PG_AUTH_RUNTIME_PASSWORD_FILE']]
        with self.assertRaisesRegex(ValueError, 'INDEPENDENT'):
            M.validate(self.values, files, self.local / 'files')
        files = dict(self.files)
        files[M.FILES['REDIS_SERVER_ACL_FILE']] = b'user default on nopass +@all\n'
        with self.assertRaisesRegex(ValueError, 'ACL_MAP'):
            M.validate(self.values, files, self.local / 'files')

    def test_wrong_key_pair_rejected(self):
        files = dict(self.files)
        files['auth-tls/auth-server.key'] = files['academic-tls/academic-server.key']
        with self.assertRaisesRegex(ValueError, 'KEY_CERT_MISMATCH'):
            M.validate(self.values, files, self.local / 'files')

    def test_monitor_password_format_and_independence_even_when_acl_matches(self):
        cache = Path(self.values['REDIS_CACHE_RUNTIME_PASSWORD_FILE']).read_text().strip()
        other = json.loads(self.files[M.FILES['REDIS_CACHE_MONITOR_PASSWORD_MAP_FILE']])['redis://redis-cache:6379']
        for value in ('', 'short', 'A' * 31 + ':', None, 42, True, [], {}, other,
                      self.values['REDIS_PASSWORD'], cache):
            with self.subTest(value_type=type(value).__name__):
                files = dict(self.files)
                if isinstance(value, str):
                    files.update(M.acl_files(self.values, cache, value, other))
                else:
                    files[M.FILES['REDIS_MONITOR_PASSWORD_MAP_FILE']] = json.dumps({'redis://redis:6379': value}).encode()
                with self.assertRaisesRegex(ValueError, 'REDIS_MONITOR_PASSWORD'):
                    M.validate(self.values, files, self.local / 'files')

    def test_unsupported_uri_credentials_fail_without_changing_password(self):
        for key in ('MONGO_PASSWORD', 'MONGO_NOTIFICATION_PASSWORD', 'RABBITMQ_PASSWORD', 'MONGO_USER', 'RABBITMQ_USER'):
            for character in (':', '/', '?', '#', '[', ']', '@', '%', ' ', '\t', '\\'):
                values = dict(self.values)
                values[key] = values[key] + character
                with self.subTest(key=key), self.assertRaisesRegex(ValueError, 'UNSUPPORTED_URI_USERINFO'):
                    M.validate(values, self.files, self.local / 'files')

    def test_rerun_does_not_rotate_and_symlink_refused(self):
        before = (self.local / '.env').read_bytes()
        result = subprocess.run(['python3', str(ROOT / 'scripts/deployment-secrets.py'), 'prepare',
                                 '--mode', 'local', '--output', str(self.local)], capture_output=True)
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(before, (self.local / '.env').read_bytes())
        link = self.root / 'link'
        link.symlink_to(self.local, target_is_directory=True)
        with self.assertRaises(ValueError):
            M.read_file(link / '.env')

    def test_production_import_preserves_provider_and_data_credentials(self):
        destination = self.root / 'prod'
        result = subprocess.run(['python3', str(ROOT / 'scripts/deployment-secrets.py'), 'prepare',
                                 '--mode', 'production', '--output', str(destination),
                                 '--import-env', str(self.local / '.env')], capture_output=True)
        self.assertEqual(result.returncode, 0, result.stderr.decode())
        imported = M.parse_env((destination / '.env.prod').read_bytes())
        for name in M.PRESERVE:
            self.assertEqual(imported.get(name), self.values.get(name), name)
        self.assertNotEqual(imported['SWAGGER_HTPASSWD'], self.values['SWAGGER_HTPASSWD'])
        self.assertNotIn('GHCR_TOKEN', imported)
        self.assertEqual(M.decode_bundle((destination / 'PROD_SECRET_FILES_B64').read_bytes()), self.files)

    def test_install_is_atomic_idempotent_and_refuses_existing_changes(self):
        parent = self.root / 'install'
        parent.mkdir(mode=0o700)
        target = parent / 'active'
        original = M.SERVER_ROOT
        M.SERVER_ROOT = target
        values = {k: v.replace(str(self.local / 'files'), str(target)) for k, v in self.values.items()}
        env = self.root / 'install.env'
        env.write_bytes(M.env_bytes(values, 'Synthetic'))
        args = SimpleNamespace(target=str(target), env=str(env), bundle=str(self.local / 'PROD_SECRET_FILES_B64'))
        try:
            bad = dict(values, BOT_TOKEN='invalid')
            env.write_bytes(M.env_bytes(bad, 'Synthetic invalid'))
            with self.assertRaises(ValueError):
                M.install(args)
            self.assertFalse(target.exists())
            env.write_bytes(M.env_bytes(values, 'Synthetic'))
            M.install(args)
            M.install(args)
            self.assertEqual((target / 'auth-tls/auth-server.key').stat().st_mode & 0o777, 0o444)
            values['GRAFANA_PASSWORD'] = 'different-synthetic-password'
            env.write_bytes(M.env_bytes(values, 'Synthetic changed'))
            with self.assertRaisesRegex(ValueError, 'NO_ROTATION'):
                M.install(args)
            self.assertNotEqual((target / '.env.prod').read_bytes(), env.read_bytes())
            parent.chmod(0o755)
            with self.assertRaises(ValueError):
                M.install(args)
        finally:
            M.SERVER_ROOT = original

    def test_optional_observer_is_separate_independent_and_never_overwrites(self):
        inventories = []
        for mode in ('production', 'local'):
            destination = self.root / ('observer-' + mode)
            command = ['python3', str(ROOT / 'scripts/deployment-secrets.py'), 'prepare-observer',
                       '--mode', mode, '--output', str(destination)]
            result = subprocess.run(command, capture_output=True)
            self.assertEqual(result.returncode, 0, result.stderr.decode())
            inventory = json.loads((destination / 'owner-inventory.json').read_bytes())
            before = (destination / 'owner-inventory.json').read_bytes()
            self.assertNotEqual(subprocess.run(command, capture_output=True).returncode, 0)
            self.assertEqual(before, (destination / 'owner-inventory.json').read_bytes())
            self.assertIsNone(inventory['glitchtip_admin']['email'])
            self.assertIn('not-created', inventory['glitchtip_admin']['status'])
            credentials = [inventory['postgres']['password'], inventory['glitchtip_signing_key'],
                           inventory['operator']['password'], inventory['glitchtip_admin']['password']]
            self.assertEqual(len(set(credentials)), 4)
            self.assertTrue(all(len(value) >= 43 for value in credentials))
            self.assertEqual(len(list((destination / 'files').iterdir())), 3)
            self.assertFalse((destination / 'PROD_SECRET_FILES_B64').exists())
            self.assertEqual(len(M.ALLOWED), 41)
            inventories.append(credentials)
        self.assertFalse(set(inventories[0]).intersection(inventories[1]))


if __name__ == '__main__':
    unittest.main()
