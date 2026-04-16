import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  HostListener,
  computed,
  inject,
  signal,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { NotificationCenterService, NotificationRecord } from './notification-center.service';
import { AuthService } from '../auth/auth.service';

/**
 * Колокольчик в шапке с выпадающим списком последних уведомлений.
 *
 * Подписан на {@link NotificationCenterService}, поэтому показывает счётчик и
 * список в любом разделе приложения (включая страницы, где раньше STOMP вообще
 * не подключался). Клик по уведомлению ведёт на соответствующую страницу:
 *   - late_checkin.requested / excuse.requested → /headman/* списки
 *   - late_checkin.decided / excuse.decided    → /student/excuses
 *                                              / /student/late-checkin
 *   - остальное                                → /student/notifications
 *
 * «Отметить всё прочитанным» сбрасывает бейдж, но сохраняет историю, чтобы
 * пользователь мог вернуться к деталям.
 */
@Component({
  selector: 'app-notification-bell',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule],
  template: `
    <div class="nbell" [class.nbell--open]="open()">
      <button
        type="button"
        class="nbell__button"
        [attr.aria-expanded]="open()"
        [attr.aria-label]="
          unreadCount() > 0
            ? 'Уведомления, ' + unreadCount() + ' непрочитанных'
            : 'Уведомления'
        "
        (click)="toggle()"
      >
        <i class="ph ph-bell" aria-hidden="true"></i>
        @if (unreadCount() > 0) {
          <span class="nbell__badge">{{ unreadCount() > 9 ? '9+' : unreadCount() }}</span>
        }
      </button>

      @if (open()) {
        <div class="nbell__panel" role="menu">
          <div class="nbell__panel-head">
            <span>Уведомления</span>
            @if (unreadCount() > 0) {
              <button type="button" class="nbell__link" (click)="markAllRead()">
                Отметить все прочитанными
              </button>
            }
          </div>

          @if (items().length === 0) {
            <p class="nbell__empty">Пока нет уведомлений.</p>
          } @else {
            <ul class="nbell__list">
              @for (item of items().slice(0, 15); track item.id) {
                <li>
                  <button
                    type="button"
                    class="nbell__item"
                    [class.is-unread]="!item.read"
                    (click)="openItem(item)"
                  >
                    <div class="nbell__item-head">
                      <strong>{{ heading(item) }}</strong>
                      <span class="nbell__item-time">{{ relativeTime(item) }}</span>
                    </div>
                    <p class="nbell__item-text">{{ body(item) }}</p>
                  </button>
                </li>
              }
            </ul>
          }

          @if (items().length > 0) {
            <div class="nbell__panel-foot">
              <button type="button" class="nbell__link" (click)="openFullList()">
                Все уведомления
              </button>
            </div>
          }
        </div>
      }
    </div>
  `,
  styles: [
    `
      .nbell { position: relative; }
      .nbell__button {
        position: relative;
        display: inline-flex;
        align-items: center;
        justify-content: center;
        width: 40px; height: 40px;
        border-radius: var(--radius-full);
        border: 1px solid var(--border-subtle);
        background: var(--bg-secondary);
        color: var(--text-secondary);
        cursor: pointer;
        transition: border-color var(--duration-base) var(--ease-out),
                    color var(--duration-base) var(--ease-out);
      }
      .nbell__button:hover,
      .nbell--open .nbell__button {
        border-color: var(--border-default);
        color: var(--text-primary);
      }
      .nbell__button i { font-size: 18px; }
      .nbell__badge {
        position: absolute;
        top: -2px; right: -2px;
        min-width: 18px; height: 18px;
        padding: 0 5px;
        border-radius: 9px;
        background: var(--accent-danger);
        color: #fff;
        font: 600 10px/18px var(--font-mono);
        text-align: center;
      }
      .nbell__panel {
        position: absolute;
        top: calc(100% + 8px); right: 0;
        width: 360px;
        max-height: 480px;
        display: flex; flex-direction: column;
        background: var(--bg-surface);
        border: 1px solid var(--border-default);
        border-radius: var(--radius-lg);
        box-shadow: var(--shadow-lg, 0 8px 32px rgba(0, 0, 0, 0.24));
        z-index: 50;
        overflow: hidden;
      }
      .nbell__panel-head,
      .nbell__panel-foot {
        display: flex; justify-content: space-between; align-items: center;
        padding: var(--space-3) var(--space-4);
        border-bottom: 1px solid var(--border-subtle);
        font: 600 0.875rem/1.2 var(--font-heading);
      }
      .nbell__panel-foot {
        border-top: 1px solid var(--border-subtle);
        border-bottom: none;
      }
      .nbell__link {
        background: none; border: 0; padding: 0;
        color: var(--accent-primary);
        font: 500 0.8125rem var(--font-body);
        cursor: pointer;
      }
      .nbell__link:hover { text-decoration: underline; }
      .nbell__empty {
        padding: var(--space-5) var(--space-4);
        text-align: center;
        color: var(--text-muted);
        font-size: 0.875rem;
        margin: 0;
      }
      .nbell__list {
        list-style: none; padding: 0; margin: 0;
        overflow-y: auto;
        flex: 1;
      }
      .nbell__item {
        display: block;
        width: 100%;
        text-align: left;
        background: none; border: 0;
        padding: var(--space-3) var(--space-4);
        border-bottom: 1px solid var(--border-subtle);
        border-left: 3px solid transparent;
        cursor: pointer;
        transition: background var(--duration-base) var(--ease-out);
      }
      .nbell__item:hover { background: var(--bg-secondary); }
      .nbell__item.is-unread { border-left-color: var(--accent-primary); }
      .nbell__item-head {
        display: flex; justify-content: space-between; align-items: baseline;
        gap: var(--space-2);
        margin-bottom: 2px;
      }
      .nbell__item-head strong {
        font: 600 0.875rem var(--font-heading);
        color: var(--text-primary);
      }
      .nbell__item-time {
        font: 500 0.6875rem var(--font-mono);
        color: var(--text-muted);
        white-space: nowrap;
      }
      .nbell__item-text {
        margin: 0;
        font-size: 0.8125rem;
        color: var(--text-secondary);
        line-height: 1.4;
        display: -webkit-box;
        -webkit-line-clamp: 2;
        -webkit-box-orient: vertical;
        overflow: hidden;
      }
    `,
  ],
})
export class NotificationBellComponent {
  private readonly center = inject(NotificationCenterService);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly host = inject(ElementRef<HTMLElement>);

