import type { MobilePushPort, MobilePushState } from '../../../../mobile-core/src/shared/host'

export interface PushOwner { userId: string; generation: number; token: string }
export interface PushBinding { userId: string; fingerprint: string }
export interface BrowserPushPort {
  supported(): boolean
  permission(): NotificationPermission
  requestPermission(): Promise<NotificationPermission>
  installationRequired(): boolean
  subscription(): Promise<PushSubscription | null>
  createSubscription(key: Uint8Array<ArrayBuffer>): Promise<PushSubscription>
  fingerprint(endpoint: string): Promise<string>
  readBinding(): Promise<PushBinding | null>
  bind(binding: PushBinding | null): Promise<void>
  clearBinding(expected: PushBinding | null): Promise<boolean>
  exclusive(operation: () => Promise<void>): Promise<void>
}

export function vapidKey(value: unknown): Uint8Array<ArrayBuffer> {
  if (typeof value !== 'string' || !/^[A-Za-z0-9_-]+$/.test(value)) throw new Error('Сервер не настроил ключ Web Push.')
  const raw = atob(value.replace(/-/g, '+').replace(/_/g, '/') + '='.repeat((4 - value.length % 4) % 4))
  const bytes = Uint8Array.from(raw, (char) => char.charCodeAt(0))
  if (bytes.length !== 65 || bytes[0] !== 4) throw new Error('Сервер не настроил ключ Web Push.')
  return bytes
}

