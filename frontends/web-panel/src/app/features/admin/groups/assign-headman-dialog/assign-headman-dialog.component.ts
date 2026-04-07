import { Component, inject } from '@angular/core';
import { MatDialogModule, MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatSelectModule } from '@angular/material/select';
import { MatFormFieldModule } from '@angular/material/form-field';

import type { GroupResponse, UserResponse } from '../../shared/types';

@Component({
  selector: 'app-assign-headman-dialog',
  standalone: true,
  imports: [MatDialogModule, MatButtonModule, MatSelectModule, MatFormFieldModule],
  template: `
    <h2 mat-dialog-title>Назначить старосту</h2>
    <mat-dialog-content>
      <p class="mb-4">Выберите студента группы {{ data.group.name }}.</p>
      <mat-form-field class="w-full">
        <mat-label>Студент</mat-label>
        <mat-select [(value)]="selectedUserId">
          @for (s of data.students; track s.id) {
            <mat-option [value]="s.id">{{ s.displayName }} ({{ s.login }})</mat-option>
          }
        </mat-select>
      </mat-form-field>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Отмена</button>
      <button mat-flat-button color="primary" [disabled]="!selectedUserId" (click)="assign()">Назначить</button>
    </mat-dialog-actions>
  `,
})
export class AssignHeadmanDialogComponent {
  data = inject<{ group: GroupResponse; students: UserResponse[] }>(MAT_DIALOG_DATA);
  private dialogRef = inject(MatDialogRef<AssignHeadmanDialogComponent>);
  selectedUserId: number | null = null;

  assign(): void {
    this.dialogRef.close(this.selectedUserId);
  }
}