  readonly items = this.center.items;
  readonly unreadCount = this.center.unreadCount;
  readonly open = signal(false);

  readonly isHeadman = computed(() => this.auth.currentUser()?.isHeadman === true);

  toggle(): void {
    this.open.update(v => !v);
  }

  markAllRead(): void {
    this.center.markAllRead();
  }

  openFullList(): void {
    this.open.set(false);
    // Отдельной страницы «все уведомления» для headman пока нет — тут оба случая
    // корректно попадают на /student/notifications (маршрут доступен всем STUDENT).
    this.router.navigate(['/student/notifications']);
  }

  openItem(item: NotificationRecord): void {
    this.open.set(false);
    this.center.markAllRead(); // упрощаем: помечаем всё, чтобы не плодить «полупрочитанных»
    const route = this.routeFor(item);
    this.router.navigateByUrl(route);
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent): void {
    if (!this.open()) return;
    if (!this.host.nativeElement.contains(event.target as Node)) {
      this.open.set(false);
    }
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    if (this.open()) this.open.set(false);
  }

  // -------- Форматирование (локально — пусть bell не зависит от student-only компонента) --------

  heading(item: NotificationRecord): string {
    switch (item.type) {
      case 'lesson.started': return 'Пара началась';
      case 'lesson.cancelled': return 'Пара отменена';
      case 'homework.published': return 'Новое задание';
      case 'homework.updated': return 'Задание обновлено';
      case 'attendance.marked': return 'Посещаемость подтверждена';
      case 'late_checkin.requested': return 'Запрос на отметку';
      case 'late_checkin.decided':
        return this.isApproved(item) ? 'Отметка подтверждена' : 'Отметка отклонена';
      case 'excuse.requested': return 'Новый тикет об у.п.';
      case 'excuse.decided':
        return this.isApproved(item) ? 'Уваж. причина одобрена' : 'Уваж. причина отклонена';
      default: return 'Уведомление';
    }
  }

