from pathlib import Path
import hashlib
import json
import re
import subprocess
import xml.etree.ElementTree as ET
from datetime import datetime, timezone

OWN = Path('/workspace/scratch/rct-validation/export_ebsr_recheck')
ROOT = Path('/workspace/rutcampustrack')
WT = Path('/workspace/rct-worktrees/exports')
AUTHOR = Path('/workspace/scratch/rct-validation/export_resume')
ORIGINAL = ROOT / 'docs/validation/cloud-20261011/receipts/export-backend-security-review'
B45 = 'b45da01a335c4ae44c40c268ac224b2a3d2dba39'
FINAL = 'e258af8f7b836814ad47aed3ded46060594f8567'
BEFORE = '0af9cae59dae04b9409a331dcf8a4ddbe89aa206'
CF6 = 'cf6d1a238441cc1b122e205e160aa23a741a3942'

def sha(data):
    return hashlib.sha256(data).hexdigest()

def read_json(path):
    return json.loads(path.read_text())

def blob(revision, path):
    return subprocess.check_output(['git', 'show', revision + ':' + path], cwd=WT)

def save(name, data):
    (OWN / name).write_text(json.dumps(data, indent=2, ensure_ascii=False) + '\n')

def xml_summary(path):
    tree = ET.parse(path).getroot()
    cases = []
    for case in tree.findall('testcase'):
        failure = case.find('failure')
        error = case.find('error')
        item = {'name': case.get('name'), 'outcome': 'FAIL' if failure is not None else 'ERROR' if error is not None else 'SKIP' if case.find('skipped') is not None else 'PASS'}
        if failure is not None:
            item['failure_type'] = failure.get('type')
            stack = failure.text or ''
            match = re.search(r'ExportCrossLayerBoundaryIT\.([\w$]+)\(ExportCrossLayerBoundaryIT.java:(\d+)\)', stack)
            if match:
                item['method'], item['test_line'] = match[1], int(match[2])
            allowed = ['ready', 'error', '403 FORBIDDEN', '200 OK', '503 SERVICE_UNAVAILABLE', '404 NOT_FOUND', '500 INTERNAL_SERVER_ERROR', '400 BAD_REQUEST', '202 ACCEPTED']
            item['safe_observed_labels'] = [v for v in allowed if v in stack]
            item['fixture_constraint_admin_access_state'] = 'admin_access_state' in stack
        cases.append(item)
    return {'path': str(path), 'sha256': sha(path.read_bytes()), 'suite': tree.get('name'), 'counts': {key: int(tree.get(key, '0')) for key in ('tests', 'failures', 'errors', 'skipped')}, 'cases': cases}

OWN.mkdir(parents=True, exist_ok=True)
governing = ['AGENTS.md', 'services/AGENTS.md', 'tests/AGENTS.md', '.agent/orchestration-v2/RULES.md', '.agent/orchestration-v2/CURRENT.md', 'docs/agent-workflow.md', 'docs/validation/cloud-20261011/TASK-CONTRACT.md', 'docs/validation/cloud-20261011/CURRENT.md', 'docs/validation/cloud-20261011/decisions.md', 'docs/validation/cloud-20261011/questions.md', 'docs/validation/cloud-20261011/resources.md', 'docs/validation/cloud-20261011/packet-export-ebsr-recheck.md', 'docs/validation/cloud-20261011/skills/project/rct-source-resolution/SKILL.md', 'docs/validation/cloud-20261011/skills/project/rct-verification/SKILL.md', 'docs/product/decisions/2026-10-11-admin-registry-export-columns-and-grants.md']
governing_records = [{'path': str(ROOT / p), 'sha256': sha((ROOT / p).read_bytes()), 'bytes': (ROOT / p).stat().st_size} for p in governing]
assert next(r['sha256'] for r in governing_records if r['path'].endswith('/TASK-CONTRACT.md')) == '8cbd27d431815647886b2f39592905d3f1d4cb985643e5d9fa3f3f669b4df4a5'
save('governing-inputs.json', governing_records)

original_records = []
for row in read_json(ORIGINAL / 'original-byte-index.json')['files']:
    path = ORIGINAL / row['path']
    if path.exists():
        actual = sha(path.read_bytes())
        assert actual == row['original_sha256']
        original_records.append({'path': str(path), 'sha256': actual, 'bytes': path.stat().st_size, 'matches_immutable_original': True})
save('original-review-hashes.json', original_records)

inventory = read_json(AUTHOR / 'source-ready-commit.json')['files']
sources = []
for row in inventory:
    path = row['path']
    committed_b45 = blob(B45, path)
    committed_final = blob(FINAL, path)
    current = (WT / path).read_bytes()
    assert sha(committed_b45) == row['sha256']
    assert current == committed_final
    sources.append({'path': path, 'status': row['status'], 'b45_sha256': sha(committed_b45), 'final_sha256': sha(committed_final), 'current_matches_final': True, 'changed_after_b45': committed_b45 != committed_final})
