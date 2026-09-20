"""Extract embedded PNG screenshot bytes from a saved Figma MCP raw response."""

from __future__ import annotations

import base64
import hashlib
import json
import sys
from pathlib import Path


def main(raw_path: str, output_path: str) -> None:
    response = json.loads(Path(raw_path).read_text(encoding="utf-8"))
    images = [item for item in response.get("content", []) if item.get("type") == "image"]
    if len(images) != 1:
        raise ValueError(f"expected one embedded image, got {len(images)}")
    image = images[0]
    if image.get("mimeType") != "image/png" or not isinstance(image.get("data"), str):
        raise ValueError("expected PNG image content")
    payload = base64.b64decode(image["data"], validate=True)
    if not payload.startswith(b"\x89PNG\r\n\x1a\n"):
        raise ValueError("embedded image does not have PNG signature")
    destination = Path(output_path)
    destination.write_bytes(payload)
    print(json.dumps({"path": destination.name, "bytes": len(payload), "sha256": hashlib.sha256(payload).hexdigest()}))


if __name__ == "__main__":
    if len(sys.argv) != 3:
        raise SystemExit("usage: extract_figma_images.py RAW_CONTEXT OUTPUT_PNG")
    main(*sys.argv[1:])
