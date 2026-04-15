import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';

/**
 * 404 page shown inside the authenticated shell for unknown routes.
 *
 * Previously the root router had `{ path: '**', redirectTo: 'login' }`, which
 * kicked signed-in users with a typo URL back through the guest guard and
 * bounced them silently to their dashboard — hiding the broken link. This
 * page surfaces the mistake and offers a single recovery CTA to the user's
 * role-appropriate dashboard.
 */
@Component({
  selector: 'app-not-found',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink],
  template: `
    <section class="page-stack" style="max-width: 640px; margin: 0 auto; padding-top: var(--space-7);">
      <div class="page-card">
        <div class="page-empty">
          <div class="page-empty__icon" aria-hidden="true">
            <i class="ph-duotone ph-compass"></i>
          </div>
          <h1 class="page-empty__title">Страница не найдена</h1>
          <p class="page-empty__text">
            Адрес, по которому вы перешли, не существует или был перемещён.
            Проверьте ссылку или вернитесь на главную.
          </p>
          <a class="btn-brand" [routerLink]="dashboardLink()" style="margin-top: var(--space-4);">
            <i class="ph ph-arrow-left" aria-hidden="true"></i>
            На главную
          </a>
        </div>
      </div>
    </section>
  `,
})
export class NotFoundComponent {
  private readonly auth = inject(AuthService);

  readonly dashboardLink = computed(() => {
    const user = this.auth.currentUser();
    return user ? this.auth.resolveDashboardFor(user) : '/login';
  });
}
