# Independent Vue release-input review

Вердикт: **CHANGES REQUIRED — один P2 в DEV static serving**. PROD/deploy path substitutions и сохранение существующих settings прошли source review. Runtime/cutover/deploy не выполнялись.

Дата: 2026-10-24. Frozen original: `429a3a27057065d564fc677092ec9214f0dd74ad`, exact parent/base `c3c08a935c223f1964a9913e299ae9e4a8d13026`. Author WT `/workspace/rct-worktrees/web-foundation`, branch `codex/pr3-web-release-inputs`, HEAD clean429 во время проверки. Scope — ровно `docker-compose.prod.yml`, `docker-compose.yml`, `.github/workflows/deploy.yml`, шесть добавленных/шесть удалённых строк. Назначение — независимое Sol6.1/high review; runtime model telemetry недоступна.

## P2 — новый DEV readonly bind не обеспечивает доступ nginx worker к текущему Vue dist

**Путь:** `docker-compose.yml:337`.

DEV сервис использует stock `nginx:1.27-alpine` и напрямую bind-mounts `frontends/web-vue/dist` в `/usr/share/nginx/html:ro`. Текущие c3 built artifacts в ROOT имеют следующие actual filesystem metadata (прочитаны только через stat):

```text
1000:1000 700 frontends/web-vue/dist
1000:1000 600 frontends/web-vue/dist/index.html
1000:1000 600 frontends/web-vue/dist/assets/auth-background-original.png
```

Официальный nginx image обслуживает static files non-root worker пользователем nginx, не владельцем UID1000. При этих modes worker не может traverse document root или читать HTML/PNG. DEV bind обходит существующий `frontends/web-vue/Dockerfile:31` (`chmod -R a+rX /usr/share/nginx/html`), который делает PROD image output читаемым. Compose schema validation не выявляет filesystem permission mismatch.

**Causal case:** использовать уже построенный c3 dist и запустить изменённый DEV web service без дополнительного неописанного chmod/копирования. Worker столкнётся с EACCES; `/login`/static assets не будут нормально обслуживаться (ожидается403). Это вывод из actual modes и service/image contract; reviewer Docker/HTTP не запускал и actual403 не заявляет.

**Влияние:** новый DEV Vue entry не работает с уже имеющимся build output managed workspace, хотя paths и compose schema корректны. Исправление должно обеспечить worker-readable runtime artifact, например через existing Vue image/readability normalization или отдельную нормализованную копию static output. Нельзя полагаться на chmod внутри PROD image, когда этот image в DEV не используется. Не требуется менять private files/весь repo или ослаблять worker privilege ради этого bind.

## Остальной результат source review

- PROD build context `./frontends`, Dockerfile `web-vue/Dockerfile` корректно разрешаются относительно compose/context. Dockerfile COPY inputs (root manifest/lock/configs, четыре workspace manifests, shared/mobile/PWA/TMA/WEB sources) доступны из этого context; runtime copy — только WEB dist/static nginx с нужной нормализацией прав. Dockerfile, package/lock/application bytes не менялись от c3, prior three-build PASS переиспользуется только как прежняя evidence, не новый запуск429.
- Deploy build-push context `frontends` и explicit file `frontends/web-vue/Dockerfile` согласованы с root checkout и existing sibling workspace build pattern. Exact image tags/service/container/DNS identity сохраняются, поэтому existing served Vue oracle/release caller и global root upstream остаются совместимыми. Missing-assets404/no-store/root base не меняются.
- Независимые exact-text replacements подтвердили: во всём PROD source изменены только два WEB build fields, в DEV — только два static mount paths, в Deploy — только context/file. Дополнительно parsed PROD YAML после восстановления этих build fields равен parent. Networks/limits/ports/logging/restart/other lanes/PWA/TMA не менялись.
- **Исправление прежней assessment формулировки:** Deploy уже имеет `workflow_run(CI)` **и** `workflow_dispatch`. Оба triggers, production-enabled/job guards, refs/actions/tags/cache и все прочие bytes побайтно сохранены. Deploy не объявляется dispatch-only. Четыре отдельные CI/Coverage/Security/OpenAPI workflow files вообще не входят в diff и остаются manual/PAUSED.
- Angular package/CSP/global nginx/security exclusions/dependency policy не удаляются и не меняются. QPROD004 и runtime/cutover/removal остаются отдельными gates.

## Evidence и точные пределы

Прочитан author report `/workspace/rct-pr3-runtime/VUE-RELEASE-INPUTS-429a3a270.md`, получен напрямую от автора. Его DEV Compose config exit0 и changed-prod WEB/private_net fragment exit0 — scoped configuration evidence. FULL PROD config exit1 на **unchanged absent** `CAMPUS_MAP_USAGE_HMAC_KEY`; это **не full-prod PASS**, и reviewer не добавлял fake variable/secret, не читал production environment и не повторял попытку.

Независимо выполнены frozen diff/source inspection, exact replacements/parsed YAML comparison без env resolution, `git diff --check` exit0 и указанный readonly stat actual built artifacts. Scope inventory подтвердил неизменность application/build inputs/four paused workflows/oracle/release caller/global nginx. Builds, unit/control suites, Docker/config/up/pull/exec, browser/HTTP/API/DB/network/CI/deploy reviewer не запускал.

После P2 correction нужен narrow recheck DEV serving strategy и только соответствующий адресный local static check под root lease; повтор всех c3 builds или whole-suite не требуется без новой причины. Source review не заменяет full production environment validation или outer-edge acceptance. Единственная запись reviewer — этот внешний immutable report; продуктовые файлы и процессы не менялись.
