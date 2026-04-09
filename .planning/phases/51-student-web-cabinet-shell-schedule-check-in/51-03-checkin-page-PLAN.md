---
phase: 51
plan: 03
type: execute
wave: 2
depends_on: [51-01]
files_modified:
  - frontends/web-panel/src/app/features/student/checkin/student-checkin.component.ts
  - frontends/web-panel/src/app/features/student/checkin/student-checkin.component.html
  - frontends/web-panel/src/app/features/student/checkin/student-checkin.component.css
  - frontends/web-panel/src/app/features/student/checkin/student-checkin.component.spec.ts
  - frontends/web-panel/src/app/features/student/checkin/checkin-error-mapper.ts
  - frontends/web-panel/src/app/features/student/checkin/checkin-error-mapper.spec.ts
autonomous: true
requirements: [STU-WEB-03]

must_haves:
  truths:
    - "Visiting /student/checkin as a logged-in student with no ACTIVE lesson today renders a centered empty state: circle icon, 'Нет активной пары' heading, body text about 5-minute window"
    - "Visiting /student/checkin when today has an ACTIVE lesson renders the hero card: blinking live dot + 'Идёт сейчас' pill, big subject name (2xl), time + room meta, 'Отметиться' button, attendee counter"
    - "When no ACTIVE lesson exists but a PLANNED lesson is next today, the empty state also shows a 'Следующая пара' hint card with the next lesson's subject and start time"
    - "When no lessons at all remain today, the empty state falls back to 'На сегодня пар больше нет'"
    - "Clicking the 'Отметиться' button transitions the button label through 'Определяем координаты…' and 'Отправляем отметку…' states, calls navigator.geolocation.getCurrentPosition, and posts the {lat, lng} to /api/attendance/checkin"
    - "On HTTP 2xx success, the button swaps to a 'Вы отметились' confirmation badge; on HTTP 4xx/5xx the button re-enables and an inline error message appears under the button using the exact copy from UI-SPEC (403/404/409/422/429/generic)"
    - "On GPS denied, the button re-enables and the inline error shows 'Нет доступа к геолокации. Разрешите доступ в настройках браузера и попробуйте снова.'"
    - "The component connects to StudentStompService on ngOnInit and disconnects on ngOnDestroy; when a STOMP 'attendance.marked' payload arrives with user_id === currentUser.id AND lesson_id === activeLesson.id, the button swaps to the confirmation badge WITHOUT another HTTP call"
    - "The attendee counter increments by 1 for every incoming STOMP attendance.marked payload targeting the active lesson (regardless of which user)"
    - "The live-blinking dot uses a CSS @keyframes animation respecting prefers-reduced-motion"
    - "The primary CTA button has min-height 48px and aria-label 'Отметиться на текущей паре' / 'Вы уже отметились' depending on state"
    - "The attendee counter element has aria-live='polite'"
  artifacts:
    - path: "frontends/web-panel/src/app/features/student/checkin/student-checkin.component.ts"
      provides: "StudentCheckinComponent full implementation — GPS capture, HTTP submit, STOMP subscription, state machine signal"
      exports: ["StudentCheckinComponent", "CheckinState"]
    - path: "frontends/web-panel/src/app/features/student/checkin/checkin-error-mapper.ts"
      provides: "Pure function mapping HTTP status → Russian user-facing error message"
      exports: ["mapCheckinError"]
    - path: "frontends/web-panel/src/app/features/student/checkin/student-checkin.component.spec.ts"
      provides: "Component test covering idle, active, GPS pending, GPS denied, HTTP success, HTTP error, STOMP-marked auto-confirmation, attendee counter increment"
      contains: "StudentCheckinComponent"
  key_links:
    - from: "student-checkin.component.ts"
      to: "StudentApiService.checkin"
      via: "inject + subscribe on button click after GPS capture"
      pattern: "studentApi\\.checkin\\("
    - from: "student-checkin.component.ts"
      to: "StudentApiService.getWeekLessons"
      via: "inject + subscribe with dateFrom=today, dateTo=today"
      pattern: "studentApi\\.getWeekLessons\\("
    - from: "student-checkin.component.ts"
      to: "StudentStompService.connect / marked$"
      via: "inject + connect(groupId) in ngOnInit, subscribe to marked$ with takeUntilDestroyed"
      pattern: "stomp\\.connect\\(|stomp\\.marked\\$"
    - from: "student-checkin.component.ts"
      to: "navigator.geolocation.getCurrentPosition"
      via: "browser API call in onCheckinClick"
      pattern: "navigator\\.geolocation\\.getCurrentPosition"
---

<objective>
Build the `/student/checkin` page as a full functional replacement for the empty-shell committed in Plan 01. A centered, single-column hero with three visual states: idle/empty (no active lesson), active (the hero card with GPS-capture CTA), and confirmed (badge after check-in). STOMP wiring subscribes to `/topic/group/{groupId}` via StudentStompService and auto-transitions the state when an `attendance.marked` payload for the current user arrives.

Purpose: Deliver STU-WEB-03 — a student on desktop can see their current active pair, hit "Отметиться", and watch the state update in real-time.

Output: A working `/student/checkin` route with GPS capture, HTTP submit, real-time STOMP status updates, full loading/error state handling, and comprehensive unit tests covering every state transition.
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
@frontends/web-panel/src/app/features/student/shared/student-stomp.service.ts
@frontends/web-panel/src/app/features/student/checkin/student-checkin.component.ts
@frontends/web-panel/src/app/core/auth/auth.service.ts
@frontends/web-panel/src/styles.css
@frontends/web-panel/src/styles/tokens.css
@frontends/pwa/src/features/checkin/CheckInScreen.tsx
@frontends/pwa/src/features/checkin/CheckInButton.tsx
@frontends/pwa/src/features/checkin/StompProvider.tsx
@frontends/pwa/src/features/checkin/useStompCheckin.ts
@frontends/pwa/src/features/checkin/api.ts
@frontends/pwa/src/features/checkin/types.ts
@frontends/web-panel/src/app/features/admin/dashboard/admin-dashboard.component.spec.ts

<interfaces>
<!-- Contracts provided by Plan 01 (51-01). -->

