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
server key files, verifies the exact UID:GID and `0600`, and fails closed on
platforms where Linux ownership cannot be confirmed. It uses non-recursive
`chown`/`chmod`, with non-interactive sudo only when ordinary permissions do
not suffice. Existing key files receive the same check. Production guidance
documents owner and mode; no production key is generated or modified.

The Requests runner was prepared in its separate owned worktree with an
ephemeral Academic certificate, isolated key directory, Academic-only server
key mount, and read-only Academic trust-certificate mounts for Schedule,
Attendance, and Mobile BFF. This preparation is not runtime evidence.

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

The correction commit is awaiting scoped independent Sol recheck. New build
and runtime validation are **NOT RUN**. The parent gate requires review and
integration before allocating the next build/runtime lifecycle. No production
certificate, private key, deployment, or rotation was created or performed.

The existing `docker-compose.e2e.yml` and `docker-compose.prod.yml` do not define
a Mobile BFF service. Its prod profile is TLS-configured here; environment and
certificate mounts for its separately packaged runtime remain outside these
Compose files and are not claimed as validated.

The working tree also contains two restored orchestration-document edits and
the untracked `.agent/teacher-replacement-ui-20260923/source.diff`; both are
pre-existing foreign work and are excluded from this change.
