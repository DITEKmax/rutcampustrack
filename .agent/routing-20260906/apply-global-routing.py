"""Apply reviewed global routing: python apply-global-routing.py --apply."""
from __future__ import annotations

import argparse
import copy
import hashlib
import os
import re
import shutil
import tempfile
import tomllib
from pathlib import Path

ROOT = Path(r"C:\Users\maksd\.codex")
STAGE = Path(__file__).parent / "global-stage"
BACKUP = ROOT / "backups" / "routing-20260906"
ROLES = ("explorer", "developer", "reviewer")
EXPECTED = {
    ROOT / "config.toml": "76B1EFB0D5136F1F1E00A31BA82FEF88205179B0F5CF751C5A1A3AB20E5DEC02",
    ROOT / "AGENTS.md": "E3B0C44298FC1C149AFBF4C8996FB92427AE41E4649B934CA495991B7852B855",
    ROOT / "agents" / "explorer.toml": None,
    ROOT / "agents" / "developer.toml": None,
    ROOT / "agents" / "reviewer.toml": None,
}
STAGE_EXPECTED = {
    STAGE / "AGENTS.md": "F1099BC84FDB03831C85A5BA30A0C3B80B4ACF585CF2DD4FED9684F7B7E1FD17",
    STAGE / "agents" / "explorer.toml": "DEEBA6EB84B9AAC37A54E254DAB62DAE1F43C737BE2499F14CE9770EC3830173",
    STAGE / "agents" / "developer.toml": "12D59489A557B49852174ACEA12071C4CBED49943547193F077226B08393125C",
    STAGE / "agents" / "reviewer.toml": "6CAD88ABC625E96626283AC34F54600F7066FBFAB8556CA415E9D194B3CAB249",
}
ROUTING_VALUES = {
    "model": "gpt-6-astra", "model_reasoning_effort": "medium",
    "agents.enabled": True, "agents.max_concurrent_threads_per_session": 3,
    "agents.default_subagent_model": "gpt-5.6-luna",
    "agents.default_subagent_reasoning_effort": "high",
}


def digest(path: Path) -> str | None:
    return hashlib.sha256(path.read_bytes()).hexdigest().upper() if path.exists() else None


def is_reparse(path: Path) -> bool:
    if path.is_symlink():
        return True
    try:
        attributes = path.stat(follow_symlinks=False).st_file_attributes
    except (AttributeError, FileNotFoundError):
        return False
    return bool(attributes & 0x400)  # Windows FILE_ATTRIBUTE_REPARSE_POINT


def require_safe(path: Path, root: Path) -> None:
    if path != root and root not in path.parents:
        raise SystemExit(f"Refusing target outside root: {path}")
    current = path
    while True:
        if current.exists() or current.is_symlink():
            if is_reparse(current):
                raise SystemExit(f"Refusing symlink/reparse path: {current}")
        if current == root:
            return
        current = current.parent


def require_hashes(items: dict[Path, str | None], root: Path) -> None:
    for path, expected in items.items():
        require_safe(path, root)
        if digest(path) != expected:
            raise SystemExit(f"Refusing changed target: {path}")


def staged_payloads() -> dict[Path, bytes]:
    require_hashes(STAGE_EXPECTED, STAGE)
    payloads = {ROOT / "AGENTS.md": (STAGE / "AGENTS.md").read_bytes()}
    if not payloads[ROOT / "AGENTS.md"].strip():
        raise SystemExit("Invalid staged AGENTS.md")
    for role in ROLES:
        source = STAGE / "agents" / f"{role}.toml"
        try:
            parsed = tomllib.loads(source.read_text(encoding="utf-8"))
        except tomllib.TOMLDecodeError as error:
            raise SystemExit(f"Invalid staged role TOML: {source}: {error}") from error
        fields = ("description", "developer_instructions")
        if parsed.get("name") != role or not all(isinstance(parsed.get(key), str) and parsed[key].strip() for key in fields):
            raise SystemExit(f"Invalid staged role fields: {source}")
        payloads[ROOT / "agents" / f"{role}.toml"] = source.read_bytes()
    return payloads