assert sum(s['changed_after_b45'] for s in sources) == 1
assert sources[-1]['changed_after_b45']
save('owned-code-inventory.json', {'b45': B45, 'final': FINAL, 'files': sources})

cf6_files = read_json(ORIGINAL / 'frozen-source-manifest.json')['files']
cf6_records = []
for row in cf6_files:
    path = row['path']
    frozen = blob(CF6, path)
    assert sha(frozen) == row['sha256']
    assert blob(BEFORE, path) == frozen
    final = blob(FINAL, path)
    cf6_records.append({'path': path, 'original_sha256': row['sha256'], 'final_sha256': sha(final), 'unchanged_from_original': frozen == final})
assert len(cf6_records) == 62
assert sum(not r['unchanged_from_original'] for r in cf6_records) == 6
dependencies = []
for row in read_json(ORIGINAL / 'dependency-manifest.json'):
    path = row['path']
    current = (WT / path).read_bytes()
    assert sha(current) == row['sha256']
    dependencies.append({'path': path, 'sha256': row['sha256'], 'unchanged_from_original': True})
save('reused-source-scope.json', {'cf6_files': cf6_records, 'unchanged_cf6_count': 56, 'dependency_files': dependencies})

before_manifest_path = Path('/workspace/scratch/rct-validation/export_developer/ebsr-runtime-before-3-source-manifest.json')
before_manifest = read_json(before_manifest_path)
copied_test = 'services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/integration/ExportCrossLayerBoundaryIT.java'
before_comparison = []
for row in before_manifest:
    path = row['path']
    saved = AUTHOR / 'before3-source' / path
    data = saved.read_bytes() if saved.exists() else blob(BEFORE, path)
    assert sha(data) == row['sha256']
    before_comparison.append({'path': path, 'sha256': row['sha256'], 'source': 'saved_original_before3' if saved.exists() else 'before_revision_blob', 'matches_original_run': True})
assert len(before_comparison) == 480
save('known-bad-source-bridge.json', {'revision': BEFORE, 'original_manifest_sha256': sha(before_manifest_path.read_bytes()), 'files': before_comparison})

compile_manifest_path = AUTHOR / 'ebsr-compile-after-2-source-manifest.json'
compile_manifest = read_json(compile_manifest_path)
for row in compile_manifest:
    assert sha(blob(B45, row['path'])) == row['sha256']
assert len(compile_manifest) == 1853
save('compile-source-bridge.json', {'compile_producer_head': BEFORE, 'tested_wip_committed_as': B45, 'inputs': len(compile_manifest), 'matches_b45': True, 'manifest_sha256': sha(compile_manifest_path.read_bytes()), 'final_fixture_test_requires_its_own_actual_run': True})

before_xml = Path('/workspace/scratch/rct-validation/export_developer/ebsr-runtime-before-3-xml/services_auth-service_auth-app_integrationTest/TEST-ru.rutcampustrack.auth.integration.ExportCrossLayerBoundaryIT.xml')
before = xml_summary(before_xml)
assert before['counts'] == {'tests': 18, 'failures': 12, 'errors': 0, 'skipped': 0}
fixture = [r for r in before['cases'] if r.get('fixture_constraint_admin_access_state')]
assert len(fixture) == 1
assert fixture[0]['method'] == 'limitedWithoutViewGrantsCannotIssueEitherRegistryCapability'
before['meaningful_rule_failures'] = 11
before['excluded_fixture_failures'] = 1
save('before3-independent-xml.json', before)
after2 = xml_summary(AUTHOR / 'ebsr-crosslayer-after-2-xml/services_auth-service_auth-app_integrationTest/TEST-ru.rutcampustrack.auth.integration.ExportCrossLayerBoundaryIT.xml')
assert after2['counts'] == {'tests': 18, 'failures': 1, 'errors': 0, 'skipped': 0}
save('after2-independent-xml.json', after2)

save('CURRENT.json', {'at': datetime.now(timezone.utc).isoformat(), 'status': 'SOURCE_RECHECK_IN_PROGRESS_AWAITING_FINAL_RUNTIME', 'b45': B45, 'final': FINAL, 'product_sources_unchanged_between_revisions': True, 'heavy': 'NOT_RUN_BY_REVIEWER', 'before3': {'meaningful_failures': 11, 'fixture_failures': 1}, 'after2': after2['counts'], 'next': 'Read fresh final after3 XML, terminal receipt, and 1853-input source bridge; finish affected review and seal.'})
print(json.dumps({'original_review_files_verified': len(original_records), 'owned_paths': len(sources), 'cf6_sources_verified': len(cf6_records), 'unchanged_cf6_sources': 56, 'original_dependencies_verified': len(dependencies), 'before3_source_inputs_verified': len(before_comparison), 'compile_source_inputs_verified': len(compile_manifest), 'before3_counts': before['counts'], 'after2_counts': after2['counts'], 'scratch': str(OWN)}, indent=2))
