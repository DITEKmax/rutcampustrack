"""Synthetic telemetry source and authenticated sink, only for the isolated CI cohort."""

import http.server
import json
import os
import time

started = time.monotonic()


class Handler(http.server.BaseHTTPRequestHandler):
    def log_message(self, *_args):
        pass

    def do_GET(self):
        if self.path != "/metrics":
            self.send_error(404)
            return
        elapsed = time.monotonic() - started
        host = 'job="node-exporter",instance="synthetic-host:9100",environment="synthetic"'
        values = [
            f'node_cpu_seconds_total{{{host},cpu="0",mode="idle"}} {elapsed / 2}',
            f'process_cpu_seconds_total{{job="distractor",instance="synthetic-process"}} {elapsed * 100}',
            f'node_filesystem_size_bytes{{{host},fstype="ext4",mountpoint="/"}} 100',
            f'node_filesystem_avail_bytes{{{host},fstype="ext4",mountpoint="/"}} 20',
            f'node_filesystem_size_bytes{{{host},fstype="xfs",mountpoint="/logs"}} 100',
            f'node_filesystem_avail_bytes{{{host},fstype="xfs",mountpoint="/logs"}} 5',
            'container_memory_usage_bytes{name="rct-fixture",instance="synthetic-cadvisor"} 75',
            'container_spec_memory_limit_bytes{name="rct-fixture",instance="synthetic-cadvisor"} 100',
        ]
        for instance, value in [("a:9091", 95), ("b:9091", 10)]:
            labels = f'job="academic-service",instance="{instance}",environment="synthetic",area="heap",id="old"'
            values += [f"jvm_memory_used_bytes{{{labels}}} {value}", f"jvm_memory_max_bytes{{{labels}}} 100"]
        # Same RPC method on two downstream services must remain two series
        # after production target-label precedence and retained dashboard grouping.
        for downstream, status, multiplier in [("ScheduleGrpcService", "OK", 1), ("AuthGrpcService", "UNAVAILABLE", 2)]:
            labels = f'grpc_service="{downstream}",method="Get",status="{status}"'
            for bound, fraction in [("0.01", 0.5), ("0.1", 1), ("+Inf", 1)]:
                values.append(f'grpc_client_duration_seconds_bucket{{{labels},le="{bound}"}} {elapsed * multiplier * fraction}')
            values.append(f'grpc_client_duration_seconds_count{{{labels}}} {elapsed * multiplier}')
            values.append(f'grpc_client_duration_seconds_sum{{{labels}}} {elapsed * multiplier * 0.02}')
        self.send_response(200)
        self.end_headers()
        self.wfile.write(("\n".join(values) + "\n").encode())

    def do_POST(self):
        payload = json.loads(self.rfile.read(int(self.headers.get("Content-Length", "0"))))
        if self.path == "/emit":
            print(
                json.dumps(
                    {
                        "service": "notification-web",
                        "environment": "synthetic",
                        "level": "ERROR",
                        "msg": "synthetic diagnostic fixture",
                        "requestId": payload["requestId"],
                        "traceId": payload["traceId"],
                        "spanId": payload["spanId"],
                    }
                ),
                flush=True,
            )
        elif self.path == "/internal/alert":
            if self.headers.get("Authorization") != "Bearer " + os.environ["SYNTHETIC_RECEIVER_TOKEN"]:
                self.send_error(401)
                return
        else:
            self.send_error(404)
            return
        self.send_response(200)
        self.end_headers()


http.server.ThreadingHTTPServer(("0.0.0.0", 9094), Handler).serve_forever()
