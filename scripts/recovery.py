#!/usr/bin/env python3
"""Explicit, immutable PG/Mongo/files backups and fresh isolated restores.

Only stdlib + Docker are required. Credentials are never included in artifacts.
There is deliberately no production restore, overwrite, retention or automatic cleanup.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import tarfile


class RecoveryError(Exception):
    pass


def require(condition, message):
    if not condition:
        raise RecoveryError(message)


def run(command, *, stdin=None, stdout=None):
    # Do not echo commands/stderr: DB tools may print credential-bearing diagnostics.
    result = subprocess.run(command, stdin=stdin, stdout=stdout or subprocess.PIPE,
                            stderr=subprocess.PIPE)
    require(result.returncode == 0, f"{Path(command[0]).name} operation failed (exit {result.returncode})")
    return result.stdout


def digest(path):
    h = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            h.update(block)
    return h.hexdigest()


def write_json(path, value):
    with path.open("x", encoding="utf-8") as stream:
        json.dump(value, stream, ensure_ascii=False, sort_keys=True, indent=2)
        stream.write("\n")


def credentials(path):
    require(path.is_file(), "Explicit credential file is missing")
    values = {}
    for line in path.read_text(encoding="utf-8-sig").splitlines():
        match = re.fullmatch(r"\s*([A-Za-z_][A-Za-z0-9_]*)=(.*)", line)
        if match:
            value = match[2]
            if len(value) >= 2 and value[0] == value[-1] and value[0] in "\"'":
                value = value[1:-1]
            values[match[1]] = value
    for key in ("POSTGRES_ACADEMIC_PASSWORD", "POSTGRES_SCHEDULE_PASSWORD", "MONGO_ROOT_PASSWORD"):
        require(values.get(key), f"Missing credential: {key}")
    return values


def pg_command(container, password, *args, stream=False):
    return ["docker", "exec", *(["-i"] if stream else []), "-e", "PGPASSWORD=" + password,
            container, *args]


def pg_query(container, password, db, sql):
    return run(pg_command(container, password, "psql", "-X", "-U", "rct_user", "-d", db,
                          "-v", "ON_ERROR_STOP=1", "-At", "-c", sql)).decode().strip()


def mongo_command(container, password, tool, args, stream=False):
    # Password expansion happens inside the container, without interpolation into shell code.
    return ["docker", "exec", *(["-i"] if stream else []), "-e", "RCT_MONGO_PASSWORD=" + password,
            container, "sh", "-c", 'exec "$@" --host localhost:27017 --username root '
            '--authenticationDatabase admin --password "$RCT_MONGO_PASSWORD"', "rct-recovery", tool, *args]


def mongo_query(container, password, js):
    return json.loads(run(mongo_command(container, password, "mongosh", ["--quiet", "--eval", js])))


def file_inventory(root):
    require(root.is_dir() and not root.is_symlink(), "Files source must be a real directory")
    entries = []
    for path in sorted(root.rglob("*")):
        require(not path.is_symlink() and (path.is_file() or path.is_dir()),
                "Files source must contain only regular files/directories (no links/devices)")
        item = {"path": path.relative_to(root).as_posix(), "directory": path.is_dir()}
        if path.is_file():
            item.update(size=path.stat().st_size, sha256=digest(path))
        entries.append(item)
    return entries


def inventory(args, secret):
    result = {}
    for label, container, db, key in (
        ("academic", args.academic_container, "academic_db", "POSTGRES_ACADEMIC_PASSWORD"),
        ("schedule", args.schedule_container, "schedule_db", "POSTGRES_SCHEDULE_PASSWORD"),
    ):
        password = secret[key]
        tables = json.loads(pg_query(container, password, db, """
            SELECT coalesce(json_agg(json_build_object('schema',schemaname,'name',tablename)
              ORDER BY schemaname,tablename),'[]'::json) FROM pg_tables
            WHERE schemaname NOT IN ('pg_catalog','information_schema') AND schemaname !~ '^pg_toast';
        """))
        data = []
        for table in tables:
            quoted = '.'.join('"' + table[k].replace('"', '""') + '"' for k in ("schema", "name"))
            rows = pg_query(container, password, db,
                            f"SELECT coalesce(jsonb_agg(to_jsonb(t) ORDER BY to_jsonb(t)::text),'[]'::jsonb) FROM {quoted} t")
            data.append({**table, "rows": json.loads(rows)})
        # SQL schema includes constraints/indexes/sequences. Remove only pg_dump's
        # volatile metadata/restrict nonce, preserving all executable definitions.
        schema = run(pg_command(container, password, "pg_dump", "-U", "rct_user", "-d", db,
                                "--schema-only", "--no-owner", "--no-privileges")).decode()
        schema = '\n'.join(line for line in schema.splitlines()
                           if line and not line.startswith("--") and not line.startswith("\\restrict")
                           and not line.startswith("\\unrestrict"))
        sequences = json.loads(pg_query(container, password, db, """
            SELECT coalesce(json_agg(json_build_object('schema',schemaname,'name',sequencename)
            ORDER BY schemaname,sequencename),'[]'::json) FROM pg_sequences
            WHERE schemaname NOT IN ('pg_catalog','information_schema');
        """))
        for seq in sequences:
            quoted = '.'.join('"' + seq[k].replace('"', '""') + '"' for k in ("schema", "name"))
            seq["state"] = json.loads(pg_query(container, password, db,
                f"SELECT row_to_json(t) FROM (SELECT last_value,is_called FROM {quoted}) t"))
        result[label] = {"schema": schema, "tables": data, "sequences": sequences}
    result["mongo"] = mongo_query(args.mongo_container, secret["MONGO_ROOT_PASSWORD"], r'''
        function canonical(v) {
          if (Array.isArray(v)) return v.map(canonical);
          if (v && typeof v === 'object') return Object.fromEntries(Object.keys(v).sort().map(k=>[k,canonical(v[k])]));
          return v;
        }
        let output = {};
        for (const name of ['attendance_db','notification_db']) {
          const d=db.getSiblingDB(name); output[name]={};
          for (const c of d.getCollectionInfos().sort((a,b)=>a.name.localeCompare(b.name))) {
            const coll=d.getCollection(c.name);
            output[name][c.name]={options:EJSON.stringify(c.options,{relaxed:false}),type:c.type,
              documents:coll.find().toArray().map(v=>EJSON.stringify(v,{relaxed:false})).sort(),
              indexes:coll.getIndexes().map(v=>({...canonical(EJSON.serialize(v,{relaxed:false})),
                key:Object.entries(EJSON.serialize(v.key,{relaxed:false}))})).sort((a,b)=>a.name.localeCompare(b.name))};
          }
        }
        print(JSON.stringify(output));
    ''')
    result["files"] = file_inventory(args.files_dir)
    return result


PAYLOADS = {"academic.dump", "schedule.dump", "attendance.archive", "notification.archive", "files.tar", "inventory.json"}


def verify_bundle(bundle):
    require(bundle.is_dir() and not bundle.is_symlink(), "Bundle must be a real directory")
    require({p.name for p in bundle.iterdir()} == PAYLOADS | {"manifest.json"},
            "Bundle incomplete or contains unexpected artifacts")
    for path in bundle.iterdir():
        require(path.is_file() and not path.is_symlink(), "Bundle artifacts must be regular files")
    manifest = json.loads((bundle / "manifest.json").read_text(encoding="utf-8"))
    require(manifest.get("format") == 1 and set(manifest.get("sha256", {})) == PAYLOADS,
            "Unsupported/incomplete manifest")
    for name, expected in manifest["sha256"].items():
        require(digest(bundle / name) == expected, f"Integrity check failed: {name}")
    expected = json.loads((bundle / "inventory.json").read_text(encoding="utf-8"))
    with tarfile.open(bundle / "files.tar", "r:") as archive:
        seen = set()
        for member in archive.getmembers():
            parts = member.name.split("/")
            require(member.name not in seen and member.name and not member.name.startswith("/")
                    and not any(p in ("", ".", "..") for p in parts) and "\\" not in member.name
                    and ":" not in member.name and (member.isfile() or member.isdir()),
                    "Unsafe files archive member")
            seen.add(member.name)
        require(seen == {entry["path"] for entry in expected["files"]}, "Files archive inventory mismatch")
    return expected


def check_targets(args, restore=False):
    names = [args.academic_container, args.schedule_container, args.mongo_container]
    require(len(set(names)) == 3, "Three distinct DB containers are required")
    for name in names:
        info = json.loads(run(["docker", "inspect", name]))[0]
        require(info["State"]["Running"], "DB target is not running")
        if restore:
            labels = info["Config"].get("Labels", {}) or {}
            require(labels.get("io.rutcampustrack.recovery") == "disposable"
                    and labels.get("com.docker.compose.project") == args.target_project,
                    "Restore accepts only the explicit labeled disposable project")


def backup(args):
    require(args.quiesced, "Stop application writers first; acknowledge using --quiesced")
    require(not args.output.exists(), "Backup output already exists; choose a new path")
    require(not args.files_dir.is_symlink(), "Files directory must not be a symlink")
    require(not args.output.resolve().is_relative_to(args.files_dir.resolve()), "Backup cannot live within files source")
    if args.dry_run:
        print("PLAN backup: explicit three containers; two PG DBs; two Mongo DBs; files; new immutable bundle; no deletion")
        return
    secret = credentials(args.env_file)
    check_targets(args)
    before = inventory(args, secret)
    args.output.mkdir(parents=True, exist_ok=False, mode=0o700)
    # No success manifest on failure; partial artifacts are retained for diagnosis.
    for container, db, key, name in (
        (args.academic_container, "academic_db", "POSTGRES_ACADEMIC_PASSWORD", "academic.dump"),
        (args.schedule_container, "schedule_db", "POSTGRES_SCHEDULE_PASSWORD", "schedule.dump"),
    ):
        with (args.output / name).open("xb") as output:
            run(pg_command(container, secret[key], "pg_dump", "-U", "rct_user", "-d", db,
                           "--format=custom", "--no-owner", "--no-privileges"), stdout=output)
    for db, name in (("attendance_db", "attendance.archive"), ("notification_db", "notification.archive")):
        with (args.output / name).open("xb") as output:
            run(mongo_command(args.mongo_container, secret["MONGO_ROOT_PASSWORD"], "mongodump",
                              ["--db", db, "--archive", "--quiet"]), stdout=output)
    with tarfile.open(args.output / "files.tar", "x:") as archive:
        for entry in before["files"]:
            archive.add(args.files_dir / entry["path"], arcname=entry["path"], recursive=False)
    require(before == inventory(args, secret), "Source changed during backup; incomplete bundle retained")
    write_json(args.output / "inventory.json", before)
    write_json(args.output / "manifest.json", {"format": 1, "sha256": {n: digest(args.output / n) for n in sorted(PAYLOADS)}})
    verify_bundle(args.output)
    print("PASS backup: complete immutable PG/Mongo/files bundle")


def restore(args):
    require(re.fullmatch(r"rct-recovery-[a-z0-9-]+", args.target_project), "Target project must start rct-recovery-")
    require(not args.files_dir.exists(), "Restore files target must not exist")
    expected = verify_bundle(args.bundle)
    if args.dry_run:
        print("PASS bundle integrity; PLAN restore: validate disposable labels and all targets empty before writing; compare exact inventory")
        return
    secret = credentials(args.env_file)
    check_targets(args, restore=True)
    for container, db, key in ((args.academic_container, "academic_db", "POSTGRES_ACADEMIC_PASSWORD"),
                               (args.schedule_container, "schedule_db", "POSTGRES_SCHEDULE_PASSWORD")):
        count = pg_query(container, secret[key], db, "SELECT count(*) FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname NOT IN ('pg_catalog','information_schema') AND n.nspname !~ '^pg_toast' AND c.relkind IN ('r','p','S','v','m','f');")
        require(count == "0", "PostgreSQL target is not fresh; restore refused")
    counts = mongo_query(args.mongo_container, secret["MONGO_ROOT_PASSWORD"],
                         "print(JSON.stringify(['attendance_db','notification_db'].map(n=>db.getSiblingDB(n).getCollectionInfos().length)))")
    require(counts == [0, 0], "Mongo target is not fresh; restore refused")
    for container, db, key, name in (
        (args.academic_container, "academic_db", "POSTGRES_ACADEMIC_PASSWORD", "academic.dump"),
        (args.schedule_container, "schedule_db", "POSTGRES_SCHEDULE_PASSWORD", "schedule.dump"),
    ):
        with (args.bundle / name).open("rb") as source:
            run(pg_command(container, secret[key], "pg_restore", "-U", "rct_user", "-d", db,
                           "--exit-on-error", "--single-transaction", "--no-owner", "--no-privileges", stream=True), stdin=source)
    for db, name in (("attendance_db", "attendance.archive"), ("notification_db", "notification.archive")):
        with (args.bundle / name).open("rb") as source:
            run(mongo_command(args.mongo_container, secret["MONGO_ROOT_PASSWORD"], "mongorestore",
                              ["--archive", "--nsInclude", db + ".*", "--stopOnError", "--quiet"], stream=True), stdin=source)
    args.files_dir.mkdir(parents=True, exist_ok=False, mode=0o700)
    with tarfile.open(args.bundle / "files.tar", "r:") as archive:
        # Explicit extraction keeps links/devices/path traversal out on Python 3.9+ too.
        for member in archive.getmembers():
            path = args.files_dir / member.name
            if member.isdir():
                path.mkdir(parents=True, exist_ok=True)
            else:
                path.parent.mkdir(parents=True, exist_ok=True)
                with archive.extractfile(member) as source, path.open("xb") as output:
                    for block in iter(lambda: source.read(1024 * 1024), b""):
                        output.write(block)
    require(expected == inventory(args, secret), "Restored inventory differs; keep target isolated; do not retry on partial target")
    print("PASS restore: PostgreSQL data/schema/sequences, Mongo documents/indexes/options, files match source")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest="operation", required=True)
    for operation in ("backup", "restore"):
        p = sub.add_parser(operation)
        p.add_argument("--env-file", type=Path, required=True)
        p.add_argument("--academic-container", required=True)
        p.add_argument("--schedule-container", required=True)
        p.add_argument("--mongo-container", required=True)
        p.add_argument("--files-dir", type=Path, required=True)
        p.add_argument("--dry-run", action="store_true")
        if operation == "backup":
            p.add_argument("--output", type=Path, required=True)
            p.add_argument("--quiesced", action="store_true")
        else:
            p.add_argument("--bundle", type=Path, required=True)
            p.add_argument("--target-project", required=True)
    args = parser.parse_args()
    os.umask(0o077)
    try:
        require(all(re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9_.-]*", name) for name in
                    (args.academic_container, args.schedule_container, args.mongo_container)),
                "Container arguments must be explicit names/IDs")
        (backup if args.operation == "backup" else restore)(args)
    except (RecoveryError, OSError, ValueError, tarfile.TarError, KeyError) as error:
        # Unexpected diagnostics never include DB subprocess output/credentials.
        print(f"FAIL recovery: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
