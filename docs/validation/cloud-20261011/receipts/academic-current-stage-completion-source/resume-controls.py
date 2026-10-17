"""Selected T006/D011 pure controls; no actual copy, child, private ENV or key read."""
import ast
import copy
import hashlib
import io
import json
import os
from pathlib import Path
import stat
import types
from unittest.mock import patch

OWN = Path(__file__).parent


def module(path):
    m = types.ModuleType(path.name)
    m.__file__ = str(path)
    exec(compile(path.read_bytes(), str(path), 'exec'), m.__dict__)
    return m


b = module(OWN / 'refresh-boundary.py')
m = module(OWN / 'stage-monitor.py')
r = module(OWN / 'partial-resume.py')
passed = []


def reject(label, fn):
    try:
        fn()
    except (RuntimeError, ValueError, PermissionError):
        passed.append(label)
    else:
        raise AssertionError(label)


grant = {'all_new_tmp_cap': 1610612736, 'workspace_floor': 4294967296,
         'tmp_floor_plus_mongo_growth': 5368709120, 'shared_stop': 16106127360,
         'shared_reserve': 1073741824, 'workspace_cache_growth_cap': 8388608}
clock = {'time': 0.0, 'deadline': 600.0, 'allocation': 140000000, 'delay': 0.0,
         'cheap_delay': 0.0, 'resource_bad_after': None, 'cheap_calls': 0}
good = {'workspace_free': 9400000000, 'tmp_free': 7600000000,
        'shared': 4800000000, 'maximum': 17179869184}


def reset():
    clock.update(time=0.0, deadline=600.0, allocation=140000000, delay=0.0,
                 cheap_delay=0.0, resource_bad_after=None, cheap_calls=0)


def allocation(path):
    clock['time'] += clock['delay']
    return clock['allocation']


def cheap():
    clock['cheap_calls'] += 1
    clock['time'] += clock['cheap_delay']
    value = dict(good)
    if clock['resource_bad_after'] is not None and clock['time'] >= clock['resource_bad_after']:
        value['workspace_free'] = 0
    return value


def fresh(stop):
    if clock['time'] >= clock['deadline']:
        raise RuntimeError('Fresh clock denied')


fake = types.SimpleNamespace(PREFIX=b.PREFIX, allocated=allocation, cheap_resources=cheap,
                             require_resources=b.require_resources, fresh=fresh)
with patch.object(m.time, 'monotonic', lambda: clock['time']):
    monitor = m.Monitor(fake, grant, None, good['workspace_free'])
    monitor.gate(force=True, entry=True)
    for i in range(64):
        monitor.gate(effect_bytes=65536)
    assert monitor.inventory_count == 1 and monitor.effects == 64
    monitor.gate(effect_bytes=65536)
    assert monitor.inventory_count == 2 and monitor.effects == 1
    passed.append('64effects_then_FORCEbefore65th')
    clock['time'] += 1.0
    monitor.gate(effect_bytes=65536)
    assert monitor.inventory_count == 3
    passed.append('one_second_cadence_before_effect')
    before = monitor.inventory_count
    monitor.gate(force=True)
    assert monitor.inventory_count == before + 1
    passed.append('mandatory_final_force_even_without_due')
    reset()
    clock.update(delay=2.0, resource_bad_after=1.0)
    monitor = m.Monitor(fake, grant, None, good['workspace_free'])
    reject('delayed_inventory_then_FRESHcheap_DENYbeforewrite', lambda: monitor.gate(effect_bytes=65536))
    assert clock['cheap_calls'] == 1 and monitor.effects == 0
    reset()
    clock.update(delay=0.9, deadline=0.8)
    monitor = m.Monitor(fake, grant, None, good['workspace_free'])
    reject('inventory_consumes_window_UTC_LAST_DENYbeforewrite', lambda: monitor.gate(effect_bytes=65536))
    assert clock['cheap_calls'] == 1 and monitor.effects == 0
    reset()
    clock['cheap_delay'] = 1.1
    monitor = m.Monitor(fake, grant, None, good['workspace_free'])
    reject('slow_cheapcheck_stale_allocation_DENYbeforewrite', lambda: monitor.gate(effect_bytes=65536))
    reset()
    clock['allocation'] = grant['all_new_tmp_cap'] - m.HEADROOM + 1
    reject('entry64MiB_headroom_boundary', lambda: m.Monitor(fake, grant, None, good['workspace_free']).gate(force=True, entry=True))
    reset()
    clock['allocation'] = grant['all_new_tmp_cap'] - 500
    reject('conservative_next_write_exceeds_cap', lambda: m.Monitor(fake, grant, None, good['workspace_free']).gate(effect_bytes=65536))
    reset()
    monitor = m.Monitor(fake, grant, None, good['workspace_free'])
    monitor.gate(force=True)
    clock['allocation'] = grant['all_new_tmp_cap'] + 1
    reject('final_full_inventory_overcap_DENYcommit', lambda: monitor.gate(force=True))
    reset()
    def unreadable(path):
        raise PermissionError('Pure inventory failure')
    broken = types.SimpleNamespace(**dict(fake.__dict__, allocated=unreadable))
    reject('inventory_error_FAILCLOSED', lambda: m.Monitor(broken, grant, None, good['workspace_free']).gate(force=True))
    reset()
    for key, bad in [('workspace_free', 4294967295), ('tmp_free', 5368709119),
                     ('shared', 16106127360), ('maximum', 4800000001)]:
        resources = dict(good, **{key: bad})
        failing = types.SimpleNamespace(**dict(fake.__dict__, cheap_resources=lambda resources=resources: resources))
        reject('freshcheap_' + key, lambda failing=failing: m.Monitor(failing, grant, None, good['workspace_free']).gate(effect_bytes=65536))

