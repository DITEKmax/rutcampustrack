"""Collect privacy-safe JUnit identities/outcomes; never copy XML text or display names."""

import argparse
import hashlib
import json
import os
import re
import subprocess
from pathlib import Path
from xml.etree import ElementTree

MODULES = {
    "api-gateway": "services/api-gateway",
    "auth-service": "services/auth-service/auth-app",
    "academic-service": "services/academic-service/academic-app",
    "schedule-service": "services/schedule-service/schedule-app",
    "attendance-service": "services/attendance-service/attendance-app",
    "notification-service": "services/notification-service/notification-app",
}
TEST_ANNOTATIONS = {"Test", "ParameterizedTest", "RepeatedTest", "TestFactory", "TestTemplate"}
IDENTIFIER = re.compile(r"[A-Za-z_$][A-Za-z0-9_$]*\Z")
MAX_XML_BYTES = 32 * 1024 * 1024
METADATA_ENV = (
    "RCT_CI_PR_HEAD_SHA",
    "RCT_CI_RUN_ID",
    "RCT_CI_RUN_ATTEMPT",
    "RCT_CI_EVENT_NAME",
    "RCT_CI_TEST_OUTCOME",
    "RCT_CI_JOB_ID",
)


def git(root: Path, *arguments: str) -> str:
    result = subprocess.run(["git", *arguments], cwd=root, capture_output=True, text=True, check=False)
    if result.returncode:
        raise ValueError("Checkout metadata unavailable")
    return result.stdout.strip()


def java_test_declarations(source: str) -> dict[str, set[str]]:
    # Mask comments, strings, chars and text blocks before reading declaration tokens.
    masked = re.sub(
        r'"""[\s\S]*?"""|"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'|//[^\n]*|/\*[\s\S]*?\*/',
        " ",
        source,
    )
    tokens = re.findall(r"[A-Za-z_$][A-Za-z0-9_$]*|[{}()@.;]", masked)
    package = re.search(r"\bpackage\s+([A-Za-z_$][A-Za-z0-9_$.]*)\s*;", masked)
    prefix = package.group(1) + "." if package else ""
    classes: list[str] = []
    braces: list[str | None] = []
    result: dict[str, set[str]] = {}
    pending_class = None
    pending_test = False
    index = 0
    while index < len(tokens):
        token = tokens[index]
        if token == "@":
            index += 1
            annotation = []
            if index < len(tokens) and IDENTIFIER.fullmatch(tokens[index]):
                annotation.append(tokens[index])
                index += 1
            while index + 1 < len(tokens) and tokens[index] == "." and IDENTIFIER.fullmatch(tokens[index + 1]):
                annotation.extend(tokens[index : index + 2])
                index += 2
            pending_test = pending_test or bool(annotation and annotation[-1] in TEST_ANNOTATIONS)
            if index < len(tokens) and tokens[index] == "(":
                depth = 1
                index += 1
                while index < len(tokens) and depth:
                    depth += (tokens[index] == "(") - (tokens[index] == ")")
                    index += 1
            continue
        if (
            token in {"class", "interface", "record", "enum"}
            and index + 1 < len(tokens)
            and IDENTIFIER.fullmatch(tokens[index + 1])
            and (index == 0 or tokens[index - 1] != ".")
        ):
            pending_class = tokens[index + 1]
            pending_test = False
        elif token == "{":
            braces.append(pending_class)
            if pending_class:
                classes.append(pending_class)
                pending_class = None
        elif token == "}":
            if braces and braces.pop():
                classes.pop()
            pending_test = False
        elif token == "(" and pending_test and classes and index:
            method = tokens[index - 1]
            if IDENTIFIER.fullmatch(method):
                result.setdefault(prefix + "$".join(classes), set()).add(method)
            pending_test = False
        index += 1
    return result


