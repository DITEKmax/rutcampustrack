# 2026-10-02 — durable notification preferences and deferred delivery

Owner decision in main chat: «Да: сохранять настройки, при сбое откладывать доставку».

Notification choices are persistent product data, not disposable Redis cache. Save them in the existing database infrastructure; cache eviction/restart must not re-enable a category or clear an active mute. When authoritative preferences cannot be read, defer delivery through existing retry/claim mechanisms; do not turn an unknown value into enabled or silently discard the event.

Scope includes canonical user settings and affected bot delivery/settings paths. Preserve explicit legacy choices where they can still be read, current default for a genuinely new user, known categories/mute semantics, account ownership and event idempotency. Missing legacy data already lost cannot be reconstructed or declared recovered. Implementation must identify the actual consumers and agree one authority/transition contract before changing shared transport.

No production migration, Redis flush, token reset, deployment or secret changes are authorized by this decision. Real provider delivery remains separately unconfirmed.
