#!/usr/bin/env python3
"""Prepare/validate an immutable deployment set. Diagnostics never contain values.

Linux + Python standard library + OpenSSL. No network or service startup.
The bundle is base64(JSON {version, files: {allowlisted-name: base64(bytes)}}).
"""
import argparse
import base64
import hashlib
import json
import os
from pathlib import Path
import re
import secrets
import shutil
import stat
import subprocess
import sys
import tempfile
import time

ROOT = Path(__file__).resolve().parents[1]
LIMIT = 48 * 1024
SERVER_ROOT = Path('/opt/rutcampustrack-secrets/active')
TLS = {'auth': (100, 101, 'auth-service'), 'academic': (10001, 10001, 'academic-service'),
       'schedule': (10002, 10002, 'schedule-service'), 'attendance': (10003, 10003, 'attendance-service'),
       'mobile-bff': (10007, 10007, 'mobile-bff')}
FILE_NAMES = '''PG_ACADEMIC_BOOTSTRAP_PASSWORD_FILE PG_ACADEMIC_RUNTIME_PASSWORD_FILE
PG_ACADEMIC_SERVICE_RUNTIME_PASSWORD_FILE PG_ACADEMIC_MIGRATION_PASSWORD_FILE
PG_ACADEMIC_MONITOR_PASSWORD_FILE PG_AUTH_RUNTIME_PASSWORD_FILE PG_AUTH_MIGRATION_PASSWORD_FILE
PG_SCHEDULE_BOOTSTRAP_PASSWORD_FILE PG_SCHEDULE_RUNTIME_PASSWORD_FILE
PG_SCHEDULE_MIGRATION_PASSWORD_FILE PG_SCHEDULE_MONITOR_PASSWORD_FILE
REDIS_CACHE_RUNTIME_PASSWORD_FILE REDIS_SERVER_ACL_FILE REDIS_CACHE_SERVER_ACL_FILE
REDIS_MONITOR_PASSWORD_MAP_FILE REDIS_CACHE_MONITOR_PASSWORD_MAP_FILE MONGO_MONITOR_ENV_FILE
MOBILE_BFF_TO_AUTH_SERVICE_TOKEN_FILE ACADEMIC_TO_AUTH_SERVICE_TOKEN_FILE
ATTENDANCE_TO_AUTH_SERVICE_TOKEN_FILE AUTH_TO_ACADEMIC_PRESENTATION_SERVICE_TOKEN_FILE
MOBILE_BFF_TO_ACADEMIC_SERVICE_TOKEN_FILE MOBILE_BFF_TO_ATTENDANCE_SERVICE_TOKEN_FILE
MOBILE_BFF_TO_SCHEDULE_SERVICE_TOKEN_FILE ATTENDANCE_BOT_READ_TO_ACADEMIC_SERVICE_TOKEN_FILE
ATTENDANCE_BOT_READ_TO_SCHEDULE_SERVICE_TOKEN_FILE ACADEMIC_REPORT_TO_ATTENDANCE_SERVICE_TOKEN_FILE
ACADEMIC_REPORT_TO_SCHEDULE_SERVICE_TOKEN_FILE NOTIFICATION_TO_ACADEMIC_SERVICE_TOKEN_FILE
BOT_TO_READ_SERVICE_TOKEN_FILE BOT_TO_AUTH_SERVICE_TOKEN_FILE'''.split()
FILES = {key: key.lower().removesuffix('_file').replace('_', '-') for key in FILE_NAMES}
ALLOWED = set(FILES.values()) | {f'{name}-tls/{name}-server.{ext}' for name in TLS for ext in ('crt', 'key')}
DIRECTED = '''ACADEMIC_TO_SCHEDULE_SERVICE_TOKEN SCHEDULE_TO_ACADEMIC_SERVICE_TOKEN
ATTENDANCE_TO_ACADEMIC_SERVICE_TOKEN BOT_TO_NOTIFICATION_SERVICE_TOKEN'''.split()
PRESERVE = '''MONGO_USER MONGO_PASSWORD MONGO_ROOT_PASSWORD MONGO_NOTIFICATION_USER
MONGO_NOTIFICATION_PASSWORD MONGODB_REPLICA_SET_KEY REDIS_PASSWORD RABBITMQ_USER
RABBITMQ_PASSWORD BOT_TOKEN TMA_BOT_TOKEN BOT_ALERT_TOKEN VAPID_PUBLIC_KEY VAPID_PRIVATE_KEY
VAPID_SUBJECT GRPC_SECRET INTERNAL_ISSUER_SECRET ALERT_WEBHOOK_SECRET GRAFANA_PASSWORD
ADMIN_TELEGRAM_IDS MINI_APP_URL MINI_APP_WEB_URL CORS_ALLOWED_ORIGIN NOTIFICATION_WS_ALLOWED_ORIGINS'''.split()


def require(condition, code):
    if not condition:
        raise ValueError(code)


def no_links(path):
    require(path.is_absolute() and '..' not in path.parts, 'ABSOLUTE_CANONICAL_PATH_REQUIRED')
    for item in (path, *path.parents):
        require(not item.is_symlink(), 'SYMLINK_REFUSED')


def read_file(path, limit=LIMIT):
    no_links(path.absolute())
    require(path.is_file() and path.stat().st_size <= limit, 'INPUT_MISSING_OR_OVERSIZED')
    return path.read_bytes()


