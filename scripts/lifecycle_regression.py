#!/usr/bin/env python3
"""Fixed ordinary regression; no browser stand or retained-cohort prerequisite."""
import argparse
import fcntl
import hashlib
import json
import os
from pathlib import Path
import re
import selectors
import shutil
import signal
import stat
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
ACADEMIC = ':services:academic-service:academic-app:'
SCHEDULE = ':services:schedule-service:schedule-app:'
ATTENDANCE = ':services:attendance-service:attendance-app:'
SELECTION = {
    'unit': {ACADEMIC + 'test': [
        'SemesterServiceTest.createAndUpdate_typedSemesterNamesUseTheExplicitAcademicYear',
        'SemesterServiceTest.educationalSemesterDatesUseMoscowWithoutReplacingSecurityClock',
    ]},
    'service-it': {
        ACADEMIC + 'integrationTest': [
            'SemesterAssignmentAuthorityIT.referencedSemesterDateChangeIsRejectedButNameOnlyUpdateKeepsEligibility',
            'GroupPromotionCycleIT.singleThenMassPromotionRejectsStaleAndRetriedPlansWithoutChangingIdentityOrHistory',
            'SubjectDeletionHttpIT.durableRemoteFailureResumesExactlyOneOwnSubjectAndKeepsOtherSemester',
            'SubjectDeletionHttpIT.localOutboxRefusalRetainsCommittedRemoteReceiptsAndRollsBackLocalCascade',
            'AssignmentReplacementIT.partialCreateExposesExactOwnPendingHandleAndOptionalResumeCommitsExistingTarget',
            'AssignmentReplacementIT.openTupleBlocksNewDifferentTeacherAndCloseButAcceptedCloseReceiptContinues',
            'SemesterArchiveStateIT.getSemesterState_reportsTransitionsReplayAndWriteBlock',
            'SemesterArchiveStateIT.archiveRestoreRecoveryRejectsMismatchedScheduleReceipts',
        ],
        SCHEDULE + 'integrationTest': [
            'AssignmentReplacementServiceIT.exactReplayReturnsDurableReceiptAndAnotherHashCannotReuseOperation',
            'SubjectDeletionParticipantIT.exactCanonicalManifestDeletesOnlySubjectAndReplayHasOneOutboxFact',
            'HomeworkBindingServiceIT.dateCutoffIsNextMidnightInMoscowAndTerminalReplayIsDenied',
        ],
        ATTENDANCE + 'integrationTest': [
            'AttendanceSubjectDeletionIT.mixedAndSoleTicketsExactMarksFilesAndReplayRemainAtomic',
            'AttendanceSubjectDeletionIT.outboxRefusalRollsBackAllMixedCoverageHistoryMarksAndCommitTombstone',
            'LessonTransferParticipantIT.archivePrepareDrainsDelayedTrustedTransferAndEffectBeforeSeal_andStaleReleaseCannotUnsealNextArchive',
        ],
    },
}
CLASS_NAMES = {'AssignmentReplacementIT': 'ru.rutcampustrack.academic.assignment.AssignmentReplacementIT', 'AssignmentReplacementServiceIT': 'ru.rutcampustrack.schedule.integration.AssignmentReplacementServiceIT', 'AttendanceSubjectDeletionIT': 'ru.rutcampustrack.attendance.subjectdeletion.AttendanceSubjectDeletionIT', 'GroupPromotionCycleIT': 'ru.rutcampustrack.academic.group.GroupPromotionCycleIT', 'HomeworkBindingServiceIT': 'ru.rutcampustrack.schedule.grpc.HomeworkBindingServiceIT', 'LessonTransferParticipantIT': 'ru.rutcampustrack.attendance.event.LessonTransferParticipantIT', 'SemesterArchiveStateIT': 'ru.rutcampustrack.academic.integration.SemesterArchiveStateIT', 'SemesterAssignmentAuthorityIT': 'ru.rutcampustrack.academic.semester.SemesterAssignmentAuthorityIT', 'SemesterServiceTest': 'ru.rutcampustrack.academic.semester.SemesterServiceTest', 'SubjectDeletionHttpIT': 'ru.rutcampustrack.academic.subjectdeletion.SubjectDeletionHttpIT', 'SubjectDeletionParticipantIT': 'ru.rutcampustrack.schedule.integration.SubjectDeletionParticipantIT'}

