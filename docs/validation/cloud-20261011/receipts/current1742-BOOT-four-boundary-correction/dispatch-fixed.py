"""Fixed prepare/consume derivative of accepted Boot CLI b78 callbacks. No SDK."""
import datetime as dt
import hashlib
import json
import os
from pathlib import Path
import stat
import sys
from types import ModuleType
OWN = Path(__file__).absolute().parent
HELPER = OWN/'private-bridge.py'
PRIVATE = OWN/'private'
GUARD = Path('/workspace/scratch/rct-validation/guard_proc_race_correction/bounded-child.py')
GUARD_SHA = 'b78c61293568d9941692e0b92ee34fd0b84db6d532b4a2c9a5ad42ccbf2204a0'
HELPER_SHA = '6f763b5e42675fbdc5137f102e41429a29fa10ba2096b9f8015cb801562b6ab1'
class Gate(Exception): pass

def need(ok, code):
    if not ok:
        raise Gate(code)

def identity(st):
    return [st.st_dev, st.st_ino, st.st_uid, st.st_gid, stat.S_IMODE(st.st_mode)]

def directory(path):
    fd = os.open('/', os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW)
    try:
        need(os.fstat(fd).st_uid == 0, 'NATIVE_ANCHOR')
        for component in path.parts[1:]:
            nxt = os.open(component, os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW, dir_fd=fd)
            os.close(fd)
            fd = nxt
        return fd
    except BaseException:
        os.close(fd)
        raise

def read_fd(parent, name, cap, private=False):
    fd = os.open(name, os.O_RDONLY | os.O_NOFOLLOW, dir_fd=parent)
    try:
        st = os.fstat(fd)
        need(stat.S_ISREG(st.st_mode) and st.st_uid == 1000 and st.st_nlink == 1
            and 0 < st.st_size <= cap, 'INPUT_METADATA')
        if private:
            need(stat.S_IMODE(st.st_mode) == 0o600, 'INPUT_PRIVATE_MODE')
        data = os.read(fd, cap + 1)
        need(len(data) == st.st_size, 'INPUT_LENGTH')
        return data
    finally:
        os.close(fd)

def public_bytes(path):
    parent = directory(path.parent)
    try:
        return read_fd(parent, path.name, 1024*1024)
    finally:
        os.close(parent)

def module_from_buffer(path, pin):
    source = public_bytes(path)
    need(hashlib.sha256(source).hexdigest() == pin, 'SOURCE_PIN_BEFORE_EXEC')
    result = ModuleType('sealed_boot_fixed_dispatch_helper')
    result.__file__ = str(path)
    exec(compile(source, str(path), 'exec'), result.__dict__)
    return result

