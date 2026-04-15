import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';

export interface BulkConfirmData {
  title: string;
  message: string;
}

@Component({
  selector: 'app-bulk-confirm-dialog',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatDialogModule, MatButtonModule],
  template: `
    <h2 mat-dialog-title>{{ data.title }}</h2>
    <mat-dialog-content>
      <p>{{ data.message }}</p>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button (click)="ref.close(false)">Отмена</button>
      <button mat-flat-button color="primary" (click)="ref.close(true)">Проставить</button>
    </mat-dialog-actions>
  `,
})
export class BulkConfirmDialogComponent {
  readonly data = inject<BulkConfirmData>(MAT_DIALOG_DATA);
  readonly ref = inject(MatDialogRef<BulkConfirmDialogComponent>);
}
