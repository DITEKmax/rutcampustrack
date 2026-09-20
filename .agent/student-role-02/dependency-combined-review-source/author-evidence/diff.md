# Scoped diff

Baseline `d3c31acb8cce53791a4981e5858a37d44fdc9a0e`; no commit was created.
The worktree contains 26 imported dependency/security files from the immutable
handoff and this worker's bounded reconciliation paths. The imported files are
listed and hash-tracked in `source-manifest.json`; they remain intact. The root
`build.gradle.kts` is intentionally mixed: foreign dependency policy plus this
worker's JaCoCo source-provenance hunk.

Own changes by behavior:

- OpenAPI helpers compare normalized UTF-8 line endings on both actual and
  expected snapshots. Java-first exports regenerate five semantic snapshot
  changesets described in `snapshot-diff.md`; academic remains semantically
  unchanged. Mobile-core TypeScript is regenerated from the BFF snapshot and
  passes its drift check.
- BFF runtime fixtures set only the test-classpath gRPC server to port `0`,
  retaining the actual HTTP-to-signed-gRPC assertions and production client
  configuration.
- The shared event guard includes exactly `homework.due_reminder` and
  `homework.weekly_digest`. `HomeworkNotificationContractIT` executes the real
  Academic scheduled producer/outbox path and validates both serialized
  envelopes against their schemas.
- JaCoCo discovers generated protobuf/gRPC Java source files under both generated
  source roots and excludes their outer/nested class files at execution time.
  Handwritten gRPC implementations remain reportable. Existing floors and DTO,
  config and security exclusions are unchanged.
- Renderer tests exercise converter success, PDF-to-PNG DPI, process exit/error/
  timeout, missing output and temporary-directory cleanup. Push compatibility
  uses the real Web Push library against an in-memory loopback server.

Content-aware tracked diff (`git diff --ignore-space-at-eol --numstat`): 40
paths, 573 additions and 462 deletions, including the preserved foreign
dependency baseline. Raw line-ending-aware diff is larger for generated JSON;
the mobile-BFF snapshot is 2/4 semantic lines after EOL normalization. The
untracked owned source/test/evidence files are enumerated in
`source-manifest.json`.

`git diff --check` exits `0`. No product business source, dependency version,
lockfile, deployment setting or external application was changed by this
worker. The only changed build file hunk outside the frozen import is the
generated-class provenance logic in the root build script.
