# PR2 — actual failure corrections, 2026-10-21

**SOURCE_VERIFIED_PENDING_NEW_GATES.** Готовность PR не заявлена. Поручение разрешает исправления и публикацию в существующую PR-ветку, запрещает merge/main/deploy. Deploy257646584 disabled_manually. Старый checkpoint остаётся в Git на `bac3ebd8`; этот документ обновляет текущие факты, не стирает прежние FAIL/UNKNOWN.

## Scope and custody

Baseline `617e1e33`, main/merge-base `3908fad9`, предыдущий published HEAD `bac3ebd8`/executable `a44636ba`; новый проверенный source **`7bd0a527`**. Финальный taskdoc HEAD и результаты его запусков сообщаются отдельно. Root единственный Git/docs integrator; отдельные sole writers и read-only reviewers, запрос Sol6.1/high/forknone, actual model metadata UNKNOWN. Audit263 groups уже source-implemented; нового общего аудита или удаления suites нет.

Все23 переданных исходных ZIP сохранены побайтно248260B, проверены exact size/SHA256/CRC/paths и GitHub artifact/run/attempt/head bindings23/23.40JSON entries прочитаны строгим decoder. Исходные runs attempt1: push39514103147/PR39514114181/Coverage39514114251 на617. PR merge carrier `ddd8a5a2` имеет тот же Git tree; текущий bac3 carrier `d3a0c2ee` также tree-equal branch source. Commit SHA не переименованы друг в друга. Дополнительные bac3 originals получены напрямую через connector, exact API digest/size/paths/CRC/source проверены. Ручная передача пользователем больше не требуется.

Raw private XML/HTML/full logs, реальные secrets/PII и signed URLs не публикуются. ORDINAL_ONLY не превращается в SOURCE_METHOD. Aggregate browser FAIL — следствие, split-group SKIP — не PASS. Coverage test-step failure не доказывает низкий процент покрытия.

## Сохранённые предыдущие исправления

- `5cbe9b46`: Academic additive V12 явно отклоняет LESSON+NULL; V1–V11 и retained data не переписаны. DATE+NULL сохранён. Homework fixture создаёт required subject/type атомарно; Jobs fixture сохраняет transaction и обязательные V8 API fields/FAILED_SAFE.
- `23fc4a35`: Schedule by-ID current определяется durable occurrence pointer в read-only REPEATABLE_READ; historical superseded/legacy NULL/wire contracts сохранены.
- `6dca44d8`: bot canonical display/current authorization, правильные EXCUSE/LATE IDs и enum reason/new_name, HTML/UTF16 bounds, логи без raw recipient IDs. Keyboard locking/common4/role union/retry/ACK/DLQ не менялись.
- `a44636ba`: ровно5CI scalar source/frame bindings; predicates/status/count/identity/schema/privacy/thresholds/required gates не менялись.

V12 валидирует retained rows: существующий invalid LESSON+NULL остановит migration; данные не исправлялись и не удалялись. Fresh owned PG/migrations и отдельные current CI tests не заменяют upgrade/restore реальной среды.

Независимые Academic/Schedule/Bot/integration SOURCE_APPROVE сохранены на этих bytes. На bac3 actual Academic4 ранее исправленных SOURCE_METHOD прошли, recurring Schedule case прошёл. Python push/PR/Coverage и Notification unit/IT jobs SUCCESS; Notification PR64PASS/0FAIL. Это не доказательство исторической причины единственного617PR notification assertion.

## Новые доказанные причины и test-only corrections

