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

const DAY_LABELS = ['Пн', 'Вт', 'Ср', 'Чт', 'Пт'];
const SLOTS = [1, 2, 3, 4, 5, 6, 7, 8];

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
                       [class.matrix-cell--empty]="!cellAt(dayIdx, slot)"
                       (click)="onCellClick(dayIdx, slot)"
                       [attr.aria-label]="cellAt(dayIdx, slot) ? 'Редактировать слот' : 'Создать слот'">
                    @if (cellAt(dayIdx, slot); as item) {
                      <div class="cell-subject">{{ subjectName(item.subjectId) }}</div>
                      <div class="cell-meta">
                        @if (item.room) { <span>{{ item.room }}</span> }
                        <mat-chip-set>
                          <mat-chip class="week-chip">{{ weekChip(item.weekType) }}</mat-chip>
                        </mat-chip-set>
                      </div>
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
    .matrix-header, .matrix-row { display: grid; grid-template-columns: 60px repeat(5, 1fr); gap: 4px; }
    .matrix-corner, .matrix-day, .matrix-slot { font-weight: 600; padding: 8px; text-align: center; }
    .matrix-day { background: var(--bg-secondary, #f5f5f5); border-radius: 4px; }
    .matrix-slot { background: var(--bg-secondary, #f5f5f5); border-radius: 4px; }
    .matrix-cell {
      min-height: 72px; padding: 8px; border-radius: 6px;
      background: var(--bg-elevated, #fafafa); cursor: pointer;
      display: flex; flex-direction: column; justify-content: center; gap: 4px;
      transition: background 150ms ease;
    }
    .matrix-cell:hover { background: var(--bg-hover, #eee); }
    .matrix-cell--empty { opacity: 0.5; text-align: center; }
    .cell-subject { font-weight: 600; font-size: 0.9rem; }
    .cell-meta { display: flex; align-items: center; gap: 8px; font-size: 0.8rem; color: var(--text-muted); }
    .week-chip { font-size: 0.7rem; }
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

  /** Ячейка для дня (0..4 = Пн..Пт, mapped to dayOfWeek=1..5) и слота. */
  cellAt(dayIdx: number, slot: number): ScheduleItem | null {
    const dayOfWeek = dayIdx + 1;
    return this.items().find(i => i.dayOfWeek === dayOfWeek && i.lessonNumber === slot) ?? null;
  }

  subjectName(subjectId: number): string {
    return this.subjects().find(s => s.id === subjectId)?.name ?? `Предмет #${subjectId}`;
  }

  weekChip(weekType: string): string {
    switch (weekType) {
      case 'ODD': return 'Нечёт';
      case 'EVEN': return 'Чёт';
      default: return 'Все';
    }
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
