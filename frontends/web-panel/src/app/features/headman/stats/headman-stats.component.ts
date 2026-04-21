import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  inject,
  signal,
} from '@angular/core';
import { trigger, transition, style, animate } from '@angular/animations';
import { forkJoin, of } from 'rxjs';
import { catchError, finalize, map, switchMap } from 'rxjs/operators';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatSnackBarModule } from '@angular/material/snack-bar';
import { HeadmanApiService } from '../shared/headman-api.service';
import { AuthService } from '../../../core/auth/auth.service';
import type { SubjectType } from '../../../api/schema';

interface StatsRow {
  subjectId: number;
  subjectName: string;
  subjectType?: SubjectType | null;
  groupAveragePercent: number;
  threshold: number;
  isRedZone: boolean;
}

const TYPE_LABEL: Record<SubjectType, string> = {
  LECTURE: 'Лек',
  PRACTICE: 'Пр',
  LAB: 'Лаб',
};
const TYPE_PILL: Record<SubjectType, string> = {
  LECTURE: 'pill--info',      // --accent-secondary (blue)
  PRACTICE: 'pill--success',  // --accent-primary (green)
  LAB: 'pill--violet',        // --accent-info (violet)
};

function computeAttendanceRate(journal: any): number {
  const allCells: any[] = [];
  (journal?.students ?? []).forEach((row: any) =>
    (row.records ?? []).forEach((cell: any) => {
      if (cell.status !== 'cancelled') allCells.push(cell);
    })
  );
  if (allCells.length === 0) return 0;
  const attended = allCells.filter(c =>
    c.status === 'present' || c.status === 'excused' || c.status === 'free_attendance'
  ).length;
  return Math.round((attended / allCells.length) * 100);
}

@Component({
  selector: 'app-headman-stats',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatSnackBarModule],
  animations: [
    trigger('routeFade', [
      transition(':enter', [
        style({ opacity: 0, transform: 'translateY(8px)' }),
        animate('200ms cubic-bezier(0.16, 1, 0.3, 1)', style({ opacity: 1, transform: 'translateY(0)' })),
      ]),
    ]),
  ],
  host: { '[@routeFade]': '' },
  styles: [`
    :host { display: block; }

    .subject-cell { display: inline-flex; align-items: center; gap: var(--space-3); }
    .subject-cell__name { font-weight: 500; }

    .percent-cell {
      font-family: var(--font-mono);
      font-size: var(--text-sm);
      font-variant-numeric: tabular-nums;
      font-weight: 600;
    }
    .percent-cell--good { color: var(--accent-primary); }
    .percent-cell--warn { color: var(--accent-warning); }
    .percent-cell--bad  { color: var(--accent-danger); }

    .threshold-input {
      width: 80px;
      padding: 6px 10px;
      border: 1px solid var(--border-subtle);
      border-radius: var(--radius-md);
      background: var(--bg-surface);
      color: var(--text-primary);
      font-family: var(--font-mono);
      font-size: var(--text-sm);
      font-variant-numeric: tabular-nums;
      transition: border-color var(--duration-base) var(--ease-out);
    }
    .threshold-input:focus {
      outline: none;
      border-color: var(--accent-primary);
    }

    .col-threshold { width: 140px; }
    .col-rate { width: 220px; }
  `],
  template: `
    <div class="page-header">
      <h1>Статистика группы</h1>
      <span class="page-header__eyebrow">Староста</span>
    </div>

    @if (loading()) {
      <div class="page-card">
        <span aria-live="polite" class="sr-only">Загрузка статистики...</span>
      </div>
    } @else if (error()) {
      <div class="page-error" role="alert">
        <i class="ph ph-warning-circle"></i>
        {{ error() }}
      </div>
    } @else if (statsRows().length === 0) {
      <div class="page-card">
        <div class="page-empty" role="status" aria-live="polite">
          <div class="page-empty__icon"><i class="ph ph-chart-bar"></i></div>
          <p class="page-empty__title">Нет данных</p>
          <p class="page-empty__text">Статистика появится после добавления предметов и проведения занятий.</p>
        </div>
      </div>
    } @else {
      <div class="table-card">
        <div class="table-card__scroll">
          <table class="table-card__table">
            <thead>
              <tr>
                <th scope="col">Предмет</th>
                <th scope="col" class="col-rate">Посещаемость группы</th>
                <th scope="col" class="col-threshold">Порог (%)</th>
              </tr>
            </thead>
            <tbody>
              @for (row of statsRows(); track row.subjectId) {
                <tr class="table-card__row">
                  <td>
                    <span class="subject-cell">
                      <span class="subject-cell__name">{{ row.subjectName }}</span>
                      @if (row.subjectType) {
                        <span class="pill {{ pillClass(row.subjectType) }}">{{ typeLabel(row.subjectType) }}</span>
                      }
                    </span>
                  </td>
                  <td class="percent-cell {{ percentClass(row.groupAveragePercent) }}">
                    {{ row.groupAveragePercent }}%
                  </td>
                  <td>
                    <input
                      type="number"
                      class="threshold-input"
                      [value]="row.threshold"
                      min="0" max="100"
                      [attr.aria-label]="'Порог для предмета ' + row.subjectName + ', процент'"
                      (blur)="onThresholdBlur(row, $event)"
                      (keydown.enter)="onThresholdEnter(row, $event)"
                      (keydown.escape)="onThresholdEscape(row, $event)"
                    />
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      </div>
    }
  `,
})
export class HeadmanStatsComponent implements OnInit {
  private readonly headmanApi = inject(HeadmanApiService);
  private readonly authService = inject(AuthService);
  private readonly snackBar = inject(MatSnackBar);

