"""Build exactly one existing PR3 Vue app, with the agreed serial disk guard."""
import argparse
import json
import os
import signal
import shutil
import subprocess
import time
from pathlib import Path

import gate


def stop_owned(process, directory):
    def group_exists():
        try:
            os.killpg(process.pid, 0)
            return True
        except ProcessLookupError:
            return False

    if not group_exists():
        process.wait(timeout=5)
        return
    try:
        os.killpg(process.pid, signal.SIGTERM)
    except ProcessLookupError:
        process.wait(timeout=5)
        return
    deadline = time.monotonic() + 8
    while group_exists() and time.monotonic() < deadline:
        process.poll()  # Reap the leader while still checking any owned descendants.
        if shutil.disk_usage(directory).free < gate.HARD:
            break
        time.sleep(0.25)
    if group_exists():
        try:
            os.killpg(process.pid, signal.SIGKILL)
        except ProcessLookupError:
            pass
    process.wait(timeout=5)


def run_owned(command, environment, stream, directory, before, started):
    received_signal = None

    def request_stop(signum, _frame):
        # Do not raise inside Popen before its newly-created child is assigned.
        nonlocal received_signal
        received_signal = signum

    previous = {item: signal.signal(item, request_stop) for item in (signal.SIGTERM, signal.SIGINT)}
    process = None
    interrupted = False
    try:
        process = subprocess.Popen(command, cwd=gate.REPO / "frontends", env=environment,
                                   stdout=stream, stderr=subprocess.STDOUT, start_new_session=True)
        while process.poll() is None:
            free = shutil.disk_usage(directory).free
            if received_signal is not None or free < gate.EARLY or before - free > 512 * 1024**2 or time.monotonic() - started > 180:
                interrupted = True
                break
            time.sleep(0.25)
    finally:
        try:
            if process is not None:
                stop_owned(process, directory)
        finally:
            for item, handler in previous.items():
                signal.signal(item, handler)
    return process.returncode, interrupted or received_signal is not None


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--expected-sha", required=True)
    parser.add_argument("--app", choices=gate.APPS, required=True)
    parser.add_argument("--out", required=True, help="Fresh artifact directory, outside tracked sources")
    args = parser.parse_args()
    gate.checkout(args.expected_sha)
    output = Path(args.out).resolve()
    gate.require(output.parent.is_dir() and not output.exists(), "Use a fresh build directory")
    gate.require(not output.is_relative_to(gate.REPO / "frontends"), "Keep gate artifacts outside frontend source")
    before = gate.budget(output.parent)
    gate.require(before - 512 * 1024**2 >= gate.EARLY, "512 MiB forecast would cross the early threshold")
    app = gate.APPS[args.app]
    for path in (gate.REPO / "frontends" / app).glob(".env*"):
        gate.require(path.name.endswith((".example", ".sample")), "Local .env build input is unpinned")
    log = output.parent / (args.app + ".build.log")
    receipt = output.parent / (args.app + ".pin.json")
    gate.require(not log.exists() and not receipt.exists(), "Do not overwrite previous build evidence")
    environment = {key: value for key, value in os.environ.items() if not key.startswith("VITE_")}
    environment.update(gate.producer_config(args.app))
    environment["CI"] = "true"
    environment["NODE_OPTIONS"] = "--max-old-space-size=1536"
    command = ["npm", "run", "build", "--workspace", "@rct/" + app, "--", "--outDir", str(output)]
    started = time.monotonic()
    with log.open("xb") as stream:
        code, interrupted = run_owned(command, environment, stream, output.parent, before, started)
    gate.require(not interrupted and code == 0, "Owned build failed or its 2.5 GiB/180s guard stopped it; keep the raw log")
    gate.checkout(args.expected_sha)
    gate.save(receipt, {"app": args.app, "sourceSha": args.expected_sha, "gateSourceSha": args.expected_sha,
        "sourceInputsSha256": gate.app_inputs(args.app, args.expected_sha),
        "files": gate.inventory(output), "buildEvidenceSha256": gate.sha(log.read_bytes()),
        "artifactContract": gate.artifact_contract(args.app, output), "producerConfigEvidence": "ENFORCED_BUILD_ENV",
        "provenance": "FRESH_STRICT_WORKSPACE_BUILD", "elapsedSeconds": round(time.monotonic() - started, 3),
        "availableBefore": before, "availableAfter": gate.budget(output.parent)})
    print(json.dumps({"state": "BUILD_PASS", "app": args.app, "sourceSha": args.expected_sha}))


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        print(json.dumps({"state": "BUILD_FAIL", "reason": str(error)}))
        raise SystemExit(1)
