import {
  ChangeDetectionStrategy, Component, DestroyRef, OnInit, computed, inject, signal,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { trigger, transition, style, animate } from '@angular/animations';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { StudentApiService } from '../shared/student-api.service';
import { SubjectCacheService } from '../shared/subject-cache.service';
import type { ExcuseTicket, ExcuseTicketStatus, ExcuseType, LessonResponse } from '../shared/student-schedule.types';
import { EXCUSE_TYPE_LABELS } from '../shared/student-schedule.types';
import { ExcuseFormDialogComponent } from './excuse-form-dialog/excuse-form-dialog.component';
import { AuthService } from '../../../core/auth/auth.service';
import { NotificationCenterService } from '../../../core/notifications/notification-center.service';
import { forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';

type ExcuseTab = 'active' | 'archive';

/** Status buckets per tab — keep in sync with the tab toggle in the template. */
const ACTIVE_STATUSES: ExcuseTicketStatus[] = ['draft', 'submitted'];
const ARCHIVE_STATUSES: ExcuseTicketStatus[] = ['approved', 'rejected'];

@Component({
  selector: 'app-student-excuses',
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
  templateUrl: './student-excuses.component.html',
  styleUrl: './student-excuses.component.css',
})
export class StudentExcusesComponent implements OnInit {
  private readonly apiService = inject(StudentApiService);
  private readonly subjectCache = inject(SubjectCacheService);
  private readonly auth = inject(AuthService);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);
  private readonly center = inject(NotificationCenterService);
  private readonly destroyRef = inject(DestroyRef);

  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly tickets = signal<ExcuseTicket[]>([]);
  readonly currentTab = signal<ExcuseTab>('active');
  readonly expandedTicketId = signal<string | null>(null);

  /** lesson cache — keyed by lessonId, lazy-loaded once per ticket expand. */
  readonly lessonCache = signal<Record<number, LessonResponse>>({});
  readonly subjectNameCache = signal<Record<number, string>>({});

  /** Partition tickets by lifecycle bucket so each tab renders instantly. */
  readonly activeTickets = computed(() =>
    this.tickets().filter(t => ACTIVE_STATUSES.includes(t.status)),
  );
  readonly archiveTickets = computed(() =>
    this.tickets().filter(t => ARCHIVE_STATUSES.includes(t.status)),
  );

  readonly visibleTickets = computed(() =>
    this.currentTab() === 'active' ? this.activeTickets() : this.archiveTickets(),
  );

  readonly statusLabels: Record<ExcuseTicketStatus, string> = {
    draft: 'Черновик',
    submitted: 'На рассмотрении',
    approved: 'Одобрено',
    rejected: 'Отклонено',
  };

  readonly excuseTypeLabels = EXCUSE_TYPE_LABELS;

  labelForExcuseType(type: ExcuseType): string {
    return this.excuseTypeLabels[type] ?? type;
  }

  ngOnInit(): void {
    this.loadTickets();

    // Когда староста принял решение (через TG или веб), перезагружаем список
    // чтобы студент сразу увидел вердикт без F5. Фильтрация по user_id
    // выполняется в NotificationCenterService.
    this.center.onEvent$
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(envelope => {
        if (envelope.type === 'excuse.decided') {
          this.loadTickets();
        }
      });
  }

  switchTab(tab: ExcuseTab): void {
    this.currentTab.set(tab);
    this.expandedTicketId.set(null);
  }

  toggleTicket(ticket: ExcuseTicket): void {
    const current = this.expandedTicketId();
    if (current === ticket.id) {
      this.expandedTicketId.set(null);
      return;
    }
    this.expandedTicketId.set(ticket.id);
    this.ensureLessonsLoaded(ticket);
  }

  isExpanded(ticketId: string): boolean {
    return this.expandedTicketId() === ticketId;
  }

  /**
   * Fetch schedule info for each lesson in the ticket. We pull only the missing
   * lessons so a user toggling several tickets doesn't refetch the same lesson
   * twice. Lessons are pulled via the existing week endpoint, filtered client-side
   * to the requested IDs.
   */
  private ensureLessonsLoaded(ticket: ExcuseTicket): void {
    const missing = ticket.lessonIds.filter(id => !this.lessonCache()[id]);
    if (missing.length === 0) {
      this.ensureSubjectsLoaded(ticket);
      return;
    }
    const groupId = this.auth.currentUser()?.groupId ?? ticket.groupId;
    if (!groupId) {
      this.ensureSubjectsLoaded(ticket);
      return;
    }
    // Pull a 90-day window around today — covers the overwhelming majority of
    // submitted excuses without needing a by-id lesson endpoint.
    const today = new Date();
    const from = new Date(today.getTime() - 60 * 24 * 60 * 60 * 1000);
    const to = new Date(today.getTime() + 30 * 24 * 60 * 60 * 1000);
    const fmt = (d: Date): string => {
      const y = d.getFullYear();
      const m = String(d.getMonth() + 1).padStart(2, '0');
      const day = String(d.getDate()).padStart(2, '0');
      return `${y}-${m}-${day}`;
    };
    this.apiService.getWeekLessons(groupId, fmt(from), fmt(to)).pipe(
      catchError(() => of<LessonResponse[]>([])),
    ).subscribe(lessons => {
      const updates: Record<number, LessonResponse> = {};
      for (const l of lessons) {
        if (ticket.lessonIds.includes(l.id)) updates[l.id] = l;
      }
      this.lessonCache.update(prev => ({ ...prev, ...updates }));
      this.ensureSubjectsLoaded(ticket);
    });
  }

  private ensureSubjectsLoaded(ticket: ExcuseTicket): void {
    const cached = this.subjectNameCache();
    const subjectIds = new Set<number>();
    for (const id of ticket.lessonIds) {
      const lesson = this.lessonCache()[id];
      if (lesson && !cached[lesson.subjectId]) subjectIds.add(lesson.subjectId);
    }
    if (subjectIds.size === 0) return;
    const calls = Array.from(subjectIds).map(sid =>
      this.subjectCache.getName(sid).pipe(
        catchError(() => of<string>('Предмет')),
      ),
    );
    if (calls.length === 0) return;
    forkJoin(calls).subscribe(names => {
      const ids = Array.from(subjectIds);
      const updates: Record<number, string> = {};
      ids.forEach((sid, i) => { updates[sid] = names[i]; });
      this.subjectNameCache.update(prev => ({ ...prev, ...updates }));
    });
  }

  lessonLabel(lessonId: number): string {
    const lesson = this.lessonCache()[lessonId];
    if (!lesson) return `Пара №${lessonId}`;
    const subject = this.subjectNameCache()[lesson.subjectId] ?? 'Предмет';
    const date = lesson.date ?? '';
    const num = lesson.lessonNumber ? `№${lesson.lessonNumber}` : '';
    return `${date} ${num} ${subject}`.trim();
  }

  loadTickets(): void {
    this.loading.set(true);
    this.error.set(null);
    this.apiService
      .getExcuseTickets()
      .subscribe({
        next: (tickets) => {
          this.tickets.set(tickets);
          this.loading.set(false);
        },
        error: () => {
          // Graceful: GET endpoint also absent — show empty state, not error
          this.tickets.set([]);
          this.loading.set(false);
        },
      });
  }

  openExcuseForm(): void {
    // Load records for lesson selection in the dialog
    this.apiService.getStudentRecords().subscribe({
      next: (records) => {
        const ref = this.dialog.open(ExcuseFormDialogComponent, {
          width: '560px',
          maxWidth: '100vw',
          maxHeight: '80vh',
          ariaLabel: 'Новый тикет о пропуске',
          data: { lessons: records },
        });
        ref.afterClosed().subscribe((submitted: boolean | undefined) => {
          if (submitted) this.loadTickets();
        });
      },
      error: () => {
        // If records fail to load, open dialog with empty lesson list
        const ref = this.dialog.open(ExcuseFormDialogComponent, {
          width: '560px',
          maxWidth: '100vw',
          maxHeight: '80vh',
          ariaLabel: 'Новый тикет о пропуске',
          data: { lessons: [] },
        });
        ref.afterClosed().subscribe((submitted: boolean | undefined) => {
          if (submitted) this.loadTickets();
        });
      },
    });
  }
}
