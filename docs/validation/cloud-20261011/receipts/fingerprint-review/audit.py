"""Read-only fingerprint/source/evidence review. Never opens private scanner reports/logs."""
from __future__ import annotations

import ast
from datetime import datetime, timezone
import hashlib
import io
import json
import os
from pathlib import Path
import re
import subprocess
import tarfile

OUT = Path('/workspace/scratch/rct-validation/fingerprint_review')
AUTHOR = Path('/workspace/scratch/rct-validation/security_fingerprint')
WT = Path('/workspace/rct-worktrees/security-fingerprint')
ROOT = Path('/workspace/rutcampustrack')
DOCS = ROOT / 'docs/validation/cloud-20261011'
NTR = Path('/workspace/scratch/rct-validation/ntr_seal_review')
BASE = 'eec3e0a864626160d33ae29da2cf3b0640e30caa'
NEW = '2a1c1f82b3b2a06daf1f666478d796e0a55e3775'
HIST = '63647fc9b63175e1e32b0773d57affc637ea1065'
SOURCE_PATH = 'docs/validation/cloud-20261011/packet-export-backend-security-review.md'
FINGERPRINT = HIST + ':' + SOURCE_PATH + ':generic-api-key:12'
RANGE = 'e99abf06062f8efd2697da4dc424658feaee08b0^..' + HIST
EXPECTED_IGNORE = ('# Historical architecture prose false positive verified with Gitleaks 8.24.3.\n'
                   + FINGERPRINT + '\n').encode()


def sha(data):
    return hashlib.sha256(data).hexdigest()


def write(name, value):
    target = OUT / name
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n')


def git(*args, cwd=WT):
    return subprocess.check_output(['git', '-C', str(cwd), *args])


def tree_entries(revision):
    entries = {}
    for row in git('ls-tree', '-r', '-z', revision).split(b'\0'):
        if not row:
            continue
        metadata, path = row.split(b'\t', 1)
        mode, object_type, oid = metadata.decode().split(' ')
        assert object_type == 'blob'
        entries[path.decode()] = {'git_mode': mode, 'blob': oid}
    return entries


def digest_file(path):
    size = path.stat().st_size
    sha256 = hashlib.sha256()
    blob = hashlib.sha1(('blob ' + str(size) + '\0').encode())
    with path.open('rb') as source:
        while chunk := source.read(1024 * 1024):
            sha256.update(chunk)
            blob.update(chunk)
    return sha256.hexdigest(), blob.hexdigest(), size


started = datetime.now(timezone.utc).isoformat()
inputs = []


def remember(path, role):
    assert '.private' not in path.name, 'Private scanner output must never be read'
    data = path.read_bytes()
    inputs.append({'path': str(path), 'role': role, 'bytes': len(data), 'sha256': sha(data)})
    return data


# Original safe author outputs are copied byte-for-byte by their explicit publishable manifest.
manifest_bytes = remember(AUTHOR / 'sealed-safe-manifest.json', 'safe-author-manifest')
manifest = json.loads(manifest_bytes)
assert len(manifest) == 12
(OUT / 'author-originals').mkdir(exist_ok=True)
for name, entry in manifest.items():
    assert '/' not in name and '.private' not in name
    data = remember(AUTHOR / name, 'original-safe-author-receipt')
    assert len(data) == entry['bytes'] and sha(data) == entry['sha256']
    (OUT / 'author-originals' / name).write_bytes(data)
(OUT / 'author-originals/sealed-safe-manifest.json').write_bytes(manifest_bytes)
for name in ['TASK-CONTRACT.md', 'CURRENT.md', 'decisions.md', 'questions.md', 'resources.md',
             'packet-fingerprint-review.md']:
    data = remember(DOCS / name, 'governing-input')
    (OUT / 'governing').mkdir(exist_ok=True)
    (OUT / 'governing' / name).write_bytes(data)
assert sha((OUT / 'governing/TASK-CONTRACT.md').read_bytes()) == '8cbd27d431815647886b2f39592905d3f1d4cb985643e5d9fa3f3f669b4df4a5'
for name in ['AGENTS.md', '.agent/orchestration-v2/RULES.md', 'docs/agent-workflow.md',
             'tests/AGENTS.md', 'docs/validation/cloud-20261011/skills/project/rct-verification/SKILL.md',
             'docs/validation/cloud-20261011/skills/project/rct-source-resolution/SKILL.md']:
    remember(ROOT / name, 'process-input')
