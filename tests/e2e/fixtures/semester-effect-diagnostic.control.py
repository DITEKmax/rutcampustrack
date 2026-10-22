"""RAM-only controls for the real projection; main and DB/process code never execute."""

import ast
import copy
import importlib.util
import json
import os
import tempfile
from pathlib import Path
from unittest.mock import patch

source_path = Path(__file__).with_name("semester-effect-diagnostic.py")
source = source_path.read_text()
tree = ast.parse(source)
names = {
    "_closed",
    "_canonical_uuid",
    "_receipt_flags",
    "_ack_flags",
    "_ack_row",
    "_mongo_script",
    "_project",
    "_consumer_failure_hint",
    "_unique_candidate_trace",
    "_effect_applicability",
}
selected = [node for node in tree.body if isinstance(node, ast.FunctionDef) and node.name in names]
assert {node.name for node in selected} == names
assert not any(isinstance(node, (ast.Import, ast.ImportFrom)) for function in selected for node in ast.walk(function))
module_spec = importlib.util.spec_from_file_location("semester_effect_control_target", source_path)
module = importlib.util.module_from_spec(module_spec)
module_spec.loader.exec_module(module)
scope = vars(module)

barrier_reasons = (
    "SCHEDULE_EFFECT_ACK_PENDING",
    "SCHEDULE_LEGACY_EFFECT_UNPROVEN",
    "SCHEDULE_EFFECT_SCOPE_UNPROVEN",
)
other_reasons = (
    "NONE",
    "SCHEDULE_TRANSFER_PENDING",
    "SCHEDULE_PUBLICATION_PENDING",
    "SCHEDULE_EDIT_PENDING",
    "ACADEMIC_EDIT_PENDING",
    "ACADEMIC_PUBLICATION_PENDING",
    "ACADEMIC_ADMISSION_PENDING",
    "ACADEMIC_TRANSFER_PENDING",
    "ACADEMIC_INITIAL_MEMBERSHIP_ACK_PENDING",
    "ACADEMIC_EFFECT_ACK_PENDING",
    "SCHEDULE_CANCELLATION_ACK_PENDING",
    "ARCHIVE_DRAIN_PENDING",
    "ARCHIVE_SEAL_ACK_PENDING",
    "RESTORE_RELEASE_PENDING",
    "STALE_STATE_VERSION",
    "ARCHIVE_TRANSITION_IN_PROGRESS",
    "ARCHIVE_PREPARE_VERSION_MISMATCH",
    "ATTENDANCE_EFFECT_REQUIRES_RECONCILIATION",
    "ATTENDANCE_TRANSFER_REQUIRES_RECONCILIATION",
    "RESTORE_REQUIRES_ARCHIVE_PREPARE",
    "RESTORE_RELEASE_VERSION_MISMATCH",
)


def failure_receipt(reason):
    return {"schema": "semester-lifecycle.v1", "primaryFailure": True, "primary": {"reasonCode": reason}}


for reason in (*barrier_reasons, *other_reasons):
    applicability = scope["_effect_applicability"](failure_receipt(reason))
    assert applicability == {
        "schema": "semester-effect.applicability.v1",
        "applicability": "APPLICABLE" if reason in barrier_reasons else "NOT_APPLICABLE",
        "reasonCode": reason,
        "primaryFailure": True,
    }
for invalid in (
    None,
    [],
    {},
    dict(failure_receipt("NONE"), schema="foreign"),
    dict(failure_receipt("NONE"), primaryFailure=False),
    dict(failure_receipt("NONE"), primaryFailure=1),
    dict(failure_receipt("NONE"), primary=[]),
    *(failure_receipt(reason) for reason in (None, [], True, 1, "UNKNOWN", "PRIVATE_UNRECOGNIZED_REASON")),
):
    try:
        scope["_effect_applicability"](invalid)
    except ValueError:
        pass
    else:
        raise AssertionError("Invalid failure must not become NOT_APPLICABLE")


