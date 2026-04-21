import { ChangeDetectionStrategy, Component, InjectionToken, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MAT_SNACK_BAR_DATA, MatSnackBarRef } from '@angular/material/snack-bar';
import type { ProblemDetails } from './problem-details';

/** Injection token для DI ProblemDetails в snackbar-компонент. */
export const ERROR_TOAST_DATA = new InjectionToken<ProblemDetails>('ERROR_TOAST_DATA');

/**
 * Material snackbar с title/detail из RFC 9457 ProblemDetails. traceId
 * скрыт под compact copy-button для обращения в поддержку.
 */
@Component({
  selector: 'app-error-toast',
  standalone: true,
  imports: [CommonModule, MatIconModule, MatButtonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="error-toast" role="alert">
      <mat-icon class="error-toast__icon" aria-hidden="true">error_outline</mat-icon>
      <div class="error-toast__body">
        <p class="error-toast__title">{{ problem.title }}</p>
        @if (problem.detail) {
          <p class="error-toast__detail">{{ problem.detail }}</p>
        }
        @if (problem.traceId) {
          <button
            type="button"
            class="error-toast__trace"
            (click)="copyTraceId()"
            [attr.aria-label]="'Скопировать trace id: ' + problem.traceId"
          >
            <mat-icon aria-hidden="true">content_copy</mat-icon>
            {{ copied() ? 'Скопировано' : 'trace: ' + traceShort }}
          </button>
        }
      </div>
      <button
        mat-icon-button
        class="error-toast__close"
        aria-label="Закрыть уведомление"
        (click)="snackBarRef.dismiss()"
      >
        <mat-icon>close</mat-icon>
      </button>
    </div>
  `,
  styles: [`
    .error-toast {
      display: flex;
      align-items: flex-start;
      gap: 12px;
      min-width: 280px;
      max-width: 420px;
      color: var(--mat-sys-on-error-container, #fff);
    }
    .error-toast__icon {
      flex-shrink: 0;
      color: var(--mat-sys-error, #ef5350);
    }
    .error-toast__body {
      flex: 1;
      min-width: 0;
    }
    .error-toast__title {
      margin: 0 0 4px;
      font-weight: 600;
      font-size: 14px;
      line-height: 1.2;
    }
    .error-toast__detail {
      margin: 0;
      font-size: 12px;
      line-height: 1.3;
      opacity: 0.85;
    }
    .error-toast__trace {
      display: inline-flex;
      align-items: center;
      gap: 4px;
      margin-top: 6px;
      padding: 2px 8px;
      font-size: 11px;
      font-family: monospace;
      border: 1px solid rgba(255, 255, 255, 0.25);
      border-radius: 999px;
      background: transparent;
      color: inherit;
      cursor: pointer;
    }
    .error-toast__trace:hover {
      background: rgba(255, 255, 255, 0.08);
    }
    .error-toast__trace mat-icon {
      font-size: 12px;
      width: 12px;
      height: 12px;
    }
    .error-toast__close {
      flex-shrink: 0;
      margin-top: -4px;
      color: inherit;
    }
  `],
})
export class ErrorToastComponent {
  protected readonly snackBarRef = inject(MatSnackBarRef);
  protected readonly problem = inject<ProblemDetails>(MAT_SNACK_BAR_DATA);
  protected readonly copied = signal(false);

  protected get traceShort(): string {
    return this.problem.traceId ? `${this.problem.traceId.slice(0, 8)}…` : '';
  }

  protected async copyTraceId(): Promise<void> {
    if (!this.problem.traceId) return;
    try {
      await navigator.clipboard.writeText(this.problem.traceId);
      this.copied.set(true);
      setTimeout(() => this.copied.set(false), 1500);
    } catch {
      /* clipboard may be unavailable in non-secure context */
    }
  }
}
