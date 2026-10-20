"""One owned post-cleanup PG read pass. No Mongo, recovery helper or business mutation."""

import json
import os
import re
import selectors
import stat
import subprocess
import sys
import time
import uuid
from pathlib import Path

LIMIT = 4096
MAX_COUNT = 257
INPUT = "transfer-input.private.json"
OUTPUT = "transfer-stage.safe.json"
INPUT_KEYS = {"schema", "operationId", "sourceLessonId", "targetLessonId", "occurrenceId",
              "groupId", "semesterId", "actorId", "observationOutcome"}
COUNT_KEYS = {"expectedBatches", "attendanceApplied", "attendanceError", "academicApplied",
              "academicError", "requestPending", "requestSent", "requestFailed", "requestOther",
              "requestRows", "requestBatchCoverage"}
SCHEDULE_BOOL = {"principalMatches", "operationPresent", "tupleMatches", "overflow",
                 "receiptIdentityMatches", "requestIdentityMatches"}
ACADEMIC_COUNTS = {"receiptRows", "expectedBatches", "applied", "error", "ackRows",
                   "ackPending", "ackSent", "ackFailed", "ackOther", "ackBatchCoverage"}
ACADEMIC_BOOL = {"principalMatches", "receiptIdentityMatches", "batchCountsConsistent",
                 "ackIdentityMatches", "overflow"}


def _pairs(items):
    value = {}
    for key, item in items:
        if key in value:
            raise ValueError("Duplicate field")
        value[key] = item
    return value


def _json(raw):
    return json.loads(raw, object_pairs_hook=_pairs,
                      parse_constant=lambda _: (_ for _ in ()).throw(ValueError("Nonfinite field")))


def _identity(info):
    return info.st_dev, info.st_ino, info.st_size, info.st_mtime_ns, info.st_ctime_ns


def _directory(path, parent=None, mode=None):
    fd = os.open(str(path), os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW, dir_fd=parent)
    info = os.fstat(fd)
    if (not stat.S_ISDIR(info.st_mode) or info.st_uid != os.getuid()
            or (mode is not None and stat.S_IMODE(info.st_mode) != mode)):
        os.close(fd)
        raise ValueError("Unowned directory")
    return fd


def _read(fd, name, limit=LIMIT, *, mode=0o600):
    before = os.stat(name, dir_fd=fd, follow_symlinks=False)
    child = os.open(name, os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK, dir_fd=fd)
    with os.fdopen(child, "rb") as stream:
        info = os.fstat(stream.fileno())
        if (not stat.S_ISREG(info.st_mode) or info.st_uid != os.getuid() or info.st_nlink != 1
                or stat.S_IMODE(info.st_mode) != mode or not 0 < info.st_size <= limit
                or _identity(before) != _identity(info)):
            raise ValueError("Unsafe private file")
        raw = stream.read(limit + 1)
        if (len(raw) != info.st_size or _identity(info) != _identity(os.fstat(stream.fileno()))
                or _identity(info) != _identity(os.stat(name, dir_fd=fd, follow_symlinks=False))):
            raise ValueError("Private file changed")
    return raw


def _write(fd, name, value):
    raw = json.dumps(value, separators=(",", ":"), allow_nan=False).encode()
    if not 0 < len(raw) <= LIMIT:
        raise ValueError("Projection limit")
    child = os.open(name, os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, 0o600, dir_fd=fd)
    with os.fdopen(child, "wb") as stream:
        os.fchmod(stream.fileno(), 0o600)
        stream.write(raw)


