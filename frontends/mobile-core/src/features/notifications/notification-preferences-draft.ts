import {
  notificationTypeSetsMatch,
  type NotificationPaletteId,
  type NotificationPreferencesV2,
  type NotificationPreferencesV2Update,
  type NotificationTypeId,
  type NotificationTypePreferences,
} from './notifications-v2-models'

export type NotificationDeliveryChannel = 'appPush' | 'telegram'
export type NotificationPreferencesEdit = (
  value: NotificationPreferencesV2,
) => NotificationPreferencesV2
type EditableField = keyof NotificationTypePreferences
type PreferenceField = 'splitChannels' | `${NotificationTypeId}.${EditableField}`
const editableFields: readonly EditableField[] = ['appPush', 'telegram', 'colorId']

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

/** Collect changed fields, including reversals hidden by a still-unchanged confirmed baseline. */
export function notificationPreferenceEdits(
  baseline: NotificationPreferencesV2,
  draft: NotificationPreferencesV2,
  previouslyEdited: ReadonlySet<PreferenceField> = new Set(),
): Set<PreferenceField> {
  const edits = new Set(previouslyEdited)
  if (draft.splitChannels !== baseline.splitChannels) edits.add('splitChannels')
  for (const id of Object.keys(draft.types) as NotificationTypeId[]) {
    for (const field of editableFields) {
      if (draft.types[id]![field] !== baseline.types[id]?.[field]) edits.add(`${id}.${field}`)
    }
  }
  return edits
}

/** Apply local changes over a compatible authoritative profile. */
export function mergeNotificationPreferenceEdits(
  baseline: NotificationPreferencesV2,
  draft: NotificationPreferencesV2,
  current: NotificationPreferencesV2,
  uncertain: ReadonlySet<PreferenceField> = new Set(),
  unifiedChannel: NotificationDeliveryChannel = 'appPush',
): NotificationPreferencesV2 {
  const edits = notificationPreferenceEdits(baseline, draft, uncertain)
  const next = copyNotificationPreferences(current)
  const changedMode = edits.has('splitChannels')
  if (changedMode) next.splitChannels = draft.splitChannels
  for (const id of Object.keys(current.types) as NotificationTypeId[]) {
    const local = draft.types[id]!
    const entry = next.types[id]!
    const appPushEdited = edits.has(`${id}.appPush`)
    const telegramEdited = edits.has(`${id}.telegram`)
    if (edits.has(`${id}.colorId`)) entry.colorId = local.colorId
    if (appPushEdited) entry.appPush = local.appPush
    if (telegramEdited) entry.telegram = local.telegram
    if (!next.splitChannels && !draft.splitChannels) {
      const channel = changedMode ? unifiedChannel : telegramEdited ? 'telegram' : 'appPush'
      const enabled = entry[channel]
      entry.appPush = enabled
      entry.telegram = enabled
    }
  }
  // An independent split-channel edit must not change the other channel when the remote
  // profile was unified. Keep split mode only if it is needed to represent those choices.
  if (!next.splitChannels && notificationChannelsDiverge(next)) next.splitChannels = true
  return next
}

/** Reapply is explicit and may only merge within the same complete type set. */
export function reapplyNotificationPreferences(
  draft: NotificationPreferencesV2,
  current: NotificationPreferencesV2,
  baseline: NotificationPreferencesV2,
  uncertain?: ReadonlySet<PreferenceField>,
  unifiedChannel?: NotificationDeliveryChannel,
): NotificationPreferencesV2Update | null {
  if (!notificationTypeSetsMatch(Object.keys(draft.types), Object.keys(current.types))) return null
  return notificationPreferencesUpdate(
    mergeNotificationPreferenceEdits(baseline, draft, current, uncertain, unifiedChannel),
    current.revision,
  )
}

/** Compare only the editable delivery/color payload, independent of server metadata or key order. */
export function notificationPreferencesDiffer(
  draft: NotificationPreferencesV2,
  saved: NotificationPreferencesV2,
): boolean {
  if (draft.splitChannels !== saved.splitChannels) return true
  const ids = Object.keys(draft.types) as NotificationTypeId[]
  if (ids.length !== Object.keys(saved.types).length) return true
  return ids.some((id) => {
    const entry = draft.types[id],
      baseline = saved.types[id]
    return (
      !entry ||
      !baseline ||
      entry.appPush !== baseline.appPush ||
      entry.telegram !== baseline.telegram ||
      entry.colorId !== baseline.colorId
    )
  })
}
