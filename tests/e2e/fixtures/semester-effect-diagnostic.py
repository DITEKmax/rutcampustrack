"""Inline in a separate failure-only CI step; never replace the semester test exit."""

import importlib.util
import json
import os
import re
import stat
import subprocess
import time
import uuid
from pathlib import Path
from types import SimpleNamespace


def _closed(value, choices):
    return next((choice for choice in choices if type(value) is str and value == choice), "UNKNOWN")


def _canonical_uuid(value):
    if type(value) is not str or str(uuid.UUID(value)) != value:
        raise ValueError("Invalid private identity")
    return value


def _receipt_flags(row, ledger):
    if row is None:
        return {"present": False, "result": "MISSING", "identityMatches": "UNKNOWN", "hashMatches": "UNKNOWN"}
    return {
        "present": True,
        "result": _closed(row.get("result"), ("PENDING", "APPLIED", "ERROR")),
        "identityMatches": row.get("source_event_id") == ledger["event_id"]
        and row.get("source_event_type") == ledger["event_type"]
        and row.get("target") == ledger["target"]
        and type(row.get("semester_id")) is int
        and row["semester_id"] == ledger["semester_id"],
        "hashMatches": row.get("payload_hash") == ledger["payload_hash"],
    }


def _ack_flags(envelope, ledger):
    payload = envelope.get("payload")
    if not isinstance(payload, dict):
        return False
    return (
        envelope.get("source") == ("attendance-service" if ledger["target"] == "ATTENDANCE" else "academic-service")
        and envelope.get("event_type") == "semester.archive.effect.ack"
        and type(envelope.get("event_version")) is int
        and envelope["event_version"] == 1
        and payload.get("source_event_id") == ledger["event_id"]
        and payload.get("target") == ledger["target"]
        and payload.get("source_event_type") == ledger["event_type"]
        and type(payload.get("semester_id")) is int
        and payload["semester_id"] == ledger["semester_id"]
        and payload.get("payload_hash") == ledger["payload_hash"]
        and payload.get("result") in ("APPLIED", "ERROR")
    )


def _ack_row(row, ledger):
    try:
        envelope = json.loads(row["payload"]) if type(row["payload"]) is str else row["payload"]
        event_id = _canonical_uuid(envelope["event_id"])
        matches = _ack_flags(envelope, ledger)
    except (KeyError, TypeError, ValueError, AttributeError):
        event_id, matches = None, False
    return {"status": row.get("status"), "identity_matches": matches, "event_id": event_id}


def _mongo_script(ledger):
    context = json.dumps(
        {
            "id": ledger["event_id"],
            "hash": ledger["payload_hash"],
            "semester": ledger["semester_id"],
            "type": ledger["event_type"],
        }
    )
    return (
        "const k="
        + context
        + r""";
const d=db.getSiblingDB('attendance_db');
const receipt=d.semester_archive_effect_receipts.find(
  {_id:k.id+':ATTENDANCE'},
  {_id:0,result:1,source_event_id:1,source_event_type:1,target:1,semester_id:1,payload_hash:1}
).maxTimeMS(2000).limit(1).toArray()[0]||null;
if(receipt) receipt.semester_id=Number(receipt.semester_id);
const pattern='"source_event_id"\\s*:\\s*"'+k.id+'"';
const acks=d.attendance_outbox.find(
  {event_type:'semester.archive.effect.ack',payload:{$regex:pattern}},
  {_id:0,status:1,payload:1}
).sort({created_at:-1}).maxTimeMS(2000).limit(3).toArray();
print(JSON.stringify({receipt:receipt,acks:acks}));
"""
    )


def _mongo_claim_script(ledger):
    # MongoIdempotencyStore persists event_id as a canonical UUID string.
    return (
        "const id="
        + json.dumps(ledger["event_id"])
        + r""";
const d=db.getSiblingDB('attendance_db');
print(JSON.stringify(d.event_consumer_processed.find(
  {consumer_id:'attendance',event_id:id},{_id:1}
).maxTimeMS(2000).limit(1).toArray().length===1));
"""
    )


