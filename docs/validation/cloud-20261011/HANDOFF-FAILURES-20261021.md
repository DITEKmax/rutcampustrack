# PR2 — causal correction checkpoint, 2026-10-21

Status: SOURCE_VERIFIED_PENDING_CURRENT_GATES. Это checkpoint проверенных corrections, не утверждение готовности PR. Merge/deploy запрещены новым поручением владельца. Existing PR2/branch сохраняются; Deploy disabled_manually.

## Goal / scope

Установить причины actual отказов после `617e1e33`, исправить доказанные ошибки продукта/fixtures/обвязки без изменения принятого API/прав/данных. Audit263 IDs уже обработан; нового массового удаления/переписывания тестов нет. Root sole Git/docs/CI integrator; шесть непересекающихся author worktrees сохранены, дополнительный E2E writer не внёс необоснованных изменений. Все новые writers/reviewers запрошены gpt-6.1-sol/high/forknone; фактические runtime model/effort UNKNOWN.

## Context and source custody

Starting branch HEAD `617e1e33`, main/merge-base `3908fad9`. Executable corrections:

- `5cbe9b46`: Academic V12 и два fixture файла.
- `23fc4a35`: Schedule durable by-ID current pointer.
- `6dca44d8`: четыре bot product и два regression test файла.
- `a44636ba`: пять CI source/frame scalar bindings.

Последующий documentation commit меняет только CURRENT и этот handoff. Его точный SHA и PR readback сообщаются отдельно; source bytes не выдаются за выполненный runtime на docs HEAD.

## Primary evidence

Все23 original ZIP из переданного manifest сохранены побайтно,248260B; size/SHA256/CRC/безопасные entry paths проверены до чтения. GitHub metadata подтвердил23/23 artifact IDs/digests/sizes/run/head bindings; все три run attempt1.40JSON entries прочитаны со строгим отказом duplicate keys/nonfinite values. Подписанные download URLs не публикуются. Git tree PR carrier `ddd8a5a2` равен tree `617e1e33`; разные commit SHA не переименованы друг в друга. Все file hashes Monitoring совпали с starting source.

