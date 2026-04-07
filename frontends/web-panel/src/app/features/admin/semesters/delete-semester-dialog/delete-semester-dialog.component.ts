import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatDialogModule, MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';

import type { SemesterResponse } from '../../shared/types';

@Component({
  selector: 'app-delete-semester-dialog',
  standalone: true,
  imports: [MatDialogModule, MatButtonModule, MatFormFieldModule, MatInputModule, FormsModule],
  template: `
    <h2 mat-dialog-title>Удалить семестр?</h2>
    <mat-dialog-content>
      <p class="mb-4">Это действие необратимо. Введите название семестра для подтверждения:</p>
      <mat-form-field class="w-full">
        <mat-label>Введите &laquo;{{ data.semester.name }}&raquo;</mat-label>
        <input matInput [(ngModel)]="confirmInput" autocomplete="off">
      </mat-form-field>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Отмена</button>
      <button mat-flat-button color="warn"
        [disabled]="confirmInput.trim() !== data.semester.name"
        (click)="confirm()">Удалить навсегда</button>
    </mat-dialog-actions>
  `,
})
export class DeleteSemesterDialogComponent {
  data = inject<{ semester: SemesterResponse }>(MAT_DIALOG_DATA);
  private dialogRef = inject(MatDialogRef<DeleteSemesterDialogComponent>);
  confirmInput = '';

  confirm(): void {
    this.dialogRef.close(this.confirmInput.trim());
  }
}
