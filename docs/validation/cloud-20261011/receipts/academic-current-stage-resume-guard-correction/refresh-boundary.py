"""Exact Academic refresh inputs and finite grants; no operation on import."""
import datetime
import hashlib
import json
import os
from pathlib import Path
import re
import stat
import types

OWN = Path(__file__).parent
PREFIX = Path('/tmp/rct-academic-refresh-20261012-attempt01')
ROOT = Path('/workspace/rutcampustrack')
FIX_PATH = 'services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/user/UserSpecifications.java'
FIX_SHA = '49dbdf2833a6be5ac35c3b873570139d62b4b965ed52370b7543590c9a998e3b'
FIX_REVISION = '96c7aee0a114e978ce3ed2976127a2a59c1a69e3'
BAD_REVISION = '538af12e280bc0e34b62c99c8823a7bb552d6ea6'
GUARD = OWN.parent / 'guard_proc_race_correction/bounded-child.py'
GUARD_SHA = 'b78c61293568d9941692e0b92ee34fd0b84db6d532b4a2c9a5ad42ccbf2204a0'
WRAPPER = Path('/workspace/rct-cloud-setup/run-gradle.py')
JAVA = Path('/workspace/rct-cloud-setup/jdk21/bin/java')
TASK = ':services:academic-service:academic-app:bootJar'
BUILD_ARGV = ['/usr/bin/python3', str(WRAPPER), '--offline', '--no-daemon',
              '--no-parallel', '--max-workers=1', '--console=plain',
              '-Dorg.gradle.jvmargs=-Xmx768m -XX:MaxMetaspaceSize=256m',
              '--project-cache-dir', str(PREFIX / 'project-cache'), TASK]
MODULES = ['services/academic-service/academic-app',
           'services/academic-service/academic-api-contract'] + [
    'services/shared/' + name for name in
    ['shared-web', 'shared-web-api', 'shared-events', 'shared-logback',
     'shared-outbox', 'shared-security', 'shared-observability']]


def regular(path):
    raw = str(path)
    path = Path(raw)
    if not path.is_absolute() or str(path) != raw or '..' in path.parts:
        raise RuntimeError('Exact lexical input required')
    for p in reversed(path.parents):
        if not stat.S_ISDIR(p.lstat().st_mode):
            raise RuntimeError('Input symlink ancestor denied')
    if not stat.S_ISREG(path.lstat().st_mode):
        raise RuntimeError('Regular input required')
    return path


def sha(path):
    with regular(path).open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def load(path, name):
    module = types.ModuleType(name)
    module.__file__ = str(path)
    exec(compile(regular(path).read_bytes(), str(path), 'exec'), module.__dict__)
    return module


def selected(path):
    include = (path in ['build.gradle.kts', 'settings.gradle.kts', 'gradlew', 'gradlew.bat',
                     'tests/e2e/backend-clock/verify-jar-isolation.py']
            or path.startswith(('gradle/', 'proto/'))
            or (path.startswith('services/') and path.endswith('/build.gradle.kts'))
            or any(path.startswith(module + '/src/main/') for module in MODULES))
    if include and (not re.fullmatch(r'[A-Za-z0-9_.+/@-]+', path) or '..' in Path(path).parts):
        raise RuntimeError('Canonical tracked input required')
    return include


def validate_fields(g, phase, now):
    expected = {'academic_refresh_phase': phase, 'execution_actor_uid': 1000,
                'execution_actor_gid': 1000, 'new_tmp_prefix': str(PREFIX),
                'all_new_tmp_cap': 1610612736, 'workspace_floor': 4294967296,
                'tmp_floor_plus_mongo_growth': 5368709120,
                'shared_stop': 16106127360, 'shared_reserve': 1073741824,
                'workspace_cache_growth_cap': 8388608,
                'product_start_authorized': False, 'tests_authorized': False}
    if any(type(g.get(k)) is not type(v) or g[k] != v for k, v in expected.items()):
        raise RuntimeError('Exact BUILD/STAGE-only grant required')
    revision = g.get('frozen_source_revision', '')
    if revision != FIX_REVISION:
        raise RuntimeError('New reviewed fixed Root revision required')
    if g.get('fixed_UserSpecifications_sha256') != FIX_SHA or g.get('heavy_leases_released') is not True:
        raise RuntimeError('Fixed input and released heavy leases required')
    start = datetime.datetime.fromisoformat(g['not_before_utc'])
    stop = datetime.datetime.fromisoformat(g['stop_utc'])
    limit = 960 if phase == 'BUILD' else 600
    if (start.utcoffset() != datetime.timedelta(0) or stop.utcoffset() != datetime.timedelta(0)
            or not start <= now < stop or not 60 < (stop - start).total_seconds() <= limit):
        raise RuntimeError('Fresh finite UTC window and cleanup margin required')
    return stop


