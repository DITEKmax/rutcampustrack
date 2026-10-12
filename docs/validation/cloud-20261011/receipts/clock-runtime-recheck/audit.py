"""Independent read/parser/hash/archive-only audit; no reviewed helper is executed/imported."""

from pathlib import Path
from datetime import datetime, timezone
from io import BytesIO
from xml.etree import ElementTree
from zipfile import ZipFile
from collections import Counter
import hashlib
import json
import re
import subprocess

OUT = Path('/workspace/scratch/rct-validation/ci_generator_recheck/clock-runtime-recheck')
AUTHOR = Path('/workspace/scratch/rct-validation/client_runtime_developer')
WT = Path('/workspace/rct-worktrees/client-runtime')
ROOT = Path('/workspace/rutcampustrack')
TARGET = '10a213344673c84af4efc6b501a8084bbf5045be'
inputs = {}


def sha(data):
    return hashlib.sha256(data).hexdigest()


def digest_file(path):
    h = hashlib.sha256()
    with path.open('rb') as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b''):
            h.update(block)
    return h.hexdigest()


def read(path):
    path = Path(path)
    data = path.read_bytes()
    inputs[str(path)] = {'path': str(path), 'sha256': sha(data), 'bytes': len(data)}
    return data


def jread(path):
    return json.loads(read(path))


def git(*args):
    return subprocess.check_output(['git', '-C', str(WT), *args])


def write(name, value):
    (OUT / name).write_text(json.dumps(value, indent=2, ensure_ascii=False) + '\n')


completion = jread(AUTHOR / 'clock-runtime-bounded-completion.json')
read(AUTHOR / 'clock-runtime-bounded-completion.md')
initial = jread(AUTHOR / 'compile-green-10a-launch.json')
tracked = [p for p in git('ls-files', '-z').decode().split('\0') if p]
current = {p: digest_file(WT / p) for p in tracked if (WT / p).is_file()}
current_mismatches = [p for p,d in initial['source_sha256'].items() if current.get(p) != d]
phase_bridges = []
for run in completion['runs']:
    launch = jread(AUTHOR / run['launch_receipt'])
    log = read(AUTHOR / run['log'])
    changed = [p for p,d in launch['source_sha256'].items() if initial['source_sha256'].get(p) != d]
    expected_changed = []
    if run['phase'] == 'negative-academic-10a':
        expected_changed = [p for p in tracked if p.endswith('/EducationalSessionClockIsolationTest.java')]
    if run['phase'] == 'negative-attendance-10a':
        expected_changed = [p for p in tracked if p.endswith('/EducationalRetentionClockIsolationTest.java')]
    phase_bridges.append({'phase': run['phase'], 'session_id': run['session_id'],
        'terminal_chunk': run['terminal_chunk'], 'native_exit': launch['native_exit'],
        'receipt_sha256': inputs[str(AUTHOR / run['launch_receipt'])]['sha256'],
        'prelaunch_utc': launch['prelaunch_utc'], 'terminal_utc': launch['terminal_utc'],
        'argv': launch['argv'], 'cwd': launch['cwd'], 'git_head': launch['git_head'],
        'tracked_hash_count': len(launch['source_sha256']), 'changed_from_initial_paths': changed,
        'only_expected_fixture_or_none_changed': changed == expected_changed,
        'identical_tracked_path_set': set(launch['source_sha256']) == set(initial['source_sha256']),
        'native_exit_matches_completion': launch['native_exit'] == run['native_exit'],
        'argv_cwd_match_completion': launch['argv'] == run['argv'] and launch['cwd'] == run['cwd'],
        'log_sha256': sha(log), 'log_hash_matches_receipt_and_completion': sha(log) == launch['log_sha256'] == run['log_sha256'],
        'expected_gradle_terminal_in_log': (b'BUILD SUCCESSFUL' if run['native_exit'] == 0 else b'BUILD FAILED') in log,
        'typed_context_success_in_initial_log': b'Typed teaching-time context controls PASS' in log if run['phase'] == 'compile-green-10a' else None})
