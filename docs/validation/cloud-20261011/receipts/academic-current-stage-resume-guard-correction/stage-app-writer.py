"""One specific Academic public app writer: dirs opened once, immutable old files."""
import os
from pathlib import Path
import stat


class AppWriter:
    def __init__(self, ns, use, boundary, stopped_directories, metadata_allowance, write_upper):
        self.ns, self.use, self.b = ns, use, boundary
        self.expected = stopped_directories
        self.meta = metadata_allowance
        self.write_upper = write_upper
        self.cache = {}
        self.created_directories = 0
        self.copied_files = 0
        self.flags = os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW | os.O_CLOEXEC
        self.directory('')

    def check(self, rel):
        self.ns.check()
        parts = [] if not rel else rel.split('/')
        names = [''] + ['/'.join(parts[:n]) for n in range(1, len(parts) + 1)]
        for name in names:
            fd, path, expected = self.cache[name]
            if self.b.identity(path.lstat()) != expected or self.b.identity(os.fstat(fd)) != expected:
                raise RuntimeError('Pinned cached public directory switched')

    def directory(self, rel):
        if rel in self.cache:
            self.check(rel)
            return self.cache[rel][0]
        if rel == '':
            parent = self.ns.fds[-1]
            component = 'app'
            expected_row = self.expected['.']
        else:
            parts = rel.split('/')
            parent_rel = '/'.join(parts[:-1])
            parent = self.directory(parent_rel)
            self.check(parent_rel)
            component = parts[-1]
            expected_row = self.expected.get(rel)
            if expected_row is None:
                self.use(effect_bytes=self.meta)
                os.mkdir(component, 0o755, dir_fd=parent)  # Unexpected existing newdir denies, no adoption.
                self.created_directories += 1
        s = os.stat(component, dir_fd=parent, follow_symlinks=False)
        identity = self.b.identity(s)
        if identity['uid'] != 1000 or identity['gid'] != 1000 or identity['mode'] != 0o755:
            raise RuntimeError('Exact public755 owned directory required')
        if expected_row is not None and any(identity[k] != expected_row[k] for k in identity):
            raise RuntimeError('Stopped public directory identity changed')
        fd = os.open(component, self.flags, dir_fd=parent)
        if self.b.identity(os.fstat(fd)) != identity:
            os.close(fd)
            raise RuntimeError('Directory switched during nofollow open')
        path = self.b.PREFIX / 'app'
        if rel:
            path = path / rel
        self.cache[rel] = (fd, path, identity)
        self.check(rel)
        return fd

    def copy(self, row, stream):
        parts = Path(row['path']).parts
        relative = '/'.join(parts[:-1])
        parent = self.directory(relative)
        self.check(relative)
        self.use(effect_bytes=self.meta)
        fd = os.open(parts[-1], os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW,
                     0o644, dir_fd=parent)
        copied = 0
        try:
            s = os.fstat(fd)
            if not stat.S_ISREG(s.st_mode) or s.st_nlink != 1 or s.st_uid != 1000 or s.st_gid != 1000 or stat.S_IMODE(s.st_mode) != 0o644:
                raise RuntimeError('NEW unaliased public644 copy required')
            while block := stream.read(262144):
                if copied + len(block) > row['bytes']:
                    raise RuntimeError('Declared current member bound exceeded')
                offset = 0
                while offset < len(block):
                    pending = block[offset:]
                    self.check(relative)
                    self.use(effect_bytes=self.write_upper(len(pending)))
                    written = os.write(fd, pending)
                    if type(written) is not int or not 0 < written <= len(pending):
                        raise RuntimeError('Public raw write failed')
                    offset += written
                copied += len(block)
            if copied != row['bytes']:
                raise RuntimeError('Exact complete current member size required')
            self.check(relative)
            self.use(effect_bytes=self.meta)
            os.fsync(fd)
        finally:
            os.close(fd)  # No BufferedWriter and no implicit write on failure/close.
        self.copied_files += 1

    def close(self):
        for fd, path, identity in reversed(list(self.cache.values())):
            os.close(fd)
        self.cache.clear()
