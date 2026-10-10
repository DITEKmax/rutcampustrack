import { describe, expect, it } from 'vitest'
import { NotificationsMutationQueue } from './notifications-mutations'
import { NotificationsUnreadState } from './notifications-unread'

describe('notification command ordering', () => {
  it('orders different-card read/unread and bulk ACKs while old GET remains fenced', async () => {
    const queue = new NotificationsMutationQueue(), unread = new NotificationsUnreadState()
    let release!: () => void, oldCount!: (count: number) => void
    const blocked = new Promise<void>(resolve => { release = resolve })
    unread.bind('HEADMAN|session1', { unreadCount: () => new Promise(resolve => { oldCount = resolve }) })
    const get = unread.refresh()
    let serverCount = 10
    const started: string[] = []
    const first = queue.run(async () => { started.push('read-A'); serverCount--; await blocked; unread.acknowledge(serverCount) })
    const second = queue.run(async () => { started.push('read-B'); serverCount--; unread.acknowledge(serverCount) })
    await Promise.resolve()
    expect(started).toEqual(['read-A'])
    release()
    await Promise.all([first, second])
    expect(serverCount).toBe(8); expect(unread.view.count).toBe(8)
    const read = queue.run(async () => { started.push('read-C'); serverCount--; unread.acknowledge(serverCount) })
    const unRead = queue.run(async () => { started.push('unread-A'); serverCount++; unread.acknowledge(serverCount) })
    const all = queue.run(async () => { started.push('all'); serverCount = 0; unread.acknowledge(serverCount) })
    await Promise.all([read, unRead, all])
    oldCount(10); await get
    expect(started).toEqual(['read-A', 'read-B', 'read-C', 'unread-A', 'all'])
    expect(unread.view.count).toBe(0)
  })
  it('does not let a rejected command poison the following explicit retry', async () => {
    const queue = new NotificationsMutationQueue()
    await expect(queue.run(async () => { throw new Error('503') })).rejects.toThrow('503')
    expect(await queue.run(async () => 2)).toBe(2)
  })
})
