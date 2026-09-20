# Root preliminary Homework UI observations

07.09.2026. WIP read, not independent review or accepted stable diff. Writer remains sole owner of homework-ui checkout.

1. use-homework.ts optionsFrom: typed empty HomeworkUseOptions {} is treated as offline input, then Boolean({}) becomes true. Expected a normal online default. Sent bounded correction/test request.
2. scopeIdentity watch clears inFlight and item errors but leaves retryCommands. retryCompletion reads old desired state then submitCompletion captures current identity; a stale retry can transfer an earlier user's intent to another user for a shared group homework ID. Require stored-command scope guard/clear and failedPUT→identity change→retry negative test. Review historical selectedRange when semester scope changes as well.

Root has not claimed runtime reproduction or severity-final review. Writer is implementing and must verify before stable handoff; fresh Sol review remains required.