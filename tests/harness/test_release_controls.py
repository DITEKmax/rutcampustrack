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
    def test_delivery_bootstrap_accepts_only_the_two_supported_workflow_events(self):
        source = (ROOT / 'scripts/deliver-release.sh').read_text()
        guard = source.split('[[ "${DEPLOY_SHA:-}"', 1)[0]
        for event, expected in (('workflow_dispatch', 0), ('workflow_run', 0),
                                ('pull_request', 2), ('schedule', 2), ('', 2)):
            with self.subTest(event=event):
                result = subprocess.run(['bash', '-c', guard], cwd=ROOT, capture_output=True, timeout=5,
                    env=dict(os.environ, BOOTSTRAP_INITIAL_ADMIN='true', GITHUB_EVENT_NAME=event))
                self.assertEqual(result.returncode, expected)

    def test_bootstrap_request_and_boolean_share_explicit_manual_or_trusted_auto_guard(self):
        workflow = (ROOT / '.github/workflows/deploy.yml').read_text()
        boolean = re.search(r'BOOTSTRAP_INITIAL_ADMIN: \$\{\{ (.+?) \}\}', workflow)[1]
        request = re.search(r'INITIAL_ADMIN_REQUEST: \$\{\{ (.+?) \}\}', workflow)[1]
        guard = boolean.removesuffix(" && 'true' || 'false'")
        self.assertEqual(request, guard + " && secrets.INITIAL_ADMIN_REQUEST || ''")
        fields = {'github.event_name': 'event', 'vars.PRODUCTION_BOOTSTRAP_INITIAL_ADMIN': 'flag',
                  'github.event.workflow_run.conclusion': 'conclusion',
                  'github.event.workflow_run.head_branch': 'branch',
                  'github.event.workflow_run.event': 'source_event',
                  'github.event.workflow_run.head_repository.full_name': 'source_repository',
                  'github.repository': 'repository'}
        policy = guard.replace('inputs.bootstrap_initial_admin == true', 'manual is True')
        for key, value in fields.items():
            policy = policy.replace(key, value)
        policy = policy.replace('&&', ' and ').replace('||', ' or ')
        base = dict(event='workflow_run', flag='true', manual=False, conclusion='success',
                    branch='main', source_event='push', source_repository=REPOSITORY, repository=REPOSITORY)
        cases = [({}, True), ({'event': 'workflow_dispatch', 'manual': True, 'flag': ''}, True),
                 ({'event': 'workflow_dispatch'}, False), ({'flag': 'false'}, False),
                 ({'flag': ''}, False), ({'flag': 'TRUE'}, False), ({'event': 'schedule'}, False),
                 ({'event': 'pull_request'}, False), ({'conclusion': 'failure'}, False),
                 ({'branch': 'feature'}, False), ({'source_event': 'pull_request'}, False),
                 ({'source_repository': 'fork/repository'}, False)]
        for changes, expected in cases:
            with self.subTest(changes=changes):
                self.assertEqual(bool(eval(policy, {'__builtins__': {}}, dict(base, **changes))), expected)

    def test_actual_deploy_tail_never_recreates_completed_admin_or_bypasses_readiness(self):
        source = (ROOT / 'scripts/deploy-release.sh').read_text()
        tail = source[source.index('"${compose[@]}" up -d --pull never --no-build --wait'):]
        cases = [
            # historical COMPLETE is enough, even if an operator later changed the live account.
            ('true', 'COMPLETE', 'COMPLETE', '', 0, True),
            ('false', 'COMPLETE', 'COMPLETE', '', 0, True),
            ('true', 'ABSENT', 'COMPLETE', '', 1, True),
            ('false', 'ABSENT', 'COMPLETE', '', 0, True),
            ('true', 'HOLD', 'COMPLETE', '', 0, False),
            ('true', 'ABSENT', 'HOLD', '', 1, False),
            ('true', 'ABSENT', 'COMPLETE', 'create', 1, False),
            ('true', 'ABSENT', 'COMPLETE', 'compose', 0, False),
            ('true', 'ABSENT', 'COMPLETE', 'nginx', 0, False),
            ('true', 'ABSENT', 'COMPLETE', 'web', 0, False),
        ]
        for bootstrap, state, post_state, failure, creates, deployed in cases:
            with self.subTest(bootstrap=bootstrap, state=state, post=post_state, failure=failure), \
                    tempfile.TemporaryDirectory(prefix='rct-admin-deploy-tail-') as tmp:
                root = Path(tmp); bindir = root / 'bin'; bindir.mkdir()
                events, marker = root / 'events', root / 'deployed'
                driver = '''#!/usr/bin/python3
import os, sys
from pathlib import Path
name = Path(sys.argv[0]).name
if name == 'python3':
    script = Path(sys.argv[1]).name
    stage = {'verify-served-web-panel.py': 'web', 'bootstrap-initial-admin.py': 'create',
             'initial-admin-status.py': 'postcheck' if '--require-ready' in sys.argv else 'status'}[script]
else:
    stage = 'nginx' if name == 'docker' else name
with open(os.environ['EVENTS'], 'a') as stream: stream.write(stage + '\\n')
if os.environ['FAIL_STAGE'] == stage: raise SystemExit(31)
if stage in ('status', 'postcheck'):
    state = os.environ['POST_STATE' if stage == 'postcheck' else 'ADMIN_STATE']
    print(state)
    if state == 'HOLD': raise SystemExit(2)
'''
                for tool in ('python3', 'docker', 'compose'):
                    path = bindir / tool; path.write_text(driver); path.chmod(0o700)
                actual = tail.replace('/opt/rutcampustrack/.deployed-sha', str(marker))
                command = 'set -euo pipefail\ncompose=(compose)\nsha=' + SHA + '\nincoming=/synthetic\n' + actual
                result = subprocess.run(['bash', '-c', command], cwd=root, capture_output=True, timeout=10,
                    env=dict(os.environ, PATH=str(bindir) + os.pathsep + os.environ['PATH'],
                             bootstrap=bootstrap, ADMIN_STATE=state, POST_STATE=post_state,
                             FAIL_STAGE=failure, EVENTS=str(events)))
                observed = events.read_text().splitlines()
                self.assertEqual(observed.count('create'), creates)
                self.assertEqual(marker.exists(), deployed)
                self.assertEqual(result.returncode == 0, deployed)
                if failure in ('compose', 'nginx', 'web'):
                    self.assertNotIn('status', observed)
                if state == 'COMPLETE':
                    self.assertNotIn('postcheck', observed)
                if deployed and creates:
                    self.assertLess(observed.index('web'), observed.index('status'))
                    self.assertLess(observed.index('status'), observed.index('create'))
                    self.assertLess(observed.index('create'), observed.index('postcheck'))

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
