import type { BrowserPushPort, PushBinding } from './push-controller'

const timeoutMs = 10_000
export function createBrowserPushPort(enabled: boolean, base: string): BrowserPushPort {
  const supported = (): boolean => enabled && window.isSecureContext && 'Notification' in window
    && 'PushManager' in window && 'serviceWorker' in navigator
  async function registration(): Promise<ServiceWorkerRegistration> {
    const result = await navigator.serviceWorker.getRegistration(new URL(base, location.href).href)
    if (!result?.active) throw new Error('Приложение ещё не готово к Web Push. Обнови страницу и повтори.')
    return result
  }
  async function message(type: string, binding?: PushBinding | null): Promise<PushBinding | null> {
    const reg = await registration()
    return new Promise((resolve, reject) => {
      const channel = new MessageChannel()
      const timer = window.setTimeout(() => { channel.port1.close(); reject(new Error('Приложение не подтвердило настройки Web Push.')) }, timeoutMs)
      channel.port1.onmessage = (event: MessageEvent<{ ok: boolean; binding?: PushBinding | null }>) => {
        clearTimeout(timer)
        channel.port1.close()
        if (event.data.ok) resolve(event.data.binding ?? null)
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
    readBinding: () => message('RCT_PUSH_READ'), bind: async (binding) => { await message('RCT_PUSH_BIND', binding) },
  }
}
