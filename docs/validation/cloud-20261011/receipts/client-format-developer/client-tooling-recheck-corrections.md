# CT02R / CT06R author corrections

Stable clean target240b10d524146d458e4edc8b9a8fbb7bce9f940f, previous independently reviewed9acc0f7028658132b0d4ad8dc10f1a4860f77dd1. Single coherent five-file E2E-only correction commit. Root original recheck immutable at /workspace/scratch/rct-validation/client_tooling_recheck/findings.md.

CT02R: JwtAuthenticationFilter#bootstrapScopeDenied/#problem rejects protected bootstrap requests403 and serializes the top-level code. Both teacher GET and attendance PUT now assert that exact BOOTSTRAP_SCOPE_DENIED shape. Auth role-selection remains its separate handler's extras.code ROLE_NOT_SELECTABLE. No product contract altered or denial widened.

CT06R: requirePrivateArtifacts now compares configured reporter's full path with require.resolve('./safe-reporter.ts'), matching pinned Playwright config resolveReporters, rather than checking basename suffix. Same pre-seed call order/debug/capture restrictions retained.

Meaningful browser-free guard control uses actual requirePrivateArtifacts and real resolved default worker config. It creates an existing temporary alternate safe-reporter.ts, resolves its path but never imports/runs it. Controls require default acceptance, alternate rejection, extra HTML reporter rejection and trace-on rejection. On old9acc guard plus new test: launcher exit1, canonicalReporterAccepted=true, sameBasenameAlternateRejected=false, extraReporterRejected=true, traceRejected=true. Relevant assertion failed without infra/parser/browser/API. On correction: exit0, all four booleans true. Owned alternate directory cleaned finally.

Combined privacy control after correction PASS: original pinned Fill-title leak reproduced; random public marker absent from captured stdout/stderr and all6 emitted files; three successful controls and two intentional failed/expected-failed diagnostic controls retained. No marker/raw error output forwarded. Product acceptance unchanged and no expected failure added there. Scoped TypeScript, targeted Prettier, Node syntax and git diffcheck PASS. No browser/backend/Gradle/Docker/UI/massformat/push run; clock proposal still separate source-only.

Fresh independent recheck needed before closure/integration; this is source preparation, not client runtime PASS.