write('source-run-bridges.json', {'target': TARGET, 'head': git('rev-parse', 'HEAD').decode().strip(),
    'worktree_status': git('status', '--short').decode(), 'initial_current_all_tracked_sha_matches': not current_mismatches,
    'tracked_count': len(current), 'tracked_path_set_matches_initial': set(current) == set(initial['source_sha256']),
    'mismatches': current_mismatches, 'phase_bridges': phase_bridges,
    'tool_provenance_limit': 'Session IDs/chunks and native exits retained in author receipts. Raw tool-result transcript unavailable; no claim of reviewer observing the original live process.'})

class_sources = {
    'academic': 'services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/studentprojection/EducationalSessionClockIsolationTest.java',
    'attendance': 'services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/studentrequest/EducationalRetentionClockIsolationTest.java',
    'bff': 'services/mobile-bff/mobile-bff-app/src/test/java/ru/rutcampustrack/mobilebff/error/EducationalProblemClockIsolationTest.java',
}
source_texts = {k: read(WT / v).decode() for k,v in class_sources.items()}
known_methods = {k: re.findall(r'\bvoid\s+([A-Za-z_$][A-Za-z0-9_$]*)\s*\(', text) for k,text in source_texts.items()}
xml_records = []
selected_phase = {
    'compile-green-10a': {'academic', 'attendance', 'bff'},
    'negative-academic-10a': {'academic'},
    'negative-attendance-10a': {'attendance'},
    'restored-green-10a': {'academic', 'attendance'},
}
for label, selected in selected_phase.items():
    summary = jread(AUTHOR / (label + '-xml.json'))
    for item in summary:
        module = 'academic' if '.academic.' in item['classname'] else 'attendance' if '.attendance.' in item['classname'] else 'bff'
        preserved = AUTHOR / (label + '-' + Path(item['path']).name)
        data = read(preserved)
        document = ElementTree.fromstring(data)
        nodes = document.findall('testcase')
        cases = []
        for ordinal, node in enumerate(nodes, 1):
            failed = node.find('failure')
            error = node.find('error')
            skipped = node.find('skipped')
            status = 'FAILED' if failed is not None else 'ERROR' if error is not None else 'SKIPPED' if skipped is not None else 'PASSED'
            # Never serialize raw testcase names or parameter values; map only known source method prefix.
            name = node.get('name', '')
            matches = [method for method in known_methods[module] if name.startswith(method + '(') or name == method]
            selected_method = 'expiredRealSessionIsDeniedRegardlessOfTeachingDate' if label == 'negative-academic-10a' else 'realExpiredAttachmentDownloadIsDeniedRegardlessOfTeachingDate' if label == 'negative-attendance-10a' else None
            case_text = ''.join(node.itertext())
            cases.append({'ordinal': ordinal, 'status': status, 'canonical_source_method': matches[0] if len(matches) == 1 else None,
                'failure_stack_mentions_exact_selected_source_method': selected_method in case_text if selected_method and failed is not None else None,
                'identity_qualification': 'Parameterized display name omitted; ordinal only unless known source method directly matches.',
                'failure_is_expected_no_throwable_assertion': 'Expecting code to raise a throwable.' in (failed.text or '') if failed is not None else None,
                'failure_type_is_assertion': failed.get('type') == 'java.lang.AssertionError' if failed is not None else None})
        statuses = Counter(c['status'] for c in cases)
        expected_counts = {'tests': len(cases), 'skipped': statuses['SKIPPED'], 'failures': statuses['FAILED'], 'errors': statuses['ERROR']}
        actual_counts = {k:int(document.get(k, '-1')) for k in expected_counts}
        timestamp = document.get('timestamp')
        launch = jread(AUTHOR / (label + '-launch.json'))
        xml_time = datetime.fromisoformat(timestamp).replace(tzinfo=timezone.utc) if timestamp else None
        start = datetime.fromisoformat(launch['prelaunch_utc'])
        end = datetime.fromisoformat(launch['terminal_utc'])
        xml_records.append({'phase': label, 'module': module, 'selected_by_exact_run': module in selected,
            'preserved_original': str(preserved), 'xml_sha256': sha(data),
            'original_hash_matches_safe_summary': sha(data) == item['sha256'],
            'counts': actual_counts, 'counts_match_nodes_and_safe_summary': actual_counts == expected_counts == item['counts'],
            'cases': cases, 'xml_timestamp_within_actual_phase': start <= xml_time <= end if xml_time else 'UNKNOWN',
            'unselected_qualification': 'Historical retained XML from previous initial phase; NOT credited as new run.' if module not in selected else None})