def parse_env(data):
    require(len(data) <= LIMIT, 'ENV_OVERSIZED')
    values = {}
    for line in data.decode('utf-8').splitlines():
        if not line.strip() or line.lstrip().startswith('#'):
            continue
        match = re.fullmatch(r'([A-Z][A-Z0-9_]*)=(.*)', line)
        require(match is not None, 'ENV_SYNTAX')
        key, value = match.groups()
        require(key not in values, 'ENV_DUPLICATE_KEY')
        if value.startswith(("'", '"')):
            quote = value[0]
            require(len(value) >= 2 and value.endswith(quote), 'ENV_QUOTES')
            value = value[1:-1]
        else:
            value = re.split(r'\s+#', value, 1)[0].rstrip()
        require(not any(c in value for c in ('\x00', '\r', '\n', "'")), 'ENV_UNSUPPORTED_CHARACTER')
        values[key] = value
    return values


def env_bytes(values, title):
    local = values.get('COMPOSE_PROJECT_NAME') == 'rct-local-data' or values.get('RCT_OBSERVER_PROJECT') == 'rct-local-observability'
    set_name = ('observer-' if 'RCT_OBSERVER_PROJECT' in values else '') + ('local' if local else 'production')
    authority = f'.secrets/{set_name}/observer.env' if set_name.startswith('observer-') else ('.env' if local else '.env.prod')
    lines = [f'# {title}', f'# Источник inline значений: {authority}; file-backed — .secrets/{set_name}/files/.',
             f'# Пароли operator/Redis monitor: .secrets/{set_name}/editable/. Inventory и payload — производные.',
             '# После правки до первого запуска: refresh-project. Уже работающая БД требует отдельной rotation.',
             '# Одинарные кавычки сохраняют literal $. Не выполнять source этого файла.']
    ordered = []
    headings = {}
    heading = ''
    for line in (ROOT / ('.env.example' if local else '.env.prod.example')).read_text().splitlines():
        if line.startswith('## '):
            heading = line
        elif re.match(r'^[A-Z][A-Z0-9_]*=', line):
            key = line.split('=', 1)[0]
            if key in values:
                ordered.append(key)
                headings[key] = heading
    ordered.extend(key for key in values if key not in ordered)
    previous = None
    for key in ordered:
        value = values[key]
        if headings.get(key) != previous:
            previous = headings.get(key)
            lines.extend(['', previous or '## Дополнительные параметры'])
        require(not any(c in value for c in ("'", '\r', '\n', '\x00')), 'ENV_UNSUPPORTED_CHARACTER')
        if key in FILES:
            lines.append('# Локальный файл: .secrets/' + set_name + '/files/' + FILES[key])
        lines.append(f"{key}='{value}'")
    return ('\n'.join(lines) + '\n').encode()


def openssl(*args, data=None):
    result = subprocess.run(['openssl', *args], input=data, capture_output=True)
    require(result.returncode == 0, 'OPENSSL_FAILED')
    return result.stdout


def token():
    return secrets.token_urlsafe(32)


def bundle_bytes(files):
    require(set(files) == ALLOWED, 'BUNDLE_FILE_SET')
    data = base64.b64encode(json.dumps({'version': 1, 'files': {
        name: base64.b64encode(value).decode() for name, value in sorted(files.items())}},
        separators=(',', ':')).encode())
    require(len(data) <= LIMIT, 'BUNDLE_EXCEEDS_GITHUB_48KB')
    return data


def unique_object(pairs):
    result = {}
    for key, value in pairs:
        require(key not in result, 'BUNDLE_DUPLICATE_KEY')
        result[key] = value
    return result


def decode_bundle(data):
    require(len(data) <= LIMIT, 'BUNDLE_EXCEEDS_GITHUB_48KB')
    obj = json.loads(base64.b64decode(data, validate=True), object_pairs_hook=unique_object)
    require(isinstance(obj, dict) and set(obj) == {'version', 'files'} and obj['version'] == 1, 'BUNDLE_SCHEMA')
    require(isinstance(obj['files'], dict) and set(obj['files']) == ALLOWED, 'BUNDLE_FILE_SET')
    files = {}
    for name, value in obj['files'].items():
        require(isinstance(value, str), 'BUNDLE_VALUE_TYPE')
        content = base64.b64decode(value, validate=True)
        require(0 < len(content) <= 8192 and b'\0' not in content, 'BUNDLE_CONTENT_SIZE')
        files[name] = content
    return files


def acl_files(values, cache_password, monitor, cache_monitor):
    files = {}
    for key, template, replacements in (
        ('REDIS_SERVER_ACL_FILE', 'redis-users.acl.template', {
            '__APPLICATION_PASSWORD_SHA256__': values['REDIS_PASSWORD'], '__MONITOR_PASSWORD_SHA256__': monitor}),
        ('REDIS_CACHE_SERVER_ACL_FILE', 'redis-cache-users.acl.template', {
            '__CACHE_PASSWORD_SHA256__': cache_password, '__MONITOR_PASSWORD_SHA256__': cache_monitor})):
        content = (ROOT / 'infra/prometheus/exporters' / template).read_text()
        for marker, value in replacements.items():
            content = content.replace(marker, hashlib.sha256(value.encode()).hexdigest())
        files[FILES[key]] = content.encode()
    for key, host, password in (('REDIS_MONITOR_PASSWORD_MAP_FILE', 'redis', monitor),
                                 ('REDIS_CACHE_MONITOR_PASSWORD_MAP_FILE', 'redis-cache', cache_monitor)):
        files[FILES[key]] = json.dumps({f'redis://{host}:6379': password}).encode()
    return files


