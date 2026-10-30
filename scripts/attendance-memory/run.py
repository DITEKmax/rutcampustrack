#!/usr/bin/env python3
"""Same bounded Attendance Coverage target in CI/local; optional explicit local A/B."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import selectors
import shutil
import signal
import subprocess
import sys
import time
import traceback

from collect import agent_file, conclusion, docker_public, docker_snapshot, memory, profile
from toolchain import ensure, SHA256, VERSION

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'scripts'))
from local_release_context import source_context

MODULE = ':services:attendance-service:attendance-app'
APP = ROOT / 'services/attendance-service/attendance-app'
ARM_SECONDS = 1800
LOG_BOUND = 512 * 1024**2
CANCELLED = False


def request_cancel(*_):
    global CANCELLED
    CANCELLED = True
EXPECTED_TEST_TREE = 'fa2953ef550d1150efcb86cd0d942b63158ed016c1e35366c263c446d463236b'


def test_tree():
    digest = hashlib.sha256()
    base = APP / 'src/test/java'
    for path in sorted(base.rglob('*.java')):
        digest.update(path.relative_to(base).as_posix().encode() + b'\0' + hashlib.sha256(path.read_bytes()).digest())
    return digest.hexdigest()


def write(path, value):
    with path.open('x') as output:
        output.write(json.dumps(value, sort_keys=True, indent=2, allow_nan=False) + '\n')


def absent(pgid):
    try:
        os.killpg(pgid, 0)
    except ProcessLookupError:
        return True
    return False


def rss(pid, pgid):
    try:
        if os.getpgid(pid) != pgid:
            return None
        for line in Path(f'/proc/{pid}/status').read_text().splitlines():
            if line.startswith('VmRSS:'):
                return int(line.split()[1]) * 1024
    except (OSError, ValueError):
        pass
    return None


def reap_group(process):
    """Registered own group only; a departed leader never hides surviving children."""
    escalated = False
    for sig, budget in ((None, 1), (signal.SIGINT, 30), (signal.SIGTERM, 5), (signal.SIGKILL, 5)):
        if absent(process.pid):
            break
        if sig is not None:
            escalated = True
            try:
                os.killpg(process.pid, sig)
            except ProcessLookupError:
                pass
        deadline = time.monotonic() + budget
        while time.monotonic() < deadline:
            process.poll()
            if absent(process.pid):
                break
            time.sleep(0.05)
    process.poll()
    return absent(process.pid), escalated


def native(arm, heap, jar, environment):
    telemetry = arm / 'jvm'
    telemetry.mkdir(mode=0o700)
    command = [str(ROOT / 'gradlew'), MODULE + ':test', MODULE + ':integrationTest', MODULE + ':jacocoTestReport',
               '--no-daemon', '--max-workers=4', f'-Prct.attendance.heapMiB={heap}',
               f'-Prct.attendance.memoryAgent={jar}', f'-Prct.attendance.memoryDir={telemetry}']
    approved = {p.relative_to(APP / 'src/test/java').with_suffix('').as_posix().replace('/', '.') for p in (APP / 'src/test/java').rglob('*.java')}
    progress, samples, pending, total = [], [], b'', 0
    worker_policy = {}
    start = time.monotonic()
    process, reason, code = None, None, None
    closed = False
    with (arm / 'gradle.private.log').open('xb') as log:
        try:
            # Entry-point handlers only set CANCELLED. A signal between spawn and
            # publication cannot throw away a newly created own process group.
            process = subprocess.Popen(command, cwd=ROOT, env=environment, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, stdin=subprocess.DEVNULL, start_new_session=True)
            if CANCELLED:
                raise KeyboardInterrupt()
            with selectors.DefaultSelector() as ready:
                ready.register(process.stdout, selectors.EVENT_READ)
                tick = heartbeat = start
                while ready.get_map() or process.poll() is None:
                    now = time.monotonic()
                    if CANCELLED:
                        raise KeyboardInterrupt()
                    if now - start > ARM_SECONDS:
                        reason = 'TIME_LIMIT'
                        raise TimeoutError('ARM_TIME_LIMIT')
                    if now >= tick:
                        sample = {'elapsedMs': round((now - start) * 1000), 'host': memory(), 'jvmRssBytes': {}}
                        for name in ('test', 'integrationTest'):
                            path = telemetry / (name + '.memory.jsonl')
                            if path.is_file() and not path.is_symlink():
                                try:
                                    with path.open() as input:
                                        first = json.loads(input.readline(4096))
                                    pid = first.get('pid')
                                    if type(pid) is int and 0 < pid <= 2**31 - 1:
                                        sample['jvmRssBytes'][name] = rss(pid, process.pid)
                                except (OSError, ValueError):
                                    pass
                        if len(samples) < 3601:
                            samples.append(sample)
                        tick = now + 1
                    if now >= heartbeat:
                        print(json.dumps({'event': 'ATTENDANCE_MEMORY_PROGRESS', 'heapMiB': heap, 'elapsedSeconds': int(now - start)}), flush=True)
                        heartbeat = now + 30
                    for key, _ in ready.select(0.25):
                        chunk = os.read(key.fd, 65536)
                        if not chunk:
                            ready.unregister(key.fileobj)
                            continue
                        total += len(chunk)
                        if total > LOG_BOUND:
                            reason = 'LOG_BOUND'
                            raise ValueError('ARM_LOG_BOUND')
                        log.write(chunk)
                        log.flush()
                        pending = (pending + chunk)[-262144:]
                        lines = pending.split(b'\n')
                        pending = lines.pop()
                        for line in lines:
                            text = line.decode('utf-8', 'replace').strip()
                            policy = re.fullmatch(r'RCT_ATTENDANCE_WORKERS_V1\|:services:attendance-service:attendance-app:(test|integrationTest)\|([0-9]{1,3})\|([0-9]{1,3})\|([0-9]{1,4})', text)
                            if policy:
                                worker_policy[policy[1]] = {'workers': int(policy[2]), 'maxParallelForks': int(policy[3]), 'forkEvery': int(policy[4])}
                            match = re.fullmatch(r'RCT_GRADLE_V1\|(TEST_START|TEST_DONE|CLASS_DONE)\|:services:attendance-service:attendance-app:(test|integrationTest)\|([A-Za-z0-9_.$]+)\|([0-9|NONE]+)', text)
                            if match and match[3].split('$')[0] in approved and len(progress) < 4096:
                                progress.append({'elapsedMs': round((time.monotonic() - start) * 1000), 'event': match[1], 'task': match[2], 'class': match[3].split('$')[0]})
            code = process.wait(timeout=5)
        except BaseException as error:
            reason = reason or ('CANCELLED' if isinstance(error, KeyboardInterrupt) else 'CONTROL_ERROR')
        finally:
            if process is not None:
                closed, escalated = reap_group(process)
                code = process.returncode
                if escalated and reason is None:
                    reason = 'OWNED_GROUP_ESCALATION'
                if process.stdout is not None:
                    process.stdout.close()
            if CANCELLED:
                reason = 'CANCELLED'
    return {'nativeExit': code, 'cause': reason, 'processGroup': process.pid if process else None,
            'processGroupAbsent': closed, 'actualWorkerPolicy': worker_policy,
            'elapsedMs': round((time.monotonic() - start) * 1000), 'resourceSamples': samples,
            'sourceProgress': progress, 'sourceProgressTruncated': len(progress) == 4096}


def error_class(error):
    name = type(error).__name__
    return name if name in {'TimeoutError', 'TimeoutExpired', 'ValueError', 'OSError', 'KeyboardInterrupt', 'CalledProcessError', 'FileNotFoundError', 'PermissionError', 'TypeError', 'KeyError'} else 'OTHER'


def collect_arm(directory, result, environment):
    env = {**environment, 'RCT_CI_TEST_OUTCOME': 'success' if result['nativeExit'] == 0 else 'failure'}
    if environment.get('GITHUB_ACTIONS') == 'true':
        for target, source in (('RCT_CI_EVENT_NAME', 'GITHUB_EVENT_NAME'), ('RCT_CI_RUN_ID', 'GITHUB_RUN_ID'), ('RCT_CI_RUN_ATTEMPT', 'GITHUB_RUN_ATTEMPT'), ('RCT_CI_JOB_ID', 'GITHUB_JOB'), ('RCT_CI_PR_HEAD_SHA', 'GITHUB_SHA')):
            env[target] = environment[source]
    with (directory / 'collect.private.log').open('xb') as log:
        for task, count in (('test', 419), ('integrationTest', 266)):
            try:
                call = subprocess.run([sys.executable, str(ROOT / 'scripts/collect-ci-test-outcomes.py'), '--service', 'attendance-service', '--task', task], cwd=ROOT, env=env, stdout=log, stderr=subprocess.STDOUT, timeout=60)
                result[task + 'CollectionExit'] = call.returncode
                source = ROOT / 'build/ci-test-outcomes/attendance-service' / task / 'safe-outcomes.json'
                report = json.loads(source.read_text())
                shutil.copyfile(source, directory / (task + '.safe.json'))
                result[task + 'Summary'] = report['summary']
                result['rosterConfirmed'] &= report['summary'] == {'PASSED': count, 'FAILED': 0, 'ERROR': 0, 'SKIPPED': 0, 'NOT_EXECUTED': 0}
                families = {row.get('failureDiagnostic', {}).get('oomResource', {}).get('family') for row in report.get('cases', [])}
                result['javaHeapOom'] |= 'JAVA_HEAP' in families
                result['nativeMemoryFailure'] |= bool(families & {'METASPACE', 'DIRECT_BUFFER', 'DIRECT_MEMORY', 'NATIVE_THREAD', 'NATIVE_ALLOCATION'})
                source_xml = APP / 'build/test-results' / task
                if source_xml.is_dir():
                    shutil.copytree(source_xml, directory / (task + '-xml.private'))
            except Exception as error:
                result[task + 'CollectionExit'] = None
                result[task + 'CollectionError'] = error_class(error)
                result['diagnosticsFailed'] = True
    xml = APP / 'build/reports/jacoco/test/jacocoTestReport.xml'
    result['jacocoPresent'] = xml.is_file() and xml.stat().st_size > 0
    if result['jacocoPresent']:
        shutil.copyfile(xml, directory / 'jacoco.private.xml')


def project_arm(directory, heap, result):
    with (directory / 'gradle.private.log').open('rb') as log:
        previous = b''
        while chunk := log.read(1024 * 1024):
            raw = previous + chunk
            result['javaHeapOom'] |= b'java.lang.OutOfMemoryError: Java heap space' in raw or b'java.lang.OutOfMemoryError: GC overhead limit exceeded' in raw
            result['nativeMemoryFailure'] |= any(marker in raw for marker in (b'unable to create native thread', b'OutOfMemoryError: Metaspace', b'OutOfMemoryError: Direct buffer memory'))
            result['probeFailureMarker'] |= bool(re.search(rb'RCT_MEMORY_PROBE_(?:ARGUMENTS|PATH|IO|MONITOR|SIZE_LIMIT)', raw))
            previous = raw[-256:]
    details = {}
    for task in ('test', 'integrationTest'):
        try:
            details[task] = agent_file(directory / 'jvm' / (task + '.memory.jsonl'), heap)
            records = details[task].pop('records')
            name = 'heap-' + str(heap) + '-' + task + '.safe.jsonl'
            data = ''.join(json.dumps(row, sort_keys=True, separators=(',', ':')) + '\n' for row in records)
            with (ROOT / 'build/attendance-memory' / name).open('x') as output:
                output.write(data)
            details[task]['numericArtifact'] = name
            details[task]['numericArtifactSha256'] = hashlib.sha256(data.encode()).hexdigest()
        except FileNotFoundError:
            details[task] = {'complete': False, 'status': 'MISSING_OR_REFUSED'}
        except (OSError, ValueError, TypeError, KeyError):
            details[task] = {'complete': False, 'status': 'MISSING_OR_REFUSED'}
            result['diagnosticsFailed'] = True
    result['jvm'] = details
    result['workerPolicyConfirmed'] = result.get('actualWorkerPolicy') == {task: {'workers': 4, 'maxParallelForks': 1, 'forkEvery': 0} for task in ('test', 'integrationTest')}
    result['telemetryComplete'] = all(row['complete'] for row in details.values()) and not result['probeFailureMarker'] and result['workerPolicyConfirmed']
    result['majorGcAssessable'] = all(row.get('majorGcAssessable', False) for row in details.values())
    result['retainedGrowthSuspected'] = any(row.get('retainedGrowthSuspected', False) for row in details.values())


def close_arm(directory, before, initial, result):
    try:
        final = memory()
    except Exception as error:
        final = None
        result['memoryError'] = error_class(error)
        result['diagnosticsFailed'] = True
    result['memoryBefore'], result['memoryAfter'] = initial, final
    result['cgroupOomDelta'] = max(0, final['cgroupEvents'].get('oom_kill', 0) - initial['cgroupEvents'].get('oom_kill', 0)) if initial is not None and final is not None else None
    deadline = time.monotonic() + 30
    observed = None
    while before is not None and time.monotonic() < deadline:
        try:
            observed = docker_snapshot(min(5, max(0.01, deadline - time.monotonic())))
            if observed == before and result.get('processGroup') is not None and absent(result['processGroup']):
                result['processGroupAbsent'] = True
                break
        except (OSError, ValueError, subprocess.SubprocessError):
            observed = None
        time.sleep(min(1, max(0, deadline - time.monotonic())))
    if observed is not None:
        write(directory / 'docker-after.private.json', observed)
    result['dockerBefore'] = docker_public(before) if before is not None else None
    result['dockerAfter'] = docker_public(observed) if observed is not None else None
    result['cleanup'] = before is not None and observed == before and result.get('processGroupAbsent') is True


def arm_run(directory, heap, jar, environment):
    directory.mkdir(mode=0o700)
    result = {'heapMiB': heap, 'nativeExit': None, 'cause': None, 'cleanup': False, 'telemetryComplete': False,
              'javaHeapOom': False, 'nativeMemoryFailure': False, 'retainedGrowthSuspected': False,
              'diagnosticsFailed': False, 'probeFailureMarker': False, 'rosterConfirmed': False, 'jacocoPresent': False}
    before = initial = None
    try:
        before = docker_snapshot()
        write(directory / 'docker-before.private.json', before)
        initial = memory()
        if CANCELLED:
            raise KeyboardInterrupt()
        result.update(native(directory, heap, jar, environment))
        result['rosterConfirmed'] = test_tree() == EXPECTED_TEST_TREE
    except BaseException as error:
        result['controlClass'] = error_class(error)
        result['cause'] = 'CANCELLED' if isinstance(error, KeyboardInterrupt) else 'CONTROL_ERROR'
    # Independent phases: diagnostic failures cannot jump over resource closure.
    try:
        collect_arm(directory, result, environment)
    except BaseException as error:
        result['collectionError'] = error_class(error)
        result['diagnosticsFailed'] = True
    try:
        project_arm(directory, heap, result)
    except BaseException as error:
        result['projectionError'] = error_class(error)
        result['diagnosticsFailed'] = True
    try:
        close_arm(directory, before, initial, result)
    except BaseException as error:
        result['closureError'] = error_class(error)
        result['cleanup'] = False
    if CANCELLED:
        result['cause'] = 'CANCELLED'
    result['result'] = 'HOLD' if result['diagnosticsFailed'] or not result['cleanup'] or result['cause'] is not None else 'PASS' if result['nativeExit'] == 0 and result['telemetryComplete'] and result['rosterConfirmed'] and result['jacocoPresent'] and all(result.get(t + 'CollectionExit') == 0 for t in ('test', 'integrationTest')) else 'FAIL'
    try:
        write(directory / 'arm.safe.json', result)
    except Exception as error:
        result['receiptError'] = error_class(error)
        result['diagnosticsFailed'] = True
        result['result'] = 'HOLD'
    return result


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--arms', choices=('target', 'comparison'), default='target')
    args = parser.parse_args()
    os.umask(0o077)
    context = source_context()
    if args.arms == 'comparison' and context['event'] != 'LOCAL_CONTROL':
        raise ValueError('COMPARISON_LOCAL_ONLY')
    if subprocess.check_output(['git', '-c', 'core.fileMode=false', 'status', '--porcelain'], cwd=ROOT, text=True).strip():
        raise ValueError('CLEAN_FRESH_SOURCE_REQUIRED')
    nonce = os.environ.get('RCT_LOCAL_RELEASE_NONCE') or os.urandom(12).hex()
    parent = Path(os.environ.get('RCT_LOCAL_RELEASE_AUDIT', os.environ.get('RUNNER_TEMP', '/tmp')))
    output = parent / ('attendance-memory-' + nonce)
    output.mkdir(mode=0o700)
    summary = {'schema': 'RCT_ATTENDANCE_MEMORY_V1', 'context': context, 'armsMode': args.arms, 'arms': [], 'result': 'HOLD', 'conclusion': 'INCONCLUSIVE', 'heapPolicyMiB': [512, 1024] if args.arms == 'comparison' else [1024], 'workers': 4, 'maxParallelForks': 1, 'forkEvery': 0, 'initialHeapPolicy': 'UNSET_JVM_DEFAULT; historical initial heap unknown', 'profile': profile(), 'jdkVersion': VERSION, 'jdkArchiveSha256': SHA256, 'enrolledTestTreeSha256': EXPECTED_TEST_TREE, 'expectedPassed': {'test': 419, 'integrationTest': 266}, 'qualification': 'Diagnostic arms retain native failures; NOT_REPRODUCED does not prove absence of leaks; WSL is not hosted-VM equivalence'}
    public = ROOT / 'build/attendance-memory'
    public.mkdir(mode=0o700, parents=True, exist_ok=True)
    try:
        if summary['profile']['os'] != 'UBUNTU_24_04' or summary['profile']['architecture'] != 'X64' or summary['profile']['affinityCount'] != 4:
            raise ValueError('HOST_PROFILE_MISMATCH')
        if test_tree() != EXPECTED_TEST_TREE:
            raise ValueError('ENROLLED_TEST_TREE_CHANGED_REBIND_ROSTER')
        home = ensure(output / 'toolchain')
        classes = output / 'classes'
        classes.mkdir(mode=0o700)
        manifest = output / 'manifest.mf'
        manifest.write_text('Manifest-Version: 1.0\nPremain-Class: MemoryProbeAgent\n\n')
        jar = output / 'memory-agent.jar'
        with (output / 'agent-compile.private.log').open('xb') as log:
            subprocess.run([str(home / 'bin/javac'), '--release', '21', '-d', str(classes), str(ROOT / 'scripts/attendance-memory/MemoryProbeAgent.java')], stdout=log, stderr=subprocess.STDOUT, timeout=60, check=True)
            subprocess.run([str(home / 'bin/jar'), '--create', '--file', str(jar), '--manifest', str(manifest), '-C', str(classes), '.'], stdout=log, stderr=subprocess.STDOUT, timeout=30, check=True)
        allowed = {'PATH', 'HOME', 'USER', 'LOGNAME', 'LANG', 'LC_ALL', 'TERM', 'TMPDIR', 'GRADLE_USER_HOME', 'DOCKER_HOST', 'HTTP_PROXY', 'HTTPS_PROXY', 'NO_PROXY', 'http_proxy', 'https_proxy', 'no_proxy', 'RCT_LOCAL_RELEASE_REVISION', 'RCT_LOCAL_RELEASE_NONCE', 'RCT_LOCAL_RELEASE_AUDIT', 'GITHUB_ACTIONS', 'GITHUB_SHA', 'GITHUB_RUN_ID', 'GITHUB_RUN_ATTEMPT', 'GITHUB_EVENT_NAME', 'GITHUB_JOB'}
        env = {k: v for k, v in os.environ.items() if k in allowed}
        env.update(JAVA_HOME=str(home), PATH=str(home / 'bin') + ':' + env.get('PATH', ''), TZ='UTC', LANG='C.UTF-8', LC_ALL='C.UTF-8', PYTHONDONTWRITEBYTECODE='1')
        for heap in summary['heapPolicyMiB']:
            if CANCELLED:
                break
            arm = arm_run(output / ('heap-' + str(heap)), heap, jar, env)
            summary['arms'].append(arm)
            if not arm['cleanup'] or arm['result'] == 'HOLD':
                break
        summary['conclusion'] = conclusion(summary['arms']) if len(summary['arms'])==len(summary['heapPolicyMiB']) and all(row['cleanup'] for row in summary['arms']) else 'INCONCLUSIVE'
        summary['result'] = 'HOLD' if any(not a['cleanup'] or a['result'] == 'HOLD' for a in summary['arms']) else 'PASS' if len(summary['arms']) == len(summary['heapPolicyMiB']) and all(a['result'] == 'PASS' for a in summary['arms']) else 'FAIL'
    except BaseException as error:
        with (output / 'control.private.log').open('x') as log:
            traceback.print_exception(error, limit=16, file=log)
        summary['controlClass'] = type(error).__name__ if type(error).__name__ in {'ValueError', 'OSError', 'CalledProcessError', 'TimeoutExpired', 'KeyboardInterrupt'} else 'OTHER'
        summary['result'] = 'HOLD'
    finally:
        summary['cancelled'] = CANCELLED
        if CANCELLED:summary['result']='HOLD'
        for arm in summary['arms']:
            try:write(public / ('heap-' + str(arm['heapMiB']) + '.safe.json'), arm)
            except Exception:summary['result']='HOLD';summary['safeArtifactWriteFailed']=True
        summary['arms'] = [{k: v for k, v in arm.items() if k not in {'resourceSamples', 'sourceProgress'}} for arm in summary['arms']]
        try:write(public / 'summary.safe.json', summary)
        except Exception:summary['result']='HOLD';summary['safeArtifactWriteFailed']=True
        print(json.dumps({'result': summary['result'], 'conclusion': summary['conclusion'], 'nativeExits': [a['nativeExit'] for a in summary['arms']]}), flush=True)
    return 130 if CANCELLED else 0 if summary['result'] == 'PASS' else 2 if summary['result'] == 'HOLD' else 1


if __name__ == '__main__':
    signal.signal(signal.SIGTERM, request_cancel)
    signal.signal(signal.SIGINT, request_cancel)
    sys.exit(main())
