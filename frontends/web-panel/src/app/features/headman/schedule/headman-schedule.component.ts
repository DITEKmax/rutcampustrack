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

/** Russian label for week type. */
function weekTypeLabel(weekType: string): string {
  switch (weekType) {
    case 'ODD': return '2-я (нечёт)';
    case 'EVEN': return '1-я (чёт)';
    default: return 'Каждую';
  }
}

/** Returns Monday 00:00 of the week containing `d` (local time). */
function mondayOf(d: Date): Date {
  const m = new Date(d.getFullYear(), d.getMonth(), d.getDate());
  const dow = m.getDay(); // 0=Sun..6=Sat
  const diff = (dow + 6) % 7; // distance back to Monday
  m.setDate(m.getDate() - diff);
  return m;
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
          <div class="current-week-banner" [class.current-week-banner--odd]="currentWeekIsOdd()">
            <i class="ph ph-calendar-check"></i>
            Сейчас идёт <strong>{{ currentWeekIsOdd() ? '2-я (нечётная)' : '1-я (чётная)' }}</strong>
            учебная неделя
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
                <div class="matrix-slot">{{ slot }}</div>
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
                             [class.cell-entry--even]="item.weekType === 'EVEN'">
                          <div class="cell-subject">{{ subjectName(item.subjectId) }}</div>
                          <div class="cell-meta">
                            @if (item.room) { <span>{{ item.room }}</span> }
                            <mat-chip-set>
                              <mat-chip class="week-chip">{{ weekChip(item.weekType) }}</mat-chip>
                            </mat-chip-set>
                          </div>
                        </div>
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
    .schedule-matrix { display: flex; flex-direction: column; gap: 4px; padding: 8px; }
    .matrix-header, .matrix-row { display: grid; grid-template-columns: 60px repeat(6, 1fr); gap: 4px; }
    .matrix-corner, .matrix-day, .matrix-slot { font-weight: 600; padding: 8px; text-align: center; }
    .matrix-day { background: var(--bg-secondary, #f5f5f5); border-radius: 4px; }
    .matrix-slot { background: var(--bg-secondary, #f5f5f5); border-radius: 4px; }
    .matrix-cell {
      min-height: 72px; padding: 6px; border-radius: 6px;
      background: var(--bg-elevated, #fafafa); cursor: pointer;
      display: flex; flex-direction: column; justify-content: center; gap: 4px;
      transition: background 150ms ease, border-color 150ms ease;
      border: 1px solid transparent;
    }
    .matrix-cell:hover { background: var(--bg-hover, #eee); }
    .matrix-cell--empty { opacity: 0.5; text-align: center; }
    .matrix-cell--occupied {
      background: #e8f5e9;
      border-color: #66bb6a;
    }
    .matrix-cell--occupied:hover { background: #d7eed9; }
    .cell-entry { padding: 2px 4px; border-radius: 4px; }
    .cell-entry + .cell-entry { border-top: 1px dashed #a5d6a7; padding-top: 4px; margin-top: 2px; }
    .cell-entry--odd { color: #1b5e20; }
    .cell-entry--even { color: #2e7d32; }
    .cell-subject { font-weight: 600; font-size: 0.9rem; color: #1b5e20; }
    .cell-meta { display: flex; align-items: center; gap: 8px; font-size: 0.75rem; color: #2e7d32; }
    .week-chip { font-size: 0.7rem; }
    .current-week-banner {
      display: flex; align-items: center; gap: 8px;
      padding: 10px 14px; margin: 8px 8px 0; border-radius: 8px;
      background: #fff8e1; color: #6d4c00;
      font-size: 0.9rem;
    }
    .current-week-banner i { font-size: 1.1rem; }
    .current-week-banner--odd {
      background: #e1f5fe; color: #01579b;
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
  readonly semesterDateFrom = signal<string | null>(null);
  readonly semesterFirstWeekIsOdd = signal<boolean>(false);

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
        this.semesterDateFrom.set(active.dateFrom ?? null);
        const fwt = (active.firstWeekType ?? 'ODD').toUpperCase();
        this.semesterFirstWeekIsOdd.set(fwt !== 'EVEN');
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
   * Идёт ли сейчас нечётная неделя относительно начала семестра.
   * Используется в баннере над матрицей. Если данные семестра не загружены,
   * считаем неделю чётной (=1-я по соглашению пользователя).
   */
  currentWeekIsOdd(): boolean {
    const start = this.semesterDateFrom();
    if (!start) return false;
    const startDate = new Date(start);
    if (Number.isNaN(startDate.getTime())) return false;
    const startMonday = mondayOf(startDate);
    const todayMonday = mondayOf(new Date());
    const weekDelta = Math.round((todayMonday.getTime() - startMonday.getTime()) / (7 * 24 * 3600 * 1000));
    const firstIsOdd = this.semesterFirstWeekIsOdd();
    const currentIsOdd = (weekDelta % 2 === 0) ? firstIsOdd : !firstIsOdd;
    return currentIsOdd;
  }

  subjectName(subjectId: number): string {
    return this.subjects().find(s => s.id === subjectId)?.name ?? `Предмет #${subjectId}`;
  }

  weekChip(weekType: string): string {
    return weekTypeLabel(weekType);
  }

  onCellClick(dayIdx: number, slot: number): void {
    const item = this.cellAt(dayIdx, slot);
    const g = this.groupId();
    const s = this.semesterId();
    if (!g || !s) return;
    const ref = this.dialog.open(ScheduleSlotDialogComponent, {
      width: '480px',
      maxWidth: '95vw',
      ariaLabel: item ? 'Редактировать слот' : 'Новый слот',
      data: item
        ? { mode: 'edit', groupId: g, semesterId: s, item }
        : { mode: 'create', groupId: g, semesterId: s, dayOfWeek: dayIdx + 1, lessonNumber: slot },
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
