# M03a Decisions

Micro-ADR формата «решение + причина» для выборов, которые НЕ покрыты
OWNER-ANSWERS.md. Каждая запись — 5-10 строк, не больше.

Не дублировать сюда:
- Решения из OWNER-ANSWERS.md (на них ссылаются через Q-ID / P2-N/M).
- Общие архитектурные принципы (они в `docs/architecture.md` / CLAUDE.md).
- Детали реализации (они в коде + DECISIONS не для how, а для why).

Дублировать сюда:
- Выборы между равнозначными опциями.
- Отклонения от типового подхода с пояснением.
- Trade-off'ы, которые будут актуальны через полгода.

---

## 2026-04-19 — Разделение M03 на M03a + M03b

**Выбрано:** (b) разбиение на M03a (Internal JWT + rate-limit, 5-8д) и
M03b (JWT cookie + ws-ticket + logout, 8-12д).
**Отвергнуто:** (a) монолитный M03 на ~14-18д с одним breaking change релизом.
**Причина:** Промежуточный tag `v0.0.0-alpha.3` после M03a даёт раннюю
стабилизацию Internal JWT и rate-limit без риска откатывать весь cookie-flow
если что-то сломается во frontend migration. Breaking change остаётся одним
релизом (M03b), но Internal JWT уходит раньше и независимо.
**Последствия:** Два post-mortem'а + два bug-hunter прохода (удвоение overhead
на ~1д). M03b depends-on M03a (Internal JWT — prerequisite для ws-ticket
endpoint защиты). README.md обновляется — M03 → M03a/M03b в таблице.

---

## 2026-04-19 — Token Exchange endpoint (a3) — исходное решение пересмотрено после Group 1 discovery

**Выбрано:** (a3) **Token Exchange паттерн** — приватный ключ остаётся
ТОЛЬКО в auth-service. Gateway НЕ подписывает Internal JWT сам — вместо этого
дёргает новый endpoint `POST /internal/issue-internal-jwt` на auth-service с
shared secret (`INTERNAL_ISSUER_SECRET` env var). Gateway кэширует
полученный JWT per-user на ~4 минуты (TTL < 5 мин токена) в in-memory
cache — на cache miss +1 network hop (~2-5ms в private-net), на cache hit
zero overhead. Инвалидация кэша при logout — Redis pub/sub (scope M03b).
**Отвергнуто:**
- (a1) shared env `JWT_PRIVATE_KEY_PEM` — breaking миграция auth-service с
  keyDir auto-gen на env var; env вариант — антипаттерн на prod (нет audit,
  попадает в `docker inspect`, CI logs).
- (a2) shared docker volume `/opt/rutcampustrack/keys` — Gateway получает
  file-level access к приватному ключу без audit, backup volume содержит
  секрет, масштабируется плохо.
**Причина:** Token exchange — индустриальный стандарт (RFC 8693, Google Cloud
IAM `iam.serviceAccounts.signJwt`, AWS STS). Principle of least privilege:
компрометация Gateway не даёт атакующему выпустить Internal JWT с
произвольными claims. Миграционный путь простой — когда (если) проект
дорастёт до Vault/STS, (a3) с shared secret мигрирует в (a3) с mTLS без
переписывания логики кэширования. (a1)/(a2) — shortcuts, создающие tech debt.
**Последствия:**
- Auth-service: новый endpoint `POST /internal/issue-internal-jwt`
  (authenticated by shared secret header `X-Internal-Issuer-Secret`), принимает
  claims `userId/role/groupId/isHeadman`, возвращает подписанный JWT с
  `iss=rutcampustrack-auth`, `aud=rutcampustrack-internal`, TTL 5 мин.
- Auth-service `JwtService` расширяется методом `generateInternalToken(claims)`.
- Gateway: новый компонент `InternalJwtIssuerClient` — WebClient + Caffeine
  cache (`user:${userId}:${role}` → JWT, expireAfterWrite 4 мин).
- Gateway filter `InternalJwtIssuerFilter`: на каждый request достаёт JWT из
  cache либо дёргает endpoint.
- `.env.prod.example` (M06) документирует `INTERNAL_ISSUER_SECRET` (32 bytes)
  — рядом с существующим `GRPC_SECRET`. Ротация — sync restart обоих сервисов.
- PLAN.md Группа «Gateway issuer» переименовывается в «Gateway issuer client»,
  Группа «Auth-service issuer endpoint» — новая, 4-6 задач.
- Estimate +1 день (token exchange тесты сложнее: нужно мокировать WebClient
  в Gateway и тестировать cache semantics).

## 2026-04-19 — Default `legacy-headers-enabled=true` + strict toggle последним commit'ом

**Выбрано:** (a) `legacy-headers-enabled: true` по-умолчанию в prod при первом
деплое M03a. Переключение на `false` — отдельным commit'ом после UAT golden
path, последний commit milestone'а перед тегом `v0.0.0-alpha.3`.
**Отвергнуто:** (b) `false` сразу — breaking change с первого деплоя без
rollback-safety.
**Причина:** Двухшаговый rollout снижает риск — если Gateway issuer упадёт
после деплоя, legacy `X-User-*` сохранит работу системы до hotfix. UAT
между двумя commit'ами даёт возможность убедиться что Internal JWT flow
действительно работает для всех ролей.
**Последствия:** Группа 13 CHECKLIST — strict toggle как отдельный commit.
UAT checklist (admin/teacher/student/headman golden path) обязателен перед
toggle. После toggle — `X-User-*` strip в Gateway issuer filter.

## 2026-04-19 — Header name `X-Internal-Token` для Internal JWT

**Выбрано:** `X-Internal-Token: <jwt>` — отдельный custom header.
**Отвергнуто:** `Authorization: Internal <jwt>` (scheme-based, как Bearer).
**Причина:** (1) Не конфликтует с proxy middleware, которые могут перезаписать
или прокинуть внешний `Authorization: Bearer`. (2) В логах чётко видно что
это internal-only trust boundary — не путается с user-facing auth. (3)
Downstream-фильтр проще: `request.getHeader("X-Internal-Token")` вместо
парсинга `Authorization` scheme. (4) OWNER-ANSWERS 02-Q2 допускает оба
варианта — выбор технический.
**Последствия:** `InternalJwtValidator` читает из `X-Internal-Token`.
`InternalJwtIssuerFilter` в Gateway кладёт туда. `shared-logback` masking
добавляет правило для `X-Internal-Token` (наряду с `Authorization` из M01).
Документация в `docs/internal-jwt-spec.md` — header name как контракт.

---

_Формат записи:_

## YYYY-MM-DD — Короткое название решения

**Выбрано:** X
**Отвергнуто:** Y, Z
**Причина:** одна-две фразы.
**Последствия:** что теперь иначе в будущем (опционально).