native_meta_bytes = remember(DOCS / 'receipts/checkpoint636-native-gitleaks-metadata.json', 'native-job-safe-metadata')
(OUT / 'native-job-metadata.original.json').write_bytes(native_meta_bytes)
native_meta = json.loads(native_meta_bytes)
assert native_meta['scanner_version'] == '8.24.3' and native_meta['commits_scanned'] == 26
assert native_meta['head_sha'] == HIST
assert any(row['field'] == 'Fingerprint' and row['value'] == FINGERPRINT for row in native_meta['selected_metadata'])

# Preserve the previous completed NTR package without changing any of its bytes.
ntr_manifest = json.loads(remember(NTR / 'transfer-manifest.json', 'previous-completed-NTR-preserved'))
ntr_rows = []
for entry in ntr_manifest['entries']:
    path = NTR / entry['path']
    data = path.read_bytes()
    assert len(data) == entry['bytes'] and sha(data) == entry['sha256']
    ntr_rows.append({'path': str(path), 'bytes': len(data), 'sha256': sha(data)})

assert git('rev-parse', 'HEAD').decode().strip() == NEW
assert git('rev-parse', 'HEAD^').decode().strip() == BASE
assert not git('status', '--porcelain')
assert git('diff', '--name-status', BASE, NEW).decode().strip() == 'A\t.gitleaksignore'
assert git('diff', '--numstat', BASE, NEW).decode().strip() == '2\t0\t.gitleaksignore'
subprocess.run(['git', '-C', str(WT), 'diff', '--check', BASE, NEW], check=True)
ignore = remember(WT / '.gitleaksignore', 'reviewed-current-source')
assert ignore == EXPECTED_IGNORE == git('show', NEW + ':.gitleaksignore')
assert sha(ignore) == 'b151fa8f6efc1340a37d1dc616aae0c803e5d2404ed063381f7128a5e03a1ba8'
ignore_filesystem_mode = oct((WT / '.gitleaksignore').stat().st_mode & 0o777)
assert (WT / '.gitleaksignore').stat().st_mode & 0o400
(OUT / 'reviewed.gitleaksignore').write_bytes(ignore)
(OUT / 'reviewed-change.patch').write_bytes(git('diff', BASE, NEW))
assert len(ignore.splitlines()) == 2 and len(ignore) < 1024 * 1024
assert ignore.endswith(b'\n') and not ignore.endswith(b'\n\n')
assert all(line == line.rstrip() for line in ignore.splitlines())

original = json.loads((OUT / 'author-originals/original-manifest.json').read_text())
before_tree = tree_entries(BASE)
after_tree = tree_entries(NEW)
assert len(original) == len(before_tree) == 6985
assert set(original) == set(before_tree)
assert set(after_tree) - set(before_tree) == {'.gitleaksignore'}
assert all(after_tree[path] == entry for path, entry in before_tree.items())
assert after_tree['.gitleaksignore']['git_mode'] == '100644'
source_rows = []
for path, expected in original.items():
    actual_path = WT / path
    actual_sha256, actual_blob, size = digest_file(actual_path)
    actual_mode = oct(actual_path.stat().st_mode & 0o777)
    assert actual_sha256 == expected['sha256'] and actual_mode == expected['mode'], path
    if before_tree[path]['git_mode'] == '120000':
        link_data = os.readlink(actual_path).encode()
        actual_blob = hashlib.sha1(('blob ' + str(len(link_data)) + '\0').encode() + link_data).hexdigest()
    assert actual_blob == before_tree[path]['blob'] == after_tree[path]['blob'], path
    source_rows.append({'path': path, 'sha256': actual_sha256, 'mode': actual_mode,
                        'git_mode': before_tree[path]['git_mode'], 'git_blob': actual_blob,
                        'prelaunch_base_author_commit_current_equal': True, 'bytes': size})

