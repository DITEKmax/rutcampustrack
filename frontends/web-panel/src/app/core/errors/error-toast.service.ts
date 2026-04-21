import { Injectable, inject } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ErrorToastComponent } from './error-toast.component';
import type { ProblemDetails } from './problem-details';

/**
 * Global RFC 9457 error toast dispatcher.
 *
 * M07 G4 (QC3): показывает Material snackbar со структурированным
 * ProblemDetails (title + detail + traceId copy button).
 *
 * Suppression: дубликаты (same status + type + detail в окне 1.5с) пропускаются,
 * чтобы batch-операции не залипали в спам при fan-out 4xx.
 */
@Injectable({ providedIn: 'root' })
export class ErrorToastService {
  private readonly snackBar = inject(MatSnackBar);
  private lastKey: string | null = null;
  private lastTimestamp = 0;

  show(problem: ProblemDetails): void {
    const key = `${problem.status}|${problem.type}|${problem.detail}`;
    const now = Date.now();
    if (this.lastKey === key && now - this.lastTimestamp < 1500) return;
    this.lastKey = key;
    this.lastTimestamp = now;

    this.snackBar.openFromComponent(ErrorToastComponent, {
      duration: 5000,
      horizontalPosition: 'end',
      verticalPosition: 'bottom',
      panelClass: ['error-toast-panel'],
      data: problem,
    });
  }
}
