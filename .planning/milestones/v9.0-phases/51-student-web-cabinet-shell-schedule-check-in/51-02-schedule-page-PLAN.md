---
phase: 51
plan: 02
type: execute
wave: 2
depends_on: [51-01]
files_modified:
  - frontends/web-panel/src/app/features/student/schedule/student-schedule.component.ts
  - frontends/web-panel/src/app/features/student/schedule/student-schedule.component.html
  - frontends/web-panel/src/app/features/student/schedule/student-schedule.component.css
  - frontends/web-panel/src/app/features/student/schedule/student-schedule.component.spec.ts
  - frontends/web-panel/src/app/features/student/schedule/lesson-row/lesson-row.component.ts
  - frontends/web-panel/src/app/features/student/schedule/lesson-row/lesson-row.component.html
  - frontends/web-panel/src/app/features/student/schedule/lesson-row/lesson-row.component.css
  - frontends/web-panel/src/app/features/student/schedule/lesson-row/lesson-row.component.spec.ts
  - frontends/web-panel/src/app/features/student/schedule/week-utils.ts
  - frontends/web-panel/src/app/features/student/schedule/week-utils.spec.ts
autonomous: true
requirements: [STU-WEB-02]

must_haves:
  truths:
    - "Visiting /student/schedule as a logged-in student renders a week nav strip (prev / week range label / next) + 6 day tabs (Пн-Сб) + a lesson list for the selected day"
    - "On first mount the selected day is today (or Пн if weekend), and the currentWeekStart is the Monday of today's week"
    - "Clicking the prev / next week buttons shifts currentWeekStart by ±7 days and fetches the new range; the week label updates"
    - "When the user is NOT on the current week, a floating 'Сегодня' pill appears; clicking it jumps back to today's week"
    - "Each lesson in the day shows start time, end time, subject name (resolved via SubjectCacheService), room, and a status chip using the existing global .status-chip--{present|absent|excused|free_attendance|cancelled} classes"
    - "Clicking a lesson row toggles an inline detail panel below it showing lesson number, lesson type (лекция/практика/лаб. работа derived from weekType+lessonNumber fallback), room, and teacher id (if present); clicking again collapses"
    - "If a day has no lessons a centered empty state with 'Занятий нет' heading is rendered"
    - "While lessons are loading, 4 skeleton rows appear in place of the list"
    - "If the lessons API errors, the page shows 'Не удалось загрузить расписание. Попробуйте позже.'"
    - "Day-tab change and route enter use @angular/animations triggers matching the UI-SPEC durations (150ms daySlide, 200ms routeFade)"
    - "The component does NOT fetch while auth.currentUser() is null or has no groupId (guards prevent this at runtime, but signal guard is idempotent)"
  artifacts:
    - path: "frontends/web-panel/src/app/features/student/schedule/student-schedule.component.ts"
      provides: "StudentScheduleComponent with full week/day state machine and signal-based rendering"
      exports: ["StudentScheduleComponent"]
    - path: "frontends/web-panel/src/app/features/student/schedule/lesson-row/lesson-row.component.ts"
      provides: "Presentational LessonRowComponent — inputs {lesson, expanded, subjectName} / output {toggle}"
      exports: ["LessonRowComponent"]
    - path: "frontends/web-panel/src/app/features/student/schedule/week-utils.ts"
      provides: "Pure date helpers: getMonday, addDays, formatDate, getTodayDayIndex, formatWeekRange, isSameWeek"
      exports: ["getMonday", "addDays", "formatDate", "getTodayDayIndex", "formatWeekRange", "isSameWeek"]
    - path: "frontends/web-panel/src/app/features/student/schedule/student-schedule.component.spec.ts"
      provides: "Unit test covering loading, empty state, error state, week navigation, day tab selection, expand toggle"
      contains: "StudentScheduleComponent"
  key_links:
    - from: "student-schedule.component.ts"
      to: "StudentApiService.getWeekLessons"
      via: "inject + subscribe in ngOnInit and on week change"
      pattern: "studentApi\\.getWeekLessons\\("
    - from: "student-schedule.component.ts"
      to: "SubjectCacheService.getName"
      via: "inject + async pipe in lesson-row"
      pattern: "subjectCache\\.getName"
    - from: "student-schedule.component.ts"
      to: "AuthService.currentUser"
      via: "inject + read groupId"
      pattern: "currentUser\\(\\)\\?.groupId|currentUser\\(\\)\\.groupId"
---

<objective>
Build the `/student/schedule` page as a full functional replacement for the empty-shell committed in Plan 01. Week-nav strip, 6-day tabs (Пн-Сб), lesson list with subway-rail visual language (time rail + station dot + subject/room/status), inline expand-on-click detail panel, loading/error/empty states, Angular animations for route-enter and day-slide transitions. Visual and functional parity with the PWA `SchedulePage.tsx`, translated idiomatically to Angular signals + standalone components + @angular/animations.

Purpose: Deliver STU-WEB-02 — the student sees their week with the same information the PWA exposes, on desktop, inside the existing web-panel shell.

Output: A working `/student/schedule` route that loads lessons, navigates weeks, expands lessons on click, and respects the UI-SPEC copy and tokens.
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@CLAUDE.md
@.planning/phases/51-student-web-cabinet-shell-schedule-check-in/51-UI-SPEC.md
@docs/design-decisions.md
@frontends/web-panel/src/app/features/student/shared/student-schedule.types.ts
@frontends/web-panel/src/app/features/student/shared/student-api.service.ts
@frontends/web-panel/src/app/features/student/shared/subject-cache.service.ts
@frontends/web-panel/src/app/features/student/schedule/student-schedule.component.ts
@frontends/web-panel/src/app/core/auth/auth.service.ts
@frontends/web-panel/src/styles.css
@frontends/web-panel/src/styles/tokens.css
@frontends/pwa/src/features/schedule/SchedulePage.tsx
@frontends/pwa/src/features/schedule/LessonCard.tsx
@frontends/pwa/src/features/schedule/WeekDayTabs.tsx
@frontends/pwa/src/features/schedule/StatusBadge.tsx
@frontends/pwa/src/features/schedule/types.ts
@frontends/web-panel/src/app/features/admin/dashboard/admin-dashboard.component.ts
@frontends/web-panel/src/app/features/admin/dashboard/admin-dashboard.component.html
@frontends/web-panel/src/app/features/admin/dashboard/admin-dashboard.component.spec.ts
@frontends/web-panel/src/app/features/teacher/journal/journal-page.component.spec.ts

<interfaces>
<!-- Contracts provided by Plan 01 (51-01). -->

From frontends/web-panel/src/app/features/student/shared/student-schedule.types.ts:
```typescript
export type LessonStatus = 'PLANNED' | 'ACTIVE' | 'CLOSED' | 'CANCELLED';
export interface LessonResponse {
  id: number; scheduleItemId: number; groupId: number; subjectId: number; teacherId: number;
  date: string; status: LessonStatus; dayOfWeek: number; lessonNumber: number;
  startTime: string; endTime: string; weekType: 'NUMERATOR' | 'DENOMINATOR' | 'BOTH';
  room: string; geoBlocked: boolean; cancelReason: string | null; createdAt: string;
}
```