write('original-xml-reconciliation.json', {'records': xml_records,
    'qualification': 'Only exact selected module/class XML contributes to phase counts. Raw XML properties/streams/failure bodies and testcase parameter labels never exported.'})

mutations = [
    ('academic', 'negative_academic', 'negative-academic-10a',
     b'context.registerBean(Clock.class, () -> realClock);', b'context.registerBean(Clock.class, () -> teachingClock);'),
    ('attendance', 'negative_attendance', 'negative-attendance-10a',
     b'Clock.fixed(realNow, ZoneOffset.UTC), mock(JournalAttachmentPort.class)',
     b'Clock.fixed(Instant.parse(teachingInstant), ZoneOffset.UTC), mock(JournalAttachmentPort.class)'),
]
mutation_records = []
for module, prefix, label, old, new in mutations:
    path = class_sources[module]
    current_data = read(WT / path)
    committed = git('show', TARGET + ':' + path)
    wrong = committed.replace(old, new)
    launch = jread(AUTHOR / (label + '-launch.json'))
    install = jread(AUTHOR / (prefix + '-install.json'))
    restore = jread(AUTHOR / (prefix + '-restore.json'))
    original = read(AUTHOR / (prefix + '-original.java'))
    oracle = jread(AUTHOR / (label + '-oracle.json'))
    mutation_records.append({'module': module, 'fixture_path': path, 'replacement_occurrences': committed.count(old),
        'one_fixture_wiring_replacement_digest_matches_red_prelaunch': committed.count(old) == 1 and sha(wrong) == launch['source_sha256'][path] == install['wrong_fixture_sha256'],
        'original_committed_current_and_preserved_bytes_equal': committed == original == current_data,
        'restore_sha_matches_original_current': restore['actual_sha256'] == sha(current_data) == restore['baseline_sha256'],
        'restore_diff_paths_empty': restore['diff_paths'] == [], 'oracle': oracle,
        'production_mutation': 'NONE; independently compared all per-phase tracked hashes, exact one test fixture changed.'})
read(AUTHOR / 'fixture-clock-seam.py')
read(AUTHOR / 'run-clock-phase.py')
read(AUTHOR / 'capture-clock-xml.py')
read(AUTHOR / 'clock-bounded-runtime-plan.json')
write('sensitivity-restoration.json', {'controls': mutation_records,
    'sensitivity_scope': 'Actual consumer failures under wrong operational clock in test wiring; no production guard or global system clock mutation.',
    'test_quality': 'Academic actual service/Spring constructor selection with controlled authority port. Attendance actual download/cleanup with controlled storage ports; synthetic query expiry emulation is not MongoDB acceptance. BFF actual response header/timestamp projection. Explicit rule/result assertions, zero skips, no broad catches.'})

inspection = jread(AUTHOR / 'artifact-inspection-launch.json')
inspection_log = read(AUTHOR / 'artifact-inspection.log')
structure = jread(AUTHOR / 'current-artifact-structure.json')
education = 'ru/rutcampustrack/shared/web/time/EducationalTime.class'
autoconfig = 'ru/rutcampustrack/shared/web/autoconfigure/SharedWebAutoConfiguration.class'