def validate(values, files, path_root):
    require(set(files) == ALLOWED, 'BUNDLE_FILE_SET')
    required = re.search(r'REQUIRED_VARS=\((.*?)\)', (ROOT / 'scripts/validate-env-prod.sh').read_text(), re.S)[1].split()
    for key in required:
        require(values.get(key) and 'CHANGE_ME' not in values[key], 'ENV_REQUIRED_' + key)
    require(values['BOT_TOKEN'] == values['TMA_BOT_TOKEN'], 'BOT_TMA_MISMATCH')
    require(values['COMPOSE_PROJECT_NAME'] in ('rutcampustrack', 'rct-local-data'), 'PROJECT_NAME')
    # These existing Compose URI interpolations do not percent-encode userinfo.
    # Refuse unsupported imports rather than changing persisted database passwords.
    for key in ('MONGO_USER', 'MONGO_PASSWORD', 'MONGO_NOTIFICATION_USER',
                'MONGO_NOTIFICATION_PASSWORD', 'RABBITMQ_USER', 'RABBITMQ_PASSWORD'):
        require(not any(c.isspace() or ord(c) < 32 or c in ':/?#[]@%\\' for c in values[key]),
                'UNSUPPORTED_URI_USERINFO_' + key)
    directed = [values[key] for key in DIRECTED]
    passwords = []
    for key, name in FILES.items():
        require(values[key] == str(path_root / name), 'SECRET_PATH_' + key)
        value = files[name].decode().strip()
        if key.endswith('_SERVICE_TOKEN_FILE'):
            directed.append(value)
        elif key.startswith('PG_') or key == 'REDIS_CACHE_RUNTIME_PASSWORD_FILE':
            require(re.fullmatch(r'[A-Za-z0-9_-]{32,128}', value), 'PASSWORD_FORMAT_' + key)
            passwords.append(value)
    require(len(passwords) == len(set(passwords)), 'PASSWORDS_NOT_INDEPENDENT')
    for value in directed:
        raw = base64.urlsafe_b64decode(value + '=')
        require(len(raw) == 32 and base64.urlsafe_b64encode(raw).decode().rstrip('=') == value, 'DIRECTED_TOKEN_FORMAT')
    require(len(set(directed)) == len(directed), 'DIRECTED_TOKENS_NOT_INDEPENDENT')
    monitor = json.loads(files[FILES['REDIS_MONITOR_PASSWORD_MAP_FILE']])
    cache_monitor = json.loads(files[FILES['REDIS_CACHE_MONITOR_PASSWORD_MAP_FILE']])
    require(set(monitor) == {'redis://redis:6379'} and set(cache_monitor) == {'redis://redis-cache:6379'}, 'REDIS_MAP')
    monitor_passwords = [monitor['redis://redis:6379'], cache_monitor['redis://redis-cache:6379']]
    for password in monitor_passwords:
        require(isinstance(password, str) and re.fullmatch(r'[A-Za-z0-9_-]{32,128}', password), 'REDIS_MONITOR_PASSWORD_FORMAT')
    require(len(set(monitor_passwords)) == 2 and
            not set(monitor_passwords).intersection(passwords + directed + [values['REDIS_PASSWORD']]),
            'REDIS_MONITOR_PASSWORDS_NOT_INDEPENDENT')
    expected = acl_files(values, files[FILES['REDIS_CACHE_RUNTIME_PASSWORD_FILE']].decode().strip(),
                         monitor['redis://redis:6379'], cache_monitor['redis://redis-cache:6379'])
    require(all(files[key] == value for key, value in expected.items()), 'REDIS_ACL_MAP_MISMATCH')
    require(re.fullmatch(rb'MONGODB_PASSWORD=[A-Za-z0-9_-]{32,128}\n', files[FILES['MONGO_MONITOR_ENV_FILE']]), 'MONGO_MONITOR_FORMAT')
    require(re.fullmatch(r'[A-Za-z0-9_-]{43,128}', values['CAMPUS_MAP_USAGE_HMAC_KEY']), 'CAMPUS_HMAC_FORMAT')
    with tempfile.TemporaryDirectory(prefix='rct-cert-check-') as tmp:
        for name, (_, _, hostname) in TLS.items():
            key = name.upper().replace('-', '_') + '_GRPC_TLS_DIR'
            require(values[key] == str(path_root / f'{name}-tls'), 'TLS_PATH')
            cert, private = Path(tmp) / 'cert', Path(tmp) / 'key'
            cert.write_bytes(files[f'{name}-tls/{name}-server.crt'])
            private.write_bytes(files[f'{name}-tls/{name}-server.key'])
            private.chmod(0o600)
            openssl('x509', '-in', str(cert), '-noout', '-checkend', '2592000')
            openssl('x509', '-in', str(cert), '-noout', '-checkhost', hostname)
            # -checkhost may accept CN fallback; require an explicit DNS SAN too.
            san = openssl('x509', '-in', str(cert), '-noout', '-ext', 'subjectAltName')
            require(('DNS:' + hostname).encode() in [v.strip() for v in san.splitlines()[-1].split(b',')], 'TLS_SAN')
            openssl('verify', '-CAfile', str(cert), '-purpose', 'sslserver', str(cert))
            require(openssl('x509', '-in', str(cert), '-pubkey', '-noout') ==
                    openssl('pkey', '-in', str(private), '-pubout'), 'TLS_KEY_CERT_MISMATCH')
        envfile = Path(tmp) / 'validation.env'
        envfile.write_bytes(env_bytes(values, 'Validation'))
        result = subprocess.run(['bash', str(ROOT / 'scripts/validate-env-prod.sh'), str(envfile)], capture_output=True)
        require(result.returncode == 0, 'ENV_FORMAT_VALIDATION_FAILED')


def write_private(path, data, mode=0o600):
    path.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
    with path.open('xb') as stream:
        stream.write(data)
    path.chmod(mode)


