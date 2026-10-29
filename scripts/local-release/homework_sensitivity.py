"""Canonical CI3718–3831 navigation omission oracle, source restoration and green.

Only the named Vue boundary may differ while the red fixture admission executes.
"""
import hashlib
import json
import time
from pathlib import Path
import local_e2e_async as lane

OWNER = "frontends/mobile-core/src/shared/components/StudentFeatureOwner.vue"
BOUNDARY = b"  if (navigate) navigation.push(homeworkNotificationRoute);"
FAULT = b"  if (navigate) void homeworkNotificationRoute;"


def red_oracle(report, lines):
    lane.require(set(report) == {"status", "runnerErrors", "unsafeArtifact", "suppressedStdoutBytes", "suppressedStderrBytes", "results"}
                 and report["status"] == "failed" and report["runnerErrors"] == 0 and report["unsafeArtifact"] is False
                 and report["suppressedStdoutBytes"] == 0 and report["suppressedStderrBytes"] == 0
                 and len(report["results"]) == 1, "HOMEWORK_RED_REPORT")
    declaration = ["isolated(", "  'MANAGE_HOMEWORK non-author edits preserve student completion and revoked authority cannot overwrite it @real-mobile',", "  async ({ page, scenario: dataset }, info) => {"]
    target_block = ["    await expect(notificationsDialog).toHaveCount(0);", "    await expect(card).toBeVisible();",
                    "    await expect(card).toHaveAttribute('data-completion', 'completed');", "    await expect(completedSlider).toBeFocused();"]
    starts = [i for i in range(len(lines) - len(target_block) + 1) if lines[i:i + len(target_block)] == target_block]
    declarations = [i for i in range(len(lines) - len(declaration) + 1) if lines[i:i + len(declaration)] == declaration]
    lane.require(len(starts) == len(declarations) == 1, "HOMEWORK_RED_SOURCE")
    result = report["results"][0]
    allowed = [[{"file": "mobile-homework-lifecycle.spec.ts", "line": starts[0] + offset}] for offset in (2, 4)]
    lane.require(result["file"] == "mobile-homework-lifecycle.spec.ts" and result["line"] == declarations[0] + 1
                 and result["project"] == "chromium" and result["status"] == "failed" and result["expectedStatus"] == "passed"
                 and type(result["errorCount"]) is int and result["errorCount"] == 1 and result["errorLocations"] in allowed,
                 "HOMEWORK_RED_WRONG_FAILURE")


def restore_owned_source(owner, original, identity):
    """Resolve publication/flag interruption by observed exact bytes, never a flag."""
    lane.require(not owner.is_symlink() and (owner.stat().st_dev, owner.stat().st_ino, owner.stat().st_mode) == identity,
                 "HOMEWORK_OWNER_CHANGED")
    observed = owner.read_bytes()
    lane.require(observed in (original, original.replace(BOUNDARY, FAULT)), "HOMEWORK_RESTORE_BYTES")
    if observed != original:
        owner.write_bytes(original)
    lane.require(not owner.is_symlink() and (owner.stat().st_dev, owner.stat().st_ino, owner.stat().st_mode) == identity
                 and owner.read_bytes() == original, "HOMEWORK_RESTORE_BYTES")


