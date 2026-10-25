#!/usr/bin/env python3
"""Populate owned synthetic role data through the real isolated PR3 APIs.

Never stores generated initial passwords or session credentials. Existing
student/headman3, teacher2 and admin1 remain the public baseline accounts.
"""
import argparse
import datetime as dt
import json
from pathlib import Path
import time
import urllib.error
import urllib.request
import uuid
from zoneinfo import ZoneInfo


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base", default="http://127.0.0.1:18080")
    parser.add_argument("--state", type=Path, required=True)
    args = parser.parse_args()
    state = json.loads(args.state.read_text()) if args.state.exists() else {"users": {}}
    sessions = []

    def save():
        args.state.write_text(json.dumps(state, ensure_ascii=False, indent=2) + "\n")

    def call(method, route, body=None, token=None, headers=None, allowed=(200, 201, 204)):
        request_headers = {"Content-Type": "application/json", "Origin": "http://127.0.0.1:14210", **(headers or {})}
        if token:
            request_headers["Authorization"] = "Bearer " + token
        request = urllib.request.Request(args.base + route, method=method, headers=request_headers, data=json.dumps(body).encode() if body is not None else None)
        try:
            with urllib.request.urlopen(request, timeout=30) as response:
                status, raw = response.status, response.read()
        except urllib.error.HTTPError as error:
            status, raw = error.code, error.read()
        data = json.loads(raw) if raw else None
        if status not in allowed:
            raise RuntimeError(f"{method} {route}: HTTP{status}; problem={json.dumps(data, ensure_ascii=False)}")
        print(f"HTTP{status} {method} {route}", flush=True)
        return data

    def admit(login, role):
        token = call("POST", "/api/auth/login", {"login": login, "password": "password"})["accessToken"]
        snapshot = call("GET", "/api/auth/session", token=token)
        admission = call("PUT", "/api/auth/session/active-role", {"role": role, "expectedSessionVersion": snapshot["sessionVersion"]}, token)
        token = admission["accessToken"]
        sessions.append(token)
        assert admission["session"]["activeRole"] == role
        return token

    try:
        admin = admit("admin", "ADMIN")
        headman = admit("student", "HEADMAN")
        semester = call("GET", "/api/academic/semesters/2", token=admin)
        assert semester["active"], "The accepted fixture semester is no longer active"
        state["semesterId"], state["groupId"], state["subjectId"], state["teacherId"] = 2, 1, 1, 2
        subject = call("GET", "/api/academic/subjects/1", token=headman)
        assignments = [row for row in subject.get("assignments", []) if row["teacherId"] == 2 and row["semesterId"] == 2 and row["lessonType"] == "LECTURE"]
        if not assignments:
            call("POST", "/api/academic/subjects/1/teachers/2", {"semesterId": 2, "lessonType": "LECTURE", "validFrom": semester["dateFrom"], "validUntilExclusive": None}, headman)
            for _ in range(15):
                subject = call("GET", "/api/academic/subjects/1", token=headman)
                assignments = [row for row in subject.get("assignments", []) if row["teacherId"] == 2 and row["semesterId"] == 2 and row["lessonType"] == "LECTURE"]
                if assignments:
                    break
                time.sleep(2)
        assert len(assignments) == 1, "Teacher assignment is not published"
        state["assignmentId"] = assignments[0]["id"]
        save()
        if "lesson" not in state:
            state.setdefault("lessonRequestKey", str(uuid.uuid4()))
            save()
            today = str(dt.datetime.now(ZoneInfo("Europe/Moscow")).date())
            state["lesson"] = call("POST", "/api/schedule/one-off-lessons", {"groupId": 1, "subjectId": 1, "assignmentId": state["assignmentId"], "date": today, "lessonNumber": 1, "classroom": "Синтетическая аудитория PR3"}, headman, {"Idempotency-Key": state["lessonRequestKey"]})
            save()
        if "adminGroup" not in state:
            state["adminGroup"] = call("POST", "/api/academic/groups/registry", {"alphabeticCode": "ПРТ", "numericCode": "119", "trainingDurationYears": 4}, admin)
            save()
        for key, last_name in [("active", "Практиков"), ("history", "Историев"), ("archiveCandidate", "Архивов")]:
            if key not in state["users"]:
                state.setdefault("syntheticTelegramIds", {}).setdefault(key, 8_000_000_000 + int.from_bytes(uuid.uuid4().bytes[:4]))
                save()
                created = call("POST", "/api/academic/users", {"lastName": last_name, "firstName": "Синтетика", "middleName": "Проверка", "role": "STUDENT", "groupId": 1, "employeeNumber": None, "telegramId": state["syntheticTelegramIds"][key], "adminAccess": None}, admin)
                state["users"][key] = {field: created[field] for field in ["id", "login"]}
                # initialPassword is deliberately discarded, never logged or saved.
                save()
        if not state.get("transferDone"):
            student = state["users"]["history"]
            current = call("GET", f"/api/academic/users/{student['id']}", token=admin)
            if current.get("groupId") != state["adminGroup"]["id"]:
                call("POST", f"/api/academic/users/{student['id']}/transfer", {"newGroupId": state["adminGroup"]["id"], "reason": "Синтетическая история перевода PR3; runtime-owned fixture"}, admin)
            state["transferDone"] = True
            save()
        teacher = admit("teacher", "TEACHER")
        catalog = call("GET", "/api/v1/teacher/catalog?semesterId=2", token=teacher)
        state["teacherCatalog"] = catalog
        save()
        assert any(row["id"] == "1" for row in catalog["groups"]), "Seed group absent from teacher catalog"
        assert any(row["groupId"] == "1" and row["subjectId"] == "1" for row in catalog["taughtSubjectOptions"]), "Seed subject absent from actual taught subjects"
        print("Synthetic role fixtures ready; generated credentials discarded.", flush=True)
    finally:
        for token in sessions:
            call("POST", "/api/auth/logout", {}, token)


if __name__ == "__main__":
    main()
