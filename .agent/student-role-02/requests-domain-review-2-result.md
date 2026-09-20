# Independent requests-domain review 2 — FAIL

07.09.2026. Reviewer requests_domain_recheck_2, Sol high. Root preserves findings below; no product acceptance.

Six original findings closed. Frozen 32/32 SHA stable before/after. Focused XML49/49; Mongo15/15 with durable outbox, rollback and barrier races. Gitleaks [].

MEDIUM: StudentRequestService.java:1614 executeWithRetry repeats transaction body on UnknownTransactionCommitResult (1646–1649). decideExcuse:1043 and decideLateCheckin:1088 reject terminal state at1055–1058/1099–1101. A committed decision whose commit ACK is lost therefore returns false409 on retry. Submit receipts protect creation; decisions lack this protection. Reproduce by injecting labelled UnknownTransactionCommitResult after successful decision commit for either kind. Persisted terminal state and event remain successful while caller gets conflict.

Reviewer correction: separately handle ambiguous commit by reading persisted result only for matching actor/outcome/comment, or use transactional decision receipt. Opposite outcome remains conflict. Scope service and focused decision retry tests; no transport/bot/proto. Verify both kinds with post-commit fault injection and exactly one terminal event, uncommitted transient retry, opposite-outcome conflict. Fresh independent recheck required.

Root independently opened affected methods and confirms the retry/terminal-state mismatch. Backend dependency findings and XFF HIGH remain separate open gates; public transport, bot/proto and full student role are not accepted.