def main(phase):
    need(phase in ('prepare','consume') and os.getuid() == 1000, 'ENTRY')
    fd = directory(PRIVATE)
    try:
        grant = json.loads(read_fd(fd, 'bridge-grant.json', 16384, True))
        need(type(grant) is dict and grant.get('authorized') is True
             and grant.get('review_approved') is True and grant.get('helper_sha256') == HELPER_SHA,
             'GRANT_BEFORE_HELPER_EXEC')
        need(grant.get('dispatcher_sha256') == hashlib.sha256(public_bytes(Path(__file__))).hexdigest(), 'DISPATCH_SELF')
        wanted = grant['private_directory_identity']
        need(identity(os.fstat(fd)) == wanted and os.fstat(fd).st_uid == 1000
             and stat.S_IMODE(os.fstat(fd).st_mode) == 0o700, 'PRIVATE_DIRECTORY')
        helper = module_from_buffer(HELPER, HELPER_SHA)
        start, cleanup, stop = helper.admit(grant)
        guard = module_from_buffer(GUARD, GUARD_SHA)
        latest_ids = {}
        status_ordinal = 0
        ws = os.statvfs('/workspace')
        baseline = ws.f_bavail * ws.f_frsize

        def resources(active=True):
            current = directory(PRIVATE)
            try:
                need(identity(os.fstat(current)) == wanted == identity(os.fstat(fd)), 'DIRECTORY_DRIFT')
            finally:
                os.close(current)
            ws, tmp = os.statvfs('/workspace'), os.statvfs('/tmp')
            workspace_free = ws.f_bavail * ws.f_frsize
            shared = int(Path('/sys/fs/cgroup/memory.current').read_text())
            table = guard.process_table()
            owned = guard.owned_records(table, os.getpid(), 0, latest_ids)
            rss = 0
            for pid in {os.getpid(), *owned}:
                try:
                    for line in (Path('/proc')/str(pid)/'status').read_text().splitlines():
                        if line.startswith('VmRSS:'):
                            rss += int(line.split()[1])*1024
                            break
                except (FileNotFoundError, ProcessLookupError):
                    continue
            need(workspace_free >= 4294967296 and tmp.f_bavail*tmp.f_frsize >= 4294967296
                 and max(0,baseline-workspace_free) <= 268435456 and rss <= 536870912
                 and shared+1073741824 < 16106127360, 'RESOURCE')
            now = dt.datetime.now(dt.timezone.utc)
            need(start <= now < (cleanup if active else stop), 'DEADLINE')
            return now

        def save(name, value):
            data = json.dumps(value, separators=(',',':')).encode('utf-8')
            need(len(data) <= 65536, 'SAFE_SIZE')
            helper.save_new(fd, name, data, lambda _active=True: resources(False))

        def observe():
            try:
                resources()
            except Gate as caught:
                return 'RESOURCE_GUARD' if caught.args[0]=='RESOURCE' else 'BOUNDED_DEADLINE'
            return None

        def status(value):
            nonlocal status_ordinal
            changed = False
            rows = value['members_including_Z']
            child = guard.process_table().get(value['child_pid'])
            if child is not None:
                rows = rows+[child]
            for row in rows:
                pid, ticks = row['pid'], row['start_ticks']
                need(pid not in latest_ids or latest_ids[pid] == ticks, 'BIRTH_IDENTITY')
                if pid not in latest_ids:
                    latest_ids[pid] = ticks
                    changed = True
            if changed or value['state'] != 'RUNNING':
                status_ordinal += 1
                save(f'{phase}-status-{status_ordinal}.safe.json', value)

        now = resources()
        need((cleanup-now).total_seconds() >= 55, 'INSUFFICIENT_PHASE_AND_SETTLEMENT_WINDOW')
        supervisor = guard.process_table()[os.getpid()]
        save(f'{phase}-supervisor.safe.json', {'pid':supervisor['pid'],
            'start_ticks':supervisor['start_ticks'],'grant_phase_seconds':45})
        env = {'PATH':'/usr/bin:/bin','LANG':'C.UTF-8','LC_ALL':'C.UTF-8','PYTHONDONTWRITEBYTECODE':'1'}
        result = guard.run_owned(['/usr/bin/python3','-B',str(HELPER),phase],
            cwd=str(OWN), env=env, deadline_seconds=45, term_seconds=5, kill_seconds=5,
            observe=observe, status=status)
        for row in result['known_owned_identities']:
            latest_ids[row['pid']] = row['start_ticks']
        table = guard.process_table()
        absent = all(pid not in table or table[pid]['start_ticks']!=ticks for pid,ticks in latest_ids.items())
        outcome_name = 'carrier-owner.safe.json' if phase=='prepare' else 'REPORT.safe.json'
        save(f'{phase}-terminal.safe.json', {'result':result,'all_known_births_absent':absent,
             'expected_safe_outcome_file':outcome_name,'raw_content_emitted':False})
        # File-based capture only. Never print any unvalidated JSON file content.
        outcome = os.stat(outcome_name, dir_fd=fd, follow_symlinks=False)
        need(stat.S_ISREG(outcome.st_mode) and outcome.st_uid==1000
             and stat.S_IMODE(outcome.st_mode)==0o600 and outcome.st_nlink==1
             and 0 < outcome.st_size <= 65536, 'SAFE_OUTCOME_METADATA')
        need(result['phase_success'] and result['settled'] is True
             and result['all_owned_processes_reaped'] is True and absent, 'NATIVE_PHASE_FAILED')
        return 0
    finally:
        os.close(fd)

if __name__ == '__main__':
    try:
        need(len(sys.argv)==2, 'ARGV')
        raise SystemExit(main(sys.argv[1]))
    except SystemExit:
        raise
    except BaseException:
        raise SystemExit(1) from None
