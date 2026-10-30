"""One visible owner lease, inherited across apply and adopted by the replay controller."""
import fcntl
import os
from pathlib import Path
import stat


def acquire(path, inherited=None):
    path = Path(path)
    if inherited is None:
        stream = path.open('a')
    else:
        if not inherited.isdecimal() or not 3 <= int(inherited) <= 1024:
            raise ValueError('Owner lease descriptor')
        expected, actual = path.lstat(), os.fstat(int(inherited))
        if not stat.S_ISREG(expected.st_mode) or not stat.S_ISREG(actual.st_mode) or expected.st_nlink != 1 or actual.st_nlink != 1:
            raise ValueError('Owner lease file')
        if (expected.st_dev, expected.st_ino, expected.st_uid) != (actual.st_dev, actual.st_ino, os.getuid()):
            raise ValueError('Owner lease identity')
        stream = os.fdopen(os.dup(int(inherited)), 'r')
    try:
        fcntl.flock(stream, fcntl.LOCK_EX | fcntl.LOCK_NB)
    except BaseException:
        stream.close()
        raise
    return stream
