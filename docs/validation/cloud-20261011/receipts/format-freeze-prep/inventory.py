#!/usr/bin/env python3
"""Read-only formatter preparation: no formatter runs or source changes."""
from collections import Counter
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import zipfile

OUT = Path(__file__).resolve().parent
ROOT = Path('/workspace/rutcampustrack')
FROZEN = 'c4d926b0c5a81ae00b639bd48dd22088991b0f2b'
EXPORT = 'f09b70e383b1c29b31960b5da99647f8ae6530bf'
RECEIPTS = 'docs/validation/cloud-20261011/receipts/'
os.umask(0o077)

def git(*args):
    return subprocess.check_output(['git', *args], cwd=ROOT)

def save(name, value):
    (OUT / name).write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n')

def walk(value):
    if isinstance(value, dict):
        yield value
        for x in value.values():
            yield from walk(x)
    elif isinstance(value, list):
        for x in value:
            yield from walk(x)

def seal(path):
    file = ROOT / path
    digest = hashlib.sha256(file.read_bytes()).hexdigest()
    receipt = ROOT / '/'.join(path.split('/')[:5])
    rel = str(file.relative_to(receipt))
    matches = []
    for meta in receipt.glob('*.json'):
        try:
            data = json.loads(meta.read_text())
        except (ValueError, UnicodeError):
            continue
        for item in walk(data):
            names = [v for k, v in item.items() if k in ('path', 'file', 'name') and isinstance(v, str)]
            hashes = [v for k, v in item.items() if 'sha256' in k and isinstance(v, str)]
            if digest in hashes and any(n == rel or n.endswith('/' + rel) for n in names):
                matches.append(str(meta.relative_to(ROOT)))
                break
    archives = []
    for archive in receipt.glob('*.zip'):
        with zipfile.ZipFile(archive) as z:
            for entry in z.infolist():
                if entry.file_size > 2_000_000 or not (entry.filename == rel or entry.filename.endswith('/' + rel)):
                    continue
                if hashlib.sha256(z.read(entry)).hexdigest() == digest:
                    archives.append({'archive': str(archive.relative_to(ROOT)), 'member': entry.filename})
    return {'path': path, 'sha256': digest, 'bytes': file.stat().st_size,
            'matching_original_hash_manifests': matches, 'matching_original_zip_members': archives,
            'exact_original_identity_verified': bool(matches or archives)}

current = json.loads((OUT / 'current-runner-inventory.json').read_text())
document_registries = {
    'docs/design/packets/JS-STUDENT-01/manifest.json': 'canonical design packet asset/source identity registry; separate source owner',
    'docs/presentations/manifest.json': 'canonical presentation source identity registry; immutable historical source owner',
    'docs/validation/cloud-20261011/receipts/manifest-post-hooks.json': 'machine verification hash/normalization receipt; preserve producer evidence',
    'docs/validation/cloud-20261011/skills/manifest.json': 'imported skills package identity manifest; preserved archive/source identity',
}
frozen_paths = git('ls-tree', '-r', '--name-only', '-z', FROZEN).decode().split('\0')[:-1]
assert {x['path'] for x in current} == set(frozen_paths)
seals = [seal(x['path']) for x in current if x.get('tool') in ('java', 'python', 'sql') and x['path'].startswith(RECEIPTS)]
sealed = {x['path']: x for x in seals if x['exact_original_identity_verified']}
inventory = []
for item in current:
    row = dict(item)
    p = row['path']; file = ROOT / p
    row['extension'] = file.suffix or '(none)'
    if p in sealed:
        row.pop('tool', None)
        row['disposition'] = 'intentional exact verification evidence/source copy; preserve original identity'
    elif p in document_registries:
        row.pop('tool', None)
        row['disposition'] = document_registries[p]
        row['preserved_sha256'] = hashlib.sha256(file.read_bytes()).hexdigest()
    elif item.get('tool'):
        row['disposition'] = 'maintained author source eligible for existing ' + item['tool']
        if p.startswith(RECEIPTS):
            row['qualification'] = 'No immutable original hash/ZIP identity found; retain as authored harness, explicit review before write'
    elif '/db/migration/V' in p and p.endswith('.sql'):
        row['disposition'] = 'versioned Flyway source; preserve checksum; no repair/clean/reformat'
    elif p.endswith(('package-lock.json', 'gradle.lockfile', 'uv.lock')):
        row['disposition'] = 'dependency lock; native dependency tool only'
    elif '/generated/' in p or re.search(r'_pb2(?:_grpc)?\.py$', p):
        row['disposition'] = 'generated API/protobuf consumer; generator owner only'
    elif p.startswith(('docs/archive/', 'docs/research/', '.agent/')):
        row['disposition'] = 'historical/source/evidence copy; immutable; atomic COW before any later accepted edit'
    elif item.get('reason', '').startswith('intentional'):
        row['disposition'] = 'reviewed exact fixture/snapshot payload; byte owner review required'
    elif p.endswith('.proto') and p.startswith('proto/'):
        row['disposition'] = 'handwritten canonical proto contract; no existing agreed formatter; preserve wire fields'
    elif p.startswith('docs/openapi/') and p.endswith('.json'):
        row['disposition'] = 'accepted OpenAPI contract snapshot; preserve generator/snapshot owner and drift checks; no generic format'
    elif file.suffix in ('.kts', '.kt', '.ps1', '.sh', '.toml', '.xml', '.properties', '.conf', '.webmanifest', '.scss') and not p.startswith('docs/'):
        row['disposition'] = 'maintained author config/script; no existing agreed formatter; preserve language checks'
    elif p.startswith(('event-schemas/', 'database/', 'dashboards/')) and p.endswith('.json'):
        row['disposition'] = 'handwritten contract/config; JSON parser and existing schema checks; no current formatter target'
    else:
        row['disposition'] = item.get('reason', 'separate owner review')
    if row.get('tool'):
        row['sha256'] = hashlib.sha256(file.read_bytes()).hexdigest()
    row['hardlink_count'] = file.stat().st_nlink
    if row['hardlink_count'] > 1:
        row['physical_write_constraint'] = 'Never edit in place; atomic private replacement/COW before later authorized modification'
    inventory.append(row)