GAPS = {
    'real_admin_reconciliation_outage': 'NOT_RUN',
    'real_application_backup_restore': 'NOT_RUN',
    'crossservice_archive_recovery': 'NOT_RUN',
    'browser_permissions_and_history': 'NOT_RUN',
}

class Refusal(Exception):
    pass

def require(value, code):
    if not value:
        raise Refusal(code)

def write_json(path, value):
    fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, 0o600)
    with os.fdopen(fd, 'w', encoding='utf-8') as out:
        json.dump(value, out, sort_keys=True, indent=2)
        out.write('\n')
        out.flush()
        os.fsync(out.fileno())

def command(level):
    result = ['bash', 'gradlew']
    for task, methods in SELECTION[level].items():
        result.extend([task, '--rerun'])
        for method in methods:
            classname, name = method.split('.', 1)
            result.extend(['--tests', CLASS_NAMES[classname] + '.' + name])
    return result + ['--continue', '--no-daemon', '--max-workers=1', '--console=plain',
                     '--system-prop=org.gradle.java.compile-classpath-packaging=true']

def child_environment():
    environment = dict(os.environ)
    # This documented legacy fixture flag opts into cached images/fixed ports.
    # The ordinary selected regression always uses the default isolated fixtures.
    environment.pop('RCT_GROUP_DELETION_BOUNDED', None)
    return environment

def wrapper_version(root):
    path = root / 'gradle/wrapper/gradle-wrapper.properties'
    require(path.is_file() and not path.is_symlink(), 'WRAPPER_PROPERTIES_REQUIRED')
    value = path.read_text(encoding='utf-8')
    match = re.search(r'^distributionUrl=.*gradle-(\d+)\.(\d+)(?:\.\d+)?-(?:bin|all)\.zip\s*$', value, re.M)
    require(match is not None, 'WRAPPER_VERSION_UNAVAILABLE')
    version = tuple(map(int, match.groups()))
    require(version >= (7, 6), 'TASK_RERUN_REQUIRES_GRADLE_7_6')
    return '.'.join(map(str, version))

def bounded(argv, log, timeout, *, env=None, limit=16 * 1024 * 1024, progress=None):
    """Only our process group is terminated; raw output stays in the private file."""
    started = time.monotonic()
    next_heartbeat = started + 30
    total, reason, code = 0, 'EXIT', None
    fd = os.open(log, os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, 0o600)
    child = None
    group_cleanup_finished = False
    try:
        with os.fdopen(fd, 'wb', buffering=0) as sink:
            child = subprocess.Popen(argv, cwd=ROOT, env=env, stdin=subprocess.DEVNULL,
                                     stdout=subprocess.PIPE, stderr=subprocess.STDOUT, start_new_session=True)
            with selectors.DefaultSelector() as selector:
                selector.register(child.stdout, selectors.EVENT_READ)
                while selector.get_map():
                    if progress == 'GRADLE_SELECTED_TESTS' and time.monotonic() >= next_heartbeat:
                        print(json.dumps({'phase': progress, 'elapsedSeconds': int(time.monotonic()-started), 'timeoutSeconds': timeout}), file=sys.stderr, flush=True)
                        next_heartbeat += 30
                    if time.monotonic() - started >= timeout:
                        reason = 'TIMEOUT'
                        break
                    for key, _ in selector.select(min(0.2, timeout)):
                        chunk = os.read(key.fileobj.fileno(), 65536)
                        if not chunk:
                            selector.unregister(key.fileobj)
                            continue
                        remaining = max(0, limit - total)
                        sink.write(chunk[:remaining])
                        total += len(chunk)
                        if total > limit:
                            reason = 'OUTPUT_BOUND'
                            break
                    if reason == 'OUTPUT_BOUND':
                        break
            if reason != 'EXIT':
                terminate_group(child.pid)
                group_cleanup_finished = True
                try:
                    code = child.wait(timeout=5)
                except subprocess.TimeoutExpired:
                    pass
            else:
                code = child.wait(timeout=5)
    except BaseException:
        if child is not None:
            try:
                if not group_cleanup_finished:
                    terminate_group(child.pid)
                child.wait(timeout=5)
            except (OSError, subprocess.TimeoutExpired):
                pass
        raise
    finally:
        if child is not None and child.stdout is not None:
            child.stdout.close()
    return {'reason': reason, 'exitCode': code, 'timeoutSeconds': timeout,
            'elapsedMs': int((time.monotonic() - started) * 1000), 'outputBoundBytes': limit,
            'outputBytes': min(total, limit), 'pass': reason == 'EXIT' and code == 0}

