"""Meaningful pure release counterexamples; no network, Docker or real inputs."""
import copy
import importlib.util
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile
import textwrap
import unittest

ROOT = Path(__file__).resolve().parents[2]
SHA = 'a' * 40
REPOSITORY = 'ditekmax/rutcampustrack'


def load(name):
    spec = importlib.util.spec_from_file_location(name.replace('-', '_'), ROOT / 'scripts' / (name + '.py'))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


gate = load('release-quality-gate')
images = load('release-images')


def manifest():
    return {'source_sha': SHA, 'images': {name: images.PREFIX + name + '@sha256:' + 'b' * 64 for name in images.IMAGES}}


def run(workflow='ci.yml', number=1):
    return {'id': number, 'run_number': number, 'run_attempt': 1, 'head_sha': SHA,
            'head_repository': {'full_name': REPOSITORY}, 'path': '.github/workflows/' + workflow,
            'event': 'workflow_dispatch', 'status': 'completed', 'conclusion': 'success'}


def jobs(workflow='ci.yml'):
    return [{'name': name, 'head_sha': SHA, 'status': 'completed', 'conclusion': 'success'}
            for name in gate.REQUIRED[workflow]]


def condition(definition):
    return definition.split('    if: >-\n', 1)[1].split('    runs-on:', 1)[0]


def check_policy(expression, fields):
    # Evaluate the operators used by the workflow guard with synthetic event contexts.
    expression = ' '.join(expression.strip().removeprefix('${{').removesuffix('}}').split())
    values = {'true': True, 'false': False, **fields}
    expression = re.sub(r"(?<!')(?:\b(?:github|inputs|vars)(?:\.[A-Za-z_]+)+|\btrue\b|\bfalse\b)(?!')",
                        lambda match: repr(values.get(match[0], '')), expression)
    return eval(expression.replace('&&', ' and ').replace('||', ' or '),
                {'__builtins__': {}, 'format': lambda template, value: template.format(value)}, {})


class QualityCounterexamples(unittest.TestCase):
    def test_complete_inventory_success_and_academic_omission_rejected(self):
        self.assertEqual([len(value) for value in gate.REQUIRED.values()], [25, 9, 3, 1])
        for workflow in gate.REQUIRED:
            self.assertEqual(gate.assess(workflow, [run(workflow)], lambda _: jobs(workflow), SHA,
                                         REPOSITORY)['conclusion'], 'success')
        for workflow in ('ci.yml', 'coverage.yml'):
            omitted = [job for job in jobs(workflow) if 'academic-service' not in job['name']]
            with self.assertRaises(gate.GateFailure):
                gate.assess(workflow, [run(workflow)], lambda _: omitted, SHA, REPOSITORY)

    def test_newer_failed_run_never_falls_back_to_green(self):
        for workflow in gate.REQUIRED:
            newer = dict(run(workflow, number=2), conclusion='failure')
            with self.subTest(workflow=workflow), self.assertRaises(gate.GateFailure):
                gate.assess(workflow, [run(workflow), newer], lambda _: jobs(workflow), SHA, REPOSITORY)

    def test_wrong_source_repository_event_workflow_and_unknown_jobs_rejected(self):
        for field in ({'head_sha': 'c' * 40}, {'head_repository': {'full_name': 'fork/repo'}},
                      {'path': '.github/workflows/imposter.yml'}, {'event': 'schedule'}):
            with self.subTest(field=field), self.assertRaises(gate.GateFailure):
                gate.assess('ci.yml', [dict(run(), **field)], lambda _: jobs(), SHA, REPOSITORY)
        for mutation in ('skipped', 'cancelled', 'timed_out', None, 'neutral'):
            altered = jobs()
            altered[0]['conclusion'] = mutation
            with self.subTest(conclusion=mutation), self.assertRaises(gate.GateFailure):
                gate.assess('ci.yml', [run()], lambda _: altered, SHA, REPOSITORY)
        for bad in ([*jobs(), dict(jobs()[0], name='unknown')], [*jobs(), jobs()[0]],
                    [dict(job, head_sha='d' * 40) for job in jobs()]):
            with self.assertRaises(gate.GateFailure):
                gate.assess('ci.yml', [run()], lambda _: bad, SHA, REPOSITORY)

    def test_absent_pending_and_failed_checks_never_pass(self):
        with self.assertRaises(gate.Pending):
            gate.assess('ci.yml', [], lambda _: jobs(), SHA, REPOSITORY)
        with self.assertRaises(gate.Pending):
            gate.assess('ci.yml', [dict(run(), status='in_progress')], lambda _: jobs(), SHA, REPOSITORY)
        for enabled, main in (('false', SHA), ('true', 'f' * 40), ('unknown', SHA)):
            with self.assertRaises(gate.GateFailure):
                gate.require_apply(SHA, main, enabled)
        gate.require_apply(SHA, SHA, 'true')

    def test_pr_diff_coverage_requires_success_and_api_path_optional_ref_is_normalized(self):
        candidate = dict(run('coverage.yml'), event='pull_request', path='.github/workflows/coverage.yml@refs/pull/3/merge')
        successful = jobs('coverage.yml') + [{'name': 'Diff coverage report', 'head_sha': SHA,
                                             'status': 'completed', 'conclusion': 'success'}]
        gate.assess('coverage.yml', [candidate], lambda _: successful, SHA, REPOSITORY)
        for altered in (jobs('coverage.yml'), [dict(job, conclusion='skipped') if job['name'] == 'Diff coverage report' else job for job in successful]):
            with self.assertRaises(gate.GateFailure):
                gate.assess('coverage.yml', [candidate], lambda _: altered, SHA, REPOSITORY)
        with self.assertRaises(gate.GateFailure):
            gate.assess('coverage.yml', [dict(candidate, path='.github/workflows/coverage.yml@main@extra')],
                        lambda _: successful, SHA, REPOSITORY)


