# Assembled Vue Nginx — final listener/producer recheck

**PASS for `666280a24a7504130cd81b8eded0fcff03a054ca`. Blocking P1/P2 findings:0.** This narrow source review covers only the delta from accepted `d633760382a348afeaf2c48a4cfa3cba63c4f5a0`; the three earlier corrections, artifact-prefix admission and unchanged core review are reused from RECHECK-d6337603.md.

The final three-file package in `/workspace/rct-worktrees/headman-web/tests/e2e/pr3-nginx/` was clean at review. Changes are confined to the finite second listener/publications, explicit protocol-specific evidence labels, production NODE_ENV and corresponding documentation.

## Listener and claims

`prepare()` replaces the one source `listen443ssl` directive with `listen14443ssl` plus `listen14240` **inside the same common server block**. It does not introduce a second route set. Existing CSP, API/SockJS routes, Origin forwarding and dynamic `$scheme`/`$server_port` headers are untouched. The source HTTP80 redirect server remains internal and is not published. There is no Main Gateway/Notification restart, configuration/CORS change or Origin substitution in this package.

The only admitted publications are `127.0.0.1:14443:14443` and `127.0.0.1:14240:14240`. Both prepared Compose and actual container publication sets must match exactly; missing/extra ports, other host interfaces and alternate port choices fail. The static oracle continues to require HTTPS14443, so an HTTP-only observation cannot pass as TLS evidence. Previous project/service/nonce/image/network/mount/resource admission remains in force.

Pins and results now explicitly separate HTTPS static/anonymous forwarding, future HTTP native Auth/SockJS, and `tlsAuthWss=NOT_RUN_OWNER_LOCAL`. Native auth, STOMP CONNECTED, PWA lifecycle, TMA provider and WebPush remain unobserved until their separate actual qualified packets run. If the unchanged source CSP or provider prevents a browser path, retain NOT_RUN; do not relax security or transfer HTTP results to HTTPS Auth/WSS claims. Root must independently qualify existing Main HTTP14240 allowlists and exact own fixture/session lifecycle before its later browser attempt.

## Producer and budget

`producer_config()` now additionally forces and records `NODE_ENV=production`; the existing build environment update overrides an inherited development value. No private environment dictionary is serialized. App-specific bases remain `/`, `/app/`, `/mini-app/`, fixture mode remainsfalse, and Docker-default public version values and actual PWA policy/prefix hashes remain checked before preparation outputs.

The extra listener adds no container or build. One retained-image Nginx remains128MiB memory/swap,0.5CPU,32PID, read-only with bounded tmpfs. Sequential mobile builds retain1536MiB Node old-space,180s/250ms monitoring,512MiB forecast/growth ceiling, early2.5GiB/hard2GiB disk thresholds and owned process-group cleanup. This source budget does not establish actual peak usage. No dependency installation, image pull/build, replacement backend or cleanup is included. The group seven-GiB floor remains separate.

Accepted WEBddf root-base artifact/strict result may be reused. Old default-root PWA/TMA artifacts remain unsuitable despite source equivalence. Root still needs exactly the planned one PWA and one TMA prefix build under the separately admitted serial budget; there is no reason to repeat unchanged WEB strict/build work.

## Exact package and bounds of verification

- `gate.py`: `38ca8c95e558738d3b745863878c8a165fa898f1048be334b650832309621740`
- `build.py`: `1e5ca3ebcf0ebf635890e6e77566cd6042cd0ce2b1827bfec372bfacc51127c8`
- `README.md`: `04bf1e99bd15b01ad100d34faacdad413ff4f53a7382f408ccf2cc47d3f8b2e1`

Author reports five focused negative publication/protocol controls and same-server/CSP/Origin comparisons plus AST/diff checks. Reviewer read the exact delta and final source/metadata and independently hashed the files; those controls were not rerun. Reviewer builds/tests/Docker/sockets/HTTP/Auth/browser/service/owner-local execution:0. No group cloud action or fixture/grant/password/clock/data mutation is approved. The final integrated Root/source SHA and current resource admission must be fixed by Root before effects.

Reviewer-created file: this external report only. Requested model `gpt-6.1-sol`, effort `high`; runtime telemetry unavailable, no fallback claimed.
