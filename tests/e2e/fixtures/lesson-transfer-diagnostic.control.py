"""Exercise the actual private entrypoints/projection with synthetic files and fake PG reads only."""

import argparse
import copy
import hashlib
import importlib.util
import io
import json
import os
import shutil
import tempfile
from pathlib import Path
from unittest.mock import patch

SOURCE = Path(__file__).with_name("lesson-transfer-diagnostic.py")
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--clock-producer", type=Path,
                    default=SOURCE.parent.parent / "backend-clock/prepare-clock.mjs")
args = parser.parse_args()
spec = importlib.util.spec_from_file_location("own_transfer_control", SOURCE)
m = importlib.util.module_from_spec(spec)
spec.loader.exec_module(m)
MARKER = "SYNTHETIC_PRIVATE_VALUE_MUST_NOT_ESCAPE"
OWN = {"schema": "lesson-transfer-input.v1", "operationId": "00000000-0000-4000-8000-000000000001",
       "sourceLessonId": "11", "targetLessonId": "12", "occurrenceId": "13", "groupId": "14",
       "semesterId": "15", "actorId": "16", "observationOutcome": "RETURNED"}


def schedule(**changes):
    value = {key: 0 for key in m.COUNT_KEYS}
    value.update({key: True for key in m.SCHEDULE_BOOL})
    value.update(overflow=False, operationState="COMPLETED", expectedBatches=1,
                 attendanceApplied=1, academicApplied=1, requestRows=1, requestSent=1, requestBatchCoverage=1)
    value.update(changes)
    return value


def academic(**changes):
    value = {key: 0 for key in m.ACADEMIC_COUNTS}
    value.update({key: True for key in m.ACADEMIC_BOOL})
    value.update(overflow=False, receiptRows=1, expectedBatches=1, applied=1,
                 ackRows=1, ackSent=1, ackBatchCoverage=1)
    value.update(changes)
    return value


class Stand:
    def __init__(self, actual_clock_producer=False):
        self.actual_clock_producer = actual_clock_producer

    def __enter__(self):
        self.temp = tempfile.TemporaryDirectory(prefix="rct-own-transfer-control-")
        self.root = Path(self.temp.name)
        self.run = self.root / "rct-lesson-lifecycle-1-1"
        self.clock = self.root / "rct-first3-clock-1-1"
        for path in (self.run, self.run / "artifacts"):
            path.mkdir(mode=0o700)
        if self.actual_clock_producer:
            # Execute the existing producer only against new temporary files; no jar is run.
            jar = self.root / "dummy.jar"
            jar.write_bytes(b"filesystem-contract-only")
            command = [shutil.which("node"), str(args.clock_producer.resolve()), str(self.clock), str(jar)]
            result = m.subprocess.run(command, env={**os.environ, "RCT_E2E_SYNTHETIC_SEED": "true",
                                                   "E2E_BASE_URL": "https://localhost:8443"},
                                      umask=0o022, capture_output=True, timeout=5)
            assert result.returncode == 0 and len(result.stdout) <= 4096 and len(result.stderr) <= 4096
        else:
            self.clock.mkdir(mode=0o755)
            self.clock.chmod(0o755)
            marker = self.clock / "disposable-target"
            marker.write_bytes(b"RCT_E2E_DISPOSABLE\n")
            marker.chmod(0o644)
        marker = self.clock / "disposable-target"
        self.env = {"GITHUB_ACTIONS": "true", "RUNNER_ENVIRONMENT": "github-hosted", "CI": "true",
                    "RCT_E2E_SYNTHETIC_SEED": "true", "E2E_BASE_URL": "https://localhost:8443",
                    "GITHUB_WORKSPACE": str(self.root), "RUNNER_TEMP": str(self.root),
                    "GITHUB_RUN_ID": "1", "GITHUB_RUN_ATTEMPT": "1",
                    "RCT_LESSON_LIFECYCLE_RUN": str(self.run), "RCT_E2E_TEACHING_CLOCK_DIR": str(self.clock)}
        return self

    def put(self, value=OWN, raw=None):
        path = self.run / m.INPUT
        path.write_bytes(json.dumps(value).encode() if raw is None else raw)
        path.chmod(0o600)
        return path

    def result(self):
        path = self.run / m.OUTPUT
        assert path.stat().st_mode & 0o777 == 0o600 and 0 < path.stat().st_size <= 4096
        raw = path.read_text()
        assert all(item not in raw for item in (MARKER, OWN["operationId"], '"sourceLessonId"', '"payload"'))
        result = json.loads(raw)
        assert result["snapshotStage"] == "AFTER_LESSON_STEP_AND_CASE_CLEANUP"
        assert result["snapshotAtomicity"] == "PER_DB_ONLY"
        assert result["cleanupOutcome"] == "UNKNOWN" and result["mongo"] == "NOT_QUERIED"
        return result

    def __exit__(self, *_):
        self.temp.cleanup()


