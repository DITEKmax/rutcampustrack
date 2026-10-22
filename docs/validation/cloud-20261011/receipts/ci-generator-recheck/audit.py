"""Read-only source/receipt/hash audit. Does not import or execute reviewed product code."""

from pathlib import Path
from datetime import datetime, timezone
import ast
import copy
import difflib
import hashlib
import json
import re
import subprocess
import zipfile

OUT = Path('/workspace/scratch/rct-validation/ci_generator_recheck')
ROOT = Path('/workspace/rutcampustrack')
CI = Path('/workspace/rct-worktrees/ci-evidence')
GEN = Path('/workspace/rct-worktrees/auth-generator')
EXPORT = Path('/workspace/rct-worktrees/exports')
CIA = Path('/workspace/scratch/rct-validation/ci_evidence_developer')
GENA = Path('/workspace/scratch/rct-validation/auth_generator_developer')
CIBASE = 'b6114f4084f757d04d166ab995b57b4f4697042f'
CIFINAL = '4b0d3c2091e3c76babca8a7e7d8bdf89b9111171'
GENBASE = 'e5870376fe9ed974ba04c4c16fa534b38cc32f6b'
GENFINAL = '381543370f08c2251daa00e80c16e3d441cfe072'
GENFILE = 'frontends/mobile-core/scripts/generate-auth-types.mjs'
opened = {}


def sha(data):
    return hashlib.sha256(data).hexdigest()


def read(path):
    path = Path(path)
    data = path.read_bytes()
    opened[str(path)] = {'path': str(path), 'sha256': sha(data), 'bytes': len(data)}
    return data


def jread(path):
    return json.loads(read(path))


def git(wt, *args):
    return subprocess.check_output(['git', '-C', str(wt), *args])


def blob(wt, revision, path):
    data = git(wt, 'show', revision + ':' + path)
    opened['git:' + revision + ':' + path] = {
        'git_commit': revision, 'path': path, 'sha256': sha(data), 'bytes': len(data),
        'git_blob': git(wt, 'rev-parse', revision + ':' + path).decode().strip(),
    }
    return data


def write(name, value):
    (OUT / name).write_text(json.dumps(value, indent=2, ensure_ascii=False) + '\n')


identities = {}
for wt, revision, paths in (
    (CI, CIFINAL, ['scripts/collect-ci-test-outcomes.py', 'tests/harness/test_ci_test_outcomes.py', '.github/workflows/ci.yml']),
    (GEN, GENFINAL, [GENFILE]),
):
    head = git(wt, 'rev-parse', 'HEAD').decode().strip()
    identities[str(wt)] = {'expected_commit': revision, 'head': head, 'head_equal': head == revision,
                           'status': git(wt, 'status', '--short').decode(), 'files': []}
    for path in paths:
        current, committed = read(wt / path), blob(wt, revision, path)
        identities[str(wt)]['files'].append({'path': path, 'sha256': sha(current),
            'bytes_equal_committed': current == committed})

ci_diff = git(CI, 'diff', '--name-status', CIBASE, CIFINAL).decode().splitlines()
ci_whole = git(CI, 'diff', '--name-status', CIBASE + '^', CIFINAL).decode().splitlines()
gen_diff = git(GEN, 'diff', '--name-status', GENBASE, GENFINAL).decode().splitlines()
write('source-identities.json', {'worktrees': identities, 'ci_correction_inventory': ci_diff,
    'ci_whole_package_inventory': ci_whole, 'generator_inventory': gen_diff,
    'generator_only_assigned_file': gen_diff == ['M\t' + GENFILE]})

workflow = '.github/workflows/ci.yml'
original_workflow = blob(CI, CIBASE + '^', workflow)
initial_workflow = blob(CI, CIBASE, workflow)
current_workflow = read(CI / workflow)
removed_ranges = []
initial_lines = initial_workflow.splitlines(keepends=True)
matcher = difflib.SequenceMatcher(None, original_workflow.splitlines(keepends=True), initial_lines, autojunk=False)
only_insertions = True
for op, i1, i2, j1, j2 in matcher.get_opcodes():
    if op != 'equal':
        only_insertions = only_insertions and op == 'insert'
        removed_ranges.append((j1, j2))
rebuilt = b''.join(line for idx, line in enumerate(initial_lines)
                   if not any(begin <= idx < end for begin, end in removed_ranges))