From frontends/web-panel/src/app/features/student/shared/student-api.service.ts:
```typescript
@Injectable({ providedIn: 'root' })
export class StudentApiService {
  getWeekLessons(groupId: number, dateFrom: string, dateTo: string): Observable<LessonResponse[]>;
}
```

From frontends/web-panel/src/app/features/student/shared/subject-cache.service.ts:
```typescript
@Injectable({ providedIn: 'root' })
export class SubjectCacheService {
  getName(subjectId: number | null | undefined): Observable<string>;  // emits 'Предмет' fallback on null/err
}
```

From frontends/web-panel/src/app/core/auth/auth.service.ts (already shipped):
```typescript
readonly currentUser: Signal<AuthUser | null>; // AuthUser { id, role, isHeadman, groupId }
```

Global CSS classes (already in styles.css) usable directly in templates:
- `.page-stack`, `.page-header`, `.page-header__title`, `.page-card`
- `.status-chip`, `.status-chip--present`, `.status-chip--absent`, `.status-chip--excused`, `.status-chip--free_attendance`

CSS tokens (from tokens.css) referenced verbatim in UI-SPEC for this page:
- Colors: `--bg-primary`, `--bg-secondary`, `--bg-surface`, `--bg-elevated`, `--text-primary`, `--text-secondary`, `--text-muted`, `--accent-primary`, `--accent-secondary`, `--border-subtle`, `--border-default`, `--border-accent`
- Spacing: `--space-1..--space-8`
- Typography: `--font-display`, `--font-heading`, `--font-sans`, `--font-mono`, `--text-xs`, `--text-sm`, `--text-base`, `--text-2xl`, `--leading-heading`, `--leading-display`, `--tracking-wide`
- Radii: `--radius-sm`, `--radius-md`, `--radius-lg`, `--radius-xl`, `--radius-full`
- Effects: `--glow-primary`, `--gradient-brand`
- Motion: `--ease-out`, `--duration-fast` (150ms), `--duration-base` (200ms)
</interfaces>
</context>

<tasks>

<task type="auto" tdd="true">
  <name>Task 1: Pure date utilities + LessonRow presentational sub-component</name>
  <files>frontends/web-panel/src/app/features/student/schedule/week-utils.ts, frontends/web-panel/src/app/features/student/schedule/week-utils.spec.ts, frontends/web-panel/src/app/features/student/schedule/lesson-row/lesson-row.component.ts, frontends/web-panel/src/app/features/student/schedule/lesson-row/lesson-row.component.html, frontends/web-panel/src/app/features/student/schedule/lesson-row/lesson-row.component.css, frontends/web-panel/src/app/features/student/schedule/lesson-row/lesson-row.component.spec.ts</files>
  <read_first>
    - frontends/web-panel/src/app/features/student/shared/student-schedule.types.ts (LessonResponse shape)
    - frontends/pwa/src/features/schedule/SchedulePage.tsx lines 15-60 (exact MONTH_ABBREV array + getMonday + addDays + formatDate + getTodayDayIndex + formatWeekRange — replicate verbatim in TypeScript)
    - frontends/pwa/src/features/schedule/LessonCard.tsx (subway-rail visual — left time rail, station dot, right subject/room column)
    - .planning/phases/51-student-web-cabinet-shell-schedule-check-in/51-UI-SPEC.md lines 164-183 (StudentScheduleComponent layout table) and lines 220-235 (animation contract)
    - frontends/web-panel/src/styles.css lines 98-140 (.status-chip classes to reuse)
  </read_first>
  <behavior>
    # week-utils.spec.ts — MUST cover (each assertion is independent):
    - `getMonday(new Date('2026-04-09T12:00:00'))` (Thursday) returns Date for 2026-04-06 with time set to 00:00:00.000 local
    - `getMonday(new Date('2026-04-12T12:00:00'))` (Sunday) returns Date for 2026-04-06 (NOT 2026-04-13 — ISO week Monday)
    - `addDays(new Date('2026-04-06'), 5)` returns Date for 2026-04-11
    - `formatDate(new Date('2026-04-06'))` returns the string `'2026-04-06'` (zero-padded month and day)
    - `formatWeekRange(new Date('2026-04-06'))` returns `'6-11 апр'` (same month)
    - `formatWeekRange(new Date('2026-03-30'))` returns `'30 мар - 4 апр'` (across months)
    - `getTodayDayIndex()` when `Date.now()` is stubbed to a Thursday returns 3 (Mon=0, Thu=3)
    - `getTodayDayIndex()` when Date.now() returns Sunday returns 5 (Sb, because the view only shows Пн-Сб)
    - `isSameWeek(monday, addDays(monday, 3))` returns true; `isSameWeek(monday, addDays(monday, 8))` returns false

    # lesson-row.component.spec.ts — MUST cover:
    - Rendering with a PLANNED lesson shows the subject name, formatted start and end time (HH:mm), room, and a `.status-chip--` class matching the status
    - Rendering with an ACTIVE lesson applies a class `lesson-row--active` (or an attribute / style) and the station dot element exists
    - Rendering with a CANCELLED lesson applies a `lesson-row--cancelled` class AND the subject name has `text-decoration: line-through` (via class check, not computed style)
    - Clicking the row emits the `toggle` output with the lesson id
    - When `expanded` input is true, the inline detail panel containing the lesson number and room is rendered; when false the panel is not in the DOM
    - `aria-expanded` attribute on the row button reflects the `expanded` input
    - The subject name passed as `subjectName` input is rendered verbatim (no fetch — this is a pure presentational component)
  </behavior>
  <action>
Step 1 — create `frontends/web-panel/src/app/features/student/schedule/week-utils.ts` with pure date helpers (no Angular imports). Copy logic verbatim from `frontends/pwa/src/features/schedule/SchedulePage.tsx` lines 15-60:

```typescript
export const MONTH_ABBREV = ['янв', 'фев', 'мар', 'апр', 'мая', 'июн', 'июл', 'авг', 'сен', 'окт', 'ноя', 'дек'] as const;

export function getMonday(date: Date): Date {
  const d = new Date(date);
  const day = d.getDay();                // 0=Sun..6=Sat
  const diff = day === 0 ? -6 : 1 - day; // shift to ISO Monday
  d.setDate(d.getDate() + diff);
  d.setHours(0, 0, 0, 0);
  return d;
}

export function addDays(date: Date, days: number): Date {
  const d = new Date(date);
  d.setDate(d.getDate() + days);
  return d;
}

export function formatDate(date: Date): string {
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, '0');
  const d = String(date.getDate()).padStart(2, '0');
  return `${y}-${m}-${d}`;
}

export function getTodayDayIndex(now: Date = new Date()): number {
  const dow = now.getDay();
  if (dow === 0) return 5;   // Sun -> Sb
  return dow - 1;            // Mon=0..Sat=5
}

export function formatWeekRange(monday: Date): string {
  const saturday = addDays(monday, 5);
  const startDay = monday.getDate();
  const endDay = saturday.getDate();
  if (monday.getMonth() === saturday.getMonth()) {
    return `${startDay}-${endDay} ${MONTH_ABBREV[saturday.getMonth()]}`;
  }
  return `${startDay} ${MONTH_ABBREV[monday.getMonth()]} - ${endDay} ${MONTH_ABBREV[saturday.getMonth()]}`;
}

export function isSameWeek(a: Date, b: Date): boolean {
  return getMonday(a).getTime() === getMonday(b).getTime();
}

export function formatLessonTime(time: string): string {
  return time.slice(0, 5); // 'HH:mm:ss' -> 'HH:mm'
}
```

