---
plan: 52-04
phase: 52
status: complete
completed_at: 2026-04-09
---

# Plan 52-04 Summary: Notifications + Profile Pages

## Objective
Implement `/student/notifications` (STOMP event log with sessionStorage persistence) and `/student/profile` (password change form + identity card).

## Tasks Completed

### Task 1: NotificationItemComponent + StudentNotificationsComponent
- `NotificationItemComponent` — OnPush card with icon/heading/bodyText getters per event type; `role="listitem"`, `aria-label`
- `StudentNotificationsComponent` — loads from `sessionStorage` key `rct-notifications`, marks all items read on ngOnInit, resets badge via `badgeService.reset()`, subscribes to `onAnyEvent$` with `takeUntilDestroyed` for live STOMP events, MAX_ITEMS=100 cap, newest-first sort via `sortedItems` computed signal, "all-read-pill" and empty state with `ph-bell-slash`

### Task 2: StudentProfileComponent — identity card + password change form
- Identity card: radial-gradient background, 64×64px avatar with last 2 digits of userId, 'Студент' role badge, 'ID {userId}' muted, group ID if present, 'Web Cabinet' blue pill
- Password form: two `MatFormField` type=password with eye toggle suffix button
- Live validation hints: 3 chips (min 8 chars / one uppercase / one digit) — green `ph-check` when met, gray `ph-circle` when not
- Submit: `mat-flat-button` full width 48px height accent-primary pill
- 204 → 3s success banner → `form.reset()`
- 401 → inline error on currentPassword field 'Неверный текущий пароль.'
- Other errors → banner 'Не удалось изменить пароль. Попробуйте позже.'
- `ChangeDetectionStrategy.OnPush`, `autocomplete` attributes, `aria-live="assertive"` on error/success regions

## Key Files Created/Modified
- `frontends/web-panel/src/app/features/student/notifications/notification-item/notification-item.component.ts`
- `frontends/web-panel/src/app/features/student/notifications/student-notifications.component.ts`
- `frontends/web-panel/src/app/features/student/notifications/student-notifications.component.html`
- `frontends/web-panel/src/app/features/student/notifications/student-notifications.component.css`
- `frontends/web-panel/src/app/features/student/profile/student-profile.component.ts`
- `frontends/web-panel/src/app/features/student/profile/student-profile.component.html`
- `frontends/web-panel/src/app/features/student/profile/student-profile.component.css`

## Commits
- `989c5ee` feat(52-04): NotificationItemComponent + StudentNotificationsComponent
- `0df2439` feat(52-04): StudentProfileComponent — identity card + password change form

## Self-Check: PASSED
- All acceptance criteria met
- ng build completed without errors
- Profile component: OnPush, ReactiveFormsModule, hintMinLength/hintUppercase/hintDigit computed signals, wrongPassword error, changePassword(), setTimeout 3s
- Notifications component: rct-notifications key, MAX_ITEMS=100, badgeService.reset(), STORED_TYPES, takeUntilDestroyed
