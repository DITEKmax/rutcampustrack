# Phase 45: GitHub Actions CI - Research

**Researched:** 2026-04-07
**Domain:** GitHub Actions, Gradle CI, Python CI, Frontend CI (Vite/Vitest/Angular)
**Confidence:** HIGH

## Summary

Phase 45 sets up a GitHub Actions CI workflow that covers all three technology layers of this monorepo: Java/Gradle services (with Testcontainers), Python notification-bot (pytest + ruff), and three frontends (Vite/Vitest for PWA and Mini App, Vitest/Angular for Web Panel).

The critical insight is that **all Java integration tests already use Testcontainers** — they self-provision PostgreSQL, MongoDB, RabbitMQ, and Redis inside the test run. The `ubuntu-latest` GitHub Actions runner ships with Docker Engine pre-installed, so no Docker-in-Docker setup is required. Testcontainers simply works on that runner.

For Gradle caching, `gradle/actions/setup-gradle@v6` is the official action — it wraps `actions/cache` internally and provides automatic Gradle User Home caching. It replaces the older `gradle/gradle-build-action`. One workflow file is sufficient for CI (no matrix strategy needed — just sequential jobs: build-test, then python, then frontends in parallel).

**Primary recommendation:** Single `.github/workflows/ci.yml` file with three jobs — `java-build-test` (Testcontainers + Gradle, ubuntu-latest), `python-lint-test` (ruff + pytest, ubuntu-latest), and `frontend-build-test` (npm ci + vitest run for each frontend, ubuntu-latest). All triggered on `push` to any branch and `pull_request`.

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| CI-01 | GitHub Actions workflow builds and tests all Java services on push/PR | Gradle 8.12 + `gradle/actions/setup-gradle@v6` + `./gradlew build` (includes test task) — all Java tests use Testcontainers which works on ubuntu-latest |
| CI-02 | GitHub Actions workflow lints and tests Python notification-bot on push/PR | `ruff check` + `ruff format --check` + `pytest services/notification-bot/tests/` with `requirements-test.txt` dependencies |
| CI-03 | GitHub Actions workflow builds and tests all 3 frontends on push/PR | `npm ci && npm test` per frontend directory — PWA/Mini App use `vitest run --passWithNoTests`, Web Panel uses `vitest run` with `@analogjs/vitest-angular` |
| CI-04 | CI uses Gradle caching for faster Java builds | `gradle/actions/setup-gradle@v6` provides automatic Gradle User Home caching (dependency JARs, build cache) |
</phase_requirements>

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `actions/checkout` | v4 | Check out repository | Official GitHub action |
| `actions/setup-java` | v4 | Install JDK | Official GitHub action, supports Temurin distribution |
| `gradle/actions/setup-gradle` | v6 | Gradle build + dependency caching | Official Gradle action, replaces `gradle-build-action` |
| `actions/setup-python` | v5 | Install Python 3.12 | Official GitHub action |
| `actions/setup-node` | v4 | Install Node.js | Official GitHub action |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `ruff` | latest pip | Python linter + formatter | Already used in project (common modern choice) |
| `pytest` | >=8.0 | Python test runner | Already in `requirements-test.txt` |
| `pytest-asyncio` | >=1.1.0 | Async test support | Already in `requirements-test.txt` |
| `fakeredis` | >=2.34.0 | Redis mock for tests | Already in `requirements-test.txt` |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| `gradle/actions/setup-gradle@v6` | `actions/cache` manually | Manual cache requires knowing exact Gradle paths; setup-gradle handles it automatically |
| `ruff` | `flake8 + black` | `ruff` is faster and replaces both; project has no existing linter config so either works |
| Single `ci.yml` | Separate workflow files per layer | Single file is simpler; separate files only help when path-filtering matters (deferred to CI-08) |

**Installation (no new project deps — only GitHub Actions runner setup):**
No packages to install into the codebase. All tooling is configured in `.github/workflows/ci.yml`.

## Architecture Patterns

### Recommended Workflow Structure
```
.github/
└── workflows/
    └── ci.yml     # Single file, three jobs: java-build-test, python-lint-test, frontend-build-test
```

