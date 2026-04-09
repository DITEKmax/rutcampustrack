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
import { forkJoin, type Observable } from 'rxjs';
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
  const yyyy = d.getFullYear();
  const mm = String(d.getMonth() + 1).padStart(2, '0');
  const dd = String(d.getDate()).padStart(2, '0');
  return `${yyyy}-${mm}-${dd}`;
}

function formatHhMm(time: string): string {
  return time.slice(0, 5);
}

/**
 * Student cabinet landing page — `/student/dashboard`.
 *
 * Delivers STU-WEB-01: the first screen after a student logs in is a concise,
 * actionable overview of their day. Layout follows Phase 51 UI-SPEC §Component
 * 2:
 *
 *   - Greeting hero with time-based heading (Доброе утро/день/вечер) and a
 *     live clock refreshed every minute.
 *   - Horizontal chip row "Расписание на сегодня" with one chip per today's
 *     lesson (time + subject + status dot); empty message when today has no
 *     lessons.
 *   - NextLessonCard spotlighting the current ACTIVE lesson or the earliest
 *     PLANNED lesson today; falls back to an empty variant otherwise.
 *   - Zero or more RedzoneWarning banners — one per subject whose attendance
 *     percentage is strictly below the resolved global threshold.
 *
 * Data is loaded via three parallel HttpClient calls merged with forkJoin:
 * getWeekLessons (today only), getStudentStats, resolveGlobalThreshold. The
 * template shows a skeleton placeholder until all three resolve, and a shared
 * error message when any of them fail.
 */
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
        animate(
          '200ms cubic-bezier(0.16, 1, 0.3, 1)',
          style({ opacity: 1, transform: 'translateY(0)' }),
        ),
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

  /** Live clock ticker — refreshed once per minute. */
  private readonly _now = signal(new Date());
  readonly timeLabel = computed(() =>
    this._now().toLocaleTimeString('ru-RU', { hour: '2-digit', minute: '2-digit' }),
  );
  readonly dateLabel = computed(() =>
    this._now().toLocaleDateString('ru-RU', {
      weekday: 'long',
      day: 'numeric',
      month: 'long',
    }),
  );
  readonly greeting = computed(() => {
    const hour = this._now().getHours();
    if (hour < 6) return 'Доброй ночи';
    if (hour < 12) return 'Доброе утро';
    if (hour < 18) return 'Добрый день';
    return 'Добрый вечер';
  });

  /** Today's lessons sorted by start time (ascending). */
  readonly todaySorted = computed(() =>
    [...this.lessons()].sort((a, b) => a.startTime.localeCompare(b.startTime)),
  );

  /**
   * Selected focal lesson for NextLessonCard: prefer ACTIVE, fall back to the
   * earliest PLANNED, null if nothing applicable.
   */
  readonly nextLesson = computed<LessonResponse | null>(() => {
    const sorted = this.todaySorted();
    return (
      sorted.find((l) => l.status === 'ACTIVE') ??
      sorted.find((l) => l.status === 'PLANNED') ??
      null
    );
  });

  /**
   * Subjects whose attendance is strictly below the resolved red-zone
   * threshold. Empty until both stats and threshold have resolved.
   */
  readonly redZoneSubjects = computed<SubjectStats[]>(() => {
    const s = this.stats();
    const t = this.threshold();
    if (!s || !t) return [];
    return s.subjects.filter((sub) => sub.percentage < t.percentage);
  });

  formatTime(time: string): string {
    return formatHhMm(time);
  }

  getSubjectName$(id: number | undefined): Observable<string> {
    return this.subjectCache.getName(id ?? null);
  }

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
        this.error.set(
          'Не удалось загрузить данные. Проверьте подключение и обновите страницу.',
        );
        this.loading.set(false);
      },
    });

    // Refresh the live clock once per minute; cleaned up on destroy.
    const tick = setInterval(() => this._now.set(new Date()), 60_000);
    this.destroyRef.onDestroy(() => clearInterval(tick));
  }
}
