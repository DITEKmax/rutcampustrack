import {
  ChangeDetectionStrategy, Component, OnInit, inject, signal,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { trigger, transition, style, animate } from '@angular/animations';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { StudentApiService } from '../shared/student-api.service';
import type { ExcuseTicket, ExcuseTicketStatus } from '../shared/student-schedule.types';
import { ExcuseFormDialogComponent } from './excuse-form-dialog/excuse-form-dialog.component';

@Component({
  selector: 'app-student-excuses',
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
  templateUrl: './student-excuses.component.html',
  styleUrl: './student-excuses.component.css',
})
export class StudentExcusesComponent implements OnInit {
  private readonly apiService = inject(StudentApiService);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);

  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly tickets = signal<ExcuseTicket[]>([]);

  readonly statusLabels: Record<ExcuseTicketStatus, string> = {
    pending: 'На рассмотрении',
    approved: 'Одобрено',
    rejected: 'Отклонено',
    cancelled: 'Отменено',
  };

  ngOnInit(): void {
    this.loadTickets();
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