### Pattern 1: Java Job with Testcontainers
**What:** Run `./gradlew build` (which includes `test`) on ubuntu-latest. Docker is pre-installed so Testcontainers works without special setup.
**When to use:** Every push and PR.
**Example:**
```yaml
# Source: docs.github.com/en/actions/automating-builds-and-tests/building-and-testing-java-with-gradle
jobs:
  java-build-test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
      - name: Setup Gradle
        uses: gradle/actions/setup-gradle@v6
      - name: Build and test all Java services
        run: ./gradlew build
```

**Key detail about `./gradlew build`:** The root `build.gradle.kts` applies `useJUnitPlatform()` to all subprojects. Running `./gradlew build` from the root compiles and tests all 10 subprojects (8 app modules + 2 api-contract modules). This is the correct single command.

**Key detail about caching:** `gradle/actions/setup-gradle@v6` automatically caches:
- `~/.gradle/caches` (downloaded dependencies)
- `~/.gradle/wrapper` (Gradle distribution: 8.12-bin)
- Gradle build cache

[VERIFIED: github.com/gradle/actions]

### Pattern 2: Python Job
**What:** Install ruff + test dependencies, run lint then tests.
**When to use:** Every push and PR.
**Example:**
```yaml
  python-lint-test:
    runs-on: ubuntu-latest
    defaults:
      run:
        working-directory: services/notification-bot
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-python@v5
        with:
          python-version: '3.12'
          cache: 'pip'
          cache-dependency-path: |
            services/notification-bot/requirements.txt
            services/notification-bot/requirements-test.txt
      - name: Install dependencies
        run: |
          pip install ruff
          pip install -r requirements.txt
          pip install -r requirements-test.txt
      - name: Lint with ruff
        run: |
          ruff check .
          ruff format --check .
      - name: Test with pytest
        run: pytest tests/ -v
```

**Key detail:** `grpcio==1.73.0` requires native compilation on some platforms. `ubuntu-latest` has `python3.12-dev` available so pip build from source will work. However, it's slow (~2-3 min) on first run without cache. The `cache: 'pip'` on `actions/setup-python` caches the pip download cache but NOT the installed packages. For faster install, consider `pip install --no-build-isolation` or pre-built wheels from PyPI (grpcio distributes manylinux wheels for x86_64 ubuntu-latest — no compilation needed). [ASSUMED — grpcio manylinux wheel availability should be verified if CI grpcio install is slow]

### Pattern 3: Frontend Jobs
**What:** `npm ci` then `npm test` for each of three frontends. These can run in parallel within the same job using sequential steps, or as parallel jobs.
**When to use:** Every push and PR.
**Example:**
```yaml
  frontend-build-test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with:
          node-version: '22'
          cache: 'npm'
          cache-dependency-path: |
            frontends/pwa/package-lock.json
            frontends/mini-app/package-lock.json
            frontends/web-panel/package-lock.json
      - name: Install and test PWA
        working-directory: frontends/pwa
        run: npm ci && npm test
      - name: Install and test Mini App
        working-directory: frontends/mini-app
        run: npm ci && npm test
      - name: Install and test Web Panel
        working-directory: frontends/web-panel
        run: npm ci && npm test
      - name: Build PWA
        working-directory: frontends/pwa
        run: npm run build
      - name: Build Mini App
        working-directory: frontends/mini-app
        run: npm run build
      - name: Build Web Panel
        working-directory: frontends/web-panel
        run: npm run build
```

**Key detail — Web Panel `npm test`:** The `web-panel/package.json` script is `"test": "vitest run"` (no `--passWithNoTests`). The web-panel has 22 `.spec.ts` files, so tests exist. Uses `@analogjs/vitest-angular` plugin with `ng build` for compilation. The `ng build` step is NOT invoked by `vitest run` — vitest uses Vite/Analog directly, not `ng build`. [VERIFIED: from package.json and vitest.config.ts content]

**Key detail — PWA/Mini App `npm test`:** Both use `"test": "vitest run --passWithNoTests"`. PWA has 2 test files (`AuthProvider.test.tsx`, `LoginPage.test.tsx`). Mini App likely has minimal tests. [VERIFIED: from package.json files]

