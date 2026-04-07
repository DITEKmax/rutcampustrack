import { Component, inject } from '@angular/core';
import { MatDialogModule, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';

import type { GroupResponse, UserResponse } from '../../shared/types';

@Component({
  selector: 'app-revoke-headman-dialog',
  standalone: true,
  imports: [MatDialogModule, MatButtonModule],
  template: `
    <h2 mat-dialog-title>Снять старосту?</h2>
    <mat-dialog-content>
      <p>{{ data.headman.displayName }} будет снят с должности старосты группы {{ data.group.name }}.</p>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Отмена</button>
      <button mat-flat-button color="warn" [mat-dialog-close]="true">Снять</button>
    </mat-dialog-actions>
  `,
})
export class RevokeHeadmanDialogComponent {
  data = inject<{ group: GroupResponse; headman: UserResponse }>(MAT_DIALOG_DATA);
}
