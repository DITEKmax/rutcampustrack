# Goal and context

Prepare an isolated teaching-clock fixture for real PWA/TMA acceptance and one precise export client configuration dependency. Preserve production clocks and authentication/TLS rules. First restore current TASK-CONTRACT, CURRENT, decisions, questions and resources, including D002–D007, and read clock-fixture-proposal.md.

Frozen root implementation: c898889b7932bf8d139a23be15a0b34762f3a387. Retained requested Sol6.1/high; actual metadata UNKNOWN.

# Writer scope

Create a new isolated worktree /workspace/rct-worktrees/client-runtime and local branch codex/rct-validation-client-runtime-20261011 at the frozen revision. The previous client-format worktree remains the immutable reviewed 240b10d input. Own receipts: /workspace/scratch/rct-validation/client_runtime_developer.

You remain sole writer of shared formatter, Gradle, npm, lock and configuration changes. Implement only test fixture/build task/isolated runtime and harness support. No auth domain or product UI edits. The export author owns export APIs, migrations and UI behavior. The security author owns OTP and enum corrections.

Academic application.yml may receive only the requested document-renderer gRPC client stanza: address dns:///document-renderer-service:19095 and negotiation-type plaintext, matching the actual existing Attendance renderer client and renderer server. Root reopened the real GrpcAuthInterceptor, application.yml and e2e private_net stanza: existing renderer uses x-grpc-secret and has no TLS server profile or trust mounts. Academic already has the global GrpcSecretClientInterceptor. Preserve that authentication and the integrated Rabbit publisher-confirm setting; do not add a protected service token on plaintext or invent TLS configuration. The exact-method ServiceIdentityServerInterceptor policy does not include renderer conversion. No new transport/security policy is inferred from this source correction. Config commit remains separate from teaching-clock fixture. Full chain authentication/native conversion must subsequently be verified with synthetic credentials; source alignment alone is not runtime PASS.

# Required behavior

Use an external test-only Clock fixture for teaching rules in Academic, Schedule, Attendance and BFF. It must be absent from production boot JARs and the production classpath, require explicit opt-in and disposable-target guards, and use existing main classes. Auth, gateway, JWT admission, DB NOW, TLS, ShedLock and cache expiry keep real clocks.

The browser shares the teaching instant and Europe/Moscow timezone, with timers running. Choose an accepted weekday and an interior class time; if Sunday is not an accepted fixture day, the nearest allowed Monday is ordinary synthetic test data. A fresh student must exercise actual positive geo check-in, followed by the existing D005 manual-absence prohibition. Do not bypass time or geofence guards.

Prove fixture/classpath isolation and Clock injection without adding a hidden daemon or control plane. Use the existing runtime harness or standard Boot/native startup helpers.

# Constraints and verification

Initially light source preparation only: SECURITY2 is the sole heavy lease. Do not start Gradle, browsers, services, Docker or full builds until root grants the next bounded lease. No mass formatting before the remaining semantic changes are frozen.

Provide a safe plan, exact diff/inventory, light parser checks and stable commits for independent review. After a lease, verify fixture build/classpath isolation and then real browser/backend acceptance. A source proposal is not PASS. Save bounded receipts immediately.

No push, shared task docs edits, API/proto/export code, private environment, actual secrets, deployment, main changes, Figma writes or scheduler.
