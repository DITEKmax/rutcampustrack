import {
  AfterViewChecked,
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import { CommonModule, NgTemplateOutlet } from '@angular/common';
import { forkJoin } from 'rxjs';
import { trigger, transition, style, animate } from '@angular/animations';
import { MatButtonModule } from '@angular/material/button';
import { MatChipsModule } from '@angular/material/chips';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatDialog } from '@angular/material/dialog';
import { AuthService } from '../../../core/auth/auth.service';
import { HeadmanApiService } from '../shared/headman-api.service';
import { ConfirmDialogComponent, ConfirmDialogData } from '../../../shared/confirm-dialog/confirm-dialog.component';
import {
  ConfirmWithReasonDialogComponent,
  type ConfirmWithReasonDialogData,
} from '../../../shared/confirm-with-reason-dialog/confirm-with-reason-dialog.component';
import { addDays, formatDate } from '../../student/schedule/week-utils';
import type { LessonResponse } from '../../../api/schema';

type Lesson = LessonResponse & {
  cancelReason?: string;
  cancelledBy?: number | null;
  cancelledAt?: string | null;
  /** 'template' = из ScheduleItem-шаблона, 'oneoff' = из schedule_one_off_lessons. */
  source: 'template' | 'oneoff';
  /** Только для source='oneoff': true если эта разовая пара заняла slot отменённой template-пары. */
  replacement?: boolean;
};

/** Сетка времён РУТ МИИТ. См. schedule-slot-dialog.component.ts:188-202. */
const LESSON_SLOT_TIMES: Record<number, [string, string]> = {
  1: ['08:30', '09:50'],
  2: ['10:05', '11:25'],
  3: ['11:40', '13:00'],
  4: ['13:45', '15:05'],
  5: ['15:20', '16:40'],
  6: ['16:55', '18:15'],
  7: ['18:30', '19:50'],
  8: ['20:00', '21:20'],
};

interface DayGroup {
  date: string;
  label: string;
  lessons: Lesson[];
}

interface ActiveSemester {
  id: number;
  name: string;
  dateFrom: string;
  dateTo: string;
}

interface MonthGroup {
  /** 'YYYY-MM' — sortable key + match с сегодняшним месяцем. */
  key: string;
  /** Display: «Апрель 2026». */
  label: string;
  days: DayGroup[];
  lessonsTotal: number;
  /** true для текущего месяца — этот <details> по умолчанию open. */
  isCurrent: boolean;
}

type Period = 'today' | 'month' | 'semester';
type Kind = 'all' | 'active' | 'cancelled' | 'oneoff';

const DAY_NAMES = ['Воскресенье', 'Понедельник', 'Вторник', 'Среда', 'Четверг', 'Пятница', 'Суббота'];
const MONTH_NAMES = [
  'января', 'февраля', 'марта', 'апреля', 'мая', 'июня',
  'июля', 'августа', 'сентября', 'октября', 'ноября', 'декабря',
];

/** Именительный падеж — для заголовков месячных групп («Апрель 2026»). */
const MONTH_TITLE_NAMES = [
  'Январь', 'Февраль', 'Март', 'Апрель', 'Май', 'Июнь',
  'Июль', 'Август', 'Сентябрь', 'Октябрь', 'Ноябрь', 'Декабрь',
];

const PERIOD_LABEL: Record<Period, string> = {
  today: 'Сегодня',
  month: 'Месяц',
  semester: 'Семестр',
};

/** Месячное окно по умолчанию = today..today+30 дней. */
const MONTH_RANGE_DAYS = 30;

/**
 * Headman lessons page — `/headman/lessons`.
 *
 * Список конкретных пар на ближайшие 2 недели. В отличие от `/headman/schedule`
 * (матрица шаблона), здесь видны конкретные `lesson` записи с датами, и каждую
 * можно отменить через `PATCH /api/schedule/lessons/{id}/cancel` с указанием
 * причины. Отменённые пары не считаются в статистике (см. CLAUDE.md).
 *
 * Поведение:
 * - Группировка по дням, дни без пар скрыты.
 * - Кнопка «Отменить» доступна для статусов PLANNED/ACTIVE.
 * - Для уже отменённых — кнопка «Восстановить» (PATCH .../restore).
 * - Reason собирается через ConfirmWithReasonDialog (M07 G7, QC4) —
 *   focus-trap, inline validation non-empty + maxLength 500.
 */
