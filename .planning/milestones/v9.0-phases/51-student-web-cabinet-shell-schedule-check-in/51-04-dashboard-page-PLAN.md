---
phase: 51
plan: 04
type: execute
wave: 2
depends_on: [51-01]
files_modified:
  - frontends/web-panel/src/app/features/student/dashboard/student-dashboard.component.ts
  - frontends/web-panel/src/app/features/student/dashboard/student-dashboard.component.html
  - frontends/web-panel/src/app/features/student/dashboard/student-dashboard.component.css
  - frontends/web-panel/src/app/features/student/dashboard/student-dashboard.component.spec.ts
  - frontends/web-panel/src/app/features/student/dashboard/next-lesson-card/next-lesson-card.component.ts
  - frontends/web-panel/src/app/features/student/dashboard/next-lesson-card/next-lesson-card.component.html
  - frontends/web-panel/src/app/features/student/dashboard/next-lesson-card/next-lesson-card.component.css
  - frontends/web-panel/src/app/features/student/dashboard/next-lesson-card/next-lesson-card.component.spec.ts
  - frontends/web-panel/src/app/features/student/dashboard/redzone-warning/redzone-warning.component.ts
  - frontends/web-panel/src/app/features/student/dashboard/redzone-warning/redzone-warning.component.html
  - frontends/web-panel/src/app/features/student/dashboard/redzone-warning/redzone-warning.component.css
  - frontends/web-panel/src/app/features/student/dashboard/redzone-warning/redzone-warning.component.spec.ts
autonomous: true
requirements: [STU-WEB-01]

must_haves:
  truths:
    - "Visiting /student/dashboard renders a greeting hero with a time-based 'Доброе утро/день/вечер' heading and a live clock"
    - "A 'Расписание на сегодня' section shows today's lessons as a horizontal row of compact chips (time + subject abbrev + status dot); if today is empty, shows 'Сегодня пар нет'"
    - "A 'Текущая пара' / 'Следующая пара' card (NextLessonCard) appears under the greeting showing the subject, HH:mm–HH:mm time, room, and an inline 'Отметиться' link routing to /student/checkin — active lessons get --border-accent + --glow-primary styling"
    - "Red-zone warnings appear as amber banner rows, one per subject whose attendance percentage is below the resolved global threshold; absent otherwise"
    - "If student has no active or planned lessons today, NextLessonCard falls back to an empty variant with 'Сегодня пар нет'"
    - "Dashboard loads data via 3 parallel HttpClient calls (getWeekLessons for today, getStudentStats, resolveGlobalThreshold) and renders skeletons until all three have resolved"
    - "On any of the three requests erroring, the template renders 'Не удалось загрузить данные. Проверьте подключение и обновите страницу.'"
    - "Dashboard uses @angular/animations routeFade (200ms) on enter"
    - "At least one red-zone warning is rendered when the mock stats contain a subject with percentage < threshold"
  artifacts:
    - path: "frontends/web-panel/src/app/features/student/dashboard/student-dashboard.component.ts"
      provides: "StudentDashboardComponent — greeting hero + today chips + next-lesson + redzone warnings"
      exports: ["StudentDashboardComponent"]
    - path: "frontends/web-panel/src/app/features/student/dashboard/next-lesson-card/next-lesson-card.component.ts"
      provides: "Presentational NextLessonCardComponent — inputs {lesson, subjectName, isActive}"
      exports: ["NextLessonCardComponent"]
    - path: "frontends/web-panel/src/app/features/student/dashboard/redzone-warning/redzone-warning.component.ts"
      provides: "Presentational RedzoneWarningComponent — inputs {subjectName, percentage}"
      exports: ["RedzoneWarningComponent"]
    - path: "frontends/web-panel/src/app/features/student/dashboard/student-dashboard.component.spec.ts"
      provides: "Component test covering loading, greeting, red-zone threshold calculation, next lesson selection, error state"
      contains: "StudentDashboardComponent"
  key_links:
    - from: "student-dashboard.component.ts"
      to: "StudentApiService.getWeekLessons"
      via: "inject + subscribe in ngOnInit with dateFrom=today, dateTo=today"
      pattern: "getWeekLessons\\("
    - from: "student-dashboard.component.ts"
      to: "StudentApiService.getStudentStats"
      via: "inject + subscribe in ngOnInit"
      pattern: "getStudentStats\\(\\)"
    - from: "student-dashboard.component.ts"
      to: "StudentApiService.resolveGlobalThreshold"
      via: "inject + subscribe in ngOnInit"
      pattern: "resolveGlobalThreshold\\(\\)"
    - from: "student-dashboard.component.ts"
      to: "/student/checkin"
      via: "routerLink in NextLessonCard template"
      pattern: "routerLink.*student/checkin"
---

<objective>
Build the `/student/dashboard` page as a full functional replacement for the empty-shell committed in Plan 01. The landing page greets the student, summarises today's schedule in a horizontal chip row, spotlights the current-or-next lesson in a NextLessonCard, and surfaces any subject whose attendance is below the global threshold as an amber red-zone warning.

Purpose: Deliver STU-WEB-01 — when the student logs in the first thing they see is a concise, actionable overview of their day.

Output: A working `/student/dashboard` route with greeting hero, today's lesson chips, next-lesson card (linking to check-in), zero or more red-zone warning banners, full loading/error handling, and unit tests.
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
@frontends/web-panel/src/app/features/student/dashboard/student-dashboard.component.ts
@frontends/web-panel/src/app/core/auth/auth.service.ts
@frontends/web-panel/src/styles.css
@frontends/web-panel/src/styles/tokens.css
@frontends/web-panel/src/app/features/admin/dashboard/admin-dashboard.component.ts
@frontends/web-panel/src/app/features/admin/dashboard/admin-dashboard.component.html
@frontends/web-panel/src/app/features/admin/dashboard/admin-dashboard.component.css
@frontends/web-panel/src/app/features/admin/dashboard/admin-dashboard.component.spec.ts

<interfaces>
<!-- Contracts provided by Plan 01 (51-01). -->

From frontends/web-panel/src/app/features/student/shared/student-api.service.ts:
```typescript
@Injectable({ providedIn: 'root' })
export class StudentApiService {
  getWeekLessons(groupId: number, dateFrom: string, dateTo: string): Observable<LessonResponse[]>;
  getStudentStats(): Observable<StudentStatsResponse>;
  resolveGlobalThreshold(): Observable<ResolvedThresholdResponse>;
}
```

