# Бот, память, разрешения и гео — рабочий план

## Goal
Локально реализовать A01–A04 из задания владельца09.11.2026, затем исправить воспроизведённые B-находки и явно отделить наблюдения/backlog. Показать настоящие Vue PWA/TMA с безопасными synthetic данными и точный список затронутых экранов. Текущий запрос разрешает реализацию и отменяет историческую пометку handoff «только подготовить». Push/deploy нового пакета не запрошены.

## Context/evidence
Base/main/исследованный production637ffc551642c2443ce54e3a9961c61f8d21471a. Integration branch codex/device-geo-reliability-1109, checkout C:/Users/maksd/.codex/worktrees/9689/rutcampustrack/.agent/worktrees/frontend-refactor-v2. Принятые интерфейсы сохранены. Outer/mainoriginal содержат чужой WIP, их не трогаем. RULES SHA2566CF90ECD0A6EEAAFDFA7BC40212E80FF7E2CABE4647AE8D3DA1D13695BA30DE2. Handoff: C:/Users/maksd/.codex/attachments/b6fef46b-269e-46aa-a967-64cea9b07fba/Вставленный текст.txt; первичные отчёты readonly C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/evidence/vps-audit-20261109/. Измерения аудита — исторический snapshot, не новый runtime PASS.

## Relevant scope / ownership
Root: contract/status/integration/browser acceptance, не пишет product code. Fresh implementation/review — gpt-6.1-sol/high/fork none, фактические metadata сообщаются честно. Независимые writers только в отдельных worktrees от base.

- bot-recovery-1109: A01, bot Python + узкий Auth recipient catalogue RPC/allowlist + его generated Python consumers + keyboard health alert. Владелец auth_authority.proto и только этих consumers.
- geo-backend-1109: A04 server/proto/BFF/audit/generated mobile contract + B02 conflict typing; владелец attendance.proto, HTTP/OpenAPI и generated mobile-bff.ts. Может проверить backend B05, не меняя неповторённые требования.
- device-access-1109: A03/A04 frontend всех трёх поверхностей; единственный владелец shared device permission/acquisition, profile routes, QR/browser/native adapters и UI. Не правит generated mobile-bff.ts. B03 только воспроизведение после основной реализации.
- infra-budget-1109: A02 память/effective compose и B01 gRPC queries/recording/fallback; не правит keyboard alert, который принадлежит bot writer. Root сериализует merge возможных соседних rule/test sections.
- B04/B06: после первичного scoped evidence отдельный назначенный writer, без массового рефакторинга.

## Required behavior / frozen minimum contracts

### Geo HTTP/proto/domain
Добавить optional nullable top-level diagnostics к StudentCheckinCommand/CheckinRequest, proto StudentCheckinCommand поле5 message GeoDiagnostics. Existing geo union и URL/API не меняются. Публичные camelCase поля:

- source: BROWSER | TMA (при наличии diagnostics обязательно).
- accuracyMeters: optional nullable finite number0..10000000.
- fixTimestamp: optional nullable ISOinstant; browser position.timestamp, TMA null, callback time не подменяет fix.
- receivedAt: optional nullable ISOinstant, время получения callback.
- sampleCount: optional integer0..64.
- acquisitionDurationMs: optional integer0..60000.
- completionReason: optional enum QUALITY_REACHED | BUDGET_EXPIRED | PERMISSION_DENIED | POSITION_UNAVAILABLE | TIMEOUT | UNSUPPORTED | APP_DISABLED.

Метаданные не дают прав/допуска. Старый command без diagnostics сохраняет прежнюю fingerprint byte semantics; новые diagnostics входят в deterministic fingerprint, одинаковый key с изменением diagnostics конфликтует. Old receipts читаются и replay идут до текущей eligibility. Geo audit сохраняет reason/quality/nulls, domain rejection без фактического расчёта не получает выдуманный verdict. Generated только официальным generator, не вручную.

### Device permission/acquisition
Общий adapter/state owner в mobile-core; платформенные реализации PWA/browser и Telegram в своих adapters. App preference отделён от system state. Preference хранится по device+account, default enabled; system grant не хранится как факт. Permission states prompt/granted/denied/unsupported/unknown/checking/error; query без prompt, явный request/probe по нажатию. Probe не вызывает checkin/approval. Отключение/owner reset/route leave отменяет watch/native callbacks/camera tracks/timers. Camera video-only, native QR по поддержке один scanner; QR всё ещё проходит прежний approval/confirm pipeline. Native camera state не притворяется известным universal grant.

