# Compact contract — JS-STUDENT-01 HTTPS transport repair

Date: 2026-09-06. Risk: S2. Base integration revision: `e583f05140c43311b547bbaeaa55c1a7bc3c9ce9`.

## 1. Goal

Isolate the browser HTTPS transport failure before repeating the six-service PWA runtime, then hand the smallest supported repair/evidence to root.

## 2. Context/evidence

The three latest integration runtime attempts end at browser login seeding with HTTP 499 and an external certificate-error HTML page. The direct gateway login in `20260906-220011` is HTTP 200. The harness has already moved its HTTPS origin from `js-student-01.test` to `127.0.0.1`, but that attempt was not rerun.

## 3. Relevant scope

Sole writer: `.agent/root.agent/js-student-01-transport-repair/`. This packet, a bounded local HTTPS probe, and its evidence only. Integration runtime files remain read-only.

## 4. Required behavior

Use a browser against a task-owned HTTPS server on `127.0.0.1:28445`; prove an actual 200 navigation GET and an actual 200 same-origin POST with observed Origin. The fixture POST also sets the required cookie attributes solely to prove Playwright URL-path cookie selection. The server must record only safe transport metadata.

## 5. Constraints

No six-service startup, integration edits, credentials, global certificate trust, AV/firewall/security-monitoring changes, proxy bypass, tunnel, production access, or mocks presented as product runtime. The self-signed certificate is task-scoped and short-lived.

## 6. Existing patterns

The successful PWA offline recipe and the integration harness both use a self-signed HTTPS certificate with `127.0.0.1` IP SAN and Playwright/Chrome. The harness preserves `Origin` while proxying requests and reports HTTP status.

## 7. Acceptance criteria

- Ports 28445 and 28446 are free before the probe.
- Browser GET receives HTTP 200 from the owned HTTPS server.
- Browser POST receives HTTP 200 and the owned server records the exact same-origin `Origin` header.
- A `/api/auth` cookie is absent from `context.cookies(origin)` and present with the required attributes when queried at `/api/auth/refresh`.
- Evidence contains statuses and safe headers/body lengths only, never tokens, passwords, certificate key material, or external HTML.

## 8. Verification

Generate a local certificate with IP SAN, run the browser probe, capture its JSON result and exit code, and record revision, command, environment, evidence and limitations in `checks.json`.

## 9. Do not

Do not treat this transport proof as a PWA, gateway, authentication, refresh-cookie, CORS, service-worker, or six-service runtime PASS. Root decides any integration-harness transfer after the proof.