From frontends/web-panel/src/app/features/student/shared/student-schedule.types.ts:
```typescript
export interface LessonResponse { id: number; subjectId: number; status: LessonStatus; date: string; startTime: string; endTime: string; room: string; ... }
export type LessonStatus = 'PLANNED' | 'ACTIVE' | 'CLOSED' | 'CANCELLED';
export interface SubjectStats { subjectId: number; subjectName: string; percentage: number; total: number; attended: number; absent: number; excused: number }
export interface StudentStatsResponse { subjects: SubjectStats[]; overall: OverallStats }
export interface ResolvedThresholdResponse { groupId: number | null; subjectId: number | null; percentage: number; level: 'global' | 'group' | 'subject' }
```

From frontends/web-panel/src/app/core/auth/auth.service.ts:
```typescript
readonly currentUser: Signal<AuthUser | null>; // { id, role, isHeadman, groupId }
```

Reference — admin dashboard pattern (replicate structure, not copy-paste):
- `admin-dashboard.component.ts` uses `ChangeDetectionStrategy.OnPush`, `signal<DashboardStatsResponse | null>`, `loading`, `error`, a `_now = signal(new Date())` ticker + setInterval cleaned via `destroyRef.onDestroy`, `computed` greeting, `ngOnInit` that calls the API service
- `admin-dashboard.component.html` uses `.dashboard`, `.dashboard__hero`, `.dashboard__greeting`, `.dashboard__clock`, `.dashboard__grid`, `.dashboard__error` CSS classes
- `admin-dashboard.component.css` defines the grid + hero with tokens — reuse the same tokens so Phase 51 dashboard looks visually consistent

CSS tokens referenced by UI-SPEC for this page:
- `--accent-primary`, `--accent-secondary`, `--accent-warning`, `--border-accent`, `--glow-primary`
- `--bg-primary`, `--bg-secondary`, `--bg-elevated`, `--text-primary`, `--text-secondary`, `--text-muted`
- `--font-display`, `--font-heading`, `--font-sans`, `--font-mono`, `--text-xs`, `--text-sm`, `--text-base`, `--text-2xl`
- `--space-1..--space-8`, `--radius-md`, `--radius-lg`, `--radius-xl`
- `--gradient-brand`, `--ease-out`
</interfaces>
</context>

<tasks>

<task type="auto" tdd="true">
  <name>Task 1: Presentational sub-components (NextLessonCard, RedzoneWarning)</name>
  <files>frontends/web-panel/src/app/features/student/dashboard/next-lesson-card/next-lesson-card.component.ts, frontends/web-panel/src/app/features/student/dashboard/next-lesson-card/next-lesson-card.component.html, frontends/web-panel/src/app/features/student/dashboard/next-lesson-card/next-lesson-card.component.css, frontends/web-panel/src/app/features/student/dashboard/next-lesson-card/next-lesson-card.component.spec.ts, frontends/web-panel/src/app/features/student/dashboard/redzone-warning/redzone-warning.component.ts, frontends/web-panel/src/app/features/student/dashboard/redzone-warning/redzone-warning.component.html, frontends/web-panel/src/app/features/student/dashboard/redzone-warning/redzone-warning.component.css, frontends/web-panel/src/app/features/student/dashboard/redzone-warning/redzone-warning.component.spec.ts</files>
  <read_first>
    - frontends/web-panel/src/app/features/student/shared/student-schedule.types.ts (LessonResponse shape)
    - frontends/web-panel/src/app/features/admin/dashboard/admin-dashboard.component.html (reference for `.dashboard` / card patterns)
    - frontends/web-panel/src/app/features/admin/dashboard/admin-dashboard.component.css (reference for `.dashboard__hero`, `.dashboard__eyebrow`, `.dashboard__pulse`)
    - .planning/phases/51-student-web-cabinet-shell-schedule-check-in/51-UI-SPEC.md lines 151-164 (dashboard component table — NextLessonCard visual contract and RedzoneWarning amber banner contract) and lines 240-255 (copy)
    - frontends/pwa/src/features/checkin/CheckInScreen.tsx lines 33-157 (ActiveLessonCard visual reference — not to copy React, but to match UX parity)
  </read_first>
  <behavior>
    # next-lesson-card.component.spec.ts — MUST cover:
    - Renders with an ACTIVE lesson: assert the heading text is `Текущая пара`, the subject name is in the DOM, the time label `HH:mm–HH:mm` is in the DOM, the room is in the DOM, and the `.next-lesson-card--active` class is applied to the root element
    - Renders with a PLANNED lesson: assert the heading text is `Следующая пара`, the `.next-lesson-card--active` class is NOT applied
    - Renders with no lesson (null input): assert the empty-variant text `Сегодня пар нет` is in the DOM
    - The inline "Отметиться" link has `routerLink="/student/checkin"` and is only rendered when `isActive === true`

    # redzone-warning.component.spec.ts — MUST cover:
    - Renders with inputs `{ subjectName: 'Математика', percentage: 42 }` → the DOM contains `Математика` AND `42%` AND the literal text `посещаемость ниже порога`
    - The root element has a class (e.g. `redzone-warning`) and a warning icon is present (`.ph-warning-duotone` or similar)
    - Rounds the percentage to the nearest integer — `percentage: 42.7` renders as `43%`
  </behavior>
  <action>
Step 1 — create `next-lesson-card.component.ts`. Presentational, OnPush, standalone. Uses `RouterLink` for the inline check-in link:

```typescript
import { ChangeDetectionStrategy, Component, Input, computed, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import type { LessonResponse } from '../../shared/student-schedule.types';

/**
 * Dashboard "current / next lesson" card.
 *
 * Visual language per UI-SPEC §Component 2 — card with --border-accent +
 * --glow-primary when active, plain --border-subtle otherwise. The inline
 * "Отметиться" link is rendered only when the lesson is ACTIVE and links to
 * /student/checkin.
 */
@Component({
  selector: 'app-next-lesson-card',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink],
  templateUrl: './next-lesson-card.component.html',
  styleUrl: './next-lesson-card.component.css',
})
export class NextLessonCardComponent {
  @Input() lesson: LessonResponse | null = null;
  @Input() subjectName: string = 'Предмет';

  get isActive(): boolean { return this.lesson?.status === 'ACTIVE'; }
  get isPresent(): boolean { return this.lesson !== null; }
  get heading(): string { return this.isActive ? 'Текущая пара' : 'Следующая пара'; }

  get startLabel(): string { return this.lesson ? this.lesson.startTime.slice(0, 5) : ''; }
  get endLabel(): string { return this.lesson ? this.lesson.endTime.slice(0, 5) : ''; }
  get timeLabel(): string { return this.isPresent ? `${this.startLabel}–${this.endLabel}` : ''; }
}
```

`next-lesson-card.component.html`:

