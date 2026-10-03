#!/usr/bin/env python3
"""Bounded application readback after a quiesced synthetic recovery checkpoint.

The operator owns stop/start, fresh labeled targets and recovery.py backup/restore.
This helper never restores, starts containers, changes DB state or handles secrets
in artifacts. capture seals one PENDING transfer from the immutable DB bundle;
verify observes real participants, then repeats the exact accepted HTTP command.
Only loopback HTTP(S) endpoints and explicitly labeled disposable DBs are accepted.
"""
import argparse
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid

spec = importlib.util.spec_from_file_location("recovery", Path(__file__).with_name("recovery.py"))
r = importlib.util.module_from_spec(spec)
spec.loader.exec_module(r)


def canonical(value):
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"))


def fingerprint(value):
    return hashlib.sha256(canonical(value).encode()).hexdigest()


def read_json(path):
    return json.loads(path.read_text(encoding="utf-8-sig"))


def table(inventory, store, name):
    matches = [t["rows"] for t in inventory[store]["tables"]
               if t["schema"] == "public" and t["name"] == name]
    r.require(len(matches) == 1, "Required application table missing: " + name)
    return matches[0]


def rows_for(rows, key, values):
    return sorted((row for row in rows if str(row.get(key)) in {str(v) for v in values}),
                  key=canonical)


def mongo_rows(inventory, name):
    collection = inventory["mongo"]["attendance_db"].get(name, {"documents": []})
    return sorted((json.loads(doc) for doc in collection["documents"]), key=canonical)


def numeric(value):
    if isinstance(value, dict):
        value = value.get("$numberLong", value.get("$numberInt"))
    return int(value)


def pointer(value, path):
    r.require(path.startswith("/"), "History pointer must select a nonempty JSON array")
    for key in path[1:].split("/"):
        key = key.replace("~1", "/").replace("~0", "~")
        value = value[int(key)] if isinstance(value, list) else value[key]
    r.require(isinstance(value, list) and bool(value), "History projection must be nonempty")
    return value


def java_time(value):
    if value is None:
        return ""
    match = re.fullmatch(r"([0-2][0-9]):([0-5][0-9])(?::([0-5][0-9])(?:\.([0-9]{1,9}))?)?", value)
    r.require(match is not None and int(match[1]) < 24, "Original request has invalid LocalTime")
    hour, minute, second, fraction = match.groups()
    nanos = int((fraction or "").ljust(9, "0"))
    base = hour + ":" + minute
    if int(second or 0) or nanos:
        base += ":" + (second or "00")
    if nanos:
        digits = 3 if nanos % 1000000 == 0 else (6 if nanos % 1000 == 0 else 9)
        base += "." + f"{nanos:09d}"[:digits]
    return base