def execute(runner):
    # Imports are local to avoid a dependency cycle when controls import this oracle.
    from importlib.util import spec_from_file_location, module_from_spec
    spec = spec_from_file_location("browser_replay_validation", Path(__file__).with_name("browser-replay.py"))
    validation = module_from_spec(spec)
    spec.loader.exec_module(validation)
    owner = lane.ROOT / OWNER
    lane.require(owner.is_file() and not owner.is_symlink(), "HOMEWORK_OWNER")
    original = owner.read_bytes()
    lane.require(original.count(BOUNDARY) == 1, "HOMEWORK_BOUNDARY")
    backup = runner.run / "homework-original.vue"
    lane.write(backup, original.decode())
    lane.require(backup.read_bytes() == original, "HOMEWORK_BACKUP")
    owner_identity = (owner.stat().st_dev, owner.stat().st_ino, owner.stat().st_mode)
    base = runner.env.copy()
    runner.deadline = time.monotonic() + 420
    runner.source_restored = False
    restored = False

    def checked_write(expected, new):
        lane.require(not owner.is_symlink() and (owner.stat().st_dev, owner.stat().st_ino, owner.stat().st_mode) == owner_identity
                     and owner.read_bytes() == expected, "HOMEWORK_OWNER_CHANGED")
        owner.write_bytes(new)
        lane.require(owner.read_bytes() == new, "HOMEWORK_SOURCE_PUBLICATION")

    def rebuild(label):
        runner.command(label + "_BUILD", runner.compose("build", "pwa-nginx"))
        # A sensitivity rebuild intentionally changes only the own nonce image ID.
        config = json.loads((runner.run / "compose.json").read_text())
        tag = config["services"]["pwa-nginx"]["image"]
        runner.image_ids[tag] = runner.image_identity(tag)
        runner.command(label + "_RECREATE", runner.compose("up", "-d", "--no-deps", "--force-recreate", "pwa-nginx"))
        runner.command(label + "_RELOAD", runner.compose("exec", "-T", "nginx", "nginx", "-s", "reload"))
        runner.command(label + "_READY", ["curl", "--silent", "--show-error", "--fail", "--retry", "5", "--retry-all-errors",
                       "--retry-delay", "1", "--retry-max-time", "15", "--max-time", "5", "--cacert", str(runner.run / "certs/server.crt"), "https://localhost:8443/app/"])

    def test(phase):
        path = runner.run / phase
        path.mkdir(mode=0o700)
        (path / "artifacts").mkdir(mode=0o700)
        runner.env = {**base, "RCT_LOCAL_RELEASE_SENSITIVITY_PHASE": phase, "RCT_HOMEWORK_LIFECYCLE_RUN": str(path),
                      "RCT_REMAINING5_RUN": str(path), "RCT_DIAGNOSTIC_RUN_DIR": str(path)}
        lane.validate_local(environment=runner.env, expected_mode="client")
        code = 0
        try:
            runner.command("HOMEWORK_" + phase.upper(), ["npx", "playwright", "test", "--config", "playwright.homework-lifecycle.config.ts",
                           "--project=chromium", "--grep", "MANAGE_HOMEWORK non-author edits"], cwd=lane.ROOT / "tests/e2e")
        except lane.AdmissionError as error:
            lane.require(str(error) == "CHILD_FAILED", "HOMEWORK_CHILD_INFRASTRUCTURE")
            code = error.returncode
        report = validation.read_report(path / "artifacts/safe-results.json")
        if phase == "red":
            lane.require(code == 1, "HOMEWORK_RED_EXIT")
            red_oracle(report, (lane.ROOT / "tests/e2e/specs/mobile-homework-lifecycle.spec.ts").read_text().splitlines())
        else:
            lane.require(code == 0, "HOMEWORK_GREEN_EXIT")
            validation.closed_report(report, 1, "chromium")

    try:
        checked_write(original, original.replace(BOUNDARY, FAULT))
        rebuild("HOMEWORK_RED")
        test("red")
        runner.env = base
        checked_write(original.replace(BOUNDARY, FAULT), original)
        rebuild("HOMEWORK_RESTORE")
        restored = True
        runner.source_restored = True
        test("green")
    finally:
        runner.env = base
        if not restored:
            # Restoration gets its own bounded recovery budget, never extends the test gate.
            runner.deadline = time.monotonic() + 420
            restore_owned_source(owner, original, owner_identity)
            rebuild("HOMEWORK_FINALLY_RESTORE")
            restored = True
            runner.source_restored = True
        lane.write(runner.run / "homework-restoration.safe.json", json.dumps({"restorationSucceeded": restored,
                   "sourceSha256": hashlib.sha256(original).hexdigest(), "source": OWNER}) + "\n")
        runner.deadline = None
