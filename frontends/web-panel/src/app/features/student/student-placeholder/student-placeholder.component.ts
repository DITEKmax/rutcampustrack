import { Component } from '@angular/core';
import { MatCardModule } from '@angular/material/card';

@Component({
  selector: 'app-student-placeholder',
  standalone: true,
  imports: [MatCardModule],
  template: `
    <div class="placeholder-wrap">
      <mat-card class="placeholder-card">
        <p class="mat-body-1 placeholder-text">
          Кабинет студента появится в Фазе 51
        </p>
      </mat-card>
    </div>
  `,
  styles: [`
    .placeholder-wrap {
      display: flex;
      justify-content: center;
      padding: 48px 16px;
    }
    .placeholder-card {
      max-width: 480px;
      width: 100%;
      text-align: center;
      padding: 32px;
    }
    .placeholder-text {
      color: var(--mat-sys-on-surface-variant);
      margin: 0;
    }
  `],
})
export class StudentPlaceholderComponent {}