```html
@if (lesson; as l) {
  <article class="next-lesson-card" [class.next-lesson-card--active]="isActive">
    <p class="next-lesson-card__eyebrow">
      @if (isActive) {
        <span class="next-lesson-card__live-dot" aria-hidden="true"></span>
      }
      {{ heading }}
    </p>
    <h3 class="next-lesson-card__subject">{{ subjectName }}</h3>
    <p class="next-lesson-card__meta">
      <i class="ph-fill ph-clock" aria-hidden="true"></i>
      <span class="next-lesson-card__time">{{ timeLabel }}</span>
      <span class="next-lesson-card__sep" aria-hidden="true"></span>
      <i class="ph-fill ph-map-pin" aria-hidden="true"></i>
      <span>Ауд. {{ l.room }}</span>
    </p>
    @if (isActive) {
      <a routerLink="/student/checkin" class="next-lesson-card__cta">
        <i class="ph-fill ph-map-pin" aria-hidden="true"></i>
        Отметиться
        <i class="ph ph-arrow-right" aria-hidden="true"></i>
      </a>
    }
  </article>
} @else {
  <article class="next-lesson-card next-lesson-card--empty">
    <div class="next-lesson-card__empty-icon" aria-hidden="true">
      <i class="ph-duotone ph-calendar-blank"></i>
    </div>
    <h3 class="next-lesson-card__subject">Сегодня пар нет</h3>
    <p class="next-lesson-card__meta">Хорошее время чтобы отдохнуть или подготовиться.</p>
  </article>
}
```

`next-lesson-card.component.css`:

```css
:host { display: block; }

.next-lesson-card {
  padding: var(--space-5);
  border-radius: var(--radius-lg);
  background: var(--bg-secondary);
  border: 1px solid var(--border-subtle);
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  transition: border-color var(--duration-base) var(--ease-out), box-shadow var(--duration-base) var(--ease-out);
}
.next-lesson-card--active {
  border-color: var(--border-accent);
  box-shadow: var(--glow-primary);
}

.next-lesson-card__eyebrow {
  margin: 0;
  font-size: var(--text-xs);
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: var(--tracking-wide);
  color: var(--text-muted);
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
}
.next-lesson-card--active .next-lesson-card__eyebrow { color: var(--accent-primary); }

.next-lesson-card__live-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--accent-primary);
  box-shadow: 0 0 6px var(--accent-primary);
  animation: next-lesson-blink 1.6s ease-in-out infinite;
}
@keyframes next-lesson-blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.4; }
}

.next-lesson-card__subject {
  margin: 0;
  font-family: var(--font-heading);
  font-size: var(--text-2xl);
  font-weight: 600;
  line-height: var(--leading-heading);
  color: var(--text-primary);
}

.next-lesson-card__meta {
  margin: 0;
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  color: var(--text-secondary);
  font-size: var(--text-sm);
}
.next-lesson-card__time {
  font-family: var(--font-mono);
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}
.next-lesson-card__sep {
  width: 4px;
  height: 4px;
  border-radius: 50%;
  background: var(--text-muted);
  display: inline-block;
}
.next-lesson-card__meta i { font-size: 14px; }

.next-lesson-card__cta {
  align-self: flex-start;
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  padding: 10px 18px;
  min-height: 44px;
  border-radius: var(--radius-full);
  background: var(--accent-primary);
  color: var(--accent-primary-contrast);
  font-family: var(--font-heading);
  font-weight: 600;
  font-size: var(--text-sm);
  text-decoration: none;
  transition: filter var(--duration-base) var(--ease-out);
}
.next-lesson-card__cta:hover { filter: brightness(1.08); }
.next-lesson-card__cta:focus-visible { outline: 2px solid var(--accent-primary); outline-offset: 2px; }
.next-lesson-card__cta i { font-size: 16px; }

.next-lesson-card--empty {
  align-items: center;
  text-align: center;
  padding: var(--space-6);
}
.next-lesson-card__empty-icon {
  width: 56px;
  height: 56px;
  border-radius: var(--radius-full);
  background: color-mix(in oklab, var(--accent-secondary) 12%, transparent);
  border: 1px solid color-mix(in oklab, var(--accent-secondary) 28%, transparent);
  color: var(--accent-secondary);
  display: grid;
  place-items: center;
}
.next-lesson-card__empty-icon i { font-size: 28px; }

@media (prefers-reduced-motion: reduce) {
  .next-lesson-card__live-dot { animation: none; }
  .next-lesson-card { transition: none; }
}
```

Step 2 — write `next-lesson-card.component.spec.ts`. Pattern like the admin stat-card spec:

```typescript
import { render, screen } from '@testing-library/angular';
import { describe, it, expect } from 'vitest';
import { provideRouter } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { NextLessonCardComponent } from './next-lesson-card.component';
import type { LessonResponse } from '../../shared/student-schedule.types';

const base: LessonResponse = {
  id: 1, scheduleItemId: 1, groupId: 5, subjectId: 7, teacherId: 11,
  date: '2026-04-09', status: 'PLANNED', dayOfWeek: 4, lessonNumber: 2,
  startTime: '10:00:00', endTime: '11:30:00',
  weekType: 'BOTH', room: '404', geoBlocked: false, cancelReason: null,
  createdAt: '2026-04-01T00:00:00Z',
};

describe('NextLessonCardComponent', () => {
  it('renders "Текущая пара" heading + active class for ACTIVE lesson', async () => {
    const { container } = await render(NextLessonCardComponent, {
      providers: [provideRouter([]), provideNoopAnimations()],
      componentInputs: { lesson: { ...base, status: 'ACTIVE' }, subjectName: 'Математика' },
    });
    expect(screen.getByText('Текущая пара')).toBeTruthy();
    expect(screen.getByText('Математика')).toBeTruthy();
    expect(screen.getByText(/10:00.*11:30/)).toBeTruthy();
    expect(screen.getByText(/Ауд.\s*404/)).toBeTruthy();
    expect(container.querySelector('.next-lesson-card--active')).toBeTruthy();
  });

  it('renders "Следующая пара" heading WITHOUT active class for PLANNED lesson', async () => {
    const { container } = await render(NextLessonCardComponent, {
      providers: [provideRouter([]), provideNoopAnimations()],
      componentInputs: { lesson: { ...base, status: 'PLANNED' }, subjectName: 'Математика' },
    });
    expect(screen.getByText('Следующая пара')).toBeTruthy();
    expect(container.querySelector('.next-lesson-card--active')).toBeNull();
  });

  it('renders "Отметиться" CTA only when lesson is ACTIVE', async () => {
    await render(NextLessonCardComponent, {
      providers: [provideRouter([]), provideNoopAnimations()],
      componentInputs: { lesson: { ...base, status: 'ACTIVE' }, subjectName: 'М' },
    });
    expect(screen.getByText('Отметиться')).toBeTruthy();
  });

  it('renders empty variant "Сегодня пар нет" when lesson is null', async () => {
    await render(NextLessonCardComponent, {
      providers: [provideRouter([]), provideNoopAnimations()],
      componentInputs: { lesson: null },
    });
    expect(screen.getByText('Сегодня пар нет')).toBeTruthy();
  });
});
```

