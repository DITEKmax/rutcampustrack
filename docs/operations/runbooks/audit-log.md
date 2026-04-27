# Audit log runbook

Что писать в `audit_log` таблицу, как читать при инциденте, какие
ограничения текущей реализации (M16 G6 v1).

## Что в audit_log

Каждая запись — одно ADMIN-действие, проксируемое
`AdminActionAspect` (shared-web). Поля:

| Колонка | Содержит | Пример |
|---|---|---|
| `id` | BIGSERIAL PK | `42` |
| `user_id` | Кто выполнил (`RequestContext.userId`). NULL если контекст недоступен | `1001` |
| `action` | Имя из `@AdminAction("...")` | `user.archive` |
| `target_type` | Тип ресурса. **На v1 всегда NULL** (см. ниже) | NULL |
| `target_id` | ID ресурса. **На v1 всегда NULL** | NULL |
| `correlation_id` | MDC `traceId` для связи с distributed trace в Tempo | `8b3a...` |
| `extras` | JSONB для будущих расширений | `{}` или NULL |
| `succeeded` | `true` если метод вернулся без exception, `false` если бросил | `true` |
| `error_message` | Truncated `Throwable.toString()` (до 500 символов). NULL при success | NULL |
| `created_at` | TIMESTAMPTZ когда aspect зарегистрировал entry | `2026-04-27T...` |

## Размеченные actions (M16 G6 baseline)

5 endpoints в `UserController`:
- `user.create` — POST /admin/users
- `user.update` — PUT /admin/users/{id}
- `user.patch` — PATCH /admin/users/{id}
- `user.archive` — DELETE /admin/users/{id}
- `user.transfer` — PATCH /admin/users/{id}/transfer

**Не размечены ещё (отложено в M16 follow-up или future-ideas):**
- `GroupController` (create/archive/restore)
- `SemesterController` (create/archive)
- `SubjectController` (create/archive)
- `ThresholdController` (update.global, update.group)
- `AssignmentController` (create/delete)

Расширение разметки — следующий incremental PR. Aspect автоматически
подхватит новые `@AdminAction`-помеченные методы без code changes.

## Что НЕ в audit_log на v1

1. **before/after diff** — не пишем. Это требует deep cloning entity
   до изменения + JSON diff библиотеки. Отложено в `future-ideas.md`.
2. **target_type/target_id** — aspect не знает о доменной семантике
   method args. Future: добавить `@AuditTarget` param annotation
   которая extract'ит `(targetType, targetId)` из `@PathVariable`.
3. **request payload** — может содержать ПДн (passwords, ФИО), а
   audit_log без shared-logback masking. Не пишем для безопасности.

## Чтение

### Кто что делал за последние 24h

```sql
SELECT created_at, user_id, action, succeeded
FROM audit_log
WHERE created_at > NOW() - INTERVAL '24 hours'
ORDER BY created_at DESC
LIMIT 100;
```

### Все действия конкретного user'а

```sql
SELECT created_at, action, succeeded, error_message
FROM audit_log
WHERE user_id = 1001
ORDER BY created_at DESC;
```

Использует `idx_audit_log_user_created` — fast even на big table.

### Сколько раз был action за период

```sql
SELECT action, COUNT(*) AS n, COUNT(*) FILTER (WHERE NOT succeeded) AS failed
FROM audit_log
WHERE created_at > NOW() - INTERVAL '7 days'
GROUP BY action
ORDER BY n DESC;
```

Использует `idx_audit_log_action_created`.

### Связка с distributed trace

```sql
SELECT * FROM audit_log WHERE correlation_id = '8b3a...';
```

Используй correlation_id из Loki/Grafana log entry → найди связанный
audit row → детали request'а в Tempo по trace_id (correlation_id ==
trace_id).

## При инциденте (insider threat / forensics)

1. **Что произошло:** найти user_id виновника (через web access logs / VPN logs).
2. **Что сделал:** SELECT по user_id за время инцидента.
3. **С чем связано:** correlation_id → Tempo trace → видны downstream
   gRPC calls (academic → schedule например).
4. **Что НЕ заметили:** SELECT по correlation_id для всех `succeeded=false`
   — возможно были failed attempts перед successful.

## Retention

**v1: без cleanup.** Таблица растёт неограниченно. На 10 ADMIN-actions/день
× 365 days × 3 года = ~11K rows — abnormal pressure не создаёт.

При появлении disk pressure (или регуляторного ограничения) добавить
cleanup job:

```sql
DELETE FROM audit_log WHERE created_at < NOW() - INTERVAL '2 years';
```

ScheduledTask раз в неделю в academic-app (см. M02 ShedLock pattern).

## Graceful degradation

Aspect — best-effort:

| Сценарий | Поведение |
|---|---|
| `AuditLogStorage` bean не зарегистрирован | warn в logs, ADMIN endpoint работает |
| `JdbcAuditLogStorage` бросает SQLException | warn в logs, ADMIN endpoint работает |
| `RequestContext` не доступен (out of request scope) | userId = NULL, остальное пишется |
| MDC `traceId` отсутствует | correlation_id = NULL |

То есть **audit log никогда не должен ломать ADMIN endpoint**. Это
осознанный trade-off: лучше неполный audit trail чем недоступный
admin UI при поломке audit storage.

Если **audit storage недоступен** длительное время — escalation:
```bash
docker logs rct-academic-service --since 1h | grep -c "audit storage failed"
# > 100/hour = real problem, эскалация
```

## Связанные документы

- `services/shared/shared-web/src/main/java/.../audit/` — SPI и aspect
- `services/academic-service/academic-app/src/main/java/.../audit/` — JDBC impl
- V19/V20 миграции — таблица + индексы
- `docs/architecture/architecture.md` — общая архитектура
- DECISIONS.md M16 § D3 (SPI pattern) + § D10 (audit v1 scope)
