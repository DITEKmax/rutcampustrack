// sockjs-client / stompjs ожидают переменную `global` из Node-окружения.
// В браузере её нет — без этого падает с ReferenceError на загрузке STOMP-чанка.
(window as unknown as { global: typeof globalThis }).global = window as unknown as typeof globalThis;

import { bootstrapApplication } from '@angular/platform-browser';
import { appConfig } from './app/app.config';
import { AppComponent } from './app/app.component';

bootstrapApplication(AppComponent, appConfig)
  .catch((err) => console.error(err));
