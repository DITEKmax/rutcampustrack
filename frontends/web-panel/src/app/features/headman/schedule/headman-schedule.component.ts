import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { trigger, transition, style, animate } from '@angular/animations';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatChipsModule } from '@angular/material/chips';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { AuthService } from '../../../core/auth/auth.service';
import { HeadmanApiService } from '../shared/headman-api.service';
import { ScheduleSlotDialogComponent } from './schedule-slot-dialog.component';
import { OneOffDialogComponent } from './one-off-dialog.component';

interface ScheduleItem {
  id: number;
  groupId: number;
  subjectId: number;
  semesterId: number;
  dayOfWeek: number;
  lessonNumber: number;
  startTime: string;
  endTime: string;
  weekType: string; // ALL | ODD | EVEN
  room?: string;
  active?: boolean;
}

interface Subject {
  id: number;
  name: string;
  type?: string;
}

const DAY_LABELS = ['Пн', 'Вт', 'Ср', 'Чт', 'Пт', 'Сб'];
const SLOTS = [1, 2, 3, 4, 5, 6, 7, 8];

/**
 * Сетка пар РУТ МИИТ. Используется только для подписи слотов в матрице —
 * фактическое время хранится в БД на каждом ScheduleItem (при создании
 * староста соглашается с дефолтом из ScheduleSlotDialog, который тоже
 * берётся отсюда).
 */
const SLOT_TIMES: Record<number, string> = {
  1: '08:30–09:50',
  2: '10:05–11:25',
  3: '11:40–13:00',
  4: '13:45–15:05',
  5: '15:20–16:40',
  6: '16:55–18:15',
  7: '18:30–19:50',
  8: '20:00–21:20',
};

/**
 * Russian label for week type. Pure number to match the user's convention
 * (UI shows only «1» / «2» without parity wording).
 * - ODD  = 1-я учебная неделя (идёт на ISO-чётных неделях года)
 * - EVEN = 2-я учебная неделя (идёт на ISO-нечётных неделях года)
 */
function weekTypeLabel(weekType: string): string {
  switch (weekType) {
    case 'ODD': return '1';
    case 'EVEN': return '2';
    default: return 'Каждую';
  }
}

/**
 * ISO 8601 week number (1..53). По его чётности определяется номер текущей
 * учебной недели РУТ МИИТ: ISO-чётный → 1-я учебная, ISO-нечётный → 2-я.
 * Эта же конвенция используется backend'ом при генерации уроков
 * (LessonGenerationService).
 */
function isoWeekNumber(d: Date): number {
  const target = new Date(Date.UTC(d.getFullYear(), d.getMonth(), d.getDate()));
  const dayNum = (target.getUTCDay() + 6) % 7; // 0=Mon..6=Sun
  target.setUTCDate(target.getUTCDate() - dayNum + 3);
  const firstThursday = new Date(Date.UTC(target.getUTCFullYear(), 0, 4));
  const firstThursdayDayNum = (firstThursday.getUTCDay() + 6) % 7;
  firstThursday.setUTCDate(firstThursday.getUTCDate() - firstThursdayDayNum + 3);
  return 1 + Math.round((target.getTime() - firstThursday.getTime()) / (7 * 24 * 3600 * 1000));
}

/**
 * Headman schedule page — `/headman/schedule`.
 *
 * Реализует AC-11: матрица дни×слоты с шаблоном активного семестра для
 * группы старосты. Редактирование слота — через `ScheduleSlotDialogComponent`
 * (D-13). Добавление разовой пары — через `OneOffDialogComponent`.
 *
 * Drag-and-drop НЕ реализован (D-14). Только клик по ячейке → диалог.
 *
 * Источники данных:
 * - активный семестр: `listSemesters()` → пункт с `active=true`
 * - шаблон: `getGroupScheduleItems(groupId, semesterId)`
 * - предметы: загружаются в самих диалогах (listSubjects).
 *
 * groupId берётся из AuthService.currentUser().groupId (JWT claim) —
 * пользователь не может подменить свой groupId (T-60-01).
 */
