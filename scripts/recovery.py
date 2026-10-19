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
import uuid


class RecoveryError(Exception):
    def __init__(self, message, hint="GUARD"):
        super().__init__(message)
        self.hint = hint


BUNDLE_FORMAT = 3  # Exact owners/ACL/default privileges are mandatory before any restore write.
FAILURE_HINTS = frozenset({
    "GUARD", "INPUT_CREDENTIAL", "PG_QUERY", "PG_SCHEMA", "PG_DUMP", "PG_RESTORE",
    "MONGO_QUERY", "MONGO_DUMP", "MONGO_RESTORE", "INSPECT", "OTHER_TOOL",
    "RESTORE_DIFF_TABLES", "RESTORE_DIFF_SEQUENCES", "RESTORE_DIFF_ACCESS_OBJECT_SET",
    "RESTORE_DIFF_ACCESS_OWNER", "RESTORE_DIFF_ACCESS_ACL", "RESTORE_DIFF_ACCESS_ORDER",
    "RESTORE_DIFF_OTHER_SHAPE", "RESTORE_DIFF_ACCESS_ACL_DATABASE",
    "RESTORE_DIFF_ACCESS_ACL_SCHEMA", "RESTORE_DIFF_ACCESS_ACL_RELATION",
    "RESTORE_DIFF_ACCESS_ACL_COLUMN", "RESTORE_DIFF_ACCESS_ACL_FUNCTION",
    "RESTORE_DIFF_ACCESS_ACL_TYPE", "RESTORE_DIFF_ACCESS_ACL_DEFAULT",
})


def require(condition, message):
    if not condition:
        raise RecoveryError(message)


def run(command, *, stdin=None, stdout=None, input_data=None):
    # Do not echo commands/stderr: DB tools may print credential-bearing diagnostics.
    result = subprocess.run(
        command, stdin=stdin, input=input_data, stdout=stdout or subprocess.PIPE, stderr=subprocess.PIPE
    )
    if result.returncode:
        hint = "OTHER_TOOL"
        if command[:2] == ["docker", "inspect"]:
            hint = "INSPECT"
        elif command[:2] == ["docker", "exec"]:
            for tool, code in (("psql", "PG_QUERY"), ("pg_dump", "PG_DUMP"),
                               ("pg_restore", "PG_RESTORE"), ("mongosh", "MONGO_QUERY"),
                               ("mongodump", "MONGO_DUMP"), ("mongorestore", "MONGO_RESTORE")):
                if tool in command:
                    hint = "PG_SCHEMA" if tool == "pg_dump" and "--schema-only" in command else code
                    break
        raise RecoveryError(f"{Path(command[0]).name} operation failed (exit {result.returncode})", hint)
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
    for key in (
        "POSTGRES_ACADEMIC_MIGRATION_PASSWORD", "POSTGRES_AUTH_MIGRATION_PASSWORD",
        "POSTGRES_SCHEDULE_PASSWORD", "MONGO_ROOT_PASSWORD",
        "POSTGRES_RECOVERY_ADMIN_USER", "POSTGRES_RECOVERY_ADMIN_PASSWORD",
    ):
        if not values.get(key):
            raise RecoveryError(f"Missing credential: {key}", "INPUT_CREDENTIAL")
    require(values["POSTGRES_RECOVERY_ADMIN_USER"] in ("rct_user", "rct_bootstrap"),
            "Unsupported recovery admin identity")
    return values


def pg_targets(args):
    # Auth and Academic share a process, never a database or owner credential.
    return (
        ("academic", args.academic_container, "academic_db", "POSTGRES_ACADEMIC_MIGRATION_PASSWORD", "rct_migrator"),
        ("auth", args.academic_container, "auth_db", "POSTGRES_AUTH_MIGRATION_PASSWORD", "rct_auth_migrator"),
        ("schedule", args.schedule_container, "schedule_db", "POSTGRES_SCHEDULE_PASSWORD", "rct_user"),
    )


def pg_command(container, password, *args, stream=False):
    return ["docker", "exec", *(["-i"] if stream else []), "-e", "PGPASSWORD=" + password, container, *args]


def pg_query(container, password, db, sql, *, user="rct_user"):
    return (
        run(
            pg_command(
                container, password, "psql", "-X", "-U", user, "-d", db, "-v", "ON_ERROR_STOP=1", "-At", "-c", sql
            )
        )
        .decode()
        .strip()
    )


