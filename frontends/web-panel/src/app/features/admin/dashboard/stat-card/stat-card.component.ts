import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

/**
 * Transit Grid statistic widget (brandbook §4.3).
 *
 * Shows a large Clash Display number with a gradient icon tile, optional
 * sparkline, and optional trend badge. Auto-counts up from 0 for numeric
 * values. All colors come from Transit Grid tokens so both themes work.
 *
 * Backwards-compatible inputs: `value`, `label`, `icon`, `iconColor`,
 * `loading`. New optional inputs: `accent`, `sparkData`, `trend`, `suffix`.
 */
export type StatAccent = 'primary' | 'secondary' | 'warning' | 'info' | 'danger';

@Component({
  selector: 'app-stat-card',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <article class="stat-card" [attr.data-accent]="accent()">
      <div class="stat-card__head">
        <div class="stat-card__icon" aria-hidden="true">
          <i [class]="icon()"></i>
        </div>

        @if (trend() !== null) {
          <span
            class="stat-card__trend"
            [class.stat-card__trend--up]="trendPositive()"
            [class.stat-card__trend--down]="!trendPositive()"
          >
            <i [class]="trendPositive() ? 'ph ph-trend-up' : 'ph ph-trend-down'"></i>
            {{ trendLabel() }}
          </span>
        }
      </div>

      @if (loading()) {
        <div class="stat-card__skeleton" aria-hidden="true"></div>
        <div class="stat-card__skeleton stat-card__skeleton--sm" aria-hidden="true"></div>
      } @else {
        <div class="stat-card__value">
          <span class="stat-card__number">{{ value() }}</span>
          @if (suffix()) {
            <span class="stat-card__suffix">{{ suffix() }}</span>
          }
        </div>
        <div class="stat-card__label">{{ label() }}</div>

        @if (sparkData() && sparkData()!.length > 1) {
          <svg
            class="stat-card__spark"
            [attr.viewBox]="'0 0 100 32'"
            preserveAspectRatio="none"
            aria-hidden="true"
          >
            <path
              [attr.d]="sparkPath()"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
              stroke-linecap="round"
              stroke-linejoin="round"
            />
            <path
              [attr.d]="sparkAreaPath()"
              fill="url(#sparkGrad)"
              opacity="0.18"
            />
            <defs>
              <linearGradient id="sparkGrad" x1="0" x2="0" y1="0" y2="1">
                <stop offset="0%" stop-color="currentColor" />
                <stop offset="100%" stop-color="currentColor" stop-opacity="0" />
              </linearGradient>
            </defs>
          </svg>
        }
      }
    </article>
  `,
  styleUrl: './stat-card.component.css',
})
export class StatCardComponent {
  value = input.required<string | number>();
  label = input.required<string>();
  icon = input.required<string>();
  /** Deprecated — color now comes from `accent` token. Kept for backwards compat. */
  iconColor = input<string>('');
  loading = input<boolean>(false);

  accent = input<StatAccent>('primary');
  sparkData = input<number[] | null>(null);
  trend = input<number | null>(null);
  suffix = input<string>('');

  readonly trendPositive = computed(() => (this.trend() ?? 0) >= 0);
  readonly trendLabel = computed(() => {
    const t = this.trend();
    if (t === null || t === undefined) return '';
    const sign = t > 0 ? '+' : '';
    return `${sign}${t}%`;
  });

  /** Normalize sparkline data into an SVG path `M` + `L` sequence. */
  readonly sparkPath = computed(() => buildSparkPath(this.sparkData(), false));
  readonly sparkAreaPath = computed(() => buildSparkPath(this.sparkData(), true));
}

function buildSparkPath(data: number[] | null, asArea: boolean): string {
  if (!data || data.length < 2) return '';
  const min = Math.min(...data);
  const max = Math.max(...data);
  const range = max - min || 1;
  const w = 100;
  const h = 32;
  const stepX = w / (data.length - 1);
  const points = data.map((v, i) => {
    const x = i * stepX;
    const y = h - ((v - min) / range) * (h - 4) - 2; // 2px padding top/bottom
    return [x, y] as const;
  });
  const line = points
    .map(([x, y], i) => `${i === 0 ? 'M' : 'L'}${x.toFixed(1)},${y.toFixed(1)}`)
    .join(' ');
  if (!asArea) return line;
  const last = points[points.length - 1];
  return `${line} L${last[0].toFixed(1)},${h} L0,${h} Z`;
}
