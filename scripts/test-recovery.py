"""Exercise backup/restore on synthetic disposable PG/Mongo/files only."""

import argparse
import importlib.util
import json
import hashlib
import stat
import os
import re
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location("recovery", HERE / "recovery.py")
r = importlib.util.module_from_spec(spec)
spec.loader.exec_module(r)
COMPOSE = HERE.parent / "docker-compose.test-restore.yml"


# Only owned literal guards become public codes; unknown exception material stays private.
FAILURE_GUARDS = {
    "Unsupported/incomplete PostgreSQL access inventory": "PG_ACCESS_INCOMPLETE",
    "Unsupported PostgreSQL access object": "PG_ACCESS_OBJECT",
    "Duplicate PostgreSQL access object": "PG_ACCESS_DUPLICATE",
    "PostgreSQL access inventory must include its database": "PG_ACCESS_DATABASE",
    "Source changed during backup; incomplete bundle retained": "SOURCE_CHANGED",
    "Choose exactly one of --no-files or --files-dir": "FILES_MODE",
    "Stop application writers first; acknowledge using --quiesced": "NOT_QUIESCED",
    "Backup output already exists; choose a new path": "OUTPUT_EXISTS",
    "Files directory must not be a symlink": "FILES_SYMLINK",
    "Backup cannot live within files source": "OUTPUT_IN_SOURCE",
    "Explicit credential file is missing": "CREDENTIAL_FILE_MISSING",
    "Unsupported recovery admin identity": "RECOVERY_ADMIN",
    "Three distinct DB containers are required": "DISTINCT_TARGETS",
    "DB target is not running": "TARGET_NOT_RUNNING",
    "Files source must be a real directory": "FILES_SOURCE",
    "Files source must contain only regular files/directories (no links/devices)": "FILES_ENTRY",
    "Bundle must be a real directory": "BUNDLE_DIRECTORY",
    "Bundle incomplete or contains unexpected artifacts": "BUNDLE_ARTIFACTS",
    "Bundle artifacts must be regular files": "BUNDLE_REGULAR_FILES",
    "Unsupported/incomplete manifest; format 3 with owner/ACL evidence is required": "MANIFEST_FORMAT",
    "Unsupported files mode in manifest": "MANIFEST_FILES_MODE",
    "Restore files mode does not match bundle; refused before target writes": "BUNDLE_MODE_MISMATCH",
    "Incomplete/unexpected database inventory": "BUNDLE_DATABASES",
    "Bundle lacks mandatory PostgreSQL access evidence; a new format-3 backup is required": "BUNDLE_ACCESS_MISSING",
    "Database-only bundle declares files; restore refused": "BUNDLE_DATABASE_ONLY_FILES",
    "Unsafe files archive member": "FILES_ARCHIVE_MEMBER",
    "Files archive inventory mismatch": "FILES_ARCHIVE_INVENTORY",
    "Target project must start rct-recovery-": "RESTORE_PROJECT_PREFIX",
    "Restore files target must not exist": "RESTORE_FILES_TARGET_EXISTS",
    "PostgreSQL target is not fresh; restore refused": "RESTORE_PG_TARGET_NOT_FRESH",
    "Mongo target is not fresh; restore refused": "RESTORE_MONGO_TARGET_NOT_FRESH",
    "Restore accepts only the explicit labeled disposable project": "RESTORE_TARGET_LABELS",
    "Schema reference database already exists": "SCHEMA_REFERENCE_EXISTS",
    "Created schema reference identity is missing": "SCHEMA_REFERENCE_ID_MISSING",
    "Schema reference identity changed; cleanup refused": "SCHEMA_REFERENCE_ID_CHANGED",
    "Restored academic data/sequences/owners/access differ; keep target isolated": "RESTORE_ACADEMIC_INVENTORY",
    "Restored auth data/sequences/owners/access differ; keep target isolated": "RESTORE_AUTH_INVENTORY",
    "Restored schedule data/sequences/owners/access differ; keep target isolated": "RESTORE_SCHEDULE_INVENTORY",
    "Restored academic schema differs from PostgreSQL reference; keep target isolated": "RESTORE_ACADEMIC_SCHEMA",
    "Restored auth schema differs from PostgreSQL reference; keep target isolated": "RESTORE_AUTH_SCHEMA",
    "Restored schedule schema differs from PostgreSQL reference; keep target isolated": "RESTORE_SCHEDULE_SCHEMA",
    "Restored Mongo/files inventory differs; keep target isolated": "RESTORE_MONGO_FILES",
    "Integrity check failed: academic.dump": "BUNDLE_INTEGRITY",
    "Integrity check failed: auth.dump": "BUNDLE_INTEGRITY",
    "Integrity check failed: schedule.dump": "BUNDLE_INTEGRITY",
    "Integrity check failed: attendance.archive": "BUNDLE_INTEGRITY",
    "Integrity check failed: notification.archive": "BUNDLE_INTEGRITY",
    "Integrity check failed: files.tar": "BUNDLE_INTEGRITY",
    "Integrity check failed: inventory.json": "BUNDLE_INTEGRITY",
}


