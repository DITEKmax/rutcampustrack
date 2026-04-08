import { CanActivateFn } from '@angular/router';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from './auth.service';

export const headmanGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);

  const user = auth.currentUser();
  if (!user) return router.createUrlTree(['/login']);
  if (user.role === 'STUDENT' && user.isHeadman) return true;
  return router.createUrlTree([auth.resolveDashboardFor(user)]);
};
