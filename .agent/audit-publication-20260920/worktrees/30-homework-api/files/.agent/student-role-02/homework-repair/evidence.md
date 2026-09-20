# Homework API repair evidence

Date: 2026-09-07. Revision: uncommitted working tree on
`8002b9ea4356b10779c5bb9a6d99746d32d78ae2`. Environment: Windows,
Java 21.0.10, Gradle 8.12, Node 24.14.0, npm 11.9.0. Gradle commands used
`--no-daemon --no-problems-report` with the previously recorded
`require_escalated` absolute-classpath environment gate.

The repair adds a safe Homework ID parser in `StudentQueryService`, a
Homework-only `activeSemesterForHomework` status policy in
`MobileAcademicClient`, a type-scoped strict Boolean Jackson mixin, and a
highest-precedence bounded Homework no-store filter. `StudentApi` error
responses declare the header in Java; the canonical snapshot and generated
types were regenerated from that source. Generic `activeSemester()` usage for
session, Today and schedule remains unchanged.

## Runtime evidence

`StudentHomeworkHttpGrpcIT` runs a random-port Spring servlet against a
task-owned in-process Academic gRPC fake that records request presence and
signed claims without logging tokens. Post-fix: 6 tests passed, exit code 0.
The matrix covers valid true/false 200, overflow 400, numeric/string/null/
missing Boolean 400, malformed input with no mutation RPC, no-store on valid
and typed 400/401/403/404/503 responses, and active-semester `NOT_FOUND` →503
with no Homework read/mutation RPC. `StudentHttpGrpcAuthIT` also passed (exit
0), covering the neighboring check-in HTTP/auth seam after the Homework-only
filter was added.

No product port, external Academic service, PostgreSQL runtime, deployment or
migration was started. The existing Academic concurrency 3/3 evidence remains
from the prior gate and was not repeated. Fresh independent Sol review and
parent integration remain outstanding.

## Backend artifacts

One escalated Gradle build produced both artifacts (exit code 0):

- `services/mobile-bff/mobile-bff-app/build/libs/mobile-bff-app-0.1.0.jar`,
  49,998,262 bytes, SHA-256
  `7E9115447BD9CB8316E866E563CF17A1FBF555CA8C61C76FE46E2A682172EC24`.
- `services/academic-service/academic-app/build/libs/academic-app-0.1.0.jar`,
  103,468,059 bytes, SHA-256
  `21DA70ED26D78AE4E18D959D5C36C333FBDE7A381A60D0917739E51E9739C926`.

## Diagnostics and limitations

The ordinary `git diff --check` exits 1 because the generated OpenAPI snapshot
contains the existing CRLF lines; this is newline-only generated content. The
required `git -c core.whitespace=cr-at-eol diff --check` exits 0. No Git config
or generated newline normalization was changed. The full product, real
database concurrency and dependency/security scanner results are parent gates.
The OpenAPI export emitted unrelated compile warnings from existing shared
modules during `--rerun-tasks`; they had no request linkage or reproduction and
were not changed.
