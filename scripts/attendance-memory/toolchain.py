"""Exact official Temurin artifact, confined to this run's private toolchain."""
from pathlib import Path
import hashlib
import os
import re
import subprocess
import tarfile
import time
import urllib.request

URL = 'https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.13%2B9/OpenJDK21U-jdk_x64_linux_hotspot_21.0.13_9.tar.gz'
SHA256 = 'd7f5d5cf865a2902b70ff549b97586a7d3f234e016f041ab447ea740c7d66d1c'
SIZE = 207858352
VERSION = '21.0.13+9'


def ensure(directory):
    directory = Path(directory)
    directory.mkdir(mode=0o700, parents=True, exist_ok=False)
    archive = directory / 'jdk.tar.gz'
    digest = hashlib.sha256()
    total = 0
    deadline = time.monotonic() + 300
    with urllib.request.urlopen(URL, timeout=60) as response, archive.open('xb') as output:
        while chunk := response.read(1024 * 1024):
            total += len(chunk)
            if total > SIZE or time.monotonic() > deadline:
                raise ValueError('JDK_SIZE')
            digest.update(chunk)
            output.write(chunk)
    if total != SIZE or digest.hexdigest() != SHA256:
        raise ValueError('JDK_PIN')
    with tarfile.open(archive) as tar:
        members = tar.getmembers()
        if len(members) > 8192 or sum(max(0, m.size) for m in members) > 1024**3:
            raise ValueError('JDK_ARCHIVE_BOUND')
        tar.extractall(directory, filter='data')
    home = directory / 'jdk-21.0.13+9'
    result = subprocess.run([str(home / 'bin/java'), '-version'], capture_output=True, timeout=15, check=True)
    text = result.stderr.decode('utf-8', 'replace')
    if not re.search(r'openjdk version "21\.0\.13"', text) or 'Temurin-21.0.13+9' not in text:
        raise ValueError('JDK_RUNTIME_PROFILE')
    return home
