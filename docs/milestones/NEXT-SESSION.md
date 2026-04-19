# Промпт для следующей сессии

Скопируй всё ниже в новый чат с Opus 4.7 (1M context). Этого достаточно —
Opus сам откроет файлы и поймёт где мы остановились.

---

Продолжай работу над v0.0.0 milestones.

Контекст:
1. Архитектурный аудит завершён, зафиксирован в `docs/report-before-v0.0.0/`
   (16 отчётов + OWNER-ANSWERS.md 6400 строк + COVERAGE-AUDIT.md 354
   пункта + 99-executive-summary.md roadmap).
2. Рабочий процесс — lightweight milestones без GSD-orchestrator'а.
   Индекс: `docs/milestones/README.md`.
3. Активный/следующий milestone указан в таблице того README как
   ⏳ в работе или ⏳ следующий (готов к старту). Внутри каталога —
   PLAN.md (scope), CHECKLIST.md (атомарные задачи), NOTES.md (живой
   лог), DECISIONS.md (micro-ADR). Все четыре файла уже заполнены
   из аудита (`report-before-v0.0.0/`) — читать, а не переписывать.

Что делать:
1. Прочитай `docs/milestones/README.md` — найди активный/следующий milestone.
2. Прочитай `PLAN.md` + `CHECKLIST.md` + `NOTES.md` + `DECISIONS.md`
   активного milestone'а.
3. Прочитай `git log --oneline -15` — посмотри последние коммиты,
   понять где остановился по CHECKLIST.
4. **Если в DECISIONS.md есть блок `## ОТКРЫТО —...`** — это развилка,
   которую нужно подтвердить до кодинга. Зачитай её пользователю,
   покажи рекомендацию, жди его решение, **тогда** запиши как
   обычный `## YYYY-MM-DD —` блок и продолжай.
5. Если статус milestone'а `⏳ следующий` — обнови на `⏳ в работе`
   в `docs/milestones/README.md` и впиши старт-дату в PLAN.md.
6. Продолжай с первой невыполненной галочки `[ ]` в CHECKLIST.md.
7. Работай сам — пиши код, запускай `./gradlew build`, правь если
   упало, коммить после каждой логической группы из CHECKLIST.
8. После каждой завершённой группы — отчитайся коротко (1-2 строки)
   и жди подтверждения перед следующей. Это для контроля, не для
   разрешения (если пользователь говорит «go» — работай молча дальше).

Правила:
- Русский язык в отчётах/NOTES/ответах пользователю (технические
  термины и код — оригинал).
- Не звать `gsd-*` агентов. Вместо этого при необходимости: `Explore`
  для «найти все X», `bug-hunter` / `code-reviewer` на итоговый diff
  milestone'а.
- Surprise / отклонение от плана → сразу в NOTES.md + спросить
  пользователя до продолжения.
- Micro-решение (не в OWNER-ANSWERS, но нужно зафиксировать) →
  в DECISIONS.md.
- Закрываешь пункт из CHECKLIST → ставишь `[x]` в том же файле
  Edit'ом (не через write).
- Закрываешь пункт из `COVERAGE-AUDIT.md` → в колонке «Closed in»
  пиши commit SHA.
- `CHANGELOG.md` → `[Unreleased]` → обновляй при значимых изменениях
  (не каждый коммит).
- Hook-reminder-ы «READ-BEFORE-EDIT» часто ложные — если файл уже
  был прочитан в этой сессии, Edit пройдёт. Игнорируй их.

Когда milestone закрыт:
1. Все пункты CHECKLIST отмечены `[x]`.
2. Все acceptance criteria в PLAN.md пройдены.
3. Post-mortem секция дописана в PLAN.md.
4. Статус в `docs/milestones/README.md` → ✅ готов.
5. Тег `git tag v0.0.0-alpha.{N}` на последнем коммите milestone'а.
6. Сообщить пользователю финальный summary + ссылку на следующий
   milestone по dependency graph.

Не делать без явного `go`:
- `git push` на origin.
- Удалять/rm файлы в production-коде.
- Менять scope milestone'а (только через NOTES + подтверждение).
- Пропускать acceptance criteria.

Старт:
> Читаю README.md → активный milestone → PLAN → CHECKLIST → git log.
> Через минуту скажу где остановились и какая первая задача.

---

## Hand-off для следующей сессии (2026-04-20, Opus 4.7)

**Состояние:** M03a ✅ закрыт, tag `v0.0.0-alpha.3` установлен локально
(commit `a6d491a`, БЕЗ push). Следующий milestone — **M03b
(JWT HttpOnly cookie + WS-ticket + logout lifecycle)**. Каталог
`docs/milestones/M03b-jwt-cookie-ws-ticket/` scaffolded с PLAN +
CHECKLIST + NOTES + DECISIONS — готов к старту.

**Ключевой контекст M03a → M03b:**
- Internal JWT token-exchange работает end-to-end в dual-mode (legacy
  X-User-* всё ещё принимаются). Переключение на strict-mode
  отложено в отдельный deploy commit после UAT.
- 9 Known Issues из M03a post-mortem зафиксированы в
  `docs/milestones/M03a-internal-jwt-ratelimit/PLAN.md` → Post-mortem →
  Known Issues. **KI-3, KI-6, KI-7, KI-8 попадают в M03b**
  (token-cache expiry check, Redis TTL race, bcrypt DoS, CacheRequestBody
  для X-Login). KI-1/2/4/5/9 — в M04/M06.
