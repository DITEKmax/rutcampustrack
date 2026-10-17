"""One fixed original GitHub artifact archive capture and unchanged closed XML projection, under reviewed b78 ownership."""
import datetime
import errno
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import time
import stat
import types
import ssl
import urllib.error
import zipfile
import xml.etree.ElementTree as ET

os.umask(0o077)
scope = Path(__file__).resolve().parent
plan_path = scope / "green-plan.safe.json"
plan = json.loads(plan_path.read_text())
worktree = Path(plan["worktree"])
run = Path(plan["run"])
grant_path = Path(sys.argv[1])

ENV_KEYS = frozenset(('HOME', 'GH_CONFIG_DIR', 'GH_TOKEN', 'GITHUB_TOKEN',
    'HTTPS_PROXY', 'HTTP_PROXY', 'ALL_PROXY', 'NO_PROXY', 'https_proxy',
    'http_proxy', 'all_proxy', 'no_proxy', 'SSL_CERT_FILE', 'SSL_CERT_DIR',
    'CURL_CA_BUNDLE', 'REQUESTS_CA_BUNDLE'))

def explicit_environment(inherited):
    # Only existing standard GH auth/proxy/CA context is read for this exact GET.
    # Never serialize values/key presence, copy all ENV, or enable GH_DEBUG.
    env = {key: inherited[key] for key in ENV_KEYS if key in inherited}
    env.update(PATH='/usr/bin:/bin', LANG='C.UTF-8', LC_ALL='C.UTF-8',
        GH_PROMPT_DISABLED='1', GH_PAGER='cat', GH_HOST='github.com',
        PYTHONDONTWRITEBYTECODE='1')
    return env


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def utc():
    return datetime.datetime.now(datetime.timezone.utc)

def write(name, value):
    target = run / name
    temporary = target.with_name(target.name + ".next")
    temporary.write_text(json.dumps(value, indent=2) + "\n")
    temporary.replace(target)

if grant_path.resolve() != grant_path.absolute() or grant_path.stat().st_mode & 0o777 != 0o600:
    raise ValueError("Exact private Root grant required")
if digest(grant_path) != sys.argv[2]:
    raise ValueError("Root grant hash mismatch")
grant = json.loads(grant_path.read_text())
if not (grant["authorized"] is True and grant["lease"] == plan["lease"]
        and grant["driver_sha256"] == digest(Path(__file__))
        and grant["child_sha256"] == digest(scope / "logged-green-child.py")
        and grant["plan_sha256"] == digest(plan_path)
        and grant["test_sha256"] == plan["test_sha256"]
        and grant["guard_sha256"] == plan["guard_sha256"]
        and grant["source_review_approved"] is True
        and grant.get("original_artifact_observation_authorized") is True
        and grant.get("github_archive_api_transport_authorized") is True
        and grant.get("selector_sha256") == digest(scope / "selector94.py")
        and grant.get("artifact_id") == 11806374316
        and grant.get("producer_head") == plan['artifact']['head_sha']
        and grant.get("actual_checkout") == plan['actual_checkout']
        and grant.get("selected_xml_projection_only") is True):
    raise ValueError("Grant/source identity mismatch")
issued = datetime.datetime.fromisoformat(grant["issued_utc"])
stop = datetime.datetime.fromisoformat(grant["stop_utc"])
if not issued.tzinfo or not stop.tzinfo or not issued <= utc() < stop:
    raise ValueError("Fresh grant required")
if subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=worktree).decode().strip() != plan["base"]:
    raise ValueError("Formatted source commit changed")
manifest_path = scope / plan["source_manifest"]
if not (digest(manifest_path) == plan["source_manifest_sha256"]
        and grant["source_manifest_sha256"] == plan["source_manifest_sha256"]):
    raise ValueError("Exact formatted source manifest required")
manifest = json.loads(manifest_path.read_text())
for path, expected in {**manifest["files"], **manifest["protected_files"]}.items():
    if digest(worktree / path) != expected:
        raise ValueError("Formatted source manifest mismatch")
