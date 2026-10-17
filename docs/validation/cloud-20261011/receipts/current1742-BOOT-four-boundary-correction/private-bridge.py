"""Two fixed private-file phases; Root dispatches each through existing b78.

No connector, child creation, environment, response/body output, or supervisor.
"""
import datetime as dt
import hashlib
import json
import os
from pathlib import Path
import signal
import stat
import sys
from types import ModuleType, SimpleNamespace

OWN = Path(__file__).absolute().parent
PRIVATE = OWN / 'private'
GRANT = PRIVATE / 'bridge-grant.json'
OLD = Path('/workspace/scratch/rct-validation/current1742_boot_mcp_bridge_source')
R2 = Path('/workspace/scratch/rct-validation/current1742_e2e_boot_diagnostic_source_r2')
CAP = 33554432
PINS = {'/workspace/scratch/rct-validation/current1742_boot_mcp_bridge_source/select-text-carrier.js': 'c4be5803f3e7d41d057d0fabcc58d0d32aecaf3f696b9958fe74c3d3c6f9da9a', '/workspace/scratch/rct-validation/current1742_boot_mcp_bridge_source/decode-carrier.py': '5e13e5a145ff2cf5a9230de9323207fea74317afd27e789ec77f85099c6608cb', '/workspace/scratch/rct-validation/current1742_boot_mcp_bridge_source/connector-call.recipe.js': '26cb77e12b459b753026a448fd680b6e3bbebc14f9d9bb9ebda2e985659601f5', '/workspace/scratch/rct-validation/current1742_boot_mcp_bridge_source/manifest.safe.json': '310c33cf1af15d258635fcbabc245d1dabf7d2e045e86324b7a1a65cf1c96a5a', '/workspace/scratch/rct-validation/current1742_e2e_boot_diagnostic_source_r2/closed-boot-parser.py': 'e7a6f7a522a5ae781a98a72bb08336fb19a4cca5364b9bd28df3215f7c3f0a0c', '/workspace/scratch/rct-validation/current1742_e2e_boot_diagnostic_source_r2/binding.actual.safe.json': '3be4bfce8d095d2201e7e7de3619ca99f9f49dc0df4073ff83c4de7d23513ca1', '/workspace/scratch/rct-validation/current1742_e2e_boot_diagnostic_source_r2/manifest.json': 'c303f7f53358bd2d6e3e56237efe6546ce29cfce25c2f1f76a4b27513a7f9a04', '/workspace/scratch/rct-validation/guard_proc_race_correction/bounded-child.py': 'b78c61293568d9941692e0b92ee34fd0b84db6d532b4a2c9a5ad42ccbf2204a0'}

class Gate(Exception):
    """Only fixed source literals, never an input-derived value."""

def need(ok, code):
    if not ok:
        raise Gate(code)

def identity(st):
    return [st.st_dev, st.st_ino, st.st_uid, st.st_gid, stat.S_IMODE(st.st_mode)]

def directory(path):
    fd = os.open('/', os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW)
    try:
        need(os.fstat(fd).st_uid == 0, 'ANCHOR')
        for part in path.parts[1:]:
            nxt = os.open(part, os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW, dir_fd=fd)
            os.close(fd)
            fd = nxt
        return fd
    except BaseException:
        os.close(fd)
        raise

def read_once(parent, name, limit, private=False, expected=None, guard=None):
    if guard is not None:
        guard()
    fd = os.open(name, os.O_RDONLY | os.O_NOFOLLOW, dir_fd=parent)
    try:
        before = os.fstat(fd)
        need(stat.S_ISREG(before.st_mode) and before.st_uid == 1000 and before.st_nlink == 1
             and 0 < before.st_size <= limit, 'READ_METADATA')
        if private:
            need(stat.S_IMODE(before.st_mode) == 0o600, 'PRIVATE_MODE')
        if expected is not None:
            need(identity(before) == expected, 'READ_IDENTITY')
        if guard is not None:
            guard()  # Current path/resources and UTC LAST after metadata.
        data = os.read(fd, limit + 1)
        after = os.fstat(fd)
        need(len(data) == before.st_size and (before.st_dev, before.st_ino, before.st_size,
             before.st_mtime_ns, before.st_ctime_ns) == (after.st_dev, after.st_ino,
             after.st_size, after.st_mtime_ns, after.st_ctime_ns), 'READ_STABILITY')
        return data
    finally:
        primary = sys.exc_info()[1]
        try:
            os.close(fd)
        except BaseException:
            if primary is None:
                raise
            primary.add_note('PRIVATE_BRIDGE_CLOSE_RECONCILIATION_REQUIRED')

