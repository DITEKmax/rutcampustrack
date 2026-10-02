# Alertmanager quiet-hours fix — S1

## Goal
Warning notifications stay muted continuously from22:00 inclusive until08:00 exclusive MSK; critical bypasses quiet hours.

## Context/evidence
Root identified UTC23:59–24:00 gap in current split range. RULES SHA A208AA4380B64376A4EAD645AA0731C9F574077107DC9C44356F5C04D80F28FA. Ordinary branch codex/alert-quiet-hours-1002 from ba05ea2dbd73cbde5b8757d42d5e23df9122d866; foreign headman/auth/user-archive evidence preserved. No applicable nested infra/docs AGENTS found; project/tests rules and rct-verification applied within current delivery policy.

## Relevant scope
Only infra/alertmanager/alertmanager.yml, one related line docs/operations/monitoring/alerts.md, and own evidence. No compose/.env/notification/bot edits. Product diff changes end_time23:59→24:00 plus explanatory comments/doc; route/inhibition data unchanged.

## Required behavior
UTC [19:00,24:00) ∪ [00:00,05:00) covers the full10-hour MSK quiet period. Warning route alone references quiet-hours-msk. Critical route has no mute interval and continue:false; existing inhibition remains unchanged.

## Constraints
No external notification/secret/prod/restart/push/main mutation; no children. Docker only root bounded lease, cached image, network:none, readonly config, no server/ports/pull.

## Existing patterns
Prod pins prom/alertmanager:v0.27.0@sha256:e13b6ed5cb929eeaee733479dce55e10eb3bc2e9c4586c705a4e8da41e5eacf5; local image v0.27.0. [Official exact-version configuration](https://raw.githubusercontent.com/prometheus/alertmanager/v0.27.0/docs/configuration.md) lines285–288 defines inclusive start/exclusive end and explicitly permits24:00 as end of day. Same document route semantics190–192 says mute prevents notifications while continue:false still stops matching siblings. No guessed midnight syntax/timezone change.

## Acceptance criteria
Config valid for pinned v0.27.0. Documented boundary matrix below follows official parser semantics and selected config; native amtool routing can prove selected receiver/routes but cannot simulate wall clock or actual notification delivery.

| MSK time | UTC time | Warning quiet interval | Critical quiet mute |
|---|---|---|---|
|21:59:59|18:59:59|false|false|
|22:00:00|19:00:00|true|false|
|00:00:00|21:00:00 previous UTC day|true|false|
|02:59:59|23:59:59 previous UTC day (old gap)|true|false|
|03:00:00|00:00:00|true|false|
|07:59:59|04:59:59|true|false|
|08:00:00|05:00:00|false|false|

## Verification
git diff --check exit0. Official v0.27.0 semantics checked; source review/root narrow ACK. Native amtool/go unavailable on Windows. Bounded cached-image amtool check-config + local routes test queued with root; runtime NOT RUN before lease. No full monitoring stand or broad audit.

## Do not / limits
Do not infer Telegram delivery or production reload from local config validation. Existing inhibition still applies independently; critical bypasses this quiet interval, not every possible inhibition/silence. No all-alert redesign/new framework. Root integrates completed scoped commit after native validation; no push.
