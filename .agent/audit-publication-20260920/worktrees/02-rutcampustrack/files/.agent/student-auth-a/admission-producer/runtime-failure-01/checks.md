# Runtime failure evidence checks

| Check | Evidence | Exit/status |
|---|---|---:|
| Source guard before batch | 17/17 expected source entries present; Java processes 0; reserved ports free; Docker query empty | PASS |
| Shared focused command | `:services:shared:shared-security:test` selectors; BUILD SUCCESSFUL in 59s | 0 |
| Auth focused command | four selectors; BUILD FAILED in 1m16s after 25 tests / 4 deterministic fixture failures | 1 |
| PostgreSQL admission IT | `:services:auth-service:auth-app:integrationTest --tests ...InternalSessionAdmissionIT` | NOT RUN |
| XML copy guard | six source XMLs compared with six evidence copies by SHA256 and byte length | 0 |
| Source guard after batch | 17/17; Java processes 0; reserved ports free | PASS |
| Docker cleanup | initial default query was access-denied; escalated read-only query exit 0 and empty, zero containers; no Testcontainers command ran | PASS |

No product or test source was changed in this evidence-only follow-up.
