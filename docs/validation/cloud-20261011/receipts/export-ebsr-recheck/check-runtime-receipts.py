from pathlib import Path
import hashlib
import json
import subprocess
import xml.etree.ElementTree as ET
from datetime import datetime, timezone

OWN = Path('/workspace/scratch/rct-validation/export_ebsr_recheck')
AUTHOR = Path('/workspace/scratch/rct-validation/export_resume')
WT = Path('/workspace/rct-worktrees/exports')
FINAL = 'f09b70e383b1c29b31960b5da99647f8ae6530bf'

def sha(data):
    return hashlib.sha256(data).hexdigest()

def save(name, data):
    (OWN / name).write_text(json.dumps(data, indent=2, ensure_ascii=False) + '\n')

phases = ['ebsr-affected-unit-1', 'ebsr-affected-unit-2', 'ebsr-auth-compatibility-1']
records = []
for phase in phases:
    receipt_path = AUTHOR / (phase + '-result.json')
    if not receipt_path.exists():
        records.append({'phase': phase, 'state': 'PENDING_TERMINAL'})
        continue
    receipt = json.loads(receipt_path.read_text())
    manifest_path = AUTHOR / receipt['sourceManifest']
    manifest = json.loads(manifest_path.read_text())
    assert sha(manifest_path.read_bytes()) == receipt['sourceManifestSHA256']
    assert receipt['sourceFilesUnchangedDuringRun'] is True and receipt['testFixtureControls'] == {}
    revision = receipt['head']
    query = ''.join(revision + ':' + row['path'] + '\n' for row in manifest).encode()
    result = subprocess.run(['git', 'cat-file', '--batch'], input=query, stdout=subprocess.PIPE, check=True, cwd=WT).stdout
    offset = 0
    source_records = []
    for row in manifest:
        end = result.index(b'\n', offset)
        header = result[offset:end].split()
        assert header[1] == b'blob'
        size = int(header[2])
        data = result[end + 1:end + 1 + size]
        offset = end + 2 + size
        assert size == row['bytes'] and sha(data) == row['sha256']
        source_records.append({'path': row['path'], 'sha256': row['sha256'], 'matches_producer_commit': True})
    assert offset == len(result) and len(manifest) == 1853
    suites = []
    for task, suite_receipts in receipt['suites'].items():
        execution = receipt['taskExecution'][task]
        for suite_receipt in suite_receipts:
            xml = AUTHOR / (phase + '-xml') / task[1:].replace(':', '_') / ('TEST-' + suite_receipt['suite'] + '.xml')
            suite = ET.parse(xml).getroot()
            assert sha(xml.read_bytes()) == suite_receipt['xmlSHA256']
            counts = {key: int(suite.get(key, '0')) for key in ('tests', 'failures', 'errors', 'skipped')}
            assert all(counts[key] == suite_receipt[key] for key in counts)
            start = datetime.fromisoformat(receipt['at']).timestamp()
            finish = datetime.fromisoformat(receipt['finished']).timestamp()
            assert start <= xml.stat().st_mtime <= finish + 1
            cases = [{'name': c.get('name'), 'outcome': 'FAIL' if c.find('failure') is not None else 'ERROR' if c.find('error') is not None else 'SKIP' if c.find('skipped') is not None else 'PASS'} for c in suite.findall('testcase')]
            assert len(cases) == counts['tests']
            if phase != 'ebsr-affected-unit-1':
                assert counts['failures'] == counts['errors'] == counts['skipped'] == 0
                assert execution == '> Task ' + task
            suites.append({'task': task, 'suite': suite.get('name'), 'sha256': sha(xml.read_bytes()), 'counts': counts, 'cases': cases, 'fresh_xml': True})
    log = AUTHOR / (phase + '.log')
    assert sha(log.read_bytes()) == receipt['logSHA256']
    if phase != 'ebsr-affected-unit-1':
        assert receipt['exitCode'] == 0 and receipt['status'] == 'PASS' and revision == FINAL
    records.append({'phase': phase, 'state': receipt['status'], 'producer': revision, 'receipt_sha256': sha(receipt_path.read_bytes()), 'exit_code': receipt['exitCode'], 'source_manifest_sha256': receipt['sourceManifestSHA256'], 'source_count': len(source_records), 'source_matches_committed_revision': True, 'log_sha256': receipt['logSHA256'], 'task_execution': receipt['taskExecution'], 'suites': suites})
    save(phase + '-source-bridge.json', {'producer': revision, 'files': source_records})

final_paths = subprocess.check_output(['git', 'diff', '--name-only', '0af9cae59dae04b9409a331dcf8a4ddbe89aa206', FINAL], cwd=WT, text=True).splitlines()
assert len(final_paths) == 8
inventory = []
for path in final_paths:
    data = subprocess.check_output(['git', 'show', FINAL + ':' + path], cwd=WT)
    assert (WT / path).read_bytes() == data
    inventory.append({'path': path, 'bytes': len(data), 'sha256': sha(data), 'category': 'test' if '/src/test/' in path else 'product', 'current_equals_commit': True})
delta = subprocess.check_output(['git', 'diff', '--name-only', 'e258af8f7b836814ad47aed3ded46060594f8567', FINAL], cwd=WT, text=True).splitlines()
assert delta == ['services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/service/ReportDownloadTicketServiceTest.java']
save('final-code-inventory.json', {'revision': FINAL, 'product_revision': 'b45da01a335c4ae44c40c268ac224b2a3d2dba39', 'crosslayer_actual_producer': 'e258af8f7b836814ad47aed3ded46060594f8567', 'files': inventory, 'delta_since_crosslayer_actual': 'Two-line signer stub in separate fixed-clock unit class only; product, crosslayer test, harness, and dependencies unchanged'})
save('remaining-actual-evidence.json', records)
print(json.dumps([{'phase': r['phase'], 'state': r['state'], 'suite_counts': [{'suite': s['suite'], **s['counts']} for s in r.get('suites', [])]} for r in records], indent=2))