try:
    import yaml
    old_model = yaml.safe_load(original_workflow)
    new_model = yaml.safe_load(current_workflow)
    stripped = copy.deepcopy(new_model)
    added_steps = []
    for job in ('java-unit-test', 'java-integration-test'):
        steps = stripped['jobs'][job]['steps']
        kept = []
        for step in steps:
            if step.get('name', '').startswith(('Collect safe ', 'Upload safe ')):
                added_steps.append(step['name'])
                continue
            if step.get('id') == 'java-tests':
                step.pop('id')
            kept.append(step)
        stripped['jobs'][job]['steps'] = kept
    model_equal = stripped == old_model
except ImportError:
    added_steps, model_equal = [], 'UNKNOWN_PYYAML_UNAVAILABLE'
other_workflows = {}
for path in git(CI, 'ls-tree', '-r', '--name-only', CIFINAL, '.github/workflows').decode().splitlines():
    if path != workflow:
        other_workflows[path] = blob(CI, CIBASE + '^', path) == blob(CI, CIFINAL, path)
write('workflow-preservation.json', {'correction_workflow_byte_equal_b611': current_workflow == initial_workflow,
    'initial_addition_diff_only_insertions': only_insertions, 'inserted_lines': sum(b-a for a,b in removed_ranges),
    'removing_only_insertions_yields_exact_original_bytes': rebuilt == original_workflow,
    'removing_four_named_always_steps_and_two_test_ids_yields_original_yaml_model': model_equal,
    'added_steps': added_steps, 'all_other_workflows_equal': other_workflows,
    'scope': 'Triggers/permissions/matrices/native test commands/original failure uploads/frontend gates/Deploy preserved.'})

red = jread(CIA / 'cio01-red-launch.json')
green = jread(CIA / 'cio01-green-launch.json')
red_log = read(CIA / 'cio01-red.log')
green_log = read(CIA / 'cio01-green.log')
control_tree = ast.parse(read(CI / 'tests/harness/test_ci_test_outcomes.py'))
controls = [node.name for node in ast.walk(control_tree) if isinstance(node, ast.FunctionDef) and node.name.startswith('test_')]
ast.parse(read(CI / 'scripts/collect-ci-test-outcomes.py'))
failure_labels = re.findall(r'^FAIL: (.*)$', red_log.decode(), re.M)
red_collector = blob(CI, CIBASE, 'scripts/collect-ci-test-outcomes.py')
green_matches = {path: sha(read(CI / path)) == digest for path, digest in green['source_sha256'].items()}
no_results = jread(CIA / 'local-no-results.json')
original_no_results = jread(CI / 'build/ci-test-outcomes/api-gateway/test/safe-outcomes.json')
prior = ROOT / 'docs/validation/cloud-20261011/receipts/ci-outcome-review'
prior_index = jread(prior / 'original-byte-index.json')
archive_checks = []
with zipfile.ZipFile(prior / 'immutable-original-receipts.zip') as archive:
    for item in prior_index['files']:
        member = item['path']
        if member not in archive.namelist():
            matches = [name for name in archive.namelist() if name.endswith('/' + member)]
            member = matches[0] if len(matches) == 1 else member
        data = archive.read(member)
        archive_checks.append({'path': item['path'], 'archive_equals_index': sha(data) == item['original_sha256'],
            'working_copy_equals_archive': read(prior / item['path']) == data})
read(prior / 'immutable-original-receipts.zip')
historic = jread(CIA / 'launch-provenance-clarification.json')
read(CIA / 'privacy-mutation-red.log')
read(CIA / 'control-final.log')
read(CIA / 'run-control.py')
write('ci-evidence-reconciliation.json', {
    'red': {'prelaunch': red, 'log_sha_matches': sha(red_log) == red['log_sha256'],
        'collector_sha_matches_frozen_b611': sha(red_collector) == red['source_sha256']['scripts/collect-ci-test-outcomes.py'],
        'six_actual_failure_labels': failure_labels,
        'failure_kind': 'Six substantive assertion failures; no syntax/infra failures.',
        'exact_preformat_red_control_snapshot': 'NOT_PRESERVED; prelaunch hash is recorded, source assertions are corroborated by safe log and final controls.'},
    'green': {'prelaunch': green, 'log_sha_matches': sha(green_log) == green['log_sha256'],
        'prelaunch_source_sha_matches_current_and_commit': green_matches,
        'current_control_count': len(controls), 'current_controls': controls,
        'observed': 'Ran 13 tests; OK; native exit 0.'},
    'local_no_results_original_equals_copy': no_results == original_no_results,
    'local_no_results': {'collection': no_results['collection'], 'acceptance': no_results['acceptance'],
        'summary': no_results['summary'], 'revision': no_results['metadata']['checkoutSha']},
    'prior_independent_review_immutable_receipt_checks': archive_checks,
    'historical_prelaunch_equivalence': 'UNKNOWN as explicitly recorded; no promotion from initial8/privacy-red9/final10 to exact-source runtime PASS.',
    'historical_log_digests': {name: sha(read(CIA / name)) == digest for name,digest in historic['log_hashes_POST_HOC'].items()},
    'actual_remote_CI_upload_readback': 'NOT_RUN', 'reviewer_runtime': 'NOT_RUN',
    'independent_producer_source_and_XML_freshness_attestation': 'UNKNOWN; current hashes are collection-time source identities.'})