# Actual old gate admits a stale resource value after delayed allocation; corrected gate above rejects.
old = module(OWN.parent / 'current_academic_refresh_pin_correction/refresh-boundary.py')
reset()
clock.update(delay=2.0, resource_bad_after=1.0)
class CounterPath:
    def __init__(self, path):
        self.path = str(path)
    def read_text(self):
        return str(good['shared'] if self.path.endswith('memory.current') else good['maximum'])
def vfs(path):
    value = 0 if clock['time'] >= 1.0 else good['workspace_free'] if path == '/workspace' else good['tmp_free']
    return types.SimpleNamespace(f_bavail=value, f_frsize=1)
with patch.object(old.os, 'statvfs', vfs), patch.object(old, 'Path', CounterPath), \
     patch.object(old, 'allocated', allocation), patch.object(old, 'fresh', fresh):
    old.gate(grant, None, good['workspace_free'])
assert clock['time'] == 2.0
passed.append('old_selected_full_gate_stale_resource_counterexample_ACCEPTS')

partial = json.loads(r.PARTIAL.read_text())
built = json.loads((b.PREFIX / 'build-result.safe.json').read_text())
files, dirs = r.validate_document(partial, built, b.PREFIX / 'app')
assert len(files) == 894
passed.append('exact894_actual_public_document_positive')
for key, bad in [('sha256', '0' * 64), ('bytes', -1), ('mode', 0o600),
                 ('nlink', 2), ('uid', 0), ('gid', 999), ('path', '../escape')]:
    doc = copy.deepcopy(partial)
    doc['files'][0][key] = bad
    reject('partial_' + key, lambda doc=doc: r.validate_document(doc, built, b.PREFIX / 'app'))
doc = copy.deepcopy(partial)
doc['files'].append(copy.deepcopy(doc['files'][0]))
reject('partial_duplicate_extra', lambda: r.validate_document(doc, built, b.PREFIX / 'app'))
doc = copy.deepcopy(partial)
doc['directories'][0]['mode'] = 0o777
reject('partial_wrong_directory', lambda: r.validate_document(doc, built, b.PREFIX / 'app'))
doc = copy.deepcopy(partial)
doc['directories'].append(dict(doc['directories'][0], path='foreign'))
reject('partial_unexpected_directory', lambda: r.validate_document(doc, built, b.PREFIX / 'app'))
binding = {'stage_resume_authorized': True, 'partial_stage_manifest': str(r.MANIFEST),
           'partial_stage_manifest_sha256': r.MANIFEST_SHA, 'partial_stage_receipt': str(r.PARTIAL),
           'partial_stage_receipt_sha256': r.PARTIAL_SHA, 'build_result_sha256': r.BUILD_SHA}
assert len(r.validate_binding(binding, b)['files']) == 894
passed.append('actual77partial08dd_binding_positive_readonly')
for key in ['partial_stage_manifest_sha256', 'partial_stage_receipt_sha256', 'build_result_sha256']:
    wrong = dict(binding, **{key: '0' * 64})
    reject('binding_' + key, lambda wrong=wrong: r.validate_binding(wrong, b))

# Actual readonly descriptor tree control on task-owned PUBLIC compiled partial files only.
prefix_fd = os.open(str(b.PREFIX), os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW)
prefix_identity = b.identity(os.fstat(prefix_fd))
def prefix_check():
    assert b.identity(b.PREFIX.lstat()) == prefix_identity
    assert b.identity(os.fstat(prefix_fd)) == prefix_identity
