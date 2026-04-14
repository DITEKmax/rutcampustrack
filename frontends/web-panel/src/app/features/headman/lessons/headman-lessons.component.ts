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
import { AuthService } from '../../../core/auth/auth.service';
import { HeadmanApiService } from '../shared/headman-api.service';
import { addDays, formatDate } from '../../student/schedule/week-utils';

interface Lesson {
  id: number;
  groupId: number;
  subjectId: number;
  date: string; // ISO YYYY-MM-DD
  status: string; // PLANNED | ACTIVE | CLOSED | CANCELLED (или lowercase)
  lessonNumber: number;
  startTime: string;
  endTime: string;
  weekType: string;
  room?: string;
  cancelReason?: string;
}

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
 * - Reason берётся через window.prompt() — без отдельного диалога, чтобы не
 *   плодить компоненты под одну форму.
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
                    <div class="lesson-subject">{{ subjectName(lesson.subjectId) }}</div>
                    <div class="lesson-meta">
                      @if (lesson.room) { <span>{{ lesson.room }}</span> }
                      <span class="status status--{{ statusKey(lesson) }}">{{ statusLabel(lesson) }}</span>
                      @if (isCancelled(lesson) && lesson.cancelReason) {
                        <span class="cancel-reason">— {{ lesson.cancelReason }}</span>
                      }
                    </div>
                  </div>
                  <div class="lesson-row__actions">
                    @if (!isCancelled(lesson) && !isClosed(lesson)) {
                      <button class="btn-stroke btn-stroke--danger" type="button"
                              [disabled]="busy() === lesson.id"
                              (click)="onCancel(lesson)">
                        <i class="ph ph-x-circle"></i> Отменить
                      </button>
                    } @else if (isCancelled(lesson)) {
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
      gap: 12px;
      align-items: center;
      padding: 10px 12px;
      border: 1px solid var(--border-subtle, #e0e0e0);
      border-radius: 8px;
      background: var(--bg-elevated, #fafafa);
    }
    .lesson-row--cancelled { opacity: 0.6; background: #fafafa; }
    .lesson-row__time { display: flex; flex-direction: column; gap: 2px; }
    .lesson-num { font-size: 0.75rem; color: var(--text-muted); }
    .lesson-time { font-family: var(--font-mono, monospace); font-variant-numeric: tabular-nums; font-size: 0.85rem; }
    .lesson-subject { font-weight: 600; }
    .lesson-meta {
      display: flex; gap: 8px; align-items: center; flex-wrap: wrap;
      font-size: 0.8rem; color: var(--text-muted); margin-top: 2px;
    }
    .status {
      display: inline-block;
      padding: 1px 8px;
      border-radius: 10px;
      font-size: 0.75rem;
      font-weight: 500;
    }
    .status--planned { background: #e3f2fd; color: #1565c0; }
    .status--active { background: #e8f5e9; color: #2e7d32; }
    .status--closed { background: #f5f5f5; color: #616161; }
    .status--cancelled { background: #ffebee; color: #c62828; }
    .cancel-reason { font-style: italic; }
    .btn-stroke {
      padding: 6px 12px;
      border-radius: 6px;
      border: 1px solid var(--border-subtle);
      background: transparent;
      cursor: pointer;
      font-size: 0.85rem;
      display: inline-flex; align-items: center; gap: 4px;
      transition: background 150ms ease;
    }
    .btn-stroke:hover:not([disabled]) { background: var(--bg-hover, #eee); }
    .btn-stroke[disabled] { opacity: 0.5; cursor: not-allowed; }
    .btn-stroke--danger { color: #c62828; border-color: #ef9a9a; }
    .btn-stroke--danger:hover:not([disabled]) { background: #ffebee; }
  `],
})
export class HeadmanLessonsComponent implements OnInit {
  private readonly headmanApi = inject(HeadmanApiService);
  private readonly auth = inject(AuthService);
  private readonly snackBar = inject(MatSnackBar);

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
    const reason = window.prompt(
      `Причина отмены пары «${this.subjectName(lesson.subjectId)}» ` +
      `(${this.dayLabel(lesson.date)}, № ${lesson.lessonNumber}):`,
    );
    if (reason === null) return;
    const trimmed = reason.trim();
    if (!trimmed) {
      this.snackBar.open('Причина отмены не может быть пустой.', undefined, { duration: 4000 });
      return;
    }
    if (trimmed.length > 512) {
      this.snackBar.open('Причина не должна превышать 512 символов.', undefined, { duration: 4000 });
      return;
    }
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
    if (!window.confirm(`Восстановить пару «${this.subjectName(lesson.subjectId)}»?`)) return;
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
