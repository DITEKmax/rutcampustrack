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

## Hand-off для следующей сессии (2026-04-20 ночь, после закрытия M03b)

**Состояние M03b:** ✅ **ЗАКРЫТ.** Tag `v0.0.0-alpha.4` установлен
(БЕЗ push — жду `go` пользователя). 13/13 групп завершены (Группа 5
удалена, CSRF не нужен).

**Закрыто в этой сессии (7 новых коммитов + финал):**

| # | Коммит | Группа |
|---|--------|--------|
| 1 | `b1fbfcc` | 8 — Logout lifecycle (ws-ticket invalidate + cookie clear + refresh revoke) |
| 2 | `9286809` | 9 — KI-3 / KI-6 / KI-8 hot-patches |
| 3 | `dff9ea1` | 10 — KI-7 bcrypt DoS Semaphore N=20 |
| 4 | `acf989b` | 11 — bug-hunter HIGH-2 + security-auditor MEDIUM-1 fixes |
| 5 | `140d7d4` | 12 — docs (auth-flow + architecture + CHANGELOG + CLAUDE + README) |
| 6 | `eb125c4` | 13 — финальный коммит + tag `v0.0.0-alpha.4` |

Итого в M03b (13 коммитов с `081d3b0..eb125c4`). Предыдущая сессия
закрыла Группы 1-7, эта — 8-13.

**Audit итог:**
- bug-hunter: 0 CRITICAL, 0 HIGH (real), 10 MEDIUM/LOW. Один HIGH →
  MEDIUM (уже защищён), один real HIGH-2 запатчен (atomic SADD+EXPIRE
  в WsTicketService).
- security-auditor: 24/24 checks PASS, 0 CRITICAL/HIGH, 2 MEDIUM +
  3 LOW. Один MEDIUM запатчен (Bearer в DELETE push/subscribe при
  logout — cross-user push leak).
- Остальные MEDIUM/LOW — в backlog M04/M06 (см. NOTES.md пост-audit блок).

**Состояние тестов (всё зелёное на момент финального тэга):**
- auth-service: все тесты ✅ (включая WsTicketIT 6, LogoutLifecycleIT 2,
  BcryptDoSMitigationIT 1, обновлённые LoginRateLimiterTest под Lua).
- api-gateway: все тесты ✅ (включая CompositeLoginKeyResolverIT 3 с KI-8,
  InternalJwtIssuerClientTest 8 с KI-3 near-expiry).
- notification-service: все тесты ✅.
- PWA: 122/122 vitest ✅, `npm run build` ✅.
- web-panel: 444/444 vitest ✅, `npm run build` ✅.

### Действия ожидающие `go` пользователя

1. `git push origin main` — 14 коммитов M03b ещё не на origin.
2. `git push origin v0.0.0-alpha.4` — tag ещё локальный.
3. Старт следующего milestone.

### Следующий milestone — выбор пользователя

Dependency graph после M03b:

| Milestone | Зависит от | Parallel-safe с M03b-наследником | Рекомендация |
|-----------|-----------|-----------------------------------|--------------|
| **M04 Observability** (OTel+Tempo+Alertmanager+JSON-логи) | M01 | да | **Рекомендую первым.** Нужен для prod-ready state + закрывает `event user.logged-out` (отложено из M03b) + KI-2 (dual-mode silent fallback без метрики) + KI-4 (PublicKeyProvider readiness) |
| **M05 Performance** (composite indexes, Caffeine, @EntityGraph, batch) | M01 | да | Parallel-safe. Можно параллелить с M04, если два трека. |
| **M06 Ops & Supply Chain** (SHA tagging, Trivy, Gitleaks, HEALTHCHECK, Renovate, mTLS) | — | полностью | Полностью независим. Можно в любой момент. Закрывает KI-1 (X-Forwarded-For spoofing), KI-9 (INTERNAL_ISSUER_SECRET → mTLS). |
| **M07 Frontend Hardening** (CSP self-host, a11y, openapi-typescript, UX fixes) | M03b ✅ | нет (затрагивает PWA+panel) | Теперь разблокирован. |
| **M08 Test Infrastructure** (Playwright e2e, golden tests, coverage-gate, diff 80%) | M01, M02, M03b ✅ | нет (нужен стабильный код) | Закрывает deferred E2E `FrontendAuthFlowPlaywrightIT` из M03b. |

