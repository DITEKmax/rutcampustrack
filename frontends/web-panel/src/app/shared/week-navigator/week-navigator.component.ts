import {
  ChangeDetectionStrategy,
  Component,
  EventEmitter,
  Input,
  OnInit,
  Output,
  computed,
  signal,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import {
  addDays,
  formatWeekRange,
  getMonday,
  isSameWeek,
} from '../../features/student/schedule/week-utils';

/**
 * Reusable week navigation strip (← label → + «Сегодня» pill).
 *
 * Phase 61 / D-08: extracted as a shared component so `/headman/homework` can
 * reuse the same visual/behavioural contract as the student schedule week-nav.
 * The student schedule page (Phase 51) has this logic inline; the headman
 * schedule matrix has a fixed current-week-only view without navigation. This
 * component is the first shared widget in `shared/` — there is no existing
 * shared/ folder, so it is created alongside.
 *
 * Contract:
 * - `@Input() initialDate`   — optional seed date (default: today). Only read
 *    on init; caller controls subsequent jumps via the `weekChanged` output.
 * - `@Output() weekChanged`  — fires on init AND on any ←/→/«Сегодня» click
 *    with `{ monday, weekOffset }`. `weekOffset` is integer weeks from the
 *    initial monday (0 = same week). Emission on init gives the parent the
 *    baseline without having to compute it separately.
 *
 * Week model: ISO Monday-Saturday (Sunday collapses into Saturday, matches
 * `week-utils.ts` which is also used by the PWA and student schedule).
 */
@Component({
  selector: 'app-week-navigator',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule],
  template: `
    <nav class="week-nav" aria-label="Навигация по неделям">
      <button
        type="button"
        class="week-nav__btn"
        (click)="previous()"
        aria-label="Предыдущая неделя">
        <i class="ph ph-caret-left" aria-hidden="true"></i>
      </button>
      <div class="week-nav__label">
        <span class="week-nav__eyebrow">Неделя</span>
        <span class="week-nav__range">{{ weekLabel() }}</span>
      </div>
      <button
        type="button"
        class="week-nav__btn"
        (click)="next()"
        aria-label="Следующая неделя">
        <i class="ph ph-caret-right" aria-hidden="true"></i>
      </button>
      @if (!isCurrentWeek()) {
        <button
          type="button"
          class="week-nav__today"
          (click)="today()"
          aria-label="Перейти на текущую неделю">
          <i class="ph ph-calendar-blank" aria-hidden="true"></i>
          Сегодня
        </button>
      }
    </nav>
  `,
  styles: [`
    :host { display: block; }
    .week-nav {
      display: flex;
      align-items: center;
      gap: var(--space-2, 8px);
      padding: var(--space-2, 8px) var(--space-3, 12px);
      background: var(--bg-secondary, #f5f5f5);
      border-radius: var(--radius-lg, 12px);
      border: 1px solid var(--border-subtle, #e0e0e0);
    }
    .week-nav__btn {
      min-width: 40px;
      min-height: 40px;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      border: none;
      background: transparent;
      border-radius: var(--radius-md, 8px);
      cursor: pointer;
      color: var(--text-primary);
      transition: background 150ms ease;
    }
    .week-nav__btn:hover { background: var(--bg-hover, #eee); }
    .week-nav__btn i { font-size: 1.2rem; }
    .week-nav__label {
      flex: 1;
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 2px;
    }
    .week-nav__eyebrow {
      font-size: 0.7rem;
      text-transform: uppercase;
      color: var(--text-muted);
      letter-spacing: 0.04em;
    }
    .week-nav__range {
      font-weight: 600;
      font-size: 0.95rem;
    }
    .week-nav__today {
      display: inline-flex;
      align-items: center;
      gap: 4px;
      padding: 6px 12px;
      border-radius: var(--radius-full, 999px);
      border: 1px solid var(--border-subtle, #d0d0d0);
      background: var(--bg-elevated, #fff);
      color: var(--text-primary);
      font-size: 0.85rem;
      cursor: pointer;
      transition: background 150ms ease;
    }
    .week-nav__today:hover { background: var(--bg-hover, #eee); }
  `],
})
export class WeekNavigatorComponent implements OnInit {
  @Input() initialDate: Date = new Date();
  @Output() weekChanged = new EventEmitter<{ monday: Date; weekOffset: number }>();

  private readonly initialMonday = signal<Date>(getMonday(new Date()));
  readonly weekOffset = signal(0);
  readonly currentMonday = computed(() =>
    addDays(this.initialMonday(), this.weekOffset() * 7),
  );
  readonly weekLabel = computed(() => formatWeekRange(this.currentMonday()));
  readonly isCurrentWeek = computed(() =>
    isSameWeek(this.currentMonday(), new Date()),
  );

  ngOnInit(): void {
    this.initialMonday.set(getMonday(this.initialDate));
    this.weekOffset.set(0);
    // Emit baseline so the parent can load data without recomputing the monday.
    this.emit();
  }

  previous(): void {
    this.weekOffset.update(v => v - 1);
    this.emit();
  }

  next(): void {
    this.weekOffset.update(v => v + 1);
    this.emit();
  }

  today(): void {
    this.initialMonday.set(getMonday(new Date()));
    this.weekOffset.set(0);
    this.emit();
  }

  private emit(): void {
    this.weekChanged.emit({
      monday: this.currentMonday(),
      weekOffset: this.weekOffset(),
    });
  }
}