def walk(archive, prefix='', depth=0):
    if depth > 8:
        raise ValueError('Archive nesting exceeds bounded inspection scope')
    observations = {'educational_classes': [], 'fixture_entries': [], 'shared_web_auto_config': [], 'nested_archives': 0, 'max_depth': depth}
    for info in archive.infolist():
        name = info.filename
        qualified = prefix + name
        if name.endswith(education):
            observations['educational_classes'].append({'entry': qualified, 'sha256': sha(archive.read(info))})
        if 'ru/rutcampustrack/e2e/' in name or 'clock-fixture' in name:
            observations['fixture_entries'].append(qualified)
        if name.endswith(autoconfig):
            observations['shared_web_auto_config'].append(qualified)
        if name.endswith('.jar'):
            with ZipFile(BytesIO(archive.read(info))) as nested:
                children = walk(nested, qualified + '!/', depth + 1)
            for key in ('educational_classes','fixture_entries','shared_web_auto_config'):
                observations[key].extend(children[key])
            observations['nested_archives'] += 1 + children['nested_archives']
            observations['max_depth'] = max(observations['max_depth'], children['max_depth'])
    return observations


artifacts = []
for expected in structure['production']:
    relative = expected['path']
    path = WT / relative
    digest = digest_file(path)
    inputs[str(path)] = {'path':str(path),'sha256':digest,'bytes':path.stat().st_size}
    with ZipFile(path) as archive:
        names = archive.namelist()
        scan = walk(archive)
        manifest = archive.read('META-INF/MANIFEST.MF').decode()
        start = next(line.removeprefix('Start-Class: ').strip() for line in manifest.splitlines() if line.startswith('Start-Class: '))
        git_props = archive.read('BOOT-INF/classes/git.properties').decode()
        commit = next(line.removeprefix('git.commit.id=').strip() for line in git_props.splitlines() if line.startswith('git.commit.id='))
        artifacts.append({'path':relative,'sha256':digest,'bytes':path.stat().st_size,
            'digest_matches_inspection_prelaunch_and_actual_receipt': digest == inspection['source_artifact_sha256'][relative] == expected['sha256'],
            'start_class':start,'main_class_exists':f'BOOT-INF/classes/{start.replace(".","/")}.class' in names,
            'embedded_git_commit':commit,'embedded_commit_matches_frozen_source':commit == TARGET,
            'educational_classes':scan['educational_classes'],
            'exact_single_api_class_matches_receipt':scan['educational_classes'] == [expected['educational_class']],
            'fixture_absent_recursive':not scan['fixture_entries'],
            'shared_web_auto_configuration_entries':scan['shared_web_auto_config'],
            'shared_web_starter_filename_present': 'BOOT-INF/lib/shared-web-0.1.0.jar' in names,
            'nested_archives_inspected':scan['nested_archives'],'maximum_nesting_depth':scan['max_depth']})
fixture = WT / 'build/e2e-clock/clock-fixture.jar'
fixture_digest = digest_file(fixture)
with ZipFile(fixture) as archive:
    fixture_classes = sorted(info.filename for info in archive.infolist() if info.filename.endswith('.class'))
inputs[str(fixture)] = {'path':str(fixture),'sha256':fixture_digest,'bytes':fixture.stat().st_size}
inspector_hashes = {}
for path,expected in inspection['source_artifact_sha256'].items():
    if path.endswith('.py'):
        local = Path(path) if path.startswith('/') else WT / path
        inspector_hashes[path] = sha(read(local)) == expected
read(WT / 'tests/e2e/backend-clock/verify-jar-isolation-control.py')
write('independent-current-artifact-inspection.json', {'production':artifacts,
    'fixture':{'path':str(fixture),'sha256':fixture_digest,'bytes':fixture.stat().st_size,'classes':fixture_classes,
        'hash_matches_actual_and_inspection_prelaunch':fixture_digest == structure['fixture']['sha256'] == inspection['source_artifact_sha256']['build/e2e-clock/clock-fixture.jar'],
        'only_two_fixture_classes':fixture_classes == ['ru/rutcampustrack/e2e/ClockInitializer.class','ru/rutcampustrack/e2e/FileClock.class'],
        'distinct_from_stale049ab':fixture_digest != '6a474b4f95e9bdde92acff655a2a7f0d3120e369a3f9ea240eb8a1908dcf6806'},
    'inspection_receipt':{'session_id':completion['inspection']['session_id'],'terminal_chunk':completion['inspection']['terminal_chunk'],
        'native_exits':inspection['native_exits'],'argv_sequence':inspection['argv_sequence'],'cwd':inspection['cwd'],
        'prelaunch_utc':inspection['prelaunch_utc'],'terminal_utc':inspection['terminal_utc'],
        'log_sha256':sha(inspection_log),'log_hash_matches_receipt':sha(inspection_log) == inspection['log_sha256'],
        'inspector_prelaunch_source_hash_matches':inspector_hashes},
    'verification_kind':'Reviewer own recursive ZIP metadata/class-byte hashing only; no Java/JVM/Gradle/apps/process execution.',
    'limit':'Embedded Git commit and captured source hashes bind these exact built artifacts to10a; no proof of root-integrated runtime graph or client/security/geo/D005/CC03 behavior.'})