| Область | Подтверждение причины | Минимальная correction и проверка |
| --- | --- | --- |
| Schedule2 | Обе native ошибки достигли authority INSERT LessonTransferWriter:279. Фиксированные январь/апрель2026 уже прошли относительно реального PG; V28:646 запрещает target_date<=CURRENT_DATE. Exact native exception/SQLState остаётся UNKNOWN. | `25e905f5`: nextMonday от SELECT CURRENT_DATE и согласованный Java Clock. Границы диапазона, canonical IDs,12000rows/exact32, history/current, own STUDENT, <=6queries/<1000ms сохранены. Native2/2PASS40.8s. `fab83837` только AOSP layout, javac AST/string identity проверена независимо; after-format native повтор не выдаётся за новый PASS. |
| Academic global HTTP500 | Native3 exact200→500. Закрытый внешний observer: requestAttributesBound=false, SCOPE_NOT_ACTIVE/ILLEGAL_STATE, AcademicUserContextFilter:68. Test-only AuthConfirmationHttpFixture.Mvc был @Configuration+EnableWebMvc и отключал Boot MVC/request-context auto-configuration при implicit scan. В production classpath helper отсутствует. | `02058dea`: @TestConfiguration(proxyBeanMethods=false), explicit context.register(Mvc.class) сохранён для настоящего Auth fixture. Native4/4PASS54.61s, включая wrong-password403 и неизменное durable состояние. Дальнейший ordinary23:19SUCCESS/4FAIL85.3s. Оба strict401 MockMvc negatives PASS. |
| Academic positive create3 | Все3 STUDENT inputs содержали null/omitted telegramId, что запрещает actual AcademicUserIdentityOperations.validate:1345. Surviving negative/positive Telegram tests сохраняют правило. Исходный runtime exception type/status в старой проекции UNKNOWN; HTTP400 не называется прочитанным фактом. | `9036600d`:3explicit positive synthetic IDs, pagination102unique, без product/DTO/helper изменения. Сохранены credentials/BCrypt/ownership, repeatable-read roster/late revocation, paging oracles. Native GroupRoster и Credential PASS; pagination дошёл до следующего assertion. |
| Academic Actuator | Boot3.5.16 test customizer отключает default metrics exporters; Prometheus auto-config условен. Production health/info/prometheus exposure и registry dependency существуют. | Тот же9036: class-local management.prometheus.metrics.export.enabled=true, exact5link oracle и все404 checks неизменны. Native все6 Actuator PASS. |
| Academic pagination | AssertJ3.27.7 doesNotContainAnyElementsOf требует непустой forbidden iterable; previous=Set.of() на первой странице. Native failure на65 после успешного создания102/HTTP200/total105/100rows; конкретный runtime exception остаётся UNKNOWN. | `7bd0a527`: separate hasSize + Collections.disjoint(ids,previous).isTrue обе итерации; cap100/105total/100+5/complete union сохранены. Native exact1PASS64.35s, без повторения остальных8. |

Новый diff после bac3 — **7 test/helper files**,0product/config/workflow/schema/dependency changes,0added/deleted paths. Все authored patches frozen/hash-checked, assertions сохранились; независимые source reviews и targeted AOSP/diff checks PASS. Native callbacks публиковали только exact identity/status/known class/source-frame/status-enum проекции, без error messages/bodies. Проверяемый checkout до/после clean.

Наборы Academic пересекаются и не суммируются: ordinary4/4; ordinary23=19PASS4FAIL; послеfixture corrections9=8PASS1FAIL; послеoracle correction pagination1PASS. Дополнительно оба прежних CacheIT failures прошли на7bd0:2/2PASS38.4s с отдельными own PG/AuthRedis/cacheRedis и исходными cache commit/invalidation assertions. Последние runs имели live witness observerClasspathPresent=false/observerSystemPropertyPresent=false. Полный541IT и3Rabbit-dependent Academic cases локально NOT_RUN; SUCCESS отдельных методов не заменяет whole-suite green.

## Runtime и ограничения

Первоначальный Docker EPERM относился к execution sandbox без стандартного additional-network grant. При штатном grant managed Docker доступен; Unix permissions/daemon/groups/config не менялись. Реальные PG/Redis адресные runs использовали fresh owned fixtures/migrations, один heavy lease,1Gradleworker/CPU2, own-RSS guard3GiB/wall600s/private-output128MiB/disk reserve4GiB. Ни один guard не сработал. Никакого доступа к production или provider.

Bot local verification: selected43 baseline28PASS15FAIL→corrected43PASS; пересекающийся107PASS; полная620 попытка610PASS10loopbackEPERM. Закрытый syscall discriminator подтвердил EPERM; exact10 при стандартном network grant10PASS. Это не единый620PASS run. Hosted bac3 Python jobs SUCCESS. Настоящий Telegram не выполнялся.