if digest(worktree / plan["test_path"]) != plan["test_sha256"]:
    raise ValueError("Test source changed")
guard_path = Path(plan["guard_path"])
if digest(guard_path) != plan["guard_sha256"]:
    raise ValueError("Reviewed ownership guard changed")
spec = importlib.util.spec_from_file_location("reviewed_b78", guard_path)
guard = importlib.util.module_from_spec(spec)
spec.loader.exec_module(guard)
limits = grant["limits"]
gib = 1024**3
if not (0 < limits["phase_seconds"] <= 540 and limits["cleanup_margin_seconds"] >= 60
        and limits["workspace_floor_bytes"] >= 4*gib and 0 < limits["disk_growth_cap_bytes"] <= 2*gib
        and limits["shared_stop_bytes"] <= 15*gib and limits["shared_reserve_bytes"] >= gib
        and 0 < limits["shared_growth_cap_bytes"] <= 4*gib
        and 0 < limits["owned_rss_cap_bytes"] <= 4*gib and 0 < limits["private_log_cap_bytes"] <= 8*1024**2):
    raise ValueError("Bounded resource specification required")
initial_free = shutil.disk_usage("/workspace").free
memory_path = Path("/sys/fs/cgroup/memory.current")
initial_memory = int(memory_path.read_text())
known = {}
peaks = {"memory": initial_memory, "min_workspace_free": initial_free, "owned_rss": 0, "log_bytes": 0}
last_status = 0.0

def observe():
    free = shutil.disk_usage("/workspace").free
    memory = int(memory_path.read_text())
    maximum = int(Path("/sys/fs/cgroup/memory.max").read_text())
    log = run / "native.raw.private.log"
    log_size = log.stat().st_size if log.exists() else 0
    rss = 0
    for pid, ticks in list(known.items()):
        try:
            proc = Path("/proc") / str(pid)
            fields = (proc / "stat").read_text().rsplit(")", 1)[1].split()
            if int(fields[19]) == ticks and fields[0] != "Z":
                rss += int((proc / "statm").read_text().split()[1]) * os.sysconf("SC_PAGE_SIZE")
        except OSError as error:
            if error.errno not in (errno.ENOENT, errno.ESRCH):
                raise
    peaks.update(memory=max(peaks["memory"], memory), min_workspace_free=min(peaks["min_workspace_free"], free),
                 owned_rss=max(peaks["owned_rss"], rss), log_bytes=max(peaks["log_bytes"], log_size))
    if utc() >= stop - datetime.timedelta(seconds=limits["cleanup_margin_seconds"]):
        return "ABSOLUTE_STOP_MARGIN"
    if free < limits["workspace_floor_bytes"] or initial_free-free > limits["disk_growth_cap_bytes"]:
        return "WORKSPACE_LIMIT"
    if memory >= min(limits["shared_stop_bytes"], maximum-limits["shared_reserve_bytes"]):
        return "SHARED_RESERVE_STOP"
    if memory-initial_memory > limits["shared_growth_cap_bytes"] or rss > limits["owned_rss_cap_bytes"]:
        return "MEMORY_GROWTH_STOP"
    if log_size > limits["private_log_cap_bytes"]:
        return "PRIVATE_LOG_CAP"
    return None

def status(value):
    global last_status
    for row in value["members_including_Z"]:
        if row["pid"] in known and known[row["pid"]] != row["start_ticks"]:
            raise ValueError("Owned process birth changed")
        known[row["pid"]] = row["start_ticks"]
    now = time.monotonic()
    if value["state"] != "RUNNING" or now-last_status >= 1:
        write("process.safe.json", {"utc": utc().isoformat(), **value})
        last_status = now

if run.exists() or run.is_symlink() or observe():
    raise ValueError("Fresh own namespace and resource floor required")