def _context(env):
    if any(env.get(key) != value for key, value in {
        "GITHUB_ACTIONS": "true", "RUNNER_ENVIRONMENT": "github-hosted", "CI": "true",
        "RCT_E2E_SYNTHETIC_SEED": "true", "E2E_BASE_URL": "https://localhost:8443",
    }.items()):
        raise ValueError("Hosted synthetic admission required")
    run_id, attempt = env.get("GITHUB_RUN_ID", ""), env.get("GITHUB_RUN_ATTEMPT", "")
    if not re.fullmatch(r"[1-9][0-9]{0,19}", run_id) or not re.fullmatch(r"[1-9][0-9]{0,5}", attempt):
        raise ValueError("Invalid run identity")
    root, temporary = Path(env["GITHUB_WORKSPACE"]), Path(env["RUNNER_TEMP"])
    if any(not p.is_absolute() or p.resolve(strict=True) != p for p in (root, temporary)):
        raise ValueError("Unanchored CI directory")
    run_name, clock_name = "rct-lesson-lifecycle-" + run_id + "-" + attempt, "rct-first3-clock-" + run_id + "-" + attempt
    if (env.get("RCT_LESSON_LIFECYCLE_RUN") != str(temporary / run_name)
            or env.get("RCT_E2E_TEACHING_CLOCK_DIR") != str(temporary / clock_name)):
        raise ValueError("Unexpected owned path")
    fds = []
    try:
        fds.append(_directory(root))
        fds.append(_directory(temporary))
        run_fd = _directory(run_name, fds[-1], 0o700)
        fds.append(run_fd)
        fds.append(_directory("artifacts", run_fd, 0o700))
        # The existing educational clock is public to service UIDs; our own run stays private.
        clock_fd = _directory(clock_name, fds[1], 0o755)
        fds.append(clock_fd)
        if _read(clock_fd, "disposable-target", 64, mode=0o644) != b"RCT_E2E_DISPOSABLE\n":
            raise ValueError("Owned stand marker mismatch")
        return {"fds": fds, "run": run_fd, "root": root, "run_name": run_name, "clock_name": clock_name}
    except BaseException:
        for fd in reversed(fds):
            os.close(fd)
        raise


def _stable(context):
    fds = context["fds"]
    for parent, name, held, mode in ((fds[1], context["run_name"], fds[2], 0o700),
                                     (fds[2], "artifacts", fds[3], 0o700),
                                     (fds[1], context["clock_name"], fds[4], 0o755)):
        current, original = os.stat(name, dir_fd=parent, follow_symlinks=False), os.fstat(held)
        if (current.st_dev, current.st_ino) != (original.st_dev, original.st_ino):
            raise ValueError("Owned directory changed")
        if not stat.S_ISDIR(current.st_mode) or current.st_uid != os.getuid() or stat.S_IMODE(current.st_mode) != mode:
            raise ValueError("Owned directory protection changed")
    if _read(fds[4], "disposable-target", 64, mode=0o644) != b"RCT_E2E_DISPOSABLE\n":
        raise ValueError("Owned marker changed")


def _input(value):
    if type(value) is not dict or set(value) != INPUT_KEYS or value.get("schema") != "lesson-transfer-input.v1":
        raise ValueError("Private input schema")
    operation = value["operationId"]
    if type(operation) is not str or str(uuid.UUID(operation)) != operation:
        raise ValueError("Private operation identity")
    for key in INPUT_KEYS - {"schema", "operationId", "observationOutcome"}:
        item = value[key]
        if type(item) is not str or not re.fullmatch(r"[1-9][0-9]{0,18}", item) or int(item) > 2**63 - 1:
            raise ValueError("Private tuple identity")
    if value["sourceLessonId"] == value["targetLessonId"] or value["observationOutcome"] not in ("TIMEOUT", "RETURNED"):
        raise ValueError("Private tuple mismatch")
    return value


