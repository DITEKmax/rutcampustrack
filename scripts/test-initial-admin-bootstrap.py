import contextlib
import importlib.util
import io
import json
import os
import subprocess
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
from xml.etree import ElementTree

SPEC = importlib.util.spec_from_file_location("initial_admin", Path(__file__).with_name("bootstrap-initial-admin.py"))
BOOTSTRAP = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(BOOTSTRAP)


def request():
    return {"version": 1, "operationId": "11111111-1111-4111-8111-111111111111",
            "login": "first-admin", "initialPassword": "Synthetic-first-123!",
            "profile": {"firstName": "Первый", "lastName": "Администратор", "middleName": ""}}


class InitialAdministratorOperatorTest(unittest.TestCase):
    def test_protected_file_denies_links_fifo_permissions_duplicates_and_size(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory, "request.json")
            path.write_text(json.dumps(request()), encoding="utf-8")
            path.chmod(0o600)
            self.assertEqual(BOOTSTRAP.read_request(path), request())
            for mode in (0o644, 0o640, 0o700):
                path.chmod(mode)
                with self.assertRaises(BOOTSTRAP.Refused):
                    BOOTSTRAP.read_request(path)
            path.chmod(0o600)
            link = Path(directory, "link")
            link.symlink_to(path)
            with self.assertRaises(OSError):
                BOOTSTRAP.read_request(link)
            parent_link = Path(directory, "directory-link")
            parent_link.symlink_to(directory, target_is_directory=True)
            with self.assertRaises(OSError):
                BOOTSTRAP.read_request(parent_link / "request.json")
            fifo = Path(directory, "fifo")
            os.mkfifo(fifo, 0o600)
            with self.assertRaises(BOOTSTRAP.Refused):
                BOOTSTRAP.read_request(fifo)
            path.write_bytes(b"x" * 8193)
            with self.assertRaises(BOOTSTRAP.Refused):
                BOOTSTRAP.read_request(path)
            path.write_text('{"version":1,"version":1}', encoding="utf-8")
            with self.assertRaises(BOOTSTRAP.Refused):
                BOOTSTRAP.read_request(path)
            path.write_text(json.dumps(request()), encoding="utf-8")
            with patch.object(BOOTSTRAP.os, "geteuid", return_value=os.geteuid() + 1):
                with self.assertRaises(BOOTSTRAP.Refused):
                    BOOTSTRAP.read_request(path)

    def test_secret_only_goes_to_auth_prepare_and_terminal_output_is_closed(self):
        calls = []

        def call(service, stage, body):
            calls.append((service, stage, body))
            reply = {k: body[k] for k in ("version", "operationId", "login", "profileDigest")}
            reply.update(userId=41, rolesVersion=1, commandHash=BOOTSTRAP.hashlib.sha256(
                BOOTSTRAP.canonical(reply)).hexdigest())
            if stage == "prepare":
                return dict(reply, status="PREPARED")
            # The digest is supplied by Auth after the first call; keep that exact binding.
            reply["commandHash"] = body["commandHash"]
            if stage == "activate":
                return dict(reply, status="COMMITTED", committedCatalogVersion=2)
            if stage in ("verify", "apply"):
                return dict(reply, academicRevision=1, receiptHash="a" * 64, status="APPLIED")
            if stage == "cursor":
                return dict(reply, afterCatalogVersion=2, status="APPLIED")
            return dict(reply, status="READY")

        result = BOOTSTRAP.bootstrap(request(), call)
        self.assertEqual(set(result), {"operationId", "userId", "status"})
        secret = request()["initialPassword"]
        for service, stage, body in calls:
            self.assertEqual(secret in json.dumps(body), service == "auth-service" and stage == "prepare")
        self.assertNotIn(secret, json.dumps(result))
        self.assertNotIn("commandHash", result)

    def test_failed_stage_stops_activation_and_does_not_print_child_error(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory, "request.json")
            path.write_text(json.dumps(request()), encoding="utf-8")
            path.chmod(0o600)
            output = io.StringIO()
            with patch.object(BOOTSTRAP, "invoke", side_effect=RuntimeError(request()["initialPassword"])), \
                    contextlib.redirect_stdout(output):
                self.assertEqual(BOOTSTRAP.main(["--request-file", str(path), "--compose-file", "unused.yml"]), 2)
            self.assertEqual(output.getvalue(), '{"status":"REFUSED"}\n')

    def test_academic_failure_stops_before_auth_activation(self):
        calls = []

        def call(service, stage, body):
            calls.append((service, stage))
            if service == "academic-service":
                raise BOOTSTRAP.Refused()
            public = {k: body[k] for k in ("version", "operationId", "login", "profileDigest")}
            return dict(public, userId=41, rolesVersion=1, status="PREPARED",
                        commandHash=BOOTSTRAP.hashlib.sha256(BOOTSTRAP.canonical(public)).hexdigest())

        with self.assertRaises(BOOTSTRAP.Refused):
            BOOTSTRAP.bootstrap(request(), call)
        self.assertEqual(calls, [("auth-service", "prepare"), ("academic-service", "apply")])

    def test_transport_never_places_secret_in_argv_or_environment(self):
        result = type("Result", (), {"returncode": 0, "stdout": b'{"status":"PREPARED"}', "stderr": b""})()
        secret = request()["initialPassword"]
        with patch.object(BOOTSTRAP.subprocess, "run", return_value=result) as run:
            BOOTSTRAP.invoke(["deployment.yml"], "auth-service", "prepare", {"password": secret})
        args, options = run.call_args
        self.assertNotIn(secret, repr(args))
        self.assertNotIn("env", options)
        self.assertIn(secret.encode(), options["input"])
        self.assertFalse(options["check"])

    def test_invalid_cli_arguments_never_echo_values_or_tracebacks(self):
        output, errors = io.StringIO(), io.StringIO()
        with contextlib.redirect_stdout(output), contextlib.redirect_stderr(errors):
            self.assertEqual(BOOTSTRAP.main(["--password", request()["initialPassword"]]), 2)
        self.assertEqual(output.getvalue(), '{"status":"REFUSED"}\n')
        self.assertEqual(errors.getvalue(), "")