def refused(stand, *, phase="--snapshot", stdin=None, pg=None, containers=None):
    with patch.dict(os.environ, stand.env, clear=True), patch.object(
        m, "_containers", side_effect=containers or AssertionError("Owned target lookup forbidden")
    ), patch.object(m, "_pg", side_effect=pg or AssertionError("PG read forbidden")):
        try:
            if stdin is None:
                m.main([phase])
            else:
                with patch.object(m.sys, "stdin", io.TextIOWrapper(io.BytesIO(stdin))):
                    m.main([phase])
        except (ValueError, OSError, KeyError, TypeError, subprocess_error):
            return
    raise AssertionError("Unsafe diagnostic admission unexpectedly succeeded")


subprocess_error = m.subprocess.SubprocessError
groups = 0

# Actual existing producer contract: old private-clock guard rejects this 0755/0644 stand.
with Stand(actual_clock_producer=True) as stand, patch.dict(os.environ, stand.env, clear=True), patch.object(
    m, "_containers", side_effect=AssertionError("Producer contract must not inspect Docker")
), patch.object(m, "_pg", side_effect=AssertionError("Producer contract must not read PG")):
    assert stand.clock.stat().st_mode & 0o777 == 0o755
    assert (stand.clock / "disposable-target").stat().st_mode & 0o777 == 0o644
    assert (stand.clock / "disposable-target").read_bytes() == b"RCT_E2E_DISPOSABLE\n"
    with patch.object(m.sys, "stdin", io.TextIOWrapper(io.BytesIO(json.dumps(OWN).encode()))):
        m.main(["--retain-private"])
    assert json.loads((stand.run / m.INPUT).read_text()) == OWN
    assert (stand.run / m.INPUT).stat().st_mode & 0o777 == 0o600
groups += 1

# Missing/pre-POST witness is a nonapplicable outcome, before every process/PG operation.
with Stand() as stand, patch.dict(os.environ, stand.env, clear=True), patch.object(
    m, "_containers", side_effect=AssertionError("No process for missing witness")
), patch.object(m, "_pg", side_effect=AssertionError("No PG for missing witness")):
    m.main(["--snapshot"])
    assert stand.result()["qualification"] == "NOT_RUN"
groups += 1

# The real producer mode writes once, owner-only, and rejects a later replacement.
with Stand() as stand, patch.dict(os.environ, stand.env, clear=True), patch.object(
    m, "_containers", side_effect=AssertionError("Producer cannot inspect Docker")
):
    with patch.object(m.sys, "stdin", io.TextIOWrapper(io.BytesIO(json.dumps(OWN).encode()))):
        m.main(["--retain-private"])
    assert json.loads((stand.run / m.INPUT).read_text()) == OWN
    assert (stand.run / m.INPUT).stat().st_mode & 0o777 == 0o600
    refused(stand, phase="--retain-private", stdin=json.dumps(dict(OWN, actorId="17")).encode())
    assert json.loads((stand.run / m.INPUT).read_text()) == OWN
groups += 1

for key, changed in (("GITHUB_ACTIONS", "false"), ("RUNNER_ENVIRONMENT", "self-hosted"),
                     ("CI", "false"), ("RCT_E2E_SYNTHETIC_SEED", "false"),
                     ("E2E_BASE_URL", "https://foreign.invalid"), ("GITHUB_RUN_ID", "01"),
                     ("GITHUB_RUN_ATTEMPT", "1\n"), ("RCT_LESSON_LIFECYCLE_RUN", "/tmp/foreign"),
                     ("RCT_E2E_TEACHING_CLOCK_DIR", "/tmp/foreign")):
    with Stand() as stand:
        stand.put()
        stand.env[key] = changed
        refused(stand)
groups += 1

