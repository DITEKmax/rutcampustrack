import {
  ChangeDetectionStrategy, Component, OnInit, computed, inject,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { trigger, transition, style, animate } from '@angular/animations';
import { NotificationCenterService, NotificationRecord } from '../../../core/notifications/notification-center.service';
import type { NotificationItem } from '../shared/student-schedule.types';
import { NotificationItemComponent } from './notification-item/notification-item.component';

/**
 * Страница «Уведомления» для студента и старосты.
 *
 * После Phase v9 централизации источника правды компонент больше не поднимает
 * собственный STOMP-клиент — всё приходит из {@link NotificationCenterService},
 * который живёт в {@code providedIn: 'root'} и подключается сразу после логина
 * (см. ShellComponent). Это устраняет баг, когда уведомления не появлялись до
 * тех пор, пока пользователь не открывал конкретную страницу.
 *
 * Страница отображает общий список, который глобальный сервис уже собрал;
 * дополнительная фильтрация user_scoped делается в самом центре.
 */
@Component({
  selector: 'app-student-notifications',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, NotificationItemComponent],
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

  /** Прокси к {@link NotificationCenterService.items} для существующего шаблона. */
  readonly items = this.center.items;

  /**
   * Адаптер: глобальный сервис хранит receivedAt как ISO-строку (иначе
   * sessionStorage теряет Date при (de)serialization), а существующий
   * {@link NotificationItemComponent} ждёт {@link NotificationItem} с Date.
   */
  readonly sortedItems = computed<NotificationItem[]>(() => {
    return this.center.items().map(toItem).sort((a, b) => b.receivedAt.getTime() - a.receivedAt.getTime());
  });

  readonly allRead = computed(() =>
    this.center.items().length > 0 && this.center.unreadCount() === 0,
  );

  ngOnInit(): void {
    // Пользователь зашёл на страницу — сбрасываем счётчик. Записи остаются.
    this.center.markAllRead();
  }
}

function toItem(record: NotificationRecord): NotificationItem {
  return {
    id: record.id,
    type: record.type,
    payload: record.payload,
    receivedAt: new Date(record.receivedAt),
    read: record.read,
  };
}