def invoke_applicability(reason, *, raw=None, reader_failure=False):
    # Real entrypoint/path/privacy checks, synthetic files only. Missing seed and
    # a forbidden importer prove nonbarrier results precede every DB/process read.
    with tempfile.TemporaryDirectory(prefix="rct-semester-applicability-control-") as folder:
        base = Path(folder)
        recovery = base / "rct-application-recovery-1-1"
        output = base / "rct-semester-lifecycle-1-1"
        for directory, marker in ((recovery, "RCT_APP_RECOVERY_DISPOSABLE"), (output, "RCT_SEMESTER_DISPOSABLE")):
            directory.mkdir(mode=0o700)
            path = directory / "disposable-target"
            path.write_text(marker)
            path.chmod(0o600)
        lifecycle = output / "lifecycle.safe.json"
        lifecycle.write_text(json.dumps(failure_receipt(reason)) if raw is None else raw)
        lifecycle.chmod(0o600)
        env = {
            "GITHUB_WORKSPACE": str(base),
            "RUNNER_TEMP": str(base),
            "GITHUB_RUN_ID": "1",
            "GITHUB_RUN_ATTEMPT": "1",
            "RCT_APP_RECOVERY_PROJECT": "rct-recovery-1-1-dst",
            "RCT_APP_RECOVERY_RUN": str(recovery),
            "RCT_SEMESTER_LIFECYCLE_RUN": str(output),
        }
        if reader_failure:
            lifecycle.unlink()
        with patch.dict(os.environ, env), patch.object(
            module.importlib.util, "spec_from_file_location", side_effect=AssertionError("DB/process import forbidden")
        ):
            try:
                module.main()
            except SystemExit as error:
                assert str(error) == "Semester effect diagnostic unavailable"
                assert not (output / "effect.safe.json").exists()
                return None
        result = output / "effect.safe.json"
        assert result.stat().st_mode & 0o777 == 0o600 and result.stat().st_size <= 4096
        assert json.loads(lifecycle.read_text())["primaryFailure"] is True
        return json.loads(result.read_text())


for reason in ("NONE", "ACADEMIC_EDIT_PENDING"):
    assert invoke_applicability(reason)["applicability"] == "NOT_APPLICABLE"
for reason in barrier_reasons:
    assert invoke_applicability(reason) is None  # Required barrier reader/seed failure remains strict.
for reason in ("UNKNOWN", "PRIVATE_UNRECOGNIZED_REASON"):
    assert invoke_applicability(reason) is None
assert invoke_applicability("NONE", raw="{") is None
assert invoke_applicability("NONE", reader_failure=True) is None

marker = "SYNTHETIC_PRIVATE_MARKER_NOT_FOR_OUTPUT"
identity = "00000000-0000-4000-8000-000000000001"
ack_id = "00000000-0000-4000-8000-000000000002"
ledger = {
    "event_id": identity,
    "event_type": "lesson.closed",
    "target": "ATTENDANCE",
    "state": "PENDING",
    "semester_id": 7,
    "payload_hash": "a" * 64,
    "source_matches": True,
    "version_matches": True,
    "scope_matches": True,
    "type_matches": True,
    "event_matches": True,
}
receipt_row = {
    "source_event_id": identity,
    "source_event_type": "lesson.closed",
    "target": "ATTENDANCE",
    "semester_id": 7,
    "payload_hash": "a" * 64,
    "result": "APPLIED",
}
receipt = scope["_receipt_flags"](receipt_row, ledger)
assert receipt == {"present": True, "result": "APPLIED", "identityMatches": True, "hashMatches": True}
envelope = {
    "event_id": ack_id,
    "source": "attendance-service",
    "event_type": "semester.archive.effect.ack",
    "event_version": 1,
    "payload": {
        "source_event_id": identity,
        "target": "ATTENDANCE",
        "source_event_type": "lesson.closed",
        "semester_id": 7,
        "payload_hash": "a" * 64,
        "result": "APPLIED",
        "blocking_reason": marker,
    },
}
assert scope["_ack_flags"](envelope, ledger) is True
ack = scope["_ack_row"]({"status": "sent", "payload": json.dumps(envelope)}, ledger)
result = scope["_project"](ledger, [{"status": "sent"}], receipt, [ack], [False], True)
assert result["eventKind"] == "lesson.closed" and result["producerStates"] == ["sent"]
assert result["ackPresent"] is True and result["ackIdentityMatches"] is True and result["ackClaimPresent"] is False
assert result["stableLedger"] is True
serialized = json.dumps(result)
assert all(value not in serialized for value in (marker, identity, ack_id, "a" * 64))

