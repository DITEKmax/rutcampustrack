import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import type { NotificationItem } from '../../shared/student-schedule.types';

@Component({
  selector: 'app-notification-item',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule],
  template: `
    <div
      class="notification-item"
      [class.is-unread]="!item.read"
      role="listitem"
      [attr.aria-label]="'Уведомление: ' + heading + ', ' + relativeTime">

      <div class="notification-item__icon" [ngClass]="iconColorClass">
        <i [class]="iconClass"></i>
      </div>

      <div class="notification-item__body">
        <div class="notification-item__header">
          <span class="notification-item__heading">{{ heading }}</span>
          <span class="notification-item__time">{{ relativeTime }}</span>
        </div>
        <p class="notification-item__text">{{ bodyText }}</p>
      </div>
    </div>
  `,
  styles: [`
    .notification-item {
      display: flex;
      gap: var(--space-3, 12px);
      align-items: flex-start;
      background: var(--bg-secondary);
      border-radius: var(--radius-md);
      padding: var(--space-4) var(--space-5);
      min-height: 64px;
      border-left: 3px solid transparent;
    }
    .notification-item.is-unread { border-left-color: var(--border-accent, var(--accent-primary)); }
    .notification-item__icon { flex-shrink: 0; padding-top: 2px; }
    .notification-item__body { flex: 1; min-width: 0; }
    .notification-item__header { display: flex; justify-content: space-between; align-items: baseline; gap: var(--space-2); }
    .notification-item__heading { font-size: var(--text-base); font-family: var(--font-heading); font-weight: 600; }
    .notification-item__time { font-size: var(--text-xs); font-family: var(--font-mono); color: var(--text-muted); white-space: nowrap; flex-shrink: 0; }
    .notification-item__text { font-size: var(--text-sm); line-height: var(--leading-body); color: var(--text-secondary); margin-top: var(--space-1); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
    .icon-primary { color: var(--accent-primary); }
    .icon-info { color: var(--accent-info); }
    .icon-secondary { color: var(--accent-secondary); }
    .icon-warning { color: var(--accent-warning); }
    .icon-muted { color: var(--text-muted); }
  `],
})
export class NotificationItemComponent {
  @Input({ required: true }) item!: NotificationItem;

  get heading(): string {
    switch (this.item.type) {
      case 'lesson.started': return 'Пара началась';
      case 'lesson.cancelled': return 'Пара отменена';
      case 'homework.published': return 'Новое задание';
      case 'homework.updated': return 'Задание обновлено';
      case 'attendance.marked': return 'Посещаемость подтверждена';
      default: return 'Уведомление';
    }
  }

  get iconClass(): string {
    switch (this.item.type) {
      case 'lesson.started': return 'ph-play-circle ph-fill';
      case 'lesson.cancelled': return 'ph-x-circle ph-fill';
      case 'homework.published': return 'ph-notebook ph-fill';
      case 'homework.updated': return 'ph-pencil-simple ph-fill';
      case 'attendance.marked': return 'ph-check-circle ph-fill';
      default: return 'ph-bell';
    }
  }

  get iconColorClass(): string {
    switch (this.item.type) {
      case 'lesson.started': return 'icon-primary';
      case 'lesson.cancelled': return 'icon-info';
      case 'homework.published': return 'icon-secondary';
      case 'homework.updated': return 'icon-warning';
      case 'attendance.marked': return 'icon-primary';
      default: return 'icon-muted';
    }
  }

  get bodyText(): string {
    const p = this.item.payload;
    const subjectName = (p['subject_name'] ?? p['subjectName'] ?? 'Пара') as string;
    const title = (p['title'] ?? '') as string;
    switch (this.item.type) {
      case 'lesson.started': return `${subjectName} — отметьтесь!`;
      case 'lesson.cancelled': return `${subjectName} — пара отменена`;
      case 'homework.published': return `Новое ДЗ по ${subjectName}: ${title}`;
      case 'homework.updated': return `ДЗ по ${subjectName} обновлено: ${title}`;
      case 'attendance.marked': return '';
      default: return '';
    }
  }

  get relativeTime(): string {
    const diff = Date.now() - this.item.receivedAt.getTime();
    const minutes = Math.floor(diff / 60_000);
    if (minutes < 1) return 'только что';
    if (minutes < 60) return `${minutes} мин назад`;
    const hours = Math.floor(minutes / 60);
    if (hours < 24) return `${hours} ч назад`;
    return new Date(this.item.receivedAt).toLocaleDateString('ru-RU', { day: 'numeric', month: 'short' });
  }
}