From frontends/web-panel/src/app/features/student/shared/student-api.service.ts:
```typescript
@Injectable({ providedIn: 'root' })
export class StudentApiService {
  getWeekLessons(groupId: number, dateFrom: string, dateTo: string): Observable<LessonResponse[]>;
  checkin(coords: CheckinRequest): Observable<CheckinResponse>;
}
```

From frontends/web-panel/src/app/features/student/shared/student-stomp.service.ts:
```typescript
@Injectable({ providedIn: 'root' })
export class StudentStompService {
  readonly marked$: Observable<AttendanceMarkedPayload>;
  connect(groupId: number, getAccessToken: () => string | null): void;
  disconnect(): void;
}
```

From frontends/web-panel/src/app/features/student/shared/subject-cache.service.ts:
```typescript
@Injectable({ providedIn: 'root' })
export class SubjectCacheService {
  getName(subjectId: number | null | undefined): Observable<string>;
}
```

From frontends/web-panel/src/app/features/student/shared/student-schedule.types.ts:
```typescript
export interface LessonResponse { id: number; groupId: number; subjectId: number; ... status: LessonStatus; startTime: string; endTime: string; room: string; ... }
export interface CheckinRequest { lat: number; lng: number }
export interface CheckinResponse { status: AttendanceStatus; lessonId: number; timestamp: string }
export interface AttendanceMarkedPayload { lesson_id: number; user_id: number; group_id: number; status: string; marked_by: string }
```

From frontends/web-panel/src/app/core/auth/auth.service.ts:
```typescript
readonly currentUser: Signal<AuthUser | null>;     // { id, role, isHeadman, groupId }
readonly accessToken: Signal<string | null>;        // read by StudentStompService
```

CSS tokens referenced by UI-SPEC for this page (all from tokens.css):
- `--accent-primary`, `--accent-primary-contrast`, `--accent-danger`, `--border-accent`, `--glow-primary`, `--gradient-brand`
- `--bg-primary`, `--bg-secondary`, `--bg-elevated`, `--text-primary`, `--text-secondary`, `--text-muted`
- `--font-display`, `--font-heading`, `--font-sans`, `--font-mono`, `--text-xs`, `--text-sm`, `--text-base`, `--text-2xl`
- `--space-1..--space-8`, `--radius-full`, `--radius-lg`
- `--ease-out`, `--duration-base`
</interfaces>
</context>

<tasks>

<task type="auto" tdd="true">
  <name>Task 1: Error mapper + StudentCheckinComponent (component, template, styles)</name>
  <files>frontends/web-panel/src/app/features/student/checkin/checkin-error-mapper.ts, frontends/web-panel/src/app/features/student/checkin/checkin-error-mapper.spec.ts, frontends/web-panel/src/app/features/student/checkin/student-checkin.component.ts, frontends/web-panel/src/app/features/student/checkin/student-checkin.component.html, frontends/web-panel/src/app/features/student/checkin/student-checkin.component.css</files>
  <read_first>
    - frontends/web-panel/src/app/features/student/checkin/student-checkin.component.ts (empty shell from Plan 01)
    - frontends/web-panel/src/app/features/student/shared/student-schedule.types.ts
    - frontends/web-panel/src/app/features/student/shared/student-api.service.ts
    - frontends/web-panel/src/app/features/student/shared/student-stomp.service.ts
    - frontends/web-panel/src/app/features/student/shared/subject-cache.service.ts
    - frontends/pwa/src/features/checkin/api.ts (mapCheckinError reference — EXACT status codes and copy to copy)
    - frontends/pwa/src/features/checkin/CheckInScreen.tsx (state layout reference — Idle / ActiveLessonCard / NextLessonHint)
    - frontends/pwa/src/features/checkin/CheckInButton.tsx (GPS capture flow — navigator.geolocation.getCurrentPosition timeout/maximumAge)
    - frontends/pwa/src/features/checkin/useStompCheckin.ts (STOMP envelope parsing pattern)
    - .planning/phases/51-student-web-cabinet-shell-schedule-check-in/51-UI-SPEC.md lines 182-217 (StudentCheckinComponent state machine and visual contract) and lines 273-295 (copy)
    - frontends/web-panel/src/app/features/student/schedule/week-utils.ts (if Plan 02 has landed — reuse `formatLessonTime`; otherwise inline a local helper)
  </read_first>
  <behavior>
    # checkin-error-mapper.spec.ts — MUST cover (every assertion is a literal string match):
    - `mapCheckinError(403)` returns `'Геоотметка заблокирована преподавателем.'`
    - `mapCheckinError(404)` returns `'Активное занятие не найдено.'`
    - `mapCheckinError(409)` returns `'Вы уже отмечены на этом занятии.'`
    - `mapCheckinError(422)` returns `'Вы находитесь слишком далеко от кампуса. Геоотметка недоступна.'`
    - `mapCheckinError(429)` returns `'Слишком много попыток. Подождите минуту и попробуйте снова.'`
    - `mapCheckinError(500)` returns `'Не удалось отправить отметку. Попробуйте ещё раз.'`
    - `mapCheckinError(0)` (network unreachable) returns `'Не удалось отправить отметку. Попробуйте ещё раз.'`
  </behavior>
  <action>
Step 1 — create `frontends/web-panel/src/app/features/student/checkin/checkin-error-mapper.ts`:

```typescript
/**
 * Map HTTP status code → user-facing Russian error message per UI-SPEC §Copy.
 * Copy is taken verbatim from .planning/phases/51-*/51-UI-SPEC.md lines 273-295.
 */
export function mapCheckinError(status: number): string {
  switch (status) {
    case 403: return 'Геоотметка заблокирована преподавателем.';
    case 404: return 'Активное занятие не найдено.';
    case 409: return 'Вы уже отмечены на этом занятии.';
    case 422: return 'Вы находитесь слишком далеко от кампуса. Геоотметка недоступна.';
    case 429: return 'Слишком много попыток. Подождите минуту и попробуйте снова.';
    default:  return 'Не удалось отправить отметку. Попробуйте ещё раз.';
  }
}

export const GPS_DENIED_MESSAGE =
  'Нет доступа к геолокации. Разрешите доступ в настройках браузера и попробуйте снова.';
```

Step 2 — write `checkin-error-mapper.spec.ts` with the 7 assertions from the behavior block. Simple pure-function test.

