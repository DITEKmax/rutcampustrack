import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { trigger, transition, style, animate } from '@angular/animations';
import { MatButtonModule } from '@angular/material/button';
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

type Lesson = LessonResponse & { cancelReason?: string };

interface DayGroup {
  date: string;
  label: string;
  lessons: Lesson[];
}

const DAY_NAMES = ['Воскресенье', 'Понедельник', 'Вторник', 'Среда', 'Четверг', 'Пятница', 'Суббота'];
const MONTH_NAMES = [
  'января', 'февраля', 'марта', 'апреля', 'мая', 'июня',
  'июля', 'августа', 'сентября', 'октября', 'ноября', 'декабря',
];

/** Range of dates visible by default — 14 days starting today. */
const DEFAULT_RANGE_DAYS = 14;

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
  imports: [CommonModule, MatButtonModule, MatProgressSpinnerModule],
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
          <h1 class="page-title">Пары на ближайшие 2 недели</h1>
        </div>
      </div>

      @if (loading()) {
        <div class="page-card"><mat-spinner diameter="32"></mat-spinner></div>
      } @else if (error()) {
        <div class="page-error"><i class="ph ph-warning-circle"></i> {{ error() }}</div>
      } @else if (dayGroups().length === 0) {
        <div class="page-empty">
          <i class="ph-duotone ph-calendar-blank"></i>
          <h3>Пар нет</h3>
          <p>На ближайшие {{ rangeDays }} дн. в расписании нет ни одной пары.</p>
        </div>
      } @else {
        @for (day of dayGroups(); track day.date) {
          <div class="page-card day-card">
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
                      @if (isCancelled(lesson) && lesson.cancelReason) {
                        <span class="cancel-reason">— {{ lesson.cancelReason }}</span>
                      }
                    </div>
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
        }
      }
    </div>
  `,
  styles: [`
    .day-card { padding: 16px; }
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
export class HeadmanLessonsComponent implements OnInit {
  private readonly headmanApi = inject(HeadmanApiService);
  private readonly auth = inject(AuthService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly dialog = inject(MatDialog);

  readonly rangeDays = DEFAULT_RANGE_DAYS;

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

  ngOnInit(): void {
    const user = this.auth.currentUser();
    if (!user?.groupId) {
      this.error.set('Не удалось определить группу. Обратитесь к администратору.');
      this.loading.set(false);
      return;
    }
    this.load(user.groupId);
  }

  private load(groupId: number): void {
    this.loading.set(true);
    this.error.set(null);
    const today = new Date();
    const dateFrom = formatDate(today);
    const dateTo = formatDate(addDays(today, this.rangeDays - 1));

    this.headmanApi.getGroupLessons(groupId, dateFrom, dateTo).subscribe({
      next: (resp) => {
        const embedded = resp?._embedded;
        const list: any[] = embedded ? ((Object.values(embedded)[0] as any[]) ?? []) : [];
        const normalised = list.map((entry: any) => {
          const item = entry?.content ?? entry;
          return item as Lesson;
        });
        this.lessons.set(normalised.filter(Boolean));
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Не удалось загрузить список пар.');
        this.loading.set(false);
      },
    });

    // Загружаем предметы параллельно — не блокируем рендер.
    this.headmanApi.listSubjects().subscribe({
      next: (resp) => {
        const embedded = resp?._embedded;
        const list = embedded ? ((Object.values(embedded)[0] as any[]) ?? []) : [];
        this.subjects.set(list);
      },
    });
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