for alteration in ("run_mode", "artifact_mode", "clock_private_mode", "clock_write_mode",
                   "marker_private_mode", "marker_write_mode", "marker_value", "marker_json",
                   "input_mode", "input_symlink", "input_hardlink", "input_empty", "input_large"):
    with Stand() as stand:
        path = stand.put()
        if alteration == "run_mode": stand.run.chmod(0o755)
        elif alteration == "artifact_mode": (stand.run / "artifacts").chmod(0o755)
        elif alteration == "clock_private_mode": stand.clock.chmod(0o700)
        elif alteration == "clock_write_mode": stand.clock.chmod(0o777)
        elif alteration == "marker_private_mode": (stand.clock / "disposable-target").chmod(0o600)
        elif alteration == "marker_write_mode": (stand.clock / "disposable-target").chmod(0o666)
        elif alteration == "marker_value": (stand.clock / "disposable-target").write_text(MARKER)
        elif alteration == "marker_json": (stand.clock / "disposable-target").write_text('{"target":"RCT_E2E_DISPOSABLE"}')
        elif alteration == "input_mode": path.chmod(0o644)
        elif alteration == "input_symlink":
            path.rename(stand.run / "foreign")
            path.symlink_to(stand.run / "foreign")
        elif alteration == "input_hardlink": os.link(path, stand.run / "other")
        elif alteration == "input_empty": path.write_bytes(b"")
        elif alteration == "input_large": path.write_bytes(b" " * 4097)
        refused(stand)
groups += 1

bad_inputs = [None, [], dict(OWN, extra=MARKER), dict(OWN, operationId=MARKER),
              dict(OWN, operationId=OWN["operationId"].upper()), dict(OWN, groupId="14;SELECT 1"),
              dict(OWN, actorId=True), dict(OWN, occurrenceId="0"), dict(OWN, semesterId=str(2**63)),
              dict(OWN, targetLessonId=OWN["sourceLessonId"]), dict(OWN, observationOutcome="ERROR")]
# UUID digits above have no letters: use a canonical UUID with a letter for the uppercase case.
bad_inputs[4] = dict(OWN, operationId="AAAAAAAA-0000-4000-8000-000000000001")
for value in bad_inputs:
    with Stand() as stand:
        stand.put(value)
        refused(stand)
        assert stand.result()["readOutcome"] == "UNAVAILABLE"
for raw in (b"{", b'{"schema":1,"schema":2}', b'{"value":NaN}'):
    with Stand() as stand:
        stand.put(raw=raw)
        refused(stand)
groups += 1

# A purported applicable input disappearing after its existence check remains a strict read failure.
with Stand() as stand:
    stand.put()
    real_read = m._read
    def disappear(fd, name, limit=m.LIMIT, **kwargs):
        if name == m.INPUT:
            raise FileNotFoundError(MARKER)
        return real_read(fd, name, limit, **kwargs)
    with patch.object(m, "_read", side_effect=disappear):
        refused(stand)
    assert stand.result()["readOutcome"] == "UNAVAILABLE"
groups += 1


def snapshot(own=OWN, s=None, a=None):
    with Stand() as stand:
        stand.put(own)
        reads = []

        def read(container, store, sql, deadline):
            assert container == store and sql.startswith("BEGIN READ ONLY;")
            assert "statement_timeout='2000ms'" in sql and sql.endswith("ROLLBACK;")
            assert not any(word in sql.upper() for word in ("FOR UPDATE", "GRANT ", "SET ROLE", "COPY ", "DELETE ", "INSERT ", "UPDATE "))
            assert OWN["operationId"] in sql
            reads.append(store)
            return copy.deepcopy((s or schedule()) if store == "schedule" else (a or academic()))

        with patch.dict(os.environ, stand.env, clear=True), patch.object(
            m, "_containers", return_value={"postgres-schedule": "schedule", "postgres-academic": "academic"}
        ), patch.object(m, "_pg", side_effect=read):
            m.main(["--snapshot"])
        assert reads == ["schedule", "academic"]
        return stand.result()


assert snapshot()["qualification"] == "PASS"  # Fake control only; never integration evidence.
assert snapshot(s=schedule(requestSent=0, requestPending=1), a=academic(ackSent=0, ackPending=1))["qualification"] == "PASS"
timeout = snapshot(dict(OWN, observationOutcome="TIMEOUT"), schedule(operationState="PENDING", academicApplied=0))
assert timeout["applicability"] == "FAILURE_STAGE" and timeout["qualification"] == "NOT_CONFIRMED"
missing = snapshot(s=schedule(operationPresent=False, tupleMatches=False, operationState="MISSING"))
assert missing["qualification"] == "NOT_CONFIRMED" and missing["schedule"]["academicApplied"] is None
assert snapshot(a=academic(receiptRows=0, expectedBatches=0, applied=0, ackRows=0, ackSent=0, ackBatchCoverage=0))["qualification"] == "NOT_CONFIRMED"
assert snapshot(s=schedule(overflow=True))["qualification"] == "NOT_CONFIRMED"
assert snapshot(a=academic(ackIdentityMatches=False))["qualification"] == "NOT_CONFIRMED"
groups += 1