Step 3 — create `redzone-warning.component.ts`:

```typescript
import { ChangeDetectionStrategy, Component, Input } from '@angular/core';

/**
 * Amber banner surfacing a subject whose attendance is below the threshold.
 * Visual contract per UI-SPEC §Component 2 (Red-zone warnings sub-component).
 */
@Component({
  selector: 'app-redzone-warning',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './redzone-warning.component.html',
  styleUrl: './redzone-warning.component.css',
})
export class RedzoneWarningComponent {
  @Input({ required: true }) subjectName!: string;
  @Input({ required: true }) percentage!: number;

  get percentLabel(): string {
    return `${Math.round(this.percentage)}%`;
  }
}
```

`redzone-warning.component.html`:

```html
<div class="redzone-warning" role="alert">
  <i class="ph-duotone ph-warning" aria-hidden="true"></i>
  <p class="redzone-warning__text">
    <strong>{{ subjectName }}</strong> — посещаемость ниже порога ({{ percentLabel }})
  </p>
</div>
```

`redzone-warning.component.css`:

```css
:host { display: block; }

.redzone-warning {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-3) var(--space-4);
  border-radius: var(--radius-md);
  background: color-mix(in oklab, var(--accent-warning) 10%, transparent);
  border: 1px solid color-mix(in oklab, var(--accent-warning) 25%, transparent);
  color: var(--accent-warning);
}
.redzone-warning i { font-size: 20px; flex-shrink: 0; }
.redzone-warning__text {
  margin: 0;
  font-size: var(--text-sm);
  color: var(--text-primary);
  line-height: var(--leading-body);
}
.redzone-warning__text strong { color: var(--accent-warning); }
```

Step 4 — write `redzone-warning.component.spec.ts`:

```typescript
import { render, screen } from '@testing-library/angular';
import { describe, it, expect } from 'vitest';
import { RedzoneWarningComponent } from './redzone-warning.component';

describe('RedzoneWarningComponent', () => {
  it('renders subject name, rounded percentage, and the threshold copy', async () => {
    await render(RedzoneWarningComponent, {
      componentInputs: { subjectName: 'Математика', percentage: 42 },
    });
    expect(screen.getByText('Математика')).toBeTruthy();
    expect(screen.getByText(/42%/)).toBeTruthy();
    expect(screen.getByText(/посещаемость ниже порога/)).toBeTruthy();
  });

  it('rounds percentage to the nearest integer', async () => {
    await render(RedzoneWarningComponent, {
      componentInputs: { subjectName: 'Физика', percentage: 42.7 },
    });
    expect(screen.getByText(/43%/)).toBeTruthy();
  });
});
```

Step 5 — run the sub-component specs:

```bash
cd frontends/web-panel && npm test -- --run src/app/features/student/dashboard/next-lesson-card src/app/features/student/dashboard/redzone-warning
```

Must exit 0.
  </action>
  <verify>
    <automated>cd frontends/web-panel && npm test -- --run src/app/features/student/dashboard/next-lesson-card src/app/features/student/dashboard/redzone-warning 2>&1 | tail -30</automated>
  </verify>
  <acceptance_criteria>
    - next-lesson-card.component.ts contains `export class NextLessonCardComponent` AND `imports: [RouterLink]` AND `selector: 'app-next-lesson-card'`
    - next-lesson-card.component.html contains `routerLink="/student/checkin"` AND the literals `Текущая пара`, `Следующая пара`, `Сегодня пар нет`, `Отметиться`
    - next-lesson-card.component.css contains `var(--border-accent)` AND `var(--glow-primary)` AND `prefers-reduced-motion` AND NO hex color values
    - redzone-warning.component.ts contains `export class RedzoneWarningComponent` AND `@Input({ required: true }) subjectName`
    - redzone-warning.component.html contains the literal `посещаемость ниже порога`
    - redzone-warning.component.css contains `var(--accent-warning)` AND NO hex color values
    - `cd frontends/web-panel && npm test -- --run src/app/features/student/dashboard/next-lesson-card src/app/features/student/dashboard/redzone-warning` exits 0
  </acceptance_criteria>
  <done>
Two presentational sub-components committed with token-only styling and unit tests. NextLessonCard covers active/planned/empty variants and the check-in link; RedzoneWarning covers percentage rounding and copy.
  </done>
</task>

<task type="auto" tdd="true">
  <name>Task 2: StudentDashboardComponent — greeting, today chips, wiring sub-components, tests</name>
  <files>frontends/web-panel/src/app/features/student/dashboard/student-dashboard.component.ts, frontends/web-panel/src/app/features/student/dashboard/student-dashboard.component.html, frontends/web-panel/src/app/features/student/dashboard/student-dashboard.component.css, frontends/web-panel/src/app/features/student/dashboard/student-dashboard.component.spec.ts</files>
  <read_first>
    - frontends/web-panel/src/app/features/student/dashboard/student-dashboard.component.ts (empty shell from Plan 01)
    - frontends/web-panel/src/app/features/student/dashboard/next-lesson-card/next-lesson-card.component.ts (just committed in Task 1)
    - frontends/web-panel/src/app/features/student/dashboard/redzone-warning/redzone-warning.component.ts (just committed in Task 1)
    - frontends/web-panel/src/app/features/student/shared/student-api.service.ts
    - frontends/web-panel/src/app/features/student/shared/subject-cache.service.ts
    - frontends/web-panel/src/app/core/auth/auth.service.ts
    - frontends/web-panel/src/app/features/admin/dashboard/admin-dashboard.component.ts (canonical dashboard structure — greeting + live clock + grid)
    - frontends/web-panel/src/app/features/admin/dashboard/admin-dashboard.component.html (`.dashboard__hero`, `.dashboard__greeting`, `.dashboard__clock`)
    - frontends/web-panel/src/app/features/admin/dashboard/admin-dashboard.component.css (hero gradient, pulse, grid)
    - frontends/web-panel/src/app/features/admin/dashboard/admin-dashboard.component.spec.ts (HttpTestingController pattern)
    - .planning/phases/51-student-web-cabinet-shell-schedule-check-in/51-UI-SPEC.md lines 120-164 (dashboard component contract) and lines 240-255 (dashboard copy)
  </read_first>
  <behavior>
    # student-dashboard.component.spec.ts — MUST cover:
    1. "on init issues three parallel API requests: today lessons, student stats, global threshold"
       - Expect `GET /api/schedule/groups/5/lessons?dateFrom={today}&dateTo={today}`
       - Expect `GET /api/attendance/reports/student/stats`
       - Expect `GET /api/academic/thresholds/resolve`
    2. "renders greeting based on current time" — with fake time at 08:00 renders `Доброе утро`; at 14:00 renders `Добрый день`; at 21:00 renders `Добрый вечер`
    3. "shows skeleton placeholders while any of the three requests is pending"
    4. "renders today chips row with one chip per today's lesson after data resolves" — assert at least one element with class `dashboard-today-chip` exists after flushing 2 lessons
    5. "renders 'Сегодня пар нет' when today's lesson list is empty"
    6. "wires NextLessonCard with the ACTIVE lesson when one exists"
    7. "wires NextLessonCard with the earliest PLANNED lesson when no ACTIVE exists"
    8. "renders one RedzoneWarning per subject whose percentage is strictly less than the resolved threshold" — flush stats `{ subjects: [{subjectId:1, subjectName:'Мат', percentage: 42}, {subjectId:2, subjectName:'Физ', percentage: 80}] }` and threshold `{ percentage: 70 }` → assert exactly one warning with the text 'Мат'
    9. "renders NO red-zone warnings when all subjects are above threshold"
    10. "renders 'Не удалось загрузить данные.' error message when any of the three requests fails"
  </behavior>
  <action>
