import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  inject,
  signal,
} from '@angular/core';
import { CommonModule, NgFor } from '@angular/common';
import { RouterModule } from '@angular/router';
import { trigger, transition, style, animate } from '@angular/animations';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { HeadmanApiService } from '../shared/headman-api.service';
import { SubjectDialogComponent } from './subject-dialog.component';
import { DeleteSubjectDialogComponent } from './delete-subject-dialog.component';

/**
 * Headman subjects page — `/headman/subjects`.
 *
 * Delivers HEAD-WEB-04: Headmen can view and manage all subjects.
 * NOTE: Subjects are institution-wide (no group filter) per research Key Finding B.
 *
 * Features:
 * - Subject list table (name + teacher columns)
 * - Create / Edit via MatDialog (SubjectDialogComponent)
 * - Delete via confirmation MatDialog (DeleteSubjectDialogComponent)
 * - 409 response on delete shows specific "cannot delete with attendance" message
 * - Empty state with inline CTA
 * - Skeleton loading rows
 */
@Component({
  selector: 'app-headman-subjects',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule,
    NgFor,
    RouterModule,
    MatTableModule,
    MatButtonModule,
    MatIconModule,
  ],
  animations: [
    trigger('routeFade', [
      transition(':enter', [
        style({ opacity: 0, transform: 'translateY(8px)' }),
        animate('200ms cubic-bezier(0.16, 1, 0.3, 1)', style({ opacity: 1, transform: 'translateY(0)' })),
      ]),
    ]),
  ],
  template: `
    <div class="page-stack" [@routeFade]>
      <!-- Page header -->
      <div class="page-header">
        <div>
          <span class="page-eyebrow">Старостат</span>
          <h1 class="page-title">Предметы группы</h1>
        </div>
        <div class="page-header__actions">
          <button class="btn-brand" (click)="openCreateDialog()">
            <i class="ph ph-plus"></i> Добавить предмет
          </button>
        </div>
      </div>

      <!-- Subject list card -->
      <div class="page-card page-card--flush">
        <div class="page-card__header">
          <h2>Предметы</h2>
        </div>

        @if (loading()) {
          <div class="skeleton-row" *ngFor="let i of [1,2,3,4]" aria-hidden="true"></div>
          <span aria-live="polite" class="sr-only">Загрузка...</span>
        }
        @else if (error()) {
          <div class="page-error"><i class="ph ph-warning-circle"></i>{{ error() }}</div>
        }
        @else if (subjects().length === 0) {
          <div class="page-empty">
            <i class="ph-duotone ph-books"></i>
            <h3>Нет предметов</h3>
            <p>Добавьте первый предмет, чтобы начать вести журнал посещаемости.</p>
            <button class="btn-brand" (click)="openCreateDialog()">Добавить предмет</button>
          </div>
        }
        @else {
          <table mat-table [dataSource]="subjects()">
            <ng-container matColumnDef="name">
              <th mat-header-cell *matHeaderCellDef>Предмет</th>
              <td mat-cell *matCellDef="let s">
                <span class="subject-name">{{ s.name }}</span>
              </td>
            </ng-container>
            <ng-container matColumnDef="teacher">
              <th mat-header-cell *matHeaderCellDef>Преподаватель</th>
              <td mat-cell *matCellDef="let s">
                @if (s.teacherName || s.teacher?.fullName) {
                  <span>{{ s.teacherName ?? s.teacher?.fullName }}</span>
                } @else {
                  <span class="no-teacher">Не назначен</span>
                }
              </td>
            </ng-container>
            <ng-container matColumnDef="actions">
              <th mat-header-cell *matHeaderCellDef></th>
              <td mat-cell *matCellDef="let s">
                <button class="icon-btn"
                        [attr.aria-label]="'Редактировать предмет ' + s.name"
                        (click)="openEditDialog(s)">
                  <i class="ph ph-pencil-simple"></i>
                </button>
                <button class="icon-btn icon-btn--danger"
                        [attr.aria-label]="'Удалить предмет ' + s.name"
                        (click)="openDeleteDialog(s)">
                  <i class="ph ph-trash"></i>
                </button>
              </td>
            </ng-container>
            <tr mat-header-row *matHeaderRowDef="['name','teacher','actions']"></tr>
            <tr mat-row *matRowDef="let row; columns: ['name','teacher','actions']"></tr>
          </table>
        }
      </div>
    </div>
  `,
  styles: [`
    .no-teacher {
      font-style: italic;
      color: var(--text-muted);
    }

    .skeleton-row {
      height: 52px;
      margin: 4px 0;
      border-radius: 4px;
      background: linear-gradient(90deg, var(--bg-secondary) 25%, var(--bg-elevated) 50%, var(--bg-secondary) 75%);
      background-size: 200% 100%;
      animation: dashboard-shimmer 1.5s infinite;
    }

    @keyframes dashboard-shimmer {
      0% { background-position: 200% 0; }
      100% { background-position: -200% 0; }
    }
  `],
})
export class HeadmanSubjectsComponent implements OnInit {
  private readonly headmanApi = inject(HeadmanApiService);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);

  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly subjects = signal<any[]>([]);

  ngOnInit(): void {
    this.loadSubjects();
  }

  loadSubjects(): void {
    this.loading.set(true);
    this.error.set(null);
    this.headmanApi.listSubjects().subscribe({
      next: (resp) => {
        this.subjects.set(Object.values(resp?._embedded ?? {})[0] as any[] ?? []);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Не удалось загрузить предметы. Попробуйте обновить страницу.');
        this.loading.set(false);
      },
    });
  }

  openCreateDialog(): void {
    const ref = this.dialog.open(SubjectDialogComponent, {
      width: '480px',
      maxWidth: '95vw',
      ariaLabel: 'Новый предмет',
      data: { mode: 'create' },
    });
    ref.afterClosed().subscribe(result => { if (result) this.loadSubjects(); });
  }

  openEditDialog(subject: any): void {
    const ref = this.dialog.open(SubjectDialogComponent, {
      width: '480px',
      maxWidth: '95vw',
      ariaLabel: 'Редактировать предмет',
      data: { mode: 'edit', subject },
    });
    ref.afterClosed().subscribe(result => { if (result) this.loadSubjects(); });
  }

  openDeleteDialog(subject: any): void {
    const ref = this.dialog.open(DeleteSubjectDialogComponent, {
      data: { subjectName: subject.name },
      ariaLabel: 'Подтверждение удаления',
    });
    ref.afterClosed().subscribe(confirmed => {
      if (!confirmed) return;
      this.headmanApi.deleteSubject(subject.id).subscribe({
        next: () => {
          this.snackBar.open('Предмет удалён.', undefined, { duration: 4000 });
          this.loadSubjects();
        },
        error: (err) => {
          if (err.status === 409) {
            this.snackBar.open('Нельзя удалить предмет с записями посещаемости.', undefined, { duration: 6000 });
          } else {
            this.snackBar.open('Не удалось удалить предмет.', undefined, { duration: 6000 });
          }
        },
      });
    });
  }
}