- Prod env vars для strict-mode переключения (`GATEWAY_STRIP_LEGACY_HEADERS=true`
  + `RUTCAMPUSTRACK_SECURITY_LEGACY_HEADERS_ENABLED=false`) задокументированы
  в `docs/milestones/M03a.../NOTES.md` → UAT Golden Path Checklist — они
  применяются отдельным operational commit'ом после UAT на staging.

**M03b scope (кратко — полный в PLAN.md):**

Закрывает C0-7 (~самый дорогой кластер, 8-12д) + C0-5:
- JWT HttpOnly cookie для refresh (09 P0-1, 10 P0-1) — `/auth/refresh`
  читает refresh из cookie, а не body.
- WebSocket ticket (09 P0-2, 10 P0-2) — внешний JWT убирается из
  query-string, заменяется short-lived ticket'ом (30s, single-use, Redis).
  `/auth/ws-ticket` endpoint защищается Internal JWT из M03a.
- `clearAllClientState()` в PWA/web-panel (09 P0-4/5, 10 P0-4) — logout
  чистит localStorage/sessionStorage/SW cache/push subscription.
- CSRF double-submit cookie pattern для cookie-based endpoints.
- Breaking frontend migration — `localStorage['rct.auth.v1']` удаляется.
- 4 hot-patches из M03a KI-3/6/7/8.

**Что делать в новой сессии (первая задача):**

1. **Прочитать PLAN/CHECKLIST/NOTES/DECISIONS M03b** — там всё уже
   готово к Группе 1 Discovery.
2. **В DECISIONS.md есть блок `## ОТКРЫТО —`** — 5 развилок (cookie Path,
   SameSite, CSRF mechanism, WS-ticket storage, deprecation timeline)
   требуют подтверждения владельцем до кодинга. Зачитать их, дать
   рекомендации, дождаться ответа, переоформить в `## YYYY-MM-DD —` блок.
3. Обновить статус `⏳ следующий` → `⏳ в работе` в
   `docs/milestones/README.md`, дату старта в PLAN.md.
4. Продолжать Группа 1 → 13 по CHECKLIST. Коммит после каждой группы,
   отчёт 1-2 строки, ждать «go» или продолжать молча.

**Правила работы (без изменений с M03a):**
- Русский в отчётах/NOTES, технические термины/код — оригинал.
- READ-BEFORE-EDIT reminder'ы ложные (после Read в той же сессии) —
  игнорируй.
- Коммит после каждой логической группы (`feat/fix/test/docs` scope:
  `<service>/<module>` + `(M03b Группа N)`).
- Не звать `gsd-*` агентов. `Explore` для «найти все X», `bug-hunter` +
  `security-auditor` — в Группе 11 (финальный аудит перед тегом).
- Surprise / отклонение от плана → NOTES.md + спросить владельца.
- Micro-решение → DECISIONS.md.
- Закрыл пункт CHECKLIST → `[x]` через Edit (не write-rewrite).

**После Группы 13 (финал M03b):**
- Acceptance criteria проверяются все сразу.
- Post-mortem в PLAN.md.
- Статус в `docs/milestones/README.md` → ✅.
- `git tag v0.0.0-alpha.4` на финальном коммите (БЕЗ push — жду go).
- Следующий milestone по dependency graph — M07 (Frontend Hardening)
  либо M04 Observability (parallel-safe).

**Последние коммиты M03a (git log --oneline -10):**
- `a6d491a` chore(m03a): close Internal JWT + rate-limit milestone (Группа 16)
- `35640b2` fix(security): audit blockers C1/C2/H3 перед v0.0.0-alpha.3 (Группа 16)
- `50123ff` docs(m03a): Internal JWT spec + rate-limits + architecture + CHANGELOG (Группа 15)
- `4a13b90` feat(gateway): strip-legacy-headers toggle + UAT checklist (Группа 14)
- `dd96917` test(gateway): contract-тест Gateway↔downstream Internal JWT pipeline (Группа 13)
- `8a320d1` test(gateway): rate-limit Testcontainers IT + 3 фикса (Группа 12)
- `315a662` feat(auth): LoginRateLimiter composite (ip, login) (Группа 11)
- `025a266` feat(gateway): rate-limit routes + RFC 7807 Problem Details (Группа 10)
- `b38d263` feat(gateway): rate-limit infra (Группа 9)
- `4a13b90` (см. выше, одна из последних)

**Source of truth для M03b:**
- `docs/milestones/M03b-jwt-cookie-ws-ticket/PLAN.md` — scope + acceptance criteria
- `docs/milestones/M03b-jwt-cookie-ws-ticket/CHECKLIST.md` — 13 групп
- `docs/milestones/M03b-jwt-cookie-ws-ticket/NOTES.md` — пустой (+ backlog KI-3/6/7/8)
- `docs/milestones/M03b-jwt-cookie-ws-ticket/DECISIONS.md` — 5 `ОТКРЫТО` развилок + 0 принятых
- `docs/report-before-v0.0.0/99-executive-summary.md` секция «C0-7»
- `docs/report-before-v0.0.0/OWNER-ANSWERS.md` → `02-Q-frontend-security` (Часть А + Часть Б)
- `docs/milestones/M03a-internal-jwt-ratelimit/PLAN.md` Post-mortem → Known Issues KI-1..KI-9
