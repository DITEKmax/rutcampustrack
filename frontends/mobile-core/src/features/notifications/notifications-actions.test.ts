import { describe, expect, it, vi } from 'vitest'
import {
  executeNotificationAction,
  notificationNumericId,
  type NotificationActionContext,
} from './notifications-actions'
import {
  consumePwaNotificationLaunch,
  decodeNotificationLaunch,
  encodeNotificationLaunch,
  NotificationLaunchOwner,
} from './notifications-launch'
import { parseNotificationAction } from './notifications-v2-client'
import type { NotificationAction } from './notifications-v2-models'
const action: NotificationAction = { kind: 'lesson', role: 'TEACHER', lessonId: '22' }
const context = (): NotificationActionContext => ({
  generation: 1,
  userId: '7',
  role: 'TEACHER',
  authorityKey: 'session1|roles1',
  online: true,
})
describe('protected action and one-time launch', () => {
  it('never asks a target owner to act for another role or offline account', async () => {
    const open = vi.fn(async () => true)
    expect(
      await executeNotificationAction(action, () => ({ ...context(), role: 'HEADMAN' }), { open }),
    ).toBe(false)
    expect(
      await executeNotificationAction(action, () => ({ ...context(), online: false }), { open }),
    ).toBe(false)
    expect(open).not.toHaveBeenCalled()
  })
  it('fences route commit after protected read when signed authority changes', async () => {
    const state = context()
    let done!: () => void
    const gate = new Promise<void>((resolve) => {
      done = resolve
    })
    let committed = false
    const task = executeNotificationAction(action, () => state, {
      async open(_target, current) {
        await gate
        if (!current()) return false
        committed = true
        return true
      },
    })
    state.authorityKey = 'session1|roles2'
    done()
    expect(await task).toBe(false)
    expect(committed).toBe(false)
  })
  it('rejects conversion to number outside an existing consumer safe ID range', () => {
    expect(notificationNumericId('9007199254740993')).toBeNull()
    expect(notificationNumericId('22')).toBe(22)
  })
  it('encodes only a bounded typed action and rejects extra raw payload or URL', () => {
    const encoded = encodeNotificationLaunch(action)!
    expect(decodeNotificationLaunch(encoded)).toEqual(action)
    const bad = btoa(
      JSON.stringify({ v: 2, action: { ...action, url: 'https://example.test' } }),
    ).replace(/=+$/, '')
    expect(decodeNotificationLaunch(bad)).toBeNull()
    expect(decodeNotificationLaunch('x'.repeat(385))).toBeNull()
  })
  it('accepts only an exact student check-in launch with a signed-64-bit string ID', async () => {
    const checkin = { kind: 'checkin', role: 'STUDENT', lessonId: '9223372036854775807' } as const
    const encodeRaw = (value: unknown) =>
      btoa(JSON.stringify(value)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')
    const decodeRaw = (value: unknown) => decodeNotificationLaunch(encodeRaw({ v: 2, action: value }))
    expect(decodeRaw(checkin)).toEqual(checkin)
    for (const bad of [
      { ...checkin, role: 'HEADMAN' }, { ...checkin, role: 'TEACHER' },
      { ...checkin, lessonId: '9223372036854775808' }, { ...checkin, lessonId: '077' },
      { ...checkin, lessonId: 77 }, { ...checkin, lessonId: '0' }, { ...checkin, lessonId: '-1' },
      { ...checkin, lessonDate: '2026-11-06' }, { ...checkin, url: 'https://example.test' },
    ]) expect(decodeRaw(bad)).toBeNull()
    expect(parseNotificationAction(checkin)).toBeNull()
    const open = vi.fn(async () => true)
    const launch = new NotificationLaunchOwner(decodeRaw(checkin), '7')
    expect(await launch.consume('7', 'HEADMAN', open)).toBe('role-required')
    expect(await launch.consume('7', 'STUDENT', open)).toBe('opened')
    expect(await launch.consume('7', 'STUDENT', open)).toBe('empty')
    expect(open).toHaveBeenCalledExactlyOnceWith(checkin)
  })
  it('consumes the PWA Telegram fallback once and preserves explicit push ownership and precedence', () => {
    const checkin = { kind: 'checkin', role: 'STUDENT', lessonId: '77' } as const
    const telegram = `n2_${encodeNotificationLaunch(checkin)}`
    const url = new URL(`https://app.test/?tgWebAppStartParam=${telegram}&pushOwner=7&pushNotifications=1#tab`)
    expect(consumePwaNotificationLaunch(url)).toEqual({ action: checkin, consumed: true, invalid: false })
    expect(url.search).toBe('?pushOwner=7&pushNotifications=1')
    expect(url.hash).toBe('#tab')
    expect(consumePwaNotificationLaunch(url).action).toBeNull()
    url.searchParams.set('tgWebAppStartParam', telegram)
    url.searchParams.set('notificationAction', encodeNotificationLaunch(action)!)
    expect(consumePwaNotificationLaunch(url)).toEqual({ action, consumed: true, invalid: false })
    url.searchParams.set('tgWebAppStartParam', `n2_${encodeNotificationLaunch(action)}`)
    expect(consumePwaNotificationLaunch(url)).toEqual({ action: null, consumed: true, invalid: false })
  })
  it('rejects a push recipient before the first authenticated consumer', async () => {
    const open = vi.fn(async () => true)
    const launch = new NotificationLaunchOwner(action, '7')
    expect(await launch.consume('8', 'TEACHER', open)).toBe('unavailable')
    expect(open).not.toHaveBeenCalled()
    expect(launch.pendingRole).toBeNull()
  })
  it('waits for explicit matching role, consumes once and never reuses on another account', async () => {
    const launch = new NotificationLaunchOwner(action),
      open = vi.fn(async () => true)
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
