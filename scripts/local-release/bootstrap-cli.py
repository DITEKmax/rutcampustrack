#!/usr/bin/env python3
"""Canonical foreground initial-administrator CLI acceptance; private same-run evidence.

Local caller supplies built immutable images/provenance after stopping its own stack.
CI may omit image arguments and retains the canonical fixed image references.
No build, pull, global prune, old resource cleanup, or GitHub identity emulation.
"""
import argparse
from contextlib import contextmanager
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import re
import shutil
import signal
import stat
import subprocess
import sys
import tempfile
import threading
import time

SOURCE = 'services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/bootstrap/InitialAdministratorBootstrapIT.java'
CLASS = 'ru.rutcampustrack.academic.bootstrap.InitialAdministratorBootstrapIT'
METHOD = 'operatorCliLoadsPrivateConfigAndCommitsFirstAdministrator'
RESULTS = 'services/academic-service/academic-app/build/test-results/integrationTest'
IMAGE = re.compile(r'sha256:[0-9a-f]{64}')
PROJECT = re.compile(r'rct-bootstrap-cli-[0-9]{1,20}-[0-9]{1,20}')
LIMIT = 16 * 1024 * 1024


def load(path, name):
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def private_directory(path, create=False):
    if create:
        path.mkdir(mode=0o700)
    info = path.lstat()
    if (not path.is_absolute() or not stat.S_ISDIR(info.st_mode)
            or info.st_uid != os.getuid() or info.st_mode & 0o077):
        raise ValueError('PRIVATE_DIRECTORY')
    return path


def write_json(path, value):
    with path.open('x', encoding='utf8') as stream:
        json.dump(value, stream, indent=2)
        stream.write('\n')


@contextmanager
def deferred_interrupts():
    """Keep Popen-to-handle registration and final reap uninterruptible by our handlers.

    Do not block the OS signal mask: the child must inherit normal TERM/INT delivery.
    """
    pending = []
    previous = {number: signal.getsignal(number) for number in (signal.SIGTERM, signal.SIGINT)}
    try:
        for number in previous:
            signal.signal(number, lambda number, frame: pending.append(number))
        yield pending
    finally:
        for number, handler in previous.items():
            signal.signal(number, handler)


def finish_outcome(primary, diagnostic, holds, cancelled):
    """Diagnostics cannot replace the operation's cause or permit batch continuation."""
    reason = 'CANCELLED' if cancelled else primary
    status = 'HOLD' if holds else 'PASS' if primary == 'NATIVE_PASS' and diagnostic is None and not cancelled else 'FAIL'
    if reason == 'NATIVE_PASS' and diagnostic is not None:
        reason = diagnostic
    return status, reason, 130 if cancelled else 2 if holds else 0 if status == 'PASS' else 1