def public_bytes(path):
    fd = directory(path.parent)
    try:
        return read_once(fd, path.name, 1048576)
    finally:
        os.close(fd)

def instant(text):
    need(type(text) is str and text.endswith('Z'), 'CLOCK_SCHEMA')
    try:
        return dt.datetime.fromisoformat(text[:-1] + '+00:00')
    except ValueError:
        raise Gate('CLOCK_SCHEMA') from None

def admit(grant):
    need(type(grant) is dict and grant.get('authorized') is True
         and grant.get('review_approved') is True and grant.get('connector_limits_accepted') is True
         and grant.get('retain_carrier_on_parser_refusal') is True
         and grant.get('scope') == 'ONE_FIXED1742_MCP_BOOT_PRIVATE_BRIDGE'
         and grant.get('head') == '1742d1e806faa5c39c6d182a3554daa8f4239801'
         and type(grant.get('job_id')) is int and grant['job_id'] == 115262001797
         and type(grant.get('run_id')) is int and grant['run_id'] == 38387263916
         and type(grant.get('run_attempt')) is int and grant['run_attempt'] == 1
         and grant.get('private_directory') == str(PRIVATE)
         and grant.get('b78_dispatch_required') is True
         and grant.get('max_bytes') == CAP and type(grant.get('max_bytes')) is int
         and grant.get('phase_seconds') == 45 and grant.get('cleanup_seconds') == 30
         and grant.get('rss_bytes') == 536870912 and grant.get('disk_growth_bytes') == 268435456
         and grant.get('floor_bytes') == 4294967296 and grant.get('shared_stop_bytes') == 16106127360
         and grant.get('shared_reserve_bytes') == 1073741824, 'GRANT')
    start, cleanup, stop = [instant(grant[k]) for k in ('not_before','cleanup_gate','hard_stop')]
    need(start < cleanup < stop and (stop-start).total_seconds() <= 120
         and (stop-cleanup).total_seconds() >= 30, 'GRANT_WINDOW')
    for path, sha in PINS.items():
        need(hashlib.sha256(public_bytes(Path(path))).hexdigest() == sha, 'SOURCE_PIN')
    need(grant.get('helper_sha256') == hashlib.sha256(public_bytes(Path(__file__))).hexdigest(), 'SELF_PIN')
    need(type(grant.get('package_manifest_sha256')) is str
         and hashlib.sha256(public_bytes(OWN/'manifest.safe.json')).hexdigest()
             == grant['package_manifest_sha256'], 'PACKAGE_PIN')
    return start, cleanup, stop

def own_leaf(fd, name, expected):
    current = os.stat(name, dir_fd=fd, follow_symlinks=False)
    need(identity(current) == expected and stat.S_ISREG(current.st_mode)
         and current.st_uid == 1000 and stat.S_IMODE(current.st_mode) == 0o600
         and current.st_nlink == 1, 'LEAF_IDENTITY')

def cleanup(fd, name, expected, guard):
    guard(False)
    own_leaf(fd, name, expected)
    guard(False)  # UTC LAST after current path/metadata/resource checks.
    os.unlink(name, dir_fd=fd)

def save_new(fd, name, data, guard):
    guard()
    out = os.open(name, os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, 0o600, dir_fd=fd)
    expected = None
    try:
        expected = identity(os.fstat(out))  # Record before any write can fail.
        own_leaf(fd, name, expected)
        offset = 0
        while offset < len(data):
            guard()
            own_leaf(fd, name, expected)
            guard()
            count = os.write(out, memoryview(data)[offset:offset+65536])
            need(type(count) is int and count > 0, 'WRITE')
            offset += count
        guard()
        os.fsync(out)
        guard()
        os.fsync(fd)
        return expected
    except BaseException as primary:
        try:
            need(expected is not None, 'UNRECORDED_LEAF')
            cleanup(fd, name, expected, guard)
        except BaseException:
            primary.add_note('PRIVATE_BRIDGE_CLEANUP_RECONCILIATION_REQUIRED')
        raise
    finally:
        primary = sys.exc_info()[1]
        try:
            os.close(out)
        except BaseException as close_error:
            if primary is None:
                # The known leaf must not become untracked merely because close
                # failed after a completed write; fail and reconcile ownership.
                close_error.bridge_leaf_identity = expected
                close_error.bridge_leaf_name = name
                try:
                    need(expected is not None, 'UNRECORDED_LEAF')
                    cleanup(fd, name, expected, guard)
                    close_error.bridge_leaf_disposition = 'REMOVED'
                except BaseException:
                    close_error.bridge_leaf_disposition = 'RECONCILIATION_REQUIRED'
                    close_error.add_note('PRIVATE_BRIDGE_CLEANUP_RECONCILIATION_REQUIRED')
                raise
            primary.add_note('PRIVATE_BRIDGE_CLOSE_RECONCILIATION_REQUIRED')

