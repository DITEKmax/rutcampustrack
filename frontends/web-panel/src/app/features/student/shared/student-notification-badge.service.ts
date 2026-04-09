import { Injectable, signal } from '@angular/core';

/**
 * Singleton holding the count of unread notifications in the current session.
 * Incremented by StudentStompService on each stored event.
 * Reset to 0 by StudentNotificationsComponent.ngOnInit().
 * Read by SidebarComponent to render the bell badge.
 */
@Injectable({ providedIn: 'root' })
export class StudentNotificationBadgeService {
  private readonly _count = signal(0);
  readonly unreadCount = this._count.asReadonly();

  increment(): void {
    this._count.update(n => n + 1);
  }

  reset(): void {
    this._count.set(0);
  }
}
