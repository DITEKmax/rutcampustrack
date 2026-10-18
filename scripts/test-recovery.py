"""Exercise backup/restore on synthetic disposable PG/Mongo/files only."""

import argparse
import importlib.util
import json
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
    if args.cleanup:
        args.failure_stage = "CLEANUP_VALIDATE"
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
    r.write_json(args.work_dir / "owned-projects.json", projects)
    for project in projects:
        # Synthetic-only credentials, deliberately different source/target passwords.
        password = project + "-disposable-only"
        (args.work_dir / (project + ".env")).write_text(
            f"POSTGRES_ACADEMIC_PASSWORD={password}\nPOSTGRES_SCHEDULE_PASSWORD={password}\n"
            f"POSTGRES_AUTH_PASSWORD={password}\nPOSTGRES_ACADEMIC_MIGRATION_PASSWORD={password}\n"
            f"POSTGRES_AUTH_MIGRATION_PASSWORD={password}\n"
            f"POSTGRES_RECOVERY_ADMIN_USER=rct_user\nPOSTGRES_RECOVERY_ADMIN_PASSWORD={password}\n"
            f"MONGO_ROOT_PASSWORD={password}\nRCT_RECOVERY_PG_IMAGE={args.pg_image}\n"
            f"RCT_RECOVERY_MONGO_IMAGE={args.mongo_image}\n",
            encoding="utf-8",
        )
    source_project, dest_project = projects
    args.failure_stage = "SOURCE_START"
    r.run(compose(args.work_dir, source_project, "up", "-d", "--pull", "never", "--wait", "--wait-timeout", "120"))
    args.failure_stage = "SOURCE_IDENTIFY"
    source = target(args.work_dir, source_project)
    secret = r.credentials(source.env_file)
    for label, container, db, key, user in r.pg_targets(source):
        args.failure_stage = "SOURCE_SEED_" + label.upper()
        r.pg_query(
            container,
            secret[key],
            db,
            "CREATE TABLE recovery_probe(id bigserial PRIMARY KEY, payload text NOT NULL); INSERT INTO recovery_probe(payload) VALUES ('Привет — recovery'),('second row'); CREATE INDEX recovery_payload_idx ON recovery_probe(payload);",
            user=user,
        )
        if label in ("academic", "auth"):
            runtime = "rct_academic_runtime" if label == "academic" else "rct_auth_runtime"
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
    r.run(compose(args.work_dir, dest_project, "up", "-d", "--pull", "never", "--wait", "--wait-timeout", "120"))
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
    for label, container, db, key, user in r.pg_targets(dest):
        if label not in ("academic", "auth"):
            continue
        runtime = "rct_academic_runtime" if label == "academic" else "rct_auth_runtime"
        password = restored_secret["POSTGRES_ACADEMIC_PASSWORD" if label == "academic" else "POSTGRES_AUTH_PASSWORD"]
        r.require(r.pg_query(container, password, db, "SELECT id FROM recovery_private", user=runtime) == "1",
                  "Restored explicit column grant was lost")
        r.require(r.pg_query(container, password, db, "SELECT recovery_probe_function()", user=runtime) == "42",
                  "Restored explicit function grant was lost")
        expect_refusal(lambda: r.pg_query(container, password, db, "SELECT payload FROM recovery_private", user=runtime),
                       "restored runtime cannot read an ungranted column")
        expect_refusal(lambda: r.pg_query(container, password, db, "INSERT INTO recovery_private VALUES(2,'refused')", user=runtime),
                       "restored runtime cannot write a protected table")
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
                "restored Auth/Academic runtime explicit column/function grants and protected DML/cross-DB denial",
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