@Component({
  selector: 'app-headman-schedule',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule,
    MatButtonModule,
    MatIconModule,
    MatChipsModule,
    MatProgressSpinnerModule,
  ],
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
          <h1 class="page-title">Расписание группы</h1>
        </div>
        <div class="page-header__actions">
          <button class="btn-brand" (click)="openOneOffDialog()" [disabled]="!groupId() || !semesterId()">
            <i class="ph ph-plus"></i> Добавить разовую пару
          </button>
        </div>
      </div>

      @if (loading()) {
        <div class="page-card"><mat-spinner diameter="32"></mat-spinner></div>
      } @else if (error()) {
        <div class="page-error"><i class="ph ph-warning-circle"></i> {{ error() }}</div>
      } @else if (!semesterId()) {
        <div class="page-empty">
          <i class="ph-duotone ph-calendar-blank"></i>
          <h3>Нет активного семестра</h3>
          <p>Попросите администратора активировать текущий семестр.</p>
        </div>
      } @else {
        <div class="page-card page-card--flush">
          <div class="current-week-banner" [class.current-week-banner--odd]="currentAcademicWeek() === 1">
            <i class="ph ph-calendar-check"></i>
            Сейчас <strong>{{ currentAcademicWeek() }}-я</strong> учебная неделя
          </div>
          <div class="schedule-matrix">
            <div class="matrix-header">
              <div class="matrix-corner">Пара</div>
              @for (d of dayLabels; track d; let i = $index) {
                <div class="matrix-day">{{ d }}</div>
              }
            </div>
            @for (slot of slots; track slot) {
              <div class="matrix-row">
                <div class="matrix-slot">
                  <span class="matrix-slot__num">{{ slot }}</span>
                  <span class="matrix-slot__time">{{ slotTime(slot) }}</span>
                </div>
                @for (d of dayLabels; track d; let dayIdx = $index) {
                  <div class="matrix-cell"
                       [class.matrix-cell--occupied]="cellsAt(dayIdx, slot).length > 0"
                       [class.matrix-cell--empty]="cellsAt(dayIdx, slot).length === 0"
                       (click)="onCellClick(dayIdx, slot)"
                       [attr.aria-label]="cellsAt(dayIdx, slot).length ? 'Редактировать слот' : 'Создать слот'">
                    @if (cellsAt(dayIdx, slot).length > 0) {
                      @for (item of cellsAt(dayIdx, slot); track item.id) {
                        <div class="cell-entry"
                             [class.cell-entry--odd]="item.weekType === 'ODD'"
                             [class.cell-entry--even]="item.weekType === 'EVEN'"
                             (click)="onEditEntry(item, $event)"
                             role="button"
                             tabindex="0"
                             [attr.aria-label]="'Редактировать ' + subjectName(item.subjectId)">
                          <div class="cell-subject">
                            {{ subjectName(item.subjectId) }}
                            @if (subjectTypeShort(item.subjectId); as t) {
                              <span class="cell-type">· {{ t }}</span>
                            }
                          </div>
                          <div class="cell-meta">
                            @if (item.room) { <span>{{ item.room }}</span> }
                            <mat-chip-set>
                              <mat-chip class="week-chip">{{ weekChip(item.weekType) }}</mat-chip>
                            </mat-chip-set>
                          </div>
                        </div>
                      }
                      <!-- Кнопка «+» — добавить ещё один слот в эту же ячейку
                           (например ODD-пара рядом с EVEN-парой). Скрывается
                           когда уже стоит ALL — туда добавлять нечего. -->
                      @if (canAddMore(dayIdx, slot)) {
                        <button type="button" class="cell-add-btn"
                                (click)="onAddInCell(dayIdx, slot, $event)"
                                aria-label="Добавить ещё пару в эту ячейку">
                          <i class="ph ph-plus"></i> Добавить пару на другую неделю
                        </button>
                      }
                    } @else {
                      <i class="ph ph-plus"></i>
                    }
                  </div>
                }
              </div>
            }
          </div>
        </div>
      }
    </div>
  `,
  styles: [`
    .schedule-matrix { display: flex; flex-direction: column; gap: 4px; padding: var(--space-3); }
    .matrix-header, .matrix-row { display: grid; grid-template-columns: 110px repeat(6, 1fr); gap: 4px; }
    .matrix-corner, .matrix-day {
      padding: var(--space-3);
      text-align: center;
      font: 500 var(--text-xs)/1 var(--font-mono);
      letter-spacing: var(--tracking-wide);
      text-transform: uppercase;
      color: var(--text-muted);
    }
    .matrix-day {
      background: var(--bg-surface);
      border: 1px solid var(--border-subtle);
      border-radius: var(--radius-md);
    }
    .matrix-slot {
      background: var(--bg-surface);
      border: 1px solid var(--border-subtle);
      border-radius: var(--radius-md);
      padding: var(--space-2);
      text-align: center;
      display: flex; flex-direction: column; align-items: center; gap: 2px;
    }
    .matrix-slot__num {
      font: 700 0.95rem/1 var(--font-display);
      color: var(--text-primary);
    }
    .matrix-slot__time {
      font-family: var(--font-mono);
      font-variant-numeric: tabular-nums;
      font-size: 0.72rem;
      color: var(--text-muted);
    }
    .matrix-cell {
      min-height: 72px; padding: var(--space-2);
      border-radius: var(--radius-md);
      background: var(--bg-surface);
      border: 1px solid var(--border-subtle);
      cursor: pointer;
      display: flex; flex-direction: column; justify-content: center; gap: 4px;
      transition:
        background-color var(--duration-base) var(--ease-out),
        border-color var(--duration-base) var(--ease-out);
    }
    .matrix-cell:hover {
      background: var(--bg-elevated);
      border-color: var(--border-default);
    }
    .matrix-cell--empty {
      opacity: 0.5;
      text-align: center;
      color: var(--text-muted);
    }
    .matrix-cell--empty:hover { opacity: 1; color: var(--accent-primary); }
    .matrix-cell--occupied {
      background: color-mix(in oklab, var(--accent-primary) 10%, transparent);
      border-color: color-mix(in oklab, var(--accent-primary) 35%, transparent);
    }
    .matrix-cell--occupied:hover {
      background: color-mix(in oklab, var(--accent-primary) 16%, transparent);
      border-color: color-mix(in oklab, var(--accent-primary) 50%, transparent);
    }
    .cell-entry {
      padding: 2px 4px;
      border-radius: var(--radius-sm);
      cursor: pointer;
      transition: background-color var(--duration-base) var(--ease-out);
    }
    .cell-entry:hover { background: color-mix(in oklab, var(--accent-primary) 14%, transparent); }
    .cell-entry + .cell-entry {
      border-top: 1px dashed color-mix(in oklab, var(--accent-primary) 35%, transparent);
      padding-top: 4px; margin-top: 2px;
    }
    .cell-add-btn {
      margin-top: 4px; width: 100%;
      padding: 4px 6px;
      border: 1px dashed color-mix(in oklab, var(--accent-primary) 40%, transparent);
      border-radius: var(--radius-sm);
      background: transparent;
      color: var(--accent-primary);
      font-family: var(--font-sans);
      font-size: 0.72rem;
      cursor: pointer;
      display: flex; align-items: center; justify-content: center; gap: 4px;
      transition: background-color var(--duration-base) var(--ease-out);
    }
    .cell-add-btn:hover { background: color-mix(in oklab, var(--accent-primary) 10%, transparent); }
    .cell-add-btn i { font-size: 0.85rem; }
    .cell-subject {
      font: 600 0.875rem/1.3 var(--font-heading);
      color: var(--text-primary);
    }
    .cell-type {
      font-weight: 500; font-size: 0.75rem;
      color: var(--text-secondary);
    }
    .cell-meta {
      display: flex; align-items: center; gap: var(--space-2);
      font-size: 0.75rem;
      color: var(--text-secondary);
    }
    .week-chip { font-size: 0.7rem; }
    .current-week-banner {
      display: inline-flex; align-items: center; gap: var(--space-2);
      padding: var(--space-3) var(--space-4);
      margin: var(--space-3) var(--space-3) 0;
      border-radius: var(--radius-md);
      background: color-mix(in oklab, var(--accent-warning) 12%, transparent);
      border: 1px solid color-mix(in oklab, var(--accent-warning) 28%, transparent);
      color: var(--accent-warning);
      font-size: 0.875rem;
    }
    .current-week-banner strong { color: var(--text-primary); font-weight: 600; }
    .current-week-banner i { font-size: 1.1rem; }
    .current-week-banner--odd {
      background: color-mix(in oklab, var(--accent-secondary) 12%, transparent);
      border-color: color-mix(in oklab, var(--accent-secondary) 28%, transparent);
      color: var(--accent-secondary);
    }
  `],
})
export class HeadmanScheduleComponent implements OnInit {
  private readonly headmanApi = inject(HeadmanApiService);
  private readonly auth = inject(AuthService);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);

  readonly dayLabels = DAY_LABELS;
  readonly slots = SLOTS;

  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly items = signal<ScheduleItem[]>([]);
  readonly subjects = signal<Subject[]>([]);
  readonly groupId = signal<number | null>(null);
  readonly semesterId = signal<number | null>(null);

  ngOnInit(): void {
    const user = this.auth.currentUser();
    if (!user?.groupId) {
      this.error.set('Не удалось определить группу. Обратитесь к администратору.');
      this.loading.set(false);
      return;
    }
    this.groupId.set(user.groupId);
    this.loadActiveSemesterAndSchedule();
    this.loadSubjects();
  }

  private loadActiveSemesterAndSchedule(): void {
    this.loading.set(true);
    this.headmanApi.listSemesters().subscribe({
      next: (resp) => {
        const embedded = resp?._embedded;
        const list = embedded
          ? ((Object.values(embedded)[0] as any[]) ?? [])
          : (Array.isArray(resp) ? resp : []);
        const active = list.find((s: any) => s.active === true);
        if (!active) {
          this.semesterId.set(null);
          this.loading.set(false);
          return;
        }
        this.semesterId.set(active.id);
        this.loadSchedule();
      },
      error: () => {
        this.error.set('Не удалось загрузить список семестров.');
        this.loading.set(false);
      },
    });
  }

  loadSchedule(): void {
    const g = this.groupId();
    const s = this.semesterId();
    if (!g || !s) return;
    this.loading.set(true);
    this.headmanApi.getGroupScheduleItems(g, s).subscribe({
      next: (resp) => {
        const embedded = resp?._embedded;
        const list = embedded ? ((Object.values(embedded)[0] as any[]) ?? []) : [];
        // HATEOAS EntityModel shape may wrap content; normalise to plain objects.
        const normalized = list.map((entry: any) => {
          const item = entry?.content ?? entry;
          return item as ScheduleItem;
        });
        this.items.set(normalized.filter((i: any) => i && i.active !== false));
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Не удалось загрузить расписание.');
        this.loading.set(false);
      },
    });
  }

  private loadSubjects(): void {
    this.headmanApi.listSubjects().subscribe({
      next: (resp) => {
        const embedded = resp?._embedded;
        const list = embedded ? ((Object.values(embedded)[0] as any[]) ?? []) : [];
        this.subjects.set(list);
      },
      error: () => {
        // Non-fatal; matrix will still render with placeholder subject names.
      },
    });
  }

  /** Ячейка для дня (0..5 = Пн..Сб, mapped to dayOfWeek=1..6) и слота. */
  cellAt(dayIdx: number, slot: number): ScheduleItem | null {
    return this.cellsAt(dayIdx, slot)[0] ?? null;
  }

  /** Все слоты в ячейке — может быть несколько (например ODD + EVEN). */
  cellsAt(dayIdx: number, slot: number): ScheduleItem[] {
    const dayOfWeek = dayIdx + 1;
    return this.items().filter(i => i.dayOfWeek === dayOfWeek && i.lessonNumber === slot);
  }

  /**
   * Можно ли в этой ячейке создать ещё один слот.
   * - Пустая → false (для пустой и так есть основная клик-зона).
   * - Уже есть ALL → false (ALL покрывает обе недели).
   * - Уже есть ODD И EVEN → false (обе недели заняты).
   * - В остальных случаях — true.
   */
  canAddMore(dayIdx: number, slot: number): boolean {
    const items = this.cellsAt(dayIdx, slot);
    if (items.length === 0) return false;
    if (items.some(i => i.weekType === 'ALL')) return false;
    const hasOdd = items.some(i => i.weekType === 'ODD');
    const hasEven = items.some(i => i.weekType === 'EVEN');
    return !(hasOdd && hasEven);
  }

  /**
   * Номер текущей учебной недели в конвенции РУТ МИИТ (1 или 2).
   *
   * Чётность «учебной» недели привязана к ISO-номеру недели года (а не к
   * дате начала семестра): ISO-чётный номер → 1-я учебная неделя,
   * ISO-нечётный → 2-я. Этой же маппинг использует backend при генерации
   * уроков (LessonGenerationService: isoEven → WeekType.ODD → «1»).
   */
  currentAcademicWeek(): 1 | 2 {
    return isoWeekNumber(new Date()) % 2 === 0 ? 1 : 2;
  }

  subjectName(subjectId: number): string {
    return this.subjects().find(s => s.id === subjectId)?.name ?? `Предмет #${subjectId}`;
  }

  /**
   * Короткая подпись типа занятия для рендера в матрице («лек», «пр», «лаб»).
   * Возвращает пустую строку если у предмета не указан тип — тогда чип
   * не рисуется (помогает старосте отличить одноимённые лекции/практики).
   */
  subjectTypeShort(subjectId: number): string {
    const t = this.subjects().find(s => s.id === subjectId)?.type;
    switch (t) {
      case 'LECTURE': return 'лек';
      case 'PRACTICE': return 'пр';
      case 'LAB': return 'лаб';
      default: return '';
    }
  }

  weekChip(weekType: string): string {
    return weekTypeLabel(weekType);
  }

  /** Подпись слота слева в матрице: «1 пара / 08:30–09:50». */
  slotTime(slot: number): string {
    return SLOT_TIMES[slot] ?? '';
  }

  onCellClick(dayIdx: number, slot: number): void {
    const items = this.cellsAt(dayIdx, slot);
    const g = this.groupId();
    const s = this.semesterId();
    if (!g || !s) return;
    // Пустая клетка → создаём.
    if (items.length === 0) {
      this.openSlotDialog({ mode: 'create', groupId: g, semesterId: s, dayOfWeek: dayIdx + 1, lessonNumber: slot });
      return;
    }
    // Один слот → сразу edit (старое поведение).
    if (items.length === 1) {
      this.openSlotDialog({ mode: 'edit', groupId: g, semesterId: s, item: items[0] });
      return;
    }
    // Несколько слотов (ODD+EVEN, или ALL+что-то ещё): нечего гадать —
    // редактируем первый. Чтобы добавить ещё один, староста использует
    // отдельный клик по «+» под ячейкой.
    this.openSlotDialog({ mode: 'edit', groupId: g, semesterId: s, item: items[0] });
  }

  /** Кнопка-«+» под занятой ячейкой — открыть create-диалог. */
  onAddInCell(dayIdx: number, slot: number, ev: MouseEvent): void {
    ev.stopPropagation();
    const g = this.groupId();
    const s = this.semesterId();
    if (!g || !s) return;
    this.openSlotDialog({ mode: 'create', groupId: g, semesterId: s, dayOfWeek: dayIdx + 1, lessonNumber: slot });
  }

  /** Клик по конкретному слоту в многослойной ячейке. */
  onEditEntry(item: ScheduleItem, ev: MouseEvent): void {
    ev.stopPropagation();
    const g = this.groupId();
    const s = this.semesterId();
    if (!g || !s) return;
    this.openSlotDialog({ mode: 'edit', groupId: g, semesterId: s, item });
  }

  private openSlotDialog(data: any): void {
    const ref = this.dialog.open(ScheduleSlotDialogComponent, {
      width: '480px',
      maxWidth: '95vw',
      ariaLabel: data.mode === 'edit' ? 'Редактировать слот' : 'Новый слот',
      data,
    });
    ref.afterClosed().subscribe(result => { if (result) this.loadSchedule(); });
  }

  openOneOffDialog(): void {
    const g = this.groupId();
    if (!g) return;
    const ref = this.dialog.open(OneOffDialogComponent, {
      width: '480px',
      maxWidth: '95vw',
      ariaLabel: 'Добавить разовую пару',
      data: { groupId: g },
    });
    ref.afterClosed().subscribe(result => {
      if (result) {
        this.snackBar.open('Разовая пара добавлена.', undefined, { duration: 3000 });
      }
    });
  }
}