Acquisition: общая ограниченная политика28s budget, свежесть browser fix30s, provisional early good accuracy<=50m; если несколько fixes противоречат друг другу, не усреднять и не выбирать по campus proximity. Выбор по качеству/freshness; при low/unknown quality по budget отдельный понятный результат и явный выбор повторить/продолжить существующий business path до первой отправки. Константы документируются как provisional до device acceptance. Unsupported/permission denied не тянут весь GPS budget. Optional AbortSignal/progress в существующем acquire callback допускается; единственный shared flow, не второй transport. После ambiguous POST повторяются тот же key и immutable command; новый fix не заменяет pending command. Перед отправкой после длительного acquisition обновить eligibility/server clock. Settings probe не отправляет координаты/отметки/заявки.

### Bot
Перепроверка роли под существующим per-chat publication lock, bounded background recovery и durable pending/receipt/retry state. Успех только после authoritative scope + TelegramACK + persisted receipt. Narrow paginated Auth bindings RPC с exact bot-service allowlist, без полного Academic catalog. Binding не доказывает writable chat; blocked/not-found terminal отдельно, uncertain/fenced authority retryable, новая привязка или /start реактивирует. Неизменённый content+authority receipt предотвращает лишнюю рассылку, version build не вызывает одинаковые сообщения. Текущие health/backlog/age gauges снимают warning после recovery; historical counters сохраняются.

## Constraints
Радиус200m/центр55.788328,37.606637, Haversine, inclusive окно[-5min,+5min], cooldown5min, права/изоляция/history/transaction/receipt fences сохраняются. Не ослаблять реальные тесты. Нет production reads/writes, Telegram user messages, secret/env reads, новых accounts/listeners/observer stack, data deletion, массового replay логов. Визуальные изменения только явные permission rows/page и текущие geo status/cancel/error; использовать существующий дизайн, не переделывать другие экраны.

## Existing patterns
Shared mobile-core/profile/StudentFeatureOwner/checkin recovery; platform adapters. Existing bot registry/signature/publication lock, Auth directed RPC authz. Existing lesson audit/redaction/bounded observability. Effective prod compose +operator overlay; heap не растёт автоматически при memory limit change. Existing Grafana/Prometheus provisioning/promtool; no mean of quantiles.

## Acceptance criteria / tracked checklist
Статус каждого пункта содержит revision/evidence/остаток; source-ready не означает device/production PASS.

| ID | Статус | Критерий | Revision/evidence/остаток |
|---|---|---|---|
| A01 | IN PROGRESS | outage>3attempts recovery, durable receipt, coverage, 429/terminal/role races, alert resolved | scout source-confirmed; implementation pending |
| A02 | IN PROGRESS | обоснованный heap/native budget, measured workload/export headroom, persisted config | audit historical768MiB/90%; local measurement pending |
| A03 | IN PROGRESS | device settings PWA/TMA, truthful states/probe/off/recovery, safe QR lifecycle | real-device prompt acceptance separate |
| A04 | IN PROGRESS | bounded quality acquisition, one POST, immutable uncertain retry, metadata roundtrip/audit | frozen contract above; native device acceptance separate |
| B01 | IN PROGRESS | gRPC1h/24h under500k samples, correct quantiles/filter/no-data/warmup | root/scout source assessment |
| B02 | IN PROGRESS | ConflictException maps domain409, no partial writes/history damage | existing archived fenceIT; mapper gap source-confirmed |
| B03 | TODO | role-change concurrent reads reproduction, fix only proven defect | generation/stale guards already exist |
| B04 | IN PROGRESS | provider accepted/expired/skipped/retryable/uncertain metrics | scoped dispatcher inspection pending |
| B05 | TODO | old pending remains available after lesson/day; counts/permissions | source list has no end/date exclusion; reproduce |
| B06 | TODO | log replay/startup/auth readiness/polling verified or explicit observation | no invented incident fixes |
| C01 | BACKLOG | observer stack not deployed; geofence cache age/version/invalidation | no installation/keys/listeners in this package |

## Verification / resources
Использовать имеющиеся адресные tests. New tests только significant authz/race/idempotency/recovery. Сначала affected Java compileJava/compileTestJava затем адресные IT, один heavy lease; bot/Python/node lightweight отдельно. WSL/local synthetic services разрешены; runtime ports/DB names назначает root перед запуском. Не копировать production данные. Root final actual Vue preview localhost14272/14273 (при занятости сохранить existingstands и назначить14372/14373), screenshots обоих themes/flows. Device-native permission prompt/GPS hardware не доказываются mocks; подготовить точные ручные шаги. Независимое fresh Sol high review для S3 package, corrections/recheck scoped.

## Do not
Не менять общий дизайн ради тестов, не расширять радиус/время/cooldown, не менять payload unresolved mutation, не выдавать fallback/COMPLETED за delivery, не делать source-ready=production ready. Не push/deploy этого пакета до отдельного решения владельца после локальной визуальной приёмки.