def verify_grant(path, phase, source):
    if os.getuid() != 1000 or os.getgid() != 1000:
        raise RuntimeError('Exact actor1000 required')
    path = regular(path)
    s = path.lstat()
    if (path != OWN / 'private' / (phase.lower() + '-grant.json')
            or s.st_uid != 1000 or s.st_gid != 1000 or s.st_nlink != 1
            or stat.S_IMODE(s.st_mode) != 0o600 or s.st_size > 65536):
        raise RuntimeError('Exact new own600 grant required')
    g = json.loads(path.read_text())
    stop = validate_fields(g, phase, datetime.datetime.now(datetime.timezone.utc))
    verify_package(g, source)
    return g, stop


def verify_package(g, source):
    if sha(OWN / 'manifest.json') != g.get('academic_refresh_manifest_sha256'):
        raise RuntimeError('Exact new refresh seal required')
    rows = json.loads((OWN / 'manifest.json').read_text())['files']
    if not any(row['path'] == str(Path(source).absolute()) for row in rows):
        raise RuntimeError('Actual executing source must be sealed')
    for row in rows:
        p = regular(row['path'])
        if p.parent != OWN or sha(p) != row['sha256']:
            raise RuntimeError('Refresh source identity changed')
    review = regular(g['academic_refresh_review_manifest'])
    if not review.is_relative_to(OWN.parent) or sha(review) != g['academic_refresh_review_manifest_sha256']:
        raise RuntimeError('Fresh independent source review required')
    for row in json.loads((OWN / 'tool-inputs.safe.json').read_text())['files']:
        if sha(row['path']) != row['sha256']:
            raise RuntimeError('Accepted wrapper/JDK/guard/source input changed')


def fresh(stop):
    if datetime.datetime.now(datetime.timezone.utc) >= stop - datetime.timedelta(seconds=60):
        raise RuntimeError('Fresh use-time gate and cleanup margin required')


def allocated(path):
    seen = set()
    total = 0
    if not path.exists():
        return 0
    for parent, dirs, files in os.walk(path, followlinks=False):
        for p in [Path(parent)] + [Path(parent) / n for n in dirs + files]:
            s = p.lstat()
            if stat.S_ISLNK(s.st_mode):
                raise RuntimeError('Own allocation symlink denied')
            key = (s.st_dev, s.st_ino)
            if key not in seen:
                seen.add(key)
                total += s.st_blocks * 512
    return total


def resource_snapshot():
    def free(p):
        s = os.statvfs(p)
        return s.f_bavail * s.f_frsize
    return {'workspace_free': free('/workspace'), 'tmp_free': free('/tmp'),
            'shared': int(Path('/sys/fs/cgroup/memory.current').read_text()),
            'maximum': int(Path('/sys/fs/cgroup/memory.max').read_text()),
            'new_tmp_allocated': allocated(PREFIX)}


def cheap_resources():
    """STAGE-only fresh counters; expensive namespace allocation is sampled separately."""
    def free(p):
        s = os.statvfs(p)
        return s.f_bavail * s.f_frsize
    return {'workspace_free': free('/workspace'), 'tmp_free': free('/tmp'),
            'shared': int(Path('/sys/fs/cgroup/memory.current').read_text()),
            'maximum': int(Path('/sys/fs/cgroup/memory.max').read_text())}


def require_resources(g, baseline, now=None):
    r = resource_snapshot() if now is None else now
    if (r['workspace_free'] < g['workspace_floor']
            or r['tmp_free'] < g['tmp_floor_plus_mongo_growth']
            or r['shared'] >= g['shared_stop'] or r['maximum'] - r['shared'] < g['shared_reserve']
            or r['new_tmp_allocated'] > g['all_new_tmp_cap']
            or baseline - r['workspace_free'] > g['workspace_cache_growth_cap']):
        raise RuntimeError('Exact refresh resource limit')
    return r


