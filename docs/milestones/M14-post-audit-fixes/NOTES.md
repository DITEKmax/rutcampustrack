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

**G2 surprise — версия appleboy/ssh-action:** hand-off зафиксировал
target `v1.2.0`, pre-flight `curl /releases/latest` показал `v1.2.5`
(maintainer выпустил три patch-релиза за время подготовки M14 PLAN'а).
Использована актуальная `v1.2.5` чтобы Renovate не bump'нул немедленно
после merge'а. Tag `v1.2.5` оказался lightweight (ref direct → commit
SHA `0ff4204d59e8e51228ff73bce53f80d53301dee2`), без extra dereference
шага через `/git/tags/`. `gh` CLI отсутствовал в bash PATH — использован
`curl https://api.github.com/...` напрямую.
