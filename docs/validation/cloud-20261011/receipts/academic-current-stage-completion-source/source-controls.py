"""Pure selected-source controls; no child, checkout, payload, ENV or key read."""
import copy
import datetime
import hashlib
import json
from pathlib import Path
import stat
import types
from unittest.mock import patch

OWN = Path(__file__).parent


def module(name):
    p = OWN / name
    m = types.ModuleType(name)
    m.__file__ = str(p)
    exec(compile(p.read_bytes(), str(p), 'exec'), m.__dict__)
    return m


b = module('refresh-boundary.py')
build = module('build-academic.py')
stage = module('stage-academic.py')
inspector = module('inspect-academic.py')
passed = []


def rejects(label, call):
    try:
        call()
    except (RuntimeError, ValueError):
        passed.append(label)
    else:
        raise AssertionError(label)


now = datetime.datetime(2026, 10, 12, 20, 0, tzinfo=datetime.timezone.utc)
grant = {'academic_refresh_phase': 'BUILD', 'execution_actor_uid': 1000,
         'execution_actor_gid': 1000, 'new_tmp_prefix': str(b.PREFIX),
         'all_new_tmp_cap': 1610612736, 'workspace_floor': 4294967296,
         'tmp_floor_plus_mongo_growth': 5368709120, 'shared_stop': 16106127360,
         'shared_reserve': 1073741824, 'workspace_cache_growth_cap': 8388608,
         'product_start_authorized': False, 'tests_authorized': False,
         'frozen_source_revision': b.FIX_REVISION, 'fixed_UserSpecifications_sha256': b.FIX_SHA,
         'heavy_leases_released': True, 'not_before_utc': now.isoformat(),
         'stop_utc': (now + datetime.timedelta(seconds=900)).isoformat()}
assert b.validate_fields(grant, 'BUILD', now) == now + datetime.timedelta(seconds=900)
passed.append('exact_BUILD_grant_positive')
for key, wrong in [('academic_refresh_phase', 'STAGE'), ('execution_actor_uid', 0),
                   ('execution_actor_gid', 0), ('new_tmp_prefix', '/tmp/foreign'),
                   ('all_new_tmp_cap', 1610612737), ('workspace_floor', 0),
                   ('tmp_floor_plus_mongo_growth', 4294967296), ('shared_stop', 17179869184),
                   ('shared_reserve', 0), ('workspace_cache_growth_cap', 16777216),
                   ('product_start_authorized', True), ('tests_authorized', True),
                   ('heavy_leases_released', False), ('fixed_UserSpecifications_sha256', '0' * 64),
                   ('frozen_source_revision', b.BAD_REVISION), ('frozen_source_revision', 'HEAD')]:
    bad = dict(grant, **{key: wrong})
    rejects('deny_' + key + '_' + str(wrong), lambda bad=bad: b.validate_fields(bad, 'BUILD', now))
rejects('expired_at_entry', lambda: b.validate_fields(grant, 'BUILD', now + datetime.timedelta(seconds=901)))

resources = {'workspace_free': 9400000000, 'tmp_free': 7750000000,
             'shared': 4800000000, 'maximum': 17179869184, 'new_tmp_allocated': 0}
assert b.require_resources(grant, resources['workspace_free'], resources) == resources
passed.append('resource_positive')
for key, bad_value in [('workspace_free', 4294967295), ('tmp_free', 5368709119),
                       ('shared', 16106127360), ('maximum', 4800000001),
                       ('new_tmp_allocated', 1610612737)]:
    bad = dict(resources, **{key: bad_value})
    rejects('resource_' + key, lambda bad=bad: b.require_resources(grant, resources['workspace_free'], bad))
rejects('cache_growth_cap', lambda: b.require_resources(grant, resources['workspace_free'] + 8388609, resources))

# Resource collection may consume the rest of the window: exact selected gate rejects AFTER it.
stop = now + datetime.timedelta(seconds=900)
clock = {'now': stop - datetime.timedelta(seconds=61)}
fake_datetime = types.SimpleNamespace(datetime=types.SimpleNamespace(now=lambda zone: clock['now']),
                                     timezone=datetime.timezone, timedelta=datetime.timedelta)
def delayed_resources(*args):
    clock['now'] += datetime.timedelta(seconds=2)
    return resources
with patch.object(b, 'datetime', fake_datetime), patch.object(b, 'require_resources', delayed_resources):
    rejects('expiry_DURING_resource_preflight_before_mutation', lambda: b.gate(grant, stop, 9400000000))

anchors = json.loads((OWN / 'tmp-anchors.safe.json').read_text())['anchors']
records = {r['path']: r['identity'] for r in anchors}
records[str(b.PREFIX)] = {'device': 33, 'inode': 77, 'uid': 1000, 'gid': 1000, 'mode': 0o700}


def stats(row, symlink=False):
    return types.SimpleNamespace(st_dev=row['device'], st_ino=row['inode'], st_uid=row['uid'],
                                 st_gid=row['gid'], st_mode=(stat.S_IFLNK if symlink else stat.S_IFDIR) | row['mode'])


