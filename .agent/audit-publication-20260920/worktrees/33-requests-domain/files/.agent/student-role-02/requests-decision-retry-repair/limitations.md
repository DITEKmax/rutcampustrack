# Limitations

- This is bounded attendance student-request domain evidence. It is not a
  full student-role, public API, BFF, proto, bot, frontend, lifecycle,
  dependency-security or XFF acceptance.
- The fault injector proves the required post-commit ambiguity against a real
  Mongo replica-set transaction and production outbox storage. It does not
  model every driver, network proxy or deployment failure mode.
- Recovery intentionally returns only when authority/group/role, self guard,
  terminal status, decision actor and requested outcome match; EXCUSE also
  requires the normalized decision comment. If the recovery read itself fails
  transiently, `recovery.get()` currently propagates that read error from the
  decision helper rather than entering a second recovery transaction. That
  behavior is outside the reproduced defect and was left unchanged; the
  current runtime evidence proves a successful recovery read, not universal
  recovery through a second temporary read failure.
- The evidence was collected on HEAD
  `8002b9ea4356b10779c5bb9a6d99746d32d78ae2` in a deliberately dirty shared
  worktree. Fresh independent Sol high recheck remains a root gate.
