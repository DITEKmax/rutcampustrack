# PWA install offer — implementation evidence

## Goal

Finish the saved PWA install offer so it is ready for integration and bounded browser acceptance. Risk: S1. The offer is available only in the authenticated active student view, as accepted in the saved package.

## Context and evidence

- Canonical project rules: `C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/orchestration-v2/RULES.md`, SHA-256 `1FF4F775373990D2FC1CDEB2DC525AB826929161BB0A4B679F69FE956428B39A`.
- Worktree baseline: `31686aa3c03848a5797d115bd82b31665b574601` (`codex/pwa-install-20260923`).
- The initial worktree also had unrelated changes in `.agent/orchestration-v2/LEAF-PACKET.md` and `.agent/orchestration-v2/RULES.md`; these are preserved and excluded from this package.
- Read the applicable project `AGENTS.md`, `frontends/AGENTS.md`, `tests/AGENTS.md`, project `CURRENT.md` and leaf packet, plus the `rct-verification`, `rct-source-resolution`, and `rutcampustrack-design` skills.
- Design originals reviewed: `docs/product/reference-rutcampustrack-design/{BRAND_DIRECTION,A11Y_REQUIREMENTS,COMPONENT_REGISTRY}.md` and `docs/wireframes/student/101-student-home.md`. The component registry says PWA is a separate surface; the saved task packet supplies the install-offer behavior and role gate.
- Root confirmed the student-only gate is accepted by the saved package. The older JS-SYSTEM-12 wording is not the canonical current product catalog and does not expand this contract to pre-auth views.

## Relevant scope

Sole-writer product scope:

- `frontends/pwa-vue/src/App.vue`
- `frontends/pwa-vue/src/main.ts`
- `frontends/pwa-vue/src/features/install/InstallOffer.vue`
- `frontends/pwa-vue/src/features/install/install-prompt.ts`
- `frontends/pwa-vue/src/features/install/install-offer.pcss`

This evidence file is the only added `.agent/` artifact in this package.

## Required behavior

- Capture `beforeinstallprompt` during bootstrap, retain the event, and defer its prompt until the user presses the install button.
- Give iOS users the Safari → Share → Add to Home Screen steps.
- Hide the offer when it was dismissed, the app is installed, or standalone display mode is active.
- Persist dismissal defensively; if browser storage is unavailable, suppress it for the current page.
- Render only for an authenticated active student view.

## Constraints

- Keep the saved feature design and role gate; no product redesign or contract expansion.
- Preserve all unrelated working-tree changes. Do not edit shared routing/config, introduce a browser-test framework, start shared Docker/Gradle resources, or push/deploy/merge.
- Do not change code because of an unlinked WARN/ERROR.

## Existing patterns

- `main.ts` creates bootstrap-scoped controllers before `App` mounts; the offer is a typed Vue feature with a controller prop.
- PCSS imports mobile-core design tokens, uses the existing focus-ring mixin and minimum touch-target token, and keeps styles outside Vue SFCs.

## Acceptance criteria

1. The early event is captured and prevented from triggering browser UI automatically.
2. The native prompt is invoked only after an explicit install-button click.
3. iOS sees clear Safari installation guidance and no native install button.
4. Dismissal hides the offer and is persisted where storage is available.
5. Installed/standalone state hides the offer.
6. Non-student or unauthenticated views do not render the offer.
7. The assigned diff contains exactly the five product files above plus this evidence file.

## Verification

Inherited checks from the saved project `CURRENT.md`: types and targeted lint are reported PASS; the exact commands and exit codes were not captured in the leaf handoff, so they are not represented as locally reproduced checks. The full lint is known to have unrelated existing Vite errors and was not rerun.

The authenticated browser flow previously stopped at a fixture 404. The root assigned the authenticated fixture and combined build to the integration runtime lane. This leaf makes no runtime PASS claim; bounded browser acceptance remains pending that integration lease.

Local candidate checks are recorded below against baseline revision `31686aa3c03848a5797d115bd82b31665b574601`. The current source review confirms the behavior is wired as listed, but does not substitute for browser runtime evidence.

## Do not

- Do not prompt on page load or expose the offer outside the accepted student view.
- Do not modify the saved role gate, broaden first-visit behavior, or implement an alternate iOS install action.
- Do not absorb unrelated `.agent/` or frontend changes into the scoped commit.

## Checks and runtime record

| Revision | Command | Exit code | Environment | Evidence / result |
|---|---|---:|---|---|
| `31686aa3` candidate | `git diff --cached --check` | 0 | Windows PowerShell, this worktree | No whitespace errors in the staged candidate. |
| `31686aa3` candidate | `git diff --cached --stat` | 0 | Windows PowerShell, this worktree | 6 files changed, 472 insertions, 1 deletion. |
| `31686aa3` candidate | `git diff --cached --name-only` | 0 | Windows PowerShell, this worktree | Exactly the five product files and this evidence file are staged; unrelated `.agent/orchestration-v2/{LEAF-PACKET,RULES}.md` remain unstaged. |
| Integration revision pending | Authenticated browser install flow | Pending | Root-owned integration harness | Runtime is pending fixture + combined build; previous unauthenticated attempt returned fixture 404. No runtime PASS is claimed. |

## Diff and limitations

The product diff captures the prompt controller during bootstrap, gates the offer to the active student session, supplies native/iOS presentation, handles dismissal and installed/standalone state, and styles the feature with existing mobile-core tokens. No browser runtime result is available in this leaf; the integration lane must verify the actual authenticated browser flow before overall acceptance.