run.mkdir(mode=0o700)
(run / "private-tmp").mkdir(mode=0o700)
run_fd = os.open(run, os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW)
write("supervisor.safe.json", guard.process_table()[os.getpid()])
write("parent-authority.private.json", {
    "parent": guard.process_table()[os.getpid()],
    "grant_path": str(grant_path.absolute()), "grant_sha256": sys.argv[2],
    "driver_sha256": digest(Path(__file__)), "child_sha256": digest(scope / "logged-green-child.py"),
    "plan_sha256": digest(plan_path), "run": str(run),
})
print(json.dumps({"stage": "STARTED", "supervisor": guard.process_table()[os.getpid()], "run": str(run)}), flush=True)
environment = {
    "JAVA_HOME": "/workspace/rct-cloud-setup/jdk21", "GRADLE_USER_HOME": "/workspace/.gradle",
    "PATH": "/workspace/rct-cloud-setup/jdk21/bin:/usr/local/bin:/usr/bin:/bin",
    "LANG": "C.UTF-8", "LC_ALL": "C.UTF-8", "TZ": "UTC", "TMPDIR": str(run / "private-tmp"),
    "PYTHONDONTWRITEBYTECODE": "1", "PYTHONNOUSERSITE": "1", "PYTHONSAFEPATH": "1",
}
environment.update(explicit_environment(os.environ))
remaining = (stop-utc()).total_seconds()-limits["cleanup_margin_seconds"]
result = guard.run_owned(
    [sys.executable, str(scope / "logged-green-child.py")], str(worktree), environment,
    deadline_seconds=min(limits["phase_seconds"], remaining), observe=observe, status=status,
    term_seconds=15, kill_seconds=10,
)
environment.clear()  # Normal auth/proxy context never persists into output or selector state.

class ClosedArchiveGate(Exception):
    def __init__(self, code):
        self.code = code
        super().__init__('CLOSED_ARCHIVE_REJECTION')

def need(ok, code):
    if not ok:
        raise ClosedArchiveGate(code)

def active():
    need(observe() is None, 'ACTIVE_RESOURCE_OR_DEADLINE')

def matches_leaf(info, record):
    return (type(record) is dict and set(record) == {'dev', 'ino', 'uid', 'mode', 'nlink', 'initial_size'}
            and all(type(v) is int for v in record.values()) and record['initial_size'] == 0
            and info.st_dev == record['dev'] and info.st_ino == record['ino']
            and stat.S_ISREG(info.st_mode) and info.st_uid == record['uid'] == os.geteuid()
            and stat.S_IMODE(info.st_mode) == record['mode'] == 0o600
            and info.st_nlink == record['nlink'] == 1)

def private_read(name, cap, expected_size=None, record=None):
    # Only the fixed metadata journal or the one captured ZIP can be read.
    descriptor = None
    data = None
    primary = None
    close_failed = False
    try:
        need(name in ('archive-identity.safe.json', 'original-auth.archive.private.zip'), 'PRIVATE_READ_SCOPE')
        active()
        descriptor = os.open(name, os.O_RDONLY | os.O_NOFOLLOW, dir_fd=run_fd)
        info = os.fstat(descriptor)
        need(stat.S_ISREG(info.st_mode) and info.st_uid == os.geteuid()
             and stat.S_IMODE(info.st_mode) == 0o600 and info.st_nlink == 1
             and 0 < info.st_size <= cap, 'PRIVATE_READ_METADATA')
        if expected_size is not None:
            need(info.st_size == expected_size and matches_leaf(info, record), 'ARCHIVE_SIZE_IDENTITY')
        active()
        data = os.read(descriptor, cap + 1)
        need(len(data) == info.st_size and len(data) <= cap, 'PRIVATE_READ_LENGTH')
    except BaseException as caught:
        primary = caught
    finally:
        if descriptor is not None:
            try:
                os.close(descriptor)
            except BaseException:
                close_failed = True
    return data, primary, close_failed

