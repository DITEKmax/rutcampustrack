import { Component, input } from '@angular/core';
import { MatCardModule } from '@angular/material/card';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

@Component({
  selector: 'app-stat-card',
  standalone: true,
  imports: [MatCardModule, MatProgressSpinnerModule],
  template: `
    <mat-card class="stat-card p-6">
      @if (loading()) {
        <mat-spinner diameter="32"></mat-spinner>
      } @else {
        <i [class]="icon()" [style.font-size.px]="32" [style.color]="iconColor()"></i>
        <span class="text-[28px] font-normal leading-[1.2] mt-2 block text-[var(--mat-sys-on-surface)]">{{ value() }}</span>
        <span class="text-[12px] font-semibold leading-[1.4] text-[var(--mat-sys-on-surface)]/60">{{ label() }}</span>
      }
    </mat-card>
  `,
})
export class StatCardComponent {
  value = input.required<string | number>();
  label = input.required<string>();
  icon = input.required<string>();
  iconColor = input<string>('#1A56DB');
  loading = input<boolean>(false);
}
