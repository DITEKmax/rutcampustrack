# Assembled Vue Nginx gate — independent source and budget review

**CHANGES REQUESTED: three P2 findings.** Reviewed the coherent commits `5228faa5709d92eef65d10f0b42d7defeb5bf26a` and `f4de9fc3dff1e5983879c870f9c50247e6223438` in `/workspace/rct-worktrees/headman-web`, limited to `tests/e2e/pr3-nginx/{gate.py,build.py,README.md}`. Root product checkpoint is `ddfca53015cb952ffd65e96689a2cba8574084f8`. No tests, builds, prepared runtime, Docker commands, Auth/API/browser operations or owner-local executions were run by the reviewer.

## F1 — P2: source admission omits the executable gate package

`gate.py:65–73` checks HEAD plus tracked/untracked frontend and Nginx files, and the served-WEB oracle. It does not inspect `tests/e2e/pr3-nginx/` itself. A local edit to `gate.py` or `build.py` therefore passes `checkout(expected_sha)`, and the modified executable can create receipts bearing the unchanged reviewed source SHA. Build receipts pin frontend inputs, not the gate code that enforced ownership/budget/adaptation rules.

Include the gate's own executable source in dirty/untracked source admission, while keeping dependency/artifact exclusions narrow. The expected source SHA must identify both admitted product inputs and the actual enforcement code. This does not require adding the gate to the reusable PWA product fingerprint.

## F2 — P2: runtime admission does not establish the prepared namespace and budget

`gate.py:166–180` writes a finite Compose specification, but `compose.json` is absent from the pinned configuration inventory. At `gate.py:218–229`, runtime admission selects the fixed container name and checks image, entrypoint/command, and a subset of mounts. It does not check Compose project/service ownership labels, selected external network, published loopback port against `--base`, read-only root, memory/CPU/PID/tmpfs limits, or unexpected additional mounts.

Thus a same-name container with the required image/config/artifact mounts but a foreign namespace, different publication/network, extra mounts or larger resource settings can pass these predicates. The later HTTP bytes checks cannot establish those ownership/resource properties. This leaves the explicitly required local namespace and bounded-runtime claims unproven.

Pin the complete prepared Compose identity/specification and validate its relevant actual selected container properties before HTTP reads. Require the project/service labels, exact expected publication/network and allowed mounts, and the advertised finite resource settings. Bind the requested HTTPS origin to that publication. Keep the explicit local Unix Docker selector and do not add broad container discovery or mutations.

## F3 — P2: terminating the build monitor can orphan its build process group

`build.py:54–67` launches npm in a new session and stops that owned group in `finally`. The script installs no SIGTERM handler. Python's default SIGTERM action exits without running this cleanup; the npm/Node session survives independently. A normal termination of the monitor can therefore remove the 250ms disk/180s guard while the build continues writing.

Handle SIGTERM through the same bounded owned-process-group cleanup path, including the launch-to-monitor interval. Preserve the raw failure log and return failure; never target other Java/Node/browser/container processes. SIGINT already unwinds through Python's exception path, but ownership must also survive the ordinary termination path.

## Verified design and practical bounds

The production Nginx configuration is copied with finite replacements: one worker, the matching external/local TLS port, fresh certificate paths, one selected gateway, and four inner listeners/static roots. Routes, CSP and API/WS headers are retained. There is no new Origin rewrite. The explicit local Docker CLI clears remote selector variables while preserving Docker config/credentials; no pull/build/replacement backend is requested.

Production Vue uses SockJS from `/api/ws?ticket=...`; `/info` and transport suffixes use the preserved `/api/ws/` upgrade route. The exact `/api/ws` seam alone is not a demonstrated production regression. Anonymous status/static checks are correctly separated from authenticated STOMP/CONNECTED, native auth, PWA lifecycle, TMA provider and WebPush, all of which remain NOT_RUN pending their own actual proof.

PWA reuse is feasible: independently calculated full `app_inputs` fingerprints at product6219 and Rootddf are both `120ee69cf3420e325c1e76b6c33ef575408cb20cc97f7428f61fb2793706097e`. The package/lock/mobile/PWA/shared build inputs did not change in Angular removal. Existing artifact bytes and the accepted build receipt/log must still match. WEB needs a fresh strict build for its chunk-recovery additions; TMA needs its matching fresh build as planned.

The disk guard uses the approved early2.5GiB/hard2GiB thresholds and an additional512MiB forecast/growth ceiling; the group seven-GiB floor does not apply here. One existing-image Nginx with128MiB/0.5CPU/32PID and finite tmpfs plus two serial small Vue artifacts fits the proposed disk scope once the ownership/termination findings are corrected. The reviewer does not certify an observed peak from source alone.

The fixed768MiB Node old-space limit is an unverified build-feasibility choice, not a proven requirement or failure. Given the shared WEB Vue/TypeScript program and reported host headroom, a finite1536MiB cap for the serial build is reasonable if Root selects it before the first attempt; retain all disk/time guards. Do not retry an identical heap failure. No broader suite or dependency installation is needed for this review.

Reviewed file SHA256: `gate.py=d8f10ffae89cba69d0c72150b4b1d9dcf0e356fcb80df5f4bf1475351d73d945`; `build.py=764a4aa801aa254a760f84256f4af226116be1014a37c1da8a12af7e279bba52`; `README.md=fbb1841f6a96b64804575a70592586457177b908c3acec4a74f4fdfd3eceb822`.

All findings are delivered together for correction and a narrow recheck of their influence. Reviewer-created file: this external report only. Requested model `gpt-6.1-sol`, effort `high`; runtime telemetry unavailable, no fallback claimed.
