# Today visual source correction — VISUAL-r3

Date: 2026-09-06. Scope: `frontends/mobile-core` and the PWA runtime files
already in the FE lane. Writer model/effort: Terra high. Contract: JS-STUDENT-01-r1.

## Source and correction

Source evidence is the retained original CSS attachment, packet contexts R002, R003
and R004, and their screenshots. This correction replaces the earlier visual attempt,
which independent source inspection found materially divergent. It preserves the
generated API, query, auth and check-in behavior.

| Source requirement | Implementation evidence |
|---|---|
| R002 rhythm at 390×844 | `today-screen.pcss`: top padding 20, role 44, header-to-hero gap 12, hero 171, list gap 12, row 94 and dock 358×76/fixed bottom 16. |
| Role and primary action gradients | Source-backed component tokens in `mobile-core/src/styles/tokens.pcss`; role uses cached chevron SVG, no border. |
| Hero and cards | No visual “Сейчас идёт”, two hero metadata groups, separate row start/end times, separate room/type lines, no default eligibility row, accent current card and 32-point present mark. |
| Pending and confirmed | Hero state data attribute: pending expands to 201 and presents retry timer under the action; confirmed uses raised hero/current card and success-fill disabled CTA. |
| Dock | Cached asset icons; order `Сегодня / Задания / Учёт / Ещё / Профиль`; disabled destinations are truthful and retain Figma opacity. |
| Typography | Reusable canonical `rct-type-body`, `rct-type-dense` and `rct-type-micro` mixins live in shared `styles/tokens.pcss`; packet-specific 20/26, 15/19, 11/14 etc. are explicit source-backed component tokens. |

The brandbook explicitly fixes mobile navigation blur at 25px. CSS therefore retains
the canonical `1.5625rem` token despite the older source CSS export showing 12.5px;
this is an explicit conflict resolution rather than a silent visual rewrite.

## Runtime support

`fixture-transport.ts` adds the documented test-only selector
`?fixtureToday=default|pending|confirmed`. It derives the three-row default and ACK
states without modifying canonical contract fixture JSON. A post-check-in refetch
keeps the matching fixture state visible. `sw.js` v2 is network-first for navigation
with cached offline fallback so a rebuilt shell cannot reference removed hashed assets.

## Checks before root build/browser pass

- `npm run typecheck --workspace @rct/mobile-core` — exit 0
- `npm run lint --workspace @rct/mobile-core` — exit 0
- `npm exec vitest -- run mobile-core/src/test-adapter/fixture-transport.test.ts mobile-core/src/domain/checkin.test.ts mobile-core/src/domain/offline-today.test.ts` — exit 0, 3 files / 6 tests
- `git diff --check` — exit 0

Root owns the post-correction production fixture build, PWA preview, service-worker
reload and the authoritative browser screenshot/geometry evidence.