def namespace_control(label, altered=None, expire=False, positive=False):
    rows = copy.deepcopy(records)
    symlinks = set()
    if altered:
        path, key, value = altered
        if key == 'symlink':
            symlinks.add(path)
        else:
            rows[path][key] = value
    fds = {}
    calls = []
    def opening(name, flags, dir_fd=None):
        path = name if dir_fd is None else str(Path(fds[dir_fd]) / name)
        fd = 100 + len(fds)
        fds[fd] = path
        return fd
    def before():
        if expire:
            raise RuntimeError('Synthetic window expired during metadata preflight')
    with patch.object(b.os, 'getuid', lambda: 1000), patch.object(b.os, 'getgid', lambda: 1000), \
         patch.object(Path, 'lstat', lambda p: stats(rows[str(p)], str(p) in symlinks)), \
         patch.object(b.os, 'open', opening), \
         patch.object(b.os, 'fstat', lambda fd: stats(rows[fds[fd]])), \
         patch.object(b.os, 'stat', lambda name, dir_fd=None, follow_symlinks=True: stats(rows[str(Path(fds[dir_fd]) / name)])), \
         patch.object(b.os, 'mkdir', lambda *a, **k: calls.append(a)), patch.object(b.os, 'close', lambda fd: None):
        if positive:
            ns = b.Namespace(before, create=True)
            assert len(calls) == 1
            ns.close()
            passed.append(label)
        else:
            rejects(label, lambda: b.Namespace(before, create=expire))
            assert calls == []


namespace_control('exact_native_actor_root0_namespace_positive', positive=True)
for altered in [('/', 'uid', 65534), ('/', 'device', 999), ('/', 'inode', 999),
                ('/tmp', 'uid', 1000), ('/tmp', 'symlink', True),
                (str(b.PREFIX), 'uid', 999), (str(b.PREFIX), 'mode', 0o755),
                (str(b.PREFIX), 'symlink', True)]:
    namespace_control('namespace_reject_' + str(altered), altered=altered)
namespace_control('expiry_DURING_namespace_preflight_NO_mkdir', expire=True)

specs = json.loads(stage.BASE_SPECS.read_text())
new = stage.rebind_specs(specs, b.PREFIX / 'app', ['BOOT-INF/lib/current.jar'])
assert [r for r in new if r['service'] != 'academic'] == [r for r in specs if r['service'] != 'academic']
passed.append('exact_six_other_host_specs_preserved')
assert next(r for r in new if r['service'] == 'academic')['private_environment_file'] == next(r for r in specs if r['service'] == 'academic')['private_environment_file']
passed.append('same_private_ENV_pointer_without_read')
for bad_libs in [[], ['BOOT-INF/lib/a.jar', 'BOOT-INF/lib/a.jar'], ['../foreign.jar'], ['/foreign.jar'], ['BOOT-INF/lib/sub/a.jar']]:
    rejects('library_order_deny_' + str(bad_libs), lambda bad_libs=bad_libs: stage.rebind_specs(specs, b.PREFIX / 'app', bad_libs))
bad_specs = copy.deepcopy(specs)
next(r for r in bad_specs if r['service'] == 'academic')['cwd'] = '/foreign'
rejects('old_Academic_cwd_mismatch', lambda: stage.rebind_specs(bad_specs, b.PREFIX / 'app', ['BOOT-INF/lib/current.jar']))
for name in ['../evil', '/evil', 'a//b', 'a/../b', 'a\\b', 'a:b', '']:
    rejects('zip_path_deny_' + repr(name), lambda name=name: inspector.member(name))
assert str(inspector.member('BOOT-INF/classes/git.properties')) == 'BOOT-INF/classes/git.properties'
passed.append('zip_regular_positive')
for path in ['docs/private note.md', '.agent/evidence/raw.json', 'frontends/web-panel/a.ts', 'tests/AcademicGrpcIT.java']:
    assert not b.selected(path)
    passed.append('source_excludes_' + path)
for path in [b.FIX_PATH, 'proto/academic.proto', 'gradle/wrapper/gradle-wrapper.jar', 'services/auth-service/auth-app/build.gradle.kts']:
    assert b.selected(path)
    passed.append('source_includes_' + path)
rejects('selected_source_traversal', lambda: b.selected('proto/../foreign'))

# Selected build tree → exact source hash validation: old4f62 cannot reach namespace/build.
fixed = Path('/workspace/rct-worktrees/enum-filter-source-only') / b.FIX_PATH
fixed_bytes = fixed.read_bytes()
tree = ('100644 blob ' + 'a' * 40 + ' ' + str(len(fixed_bytes)) + '\t' + b.FIX_PATH + '\0').encode()
with patch.object(build, 'git_read', lambda *args: tree if args[0] == 'ls-tree' else fixed_bytes):
    assert build.tracked_inputs('a' * 40)[0]['path'] == b.FIX_PATH
    passed.append('selected_Git_tree_fixed_source_positive')
original_bytes = (Path('/workspace/rutcampustrack') / b.FIX_PATH).read_bytes()
if hashlib.sha256(original_bytes).hexdigest() != b.FIX_SHA:
    with patch.object(build, 'git_read', lambda *args: tree if args[0] == 'ls-tree' else original_bytes):
        rejects('selected_Git_tree_original4f62_rejected_before_namespace', lambda: build.tracked_inputs('a' * 40))
else:
    # Source may already be integrated while this pure control runs; known old source is retained in Git.
    with patch.object(build, 'git_read', lambda *args: tree if args[0] == 'ls-tree' else b'known old source'):
        rejects('selected_Git_tree_wrong_source_rejected_before_namespace', lambda: build.tracked_inputs('a' * 40))

result = {'scope': 'PURE_SELECTED_SOURCE_ONLY', 'passed': len(passed), 'controls': passed,
          'child_processes': 0, 'foreign_writes': 0, 'checkout_build_stage_runtime': 'NOT_RUN',
          'package_not_yet_independently_reviewed': True}
(OWN / 'source-controls.safe.json').write_text(json.dumps(result, indent=2) + '\n')
print(json.dumps({'pure_controls_passed': len(passed), 'native_build_stage_runtime': False}))