def _call(argv, deadline, data=None):
    end = min(deadline, time.monotonic() + 5)
    if end <= time.monotonic():
        raise ValueError("Diagnostic deadline")
    child = subprocess.Popen(argv, stdin=subprocess.PIPE if data is not None else subprocess.DEVNULL,
                             stdout=subprocess.PIPE, stderr=subprocess.DEVNULL)
    payload = bytearray()
    try:
        with selectors.DefaultSelector() as selector:
            selector.register(child.stdout, selectors.EVENT_READ)
            pending = memoryview(data or b"")
            if data is not None:
                os.set_blocking(child.stdin.fileno(), False)
                if pending:
                    selector.register(child.stdin, selectors.EVENT_WRITE)
                else:
                    child.stdin.close()
            while selector.get_map():
                remaining = end - time.monotonic()
                if remaining <= 0:
                    raise ValueError("Diagnostic child deadline")
                ready = selector.select(remaining)
                if not ready:
                    raise ValueError("Diagnostic child deadline")
                for key, _ in ready:
                    if key.fileobj is child.stdin:
                        written = os.write(child.stdin.fileno(), pending[:4096])
                        pending = pending[written:]
                        if not pending:
                            selector.unregister(child.stdin)
                            child.stdin.close()
                    else:
                        chunk = os.read(child.stdout.fileno(), min(1024, LIMIT + 1 - len(payload)))
                        if not chunk:
                            selector.unregister(child.stdout)
                        else:
                            payload.extend(chunk)
                            if len(payload) > LIMIT:
                                raise ValueError("Diagnostic child byte limit")
        if child.wait(timeout=max(0.001, end - time.monotonic())):
            raise ValueError("Diagnostic read failed")
        return bytes(payload)
    finally:
        if child.poll() is None:
            child.kill()
        child.wait(timeout=1)
        if child.stdin is not None and not child.stdin.closed:
            child.stdin.close()
        child.stdout.close()


def _containers(deadline):
    result = {}
    for service in ("postgres-schedule", "postgres-academic"):
        raw = _call(["docker", "ps", "-q", "--no-trunc", "--filter", "status=running", "--filter",
                     "label=com.docker.compose.project=rct-e2e", "--filter",
                     "label=com.docker.compose.service=" + service], deadline).decode().strip()
        if not re.fullmatch(r"[0-9a-f]{64}", raw):
            raise ValueError("Ambiguous owned PG target")
        template = '[{{json .Id}},{{json (index .Config.Labels "com.docker.compose.project")}},{{json (index .Config.Labels "com.docker.compose.service")}},{{json .State.Running}}]'
        info = _json(_call(["docker", "inspect", "--format", template, raw], deadline))
        if info != [raw, "rct-e2e", service, True]:
            raise ValueError("Owned PG target changed")
        result[service] = raw
    return result


