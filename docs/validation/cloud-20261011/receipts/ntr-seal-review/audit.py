"""Independent bounded NTR source/evidence audit. No application code is executed."""
from __future__ import annotations

import hashlib
import io
import json
from pathlib import Path
import re
import shlex
import shutil
import subprocess
from datetime import datetime, timezone
import xml.etree.ElementTree as ET
import zipfile

OUT = Path('/workspace/scratch/rct-validation/ntr_seal_review')
WT = Path('/workspace/rct-worktrees/notification-provider')
AUTHOR = Path('/workspace/scratch/rct-validation/notification_provider_developer')
SEALED = AUTHOR / 'NTR01-recheck'
PARTIAL = Path('/workspace/scratch/rct-validation/map_validation_recheck/ntr01-recheck')
ROOT = Path('/workspace/rutcampustrack')
DOCS = ROOT / 'docs/validation/cloud-20261011'
PRIOR = DOCS / 'receipts/notification-recovery-review'
NEW = 'd674ea1ff1d26bf40f2585e0a3438c1896e60a1b'
OLD = 'fa02ce439dfb989fc28eaddcbf1a8840d16bf9ff'
TEST = 'services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/integration/WsTicketConcurrencyBoundaryIT.java'
FINAL_TEST_HASH = '4d025a60b627c6777676e5f23a2d7419308ca860e38a51fcaa6ba68cad6cba51'


def sha(data):
    return hashlib.sha256(data).hexdigest()


def write(name, value):
    target = OUT / name
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n')


def read(name):
    return json.loads((SEALED / name).read_text())


def git(*args):
    return subprocess.check_output(['git', '-C', str(WT), *args])


def time_value(value):
    return datetime.fromisoformat(value)


started = datetime.now(timezone.utc).isoformat()
input_rows = []


def remember(path, role):
    data = path.read_bytes()
    input_rows.append({'path': str(path), 'role': role, 'bytes': len(data), 'sha256': sha(data)})
    return data


# Snapshot only governing prose, safe pre-existing archives, and the previous verdict.
# Raw runtime logs, raw application XML, credentials, and runtime bodies are never copied.
governing = ['TASK-CONTRACT.md', 'CURRENT.md', 'decisions.md', 'questions.md',
             'resources.md', 'packet-ntr-seal-review.md']
for name in governing:
    data = remember(DOCS / name, 'governing-input')
    (OUT / 'governing').mkdir(exist_ok=True)
    (OUT / 'governing' / name).write_bytes(data)
assert sha((OUT / 'governing/TASK-CONTRACT.md').read_bytes()) == '8cbd27d431815647886b2f39592905d3f1d4cb985643e5d9fa3f3f669b4df4a5'
for name in ['AGENTS.md', '.agent/orchestration-v2/RULES.md', 'services/AGENTS.md',
             'tests/AGENTS.md', 'docs/agent-workflow.md',
             'docs/validation/cloud-20261011/skills/project/rct-verification/SKILL.md',
             'docs/validation/cloud-20261011/skills/project/rct-source-resolution/SKILL.md']:
    remember(ROOT / name, 'process-input')

partial_rows = []
for path in sorted(PARTIAL.rglob('*')):
    if path.is_file():
        data = remember(path, 'prior-incomplete-review-preserved')
        partial_rows.append({'path': str(path.relative_to(PARTIAL)), 'bytes': len(data), 'sha256': sha(data)})
assert not (PARTIAL / 'completion.json').exists()
assert not (PARTIAL / 'transfer-manifest.json').exists()
assert not (PARTIAL / 'immutable-review-manifest.json').exists()
(OUT / 'prior-partial').mkdir(exist_ok=True)
(OUT / 'prior-partial/findings.md').write_bytes((PARTIAL / 'findings.md').read_bytes())
assert '**APPROVE**' in (PARTIAL / 'findings.md').read_text()

