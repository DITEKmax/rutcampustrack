"""Private first-admin transport boundaries; only synthetic subprocesses, no network or Docker."""
import json
import importlib.util
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
SHA = "a" * 40
spec = importlib.util.spec_from_file_location('release_images', ROOT / 'scripts/release-images.py')
images = importlib.util.module_from_spec(spec)
spec.loader.exec_module(images)
MANIFEST = {'source_sha': SHA, 'images': {name: images.PREFIX + name + '@sha256:' + 'b' * 64 for name in images.IMAGES}}
PASSWORD = "Synthetic-first-123!"


def request():
    return {"version": 1, "operationId": "11111111-1111-4111-8111-111111111111",
            "login": "rootadmin", "initialPassword": PASSWORD,
            "profile": {"firstName": "Админ", "middleName": "Админович", "lastName": "Администратов"}}


def write(path, text, mode=0o700):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8")
    path.chmod(mode)


class InitialAdminDeploymentControls(unittest.TestCase):
    def sandbox(self, root):
        scripts, bindir = root / "scripts", root / "bin"
        scripts.mkdir()
        (root / 'release-images.json').write_text(json.dumps(MANIFEST))
        bindir.mkdir()
        for name in ("deliver-release.sh", "deploy-release.sh", "bootstrap-initial-admin.py", "release-images.py"):
            shutil.copyfile(ROOT / "scripts" / name, scripts / name)
        write(scripts / "deployment-secrets.py", "import sys\n")
        env = dict(os.environ, PATH=str(bindir) + os.pathsep + os.environ["PATH"],
                   DEPLOY_SHA=SHA, VPS_HOST="synthetic.invalid", VPS_USER="synthetic",
                   GITHUB_RUN_ID="123", GITHUB_RUN_ATTEMPT="1", GITHUB_EVENT_NAME="workflow_dispatch",
                   BOOTSTRAP_INITIAL_ADMIN="true", INITIAL_ADMIN_REQUEST=json.dumps(request()),
                   RCT_TEST_LOG=str(root / "calls"), RCT_TEST_EARLY_LOG=str(root / "early-calls"), TMPDIR=str(root / "private"),
                   PROD_ENV="synthetic", PROD_SECRET_FILES_B64="synthetic",
                   SSH_KNOWN_HOSTS="synthetic", SSH_PRIVATE_KEY="synthetic", GHCR_TOKEN="synthetic")
        (root / "private").mkdir()
        for name in ("ssh", "scp", "git"):
            write(bindir / name, """#!/usr/bin/env python3
import json, os, pathlib, stat, sys
name = pathlib.Path(sys.argv[0]).name
with open(os.environ['RCT_TEST_EARLY_LOG'], 'a') as log:
    log.write(json.dumps([name, sys.argv[1:], 'INITIAL_ADMIN_REQUEST' in os.environ]) + '\\n')
if name == 'git':
    if sys.argv[1] == 'rev-parse': print(os.environ['DEPLOY_SHA'])
    else: sys.stdout.buffer.write(b'synthetic archive')
else:
    with open(os.environ['RCT_TEST_LOG'], 'a') as log:
        log.write(json.dumps([name, sys.argv[1:], 'INITIAL_ADMIN_REQUEST' in os.environ]) + '\\n')
    if name == 'scp':
        for value in sys.argv[1:]:
            if value.endswith('/INITIAL_ADMIN_REQUEST'):
                p = pathlib.Path(value)
                assert stat.S_IMODE(p.stat().st_mode) == 0o600
                assert json.loads(p.read_text())['login'] == 'rootadmin'
    if name == os.environ.get('RCT_TEST_FAIL'):
        raise SystemExit(17)
""")
        for name in ("grep", "mktemp"):
            native = shutil.which(name)
            write(bindir / name, """#!/usr/bin/env python3
import json, os, sys
with open(os.environ['RCT_TEST_EARLY_LOG'], 'a') as log:
    log.write(json.dumps([%r, sys.argv[1:], 'INITIAL_ADMIN_REQUEST' in os.environ]) + '\\n')
os.execv(%r, [%r] + sys.argv[1:])
""" % (name, native, name))
        return env

    def test_early_workflow_and_delivery_children_never_inherit_request(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            env = self.sandbox(root)
            self.assertEqual(self.run_delivery(root, env, workflow_gate=True).returncode, 0)
            raw = (root / "early-calls").read_text()
            calls = [json.loads(line) for line in raw.splitlines()]
            names = [call[0] for call in calls]
            self.assertGreaterEqual(names.count("grep"), 2, "workflow and delivery capability probes")
            self.assertIn("git", names)
            self.assertIn("mktemp", names)
            self.assertTrue(all(not call[2] for call in calls), "early child environment must be clear")
            self.assertNotIn(PASSWORD, raw)
            self.assertNotIn(json.dumps(request()), raw)

    def run_delivery(self, root, env, workflow_gate=False):
        command = ["bash", "scripts/deliver-release.sh"]
        if workflow_gate:
            workflow = (ROOT / ".github/workflows/deploy.yml").read_text()
            tail = workflow.split("      - name: Deliver exact revision", 1)[1]
            run = tail.split("        run: |\n", 1)[1]
            command = ["bash", "-c", "\n".join(line[10:] for line in run.splitlines())]
        result = subprocess.run(command, cwd=root, env=env, capture_output=True, timeout=10)
        self.assertNotIn(PASSWORD.encode(), result.stdout + result.stderr)
        self.assertNotIn("Администратов".encode(), result.stdout + result.stderr)
        self.assertEqual(list((root / "private").iterdir()), [], "runner files must be removed")
        return result

    def test_workflow_default_and_conditional_secret_never_authorize_automatic_event(self):
        workflow = (ROOT / ".github/workflows/deploy.yml").read_text()
        flag = workflow.split("      bootstrap_initial_admin:\n", 1)[1].split("      reason:", 1)[0]
        self.assertIn("default: false", flag)
        expressions = {}
        for name in ("BOOTSTRAP_INITIAL_ADMIN", "INITIAL_ADMIN_REQUEST"):
            expressions[name] = re.search(r"          " + name + r": \$\{\{ (.+) \}\}", workflow)[1]
        for event, enabled in (("workflow_run", True), ("workflow_dispatch", False),
                               ("workflow_dispatch", True)):
            values = {}
            for name, expression in expressions.items():
                expression = (expression.replace("github.event_name", repr(event))
                              .replace("inputs.bootstrap_initial_admin", repr(enabled))
                              .replace("secrets.INITIAL_ADMIN_REQUEST", repr(PASSWORD))
                              .replace("true", "True").replace("false", "False")
                              .replace("&&", " and ").replace("||", " or "))
                # The evaluated expression is the fixed checked-in boolean/string policy, no input code.
                values[name] = eval(expression, {"__builtins__": {}}, {})
            authorized = event == "workflow_dispatch" and enabled
            self.assertEqual(values["BOOTSTRAP_INITIAL_ADMIN"], "True" if authorized else "False")
            self.assertEqual(values["INITIAL_ADMIN_REQUEST"], PASSWORD if authorized else "")

    def test_disabled_ignores_present_malformed_request_and_does_not_transfer_it(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            env = self.sandbox(root)
            env.update(BOOTSTRAP_INITIAL_ADMIN="false", INITIAL_ADMIN_REQUEST="malformed",
                       GITHUB_EVENT_NAME="workflow_run")
            self.assertEqual(self.run_delivery(root, env).returncode, 0)
            calls = (root / "calls").read_text()
            transfer = next(json.loads(line) for line in calls.splitlines() if json.loads(line)[0] == "scp")
            self.assertNotIn("INITIAL_ADMIN_REQUEST", repr(transfer[1]))
            self.assertFalse(any(json.loads(line)[2] for line in calls.splitlines()))
            self.assertIn("'false'", calls)

    def test_invalid_or_automatic_request_has_no_transport(self):
        invalid = ["", "{", '{"version":1,"version":1}', "x" * 8193]
        for mutation in (dict(initialPassword="short1!"), dict(version=True),
                         dict(operationId="not-a-uuid"), dict(extra=PASSWORD)):
            invalid.append(json.dumps(dict(request(), **mutation)))
        for value in invalid + [json.dumps(request())]:
            with self.subTest(size=len(value)), tempfile.TemporaryDirectory() as tmp:
                root = Path(tmp)
                env = self.sandbox(root)
                env["INITIAL_ADMIN_REQUEST"] = value
                if value == json.dumps(request()):
                    env["GITHUB_EVENT_NAME"] = "workflow_run"
                self.assertNotEqual(self.run_delivery(root, env).returncode, 0)
                self.assertFalse((root / "calls").exists())

    def test_selected_older_or_missing_delivery_script_fails_before_execution(self):
        for missing in (False, True):
            with self.subTest(missing=missing), tempfile.TemporaryDirectory() as tmp:
                root = Path(tmp)
                env = self.sandbox(root)
                old = root / "scripts/deliver-release.sh"
                if missing:
                    old.unlink()
                else:
                    write(old, "#!/bin/bash\ntouch unexpected-side-effect\n")
                self.assertNotEqual(self.run_delivery(root, env, workflow_gate=True).returncode, 0)
                self.assertFalse((root / "unexpected-side-effect").exists())
                self.assertFalse((root / "calls").exists())
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            env = self.sandbox(root)
            write(root / "scripts/deploy-release.sh", "#!/bin/bash\nexit 0\n")
            self.assertNotEqual(self.run_delivery(root, env).returncode, 0)
            self.assertFalse((root / "calls").exists())

    def test_enabled_private_transfer_and_failure_cleanup(self):
        for failure in ("", "scp", "ssh"):
            with self.subTest(failure=failure), tempfile.TemporaryDirectory() as tmp:
                root = Path(tmp)
                env = self.sandbox(root)
                env["RCT_TEST_FAIL"] = failure
                result = self.run_delivery(root, env)
                self.assertEqual(result.returncode, 0 if not failure else 17)
                calls = [json.loads(line) for line in (root / "calls").read_text().splitlines()]
                self.assertTrue(all(not call[2] for call in calls), "request env must not reach transport")
                self.assertNotIn(PASSWORD, repr(calls))
                if failure != "ssh":
                    scp = next(call for call in calls if call[0] == "scp")
                    self.assertTrue(any(arg.endswith("/INITIAL_ADMIN_REQUEST") for arg in scp[1]))
                    self.assertIn("rm -f", calls[-1][1][-1])
                    self.assertIn("/INITIAL_ADMIN_REQUEST", calls[-1][1][-1])

    def remote_sandbox(self, root, failure=""):
        env = self.sandbox(root)
        incoming = root / "server/incoming/123-1"
        incoming.mkdir(parents=True)
        (incoming / 'release-images.json').write_text(json.dumps(MANIFEST))
        for name, value in (("REVISION", SHA), ("PROD_ENV", "synthetic"),
                            ("PROD_SECRET_FILES_B64", "synthetic"), ("GHCR_TOKEN", "synthetic"),
                            ("INITIAL_ADMIN_REQUEST", json.dumps(request()))):
            write(incoming / name, value, 0o600)
        (root / "vault/active").mkdir(parents=True)
        write(root / "nginx/dhparam.pem", "synthetic")
        write(root / "scripts/verify-served-web-panel.py", "pass\n")
        release = root / "scripts/deploy-release.sh"
        source = release.read_text().replace("/opt/rutcampustrack-secrets", str(root / "vault"))
        source = source.replace("/opt/rutcampustrack", str(root / "server"))
        write(release, source)
        env["RCT_TEST_BOOTSTRAP_FAIL"] = failure
        write(root / "bin/openssl", """#!/usr/bin/env python3
import pathlib, sys
if '-out' in sys.argv: pathlib.Path(sys.argv[sys.argv.index('-out')+1]).write_text('SYNTHETIC-PUBLIC\\n')
""")
        write(root / "bin/docker", """#!/usr/bin/env python3
import hashlib, json, os, sys
args=sys.argv[1:]
with open(os.environ['RCT_TEST_LOG'], 'a') as log:
    log.write(json.dumps([args, os.environ.get('IMAGE_TAG')])+'\\n')
if args[:2] == ['image', 'inspect']:
    name = args[-1].rsplit(':', 1)[0]
    print(json.dumps([{'RepoDigests': [name + '@sha256:' + 'b'*64]}]))
if args[:2] == ['ps', '--format']: raise SystemExit(0)
if args and args[0]=='run':
    print('SYNTHETIC-PUBLIC')
if args and args[0]=='compose' and 'run' in args:
    body=json.load(sys.stdin)
    stage=args[-1]
    if os.environ.get('RCT_TEST_BOOTSTRAP_FAIL') == stage:
        print(body.get('password', 'PRIVATE-CHILD'), file=sys.stderr)
        raise SystemExit(23)
    fields=('version','operationId','login','profileDigest')
    reply={k:body[k] for k in fields}
    digest=hashlib.sha256(json.dumps(reply,sort_keys=True,separators=(',',':'),ensure_ascii=False).encode()).hexdigest()
    reply.update(commandHash=digest,userId=41,rolesVersion=1,status='APPLIED')
    if stage=='prepare': reply['status']='PREPARED'
    elif stage=='verify': reply.update(academicRevision=1,receiptHash='b'*64)
    elif stage=='activate': reply.update(status='COMMITTED',committedCatalogVersion=2)
    elif stage=='cursor': reply['afterCatalogVersion']=2
    elif stage=='finalize': reply['status']='READY'
    print(json.dumps(reply))
""")
        return env, incoming

    def test_remote_explicit_context_health_order_failure_and_cleanup(self):
        for enabled, failure in (("false", ""), ("true", ""), ("true", "prepare"), ("true", "finalize")):
            with self.subTest(enabled=enabled, failure=failure), tempfile.TemporaryDirectory() as tmp:
                root = Path(tmp)
                env, incoming = self.remote_sandbox(root, failure)
                result = subprocess.run(["bash", "scripts/deploy-release.sh", SHA, str(incoming), enabled],
                                        cwd=root, env=env, capture_output=True, timeout=10)
                self.assertEqual(result.returncode, 2 if failure else 0, result.stderr.decode())
                self.assertNotIn(PASSWORD.encode(), result.stdout + result.stderr)
                self.assertNotIn("Администратов".encode(), result.stdout + result.stderr)
                for name in ("INITIAL_ADMIN_REQUEST", "PROD_ENV", "PROD_SECRET_FILES_B64", "GHCR_TOKEN",
                             "docker-auth", "jwt-candidate"):
                    self.assertFalse((incoming / name).exists(), "remote cleanup: " + name)
                deployed = root / "server/.deployed-sha"
                self.assertEqual(deployed.exists(), not bool(failure))
                calls = [json.loads(line) for line in (root / "calls").read_text().splitlines()]
                commands = [call[0] for call in calls]
                oneshots = [(index, args, calls[index][1]) for index, args in enumerate(commands)
                            if args[0] == "compose" and "run" in args]
                self.assertEqual(bool(oneshots), enabled == "true")
                for index, args, tag in oneshots:
                    self.assertEqual(args[:10], ["compose", "--project-name", "rutcampustrack",
                        "--env-file", ".env.prod", "-f", "docker-compose.prod.yml",
                        "-f", "docker-compose.operator.yml", "run"])
                    self.assertEqual(tag, SHA)
                    self.assertGreater(index, next(i for i, command in enumerate(commands) if "--wait" in command))
                self.assertNotIn(PASSWORD, repr(calls))

    def test_remote_bad_request_refused_before_any_container_or_install(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            env, incoming = self.remote_sandbox(root)
            write(incoming / "INITIAL_ADMIN_REQUEST", "{", 0o600)
            write(root / "scripts/deployment-secrets.py", "raise AssertionError('must not install')\n")
            result = subprocess.run(["bash", "scripts/deploy-release.sh", SHA, str(incoming), "true"],
                                    cwd=root, env=env, capture_output=True, timeout=10)
            self.assertEqual(result.returncode, 2)
            self.assertFalse((root / "calls").exists())
            self.assertFalse((incoming / "INITIAL_ADMIN_REQUEST").exists())
            self.assertFalse((root / "server/.deployed-sha").exists())


if __name__ == "__main__":
    unittest.main()
