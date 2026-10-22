"""F01/F02 exact selected consumers, RAM adapters only; no prefix/private/native reads."""
import ast
import copy
import datetime
import hashlib
import io
import json
import os
from pathlib import Path
import re
import stat
import types

OWN = Path(__file__).parent
OLD = OWN.parent / 'current_academic_stage_resume_prepare'
results = []


def selected(root, file, names, namespace):
    nodes = [n for n in ast.parse((root / file).read_text()).body
             if isinstance(n, (ast.FunctionDef, ast.ClassDef)) and n.name in names]
    assert {n.name for n in nodes} == set(names)
    exec(compile(ast.Module(body=nodes, type_ignores=[]), file, 'exec'), namespace)
    return namespace


def passed(name):
    results.append(name)


def deny(name, action, error=RuntimeError):
    try:
        action()
    except error:
        passed(name)
    else:
        raise AssertionError(name)


actual_manifest = (OWN / 'manifest.json').read_bytes()
manifest = json.loads(actual_manifest)
tools = json.loads((OWN / 'tool-inputs.safe.json').read_text())
table = {str(OWN / r['path']): (OWN / r['path']).read_bytes() for r in manifest['entries']}
review = OWN.parent / 'RAM-review' / 'manifest.json'
table[str(review)] = b'{}'
for row in tools['files']:
    table.setdefault(row['path'], None)
state = {'manifest': actual_manifest, 'tool_bad': False, 'member_bad': False}


class PublicPath(type(Path())):
    def read_text(self, *args, **kwargs):
        if str(self) == str(OWN / 'manifest.json'):
            return state['manifest'].decode()
        return table[str(self)].decode()


def digest(path):
    name = str(path)
    if name == str(OWN / 'manifest.json'):
        return hashlib.sha256(state['manifest']).hexdigest()
    if state['member_bad'] and name == str(OWN / 'stage-academic.py'):
        return '0' * 64
    for row in tools['files']:
        if name == row['path']:
            return '0' * 64 if state['tool_bad'] else row['sha256']
    return hashlib.sha256(table[name]).hexdigest()


package_ns = selected(OWN, 'refresh-boundary.py', ['verify_package'],
    {'OWN': PublicPath(OWN), 'json': json, 'Path': PublicPath, 'sha': digest,
     'regular': lambda p: PublicPath(p)})
grant = {'academic_refresh_manifest_sha256': hashlib.sha256(actual_manifest).hexdigest(),
         'academic_refresh_review_manifest': str(review),
         'academic_refresh_review_manifest_sha256': hashlib.sha256(b'{}').hexdigest()}
for source in ['stage-academic.py', 'run-academic-restart.py']:
    package_ns['verify_package'](grant, OWN / source)
    passed('whole_actual_manifest_consumer_' + source)
deny('unsealed_executing_source_denied', lambda: package_ns['verify_package'](grant, OWN / 'unsealed.py'))
state['member_bad'] = True
deny('sealed_member_hash_mismatch_denied', lambda: package_ns['verify_package'](grant, OWN / 'stage-academic.py'))
state['member_bad'] = False
changed = copy.deepcopy(manifest)
changed['files'][0]['path'] = str(OWN / 'outside' / Path(changed['files'][0]['path']).name)
table[changed['files'][0]['path']] = (OWN / manifest['entries'][0]['path']).read_bytes()
state['manifest'] = json.dumps(changed).encode()
changed_grant = dict(grant, academic_refresh_manifest_sha256=hashlib.sha256(state['manifest']).hexdigest())
deny('nested_member_containment_denied', lambda: package_ns['verify_package'](changed_grant, OWN / 'stage-academic.py'))
state['manifest'] = actual_manifest
state['tool_bad'] = True
deny('accepted_tool_hash_mismatch_denied', lambda: package_ns['verify_package'](grant, OWN / 'stage-academic.py'))
state['tool_bad'] = False
deny('independent_review_hash_mismatch_denied', lambda: package_ns['verify_package'](
    dict(grant, academic_refresh_review_manifest_sha256='0' * 64), OWN / 'stage-academic.py'))
deny('source_manifest_hash_mismatch_denied', lambda: package_ns['verify_package'](
    dict(grant, academic_refresh_manifest_sha256='0' * 64), OWN / 'stage-academic.py'))
old_manifest = (OLD / 'manifest.json').read_bytes()
old_ns = selected(OLD, 'refresh-boundary.py', ['verify_package'],
    {'OWN': OLD, 'json': json, 'Path': Path,
     'sha': lambda p: hashlib.sha256(Path(p).read_bytes()).hexdigest()})
