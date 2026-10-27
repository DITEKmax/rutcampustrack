#!/usr/bin/env python3
"""Seven ordered local acceptance stories; original assertions and recovery tools remain authoritative."""
import datetime
import hashlib
import json
import os
from pathlib import Path
import re
import selectors
import signal
import stat
import subprocess
import time
import uuid
from zoneinfo import ZoneInfo

def private_new(path, data):
    fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, 0o600)
    with os.fdopen(fd, 'wb') as stream:
        stream.write(data)


def reap_owned_child(runner, process, *, finished):
    if not finished:
        try:
            os.killpg(process.pid, signal.SIGTERM)
        except ProcessLookupError:
            pass
        try:
            process.wait(timeout=45)
        except subprocess.TimeoutExpired:
            pass
        # The direct CLI can exit before its descendants; reap its owned group too.
        try:
            os.killpg(process.pid, signal.SIGKILL)
        except ProcessLookupError:
            pass
    process.wait(timeout=10)
    if getattr(runner, 'child', None) is process:
        runner.child = None


def native_action(argv):
    if argv[:2] == ['python3', 'scripts/recovery.py']:
        return {'backup': 'DATABASE_BACKUP', 'restore': 'DATABASE_RESTORE'}.get(argv[2], 'OTHER')
    if argv[:2] == ['python3', 'scripts/test-application-recovery.py']:
        return {'capture': 'TRANSFER_CAPTURE', 'verify': 'TRANSFER_VERIFY'}.get(argv[2], 'OTHER')
    if argv[:2] == ['docker', 'inspect']:
        return 'RESOURCE_INSPECT'
    if argv[:2] == ['docker', 'logs']:
        return 'SERVICE_LOG'
    if argv[:3] == ['docker', 'network', 'inspect']:
        return 'NETWORK_INSPECT'
    if argv[:3] == ['docker', 'network', 'disconnect']:
        return 'SOURCE_ENDPOINT_DISCONNECT'
    if argv[:2] == ['docker', 'compose']:
        return next(({'config': 'COMPOSE_CONFIG', 'ps': 'COMPOSE_IDENTIFY', 'up': 'COMPOSE_START',
                      'stop': 'COMPOSE_STOP'}[arg] for arg in argv[2:] if arg in {'config', 'ps', 'up', 'stop'}), 'OTHER')
    return 'OTHER'


def logged_run(runner, context, argv, *, cwd=None, env=None, timeout=180,
               stdout=None, stderr=None, check=False):
    """Bound both pipes while retaining every command's private output before errors."""
    directory = Path(context['laneRun']) / 'native-logs'
    directory.mkdir(mode=0o700, exist_ok=True)
    require(not directory.is_symlink() and stat.S_IMODE(directory.stat().st_mode) == 0o700,
            'LIFECYCLE_LOG_DIRECTORY')
    sequence = getattr(runner, '_lifecycle_command_sequence', 0) + 1
    runner._lifecycle_command_sequence = sequence
    path = directory / ('%04d.private.log' % sequence)
    receipt = directory / ('%04d.safe.json' % sequence)
    limit = 16 * 1024 * 1024
    buffers = {'stdout': bytearray(), 'stderr': bytearray()}
    total = 0
    process = None
    finished = False
    failure = None
    code = None
    cancelled = False
    previous_handlers = {}

    def defer_signal(*_):
        nonlocal cancelled
        cancelled = True

    action = native_action(argv)
    fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, 0o600)
    try:
        # Non-raising handlers precede spawn: cancellation cannot escape between
        # Popen creating a new session and our registration of the owned child.
        for kind in (signal.SIGTERM, signal.SIGINT):
            previous_handlers[kind] = signal.signal(kind, defer_signal)
        with os.fdopen(fd, 'wb') as log:
            require(getattr(runner, 'child', None) is None, 'LIFECYCLE_CHILD_ALREADY_OWNED')
            primary_error = None
            try:
                process = subprocess.Popen(argv, cwd=cwd or context['root'], env=env or runner.env,
                                           stdin=subprocess.DEVNULL, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                                           start_new_session=True)
                runner.child = process
                if cancelled:
                    raise KeyboardInterrupt()
                deadline = time.monotonic() + timeout
                heartbeat = time.monotonic() + 15
                with selectors.DefaultSelector() as ready:
                    ready.register(process.stdout, selectors.EVENT_READ, 'stdout')
                    ready.register(process.stderr, selectors.EVENT_READ, 'stderr')
                    while ready.get_map() or process.poll() is None:
                        if cancelled:
                            raise KeyboardInterrupt()
                        remaining = deadline - time.monotonic()
                        if remaining <= 0:
                            raise subprocess.TimeoutExpired(argv, timeout)
                        if time.monotonic() >= heartbeat:
                            print(json.dumps({'phase': 'LIFECYCLE_NATIVE_COMMAND', 'state': 'RUNNING',
                                              'action': action}), flush=True)
                            heartbeat = time.monotonic() + 15
                        for key, _ in ready.select(min(remaining, 0.25)):
                            block = os.read(key.fileobj.fileno(), min(65536, limit - total + 1))
                            if not block:
                                ready.unregister(key.fileobj)
                                continue
                            allowed = min(len(block), limit - total)
                            log.write(block[:allowed])
                            buffers[key.data].extend(block[:allowed])
                            total += allowed
                            if len(block) > allowed:
                                raise RuntimeError('LIFECYCLE_OUTPUT_BOUND')
                    code = process.wait(timeout=max(0.001, deadline - time.monotonic()))
                    finished = True
            except BaseException as error:
                primary_error = error
                raise
            finally:
                try:
                    if process is not None:
                        reap_owned_child(runner, process, finished=finished)
                    log.flush()
                    os.fsync(log.fileno())
                except BaseException:
                    if primary_error is None:
                        raise
        result = subprocess.CompletedProcess(argv, code,
                    path.read_bytes() if stderr == subprocess.STDOUT else bytes(buffers['stdout']),
                    b'' if stderr == subprocess.STDOUT else bytes(buffers['stderr']))
        if check and code:
            raise subprocess.CalledProcessError(code, argv)
        return result
    except BaseException as error:
        if cancelled:
            failure = 'KeyboardInterrupt'
            raise KeyboardInterrupt() from None
        failure = type(error).__name__ if type(error).__name__ in {
            'TimeoutExpired', 'CalledProcessError', 'OSError', 'FileNotFoundError', 'PermissionError',
            'RuntimeError', 'KeyboardInterrupt'} else 'OTHER'
        raise
    finally:
        try:
            if cancelled and failure is None:
                failure = 'KeyboardInterrupt'
            try:
                private_new(receipt, (json.dumps({'action': action, 'exitStatus': code, 'errorClass': failure,
                                                 'bytes': total}, sort_keys=True) + '\n').encode('ascii'))
            except OSError:
                if failure is None:
                    raise
        finally:
            for kind, handler in previous_handlers.items():
                signal.signal(kind, handler)
        if cancelled:
            raise KeyboardInterrupt() from None


def require(condition, code):
    if not condition:
        raise RuntimeError(code)


def owned_read(path, maximum, mode=0o644):
    before = path.lstat()
    fd = os.open(path, os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK)
    with os.fdopen(fd, 'rb') as stream:
        info = os.fstat(stream.fileno())
        identity = lambda value: (value.st_dev, value.st_ino, value.st_mode, value.st_uid,
                                  value.st_size, value.st_mtime_ns, value.st_ctime_ns)
        require(stat.S_ISREG(info.st_mode) and info.st_uid == os.getuid()
                and stat.S_IMODE(info.st_mode) == mode and 0 < info.st_size <= maximum
                and identity(before) == identity(info), 'LIFECYCLE_OWNED_FILE')
        data = stream.read(maximum + 1)
        require(len(data) == info.st_size and identity(info) == identity(os.fstat(stream.fileno()))
                and identity(info) == identity(path.lstat()), 'LIFECYCLE_FILE_CHANGED')
        return data, identity(info)


def clock_snapshot(context):
    directory = Path(context['run']) / 'clock'
    info = directory.lstat()
    require(stat.S_ISDIR(info.st_mode) and not directory.is_symlink()
            and info.st_uid == os.getuid() and stat.S_IMODE(info.st_mode) == 0o755,
            'LIFECYCLE_CLOCK_DIRECTORY')
    marker = owned_read(directory / 'disposable-target', 64)
    require(marker[0] == b'RCT_E2E_DISPOSABLE\n', 'LIFECYCLE_CLOCK_MARKER')
    clock = owned_read(directory / 'current-instant', 128)
    require(re.fullmatch(rb'\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,3})?Z\n?', clock[0]),
            'LIFECYCLE_CLOCK_INSTANT')
    return (info.st_dev, info.st_ino, info.st_mode, info.st_uid), marker, clock


