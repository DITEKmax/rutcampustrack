# Evidence

## Source and scope

- Figma source opened before implementation: `VgVjQYWILLG9AC7Eh12VMk`, node
  `4581:3062`.
- Frozen baseline: `8002b9ea4356b10779c5bb9a6d99746d32d78ae2`.
- Changed implementation is limited to the mobile-core shell extraction,
  Today integration, exports, and navigation contract test listed in `diff.md`.
- The canonical Figma glass-background-blur variable is `25`; the `12.5` value
  visible in generated CSS context is a conversion representation, so there is
  no source-token defect in this scope.

## Static evidence

- `npm run typecheck` in `frontends`: exit `0`.
- `npm run build` in `frontends`: exit `0`; both PWA and TMA Vite builds emitted
  production bundles.
- Contract tests: exit `0`, 6/6 passed.
- Scoped ESLint: exit `0`.
- Full workspace lint: exit `1` only at the pre-existing fixture transport alias
  (`frontends/mobile-core/src/test-adapter/fixture-transport.test.ts:94`), with
  no findings in the changed shell/Today files and app lint completing cleanly.

## Recorded defect gate and correction

- Request/reference: the shell contract requires host keyboard ownership to hide
  the dock when `keyboardVisible` is omitted by the feature.
- Reproduction against the compiled `MobileShell` before correction: Vue emitted
  `keyboardVisible: { type: Boolean, required: false }`; the custom-renderer
  component probe returned `{"before":true,"after":true,"propType":"Boolean"}`
  after the host callback supplied `true`. The callback was therefore masked by
  Vue's absent Boolean prop cast to `false`.
- Correction: `MobileShell` supplies a typed default factory that resolves to
  `undefined`, preserving the distinction between an omitted feature override
  and host-owned keyboard state.
- New evidence: the same component probe returns
  `{"before":true,"after":false,"propType":"Boolean","propDefault":"undefined"}`
  with exit `0`.

## Typography evidence

After hot reload of the existing `http://localhost:5175/` runtime, computed
styles returned `"Onest Variable", Onest, sans-serif` for both `.mobile-shell`
and `.mobile-bottom-nav__label`, with `font-variant-numeric: tabular-nums` and
`font-feature-settings: "lnum", "tnum"`. The dock labels therefore retain the
Today typography after shell extraction.

## Runtime evidence

The existing server at `http://localhost:5175/` responded `HTTP/1.1 200 OK`.
The rendered page showed the Today surface and one shell-owned navigation dock.
DOM inspection returned:

```json
{
  "shell": "root",
  "dockVisible": "true",
  "keyboardVisible": "false",
  "navLabels": [
    {"label":"Сегодня","disabled":false},
    {"label":"Задания","disabled":true},
    {"label":"Учёт","disabled":true},
    {"label":"Ещё","disabled":true},
    {"label":"Профиль","disabled":true}
  ]
}
```

Runtime was read-only against the already running fixture server; no competing
server was started and no external host SDK was invoked.
