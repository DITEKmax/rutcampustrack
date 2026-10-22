"""LIGHT collector: read/hash/AST/XML/Git only; never imports or runs reviewed code."""
from pathlib import Path
from datetime import datetime, timezone
import ast
import hashlib
import json
import os
import subprocess
import xml.etree.ElementTree as ET
import zipfile

OUT = Path(__file__).resolve().parent
N = Path('/workspace/rct-worktrees/notification-provider')
R = Path('/workspace/rct-worktrees/recovery')
A = Path('/workspace/scratch/rct-validation/notification_provider_developer')
B = Path('/workspace/scratch/rct-validation/recovery_developer')
ROOT = Path('/workspace/rutcampustrack')
NC = 'fa02ce439dfb989fc28eaddcbf1a8840d16bf9ff'
NB = 'e093eb988785ed48f26c4f3a561f168ed4d96401'
RC = 'bd430a37e689a523a227ac85b7ac0c4018629efb'
RB = '232f0210ab65a6750b120e811d81f0e110b9e107'

def digest_bytes(data):
    return hashlib.sha256(data).hexdigest()

def digest(path):
    return digest_bytes(path.read_bytes())

def git(tree, *args):
    return subprocess.check_output(['git', '-C', str(tree), *args],
                                   env=dict(os.environ, GIT_OPTIONAL_LOCKS='0'))

def read_json(path):
    return json.loads(path.read_text())

def write_json(name, value):
    (OUT / name).write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n')

def normalized_ast(path):
    tree = ast.parse(path.read_text())
    imports = sorted(ast.dump(n, include_attributes=False) for n in tree.body
                     if isinstance(n, (ast.Import, ast.ImportFrom)))
    body = [ast.dump(n, include_attributes=False) for n in tree.body
            if not isinstance(n, (ast.Import, ast.ImportFrom))]
    return imports, body

archive_inputs = []
def add(path, member, category):
    if not path.is_file() or path.is_symlink():
        raise ValueError('Expected regular review input: ' + str(path))
    data = path.read_bytes()
    archive_inputs.append((path, member, category, data))
    return {'original_path': str(path), 'member': member, 'category': category,
            'bytes': len(data), 'sha256': digest_bytes(data)}

result = {
    'timestamp': datetime.now(timezone.utc).isoformat(),
    'reviewer': 'notification_recovery_review',
    'requested': 'gpt-6.1-sol/xhigh', 'actual_metadata': 'UNKNOWN',
    'method': 'LIGHT static read/hash/AST/XML/Git; no test replay, native runtime or mutation',
    'git': [], 'bounded_sources': [], 'dependency_sources': [], 'governing_inputs': [],
    'format_bridges': [], 'notification_receipts': [], 'recovery_artifacts': [],
    'xml_summaries': [], 'safe_archive_inputs': [],
}

for tree, base, commit in ((N, NB, NC), (R, RB, RC)):
    result['git'].append({'tree': str(tree), 'base': base, 'commit': commit,
                          'observed_head': git(tree, 'rev-parse', 'HEAD').decode().strip(),
                          'status_porcelain': git(tree, 'status', '--porcelain', '--untracked-files=no').decode(),
                          'diff_name_status': git(tree, 'diff', '--name-status', base, commit).decode().splitlines()})

for f in read_json(A / 'inventory.json')['files']:
    relative = f['relative_path']
    p = N / relative
    item = add(p, 'sources/notification/' + relative, 'bounded-source')
    item.update(change=f['change'], author_manifest_match=item['sha256'] == f['sha256'],
                committed_sha256=digest_bytes(git(N, 'show', NC + ':' + relative)))
    item['commit_match'] = item['sha256'] == item['committed_sha256']
    result['bounded_sources'].append(item)

p = R / 'scripts/test-recovery-failure-boundaries.py'
item = add(p, 'sources/recovery/scripts/test-recovery-failure-boundaries.py', 'bounded-source')
item.update(change='Created', committed_sha256=digest_bytes(git(R, 'show', RC + ':scripts/test-recovery-failure-boundaries.py')))
item['commit_match'] = item['sha256'] == item['committed_sha256']
result['bounded_sources'].append(item)

deps = [
    (N, NB, 'services/notification-bot/bot/consumers/event_consumer.py'),
    (N, NB, 'services/notification-bot/bot/consumers/event_dispatcher.py'),
    (N, NB, 'services/notification-bot/bot/services/idempotency_guard.py'),
    (N, NB, 'services/notification-bot/bot/services/send_queue.py'),
    (N, NB, 'services/notification-bot/bot/services/notification_prefs.py'),
    (N, NB, 'services/notification-bot/bot/services/otp_message_tracker.py'),
    (N, NB, 'services/notification-bot/bot/handlers/start.py'),
    (N, NB, 'services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/grpc/AcademicGrpcServiceImpl.java'),
    (N, NB, 'services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/service/WsTicketService.java'),
    (N, NB, 'services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/integration/AbstractIntegrationTest.java'),
    (N, NB, 'services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/integration/WsTicketIT.java'),
    (N, NB, 'services/notification-service/notification-app/src/main/java/ru/rutcampustrack/notification/config/WsSessionAdmissionOutboundInterceptor.java'),
    (N, NB, 'services/notification-service/notification-app/src/main/java/ru/rutcampustrack/notification/config/WsSessionBindingRegistry.java'),
    (N, NB, 'services/notification-service/notification-app/src/main/java/ru/rutcampustrack/notification/config/WebSocketConfig.java'),
    (R, RB, 'scripts/recovery.py'),
]
for tree, base, relative in deps:
    prefix = 'notification' if tree == N else 'recovery'
    item = add(tree / relative, 'dependencies/' + prefix + '/' + relative, 'production-or-test-dependency')
    item['frozen_sha256'] = digest_bytes(git(tree, 'show', base + ':' + relative))
    item['unchanged_from_frozen'] = item['sha256'] == item['frozen_sha256']
    result['dependency_sources'].append(item)

