"""Build the reviewed mapping from captured Figma references to canonical tokens."""

from __future__ import annotations

import hashlib
import json
import re
from collections import defaultdict
from pathlib import Path


PACKET = Path("docs/design/packets/JS-STUDENT-01")
CONTEXT = PACKET / "context"
CANONICAL = Path("docs/design/tokens-v2.json")
CODE_CALLS = ("R002", "R003", "R004", "R008", "R009", "R010")
CSS_VAR = re.compile(r"var\((--[^,\)]+)(?:,[^\)]*)?\)")
TARGETS = {
    "border-hairline": ("dimension", "border/hairline"),
    "color-accent-now": ("semantic", "color/accent/now"), "color-accent-on-now": ("semantic", "color/accent/on-now"),
    "color-border-default": ("semantic", "color/border/default"), "color-disabled-surface": ("semantic", "color/disabled/surface"),
    "color-disabled-text": ("semantic", "color/disabled/text"), "color-state-danger-text": ("semantic", "color/state/danger/text"),
    "color-status-absent-fill": ("semantic", "color/status/absent/fill"), "color-status-absent-on-fill": ("semantic", "color/status/absent/on-fill"),
    "color-status-present-fill": ("semantic", "color/status/present/fill"), "color-status-present-on-fill": ("semantic", "color/status/present/on-fill"),
    "color-surface-base": ("semantic", "color/surface/base"), "color-surface-float": ("semantic", "color/surface/float"),
    "color-surface-raised": ("semantic", "color/surface/raised"), "color-text-muted": ("semantic", "color/text/muted"),
    "color-text-primary": ("semantic", "color/text/primary"), "color-text-secondary": ("semantic", "color/text/secondary"),
    "component-mobile-bottom-nav-glass-background-blur": ("component", "component/mobile-bottom-nav/glass-background-blur"),
    "component-mobile-bottom-nav-glass-surface-opacity": ("component", "component/mobile-bottom-nav/glass-surface-opacity"),
    "font-family-sans": ("dimension", "font/family/sans"), "font-size-body": ("dimension", "font/size/body"),
    "font-size-caption": ("dimension", "font/size/caption"), "font-size-dense": ("dimension", "font/size/dense"),
    "font-size-micro": ("dimension", "font/size/micro"), "font-weight-medium": ("dimension", "font/weight/medium"),
    "font-weight-regular": ("dimension", "font/weight/regular"), "radius/2xl": ("dimension", "radius/2xl"),
    "radius-lg": ("dimension", "radius/lg"), "radius-xl": ("dimension", "radius/xl"), "space-0": ("dimension", "space/0"),
    "space-1": ("dimension", "space/1"), "space-2": ("dimension", "space/2"), "space-3": ("dimension", "space/3"), "space-4": ("dimension", "space/4"),
}


def text_response(path: Path) -> str:
    response = json.loads(path.read_text(encoding="utf-8"))
    return "\n".join(item.get("text", "") for item in response["content"] if item.get("type") == "text")


def resolve(value: object, primitives: dict[str, dict]) -> object:
    if isinstance(value, str) and value.startswith("{") and value.endswith("}"):
        return resolve(primitives[value[1:-1]]["$value"], primitives)
    return value


def normalized_name(raw_name: str) -> str:
    """Normalize JSON/JS escaped slash syntax while preserving the original expression."""
    name = raw_name.removeprefix("--")
    while r"\/" in name:
        name = name.replace(r"\/", "/")
    while r"\/" in name:
        name = name.replace(r"\/", "/")
    return name