def next_teaching_instant(current, natural):
    moscow = ZoneInfo('Europe/Moscow')
    date = max(current.astimezone(moscow).date(), natural.astimezone(moscow).date()) + datetime.timedelta(days=1)
    while date.weekday() >= 5:
        date += datetime.timedelta(days=1)
    future = datetime.datetime.combine(date, datetime.time(9), moscow).astimezone(datetime.timezone.utc)
    require(future > current, 'LIFECYCLE_CLOCK_NOT_FORWARD')
    return future


def advance_to_next_teaching_day(runner):
    context = runner.special_context('semester-reconciliation', 'main')
    original = clock_snapshot(context)
    current = datetime.datetime.fromisoformat(original[2][0].decode().strip().replace('Z', '+00:00'))
    future = next_teaching_instant(current, datetime.datetime.now(datetime.timezone.utc))
    updated = (future.isoformat(timespec='milliseconds').replace('+00:00', 'Z') + '\n').encode('ascii')
    directory = Path(context['run']) / 'clock'
    temporary = directory / ('lifecycle-' + uuid.uuid4().hex)
    intent = runner.special_intent('semester-reconciliation', 'main', 'CLOCK_ADVANCE', [], {})
    fd = os.open(temporary, os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, 0o644)
    try:
        with os.fdopen(fd, 'wb') as stream:
            os.fchmod(stream.fileno(), 0o644)
            stream.write(updated)
            stream.flush()
            os.fsync(stream.fileno())
        require(clock_snapshot(context) == original, 'LIFECYCLE_CLOCK_CHANGED')
        os.replace(temporary, directory / 'current-instant')
        require(owned_read(directory / 'current-instant', 128)[0] == updated, 'LIFECYCLE_CLOCK_PUBLICATION')
        runner.special_outcome(intent, True, restored=True)
    except Exception:
        runner.special_outcome(intent, False, restored=False)
        raise
    finally:
        if temporary.exists():
            temporary.unlink()


def same_service_networks(current, original, *, stopped, verified_networks=None):
    """Planned stop/start preserves bindings; Docker recreates EndpointID/SandboxID.

    Canonical CI assignment lines 4074–4128 pins the same container/image/clock.
    Root's explicit 2026-10-27 decision pins the exact binding and stopped shape.
    A planned restored service may use only core's concrete authoritative map:
    core validates the same original ID/image/config/mounts/network ID/aliases,
    healthy state and Docker network-inspect ID-to-IP/prefix binding under the
    exact open intent. Actual Networks must equal that entire returned map.
    """
    require(set(current) == set(original), 'LIFECYCLE_NETWORK_SET_CHANGED')
    if verified_networks is not None:
        require(not stopped and current == verified_networks, 'LIFECYCLE_VERIFIED_NETWORK_MAP_MISMATCH')
    for name, before in original.items():
        after = current[name]
        for key in ('NetworkID', 'Aliases'):
            require(after.get(key) == before.get(key), 'LIFECYCLE_NETWORK_BINDING_CHANGED')
        for key in ('IPAddress', 'IPPrefixLen', 'Gateway', 'GlobalIPv6Address', 'GlobalIPv6PrefixLen', 'IPv6Gateway'):
            if stopped:
                require(after.get(key) in ('', 0, None), 'LIFECYCLE_STOPPED_ENDPOINT_NOT_EMPTY')
            else:
                expected = verified_networks[name] if verified_networks is not None else before
                require(after.get(key) == expected.get(key), 'LIFECYCLE_RESTORED_ADDRESS_CHANGED')
        if stopped:
            require(after.get('EndpointID') in ('', None), 'LIFECYCLE_STOPPED_ENDPOINT_NOT_EMPTY')


def service_info(runner, context, service, *, pin=None, stopped=False, planned_restart=False):
    result = logged_run(runner, context, ['docker', 'compose', '-p', context['project'], '-f', context['composeFile'],
                             'ps', '--all', '-q', service], cwd=context['root'], env=runner.env,
                            stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, timeout=15, check=True)
    cid = result.stdout.decode('ascii').strip()
    require(re.fullmatch('[0-9a-f]{64}', cid), 'LIFECYCLE_SERVICE_ID')
    result = logged_run(runner, context, ['docker', 'inspect', cid], cwd=context['root'], env=runner.env,
                            stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, timeout=15, check=True)
    require(len(result.stdout) <= 262144, 'LIFECYCLE_INSPECTION_BOUND')
    info = json.loads(result.stdout)[0]
    labels = info['Config']['Labels']
    require(info['Id'] == cid and labels.get('com.docker.compose.project') == context['project']
            and labels.get('com.docker.compose.service') == service
            and labels.get('rct.local.stage17') == context['nonce'], 'LIFECYCLE_FOREIGN_SERVICE')
    if pin:
        for key in ('Id', 'Image', 'Mounts'):
            require(info[key] == pin[key], 'LIFECYCLE_SERVICE_CHANGED')
        require(info['Config'] == pin['Config'], 'LIFECYCLE_SERVICE_IDENTITY_CHANGED')
        verified = runner.special_verified_networks(service, cid) if planned_restart and not stopped else None
        same_service_networks(info['NetworkSettings']['Networks'], pin['NetworkSettings']['Networks'],
                              stopped=stopped, verified_networks=verified)
    return info


def healthy(info):
    return info['State']['Running'] and info['State'].get('Health', {}).get('Status') == 'healthy'


def recovery_python_environment(runner, context):
    certificate = Path(context['certsDir']) / 'server.crt'
    certificate_bytes = owned_read(certificate, 128 * 1024)[0]
    require(hashlib.sha256(certificate_bytes).hexdigest() == runner.special_value()['node_ca_sha256']
            and runner.env.get('NODE_EXTRA_CA_CERTS') == str(certificate), 'RECOVERY_PYTHON_CA_IDENTITY')
    return {**runner.env, 'SSL_CERT_FILE': str(certificate)}


def execute_assignment(runner):
    context = runner.special_context('assignment', 'prepare')
    work = Path(context['laneRun'])
    private_new(work / 'disposable-target', b'RCT_ASSIGNMENT_LIFECYCLE_DISPOSABLE\n')
    pin = service_info(runner, context, 'schedule-service')
    require(healthy(pin), 'ASSIGNMENT_SCHEDULE_NOT_HEALTHY')
    original_clock = clock_snapshot(context)
    env = {'RCT_ASSIGNMENT_LIFECYCLE_RUN': str(work)}
    outcome = runner.special_phase('assignment', 'prepare', {**env, 'RCT_ASSIGNMENT_LIFECYCLE_PHASE': 'prepare'})
    require(clock_snapshot(context) == original_clock, 'ASSIGNMENT_CLOCK_CHANGED')
    if outcome['status'] != 'PASS':
        runner.special_health()
        return outcome['status']
    intent = runner.special_intent('assignment', 'pending', 'ASSIGNMENT_RESTART', ['schedule-service'], {})
    compose = ['docker', 'compose', '-p', context['project'], '-f', context['composeFile']]
    restart_needed = True
    result = 'FAIL'
    restored = False

    def restart(suffix):
        # The exact same deadline is consumed by Docker HTTP health and TS RPC readiness.
        deadline = int(time.time() * 1000) + 120000
        runner.command('SPECIAL_ASSIGNMENT_RESTART_' + suffix, [*compose, 'up', '-d', '--no-build',
                       '--no-recreate', '--no-deps', '--pull', 'never', '--wait', '--wait-timeout', '120', 'schedule-service'])
        require(healthy(service_info(runner, context, 'schedule-service', pin=pin, planned_restart=True)), 'ASSIGNMENT_RESTART_IDENTITY')
        require(time.time() * 1000 < deadline, 'ASSIGNMENT_RESTART_DEADLINE')
        return deadline

    try:
        runner.command('SPECIAL_ASSIGNMENT_STOP', [*compose, 'stop', '--timeout', '15', 'schedule-service'])
        require(not service_info(runner, context, 'schedule-service', pin=pin, stopped=True)['State']['Running'], 'ASSIGNMENT_NOT_STOPPED')
        outcome = runner.special_phase('assignment', 'pending', {**env, 'RCT_ASSIGNMENT_LIFECYCLE_PHASE': 'pending'})
        require(clock_snapshot(context) == original_clock, 'ASSIGNMENT_CLOCK_CHANGED')
        deadline = restart('PRIMARY')
        restart_needed = False
        if outcome['status'] == 'PASS':
            outcome = runner.special_phase('assignment', 'resume', {**env, 'RCT_ASSIGNMENT_LIFECYCLE_PHASE': 'resume',
                                             'RCT_ASSIGNMENT_RESTART_READY_UNTIL_MS': str(deadline)})
            result = outcome['status']
    finally:
        try:
            if restart_needed:
                restart('RESTORATION')
            require(healthy(service_info(runner, context, 'schedule-service', pin=pin, planned_restart=True))
                    and clock_snapshot(context) == original_clock, 'ASSIGNMENT_RESTORATION_UNVERIFIED')
            restored = True
        finally:
            runner.special_outcome(intent, result == 'PASS', restored=restored)
    runner.special_health()
    return result


