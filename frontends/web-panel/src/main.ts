// sockjs-client / stompjs ожидают переменную `global` из Node-окружения.
// В браузере её нет — без этого падает с ReferenceError на загрузке STOMP-чанка.
(window as unknown as { global: typeof globalThis }).global = window as unknown as typeof globalThis;

// Auto-recover from stale-chunk errors after a prod deploy: when a user keeps
// an old index.html open and lazy-loads a chunk whose hash no longer exists,
// Angular throws ChunkLoadError. Reload once to pick up the new index.html +
// fresh chunks. One reload per tab to avoid infinite loops if the error is
// actually something else (bad deploy, offline, etc.) — sessionStorage key
// clears on tab close.
const RELOADED_KEY = 'rct.chunk-reload.v1';
function recoverFromChunkError(err: unknown): boolean {
  const msg = err instanceof Error ? `${err.name} ${err.message}` : String(err);
  const isChunkError =
    /ChunkLoadError/i.test(msg) ||
    /Loading chunk [\w-]+ failed/i.test(msg) ||
    /Failed to fetch dynamically imported module/i.test(msg);
  if (!isChunkError) return false;
  if (sessionStorage.getItem(RELOADED_KEY)) return false;
  sessionStorage.setItem(RELOADED_KEY, '1');
  window.location.reload();
  return true;
}
window.addEventListener('error', (event) => {
  recoverFromChunkError(event.error ?? event.message);
});
window.addEventListener('unhandledrejection', (event) => {
  recoverFromChunkError(event.reason);
});

import { bootstrapApplication } from '@angular/platform-browser';
import { appConfig } from './app/app.config';
import { AppComponent } from './app/app.component';

bootstrapApplication(AppComponent, appConfig)
  .catch((err) => {
    if (recoverFromChunkError(err)) return;
    console.error(err);
  });
