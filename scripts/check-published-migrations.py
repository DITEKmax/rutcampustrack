"""Keep published versioned production migrations immutable; allow new files.

Only CRLF/LF differences are ignored. Flyway 11.7.2 ChecksumCalculator reads
lines before computing its checksum, so this policy preserves that compatibility
without allowing other whitespace, encoding, BOM or SQL changes.
Trusted baseline: the published PR #2 head when the 2026-10-17 stage began.
"""

import argparse
import re
import subprocess
from pathlib import Path

PUBLISHED_REVISION = "753173688d633ca07991f4dd73bdcd2698f45d96"
MIGRATION = re.compile(r"services/(?:[^/]+/)+src/main/resources/db/migration/V[^/]+\.sql\Z")


def git(root: Path, *arguments: str) -> bytes:
    result = subprocess.run(["git", *arguments], cwd=root, capture_output=True, check=False)
    if result.returncode:
        raise ValueError("Published migration revision unavailable")
    return result.stdout


def check(root: Path, revision: str = PUBLISHED_REVISION) -> int:
    entries = git(root, "ls-tree", "-rz", "--full-tree", revision, "--", "services").split(b"\0")
    checked = 0
    for entry in entries:
        if not entry:
            continue
        metadata, raw_path = entry.split(b"\t", 1)
        relative = raw_path.decode("utf8")
        if not MIGRATION.fullmatch(relative):
            continue
        mode, kind, object_id = metadata.decode("ascii").split()
        if mode != "100644" or kind != "blob":
            raise ValueError("Published migration is not a regular source file")
        path = root
        for part in Path(relative).parts:
            path /= part
            if path.is_symlink():
                raise ValueError("Published migration path replaced by a symbolic link: " + relative)
        if not path.is_file():
            raise ValueError("Published migration missing or renamed: " + relative)
        original = git(root, "cat-file", "blob", object_id)
        if original.replace(b"\r\n", b"\n") != path.read_bytes().replace(b"\r\n", b"\n"):
            raise ValueError("Published migration content changed: " + relative)
        checked += 1
    if checked == 0:
        raise ValueError("Published migration inventory is empty")
    return checked


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--fetch-published", action="store_true", help="Fetch the exact trusted SHA only if absent")
    arguments = parser.parse_args()
    root = Path(__file__).resolve().parent.parent
    try:
        try:
            git(root, "cat-file", "-e", PUBLISHED_REVISION + "^{commit}")
        except ValueError:
            if not arguments.fetch_published:
                raise
            git(root, "fetch", "--no-tags", "--depth=1", "origin", PUBLISHED_REVISION)
        count = check(root)
    except (OSError, UnicodeError, ValueError) as error:
        # No SQL, subprocess output, credential-bearing remote URL or environment is printed.
        if isinstance(error, ValueError):
            print("FAIL: " + str(error))
        else:
            print("FAIL: Published migration sources could not be inspected")
        raise SystemExit(1) from None
    print(f"PASS: {count} published migrations preserved at {PUBLISHED_REVISION} (CRLF/LF equivalent)")


if __name__ == "__main__":
    main()
