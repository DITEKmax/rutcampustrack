# Packet подготовительной партии 09–10

- Дата: 06.09.2026
- Writer: `/root/preparation_writer`, developer, `gpt-5.6-terra`, high; S2.
- Baseline: `87784165874e2da6fc261abc1c01584e24624289` (`codex/materials-transfer`).
- Scope: canonical registry job stories, provenance/drift, backend/legacy/readiness/check
  matrices, навигация и правила разделённой FE/BE реализации. Исходники kit, Figma,
  продуктовый код, API, базы и настройки инструментов вне scope.
- Owner direction: перенос материалов уже выполнен; не запускать bulk transfer и не
  менять source bytes. Разрешена точная копия playbook в `docs/implementation/`.
- Criteria: все 147 stable JS IDs имеют mapping; документы отличают решение от
  hypothesis и static evidence от runtime; контракт до parallel FE/BE не объявлен
  frozen без выбранного canonical artifact; legacy не удаляется без replacement and
  coverage; catalog содержит реальные commands/status; docs parse and cross-reference.
- Risks: S2 из-за contracts/authz/retirement; S3 действий не выполняется. Независимое
  Sol high review запрошено root, но routing пока blocked лимитом потоков.
- Ownership: один writer в текущем checkout; чужие untracked transfer artifacts
  сохраняются. Перед началом не найдено активного claim/transaction для данного scope.
