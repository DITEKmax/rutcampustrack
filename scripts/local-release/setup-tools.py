"""Install only task-private, version-bound release-check tools."""
from pathlib import Path
import hashlib
import os
import re
import subprocess
import sys
import time
import tarfile
import urllib.request

MAX_ARCHIVE=128*1024*1024

def download(url, target, maximum=MAX_ARCHIVE):
    deadline=time.monotonic()+120
    with urllib.request.urlopen(url,timeout=30) as response, target.open('xb') as out:
        total=0
        while chunk:=response.read(1024*1024):
            if time.monotonic()>deadline:raise TimeoutError('Tool download deadline')
            total+=len(chunk)
            if total>maximum:raise ValueError('Tool archive exceeds its bound')
            out.write(chunk)
    return target

def binary(archive, member, target):
    with tarfile.open(archive) as tar:
        info=tar.getmember(member)
        if not info.isfile() or not 0<info.size<=256*1024*1024:raise ValueError('Unexpected tool member')
        with tar.extractfile(info) as source,target.open('xb') as out:
            while chunk:=source.read(1024*1024):out.write(chunk)
    target.chmod(0o700)

def main():
    audit=Path(os.environ['RCT_LOCAL_RELEASE_AUDIT']);tools=audit/'tools';tools.mkdir(mode=0o700)
    if sys.argv[1:] not in ([], ['--monitoring-only']):raise ValueError('Tool selection')
    if not sys.argv[1:]:
        trivy=download('https://github.com/aquasecurity/trivy/releases/download/v0.70.0/trivy_0.70.0_Linux-64bit.tar.gz',tools/'trivy.tar.gz',47748408)
        if trivy.stat().st_size!=47748408 or hashlib.sha256(trivy.read_bytes()).hexdigest()!='8b4376d5d6befe5c24d503f10ff136d9e0c49f9127a4279fd110b727929a5aa9':raise ValueError('Trivy pin mismatch')
        binary(trivy,'trivy',tools/'trivy')
    for project,version,names in [('prometheus','2.55.1',['prometheus','promtool']),('alertmanager','0.27.0',['alertmanager','amtool'])]:
        filename=f'{project}-{version}.linux-amd64.tar.gz';base=f'https://github.com/prometheus/{project}/releases/download/v{version}/'
        sums=download(base+'sha256sums.txt',tools/(project+'.sha256sums'),1024*1024).read_text()
        candidates=[l.split()[0] for l in sums.splitlines() if l.endswith(filename)]
        if len(candidates)!=1 or not re.fullmatch('[a-f0-9]{64}',candidates[0]):raise ValueError('Tool checksum publication mismatch')
        archive=download(base+filename,tools/filename)
        if hashlib.sha256(archive.read_bytes()).hexdigest()!=candidates[0]:raise ValueError('Monitoring tool pin mismatch')
        for name in names:binary(archive,f'{project}-{version}.linux-amd64/{name}',tools/name)
    print('PASS task-private prometheus2.55.1/alertmanager0.27.0' + ('/Trivy0.70.0' if not sys.argv[1:] else ''))

if __name__=='__main__':main()