def capture(args):
    r.require(args.quiesced, "Capture requires acknowledged stopped writers")
    r.require(not args.output.exists(), "Capture output already exists")
    if args.dry_run:
        print("PLAN capture: verify immutable database-only bundle; exact pending operation/batches/outbox; protected past marks and nonempty API history")
        return
    inventory = r.verify_bundle(args.bundle, "database-only")
    operation_id = str(uuid.UUID(args.operation_id))
    operations = rows_for(table(inventory, "schedule", "lesson_transfer_operations"), "operation_id", [operation_id])
    r.require(len(operations) == 1 and operations[0]["state"] == "PENDING", "Checkpoint must contain exactly one requested PENDING operation")
    op = operations[0]
    request = read_json(args.request_file)
    r.require(str(uuid.UUID(request["requestKey"])) == op["request_key"]
              and request["expectedRevision"] == str(op["expected_occurrence_revision"])
              and request["targetDate"] == op["target_snapshot"]["date"]
              and request["targetLessonNumber"] == op["target_snapshot"]["lesson_number"],
              "Original HTTP request must match checkpoint transfer identity")
    # Java's request hash normalizes LocalTime before joining the seven values.
    joined = "\n".join([str(op["source_lesson_id"]), request["targetDate"], str(request["targetLessonNumber"]),
                        java_time(request.get("targetStartTime")), java_time(request.get("targetEndTime")),
                        request.get("targetRoom") or "", request["expectedRevision"]])
    r.require("\\x" + hashlib.sha256(joined.encode()).hexdigest() == op["request_hash"], "Original request hash differs from durable request")
    batches = rows_for(table(inventory, "schedule", "lesson_transfer_binding_batches"), "operation_id", [operation_id])
    r.require(len(batches) == op["batch_count"] and {b["batch_index"] for b in batches} == set(range(op["batch_count"])), "Incomplete immutable binding batches")
    bindings = [binding for batch in batches for binding in batch["payload"]["bindings"]]
    r.require(any(b["state"] == "ACTIVE" and b["homework_id"] is not None for b in bindings), "Drill requires an active real homework binding")
    outbox = [row for row in table(inventory, "schedule", "schedule_outbox")
              if row["payload"].get("payload", {}).get("operation_id") == operation_id]
    r.require(outbox and all(row["status"] == "pending" for row in outbox), "Checkpoint transfer outbox must remain pending")
    r.require(not rows_for(table(inventory, "schedule", "lesson_transfer_participant_receipts"), "operation_id", [operation_id]), "Source participants must not have completed before checkpoint")
    r.require(not rows_for(table(inventory, "academic", "lesson_transfer_receipts"), "operation_id", [operation_id])
              and not rows_for(mongo_rows(inventory, "lesson_transfer_receipts"), "_id", [operation_id]), "Source participant local receipts must also be absent")
    protected = sorted(set(args.history_lesson_id))
    r.require(protected and not {op["source_lesson_id"], op["target_lesson_id"]}.intersection(protected), "Select independent historical lessons")
    lessons = rows_for(table(inventory, "schedule", "lessons"), "id", [op["source_lesson_id"], op["target_lesson_id"], *protected])
    r.require(len(lessons) == len(protected) + 2 and all(l["status"] == "closed" for l in lessons if l["id"] in protected), "Protected history must identify existing closed lessons")
    marks = [doc for doc in mongo_rows(inventory, "attendances") if numeric(doc["lesson_id"]) in protected]
    r.require(marks, "Drill requires existing historical attendance marks")
    future_marks = [doc for doc in mongo_rows(inventory, "attendances") if numeric(doc["lesson_id"]) in (op["source_lesson_id"], op["target_lesson_id"])]
    r.require(not future_marks, "This minimal future transfer drill requires no future attendance marks")
    occurrence_ids = [l["occurrence_id"] for l in lessons]
    snapshot = {"operation": op, "batches": batches, "outbox": outbox, "bindings": bindings,
                "lessons": lessons, "occurrences": rows_for(table(inventory, "schedule", "lesson_occurrences"), "id", occurrence_ids),
                "lifecycle": rows_for(table(inventory, "schedule", "lesson_lifecycle_entries"), "occurrence_id", occurrence_ids),
                "schedule_bindings": rows_for(table(inventory, "schedule", "lesson_homework_bindings"), "occurrence_id", [op["occurrence_id"]]),
                "homeworks": rows_for(table(inventory, "academic", "homeworks"), "id", [b["homework_id"] for b in bindings if b["homework_id"] is not None]),
                "protected_marks": marks, "history_lesson_ids": protected}
    history = pointer(read_json(args.history_before), args.history_pointer)
    r.write_json(args.output, {"format": 1, "bundle_manifest_sha256": r.digest(args.bundle / "manifest.json"),
                              "request": request, "snapshot": snapshot, "history_pointer": args.history_pointer,
                              "history": history, "snapshot_sha256": fingerprint(snapshot)})
    print("PASS capture: pending operation and outbox sealed against immutable bundle; real homework and past attendance/history present")


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def endpoint(url):
    parsed = urllib.parse.urlsplit(url)
    r.require(parsed.scheme in ("http", "https") and parsed.hostname in ("localhost", "127.0.0.1", "::1")
              and not parsed.username and not parsed.password and not parsed.fragment, "Only explicit loopback application endpoints are allowed")
    return url


def http(url, token_file, payload=None):
    token = token_file.read_text(encoding="utf-8-sig").strip()
    r.require(token and "\n" not in token and "\r" not in token, "Invalid private token file")
    request = urllib.request.Request(endpoint(url), data=None if payload is None else canonical(payload).encode(),
                                     headers={"Authorization": "Bearer " + token, "Content-Type": "application/json"})
    try:
        with urllib.request.build_opener(NoRedirect).open(request, timeout=10) as response:
            return json.load(response)
    except urllib.error.HTTPError as error:
        raise r.RecoveryError("Application HTTP request failed with status " + str(error.code)) from None
    except urllib.error.URLError:
        raise r.RecoveryError("Application endpoint unavailable") from None