def kill_group(pid, sig):
    try:
        os.killpg(pid, sig)
    except ProcessLookupError:
        pass

def terminate_group(pgid):
    # The session leader may already have exited while its descendants still run.
    # Give the whole owned group a bounded grace, then always escalate that PGID.
    try:
        kill_group(pgid, signal.SIGTERM)
        time.sleep(5)
    finally:
        kill_group(pgid, signal.SIGKILL)

def log_categories(path):
    # Search a bounded private log, export only these authored finite categories.
    data = path.read_bytes()[:16 * 1024 * 1024]
    patterns = {
        'COMPILATION_FAILURE': rb'(?:Compilation failed|compileJava FAILED|cannot find symbol)',
        'DEPENDENCY_RESOLUTION_FAILURE': rb'(?:Could not resolve all|Could not resolve |Could not download )',
        'DOCKER_UNAVAILABLE': rb'(?:Could not find a valid Docker environment|Cannot connect to the Docker daemon|Docker environment should be available)',
        'CONTAINER_IMAGE_PULL_FAILURE': rb'(?:Could not pull image|pull access denied|Failed to pull image|ContainerFetchException)',
        'CONTAINER_START_FAILURE': rb'(?:ContainerLaunchException|Could not start container|Timed out waiting for container)',
        'NO_MATCHING_TESTS': rb'(?:No tests found for given includes|No matching tests found)',
        'GRADLE_TASK_FAILURE': rb'(?:Execution failed for task|BUILD FAILED)',
    }
    return [code for code, pattern in patterns.items() if re.search(pattern, data)]

def resource_gate(out):
    # Host observations only; never start, stop, exec or connect to a product container.
    require(os.environ.get('TESTCONTAINERS_RYUK_DISABLED', '').lower() not in {'true', '1'}, 'TESTCONTAINERS_CLEANUP_REQUIRED')
    require(not any(os.environ.get(key) for key in ('DOCKER_CONTEXT', 'DOCKER_TLS', 'DOCKER_TLS_VERIFY', 'DOCKER_CERT_PATH')), 'LOCAL_TESTCONTAINERS_BINDING_REQUIRED')
    host = os.environ.get('DOCKER_HOST', 'unix:///var/run/docker.sock')
    require(re.fullmatch(r'unix:///[A-Za-z0-9_./-]+', host) and '..' not in Path(host[7:]).parts, 'LOCAL_TESTCONTAINERS_UNIX_BINDING_REQUIRED')
    os.environ['DOCKER_HOST'] = host
    mem = re.search(r'^MemAvailable:\s+(\d+)\s+kB$', Path('/proc/meminfo').read_text(), re.M)
    memory = int(mem.group(1)) * 1024 if mem else 0
    disk = shutil.disk_usage(ROOT).free
    cpus = os.cpu_count() or 0
    observed = {'memoryAvailableBytes': memory, 'diskAvailableBytes': disk, 'cpus': cpus,
                'requiredMemoryBytes': 4 * 1024**3, 'requiredDiskBytes': 8 * 1024**3, 'requiredCpus': 2}
    write_json(out / 'resource.safe.json', observed)
    require(memory >= observed['requiredMemoryBytes'] and disk >= observed['requiredDiskBytes'] and cpus >= 2, 'INSUFFICIENT_IT_RESOURCES')
    native = bounded(['docker', 'info', '--format', '{{json .ServerVersion}}'], out / 'docker-info.private.log', 15, limit=65536)
    write_json(out / 'docker-info.safe.json', native)
    require(native['pass'], 'DOCKER_DAEMON_UNAVAILABLE')

