import { acquireWsTicketApi } from './api'

/**
 * M03b Группа 6: pre-connect helper для WebSocket. Запрашивает short-lived
 * (30s, single-use) ticket из auth-service и строит URL вида
 * `/api/ws?ticket=<uuid>`.
 *
 * Вызывать из `webSocketFactory` в @stomp/stompjs Client:
 *
 * ```ts
 * webSocketFactory: async () => new SockJS(await buildWsUrl())
 * ```
 *
 * SockJS и @stomp/stompjs поддерживают async factory. На каждый reconnect
 * ticket запрашивается заново — это by design (single-use).
 */
export async function buildWsUrl(): Promise<string> {
  const { ticket } = await acquireWsTicketApi()
  return `/api/ws?ticket=${encodeURIComponent(ticket)}`
}