def pg_schema(container, password, db, *, user="rct_user"):
    # DDL-only native scratch comparison changes object owner. Access is compared separately
    # against the original named owners/ACLs; real archive/restore never discards privileges.
    schema = run(
        pg_command(
            container, password, "pg_dump", "-U", user, "-d", db, "--schema-only", "--no-owner", "--no-privileges"
        )
    ).decode()
    return "\n".join(
        line
        for line in schema.splitlines()
        if line
        and not line.startswith("--")
        and not line.startswith("\\restrict")
        and not line.startswith("\\unrestrict")
    )


def pg_access(container, password, db, *, user):
    # Catalog ACL text contains role names, never OIDs or passwords. Array order is not
    # an authority fact; NULL (default ACL) remains distinct from an explicit empty ACL.
    # In the supported UTF8 inventory, C ordering matches Python Unicode ordering.
    # Canonicalize both ACL values and object order independently of the DB locale.
    value = json.loads(pg_query(container, password, db, """
WITH namespaces AS (
  SELECT oid,nspname,nspowner,nspacl FROM pg_namespace
  WHERE nspname NOT IN ('pg_catalog','information_schema') AND nspname !~ '^pg_'
), objects(kind,name,owner,acl) AS (
  SELECT 'DATABASE',current_database(),datdba,datacl FROM pg_database
    WHERE datname=current_database()
  UNION ALL SELECT 'SCHEMA',nspname,nspowner,nspacl FROM namespaces
  UNION ALL SELECT 'RELATION_'||c.relkind::text,quote_ident(n.nspname)||'.'||quote_ident(c.relname),c.relowner,c.relacl
    FROM pg_class c JOIN namespaces n ON n.oid=c.relnamespace
    WHERE c.relkind IN ('r','p','S','v','m','f')
  UNION ALL SELECT 'COLUMN',quote_ident(n.nspname)||'.'||quote_ident(c.relname)||'.'||quote_ident(a.attname),c.relowner,a.attacl
    FROM pg_attribute a JOIN pg_class c ON c.oid=a.attrelid
    JOIN namespaces n ON n.oid=c.relnamespace WHERE a.attnum>0 AND NOT a.attisdropped AND a.attacl IS NOT NULL
  UNION ALL SELECT 'FUNCTION_'||p.prokind::text,quote_ident(n.nspname)||'.'||quote_ident(p.proname)||'('||pg_get_function_identity_arguments(p.oid)||')',p.proowner,p.proacl
    FROM pg_proc p JOIN namespaces n ON n.oid=p.pronamespace
  UNION ALL SELECT 'TYPE_'||t.typtype::text,quote_ident(n.nspname)||'.'||quote_ident(t.typname),t.typowner,t.typacl
    FROM pg_type t JOIN namespaces n ON n.oid=t.typnamespace
  UNION ALL SELECT 'DEFAULT_'||d.defaclobjtype::text,COALESCE(n.nspname,'*'),d.defaclrole,d.defaclacl
    FROM pg_default_acl d LEFT JOIN pg_namespace n ON n.oid=d.defaclnamespace
), named AS (
  SELECT kind,name,pg_get_userbyid(owner) AS owner,
    CASE WHEN acl IS NULL THEN NULL ELSE
      COALESCE((SELECT jsonb_agg(a::text ORDER BY a::text COLLATE "C") FROM unnest(acl) a),'[]'::jsonb) END AS acl
  FROM objects
)
SELECT json_build_object('format',1,'objects',
  COALESCE((SELECT jsonb_agg(to_jsonb(o)
    ORDER BY kind COLLATE "C",name COLLATE "C",owner COLLATE "C") FROM named o),'[]'::jsonb),
  'unsupported',EXISTS(SELECT 1 FROM pg_class c JOIN namespaces n ON n.oid=c.relnamespace
    WHERE c.relkind NOT IN ('r','p','S','v','m','f','i','I','c','t'))
    OR EXISTS(SELECT 1 FROM pg_foreign_server)
    OR EXISTS(SELECT 1 FROM pg_foreign_data_wrapper)
    OR EXISTS(SELECT 1 FROM pg_largeobject_metadata));
""", user=user))
    validate_access(value)
    return value


def validate_access(value):
    kinds = {"DATABASE", "SCHEMA", "COLUMN", *("RELATION_" + k for k in "rpSvmf"),
             *("FUNCTION_" + k for k in "fpaw"), *("TYPE_" + k for k in "bcdempr"),
             *("DEFAULT_" + k for k in "rSfTn")}
    require(type(value) is dict and set(value) == {"format", "objects", "unsupported"}
            and type(value["format"]) is int and value["format"] == 1 and value["unsupported"] is False
            and type(value["objects"]) is list, "Unsupported/incomplete PostgreSQL access inventory")
    seen = set()
    for row in value["objects"]:
        require(type(row) is dict and set(row) == {"kind", "name", "owner", "acl"}
                and type(row["kind"]) is str and row["kind"] in kinds
                and all(type(row[k]) is str and row[k] for k in ("name", "owner"))
                and (row["acl"] is None or type(row["acl"]) is list
                     and all(type(item) is str for item in row["acl"]) and row["acl"] == sorted(row["acl"])),
                "Unsupported PostgreSQL access object")
        identity = (row["kind"], row["name"], row["owner"])
        require(identity not in seen, "Duplicate PostgreSQL access object")
        seen.add(identity)
    require(sum(row["kind"] == "DATABASE" for row in value["objects"]) == 1,
            "PostgreSQL access inventory must include its database")