governing = [
    (ROOT, 'docs/validation/cloud-20261011/packet-notification-recovery-review.md'),
    (ROOT, 'docs/validation/cloud-20261011/TASK-CONTRACT.md'),
    (ROOT, 'docs/validation/cloud-20261011/CURRENT.md'),
    (ROOT, 'docs/validation/cloud-20261011/decisions.md'),
    (ROOT, 'docs/validation/cloud-20261011/questions.md'),
    (ROOT, 'docs/validation/cloud-20261011/resources.md'),
    (ROOT, 'AGENTS.md'),
    (ROOT, '.agent/orchestration-v2/RULES.md'),
    (ROOT, 'docs/agent-workflow.md'),
    (N, 'docs/research/reference-rutcampustrack-design/knowledge/job-stories.md'),
    (N, 'docs/product/decisions/2026-10-02-durable-notification-preferences.md'),
    (N, 'docs/product/decisions/2026-10-02-notification-preferences-authority.md'),
    (N, 'docs/operations/runbooks/dlq-triage.md'),
    (N, 'docs/milestones/M03b-jwt-cookie-ws-ticket/DECISIONS.md'),
    (N, 'docs/architecture/websocket-flow.md'),
    (R, 'docs/operations/runbooks/backup-restore.md'),
    (R, 'docs/product/decisions/2026-10-02-offsite-backup-deferred-to-deploy.md'),
]
for tree, relative in governing:
    prefix = 'root' if tree == ROOT else ('notification' if tree == N else 'recovery')
    result['governing_inputs'].append(add(tree / relative, 'governing/' + prefix + '/' + relative, 'governing-or-accepted-source'))

for f in read_json(A / 'receipt-manifest.json')['files']:
    p = A / f['path']
    item = {'path': str(p), 'sha256': digest(p), 'bytes': p.stat().st_size}
    item['author_manifest_match'] = item['sha256'] == f['sha256'] and item['bytes'] == f['bytes']
    result['notification_receipts'].append(item)

for group in read_json(B / 'controlled-artifacts.json')['artifacts']:
    for f in group['files']:
        p = Path(group['directory']) / f['path']
        result['recovery_artifacts'].append({'path': str(p), 'sha256': digest(p), 'bytes': p.stat().st_size,
                                            'author_ledger_match': digest(p) == f['sha256'] and p.stat().st_size == f['size']})

for name, relative in (
    ('otp_requested.py', 'bot/notifications/otp_requested.py'),
    ('otp_delivery_ack_probe.py', 'tests/harness/otp_delivery_ack_probe.py'),
    ('test_otp_requested.py', 'tests/test_otp_requested.py'),
    ('test_provider_http_boundaries.py', 'tests/test_provider_http_boundaries.py'),
):
    p = A / ('pre-format-' + name)
    q = N / 'services/notification-bot' / relative
    result['format_bridges'].append({'path': str(q), 'pre_format_sha256': digest(p), 'current_sha256': digest(q),
                                     'exact_bytes_equal': p.read_bytes() == q.read_bytes(),
                                     'AST_body_and_import_multiset_equal': normalized_ast(p) == normalized_ast(q)})
    add(p, 'receipts/notification/' + p.name, 'executed-source-snapshot')

before = A / 'NP02-before-product.py'
result['np02_before_source_bridge'] = {
    'snapshot_sha256': digest(before),
    'equals_frozen_product': before.read_bytes() == git(N, 'show', NB + ':services/notification-bot/bot/notifications/otp_requested.py'),
    'prelaunch_hash_proof': 'UNKNOWN: before actual command has HEAD/argv/resources but no product/probe hash timestamped before launch',
    'terminal': 'author original bounded counts and process exit preserved; not reviewer replay',
}

