"""Synthetic aggregator branch controls. No services, Docker, credentials or network."""
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]


class RequiredDeployChecks(unittest.TestCase):
    def sandbox(self, root):
        bindir = root / 'bin'
        bindir.mkdir()
        for name in ('bash', 'python3', 'grep', 'tr', 'head', 'sed', 'seq', 'sort', 'wc'):
            (bindir / name).symlink_to(shutil.which(name))
        (bindir / 'curl').write_text('''#!/bin/bash
case "$*" in
  *-ksI*) printf 'HTTP/1.1 200 OK\\nStrict-Transport-Security: max-age=1\\nContent-Security-Policy: report-uri /api/csp-report\\nX-Frame-Options: SAMEORIGIN\\n';;
  *api/csp-report*) printf '204';;
  *prometheus/*|*alertmanager/*) printf '401';;
  */login*|*/app/*|*/presentation/*) printf '200';;
  *) printf '301';;
esac
''')
        (bindir / 'curl').chmod(0o700)
        environment = dict(os.environ)
        environment['PATH'] = str(bindir)
        for name in ('SWAGGER_USER', 'SWAGGER_PASS', 'MONGO_INITDB_ROOT_PASSWORD'):
            environment.pop(name, None)
        return environment

    def test_preflight_missing_dashboard_and_docker_never_reports_pass(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            env = self.sandbox(root)
            (root / 'scripts').mkdir()
            validator = root / 'scripts/validate-env-prod.sh'
            validator.write_text('#!/bin/bash\nexit 0\n')
            validator.chmod(0o700)
            for name in ('nginx/conf.d/default.conf', 'nginx/scripts/entrypoint.sh', 'nginx/dhparam.pem',
                         'infra/blackbox/blackbox.yml', 'infra/alertmanager/alertmanager.yml',
                         'infra/prometheus/prometheus.yml', 'infra/loki/loki.yml', 'infra/tempo/tempo.yml',
                         'scripts/backup.sh', 'scripts/restore.sh', 'scripts/recovery.py'):
                path = root / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.touch()
            (root / 'synthetic.env').write_text('SYNTHETIC_ONLY=1\n')
            result = subprocess.run(['/bin/bash', str(ROOT / 'scripts/preflight-deploy.sh'), 'synthetic.env'],
                                    cwd=root, env=env, capture_output=True, timeout=10)
            self.assertEqual(result.returncode, 6)
            for name in (b'grafana-dashboard-validator', b'promtool-rules', b'compose-config'):
                self.assertIn(b'INCOMPLETE: ' + name, result.stdout)
            self.assertNotIn('Все pre-flight проверки прошли'.encode(), result.stdout)
            validator.write_text('#!/bin/bash\nexit 1\n')
            failed = subprocess.run(['/bin/bash', str(ROOT / 'scripts/preflight-deploy.sh'), 'synthetic.env'],
                                    cwd=root, env=env, capture_output=True, timeout=10)
            self.assertEqual(failed.returncode, 1, 'Original required failure remains primary')

    def test_postdeploy_missing_native_and_private_prerequisites_never_reports_pass(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            result = subprocess.run(['/bin/bash', str(ROOT / 'scripts/verify-deploy.sh'), 'https://synthetic.invalid'],
                                    cwd=root, env=self.sandbox(root), capture_output=True, timeout=10)
            self.assertEqual(result.returncode, 6)
            for name in (b'container-health', b'gateway-health', b'loaded-prometheus-rules', b'mongo-ttl-index'):
                self.assertIn(b'INCOMPLETE: ' + name, result.stdout)
            self.assertNotIn(b'Post-deploy verification passed', result.stdout)


if __name__ == '__main__':
    unittest.main()