class ImageCounterexamples(unittest.TestCase):
    def test_inventory_registry_digest_source_and_pulled_content_are_bound(self):
        value = manifest()
        images.validate(value, SHA)
        images.verify_local(value, SHA, lambda ref: {'RepoDigests': [ref.rsplit(':', 1)[0] + '@sha256:' + 'b' * 64]})
        variants = []
        wrong = copy.deepcopy(value); wrong['images'].pop('mobile-bff'); variants.append(wrong)
        wrong = copy.deepcopy(value); wrong['source_sha'] = 'f' * 40; variants.append(wrong)
        for reference in ('ghcr.io/other/api-gateway@sha256:' + 'b' * 64,
                          images.PREFIX + 'api-gateway:latest',
                          images.PREFIX + 'api-gateway@sha256:bad'):
            wrong = copy.deepcopy(value); wrong['images']['api-gateway'] = reference; variants.append(wrong)
        for wrong in variants:
            with self.assertRaises(ValueError): images.validate(wrong, SHA)
        with self.assertRaises(ValueError):
            images.verify_local(value, SHA, lambda _: {'RepoDigests': ['sha256:' + 'f' * 64]})

    def test_bad_manifest_fails_before_private_materialization_or_ssh(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / 'scripts').mkdir()
            for name in ('deliver-release.sh', 'release-images.py'):
                (root / 'scripts' / name).write_bytes((ROOT / 'scripts' / name).read_bytes())
            (root / 'release-images.json').write_text(json.dumps(dict(manifest(), source_sha='f' * 40)))
            bindir = root / 'bin'; bindir.mkdir()
            for name, command in (('git', 'echo "$DEPLOY_SHA"'), ('ssh', 'touch ssh-called; exit 99'),
                                  ('mktemp', 'touch materialized-private; exit 99')):
                path = bindir / name; path.write_text('#!/bin/bash\n' + command + '\n'); path.chmod(0o700)
            result = subprocess.run(['bash', 'scripts/deliver-release.sh'], cwd=root,
                env=dict(os.environ, PATH=str(bindir) + os.pathsep + os.environ['PATH'], DEPLOY_SHA=SHA,
                         VPS_HOST='synthetic.invalid', VPS_USER='synthetic', GITHUB_RUN_ID='1',
                         GITHUB_RUN_ATTEMPT='1', BOOTSTRAP_INITIAL_ADMIN='false'), capture_output=True, timeout=10)
            self.assertNotEqual(result.returncode, 0)
            self.assertFalse((root / 'ssh-called').exists())
            self.assertFalse((root / 'materialized-private').exists())


