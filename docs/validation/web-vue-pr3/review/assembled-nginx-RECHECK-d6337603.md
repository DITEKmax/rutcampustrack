# Assembled Vue Nginx — targeted correction recheck

**PASS for `d633760382a348afeaf2c48a4cfa3cba63c4f5a0`.** F1–F3 from REVIEW-f4de9fc3.md are closed by source inspection. The additional producer/public-base risk identified by Root is addressed. No new blocking P1/P2 finding was found in the corrections and their influence. This is a source recheck, not runtime acceptance.

Reviewed only the correction to the same three files under `/workspace/rct-worktrees/headman-web/tests/e2e/pr3-nginx/`; unchanged routing/oracle core review is reused.

- **F1:** `checkout()` now admits its own executable package as well as frontend/Nginx/oracle files. Tracked modifications and untracked gate files fail before outputs or build effects; dependency/artifact exceptions are restricted to frontend paths. External `pin` now requires the current exact gate SHA separately from the artifact's accepted source revision.
- **F2:** The complete prepared Compose file is hashed. A public run nonce binds preparation to the selected container. Before HTTP, admission checks project/service/nonce labels, image and command, one selected external network/network mode, exact loopback publication and requested origin, all read-only bind mounts with no extra data mounts, read-only root, nonprivileged mode,128MiB memory/swap,0.5CPU,32PID, bounded tmpfs, capabilities and security options. The explicit local Unix Docker selector remains unchanged. These predicates establish the properties missing from the previous name-only selection; actual engine admission still needs its future observation.
- **F3:** SIGTERM/SIGINT handlers are installed before `Popen`. They record a stop flag without raising inside the launch-to-assignment window. `finally` stops only the newly owned process group, checks surviving descendants even after the leader exits, bounds TERM/KILL cleanup, and restores the previous handlers. The raw log remains preserved and interruption cannot produce BUILD_PASS. Disk thresholds,512MiB growth ceiling and180s limit remain; Node old-space is now1536MiB before the first prefix build.

## Producer/public-base correction

Root observed that accepted source-equivalent old PWA and fresh default TMA artifacts used `/assets/`, which is unsuitable for `/app/` and `/mini-app/`. The earlier review's PWA source fingerprint equality remains true but does not establish producer configuration or suitability of those bytes. **Those two old default-root artifacts must not be reused for common-prefix serving.** Accepted WEB with base `/` may be reused; its strict result is not repeated. One matching PWA prefix build and one matching TMA prefix build are still required under Root's serial budget.

`build.py` clears inherited `VITE_*`, enforces the app base and fixture=false, and supplies Docker-default public version policy values (`0.0.0`, minimum fallback, force=true). Receipts distinguish enforced build environment from explicit accepted external fixture=false attestation; a bare compiler flag is not claimed to be recoverable from arbitrary JavaScript. The actual HTML entry module and root-relative resource prefixes are checked, and PWA version.json is checked and hashed. `pin` evaluates this contract before creating its output; `prepare` checks every app contract and inventory before creating the run directory. Wrong-base mobile bytes therefore fail before preparation outputs. No fake prefix rewriting of an unsuitable artifact is introduced.

The source checks preserve private environment values in process memory only; no environment dictionary, credentials, cookies or bearer is emitted. A minor pre-build recommendation was sent to the author: force `NODE_ENV=production` in addition to the explicit VITE settings, so an inherited development setting cannot alter production conditionals. No observed NODE_ENV-related failure is claimed.

## Pending finite HTTP listener delta

This commit still prepares one TLS publication. Root's proposed additional loopback HTTP14240 listener is not included in this PASS. It can be reviewed as a narrow next delta: same owned common server/routes/CSP and128MiB container, exactly14240+14443 loopback publications, no Main restart/config/CORS or Origin rewrite. HTTPS static/anonymous forwarding and HTTP native Auth/SockJS observations must remain separate; neither combines into TLS Auth/WSS proof. If unchanged CSP or a provider blocks the browser path, record that criterion NOT_RUN.

Author reports focused pure controls (3 accepted/25 rejected, producer-prefix controls, AST/diff checks); the reviewer did not execute those controls again. Reviewer actions were source/diff/Git metadata and file-hash reads only. Builds/tests/Docker/HTTP/Auth/browser/service/owner-local operations:0. No group cloud start, fixture/grant/password/clock mutation or deletion is approved.

Reviewed SHA256: `gate.py=2cff7f73c757a2d64d434a5ec0094568566cdb2b8aa8e0a2d86c839f569ca46b`; `build.py=fae8c970cdeecd0bc77d61dd6c62e4267c5592661baa34607cde991f9a82fe84`; `README.md=81d4d8f440976718871059b12b8dd6b422d4a067e11a2be6bd5a367103f1ec16`.

Requested model `gpt-6.1-sol`, effort `high`; runtime telemetry unavailable, no fallback claimed. Reviewer-created file: this external report only.