java_runner = {x['path'] for x in current if x.get('tool') == 'java'}
spotless = {p for p in frozen_paths if p.endswith('.java') and ((p.startswith('services/') and '/src/' in p) or p.startswith('tests/e2e/backend-clock/')) and '/generated/' not in p and '/snapshots/' not in p}
curated_java = {x['path'] for x in inventory if x.get('tool') == 'java'}
curated_python = {x['path'] for x in inventory if x.get('tool') == 'python'}
drivers = {'current_java_runner_count': len(java_runner), 'actual_spotless_target_count': len(spotless),
           'curated_java_count': len(curated_java), 'current_inventory_but_not_spotless': sorted(java_runner - spotless),
           'curated_java_vs_spotless_equal': curated_java == spotless,
           'current_python_count': sum(x.get('tool') == 'python' for x in current), 'curated_python_count': len(curated_python),
           'ruff_existing_driver_would_touch_exact_originals': [x['path'] for x in current if x.get('tool') == 'python' and x['path'] in sealed],
           'prettier_exact_original_collisions': [x['path'] for x in current if x.get('tool') == 'prettier' and x['path'] in sealed],
           'prettier_document_registry_collisions': document_registries,
           'status': 'PREPARATION: exact targeted classifier disposition correction needed before blind --write; no config edits performed'}
common = git('merge-base', FROZEN, EXPORT).decode().strip()
changed = git('diff', '--name-only', '-z', common, EXPORT).decode().split('\0')[:-1]
product_prefixes = ('services/', 'frontends/', 'proto/', 'tests/', 'event-schemas/', 'scripts/')
export_rows = []
by_path = {x['path']: x for x in inventory}
for p in changed:
    if not p.startswith(product_prefixes):
        continue
    author = subprocess.run(['git', 'show', EXPORT + ':' + p], cwd=ROOT, capture_output=True)
    if author.returncode:
        export_rows.append({'path': p, 'status': 'author branch deletion; root integration decision required'})
        continue
    export_hash = hashlib.sha256(author.stdout).hexdigest()
    root_hash = hashlib.sha256((ROOT / p).read_bytes()).hexdigest() if (ROOT / p).exists() else None
    export_rows.append({'path': p, 'export_sha256': export_hash, 'root_sha256': root_hash,
                        'root_export_equal': root_hash == export_hash,
                        'disposition': by_path.get(p, {}).get('disposition', 'new export author input: reclassify at final freeze'),
                        'format_tool': by_path.get(p, {}).get('tool'), 'status': 'expected export overlay candidate; not integrated' if root_hash != export_hash else 'already byte equal'})

save('verified-exact-original-dispositions.json', seals)
save('curated-authored-inventory.json', inventory)
save('driver-target-reconciliation.json', drivers)
save('expected-export-inputs.json', {'root_frozen': FROZEN, 'export_observed': EXPORT, 'common_ancestor': common, 'files': export_rows,
     'limits': 'Read-only forecast; integrator owns actual inventory/hunks; no root-only files assumed deleted; NP01 semantic sources not yet frozen.'})
summary = {'status': 'READ_ONLY_PREPARATION_COMPLETE; semantic freeze pending', 'root_frozen': FROZEN,
           'tracked': len(inventory), 'current_tools': dict(Counter(x.get('tool', 'excluded') for x in current)),
           'curated_tools': dict(Counter(x.get('tool', 'excluded') for x in inventory)),
           'originals_verified': len(sealed), 'exact_original_python': sum(x['path'].endswith('.py') for x in sealed.values()),
           'unsealed_doc_authored_harness_candidates': [x['path'] for x in inventory if x.get('tool') and x['path'].startswith(RECEIPTS)],
           'hardlinked_files': sum(x['hardlink_count'] > 1 for x in inventory),
           'eligible_tool_targets_hardlinked': [x['path'] for x in inventory if x.get('tool') and x['hardlink_count'] > 1],
           'export_changed_sources': len(export_rows), 'export_unequal_sources': sum(not x.get('root_export_equal', False) for x in export_rows),
           'runtime': 'NOT_RUN: no formatting, lint/type/build/Gradle/app/browser/container execution authorized',
           'next': 'Preserve sealed originals with exact perpath exclusions; reconcile actual drivers; refresh inventory after exports+NP01 integration and explicit frozen writer assignment.'}
save('summary.json', summary)
print(json.dumps(summary, ensure_ascii=False))