def _execute_lifecycle_serial(runner, results):
    """Serial original logical stories; caller aggregates phase outcomes by lane."""
    for lane in ('semester-metadata', 'subject-deletion'):
        runner._lifecycle_active_lane = lane
        context = runner.special_context(lane, 'main')
        private_new(Path(context['laneRun']) / 'disposable-target', b'RCT_SEMESTER_DISPOSABLE\n')
        outcome = runner.special_phase(lane, 'main', {'RCT_SEMESTER_LIFECYCLE_RUN': context['laneRun']})
        results[lane] = outcome['status']
        runner.special_health()
    runner._lifecycle_active_lane = 'assignment'
    results['assignment'] = execute_assignment(runner)
    runner._lifecycle_active_lane = 'today'
    context = runner.special_context('today', 'main')
    intent = runner.special_intent('today', 'main', 'CLOCK_ADVANCE', [], {})
    before = clock_snapshot(context)
    outcome = runner.special_phase('today', 'main', {'RCT_E2E_GEO_RUN': context['laneRun']})
    after = clock_snapshot(context)
    start = datetime.datetime.fromisoformat(before[2][0].decode().strip().replace('Z', '+00:00'))
    end = datetime.datetime.fromisoformat(after[2][0].decode().strip().replace('Z', '+00:00'))
    valid = start.hour == 6 and start.minute == 0 and end == start + datetime.timedelta(hours=1)
    runner.special_outcome(intent, outcome['status'] == 'PASS' and valid, restored=valid)
    require(valid, 'TODAY_CLOCK_TRANSITION_UNVERIFIED')
    results['today'] = outcome['status']
    runner.special_health()
    runner._lifecycle_active_lane = 'semester-reconciliation'
    advance_to_next_teaching_day(runner)
    context = runner.special_context('semester-reconciliation', 'main')
    private_new(Path(context['laneRun']) / 'disposable-target', b'RCT_SEMESTER_DISPOSABLE\n')
    intent = runner.special_intent('semester-reconciliation', 'main', 'RECONCILIATION_OUTAGE',
                                   ['schedule-service', 'attendance-service'], {})
    outcome = runner.special_phase('semester-reconciliation', 'main', {'RCT_SEMESTER_LIFECYCLE_RUN': context['laneRun']})
    receipt = json.loads(owned_read(Path(context['laneRun']) / 'reconciliation.safe.json', 4096, 0o600)[0])
    restored = receipt.get('restorationSucceeded') is True
    runner.special_outcome(intent, outcome['status'] == 'PASS', restored=restored)
    require(restored, 'RECONCILIATION_RESTORATION_UNVERIFIED')
    runner.special_health()
    results['semester-reconciliation'] = outcome['status']
    # Reconciliation is clock-immutable; recovery starts on the same fresh eligible 09:00.
    runner._lifecycle_active_lane = 'application-recovery'
    results['application-recovery'] = execute_recovery(runner)
    require(results['application-recovery'] == 'PASS', 'RECOVERY_DEPENDENT_ARCHIVE_BLOCKED')
    runner.special_health()
    runner._lifecycle_active_lane = 'semester-archive'
    context = runner.special_context('semester-archive', 'main')
    recovery_context = runner.special_context('application-recovery', 'attachment')
    private_new(Path(context['laneRun']) / 'disposable-target', b'RCT_SEMESTER_DISPOSABLE\n')
    outcome = runner.special_phase('semester-archive', 'main', {'RCT_SEMESTER_LIFECYCLE_RUN': context['laneRun'],
                        'RCT_APP_RECOVERY_RUN': recovery_context['laneRun'], 'RCT_APP_RECOVERY_PROJECT': context['recoveryProject']})
    runner.special_health()
    results['semester-archive'] = outcome['status']
    return results


def lifecycle_reporting_directory(runner, lane, phases):
    """Resolve only the launcher's frozen private path; never re-admit live resources."""
    allowed = {'semester-metadata', 'subject-deletion', 'assignment', 'today',
               'semester-reconciliation', 'application-recovery', 'semester-archive'}
    expected = ('prepare', 'pending', 'resume') if lane == 'assignment' else (
               ('seed', 'pending', 'admit', 'attachment') if lane == 'application-recovery' else ('main',))
    require(lane in allowed and tuple(phases) == expected
            and re.fullmatch('[0-9a-f]{24}', runner.nonce), 'LIFECYCLE_REPORT_LANE_BINDING')
    run = Path(runner.run)
    require(run.is_absolute() and run.resolve() == run
            and run.name == runner.project == 'rct-local-stage17-' + runner.nonce[:12],
            'LIFECYCLE_REPORT_RUN_BINDING')
    directory = run / 'lanes' / lane
    paths = (run, run / 'lanes', directory, directory / 'artifacts',
             *(directory / 'artifacts' / phase for phase in phases if phase != 'main'))
    for path in paths:
        if path != run:
            try:
                path.mkdir(mode=0o700)
            except FileExistsError:
                pass
        info = path.lstat()
        require(stat.S_ISDIR(info.st_mode) and not path.is_symlink()
                and info.st_uid == os.getuid() and stat.S_IMODE(info.st_mode) == 0o700,
                'LIFECYCLE_REPORT_DIRECTORY')
    return directory


def execute_lifecycle(runner):
    """Keep exactly seven logical rows even when an unsafe continuation is blocked."""
    lanes = ('semester-metadata', 'subject-deletion', 'assignment', 'today',
             'semester-reconciliation', 'application-recovery', 'semester-archive')
    results = {}
    failure_class = None
    try:
        _execute_lifecycle_serial(runner, results)
    except Exception as error:
        failure_class = type(error).__name__ if type(error).__name__ in {
            'RuntimeError', 'AdmissionError', 'TimeoutExpired', 'CalledProcessError',
            'FileNotFoundError', 'PermissionError', 'JSONDecodeError'} else 'OTHER'
        active = getattr(runner, '_lifecycle_active_lane', lanes[0])
        results[active] = 'FAIL'
    for lane in lanes:
        status = results.get(lane, 'BLOCKED')
        phases = ('prepare', 'pending', 'resume') if lane == 'assignment' else (
                 ('seed', 'pending', 'admit', 'attachment') if lane == 'application-recovery' else ('main',))
        directory = None
        reporting_error = False
        phase_results = [{'phase': phase, 'status': 'NOT_RUN'} for phase in phases]
        try:
            directory = lifecycle_reporting_directory(runner, lane, phases)
            for phase_result in phase_results:
                path = directory / (phase_result['phase'] + '.result.safe.json')
                if path.exists():
                    outcome = json.loads(owned_read(path, 65536, 0o600)[0])
                    require(outcome['status'] in {'PASS', 'FAIL', 'NOT_RUN', 'BLOCKED'}, 'LIFECYCLE_PHASE_REPORT_STATUS')
                    phase_result['status'] = outcome['status']
        except (OSError, RuntimeError, ValueError, KeyError, TypeError):
            reporting_error = True
            if status != 'BLOCKED':
                status = 'FAIL'
        row = {'lane': lane, 'status': status, 'expected': 1, 'passed': int(status == 'PASS'),
               'failed': int(status == 'FAIL'), 'notRun': int(status in {'NOT_RUN', 'BLOCKED'}),
               'phases': phase_results}
        if status == 'FAIL' and failure_class:
            row['errorClass'] = failure_class
        if reporting_error:
            row['reporting'] = 'UNAVAILABLE'
        runner.special_outcomes.append(row)
        if directory is not None and not reporting_error:
            try:
                private_new(directory / 'logical.result.safe.json', (json.dumps(row, sort_keys=True) + '\n').encode('ascii'))
            except OSError:
                row['reporting'] = 'UNAVAILABLE'
                if row['status'] != 'BLOCKED':
                    row.update(status='FAIL', passed=0, failed=1, notRun=0)
    return results

