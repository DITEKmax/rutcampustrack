"""Read-only verification of frozen sources and existing synthetic run evidence."""
from pathlib import Path
import hashlib
import json
import re
import shutil
import subprocess
import xml.etree.ElementTree as ET

OUT = Path('/workspace/scratch/rct-validation/map_validation_review')
AUTHOR = Path('/workspace/scratch/rct-validation/file_boundaries_developer')
WT = Path('/workspace/rct-worktrees/file-boundaries')
SHA = 'e68989d29e65008dbbeba7b8df1b80bcb070a538'
PARENT = 'e093eb988785ed48f26c4f3a561f168ed4d96401'

def digest(data):
    return hashlib.sha256(data).hexdigest()

def git(*args):
    return subprocess.check_output(['git', '-C', str(WT), *args])

def save(name, data):
    (OUT / name).write_text(json.dumps(data, indent=2, ensure_ascii=False) + '\n')

inventory = json.loads((AUTHOR / 'inventory.json').read_text())
prep = json.loads((AUTHOR / 'correction-prep.json').read_text())
attempts = [json.loads(line) for line in (AUTHOR / 'attempts.ndjson').read_text().splitlines() if line.strip()]
launch = next(x for x in attempts if x.get('id') == 'green-map-04' and x.get('status') == 'STARTING')
prep_hashes = {x['path']: x['sha256'] for x in prep['files']}
launch_hashes = {x['path']: x['sha256'] for x in launch['source_hashes']}
source_audit = []
for entry in inventory['files']:
    path = entry['path']
    current = (WT / path).read_bytes()
    committed = git('show', SHA + ':' + path)
    hashes = {
        'inventory': entry['sha256'],
        'correction_prep': prep_hashes[path],
        'prelaunch': launch_hashes[path],
        'current_worktree': digest(current),
        'final_commit_blob': digest(committed),
    }
    source_audit.append({'path': path, 'hashes': hashes, 'all_equal': len(set(hashes.values())) == 1})
diff_paths = git('diff', '--name-only', PARENT + '..' + SHA).decode().splitlines()
save('source-equivalence.json', {
    'frozen_sha': SHA, 'parent_sha': PARENT,
    'observed_head': git('rev-parse', 'HEAD').decode().strip(),
    'worktree_clean': not git('status', '--porcelain').strip(),
    'exact_inventory': sorted(diff_paths) == sorted(x['path'] for x in inventory['files']),
    'source_equality': source_audit,
    'execution_revision': 'bounded uncommitted WIP based on e093; final source blobs equal the prelaunch hashes',
    'independent_runtime': 'NOT_RUN',
})

xml_dirs = ['red-map-01-xml', 'red-map-02-xml', 'red-map-served-03-xml',
            'green-map-04-test-xml', 'green-map-04-integrationTest-xml']
xml_audit = []
input_hashes = []
for directory in xml_dirs:
    for path in sorted((AUTHOR / directory).glob('*.xml')):
        raw = path.read_bytes()
        suite = ET.fromstring(raw)
        cases = []
        for case in suite.findall('testcase'):
            bad = list(case.findall('failure')) + list(case.findall('error'))
            cases.append({
                'name': case.get('name'),
                'outcome': 'FAIL' if bad else ('SKIP' if case.find('skipped') is not None else 'PASS'),
                'failure_type': bad[0].get('type') if bad else None,
                'failure_message': bad[0].get('message') if bad else None,
            })
        stdout = suite.findtext('system-out', '')
        diagnostics = [line.strip() for line in stdout.splitlines() if line.startswith('Unexpected admitted file:')]
        observed = {
            'tests': len(cases),
            'failures_and_errors': sum(c['outcome'] == 'FAIL' for c in cases),
            'skipped': sum(c['outcome'] == 'SKIP' for c in cases),
        }
        declared = {k: int(suite.get(k, '0')) for k in ['tests', 'failures', 'errors', 'skipped']}
        item = {
            'path': str(path), 'sha256': digest(raw), 'suite': suite.get('name'),
            'timestamp': suite.get('timestamp'), 'declared': declared, 'observed': observed,
            'counts_match': observed['tests'] == declared['tests']
                and observed['failures_and_errors'] == declared['failures'] + declared['errors']
                and observed['skipped'] == declared['skipped'],
            'cases': cases, 'safe_served_diagnostics': diagnostics,
        }
        xml_audit.append(item)
        copy_path = OUT / 'original-xml' / directory / path.name
        copy_path.parent.mkdir(parents=True, exist_ok=True)
        copy_path.write_bytes(raw)
        input_hashes.append({'path': str(path), 'bytes': len(raw), 'sha256': digest(raw),
                             'immutable_copy': str(copy_path)})