def database_snapshot(args, op, secret):
    operation_id = str(uuid.UUID(op["operation_id"]))
    ids = ",".join(str(v) for v in [op["source_lesson_id"], op["target_lesson_id"], *args.history_lesson_ids])
    occurrence_id = int(op["occurrence_id"])
    def pg(store, name, where):
        return json.loads(r.pg_query(getattr(args, store + "_container"), secret["POSTGRES_" + store.upper() + "_PASSWORD"],
                                    store + "_db", "SELECT coalesce(jsonb_agg(to_jsonb(t) ORDER BY to_jsonb(t)::text),'[]'::jsonb) FROM " + name + " t WHERE " + where))
    same_operation = "operation_id='" + operation_id + "'::uuid"
    result = {"operation": pg("schedule", "lesson_transfer_operations", same_operation),
              "batches": pg("schedule", "lesson_transfer_binding_batches", same_operation),
              "receipts": pg("schedule", "lesson_transfer_participant_receipts", same_operation),
              "outbox": pg("schedule", "schedule_outbox", "payload->'payload'->>'operation_id'='" + operation_id + "'"),
              "lessons": pg("schedule", "lessons", "id IN (" + ids + ")"),
              "occurrences": pg("schedule", "lesson_occurrences", "id IN (SELECT occurrence_id FROM lessons WHERE id IN (" + ids + "))"),
              "lifecycle": pg("schedule", "lesson_lifecycle_entries", "occurrence_id IN (SELECT occurrence_id FROM lessons WHERE id IN (" + ids + "))"),
              "schedule_bindings": pg("schedule", "lesson_homework_bindings", "occurrence_id=" + str(occurrence_id)),
              "academic_receipts": pg("academic", "lesson_transfer_receipts", same_operation),
              "academic_history": pg("academic", "homework_binding_transfer_history", same_operation),
              "homeworks": pg("academic", "homeworks", "id IN (" + ",".join(str(int(b["homework_id"])) for b in args.bindings if b["homework_id"] is not None) + ")")}
    result["mongo"] = r.mongo_query(args.mongo_container, secret["MONGO_ROOT_PASSWORD"],
        "const d=db.getSiblingDB('attendance_db'); print(JSON.stringify({marks:d.attendances.find({lesson_id:{$in:[" + ids + "]}}).toArray().map(v=>EJSON.stringify(v,{relaxed:false})).sort(),"
        "receipts:d.lesson_transfer_receipts.find({_id:" + json.dumps(operation_id) + "}).toArray().map(v=>EJSON.stringify(v,{relaxed:false})).sort()}));")
    return result


