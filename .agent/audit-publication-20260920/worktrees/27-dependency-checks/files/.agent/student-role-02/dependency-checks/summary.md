# Dependency checks handoff

Дата: 2026-09-07. Риск: S2. Ветка: `codex/student-role-02-dependency-checks`.
Исполнитель: свежий bounded Luna/max; детей не создавал, Terra не использовал,
коммит не создавал.

## Scope

Закрыт свежий compact contract для snapshot/EOL comparison, mobile-core
generated BFF type, динамического BFF test-port, shared-event whitelist и
Academic producer contract, JaCoCo generated-source provenance, renderer
converter boundary и реального web-push compatibility. Чужие 26 dependency/
security paths импортированы и сохранены; root build script помечен как mixed
ownership в `source-manifest.json`.

## Criteria

| Criterion | Result |
| --- | --- |
| Frozen 26 input files and hashes | Initial 26/26 hash+byte check exit `0`; immutable manifest SHA recorded |
| Java-first snapshots and no-update drift | Six update runs exit `0`; final combined no-update exit `0`; only documented semantic deltas |
| Generated mobile-core contract | Generation and `generate:types:check` exit `0`; generated hash matches BFF snapshot |
| Homework schemas | Both schemas are emitted by the real producer/outbox path and validate; 2/2 tests exit `0` |
| BFF dynamic port | Auth and homework HTTP→signed-gRPC suites pass; 19/19 tests, exit `0`, test server port `0` |
| JaCoCo provenance/floor | Full renderer report/verification exit `0`; handwritten line coverage `75.19%` (100/133), class coverage `75%` (6/8); generated protobuf/gRPC classes filtered by source provenance |
| Renderer boundaries | 10 renderer tests exit `0`, including 7 new converter cases and existing ProcessRunner/gRPC tests |
| Web Push compatibility | Actual `PushService.send(Notification)` to loopback returns `201`; 1/1 test exit `0` |
| Security handoff | Separate immutable dependency handoff proves 8 JARs / 1,141 packages / 0 HIGH / 0 CRITICAL; 57 MEDIUM remain visible |

## Evidence

- `checks.json` — commands, exit codes, environments and artifact paths.
- `source-manifest.json` — final product/test/build path manifest and ownership.
- `snapshot-diff.md` — normalized semantic OpenAPI comparison and generated
  type evidence.
- `runtime-evidence.md` — runtime/test reports, Docker cleanup and security
  handoff.
- `diff.md` — content-aware diff and preserved foreign scope.
- `failed-evidence/push-library-aead-diagnostic.xml` — prior diagnostic retained;
  it is not the current push result.

## Limitations

Fresh independent Sol high review is still required by the S2 route. This worker
does not claim whole-role or full PWA/TMA acceptance. A broad root `check` is
root-owned and remains outside this bounded reconciliation; the applicable
focused suites and final six snapshot no-update gate pass. The full dependency
rootfs report intentionally exits `1` because 57 MEDIUM records remain; its
separate HIGH/CRITICAL gate exits `0`. Windows Git stat refresh could not write
the shared repository object database, so the academic no-delta snapshot may
remain shown as a status `M` even though content comparison is equal.
