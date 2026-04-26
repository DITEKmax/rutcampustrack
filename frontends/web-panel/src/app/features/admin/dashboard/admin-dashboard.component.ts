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
  imports: [StatCardComponent, RouterLink],
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

  /**
   * M07 G9 (QC6/7, D1 решение 4): sparklines и chart «Посещаемость за 7
   * дней» показывали псевдо-данные (buildSpark + detrministic wobble),
   * которые юзер не мог отличить от реальных. Убрали до появления
   * time-series endpoint'а (candidate v0.1 → NEW-94 "real sparklines"
   * в `docs/archive/future-ideas.md`). Stat-cards остаются с числами без
   * графиков; вместо chart-card — info-badge "Графики появятся в
   * следующем релизе".
   */

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
