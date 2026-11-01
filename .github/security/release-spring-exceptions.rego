package trivy

# Owner-approved alpha risk exception, 2026-11-01. Release image scan only.
# Scope/risk/reassessment: docs/runbooks/release-spring-exceptions.md.
# These dependencies remain vulnerable; this is not a framework patch.
default ignore = false

accepted_packages = {
    "CVE-2026-47884": {
        "org.springframework:spring-webmvc": "pkg:maven/org.springframework/spring-webmvc@6.2.19",
    },
    "CVE-2026-47890": {
        "org.springframework:spring-webmvc": "pkg:maven/org.springframework/spring-webmvc@6.2.19",
        "org.springframework:spring-webflux": "pkg:maven/org.springframework/spring-webflux@6.2.19",
    },
    "CVE-2026-47892": {
        "org.springframework:spring-webflux": "pkg:maven/org.springframework/spring-webflux@6.2.19",
    },
}

ignore {
    input.Type == "vulnerability"
    input.InstalledVersion == "6.2.19"
    accepted_packages[input.VulnerabilityID][input.PkgName] == input.PkgIdentifier.PURL
}
