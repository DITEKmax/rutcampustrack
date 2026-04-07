import { Component } from '@angular/core';

@Component({
  selector: 'app-admin-dashboard',
  standalone: true,
  template: `
    <div>
      <h1 class="text-[20px] font-semibold leading-[1.2] mb-4 text-[var(--mat-sys-on-surface)]">
        Панель администратора
      </h1>
      <p class="text-sm text-[var(--mat-sys-on-surface)]/60">
        Выберите раздел в боковом меню.
      </p>
    </div>
  `,
})
export class AdminDashboardComponent {}