def settled_absent():
    if not (result['settled'] and result['all_owned_processes_reaped']
            and result['supervision_error_type'] is None
            and bool(known) and bool(result['known_owned_identities'])):
        return False
    table = guard.process_table()
    return all(pid not in table or table[pid]['start_ticks'] != ticks for pid, ticks in known.items())

operation_stage = 'GITHUB_ARCHIVE_GET'
primary_rejection = 'NONE'
cleanup_rejection = 'NONE'
cleanup_outcome = 'NOT_STARTED'
fd_close_failed = False
exception_code = 'NONE'
record = None
selector = None
try:
    need(settled_absent(), 'UNSETTLED_CAPTURE')
    raw_record, record_error, closed = private_read('archive-identity.safe.json', 1024)
    fd_close_failed |= closed
    if record_error is None:
        try:
            record = json.loads(raw_record.decode('utf-8'))
        except (ValueError, UnicodeDecodeError):
            record = None
    # Capture failure is the primary result even if metadata/cleanup also fails.
    need(result['phase_success'] and result['native_exit'] == 0, 'GH_ARCHIVE_CAPTURE_FAILED')
    if record_error is not None:
        raise record_error
    need(not fd_close_failed, 'PRIVATE_FD_CLOSE_FAILED')
    need(type(record) is dict, 'ARCHIVE_JOURNAL_INVALID')
    operation_stage = 'SELECTOR_SOURCE_LOAD'
    active()
    selector_bytes = (scope / 'selector94.py').read_bytes()
    need(hashlib.sha256(selector_bytes).hexdigest() == grant['selector_sha256'], 'SELECTOR_SOURCE_PIN')
    selector = types.ModuleType('p10_verified_selector')
    selector.__file__ = str(scope / 'selector94.py')
    exec(compile(selector_bytes, selector.__file__, 'exec'), selector.__dict__)
    for path, expected in {**manifest['files'], **manifest['protected_files']}.items():
        need(digest(worktree / path) == expected, 'SOURCE_PIN_AFTER_CAPTURE')
    operation_stage = 'PRIVATE_ARCHIVE_READ'
    archive, archive_error, closed = private_read('original-auth.archive.private.zip', 1048576,
                                                 192040, record)
    fd_close_failed |= closed
    if archive_error is not None:
        raise archive_error
    need(not fd_close_failed, 'PRIVATE_FD_CLOSE_FAILED')
    operation_stage = 'SELECTED_ZIP_READ'
    active()
    body = selector.selected_xml_from_zip(archive)
    operation_stage = 'SELECTED_XML_PROJECTION'
    active()
    projection = selector.selected_xml_projection(body, len((worktree / plan['test_path']).read_text().splitlines()))
    operation_stage = 'PRIVATE_XML_WRITE'
    active()
    descriptor = os.open(run / 'selected-auth.raw.private.xml',
                         os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, 0o600)
    try:
        offset = 0
        while offset < len(body):
            active()
            written = os.write(descriptor, body[offset:offset + 65536])
            need(written > 0, 'PRIVATE_XML_WRITE_FAILED')
            offset += written
    finally:
        try:
            os.close(descriptor)
        except BaseException:
            fd_close_failed = True
    need(not fd_close_failed, 'PRIVATE_FD_CLOSE_FAILED')
    projection.update(artifact_id=11806374316, run_id=38387263916,
                      producer_head=plan['artifact']['head_sha'], actual_checkout=plan['actual_checkout'],
                      selected_class=plan['selected_class'], selected_method=plan['selected_method'],
                      case_status='FAILED', private_selected_xml_retained_unhashed=True,
                      other_members_read_or_extracted=0)
    operation_stage = 'SAFE_PROJECTION_WRITE'
    active()
    write('case-observation.safe.json', projection)
