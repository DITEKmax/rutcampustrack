"""Bounded loopback handoff for one Figma reader → packet-writer transfer.

The listener accepts only JSON chunks from 127.0.0.1 and writes only to its own
staging directory. Clients cannot select paths. It is deliberately independent of
Figma credentials, accounts, or external networking.
"""

from __future__ import annotations

import argparse
import base64
import hashlib
import json
import re
import sys
from http import HTTPStatus
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from typing import Any


MAX_BODY_BYTES = 14_000
MAX_TRANSFER_BYTES = 10 * 1024 * 1024
TRANSFER_ID = re.compile(r"^[A-Za-z0-9_-]{1,64}$")
KINDS = {"raw-json", "screenshot-base64"}
# Root has scheduled only these logical reads for this packet. This is a protocol
# allow-list, not a client-controlled path or an authority to issue Figma reads.
ALLOWED_TRANSFER_IDS = {
    "R002", "R003", "R004", "R005", "R006", "R007", "R008", "R009", "R010", "R011"
}


class Receiver:
    def __init__(self, output_dir: Path) -> None:
        self.output_dir = output_dir.resolve()
        self.transfers: dict[tuple[str, str], dict[str, Any]] = {}

    def receive(self, packet: dict[str, Any]) -> tuple[int, dict[str, Any]]:
        transfer_id = packet.get("transferId")
        kind = packet.get("kind")
        sequence = packet.get("sequence")
        total = packet.get("total")
        payload = packet.get("payload")
        if (
            not isinstance(transfer_id, str)
            or not TRANSFER_ID.fullmatch(transfer_id)
            or transfer_id not in ALLOWED_TRANSFER_IDS
            or kind not in KINDS
            or not isinstance(sequence, int)
            or not isinstance(total, int)
            or not 0 <= sequence < total <= 2048
            or not isinstance(payload, str)
            or len(payload.encode("utf-8")) > 7_000
        ):
            return HTTPStatus.BAD_REQUEST, {"error": "invalid chunk envelope"}

        key = (transfer_id, kind)
        state = self.transfers.get(key)
        if state is None:
            state = {"total": total, "parts": {}, "bytes": 0}
            self.transfers[key] = state
        if state["total"] != total:
            return HTTPStatus.CONFLICT, {"error": "transfer total changed"}
        prior = state["parts"].get(sequence)
        if prior is not None and prior != payload:
            return HTTPStatus.CONFLICT, {"error": "chunk conflicts with prior sequence"}
        if prior is None:
            state["parts"][sequence] = payload
            state["bytes"] += len(payload.encode("utf-8"))
        if state["bytes"] > MAX_TRANSFER_BYTES:
            del self.transfers[key]
            return HTTPStatus.REQUEST_ENTITY_TOO_LARGE, {"error": "transfer exceeds size limit"}

        received = len(state["parts"])
        if received != total:
            return HTTPStatus.ACCEPTED, {"received": received, "total": total}
        if set(state["parts"]) != set(range(total)):
            return HTTPStatus.CONFLICT, {"error": "transfer has missing sequence"}

        joined = "".join(state["parts"][index] for index in range(total))
        try:
            result = self._write(transfer_id, kind, joined)
        except (ValueError, UnicodeError, json.JSONDecodeError) as error:
            return HTTPStatus.UNPROCESSABLE_ENTITY, {"error": str(error)}
        del self.transfers[key]
        return HTTPStatus.CREATED, result

    def _write(self, transfer_id: str, kind: str, joined: str) -> dict[str, Any]:
        self.output_dir.mkdir(parents=True, exist_ok=True)
        if kind == "raw-json":
            # Require syntactically valid JSON but preserve the original UTF-8 bytes.
            json.loads(joined)
            target = self.output_dir / f"{transfer_id}.raw.json"
            content = joined.encode("utf-8")
        else:
            encoded = joined.split(",", 1)[1] if joined.startswith("data:image/") else joined
            content = base64.b64decode(encoded, validate=True)
            if not content.startswith(b"\x89PNG\r\n\x1a\n"):
                raise ValueError("screenshot is not a PNG byte stream")
            target = self.output_dir / f"{transfer_id}.screenshot.png"
        target.write_bytes(content)
        digest = hashlib.sha256(content).hexdigest()
        receipt = self.output_dir / f"{transfer_id}.{kind}.receipt.json"
        receipt.write_text(
            json.dumps(
                {
                    "transferId": transfer_id,
                    "kind": kind,
                    "path": target.name,
                    "bytes": len(content),
                    "sha256": digest,
                },
                ensure_ascii=False,
                indent=2,
            ) + "\n",
            encoding="utf-8",
        )
        return {"complete": True, "path": target.name, "bytes": len(content), "sha256": digest}


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=8765)
    parser.add_argument("--output-dir", type=Path, required=True)
    args = parser.parse_args()
    if args.host != "127.0.0.1":
        raise SystemExit("host must be 127.0.0.1")

    receiver = Receiver(args.output_dir)

    class Handler(BaseHTTPRequestHandler):
        def do_POST(self) -> None:  # noqa: N802
            if self.client_address[0] != "127.0.0.1" or self.path != "/figma-chunk":
                self.send_error(HTTPStatus.FORBIDDEN)
                return
            content_length = self.headers.get("Content-Length")
            if content_length is None or not content_length.isdecimal() or int(content_length) > MAX_BODY_BYTES:
                self.send_error(HTTPStatus.REQUEST_ENTITY_TOO_LARGE)
                return
            try:
                packet = json.loads(self.rfile.read(int(content_length)).decode("utf-8"))
                if not isinstance(packet, dict):
                    raise ValueError("JSON body must be an object")
                status, result = receiver.receive(packet)
            except (ValueError, UnicodeError, json.JSONDecodeError) as error:
                status, result = HTTPStatus.BAD_REQUEST, {"error": str(error)}
            encoded = (json.dumps(result, ensure_ascii=False) + "\n").encode("utf-8")
            self.send_response(status)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Content-Length", str(len(encoded)))
            self.end_headers()
            self.wfile.write(encoded)

        def log_message(self, _format: str, *_args: object) -> None:
            return

    server = ThreadingHTTPServer((args.host, args.port), Handler)
    print(f"LISTENING http://{args.host}:{args.port}/figma-chunk", flush=True)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()


if __name__ == "__main__":
    main()