def native_diagnostic(output, records):
    """Closed projection from retained native logs, including failures before JUnit.

    No captured message, path, method, URL or environment value crosses this boundary.
    """
    classes = {
        'org.postgresql.util.PSQLException': 'DB_POSTGRES',
        'java.sql.SQLException': 'DB_SQL',
        'java.net.ConnectException': 'TRANSPORT_CONNECT',
        'java.net.SocketTimeoutException': 'TRANSPORT_TIMEOUT',
        'java.net.UnknownHostException': 'TRANSPORT_DNS',
        'org.springframework.jdbc.CannotGetJdbcConnectionException': 'DB_CONNECT',
        'org.flywaydb.core.api.FlywayException': 'DB_MIGRATION',
        'org.gradle.api.tasks.TaskExecutionException': 'GRADLE_TASK',
        'org.gradle.process.internal.ExecException': 'CHILD_EXEC',
        'java.lang.OutOfMemoryError': 'JVM_MEMORY',
        'java.lang.AssertionError': 'ASSERTION',
    }
    sources = {
        'InitialAdministratorBootstrapIT': 'ru.rutcampustrack.academic.bootstrap.InitialAdministratorBootstrapIT',
        'InitialAdministratorAuthCommand': 'ru.rutcampustrack.auth.bootstrap.InitialAdministratorAuthCommand',
        'InitialAdministratorAcademicCommand': 'ru.rutcampustrack.academic.bootstrap.InitialAdministratorAcademicCommand',
    }
    patterns = [(category, re.compile(r'(?<![\w.$])' + re.escape(name) + r'(?![\w.$])'), name)
                for name, category in classes.items()]
    patterns.extend([
        ('JAVA_COMPILATION', re.compile(r'(?m)\.java:[1-9][0-9]{0,5}: error:'), None),
        ('JAVA_MISSING_SYMBOL', re.compile(r'\berror: cannot find symbol\b'), None),
        ('JAVA_INCOMPATIBLE_TYPES', re.compile(r'\berror: incompatible types\b'), None),
        ('DB_SQLSTATE', re.compile(r'(?i)SQLSTATE(?:\s*[:=]\s*|\s+)(?:08001|08006|28P01|42501|42P01|23505)\b'), None),
        ('DB_AUTHENTICATION', re.compile(r'(?i)SQLSTATE(?:\s*[:=]\s*|\s+)28P01\b'), None),
        ('DB_PERMISSION', re.compile(r'(?i)SQLSTATE(?:\s*[:=]\s*|\s+)42501\b'), None),
    ])
    result = {'state': 'NOT_RUN', 'nativeExit': None, 'observations': [], 'sourceFrames': []}
    for record in records:
        if record['command'][:2] != ['bash', 'gradlew']:
            continue
        result.update(state='NO_CLOSED_MATCH', nativeExit=record['nativeExit'])
        for stream in (0, 1):
            path = output / f"{record['ordinal']:03d}.{stream}.private.log"
            if path.is_symlink() or not path.is_file() or path.stat().st_size > LIMIT:
                result['state'] = 'UNREADABLE'
                continue
            data = path.read_bytes().decode('utf8', errors='replace')
            matches = []
            for category, pattern, exception in patterns:
                match = pattern.search(data)
                if match:
                    matches.append((match.start(), category, exception))
            for _, category, exception in sorted(matches)[:8]:
                item = {'stream': stream, 'category': category}
                if exception is not None:
                    item['exceptionClass'] = exception
                if item not in result['observations'] and len(result['observations']) < 16:
                    result['observations'].append(item)
            for basename, classname in sources.items():
                # A compiler location or an exact known bootstrap stack frame only.
                compiler = re.search(r'(?m)(?:^|[/\\])' + basename + r'\.java:([1-9][0-9]{0,5}): error:', data)
                frame = re.search(r'\bat ' + re.escape(classname) + r'\.[A-Za-z_$][A-Za-z0-9_$]*\('
                                  + basename + r'\.java:([1-9][0-9]{0,5})\)', data)
                for kind, match in (('JAVA_COMPILATION', compiler), ('SOURCE_FRAME', frame)):
                    if match and len(result['sourceFrames']) < 8:
                        result['sourceFrames'].append({'stream': stream, 'kind': kind,
                            'class': classname, 'file': basename + '.java', 'line': int(match[1])})
        if result['state'] != 'UNREADABLE' and (result['observations'] or result['sourceFrames']):
            result['state'] = 'OBSERVED'
    return result


