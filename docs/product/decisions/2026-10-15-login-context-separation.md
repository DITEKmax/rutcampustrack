# D020 — Q016 separate login and current context

Received UTC 2026-10-15T09:31:50.668456+00:00 in main Cloud chat.

Owner answered: «Сохранить вход и отдельную загрузку контекста (рекомендую)».

For current acceptance retain the compatible existing login response with tokens, followed by separate authoritative session-context reads for profile, roles and active semester. The client must remain in a loading/non-operational state until required context is loaded; failure or revocation must not expose a protected screen or enable operations. Every server action still checks current authority; client context is not a grant. One combined response is not required by this acceptance, and no new bootstrap endpoint or Auth DTO expansion is authorized by the old story alone. Verify successful bootstrap plus partial failure/revocation behavior through existing real PWA/TMA/server paths.

Supersedes only the unresolved combined-response assembly clause in historical JS-SYSTEM-14 for current acceptance. Token/session, grants, password, privacy, compatibility and other mandatory criteria remain unchanged. No new web frontend or production operations.