def canonical_reference_schema(container, password, db, schema, *, user="rct_user"):
    # PostgreSQL reparses dump expressions (casts/AND grouping). Compare its own
    # deparsed reference, never discard constraints/indexes or normalize SQL text.
    require(user in ("rct_user", "rct_bootstrap"), "Unsupported recovery admin identity")
    def query(database, sql):
        return pg_query(container, password, database, sql, user=user)

    scratch = "rct_recovery_schema_" + uuid.uuid4().hex
    require(
        query("postgres", f"SELECT count(*) FROM pg_database WHERE datname='{scratch}'") == "0",
        "Schema reference database already exists",
    )
    timezone = query(db, "SHOW TimeZone").replace("'", "''")
    query("postgres", f'CREATE DATABASE "{scratch}" TEMPLATE template0 OWNER "{user}"')
    identity = query(
        "postgres",
        f"SELECT oid FROM pg_database WHERE datname='{scratch}' "
        f"AND datdba=(SELECT oid FROM pg_roles WHERE rolname='{user}')",
    )
    require(identity.isdigit(), "Created schema reference identity is missing")
    try:
        query("postgres", f"ALTER DATABASE \"{scratch}\" SET TimeZone TO '{timezone}'")
        run(
            pg_command(
                container,
                password,
                "psql",
                "-X",
                "-U",
                user,
                "-d",
                scratch,
                "-v",
                "ON_ERROR_STOP=1",
                "--single-transaction",
                stream=True,
            ),
            input_data=schema.encode("utf-8"),
        )
        return pg_schema(container, password, scratch, user=user)
    finally:
        require(
            query(
                "postgres",
                f"SELECT oid FROM pg_database WHERE datname='{scratch}' "
                f"AND datdba=(SELECT oid FROM pg_roles WHERE rolname='{user}')",
            )
            == identity,
            "Schema reference identity changed; cleanup refused",
        )
        query("postgres", f'DROP DATABASE "{scratch}"')


def compare_restored_inventory(args, secret, expected):
    actual = inventory(args, secret)
    for label, container, db, key, user in pg_targets(args):
        try:
            require(
                {k: v for k, v in expected[label].items() if k != "schema"}
                == {k: v for k, v in actual[label].items() if k != "schema"},
                f"Restored {label} data/sequences/owners/access differ; keep target isolated",
            )
        except RecoveryError as mismatch:
            hint = "RESTORE_DIFF_OTHER_SHAPE"
            before, after = expected[label], actual[label]
            if (set(before) == set(after) == {"schema", "tables", "sequences", "access"}
                    and all(type(value[field]) is list
                            for value in (before, after) for field in ("tables", "sequences"))):
                if before["tables"] != after["tables"]:
                    hint = "RESTORE_DIFF_TABLES"
                elif before["sequences"] != after["sequences"]:
                    hint = "RESTORE_DIFF_SEQUENCES"
                elif before["access"] != after["access"]:
                    try:
                        validate_access(before["access"])
                        validate_access(after["access"])
                    except RecoveryError:
                        pass
                    else:
                        before_objects = before["access"]["objects"]
                        after_objects = after["access"]["objects"]
                        if (sorted((row["kind"], row["name"]) for row in before_objects)
                                != sorted((row["kind"], row["name"]) for row in after_objects)):
                            hint = "RESTORE_DIFF_ACCESS_OBJECT_SET"
                        else:
                            before_acl = {(row["kind"], row["name"], row["owner"]): row["acl"]
                                          for row in before_objects}
                            after_acl = {(row["kind"], row["name"], row["owner"]): row["acl"]
                                         for row in after_objects}
                            if before_acl.keys() != after_acl.keys():
                                hint = "RESTORE_DIFF_ACCESS_OWNER"
                            elif before_acl != after_acl:
                                kind = next(key[0] for key in sorted(before_acl)
                                            if before_acl[key] != after_acl[key])
                                hint = {
                                    "DATABASE": "RESTORE_DIFF_ACCESS_ACL_DATABASE",
                                    "SCHEMA": "RESTORE_DIFF_ACCESS_ACL_SCHEMA",
                                    "RELATION": "RESTORE_DIFF_ACCESS_ACL_RELATION",
                                    "COLUMN": "RESTORE_DIFF_ACCESS_ACL_COLUMN",
                                    "FUNCTION": "RESTORE_DIFF_ACCESS_ACL_FUNCTION",
                                    "TYPE": "RESTORE_DIFF_ACCESS_ACL_TYPE",
                                    "DEFAULT": "RESTORE_DIFF_ACCESS_ACL_DEFAULT",
                                }.get(kind.split("_", 1)[0], "RESTORE_DIFF_ACCESS_ACL")
                            else:
                                hint = "RESTORE_DIFF_ACCESS_ORDER"
            mismatch.hint = hint
            raise
        require(
            canonical_reference_schema(
                container,
                secret[key] if label == "schedule" else secret["POSTGRES_RECOVERY_ADMIN_PASSWORD"],
                db, expected[label]["schema"],
                user="rct_user" if label == "schedule" else secret["POSTGRES_RECOVERY_ADMIN_USER"],
            )
            == actual[label]["schema"],
            f"Restored {label} schema differs from PostgreSQL reference; keep target isolated",
        )
    require(
        {k: v for k, v in expected.items() if k not in ("academic", "auth", "schedule")}
        == {k: v for k, v in actual.items() if k not in ("academic", "auth", "schedule")},
        "Restored Mongo/files inventory differs; keep target isolated",
    )


