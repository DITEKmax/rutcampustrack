"""Download only Figma MCP SVG URLs already retained in a packet raw context."""

from __future__ import annotations

import hashlib
import json
import re
import subprocess
import sys
import tempfile
from pathlib import Path


# A Figma context can contain prose that demonstrates a placeholder URL. Only URLs
# declared as code constants (or a literal img src) are generated asset references.
ASSET_URL = re.compile(r'https://www\.figma\.com/api/mcp/asset/([0-9a-f-]{36})\.(svg|png)', re.IGNORECASE)
CODE_CONSTANT = re.compile(r'(?:const|let|var)\s+[A-Za-z_$][\w$]*\s*=\s*["\'](' + ASSET_URL.pattern + r')["\']', re.IGNORECASE)
LITERAL_SRC = re.compile(r'\bsrc\s*=\s*["\'](' + ASSET_URL.pattern + r')["\']', re.IGNORECASE)
MAX_ASSET_BYTES = 5 * 1024 * 1024


def main(raw_path: str, asset_dir: str, manifest_path: str) -> None:
    source = Path(raw_path)
    raw_files = sorted(source.glob("*.raw.json")) if source.is_dir() else [source]
    urls = set()
    for raw_file in raw_files:
        document = json.loads(raw_file.read_text(encoding="utf-8"))
        text = "\n".join(
            item.get("text", "") for item in document.get("content", []) if item.get("type") == "text"
        )
        urls.update(match.group(1) for match in CODE_CONSTANT.finditer(text))
        urls.update(match.group(1) for match in LITERAL_SRC.finditer(text))
    urls = sorted(urls)
    output = Path(asset_dir)
    output.mkdir(parents=True, exist_ok=True)
    records = []
    errors = []
    error_cache_path = output / "asset-download-errors.json"
    failed_urls = (
        json.loads(error_cache_path.read_text(encoding="utf-8"))
        if error_cache_path.exists()
        else {}
    )
    for url in urls:
        match = ASSET_URL.fullmatch(url)
        if match is None:
            raise ValueError("unexpected asset URL")
        asset_id, extension = match.groups()
        destination = output / f"figma-{asset_id}.{extension.lower()}"
        if url in failed_urls:
            errors.append({"sourceUrl": url, "error": failed_urls[url], "retry": "not-retried"})
            continue
        if destination.exists():
            payload = destination.read_bytes()
        else:
            with tempfile.NamedTemporaryFile(dir=output, delete=False, suffix=".download") as temporary:
                temporary_path = Path(temporary.name)
            try:
                subprocess.run(
                    ["curl.exe", "-sS", "-f", "-L", "--max-redirs", "5", "-o", str(temporary_path), url],
                    check=True,
                    timeout=30,
                )
                payload = temporary_path.read_bytes()
            except (subprocess.CalledProcessError, subprocess.TimeoutExpired) as error:
                failed_urls[url] = f"curl download failed: {type(error).__name__}"
                errors.append({"sourceUrl": url, "error": failed_urls[url], "retry": "stopped"})
                continue
            finally:
                temporary_path.unlink(missing_ok=True)
        content_type = "image/svg+xml" if extension.lower() == "svg" else "image/png"
        if len(payload) > MAX_ASSET_BYTES:
            raise ValueError(f"asset {asset_id} exceeds size limit")
        if extension.lower() == "svg":
            try:
                decoded = payload.decode("utf-8")
            except UnicodeDecodeError as error:
                raise ValueError(f"asset {asset_id} is not UTF-8 SVG") from error
            if "<svg" not in decoded[:4096].lower():
                prefix = decoded[:120].replace("\r", " ").replace("\n", " ")
                raise ValueError(
                    f"asset {asset_id} does not contain SVG root; content-type={content_type}; prefix={prefix!r}"
                )
        elif not payload.startswith(b"\x89PNG\r\n\x1a\n"):
            raise ValueError(f"asset {asset_id} is not a PNG byte stream")
        destination.write_bytes(payload)
        records.append(
            {
                "sourceUrl": url,
                "path": destination.name,
                "mimeType": content_type,
                "bytes": len(payload),
                "sha256": hashlib.sha256(payload).hexdigest(),
                "validated": "xml" if extension.lower() == "svg" else "png-signature",
            }
        )
    Path(manifest_path).write_text(
        json.dumps(
            {"schema": "rutcampustrack.figma-assets.v1", "assets": records, "errors": errors},
            ensure_ascii=False,
            indent=2,
        )
        + "\n",
        encoding="utf-8",
    )
    error_cache_path.write_text(json.dumps(failed_urls, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"downloaded": len(records), "errors": len(errors)}, ensure_ascii=False))


if __name__ == "__main__":
    if len(sys.argv) != 4:
        raise SystemExit("usage: download_figma_assets.py RAW_CONTEXT ASSET_DIR ASSET_MANIFEST")
    main(*sys.argv[1:])
