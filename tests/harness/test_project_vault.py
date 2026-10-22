"""Offline synthetic adoption/edit/refresh; no real inputs or service startup."""
import contextlib
import hashlib
import importlib.util
import io
import json
import os
from pathlib import Path
import shutil
import stat
import subprocess
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location('project_secrets', ROOT / 'scripts/deployment-secrets.py')
M = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(M)


class ProjectVault(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temporary = tempfile.TemporaryDirectory(prefix='rct-project-vault-')
        cls.work = Path(cls.temporary.name)
        cls.seed = cls.work / 'seed'
        cls.seed.mkdir(mode=0o700)
        def prepare(*args):
            result = subprocess.run(['python3', str(ROOT / 'scripts/deployment-secrets.py'), *args], capture_output=True)
            if result.returncode:
                raise AssertionError('Synthetic preparation failed: ' + result.stderr.decode())
        prepare('prepare', '--mode', 'local', '--output', str(cls.seed / 'local'))
        prepare('prepare', '--mode', 'production', '--output', str(cls.seed / 'production'),
                '--import-env', str(cls.seed / 'local/.env'))
        for mode in ('production', 'local'):
            prepare('prepare-observer', '--mode', mode, '--output', str(cls.seed / ('observer-' + mode)))

    @classmethod
    def tearDownClass(cls):
        cls.temporary.cleanup()

    def setUp(self):
        self.project = Path(tempfile.mkdtemp(prefix='project-', dir=self.work))
        subprocess.run(['git', 'init', '-q', str(self.project)], check=True, capture_output=True)
        (self.project / '.gitignore').write_text('/.secrets/\n/.env\n/.env.prod\n')
        (self.project / '.dockerignore').write_text('/.secrets/\n.env*\n')
        shutil.copytree(self.seed, self.project / '.secrets')
        for name, env in (('production', '.env.prod'), ('local', '.env')):
            shutil.copy2(self.project / '.secrets' / name / env, self.project / env)

    def command(self, command='refresh-project', mode=None, observer=False):
        args = SimpleNamespace(project_root=str(self.project), command=command, mode=mode,
                               observer=observer, all=mode is None)
        with contextlib.redirect_stdout(io.StringIO()), patch.object(M, 'token', side_effect=AssertionError('RNG forbidden')):
            M.project_command(args)

    def snapshot(self):
        return {str(p.relative_to(self.project)): (p.read_bytes(), stat.S_IMODE(p.stat().st_mode))
                for p in self.project.rglob('*') if p.is_file() and '.git' not in p.parts
                and 'history' not in p.parts}

    def edit_env(self, **updates):
        path = self.project / '.env'
        values = M.parse_env(path.read_bytes())
        values.update(updates)
        path.write_bytes(M.env_bytes(values, 'Synthetic edit'))

    def test_adoption_preserves_credentials_and_noop_bytes(self):
        before = {str(p.relative_to(self.project)): p.read_bytes() for p in (self.project / '.secrets').rglob('*')
                  if p.is_file() and 'files' in p.parts}
        old = M.parse_env((self.project / '.env').read_bytes())
        self.command('adopt-project')
        self.assertTrue(all((self.project / name).read_bytes() == data for name, data in before.items()))
        now = M.parse_env((self.project / '.env').read_bytes())
        self.assertEqual(old['SWAGGER_HTPASSWD'], now['SWAGGER_HTPASSWD'])
        self.assertEqual(now['PG_AUTH_RUNTIME_PASSWORD_FILE'], '.secrets/local/files/pg-auth-runtime-password')
        self.assertEqual(stat.S_IMODE((self.project / now['PG_AUTH_RUNTIME_PASSWORD_FILE']).stat().st_mode), 0o644)
        snapshot = self.snapshot()
        self.command()
        self.command('adopt-project')
        self.assertEqual(snapshot, self.snapshot())

    def test_owner_edits_rebuild_only_derived_credentials(self):
        self.command('adopt-project')
        vault = self.project / '.secrets/local'
        old_hash = M.parse_env((self.project / '.env').read_bytes())['SWAGGER_HTPASSWD']
        old_key = (vault / 'files/auth-tls/auth-server.key').read_bytes()
        self.edit_env(REDIS_PASSWORD='R' * 43, GRAFANA_PASSWORD='G' * 43)
        (vault / 'files/pg-auth-runtime-password').write_text('P' * 43 + '\n')
        (vault / 'editable/operator-password').write_text('O' * 43 + '\n')
        (vault / 'editable/redis-monitor-password').write_text('M' * 43 + '\n')
        self.command(mode='local')
        values = M.parse_env((self.project / '.env').read_bytes())
        files = M.decode_bundle((vault / 'PROD_SECRET_FILES_B64').read_bytes())
        self.assertEqual(files['pg-auth-runtime-password'], ('P' * 43 + '\n').encode())
        self.assertEqual(values['SWAGGER_HTPASSWD'], M.operator_line(old_hash, 'O' * 43))
        self.assertIn(hashlib.sha256(('R' * 43).encode()).hexdigest().encode(), files['redis-server-acl'])
        self.assertEqual(json.loads(files['redis-monitor-password-map'])['redis://redis:6379'], 'M' * 43)
        self.assertEqual(old_key, files['auth-tls/auth-server.key'])
        self.assertEqual((self.project / '.env').read_bytes(), (vault / 'PROD_ENV').read_bytes())

    def test_cross_process_hash_seeds_do_not_rewrite_or_create_backups(self):
        def run(command, seed):
            result = subprocess.run(
                ['python3', str(ROOT / 'scripts/deployment-secrets.py'), command,
                 '--project-root', str(self.project), '--all'],
                env={**os.environ, 'PYTHONHASHSEED': str(seed)}, capture_output=True)
            self.assertEqual(result.returncode, 0, 'Synthetic subprocess failed (output withheld)')
            return result.stdout.decode().strip()
        run('adopt-project', 11)
        before = self.snapshot()
        history = sorted(p.name for p in (self.project / '.secrets/history').iterdir())
        for seed in (72, 135):
            self.assertEqual(run('refresh-project', seed),
                             'UNCHANGED project inputs and derived payloads verified')
            self.assertEqual(before, self.snapshot())
            self.assertEqual(history, sorted(p.name for p in (self.project / '.secrets/history').iterdir()))

    def test_observer_edits_preserve_salt_and_account_not_created(self):
        self.command('adopt-project')
        vault = self.project / '.secrets/observer-local'
        old = (vault / 'files/observer-htpasswd').read_text().strip()
        (vault / 'files/glitchtip-db-password').write_text('D' * 43 + '\n')
        (vault / 'editable/operator-password').write_text('O' * 43 + '\n')
        (vault / 'editable/glitchtip-admin-password').write_text('A' * 43 + '\n')
        self.command(mode='local', observer=True)
        inventory = json.loads((vault / 'owner-inventory.json').read_bytes())
        self.assertEqual(inventory['postgres']['password'], 'D' * 43)
        self.assertEqual(inventory['glitchtip_admin']['password'], 'A' * 43)
        self.assertIsNone(inventory['glitchtip_admin']['email'])
        self.assertIn('not-created', inventory['glitchtip_admin']['status'])
        self.assertEqual((vault / 'files/observer-htpasswd').read_text().strip(), M.operator_line(old, 'O' * 43))

    def test_all_preflight_rejects_bad_input_before_any_derived_write(self):
        self.command('adopt-project')
        (self.project / '.secrets/production/editable/operator-password').write_text('N' * 43 + '\n')
        (self.project / '.secrets/observer-local/editable/operator-password').write_text('bad\n')
        before = self.snapshot()
        history = list((self.project / '.secrets/history').iterdir())
        with self.assertRaisesRegex(ValueError, 'PASSWORD_FORMAT'):
            self.command()
        self.assertEqual(before, self.snapshot())
        self.assertEqual(history, list((self.project / '.secrets/history').iterdir()))

    def test_adoption_rejects_changed_source(self):
        self.edit_env(GRAFANA_PASSWORD='G' * 43)
        before = self.snapshot()
        with self.assertRaisesRegex(ValueError, 'ADOPTION_ENV_MISMATCH'):
            self.command('adopt-project')
        self.assertEqual(before, self.snapshot())

    def test_ignore_and_symlink_boundaries(self):
        (self.project / '.dockerignore').write_text('.env*\n')
        with self.assertRaisesRegex(ValueError, 'EXCLUSION'):
            self.command('adopt-project')
        (self.project / '.dockerignore').write_text('/.secrets/\n')
        (self.project / '.secrets/local/unsafe').symlink_to('/tmp')
        with self.assertRaisesRegex(ValueError, 'SYMLINK'):
            self.command('adopt-project')

    def test_failed_replace_rolls_back_and_retains_private_backup(self):
        self.command('adopt-project')
        self.edit_env(REDIS_PASSWORD='R' * 43)
        before = self.snapshot()
        replace = M.os.replace
        calls = 0
        def fail_second(source, target):
            nonlocal calls
            calls += 1
            if calls == 2:
                raise OSError('synthetic write failure')
            return replace(source, target)
        with patch.object(M.os, 'replace', side_effect=fail_second):
            with self.assertRaisesRegex(ValueError, 'FAILED_ROLLED_BACK'):
                self.command(mode='local')
        self.assertEqual(before, self.snapshot())
        self.assertTrue(list((self.project / '.secrets/history').glob('*/manifest.json')))

    def test_relative_local_and_observer_compose_render(self):
        self.command('adopt-project')
        # Compose env_file inputs resolve against explicit project directory.
        shutil.copytree(ROOT / 'infra/glitchtip', self.project / 'infra/glitchtip')
        for env, compose in (
            ('.env', ('docker-compose.prod.yml', 'docker-compose.operator.yml', 'docker-compose.local-data.yml')),
            ('.secrets/observer-local/observer.env', ('docker-compose.observability-apps.yml', 'docker-compose.observability-operator.yml'))):
            args = ['docker', 'compose', '--project-directory', str(self.project), '--env-file', str(self.project / env)]
            for filename in compose:
                args += ['-f', str(ROOT / filename)]
            result = subprocess.run([*args, 'config', '--quiet'], capture_output=True)
            self.assertEqual(result.returncode, 0, 'Synthetic Compose config failed (output withheld)')


if __name__ == '__main__':
    os.umask(0o077)
    unittest.main()
