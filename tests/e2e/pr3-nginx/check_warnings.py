"""Focused bootstrap/CSP regression checks; never starts Docker or a certificate provider."""
import argparse
import http.client
import json
import os
from pathlib import Path
import shutil
import ssl
import subprocess
import tempfile
import time
from urllib.parse import urlsplit

REPO = Path(__file__).resolve().parents[3]
SDK = "'self' https://telegram.org/js/telegram-web-app.js"
STRICT = "'self'"


def require(condition, message):
    if not condition:
        raise AssertionError(message)


def bootstrap(bash):
    script = REPO / "nginx/scripts/init-letsencrypt.sh"
    subprocess.run([bash, "-n", str(script)], check=True, timeout=10)
    stub = r'''#!/usr/bin/env python3
import json, os, re
from pathlib import Path
root=Path(os.environ['RCT_BOOTSTRAP_FIXTURE'])
args=__import__('sys').argv[1:]
assert args[:5]==['compose','--env-file','.env.prod','-f','docker-compose.prod.yml']
command=args[5:]
active=sorted(p.name for p in (root/'nginx/conf.d').glob('*.conf'))
production=root/'nginx/conf.d/default.conf'
if not production.exists(): production=root/'nginx/conf.d/default.conf.disabled'
config=production.read_text()
lineages=re.findall(r'(?m)^\s*ssl_certificate\s+/etc/letsencrypt/live/([^/]+)/fullchain\.pem;',config)
key_lineages=re.findall(r'(?m)^\s*ssl_certificate_key\s+/etc/letsencrypt/live/([^/]+)/privkey\.pem;',config)
assert len(lineages)==1 and key_lineages==lineages
lineage=lineages[0]
if command==['up','-d','nginx']:
    assert active==['http-only.conf']
elif command[:6]==['run','--rm','--entrypoint','certbot','certbot','certonly']:
    assert active==['http-only.conf']
    expected=['--webroot','--webroot-path','/var/www/certbot','--email','fixture@example.invalid',
              '--agree-tos','--no-eff-email','--cert-name',lineage]
    assert command[6:6+len(expected)]==expected
    assert command[6+len(expected):] in [
        ['--staging','-d','fixture.invalid'],['--force-renewal','-d','fixture.invalid']]
elif command==['exec','nginx','nginx','-s','reload']:
    assert active==['default.conf']
    assert (root/'nginx/conf.d/http-only.conf.bak').read_text()==os.environ['RCT_EXPECTED_BOOTSTRAP']
else:
    raise AssertionError('Unexpected provider command in isolated bootstrap fixture')
if active==['http-only.conf']:
    text=(root/'nginx/conf.d/http-only.conf').read_text()
    assert text==os.environ['RCT_EXPECTED_BOOTSTRAP'] and 'ssl_certificate' not in text
with (root/'calls.jsonl').open('a') as output:
    output.write(json.dumps(command)+'\n')
'''
    for scenario in ("fresh", "legacy-backup", "aborted-resume"):
        with tempfile.TemporaryDirectory(prefix="rct-nginx-bootstrap-") as directory:
            root = Path(directory)
            (root / "nginx/scripts").mkdir(parents=True)
            (root / "nginx/conf.d").mkdir()
            (root / "nginx/bootstrap").mkdir()
            for relative in ("nginx/scripts/init-letsencrypt.sh", "nginx/conf.d/default.conf",
                             "nginx/bootstrap/http-only.conf"):
                (root / relative).write_text((REPO / relative).read_text(), encoding="utf-8")
            (root / "nginx/dhparam.pem").write_text("isolated shell fixture; never used by TLS\n")
            (root / "docker-compose.prod.yml").write_text("# provider is a test stub\n")
            (root / ".env.prod").write_text("DOMAIN=fixture.invalid\nCERTBOT_EMAIL=fixture@example.invalid\n")
            expected = (root / "nginx/bootstrap/http-only.conf").read_text()
            if scenario == "legacy-backup":
                expected += "\n# retained legacy bootstrap copy\n"
                (root / "nginx/conf.d/http-only.conf.bak").write_text(expected)
            binaries = root / "bin"
            binaries.mkdir()
            (binaries / "docker").write_text(stub)
            (binaries / "sleep").write_text("#!/bin/sh\nexit 0\n")
            (binaries / "openssl").write_text("#!/bin/sh\nexit 97\n")
            for binary in binaries.iterdir():
                binary.chmod(0o700)
            environment = dict(os.environ, PATH=str(binaries) + os.pathsep + os.environ["PATH"],
                               RCT_BOOTSTRAP_FIXTURE=str(root), RCT_EXPECTED_BOOTSTRAP=expected,
                               RCT_BOOTSTRAP_DOCKER=str(binaries / "docker"))
            provider = subprocess.run([bash, "-c", 'test "$(command -v docker)" = "$RCT_BOOTSTRAP_DOCKER"'],
                                      env=environment, capture_output=True, timeout=5)
            require(provider.returncode == 0, "Refusing bootstrap without the exact fake Docker provider")
            for answer in (["n\n", "y\n"] if scenario == "aborted-resume" else ["y\n"]):
                result = subprocess.run([bash, str(root / "nginx/scripts/init-letsencrypt.sh")],
                                        input=answer, text=True, capture_output=True,
                                        env=environment, timeout=15)
                require(result.returncode == 0, "Isolated bootstrap failed: " + scenario)
            calls = [json.loads(line) for line in (root / "calls.jsonl").read_text().splitlines()]
            require(calls[-1] == ["exec", "nginx", "nginx", "-s", "reload"], "Missing final HTTPS activation")
            require(sorted(p.name for p in (root / "nginx/conf.d").glob("*.conf")) == ["default.conf"],
                    "Bootstrap remained active alongside production")
    return {"bootstrapScenarios": 3, "realProviderCalls": 0}