class Commands:
    """Drain both pipes into finite private logs; terminate and reap the process group."""
    def __init__(self, root, output, environment):
        self.root, self.output, self.environment = root, output, environment
        self.deadline = time.monotonic() + 600
        self.records = []
        self.closure_unknown = False
        self.cancelled = False

    def close_group(self, proc, threads):
        """Escalate even when the leader/pipes exited, then prove the whole PG absent."""
        known = True
        for number in (signal.SIGTERM, signal.SIGKILL):
            try:
                os.killpg(proc.pid, number)
            except ProcessLookupError:
                pass
            except OSError:
                known = False
            if number == signal.SIGTERM:
                try:
                    proc.wait(timeout=3)
                except subprocess.TimeoutExpired:
                    pass
                except OSError:
                    known = False
        try:
            proc.wait(timeout=5)
        except (OSError, subprocess.TimeoutExpired):
            known = False
        absent = False
        end = time.monotonic() + 3
        while True:
            try:
                os.killpg(proc.pid, 0)
            except ProcessLookupError:
                absent = True
                break
            except OSError:
                break
            if time.monotonic() >= end:
                break
            time.sleep(0.05)
        for thread in threads:
            thread.join(timeout=3)
        return known and absent and all(not thread.is_alive() for thread in threads)

    def call(self, arguments, seconds=20, check=True, cleanup=False):
        number = len(self.records) + 1
        started = time.monotonic()
        record = {'ordinal': number, 'command': arguments, 'budgetSeconds': seconds,
                  'nativeExit': None, 'reason': 'START_FAILED'}
        self.records.append(record)
        streams, threads, chunks = [], [], [bytearray(), bytearray()]
        overflow = threading.Event()
        proc = None
        primary = None
        try:
            remaining = self.deadline - time.monotonic()
            if remaining <= 0 and not cleanup:
                raise ValueError('JOB_DEADLINE')
            seconds = min(seconds, max(1, remaining)) if not cleanup else seconds
            with deferred_interrupts() as pending:
                proc = subprocess.Popen(arguments, cwd=self.root, env=self.environment,
                                        stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                                        start_new_session=True)
                record['reason'] = 'STARTED'
            if pending:
                raise KeyboardInterrupt()

            def drain(pipe, index, stream):
                try:
                    total = 0
                    while True:
                        block = pipe.read(65536)
                        if not block:
                            break
                        keep = block[:max(0, LIMIT - total)]
                        stream.write(keep)
                        total += len(block)
                        if len(chunks[index]) <= 1048576:
                            chunks[index].extend(block[:1048577 - len(chunks[index])])
                        if total > LIMIT:
                            overflow.set()
                except BaseException:
                    overflow.set()
                finally:
                    pipe.close()

            for index, pipe in enumerate((proc.stdout, proc.stderr)):
                stream = (self.output / f'{number:03d}.{index}.private.log').open('xb')
                streams.append(stream)
                thread = threading.Thread(target=drain, args=(pipe, index, stream), daemon=True)
                threads.append(thread)
                thread.start()
            end = time.monotonic() + seconds
            while proc.poll() is None:
                if overflow.is_set():
                    raise ValueError('LOG_LIMIT')
                if time.monotonic() >= end:
                    raise TimeoutError('COMMAND_TIMEOUT')
                time.sleep(0.05)
            record['nativeExit'] = proc.returncode
            record['reason'] = 'EXIT'
            if overflow.is_set():
                raise ValueError('LOG_LIMIT')
            if check and proc.returncode != 0:
                raise ValueError('NATIVE_FAILURE')
        except BaseException as error:
            primary = error
            if isinstance(error, KeyboardInterrupt):
                self.cancelled = True
                record['reason'] = 'CANCELLED'
            elif isinstance(error, TimeoutError):
                record['reason'] = 'TIMEOUT'
            elif proc is not None:
                record['reason'] = str(error) if str(error) in ('NATIVE_FAILURE', 'LOG_LIMIT') else 'COMMAND_ERROR'
        finally:
            if proc is not None:
                with deferred_interrupts() as pending:
                    try:
                        closed = self.close_group(proc, threads)
                    except BaseException:
                        closed = False
                    record['closure'] = 'CLOSED' if closed else 'HOLD'
                    self.closure_unknown |= not closed
                if pending:
                    self.cancelled = True
                    if primary is None:
                        primary = KeyboardInterrupt()
                        record['reason'] = 'CANCELLED'
                record['nativeExit'] = proc.returncode
                if not threads:
                    proc.stdout.close()
                    proc.stderr.close()
            for index, stream in enumerate(streams):
                if index >= len(threads) or not threads[index].is_alive():
                    stream.close()
            record['durationSeconds'] = round(time.monotonic() - started, 3)
        if primary is not None:
            raise primary
        if self.closure_unknown:
            raise ValueError('PROCESS_CLOSURE_UNCERTAIN')
        if overflow.is_set() or any(t.is_alive() for t in threads):
            raise ValueError('PRIVATE_LOG_INCOMPLETE')
        return bytes(chunks[0])


def validate_provenance(value, context, auth, academic):
    images = value.get('images', {})
    if (value.get('schemaVersion') != 1 or value.get('event') != 'LOCAL_CONTROL'
            or value.get('revision') != context['checkoutSha']
            or value.get('nonce') != context['runIdentity'].removeprefix('local-')
            or value.get('stackStopped') is not True
            or not re.fullmatch(r'[a-z0-9][a-z0-9_-]{0,100}', value.get('project', ''))
            or images.get('auth') != auth or images.get('academic') != academic
            or any(not IMAGE.fullmatch(images.get(k, '')) for k in ('auth', 'academic', 'postgres'))):
        raise ValueError('IMAGE_PROVENANCE')
    return images


