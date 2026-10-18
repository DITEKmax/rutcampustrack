import contextlib
import importlib.util
import io
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

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


if __name__ == "__main__":
    unittest.main()
