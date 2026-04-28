import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { trigger, transition, style, animate } from '@angular/animations';
import {
  CATEGORY_LABELS,
  NotificationCategory,
  NotificationPrefsService,
} from '../../../core/notifications/notification-prefs.service';

/**
 * Страница настроек уведомлений студента/старосты в web-panel.
 *
 * Симметрия с PWA `NotificationSettingsPage.tsx`: 6 категорий + квайт-час.
 * Хранение — localStorage (см. {@link NotificationPrefsService}). Серверу
 * ничего не отправляется: фильтрация локальная, поэтому изменения
 * применяются мгновенно и не зависят от рассыльной стороны.
 */
@Component({
  selector: 'app-notification-settings',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, FormsModule, RouterLink],
  animations: [
    trigger('routeFade', [
      transition(':enter', [
        style({ opacity: 0, transform: 'translateY(8px)' }),
        animate('200ms cubic-bezier(0.16, 1, 0.3, 1)', style({ opacity: 1, transform: 'translateY(0)' })),
      ]),
    ]),
  ],
  template: `
    <div class="page-stack" [@routeFade]>
      <header class="page-header">
        <a class="back-link" routerLink="/student/notifications" aria-label="Назад">
          <i class="ph-arrow-left ph-bold"></i>
          <span>К списку</span>
        </a>
        <h1 class="page-header__title">Настройки уведомлений</h1>
        <p class="page-header__sub">Что присылать, и когда молчать.</p>
      </header>

      <section class="card">
        <h2 class="card__title">Категории</h2>
        <p class="card__hint">
          Выключенные категории не показываются в bell-меню, в списке уведомлений и не подсвечиваются как непрочитанные.
        </p>
        <ul class="toggle-list" role="list">
          @for (cat of categories; track cat) {
            <li class="toggle-row">
              <label>
                <span class="toggle-row__label">{{ labels[cat] }}</span>
                <input
                  type="checkbox"
                  [checked]="enabled(cat)"
                  (change)="prefs.toggleCategory(cat)" />
              </label>
            </li>
          }
        </ul>
      </section>

      <section class="card">
        <h2 class="card__title">Тихий режим</h2>
        <p class="card__hint">
          В этом окне native-баннеры не показываются, но события всё равно сохраняются в историю.
        </p>
        <label class="toggle-row toggle-row--standalone">
          <span class="toggle-row__label">Включён</span>
          <input
            type="checkbox"
            [checked]="quietEnabled()"
            (change)="toggleQuiet()" />
        </label>
        @if (quietEnabled()) {
          <div class="quiet-times">
            <label>
              <span>С</span>
              <input type="time" [ngModel]="quietFrom()" (ngModelChange)="setQuietFrom($event)" />
            </label>
            <label>
              <span>До</span>
              <input type="time" [ngModel]="quietTo()" (ngModelChange)="setQuietTo($event)" />
            </label>
          </div>
        }
      </section>

      <section class="card card--reset">
        <button type="button" class="reset-btn" (click)="prefs.resetAll()">
          Сбросить к умолчаниям
        </button>
      </section>
    </div>
  `,
  styles: [
    `
      .page-stack { display: flex; flex-direction: column; gap: var(--space-5); padding: var(--space-5); max-width: 720px; margin: 0 auto; }
      .page-header { display: flex; flex-direction: column; gap: var(--space-2); }
      .page-header__title { font-family: var(--font-heading); font-size: var(--text-2xl); margin: 0; }
      .page-header__sub { color: var(--text-muted); font-size: var(--text-sm); margin: 0; }
      .back-link { color: var(--text-muted); display: inline-flex; gap: var(--space-2); align-items: center; text-decoration: none; font-size: var(--text-sm); width: fit-content; }
      .back-link:hover { color: var(--text-primary); }
      .card { background: var(--bg-secondary); border-radius: var(--radius-lg); padding: var(--space-5); display: flex; flex-direction: column; gap: var(--space-3); }
      .card__title { margin: 0; font-family: var(--font-heading); font-size: var(--text-lg); }
      .card__hint { margin: 0; color: var(--text-muted); font-size: var(--text-sm); }
      .toggle-list { list-style: none; padding: 0; margin: 0; display: flex; flex-direction: column; gap: var(--space-2); }
      .toggle-row label { display: flex; align-items: center; justify-content: space-between; padding: var(--space-3) var(--space-4); border-radius: var(--radius-md); background: var(--bg-primary); cursor: pointer; }
      .toggle-row__label { font-size: var(--text-sm); }
      .toggle-row--standalone { padding: 0; }
      .quiet-times { display: flex; gap: var(--space-3); }
      .quiet-times label { display: inline-flex; flex-direction: column; gap: var(--space-1); font-size: var(--text-xs); color: var(--text-muted); }
      .quiet-times input[type='time'] { padding: var(--space-2) var(--space-3); border-radius: var(--radius-md); background: var(--bg-primary); border: 1px solid var(--border-subtle); color: var(--text-primary); }
      .card--reset { align-items: flex-start; }
      .reset-btn { background: transparent; border: 1px solid var(--border-default); color: var(--text-muted); padding: var(--space-2) var(--space-4); border-radius: var(--radius-md); cursor: pointer; }
      .reset-btn:hover { color: var(--accent-warning); border-color: var(--accent-warning); }
    `,
  ],
})
export class NotificationSettingsComponent {
  protected readonly prefs = inject(NotificationPrefsService);
  private readonly router = inject(Router);

  readonly labels = CATEGORY_LABELS;
  readonly categories: NotificationCategory[] = [
    'lessons',
    'reminders',
    'homework',
    'tickets',
    'schedule',
    'group',
  ];

  enabled(c: NotificationCategory): boolean {
    return this.prefs.prefs().categories[c];
  }

  quietEnabled = computed(() => this.prefs.prefs().quietHours !== null);
  quietFrom = computed(() => this.prefs.prefs().quietHours?.from ?? '22:00');
  quietTo = computed(() => this.prefs.prefs().quietHours?.to ?? '08:00');

  toggleQuiet(): void {
    if (this.quietEnabled()) {
      this.prefs.setQuietHours(null);
    } else {
      this.prefs.setQuietHours({ from: '22:00', to: '08:00' });
    }
  }

  setQuietFrom(v: string): void {
    const curr = this.prefs.prefs().quietHours ?? { from: v, to: '08:00' };
    this.prefs.setQuietHours({ ...curr, from: v });
  }

  setQuietTo(v: string): void {
    const curr = this.prefs.prefs().quietHours ?? { from: '22:00', to: v };
    this.prefs.setQuietHours({ ...curr, to: v });
  }
}
