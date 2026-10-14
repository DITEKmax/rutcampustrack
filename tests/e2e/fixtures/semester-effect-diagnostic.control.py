"""RAM-only controls for the real projection; main and DB/process code never execute."""

import ast
import copy
import importlib.util
import json
from pathlib import Path

source_path = Path(__file__).with_name("semester-effect-diagnostic.py")
source = source_path.read_text()
tree = ast.parse(source)
names = {"_closed", "_canonical_uuid", "_receipt_flags", "_ack_flags", "_ack_row", "_mongo_script", "_project"}
selected = [node for node in tree.body if isinstance(node, ast.FunctionDef) and node.name in names]
assert {node.name for node in selected} == names
assert not any(isinstance(node, (ast.Import, ast.ImportFrom)) for function in selected for node in ast.walk(function))
module_spec = importlib.util.spec_from_file_location("semester_effect_control_target", source_path)
module = importlib.util.module_from_spec(module_spec)
module_spec.loader.exec_module(module)
scope = vars(module)

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
            "qualification": "Actual pure projection functions only; no subprocess, DB, Docker, network, queue or real event recovery",
        }
    )
)
