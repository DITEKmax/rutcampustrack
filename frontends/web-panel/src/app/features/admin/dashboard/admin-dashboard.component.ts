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
import type { DashboardStatsResponse } from '../shared/types';

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

    // Refresh the clock every minute (cleaned up on destroy).
    const tick = setInterval(() => this._now.set(new Date()), 60_000);
    this.destroyRef.onDestroy(() => clearInterval(tick));
  }
}