def create(args):
    require(os.name == 'posix', 'LINUX_REQUIRED')
    destination = Path(args.output)
    no_links(destination)
    require(not destination.exists(), 'DESTINATION_EXISTS_NO_ROTATION')
    require(not destination.is_relative_to(ROOT), 'PRIVATE_OUTPUT_MUST_BE_OUTSIDE_REPOSITORY')
    path_root = Path(args.server_root) if args.mode == 'production' else destination / 'files'
    no_links(path_root)
    source = parse_env(read_file(Path(args.import_env))) if args.import_env else {}
    require(args.mode == 'local' or args.import_env, 'PRODUCTION_REQUIRES_IMPORT')
    require(not (args.mode == 'local' and args.import_env), 'LOCAL_MUST_NOT_IMPORT_PRODUCTION')
    values = {k: v for k, v in parse_env((ROOT / '.env.prod.example').read_bytes()).items()
              if k not in FILES and not k.endswith('_GRPC_TLS_DIR')}
    for key in PRESERVE:
        if source.get(key):
            values[key] = source[key]
    for key in ('MONGO_PASSWORD', 'MONGO_ROOT_PASSWORD', 'MONGO_NOTIFICATION_PASSWORD', 'REDIS_PASSWORD',
                'RABBITMQ_PASSWORD', 'GRAFANA_PASSWORD', 'GRPC_SECRET', 'INTERNAL_ISSUER_SECRET'):
        if key not in source:
            values[key] = token()
    if 'MONGODB_REPLICA_SET_KEY' not in source:
        values['MONGODB_REPLICA_SET_KEY'] = base64.b64encode(secrets.token_bytes(768)).decode()
    values['ALERT_WEBHOOK_SECRET'] = source.get('ALERT_WEBHOOK_SECRET', secrets.token_hex(32))
    values['CAMPUS_MAP_USAGE_HMAC_KEY'] = source.get('CAMPUS_MAP_USAGE_HMAC_KEY', token())
    for key in DIRECTED:
        values[key] = source.get(key, token())
    if 'MINI_APP_WEB_URL' not in source:
        values['MINI_APP_WEB_URL'] = 'https://ruttrack.site/mini-app/'
    values['IMAGE_TAG'] = 'CHANGE_ME_EXACT_RELEASE_SHA'
    values['COMPOSE_PROJECT_NAME'] = 'rutcampustrack' if args.mode == 'production' else 'rct-local-data'
    inventory = {'mode': args.mode, 'operator': {'login': 'operator', 'password': token()}, 'files': {}}
    digest = openssl('passwd', '-apr1', '-stdin', data=(inventory['operator']['password'] + '\n').encode()).decode().strip()
    values['SWAGGER_HTPASSWD'] = 'operator:' + digest
    if args.mode == 'local':
        for key in ('BOT_TOKEN', 'TMA_BOT_TOKEN', 'BOT_ALERT_TOKEN'):
            values[key] = '000000000:' + 'LOCAL_ONLY_DISABLED_TOKEN_DO_NOT_USE_000'
        values.update(ADMIN_TELEGRAM_IDS='0', GATEWAY_PRIVATE_SUBNET='172.31.0.0/24',
                      GATEWAY_NETWORK_GATEWAY='172.31.0.1', GATEWAY_NGINX_IPV4='172.31.0.10',
                      GATEWAY_DYNAMIC_IP_RANGE='172.31.0.128/25',
                      VAPID_PUBLIC_KEY='A' * 87, VAPID_PRIVATE_KEY='A' * 43,
                      VAPID_SUBJECT='mailto:local@example.invalid')
    files = {}
    for key, name in FILES.items():
        values[key] = str(path_root / name)
        if source.get(key):
            files[name] = read_file(Path(source[key]), 8192)
        else:
            files[name] = (token() + '\n').encode()
    mongo_key = FILES['MONGO_MONITOR_ENV_FILE']
    if 'MONGO_MONITOR_ENV_FILE' not in source:
        files[mongo_key] = ('MONGODB_PASSWORD=' + token() + '\n').encode()
    monitor, cache_monitor = token(), token()
    redis_keys = ['REDIS_SERVER_ACL_FILE', 'REDIS_CACHE_SERVER_ACL_FILE', 'REDIS_MONITOR_PASSWORD_MAP_FILE', 'REDIS_CACHE_MONITOR_PASSWORD_MAP_FILE']
    require(not any(k in source for k in redis_keys) or all(k in source for k in redis_keys), 'PARTIAL_REDIS_IMPORT_REFUSED')
    if not any(k in source for k in redis_keys):
        files.update(acl_files(values, files[FILES['REDIS_CACHE_RUNTIME_PASSWORD_FILE']].decode().strip(), monitor, cache_monitor))
    with tempfile.TemporaryDirectory(prefix='rct-generate-') as tmp:
        for name, (_, _, hostname) in TLS.items():
            envkey = name.upper().replace('-', '_') + '_GRPC_TLS_DIR'
            values[envkey] = str(path_root / f'{name}-tls')
            cert, key = Path(tmp) / f'{name}.crt', Path(tmp) / f'{name}.key'
            if envkey in source:
                cert.write_bytes(read_file(Path(source[envkey]) / f'{name}-server.crt', 8192))
                key.write_bytes(read_file(Path(source[envkey]) / f'{name}-server.key', 8192))
            else:
                openssl('req', '-x509', '-newkey', 'ec', '-pkeyopt', 'ec_paramgen_curve:P-256', '-nodes',
                        '-days', '365', '-subj', '/CN=' + hostname, '-addext', 'subjectAltName=DNS:' + hostname,
                        '-addext', 'extendedKeyUsage=serverAuth', '-keyout', str(key), '-out', str(cert))
            files[f'{name}-tls/{name}-server.crt'] = cert.read_bytes()
            files[f'{name}-tls/{name}-server.key'] = key.read_bytes()
    # IMAGE_TAG is injected from exact CI SHA; generation itself is not a release.
    validate(values, files, path_root)
    payload = bundle_bytes(files)
    envdata = env_bytes(values, 'Production application inputs' if args.mode == 'production' else
                        'Local data stack only: scripts/local-data.sh .env config|up; bot/providers disabled')
    require(len(envdata) <= LIMIT, 'ENV_EXCEEDS_GITHUB_48KB')
    destination.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
    destination.mkdir(mode=0o700)
    for name, data in files.items():
        write_private(destination / 'files' / name, data, 0o444 if args.mode == 'local' else 0o600)
    inventory['environment'] = values
    inventory['files'] = {key: value.decode() for key, value in files.items() if not key.endswith(('.crt', '.key'))}
    write_private(destination / 'owner-inventory.json', json.dumps(inventory, indent=2).encode())
    write_private(destination / ('.env.prod' if args.mode == 'production' else '.env'), envdata)
    write_private(destination / 'PROD_ENV', envdata)
    write_private(destination / 'PROD_SECRET_FILES_B64', payload)
    if args.import_env:
        write_private(destination / 'imported-env.original', read_file(Path(args.import_env)))
    if source.get('GHCR_TOKEN'):
        write_private(destination / 'GHCR_TOKEN', source['GHCR_TOKEN'].encode())
    if args.ci_key:
        result = subprocess.run(['ssh-keygen', '-q', '-t', 'ed25519', '-N', '', '-C', 'rutcampustrack-ci',
                                 '-f', str(destination / 'SSH_PRIVATE_KEY')], capture_output=True)
        require(result.returncode == 0, 'SSH_KEY_GENERATION_FAILED')
    print(f'PREPARED mode={args.mode} files={len(files)} env_bytes={len(envdata)} bundle_bytes={len(payload)}')


