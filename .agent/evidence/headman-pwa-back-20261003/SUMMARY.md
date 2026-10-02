# SOURCE-READY: PWA product Back

Goal/criteria: староста возвращается из Группы, ДЗ и списка Конструктора в «Ещё» существующим Back, без reload/выхода; из editor Back возвращает предыдущий список. Telegram native Back не дублируется, navigation не отправляет draft/pending запросы.

Baseline2813a8b6f1578f8c2505ea750148dcef75bc06ee, branchcodex/headman-back-1003, sole writer assigned admin-group-promotion worktree. Canonical RULES SHA A208AA4380B64376A4EAD645AA0731C9F574077107DC9C44356F5C04D80F28FA. Current root reviewed critical originals and explicitly authorized adapter-only correction; no separate independent review required for this one-line ownership change.

Cause: PwaHostAdapter declares backOwner=browser despite comment that product owns navigation; it has no subscribeBack/history integration. MobileShell resolves product Back only for owner=product and host Back only for owner=host. Nested task/detail/editor hide dock, so browser ownership leaves none of the three visible controls. Screen #back is wired correctly. Existing mobile spec431/593 requires one product Back for PWA nested tasks and native Telegram Back in TMA. This is a functional defect, not a postponed redesign.

Minimal source fix: frontends/pwa-vue/src/pwa-host.ts changes backOwner browser→product. Existing shell-contract/HeadmanScheduleScreen/host interface/history semantics remain unchanged. An initial proposed shared browser→product mapping was locally checked, then fully withdrawn before commit after root architecture decision; final shared diff is empty. Future intentional browser-owned history remains distinct.

Existing test augmented: frontends/mobile-core/tests/navigation.test.mjs visibility case instantiates real PwaHostAdapter; confirms visible product Back/no host duplicate/no dock for group/detail, homework/task, constructor/task and editor. Existing stack returns those three screens to headman-more; editor first returns schedule/list, then headman-more. Telegram owner=host still hides product Back and exposes native Back. No new basic button-wiring test or framework.

Checks on final source: focused node test1PASS and Vue/PWA typecheck exit0/chunkb9c539; scoped ESLint and product diffcheck exit0/chunk7635dd. Earlier alternate-mapping focused check717d43 exit0 is preparatory only, not final proof or additional acceptance. Exact commands/environment in checks.json; final source diff in source.diff.

Inventory: modified product pwa-host.ts (one line), modified existing navigation.test.mjs; created own SUMMARY/checks/source.diff. No deleted files, new styles/tokens/dependencies/history API/auth/transport changes. Draft, retry intent, save handlers and TMA adapter unchanged. Foreign .agent/transfer-attendance-evidence.md and unrelated untracked/cache preserved; main/runtimeholder untouched.

Limits/runtime: no build, browser, screenshot or shared stand launched; root queues actual PWA return acceptance after common artifact update. No Figma/redesign/whole-UI audit/full suite/providers/deploy/push. Light checks completed with no retained processes/resources.
