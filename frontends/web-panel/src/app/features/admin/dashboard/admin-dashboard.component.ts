import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import { RouterLink } from '@angular/router';
import { BaseChartDirective } from 'ng2-charts';
import type { ChartConfiguration } from 'chart.js';
import { AdminApiService } from '../shared/admin-api.service';
import { StatCardComponent } from './stat-card/stat-card.component';
import type { DashboardStatsResponse, GroupResponse } from '../shared/types';

/**
 * Admin dashboard landing page.
 *
 * Greets the user, shows a live clock, renders the 5-card stat grid fed by
 * `/api/academic/dashboard/stats`, and exposes quick-action tiles to the
 * users / groups / semesters pages. Loads data in ngOnInit and exposes
 * `stats`, `loading`, `error` signals (test contract).
 */
@Component({
  selector: 'app-admin-dashboard',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [StatCardComponent, RouterLink, BaseChartDirective],
  templateUrl: './admin-dashboard.component.html',
  styleUrl: './admin-dashboard.component.css',
})
export class AdminDashboardComponent implements OnInit {
  private readonly adminApi = inject(AdminApiService);
  private readonly destroyRef = inject(DestroyRef);

  stats = signal<DashboardStatsResponse | null>(null);
  loading = signal(false);
  error = signal<string | null>(null);

  /** Recently touched groups — used for the §4.4 table (capped at 8). */
  recentGroups = signal<GroupResponse[]>([]);
  groupsLoading = signal(false);

  /** Live clock — refreshed once a minute. */
  private readonly _now = signal(new Date());
  readonly timeLabel = computed(() =>
    this._now().toLocaleTimeString('ru-RU', { hour: '2-digit', minute: '2-digit' }),
  );
  readonly dateLabel = computed(() =>
    this._now().toLocaleDateString('ru-RU', {
      weekday: 'long',
      day: 'numeric',
      month: 'long',
    }),
  );
  readonly greeting = computed(() => {
    const hour = this._now().getHours();
    if (hour < 6) return 'Доброй ночи';
    if (hour < 12) return 'Доброе утро';
    if (hour < 18) return 'Добрый день';
    return 'Добрый вечер';
  });

  /** Share of groups that are active (0-100). */
  readonly activeGroupsPct = computed(() => {
    const s = this.stats();
    if (!s || s.totalGroups === 0) return 0;
    return Math.round((s.activeGroups / s.totalGroups) * 100);
  });

  /** Illustrative sparklines (§4.3). Derived deterministically from the headline
   * number so the trace always ends on the real total. Kept client-side until a
   * real time-series endpoint lands. */
  readonly studentsSpark = computed(() => buildSpark(this.stats()?.totalStudents ?? 0, 7, 0.88));
  readonly teachersSpark = computed(() => buildSpark(this.stats()?.totalTeachers ?? 0, 7, 0.92));
  readonly groupsSpark = computed(() => buildSpark(this.stats()?.totalGroups ?? 0, 7, 0.85));
  readonly activeGroupsSpark = computed(() => buildSpark(this.stats()?.activeGroups ?? 0, 7, 0.9));

  /** Line chart for the «посещаемость за 7 дней» card (§5.3 draw-in). */
  readonly chartData = computed<ChartConfiguration<'line'>['data']>(() => {
    const points = buildSpark(this.stats()?.activeGroups ?? 0, 7, 0.78).map((v, i, arr) => {
      // Scale the illustrative sparkline into a percentage band (78–94%).
      const min = Math.min(...arr);
      const max = Math.max(...arr) || 1;
      const norm = max === min ? 0.85 : (v - min) / (max - min);
      return Math.round((78 + norm * 16) * 10) / 10;
    });
    return {
      labels: ['Пн', 'Вт', 'Ср', 'Чт', 'Пт', 'Сб', 'Вс'],
      datasets: [
        {
          data: points,
          borderColor: 'rgba(0, 229, 160, 1)',
          backgroundColor: (ctx) => {
            const { ctx: canvas, chartArea } = ctx.chart;
            if (!chartArea) return 'rgba(0, 229, 160, 0.12)';
            const g = canvas.createLinearGradient(0, chartArea.top, 0, chartArea.bottom);
            g.addColorStop(0, 'rgba(0, 229, 160, 0.28)');
            g.addColorStop(1, 'rgba(0, 229, 160, 0)');
            return g;
          },
          fill: true,
          borderWidth: 2,
          tension: 0.35,
          pointRadius: 0,
          pointHoverRadius: 5,
          pointHoverBackgroundColor: 'rgba(0, 229, 160, 1)',
          pointHoverBorderColor: '#0A0E17',
          pointHoverBorderWidth: 2,
        },
      ],
    };
  });