  body(item: NotificationRecord): string {
    const p = item.payload ?? {};
    const subjectName = (p['subject_name'] ?? '') as string;
    const student = (p['student_name'] ?? '') as string;
    const lessonNumber = p['lesson_number'] ?? p['lessonNumber'];
    const lessonDate = (p['lesson_date'] ?? '') as string;
    const dateText = lessonDate ? this.formatRuDate(lessonDate) : '';
    const comment = (p['decision_comment'] ?? '') as string;

    switch (item.type) {
      case 'lesson.started': return `${subjectName || 'Пара'} началась — отметьтесь.`;
      case 'lesson.cancelled': return `Пара${subjectName ? ' «' + subjectName + '»' : ''}${dateText ? ' ' + dateText : ''} отменена.`;
      case 'homework.published': return subjectName ? `Задание по ${subjectName}.` : 'Опубликовано новое задание.';
      case 'homework.updated': return subjectName ? `Задание по ${subjectName} обновлено.` : 'Задание обновлено.';
      case 'attendance.marked': {
        const n = lessonNumber ? ` №${lessonNumber}` : '';
        return `Вы отмечены на паре${n}.`;
      }
      case 'late_checkin.requested': {
        const n = lessonNumber ? `№${lessonNumber}` : '';
        const subj = subjectName ? ` «${subjectName}»` : '';
        const d = dateText ? ` (${dateText})` : '';
        return `${student || 'Студент'} просит отметить ${n}${subj}${d}.`.replace(/\s+/g, ' ').trim();
      }
      case 'late_checkin.decided': {
        const v = this.isApproved(item) ? 'подтверждена' : 'отклонена';
        return `Ваша отметка ${v}.`;
      }
      case 'excuse.requested': {
        const type = (p['excuse_type'] ?? '') as string;
        return `${student || 'Студент'} подал тикет об у.п.${type ? ' (' + type + ')' : ''}.`;
      }
      case 'excuse.decided': {
        const v = this.isApproved(item) ? 'одобрена' : 'отклонена';
        const tail = comment ? ` Комментарий: «${comment}».` : '';
        return `Ваша заявка об уважительной причине ${v}.${tail}`;
      }
      default: return '';
    }
  }

  relativeTime(item: NotificationRecord): string {
    const parsed = new Date(item.receivedAt);
    const diff = Date.now() - parsed.getTime();
    const minutes = Math.floor(diff / 60_000);
    if (minutes < 1) return 'только что';
    if (minutes < 60) return `${minutes} мин`;
    const hours = Math.floor(minutes / 60);
    if (hours < 24) return `${hours} ч`;
    return parsed.toLocaleDateString('ru-RU', { day: 'numeric', month: 'short' });
  }

  private isApproved(item: NotificationRecord): boolean {
    const raw = item.payload?.['status'];
    return typeof raw === 'string' && raw.toLowerCase() === 'approved';
  }

  private formatRuDate(iso: string): string {
    const parsed = new Date(iso.length === 10 ? `${iso}T00:00:00` : iso);
    if (Number.isNaN(parsed.getTime())) return iso;
    return parsed.toLocaleDateString('ru-RU', { day: 'numeric', month: 'short' });
  }

  private routeFor(item: NotificationRecord): string {
    const user = this.auth.currentUser();
    const isHeadman = user?.isHeadman === true;
    switch (item.type) {
      case 'late_checkin.requested':
        return isHeadman ? '/headman/late-checkin' : '/student/notifications';
      case 'excuse.requested':
        return isHeadman ? '/headman/excuses' : '/student/notifications';
      case 'late_checkin.decided':
        return '/student/late-checkin';
      case 'excuse.decided':
        return '/student/excuses';
      default:
        return '/student/notifications';
    }
  }
}
