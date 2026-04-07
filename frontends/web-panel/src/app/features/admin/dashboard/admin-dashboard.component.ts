import { Component, inject, OnInit, signal } from '@angular/core';
import { AdminApiService } from '../shared/admin-api.service';
import { StatCardComponent } from './stat-card/stat-card.component';
import type { DashboardStatsResponse } from '../shared/types';

@Component({
  selector: 'app-admin-dashboard',
  standalone: true,
  imports: [StatCardComponent],
  templateUrl: './admin-dashboard.component.html',
})
export class AdminDashboardComponent implements OnInit {
  private readonly adminApi = inject(AdminApiService);

  stats = signal<DashboardStatsResponse | null>(null);
  loading = signal(false);
  error = signal<string | null>(null);

  ngOnInit(): void {
    this.loading.set(true);
    this.adminApi.getDashboardStats().subscribe({
      next: data => {
        this.stats.set(data);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Не удалось загрузить сводку. Попробуйте позже.');
        this.loading.set(false);
      },
    });
  }
}
