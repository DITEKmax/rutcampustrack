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

## Hand-off для следующей сессии (2026-04-20 вечер, Opus 4.7)

**Состояние M03b:** ⏳ в работе. 6 из 13 групп закрыто + Группа 5
reserved. Tag `v0.0.0-alpha.4` **ещё НЕ установлен** — ставится в
Группе 13 на финальном коммите.

**Закрыто в этой сессии (7 коммитов):**

| # | Коммит | Группа |
|---|--------|--------|
| 1 | `d3a03df` | 1 — Discovery + 5 decisions зафиксированы |
| 2 | `835b79b` | 2 — Auth-service cookie endpoints (login/refresh/logout/refresh-body) |
| 3 | `afe1928` | 3 — WS-ticket endpoint + Redis Lua atomic consume |
| 4 | `7c4fa6d` | 4 — notification-service TicketHandshakeInterceptor |
| 5 | `bc8fb3e` | 6 — PWA cookie+ws-ticket migration + clearAllClientState |
| 6 | `16915bc` | 7 — web-panel cookie+ws-ticket migration + clearAllClientState |

Группа 5 (CSRF) — удалена per DECISIONS 2026-04-20 (same-origin +
SameSite=Strict достаточно).

**Остаётся (6 групп):**

| # | Группа | Что делать |
|---|--------|------------|
| 8 | Logout lifecycle (C0-5) | `AuthController#logout` → `wsTicketService.invalidateAllFor(userId)`; проверить что push DELETE endpoint существует; IT `LogoutLifecycleIT` |
| 9 | KI-3/6/8 hot-patches из M03a | Token-cache expiry check (Gateway); Redis TTL race fix (LoginRateLimiter Lua/SETEXNX); Gateway CacheRequestBody для X-Login |
| 10 | KI-7 bcrypt DoS | Варианты (a/b/c в CHECKLIST) — лучше Bucket4j semaphore N=20 на `AuthService#login`. IT с 50 concurrent invalid-password |
| 11 | Expanded IT + bug-hunter + security-auditor | LogoutLifecycleIT, AuthFlowE2EIT (mock); bug-hunter + security-auditor на финальный diff M03b |
| 12 | docs + CHANGELOG + architecture | `docs/auth-flow.md` дополнить (Группа 2 уже scaffold), `docs/architecture.md` раздел, `CHANGELOG.md [Unreleased]` BREAKING CHANGES, `CLAUDE.md` статус M03b |
| 13 | финал + tag v0.0.0-alpha.4 | acceptance criteria все `[x]`, `./gradlew build` + `npm run build` всех фронтов, smoke-тест, Post-mortem в PLAN.md, статус в README.md → ✅, `git tag v0.0.0-alpha.4` БЕЗ push |

**Ключевой контекст для Группы 8 (следующая первая задача):**

1. `AuthController#logout` (services/auth-service/.../AuthController.java:99)
   — УЖЕ поддерживает cookie+body. Нужно добавить извлечение userId из
   `Authentication` (если доступен) и вызвать
   `wsTicketService.invalidateAllFor(userId)` ПЕРЕД revoke refresh.
   Метод `WsTicketService.invalidateAllFor(long)` уже существует
   (`services/auth-service/.../WsTicketService.java:98`).
