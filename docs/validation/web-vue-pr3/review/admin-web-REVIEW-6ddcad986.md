# ADMIN source review — frozen 6ddcad986

Verdict: **three confirmed P2 findings**. No confirmed P1 found in the reviewed scope.

Reviewed the frozen author tip `6ddcad986` for the complete ADMIN page packet: user/profile/access/credential workflows, groups and retained registry links, semesters, maps, dashboard/attendance and exports, including the named `dc89915be`, `8b93ef99a`, `e8f866dd8`, `ae9b77306`, `b21a9c0ec`, `df9610dda`, `112e6b019`, `ba4c781be`, `61ab2ca57`, `6ddcad986` corrections. Source was read with Git, not from a moving worktree. Existing accepted credential/group-filter/lookup backend packages were not reopened as full backend reviews.

## Findings

### P2 — ADMIN export loses its command identity after an unknown acceptance result

Location: `frontends/web-vue/src/features/admin/ExportControl.vue:29–30,68`; affected adapter behavior: `frontends/mobile-core/src/shared/report-download-client.ts:438–458`.

The control creates a ReportDownloadClient and calls `download` again after any surfaced failure. That client retains pending identity only for weekly reports with a recovery scope. All three ADMIN kinds are non-recoverable here, so each call allocates a new requestId. Neither the component nor its adapter retains the original ADMIN request/job when enqueue acceptance or later polling/download outcome is unknown.

Concrete reproduction: the server accepts an ADMIN_USERS export, but the acceptance response is lost; the control returns to its normal enabled Export button. Clicking it for the same selector allocates a different requestId and posts another job instead of recovering the first one. The existing durable job identity is unique by user/session/request_id, so it cannot deduplicate these new IDs. This also abandons a known job if a later read fails and the user uses the same button again.

Independent lightweight probe against the actual frozen ReportDownloadClient called the same ADMIN_USERS request twice with a deferred/lost-acceptance fetch outcome and recorded `exportPosts=2, sameRequestId=false`.

Retain the frozen selector and original requestId/jobId for an unresolved ADMIN intent; recovery must reuse that identity or read the known job. A new export identity should require an explicit new intent after a terminal outcome. Preserve the existing current-owner checks and selector binding.

### P2 — Successful create recovery discards the initial access receipt

Location: `frontends/web-vue/src/features/admin/UsersPage.vue:362–373`.

The normal create path copies `user.initialPassword` into the volatile `initialReceipt`. The resume path receives `{created: true, user}` from AdminUsersClient, selects the recovered user and acknowledges the identity operation, but does not create that receipt. The adapter explicitly preserves the credential in a resumed create response (`admin-users-client.ts:647–651`); the only initial-access display is bound to `initialReceipt`, not to the selected user's initialPassword.

Concrete reproduction: a LIMITED actor with USERS_VIEW and USERS_MANAGE creates an ordinary user, loses the 201 response, then confirms the existing operation through Resume. The backend replay supplies the initial login/password, but the UI shows only the selected account and “Результат операции подтверждён.” The actor lacks USER_CREDENTIALS_MANAGE, so the separate disclosure panel cannot recover this missing user-visible result.

A lightweight frozen UsersPage script probe supplied a resumed create response with a synthetic initial password. It produced `selected user=17, initialReceipt=null, acknowledged=1`.

Publish the resumed create's login/password through the same volatile receipt before acknowledging it. Keep the receipt out of storage, route state and logs, and avoid creating another account to recover access.

### P2 — Archiving immediately after group rename sends the stale name and can undo the rename

Location: `frontends/web-vue/src/features/admin/GroupsPage.vue:118–122`; downstream archive reads `selected.value.name` in `archiveOrRestore`.

The rename branch updates the backend and reloads the table, but retains the old selected group object. The next Archive action uses that object's old name. AdminGroupsClient.archiveGroup sends `{name: oldName, active:false}` to the existing update endpoint. GroupService.validateUpdate/updateGroupLocal treats the supplied name as the intended new canonical code and applies it before archiving; it is not a harmless precondition field.

Concrete reproduction: open active group УИТ-311, save its code as УИТ-312, then archive from the still-open card. The table now contains УИТ-312 while selected.name remains УИТ-311. Archive sends УИТ-311 and can archive under the old code; if that code has since become occupied, it fails with a name conflict instead.

A lightweight frozen GroupsPage script probe reproduced `selected=УИТ-311, refreshedRow=УИТ-312, archivePayloadName=УИТ-311`. Existing backend source confirms that supplied code is applied before the archive writer.

Refresh/rebind the selected group to the authoritative renamed result before subsequent actions, or close/reopen the card. Retain its stable id and current lifecycle fields as well as its name.

## Other reviewed boundaries

- useAdmin captures session identity and mounted lifetime, verifies the administrative profile's user id, and refreshes server-backed granular capability admission before guarded actions. Decoded page/credential/own-operation results use authority assertions; authority retirement hides protected detail controls.
- User access edits use server versions, and identity operation recovery retains only validated nonsecret references. Archive previews/password confirmation and restore-without-old-rights remain connected to the existing API. Credential generation preserves exact decimal versions, uses CAS and a volatile self-revocation receipt; unknown generation uses readback instead of automatic regeneration.
- User list/group/status/sort selectors and exports are derived from the applied filter, and retained group routing requires the applicable permissions. The transfer correction updates role draft group/status from the returned retained grant.
- Semester archive/restore retain accepted operation UUIDs separately from request keys; deletion uses the existing same-key operation contract. Pending operations use status reads rather than a fresh destructive command. Map asset reads remain authenticated and version-specific; unknown publication is blocked for explicit readback. Final map deletion is explicitly disabled with an honest explanation.
- Table regions expose keyboard scrolling. This was a source review, not another renderer/visual acceptance.

## Evidence and limits

Read the ten requested nonprivate runtime JSON files under `/workspace/rct-pr3-runtime/admin-web/`: users-actions, user-lifecycle, group-filter, map-actions, exports, dashboard-controls, semester-final-status, semester-operations-final-status, transfer-form and visuals. They contain useful real positive flows and preserve pending-operation explanations, including the fresh transferred user16 retained STUDENT group3/EXPELLED result. They do not cover the three interleavings/action sequences above. No private credentials, issued codes or private artifacts were accessed.

The recorded existing runtime gaps remain gaps: FULL user8/self-unknown Q-ENV003, exclusive global activation, owned eligible promotion fixture, complete map/group final deletion, and actual USER_RECOVERY/ADMIN_ACCESS UI acceptance are not promoted to runtime PASS by this report. The accepted disabled deletion state is not reported as a new implementation finding.

`git diff --check 072edf183..6ddcad986 -- frontends/web-vue/src/features/admin frontends/mobile-core/src/features/admin-users/admin-users-client.ts` passed. Three dependency-light frozen-module/script probes used existing TypeScript/Vue, dummy data and mocked adapters/fetch only. No browser mass/suite, build, Gradle, Docker, install, CI, server mutation, product source write, commit or child agent was used. Requested review model remains `gpt-6.1-sol/high`; runtime telemetry is unavailable and no fallback/telemetry claim is made.