safe_notification = [
    'review-packet.md', 'inventory.json', 'tested-final-manifest.json', 'receipt-manifest.json', 'source-ledger.json',
    'CURRENT.md', 'NP01-result.json', 'NP01-before-safe.xml', 'NP01-pending-files.json',
    'NP01-safe-transport-proposal.md', 'NP02-before-product.py', 'NP02-before-probe.py',
    'NP02-attempt1-command.json', 'NP02-attempt1-result.json', 'NP02-attempt1-cleanup.json',
    'NP02-before-actual-command.json', 'NP02-before-actual-result.json', 'NP02-before-actual-cleanup.json',
    'NP02-green-command.json', 'NP02-green-result.json', 'NP02-command.json', 'NP02-result.json', 'NP02-cleanup.json',
    'NP02-unit-command.json', 'NP02-unit-result.json', 'NP02-otp-unit.xml',
    'NP03-command.json', 'NP03-result.json', 'NP03-http.xml',
    'NP04-command.json', 'NP04-result.json', 'NP04-auth-safe.xml', 'NP04-socket-before-safe.xml',
    'NP04-socket-retry-command.json', 'NP04-socket-result.json', 'NP04-socket-green-safe.xml',
    'NP02-NP03-final-command.json', 'NP02-NP03-final-result.json', 'NP02-NP03-final.xml',
    'python-format-equivalence.json', 'cleanup.json', 'run_np02.py',
    'NP01-pending-source/BotAdmissionProviderBridgeIT.java', 'NP01-pending-source/bot_admission_provider_probe.py',
]
safe_recovery = [
    'review-packet.json', 'CURRENT.md', 'source-ledger.json', 'source-equivalence.json', 'controlled-artifacts.json',
    'changed-consumers.json', 'run-sensitivity.py', 'light-run-1/run.json', 'light-final-1/run.json',
    'light-sensitivity-1/run.json', 'attempts.ndjson',
]
for directory, prefix, names in ((A, 'notification', safe_notification), (B, 'recovery', safe_recovery)):
    for name in names:
        item = add(directory / name, 'receipts/' + prefix + '/' + name, 'safe-author-receipt-or-harness')
        if name.endswith('.xml'):
            root = ET.fromstring((directory / name).read_bytes())
            suites = [root] if root.tag == 'testsuite' else list(root.findall('testsuite'))
            result['xml_summaries'].append({'path': str(directory / name), 'sha256': item['sha256'],
                                           'suites': [{'attributes': dict(s.attrib), 'testcases':
                                                       [dict(c.attrib) for c in s.findall('testcase')]} for s in suites]})

result['source_timing_limits'] = {
    'notification_final_source_current_vs_git': 'exact all six files',
    'notification_np02_green': 'prelaunch fixed product/probe/wrapper hashes preserved; current product exact, probe AST/import-order bridge independently verified',
    'notification_np03_and_np04': 'new test prelaunch hashes preserved; all production dependencies independently unchanged from frozen; full per-phase dependency hash set not captured in supplied launch receipts',
    'notification_np02_final_python': 'exact final two test prelaunch hashes and terminal zero-skip XML; not duplicate unique coverage',
    'recovery_first_control': 'test hash timestamped 22:23:25.923882 before 22:23:26.310982 launch; product current and frozen equal; terminal run receipt carries product hash',
    'recovery_final_control': 'restored terminal exit0 and matching hashes supplied; separate immediate final prelaunch hash record UNKNOWN',
    'raw_sensitive_application_log_review': 'NOT_TRANSFERRED; static suppression/test marker proof only, not full secret-free production logging acceptance',
    'reviewer_runtime': 'NOT_RUN',
}

assert len(result['bounded_sources']) == 7
assert all(x['commit_match'] for x in result['bounded_sources'])
assert all(x.get('author_manifest_match', True) for x in result['bounded_sources'])
assert all(x['unchanged_from_frozen'] for x in result['dependency_sources'])
assert all(x['author_manifest_match'] for x in result['notification_receipts'])
assert all(x['author_ledger_match'] for x in result['recovery_artifacts'])
assert all(x['AST_body_and_import_multiset_equal'] for x in result['format_bridges'])

archive = OUT / 'immutable-original-review-inputs.zip'
with zipfile.ZipFile(archive, 'x', zipfile.ZIP_DEFLATED) as z:
    seen = set()
    for path, member, category, data in archive_inputs:
        if member in seen:
            raise ValueError('Duplicate archive member: ' + member)
        seen.add(member)
        z.writestr(member, data)
        result['safe_archive_inputs'].append({'original_path': str(path), 'member': member, 'category': category,
                                              'bytes': len(data), 'sha256': digest_bytes(data)})
with zipfile.ZipFile(archive) as z:
    assert z.testzip() is None
    for item in result['safe_archive_inputs']:
        assert digest_bytes(z.read(item['member'])) == item['sha256']
result['archive'] = {'path': str(archive), 'sha256': digest(archive), 'bytes': archive.stat().st_size,
                     'members': len(result['safe_archive_inputs']), 'CRC_and_original_bytes': 'PASS',
                     'exclusions': 'no raw app XML/stdout/stderr/logs, credentials, request bodies, tool caches, native backup payloads'}
write_json('source-evidence-manifest.json', result)
print(json.dumps({'source_files': len(result['bounded_sources']), 'dependencies': len(result['dependency_sources']),
                  'notification_receipts_hashed': len(result['notification_receipts']),
                  'recovery_artifacts_hashed': len(result['recovery_artifacts']),
                  'archive_members': len(result['safe_archive_inputs']), 'all_identity_checks': 'PASS'}))