def gate(g, stop, baseline):
    r = require_resources(g, baseline)
    fresh(stop)  # Resource collection precedes the last clock check.
    return r


def identity(s):
    if not stat.S_ISDIR(s.st_mode):
        raise RuntimeError('No-symlink directory required')
    return {'device': s.st_dev, 'inode': s.st_ino, 'uid': s.st_uid,
            'gid': s.st_gid, 'mode': stat.S_IMODE(s.st_mode)}


class Namespace:
    """Pinned root/tmp, exact new own leaf; no hard same-UID isolation claim."""
    def __init__(self, before_mutation, create=False):
        self.fds = []
        self.rows = []
        self.before_mutation = before_mutation
        flags = os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW | os.O_CLOEXEC
        try:
            rows = json.loads((OWN / 'tmp-anchors.safe.json').read_text())['anchors']
            if [r['path'] for r in rows] != ['/', '/tmp'] or os.getuid() != 1000 or os.getgid() != 1000:
                raise RuntimeError('Exact approved native actor/anchors required')
            for row in rows:
                p = Path(row['path'])
                if identity(p.lstat()) != row['identity']:
                    raise RuntimeError('Native-context anchor mismatch')
                fd = os.open(str(p) if not self.fds else p.name, flags,
                             dir_fd=None if not self.fds else self.fds[-1])
                self.fds.append(fd)
                self.rows.append((p, row['identity']))
                self.check()
            if create:
                self.check()
                before_mutation()
                os.mkdir(PREFIX.name, 0o700, dir_fd=self.fds[-1])
            s = os.stat(PREFIX.name, dir_fd=self.fds[-1], follow_symlinks=False)
            item = identity(s)
            if item['uid'] != 1000 or item['gid'] != 1000 or item['mode'] != 0o700:
                raise RuntimeError('Exact own700 prefix required')
            fd = os.open(PREFIX.name, flags, dir_fd=self.fds[-1])
            self.fds.append(fd)
            self.rows.append((PREFIX, item))
            self.check()
        except BaseException:
            self.close()
            raise

    def check(self):
        if len(self.fds) != len(self.rows):
            raise RuntimeError('Ambiguous namespace descriptors')
        for fd, (p, expected) in zip(self.fds, self.rows):
            if identity(p.lstat()) != expected or identity(os.fstat(fd)) != expected:
                raise RuntimeError('Pinned namespace switched')

    def save(self, name, value, new=False, before_effect=None):
        if not re.fullmatch(r'[a-z0-9-]+\.safe\.json', name):
            raise RuntimeError('Closed public receipt filename required')
        # Prepare all serialization BEFORE the fresh effect gate; no buffered I/O.
        encoded = (json.dumps(value, indent=2) + '\n').encode('utf8')
        def ready(cost):
            self.check()
            if before_effect is None:
                self.before_mutation()
            else:
                before_effect(effect_bytes=cost)
        ready(65536)
        flags = os.O_WRONLY | os.O_CREAT | os.O_NOFOLLOW | os.O_CLOEXEC | (os.O_EXCL if new else 0)
        fd = os.open(name, flags, 0o600, dir_fd=self.fds[-1])
        try:
            s = os.fstat(fd)
            if not stat.S_ISREG(s.st_mode) or s.st_uid != 1000 or s.st_gid != 1000 or s.st_nlink != 1 or stat.S_IMODE(s.st_mode) != 0o600:
                raise RuntimeError('Own unaliased600 receipt required')
            if not new:
                ready(65536)
                os.ftruncate(fd, 0)
            offset = 0
            while offset < len(encoded):
                block = encoded[offset:offset + 262144]
                ready(((len(block) + 4095) // 4096) * 4096 + 65536)
                written = os.write(fd, block)
                if type(written) is not int or not 0 < written <= len(block):
                    raise RuntimeError('Receipt raw write failed')
                offset += written
            ready(65536)
            os.fsync(fd)
        finally:
            os.close(fd)  # Raw descriptor close never flushes buffered payload.

    def close(self):
        for fd in reversed(self.fds):
            os.close(fd)
        self.fds.clear()
        self.rows.clear()
