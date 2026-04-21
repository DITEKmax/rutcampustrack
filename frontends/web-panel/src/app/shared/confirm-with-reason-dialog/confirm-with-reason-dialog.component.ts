import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import {
  MatDialogModule,
  MatDialogRef,
  MAT_DIALOG_DATA,
} from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';

export interface ConfirmWithReasonDialogData {
  title: string;
  /** Необязательное описание над полем reason. */
  message?: string;
  /** Label под textarea (по умолчанию «Причина»). */
  reasonLabel?: string;
  /** Placeholder для textarea. */
  reasonPlaceholder?: string;
  /** Подпись на кнопке подтверждения. */
  confirmLabel?: string;
  /** Подпись на кнопке отмены. */
  cancelLabel?: string;
  /** Отображать ли `destructive`-акцент (warn-color). */
  destructive?: boolean;
  /** Максимум символов (по умолчанию 500, QC4). */
  maxLength?: number;
  /** Предзаполнить textarea (редактирование существующей причины). */
  initialReason?: string;
}

/**
 * Material dialog для действий, требующих текстовую причину
 * (QC4, M07 G7). Заменяет нативный `window.prompt()` — даёт фокус-трап,
 * валидацию non-empty + maxLength, destructive-акцент для
 * «Отменить пару», auto-trim reason перед возвратом.
 *
 * Результат `afterClosed()`:
 *  - `string` — обрезанный trim()'ом reason при confirm;
 *  - `null` — при cancel / Escape / backdrop click.
 */
@Component({
  selector: 'app-confirm-with-reason-dialog',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule,
    FormsModule,
    MatDialogModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
  ],
  template: `
    <h2 mat-dialog-title>{{ data.title }}</h2>
    <mat-dialog-content>
      @if (data.message) {
        <p class="cwr-message">{{ data.message }}</p>
      }
      <mat-form-field appearance="outline" class="cwr-field">
        <mat-label>{{ data.reasonLabel ?? 'Причина' }}</mat-label>
        <textarea
          matInput
          rows="3"
          cdkFocusInitial
          [attr.maxlength]="maxLength"
          [placeholder]="data.reasonPlaceholder ?? ''"
          [(ngModel)]="reason"
          (ngModelChange)="onChange($event)"
        ></textarea>
        <mat-hint align="end">{{ length() }} / {{ maxLength }}</mat-hint>
      </mat-form-field>
      @if (errorMessage()) {
        <p class="cwr-error" role="alert">{{ errorMessage() }}</p>
      }
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button (click)="cancel()">{{ data.cancelLabel ?? 'Отмена' }}</button>
      <button
        mat-flat-button
        [color]="data.destructive ? 'warn' : 'primary'"
        [disabled]="!canConfirm()"
        (click)="confirm()"
      >
        {{ data.confirmLabel ?? 'Подтвердить' }}
      </button>
    </mat-dialog-actions>
  `,
  styles: [`
    .cwr-message {
      margin: 0 0 12px;
      color: var(--text-primary);
      font-size: 0.9375rem;
      line-height: 1.5;
    }
    .cwr-field {
      width: 100%;
      min-width: 320px;
    }
    .cwr-error {
      margin: 0;
      color: var(--mat-sys-error, #c62828);
      font-size: 0.8125rem;
    }
  `],
})
export class ConfirmWithReasonDialogComponent {
  readonly data = inject<ConfirmWithReasonDialogData>(MAT_DIALOG_DATA);
  private readonly dialogRef = inject(MatDialogRef<ConfirmWithReasonDialogComponent, string | null>);

  protected readonly maxLength = this.data.maxLength ?? 500;

  protected reason = this.data.initialReason ?? '';
  private readonly trimmed = signal<string>(this.reason.trim());

  protected readonly length = computed(() => this.trimmed().length);
  protected readonly canConfirm = computed(() => {
    const len = this.trimmed().length;
    return len > 0 && len <= this.maxLength;
  });
  protected readonly errorMessage = computed(() => {
    if (this.trimmed().length > this.maxLength) {
      return `Не более ${this.maxLength} символов.`;
    }
    return null;
  });

  protected onChange(value: string): void {
    this.trimmed.set(value.trim());
  }

  protected confirm(): void {
    if (!this.canConfirm()) return;
    this.dialogRef.close(this.trimmed());
  }

  protected cancel(): void {
    this.dialogRef.close(null);
  }
}
