#!/usr/bin/env python3
"""Controlled settings/startup checks with no real environment or application startup."""
import ast
import base64
import hashlib
import importlib.metadata
import json
import os
from pathlib import Path
import secrets
import subprocess
import sys
from unittest.mock import patch

import yaml

OUT = Path(__file__).resolve().parent
WT = Path('/workspace/rct-worktrees/np01-config')
BASE = '2bca095380f72e8908a2511f2b10f0e795607f05'
CONFIG = 'services/notification-bot/bot/config.py'
YAML = 'services/academic-service/academic-app/src/main/resources/application.yml'
os.umask(0o077)
sys.path.insert(0, str(WT / 'services/notification-bot'))

def load_config(source):
    # Load actual declarations without app dependency imports or eager global Settings().
    tree = ast.parse(source)
    tree.body = [n for n in tree.body if not (
        isinstance(n, ast.ImportFrom) and n.module == 'bot.services.mini_app_links'
    ) and not (isinstance(n, ast.Assign) and any(isinstance(t, ast.Name) and t.id == 'config' for t in n.targets))]
    mini = ast.parse((WT / 'services/notification-bot/bot/services/mini_app_links.py').read_text())
    mini.body = [n for n in mini.body if not (isinstance(n, ast.ImportFrom) and n.module == 'aiogram.types')]
    namespace = {'__name__': 'controlled_mini_app'}
    exec(compile(mini, 'actual-mini-app-helper', 'exec'), namespace)
    config_namespace = {'__name__': 'controlled_settings', 'build_mini_app_url': namespace['build_mini_app_url']}
    exec(compile(tree, 'actual-settings-declarations', 'exec'), config_namespace)
    return config_namespace

source = (WT / CONFIG).read_bytes()
old_source = subprocess.check_output(['git', 'show', BASE + ':' + CONFIG], cwd=WT)
before = load_config(old_source.decode())
after = load_config(source.decode())
checks = {}
old_yaml = yaml.safe_load(subprocess.check_output(['git', 'show', BASE + ':' + YAML], cwd=WT))
new_yaml = yaml.safe_load((WT / YAML).read_bytes())
identity = new_yaml['grpc']['service-identity']
assert identity.pop('bot-to-academic-token') == '${BOT_TO_ACADEMIC_SERVICE_TOKEN:}'
assert new_yaml == old_yaml
checks['yaml_one_additive_blank_default_property'] = True

with patch.dict(os.environ, {}, clear=True):
    old = before['Settings'](_env_file=None)
    new = after['Settings'](_env_file=None)
    expected = old.model_dump()
    observed = new.model_dump()
    assert observed.pop('bot_to_academic_service_token') == ''
    assert observed == expected
    checks['all_legacy_settings_defaults_preserved'] = True
    checks['academic_existing_host_port_tls_ca_defaults_preserved'] = True
    safe_token = base64.urlsafe_b64encode(secrets.token_bytes(32)).decode().rstrip('=')
    academic_token = base64.urlsafe_b64encode(secrets.token_bytes(32)).decode().rstrip('=')
    legacy_args = {'grpc_secret': secrets.token_hex(12), 'bot_token': secrets.token_hex(12),
                   'bot_to_notification_service_token': safe_token, '_env_file': None}
    before['validate_startup_config'](before['Settings'](**legacy_args))
    for token in ['', ' ', 'malformed', academic_token + '=', academic_token]:
        settings = after['Settings'](**legacy_args, bot_to_academic_service_token=token)
        after['validate_startup_config'](settings)
        if token and token not in (' ', 'malformed'):
            assert token not in repr(settings)
    checks['legacy_startup_compatible_without_new_token_or_with_malformed_string'] = True
    checks['new_token_not_automatic_settings_repr'] = True
    absent = after['Settings'](**legacy_args)
    assert absent.bot_to_academic_service_token == ''
    assert absent.bot_to_academic_service_token != absent.bot_to_notification_service_token
    assert absent.bot_to_academic_service_token != absent.grpc_secret
    checks['no_notification_or_shared_grpc_secret_fallback'] = True
    with patch.dict(os.environ, {'BOT_TO_ACADEMIC_SERVICE_TOKEN': academic_token}):
        assert after['Settings'](_env_file=None).bot_to_academic_service_token == academic_token
    with patch.dict(os.environ, {'bot_to_academic_service_token': academic_token}):
        assert after['Settings'](_env_file=None).bot_to_academic_service_token == academic_token
    checks['uppercase_and_case_insensitive_env_aliases'] = True
    checks['new_field_remains_str'] = after['Settings'].model_fields['bot_to_academic_service_token'].annotation is str

old_ast = ast.parse(old_source.decode())
new_ast = ast.parse(source.decode())
old_guard = next(x for x in old_ast.body if isinstance(x, ast.FunctionDef) and x.name == 'validate_startup_config')
new_guard = next(x for x in new_ast.body if isinstance(x, ast.FunctionDef) and x.name == 'validate_startup_config')
assert ast.dump(old_guard) == ast.dump(new_guard)
checks['legacy_startup_validator_ast_unchanged'] = True
receipt = {'status': 'PASS_BOUNDED_CONFIG_SOURCE_AND_CONTROLLED_SETTINGS', 'frozen_base': BASE,
           'checks': checks, 'observed_versions': {n: importlib.metadata.version(n) for n in ['pydantic', 'pydantic-settings', 'PyYAML']},
           'pinned_bot_versions': {'pydantic': '2.9.2', 'pydantic-settings': '2.6.1'},
           'method': 'Actual Settings/startup and MiniApp helper AST declarations, app-only imports/eager global instance omitted; no real env/dotenv/credentials read. Pure YAML comparison.',
           'limits': ['Pinned bot dependency runtime NOT_RUN.', 'Protected RPC malformed-token/TLS/target/method denial belongs to domain receiver/client checks, NOT_RUN here.', 'No Java/Gradle/app/provider/secure RPC runtime.'],
           'input_sha256': {p: hashlib.sha256((WT / p).read_bytes()).hexdigest() for p in [CONFIG, YAML]}}
(OUT / 'verification.safe.json').write_text(json.dumps(receipt, indent=2) + '\n')
print(json.dumps({'status': receipt['status'], 'checks': checks, 'observed_versions': receipt['observed_versions'], 'pinned_runtime': 'NOT_RUN'}))
