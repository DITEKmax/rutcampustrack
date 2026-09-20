# Correction-02 checks

| Check | Command / evidence | Exit | Result |
|---|---|---:|---|
| Initial compile source failure | exact compile command, session `60085`; raw record `failures/compile-01/failure.md` | 1 | stale test import; corrected |
| Compile infrastructure failure | exact compile command, session `12186`; raw record `failures/compile-02/failure.md` | 1 | generated Gradle problems report collision |
| Compile | exact compile command, session `13664` | 0 | `compileJava` + `compileTestJava` passed |
| Focused unit | controller, OTP, Auth repository and TMA repository selectors, session `93390` | 0 | 24/24; zero skipped/failures/errors |
| Affected PG/Redis IT | LogoutLifecycle, Otp and SessionAuthFlow selectors, session `65967` | 0 | 20/20; zero skipped/failures/errors |
| OpenAPI compare | property-free `OpenApiSnapshotIT`, session `98527` | 0 | 1/1; zero skipped/failures/errors |
| Saved affected XML | same affected selector already-running capture, session `67640` | 0 | XML copied byte-for-byte into `junit/affected` |
| XML/hash self-audit | `powershell.exe -NoProfile -ExecutionPolicy Bypass -File generate-correction-manifest.ps1 -Verify` | 0 | `MANIFEST_VERIFY PASS` |
| Diff whitespace | `git diff --check` | 0 | existing LF/CRLF warnings only |

The first two compile exits are preserved as correction evidence. No further
Gradle/Docker/runtime check is run while finalizing this evidence package.

