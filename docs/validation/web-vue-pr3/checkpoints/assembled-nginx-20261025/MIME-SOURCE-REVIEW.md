# Assembled Vue Nginx — module-worker MIME correction

**PASS for the coherent commits `d7edbb7539ae88e945522f49cd15a92a6f9e95d5` and `82daf076255b7ef61be34ae448af02bfa8fc1403`. Blocking P1/P2 findings:0.** Narrow source review against parentf81d14d7; prior source, CA/origin, ownership, resource and lifecycle reviews are reused.

Root's existing diagnostic independently records46 served script resources: all bytes/statuses match,43.js resources have the correct JavaScript MIME, and the three actual PDF.mjs workers return application/octet-stream. This report uses that existing observation as the concrete trigger; the reviewer made no new Docker/HTTP/browser request.

Each of the three inner frontend servers now includes the complete standard MIME table at server scope, then adds `application/javascript` for `.mjs`. Keeping the table in the same scope preserves other standard MIME mappings rather than replacing them with a one-extension table. This applies to WEB's existing `^~ /assets/` location as well as standalone image configurations. PWA/TMA asset regexes add only `.mjs`, retaining strict `try_files ... =404` and immutable cache handling. An independent textual reconstruction confirms that these are the only three server-config changes; other routes, headers, cache rules and security behavior are unchanged.

The finite oracle still checks actual bytes and JavaScript MIME, now also requires immutable caching for actual assets/.mjs workers, and checks missing `.js` **and** `.mjs` assets for404 without SPA fallback. Diagnostic messages expose only app/relative asset path and a normalized MIME classification. They do not weaken an oracle or manufacture successful responses.

## Artifact reuse remains separately bound

`app_inputs` excludes exactly the three runtime files `frontends/{web-vue,pwa-vue,tma-vue}/nginx.conf` by full-path equality. It adds no directory, suffix or wildcard exclusion. All other previously admitted source/lock/shared/PWA/Vite inputs remain in the fingerprint. The existing strict scripts run vue-tsc/Vite, the Vite/source configurations do not read these Nginx files, and Dockerfiles copy them to their final Nginx stage. A server MIME-only correction therefore does not require new JS/CSS compilation.

The source checkout still covers those config files; generated server copies and complete Compose bytes remain independently hashed in prepared pins and matched to actual runtime mounts. Fingerprint equality for compiled artifacts does not bypass runtime config binding. Independent AST comparison finds only `app_inputs` and the finite HTTP `check` function changed; all other gate functions, including CA trust, producer config, source admission and container/resource predicates, remain identical. `build.py` is byte-identical.

Root should create fresh external pin receipts using the accepted original WEB/PWA/TMA artifact source revisions, actual immutable artifact inventories, producer qualification and real original build logs. Preserve old receipts/logs and the original MIME failure; do not relabel an old artifact's source revision or overwrite its evidence. No app rebuild is justified by this exact runtime-only delta.

## Final package and verification bounds

The five assigned source paths were clean at final82daf076. Exact SHA256:

- WEB `nginx.conf`: `3f80ee225fe0ccbc2a3467c017cffe7cbc95ed348a623d7c52f787132ca7c8cf`
- PWA `nginx.conf`: `e5131c7773bda8757ad546d339006ad357ae1853dffad53c5d19cb3f0c90c307`
- TMA `nginx.conf`: `266fd96de3ae7ae52e21e483797a78df7b60901ca161d2c6848f80a82f155a75`
- `gate.py`: `0daf2c1f87d33adc107296d4cb80016ed6c34e9484a8d1338e05a1c90cbb2c16`
- README: `3fa84d62eaa0603841acc267ce98e534f292c50f2a267dba472d3d5ff3337582`

Author reports focused MIME/regex/404/cache and exact-closure controls plus AST/diff checks. Reviewer performed source/diff/hash/AST comparisons and read Root's existing diagnostic, without repeating those controls or running a suite. Reviewer Docker/HTTP/Auth/browser/build/runtime operations:0. The only reviewer write is this external report.

Root still needs final integration/source freeze, new config/artifact receipts, and its one changed-configuration Nginx recheck. Source PASS does not close the observed MIME failure until actual served workers pass. Existing memory/disk/time limits, private modes, Auth gates, protocol-specific NOT_RUN claims and owner-local full group gate remain unchanged.

Requested model `gpt-6.1-sol`, effort `high`; runtime telemetry unavailable, no fallback claimed.