def validate_terminal(actual, baseline):
    snapshot, op = baseline["snapshot"], baseline["snapshot"]["operation"]
    r.require(len(actual["operation"]) == 1, "Operation count changed")
    observed = actual["operation"][0]
    r.require(observed["state"] == "COMPLETED" and observed["error_code"] is None, "Restored operation did not complete")
    immutable = lambda row: {k: v for k, v in row.items() if k not in ("state", "error_code", "updated_at")}
    r.require(immutable(observed) == immutable(op), "Durable operation identity changed")
    for key in ("batches", "lessons", "occurrences", "lifecycle", "schedule_bindings"):
        r.require(sorted(actual[key], key=canonical) == sorted(snapshot[key], key=canonical), "Restored canonical " + key + " changed")
    expected_receipts = {("ATTENDANCE", -1), *(("ACADEMIC", i) for i in range(op["batch_count"]))}
    receipts = actual["receipts"]
    r.require(len(receipts) == len(expected_receipts) and {(v["participant"], v["batch_index"]) for v in receipts} == expected_receipts
              and all(v["result"] == "APPLIED" and v["payload_hash"] == op["operation_hash"] for v in receipts), "Participant receipts missing, duplicated or wrong identity")
    academic = actual["academic_receipts"]
    r.require(len(academic) == op["batch_count"] and {v["batch_index"] for v in academic} == set(range(op["batch_count"]))
              and all(v["result"] == "APPLIED" and v["operation_hash"] == op["operation_hash"] for v in academic), "Academic durable receipt mismatch")
    history = actual["academic_history"]
    binding_by_id = {v["binding_id"]: v for v in snapshot["bindings"]}
    r.require(len(history) == len(snapshot["bindings"]) and {v["binding_id"] for v in history} == {v["binding_id"] for v in snapshot["bindings"]}
              and all(v["operation_hash"] == op["operation_hash"] and v["target_lesson_id"] == op["target_lesson_id"]
                      and v["result_state"] == ("MOVED" if binding_by_id[v["binding_id"]]["homework_id"] is not None else "PENDING_PUBLICATION") for v in history), "Homework transfer history missing or duplicated")
    homework_ids = {v["homework_id"] for v in snapshot["bindings"] if v["homework_id"] is not None}
    r.require(len(actual["homeworks"]) == len(homework_ids) and {v["id"] for v in actual["homeworks"]} == homework_ids
              and all(v["lesson_date"] == op["target_snapshot"]["date"]
                      and v["lesson_number"] == op["target_snapshot"]["lesson_number"] for v in actual["homeworks"]), "Restored homework did not follow the accepted target slot")
    prior_homeworks = {v["id"]: v for v in snapshot["homeworks"]}
    stable_homework = lambda row: {k: v for k, v in row.items() if k not in ("lesson_date", "lesson_number", "revision", "updated_at", "due_reminder_sent_at")}
    for homework in actual["homeworks"]:
        prior = prior_homeworks[homework["id"]]
        slot_changed = (prior["lesson_date"], prior["lesson_number"]) != (homework["lesson_date"], homework["lesson_number"])
        r.require(stable_homework(homework) == stable_homework(prior)
                  and homework["revision"] == prior["revision"] + int(slot_changed), "Homework content/identity/revision changed beyond one transfer")
    outbox_by_id = {v["id"]: v for v in actual["outbox"]}
    r.require(all(v["id"] in outbox_by_id and outbox_by_id[v["id"]]["payload"] == v["payload"]
                  and outbox_by_id[v["id"]]["status"] == "sent" for v in snapshot["outbox"]), "Original pending outbox was not sent with its original envelope")
    marks = sorted((json.loads(v) for v in actual["mongo"]["marks"]), key=canonical)
    r.require(marks == snapshot["protected_marks"], "Past attendance changed or incorrect future marks appeared")
    receipts = [json.loads(v) for v in actual["mongo"]["receipts"]]
    r.require(len(receipts) == 1 and receipts[0]["result"] == "APPLIED"
              and receipts[0]["transfer_payload_hash"] == op["operation_hash"][2:], "Attendance durable receipt mismatch")


def schedule_readback(args, op):
    dates = sorted((op["source_snapshot"]["date"], op["target_snapshot"]["date"]))
    url = args.schedule_url.rstrip("/") + "/schedule/groups/" + str(op["source_snapshot"]["group_id"]) + "/lessons?" + urllib.parse.urlencode(
        {"dateFrom": dates[0], "dateTo": dates[1], "size": 200})
    response = http(url, args.actor_token_file)
    lessons = [lesson for value in response.get("_embedded", {}).values() if isinstance(value, list) for lesson in value]
    selected = {int(v["id"]): v for v in lessons if int(v["id"]) in (op["source_lesson_id"], op["target_lesson_id"])}
    r.require(len(selected) == 2, "Application schedule must expose source history and current target")
    source, target = selected[op["source_lesson_id"]], selected[op["target_lesson_id"]]
    r.require(source["current"] is False and source["status"].upper() == "TRANSFERRED"
              and target["current"] is True and target["status"].upper() == "PLANNED"
              and int(target["generation"]) == op["target_generation"]
              and int(target["occurrenceRevision"]) == op["result_occurrence_revision"]
              and target["transferOperationId"] == op["operation_id"] and target["transferState"] == "COMPLETED",
              "Application schedule has incorrect canonical flags/generation/transfer")
    return selected


