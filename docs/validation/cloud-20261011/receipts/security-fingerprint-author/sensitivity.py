#!/usr/bin/env python3
"""Create a synthetic commit in a separate local object store, never author worktree."""
import hashlib
import json
import os
from pathlib import Path
import secrets
import subprocess
from verify import ROOT, WT, BASE, HIST, PATH, FP, manifest, save

os.umask(0o077)
controlled = ROOT / 'controlled-repo'
assert not controlled.exists()
subprocess.run(['git', 'init', '--quiet', str(controlled)], check=True)
objects = subprocess.check_output(['git', 'rev-parse', '--path-format=absolute', '--git-common-dir'], cwd=WT).decode().strip() + '/objects'
(controlled / '.git/objects/info/alternates').write_text(objects + '\n')

def git(*args, data=None, env=None):
    return subprocess.check_output(['git', *args], cwd=controlled, input=data, env=env)

git('read-tree', BASE)
original = git('show', BASE + ':' + PATH)
lines = original.decode().splitlines(keepends=True)
lines[11] = 'api_key="' + secrets.token_hex(24) + '"\n'
synthetic = ''.join(lines).encode()
blob = git('hash-object', '-w', '--stdin', data=synthetic).decode().strip()
git('update-index', '--cacheinfo', '100644', blob, PATH)
ignore = (WT / '.gitleaksignore').read_bytes()
ignore_blob = git('hash-object', '-w', '--stdin', data=ignore).decode().strip()
git('update-index', '--add', '--cacheinfo', '100644', ignore_blob, '.gitleaksignore')
tree = git('write-tree').decode().strip()
env = os.environ.copy()
env.update({'GIT_AUTHOR_NAME': 'Synthetic scanner sensitivity', 'GIT_AUTHOR_EMAIL': 'scanner@example.invalid',
            'GIT_COMMITTER_NAME': 'Synthetic scanner sensitivity', 'GIT_COMMITTER_EMAIL': 'scanner@example.invalid',
            'GIT_AUTHOR_DATE': '2026-10-12T00:00:00Z', 'GIT_COMMITTER_DATE': '2026-10-12T00:00:00Z'})
commit = git('commit-tree', tree, '-p', BASE, data=b'Synthetic random fake scanner sensitivity only\n', env=env).decode().strip()
git('update-ref', 'refs/heads/synthetic', commit)
git('symbolic-ref', 'HEAD', 'refs/heads/synthetic')
(controlled / '.gitleaksignore').write_bytes(ignore)
report = ROOT / 'sensitivity.private-redacted.json'
cmd = [str(ROOT / 'gitleaks'), 'git', '--no-banner', '--no-color', '--redact=100',
       '--report-format=json', '--report-path=' + str(report),
       '--log-opts=--no-merges --first-parent e99abf06062f8efd2697da4dc424658feaee08b0^..' + commit,
       '--gitleaks-ignore-path=' + str(controlled), str(controlled)]
run = subprocess.run(cmd, cwd=controlled, capture_output=True)
(ROOT / 'sensitivity.private.log').write_bytes(run.stdout + run.stderr)
findings = json.loads(report.read_text())
safe = [{k: f.get(k) for k in ['RuleID', 'File', 'StartLine', 'EndLine', 'Commit', 'Fingerprint']} for f in findings]
assert run.returncode == 1 and len(safe) == 1
assert safe[0]['Commit'] == commit and safe[0]['RuleID'] == 'generic-api-key'
assert safe[0]['File'] == PATH and safe[0]['StartLine'] == 12
assert safe[0]['Fingerprint'] != FP
assert manifest() == json.loads((ROOT / 'original-manifest.json').read_text())
assert subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=WT).decode().strip() == BASE
assert (WT / '.gitleaksignore').read_bytes() == ignore
result = {'command': cmd, 'exit_code': run.returncode, 'findings_count': len(safe), 'findings': safe,
          'fake_value_random_only': True, 'synthetic_parent': BASE, 'synthetic_commit': commit,
          'synthetic_repo': str(controlled), 'synthetic_objects_separate_from_main': True,
          'report_sha256': hashlib.sha256(report.read_bytes()).hexdigest(),
          'author_worktree_tracked_source_identity_preserved': True,
          'author_worktree_head_preserved': True, 'ignore_identity_preserved': True}
save('sensitivity.safe.json', result)
print(json.dumps(result))
