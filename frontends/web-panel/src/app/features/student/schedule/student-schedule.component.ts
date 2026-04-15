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

import { MatDialog } from '@angular/material/dialog';
import { forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';

import { AuthService } from '../../../core/auth/auth.service';
import { StudentApiService } from '../shared/student-api.service';
import { SubjectCacheService } from '../shared/subject-cache.service';
import type {
  AttendanceRecord,
  AttendanceStatus,
  LessonResponse,
} from '../shared/student-schedule.types';

export interface PersonalLessonState {
  status: AttendanceStatus;
  source: string;
}
import { LessonRowComponent } from './lesson-row/lesson-row.component';
import { ExcuseFormDialogComponent } from '../excuses/excuse-form-dialog/excuse-form-dialog.component';
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
  private readonly dialog = inject(MatDialog);

  readonly dayLabels = DAY_LABELS;

  readonly currentWeekStart = signal<Date>(getMonday(new Date()));
  readonly selectedDayIndex = signal<number>(getTodayDayIndex());
  readonly lessons = signal<LessonResponse[]>([]);
  readonly personalStateByLessonId = signal<Map<number, PersonalLessonState>>(new Map());
  readonly loading = signal<boolean>(false);
  readonly error = signal<string | null>(null);
  readonly expandedLessonId = signal<number | null>(null);
  readonly lateCheckinSent = signal<Set<number>>(new Set());
  readonly lateCheckinPending = signal<Set<number>>(new Set());
  readonly lateCheckinErrors = signal<Record<number, string>>({});

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
    forkJoin({
      lessons: this.studentApi.getWeekLessons(groupId, formatDate(monday), formatDate(saturday)),
      records: this.studentApi.getStudentRecords().pipe(
        catchError(() => of<AttendanceRecord[]>([])),
      ),
    })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: ({ lessons, records }) => {
          this.lessons.set(lessons);
          const map = new Map<number, PersonalLessonState>();
          for (const r of records) {
            if (r.status === 'present' || r.status === 'absent'
                || r.status === 'excused' || r.status === 'free_attendance') {
              map.set(r.lessonId, { status: r.status, source: r.source });
            }
          }
          this.personalStateByLessonId.set(map);
          this.loading.set(false);
        },
        error: () => {
          this.lessons.set([]);
          this.personalStateByLessonId.set(new Map());
          this.error.set('Не удалось загрузить расписание. Попробуйте позже.');
          this.loading.set(false);
        },
      });
  }

  personalStatusFor(lessonId: number): AttendanceStatus | null {
    return this.personalStateByLessonId().get(lessonId)?.status ?? null;
  }

  personalSourceFor(lessonId: number): string | null {
    return this.personalStateByLessonId().get(lessonId)?.source ?? null;
  }

  isLateCheckinSent(lessonId: number): boolean {
    return this.lateCheckinSent().has(lessonId);
  }

  isLateCheckinPending(lessonId: number): boolean {
    return this.lateCheckinPending().has(lessonId);
  }

  getLateCheckinError(lessonId: number): string | null {
    return this.lateCheckinErrors()[lessonId] ?? null;
  }

  /** Open excuse-ticket dialog preselected with this lesson. */
  openExcuseDialog(lessonId: number): void {
    const lesson = this.lessons().find(l => l.id === lessonId);
    if (!lesson) return;

    // Load full record history so the dialog can show other absences too.
    this.studentApi.getStudentRecords().pipe(
      catchError(() => of<AttendanceRecord[]>([])),
    ).subscribe(records => {
      // Ensure the target lesson appears in the dialog even if not yet absent —
      // synthesize a lightweight record so it's selectable.
      const alreadyThere = records.some(r => r.lessonId === lessonId);
      const seed: AttendanceRecord[] = alreadyThere ? records : [
        {
          lessonId: lesson.id,
          subjectId: lesson.subjectId,
          lessonDate: lesson.date,
          lessonNumber: lesson.lessonNumber,
          status: 'absent',
          symbol: 'н',
          source: 'manual',
        },
        ...records,
      ];

      this.dialog.open(ExcuseFormDialogComponent, {
        width: '560px',
        maxWidth: '100vw',
        maxHeight: '80vh',
        ariaLabel: 'Новый тикет о пропуске',
        data: { lessons: seed, preselectedLessonIds: [lessonId] },
      });
    });
  }

  requestLateCheckin(lessonId: number): void {
    if (this.isLateCheckinSent(lessonId) || this.isLateCheckinPending(lessonId)) return;

    this.lateCheckinErrors.update(e => {
      const next = { ...e };
      delete next[lessonId];
      return next;
    });
    this.lateCheckinPending.update(set => {
      const next = new Set(set);
      next.add(lessonId);
      return next;
    });

    this.studentApi.requestLateCheckin(lessonId).subscribe({
      next: () => {
        this.lateCheckinPending.update(set => {
          const next = new Set(set);
          next.delete(lessonId);
          return next;
        });
        this.lateCheckinSent.update(set => {
          const next = new Set(set);
          next.add(lessonId);
          return next;
        });
      },
      error: () => {
        this.lateCheckinPending.update(set => {
          const next = new Set(set);
          next.delete(lessonId);
          return next;
        });
        this.lateCheckinErrors.update(e => ({
          ...e,
          [lessonId]: 'Не удалось отправить запрос. Попробуйте ещё раз.',
        }));
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
