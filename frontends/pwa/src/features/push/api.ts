import { apiClient } from '@/shared/lib/axios'

/**
 * notification-web VAPID key endpoint — не проходит через backend OpenAPI
 * (отдельный сервис). Ручной тип до M11 OpenAPI polish.
 */
interface VapidPublicKeyResponse {
  publicKey: string
}

export async function fetchVapidPublicKey(): Promise<string> {
  // GET /api/push/vapid-public-key returns EntityModel<VapidPublicKeyResponse>
  // HATEOAS wraps it: { publicKey: "...", _links: {...} }
  const { data } = await apiClient.get<VapidPublicKeyResponse>('/push/vapid-public-key')
  return data.publicKey
}

export async function subscribePush(subscription: PushSubscription): Promise<void> {
  // POST /api/push/subscribe expects { endpoint, keys: { p256dh, auth } }
  const json = subscription.toJSON()
  await apiClient.post('/push/subscribe', {
    endpoint: json.endpoint,
    keys: {
      p256dh: json.keys?.p256dh ?? '',
      auth: json.keys?.auth ?? '',
    },
  })
}

export async function unsubscribePush(endpoint: string): Promise<void> {
  // DELETE /api/push/subscribe expects body { endpoint }
  await apiClient.delete('/push/subscribe', { data: { endpoint } })
}