Step 2 — write `week-utils.spec.ts` using vitest. Use `vi.useFakeTimers(); vi.setSystemTime(new Date('2026-04-09T12:00:00'))` to freeze time for the `getTodayDayIndex()` test. Cover every case in the behavior block. Example:

```typescript
import { describe, it, expect } from 'vitest';
import { getMonday, addDays, formatDate, formatWeekRange, getTodayDayIndex, isSameWeek } from './week-utils';

describe('week-utils', () => {
  it('getMonday returns Monday of the week containing a Thursday', () => {
    const thu = new Date(2026, 3, 9); // Apr 9 2026 is Thursday
    const mon = getMonday(thu);
    expect(mon.getFullYear()).toBe(2026);
    expect(mon.getMonth()).toBe(3);  // April
    expect(mon.getDate()).toBe(6);
    expect(mon.getHours()).toBe(0);
  });
  // ...other cases from the behavior block
});
```

Step 3 — create the LessonRow sub-component at `frontends/web-panel/src/app/features/student/schedule/lesson-row/lesson-row.component.ts`:

```typescript
import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output } from '@angular/core';
import { NgClass } from '@angular/common';
import type { LessonResponse, LessonStatus, AttendanceStatus } from '../../shared/student-schedule.types';
import { formatLessonTime } from '../week-utils';

/**
 * Presentational lesson row — subway-rail layout per UI-SPEC §Component 3.
 * Left rail: startTime / station dot / endTime.
 * Right: subject heading + room meta + status chip.
 * Click emits `toggle` with the lesson id; the parent drives the expanded panel.
 */
@Component({
  selector: 'app-lesson-row',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [NgClass],
  templateUrl: './lesson-row.component.html',
  styleUrl: './lesson-row.component.css',
})
export class LessonRowComponent {
  @Input({ required: true }) lesson!: LessonResponse;
  @Input() subjectName: string = 'Предмет';
  @Input() personalStatus: AttendanceStatus | null = null;
  @Input() expanded = false;
  @Output() toggle = new EventEmitter<number>();

  get isActive(): boolean { return this.lesson.status === 'ACTIVE'; }
  get isCancelled(): boolean { return this.lesson.status === 'CANCELLED'; }

  get startLabel(): string { return formatLessonTime(this.lesson.startTime); }
  get endLabel(): string { return formatLessonTime(this.lesson.endTime); }

  get statusChipClass(): string {
    if (this.personalStatus) return `status-chip status-chip--${this.personalStatus}`;
    if (this.isActive) return 'status-chip status-chip--active';
    if (this.isCancelled) return 'status-chip status-chip--cancelled';
    return 'status-chip';
  }

  get statusLabel(): string {
    if (this.personalStatus === 'present') return 'б';
    if (this.personalStatus === 'absent') return 'н';
    if (this.personalStatus === 'excused') return 'у';
    if (this.personalStatus === 'free_attendance') return 'сп';
    if (this.isActive) return 'Идёт';
    if (this.isCancelled) return 'Отменена';
    return 'Пара';
  }

  get lessonTypeLabel(): string {
    // Simple deterministic label — backend doesn't expose lesson type in LessonResponse
    // (Phase 52 will extend). For Phase 51 fall back to lessonNumber-based hint.
    return `Пара №${this.lesson.lessonNumber}`;
  }

  onClick(): void {
    if (this.isCancelled) return; // Cancelled lessons are not expandable
    this.toggle.emit(this.lesson.id);
  }
}
```

Step 4 — write `lesson-row.component.html`. Use the subway-rail structure from `LessonCard.tsx`:

```html
<button
  type="button"
  class="lesson-row"
  [ngClass]="{
    'lesson-row--active': isActive,
    'lesson-row--cancelled': isCancelled,
    'lesson-row--expanded': expanded
  }"
  [attr.aria-expanded]="expanded"
  [attr.data-lesson-id]="lesson.id"
  (click)="onClick()"
>
  <div class="lesson-row__rail" aria-hidden="true">
    <span class="lesson-row__time lesson-row__time--start">{{ startLabel }}</span>
    <span class="lesson-row__dot"></span>
    <span class="lesson-row__time lesson-row__time--end">{{ endLabel }}</span>
  </div>

  <div class="lesson-row__body">
    <div class="lesson-row__head">
      <h3 class="lesson-row__subject">{{ subjectName }}</h3>
      <span [class]="statusChipClass">{{ statusLabel }}</span>
    </div>
    <p class="lesson-row__meta">
      <i class="ph ph-map-pin" aria-hidden="true"></i>
      <span>Ауд. {{ lesson.room }}</span>
    </p>
  </div>
</button>

@if (expanded) {
  <div class="lesson-row__detail" [attr.id]="'lesson-detail-' + lesson.id" role="region">
    <dl class="lesson-row__detail-list">
      <div><dt>Номер пары</dt><dd>{{ lesson.lessonNumber }}</dd></div>
      <div><dt>Аудитория</dt><dd>{{ lesson.room }}</dd></div>
      <div><dt>Длительность</dt><dd>{{ startLabel }} — {{ endLabel }}</dd></div>
      @if (lesson.teacherId) {
        <div><dt>Преподаватель</dt><dd>ID {{ lesson.teacherId }}</dd></div>
      }
      @if (lesson.cancelReason) {
        <div><dt>Причина отмены</dt><dd>{{ lesson.cancelReason }}</dd></div>
      }
    </dl>
  </div>
}
```

Step 5 — write `lesson-row.component.css` using the tokens from the UI-SPEC (values copied literally — NO hex values):