for field, value in (("source", marker), ("event_version", 2), ("event_version", True)):
    changed = copy.deepcopy(envelope)
    changed[field] = value
    assert scope["_ack_flags"](changed, ledger) is False
for field, value in (
    ("source_event_id", ack_id),
    ("semester_id", 8),
    ("source_event_type", "lesson.deleted"),
    ("target", "ACADEMIC"),
    ("payload_hash", "b" * 64),
    ("result", marker),
):
    changed = copy.deepcopy(envelope)
    changed["payload"][field] = value
    assert scope["_ack_flags"](changed, ledger) is False
assert scope["_ack_row"]({"status": "sent", "payload": marker}, ledger)["identity_matches"] is False
assert scope["_receipt_flags"](None, ledger)["result"] == "MISSING"
changed = dict(receipt_row, result="ERROR", semester_id=8, payload_hash="b" * 64)
rejected = scope["_receipt_flags"](changed, ledger)
assert rejected["result"] == "ERROR" and rejected["identityMatches"] is False and rejected["hashMatches"] is False

bad_ledger = {key: marker for key in ledger}
bad_receipt = {"present": marker, "result": marker, "identityMatches": marker, "hashMatches": marker, "message": marker}
bad_ack = {"status": marker, "identity_matches": marker, "event_id": marker, "body": marker}
result = scope["_project"](bad_ledger, [{"status": marker}], bad_receipt, [bad_ack], [marker], marker)
assert marker not in json.dumps(result)
assert result["receipt"]["present"] == "UNKNOWN" and result["ackIdentityMatches"] is False
assert result["ackClaimPresent"] == "UNKNOWN" and result["stableLedger"] == "UNKNOWN"
empty = scope["_project"](None, [], scope["_receipt_flags"](None, {}), [], [], True)
assert empty["found"] is False and empty["ackPresent"] is False
assert empty["producerPresent"] is False and empty["receipt"]["result"] == "MISSING"
legacy = scope["_project"](None, [], scope["_receipt_flags"](None, {}), [], [], True, "SCHEDULE_LEGACY_EFFECT_UNPROVEN")
assert legacy["selection"] == "LEGACY_OUTBOX_UNAVAILABLE"
missing_scope = scope["_project"](dict(ledger, semester_id=None), [], receipt, [], [], True)
assert missing_scope["scopeMissing"] is True

academic = dict(ledger, event_type="homework.binding.archived", target="ACADEMIC")
academic_ack = copy.deepcopy(envelope)
academic_ack["source"] = "academic-service"
academic_ack["payload"].update(target="ACADEMIC", source_event_type="homework.binding.archived")
assert scope["_ack_flags"](academic_ack, academic) is True
script = scope["_mongo_script"](ledger)
assert "limit(1)" in script and "limit(3)" in script and "maxTimeMS(2000)" in script
assert '"source_event_id"' in script and r"\\s*:\\s*" in script and "$regex:pattern" in script
assert not any(value in script for value in (".insert", ".update", ".delete", ".remove", ".save"))
assert len(json.dumps(empty).encode()) <= 4096

# Actual AMQP 3.2.12 representation for a body >50 bytes has no event identity.
# Only queue/type hints may be produced, even in this positive fixture.
log_row = {
    "service": "attendance-service",
    "level": "WARN",
    "logger": "org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer",
    "msg": "Retries exhausted for message (Body:'[B@123abc(byte[400])' MessageProperties [headers={event_type=lesson.closed}, contentType=application/json, receivedExchange=rut-uit.events, receivedRoutingKey=, consumerQueue=attendance-service.events])",
    "stack": "org.springframework.amqp.rabbit.support.ListenerExecutionFailedException: "
    + marker
    + "\nCaused by: io.grpc.StatusRuntimeException: PERMISSION_DENIED: "
    + marker
    + "\n\tat ru.rutcampustrack.attendance.grpc.AcademicGrpcClient.getGroupMembers(AcademicGrpcClient.java:300)\nCaused by: "
    + marker
    + ": secret\n\tat private."
    + marker
    + "(Secret.java:1)",
    "traceId": identity,
    "private": marker,
}