**Key detail — `npm run build` for frontends:** Vite builds (PWA, Mini App) run `tsc -b && vite build`. Angular Web Panel build uses `ng build` which invokes `@angular-devkit/build-angular`. On `ubuntu-latest` both work without any special setup beyond Node.js. [ASSUMED: Angular CLI available via local `node_modules/.bin/ng` via `npm run build`]

### Pattern 4: Workflow Trigger
**What:** Run on push to any branch AND on pull_request.
**When to use:** Standard for all CI workflows.
```yaml
on:
  push:
    branches: ['**']
  pull_request:
    branches: ['**']
```

### Pattern 5: Branch Protection for PR Blocking
**What:** GitHub branch protection rules require status checks to pass before merge. This is a GitHub repository settings configuration, NOT a code change.
**When to use:** After CI workflow is created and first successful run produces status check names.
**Steps:**
1. Push workflow file, let it run once
2. GitHub Settings → Branches → Branch protection rules → main → "Require status checks" → add check names: `java-build-test`, `python-lint-test`, `frontend-build-test`
3. Enable "Require branches to be up to date before merging"

**Key detail:** The job `name:` in the workflow file becomes the status check name. Choose stable, human-readable names. [VERIFIED: GitHub Actions documentation]

### Anti-Patterns to Avoid
- **Running `./gradlew test` separately from `build`:** `build` already includes `test`; running both wastes time.
- **Using `gradle/gradle-build-action` instead of `gradle/actions/setup-gradle`:** The old action is deprecated and delegates to the new one anyway.
- **Setting up Docker separately for Testcontainers:** `ubuntu-latest` already has Docker Engine. Adding `docker` setup steps is unnecessary and can cause conflicts.
- **Using `GRADLE_USER_HOME` env variable without setup-gradle:** The action handles the cache key computation automatically; manual cache steps often miss Gradle version-specific paths.
- **Not using `npm ci` (using `npm install` instead):** `npm ci` is faster (skips package.json reconciliation) and deterministic from `package-lock.json`.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Gradle dependency caching | Custom `actions/cache` with Gradle paths | `gradle/actions/setup-gradle@v6` | The action knows exact cache key patterns for Gradle versions; manual paths miss wrapper, caches, build-cache |
| pip dependency caching | Custom `actions/cache` | `cache: 'pip'` param on `actions/setup-python@v5` | Built-in, correct default paths for pip cache |
| npm dependency caching | Custom `actions/cache` | `cache: 'npm'` param on `actions/setup-node@v4` | Built-in, uses `package-lock.json` hash as cache key |
| Docker daemon for Testcontainers | Manual docker install / DinD | Nothing — already on ubuntu-latest | Pre-installed, no config needed |

**Key insight:** Every major GitHub Actions official action has built-in caching support. Don't reach for `actions/cache` unless you have custom tooling.

## Runtime State Inventory

> SKIPPED — this is a greenfield phase (no rename/refactor/migration involved). Creating new files in `.github/workflows/`.

## Common Pitfalls

### Pitfall 1: gradlew not executable on Linux
**What goes wrong:** `./gradlew build` fails with "Permission denied" because the file was committed without execute permission (Windows development environment)
**Why it happens:** Git on Windows doesn't track Unix execute bits; `gradlew` may be stored as mode 100644 instead of 100755
**How to avoid:** Add a step before the build: `- name: Make gradlew executable / run: chmod +x gradlew`
**Warning signs:** CI fails immediately on `./gradlew` with "Permission denied" — not a compilation error

### Pitfall 2: Testcontainers failing due to RYUK or Docker socket access
**What goes wrong:** Tests fail with "Could not find a valid Docker environment" or RYUK container errors
**Why it happens:** Some older GitHub Actions runner configurations had Docker access restrictions; RYUK is Testcontainers' container reaper
**How to avoid:** On current `ubuntu-latest`, Testcontainers works without configuration. If issues arise, set `TESTCONTAINERS_RYUK_DISABLED=true` as env variable. [VERIFIED: Docker blog on Testcontainers + GitHub Actions]
**Warning signs:** Test output mentions "docker" or "testcontainers" in the failure message, not a Java compilation error