# Open only the known historical prose source, never a scanner Finding or Secret field.
historical = git('show', HIST + ':' + SOURCE_PATH)
line = historical.decode().splitlines()[11]
source_record = json.loads((OUT / 'author-originals/historical-source.safe.json').read_text())
assert sha(historical) == source_record['blob_sha256']
assert sha(line.encode()) == source_record['line_sha256']
assert sha(line.encode()) == '8d45ff494f4991e99c94509811935a760c82693d23a3eeb1773b41a6cf99e719'
assert not re.search(r'(api[_-]?key|password|secret|token)\s*[:=]', line, re.IGNORECASE)
assert len(git('rev-list', '--no-merges', '--first-parent', RANGE).splitlines()) == 26

provenance = json.loads((OUT / 'author-originals/provenance.json').read_text())
archive_path = AUTHOR / 'gitleaks_8.24.3_linux_x64.tar.gz'
binary_path = AUTHOR / 'gitleaks'
archive = archive_path.read_bytes()
binary = binary_path.read_bytes()
assert len(archive) == provenance['archive_bytes'] == 5796002
assert sha(archive) == provenance['archive_sha256'] == '9991e0b2903da4c8f6122b5c3186448b927a5da4deef1fe45271c3793f4ee29c'
checksums = (OUT / 'author-originals/gitleaks_8.24.3_checksums.txt').read_text().splitlines()
assert sha(archive) + '  ' + archive_path.name in checksums
with tarfile.open(fileobj=io.BytesIO(archive), mode='r:gz') as tar:
    official_binary = tar.extractfile('gitleaks').read()
assert official_binary == binary
assert sha(binary) == provenance['binary_sha256'] == 'e18325d568268b6efb6cd0cc721f8bea5667f4cd1c8d03618ab61987f41269ad'
version = subprocess.check_output([str(binary_path), 'version']).decode().strip()
assert version == '8.24.3'
assert provenance['archive_origin'] == 'https://github.com/gitleaks/gitleaks/releases/download/v8.24.3/' + archive_path.name
assert provenance['checksum_origin'] == 'https://github.com/gitleaks/gitleaks/releases/download/v8.24.3/gitleaks_8.24.3_checksums.txt'

red = json.loads((OUT / 'author-originals/baseline-historical.safe.json').read_text())
green = json.loads((OUT / 'author-originals/corrected-historical.safe.json').read_text())
sensitivity = json.loads((OUT / 'author-originals/sensitivity.safe.json').read_text())
proof = json.loads((OUT / 'author-originals/historical-proof.safe.json').read_text())
commit_checks = json.loads((OUT / 'author-originals/commit-checks.safe.json').read_text())
assert proof['commits'] == 26 and proof['scanner_version'] == version and proof['ignore_sha256'] == sha(ignore)
assert proof['baseline'] == BASE and proof['history'] == RANGE
assert red['exit_code'] == 1 and red['findings_count'] == 1
assert red['findings'] == [{'RuleID': 'generic-api-key', 'File': SOURCE_PATH, 'StartLine': 12,
                            'EndLine': 12, 'Commit': HIST, 'Fingerprint': FINGERPRINT}]
assert green['exit_code'] == 0 and green['findings_count'] == 0 and green['findings'] == []
red_cmd, green_cmd = red['command'], green['command']
assert red_cmd[0] == green_cmd[0] == str(binary_path)
for cmd in [red_cmd, green_cmd, sensitivity['command']]:
    assert cmd[:5] == [str(binary_path), 'git', '--no-banner', '--no-color', '--redact=100']
    assert not any(flag in cmd for flag in ['--config', '--baseline-path', '--ignore-gitleaks-allow'])
    assert '--report-format=json' in cmd
assert red_cmd[-1] == green_cmd[-1] == str(WT)
assert '--log-opts=--no-merges --first-parent ' + RANGE in red_cmd
assert '--log-opts=--no-merges --first-parent ' + RANGE in green_cmd
assert '--gitleaks-ignore-path=' + str(WT) in green_cmd
assert '--gitleaks-ignore-path=' + str(AUTHOR / 'empty-ignore') in red_cmd
assert not (AUTHOR / 'empty-ignore/.gitleaksignore').exists()

