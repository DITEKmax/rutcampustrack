#!/usr/bin/env python3
"""Finite offline controls using real Trivy filtering, without a scan or database."""

import argparse
import copy
import json
from pathlib import Path
import subprocess
import tempfile


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--trivy", default="trivy")
    args = parser.parse_args()
    policy = Path(__file__).resolve().parents[1] / ".github/security/release-spring-exceptions.rego"
    version = subprocess.run([args.trivy, "--version"], check=True, capture_output=True, text=True)
    if version.stdout.splitlines()[0] != "Version: 0.70.0":
        raise SystemExit("Controls require Trivy 0.70.0, matching the pinned release action")

    cases = []
    for cve, artifact in [("47884", "spring-webmvc"), ("47890", "spring-webmvc"),
                          ("47890", "spring-webflux"), ("47892", "spring-webflux")]:
        finding = {
            "VulnerabilityID": "CVE-2026-" + cve,
            "PkgName": "org.springframework:" + artifact,
            "InstalledVersion": "6.2.19", "FixedVersion": "7.0.9",
            "PkgIdentifier": {"PURL": "pkg:maven/org.springframework/" + artifact + "@6.2.19"},
            "Severity": "HIGH", "Status": "fixed",
        }
        cases.append((cve + "/" + artifact, finding, True))

    baseline = cases[0][1]
    for name, change in [
        ("other-cve", {"VulnerabilityID": "CVE-2026-99999"}),
        ("other-package", {"PkgName": "org.springframework:spring-core",
                           "PkgIdentifier": {"PURL": "pkg:maven/org.springframework/spring-core@6.2.19"}}),
        ("other-version", {"InstalledVersion": "6.2.20",
                           "PkgIdentifier": {"PURL": "pkg:maven/org.springframework/spring-webmvc@6.2.20"}}),
        ("inconsistent-version", {"InstalledVersion": "6.2.20"}),
        ("inconsistent-package", {"PkgName": "other:package"}),
        ("missing-purl", {"PkgIdentifier": {}}),
        ("null-purl", {"PkgIdentifier": {"PURL": None}}),
        ("cross-binding-47884-flux", {"PkgName": "org.springframework:spring-webflux",
                                     "PkgIdentifier": {"PURL": "pkg:maven/org.springframework/spring-webflux@6.2.19"}}),
        ("cross-binding-47892-mvc", {"VulnerabilityID": "CVE-2026-47892"}),
    ]:
        finding = copy.deepcopy(baseline)
        finding.update(change)
        cases.append((name, finding, False))
    for field in ["PkgIdentifier", "PkgName", "InstalledVersion", "VulnerabilityID"]:
        finding = copy.deepcopy(baseline)
        del finding[field]
        cases.append(("missing-" + field, finding, False))

    with tempfile.TemporaryDirectory(prefix="release-spring-policy-") as directory:
        source = Path(directory) / "input.json"
        output = Path(directory) / "output.json"
        for name, finding, accepted in cases:
            source.write_text(json.dumps({"SchemaVersion": 2, "ArtifactName": "offline-control",
                "ArtifactType": "container_image", "Results": [{"Target": "Java", "Class": "lang-pkgs",
                "Type": "jar", "Vulnerabilities": [finding]}]}), encoding="utf-8")
            result = subprocess.run([args.trivy, "convert", "--format", "json", "--severity", "HIGH,CRITICAL",
                "--exit-code", "1", "--ignore-policy", str(policy), "--output", str(output), str(source)],
                capture_output=True, text=True, timeout=30)
            if result.returncode != (0 if accepted else 1):
                raise AssertionError(f"{name}: unexpected exit {result.returncode}: {result.stderr}")
            findings = [v for r in json.loads(output.read_text())["Results"] for v in r.get("Vulnerabilities") or []]
            if len(findings) != (0 if accepted else 1):
                raise AssertionError(f"{name}: unexpected native filtering result")
            print(f"PASS {name}: {'accepted owner exception' if accepted else 'still blocking'}")
    print(f"PASS {len(cases)} native Trivy 0.70.0 offline controls; no image/database scan")


if __name__ == "__main__":
    main()
