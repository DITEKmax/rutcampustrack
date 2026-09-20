"""Integrity check for a stable local JS-STUDENT-01 design packet."""

from __future__ import annotations

import hashlib
import json
from pathlib import Path


PACKET = Path("docs/design/packets/JS-STUDENT-01")


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main() -> None:
    parsed_json = 0
    for path in PACKET.rglob("*.json"):
        json.loads(path.read_text(encoding="utf-8"))
        parsed_json += 1

    manifest = json.loads((PACKET / "manifest.json").read_text(encoding="utf-8"))
    for category in ("context", "screenshots"):
        for artifact in manifest["artifacts"][category]:
            path = PACKET / artifact["path"]
            assert path.is_file(), path
            assert path.stat().st_size == artifact["bytes"], path
            assert sha256(path) == artifact["sha256"], path

    source_urls: set[str] = set()
    for per_call in sorted((PACKET / "assets").glob("assets-r*.json")):
        document = json.loads(per_call.read_text(encoding="utf-8"))
        for asset in document["assets"]:
            path = PACKET / "assets" / asset["path"]
            assert path.is_file(), path
            assert path.stat().st_size == asset["bytes"], path
            assert sha256(path) == asset["sha256"], path
            source_urls.add(asset["sourceUrl"])

    index = json.loads((PACKET / "assets" / "asset-index.json").read_text(encoding="utf-8"))
    assert len(index["uniqueAssets"]) == 16
    assert sum(len(asset["sourceUrls"]) for asset in index["uniqueAssets"]) == 58
    assert {url for asset in index["uniqueAssets"] for url in asset["sourceUrls"]} == source_urls
    assert len(source_urls) == 58

    calls = json.loads((PACKET / "call-log.json").read_text(encoding="utf-8"))["calls"]
    assert [call["id"] for call in calls] == [f"R{i:03d}" for i in range(1, 12)]
    assert sum(call["localRequestTimingProxy"] is not None for call in calls) == 9
    assert all(call["figmaCapturedAt"] is None for call in calls)
    assert manifest["coverage"]["capturedFigmaStates"] == ["default", "geo-unconfirmed", "attendance-confirmed", "absent-actions", "reason-form", "pending-request"]
    assert manifest["coverage"]["plannedImplementationStates"] == ["loading", "error", "offline", "responsive", "PWA-host", "TMA-host"]

    for path in ("gaps.md", "decisions.md", "source-links.md", "token-mapping.json", "assets/asset-download-diagnostics.json"):
        assert (PACKET / path).is_file(), path
    for path, expected in manifest["documentHashes"].items():
        assert sha256(PACKET / path) == expected["sha256"], path
        assert (PACKET / path).stat().st_size == expected["bytes"], path
    canonical_tokens = Path("docs/design/tokens-v2.json")
    assert sha256(canonical_tokens) == manifest["externalEvidenceHashes"]["docs/design/tokens-v2.json"]
    mapping = json.loads((PACKET / "token-mapping.json").read_text(encoding="utf-8"))
    extracted = {reference for references in mapping["extraction"]["referencesByCall"].values() for reference in references}
    assert mapping["extraction"]["actualReferenceCount"] == 34
    assert len(extracted) == 34
    assert {record["liveReference"].removeprefix("--") for record in mapping["mapping"]} == extracted
    assert "radius/2xl" in extracted
    assert "radius" not in extracted
    radius = next(record for record in mapping["mapping"] if record["liveReference"] == "--radius/2xl")
    assert radius["canonical"]["cssVariable"] == "--radius-2xl"
    assert radius["status"] == "exact-under-rem-base-16"
    print(json.dumps({"status": "PASS", "jsonFilesParsed": parsed_json, "contexts": 10, "screenshots": 6, "assetSourceUrls": len(source_urls), "canonicalAssets": 16, "timingProxies": 9, "tokenReferences": len(extracted)}, ensure_ascii=False))


if __name__ == "__main__":
    main()
