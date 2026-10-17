from pathlib import Path
from datetime import datetime, timezone
import hashlib
import json
import subprocess
import xml.etree.ElementTree as ET

OWN = Path('/workspace/scratch/rct-validation/export_ebsr_recheck')
AUTHOR = Path('/workspace/scratch/rct-validation/export_resume')
WT = Path('/workspace/rct-worktrees/exports')
FINAL = 'e258af8f7b836814ad47aed3ded46060594f8567'

def sha(data):
    return hashlib.sha256(data).hexdigest()

def save(name, data):
    (OWN / name).write_text(json.dumps(data, indent=2, ensure_ascii=False) + '\n')

phase = 'ebsr-crosslayer-after-3'
receipt_path = AUTHOR / (phase + '-result.json')
receipt = json.loads(receipt_path.read_text())
manifest_path = AUTHOR / receipt['sourceManifest']
manifest = json.loads(manifest_path.read_text())
assert sha(manifest_path.read_bytes()) == receipt['sourceManifestSHA256']
assert receipt['head'] == FINAL and receipt['exitCode'] == 0 and receipt['status'] == 'PASS'
assert receipt['sourceFilesUnchangedDuringRun'] is True
assert receipt['testFixtureControls'] == {}
assert receipt['taskExecution'][':services:auth-service:auth-app:integrationTest'] == '> Task :services:auth-service:auth-app:integrationTest'
assert '--rerun' in receipt['command']
assert len(manifest) == 1853
query = ''.join(FINAL + ':' + row['path'] + '\n' for row in manifest).encode()
result = subprocess.run(['git', 'cat-file', '--batch'], input=query, stdout=subprocess.PIPE, check=True, cwd=WT).stdout
offset = 0
records = []
for row in manifest:
    end = result.index(b'\n', offset)
    header = result[offset:end].split()
    assert header[1] == b'blob'
    size = int(header[2])
    data = result[end + 1:end + 1 + size]
    offset = end + 2 + size
    assert size == row['bytes'] and sha(data) == row['sha256']
    current_equal = (WT / row['path']).read_bytes() == data
    records.append({'path': row['path'], 'sha256': row['sha256'], 'bytes': size, 'matches_commit': True, 'current_bytes_unchanged': current_equal})
assert offset == len(result)
after2 = {row['path']: row['sha256'] for row in json.loads((AUTHOR / 'ebsr-crosslayer-after-2-source-manifest.json').read_text())}
changed = [row['path'] for row in manifest if after2.get(row['path']) != row['sha256']]
assert changed == ['services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/integration/ExportCrossLayerBoundaryIT.java']
save('after3-source-bridge.json', {'actual_producer': FINAL, 'inputs': records, 'manifest_sha256': receipt['sourceManifestSHA256'], 'inputs_changed_from_after2': changed, 'product_bytes_unchanged_from_after2': True})

xml = AUTHOR / (phase + '-xml/services_auth-service_auth-app_integrationTest/TEST-ru.rutcampustrack.auth.integration.ExportCrossLayerBoundaryIT.xml')
suite = ET.parse(xml).getroot()
assert sha(xml.read_bytes()) == receipt['suites'][':services:auth-service:auth-app:integrationTest'][0]['xmlSHA256']
counts = {key: int(suite.get(key, '0')) for key in ('tests', 'failures', 'errors', 'skipped')}
assert counts == {'tests': 18, 'failures': 0, 'errors': 0, 'skipped': 0}
cases = [{'name': c.get('name'), 'outcome': 'FAIL' if c.find('failure') is not None else 'ERROR' if c.find('error') is not None else 'SKIP' if c.find('skipped') is not None else 'PASS'} for c in suite.findall('testcase')]
assert len(cases) == 18 and all(c['outcome'] == 'PASS' for c in cases)
assert [c['name'] for c in cases] == [c['name'] for c in json.loads((OWN / 'after2-independent-xml.json').read_text())['cases']]
start = datetime.fromisoformat(receipt['at']).timestamp()
finish = datetime.fromisoformat(receipt['finished']).timestamp()
assert start <= xml.stat().st_mtime <= finish + 1
timestamp = suite.get('timestamp')
assert start <= datetime.fromisoformat(timestamp).replace(tzinfo=timezone.utc).timestamp() <= finish
save('after3-independent-xml.json', {'source': str(xml), 'sha256': sha(xml.read_bytes()), 'suite': suite.get('name'), 'counts': counts, 'cases': cases, 'xml_mtime_fresh': True, 'suite_timestamp_fresh': True})

log_path = AUTHOR / (phase + '.log')
log_bytes = log_path.read_bytes()
assert sha(log_bytes) == receipt['logSHA256']
lines = log_bytes.decode(errors='replace').splitlines()
task = '> Task :services:auth-service:auth-app:integrationTest'
assert task in lines and not any(line.startswith(task + ' UP-TO-DATE') or line.startswith(task + ' SKIPPED') for line in lines)
assert any(line.startswith('BUILD SUCCESSFUL') for line in lines)
log_metadata = {'source': str(log_path), 'sha256': sha(log_bytes), 'task_executed': True, 'build_successful': True, 'raw_content_not_published': True}
save('after3-independent-terminal.json', {'actual_producer': FINAL, 'receipt_sha256': sha(receipt_path.read_bytes()), 'exit_code': 0, 'counts': counts, 'source_inputs': 1853, 'fixture_controls': {}, 'task_execution': 'EXECUTED', 'log': log_metadata})

ledger = json.loads((Path('/workspace/rutcampustrack') / 'docs/validation/cloud-20261011/receipts/export-backend-security-review/source-ledger.json').read_text())
source_records = []
for row in ledger:
    path = Path(row['path'])
    actual = sha(path.read_bytes())
    source_records.append({'path': str(path), 'original_review_sha256': row['sha256'], 'current_sha256': actual, 'unchanged_from_original_review': actual == row['sha256'], 'source_metadata': row.get('source_entries', [])})
save('acceptance-source-identity.json', source_records)
print(json.dumps({'revision': FINAL, 'actual_terminal': 'PASS', 'counts': counts, 'inputs_exact_to_commit': len(records), 'current_changed_inputs': [r['path'] for r in records if not r['current_bytes_unchanged']], 'xml_fresh': True, 'immutable_original_log_hash_match': True, 'only_fixture_input_changed_since_after2': changed}, indent=2))