def execute_recovery(runner):
    import datetime, importlib.util, json, os, re, stat, subprocess, time
    from pathlib import Path
    from zoneinfo import ZoneInfo

    context = runner.special_context('application-recovery', 'seed')
    root = Path(context['root'])
    work = Path(context['laneRun'])
    project = context['recoveryProject']
    work.mkdir(mode=0o700, exist_ok=True)
    (work / 'artifacts').mkdir(mode=0o700, exist_ok=True)
    for phase in ('seed', 'pending', 'admit', 'attachment'):
        (work / 'artifacts' / phase).mkdir(mode=0o700, exist_ok=True)
    private_new(work / 'disposable-target', b'RCT_APP_RECOVERY_DISPOSABLE\n')
    env_file = Path(context['run']) / 'synthetic.env'
    spec = importlib.util.spec_from_file_location('recovery', root / 'scripts/recovery.py')
    recovery = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(recovery)
    compose = ['docker', 'compose', '-p', context['project'], '-f', context['composeFile']]
    stage = 'source'
    application_failure = {'action': 'UNKNOWN', 'reason': 'UNKNOWN',
                           'errorClass': 'UNKNOWN', 'errno': 'UNKNOWN', 'exitStatus': 'UNKNOWN'}
    backup_failure = {'action': 'UNKNOWN', 'hint': 'UNKNOWN', 'guardReason': 'UNKNOWN',
                      'errorClass': 'UNKNOWN', 'exitStatus': 'UNKNOWN',
                      'mongoPhase': 'UNKNOWN', 'mongoCodeClass': 'UNKNOWN'}
    source_pins = {}

    def retain_backup_failure(error=None, *, exit_status=None, stderr=None):
        if stage not in {'backup', 'fresh-restore'}:
            return
        if error is not None:
            name = type(error).__name__
            backup_failure['errorClass'] = name if name in {
                'RuntimeError', 'TimeoutExpired', 'FileNotFoundError', 'PermissionError',
                'OSError', 'JSONDecodeError', 'ValueError', 'KeyError', 'TypeError',
            } else 'UNKNOWN'
        if type(exit_status) is int and -255 <= exit_status <= 255:
            backup_failure['exitStatus'] = exit_status
        if isinstance(stderr, bytes) and len(stderr) <= 65536:
            hints = re.findall(rb'(?m)^RECOVERY_FAILURE_HINT:([A-Z_]{1,64})$', stderr)
            allowed = {b'GUARD', b'INPUT_CREDENTIAL', b'PG_QUERY', b'PG_SCHEMA', b'PG_DUMP',
                       b'PG_RESTORE', b'MONGO_QUERY', b'MONGO_DUMP', b'MONGO_RESTORE',
                       b'INSPECT', b'OTHER_TOOL',
                       b'RESTORE_DIFF_ACCESS_ACL',
                       b'RESTORE_DIFF_ACCESS_ACL_COLUMN',
                       b'RESTORE_DIFF_ACCESS_ACL_DATABASE',
                       b'RESTORE_DIFF_ACCESS_ACL_DEFAULT',
                       b'RESTORE_DIFF_ACCESS_ACL_FUNCTION',
                       b'RESTORE_DIFF_ACCESS_ACL_RELATION',
                       b'RESTORE_DIFF_ACCESS_ACL_SCHEMA',
                       b'RESTORE_DIFF_ACCESS_ACL_TYPE',
                       b'RESTORE_DIFF_ACCESS_OBJECT_SET',
                       b'RESTORE_DIFF_ACCESS_ORDER',
                       b'RESTORE_DIFF_ACCESS_OWNER',
                       b'RESTORE_DIFF_OTHER_SHAPE',
                       b'RESTORE_DIFF_SEQUENCES',
                       b'RESTORE_DIFF_TABLES'}
            if len(hints) == 1 and hints[0] in allowed:
                backup_failure['hint'] = hints[0].decode('ascii')
                if hints[0] == b'MONGO_QUERY':
                    backup_failure.update(recovery.mongo_failure_details(stderr))
                if hints[0] == b'GUARD':
                    reasons = [line for line in stderr.split(b'\n')
                               if line.startswith(b'RECOVERY_GUARD_REASON')]
                    allowed_reasons = {
                        b'OS_ERROR',
                        b'OS_ARGUMENT_TOO_LARGE',
                        b'OS_NO_SPACE',
                        b'OS_NOT_FOUND',
                        b'OS_PERMISSION',
                        b'OS_MEMORY',
                        b'OS_FILE_LIMIT',
                        b'OS_PIPE',
                        b'JSON_PARSE',
                        b'KEY_ERROR',
                        b'TAR_ERROR',
                        b'INVALID_VALUE',
                        b'ACCESS_DATABASE_MISSING',
                        b'ACCESS_INVENTORY_SHAPE',
                        b'ACCESS_OBJECT_DUPLICATE',
                        b'ACCESS_OBJECT_SHAPE',
                        b'BUNDLE_ACCESS_EVIDENCE',
                        b'BUNDLE_ARTIFACT_KIND',
                        b'BUNDLE_CONTENTS',
                        b'BUNDLE_DATABASE_INVENTORY',
                        b'BUNDLE_DECLARES_FILES',
                        b'BUNDLE_DIRECTORY',
                        b'BUNDLE_FILES_MODE',
                        b'BUNDLE_MANIFEST',
                        b'BUNDLE_MODE_MISMATCH',
                        b'CONTAINER_ARGUMENTS',
                        b'CREDENTIAL_FILE_MISSING',
                        b'FILE_MODE',
                        b'MONGO_FILES_INVENTORY',
                        b'MONGO_TARGET_NOT_FRESH',
                        b'POSTGRES_TARGET_NOT_FRESH',
                        b'RECOVERY_ADMIN_IDENTITY',
                        b'SCHEMA_REFERENCE_CLEANUP_IDENTITY',
                        b'SCHEMA_REFERENCE_EXISTS',
                        b'SCHEMA_REFERENCE_MISSING',
                        b'TARGET_CONTAINER_DISTINCTNESS',
                        b'TARGET_DISPOSABLE_IDENTITY',
                        b'TARGET_NOT_RUNNING',
                        b'TARGET_PROJECT',
                        b'UNKNOWN',
                    }
                    if len(reasons) == 1:
                        reason = re.fullmatch(rb'RECOVERY_GUARD_REASON:([A-Z_]{1,64})', reasons[0])
                        if reason and reason[1] in allowed_reasons:
                            backup_failure['guardReason'] = reason[1].decode('ascii')

    def retain_application_failure(error=None, *, exit_status=None):
        if stage != 'application':
            return
        if error is not None and application_failure['errorClass'] == 'UNKNOWN':
            name = type(error).__name__
            application_failure['errorClass'] = name if name in {
                'RuntimeError', 'TimeoutExpired', 'FileNotFoundError', 'PermissionError',
                'OSError', 'JSONDecodeError', 'ValueError', 'KeyError', 'TypeError',
            } else 'UNKNOWN'
            if isinstance(error, subprocess.TimeoutExpired):
                application_failure['reason'] = 'COMMAND_TIMEOUT'
            elif isinstance(error, OSError):
                application_failure['reason'] = 'OS_ERROR'
                number = error.errno
                if type(number) is int and number in {2, 5, 12, 13, 22, 24, 28, 32, 98, 99, 104, 110, 111}:
                    application_failure['errno'] = number
            elif isinstance(error, json.JSONDecodeError) and application_failure['reason'] == 'UNKNOWN':
                application_failure['reason'] = 'JSON_PARSE'
        if exit_status is not None and application_failure['exitStatus'] == 'UNKNOWN':
            application_failure['reason'] = 'COMMAND_EXIT'
            if type(exit_status) is int and -255 <= exit_status <= 255:
                application_failure['exitStatus'] = exit_status

    def application_service_observation():
        # Only the original pinned startup set; neither restored DBs nor discovered names.
        names = ('redis', 'rabbitmq', 'auth-service', 'academic-service', 'schedule-service',
                 'attendance-service', 'document-renderer-service', 'mobile-bff', 'api-gateway',
                 'notification-web', 'pwa-nginx', 'mini-app-nginx', 'nginx')
        wanted = {'redis', 'rabbitmq', *writers}
        result = {'capture': 'PARTIAL', 'unsupportedService': bool(wanted - set(names)),
                  'services': []}
        selected = []
        for name in names:
            if name not in wanted:
                continue
            row = {'service': name, 'identity': 'UNKNOWN', 'status': 'UNKNOWN',
                   'health': 'UNKNOWN', 'oomKilled': 'UNKNOWN', 'exitCode': 'UNKNOWN'}
            result['services'].append(row)
            pin = sources.get(name, {})
            labels = pin.get('Config', {}).get('Labels', {})
            cid, image = ids.get(name), pin.get('Image')
            if (not isinstance(cid, str) or not re.fullmatch('[0-9a-f]{64}', cid)
                    or pin.get('Id') != cid
                    or not isinstance(image, str) or not re.fullmatch('sha256:[0-9a-f]{64}', image)
                    or labels.get('com.docker.compose.project') != context['project']
                    or labels.get('com.docker.compose.service') != name):
                row['identity'] = 'PIN_MISMATCH'
                continue
            selected.append((name, cid, image, row))
        deadline = time.monotonic() + 8
        identity_format = ('{"id":{{json .Id}},"image":{{json .Image}},'
                           '"project":{{json (index .Config.Labels "com.docker.compose.project")}},'
                           '"service":{{json (index .Config.Labels "com.docker.compose.service")}}}')

        def read_selected(pins, state=False):
            remaining = deadline - time.monotonic()
            if not pins or remaining <= 0:
                return None
            template = identity_format
            if state:
                template = template[:-1] + (',"status":{{json .State.Status}},'
                    '"health":{{if .State.Health}}{{json .State.Health.Status}}{{else}}null{{end}},'
                    '"oomKilled":{{json .State.OOMKilled}},"exitCode":{{json .State.ExitCode}}}')
            response = logged_run(runner, context, ['docker', 'inspect', '--format', template,
                                       *(pin[1] for pin in pins)],
                                      stdout=subprocess.PIPE, stderr=subprocess.DEVNULL,
                                      timeout=min(4, remaining))
            if response.returncode != 0 or len(response.stdout) > 16384:
                return None
            records = [json.loads(line) for line in response.stdout.splitlines()]
            if (len(records) != len(pins) or any(type(item) is not dict for item in records)
                    or len({item.get('id') for item in records}) != len(pins)):
                return None
            return {item.get('id'): item for item in records}

        def matches(current, name, cid, image):
            return (current.get('id') == cid and current.get('image') == image
                    and current.get('project') == context['project'] and current.get('service') == name)

        # Admission precedes the state query. Recheck ownership before reading its fields.
        admitted = []
        first = read_selected(selected)
        if first is None:
            result['capture'] = 'INSPECT_UNAVAILABLE'
            return result
        for pin in selected:
            name, cid, image, row = pin
            if not matches(first.get(cid, {}), name, cid, image):
                row['identity'] = 'MISMATCH'
            else:
                row['identity'] = 'MATCHED'
                admitted.append(pin)
        second = read_selected(admitted, state=True)
        if second is None:
            result['capture'] = 'STATE_UNAVAILABLE'
            return result
        for name, cid, image, row in admitted:
            current = second.get(cid, {})
            if not matches(current, name, cid, image):
                row['identity'] = 'MISMATCH'
                continue
            status, health = current.get('status'), current.get('health')
            row['status'] = status if status in {
                'created', 'running', 'paused', 'restarting', 'removing', 'exited', 'dead',
            } else 'UNKNOWN'
            row['health'] = ('NONE' if health is None else health if health in {
                'starting', 'healthy', 'unhealthy',
            } else 'UNKNOWN')
            oom, code = current.get('oomKilled'), current.get('exitCode')
            row['oomKilled'] = oom if type(oom) is bool else 'UNKNOWN'
            row['exitCode'] = code if type(code) is int and 0 <= code <= 255 else 'UNKNOWN'
        if (not result['unsupportedService'] and all(row['identity'] == 'MATCHED'
                and row['status'] != 'UNKNOWN' and row['health'] != 'UNKNOWN'
                and row['oomKilled'] != 'UNKNOWN' and row['exitCode'] != 'UNKNOWN'
                for row in result['services'])):
            result['capture'] = 'COMPLETE'
        return result

    def verify_failure(stderr, *, timed_out=False):
        # Only exact verifier constants become public; never publish captured stderr.
        cause, http_status = 'OTHER', 0
        messages = {
            'Restored API operation identity/error mismatch': 'OPERATION_STATE',
            'Timed out waiting for restored real participants': 'PARTICIPANT_TIMEOUT',
            'Operation count changed': 'OPERATION_COUNT',
            'Restored operation did not complete': 'OPERATION_INCOMPLETE',
            'Durable operation identity changed': 'OPERATION_IDENTITY',
            'Participant receipts missing, duplicated or wrong identity': 'PARTICIPANT_RECEIPTS',
            'Academic durable receipt mismatch': 'ACADEMIC_RECEIPT',
            'Homework transfer history missing or duplicated': 'HOMEWORK_HISTORY',
            'Restored homework did not follow the accepted target slot': 'HOMEWORK_SLOT',
            'Restored homework placement identity/revision differs from accepted transfer': 'HOMEWORK_PLACEMENT',
            'Homework content/identity/revision changed beyond one transfer': 'HOMEWORK_CONTENT',
            'Original pending outbox was not sent with its original envelope': 'OUTBOX',
            'Past attendance changed or incorrect future marks appeared': 'ATTENDANCE_MARKS',
            'Attendance durable receipt mismatch': 'ATTENDANCE_RECEIPT',
            'Application schedule must expose source history and current target': 'SCHEDULE_VISIBILITY',
            'Application schedule has incorrect canonical flags/generation/transfer': 'SCHEDULE_FLAGS',
            'User API historical projection changed after restore': 'HISTORY_RESTORE',
            'Exact accepted HTTP replay changed the operation result': 'REPLAY_RESULT',
            'Exact HTTP replay changed business state/history': 'REPLAY_DATA',
            'Application canonical schedule changed after exact replay': 'REPLAY_SCHEDULE',
            'User history changed after exact replay': 'REPLAY_HISTORY',
            'Application endpoint unavailable': 'ENDPOINT_UNAVAILABLE',
            'OSError': 'OS_ERROR', 'ValueError': 'VALUE_ERROR', 'KeyError': 'KEY_ERROR',
            'IndexError': 'INDEX_ERROR', 'TypeError': 'TYPE_ERROR',
        }
        for key in ('batches', 'lessons', 'occurrences', 'lifecycle', 'schedule_bindings'):
            messages['Restored canonical ' + key + ' changed'] = 'CANONICAL_STATE'
        if timed_out:
            cause = 'COMMAND_TIMEOUT'
        elif isinstance(stderr, bytes) and len(stderr) <= 65536:
            try:
                message = stderr.decode('ascii').removesuffix('\n')
            except UnicodeDecodeError:
                message = ''
            prefix = 'FAIL application recovery: '
            if message.startswith(prefix):
                message = message[len(prefix):]
                cause = messages.get(message, 'OTHER')
                match = re.fullmatch(r'Application HTTP request failed with status (400|401|403|404|409|413|422|429|500|502|503|504)', message)
                if match:
                    cause, http_status = 'HTTP_STATUS', int(match[1])
        path = work / 'artifacts' / 'admit' / 'verification.safe.json'
        try:
            fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, 0o600)
            with os.fdopen(fd, 'w') as stream:
                json.dump({'stage': 'APPLICATION_VERIFY', 'cause': cause, 'httpStatus': http_status}, stream)
                stream.write('\n')
        except OSError:
            print('Verification diagnostic unavailable; original failure retained')

    def call(argv, *, cwd=root, env=None, seconds=180):
        verifier = argv[:3] == ['python3', 'scripts/test-application-recovery.py', 'verify']
        try:
                result = logged_run(runner, context, argv, cwd=cwd, env=env or runner.env, stdout=subprocess.PIPE,
                                    stderr=subprocess.PIPE, timeout=seconds)
        except subprocess.TimeoutExpired as error:
            retain_application_failure(error)
            retain_backup_failure(error)
            if verifier:
                verify_failure(b'', timed_out=True)
            raise
        except OSError as error:
            retain_application_failure(error)
            retain_backup_failure(error)
            raise
        if result.returncode:
            retain_application_failure(exit_status=result.returncode)
            retain_backup_failure(exit_status=result.returncode, stderr=result.stderr)
            if verifier:
                verify_failure(result.stderr)
            raise RuntimeError('Application recovery command failed')
        return result.stdout

    def api(phase):
        outcome = runner.special_phase('application-recovery', phase, {'RCT_APP_RECOVERY_RUN': str(work), 'RCT_APP_RECOVERY_PROJECT': project, 'RCT_APP_RECOVERY_PHASE': phase})
        if outcome['status'] != 'PASS':
            raise RuntimeError('Application recovery API phase failed')

    def inspect(container):
        value = json.loads(call(['docker', 'inspect', container]))[0]
        if value['Config']['Labels'].get('com.docker.compose.project') != context['project'] or value['Config']['Labels'].get('rct.local.stage17') != context['nonce']:
            raise RuntimeError('Source container ownership mismatch')
        require(value['Id'] == container, 'RECOVERY_SOURCE_IDENTITY')
        if container in source_pins:
            pin = source_pins[container]
            require(all(value[key] == pin[key] for key in ('Id', 'Image', 'Config', 'Mounts')),
                    'RECOVERY_SOURCE_IDENTITY_CHANGED')
            if value['NetworkSettings']['Networks']:
                service = value['Config']['Labels']['com.docker.compose.service']
                verified = runner.special_verified_networks(service, container) if (
                    stage == 'application' and value['State']['Running']) else None
                same_service_networks(value['NetworkSettings']['Networks'], pin['NetworkSettings']['Networks'],
                                      stopped=not value['State']['Running'], verified_networks=verified)
            else:
                require(value['Config']['Labels']['com.docker.compose.service'] in {
                    'postgres-academic', 'postgres-schedule', 'mongo-attendance'}
                    and not value['State']['Running'] and stage in {'fresh-restore', 'application'},
                    'RECOVERY_UNPLANNED_DISCONNECT')
        else:
            source_pins[container] = value
        return value

    def recovery_inputs(config, sources):
        # Synthetic CI only. Resolve the same mounted DB secrets, never application tokens
        # or a broad secret directory; no credential-bearing value becomes diagnostic output.
        runner.special_context('application-recovery', 'seed')
        pg = config['services']['postgres-academic']
        environment = pg['environment']
        if environment.get('POSTGRES_DB') != 'academic_db' or environment.get('POSTGRES_USER') != 'rct_user':
            raise RuntimeError('Unexpected recovery database bootstrap identity')
        expected = {
            'e2e-pg-legacy-runtime-password': ('rct-runtime-password', 'pg-legacy-runtime-password'),
            'e2e-pg-migration-password': ('rct-migration-password', 'pg-migration-password'),
            'e2e-pg-monitor-password': ('rct-monitor-password', 'pg-monitor-password'),
            'e2e-pg-auth-runtime-password': ('rct-auth-runtime-password', 'pg-auth-runtime-password'),
            'e2e-pg-academic-runtime-password': ('rct-academic-runtime-password', 'pg-academic-runtime-password'),
            'e2e-pg-auth-migration-password': ('rct-auth-migration-password', 'pg-auth-migration-password'),
        }
        bindings = pg.get('secrets', [])
        if (len(bindings) != len(expected) or {item.get('source') for item in bindings} != set(expected)):
            raise RuntimeError('Unexpected recovery database secret bindings')
        definitions, passwords = {}, {}
        parent = Path(context['certsDir']) / 'auth-secrets'
        parent_info = parent.lstat()
        if (not stat.S_ISDIR(parent_info.st_mode) or stat.S_ISLNK(parent_info.st_mode)
                or parent_info.st_uid != os.getuid() or stat.S_IMODE(parent_info.st_mode) != 0o700):
            raise RuntimeError('Unsafe owned recovery secret directory')
        mounts = sources['postgres-academic']['Mounts']
        for item in bindings:
            source = item['source']
            target_name, filename = expected[source]
            definition = config['secrets'][source]
            path = parent / filename
            if (item.get('target') != target_name or set(definition) - {'name', 'file'}
                    or definition.get('file') != str(path)
                    or len([mount for mount in mounts if mount.get('Type') == 'bind'
                            and mount.get('Source') == str(path) and mount.get('RW') is False
                            and mount.get('Destination') == '/run/secrets/' + target_name]) != 1):
                raise RuntimeError('Owned recovery secret mount changed')
            # Anchor every directory component; a linked ancestor must not redirect
            # an authorized synthetic secret read outside this pinned workspace.
            directory_flags = os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW
            parent_fd = os.open(Path(context['run']), directory_flags)
            try:
                for part in parent.relative_to(Path(context['run'])).parts:
                    next_fd = os.open(part, directory_flags, dir_fd=parent_fd)
                    os.close(parent_fd)
                    parent_fd = next_fd
                held = os.fstat(parent_fd)
                if (held.st_dev, held.st_ino) != (parent_info.st_dev, parent_info.st_ino):
                    raise RuntimeError('Owned recovery secret directory changed')
                fd = os.open(filename, os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK,
                             dir_fd=parent_fd)
            finally:
                os.close(parent_fd)
            with os.fdopen(fd, 'rb') as stream:
                before = os.fstat(stream.fileno())
                if (not stat.S_ISREG(before.st_mode) or before.st_uid != os.getuid()
                        or before.st_nlink != 1 or stat.S_IMODE(before.st_mode) != 0o444
                        or not 0 < before.st_size <= 256):
                    raise RuntimeError('Unsafe owned recovery secret file')
                data = stream.read(257)
                after = os.fstat(stream.fileno())
                identity = lambda info: (info.st_dev, info.st_ino, info.st_size,
                                         info.st_mtime_ns, info.st_ctime_ns)
                if (identity(before) != identity(after) or identity(before) != identity(path.lstat())
                        or not re.fullmatch(b'[0-9a-f]{64}', data)):
                    raise RuntimeError('Owned recovery secret changed')
            definitions[source] = definition
            passwords[source] = data.decode('ascii')
        values = {
            'POSTGRES_ACADEMIC_MIGRATION_PASSWORD': passwords['e2e-pg-migration-password'],
            'POSTGRES_AUTH_MIGRATION_PASSWORD': passwords['e2e-pg-auth-migration-password'],
            # Explicit mapping for this pre-existing isolated E2E owner only.
            # Production/disposable production-principal drills use rct_migrator.
            'POSTGRES_SCHEDULE_MIGRATION_PASSWORD': config['services']['postgres-schedule']['environment']['POSTGRES_PASSWORD'],
            'POSTGRES_SCHEDULE_RECOVERY_USER': config['services']['postgres-schedule']['environment']['POSTGRES_USER'],
            'POSTGRES_SCHEDULE_RECOVERY_ADMIN_USER': config['services']['postgres-schedule']['environment']['POSTGRES_USER'],
            'POSTGRES_SCHEDULE_RECOVERY_ADMIN_PASSWORD': config['services']['postgres-schedule']['environment']['POSTGRES_PASSWORD'],
            'MONGO_ROOT_PASSWORD': config['services']['mongo-attendance']['environment']['MONGODB_ROOT_PASSWORD'],
            'POSTGRES_RECOVERY_ADMIN_USER': environment['POSTGRES_USER'],
            'POSTGRES_RECOVERY_ADMIN_PASSWORD': environment['POSTGRES_PASSWORD'],
        }
        if any(not isinstance(value, str) or not 0 < len(value) <= 512
               or any(char in value for char in ('\n', '\r', '\0')) for value in values.values()):
            raise RuntimeError('Unsupported synthetic recovery credential encoding')
        path = work / 'recovery-input.private.env'
        fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, 0o600)
        with os.fdopen(fd, 'w') as stream:
            # Only recovery.credentials consumes this file; it strips the outer quotes
            # literally. No shell/Compose expansion or escape transformation occurs.
            stream.write(''.join(key + '="' + value + '"\n' for key, value in values.items()))
        return path, definitions

    def lifecycle_diagnostic():
        # Only the failed seed's own target is queried; no log/body/identifier is published.
        path = work / 'lifecycle-input.private.json'
        info = path.lstat()
        if not stat.S_ISREG(info.st_mode) or stat.S_IMODE(info.st_mode) != 0o600 or info.st_size > 4096:
            raise RuntimeError('Invalid private lifecycle input')
        fd = os.open(path, os.O_RDONLY | os.O_NOFOLLOW)
        with os.fdopen(fd) as stream:
            identity = os.fstat(stream.fileno())
            if (identity.st_dev, identity.st_ino) != (info.st_dev, info.st_ino):
                raise RuntimeError('Lifecycle input changed')
            value = json.load(stream)
        if set(value) != {'lessonId', 'semesterId', 'expectedInstant', 'clockSha256'}:
            raise RuntimeError('Invalid lifecycle input keys')
        for key in ('lessonId', 'semesterId'):
            if type(value[key]) is not int or not 0 < value[key] < 2**63:
                raise RuntimeError('Invalid lifecycle target')
        if not isinstance(value['expectedInstant'], str) or not re.fullmatch(
                r'\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}Z', value['expectedInstant']):
            raise RuntimeError('Invalid lifecycle clock')
        if not isinstance(value['clockSha256'], str) or not re.fullmatch('[0-9a-f]{64}', value['clockSha256']):
            raise RuntimeError('Invalid lifecycle digest')
        expected = datetime.datetime.fromisoformat(value['expectedInstant'].replace('Z', '+00:00'))
        wall = expected.astimezone(ZoneInfo('Europe/Moscow')).strftime('%Y-%m-%d %H:%M:%S.%f')
        lesson, semester = value['lessonId'], value['semesterId']
        for service in ('postgres-schedule', 'schedule-service'):
            if inspect(ids[service])['Id'] != sources[service]['Id']:
                raise RuntimeError('Lifecycle source identity changed')
        sql = f"""BEGIN READ ONLY; SET LOCAL statement_timeout = '5s';
        SELECT json_build_object(
          'targetPresent', EXISTS (SELECT 1 FROM lessons WHERE id = {lesson}),
          'targetSemesterMatches', EXISTS (SELECT 1 FROM lessons WHERE id = {lesson} AND semester_id = {semester}),
          'targetStatus', COALESCE((SELECT CASE WHEN status::text IN ('planned','active','closed','cancelled','transferred') THEN status::text ELSE 'OTHER' END FROM lessons WHERE id = {lesson}), 'MISSING'),
          'endPlus5IsDue', COALESCE((SELECT date + end_time + INTERVAL '5 minutes' <= TIMESTAMP '{wall}' FROM lessons WHERE id = {lesson}), false),
          'unfinishedTransfer', EXISTS (SELECT 1 FROM lesson_transfer_operations t JOIN lessons l ON t.occurrence_id = l.occurrence_id WHERE l.id = {lesson} AND t.state <> 'COMPLETED'),
          'barrierState', COALESCE((SELECT CASE WHEN participant_state IN ('PENDING','READY','PREPARED_RESTORE','RELEASED','DELETE_PREPARING','DELETE_SEALED','DELETED') THEN participant_state ELSE 'OTHER' END FROM schedule_semester_archive_barriers WHERE semester_id = {semester}), 'NONE'),
          'shedlockPresent', EXISTS (SELECT 1 FROM shedlock WHERE name = 'lesson-status-transition'),
          'shedlockCurrentlyHeld', EXISTS (SELECT 1 FROM shedlock WHERE name = 'lesson-status-transition' AND lock_until > now()),
          'shedlockRecentlyAcquired', EXISTS (SELECT 1 FROM shedlock WHERE name = 'lesson-status-transition' AND locked_at >= now() - INTERVAL '3 minutes'));
        ROLLBACK;"""
        raw = call(['docker', 'exec', ids['postgres-schedule'], 'psql', '-X', '-qAt',
                    '-U', 'rct_user', '-d', 'schedule_db', '-v', 'ON_ERROR_STOP=1', '-c', sql], seconds=8)
        if len(raw) > 4096:
            raise RuntimeError('Lifecycle projection budget exceeded')
        facts = json.loads(raw)
        bools = {'targetPresent', 'targetSemesterMatches', 'endPlus5IsDue', 'unfinishedTransfer',
                 'shedlockPresent', 'shedlockCurrentlyHeld', 'shedlockRecentlyAcquired'}
        if set(facts) != bools | {'targetStatus', 'barrierState'} or any(type(facts[key]) is not bool for key in bools):
            raise RuntimeError('Invalid lifecycle projection')
        if facts['targetStatus'] not in {'planned','active','closed','cancelled','transferred','OTHER','MISSING'} or facts['barrierState'] not in {'PENDING','READY','PREPARED_RESTORE','RELEASED','DELETE_PREPARING','DELETE_SEALED','DELETED','OTHER','NONE'}:
            raise RuntimeError('Invalid lifecycle projection enum')
        # The finite source names below are untrusted reported hints, never raw error text.
        logs = logged_run(runner, context, ['docker', 'logs', '--tail', '160', '--since', '180s', ids['schedule-service']],
                              stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=8)
        readable = logs.returncode == 0 and len(logs.stdout) <= 1048576
        body = logs.stdout if readable else b''
        classes = ('PSQLException', 'DataIntegrityViolationException', 'IllegalStateException',
                   'StatusRuntimeException', 'TransactionSystemException', 'NullPointerException')
        states = ('23502', '23503', '23505', '23514', '40P01', '40001', '55P03')
        # Associate hints with ONE complete logged exception, never an unrelated tail match.
        lifecycle = re.compile(rb'(?m)^\s*at ru\.rutcampustrack\.schedule\.lesson\.LessonStatusTransitionJob\.runTransitions\(')
        exception = re.compile(rb'(?m)^(?:Caused by: )?(?:[A-Za-z_$][A-Za-z0-9_$]*\.)+[A-Za-z_$][A-Za-z0-9_$]*(?:Exception|Error)(?::|$)')
        linked = []
        # shared/logback-base.xml writes each complete exception in one JSON stack field.
        for line in body.splitlines()[-160:]:
            try:
                record = json.loads(line)
            except (ValueError, RecursionError):
                continue
            if type(record) is not dict or type(record.get('stack')) is not str:
                continue
            stack = record['stack'].encode('utf-8', errors='replace')
            if exception.search(stack) and lifecycle.search(stack):
                linked.append(stack)
        chain = linked[-1] if linked else b''
        # These remain untrusted reported source hints, not target/commit or causal proof.
        origins = (
            ('FILE_CLOCK', b'at ru.rutcampustrack.e2e.FileClock.instant('),
            ('SEMESTER_AUTHORITY', b'at ru.rutcampustrack.schedule.grpc.ScheduleSemesterArchiveWriteFence.readAuthority('),
            ('OUTBOX_LISTENER', b'at ru.rutcampustrack.schedule.event.DomainEventListener.onDomainEvent('),
            ('JDBC_OR_LOCK', b'at org.postgresql.'),
        )
        origin = 'UNKNOWN'
        if chain:
            matches = [name for name, frame in origins if frame in chain]
            origin = matches[0] if len(matches) == 1 else 'OTHER'
        facts.update(logsObserved=readable,
                     reportedClasses=[name for name in classes if re.search(rb'(?<![A-Za-z0-9_$])' + name.encode() + rb'(?![A-Za-z0-9_$])', chain)],
                     reportedSqlStates=[code for code in states if re.search(rb'SQLState: ?' + code.encode() + rb'(?![0-9A-Z])', chain)],
                     reportedLifecycleFailure=bool(chain),
                     reportedAuthorityFrame=b'at ru.rutcampustrack.schedule.grpc.ScheduleSemesterArchiveWriteFence.readAuthority(' in chain,
                     reportedLifecycleOrigin=origin)
        output = work / 'artifacts' / 'seed' / 'lifecycle-server.safe.json'
        fd = os.open(output, os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, 0o600)
        with os.fdopen(fd, 'w') as stream:
            json.dump(facts, stream)

    try:
        config = json.loads(call([*compose, 'config', '--format', 'json']))
        ids = {service: call([*compose, 'ps', '-q', service]).decode().strip()
               for service in config['services']}
        if not all(ids.values()):
            raise RuntimeError('Source stack is incomplete')
        sources = {service: inspect(cid) for service, cid in ids.items()}
        network = context['networkName']
        network_info = json.loads(call(['docker', 'network', 'inspect', network]))[0]
        if network_info['Labels'].get('com.docker.compose.project') != context['project'] or network_info['Labels'].get('rct.local.stage17') != context['nonce']:
            raise RuntimeError('Source network ownership mismatch')
        recovery_env, db_secrets = recovery_inputs(config, sources)
        db_services = ('postgres-academic', 'postgres-schedule', 'mongo-attendance')
        target = {'services': {}, 'secrets': db_secrets,
                  'networks': {'private_net': {'external': True, 'name': network}}}
        init_mounts = {
            'postgres-academic': {str(root / 'infra/postgres/init-roles.sh'): '/docker-entrypoint-initdb.d/10-rct-roles.sh'},
            'postgres-schedule': {},
            'mongo-attendance': {str(root / 'infra/mongo/init-mongo.js'): '/docker-entrypoint-initdb.d/init-mongo.js'},
        }
        for service in db_services:
            original = config['services'][service]
            target['services'][service] = {
                key: original[key] for key in ('image', 'environment', 'healthcheck')
                if key in original
            }
            mounts = original.get('volumes', [])
            expected_mounts = init_mounts[service]
            if len(mounts) != len(expected_mounts) or any(
                    item.get('type') != 'bind' or item.get('read_only') is not True
                    or expected_mounts.get(item.get('source')) != item.get('target')
                    or len([mount for mount in sources[service]['Mounts']
                            if mount.get('Type') == 'bind' and mount.get('RW') is False
                            and mount.get('Source') == item['source']
                            and mount.get('Destination') == item['target']]) != 1
                    for item in mounts):
                raise RuntimeError('Only pinned read-only database init mounts may enter fresh targets')
            if mounts:
                target['services'][service]['volumes'] = mounts
            if service == 'postgres-academic':
                target['services'][service]['secrets'] = original['secrets']
            if service == 'mongo-attendance':
                target['services'][service]['healthcheck'] = recovery.mongo_restore_healthcheck(original['healthcheck'])
            target['services'][service]['image'] = sources[service]['Image']
            target['services'][service].update(
                labels={'io.rutcampustrack.recovery': 'disposable', 'rct.local.stage17': context['nonce']},
                networks={'private_net': {'aliases': [service]}})
        target_file = work / 'fresh-target.compose.json'
        private_new(target_file, json.dumps(target).encode())
        runner.special_preserve_recovery = True
        intent = runner.special_intent('application-recovery', 'pending', 'RECOVERY_QUIESCE', list(ids), {})
        target_compose = ['docker', 'compose', '-p', project, '-f', str(target_file)]
        api('seed')
        stage = 'pending'
        call([*compose, 'stop', '--timeout', '15', 'rabbitmq'])
        if inspect(ids['rabbitmq'])['State']['Running']:
            raise RuntimeError('Owned broker did not stop')
        api('pending')
        stage = 'backup'
        writers = [name for name in ids if name not in (*db_services, 'rabbitmq', 'redis')]
        backup_failure['action'] = 'WRITERS_STOP'
        call([*compose, 'stop', '--timeout', '30', *writers])
        backup_failure['action'] = 'WRITER_INSPECT'
        if any(inspect(ids[name])['State']['Running'] for name in writers):
            raise RuntimeError('Application writers remain active')
        db_args = ['--env-file', str(recovery_env), '--academic-container', ids['postgres-academic'],
                   '--schedule-container', ids['postgres-schedule'], '--mongo-container', ids['mongo-attendance']]
        bundle = work / 'bundle'
        backup_failure['action'] = 'BACKUP_SCRIPT'
        call(['python3', 'scripts/recovery.py', 'backup', *db_args,
              '--no-files', '--quiesced', '--output', str(bundle)])
        backup_failure['action'] = 'TRANSFER_CAPTURE'
        pending = json.loads((work / 'pending.json').read_text())
        baseline = work / 'capture.private.json'
        call(['python3', 'scripts/test-application-recovery.py', 'capture', '--bundle', str(bundle),
              '--operation-id', pending['operationId'], '--request-file', str(work / 'request.json'),
              '--history-before', str(work / 'history-before.json'), '--history-pointer', pending['historyPointer'],
              '--history-lesson-id', str(pending['historyLessonId']), '--quiesced', '--output', str(baseline)])
        runner.special_register_recovery(target_file, project, {name: ids[name] for name in db_services})
        stage = 'fresh-restore'
        backup_failure['action'] = 'SOURCE_DATABASE_STOP'
        call([*compose, 'stop', '--timeout', '15', *db_services, 'redis'])
        for service in db_services:
            backup_failure['action'] = 'SOURCE_DATABASE_INSPECT'
            if inspect(ids[service])['State']['Running']:
                raise RuntimeError('Source database did not stop')
            backup_failure['action'] = 'SOURCE_ENDPOINT_DISCONNECT'
            call(['docker', 'network', 'disconnect', network, ids[service]])
        # New target containers have no application/Flyway startup or source database mounts.
        backup_failure['action'] = 'TARGET_DATABASE_START'
        call([*target_compose, 'up', '-d', '--pull', 'never', '--wait', '--wait-timeout', '150'])
        backup_failure['action'] = 'TARGET_DATABASE_IDENTIFY'
        target_ids = {name: call([*target_compose, 'ps', '-q', name]).decode().strip() for name in db_services}
        for name, cid in target_ids.items():
            backup_failure['action'] = 'TARGET_DATABASE_INSPECT'
            restored_info = json.loads(call(['docker', 'inspect', cid]))[0]
            labels = restored_info['Config']['Labels']
            if (labels.get('com.docker.compose.project') != project
                    or labels.get('io.rutcampustrack.recovery') != 'disposable'
                    or restored_info['Image'] != sources[name]['Image']):
                raise RuntimeError('Fresh target identity mismatch')
        target_args = ['--env-file', str(recovery_env), '--academic-container', target_ids['postgres-academic'],
                       '--schedule-container', target_ids['postgres-schedule'], '--mongo-container', target_ids['mongo-attendance']]
        backup_failure['action'] = 'RESTORE_SCRIPT'
        call(['python3', 'scripts/recovery.py', 'restore', *target_args,
              '--no-files', '--bundle', str(bundle), '--target-project', project])
        stage = 'application'
        application_failure['action'] = 'START_APPLICATIONS'
        # Restart the same built apps, signing volume and TLS identities on restored databases.
        call([*compose, 'up', '-d', '--no-build', '--no-recreate', '--no-deps', '--pull', 'never',
              '--wait', '--wait-timeout', '180', 'redis', 'rabbitmq', *writers], seconds=220)
        application_failure['action'] = 'VERIFY_APPLICATION_IDENTITIES'
        for name in ('redis', 'rabbitmq', *writers):
            cid = call([*compose, 'ps', '-q', name]).decode().strip()
            if cid != ids[name] or inspect(cid)['Image'] != sources[name]['Image']:
                application_failure['reason'] = 'APPLICATION_IDENTITY_CHANGED'
                raise RuntimeError('Original application container or image changed')
        application_failure['action'] = 'VERIFY_SOURCE_DATABASES_STOPPED'
        if any(inspect(ids[name])['State']['Running'] for name in db_services):
            application_failure['reason'] = 'SOURCE_DATABASE_RUNNING'
            raise RuntimeError('Source database restarted unexpectedly')
        application_failure['action'] = 'VERIFY_DATABASE_ENDPOINTS'
        connected = json.loads(call(['docker', 'network', 'inspect', network]))[0]['Containers']
        if any(ids[name] in connected for name in db_services):
            application_failure['reason'] = 'SOURCE_ENDPOINT_ATTACHED'
            raise RuntimeError('Source database endpoint remains attached')
        if not all(target_ids[name] in connected for name in db_services):
            application_failure['reason'] = 'RESTORED_ENDPOINT_MISSING'
            raise RuntimeError('Restored database endpoint is missing')
        application_failure['action'] = 'API_ADMIT'
        api('admit')
        application_failure['action'] = 'VERIFY_RESTORED_STATE'
        call(['python3', 'scripts/test-application-recovery.py', 'verify', *target_args,
              '--bundle', str(bundle), '--baseline', str(baseline), '--target-project', project,
              '--schedule-url', 'https://localhost:8443/api',
              '--history-url', 'https://localhost:8443' + pending['historyUrl'],
              '--actor-token-file', str(work / 'actor-token.private'),
              '--history-token-file', str(work / 'history-token.private'),
              '--timeout-seconds', '130', '--output', str(work / 'verified.private.json')],
             env=recovery_python_environment(runner, context))
        application_failure['action'] = 'API_ATTACHMENT'
        api('attachment')
        runner.special_seal_recovery(target_ids, work / 'verified.private.json')
        runner.special_outcome(intent, True, restored=True)
        return 'PASS'
    except Exception as error:
        if stage in {'backup', 'fresh-restore'}:
            retain_backup_failure(error)
            receipt = {'stage': 'RESTORE' if stage == 'fresh-restore' else 'BACKUP', **backup_failure}
            try:
                fd = os.open(work / 'artifacts' / 'admit' / 'backup-failure.safe.json',
                             os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, 0o600)
                with os.fdopen(fd, 'w') as stream:
                    json.dump(receipt, stream, sort_keys=True)
                    stream.write('\n')
            except OSError:
                print('Backup failure receipt unavailable; original failure retained')
            print(json.dumps(receipt, sort_keys=True))
        if stage == 'source' and (work / 'lifecycle-input.private.json').exists():
            try:
                lifecycle_diagnostic()
            except Exception:
                print('Lifecycle diagnostic unavailable; original failure retained')
        if stage == 'application':
            retain_application_failure(error)
            try:
                services = application_service_observation()
            except Exception:
                services = {'capture': 'UNAVAILABLE', 'unsupportedService': False, 'services': []}
            receipt = {'stage': 'APPLICATION', **application_failure, 'serviceObservation': services}
            try:
                data = (json.dumps(receipt, sort_keys=True) + '\n').encode('ascii')
                if len(data) > 16384:
                    raise ValueError('Closed application failure receipt exceeds its bound')
                fd = os.open(work / 'artifacts' / 'admit' / 'application-failure.safe.json',
                             os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, 0o600)
                with os.fdopen(fd, 'wb') as stream:
                    stream.write(data)
            except Exception:
                print('Application failure receipt unavailable; original failure retained')
            print(json.dumps(receipt, sort_keys=True))
        error_class = application_failure['errorClass'] if stage == 'application' else type(error).__name__
        print('FAIL application recovery stage=' + stage + ' class=' + error_class)
        if 'intent' in locals():
            runner.special_outcome(intent, False, restored=False)
        return 'FAIL'
