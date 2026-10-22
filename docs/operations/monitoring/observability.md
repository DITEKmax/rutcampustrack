# Наблюдаемость RutCampusTrack

Это рабочая карта конфигурации ветки PR #2. Фактические проверки и исполняемый
SHA указаны в [CURRENT](../../validation/cloud-20261011/CURRENT.md); наличие
конфигурации не означает, что она уже развёрнута или принята в production.

## Компоненты и хранение

| Сигнал | Источник и путь | Хранение / граница доступа |
| --- | --- | --- |
| Метрики приложений | Gateway, BFF, Auth, Academic, Schedule, Attendance, Notification, renderer: `/actuator/prometheus`; Python-бот: частный `/metrics` | Prometheus, 35 дней и предел размера 4 GB; доступ через частную сеть и разрешённый proxy |
| Ресурсы и зависимости | node-exporter, cAdvisor, PostgreSQL academic/schedule, MongoDB, Redis, RabbitMQ | Отдельные exporter/monitoring identities с минимальными правами; не публичные database/admin API |
| Логи | JSON stdout Java/Python → Promtail → Loki | 14 дней; ограничение строк/потоков/ingestion; positions Promtail и WAL/storage сохраняются в выделенных volumes |
| Трассы | Framework observations / OTel SDK → Tempo OTLP HTTP `4318` или gRPC `4317` | 14 дней; bounded attributes, events, links и ingestion; query/storage API частные |
| Оповещения | Prometheus rules → Alertmanager → авторизованный receiver | Grouping/dedup/inhibition; повтор 3 часа, warning quiet hours 22:00–08:00 Europe/Moscow; critical не заглушается |
| Интерфейс оператора | Git-provisioned Grafana | Доступ через доверенный reverse proxy; разрешённые query endpoints не открывают произвольные admin mutations |

Размерный предел может удалить историю Prometheus раньше 35 дней. Неполные
30 дней показываются как недостаточная история, а не как 100% доступности.
Все лимиты контейнеров, диска и ротации собраны в
[resource-limits.md](../deploy/resource-limits.md). Это исходный бюджет для
подбора VPS; производительность под нагрузкой владельца им не доказана.

Один актуальный перечень targets — `infra/prometheus/prometheus.yml` вместе с
`rules/expected-targets.yml`. Важны и `up=0`, и исчезнувший ожидаемый target.
Отсутствующий exporter, метрика без producer и нулевой трафик — разные состояния.
Host RAM, cgroup limit и JVM heap/native renderer memory также различаются.

## Идентификаторы и переходы

| Поле | Назначение |
| --- | --- |
| `requestId` | Канонический UUID одного HTTP-обращения; Gateway нормализует вход и возвращает header, включая application-отказ |
| `traceId` / `spanId` | Стандартные W3C/OTel IDs: 32/16 hex-символов; связанная работа имеет собственные spans и корректные parent/link |
| `originTraceId` | Сохранённая диагностическая связь с исходной работой при отдельном фоновом выполнении |
| `eventId` / message ID | Стабильная идентичность события и dedup; повтор доставки не является новым бизнес-событием |
| job ID / idempotency request ID / lease / attempt | Принадлежность и повтор фонового отчёта; не заменяют HTTP request ID, trace context или проверку прав |

Java JSON содержит `service`, `environment`, `version`, `instance` и разрешённые
поля MDC. Python использует согласованные `traceId`/`spanId`/`requestId`;
старое `trace_id` остаётся legacy correlation и не становится W3C trace ID
только из-за имени. Не ищи UUID запроса в Tempo как trace ID.

Gateway → BFF → gRPC используют стандартные framework observations и
propagators. Настройки baggage включают только принятый `requestId`;
Authentication и весь MDC между процессами не копируются. Executor и consumer
восстанавливают прежний контекст после success/error/cancel. Optional event
`traceparent`/`request_id` совместимы со старыми envelope; повтор worker не
должен перезаписать связь или оставить контекст предыдущего сообщения.

В Grafana сохранены datasource UIDs: Prometheus `PBFA97CFB590B2093`, Loki `loki`,
Tempo `tempo`. В Explore:

```logql
{environment="production",service="api-gateway"} | json | requestId="<UUID>"
```

Из JSON-лога поле `traceId` открывает Tempo через Loki derived field. Из span
переход в Loki ограничивает service и окно времени, затем фильтрует JSON
`traceId`. Request/trace/job IDs не превращаются в Loki или metric labels.
Если нужного span нет, проверь sampling, export/drop/queue и freshness;
отсутствие trace не доказывает отсутствие полезной операции.

## Метрики и смысл результата

- HTTP eligible — наблюдавшиеся business 2xx/3xx/5xx. Good — 2xx/3xx, error —
  5xx. Ожидаемые отказы прав/валидации и cancellation не входят в этот
  знаменатель. Отдельные ранние ingress/TLS/parser отказы ищутся по ingress
  сигналам; HTML 200 сам по себе не доказывает работающий журнал.
- Исходная цель доступности — 99,9% за 30 дней. Latency p95: чтение/вход 2 s,
  обычная запись 3 s, семестровый DOCX 30 s. Это инженерные начальные цели,
  которые следует сверять с измерениями, а не обещание времени каждого запроса.
- p95 считается из сумм bucket histogram по подходящему operation scope.
  Широкий Gateway route вроде `/api/attendance/**` не классифицируется как
  обычная запись с целью 3 s. Backend report timer имеет отдельную цель.
- 30-дневная доступность использует ограниченные recording rollups конечных
  operation classes. Требуются успешные scrape, наблюдавшиеся counters и
  полная история; idle/missing/stale/query error не заменяются здоровым нулём.
  Короткие окна по route/replica остаются для расследования.
