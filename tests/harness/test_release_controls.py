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
        newer = dict(run(number=2), conclusion='failure')
        with self.assertRaises(gate.GateFailure):
            gate.assess('ci.yml', [run(), newer], lambda _: jobs(), SHA, REPOSITORY)

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
    def test_counterfeit_candidate_helper_cannot_approve_initial_or_final_gate(self):
        workflow = (ROOT / '.github/workflows/deploy.yml').read_text()
        release = workflow.split('  release-gate:\n', 1)[1].split('  build-push:\n', 1)[0]
        deploy = workflow.split('  deploy:\n', 1)[1]
        initial = release.split('      - name: Bound same-SHA quality wait', 1)[1]
        initial = textwrap.dedent(initial.split('        run: |\n', 1)[1].split('      - name:', 1)[0])
        final = deploy.split('      - name: Recheck current main and same-SHA checks', 1)[1]
        final = final.split('        run: ', 1)[1].split('\n', 1)[0]
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
        build = workflow.split('  build-push:\n', 1)[1].split('  scan-sign:\n', 1)[0]
        scan = workflow.split('  scan-sign:\n', 1)[1].split('  deploy:\n', 1)[0]
        deploy = workflow.split('  deploy:\n', 1)[1]
        guard = textwrap.dedent(release.split('        run: |\n', 1)[1].split('      - uses:', 1)[0])
        expression = ' '.join(deploy.split('    if: >-\n', 1)[1].split('    runs-on:', 1)[0].split())
        for prepare, enabled, ref, expected in ((True, 'false', 'refs/heads/main', 0),
                                                (False, 'false', 'refs/heads/main', 1),
                                                (False, 'true', 'refs/heads/main', 0),
                                                (True, 'false', 'refs/heads/feature', 1)):
            with self.subTest(prepare=prepare, enabled=enabled, ref=ref):
                checked = subprocess.run(['bash', '-e', '-c', guard], env=dict(os.environ,
                    PREPARE_ONLY=str(prepare).lower(), PRODUCTION_DEPLOY_ENABLED=enabled,
                    DEPLOY_SHA=SHA, GITHUB_REF=ref), capture_output=True, timeout=5)
                self.assertEqual(checked.returncode, expected)
                policy = (expression.replace('vars.PRODUCTION_DEPLOY_ENABLED', repr(enabled))
                          .replace('github.event_name', repr('workflow_dispatch'))
                          .replace('inputs.prepare_only', repr(prepare))
                          .replace('false', 'False').replace('true', 'True')
                          .replace('&&', ' and ').replace('||', ' or '))
                self.assertEqual(bool(eval(policy, {'__builtins__': {}}, {})), not prepare and enabled == 'true')
        needs = re.search(r'    needs: \[([^\]]+)\]', deploy)[1]
        self.assertEqual({value.strip() for value in needs.split(',')}, {'release-gate', 'build-push', 'scan-sign'})
        self.assertNotIn('always()', expression, 'a failed scan must retain default success() dependency gating')
        self.assertIn('      fail-fast: false', scan)
        inventory = re.search(r'        image: \[([^\]]+)\]', scan)[1]
        self.assertEqual({value.strip() for value in inventory.split(',')}, set(images.IMAGES))
        blocking = scan.split('      - name: Blocking release image scan\n', 1)[1].split('      - name:', 1)[0]
        self.assertIn("          exit-code: '1'", blocking)
        digest_expression = '${{ fromJSON(needs.build-push.outputs.manifest).images[matrix.image] }}'
        self.assertIn('          image-ref: ' + digest_expression, blocking)
        self.assertIn('          IMAGE_REF: ' + digest_expression, scan)
        # Preserve weekly :latest consumers without treating those tags as release proof.
        tags = re.findall(r'          tags: (.+)', build)
        self.assertEqual(len(tags), len(images.IMAGES))
        for name in images.IMAGES:
            self.assertIn(images.PREFIX + name + ':${{ env.DEPLOY_SHA }},' + images.PREFIX + name + ':latest', tags)
        self.assertNotIn('continue-on-error', blocking)
        self.assertNotIn('continue-on-error', scan)
        # Production inputs exist only in the apply job, not in any preparation job.
        for definition in (release, build, scan):
            for secret in ('PROD_ENV', 'PROD_SECRET_FILES_B64', 'SSH_PRIVATE_KEY', 'INITIAL_ADMIN_REQUEST', 'VPS_HOST'):
                self.assertNotIn('secrets.' + secret, definition)


if __name__ == '__main__':
    unittest.main()