def public_initializer(work):
    """Copy public bytes only; a private checkout's umask must not reach PostgreSQL."""
    source = HERE.parent / "infra/postgres/init-roles.sh"
    flags = os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK
    fd = os.open(source, flags)
    try:
        before = os.fstat(fd)
        r.require(stat.S_ISREG(before.st_mode) and 0 < before.st_size <= 256 * 1024,
                  "Public initializer must be a bounded regular file")
        data = os.read(fd, 256 * 1024 + 1)
        after = os.fstat(fd)
        r.require(len(data) == before.st_size and
                  (before.st_dev, before.st_ino, before.st_mode, before.st_size, before.st_mtime_ns, before.st_ctime_ns) ==
                  (after.st_dev, after.st_ino, after.st_mode, after.st_size, after.st_mtime_ns, after.st_ctime_ns), "Public initializer changed")
    finally:
        os.close(fd)
    target = work / "init-roles.public.sh"
    fd = os.open(target, os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, 0o755)
    try:
        os.fchmod(fd, 0o755)
        with os.fdopen(fd, "wb", closefd=False) as stream:
            stream.write(data)
            stream.flush()
            os.fsync(fd)
    finally:
        os.close(fd)
    r.require(target.read_bytes() == data and stat.S_IMODE(target.stat().st_mode) == 0o755,
              "Public initializer copy mismatch")
    with (work / "initializer.safe.json").open("x", encoding="utf-8") as stream:
        json.dump({"schema": "rct-recovery-initializer.v1", "bytes": len(data),
                   "sha256": hashlib.sha256(data).hexdigest(), "mode": "0755",
                   "byteIdentity": True}, stream)
    return target


def startup(args, project):
    diagnostics = args.work_dir / "private-diagnostics"
    diagnostics.mkdir(mode=0o700, exist_ok=True)
    return r.run(compose(args.work_dir, project, "up", "-d", "--pull", "never",
                         "--wait", "--wait-timeout", "120"),
                 diagnostic_dir=diagnostics, diagnostic_stage=args.failure_stage)


def closure_receipt(args, projects):
    absent = {}
    for kind, prefix in (("containersAbsent", ["docker", "ps", "-aq"]),
                         ("networksAbsent", ["docker", "network", "ls", "-q"]),
                         ("volumesAbsent", ["docker", "volume", "ls", "-q"])):
        observations = [not r.run(prefix + ["--filter", "label=com.docker.compose.project=" + project]).strip()
                        for project in projects]
        absent[kind] = all(observations)
    receipt = {"schema": "rct-files-recovery-cleanup.v1", "sourceRevision": args.source_revision,
               "nonce": args.nonce, "projectPairBound": True, "syntheticLabelsValidated": True,
               **absent, "closed": all(absent.values()), "cleanupExit": 0 if all(absent.values()) else 1}
    fd = os.open(args.work_dir / "cleanup.safe.json",
                 os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, 0o600)
    try:
        with os.fdopen(fd, "w", encoding="utf-8", closefd=False) as stream:
            json.dump(receipt, stream, sort_keys=True)
            stream.write("\n")
            stream.flush()
            os.fsync(fd)
    finally:
        os.close(fd)
    r.require(receipt["closed"], "Owned recovery resources remain after cleanup")


