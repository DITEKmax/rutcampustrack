# Shared shell + Homework integration packet

Status: frozen integration contract; S3; fresh Luna max leaf; no children.
Baseline: `8002b9ea4356b10779c5bb9a6d99746d32d78ae2`.
Target: `.agent/worktrees/student-role-02/integration`.

## Goal

Integrate the accepted shared shell foundation and Homework API/date delta into this clean integration checkout and produce a verified revision for the shared Homework UI. Do not integrate main or the requests domain.

## Context/evidence

Root acceptance dated 2026-09-07 freezes 12 paths from `shared-shell-final-manifest.json` and 30 paths from the 31-file Homework preservation manifest after excluding `docs/openapi/academic.json`. `shared-shell-review-2.md`, `homework-api-review-2.md`, and `homework-contract-review-3.md` are accepted PASS artifacts. The one LOW SHA typo in the writer-local Homework evidence manifest is excluded; canonical root hashes are authoritative. Source worktrees are siblings `shared-shell` and `homework-api`, both at the baseline revision.

## Relevant scope

Only the 42 paths listed in `manifest.json` are integration-owned: 12 shared shell paths and 30 Homework paths. The target checkout is the sole writer. Evidence under this directory is integration bookkeeping. Main and source worktrees are read-only.

## Required behavior

Copy the exact accepted union after source hash checks, preserve all other work, and retain canonical source bytes. Keep the shell `package.json` outside the integration. Exclude the raw-only Academic OpenAPI residue. Keep the Java-first OpenAPI/generated TypeScript boundary, shared navigation/host behavior, Homework date/completion API, and typed fixtures from the accepted source worktrees.

## Constraints

No UI implementation, requests/domain files, dependency/config/lockfile changes, compatibility layer, cleanup/reset, cherry-pick of unknown commits, deploy, production data, secret handling, or main checkout writes. Do not silently regenerate unrelated contracts. Use explicit paths for staging and commit.

## Existing patterns

Java-first source produces canonical OpenAPI and generated TypeScript. The mobile core uses typed `StudentApi`, fixture transport, Vue shell navigation/host boundaries, and semantic PCSS tokens. The backend owns authz and transaction behavior. Existing full-lint baseline noise at `fixture-transport.test.ts:94` is compared against the accepted fixture delta; it is not a reason to modify unrelated files.

## Acceptance criteria

All 42 canonical source hashes match their destinations; source worktrees remain unchanged; the target product diff contains exactly those 42 paths; no excluded file or package metadata is copied; the union typechecks, lints, contracts, fixtures, targeted backend tests, and both PWA/TMA Vue builds pass; generated contract drift is absent; a local explicit-path commit identifies the frozen integration revision. This is not a full student-role, security, requests, or downstream UI acceptance.

## Verification

Record exact commands, exit codes, environment, revision and evidence in `checks.json` and `runtime-evidence.md`. Run generated drift, fixture validation, core contract/navigation tests, foundation/workspace typecheck and lint, both Vue builds, focused Academic/BFF unit checks, and the two accepted Homework runtime ITs. Use a CRLF-aware Git whitespace check to preserve canonical OpenAPI bytes. Recheck source/destination hashes and product scope after tests and before/after the explicit commit.

## Do not

Do not copy `docs/openapi/academic.json`, `frontends/mobile-core/package.json`, requests worktree content, unrelated docs/config/dependencies, or build output. Do not claim full-role/security PASS, run redundant full Academic/Mongo suites, or change code in response to unrelated WARN/ERROR output.
