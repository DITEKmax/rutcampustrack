import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { trigger, transition, style, animate } from '@angular/animations';
import { catchError, of } from 'rxjs';
import { HeadmanApiService } from '../shared/headman-api.service';

@Component({
  selector: 'app-headman-excuses',
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
      <h1>Пропуски</h1>
      <span class="page-header__eyebrow">Староста</span>
    </div>
    <div class="page-card">
      <div class="page-empty" role="status" aria-live="polite">
        <div class="page-empty__icon"><i class="ph ph-file-text"></i></div>
        <p class="page-empty__title">Нет активных заявок</p>
        <p class="page-empty__text">Функция находится в разработке. Заявки появятся здесь автоматически.</p>
      </div>
    </div>
  `,
})
export class HeadmanExcusesComponent implements OnInit {
  private readonly headmanApi = inject(HeadmanApiService);
  excusesData = signal<any>(null);

  ngOnInit(): void {
    this.headmanApi.getPendingExcuses()
      .pipe(catchError(() => of(null)))
      .subscribe(data => this.excusesData.set(data));
  }
}
