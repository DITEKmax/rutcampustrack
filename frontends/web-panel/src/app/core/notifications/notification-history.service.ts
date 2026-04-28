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
    this._items.set([]);
    this._pageNumber.set(-1);
    this._hasMore.set(true);
    this._loading.set(true);
    this.api.list(0, 20, unreadOnly).subscribe({
      next: (page) => {
        this._firstPage.set(page);
        this._items.set([...page.items]);
        this._pageNumber.set(page.pageNumber);
        this._hasMore.set(page.pageNumber + 1 < page.totalPages);
        this._loading.set(false);
      },
      error: () => {
        this._loading.set(false);
      },
    });
  }

  /** Дозагрузить следующую страницу (для infinite-scroll). Idempotent. */
  loadMore(unreadOnly = false): void {
    if (this._loading() || !this._hasMore()) return;
    const next = this._pageNumber() + 1;
    this._loading.set(true);
    this.api.list(next, 20, unreadOnly).subscribe({
      next: (page) => {
        this._items.update((curr) => [...curr, ...page.items]);
        this._pageNumber.set(page.pageNumber);
        this._hasMore.set(page.pageNumber + 1 < page.totalPages);
        this._loading.set(false);
      },
      error: () => this._loading.set(false),
    });
  }

  /** Совокупный аккумулятор всех загруженных страниц для UI. */
  private readonly _items = signal<NotificationHistoryPage['items']>([]);
  readonly items = this._items.asReadonly();

  private readonly _pageNumber = signal<number>(-1);
  private readonly _hasMore = signal<boolean>(true);
  readonly hasMore = this._hasMore.asReadonly();

  private readonly _loading = signal<boolean>(false);
  readonly loading = this._loading.asReadonly();

  /** Пометить запись прочитанной локально (после клика) + best-effort серверу. */
  markItemRead(id: string): void {
    this._items.update((arr) =>
      arr.map((it) =>
        it.id === id && it.readAt === null
          ? { ...it, readAt: new Date().toISOString() }
          : it,
      ),
    );
    this.api.markRead(id).subscribe({ error: () => {} });
  }

  /** Live-вставка из STOMP: новая запись из сервера ещё не подгружена,
   *  но уже видна в feed'е. Refresh first page чтобы id из бекенда. */
  refreshList(unreadOnly = false): void {
    if (this._items().length === 0) return; // ещё не загружали — пропустим
    this.api.list(0, 20, unreadOnly).subscribe({
      next: (page) => {
        // merge: новые записи идут перед уже-загруженными (server отдаёт desc),
        // дубли по id отбрасываем.
        this._items.update((curr) => {
          const seen = new Set(curr.map((c) => c.id));
          const fresh = page.items.filter((p) => !seen.has(p.id));
          return [...fresh, ...curr];
        });
      },
      error: () => {},
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
          // Локально тоже помечаем — иначе после refresh badge всплывёт снова.
          this._items.update((arr) =>
            arr.map((it) =>
              it.readAt === null
                ? { ...it, readAt: new Date().toISOString() }
                : it,
            ),
          );
          resolve();
        },
        error: () => resolve(),
      });
    });
  }
}