def mongo_command(container, password, tool, args, stream=False):
    # Password expansion happens inside the container, without interpolation into shell code.
    return [
        "docker",
        "exec",
        *(["-i"] if stream else []),
        "-e",
        "RCT_MONGO_PASSWORD=" + password,
        container,
        "sh",
        "-c",
        'exec "$@" --host localhost:27017 --username root '
        '--authenticationDatabase admin --password "$RCT_MONGO_PASSWORD"',
        "rct-recovery",
        tool,
        *args,
    ]


def mongo_query(container, password, js):
    return json.loads(run(mongo_command(container, password, "mongosh", ["--quiet", "--eval", js])))


def file_inventory(root):
    require(root.is_dir() and not root.is_symlink(), "Files source must be a real directory")
    entries = []
    for path in sorted(root.rglob("*")):
        require(
            not path.is_symlink() and (path.is_file() or path.is_dir()),
            "Files source must contain only regular files/directories (no links/devices)",
        )
        item = {"path": path.relative_to(root).as_posix(), "directory": path.is_dir()}
        if path.is_file():
            item.update(size=path.stat().st_size, sha256=digest(path))
        entries.append(item)
    return entries


def file_mode(args):
    no_files = getattr(args, "no_files", False)
    require(no_files != (args.files_dir is not None), "Choose exactly one of --no-files or --files-dir")
    return "database-only" if no_files else "files"


def inventory(args, secret):
    result = {}
    for label, container, db, key, user in pg_targets(args):
        password = secret[key]
        tables = json.loads(
            pg_query(
                container,
                password,
                db,
                """
            SELECT coalesce(json_agg(json_build_object('schema',schemaname,'name',tablename)
              ORDER BY schemaname,tablename),'[]'::json) FROM pg_tables
            WHERE schemaname NOT IN ('pg_catalog','information_schema') AND schemaname !~ '^pg_toast';
        """,
                user=user,
            )
        )
        data = []
        for table in tables:
            quoted = ".".join('"' + table[k].replace('"', '""') + '"' for k in ("schema", "name"))
            rows = pg_query(
                container,
                password,
                db,
                f"SELECT coalesce(jsonb_agg(to_jsonb(t) ORDER BY to_jsonb(t)::text),'[]'::jsonb) FROM {quoted} t",
                user=user,
            )
            data.append({**table, "rows": json.loads(rows)})
        schema = pg_schema(container, password, db, user=user)
        sequences = json.loads(
            pg_query(
                container,
                password,
                db,
                """
            SELECT coalesce(json_agg(json_build_object('schema',schemaname,'name',sequencename)
            ORDER BY schemaname,sequencename),'[]'::json) FROM pg_sequences
            WHERE schemaname NOT IN ('pg_catalog','information_schema');
        """,
                user=user,
            )
        )
        for seq in sequences:
            quoted = ".".join('"' + seq[k].replace('"', '""') + '"' for k in ("schema", "name"))
            seq["state"] = json.loads(
                pg_query(
                    container, password, db, f"SELECT row_to_json(t) FROM (SELECT last_value,is_called FROM {quoted}) t", user=user
                )
            )
        result[label] = {"schema": schema, "tables": data, "sequences": sequences,
                         "access": pg_access(container, password, db, user=user)}
    result["mongo"] = mongo_query(
        args.mongo_container,
        secret["MONGO_ROOT_PASSWORD"],
        r"""
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
    """,
    )
    result["files"] = [] if file_mode(args) == "database-only" else file_inventory(args.files_dir)
    return result


