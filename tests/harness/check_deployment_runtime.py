#!/usr/bin/env python3
"""Bounded synthetic Compose + PostgreSQL/Redis/edge auth smoke. Own resources only."""
import base64
import importlib.util
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile
import time
import urllib.error
import urllib.request

ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location('secrets_tool', ROOT / 'scripts/deployment-secrets.py')
M = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(M)


def run(args, **kwargs):
    result = subprocess.run(args, capture_output=True, **kwargs)
    if result.returncode:
        # All inputs are synthetic, but do not print config/credentials even here.
        raise RuntimeError('Command failed: ' + str(args[0]) + ' exit=' + str(result.returncode))
    return result.stdout


def block(source, name):
    start = source.index('    location ' + name + ' {')
    begin = source.index('{', start)
    depth = 1
    end = begin + 1
    while depth:
        if source[end] == '{':
            depth += 1
        if source[end] == '}':
            depth -= 1
        end += 1
    return source[start:end]


def main():
    project = 'rct-deploy-prep-' + str(os.getpid())
    with tempfile.TemporaryDirectory(prefix=project + '-') as tmp:
        private = Path(tmp)
        vault = private / 'vault'
        run(['python3', str(ROOT / 'scripts/deployment-secrets.py'), 'prepare', '--mode', 'local', '--output', str(vault)])
        envfile = vault / '.env'
        values = M.parse_env(envfile.read_bytes())
        compose = ['docker', 'compose', '--env-file', str(envfile), '-f', str(ROOT / 'docker-compose.prod.yml'),
                   '-f', str(ROOT / 'docker-compose.operator.yml')]
        model = json.loads(run([*compose, 'config', '--format', 'json']))
        operator = json.loads((vault / 'owner-inventory.json').read_text())['operator']
        resolved_hash = model['services']['nginx']['environment']['SWAGGER_HTPASSWD']
        # Compose config serializes dollars escaped for a second Compose parse.
        assert resolved_hash.replace('$$', '$') == values['SWAGGER_HTPASSWD'], 'Resolved literal htpasswd differs'
        for name in ('postgres-academic', 'postgres-schedule', 'mongo-attendance', 'redis', 'redis-cache', 'rabbitmq'):
            assert all(port['host_ip'] == '127.0.0.1' for port in model['services'][name]['ports'])
        # Linux snapshot of the unchanged initialization script (Git stores LF).
        init = private / 'init-roles.sh'
        init.write_text((ROOT / 'infra/postgres/init-roles.sh').read_text())
        init.chmod(0o755)
        config = {'services': {}, 'secrets': model['secrets'], 'volumes': {}}
        for name in ('postgres-academic', 'postgres-schedule', 'redis', 'redis-cache'):
            original = model['services'][name]
            service = {k: original[k] for k in ('image', 'environment', 'command', 'secrets', 'healthcheck') if k in original}
            service['volumes'] = []
            if name.startswith('postgres'):
                service['volumes'].append({'type': 'bind', 'source': str(init), 'target': '/docker-entrypoint-initdb.d/10-rct-roles.sh', 'read_only': True})
            config['services'][name] = service
        source = (ROOT / 'nginx/conf.d/default.conf').read_text()
        selected = '\n'.join(block(source, name) for name in ('/grafana/', '/prometheus/', '/swagger-ui/', '/v3/api-docs'))
        nginx = private / 'nginx.conf'
        nginx.write_text('events {}\nhttp { access_log off; server { listen 18081; return 200 "$request_uri|$http_authorization"; }\n'
                         'server { listen 80; set $upstream_grafana "127.0.0.1:18081"; '
                         'set $upstream_prometheus "127.0.0.1:18081"; set $upstream_gateway "127.0.0.1:18081";\n' + selected + '\n}}')
        config['services']['edge'] = {
            'image': model['services']['nginx']['image'],
            'environment': {'SWAGGER_HTPASSWD': resolved_hash},
            'entrypoint': ['/bin/sh', '/entrypoint.sh'], 'ports': ['127.0.0.1::80'],
            'secrets': ['alert-webhook-token'],
            'volumes': [str(nginx) + ':/etc/nginx/nginx.conf:ro', str(ROOT / 'nginx/scripts/entrypoint.sh') + ':/entrypoint.sh:ro']}
        runtime = private / 'runtime.json'
        runtime.write_text(json.dumps(config))
        command = ['docker', 'compose', '-p', project, '--env-file', str(envfile), '-f', str(runtime)]
        try:
            run([*command, 'up', '-d', '--wait', '--wait-timeout', '90'], timeout=180)
            webhook = run([*command, 'exec', '-T', 'edge', 'cat', '/run/secrets/alert-webhook-token']).decode()
            assert webhook == values['ALERT_WEBHOOK_SECRET']
            for db, role, password_key, service in (
                ('academic_db', 'rct_academic_runtime', 'PG_ACADEMIC_SERVICE_RUNTIME_PASSWORD_FILE', 'postgres-academic'),
                ('auth_db', 'rct_auth_runtime', 'PG_AUTH_RUNTIME_PASSWORD_FILE', 'postgres-academic'),
                ('schedule_db', 'rct_runtime', 'PG_SCHEDULE_RUNTIME_PASSWORD_FILE', 'postgres-schedule')):
                password = Path(values[password_key]).read_text().strip()
                result = run([*command, 'exec', '-T', '-e', 'PGPASSWORD=' + password, service,
                              'psql', '-h', '127.0.0.1', '-U', role, '-d', db, '-Atc', 'SELECT current_user'])
                assert result.decode().strip() == role
            for name, user, key in (('redis', 'default', None), ('redis-cache', 'rct_cache', 'REDIS_CACHE_RUNTIME_PASSWORD_FILE')):
                password = values['REDIS_PASSWORD'] if key is None else Path(values[key]).read_text().strip()
                result = run([*command, 'exec', '-T', '-e', 'REDISCLI_AUTH=' + password, name, 'redis-cli', '--user', user, 'ping'])
                assert result.strip() == b'PONG'
            # Each actual image UID can read its mount despite host private parent0700.
            for name, (uid, gid, _) in M.TLS.items():
                path = vault / 'files' / f'{name}-tls/{name}-server.key'
                run(['docker', 'run', '--rm', '--network', 'none', '--user', f'{uid}:{gid}',
                     '--mount', f'type=bind,source={path},target=/key,readonly', '--entrypoint', '/bin/sh',
                     model['services']['nginx']['image'], '-ec', 'test -r /key; cat /key > /dev/null'])
            address = run([*command, 'port', 'edge', '80']).decode().strip()
            auth = base64.b64encode((operator['login'] + ':' + operator['password']).encode()).decode()
            for route in ('/grafana/', '/prometheus/', '/swagger-ui/', '/v3/api-docs'):
                url = 'http://' + address + route
                for credential, expected in ((None, 401), ('Basic ' + base64.b64encode(b'operator:wrong').decode(), 401), ('Basic ' + auth, 200)):
                    req = urllib.request.Request(url, headers={} if credential is None else {'Authorization': credential})
                    try:
                        with urllib.request.urlopen(req, timeout=5) as response:
                            code, body = response.status, response.read()
                    except urllib.error.HTTPError as error:
                        code, body = error.code, error.read()
                    assert code == expected, route
                    if route == '/grafana/' and expected == 200:
                        assert body == b'/grafana/|', 'Grafana must not receive operator Authorization'
            print('PASS Compose literal htpasswd; loopback operator ports; PG three databases; two Redis ACLs; TLS5 UID readability; HTTPS-location auth rules 401/401/200 and Grafana header removal (HTTP isolated edge)')
        finally:
            run([*command, 'down', '--volumes', '--remove-orphans'], timeout=90)
            print('CLEANUP own synthetic Compose project removed: ' + project)


if __name__ == '__main__':
    main()
