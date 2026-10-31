"""Extract only regular tracked archive bytes for fs/config scanning."""
from pathlib import Path, PurePosixPath
import sys
import tarfile

def extract(archive, destination):
    count=0;size=0
    with tarfile.open(archive) as tar:
        for member in tar:
            name=PurePosixPath(member.name)
            if name.is_absolute() or '..' in name.parts:raise ValueError('Unsafe tracked archive path')
            if not member.isdir() and not member.isfile():raise ValueError('Tracked links/special files need explicit scan admission')
            if name.parts and name.parts[0] in {'.git','.pr3-local','.pr3-release'}:raise ValueError('Private runtime path is not an admitted scan input')
            count+=1;size+=member.size
            if count>100000 or size>2*1024**3 or member.size>128*1024**2:raise ValueError('Tracked archive bound exceeded')
            target=destination.joinpath(*name.parts)
            if member.isdir():target.mkdir(mode=0o700,parents=True,exist_ok=True)
            else:
                target.parent.mkdir(mode=0o700,parents=True,exist_ok=True)
                with tar.extractfile(member) as source,target.open('xb') as output:
                    while chunk:=source.read(1024*1024):output.write(chunk)
    return {'trackedEntries':count,'trackedBytes':size}

if __name__=='__main__':extract(Path(sys.argv[1]),Path(sys.argv[2]))
