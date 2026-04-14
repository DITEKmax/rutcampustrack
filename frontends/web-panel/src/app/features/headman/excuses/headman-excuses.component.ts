import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { trigger, transition, style, animate } from '@angular/animations';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';

import { HeadmanApiService } from '../shared/headman-api.service';
import { AuthService } from '../../../core/auth/auth.service';
import {
  EXCUSE_STATUS_LABELS,
  EXCUSE_TYPE_LABELS,
  ExcuseTicket,
  ExcuseType,
  ExcuseTicketStatus,
} from './excuse.types';

/**
 * Headman excuses approval page (Phase 59, D-23/D-24).
 *
 * Responsibilities:
 * - Load excuse tickets for the headman's group via HeadmanApiService.
 * - Render pending (submitted) tickets on top with Approve / Reject actions.
 * - Render resolved (approved/rejected/draft) tickets below.
 * - Require a comment on reject; surface 403 / 409 from the backend with
 *   friendly Russian messages in MatSnackBar.
 *
 * Upstream contract (plan 59-01 / 59-04):
 * - GET  /api/attendance/excuses/group/{groupId}
 * - PATCH /api/attendance/excuses/{id}/status
 *   - 409 is returned when headman approves own ticket (D-13) or the ticket
 *     is already decided (D-18); both are mapped to a single user-friendly
 *     message — backend decides, UI just tells the user to refresh.
 */
