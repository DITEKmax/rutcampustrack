# M15 Notes (retrospective)

Lessons learned из first VPS deploy. Большинство закрывается в M16
(post-deploy hardening) либо отложено в `docs/archive/future-ideas.md`.

---

## 2026-04-27

### Memory budgeting

**Lesson:** `mem_limit: 256m` для Spring Boot 3.4 + WebFlux + STOMP +
OTel + Caffeine + Mongo driver — слишком оптимистично. JVM heap 75%
оставляет ~64m под non-heap (metaspace, threads, NIO, native libs),
этого мало для production-нагруженного Spring Boot.

**Floor для будущих сервисов:** 384m минимум. 512m для тех, кто
консьюмит RabbitMQ (auth/academic/schedule/attendance/notification-web)
— RabbitMQ Java client держит buffer'а на каждое connection.

**M16 follow-up:** добавить guard-test или Prometheus alert
`ContainerMemoryHigh` > 80% sustained 5min — ловить такие проблемы
до OOM, а не после.

### Env-block drift между сервисами

**Lesson:** M14 G9 закрывал `INTERNAL_ISSUER_SECRET` для
notification-web, но auth-service пропустили. `RequiredSecretsValidator`
поймал отсутствие на boot — это **сработало как и должно** (fail-fast
вместо misconfigured deploy). Но обнаружили только в проде.

**Корень:** ручная синхронизация env-block между сервисами в
`docker-compose.prod.yml` подвержена drift'у.

**M16/M17 follow-up:** либо template/anchor в YAML для общих секретов
(`<<: *internal-secrets`), либо генератор compose из manifest.

### Alertmanager time_intervals полночь UTC

**Lesson:** Alertmanager v0.27 не поддерживает интервалы, пересекающие
полночь, одной записью. Документация это не упоминает — узнали через
boot error.

**Workaround:** split на два sub-interval'а. Стабильно работает.

### Angular inlineCritical + строгий CSP

**Lesson:** дефолт Angular `@angular/build:application` несовместим с
строгим CSP `script-src 'self'`. Inline onload event handler требует
либо `'unsafe-inline'`, либо `'unsafe-hashes' + sha256-хеш`, либо
выключить `inlineCritical`.

Хеш привязан к точной строке `onload="this.media='all'"`. Если Angular
изменит pattern (Angular 19 → 20 upgrade) — CSP сломается.

**M16 follow-up:** `inlineCritical: false` в `frontends/web-panel/
angular.json` — уберёт зависимость от хеша, чуть хуже FCP на slow 3G.
Tradeoff в пользу maintainability.

### Cosign keyless cert subject — case-sensitive

**Lesson:** GitHub username в Sigstore certificate subject сохраняет
оригинальный регистр. Regex без `(?i)` ловит mismatch если в `deploy.yml`
username написан в lowercase.

**Стандарт практики:** всегда `(?i)` для GitHub username в
`identity-regexp`. Path-часть (репо/workflow) можно оставить
case-sensitive — она задаётся самим пользователем как код, не платформой.

### Working tree drift на VPS

**Lesson:** хотфиксы прямо на VPS (`nano /opt/rutcampustrack/<file>`)
без backporting в репо в той же сессии создают drift. Следующий
`deploy.yml` падает на `git pull` с conflict'ом.

**В M15 повезло:** изменения на VPS были идентичны коммитам `c3ff148` +
`8d7c168` (делал параллельно). `git stash` + `git pull` + diff пустой
→ stash дропнут.

**M16 follow-up:** добавить в `docs/meta/contributing.md` правило:
любой VPS-edit либо backport'ится в репо в той же сессии, либо снимается
как `git format-patch` для последующего PR. См. `future-ideas.md` §
«VPS local edits drift».

### Скоп hotfix'ов vs M16

Большинство hotfix'ов M15 — не баги, а runtime-конфигурация и suppressions.
Реальные баги, обнаруженные в проде:

1. **OTel exporter port** (4317 vs 4318) — distributed tracing не работает,
   шумит ERROR в логи. → **M16 G1**.
2. **nginx DNS race** — UI отваливается на 502 после каждого compose
   restart. → **M16 G5**.
3. **Loki `InstancesCount <= 0`** — спорадические потери логов? →
   **M16 G4** (диагностика).

Эти три — pure tech debt M04/M13, не M15 hotfix material. M15 hotfix
list был чисто про boot/security/CSP блокеры.