def _transport_flags(queues=None, bindings=None):
    # Queue counts describe the observation after fixture cleanup, never delivery
    # of the selected event. No message-get, consumer or requeue operation is used.
    result = {
        "available": False,
        "bindingPresent": "UNKNOWN",
        "mainPresent": "UNKNOWN",
        "consumers": "UNKNOWN",
        "messagesReady": "UNKNOWN",
        "messagesUnacknowledged": "UNKNOWN",
        "dlqPresent": "UNKNOWN",
        "dlqMessagesReady": "UNKNOWN",
        "dlqMessagesUnacknowledged": "UNKNOWN",
        "exactDlqEventPresent": "UNKNOWN",
    }
    if (
        type(queues) is not list
        or type(bindings) is not list
        or len(queues) > 512
        or len(bindings) > 512
        or any(type(row) is not dict or type(row.get("name")) is not str for row in queues)
        or any(
            type(row) is not dict
            or any(type(row.get(key)) is not str for key in ("source_name", "destination_name", "destination_kind"))
            for row in bindings
        )
    ):
        return result

    def count(row, key):
        value = row.get(key)
        return value if type(value) is int and 0 <= value <= 1000000000 else "UNKNOWN"

    names = ("attendance-service.events", "attendance-service.events.dlq")
    selected = [[row for row in queues if row["name"] == name] for name in names]
    if any(len(rows) > 1 for rows in selected):
        return result
    main, dead = (rows[0] if rows else {} for rows in selected)
    result.update(
        available=True,
        bindingPresent=any(
            row["source_name"] == "rut-uit.events"
            and row["destination_name"] == names[0]
            and row["destination_kind"] == "queue"
            for row in bindings
        ),
        mainPresent=bool(main),
        consumers=count(main, "consumers"),
        messagesReady=count(main, "messages_ready"),
        messagesUnacknowledged=count(main, "messages_unacknowledged"),
        dlqPresent=bool(dead),
        dlqMessagesReady=count(dead, "messages_ready"),
        dlqMessagesUnacknowledged=count(dead, "messages_unacknowledged"),
    )
    return result


