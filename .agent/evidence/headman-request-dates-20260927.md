# Headman request date correction — 2026-09-27

## Scope and criteria

- Branch `codex/headman-request-dates-20260927`, based on `f10e3c39df5ab44b4fa47937acc8cd65bfe45dd1`.
- In the Headman requests queue, coverage period dates and lesson detail dates must retain the calendar day from `YYYY-MM-DD` under Western and Moscow browser timezones. Both views call the same `dateLabel` helper.
- Timestamp inputs retain the former browser-local formatting. `null` remains `—`; unparseable values remain unchanged.
- No API, stored dates, layout, styles, or unrelated warning/error fixes are in scope.

## Context and implementation evidence

The supplied live-browser observation on main `f10e3c39` showed the Headman queue one day earlier than the matching student request detail (`12 Sep` vs `13 Sep 2026`). The cause was parsing a date-only string as UTC midnight and formatting it in the browser timezone.

`HeadmanRequestsScreen.vue` now detects exact `YYYY-MM-DD` values and formats them in UTC. Timestamp values continue through the old local-time path:

```ts
const dateOnly = /^\d{4}-\d{2}-\d{2}$/.test(value)
const date = new Date(value)
if (Number.isNaN(date.getTime())) return value
const options: Intl.DateTimeFormatOptions = { dateStyle: 'medium' }
if (dateOnly) options.timeZone = 'UTC'
return new Intl.DateTimeFormat('ru-RU', options).format(date)
```

## Checks and runtime evidence

Commands ran in Windows PowerShell in the assigned worktree unless stated otherwise:

| Check | Exit | Evidence |
|---|---:|---|
| `npm.cmd --prefix frontends/mobile-core run typecheck` | 0 | `tsc -p tsconfig.json --noEmit` |
| ESLint on the assigned SFC with `--quiet` | 0 | No lint errors. Warnings were suppressed; this is not a clean strict-lint claim. |
| ESLint on the assigned SFC with `--max-warnings=0` | 1 | 124 warnings, 0 errors, all reported in unchanged markup outside the formatter diff. |
| `npm.cmd --prefix frontends/mobile-core run lint` | 1 | Broad package command reported 1 error and 552 warnings. This exceeded the requested scope; unrelated diagnostics were left untouched. |
| `git diff --check` | 0 | No whitespace errors in the code diff. |
| Node TZ reproduction, extracting the actual `dateLabel` body from the Vue source | 0 | `America/Los_Angeles`: date-only `13 сент. 2026 г.`, timestamp `12 сент. 2026 г.`; `Europe/Moscow`: date-only `13 сент. 2026 г.`, timestamp `13 сент. 2026 г.`. Assertions also confirmed `null` → `—`, invalid input → unchanged, and timestamp output equals the former formatter. |

The shared authenticated PWA stand was closed safely. No app build, server restart, Docker action, or live UI acceptance was run for this branch.

## Diff and limitations

- Changed: `frontends/mobile-core/src/features/headman-requests/HeadmanRequestsScreen.vue` — date-only timezone selection in `dateLabel`.
- Added: this evidence file.
- API and stored data are unchanged. The Node reproduction verifies timezone formatting, not the full authenticated PWA flow. Existing ESLint warnings and the broad lint error remain outside this date-fix scope.
