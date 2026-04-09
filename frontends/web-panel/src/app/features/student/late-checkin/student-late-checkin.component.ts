import {
  ChangeDetectionStrategy, Component, OnInit, inject, signal, computed,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { trigger, transition, style, animate } from '@angular/animations';
import { StudentApiService } from '../shared/student-api.service';
import type { AttendanceRecord } from '../shared/student-schedule.types';

@Component({
  selector: 'app-student-late-checkin',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule],
  animations: [
    trigger('routeFade', [
      transition(':enter', [
        style({ opacity: 0, transform: 'translateY(8px)' }),
        animate('200ms var(--ease-out, ease-out)', style({ opacity: 1, transform: 'translateY(0)' })),
      ]),
    ]),
  ],
  templateUrl: './student-late-checkin.component.html',
  styleUrl: './student-late-checkin.component.css',
})
export class StudentLateCheckinComponent implements OnInit {
  private readonly apiService = inject(StudentApiService);

  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly allRecords = signal<AttendanceRecord[]>([]);

  /** IDs of rows where request has been sent successfully (or graceful 404) */
  readonly sentRows = signal<Set<number>>(new Set());
  /** IDs of rows currently being submitted */
  readonly pendingRows = signal<Set<number>>(new Set());
  /** Per-row error messages */
  readonly rowErrors = signal<Record<number, string>>({});

  readonly absentRecords = computed(() =>
    this.allRecords().filter(r => r.status === 'absent'),
  );

  ngOnInit(): void {
    this.loading.set(true);
    this.apiService.getStudentRecords().subscribe({
      next: (records) => {
        this.allRecords.set(records);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Не удалось загрузить список занятий. Попробуйте обновить страницу.');
        this.loading.set(false);
      },
    });
  }

  requestLateCheckin(lessonId: number): void {
    if (this.sentRows().has(lessonId) || this.pendingRows().has(lessonId)) return;

    // Clear previous row error
    this.rowErrors.update(e => {
      const next = { ...e };
      delete next[lessonId];
      return next;
    });

    // Set pending
    this.pendingRows.update(set => {
      const next = new Set(set);
      next.add(lessonId);
      return next;
    });

    this.apiService.requestLateCheckin(lessonId).subscribe({
      next: () => {
        // Success or graceful 404 — show success state in-place
        this.pendingRows.update(set => {
          const next = new Set(set);
          next.delete(lessonId);
          return next;
        });
        this.sentRows.update(set => {
          const next = new Set(set);
          next.add(lessonId);
          return next;
        });
      },
      error: () => {
        this.pendingRows.update(set => {
          const next = new Set(set);
          next.delete(lessonId);
          return next;
        });
        this.rowErrors.update(e => ({
          ...e,
          [lessonId]: 'Ошибка. Попробуйте ещё раз.',
        }));
      },
    });
  }

  isSent(lessonId: number): boolean {
    return this.sentRows().has(lessonId);
  }

  isPending(lessonId: number): boolean {
    return this.pendingRows().has(lessonId);
  }

  getRowError(lessonId: number): string | null {
    return this.rowErrors()[lessonId] ?? null;
  }
}
