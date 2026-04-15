import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { trigger, transition, style, animate } from '@angular/animations';
import { switchMap } from 'rxjs/operators';
import { of } from 'rxjs';

import { AuthService } from '../../../core/auth/auth.service';
import { StudentApiService } from '../shared/student-api.service';
import type { HomeworkItem } from '../shared/student-schedule.types';
import { addDays, formatDate, getMonday } from '../schedule/week-utils';
import { SegmentedControlComponent } from '../../../shared/segmented-control/segmented-control.component';
import { StudentHomeworkDayViewComponent } from './day-view/student-homework-day-view.component';
import { StudentHomeworkWeekViewComponent } from './week-view/student-homework-week-view.component';
import { StudentHomeworkMonthViewComponent } from './month-view/student-homework-month-view.component';
import type { HomeworkCardModel } from '../../../shared/homework-card/homework-card.component';

export type HomeworkMode = 'day' | 'week' | 'month';

/**
 * Student homework page — `/student/homework`.
 *
 * Phase 61-06 / D-09: Rewritten as a container with three sub-views:
 * - День (default: tomorrow) — shows assignments for one day with ← → nav
 * - Неделя — vertical list of days with homework, week navigation
 * - Месяц — 6×7 matrix with count badges, click → switch to day view
 *
 * Filter toggle «только невыполненные» applies across all three modes.
 * markComplete/unmarkComplete use the existing StudentApiService API.
 */
@Component({
  selector: 'app-student-homework',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule,
    FormsModule,
    MatSlideToggleModule,
    SegmentedControlComponent,
    StudentHomeworkDayViewComponent,
    StudentHomeworkWeekViewComponent,
    StudentHomeworkMonthViewComponent,
  ],
  animations: [
    trigger('routeFade', [
      transition(':enter', [
        style({ opacity: 0, transform: 'translateY(8px)' }),
        animate('200ms cubic-bezier(0.16, 1, 0.3, 1)', style({ opacity: 1, transform: 'translateY(0)' })),
      ]),
    ]),
  ],
  templateUrl: './student-homework.component.html',
  styleUrl: './student-homework.component.css',
})
export class StudentHomeworkComponent implements OnInit {
  private readonly authService = inject(AuthService);
  private readonly apiService = inject(StudentApiService);

  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly homeworks = signal<HomeworkItem[]>([]);

  /** Current display mode. Default: «День» per D-09. */
  readonly mode = signal<HomeworkMode>('day');

  /** Currently selected date for day view (default: tomorrow per D-09). */
  readonly selectedDate = signal<Date>(addDays(new Date(), 1));

  /** Monday of the current week for week view. */
  readonly weekMonday = signal<Date>(getMonday(new Date()));

  /** First day of the current month for month view. */
  readonly currentMonth = signal<Date>((() => {
    const d = new Date();
    d.setDate(1);
    d.setHours(0, 0, 0, 0);
    return d;
  })());

  /** Toggle: show only uncompleted homeworks. */
  readonly filterUncompleted = signal(false);

  /** Filtered homework list — applied in all three modes. */
  readonly filteredHomeworks = computed(() => {
    const all = this.homeworks();
    return this.filterUncompleted() ? all.filter(h => !h.completed) : all;
  });

  readonly modeOptions: { value: HomeworkMode; label: string }[] = [
    { value: 'day',   label: 'День' },
    { value: 'week',  label: 'Неделя' },
    { value: 'month', label: 'Месяц' },
  ];

  ngOnInit(): void {
    this.load();
  }

  private load(): void {
    const groupId = this.authService.currentUser()?.groupId;
    if (groupId == null) {
      this.error.set('Не удалось загрузить задания. Проверьте подключение.');
      return;
    }
    this.loading.set(true);
    this.error.set(null);
    this.apiService
      .getActiveSemesterId()
      .pipe(
        switchMap(semesterId => {
          if (semesterId == null) return of([]);
          return this.apiService.getHomeworks(groupId, semesterId);
        }),
      )
      .subscribe({
        next: homeworks => {
          this.homeworks.set(homeworks);
          this.loading.set(false);
        },
        error: () => {
          this.error.set('Не удалось загрузить задания. Проверьте подключение.');
          this.loading.set(false);
        },
      });
  }

  onModeChange(mode: HomeworkMode): void {
    this.mode.set(mode);
  }

  onFilterChange(value: boolean): void {
    this.filterUncompleted.set(value);
  }

  /** MonthView cell click → switch to day mode with that date. */
  onDateSelected(date: Date): void {
    this.selectedDate.set(date);
    this.mode.set('day');
  }

  onDayDateChanged(date: Date): void {
    this.selectedDate.set(date);
  }

  onWeekMondayChanged(monday: Date): void {
    this.weekMonday.set(monday);
  }

  onMonthChanged(month: Date): void {
    this.currentMonth.set(month);
  }

  onCompleteToggled(event: { homework: HomeworkCardModel; completed: boolean }): void {
    const req$ = event.completed
      ? this.apiService.markHomeworkComplete(event.homework.id)
      : this.apiService.unmarkHomeworkComplete(event.homework.id);

    req$.subscribe({
      next: () => this.load(),
      error: () => {
        // Non-fatal — reload anyway to get authoritative state
        this.load();
      },
    });
  }
}