def _schedule_sql(v):
    # Values have canonical UUID/positive-decimal validation. SQL structure is never input.
    return f"""BEGIN READ ONLY; SET LOCAL statement_timeout='2000ms';
WITH pin AS MATERIALIZED (SELECT * FROM lesson_transfer_operations WHERE operation_id='{v['operationId']}'::uuid),
own AS MATERIALIZED (
 SELECT p.* FROM pin p WHERE p.actor_id={v['actorId']} AND p.source_lesson_id={v['sourceLessonId']}
 AND p.target_lesson_id={v['targetLessonId']} AND p.occurrence_id={v['occurrenceId']} AND p.template_operation_id IS NULL
 AND p.source_snapshot->'group_id'=to_jsonb({v['groupId']}::bigint) AND p.target_snapshot->'group_id'=to_jsonb({v['groupId']}::bigint)
 AND p.source_snapshot->'semester_id'=to_jsonb({v['semesterId']}::bigint) AND p.target_snapshot->'semester_id'=to_jsonb({v['semesterId']}::bigint)
 AND p.source_snapshot->'lesson_id'=to_jsonb(p.source_lesson_id) AND p.target_snapshot->'lesson_id'=to_jsonb(p.target_lesson_id)
 AND p.source_snapshot->'occurrence_id'=to_jsonb(p.occurrence_id) AND p.target_snapshot->'occurrence_id'=to_jsonb(p.occurrence_id)
 AND p.source_snapshot->>'one_off_lesson_id' ~ '^[1-9][0-9]*$'
 AND p.source_snapshot->'one_off_lesson_id'=p.target_snapshot->'one_off_lesson_id'
 AND p.source_snapshot->'schedule_item_id'='null'::jsonb AND p.target_snapshot->'schedule_item_id'='null'::jsonb),
receipts AS MATERIALIZED (SELECT r.*, r.payload_hash=o.operation_hash AND r.source_lesson_id=o.source_lesson_id
 AND r.target_lesson_id=o.target_lesson_id AND ((r.participant='ATTENDANCE' AND r.batch_index=-1)
 OR (r.participant='ACADEMIC' AND r.batch_index>=0 AND r.batch_index<o.batch_count)) AS matches
 FROM lesson_transfer_participant_receipts r JOIN own o USING(operation_id)),
requests AS MATERIALIZED (SELECT r.status, r.payload#>>'{{payload,batch_index}}' AS batch,
 COALESCE(r.payload->>'source'='schedule-service' AND r.payload->'event_version'='2'::jsonb
 AND EXISTS(SELECT 1 FROM lesson_transfer_binding_batches b JOIN own o USING(operation_id)
 WHERE r.payload->'payload'=b.payload),false) AS matches
 FROM schedule_outbox r WHERE r.event_type='lesson.transfer.requested'
 AND r.payload#>>'{{payload,operation_id}}'='{v['operationId']}' AND EXISTS(SELECT 1 FROM own))
SELECT json_build_object('principalMatches',current_user='rct_user' AND current_database()='schedule_db',
 'operationPresent',EXISTS(SELECT 1 FROM pin),'tupleMatches',EXISTS(SELECT 1 FROM own),
 'operationState',COALESCE((SELECT CASE WHEN state IN ('PENDING','COMPLETED','ERROR') THEN state ELSE 'OTHER' END FROM own),'MISSING'),
 'expectedBatches',COALESCE((SELECT LEAST(batch_count,257) FROM own),0),
 'attendanceApplied',(SELECT LEAST(count(*),257) FROM receipts WHERE participant='ATTENDANCE' AND result='APPLIED'),
 'attendanceError',(SELECT LEAST(count(*),257) FROM receipts WHERE participant='ATTENDANCE' AND result='ERROR'),
 'academicApplied',(SELECT LEAST(count(*),257) FROM receipts WHERE participant='ACADEMIC' AND result='APPLIED'),
 'academicError',(SELECT LEAST(count(*),257) FROM receipts WHERE participant='ACADEMIC' AND result='ERROR'),
 'receiptIdentityMatches',COALESCE((SELECT bool_and(matches) FROM receipts),true),
 'requestRows',(SELECT LEAST(count(*),257) FROM requests),
 'requestPending',(SELECT LEAST(count(*),257) FROM requests WHERE status='pending'),
 'requestSent',(SELECT LEAST(count(*),257) FROM requests WHERE status='sent'),
 'requestFailed',(SELECT LEAST(count(*),257) FROM requests WHERE status='failed'),
 'requestOther',(SELECT LEAST(count(*),257) FROM requests WHERE status NOT IN ('pending','sent','failed')),
 'requestIdentityMatches',COALESCE((SELECT bool_and(matches) FROM requests),true),
 'requestBatchCoverage',(SELECT LEAST(count(DISTINCT batch),257) FROM requests WHERE matches),
 'overflow',COALESCE((SELECT batch_count>256 FROM own),false) OR (SELECT count(*)>256 FROM requests) OR (SELECT count(*)>256 FROM receipts));
ROLLBACK;"""