```css
:host { display: block; }

.lesson-row {
  display: flex;
  gap: var(--space-4);
  width: 100%;
  padding: var(--space-4);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  background: var(--bg-secondary);
  color: var(--text-primary);
  text-align: left;
  cursor: pointer;
  transition:
    border-color var(--duration-base) var(--ease-out),
    box-shadow var(--duration-base) var(--ease-out),
    transform var(--duration-base) var(--ease-out);
}
.lesson-row:hover:not(.lesson-row--cancelled) {
  border-color: var(--border-default);
}
.lesson-row:focus-visible {
  outline: 2px solid var(--accent-primary);
  outline-offset: 2px;
}
.lesson-row--active {
  border-color: var(--border-accent);
  box-shadow: var(--glow-primary);
}
.lesson-row--cancelled {
  opacity: 0.55;
  cursor: default;
}
.lesson-row--cancelled .lesson-row__subject {
  text-decoration: line-through;
}

.lesson-row__rail {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--space-2);
  flex-shrink: 0;
  min-width: 48px;
}
.lesson-row__time {
  font-family: var(--font-mono);
  font-size: var(--text-sm);
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}
.lesson-row__time--end { color: var(--text-muted); font-weight: 400; }

.lesson-row__dot {
  width: 12px;
  height: 12px;
  border-radius: 50%;
  background: var(--text-muted);
}
.lesson-row--active .lesson-row__dot {
  background: var(--accent-primary);
  box-shadow: 0 0 12px var(--accent-primary);
}

.lesson-row__body { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: var(--space-1); }
.lesson-row__head { display: flex; align-items: flex-start; justify-content: space-between; gap: var(--space-3); }
.lesson-row__subject {
  margin: 0;
  font-family: var(--font-heading);
  font-size: var(--text-base);
  font-weight: 600;
  line-height: var(--leading-heading);
  color: var(--text-primary);
}
.lesson-row__meta {
  margin: 0;
  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
  font-size: var(--text-xs);
  color: var(--text-secondary);
}
.lesson-row__meta i { font-size: 14px; }

.lesson-row__detail {
  margin-top: var(--space-2);
  padding: var(--space-4);
  border-radius: var(--radius-md);
  background: var(--bg-elevated);
  border: 1px solid var(--border-subtle);
}
.lesson-row__detail-list { margin: 0; display: flex; flex-direction: column; gap: var(--space-2); }
.lesson-row__detail-list div { display: flex; justify-content: space-between; font-size: var(--text-xs); }
.lesson-row__detail-list dt { color: var(--text-muted); text-transform: uppercase; letter-spacing: var(--tracking-wide); }
.lesson-row__detail-list dd { margin: 0; color: var(--text-primary); font-variant-numeric: tabular-nums; }

.status-chip--active {
  background: color-mix(in srgb, var(--accent-primary) 14%, transparent);
  color: var(--accent-primary);
  padding: 2px 8px;
  border-radius: var(--radius-full);
  font-size: var(--text-xs);
  font-weight: 600;
  line-height: 1.4;
  width: auto;
  height: auto;
}
.status-chip--cancelled {
  background: color-mix(in srgb, var(--text-muted) 14%, transparent);
  color: var(--text-muted);
  padding: 2px 8px;
  border-radius: var(--radius-full);
  font-size: var(--text-xs);
  font-weight: 600;
  line-height: 1.4;
  width: auto;
  height: auto;
}

@media (prefers-reduced-motion: reduce) {
  .lesson-row { transition: none; }
}
```

Step 6 — write `lesson-row.component.spec.ts` using @testing-library/angular. Follow the pattern in `frontends/web-panel/src/app/features/admin/dashboard/admin-dashboard.component.spec.ts` for TestBed setup. Example shell:

```typescript
import { render, screen } from '@testing-library/angular';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi } from 'vitest';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { LessonRowComponent } from './lesson-row.component';
import type { LessonResponse } from '../../shared/student-schedule.types';

const baseLesson: LessonResponse = {
  id: 42, scheduleItemId: 1, groupId: 5, subjectId: 7, teacherId: 11,
  date: '2026-04-09', status: 'PLANNED', dayOfWeek: 4, lessonNumber: 2,
  startTime: '10:00:00', endTime: '11:30:00',
  weekType: 'BOTH', room: '404', geoBlocked: false, cancelReason: null,
  createdAt: '2026-04-01T00:00:00Z',
};

describe('LessonRowComponent', () => {
  it('renders subject name, start/end time, room and a status chip', async () => {
    await render(LessonRowComponent, {
      providers: [provideNoopAnimations()],
      componentInputs: { lesson: baseLesson, subjectName: 'Математика' },
    });
    expect(screen.getByText('Математика')).toBeTruthy();
    expect(screen.getByText('10:00')).toBeTruthy();
    expect(screen.getByText('11:30')).toBeTruthy();
    expect(screen.getByText(/Ауд.\s*404/)).toBeTruthy();
  });

  it('emits toggle with lesson id on click', async () => {
    const onToggle = vi.fn();
    await render(LessonRowComponent, {
      providers: [provideNoopAnimations()],
      componentInputs: { lesson: baseLesson, subjectName: 'Математика' },
      componentOutputs: { toggle: { emit: onToggle } as any },
    });
    const user = userEvent.setup();
    await user.click(screen.getByRole('button'));
    expect(onToggle).toHaveBeenCalledWith(42);
  });

  it('renders detail panel when expanded = true', async () => {
    await render(LessonRowComponent, {
      providers: [provideNoopAnimations()],
      componentInputs: { lesson: baseLesson, subjectName: 'Математика', expanded: true },
    });
    expect(screen.getByText('Номер пары')).toBeTruthy();
  });

  it('does not emit toggle for CANCELLED lessons', async () => {
    const onToggle = vi.fn();
    await render(LessonRowComponent, {
      providers: [provideNoopAnimations()],
      componentInputs: { lesson: { ...baseLesson, status: 'CANCELLED' }, subjectName: 'Математика' },
      componentOutputs: { toggle: { emit: onToggle } as any },
    });
    await userEvent.setup().click(screen.getByRole('button'));
    expect(onToggle).not.toHaveBeenCalled();
  });

  it('applies lesson-row--active class for ACTIVE lessons', async () => {
    const { container } = await render(LessonRowComponent, {
      providers: [provideNoopAnimations()],
      componentInputs: { lesson: { ...baseLesson, status: 'ACTIVE' }, subjectName: 'Математика' },
    });
    expect(container.querySelector('.lesson-row--active')).toBeTruthy();
  });
});
```

If `componentOutputs` binding with `.emit` stub is not supported by the installed @testing-library/angular version, substitute with `vi.spyOn(component.toggle, 'emit')` pattern by resolving the fixture's `componentInstance`.

Run specs:

```bash
cd frontends/web-panel && npm test -- --run src/app/features/student/schedule/lesson-row src/app/features/student/schedule/week-utils
```

Must exit 0.
  </action>
  <verify>
    <automated>cd frontends/web-panel && npm test -- --run src/app/features/student/schedule/lesson-row src/app/features/student/schedule/week-utils 2>&1 | tail -30</automated>
  </verify>
  <acceptance_criteria>
    - frontends/web-panel/src/app/features/student/schedule/week-utils.ts exists and exports all of: `getMonday`, `addDays`, `formatDate`, `getTodayDayIndex`, `formatWeekRange`, `isSameWeek`, `formatLessonTime`, `MONTH_ABBREV`
    - frontends/web-panel/src/app/features/student/schedule/lesson-row/lesson-row.component.ts contains `export class LessonRowComponent` AND `standalone: true` AND `selector: 'app-lesson-row'` AND `@Input({ required: true }) lesson`
    - lesson-row.component.html contains the literals `Ауд.`, `aria-expanded`, `lesson-row__rail`, `lesson-row__subject`, `lesson-row__detail`
    - lesson-row.component.css contains `var(--accent-primary)` AND `var(--border-accent)` AND `var(--glow-primary)` AND NO hex color values (grep `#[0-9a-fA-F]{3,8}` returns zero matches)
    - lesson-row.component.css contains `prefers-reduced-motion`
    - week-utils.spec.ts and lesson-row.component.spec.ts both run green via `cd frontends/web-panel && npm test -- --run src/app/features/student/schedule/lesson-row src/app/features/student/schedule/week-utils`
  </acceptance_criteria>
  <done>
Pure date helpers are unit-tested; LessonRowComponent renders subway-rail layout, status chips, and expanding detail panel; clicking the row emits `toggle` (and does NOT emit for cancelled lessons); all specs pass.
  </done>