controlled = Path(sensitivity['synthetic_repo'])
synthetic_commit = sensitivity['synthetic_commit']
assert synthetic_commit == '1901133a3df2da743e32c6b54d965b51a992a5f5'
assert sensitivity['synthetic_parent'] == BASE and sensitivity['fake_value_random_only']
assert sensitivity['exit_code'] == 1 and sensitivity['findings_count'] == 1
assert sensitivity['findings'] == [{'RuleID': 'generic-api-key', 'File': SOURCE_PATH, 'StartLine': 12,
                                    'EndLine': 12, 'Commit': synthetic_commit,
                                    'Fingerprint': synthetic_commit + ':' + SOURCE_PATH + ':generic-api-key:12'}]
assert sensitivity['findings'][0]['Fingerprint'] != FINGERPRINT
assert git('rev-parse', synthetic_commit + '^', cwd=controlled).decode().strip() == BASE
assert git('show', synthetic_commit + ':.gitleaksignore', cwd=controlled) == ignore == (controlled / '.gitleaksignore').read_bytes()
assert git('diff', '--name-only', BASE, synthetic_commit, cwd=controlled).decode().splitlines() == ['.gitleaksignore', SOURCE_PATH]
synthetic_source = git('show', synthetic_commit + ':' + SOURCE_PATH, cwd=controlled)
base_source = git('show', BASE + ':' + SOURCE_PATH, cwd=controlled)
synthetic_lines = synthetic_source.splitlines(keepends=True)
base_lines = base_source.splitlines(keepends=True)
assert len(synthetic_lines) == len(base_lines)
assert [i + 1 for i in range(len(base_lines)) if base_lines[i] != synthetic_lines[i]] == [12]
assert re.fullmatch(rb'api_key="[0-9a-f]{48}"\n', synthetic_lines[11])
assert git('rev-parse', '--git-common-dir', cwd=controlled).decode().strip() == '.git'
common = Path(git('rev-parse', '--path-format=absolute', '--git-common-dir').decode().strip())
assert (controlled / '.git/objects/info/alternates').read_text().strip() == str(common / 'objects')
assert (controlled / '.git/objects' / synthetic_commit[:2] / synthetic_commit[2:]).exists()
not_in_main = subprocess.run(['git', '-C', str(WT), 'cat-file', '-e', synthetic_commit],
                             stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL).returncode
assert not_in_main != 0

# The safe hook receipt records actual equivalent guards, not an installed/pinned hook run.
assert commit_checks['installed_hook'].startswith('ABSENT')
assert all(commit_checks[name] is True for name in ['large_file_under_1024KiB', 'end_of_file', 'no_trailing_whitespace', 'diff_check'])
assert commit_checks['scanner_version'] == '8.24.3'
assert commit_checks['scanner_exit_code'] == commit_checks['findings_count'] == 0
assert commit_checks['staged_paths'] == ['.gitleaksignore']
assert '--pre-commit' in commit_checks['scanner_command'] and '--staged' in commit_checks['scanner_command']
for name in ['.github/workflows/security.yml', '.pre-commit-config.yaml']:
    assert (WT / name).read_bytes() == git('show', BASE + ':' + name) == git('show', NEW + ':' + name)
assert b'rev: v8.22.1' in (WT / '.pre-commit-config.yaml').read_bytes()
for name in ['verify.py', 'sensitivity.py']:
    ast.parse((OUT / 'author-originals' / name).read_text())

write('source-equivalence.json', {'reviewed_commit': NEW, 'parent': BASE, 'current_clean': True,
    'scope': {'Created': ['.gitleaksignore'], 'Modified': [], 'Deleted': []},
    'ignore_sha256': sha(ignore), 'ignore_git_mode': '100644', 'ignore_filesystem_mode': ignore_filesystem_mode,
    'exact_comment_plus_one_fingerprint': True, 'source_inputs_verified': len(source_rows),
    'all_original_sha256_filesystem_modes_and_git_blobs_equal': True, 'inputs': source_rows,
    'source_execution_relation': 'Author red/green/sensitivity ran BASE worktree with untracked exact ignore; final2a1c adds only that exact file. No execution-at-final2a1c claim.'})
