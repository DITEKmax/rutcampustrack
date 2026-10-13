"""Prepared one-service foreground entry; blocked without Root's exact lease."""
import argparse
import errno
import hashlib
import types
import json
import os
from pathlib import Path
import stat

OWN = Path('/workspace/scratch/rct-validation/current_host_config_v5')
PLAN = Path('/workspace/scratch/rct-validation/current_host_restart_plan')
ANCESTOR_CORRECTION = Path('/workspace/scratch/rct-validation/current_host_restart_data157_docker_correction')
REFRESH = Path(__file__).parent
ORIGINAL_RUNNER = ANCESTOR_CORRECTION / 'run-service-restart.py'
GUARD = Path('/workspace/scratch/rct-validation/guard_proc_race_correction/bounded-child.py')
GUARD_SHA = 'b78c61293568d9941692e0b92ee34fd0b84db6d532b4a2c9a5ad42ccbf2204a0'


def digest(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def safe_owned(path, root):
    raw = str(path)
    path = Path(raw)
    if not path.is_absolute() or '..' in path.parts or str(path) != raw or root not in path.parents:
        raise ValueError('Exact own private path required')
    for parent in reversed(path.parents):
        if not stat.S_ISDIR(parent.lstat().st_mode):
            raise ValueError('Private path symlink ancestor denied')
    s = path.lstat()
    if not stat.S_ISREG(s.st_mode) or stat.S_IMODE(s.st_mode) != 0o600 or s.st_uid != os.getuid():
        raise ValueError('Private regular600 owner required')
    return path


def main():
    if os.getuid()!=1000 or os.getgid()!=1000:
        raise RuntimeError('Exact approved execution actor1000 required')
    parser = argparse.ArgumentParser()
    parser.add_argument('--service', choices=['academic'], required=True)
    parser.add_argument('--grant', required=True)
    args = parser.parse_args()
    grant = json.loads(safe_owned(args.grant, OWN / 'private').read_text())
    if Path(args.grant) != OWN / 'private/restart-launch-grant-next02.json':
        raise RuntimeError('NEW exact next02 refreshed stand grant required')
    # Pinned new helper bytes checked before helper execution (hash inserted at freeze).
    helper = REFRESH / 'runtime-binding.py'
    if digest(helper) != 'be1488b5995accb02212c36e4ce919fe2ddac61de50eeeb9d7d43d58571ecc24':
        raise RuntimeError('Exact reviewed Academic binding helper required')
    refreshed = types.ModuleType('academic_runtime_binding')
    refreshed.__file__ = str(helper)
    exec(compile(helper.read_bytes(), str(helper), 'exec'), refreshed.__dict__)
    specs = refreshed.verify(grant, __file__)
    if grant.get('launch_authorized') is not True or grant.get('reviewed_guard_sha256') != GUARD_SHA:
        raise RuntimeError('Root launch grant and fresh reviewed guard required')
    if grant.get('heavy_leases_released') != {'NP01_BROKER1': True, 'FORMAT_JAVA': True, 'FORMAT_NODE': True}:
        raise RuntimeError('Other heavy leases must be released')
    if digest(OWN / 'launch-manifest.json') != grant.get('config_manifest_sha256') or digest(GUARD) != GUARD_SHA:
        raise RuntimeError('Sealed exact configuration/guard identity required')
    if digest(grant['guard_review_manifest']) != grant['guard_review_manifest_sha256']:
        raise RuntimeError('Fresh guard review receipt identity required')
    if digest(grant['config_review_manifest']) != grant['config_review_manifest_sha256']:
        raise RuntimeError('Fresh independent v5 config review receipt required')
    manifest = json.loads((OWN / 'launch-manifest.json').read_text())
    for name in ['host-service-specs.json', 'run-service-v5.py']:
        row = next(r for r in manifest['files'] if Path(r['path']).name == name)
        if digest(OWN / name) != row['sha256']:
            raise RuntimeError('Launch input source identity changed')
    # Verify own executable and boundary bytes BEFORE executing the boundary module.
    restart_manifest_path = ANCESTOR_CORRECTION / 'manifest.json'
    if digest(restart_manifest_path) != grant.get('restart_ancestor_correction_manifest_sha256'):
        raise RuntimeError('New exact restart seal required before helper import')
    restart_manifest = json.loads(restart_manifest_path.read_text())
    for source in [ORIGINAL_RUNNER, ANCESTOR_CORRECTION / 'restart-boundary.py']:
        row = next((row for row in restart_manifest['files'] if row['path'] == str(source)), None)
        if row is None or digest(source) != row['sha256']:
            raise RuntimeError('Exact executing source and boundary required')
    boundary = types.ModuleType('restart_boundary')
    boundary.__file__ = str(ANCESTOR_CORRECTION / 'restart-boundary.py')
    exec(compile((ANCESTOR_CORRECTION / 'restart-boundary.py').read_bytes(), str(ANCESTOR_CORRECTION / 'restart-boundary.py'), 'exec'), boundary.__dict__)
    runtime, remaining = boundary.verify_restart(grant, str(ORIGINAL_RUNNER))
    cohort = boundary.CohortDirectory(runtime, create=False)
    if (runtime / (args.service + '-process.safe.json')).exists():
        raise RuntimeError('One service launch per new cohort only')
    spec = next(s for s in specs if s['service'] == args.service)
    tools = json.loads((OWN / 'tool-provenance.json').read_text())['results']
    java = next(t for t in tools if t['argv'][0] == spec['argv'][0])
    if digest(spec['argv'][0]) != java['binary_sha256']:
        raise RuntimeError('Measured Java executable changed')
    private_env = json.loads(safe_owned(spec['private_environment_file'], OWN / 'private').read_text())
    if set(private_env) != set(spec['required_private_names']) or not all(type(v) is str for v in private_env.values()):
        raise RuntimeError('Exact declared private environment names required')
    env = {'PATH': '/workspace/rct-cloud-setup/jdk21/bin:/usr/bin:/bin', 'LANG': 'C.UTF-8', 'LC_ALL': 'C.UTF-8',
           'HOME': str(runtime), 'JAVA_HOME': '/workspace/rct-cloud-setup/jdk21'}
    if set(private_env) & set(spec['public_environment']):
        raise RuntimeError('Ambiguous public/private environment denied')
    env.update(spec['public_environment'])
    env.update(private_env)
    guard = types.ModuleType('reviewed_owned_guard')
    guard.__file__ = str(GUARD)
    exec(compile(GUARD.read_bytes(), str(GUARD), 'exec'), guard.__dict__)
    latest = {}
    initial = int(Path('/sys/fs/cgroup/memory.current').read_text())

    def status(value):
        latest.clear()
        latest.update(value)
        cohort.write_json(args.service + '-process.safe.json', value)

    def observe():
        cohort.assert_current()
        try:
            refreshed.require_runtime_resources()
        except RuntimeError:
            return 'REFRESHED_ACADEMIC_RESOURCE_STOP'
        vfs = os.statvfs('/workspace')
        used = int(Path('/sys/fs/cgroup/memory.current').read_text())
        limit = int(Path('/sys/fs/cgroup/memory.max').read_text())
        if vfs.f_bavail * vfs.f_frsize < 4294967296:
            return 'DISK_FLOOR'
        if used >= 16106127360 or limit - used < 1073741824:
            return 'SHARED_MEMORY_STOP'
        table = guard.process_table()
        for row in latest.get('members_including_Z', []):
            current = table.get(row['pid'])
            if current is None or current['start_ticks'] != row['start_ticks']:
                continue
            try:
                lines = Path('/proc/' + str(row['pid']) + '/status').read_text().splitlines()
            except OSError as caught:
                if caught.errno in (errno.ENOENT, errno.ESRCH):
                    continue
                raise
            rss = next((int(line.split()[1]) * 1024 for line in lines if line.startswith('VmRSS:')), 0)
            if rss > spec['rss_stop_bytes']:
                return 'OWN_RSS_STOP'
        return None

    cohort.assert_current()
    refreshed.require_runtime_resources()
    # verify_restart's original exact data/config/guard chain remains authoritative.
    refreshed.boundary.fresh(__import__('datetime').datetime.fromisoformat(grant['stop_utc']))
    result = guard.run_owned(spec['argv'], spec['cwd'], env, deadline_seconds=boundary.validate_restart_fields(grant, boundary.datetime.datetime.now(boundary.datetime.timezone.utc)),
                             observe=observe, status=status)
    result['meaning'] = 'Process lifecycle only; planned deadline/stop is not product/client verdict.'
    result['initial_shared_memory'] = initial
    cohort.write_json(args.service + '-terminal.safe.json', result)
    print(json.dumps({'service': args.service, 'settled': result['settled'], 'native_exit': result['native_exit'],
                      'terminal_reason': result['terminal_reason'], 'product_client_acceptance': 'SEPARATE'}))
    planned_stop = result['terminal_reason'] in ('STOP_REQUEST', 'BOUNDED_DEADLINE')
    lifecycle_ok = result['settled'] and result['supervision_error_type'] is None and (planned_stop or result['phase_success'])
    cohort.close()
    return 0 if lifecycle_ok else 1


if __name__ == '__main__':
    raise SystemExit(main())
