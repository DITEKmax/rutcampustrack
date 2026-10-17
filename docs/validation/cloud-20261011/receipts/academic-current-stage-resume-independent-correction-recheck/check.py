"""Narrow F01/F02 recheck; selected public source and RAM adapters only."""
import ast
import datetime
import hashlib
import io
import json
import os
from pathlib import Path
import re
import stat
import types

HERE = Path(__file__).parent
NEW = Path('/workspace/scratch/rct-validation/current_academic_stage_resume_guard_correction')
OLD = Path('/workspace/scratch/rct-validation/current_academic_stage_resume_prepare')
groups = []


def extract(root, filename, names, namespace):
    tree = ast.parse((root / filename).read_bytes())
    nodes = [n for n in tree.body if isinstance(n, (ast.ClassDef, ast.FunctionDef)) and n.name in names]
    assert {n.name for n in nodes} == set(names)
    exec(compile(ast.Module(body=nodes, type_ignores=[]), filename, 'exec'), namespace)
    return namespace


def passed(name, **facts):
    groups.append(dict(name=name, outcome='PASS_SOURCE_RAM', **facts))


def denied(name, action, message):
    try:
        action()
    except RuntimeError as error:
        assert str(error) == message
        passed(name, exact_denial=message)
    else:
        raise AssertionError(name)


manifest_bytes = (NEW / 'manifest.json').read_bytes()
manifest = json.loads(manifest_bytes)
assert len(manifest['files']) == len(manifest['entries']) == 27
table = {str(NEW / r['path']): (NEW / r['path']).read_bytes() for r in manifest['entries']}
for rel, absolute in zip(manifest['entries'], manifest['files']):
    assert absolute['path'] == str(NEW / rel['path'])
    assert Path(absolute['path']).parent == NEW
    assert all(absolute[k] == rel[k] for k in ('bytes', 'sha256', 'mode'))
passed('F01_actual_manifest_absolute_relative_bijection27')
tools = json.loads((NEW / 'tool-inputs.safe.json').read_text())
review_path = NEW.parent / 'RAM-independent-review/manifest.json'
table[str(review_path)] = b'{}'
state = {'manifest': manifest_bytes, 'bad_member': False, 'bad_tool': False}


class RamPath(type(Path())):
    def lstat(self):
        return types.SimpleNamespace(st_mode=stat.S_IFREG | 0o600,
            st_uid=1000, st_gid=1000, st_nlink=1, st_size=4096)
    def read_text(self, *args, **kwargs):
        if str(self) == str(NEW / 'manifest.json'):
            return state['manifest'].decode()
        if str(self) == str(NEW / 'private/stage-grant.json'):
            return json.dumps(grant)
        return table[str(self)].decode()


def sha(path):
    name = str(path)
    if name == str(NEW / 'manifest.json'):
        return hashlib.sha256(state['manifest']).hexdigest()
    if state['bad_member'] and name == str(NEW / 'stage-academic.py'):
        return '0' * 64
    for row in tools['files']:
        if row['path'] == name:
            return '0' * 64 if state['bad_tool'] else row['sha256']
    return hashlib.sha256(table[name]).hexdigest()


now = datetime.datetime(2026, 10, 12, 22, tzinfo=datetime.timezone.utc)
class ClockDate(datetime.datetime):
    @classmethod
    def now(cls, tz=None):
        return now


grant = json.loads((NEW / 'stage-grant.template.safe.json').read_text())
grant.update(not_before_utc=now.isoformat(), stop_utc=(now + datetime.timedelta(seconds=600)).isoformat(),
    academic_refresh_manifest_sha256=hashlib.sha256(manifest_bytes).hexdigest(),
    academic_refresh_review_manifest=str(review_path),
    academic_refresh_review_manifest_sha256=hashlib.sha256(b'{}').hexdigest())
ns = extract(NEW, 'refresh-boundary.py', ['verify_package', 'verify_grant', 'validate_fields'],
    {'OWN': RamPath(NEW), 'Path': RamPath, 'regular': lambda p: RamPath(p), 'sha': sha,
     'json': json, 'stat': stat, 'os': types.SimpleNamespace(getuid=lambda: 1000, getgid=lambda: 1000),
     'datetime': types.SimpleNamespace(datetime=ClockDate, timezone=datetime.timezone, timedelta=datetime.timedelta),
     'PREFIX': Path('/tmp/rct-academic-refresh-20261012-attempt01'),
     'FIX_SHA': '49dbdf2833a6be5ac35c3b873570139d62b4b965ed52370b7543590c9a998e3b',
     'FIX_REVISION': '96c7aee0a114e978ce3ed2976127a2a59c1a69e3'})
