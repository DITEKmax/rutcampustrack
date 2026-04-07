---
phase: 45-github-actions-ci
plan: 01
subsystem: ci
tags: [github-actions, gradle, ruff, pytest, npm, ci-pipeline]
dependency_graph:
  requires: []
  provides: [ci-pipeline]
  affects: [all-services, notification-bot, frontends]
tech_stack:
  added: [github-actions, ruff]
  patterns: [parallel-ci-jobs, gradle-caching, ruff-lint-format]
key_files:
  created:
    - .github/workflows/ci.yml
    - services/notification-bot/pyproject.toml
  modified:
    - services/notification-bot/bot/**/*.py (35 files — ruff format/lint fixes)
    - services/notification-bot/tests/**/*.py (35 files — ruff format/lint fixes)
decisions:
  - Three parallel CI jobs (no needs:) covering Java, Python, Frontend
  - gradle/actions/setup-gradle@v6 for automatic Gradle dependency + build cache
  - ruff with select E,F,W,I and per-file-ignores for protobuf-generated files
  - pull_request trigger (not pull_request_target) — fork PRs cannot access secrets
  - chmod +x gradlew step because Windows dev environment stores gradlew as 100644
metrics:
  duration: ~10 minutes
  completed: "2026-04-07"
  tasks_completed: 2
  tasks_total: 2
  files_created: 2
  files_modified: 37
requirements_satisfied: [CI-01, CI-02, CI-03, CI-04]
---

# Phase 45 Plan 01: GitHub Actions CI Summary

GitHub Actions CI workflow with three parallel jobs (Java/Gradle, Python/ruff/pytest, Frontend/npm) triggered on every push and pull request. Python notification-bot codebase brought to full ruff lint and format compliance.

## What Was Built

### `.github/workflows/ci.yml`

Single workflow file with three independent parallel jobs:

| Job | Runner | Key steps |
|-----|--------|-----------|
| `java-build-test` | ubuntu-latest | checkout, setup-java@v4 (Temurin 21), setup-gradle@v6, chmod +x gradlew, ./gradlew build |
| `python-lint-test` | ubuntu-latest | checkout, setup-python@v5 (3.12, pip cache), install ruff+deps, ruff check, ruff format --check, pytest tests/ -v |
| `frontend-build-test` | ubuntu-latest | checkout, setup-node@v4 (22, npm cache), npm ci && npm test && npm run build for pwa/mini-app/web-panel |

All three jobs run in parallel (no `needs:` dependency). A failure in any job fails the workflow.

### `services/notification-bot/pyproject.toml`

Ruff configuration:
- `target-version = "py312"`, `line-length = 120`
- `select = ["E", "F", "W", "I"]` — pycodestyle errors, pyflakes, warnings, isort
- `per-file-ignores` for `bot/grpc_client/*_pb2.py` and `*_pb2_grpc.py` — suppresses import ordering and unused import false positives in generated protobuf code

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Python codebase had 21 ruff lint/format violations**
- **Found during:** Task 2 validation
- **Issue:** 21 ruff errors across bot/ and tests/ — unused imports (F401), unused variable (F841), import ordering (I001), formatting violations across 35 files
- **Fix:** `ruff check --fix .` auto-fixed 20 issues (unused imports, import ordering); manually removed dead `text=` variable assignment in test_lesson_started.py; `ruff format .` reformatted 35 files (quote style, trailing commas, whitespace)
- **Files modified:** 37 Python files in `services/notification-bot/`
- **Commit:** 326a295
- **Behavioral impact:** None — formatting and import cleanup only, no logic changes. Generated protobuf files' import ordering was fixed by auto-fix (the per-file-ignores suppresses the I001 rule on CI but auto-fix still applies it locally — the files remain valid)

## Security Review

Threat model mitigations applied as designed:
- T-45-01: No `${{ github.event.* }}` interpolation in any `run:` block — verified
- T-45-02: `pull_request` trigger used (not `pull_request_target`) — fork PRs cannot access repo secrets
- T-45-03: Using `@v4`/`@v6` major version tags — accepted risk for portfolio project
- T-45-04: No secrets configured in Phase 45 — no disclosure risk

## Known Stubs

None — CI workflow is complete and functional.

## Threat Flags

None — `.github/workflows/ci.yml` does not introduce new network endpoints, auth paths, or file access patterns beyond the CI runner environment.

## Verification Results

All acceptance criteria satisfied:
- `.github/workflows/ci.yml` exists and is valid YAML
- Three parallel jobs: `java-build-test`, `python-lint-test`, `frontend-build-test`
- Triggers on `push: branches: ['**']` and `pull_request: branches: ['**']`
- Java job uses `gradle/actions/setup-gradle@v6` (CI-04 caching), `chmod +x gradlew`, `./gradlew build`
- Python job uses `working-directory: services/notification-bot` via defaults, setup-python@v5 with pip cache
- Python job runs `ruff check .`, `ruff format --check .`, `pytest tests/ -v`
- Frontend job runs `npm ci && npm test && npm run build` for all 3 frontends with npm cache
- `services/notification-bot/pyproject.toml` contains `[tool.ruff]` with `target-version = "py312"`
- `ruff check .` passes locally with exit 0
- `ruff format --check .` passes locally with exit 0
- No `${{ github.event.* }}` interpolation in `run:` steps

## Commits

| Hash | Message |
|------|---------|
| 6fc75ba | feat(45-01): add GitHub Actions CI workflow and ruff config |
| 326a295 | fix(45-01): apply ruff lint and format fixes to notification-bot |

## Self-Check: PASSED

- `.github/workflows/ci.yml`: EXISTS
- `services/notification-bot/pyproject.toml`: EXISTS
- Commit 6fc75ba: FOUND
- Commit 326a295: FOUND