def xml_results(root, level, started_ns):
    rows, problems, class_diagnostics = [], [], []
    for task, methods in SELECTION[level].items():
        segments = task.strip(':').split(':')
        directory = root.joinpath(*segments[:-1], 'build/test-results', segments[-1])
        expected = set(methods)
        found = {}
        selected_classes = {identity.split('.', 1)[0] for identity in methods}
        paths = [directory / ('TEST-' + CLASS_NAMES[name] + '.xml') for name in sorted(selected_classes)]
        for path in paths:
            if not path.exists():
                continue
            try:
                require(not path.is_symlink() and path.stat().st_size <= 4 * 1024 * 1024, 'XML_UNSAFE_OR_OVERSIZE')
                data = path.read_bytes()
                require(b'<!DOCTYPE' not in data.upper() and b'<!ENTITY' not in data.upper(), 'XML_ENTITY_REFUSED')
                document = ET.fromstring(data)
            except (OSError, ET.ParseError, Refusal) as error:
                code = str(error) if isinstance(error, Refusal) else 'XML_PARSE_ERROR' if isinstance(error, ET.ParseError) else 'XML_UNAVAILABLE'
                problems.append(code)
                class_diagnostics.append({'class': path.name[5:-4], 'code': code})
                continue
            for case in document.iter('testcase'):
                fullclass = case.attrib.get('classname', '')
                classname = fullclass.rsplit('.', 1)[-1]
                name = case.attrib.get('name', '')
                name = name[:-2] if name.endswith('()') else name
                identity = classname + '.' + name
                if identity not in expected or CLASS_NAMES.get(classname) != fullclass:
                    # Task --tests is exact; unexpected fresh execution must never be counted as selected coverage.
                    if path.stat().st_mtime_ns >= started_ns:
                        problems.append('UNEXPECTED_FRESH_TESTCASE')
                    continue
                if path.stat().st_mtime_ns < started_ns:
                    problems.append('STALE_SELECTED_XML')
                    continue
                state = ('SKIP' if case.find('skipped') is not None else
                         'FAIL' if case.find('failure') is not None or case.find('error') is not None else 'PASS')
                failures = [node for tag in ('failure', 'error') for node in case.findall(tag)]
                evidence = []
                for failure in failures[:2]:
                    kind = failure.attrib.get('type', '')
                    kind = kind if kind in {'java.lang.AssertionError', 'java.lang.IllegalStateException', 'java.lang.IllegalArgumentException', 'java.lang.NullPointerException', 'org.opentest4j.AssertionFailedError', 'org.testcontainers.containers.ContainerLaunchException', 'org.testcontainers.containers.ContainerFetchException', 'java.util.concurrent.TimeoutException'} else 'OTHER'
                    # Only selected public Java source frames, no exception messages or arbitrary paths.
                    frames = re.findall(r'\bat\s+([\w.$]+)\.([\w$]+)\(([A-Za-z_$][\w$]*\.java):(\d+)\)', failure.text or '')
                    safe_frames = [{'class': fqcn, 'method': method, 'file': file, 'line': int(line)}
                                   for fqcn, method, file, line in frames
                                   if fqcn in CLASS_NAMES.values() and fqcn.rsplit('.', 1)[-1] + '.' + method in expected and file == fqcn.rsplit('.', 1)[-1] + '.java' and 0 < int(line) < 100000][:3]
                    evidence.append({'errorClass': kind, 'sourceFrames': safe_frames})
                found.setdefault(identity, []).append({'status': state, 'failureEvidence': evidence})
        for identity in methods:
            states = found.get(identity, [])
            status = states[0]['status'] if len(states) == 1 else 'MISSING' if not states else 'DUPLICATE'
            rows.append({'test': identity, 'status': status, 'failureEvidence': states[0]['failureEvidence'] if len(states) == 1 else []})
            if status != 'PASS':
                problems.append('SELECTED_XML_' + status)
    return {'tests': rows, 'expected': len(rows), 'passed': sum(row['status'] == 'PASS' for row in rows),
            'skipped': sum(row['status'] == 'SKIP' for row in rows), 'problems': sorted(set(problems)), 'classDiagnostics': class_diagnostics,
            'pass': not problems and bool(rows)}