def _academic_sql(v):
    return f"""BEGIN READ ONLY; SET LOCAL statement_timeout='2000ms';
WITH receipts AS MATERIALIZED (SELECT * FROM lesson_transfer_receipts WHERE operation_id='{v['operationId']}'::uuid),
acks AS MATERIALIZED (SELECT a.status,a.payload#>>'{{payload,batch_index}}' AS batch,
 COALESCE(a.payload->>'source'='academic-service' AND a.payload->'event_version'='1'::jsonb
 AND a.payload#>>'{{payload,participant}}'='ACADEMIC' AND a.payload#>'{{payload,retryable}}'='false'::jsonb
 AND a.payload#>'{{payload,source_lesson_id}}'=to_jsonb({v['sourceLessonId']}::bigint)
 AND a.payload#>'{{payload,target_lesson_id}}'=to_jsonb({v['targetLessonId']}::bigint)
 AND EXISTS(SELECT 1 FROM receipts r WHERE a.payload#>>'{{payload,batch_index}}'=r.batch_index::text
 AND a.payload#>>'{{payload,transfer_payload_hash}}'=encode(r.operation_hash,'hex')
 AND a.payload#>>'{{payload,result}}'=r.result
 AND a.payload#>>'{{payload,error_code}}' IS NOT DISTINCT FROM r.error_code),false) AS matches
 FROM academic_outbox a WHERE a.event_type='lesson.transfer.participant.applied'
 AND a.payload#>>'{{payload,operation_id}}'='{v['operationId']}')
SELECT json_build_object('principalMatches',current_user='rct_academic_runtime' AND current_database()='academic_db'
 AND NOT EXISTS(SELECT 1 FROM pg_roles WHERE rolname=current_user AND (rolsuper OR rolcreatedb OR rolcreaterole OR rolreplication OR rolbypassrls)),
 'receiptRows',(SELECT LEAST(count(*),257) FROM receipts),
 'expectedBatches',COALESCE((SELECT LEAST(max(batch_count),257) FROM receipts),0),
 'applied',(SELECT LEAST(count(*),257) FROM receipts WHERE result='APPLIED'),
 'error',(SELECT LEAST(count(*),257) FROM receipts WHERE result='ERROR'),
 'receiptIdentityMatches',COALESCE((SELECT bool_and(source_lesson_id={v['sourceLessonId']} AND target_lesson_id={v['targetLessonId']}) FROM receipts),true),
 'batchCountsConsistent',(SELECT count(DISTINCT batch_count)<=1 AND count(DISTINCT operation_hash)<=1 FROM receipts),
 'ackRows',(SELECT LEAST(count(*),257) FROM acks),
 'ackPending',(SELECT LEAST(count(*),257) FROM acks WHERE status='pending'),
 'ackSent',(SELECT LEAST(count(*),257) FROM acks WHERE status='sent'),
 'ackFailed',(SELECT LEAST(count(*),257) FROM acks WHERE status='failed'),
 'ackOther',(SELECT LEAST(count(*),257) FROM acks WHERE status NOT IN ('pending','sent','failed')),
 'ackIdentityMatches',COALESCE((SELECT bool_and(matches) FROM acks),true),
 'ackBatchCoverage',(SELECT LEAST(count(DISTINCT batch),257) FROM acks WHERE matches),
 'overflow',(SELECT count(*)>256 FROM receipts) OR (SELECT count(*)>256 FROM acks));
ROLLBACK;"""


def _pg(container, store, query, deadline):
    user = "rct_user" if store == "schedule" else "rct_academic_runtime"
    raw = _call(["docker", "exec", "-i", container, "psql", "-X", "-qAt", "--no-password",
                 "-U", user, "-d", store + "_db", "-v", "ON_ERROR_STOP=1"], deadline, query.encode())
    return _json(raw)


def _facts(value, store):
    counts, booleans = (COUNT_KEYS, SCHEDULE_BOOL) if store == "schedule" else (ACADEMIC_COUNTS, ACADEMIC_BOOL)
    extra = {"operationState"} if store == "schedule" else set()
    if (type(value) is not dict or set(value) != counts | booleans | extra
            or any(type(value[k]) is not int or not 0 <= value[k] <= MAX_COUNT for k in counts)
            or any(type(value[k]) is not bool for k in booleans)
            or value["principalMatches"] is not True
            or (extra and value["operationState"] not in ("PENDING", "COMPLETED", "ERROR", "OTHER", "MISSING"))):
        raise ValueError("Closed PG projection invalid")
    return {key: value[key] for key in sorted(counts | booleans | extra)}


