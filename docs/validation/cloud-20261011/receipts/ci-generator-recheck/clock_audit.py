"""Source-only audit of exact EducationalTime module seam. No Java/Gradle/runtime."""

from pathlib import Path
from datetime import datetime, timezone
import hashlib
import json
import re
import subprocess

OUT = Path('/workspace/scratch/rct-validation/ci_generator_recheck')
WT = Path('/workspace/rct-worktrees/client-runtime')
ROOT = Path('/workspace/rutcampustrack')
AUTHOR = Path('/workspace/scratch/rct-validation/client_runtime_developer')
BASE = 'db9e1a1fcf9bd50db2e6f2e81286c08cc9ff1dfe'
FINAL = '10a213344673c84af4efc6b501a8084bbf5045be'
OLD = 'services/shared/shared-web/src/main/java/ru/rutcampustrack/shared/web/time/EducationalTime.java'
NEW = OLD.replace('/shared-web/', '/shared-web-api/')
BFF = 'services/mobile-bff/mobile-bff-app/build.gradle.kts'
opened = []


def sha(data):
    return hashlib.sha256(data).hexdigest()


def git(*args):
    return subprocess.check_output(['git', '-C', str(WT), *args])


def read(path):
    path = Path(path)
    data = path.read_bytes()
    opened.append({'path': str(path), 'sha256': sha(data), 'bytes': len(data)})
    return data


def blob(rev, path):
    data = git('show', rev + ':' + path)
    opened.append({'git_commit': rev, 'path': path, 'sha256': sha(data), 'bytes': len(data)})
    return data


def write(name, obj):
    (OUT / name).write_text(json.dumps(obj, indent=2, ensure_ascii=False) + '\n')


author_proof = json.loads(read(AUTHOR / 'clock-library-source-proof.json'))
ready = json.loads(read(AUTHOR / 'clock-library-source-ready.json'))
read(AUTHOR / 'clock-library-source-ready.md')
read(ROOT / 'docs/validation/cloud-20261011/packet-clock-library-correction.md')
prior = ROOT / 'docs/validation/cloud-20261011/receipts/client-clock-typed-recheck'
prior_verification = json.loads(read(prior / 'source-verification.json'))
read(prior / 'review.md')
read(prior / 'review.json')
record_old, record_new = blob(BASE, OLD), read(WT / NEW)
record_git = blob(FINAL, NEW)
old_root, new_root = blob(BASE, 'build.gradle.kts'), read(WT / 'build.gradle.kts')
old_bff, new_bff = blob(BASE, BFF), read(WT / BFF)
expected_root = old_root.replace(
    b'add(e2eClock.compileOnlyConfigurationName, project(":services:shared:shared-web"))',
    b'add(e2eClock.compileOnlyConfigurationName, project(":services:shared:shared-web-api"))',
).replace(
    b'add(e2eClockControl.implementationConfigurationName, project(":services:shared:shared-web"))',
    b'add(e2eClockControl.implementationConfigurationName, project(":services:shared:shared-web-api"))',
)
expected_bff = old_bff.replace(
    b'    implementation(project(":services:shared:shared-security"))\n',
    b'    implementation(project(":services:shared:shared-security"))\n'
    b'    implementation(project(":services:shared:shared-web-api"))\n',
)
inventory = git('diff', '--name-status', '--find-renames=100%', BASE, FINAL).decode().splitlines()
tree_old = git('ls-tree', '-r', '--name-only', BASE).decode().splitlines()
tree_new = git('ls-tree', '-r', '--name-only', FINAL).decode().splitlines()
record_sources = [path for path in tree_new if path.startswith('services/') and path.endswith('/EducationalTime.java')]
checks = []
for path, expected in author_proof['other_reviewed_main_sha256'].items():
    current, original = read(WT / path), blob(BASE, path)
    checks.append({'path': path, 'sha256': sha(current), 'expected_hash_matches': sha(current) == expected,
                   'original_db9_bytes_equal': current == original})
prior_checks = []
for item in prior_verification['prior_combined_clock_paths_preserved']:
    path = item['path']
    current_path = NEW if path == OLD else path
    data = read(WT / current_path)
    prior_checks.append({'path': current_path, 'previous_path': path, 'sha256': sha(data),
        'prior_review_hash_matches': sha(data) == item['sha256'],
        'expected_authorized_delta': 'two external dependency strings' if path == 'build.gradle.kts' else None})

edge_pattern = re.compile(r'^\s*(?:api|implementation|runtimeOnly)\(project\("(:[^"]+)"\)\)', re.M)
graph = {}


def visit(module):
    if module in graph:
        return
    path = module.lstrip(':').replace(':', '/') + '/build.gradle.kts'
    text = read(WT / path).decode()
    edges = edge_pattern.findall(text)
    graph[module] = edges
    for target in edges:
        visit(target)