Step 1 — replace the empty shell `student-dashboard.component.ts` with the full implementation. The component runs three parallel requests and a live clock:

```typescript
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import { AsyncPipe } from '@angular/common';
import { trigger, transition, style, animate } from '@angular/animations';
import { forkJoin } from 'rxjs';
import { AuthService } from '../../../core/auth/auth.service';
import { StudentApiService } from '../shared/student-api.service';
import { SubjectCacheService } from '../shared/subject-cache.service';
import type {
  LessonResponse,
  StudentStatsResponse,
  ResolvedThresholdResponse,
  SubjectStats,
} from '../shared/student-schedule.types';
import { NextLessonCardComponent } from './next-lesson-card/next-lesson-card.component';
import { RedzoneWarningComponent } from './redzone-warning/redzone-warning.component';

function todayDateString(): string {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

function formatHhMm(time: string): string { return time.slice(0, 5); }

@Component({
  selector: 'app-student-dashboard',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [AsyncPipe, NextLessonCardComponent, RedzoneWarningComponent],
  templateUrl: './student-dashboard.component.html',
  styleUrl: './student-dashboard.component.css',
  animations: [
    trigger('routeFade', [
      transition(':enter', [
        style({ opacity: 0, transform: 'translateY(8px)' }),
        animate('200ms cubic-bezier(0.16, 1, 0.3, 1)', style({ opacity: 1, transform: 'translateY(0)' })),
      ]),
    ]),
  ],
})
export class StudentDashboardComponent implements OnInit {
  private readonly studentApi = inject(StudentApiService);
  private readonly subjectCache = inject(SubjectCacheService);
  private readonly auth = inject(AuthService);
  private readonly destroyRef = inject(DestroyRef);

  readonly lessons = signal<LessonResponse[]>([]);
  readonly stats = signal<StudentStatsResponse | null>(null);
  readonly threshold = signal<ResolvedThresholdResponse | null>(null);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  private readonly _now = signal(new Date());
  readonly timeLabel = computed(() =>
    this._now().toLocaleTimeString('ru-RU', { hour: '2-digit', minute: '2-digit' }),
  );
  readonly dateLabel = computed(() =>
    this._now().toLocaleDateString('ru-RU', { weekday: 'long', day: 'numeric', month: 'long' }),
  );
  readonly greeting = computed(() => {
    const hour = this._now().getHours();
    if (hour < 6)  return 'Доброй ночи';
    if (hour < 12) return 'Доброе утро';
    if (hour < 18) return 'Добрый день';
    return 'Добрый вечер';
  });

  readonly todaySorted = computed(() =>
    [...this.lessons()].sort((a, b) => a.startTime.localeCompare(b.startTime)),
  );

  readonly nextLesson = computed<LessonResponse | null>(() => {
    const sorted = this.todaySorted();
    return sorted.find(l => l.status === 'ACTIVE')
        ?? sorted.find(l => l.status === 'PLANNED')
        ?? null;
  });

  readonly redZoneSubjects = computed<SubjectStats[]>(() => {
    const s = this.stats();
    const t = this.threshold();
    if (!s || !t) return [];
    return s.subjects.filter(sub => sub.percentage < t.percentage);
  });

  formatTime(time: string): string { return formatHhMm(time); }
  getSubjectName$(id: number | undefined) { return this.subjectCache.getName(id ?? null); }

  ngOnInit(): void {
    const user = this.auth.currentUser();
    const groupId = user?.groupId;
    if (!groupId) {
      this.error.set('Не удалось определить группу пользователя.');
      return;
    }

    this.loading.set(true);
    const today = todayDateString();
    forkJoin({
      lessons: this.studentApi.getWeekLessons(groupId, today, today),
      stats: this.studentApi.getStudentStats(),
      threshold: this.studentApi.resolveGlobalThreshold(),
    }).subscribe({
      next: ({ lessons, stats, threshold }) => {
        this.lessons.set(lessons);
        this.stats.set(stats);
        this.threshold.set(threshold);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Не удалось загрузить данные. Проверьте подключение и обновите страницу.');
        this.loading.set(false);
      },
    });

    const tick = setInterval(() => this._now.set(new Date()), 60_000);
    this.destroyRef.onDestroy(() => clearInterval(tick));
  }
}
```

Step 2 — write `student-dashboard.component.html`. Reuse `.dashboard` / `.dashboard__hero` / `.dashboard__greeting` structure from the admin dashboard for visual consistency, but adapt copy + content for the student context:

```html
<section class="dashboard student-dashboard page-stack" [@routeFade]>
  <!-- Greeting hero -->
  <header class="dashboard__hero">
    <div class="dashboard__greeting">
      <p class="dashboard__eyebrow">
        <span class="dashboard__pulse" aria-hidden="true"></span>
        Студент
      </p>
      <h2 class="dashboard__title">{{ greeting() }}</h2>
      <p class="dashboard__subtitle">{{ dateLabel() }}</p>
    </div>
    <div class="dashboard__clock" aria-hidden="true">
      <span class="dashboard__clock-value">{{ timeLabel() }}</span>
      <span class="dashboard__clock-label">по Москве</span>
    </div>
  </header>

  @if (error()) {
    <div class="dashboard__error" role="alert">
      <i class="ph ph-warning-circle" aria-hidden="true"></i>
      {{ error() }}
    </div>
  } @else if (loading()) {
    <div class="dashboard__skeleton-row" aria-busy="true" aria-label="Загрузка…">
      <div class="dashboard-skeleton"></div>
      <div class="dashboard-skeleton"></div>
    </div>
  } @else {
    <!-- Next lesson card -->
    <app-next-lesson-card
      [lesson]="nextLesson()"
      [subjectName]="(getSubjectName$(nextLesson()?.subjectId) | async) ?? 'Предмет'"
    />

    <!-- Today's schedule summary -->
    <section class="dashboard-today">
      <h3 class="dashboard-today__title">Расписание на сегодня</h3>
      @if (todaySorted().length === 0) {
        <p class="dashboard-today__empty">Сегодня пар нет</p>
      } @else {
        <div class="dashboard-today__row">
          @for (lesson of todaySorted(); track lesson.id) {
            <div
              class="dashboard-today-chip"
              [class.dashboard-today-chip--active]="lesson.status === 'ACTIVE'"
              [class.dashboard-today-chip--cancelled]="lesson.status === 'CANCELLED'"
            >
              <span class="dashboard-today-chip__time">{{ formatTime(lesson.startTime) }}</span>
              <span
                class="dashboard-today-chip__dot"
                [attr.data-status]="lesson.status"
                aria-hidden="true"
              ></span>
              <span class="dashboard-today-chip__name">
                {{ (getSubjectName$(lesson.subjectId) | async) ?? 'Предмет' }}
              </span>
            </div>
          }
        </div>
      }
    </section>

    <!-- Red-zone warnings -->
    @if (redZoneSubjects().length > 0) {
      <section class="dashboard-redzone">
        <h3 class="dashboard-redzone__title">Внимание к посещаемости</h3>
        <div class="dashboard-redzone__list">
          @for (subject of redZoneSubjects(); track subject.subjectId) {
            <app-redzone-warning
              [subjectName]="subject.subjectName"
              [percentage]="subject.percentage"
            />
          }
        </div>
      </section>
    }
  }
</section>
```

Step 3 — write `student-dashboard.component.css`. Pull hero structure from admin-dashboard.component.css (tokens only) and add today-chip styles:

```css
:host { display: block; }

.student-dashboard { display: flex; flex-direction: column; gap: var(--space-6); }

/* Hero — mirrors admin dashboard for visual consistency */
.dashboard__hero {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-5);
  padding: var(--space-6);
  border-radius: var(--radius-xl);
  background:
    radial-gradient(ellipse at 15% 20%, color-mix(in oklab, var(--accent-primary) 16%, transparent), transparent 55%),
    radial-gradient(ellipse at 90% 80%, color-mix(in oklab, var(--accent-secondary) 18%, transparent), transparent 55%),
    var(--bg-secondary);
  border: 1px solid var(--border-default);
}
.dashboard__greeting { min-width: 0; flex: 1; }
.dashboard__eyebrow {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  padding: 4px 12px;
  border-radius: var(--radius-full);
  background: color-mix(in oklab, var(--accent-primary) 12%, transparent);
  border: 1px solid color-mix(in oklab, var(--accent-primary) 30%, transparent);
  color: var(--accent-primary);
  font-family: var(--font-mono);
  font-size: 0.6875rem;
  font-weight: 500;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  margin: 0 0 var(--space-4);
}
.dashboard__pulse {
  width: 6px; height: 6px;
  border-radius: 50%;
  background: var(--accent-primary);
  box-shadow: 0 0 8px var(--accent-primary);
  animation: dashboard-pulse 2.2s ease-in-out infinite;
}
@keyframes dashboard-pulse {
  0%, 100% { opacity: 1; transform: scale(1); }
  50% { opacity: 0.5; transform: scale(1.4); }
}
.dashboard__title {
  margin: 0;
  font-family: var(--font-display);
  font-size: clamp(1.75rem, 2.4vw + 0.5rem, 2.5rem);
  font-weight: 700;
  line-height: 1.1;
  color: var(--text-primary);
}
.dashboard__subtitle {
  margin: var(--space-2) 0 0;
  font-size: var(--text-sm);
  color: var(--text-secondary);
}
.dashboard__clock {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 2px;
}
.dashboard__clock-value {
  font-family: var(--font-mono);
  font-size: var(--text-2xl);
  font-weight: 600;
  color: var(--text-primary);
  font-variant-numeric: tabular-nums;
}
.dashboard__clock-label {
  font-size: var(--text-xs);
  color: var(--text-muted);
  text-transform: uppercase;
  letter-spacing: var(--tracking-wide);
}

.dashboard__error {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-4);
  border-radius: var(--radius-md);
  background: color-mix(in srgb, var(--accent-danger) 12%, transparent);
  border: 1px solid color-mix(in srgb, var(--accent-danger) 28%, transparent);
  color: var(--accent-danger);
  font-size: var(--text-sm);
}

.dashboard__skeleton-row { display: flex; flex-direction: column; gap: var(--space-3); }
.dashboard-skeleton {
  height: 120px;
  border-radius: var(--radius-lg);
  background: linear-gradient(
    90deg,
    var(--bg-elevated) 25%,
    color-mix(in oklab, var(--text-primary) 8%, transparent) 50%,
    var(--bg-elevated) 75%
  );
  background-size: 200% 100%;
  animation: dashboard-shimmer 1.5s linear infinite;
}
@keyframes dashboard-shimmer {
  0% { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}

/* Today chips row */
.dashboard-today__title {
  margin: 0 0 var(--space-3);
  font-family: var(--font-heading);
  font-size: var(--text-base);
  font-weight: 600;
  color: var(--text-primary);
}
.dashboard-today__empty {
  margin: 0;
  font-size: var(--text-sm);
  color: var(--text-secondary);
}
.dashboard-today__row {
  display: flex;
  gap: var(--space-3);
  overflow-x: auto;
  padding-bottom: var(--space-2);
}
.dashboard-today-chip {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-2) var(--space-3);
  border-radius: var(--radius-full);
  background: var(--bg-secondary);
  border: 1px solid var(--border-subtle);
  font-size: var(--text-xs);
  color: var(--text-primary);
}
.dashboard-today-chip--active {
  border-color: var(--border-accent);
  box-shadow: var(--glow-primary);
}
.dashboard-today-chip--cancelled { opacity: 0.55; }
.dashboard-today-chip__time {
  font-family: var(--font-mono);
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}
.dashboard-today-chip__dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--text-muted);
}
.dashboard-today-chip__dot[data-status="ACTIVE"]   { background: var(--accent-primary); box-shadow: 0 0 6px var(--accent-primary); }
.dashboard-today-chip__dot[data-status="CLOSED"]   { background: var(--status-present); }
.dashboard-today-chip__dot[data-status="CANCELLED"]{ background: var(--text-muted); }

.dashboard-today-chip__name {
  font-family: var(--font-sans);
  font-weight: 400;
}

/* Red-zone list */
.dashboard-redzone__title {
  margin: 0 0 var(--space-3);
  font-family: var(--font-heading);
  font-size: var(--text-base);
  font-weight: 600;
  color: var(--text-primary);
}
.dashboard-redzone__list {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

@media (prefers-reduced-motion: reduce) {
  .dashboard-skeleton { animation: none; }
  .dashboard__pulse { animation: none; }
}
```