before = read(GENA / 'before/frontends/mobile-core/src/api/generated/auth.ts').decode()
after = read(GENA / 'after/frontends/mobile-core/src/api/generated/auth.ts').decode()
base_generated = blob(GEN, GENBASE, 'frontends/mobile-core/src/api/generated/auth.ts').decode()
base_script = blob(GEN, GENBASE, GENFILE)
before_script = read(GENA / 'before/frontends/mobile-core/scripts/generate-auth-types.mjs')
after_script = read(GENA / 'after/frontends/mobile-core/scripts/generate-auth-types.mjs')
before_schema = read(GENA / 'before/docs/openapi/auth.json')
after_schema = read(GENA / 'after/docs/openapi/auth.json')
export_schema = read(EXPORT / 'docs/openapi/auth.json')
schema = json.loads(after_schema)
core, legacy_tail = after.rsplit('\nexport interface paths {', 1)
legacy_tail = 'export interface paths {' + legacy_tail
replacements = {
    '[path: `/auth/admin/users/${number}/password-reset-link`]': '"/auth/admin/users/{userId}/password-reset-link"',
    '[path: `/auth/sessions/${string}`]': '"/auth/sessions/{sessionId}"',
    '[path: `/auth/export-jobs/${string}`]': '"/auth/export-jobs/{id}"',
    '[path: `/auth/export-jobs/${string}/file`]': '"/auth/export-jobs/{id}/file"',
    '[path: `/auth/export-jobs/${string}/ticket`]': '"/auth/export-jobs/{id}/ticket"',
}
normalized = before
for dynamic, literal in replacements.items():
    normalized = normalized.replace(dynamic, literal)
members = re.compile(r'^    (\[path: `[^`]+`\]|"[^"]+"):\s*(\{.*?^    };)', re.M | re.S)
before_members = dict(members.findall(before.split('\nexport type webhooks', 1)[0]))
after_members = dict(members.findall(legacy_tail))
base_members = dict(members.findall(base_generated.split('\nexport type webhooks', 1)[0]))
alias_checks = []
for key in list(replacements)[:2]:
    b, a, original = before_members[key], after_members[key], base_members[key]
    declaration = (key + ': ' + a).rstrip()
    alias_checks.append({'key': key, 'body_byte_equal_before_vs_after': b == a,
        'body_byte_equal_original_base_vs_export_schema_vs_after': original == b == a,
        'safe_body_sha256': sha(a.encode()), 'exact_declaration_sha256': sha(declaration.encode())})
literal_keys = re.findall(r'^    "([^"]+)": \{', core.split('export interface components {', 1)[0], re.M)
components_before = before.split('export interface components {', 1)[1]
components_after = core.split('export interface components {', 1)[1]
schema_path_methods = {path: sorted(key for key in body if key.lower() in {'get','post','put','patch','delete','options','head','trace'})
                       for path,body in schema['paths'].items()}
