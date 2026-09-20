# FE13 source map and geometry checkpoint

Baseline: `8002b9ea4356b10779c5bb9a6d99746d32d78ae2`
Captured before implementation: 2026-09-08, read-only sources.

## Contract/design sources

| Source | Read evidence | Applied boundary |
|---|---|---|
| `docs/design/tokens-v2.json` | semantic modes and primitive values | local scoped tokens resolve semantic aliases for dark/light; components use vars |
| `docs/design/COMPONENT_REGISTRY.md:1324-1357` | MobileAttendance, MobileMetricLens, inline request | composed features; Requests remains sole form owner |
| `docs/design/COMPONENT_REGISTRY.md:1359-1375` | visible status symbols, compact type cards, 10px metadata | status and metadata geometry |
| `docs/design/figma-spec-mobile.md:356-389` | states, shell/dock boundary, 10px icons, 44px target, history fill | feature states, harness chrome, responsive/a11y |
| `docs/product/reference-rutcampustrack-design/BRAND_DIRECTION.md` | status symbol + word + color; no gradients/glow; reduced motion | local visual treatment and accessibility |
| `docs/product/reference-rutcampustrack-design/A11Y_REQUIREMENTS.md` | WCAG AA, landmarks, focus, live states, 44px touch target | component markup and harness checks |
| `.agent/student-role-02/attendance-source-note.md` | Attendance projection/request boundary | no client formula or eligibility inference |
| `.agent/student-role-02/statistics-root-decision.md` | root accepted statistics boundary | no peer payload, no legacy semantics |
| `.agent/student-role-02/statistics-decision-result.md` | frozen candidate shape | MetricSet4, server series and type selection |

## Exact source frames

| Node | State |
|---:|---|
| 4593:142 | attendance days |
| 4593:848365 | attendance empty day |
| 4593:848496 | attendance absence actions |
| 4595:293 | attendance graph days |
| 4595:848430 | attendance graph weeks |
| 4596:365 | attendance subjects open |
| 4710:232 | inline request slot gate |
| 4768:228 | attendance subjects closed |
| 4603:142 | statistics overview |
| 4603:848696 | detail with two types |
| 4798:142 | detail with one type |
| 4798:200 | detail with two selected types |
| 4798:285 | detail with all three types |

Originals: `C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/student-role-02/design-context/<node>.png` and matching `.txt`.

## Resolved semantic aliases

Values below are resolved from `docs/design/tokens-v2.json` primitive references;
no new palette value is introduced.

| Local var meaning | dark | light | semantic token |
|---|---:|---:|---|
| base | `#0B0B0C` | `#FCFBFE` | `color/surface/base` |
| raised | `#1C1922` | `#FFFFFF` | `color/surface/raised` |
| float | `#292531` | `#FFFFFF` | `color/surface/float` |
| primary text | `#EDEAF3` | `#292531` | `color/text/primary` |
| secondary text | `#C8C2D6` | `#565061` | `color/text/secondary` |
| muted text | `#8A8299` | `#6E6779` | `color/text/muted` |
| border default | `#3D3846` | `#DED9E8` | `color/border/default` |
| border focus | `#9E7AFF` | `#8557F5` | `color/border/focus` |
| accent now | `#B79CFA` | `#6D28D9` | `color/accent/now` |
| accent on now | `#0B0B0C` | `#FFFFFF` | `color/accent/on-now` |
| present fill | `#61DFB8` | `#94E6CB` | `color/status/present/fill` |
| present text | `#61DFB8` | `#0C6A53` | `color/status/present/text` |
| absent fill | `#F27F7F` | `#F7AAAA` | `color/status/absent/fill` |
| absent text | `#F27F7F` | `#8E1D1D` | `color/status/absent/text` |
| excused fill | `#F5B443` | `#F8CE7D` | `color/status/excused/fill` |
| excused text | `#F5B443` | `#764B09` | `color/status/excused/text` |
| none fill | `#C4C4C4` | `#DCDCDC` | `color/status/none/fill` |
| success bg | `#063429` | `#E4F9F1` | `color/state/success/bg` |
| danger bg | `#470E0E` | `#FDECEC` | `color/state/danger/bg` |
| warning text | `#F5B443` | `#764B09` | `color/state/warning/text` |
| disabled surface | `#262626` | `#EDEDED` | `color/disabled/surface` |
| disabled text | `#6B6B6B` | `#A3A3A3` | `color/disabled/text` |

## Pixel → rem geometry

All PCSS dimensions use a 16px root basis (`1rem = 16px`); the browser root is
never rewritten.

| Source geometry | Scoped token |
|---:|---:|
| content 358px | `22.375rem` |
| inset 16px | `1rem` |
| touch/control 44px | `2.75rem` |
| metadata 10px | `0.625rem` |
| stroke 1px | `0.0625rem` |
| metadata/status gap 4px | `0.25rem` |
| subject disclosure visible 22px | `1.375rem` |
| graph plot 170px | `10.625rem` |
| history segment height 8px | `0.5rem` |
| 390px viewport | `24.375rem` |
| 844px viewport | `52.75rem` |

Source-specific frame geometry (e.g. 111px metric cards, 296px lesson card,
342px type cards) is represented by local rem vars only where needed by the
corresponding composition and remains subject to fluid/narrow constraints.
