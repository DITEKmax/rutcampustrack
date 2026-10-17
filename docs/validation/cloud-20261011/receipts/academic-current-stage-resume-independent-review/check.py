"""Independent selected-source RAM checks. Never scan the actual staging prefix."""
import ast
import copy
import hashlib
import io
import json
import os
from pathlib import Path
import re
import stat
import types
import zipfile

OWN = Path(__file__).parent
SOURCE = Path('/workspace/scratch/rct-validation/current_academic_stage_resume_prepare')
RECEIPTS = Path('/workspace/rutcampustrack/docs/validation/cloud-20261011/receipts')
results = []


def selected(name, definitions, namespace):
    tree = ast.parse((SOURCE / name).read_text())
    nodes = [node for node in tree.body if isinstance(node, (ast.FunctionDef, ast.ClassDef))
             and node.name in definitions]
    assert {node.name for node in nodes} == set(definitions)
    exec(compile(ast.Module(body=nodes, type_ignores=[]), name, 'exec'), namespace)
    return namespace


def passed(name, **facts):
    results.append(dict(name=name, outcome='PASS_SOURCE_RAM', **facts))


def deny(name, action, code=None):
    try:
        action()
    except RuntimeError as error:
        if code is not None:
            assert getattr(error, 'code', None) == code
        passed(name, denial_code=code)
    else:
        raise AssertionError(name)


manifest = json.loads((SOURCE / 'manifest.json').read_text())
with zipfile.ZipFile(SOURCE / 'source-package.safe.zip') as archive:
    assert archive.testzip() is None
    assert set(archive.namelist()) == {row['path'] for row in manifest['entries']} | {'manifest.json'}
    for row in manifest['entries']:
        data = (SOURCE / row['path']).read_bytes()
        assert archive.read(row['path']) == data
        assert len(data) == row['bytes'] and hashlib.sha256(data).hexdigest() == row['sha256']
        assert stat.S_IMODE((SOURCE / row['path']).lstat().st_mode) == row['mode']
passed('exact_25_members_plus_inner_manifest_CRC_bytes_modes')

with zipfile.ZipFile(RECEIPTS / 'academic-current-build-actual/actual-academic-build01.safe.zip') as archive:
    built_data = archive.read('build-result.safe.json')
    assert hashlib.sha256(built_data).hexdigest() == '08dd08de01d3c4fe5c0ebcea7cd53e22c41f36fb4d65e94b8d216b8aacda80ac'
    built = json.loads(built_data)
with zipfile.ZipFile(RECEIPTS / 'academic-current-stage-actual-partial/actual-academic-stage01-partial.safe.zip') as archive:
    partial_data = archive.read('partial-payload.safe.json')
    assert hashlib.sha256(partial_data).hexdigest() == '7369bebaef675999e2d112b039e107a290a79dd5d75b34c3b3fba86912480fc7'
    partial = json.loads(partial_data)
partial_ns = selected('partial-resume.py', ['path_name', 'validate_document', 'same_directory', 'same_regular'],
                      {'PurePosixPath': __import__('pathlib').PurePosixPath,
                       'stat': stat, 'JAR_SHA': built['jar_sha256']})
files, directories = partial_ns['validate_document'](partial, built, Path(partial['root']))
missing = [row for row in built['regular_entries'] if row['path'] not in files]
assert len(files) == 894 and len(missing) == 903 and len(directories) == 40
passed('sealed_build_partial_document_positive_894_903_no_actual_prefix_read')
for field, value in [('sha256', '0' * 64), ('mode', 0o600), ('nlink', 2), ('uid', 0), ('path', '../outside')]:
    changed = copy.deepcopy(partial)
    changed['files'][0][field] = value
    deny('partial_document_' + field, lambda: partial_ns['validate_document'](changed, built, Path(partial['root'])))

grant = {'all_new_tmp_cap': 1610612736, 'workspace_floor': 4294967296,
         'tmp_floor_plus_mongo_growth': 5368709120, 'shared_stop': 16106127360,
         'shared_reserve': 1073741824, 'workspace_cache_growth_cap': 8388608}
good = {'workspace_free': 9400000000, 'tmp_free': 7600000000,
        'shared': 4800000000, 'maximum': 17179869184}
clock = {'now': 0.0, 'scan_delay': 0.0, 'cheap_delay': 0.0, 'bytes': 140000000,
         'deadline': 540.0, 'resource_after_scan': False}
resource_ns = selected('refresh-boundary.py', ['require_resources'], {})
monitor_ns = {'time': types.SimpleNamespace(monotonic=lambda: clock['now']),
              'INTERVAL': 1.0, 'EFFECTS': 64, 'ONE_EFFECT_MAX': 1048576,
              'HEADROOM': 67108864, 'METADATA_ALLOWANCE': 65536}
selected('stage-monitor.py', ['StageStop', 'Monitor', 'write_upper'], monitor_ns)


def scan(path):
    clock['now'] += clock['scan_delay']
    return clock['bytes'], {}