def admit(report, metadata, source):
    if (metadata.get('testStepOutcome') != 'success'
            or metadata.get('job') != 'e2e-auth'
            or metadata.get('event') == 'LOCAL_CONTROL' and (
                metadata.get('runId') is not None or metadata.get('runAttempt') is not None)):
        raise ValueError('EXECUTION_CONTEXT')
    expected = {'PASSED': 1, 'FAILED': 0, 'ERROR': 0, 'SKIPPED': 0, 'NOT_EXECUTED': 0}
    cases = report.get('cases', [])
    xml = report.get('xml', [])
    if (report.get('collection') != 'RESULTS' or report.get('summary') != expected
            or report.get('metadata') != metadata or len(cases) != 1 or len(xml) != 1
            or xml[0].get('state') != 'PARSED' or not xml[0].get('suites')
            or any(not s.get('countsConsistent') for s in xml[0]['suites'])):
        raise ValueError('ONE_FRESH_NATIVE_PASS')
    case = cases[0]
    if any(case.get(k) != v for k, v in {
        'identity': 'SOURCE_METHOD', 'status': 'PASSED', 'classname': CLASS,
        'method': METHOD, 'source': SOURCE,
        'sourceSha256': hashlib.sha256(source).hexdigest()}.items()):
        raise ValueError('SOURCE_LINKED_PASS')


def cleanup_owned(call, project, images):
    for label in ('rct.bootstrap.cli.owner', 'rct.bootstrap.cli.fixture'):
        identifiers = call(['docker', 'ps', '-aq', '--filter', 'label=' + label + '=' + project], cleanup=True).decode().split()
        for identifier in identifiers:
            if not re.fullmatch(r'[0-9a-f]{12,64}', identifier):
                raise ValueError('CLEANUP_IDENTIFIER')
            info = json.loads(call(['docker', 'inspect', identifier], cleanup=True))[0]
            labels = info['Config'].get('Labels') or {}
            allowed = {images['auth'], images['academic']} if label.endswith('.owner') else {images['postgres']}
            if (labels.get(label) != project or info['Image'] not in allowed
                    or label.endswith('.owner') and (
                        labels.get('com.docker.compose.project') != project
                        or labels.get('com.docker.compose.service') not in ('auth-service', 'academic-service'))):
                raise ValueError('CLEANUP_SCOPE')
            call(['docker', 'rm', '--force', '--volumes', identifier], cleanup=True)
        if call(['docker', 'ps', '-aq', '--filter', 'label=' + label + '=' + project], cleanup=True).strip():
            raise ValueError('CLEANUP_REMAINS')


def retain_xml(root, output):
    scoped_path(root, root / RESULTS)
    paths = sorted((root / RESULTS).glob('TEST-*.xml'))
    total = 0
    target = output / 'native-xml-private'
    target.mkdir(mode=0o700)
    for ordinal, path in enumerate(paths, 1):
        if path.is_symlink() or not path.is_file():
            raise ValueError('XML_PATH')
        data = path.read_bytes() if path.stat().st_size <= LIMIT else None
        if data is None or len(data) + total > LIMIT:
            raise ValueError('XML_LIMIT')
        total += len(data)
        (target / f'{ordinal:03d}.xml').write_bytes(data)


