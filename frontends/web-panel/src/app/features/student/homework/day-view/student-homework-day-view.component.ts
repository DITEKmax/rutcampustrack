import {
  ChangeDetectionStrategy,
  Component,
  computed,
  EventEmitter,
  Input,
  Output,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { addDays, formatDate, MONTH_ABBREV } from '../../schedule/week-utils';
import { HomeworkCardComponent, HomeworkCardModel } from '../../../../shared/homework-card/homework-card.component';
import type { HomeworkItem } from '../../shared/student-schedule.types';

const RU_DAYS = ['вс', 'пн', 'вт', 'ср', 'чт', 'пт', 'сб'];

/**
 * Student homework day view — shows all homeworks for one selected date.
 *
 * Phase 61-06 / D-09: Day mode with ← → navigation between dates.
 * Default date = tomorrow (set by parent StudentHomeworkComponent).
 *
 * Items are filtered by parent via `items` input (already filtered by
 * filterUncompleted if applicable). Day-view further filters to matching date.
 */
@Component({
  selector: 'app-student-homework-day-view',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, HomeworkCardComponent],
  templateUrl: './student-homework-day-view.component.html',
  styleUrls: ['./student-homework-day-view.component.css'],
})
export class StudentHomeworkDayViewComponent {
  @Input({ required: true }) date!: Date;
  @Input({ required: true }) items!: HomeworkItem[];
  @Output() dateChanged = new EventEmitter<Date>();
  @Output() completeToggled = new EventEmitter<{ homework: HomeworkCardModel; completed: boolean }>();

  get dateKey(): string {
    return formatDate(this.date);
  }

  get itemsForDay(): HomeworkItem[] {
    const key = formatDate(this.date);
    return this.items.filter(h => h.lessonDate === key);
  }

  get dateLabel(): string {
    const d = this.date;
    const dayName = RU_DAYS[d.getDay()];
    const dayNum = d.getDate();
    const monthName = MONTH_ABBREV[d.getMonth()];
    const year = d.getFullYear();
    const today = new Date();
    const isToday = formatDate(d) === formatDate(today);
    const tomorrow = addDays(today, 1);
    const isTomorrow = formatDate(d) === formatDate(tomorrow);

    let prefix = '';
    if (isToday) prefix = 'Сегодня, ';
    else if (isTomorrow) prefix = 'Завтра, ';
    return `${prefix}${dayName} ${dayNum} ${monthName} ${year}`;
  }

  previous(): void {
    this.dateChanged.emit(addDays(this.date, -1));
  }

  next(): void {
    this.dateChanged.emit(addDays(this.date, 1));
  }

  onCompleteToggled(event: { homework: HomeworkCardModel; completed: boolean }): void {
    this.completeToggled.emit(event);
  }
}
