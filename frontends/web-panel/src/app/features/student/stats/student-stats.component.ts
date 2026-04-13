import {
  ChangeDetectionStrategy, Component, OnInit, inject, signal,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { trigger, transition, style, animate } from '@angular/animations';
import { forkJoin } from 'rxjs';
import { AuthService } from '../../../core/auth/auth.service';
import { StudentApiService } from '../shared/student-api.service';
import type { StudentStatsResponse } from '../shared/student-schedule.types';
import { StudentSubjectChartComponent } from './student-subject-chart/student-subject-chart.component';
import { StudentOverallCardComponent } from './student-overall-card/student-overall-card.component';
import { formatLoadError } from '../shared/format-load-error';

@Component({
  selector: 'app-student-stats',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, MatProgressBarModule, StudentSubjectChartComponent, StudentOverallCardComponent],
  animations: [
    trigger('routeFade', [
      transition(':enter', [
        style({ opacity: 0, transform: 'translateY(8px)' }),
        animate('200ms var(--ease-out, ease-out)', style({ opacity: 1, transform: 'translateY(0)' })),
      ]),
    ]),
  ],
  templateUrl: './student-stats.component.html',
  styleUrl: './student-stats.component.css',
})
export class StudentStatsComponent implements OnInit {
  private readonly authService = inject(AuthService);
  private readonly apiService = inject(StudentApiService);

  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly stats = signal<StudentStatsResponse | null>(null);
  readonly threshold = signal(75);

  ngOnInit(): void {
    const groupId = this.authService.currentUser()?.groupId;
    this.loading.set(true);
    const threshold$ = groupId != null
      ? this.apiService.resolveGroupThreshold(groupId)
      : this.apiService.resolveGlobalThreshold();

    forkJoin([this.apiService.getStudentStats(), threshold$]).subscribe({
      next: ([statsResp, thresholdResp]) => {
        this.stats.set(statsResp);
        this.threshold.set(thresholdResp.percentage);
        this.loading.set(false);
      },
      error: (err) => {
        console.error('[student-stats] failed to load', err);
        this.error.set(formatLoadError(err, 'Не удалось загрузить статистику.'));
        this.loading.set(false);
      },
    });
  }
}
