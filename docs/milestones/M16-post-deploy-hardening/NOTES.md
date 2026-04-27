# M16 Notes

Живой файл. Пиши сюда отклонения, измерения, surprises, вопросы.

---

## 2026-04-27

- Milestone стартовал по результату вопросов про `future-ideas.md`. Owner
  выбрал scope: 5 🔴 + 4 🟡, magic-link и 🟢 отложены.
- Headman rate-limit лимит **300 req/min** (не 120 как в `future-ideas.md`)
  — owner мотивировал: «староста иногда быстро прям проставляет отметки
  всем, нехорошо получится». G7 учитывает это.
- M15 — retrospective папка создана пост-фактум. См. `M15-first-vps-deploy/`.

### G1 — surprise: Java HTTP exporter ≠ Python gRPC exporter

При фиксе чуть не сломал notification-bot. Edit на docker-compose
прошёлся `replace_all` и переключил env var у бота тоже на 4318/v1/traces.

**Insight:** notification-bot использует
`opentelemetry.exporter.otlp.proto.grpc.trace_exporter.OTLPSpanExporter`
(см. `services/notification-bot/bot/observability.py:33`) — это **gRPC
exporter**, для него правильный port — **4317** без пути. Java-сервисы
(Spring Boot Micrometer) шлют HTTP/protobuf — для них **4318/v1/traces**.

**Откат:** notification-bot env вернулся на `http://tempo:4317`,
комментарий явно отмечает почему. Tempo слушает оба протокола (см.
`infra/tempo/tempo.yml`), так что mixed-mode работает корректно.

**Lesson learned:** при OTel-миграции в monorepo с разными языками
проверять каждый exporter по импорту (`.proto.grpc.` vs `.proto.http.`),
не только конфиг.

### G1 verify статус

Локально не проверял — нет dev VPS, локальный compose тяжёлый. Verify
шаги (логи без `Connection reset`, trace в Tempo) — на VPS после
redeploy всех Java-сервисов через CI.

### G1 итог

8 файлов изменено: 6 application.yml + docker-compose.yml +
docker-compose.prod.yml + scripts/m07-g3-launch-services.sh
(косметика). Готово к commit.

### G2 — surprise: idempotency уже сделана в M13 G8

`future-ideas.md` § «OTP hardening bundle / HIGH.2» предписывал
добавить idempotency через Redis SET NX в EventDispatcher. При
проверке кода обнаружено что `BotIdempotencyGuard` уже полностью
реализован в M13 G8 (commit `494821fe`):

- `services/notification-bot/bot/services/idempotency_guard.py` — `try_claim()`
  через `redis.set(key, "1", nx=True, ex=3600)`
- 7 unit-тестов в `tests/test_idempotency_guard.py`
- wired в `event_consumer.py:97-99` перед `dispatcher.dispatch(body)`

**`future-ideas.md` HIGH.2 устарел.** Записал в DECISIONS.md § D5.

### G2 — реальная остаточная проблема: silent loss в dispatcher

При чтении кода нашёл inconsistency:
- `event_consumer.py:101-105` комментировал: «handler exceptions
  пробрасываются → DLQ» (G24-fix-2)
- Но `event_dispatcher.py:dispatch()` имел `try/except Exception:` —
  swallow на уровне ниже

Эффект: handler bug → swallow → ack message → silent loss event'а,
DLQ-flow не активировался.

### G2 — обсуждение скоупа с owner

Owner напомнил про retention 14d (Loki) → **должны не плодить
бесконечные DLQ**. Обнаружено: `notification-bot.events.dlq`
declare'ился без `x-message-ttl` / `x-max-length`.

Выбран **B+ скоуп**:
1. Убрать swallow → propagate exception до consumer → DLQ
2. Добавить DLQ retention `7d + max-length 10000 + drop-head`
3. Перевернуть тесты (was: ack safety, now: pytest.raises)
4. Создать runbook `docs/operations/runbooks/dlq-triage.md`
5. Обновить вводящий в заблуждение комментарий в consumer'е
6. Verify alert `DLQBacklog` уже существует, добавлять не надо

### G2 итог

Изменено 4 файла + 1 новый:
- `services/notification-bot/bot/consumers/event_dispatcher.py` — убран swallow
- `services/notification-bot/bot/consumers/event_consumer.py` — DLQ retention args + комментарий
- `services/notification-bot/tests/test_event_dispatcher.py` — `pytest.raises` вместо «ack safety»
- `docs/operations/runbooks/dlq-triage.md` — **новый** runbook
- M16 PLAN/CHECKLIST/DECISIONS обновлены

Не тронуто (отложено):
- Java side (`notification-web.history.dlq` без TTL) — это отдельный
  пункт N6 в `future-ideas.md` § «Notification history bundle»