def hint(row):
    return scope["_consumer_failure_hint"](json.dumps(row).encode())


known_hint = hint(log_row)
assert known_hint == {
    "observation": "UNMATCHED_QUEUE_EVENT_TYPE_HINT",
    "bodyIdentityUnavailable": True,
    "exactCandidateMatched": False,
    "hintRecords": 1,
    "sampleCapped": False,
    "exceptionKinds": ["GRPC_STATUS"],
    "grpcCodes": ["PERMISSION_DENIED"],
    "sourceFrames": ["ACADEMIC_ROSTER"],
}
assert all(
    value not in json.dumps(known_hint) for value in (marker, identity, "123abc", "400", "AcademicGrpcClient.java")
)
for old, new in (
    ("consumerQueue=attendance-service.events", "consumerQueue=foreign.events"),
    ("receivedExchange=rut-uit.events", "receivedExchange=foreign.events"),
    ("event_type=lesson.closed", "event_type=lesson.cancelled"),
    ("receivedRoutingKey=,", "receivedRoutingKey=foreign,"),
    (
        "consumerQueue=attendance-service.events",
        "consumerQueue=foreign.events, consumerQueue=attendance-service.events",
    ),
):
    changed = dict(log_row, msg=log_row["msg"].replace(old, new))
    assert hint(changed)["hintRecords"] == 0
for field, value in (("service", "schedule-service"), ("logger", "private." + marker), ("level", "ERROR")):
    assert hint(dict(log_row, **{field: value}))["hintRecords"] == 0
assert hint(dict(log_row, msg=log_row["msg"][:-4]))["hintRecords"] == 0
assert scope["_consumer_failure_hint"](json.dumps(log_row).encode()[:-4])["hintRecords"] == 0
duplicate = json.dumps(log_row)[:-1] + ',"logger":"private.' + marker + '"}'
assert scope["_consumer_failure_hint"](duplicate.encode())["hintRecords"] == 0
assert scope["_consumer_failure_hint"](b"x" * 262145)["observation"] == "UNAVAILABLE"
assert scope["_consumer_failure_hint"](b"\xff")["observation"] == "UNAVAILABLE"
assert scope["_consumer_failure_hint"](b"{}\n" * 201)["observation"] == "UNAVAILABLE"
unknown_stack = hint(dict(log_row, stack="Caused by: private." + marker + ": UNKNOWN_SECRET_STATUS: " + identity))
assert (
    unknown_stack["exceptionKinds"] == [] and unknown_stack["grpcCodes"] == [] and unknown_stack["sourceFrames"] == []
)
projected_hint = scope["_project"](ledger, [], receipt, [], [], True, consumer_logs=json.dumps(log_row).encode())
assert projected_hint["schema"] == "semester-effect.v3" and projected_hint["consumerFailureHint"] == known_hint
assert projected_hint["ledgerState"] == "PENDING" and projected_hint["ackPresent"] is False
assert len(json.dumps(projected_hint).encode()) <= 4096
assert marker not in json.dumps(projected_hint) and identity not in json.dumps(projected_hint)

# A unique trace is only a reported stage hint, never exact message/body identity.
trace = "00000000-0000-4000-8000-000000000003"
traced = dict(ledger, trace_id=trace)
assert scope["_unique_candidate_trace"](traced, [identity]) == trace
assert scope["_unique_candidate_trace"](traced, [identity, identity]) == trace
for events in ([], [ack_id], [identity, ack_id], [identity, identity, identity], [marker], [None]):
    assert scope["_unique_candidate_trace"](traced, events) is None
for field, value in (
    ("source_matches", False),
    ("version_matches", "true"),
    ("scope_matches", False),
    ("event_matches", False),
    ("type_matches", False),
    ("trace_id", marker),
    ("event_id", marker),
    ("event_type", "lesson.cancelled"),
    ("target", "ACADEMIC"),
):
    assert scope["_unique_candidate_trace"](dict(traced, **{field: value}), [identity]) is None

stage_row = {
    "service": "attendance-service",
    "level": "WARN",
    "logger": "ru.rutcampustrack.attendance.event.EventConsumer",
    "traceId": trace,
    "msg": "lesson.closed failure stage=PREPARE category=SCHEDULE_UNAVAILABLE",
}