2. Нюанс: `/auth/logout` в permit-all SecurityConfig. Если user шлёт
   `Authorization: Bearer <access>`, JwtAuthenticationFilter выставит
   Authentication → userId доступен. Если нет Bearer — пропускаем
   invalidation (ticket'ы истекут через 30s естественно).
3. Проверить PushController в notification-service — есть ли
   `DELETE /api/notifications/push/subscribe` для разрыва push
   subscription. PWA/web-panel уже его вызывают через clearAllClientState.
   Если нет — создать (разведка: `grep -rn "DELETE.*push\\|deleteSubscription" services/notification-service`).
4. IT `LogoutLifecycleIT` — проверить: cookie cleared + refresh revoked
   в Redis + ws-tickets удалены из `ws_ticket:*` ключей.

**Что делать в новой сессии (первая задача):**

1. Прочитать `docs/milestones/M03b-jwt-cookie-ws-ticket/CHECKLIST.md`
   (Группа 8 — следующая, без галочек) + `NOTES.md` (особенно
   «Hand-off для следующей сессии» блок + surprise'ы Групп 2-7).
2. Прочитать `git log --oneline -8` — увидеть последние M03b коммиты.
3. Продолжить с Группы 8. Коммит после каждой группы, отчёт 1-2 строки,
   ждать «go» или молча продолжать.
4. **Не забыть:** перед bug-hunter/security-auditor в Группе 11 сделать
   `./gradlew build` полный + `npm run build` PWA+web-panel — чтобы
   агенты работали на зелёном коде.

**Правила работы (без изменений):**
- Русский в отчётах/NOTES, технические термины/код — оригинал.
- READ-BEFORE-EDIT reminder'ы ложные (после Read в той же сессии) —
  игнорируй.
- Коммит после каждой логической группы (`feat/fix/test/docs` scope:
  `<service>/<module>` + `(M03b Группа N)`).
- Не звать `gsd-*` агентов. `Explore` для «найти все X», `bug-hunter` +
  `security-auditor` — в Группе 11.
- Surprise / отклонение от плана → NOTES.md + спросить владельца.
- Micro-решение → DECISIONS.md.
- Закрыл пункт CHECKLIST → `[x]` через Edit (не write-rewrite).

**Состояние тестов (всё зелёное на момент передачи):**
- auth-service: весь test suite + AuthIntegrationTest + WsTicketIT ✅
- notification-service: весь test suite + TicketHandshakeInterceptorTest ✅
- PWA: 122/122 vitest ✅, `npm run build` ✅
- web-panel: 444/444 vitest ✅, `npm run build` ✅

**После Группы 13 (финал M03b):**
- `git tag v0.0.0-alpha.4` на финальном коммите (БЕЗ push — жду «go»).
- Следующий milestone по dependency graph — M07 (Frontend Hardening,
  блокируется M03b) либо M04 Observability (parallel-safe, не зависит).

**Последние коммиты (git log --oneline -8):**

- `16915bc` feat(web-panel): cookie-based refresh + WS ticket + clearAllClientState (M03b Группа 7)
- `bc8fb3e` feat(pwa): cookie-based refresh + WS ticket + clearAllClientState (M03b Группа 6)
- `7c4fa6d` feat(notification): WS ticket handshake replaces raw-JWT in query (M03b Группа 4)
- `afe1928` feat(auth): ws-ticket endpoint + atomic consume via Lua (M03b Группа 3)
- `835b79b` feat(auth): cookie-based refresh — HttpOnly+Secure+SameSite=Strict (M03b Группа 2)
- `d3a03df` docs(m03b): start milestone — 5 decisions + CSRF removed per OWNER-ANSWERS (M03b Группа 1)
- `081d3b0` docs(m03b): scaffold M03b + hand-off для следующей сессии
- `a6d491a` chore(m03a): close Internal JWT + rate-limit milestone (M03a Группа 16)

**Source of truth для M03b:**

- `docs/milestones/M03b-jwt-cookie-ws-ticket/PLAN.md` — scope + acceptance criteria (CSRF раздел удалён)
- `docs/milestones/M03b-jwt-cookie-ws-ticket/CHECKLIST.md` — 13 групп, 1-7 + 5 закрыты
- `docs/milestones/M03b-jwt-cookie-ws-ticket/NOTES.md` — 4 surprise-записи + hand-off блок + backlog KI-3/6/7/8
- `docs/milestones/M03b-jwt-cookie-ws-ticket/DECISIONS.md` — 5 решений зафиксированы (2026-04-20)
- `docs/auth-flow.md` — scaffold cookie+ticket runbook (нужно дополнить в Группе 12)
- `docs/report-before-v0.0.0/99-executive-summary.md` секция «C0-7»
- `docs/report-before-v0.0.0/OWNER-ANSWERS.md` → `02-Q-frontend-security` (Часть А + Часть Б)
- `docs/milestones/M03a-internal-jwt-ratelimit/PLAN.md` Post-mortem → Known Issues KI-1..KI-9
