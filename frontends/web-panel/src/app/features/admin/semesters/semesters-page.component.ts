import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';

import { AdminApiService } from '../shared/admin-api.service';
import type { SemesterResponse, SemesterStatus } from '../shared/types';
import { deriveSemesterStatus } from '../shared/types';
import { SemesterDialogComponent, SemesterDialogData } from './semester-dialog/semester-dialog.component';
import { DeleteSemesterDialogComponent } from './delete-semester-dialog/delete-semester-dialog.component';

@Component({
  selector: 'app-semesters-page',
  standalone: true,
  imports: [
    CommonModule,
    MatTableModule,
    MatButtonModule,
    MatIconModule,
    MatProgressBarModule,
    MatTooltipModule,
  ],
  templateUrl: './semesters-page.component.html',
})
export class SemestersPageComponent implements OnInit {
  private readonly adminApi = inject(AdminApiService);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);

  semesters = signal<SemesterResponse[]>([]);
  loading = signal(false);
  error = signal<string | null>(null);

  displayedColumns = ['name', 'dateFrom', 'dateTo', 'status', 'actions'];

  ngOnInit(): void {
    this.reload();
  }

  reload(): void {
    this.loading.set(true);
    this.error.set(null);

    this.adminApi.listSemesters().subscribe({
      next: semesters => {
        this.semesters.set(semesters);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Не удалось загрузить семестры.');
        this.loading.set(false);
      },
    });
  }

  getStatus(sem: SemesterResponse): SemesterStatus {
    return deriveSemesterStatus(sem);
  }

  getStatusLabel(sem: SemesterResponse): string {
    const status = this.getStatus(sem);
    switch (status) {
      case 'active': return 'Активный';
      case 'planned': return 'Запланирован';
      case 'finished': return 'Завершён';
    }
  }

  formatDate(iso: string): string {
    if (!iso) return '';
    const parts = iso.slice(0, 10).split('-');
    if (parts.length !== 3) return iso;
    return `${parts[2]}.${parts[1]}.${parts[0]}`;
  }

  openCreateDialog(): void {
    const ref = this.dialog.open(SemesterDialogComponent, {
      maxWidth: '480px',
      width: '100%',
      data: { mode: 'create' } as SemesterDialogData,
    });
    ref.afterClosed().subscribe(result => {
      if (result) {
        this.snackBar.open('Семестр создан.', undefined, { duration: 4000 });
        this.reload();
      }
    });
  }

  openEditDialog(sem: SemesterResponse): void {
    const ref = this.dialog.open(SemesterDialogComponent, {
      maxWidth: '480px',
      width: '100%',
      data: { mode: 'edit', semester: sem } as SemesterDialogData,
    });
    ref.afterClosed().subscribe(result => {
      if (result) {
        this.snackBar.open('Семестр обновлён.', undefined, { duration: 4000 });
        this.reload();
      }
    });
  }

  deleteSemester(sem: SemesterResponse): void {
    if (sem.active) return;

    const ref = this.dialog.open(DeleteSemesterDialogComponent, {
      maxWidth: '480px',
      width: '100%',
      data: { semester: sem },
    });
    ref.afterClosed().subscribe(confirmation => {
      if (confirmation) {
        this.adminApi.deleteSemester(sem.id, confirmation).subscribe({
          next: () => {
            this.snackBar.open('Семестр удалён.', undefined, { duration: 4000 });
            this.reload();
          },
          error: () => {
            this.snackBar.open('Не удалось удалить семестр.', undefined, { duration: 6000 });
          },
        });
      }
    });
  }
}
