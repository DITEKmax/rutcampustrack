# Targeted Q09/Q12 frame and Q09 selector review

Verdict: CHANGES_REQUIRED — one P2 in Q09 selector. The exact-path privacy correction alone is APPROVE_SOURCE.

Independent read-only Source review against Root base `75cb5ed5ae8d731991a5efd447c611b0eb9f65da`; requested/selected Sol6.1/high, actual metadata UNKNOWN. Current contract, CURRENT, decisions, questions, resources, RULES and `receipts/Q09-Q12-exact-frame-correction.safe.json` were read. No project checks, browser, server, network or product mutations were run by this reviewer.

## P2 — Prefix also selects the labeled section

`tests/e2e/specs/mobile-homework-lifecycle.spec.ts:92` changes the locator to `page.getByLabel(/^Привязка/)`. Actual `AssistantHomeworkScreen.vue:689` gives the containing section `aria-label="Привязка домашнего задания"`; its select at line 698 has the wrapping label starting with `Привязка`. Both therefore match this locator. The pinned Playwright 1.59.1 label engine queries all elements and returns an element's nonempty aria-label before checking its tag. `Locator.selectOption` passes `strict: true`, and the frame action rejects multiple matches. Once reached, this correction creates a strict mode failure before selecting DATE.

Independently opened retained library Source at `/workspace/rct-worktrees/client-acceptance/tests/e2e/node_modules/playwright-core/lib/generated/injectedScriptSource.js`: SHA256 `53aac4133e2d1bfa605c7c33590d95e4edb57078412f895d21b5a08663296975`, identical to the author's pinned library. Critical symbols: `getElementLabels`, `_createInternalLabelEngine`; `lib/client/locator.js:250` and `lib/server/frames.js:930,1125` establish strict action behavior. Minimal correction: constrain the target to the select, for example `page.getByRole('combobox', { name: /^Привязка/ }).selectOption('DATE')`. Preserve all existing result/history/completion/rights assertions.

This is a Source counterexample to the new locator, not attribution of the historical 75cb FAIL (fallback16); that cause remains UNKNOWN.

## Privacy correction

The production helper delta is only four literal public spec paths. The path read remains gated by exact `info.file` membership; stack text cannot choose a different file. Own stack descriptor access, 8192-byte/64-line budgets, per-frame length/control-character restrictions, exact current-file comparison, real source line/column bounds and fallback behavior are unchanged. No dynamic path execution or raw message/body publication is introduced. Sanitization retains the generic replacement, failed outcome, fixed Source location and closed reporter projection. A fabricated matching frame remains an untrusted reported hint; it does not authenticate a cause, operation or success.

The four added positive controls distinguish accepted line1 from fallback line2, also reject a valid foreign assignment frame for each admitted file, and retain a disallowed auth.spec control. Existing secret/path/range/descriptor and producer-before-attachment/failure-retention controls remain. The supplied unchanged browser-free check receipt reports PASS, with markers absent, original metadata leak reproduced, failure retained, interruptions/missing-guard handled, processes reaped and temporary link removed. These are reused author checks, not reviewer execution.

Reviewed SHA256 pins (independently matched current Root bytes):

- `tests/e2e/fixtures/artifact-privacy.ts`: `d200c9adb73cd8ead7967b82fdbdbd1cc0f2e8555498ccbde7f2e51688502da1`
- `tests/e2e/artifact-controls/privacy.control.spec.ts`: `6707de06b0d46af08761aa8c38e2dae2609b7daa20d5f3b2bfd020ba0903f244`
- `tests/e2e/specs/mobile-homework-lifecycle.spec.ts`: `13c2b7aec8e0644504f8209ee3d8f4bc983a76c0034ef058e0485d143ab7f6d5` (Q09 author commit `c654a77660251cbce90fb10bf5b906802cc0949b`).

Actual corrected browser/backend acceptance: NOT_RUN. Historical Q09/Q12 fallback16/95 failures remain UNKNOWN. Q11/Q14 behavior and whole Q12 acceptance are outside this targeted verdict. B013/B014 remain frozen.

## Narrow P2 closure — b1158b0

Final verdict: APPROVE_SOURCE. Independently checked the one-line correction in commit `b1158b079a5e0fd5951f35fe0e89b331b7e67579` and current Root bytes. Q09 spec SHA256 is now `a2c98ce9612f317826b6b76d80e242b8998c5ba39f0036e0722b923f592ae726`; the privacy helper/control SHA256 pins above remain identical.

The locator is now `page.getByRole('combobox', { name: /^Привязка/ }).selectOption('DATE')`. Actual Vue Source has an ordinary single-select without multiple/size; the pinned role engine maps that SELECT to combobox, resolves its associated label, and rejects elements whose role differs. Its enclosing named SECTION maps to region and is excluded. The other select is labeled Предмет and does not match the name prefix. This closes the specific multiple-match finding. The commit changes no other assertion, timeout or fixture behavior. Supplied formatter/type-check PASS and cleanup are reused; no checks were rerun by this reviewer. Actual corrected browser/backend acceptance remains NOT_RUN; historical failure causes remain UNKNOWN.
