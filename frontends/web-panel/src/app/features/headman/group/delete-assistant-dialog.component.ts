import { Component, inject } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';

export interface DeleteAssistantDialogData {
  fullName: string;
}

@Component({
  selector: 'app-delete-assistant-dialog',
  standalone: true,
  imports: [MatDialogModule, MatButtonModule],
  template: `
    <h2 mat-dialog-title>Удалить помощника?</h2>
    <mat-dialog-content>
      Студент {{ data.fullName }} потеряет все права помощника старосты.
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
export class DeleteAssistantDialogComponent {
  readonly data = inject<DeleteAssistantDialogData>(MAT_DIALOG_DATA);
}