def cheap():
    clock['now'] += clock['cheap_delay']
    return dict(good, workspace_free=0) if clock['resource_after_scan'] else dict(good)


def fresh(stop):
    if clock['now'] >= clock['deadline']:
        raise RuntimeError('Public synthetic cleanup boundary reached')


fake_boundary = types.SimpleNamespace(PREFIX=Path('/RAM/stage'), cheap_resources=cheap,
    require_resources=resource_ns['require_resources'], fresh=fresh)


def monitor():
    clock.update(now=0.0, scan_delay=0.0, cheap_delay=0.0, bytes=140000000,
                 deadline=540.0, resource_after_scan=False)
    return monitor_ns['Monitor'](fake_boundary, grant, None, good['workspace_free'], inventory_reader=scan)


mon = monitor()
mon.gate(force=True, entry=True)
for _ in range(64):
    mon.gate(effect_bytes=65536)
assert mon.inventory_count == 1 and mon.effects == 64
mon.gate(effect_bytes=65536)
assert mon.inventory_count == 2 and mon.effects == 1
clock['now'] += 1.0
mon.gate(effect_bytes=65536)
assert mon.inventory_count == 3
mon.gate(force=True)
assert mon.inventory_count == 4
passed('64_effects_OR_one_second_force_and_final_force')
for name, changes, code in [
    ('clock_consumed_during_scan', {'scan_delay': 0.9, 'deadline': 0.8}, 'FRESH_WINDOW_STOP'),
    ('fresh_resource_after_scan', {'scan_delay': 0.5, 'resource_after_scan': True}, 'CHEAP_RESOURCE_OR_ALLOC_CAP'),
    ('slow_scan', {'scan_delay': 1.01}, 'ALLOCATION_SAMPLE_STALE'),
    ('slow_cheap', {'cheap_delay': 1.01}, 'ALLOCATION_SAMPLE_STALE'),
    ('entry_headroom', {'bytes': grant['all_new_tmp_cap'] - 67108864 + 1}, 'OWN_SAMPLE_HEADROOM'),
]:
    mon = monitor()
    clock.update(changes)
    deny(name, lambda: mon.gate(entry=True, force=True, effect_bytes=65536), code)
    assert mon.effects == 0
mon = monitor()
mon.gate(force=True)
clock['bytes'] = grant['all_new_tmp_cap'] + 1
deny('forced_final_over_cap', lambda: mon.gate(force=True), 'CHEAP_RESOURCE_OR_ALLOC_CAP')
record = {'kind': 'regular', 'device': 33, 'inode': 17, 'uid': 1000, 'gid': 1000,
          'mode': 0o644, 'nlink': 1, 'bytes': 10, 'blocks': 4096, 'mtime_ns': 5, 'ctime_ns': 5}
mon.base_records = {'app/existing.class': dict(record), 'checkout/JAR': dict(record)}
mon.configure([{'path': 'new.class', 'bytes': 20}], set())
mon.reconcile(copy.deepcopy(mon.base_records))
for name, code in [('mutable_old', 'PROTECTED_PREFIX_NODE_CHANGED'),
                   ('missing_old', 'PROTECTED_PREFIX_NODE_MISSING'),
                   ('foreign_outside_app', 'FOREIGN_PREFIX_ADDITION'),
                   ('new_alias', 'NEW_MEMBER_BOUND'), ('new_oversize', 'NEW_MEMBER_BOUND')]:
    rows = copy.deepcopy(mon.base_records)
    if name == 'mutable_old':
        rows['app/existing.class']['ctime_ns'] += 1
    elif name == 'missing_old':
        del rows['checkout/JAR']
    elif name == 'foreign_outside_app':
        rows['outside.bin'] = dict(record)
    elif name == 'new_alias':
        rows['app/new.class'] = dict(record, nlink=2)
    else:
        rows['app/new.class'] = dict(record, bytes=21)
    deny(name, lambda: mon.reconcile(rows), code)

# F01: actual consumer uses 'files', sealed manifest uses relative 'entries'.
package_ns = selected('refresh-boundary.py', ['verify_package'],
    {'OWN': SOURCE, 'json': json, 'Path': Path,
     'sha': lambda path: hashlib.sha256(Path(path).read_bytes()).hexdigest()})
try:
    package_ns['verify_package']({'academic_refresh_manifest_sha256':
        hashlib.sha256((SOURCE / 'manifest.json').read_bytes()).hexdigest()}, SOURCE / 'stage-academic.py')
except KeyError as error:
    assert error.args == ('files',)
    passed('F01_exact_manifest_consumer_keyerror_before_namespace', exception_key='files')
else:
    raise AssertionError('F01 counterexample did not reproduce')

# F02a: actual Namespace.save, fake descriptors/streams, slow JSON serialization.
effects = []
clock.update(now=539.99, deadline=540.0)
metadata = types.SimpleNamespace(st_mode=stat.S_IFREG | 0o600, st_uid=1000, st_gid=1000, st_nlink=1)


class ReceiptStream:
    def __enter__(self):
        return self
    def write(self, value):
        effects.append(('write', clock['now']))
    def __exit__(self, *args):
        effects.append(('implicit_flush_close', clock['now']))