def install(args):
    require(os.name == 'posix', 'LINUX_REQUIRED')
    target = Path(args.target)
    no_links(target)
    envdata = read_file(Path(args.env))
    values = parse_env(envdata)
    files = decode_bundle(read_file(Path(args.bundle)))
    require(target == SERVER_ROOT, 'FIXED_SERVER_ROOT_REQUIRED')
    validate(values, files, target)
    require(target.parent.is_dir() and target.parent.stat().st_uid == os.geteuid()
            and stat.S_IMODE(target.parent.stat().st_mode) == 0o700, 'PRIVATE_PARENT_OWNER_MODE')
    expected = {**files, '.env.prod': envdata}
    if target.exists():
        require(target.is_dir() and stat.S_IMODE(target.stat().st_mode) == 0o700 and target.stat().st_uid == os.geteuid(),
                'EXISTING_ROOT_PERMISSIONS')
        for path in target.rglob('*'):
            no_links(path)
        actual = {str(p.relative_to(target)) for p in target.rglob('*') if not p.is_dir()}
        require(actual == set(expected), 'EXISTING_FILE_SET_NO_ROTATION')
        for name, data in expected.items():
            require(read_file(target / name) == data, 'EXISTING_CONTENT_DIFFERS_NO_ROTATION')
        check_permissions(target, expected)
        print('UNCHANGED immutable deployment inputs verified')
        return
    require(target.parent.is_dir(), 'INSTALL_PARENT_MISSING')
    with tempfile.TemporaryDirectory(prefix='.rct-inputs-', dir=target.parent) as tmp:
        staging = Path(tmp) / 'set'
        staging.mkdir(mode=0o700)
        for name, data in expected.items():
            mode = 0o600 if name == '.env.prod' else 0o444
            write_private(staging / name, data, mode)
        check_permissions(staging, expected)
        staging.rename(target)
    print('INSTALLED immutable deployment inputs; services untouched')


def check_permissions(target, files):
    for name in files:
        path = target / name
        no_links(path)
        st = path.stat()
        mode = 0o600 if name == '.env.prod' else 0o444
        require((st.st_uid, stat.S_IMODE(st.st_mode)) == (os.geteuid(), mode), 'FILE_OWNER_MODE_MISMATCH')