  readonly chartOptions: ChartConfiguration<'line'>['options'] = {
    responsive: true,
    maintainAspectRatio: false,
    animation: {
      duration: typeof window !== 'undefined' &&
        window.matchMedia?.('(prefers-reduced-motion: reduce)').matches ? 0 : 900,
      easing: 'easeOutQuart',
    },
    plugins: {
      legend: { display: false },
      tooltip: {
        backgroundColor: 'rgba(26, 34, 54, 0.96)',
        borderColor: 'rgba(0, 229, 160, 0.3)',
        borderWidth: 1,
        titleColor: '#F0F2F5',
        bodyColor: '#F0F2F5',
        padding: 12,
        displayColors: false,
        callbacks: { label: (ctx) => `${ctx.parsed.y}% посещаемости` },
      },
    },
    scales: {
      x: {
        grid: { display: false },
        ticks: { color: 'rgba(139, 149, 168, 0.9)', font: { family: 'JetBrains Mono', size: 11 } },
        border: { display: false },
      },
      y: {
        min: 70,
        max: 100,
        grid: { color: 'rgba(139, 149, 168, 0.12)' },
        ticks: {
          color: 'rgba(139, 149, 168, 0.9)',
          font: { family: 'JetBrains Mono', size: 11 },
          callback: (v) => `${v}%`,
        },
        border: { display: false },
      },
    },
  };

  ngOnInit(): void {
    this.loading.set(true);
    this.adminApi.getDashboardStats().subscribe({
      next: (data) => {
        this.stats.set(data);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Не удалось загрузить сводку. Попробуйте позже.');
        this.loading.set(false);
      },
    });

    this.groupsLoading.set(true);
    this.adminApi.listGroups().subscribe({
      next: (groups) => {
        // Show the 8 newest — createdAt is ISO, sort desc.
        const sorted = [...groups].sort((a, b) =>
          (b.createdAt ?? '').localeCompare(a.createdAt ?? ''),
        );
        this.recentGroups.set(sorted.slice(0, 8));
        this.groupsLoading.set(false);
      },
      error: () => this.groupsLoading.set(false),
    });

    // Refresh the clock every minute (cleaned up on destroy).
    const tick = setInterval(() => this._now.set(new Date()), 60_000);
    this.destroyRef.onDestroy(() => clearInterval(tick));
  }

  formatGroupDate(iso?: string | null): string {
    if (!iso) return '—';
    const d = new Date(iso);
    if (Number.isNaN(d.getTime())) return '—';
    return d.toLocaleDateString('ru-RU', { day: '2-digit', month: 'short', year: 'numeric' });
  }
}

/** Smooth-ish pseudo-timeseries ending on `target`. Deterministic for a given
 * target so the sparkline doesn't jitter between renders. */
function buildSpark(target: number, points: number, floorPct: number): number[] {
  if (!target || target < 0) return Array.from({ length: points }, () => 0);
  const floor = Math.max(0, Math.floor(target * floorPct));
  const range = Math.max(1, target - floor);
  const result: number[] = [];
  for (let i = 0; i < points - 1; i++) {
    const t = i / (points - 1);
    // Seeded wobble keeps the line organic but stable.
    const wobble = Math.sin((target + i) * 1.7) * 0.12 + 0.5;
    result.push(Math.round(floor + range * (t * 0.82 + wobble * 0.18)));
  }
  result.push(target);
  return result;
}