def fetch(base, path, context=None, headers=None, plain=False):
    url = urlsplit(base)
    require(url.hostname in {"127.0.0.1", "localhost", "::1"}, "Only isolated loopback endpoints are accepted")
    require(url.port is not None and url.port >= 1024, "Use an explicit unprivileged scratch port, never production 80/443")
    connection = (http.client.HTTPSConnection(url.hostname, url.port, context=context, timeout=5)
                  if url.scheme == "https" and not plain else
                  http.client.HTTPConnection(url.hostname, url.port, timeout=5))
    try:
        connection.request("GET", path, headers=headers or {})
        response = connection.getresponse()
        body = response.read(65537)
        require(len(body) <= 65536, "Unexpected oversized fixture response")
        return response.status, {name.lower(): value for name, value in response.getheaders()}, body
    finally:
        connection.close()


def script_policy(headers):
    policies = [directive.strip().split(None, 1)[1]
                for directive in headers.get("content-security-policy", "").split(";")
                if directive.strip().startswith("script-src ")]
    require(len(policies) == 1, "Missing or duplicate script-src directive")
    return policies[0]


def edge(args):
    require(args.ca and args.http_base and args.error_log and args.access_log,
            "Edge checks require own CA, HTTP base and isolated log paths")
    context = ssl.create_default_context(cafile=args.ca)
    cases = [
        ("/mini-app/", SDK), ("/mini-app/deep", SDK), ("/mini%2Dapp/", SDK),
        ("/app/../mini-app/", SDK), ("//mini-app/", SDK),
        ("/", STRICT), ("/app/", STRICT), ("/mini-app-not/", STRICT),
        ("/app/?next=/mini-app/", STRICT), ("/?next=/mini-app/", STRICT),
        ("/mini-app/../app/", STRICT), ("/mini-app/%2e%2e/app/", STRICT),
        ("/mini-app%2F..%2Fapp/", STRICT), ("/mini-app", STRICT),
    ]
    for path, expected in cases:
        status, headers, _ = fetch(args.https_base, path, context)
        require(status in {200, 301, 302, 404}, "Unexpected synthetic route status")
        require(script_policy(headers) == expected, "Normalized route CSP mismatch")
        require(headers.get("x-content-type-options") == "nosniff", "Security header lost")
        require("max-age=" in headers.get("strict-transport-security", ""), "HSTS lost")
    status, headers, _ = fetch(args.https_base, "/mini-app/", plain=True)
    require(status == 400 and script_policy(headers) == STRICT, "Early HTTP-to-TLS error must keep strict CSP")
    status, headers, _ = fetch(args.http_base, "/app/?fixture=1")
    redirect = urlsplit(headers.get("location", ""))
    require(status == 301 and redirect.scheme == "https" and redirect.path == "/app/"
            and redirect.query == "fixture=1", "HTTP HTTPS redirect changed")
    status, _, body = fetch(args.http_base, "/.well-known/acme-challenge/rct-owned-warning-check")
    require(status == 200 and body == b"rct-owned-acme\n", "ACME challenge route changed")
    status, _, _ = fetch(args.https_base, "/api/report-download/rct-warning-fixture-capability", context,
                         {"Referer": "https://fixture.invalid/rct-referrer-secret", "User-Agent": "rct-private-fixture-agent"})
    require(status == 200, "Synthetic report proxy route failed")
    # The operator supplies only this newly owned scratch instance's logs.
    deadline = time.monotonic() + 2
    access = Path(args.access_log).read_text()
    while "/api/report-download/[REDACTED]" not in access and time.monotonic() < deadline:
        time.sleep(0.05)
        access = Path(args.access_log).read_text()
    require("/api/report-download/[REDACTED]" in access and
            all(secret not in access for secret in ["rct-warning-fixture-capability", "rct-referrer-secret", "rct-private-fixture-agent"]),
            "Report download access-log redaction changed")
    errors = Path(args.error_log).read_text()
    require("conflicting server name" not in errors and "uninitialized" not in errors,
            "NGINX duplicate server/CSP initialization warning remains")
    return {"cspRoutes": len(cases), "early400": "PASS", "redirectAcmeRedaction": "PASS"}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--bootstrap", action="store_true")
    parser.add_argument("--bash", default=shutil.which("bash"))
    parser.add_argument("--https-base")
    parser.add_argument("--http-base")
    parser.add_argument("--ca")
    parser.add_argument("--error-log")
    parser.add_argument("--access-log")
    args = parser.parse_args()
    require(args.bootstrap or args.https_base, "Select bootstrap and/or isolated edge checks")
    result = {}
    if args.bootstrap:
        require(args.bash, "Bash is required for the isolated shell fixture")
        result.update(bootstrap(args.bash))
    if args.https_base:
        result.update(edge(args))
    print(json.dumps(result, sort_keys=True))


if __name__ == "__main__":
    main()