Step 3 — fully implement `student-checkin.component.ts`. The state machine uses a signal of a discriminated union. Logic:

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
import { AsyncPipe } from '@angular/common';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { trigger, transition, style, animate } from '@angular/animations';
import { AuthService } from '../../../core/auth/auth.service';
import { StudentApiService } from '../shared/student-api.service';
import { SubjectCacheService } from '../shared/subject-cache.service';
import { StudentStompService } from '../shared/student-stomp.service';
import type { LessonResponse } from '../shared/student-schedule.types';
import { mapCheckinError, GPS_DENIED_MESSAGE } from './checkin-error-mapper';

export type CheckinState =
  | { kind: 'idle' }                           // no active lesson
  | { kind: 'ready' }                          // active lesson, CTA enabled
  | { kind: 'gps_pending' }                    // capturing GPS
  | { kind: 'submitting' }                     // POST /api/attendance/checkin in flight
  | { kind: 'confirmed' }                      // server 2xx OR STOMP attendance.marked
  | { kind: 'error'; message: string };        // error with user-facing copy

function todayDateString(): string {
  const d = new Date();
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
}

function formatHhMm(time: string): string { return time.slice(0, 5); }

@Component({
  selector: 'app-student-checkin',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [AsyncPipe],
  templateUrl: './student-checkin.component.html',
  styleUrl: './student-checkin.component.css',
  animations: [
    trigger('routeFade', [
      transition(':enter', [
        style({ opacity: 0, transform: 'translateY(8px)' }),
        animate('200ms cubic-bezier(0.16, 1, 0.3, 1)', style({ opacity: 1, transform: 'translateY(0)' })),
      ]),
    ]),
  ],
})
export class StudentCheckinComponent implements OnInit, OnDestroy {
  private readonly studentApi = inject(StudentApiService);
  private readonly subjectCache = inject(SubjectCacheService);
  private readonly stomp = inject(StudentStompService);
  private readonly auth = inject(AuthService);
  private readonly destroyRef = inject(DestroyRef);

  readonly lessons = signal<LessonResponse[]>([]);
  readonly loading = signal(false);
  readonly fetchError = signal<string | null>(null);
  readonly state = signal<CheckinState>({ kind: 'idle' });
  readonly attendeeCount = signal(0);

  readonly activeLesson = computed<LessonResponse | null>(() => {
    return this.lessons().find(l => l.status === 'ACTIVE') ?? null;
  });

  readonly nextPlannedLesson = computed<LessonResponse | null>(() => {
    if (this.activeLesson()) return null;
    return this.lessons()
      .filter(l => l.status === 'PLANNED')
      .sort((a, b) => a.startTime.localeCompare(b.startTime))[0] ?? null;
  });

  /** For template mono time rendering. */
  formatTime(time: string): string { return formatHhMm(time); }

  getSubjectName$(subjectId: number | undefined) {
    return this.subjectCache.getName(subjectId ?? null);
  }

  ngOnInit(): void {
    const user = this.auth.currentUser();
    const groupId = user?.groupId;
    if (!groupId) {
      this.fetchError.set('Не удалось определить группу пользователя.');
      return;
    }
    this.fetchToday(groupId);
    // STOMP connect
    this.stomp.connect(groupId, () => this.auth.accessToken());
    this.stomp.marked$
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((payload) => {
        const active = this.activeLesson();
        if (active && payload.lesson_id === active.id) {
          this.attendeeCount.update(n => n + 1);
          if (user && payload.user_id === user.id) {
            this.state.set({ kind: 'confirmed' });
          }
        }
      });
  }

  ngOnDestroy(): void {
    this.stomp.disconnect();
  }

  private fetchToday(groupId: number): void {
    const today = todayDateString();
    this.loading.set(true);
    this.studentApi.getWeekLessons(groupId, today, today).subscribe({
      next: (lessons) => {
        this.lessons.set(lessons);
        const active = lessons.find(l => l.status === 'ACTIVE');
        this.state.set(active ? { kind: 'ready' } : { kind: 'idle' });
        this.loading.set(false);
      },
      error: () => {
        this.fetchError.set('Не удалось загрузить данные. Проверьте подключение и обновите страницу.');
        this.loading.set(false);
      },
    });
  }

  onCheckinClick(): void {
    const current = this.state();
    if (current.kind !== 'ready' && current.kind !== 'error') return;
    const active = this.activeLesson();
    if (!active) return;

    this.state.set({ kind: 'gps_pending' });

    if (!navigator.geolocation) {
      this.state.set({ kind: 'error', message: GPS_DENIED_MESSAGE });
      return;
    }

    navigator.geolocation.getCurrentPosition(
      (position) => {
        this.state.set({ kind: 'submitting' });
        this.studentApi
          .checkin({ lat: position.coords.latitude, lng: position.coords.longitude })
          .subscribe({
            next: () => this.state.set({ kind: 'confirmed' }),
            error: (err: unknown) => {
              const status = (err as { status?: number } | null)?.status ?? 0;
              this.state.set({ kind: 'error', message: mapCheckinError(status) });
            },
          });
      },
      () => {
        this.state.set({ kind: 'error', message: GPS_DENIED_MESSAGE });
      },
      { timeout: 10000, maximumAge: 30000 },
    );
  }