author_archive = remember(AUTHOR / 'NTR01-recheck-originals.zip', 'sealed-author-25-originals')
assert sha(author_archive) == 'f05c655bf04c06e5a409cc855e022f00790bcf11293140d4b250326d81f61317'
(OUT / 'immutable-author-25-originals.zip').write_bytes(author_archive)
with zipfile.ZipFile(io.BytesIO(author_archive)) as az:
    assert az.testzip() is None
    assert len(az.namelist()) == 25
    manifest = json.loads(az.read('source-manifest.json'))
    assert len(manifest['files']) == 24
    for entry in manifest['files']:
        data = az.read(entry['path'])
        assert len(data) == entry['bytes'] and sha(data) == entry['sha256']
        assert data == remember(SEALED / entry['path'], 'sealed-author-original')
    assert az.read('source-manifest.json') == remember(SEALED / 'source-manifest.json', 'sealed-author-manifest')

assert git('rev-parse', 'HEAD').decode().strip() == NEW
assert git('rev-parse', 'HEAD^').decode().strip() == OLD
assert not git('status', '--porcelain')
assert git('diff', '--name-status', OLD, NEW).decode().strip() == 'M\t' + TEST
assert git('diff', '--numstat', OLD, NEW).decode().strip() == '20\t2\t' + TEST
subprocess.run(['git', '-C', str(WT), 'diff', '--check', OLD, NEW], check=True)
diff = git('diff', OLD, NEW)
(OUT / 'reviewed-change.patch').write_bytes(diff)

red = read('NTR01-red-command.json')
green = read('NTR01-green-command.json')
restoration = read('NTR01-restoration.json')
completion = read('completion.json')
before = (SEALED / 'NTR01-before-test.java').read_bytes()
controlled = (SEALED / 'NTR01-controlled-test.java').read_bytes()
final = (SEALED / 'NTR01-restored-target.java').read_bytes()
assert before == git('show', OLD + ':' + TEST)
assert final == remember(WT / TEST, 'current-reviewed-test') == git('show', NEW + ':' + TEST)
assert sha(final) == FINAL_TEST_HASH == restoration['test_sha256'] == completion['final_test_sha256']
temporary = ('            // NTR01 temporary control: only this freshly issued key in owned disposable Redis.\n'
             '            assertThat(redis.expire("ws_ticket:" + issued.ticket(), Duration.ofSeconds(1))).isTrue();\n')
assert temporary.encode() in controlled
assert controlled.replace(temporary.encode(), b'', 1) == final
assert b'redis.expire(' not in final
assert sha(controlled) == red['test_sha256']
assert green['test_sha256'] == FINAL_TEST_HASH
assert red['head'] == green['head'] == OLD
assert red['cwd'] == green['cwd'] == str(WT)
argv = shlex.split(red['command'])
assert argv == shlex.split(green['command'])
assert '--max-workers=1' in argv
assert argv[-1] == 'ru.rutcampustrack.auth.integration.WsTicketConcurrencyBoundaryIT.actualRedisThirtySecondExpiryRejectsUnconsumedTicket'
assert green['changed_dependencies_from_red'] == [TEST]

red_hashes = {entry['path']: entry['sha256'] for entry in red['dependencies']}
green_hashes = {entry['path']: entry['sha256'] for entry in green['dependencies']}
assert len(red_hashes) == len(green_hashes) == 292
assert red_hashes.keys() == green_hashes.keys()
assert [path for path in green_hashes if red_hashes[path] != green_hashes[path]] == [TEST]
dependency_rows = []
for path, expected in green_hashes.items():
    actual = remember(WT / path, 'green-prelaunch-source-byte-bridge')
    assert sha(actual) == expected == sha(git('show', NEW + ':' + path))
    if path == TEST:
        assert red_hashes[path] == sha(controlled)
    else:
        assert expected == red_hashes[path] == sha(git('show', OLD + ':' + path))
    dependency_rows.append({'path': path, 'sha256': expected, 'current_d674_green_equal': True,
                            'red_fa02_equal': path != TEST})

