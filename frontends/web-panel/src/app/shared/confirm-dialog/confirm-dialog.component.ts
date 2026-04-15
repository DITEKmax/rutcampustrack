import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import {
  MatDialogModule,
  MatDialogRef,
  MAT_DIALOG_DATA,
} from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';

export interface ConfirmDialogData {
  title: string;
  message: string;
  confirmLabel?: string;
  cancelLabel?: string;
  destructive?: boolean;
}

/**
 * Shared confirmation dialog replacing native `window.confirm()` calls.
 *
 * Matches the existing MatDialog pattern used by DeleteGroupDialog /
 * ArchiveUserDialog / DeleteSemesterDialog so destructive actions across the
 * app share one visual language and focus-trap behaviour.
 *
 * Usage:
 * ```ts
 * const ref = this.dialog.open<ConfirmDialogComponent, ConfirmDialogData, boolean>(
 *   ConfirmDialogComponent,
 *   { data: { title: 'Удалить?', message: '…', destructive: true } },
 * );
 * ref.afterClosed().subscribe(ok => { if (ok) this.delete(); });
 * ```
 */
@Component({
  selector: 'app-confirm-dialog',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatDialogModule, MatButtonModule],
  template: `
    <h2 mat-dialog-title>{{ data.title }}</h2>
    <mat-dialog-content>
      <p style="margin: 0; color: var(--text-primary); font-size: 0.9375rem; line-height: 1.5;">
        {{ data.message }}
      </p>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>{{ data.cancelLabel ?? 'Отмена' }}</button>
      <button
        mat-flat-button
        [color]="data.destructive ? 'warn' : 'primary'"
        [mat-dialog-close]="true"
        cdkFocusInitial
      >
        {{ data.confirmLabel ?? 'Подтвердить' }}
      </button>
    </mat-dialog-actions>
  `,
})
export class ConfirmDialogComponent {
  readonly data = inject<ConfirmDialogData>(MAT_DIALOG_DATA);
  private readonly dialogRef = inject(MatDialogRef<ConfirmDialogComponent, boolean>);
}
