import { describe, expect, it, vi } from 'vitest'
import { executeNotificationAction, notificationNumericId, type NotificationActionContext } from './notifications-actions'
import { decodeNotificationLaunch, encodeNotificationLaunch, NotificationLaunchOwner } from './notifications-launch'
import type { NotificationAction } from './notifications-v2-models'
const action: NotificationAction = { kind: 'lesson', role: 'TEACHER', lessonId: '22' }
const context = (): NotificationActionContext => ({ generation: 1, userId: '7', role: 'TEACHER', authorityKey: 'session1|roles1', online: true })
describe('protected action and one-time launch', () => {
  it('never asks a target owner to act for another role or offline account', async () => {
    const open = vi.fn(async () => true)
    expect(await executeNotificationAction(action, () => ({ ...context(), role: 'HEADMAN' }), { open })).toBe(false)
    expect(await executeNotificationAction(action, () => ({ ...context(), online: false }), { open })).toBe(false)
    expect(open).not.toHaveBeenCalled()
  })
  it('fences route commit after protected read when signed authority changes', async () => {
    const state = context()
    let done!: () => void
    const gate = new Promise<void>(resolve => { done = resolve })
    let committed = false
    const task = executeNotificationAction(action, () => state, { async open(_target, current) { await gate; if (!current()) return false; committed = true; return true } })
    state.authorityKey = 'session1|roles2'
    done()
    expect(await task).toBe(false)
    expect(committed).toBe(false)
  })
  it('rejects conversion to number outside an existing consumer safe ID range', () => { expect(notificationNumericId('9007199254740993')).toBeNull(); expect(notificationNumericId('22')).toBe(22) })
  it('encodes only a bounded typed action and rejects extra raw payload or URL', () => {
    const encoded = encodeNotificationLaunch(action)!
    expect(decodeNotificationLaunch(encoded)).toEqual(action)
    const bad = btoa(JSON.stringify({ v: 2, action: { ...action, url: 'https://example.test' } })).replace(/=+$/, '')
    expect(decodeNotificationLaunch(bad)).toBeNull()
    expect(decodeNotificationLaunch('x'.repeat(385))).toBeNull()
  })
  it('rejects a push recipient before the first authenticated consumer', async () => {
    const open = vi.fn(async () => true)
    const launch = new NotificationLaunchOwner(action, '7')
    expect(await launch.consume('8', 'TEACHER', open)).toBe('unavailable')
    expect(open).not.toHaveBeenCalled()
    expect(launch.pendingRole).toBeNull()
  })
  it('waits for explicit matching role, consumes once and never reuses on another account', async () => {
    const launch = new NotificationLaunchOwner(action), open = vi.fn(async () => true)
    expect(await launch.consume('7', 'HEADMAN', open)).toBe('role-required')
    expect(open).not.toHaveBeenCalled()
    expect(await launch.consume('7', 'TEACHER', open)).toBe('opened')
    expect(await launch.consume('7', 'TEACHER', open)).toBe('empty')
    expect(open).toHaveBeenCalledTimes(1)
    const other = new NotificationLaunchOwner(action)
    await other.consume('7', 'HEADMAN', open)
    expect(await other.consume('8', 'TEACHER', open)).toBe('unavailable')
    expect(open).toHaveBeenCalledTimes(1)
  })
})
