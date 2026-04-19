# M03a Notes

Живой файл. Пиши сюда:
- **Отклонения от плана:** «решил сделать X вместо Y, потому что...»
- **Измерения:** «p95 latency до: 450ms, после: 120ms»
- **Surprises:** «обнаружил, что Gateway уже держит RSA keypair, а я думал нужно из auth-service тянуть»
- **Вопросы к владельцу:** «fail-open при Redis down — логировать WARN или ERROR?»
- **Технические долги:** «оставил TODO про X — закрою в M{X}»

Не пиши:
- Общие описания модулей (это в PLAN.md).
- WHY-обоснования (это в OWNER-ANSWERS.md и 99-executive-summary.md).
- Пошаговые инструкции (это в CHECKLIST.md).

---

## 2026-04-19 — Старт M03a

- M03 (original, ~14-18д) разделён на M03a (Internal JWT + rate-limit, 5-8д)
  и M03b (JWT cookie + ws-ticket + logout, 8-12д). Решение владельца —
  промежуточный тег v0.0.0-alpha.3 между ними, снижение риска breaking change.
- M02 закрыт коммитом `da38ef3` (2026-04-19), tag `v0.0.0-alpha.2` ещё не поставлен
  (нужно проверить и сделать).
- PLAN/CHECKLIST заполнены из аудита, dual-mode flag default = `true` в M03a
  (breaking переключение — последний commit перед тегом).
- Tag `v0.0.0-alpha.2` уже установлен — ОК.

## 2026-04-19 — SURPRISE: текущая keypair-архитектура не match решение DECISIONS #1

**Discovery Группы 1 (grep):**
- `auth-service/.../JwtService.java:47-83` — keypair **генерируется в `@PostConstruct`**
  из `jwtProperties.keyDir()` (`/app/keys/private.key` + `public.key` + `kid.txt`).
  Если файлов нет — генерит RSA 3072-bit, пишет на диск, кэширует PEM в Redis
  (`jwt:public_key` TTL 1h). Если есть — читает с диска. Env var `JWT_PRIVATE_KEY_PEM`
  НЕ существует.
- `api-gateway/.../PublicKeyConfig.java:40-70` — Gateway только **read-only consumer**:
  пуллит PEM через `WebClient` на `http://auth-service:9090/auth/public-key` каждый
  час (`@Scheduled(fixedRate = 3_600_000)`), хранит в `AtomicReference<PublicKey>`.
  Приватного ключа у Gateway нет сейчас.
- `JwtAuthenticationFilter.java:86` — Gateway использует public key только для ВАЛИДАЦИИ
  входящего внешнего JWT. Gateway ничего не подписывает.

**Что это меняет в DECISIONS #1:** исходное решение (a) «Shared RSA keypair + env var
`JWT_PRIVATE_KEY_PEM`» требует миграции auth-service с keyDir на env var. Это отдельный
architectural risk — keys сейчас persisted volume в prod (`docker-compose.prod.yml`
mounted `/opt/rutcampustrack/keys:/app/keys`), env var — stateless.

**Реальные варианты (с учётом discovery):**
- **(a1)** Мигрировать auth-service на env var `JWT_PRIVATE_KEY_PEM` + `JWT_PUBLIC_KEY_PEM`,
  удалить keyDir auto-gen. Gateway читает ту же env var. Breaking в деплое — нужно
  вытащить текущие ключи из VPS volume, положить в `.env.prod`, удалить volume. Ротация
  через env + рестарт обоих.
- **(a2)** Оставить keyDir в auth-service, **добавить shared volume** `/opt/rutcampustrack/keys`
  mounted в оба сервиса (RO для Gateway). Gateway читает `/app/keys/private.key` напрямую.
  Минимум изменений, но Gateway получает file access вместо network.
- **(a3)** **Gateway НЕ подписывает.** Auth-service получает новый internal endpoint
  `POST /internal/issue-internal-jwt` (авторизация — shared secret между Gateway и
  auth-service через env `INTERNAL_ISSUER_SECRET`). Gateway на каждый запрос (или
  раз в 4 мин с TTL-cache) дёргает endpoint с claims `userId/role/groupId`, получает
  подписанный Internal JWT. Приватный ключ остаётся ТОЛЬКО в auth-service. Trade-off —
  +1 network hop per request (или кэш с TTL 4 мин, invalidation при logout).

**Рекомендация: (a2)** — shared volume. Минимум кода (Gateway не дёргает сеть per
request), минимум миграции (keyDir остаётся), приватный ключ не публикуется нигде
кроме volume в VPS, ротация = подменить файл + `docker compose restart`. Risk —
Gateway получает file read access к секрету, но они уже в одной private-net
через docker-compose.

**Вопрос владельцу:** (a1 / a2 / a3)? Жду решения.