def _consumer_failure_hint(logs=None):
    # Spring AMQP 3.2.12 renders bodies over 50 bytes as byte-array labels.
    # The sender sets only event_type, not an event/message/correlation ID.
    # These untrusted queue/type hints NEVER identify the ledger candidate.
    result = {
        "observation": "UNAVAILABLE",
        "bodyIdentityUnavailable": True,
        "exactCandidateMatched": False,
        "hintRecords": 0,
        "sampleCapped": False,
        "exceptionKinds": [],
        "grpcCodes": [],
        "sourceFrames": [],
    }
    if type(logs) is not bytes or len(logs) > 262144:
        return result

    def unique_object(pairs):
        obj = {}
        for key, value in pairs:
            if key in obj:
                raise ValueError("Duplicate private log key")
            obj[key] = value
        return obj

    try:
        lines = logs.decode("utf-8").splitlines()
    except UnicodeError:
        return result
    if len(lines) > 200:
        return result
    result.update(observation="NO_QUEUE_TYPE_HINT", sampleCapped=len(lines) == 200)
    exceptions = {
        "ru.rutcampustrack.attendance.exception.AcademicServiceUnavailableException": "ACADEMIC_UNAVAILABLE",
        "ru.rutcampustrack.attendance.exception.ScheduleServiceUnavailableException": "SCHEDULE_UNAVAILABLE",
        "ru.rutcampustrack.attendance.contract.exception.ResourceNotFoundException": "RESOURCE_NOT_FOUND",
        "ru.rutcampustrack.attendance.event.SemesterArchiveEffectRejectedException": "EFFECT_REJECTED",
        "java.lang.IllegalArgumentException": "INVALID_ARGUMENT",
        "io.grpc.StatusRuntimeException": "GRPC_STATUS",
        "org.springframework.transaction.UnexpectedRollbackException": "TRANSACTION_ROLLBACK",
        "org.springframework.data.mongodb.MongoTransactionException": "MONGO_TRANSACTION",
        "org.springframework.dao.DuplicateKeyException": "DUPLICATE_KEY",
    }
    frames = {
        "ru.rutcampustrack.attendance.event.EventConsumer.handleLessonClosed": "CLOSE_HANDLER",
        "ru.rutcampustrack.attendance.event.LessonEventService.prepareLessonClosed": "PREPARE_CLOSE",
        "ru.rutcampustrack.attendance.event.LessonEventService.validateLessonSnapshot": "LESSON_SNAPSHOT",
        "ru.rutcampustrack.attendance.event.LessonEventService.validateRosterEcho": "ROSTER_ECHO",
        "ru.rutcampustrack.attendance.event.LessonEventService.preflightLessonClosed": "CLOSE_PREFLIGHT",
        "ru.rutcampustrack.attendance.event.SemesterArchiveEffectService.parse": "EFFECT_ENVELOPE",
        "ru.rutcampustrack.attendance.event.SemesterArchiveEffectService.applyInCurrentTransaction": "EFFECT_TRANSACTION",
        "ru.rutcampustrack.attendance.grpc.AcademicGrpcClient.getGroupMembers": "ACADEMIC_ROSTER",
        "ru.rutcampustrack.attendance.grpc.ScheduleGrpcClient.getLessonById": "SCHEDULE_LESSON",
        "ru.rutcampustrack.shared.events.IdempotencyGuard.tryClaim": "IDEMPOTENCY_CLAIM",
    }
    grpc_codes = (
        "CANCELLED",
        "UNKNOWN",
        "INVALID_ARGUMENT",
        "DEADLINE_EXCEEDED",
        "NOT_FOUND",
        "ALREADY_EXISTS",
        "PERMISSION_DENIED",
        "RESOURCE_EXHAUSTED",
        "FAILED_PRECONDITION",
        "ABORTED",
        "OUT_OF_RANGE",
        "UNIMPLEMENTED",
        "INTERNAL",
        "UNAVAILABLE",
        "DATA_LOSS",
        "UNAUTHENTICATED",
    )
    kinds, codes, locations = set(), set(), set()
    for line in lines:
        try:
            row = json.loads(line, object_pairs_hook=unique_object)
            if (
                type(row) is not dict
                or row.get("service") != "attendance-service"
                or row.get("level") != "WARN"
                or row.get("logger") != "org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer"
                or type(row.get("msg")) is not str
                or type(row.get("stack")) is not str
            ):
                continue
            message = re.fullmatch(
                r"Retries exhausted for message \(Body:'\[B@[0-9a-f]+\(byte\[[0-9]+\]\)' MessageProperties \[(.*)\]\)",
                row["msg"],
            )
            if not message:
                continue
            parts = message[1].split(", ")
            if parts[0] != "headers={event_type=lesson.closed}":
                continue
            properties = {}
            for part in parts[1:]:
                key, separator, value = part.partition("=")
                if not separator or key in properties or not re.fullmatch(r"[A-Za-z]+", key):
                    raise ValueError("Ambiguous private message properties")
                properties[key] = value
            if (
                properties.get("consumerQueue") != "attendance-service.events"
                or properties.get("receivedExchange") != "rut-uit.events"
                or properties.get("receivedRoutingKey") != ""
            ):
                continue
            result["hintRecords"] += 1
            for stack_line in row["stack"].splitlines():
                cause = re.fullmatch(r"(?:Caused by: )?([A-Za-z0-9_.$]+)(?::(.*))?", stack_line)
                if cause and cause[1] in exceptions:
                    kinds.add(exceptions[cause[1]])
                    # Only the known exception line is classified; descriptions
                    # and unknown class names are never copied into the output.
                    status = re.search(r"(?:Status\{code=|^\s*)([A-Z_]+)(?:,|:)", cause[2] or "")
                    if status and status[1] in grpc_codes:
                        codes.add(status[1])
                frame = re.fullmatch(r"\s+at ([A-Za-z0-9_.$]+)\([A-Za-z0-9_.$]+\.java:[0-9]+\)", stack_line)
                if frame and frame[1] in frames:
                    locations.add(frames[frame[1]])
        except (ValueError, TypeError, KeyError, AttributeError, RecursionError):
            continue
    if result["hintRecords"]:
        result.update(
            observation="UNMATCHED_QUEUE_EVENT_TYPE_HINT",
            exceptionKinds=sorted(kinds),
            grpcCodes=sorted(codes),
            sourceFrames=sorted(locations),
        )
    return result