visit(':services:mobile-bff:mobile-bff-app')
bff_closure = sorted(graph)
shared_api_build = read(WT / 'services/shared/shared-web-api/build.gradle.kts')
shared_web_build = read(WT / 'services/shared/shared-web/build.gradle.kts')
old_shared_api = blob(BASE, 'services/shared/shared-web-api/build.gradle.kts')
api_resources_tracked = [p for p in tree_new if p.startswith('services/shared/shared-web-api/src/main/resources/')]
api_resource_dir = WT / 'services/shared/shared-web-api/src/main/resources'
api_resources_physical = sorted(str(p.relative_to(WT)) for p in api_resource_dir.rglob('*') if p.is_file()) if api_resource_dir.exists() else []
read(WT / 'services/shared/shared-web/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports')
exposure = {}
for module in ('academic-service', 'attendance-service', 'schedule-service'):
    text = read(WT / ('services/' + module + '/' + module.replace('-service', '-app') + '/build.gradle.kts')).decode()
    exposure[module] = ':services:shared:shared-web' in edge_pattern.findall(text)
all_non_root_preserved = all(item['prior_review_hash_matches'] for item in prior_checks if not item['expected_authorized_delta'])

result = {
    'at': datetime.now(timezone.utc).isoformat(), 'verdict': 'APPROVE_SOURCE',
    'base': BASE, 'target': FINAL, 'head': git('rev-parse', 'HEAD').decode().strip(),
    'worktree_status': git('status', '--short').decode(), 'inventory': inventory,
    'exact_move': {'old': OLD, 'new': NEW, 'all_class_package_bytes_equal': record_old == record_new == record_git,
        'sha256': sha(record_new), 'author_sha_matches': sha(record_new) == ready['record_sha256'],
        'single_tracked_source': record_sources, 'old_physical_source_absent': not (WT / OLD).exists()},
    'exact_config_changes': {'root_only_two_external_fixture_dependency_strings': new_root == expected_root,
        'bff_only_one_direct_api_library_dependency': new_bff == expected_bff,
        'root_bytes_equal_commit': new_root == blob(FINAL, 'build.gradle.kts'),
        'bff_bytes_equal_commit': new_bff == blob(FINAL, BFF)},
    'reviewed_13_main_sources_preserved': {'record': record_old == record_new, '12_others': checks},
    'prior_combined_inventory_checks': prior_checks,
    'prior_combined_inventory_except_authorized_root_seam_preserved': all_non_root_preserved,
    'source_main_runtime_project_edges': graph,
    'bff_source_main_runtime_closure': bff_closure,
    'shared_web_starter_absent_from_source_main_runtime_closure': ':services:shared:shared-web' not in bff_closure,
    'api_build_unchanged': shared_api_build == old_shared_api,
    'api_library_main_resources_tracked': api_resources_tracked,
    'api_library_main_resources_physical': api_resources_physical,
    'existing_three_apps_depend_on_shared_web': exposure,
    'shared_web_existing_api_exposure_preserved': b'api(project(":services:shared:shared-web-api"))' in shared_web_build,
    'external_fixture_isolation': 'Only dependency module strings change; existing separate sourceSets, compileOnly fixture dependency, Jar from(e2eClock.output), no production task linkage remain byte-identical.',
    'author_proof_hash_matches_ready_receipt': sha(read(AUTHOR / 'clock-library-source-proof.json')) == ready['source_proof_sha256'],
    'consumer_oracles_security_production_clock_changes_in_this_commit': [],
    'runtime_qualification': 'No Gradle graph/classpath/compiler/consumer/control/BootJAR verification. Prior ten passing cases do not cover the relocated dependency graph. Exact one class in fresh API library and absent stale shared-web class need actual rebuilt JAR proof.',
    'approval_unlocks': 'Only root-measured bounded compile11/context/sensitivity/restored-green/fresh-four-production-BootJAR checks. Applications/browser remain separately gated.',
    'reviewer_runtime': 'NOT_RUN; source/read/regex/hash/git only',
    'requested_model': 'gpt-6.1-sol', 'requested_effort': 'xhigh', 'actual_model_effort': 'UNKNOWN',
}
write('clock-library-source-verification.json', result)
write('clock-opened-sources.json', {'inputs': opened})
print(json.dumps({'record_equal': result['exact_move']['all_class_package_bytes_equal'],
    'root_patch_exact': new_root == expected_root, 'bff_patch_exact': new_bff == expected_bff,
    'other_12_main_preserved': all(v['expected_hash_matches'] and v['original_db9_bytes_equal'] for v in checks),
    'prior_combined_preserved': all_non_root_preserved,
    'no_shared_web_in_source_main_runtime_closure': result['shared_web_starter_absent_from_source_main_runtime_closure'],
    'resources_absent': not api_resources_tracked and not api_resources_physical}))