@Component({
  selector: 'app-headman-excuses',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, FormsModule, MatSnackBarModule],
  animations: [
    trigger('routeFade', [
      transition(':enter', [
        style({ opacity: 0, transform: 'translateY(8px)' }),
        animate('200ms cubic-bezier(0.16, 1, 0.3, 1)', style({ opacity: 1, transform: 'translateY(0)' })),
      ]),
    ]),
  ],
  host: { '[@routeFade]': '' },
  template: `
    <div class="page-stack">
      <div class="page-header">
        <div>
          <span class="page-eyebrow">Старостат</span>
          <h1 class="page-title">Пропуски группы</h1>
        </div>
      </div>

      @if (noGroup()) {
        <div class="page-card">
          <div class="page-empty" role="status">
            <div class="page-empty__icon"><i class="ph ph-users"></i></div>
            <p class="page-empty__title">Группа не определена</p>
            <p class="page-empty__text">
              Ваш профиль не привязан к группе — функция недоступно. Обратитесь к администратору.
            </p>
          </div>
        </div>
      } @else if (loading()) {
        <div class="page-card">
          <div class="skeleton-row" aria-hidden="true"></div>
          <div class="skeleton-row" aria-hidden="true"></div>
          <span aria-live="polite" class="sr-only">Загрузка...</span>
        </div>
      } @else if (loadError()) {
        <div class="page-card">
          <div class="page-error" role="alert">
            <i class="ph ph-warning-circle"></i>
            {{ loadError() }}
          </div>
        </div>
      } @else {
        <!-- Pending section -->
        <section class="page-card">
          <div class="page-card__header">
            <h2>На рассмотрении</h2>
            <span class="page-card__badge">{{ pendingTickets().length }}</span>
          </div>

          @if (pendingTickets().length === 0) {
            <div class="page-empty">
              <p class="page-empty__title">Нет заявок на рассмотрении</p>
              <p class="page-empty__text">Новые заявки студентов появятся здесь автоматически.</p>
            </div>
          } @else {
            @for (ticket of pendingTickets(); track ticket.id) {
              <article class="excuse-card">
                <header class="excuse-card__head">
                  <div>
                    <strong class="excuse-card__student">{{ ticket.studentName }}</strong>
                    <span class="excuse-card__type">{{ typeLabel(ticket.excuseType) }}</span>
                  </div>
                  <span class="excuse-card__meta">
                    {{ ticket.lessonIds.length }} урок(ов) · {{ ticket.createdAt | date:'dd.MM.yyyy HH:mm' }}
                  </span>
                </header>
                @if (ticket.comment) {
                  <p class="excuse-card__comment">{{ ticket.comment }}</p>
                }
                <div class="excuse-card__actions">
                  @if (rejectingId() !== ticket.id) {
                    <button
                      type="button"
                      class="btn-brand"
                      [disabled]="busyId() === ticket.id"
                      (click)="approve(ticket.id)"
                    >
                      <i class="ph ph-check"></i> Одобрить
                    </button>
                    <button
                      type="button"
                      class="btn-ghost"
                      [disabled]="busyId() === ticket.id"
                      (click)="startReject(ticket.id)"
                    >
                      <i class="ph ph-x"></i> Отклонить
                    </button>
                  } @else {
                    <div class="excuse-card__reject">
                      <label>
                        <span class="sr-only">Комментарий к отклонению</span>
                        <input
                          type="text"
                          class="form-input"
                          placeholder="Укажите причину отклонения"
                          [ngModel]="rejectComment()"
                          (ngModelChange)="rejectComment.set($event)"
                          [attr.aria-invalid]="validationError() ? 'true' : null"
                        />
                      </label>
                      @if (validationError()) {
                        <p class="form-error" role="alert">{{ validationError() }}</p>
                      }
                      <div class="excuse-card__reject-actions">
                        <button
                          type="button"
                          class="btn-brand"
                          [disabled]="busyId() === ticket.id"
                          (click)="confirmReject(ticket.id)"
                        >
                          Подтвердить отклонение
                        </button>
                        <button type="button" class="btn-ghost" (click)="cancelReject()">
                          Отмена
                        </button>
                      </div>
                    </div>
                  }
                </div>
              </article>
            }
          }
        </section>

        <!-- Resolved section -->
        @if (resolvedTickets().length > 0) {
          <section class="page-card">
            <div class="page-card__header">
              <h2>Решения</h2>
              <span class="page-card__badge">{{ resolvedTickets().length }}</span>
            </div>
            @for (ticket of resolvedTickets(); track ticket.id) {
              <article class="excuse-card excuse-card--resolved">
                <header class="excuse-card__head">
                  <div>
                    <strong class="excuse-card__student">{{ ticket.studentName }}</strong>
                    <span class="excuse-card__type">{{ typeLabel(ticket.excuseType) }}</span>
                  </div>
                  <span
                    class="excuse-card__status"
                    [class.excuse-card__status--approved]="ticket.status === 'approved'"
                    [class.excuse-card__status--rejected]="ticket.status === 'rejected'"
                  >
                    {{ statusLabel(ticket.status) }}
                  </span>
                </header>
                @if (ticket.decisionComment) {
                  <p class="excuse-card__comment">{{ ticket.decisionComment }}</p>
                }
              </article>
            }
          </section>
        }
      }
    </div>
  `,
  styles: [
    `
      .excuse-card {
        border: 1px solid var(--color-border, #e5e7eb);
        border-radius: 8px;
        padding: 12px 16px;
        margin-bottom: 10px;
        background: var(--color-surface, #fff);
      }
      .excuse-card--resolved { opacity: 0.85; }
      .excuse-card__head {
        display: flex;
        justify-content: space-between;
        align-items: baseline;
        gap: 12px;
        flex-wrap: wrap;
      }
      .excuse-card__student { font-weight: 600; margin-right: 10px; }
      .excuse-card__type {
        font-size: 0.9em;
        color: var(--color-text-muted, #64748b);
      }
      .excuse-card__meta {
        font-size: 0.85em;
        color: var(--color-text-muted, #64748b);
      }
      .excuse-card__comment {
        margin: 8px 0;
        color: var(--color-text, #111827);
      }
      .excuse-card__actions {
        display: flex;
        gap: 8px;
        margin-top: 8px;
        flex-wrap: wrap;
      }
      .excuse-card__reject { flex: 1; display: flex; flex-direction: column; gap: 6px; }
      .excuse-card__reject-actions { display: flex; gap: 8px; }
      .excuse-card__status {
        font-size: 0.85em;
        padding: 2px 8px;
        border-radius: 999px;
        background: var(--color-surface-2, #f1f5f9);
      }
      .excuse-card__status--approved { background: #dcfce7; color: #14532d; }
      .excuse-card__status--rejected { background: #fee2e2; color: #7f1d1d; }
      .form-input {
        width: 100%;
        padding: 6px 10px;
        border: 1px solid var(--color-border, #e5e7eb);
        border-radius: 6px;
      }
      .form-error { color: #b91c1c; font-size: 0.85em; margin: 0; }
      .sr-only {
        position: absolute;
        width: 1px; height: 1px;
        padding: 0; margin: -1px;
        overflow: hidden; clip: rect(0, 0, 0, 0);
        white-space: nowrap; border: 0;
      }
      .page-card__badge {
        background: var(--color-surface-2, #f1f5f9);
        padding: 2px 8px;
        border-radius: 999px;
        font-size: 0.85em;
        margin-left: 8px;
      }
    `,
  ],
})
export class HeadmanExcusesComponent implements OnInit {
  private readonly headmanApi = inject(HeadmanApiService);
  private readonly auth = inject(AuthService);
  private readonly snackBar = inject(MatSnackBar);

