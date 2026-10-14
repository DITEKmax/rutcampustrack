# Standalone administrator headman removal — 2026-10-14

Owner answered Q012: «Разрешить только замену на другого старосту (рекомендация по позднему макету)».

D016: the administrator may replace a group's current headman with another eligible headman through the accepted assignment/transfer operation. A standalone administrative request to remove the current headman flag without appointing a replacement must be rejected by the server; the client cannot be the sole enforcement. Replacement continues to revoke the previous headman/helper authority and preserve required membership/history/atomicity rules.

This resolves the specific Q012 ambiguity concerning an administrative isHeadman=false/no-replacement update. It is not a new permission to retain revoked security authority, change unrelated account archive/restore/group-transfer lifecycle rules, rewrite the 12-grant catalogue, or add a new web frontend. Review concrete adjacent paths against existing accepted rules rather than broadening this decision to all automatic authority cleanup.

Supersedes the old standalone-removal behavior and old tests only for this branch. Existing replacement, foreign-scope, live revocation, transaction/history and terminal-state boundaries remain required. API/proto/schema compatibility remains; use the existing typed conflict convention with no partial mutation. Canonical JS-ADMIN-07 plus this direct owner decision now resolves candidate historical wireframe contradictions.