deny('old_actual_manifest_KeyError_files_counterexample', lambda: old_ns['verify_package'](
    {'academic_refresh_manifest_sha256': hashlib.sha256(old_manifest).hexdigest()}, OLD / 'stage-academic.py'), KeyError)

# Exact verify_grant -> validate_fields -> verify_package chain, grant contents in RAM.
gpath = OWN / 'private' / 'stage-grant.json'
ram_grant = json.loads((OWN / 'stage-grant.template.safe.json').read_text())
now = datetime.datetime(2026, 10, 12, 22, tzinfo=datetime.timezone.utc)
ram_grant.update(grant, not_before_utc=now.isoformat(),
                 stop_utc=(now + datetime.timedelta(seconds=600)).isoformat())
class GrantPath(PublicPath):
    def lstat(self):
        return types.SimpleNamespace(st_uid=1000, st_gid=1000, st_nlink=1,
                                     st_mode=stat.S_IFREG | 0o600, st_size=4096)
    def read_text(self, *args, **kwargs):
        return json.dumps(ram_grant) if str(self) == str(gpath) else super().read_text()
class ClockDate(datetime.datetime):
    @classmethod
    def now(cls, tz=None):
        return now
chain = dict(package_ns, os=types.SimpleNamespace(getuid=lambda: 1000, getgid=lambda: 1000),
             stat=stat, datetime=types.SimpleNamespace(datetime=ClockDate, timezone=datetime.timezone,
                 timedelta=datetime.timedelta), regular=lambda p: GrantPath(p),
             PREFIX=Path('/tmp/rct-academic-refresh-20261012-attempt01'),
             FIX_REVISION=ram_grant['frozen_source_revision'], FIX_SHA=ram_grant['fixed_UserSpecifications_sha256'])
selected(OWN, 'refresh-boundary.py', ['validate_fields', 'verify_grant'], chain)
assert chain['verify_grant'](gpath, 'STAGE', OWN / 'stage-academic.py')[0] == ram_grant
passed('whole_verify_grant_fields_actual_manifest_tools_review_RAM_positive')

clock = {'now': 539.99, 'dump_delay': 0, 'open_delay': 0, 'write_delay': 0, 'partial': False}
effects = []
metadata = types.SimpleNamespace(st_mode=stat.S_IFREG | 0o600, st_uid=1000, st_gid=1000, st_nlink=1)
def fresh(**kwargs):
    if clock['now'] >= 540:
        raise RuntimeError('Synthetic cleanup gate reached')
def dumps(value, indent):
    clock['now'] += clock['dump_delay']
    return json.dumps(value, indent=indent)
def opened(*args, **kwargs):
    effects.append(('open', clock['now']))
    clock['now'] += clock['open_delay']
    return 7
def write(fd, block):
    effects.append(('write', clock['now'], len(block)))
    clock['now'] += clock['write_delay']
    return 1 if clock['partial'] else len(block)
fake_os = types.SimpleNamespace(**{name: getattr(os, name) for name in
    ['O_WRONLY', 'O_CREAT', 'O_NOFOLLOW', 'O_CLOEXEC', 'O_EXCL']},
    open=opened, fstat=lambda fd: metadata, write=write,
    ftruncate=lambda *args: effects.append(('truncate', clock['now'])),
    fsync=lambda fd: effects.append(('fsync', clock['now'])),
    close=lambda fd: effects.append(('close_no_pending_bytes', clock['now'])))
ns_class = selected(OWN, 'refresh-boundary.py', ['Namespace'],
    {'os': fake_os, 'stat': stat, 're': re, 'json': types.SimpleNamespace(dumps=dumps)})['Namespace']
def ns():
    n = ns_class.__new__(ns_class)
    n.fds = [7]; n.check = lambda: None; n.before_mutation = fresh
    return n
def reset(**changes):
    effects.clear()
    clock.update(now=539.99, dump_delay=0, open_delay=0, write_delay=0, partial=False, **changes)
def before_effects():
    assert not any(e[0] in ['open', 'write', 'truncate', 'fsync'] and e[1] >= 540 for e in effects)
for name in ['host-service-specs.safe.json', 'stage-result.safe.json', 'stage-committed.safe.json']:
    reset(); ns().save(name, {'public': True}, new=True, before_effect=fresh)
    assert [e[0] for e in effects] == ['open', 'write', 'fsync', 'close_no_pending_bytes']
    passed('receipt_positive_all_effects_gated_' + name)
reset(); clock['dump_delay'] = 0.02
deny('serialization_crosses_boundary_before_first_create_denied',
     lambda: ns().save('stage-result.safe.json', {}, new=True, before_effect=fresh))