</task>

<task type="auto" tdd="true">
  <name>Task 2: StudentScheduleComponent — week nav, day tabs, lesson list, animations, tests</name>
  <files>frontends/web-panel/src/app/features/student/schedule/student-schedule.component.ts, frontends/web-panel/src/app/features/student/schedule/student-schedule.component.html, frontends/web-panel/src/app/features/student/schedule/student-schedule.component.css, frontends/web-panel/src/app/features/student/schedule/student-schedule.component.spec.ts</files>
  <read_first>
    - frontends/web-panel/src/app/features/student/schedule/student-schedule.component.ts (the empty shell committed in Plan 01)
    - frontends/web-panel/src/app/features/student/shared/student-api.service.ts + student-schedule.types.ts
    - frontends/web-panel/src/app/features/student/shared/subject-cache.service.ts
    - frontends/web-panel/src/app/features/student/schedule/week-utils.ts (just committed in Task 1)
    - frontends/web-panel/src/app/features/student/schedule/lesson-row/lesson-row.component.ts (just committed in Task 1)
    - frontends/web-panel/src/app/core/auth/auth.service.ts (currentUser signal usage)
    - frontends/pwa/src/features/schedule/SchedulePage.tsx (state machine reference — weekStart, selectedDayIndex, week navigation, today pill)
    - .planning/phases/51-student-web-cabinet-shell-schedule-check-in/51-UI-SPEC.md lines 164-183 (layout), 220-235 (animations), 258-272 (schedule copy)
    - frontends/web-panel/src/app/features/admin/dashboard/admin-dashboard.component.ts (signal + ngOnInit + HttpClient pattern reference)
    - frontends/web-panel/src/app/features/admin/dashboard/admin-dashboard.component.spec.ts (HttpTestingController pattern — use this pattern for the component spec)
  </read_first>
  <behavior>
    # student-schedule.component.spec.ts — MUST cover (using provideHttpClient + provideHttpClientTesting, provideRouter([]), provideNoopAnimations(), and a fake AuthService with currentUser returning {id: 3, role: 'STUDENT', isHeadman: false, groupId: 5}):
    - On init, the component issues `GET /api/schedule/groups/5/lessons` with params `dateFrom=<Monday of today>`, `dateTo=<Saturday of today>`, `size=100`
    - While the HTTP request is pending, the template renders at least 4 skeleton row elements (assert `container.querySelectorAll('.schedule-skeleton').length >= 4`)
    - After the HTTP response is flushed with a lessons array, the skeletons disappear and lesson rows appear
    - Clicking the "next week" button issues a second HTTP call with `dateFrom` shifted +7 days; the week label updates
    - When `isCurrentWeek() === false`, a "Сегодня" pill is rendered; clicking it issues another HTTP call for the current week and the pill disappears
    - When the HTTP response is an empty array, the template renders the literal text "Занятий нет" (empty state)
    - When the HTTP response is a 500 error, the template renders the literal text "Не удалось загрузить расписание. Попробуйте позже."
    - Clicking a day tab updates the selected day; the lesson list rerenders to show only that day's lessons (use a fixture with lessons on days 1 and 3, assert that switching to day index 0 shows only day-1 lessons)
    - Clicking a lesson row sets the expandedLessonId signal; clicking the same lesson again clears it (toggle behavior)
  </behavior>
  <action>
Step 1 — replace the empty shell at `frontends/web-panel/src/app/features/student/schedule/student-schedule.component.ts` with the full component. Use signals + computed + inject:

```typescript
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  OnDestroy,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import { AsyncPipe, NgClass } from '@angular/common';
import {
  trigger, transition, style, animate,
} from '@angular/animations';
import { AuthService } from '../../../core/auth/auth.service';
import { StudentApiService } from '../shared/student-api.service';
import { SubjectCacheService } from '../shared/subject-cache.service';
import type { LessonResponse } from '../shared/student-schedule.types';
import { LessonRowComponent } from './lesson-row/lesson-row.component';
import {
  getMonday, addDays, formatDate, formatWeekRange, getTodayDayIndex, isSameWeek,
} from './week-utils';

const DAY_LABELS = ['Пн', 'Вт', 'Ср', 'Чт', 'Пт', 'Сб'] as const;

@Component({
  selector: 'app-student-schedule',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [AsyncPipe, NgClass, LessonRowComponent],
  templateUrl: './student-schedule.component.html',
  styleUrl: './student-schedule.component.css',
  animations: [
    trigger('routeFade', [
      transition(':enter', [
        style({ opacity: 0, transform: 'translateY(8px)' }),
        animate('200ms cubic-bezier(0.16, 1, 0.3, 1)', style({ opacity: 1, transform: 'translateY(0)' })),
      ]),
    ]),
    trigger('daySlide', [
      transition(':enter', [
        style({ opacity: 0, transform: 'translateX(8px)' }),
        animate('150ms cubic-bezier(0.16, 1, 0.3, 1)', style({ opacity: 1, transform: 'translateX(0)' })),
      ]),
    ]),
  ],
})
export class StudentScheduleComponent implements OnInit, OnDestroy {
  private readonly studentApi = inject(StudentApiService);
  private readonly subjectCache = inject(SubjectCacheService);
  private readonly auth = inject(AuthService);
  private readonly destroyRef = inject(DestroyRef);

  readonly dayLabels = DAY_LABELS;

  /** Current Monday (week start). */
  readonly currentWeekStart = signal<Date>(getMonday(new Date()));
  /** Selected day index: 0=Mon..5=Sat. */
  readonly selectedDayIndex = signal<number>(getTodayDayIndex());
  readonly lessons = signal<LessonResponse[]>([]);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly expandedLessonId = signal<number | null>(null);

  readonly weekLabel = computed(() => formatWeekRange(this.currentWeekStart()));
  readonly isCurrentWeek = computed(() => isSameWeek(this.currentWeekStart(), new Date()));

  readonly dayLessons = computed(() => {
    const selected = this.selectedDayIndex();
    const backendDow = selected + 1; // Mon=1
    return this.lessons()
      .filter(l => l.dayOfWeek === backendDow)
      .sort((a, b) => a.lessonNumber - b.lessonNumber);
  });

  ngOnInit(): void {
    this.loadWeek();
  }

  ngOnDestroy(): void {
    // RxJS subscriptions go through destroyRef takeUntilDestroyed pattern below
  }

  loadWeek(): void {
    const user = this.auth.currentUser();
    const groupId = user?.groupId;
    if (!groupId) {
      this.error.set('Не удалось определить группу пользователя.');
      return;
    }
    const monday = this.currentWeekStart();
    const saturday = addDays(monday, 5);
    this.loading.set(true);
    this.error.set(null);
    this.studentApi.getWeekLessons(groupId, formatDate(monday), formatDate(saturday)).subscribe({
      next: (lessons) => {
        this.lessons.set(lessons);
        this.loading.set(false);
      },
      error: () => {
        this.lessons.set([]);
        this.error.set('Не удалось загрузить расписание. Попробуйте позже.');
        this.loading.set(false);
      },
    });
  }

  prevWeek(): void {
    this.currentWeekStart.update(d => addDays(d, -7));
    this.expandedLessonId.set(null);
    this.loadWeek();
  }

  nextWeek(): void {
    this.currentWeekStart.update(d => addDays(d, 7));
    this.expandedLessonId.set(null);
    this.loadWeek();
  }

  jumpToToday(): void {
    this.currentWeekStart.set(getMonday(new Date()));
    this.selectedDayIndex.set(getTodayDayIndex());
    this.expandedLessonId.set(null);
    this.loadWeek();
  }

  selectDay(index: number): void {
    this.selectedDayIndex.set(index);
    this.expandedLessonId.set(null);
  }

  toggleLesson(lessonId: number): void {
    this.expandedLessonId.update(cur => (cur === lessonId ? null : lessonId));
  }

  getSubjectName$(subjectId: number) {
    return this.subjectCache.getName(subjectId);
  }
}
```

