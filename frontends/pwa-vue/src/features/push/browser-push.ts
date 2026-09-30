import type { BrowserPushPort, PushBinding } from './push-controller'

const timeoutMs = 10_000
export function createBrowserPushPort(enabled: boolean, base: string): BrowserPushPort {
  const supported = (): boolean => enabled && window.isSecureContext && 'Notification' in window
    && 'PushManager' in window && 'serviceWorker' in navigator && 'locks' in navigator
  async function registration(): Promise<ServiceWorkerRegistration> {
    const result = await navigator.serviceWorker.getRegistration(new URL(base, location.href).href)
    if (!result?.active) throw new Error('Приложение ещё не готово к Web Push. Обнови страницу и повтори.')
    return result
  }
  async function message(type: string, binding?: PushBinding | null): Promise<{ binding: PushBinding | null; cleared: boolean }> {
    const reg = await registration()
    return new Promise((resolve, reject) => {
      const channel = new MessageChannel()
      const timer = window.setTimeout(() => { channel.port1.close(); reject(new Error('Приложение не подтвердило настройки Web Push.')) }, timeoutMs)
      channel.port1.onmessage = (event: MessageEvent<{ ok: boolean; binding?: PushBinding | null; cleared?: boolean }>) => {
        clearTimeout(timer)
        channel.port1.close()
        if (event.data.ok) resolve({ binding: event.data.binding ?? null, cleared: event.data.cleared === true })
        else reject(new Error('Не удалось сохранить настройки Web Push на устройстве.'))
      }
      reg.active!.postMessage({ type, binding }, [channel.port2])
    })
  }
  return {
    supported, permission: () => Notification.permission,
    requestPermission: () => Notification.requestPermission(),
    installationRequired: () => /iphone|ipad|ipod/i.test(navigator.userAgent)
      && !window.matchMedia('(display-mode: standalone)').matches
      && !(navigator as Navigator & { standalone?: boolean }).standalone,
    subscription: async () => (await registration()).pushManager.getSubscription(),
    createSubscription: async (key) => (await registration()).pushManager.subscribe({ userVisibleOnly: true, applicationServerKey: key }),
    fingerprint: async (endpoint) => Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256', new TextEncoder().encode(endpoint))), (byte) => byte.toString(16).padStart(2, '0')).join(''),
    readBinding: async () => (await message('RCT_PUSH_READ')).binding,
    bind: async (binding) => { await message('RCT_PUSH_BIND', binding) },
    clearBinding: async (binding) => (await message('RCT_PUSH_CLEAR', binding)).cleared,
    exclusive: async (operation) => { await navigator.locks.request('rct-pwa-push-owner-v1', { signal: AbortSignal.timeout(20_000) }, operation) },
  }
}
