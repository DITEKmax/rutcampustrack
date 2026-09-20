# Адресные решения и blockers

Дата: 06.09.2026. Вопросы не повышены до product decisions.

| ID | Статус | Evidence | Нужная дельта |
|---|---|---|---|
| CONTRACT-01 | BLOCKED | `backend-delta.yaml#contract_freeze` | Выбрать spec-first либо Java-first/exported OpenAPI, contract owner и revision. |
| SESSION-01 | NEEDS-DECISION | `AU-10`, `AC-09`; static `UserResponse` scalar role/status + headman flag | Согласовать projection roles/statuses/effective permissions/group/active semester; не маскировать BFF DTO. |
| GEO-01 | NEEDS-DECISION | `journal/to-owner.md:802-808` | Server fields requestId/requestStatus/retryAt or retryAfter для cooldown; это вопрос владельцу. |
| MARKING-01 | CONTRACT-PENDING | `journal/to-owner.md:879-886` | Atomic multi-diff and staged/global review are cancelled. Scope the accepted per-row revision/ETag, idempotency and stale-conflict semantics in the selected contract. |
| MAP-01 | CONTRACT-PENDING | `backend-conflicts.md:481-505` (R-30), `backend-delta.yaml` MAP-01 | Product policy is closed: one floor-plan subsystem with registry, upload/replace and server demand metric. Assets, IDs and loader shape remain contract gaps; they do not reopen the policy. |
| LEGACY-01 | DEFERRED | `legacy-retirement.yaml` | Не удалять React/Angular clients до verified replacement и consumer audit. |
| SCHEDULE-01 | FUTURE RETIRE | `JS-HEADMAN-09/10`, `LessonApi` static evidence | POST mass-cancel only after separate consumer and regression review. |