  // Template helpers
  isIdle(): boolean { return this.state().kind === 'idle'; }
  isReady(): boolean { return this.state().kind === 'ready'; }
  isGpsPending(): boolean { return this.state().kind === 'gps_pending'; }
  isSubmitting(): boolean { return this.state().kind === 'submitting'; }
  isConfirmed(): boolean { return this.state().kind === 'confirmed'; }
  isError(): boolean { return this.state().kind === 'error'; }
  errorMessage(): string | null {
    const s = this.state();
    return s.kind === 'error' ? s.message : null;
  }
  buttonLabel(): string {
    switch (this.state().kind) {
      case 'gps_pending': return 'Определяем координаты…';
      case 'submitting':  return 'Отправляем отметку…';
      case 'confirmed':   return 'Вы отметились';
      default:            return 'Отметиться';
    }
  }
  buttonAriaLabel(): string {
    return this.isConfirmed() ? 'Вы уже отметились' : 'Отметиться на текущей паре';
  }
  buttonDisabled(): boolean {
    return this.isGpsPending() || this.isSubmitting() || this.isConfirmed();
  }
}
```

Step 4 — write `student-checkin.component.html`. All copy is from UI-SPEC lines 273-295:

```html
<section class="checkin" [@routeFade]>
  <div class="checkin__frame">
    @if (loading()) {
      <div class="checkin__skeleton" aria-busy="true" aria-label="Загрузка…"></div>
    } @else if (fetchError()) {
      <p class="checkin__fetch-error" role="alert">{{ fetchError() }}</p>
    } @else if (activeLesson(); as active) {
      <!-- Active lesson hero -->
      <div class="checkin__hero" data-testid="checkin-active-hero">
        <div class="checkin__live" aria-hidden="true">
          <span class="checkin__live-dot"></span>
          <span class="checkin__live-label">Идёт сейчас</span>
        </div>

        <h2 class="checkin__subject">{{ (getSubjectName$(active.subjectId) | async) ?? 'Предмет' }}</h2>

        <p class="checkin__meta">
          <i class="ph-fill ph-clock" aria-hidden="true"></i>
          <span class="checkin__meta-time">{{ formatTime(active.startTime) }}–{{ formatTime(active.endTime) }}</span>
          <span class="checkin__meta-sep" aria-hidden="true"></span>
          <i class="ph-fill ph-map-pin" aria-hidden="true"></i>
          <span>Ауд. {{ active.room }}</span>
        </p>

        <div class="checkin__cta-wrap">
          @if (isConfirmed()) {
            <div class="checkin__confirmed" role="status">
              <i class="ph-fill ph-check-circle" aria-hidden="true"></i>
              Вы отметились
            </div>
          } @else {
            <button
              type="button"
              class="checkin__cta"
              [attr.aria-label]="buttonAriaLabel()"
              [attr.aria-busy]="isGpsPending() || isSubmitting()"
              [disabled]="buttonDisabled()"
              (click)="onCheckinClick()"
            >
              {{ buttonLabel() }}
            </button>
          }

          <p class="checkin__counter" aria-live="polite">
            <i class="ph-fill ph-users" aria-hidden="true"></i>
            {{ attendeeCount() }} отметилось
          </p>
        </div>

        @if (isError()) {
          <p class="checkin__error" role="alert">{{ errorMessage() }}</p>
        }
      </div>
    } @else {
      <!-- Idle empty state -->
      <div class="checkin__empty" data-testid="checkin-empty">
        <div class="checkin__empty-icon" aria-hidden="true">
          <i class="ph-duotone ph-clock"></i>
        </div>
        <h2 class="checkin__empty-title">Нет активной пары</h2>
        <p class="checkin__empty-text">Отметка станет доступна за 5 минут до начала следующего занятия.</p>

        @if (nextPlannedLesson(); as next) {
          <div class="checkin__next-hint" data-testid="checkin-next-hint">
            <span class="checkin__next-label">Следующая пара</span>
            <span class="checkin__next-subject">{{ (getSubjectName$(next.subjectId) | async) ?? 'Предмет' }}</span>
            <span class="checkin__next-time">начало в {{ formatTime(next.startTime) }}</span>
          </div>
        } @else {
          <p class="checkin__no-more">На сегодня пар больше нет</p>
        }
      </div>
    }
  </div>
</section>
```

Step 5 — write `student-checkin.component.css`. All tokens — no hex:

```css
:host { display: block; min-height: 100%; }

.checkin {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100%;
  padding: var(--space-8) var(--space-5);
}

.checkin__frame {
  width: 100%;
  max-width: 480px;
  display: flex;
  justify-content: center;
  align-items: center;
}

.checkin__skeleton {
  width: 100%;
  height: 200px;
  border-radius: var(--radius-lg);
  background: linear-gradient(
    90deg,
    var(--bg-elevated) 25%,
    color-mix(in oklab, var(--text-primary) 8%, transparent) 50%,
    var(--bg-elevated) 75%
  );
  background-size: 200% 100%;
  animation: checkin-shimmer 1.5s linear infinite;
}
@keyframes checkin-shimmer {
  0% { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}

.checkin__fetch-error {
  padding: var(--space-4);
  border-radius: var(--radius-md);
  background: color-mix(in srgb, var(--accent-danger) 12%, transparent);
  border: 1px solid color-mix(in srgb, var(--accent-danger) 28%, transparent);
  color: var(--accent-danger);
  font-size: var(--text-sm);
}

/* Active hero */
.checkin__hero {
  width: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--space-5);
  padding: var(--space-6);
  border-radius: var(--radius-lg);
  background: var(--bg-secondary);
  border: 1px solid var(--border-accent);
  box-shadow: var(--glow-primary);
  text-align: center;
}

.checkin__live {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  padding: 4px 12px;
  border-radius: var(--radius-full);
  background: color-mix(in oklab, var(--accent-primary) 12%, transparent);
  border: 1px solid var(--border-accent);
  color: var(--accent-primary);
  font-size: var(--text-xs);
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: var(--tracking-wide);
}
.checkin__live-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--accent-primary);
  box-shadow: 0 0 6px var(--accent-primary);
  animation: checkin-live-blink 1.6s ease-in-out infinite;
}
@keyframes checkin-live-blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.35; }
}

.checkin__subject {
  margin: 0;
  font-family: var(--font-display);
  font-size: var(--text-2xl);
  font-weight: 600;
  line-height: var(--leading-display);
  color: var(--text-primary);
}

.checkin__meta {
  margin: 0;
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  color: var(--text-secondary);
  font-size: var(--text-sm);
  font-variant-numeric: tabular-nums;
}
.checkin__meta i { font-size: 14px; }
.checkin__meta-time { font-family: var(--font-mono); font-weight: 600; }
.checkin__meta-sep {
  width: 4px;
  height: 4px;
  border-radius: 50%;
  background: var(--text-muted);
  display: inline-block;
}

.checkin__cta-wrap {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--space-3);
  padding-top: var(--space-2);
}

