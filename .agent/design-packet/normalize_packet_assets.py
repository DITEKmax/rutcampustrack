"""Deduplicate already-captured packet assets by content hash without losing URL provenance."""

from __future__ import annotations

import hashlib
import json
from collections import defaultdict
from pathlib import Path


def main() -> None:
    asset_dir = Path("docs/design/packets/JS-STUDENT-01/assets")
    grouped: dict[str, list[dict]] = defaultdict(list)
    source_files = sorted(asset_dir.glob("assets-r*.json"))

    for source_file in source_files:
        document = json.loads(source_file.read_text(encoding="utf-8"))
        for asset in document.get("assets", []):
            original = asset_dir / asset["path"]
            payload = original.read_bytes()
            actual_hash = hashlib.sha256(payload).hexdigest()
            if actual_hash != asset["sha256"]:
                raise ValueError(f"hash mismatch: {original}")
            if len(payload) != asset["bytes"]:
                raise ValueError(f"size mismatch: {original}")
            canonical = Path("sha256") / f"{actual_hash}{original.suffix.lower()}"
            canonical_path = asset_dir / canonical
            canonical_path.parent.mkdir(exist_ok=True)
            if canonical_path.exists() and canonical_path.read_bytes() != payload:
                raise ValueError(f"canonical collision: {canonical_path}")
            canonical_path.write_bytes(payload)
            asset["path"] = canonical.as_posix()
            asset["canonical"] = True
            asset["sourceCall"] = source_file.stem.removeprefix("assets-").upper()
            grouped[actual_hash].append(asset)
        source_file.write_text(json.dumps(document, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    # These were all created during this packet. Remove a UUID-named duplicate only
    # after every referenced byte has been written to its hash-addressed canonical path.
    for original in asset_dir.glob("figma-*.*"):
        original.unlink()

    index = {
        "schema": "rutcampustrack.figma-asset-index.v1",
        "deduplication": "one local canonical byte source per SHA-256; each source URL remains in its per-call manifest",
        "uniqueAssets": [
            {
                "sha256": digest,
                "path": records[0]["path"],
                "mimeType": records[0]["mimeType"],
                "bytes": records[0]["bytes"],
                "validated": records[0].get("validated", "validated-during-download; legacy per-call field absent"),
                "sourceUrls": sorted(record["sourceUrl"] for record in records),
                "sourceCalls": sorted({record["sourceCall"] for record in records}),
            }
            for digest, records in sorted(grouped.items())
        ],
    }
    (asset_dir / "asset-index.json").write_text(
        json.dumps(index, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )
    print(json.dumps({"sourceFiles": len(source_files), "uniqueAssets": len(index["uniqueAssets"]), "sourceUrls": sum(len(v) for v in grouped.values())}))


if __name__ == "__main__":
    main()