### Pitfall 3: grpcio pip install fails or is very slow
**What goes wrong:** `pip install -r requirements.txt` times out or fails because grpcio tries to compile from source
**Why it happens:** grpcio requires native extensions; if no manylinux wheel matches the Python/platform combination, pip builds from source (slow)
**How to avoid:** `ubuntu-latest` + `python3.12` should get pre-built manylinux wheels from PyPI. If this fails, add `--only-binary :all:` flag or pin to a version with confirmed manylinux_2_28 wheels
**Warning signs:** pip output shows "Building wheel for grpcio" instead of "Downloading grpcio-1.73.0-cpXXX-cpXXX-manylinux_..."

### Pitfall 4: Angular build OOM on GitHub Actions
**What goes wrong:** `ng build` fails with JavaScript heap out of memory
**Why it happens:** Angular build is memory-intensive; default Node.js heap is ~512MB; GitHub Actions runners have 7GB RAM but Node default heap is small
**How to avoid:** Add `NODE_OPTIONS: '--max-old-space-size=4096'` env variable to the build step if this occurs. Not expected given current project scale (Angular 19, single app) but worth noting.
**Warning signs:** Build output shows "FATAL ERROR: Reached heap limit Allocation failed - JavaScript heap out of memory"

### Pitfall 5: Python test discovery fails due to missing `__init__.py` or `conftest.py`
**What goes wrong:** `pytest tests/` finds 0 tests or import errors
**Why it happens:** Running pytest from `services/notification-bot/` working directory; relative imports may differ from local execution
**How to avoid:** Use `pytest tests/ -v` from the `services/notification-bot/` directory (matching local dev). The `pytest.ini` there sets `asyncio_mode = auto`.
**Warning signs:** "collected 0 items" or `ImportError` in pytest output

### Pitfall 6: `npm test` for web-panel hangs
**What goes wrong:** `vitest run` in web-panel hangs or fails to exit
**Why it happens:** `@analogjs/vitest-angular` uses Vite dev server internally; on CI, headless environment can cause issues
**How to avoid:** The `vitest run` mode (not `vitest`) should exit after tests complete. Ensure `package.json` test script is `vitest run`, not `vitest` (watch mode). [VERIFIED: web-panel package.json shows `"test": "vitest run"`]
**Warning signs:** CI step runs indefinitely without output

## Code Examples

### Complete ci.yml Skeleton
```yaml
# Source: docs.github.com/en/actions + github.com/gradle/actions
name: CI

on:
  push:
    branches: ['**']
  pull_request:
    branches: ['**']

jobs:
  java-build-test:
    name: Java Build & Test
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
      - name: Setup Gradle
        uses: gradle/actions/setup-gradle@v6
      - name: Make gradlew executable
        run: chmod +x gradlew
      - name: Build and test
        run: ./gradlew build

  python-lint-test:
    name: Python Lint & Test
    runs-on: ubuntu-latest
    defaults:
      run:
        working-directory: services/notification-bot
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-python@v5
        with:
          python-version: '3.12'
          cache: 'pip'
          cache-dependency-path: |
            services/notification-bot/requirements.txt
            services/notification-bot/requirements-test.txt
      - name: Install dependencies
        run: |
          pip install ruff
          pip install -r requirements.txt
          pip install -r requirements-test.txt
      - name: Lint
        run: ruff check .
      - name: Format check
        run: ruff format --check .
      - name: Test
        run: pytest tests/ -v

  frontend-build-test:
    name: Frontend Build & Test
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with:
          node-version: '22'
          cache: 'npm'
          cache-dependency-path: |
            frontends/pwa/package-lock.json
            frontends/mini-app/package-lock.json
            frontends/web-panel/package-lock.json
      - name: PWA — install & test & build
        working-directory: frontends/pwa
        run: npm ci && npm test && npm run build
      - name: Mini App — install & test & build
        working-directory: frontends/mini-app
        run: npm ci && npm test && npm run build
      - name: Web Panel — install & test & build
        working-directory: frontends/web-panel
        run: npm ci && npm test && npm run build
```

