import { Injectable, Signal, inject, signal } from '@angular/core';
import { NotificationHistoryApi, NotificationHistoryPage } from './notification-history.api';

/**
 * Stateful façade над {@link NotificationHistoryApi} — Signal-based
 * snapshot серверной истории + unread-count (M10 G7).
 *
 * Использование:
 * - `refreshUnreadCount()` — вызывается из NotificationCenterService
 *   после каждого STOMP event; также при вызове markAllRead.
 * - `loadFirstPage()` — для страницы «История уведомлений» (если
 *   будет отдельный view в v0.1).
 *
 * Hybrid D7: SignalSubject остаётся внутри NotificationCenterService
 * (broadcast sessionStorage items), этот service — независимый
 * server-side snapshot.
 */
@Injectable({ providedIn: 'root' })
export class NotificationHistoryService {
  private readonly api = inject(NotificationHistoryApi);

  private readonly _serverUnreadCount = signal<number | null>(null);
  readonly serverUnreadCount: Signal<number | null> =
    this._serverUnreadCount.asReadonly();

  private readonly _firstPage = signal<NotificationHistoryPage | null>(null);
  readonly firstPage: Signal<NotificationHistoryPage | null> =
    this._firstPage.asReadonly();

  /** Best-effort. Ошибка не бросается — offline / 401 просто оставляют null. */
  refreshUnreadCount(): void {
    this.api.unreadCount().subscribe({
      next: (count) => this._serverUnreadCount.set(count),
      error: () => {
        // оставляем предыдущее значение; UI fallback на client-side count
      },
    });
  }

  loadFirstPage(unreadOnly = false): void {
    this.api.list(0, 20, unreadOnly).subscribe({
      next: (page) => this._firstPage.set(page),
      error: () => {
        // noop
      },
    });
  }

  /**
   * Сервер best-effort mark-all-read + refresh локального счётчика.
   * Возвращает Promise для callsites которые хотят дождаться.
   */
  markAllRead(): Promise<void> {
    return new Promise((resolve) => {
      this.api.markAllRead().subscribe({
        next: () => {
          this._serverUnreadCount.set(0);
          resolve();
        },
        error: () => resolve(),
      });
    });
  }
}