def compose(work, project, *args):
    return ["docker", "compose", "-f", str(COMPOSE), "--env-file", str(work / (project + ".env")), "-p", project, *args]


def target(work, project):
    containers = [
        r.run(compose(work, project, "ps", "-q", service)).decode().strip()
        for service in ("postgres-academic", "postgres-schedule", "mongo-attendance")
    ]
    r.require(all(containers), "Missing isolated containers")
    return argparse.Namespace(
        academic_container=containers[0],
        schedule_container=containers[1],
        mongo_container=containers[2],
        env_file=work / (project + ".env"),
        files_dir=work / (project + "-files"),
        target_project=project,
        dry_run=False,
        quiesced=True,
    )


def expect_refusal(callback, reason):
    try:
        callback()
    except r.RecoveryError:
        print("PASS refusal: " + reason)
        return
    raise r.RecoveryError("Expected refusal missing: " + reason)


def exercise(args):
    r.require(re.fullmatch(r"rct-recovery-[a-z0-9-]+", args.project_base), "Project base must start rct-recovery-")
    projects = [args.project_base + "-src", args.project_base + "-dst"]
    r.require(args.work_dir.is_absolute() and args.work_dir.resolve() == args.work_dir,
              "Recovery work directory must be an absolute non-link path")
    if args.source_revision is not None or args.nonce is not None:
        r.require(type(args.source_revision) is str and re.fullmatch(r"[0-9a-f]{40}", args.source_revision)
                  and type(args.nonce) is str and re.fullmatch(r"[0-9a-f]{24}", args.nonce)
                  and args.project_base == "rct-recovery-files-" + args.nonce,
                  "Recovery source/nonce binding mismatch")
    os.environ["RCT_RECOVERY_INIT_ROLES_FILE"] = str(args.work_dir / "init-roles.public.sh")
    if args.cleanup:
        args.failure_stage = "CLEANUP_VALIDATE"
        info = args.work_dir.lstat()
        r.require(stat.S_ISDIR(info.st_mode) and info.st_uid == os.getuid()
                  and stat.S_IMODE(info.st_mode) == 0o700, "Recovery cleanup directory must remain private")
        r.require(not args.dry_run, "--cleanup and --dry-run cannot be combined")
        marker = json.loads((args.work_dir / "owned-projects.json").read_text())
        r.require(marker == projects, "Cleanup ownership marker mismatch")
        for project in projects:
            ids = (
                r.run(["docker", "ps", "-aq", "--filter", "label=com.docker.compose.project=" + project])
                .decode()
                .split()
            )
            for container in ids:
                info = json.loads(r.run(["docker", "inspect", container]))[0]
                r.require(
                    info["Config"]["Labels"].get("io.rutcampustrack.recovery") == "disposable",
                    "Cleanup refused foreign resources",
                )
            networks = (
                r.run(["docker", "network", "ls", "-q", "--filter", "label=com.docker.compose.project=" + project])
                .decode()
                .split()
            )
            for network in networks:
                info = json.loads(r.run(["docker", "network", "inspect", network]))[0]
                r.require(
                    info["Labels"].get("io.rutcampustrack.recovery") == "disposable", "Cleanup refused foreign network"
                )
            volumes = r.run(["docker", "volume", "ls", "-q", "--filter", "label=com.docker.compose.project=" + project])
            r.require(not volumes.strip(), "Cleanup refused unexpected persistent volumes")
        args.failure_stage = "CLEANUP_DOWN"
        for project in projects:
            r.run(compose(args.work_dir, project, "down", "--timeout", "10"))
        closure_receipt(args, projects)
        print("PASS cleanup: only marked disposable projects removed; all artifacts retained")
        return
    r.require(not args.work_dir.exists(), "Work directory must be new")
    r.require(args.pg_image and args.mongo_image, "Both cached image refs required")
    if args.dry_run:
        print("PLAN: " + ", ".join(projects) + "; max three active containers; no ports; tmpfs DBs; retained artifacts")
        return
    # Fail before writing if any existing resources use either exact project name.
    for project in projects:
        existing = r.run(["docker", "ps", "-aq", "--filter", "label=com.docker.compose.project=" + project])
        networks = r.run(["docker", "network", "ls", "-q", "--filter", "label=com.docker.compose.project=" + project])
        volumes = r.run(["docker", "volume", "ls", "-q", "--filter", "label=com.docker.compose.project=" + project])
        r.require(not (existing.strip() or networks.strip() or volumes.strip()), "Project resources already exist")
    for image in (args.pg_image, args.mongo_image):
        r.run(["docker", "image", "inspect", image])
    args.work_dir.mkdir(parents=True, mode=0o700)
    public_initializer(args.work_dir)
    r.write_json(args.work_dir / "owned-projects.json", projects)
    for project in projects:
        # Synthetic-only credentials, deliberately different source/target passwords.
        keys = (
            "POSTGRES_ACADEMIC_PASSWORD", "POSTGRES_AUTH_PASSWORD", "POSTGRES_SCHEDULE_PASSWORD",
            "POSTGRES_ACADEMIC_LEGACY_RUNTIME_PASSWORD", "POSTGRES_ACADEMIC_MIGRATION_PASSWORD",
            "POSTGRES_AUTH_MIGRATION_PASSWORD", "POSTGRES_SCHEDULE_MIGRATION_PASSWORD",
            "POSTGRES_ACADEMIC_MONITOR_PASSWORD", "POSTGRES_SCHEDULE_MONITOR_PASSWORD",
            "POSTGRES_RECOVERY_ADMIN_PASSWORD", "POSTGRES_SCHEDULE_RECOVERY_ADMIN_PASSWORD", "MONGO_ROOT_PASSWORD",
        )
        values = {key: project + "-" + str(index) + "-disposable-only" for index, key in enumerate(keys)}
        values.update(POSTGRES_RECOVERY_ADMIN_USER="rct_bootstrap",
                      POSTGRES_SCHEDULE_RECOVERY_ADMIN_USER="rct_bootstrap",
                      RCT_RECOVERY_PG_IMAGE=args.pg_image, RCT_RECOVERY_MONGO_IMAGE=args.mongo_image)
        (args.work_dir / (project + ".env")).write_text(
            "".join(key + "=" + value + "\n" for key, value in values.items()), encoding="utf-8",
        )
    source_project, dest_project = projects
    args.failure_stage = "SOURCE_START"
    startup(args, source_project)
    args.failure_stage = "SOURCE_IDENTIFY"
    source = target(args.work_dir, source_project)
    secret = r.credentials(source.env_file)
    for label, container, db, key, user in r.pg_targets(source, secret):
        args.failure_stage = "SOURCE_SEED_" + label.upper()
        r.pg_query(
            container,
            secret[key],
            db,
            "CREATE TABLE recovery_probe(id bigserial PRIMARY KEY, payload text NOT NULL); INSERT INTO recovery_probe(payload) VALUES ('Привет — recovery'),('second row'); CREATE INDEX recovery_payload_idx ON recovery_probe(payload);",
            user=user,
        )
        runtime = {"academic": "rct_academic_runtime", "auth": "rct_auth_runtime", "schedule": "rct_runtime"}[label]
        # The fixture invokes the actual production initializer, with no rct_user.
        roles = json.loads(r.pg_query(container, secret[key], db, """
SELECT json_agg(json_build_object('name',rolname,'super',rolsuper,'createdb',rolcreatedb,
 'createrole',rolcreaterole,'bypassrls',rolbypassrls,'replication',rolreplication))
FROM pg_roles WHERE rolname LIKE 'rct_%';
""", user=user))
        r.require({role["name"] for role in roles} == ({"rct_bootstrap", "rct_runtime", "rct_migrator", "rct_monitor"}
                  | ({"rct_auth_runtime", "rct_academic_runtime", "rct_auth_migrator"} if label != "schedule" else set())),
                  "Disposable roles differ from production principals")
        r.require(all(not any(role[flag] for flag in ("super", "createdb", "createrole", "bypassrls", "replication"))
                      for role in roles if role["name"] != "rct_bootstrap"),
                  "Application roles must retain production minimum privileges")
        r.pg_query(container, secret[key], db, f"""
CREATE TABLE recovery_private(id integer PRIMARY KEY,payload text NOT NULL);
INSERT INTO recovery_private VALUES(1,'synthetic-private-probe');
REVOKE ALL ON recovery_private FROM {runtime};
GRANT SELECT(id) ON recovery_private TO {runtime};
CREATE FUNCTION recovery_probe_function() RETURNS integer LANGUAGE SQL AS 'SELECT 42';
REVOKE EXECUTE ON FUNCTION recovery_probe_function() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION recovery_probe_function() TO {runtime};
ALTER DEFAULT PRIVILEGES REVOKE EXECUTE ON FUNCTIONS FROM PUBLIC;
""", user=user)
    args.failure_stage = "SOURCE_SEED_MONGO"
    r.mongo_query(
        source.mongo_container,
        secret["MONGO_ROOT_PASSWORD"],
        """
        for (const name of ['attendance_db','notification_db']) {
          const c=db.getSiblingDB(name).recovery_probe;
          c.insertMany([{_id:1,payload:'Привет — recovery'},{_id:2,payload:'second row'}]);
          c.createIndex({payload:1},{unique:true});
        } print(JSON.stringify(true));
    """,
    )
    args.failure_stage = "SOURCE_SEED_FILES"
    source.files_dir.mkdir()
    (source.files_dir / "nested").mkdir()
    (source.files_dir / "empty-dir").mkdir()
    (source.files_dir / "nested" / "Привет.txt").write_text("attachment bytes\n", encoding="utf-8")
    (source.files_dir / "binary.bin").write_bytes(bytes(range(256)))
    source.output = args.work_dir / "bundle"
    args.failure_stage = "BACKUP"
    r.backup(source)
    args.failure_stage = "REFUSAL_OVERWRITE"
    expect_refusal(lambda: r.backup(source), "existing backup cannot be overwritten")
    # Stop only our source before target startup: never more than three active DBs.
    args.failure_stage = "SOURCE_STOP"
    r.run(compose(args.work_dir, source_project, "stop", "--timeout", "10"))
    args.failure_stage = "DESTINATION_START"
    startup(args, dest_project)
    args.failure_stage = "DESTINATION_IDENTIFY"
    dest = target(args.work_dir, dest_project)
    dest.bundle = source.output
    # Corrupt an expendable copy without touching the real bundle. Integrity rejection
    # happens before any SQL/files write; full restore on the same fresh target proves it.
    import shutil

    args.failure_stage = "REFUSAL_CORRUPT"
    damaged = args.work_dir / "damaged-copy"
    shutil.copytree(source.output, damaged)
    with (damaged / "academic.dump").open("ab") as stream:
        stream.write(b"corrupt")
    dest.bundle = damaged
    expect_refusal(lambda: r.restore(dest), "corrupt archive before any target write")
    dest.bundle = source.output
    args.failure_stage = "REFUSAL_FOREIGN"
    original_project = dest.target_project
    dest.target_project = args.project_base + "-foreign"
    expect_refusal(lambda: r.restore(dest), "project label mismatch")
    dest.target_project = original_project
    args.failure_stage = "RESTORE"
    r.restore(dest)
    args.failure_stage = "RESTORED_RUNTIME_ACL"
    restored_secret = r.credentials(dest.env_file)
    for label, container, db, key, user in r.pg_targets(dest, restored_secret):
        runtime = {"academic": "rct_academic_runtime", "auth": "rct_auth_runtime", "schedule": "rct_runtime"}[label]
        password = restored_secret[{"academic": "POSTGRES_ACADEMIC_PASSWORD", "auth": "POSTGRES_AUTH_PASSWORD",
                                    "schedule": "POSTGRES_SCHEDULE_PASSWORD"}[label]]
        r.require(r.pg_query(container, password, db, "SELECT id FROM recovery_private", user=runtime) == "1",
                  "Restored explicit column grant was lost")
        r.require(r.pg_query(container, password, db, "SELECT recovery_probe_function()", user=runtime) == "42",
                  "Restored explicit function grant was lost")
        expect_refusal(lambda: r.pg_query(container, password, db, "SELECT payload FROM recovery_private", user=runtime),
                       "restored runtime cannot read an ungranted column")
        expect_refusal(lambda: r.pg_query(container, password, db, "INSERT INTO recovery_private VALUES(2,'refused')", user=runtime),
                       "restored runtime cannot write a protected table")
        expect_refusal(lambda: r.pg_query(container, password, db, "CREATE TABLE forbidden_runtime_ddl(id integer)", user=runtime),
                       "restored runtime cannot create schema objects")
        monitor_password = restored_secret["POSTGRES_SCHEDULE_MONITOR_PASSWORD" if label == "schedule"
                                           else "POSTGRES_ACADEMIC_MONITOR_PASSWORD"]
        r.require(r.pg_query(container, monitor_password, db, "SELECT current_user", user="rct_monitor") == "rct_monitor",
                  "Restored monitor credential/CONNECT is invalid")
        expect_refusal(lambda: r.pg_query(container, monitor_password, db, "SELECT payload FROM recovery_private", user="rct_monitor"),
                       "restored monitor cannot read application data")
        if label != "schedule":
            other_db = "auth_db" if label == "academic" else "academic_db"
            expect_refusal(lambda: r.pg_query(container, password, other_db, "SELECT 1", user=runtime),
                           "restored runtime cannot connect to the other owner's database")
    # Remove files guard from this refusal check so it reaches the DB freshness guard.
    args.failure_stage = "REFUSAL_NONEMPTY"
    dest.files_dir = args.work_dir / "never-created-repeat-files"
    expect_refusal(lambda: r.restore(dest), "nonempty database target")
    args.failure_stage = "WRITE_PASS"
    r.write_json(
        args.work_dir / "PASS.json",
        {
            "result": "PASS",
            "projects": projects,
            "bundle_manifest_sha256": r.digest(source.output / "manifest.json"),
            "checks": [
                "all three PG databases: exact data/schema/sequences/owners/ACL/default privileges",
                "production principals (no rct_user); all runtime column/function grants and protected DML/DDL/monitor/cross-DB denial",
                "both Mongo documents/indexes/options",
                "all file bytes/paths",
                "overwrite/corruption/project/nonempty refusal",
            ],
        },
    )
    print("PASS synthetic recovery; target retained isolated; cleanup requires separate explicit command")


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--work-dir", type=Path, required=True)
    p.add_argument("--project-base", required=True)
    p.add_argument("--pg-image")
    p.add_argument("--mongo-image")
    p.add_argument("--dry-run", action="store_true")
    p.add_argument("--cleanup", action="store_true")
    p.add_argument("--source-revision")
    p.add_argument("--nonce")
    args = p.parse_args()
    args.failure_stage = "PREFLIGHT"
    os.umask(0o077)
    try:
        exercise(args)
    except (r.RecoveryError, OSError, ValueError, KeyError) as error:
        failure_class = next(
            (code for kind, code in (
                (r.RecoveryError, "RECOVERY_ERROR"),
                (json.JSONDecodeError, "JSON_DECODE_ERROR"),
                (UnicodeDecodeError, "UNICODE_DECODE_ERROR"),
                (OSError, "OS_ERROR"),
                (ValueError, "VALUE_ERROR"),
                (KeyError, "KEY_ERROR"),
            ) if isinstance(error, kind)),
            "OTHER",
        )
        diagnostic = getattr(error, "synthetic_diagnostic", None)
        if type(diagnostic) is dict:
            print("RECOVERY_STARTUP_CLASS:" + diagnostic["dockerClass"], file=sys.stderr)
            print("RECOVERY_STARTUP_LOG:" + diagnostic["privateLog"], file=sys.stderr)
        guard = "OTHER"
        if isinstance(error, r.RecoveryError) and len(error.args) == 1 and type(error.args[0]) is str:
            guard = FAILURE_GUARDS.get(error.args[0], "OTHER")
        print("RECOVERY_FAILURE_HINT:" + (error.hint if isinstance(error, r.RecoveryError) and error.hint in r.FAILURE_HINTS else "GUARD"), file=sys.stderr)
        print("RECOVERY_FAILURE_CLASS:" + failure_class, file=sys.stderr)
        print("RECOVERY_FAILURE_GUARD:" + guard, file=sys.stderr)
        print(
            f"FAIL synthetic recovery: stage={args.failure_stage}; owned resources/artifacts retained", file=sys.stderr
        )
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
