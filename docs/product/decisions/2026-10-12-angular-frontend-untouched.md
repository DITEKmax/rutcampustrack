# Angular frontend excluded from Cloud mutations

## D010 — 2026-10-12: Angular frontend remains untouched

Owner instruction: «помни, что фронт на ангуляр мы вообще не трогаем (весь веб сейчас переписывается на vue) и почему-то модель ушла с ULTRA на extra high но ты продолай свою работу с агентами и тд».

Accepted scope: do not change the Angular frontend at all, including formatting, configuration, template whitespace corrections or functionality. Current tracked Angular application is frontends/web-panel/ (package dependency @angular/core ^19.2.0); its complete tracked subtree is excluded from this Cloud task's mutation and formatting inventory. Preserve exact baseline/current Root bytes and file modes, and unknown/foreign WIP. Sole formatter may undo only its own already prepared Angular changes in its isolated worktree, preserving receipts; no Angular change has been integrated into Root. Explicit supported inventory exclusion follows this owner decision, not a formatter or lint failure.

Supersedes: TASK-CONTRACT §3/§4 all-author-source formatting scope ONLY for the current Angular frontend, and earlier technical approvals of Angular formatting/JSON-header/template-preservation corrections. Vue PWA/TMA, server, tests/harness and other accepted author-source formatting remain in scope. The independent local Vue web rewrite does not authorize new web implementation in this Cloud task.

Model preference: Root remains requested/selected Sol6.1/ULTRA under the original contract; user observes an extra-high UI setting. Actual runtime model/effort remains UNKNOWN where tools do not expose it. No silent fallback or claim of measured ULTRA is permitted. Explicit selected child efforts may still vary by scope under the original contract. Continue authorized work with agents.

Verification: net Root changes beneath frontends/web-panel/ must be zero versus the accepted Root revision and baseline; exclude the exact Angular subtree from reproducible formatter inventory/checks, while preserving all required non-Angular lint/build/security gates. Do not weaken unrelated rules or conceal failed required client/server checks.
