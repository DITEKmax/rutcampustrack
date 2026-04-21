import { ApplicationConfig, provideAppInitializer, inject, provideZoneChangeDetection } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { provideNativeDateAdapter } from '@angular/material/core';
import { routes } from './app.routes';
import { authInterceptor } from './core/auth/auth.interceptor';
import { problemDetailsInterceptor } from './core/errors/problem-details.interceptor';
import { AuthService } from './core/auth/auth.service';
import {
  Chart,
  BarController,
  LineController,
  CategoryScale,
  LinearScale,
  BarElement,
  LineElement,
  PointElement,
  Filler,
  Legend,
  Tooltip,
} from 'chart.js';

Chart.register(
  BarController,
  LineController,
  CategoryScale,
  LinearScale,
  BarElement,
  LineElement,
  PointElement,
  Filler,
  Legend,
  Tooltip,
);

export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(routes),
    // M07 G4: problemDetailsInterceptor стоит после authInterceptor —
    // 401 обрабатывается auth'ом (silent refresh), сюда попадают только
    // «настоящие» server errors для snackbar.
    provideHttpClient(withInterceptors([authInterceptor, problemDetailsInterceptor])),
    provideAnimationsAsync(),
    provideNativeDateAdapter(),
    // M03b Группа 7: cookie-based bootstrap — попытка восстановить сессию
    // через /auth/refresh (HttpOnly cookie) до того как guards начнут работать.
    provideAppInitializer(() => inject(AuthService).bootstrap()),
  ],
};