def delayed_dumps(value, indent):
    clock['now'] += 0.02
    return '{}'


fake_os = types.SimpleNamespace(O_WRONLY=os.O_WRONLY, O_CREAT=os.O_CREAT,
    O_NOFOLLOW=os.O_NOFOLLOW, O_CLOEXEC=os.O_CLOEXEC, O_EXCL=os.O_EXCL,
    getuid=lambda: 1000, open=lambda *args, **kwargs: 7,
    fstat=lambda fd: metadata, ftruncate=lambda *args: effects.append(('truncate', clock['now'])),
    fdopen=lambda *args: ReceiptStream(), close=lambda fd: None)
save_class = selected('refresh-boundary.py', ['Namespace'],
    {'os': fake_os, 'stat': stat, 're': re, 'json': types.SimpleNamespace(dumps=delayed_dumps)})['Namespace']
ns = save_class.__new__(save_class)
ns.fds = [7]
ns.check = lambda: None
fresh(None)  # Same single external use-time gate as stage-academic.py.
ns.save('stage-result.safe.json', {}, new=True)
assert any(kind == 'write' and when >= 540.0 for kind, when in effects)
passed('F02_receipt_write_and_implicit_flush_after_cleanup_boundary',
       external_gate_before_boundary=True, write_after_boundary=True, actual_file_effects=0)

# F02b: exact AppWriter.copy with a REAL RAM BufferedWriter closes pending bytes
# after its explicit flush gate denies. No real OS descriptor is opened.
raw_events = []


class Raw(io.BytesIO):
    def fileno(self):
        return 7
    def write(self, value):
        raw_events.append((len(value), clock['now']))
        return super().write(value)


class SmallStream:
    calls = 0
    def read(self, size):
        self.calls += 1
        if self.calls == 1:
            return b'x' * 10
        clock['now'] += 0.02
        return b''


clock.update(now=539.99, deadline=540.0)
metadata.st_mode = stat.S_IFREG | 0o644
writer_os = types.SimpleNamespace(O_WRONLY=os.O_WRONLY, O_CREAT=os.O_CREAT,
    O_EXCL=os.O_EXCL, O_NOFOLLOW=os.O_NOFOLLOW, open=lambda *args, **kwargs: 7,
    fstat=lambda fd: metadata, fdopen=lambda *args: io.BufferedWriter(Raw()), fsync=lambda fd: None)
writer_class = selected('stage-app-writer.py', ['AppWriter'],
                       {'os': writer_os, 'stat': stat, 'Path': Path})['AppWriter']
writer = writer_class.__new__(writer_class)
writer.directory = lambda relative: 7
writer.check = lambda relative: None
writer.meta = 65536
writer.write_upper = monitor_ns['write_upper']
writer.use = lambda **kwargs: fresh(None)
writer.copied_files = 0
try:
    writer.copy({'path': 'public.class', 'bytes': 10}, SmallStream())
except RuntimeError:
    assert raw_events == [(10, 540.01)]
    passed('F02_actual_RAM_BufferedWriter_implicit_failure_close_writes_after_denial',
           explicit_flush_gate_denied=True, pending_bytes_flushed_after_boundary=True, actual_file_effects=0)
else:
    raise AssertionError('F02 buffered failure counterexample did not reproduce')

# All903 selected actual loop calls; exact894 excluded. No real ZIP payload read.
tree = ast.parse((SOURCE / 'stage-academic.py').read_text())
main = next(n for n in tree.body if isinstance(n, ast.FunctionDef) and n.name == 'main')
loop = next(n for n in ast.walk(main) if isinstance(n, ast.For) and isinstance(n.iter, ast.Name) and n.iter.id == 'missing')
calls = []
exec(compile(ast.Module(body=[loop], type_ignores=[]), 'actual903_selected_loop', 'exec'),
    {'missing': missing, 'archive': types.SimpleNamespace(open=lambda name: io.BytesIO()),
     'writer': types.SimpleNamespace(copy=lambda row, stream: calls.append(row['path']))})
assert len(calls) == 903 and len(set(calls)) == 903 and not set(calls) & set(files)
passed('whole903_selected_loop_positive_excludes_all894_mocked_writer_only')

output = {'scope': 'INDEPENDENT_SELECTED_SOURCE_RAM_ONLY', 'controls': results,
          'native_stage_runtime': 'NOT_RUN', 'actual_prefix_scan': False,
          'private_ENV_key_data_body_reads': False, 'source_import_main': False,
          'actual_file_effects_outside_own_review': 0,
          'limits': 'Source/SAFE metadata and selected RAM adapters only; not native transport/capacity/timing or complete artifact proof.'}
(OWN / 'checks.safe.json').write_text(json.dumps(output, indent=2) + '\n')
print(json.dumps({'selected_RAM_groups': len(results), 'findings_reproduced': ['F01', 'F02a', 'F02b'],
                  'actual_prefix_scan': False, 'native_stage': 'NOT_RUN'}))