.checkin__cta {
  min-height: 48px;
  min-width: 180px;
  padding: 0 var(--space-6);
  border-radius: var(--radius-full);
  border: 0;
  background: var(--accent-primary);
  color: var(--accent-primary-contrast);
  font-family: var(--font-heading);
  font-size: var(--text-sm);
  font-weight: 600;
  cursor: pointer;
  transition:
    filter var(--duration-base) var(--ease-out),
    transform var(--duration-base) var(--ease-out),
    opacity var(--duration-base) var(--ease-out);
}
.checkin__cta:hover:not(:disabled) { filter: brightness(1.08); }
.checkin__cta:active:not(:disabled) { transform: scale(0.97); }
.checkin__cta:disabled { opacity: 0.55; cursor: not-allowed; }
.checkin__cta:focus-visible { outline: 2px solid var(--accent-primary); outline-offset: 2px; }

.checkin__confirmed {
  min-height: 48px;
  padding: 0 var(--space-5);
  border-radius: var(--radius-full);
  background: color-mix(in oklab, var(--accent-primary) 14%, transparent);
  border: 1px solid var(--border-accent);
  color: var(--accent-primary);
  font-family: var(--font-heading);
  font-size: var(--text-sm);
  font-weight: 600;
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
}
.checkin__confirmed i { font-size: 18px; }

.checkin__counter {
  margin: 0;
  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
  font-size: var(--text-xs);
  color: var(--text-muted);
  font-variant-numeric: tabular-nums;
}
.checkin__counter i { font-size: 12px; }

.checkin__error {
  margin: 0;
  color: var(--accent-danger);
  font-size: var(--text-sm);
  text-align: center;
}

/* Empty state */
.checkin__empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--space-5);
  text-align: center;
  padding: var(--space-7);
}
.checkin__empty-icon {
  width: 80px;
  height: 80px;
  border-radius: var(--radius-full);
  background: var(--bg-secondary);
  border: 1px solid var(--border-subtle);
  color: var(--text-muted);
  display: grid;
  place-items: center;
}
.checkin__empty-icon i { font-size: 36px; }
.checkin__empty-title {
  margin: 0;
  font-family: var(--font-display);
  font-size: var(--text-2xl);
  font-weight: 600;
  color: var(--text-primary);
}
.checkin__empty-text {
  margin: 0;
  font-size: var(--text-sm);
  color: var(--text-secondary);
  max-width: 300px;
}
.checkin__next-hint {
  margin-top: var(--space-3);
  padding: var(--space-4) var(--space-5);
  background: var(--bg-secondary);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--space-1);
}
.checkin__next-label {
  font-size: var(--text-xs);
  color: var(--text-muted);
  text-transform: uppercase;
  letter-spacing: var(--tracking-wide);
  font-weight: 600;
}
.checkin__next-subject {
  font-family: var(--font-heading);
  font-size: var(--text-base);
  font-weight: 600;
  color: var(--text-primary);
}
.checkin__next-time {
  font-family: var(--font-mono);
  font-size: var(--text-sm);
  color: var(--text-secondary);
  font-variant-numeric: tabular-nums;
}
.checkin__no-more {
  margin: 0;
  font-size: var(--text-xs);
  color: var(--text-muted);
}