Step 2 — write `student-schedule.component.html`. Use the `.page-stack`, `.page-header` global primitives. All copy is from UI-SPEC lines 258-272:

```html
<section class="page-stack schedule" [@routeFade]>
  <header class="page-header">
    <div class="page-header__title">
      <h2>Расписание</h2>
      <p class="page-header__subtitle">Неделя с занятиями вашей группы</p>
    </div>
  </header>

  <!-- Week nav strip -->
  <nav class="schedule__weeknav" aria-label="Навигация по неделям">
    <button
      type="button"
      class="schedule__weeknav-btn"
      (click)="prevWeek()"
      aria-label="Предыдущая неделя"
    >
      <i class="ph ph-caret-left" aria-hidden="true"></i>
    </button>
    <div class="schedule__weeknav-label">
      <span class="schedule__weeknav-eyebrow">Неделя</span>
      <span class="schedule__weeknav-range">{{ weekLabel() }}</span>
    </div>
    <button
      type="button"
      class="schedule__weeknav-btn"
      (click)="nextWeek()"
      aria-label="Следующая неделя"
    >
      <i class="ph ph-caret-right" aria-hidden="true"></i>
    </button>
  </nav>

  <!-- Day tabs -->
  <div class="schedule__days" role="tablist" aria-label="День недели">
    @for (label of dayLabels; track label; let i = $index) {
      <button
        type="button"
        role="tab"
        [attr.aria-selected]="selectedDayIndex() === i"
        [class.schedule__day--selected]="selectedDayIndex() === i"
        class="schedule__day"
        (click)="selectDay(i)"
      >
        {{ label }}
      </button>
    }
  </div>

  <!-- Lesson list -->
  <div class="schedule__list" [attr.aria-busy]="loading() ? 'true' : null">
    @if (loading()) {
      <div class="schedule-skeleton" aria-hidden="true"></div>
      <div class="schedule-skeleton" aria-hidden="true"></div>
      <div class="schedule-skeleton" aria-hidden="true"></div>
      <div class="schedule-skeleton" aria-hidden="true"></div>
    } @else if (error()) {
      <div class="schedule__error" role="alert">
        <i class="ph ph-warning-circle" aria-hidden="true"></i>
        {{ error() }}
      </div>
    } @else if (dayLessons().length === 0) {
      <div class="schedule__empty">
        <div class="schedule__empty-icon" aria-hidden="true">
          <i class="ph-duotone ph-calendar-blank"></i>
        </div>
        <h3 class="schedule__empty-title">Занятий нет</h3>
        <p class="schedule__empty-text">В этот день пар не запланировано.</p>
      </div>
    } @else {
      <div [@daySlide]="selectedDayIndex()" class="schedule__day-list">
        @for (lesson of dayLessons(); track lesson.id) {
          <app-lesson-row
            [lesson]="lesson"
            [subjectName]="(getSubjectName$(lesson.subjectId) | async) ?? 'Предмет'"
            [expanded]="expandedLessonId() === lesson.id"
            (toggle)="toggleLesson($event)"
          />
        }
      </div>
    }
  </div>

  <!-- "Сегодня" floating pill -->
  @if (!isCurrentWeek()) {
    <button type="button" class="schedule__today-pill" (click)="jumpToToday()">
      <i class="ph-fill ph-calendar-blank" aria-hidden="true"></i>
      Сегодня
    </button>
  }
</section>
```

Step 3 — write `student-schedule.component.css`. Every value is a token — NO hex literals, no Tailwind utilities:

```css
:host { display: block; }

.schedule { }

.schedule__weeknav {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--space-2) var(--space-4);
  background: var(--bg-surface);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
}
.schedule__weeknav-btn {
  width: 44px;
  height: 44px;
  border-radius: var(--radius-full);
  border: 0;
  background: transparent;
  color: var(--text-secondary);
  cursor: pointer;
  display: grid;
  place-items: center;
  transition: background var(--duration-fast) var(--ease-out);
}
.schedule__weeknav-btn:hover { background: var(--bg-elevated); }
.schedule__weeknav-btn:focus-visible { outline: 2px solid var(--accent-primary); outline-offset: 2px; }
.schedule__weeknav-btn i { font-size: 20px; }
.schedule__weeknav-label { display: flex; flex-direction: column; align-items: center; gap: 2px; }
.schedule__weeknav-eyebrow {
  font-size: var(--text-xs);
  font-weight: 600;
  color: var(--text-muted);
  text-transform: uppercase;
  letter-spacing: var(--tracking-wide);
}
.schedule__weeknav-range {
  font-size: var(--text-sm);
  font-weight: 600;
  color: var(--text-primary);
  font-variant-numeric: tabular-nums;
}

.schedule__days {
  display: flex;
  gap: var(--space-2);
  border-bottom: 1px solid var(--border-subtle);
  padding-bottom: var(--space-2);
}
.schedule__day {
  flex: 1;
  min-height: 44px;
  padding: var(--space-2) var(--space-3);
  border: 0;
  background: transparent;
  cursor: pointer;
  font-family: var(--font-sans);
  font-size: var(--text-sm);
  font-weight: 400;
  color: var(--text-muted);
  border-radius: var(--radius-md);
  transition: color var(--duration-fast) var(--ease-out), background var(--duration-fast) var(--ease-out);
}
.schedule__day:hover { background: var(--bg-secondary); color: var(--text-secondary); }
.schedule__day--selected {
  color: var(--text-primary);
  font-weight: 600;
  border-bottom: 2px solid var(--accent-primary);
  border-radius: var(--radius-md) var(--radius-md) 0 0;
}
.schedule__day:focus-visible { outline: 2px solid var(--accent-primary); outline-offset: 2px; }

.schedule__list { display: flex; flex-direction: column; gap: var(--space-3); min-height: 200px; }
.schedule__day-list { display: flex; flex-direction: column; gap: var(--space-3); }

.schedule-skeleton {
  height: 80px;
  border-radius: var(--radius-lg);
  background: linear-gradient(
    90deg,
    var(--bg-elevated) 25%,
    color-mix(in oklab, var(--text-primary) 8%, transparent) 50%,
    var(--bg-elevated) 75%
  );
  background-size: 200% 100%;
  animation: schedule-shimmer 1.5s linear infinite;
}
@keyframes schedule-shimmer {
  0% { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}

.schedule__empty {
  padding: var(--space-7) var(--space-5);
  border: 1px dashed var(--border-default);
  border-radius: var(--radius-lg);
  background: color-mix(in oklab, var(--bg-secondary) 50%, transparent);
  text-align: center;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--space-3);
}
.schedule__empty-icon {
  width: 64px;
  height: 64px;
  border-radius: var(--radius-full);
  background: color-mix(in oklab, var(--accent-primary) 12%, transparent);
  border: 1px solid var(--border-accent);
  color: var(--accent-primary);
  display: grid;
  place-items: center;
}
.schedule__empty-icon i { font-size: 32px; }
.schedule__empty-title {
  margin: 0;
  font-family: var(--font-display);
  font-size: var(--text-2xl);
  font-weight: 600;
  color: var(--text-primary);
}
.schedule__empty-text {
  margin: 0;
  font-size: var(--text-sm);
  color: var(--text-secondary);
  max-width: 44ch;
}

.schedule__error {
  padding: var(--space-4);
  border-radius: var(--radius-md);
  background: color-mix(in srgb, var(--accent-danger) 12%, transparent);
  border: 1px solid color-mix(in srgb, var(--accent-danger) 28%, transparent);
  color: var(--accent-danger);
  font-size: var(--text-sm);
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
}

.schedule__today-pill {
  position: fixed;
  left: 50%;
  bottom: var(--space-6);
  transform: translateX(-50%);
  padding: 10px 20px;
  border-radius: var(--radius-full);
  border: 0;
  background: var(--gradient-brand);
  color: var(--accent-primary-contrast);
  font-family: var(--font-heading);
  font-size: var(--text-sm);
  font-weight: 600;
  box-shadow: var(--glow-primary);
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  z-index: var(--z-sticky);
}
.schedule__today-pill:hover { filter: brightness(1.08); }
.schedule__today-pill:active { transform: translateX(-50%) scale(0.97); }
.schedule__today-pill i { font-size: 16px; }

@media (prefers-reduced-motion: reduce) {
  .schedule-skeleton { animation: none; }
}
```

