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
import {
  mapCheckinError,
  mapRequestHeadmanError,
  GPS_DENIED_MESSAGE,
} from './checkin-error-mapper';
import { formatLoadError } from '../shared/format-load-error';

/**
 * Discriminated union capturing the six visible states of the check-in
 * CTA. Keep this exported — downstream tests assert on `state.kind`.
 */
export type CheckinState =
  | { kind: 'idle' }
  | { kind: 'ready' }
  | { kind: 'gps_pending' }
  | { kind: 'submitting' }
  | { kind: 'confirmed' }
  | { kind: 'error'; message: string }
  | { kind: 'request_submitting' }
  | { kind: 'request_pending' }
  | { kind: 'request_error'; message: string };

/** Local YYYY-MM-DD (avoids UTC-rollover off-by-one vs `new Date().toISOString()`). */
function todayDateString(): string {
  const d = new Date();
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
}

/** Trim `HH:mm:ss` → `HH:mm`. */
function formatHhMm(time: string): string {
  return time.slice(0, 5);
}

/**
 * StudentCheckinComponent — the `/student/checkin` page.
 *
 * Three visible surfaces:
 *  1. Idle/empty  — no ACTIVE lesson today. Shows the circle icon, the
 *                   "Нет активной пары" heading, and (if any) a "Следующая
 *                   пара" hint or the "На сегодня пар больше нет" fallback.
 *  2. Active hero — an ACTIVE lesson exists. Shows the blinking live dot,
 *                   subject name, time + room, the primary CTA, and the
 *                   STOMP-driven attendee counter.
 *  3. Confirmed   — either the POST succeeded (2xx) or an incoming STOMP
 *                   attendance.marked event matches the current user.
 *
 * The state machine (`state: Signal<CheckinState>`) is a discriminated
 * union so the template can rely on `isReady() / isConfirmed()` helpers
 * without ever touching `state().kind` directly.
 *
 * ## Security notes
 *
 * - T-51-13: `navigator.geolocation.getCurrentPosition` is invoked ONLY
 *   inside `onCheckinClick`. Coordinates are used exactly once (passed to
 *   `StudentApiService.checkin`) and never logged or persisted.
 * - T-51-15: The STOMP user_id check compares against `auth.currentUser().id`
 *   which is parsed from the JWT — a crafted message cannot confirm for a
 *   different student.
 * - T-51-20: The component passes `() => this.auth.accessToken()` as the
 *   token factory to StudentStompService; it never reads the token directly.
 */
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
        animate(
          '200ms cubic-bezier(0.16, 1, 0.3, 1)',
          style({ opacity: 1, transform: 'translateY(0)' }),
        ),
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
    if (this.activeLesson()) {
      return null;
    }
    return (
      this.lessons()
        .filter(l => l.status === 'PLANNED')
        .sort((a, b) => a.startTime.localeCompare(b.startTime))[0] ?? null
    );
  });

  /** Template helper for mono time rendering. */
  formatTime(time: string): string {
    return formatHhMm(time);
  }

  /** Template helper — delegates to SubjectCacheService. */
  getSubjectName$(subjectId: number | undefined) {
    return this.subjectCache.getName(subjectId ?? null);
  }

  ngOnInit(): void {
    const user = this.auth.currentUser();
    const groupId = user?.groupId ?? null;
    if (!groupId) {
      this.fetchError.set('Не удалось определить группу пользователя.');
      return;
    }
    this.fetchToday(groupId);
    // STOMP connect — pass a lazy getter so the component never reads
    // the token directly (T-51-20 mitigation).
    this.stomp.connect(groupId, () => this.auth.accessToken());
    // BUG-008: оборачиваем подписку в next/error, чтобы STOMP-ошибка не
    // обрушивала компонент целиком (страница «не открывается»).
    this.stomp.marked$
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: payload => {
          const active = this.activeLesson();
          if (active && payload.lesson_id === active.id) {
            this.attendeeCount.update(n => n + 1);
            if (user && payload.user_id === user.id) {
              this.state.set({ kind: 'confirmed' });
            }
          }
        },
        error: err => {
          console.error('[student-checkin] STOMP subscription error', err);
        },
      });
  }

  ngOnDestroy(): void {
    this.stomp.disconnect();
  }

  private fetchToday(groupId: number): void {
    const today = todayDateString();
    this.loading.set(true);
    this.studentApi.getWeekLessons(groupId, today, today).subscribe({
      next: lessons => {
        this.lessons.set(lessons);
        const active = lessons.find(l => l.status === 'ACTIVE');
        this.state.set(active ? { kind: 'ready' } : { kind: 'idle' });
        this.loading.set(false);
      },
      error: err => {
        console.error('[student-checkin] failed to load lessons', err);
        this.fetchError.set(formatLoadError(err, 'Не удалось загрузить данные.'));
        this.loading.set(false);
      },
    });
  }

  onCheckinClick(): void {
    const current = this.state();
    if (current.kind !== 'ready' && current.kind !== 'error') {
      return;
    }
    const active = this.activeLesson();
    if (!active) {
      return;
    }

    this.state.set({ kind: 'gps_pending' });

    if (!navigator.geolocation) {
      this.state.set({ kind: 'error', message: GPS_DENIED_MESSAGE });
      return;
    }

    navigator.geolocation.getCurrentPosition(
      position => {
        this.state.set({ kind: 'submitting' });
        this.studentApi
          .checkin({
            lat: position.coords.latitude,
            lng: position.coords.longitude,
          })
          .subscribe({
            next: () => this.state.set({ kind: 'confirmed' }),
            error: (err: unknown) => {
              const status = (err as { status?: number } | null)?.status ?? 0;
              this.state.set({
                kind: 'error',
                message: mapCheckinError(status),
              });
            },
          });
      },
      () => {
        this.state.set({ kind: 'error', message: GPS_DENIED_MESSAGE });
      },
      { timeout: 10000, maximumAge: 30000 },
    );
  }

  /**
   * Send a "please mark me present" request to the group's headman.
   * Fires only from `error` / `request_error` states — invalid transitions are ignored.
   */
  onRequestHeadmanClick(): void {
    const current = this.state();
    if (current.kind !== 'error' && current.kind !== 'request_error') {
      return;
    }
    const active = this.activeLesson();
    if (!active) {
      return;
    }
    this.state.set({ kind: 'request_submitting' });
    this.studentApi.requestLateCheckin(active.id).subscribe({
      next: () => this.state.set({ kind: 'request_pending' }),
      error: (err: unknown) => {
        const status = (err as { status?: number } | null)?.status ?? 0;
        this.state.set({
          kind: 'request_error',
          message: mapRequestHeadmanError(status),
        });
      },
    });
  }

  // ── Template helpers ───────────────────────────────────────────────
  isIdle(): boolean {
    return this.state().kind === 'idle';
  }
  isReady(): boolean {
    return this.state().kind === 'ready';
  }
  isGpsPending(): boolean {
    return this.state().kind === 'gps_pending';
  }
  isSubmitting(): boolean {
    return this.state().kind === 'submitting';
  }
  isConfirmed(): boolean {
    return this.state().kind === 'confirmed';
  }
  isError(): boolean {
    const k = this.state().kind;
    return k === 'error' || k === 'request_error';
  }
  isRequestSubmitting(): boolean {
    return this.state().kind === 'request_submitting';
  }
  isRequestPending(): boolean {
    return this.state().kind === 'request_pending';
  }
  errorMessage(): string | null {
    const s = this.state();
    if (s.kind === 'error') return s.message;
    if (s.kind === 'request_error') return s.message;
    return null;
  }
  requestButtonLabel(): string {
    return this.isRequestSubmitting() ? 'Отправляем…' : 'Попросить старосту отметить';
  }
  requestButtonDisabled(): boolean {
    return this.isRequestSubmitting();
  }
  buttonLabel(): string {
    switch (this.state().kind) {
      case 'gps_pending':
        return 'Определяем координаты…';
      case 'submitting':
        return 'Отправляем отметку…';
      case 'confirmed':
        return 'Вы отметились';
      default:
        return 'Отметиться';
    }
  }
  buttonAriaLabel(): string {
    return this.isConfirmed() ? 'Вы уже отметились' : 'Отметиться на текущей паре';
  }
  buttonDisabled(): boolean {
    return this.isGpsPending() || this.isSubmitting() || this.isConfirmed();
  }
}