PAYLOADS = {
    "auth.dump",
    "academic.dump",
    "schedule.dump",
    "attendance.archive",
    "notification.archive",
    "files.tar",
    "inventory.json",
}


def verify_bundle(bundle, requested_mode=None):
    require(bundle.is_dir() and not bundle.is_symlink(), "Bundle must be a real directory")
    require(
        {p.name for p in bundle.iterdir()} == PAYLOADS | {"manifest.json"},
        "Bundle incomplete or contains unexpected artifacts",
    )
    for path in bundle.iterdir():
        require(path.is_file() and not path.is_symlink(), "Bundle artifacts must be regular files")
    manifest = json.loads((bundle / "manifest.json").read_text(encoding="utf-8"))
    require(
        manifest.get("format") == BUNDLE_FORMAT and set(manifest.get("sha256", {})) == PAYLOADS,
        "Unsupported/incomplete manifest; format 3 with owner/ACL evidence is required"
    )
    mode = manifest.get("files_mode")
    require(mode in ("files", "database-only"), "Unsupported files mode in manifest")
    require(
        requested_mode is None or requested_mode == mode,
        "Restore files mode does not match bundle; refused before target writes",
    )
    for name, expected in manifest["sha256"].items():
        require(digest(bundle / name) == expected, f"Integrity check failed: {name}")
    expected = json.loads((bundle / "inventory.json").read_text(encoding="utf-8"))
    require(set(expected) == {"academic", "auth", "schedule", "mongo", "files"},
            "Incomplete/unexpected database inventory")
    for label in ("academic", "auth", "schedule"):
        require(type(expected[label]) is dict and "access" in expected[label],
                "Bundle lacks mandatory PostgreSQL access evidence; a new format-3 backup is required")
        validate_access(expected[label]["access"])
    require(mode != "database-only" or expected["files"] == [], "Database-only bundle declares files; restore refused")
    with tarfile.open(bundle / "files.tar", "r:") as archive:
        seen = set()
        for member in archive.getmembers():
            parts = member.name.split("/")
            require(
                member.name not in seen
                and member.name
                and not member.name.startswith("/")
                and not any(p in ("", ".", "..") for p in parts)
                and "\\" not in member.name
                and ":" not in member.name
                and (member.isfile() or member.isdir()),
                "Unsafe files archive member",
            )
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
            require(
                labels.get("io.rutcampustrack.recovery") == "disposable"
                and labels.get("com.docker.compose.project") == args.target_project,
                "Restore accepts only the explicit labeled disposable project",
            )


def backup(args):
    mode = file_mode(args)
    require(args.quiesced, "Stop application writers first; acknowledge using --quiesced")
    require(not args.output.exists(), "Backup output already exists; choose a new path")
    if mode == "files":
        require(not args.files_dir.is_symlink(), "Files directory must not be a symlink")
        require(
            not args.output.resolve().is_relative_to(args.files_dir.resolve()), "Backup cannot live within files source"
        )
    if args.dry_run:
        print(
            f"PLAN backup: explicit three containers; three PG DBs (Academic/Auth share one container); two Mongo DBs; mode={mode}; new immutable bundle; no deletion"
        )
        return
    secret = credentials(args.env_file)
    check_targets(args)
    before = inventory(args, secret)
    args.output.mkdir(parents=True, exist_ok=False, mode=0o700)
    # No success manifest on failure; partial artifacts are retained for diagnosis.
    for label, container, db, key, user in pg_targets(args):
        name = label + ".dump"
        with (args.output / name).open("xb") as output:
            run(
                pg_command(
                    container,
                    secret[key],
                    "pg_dump",
                    "-U",
                    user,
                    "-d",
                    db,
                    "--format=custom",
                    "--no-owner",
                ),
                stdout=output,
            )
    for db, name in (("attendance_db", "attendance.archive"), ("notification_db", "notification.archive")):
        with (args.output / name).open("xb") as output:
            run(
                mongo_command(
                    args.mongo_container,
                    secret["MONGO_ROOT_PASSWORD"],
                    "mongodump",
                    ["--db", db, "--archive", "--quiet"],
                ),
                stdout=output,
            )
    with tarfile.open(args.output / "files.tar", "x:") as archive:
        for entry in before["files"]:
            archive.add(args.files_dir / entry["path"], arcname=entry["path"], recursive=False)
    require(before == inventory(args, secret), "Source changed during backup; incomplete bundle retained")
    write_json(args.output / "inventory.json", before)
    write_json(
        args.output / "manifest.json",
        {"format": BUNDLE_FORMAT, "files_mode": mode, "sha256": {n: digest(args.output / n) for n in sorted(PAYLOADS)}},
    )
    verify_bundle(args.output, mode)
    print(f"PASS backup: complete immutable format-3 PG/Mongo bundle with owner/ACL evidence; mode={mode}")