Step 4 — write `student-schedule.component.spec.ts`. Use the admin-dashboard.component.spec.ts pattern with `provideHttpClient` + `provideHttpClientTesting` + `provideRouter([])` + a fake `AuthService`:

```typescript
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { signal, computed } from '@angular/core';
import { render, screen, within } from '@testing-library/angular';
import userEvent from '@testing-library/user-event';
import { StudentScheduleComponent } from './student-schedule.component';
import { AuthService, AuthUser } from '../../../core/auth/auth.service';
import type { LessonResponse } from '../shared/student-schedule.types';

const STUDENT_USER: AuthUser = { id: 3, role: 'STUDENT', isHeadman: false, groupId: 5 };

function makeAuthMock(user: AuthUser | null) {
  return {
    currentUser: computed(() => user),
    isAuthenticated: computed(() => user !== null),
    accessToken: signal<string | null>(user ? 'fake-token' : null).asReadonly(),
    resolveDashboardFor: vi.fn(),
  };
}

function mkLesson(over: Partial<LessonResponse>): LessonResponse {
  return {
    id: 1, scheduleItemId: 1, groupId: 5, subjectId: 7, teacherId: 11,
    date: '2026-04-09', status: 'PLANNED', dayOfWeek: 4, lessonNumber: 1,
    startTime: '09:00:00', endTime: '10:30:00',
    weekType: 'BOTH', room: '404', geoBlocked: false, cancelReason: null,
    createdAt: '2026-04-01T00:00:00Z',
    ...over,
  };
}

describe('StudentScheduleComponent', () => {
  let httpMock: HttpTestingController;

  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date(2026, 3, 9, 12, 0, 0)); // Thu Apr 9 2026 12:00 local
  });
  afterEach(() => {
    vi.useRealTimers();
  });

  async function setup(user: AuthUser | null = STUDENT_USER) {
    const { fixture } = await render(StudentScheduleComponent, {
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        provideNoopAnimations(),
        { provide: AuthService, useValue: makeAuthMock(user) },
      ],
    });
    httpMock = TestBed.inject(HttpTestingController);
    return fixture;
  }

  it('fetches lessons for the current Monday-Saturday range on init', async () => {
    await setup();
    const req = httpMock.expectOne(r => r.url === '/api/schedule/groups/5/lessons');
    expect(req.request.params.get('dateFrom')).toBe('2026-04-06');
    expect(req.request.params.get('dateTo')).toBe('2026-04-11');
    req.flush({ _embedded: { lessonResponseList: [] } });
  });

  it('shows 4 skeleton rows while loading', async () => {
    const { nativeElement } = await setup();
    expect(nativeElement.querySelectorAll('.schedule-skeleton').length).toBe(4);
    const req = httpMock.expectOne(r => r.url === '/api/schedule/groups/5/lessons');
    req.flush({ _embedded: { lessonResponseList: [] } });
  });

  it('renders "Занятий нет" empty state on zero lessons', async () => {
    await setup();
    const req = httpMock.expectOne(r => r.url === '/api/schedule/groups/5/lessons');
    req.flush({ _embedded: { lessonResponseList: [] } });
    expect(screen.getByText('Занятий нет')).toBeTruthy();
  });

  it('renders error message on 500', async () => {
    await setup();
    const req = httpMock.expectOne(r => r.url === '/api/schedule/groups/5/lessons');
    req.flush(null, { status: 500, statusText: 'Server Error' });
    expect(screen.getByText('Не удалось загрузить расписание. Попробуйте позже.')).toBeTruthy();
  });

  it('next week button shifts dateFrom by +7 and issues a new request', async () => {
    const { nativeElement } = await setup();
    const first = httpMock.expectOne(r => r.url === '/api/schedule/groups/5/lessons');
    first.flush({ _embedded: { lessonResponseList: [] } });

    const nextBtn = nativeElement.querySelector('button[aria-label="Следующая неделя"]') as HTMLButtonElement;
    await userEvent.setup({ advanceTimers: vi.advanceTimersByTime }).click(nextBtn);

    const second = httpMock.expectOne(r => r.url === '/api/schedule/groups/5/lessons');
    expect(second.request.params.get('dateFrom')).toBe('2026-04-13');
    expect(second.request.params.get('dateTo')).toBe('2026-04-18');
    second.flush({ _embedded: { lessonResponseList: [] } });
  });

  it('"Сегодня" pill appears after next-week navigation and jumps back to current week on click', async () => {
    const { nativeElement } = await setup();
    httpMock.expectOne(r => r.url === '/api/schedule/groups/5/lessons')
            .flush({ _embedded: { lessonResponseList: [] } });

    const nextBtn = nativeElement.querySelector('button[aria-label="Следующая неделя"]') as HTMLButtonElement;
    await userEvent.setup({ advanceTimers: vi.advanceTimersByTime }).click(nextBtn);
    httpMock.expectOne(r => r.url === '/api/schedule/groups/5/lessons')
            .flush({ _embedded: { lessonResponseList: [] } });

    const pill = screen.getByText('Сегодня');
    expect(pill).toBeTruthy();
    await userEvent.setup({ advanceTimers: vi.advanceTimersByTime }).click(pill);
    const third = httpMock.expectOne(r => r.url === '/api/schedule/groups/5/lessons');
    expect(third.request.params.get('dateFrom')).toBe('2026-04-06');
    third.flush({ _embedded: { lessonResponseList: [] } });
  });

  it('filters lessons by selected day', async () => {
    const fixture = await setup();
    const req = httpMock.expectOne(r => r.url === '/api/schedule/groups/5/lessons');
    req.flush({
      _embedded: {
        lessonResponseList: [
          mkLesson({ id: 1, dayOfWeek: 1, subjectId: 7, lessonNumber: 1 }),
          mkLesson({ id: 2, dayOfWeek: 4, subjectId: 7, lessonNumber: 2 }),
        ],
      },
    });
    // subject cache makes subject lookup requests — flush as 'Предмет1'
    httpMock.match(r => r.url === '/api/academic/subjects/7').forEach(r =>
      r.flush({ id: 7, name: 'Математика' }),
    );
    fixture.detectChanges();
    // Default selectedDayIndex is Thursday (3), so lesson #2 should be visible
    expect(screen.getAllByText(/Пара № *2|10:30|09:00/).length).toBeGreaterThan(0);
    // Switch to Monday (index 0) — lesson #1 should now be visible
    const monTab = screen.getByRole('tab', { name: 'Пн' });
    await userEvent.setup({ advanceTimers: vi.advanceTimersByTime }).click(monTab);
    fixture.detectChanges();
    // now #1 dayOfWeek:1 shows; #2 dayOfWeek:4 hides
  });

  afterEach(() => {
    if (httpMock) httpMock.verify();
  });
});
```

