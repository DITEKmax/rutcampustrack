#!/usr/bin/env python3
"""Seed and exercise real PR3 homework APIs in the disposable synthetic stand.

Uses only public fixture accounts (student/admin, password). Tokens stay in memory;
the state file contains only synthetic domain IDs and dates for restartability.
"""
import argparse
import datetime as dt
import json
import time
import urllib.error
import urllib.request
import uuid
from pathlib import Path
from zoneinfo import ZoneInfo


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base", default="http://127.0.0.1:18080")
    parser.add_argument("--state", type=Path, required=True)
    args = parser.parse_args()
    state = json.loads(args.state.read_text()) if args.state.exists() else {}

    def save():
        args.state.write_text(json.dumps(state, ensure_ascii=False, indent=2) + "\n")

    def call(method, path, body=None, token=None, allowed=(200, 201, 204)):
        headers = {"Content-Type": "application/json", "Origin": "http://127.0.0.1:14210"}
        if token:
            headers["Authorization"] = "Bearer " + token
        request = urllib.request.Request(args.base + path, data=json.dumps(body).encode() if body is not None else None, headers=headers, method=method)
        try:
            with urllib.request.urlopen(request, timeout=30) as response:
                status, raw = response.status, response.read()
        except urllib.error.HTTPError as error:
            status, raw = error.code, error.read()
        data = json.loads(raw) if raw else None
        if status not in allowed:
            # Problem responses contain domain diagnostics, never request auth headers.
            raise RuntimeError(f"{method} {path}: HTTP {status}: {json.dumps(data, ensure_ascii=False)}")
        return data

    sessions = []

    def admit(login, role):
        login_result = call("POST", "/api/auth/login", {"login": login, "password": "password"})
        token = login_result["accessToken"]
        session = call("GET", "/api/auth/session", token=token)
        admission = call("PUT", "/api/auth/session/active-role", {"role": role, "expectedSessionVersion": session["sessionVersion"]}, token)
        token = admission["accessToken"]
        sessions.append(token)
        assert admission["session"]["activeRole"] == role
        print(f"Authoritative {role} admission PASS (tokens withheld)", flush=True)
        return token

    try:
        assert call("GET", "/actuator/health")["status"] == "UP"
        admin = admit("admin", "ADMIN")
        today = dt.datetime.now(ZoneInfo("Europe/Moscow")).date()
        if "semester" not in state:
            listing = call("GET", "/api/academic/semesters?size=100", token=admin)
            rows = listing.get("content", listing.get("_embedded", {}).get("semesterResponseList", []))
            semester = next((row for row in rows if row["dateFrom"] <= str(today) <= row["dateTo"] and row.get("active")), None)
            if semester is None:
                semester = call("POST", "/api/academic/semesters", {"name": "PR3 synthetic autumn 2026", "dateFrom": str(today - dt.timedelta(days=1)), "dateTo": str(today + dt.timedelta(days=60)), "semesterType": "AUTUMN", "academicYear": today.year}, admin)
                semester = call("PATCH", f"/api/academic/semesters/{semester['id']}/activate", token=admin)
            state["semester"] = semester
            save()
        semester = state["semester"]
        semester = call("GET", f"/api/academic/semesters/{semester['id']}", token=admin)
        assert semester["active"] and semester["dateFrom"] <= str(today) <= semester["dateTo"], "Fixture semester is no longer current"
        state["semester"] = semester
        save()
        headman = admit("student", "HEADMAN")
        if "subject" not in state:
            subject = call("POST", "/api/academic/subjects", {"name": "Математика · PR3 synthetic", "type": "LECTURE", "lessonTypes": ["LECTURE"]}, headman)
            state["subject"] = subject
            save()
        if "homeworkRequest" not in state:
            state["homeworkRequest"] = {"title": "Решить задачи 1–5 · PR3", "description": "Синтетическое домашнее задание для проверки чтения и личной отметки выполнения.", "link": None, "subjectId": state["subject"]["id"], "groupId": 1, "semesterId": semester["id"], "lessonDate": str(today), "lessonNumber": None, "bindingMode": "DATE", "lessonType": "LECTURE", "requestKey": str(uuid.uuid4())}
            save()
        if "homework" not in state:
            for attempt in range(15):
                homework = call("POST", "/api/academic/homeworks", state["homeworkRequest"], headman, allowed=(200, 201, 202))
                if homework.get("id") is not None and homework.get("state") != "PENDING":
                    state["homework"] = homework
                    save()
                    break
                time.sleep(2)
            else:
                raise RuntimeError("Publication still pending after bounded retries")
        student = admit("student", "STUDENT")
        context = call("GET", "/api/v1/student/session", token=student)
        assert context["user"]["id"] == "3"
        path = f"/api/v1/student/homework?from={semester['dateFrom']}&to={semester['dateTo']}"
        homework_id = str(state["homework"]["id"])

        def item():
            feed = call("GET", path, token=student)
            found = [row for row in feed["items"] if row["id"] == homework_id]
            assert len(found) == 1, "Seeded homework missing from real student feed"
            return found[0]

        item()
        print(f"Populated homework read PASS; homeworkId={homework_id}", flush=True)
        completion_path = f"/api/v1/student/homework/{homework_id}/completion"
        completed = call("PUT", completion_path, {"completed": True}, student)
        assert completed["completed"] and completed["completedAt"]
        assert item()["completed"] is True
        print("Completion write and authoritative readback PASS", flush=True)
        undone = call("PUT", completion_path, {"completed": False}, student)
        assert undone["completed"] is False and item()["completed"] is False
        print("Completion undo and readback PASS; fixture left incomplete for browser acceptance", flush=True)
    finally:
        for token in sessions:
            call("POST", "/api/auth/logout", {}, token)


if __name__ == "__main__":
    main()
