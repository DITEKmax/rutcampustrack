"""Finite numeric memory projections. No object, environment or property dumps."""
from pathlib import Path
import hashlib
import json
import os
import platform
import re
import subprocess
import time

MAX_AGENT = 16777216
BASE = {'schema', 'event', 'timestampMs', 'pid'}
FIELDS = {
    'START': {'javaVersion', 'javaVendor', 'heapInitBytes', 'heapMaxBytes', 'intervalMs', 'maxBytes', 'tcSessionId', 'xmxBytes'},
    'SAMPLE': {'heapUsedBytes', 'heapCommittedBytes', 'heapMaxBytes', 'nonHeapUsedBytes', 'nonHeapCommittedBytes', 'gcCount', 'gcTimeMs', 'processCpuTimeNs'},
    'GC': {'gcId', 'gcStartMs', 'gcEndMs', 'gcDurationMs', 'gcAction', 'heapUsedBeforeBytes', 'heapUsedAfterBytes', 'heapPools'},
    'STOP': {'reason'},
}


def integer(value):
    return type(value) is int and -1 <= value <= 2**63 - 1


def agent_record(row):
    if type(row) is not dict or row.get('event') not in FIELDS or set(row) != BASE | FIELDS[row['event']]:
        raise ValueError('AGENT_SCHEMA')
    if row['schema'] != 1 or not all(integer(row[k]) for k in ('timestampMs', 'pid')) or row['pid'] <= 0:
        raise ValueError('AGENT_IDENTITY')
    special = {'event', 'javaVersion', 'javaVendor', 'tcSessionId', 'gcAction', 'heapPools', 'reason', 'xmxBytes'}
    if any(not integer(v) for k, v in row.items() if k not in special):
        raise ValueError('AGENT_NUMBER')
    if row['event'] == 'START':
        if not re.fullmatch(r'[0-9.+]{1,64}', row['javaVersion']) or row['javaVendor'] not in {'TEMURIN', 'OTHER'} or row['tcSessionId'] is not None:
            raise ValueError('AGENT_PROFILE')
        if row['xmxBytes'] is not None and not integer(row['xmxBytes']):
            raise ValueError('AGENT_XMX')
    if row['event'] == 'GC':
        pools = row['heapPools']
        if row['gcAction'] not in {'MINOR', 'MAJOR', 'OTHER'} or type(pools) is not list or len(pools) > 32:
            raise ValueError('AGENT_GC')
        for pool in pools:
            if type(pool) is not dict or set(pool) != {'category', 'usedBeforeBytes', 'usedAfterBytes'} or pool['category'] not in {'EDEN', 'SURVIVOR', 'OLD', 'OTHER'} or not all(integer(pool[k]) for k in ('usedBeforeBytes', 'usedAfterBytes')):
                raise ValueError('AGENT_POOL')
    if row['event'] == 'STOP' and row['reason'] not in {'SHUTDOWN', 'SIZE_LIMIT'}:
        raise ValueError('AGENT_STOP')
    return row


def agent_file(path, heap_mib):
    path = Path(path)
    if path.is_symlink() or not path.is_file() or not 0 < path.stat().st_size <= MAX_AGENT:
        raise ValueError('AGENT_FILE')
    raw = path.read_text()
    lines = raw.splitlines()
    partial = not raw.endswith('\n')
    if partial:
        lines = lines[:-1]
    rows = [agent_record(json.loads(line)) for line in lines]
    if not rows or rows[0]['event'] != 'START' or sum(r['event'] == 'START' for r in rows) != 1:
        raise ValueError('AGENT_START')
    start = rows[0]
    if start['javaVersion'] != '21.0.13+9' or start['javaVendor'] != 'TEMURIN' or start['xmxBytes'] != heap_mib * 1024**2 or start['heapMaxBytes'] <= 0:
        raise ValueError('AGENT_PROFILE_MISMATCH')
    if any(r['pid'] != start['pid'] for r in rows):
        raise ValueError('AGENT_PID')
    complete = not partial and rows[-1]['event'] == 'STOP' and rows[-1]['reason'] == 'SHUTDOWN' and any(r['event'] == 'SAMPLE' and all(r[k] >= 0 for k in ('heapUsedBytes', 'heapMaxBytes', 'nonHeapUsedBytes', 'gcCount', 'gcTimeMs', 'processCpuTimeNs')) for r in rows)
    samples = [r for r in rows if r['event'] == 'SAMPLE']
    major = [r for r in rows if r['event'] == 'GC' and r['gcAction'] == 'MAJOR']
    growth = len(major) >= 3 and major[-1]['gcEndMs'] - major[-3]['gcEndMs'] >= 30000 and major[-3]['heapUsedAfterBytes'] < major[-2]['heapUsedAfterBytes'] < major[-1]['heapUsedAfterBytes'] and major[-1]['heapUsedAfterBytes'] - major[-3]['heapUsedAfterBytes'] >= heap_mib * 1024**2 // 10
    return {'complete': complete, 'pid': start['pid'], 'start': start, 'sampleCount': len(samples), 'gcCount': sum(r['event'] == 'GC' for r in rows), 'majorGcCount': len(major), 'majorGcAssessable': len(major) >= 3 and major[-1]['gcEndMs'] - major[-3]['gcEndMs'] >= 30000, 'retainedGrowthSuspected': growth,
            'peakHeapUsedBytes': max((r['heapUsedBytes'] for r in samples), default=None),
            'peakNonHeapUsedBytes': max((r['nonHeapUsedBytes'] for r in samples), default=None),
            'partialFinalLine': partial, 'records': rows}