/** One serial owner for browser subscription mutations; invalidation cancels stale enrollment. */
export function createPushController(browser: BrowserPushPort, fetcher: typeof fetch): MobilePushPort & {
  setOwner(owner: PushOwner | null): void
  invalidate(): Promise<void>
} {
  let owner: PushOwner | null = null
  let epoch = 0
  let queue: Promise<void> = Promise.resolve()
  let ownedSubscription: PushSubscription | null = null
  let ownedBinding: PushBinding | null = null
  const retired = new WeakSet<PushSubscription>()
  let state: MobilePushState = { status: browser.supported() ? 'signed-out' : 'unsupported', message: browser.supported() ? 'Войди в аккаунт студента, чтобы включить Web Push.' : 'Этот браузер или режим приложения не поддерживает Web Push.' }
  const listeners = new Set<(state: MobilePushState) => void>()
  const publish = (status: MobilePushState['status'], message: string): void => {
    state = { status, message }
    for (const listener of listeners) listener(state)
  }
  const serial = (operation: () => Promise<void>): Promise<void> => {
    const version = epoch
    const task = queue.then(async () => {
      try { await browser.exclusive(operation) }
      catch { if (epoch === version) publish('error', 'Не удалось обновить подписку устройства. Повтори действие.') }
    })
    queue = task.catch(() => undefined)
    return task
  }
  const current = (captured: PushOwner, version: number): boolean => owner === captured && epoch === version
  async function api(captured: PushOwner, method: string, body?: unknown): Promise<Response> {
    return fetcher(`/api/push/${method === 'GET' ? 'vapid-public-key' : 'subscribe'}`, {
      method, credentials: 'omit', cache: 'no-store', signal: AbortSignal.timeout(10_000),
      headers: { Authorization: `Bearer ${captured.token}`, ...(body ? { 'Content-Type': 'application/json' } : {}) },
      ...(body ? { body: JSON.stringify(body) } : {}),
    })
  }
  async function remove(captured: PushOwner | null, sub: PushSubscription | null, binding: PushBinding | null): Promise<void> {
    // Disable display first, including notifications already in the system tray.
    // An old/unresponsive worker or unavailable IDB must not skip browser retirement.
    let gateFailure: unknown
    const closed = browser.clearBinding(binding).catch((error: unknown) => { gateFailure = error })
    if (!sub) { await closed; if (gateFailure) throw gateFailure; return }
    let failure: unknown
    try {
      if (captured) {
        const response = await api(captured, 'DELETE', { endpoint: sub.endpoint })
        if (!response.ok) failure = new Error('Сервер не подтвердил отключение. На этом устройстве уведомления отключены.')
      }
    } catch { failure = new Error('Сервер недоступен. На этом устройстве уведомления отключены.') }
    // Even after auth/network failure retire the endpoint so it cannot be reused by a new account.
    let retirementFailure: unknown
    try {
      if (!retired.has(sub)) {
        if (!await sub.unsubscribe()) {
          const active = await browser.subscription()
          if (active?.endpoint === sub.endpoint) retirementFailure = new Error('Не удалось удалить подписку браузера. Повтори отключение.')
        }
        if (!retirementFailure) retired.add(sub)
      }
    } catch (error) { retirementFailure = error }
    await closed
    if (retirementFailure) throw retirementFailure
    if (ownedSubscription === sub) { ownedSubscription = null; ownedBinding = null }
    if (gateFailure) throw gateFailure
    if (failure) throw failure
  }
  function invalidate(): Promise<void> {
    const previous = owner
    const sub = ownedSubscription
    const binding = ownedBinding
    ownedSubscription = null
    ownedBinding = null
    owner = null
    const version = ++epoch
    if (!browser.supported()) return Promise.resolve()
    publish('signed-out', 'Войди в аккаунт студента, чтобы включить Web Push.')
    // Close the worker gate immediately while any old subscribe request completes.
    const closed = binding ? browser.clearBinding(binding).catch(() => undefined) : Promise.resolve()
    return serial(async () => {
      await closed
      try { if (sub || binding) await remove(previous, sub, binding) } catch (error) {
        if (epoch === version) publish('error', error instanceof Error ? error.message : 'Повтори отключение уведомлений.')
      }
    })
  }
  function setOwner(next: PushOwner | null): void {
    if (owner?.userId === next?.userId && owner?.generation === next?.generation) {
      if (next && owner) owner.token = next.token
      return
    }
    // First bootstrap may restore only this account's already-confirmed binding.
    if (owner) void invalidate()
    owner = next
    const version = ++epoch
    if (!browser.supported() || !next) return
    publish('busy', 'Проверяем подписку устройства…')
    void serial(async () => {
      try {
        if (!current(next, version)) return
        const binding = await browser.readBinding()
        const sub = await browser.subscription()
        const matches = binding?.userId === next.userId && sub
          && binding.fingerprint === await browser.fingerprint(sub.endpoint)
        if (!current(next, version)) {
          if (matches) await remove(next, sub, binding)
          return
        }
        if (matches && browser.permission() === 'granted') {
          ownedSubscription = sub
          ownedBinding = binding
          publish('enabled', 'Web Push включён на этом устройстве.')
        }
        else {
          await remove(null, sub, binding)
          if (current(next, version)) publish(browser.permission() === 'denied' ? 'denied' : 'off', browser.permission() === 'denied' ? 'Браузер запретил уведомления. Разреши их в настройках сайта.' : 'Web Push выключен на этом устройстве.')
        }
      } catch { if (current(next, version)) publish('error', 'Не удалось проверить подписку. Повтори включение или отключение.') }
    })
  }
  async function enable(): Promise<void> {
    const captured = owner
    const version = epoch
    if (!captured || !browser.supported() || state.status === 'busy') return
    if (browser.installationRequired()) { publish('off', 'На iPhone и iPad сначала добавь приложение на домашний экран и открой его оттуда.'); return }
    publish('busy', 'Включаем Web Push…')
    // Called synchronously from the button's user gesture, before any other await.
    let permissionPromise: Promise<{ permission: NotificationPermission } | { error: unknown }>
    try { permissionPromise = browser.requestPermission().then((permission) => ({ permission }), (error: unknown) => ({ error })) }
    catch { publish('error', 'Не удалось запросить разрешение браузера.'); return }
    const result = await permissionPromise
    if (!current(captured, version)) return
    if ('error' in result) { publish('error', 'Не удалось запросить разрешение браузера.'); return }
    if (result.permission !== 'granted') {
      publish(result.permission === 'denied' ? 'denied' : 'off', result.permission === 'denied' ? 'Браузер запретил уведомления. Разреши их в настройках сайта.' : 'Разрешение на уведомления не выдано.')
      return
    }
    await serial(async () => {
      let created: PushSubscription | null = null
      let createdBinding: PushBinding | null = null
      try {
        if (!current(captured, version)) return
        const response = await api(captured, 'GET')
        if (!response.ok) throw new Error('Не удалось получить настройки Web Push с сервера.')
        const key = vapidKey((await response.json() as { publicKey?: unknown }).publicKey)
        if (!current(captured, version)) return
        // Always rotate an unconfirmed endpoint; another account must never inherit it.
        const previousBinding = await browser.readBinding()
        const previousSub = await browser.subscription()
        if (!current(captured, version)) return
        await remove(previousBinding?.userId === captured.userId ? captured : null, previousSub, previousBinding)
        if (!current(captured, version)) return
        created = await browser.createSubscription(key)
        if (!current(captured, version)) { await created.unsubscribe(); return }
        createdBinding = { userId: captured.userId, fingerprint: await browser.fingerprint(created.endpoint) }
        if (!current(captured, version)) { await created.unsubscribe(); return }
        ownedSubscription = created
        ownedBinding = createdBinding
        const json = created.toJSON()
        if (!json.endpoint || !json.keys?.p256dh || !json.keys.auth) throw new Error('Браузер вернул неполную подписку.')
        const bound = await api(captured, 'POST', { endpoint: json.endpoint, keys: json.keys })
        if (!bound.ok) throw new Error('Сервер не подтвердил подписку. Повтори включение.')
        if (!current(captured, version)) { await remove(captured, created, createdBinding); return }
        await browser.bind(createdBinding)
        if (!current(captured, version)) { await remove(captured, created, createdBinding); return }
        publish('enabled', 'Web Push включён на этом устройстве.')
      } catch (error) {
        if (created) { try { await remove(captured, created, createdBinding) } catch { /* Only the captured endpoint is retired. */ } }
        if (current(captured, version)) publish('error', error instanceof Error ? error.message : 'Не удалось включить Web Push.')
      }
    })
  }
  async function disable(): Promise<void> {
    const captured = owner
    const capturedSub = ownedSubscription
    const capturedBinding = ownedBinding
    const version = ++epoch
    if (!browser.supported()) return
    publish('busy', 'Отключаем Web Push…')
    await serial(async () => {
      try {
        if (!captured || !current(captured, version)) return
        // An explicit disable may target this account's newest binding from another tab.
        const binding = await browser.readBinding().catch(() => capturedBinding)
        const sub = binding?.userId === captured.userId ? await browser.subscription() : capturedSub
        await remove(captured, sub, binding?.userId === captured.userId ? binding : capturedBinding)
        if (epoch === version) publish('off', 'Web Push выключен на этом устройстве.')
      } catch (error) {
        if (epoch === version) publish('error', error instanceof Error ? error.message : 'Повтори отключение Web Push.')
      }
    })
  }
  return { snapshot: () => state, subscribe(listener) { listeners.add(listener); listener(state); return () => { listeners.delete(listener) } }, enable, disable, setOwner, invalidate }
}