def _project(
    ledger,
    producer,
    receipt,
    acknowledgements,
    claims,
    stable,
    barrier_reason="SCHEDULE_EFFECT_ACK_PENDING",
    consumer_claim="UNKNOWN",
    queues=None,
    bindings=None,
    consumer_logs=None,
):
    def boolean(value):
        return value if type(value) is bool else "UNKNOWN"

    def states(rows):
        return sorted({_closed(row.get("status"), ("pending", "sent", "failed")) for row in rows})

    return {
        "schema": "semester-effect.v3",
        "observationPhase": "AFTER_FIXTURE_CLEANUP_BEFORE_CONTAINER_TEARDOWN",
        "found": ledger is not None,
        "selection": "LEDGER_CANDIDATE"
        if ledger is not None
        else (
            "LEGACY_OUTBOX_UNAVAILABLE"
            if barrier_reason == "SCHEDULE_LEGACY_EFFECT_UNPROVEN"
            else "NO_LEDGER_CANDIDATE"
        ),
        "eventKind": _closed(
            (ledger or {}).get("event_type"),
            (
                "lesson.closed",
                "lesson.cancelled",
                "lesson.deleted",
                "lesson.one_off.cancelled",
                "homework.binding.archived",
            ),
        ),
        "target": _closed((ledger or {}).get("target"), ("ATTENDANCE", "ACADEMIC")),
        "ledgerState": _closed((ledger or {}).get("state"), ("PENDING", "ERROR", "APPLIED")),
        "scopeMissing": ledger is not None and ledger.get("semester_id") is None,
        "sourceMatches": boolean((ledger or {}).get("source_matches")),
        "versionMatches": boolean((ledger or {}).get("version_matches")),
        "payloadScopeMatches": boolean((ledger or {}).get("scope_matches")),
        "payloadTypeMatches": boolean((ledger or {}).get("type_matches")),
        "payloadEventMatches": boolean((ledger or {}).get("event_matches")),
        "producerPresent": bool(producer),
        "producerStates": states(producer),
        "producerSampleCapped": len(producer) == 3,
        "receipt": {
            "present": boolean(receipt.get("present")),
            "result": _closed(receipt.get("result"), ("MISSING", "PENDING", "APPLIED", "ERROR")),
            "identityMatches": boolean(receipt.get("identityMatches")),
            "hashMatches": boolean(receipt.get("hashMatches")),
        },
        "ackPresent": bool(acknowledgements),
        "ackStates": states(acknowledgements),
        "ackSampleCapped": len(acknowledgements) == 3,
        "ackIdentityMatches": all(row["identity_matches"] is True for row in acknowledgements)
        if acknowledgements
        else "UNKNOWN",
        "ackClaimPresent": any(claims)
        if acknowledgements and all(type(value) is bool for value in claims)
        else "UNKNOWN",
        "stableLedger": boolean(stable),
        "consumerClaimPresent": boolean(consumer_claim),
        "transport": _transport_flags(queues, bindings),
        "consumerFailureHint": _consumer_failure_hint(consumer_logs),
    }


