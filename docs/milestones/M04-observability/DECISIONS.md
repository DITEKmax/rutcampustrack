# M04 Decisions

Micro-ADR per решение. Формат: `## YYYY-MM-DD — заголовок`.
Открытые развилки: `## ОТКРЫТО — ...`.

---

## ОТКРЫТО — D1: shared-observability модуль vs дублирование по сервисам

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

## ОТКРЫТО — D2: Alertmanager receiver — новый endpoint в notification-service vs новый endpoint в notification-bot

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

## ОТКРЫТО — D3: Тихий час — фиксированный vs configurable per-alert

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