def create_observer(args):
    """Separate undeployed observer scope; never included in the CI file bundle."""
    require(os.name == 'posix', 'LINUX_REQUIRED')
    destination = Path(args.output)
    no_links(destination)
    require(not destination.exists(), 'DESTINATION_EXISTS_NO_ROTATION')
    require(not destination.is_relative_to(ROOT), 'PRIVATE_OUTPUT_MUST_BE_OUTSIDE_REPOSITORY')
    path_root = Path('/opt/rutcampustrack-secrets/observer') if args.mode == 'production' else destination / 'files'
    password, signing_key, operator_password, admin_password = token(), token(), token(), token()
    operator_hash = 'operator:' + openssl('passwd', '-apr1', '-stdin', data=(operator_password + '\n').encode()).decode().strip()
    files = {'glitchtip-db-password': password + '\n', 'glitchtip-secret-key': signing_key + '\n',
             'observer-htpasswd': operator_hash + '\n'}
    values = {'RCT_OBSERVER_PROJECT': 'rct-observability-apps' if args.mode == 'production' else 'rct-local-observability',
              'RCT_OBSERVER_URL': 'http://localhost:18880', 'RCT_OBSERVER_PORT': '18880',
              'RCT_GLITCHTIP_DB_PASSWORD_FILE': str(path_root / 'glitchtip-db-password'),
              'RCT_GLITCHTIP_SECRET_KEY_FILE': str(path_root / 'glitchtip-secret-key'),
              'RCT_OBSERVER_HTPASSWD_FILE': str(path_root / 'observer-htpasswd')}
    inventory = {'mode': args.mode, 'status': 'prepared-only-not-installed-or-started', 'environment': values,
                 'postgres': {'database': 'glitchtip', 'user': 'glitchtip', 'password': password,
                              'privilege': 'dedicated PostgreSQL bootstrap superuser'},
                 'glitchtip_signing_key': signing_key,
                 'operator': {'login': 'operator', 'password': operator_password},
                 'glitchtip_admin': {'email': None, 'password': admin_password,
                                    'status': 'not-created; owner must choose email before createsuperuser'}}
    destination.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
    destination.mkdir(mode=0o700)
    for name, value in files.items():
        write_private(destination / 'files' / name, value.encode(), 0o444 if args.mode == 'local' else 0o600)
    write_private(destination / 'observer.env', env_bytes(values, 'Optional observer only; separate from production release; no account created'))
    write_private(destination / 'owner-inventory.json', json.dumps(inventory, indent=2).encode())
    print('PREPARED observer mode=' + args.mode + ' files=3 account_created=false')


def project_boundary(project):
    """Only a user-owned native Linux checkout with explicit private exclusions."""
    require(os.name == 'posix', 'LINUX_REQUIRED')
    no_links(project)
    require(project.is_dir() and project.stat().st_uid == os.geteuid(), 'PROJECT_OWNER_MISMATCH')
    for filename in ('.gitignore', '.dockerignore'):
        require('/.secrets/' in read_file(project / filename).decode().splitlines(), 'PRIVATE_EXCLUSION_MISSING')
    tracked = subprocess.run(['git', '-C', str(project), 'ls-files', '-z', '--', '.secrets', '.env', '.env.prod'], capture_output=True)
    require(tracked.returncode == 0 and not tracked.stdout, 'PRIVATE_INPUT_TRACKED_OR_NOT_GIT')
    ignored = subprocess.run(['git', '-C', str(project), 'check-ignore', '-q', '.secrets/probe'], capture_output=True)
    require(ignored.returncode == 0, 'PRIVATE_GIT_IGNORE_NOT_EFFECTIVE')
    private = project / '.secrets'
    no_links(private)
    require(private.is_dir() and private.stat().st_uid == os.geteuid()
            and stat.S_IMODE(private.stat().st_mode) == 0o700, 'PRIVATE_ROOT_OWNER_MODE')


def owned_bytes(path, limit=LIMIT):
    data = read_file(path, limit)
    require(path.stat().st_uid == os.geteuid() and not path.stat().st_mode & 0o022, 'SOURCE_OWNER_OR_WRITE_MODE')
    return data


def password_text(data):
    value = data.decode().rstrip('\n')
    require(re.fullmatch(r'[A-Za-z0-9_-]{32,128}', value), 'EDITABLE_PASSWORD_FORMAT')
    return value


def operator_line(existing, password):
    match = re.fullmatch(r'([A-Za-z0-9_]+):\$apr1\$([A-Za-z0-9./]{1,8})\$[A-Za-z0-9./]{22}', existing)
    require(match is not None, 'APR1_SOURCE_HASH_REQUIRED')
    # Explicit old salt: no random credential or salt generation during refresh.
    return match[1] + ':' + openssl('passwd', '-apr1', '-salt', match[2], '-stdin',
                                    data=(password + '\n').encode()).decode().strip()


