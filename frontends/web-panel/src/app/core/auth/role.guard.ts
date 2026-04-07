import { CanActivateFn } from '@angular/router';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from './auth.service';

export const roleGuard =
  (allowedRoles: string[]): CanActivateFn =>
  () => {
    const auth = inject(AuthService);
    const router = inject(Router);

    const user = auth.currentUser();
    if (!user) return router.createUrlTree(['/login']);

    if (!allowedRoles.includes(user.role)) {
      const dashboard = user.role === 'ADMIN' ? '/admin/dashboard' : '/teacher/dashboard';
      return router.createUrlTree([dashboard]);
    }

    return true;
  };