final = jread(GENA / 'final-launch.json')
final_log = read(GENA / 'final.log')
before_launch = jread(GENA / 'before-launch.json')
before_types = jread(GENA / 'before-types-launch.json')
after_types = jread(GENA / 'after-types-launch.json')
before_types_log = read(GENA / 'before-types.log')
read(GENA / 'after-types.log')
read(GENA / 'after-launch.json')
read(GENA / 'before-generate.log')
read(GENA / 'after-generate.log')
read(GENA / 'legacy-alias-proof.json')
read(GENA / 'check-aliases.mjs')
probe = read(GENA / 'after/frontends/type-probe.ts')
probe_before = read(GENA / 'before/frontends/type-probe.ts')
write('generator-evidence-reconciliation.json', {
    'base_generator_equals_preserved_before_generator': base_script == before_script,
    'final_generator_equals_preserved_after_generator': after_script == read(GEN / GENFILE),
    'schema_before_after_current_export_byte_equal': before_schema == after_schema == export_schema,
    'schema_sha256': sha(after_schema),
    'schema_paths': schema_path_methods,
    'schema_path_set_equals_literal_generated_path_set': set(schema['paths']) == set(literal_keys),
    'literal_route_count': len(literal_keys),
    'normal_generated_core_byte_equal_except_five_path_keys': normalized.rstrip() == core.rstrip(),
    'all_components_operations_and_other_generated_declarations_byte_equal': components_before.rstrip() == components_after.rstrip(),
    'legacy_dynamic_count_exactly_two': len(after_members) == 2,
    'legacy_exact_declarations': alias_checks,
    'source_check_comparison_preserved': base_script.decode().split('if (checkOnly)',1)[1] == after_script.decode().split('if (checkOnly)',1)[1],
    'legacy_and_three_nested_route_strict_probe': {'consumer_sha_matches': sha(probe) == final['consumer_sha256'],
        'before_after_consumer_byte_equal': probe == probe_before,
        'strict_typescript_argv': final['argv_sequence'][2],
        'before_generated_sha_matches_receipt': sha(before.encode()) == before_types['generated_sha256'],
        'after_generated_sha_matches_receipt': sha(after.encode()) == after_types['generated_sha256'],
        'before_native_exit': before_types['native_exit'], 'before_ts2413_count': before_types_log.count(b'error TS2413'),
        'before_extra_probe_diagnostic': 'TS2339 expected for new canonical legacy literal alias absent before correction.',
        'after_native_exit': after_types['native_exit']},
    'final_actual_receipt': final,
    'final_prelaunch_hashes_match': {'generator': sha(after_script) == final['generator_sha256'],
        'schema': sha(after_schema) == final['schema_sha256'], 'consumer': sha(probe) == final['consumer_sha256']},
    'final_log': {'sha256': sha(final_log), 'observed': 'Normal generation and source-exact --check messages; consumer silent success.'},
    'generated_types_schema_schedule_locks_configs_changed_in_generator_commit': [],
    'reviewer_runtime': 'NOT_RUN; no generator/typecheck replay',
    'acceptance_limit': 'Source correction and preserved author scratch strict proof only. Export author owns tracked generated consumers and full workspace checks.'})

safe_sources = [
    ROOT / 'docs/validation/cloud-20261011/packet-ci-generator-recheck.md',
    ROOT / 'docs/validation/cloud-20261011/TASK-CONTRACT.md',
    ROOT / 'docs/validation/cloud-20261011/CURRENT.md',
    ROOT / 'docs/validation/cloud-20261011/decisions.md',
    ROOT / 'docs/validation/cloud-20261011/questions.md',
    ROOT / 'docs/validation/cloud-20261011/resources.md',
    CIA / 'cio01-source-ready.json', CIA / 'cio01-source-ready.md',
    CIA / 'cio01-stable-equivalence.json', GENA / 'source-ready.json', GENA / 'source-ready.md',
    GENA / 'stable-equivalence.json', EXPORT / 'docs/openapi/schedule.json',
    GEN / 'frontends/package.json', GEN / 'frontends/package-lock.json',
]
for path in safe_sources:
    read(path)
write('opened-sources.json', {'at': datetime.now(timezone.utc).isoformat(),
    'mechanism': 'Local source/receipt reads, stdlib AST/JSON/regex/hash and optional YAML parser; git read-only commands.',
    'files': list(opened.values())})
print(json.dumps({'source_heads_equal': all(v['head_equal'] for v in identities.values()),
    'workflow_exact': current_workflow == initial_workflow and model_equal,
    'ci_green_source_sha_matches': all(green_matches.values()), 'red_failures': len(failure_labels),
    'generator_core_except_keys_equal': normalized.rstrip() == core.rstrip(),
    'schema_path_set_equal': set(schema['paths']) == set(literal_keys),
    'legacy_declarations_equal': all(v['body_byte_equal_original_base_vs_export_schema_vs_after'] for v in alias_checks),
    'outputs': str(OUT)}))
