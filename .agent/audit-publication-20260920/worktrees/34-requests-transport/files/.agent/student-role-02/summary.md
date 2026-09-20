# Requests transport handoff checkpoint

Date: 2026-09-08. Writer: fresh Luna implementation leaf. Risk: S3. Worktree:
`C:\Users\maksd\IntelliJIDEA\rutcampustrack\.agent\worktrees\student-role-02\requests-transport`.
Branch: `codex/student-role-02-requests-transport`. Baseline/current HEAD:
`d3c31acb8cce53791a4981e5858a37d44fdc9a0e`.

The bounded Requests transport implementation and the explicit null-fallback
correction are present and isolated checks are green. The Attendance listener
now has a three-attempt bounded retry policy and reject-to-DLQ recovery, with a
real Rabbit Testcontainers check covering invalid envelopes and transient
decision failures. EventConsumer no longer falls back to retired lesson
services when StudentRequestService is absent.

Recorded green checks include Attendance compile and unit tests, isolated
EventConsumerIT, isolated RabbitDecisionRetryIT (2/2), mobile BFF tests,
frontend typecheck/lint/contract/generated-type checks, targeted StudentApi
client tests (4/4), protobuf generation, JSON parsing, bot compileall and
whitespace diff validation. Exact commands and exit codes are in `checks.json`.

The combined EventConsumerIT/RabbitDecisionRetryIT run remains OPEN: exit 1,
11 tests with one `WantedButNotInvoked` at RabbitDecisionRetryIT.java:93,
although both classes pass alone. No further implementation or broad checks are
started after the root stop instruction. Gateway/XFF and full ingress remain
root-owned and deferred. No commit, reset, clean, production operation or
foreign-worktree change was performed.