assert not effects
reset(); clock['open_delay'] = 0.02
deny('create_consumes_window_no_write_on_close',
     lambda: ns().save('stage-result.safe.json', {}, new=True, before_effect=fresh))
before_effects(); assert not any(e[0] == 'write' for e in effects)
reset(); clock.update(partial=True, write_delay=0.02)
deny('partial_raw_write_next_write_rechecks_no_close_flush',
     lambda: ns().save('stage-result.safe.json', {}, new=True, before_effect=fresh))
before_effects(); assert sum(e[0] == 'write' for e in effects) == 1
reset(); clock['open_delay'] = 0.02
deny('truncate_expiry_denied_before_truncate', lambda: ns().save('stage-result.safe.json', {}))
assert not any(e[0] == 'truncate' for e in effects)
reset(); clock['write_delay'] = 0.02
deny('fsync_expiry_denied', lambda: ns().save('stage-result.safe.json', {}, new=True, before_effect=fresh))
assert not any(e[0] == 'fsync' for e in effects)

# Preserve and reproduce actual RAM BufferedWriter old exception-close witness.
class Raw(io.BytesIO):
    def fileno(self):
        return 7
    def write(self, value):
        effects.append(('buffered_write', clock['now'], len(value)))
        return super().write(value)
class Stream:
    def __init__(self, fail=False):
        self.calls = 0; self.fail = fail
    def read(self, size):
        self.calls += 1
        if self.calls == 1:
            return b'x' * 10
        clock['now'] += 0.02
        if self.fail:
            raise RuntimeError('Synthetic read failure')
        return b''
fake_os.fdopen = lambda *args: io.BufferedWriter(Raw())
def writer(root):
    cls = selected(root, 'stage-app-writer.py', ['AppWriter'],
                   {'os': fake_os, 'stat': stat, 'Path': Path})['AppWriter']
    w = cls.__new__(cls); w.directory = lambda rel: 7; w.check = lambda rel: None
    w.meta = 65536; w.write_upper = lambda n: ((n + 4095) // 4096) * 4096 + 65536
    w.use = fresh; w.copied_files = 0
    return w
metadata.st_mode = stat.S_IFREG | 0o644
reset()
deny('old_REAL_RAM_BufferedWriter_exception_close_witness',
     lambda: writer(OLD).copy({'path': 'public.class', 'bytes': 10}, Stream()))
assert any(e[0] == 'buffered_write' and e[1] >= 540 for e in effects)
reset()
deny('new_terminal_read_expiry_no_exception_close_write',
     lambda: writer(OWN).copy({'path': 'public.class', 'bytes': 10}, Stream()))
before_effects(); assert not any(e[0] == 'buffered_write' for e in effects)
reset()
deny('new_read_failure_no_exception_close_write',
     lambda: writer(OWN).copy({'path': 'public.class', 'bytes': 10}, Stream(fail=True)))
before_effects(); assert not any(e[0] == 'buffered_write' for e in effects)
reset(); clock.update(partial=True, write_delay=0.02)
deny('new_app_partial_write_expiry_denied_before_next_write',
     lambda: writer(OWN).copy({'path': 'public.class', 'bytes': 10}, Stream()))
before_effects(); assert sum(e[0] == 'write' for e in effects) == 1
class TimelyStream(Stream):
    def read(self, size):
        self.calls += 1
        return b'x' * 10 if self.calls == 1 else b''
reset()
w = writer(OWN)
w.copy({'path': 'public.class', 'bytes': 10}, TimelyStream())
assert w.copied_files == 1
assert [e[0] for e in effects] == ['open', 'write', 'fsync', 'close_no_pending_bytes']
passed('new_app_complete_raw_write_positive_no_buffered_flush')
reset()
metadata.st_mode = stat.S_IFREG | 0o600
calls = {'count': 0}
def resource_change_gate(**kwargs):
    calls['count'] += 1
    fresh(**kwargs)
    if calls['count'] == 2:
        raise RuntimeError('Synthetic fresh resource floor changed after create')
deny('internal_receipt_resource_floor_changes_before_write_denied',
     lambda: ns().save('stage-result.safe.json', {}, new=True, before_effect=resource_change_gate))
assert not any(e[0] == 'write' for e in effects)
print(json.dumps({'scope': 'TARGETED_F01_F02_RAM_CORRECTION', 'passed': len(results),
    'controls': results, 'actual_prefix_scan': False, 'actual_copy_private_native': False,
    'original43_and_independent23_reused_not_rerun': True}, indent=2))
