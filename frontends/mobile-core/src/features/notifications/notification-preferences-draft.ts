import {
  notificationTypeIds,
  type NotificationPaletteId,
  type NotificationPreferencesV2,
  type NotificationPreferencesV2Update,
  type NotificationTypeId,
} from './notifications-v2-models'

export type NotificationDeliveryChannel = 'appPush' | 'telegram'

/** Clone every type: the host's confirmed preferences must never become a mutable draft. */
export function copyNotificationPreferences(value: NotificationPreferencesV2): NotificationPreferencesV2 {
  return {
    ...value,
    types: Object.fromEntries(notificationTypeIds.map((id) => [id, { ...value.types[id] }])) as NotificationPreferencesV2['types'],
  }
}

export function notificationChannelsDiverge(value: NotificationPreferencesV2): boolean {
  return notificationTypeIds.some((id) => value.types[id].appPush !== value.types[id].telegram)
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
    for (const id of notificationTypeIds) {
      next.types[id].appPush = next.types[id][source]
      next.types[id].telegram = next.types[id][source]
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
  next.types[id][channel] = enabled
  if (!next.splitChannels) {
    next.types[id].appPush = enabled
    next.types[id].telegram = enabled
  }
  return next
}

export function changeNotificationColor(
  value: NotificationPreferencesV2,
  id: NotificationTypeId,
  colorId: NotificationPaletteId,
): NotificationPreferencesV2 {
  const next = copyNotificationPreferences(value)
  next.types[id].colorId = colorId
  return next
}

/** Pause is deliberately omitted. Changing delivery or colors must not accidentally unmute. */
export function notificationPreferencesUpdate(
  value: NotificationPreferencesV2,
  expectedRevision: string,
): NotificationPreferencesV2Update {
  return { expectedRevision, splitChannels: value.splitChannels, types: copyNotificationPreferences(value).types }
}