  readonly loading = signal<boolean>(false);
  readonly loadError = signal<string | null>(null);
  readonly tickets = signal<ExcuseTicket[]>([]);

  /** id of the ticket for which the inline reject form is open. */
  readonly rejectingId = signal<string | null>(null);
  readonly rejectComment = signal<string>('');
  readonly validationError = signal<string | null>(null);
  /** id of the ticket currently hitting the network (disables its buttons). */
  readonly busyId = signal<string | null>(null);

  readonly pendingTickets = computed(() =>
    this.tickets().filter(t => t.status === 'submitted'),
  );
  readonly resolvedTickets = computed(() =>
    this.tickets().filter(t => t.status !== 'submitted'),
  );

  readonly noGroup = computed(() => this.auth.currentUser()?.groupId == null);

  ngOnInit(): void {
    this.loadTickets();
  }

  loadTickets(): void {
    const groupId = this.auth.currentUser()?.groupId;
    if (groupId == null) {
      this.tickets.set([]);
      return;
    }
    this.loading.set(true);
    this.loadError.set(null);
    this.headmanApi.getGroupExcuses(groupId).subscribe({
      next: list => {
        this.tickets.set(list);
        this.loading.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.loading.set(false);
        this.loadError.set(
          err.status === 403
            ? 'Доступ запрещён — только староста группы может просматривать заявки.'
            : 'Не удалось загрузить заявки. Попробуйте позже.',
        );
      },
    });
  }

  approve(id: string): void {
    this.busyId.set(id);
    this.headmanApi.approveExcuse(id, null).subscribe({
      next: () => {
        this.busyId.set(null);
        this.snackBar.open('Заявка одобрена', 'OK', { duration: 3000 });
        this.loadTickets();
      },
      error: (err: HttpErrorResponse) => {
        this.busyId.set(null);
        this.snackBar.open(this.errorMessage(err, 'approve'), 'OK', { duration: 5000 });
      },
    });
  }

  startReject(id: string): void {
    this.rejectingId.set(id);
    this.rejectComment.set('');
    this.validationError.set(null);
  }

  cancelReject(): void {
    this.rejectingId.set(null);
    this.rejectComment.set('');
    this.validationError.set(null);
  }

  confirmReject(id: string): void {
    const comment = this.rejectComment().trim();
    if (!comment) {
      this.validationError.set('Введите комментарий для отклонения');
      return;
    }
    this.validationError.set(null);
    this.busyId.set(id);
    this.headmanApi.rejectExcuse(id, comment).subscribe({
      next: () => {
        this.busyId.set(null);
        this.rejectingId.set(null);
        this.rejectComment.set('');
        this.snackBar.open('Заявка отклонена', 'OK', { duration: 3000 });
        this.loadTickets();
      },
      error: (err: HttpErrorResponse) => {
        this.busyId.set(null);
        this.snackBar.open(this.errorMessage(err, 'reject'), 'OK', { duration: 5000 });
      },
    });
  }

  typeLabel(type: ExcuseType): string {
    return EXCUSE_TYPE_LABELS[type] ?? type;
  }

  statusLabel(status: ExcuseTicketStatus): string {
    return EXCUSE_STATUS_LABELS[status] ?? status;
  }

  private errorMessage(err: HttpErrorResponse, action: 'approve' | 'reject'): string {
    if (err.status === 409) {
      // D-13 (cannot decide own ticket) and D-18 (already decided) both map to 409.
      return action === 'approve'
        ? 'Невозможно одобрить: решение уже принято или заявка недоступна для одобрения.'
        : 'Невозможно отклонить: решение уже принято или заявка недоступна для отклонения.';
    }
    if (err.status === 403) {
      return 'Недостаточно прав — эта заявка принадлежит другой группе.';
    }
    if (err.status === 404) {
      return 'Заявка не найдена — возможно, она была удалена.';
    }
    return action === 'approve'
      ? 'Не удалось одобрить заявку. Попробуйте позже.'
      : 'Не удалось отклонить заявку. Попробуйте позже.';
  }
}
