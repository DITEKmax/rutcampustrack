# Phase 30: Schedule + Check-in UI - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-04-06
**Phase:** 30-schedule-check-in-ui
**Areas discussed:** Schedule navigation, Check-in flow, Real-time updates, Offline data strategy

---

## Schedule Navigation

| Option | Description | Selected |
|--------|-------------|----------|
| Swipe + tap tabs | Horizontal swipe gesture between days + tappable day tabs. Feels native on mobile. | ✓ |
| Tap tabs only | Day tabs at top, no swipe gesture. Simpler to implement. | |
| Calendar picker | Mini calendar dropdown to jump to any date. | |

**User's choice:** Swipe + tap tabs
**Notes:** None

---

| Option | Description | Selected |
|--------|-------------|----------|
| Today, auto-scroll to current/next lesson | Opens on today's tab, scrolls to active/upcoming lesson. | ✓ |
| Today, show full day from top | All lessons from first to last, no auto-scroll. | |
| Week overview first | Compact grid of all 6 days, tap to expand. | |

**User's choice:** Today, auto-scroll to current/next lesson
**Notes:** None

---

| Option | Description | Selected |
|--------|-------------|----------|
| Current week only | Mon–Sat tabs for this week. Next week appears on Monday. | |
| Swipe between weeks | Left/right week navigation with week indicator. | ✓ |
| 2-week window | Current + next week visible. | |

**User's choice:** Swipe between weeks
**Notes:** User wants students to see next week for planning ahead.

---

| Option | Description | Selected |
|--------|-------------|----------|
| Header with arrows + week range | "← 7–12 апр →" above day tabs. Clear context. | ✓ |
| Infinite horizontal scroll | Continuous scroll, no week boundary. | |
| Week dots below tabs | Small dots showing current week position. | |

**User's choice:** Header with arrows + week range
**Notes:** None

---

| Option | Description | Selected |
|--------|-------------|----------|
| Floating "Сегодня" pill appears | Small floating button to snap back to today. Disappears when on current week. | ✓ |
| Today tab always highlighted | Today's tab has dot/underline even on other weeks. | |
| Double-tap tab strip | Double-tap to return to today. | |

**User's choice:** Floating "Сегодня" pill appears
**Notes:** None

---

| Option | Description | Selected |
|--------|-------------|----------|
| Mon–Sat only | Russian university schedule, no Sunday. | ✓ |
| Mon–Sun, Sunday grayed if empty | All 7 days, gray out empty Sunday. | |
| Dynamic — only days with lessons | Show only days with lessons. | |

**User's choice:** Mon–Sat only
**Notes:** None

---

## Check-in Flow

| Option | Description | Selected |
|--------|-------------|----------|
| On the active lesson card | "Отметиться" button directly on lesson card in schedule. | ✓ |
| Dedicated /checkin screen | Separate screen via bottom nav tab. | |
| Both — card button + dedicated screen | Redundant paths. | |

**User's choice:** On the active lesson card
**Notes:** None

---

| Option | Description | Selected |
|--------|-------------|----------|
| Instant GPS capture + submit | Tap → spinner → GPS → submit → toast. Single action. | ✓ |
| Confirmation dialog first | Extra confirmation step before GPS capture. | |
| Preview GPS on map first | Mini-map with location before submit. | |

**User's choice:** Instant GPS capture + submit
**Notes:** None

---

| Option | Description | Selected |
|--------|-------------|----------|
| Inline error with settings link | Toast with instructions to enable GPS in browser settings. | ✓ |
| Full-screen instructions overlay | Step-by-step screenshots for enabling GPS. | |
| Silent retry with timeout | Retry GPS silently for 10s, then show error. | |

**User's choice:** Inline error with settings link
**Notes:** None

---

| Option | Description | Selected |
|--------|-------------|----------|
| Card updates status badge + button disappears | Badge: "Идёт" → "Отмечен" (green). Button → checkmark. | ✓ |
| Success toast only, card stays same | No card UI change until refresh. | |
| Card animates celebration | Confetti/pulse animation. | |

**User's choice:** Card updates status badge + button disappears
**Notes:** None

---

| Option | Description | Selected |
|--------|-------------|----------|
| Redirect to /schedule | /checkin is alias for /schedule. | |
| Empty state with next lesson info | "Сейчас нет активных пар. Следующая: [Subject] в [time]" | ✓ |
| Remove /checkin tab entirely | 3-tab nav. | |

**User's choice:** Empty state with next lesson info
**Notes:** /checkin stays as a dedicated screen, not just a redirect.

---

## Real-time Updates

| Option | Description | Selected |
|--------|-------------|----------|
| Animate counter increment | Subtle number flip animation on STOMP event. No toast. | ✓ |
| Counter + subtle pulse | Number updates + highlight pulse. | |
| Counter + toast per person | Toast per check-in. Noisy in large groups. | |

**User's choice:** Animate counter increment
**Notes:** Avoids noise in large groups.

---

| Option | Description | Selected |
|--------|-------------|----------|
| Silent auto-reconnect with backoff | Exponential backoff. Indicator only after 30s. Refetch on reconnect. | ✓ |
| Always show connection indicator | Persistent green/red dot. | |
| No reconnect — rely on polling | Fall back to polling on drop. | |

**User's choice:** Silent auto-reconnect with backoff
**Notes:** None

---

| Option | Description | Selected |
|--------|-------------|----------|
| Yes, both schedule and /checkin | Shared STOMP subscription. | ✓ |
| Schedule cards only | Only schedule view gets updates. | |
| Checkin screen only | Only dedicated screen gets updates. | |

**User's choice:** Yes, both schedule cards and /checkin screen
**Notes:** None

---

## Offline Data Strategy

| Option | Description | Selected |
|--------|-------------|----------|
| Subtle top banner with last-updated time | "Офлайн · обновлено 15 мин назад" using OfflineBanner. | ✓ |
| Dim overlay on stale cards | Semi-transparent overlay + "stale" badge. | |
| No indicator until error | Show cached data silently. | |

**User's choice:** Subtle top banner with last-updated time
**Notes:** None

---

| Option | Description | Selected |
|--------|-------------|----------|
| Auto-refetch on reconnect + pull-to-refresh | TanStack Query onReconnect + manual pull-to-refresh. | ✓ |
| Pull-to-refresh only | No auto-refetch. | |
| Auto-refetch only | No manual trigger. | |

**User's choice:** Auto-refetch on reconnect + pull-to-refresh
**Notes:** None

---

| Option | Description | Selected |
|--------|-------------|----------|
| Disabled with explanation | Button grayed + "Нет подключения". No offline queue. | ✓ |
| Hidden when offline | Button disappears. | |
| Queue offline, submit later | Queue check-in, submit on reconnect. | |

**User's choice:** Disabled with explanation
**Notes:** GPS coords become stale, server might reject delayed check-ins anyway.

---

## Claude's Discretion

- Swipeable tab strip library choice / custom implementation
- Exact lesson card layout and information density
- STOMP client library and connection management details
- Pull-to-refresh implementation approach
- Loading skeleton design for schedule
- Error toast styling and timing
- Week data prefetching strategy

## Deferred Ideas

None — discussion stayed within phase scope
