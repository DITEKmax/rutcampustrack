import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

import { AdminApiService } from '../../shared/admin-api.service';
import type { PromotionSummary } from '../../shared/types';

type Phase = 'loading' | 'preview' | 'executing' | 'done' | 'error';

/**
 * BUG-006-6 / plan 58-08: модалка перевода групп на следующий курс.
 *
 * Фазы:
 *  1. loading   — первичный preview (POST /groups/promote/preview).
 *  2. preview   — показываем 3 секции (rename / archive / conflicts).
 *  3. executing — нажат "Подтвердить"; идёт POST /groups/promote.
 *  4. done      — итоговый summary, кнопка закрытия.
 *  5. error     — ошибка запроса.
 *
 * Defence против T-58-08-01 (double-click / form re-submission): кнопка "Подтвердить"
 * блокируется при переходе в executing, а также если preview пустой.
 */
@Component({
  selector: 'app-promotion-preview-dialog',
  standalone: true,
  imports: [CommonModule, MatDialogModule, MatButtonModule, MatProgressSpinnerModule],
  templateUrl: './promotion-preview-dialog.component.html',
  styleUrls: ['./promotion-preview-dialog.component.scss'],
})
export class PromotionPreviewDialogComponent implements OnInit {
  private readonly api = inject(AdminApiService);
  private readonly dialogRef = inject(MatDialogRef<PromotionPreviewDialogComponent>);

  readonly phase = signal<Phase>('loading');
  readonly summary = signal<PromotionSummary | null>(null);
  readonly result = signal<PromotionSummary | null>(null);
  readonly errorMessage = signal<string | null>(null);

  ngOnInit(): void {
    this.loadPreview();
  }

  loadPreview(): void {
    this.phase.set('loading');
    this.errorMessage.set(null);
    this.api.promotePreview().subscribe({
      next: s => {
        this.summary.set(s);
        this.phase.set('preview');
      },
      error: err => {
        this.errorMessage.set(err?.error?.detail ?? 'Не удалось получить предпросмотр.');
        this.phase.set('error');
      },
    });
  }

  isEmpty(): boolean {
    const s = this.summary();
    if (!s) return true;
    return s.toPromote.length === 0 && s.toArchive.length === 0;
  }

  confirm(): void {
    if (this.phase() !== 'preview' || this.isEmpty()) return;
    this.phase.set('executing');
    this.errorMessage.set(null);
    this.api.promote().subscribe({
      next: s => {
        this.result.set(s);
        this.phase.set('done');
      },
      error: err => {
        this.errorMessage.set(err?.error?.detail ?? 'Не удалось выполнить перевод.');
        this.phase.set('error');
      },
    });
  }

  cancel(): void {
    this.dialogRef.close();
  }

  close(): void {
    this.dialogRef.close('done');
  }
}