  statsRows = signal<StatsRow[]>([]);
  loading = signal(true);
  error = signal<string | null>(null);

  typeLabel(t: SubjectType): string {
    return TYPE_LABEL[t];
  }
  pillClass(t: SubjectType): string {
    return TYPE_PILL[t];
  }
  percentClass(p: number): string {
    if (p >= 80) return 'percent-cell--good';
    if (p >= 50) return 'percent-cell--warn';
    return 'percent-cell--bad';
  }

  ngOnInit(): void {
    const groupId = this.authService.currentUser()?.groupId;
    if (!groupId) {
      this.error.set('Ошибка: не удалось определить группу.');
      this.loading.set(false);
      return;
    }

    const today = new Date().toISOString().split('T')[0];
    const ninetyDaysAgo = new Date(Date.now() - 90 * 24 * 60 * 60 * 1000).toISOString().split('T')[0];

    this.headmanApi.listSubjects(0, 100)
      .pipe(
        catchError(() => of(null)),
        switchMap((subjectsPage: any) => {
          if (!subjectsPage) return of([]);
          const embedded = subjectsPage._embedded ?? {};
          const subjects: any[] = embedded[Object.keys(embedded)[0]] ?? [];
          if (subjects.length === 0) return of([]);

          const requests = subjects.map((s: any) =>
            forkJoin({
              journal: this.headmanApi.getJournal(groupId, s.id, ninetyDaysAgo, today)
                .pipe(catchError(() => of(null))),
              threshold: this.headmanApi.resolveThreshold(groupId, s.id)
                .pipe(catchError(() => of({ minPercentage: 75 }))),
            }).pipe(
              map(({ journal, threshold }) => {
                const rate = journal ? computeAttendanceRate(journal) : 0;
                const thresh = threshold?.minPercentage ?? 75;
                return {
                  subjectId: s.id,
                  subjectName: s.name,
                  subjectType: (s.type ?? null) as SubjectType | null,
                  groupAveragePercent: rate,
                  threshold: thresh,
                  isRedZone: rate < thresh,
                } as StatsRow;
              })
            )
          );

          return forkJoin(requests);
        }),
        catchError(() => {
          this.error.set('Не удалось загрузить данные. Обновите страницу или попробуйте позже.');
          return of([]);
        }),
        finalize(() => this.loading.set(false)),
      )
      .subscribe(rows => this.statsRows.set(rows as StatsRow[]));
  }

  onThresholdBlur(row: StatsRow, event: Event): void {
    const input = event.target as HTMLInputElement;
    const value = parseInt(input.value, 10);
    if (isNaN(value) || value < 0 || value > 100) {
      input.value = String(row.threshold);
      return;
    }
    if (value === row.threshold) return;
    this.saveThreshold(row, value, input);
  }

  onThresholdEnter(row: StatsRow, event: Event): void {
    (event.target as HTMLElement).blur();
  }

  onThresholdEscape(row: StatsRow, event: Event): void {
    const input = event.target as HTMLInputElement;
    input.value = String(row.threshold);
    input.blur();
  }

  private saveThreshold(row: StatsRow, newValue: number, input: HTMLInputElement): void {
    const prevValue = row.threshold;
    row.threshold = newValue;
    row.isRedZone = row.groupAveragePercent < newValue;
    this.statsRows.set([...this.statsRows()]);

    this.headmanApi.setSubjectThreshold(row.subjectId, newValue)
      .pipe(
        catchError(() => {
          row.threshold = prevValue;
          row.isRedZone = row.groupAveragePercent < prevValue;
          input.value = String(prevValue);
          this.statsRows.set([...this.statsRows()]);
          this.snackBar.open('Не удалось сохранить порог. Попробуйте снова.', undefined, { duration: 4000 });
          return of(null);
        })
      )
      .subscribe();
  }
}