def stage_hint(row, candidate=trace):
    return scope["_consumer_failure_hint"](json.dumps(row).encode(), candidate)


expected_stage_hint = {
    "observation": "UNIQUE_CANDIDATE_TRACE_STAGE_HINT",
    "bodyIdentityUnavailable": True,
    "exactCandidateMatched": False,
    "hintRecords": 1,
    "sampleCapped": False,
    "exceptionKinds": ["SCHEDULE_UNAVAILABLE"],
    "grpcCodes": [],
    "sourceFrames": ["PREPARE_CLOSE"],
}
assert stage_hint(stage_row) == expected_stage_hint
for stage, frame in (
    ("RECEIPT", "CLOSE_HANDLER"),
    ("PAYLOAD", "CLOSE_HANDLER"),
    ("PREPARE", "PREPARE_CLOSE"),
    ("TRANSACTION", "EFFECT_TRANSACTION"),
):
    value = stage_hint(dict(stage_row, msg=f"lesson.closed failure stage={stage} category=MONGO_TRANSACTION"))
    assert value["exceptionKinds"] == ["MONGO_TRANSACTION"] and value["sourceFrames"] == [frame]
    assert value["exactCandidateMatched"] is False and value["bodyIdentityUnavailable"] is True
assert stage_hint(dict(stage_row, msg="lesson.closed failure stage=TRANSACTION category=OTHER"))["exceptionKinds"] == []
for field, value in (
    ("service", "schedule-service"),
    ("level", "ERROR"),
    ("logger", "private." + marker),
    ("traceId", ack_id),
    ("traceId", None),
    ("stack", marker),
    ("msg", stage_row["msg"] + " " + marker),
    ("msg", "lesson.closed failure stage=" + marker + " category=SCHEDULE_UNAVAILABLE"),
    ("msg", "lesson.closed failure stage=PREPARE category=" + marker),
):
    assert stage_hint(dict(stage_row, **{field: value}))["hintRecords"] == 0
for candidate in (None, marker, ack_id):
    assert stage_hint(stage_row, candidate)["hintRecords"] == 0
duplicate_stage = json.dumps(stage_row)[:-1] + ',"traceId":"' + trace + '"}'
assert scope["_consumer_failure_hint"](duplicate_stage.encode(), trace)["hintRecords"] == 0
assert scope["_consumer_failure_hint"](json.dumps(stage_row).encode()[:-2], trace)["hintRecords"] == 0
# Unrelated retry stacks must not contaminate the uniquely trace-correlated stage/category.
mixed = (json.dumps(log_row) + "\n" + json.dumps(stage_row)).encode()
assert scope["_consumer_failure_hint"](mixed, trace) == expected_stage_hint
stage_projection = scope["_project"](traced, [], receipt, [], [], True, consumer_logs=mixed, candidate_trace=trace)
assert stage_projection["consumerFailureHint"] == expected_stage_hint
assert len(json.dumps(stage_projection).encode()) <= 4096
assert all(value not in json.dumps(stage_projection) for value in (marker, identity, ack_id, trace))
print(
    json.dumps(
        {
            "unknown_secret_rejected": True,
            "valid_private_identity_not_emitted": True,
            "known_positive_projection": True,
            "scope_version_hash_and_result_mismatch_rejected": True,
            "handled_error_and_missing_receipt_distinct": True,
            "academic_ack_mapping": True,
            "bounded_readonly_mongo_source": True,
            "unmatched_queue_type_positive_hint_only": True,
            "foreign_truncated_spoofed_and_duplicate_logs_rejected": True,
            "unique_trace_stage_hint_only_not_message_identity": True,
            "duplicate_source_copies_allowed_distinct_shared_trace_rejected": True,
            "preparation_and_transaction_reported_stages_distinct": True,
            "raw_log_identity_unknown_class_and_private_marker_not_emitted": True,
            "known_nonbarrier_not_applicable_before_private_seed_or_process_reads": True,
            "three_barrier_reasons_still_require_diagnostic_readers": True,
            "unknown_malformed_and_reader_failure_remain_unavailable": True,
            "original_primary_failure_retained": True,
            "qualification": "Actual projection/applicability functions and owned-file entrypoint; no subprocess, DB, Docker, network, queue or real event recovery",
        }
    )
)
