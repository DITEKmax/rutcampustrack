#!/usr/bin/env python3
"""Narrow Gitleaks proof; outputs only safe metadata, private raw logs remain local."""
import hashlib
import json
import os
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parent
WT = Path('/workspace/rct-worktrees/security-fingerprint')
BASE = 'eec3e0a864626160d33ae29da2cf3b0640e30caa'
HIST = '63647fc9b63175e1e32b0773d57affc637ea1065'
PATH = 'docs/validation/cloud-20261011/packet-export-backend-security-review.md'
FP = HIST + ':' + PATH + ':generic-api-key:12'
RANGE = 'e99abf06062f8efd2697da4dc424658feaee08b0^..' + HIST
IGNORE = '# Historical architecture prose false positive verified with Gitleaks 8.24.3.\n' + FP + '\n'
os.umask(0o077)

def git(*args):
    return subprocess.check_output(['git', *args], cwd=WT)

def save(name, value):
    (ROOT / name).write_text(json.dumps(value, indent=2, ensure_ascii=False) + '\n')

def manifest():
    paths = git('ls-files', '-z').decode().split('\0')[:-1]
    return {p: {'sha256': hashlib.sha256((WT / p).read_bytes()).hexdigest(),
                'mode': oct((WT / p).stat().st_mode & 0o777)} for p in paths}

def scan(name, logopts, ignore):
    report = ROOT / (name + '.private-redacted.json')
    cmd = [str(ROOT / 'gitleaks'), 'git', '--no-banner', '--no-color', '--redact=100',
           '--report-format=json', '--report-path=' + str(report),
           '--log-opts=' + logopts, '--gitleaks-ignore-path=' + str(ignore), str(WT)]
    run = subprocess.run(cmd, cwd=WT, capture_output=True)
    (ROOT / (name + '.private.log')).write_bytes(run.stdout + run.stderr)
    findings = json.loads(report.read_text()) if report.exists() else []
    result = {'command': cmd, 'exit_code': run.returncode, 'findings_count': len(findings),
              'findings': [{k: f.get(k) for k in ['RuleID', 'File', 'StartLine', 'EndLine', 'Commit', 'Fingerprint']} for f in findings],
              'report_sha256': hashlib.sha256(report.read_bytes()).hexdigest() if report.exists() else None}
    save(name + '.safe.json', result)
    print(json.dumps({'stage': name, 'exit_code': run.returncode, 'findings_count': len(findings), 'findings': result['findings']}))
    return result

if __name__ == '__main__':
    assert git('rev-parse', 'HEAD').decode().strip() == BASE
    assert git('status', '--porcelain') == b''
    assert not (WT / '.gitleaksignore').exists()
    save('original-manifest.json', manifest())
    historical = git('show', HIST + ':' + PATH)
    line = historical.decode().splitlines()[11]
    save('historical-source.safe.json', {'commit': HIST, 'path': PATH, 'line': 12,
         'blob_sha256': hashlib.sha256(historical).hexdigest(),
         'line_sha256': hashlib.sha256(line.encode()).hexdigest(),
         'classification': 'Architecture prose describing REST Auth Academic and export scope; no credential assignment.'})
    empty = ROOT / 'empty-ignore'; empty.mkdir(exist_ok=True)
    red = scan('baseline-historical', '--no-merges --first-parent ' + RANGE, empty)
    assert red['exit_code'] == 1 and red['findings_count'] == 1
    assert red['findings'][0]['Fingerprint'] == FP
    (WT / '.gitleaksignore').write_text(IGNORE)
    green = scan('corrected-historical', '--no-merges --first-parent ' + RANGE, WT)
    assert green['exit_code'] == 0 and green['findings_count'] == 0
    assert manifest() == json.loads((ROOT / 'original-manifest.json').read_text())
    save('historical-proof.safe.json', {'baseline': BASE, 'history': RANGE,
         'commits': len(git('rev-list', '--no-merges', '--first-parent', RANGE).splitlines()),
         'scanner_version': subprocess.check_output([str(ROOT / 'gitleaks'), 'version']).decode().strip(),
         'red': 'baseline-historical.safe.json', 'green': 'corrected-historical.safe.json',
         'ignore_sha256': hashlib.sha256((WT / '.gitleaksignore').read_bytes()).hexdigest(),
         'tracked_source_identity_preserved': True})
