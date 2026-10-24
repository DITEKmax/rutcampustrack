import { afterEach, describe, expect, it, vi } from 'vitest'
import { notificationTypeIds } from '../../../../mobile-core/src/features/notifications/notifications-v2-models'
vi.mock('../../app/live', () => ({ useWebLiveRefresh() {} }))
function snapshot() {
  return {
    sessionId: '00000000-0000-4000-8000-000000000001',
    userId: '42',
    displayName: 'Тест',
    sessionVersion: '1',
    rolesVersion: '1',
    activeRole: 'HEADMAN',
    readOnly: false,
    roles: [
      {
        grantId: '1',
        role: 'HEADMAN',
        status: 'ACTIVE',
        selectable: true,
        readOnly: false,
        groupId: '1',
      },
    ],
    passwordPolicy: {
      minCodePoints: 12,
      maxUtf8Bytes: 72,
      requiresDecimalDigit: true,
      specialCategories: ['P', 'S'],
      normalization: 'NONE',
    },
  }
}
function item(id: string, read = false) {
  return {
    id,
    type: 'HOMEWORK_PUBLISHED',
    heading: 'Задание ' + id,
    sentAt: '2026-10-24T09:00:00Z',
    orderAt: '2026-10-24T09:00:00Z',
    readAt: read ? '2026-10-24T09:05:00Z' : null,
    revision: read ? '2' : '1',
    details: [],
    action: null,
  }
}
const preferences = {
  revision: '1',
  splitChannels: false,
  telegramLinked: true,
  mutedUntil: null,
  types: Object.fromEntries(
    notificationTypeIds.map((id) => [id, { appPush: true, telegram: true, colorId: 'violet' }]),
  ),
}
afterEach(async () => {
  const { webSession } = await import('../../auth/session')
  webSession.invalidate()
  vi.unstubAllGlobals()
  vi.resetModules()
  vi.useRealTimers()
})
describe('web notification projection ownership', () => {
  it('keeps the confirmed read state and count when an older inbox arrives afterwards', async () => {
    let read = false
    let hold = false
    let release!: (value: Response) => void
    const old = new Promise<Response>((resolve) => {
      release = resolve
    })
    vi.stubGlobal('fetch', async (input: RequestInfo | URL, init?: RequestInit) => {
      const path = String(input)
      if (path.endsWith('/login')) return Response.json({ accessToken: 'token' })
      if (path.endsWith('/session')) return Response.json(snapshot())
      if (path.endsWith('/preferences/v2')) return Response.json(preferences)
      if (path.endsWith('/read-state') && init?.method === 'PUT') {
        read = true
        return Response.json({ role: 'HEADMAN', item: item('one', true), unreadCount: 0 })
      }
      if (path.includes('/inbox/unread-count'))
        return Response.json({ role: 'HEADMAN', count: read ? 0 : 1 })
      if (path.includes('/inbox?')) {
        if (hold) {
          hold = false
          return old
        }
        return Response.json({ role: 'HEADMAN', items: [item('one', read)], nextCursor: null })
      }
      throw new Error(path)
    })
    const { webNotifications } = await import('./notifications')
    const { webSession } = await import('../../auth/session')
    await webSession.login('test', 'password')
    const store = webNotifications.value!
    await store.load()
    hold = true
    const reading = store.load(true)
    await store.mark(store.items.value[0]!, true)
    release(Response.json({ role: 'HEADMAN', items: [item('one')], nextCursor: null }))
    await reading
    expect(store.items.value[0]?.readAt).toBe('2026-10-24T09:05:00Z')
    expect(store.count.value).toBe(0)
    expect(store.uncertain.value).toBe(false)
  })

  it('expires a temporary toast after ten seconds while preserving the event in history', async () => {
    vi.useFakeTimers()
    let server = [item('one')]
    vi.stubGlobal('fetch', async (input: RequestInfo | URL) => {
      const path = String(input)
      if (path.endsWith('/login')) return Response.json({ accessToken: 'token' })
      if (path.endsWith('/session')) return Response.json(snapshot())
      if (path.endsWith('/preferences/v2')) return Response.json(preferences)
      if (path.includes('/inbox/unread-count'))
        return Response.json({ role: 'HEADMAN', count: server.length })
      if (path.includes('/inbox?'))
        return Response.json({ role: 'HEADMAN', items: server, nextCursor: null })
      throw new Error(path)
    })
    const { webNotifications } = await import('./notifications')
    const { webSession } = await import('../../auth/session')
    await webSession.login('test', 'password')
    const store = webNotifications.value!
    await store.load()
    expect(store.toasts.value).toHaveLength(0)
    server = [item('new'), ...server]
    await store.load(true)
    expect(store.toasts.value.map((value) => value.item.id)).toEqual(['new'])
    await vi.advanceTimersByTimeAsync(10_250)
    expect(store.toasts.value).toHaveLength(0)
    expect(store.items.value.some((value) => value.id === 'new')).toBe(true)
    webSession.invalidate()
    expect(webNotifications.value).toBeNull()
  })
})

describe('notification loading and count recovery', () => {
  it('finishes inbox loading and keeps a read ACK while the first count request is delayed', async () => {
    let read = false
    let hold = true
    let release!: (value: Response) => void
    const countResponse = new Promise<Response>((resolve) => {
      release = resolve
    })
    vi.stubGlobal('fetch', async (input: RequestInfo | URL, init?: RequestInit) => {
      const path = String(input)
      if (path.endsWith('/login')) return Response.json({ accessToken: 'token' })
      if (path.endsWith('/session')) return Response.json(snapshot())
      if (path.endsWith('/preferences/v2')) return Response.json(preferences)
      if (path.endsWith('/read-state') && init?.method === 'PUT') {
        read = true
        return Response.json({ role: 'HEADMAN', item: item('one', true), unreadCount: 0 })
      }
      if (path.includes('/inbox/unread-count')) {
        if (hold) {
          hold = false
          return countResponse
        }
        return Response.json({ role: 'HEADMAN', count: read ? 0 : 1 })
      }
      if (path.includes('/inbox?'))
        return Response.json({ role: 'HEADMAN', items: [item('one', read)], nextCursor: null })
      throw new Error(path)
    })
    const { webNotifications } = await import('./notifications')
    const { webSession } = await import('../../auth/session')
    await webSession.login('test', 'password')
    const store = webNotifications.value!
    await vi.waitFor(() => expect(store.items.value).toHaveLength(1))
    expect(store.loading.value).toBe(false)
    await store.mark(store.items.value[0]!, true)
    release(Response.json({ role: 'HEADMAN', count: 1 }))
    await vi.waitFor(() => expect(store.count.value).toBe(0))
    expect(store.loading.value).toBe(false)
    expect(store.items.value[0]?.readAt).not.toBeNull()
  })
})
