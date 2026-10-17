"""Exact readonly stopped894 proof; no writes, no symlink/hash/owner relaxation."""
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import stat

BASE = Path('/workspace/scratch/rct-validation/current_academic_refresh_pin_correction/actual-stage01')
MANIFEST = BASE / 'manifest.json'
MANIFEST_SHA = '77b75e656d7b3857879fa4936a59a4e3abddc85761462dbf81e382bb541fa824'
PARTIAL = BASE / 'partial-payload.safe.json'
PARTIAL_SHA = '7369bebaef675999e2d112b039e107a290a79dd5d75b34c3b3fba86912480fc7'
BUILD_SHA = '08dd08de01d3c4fe5c0ebcea7cd53e22c41f36fb4d65e94b8d216b8aacda80ac'
JAR_SHA = '4bc14708baff86a6c8b88824406cbe3513136c9664d8491fda3e3cec61c67b88'


def path_name(raw, root=False):
    if root and raw == '.':
        return raw
    p = PurePosixPath(raw)
    if (not raw or p.is_absolute() or str(p) != raw or '..' in p.parts
            or '\\' in raw or ':' in raw or '\0' in raw):
        raise RuntimeError('Canonical partial relative path required')
    return raw


def validate_binding(g, boundary):
    expected = {'stage_resume_authorized': True, 'partial_stage_manifest': str(MANIFEST),
                'partial_stage_manifest_sha256': MANIFEST_SHA,
                'partial_stage_receipt': str(PARTIAL), 'partial_stage_receipt_sha256': PARTIAL_SHA,
                'build_result_sha256': BUILD_SHA}
    if any(type(g.get(k)) is not type(v) or g[k] != v for k, v in expected.items()):
        raise RuntimeError('NEW exact originalBUILD/partial894 grant binding required')
    if boundary.sha(MANIFEST) != MANIFEST_SHA or boundary.sha(PARTIAL) != PARTIAL_SHA:
        raise RuntimeError('Original partial authority changed')
    manifest = json.loads(MANIFEST.read_text())
    if not any(row['path'] == str(PARTIAL) and row['sha256'] == PARTIAL_SHA for row in manifest['files']):
        raise RuntimeError('Exact partial payload must belong to original77 seal')
    return json.loads(PARTIAL.read_text())


def validate_document(doc, built, app):
    rows = doc['files']
    dirs = doc['directories']
    expected = {r['path']: r for r in built['regular_entries']}
    if (doc['status'] != 'PARTIAL_STAGE_ONLY_NOT_READY' or doc['root'] != str(app)
            or doc['current_JAR_sha256'] != JAR_SHA or built['jar_sha256'] != JAR_SHA
            or doc['current_producer'] != built['producer_revision']
            or doc['actual_regular_members'] != 894 or len(rows) != 894
            or doc['matching_complete_members'] != 894 or doc['incomplete_or_unknown_members'] != []
            or len(expected) != 1797 or doc['expected_regular_members'] != 1797
            or doc['full_stage_receipt_present'] is not False or doc['host_specs_present'] is not False
            or len({path_name(r['path']) for r in rows}) != 894
            or len({path_name(r['path'], root=True) for r in dirs}) != len(dirs)):
        raise RuntimeError('Exact original stopped894/current1797 state required')
    for row in rows:
        original = expected.get(row['path'])
        if (original is None or row['sha256'] != original['sha256'] or row['bytes'] != original['bytes']
                or row['mode'] != 0o644 or row['uid'] != 1000 or row['gid'] != 1000 or row['nlink'] != 1
                or row['matches_current_JAR_member'] is not True):
            raise RuntimeError('Exact partial member/JAR/hash/size/readability identity required')
    wanted_dirs = {'.'}
    for row in built['regular_entries']:
        path_name(row['path'])
        wanted_dirs.update(str(p) for p in PurePosixPath(row['path']).parents if str(p) != '.')
    if any(row['path'] not in wanted_dirs or row['mode'] != 0o755 or row['uid'] != 1000 or row['gid'] != 1000 for row in dirs):
        raise RuntimeError('Only expected currentJAR public directories allowed')
    if '.' not in {r['path'] for r in dirs}:
        raise RuntimeError('Exact stopped app root identity required')
    return {r['path']: r for r in rows}, {r['path']: r for r in dirs}


def same_directory(s, row):
    return (stat.S_ISDIR(s.st_mode) and s.st_dev == row['device'] and s.st_ino == row['inode']
            and s.st_uid == 1000 and s.st_gid == 1000 and stat.S_IMODE(s.st_mode) == 0o755)


def same_regular(s, row):
    return (stat.S_ISREG(s.st_mode) and s.st_dev == row['device'] and s.st_ino == row['inode']
            and s.st_uid == 1000 and s.st_gid == 1000 and s.st_nlink == 1
            and stat.S_IMODE(s.st_mode) == 0o644 and s.st_size == row['bytes'])


def verify_tree(ns, app, files, directories):
    """FD-scandir + relative O_NOFOLLOW reads; existing complete files are never modified."""
    flags = os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW | os.O_CLOEXEC
    seen_files, seen_dirs = set(), set()
    root = directories['.']
    ns.check()
    s = os.stat('app', dir_fd=ns.fds[-1], follow_symlinks=False)
    if not same_directory(s, root):
        raise RuntimeError('Stopped app root changed')
    fd = os.open('app', flags, dir_fd=ns.fds[-1])
    try:
        if not same_directory(os.fstat(fd), root):
            raise RuntimeError('Stopped app root switched')
        def walk(parent, relative):
            ns.check()
            row = directories.get(relative)
            if row is None or not same_directory(os.fstat(parent), row):
                raise RuntimeError('Partial directory foreign/extra/changed')
            seen_dirs.add(relative)
            with os.scandir(parent) as iterator:
                names = sorted(entry.name for entry in iterator)
            for name in names:
                rel = name if relative == '.' else relative + '/' + name
                path_name(rel)
                s = os.stat(name, dir_fd=parent, follow_symlinks=False)
                if stat.S_ISDIR(s.st_mode):
                    expected = directories.get(rel)
                    if expected is None or not same_directory(s, expected):
                        raise RuntimeError('Partial extra/foreign/changed directory denied')
                    child = os.open(name, flags, dir_fd=parent)
                    try:
                        if not same_directory(os.fstat(child), expected):
                            raise RuntimeError('Partial directory switched')
                        walk(child, rel)
                    finally:
                        os.close(child)
                else:
                    expected = files.get(rel)
                    if expected is None or not same_regular(s, expected):
                        raise RuntimeError('Partial file alias/link/owner/mode/size/extra denied')
                    child = os.open(name, os.O_RDONLY | os.O_NOFOLLOW | os.O_CLOEXEC, dir_fd=parent)
                    try:
                        if not same_regular(os.fstat(child), expected):
                            raise RuntimeError('Partial file switched')
                        with os.fdopen(child, 'rb') as stream:
                            child = None
                            digest = hashlib.file_digest(stream, 'sha256').hexdigest()
                        if digest != expected['sha256']:
                            raise RuntimeError('Partial file content changed')
                    finally:
                        if child is not None:
                            os.close(child)
                    seen_files.add(rel)
        walk(fd, '.')
        if seen_files != set(files) or seen_dirs != set(directories):
            raise RuntimeError('Exact partial inventory missing/extra denied')
        ns.check()
    finally:
        os.close(fd)
    return seen_files
