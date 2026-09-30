import { describe, expect, it, vi } from 'vitest'
import { createPushController, type BrowserPushPort, type PushBinding } from './push-controller'

const key = btoa(String.fromCharCode(4, ...Array<number>(64).fill(1))).replace(/=/g, '')
function harness() {
  let sub: PushSubscription | null = null
  let binding: PushBinding | null = null
  let sequence = 0
  const retired: string[] = []
  const browser: BrowserPushPort = {
    supported: () => true, permission: () => 'granted', requestPermission: vi.fn(async (): Promise<NotificationPermission> => 'granted'),
    installationRequired: () => false, subscription: async () => sub,
    createSubscription: async () => {
      const endpoint = `endpoint-${++sequence}`
      const created = { endpoint, toJSON: () => ({ endpoint, keys: { p256dh: 'key', auth: 'auth' } }), unsubscribe: async () => { retired.push(endpoint); if (sub === created) sub = null; return true } } as unknown as PushSubscription
      sub = created
      return created
    },
    fingerprint: async (endpoint) => endpoint,
    readBinding: async () => binding, bind: async (next) => { binding = next },
  }
  const fetcher = vi.fn<typeof fetch>(async (_input, init) => init?.method === 'GET'
    ? new Response(JSON.stringify({ publicKey: key })) : new Response(null, { status: 204 }))
  const controller = createPushController(browser, fetcher)
  const ready = async (userId = '1', generation = 1) => {
    controller.setOwner({ userId, generation, token: `token-${userId}` })
    await vi.waitFor(() => expect(controller.snapshot().status).not.toBe('busy'))
  }
  return { browser, fetcher, controller, ready, retired, binding: () => binding, sub: () => sub }
}

describe('PWA device enrollment isolation', () => {
  it('requires an explicit action and stores binding only after server confirmation', async () => {
    const h = harness()
    await h.ready()
    expect(h.browser.requestPermission).not.toHaveBeenCalled()
    expect(h.fetcher).not.toHaveBeenCalled()
    await h.controller.enable()
    expect(h.controller.snapshot().status).toBe('enabled')
    expect(h.binding()).toEqual({ userId: '1', fingerprint: 'endpoint-1' })
    expect(h.fetcher.mock.calls.at(-1)?.[1]?.headers).toMatchObject({ Authorization: 'Bearer token-1' })
  })

  it('retires a subscription when server enrollment fails and cannot display it', async () => {
    const h = harness()
    await h.ready()
    h.fetcher.mockImplementation(async (_url, init) => init?.method === 'GET'
      ? new Response(JSON.stringify({ publicKey: key })) : new Response(null, { status: 503 }))
    await h.controller.enable()
    expect(h.controller.snapshot().status).toBe('error')
    expect(h.binding()).toBeNull()
    expect(h.sub()).toBeNull()
    expect(h.retired).toEqual(['endpoint-1'])
  })

  it('drops delayed enrollment after logout/account switch and never transfers an endpoint', async () => {
    const h = harness()
    await h.ready()
    let finish!: (response: Response) => void
    h.fetcher.mockImplementation(async (_url, init) => {
      if (init?.method === 'GET') return new Response(JSON.stringify({ publicKey: key }))
      if (init?.method === 'POST') return new Promise<Response>((resolve) => { finish = resolve })
      return new Response(null, { status: 204 })
    })
    const pending = h.controller.enable()
    await vi.waitFor(() => expect(finish).toBeTypeOf('function'))
    const logout = h.controller.invalidate()
    h.controller.setOwner({ userId: '2', generation: 2, token: 'token-2' })
    finish(new Response(null, { status: 201 }))
    await pending
    await logout
    await vi.waitFor(() => expect(h.controller.snapshot().status).toBe('off'))
    expect(h.binding()).toBeNull()
    expect(h.sub()).toBeNull()
    expect(h.retired).toEqual(['endpoint-1'])
    h.fetcher.mockImplementation(async (_url, init) => init?.method === 'GET' ? new Response(JSON.stringify({ publicKey: key })) : new Response(null, { status: 201 }))
    await h.controller.enable()
    expect(h.binding()).toEqual({ userId: '2', fingerprint: 'endpoint-2' })
  })

  it('closes display and retires the browser endpoint even when authenticated disable fails', async () => {
    const h = harness()
    await h.ready()
    await h.controller.enable()
    h.fetcher.mockResolvedValue(new Response(null, { status: 401 }))
    await h.controller.disable()
    expect(h.binding()).toBeNull()
    expect(h.sub()).toBeNull()
    expect(h.controller.snapshot().status).toBe('error')
  })

  it('retires the endpoint even if the worker cannot acknowledge a closed display gate', async () => {
    const h = harness()
    await h.ready()
    await h.controller.enable()
    h.browser.bind = async () => { throw new Error('Worker unavailable') }
    await h.controller.disable()
    expect(h.sub()).toBeNull()
    expect(h.retired).toEqual(['endpoint-1'])
    expect(h.controller.snapshot().status).toBe('error')
  })

  it.each(['denied', 'default'] as const)('does not enroll when permission is %s', async (permission) => {
    const h = harness()
    await h.ready()
    h.browser.requestPermission = vi.fn(async () => permission)
    await h.controller.enable()
    expect(h.fetcher).not.toHaveBeenCalled()
    expect(h.binding()).toBeNull()
  })

  it('explains missing VAPID without creating a browser subscription', async () => {
    const h = harness()
    await h.ready()
    h.fetcher.mockResolvedValue(new Response(JSON.stringify({ publicKey: '' })))
    await h.controller.enable()
    expect(h.controller.snapshot()).toMatchObject({ status: 'error', message: 'Сервер не настроил ключ Web Push.' })
    expect(h.sub()).toBeNull()
  })
})