def _project(value=None, schedule=None, academic=None, unavailable=False):
    result = {"schema": "lesson-transfer-stage.v1", "snapshotStage": "AFTER_LESSON_STEP_AND_CASE_CLEANUP",
              "snapshotAtomicity": "PER_DB_ONLY", "cleanupOutcome": "UNKNOWN", "mongo": "NOT_QUERIED",
              "schedulePrincipal": "EXISTING_E2E_BOOTSTRAP_NOT_PRODUCTION_ACL_PROOF",
              "qualification": "NOT_RUN", "schedule": None, "academic": None,
              "missingRowsMeaning": "MISSING_AT_STAGE_NOT_AT_DEADLINE",
              "outboxMeaning": "SENT_IS_NOT_APPLIED_ABSENT_ACK_IS_NOT_NONAPPLICATION"}
    if value is None:
        result.update(applicability="NOT_APPLICABLE", readOutcome="NOT_APPLICABLE")
        return result
    result["applicability"] = "FAILURE_STAGE" if value["observationOutcome"] == "TIMEOUT" else "OWN_COMPLETION_CONTROL"
    result["readOutcome"] = "UNAVAILABLE" if unavailable else "OBSERVED"
    if unavailable:
        return result
    result["schedule"], result["academic"] = dict(schedule), academic
    if not schedule["operationPresent"]:
        # The scoped receipt/outbox CTEs deliberately read nothing without the owned operation.
        # Their zero counts therefore authenticate no receipt/outbox absence, even at this stage.
        for key in COUNT_KEYS | {"receiptIdentityMatches", "requestIdentityMatches"}:
            result["schedule"][key] = None
    qualified = (value["observationOutcome"] == "RETURNED" and schedule["operationPresent"] and schedule["tupleMatches"]
                 and schedule["operationState"] == "COMPLETED" and not schedule["overflow"]
                 and schedule["receiptIdentityMatches"] and schedule["requestIdentityMatches"]
                 and academic["ackIdentityMatches"] and schedule["attendanceApplied"] == 1
                 and schedule["attendanceError"] == 0 and schedule["expectedBatches"] > 0
                 and schedule["academicApplied"] == schedule["expectedBatches"] and schedule["academicError"] == 0
                 and not academic["overflow"] and academic["receiptIdentityMatches"]
                 and academic["batchCountsConsistent"] and academic["error"] == 0
                 and academic["applied"] == academic["receiptRows"] == academic["expectedBatches"] == schedule["expectedBatches"])
    result["qualification"] = "PASS" if qualified else "NOT_CONFIRMED"
    return result


def main(argv=None):
    args = sys.argv[1:] if argv is None else argv
    if args not in (["--retain-private"], ["--snapshot"]):
        raise ValueError("Unsupported diagnostic mode")
    context = _context(os.environ)
    value = None
    try:
        if args == ["--retain-private"]:
            raw = sys.stdin.buffer.read(LIMIT + 1)
            if len(raw) > LIMIT:
                raise ValueError("Private input limit")
            value = _input(_json(raw))
            _stable(context)
            _write(context["run"], INPUT, value)
            return
        try:
            os.stat(INPUT, dir_fd=context["run"], follow_symlinks=False)
        except FileNotFoundError:
            _write(context["run"], OUTPUT, _project())
            return
        raw = _read(context["run"], INPUT)
        value = _input(_json(raw))
        deadline = time.monotonic() + 20
        _stable(context)
        containers = _containers(deadline)
        if _read(context["run"], INPUT) != raw:
            raise ValueError("Private input changed before PG read")
        _stable(context)
        schedule = _facts(_pg(containers["postgres-schedule"], "schedule", _schedule_sql(value), deadline), "schedule")
        if schedule["operationPresent"] and not schedule["tupleMatches"]:
            raise ValueError("Own operation tuple rejected")
        academic = _facts(_pg(containers["postgres-academic"], "academic", _academic_sql(value), deadline), "academic")
        if not academic["receiptIdentityMatches"]:
            raise ValueError("Own Academic receipt tuple rejected")
        _write(context["run"], OUTPUT, _project(value, schedule, academic))
    except BaseException:
        if args == ["--snapshot"]:
            try:
                _write(context["run"], OUTPUT, _project(value, unavailable=True) if value else {
                    **_project(), "applicability": "INVALID_PRIVATE_INPUT", "readOutcome": "UNAVAILABLE"})
            except (OSError, ValueError):
                pass
        raise
    finally:
        for fd in reversed(context["fds"]):
            os.close(fd)


if __name__ == "__main__":
    try:
        main()
    except BaseException:
        print("Own transfer stage diagnostic unavailable; original test outcome retained", file=sys.stderr)
        raise SystemExit(1) from None
