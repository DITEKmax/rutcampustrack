import {
  ChangeDetectionStrategy, Component, OnInit, inject, signal, computed,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { trigger, transition, style, animate } from '@angular/animations';
import { StudentApiService } from '../shared/student-api.service';
import type { AttendanceRecord } from '../shared/student-schedule.types';

interface DayGroup {
  date: string;
  label: string;
  records: AttendanceRecord[];
}

const DAY_NAMES = ['вс', 'пн', 'вт', 'ср', 'чт', 'пт', 'сб'] as const;
const MONTH_NAMES = [
  'янв', 'фев', 'мар', 'апр', 'май', 'июн',
  'июл', 'авг', 'сен', 'окт', 'ноя', 'дек',
] as const;

function formatDayLabel(isoDate: string): string {
  const d = new Date(isoDate + 'T00:00:00');
  if (Number.isNaN(d.getTime())) return isoDate;
  const dow = DAY_NAMES[d.getDay()];
  const month = MONTH_NAMES[d.getMonth()];
  return `${dow}, ${d.getDate()} ${month}`;
}

@Component({
  selector: 'app-student-late-checkin',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule],
  animations: [
    trigger('routeFade', [
      transition(':enter', [
        style({ opacity: 0, transform: 'translateY(8px)' }),
        animate('200ms cubic-bezier(0.16, 1, 0.3, 1)', style({ opacity: 1, transform: 'translateY(0)' })),
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

  readonly groupedByDay = computed<DayGroup[]>(() => {
    const byDate = new Map<string, AttendanceRecord[]>();
    for (const r of this.absentRecords()) {
      const bucket = byDate.get(r.lessonDate);
      if (bucket) bucket.push(r);
      else byDate.set(r.lessonDate, [r]);
    }
    return Array.from(byDate.entries())
      .sort(([a], [b]) => a.localeCompare(b))
      .map(([date, records]) => ({
        date,
        label: formatDayLabel(date),
        records: records.slice().sort((a, b) => a.lessonNumber - b.lessonNumber),
      }));
  });

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
