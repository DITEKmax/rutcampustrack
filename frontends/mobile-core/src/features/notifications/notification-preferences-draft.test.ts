import { describe, expect, it } from 'vitest'
import { notificationTypeIds, type NotificationPreferencesV2 } from './notifications-v2-models'
import { changeNotificationChannelMode, changeNotificationColor, changeNotificationDelivery, notificationPreferencesUpdate } from './notification-preferences-draft'

function preferences(): NotificationPreferencesV2 {
  return {
    revision: 'confirmed-7', splitChannels: false, mutedUntil: '2026-10-12T09:00:00Z', telegramLinked: false,
    types: Object.fromEntries(notificationTypeIds.map((id, index) => [id, { appPush: index % 2 === 0, telegram: index % 2 === 0, colorId: 'violet' }])) as NotificationPreferencesV2['types'],
  }
}

describe('account notification draft transitions', () => {
  it('copies unified choices into both split channels without modifying the confirmed profile', () => {
    const confirmed = preferences()
    const before = structuredClone(confirmed)
    const split = changeNotificationChannelMode(confirmed, true)!
    expect(split.splitChannels).toBe(true)
    for (const id of notificationTypeIds) {
      expect(split.types[id]).toEqual(confirmed.types[id])
      expect(split.types[id]).not.toBe(confirmed.types[id])
    }
    expect(confirmed).toEqual(before)
  })

  it('requires an explicit column for divergent channels and preserves the complete draft on cancellation', () => {
    const draft = preferences()
    draft.splitChannels = true
    draft.types.EXCUSE_APPROVED.telegram = !draft.types.EXCUSE_APPROVED.appPush
    const before = structuredClone(draft)
    expect(changeNotificationChannelMode(draft, false)).toBeNull()
    expect(draft).toEqual(before)
  })

  it.each(['appPush', 'telegram'] as const)('applies only the chosen %s column to every type in one complete update', (channel) => {
    const draft = preferences()
    draft.splitChannels = true
    for (const id of notificationTypeIds) draft.types[id].telegram = !draft.types[id].appPush
    draft.types.LESSON_STARTED.colorId = 'coral'
    const before = structuredClone(draft)
    const unified = changeNotificationChannelMode(draft, false, channel)!
    const payload = notificationPreferencesUpdate(unified, 'confirmed-7')
    expect(payload.splitChannels).toBe(false)
    expect(Object.keys(payload.types)).toHaveLength(notificationTypeIds.length)
    for (const id of notificationTypeIds) {
      expect(payload.types[id].appPush).toBe(before.types[id][channel])
      expect(payload.types[id].telegram).toBe(before.types[id][channel])
      expect(payload.types[id].colorId).toBe(before.types[id].colorId)
    }
    expect(unified.mutedUntil).toBe(before.mutedUntil)
    expect(payload).not.toHaveProperty('mutedUntil')
    expect(draft).toEqual(before)
  })

  it('keeps split Telegram independent and unified delivery changes equal without changing pause or color', () => {
    const confirmed = preferences()
    const split = changeNotificationChannelMode(confirmed, true)!
    const changed = changeNotificationDelivery(split, 'LESSON_STARTED', 'telegram', false)
    expect(changed.types.LESSON_STARTED.appPush).toBe(true)
    expect(changed.types.LESSON_STARTED.telegram).toBe(false)
    const unified = changeNotificationDelivery(confirmed, 'LESSON_STARTED', 'appPush', false)
    expect(unified.types.LESSON_STARTED).toEqual({ appPush: false, telegram: false, colorId: 'violet' })
    expect(unified.mutedUntil).toBe(confirmed.mutedUntil)
  })

  it('keeps the edit isolated from confirmed/conflict profiles and sends a new CAS baseline only deliberately', () => {
    const confirmed = preferences()
    const draft = changeNotificationColor(confirmed, 'LESSON_STARTED', 'coral')
    const conflict = { ...preferences(), revision: 'confirmed-8' }
    const payload = notificationPreferencesUpdate(draft, confirmed.revision)
    expect(payload.expectedRevision).toBe('confirmed-7')
    expect(confirmed.types.LESSON_STARTED.colorId).toBe('violet')
    expect(conflict.types.LESSON_STARTED.colorId).toBe('violet')
    const reapply = notificationPreferencesUpdate(draft, conflict.revision)
    expect(reapply.expectedRevision).toBe('confirmed-8')
    expect(reapply.types.LESSON_STARTED.colorId).toBe('coral')
    expect(reapply).not.toHaveProperty('mutedUntil')
    reapply.types.LESSON_STARTED.colorId = 'grey'
    expect(draft.types.LESSON_STARTED.colorId).toBe('coral')
  })
})
