import {
  ChangeDetectionStrategy,
  Component,
  EventEmitter,
  Input,
  Output,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { addDays, formatDate, MONTH_ABBREV } from '../../schedule/week-utils';
import { HomeworkCardComponent, HomeworkCardModel } from '../../../../shared/homework-card/homework-card.component';
import type { HomeworkItem } from '../../shared/student-schedule.types';

const RU_DAY_NAMES = ['вс', 'пн', 'вт', 'ср', 'чт', 'пт', 'сб'];
const RU_DAY_FULL = ['Воскресенье', 'Понедельник', 'Вторник', 'Среда', 'Четверг', 'Пятница', 'Суббота'];

export interface WeekDayRow {
  date: Date;
  dateKey: string;
  label: string;
  items: HomeworkItem[];
}

/**
 * Student homework week view — vertical list of days (Mon–Sat) with
 * homeworks grouped under each day heading.
 *
 * Phase 61-06 / D-09: Week mode with ← → navigation between weeks.
 * Only days that have homework are shown (skip empty days).
 * If the whole week is empty, shows empty state.
 */
@Component({
  selector: 'app-student-homework-week-view',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, HomeworkCardComponent],
  templateUrl: './student-homework-week-view.component.html',
  styleUrls: ['./student-homework-week-view.component.css'],
})
export class StudentHomeworkWeekViewComponent {
  @Input({ required: true }) monday!: Date;
  @Input({ required: true }) items!: HomeworkItem[];
  @Output() mondayChanged = new EventEmitter<Date>();
  @Output() completeToggled = new EventEmitter<{ homework: HomeworkCardModel; completed: boolean }>();

  get weekLabel(): string {
    const saturday = addDays(this.monday, 5);
    const s = this.monday;
    const e = saturday;
    if (s.getMonth() === e.getMonth()) {
      return `${s.getDate()}–${e.getDate()} ${MONTH_ABBREV[e.getMonth()]}`;
    }
    return `${s.getDate()} ${MONTH_ABBREV[s.getMonth()]} – ${e.getDate()} ${MONTH_ABBREV[e.getMonth()]}`;
  }

  get days(): WeekDayRow[] {
    const rows: WeekDayRow[] = [];
    for (let i = 0; i < 6; i++) {
      const d = addDays(this.monday, i);
      const dateKey = formatDate(d);
      const dayItems = this.items.filter(h => h.lessonDate === dateKey);
      const dayIndex = d.getDay();
      const label = `${RU_DAY_FULL[dayIndex]}, ${d.getDate()} ${MONTH_ABBREV[d.getMonth()]}`;
      rows.push({ date: d, dateKey, label, items: dayItems });
    }
    return rows;
  }

  get hasAnyItems(): boolean {
    return this.days.some(d => d.items.length > 0);
  }

  previous(): void {
    this.mondayChanged.emit(addDays(this.monday, -7));
  }

  next(): void {
    this.mondayChanged.emit(addDays(this.monday, 7));
  }

  onCompleteToggled(event: { homework: HomeworkCardModel; completed: boolean }): void {
    this.completeToggled.emit(event);
  }
}
