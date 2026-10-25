# Independent source-only archive verification

**PASS for the declared source-only snapshot and manual finding qualification.**

Archive: `/workspace/rct-pr3-recovery/pr3-source-only-08e08aedc.zip` — **11,130,527 B**, SHA256 `caa5b61406f5bd6a37f78169159fef73cf512b58c1e8cadd8ed1245e66d8f82c`; source commit `08e08aedc00dd7a6eae173a9766dbfcc55c1ba8c`.

Independently verified CRC, safe/unique paths, absence of symlinks/encrypted entries, exact manifest inventory and all **3,666 source files' sizes/SHA256/file modes/exact Git blob bytes**. The archive contains3,668 files including manifest and restore instructions. Source payload is38,492,611 B; total uncompressed archive including those two metadata files is39,633,076 B.

Selection contains no Git history, installed dependency/cache directories, compiled Vue distributions, runtime gallery, DB dumps, private runtime directories, sessions or private keys. The sole PEM is the explicitly allowed committed public test key. The Gradle wrapper is retained. The27 files under landing/dist are canonical committed static landing source/assets: that Dockerfile only copies them and performs no build. Only env examples are included; the two declared public CI env inputs are omitted.

Every selected file matches the published Git snapshot. The24-file delta from runtime-qualified240 to this commit is documentation only. Restore instructions require a fresh clone and exact SHA in a new directory, preserve the owner's old checkout, and require newly generated local secrets/PKI. This ZIP is neither an offline Git bundle nor a stand/database backup. The separate immutable WIP recovery ZIP remains8,784,846 B with its expected00fb9f01… SHA unchanged.

**Native Gitleaks result remains EXIT1 /12 findings.** Reused Root's one scan; no second scan or report/rule/allowlist/history changes. All12 actual source candidates were individually checked against exact archive/Git bytes and their code context, without using redacted placeholders as candidate values. Nine findings are fixed request/idempotency UUID occurrences in tests, two are unquoted TypeScript API type annotations, and one is a fixed test-only public-key endpoint response stub with mocked controller dependencies. All are pre-existing public nonsecrets; their source blobs also match runtime240. **Manually qualified12; unresolved0; genuine private/cloud secrets identified0.** The scanner's raw count/exit are preserved rather than relabeled as a clean scan.

Durable evidence:

- `integrity.json` — SHA256 `afaa6b926690a30bf1f1921dbe2ee50e80f88fc00a740aefb41e042ac461251b`
- `findings-qualified.json` — SHA256 `00f7ce4fbaddfddb2ab50d1b4602f589da8ee48887ab4ebc4e59ac05ca2ec13c`; retains all12 finding indices/path/line/digest/classification and safe evidence, without raw candidate values or snippets.

No archived code, service, Auth/API/browser/build/test or owner-local gate was executed. Reviewer writes are these external verification receipts only. Original ZIPs, repository and scanner policy were unchanged. Full group gate remains owner-local NOT_RUN. Requested model `gpt-6.1-sol`, effort `high`; telemetry unavailable, no fallback claimed.
