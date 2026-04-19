import { inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { AuthApi } from './auth.api';

/**
 * M03b Группа 7: pre-connect helper для WebSocket. Запрашивает single-use
 * ticket (30s) через `POST /auth/ws-ticket` и строит URL вида
 * `/api/ws?ticket=<uuid>`. Использовать в webSocketFactory:
 *
 * ```ts
 * webSocketFactory: () => {
 *   const url = await buildWsUrl(authApi);
 *   return new SockJS(url);
 * }
 * ```
 *
 * @stomp/stompjs поддерживает async factory — каждый reconnect
 * запрашивает свежий ticket (by design — single-use).
 */
export async function buildWsUrl(authApi: AuthApi): Promise<string> {
  const { ticket } = await firstValueFrom(authApi.acquireWsTicket());
  return `/api/ws?ticket=${encodeURIComponent(ticket)}`;
}

/**
 * Angular-friendly обёртка: inject AuthApi внутри injection context'а
 * компонента/service'а.
 */
export function createWsUrlBuilder(): () => Promise<string> {
  const authApi = inject(AuthApi);
  return () => buildWsUrl(authApi);
}
