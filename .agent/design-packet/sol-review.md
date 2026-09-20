# Sol targeted review record — 06.09.2026

## FAIL 1 (S1)

`docs/design/packets/JS-STUDENT-01/token-mapping.json` did not trace every
captured token reference to a distinct live definition and canonical token. It
could therefore silently choose a Figma value or invent an alias.

## Bounded correction

- Rebuilt the mapping from actual `var(--…)` references in R002/R003/R004/R008/R009/R010:
  34 unique references.
- Each record cites R011, a canonical `tokens-v2.json` path, target CSS variable,
  source and resolved canonical value, plus exact/rem-base-16/discrepancy status.
- The initial parser incorrectly truncated escaped `radius/2xl` to `radius`.
  The focused correction preserves serialized expressions such as
  `var(--radius\\/2xl,24px)`, normalizes the name to `radius/2xl` and maps it
  exactly to existing `--radius-2xl` at the 16px rem base. No alias or discrepancy
  remains.
- Literal role-pill and CTA gradients are reference-style gaps. Live-captured
  SelectField/FileUploadField reuse is distinguished from registry-only composition.

## Re-review status

**PENDING focused re-review:** mapping source coverage, target validity, rem conversion
and manifest hashes only. Previously passed screenshots, downloaded SVGs and runtime
are deliberately not repeated.

## Focused re-review — PASS

**Verdict:** PASS, S2 bounded review, findings 0.

Evidence accepted without repeating earlier PNG/SVG/raw checks:

- six actual code responses contain 34 unique token references across 436 `var(…)` occurrences;
- both serialized radius forms resolve to `radius/2xl`; R011 gives `24`, canonical target is `--radius-2xl: 1.5rem` at the fixed 16px base; no phantom `--radius`;
- all 34 live/canonical targets and values are exact under their recorded unit rule;
- four packet-document hashes and the external canonical-token hash are valid.

Receiver is confirmed off. No new Figma MCP reads, asset downloads or product changes occurred after the earlier evidence checks.