ns = types.SimpleNamespace(fds=[prefix_fd], check=prefix_check)
try:
    real_open = os.open
    def nofollow_open(name, flags, *args, **kwargs):
        assert flags & os.O_NOFOLLOW
        return real_open(name, flags, *args, **kwargs)
    with patch.object(r.os, 'open', nofollow_open):
        assert len(r.verify_tree(ns, b.PREFIX / 'app', files, dirs)) == 894
    passed.append('actual_readonly_FD_full894_hash_owner_mode_inventory_nofollow_positive')
    wrong_files = copy.deepcopy(files)
    first = next(iter(wrong_files))
    wrong_files[first]['sha256'] = '0' * 64
    reject('actual_FD_tree_wrong_hash_denied', lambda: r.verify_tree(ns, b.PREFIX / 'app', wrong_files, dirs))
    real_stat = os.stat
    selected_name = Path(first).name
    for case in ['alias', 'symlink', 'foreign_uid']:
        def altered_stat(name, *args, case=case, **kwargs):
            value = real_stat(name, *args, **kwargs)
            if name == selected_name:
                items = list(value)
                if case == 'alias':
                    items[3] = 2
                elif case == 'symlink':
                    items[0] = stat.S_IFLNK | 0o644
                else:
                    items[4] = 0
                return os.stat_result(items)
            return value
        with patch.object(r.os, 'stat', altered_stat):
            reject('selected_FD_tree_' + case + '_metadata_counterexample', lambda: r.verify_tree(ns, b.PREFIX / 'app', files, dirs))
    writer_module = module(OWN / 'stage-app-writer.py')
    no_effects = []
    writer = writer_module.AppWriter(ns, lambda **k: no_effects.append(k), b, dirs, 65536, m.write_upper)
    try:
        relative = next(name for name in dirs if name != '.')
        one = writer.directory(relative)
        count = len(writer.cache)
        two = writer.directory(relative)
        assert one == two and len(writer.cache) == count and no_effects == []
        passed.append('actual_readonly_existing_dirs_opened_once_no_mkdir_effect')
    finally:
        writer.close()
finally:
    os.close(prefix_fd)

# Whole selected missing-member loop uses only903; no writer is called for the existing894.
tree = ast.parse((OWN / 'stage-academic.py').read_text())
main = next(n for n in tree.body if isinstance(n, ast.FunctionDef) and n.name == 'main')
copy_loop = next(n for n in ast.walk(main) if isinstance(n, ast.For) and isinstance(n.iter, ast.Name) and n.iter.id == 'missing')
missing = [row for row in built['regular_entries'] if row['path'] not in set(files)]
calls = []
class Archive:
    def open(self, name):
        return io.BytesIO(b'')
writer = types.SimpleNamespace(copy=lambda row, stream: calls.append(row['path']))
exec(compile(ast.Module(body=[copy_loop], type_ignores=[]), 'selected_actual_copy_loop', 'exec'),
     {'missing': missing, 'archive': Archive(), 'writer': writer})
assert len(calls) == 903 and len(set(calls)) == 903 and not set(calls) & set(files)
passed.append('actual_full903_copy_loop_never_calls_writer_for894')

# Metadata reconciliation observes protected/foreign changes and stops, rather than trusting samples.
record = {'kind': 'regular', 'device': 33, 'inode': 17, 'uid': 1000, 'gid': 1000,
          'mode': 0o644, 'nlink': 1, 'bytes': 10, 'blocks': 4096, 'mtime_ns': 5, 'ctime_ns': 5}
monitor = m.Monitor(fake, grant, None, good['workspace_free'])
monitor.base_records = {'app/existing.class': dict(record), 'checkout/JAR': dict(record)}
monitor.configure([{'path': 'new.class', 'bytes': 20}], set())
monitor.reconcile(copy.deepcopy(monitor.base_records))
passed.append('protected_baseline_metadata_positive')
for change in ['old_hash_metadata', 'missing_old', 'foreign_extra', 'new_alias', 'new_oversize']:
    current = copy.deepcopy(monitor.base_records)
    if change == 'old_hash_metadata':
        current['app/existing.class']['ctime_ns'] += 1
    elif change == 'missing_old':
        del current['checkout/JAR']
    elif change == 'foreign_extra':
        current['foreign.bin'] = dict(record)
    elif change == 'new_alias':
        current['app/new.class'] = dict(record, nlink=2)
    else:
        current['app/new.class'] = dict(record, bytes=21)
    reject('reconcile_' + change, lambda current=current: monitor.reconcile(current))

result = {'scope': 'PURE_SELECTED_T006_D011_CONTROLS', 'passed': len(passed), 'controls': passed,
          'actual_copy_child_private_ENV_key_data': 'NONE', 'actual_original_partial_build_reads': 'Public safe metadata only',
          'old_stale_resource_acceptance_counterexample': True, 'corrected_rechecks_after_inventory': True,
          'actual903_source_loop_mocked_writer': True, 'native_stage_runtime': 'NOT_RUN'}
(OWN / 'resume-controls.safe.json').write_text(json.dumps(result, indent=2) + '\n')
print(json.dumps({'pure_selected_controls': len(passed), 'native_copy_or_child': False}))
