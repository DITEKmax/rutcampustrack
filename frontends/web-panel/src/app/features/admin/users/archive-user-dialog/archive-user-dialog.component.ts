import { Component, inject } from '@angular/core';
import { MatDialogModule, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { fullName, type UserResponse } from '../../shared/types';

@Component({
  selector: 'app-archive-user-dialog',
  standalone: true,
  imports: [MatDialogModule, MatButtonModule],
  template: `
    <h2 mat-dialog-title>Архивировать пользователя?</h2>
    <mat-dialog-content>
      <p>Пользователь {{ fullName(data.user) }} будет деактивирован. Войти в систему он не сможет.</p>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Отмена</button>
      <button mat-flat-button color="warn" [mat-dialog-close]="true">Архивировать</button>
    </mat-dialog-actions>
  `,
})
export class ArchiveUserDialogComponent {
  data = inject<{ user: UserResponse }>(MAT_DIALOG_DATA);
  readonly fullName = fullName;
}
