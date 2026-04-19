/**
 * M03b Группа 6 (OWNER-ANSWERS 02-Q-frontend-security Часть Б):
 * выполняет полную очистку клиентского state при logout.
 *
 * Last-resort helper — должен вызываться ПОСЛЕ успешного backend logout
 * (который дерегистрирует refresh-token в Redis и чистит HttpOnly cookie).
 * Без backend'а helper просто чистит local storage — следующий пользователь
 * не увидит чужих данных даже если backend недоступен.
 *
 * Включает:
 * - localStorage + sessionStorage (вся история уведомлений, cached settings)
 * - Service Worker runtime кэши (headman-api-cache, etc.) — cross-user leak fix P0-4
 * - Push subscription unsubscribe + backend DELETE — P0-5
 *
 * SW precache НЕ чистится — он per-version, не содержит user-specific данных.
 */
export async function clearAllClientState(): Promise<void> {
  // 1) local + session storage
  try {
    localStorage.clear()
  } catch { /* storage may be disabled */ }
  try {
    sessionStorage.clear()
  } catch { /* storage may be disabled */ }

  // 2) Service Worker runtime caches (но не precache — он per-version, не user-specific)
  if (typeof caches !== 'undefined') {
    try {
      const keys = await caches.keys()
      await Promise.all(
        keys
          .filter((k) => k.startsWith('headman-api-cache') || k.includes('runtime'))
          .map((k) => caches.delete(k))
      )
    } catch { /* best-effort */ }
  }

  // 3) Push subscription — unsubscribe locally + best-effort tell backend
  if ('serviceWorker' in navigator && 'PushManager' in window) {
    try {
      const reg = await navigator.serviceWorker.ready
      const sub = await reg.pushManager.getSubscription()
      if (sub) {
        // Best-effort backend-delete. Endpoint path — notification-service push API.
        // Если backend недоступен — local unsubscribe всё равно сработает и следующий
        // пользователь просто пере-subscribe'нется.
        try {
          await fetch('/api/notifications/push/subscribe', {
            method: 'DELETE',
            credentials: 'include',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ endpoint: sub.endpoint }),
          })
        } catch { /* best-effort */ }
        await sub.unsubscribe()
      }
    } catch { /* best-effort */ }
  }
}
