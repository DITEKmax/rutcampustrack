# Decision — 2026-09-06

`late_checkin.decision.payload.decision_by` is the positive internal academic user id. The notification bot resolves it from the existing academic lookup, and attendance-service remains authoritative for group-scoped headman authorization through its existing gRPC client. No legacy Telegram-id handling is needed because the frozen product contract has no existing production data.
