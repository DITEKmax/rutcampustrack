"""Bounded local process smoke. Run only after root grants the heavy lease."""
import hashlib
import json
import os
from pathlib import Path
import re
import socket
import subprocess
import sys
import time
import urllib.request

root = Path(__file__).resolve().parents[3]
evidence = Path(__file__).resolve().parent
if len(sys.argv) > 1:
    assert re.fullmatch(r"[a-z0-9-]+", sys.argv[1]), "invalid evidence run label"
    evidence = evidence / sys.argv[1]
    evidence.mkdir(exist_ok=True)
jar = root / "services/mobile-bff/mobile-bff-app/build/libs/mobile-bff-app-0.1.0.jar"
java = Path("C:/Users/maksd/.jdks/ms-21.0.10/bin/java.exe")
log_path = evidence / "runtime-startup.log"
result_path = evidence / "runtime-result.json"
for path in (log_path, result_path, evidence / "runtime-metrics.txt"):
    if path.exists():
        raise RuntimeError(f"Do not overwrite existing run evidence: {path}")
result = {"sourceRevision": subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=root, text=True).strip(),
          "jarSha256": hashlib.sha256(jar.read_bytes()).hexdigest(),
          "status": "FAIL", "startedAt": time.strftime("%Y-%m-%dT%H:%M:%S%z"),
          "limitations": ["Auth key unavailable: expected loopback connection refused during startup; no auth path tested.",
                          "No Prometheus server/alert evaluation/provider/deploy check."]}
env = {key: os.environ[key] for key in ("SystemRoot", "WINDIR", "PATH", "TEMP", "TMP") if key in os.environ}
env["GRPC_SECRET"] = "synthetic-mobile-monitoring-local-only"
process = None
with socket.socket() as reserved, log_path.open("w", encoding="utf-8") as log:
    # Bound but not listening: no helper/server, no outside connection or borrowed port.
    reserved.bind(("127.0.0.1", 0))
    unavailable_port = reserved.getsockname()[1]
    command = [str(java), "-Xms64m", "-Xmx256m", "-jar", str(jar),
               "--server.address=127.0.0.1", "--server.port=0",
               f"--rutcampustrack.security.internal-jwt.auth-service-url=http://127.0.0.1:{unavailable_port}"]
    command += [f"--grpc.client.{service}.address=dns:///127.0.0.1:{unavailable_port}"
                for service in ("academic-service", "attendance-service", "schedule-service")]
    result["command"] = command
    try:
        process = subprocess.Popen(command, cwd=root, env=env, stdout=log, stderr=subprocess.STDOUT,
                                   creationflags=subprocess.CREATE_NO_WINDOW)
        result["ownedPid"] = process.pid
        deadline = time.monotonic() + 90
        port = None
        while time.monotonic() < deadline:
            if process.poll() is not None:
                raise RuntimeError(f"BFF exited during startup: {process.returncode}")
            match = re.search(r"Tomcat started on port (\d+)", log_path.read_text(encoding="utf-8"))
            if match:
                port = int(match.group(1))
                break
            time.sleep(0.25)
        if port is None:
            raise RuntimeError("BFF startup exceeded bounded 90 seconds")
        result["port"] = port
        result["url"] = f"http://127.0.0.1:{port}/actuator/prometheus"
        request = urllib.request.Request(result["url"], headers={"Accept": "text/plain"})
        opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
        with opener.open(request, timeout=10) as response:
            body = response.read().decode("utf-8")
            result["httpStatus"] = response.status
            result["contentType"] = response.headers.get("Content-Type")
        (evidence / "runtime-metrics.txt").write_text(body, encoding="utf-8")
        assert result["httpStatus"] == 200
        assert "text/plain" in result["contentType"]
        assert "# HELP " in body and "# TYPE " in body
        required = ("jvm_memory_used_bytes", "process_uptime_seconds", "process_cpu_usage")
        for metric in required:
            assert re.search(r"^" + metric + r"(?:\{|\s)", body, re.MULTILINE), metric
        result["verifiedMetrics"] = required
        result["status"] = "PASS"
    except BaseException as error:
        result["error"] = f"{type(error).__name__}: {error}"
        raise
    finally:
        if process is not None:
            if process.poll() is None:
                process.terminate()
                try:
                    process.wait(timeout=15)
                except subprocess.TimeoutExpired:
                    process.kill()
                    process.wait(timeout=5)
            result["cleanup"] = {"ownedPid": process.pid, "poll": process.poll(), "reaped": process.poll() is not None}
        result["finishedAt"] = time.strftime("%Y-%m-%dT%H:%M:%S%z")
        result_path.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print(json.dumps(result, ensure_ascii=False))