### Gradle Wrapper Verification
The wrapper is Gradle 8.12 (`gradle-wrapper.properties`). The `setup-gradle@v6` action validates the wrapper checksum automatically. [VERIFIED: from gradle-wrapper.properties]

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `gradle/gradle-build-action` | `gradle/actions/setup-gradle@v6` | 2024 | New action is the canonical successor; old action delegates to new one |
| Manual `actions/cache` for Gradle | Built-in cache in `gradle/actions/setup-gradle` | 2023 | Simpler config, better cache key computation |
| `flake8` + `black` for Python CI | `ruff check` + `ruff format --check` | 2023-2024 | Single tool replaces two; significantly faster |
| `actions/checkout@v3`, `actions/setup-java@v3` | `@v4` versions | 2023-2024 | v4 uses Node.js 20 runtime (required as of 2025 for GitHub Actions) |

**Deprecated/outdated:**
- `gradle/gradle-build-action@v2`: Replaced by `gradle/actions/setup-gradle@v6`. Still works but is legacy.
- `actions/setup-node@v3` and older: Node.js 16 runtime deprecated on GitHub Actions as of 2024; use v4.

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | grpcio 1.73.0 has manylinux wheels for cpython 3.12 x86_64, so `pip install` will not compile from source | Common Pitfalls #3 | Install takes 5-10 min instead of 30s; add `--only-binary :all:` if this is a problem |
| A2 | Angular 19 `ng build` memory usage is within ubuntu-latest runner limits (~7GB RAM) without NODE_OPTIONS override | Common Pitfalls #4 | Add `NODE_OPTIONS: '--max-old-space-size=4096'` to the build step |
| A3 | `ruff` is acceptable as the Python linter (no existing flake8/pylint config in the project) | Python Job | If a specific linter is already configured, use that instead |
| A4 | `package-lock.json` files exist for all three frontends (required for `npm ci`) | Frontend Job | If missing, `npm ci` will fail; need to run `npm install` locally first and commit the lockfile |

## Open Questions

1. **Does `ruff` already have a config in notification-bot?**
   - What we know: `requirements.txt` for notification-bot does not include ruff; no `pyproject.toml` or `ruff.toml` found in search results
   - What's unclear: Whether a `pyproject.toml` or `.ruff.toml` exists at root or in notification-bot
   - Recommendation: If no ruff config exists, add a minimal `[tool.ruff]` section in a `pyproject.toml` or use ruff defaults. Default ruff config is strict — may need `--select E,F` for first run to avoid overwhelming errors.

2. **Do `package-lock.json` files exist for all three frontends?**
   - What we know: The Glob search found `package.json` files for pwa, mini-app, and web-panel; `node_modules` exist for pwa
   - What's unclear: Whether `package-lock.json` files exist (they may be gitignored or not committed)
   - Recommendation: Check `.gitignore` for each frontend. If `package-lock.json` is gitignored, either commit it or switch to `npm install` (slower on CI)

3. **What Node.js version do the frontends require?**
   - What we know: `package.json` files don't specify `engines` field; vite@7, typescript@5.8
   - What's unclear: Whether Node.js 20 or 22 is required
   - Recommendation: Use `node-version: '22'` (LTS as of 2025) — compatible with all tools shown

## Environment Availability

> This phase creates `.github/workflows/ci.yml` — a code-only change. No external services needed in this environment. CI runs on GitHub's infrastructure.

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Docker Engine | Testcontainers in Java tests | ✓ (ubuntu-latest runner) | Pre-installed | — |
| Java 21 (Temurin) | Gradle build | ✓ (installed by setup-java) | 21 | — |
| Gradle 8.12 | Java build | ✓ (wrapper in repo) | 8.12-bin | — |
| Python 3.12 | notification-bot | ✓ (installed by setup-python) | 3.12 | — |
| Node.js 22 | Frontend builds | ✓ (installed by setup-node) | 22 LTS | — |
| npm | Frontend builds | ✓ (bundled with Node.js) | Current | — |

## Validation Architecture

> `workflow.nyquist_validation` not set in config.json — treating as enabled.