def frozen_module(path):
    source = public_bytes(path)
    need(hashlib.sha256(source).hexdigest() == PINS[str(path)], 'HELPER_PIN_BEFORE_EXEC')
    module = ModuleType('own_fixed_private_bridge_helper')
    module.__file__ = str(path)
    exec(compile(source, str(path), 'exec'), module.__dict__)
    return module

def run_phase(phase):
    need(phase in ('prepare','consume') and os.getuid() == 1000, 'ENTRY')
    fd = directory(PRIVATE)
    try:
        grant = json.loads(read_once(fd, 'bridge-grant.json', 16384, True))
        start, deadline, stop = admit(grant)
        wanted = grant['private_directory_identity']
        need(identity(os.fstat(fd)) == wanted and os.fstat(fd).st_uid == 1000
             and stat.S_IMODE(os.fstat(fd).st_mode) == 0o700, 'DIRECTORY')
        initial_free = os.statvfs('/workspace')
        baseline = initial_free.f_bavail * initial_free.f_frsize

        def guard(active=True):
            fresh = directory(PRIVATE)
            try:
                need(identity(os.fstat(fresh)) == wanted == identity(os.fstat(fd)), 'DIRECTORY_DRIFT')
            finally:
                os.close(fresh)
            ws, tmp = os.statvfs('/workspace'), os.statvfs('/tmp')
            available = ws.f_bavail * ws.f_frsize
            shared = int(Path('/sys/fs/cgroup/memory.current').read_text())
            rss = 0
            for line in Path('/proc/self/status').read_text().splitlines():
                if line.startswith('VmRSS:'):
                    rss = int(line.split()[1]) * 1024
            need(available >= 4294967296 and tmp.f_bavail * tmp.f_frsize >= 4294967296
                 and max(0,baseline-available) <= 268435456 and rss > 0 and rss <= 536870912
                 and shared + 1073741824 < 16106127360, 'RESOURCE')
            now = dt.datetime.now(dt.timezone.utc)  # UTC LAST after the above work.
            need(start <= now < (deadline if active else stop), 'DEADLINE')

        guard()
        if phase == 'prepare':
            # No private tool input yet. Root fills ONLY this exact precreated inode.
            expected = save_new(fd, 'job.carrier.private', b'', guard)
            try:
                save_new(fd, 'carrier-owner.safe.json', json.dumps({'identity':expected}).encode(), guard)
            except BaseException as primary:
                try:
                    cleanup(fd, 'job.carrier.private', expected, guard)
                except BaseException:
                    primary.add_note('PRIVATE_BRIDGE_CLEANUP_RECONCILIATION_REQUIRED')
                raise
            return {'state':'PREPARED','carrier_identity':expected,'raw_content_emitted':False}

        producer = json.loads(read_once(fd, 'connector-returned.safe.json', 16384, True))
        need(type(producer) is dict and set(producer) == {'state','job_id','run_id','head','utc','carrier_bytes'}
             and producer['state'] == 'ROOT_OBSERVED_TOOL_RETURNED'
             and type(producer['job_id']) is int and producer['job_id'] == 115262001797
             and type(producer['run_id']) is int and producer['run_id'] == 38387263916
             and producer['head'] == grant['head'] and type(producer['carrier_bytes']) is int
             and 0 < producer['carrier_bytes'] <= CAP
             and start <= instant(producer['utc']) < deadline, 'PRODUCER_SETTLEMENT')
        owner = json.loads(read_once(fd, 'carrier-owner.safe.json', 16384, True))
        need(type(owner) is dict and set(owner) == {'identity'}, 'OWNER_SCHEMA')
        carrier_identity = owner['identity']
        save_new(fd, 'consume-attempt.safe.json', b'{"state":"ONE_CONSUME_ADMITTED"}', guard)
        data = read_once(fd, 'job.carrier.private', CAP+1, True, carrier_identity, guard)
        need(len(data) == producer['carrier_bytes'] or
             (len(data) == producer['carrier_bytes']+1 and data.endswith(b'\n')), 'CARRIER_LENGTH')
        if len(data) != producer['carrier_bytes']:
            data = data[:-1]  # Exactly the patch file's extra newline, not payload newline.
        decoder = frozen_module(OLD/'decode-carrier.py')
        payload = decoder.decode_carrier(data)
        parser = frozen_module(R2/'closed-boot-parser.py')
        binding = json.loads(public_bytes(R2/'binding.actual.safe.json'))
        parser.validate_binding(binding)
        raw_identity = None
        # Exact accepted consumer is the sole read/parse/cleanup owner here.
        # A refusal retains only the private carrier for later authorized RAM use;
        # never automatically invoke the parser again or unlink its ambiguous leaf.
        def parser_open(name, flags, *, dir_fd):
            need(name == 'job.log.private' and dir_fd == fd
                 and flags == os.O_RDONLY | os.O_NOFOLLOW, 'PARSER_OPEN')
            own_leaf(fd, name, raw_identity)
            guard()
            return os.open(name, flags, dir_fd=dir_fd)
        def parser_read(raw_fd, limit):
            need(limit == CAP+1 and identity(os.fstat(raw_fd)) == raw_identity, 'PARSER_READ')
            guard()
            return os.read(raw_fd, limit)
        def parser_unlink(name, *, dir_fd):
            need(name == 'job.log.private' and dir_fd == fd, 'PARSER_UNLINK')
            cleanup(fd, name, raw_identity, guard)
        close_error = None
        def parser_close(raw_fd):
            nonlocal close_error
            pending = sys.exc_info()[1]
            try:
                os.close(raw_fd)
            except BaseException as caught:
                close_error = caught
                if pending is not None:
                    pending.add_note('PRIVATE_BRIDGE_CLOSE_RECONCILIATION_REQUIRED')
                # Defer close-only failure until the immutable consumer's one
                # metadata/unlink path has run. Never mask read/parse primary.
        started = False
        try:
            guard()
            raw_identity = save_new(fd, 'job.log.private', payload, guard)
            parser.os = SimpleNamespace(O_RDONLY=os.O_RDONLY, O_NOFOLLOW=os.O_NOFOLLOW,
                open=parser_open, fstat=os.fstat, read=parser_read, close=parser_close,
                stat=os.stat, unlink=parser_unlink)
            guard()
            started = True
            projection = parser.consume_private(fd, binding)
            if close_error is not None:
                raise close_error
        except BaseException as primary:
            if not started and raw_identity is not None:
                try:
                    cleanup(fd, 'job.log.private', raw_identity, guard)
                except BaseException:
                    primary.add_note('PRIVATE_BRIDGE_CLEANUP_RECONCILIATION_REQUIRED')
            raise
        safe_projection = {'state':'CLOSED_REPORTED_HINTS','projection':projection,
            'carrier_cleanup':'PENDING','raw_content_emitted':False,'cause':'UNKNOWN'}
        safe_bytes = json.dumps(safe_projection, separators=(',',':')).encode('utf-8')
        need(0 < len(safe_bytes) <= 65536, 'SAFE_RECEIPT_SIZE')
        save_new(fd, 'REPORT.safe.json', safe_bytes, guard)  # Before input cleanup.
        cleanup(fd, 'job.carrier.private', carrier_identity, guard)
        save_new(fd, 'CLEANUP.safe.json', b'{"carrier_cleanup":"REMOVED","raw_content_emitted":false}', guard)
        return {'state':'CLOSED_REPORTED_HINTS','projection':projection,
                'carrier_cleanup':'REMOVED','raw_content_emitted':False,'cause':'UNKNOWN'}
    except BaseException as primary:
        # A closed ownership/disposition receipt survives b78 stdout=DEVNULL
        # when a known close-only leaf cannot be removed. No error body/notes.
        if 'guard' in locals() and hasattr(primary, 'bridge_leaf_identity'):
            try:
                safe = {'state':'BLOCKED','leaf':primary.bridge_leaf_name,
                    'identity':primary.bridge_leaf_identity,
                    'disposition':primary.bridge_leaf_disposition,
                    'cause':'UNKNOWN','raw_content_emitted':False}
                encoded = json.dumps(safe, separators=(',',':')).encode('utf-8')
                need(len(encoded) <= 16384, 'SAFE_RECEIPT_SIZE')
                save_new(fd, 'FAILURE.safe.json', encoded, lambda _active=True: guard(False))
            except BaseException:
                primary.add_note('PRIVATE_BRIDGE_RECEIPT_RECONCILIATION_REQUIRED')
        raise
    finally:
        primary = sys.exc_info()[1]
        try:
            os.close(fd)
        except BaseException:
            if primary is None:
                raise
            primary.add_note('PRIVATE_BRIDGE_CLOSE_RECONCILIATION_REQUIRED')

if __name__ == '__main__':
    try:
        def handled_term(_signum, _frame):
            raise KeyboardInterrupt()
        signal.signal(signal.SIGTERM, handled_term)
        need(len(sys.argv) == 2, 'ARGV')
        result = run_phase(sys.argv[1])
        print(json.dumps(result, separators=(',',':')))
    except BaseException:
        print('{"state":"BLOCKED","cause":"UNKNOWN","raw_content_emitted":false}')
        raise SystemExit(1) from None