write('scanner-and-false-positive-audit.json', {'provenance': provenance,
    'archive_hash_matches_published_checksum_file': True, 'archive_binary_equals_used_binary': True,
    'independently_observed_binary_version': version,
    'official_download_provenance': 'Author-recorded official release URLs; independent local byte/checksum/version verification, no reviewer network redownload or signature claim.',
    'historical_source': source_record, 'historical_line_independently_opened': True,
    'false_positive_classification': 'Known task-packet line12 architecture prose; no credential assignment; no real secret was read or repeated.',
    'native_security_job': native_meta, 'history_commit_count_independently_verified': 26,
    'red': red, 'green': green, 'private_raw_reports_or_logs_opened': False,
    'recorded_report_hashes': 'Author original safe projections retain hashes; private report/log bytes deliberately not opened by reviewer.',
    'reviewer_scanner_history_replay': 'NOT_RUN; sufficient original bounded native records retained'})
write('sensitivity-audit.json', {'original_safe_record': sensitivity,
    'independent_source_and_object_store_checks': {'same_path_rule_line12_new_fingerprint': True,
        'exact_original_ignore_retained': True, 'synthetic_parent_equals_BASE': True,
        'synthetic_source_diff_only_line12': True, 'controlled_value_shape_random48hex_assignment': True,
        'synthetic_source_sha256': sha(synthetic_source), 'controlled_value_not_published': True,
        'separate_controlled_object_store': True, 'synthetic_commit_absent_from_product_object_store': True},
    'meaningful_control': 'Same official8.24.3 detected exactly one NEW commit:path:generic-api-key:12 fingerprint while original636 was ignored.',
    'limitations': 'One relevant new-commit same-location secret regression control; not exhaustive all-secret-rule certification. Reviewer did not replay the mutation or scanner.'})
write('hook-and-gate-qualification.json', {'author_equivalent_guard_receipt': commit_checks,
    'independent_added_file_size_EOF_whitespace_diffcheck': 'PASS',
    'pinned_local_hook_configuration': 'Unchanged Gitleaks v8.22.1; actual installed hook absent at author commit. No pinned-hook-PASS claim.',
    'source_hook_version_vs_proof': 'Author staged guard used official8.24.3, matching native CI scanner; root retains mandatory integration-hook ownership.',
    'CI_workflow_rules_paths_entropy_triggers_and_history': 'UNCHANGED',
    'existing_remote_security': 'FAIL38184430147 remains until actual new current gate receipts',
    'whole_project_security_or_DONE': False})
write('input-integrity.json', {'safe_author_files_verified': len(manifest), 'safe_author_manifest_sha256': sha(manifest_bytes),
    'inputs': inputs, 'private_reports_logs_finding_secret_fields': 'NOT_OPENED_OR_COPIED',
    'previous_NTR_package_preserved': ntr_rows, 'previous_NTR_transfer_manifest_sha256': sha((NTR / 'transfer-manifest.json').read_bytes()),
    'binary_and_archive_hashes': {'gitleaks': sha(binary), 'archive': sha(archive)}})

for row in inputs:
    if row['role'] == 'governing-input':
        continue  # Root owns live status; consumed snapshots remain preserved.
    data = Path(row['path']).read_bytes()
    assert len(data) == row['bytes'] and sha(data) == row['sha256']
for row in ntr_rows:
    data = Path(row['path']).read_bytes()
    assert len(data) == row['bytes'] and sha(data) == row['sha256']
assert not git('status', '--porcelain')
assert git('rev-parse', 'HEAD').decode().strip() == NEW
write('verification.json', {'started_utc': started, 'completed_utc': datetime.now(timezone.utc).isoformat(),
    'command': 'python3 /workspace/scratch/rct-validation/fingerprint_review/audit.py', 'native_exit': 0,
    'mode': 'INDEPENDENT_LIGHT_READ_ONLY_HASH_GIT_TAR_SAFE_RECORD_PARSER',
    'source_inputs': 6985, 'safe_author_originals': 12, 'prior_NTR_unchanged': True,
    'binary_version_observed': version, 'scanner_replay': 'NOT_RUN',
    'product_config_source_edits': 0, 'heavy_operations': 0, 'children': 0})
print(json.dumps({'audit': 'PASS', 'author_commit': NEW, 'original_input_byte_mode_git_identity': 6985,
    'safe_author_originals': 12, 'official_binary_version': version,
    'historical_red_green': 'native1_one_exact_historical_finding/native0_zero_findings',
    'new_same_path_rule_line12': 'native1_one_distinct_new_fingerprint', 'prior_NTR_preserved': True}))
