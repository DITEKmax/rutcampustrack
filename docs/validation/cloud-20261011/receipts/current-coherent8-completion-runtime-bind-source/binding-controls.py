"""Actual immutable consumer + actual SAFE receipts; mocked artifact filesystem in RAM."""
import ast
import copy
import datetime
import hashlib
import json
from pathlib import Path
import re
import stat
import types

OWN = Path(__file__).parent
BASE = OWN.parent
SOURCE = BASE / 'current_academic_stage_completion_prepare'
ACTUAL = SOURCE / 'actual-completion01'
PREFIX = Path('/tmp/rct-academic-refresh-20261012-attempt01')
APP = PREFIX / 'app'
proof = json.loads((ACTUAL / 'stage-result.safe.json').read_text())
commit = json.loads((ACTUAL / 'stage-committed.safe.json').read_text())
specs = json.loads((ACTUAL / 'host-service-specs.safe.json').read_text())
grant = json.loads((OWN / 'restart-grant.template.safe.json').read_text())
grant['stop_utc'] = '2026-10-12T23:30:00+00:00'  # RAM synthetic future, no private grant exists.
results = []
state = {'bad_sha': None, 'bad_meta': None, 'late': False, 'namespace_closed': 0}


def selected(path, names, ns):
    nodes = [n for n in ast.parse(path.read_text()).body
             if isinstance(n, ast.FunctionDef) and n.name in names]
    assert {n.name for n in nodes} == set(names)
    exec(compile(ast.Module(body=nodes, type_ignores=[]), str(path), 'exec'), ns)
    return ns


def passed(name):
    results.append(name)


def deny(name, action):
    try:
        action()
    except RuntimeError:
        passed(name)
    else:
        raise AssertionError(name)


table = {}
for row in json.loads((SOURCE / 'manifest.json').read_text())['files']:
    table[row['path']] = Path(row['path']).read_bytes()
table[str(SOURCE / 'manifest.json')] = (SOURCE / 'manifest.json').read_bytes()
for name in ['stage-result.safe.json', 'stage-committed.safe.json', 'host-service-specs.safe.json']:
    table[str(PREFIX / name)] = (ACTUAL / name).read_bytes()
review = Path(grant['academic_refresh_review_manifest'])
table[str(review)] = review.read_bytes()
old_specs = BASE / 'current_host_config_v5/host-service-specs.json'
table[str(old_specs)] = old_specs.read_bytes()
tools = json.loads(table[str(SOURCE / 'tool-inputs.safe.json')])
declared_tool_hashes = {row['path']: row['sha256'] for row in tools['files']}
file_rows = {str(APP / row['path']): row for row in proof['files']}
directories = {APP}
for name in file_rows:
    directories.update(p for p in Path(name).parents if p == APP or APP in p.parents)


class SafePath(type(Path())):
    def read_text(self, *args, **kwargs):
        return table[str(self)].decode()
    def lstat(self):
        name = str(self)
        if name in file_rows:
            row = file_rows[name]
            metadata = dict(st_mode=stat.S_IFREG | 0o644, st_uid=1000, st_gid=1000,
                            st_nlink=1, st_size=row['bytes'], st_dev=row['device'], st_ino=row['inode'])
            if state['bad_meta'] and name == next(iter(file_rows)):
                metadata.update(state['bad_meta'])
            return types.SimpleNamespace(**metadata)
        return types.SimpleNamespace(st_mode=stat.S_IFDIR | 0o755, st_uid=1000, st_gid=1000)


def digest(path):
    name = str(path)
    if state['bad_sha'] == name:
        return '0' * 64
    if name == proof['jar']:
        return proof['jar_sha256']  # Existing actual JAR proof, not an actual artifact read.
    if name in file_rows:
        return file_rows[name]['sha256']
    if name in declared_tool_hashes:
        return declared_tool_hashes[name]
    return hashlib.sha256(table[name]).hexdigest()


def walk(app, followlinks=False):
    assert str(app) == str(APP) and followlinks is False
    for parent in sorted(directories, key=str):
        dirs = [p.name for p in directories if p.parent == parent]
        files = [Path(p).name for p in file_rows if Path(p).parent == parent]
        yield str(parent), dirs, files


class Namespace:
    def __init__(self, callback):
        pass
    def check(self):
        pass
    def close(self):
        state['namespace_closed'] += 1


def fresh(stop):
    if state['late']:
        raise RuntimeError('RAM expiry after full verification')


package = {'OWN': SafePath(SOURCE), 'json': json, 'Path': SafePath, 'sha': digest,
           'regular': lambda path: SafePath(path)}
selected(SOURCE / 'refresh-boundary.py', ['verify_package'], package)
rebind = {'copy': copy, 're': re,
          'OLD_APP': BASE / 'current_host_stage/payload/academic/app',
          'CLOCK': BASE / 'real_client_runtime/teaching-clock-eec3/clock-fixture.jar'}