sibling_rows = []
assert len(green['other5unchanged']) == 5
for entry in green['other5unchanged']:
    path = entry['path']
    actual = remember(WT / path, 'unchanged-fa02-package-sibling')
    assert sha(actual) == entry['sha256'] == sha(git('show', OLD + ':' + path)) == sha(git('show', NEW + ':' + path))
    sibling_rows.append({'path': path, 'sha256': sha(actual), 'fa02_d674_current_equal': True})

source = final.decode()
for snippet in ['Duration expectedLifetime = Duration.ofSeconds(30);',
                'Duration observationBudget = Duration.ofSeconds(5);',
                'Duration observationSlack = Duration.ofMillis(250);',
                'long observationStarted = System.nanoTime();',
                'Instant issuanceStarted = Instant.now();',
                'Instant issuanceReturned = Instant.now();',
                '.isLessThanOrEqualTo(observationBudget.toMillis());',
                'expectedLifetime.toMillis() - elapsedMillis - observationSlack.toMillis()',
                'issuanceStarted.plus(expectedLifetime).minus(observationSlack)',
                'issuanceReturned.plus(expectedLifetime).plus(observationSlack)',
                '.isBetween(minimumRemaining, expectedLifetime.toMillis());',
                'await().atMost(Duration.ofSeconds(32)).pollInterval(Duration.ofMillis(100))',
                'assertThat(consume(issued.ticket())).isEqualTo(HttpStatus.NOT_FOUND);']:
    assert snippet in source
assert 'TICKET_TTL' not in source
method = source.split('void actualRedisThirtySecondExpiryRejectsUnconsumedTicket()', 1)[1].split('\n    @Test', 1)[0]
assert '} finally {\n            logout(access);' in method
assert not re.search(r'catch\s*\(|@Disabled|assume|TeachingClock|Clock\.fixed', method)

context = []
for name, path in [
    ('WsTicketService.java', 'services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/service/WsTicketService.java'),
    ('WsTicketController.java', 'services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/controller/WsTicketController.java'),
    ('InternalWsTicketController.java', 'services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/controller/InternalWsTicketController.java'),
    ('WsTicketResponse.java', 'services/auth-service/auth-api-contract/src/main/java/ru/rutcampustrack/auth/dto/WsTicketResponse.java'),
    ('websocket-flow.md', 'docs/architecture/websocket-flow.md')]:
    data = remember(WT / path, 'critical-original-current-source')
    assert data == (SEALED / 'sources' / name).read_bytes() == git('show', OLD + ':' + path) == git('show', NEW + ':' + path)
    context.append({'path': path, 'sha256': sha(data), 'author_snapshot_fa02_d674_current_equal': True})
service = (WT / context[0]['path']).read_text()
assert 'public static final Duration TICKET_TTL = Duration.ofSeconds(30);' in service
assert 'Instant.now().plus(TICKET_TTL)' in service and 'payload, TICKET_TTL)' in service
fixture_path = 'services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/integration/AbstractIntegrationTest.java'
fixture = (WT / fixture_path).read_text()
assert 'WebEnvironment.RANDOM_PORT' in fixture and fixture.count('.withReuse(false)') == 2
assert 'postgres:16' in fixture and 'redis:7-alpine' in fixture
academic_path = 'services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/grpc/AcademicGrpcServiceImpl.java'
academic = remember(WT / academic_path, 'NP01-generic-redaction-boundary')
assert academic == git('show', OLD + ':' + academic_path) == git('show', NEW + ':' + academic_path)
assert b'.setInitialPassword("")' in academic