save('original-xml-audit.json', xml_audit)

log_audit = []
for filename in ['red-map-01.log', 'red-map-02.log', 'red-map-served-03.log', 'green-map-04.log', 'commit-map-01.log']:
    path = AUTHOR / filename
    raw = path.read_bytes()
    lines = raw.decode(errors='replace').splitlines()
    fragments = [{'line': i + 1, 'text': line} for i, line in enumerate(lines)
                 if re.search(r'BUILD (?:SUCCESSFUL|FAILED)|tests completed|Unexpected admitted file|FAILED$|ClassCastException|unsafe or invalid file upload', line)]
    log_audit.append({'path': str(path), 'sha256': digest(raw), 'bytes': len(raw), 'lines': len(lines),
                      'safe_outcome_fragments': fragments})
    input_hashes.append({'path': str(path), 'bytes': len(raw), 'sha256': digest(raw)})
save('original-log-audit.json', log_audit)

artifacts = []
for entry in json.loads((AUTHOR / 'served-artifact-sha256.json').read_text()):
    path = AUTHOR / entry['path']
    raw = path.read_bytes()
    artifacts.append({'path': str(path), 'bytes': len(raw), 'sha256': digest(raw),
                      'matches_author': len(raw) == entry['bytes'] and digest(raw) == entry['sha256']})
    input_hashes.append({'path': str(path), 'bytes': len(raw), 'sha256': digest(raw)})
save('served-artifact-audit.json', artifacts)

for filename in ['inventory.json', 'correction-prep.json', 'final-receipt.json', 'attempts.ndjson',
                 'served-artifact-sha256.json', 'source-ledger.json']:
    path = AUTHOR / filename
    raw = path.read_bytes()
    input_hashes.append({'path': str(path), 'bytes': len(raw), 'sha256': digest(raw)})
for entry in inventory['files']:
    path = WT / entry['path']
    raw = path.read_bytes()
    input_hashes.append({'path': str(path), 'bytes': len(raw), 'sha256': digest(raw), 'commit': SHA})
for rel in ['docs/wireframes/admin/135-admin-map.md', 'docs/architecture/reference-rutcampustrack-design/backend-requests.md',
            'docs/product/decisions/2026-10-11-map-legend-deferred.md', 'docs/sources/manifest.yaml',
            'services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/map/CampusMapAdminController.java',
            'services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/map/CampusMapAdminRepository.java',
            'services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/map/CampusMapReadService.java',
            'services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/security/RoleCheckAspect.java',
            'services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/admin/AcademicAdminAccess.java']:
    path = WT / rel
    raw = path.read_bytes()
    input_hashes.append({'path': str(path), 'bytes': len(raw), 'sha256': digest(raw), 'commit': SHA})
save('input-hash-manifest.json', {'frozen_sha': SHA, 'files': input_hashes})
print(json.dumps({
    'source_all_equal': all(x['all_equal'] for x in source_audit),
    'exact_inventory': sorted(diff_paths) == sorted(x['path'] for x in inventory['files']),
    'xml_files': len(xml_audit), 'xml_counts_all_match': all(x['counts_match'] for x in xml_audit),
    'map_suites': [{k: x[k] for k in ['path', 'declared', 'observed']} for x in xml_audit if '.map.' in x['suite']],
    'served_artifacts': len(artifacts), 'served_hashes_all_match': all(x['matches_author'] for x in artifacts),
}, indent=2))
