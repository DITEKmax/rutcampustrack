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
  it('fills the complete event union after a burst while More is queued behind a live head', async () => {
    let server = ['a', 'b', 'c', 'd']
    let holdHead = false
    let release!: (value: Response) => void
    let started!: () => void
    const headStarted = new Promise<void>((resolve) => {
      started = resolve
    })
    const head = new Promise<Response>((resolve) => {
      release = resolve
    })
    vi.stubGlobal('fetch', async (input: RequestInfo | URL) => {
      const path = String(input)
      if (path.endsWith('/login')) return Response.json({ accessToken: 'token' })
      if (path.endsWith('/session')) return Response.json(snapshot())
      if (path.endsWith('/preferences/v2')) return Response.json(preferences)
      if (path.includes('/inbox/unread-count'))
        return Response.json({ role: 'HEADMAN', count: server.length })
      if (path.includes('/inbox?')) {
        const cursor = new URL(path, 'http://localhost').searchParams.get('cursor')
        if (!cursor && holdHead) {
          holdHead = false
          started()
          return head
        }
        const start = cursor ? server.indexOf(cursor) + 1 : 0
        const values = server.slice(start, start + 2)
        return Response.json({
          role: 'HEADMAN',
          items: values.map((id) => item(id)),
          nextCursor: start + 2 < server.length ? values.at(-1) : null,
        })
      }
      throw new Error(path)
    })
    const { webNotifications } = await import('./notifications')
    const { webSession } = await import('../../auth/session')
    await webSession.login('test', 'password')
    const store = webNotifications.value!
    await store.load()
    server = ['x', 'y', 'z', ...server]
    holdHead = true
    const refresh = store.load(true)
    await headStarted
    const more = store.more()
    release(
      Response.json({
        role: 'HEADMAN',
        items: server.slice(0, 2).map((id) => item(id)),
        nextCursor: 'y',
      }),
    )
    await Promise.all([refresh, more])
    expect(store.items.value.some((value) => value.id === 'z')).toBe(true)
    for (let page = 0; store.nextCursor.value && page < 5; page++) await store.more()
    expect(new Set(store.items.value.map((value) => value.id))).toEqual(new Set(server))
    expect(store.nextCursor.value).toBeNull()
  })

  it('reads an uncertain outcome without replaying it and permits a new explicit CAS from the current state', async () => {
    let unavailable = false
    let failedWrite = false
    let serverRead = false
    let revision = '1'
    const current = () => ({ ...item('one', serverRead), revision })
    vi.stubGlobal('fetch', async (input: RequestInfo | URL, init?: RequestInit) => {
      const path = String(input)
      if (path.endsWith('/login')) return Response.json({ accessToken: 'token' })
      if (path.endsWith('/session')) return Response.json(snapshot())
      if (path.endsWith('/preferences/v2')) return Response.json(preferences)
      if (path.endsWith('/read-state')) {
        if (!failedWrite) {
          failedWrite = true
          unavailable = true
          throw new TypeError('unknown result')
        }
        const command = JSON.parse(String(init?.body)) as {
          expectedRevision: string
          read: boolean
        }
        if (command.expectedRevision !== revision) return new Response(null, { status: 409 })
        serverRead = command.read
        revision = '8'
        return Response.json({ role: 'HEADMAN', item: current(), unreadCount: serverRead ? 0 : 1 })
      }
      if (path.includes('/inbox/unread-count'))
        return Response.json({ role: 'HEADMAN', count: serverRead ? 0 : 1 })
      if (path.includes('/inbox?')) {
        if (unavailable) throw new TypeError('readback unavailable')
        return Response.json({ role: 'HEADMAN', items: [current()], nextCursor: null })
      }
      throw new Error(path)
    })
    const { webNotifications } = await import('./notifications')
    const { webSession } = await import('../../auth/session')
    await webSession.login('test', 'password')
    const store = webNotifications.value!
    await store.load()
    await store.mark(store.items.value[0]!, true)
    expect(store.uncertain.value).toBe(true)
    unavailable = false
    revision = '7'
    await store.reconcile()
    expect(store.uncertain.value).toBe(false)
    expect(store.items.value[0]?.revision).toBe('7')
    expect(serverRead).toBe(false)
    expect(store.notice.value).toContain('не подтверждено')
    await store.mark(store.items.value[0]!, true)
    expect(serverRead).toBe(true)
    expect(store.items.value[0]?.revision).toBe('8')
    expect(store.count.value).toBe(0)
  })

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

  it('retains four arrivals until their individual ten-second expiries while preserving history', async () => {
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
    const arrivals = ['new-1', 'new-2', 'new-3', 'new-4']
    for (const [index, id] of arrivals.entries()) {
      if (index > 0) await vi.advanceTimersByTimeAsync(750)
      server = [item(id), ...server]
      await store.load(true)
      expect(store.toasts.value.map((value) => value.item.id)).toEqual(arrivals.slice(0, index + 1))
    }
    // A repeated live projection neither duplicates nor renews a seen event's lifetime.
    await store.load(true)
    await vi.advanceTimersByTimeAsync(7_749)
    expect(store.toasts.value.map((value) => value.item.id)).toEqual(arrivals)
    await vi.advanceTimersByTimeAsync(1)
    expect(store.toasts.value.map((value) => value.item.id)).toEqual(arrivals.slice(1))
    for (let expired = 2; expired <= arrivals.length; expired++) {
      await vi.advanceTimersByTimeAsync(750)
      expect(store.toasts.value.map((value) => value.item.id)).toEqual(arrivals.slice(expired))
    }
    expect(new Set(store.items.value.map((value) => value.id))).toEqual(
      new Set(['one', ...arrivals]),
    )
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
