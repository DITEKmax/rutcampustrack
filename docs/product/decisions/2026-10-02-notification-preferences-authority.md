# 2026-10-02 — Notification preference authority and bot transport

Status: bounded implementation contract; critical dispatch protocol still requires independent review. Owner decision: durable settings, defer delivery when preferences are unavailable (2026-10-02-durable-notification-preferences.md).

Canonical category/mute settings belong to notification_db MongoDB under exact user ID. Redis is only a source for one-time legacy adoption when no durable record exists; successful lookup is required before distinguishing missing data from unavailable data. Existing durable settings are never overwritten by cache state. Atomic insert-if-absent and field updates avoid lost concurrent preferences.

Telegram-specific legacy global/category/mute settings remain a separate facet bound to exact userId+telegramId. The trusted bot must resolve and server must validate the live Academic binding for read, update and delivery. Old facets never migrate implicitly to another account/binding. Canonical user settings remain attached to the user. Already lost legacy data cannot be reconstructed.

Narrow transport exception: bot accesses a private Notification HTTP boundary with a dedicated canonical 32-byte BOT_TO_NOTIFICATION_SERVICE_TOKEN. This does not change public REST/BFF/gRPC composition, add a second public preference API, or authorize unauthenticated Gateway paths. Exact routes/DTOs are frozen by the writer for review; identity is checked in addition to service authentication. No raw secret values appear in docs/logs. Existing configuration examples/validator receive only this token's wiring; actual rotation/deploy is separate.

Before committing an event claim or flushing staged external tasks, resolve applicable authoritative preferences. Unknown/unavailable state must roll back/retry through existing delivery mechanisms. Known disabled/muted state suppresses delivery by the user's choice. A bot worker encountering an unavailable preference retains/retries its task instead of crashing or dropping it. Existing provider delivery and in-memory crash limitations are not represented as exactly-once durability.

Sole writer: e_homework_date_bot_1002 in headman-assistants worktree. NotificationJava preferences/store/delivery, affected bot handlers/client/queue and minimal token configuration are its scope; no shared proto/generated, Auth, Schedule or report changes. Production migration/Redis flush/deployment are not authorized.