results = {}
timing_re = re.compile(r'WS ticket TTL remaining_ms=(\d+) issue_read_ms=(\d+) minimum_ms=(\d+)\n?')
for phase, command, session, native_exit, failures in [('red', red, 86023, 1, 1), ('green', green, 48663, 0, 0)]:
    result = read('NTR01-' + phase + '-result.json')
    xml_bytes = (SEALED / result['safe_xml']).read_bytes()
    assert sha(xml_bytes) == result['safe_xml_sha256']
    suite = ET.fromstring(xml_bytes)
    cases = suite.findall('testcase')
    assert len(cases) == int(suite.get('tests')) == 1
    assert int(suite.get('skipped')) == int(suite.get('errors')) == 0
    assert int(suite.get('failures')) == failures
    assert len(suite.findall('.//failure')) == failures
    assert not suite.findall('.//property') and not suite.findall('.//system-err')
    assert cases[0].get('name') == 'actualRedisThirtySecondExpiryRejectsUnconsumedTicket()'
    timing = suite.findtext('system-out', '')
    matched = timing_re.fullmatch(timing)
    assert matched
    remaining, elapsed, minimum = map(int, matched.groups())
    assert elapsed <= 5000 and minimum == 30000 - elapsed - 250
    if phase == 'red':
        assert (remaining, elapsed, minimum) == (993, 209, 29541)
        failure = cases[0].find('failure')
        assert failure.get('type') == 'java.lang.AssertionError'
        assert '[Redis retains the accepted 30-second issuance window]' in failure.get('message')
        assert '993L' in failure.get('message') and '[29541L, 30000L]' in failure.get('message')
        assert remaining < minimum
    else:
        assert (remaining, elapsed, minimum) == (29923, 168, 29582)
        assert minimum <= remaining <= 30000
        assert cases[0].get('time') == '31.905'
    assert result['session'] == session and result['native_exit'] == native_exit
    assert result['test_sha256'] == command['test_sha256'] and result['test_hash_matches_prelaunch']
    assert result['production_TTL_Clock_unchanged']
    results[phase] = {'session': session, 'native_exit': native_exit, 'testcases': 1,
                      'failures': failures, 'errors': 0, 'skips': 0,
                      'testcase_seconds': float(cases[0].get('time')),
                      'remaining_ms': remaining, 'elapsed_ms': elapsed, 'minimum_ms': minimum,
                      'safe_xml_sha256': sha(xml_bytes), 'test_sha256': result['test_sha256'],
                      'raw_xml_sha256_recorded': result['raw_xml_sha256'],
                      'raw_log_sha256_recorded': result['raw_log_sha256']}

attempt_bytes = remember(AUTHOR / 'attempts.ndjson', 'author-lifecycle-hash-only')
attempts = [json.loads(line) for line in attempt_bytes.decode().splitlines() if line]
relevant = [row for row in attempts if row.get('action', '').startswith('NTR01_')]
by_action = {row['action']: row for row in relevant}
projection = [{k: row[k] for k in ['time', 'action', 'phase', 'session', 'native_exit', 'status'] if k in row}
              for row in relevant]
for phase in ['red', 'green']:
    command = red if phase == 'red' else green
    pointer = by_action['NTR01_' + phase + '_process_pointer']
    terminal = by_action['NTR01_' + phase + '_terminal']
    assert pointer['session'] == terminal['session'] == results[phase]['session']
    assert terminal['native_exit'] == results[phase]['native_exit']
    assert terminal == read('NTR01-' + phase + '-result.json')
    assert time_value(command['time']) < time_value(pointer['time']) < time_value(terminal['time'])
assert time_value(by_action['NTR01_red_terminal']['time']) < time_value(restoration['time']) < time_value(green['time'])
assert by_action['NTR01_green_source_still_exact']['immediate292hashes_unchanged']
assert by_action['NTR01_terminal_cleanup_release']['immediate292green_hashes_still_exact']

log_identity = []
for phase, name in [('red', 'NTR01-control-red.log'), ('green', 'NTR01-restored-green.log')]:
    path = WT / '.agent/evidence/notification-provider' / name
    data = path.read_bytes()  # Never copy or print this private raw log.
    assert sha(data) == results[phase]['raw_log_sha256_recorded']
    marker = re.findall(rb'^BUILD (?:FAILED|SUCCESSFUL) in [0-9a-z ]+$', data, re.MULTILINE)
    assert len(marker) == 1
    assert ('FAILED' if phase == 'red' else 'SUCCESSFUL') in marker[0].decode()
    log_identity.append({'phase': phase, 'path': str(path), 'sha256': sha(data),
                         'build_marker': marker[0].decode(), 'raw_copied': False})

