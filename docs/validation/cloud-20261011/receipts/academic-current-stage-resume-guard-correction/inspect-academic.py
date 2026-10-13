"""Read only a new standard Academic BootJAR and its fixture inspector."""
import hashlib
from pathlib import Path, PurePosixPath
import stat
import types
import zipfile


def headers(raw):
    unfolded = []
    for line in raw.decode('utf8').replace('\r\n', '\n').splitlines():
        if line.startswith(' '):
            if not unfolded:
                raise RuntimeError('Manifest continuation denied')
            unfolded[-1] += line[1:]
        else:
            unfolded.append(line)
    return dict(line.split(': ', 1) for line in unfolded if ': ' in line)


def member(name, directory=False):
    raw = name[:-1] if directory and name.endswith('/') else name
    p = PurePosixPath(raw)
    if not raw or p.is_absolute() or str(p) != raw or '..' in p.parts or '\\' in raw or ':' in raw or '\0' in raw:
        raise RuntimeError('Canonical contained ZIP member required')
    return p


def inspect(checkout, revision):
    path = checkout / 'services/academic-service/academic-app/build/libs/academic-app-0.1.0.jar'
    s = path.lstat()
    if not stat.S_ISREG(s.st_mode) or s.st_uid != 1000 or s.st_nlink != 1 or not 0 < s.st_size <= 268435456:
        raise RuntimeError('Bounded own fresh production JAR required')
    fixture = checkout / 'tests/e2e/backend-clock/verify-jar-isolation.py'
    isolation = types.ModuleType('isolation')
    exec(compile(fixture.read_bytes(), str(fixture), 'exec'), isolation.__dict__)
    files = []
    with zipfile.ZipFile(path) as archive:
        entries = archive.infolist()
        if len(entries) > 6000 or len({m.filename for m in entries}) != len(entries):
            raise RuntimeError('Bounded unique ZIP members required')
        if sum(m.file_size for m in entries) > 268435456 or archive.testzip() is not None:
            raise RuntimeError('Payload bound/ZIP CRC failed')
        for m in entries:
            member(m.filename, m.is_dir())
            kind = stat.S_IFMT(m.external_attr >> 16)
            if m.flag_bits & 1 or kind not in [0, stat.S_IFREG, stat.S_IFDIR]:
                raise RuntimeError('ZIP links/special/encrypted members denied')
            if not m.is_dir():
                with archive.open(m) as stream:
                    digest = hashlib.file_digest(stream, 'sha256').hexdigest()
                files.append({'path': m.filename, 'bytes': m.file_size, 'sha256': digest})
        isolation.require_fixture_absent(archive)
        manifest = headers(archive.read('META-INF/MANIFEST.MF'))
        expected = {'Main-Class': 'org.springframework.boot.loader.launch.JarLauncher',
                    'Start-Class': 'ru.rutcampustrack.academic.AcademicApplication',
                    'Spring-Boot-Classes': 'BOOT-INF/classes/', 'Spring-Boot-Lib': 'BOOT-INF/lib/',
                    'Spring-Boot-Classpath-Index': 'BOOT-INF/classpath.idx',
                    'Spring-Boot-Layers-Index': 'BOOT-INF/layers.idx'}
        if any(manifest.get(k) != v for k, v in expected.items()):
            raise RuntimeError('Canonical current standard Boot layout required')
        if 'BOOT-INF/classes/ru/rutcampustrack/academic/AcademicApplication.class' not in archive.namelist():
            raise RuntimeError('Genuine application entrypoint required')
        props = dict(line.split('=', 1) for line in archive.read('BOOT-INF/classes/git.properties').decode().splitlines()
                     if '=' in line and not line.startswith('#'))
        if props.get('git.commit.id') != revision:
            raise RuntimeError('Fresh embedded frozen producer revision required')
    with path.open('rb') as stream:
        digest = hashlib.file_digest(stream, 'sha256').hexdigest()
    return {'producer_revision': revision, 'jar': str(path), 'jar_sha256': digest,
            'jar_bytes': s.st_size, 'jar_allocated': s.st_blocks * 512,
            'unfolded_manifest': manifest, 'regular_entries': files,
            'recursive_fixture_absent': True, 'CRC': 'PASS',
            'classpath_idx_sha256': next(r['sha256'] for r in files if r['path'] == 'BOOT-INF/classpath.idx'),
            'layers_idx_sha256': next(r['sha256'] for r in files if r['path'] == 'BOOT-INF/layers.idx')}
