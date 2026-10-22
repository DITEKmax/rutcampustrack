#!/usr/bin/env python3
"""Focused failure guards of the real recovery functions; no native restore claim.

DB command/inventory seams are controlled here. Filesystem effects, retained partial
bundles, manifest verification and restore preflight execute production functions.
Actual native dumps and application readback require a separate isolated lease.
"""

import argparse
import ast
import copy
import importlib.util
import json
from pathlib import Path
import re
import tarfile
import tempfile
import textwrap
from types import SimpleNamespace
import unittest
from unittest.mock import patch

HERE = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location("recovery", HERE / "recovery.py")
r = importlib.util.module_from_spec(spec)
spec.loader.exec_module(r)


def arguments(root):
    return argparse.Namespace(
        env_file=root / "never-read-fixture.env",
        academic_container="owned-academic-fixture",
        schedule_container="owned-schedule-fixture",
        mongo_container="owned-mongo-fixture",
        files_dir=None,
        no_files=True,
        output=root / "partial-bundle",
        target_project="rct-recovery-unit-fixture",
        dry_run=False,
        quiesced=True,
    )


def synthetic_inventory():
    postgres = {
        label: {
            "schema": "", "tables": [], "sequences": [],
            "access": {
                "format": 1, "unsupported": False,
                "objects": [{"kind": "DATABASE", "name": label + "_db", "owner": "rct_user", "acl": None}],
            },
        }
        for label in ("academic", "auth", "schedule")
    }
    return {
        **postgres,
        "mongo": {"attendance_db": {}, "notification_db": {}},
        "files": [],
    }


def make_bundle(root):
    bundle = root / "valid-fixture-bundle"
    bundle.mkdir()
    for name in r.PAYLOADS - {"files.tar", "inventory.json"}:
        (bundle / name).write_bytes(b"controlled archive placeholder, not a native dump")
    with tarfile.open(bundle / "files.tar", "w:"):
        pass
    r.write_json(bundle / "inventory.json", synthetic_inventory())
    r.write_json(
        bundle / "manifest.json",
        {
            "format": r.BUNDLE_FORMAT,
            "files_mode": "database-only",
            "sha256": {name: r.digest(bundle / name) for name in sorted(r.PAYLOADS)},
        },
    )
    return bundle


