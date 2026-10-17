# D022 — Q015 current homework management includes nonauthor deletion

Received UTC 2026-10-15T10:24:21.207843+00:00 in main Cloud chat.

Owner answered: «Разрешить удаление любого ДЗ своей группы при MANAGE_HOMEWORK (рекомендую)».

A current headman or helper with effective MANAGE_HOMEWORK may delete any current homework of their own group, regardless of authorship. Keep original creator attribution; record the actual deletion actor, immutable history and audit according to the existing delete/archive operation. Every command, replay and client action requires current authority and own-group scope. Retain existing archived/terminal/read-only, semester, revision/concurrency, placement, idempotency and event/outbox rules. This does not permit foreign-group access, removal of historical audit, hard data purge or a new permission code.

Supersedes only the previously unresolved nonauthor DELETE branch of JS-HEADMAN-17 and any author-only check conflicting with this accepted rule. Existing edit permissions remain. Implement only confirmed missing behavior after Source diagnosis and verify owner/nonowner with live grants, foreign scope, revocation, history/events and appropriate real client/server evidence; do not infer implementation completeness from this decision. No production/new web/Angular change.
