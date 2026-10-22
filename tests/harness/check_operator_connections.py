#!/usr/bin/env python3
"""Render real Compose for new operator ports and Academic trust; no stack startup."""
import importlib.util
import json
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location('deployment', ROOT / 'scripts/deployment-secrets.py')
M = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(M)


def run(arguments):
    result = subprocess.run(arguments, capture_output=True)
    assert result.returncode == 0, 'Synthetic command failed: ' + arguments[0]
    return result.stdout


def main():
    with tempfile.TemporaryDirectory(prefix='rct-deploy-prep-connections-') as tmp:
        root = Path(tmp)
        vault = root / 'local'
        run(['python3', str(ROOT / 'scripts/deployment-secrets.py'), 'prepare', '--mode', 'local', '--output', str(vault)])
        template = M.parse_env((ROOT / '.env.prod.example').read_bytes())
        for key, name in M.FILES.items():
            assert template[key] == str(M.SERVER_ROOT / name), key
        for name in M.TLS:
            assert template[name.upper().replace('-', '_') + '_GRPC_TLS_DIR'] == str(M.SERVER_ROOT / (name + '-tls'))
        model = json.loads(run(['docker', 'compose', '--env-file', str(vault / '.env'), '-f', str(ROOT / 'docker-compose.prod.yml'),
                               '-f', str(ROOT / 'docker-compose.operator.yml'), 'config', '--format', 'json']))
        for name, port, target in (('prometheus', '19090', 9090), ('alertmanager', '19093', 9093),
                                   ('loki', '13100', 3100), ('tempo', '13200', 3200), ('grafana', '13000', 3000)):
            assert model['services'][name]['ports'] == [{'mode': 'ingress', 'host_ip': '127.0.0.1',
                                                        'target': target, 'published': port, 'protocol': 'tcp'}]
        academic = model['services']['academic-service']
        trust = academic['environment']['ATTENDANCE_GRPC_TRUST_CERT_COLLECTION'].removeprefix('file:')
        certificate = next(m for m in academic['volumes'] if m['target'] == trust)
        assert certificate['read_only'] is True
        assert certificate['source'] == str(vault / 'files/attendance-tls/attendance-server.crt')
        assert not any(m['source'].endswith('attendance-server.key') for m in academic['volumes'])
        # Actual Academic UID can read the exact client trust mount. Reuse cached nginx shell image only.
        run(['docker', 'run', '--rm', '--network', 'none', '--user', '10001:10001',
             '--mount', 'type=bind,source=' + certificate['source'] + ',target=/trust,readonly',
             '--entrypoint', '/bin/sh', model['services']['nginx']['image'], '-ec', 'test -r /trust; cat /trust > /dev/null'])
        observer_env = root / 'observer.env'
        for name in ('password', 'key', 'htpasswd'):
            (root / name).write_text('synthetic-only-never-deployed')
        observer_env.write_text('RCT_GLITCHTIP_DB_PASSWORD_FILE=' + str(root / 'password') + '\n' +
                                'RCT_GLITCHTIP_SECRET_KEY_FILE=' + str(root / 'key') + '\n' +
                                'RCT_OBSERVER_HTPASSWD_FILE=' + str(root / 'htpasswd') + '\n')
        observer = json.loads(run(['docker', 'compose', '--env-file', str(observer_env),
                                  '-f', str(ROOT / 'docker-compose.observability-apps.yml'),
                                  '-f', str(ROOT / 'docker-compose.observability-operator.yml'), 'config', '--format', 'json']))
        assert set(observer['services']) == {'glitchtip-db', 'glitchtip', 'pyroscope', 'homepage', 'observer-proxy'}
        assert observer['services']['glitchtip-db']['ports'][0]['host_ip'] == '127.0.0.1'
        assert observer['services']['glitchtip-db']['ports'][0]['published'] == '15434'
        assert observer['services']['observer-proxy']['ports'][0]['host_ip'] == '127.0.0.1'
        assert observer['services']['observer-proxy']['ports'][0]['published'] == '18880'
        assert not any('ports' in observer['services'][n] for n in ('glitchtip', 'pyroscope', 'homepage'))
        assert not set(observer['services']).intersection(model['services'])
        print('PASS template POSIX active paths; five additional loopback API ports; Academic Attendance trust exact mount+UID; optional observer DB/proxy ports and release separation; no stack started')


if __name__ == '__main__':
    main()
