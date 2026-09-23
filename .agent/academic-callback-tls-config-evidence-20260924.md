# Academic callback gRPC TLS source fix

Date: 2026-09-24
Branch: `codex/academic-callback-tls-20260923`
Frozen source base: `fd1fac8bce8096b6dcf036b8b98bbb6dbf00637c`

## Goal and scope

The replacement UI's Academic callback returned HTTP 503 because Schedule's
Academic gRPC channel used plaintext while `ServiceTokenCallCredentials`
requires an authenticated TLS transport. This source-only change enables the
Academic gRPC server's prod TLS endpoint and moves every existing Academic
client in scope to TLS with explicit trust material and authority.

Changed product files:

- `.env.prod.example`
- `docker-compose.e2e.yml`, `docker-compose.prod.yml`
- `services/academic-service/academic-app/Dockerfile`
- `services/schedule-service/schedule-app/Dockerfile`
- `services/academic-service/academic-app/src/main/resources/application-prod.yml`
- `services/schedule-service/schedule-app/src/main/resources/application-prod.yml`
- `services/attendance-service/attendance-app/src/main/resources/application-prod.yml`
- `services/mobile-bff/mobile-bff-app/src/main/resources/application-prod.yml`
- `services/notification-bot/bot/{config.py,__main__.py,grpc_client/academic_client.py}`
- `services/notification-bot/tests/test_academic_client.py`
- `tests/e2e/infra/scripts/generate-test-certs.sh`

## Independent review correction

The first Sol review found that e2e private keys were mode `0600` but owned by
the Linux runner, while both images run as non-root `app`; therefore the TLS
servers could not read their bind-mounted keys. Both images now use stable,
distinct `app` identities: Academic `10001:10001`, Schedule `10002:10002`.
E2E key provisioning changes ownership and mode only on the two generated
server key files on Linux, verifies the exact UID:GID and `0600`, and fails
closed if that cannot be confirmed. It uses non-recursive `chown`/`chmod`,
with non-interactive sudo only when ordinary permissions do not suffice.
Existing key files receive the same check. Windows Git Bash verifies actual
container readability without changing host key permissions; other platforms
fail closed. Production guidance documents owner and mode; no production key
is generated or modified.

The Requests runner was prepared in its separate owned worktree with an
ephemeral Academic certificate, isolated key directory, Academic-only server
key mount, and read-only Academic trust-certificate mounts for Schedule,
Attendance, and Mobile BFF. This preparation is not runtime evidence.

The Windows Git Bash correction keeps the Linux owner/mode checks unchanged.
For `MINGW*`/`MSYS*`, each generated gRPC server key is checked separately in
the existing `eclipse-temurin:21-jre-alpine` runtime image using a single
read-only bind mount and the corresponding numeric service UID:GID. The probe
has no network, a read-only root filesystem, dropped capabilities, and
redirects the key read to `/dev/null`. `cygpath -am` plus
`MSYS_NO_PATHCONV`/`MSYS2_ARG_CONV_EXCL` keep the Windows source path intact.
It does not pull images or change host key permissions, and fails closed if
Docker Desktop, `cygpath`, or the local helper image is unavailable. macOS and
other platforms are not claimed as supported.

## Acceptance criteria

- Academic's prod gRPC server requires its configured certificate chain and
  private key; no prod plaintext fallback is configured.
- Schedule, Attendance, and Mobile BFF Academic clients require TLS, the
  Academic trust certificate, and `academic-service` authority.
- The notification bot uses a secure channel when its prod TLS flag is on and
  fails startup if its CA path is absent or invalid; the existing directed
  gRPC metadata remains intact.
- E2E creates a certificate with DNS SAN `academic-service`. Compose mounts
  each server key only into that server; clients receive only the relevant
  public certificate. Prod asset paths are required inputs.

## Evidence and checks

Prior runtime evidence on the frozen base is in
`C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/worktrees/v2-l5b-retrospective-union/.agent/student-role-orchestrator/requests-runtime/evidence/headman-replacement-diagnostic-fd1fac8b-20260923.md`.
The sanitized Schedule exception identifies
`UNAUTHENTICATED: Service identity requires authenticated TLS` at
`AcademicGrpcClient.getPreparedAssignmentCloseOperation`; the gRPC call
credentials reject the plaintext channel before token issuance or delivery to
Academic.