(Adjust assertions to the real DOM — the test's goal is to prove the behavior, not match exact pixel/markup.)

Step 5 — run the new spec and the full regression:

```bash
cd frontends/web-panel && npm test -- --run src/app/features/student/schedule && npm run build && npm test
```

All must exit 0.
  </action>
  <verify>
    <automated>cd frontends/web-panel && npm test -- --run src/app/features/student/schedule 2>&1 | tail -40</automated>
  </verify>
  <acceptance_criteria>
    - student-schedule.component.ts contains `export class StudentScheduleComponent` AND imports `LessonRowComponent` AND `StudentApiService` AND `SubjectCacheService` AND `AuthService`
    - student-schedule.component.ts contains `trigger('routeFade'` AND `trigger('daySlide'`
    - student-schedule.component.ts contains `signal<Date>` AND `computed(` AND `currentWeekStart` AND `selectedDayIndex` AND `expandedLessonId`
    - student-schedule.component.html contains the literal strings `Расписание`, `Предыдущая неделя`, `Следующая неделя`, `Занятий нет`, `В этот день пар не запланировано.`, `Сегодня`
    - student-schedule.component.html contains `role="tablist"` AND `role="tab"` AND `aria-selected`
    - student-schedule.component.html contains `<app-lesson-row`
    - student-schedule.component.css contains `var(--gradient-brand)` AND `var(--accent-primary)` AND `prefers-reduced-motion` AND NO hex color values (grep `#[0-9a-fA-F]{3,8}` returns zero matches — excluding `--` tokens)
    - student-schedule.component.spec.ts contains `provideHttpClient` AND `provideHttpClientTesting` AND `makeAuthMock` AND `HttpTestingController`
    - `cd frontends/web-panel && npm test -- --run src/app/features/student/schedule` exits 0
    - `cd frontends/web-panel && npm run build` exits 0
  </acceptance_criteria>
  <done>
`/student/schedule` is fully functional: it fetches the current week on load, shows skeletons then lessons, supports prev/next week + "Сегодня" pill, shows empty state and error state, filters lessons by selected day tab (Пн-Сб), and expands lesson detail on click. Specs cover loading, empty, error, week navigation, today pill, and day filter. All existing vitest tests continue to pass.
  </done>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| browser → `/api/schedule/groups/{groupId}/lessons` | authInterceptor attaches JWT; backend authorises by groupId |
| browser → `/api/academic/subjects/{subjectId}` | Same bearer auth; subject names are non-sensitive |
| template → DOM | User-sourced subject names and room values interpolated via Angular {{ }} bindings |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-51-08 | XSS | student-schedule.component.html | mitigate | All lesson.room, subjectName, and cancelReason values are rendered via Angular `{{ }}` interpolation which HTML-escapes by default. No `[innerHTML]` bindings. No `bypassSecurityTrustHtml`. grep asserts: `grep -rn "innerHTML\|bypassSecurityTrust" frontends/web-panel/src/app/features/student/schedule` returns zero matches. |
| T-51-09 | IDOR | getWeekLessons call | mitigate | `groupId` is read from `AuthService.currentUser().groupId` (parsed from JWT claims), NEVER from route params or query strings. A tampered client cannot fetch another group's schedule without a forged JWT, which the backend rejects. |
| T-51-10 | Information Disclosure | URL with groupId | accept | The route `/api/schedule/groups/{id}/lessons` embeds the id in the path; this is standard REST and authorised server-side. Low risk. |
| T-51-11 | Denial of Service | rapid prev/next clicking | mitigate | `loadWeek()` issues one HTTP call per click without debouncing; `reconnectDelay` n/a here. Trade-off accepted — desktop interaction rate is low. If concern, Phase 52 can add `distinctUntilChanged` on `currentWeekStart`. |
| T-51-12 | Route guard bypass | unauthenticated access | mitigate | Route `/student/schedule` is under `canActivate: [studentGuard]` set by Plan 01's app.routes.ts. The component additionally checks `auth.currentUser()?.groupId` and refuses to fetch if absent — defense in depth. |
</threat_model>

<verification>
Automated gates that must pass:
1. `cd frontends/web-panel && npm test -- --run src/app/features/student/schedule` exits 0
2. `cd frontends/web-panel && npm run build` exits 0
3. `cd frontends/web-panel && npm test` exits 0 (full regression — no prior test regressed)
4. `grep -n "innerHTML\|bypassSecurityTrust" frontends/web-panel/src/app/features/student/schedule` returns zero matches
5. `grep -nE "#[0-9a-fA-F]{3,8}" frontends/web-panel/src/app/features/student/schedule/*.css frontends/web-panel/src/app/features/student/schedule/lesson-row/*.css` returns zero matches (no hex literals outside token variables)
</verification>

<success_criteria>
- STU-WEB-02 satisfied: `/student/schedule` shows weekly calendar with prev/next nav, day tabs, lesson list with subject/time/room/status, inline expand-on-click detail panel, loading/empty/error states
- Visual parity with PWA schedule view (subway-rail lesson row, status chips, week nav strip)
- All tokens from UI-SPEC (colors, spacing, typography, motion) used via CSS vars — no hex, no Tailwind utilities, no direct Material theming
- Angular animations for routeFade (200ms) and daySlide (150ms) match UI-SPEC line 224-226
- Full web-panel vitest suite green, prod build green
</success_criteria>

<output>
After completion, create `.planning/phases/51-student-web-cabinet-shell-schedule-check-in/51-02-SUMMARY.md` capturing: files created, test count delta, any deviations from the UI-SPEC (e.g. if lesson-type label fell back to `Пара №N` because LessonResponse lacks a type field), verification output.
</output>
