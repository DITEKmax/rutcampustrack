# Промпт для следующей сессии — M13 G25.14: проверить CI после SHA1PRNG fix, продолжить fix-cycle если красный

Скопируй всё ниже в новый чат с Opus 4.7 (1M context). Opus сам
откроет нужные файлы и продолжит.

---

**M13 G25.14 запушен (`cd0e1d2` на origin/dev). CI запустился сразу
после push. На момент написания hand-off результат ещё неизвестен —
проверить в новой сессии. Если зелёный → tag `v0.0.0-alpha.15`. Если
красный → fix-cycle продолжается.**

## Текущая позиция (на конец сессии 2026-04-25 evening)

**14 коммитов G25 push'нуты. Последняя сессия добавила 3 fix'а:**

| Коммит | Группа | Что сделано | CI status |
|--------|--------|-------------|-----------|
| `5f4378c` | G25.12 | `infra/mongo/init-mongo.js` mount в обоих compose, удалены `MONGODB_EXTRA_USERNAMES/PASSWORDS/DATABASES` | ✅ MONGO FIX РАБОТАЕТ (CI #134 attendance-service `Up 2 minutes (healthy)`) |
| `ef21c6c` | G25.13 | `SecureRandom.getInstance("NativePRNGNonBlocking")` в `JwtService` | ❌ не помогло (auth-service всё ещё hang на RSA gen) |
| `cd0e1d2` | G25.14 | Прямое чтение 32-байт seed из `/dev/urandom` + `SHA1PRNG` | 🟡 ожидает CI результата |

## Что делать в этой сессии (по порядку)

### Шаг 1 — пользователь скинет лог CI или скажет "всё хорошо"

Жду ответ от пользователя в первом сообщении сессии. Два сценария:

**Сценарий A: пользователь говорит "всё зелёное" / "passed"**

Tag и финал G25:
```bash
git fetch origin
git checkout dev
git pull origin dev
git tag -a v0.0.0-alpha.15 -m "M13 G25 ✅ — CI green: ruff + watchdog + e2e compose + Docker shared modules + git fallback + Mongo init-script + SHA1PRNG urandom seed"
git push origin v0.0.0-alpha.15
```

Затем переход к **Шагу 3 оригинального плана**: Live VPS dry-run по
`docs/prod-deploy-checklist.md` (owner-driven). Findings → M13 NOTES.md
G23 секция.

**Сценарий B: auth-service всё ещё hang на RSA**

Это означает что даже **прямое чтение `/dev/urandom`** не работает на
GitHub Actions Azure runner. Гипотезы:
1. `/dev/urandom` не существует в alpine container (маловероятно — это
   стандарт Linux). Проверить через `docker exec rct-auth-service ls -la /dev/urandom`.
2. `Files.exists(urandom)` возвращает true, но `Files.newInputStream`
   блокируется. Тоже маловероятно.
3. **SHA1PRNG.setSeed() сам по себе НЕ заменяет дефолтный seed —
   он его augment'ит.** Если SHA1PRNG instantiation упирается в
   `/dev/random` для initial seed → hang остаётся. Тогда нужен
   **последний резервный план**: pre-generate keys в Dockerfile через
   `openssl genrsa` (build-time, не runtime). Security regression только
   для CI/e2e, prod compose использует named volume `jwt-keys` который
   на чистом VPS pre-fills через docker volume init.

**Резервный план G25.15** (если нужен):
```dockerfile
# В services/auth-service/Dockerfile перед USER app:
RUN apk add --no-cache openssl && \
    mkdir -p /keys && \
    openssl genrsa -out /keys/private.key 3072 && \
    openssl rsa -in /keys/private.key -pubout -out /keys/public.key && \
    head -c 8 /dev/urandom | xxd -p > /keys/kid.txt && \
    chown -R app:app /keys && \
    chmod 600 /keys/private.key && \
    apk del openssl
```

`openssl` использует системный `/dev/urandom` напрямую, не страдая от JDK
provider quirks. Build-time generation в layered image: эта layer
кешируется, но JwtService.init() при старте увидит существующие keys и
пойдёт по `Files.exists()` ветке без regeneration. Для **e2e** это
acceptable: ключи одинаковые во всех контейнерах одного билда, для
**prod** named volume переопределяет (RUN копирует в image, named volume
mount override'ит на пустой volume — но при первом запуске Docker
скопирует image content в empty volume, поэтому prod **тоже получит
pre-generated keys**, что нормально для VPS first-deploy).

Альтернатива: явно отключить prod-shared keys через env-флаг в e2e —
например `JWT_SKIP_KEYGEN=true` или вынести генерацию в init-container.
Но это большее изменение.

### Шаг 2 — после CI зелёный → tag → продолжить оригинальный план

После `v0.0.0-alpha.15` ✅:
- **Шаг 3 (оригинальный)**: Live VPS dry-run по `docs/prod-deploy-checklist.md`.
- **Шаг 4 (оригинальный)**: Tag `v0.0.0` GA + bump version в root
  `build.gradle.kts` + `frontends/*/package.json` на `0.0.0`. Push tag.

## Контекст недавно решённых блокеров (G25.12-14)

### G25.12 — Mongo `UserNotFound: rct_user@admin` (CI #133 attendance fail)

**Проблема**: Bitnami `MONGODB_EXTRA_USERNAMES` в digest `sha256:16a57fa`
создаёт users в `test` DB вместо `admin`. Spring подключается с
`?authSource=admin` → `AuthenticationFailed`. Prod на VPS работал
только потому что mongo-data volume содержал users из старого
init-mongo.js (commit `d6c0f14`/`6c1493f`), на чистом эфемерном CI
volume пусто.

**Fix**: восстановлен `infra/mongo/init-mongo.js` (one-to-one с pre-G7
версией), mount в **обоих** `docker-compose.e2e.yml` и
`docker-compose.prod.yml` через `/docker-entrypoint-initdb.d/init-mongo.js:ro`.
Удалены `MONGODB_EXTRA_USERNAMES/PASSWORDS/DATABASES`. Idempotent
(Bitnami пропускает initdb scripts при non-empty data dir).

**Подтверждено в CI #134**: attendance-service `Up 2 minutes (healthy)`,
mongo-attendance `Up 3 minutes (healthy)`. Mongo блокер closed.

### G25.13 — RSA hang (попытка #1, неудачная)

**Гипотеза**: KeyPairGenerator.initialize(3072) с default-SecureRandom
блокируется на `/dev/random`, флаг `-Djava.security.egd=file:/dev/./urandom`
игнорируется некоторыми provider'ами.

**Попытка**: явный `SecureRandom.getInstance("NativePRNGNonBlocking")`.

**Результат**: НЕ ПОМОГ. CI #134 auth-service лог обрывается на той же
строке `"Generating new RSA 3072-bit key pair in: /keys"` (3+ минуты
hang, healthcheck timeout, dependency chain валится).

**Вывод**: на alpine musl JDK 21 ВСЕ native-PRNG provider'ы под капотом
читают `/dev/random` при init/reseed, не реагируя на JVM flag.

### G25.14 — RSA hang (попытка #2)

**Подход**: bypass JDK provider abstraction полностью. Прочитать 32 байта
seed напрямую из `/dev/urandom` (Linux gurantee: NEVER blocks) и
seedить SHA1PRNG (software-only после seed). Windows fallback на
default SecureRandom (там Crypto API non-blocking).

```java
private static SecureRandom nonBlockingSecureRandom() {
    Path urandom = Paths.get("/dev/urandom");
    if (Files.exists(urandom)) {
        try {
            byte[] seed = new byte[32];
            try (var is = Files.newInputStream(urandom)) {
                int read = is.read(seed);
                if (read != seed.length) {
                    throw new IOException("Short read from /dev/urandom: " + read);
                }
            }
            SecureRandom random = SecureRandom.getInstance("SHA1PRNG");
            random.setSeed(seed);
            return random;
        } catch (IOException | NoSuchAlgorithmException e) {
            log.warn("Failed to seed SHA1PRNG from /dev/urandom, falling back to default SecureRandom", e);
        }
    }
    return new SecureRandom();
}
```

Файл: `services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/service/JwtService.java:185-205`.

**Ожидаю**: ~1-2 секунды между `"Generating new RSA 3072-bit"` и
`"RSA key pair ready (kid=...)"`. Healthcheck green в течение 60s
start_period.

**Риск**: SHA1PRNG.setSeed() **augment'ит** seed, не заменяет — если
SHA1PRNG instantiation само упирается в `/dev/random` для initial seed,
hang остаётся. Тогда → G25.15 резервный план (см. выше).

## Local state на момент hand-off

```
Working tree: clean
Branch: dev (synced with origin)
Last commit: cd0e1d2 fix(auth): seed SHA1PRNG из /dev/urandom напрямую (M13 G25.14)
```

Никаких uncommitted изменений. CI ожидает результата на `cd0e1d2`.

## Что было раньше (G25.1..G25.11) — кратко

| G25.x | Commit | Fix |
|-------|--------|-----|
| G25.1 | `981f2b1` | ruff format compliance (9 .py файлов) |
| G25.2 | `b373b3e` | watchdog mock signature `idempotency_guard=None` |
| G25.3 | `be71ed1` | docker-compose.e2e.yml + self-signed TLS + CI integration |
| G25.4 | `346c147` | CHANGELOG + e2e-testing.md docs |
| G25.5 | `549e9dc` | `COPY services/shared` в 6 backend Dockerfiles |
| G25.6 | `ed40d36` | `gitOutput()` try/catch IOException |
| G25.7 | `7d794db` | `MONGODB_REPLICA_SET_KEY` в base64-алфавите |
| G25.8 | `de7d3d9` | Dump docker logs для exited контейнеров в CI |
| G25.9 | `6d31674` | `RUN mkdir -p /keys && chown app:app /keys` в auth Dockerfile |
| G25.10+11 | `0e7d91f` | Mongo digest pin + JAVA_TOOL_OPTIONS java.security.egd urandom |
| G25.12 | `5f4378c` | init-mongo.js (Mongo блокер ✅) |
| G25.13 | `ef21c6c` | NativePRNGNonBlocking (НЕ помог) |
| G25.14 | `cd0e1d2` | SHA1PRNG + /dev/urandom direct seed (ожидает CI) |

## История milestone'ов (архив)

M01-M08 ✅ (`v0.0.0-alpha.1..alpha.9`).
M09-M12 ✅ 2026-04-24 (`alpha.10..alpha.13`).
M13 Pre-Deploy Hardening ✅ 2026-04-25 (`v0.0.0-alpha.14`).
**→ Группа 25** (CI hot-fixes + e2e infra) → ожидает `v0.0.0-alpha.15`.

Debt report (источник scope M13) — `docs/report-before-v0.0.0/v0.0.0-debt.md`.
Dependency graph и полный roadmap — `docs/milestones/README.md`.

## Pending decisions для new conversation

1. **CI #135 (cd0e1d2) зелёный?** Пользователь скинет лог или скажет
   результат в первом сообщении. Tag `v0.0.0-alpha.15` если зелёный.

2. **Если G25.14 не помог** — переход к G25.15 (pre-generate keys в
   Dockerfile через openssl). См. секцию "Резервный план G25.15" выше.
   Это последний реалистичный fix перед признанием что нужна smoke-suite
   с auth-service отключенным или дисабленным RSA generation.

3. **После tag → live VPS dry-run**. Owner-driven, не Claude. Опираться
   на `docs/prod-deploy-checklist.md`. Основные новые риски на VPS:
   - init-mongo.js теперь mount'ится — на существующем mongo-data volume
     init script пропустится (Bitnami idempotency), users остаются как
     были. На чистом VPS — script отработает и создаст users.
   - JwtService теперь читает `/dev/urandom` — на любом Linux VPS это
     работает out-of-the-box.