def run(level, out):
    require(sys.platform == 'linux', 'LINUX_OWNER_REQUIRED')
    require(out.is_absolute() and not out.exists() and out.parent.is_dir() and not out.parent.is_symlink(), 'EXCLUSIVE_OUTPUT_REQUIRED')
    os.umask(0o077)
    out.mkdir(mode=0o700)
    output_stat = out.lstat()
    require(stat.S_ISDIR(output_stat.st_mode) and stat.S_IMODE(output_stat.st_mode) == 0o700 and output_stat.st_uid == os.getuid(), 'PRIVATE_OUTPUT_FILESYSTEM_REQUIRED')
    result = {'schema': 'RCT_ORDINARY_LIFECYCLE_REGRESSION_V1', 'level': level, 'status': 'FAIL',
              'operationalGaps': GAPS, 'native': None, 'xml': None, 'stage': 'PREFLIGHT', 'failure': None}
    try:
        result['gradleVersion'] = wrapper_version(ROOT)
        lock_path = ROOT / '.gradle/rct-lifecycle-regression.lock'
        lock_path.parent.mkdir(exist_ok=True)
        fd = os.open(lock_path, os.O_RDWR | os.O_CREAT | os.O_NOFOLLOW, 0o600)
        with os.fdopen(fd, 'r+') as lease:
            lock_stat = os.fstat(lease.fileno())
            require(stat.S_ISREG(lock_stat.st_mode) and lock_stat.st_uid == os.getuid() and stat.S_IMODE(lock_stat.st_mode) == 0o600 and lock_stat.st_nlink == 1, 'HEAVY_LEASE_OWNER')
            fcntl.flock(lease, fcntl.LOCK_EX | fcntl.LOCK_NB)
            if level == 'service-it':
                result['stage'] = 'IT_RESOURCE_GATE'
                resource_gate(out)
            result['stage'] = 'GRADLE_SELECTED_TESTS'
            print(json.dumps({'phase': result['stage'], 'level': level, 'expected': sum(map(len, SELECTION[level].values())), 'privateLog': str(out/'gradle.private.log')}), file=sys.stderr, flush=True)
            started_ns = time.time_ns()
            result['legacyBoundedProfileSuppressed'] = 'RCT_GROUP_DELETION_BOUNDED' in os.environ
            result['native'] = bounded(command(level), out / 'gradle.private.log', 600 if level == 'unit' else 1800, env=child_environment(), progress='GRADLE_SELECTED_TESTS')
            result['nativeCategories'] = log_categories(out / 'gradle.private.log')
            result['stage'] = 'FRESH_JUNIT_XML'
            result['xml'] = xml_results(ROOT, level, started_ns)
            if result['native']['pass'] and result['xml']['pass']:
                result['status'] = 'PASS'
            else:
                result['failure'] = 'NATIVE_OR_EXACT_XML_FAILURE'
    except BaseException as error:
        result['failure'] = str(error) if isinstance(error, Refusal) else ('LEASE_BUSY' if isinstance(error, BlockingIOError) else 'INTERRUPTED' if isinstance(error, KeyboardInterrupt) else type(error).__name__ if type(error).__name__ in {'OSError', 'ParseError', 'ValueError', 'TimeoutExpired'} else 'OTHER')
    write_json(out / 'result.safe.json', result)
    print(json.dumps(result, sort_keys=True))
    return 0 if result['status'] == 'PASS' else 130 if result['failure'] == 'INTERRUPTED' else 1

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('level', choices=SELECTION)
    parser.add_argument('--out', required=True, type=Path)
    args = parser.parse_args()
    return run(args.level, args.out)

if __name__ == '__main__':
    signal.signal(signal.SIGTERM, lambda *_: (_ for _ in ()).throw(KeyboardInterrupt()))
    try:
        sys.exit(main())
    except BaseException as error:
        if isinstance(error, SystemExit):
            raise
        print(json.dumps({'status': 'HOLD', 'failure': str(error) if isinstance(error, Refusal) else 'ENTRY_FAILURE'}))
        sys.exit(1)
