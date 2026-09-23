# Mobile BFF release packaging

Date: 2026-09-24

Branch: `codex/teacher-word-export-20260923`

Worktree base before this packaging delta: `85c89e4f3f9084d5304c5ceee14aec032fd41b7a`

## Goal and scope

Package the existing Mobile BFF for the production and e2e Compose stacks so
the shared PWA/TMA Gateway can reach it at `http://mobile-bff:9080`. Add the
image to the existing production build, SBOM, and signature-verification
lists. This task does not change BFF Java/API behavior, the Gateway routes,
the deployment framework, or production state.

## Contract and acceptance criteria

- Build the existing `:services:mobile-bff:mobile-bff-app` boot jar with the
  repository's Java 21 / Gradle wrapper pattern; run the runtime image as a
  non-root user and expose its configured HTTP port 9080 with an actuator
  health check.
- Define `mobile-bff` in both production and e2e Compose, attached only to
  `private_net`, with no host-published port. Gateway starts after the BFF is
  healthy; BFF waits for healthy Auth, Academic, Schedule, and Attendance
  services without depending on Gateway.
- Preserve BFF downstream defaults from `application.yml`: Auth HTTP 9090,
  Academic gRPC 19091, Schedule gRPC 19092, and Attendance gRPC 19093.
  Production Academic and Schedule channels use their existing TLS profile;
  BFF mounts only the corresponding public `.crt` files read-only and gets
  no private key.
- The same Mobile BFF image is included in build/push, SBOM generation, and
  signature verification. Existing whole-Compose `pull` and `up --wait`
  commands include it without adding explicit service lists.

## Changed files

- `services/mobile-bff/mobile-bff-app/Dockerfile` — Java 21 builder, selective
  Gradle project inputs, layered JRE runtime, non-root user, port 9080 and
  health check.
- `docker-compose.prod.yml` — production BFF service and Gateway health
  dependency; TLS trust certificate mounts only.
- `docker-compose.e2e.yml` — e2e BFF service and Gateway health dependency;
  TLS trust certificate mounts only; backend count corrected to six.
- `.github/workflows/deploy.yml` — build/push action, SBOM image matrix, and
  signature verification list now all cover 13 images.
- `.agent/mobile-bff-release-packaging-20260924.md` — this scoped record.

`.env.prod.example` is unchanged: the required TLS directory variables,
Academic TLS authority, and `GRPC_SECRET` already exist there.

## Evidence and checks

- Python/PyYAML 6.0.3 static config check parsed both Compose files, both BFF
  Spring configurations, Gateway configuration, and the deploy workflow;
  asserted service ports/networks/dependencies/health, no Gateway cycle,
  public-certificate-only read-only mounts, configured downstream ports,
  and identical 13-image membership across build, SBOM, and signature
  verification. It also confirmed the deploy `pull` and `up --wait` commands
  have no explicit service arguments. Exit code: **0**.
- Dockerfile source audit confirmed every local `COPY` input exists. Exit
  code: **0**.
- `git diff --check -- .github/workflows/deploy.yml docker-compose.prod.yml
  docker-compose.e2e.yml services/mobile-bff/mobile-bff-app/Dockerfile`.
  Exit code: **0**. Git reports its configured LF-to-CRLF notice for the two
  Compose files and the workflow.
- Read-only source cross-check confirmed the existing Gateway route default
  points to `http://mobile-bff:9080`, the BFF application binds to 9080, and
  the prod profile selects TLS for Academic and Schedule.

## Runtime evidence and limitations

No Docker image build, container startup, Gateway request, authentication
request, or deployment was run in this task. The HEAVY runtime lease was held
by another task; the package build and end-to-end route/auth-path evidence
remain pending a lease callback. Do not describe this change as runtime
validated or production-ready until those checks pass. No production `.env`
file, production credential, certificate contents, or private key was
inspected.

## Diff boundary

The scoped product diff is limited to the four changed files listed above;
this note is scoped evidence. Pre-existing edits to
`.agent/orchestration-v2/RULES.md` and `LEAF-PACKET.md` were preserved and are
not part of this change.
