#!/usr/bin/env python3
"""Negative checks for the recovery evidence validator; never application evidence."""
import copy
import importlib.util
import json
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location("app_recovery", Path(__file__).with_name("test-application-recovery.py"))
app = importlib.util.module_from_spec(spec)
spec.loader.exec_module(app)


def fixture():
    op = {"operation_id": "00000000-0000-0000-0000-000000000001", "state": "PENDING", "error_code": None,
          "operation_hash": "\\x" + "aa" * 32, "batch_count": 1, "source_lesson_id": 11, "target_lesson_id": 12,
          "target_snapshot": {"date": "2026-10-10", "lesson_number": 2}}
    mark = {"_id": "past-user", "lesson_id": {"$numberLong": "1"}, "status": "PRESENT"}
    baseline = {"snapshot": {"operation": op, "batches": [{"batch_index": 0}], "lessons": [{"id": 1}, {"id": 11}, {"id": 12}],
                "occurrences": [{"id": 1, "current_lesson_id": 12}], "lifecycle": [{"action": "TRANSFERRED"}],
                "schedule_bindings": [{"binding_id": 5, "current_lesson_id": 12}],
                "homeworks": [{"id": 9, "lesson_date": "2026-10-09", "lesson_number": 1, "revision": 1, "title": "original"}],
                "bindings": [{"binding_id": 5, "homework_id": 9}], "protected_marks": [mark],
                "outbox": [{"id": 1, "payload": {"event_id": "unchanged-envelope"}, "status": "pending"}]}}
    actual = {key: copy.deepcopy(baseline["snapshot"][key]) for key in ("batches", "lessons", "occurrences", "lifecycle", "schedule_bindings", "outbox")}
    actual["operation"] = [{**op, "state": "COMPLETED"}]
    actual["outbox"][0]["status"] = "sent"
    actual["receipts"] = [{"participant": p, "batch_index": i, "result": "APPLIED", "payload_hash": op["operation_hash"]}
                           for p, i in (("ATTENDANCE", -1), ("ACADEMIC", 0))]
    actual["academic_receipts"] = [{"batch_index": 0, "result": "APPLIED", "operation_hash": op["operation_hash"]}]
    actual["academic_history"] = [{"binding_id": 5, "operation_hash": op["operation_hash"], "target_lesson_id": 12, "result_state": "MOVED"}]
    actual["homeworks"] = [{"id": 9, "lesson_date": "2026-10-10", "lesson_number": 2, "revision": 2, "title": "original"}]
    actual["mongo"] = {"marks": [json.dumps(mark)], "receipts": [json.dumps({"result": "APPLIED", "transfer_payload_hash": "aa" * 32})]}
    return baseline, actual


class EvidenceGuards(unittest.TestCase):
    def test_terminal_evidence_requires_each_durable_boundary(self):
        baseline, original = fixture()
        app.validate_terminal(original, baseline)
        mutations = [
            lambda a: a["operation"][0].update(state="PENDING"),
            lambda a: a["operation"][0].update(target_lesson_id=13),
            lambda a: a["occurrences"][0].update(current_lesson_id=11),
            lambda a: a["receipts"].pop(),
            lambda a: a["receipts"].append(copy.deepcopy(a["receipts"][0])),
            lambda a: a["academic_receipts"][0].update(operation_hash="\\x" + "bb" * 32),
            lambda a: a["academic_history"].append(copy.deepcopy(a["academic_history"][0])),
            lambda a: a["homeworks"][0].update(lesson_date="2026-10-11"),
            lambda a: a["homeworks"][0].update(title="lost original content"),
            lambda a: a["homeworks"][0].update(revision=3),
            lambda a: a["outbox"][0].update(status="pending"),
            lambda a: a["outbox"][0].update(payload={"event_id": "replacement"}),
            lambda a: a["mongo"]["marks"].append(a["mongo"]["marks"][0]),
            lambda a: a["mongo"].update(receipts=[]),
        ]
        for index, mutate in enumerate(mutations):
            with self.subTest(boundary=index):
                actual = copy.deepcopy(original)
                mutate(actual)
                with self.assertRaises(app.r.RecoveryError):
                    app.validate_terminal(actual, baseline)

    def test_external_or_credential_urls_refused(self):
        for url in ("http://example.com/api", "http://127.0.0.1.example.com/api", "file:///private", "http://user:pass@localhost/api", "http://localhost/api#x"):
            with self.subTest(url=url), self.assertRaises(app.r.RecoveryError):
                app.endpoint(url)
        self.assertEqual(app.endpoint("http://127.0.0.1:9092"), "http://127.0.0.1:9092")

    def test_history_cannot_be_empty(self):
        with self.assertRaises(app.r.RecoveryError):
            app.pointer({"history": []}, "/history")

    def test_request_hash_time_uses_java_precision(self):
        for source, expected in (("10:00:00", "10:00"), ("10:00:00.1", "10:00:00.100"),
                                 ("10:00:00.000001", "10:00:00.000001"), ("10:00:00.000000001", "10:00:00.000000001"), (None, "")):
            self.assertEqual(app.java_time(source), expected)


if __name__ == "__main__":
    unittest.main()