actual_grant, stop = ns['verify_grant'](NEW / 'private/stage-grant.json', 'STAGE', NEW / 'stage-academic.py')
assert actual_grant == grant and stop == now + datetime.timedelta(seconds=600)
ns['verify_package'](grant, NEW / 'run-academic-restart.py')
passed('F01_full_actual_grant_fields_package_stage_and_runtime_positive_RAM_private_tool_review_inputs')
denied('F01_unsealed_executing_source', lambda: ns['verify_package'](grant, NEW / 'unsealed.py'),
       'Actual executing source must be sealed')
state['bad_member'] = True
denied('F01_member_hash', lambda: ns['verify_package'](grant, NEW / 'stage-academic.py'), 'Refresh source identity changed')
state['bad_member'] = False
state['bad_tool'] = True
denied('F01_tool_hash', lambda: ns['verify_package'](grant, NEW / 'stage-academic.py'),
       'Accepted wrapper/JDK/guard/source input changed')
state['bad_tool'] = False
old_ns = extract(OLD, 'refresh-boundary.py', ['verify_package'],
    {'OWN': OLD, 'json': json, 'Path': Path,
     'sha': lambda p: hashlib.sha256(Path(p).read_bytes()).hexdigest()})
try:
    old_ns['verify_package']({'academic_refresh_manifest_sha256': hashlib.sha256((OLD / 'manifest.json').read_bytes()).hexdigest()},
        OLD / 'stage-academic.py')
except KeyError as error:
    assert error.args == ('files',)
    passed('F01_original_exact_keyerror_witness_retained')
else:
    raise AssertionError('Old F01 witness missing')

clock = {'time': 539.99, 'dump_delay': 0.0, 'open_delay': 0.0, 'write_delay': 0.0, 'partial': False}
events, payload, gates = [], bytearray(), []
metadata = types.SimpleNamespace(st_mode=stat.S_IFREG | 0o600, st_uid=1000, st_gid=1000, st_nlink=1)


def reset(**changes):
    clock.update(time=539.99, dump_delay=0.0, open_delay=0.0, write_delay=0.0, partial=False)
    clock.update(changes)
    events.clear(); payload.clear(); gates.clear()


def gate(**kwargs):
    gates.append(kwargs)
    if clock['time'] >= 540:
        raise RuntimeError('RAM_CLEANUP_BOUNDARY')


def dump(value, indent):
    clock['time'] += clock['dump_delay']
    return json.dumps(value, indent=indent)


def opened(*args, **kwargs):
    events.append(('open', clock['time']))
    clock['time'] += clock['open_delay']
    return 7


def written(fd, block):
    count = min(3, len(block)) if clock['partial'] else len(block)
    events.append(('write', clock['time']))
    payload.extend(block[:count])
    clock['time'] += clock['write_delay']
    return count


fake_os = types.SimpleNamespace(**{key: getattr(os, key) for key in
    ('O_WRONLY', 'O_CREAT', 'O_NOFOLLOW', 'O_CLOEXEC', 'O_EXCL')},
    open=opened, fstat=lambda fd: metadata, write=written,
    ftruncate=lambda *args: events.append(('truncate', clock['time'])),
    fsync=lambda *args: events.append(('fsync', clock['time'])),
    close=lambda *args: events.append(('raw_close', clock['time'])))
save_class = extract(NEW, 'refresh-boundary.py', ['Namespace'],
    {'os': fake_os, 'json': types.SimpleNamespace(dumps=dump), 'stat': stat, 're': re})['Namespace']


def receipt(new=True, callback=gate):
    instance = save_class.__new__(save_class)
    instance.fds = [7]; instance.check = lambda: None; instance.before_mutation = gate
    instance.save('stage-result.safe.json', {'public': True}, new=new, before_effect=callback)


def timely_effects():
    assert all(when < 540 for kind, when in events if kind != 'raw_close')