Step 4 — write `student-dashboard.component.spec.ts`. Uses `provideHttpClient` + `provideHttpClientTesting` + `provideRouter([])` + `provideNoopAnimations()` + fake AuthService + fake timers:

```typescript
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { signal, computed } from '@angular/core';
import { render, screen } from '@testing-library/angular';
import { StudentDashboardComponent } from './student-dashboard.component';
import { AuthService, AuthUser } from '../../../core/auth/auth.service';
import type { LessonResponse, StudentStatsResponse, ResolvedThresholdResponse } from '../shared/student-schedule.types';

const STUDENT_USER: AuthUser = { id: 3, role: 'STUDENT', isHeadman: false, groupId: 5 };

function makeAuthMock() {
  return {
    currentUser: computed(() => STUDENT_USER),
    isAuthenticated: computed(() => true),
    accessToken: signal<string | null>('fake-token').asReadonly(),
    resolveDashboardFor: vi.fn(),
  };
}

function lesson(over: Partial<LessonResponse>): LessonResponse {
  return {
    id: 1, scheduleItemId: 1, groupId: 5, subjectId: 7, teacherId: 11,
    date: '2026-04-09', status: 'PLANNED', dayOfWeek: 4, lessonNumber: 1,
    startTime: '09:00:00', endTime: '10:30:00',
    weekType: 'BOTH', room: '404', geoBlocked: false, cancelReason: null,
    createdAt: '2026-04-01T00:00:00Z',
    ...over,
  };
}

describe('StudentDashboardComponent', () => {
  let httpMock: HttpTestingController;

  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date(2026, 3, 9, 14, 0, 0)); // 14:00 — «Добрый день»
  });
  afterEach(() => {
    vi.useRealTimers();
    httpMock?.verify();
  });

  async function setup() {
    const fixture = await render(StudentDashboardComponent, {
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        provideNoopAnimations(),
        { provide: AuthService, useValue: makeAuthMock() },
      ],
    });
    httpMock = TestBed.inject(HttpTestingController);
    return fixture;
  }

  function flushAll(opts: {
    lessons?: LessonResponse[];
    stats?: StudentStatsResponse;
    threshold?: ResolvedThresholdResponse;
  } = {}) {
    const lessons = opts.lessons ?? [];
    const stats = opts.stats ?? { subjects: [], overall: { total: 0, attended: 0, absent: 0, excused: 0, percentage: 100 } };
    const threshold = opts.threshold ?? { groupId: null, subjectId: null, percentage: 70, level: 'global' };
    httpMock.expectOne(r => r.url === '/api/schedule/groups/5/lessons').flush({ _embedded: { lessonResponseList: lessons } });
    httpMock.expectOne('/api/attendance/reports/student/stats').flush(stats);
    httpMock.expectOne('/api/academic/thresholds/resolve').flush(threshold);
    httpMock.match(r => r.url.startsWith('/api/academic/subjects/')).forEach(r => r.flush({ id: 7, name: 'Математика' }));
  }

  it('issues three parallel API requests on init', async () => {
    await setup();
    expect(httpMock.match(r => r.url === '/api/schedule/groups/5/lessons').length).toBeGreaterThanOrEqual(1);
    expect(httpMock.match('/api/attendance/reports/student/stats').length).toBeGreaterThanOrEqual(1);
    expect(httpMock.match('/api/academic/thresholds/resolve').length).toBeGreaterThanOrEqual(1);
    // Drain
    httpMock.match(r => r.url === '/api/schedule/groups/5/lessons').forEach(r => r.flush({ _embedded: { lessonResponseList: [] } }));
    httpMock.match('/api/attendance/reports/student/stats').forEach(r => r.flush({ subjects: [], overall: { total: 0, attended: 0, absent: 0, excused: 0, percentage: 0 } }));
    httpMock.match('/api/academic/thresholds/resolve').forEach(r => r.flush({ groupId: null, subjectId: null, percentage: 70, level: 'global' }));
  });

  it('renders "Добрый день" at 14:00', async () => {
    await setup();
    flushAll();
    expect(screen.getByText('Добрый день')).toBeTruthy();
  });

  it('renders "Сегодня пар нет" when today lesson list is empty', async () => {
    await setup();
    flushAll();
    expect(screen.getByText('Сегодня пар нет')).toBeTruthy();
  });

  it('renders one today-chip per lesson', async () => {
    const fixture = await setup();
    flushAll({ lessons: [lesson({ id: 1, startTime: '09:00:00' }), lesson({ id: 2, startTime: '10:45:00' })] });
    const chips = fixture.nativeElement.querySelectorAll('.dashboard-today-chip');
    expect(chips.length).toBe(2);
  });

  it('wires NextLessonCard with the ACTIVE lesson when one exists', async () => {
    await setup();
    flushAll({ lessons: [lesson({ id: 1, status: 'PLANNED', startTime: '08:00:00' }), lesson({ id: 2, status: 'ACTIVE', startTime: '14:00:00' })] });
    expect(screen.getByText('Текущая пара')).toBeTruthy();
  });

  it('wires NextLessonCard with earliest PLANNED lesson when no ACTIVE', async () => {
    await setup();
    flushAll({ lessons: [lesson({ id: 1, status: 'PLANNED', startTime: '12:00:00' }), lesson({ id: 2, status: 'PLANNED', startTime: '10:00:00' })] });
    expect(screen.getByText('Следующая пара')).toBeTruthy();
  });

  it('renders RedzoneWarning for subjects below threshold (70%)', async () => {
    await setup();
    flushAll({
      stats: {
        subjects: [
          { subjectId: 1, subjectName: 'Мат', total: 10, attended: 4, absent: 6, excused: 0, percentage: 42 },
          { subjectId: 2, subjectName: 'Физ', total: 10, attended: 8, absent: 2, excused: 0, percentage: 80 },
        ],
        overall: { total: 20, attended: 12, absent: 8, excused: 0, percentage: 60 },
      },
      threshold: { groupId: null, subjectId: null, percentage: 70, level: 'global' },
    });
    expect(screen.getByText('Мат')).toBeTruthy();
    expect(screen.getByText(/42%/)).toBeTruthy();
    expect(screen.queryByText('Физ')).toBeNull();
  });

  it('renders NO red-zone warnings when all subjects are above threshold', async () => {
    const fixture = await setup();
    flushAll({
      stats: {
        subjects: [{ subjectId: 1, subjectName: 'Мат', total: 10, attended: 9, absent: 1, excused: 0, percentage: 90 }],
        overall: { total: 10, attended: 9, absent: 1, excused: 0, percentage: 90 },
      },
      threshold: { groupId: null, subjectId: null, percentage: 70, level: 'global' },
    });
    const banners = fixture.nativeElement.querySelectorAll('app-redzone-warning');
    expect(banners.length).toBe(0);
  });

  it('renders error copy when any of the three requests fails', async () => {
    await setup();
    httpMock.expectOne(r => r.url === '/api/schedule/groups/5/lessons').flush(null, { status: 500, statusText: 'Server Error' });
    // forkJoin aborts — subsequent requests still need to be flushed OR the spec drains them
    httpMock.match('/api/attendance/reports/student/stats').forEach(r => r.flush({ subjects: [], overall: { total:0, attended:0, absent:0, excused:0, percentage:0 } }));
    httpMock.match('/api/academic/thresholds/resolve').forEach(r => r.flush({ groupId: null, subjectId: null, percentage: 70, level: 'global' }));
    expect(screen.getByText('Не удалось загрузить данные. Проверьте подключение и обновите страницу.')).toBeTruthy();
  });
});
```