def main():
    # No exception, command, captured stdout/stderr, private identity or credential is printed.
    try:
        root = Path(os.environ["GITHUB_WORKSPACE"])
        temporary = Path(os.environ["RUNNER_TEMP"])
        run_id, attempt = os.environ["GITHUB_RUN_ID"], os.environ["GITHUB_RUN_ATTEMPT"]
        if not all(re.fullmatch(r"[1-9][0-9]*", value) for value in (run_id, attempt)):
            raise ValueError("Invalid CI identity")
        project = "rct-recovery-" + run_id + "-" + attempt + "-dst"
        if os.environ["RCT_APP_RECOVERY_PROJECT"] != project:
            raise ValueError("Unexpected recovery project")
        recovery = temporary / ("rct-application-recovery-" + run_id + "-" + attempt)
        output = temporary / ("rct-semester-lifecycle-" + run_id + "-" + attempt)
        if os.environ["RCT_APP_RECOVERY_RUN"] != str(recovery) or os.environ["RCT_SEMESTER_LIFECYCLE_RUN"] != str(
            output
        ):
            raise ValueError("Unexpected owned path")

        def guarded(path, size, mode, directory=False):
            value = path.lstat()
            if stat.S_ISLNK(value.st_mode) or stat.S_IMODE(value.st_mode) != mode or value.st_uid != os.getuid():
                raise ValueError("Unowned diagnostic input")
            if directory:
                if not stat.S_ISDIR(value.st_mode):
                    raise ValueError("Invalid directory")
            elif not stat.S_ISREG(value.st_mode) or value.st_size > size:
                raise ValueError("Unbounded diagnostic input")

        for directory, marker in ((recovery, "RCT_APP_RECOVERY_DISPOSABLE"), (output, "RCT_SEMESTER_DISPOSABLE")):
            guarded(directory, 0, 0o700, True)
            guarded(directory / "disposable-target", 64, 0o600)
            if (directory / "disposable-target").read_text().strip() != marker:
                raise ValueError("Marker mismatch")
        guarded(output / "lifecycle.safe.json", 4096, 0o600)
        progress = json.loads((output / "lifecycle.safe.json").read_text())
        barrier_reason = progress.get("primary", {}).get("reasonCode")
        if (
            progress.get("schema") != "semester-lifecycle.v1"
            or progress.get("primaryFailure") is not True
            or barrier_reason
            not in ("SCHEDULE_EFFECT_ACK_PENDING", "SCHEDULE_LEGACY_EFFECT_UNPROVEN", "SCHEDULE_EFFECT_SCOPE_UNPROVEN")
        ):
            raise ValueError("Different semester failure")
        guarded(recovery / "seed.json", 65536, 0o600)
        seed = json.loads((recovery / "seed.json").read_text())
        match = re.fullmatch(r"/api/v1/student/attendance\?semesterId=([1-9][0-9]*)", seed.get("historyUrl", ""))
        if not match or int(match[1]) > 9007199254740991:
            raise ValueError("Invalid private semester pin")
        semester = int(match[1])
        spec = importlib.util.spec_from_file_location("recovery", root / "scripts/recovery.py")
        r = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(r)
        deadline = time.monotonic() + 50

        def bounded_run(command, *, stdin=None, stdout=None, input_data=None, minimum_timeout=0):
            child_timeout = min(5, deadline - time.monotonic())
            if stdin is not None or stdout is not None or child_timeout <= minimum_timeout:
                raise ValueError("Diagnostic deadline")
            child = subprocess.run(
                command,
                input=input_data,
                capture_output=True,
                check=False,
                timeout=child_timeout,
            )
            if child.returncode or len(child.stdout) > 262144 or len(child.stderr) > 262144:
                raise ValueError("Diagnostic read unavailable")
            return child.stdout

        # Same helper semantics/captured output, adding only per-child deadline and output bound.
        r.run = bounded_run

        def container(service):
            found = (
                r.run(
                    [
                        "docker",
                        "ps",
                        "-q",
                        "--filter",
                        "label=com.docker.compose.project=" + project,
                        "--filter",
                        "label=com.docker.compose.service=" + service,
                        "--filter",
                        "label=io.rutcampustrack.recovery=disposable",
                    ]
                )
                .decode()
                .split()
            )
            if len(found) != 1 or not re.fullmatch(r"[0-9a-f]{12,64}", found[0]):
                raise ValueError("Ambiguous owned target")
            return found[0]

        args = SimpleNamespace(
            academic_container=container("postgres-academic"),
            schedule_container=container("postgres-schedule"),
            mongo_container=container("mongo-attendance"),
            target_project=project,
        )
        try:
            r.check_targets(args, restore=True)  # Read-only identity check; invokes no restore.
        except r.RecoveryError:
            raise ValueError("Owned diagnostic targets unavailable") from None
        env_file = root / "tests/e2e/.env.ci"
        env_info = env_file.lstat()
        if (
            not stat.S_ISREG(env_info.st_mode)
            or stat.S_ISLNK(env_info.st_mode)
            or env_info.st_size > 65536
            or stat.S_IMODE(env_info.st_mode) & 0o022
        ):
            raise ValueError("Unsafe synthetic CI configuration")
        try:
            secret = r.credentials(env_file)
        except r.RecoveryError:
            raise ValueError("Synthetic diagnostic configuration unavailable") from None

        def pg(store, query):
            result = r.pg_query(
                getattr(args, store + "_container"),
                secret["POSTGRES_" + store.upper() + "_PASSWORD"],
                store + "_db",
                "BEGIN READ ONLY; SET LOCAL statement_timeout='2000ms'; " + query + "; COMMIT;",
            )
            values = [line for line in result.splitlines() if line not in ("BEGIN", "SET", "COMMIT")]
            if len(values) != 1:
                raise ValueError("Ambiguous SQL projection")
            return json.loads(values[0])

        # Same scope/state as the barrier, including global NULL scope. Its tie ordering is
        # unspecified; this deterministic ledger candidate is not a unique cause under ties.
        # Legacy outbox UNION rows are not inspected: report that limitation explicitly.
        ledger = pg(
            "schedule",
            "SELECT coalesce((SELECT row_to_json(e) FROM (SELECT event_id,target,event_type,semester_id,scope_id,encode(payload_hash,'hex') AS payload_hash,state,(event_payload->>'source'='schedule-service') AS source_matches,(event_payload->'event_version'='1'::jsonb) AS version_matches,(event_payload#>'{payload,semester_id}'=to_jsonb(semester_id) AND scope_id=semester_id) AS scope_matches,(event_payload->>'event_type'=event_type) AS type_matches,(event_payload->>'event_id'=event_id::text) AS event_matches FROM schedule_semester_archive_effect_ledger WHERE (semester_id="
            + str(semester)
            + " OR semester_id IS NULL) AND state<>'APPLIED' ORDER BY created_at,event_id,target,event_type,scope_id LIMIT 1) e),'null'::json)",
        )
        producer, acknowledgements, claims = [], [], []
        consumer_claim = "UNKNOWN"
        receipt = _receipt_flags(None, {})
        stable = True
        if ledger is not None:
            identity = _canonical_uuid(ledger["event_id"])
            if not re.fullmatch(r"[0-9a-f]{64}", ledger["payload_hash"]) or ledger["target"] not in (
                "ATTENDANCE",
                "ACADEMIC",
            ):
                raise ValueError("Invalid private effect key")
            if (
                _closed(
                    ledger["event_type"],
                    (
                        "lesson.closed",
                        "lesson.cancelled",
                        "lesson.deleted",
                        "lesson.one_off.cancelled",
                        "homework.binding.archived",
                    ),
                )
                == "UNKNOWN"
            ):
                raise ValueError("Invalid private effect type")
            producer = pg(
                "schedule",
                "SELECT coalesce(json_agg(t),'[]'::json) FROM (SELECT status FROM schedule_outbox WHERE payload->>'event_id'='"
                + identity
                + "' AND event_type='"
                + ledger["event_type"]
                + "' ORDER BY created_at DESC LIMIT 3) t",
            )
            if ledger["target"] == "ATTENDANCE":
                observed = r.mongo_query(args.mongo_container, secret["MONGO_ROOT_PASSWORD"], _mongo_script(ledger))
                raw_receipt = observed["receipt"]
                if raw_receipt is not None:
                    raw_receipt["semester_id"] = int(raw_receipt["semester_id"])
                receipt = _receipt_flags(raw_receipt, ledger)
                raw_acks = observed["acks"]
                for row in raw_acks:
                    acknowledgements.append(_ack_row(row, ledger))
            else:
                raw_receipt = pg(
                    "academic",
                    "SELECT coalesce((SELECT json_build_object('source_event_id',source_event_id,'source_event_type',event_type,'target','ACADEMIC','semester_id',semester_id,'payload_hash',encode(payload_hash,'hex'),'result',state) FROM academic_semester_archive_effect_receipts WHERE source_event_id='"
                    + identity
                    + "'::uuid),'null'::json)",
                )
                receipt = _receipt_flags(raw_receipt, ledger)
                raw_acks = pg(
                    "academic",
                    "SELECT coalesce(json_agg(t),'[]'::json) FROM (SELECT status,payload FROM academic_outbox WHERE event_type='semester.archive.effect.ack' AND payload#>>'{payload,source_event_id}'='"
                    + identity
                    + "' ORDER BY created_at DESC LIMIT 3) t",
                )
                for row in raw_acks:
                    acknowledgements.append(_ack_row(row, ledger))
            for row in acknowledgements:
                claims.append(
                    pg(
                        "schedule",
                        "SELECT to_json(EXISTS(SELECT 1 FROM event_consumer_processed WHERE consumer_id='schedule' AND event_id='"
                        + row["event_id"]
                        + "'::uuid))",
                    )
                    if row["event_id"]
                    else "UNKNOWN"
                )
            current = pg(
                "schedule",
                "SELECT coalesce(to_json((SELECT state FROM schedule_semester_archive_effect_ledger WHERE event_id='"
                + identity
                + "'::uuid AND target='"
                + ledger["target"]
                + "' AND event_type='"
                + ledger["event_type"]
                + "' AND scope_id="
                + str(int(ledger["scope_id"]))
                + ")),'null'::json)",
            )
            stable = current == ledger["state"]
        queues, bindings, consumer_logs = None, None, None
        if ledger is not None and ledger["target"] == "ATTENDANCE":
            try:
                # All primary reads are already retained. A separate optional
                # child timeout must not discard their receipt/ACK projection.
                consumer_claim = r.mongo_query(
                    args.mongo_container, secret["MONGO_ROOT_PASSWORD"], _mongo_claim_script(ledger)
                )
            except (
                OSError,
                ValueError,
                TypeError,
                LookupError,
                AttributeError,
                RuntimeError,
                subprocess.SubprocessError,
            ):
                consumer_claim = "UNKNOWN"
            try:
                # Recovery retains original applications in rct-e2e. Admit only
                # that Attendance service; fixed inspect excludes Config.Env.
                attendance_ids = (
                    r.run(
                        [
                            "docker",
                            "ps",
                            "-q",
                            "--filter",
                            "label=com.docker.compose.project=rct-e2e",
                            "--filter",
                            "label=com.docker.compose.service=attendance-service",
                        ]
                    )
                    .decode()
                    .split()
                )
                if len(attendance_ids) != 1 or not re.fullmatch(r"[0-9a-f]{12,64}", attendance_ids[0]):
                    raise ValueError("Ambiguous source Attendance")
                attendance = attendance_ids[0]
                info = json.loads(
                    r.run(
                        [
                            "docker",
                            "inspect",
                            "--format",
                            '{"labels":{{json .Config.Labels}},"networks":{{json .NetworkSettings.Networks}}}',
                            attendance,
                        ]
                    )
                )
                if (
                    type(info) is not dict
                    or info["labels"].get("com.docker.compose.project") != "rct-e2e"
                    or info["labels"].get("com.docker.compose.service") != "attendance-service"
                    or "rct-e2e_private_net" not in info["networks"]
                ):
                    raise ValueError("Unexpected source Attendance")
                consumer_logs = r.run(["docker", "logs", "--tail", "200", "--since", "30m", attendance])
            except (
                OSError,
                ValueError,
                TypeError,
                LookupError,
                AttributeError,
                RuntimeError,
                subprocess.SubprocessError,
            ):
                # All primary reads survive optional log/deadline failures.
                consumer_logs = None
            try:
                # Recovery restarts the original rct-e2e broker; only its DBs
                # are fresh recovery-project containers. Admit that exact source
                # project/service/network before invoking passive JSON CLI reads.
                broker_ids = (
                    r.run(
                        [
                            "docker",
                            "ps",
                            "-q",
                            "--filter",
                            "label=com.docker.compose.project=rct-e2e",
                            "--filter",
                            "label=com.docker.compose.service=rabbitmq",
                        ]
                    )
                    .decode()
                    .split()
                )
                if len(broker_ids) != 1 or not re.fullmatch(r"[0-9a-f]{12,64}", broker_ids[0]):
                    raise ValueError("Ambiguous source broker")
                broker = broker_ids[0]
                info = json.loads(
                    r.run(
                        [
                            "docker",
                            "inspect",
                            "--format",
                            '{"labels":{{json .Config.Labels}},"networks":{{json .NetworkSettings.Networks}}}',
                            broker,
                        ]
                    )
                )
                if (
                    type(info) is not dict
                    or info["labels"].get("com.docker.compose.project") != "rct-e2e"
                    or info["labels"].get("com.docker.compose.service") != "rabbitmq"
                    or "rct-e2e_private_net" not in info["networks"]
                ):
                    raise ValueError("Unexpected source broker")
                # The known Alpine broker supplies BusyBox timeout. SIGKILL
                # stops the remote CLI even when it cannot service an RPC
                # timeout; rabbitmqctl execs its Erlang VM in the same process.
                # Reserve >4s of the host/global budget for its 3s lifetime.
                prefix = [
                    "docker",
                    "exec",
                    broker,
                    "/bin/busybox",
                    "timeout",
                    "-s",
                    "KILL",
                    "3",
                    "rabbitmqctl",
                    "-q",
                    "--timeout",
                    "2",
                    "--formatter",
                    "json",
                ]
                queues = json.loads(
                    r.run(
                        [
                            *prefix,
                            "list_queues",
                            "-p",
                            "/",
                            "name",
                            "messages_ready",
                            "messages_unacknowledged",
                            "consumers",
                        ],
                        minimum_timeout=4,
                    )
                )
                bindings = json.loads(
                    r.run(
                        [
                            *prefix,
                            "list_bindings",
                            "-p",
                            "/",
                            "source_name",
                            "destination_name",
                            "destination_kind",
                        ],
                        minimum_timeout=4,
                    )
                )
            except (
                OSError,
                ValueError,
                TypeError,
                LookupError,
                AttributeError,
                RuntimeError,
                subprocess.SubprocessError,
            ):
                # Optional transport observation never discards the existing
                # durable ledger/receipt projection or the original test failure.
                queues, bindings = None, None
        result = _project(
            ledger,
            producer,
            receipt,
            acknowledgements,
            claims,
            stable,
            barrier_reason,
            consumer_claim,
            queues,
            bindings,
            consumer_logs,
        )
        encoded = json.dumps(result, separators=(",", ":")).encode()
        if len(encoded) > 4096:
            raise ValueError("Diagnostic projection bound")
        guarded(output, 0, 0o700, True)
        descriptor = os.open(output / "effect.safe.json", os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, 0o600)
        with os.fdopen(descriptor, "wb") as stream:
            stream.write(encoded)
    except (
        OSError,
        ValueError,
        TypeError,
        LookupError,
        AttributeError,
        ImportError,
        RuntimeError,
        subprocess.SubprocessError,
    ):
        raise SystemExit("Semester effect diagnostic unavailable") from None


if __name__ == "__main__":
    main()
