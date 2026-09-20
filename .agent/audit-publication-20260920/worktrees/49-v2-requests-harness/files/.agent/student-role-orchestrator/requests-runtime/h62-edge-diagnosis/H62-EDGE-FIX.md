# H62 edge fix handoff

Дата: 2026-09-19. Риск: S3. Статус: authorized minimal correction implemented; pure check PASS including both production outer failure cases; actual full runtime remains a root-owned gate.

Пакет: `REQUESTS-H62-EDGE-FIX.md`, latest full-review amendment SHA256 `25f8632b8a9debc63e8d2ee1bb79a4c2ceff57dec5214938c28ec8601da1418c`.
Правила: `RULES.md`, SHA256 `b256a175274987da9710d804b3c050a5dbcb47d8448cc03644d74168b52a437a`.
Frozen baseline runner: `2b47da9105461eb3c9d11fae7ca92b319a7b8695725f665d96d8f03930ccd9a0`.
Union revision: `426a15b6b42e816deaa3ca5c50437e0964aaf85e`; checkout owner remains the H62 requests harness.

## Compact contract

### Goal

Исправить только disposable edge-подготовку H62: убрать BOM из двух runtime Nginx-конфигов, устранить зависимости от отсутствующих UI-контейнеров и сохранить bounded evidence при ошибке запуска до `finally` cleanup.

### Context/evidence

- H62 report `runs/20260919-190530008-e9xjq42a/report.json` (`c677c560181922daecea204d012e9fc7747a4ed714964dbf4b03734a706b8590`) имеет `status=FAIL`, `runtime=FAIL`; все Java readiness прошли, edge IP assertion получил пустой адрес, cleanup 12 containers/network/keys/artifacts прошёл.
- Сгенерированный H62 `nginx.conf` начинался байтами `239 187 191`; это следовало из `[Text.Encoding]::UTF8` в прежних двух config writers.
- H65 pinned `nginx -t` с `network=none` дал exit `1`: `unknown directive "﻿worker_processes"` (`h65-nginx.stderr.log`, SHA256 `abd20dac84f84895a08f2653d4546ce35894675ced499b127a2f8f61bb44e00b`; result SHA256 `ec80fd89bff2b044d4872b20ca79a9e9ee3f160ba4b867946630f9006abd844e`).
- H66 удалил только начальный `EF BB BF` из копий и дал exit `1`: `host not found in upstream "landing-nginx"` на `default.conf:76` (`h66-nginx.stderr.log`, SHA256 `20087d762540d8eb186c0d82ee3ac50e81d027f0e3611c69c11c158e9a44e00b`; result SHA256 `f2e2d1a11399e809a7ca57933c58b34eee650306edf262302c28b223e120f4cb`). Оба диагностических контейнера удалены и проверены отсутствующими.

### Relevant scope

- `requests-runtime/runner.ps1` — единственный product-adjacent writer в этой области.
- `h62-edge-fix-check.ps1`, `h62-edge-fix-evidence.json`, fixture и этот handoff — только собственная H62 область.
- Before correction, runner SHA256 was `13aafbf6f6f437ab75910abf2feae79d3d2e710443efe4f77c6816986445b99a`; corrected runner SHA256 is recorded in current evidence. The prefixed failing evidence copy is `h62-edge-fix-evidence.outer-failure.json`, SHA256 `f2c0a09be9e390a478db89461528990da2148bdd91680b5d1e42922ccb068c3c`.
- Приёмочный `tests/e2e/infra/nginx` union checkout и H62/H65/H66 артефакты не изменялись.

### Required behavior