for path in [
    'services/shared/shared-web-api/src/main/java/ru/rutcampustrack/shared/web/time/EducationalTime.java',
    'services/shared/shared-web-api/build.gradle.kts','services/shared/shared-web/build.gradle.kts',
    'services/mobile-bff/mobile-bff-app/build.gradle.kts','build.gradle.kts',
    'services/shared/shared-web/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports',
    'tests/e2e/backend-clock/src/main/java/ru/rutcampustrack/e2e/ClockInitializer.java',
    'tests/e2e/backend-clock/src/main/java/ru/rutcampustrack/e2e/FileClock.java',
    'tests/e2e/backend-clock/src/control/java/ru/rutcampustrack/e2e/ClockFixtureControl.java',
    'services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/config/ClockConfig.java',
    'services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/config/ClockConfig.java',
    'services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/config/ClockConfig.java',
    'services/mobile-bff/mobile-bff-app/src/main/java/ru/rutcampustrack/mobilebff/MobileBffRuntimeConfig.java',
    'services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/studentprojection/StudentProjectionScopeService.java',
    'services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/studentrequest/StudentRequestService.java',
    'services/mobile-bff/mobile-bff-app/src/main/java/ru/rutcampustrack/mobilebff/error/MobileProblemHandler.java',
]:
    read(WT / path)
for path in ['TASK-CONTRACT.md','CURRENT.md','decisions.md','questions.md','resources.md','packet-clock-runtime-review.md']:
    read(ROOT / 'docs/validation/cloud-20261011' / path)
write('opened-sources-and-receipts.json',{'at':datetime.now(timezone.utc).isoformat(),'files':list(inputs.values()),
    'raw_logs_XML': 'Hashed/privately parsed only. No contents, streams, properties, sensitive values or failure bodies exported.'})
selected = [v for v in xml_records if v['selected_by_exact_run']]
print(json.dumps({'all6599_sources_match':not current_mismatches,
    'all_phase_bridges_exact':all(v['only_expected_fixture_or_none_changed'] and v['log_hash_matches_receipt_and_completion'] for v in phase_bridges),
    'all_selected_xml_counts_hashes_match':all(v['counts_match_nodes_and_safe_summary'] and v['original_hash_matches_safe_summary'] for v in selected),
    'all_selected_xml_timestamps_current':all(v['xml_timestamp_within_actual_phase'] is True for v in selected),
    'all_mutation_restore_exact':all(v['one_fixture_wiring_replacement_digest_matches_red_prelaunch'] and v['original_committed_current_and_preserved_bytes_equal'] and v['restore_sha_matches_original_current'] for v in mutation_records),
    'all_four_artifacts_exact':all(v['digest_matches_inspection_prelaunch_and_actual_receipt'] and v['exact_single_api_class_matches_receipt'] and v['embedded_commit_matches_frozen_source'] and v['fixture_absent_recursive'] for v in artifacts),
    'bff_shared_web_autoconfig_absent':not artifacts[-1]['shared_web_auto_configuration_entries'],
    'archive_class_hashes':[v['educational_classes'][0]['sha256'] for v in artifacts],
    'unselected_stale_XMLs_excluded':sum(not v['selected_by_exact_run'] for v in xml_records)}))
