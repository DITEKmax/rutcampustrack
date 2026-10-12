import {
  notificationTypeSetsMatch,
  type NotificationPaletteId,
  type NotificationPreferencesV2,
  type NotificationPreferencesV2Update,
  type NotificationTypeId,
} from './notifications-v2-models'

export type NotificationDeliveryChannel = 'appPush' | 'telegram'

/** Clone every type: the host's confirmed preferences must never become a mutable draft. */
export function copyNotificationPreferences(
  value: NotificationPreferencesV2,
): NotificationPreferencesV2 {
  return {
    ...value,
    types: Object.fromEntries(
      Object.entries(value.types).map(([id, entry]) => [id, { ...entry }]),
    ) as NotificationPreferencesV2['types'],
  }
}

export function notificationChannelsDiverge(value: NotificationPreferencesV2): boolean {
  return Object.values(value.types).some((entry) => entry.appPush !== entry.telegram)
}

/** Undefined means the split → unified operation still needs the user's column choice. */
export function changeNotificationChannelMode(
  value: NotificationPreferencesV2,
  splitChannels: boolean,
  keep?: NotificationDeliveryChannel,
): NotificationPreferencesV2 | null {
  if (!splitChannels && notificationChannelsDiverge(value) && !keep) return null
  const next = copyNotificationPreferences(value)
  next.splitChannels = splitChannels
  if (!splitChannels || !value.splitChannels) {
    const source = keep ?? 'appPush'
    for (const entry of Object.values(next.types)) {
      entry.appPush = entry[source]
      entry.telegram = entry[source]
    }
  }
  return next
}

export function changeNotificationDelivery(
  value: NotificationPreferencesV2,
  id: NotificationTypeId,
  channel: NotificationDeliveryChannel,
  enabled: boolean,
): NotificationPreferencesV2 {
  const next = copyNotificationPreferences(value)
  const entry = next.types[id]
  if (!entry) throw new Error('Тип уведомления отсутствует в подтверждённом каталоге')
  entry[channel] = enabled
  if (!next.splitChannels) {
    entry.appPush = enabled
    entry.telegram = enabled
  }
  return next
}

export function changeNotificationColor(
  value: NotificationPreferencesV2,
  id: NotificationTypeId,
  colorId: NotificationPaletteId,
): NotificationPreferencesV2 {
  const next = copyNotificationPreferences(value)
  const entry = next.types[id]
  if (!entry) throw new Error('Тип уведомления отсутствует в подтверждённом каталоге')
  entry.colorId = colorId
  return next
}

/** Pause is deliberately omitted. Changing delivery or colors must not accidentally unmute. */
export function notificationPreferencesUpdate(
  value: NotificationPreferencesV2,
  expectedRevision: string,
): NotificationPreferencesV2Update {
  return {
    expectedRevision,
    splitChannels: value.splitChannels,
    types: copyNotificationPreferences(value).types,
  }
}

/** Reapply is explicit and may only change the revision within the same complete type set. */
export function reapplyNotificationPreferences(
  draft: NotificationPreferencesV2,
  current: NotificationPreferencesV2,
): NotificationPreferencesV2Update | null {
  if (!notificationTypeSetsMatch(Object.keys(draft.types), Object.keys(current.types))) return null
  return notificationPreferencesUpdate(draft, current.revision)
}