class WorkflowPrivilegeBoundaries(unittest.TestCase):
    def test_actual_delivery_keeps_public_mounts_readable_and_private_inputs_private(self):
        self.assertNotEqual(os.geteuid(), 0, 'Delivery permission control requires a non-root user')
        with tempfile.TemporaryDirectory(prefix='rct-delivery-modes-') as tmp:
            root = Path(tmp)
            repository, bindir, remote = root / 'repository', root / 'bin', root / 'remote'
            repository.mkdir(); bindir.mkdir()
            scripts = repository / 'scripts'; scripts.mkdir()
            for name in ('deliver-release.sh', 'release-images.py'):
                (scripts / name).write_bytes((ROOT / 'scripts' / name).read_bytes())
            # Only the unrelated private-input validator and service entrypoint are synthetic.
            # The entire real delivery script, Git archive, SSH payload and tar still execute.
            (scripts / 'deployment-secrets.py').write_text('raise SystemExit(0)\n')
            (scripts / 'deploy-release.sh').write_text('''#!/usr/bin/env bash
set -eu
python3 - "$2" <<'PY'
import json, os, stat, sys
from pathlib import Path
incoming = Path(sys.argv[1])
Path('.env.prod').write_text('synthetic-only')
Path('private-after-directory').mkdir()
mode = lambda path: stat.S_IMODE(path.stat().st_mode)
public = {name: mode(Path(name)) for name in ('infra', 'infra/loki', 'infra/loki/loki.yml', 'public-tool')}
private = {name: mode(incoming / name) for name in ('PROD_ENV', 'PROD_SECRET_FILES_B64', 'GHCR_TOKEN')}
Path(os.environ['RCT_TEST_RECEIPT']).write_text(json.dumps({'public': public, 'private': private,
    'release': mode(Path('.')), 'incoming': mode(incoming), 'privateAfter': mode(Path('.env.prod')),
    'directoryAfter': mode(Path('private-after-directory'))}))
PY
''')
            (repository / 'infra/loki').mkdir(parents=True)
            (repository / 'infra/loki/loki.yml').write_text('auth_enabled: false\n')
            executable = repository / 'public-tool'
            executable.write_text('#!/bin/sh\nexit 0\n'); executable.chmod(0o755)
            for command in (['git', 'init', '-q'], ['git', 'add', '.'],
                            ['git', '-c', 'user.name=Control', '-c', 'user.email=control@example.invalid',
                             'commit', '-qm', 'Synthetic public source']):
                subprocess.run(command, cwd=repository, check=True, capture_output=True, timeout=10)
            sha = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=repository, text=True).strip()
            (repository / 'release-images.json').write_text(json.dumps(dict(manifest(), source_sha=sha)))
            (bindir / 'ssh').write_text('''#!/usr/bin/python3
import os, subprocess, sys
# Redirect only the fixed deployment root into this newly owned POSIX fixture.
command = sys.argv[-1].replace('/opt/rutcampustrack', os.environ['RCT_TEST_REMOTE'])
raise SystemExit(subprocess.run(['bash', '-e', '-c', command]).returncode)
''')
            (bindir / 'scp').write_text('''#!/usr/bin/python3
import os, shutil, sys
from pathlib import Path
args = sys.argv[1:]
while args[0].startswith('-'):
    args = args[2:]  # The real sender supplies only -i and -o option/value pairs.
destination = Path(args[-1].split(':', 1)[1].replace('/opt/rutcampustrack', os.environ['RCT_TEST_REMOTE']))
for source in args[:-1]:
    shutil.copyfile(source, destination / Path(source).name)
''')
            for tool in ('ssh', 'scp'):
                (bindir / tool).chmod(0o700)
            receipt = root / 'receipt.json'
            environment = dict(os.environ, PATH=str(bindir) + os.pathsep + os.environ['PATH'],
                DEPLOY_SHA=sha, VPS_HOST='synthetic.invalid', VPS_USER='synthetic', GITHUB_RUN_ID='1',
                GITHUB_RUN_ATTEMPT='1', BOOTSTRAP_INITIAL_ADMIN='false', RCT_TEST_REMOTE=str(remote),
                RCT_TEST_RECEIPT=str(receipt), RELEASE_IMAGE_MANIFEST=str(repository / 'release-images.json'))
            environment.update({name: 'synthetic-only' for name in
                ('PROD_ENV', 'PROD_SECRET_FILES_B64', 'SSH_KNOWN_HOSTS', 'SSH_PRIVATE_KEY', 'GHCR_TOKEN')})
            result = subprocess.run(['bash', 'scripts/deliver-release.sh'], cwd=repository,
                                    env=environment, capture_output=True, timeout=20)
            self.assertEqual(result.returncode, 0, result.stderr.decode())
            observed = json.loads(receipt.read_text())
            self.assertEqual(observed['public'], {'infra': 0o755, 'infra/loki': 0o755,
                                                  'infra/loki/loki.yml': 0o644, 'public-tool': 0o755})
            self.assertEqual(set(observed['private'].values()), {0o600})
            self.assertEqual((observed['release'], observed['incoming'], observed['directoryAfter']),
                             (0o700, 0o700, 0o700))
            self.assertEqual(observed['privateAfter'], 0o600)

    def test_counterfeit_candidate_helper_cannot_approve_initial_or_final_gate(self):
        workflow = (ROOT / '.github/workflows/deploy.yml').read_text()
        release = workflow.split('  release-gate:\n', 1)[1].split('  build-push:\n', 1)[0]
        deploy = workflow.split('  deploy:\n', 1)[1]
        initial = release.split('      - name: Bound same-SHA quality wait', 1)[1]
        initial = textwrap.dedent(initial.split('        run: |\n', 1)[1].split('      - name:', 1)[0])
        final = deploy.split('      - name: Recheck current main and same-SHA checks', 1)[1]
        final = textwrap.dedent(final.split('        run: |\n', 1)[1].split('      - name:', 1)[0])
        for definition, command in ((release, initial), (deploy, final)):
            with self.subTest(command=command), tempfile.TemporaryDirectory() as tmp:
                root = Path(tmp)
                # Materialize only each job's declared checkouts. Workflow SHA gets the real
                # control; candidate SHA gets an executable forged approval with a marker.
                for block in re.findall(r'      - uses: actions/checkout@[^\n]+\n        with:\n(.*?)(?=      -|\Z)', definition, re.S):
                    revision = re.search(r'          ref: \$\{\{ (.+?) \}\}', block)[1]
                    path = re.search(r'          path: ([^\n]+)', block)
                    scripts = root / (path[1] if path else '.') / 'scripts'
                    scripts.mkdir(parents=True, exist_ok=True)
                    helper = scripts / 'release-quality-gate.py'
                    if revision == 'github.workflow_sha':
                        helper.write_bytes((ROOT / 'scripts/release-quality-gate.py').read_bytes())
                    else:
                        helper.write_text("import os\nfrom pathlib import Path\nPath(os.environ['COUNTERFEIT_MARKER']).touch()\nraise SystemExit(0)\n")
                bindir = root / 'bin'; bindir.mkdir()
                driver = bindir / 'python3'
                driver.write_text('''#!/usr/bin/python3
import importlib.util, pathlib, sys
args = sys.argv[1:]
if args[0] == '-I': args = args[1:]
target = pathlib.Path(args.pop(0)).resolve()
spec = importlib.util.spec_from_file_location('actual_quality_control', target)
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)
class ReadOnlyAPI:
    def get(self, path): return {'object': {'sha': 'f' * 40}}
    def pages(self, path, key): return []
module.GitHub = lambda *args: ReadOnlyAPI()
sys.argv = [str(target)] + args
try:
    module.main()
except module.GateFailure:
    raise SystemExit(42)
''')
                driver.chmod(0o700)
                result = subprocess.run(['bash', '-e', '-c', command], cwd=root,
                    env=dict(os.environ, PATH=str(bindir) + os.pathsep + os.environ['PATH'],
                             DEPLOY_SHA=SHA, GITHUB_REPOSITORY=REPOSITORY, GH_TOKEN='synthetic',
                             PREPARE_ONLY='false', PRODUCTION_DEPLOY_ENABLED='true',
                             COUNTERFEIT_MARKER=str(root / 'counterfeit-executed')),
                    capture_output=True, timeout=10)
                self.assertEqual(result.returncode, 42, result.stderr.decode())
                self.assertFalse((root / 'counterfeit-executed').exists())

    def test_prepare_and_disabled_apply_cannot_reach_production_delivery(self):
        # Read the existing fixed policy blocks, like the inherited private-input controls.
        # No parser package or runner-global Python dependency is needed by this control.
        workflow = (ROOT / '.github/workflows/deploy.yml').read_text()
        release = workflow.split('  release-gate:\n', 1)[1].split('  build-push:\n', 1)[0]
        build = workflow.split('  build-push:\n', 1)[1].split('  deploy:\n', 1)[0]
        deploy = workflow.split('  deploy:\n', 1)[1]
        guard = textwrap.dedent(release.split('        run: |\n', 1)[1].split('      - uses:', 1)[0])
        expression = condition(deploy)
        for prepare, enabled, ref, expected in ((True, 'false', 'refs/heads/main', 0),
                                                (False, 'false', 'refs/heads/main', 1),
                                                (False, 'true', 'refs/heads/main', 0),
                                                (True, 'false', 'refs/heads/feature', 1)):
            with self.subTest(prepare=prepare, enabled=enabled, ref=ref):
                checked = subprocess.run(['bash', '-e', '-c', guard], env=dict(os.environ,
                    PREPARE_ONLY=str(prepare).lower(), PRODUCTION_DEPLOY_ENABLED=enabled,
                    DEPLOY_SHA=SHA, GITHUB_REF=ref), capture_output=True, timeout=5)
                self.assertEqual(checked.returncode, expected)
                self.assertEqual(bool(check_policy(expression, {
                    'github.event_name': 'workflow_dispatch',
                    'inputs.prepare_only': prepare,
                    'vars.PRODUCTION_DEPLOY_ENABLED': enabled,
                })), not prepare and enabled == 'true')
        self.assertIn('    needs: release-gate\n', build)
        needs = re.search(r'    needs: \[([^\]]+)\]', deploy)[1]
        self.assertEqual({value.strip() for value in needs.split(',')}, {'release-gate', 'build-push'})
        self.assertNotIn('always()', expression, 'failed quality or build jobs must retain default success() dependency gating')
        # Owner decision 2026-11-03 removes the entire Deploy scan/sign/SBOM stage.
        for removed in ('  scan-sign:', 'aquasecurity/trivy-action@', 'anchore/sbom-action@', 'cosign'):
            self.assertNotIn(removed, workflow)
        # Preserve weekly :latest consumers without treating those tags as release proof.
        tags = re.findall(r'          tags: (.+)', build)
        self.assertEqual(len(tags), len(images.IMAGES))
        for name in images.IMAGES:
            self.assertIn(images.PREFIX + name + ':${{ env.DEPLOY_SHA }},' + images.PREFIX + name + ':latest', tags)
        # Production inputs exist only in the apply job, not in any preparation job.
        for definition in (release, build):
            for secret in ('PROD_ENV', 'PROD_SECRET_FILES_B64', 'SSH_PRIVATE_KEY', 'INITIAL_ADMIN_REQUEST', 'VPS_HOST'):
                self.assertNotIn('secrets.' + secret, definition)


    def test_auto_deploy_uses_only_successful_main_push_in_same_repository(self):
        workflow = (ROOT / '.github/workflows/deploy.yml').read_text()
        self.assertIn('  workflow_run:\n    workflows: [CI]\n    types: [completed]\n    branches: [main]\n', workflow)
        concurrency = workflow.split('  group: >-\n', 1)[1].split('  cancel-in-progress: false', 1)[0]
        concurrency_policy = workflow.split('\nconcurrency:\n', 1)[1].split('\nenv:', 1)[0]
        self.assertRegex(concurrency_policy, r'(?m)^  queue: max$',
                         'a late CI result for an older SHA must not replace a pending release')
        revision = workflow.split('  DEPLOY_SHA: >-\n', 1)[1].split('\njobs:', 1)[0]
        release = workflow.split('  release-gate:\n', 1)[1].split('  build-push:\n', 1)[0]
        deploy = workflow.split('  deploy:\n', 1)[1]
        eligible = {
            'github.event_name': 'workflow_run',
            'github.ref': 'refs/heads/main',
            'github.repository': REPOSITORY,
            'github.run_id': 123,
            'github.sha': 'b' * 40,
            'github.event.workflow_run.conclusion': 'success',
            'github.event.workflow_run.head_branch': 'main',
            'github.event.workflow_run.event': 'push',
            'github.event.workflow_run.head_repository.full_name': REPOSITORY,
            'github.event.workflow_run.head_sha': SHA,
            'vars.PRODUCTION_AUTO_DEPLOY_ENABLED': 'true',
            'vars.PRODUCTION_DEPLOY_ENABLED': 'true',
            'vars.PRODUCTION_BOOTSTRAP_INITIAL_ADMIN': 'true',
            'inputs.commit_sha': 'c' * 40,
            'inputs.prepare_only': True,
        }
        self.assertEqual(check_policy(concurrency, eligible), 'production-deploy')
        self.assertTrue(check_policy(condition(release), eligible))
        self.assertTrue(check_policy(condition(deploy), eligible))
        self.assertEqual(check_policy(revision, eligible), SHA)
        for prepare in re.findall(r'PREPARE_ONLY: (.+)', release):
            self.assertEqual(check_policy(prepare, eligible), 'false')
        bootstrap = re.search(r'RAW_BOOTSTRAP_INITIAL_ADMIN: (.+)', deploy)[1]
        self.assertEqual(check_policy(bootstrap, eligible), 'false')
        for name in ('BOOTSTRAP_INITIAL_ADMIN', 'INITIAL_ADMIN_REQUEST'):
            delivery_input = re.search(r'^          ' + name + r': (.+)', deploy, re.M)[1]
            self.assertIn("github.event_name == 'workflow_dispatch' &&", delivery_input)

        for field, value in (
            ('github.event_name', 'push'),
            ('github.event.workflow_run.conclusion', 'failure'),
            ('github.event.workflow_run.conclusion', 'cancelled'),
            ('github.event.workflow_run.conclusion', 'skipped'),
            ('github.event.workflow_run.head_branch', 'dev'),
            ('github.event.workflow_run.event', 'pull_request'),
            ('github.event.workflow_run.event', 'workflow_dispatch'),
            ('github.event.workflow_run.head_repository.full_name', 'fork/rutcampustrack'),
            ('vars.PRODUCTION_AUTO_DEPLOY_ENABLED', ''),
            ('vars.PRODUCTION_AUTO_DEPLOY_ENABLED', 'false'),
            ('vars.PRODUCTION_DEPLOY_ENABLED', 'false'),
        ):
            with self.subTest(field=field, value=value):
                ineligible = {**eligible, field: value}
                self.assertFalse(check_policy(condition(release), ineligible))
                self.assertEqual(check_policy(concurrency, ineligible), 'production-deploy-ineligible-123')

        self.assertEqual(check_policy(revision, {**eligible, 'github.event.workflow_run.head_sha': ''}), '')
        manual = {**eligible, 'github.event_name': 'workflow_dispatch',
                  'vars.PRODUCTION_AUTO_DEPLOY_ENABLED': '', 'inputs.prepare_only': False}
        self.assertEqual(check_policy(concurrency, manual), 'production-deploy')
        self.assertTrue(check_policy(condition(release), manual))
        self.assertTrue(check_policy(condition(deploy), manual))
        self.assertEqual(check_policy(revision, manual), manual['inputs.commit_sha'])
        self.assertEqual(check_policy(revision, {**manual, 'inputs.commit_sha': ''}), '')


if __name__ == '__main__':
    unittest.main()