@Component({
  selector: 'app-headman-lessons',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, NgTemplateOutlet, MatButtonModule, MatChipsModule, MatPaginatorModule, MatProgressSpinnerModule],
  animations: [
    trigger('routeFade', [
      transition(':enter', [
        style({ opacity: 0, transform: 'translateY(8px)' }),
        animate('200ms cubic-bezier(0.16, 1, 0.3, 1)', style({ opacity: 1, transform: 'translateY(0)' })),
      ]),
    ]),
  ],
  template: `
    <div class="page-stack" [@routeFade]>
      <div class="page-header">
        <div>
          <span class="page-eyebrow">Старостат</span>
          <h1 class="page-title">{{ pageTitle() }}</h1>
        </div>
      </div>

      <div class="filter-bar">
        <mat-chip-listbox
          aria-label="Период"
          [value]="period()"
          (change)="onPeriodChange($event.value)">
          <mat-chip-option value="today">Сегодня</mat-chip-option>
          <mat-chip-option value="month">Месяц</mat-chip-option>
          <mat-chip-option value="semester">Семестр</mat-chip-option>
        </mat-chip-listbox>
        <mat-chip-listbox
          aria-label="Тип пар"
          [value]="kind()"
          (change)="onKindChange($event.value)">
          <mat-chip-option value="all">Все</mat-chip-option>
          <mat-chip-option value="active">Активные</mat-chip-option>
          <mat-chip-option value="cancelled">Отменённые</mat-chip-option>
          <mat-chip-option value="oneoff">Разовые</mat-chip-option>
        </mat-chip-listbox>
      </div>

      @if (loading()) {
        <div class="page-card"><mat-spinner diameter="32"></mat-spinner></div>
      } @else if (error()) {
        <div class="page-error"><i class="ph ph-warning-circle"></i> {{ error() }}</div>
      } @else if (dayGroups().length === 0) {
        <div class="page-empty">
          <i class="ph-duotone ph-calendar-blank"></i>
          <h3>Пар нет</h3>
          <p>{{ emptyHint() }}</p>
        </div>
      } @else if (period() === 'semester') {
        @for (month of monthGroups(); track month.key) {
          <details class="month-group" [open]="month.isCurrent">
            <summary class="month-summary">
              <span class="month-summary__label">{{ month.label }}</span>
              <span class="month-summary__count">{{ month.lessonsTotal }} пар</span>
            </summary>
            @for (day of month.days; track day.date) {
              <ng-container *ngTemplateOutlet="dayTpl; context: { $implicit: day }"></ng-container>
            }
          </details>
        }
      } @else {
        @for (day of pagedDayGroups(); track day.date) {
          <ng-container *ngTemplateOutlet="dayTpl; context: { $implicit: day }"></ng-container>
        }
      }

      @if (!loading() && !error() && dayGroups().length > 0) {
        <mat-paginator
          [length]="dayGroups().length"
          [pageSize]="pageSize()"
          [pageIndex]="pageIndex()"
          [pageSizeOptions]="pageSizeOptions"
          (page)="onPage($event)"
          aria-label="Постранично по дням">
        </mat-paginator>
      }
    </div>

    <ng-template #dayTpl let-day>
      <div class="page-card day-card" [attr.data-date]="day.date" [class.day-card--today]="day.date === todayIso()">
        <h2 class="day-heading">{{ day.label }}</h2>
        <ul class="lesson-list">
          @for (lesson of day.lessons; track lesson.id) {
            <li class="lesson-row" [class.lesson-row--cancelled]="isCancelled(lesson)">
              <div class="lesson-row__time">
                <span class="lesson-num">№{{ lesson.lessonNumber }}</span>
                <span class="lesson-time">{{ shortTime(lesson.startTime) }}–{{ shortTime(lesson.endTime) }}</span>
              </div>
              <div class="lesson-row__main">
                <div class="lesson-subject">
                  {{ subjectName(lesson.subjectId) }}
                  @if (subjectTypeLabel(lesson.subjectId); as t) {
                    <span class="lesson-type">— {{ t }}</span>
                  }
                </div>
                <div class="lesson-meta">
                  @if (lesson.room) { <span>{{ lesson.room }}</span> }
                  <span class="status status--{{ statusKey(lesson) }}">{{ statusLabel(lesson) }}</span>
                  @if (lesson.source === 'oneoff') {
                    @if (lesson.replacement) {
                      <span class="badge badge--replacement" title="Разовая пара заменяет отменённую">Замена</span>
                    } @else {
                      <span class="badge badge--oneoff" title="Разовая пара (вне постоянного расписания)">Разовая</span>
                    }
                  }
                  @if (isCancelled(lesson) && lesson.cancelReason) {
                    <span class="cancel-reason">— {{ lesson.cancelReason }}</span>
                  }
                </div>
                @if (isCancelled(lesson) && cancellerDisplay(lesson); as ad) {
                  <div class="lesson-audit">{{ ad }}</div>
                }
              </div>
              <div class="lesson-row__actions">
                @if (!isCancelled(lesson)) {
                  <button class="btn-stroke btn-stroke--danger" type="button"
                          [disabled]="busy() === lesson.id"
                          [title]="isClosed(lesson) ? 'Отменить уже прошедшую пару (для исторических данных)' : 'Отменить пару'"
                          (click)="onCancel(lesson)">
                    <i class="ph ph-x-circle"></i> Отменить
                  </button>
                } @else {
                  <button class="btn-stroke" type="button"
                          [disabled]="busy() === lesson.id"
                          (click)="onRestore(lesson)">
                    <i class="ph ph-arrow-counter-clockwise"></i> Восстановить
                  </button>
                }
              </div>
            </li>
          }
        </ul>
      </div>
    </ng-template>
  `,
  styles: [`
    .filter-bar { display: flex; gap: var(--space-3); flex-wrap: wrap; align-items: center; }
    .month-group {
      border: 1px solid var(--border-subtle);
      border-radius: var(--radius-lg);
      background: var(--bg-surface);
      padding: 4px 0;
      margin-bottom: var(--space-3);
    }
    .month-group[open] { padding-bottom: var(--space-3); }
    .month-summary {
      list-style: none;
      cursor: pointer;
      padding: var(--space-3) var(--space-4);
      display: flex; align-items: center; justify-content: space-between;
      gap: var(--space-3);
      font-weight: 600;
      color: var(--text-primary);
      user-select: none;
    }
    .month-summary::-webkit-details-marker { display: none; }
    .month-summary:hover { background: var(--bg-elevated); border-radius: var(--radius-md); }
    .month-summary__count {
      font: 500 0.6875rem/1 var(--font-mono);
      color: var(--text-muted);
      letter-spacing: 0.04em; text-transform: uppercase;
    }
    .month-group .day-card { margin: var(--space-2) var(--space-3) 0; }
    .day-card { padding: 16px; }
    .day-card--today {
      border: 1px solid color-mix(in oklab, var(--accent-primary) 40%, transparent);
      box-shadow: 0 0 0 3px color-mix(in oklab, var(--accent-primary) 10%, transparent);
    }
    .day-heading {
      font-size: 1rem;
      font-weight: 600;
      margin: 0 0 12px;
      color: var(--text-primary);
    }
    .lesson-list { list-style: none; padding: 0; margin: 0; display: flex; flex-direction: column; gap: 8px; }
    .lesson-row {
      display: grid;
      grid-template-columns: 110px 1fr auto;
      gap: var(--space-3);
      align-items: center;
      padding: var(--space-3) var(--space-4);
      border: 1px solid var(--border-subtle);
      border-radius: var(--radius-md);
      background: var(--bg-surface);
      transition:
        border-color var(--duration-base) var(--ease-out),
        background-color var(--duration-base) var(--ease-out);
    }
    .lesson-row:hover { background: var(--bg-elevated); border-color: var(--border-default); }
    .lesson-row--cancelled { opacity: 0.55; }
    .lesson-row__time { display: flex; flex-direction: column; gap: 2px; }
    .lesson-num {
      font-family: var(--font-mono);
      font-size: 0.6875rem;
      letter-spacing: 0.04em;
      text-transform: uppercase;
      color: var(--text-muted);
    }
    .lesson-time {
      font-family: var(--font-mono);
      font-variant-numeric: tabular-nums;
      font-size: 0.875rem;
      color: var(--text-primary);
    }
    .lesson-subject { font-weight: 600; color: var(--text-primary); }
    .lesson-type { font-weight: 500; color: var(--text-muted); margin-left: 4px; font-size: 0.875rem; }
    .lesson-meta {
      display: flex; gap: var(--space-2); align-items: center; flex-wrap: wrap;
      font-size: 0.8125rem; color: var(--text-secondary); margin-top: 2px;
    }
    /* Status — shares vocabulary with .pill */
    .status {
      display: inline-flex; align-items: center; gap: 6px;
      padding: 4px 10px;
      border-radius: var(--radius-full);
      font: 500 0.6875rem/1 var(--font-mono);
      letter-spacing: 0.04em; text-transform: uppercase; white-space: nowrap;
    }
    .status--planned {
      background: color-mix(in oklab, var(--accent-secondary) 14%, transparent);
      color: var(--accent-secondary);
      border: 1px solid color-mix(in oklab, var(--accent-secondary) 30%, transparent);
    }
    .status--active {
      background: color-mix(in oklab, var(--accent-primary) 14%, transparent);
      color: var(--accent-primary);
      border: 1px solid color-mix(in oklab, var(--accent-primary) 30%, transparent);
    }
    .status--closed {
      background: color-mix(in oklab, var(--text-muted) 14%, transparent);
      color: var(--text-secondary);
      border: 1px solid color-mix(in oklab, var(--text-muted) 26%, transparent);
    }
    .status--cancelled {
      background: color-mix(in oklab, var(--accent-danger) 14%, transparent);
      color: var(--accent-danger);
      border: 1px solid color-mix(in oklab, var(--accent-danger) 30%, transparent);
    }
    .cancel-reason { font-style: italic; }
    .lesson-audit {
      grid-column: 2 / -1;
      font-size: 0.75rem;
      color: var(--text-muted);
      margin-top: 4px;
      font-style: italic;
    }
    .badge {
      display: inline-flex; align-items: center; gap: 4px;
      padding: 2px 8px;
      border-radius: var(--radius-full);
      font: 500 0.6875rem/1 var(--font-mono);
      letter-spacing: 0.04em; text-transform: uppercase; white-space: nowrap;
    }
    .badge--replacement {
      background: color-mix(in oklab, var(--accent-warning, #c98a00) 14%, transparent);
      color: var(--accent-warning, #c98a00);
      border: 1px solid color-mix(in oklab, var(--accent-warning, #c98a00) 30%, transparent);
    }
    .badge--oneoff {
      background: color-mix(in oklab, var(--accent-secondary) 14%, transparent);
      color: var(--accent-secondary);
      border: 1px solid color-mix(in oklab, var(--accent-secondary) 30%, transparent);
    }
    .btn-stroke {
      padding: 6px 14px;
      border-radius: var(--radius-md);
      border: 1px solid var(--border-default);
      background: transparent;
      color: var(--text-primary);
      cursor: pointer;
      font-size: 0.8125rem;
      font-family: inherit;
      display: inline-flex; align-items: center; gap: 4px;
      transition:
        background-color var(--duration-base) var(--ease-out),
        border-color var(--duration-base) var(--ease-out);
    }
    .btn-stroke:hover:not([disabled]) {
      background: var(--bg-elevated);
      border-color: var(--border-accent);
    }
    .btn-stroke[disabled] { opacity: 0.5; cursor: not-allowed; }
    .btn-stroke--danger {
      color: var(--accent-danger);
      border-color: color-mix(in oklab, var(--accent-danger) 35%, transparent);
    }
    .btn-stroke--danger:hover:not([disabled]) {
      background: color-mix(in oklab, var(--accent-danger) 12%, transparent);
      border-color: color-mix(in oklab, var(--accent-danger) 50%, transparent);
    }
  `],
})
export class HeadmanLessonsComponent implements OnInit, AfterViewChecked {
  private readonly headmanApi = inject(HeadmanApiService);
  private readonly auth = inject(AuthService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly dialog = inject(MatDialog);
  private readonly host = inject(ElementRef<HTMLElement>);
  /** Флаг — после следующего ViewChecked нужно проскроллить к сегодняшней дате (Семестр режим). */
  private pendingTodayScroll = false;

  /** Текущий выбранный период. Default = month (компромисс по решению owner'а). */
  readonly period = signal<Period>('month');
  /** Текущий выбранный тип пар (фильтр по статусу + видимость one-off). Default = all. */
  readonly kind = signal<Kind>('all');
  /** Активный семестр — резолвится один раз lazy при первом переключении на 'semester'. */
  readonly activeSemester = signal<ActiveSemester | null>(null);
  /** Paginator: индекс страницы (zero-based). */
  readonly pageIndex = signal(0);
  /** Paginator: размер страницы = количество день-карточек. */
  readonly pageSize = signal(50);
  readonly pageSizeOptions = [20, 50, 100, 200];
  /** Map<userId, fullName> для отображения «отменено таким-то». Заполняется async batch-резолвом. */
  readonly userNames = signal<Map<number, string>>(new Map());

  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly lessons = signal<Lesson[]>([]);
  readonly subjects = signal<{ id: number; name: string }[]>([]);
  /** id урока, для которого сейчас идёт PATCH (cancel/restore) — блокирует кнопки. */
  readonly busy = signal<number | null>(null);

  readonly dayGroups = computed<DayGroup[]>(() => {
    const map = new Map<string, Lesson[]>();
    for (const l of this.lessons()) {
      const arr = map.get(l.date) ?? [];
      arr.push(l);
      map.set(l.date, arr);
    }
    const dates = Array.from(map.keys()).sort();
    return dates.map(date => {
      const arr = (map.get(date) ?? []).slice().sort((a, b) => a.lessonNumber - b.lessonNumber);
      return { date, label: this.dayLabel(date), lessons: arr };
    });
  });

  /** Slice dayGroups по текущему пэйджу. Используется для today/month рендера. */
  readonly pagedDayGroups = computed<DayGroup[]>(() => {
    const all = this.dayGroups();
    const start = this.pageIndex() * this.pageSize();
    return all.slice(start, start + this.pageSize());
  });

  /** Group dayGroups в месяцы (только для period='semester'). Текущий месяц помечается isCurrent=true. */
  readonly monthGroups = computed<MonthGroup[]>(() => {
    const days = this.pagedDayGroups();
    const todayPrefix = this.todayIso().slice(0, 7); // 'YYYY-MM'
    const map = new Map<string, DayGroup[]>();
    for (const d of days) {
      const key = d.date.slice(0, 7);
      const arr = map.get(key) ?? [];
      arr.push(d);
      map.set(key, arr);
    }
    return Array.from(map.entries()).sort(([a], [b]) => a.localeCompare(b)).map(([key, days]) => {
      const [year, month] = key.split('-').map(Number);
      const lessonsTotal = days.reduce((s, d) => s + d.lessons.length, 0);
      return {
        key,
        label: `${MONTH_TITLE_NAMES[month - 1]} ${year}`,
        days,
        lessonsTotal,
        isCurrent: key === todayPrefix,
      };
    });
  });

  /** Сегодня в ISO YYYY-MM-DD — нужно для подсветки day-card и якоря. */
  todayIso(): string {
    return formatDate(new Date());
  }

  /** Динамический заголовок страницы — зависит от period + (для семестра) имени семестра. */
  readonly pageTitle = computed<string>(() => {
    switch (this.period()) {
      case 'today': return 'Пары на сегодня';
      case 'month': return 'Пары на месяц';
      case 'semester': {
        const sem = this.activeSemester();
        return sem ? `Пары на семестр (${sem.name})` : 'Пары на семестр';
      }
    }
  });

  /** Подсказка для пустого state — отражает выбранный период. */
  readonly emptyHint = computed<string>(() => {
    switch (this.period()) {
      case 'today': return 'Сегодня нет ни одной пары.';
      case 'month': return 'В ближайший месяц нет ни одной пары.';
      case 'semester': return 'В выбранном семестре нет ни одной пары.';
    }
  });

  ngOnInit(): void {
    const user = this.auth.currentUser();
    if (!user?.groupId) {
      this.error.set('Не удалось определить группу. Обратитесь к администратору.');
      this.loading.set(false);
      return;
    }
    // Предметы загружаем один раз — не зависят от period.
    this.loadSubjects();
    this.reload();
  }

  ngAfterViewChecked(): void {
    if (!this.pendingTodayScroll) return;
    const el = this.host.nativeElement.querySelector(`.day-card[data-date="${this.todayIso()}"]`);
    if (el) {
      this.pendingTodayScroll = false;
      // smooth + block:start — текущая дата окажется в верхней части viewport.
      (el as HTMLElement).scrollIntoView({ behavior: 'smooth', block: 'start' });
    }
  }

  /** Обработчик смены period chip — обновляет signal и перезапускает load. */
  onPeriodChange(value: Period | null): void {
    if (!value || value === this.period()) return;
    this.period.set(value);
    this.pageIndex.set(0); // сброс пагинатора при любой смене фильтра
    if (value === 'semester') this.pendingTodayScroll = true;
    this.reload();
  }

  /** Обработчик смены kind chip — обновляет signal и перезапускает load. */
  onKindChange(value: Kind | null): void {
    if (!value || value === this.kind()) return;
    this.kind.set(value);
    this.pageIndex.set(0);
    this.reload();
  }

  /** Обработчик mat-paginator: pageIndex + pageSize, без re-fetch (всё в памяти). */
  onPage(event: PageEvent): void {
    this.pageIndex.set(event.pageIndex);
    this.pageSize.set(event.pageSize);
  }

  /**
   * Маппинг kind → CSV статусов для бэкенда.
   * NB: бэкенд по умолчанию возвращает planned+active+closed (исключает cancelled),
   * поэтому для 'all' и 'cancelled' нам надо явно передать список.
   * Для 'oneoff' тоже шлём — мы всё равно не покажем template (фильтр в C6).
   */
  private statusFilter(): string {
    switch (this.kind()) {
      case 'all':       return 'planned,active,closed,cancelled';
      case 'active':    return 'planned,active,closed';
      case 'cancelled': return 'cancelled';
      case 'oneoff':    return 'planned,active,closed,cancelled'; // template скроется в C6
    }
  }

  /** Re-fetch lessons для текущего period. Для 'semester' lazy-резолвит активный семестр. */
  private reload(): void {
    const user = this.auth.currentUser();
    if (!user?.groupId) return;
    const groupId = user.groupId;

    if (this.period() === 'semester' && !this.activeSemester()) {
      this.loading.set(true);
      this.error.set(null);
      this.loadActiveSemester(() => this.fetchLessons(groupId));
      return;
    }
    this.fetchLessons(groupId);
  }

  /**
   * Inline-загрузка активного семестра. Дублирует логику из 4 других
   * компонентов (см. docs/archive/future-ideas.md "getActiveSemester() —
   * устранить inline-дублирование (5 мест)" — будет вынесено в shared
   * service отдельным cleanup-PR).
   */
  private loadActiveSemester(onResolved: () => void): void {
    this.headmanApi.listSemesters().subscribe({
      next: (resp: any) => {
        const embedded = resp?._embedded;
        const list = embedded
          ? ((Object.values(embedded)[0] as any[]) ?? [])
          : (Array.isArray(resp) ? resp : []);
        const active = list.find((s: any) => s.active === true);
        if (!active) {
          this.error.set('Активный семестр не найден. Переключите период.');
          this.loading.set(false);
          return;
        }
        const dateFrom = active.dateFrom ?? active.startDate;
        const dateTo = active.dateTo ?? active.endDate;
        if (!dateFrom || !dateTo) {
          this.error.set('У активного семестра не заполнены даты. Переключите период.');
          this.loading.set(false);
          return;
        }
        this.activeSemester.set({
          id: active.id,
          name: active.name,
          dateFrom,
          dateTo,
        });
        onResolved();
      },
      error: () => {
        this.error.set('Не удалось загрузить активный семестр.');
        this.loading.set(false);
      },
    });
  }

  /** Вычисляет [dateFrom, dateTo] для текущего period + activeSemester. */
  private computeDateRange(): { from: string; to: string } {
    const today = new Date();
    switch (this.period()) {
      case 'today':
        return { from: formatDate(today), to: formatDate(today) };
      case 'month':
        return { from: formatDate(today), to: formatDate(addDays(today, MONTH_RANGE_DAYS - 1)) };
      case 'semester': {
        const sem = this.activeSemester();
        if (sem) return { from: sem.dateFrom, to: sem.dateTo };
        // Fallback (не должен срабатывать — reload() резолвит семестр перед fetchLessons)
        return { from: formatDate(today), to: formatDate(addDays(today, MONTH_RANGE_DAYS - 1)) };
      }
    }
  }

  private fetchLessons(groupId: number): void {
    this.loading.set(true);
    this.error.set(null);
    const range = this.computeDateRange();
    const includeOneOff = this.kind() === 'all' || this.kind() === 'active' || this.kind() === 'oneoff';
    const includeTemplate = this.kind() !== 'oneoff';

    forkJoin({
      template: this.headmanApi.getGroupLessons(groupId, range.from, range.to, this.statusFilter()),
      oneoff: includeOneOff
        ? this.headmanApi.getOneOffLessons(groupId, range.from, range.to)
        : Promise.resolve(null) as any,
    }).subscribe({
      next: ({ template, oneoff }: any) => {
        const templateLessons: Lesson[] = includeTemplate
          ? this.extractEmbedded(template).map((entry: any) => {
              const item = entry?.content ?? entry;
              return { ...item, source: 'template' as const };
            }).filter(Boolean)
          : [];

        const oneOffLessons: Lesson[] = includeOneOff
          ? this.extractEmbedded(oneoff).map((entry: any) => this.toOneOffLesson(entry?.content ?? entry))
          : [];

        // Слот-match: one-off на (date, lesson_number) совпадающий с CANCELLED template-парой = «Замена».
        const cancelledKeys = new Set(
          templateLessons
            .filter(l => (l.status ?? '').toString().toUpperCase() === 'CANCELLED')
            .map(l => `${l.date}#${l.lessonNumber}`)
        );
        for (const oo of oneOffLessons) {
          if (cancelledKeys.has(`${oo.date}#${oo.lessonNumber}`)) {
            oo.replacement = true;
          }
        }

        // Для kind='cancelled' — one-off вообще не добавляем (мы их и не запрашивали).
        const merged = [...templateLessons, ...oneOffLessons];
        this.lessons.set(merged);
        this.loading.set(false);
        // Async-резолв имён отменивших — не блокирует рендер; имя подставится при ответе.
        this.resolveCancellerNames(merged);
      },
      error: () => {
        this.error.set('Не удалось загрузить список пар.');
        this.loading.set(false);
      },
    });
  }

  /** Spring HATEOAS PagedModel/CollectionModel _embedded → array. */
  private extractEmbedded(resp: any): any[] {
    if (!resp) return [];
    const embedded = resp._embedded;
    if (!embedded) return Array.isArray(resp) ? resp : [];
    return (Object.values(embedded)[0] as any[]) ?? [];
  }

  /** Конвертирует OneOffLessonResponse в Lesson-shape с дефолтными временами по сетке. */
  private toOneOffLesson(o: any): Lesson {
    const num = Number(o.lessonNumber);
    const slot = LESSON_SLOT_TIMES[num] ?? ['08:30', '09:50'];
    return {
      id: o.id,
      groupId: o.groupId,
      subjectId: o.subjectId,
      date: o.date,
      status: 'PLANNED' as any,
      lessonNumber: num,
      startTime: slot[0],
      endTime: slot[1],
      room: o.classroom ?? null,
      source: 'oneoff',
      // Остальные поля LessonResponse — не нужны для рендера one-off (HATEOAS links и т.п.).
    } as unknown as Lesson;
  }

  private loadSubjects(): void {
    this.headmanApi.listSubjects().subscribe({
      next: (resp) => {
        const embedded = resp?._embedded;
        const list = embedded ? ((Object.values(embedded)[0] as any[]) ?? []) : [];
        this.subjects.set(list);
      },
    });
  }

  /** Batch-резолв display-имён старост/админов, отменивших пары (только для cancelled lessons). */
  private resolveCancellerNames(lessons: Lesson[]): void {
    const known = this.userNames();
    const ids = new Set<number>();
    for (const l of lessons) {
      if (this.isCancelled(l) && l.cancelledBy != null && !known.has(l.cancelledBy)) {
        ids.add(l.cancelledBy);
      }
    }
    if (ids.size === 0) return;
    this.headmanApi.getUsersByIds(Array.from(ids)).subscribe({
      next: (resp: any) => {
        const items = this.extractEmbedded(resp).map((e: any) => e?.content ?? e);
        const next = new Map(this.userNames());
        for (const u of items) {
          if (u?.id && u?.fullName) next.set(u.id, u.fullName);
        }
        this.userNames.set(next);
      },
      error: () => {
        // Тихий degrade — рендер просто не покажет имя, дата отмены остаётся.
      },
    });
  }

  /** Display строка для cancelledAt + (опционально) имя отменившего. Возвращает null если нет cancelledAt. */
  cancellerDisplay(lesson: Lesson): string | null {
    if (!lesson.cancelledAt) return null;
    const dt = new Date(lesson.cancelledAt);
    if (isNaN(dt.getTime())) return null;
    const day = dt.getDate();
    const month = MONTH_NAMES[dt.getMonth()];
    const hh = String(dt.getHours()).padStart(2, '0');
    const mm = String(dt.getMinutes()).padStart(2, '0');
    const dateStr = `${day} ${month} в ${hh}:${mm}`;

    if (lesson.cancelledBy == null) return `Отменена ${dateStr}`;
    const me = this.auth.currentUser();
    if (me && lesson.cancelledBy === me.id) return `Отменена ${dateStr} вами`;
    const name = this.userNames().get(lesson.cancelledBy);
    return name ? `Отменена ${dateStr} — ${name}` : `Отменена ${dateStr}`;
  }

  subjectName(id: number): string {
    return this.subjects().find(s => s.id === id)?.name ?? `Предмет #${id}`;
  }

  /** Полное название типа занятия для рендера рядом с предметом. */
  subjectTypeLabel(id: number): string {
    const t = (this.subjects().find(s => s.id === id) as any)?.type;
    switch (t) {
      case 'LECTURE': return 'Лекция';
      case 'PRACTICE': return 'Практика';
      case 'LAB': return 'Лабораторная';
      default: return '';
    }
  }

  shortTime(t: string): string {
    return t?.length >= 5 ? t.slice(0, 5) : t;
  }

  dayLabel(date: string): string {
    const d = new Date(date + 'T00:00:00');
    const dow = d.getDay();
    return `${DAY_NAMES[dow]}, ${d.getDate()} ${MONTH_NAMES[d.getMonth()]}`;
  }

  isCancelled(l: Lesson): boolean {
    return (l.status ?? '').toUpperCase() === 'CANCELLED';
  }

  isClosed(l: Lesson): boolean {
    return (l.status ?? '').toUpperCase() === 'CLOSED';
  }

  statusKey(l: Lesson): string {
    return (l.status ?? '').toLowerCase();
  }

  statusLabel(l: Lesson): string {
    switch ((l.status ?? '').toUpperCase()) {
      case 'PLANNED': return 'Запланирована';
      case 'ACTIVE': return 'Идёт';
      case 'CLOSED': return 'Завершена';
      case 'CANCELLED': return 'Отменена';
      default: return l.status ?? '';
    }
  }

  onCancel(lesson: Lesson): void {
    // M07 G7 (QC4): Material ConfirmWithReasonDialog вместо window.prompt —
    // focus-trap, inline validation, 500-char limit, destructive-акцент.
    const ref = this.dialog.open<
      ConfirmWithReasonDialogComponent,
      ConfirmWithReasonDialogData,
      string | null
    >(ConfirmWithReasonDialogComponent, {
      data: {
        title: 'Отмена пары',
        message:
          `«${this.subjectName(lesson.subjectId)}» — ` +
          `${this.dayLabel(lesson.date)}, № ${lesson.lessonNumber}`,
        reasonLabel: 'Причина отмены',
        reasonPlaceholder: 'Например: «Преподаватель на больничном»',
        confirmLabel: 'Отменить пару',
        destructive: true,
        maxLength: 500,
      },
    });
    ref.afterClosed().subscribe((trimmed) => {
      if (!trimmed) return;
      this.doCancel(lesson, trimmed);
    });
  }

  private doCancel(lesson: Lesson, trimmed: string): void {
    this.busy.set(lesson.id);
    this.headmanApi.cancelLesson(lesson.id, trimmed).subscribe({
      next: () => {
        this.busy.set(null);
        this.snackBar.open('Пара отменена.', undefined, { duration: 3000 });
        this.applyLocalUpdate(lesson.id, l => ({ ...l, status: 'CANCELLED', cancelReason: trimmed }));
      },
      error: (err) => {
        this.busy.set(null);
        this.snackBar.open(this.errorMessage(err, 'Не удалось отменить пару.'), undefined, { duration: 5000 });
      },
    });
  }

  onRestore(lesson: Lesson): void {
    this.dialog
      .open<ConfirmDialogComponent, ConfirmDialogData, boolean>(ConfirmDialogComponent, {
        data: {
          title: 'Восстановить пару?',
          message: `Пара «${this.subjectName(lesson.subjectId)}» снова появится в расписании и будет учитываться в статистике.`,
          confirmLabel: 'Восстановить',
        },
        autoFocus: 'first-tabbable',
      })
      .afterClosed()
      .subscribe((ok) => {
        if (!ok) return;
        this.busy.set(lesson.id);
        this.headmanApi.restoreLesson(lesson.id).subscribe({
          next: () => {
            this.busy.set(null);
            this.snackBar.open('Пара восстановлена.', undefined, { duration: 3000 });
            this.applyLocalUpdate(lesson.id, l => ({ ...l, status: 'PLANNED', cancelReason: undefined }));
          },
          error: (err) => {
            this.busy.set(null);
            this.snackBar.open(this.errorMessage(err, 'Не удалось восстановить пару.'), undefined, { duration: 5000 });
          },
        });
      });
  }

  private applyLocalUpdate(id: number, fn: (l: Lesson) => Lesson): void {
    this.lessons.update(arr => arr.map(l => l.id === id ? fn(l) : l));
  }

  private errorMessage(err: any, fallback: string): string {
    if (err?.status === 403) return 'Недостаточно прав для этого действия.';
    if (err?.status === 404) return 'Пара не найдена — возможно уже изменена.';
    if (err?.status === 422) return 'Недопустимый переход статуса для этой пары.';
    return fallback;
  }
}
