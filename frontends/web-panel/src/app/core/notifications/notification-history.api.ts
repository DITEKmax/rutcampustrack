import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';

/**
 * Backend notification history REST client (M10 G7).
 *
 * Backend persist'ит только user-facing events (excuse.*, late_checkin.*,
 * attendance.marked — D6). Broadcast events (lesson.*, homework.*) живут
 * в sessionStorage — сервер их не хранит.
 */

export interface NotificationHistoryItem {
  id: string;
  userId: number;
  type: string;
  payload: Record<string, unknown>;
  sentAt: string;
  readAt: string | null;
  _links?: Record<string, { href: string }>;
}

export interface NotificationHistoryPage {
  items: NotificationHistoryItem[];
  totalElements: number;
  totalPages: number;
  pageNumber: number;
}

interface PagedResponse {
  _embedded?: {
    notificationHistoryDtoList?: NotificationHistoryItem[];
  };
  page: { size: number; totalElements: number; totalPages: number; number: number };
}

@Injectable({ providedIn: 'root' })
export class NotificationHistoryApi {
  private http = inject(HttpClient);

  list(page: number, size = 20, unreadOnly = false): Observable<NotificationHistoryPage> {
    const params = new HttpParams()
      .set('page', page)
      .set('size', size)
      .set('unreadOnly', unreadOnly)
      .set('sort', 'sentAt,desc');
    return this.http.get<PagedResponse>('/api/notifications', { params }).pipe(
      map((resp) => ({
        items: resp._embedded?.notificationHistoryDtoList ?? [],
        totalElements: resp.page.totalElements,
        totalPages: resp.page.totalPages,
        pageNumber: resp.page.number,
      })),
    );
  }

  unreadCount(): Observable<number> {
    return this.http
      .get<{ count: number }>('/api/notifications/unread-count')
      .pipe(map((r) => r.count));
  }

  markRead(id: string): Observable<void> {
    return this.http.patch<void>(
      `/api/notifications/${encodeURIComponent(id)}/read`,
      null,
    );
  }

  markAllRead(): Observable<void> {
    return this.http.post<void>('/api/notifications/mark-all-read', null);
  }
}