@media (prefers-reduced-motion: reduce) {
  .checkin__live-dot { animation: none; }
  .checkin__skeleton { animation: none; }
  .checkin__cta { transition: none; }
}
```

Run a quick TypeScript compile check to surface syntax errors before moving to the spec:

```bash
cd frontends/web-panel && npx ng build --configuration development 2>&1 | tail -20
```

Must exit 0.
  </action>
  <verify>
    <automated>cd frontends/web-panel && npm test -- --run src/app/features/student/checkin/checkin-error-mapper 2>&1 | tail -20</automated>
  </verify>
  <acceptance_criteria>
    - frontends/web-panel/src/app/features/student/checkin/checkin-error-mapper.ts contains `export function mapCheckinError` AND each of the literal strings: `Геоотметка заблокирована преподавателем.`, `Активное занятие не найдено.`, `Вы уже отмечены на этом занятии.`, `Вы находитесь слишком далеко от кампуса.`, `Слишком много попыток.`, `Не удалось отправить отметку.`, `Нет доступа к геолокации.`
    - `cd frontends/web-panel && npm test -- --run src/app/features/student/checkin/checkin-error-mapper` exits 0 with all 7 assertions passing
    - student-checkin.component.ts contains `export class StudentCheckinComponent` AND `export type CheckinState` AND `standalone: true` AND `selector: 'app-student-checkin'`
    - student-checkin.component.ts contains `navigator.geolocation.getCurrentPosition` AND `studentApi.checkin(` AND `stomp.connect(` AND `stomp.marked$`
    - student-checkin.component.ts contains `takeUntilDestroyed(this.destroyRef)`
    - student-checkin.component.ts contains `ngOnDestroy` that calls `this.stomp.disconnect()`
    - student-checkin.component.html contains the literals: `Идёт сейчас`, `Отметиться`, `Определяем координаты…`, `Отправляем отметку…`, `Вы отметились`, `Нет активной пары`, `Отметка станет доступна за 5 минут`, `Следующая пара`, `На сегодня пар больше нет`, `отметилось`
    - student-checkin.component.html contains `aria-live="polite"` AND `[attr.aria-label]="buttonAriaLabel()"`
    - student-checkin.component.css contains `var(--accent-primary)` AND `var(--border-accent)` AND `var(--glow-primary)` AND `min-height: 48px` AND `prefers-reduced-motion` AND NO hex color values (grep `#[0-9a-fA-F]{3,8}` returns zero matches)
    - `cd frontends/web-panel && npm run build` exits 0
  </acceptance_criteria>
  <done>
The component, template, and styles compile cleanly. The pure error mapper is unit-tested and passes. The hero card and empty state render per UI-SPEC with all required copy, aria attributes, and token-only styling.
  </done>
</task>

<task type="auto" tdd="true">
  <name>Task 2: StudentCheckinComponent spec — GPS, HTTP, STOMP, all state transitions</name>
  <files>frontends/web-panel/src/app/features/student/checkin/student-checkin.component.spec.ts</files>
  <read_first>
    - frontends/web-panel/src/app/features/student/checkin/student-checkin.component.ts (just committed in Task 1)
    - frontends/web-panel/src/app/features/student/shared/student-stomp.service.ts (public surface to mock)
    - frontends/web-panel/src/app/features/student/shared/student-api.service.ts (public surface to mock)
    - frontends/web-panel/src/app/features/admin/dashboard/admin-dashboard.component.spec.ts (HttpTestingController reference)
    - frontends/web-panel/src/app/layout/sidebar/sidebar.component.spec.ts (AuthService mock with signal/computed pattern)
  </read_first>
  <behavior>
    # student-checkin.component.spec.ts — MUST cover, each test is independent:
    1. "renders empty state when no ACTIVE lesson exists today" — flush an empty lesson list → assert `Нет активной пары` is in the document AND `На сегодня пар больше нет` is also present (no planned lesson)
    2. "renders empty state + 'Следующая пара' hint when only PLANNED lessons exist" — flush one PLANNED lesson → assert `Нет активной пары` AND `Следующая пара` AND the subject name are all present
    3. "renders active hero + 'Отметиться' button when an ACTIVE lesson exists" — flush one ACTIVE lesson → assert the subject name, `Идёт сейчас`, `Отметиться` button by role, `HH:mm` time label, `Ауд. 404`
    4. "clicking Отметиться requests GPS and POSTs to /api/attendance/checkin with {lat, lng}" — stub `navigator.geolocation.getCurrentPosition` with `Object.defineProperty(navigator, 'geolocation', { value: { getCurrentPosition: (ok) => ok({ coords: { latitude: 55.1, longitude: 37.2 }}) }, configurable: true })` → click the CTA → assert `HttpTestingController.expectOne(r => r.url === '/api/attendance/checkin' && r.method === 'POST')` receives body `{lat: 55.1, lng: 37.2}`
    5. "on 2xx success the CTA swaps to confirmation badge" — flush the checkin POST with 201 success body → assert `Вы отметились` is in the document
    6. "on 409 error the inline error shows 'Вы уже отмечены на этом занятии.'" — flush the checkin POST with 409 → assert the error copy
    7. "on GPS denied the inline error shows the GPS-denied copy" — stub `getCurrentPosition` with `(_ok, fail) => fail({ code: 1 })` → click the CTA → assert 'Нет доступа к геолокации.' copy
    8. "STOMP attendance.marked for the current user auto-transitions to confirmed WITHOUT an HTTP call" — mock `StudentStompService` so that `marked$` is a controllable Subject → render with an ACTIVE lesson → emit `{ lesson_id: 42, user_id: 3, group_id: 5, status: 'present', marked_by: 'self' }` → assert `Вы отметились` appears AND `httpMock.expectNone(r => r.url === '/api/attendance/checkin')`
    9. "STOMP attendance.marked for a DIFFERENT user increments the counter but does NOT confirm" — emit `{ lesson_id: 42, user_id: 99, ... }` → assert the counter text updates (`1 отметилось`) AND the button is still 'Отметиться' (not confirmed)
    10. "ngOnInit calls StudentStompService.connect(groupId=5, getAccessToken)" — assert the mock .connect was called with 5
    11. "ngOnDestroy calls StudentStompService.disconnect" — after destroying the fixture, assert the mock .disconnect was called
  </behavior>
  <action>
Create `student-checkin.component.spec.ts`. Use the following test harness pattern:

```typescript
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { signal, computed } from '@angular/core';
import { Subject } from 'rxjs';
import { render, screen } from '@testing-library/angular';
import userEvent from '@testing-library/user-event';
import { StudentCheckinComponent } from './student-checkin.component';
import { AuthService, AuthUser } from '../../../core/auth/auth.service';
import { StudentStompService } from '../shared/student-stomp.service';
import type { AttendanceMarkedPayload, LessonResponse } from '../shared/student-schedule.types';

const STUDENT_USER: AuthUser = { id: 3, role: 'STUDENT', isHeadman: false, groupId: 5 };

function makeAuthMock(user: AuthUser | null = STUDENT_USER) {
  const accessToken = signal<string | null>(user ? 'fake-token' : null);
  return {
    currentUser: computed(() => user),
    isAuthenticated: computed(() => user !== null),
    accessToken: accessToken.asReadonly(),
    resolveDashboardFor: vi.fn(),
  };
}

function makeStompMock() {
  const marked$ = new Subject<AttendanceMarkedPayload>();
  return {
    marked$: marked$.asObservable(),
    connect: vi.fn(),
    disconnect: vi.fn(),
    __emit(payload: AttendanceMarkedPayload) { marked$.next(payload); },
  };
}

function lesson(over: Partial<LessonResponse>): LessonResponse {
  return {
    id: 42, scheduleItemId: 1, groupId: 5, subjectId: 7, teacherId: 11,
    date: '2026-04-09', status: 'PLANNED', dayOfWeek: 4, lessonNumber: 2,
    startTime: '09:00:00', endTime: '10:30:00',
    weekType: 'BOTH', room: '404', geoBlocked: false, cancelReason: null,
    createdAt: '2026-04-01T00:00:00Z',
    ...over,
  };
}

// Stub navigator.geolocation for all tests
function stubGeo(success: boolean, position = { coords: { latitude: 55.1, longitude: 37.2 } }) {
  const impl = success
    ? (ok: PositionCallback) => ok(position as GeolocationPosition)
    : (_ok: PositionCallback, fail: PositionErrorCallback) =>
        fail({ code: 1, message: 'denied', PERMISSION_DENIED: 1, POSITION_UNAVAILABLE: 2, TIMEOUT: 3 } as GeolocationPositionError);
  Object.defineProperty(navigator, 'geolocation', {
    configurable: true,
    value: { getCurrentPosition: vi.fn().mockImplementation(impl) },
  });
}

describe('StudentCheckinComponent', () => {
  let httpMock: HttpTestingController;
  let stompMock: ReturnType<typeof makeStompMock>;

  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date(2026, 3, 9, 12, 0, 0));
  });
  afterEach(() => {
    vi.useRealTimers();
    httpMock?.verify();
  });

  async function setup(user: AuthUser | null = STUDENT_USER) {
    stompMock = makeStompMock();
    const fixture = await render(StudentCheckinComponent, {
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        provideNoopAnimations(),
        { provide: AuthService, useValue: makeAuthMock(user) },
        { provide: StudentStompService, useValue: stompMock },
      ],
    });
    httpMock = TestBed.inject(HttpTestingController);
    return fixture;
  }

  it('renders "Нет активной пары" + "На сегодня пар больше нет" when no lessons exist', async () => {
    await setup();
    httpMock
      .expectOne(r => r.url === '/api/schedule/groups/5/lessons')
      .flush({ _embedded: { lessonResponseList: [] } });
    expect(screen.getByText('Нет активной пары')).toBeTruthy();
    expect(screen.getByText('На сегодня пар больше нет')).toBeTruthy();
  });

  it('renders "Следующая пара" hint when only a PLANNED lesson exists', async () => {
    await setup();
    httpMock
      .expectOne(r => r.url === '/api/schedule/groups/5/lessons')
      .flush({ _embedded: { lessonResponseList: [lesson({ status: 'PLANNED' })] } });
    httpMock.match(r => r.url === '/api/academic/subjects/7').forEach(r =>
      r.flush({ id: 7, name: 'Математика' }),
    );
    expect(screen.getByText('Нет активной пары')).toBeTruthy();
    expect(screen.getByText('Следующая пара')).toBeTruthy();
  });

  it('renders active hero with "Отметиться" CTA when an ACTIVE lesson exists', async () => {
    await setup();
    httpMock
      .expectOne(r => r.url === '/api/schedule/groups/5/lessons')
      .flush({ _embedded: { lessonResponseList: [lesson({ status: 'ACTIVE' })] } });
    httpMock.match(r => r.url === '/api/academic/subjects/7').forEach(r =>
      r.flush({ id: 7, name: 'Математика' }),
    );
    expect(screen.getByText('Идёт сейчас')).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Отметиться на текущей паре' })).toBeTruthy();
    expect(screen.getByText(/09:00/)).toBeTruthy();
    expect(screen.getByText(/Ауд.\s*404/)).toBeTruthy();
  });

  it('POSTs to /api/attendance/checkin with GPS coords on button click', async () => {
    stubGeo(true);
    await setup();
    httpMock
      .expectOne(r => r.url === '/api/schedule/groups/5/lessons')
      .flush({ _embedded: { lessonResponseList: [lesson({ status: 'ACTIVE' })] } });
    httpMock.match(r => r.url === '/api/academic/subjects/7').forEach(r =>
      r.flush({ id: 7, name: 'Математика' }),
    );

    const btn = screen.getByRole('button', { name: 'Отметиться на текущей паре' });
    await userEvent.setup({ advanceTimers: vi.advanceTimersByTime }).click(btn);

    const postReq = httpMock.expectOne(r => r.url === '/api/attendance/checkin' && r.method === 'POST');
    expect(postReq.request.body).toEqual({ lat: 55.1, lng: 37.2 });
    postReq.flush({ status: 'present', lessonId: 42, timestamp: '2026-04-09T09:05:00Z' });

    expect(screen.getByText('Вы отметились')).toBeTruthy();
  });

  it('shows "Вы уже отмечены" on HTTP 409', async () => {
    stubGeo(true);
    await setup();
    httpMock.expectOne(r => r.url === '/api/schedule/groups/5/lessons')
      .flush({ _embedded: { lessonResponseList: [lesson({ status: 'ACTIVE' })] } });
    httpMock.match(r => r.url === '/api/academic/subjects/7').forEach(r =>
      r.flush({ id: 7, name: 'Математика' }),
    );
    await userEvent.setup({ advanceTimers: vi.advanceTimersByTime }).click(
      screen.getByRole('button', { name: 'Отметиться на текущей паре' }),
    );
    httpMock.expectOne('/api/attendance/checkin').flush(null, { status: 409, statusText: 'Conflict' });
    expect(screen.getByText('Вы уже отмечены на этом занятии.')).toBeTruthy();
  });

  it('shows GPS denied copy when permission is rejected', async () => {
    stubGeo(false);
    await setup();
    httpMock.expectOne(r => r.url === '/api/schedule/groups/5/lessons')
      .flush({ _embedded: { lessonResponseList: [lesson({ status: 'ACTIVE' })] } });
    httpMock.match(r => r.url === '/api/academic/subjects/7').forEach(r =>
      r.flush({ id: 7, name: 'Математика' }),
    );
    await userEvent.setup({ advanceTimers: vi.advanceTimersByTime }).click(
      screen.getByRole('button', { name: 'Отметиться на текущей паре' }),
    );
    expect(screen.getByText(/Нет доступа к геолокации/)).toBeTruthy();
  });

  it('STOMP attendance.marked for current user auto-confirms WITHOUT extra HTTP call', async () => {
    await setup();
    httpMock.expectOne(r => r.url === '/api/schedule/groups/5/lessons')
      .flush({ _embedded: { lessonResponseList: [lesson({ status: 'ACTIVE' })] } });
    httpMock.match(r => r.url === '/api/academic/subjects/7').forEach(r =>
      r.flush({ id: 7, name: 'Математика' }),
    );

    stompMock.__emit({ lesson_id: 42, user_id: 3, group_id: 5, status: 'present', marked_by: 'self' });

    expect(screen.getByText('Вы отметились')).toBeTruthy();
    httpMock.expectNone(r => r.url === '/api/attendance/checkin');
  });

  it('STOMP attendance.marked for another user increments the counter but does NOT confirm', async () => {
    await setup();
    httpMock.expectOne(r => r.url === '/api/schedule/groups/5/lessons')
      .flush({ _embedded: { lessonResponseList: [lesson({ status: 'ACTIVE' })] } });
    httpMock.match(r => r.url === '/api/academic/subjects/7').forEach(r =>
      r.flush({ id: 7, name: 'Математика' }),
    );

    stompMock.__emit({ lesson_id: 42, user_id: 99, group_id: 5, status: 'present', marked_by: 'self' });

    expect(screen.getByText(/1 отметилось/)).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Отметиться на текущей паре' })).toBeTruthy();
  });

  it('ngOnInit calls StudentStompService.connect with the user groupId', async () => {
    await setup();
    httpMock.expectOne(r => r.url === '/api/schedule/groups/5/lessons').flush({ _embedded: { lessonResponseList: [] } });
    expect(stompMock.connect).toHaveBeenCalled();
    expect(stompMock.connect.mock.calls[0][0]).toBe(5);
  });

  it('ngOnDestroy calls StudentStompService.disconnect', async () => {
    const fixture = await setup();
    httpMock.expectOne(r => r.url === '/api/schedule/groups/5/lessons').flush({ _embedded: { lessonResponseList: [] } });
    fixture.destroy();
    expect(stompMock.disconnect).toHaveBeenCalled();
  });
});
```

Step 2 — run the checkin spec in isolation, then run the full suite + build:

```bash
cd frontends/web-panel && npm test -- --run src/app/features/student/checkin && npm test && npm run build
```

All three must exit 0.
  </action>
  <verify>
    <automated>cd frontends/web-panel && npm test -- --run src/app/features/student/checkin 2>&1 | tail -40</automated>
  </verify>
  <acceptance_criteria>
    - student-checkin.component.spec.ts contains all of: `provideHttpClient`, `provideHttpClientTesting`, `provideNoopAnimations`, `HttpTestingController`, `navigator`, `geolocation`, `getCurrentPosition`, `stompMock.__emit`, `expectNone`
    - student-checkin.component.spec.ts contains at least 10 `it(` test cases
    - `cd frontends/web-panel && npm test -- --run src/app/features/student/checkin` exits 0 with all checkin tests green
    - `cd frontends/web-panel && npm test` exits 0 (full regression — no prior test regressed)
    - `cd frontends/web-panel && npm run build` exits 0
    - grep `console.log` in student-checkin.component.ts returns zero matches (no token/coord logging)
  </acceptance_criteria>
  <done>
Every state transition of the check-in state machine is covered by a dedicated test. The component correctly handles idle/active/GPS-pending/submitting/confirmed/error states, triggers STOMP connect/disconnect on mount/unmount, auto-confirms on incoming attendance.marked for the current user, and never double-charges HTTP. Full web-panel vitest suite green, prod build green.
  </done>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| browser → `/api/attendance/checkin` | JWT bearer attached by authInterceptor; backend validates caller identity, active lesson, geofence |
| browser → `/api/ws` (STOMP) | Access token in `?token=` query param validated at upgrade handshake (Phase 20 JwtHandshakeInterceptor) |
| browser → `navigator.geolocation` | Browser-gated user permission; coordinates captured only on explicit click |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-51-13 | Information Disclosure | GPS coordinates | mitigate | `navigator.geolocation.getCurrentPosition` is invoked ONLY inside `onCheckinClick` (never on mount, never automatically). Coordinates are used exactly once — passed to `StudentApiService.checkin(...)` — and never logged, never stored in signals beyond the in-flight mutation request. grep asserts no `console.log.*lat|console.log.*lng|localStorage.*lat|localStorage.*lng` in checkin sources. |
| T-51-14 | Tampering | Check-in request body | mitigate | The POST body is `{lat, lng}` only. The client does not choose `lesson_id` or `user_id` — the backend resolves those from the JWT subject + current time. A tampered body cannot check-in a different student or a different lesson. |
| T-51-15 | Spoofing | STOMP user_id check | mitigate | The component compares `payload.user_id === currentUser.id` where `currentUser` is parsed from the JWT inside `AuthService` (not from client input). A crafted STOMP message cannot trick the UI into auto-confirming for a different user. The backend broker is trusted (IMP-01 SubscriptionAuthInterceptor enforces topic isolation per WebSocketConfig.java:51). |
| T-51-16 | XSS | subjectName, room rendering | mitigate | All interpolation uses `{{ }}` which Angular HTML-escapes. No `[innerHTML]` anywhere. grep asserts zero `innerHTML|bypassSecurityTrust` in the checkin sources. |
| T-51-17 | IDOR | groupId in STOMP connect | mitigate | `groupId` is read from `auth.currentUser().groupId` (JWT claim), NEVER from route params or URL. The backend SubscriptionAuthInterceptor independently verifies the subscription destination matches the connecting user's group. |
| T-51-18 | Denial of Service | CTA spamming | mitigate | `buttonDisabled()` returns true while `gps_pending`, `submitting`, or `confirmed`. The button cannot be clicked again during an in-flight request. |
| T-51-19 | Repudiation | checkin audit | accept | Server already logs every attempt (Phase 17). No client-side audit needed. |
| T-51-20 | Information Disclosure | access token exposure via StudentStompService | mitigate | See Plan 01 T-51-01. StudentCheckinComponent passes `() => this.auth.accessToken()` as the token factory — the component itself never reads the token value directly, never logs it, never writes it to localStorage or template. |
</threat_model>

<verification>
Automated gates that must pass:
1. `cd frontends/web-panel && npm test -- --run src/app/features/student/checkin` exits 0 with at least 10 tests passing (Task 2)
2. `cd frontends/web-panel && npm run build` exits 0
3. `cd frontends/web-panel && npm test` exits 0 (full regression)
4. `grep -rn "console\.log.*(lat|lng|token|accessToken)" frontends/web-panel/src/app/features/student/checkin` returns zero matches
5. `grep -rn "innerHTML\|bypassSecurityTrust" frontends/web-panel/src/app/features/student/checkin` returns zero matches
6. `grep -nE "#[0-9a-fA-F]{3,8}" frontends/web-panel/src/app/features/student/checkin/*.css` returns zero matches
</verification>

<success_criteria>
- STU-WEB-03 satisfied: `/student/checkin` shows the active lesson card with a functional "Отметиться" CTA that captures GPS and posts to `/api/attendance/checkin`
- State machine covers idle, ready, gps_pending, submitting, confirmed, and error states with the correct copy and aria attributes
- STOMP `attendance.marked` events for the current user auto-transition to confirmed; events for other users only increment the attendee counter
- Empty state shows "Нет активной пары" + optional "Следующая пара" hint + "На сегодня пар больше нет" fallback
- All animations (live-blinking dot, button transitions) respect `prefers-reduced-motion`
- Full web-panel vitest suite green, prod build green
- No secrets or PII leaked via console, localStorage, or URL
</success_criteria>

<output>
After completion, create `.planning/phases/51-student-web-cabinet-shell-schedule-check-in/51-03-SUMMARY.md` capturing: files created, test count delta, any deviations from the UI-SPEC, verification output.
</output>