reset(partial=True)
receipt()
assert payload == (json.dumps({'public': True}, indent=2) + '\n').encode()
assert len(gates) == len(events) - 1 and all(0 < row['effect_bytes'] <= 1048576 for row in gates)
passed('F02_receipt_complete_partial_write_positive_exact_encoding_and_per_effect_charges')
reset()
receipt(new=False)
assert [e[0] for e in events] == ['open', 'truncate', 'write', 'fsync', 'raw_close']
assert len(gates) == 4
passed('F02_optional_truncate_positive_separate_gate')
for label, changes in [('serialization', {'dump_delay': 0.02}), ('create', {'open_delay': 0.02}),
                        ('partial_write', {'partial': True, 'write_delay': 0.02})]:
    reset(**changes)
    denied('F02_receipt_' + label + '_expiry', receipt, 'RAM_CLEANUP_BOUNDARY')
    timely_effects()
    if label == 'serialization': assert events == []
    elif label == 'create': assert not payload
    else: assert len(payload) == 3
reset(open_delay=0.02)
denied('F02_optional_truncate_expiry', lambda: receipt(new=False), 'RAM_CLEANUP_BOUNDARY')
assert not any(kind == 'truncate' for kind, when in events)
reset(write_delay=0.02)
denied('F02_fsync_expiry', receipt, 'RAM_CLEANUP_BOUNDARY')
assert not any(kind == 'fsync' for kind, when in events)


class Raw(io.BytesIO):
    def fileno(self): return 7
    def write(self, data):
        events.append(('old_buffered_write', clock['time']))
        return super().write(data)


class Stream:
    def __init__(self, late=False, fail=False): self.calls = 0; self.late = late; self.fail = fail
    def read(self, size):
        self.calls += 1
        if self.calls == 1: return b'x' * 10
        if self.late: clock['time'] += 0.02
        if self.fail: raise RuntimeError('RAM_READ_FAILURE')
        return b''


fake_os.fdopen = lambda *args: io.BufferedWriter(Raw())


def copy_file(root, stream):
    cls = extract(root, 'stage-app-writer.py', ['AppWriter'], {'os': fake_os, 'stat': stat, 'Path': Path})['AppWriter']
    w = cls.__new__(cls); w.directory = lambda rel: 7; w.check = lambda rel: None
    w.meta = 65536; w.write_upper = lambda n: ((n + 4095) // 4096) * 4096 + 65536
    w.use = gate; w.copied_files = 0
    w.copy({'path': 'public.class', 'bytes': 10}, stream)
    assert w.copied_files == 1


metadata.st_mode = stat.S_IFREG | 0o644
reset()
denied('F02_original_real_RAM_BufferedWriter_late_close_witness', lambda: copy_file(OLD, Stream(late=True)), 'RAM_CLEANUP_BOUNDARY')
assert any(kind == 'old_buffered_write' and when >= 540 for kind, when in events)
reset(partial=True)
copy_file(NEW, Stream())
assert payload == b'x' * 10 and len(gates) == len(events) - 1
passed('F02_corrected_complete_app_partial_writes_positive_no_implicit_flush')
for label, changes, stream, expected_error in [
    ('late_terminal_read', {}, Stream(late=True), 'RAM_CLEANUP_BOUNDARY'),
    ('read_failure', {}, Stream(late=True, fail=True), 'RAM_READ_FAILURE'),
    ('partial_write', {'partial': True, 'write_delay': 0.02}, Stream(), 'RAM_CLEANUP_BOUNDARY')]:
    reset(**changes)
    denied('F02_corrected_app_' + label + '_no_close_write', lambda: copy_file(NEW, stream), expected_error)
    timely_effects()
    assert not any(kind == 'old_buffered_write' for kind, when in events)
    assert events[-1][0] == 'raw_close'

result = {'scope': 'NARROW_INDEPENDENT_F01_F02_SOURCE_RAM_RECHECK', 'groups': groups,
          'old_findings_witnesses_reused_exact_source': True, 'native_stage_runtime': 'NOT_RUN',
          'actual_prefix_JAR_private_reads': False, 'original43_or23_suite_rerun': False,
          'target_module_top_level_or_main': False, 'actual_IO_effects_outside_new_review': 0}
(HERE / 'checks.safe.json').write_text(json.dumps(result, indent=2) + '\n')
print(json.dumps({'independent_narrow_RAM_groups': len(groups), 'F01': 'CLOSED_SOURCE', 'F02': 'CLOSED_SOURCE',
                  'native_stage': 'NOT_RUN', 'actual_prefix_scan': False}))
