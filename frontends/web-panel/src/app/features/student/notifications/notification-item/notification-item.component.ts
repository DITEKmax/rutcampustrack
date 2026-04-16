import { ChangeDetectionStrategy, Component, Input, OnChanges, SimpleChanges, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Observable, of } from 'rxjs';
import type { NotificationItem } from '../../shared/student-schedule.types';
import { SubjectCacheService } from '../../shared/subject-cache.service';

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
        <p class="notification-item__text">{{ getBodyText(subjectName$ | async) }}</p>
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
    .notification-item__text { font-size: var(--text-sm); line-height: var(--leading-body); color: var(--text-secondary); margin-top: var(--space-1); white-space: normal; }
    .icon-primary { color: var(--accent-primary); }
    .icon-info { color: var(--accent-info); }
    .icon-secondary { color: var(--accent-secondary); }
    .icon-warning { color: var(--accent-warning); }
    .icon-danger { color: var(--accent-danger); }
    .icon-muted { color: var(--text-muted); }
  `],
})
export class NotificationItemComponent implements OnChanges {
  private readonly subjectCache = inject(SubjectCacheService);

  @Input({ required: true }) item!: NotificationItem;

  subjectName$: Observable<string> = of('');

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['item']) {
      this.subjectName$ = this.resolveSubjectName$(this.item);
    }
  }

  private resolveSubjectName$(item: NotificationItem): Observable<string> {
    const p = item.payload ?? {};
    const inline = (p['subject_name'] ?? p['subjectName']) as string | undefined;
    if (typeof inline === 'string' && inline.trim() !== '') {
      return of(inline);
    }
    const rawId = p['subject_id'] ?? p['subjectId'];
    const subjectId = typeof rawId === 'number' ? rawId : Number(rawId);
    if (!Number.isFinite(subjectId) || subjectId <= 0) {
      return of('');
    }
    return this.subjectCache.getName(subjectId);
  }

  get heading(): string {
    switch (this.item.type) {
      case 'lesson.started': return 'Пара началась';
      case 'lesson.cancelled': return 'Пара отменена';
      case 'homework.published': return 'Новое задание';
      case 'homework.updated': return 'Задание обновлено';
      case 'attendance.marked': return 'Посещаемость подтверждена';
      case 'late_checkin.decided': return this.isApproved
        ? 'Отметка подтверждена'
        : 'Отметка отклонена';
      case 'excuse.decided': return this.isApproved
        ? 'Уваж. причина одобрена'
        : 'Уваж. причина отклонена';
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
      case 'late_checkin.decided':
      case 'excuse.decided':
        return this.isApproved ? 'ph-check-circle ph-fill' : 'ph-x-circle ph-fill';
      default: return 'ph-bell';
    }
  }

  get iconColorClass(): string {
    switch (this.item.type) {
      case 'lesson.started': return 'icon-secondary';
      case 'lesson.cancelled': return 'icon-danger';
      case 'homework.published': return 'icon-info';
      case 'homework.updated': return 'icon-warning';
      case 'attendance.marked': return 'icon-primary';
      case 'late_checkin.decided':
      case 'excuse.decided':
        return this.isApproved ? 'icon-primary' : 'icon-warning';
      default: return 'icon-muted';
    }
  }

  getBodyText(resolvedSubjectName: string | null): string {
    const p = this.item.payload ?? {};
    const subjectName = (resolvedSubjectName && resolvedSubjectName.trim()) || 'пары';
    const title = (p['title'] ?? '') as string;
    const lessonNumber = p['lesson_number'] ?? p['lessonNumber'];
    const lessonDate = (p['lesson_date'] ?? p['lessonDate'] ?? '') as string;
    const dateText = lessonDate ? this.formatRuDate(lessonDate) : '';
    const comment = (p['decision_comment'] ?? p['decisionComment'] ?? '') as string;
    switch (this.item.type) {
      case 'lesson.started':
        return `${this.capitalize(subjectName)} началась — не забудьте отметиться.`;
      case 'lesson.cancelled': {
        const d = dateText ? ` ${dateText}` : '';
        return `Пара «${subjectName}»${d} отменена.`;
      }
      case 'homework.published':
        return title
          ? `Задание по ${subjectName}: «${title}».`
          : `Опубликовано новое задание по ${subjectName}.`;
      case 'homework.updated':
        return title
          ? `Задание по ${subjectName} обновлено: «${title}».`
          : `Задание по ${subjectName} обновлено.`;
      case 'attendance.marked': {
        const num = lessonNumber ? ` №${lessonNumber}` : '';
        return `Вы отмечены на паре${num}.`;
      }
      case 'late_checkin.decided': {
        const verdict = this.isApproved ? 'подтвердил вашу отметку' : 'отклонил запрос на отметку';
        const lessonPart = lessonNumber ? ` №${lessonNumber}` : '';
        const datePart = dateText ? ` (${dateText})` : '';
        return `Староста ${verdict} по «${subjectName}»${lessonPart}${datePart}.`;
      }
      case 'excuse.decided': {
        const verdict = this.isApproved ? 'одобрил' : 'отклонил';
        const tail = comment ? ` Комментарий: «${comment}».` : '';
        return `Староста ${verdict} заявку об уважительной причине.${tail}`;
      }
      default: return '';
    }
  }

  private capitalize(value: string): string {
    if (!value) return value;
    return value.charAt(0).toUpperCase() + value.slice(1);
  }

  private formatRuDate(iso: string): string {
    // Accepts both "YYYY-MM-DD" and full ISO. Falls back to raw string on parse error.
    const parsed = new Date(iso.length === 10 ? `${iso}T00:00:00` : iso);
    if (Number.isNaN(parsed.getTime())) return iso;
    return parsed.toLocaleDateString('ru-RU', { day: 'numeric', month: 'short' });
  }

  private get isApproved(): boolean {
    const raw = this.item.payload?.['status'];
    return typeof raw === 'string' && raw.toLowerCase() === 'approved';
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
