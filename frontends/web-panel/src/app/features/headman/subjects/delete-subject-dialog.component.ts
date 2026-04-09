import { Component, inject } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';

export interface DeleteSubjectDialogData {
  subjectName: string;
}

@Component({
  selector: 'app-delete-subject-dialog',
  standalone: true,
  imports: [MatDialogModule, MatButtonModule],
  template: `
    <h2 mat-dialog-title>Удалить предмет?</h2>
    <mat-dialog-content>
      Предмет «{{ data.subjectName }}» будет удалён. Это действие нельзя отменить.
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-stroked-button [mat-dialog-close]="false">Отмена</button>
      <button mat-flat-button class="btn-danger" [mat-dialog-close]="true">Удалить</button>
    </mat-dialog-actions>
  `,
  styles: [`
    .btn-danger {
      background: var(--accent-danger);
      color: white;
    }
  `],
})
export class DeleteSubjectDialogComponent {
  readonly data = inject<DeleteSubjectDialogData>(MAT_DIALOG_DATA);
}