def project_plan(project, set_name, adopting):
    vault = project / '.secrets' / set_name
    no_links(vault)
    require(vault.is_dir() and vault.stat().st_uid == os.geteuid()
            and stat.S_IMODE(vault.stat().st_mode) == 0o700, 'VAULT_OWNER_MODE')
    # Do not follow links anywhere in the moved set, including historical copies.
    for path in vault.rglob('*'):
        no_links(path)
        require(path.stat().st_uid == os.geteuid(), 'VAULT_ENTRY_OWNER_MISMATCH')
        if path.is_dir():
            require(stat.S_IMODE(path.stat().st_mode) == 0o700, 'VAULT_DIRECTORY_MODE')
    marker = vault / 'project-vault.json'
    if marker.exists():
        require(json.loads(owned_bytes(marker)) == {'version': 1, 'set': set_name}, 'PROJECT_VAULT_MARKER')
        adopting = False
    require(adopting or marker.exists(), 'ADOPT_PROJECT_FIRST')
    observer, local = set_name.startswith('observer-'), set_name.endswith('local')
    inventory = json.loads(owned_bytes(vault / 'owner-inventory.json', 128 * 1024))
    require(inventory['mode'] == ('local' if local else 'production'), 'VAULT_MODE_MISMATCH')
    env_path = vault / 'observer.env' if observer else project / ('.env' if local else '.env.prod')
    values = parse_env(owned_bytes(env_path))
    changes = {}

    def editable(name, seed):
        path = vault / 'editable' / name
        if adopting:
            require(isinstance(seed, str), 'ADOPTION_PASSWORD_MISSING')
            data = (seed + '\n').encode()
            if path.exists():
                require(owned_bytes(path) == data, 'ADOPTION_EDITABLE_CONTENT_DIFFERS')
        else:
            data = owned_bytes(path)
        value = password_text(data)
        changes[path] = (data, 0o600)
        return value

    operator = editable('operator-password', inventory['operator']['password'])
    if observer:
        names = {'RCT_GLITCHTIP_DB_PASSWORD_FILE': 'glitchtip-db-password',
                 'RCT_GLITCHTIP_SECRET_KEY_FILE': 'glitchtip-secret-key',
                 'RCT_OBSERVER_HTPASSWD_FILE': 'observer-htpasswd'}
        files = {name: owned_bytes(vault / 'files' / name, 8192) for name in names.values()}
        db_password = password_text(files['glitchtip-db-password'])
        signing_key = password_text(files['glitchtip-secret-key'])
        admin_password = editable('glitchtip-admin-password', inventory['glitchtip_admin']['password'])
        existing_hash = files['observer-htpasswd'].decode().rstrip('\n')
        new_hash = operator_line(existing_hash, operator)
        if adopting:
            require(new_hash == existing_hash and db_password == inventory['postgres']['password']
                    and signing_key == inventory['glitchtip_signing_key'], 'ADOPTION_CREDENTIAL_MISMATCH')
            require(values == inventory['environment'], 'ADOPTION_ENV_MISMATCH')
        require(len({db_password, signing_key, operator, admin_password}) == 4, 'OBSERVER_CREDENTIALS_NOT_INDEPENDENT')
        root = Path('.secrets') / set_name / 'files' if local else Path('/opt/rutcampustrack-secrets/observer')
        if not adopting:
            require(all(values.get(key) == str(root / name) for key, name in names.items()), 'OBSERVER_FILE_REFERENCE_MISMATCH')
        values.update({key: str(root / name) for key, name in names.items()})
        values.setdefault('RCT_GLITCHTIP_ADMIN_EMAIL', '')
        require(values['RCT_OBSERVER_PROJECT'] == ('rct-local-observability' if local else 'rct-observability-apps'), 'OBSERVER_PROJECT_NAME')
        files['observer-htpasswd'] = (new_hash + '\n').encode()
        inventory['postgres']['password'] = db_password
        inventory['glitchtip_signing_key'] = signing_key
        inventory['glitchtip_admin']['password'] = admin_password
        inventory['glitchtip_admin']['email'] = values['RCT_GLITCHTIP_ADMIN_EMAIL'] or None
    else:
        files = {name: owned_bytes(vault / 'files' / name, 8192) for name in sorted(ALLOWED)}
        if adopting:
            require(files == decode_bundle(owned_bytes(vault / 'PROD_SECRET_FILES_B64')), 'ADOPTION_FILE_BYTES_DIFFER')
            require(values == inventory['environment'], 'ADOPTION_ENV_MISMATCH')
            old_root = Path(values['PG_AUTH_RUNTIME_PASSWORD_FILE']).parent
            validate(values, files, old_root)
        monitor = editable('redis-monitor-password', json.loads(files[FILES['REDIS_MONITOR_PASSWORD_MAP_FILE']])['redis://redis:6379'])
        cache_monitor = editable('redis-cache-monitor-password', json.loads(files[FILES['REDIS_CACHE_MONITOR_PASSWORD_MAP_FILE']])['redis://redis-cache:6379'])
        old_hash = values['SWAGGER_HTPASSWD']
        new_hash = operator_line(old_hash, operator)
        if adopting:
            require(new_hash == old_hash, 'ADOPTION_OPERATOR_PASSWORD_MISMATCH')
        values['SWAGGER_HTPASSWD'] = new_hash
        root = Path('.secrets') / set_name / 'files' if local else SERVER_ROOT
        if not adopting:
            require(all(values.get(key) == str(root / name) for key, name in FILES.items()), 'FILE_REFERENCE_MISMATCH')
        values.update({key: str(root / name) for key, name in FILES.items()})
        for name in TLS:
            key = name.upper().replace('-', '_') + '_GRPC_TLS_DIR'
            require(adopting or values.get(key) == str(root / (name + '-tls')), 'TLS_REFERENCE_MISMATCH')
            values[key] = str(root / (name + '-tls'))
        rebuilt = acl_files(values, password_text(files[FILES['REDIS_CACHE_RUNTIME_PASSWORD_FILE']]), monitor, cache_monitor)
        if adopting:
            require(all(files[name] == data for name, data in rebuilt.items()), 'ADOPTION_DERIVED_BYTES_DIFFER')
        files.update(rebuilt)
        validate(values, files, root)
        changes[vault / 'PROD_SECRET_FILES_B64'] = (bundle_bytes(files), 0o600)
        # Stable JSON bytes across Python processes/hash seeds avoid no-op backups.
        inventory['files'] = {name: files[name].decode() for name in sorted(files)
                              if not name.endswith(('.crt', '.key'))}
    derived_names = {'observer-htpasswd'} if observer else {
        FILES[key] for key in ('REDIS_SERVER_ACL_FILE', 'REDIS_CACHE_SERVER_ACL_FILE',
                              'REDIS_MONITOR_PASSWORD_MAP_FILE', 'REDIS_CACHE_MONITOR_PASSWORD_MAP_FILE')}
    for name, data in files.items():
        # Private parent0700 prevents host access; bind-mounted local inputs remain
        # readable by their container UID and writable by their human owner.
        mode = (0o444 if name in derived_names else 0o644) if local else 0o600
        changes[vault / 'files' / name] = (data, mode)
    inventory['operator'] = {'login': new_hash.split(':', 1)[0], 'password': operator}
    inventory['environment'] = values
    inventory['derived'] = True
    inventory['authority'] = {'inline': str(env_path.relative_to(project)),
                              'files': str((vault / 'files').relative_to(project)),
                              'plaintext': str((vault / 'editable').relative_to(project))}
    envdata = env_bytes(values, 'Рабочая конфигурация: ' + str(env_path.relative_to(project)))
    require(len(envdata) <= LIMIT, 'ENV_EXCEEDS_GITHUB_48KB')
    changes[env_path] = (envdata, 0o600)
    if not observer:
        changes[vault / ('.env' if local else '.env.prod')] = (envdata, 0o600)
        changes[vault / 'PROD_ENV'] = (envdata, 0o600)
    changes[vault / 'owner-inventory.json'] = (json.dumps(inventory, indent=2).encode(), 0o600)
    changes[marker] = (json.dumps({'version': 1, 'set': set_name}).encode(), 0o600)
    return changes


