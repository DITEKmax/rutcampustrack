import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatDialogModule, MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';

import type { GroupResponse } from '../../shared/types';

export interface DeleteGroupDialogData {
  group: GroupResponse;
  studentCount: number;
}

@Component({
  selector: 'app-delete-group-dialog',
  standalone: true,
  imports: [MatDialogModule, MatButtonModule, MatFormFieldModule, MatInputModule, FormsModule],
  template: `
    <h2 mat-dialog-title>Удалить группу?</h2>
    <mat-dialog-content>
      @if (data.studentCount > 0) {
        <p class="mb-4">
          В группе <strong>{{ data.group.name }}</strong> числится
          {{ data.studentCount }} {{ data.studentCount === 1 ? 'студент' : 'студент(ов)' }}.
          Удаление возможно только после переноса всех студентов в другую группу.
        </p>
      } @else {
        <p class="mb-4">
          Это действие необратимо. Введите название группы для подтверждения:
        </p>
        <mat-form-field class="w-full">
          <mat-label>Введите &laquo;{{ data.group.name }}&raquo;</mat-label>
          <input matInput [(ngModel)]="confirmInput" autocomplete="off">
        </mat-form-field>
      }
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Отмена</button>
      @if (data.studentCount === 0) {
        <button mat-flat-button color="warn"
          [disabled]="confirmInput.trim() !== data.group.name"
          (click)="confirm()">Удалить навсегда</button>
      }
    </mat-dialog-actions>
  `,
})
export class DeleteGroupDialogComponent {
  data = inject<DeleteGroupDialogData>(MAT_DIALOG_DATA);
  private dialogRef = inject(MatDialogRef<DeleteGroupDialogComponent>);
  confirmInput = '';

  confirm(): void {
    this.dialogRef.close(true);
  }
}