except BaseException as failure:
    exception_code = 'OTHER'
    failure_type = type(failure)
    for known_type, known_code in (
        (ClosedArchiveGate, 'DIAGNOSTIC_BLOCKED'),
        (urllib.error.HTTPError, 'HTTP_ERROR'),
        (urllib.error.URLError, 'URL_ERROR'),
        (ssl.SSLCertVerificationError, 'TLS_CERT_VERIFICATION'),
        (ssl.SSLError, 'TLS_ERROR'), (TimeoutError, 'TIMEOUT'),
        (ConnectionResetError, 'CONNECTION_RESET'), (ConnectionRefusedError, 'CONNECTION_REFUSED'),
        (PermissionError, 'PERMISSION'), (FileNotFoundError, 'FILE_NOT_FOUND'),
        (FileExistsError, 'FILE_EXISTS'), (OSError, 'OS_ERROR'), (ValueError, 'VALUE'),
        (TypeError, 'TYPE'), (KeyError, 'KEY'), (UnicodeDecodeError, 'UNICODE_DECODE'),
        (json.JSONDecodeError, 'JSON_DECODE'), (zipfile.BadZipFile, 'ZIP_ERROR'), (ET.ParseError, 'XML_PARSE'),
    ):
        if failure_type is known_type:
            exception_code = known_code
            break
    if type(failure) is ClosedArchiveGate:
        primary_rejection = failure.code
    elif selector is not None and type(failure) is selector.DiagnosticBlocked and failure.code in selector.PRIMARY_REJECTION_CODES:
        primary_rejection = failure.code
        exception_code = 'DIAGNOSTIC_BLOCKED'
    else:
        primary_rejection = 'BOUNDARY_UNKNOWN'
finally:
    try:
        try:
            info = os.stat('original-auth.archive.private.zip', dir_fd=run_fd, follow_symlinks=False)
        except FileNotFoundError:
            info = None
        if info is None:
            cleanup_outcome = 'ALREADY_ABSENT'
        elif not settled_absent():
            cleanup_outcome = 'RETAINED_UNSETTLED'
            cleanup_rejection = 'ARCHIVE_CLEANUP_UNSETTLED'
        elif not matches_leaf(info, record):
            cleanup_outcome = 'RETAINED_METADATA_MISMATCH'
            cleanup_rejection = 'ARCHIVE_CLEANUP_IDENTITY'
        else:
            need(utc() < stop, 'ARCHIVE_CLEANUP_DEADLINE')
            os.unlink('original-auth.archive.private.zip', dir_fd=run_fd)
            cleanup_outcome = 'REMOVED'
    except BaseException:
        cleanup_outcome = 'RECONCILIATION_REQUIRED'
        cleanup_rejection = 'ARCHIVE_CLEANUP_UNKNOWN'
    lifecycle = {'schema': 'P10_GITHUB_ARCHIVE_LIFECYCLE_V1', 'operation_stage': operation_stage,
                 'exception_code': exception_code, 'http_status': 0,
                 'primary_rejection': primary_rejection, 'cleanup_rejection': cleanup_rejection,
                 'cleanup_outcome': cleanup_outcome, 'fd_close_failed': fd_close_failed,
                 'diagnostic_valid': primary_rejection == 'NONE' and cleanup_rejection == 'NONE' and not fd_close_failed,
                 'capture_native_exit': result['native_exit'], 'request_reconciliation_required': cleanup_rejection != 'NONE',
                 'no_private_message_context_or_values_export': True}
    need(len(json.dumps(lifecycle).encode('utf-8')) <= 1024, 'LIFECYCLE_CAP')
    write('case-observation-lifecycle.safe.json', lifecycle)

result.update(utc=utc().isoformat(), peaks=peaks, expected_native_exit=0,
              native_phase_pass=result['phase_success'], diagnostic_pass=lifecycle['diagnostic_valid'],
              qualification='Original failed-case location only; no test replay or case PASS')
write('terminal.safe.json', result)
if result['settled']:
    shutil.rmtree(run / 'private-tmp')
    (run / 'parent-authority.private.json').unlink()
os.close(run_fd)
print(json.dumps(result), flush=True)
raise SystemExit(0 if result['phase_success'] and lifecycle['diagnostic_valid'] else 1)