# A purported owned operation with a foreign immutable tuple fails before the Academic query.
with Stand() as stand:
    stand.put()
    observed = []
    def foreign(_container, store, _sql, _deadline):
        observed.append(store)
        return schedule(tupleMatches=False)
    refused(stand, containers=lambda _: {"postgres-schedule": "s", "postgres-academic": "a"}, pg=foreign)
    assert observed == ["schedule"] and stand.result()["readOutcome"] == "UNAVAILABLE"
groups += 1

for invalid in (dict(schedule(), private=MARKER), schedule(operationState=MARKER),
                schedule(principalMatches=False), schedule(requestRows=True), schedule(expectedBatches=258)):
    with Stand() as stand:
        stand.put()
        refused(stand, containers=lambda _: {"postgres-schedule": "s", "postgres-academic": "a"}, pg=lambda *args: invalid)
        assert stand.result()["qualification"] == "NOT_RUN"
groups += 1

for failure in (ValueError(MARKER), TimeoutError(MARKER), m.subprocess.TimeoutExpired(MARKER, 1)):
    with Stand() as stand:
        stand.put()
        refused(stand, containers=lambda _: {"postgres-schedule": "s", "postgres-academic": "a"}, pg=lambda *args: (_ for _ in ()).throw(failure))
        assert stand.result()["readOutcome"] == "UNAVAILABLE"
groups += 1

# Real owned-container guard, with fake command bytes only, before any PG call.
def owned_docker(argv, _deadline):
    if argv[1] == "ps":
        assert "--no-trunc" in argv
        return ("a" * 64 if argv[-1].endswith("postgres-schedule") else "b" * 64).encode()
    identity = argv[-1]
    return json.dumps([identity, "rct-e2e", "postgres-schedule" if identity == "a" * 64 else "postgres-academic", True]).encode()
with patch.object(m, "_call", side_effect=owned_docker):
    assert m._containers(1) == {"postgres-schedule": "a" * 64, "postgres-academic": "b" * 64}
for altered in ("ambiguous", "foreign_project", "foreign_service", "stopped"):
    seen = []
    def docker(argv, _deadline):
        seen.append(argv)
        if argv[1] == "ps":
            return ("a" * 64 + ("\n" + "b" * 64 if altered == "ambiguous" else "")).encode()
        return json.dumps(["a" * 64, "foreign" if altered == "foreign_project" else "rct-e2e",
                           "foreign" if altered == "foreign_service" else "postgres-schedule",
                           altered != "stopped"]).encode()
    with patch.object(m, "_call", side_effect=docker):
        try:
            m._containers(1)
        except ValueError:
            pass
        else:
            raise AssertionError("Unowned PG container admitted")
    assert seen
groups += 1

# Fixed principal/command shape: no credential, recovery helper, Mongo or queue reader can be invoked.
calls = []
with patch.object(m, "_call", side_effect=lambda argv, deadline, data: (calls.append((argv, data)) or b"{}")):
    m._pg("a" * 64, "schedule", m._schedule_sql(OWN), 1)
    m._pg("b" * 64, "academic", m._academic_sql(OWN), 1)
assert [call[0][call[0].index("-U") + 1] for call in calls] == ["rct_user", "rct_academic_runtime"]
assert all("--no-password" in call[0] and "PGPASSWORD" not in " ".join(call[0]) for call in calls)
assert "one_off_lesson_id" in m._schedule_sql(OWN) and "state='planned'" not in m._schedule_sql(OWN)
assert not any(word in SOURCE.read_text() for word in ("mongo_query", "check_targets", "import recovery", "MONGO_ROOT_PASSWORD"))
groups += 1

print(json.dumps({"schema": "lesson-transfer-controls.v1", "groupsPassed": groups,
                  "actualClockProducerSha256": hashlib.sha256(args.clock_producer.read_bytes()).hexdigest(),
                  "runtimeIntegration": "NOT_RUN", "actualPgQualification": "NOT_RUN"}))