def set_toml_key(lines: list[str], section: str | None, key: str, value: str, newline: str) -> list[str]:
    header = re.compile(r"^\s*\[([^]]+)]\s*(?:#.*)?$")
    target = f"[{section}]" if section else None
    start, end = 0, len(lines)
    if target:
        for index, line in enumerate(lines):
            if line.strip() == target:
                start = index + 1
                break
        else:
            return lines + ([newline] if lines and lines[-1].strip() else []) + [target + newline, f"{key} = {value}{newline}"]
        for index in range(start, len(lines)):
            if header.match(lines[index]):
                end = index
                break
    else:
        for index, line in enumerate(lines):
            if header.match(line):
                end = index
                break
    matcher = re.compile(rf"^(\s*){re.escape(key)}\s*=")
    for index in range(start, end):
        if matcher.match(lines[index]):
            lines[index] = f"{key} = {value}{newline}"
            return lines
    return lines[:end] + [f"{key} = {value}{newline}"] + lines[end:]


def without_routing_values(data: dict[str, object]) -> dict[str, object]:
    clean = copy.deepcopy(data)
    clean.pop("model", None)
    clean.pop("model_reasoning_effort", None)
    agents = clean.get("agents")
    if isinstance(agents, dict):
        for key in ("enabled", "max_concurrent_threads_per_session", "default_subagent_model", "default_subagent_reasoning_effort"):
            agents.pop(key, None)
        if not agents:
            clean.pop("agents")
    return clean


def validate_config(before: dict[str, object], after: dict[str, object]) -> None:
    if without_routing_values(before) != without_routing_values(after):
        raise SystemExit("Refusing semantic change outside six routing values")
    agents = after.get("agents", {})
    actual = {"model": after.get("model"), "model_reasoning_effort": after.get("model_reasoning_effort"), **{f"agents.{key}": agents.get(key) for key in ("enabled", "max_concurrent_threads_per_session", "default_subagent_model", "default_subagent_reasoning_effort")}}
    if actual != ROUTING_VALUES:
        raise SystemExit("Generated config does not contain expected routing values")


def rendered_config() -> tuple[bytes, dict[str, object]]:
    text = (ROOT / "config.toml").read_bytes().decode("utf-8")
    before = tomllib.loads(text)
    newline = "\r\n" if "\r\n" in text else "\n"
    lines = text.splitlines(keepends=True)
    for section, key, value in ((None, "model", '"gpt-6-astra"'), (None, "model_reasoning_effort", '"medium"'), ("agents", "enabled", "true"), ("agents", "max_concurrent_threads_per_session", "3"), ("agents", "default_subagent_model", '"gpt-5.6-luna"'), ("agents", "default_subagent_reasoning_effort", '"high"')):
        lines = set_toml_key(lines, section, key, value, newline)
    rendered = "".join(lines)
    validate_config(before, tomllib.loads(rendered))
    return rendered.encode("utf-8"), before


def atomic_replace(path: Path, payload: bytes) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    handle, temporary = tempfile.mkstemp(prefix=f".{path.name}.", dir=path.parent)
    try:
        with os.fdopen(handle, "wb") as output:
            output.write(payload)
        os.replace(temporary, path)
    except BaseException:
        Path(temporary).unlink(missing_ok=True)
        raise


def backup_originals(originals: dict[Path, bytes | None]) -> None:
    BACKUP.mkdir(parents=True)
    for target, payload in originals.items():
        if payload is not None:
            destination = BACKUP / target.relative_to(ROOT)
            destination.parent.mkdir(parents=True, exist_ok=True)
            destination.write_bytes(payload)


def apply() -> None:
    require_hashes(EXPECTED, ROOT)
    require_safe(BACKUP, ROOT)
    if BACKUP.exists() or BACKUP.is_symlink():
        raise SystemExit(f"Refusing preexisting backup: {BACKUP}")
    payloads = staged_payloads()  # All staged inputs are frozen, parsed before writes.
    config_payload, before = rendered_config()  # TOML parses and semantic check before writes.
    payloads[ROOT / "config.toml"] = config_payload
    for target in payloads:
        require_safe(target, ROOT)
    originals = {target: target.read_bytes() if target.exists() else None for target in payloads}
    backup_originals(originals)
    changed: list[Path] = []
    try:
        for target, payload in payloads.items():
            atomic_replace(target, payload)
            changed.append(target)
        readback = tomllib.loads((ROOT / "config.toml").read_bytes().decode("utf-8"))
        validate_config(before, readback)
        for target, payload in payloads.items():
            if target != ROOT / "config.toml" and target.read_bytes() != payload:
                raise RuntimeError(f"Readback mismatch: {target}")
    except BaseException:
        for target in reversed(changed):
            original = originals[target]
            if original is None:
                target.unlink(missing_ok=True)
            else:
                atomic_replace(target, original)
        raise


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--apply", action="store_true")
    if not parser.parse_args().apply:
        raise SystemExit("Staged only. Re-run with --apply after review.")
    apply()