class RecoveryFailureBoundaries(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        evidence = HERE.parent / ".agent" / "evidence" / "recovery-validation"
        evidence.mkdir(parents=True, exist_ok=True)
        cls.retained = Path(tempfile.mkdtemp(prefix="guard-unit-", dir=evidence))
        # These command-seam bundles are retained too; no backup/artifact deletion.
        print(f"CONTROLLED DB COMMAND SEAMS ONLY; retained artifacts: {cls.retained}", flush=True)

    def setUp(self):
        # An accidentally unmocked native command must fail before Docker/DB access.
        self.enterContext(patch.object(r.subprocess, "run", side_effect=AssertionError("Native command forbidden")))

    def test_mongo_closed_details_are_unique_bounded_enums_without_private_material(self):
        private = b"mongodb://synthetic-user:PRIVATE_VALUE@synthetic-host/private"
        complete = (b"RECOVERY_MONGO_PHASE:EMPTY_PREFLIGHT\n"
                    b"RECOVERY_MONGO_CODE_CLASS:AUTH_FAILED\n" + private)
        expected = {"mongoPhase": "EMPTY_PREFLIGHT", "mongoCodeClass": "AUTH_FAILED"}
        self.assertEqual(r.mongo_failure_details(complete), expected)
        self.assertNotIn("PRIVATE_VALUE", json.dumps(r.mongo_failure_details(complete)))
        for name, valid, invalid in (
            ("mongoPhase", b"RECOVERY_MONGO_PHASE:EMPTY_PREFLIGHT", b"RECOVERY_MONGO_PHASE:" + private),
            ("mongoCodeClass", b"RECOVERY_MONGO_CODE_CLASS:AUTH_FAILED", b"RECOVERY_MONGO_CODE_CLASS:" + private),
        ):
            for defect in (valid + b"\n" + valid, valid + b"\n" + invalid, invalid, valid + b"\r"):
                with self.subTest(field=name, defect_length=len(defect)):
                    self.assertEqual(r.mongo_failure_details(defect)[name], "UNKNOWN")
        for invalid in (complete.decode(), complete + b"x" * 65536, None):
            self.assertEqual(r.mongo_failure_details(invalid), {"mongoPhase": "UNKNOWN", "mongoCodeClass": "UNKNOWN"})
        error = r.RecoveryError("safe", "MONGO_QUERY")
        error.mongo_phase, error.mongo_code_class = ["PRIVATE_VALUE"], private
        self.assertEqual(r.mongo_failure_markers(error),
                         ("RECOVERY_MONGO_PHASE:UNKNOWN", "RECOVERY_MONGO_CODE_CLASS:UNKNOWN"))

    def test_native_mongo_failures_emit_only_allowlisted_classes(self):
        examples = {
            b"MongoServerError: Authentication failed.\n": "AUTH_FAILED",
            b"RECOVERY_MONGO_CODE_CLASS:NOT_WRITABLE_PRIMARY\n": "NOT_WRITABLE_PRIMARY",
            b"RECOVERY_MONGO_CODE_CLASS:NOT_PRIMARY_OR_SECONDARY\n": "NOT_PRIMARY_OR_SECONDARY",
            b"MongoServerSelectionError: mongodb://synthetic:PRIVATE_VALUE@local\n": "TIMEOUT",
            b"MongoNetworkTimeoutError: PRIVATE_VALUE\n": "TIMEOUT",
            b"MongoServerError: PRIVATE_VALUE\n": "OTHER",
            b"unrecognized PRIVATE_VALUE\n": "UNKNOWN",
            b"RECOVERY_MONGO_CODE_CLASS:AUTH_FAILED\nRECOVERY_MONGO_CODE_CLASS:OTHER\n": "UNKNOWN",
            b"MongoServerError: Authentication failed.\n" + b"x" * 65536: "UNKNOWN",
        }
        for stderr, expected in examples.items():
            with self.subTest(expected=expected, stderr_length=len(stderr)):
                with patch.object(r.subprocess, "run", return_value=SimpleNamespace(returncode=1, stdout=b"", stderr=stderr)):
                    with self.assertRaises(r.RecoveryError) as caught:
                        r.run(["docker", "exec", "synthetic", "mongosh"])
                self.assertEqual(caught.exception.hint, "MONGO_QUERY")
                self.assertEqual(caught.exception.mongo_code_class, expected)
                self.assertNotIn("PRIVATE_VALUE", str(caught.exception))

    def test_actual_restore_and_final_inventory_attach_distinct_mongo_phases(self):
        root = self.retained / "mongo-phase"
        root.mkdir()
        args = arguments(root)
        args.bundle = make_bundle(root)
        secret = {key: "SYNTHETIC-ONLY" for key in (
            "POSTGRES_ACADEMIC_MIGRATION_PASSWORD", "POSTGRES_AUTH_MIGRATION_PASSWORD",
            "POSTGRES_SCHEDULE_MIGRATION_PASSWORD", "MONGO_ROOT_PASSWORD",
        )}
        for phase in ("EMPTY_PREFLIGHT", "FINAL_INVENTORY"):
            error = r.RecoveryError("safe native failure", "MONGO_QUERY")
            error.mongo_code_class = "NOT_WRITABLE_PRIMARY"
            with (patch.object(r, "credentials", return_value=secret),
                  patch.object(r, "check_targets"), patch.object(r, "pg_query", return_value="0"),
                  patch.object(r, "run", side_effect=error)):
                with self.assertRaises(r.RecoveryError) as caught:
                    if phase == "EMPTY_PREFLIGHT":
                        r.restore(args)
                    else:
                        with patch.object(r, "pg_targets", return_value=()):
                            r.compare_restored_inventory(args, secret, {})
            self.assertEqual(caught.exception.mongo_phase, phase)
            self.assertEqual(r.mongo_failure_details(("\n".join(r.mongo_failure_markers(caught.exception)) + "\n").encode()),
                             {"mongoPhase": phase, "mongoCodeClass": "NOT_WRITABLE_PRIMARY"})

    def test_existing_ci_consumer_keeps_old_fields_and_rejects_ambiguous_or_oversize_details(self):
        workflow = (HERE.parent / ".github/workflows/ci.yml").read_text()
        body = textwrap.dedent(workflow.split("<<'PY_RECOVERY'\n", 1)[1].split("          PY_RECOVERY", 1)[0])
        tree = ast.parse(body)
        consumer = next(node for node in tree.body if isinstance(node, ast.FunctionDef) and node.name == "retain_backup_failure")
        initial = {"action": "RESTORE_SCRIPT", "hint": "UNKNOWN", "guardReason": "UNKNOWN",
                   "errorClass": "UNKNOWN", "exitStatus": "UNKNOWN", "mongoPhase": "UNKNOWN", "mongoCodeClass": "UNKNOWN"}
        for malformed in (False, True, "oversize"):
            state = {"stage": "fresh-restore", "backup_failure": dict(initial), "re": re, "recovery": r}
            exec(compile(ast.Module(body=[consumer], type_ignores=[]), "existing-ci-consumer", "exec"), state)
            stderr = (b"RECOVERY_FAILURE_HINT:MONGO_QUERY\nRECOVERY_MONGO_PHASE:FINAL_INVENTORY\n"
                      b"RECOVERY_MONGO_CODE_CLASS:TIMEOUT\n")
            if malformed is True:
                stderr += b"RECOVERY_MONGO_PHASE:PRIVATE_VALUE\nRECOVERY_MONGO_CODE_CLASS:OTHER\n"
            elif malformed == "oversize":
                stderr += b"x" * 65536
            state["retain_backup_failure"](RuntimeError("PRIVATE_VALUE"), exit_status=1, stderr=stderr)
            result = state["backup_failure"]
            self.assertEqual(set(result), set(initial))
            self.assertEqual({key: result[key] for key in ("action", "guardReason", "errorClass", "exitStatus")},
                             {"action": "RESTORE_SCRIPT", "guardReason": "UNKNOWN", "errorClass": "RuntimeError", "exitStatus": 1})
            self.assertEqual(result["mongoPhase"], "FINAL_INVENTORY" if malformed is False else "UNKNOWN")
            self.assertEqual(result["mongoCodeClass"], "TIMEOUT" if malformed is False else "UNKNOWN")
            self.assertNotIn("PRIVATE_VALUE", json.dumps(result))

    def test_fresh_mongo_health_contract_preserves_bootstrap_and_every_deadline(self):
        original = {"test": ["CMD", "mongosh", "--quiet", "--eval", "db.adminCommand('ping').ok"],
                    "interval": "10s", "timeout": "5s", "retries": 15, "start_period": "60s"}
        before = copy.deepcopy(original)
        health = r.mongo_restore_healthcheck(original)
        self.assertEqual(original, before)
        self.assertEqual({key: value for key, value in health.items() if key != "test"},
                         {key: value for key, value in original.items() if key != "test"})
        self.assertEqual(health["test"][:8], ["CMD", "mongosh", "--quiet", "--host", "127.0.0.1", "--port", "27017", "--eval"])
        js = health["test"][8]
        for predicate in ("admin.auth(", "MONGODB_ROOT_USER", "MONGODB_ROOT_PASSWORD", "auth === 1 || auth.ok === 1",
                          "isWritablePrimary !== true", "['attendance_db', 'notification_db']", "getCollectionInfos()"):
            self.assertIn(predicate, js)
        self.assertNotIn("rs.initiate", js)
        with self.assertRaises(r.RecoveryError):
            r.mongo_restore_healthcheck({**original, "test": ["CMD-SHELL", "rs.initiate()"]})
        workflow = (HERE.parent / ".github/workflows/ci.yml").read_text()
        body = textwrap.dedent(workflow.split("<<'PY_RECOVERY'\n", 1)[1].split("          PY_RECOVERY", 1)[0])
        tree = ast.parse(body)
        branch = next(node for node in ast.walk(tree) if isinstance(node, ast.If)
                      and ast.unparse(node.test) == "service == 'mongo-attendance'")
        target = {"services": {"mongo-attendance": {"environment": {"replica-bootstrap": "unchanged"}, "image": "pinned"}}}
        state = {"service": "mongo-attendance", "target": target, "original": {"healthcheck": original}, "recovery": r}
        # Execute only the actual target configuration branch, never the CI runner.
        exec(compile(ast.Module(body=[branch], type_ignores=[]), "existing-ci-mongo-health-contract", "exec"), state)
        self.assertEqual(target["services"]["mongo-attendance"],
                         {"environment": {"replica-bootstrap": "unchanged"}, "image": "pinned", "healthcheck": health})

    def test_production_credentials_require_schedule_migrator_and_separate_cluster_admin(self):
        root = self.retained / "production-credentials"
        root.mkdir()
        args = arguments(root)
        values = {
            key: "synthetic-only-" + str(index)
            for index, key in enumerate((
                "POSTGRES_ACADEMIC_MIGRATION_PASSWORD", "POSTGRES_AUTH_MIGRATION_PASSWORD",
                "POSTGRES_SCHEDULE_MIGRATION_PASSWORD", "POSTGRES_RECOVERY_ADMIN_PASSWORD",
                "POSTGRES_SCHEDULE_RECOVERY_ADMIN_PASSWORD", "MONGO_ROOT_PASSWORD",
            ))
        }
        values.update(POSTGRES_RECOVERY_ADMIN_USER="rct_bootstrap",
                      POSTGRES_SCHEDULE_RECOVERY_ADMIN_USER="rct_bootstrap")

        def write(inputs):
            args.env_file.write_text("".join(key + "=" + value + "\n" for key, value in inputs.items()))

        write(values)
        secret = r.credentials(args.env_file)
        targets = r.pg_targets(args, secret)
        self.assertEqual([row[4] for row in targets], ["rct_migrator", "rct_auth_migrator", "rct_migrator"])
        self.assertEqual(targets[2][3], "POSTGRES_SCHEDULE_MIGRATION_PASSWORD")
        # The old runtime credential cannot silently substitute for a migrator.
        incomplete = dict(values)
        incomplete["POSTGRES_SCHEDULE_PASSWORD"] = incomplete.pop("POSTGRES_SCHEDULE_MIGRATION_PASSWORD")
        write(incomplete)
        with self.assertRaises(r.RecoveryError):
            r.credentials(args.env_file)
        incomplete = dict(values)
        del incomplete["POSTGRES_SCHEDULE_RECOVERY_ADMIN_PASSWORD"]
        write(incomplete)
        with self.assertRaises(r.RecoveryError):
            r.credentials(args.env_file)

    def test_partial_dump_is_retained_without_success_manifest_and_retry_needs_new_output(self):
        root = self.retained / "partial"
        root.mkdir()
        args = arguments(root)
        command_count = 0
        fail_schedule = True

        def dump(command, *, stdout=None, **kwargs):
            nonlocal command_count
            command_count += 1
            if fail_schedule and "schedule_db" in command and "pg_dump" in command:
                stdout.write(b"controlled incomplete Schedule archive")
                raise r.RecoveryError("controlled Schedule dump failure")
            if stdout is not None:
                stdout.write(b"controlled native-command output")
            return b""

        # No real credential is read; do not echo command arguments from this seam.
        secret = {
            key: "SYNTHETIC-ONLY"
            for key in ("POSTGRES_ACADEMIC_MIGRATION_PASSWORD", "POSTGRES_AUTH_MIGRATION_PASSWORD",
                        "POSTGRES_SCHEDULE_MIGRATION_PASSWORD", "MONGO_ROOT_PASSWORD", "POSTGRES_RECOVERY_ADMIN_PASSWORD")
        }
        with (
            patch.object(r, "credentials", return_value=secret),
            patch.object(r, "check_targets"),
            patch.object(r, "inventory", side_effect=lambda *_: copy.deepcopy(synthetic_inventory())),
            patch.object(r, "run", side_effect=dump),
        ):
            with self.assertRaises(r.RecoveryError):
                r.backup(args)
            self.assertTrue(args.output.is_dir())
            self.assertEqual((args.output / "academic.dump").read_bytes(), b"controlled native-command output")
            self.assertEqual((args.output / "auth.dump").read_bytes(), b"controlled native-command output")
            self.assertEqual((args.output / "schedule.dump").read_bytes(), b"controlled incomplete Schedule archive")
            self.assertFalse((args.output / "manifest.json").exists())
            self.assertFalse((args.output / "inventory.json").exists())
            with self.assertRaises(r.RecoveryError):
                r.verify_bundle(args.output, "database-only")

            partial_hashes = {path.name: r.digest(path) for path in args.output.iterdir()}
            calls_before_retry = command_count
            with self.assertRaises(r.RecoveryError):
                r.backup(args)
            self.assertEqual(command_count, calls_before_retry)
            self.assertEqual({path.name: r.digest(path) for path in args.output.iterdir()}, partial_hashes)

            fail_schedule = False
            partial = args.output
            args.output = root / "new-output-bundle"
            r.backup(args)
            self.assertEqual(r.verify_bundle(args.output, "database-only"), synthetic_inventory())
            self.assertTrue(partial.is_dir())
            self.assertFalse((partial / "manifest.json").exists())
            self.assertEqual({path.name: r.digest(path) for path in partial.iterdir()}, partial_hashes)

    def test_auth_schedule_or_mongo_preflight_failure_prevents_every_restore_write(self):
        for failed_target in ("auth", "schedule", "notification-mongo", "label"):
            with self.subTest(failed_target=failed_target):
                root = self.retained / ("preflight-" + failed_target)
                root.mkdir()
                args = arguments(root)
                args.bundle = make_bundle(root)
                bundle_before = {path.name: r.digest(path) for path in args.bundle.iterdir()}
                writes = []
                queries = []
                inspections = []
                mongo_queries = []

                def pg_count(container, password, db, sql, *, user):
                    self.assertCountEqual(
                        inspections, [args.academic_container, args.schedule_container, args.mongo_container]
                    )
                    queries.append(db)
                    return "1" if db == failed_target + "_db" else "0"

                def mongo_count(container, password, script, *, phase):
                    self.assertCountEqual(
                        inspections, [args.academic_container, args.schedule_container, args.mongo_container]
                    )
                    self.assertEqual(container, args.mongo_container)
                    self.assertEqual(phase, "EMPTY_PREFLIGHT")
                    mongo_queries.append("EMPTY_PREFLIGHT")
                    return [0, 1] if failed_target == "notification-mongo" else [0, 0]

                def native_write(command, **kwargs):
                    if command[:2] == ["docker", "inspect"]:
                        container = command[2]
                        inspections.append(container)
                        label = "disposable"
                        if failed_target == "label" and container == args.schedule_container:
                            label = "not-disposable"
                        return json.dumps(
                            [
                                {
                                    "State": {"Running": True},
                                    "Config": {
                                        "Labels": {
                                            "io.rutcampustrack.recovery": label,
                                            "com.docker.compose.project": args.target_project,
                                        }
                                    },
                                }
                            ]
                        ).encode()
                    # Store only a safe operation name, never the credential-bearing argv.
                    writes.append("native restore invoked")
                    return b""

                secret = {
                    key: "SYNTHETIC-ONLY"
                    for key in ("POSTGRES_ACADEMIC_MIGRATION_PASSWORD", "POSTGRES_AUTH_MIGRATION_PASSWORD",
                        "POSTGRES_SCHEDULE_MIGRATION_PASSWORD", "MONGO_ROOT_PASSWORD", "POSTGRES_RECOVERY_ADMIN_PASSWORD")
                }
                with (
                    patch.object(r, "credentials", return_value=secret),
                    patch.object(r, "pg_query", side_effect=pg_count),
                    patch.object(r, "mongo_query", side_effect=mongo_count),
                    patch.object(r, "run", side_effect=native_write),
                    patch.object(r, "compare_restored_inventory"),
                ):
                    with self.assertRaises(r.RecoveryError):
                        r.restore(args)
                self.assertEqual(writes, [])
                self.assertEqual({path.name: r.digest(path) for path in args.bundle.iterdir()}, bundle_before)
                self.assertFalse((root / "never-read-fixture.env").exists())
                self.assertEqual(len(queries), len(set(queries)))
                self.assertTrue(set(queries) <= {"academic_db", "auth_db", "schedule_db"})
                self.assertLessEqual(len(mongo_queries), 1)
                if failed_target == "label":
                    self.assertEqual(queries, [])
                    self.assertEqual(mongo_queries, [])
                    self.assertEqual(inspections.count(args.schedule_container), 1)
                    self.assertEqual(len(inspections), len(set(inspections)))
                    self.assertTrue(set(inspections) <= {
                        args.academic_container, args.schedule_container, args.mongo_container,
                    })
                else:
                    self.assertCountEqual(
                        inspections, [args.academic_container, args.schedule_container, args.mongo_container]
                    )
                    if failed_target in ("auth", "schedule"):
                        self.assertEqual(queries.count(failed_target + "_db"), 1)
                    else:
                        self.assertEqual(mongo_queries, ["EMPTY_PREFLIGHT"])

    def test_auth_omission_or_unsupported_manifest_refused_before_target_access(self):
        for defect in ("auth-artifact", "auth-inventory", "unsupported-format", "foreign-project"):
            with self.subTest(defect=defect):
                root = self.retained / defect
                root.mkdir()
                args = arguments(root)
                args.bundle = make_bundle(root)
                manifest_path = args.bundle / "manifest.json"
                manifest = json.loads(manifest_path.read_text())
                if defect == "auth-artifact":
                    # Simulate a two-database bundle without deleting an artifact.
                    (args.bundle / "auth.dump").rename(root / "retained-auth.dump")
                    manifest["sha256"].pop("auth.dump")
                elif defect == "auth-inventory":
                    inventory_path = args.bundle / "inventory.json"
                    inventory = json.loads(inventory_path.read_text())
                    inventory.pop("auth")
                    inventory_path.write_text(json.dumps(inventory))
                    manifest["sha256"]["inventory.json"] = r.digest(inventory_path)
                elif defect == "unsupported-format":
                    manifest["format"] = 1
                else:
                    args.target_project = "existing-live-project"
                manifest_path.write_text(json.dumps(manifest))
                before = {path.name: r.digest(path) for path in args.bundle.iterdir()}
                with (
                    patch.object(r, "credentials", side_effect=AssertionError("Credentials must not be read")),
                    patch.object(r, "run", side_effect=AssertionError("Target access forbidden")),
                ):
                    with self.assertRaises(r.RecoveryError):
                        r.restore(args)
                self.assertEqual({path.name: r.digest(path) for path in args.bundle.iterdir()}, before)


if __name__ == "__main__":
    unittest.main()