**Мой рекомендованный порядок:** M04 → M06 → M05 → M07 → M08.
Причина: M04+M06 снимают большую часть ops/prod рисков, M05 —
оптимизация (не критично для alpha), M07 полирует UX после observability
(чтобы перфорации frontend'а заметны), M08 валидирует всё E2E.

### Что делать в новой сессии (первая задача)

1. Прочитать `docs/milestones/README.md` — увидеть что M03b ✅.
2. **Спросить пользователя:** какой milestone следующий (M04 / M06 /
   другой). Не начинать без подтверждения — выбор влияет на
   parallel-tracks с другими сессиями.
3. Когда выбран — прочитать `docs/report-before-v0.0.0/99-executive-summary.md`
   секцию соответствующую milestone'у.
4. Создать каталог `docs/milestones/M{NN}-{slug}/` со скелетом из
   `_TEMPLATE/` (PLAN.md + CHECKLIST.md + NOTES.md + DECISIONS.md).
5. Заполнить PLAN.md из OWNER-ANSWERS + 99-executive-summary +
   connected отчётов в `docs/report-before-v0.0.0/`.
6. Обновить `docs/milestones/README.md` — новый milestone ⏳ в работе.
7. Прикрепить commit `docs(mNN): scaffold milestone + hand-off`.
8. Начинать работу по CHECKLIST.

### Правила работы (без изменений)

- Русский в отчётах / NOTES / ответах пользователю. Технические термины /
  код — оригинал.
- READ-BEFORE-EDIT reminder'ы ложные (после Read в той же сессии) —
  игнорируй.
- Коммит после каждой логической группы (`feat/fix/test/docs` scope:
  `<service>/<module>` + `(M{NN} Группа N)`).
- Не звать `gsd-*` агентов. `Explore` для «найти все X», `bug-hunter` +
  `security-auditor` / `code-reviewer` — в финальной группе audit'а.
- Surprise / отклонение от плана → NOTES.md + спросить владельца до
  продолжения.
- Micro-решение (не в OWNER-ANSWERS) → DECISIONS.md.
- Закрыл пункт CHECKLIST → `[x]` через Edit.
- `CHANGELOG.md` → `[Unreleased]` → обновляй при значимых изменениях.

### Последние коммиты (git log --oneline -10)

```
eb125c4 chore(m03b): close cookie + ws-ticket + logout milestone
140d7d4 docs(m03b): auth-flow runbook + architecture + CHANGELOG (M03b Группа 12)
acf989b fix(auth,pwa): hotfixes из bug-hunter + security-auditor (M03b Группа 11)
dff9ea1 feat(auth): KI-7 bcrypt DoS — Semaphore N=20 guard (M03b Группа 10)
9286809 feat(gateway,auth): KI-3/6/8 hot-patches из M03a (M03b Группа 9)
b1fbfcc feat(auth): logout lifecycle — ws-ticket invalidation + cookie clear + refresh revoke (M03b Группа 8)
b1dd975 docs(m03b): hand-off для следующей сессии — 7/13 групп закрыто
16915bc feat(web-panel): cookie-based refresh + WS ticket + clearAllClientState (M03b Группа 7)
bc8fb3e feat(pwa): cookie-based refresh + WS ticket + clearAllClientState (M03b Группа 6)
7c4fa6d feat(notification): WS ticket handshake replaces raw-JWT in query (M03b Группа 4)
```

Tag: `v0.0.0-alpha.4` на `eb125c4` (локально, без push).

### Source of truth

Для M03b (закрыт, historical reference):
- `docs/milestones/M03b-jwt-cookie-ws-ticket/PLAN.md` — + Post-mortem
- `docs/milestones/M03b-jwt-cookie-ws-ticket/CHECKLIST.md` — 13/13 ✅
- `docs/milestones/M03b-jwt-cookie-ws-ticket/NOTES.md` — 6 surprise-записей
  + 2 hand-off блока + audit-result блок + backlog MEDIUM/LOW
- `docs/milestones/M03b-jwt-cookie-ws-ticket/DECISIONS.md` — 7 решений
- `docs/auth-flow.md` — полный runbook cookie+ticket+logout
- `docs/architecture.md` раздел «Auth flow (cookie + ws-ticket + logout lifecycle)»
- `CHANGELOG.md [Unreleased]` — M03b секция: Added/Changed(breaking)/Fixed/Documentation

Для всех milestones:
- `docs/report-before-v0.0.0/99-executive-summary.md` — roadmap + cluster IDs
- `docs/report-before-v0.0.0/OWNER-ANSWERS.md` — решения владельца
- `docs/report-before-v0.0.0/COVERAGE-AUDIT.md` — 354 пункта, «Closed in» колонка
- `docs/milestones/README.md` — индекс milestones + статусы
