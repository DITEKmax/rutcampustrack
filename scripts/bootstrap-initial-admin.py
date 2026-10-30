#!/usr/bin/env python3
"""Explicit first-admin operator command; initial credentials travel only on private stdin."""

import argparse
import hashlib
import json
import os
import re
import stat
import subprocess
import sys
import uuid
import unicodedata


DIAGNOSTIC_ENV = "RCT_INITIAL_ADMIN_DIAGNOSTICS"
PHASES = frozenset({"PARSE", "READ_REQUEST", "VALIDATE_REQUEST", "AUTH_PREPARE",
                    "ACADEMIC_APPLY", "ACADEMIC_VERIFY", "AUTH_ACTIVATE", "ACADEMIC_CURSOR",
                    "AUTH_CATALOG", "ACADEMIC_FINALIZE", "RESULT", "OTHER"})
REASONS = frozenset({"INPUT", "IO", "CHILD_START", "CHILD_TIMEOUT", "CHILD_EXIT",
                     "OUTPUT_LIMIT", "REPLY_JSON", "REPLY_GUARD", "PROTOCOL", "OTHER"})
OWNER_PHASES = frozenset({"NONE", "INPUT", "CONFIG", "EXECUTE", "OUTPUT", "NO_MARKER", "OTHER"})
CATEGORIES = frozenset({"NONE", "CONFIG", "INPUT", "DB_CONNECT", "DB_PERMISSION",
                        "DB_SCHEMA", "DB_CONSTRAINT", "GUARD", "OTHER"})
OWNER_MARKER = re.compile(
    rb"BOOTSTRAP_OWNER_FAILURE_V1 owner=(AUTH|ACADEMIC) "
    rb"phase=(INPUT|CONFIG|EXECUTE|OUTPUT) "
    rb"category=(CONFIG|INPUT|DB_CONNECT|DB_PERMISSION|DB_SCHEMA|DB_CONSTRAINT|GUARD|OTHER)"
)


class Refused(Exception):
    def __init__(self, *, reason=None, owner_phase="NONE", category="NONE"):
        super().__init__()
        self.reason = reason
        self.owner_phase = owner_phase
        self.category = category


def owner_diagnostic(errors, owner):
    # Inspect only this bounded private channel. Never return a line or unmatched material.
    lines = [line for line in errors.splitlines() if b"BOOTSTRAP_OWNER_FAILURE_V1" in line]
    if not lines:
        return "NO_MARKER", "OTHER"
    if len(lines) == 1 and (match := OWNER_MARKER.fullmatch(lines[0])):
        if match[1].decode("ascii") == owner.upper():
            return match[2].decode("ascii"), match[3].decode("ascii")
    return "OTHER", "OTHER"


class ClosedParser(argparse.ArgumentParser):
    def error(self, message):
        raise Refused()


def canonical(value):
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"),
                      allow_nan=False).encode("utf-8")


