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
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  animate,
  style,
  transition,
  trigger,
} from '@angular/animations';

import { AuthService } from '../../../core/auth/auth.service';
import { StudentApiService } from '../shared/student-api.service';
import { SubjectCacheService } from '../shared/subject-cache.service';
import type { LessonResponse } from '../shared/student-schedule.types';
import { LessonRowComponent } from './lesson-row/lesson-row.component';
import {
  addDays,
  formatDate,
  formatWeekRange,
  getMonday,
  getTodayDayIndex,
  isSameWeek,
} from './week-utils';

const DAY_LABELS = ['Пн', 'Вт', 'Ср', 'Чт', 'Пт', 'Сб'] as const;

/**
 * Full week-view schedule page for a logged-in STUDENT.
 *
 * State machine (signals):
 *   currentWeekStart — Monday of the visible week
 *   selectedDayIndex — 0..5 (Пн..Сб)
 *   lessons          — raw week lessons from the schedule service
 *   loading          — true while a week is being fetched
 *   error            — human-readable error (rendered in-place)
 *   expandedLessonId — which lesson row is currently expanded (null = none)
 *
 * Derived (computed):
 *   weekLabel        — "6-11 апр" or "30 мар - 4 апр"
 *   isCurrentWeek    — hides the "Сегодня" pill when true
 *   dayLessons       — lessons filtered to the selected day, sorted by
 *                      lesson number
 *
 * Data sources (see 51-01 summary):
 *   StudentApiService.getWeekLessons (schedule-service, unwraps HATEOAS)
 *   SubjectCacheService.getName      (academic-service, shareReplay cache)
 *   AuthService.currentUser          (JWT-derived; {id, role, isHeadman, groupId})
 *
 * Animations (UI-SPEC §Interaction):
 *   routeFade — 200ms ease-out on :enter
 *   daySlide  — 150ms ease-out on :enter (triggered by selectedDayIndex)
 */
@Component({
  selector: 'app-student-schedule',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [AsyncPipe, LessonRowComponent],
  templateUrl: './student-schedule.component.html',
  styleUrl: './student-schedule.component.css',
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
    trigger('daySlide', [
      transition(':enter', [
        style({ opacity: 0, transform: 'translateX(8px)' }),
        animate(
          '150ms cubic-bezier(0.16, 1, 0.3, 1)',
          style({ opacity: 1, transform: 'translateX(0)' }),
        ),
      ]),
    ]),
  ],
})
export class StudentScheduleComponent implements OnInit {
  private readonly studentApi = inject(StudentApiService);
  private readonly subjectCache = inject(SubjectCacheService);
  private readonly auth = inject(AuthService);
  private readonly destroyRef = inject(DestroyRef);

  readonly dayLabels = DAY_LABELS;

  readonly currentWeekStart = signal<Date>(getMonday(new Date()));
  readonly selectedDayIndex = signal<number>(getTodayDayIndex());
  readonly lessons = signal<LessonResponse[]>([]);
  readonly loading = signal<boolean>(false);
  readonly error = signal<string | null>(null);
  readonly expandedLessonId = signal<number | null>(null);

  readonly weekLabel = computed(() => formatWeekRange(this.currentWeekStart()));
  readonly isCurrentWeek = computed(() =>
    isSameWeek(this.currentWeekStart(), new Date()),
  );

  readonly dayLessons = computed(() => {
    // Backend stores dayOfWeek as 1=Mon..7=Sun.
    // selectedDayIndex is 0=Mon..5=Sat — offset by +1.
    const backendDow = this.selectedDayIndex() + 1;
    return this.lessons()
      .filter(l => l.dayOfWeek === backendDow)
      .sort((a, b) => a.lessonNumber - b.lessonNumber);
  });

  ngOnInit(): void {
    this.loadWeek();
  }

  loadWeek(): void {
    const user = this.auth.currentUser();
    const groupId = user?.groupId;
    if (!groupId) {
      // Defense in depth: studentGuard already blocks this route for
      // unauthenticated users, but a logged-in user without a groupId
      // (e.g. a misconfigured test account) must not fire the request.
      this.error.set('Не удалось определить группу пользователя.');
      this.lessons.set([]);
      this.loading.set(false);
      return;
    }
    const monday = this.currentWeekStart();
    const saturday = addDays(monday, 5);
    this.loading.set(true);
    this.error.set(null);
    this.studentApi
      .getWeekLessons(groupId, formatDate(monday), formatDate(saturday))
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: lessons => {
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

  /** Subject-name observable for the async pipe inside the template. */
  getSubjectName$(subjectId: number) {
    return this.subjectCache.getName(subjectId);
  }
}
