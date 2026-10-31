#!/usr/bin/env python3
"""Run the existing finite pure control; retain raw text privately and publish primitives."""
import ast
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import time

ROOT = Path(__file__).resolve().parents[1]
CONTROL = 'tests/harness/test_prepared_probe_projection.py'
SOURCES = (CONTROL, 'infra/prometheus/tests/check_pipeline.py',
           'infra/prometheus/tests/check_exporters.py',
           'services/notification-bot/tests/prepared_broker_probe.py')
CLASSES = frozenset(('AssertionError', 'ValueError', 'TypeError', 'KeyError', 'RuntimeError',
                     'OSError', 'TimeoutError', 'ImportError', 'ModuleNotFoundError', 'NameError'))


def project(raw, source):
    """Allow only authored test identities, fixed own paths and exception classes."""
    methods = {node.name for node in ast.walk(ast.parse(source))
               if isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef)) and node.name.startswith('test_')}
    failures = []
    current = None
    ran = None
    for line in raw.decode('utf-8', 'replace').splitlines():
        header = re.fullmatch(r'(FAIL|ERROR): (test_[A-Za-z0-9_]+) \(__main__\.[A-Za-z0-9_]+\.[A-Za-z0-9_]+\)', line)
        if header:
            current = {'kind': header[1], 'test': header[2] if header[2] in methods else 'OTHER',
                       'exceptionClass': 'OTHER', 'sourceFrame': None}
            if len(failures) < 32:
                failures.append(current)
        if current is not None:
            frame = re.fullmatch(r'  File "([^"]{1,2048})", line ([0-9]{1,6}), in [A-Za-z0-9_<>]+', line)
            if frame:
                for path in SOURCES:
                    if frame[1] == str(ROOT / path):
                        current['sourceFrame'] = {'path': path, 'line': int(frame[2])}
            category = re.match(r'^([A-Za-z]+Error):', line)
            if category:
                current['exceptionClass'] = category[1] if category[1] in CLASSES else 'OTHER'
        summary = re.fullmatch(r'Ran ([0-9]{1,5}) tests? in [0-9.]+s', line)
        if summary:
            ran = int(summary[1])
    return {'testsRun': ran, 'failures': failures, 'failureDetailsTruncated': len(failures) == 32}


def main():
    os.umask(0o077)
    output = ROOT / 'build/monitoring'
    output.mkdir(mode=0o700, parents=True, exist_ok=True)
    private = Path(os.environ.get('RUNNER_TEMP', str(output))) / 'prepared-projection.private.log'
    private.parent.mkdir(mode=0o700, parents=True, exist_ok=True)
    started = time.monotonic()
    code, timed_out = None, False
    with private.open('xb') as stream:
        try:
            child = subprocess.run([sys.executable, '-B', str(ROOT / CONTROL)], cwd=ROOT,
                                   stdout=stream, stderr=subprocess.STDOUT, timeout=60, check=False)
            code = child.returncode
        except subprocess.TimeoutExpired:
            timed_out = True
    size = private.stat().st_size
    with private.open('rb') as stream:
        raw = stream.read(1024 * 1024)
    result = {'schema': 'RCT_PREPARED_CONTROL_V1', 'result': 'PASS' if code == 0 and not timed_out else 'FAIL',
              'nativeExit': code, 'timedOut': timed_out, 'secondsLimit': 60,
              'elapsedMs': round((time.monotonic() - started) * 1000), 'privateLogBytes': size,
              'diagnosticReadTruncated': size > len(raw),
              'sourceSha256': {name: hashlib.sha256((ROOT / name).read_bytes()).hexdigest() for name in SOURCES},
              **project(raw, (ROOT / CONTROL).read_text())}
    with (output / 'prepared-control.safe.json').open('x') as stream:
        stream.write(json.dumps(result, sort_keys=True, indent=2) + '\n')
    print(json.dumps(result, sort_keys=True))
    return 1 if timed_out else code if code is not None and 0 <= code <= 255 else 1


if __name__ == '__main__':
    sys.exit(main())