- `New-RuntimeConfig` пишет `nginx.conf` и `default.conf` через явный `[Text.UTF8Encoding]::new($false)`; JSON writer и ASCII TLS writer не менялись.
- `Convert-EdgeToRuntimeConfig` принимает ровно ожидаемые source-блоки. `/presentation/` и `/mini-app/` становятся локальным `404`; `/app/` переписывает префикс и отдаёт существующий mounted PWA через локальные `root`/`try_files`; `/` остаётся static PWA.
- API и WebSocket маршруты, global `2m` cap, excuse `24m` cap, inherited buffering, `no-store`, canonical forwarded headers и `GATEWAY_TRUSTED_PROXY_ADDRESSES=$Config.NginxIp` сохраняются.
- На edge-reservation/start/IP/`nginx -t`/health failure до `finally` записываются только bounded state (`status`, `exitCode`, `oomKilled`, `error`) и последние 80 log lines через `Protect-ReportText`; environment и key material не читаются.
- Требование сохранения первичного исключения и дополнительного cleanup evidence подтверждено фактическим outer catch/finally execution; оба fault-injection case завершились PASS.

### Constraints

Нет Docker, Gradle, build, seed, product-source, shared-contract, fake UI alias, ослабления IP assertion или cleanup чужих ресурсов. TLS fixture ephemeral и task-owned; содержимое private key не читалось и не печаталось. Terra и дети не создавались.

### Existing patterns

Использованы существующие `Convert-EdgeToRuntimeConfig`, `New-RuntimeConfig`, `New-LocalTlsCertificate`, `Protect-ReportText`, `Invoke-DockerSafe`, owned-container ledger и `Remove-OwnedResources`. H40 static IP proof остаётся отдельным evidence и не заменяется сетевой гипотезой.

### Acceptance criteria

Pure check должен подтвердить parser, exact converter replacements, API guards, negative unexpected-source rejection, two no-BOM writers, redacted bounded diagnostics, production-generated TLS fixture и фактическое поведение outer catch/finally. Все эти проверки PASS. Root затем отдельно принимает pinned H67 `nginx -t`; только после него возможен H68 full runtime. Этот leaf не объявляет runtime PASS.

### Verification

Команда pure check:

```powershell
pwsh -NoProfile -File .agent\worktrees\v2-requests-harness\.agent\student-role-orchestrator\requests-runtime\h62-edge-diagnosis\h62-edge-fix-check.ps1 -UnionRoot C:\Users\maksd\IntelliJIDEA\rutcampustrack\.agent\worktrees\v2-runtime-build-r2
```

Она не запускает Docker/Gradle. Fixture остаётся до root-owned H67 diagnostic и затем удаляется владельцем после проверки ownership.

### Do not

Не добавлять отсутствующие `landing-nginx`, `pwa-nginx`, `mini-app-nginx` как fake aliases; не менять accepted `default.conf`; не скрывать original exception; не печатать TLS private key, environment или secrets; не повторять H68 runtime без H67 evidence.

## Confirmed alias inventory

| Generated runtime reference | Declared H62 runtime alias | Result |
| --- | --- | --- |
| `api-gateway:8080` in API/WS locations | `api-gateway` | retained; H62 report command exit `0` |
| `landing-nginx:80` at generated `default.conf:76` | absent | H66 reproduced exit `1` after BOM removal |
| `pwa-nginx:80` at generated `default.conf:81` | absent | structurally proven; no fake alias added |
| `mini-app-nginx:80` at generated `default.conf:86` | absent | structurally proven; no fake alias added |
| `web-panel-nginx:80` in accepted source root block | absent | converter replaces the exact block with local root PWA |

## Implemented diff

`runner.ps1` now validates and replaces only the three known UI blocks, with a negative failure for missing or altered source. The local `/app/` block strips its public prefix before `try_files`; `/presentation/` and `/mini-app/` return 404. API, WS, caps, no-store and trusted-peer source remain guarded by the pure check.

The two runtime config writes use UTF-8 without BOM. The edge diagnostic path selects the known edge service or the last owned container, captures a JSON state subset and `docker logs --tail 80 --timestamps`, and bounds and redacts both outputs. The authorized correction assigns the original failure before diagnostics, guards diagnostic selection/capture with bounded redacted capture-error evidence, and uses dictionary-safe failure-key detection when cleanup appends evidence.

## Full-review outer catch/finally gate