- `outbox_publish_attempts_total` означает попытки с конечным outcome.
  Broker confirmation не доказывает consumer effect.
  `outbox_published_total` учитывает SENT после commit; откатившаяся запись не
  считается опубликованной бизнес-операцией. Persistent dedup защищает effect.
- `attendance_report_duration_seconds_*` и `attendance_report_active` показывают
  выполнение синхронного совместимого report API, его конечный outcome и slots.
  Повторы временной зависимости не становятся отдельными готовыми отчётами.
- `rct_export_jobs_*` отражают durable async admission, retry, terminal outcome,
  due/waiting/running/stuck/expired и возраст. Snapshot timestamp проверяется
  отдельно; scrape не ходит в БД. Цель окончательного результата — 99,5% в
  пределах 60 s; accepted/queued не означает готовый файл.
- RabbitMQ ready/unacked/total, retry и DLQ проверяются раздельно. Возраст head
  вычисляется по AMQP publisher timestamp в секундах: это не время нахождения
  в DLQ. Старый head без timestamp остаётся unknown. Classic DLX сам по себе
  не доказывает zero loss.
- Счётчики process-local сбрасываются при restart. Durable состояние jobs и
  business data остаётся источником истины; crash после commit до increment
  нельзя выдавать за exactly-once долговременный счётчик.

Диск показывается по наиболее заполненному filesystem, heap — только по пулам
с положительным max, реплики — по отдельным instance. Process CPU не называется
host CPU, а свободная память не вычисляется формулой занятой памяти.
Окончательные запросы находятся в versioned dashboards/rules, а не в UI-save.

## Отчёт журнала за 18 недель и восстановление

В PWA/TMA журнал использует существующий async export port. Auth сохраняет job
в PostgreSQL до 202 Accepted; Gateway worker получает lease, вызывает реальный
HTTP report API Attendance и сохраняет результат. Сам DOCX renderer этого пути
не передаёт большой файл через RabbitMQ. Совместимый синхронный endpoint
`POST /api/attendance/reports/headman-weekly/export` сохраняется.

Клиент хранит только безопасный locator/intention той же сессии; возвращение
на экран, reconnect и пропущенный push читают серверное состояние. Неизвестный
исход create повторяется с тем же idempotency ID, известный job читается по ID.
Другой экран остаётся доступным. Logout/отзыв прав проверяет сервер; possession
job ID не разрешает status, ticket или файл.

Текущие границы: срок результата 10 минут, lease 90 секунд, до 3 attempts,
не более 2 действующих тяжёлых leases, bounded backlog/expiry cleanup. Retry
временной ошибки имеет задержку 5/10 секунд. Отмена локального ожидания не
отменяет серверную работу. Ограничение leases не означает автоматической
остановки удалённого physical renderer после сетевой cancellation.

События RabbitMQ имеют отдельный путь: подтверждённая публикация, устойчивый
consumer effect/dedup, ACK; временная ошибка допускает ограниченный delayed
redrive, неисправимая изолируется. См.
[dlq-triage.md](../runbooks/dlq-triage.md). Не удаляй очереди/volumes и не
перезапускай бесконечно poison message ради освобождения очереди.

## Расследование и безопасная эксплуатация

1. Найди request ID, временное окно, service/instance и соответствующий trace.
   Для фонового действия свяжи job/event ID с исходным запросом и попыткой.
2. Различи liveness, readiness конкретной функции и её полезный результат.
   Readiness dependency outage не должен превращаться в каскад liveness restart.
3. Проверь target/series/freshness, p95/error ratio и зависимость. Rate errors/s
   не является процентом ошибок; no traffic не является успешным synthetic probe.
4. При telemetry outage проверь собственные scrape/evaluation/ingest/drop/
   queue/storage/notification failure метрики. Недоступность мониторинга не
   должна блокировать commit пользовательских данных.
5. Пользуйся частными read endpoints и разрешёнными operator правами. Не
   открывай весь actuator/raw query/admin API наружу ради диагностики.

JSON message masking — дополнительная защита. Credentials запрещены также в
structured arguments, exception/stack details, MDC, baggage, spans, URL и
публикуемых CI artifacts. Export filter сохраняет разрешённые protocol outcomes
и URI templates, удаляя raw URL/query, чувствительные attributes и exception
text. Новый diagnostic field требует проверки источника, а не только новой
регулярной маски. Synthetic marker controls и safe CI summaries не разрешают
читать настоящие пароли/токены ради поиска утечек.

Панели/datasources/rules, volumes, retention и secret **references** хранятся
в Git. Secret values туда не входят. Адресный recreate/storage/positions check
выполняется только на выделенном synthetic scope; production deploy,
grants/secret rotation и удаление данных требуют отдельного разрешения.
Актуальная карта stores/owners/auth находится в
[architecture.md, §6](../../architecture/architecture.md#6-хранилища-данных).

## Локальная передача владельцу

- **DEFERRED_LOCAL:** конечный alert-бот, его URL/auth secret references и
  настоящая доставка; Cloud проверяет firing/resolved до тестового receiver.
  Это не отключает существующий бизнес notification-bot.
- **DEFERRED_LOCAL:** независимый off-host probe при deploy: настроить и
  проверить обнаружение потери всей машины. Same-host blackbox такого
  доказательства не даёт (решение Q030).
- VPS ещё не выбран. Нагрузка 300–400 check-ins, около 200 других активных
  пользователей и 3–7 отчётов — вход для sizing/приёмки, а не выполненный benchmark.

Alert descriptions содержат owner/impact/action/runbook. Проверки доставки,
roles/exporters, реальных запросов Grafana и восстановления отмечаются в
CURRENT отдельно от source/formula PASS; Cloud DONE не является production deploy.
