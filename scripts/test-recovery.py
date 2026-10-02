#!/usr/bin/env python3
"""Exercise backup/restore on synthetic disposable PG/Mongo/files only."""
import argparse
import importlib.util
import json
import os
from pathlib import Path
import re
import subprocess
import sys

HERE = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location("recovery", HERE / "recovery.py")
r = importlib.util.module_from_spec(spec)
spec.loader.exec_module(r)
COMPOSE = HERE.parent / "docker-compose.test-restore.yml"


def compose(work, project, *args):
    return ["docker", "compose", "-f", str(COMPOSE), "--env-file", str(work / (project + ".env")),
            "-p", project, *args]


def target(work, project):
    containers = [r.run(compose(work, project, "ps", "-q", service)).decode().strip()
                  for service in ("postgres-academic", "postgres-schedule", "mongo-attendance")]
    r.require(all(containers), "Missing isolated containers")
    return argparse.Namespace(academic_container=containers[0], schedule_container=containers[1],
                              mongo_container=containers[2], env_file=work / (project + ".env"),
                              files_dir=work / (project + "-files"), target_project=project,
                              dry_run=False, quiesced=True)


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
        r.require(not args.dry_run, "--cleanup and --dry-run cannot be combined")
        marker = json.loads((args.work_dir / "owned-projects.json").read_text())
        r.require(marker == projects, "Cleanup ownership marker mismatch")
        for project in projects:
            ids = r.run(["docker", "ps", "-aq", "--filter", "label=com.docker.compose.project=" + project]).decode().split()
            for container in ids:
                info = json.loads(r.run(["docker", "inspect", container]))[0]
                r.require(info["Config"]["Labels"].get("io.rutcampustrack.recovery") == "disposable",
                          "Cleanup refused foreign resources")
            networks = r.run(["docker", "network", "ls", "-q", "--filter", "label=com.docker.compose.project=" + project]).decode().split()
            for network in networks:
                info = json.loads(r.run(["docker", "network", "inspect", network]))[0]
                r.require(info["Labels"].get("io.rutcampustrack.recovery") == "disposable",
                          "Cleanup refused foreign network")
            volumes = r.run(["docker", "volume", "ls", "-q", "--filter", "label=com.docker.compose.project=" + project])
            r.require(not volumes.strip(), "Cleanup refused unexpected persistent volumes")
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
            f"MONGO_ROOT_PASSWORD={password}\nRCT_RECOVERY_PG_IMAGE={args.pg_image}\n"
            f"RCT_RECOVERY_MONGO_IMAGE={args.mongo_image}\n", encoding="utf-8")
    source_project, dest_project = projects
    r.run(compose(args.work_dir, source_project, "up", "-d", "--pull", "never", "--wait", "--wait-timeout", "120"))
    source = target(args.work_dir, source_project)
    secret = r.credentials(source.env_file)
    for container, db, key in ((source.academic_container, "academic_db", "POSTGRES_ACADEMIC_PASSWORD"),
                               (source.schedule_container, "schedule_db", "POSTGRES_SCHEDULE_PASSWORD")):
        r.pg_query(container, secret[key], db, "CREATE TABLE recovery_probe(id bigserial PRIMARY KEY, payload text NOT NULL); INSERT INTO recovery_probe(payload) VALUES ('Привет — recovery'),('second row'); CREATE INDEX recovery_payload_idx ON recovery_probe(payload);")
    r.mongo_query(source.mongo_container, secret["MONGO_ROOT_PASSWORD"], """
        for (const name of ['attendance_db','notification_db']) {
          const c=db.getSiblingDB(name).recovery_probe;
          c.insertMany([{_id:1,payload:'Привет — recovery'},{_id:2,payload:'second row'}]);
          c.createIndex({payload:1},{unique:true});
        } print(JSON.stringify(true));
    """)
    source.files_dir.mkdir()
    (source.files_dir / "nested").mkdir()
    (source.files_dir / "empty-dir").mkdir()
    (source.files_dir / "nested" / "Привет.txt").write_text("attachment bytes\n", encoding="utf-8")
    (source.files_dir / "binary.bin").write_bytes(bytes(range(256)))
    source.output = args.work_dir / "bundle"
    r.backup(source)
    expect_refusal(lambda: r.backup(source), "existing backup cannot be overwritten")
    # Stop only our source before target startup: never more than three active DBs.
    r.run(compose(args.work_dir, source_project, "stop", "--timeout", "10"))
    r.run(compose(args.work_dir, dest_project, "up", "-d", "--pull", "never", "--wait", "--wait-timeout", "120"))
    dest = target(args.work_dir, dest_project)
    dest.bundle = source.output
    # Corrupt an expendable copy without touching the real bundle. Integrity rejection
    # happens before any SQL/files write; full restore on the same fresh target proves it.
    import shutil
    damaged = args.work_dir / "damaged-copy"
    shutil.copytree(source.output, damaged)
    with (damaged / "academic.dump").open("ab") as stream:
        stream.write(b"corrupt")
    dest.bundle = damaged
    expect_refusal(lambda: r.restore(dest), "corrupt archive before any target write")
    dest.bundle = source.output
    original_project = dest.target_project
    dest.target_project = args.project_base + "-foreign"
    expect_refusal(lambda: r.restore(dest), "project label mismatch")
    dest.target_project = original_project
    r.restore(dest)
    # Remove files guard from this refusal check so it reaches the DB freshness guard.
    dest.files_dir = args.work_dir / "never-created-repeat-files"
    expect_refusal(lambda: r.restore(dest), "nonempty database target")
    r.write_json(args.work_dir / "PASS.json", {"result": "PASS", "projects": projects,
        "bundle_manifest_sha256": r.digest(source.output / "manifest.json"),
        "checks": ["exact PG data/schema/sequences", "both Mongo documents/indexes/options", "all file bytes/paths", "overwrite/corruption/project/nonempty refusal"]})
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
    os.umask(0o077)
    try:
        exercise(args)
    except (r.RecoveryError, OSError, ValueError, KeyError) as error:
        print(f"FAIL synthetic recovery: {error}; owned resources/artifacts retained", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