- Push CI [39514103147](https://github.com/DITEKmax/rutcampustrack/actions/runs/39514103147): FAILURE.
- PR CI [39514114181](https://github.com/DITEKmax/rutcampustrack/actions/runs/39514114181): FAILURE.
- Coverage [39514114251](https://github.com/DITEKmax/rutcampustrack/actions/runs/39514114251): FAILURE в шагах тестов; недостаточный coverage percentage не установлен.

Сырые private XML/HTML/full logs не читались. ORDINAL_ONLY не превращался в имя метода; безопасная категория исключения не превращалась в первую assertion. Parent aggregate browser FAIL — следствие client FAIL, не отдельная причина. Split-group SKIP не PASS.

## Actual failures and corrections

| Область | Actual617 факт | Подтверждённая correction / остаток |
| --- | --- | --- |
| Academic | Во всех трёх IT projections510PASS/30FAIL/1SKIP; unit584PASS | V3 CHECK пропускает LESSON+NULL через UNKNOWN; additive V12 явно требует номер, DATE+NULL сохранён. Homework fixture атомарно создаёт required subject/type. Jobs teacher fixture делает то же в одной транзакции; guard fixtures заполняют обязательные V8 API fields и моделируют FAILED_SAFE. First actual assertion/stage остаётся UNKNOWN; 24 HTTP/domain и2 report HTTP causes не угаданы. |
| Schedule | Во всех трёх IT211PASS/3FAIL; unit75PASS | getLessonById раньше сериализовал transient current=true для retained superseded rows. Теперь читает durable occurrence pointer в read-only REPEATABLE_READ. Existing recurring history/superseded/successor assertions сохранены. Actual stages group/performance/recurring UNKNOWN; source fix не объявлен устранением всех трёх native failures. |
| Attendance | IT255PASS/2FAIL/1SKIP, unit407PASS | Все11MarkingIT и8RabbitDecisionRetryIT PASS. Два ORDINAL_ONLY имеют closed codes GRADLE_SUITE/OOM и ASSERTION; OOM subtype/firststage UNKNOWN. Product/test patch0. Source-proven CI defect: comparison829 против уже admitted source assertion/ruleFrame778; исправлен только scalar. |
| Notification | Push64PASS; PR63PASS/1FAIL | Единственный PR SOURCE_METHOD — groupFailureRollsBackClaimAndCommittedReplayKeepsReadState, ASSERTION. WS cases PASS. First assertion UNKNOWN; fanout/snapshot race остаётся source hypothesis, speculative patch0. Нужен закрытый first-source-assertion/stage witness либо доступный addressed stand. |
| Monitoring | deliveryPASS; exporters PREPARED_TRANSACTION_NATIVE_BOUNDARIES/AssertionError; pipeline ACTUAL_BACKGROUND_TRANSPORT/CalledProcessError1; cleanupPASS | Prepared child execution versus receipt-admission stage не различены; root readiness probe_returncode0 не является child exit. Background Gradle child завершился1 до receipt/Tempo validation; compile/setup/assertion UNKNOWN. Исправлений по предположению нет. |
| Browser | Оба lifecycle jobs SUCCESS; first3 оба3PASS; push remaining10PASS, PR9PASS/1D002FAIL | D002 pre-online native refresh к snapshot REJECTED, post-online auth calls нет; rejection/finally timing UNKNOWN. N2 допускает явную current-only «Перезагрузить» и запрещает blind refresh mutation retry. D002 не ослаблялся. Push smoke не имеет exact safe case evidence в этих23ZIP; Homework GREEN save-click/PUT observation deadline отдельно UNKNOWN, PRGREEN PASS. |
| Python bot | Hosted test steps FAIL; отдельного testcase artifact нет | Семь source defects воспроизведены адресными локальными tests и исправлены. Hosted failed method mapping остаётся UNKNOWN; локальные PASS не заменяют hosted proof. |

## Bot accepted behavior

Текущий уполномоченный HEADMAN получает только canonical student/lesson/known-reason display facts, без comments/attachments/event-supplied PII. Queued authority/status revalidation сохраняется. EXCUSE использует ticket_id, LATE_CHECKIN — request_id; alternate field не перенаправляет N2. Producer resolution_reason проходит через private owner projection и отображается только известными enum labels. Group rename использует producer new_name с прежним fallback. Alert fields ограничены до4096 UTF-16 units после parsing, HTML escaping выполняется после truncation. Retry templates не содержат raw recipient IDs; счётчики/отсрочки/тип ошибки сохранены. Roles/common4/keyboard publication lock, ACK/DLQ/idempotency не менялись.

## Migration and data safety

V1–V11 побайтно сохранены. V12 не переписывает retained data и не меняет grants/владельца. Если в существующей БД есть invalid LESSON+NULL, validation откажется продолжать migration; исправление данных не выполнялось и не скрыто. Fresh migrations не seed/copy homework rows.17 direct SQL test insertion sites и persistent helpers проверены на совместимость; дополнительных invalid fixtures не найдено.39 finite expression witnesses подтверждают только логическую acceptance delta; PostgreSQL/Flyway runtime NOT_RUN.

## Verification and independent review

- Academic+Schedule compileTestJava: PASS30.79s, оба target tasks observed, frozen input hashes unchanged. Проверялся тот же source до scoped commits; ни DB, ни acceptance tests не выполнялись.
- Bot existing selected43: baseline28PASS/15FAIL, исправленные43PASS. Дополнительный пересекающийся набор107PASS/0FAIL/0SKIP; counts не суммируются. Семь добавленных cases защищают queued authority/status privacy и HTML/astral/uppercase protocol bounds. Ruff check/format и diff-check PASS; full suite/Telegram/broker NOT_RUN.
- Independent Academic, Schedule, Bot reviews: APPROVE_SOURCE; authors собственные patches не одобряли. Final independent integration review проверил11 code/SQL/test/CI paths и все author hashes, findings0.
- CI delta ровно5scalars: current Jobs hash2, moved teacher coordinates2, existing Attendance backfill frame1. Все29embedded Python blocks AST-parse; native counterexamples/frozen checks локально не исполнялись. Predicates, wire/status/count/identity/schema/privacy/current-source/restored-green/thresholds/required gates не ослаблены.
- Managed Docker daemon/socket проверен штатно: uid1000 получает socket permission denied. Daemon/groups/config не менялись; sudo/alternate sockets/network bypass не использовались. Реальные PostgreSQL/Rabbit/Mongo/browser checks сейчас NOT_RUN по этому resource/access blocker, не PASS.

## Exact changed inventory

11 source/SQL/test/tooling paths:10 modified,1 added,0 deleted. Modified:

- `.github/workflows/ci.yml`
- `services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/exportjobs/AcademicExportJobsIT.java`
- `services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/homework/HomeworkMigrationIT.java`
- `services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/grpc/ScheduleGrpcServiceImpl.java`
- `services/notification-bot/bot/notifications/alert_fired.py`
- `services/notification-bot/bot/notifications/audience.py`
- `services/notification-bot/bot/notifications/presentation.py`
- `services/notification-bot/bot/services/send_queue.py`
- `services/notification-bot/tests/test_alert_fired.py`
- `services/notification-bot/tests/test_notification_presentation.py`

Added: `services/academic-service/academic-app/src/main/resources/db/academic-migration/V12__homework_lesson_binding_requires_number.sql`. Taskdocs: CURRENT modified, this handoff added. Dependencies/lockfiles/proto/generated/API schema/scanner rules/branch protections unchanged.

## Remaining work and constraints

Новый stable published HEAD должен получить свои ordinary gates; нельзя переносить старый PASS или disappearance имени теста в утверждение fixed. Safe outcomes читать по точным run/attempt/head/digest. При remaining FAIL устанавливать первый подтверждённый механизм либо сохранять UNKNOWN с конкретным missing witness. Source approvals и завершение audit не равны readiness PR.

F01–F09/N1/N2 не ослаблены. B013/B014/B015 и rejected Mongo/Chromium equivalents FROZEN; raw diagnostic disclosure не расширялся. CI не отменялись/не перезапускались. Worktrees/WIP/data/foreign resources сохранены, cleanup0. Deploy257646584 disabled_manually; merge/main/deploy/VPS/SSH/provider/Telegram/production migrations/secrets/grants/firewall/backup операции не выполнялись и не разрешены этой задачей.
