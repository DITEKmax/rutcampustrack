import { ChangeDetectionStrategy, Component } from '@angular/core';
import { trigger, transition, style, animate } from '@angular/animations';

@Component({
  selector: 'app-headman-late-checkin',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [],
  animations: [
    trigger('routeFade', [
      transition(':enter', [
        style({ opacity: 0, transform: 'translateY(8px)' }),
        animate('200ms cubic-bezier(0.16, 1, 0.3, 1)', style({ opacity: 1, transform: 'translateY(0)' })),
      ]),
    ]),
  ],
  host: { '[@routeFade]': '' },
  template: `
    <div class="page-header">
      <h1>Запросы отметки</h1>
      <span class="page-header__eyebrow">Староста</span>
    </div>
    <div class="page-card">
      <div class="page-empty" role="status" aria-live="polite">
        <div class="page-empty__icon"><i class="ph ph-clock-countdown"></i></div>
        <p class="page-empty__title">Нет активных запросов</p>
        <p class="page-empty__text">Функция находится в разработке. Заявки появятся здесь автоматически.</p>
      </div>
    </div>
  `,
})
export class HeadmanLateCheckinComponent {}
