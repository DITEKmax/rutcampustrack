# Б — selected role lifecycle, S3

## Goal
ADMIN revokes/suspends or archives a user's role; already-open sessions immediately lose that selected authority. Reactivation must not silently select it again.

## Context/evidence
RULES canonical root SHA256 01DAD59DF0F7CFADE73D40BB8F8660F5E1D41017BF5BB34150C79BB460676185. Explicit root packet 2026-10-01, root confirms explicit spawn model=gpt-6.1-sol, effort=high, role=developer with same fixed settings; spawn tool echoed only task name, resolved metadata unavailable (not inferred as verified). Root confirmed this two-file correction and exclusive heavy lease. JS-ADMIN-02/04 refined by docs/design/COMPONENT_REGISTRY.md: status per role, other active roles retain access. Academic grants bump users.roles_version via V24 trigger. Auth JdbcSessionAuthority clears unselectable active pointer only lazily in snapshot/refresh, so suspend→restore with no Auth call preserves pointer.

## Relevant scope
Sole writer account_lifecycle_sol61; assigned WT map-usage-delivery-20260922, branch codex/lesson-transfer-ui-20260929. Original HEAD 2e9380c0; authorized normal main a6fd83e8 merge baseline 4ec61a55190b28fa866b3342493827782036a2aa. Exact product file services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/repository/UserRoleGrantWriter.java; existing test services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/integration/HistoricalMembershipIT.java. Evidence in this directory only. Root owns main/runtime integration.

## Required behavior
Same user-locked transaction changes grants and clears selected active role/session_version for non-active grants. Active unrelated selections are preserved. Restoring status keeps cleared pointers; explicit role selection remains existing Auth flow. Archive applies to all retained grants. No delete of grant/user/history.

## Constraints
No children/Terra/fallback; preserve foreign headman-trend-export-20260926 contract/result bytes (SHA256 B883F73A83EA69C10776DB5E5CB9B088C97DCBE1E85E42644702F765920BE656 and 27883582E547397C599CA3B38DF5A279116241C99B801252D08B62E8346D46E0). Heavy Gradle/Docker needs root lease. WARN/ERROR changes code only when reproduced and tied to requested behavior.

## Existing patterns
JdbcSessionAuthority CLEAR_ACTIVE_ROLE_SQL increments session_version; authoritative user→session lock order. UserRoleGrantWriter MANDATORY surrounding Academic transaction; durable ID-based grants. Existing HistoricalMembershipIT managed-user fixture/PostgreSQL and cleanup.

## Acceptance criteria
Before any Auth read, suspend clears selected STUDENT and derived HEADMAN sessions, preserves TEACHER; immediate restore does not resurrect selection. Repeated revoke does not increment an already-cleared session. Legacy status/derived headman/archive mutation seams enforce same rule. Outer transaction rollback restores grant and session together. Existing membership/history behavior passes.

## Verification
Existing Academic compileJava/compileTestJava then focused HistoricalMembershipIT in one Gradle invocation after lease; pre-fix regression proves gap, post-fix affected class proves guard/history. git diff --check; precise diff/inventory; root independent auth review and runtime admission/refresh proof pending.

## Do not
No product redesign/status semantics, no Auth API/recovery rewrite, shared contracts/proto/generated/config/lockfiles/global docs, requests/attachments/reports/PWA/offline/SW/semester deletion. No reset/stash/clean, push/deploy or main write.