class InitialAdministratorDiagnosticsTest(unittest.TestCase):
    ARGS = ["--request-file", "unused-private-request", "--compose-file", "unused-compose"]

    def main_result(self, result=None, *, failure=None, diagnostics=True):
        output, errors = io.StringIO(), io.StringIO()
        with patch.dict(BOOTSTRAP.os.environ, {BOOTSTRAP.DIAGNOSTIC_ENV: "1" if diagnostics else "0"}), \
                patch.object(BOOTSTRAP, "read_request", return_value=request()), \
                patch.object(BOOTSTRAP.subprocess, "run", return_value=result, side_effect=failure) as run, \
                contextlib.redirect_stdout(output), contextlib.redirect_stderr(errors):
            code = BOOTSTRAP.main(self.ARGS)
        return code, output.getvalue(), errors.getvalue(), run

    @staticmethod
    def child(code=2, output=b'{"status":"REFUSED"}', errors=b""):
        return type("Result", (), {"returncode": code, "stdout": output, "stderr": errors})()

    def test_default_refusal_stays_one_field_and_private_child_material_is_never_forwarded(self):
        secret = request()["initialPassword"].encode()
        result = self.child(errors=b"SQL/private/path/" + secret)
        code, output, errors, run = self.main_result(result, diagnostics=False)
        self.assertEqual((code, output, errors), (2, '{"status":"REFUSED"}\n', ""))
        self.assertNotIn("--env", run.call_args.args[0])

    def test_parse_and_request_read_are_distinct_from_child_failure(self):
        output, errors = io.StringIO(), io.StringIO()
        with patch.dict(BOOTSTRAP.os.environ, {BOOTSTRAP.DIAGNOSTIC_ENV: "1"}), \
                contextlib.redirect_stdout(output), contextlib.redirect_stderr(errors):
            self.assertEqual(BOOTSTRAP.main(["--password", request()["initialPassword"]]), 2)
        self.assertEqual(errors.getvalue(), "BOOTSTRAP_CLI_PHASE_V1 phase=PARSE reason=INPUT ownerPhase=NONE category=NONE\n")
        output, errors = io.StringIO(), io.StringIO()
        with patch.dict(BOOTSTRAP.os.environ, {BOOTSTRAP.DIAGNOSTIC_ENV: "1"}), \
                patch.object(BOOTSTRAP, "read_request", side_effect=OSError(request()["initialPassword"])), \
                contextlib.redirect_stdout(output), contextlib.redirect_stderr(errors):
            self.assertEqual(BOOTSTRAP.main(self.ARGS), 2)
        self.assertEqual(errors.getvalue(), "BOOTSTRAP_CLI_PHASE_V1 phase=READ_REQUEST reason=IO ownerPhase=NONE category=NONE\n")
        self.assertEqual(output.getvalue(), '{"status":"REFUSED"}\n')

    def test_auth_child_closed_category_crosses_only_explicit_private_channels(self):
        raw = (b"Picked up JAVA_TOOL_OPTIONS: private-value\n"
               b"BOOTSTRAP_OWNER_FAILURE_V1 owner=AUTH phase=CONFIG category=CONFIG\n")
        code, output, errors, run = self.main_result(self.child(errors=raw))
        self.assertEqual((code, output), (2, '{"status":"REFUSED"}\n'))
        self.assertEqual(errors, "BOOTSTRAP_CLI_PHASE_V1 phase=AUTH_PREPARE reason=CHILD_EXIT ownerPhase=CONFIG category=CONFIG\n")
        command = run.call_args.args[0]
        self.assertIn(BOOTSTRAP.DIAGNOSTIC_ENV + "=1", command)
        self.assertNotIn(request()["initialPassword"], repr(command))
        self.assertNotIn("private-value", output + errors)
        self.assertNotIn("env", run.call_args.kwargs)

    def test_start_timeout_reply_and_protocol_failures_have_distinct_closed_reasons(self):
        secret = request()["initialPassword"]
        cases = [
            (None, OSError(secret), "CHILD_START"),
            (None, subprocess.TimeoutExpired(secret, 30, output=secret, stderr=secret), "CHILD_TIMEOUT"),
            (self.child(0, ("{" + secret).encode()), None, "REPLY_JSON"),
            (self.child(0), None, "REPLY_GUARD"),
            (self.child(0, b'{"status":"PREPARED"}'), None, "PROTOCOL"),
            (self.child(errors=b"x" * 16385), None, "OUTPUT_LIMIT"),
        ]
        for result, failure, reason in cases:
            with self.subTest(reason=reason):
                code, output, errors, _ = self.main_result(result, failure=failure)
                self.assertEqual((code, output), (2, '{"status":"REFUSED"}\n'))
                self.assertEqual(errors, "BOOTSTRAP_CLI_PHASE_V1 phase=AUTH_PREPARE reason=" + reason + " ownerPhase=NONE category=NONE\n")
                self.assertNotIn(secret, errors + output)

    def test_owner_marker_rejects_wrong_owner_duplicates_extensions_and_unrecognized_material(self):
        valid = b"BOOTSTRAP_OWNER_FAILURE_V1 owner=AUTH phase=EXECUTE category=DB_PERMISSION"
        self.assertEqual(BOOTSTRAP.owner_diagnostic(valid, "auth"), ("EXECUTE", "DB_PERMISSION"))
        for raw in [valid + b" private", valid + b"\n" + valid,
                    valid.replace(b"owner=AUTH", b"owner=ACADEMIC"),
                    valid + b"\nBOOTSTRAP_OWNER_FAILURE_V1 malformed"]:
            with self.subTest(size=len(raw)):
                self.assertEqual(BOOTSTRAP.owner_diagnostic(raw, "auth"), ("OTHER", "OTHER"))
        self.assertEqual(BOOTSTRAP.owner_diagnostic(request()["initialPassword"].encode(), "auth"),
                         ("NO_MARKER", "OTHER"))

    def test_collector_projects_only_exact_case_owned_frame_and_closed_marker(self):
        spec = importlib.util.spec_from_file_location("bootstrap_outcomes", Path(__file__).with_name("collect-ci-test-outcomes.py"))
        collector = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(collector)
        root = Path(__file__).resolve().parent.parent
        source = (root / collector.BOOTSTRAP_CLI_SOURCE).read_text()
        line = next(i for i, value in enumerate(source.splitlines(), 1) if '"BOOTSTRAP_CLI_RESULT_V1 invocation="' in value)
        base = "BOOTSTRAP_CLI_RESULT_V1 invocation=FRESH guard=UNEXPECTED_EXIT expectedExit=0 actualExit=2"
        suffix = " phase=AUTH_PREPARE reason=CHILD_EXIT ownerPhase=EXECUTE category=DB_SCHEMA"

        def project(message, first=None):
            case = ElementTree.Element("testcase")
            failure = ElementTree.SubElement(case, "failure", type="java.lang.AssertionError", message=message)
            failure.text = "java.lang.AssertionError: " + (message if first is None else first) + "\n\tat " + collector.BOOTSTRAP_CLI_CLASS + ".runOperatorCli(InitialAdministratorBootstrapIT.java:" + str(line) + ")"
            return collector.bootstrap_cli_failure_diagnostic(case, source)

        result = project(base + suffix)
        self.assertEqual(result["state"], "CLOSED_MARKER")
        self.assertEqual({k: result[k] for k in ("phase", "reason", "ownerPhase", "category")},
                         {"phase": "AUTH_PREPARE", "reason": "CHILD_EXIT", "ownerPhase": "EXECUTE", "category": "DB_SCHEMA"})
        self.assertEqual(project(base)["state"], "CLOSED_MARKER")  # Historical diagnostics remain valid.
        for corrupt in [base + suffix + " " + request()["initialPassword"],
                        base + suffix.replace("DB_SCHEMA", request()["initialPassword"]),
                        base + suffix + "\n" + base + suffix,
                        base.replace("actualExit=2", "actualExit=999") + suffix]:
            result = project(corrupt)
            self.assertNotEqual(result["state"], "CLOSED_MARKER")
            self.assertNotIn(request()["initialPassword"], json.dumps(result))
        self.assertNotEqual(project(base + suffix, base + suffix.replace("DB_SCHEMA", "GUARD"))["state"], "CLOSED_MARKER")


if __name__ == "__main__":
    unittest.main()