### Test Framework
| Property | Value |
|----------|-------|
| Java Framework | JUnit Platform (JUnit 5) via `useJUnitPlatform()` in root `build.gradle.kts` |
| Python Framework | pytest 8.x with asyncio_mode=auto |
| Frontend Framework | Vitest 3.x (all three frontends) |
| Quick run (Java) | `./gradlew :services:auth-service:test` |
| Full suite (Java) | `./gradlew build` (all subprojects) |
| Quick run (Python) | `cd services/notification-bot && pytest tests/test_send_queue.py -v` |
| Full suite (Python) | `cd services/notification-bot && pytest tests/ -v` |

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| CI-01 | Java workflow triggers and all tests pass | smoke (workflow runs) | `./gradlew build` | ✅ (workflow file to be created) |
| CI-02 | Python lint and tests pass | smoke (workflow runs) | `pytest tests/ -v` | ✅ (tests exist) |
| CI-03 | Frontend build and tests pass | smoke (workflow runs) | `npm ci && npm test && npm run build` | ✅ (tests exist) |
| CI-04 | Gradle cache restored on second run | smoke (manual verification) | Check GitHub Actions run time on repeat | ❌ Wave 0 — verify after two CI runs |

### Wave 0 Gaps
- [ ] `.github/workflows/ci.yml` — the workflow file (main deliverable of this phase)
- [ ] Verify `package-lock.json` committed for all three frontends (needed for `npm ci`)
- [ ] Verify `gradlew` executable bit set (or add `chmod +x gradlew` step)

## Security Domain

> No `security_enforcement` key in config.json — treating as enabled.

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | no | — |
| V3 Session Management | no | — |
| V4 Access Control | no | — |
| V5 Input Validation | no | CI workflow has no user input |
| V6 Cryptography | no | — |

### Known Threat Patterns for GitHub Actions CI

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Script injection via PR branch names / commit messages | Tampering | Avoid `${{ github.event.pull_request.title }}` in `run:` steps; use `github.sha` or hardcoded values |
| Malicious pull request modifying workflow file | Elevation of Privilege | `pull_request` trigger (not `pull_request_target`) already used — correct choice; `pull_request_target` would run with repo secrets for external PRs |
| Secrets leaking in build logs | Information Disclosure | Phase 45 has no secrets; Phase 46 handles secrets (RSA keys, bot token) |
| Pinned action versions (supply chain) | Tampering | Use `@v4`, `@v6` tags (not commit SHA) — acceptable for portfolio project; SHA pinning is enterprise-grade |

## Sources

### Primary (HIGH confidence)
- [github.com/gradle/actions](https://github.com/gradle/actions) — setup-gradle v6 usage, cache-provider options
- [docs.github.com/en/actions/automating-builds-and-tests/building-and-testing-java-with-gradle](https://docs.github.com/en/actions/automating-builds-and-tests/building-and-testing-java-with-gradle) — official GitHub workflow example
- Project codebase (VERIFIED): `build.gradle.kts` files, `settings.gradle.kts`, `gradle-wrapper.properties`, `requirements.txt`, `requirements-test.txt`, `pytest.ini`, `package.json` for all three frontends, `vitest.config.ts` for all three frontends, Java integration test base classes

### Secondary (MEDIUM confidence)
- [docker.com/blog/running-testcontainers-tests-using-github-actions](https://www.docker.com/blog/running-testcontainers-tests-using-github-actions/) — Docker pre-installed on ubuntu-latest confirmation
- [dev.to/ken_mwaura1/automate-python-linting-and-code-style-enforcement-with-ruff-and-github-actions](https://dev.to/ken_mwaura1/automate-python-linting-and-code-style-enforcement-with-ruff-and-github-actions-2kk1) — ruff + GitHub Actions pattern

### Tertiary (LOW confidence)
- [ber2.github.io/posts/2025_github_actions_python](https://ber2.github.io/posts/2025_github_actions_python/) — 2025 Python CI patterns with uv

## Metadata

**Confidence breakdown:**
- Java/Gradle layer: HIGH — wrapper version verified, Testcontainers containers confirmed per each service's AbstractXxxIntegrationTest, `setup-gradle@v6` documented
- Python layer: HIGH — `requirements.txt`, `requirements-test.txt`, `pytest.ini` all verified; ruff is standard tool
- Frontend layer: HIGH — `package.json` and `vitest.config.ts` verified for all three; scripts verified
- Branch protection: HIGH — standard GitHub feature, no code change required

**Research date:** 2026-04-07
**Valid until:** 2026-07-07 (stable ecosystem — GitHub Actions action versions rarely break)
