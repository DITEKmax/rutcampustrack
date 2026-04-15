import {
  ChangeDetectionStrategy,
  Component,
  EventEmitter,
  Input,
  Output,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { addDays, formatDate, getMonday, MONTH_ABBREV } from '../../schedule/week-utils';
import type { HomeworkItem } from '../../shared/student-schedule.types';

export interface MonthCell {
  date: Date;
  dateKey: string;
  dayNumber: number;
  isCurrentMonth: boolean;
  isToday: boolean;
  count: number;
  hasUncompleted: boolean;
}

const RU_MONTH_FULL = [
  'Январь', 'Февраль', 'Март', 'Апрель', 'Май', 'Июнь',
  'Июль', 'Август', 'Сентябрь', 'Октябрь', 'Ноябрь', 'Декабрь',
];

/**
 * Student homework month view — 6×7 calendar matrix.
 *
 * Phase 61-06 / D-09: Month mode. First row starts on Monday of the week
 * containing the 1st of the month. Each cell shows day number, homework
 * count badge, and «*» indicator for uncompleted items.
 *
 * Click on a cell emits dateSelected → parent switches to day mode.
 */
@Component({
  selector: 'app-student-homework-month-view',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule],
  templateUrl: './student-homework-month-view.component.html',
  styleUrls: ['./student-homework-month-view.component.css'],
})
export class StudentHomeworkMonthViewComponent {
  @Input({ required: true }) month!: Date;
  @Input({ required: true }) items!: HomeworkItem[];
  @Output() dateSelected = new EventEmitter<Date>();
  @Output() monthChanged = new EventEmitter<Date>();

  readonly WEEK_DAYS = ['Пн', 'Вт', 'Ср', 'Чт', 'Пт', 'Сб', 'Вс'];

  get monthLabel(): string {
    return `${RU_MONTH_FULL[this.month.getMonth()]} ${this.month.getFullYear()}`;
  }

  get cells(): MonthCell[] {
    const year = this.month.getFullYear();
    const monthIndex = this.month.getMonth();
    const firstDay = new Date(year, monthIndex, 1);
    const startMonday = getMonday(firstDay);
    const todayKey = formatDate(new Date());

    const cells: MonthCell[] = [];
    for (let i = 0; i < 42; i++) {
      const d = addDays(startMonday, i);
      const dateKey = formatDate(d);
      const isCurrentMonth = d.getMonth() === monthIndex;
      const dayItems = this.items.filter(h => h.lessonDate === dateKey);
      cells.push({
        date: d,
        dateKey,
        dayNumber: d.getDate(),
        isCurrentMonth,
        isToday: dateKey === todayKey,
        count: dayItems.length,
        hasUncompleted: dayItems.some(h => !h.completed),
      });
    }
    return cells;
  }

  get weeks(): MonthCell[][] {
    const all = this.cells;
    const result: MonthCell[][] = [];
    for (let i = 0; i < 6; i++) {
      result.push(all.slice(i * 7, i * 7 + 7));
    }
    return result;
  }

  previousMonth(): void {
    const d = new Date(this.month);
    d.setMonth(d.getMonth() - 1);
    d.setDate(1);
    this.monthChanged.emit(d);
  }

  nextMonth(): void {
    const d = new Date(this.month);
    d.setMonth(d.getMonth() + 1);
    d.setDate(1);
    this.monthChanged.emit(d);
  }

  onCellClick(cell: MonthCell): void {
    this.dateSelected.emit(cell.date);
  }
}