def unlinked_scoped_path(root: Path, path: Path, scope: Path) -> bool:
    # Reject links before globbing, reading or hashing; resolved-in-repo alone
    # would still permit a link into another task or source directory.
    try:
        relative = path.relative_to(root)
        path.relative_to(scope)
        if any(part in {".", ".."} for part in relative.parts):
            return False
        current = root
        for part in relative.parts:
            current = current / part
            if current.is_symlink():
                return False
        return path.resolve() == root.resolve() / relative
    except (OSError, ValueError):
        return False


def source_registry(root: Path, module: str) -> dict[str, dict]:
    registry = {}
    paths = git(root, "ls-files", "-z", "--", f"{module}/src/test/java").split("\0")
    for relative in paths:
        if not relative.endswith(".java"):
            continue
        path = root / relative
        if not unlinked_scoped_path(root, path, root / module / "src/test/java"):
            continue
        data = path.read_bytes()
        for classname, methods in java_test_declarations(data.decode("utf8")).items():
            registry[classname] = {"methods": methods, "source": relative, "sha256": hashlib.sha256(data).hexdigest()}
    return registry


def metadata(root: Path, service: str, task: str, environment: dict[str, str]) -> dict:
    checkout = git(root, "rev-parse", "HEAD")
    head = environment.get("RCT_CI_PR_HEAD_SHA", checkout)
    if not all(re.fullmatch(r"[0-9a-f]{40}", value) for value in (checkout, head)):
        raise ValueError("Invalid revision metadata")
    expected_job = "java-unit-test" if task == "test" else "java-integration-test"
    job = environment.get("RCT_CI_JOB_ID", expected_job)
    if job != expected_job:
        raise ValueError("Invalid job scope")
    result = {
        "checkoutSha": checkout,
        "prHeadSha": head,
        "service": service,
        "task": task,
        "job": job,
    }
    for variable, field in (("RCT_CI_RUN_ID", "runId"), ("RCT_CI_RUN_ATTEMPT", "runAttempt")):
        value = environment.get(variable)
        if value is not None and not re.fullmatch(r"[0-9]{1,20}", value):
            raise ValueError("Invalid run metadata")
        result[field] = value
    event = environment.get("RCT_CI_EVENT_NAME", "LOCAL_CONTROL")
    outcome = environment.get("RCT_CI_TEST_OUTCOME", "UNKNOWN")
    if event not in {"push", "pull_request", "LOCAL_CONTROL"} or outcome not in {
        "success",
        "failure",
        "cancelled",
        "skipped",
        "UNKNOWN",
    }:
        raise ValueError("Invalid execution metadata")
    return {**result, "event": event, "testStepOutcome": outcome}


def number(value: str | None) -> int | None:
    return int(value) if value is not None and re.fullmatch(r"[0-9]{1,5}", value) else None