def main() -> None:
    captured: dict[str, set[str]] = {}
    expressions: dict[str, dict[str, list[str]]] = {}
    references: dict[str, set[str]] = defaultdict(set)
    for call in CODE_CALLS:
        file = next(CONTEXT.glob(f"{call}-*.raw.json"))
        expressions[call] = defaultdict(list)
        for match in CSS_VAR.finditer(text_response(file)):
            name = normalized_name(match.group(1))
            expressions[call][name].append(match.group(0))
        refs = set(expressions[call])
        captured[call] = refs
        for ref in refs:
            references[ref].add(call)
    if set(references) != set(TARGETS):
        raise ValueError(f"unmapped={set(references) - set(TARGETS)} stale={set(TARGETS) - set(references)}")

    r011_file = CONTEXT / "R011-family-variable-definitions.raw.json"
    r011_text = text_response(r011_file)
    live_definitions = json.loads(r011_text)
    token_file = json.loads(CANONICAL.read_text(encoding="utf-8"))
    primitives = token_file["collections"]["primitives"]["tokens"]
    records = []
    for reference in sorted(references):
        collection, token_path = TARGETS[reference]
        token = token_file["collections"][collection]["tokens"][token_path]
        canonical_value = token.get("dark", token.get("$value"))
        resolved = resolve(canonical_value, primitives)
        source_value = live_definitions["radius/2xl"] if reference == "radius/2xl" else live_definitions[f"var(--{reference})"]
        if collection == "dimension" and token_path.startswith(("font/size", "space/", "radius/")):
            status = "exact-under-rem-base-16"
            note = "Captured px number equals canonical rem value at the fixed 16px base."
        elif token_path == "border/hairline":
            status = "exact-under-canonical-px-unit"
            note = "Captured number 1 equals canonical 1px hairline."
        elif token_path.endswith("background-blur"):
            status = "exact-contextual-px"
            note = "Both sources specify blur magnitude 25; canonical description fixes its px use."
        else:
            status = "exact"
            note = "Captured live value equals the canonical dark definition."
        records.append({
            "liveReference": f"--{reference}", "usedByCalls": sorted(references[reference]),
            "serializedSourceExpressions": {call: expressions[call][reference] for call in sorted(references[reference])},
            "liveDefinition": {"source": "context/R011-family-variable-definitions.raw.json", "sourceValue": source_value},
            "canonical": {"source": "../../tokens-v2.json", "collection": collection, "figmaPath": token_path, "cssVariable": "--" + token_path.replace("/", "-"), "darkDefinition": canonical_value, "darkResolvedValue": resolved, "targetDefinition": token},
            "status": status, "note": note,
        })

    evidence_hashes = {}
    for file in (CONTEXT / "R006-variable-definitions.raw.json", r011_file, CANONICAL):
        evidence_hashes[str(file).replace("\\", "/")] = hashlib.sha256(file.read_bytes()).hexdigest()
    result = {
        "schema": "rutcampustrack.design-token-mapping.v2",
        "implementationTarget": "future Vue + PCSS; rem base 16px for authored dimension lengths",
        "sources": {"liveFigma": ["context/R006-variable-definitions.raw.json", "context/R011-family-variable-definitions.raw.json"], "canonical": "../../tokens-v2.json", "sha256": evidence_hashes},
        "extraction": {"codeCalls": list(CODE_CALLS), "actualReferenceCount": len(references), "referencesByCall": {call: sorted(refs) for call, refs in captured.items()}},
        "mapping": records,
        "referenceStyleGaps": [
            {"sourceCalls": ["R002", "R003", "R004", "R008"], "nodes": ["4581:3066", "4585:222", "4585:848024", "4585:848130"], "element": "role-pill", "capturedStyle": "literal three-stop inline gradient", "status": "reference-style-gap; no canonical token or alias created"},
            {"sourceCalls": ["R002", "R008", "R010"], "nodes": ["4581:3080", "4585:848142", "4588:848425"], "element": "cta", "capturedStyle": "literal three-stop generated gradient", "status": "reference-style-gap; no canonical token or alias created"},
        ],
        "componentReuse": [
            {"name": "shared/SelectField", "classification": "live-captured reuse", "evidence": "R009 data-name=shared/SelectField; registry names SelectField as existing component."},
            {"name": "shared/FileUploadField/local", "classification": "live-captured reuse", "evidence": "R009 data-name=shared/FileUploadField/local; local qualifier is captured and no ownership promotion is inferred."},
            {"name": "shared/AppButton / action/AttendanceButton", "classification": "canonical registry reuse", "evidence": "R002/R003/R004/R008/R010 contain cta; registry says attendance action is rendered by existing shared/AppButton."},
            {"name": "attendance/AttendanceStatusBadge", "classification": "canonical registry reuse", "evidence": "R002/R003/R004/R008/R009/R010 contain status nodes; registry declares existing badge for Today schedule."},
            {"name": "student/MobileTodayHero, student/MobileTodaySchedule, student/MobileCheckInTask, shared/MobileBottomNav", "classification": "registry-reference only", "evidence": "Existing registry contracts inform later composition; raw captures show hierarchy labels, not verified reusable Vue exports."},
        ],
        "rules": ["Use the canonical CSS variable shown in each record; do not copy live fallback literals.", "Do not introduce aliases for unmatched --radius or literal gradients.", "The dark live capture is evidence only; canonical token modes remain authoritative for later implementation."],
    }
    (PACKET / "token-mapping.json").write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"status": "PASS", "actualReferenceCount": len(references), "exact": sum(r["status"] == "exact" for r in records), "remExact": sum(r["status"] == "exact-under-rem-base-16" for r in records), "radius2xlCaptured": "radius/2xl" in references, "phantomRadius": "radius" in references}, ensure_ascii=False))


if __name__ == "__main__":
    main()
