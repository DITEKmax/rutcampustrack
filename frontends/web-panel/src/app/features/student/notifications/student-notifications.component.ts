import {
  ChangeDetectionStrategy, Component, DestroyRef, OnInit, computed, inject, signal,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { trigger, transition, style, animate } from '@angular/animations';
import { StudentStompService } from '../shared/student-stomp.service';
import { StudentNotificationBadgeService } from '../shared/student-notification-badge.service';
import type { NotificationItem } from '../shared/student-schedule.types';
import { NotificationItemComponent } from './notification-item/notification-item.component';

const STORAGE_KEY = 'rct-notifications';
const MAX_ITEMS = 100;
const STORED_TYPES = ['lesson.started', 'lesson.cancelled', 'homework.published', 'homework.updated', 'attendance.marked'];

@Component({
  selector: 'app-student-notifications',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, NotificationItemComponent],
  animations: [
    trigger('routeFade', [
      transition(':enter', [
        style({ opacity: 0, transform: 'translateY(8px)' }),
        animate('200ms var(--ease-out, ease-out)', style({ opacity: 1, transform: 'translateY(0)' })),
      ]),
    ]),
  ],
  templateUrl: './student-notifications.component.html',
  styleUrl: './student-notifications.component.css',
})
export class StudentNotificationsComponent implements OnInit {
  private readonly stompService = inject(StudentStompService);
  private readonly badgeService = inject(StudentNotificationBadgeService);
  private readonly destroyRef = inject(DestroyRef);

  readonly items = signal<NotificationItem[]>(this.loadFromStorage());

  readonly sortedItems = computed(() =>
    [...this.items()].sort((a, b) => b.receivedAt.getTime() - a.receivedAt.getTime()),
  );

  readonly allRead = computed(() =>
    this.items().length > 0 && this.items().every(i => i.read),
  );

  ngOnInit(): void {
    // Mark all items as read
    this.items.update(list => list.map(i => ({ ...i, read: true })));
    this.persistToStorage();
    // Reset badge
    this.badgeService.reset();

    // Subscribe to new STOMP events (for items arriving while on this page)
    this.stompService.onAnyEvent$
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(envelope => {
        if (!STORED_TYPES.includes(envelope.type)) return;
        const newItem: NotificationItem = {
          id: crypto.randomUUID(),
          type: envelope.type,
          payload: envelope.payload,
          receivedAt: new Date(),
          read: true, // already on the page — immediately read
        };
        this.items.update(list => {
          const updated = [newItem, ...list];
          return updated.length > MAX_ITEMS ? updated.slice(0, MAX_ITEMS) : updated;
        });
        this.persistToStorage();
      });
  }

  private loadFromStorage(): NotificationItem[] {
    try {
      const raw = sessionStorage.getItem(STORAGE_KEY);
      if (!raw) return [];
      const parsed = JSON.parse(raw) as Array<NotificationItem & { receivedAt: string }>;
      return parsed.map(i => ({ ...i, receivedAt: new Date(i.receivedAt) }));
    } catch {
      return [];
    }
  }

  private persistToStorage(): void {
    try {
      sessionStorage.setItem(STORAGE_KEY, JSON.stringify(this.items()));
    } catch {
      // sessionStorage full — ignore
    }
  }
}