Step 5 — run dashboard tests, then full suite + build:

```bash
cd frontends/web-panel && npm test -- --run src/app/features/student/dashboard && npm test && npm run build
```

All must exit 0.
  </action>
  <verify>
    <automated>cd frontends/web-panel && npm test -- --run src/app/features/student/dashboard 2>&1 | tail -40</automated>
  </verify>
  <acceptance_criteria>
    - student-dashboard.component.ts contains `export class StudentDashboardComponent` AND imports `NextLessonCardComponent` AND `RedzoneWarningComponent` AND `StudentApiService` AND `SubjectCacheService` AND `forkJoin` (from rxjs)
    - student-dashboard.component.ts contains `computed` and `signal` usage for `nextLesson`, `redZoneSubjects`, `greeting`
    - student-dashboard.component.ts contains the literals `Доброй ночи`, `Доброе утро`, `Добрый день`, `Добрый вечер`
    - student-dashboard.component.html contains the literals `Расписание на сегодня`, `Сегодня пар нет`, `Внимание к посещаемости`, `Студент`
    - student-dashboard.component.html contains `<app-next-lesson-card` AND `<app-redzone-warning`
    - student-dashboard.component.css contains `var(--accent-primary)` AND `var(--accent-warning)` AND `prefers-reduced-motion` AND NO hex color values
    - student-dashboard.component.spec.ts contains at least 8 `it(` test cases AND uses `provideHttpClient`, `provideHttpClientTesting`, `forkJoin`-flushing logic
    - `cd frontends/web-panel && npm test -- --run src/app/features/student/dashboard` exits 0
    - `cd frontends/web-panel && npm test` exits 0 (full regression)
    - `cd frontends/web-panel && npm run build` exits 0
  </acceptance_criteria>
  <done>
`/student/dashboard` is fully functional: greeting hero + live clock, horizontal today-chips row, NextLessonCard pointing to the right lesson, zero-or-more red-zone warnings derived by comparing `StudentStatsResponse.subjects[].percentage` against `ResolvedThresholdResponse.percentage`. Loading, empty, and error states all covered. Unit tests cover threshold filtering, greeting by hour, active vs planned selection, and error fallback. Full web-panel vitest suite green.
  </done>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| browser → 3× REST calls | authInterceptor bearer token; backend owns authorization |
| template → DOM | Interpolation of subject names, room, percentages (all server-provided) |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-51-21 | XSS | student-dashboard.component.html | mitigate | All subject names, percentages, time labels are rendered via `{{ }}` interpolation (HTML-escaped). No `[innerHTML]`. grep asserts zero `innerHTML\|bypassSecurityTrust` in dashboard sources. |
| T-51-22 | IDOR | getWeekLessons groupId | mitigate | `groupId` is read from `auth.currentUser().groupId` (parsed from JWT), not from route or URL. |
| T-51-23 | Information Disclosure | redzone warnings expose low attendance | accept | A student can only see their OWN stats via `/api/attendance/reports/student/stats` (backend enforces). No other student's data is exposed. |
| T-51-24 | Tampering | threshold display | mitigate | The threshold is resolved server-side via `/api/academic/thresholds/resolve`; the client only compares `percentage < threshold` for display. Tampering a local threshold value cannot let the student check-in out of bounds — that's a server-side decision. |
| T-51-25 | Spoofing | routerLink to /student/checkin | mitigate | The inline "Отметиться" link is a standard Angular RouterLink — navigation goes through the router's guard chain (`studentGuard`), so bypassing client-side state cannot escalate access. |
</threat_model>

<verification>
Automated gates that must pass:
1. `cd frontends/web-panel && npm test -- --run src/app/features/student/dashboard` exits 0 with all sub-component + component tests green
2. `cd frontends/web-panel && npm run build` exits 0
3. `cd frontends/web-panel && npm test` exits 0 (full regression)
4. `grep -rn "innerHTML\|bypassSecurityTrust" frontends/web-panel/src/app/features/student/dashboard` returns zero matches
5. `grep -nE "#[0-9a-fA-F]{3,8}" frontends/web-panel/src/app/features/student/dashboard/**/*.css` returns zero matches (token-only styling)
</verification>

<success_criteria>
- STU-WEB-01 satisfied: `/student/dashboard` renders a greeting hero, today's schedule summary, a current-or-next lesson card, and red-zone subject warnings when attendance is below the global threshold
- Visual consistency with admin dashboard (same hero/grid tokens) while delivering the student-specific content
- Three parallel API calls (schedule, stats, threshold) merged via `forkJoin`
- Full web-panel vitest suite green; prod build green
- NextLessonCard and RedzoneWarning are reusable presentational sub-components (useful for Phase 52 extensions)
</success_criteria>

<output>
After completion, create `.planning/phases/51-student-web-cabinet-shell-schedule-check-in/51-04-SUMMARY.md` capturing: files created, test count delta, any deviations from the UI-SPEC (e.g. if a sub-section was simplified), verification output.
</output>