def memory():
    result = {}
    for line in Path('/proc/meminfo').read_text().splitlines():
        key, *values = line.split()
        if key in {'MemTotal:', 'MemAvailable:', 'SwapTotal:', 'SwapFree:'}:
            result[key[:-1] + 'Bytes'] = int(values[0]) * 1024
    group = Path('/sys/fs/cgroup')
    membership = Path('/proc/self/cgroup').read_text().splitlines()
    relative = next((line[3:] for line in membership if line.startswith('0::')), '/')
    candidate = group / relative.lstrip('/')
    matched = candidate.resolve().is_relative_to(group) and candidate.is_dir()
    if matched:
        group = candidate
    result['cgroupScope'] = 'PROCESS' if matched else 'ROOT_FALLBACK'
    for name in ('memory.current', 'memory.max', 'memory.peak'):
        path = group / name
        value = path.read_text().strip() if path.is_file() else 'UNKNOWN'
        result[name] = int(value) if value.isdecimal() else None
    events = {}
    if (group / 'memory.events').is_file():
        for line in (group / 'memory.events').read_text().splitlines():
            key, value = line.split()
            if key in {'low', 'high', 'max', 'oom', 'oom_kill', 'oom_group_kill'} and value.isdecimal():
                events[key] = int(value)
    result['cgroupEvents'] = events
    return result


def profile():
    release = {}
    for line in Path('/etc/os-release').read_text().splitlines():
        if '=' in line:
            key, value = line.split('=', 1)
            if key in {'ID', 'VERSION_ID'}:
                release[key] = value.strip('"')
    return {'os': 'UBUNTU_24_04' if release == {'ID': 'ubuntu', 'VERSION_ID': '24.04'} else 'OTHER', 'architecture': 'X64' if platform.machine() == 'x86_64' else 'OTHER',
            'kernelFamily': 'WSL' if 'microsoft' in platform.release().lower() else 'LINUX', 'cpuCount': os.cpu_count(), 'affinityCount': len(os.sched_getaffinity(0)), 'memory': memory(),
            'hostedReference': {'cpuCount': 4, 'memoryBytes': 16 * 1024**3}, 'qualification': 'OBSERVED_HOST_PROFILE; WSL is not a hosted VM; Docker siblings are outside the Java cgroup'}


def docker_snapshot(seconds=30):
    deadline = time.monotonic() + seconds
    def capture(args):
        remaining = deadline - time.monotonic()
        if remaining <= 0:
            raise TimeoutError('DOCKER_SNAPSHOT_TIME')
        return subprocess.check_output(['docker', *args], stderr=subprocess.DEVNULL, text=True, timeout=min(10, remaining)).splitlines()
    specs = (
        ('containers', ['ps', '-aq', '--no-trunc'], 'inspect', '{{.Id}}|{{.Image}}|{{.State.Status}}|{{.State.Pid}}|{{.State.StartedAt}}|{{.RestartCount}}|{{.HostConfig.Memory}}|{{.HostConfig.NanoCpus}}'),
        ('networks', ['network', 'ls', '-q', '--no-trunc'], 'network', '{{.Id}}|{{.Created}}|{{.Driver}}|{{.Scope}}|{{.Internal}}|{{range $id, $value := .Containers}}{{$id}}:{{$value.EndpointID}};{{end}}'),
        ('volumes', ['volume', 'ls', '-q'], 'volume', '{{.Name}}|{{.CreatedAt}}|{{.Driver}}|{{.Mountpoint}}|{{.Scope}}'),
    )
    result = {}
    for kind, listing, command, format_string in specs:
        identifiers = sorted(capture(listing))
        if len(identifiers) > 4096 or any(not re.fullmatch(r'[A-Za-z0-9_.-]{1,256}', item) for item in identifiers):
            raise ValueError('DOCKER_INVENTORY_BOUND')
        rows = []
        for identifier in identifiers:
            argv = ['inspect'] if command == 'inspect' else [command, 'inspect']
            row = capture([*argv, '--format', format_string, identifier])
            if len(row) != 1 or len(row[0]) > 65536:
                raise ValueError('DOCKER_PIN_BOUND')
            rows.append(row[0])
        result[kind] = rows
    return result


def docker_public(snapshot):
    return {kind: {'count': len(rows), 'pinsSha256': hashlib.sha256(json.dumps(rows, sort_keys=True).encode()).hexdigest()} for kind, rows in snapshot.items()}


def conclusion(arms):
    if any((a.get('cgroupOomDelta') or 0) > 0 or a.get('nativeMemoryFailure') for a in arms):
        return 'ENVIRONMENT_LIMIT'
    if not arms or any(not a.get('telemetryComplete') for a in arms):
        return 'INCONCLUSIVE'
    if any(a.get('retainedGrowthSuspected') for a in arms):
        return 'RETAINED_HEAP_GROWTH_SUSPECTED'
    baseline = next((a for a in arms if a['heapMiB'] == 512), None)
    target = next((a for a in arms if a['heapMiB'] == 1024), None)
    if baseline and baseline.get('javaHeapOom') and target and target.get('nativeExit') == 0:
        return 'LOW_HEAP_SUSPECTED'
    if any(not a.get('majorGcAssessable') for a in arms):
        return 'INCONCLUSIVE'
    if all(a.get('nativeExit') == 0 and not a.get('javaHeapOom') for a in arms):
        return 'NOT_REPRODUCED'
    return 'INCONCLUSIVE'