raw_green_path = WT / 'services/auth-service/auth-app/build/test-results/integrationTest/TEST-ru.rutcampustrack.auth.integration.WsTicketConcurrencyBoundaryIT.xml'
raw_green = raw_green_path.read_bytes()  # Private parse only; no original properties or streams exported.
assert sha(raw_green) == results['green']['raw_xml_sha256_recorded']
raw_green_root = ET.fromstring(raw_green)
assert raw_green_root.get('tests') == '1' and raw_green_root.get('failures') == '0'
assert raw_green_root.get('skipped') == raw_green_root.get('errors') == '0'
assert timing_re.findall(raw_green_root.findtext('system-out', '')) == [('29923', '168', '29582')]
startup_ids = re.findall(r'Container (?:postgres:16|redis:7-alpine) is starting: ([0-9a-f]{64})', raw_green_root.findtext('system-out', ''))
cleanup = read('NTR01-cleanup.json')
owned = cleanup['owned_green_phase_container_observations_from_original_XML']
assert len(startup_ids) == 2
assert set(startup_ids) == {row['id'] for row in owned}
assert all(row['inspect_exit'] == 1 and row['running'] is False for row in owned)
assert cleanup['owned_live_java'] == [] and cleanup['lease'] == 'NOTIFY3_RELEASED'

# Reuse the original independent review, with its exact original bytes and all original limits.
prior_zip = remember(PRIOR / 'immutable-original-receipts.zip', 'prior-original-nine-receipts')
assert sha(prior_zip) == '53a1ffbea1d68a2b2e0040cc56af388b72bda09211c6a6c114e5fde886af3214'
(OUT / 'immutable-prior-review-originals.zip').write_bytes(prior_zip)
index = json.loads(remember(PRIOR / 'original-byte-index.json', 'prior-original-byte-index'))
with zipfile.ZipFile(io.BytesIO(prior_zip)) as pz:
    assert pz.testzip() is None and len(pz.namelist()) == 9
    for entry in index['files']:
        data = pz.read(entry['path'])
        assert sha(data) == entry['original_sha256'] and len(data) == entry['original_bytes']
    prior_review = json.loads(pz.read('review.json'))
    prior_manifest = json.loads(pz.read('source-evidence-manifest.json'))
    assert prior_review['notification']['verdict'] == 'CHANGES_REQUESTED'
    assert [f['id'] for f in prior_review['notification']['findings']] == ['NTR01']
    nested_bytes = pz.read('immutable-original-review-inputs.zip')
    assert nested_bytes == (PRIOR / 'immutable-original-review-inputs.zip').read_bytes()
    with zipfile.ZipFile(io.BytesIO(nested_bytes)) as pinputs:
        assert pinputs.testzip() is None and len(pinputs.namelist()) == 98
        assert len(prior_manifest['safe_archive_inputs']) == 98
        for entry in prior_manifest['safe_archive_inputs']:
            data = pinputs.read(entry['member'])
            assert sha(data) == entry['sha256'] and len(data) == entry['bytes']
        notification_sources = [entry for entry in prior_manifest['bounded_sources']
                                if entry['original_path'].startswith(str(WT) + '/')]
        assert len(notification_sources) == 6
        for entry in notification_sources:
            relative = str(Path(entry['original_path']).relative_to(WT))
            data = pinputs.read(entry['member'])
            assert data == git('show', OLD + ':' + relative)
            if relative != TEST:
                assert data == (WT / relative).read_bytes() == git('show', NEW + ':' + relative)
        # Read exact original prior outcome counts instead of assuming its summarized verdict.
        def receipt(name):
            return json.loads(pinputs.read('receipts/notification/' + name))
        np02_before = receipt('NP02-before-actual-result.json')
        np02_green = receipt('NP02-green-result.json')
        np02_counts = receipt('NP02-result.json')
        assert np02_before['provider_attempts'] == 1 and np02_before['provider_successes'] == 0
        assert np02_before['dedup_completed'] and np02_before['silent_loss'] and np02_before['exit'] == 1
        assert np02_before['main']['ack'] == 1 and np02_before['dlq']['messages_ready'] == 0
        assert np02_green['wrapper_exit'] == np02_green['probe_exit'] == 0
        assert sha(pinputs.read('receipts/notification/NP02-result.json')) == np02_green['counts_sha256']
        np03 = receipt('NP03-result.json')
        np04 = receipt('NP04-result.json')
        socket = receipt('NP04-socket-result.json')
        python_final = receipt('NP02-NP03-final-result.json')
        np01 = receipt('NP01-result.json')
        assert np03['exit'] == 0 and np03['tests'][0]['tests'] == '4'
        assert np04['exit'] == 1 and np04['results'][0]['tests']['tests'] == '3'
        assert socket['exit'] == 0 and socket['tests']['tests'] == '1'
        assert python_final['exit'] == 0 and python_final['suites'][0]['tests'] == '9'
        assert np01['exit'] == 1 and np01['actual_failed_oracle'] == 'delivered_initial=false'
        prior_outcomes = {'NP02_before': np02_before, 'NP02_after_counts': np02_counts,
                          'NP02_after_native_exits': [np02_green['wrapper_exit'], np02_green['probe_exit']],
                          'NP03': np03, 'NP04': np04, 'NP04_socket': socket,
                          'NP02_NP03_final_python': python_final, 'NP01': np01}

