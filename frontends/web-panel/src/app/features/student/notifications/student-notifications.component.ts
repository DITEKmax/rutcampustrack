import {
  ChangeDetectionStrategy, Component, OnInit, computed, inject,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { trigger, transition, style, animate } from '@angular/animations';
import { NotificationCenterService } from '../../../core/notifications/notification-center.service';
import { NotificationHistoryService } from '../../../core/notifications/notification-history.service';
import type { NotificationHistoryItem } from '../../../core/notifications/notification-history.api';
import type { NotificationItem } from '../shared/student-schedule.types';
import { NotificationItemComponent } from './notification-item/notification-item.component';

/**
 * Страница «Уведомления» для студента и старосты.
 *
 * NOTIF unification: источник правды — серверная история M10
 * ({@link NotificationHistoryService}), а не sessionStorage. Это даёт
 * 30-дневный лог переживающий релогин/F5/переустановку клиента. Live-события
 * через STOMP инвалидируют список через {@code refreshList()} в
 * {@link NotificationCenterService}. sessionStorage-кеш центра используется
 * только для bell-badge между страницами в рамках сессии.
 */
@Component({
  selector: 'app-student-notifications',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, RouterLink, NotificationItemComponent],
  animations: [
    trigger('routeFade', [
      transition(':enter', [
        style({ opacity: 0, transform: 'translateY(8px)' }),
        animate('200ms cubic-bezier(0.16, 1, 0.3, 1)', style({ opacity: 1, transform: 'translateY(0)' })),
      ]),
    ]),
  ],
  templateUrl: './student-notifications.component.html',
  styleUrl: './student-notifications.component.css',
})
export class StudentNotificationsComponent implements OnInit {
  private readonly center = inject(NotificationCenterService);
  private readonly history = inject(NotificationHistoryService);

  readonly items = this.history.items;
  readonly hasMore = this.history.hasMore;
  readonly loading = this.history.loading;

  readonly sortedItems = computed<NotificationItem[]>(() =>
    this.history.items().map(toItem),
  );

  readonly allRead = computed(() =>
    this.sortedItems().length > 0 && this.sortedItems().every((i) => i.read),
  );

  ngOnInit(): void {
    this.history.loadFirstPage(false);
    this.history.markAllRead();
    this.center.markAllRead();
  }

  loadMore(): void {
    this.history.loadMore(false);
  }
}

function toItem(record: NotificationHistoryItem): NotificationItem {
  return {
    id: record.id,
    type: record.type,
    payload: record.payload,
    receivedAt: new Date(record.sentAt),
    read: record.readAt !== null,
  };
}