def collect(root: Path, service: str, task: str, environment: dict[str, str]) -> dict:
    if service not in MODULES or task not in {"test", "integrationTest"}:
        raise ValueError("Invalid outcome scope")
    module = MODULES[service]
    registry = source_registry(root, module)
    report = {
        "schemaVersion": 1,
        "metadata": metadata(root, service, task, environment),
        "collection": "NO_RESULTS",
        "acceptance": "NOT_ASSESSED",
        "xml": [],
        "cases": [],
    }
    directory = root / module / "build/test-results" / task
    directory_safe = unlinked_scoped_path(root, directory, directory)
    incomplete = not directory_safe
    paths = sorted(directory.glob("TEST-*.xml")) if directory_safe else []
    for xml_ordinal, path in enumerate(paths, 1):
        if not unlinked_scoped_path(root, path, directory):
            report["xml"].append({"ordinal": xml_ordinal, "sha256": None, "suites": [], "state": "UNINSPECTABLE_XML"})
            incomplete = True
            continue
        data = path.read_bytes()
        entry = {"ordinal": xml_ordinal, "sha256": hashlib.sha256(data).hexdigest(), "suites": []}
        report["xml"].append(entry)
        if len(data) > MAX_XML_BYTES or b"<!DOCTYPE" in data or b"<!ENTITY" in data:
            entry["state"] = "UNINSPECTABLE_XML"
            incomplete = True
            continue
        try:
            document = ElementTree.fromstring(data)
        except ElementTree.ParseError:
            entry["state"] = "MALFORMED_XML"
            incomplete = True
            continue
        suites = [document] if document.tag == "testsuite" else list(document.iter("testsuite"))
        entry["state"] = "PARSED"
        for suite_ordinal, suite in enumerate(suites, 1):
            declared = {key: number(suite.get(key)) for key in ("tests", "failures", "errors", "skipped")}
            nodes = list(suite.findall("testcase"))
            counts = {key: 0 for key in ("PASSED", "FAILED", "ERROR", "SKIPPED", "NOT_EXECUTED")}
            for ordinal, case in enumerate(nodes, 1):
                tags = {child.tag for child in case}
                status = (
                    "ERROR"
                    if "error" in tags
                    else "FAILED"
                    if "failure" in tags
                    else "SKIPPED"
                    if "skipped" in tags
                    else "NOT_EXECUTED"
                    if case.get("status") in {"notrun", "notexecuted", "disabled"}
                    else "PASSED"
                )
                counts[status] += 1
                raw_class = case.get("classname", "")
                known = registry.get(raw_class)
                candidate = case.get("name", "").split("(", 1)[0]
                method = candidate if known and candidate in known["methods"] else None
                safe = {
                    "xmlOrdinal": xml_ordinal,
                    "suiteOrdinal": suite_ordinal,
                    "ordinal": ordinal,
                    "classname": raw_class if known else None,
                    "method": method,
                    "status": status,
                    "identity": "SOURCE_METHOD" if method else "ORDINAL_ONLY",
                }
                if known:
                    safe.update(source=known["source"], sourceSha256=known["sha256"])
                report["cases"].append(safe)
            missing = max(0, (declared["tests"] or 0) - len(nodes))
            counts["NOT_EXECUTED"] += missing
            for ordinal in range(len(nodes) + 1, len(nodes) + missing + 1):
                report["cases"].append(
                    {
                        "xmlOrdinal": xml_ordinal,
                        "suiteOrdinal": suite_ordinal,
                        "ordinal": ordinal,
                        "classname": None,
                        "method": None,
                        "status": "NOT_EXECUTED",
                        "identity": "ORDINAL_ONLY",
                    }
                )
            consistent = (
                declared["tests"] == len(nodes)
                and declared["failures"] == counts["FAILED"]
                and declared["errors"] == counts["ERROR"]
                and declared["skipped"] == counts["SKIPPED"]
            )
            incomplete = incomplete or not consistent or bool(counts["NOT_EXECUTED"])
            entry["suites"].append(
                {"ordinal": suite_ordinal, "declared": declared, "observed": counts, "countsConsistent": consistent}
            )
    report["summary"] = {
        status: sum(case["status"] == status for case in report["cases"])
        for status in ("PASSED", "FAILED", "ERROR", "SKIPPED", "NOT_EXECUTED")
    }
    if report["cases"]:
        report["collection"] = "INCOMPLETE" if incomplete else "RESULTS"
    elif incomplete:
        report["collection"] = "UNINSPECTABLE_RESULTS"
    return report


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--service", required=True, choices=MODULES)
    parser.add_argument("--task", required=True, choices=("test", "integrationTest"))
    arguments = parser.parse_args()
    root = Path(__file__).resolve().parent.parent
    output = root / "build/ci-test-outcomes" / arguments.service / arguments.task / "safe-outcomes.json"
    try:
        environment = {name: os.environ[name] for name in METADATA_ENV if name in os.environ}
        report = collect(root, arguments.service, arguments.task, environment)
    except Exception:
        # Unknown collector/metadata failure must not echo raw XML, paths, values or environment.
        report = {"schemaVersion": 1, "collection": "COLLECTION_ERROR", "acceptance": "NOT_ASSESSED"}
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(report, indent=2) + "\n", encoding="utf8")
    print(json.dumps({"collection": report["collection"], "acceptance": "NOT_ASSESSED"}))


if __name__ == "__main__":
    main()