def restore(args):
    mode = file_mode(args)
    require(re.fullmatch(r"rct-recovery-[a-z0-9-]+", args.target_project), "Target project must start rct-recovery-")
    expected = verify_bundle(args.bundle, mode)
    if mode == "files":
        require(not args.files_dir.exists(), "Restore files target must not exist")
    if args.dry_run:
        print(
            "PASS bundle integrity; PLAN restore: validate disposable labels and all targets empty before writing; compare exact inventory"
        )
        return
    secret = credentials(args.env_file)
    check_targets(args, restore=True)
    for label, container, db, key, user in pg_targets(args):
        count = pg_query(
            container,
            secret[key],
            db,
            "SELECT count(*) FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname NOT IN ('pg_catalog','information_schema') AND n.nspname !~ '^pg_toast' AND c.relkind IN ('r','p','S','v','m','f');",
            user=user,
        )
        require(count == "0", "PostgreSQL target is not fresh; restore refused")
    counts = mongo_query(
        args.mongo_container,
        secret["MONGO_ROOT_PASSWORD"],
        "print(JSON.stringify(['attendance_db','notification_db'].map(n=>db.getSiblingDB(n).getCollectionInfos().length)))",
    )
    require(counts == [0, 0], "Mongo target is not fresh; restore refused")
    # pg_dump object ACLs are relative to built-in defaults, not target role defaults.
    # Per-schema defaults are additive: empty only this owner's existing bootstrap
    # table/sequence slots before CREATE; the archive restores source DEFAULT ACLs later.
    for label, container, db, key, user in pg_targets(args):
        # The database owner can differ from the migration/schema owner. Require
        # the exact source database identity without widening default-ACL scope.
        access_hex = json.dumps(expected[label]["access"]["objects"], ensure_ascii=True).encode("utf-8").hex()
        pg_query(container, secret[key], db, f"""
DO $rct_recovery_defaults$
DECLARE
  source_access jsonb := convert_from(decode('{access_hex}','hex'),'UTF8')::jsonb;
  owner_oid oid := current_user::regrole::oid;
  slot record;
BEGIN
  IF NOT EXISTS (
       SELECT 1 FROM pg_database d
       JOIN LATERAL jsonb_array_elements(source_access) source_row(value)
         ON source_row.value->>'kind'='DATABASE'
         AND source_row.value->>'name'=d.datname
         AND source_row.value->>'owner'=pg_get_userbyid(d.datdba)
       WHERE d.datname=current_database()
     )
     OR EXISTS (
       SELECT 1 FROM pg_default_acl d
       LEFT JOIN pg_namespace n ON n.oid=d.defaclnamespace
       WHERE d.defaclrole<>owner_oid
         OR d.defaclnamespace=0 OR d.defaclobjtype NOT IN ('r','S')
         OR (n.nspowner IS DISTINCT FROM owner_oid
             AND n.nspowner IS DISTINCT FROM 'pg_database_owner'::regrole::oid)
         OR n.nspname IN ('pg_catalog','information_schema') OR n.nspname ~ '^pg_'
     ) THEN
    RAISE EXCEPTION 'Unsupported PostgreSQL target default privileges';
  END IF;
  FOR slot IN
    SELECT DISTINCT n.nspname,
      CASE d.defaclobjtype WHEN 'r' THEN 'TABLES' WHEN 'S' THEN 'SEQUENCES' END AS object_type,
      a.grantee
    FROM pg_default_acl d JOIN pg_namespace n ON n.oid=d.defaclnamespace
    CROSS JOIN LATERAL aclexplode(d.defaclacl) a
    WHERE d.defaclrole=owner_oid
    ORDER BY 1,2,3
  LOOP
    EXECUTE format(
      'ALTER DEFAULT PRIVILEGES FOR ROLE %I IN SCHEMA %I REVOKE ALL PRIVILEGES ON %s FROM %s RESTRICT',
      current_user, slot.nspname, slot.object_type,
      CASE WHEN slot.grantee=0 THEN 'PUBLIC' ELSE format('%I',pg_get_userbyid(slot.grantee)) END
    );
  END LOOP;
  IF EXISTS (SELECT 1 FROM pg_default_acl WHERE defaclrole=owner_oid) THEN
    RAISE EXCEPTION 'PostgreSQL target default privileges were not reset';
  END IF;
END;
$rct_recovery_defaults$;
""", user=user)
    for label, container, db, key, user in pg_targets(args):
        name = label + ".dump"
        with (args.bundle / name).open("rb") as source:
            run(
                pg_command(
                    container,
                    secret[key],
                    "pg_restore",
                    "-U",
                    user,
                    "-d",
                    db,
                    "--exit-on-error",
                    "--single-transaction",
                    "--no-owner",
                    stream=True,
                ),
                stdin=source,
            )
        # GRANT to the current owner is effective-rights neutral when target ACL is
        # NULL. Materialize only an explicitly stored, identical source built-in ACL.
        # Hex keeps inventory identifiers/ACL text out of SQL and dollar-quote syntax.
        access_hex = json.dumps(expected[label]["access"]["objects"], ensure_ascii=True).encode("utf-8").hex()
        pg_query(container, secret[key], db, f"""
DO $rct_recovery_owner_acl$
DECLARE
  source_access jsonb := convert_from(decode('{access_hex}','hex'),'UTF8')::jsonb;
  slot record;
BEGIN
  FOR slot IN
    SELECT n.nspname, c.relname,
      CASE WHEN c.relkind='S' THEN 'SEQUENCE' ELSE 'TABLE' END AS object_type
    FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
    JOIN LATERAL jsonb_array_elements(source_access) source_row(value)
      ON source_row.value->>'kind'='RELATION_'||c.relkind::text
      AND source_row.value->>'name'=quote_ident(n.nspname)||'.'||quote_ident(c.relname)
      AND source_row.value->>'owner'=current_user
    WHERE c.relkind IN ('r','p','S','v','m','f')
      AND c.relowner=current_user::regrole::oid AND c.relacl IS NULL
      AND n.nspname NOT IN ('pg_catalog','information_schema') AND n.nspname !~ '^pg_'
      AND source_row.value->'acl'=(
        SELECT jsonb_agg(a::text ORDER BY a::text COLLATE "C")
        FROM unnest(acldefault(
          CASE WHEN c.relkind='S' THEN 's'::"char" ELSE 'r'::"char" END,c.relowner)) a
      )
    ORDER BY n.nspname COLLATE "C", c.relname COLLATE "C"
  LOOP
    EXECUTE format('GRANT ALL PRIVILEGES ON %s %I.%I TO %I',
                   slot.object_type, slot.nspname, slot.relname, current_user);
  END LOOP;
END;
$rct_recovery_owner_acl$;
""", user=user)
    for db, name in (("attendance_db", "attendance.archive"), ("notification_db", "notification.archive")):
        with (args.bundle / name).open("rb") as source:
            run(
                mongo_command(
                    args.mongo_container,
                    secret["MONGO_ROOT_PASSWORD"],
                    "mongorestore",
                    ["--archive", "--nsInclude", db + ".*", "--stopOnError", "--quiet"],
                    stream=True,
                ),
                stdin=source,
            )
    if mode == "files":
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
    compare_restored_inventory(args, secret, expected)
    print(f"PASS restore: PostgreSQL data/schema/sequences/owners/ACL, Mongo documents/indexes/options match source; mode={mode}")