def unique_object(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise Refused()
        result[key] = value
    return result


def read_request(path):
    # Walk open directory descriptors: neither the final file nor an ancestor may be a symlink.
    if not hasattr(os, "O_NOFOLLOW") or not hasattr(os, "geteuid"):
        raise Refused()
    parts = os.path.abspath(path).split(os.sep)[1:]
    if not parts or any(part in ("", ".", "..") for part in parts):
        raise Refused()
    directory = os.open(os.sep, os.O_RDONLY | os.O_DIRECTORY | os.O_CLOEXEC)
    file_fd = None
    try:
        for part in parts[:-1]:
            following = os.open(part, os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW | os.O_CLOEXEC,
                                dir_fd=directory)
            os.close(directory)
            directory = following
        file_fd = os.open(parts[-1], os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK | os.O_CLOEXEC,
                          dir_fd=directory)
        before = os.fstat(file_fd)
        if (not stat.S_ISREG(before.st_mode) or before.st_uid != os.geteuid()
                or stat.S_IMODE(before.st_mode) not in (0o400, 0o600)
                or before.st_nlink != 1 or not 0 < before.st_size <= 8192):
            raise Refused()
        contents = os.read(file_fd, 8193)
        after = os.fstat(file_fd)
        attrs = ("st_dev", "st_ino", "st_uid", "st_mode", "st_nlink", "st_size",
                 "st_mtime_ns", "st_ctime_ns")
        if len(contents) != before.st_size or any(getattr(before, a) != getattr(after, a) for a in attrs):
            raise Refused()
        request = json.loads(contents.decode("utf-8"), object_pairs_hook=unique_object)
    finally:
        if file_fd is not None:
            os.close(file_fd)
        os.close(directory)
    validate_request(request)
    return request


def validate_request(request):
    if (not isinstance(request, dict)
            or set(request) != {"version", "operationId", "login", "initialPassword", "profile"}
            or type(request["version"]) is not int or request["version"] != 1):
        raise Refused()
    operation = request["operationId"]
    if not isinstance(operation, str) or str(uuid.UUID(operation)) != operation:
        raise Refused()
    if not isinstance(request["login"], str) or not re.fullmatch(r"[A-Za-z0-9_.-]{3,32}", request["login"]):
        raise Refused()
    password = request["initialPassword"]
    if (not isinstance(password, str) or not 12 <= len(password)
            or not 0 < len(password.encode("utf-8")) <= 72
            or not any(unicodedata.category(c) == "Nd" for c in password)
            or not any(unicodedata.category(c)[0] in "PS" for c in password)):
        raise Refused()
    profile = request["profile"]
    if not isinstance(profile, dict) or set(profile) != {"firstName", "lastName", "middleName"}:
        raise Refused()
    for key, value in profile.items():
        if (not isinstance(value, str) or len(value.encode("utf-16-le")) // 2 > 128
                or (key != "middleName" and not value.strip())):
            raise Refused()
    canonical(profile)  # Reject unpaired Unicode surrogates before making any DB command.


def invoke(compose_files, service, stage, payload, *, diagnostics=False,
           project_name=None, env_file=None, image_tag=None):
    owner = {"auth-service": "auth", "academic-service": "academic"}[service]
    name = "Auth" if owner == "auth" else "Academic"
    main = f"ru.rutcampustrack.{owner}.bootstrap.InitialAdministrator{name}Command"
    command = ["docker", "compose"]
    if project_name is not None:
        command += ["--project-name", project_name]
    if env_file is not None:
        command += ["--env-file", env_file]
    for path in compose_files:
        command += ["-f", path]
    command += ["run", "--rm", "--no-deps", "-T", "--entrypoint", "java"]
    if diagnostics:
        # The parent opt-in is not implicitly inherited by Compose's one-shot containers.
        command += ["--env", DIAGNOSTIC_ENV + "=1"]
    command += [service,
                "-XX:-HeapDumpOnOutOfMemoryError", "-XX:ErrorFile=/dev/null",
                f"-Dloader.main={main}", "org.springframework.boot.loader.launch.PropertiesLauncher", stage]
    try:
        options = {} if image_tag is None else {"env": dict(os.environ, IMAGE_TAG=image_tag)}
        result = subprocess.run(command, input=canonical(payload), capture_output=True,
                                timeout=30, check=False, close_fds=True, **options)
    except subprocess.TimeoutExpired:
        raise Refused(reason="CHILD_TIMEOUT") from None
    except OSError:
        raise Refused(reason="CHILD_START") from None
    # Child output remains private. Never forward DB/launcher/compose stderr or exception text.
    if len(result.stdout) > 1_048_576 or len(result.stderr) > 16384:
        raise Refused(reason="OUTPUT_LIMIT")
    if result.returncode != 0:
        phase, category = owner_diagnostic(result.stderr, owner) if diagnostics else ("NONE", "NONE")
        raise Refused(reason="CHILD_EXIT", owner_phase=phase, category=category)
    try:
        reply = json.loads(result.stdout.decode("utf-8"), object_pairs_hook=unique_object)
    except (ValueError, UnicodeError, Refused):
        raise Refused(reason="REPLY_JSON") from None
    if not isinstance(reply, dict) or reply.get("status") == "REFUSED":
        raise Refused(reason="REPLY_GUARD")
    return reply


def bootstrap(request, call):
    validate_request(request)
    public = {"version": 1, "operationId": request["operationId"], "login": request["login"],
              "profileDigest": hashlib.sha256(canonical(request["profile"])).hexdigest()}
    command_hash = hashlib.sha256(canonical(public)).hexdigest()
    prepared = call("auth-service", "prepare", dict(public, password=request["initialPassword"]))
    if (prepared.get("version") != 1 or prepared.get("operationId") != public["operationId"]
            or prepared.get("login") != public["login"] or prepared.get("profileDigest") != public["profileDigest"]
            or prepared.get("commandHash") != command_hash or type(prepared.get("userId")) is not int
            or prepared["userId"] <= 0 or prepared.get("rolesVersion") != 1
            or prepared.get("status") not in ("PREPARED", "COMMITTED")):
        raise Refused()
    binding = dict(public, commandHash=command_hash, userId=prepared["userId"], rolesVersion=1)
    academic = dict(binding, authStatus=prepared["status"])
    call("academic-service", "apply", dict(academic, profile=request["profile"]))
    verified = call("academic-service", "verify", academic)
    receipt_fields = ("version", "operationId", "commandHash", "profileDigest", "userId",
                      "rolesVersion", "academicRevision", "receiptHash")
    if any(key not in verified for key in receipt_fields):
        raise Refused()
    receipt = {key: verified[key] for key in receipt_fields}
    activated = call("auth-service", "activate", dict(binding, academicReceipt=receipt))
    if (activated.get("status") != "COMMITTED" or activated.get("operationId") != binding["operationId"]
            or activated.get("userId") != binding["userId"]
            or activated.get("commandHash") != command_hash
            or type(activated.get("committedCatalogVersion")) is not int
            or activated["committedCatalogVersion"] <= 0):
        raise Refused()
    academic["authStatus"] = "COMMITTED"
    target = activated["committedCatalogVersion"]
    for _ in range(32):
        cursor = call("academic-service", "cursor", academic)
        after = cursor.get("afterCatalogVersion")
        if type(after) is not int or after < 0:
            raise Refused()
        final = dict(academic, committedCatalogVersion=target)
        if after < target:
            page = call("auth-service", "catalog", dict(binding, afterCatalogVersion=after))
            payload = page.get("catalogPayload")
            if not isinstance(payload, str) or len(payload) > 1_000_000:
                raise Refused()
            final["catalogPayload"] = payload
        result = call("academic-service", "finalize", final)
        if result.get("status") == "READY":
            return {"operationId": binding["operationId"], "userId": binding["userId"], "status": "COMMITTED"}
        if result.get("status") != "MORE":
            raise Refused()
    raise Refused()  # Bounded work. Exact same request resumes the retained catalog frontier.


def main(argv=None):
    diagnostics = os.environ.get(DIAGNOSTIC_ENV) == "1"
    phase, reason = "PARSE", "INPUT"
    parser = ClosedParser(description="Explicit initial FULL administrator bootstrap")
    parser.add_argument("--request-file", required=True)
    parser.add_argument("--compose-file", action="append")
    parser.add_argument("--validate-only", action="store_true")
    parser.add_argument("--project-name")
    parser.add_argument("--env-file")
    parser.add_argument("--image-tag")
    try:
        args = parser.parse_args(argv)
        if not args.validate_only and not args.compose_file:
            raise Refused()
        if args.image_tag is not None and not re.fullmatch(r"[0-9a-f]{40}", args.image_tag):
            raise Refused()
        phase = "READ_REQUEST"
        request = read_request(args.request_file)
        phase = "VALIDATE_REQUEST"
        if args.validate_only:
            return 0

        def call(service, stage, body):
            nonlocal phase, reason
            phase = {
                ("auth-service", "prepare"): "AUTH_PREPARE",
                ("academic-service", "apply"): "ACADEMIC_APPLY",
                ("academic-service", "verify"): "ACADEMIC_VERIFY",
                ("auth-service", "activate"): "AUTH_ACTIVATE",
                ("academic-service", "cursor"): "ACADEMIC_CURSOR",
                ("auth-service", "catalog"): "AUTH_CATALOG",
                ("academic-service", "finalize"): "ACADEMIC_FINALIZE",
            }.get((service, stage), "OTHER")
            reason = "OTHER"
            reply = invoke(args.compose_file, service, stage, body, diagnostics=diagnostics,
                           project_name=args.project_name, env_file=args.env_file, image_tag=args.image_tag)
            reason = "PROTOCOL"
            return reply

        result = bootstrap(request, call)
        phase, reason = "RESULT", "OTHER"
        print(json.dumps(result, separators=(",", ":")))
        return 0
    except Exception as failure:
        print('{"status":"REFUSED"}')
        if diagnostics:
            owner_phase, category = "NONE", "NONE"
            if isinstance(failure, Refused):
                reason = failure.reason if failure.reason in REASONS else reason
                owner_phase = failure.owner_phase if failure.owner_phase in OWNER_PHASES else "OTHER"
                category = failure.category if failure.category in CATEGORIES else "OTHER"
            elif isinstance(failure, OSError):
                reason = "IO"
            elif isinstance(failure, (ValueError, UnicodeError)):
                reason = "INPUT"
            else:
                reason = "OTHER"
            print("BOOTSTRAP_CLI_PHASE_V1 phase=" + (phase if phase in PHASES else "OTHER")
                  + " reason=" + (reason if reason in REASONS else "OTHER")
                  + " ownerPhase=" + owner_phase + " category=" + category, file=sys.stderr)
        return 2


if __name__ == "__main__":
    sys.exit(main())