Rabbit native diagnostic: R1 framing исправлен только в external helper; затем собственная fixture не создалась до child. R2 с независимым review различил CREATE0 и START1/START_FAILURE_OTHER, state created/running=false/exit128/OOM=false; child invocations0. Readiness/native testcase NOT_RUN, specific START cause UNKNOWN. Owned container/network cleanup завершён, claim сохранён; caps/image/privileges не расширялись, дальнейшего обхода/повторного probe нет. Docker PG/Redis PASS не доказывает, что Rabbit может стартовать. Его локальный отказ не объявлен причиной hosted Monitoring FAIL.

## Последний опубликованный gate snapshot и остаток

Readback bac3: Security39522002428/OpenAPI39522002569 SUCCESS; Coverage39522002380/PRCI39522002462/pushCI39521996471 FAILURE, attempt1/completed. Новые7test bytes пока не имеют natural hosted результатов; финальные source/docs SHA и новые runs будут сообщены отдельно. Built-image Security scan SKIPPED, не PASS.

| Остаток на bac3 | Доказанные факты и неизвестность |
| --- | --- |
| Attendance | unit407PASS;IT255PASS2ORDINAL_FAIL1SKIP. Safe categories GRADLE_SUITE/OOM и ASSERTION; OOM subtype/JVM role/effective limits/firststage UNKNOWN. Ordinal mapping и guess heap/test patches не выполнялись. Для cause-directed correction нужен закрытый subtype/role/limits либо first-source-stage witness. |
| Monitoring | deliveryPASS; exporters PREPARED_TRANSACTION_NATIVE_BOUNDARIES/AssertionError; pipeline ACTUAL_BACKGROUND_TRANSPORT/CalledProcessError1. probe_returncode0 относится к datastore readiness, не native child. Нужны первый prepared child boundary/stage/category и background child compile/setup/assertion category. Source callgraph исключает новую canonical-display enrichment как причину prepared probe: он подменяет prepare/dispatch. Runtime cause UNKNOWN; source patch не выдуман. |
| Browser push lifecycle | Transfer POST202/PENDING, собственный GET PENDING к10s deadline, identities совпали; opposite PR case PASS. После step+cleanup snapshots COMPLETED/APPLIED/SENT, PER_DB_ONLY/DEADLINE_NOT_CONFIRMED. Это не state-at-deadline или доказательство стабилизации. |
| Browser PR worker | 1FAIL2PASS: update click завершён, reload не наблюдался, candidate installed/старый document; native postMessage вернулся, candidate не activated. Opposite push3PASS. Нужны worker-side receive/handler/skipWaiting settlement; отсутствие waitUntil само по себе не finding. |
| D002/история | На bac3 remaining scenarios push/PR10PASS, включая D002/N2. Исторические refresh rejection/finally timing, SETUPPUT500, activity-card,429 и другие квалифицированные причины UNKNOWN сохранены. Новые PASS не доказывают их причинного исправления. |

## Точный inventory / доставка

С617:18 code/SQL/test/workflow paths (17modified/1added V12),0deleted, плюс2taskdocs. Новый послеbac3 пакет7modified:

- services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/grpc/ScheduleGrpcServiceImplIT.java
- services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/integration/LessonPerformanceIT.java
- services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/testhelpers/AuthConfirmationHttpFixture.java
- services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/group/GroupRosterSnapshotIT.java
- services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/integration/PaginationCapIT.java
- services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/user/UserCredentialOwnershipIT.java
- services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/integration/ActuatorIT.java

Taskdocs: CURRENT и этот handoff. Root должен опубликовать только проверенный checkpoint обычным nonforce push после сверки expected remote HEAD, source equivalence, scanner и independent review. Текущие CI не отменять/не перезапускать. Внешние safe artifacts читаются с exact bindings; UNKNOWN не меняется на PASS из-за исчезновения имени/job.

F01–F09/N1/N2/security/ownership/uncertain keys/late generation guards/CAS/outbox/ACK/DLQ/privacy не ослаблены. B013/B014/B015 и rejected Mongo/Chromium equivalents FROZEN. Все WIP/worktrees/data/foreign resources сохранены; foreign cleanup0, собственные disposable fixtures штатно завершаются. Нет merge/main/deploy/VPS/SSH/provider/Telegram/production migrations/secrets/grants/firewall/backup операций.
