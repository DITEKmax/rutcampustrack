# M04 Decisions

Micro-ADR per решение. Формат: `## YYYY-MM-DD — заголовок`.
Открытые развилки: `## ОТКРЫТО — ...`.

---

## 2026-04-20 — D1: shared-observability модуль (a)

**Решение владельца:** **(a)** Новый Gradle-модуль `services/shared/shared-observability/`.

**Обоснование:** прецедент в M01 (4 shared-модуля), цена +1 модуля
несоизмеримо меньше цены drift'а в 6 сервисах. testFixtures даст
`MetricsTestSupport` бесплатно для всех тестов.

**Применение:** Группа 1 CHECKLIST — создать модуль, подключить во все
6 backend-сервисов через `implementation(libs.shared.observability)`.

---

## 2026-04-20 — D2: Alertmanager receiver — POST /internal/alert в notification-service (a)

**Решение владельца:** **(a)** Java endpoint в `notification-service`,
forward в Telegram через RabbitMQ event → notification-bot consumer.

**Обоснование:** единственный вариант, который не теряет алерты при
инцидентах (RabbitMQ buffer/dedup при падении bot'а). Реиспользует
internal-JWT auth из M03a. Сохраняет паттерн bot=consumer-only.
Audit trail в Loki бесплатно. Цена лишнего hop'а ~80мс несущественна
для алертов с порогом ≥30с.

**Применение:** Группа 9 CHECKLIST — `POST /internal/alert` контроллер
в `notification-service`, новая RabbitMQ event schema `alert.fired`,
consumer в notification-bot форматирует и шлёт в Telegram.

---

## 2026-04-20 — D3: Тихий час — фиксированный 22:00-08:00 MSK (a)

**Решение владельца:** **(a)** Фиксированный интервал 22:00-08:00 MSK
для всех «низких» алертов через `mute_time_intervals` в alertmanager.yml.

**Обоснование:** для ~10 алертов M04 гибкость per-alert не оправдана.
Триггер пересмотра в `docs/future-ideas.md` — если pet-проект вырастет
в production с дежурствами.

**Применение:** Группа 9 CHECKLIST — секция `mute_time_intervals` в
alertmanager.yml, label-based routing (severity=critical обходит mute,
severity=warning/info попадает под mute).

---

## 2026-04-20 — D4: GrpcClientHealthIndicator per-channel — отложен в backlog (b)

**Контекст (Группа 4):** для каждого gRPC-клиента в каждом сервисе
нужен `GrpcClientHealthIndicator` (QA6). Но `net.devh:grpc-client-spring-boot-starter`
3.1.x уже регистрирует свои indicators при `management.health.grpc-client.enabled=true`
(это default), и Spring Boot автоматически подключает `db`/`rabbit`/`redis`/`mongo`
indicators когда соответствующие starters на classpath.

**Варианты:**

- (a) Подключить shared `GrpcClientHealthIndicator` per-channel через
  custom configurations × 5 сервисов (academic, schedule, attendance,
  notification + gateway если он использует gRPC). +20-30 строк
  per service, дублирование с indicators от grpc-client-spring-boot-starter.
- (b) Положиться на встроенные indicators (Spring Boot + grpc-client-spring-boot-starter).
  shared `GrpcClientHealthIndicator` остаётся в shared-observability как
  опциональная утилита для случаев где нужен fine-grained probe (например
  cross-channel ping без trip-on-call).

**Решение:** **(b)**. Дублирование без выгоды — `show-details: always`
покажет все встроенные indicators в /actuator/health. Использовать shared
indicator только если кому-то нужно перекрыть/расширить дефолт. KI-4
(`PublicKeyHealthIndicator`) — отдельная история, реализован сейчас.

**Применение:** Группа 4 закрыта без custom GrpcClientHealthIndicator
beans. Если в Группе 11 audit найдёт missing health-coverage — добавим
точечно.

---

## (исторический раздел — изначально открытые развилки, для справки)

## ЗАКРЫТО — D1: shared-observability модуль vs дублирование по сервисам

**Контекст:** QA2/QA4/QA6/QA7 требуют идентичной обвязки в 6 Spring-сервисах
(tracing config, MDC keys, BusinessMetrics helper, GrpcClientHealthIndicator,
PublicKeyHealthIndicator). Без shared-модуля — drift между сервисами через
1-2 milestone'а.

**Варианты:**

| | Плюсы | Минусы |
|--|-------|--------|
| **(a) Новый `shared-observability` модуль** | Единый источник правды; auto-config через `@ConditionalOnClass`; testFixtures для всех; защита от drift | +1 модуль в monorepo; auto-config надо отлаживать |
| **(b) Дублировать в каждом сервисе** | Простота; нет нового модуля | Drift гарантирован; CI-check на дубликат classes лишний; нарушает DRY с shared-web/shared-events/shared-logback |
| **(c) Расширить `shared-web`** | Без нового модуля | shared-web разрастается; теряется тематическая изоляция (web vs observability) |

**Рекомендация:** **(a)**. Уже есть прецедент в M01 (4 shared-модуля). Цена
+1 модуля несоизмеримо меньше цены drift'а в 6 сервисах. testFixtures даст
`MetricsTestSupport` бесплатно для всех тестов.

**Нужно решение перед стартом Группы 1.**

---

## ЗАКРЫТО — D2: Alertmanager receiver — новый endpoint в notification-service vs новый endpoint в notification-bot

**Контекст:** QA4/NEW-62 — Alertmanager webhook → forward в Telegram админу.
Где разместить receiver?

**Варианты:**

| | Плюсы | Минусы |
|--|-------|--------|
| **(a) `POST /internal/alert` в `notification-service` (Java)** | Stateless event-forwarder уже там (M03b комментарий «становится stateful после M04 — NEW-168»); единый паттерн с RabbitMQ events; auth через internal-secret (готово в M03a) | Лишний hop: AM → notification-service → RabbitMQ → notification-bot → Telegram |
| **(b) Прямой endpoint в `notification-bot` (Python FastAPI)** | Меньше hops; bot уже Python — close to Telegram API | Bot становится HTTP-сервером (overhead Aiogram + FastAPI); auth-pattern придумывать заново; нарушает текущую архитектуру (bot — consumer, не producer) |
| **(c) Прямой webhook → Telegram Bot API (без bot контейнера)** | Zero hops | Теряем форматирование/dedup/inhibit логику; нет audit-trail; admin chat_id хардкодится в alertmanager.yml |

**Рекомендация:** **(a)**. Сохраняет архитектурный паттерн (single-direction
events → bot), переиспользует internal-secret из M03a. Лишний hop — это
~20мс, не критично для алертов с порогом минуты.

**Нужно решение перед стартом Группы 9.**

---

## ЗАКРЫТО — D3: Тихий час — фиксированный vs configurable per-alert

**Контекст:** QA4/NEW-64 — не будить админа ночью некритичными алертами.
TZ — Москва (UTC+3, по большинству пользователей).

**Варианты:**

| | Плюсы | Минусы |
|--|-------|--------|
| **(a) Фиксированный 22:00-08:00 MSK для всех «низких» алертов** | Простота; одна `mute_time_intervals` секция; predictable для админа | Нет flexibility если появится «срочный, но не critical» алерт |
| **(b) Configurable per-alert через label `severity` (`critical` всегда, `warning` muted ночью, `info` muted всегда после 18:00)** | Гибкость; явная семантика | Больше строк config; админу надо понимать labels; можно ошибиться при добавлении нового alert'а |
| **(c) Только `critical` шлёт ночью, всё остальное — по утрам в digest** | Минимум шума | Требует новой digest-логики (сложнее реализация) |

**Рекомендация:** **(a)** для v0.0.0-alpha.5 + триггер пересмотра в
`docs/future-ideas.md` если pet-проект вырастет в production. Для текущих
~10 alerts (M04 scope) дополнительная гибкость не оправдана.

**Нужно решение перед стартом Группы 9.**
