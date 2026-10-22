# D021 — Q019 available offline browser capability and owner Safari acceptance

Received UTC 2026-10-15T09:36:45.637550+00:00 in main Cloud chat.

Owner answered: «Chromium offline + WebKit online; Safari offline проверю локально (рекомендую)».

For current Cloud acceptance use genuine Chromium PWA offline/read-only/reconnect checks and genuine WebKit online checks. Transfer actual Safari offline acceptance to the owner local stand, status OWNER_MANUAL_PENDING, not PASS. Preserve the original production offline scenarios, original WebKit failures and exact bb minimal direct-cache-control failure as evidence of an unavailable Cloud emulation path; do not assert actual Safari fails or is unsupported. The isolated control proved only that Playwright WebKit offline reload rejected outside production PWA after successful controlled online cached-document loading.

The mandatory ordinary CI WebKit selection may now cover the retained online session/admission/revocation/recovery scenario instead of the two failing offline-emulation scenarios and their diagnostic control. Keep Chromium offline scenarios and all unrelated gates/assertions. Keep diagnostic/production test implementations unchanged and document their exact historical results; do not make a failing diagnostic look like a passed product check. No test skip or relaxed assertion may replace remaining mandatory client/server behavior. Current Cloud W1 acceptance consists of the available engine checks plus source-qualified known limitation and explicit owner handoff. Actual Safari offline remains a finite local manual check on the final reviewed revision.

Supersedes only the requirement that Cloud prove offline behavior through this unavailable WebKit emulation path. D002 behavior, Chromium actual offline and reconnect, current WebKit online, data/privacy/session rules and all other criteria remain unchanged. This is no production-readiness or deployment approval.
