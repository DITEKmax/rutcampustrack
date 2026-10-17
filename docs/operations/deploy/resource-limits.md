# Resource Limits (NEW-157, актуализация stage17)

Единственный источник значений — `docker-compose.prod.yml`. Таблица ниже соответствует подготовленной конфигурации 17.10.2026; она не подтверждает production deploy или измеренную пропускную способность. Конкретный VPS ещё не выбран.

## Общий бюджет

- Сумма жёстких container limits: **8448 MiB (8,25 GiB)** для 33 сервисов.
- Сумма `mem_reservation`: **4768 MiB**. Это мягкие reservations, а не гарантия физически выделенной RAM.
- Telemetry: девять компонентов 1664 MiB hard / 784 MiB reservation; четыре DB/cache exporters — ещё 320/80 MiB. Всего monitoring/exporters: **1984/864 MiB**.
- Планировать для этой совместной раскладки **от 12 GiB RAM**: лимиты контейнеров + ориентировочно 2 GiB для OS/Docker/файлового кэша оставляют около 1,75 GiB запаса. Старое утверждение о достаточности 4 GiB больше не применяется. Это бюджет памяти, не доказательство выдерживания нагрузки.
- Каждый exporter ограничен также 0,25 CPU. CPU и storage выбирать после проверки реальной смеси операций; число пользователей само по себе не переводится в число vCPU.

Ориентир владельца: 300–400 одновременно отмечающихся, около 200 других активных пользователей, уведомления и 3–7 семестровых отчётов. Приёмка должна подтвердить несколько очередных отчётов с ограниченным исполнением и продолжающейся обычной операцией. Изолированный функциональный PASS не является benchmark на 600 пользователей.

## Лимиты из Compose

| Сервис | Hard limit, MiB | Reservation, MiB |
| --- | ---: | ---: |
| postgres-academic | 192 | 128 |
| postgres-schedule | 256 | 192 |
| mongo-attendance | 512 | 320 |
| redis | 128 | 64 |
| rabbitmq | 384 | 256 |
| auth-service | 384 | 256 |
| academic-service | 512 | 384 |
| schedule-service | 512 | 384 |
| attendance-service | 768 | 384 |
| document-renderer-service | 768 | 512 |
| notification-web | 384 | 256 |
| notification-bot | 256 | 128 |
| mobile-bff | 384 | 256 |
| api-gateway | 384 | 256 |
| postgres-academic-exporter | 64 | 16 |
| postgres-schedule-exporter | 64 | 16 |
| mongodb-exporter | 128 | 32 |
| redis-exporter | 64 | 16 |
| node-exporter | 128 | 32 |
| cadvisor | 256 | 64 |
| blackbox-exporter | 64 | 16 |
| prometheus | 192 | 128 |
| alertmanager | 64 | 32 |
| tempo | 256 | 192 |
| grafana | 256 | 128 |
| loki | 192 | 128 |
| promtail | 256 | 64 |
| nginx | 256 | 32 |
| certbot | 128 | 32 |
| pwa-nginx | 64 | 16 |
| mini-app-nginx | 64 | 16 |
| web-panel-nginx | 64 | 16 |
| landing-nginx | 64 | 16 |
| **Всего** | **8448** | **4768** |

Ограничение Java heap не равно лимиту контейнера: metaspace, stacks, direct buffers и driver/native memory входят в тот же container budget. Attendance и renderer требуют отдельного наблюдения; native renderer memory не включается в Java heap gauge. `JAVA_TOOL_OPTIONS` брать из Compose конкретного сервиса. Рост лимитов без установленной причины не заменяет исправление отказа.

## Диск, retention и свежесть

Prometheus сохраняет до 35 дней при size cap 4 GB. Size cap может удалить данные раньше; 30-дневная SLO-панель тогда обязана показать недостаточную историю, а не здоровые 100%. Loki/Tempo retention — 14 дней. Positions Promtail и Loki WAL имеют отдельное persistent состояние.

Для telemetry предварительно выделить около **16 GiB**: 4 GiB Prometheus, ориентир 8 GiB logs/traces и 4 GiB WAL/headroom. Фактические ingest rate, compression и inode расход пока UNKNOWN: размер уточняется по измерению, alerts на диск/inodes/headroom обязательны. Этот запас не включает PostgreSQL/MongoDB/RabbitMQ, файлы отчётов или backups.

У всех 33 контейнеров JSON log rotation `10m × 3`: номинально около 990 MB суммарно при заполнении всех файлов; это не квота всего Docker filesystem. Images, writable layers, volumes, journal и transient files считаются отдельно. Rotation не заменяет retention Loki и не очищает чужие данные.

## Проверка при разрешённом развёртывании

1. Сверить доступную RAM/CPU/диск/inodes с суммой лимитов и запасом; проверить реальные container limits, а не только Compose reservations.
2. Измерить обычную операцию одновременно с 3–7 заданиями отчёта; проверить очередь, лимит активных leases/render slots, retry/recovery и отсутствие OOM/restart. Истечение HTTP ожидания не доказывает остановку удалённого servlet render.
3. Проверить container RSS/working set, JVM heap/GC/native headroom, renderer RSS, DB pools и Rabbit ready/unacked/DLQ. Перегруженная replica не должна скрываться усреднением со свободными.
4. После адресного recreate проверить Promtail positions, Loki/Tempo/Prometheus storage, provisioned Grafana UID и permissions. Изменения существующих volumes/roles выполняются только по отдельно разрешённому плану.
5. Настроить независимый off-host probe и проверить потерю всей машины; владелец передал это в deploy backlog (D034, DEFERRED_LOCAL). Same-host Blackbox не подтверждает этот сценарий. Реального будущего alert-бота подключить отдельно; бизнес notification-bot является действующей частью системы.

Ожидаемый отказ доступа/валидации, отсутствие трафика, сбой telemetry и отказ полезной операции показываются раздельно. Алёрт требует конкретного действия по причине; автоматический restart всех зависимых сервисов не является общим восстановлением.

## Связанные документы

- [Мониторинг](../monitoring/observability.md)
- [Deploy checklist](prod-deploy-checklist.md)
- [Актуальная карта stores/owners/auth](../../architecture/architecture.md#6-хранилища-данных)
- [Stage17 CURRENT и реальные receipts](../../validation/cloud-20261011/CURRENT.md)
