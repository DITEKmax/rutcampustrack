"""Controls exercise the guard with synthetic committed SQL, never real databases."""

import importlib.util
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

sys.dont_write_bytecode = True
SCRIPT = Path(__file__).resolve().parents[2] / "scripts/check-published-migrations.py"
SPEC = importlib.util.spec_from_file_location("published_migrations", SCRIPT)
if SPEC is None or SPEC.loader is None:
    raise RuntimeError("Migration guard unavailable")
GUARD = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(GUARD)


class PublishedMigrationControl(unittest.TestCase):
    def setUp(self) -> None:
        temporary = tempfile.TemporaryDirectory(prefix="rct-migration-control-")
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        self.path = self.root / "services/example/example-app/src/main/resources/db/migration/V1__published.sql"
        self.path.parent.mkdir(parents=True)
        self.path.write_bytes(b"CREATE TABLE synthetic (id bigint);\n-- published\n")
        for arguments in (
            ["init", "-q"],
            ["add", "."],
            [
                "-c", "user.name=Migration Control", "-c", "user.email=control@example.invalid",
                "commit", "-qm", "fixture",
            ],
        ):
            subprocess.run(["git", *arguments], cwd=self.root, capture_output=True, check=True)
        self.revision = GUARD.git(self.root, "rev-parse", "HEAD").decode().strip()

    def test_original_and_new_migrations_are_accepted(self) -> None:
        self.assertEqual(GUARD.check(self.root, self.revision), 1)
        self.path.with_name("V2__new.sql").write_bytes(b"ALTER TABLE synthetic ADD COLUMN label text;\n")
        self.assertEqual(GUARD.check(self.root, self.revision), 1)

    def test_crlf_lf_conversion_is_accepted_in_both_directions(self) -> None:
        original = self.path.read_bytes()
        self.path.write_bytes(original.replace(b"\n", b"\r\n"))
        self.assertEqual(GUARD.check(self.root, self.revision), 1)
        subprocess.run(["git", "add", "."], cwd=self.root, capture_output=True, check=True)
        subprocess.run(
            [
                "git", "-c", "user.name=Migration Control", "-c", "user.email=control@example.invalid",
                "commit", "-qm", "CRLF fixture",
            ],
            cwd=self.root,
            capture_output=True,
            check=True,
        )
        crlf_revision = GUARD.git(self.root, "rev-parse", "HEAD").decode().strip()
        self.path.write_bytes(original)
        self.assertEqual(GUARD.check(self.root, crlf_revision), 1)

    def test_changed_old_sql_is_rejected(self) -> None:
        self.path.write_bytes(b"CREATE TABLE synthetic (id text);\n-- published\n")
        with self.assertRaisesRegex(ValueError, "content changed"):
            GUARD.check(self.root, self.revision)

    def test_whitespace_only_change_is_rejected(self) -> None:
        self.path.write_bytes(self.path.read_bytes().replace(b"id bigint", b"id  bigint"))
        with self.assertRaisesRegex(ValueError, "content changed"):
            GUARD.check(self.root, self.revision)

    def test_deletion_is_rejected(self) -> None:
        self.path.unlink()
        with self.assertRaisesRegex(ValueError, "missing or renamed"):
            GUARD.check(self.root, self.revision)

    def test_rename_is_rejected_even_with_identical_sql(self) -> None:
        self.path.rename(self.path.with_name("V1__renamed.sql"))
        with self.assertRaisesRegex(ValueError, "missing or renamed"):
            GUARD.check(self.root, self.revision)

    def test_symlink_to_identical_bytes_is_rejected(self) -> None:
        target = self.path.with_name("V2__new.sql")
        self.path.rename(target)
        self.path.symlink_to(target)
        with self.assertRaisesRegex(ValueError, "symbolic link"):
            GUARD.check(self.root, self.revision)

    def test_missing_revision_and_empty_inventory_fail_closed(self) -> None:
        with self.assertRaisesRegex(ValueError, "revision unavailable"):
            GUARD.check(self.root, "0" * 40)
        self.path.unlink()
        subprocess.run(["git", "add", "."], cwd=self.root, capture_output=True, check=True)
        subprocess.run(
            [
                "git", "-c", "user.name=Migration Control", "-c", "user.email=control@example.invalid",
                "commit", "-qm", "empty fixture",
            ],
            cwd=self.root,
            capture_output=True,
            check=True,
        )
        empty_revision = GUARD.git(self.root, "rev-parse", "HEAD").decode().strip()
        with self.assertRaisesRegex(ValueError, "inventory is empty"):
            GUARD.check(self.root, empty_revision)


if __name__ == "__main__":
    unittest.main()