write('source-equivalence.json', {
    'head': NEW, 'parent': OLD, 'clean': True,
    'diff': {'modified': [TEST], 'insertions': 20, 'deletions': 2, 'diff_check': 'PASS'},
    'final_test_sha256': FINAL_TEST_HASH, 'before_equals_fa02': True,
    'controlled_test_sha256': sha(controlled), 'controlled_only_two_temporary_test_lines': True,
    'exact_restoration': True, 'green_current_git_sources': len(dependency_rows),
    'all_other_291_red_green_fa02_d674_equal': True, 'dependencies': dependency_rows,
    'other5': sibling_rows, 'context': context, 'argv': argv,
    'execution_relation': 'Both phases executed uncommitted test WIP while HEAD=fa02; exact source bytes bridge d674. No execution-at-HEAD-d674 claim.',
    'terminal_qualification': 'Terminal test hashes exact. Mid-run and terminal cleanup record author 292-unchanged boolean attestations; full per-file terminal ledger was not separately captured. All 292 current/Git bytes independently match green prelaunch.',
    'generic_academic_rpc_redaction': 'unchanged .setInitialPassword("")',
    'generic_academic_rpc_sha256': sha(academic)})
write('oracle-evidence-audit.json', {
    'source_independent_expected_ms': 30000, 'monotonic_observation_cap_ms': 5000,
    'slack_ms': 250, 'minimum_at_cap_ms': 24750,
    'advertised_expiry_issuance_bracket': True, 'real_expiry_wait_cap_seconds': 32,
    'real_expiry_http_404_and_logout_finally': True, 'phases': results,
    'meaningful_red': 'Corrected independent Redis lifetime lower bound fails after preceding advertised expiry and observation assertions pass; no setup/syntax error or skip.',
    'green': 'Real 30s Redis expiry/key absence/HTTP404/finally logout per exact selected executed source and one-case PASS.',
    'scope_limit': 'Bounded lifetime regression oracle, not exact millisecond lifetime certification or new user latency SLA.'})