- Targeted Python client tests, project venv, scoped `--basetemp`, coverage
  disabled for this single-file run: exit 0, **8 passed**.
- `python -m py_compile bot/grpc_client/academic_client.py bot/config.py
  bot/__main__.py` (project venv): exit 0.
- `ruff check bot/grpc_client/academic_client.py bot/config.py
  tests/test_academic_client.py`: exit 0, all checks passed.
- Python YAML parse for both Compose files and four changed Spring prod
  profiles: exit 0.
- Git Bash `bash -n tests/e2e/infra/scripts/generate-test-certs.sh`: exit 0.
- `git diff --check` scoped to the changed product files: exit 0.
- After the key-ownership correction, Git Bash `bash -n` for the certificate
  generator: exit 0; scoped `git diff --check`: exit 0.
- After the Windows helper correction, `& 'C:\Program Files\Git\bin\bash.exe'
  -n tests/e2e/infra/scripts/generate-test-certs.sh` (PowerShell, product WT):
  exit 0; `git diff --check -- tests/e2e/infra/scripts/generate-test-certs.sh
  docs/testing/e2e-testing.md .agent/academic-callback-tls-config-evidence-20260924.md`:
  exit 0. The docs state the local helper-image prerequisite for Windows Git Bash.
- A read-only Git Bash path calculation exited 0 and resolved `SCRIPT_DIR` to
  `tests/e2e/infra/scripts` and `CERTS_DIR` to `tests/e2e/infra/scripts/../certs`.
  An unprivileged generator invocation exited 1 at its initial `mkdir -p` with
  permission denied on `/c/Users/maksd`, before certificate creation. After
  the authorized elevated retry, the generator created the ignored local e2e
  Nginx and Schedule cert/key fixtures, then exited 1 at
  `docker image inspect busybox:1.36.1` because that image was not present.
  No helper container started in that attempt. The helper was then retargeted
  to the locally present application runtime image, `eclipse-temurin:21-jre-alpine`.
- One subsequent elevated Git Bash generator invocation with the retained
  fixtures exited 0 and reached `Done`. It ran the no-network, read-only
  helper twice: Schedule key readable as `10002:10002`, Academic key readable
  as `10001:10001`; the Academic cert/key fixture was created in that run.
  The only Docker containers were the two `--rm` key-read helpers; no app stack
  or build was started. Key contents were not read or printed. The ignored
  Nginx/Schedule/Academic fixtures are retained for the upcoming acceptance.
- After runner adaptation, PowerShell AST parse of `runner.ps1` and its scoped
  `git diff --check`: exit 0.
- Source cross-check of Dockerfile IDs and e2e/prod certificate paths/authority:
  exit 0.
- An initial targeted pytest attempt using the system Python exited 1 because
  pytest was not installed there. The project venv run initially hit the
  Windows default temp-directory permission and single-file coverage threshold;
  rerunning with a workspace-local basetemp and `--no-cov` passed. Its generated
  temp directory and coverage report were removed.
- `bash -n` through the Windows WSL alias exited 1 because WSL is unavailable;
  the exact Git Bash syntax check above passed.

## Runtime status and limitations

Correction commit `b9008478d25fd2572d5bba89aa8caeb054212da6` plus the pending
image-retarget delta is awaiting scoped independent Sol recheck. Windows key
readability is **PASS** for both gRPC server identities via the isolated helper
described above. No build or replacement application runtime lifecycle was run
for this correction; those remain pending review/integration and a separate
HEAVY lease. Only ignored local e2e fixtures were generated. No production
certificate, private key, deployment, or rotation was created or performed.

The existing `docker-compose.e2e.yml` and `docker-compose.prod.yml` do not define
a Mobile BFF service. Its prod profile is TLS-configured here; environment and
certificate mounts for its separately packaged runtime remain outside these
Compose files and are not claimed as validated.

The working tree also contains two restored orchestration-document edits and
the untracked `.agent/teacher-replacement-ui-20260923/source.diff`; both are
pre-existing foreign work and are excluded from this change.