The pure check extracts the production outer `TryStatementAst` and executes its actual catch/finally bodies. It injects failure only at the existing diagnostics and owned-cleanup seams; Docker is not invoked. AST offsets are `161896..167135` in the frozen runner.

| Injected case | Result |
| --- | --- |
| Diagnostics throws | diagnostics call `1`, cleanup call `1`, order `diagnostics → cleanup`, process exit `1`, `failure.message = original edge failure <redacted>`, PASS. |
| Cleanup throws | diagnostics call `1`, cleanup call `1`, cleanup FAIL phase is recorded, `failure.message = original edge failure <redacted>; injected cleanup failure <redacted>`, PASS. |

The root-authorized bounded correction implements that proposal without changing API, IP, cleanup ownership or unrelated branches.

## Checks and exit codes

| Check | Command/result | Exit |
| --- | --- | ---: |
| Parser, converter, API guards, negative source, no-BOM writers, diagnostic redaction | Pure check → `H62 edge fix check: PASS` | 0 |
| Production outer catch/finally AST fault injection | Diagnostics-throw and cleanup-throw cases above; original failure and cleanup order preserved | 0 |
| Generated config/TLS fixture | `New-RuntimeConfig` + `New-LocalTlsCertificate`; config/cert/key files exist; key contents not read | 0 |
| Docker | Intentionally not run by this leaf | N/A |
| Gradle/build | Intentionally not run by this leaf | N/A |
| H65 retained diagnostic | pinned local `nginx -t`, network none, only `api-gateway` host override | 1 (root evidence) |
| H66 retained diagnostic | same bounds after stripping only BOM | 1 (root evidence) |

Pure evidence: [h62-edge-fix-evidence.json](h62-edge-fix-evidence.json), SHA256 `466af41bfa7d74e2c9aabe4daab1bc79acb0db92922716d2d25fec48e27bdc81`; corrected runner SHA256 `9a2d703548110a142597b02196f5483f0f61d7329dfbfed313e38f1afc0b4b9d`, packet SHA256 `25f8632b8a9debc63e8d2ee1bb79a4c2ceff57dec5214938c28ec8601da1418c`.

## Root H67 fixture handoff

The pure check generated a task-owned fixture using the existing production helpers:

- config directory: `.../h62-edge-diagnosis/fixture/nginx/`;
- TLS certificate: `.../h62-edge-diagnosis/fixture/keys/server.crt`;
- private key: `.../h62-edge-diagnosis/fixture/keys/server.key` (path recorded, contents never printed);
- generated config SHA256: `bc18f838fae0d8aa9e99c2ec5d99c2669efe8a9dff47068bab6b34985184be20`;
- generated default SHA256: `d0ccfae3573bc6a233354288c47d1b0eb3601ce6ef9f7cb2887fa5690f0e1b17`.

Root-owned H67 procedure is referenced by absolute path and is not duplicated or executed here: `C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/orchestration-v2/H67-NGINX-VALIDATION.md` (current SHA256 `BE990C133B4B1666DE5C690AAC0000F6269C885F9CBD7F79857C5EAF6C71F46B`). It owns the bounded `nginx -t` command and cleanup.

## Runtime limits and remaining gate

H62 itself remains a failed pre-fix run: its empty edge IP and missing startup log are immutable evidence. H65 proves the BOM defect; H66 proves the first missing UI upstream after BOM removal. The pure check now proves generated bytes, source guards, bounded diagnostics, and both outer failure guarantees, but it does not prove Docker networking, actual Nginx process startup, HTTPS health, I1 or I2. Root must record H67 exit/evidence and cleanup before granting H68. The disposable `/presentation/` and `/mini-app/` 404 behavior is the explicit bounded scope; serving those UIs requires a separate root contract with real containers.

## Diff boundary

The authorized correction changed only the assigned runner outer failure handling, bounded H62 pure check, own evidence and handoff; accepted union source, H62/H65/H66 artifacts and foreign work remain untouched. The pure check retained the public config hashes and regenerated only its task-owned ephemeral TLS files through the existing helper; key contents were never read. The prefixed failing evidence copy remains immutable in the own directory.