selected(SOURCE / 'stage-academic.py', ['rebind_specs'], rebind)
stage = types.SimpleNamespace(BASE_SPECS=SafePath(old_specs), rebind_specs=rebind['rebind_specs'])
boundary = types.SimpleNamespace(PREFIX=SafePath(PREFIX),
    FIX_SHA=grant['fixed_UserSpecifications_sha256'], FIX_REVISION=grant['frozen_source_revision'],
    verify_package=package['verify_package'], Namespace=Namespace, sha=digest,
    regular=lambda path: SafePath(path), load=lambda path, name: stage, fresh=fresh)
consumer = {'OWN': SafePath(SOURCE), 'boundary': boundary, 'Path': SafePath,
            'json': json, 'os': types.SimpleNamespace(walk=walk), 'stat': stat, 'datetime': datetime}
selected(SOURCE / 'runtime-binding.py', ['verify'], consumer)
runner = SOURCE / 'run-academic-restart.py'
assert consumer['verify'](grant, runner) == specs
assert state['namespace_closed'] == 1
passed('whole_actual_runtime_consumer_actual_result_commit_spec182_package_RAM_positive')
assert len(proof['library_order']) == 182 and len(proof['files']) == 1797
assert [s for s in specs if s['service'] != 'academic'] == [s for s in json.loads(table[str(old_specs)]) if s['service'] != 'academic']
passed('actual_other6_specs_equal_only_Academic_cwd_classpath_delta')
for field, bad in [
    ('academic_refresh_runtime_authorized', False), ('frozen_source_revision', 'OLD'),
    ('academic_full_app_path', '/RAM/foreign'), ('academic_stage_receipt_path', '/RAM/foreign'),
    ('academic_stage_receipt_sha256', '0' * 64), ('academic_stage_commit_receipt_path', '/RAM/foreign'),
    ('academic_stage_commit_receipt_sha256', '0' * 64), ('academic_refresh_manifest_sha256', '0' * 64),
]:
    changed = dict(grant, **{field: bad})
    deny('runtime_grant_' + field, lambda: consumer['verify'](changed, runner))
deny('unsealed_executing_Academic_runner', lambda: consumer['verify'](grant, SOURCE / 'foreign.py'))
for path, name in [(str(PREFIX / 'host-service-specs.safe.json'), 'spec'),
                   (proof['jar'], 'JAR'), (next(iter(file_rows)), 'member')]:
    state['bad_sha'] = path
    deny('actual_consumer_' + name + '_SHA_mismatch', lambda: consumer['verify'](grant, runner))
state['bad_sha'] = None
for field, bad in [('st_ino', -1), ('st_nlink', 2), ('st_uid', 0), ('st_mode', stat.S_IFREG | 0o600)]:
    state['bad_meta'] = {field: bad}
    deny('actual_consumer_member_' + field, lambda: consumer['verify'](grant, runner))
state['bad_meta'] = None
state['late'] = True
deny('actual_consumer_freshclock_after_full_verification_denied', lambda: consumer['verify'](grant, runner))
state['late'] = False
restart = BASE / 'current_host_restart_data157_docker_correction'
definitions = {'datetime': datetime, 're': re, 'DB': Path(grant['reuse_existing_mongo_db']),
               'KEY': Path(grant['reuse_existing_mongo_key'])}
selected(restart / 'restart-boundary.py', ['validate_restart_fields'], definitions)
now = datetime.datetime(2026, 10, 12, 23, tzinfo=datetime.timezone.utc)
g = dict(grant, not_before_utc=now.isoformat(), stop_utc=(now + datetime.timedelta(seconds=1800)).isoformat())
assert definitions['validate_restart_fields'](g, now) == 1800
passed('original157_durable_fields_newcohort1800_template_positive_RAM')
for field, bad in [('repeat_bootstrap_authorized', True), ('data_reset_authorized', True),
                   ('reuse_existing_mongo_db', '/RAM/foreign'), ('cohort_name', '../foreign')]:
    deny('original157_boundary_' + field, lambda: definitions['validate_restart_fields'](dict(g, **{field: bad}), now))
print(json.dumps({'scope': 'ACTUAL_SAFE_BIND_UNCHANGED_CONSUMER_SOURCE_RAM', 'passed': len(results),
    'controls': results, 'actual_result_SHA': grant['academic_stage_receipt_sha256'],
    'actual_commit_SHA': grant['academic_stage_commit_receipt_sha256'],
    'actual_artifact_data_private_environment_reads': False, 'runtime': 'NOT_RUN',
    'qualification': 'Exact selected consumer and real SAFE bytes; artifact filesystem/JAR hashes/tool references/namespace/clock modeled in RAM. No actual fresh artifact/data/port/container or process availability proof.'}, indent=2))
