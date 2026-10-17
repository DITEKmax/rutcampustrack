# Next checks and formatting ownership proposal

No commands below executed. All heavy/runtime/product work awaits root assignment. Reference finite-runtime-packet.md for client acceptance; scenarios.csv contains 41 grouped source-mapped scenario families, not a readiness denominator.

## Formatter inventory and minimal plan

| Scope | Existing mechanism | Minimal proposed implementation/check | Writer and limits |
|---|---|---|---|
| Java source/tests | Gradle21 toolchain, no Spotless/Checkstyle/PMD formatter found in root build/scripts/workflows | Pin one Java21-capable formatter; add root Gradle format/formatCheck tasks (e.g. Spotless+google-java-format/AOSP matching 4-space Java); first dry-run representative record/switch/controller/test files; one mechanical commit | Sole formatter config writer + Java mechanical writer/worktree; no generator output; compileJava+compileTestJava for affected modules, targeted tests only if semantics risk |
| Vue/TS/JS/HTML/CSS/PCSS | Mobile ESLint recommended + TS/Vue parser; Angular EditorConfig only; no Prettier/format scripts | Pin Prettier, single quote/no semicolons for mobile existing style, preserve Angular semicolon style with directory override; Vue htmlWhitespaceSensitivity=strict; explicit .pcss css parser override (PostCSS mixins smoke build required); add format/format:check using curated tracked path manifest | One owner for root/mobile formatter configs and frontends/package-lock.json; mechanical source work separate from UI behavior. No ESLint weakening; style should not alter rem/tokens/template semantics |
| Python notification-bot | services/notification-bot/pyproject.toml Ruff py312 line-length120; CI ruff check + format --check | Reuse Ruff config; reproducible pinned command/dependency if needed, no second Black formatter | Python writer; generated *_pb2 excluded; ruff check+format --check and existing bot tests for touched behavior |
| Python root scripts/tests/load | No discovered project Ruff config outside bot | Minimal root per-scope Ruff format config, preserve existing accepted script style, exclude machine fixtures; dry-run bounded scripts before apply | Shared formatter config owner; do not silently apply bot lint rules globally; parser/targeted recovery fixture checks when relevant |
| SQL non-Flyway/testing | No SQL formatter found | Pin PostgreSQL-aware formatter only for eligible scripts and new unpublished migrations, preserve PL/pgSQL syntax; compare parser/DB checks as relevant | SQL owner; fixture/seed exactness reviewed. 87 tracked Flyway migration files require historical checksum guard, not blind rewrite |
| Published Flyway V*.sql | Existing clean/upgrade+validate receipts, checksummed source | Protect byte hashes; existing migration SQL may already be readable; report compressed historical scripts and route any change through explicit compatibility plan. Never repair/clean DB to accept cosmetic changes | Shared migration owner + root reconciliation. No blanket formatting before checksum disposition |
| Kotlin build, YAML/JSON/TOML/XML/proto/properties, shell/PowerShell | ShellCheck warnings/errors for scripts/*.sh; dashboard JSON structure; typed builds/proto generation; no generic formatter | Extend formatter only for authored relevant configs with reliable parser; pinned ktfmt/XML/proto/shfmt only if real compressed sources justify new tools; managed lockfiles not formatter input | Config/proto sole writer; preserve YAML `${...}`, nginx/shell quoting; no semantics-changing lint refactor |

Required existing mobile commands from /workspace/rutcampustrack/frontends, once writer assigned:

```bash
npm run lint
npm run generate:types:check
npm run generate:auth-types:check
npm run generate:academic-schedule-types:check
npm run typecheck
npm run build
```

The generation checks matter only if formatting interacts with generated contracts/generator inputs; do not format or regenerate unchanged generated files. Existing runtime unit suite is `npx vitest run`; CI mobile job runs contract/typechecks/worker/build and Coverage job runs Vitest, but mobile CI does not currently run npm run lint. Add actual lint/format gate only through assigned config owner; current owner contract requires them on final changed author sources. `npm run lint` delegates workspace ESLint scripts; scripts use `--max-warnings=0`.

Notification-bot from its directory: `ruff check .`, `ruff format --check .`; actual CI installs unpinned ruff then requirements and executes pytest. Preserve existing test scope; adding pinned Ruff should follow normal dependency/tool config ownership.

## Curated path exclusions — review before formatting

tracked-language-inventory.json is a machine-readable triage of 6297 tracked files. Raw/candidate/exclusion counts are inventory, not runtime/acceptance. Conservative fixture/snapshot substring matches are review buckets, not automatic permanent exclusions. Verify actual handwritten files in each bucket before deciding (e.g. a source model named Snapshot is not an exact fixture). Historical docs/archive/research/.agent source copies need provenance disposition; don't apply new production style to byte-exact archived copies.

Exclude generated API/schema, protobuf Python stubs, node_modules/vendor, target/build, generated assets/precache lists, binary images/fonts, machine logs/results and lockfiles. Preserve intentionally exact snapshots/fixtures. `frontends/landing/dist` is explicitly canonical handwritten HTML/CSS/JS per landing/package.json; this exception must override blanket **/dist/** exclusion for authored files while generated og images remain excluded. Its initial CSS includes legacy px; formatting must preserve units rather than convert accepted style opportunistically. Existing mobile PCSS uses separate files, rem lengths, tokens, PostCSS mixins; formatter parse/build check must cover @define-mixin/@mixin before mass apply.

readability-hotspots.json provides exact candidate files/lines with >300-character lines. This is triage, not a hard style rule: base64/URLs/fixtures may be intentional. Examples of plainly compressed source groups: admin-access-client.ts, shared/admin-authority.ts, headman lesson management screen/state tests, headman-homework-screen.pcss, teacher screen styles. Avoid changing architecture just to satisfy line-length.

## Remaining source/prerequisite checks

- rutcampustrack-design current skill not present in executor skills catalog or /workspace/.agents search. No executable skill package found in repository. Archive SKILL.md.inactive and design reference docs are source material, not a substitute for the mandatory current skill. Root may restore accepted skill bytes/path from source manifest/handoff. UI defect repair alone depends on this; independent runtime harness/mechanical formatting continues.
- Historical provider acceptance mentioned by original .agent/orchestration-v2/CURRENT.md must be linked to actual dated immutable receipt/revision/scope; synthetic Telegram host smoke verifies backend seam, not provider launch.
- Q001 accepted D002: offline read-only last confirmed STUDENT schedule/homework until online validation; no offline write/attachment/TMA. No owner question remains on this policy.
- Q002 answered D003: reusable signed initData within86400s with fresh live authority, no same-session promise. Malformed/expired/future/live-binding verification remains required. D004 excludes only map icon catalogue/legend pending separate accepted meanings.
- Existing scripts/local-stand-acceptance.mjs explicitly targets private localhost:18514/LOCALAPPDATA and must not be run/copied over owner's private stand. Use task-owned E2E compose/seed instead.

## Concrete recommended tool pins

Recommended first stack, without installing a competing UI/style framework:

- Gradle Spotless7.0.4 + google-java-format1.25.2 in AOSP mode (`googleJavaFormat("1.25.2").aosp()`), which uses four-space Java indentation. Use existing JDK21/Gradle8.12; do not alter JVM/toolchain just for formatter. Names: conventional `spotlessApply`/`spotlessCheck` plus owner-requested `format`/`formatCheck` aliases if useful. Initial Java formatter source targets authored src/main/java and src/test/java, excludes build/generated, baseline migration contents protected.
- Prettier3.6.2 as exact mobile devDependency. Settings: tabWidth2, singleQuote true, semi false for Vue/mobile TS/MJS, printWidth100, htmlWhitespaceSensitivity strict, singleAttributePerLine true; Angular/web-panel/tests-e2e TS retain existing semicolon style through path override. CSS parser explicitly for `*.pcss`; Prettier formats syntax only, does not convert rem/pixels/tokens. Test representative @define-mixin/@mixin and Vue conditional slot/text whitespace through PostCSS/Vue build+ESLint before bulk apply. Do not add eslint-config-prettier or disable recommended rules merely for green lint.
- Ruff0.11.13 exact pin for Python, reusing bot pyproject py312/line-length120 and E/F/W/I configuration. Root scripts get scoped format settings; no global imposition of bot lint selectors on unrelated files.
- pgFormatter5.6 (`pg_format --spaces 4`, matching SQL indentation) only for authored nonhistorical PostgreSQL SQL after PL/pgSQL roundtrip/sample review. Versioned Flyway baseline SQL remains byte-protected; no new formatter dependency needed merely to check hashes. Whitespace-only check can format to temporary output and compare without rewriting source; authored seeds/fixtures require exactness disposition before inclusion.
- Prettier can also format authored JSON/YAML/Markdown configs selected by curated inventory; do not include machine source ledgers/fixtures/archives/lockfiles by blanket extension. Existing ShellCheck remains; no extra shell/Kotlin/XML formatter until actual compressed scope needs it.

Registry metadata read-only checks succeeded: npm prettier3.6.2 requires Node>=14; PyPI ruff0.11.13 Python>=3.7; Maven google-java-format1.25.2 POM and Gradle Plugin Portal Spotless7.0.4 marker POM return200; GitHub pgFormatter v5.6 published2025-03-17. Exact public metadata and URLs in formatter-version-metadata.json. This validates available pins, not local parser/lint/build compatibility or formatter PASS. First query under default sandbox had EPERM; retry with explicit network capability succeeded through configured proxy, no bypass/secret access.