def scoped_path(root, path):
    relative = path.relative_to(root)
    current = root
    for part in relative.parts:
        current = current / part
        if current.is_symlink():
            raise ValueError('SYMLINK_SCOPE')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--auth-image')
    parser.add_argument('--academic-image')
    parser.add_argument('--provenance', type=Path)
    args = parser.parse_args()
    os.umask(0o077)
    root = Path(__file__).resolve().parents[2]
    context = load(root / 'scripts/local_release_context.py', 'bootstrap_context').source_context()
    output = private_directory(args.output.absolute(), create=True)
    env = dict(os.environ)
    commands = Commands(root, output, env)
    project = None
    images = {}
    tracked = None
    temporary = None
    collector = None
    invoked = False
    owned = False
    status, reason, cleanup = 'FAIL', 'PREFLIGHT', 'NOT_NEEDED'
    diagnostic = None
    holds = []
    cancelled = False
    finalizing = False

    def interrupted(number, frame):
        nonlocal cancelled
        cancelled = True
        if finalizing:
            return
        raise KeyboardInterrupt()

    signal.signal(signal.SIGTERM, interrupted)
    signal.signal(signal.SIGINT, interrupted)
    try:
        if context['event'] == 'LOCAL_CONTROL':
            if not args.provenance or not args.auth_image or not args.academic_image:
                raise ValueError('LOCAL_IMAGE_INPUTS')
            if args.provenance.is_symlink() or args.provenance.stat().st_size > 16384:
                raise ValueError('PROVENANCE_PATH')
            provenance = json.loads(args.provenance.read_text())
            images = validate_provenance(provenance, context, args.auth_image, args.academic_image)
            stack = provenance['project']
            project = 'rct-bootstrap-cli-' + str(time.time_ns()) + '-' + str(int(context['runIdentity'][-12:], 16))
            env.update(RCT_BOOTSTRAP_AUTH_IMAGE=images['auth'], RCT_BOOTSTRAP_ACADEMIC_IMAGE=images['academic'])
            write_json(output / 'image-provenance.json', provenance)
        else:
            if os.environ.get('RUNNER_ENVIRONMENT') != 'github-hosted':
                raise ValueError('HOSTED_SCOPE')
            stack = 'rct-e2e'
            project = env['RCT_BOOTSTRAP_CLI_PROJECT']
            for key, reference in (('auth', 'rct/auth-service:e2e-local'), ('academic', 'rct/academic-service:e2e-local'), ('postgres', 'postgres:16')):
                images[key] = commands.call(['docker', 'image', 'inspect', '--format', '{{.Id}}', reference]).decode().strip()
            env.update(RCT_BOOTSTRAP_AUTH_IMAGE='rct/auth-service:e2e-local', RCT_BOOTSTRAP_ACADEMIC_IMAGE='rct/academic-service:e2e-local')
        if not PROJECT.fullmatch(project):
            raise ValueError('PROJECT_SCOPE')
        if shutil.disk_usage(root).free < 4 * 1024**3:
            raise ValueError('DISK_RESERVE')
        memory = dict(line.split(':', 1) for line in Path('/proc/meminfo').read_text().splitlines())
        if int(memory['MemAvailable'].split()[0]) * 1024 < 4 * 1024**3:
            raise ValueError('MEMORY_RESERVE')
        commands.call(['docker', 'compose', 'version'])
        if commands.call(['docker', 'ps', '-q', '--filter', 'label=com.docker.compose.project=' + stack]).strip():
            raise ValueError('STACK_NOT_STOPPED')
        for image in images.values():
            if not IMAGE.fullmatch(image) or commands.call(['docker', 'image', 'inspect', '--format', '{{.Id}}', image]).decode().strip() != image:
                raise ValueError('IMAGE_IDENTITY')
        if commands.call(['docker', 'image', 'inspect', '--format', '{{.Id}}', 'postgres:16']).decode().strip() != images['postgres']:
            raise ValueError('POSTGRES_IMAGE_CHANGED')
        # An existing namespace is never adopted, even if its image IDs match.
        for label in ('rct.bootstrap.cli.owner', 'rct.bootstrap.cli.fixture'):
            if commands.call(['docker', 'ps', '-aq', '--filter', 'label=' + label + '=' + project]).strip():
                raise ValueError('PROJECT_COLLISION')
        scoped_path(root, root / RESULTS)
        if any((root / RESULTS).glob('TEST-*.xml')):
            raise ValueError('FRESH_XML_REQUIRED')
        tracked = commands.call(['git', 'show', 'HEAD:' + SOURCE])
        scoped_path(root, root / SOURCE)
        if tracked != (root / SOURCE).read_bytes():
            raise ValueError('SOURCE_IDENTITY')
        collector = load(root / 'scripts/collect-ci-test-outcomes.py', 'bootstrap_collector')
        temporary = tempfile.TemporaryDirectory(prefix=project + '-', dir=output)
        env.update(RCT_BOOTSTRAP_CLI_ACCEPTANCE='true', TESTCONTAINERS_RYUK_DISABLED='true',
                   TESTCONTAINERS_CHECKS_DISABLE='true', RCT_BOOTSTRAP_CLI_PROJECT=project,
                   RCT_BOOTSTRAP_CLI_DIRECTORY=temporary.name, RCT_CI_JOB_ID='e2e-auth',
                   RCT_CI_PR_HEAD_SHA=context['producerHeadSha'], RCT_CI_EVENT_NAME=context['event'])
        if context['event'] == 'LOCAL_CONTROL':
            if any(env.get(k) for k in ('RCT_CI_RUN_ID', 'RCT_CI_RUN_ATTEMPT')):
                raise ValueError('LOCAL_RUN_IDS')
        else:
            env.update(RCT_CI_RUN_ID=context['runId'], RCT_CI_RUN_ATTEMPT=context['runAttempt'])
        owned, invoked = True, True
        env['RCT_CI_TEST_OUTCOME'] = 'failure'
        commands.call(['bash', 'gradlew', ':services:academic-service:academic-app:integrationTest',
                       '--no-daemon', '--max-workers=1', '--rerun-tasks', '--no-build-cache',
                       '-Dorg.gradle.jvmargs=-Xmx512m -XX:ActiveProcessorCount=1',
                       '--tests', CLASS + '.' + METHOD], seconds=480)
        env['RCT_CI_TEST_OUTCOME'] = 'success'
        status, reason = 'PASS', 'NATIVE_PASS'
    except KeyboardInterrupt:
        cancelled = True
        status, reason = 'FAIL', 'CANCELLED'
        env['RCT_CI_TEST_OUTCOME'] = 'cancelled'
    except BaseException:
        command_reason = commands.records[-1]['reason'] if commands.records else None
        status, reason = 'FAIL', command_reason if command_reason in (
            'TIMEOUT', 'NATIVE_FAILURE', 'LOG_LIMIT', 'START_FAILED', 'COMMAND_ERROR') else 'NATIVE_OR_PREFLIGHT_FAILURE'
    finally:
        finalizing = True
        # Complete diagnostics before removing only current-run synthetic resources.
        if invoked:
            try:
                retain_xml(root, output)
                report = collector.collect(root, 'academic-service', 'integrationTest', env)
                write_json(output / 'safe-outcomes.json', report)
                expected_metadata = {
                    'checkoutSha': context['checkoutSha'], 'prHeadSha': context['producerHeadSha'],
                    'runId': context['runId'], 'runAttempt': context['runAttempt'],
                    'event': context['event'], 'testStepOutcome': 'success',
                    'service': 'academic-service', 'task': 'integrationTest', 'job': 'e2e-auth'}
                admit(report, expected_metadata, tracked)
                if (root / SOURCE).read_bytes() != tracked:
                    raise ValueError('SOURCE_CHANGED')
            except BaseException:
                diagnostic = 'NATIVE_PROOF_REJECTED'
        if tracked is not None:
            try:
                scoped_path(root, root / SOURCE)
                if (root / SOURCE).read_bytes() != tracked:
                    raise ValueError('SOURCE_CHANGED')
            except BaseException:
                holds.append('SOURCE_RESTORATION_UNCERTAIN')
        if owned:
            try:
                cleanup_owned(commands.call, project, images)
                cleanup = 'PASS'
            except BaseException:
                holds.append('OWNED_CLEANUP_UNCERTAIN')
                cleanup = 'HOLD'
        if commands.closure_unknown:
            holds.append('PROCESS_CLOSURE_UNCERTAIN')
            cleanup = 'HOLD'
        if temporary is not None and cleanup != 'HOLD':
            try:
                temporary.cleanup()
            except BaseException:
                holds.append('PRIVATE_CLEANUP_UNCERTAIN')
                cleanup = 'HOLD'
        elif temporary is not None:
            # Keep private config on uncertainty; do not delete files still in use.
            temporary._finalizer.detach()
        write_json(output / 'commands.private.json', commands.records)
        try:
            native_projection = native_diagnostic(output, commands.records)
        except BaseException:
            native_projection = {'state': 'UNREADABLE', 'nativeExit': None,
                                 'observations': [], 'sourceFrames': []}
        cancelled = cancelled or commands.cancelled
        status, reason, exit_code = finish_outcome(reason, diagnostic, holds, cancelled)
        safe = {'schemaVersion': 1, 'context': context, 'status': status, 'reason': reason,
                'diagnosticFailure': diagnostic, 'holdReasons': holds, 'cancelled': cancelled,
                'nativeDiagnostic': native_projection,
                'cleanup': cleanup, 'project': project, 'images': images,
                'nativeInvoked': invoked, 'nativeBudgetSeconds': 480, 'jobBudgetSeconds': 600,
                'sourceSha256': hashlib.sha256(tracked).hexdigest() if tracked else None}
        write_json(output / 'bootstrap-safe.json', safe)
        print(json.dumps(safe))
    return exit_code


if __name__ == '__main__':
    raise SystemExit(main())
