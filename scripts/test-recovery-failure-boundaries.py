#!/usr/bin/env python3
"""Focused failure guards of the real recovery functions; no native restore claim.

DB command/inventory seams are controlled here. Filesystem effects, retained partial
bundles, manifest verification and restore preflight execute production functions.
Actual native dumps and application readback require a separate isolated lease.
"""

import argparse
import copy
import importlib.util
import json
from pathlib import Path
import tarfile
import tempfile
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
    return {
        "academic": {"schema": "", "tables": [], "sequences": []},
        "auth": {"schema": "", "tables": [], "sequences": []},
        "schedule": {"schema": "", "tables": [], "sequences": []},
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
            "format": 2,
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
                        "POSTGRES_SCHEDULE_PASSWORD", "MONGO_ROOT_PASSWORD", "POSTGRES_RECOVERY_ADMIN_PASSWORD")
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

                def pg_count(container, password, db, sql, *, user):
                    queries.append(db)
                    return "1" if db == failed_target + "_db" else "0"

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
                        "POSTGRES_SCHEDULE_PASSWORD", "MONGO_ROOT_PASSWORD", "POSTGRES_RECOVERY_ADMIN_PASSWORD")
                }
                with (
                    patch.object(r, "credentials", return_value=secret),
                    patch.object(r, "pg_query", side_effect=pg_count),
                    patch.object(
                        r, "mongo_query", return_value=([0, 1] if failed_target == "notification-mongo" else [0, 0])
                    ),
                    patch.object(r, "run", side_effect=native_write),
                    patch.object(r, "compare_restored_inventory"),
                ):
                    with self.assertRaises(r.RecoveryError):
                        r.restore(args)
                self.assertEqual(writes, [])
                self.assertEqual({path.name: r.digest(path) for path in args.bundle.iterdir()}, bundle_before)
                self.assertFalse((root / "never-read-fixture.env").exists())
                if failed_target == "label":
                    self.assertEqual(queries, [])
                    self.assertEqual(inspections, [args.academic_container, args.schedule_container])
                else:
                    self.assertEqual(queries, ["academic_db", "auth_db"] if failed_target == "auth"
                                     else ["academic_db", "auth_db", "schedule_db"])
                    self.assertEqual(
                        inspections, [args.academic_container, args.schedule_container, args.mongo_container]
                    )

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
