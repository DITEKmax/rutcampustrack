# M14 Notes

Живой файл. Сюда — отклонения от плана, измерения, surprises, вопросы
к владельцу, технические долги для будущих milestones.

---

## 2026-04-26

- Стартовая позиция: M13 ✅, тег `v0.0.0-alpha.15`. Четыре аудита (G26 code review,
  G26 test audit, G27 CSO comprehensive, G27 tech debt) дали ~70 findings. Триаж
  показал что только **9 групп** действительно блокируют first VPS deploy либо
  имеют trivial fix — остальное в `docs/future-ideas.md` (pre-v0.1) либо в
  `docs/deferred-ideas.md` (v0.1+).
- Принципы триажа:
  1. **Блокирует deploy функционально** (HIGH-05 PKCS#1 → first deploy упадёт) — must.
  2. **Runtime guard против operator mistake** (HIGH-06 fail-fast secrets) — must.
  3. **Окно эксплуатации между deploy и first user** (CRIT-01 IDOR на private_net) — must.
  4. **Trivial cost / non-trivial impact** (CRIT-02 SHA-pin, HIGH-07 aiohttp bump) — must.
  5. **CI gate compromised без fix** (G26 false-pass tests) — must, иначе зелёное CI = ложное чувство безопасности.
  6. **Performance / scaling tech debt** — gate'нуто на real-user signal (Grafana latency на teacher journal, решение о horizontal scale).
- Оценка: 3-4 часа на всё M14 если без surprises. Один сеанс.

### Открытые решения

- **G7 категория E (`role-student.spec.ts`):** путь A (удалить 2 теста) или путь B (seed `student_plain`)?
  - **Path A cost:** ~5 мин, минус 2 теста coverage негативного RBAC.
  - **Path B cost:** ~30-45 мин (Flyway seed migration в test profile + fixtures + verify).
  - **Lean toward A:** RBAC уже покрыт `SecurityIdorIT` на backend; e2e дублирующий тест избыточен. Запишу окончательное решение когда дойду до G7.

### Вопросы к владельцу

_(пока нет; добавлю если возникнут при выполнении)_

### Технические долги, открываемые в M14

**G1 surprise — `application-test.yml` асимметрия между сервисами:**
academic/schedule/attendance имеют `application-test.yml` с явным
`legacy-headers-enabled: true` (artifact M03a — когда defaults флипались
впервые, тесты получили локальный override, чтобы не переписывать на
Internal JWT). Notification-app аналогичного override НЕ имел — пришлось
добавить inline через `@SpringBootTest properties` в SecurityIdorIT.
Долгосрочное решение — мигрировать все IDOR/security IT на `InternalJwtTestFactory`
из shared-security testFixtures (тогда test-profile override становится
ненужным). Записано в `docs/deferred-ideas.md` как кандидат на v0.1
test cleanup PR.

### Измерения

- **G1 IT runtime:** 5m33s (4× SecurityIdorIT + 3× *StrictModeIT параллельно через single-task gradle invocation, `--no-daemon`).
- **G1 commit footprint:** 5 application.yml + 1 .env.prod.example + 1 test fixup = 7 files / 34 insertions / 10 deletions.
- **G2 commit footprint:** 1 file (`deploy.yml`) / 1 insertion / 1 deletion. Минимальный возможный diff.
- **G3 commit footprint:** 1 file (`deploy.yml`) / 27 insertions / 3 deletions (большая часть — комментарии-документация почему именно такой pipeline).
- **G3 dry-run runtime:** ~6s на pipeline (alpine pull + apk add openssl + 3072-bit RSA + pkcs8 + rsa pubout); JDK standalone parse ~1s. Полный verification cycle <30s.

**G3 surprise #1 — OpenSSL 3.x уже выдаёт PKCS#8 by default:** baseline
dry-run на `alpine:latest` (OpenSSL 3.5.6) показал что bare `openssl genrsa`
УЖЕ выдаёт `-----BEGIN PRIVATE KEY-----` (PKCS#8). На `alpine:3.13`
(OpenSSL 1.1.1q) — `BEGIN RSA PRIVATE KEY` (PKCS#1). Поведение
изменилось в OpenSSL 3.0: PKCS#1 теперь требует explicit `-traditional`.
Поэтому CSO HIGH-05 finding концептуально правильный, но **на современном
deploy не воспроизводится** — implicit зависит от alpine major version.
Fix всё равно нужен: explicit `pkcs8 -topk8` снимает implicit dependency
на OpenSSL major version (если кто-то запинит alpine на старую версию,
deploy всё равно сработает).

**G3 surprise #2 — race-edge в старой idempotency guard:** старая форма
`([ -f X ] || gen X) && ([ -f Y ] || gen Y)` имела edge case: если
private.key существует, а public.key нет (interrupted deploy), то
public.key регенерится **из существующего private.key** — пара
mismatch'нется только если private изменился между запусками, что не
наш случай. Но на edge: если оба файла существуют, но один corrupt —
старая логика их **не** регенерит. Новая форма `if [ ! -f private.key ]
then gen-all` атомарна: либо все 4 файла генерятся одним блоком, либо
никаких. Чище семантически.

**G3 dry-run обходные пути на Windows/Git Bash:** Git Bash MSYS path
conversion ломает absolute Linux paths в `docker run`/`docker cp`
(`/keys/x` → `C:/Program Files/Git/keys/x`). Workaround: prefix
`MSYS_NO_PATHCONV=1` для bash-инкарнации, либо PowerShell tool
для нативных Windows paths в `docker cp` host-side. Записано в
`docs/deferred-ideas.md` как кандидат на dev-handbook entry.

**G3 standalone Java verification — приём для будущих GSD audits:**
вместо тяжёлого `docker compose up auth-service`, сделан минимальный
clone `JwtService.loadPrivateKey/loadPublicKey` (40 строк), который
парсит ключ через `PKCS8EncodedKeySpec`/`X509EncodedKeySpec` ровно
теми же `KeyFactory.getInstance("RSA")` API что production code. Если
clone парсит — production code тоже парсит (та же JDK 21, те же specs).
Negative test (PKCS#1) — `InvalidKeySpecException` подтверждает что
clone семантически корректный.

### Group 4 — DEFERRED post-mortem (~1ч инвестировано, всё откатано)

**Цель:** заменить `${SECRET:dev_default}` → fail-fast в 6 application.yml,
чтобы deploy без `--env-file` упал с явной ошибкой вместо silent boot
на dev secrets.

**План:** простой YAML edit в 6 файлах + UAT через docker run без env.

**Что прошло как ожидалось:**
- Inventory dev fallback'ов через grep дал 21 точку (POSTGRES_*, REDIS,
  RABBITMQ, INTERNAL_ISSUER, GRPC, ALERT_WEBHOOK, MONGODB_URI).
- Все 6 YAML edits применились чисто, gradle test для всех 6 сервисов
  прошли BUILD SUCCESSFUL (testcontainers @DynamicPropertySource override
  работает корректно).
- Rebuild auth image (~6 мин) + UAT setup без проблем.

**Где прокололся:**

1. **Использовал `${VAR:?msg}` syntax** — это **bash variable substitution**,
   НЕ Spring Boot. Spring трактует `?msg` как часть default value
   (literal string `"?msg"` is the default). НИЧЕГО не fail-fast.

2. **Тогда переключился на `${VAR}` без default** (на основе web-search,
   утверждавшего что Spring fail-fast). Сделал regex-replace через Python
   во всех 6 файлах, rebuild, повторный UAT. **ТОЖЕ не сработал**:
   placeholder остаётся literal `"${VAR}"` string в значении property,
   Spring не throws.

3. **Доказательство в логах**: `IllegalStateException:
   rutcampustrack.security.internal-issuer.secret must be at least 32
   bytes (got 25)` — 25 символов = ровно длина literal
   `"${INTERNAL_ISSUER_SECRET}"`. То есть Spring resolved placeholder в
   literal string и отдал в `InternalIssuerProperties.validate()`,
   который **поймал through length check**. Если бы length check там
   не было (как у других secrets), Spring продолжил бы boot с broken
   value.

**Почему web-search ввёл в заблуждение:** результаты ссылались на
`@Value` annotations + `.properties` files (где Spring дей действительно
fail-fast). Для **YAML** + **`@ConfigurationProperties`** поведение
другое — placeholder остаётся literal. Это zaregistrated issue
[spring-boot#10463](https://github.com/spring-projects/spring-boot/issues/10463) и
[#18816](https://github.com/spring-projects/spring-boot/issues/18816),
открыто с 2017+.

**Решение для M14:** **defer всю G4** в pre-v0.1. Спецификация в
`docs/future-ideas.md` § «CSO HIGH-06: fail-fast secrets через
ApplicationContextInitializer» с двумя вариантами impl (Java initializer
vs bash entrypoint). Полный revert YAML changes через `git checkout`.

**Mitigation в v0.0.0 (без G4):**
- M13 G15 preflight script + .env.prod validator ловит missing env
  vars **ДО** SSH deploy → primary защита от operator forgetting env-file.
- `InternalIssuerProperties.validate()` runtime check на critical secret.
- `GrpcSecretFailFast` test contract (M08).
- Healthcheck unhealthy в течение 30-60 сек → second signal.

**Урок для будущих сессий:** при работе с syntax-критичными изменениями
(`${VAR:?...}` vs `${VAR}`) — первый шаг это **доказательный UAT**, не
gradle test. Gradle tests passed — но они не testят production
boot path с missing env. Только реальный `docker run` без env
показал что fix не работает.

**Footprint G4 v1:** 0 commit'ов functional changes (всё revert'нуто).
1 commit docs (CHECKLIST + NOTES + future-ideas + NEXT-SESSION).

### Group 4 v2 — SUCCESS (через час после v1, 2026-04-26)

**Trigger от пользователя:** "давай сделаем правильнее ... зачем плодить
legacy и потом фиксить то что лучше ща зафиксить. а то потом будет прод
и тд нехорошо". Хороший pushback — defer'ить было прагматично, но
закрыть G4 properly **сейчас** ещё лучше для prod safety.

**v2 design:**
- `RequiredSecretsValidator implements EnvironmentPostProcessor` в
  `shared-web/autoconfigure/`. 95 строк, slf4j + Spring API.
- Регистрация через `META-INF/spring.factories` (не AutoConfiguration —
  EnvPostProcessor работает раньше, ДО bean creation).
- Profile-aware skip: `test`/`local` → no-op (existing tests + local dev
  не страдают).
- Per-service opt-in через `rutcampustrack.security.required-env-vars`
  CSV property — каждый сервис сам указывает свои critical secrets.

**Почему EnvironmentPostProcessor а не ApplicationContextInitializer:**
EnvPostProcessor вызывается **раньше** (`ApplicationEnvironmentPreparedEvent`),
ДО Spring banner и ДО создания любых beans. Failure → container exit
immediately, без затрат CPU на Tomcat init / RSA key generation /
Lettuce client setup. ContextInitializer — ещё на milisecondы позже,
после Environment fully ready. Для validation env vars — EnvPostProcessor
правильнее.

**Test profile bypass через `getActiveProfiles()`** — не через
DynamicPropertySource (тот в EnvPostProcessor ещё не виден). Все
существующие IT в проекте используют `@ActiveProfiles("test")` (verified
grep 10+ файлов), поэтому unconditional bypass без false-positives.

**api-gateway dependency surprise:** gateway не имеет `shared-web`
(servlet-only), но мне нужен EnvPostProcessor там. Решение: добавил
`implementation(":services:shared:shared-web")`. SharedWebAutoConfiguration
сам остаётся inactive (ConditionalOnWebApplication SERVLET — false для
WebFlux), но `META-INF/spring.factories` registration работает
**независимо** от auto-config conditions. Транзитивно приходит
`spring-security-core` (~200KB) — приемлемо.

**UAT — самое важное:**

```bash
docker run --rm rct-auth-uat:m14g4v2 2>&1 | head -5
# 13:36:31.189 [main] ERROR org.springframework.boot.SpringApplication -- Application run failed
# java.lang.IllegalStateException: M14 G4 (CSO HIGH-06): required environment variables are not set:
# [REDIS_PASSWORD, SPRING_RABBITMQ_PASSWORD, POSTGRES_ACADEMIC_PASSWORD, INTERNAL_ISSUER_SECRET, TMA_BOT_TOKEN].
# These secrets must be provided via `--env-file .env.prod` or explicit `-e VAR=value`.
# If running tests, ensure @ActiveProfiles("test") is set on the test class.
```

**До Spring banner — 0 ASCII art, 0 logger init, 0 bean creation.**
Падает на `EnvironmentPostProcessorApplicationListener.onApplicationEnvironmentPreparedEvent`
→ container exit. Это **именно** то поведение, которое нужно operator'у:
hard signal forgot env-file, immediate, actionable.

**Verification matrix:**
| Test | Expected | Actual |
|------|----------|--------|
| 0 of 5 vars | IllegalStateException, list 5 | ✅ |
| 3 of 5 vars (missing 2) | IllegalStateException, list ровно 2 | ✅ |
| All 5 vars | validator passes, fail дальше на JwtService.init (orthogonal) | ✅ |
| `@ActiveProfiles("test")` | validator skip → existing IT не сломан | ✅ (4m51s gradle test 7 modules SUCCESSFUL) |
| `application-test.yml` без override secrets | OK через test profile skip | ✅ |

**Footprint G4 v2:**
- 1 commit functional: `bf915ec` (10 files / 270 insertions, +1 deletion)
  - 1 new Java class (95 строк)
  - 1 new test file (9 tests, ~120 строк)
  - 1 META-INF/spring.factories update
  - 1 build.gradle.kts (api-gateway dependency)
  - 6 application.yml (per-service required-env-vars property)
- 1 docs commit (CHECKLIST status update + NOTES post-mortem v1+v2 +
  future-ideas pre-v0.1 entry удалить + NEXT-SESSION rotate на G5)

**Урок:** **defer-vs-fix decision** должен делать **пользователь**, не
агент. Я выбрал defer как pragmatic, но user push к "сделать правильно
сейчас" дал намного лучший результат — G4 закрыта в той же сессии,
~2-3 часа vs theoretical pre-v0.1 work, и prod safety guarantee
получена сразу.

**G2 surprise — версия appleboy/ssh-action:** hand-off зафиксировал
target `v1.2.0`, pre-flight `curl /releases/latest` показал `v1.2.5`
(maintainer выпустил три patch-релиза за время подготовки M14 PLAN'а).
Использована актуальная `v1.2.5` чтобы Renovate не bump'нул немедленно
после merge'а. Tag `v1.2.5` оказался lightweight (ref direct → commit
SHA `0ff4204d59e8e51228ff73bce53f80d53301dee2`), без extra dereference
шага через `/git/tags/`. `gh` CLI отсутствовал в bash PATH — использован
`curl https://api.github.com/...` напрямую.