def apply_project_plans(project, changes):
    """Preflight all output targets, stage + back up all bytes, then atomic replaces.

    A failure during replacements restores previous files. Backups are retained
    even on failure; no old vault, history or imported input is deleted.
    """
    pending = {}
    for path, (data, mode) in changes.items():
        no_links(path)
        require(path.is_relative_to(project), 'PROJECT_OUTPUT_ESCAPE')
        original = owned_bytes(path, 128 * 1024) if path.exists() else None
        old_mode = stat.S_IMODE(path.stat().st_mode) if original is not None else None
        if data != original or mode != old_mode:
            pending[path] = (data, mode, original, old_mode)
    if not pending:
        print('UNCHANGED project inputs and derived payloads verified')
        return
    history = project / '.secrets' / 'history'
    no_links(history)
    history.mkdir(mode=0o700, exist_ok=True)
    require(history.stat().st_uid == os.geteuid() and stat.S_IMODE(history.stat().st_mode) == 0o700, 'HISTORY_OWNER_MODE')
    transaction = history / ('refresh-' + str(time.time_ns()))
    transaction.mkdir(mode=0o700)
    records = []
    for index, (path, (data, mode, original, old_mode)) in enumerate(pending.items()):
        if original is not None:
            write_private(transaction / (str(index) + '.before'), original)
        write_private(transaction / (str(index) + '.next'), data, mode)
        records.append({'path': str(path.relative_to(project)), 'existed': original is not None, 'mode': old_mode})
    write_private(transaction / 'manifest.json', json.dumps(records, indent=2).encode())
    applied = []
    try:
        for index, path in enumerate(pending):
            path.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
            os.replace(transaction / (str(index) + '.next'), path)
            applied.append((index, path))
    except OSError:
        failed = False
        for index, path in reversed(applied):
            original, old_mode = pending[path][2:]
            try:
                if original is None:
                    path.unlink()
                else:
                    restore = transaction / (str(index) + '.restore')
                    write_private(restore, original, old_mode)
                    os.replace(restore, path)
            except OSError:
                failed = True
        raise ValueError('REFRESH_FAILED_BACKUPS_PRESERVED' if failed else 'REFRESH_FAILED_ROLLED_BACK') from None
    print('REFRESHED project files=' + str(len(pending)) + ' credentials_generated=false backups_preserved=true')


def project_command(args):
    project = Path(args.project_root)
    project_boundary(project)
    require(args.all or args.mode, 'MODE_OR_ALL_REQUIRED')
    require(not (args.all and (args.mode or args.observer)), 'ALL_EXCLUDES_MODE_OR_OBSERVER')
    names = ['production', 'local', 'observer-production', 'observer-local'] if args.all else [
        ('observer-' if args.observer else '') + args.mode]
    changes = {}
    for name in names:
        changes.update(project_plan(project, name, args.command == 'adopt-project'))
    # Every selected set passes before any mutation, including the --all path.
    apply_project_plans(project, changes)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest='command', required=True)
    prepare = sub.add_parser('prepare')
    prepare.add_argument('--mode', choices=['production', 'local'], required=True)
    prepare.add_argument('--output', required=True)
    prepare.add_argument('--import-env')
    prepare.add_argument('--server-root', default=str(SERVER_ROOT), choices=[str(SERVER_ROOT)])
    prepare.add_argument('--ci-key', action='store_true')
    observer = sub.add_parser('prepare-observer')
    observer.add_argument('--mode', choices=['production', 'local'], required=True)
    observer.add_argument('--output', required=True)
    for command in ('adopt-project', 'refresh-project'):
        item = sub.add_parser(command)
        item.add_argument('--project-root', default=str(ROOT))
        item.add_argument('--mode', choices=['production', 'local'])
        item.add_argument('--observer', action='store_true')
        item.add_argument('--all', action='store_true')
    for command in ('validate', 'install'):
        item = sub.add_parser(command)
        item.add_argument('--env', required=True)
        item.add_argument('--bundle', required=True)
        item.add_argument('--target', default=str(SERVER_ROOT))
    args = parser.parse_args()
    try:
        os.umask(0o077)
        if args.command == 'prepare':
            create(args)
        elif args.command == 'prepare-observer':
            create_observer(args)
        elif args.command in ('adopt-project', 'refresh-project'):
            project_command(args)
        elif args.command == 'install':
            install(args)
        else:
            validate(parse_env(read_file(Path(args.env))), decode_bundle(read_file(Path(args.bundle))), Path(args.target))
            print('VALIDATED environment, allowlist, credentials and TLS; services untouched')
    except (ValueError, OSError, KeyError, TypeError, UnicodeError) as error:
        # Exception text can contain input values/paths. Emit only our constant code.
        code = str(error) if type(error) is ValueError and re.fullmatch('[A-Z0-9_]+', str(error)) else type(error).__name__
        print('FAILED ' + code, file=sys.stderr)
        return 1
    return 0


if __name__ == '__main__':
    sys.exit(main())