def verify(args):
    r.require(not args.output.exists(), "Result output already exists")
    baseline = read_json(args.baseline)
    r.require(baseline["format"] == 1 and fingerprint(baseline["snapshot"]) == baseline["snapshot_sha256"], "Capture integrity mismatch")
    r.verify_bundle(args.bundle, "database-only")
    r.require(r.digest(args.bundle / "manifest.json") == baseline["bundle_manifest_sha256"], "Checkpoint bundle identity changed")
    r.require(re.fullmatch(r"rct-recovery-[a-z0-9-]+", args.target_project), "Target must use explicit rct-recovery project")
    endpoint(args.schedule_url)
    endpoint(args.history_url)
    if args.dry_run:
        print("PLAN verify: disposable DB label guards; bounded real terminal API readback; immutable IDs/receipts/outbox/history; one exact HTTP replay; stable state")
        return
    r.check_targets(args, restore=True)
    secret = r.credentials(args.env_file)
    op = baseline["snapshot"]["operation"]
    args.history_lesson_ids = baseline["snapshot"]["history_lesson_ids"]
    args.bindings = baseline["snapshot"]["bindings"]
    status_url = args.schedule_url.rstrip("/") + "/schedule/lesson-transfers/" + op["operation_id"]
    deadline = time.monotonic() + args.timeout_seconds
    while True:
        status = http(status_url, args.actor_token_file)
        r.require(status["operationId"] == op["operation_id"] and status["state"] != "ERROR", "Restored API operation identity/error mismatch")
        if status["state"] == "COMPLETED":
            break
        r.require(time.monotonic() < deadline, "Timed out waiting for restored real participants")
        time.sleep(2)
    actual = database_snapshot(args, op, secret)
    validate_terminal(actual, baseline)
    schedule = schedule_readback(args, op)
    history = pointer(http(args.history_url, args.history_token_file), baseline["history_pointer"])
    r.require(history == baseline["history"], "User API historical projection changed after restore")
    replay = http(args.schedule_url.rstrip("/") + "/schedule/lessons/" + str(op["source_lesson_id"]) + "/transfer",
                  args.actor_token_file, baseline["request"])
    r.require(replay == status, "Exact accepted HTTP replay changed the operation result")
    after = database_snapshot(args, op, secret)
    validate_terminal(after, baseline)
    # Publisher bookkeeping is allowed to finish independently; business history is not.
    stable = lambda value: {k: v for k, v in value.items() if k != "outbox"}
    r.require(stable(after) == stable(actual), "Exact HTTP replay changed business state/history")
    r.require(schedule_readback(args, op) == schedule, "Application canonical schedule changed after exact replay")
    r.require(pointer(http(args.history_url, args.history_token_file), baseline["history_pointer"]) == history, "User history changed after exact replay")
    r.write_json(args.output, {"result": "PASS", "operation_id": op["operation_id"], "actor_id": op["actor_id"],
                              "target_project": args.target_project, "bundle_manifest_sha256": baseline["bundle_manifest_sha256"],
                              "capture_sha256": r.digest(args.baseline), "terminal_state_sha256": fingerprint(stable(after)),
                              "history_sha256": fingerprint(history), "checks": ["real application terminal readback", "original pending outbox sent",
                              "immutable operation/batches/canonical generations", "both real participant receipts", "no duplicate homework history/attendance",
                              "preserved user history", "exact accepted HTTP replay"]})
    print("PASS application recovery: real terminal participants, original outbox, unchanged canonical/past history and exact replay")


def main():
    p = argparse.ArgumentParser(description=__doc__)
    sub = p.add_subparsers(dest="command", required=True)
    capture_parser = sub.add_parser("capture")
    capture_parser.add_argument("--operation-id", required=True)
    capture_parser.add_argument("--request-file", type=Path, required=True)
    capture_parser.add_argument("--history-before", type=Path, required=True)
    capture_parser.add_argument("--history-pointer", required=True)
    capture_parser.add_argument("--history-lesson-id", type=int, action="append", required=True)
    capture_parser.add_argument("--quiesced", action="store_true")
    verify_parser = sub.add_parser("verify")
    verify_parser.add_argument("--baseline", type=Path, required=True)
    verify_parser.add_argument("--env-file", type=Path, required=True)
    for name in ("academic-container", "schedule-container", "mongo-container", "target-project", "schedule-url", "history-url"):
        verify_parser.add_argument("--" + name, required=True)
    for name in ("actor-token-file", "history-token-file"):
        verify_parser.add_argument("--" + name, type=Path, required=True)
    verify_parser.add_argument("--timeout-seconds", type=int, choices=range(1, 301), default=130)
    for parser in (capture_parser, verify_parser):
        parser.add_argument("--bundle", type=Path, required=True)
        parser.add_argument("--output", type=Path, required=True)
        parser.add_argument("--dry-run", action="store_true")
    args = p.parse_args()
    os.umask(0o077)
    try:
        (capture if args.command == "capture" else verify)(args)
    except (r.RecoveryError, OSError, ValueError, KeyError, IndexError, TypeError) as error:
        # HTTP responses, DB diagnostics and private inputs are never echoed.
        print("FAIL application recovery: " + (str(error) if isinstance(error, r.RecoveryError) else type(error).__name__), file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