def verify_restored(args):
    mode = file_mode(args)
    require(args.quiesced, "Keep target isolated and stop application writers; acknowledge using --quiesced")
    require(re.fullmatch(r"rct-recovery-[a-z0-9-]+", args.target_project), "Target project must start rct-recovery-")
    expected = verify_bundle(args.bundle, mode)
    if args.dry_run:
        print(
            "PASS bundle integrity; PLAN verify isolated restored targets; schema-only scratch references; no archive replay"
        )
        return
    secret = credentials(args.env_file)
    check_targets(args, restore=True)
    compare_restored_inventory(args, secret, expected)
    print(
        f"PASS verify-restored: exact data/sequences/Mongo/files and PostgreSQL-native schema references; mode={mode}"
    )


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest="operation", required=True)
    for operation in ("backup", "restore", "verify-restored"):
        p = sub.add_parser(operation)
        p.add_argument("--env-file", type=Path, required=True)
        p.add_argument("--academic-container", required=True)
        p.add_argument("--schedule-container", required=True)
        p.add_argument("--mongo-container", required=True)
        files = p.add_mutually_exclusive_group(required=True)
        files.add_argument("--files-dir", type=Path)
        files.add_argument("--no-files", action="store_true")
        p.add_argument("--dry-run", action="store_true")
        if operation == "backup":
            p.add_argument("--output", type=Path, required=True)
            p.add_argument("--quiesced", action="store_true")
        else:
            p.add_argument("--bundle", type=Path, required=True)
            p.add_argument("--target-project", required=True)
            if operation == "verify-restored":
                p.add_argument("--quiesced", action="store_true")
    args = parser.parse_args()
    os.umask(0o077)
    try:
        require(
            all(
                re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9_.-]*", name)
                for name in (args.academic_container, args.schedule_container, args.mongo_container)
            ),
            "Container arguments must be explicit names/IDs",
        )
        {"backup": backup, "restore": restore, "verify-restored": verify_restored}[args.operation](args)
    except (RecoveryError, OSError, ValueError, tarfile.TarError, KeyError) as error:
        # Unexpected diagnostics never include DB subprocess output/credentials.
        hint = getattr(error, "hint", "GUARD")
        print("RECOVERY_FAILURE_HINT:" + (hint if hint in FAILURE_HINTS else "GUARD"), file=sys.stderr)
        guard_reasons = {
            'Unsupported recovery admin identity': 'RECOVERY_ADMIN_IDENTITY',
            'Schema reference database already exists': 'SCHEMA_REFERENCE_EXISTS',
            'Created schema reference identity is missing': 'SCHEMA_REFERENCE_MISSING',
            'Schema reference identity changed; cleanup refused': 'SCHEMA_REFERENCE_CLEANUP_IDENTITY',
            'Three distinct DB containers are required': 'TARGET_CONTAINER_DISTINCTNESS',
            'DB target is not running': 'TARGET_NOT_RUNNING',
            'Restore accepts only the explicit labeled disposable project': 'TARGET_DISPOSABLE_IDENTITY',
            'Restored Mongo/files inventory differs; keep target isolated': 'MONGO_FILES_INVENTORY',
            'Explicit credential file is missing': 'CREDENTIAL_FILE_MISSING',
            'Choose exactly one of --no-files or --files-dir': 'FILE_MODE',
            'Target project must start rct-recovery-': 'TARGET_PROJECT',
            'Mongo target is not fresh; restore refused': 'MONGO_TARGET_NOT_FRESH',
            'PostgreSQL target is not fresh; restore refused': 'POSTGRES_TARGET_NOT_FRESH',
            'Unsupported/incomplete PostgreSQL access inventory': 'ACCESS_INVENTORY_SHAPE',
            'PostgreSQL access inventory must include its database': 'ACCESS_DATABASE_MISSING',
            'Unsupported PostgreSQL access object': 'ACCESS_OBJECT_SHAPE',
            'Duplicate PostgreSQL access object': 'ACCESS_OBJECT_DUPLICATE',
            'Bundle must be a real directory': 'BUNDLE_DIRECTORY',
            'Bundle incomplete or contains unexpected artifacts': 'BUNDLE_CONTENTS',
            'Unsupported/incomplete manifest; format 3 with owner/ACL evidence is required': 'BUNDLE_MANIFEST',
            'Unsupported files mode in manifest': 'BUNDLE_FILES_MODE',
            'Restore files mode does not match bundle; refused before target writes': 'BUNDLE_MODE_MISMATCH',
            'Incomplete/unexpected database inventory': 'BUNDLE_DATABASE_INVENTORY',
            'Database-only bundle declares files; restore refused': 'BUNDLE_DECLARES_FILES',
            'Bundle artifacts must be regular files': 'BUNDLE_ARTIFACT_KIND',
            'Bundle lacks mandatory PostgreSQL access evidence; a new format-3 backup is required': 'BUNDLE_ACCESS_EVIDENCE',
            'Container arguments must be explicit names/IDs': 'CONTAINER_ARGUMENTS',
        }
        guard_reason = "UNKNOWN"
        if hint == "GUARD":
            if isinstance(error, RecoveryError):
                guard_reason = guard_reasons.get(str(error), "UNKNOWN")
            elif isinstance(error, OSError):
                guard_reason = "OS_ERROR"
            elif isinstance(error, json.JSONDecodeError):
                guard_reason = "JSON_PARSE"
            elif isinstance(error, KeyError):
                guard_reason = "KEY_ERROR"
            elif isinstance(error, tarfile.TarError):
                guard_reason = "TAR_ERROR"
            elif isinstance(error, ValueError):
                guard_reason = "INVALID_VALUE"
        print("RECOVERY_GUARD_REASON:" + guard_reason, file=sys.stderr)
        print(f"FAIL recovery: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