write('lifecycle-and-private-log-identity.json', {
    'attempts_sha256': sha(attempt_bytes), 'safe_lifecycle_projection': projection,
    'native_exit_provenance': 'Exact author original terminal receipts/append-only lifecycle, independently parsed; reviewer did not run or replay runtime.',
    'logs': log_identity, 'current_raw_green_xml_sha256': sha(raw_green),
    'raw_green_xml_hash_matches_record': True,
    'raw_red_xml': 'Original historical SHA retained; Gradle selected XML overwritten by green. No independent original red raw XML availability claim.',
    'raw_logs_or_properties_or_application_streams_copied': False})
write('prior-review-reuse.json', {
    'original_nine_receipt_hashes_verified': 9, 'nested_safe_input_hashes_verified': 98,
    'original_archive_sha256': sha(prior_zip), 'original_prior_notification_review': prior_review['notification'],
    'five_unchanged_scope_sources_exact': True, 'original_actual_safe_outcomes': prior_outcomes,
    'remaining_history_limits': prior_review['source_and_evidence']['limits'],
    'original_before_source_bridge': prior_review['source_and_evidence']['before_source_bridge'],
    'recovery_scope': 'Unchanged separate prior approval retained; no fresh recovery/source/runtime acceptance here.',
    'new_292_ledger_does_not_fill_old_missing_dependencies': True,
    'full_real_Auth_to_WebSocket_or_private_Telegram_chain': 'NOT_ESTABLISHED',
    'NP01': {'severity': 'P2', 'status': 'GAP', 'actual_oracle': 'delivered_initial=false',
             'dedicated_transport': 'Proposal only; not implemented or approved by this review',
             'generic_redaction': 'unchanged'}})
write('original-input-integrity.json', {
    'author_archive_sha256': sha(author_archive), 'author_archive_members': 25,
    'author_manifest_hashed_payloads': 24, 'all_original_bytes_equal': True,
    'prior_review_archive_sha256': sha(prior_zip), 'prior_archive_members': 9,
    'prior_nested_archive_members': 98, 'all_archive_CRC_checks': 'PASS',
    'prior_partial_files': partial_rows,
    'prior_partial_findings': 'APPROVE preserved; original completion/transfer metadata absent. Fresh completion belongs only to ntr_seal_review.',
    'input_hashes': input_rows})
write('cleanup-qualification.json', {
    'author_cleanup': cleanup, 'green_startup_IDs_independently_match_original_raw_XML': True,
    'green_cleanup_claim': 'Author subsequent inspect_exit1/absent for exact two original startup IDs; reviewer performs no Docker readback.',
    'red_startup_IDs': 'Not separately preserved; exact red-ID cleanup UNKNOWN',
    'reviewer_runtime_resources_created': 0, 'reviewer_java_gradle_docker_browser_mutations_children': 'NONE'})

# End-of-audit readback protects all originals and the stable reviewed source.
for row in input_rows:
    data = Path(row['path']).read_bytes()
    if row['role'] == 'governing-input':
        continue  # Root owns live governing updates; our consumed byte snapshot is preserved.
    assert len(data) == row['bytes'] and sha(data) == row['sha256'], row['path']
assert not git('status', '--porcelain')
assert git('rev-parse', 'HEAD').decode().strip() == NEW
write('verification.json', {
    'started_utc': started, 'completed_utc': datetime.now(timezone.utc).isoformat(),
    'command': 'python3 /workspace/scratch/rct-validation/ntr_seal_review/audit.py',
    'exit': 0, 'mode': 'LIGHT_READ_ONLY_SOURCE_HASH_XML_ARCHIVE_AUDIT',
    'reviewed_commit': NEW, 'independent_prelaunch_current_git_hashes': 292,
    'all_prior_partial_and_author_original_inputs_preserved': True,
    'runtime_replay': 'NOT_RUN', 'source_edits': 0, 'children': 0})
print(json.dumps({'audit': 'PASS', 'head': NEW, 'source_hashes': 292,
                  'author_originals': 25, 'prior_originals': 9, 'prior_nested_inputs': 98,
                  'red': '1 relevant assertion FAIL/native1/0skip',
                  'green': '1 PASS/native0/31.905s/0skip', 'NP01': 'P2 GAP remains'}))
