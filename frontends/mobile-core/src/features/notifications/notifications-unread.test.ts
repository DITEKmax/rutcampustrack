import { describe, expect, it, vi } from 'vitest'
import type { NotificationsApi } from './notifications-client'
import { NotificationsUnreadState } from './notifications-unread'

function deferred() {
  let resolve!: (count: number) => void
  const promise = new Promise<number>(accept => { resolve = accept })
  return { promise, resolve }
}
function api(read: () => Promise<number>): NotificationsApi {
  return { unreadCount: read, listHistory: async () => ({ items: [], pageNumber: 0, totalPages: 0, totalElements: 0 }), markRead: async () => undefined, markAllRead: async () => undefined, getPreferences: async () => ({ categories: { lessons: true, reminders: true, homework: true, tickets: true, schedule: true, group: true }, mutedUntil: null }), updatePreferences: async value => value }
}

describe('NotificationsUnreadState', () => {
  it('coalesces one count owner and clears before changing account, ignoring a late old answer', async () => {
    const old = deferred()
    const read = vi.fn(() => old.promise)
    const state = new NotificationsUnreadState()
    state.bind('generation1|account1', api(read))
    const pending = state.refresh()
    expect(state.refresh()).toBe(pending)
    expect(read).toHaveBeenCalledTimes(1)
    state.bind('generation2|account2', api(async () => 105))
    expect(state.view.count).toBeNull()
    await state.refresh()
    old.resolve(7)
    await pending
    expect(state.view.count).toBe(105)
    state.bind(null, null)
    expect(state.view.count).toBeNull()
  })

  it('never publishes an in-flight count invalidated by read, all-read or realtime, then resolves server zero', async () => {
    const first = deferred()
    const read = vi.fn().mockImplementationOnce(() => first.promise).mockResolvedValue(0)
    const state = new NotificationsUnreadState()
    const values: (number | null)[] = []
    state.subscribe(() => values.push(state.view.count))
    state.bind('account', api(read))
    const pending = state.refresh()
    void state.invalidate()
    first.resolve(9)
    await pending
    expect(values).not.toContain(9)
    expect(state.view.count).toBe(0)
    expect(read).toHaveBeenCalledTimes(2)
  })

  it('marks an unavailable count unknown and retries without carrying stale account values', async () => {
    const read = vi.fn().mockResolvedValueOnce(4).mockRejectedValueOnce(new Error('offline')).mockResolvedValueOnce(3)
    const state = new NotificationsUnreadState()
    state.bind('account', api(read))
    await state.refresh()
    expect(state.view.count).toBe(4)
    await state.invalidate()
    expect(state.view.count).toBeNull()
    expect(state.view.status).toBe('error')
    await state.refresh()
    expect(state.view.count).toBe(3)
  })
})
