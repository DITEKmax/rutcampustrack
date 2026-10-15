# D027 — Q024: Administrator group attendance report authorization

Received UTC 2026-10-15T13:10:09.499816+00:00 in main Cloud chat.

Owner exact answer: «FULL + LIMITED с DASHBOARD_VIEW (рекомендую)».

The new group-semester attendance report and its export are available to current FULL administrators and current LIMITED administrators with DASHBOARD_VIEW. Without the live grant or after its revocation, the server must deny report reads, export initiation, status/read and downloads under the existing current-capability conventions. UI visibility is not authorization. No new permission code is introduced.

The report contains group totals only, without a list or personal data of individual students. This does not imply USERS_VIEW/GROUPS_VIEW/credential grants or change their existing separate scope rules. Preserve immutable export audit facts and current recipient/ticket privacy. Together with D025/D026, this resolves the dependent report product questions; implementation and sufficient real client/server/data/export checks, regression sensitivity by risk and independent review remain required. No production/main/deploy/new web/Angular authority.
