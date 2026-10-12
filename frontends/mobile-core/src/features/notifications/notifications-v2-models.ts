/** Frozen Notifications V2 consumer contract; no transport or query owner here. */
export const legacyNotificationTypeIds = [
  'EXCUSE_REQUESTED',
  'EXCUSE_APPROVED',
  'EXCUSE_REJECTED',
  'EXCUSE_CANCELLED',
  'LATE_CHECKIN_REQUESTED',
  'LATE_CHECKIN_APPROVED',
  'LATE_CHECKIN_REJECTED',
  'LATE_CHECKIN_CANCELLED',
  'LESSON_STARTED',
  'LESSON_CLOSED',
  'LESSON_CANCELLED',
  'LESSON_REMINDER',
  'HOMEWORK_PUBLISHED',
  'HOMEWORK_UPDATED',
  'HOMEWORK_WEEKLY_DIGEST',
  'HOMEWORK_DUE_REMINDER',
  'ATTENDANCE_MARKED_BY_HEADMAN',
  'LESSON_BLOCKED',
  'LESSON_ONE_OFF_CREATED',
  'GROUP_RENAMED',
  'GROUP_ARCHIVED',
] as const
export const addedNotificationTypeIds = [
  'LESSON_TRANSFERRED',
  'LESSON_RESTORED',
  'LESSON_UPDATED',
  'HOMEWORK_WITHDRAWN',
  'GROUP_MEMBERSHIP_CHANGED',
  'HEADMAN_ACCESS_CHANGED',
  'ASSISTANT_ACCESS_CHANGED',
  'ADMIN_ACCESS_CHANGED',
  'SEMESTER_ACTIVATED',
  'SEMESTER_OPERATION_COMPLETED',
  'GROUP_PROMOTION_COMPLETED',
] as const
export const notificationTypeIds = [
  ...legacyNotificationTypeIds,
  ...addedNotificationTypeIds,
] as const
export type NotificationTypeId = (typeof notificationTypeIds)[number]
export type LegacyNotificationTypeId = (typeof legacyNotificationTypeIds)[number]

/** Only the complete transitional 21 or current 32 profile is accepted, never a partial migration. */
export function isCompleteNotificationTypeSet(ids: readonly string[]): boolean {
  const set = new Set(ids)
  const expected =
    ids.length === legacyNotificationTypeIds.length
      ? legacyNotificationTypeIds
      : notificationTypeIds
  return (
    set.size === ids.length && ids.length === expected.length && expected.every((id) => set.has(id))
  )
}
export function notificationTypeSetsMatch(
  left: readonly string[],
  right: readonly string[],
): boolean {
  return (
    isCompleteNotificationTypeSet(left) &&
    isCompleteNotificationTypeSet(right) &&
    left.length === right.length &&
    left.every((id) => right.includes(id))
  )
}
export const notificationPaletteIds = [
  'violet',
  'lavender',
  'purple',
  'indigo',
  'blue',
  'cobalt',
  'sky',
  'cyan',
  'turquoise',
  'teal',
  'green',
  'mint',
  'lime',
  'olive',
  'yellow',
  'amber',
  'orange',
  'coral',
  'red',
  'raspberry',
  'pink',
  'rose',
  'brown',
  'grey',
] as const
export type NotificationPaletteId = (typeof notificationPaletteIds)[number]
export type NotificationSignedRole = 'STUDENT' | 'HEADMAN' | 'TEACHER' | 'ADMIN'
export type NotificationAction =
  | { kind: 'lesson'; role: NotificationSignedRole; lessonId: string; lessonDate?: string }
  | {
      kind: 'request'
      role: NotificationSignedRole
      requestId: string
      requestKind: 'EXCUSE' | 'LATE_CHECKIN'
    }
  | { kind: 'homework'; role: NotificationSignedRole; homeworkId: string; lessonDate?: string }
  | { kind: 'group'; role: NotificationSignedRole; groupId: string }
  | { kind: 'homework-week'; role: NotificationSignedRole; weekStart: string }
export interface NotificationItem {
  id: string
  type: NotificationTypeId
  heading: string
  summary?: string
  sentAt: string
  orderAt: string
  readAt: string | null
  revision: string
  details: readonly { label?: string; value: string }[]
  action: NotificationAction | null
}
export interface NotificationInboxPage {
  role: NotificationSignedRole
  items: NotificationItem[]
  nextCursor: string | null
}
export interface NotificationCatalog {
  schemaVersion: 2
  revision: string
  groups: readonly { id: string; label: string }[]
  types: readonly {
    id: NotificationTypeId
    groupId: string
    label: string
    roles: readonly NotificationSignedRole[]
  }[]
  palette: readonly { id: NotificationPaletteId; label: string }[]
}
export interface NotificationTypePreferences {
  appPush: boolean
  telegram: boolean
  colorId: NotificationPaletteId
}
/** Added keys are absent in a validated legacy profile; the parser validates completeness. */
export type NotificationPreferenceTypes =
  | Record<NotificationTypeId, NotificationTypePreferences>
  | (Record<LegacyNotificationTypeId, NotificationTypePreferences> &
      Partial<Record<(typeof addedNotificationTypeIds)[number], never>>)
export function notificationCatalogMatchesPreferences(
  catalog: NotificationCatalog,
  preferences: { types: NotificationPreferenceTypes },
): boolean {
  return notificationTypeSetsMatch(
    catalog.types.map((type) => type.id),
    Object.keys(preferences.types),
  )
}
export interface NotificationPreferencesV2 {
  revision: string
  splitChannels: boolean
  types: NotificationPreferenceTypes
  mutedUntil: string | null
  telegramLinked: boolean
}
export interface NotificationPreferencesV2Update {
  expectedRevision: string
  splitChannels: boolean
  types: NotificationPreferenceTypes
  /** Omission preserves an existing pause; null explicitly unmutes. */
  mutedUntil?: string | null
}
export interface NotificationReadStateAck {
  role: NotificationSignedRole
  item: NotificationItem
  unreadCount: number
}
export interface NotificationCountAck {
  role: NotificationSignedRole
  count: number
}
export interface NotificationMarkAllAck {
  role: NotificationSignedRole
  unreadCount: number
}
export interface NotificationsV2Api {
  catalog(): Promise<NotificationCatalog>
  inbox(options?: { unreadOnly?: boolean; cursor?: string }): Promise<NotificationInboxPage>
  unreadCount(): Promise<number>
  setReadState(
    item: Pick<NotificationItem, 'id' | 'revision'>,
    read: boolean,
  ): Promise<NotificationReadStateAck>
  markAllRead(): Promise<NotificationMarkAllAck>
  getPreferences(): Promise<NotificationPreferencesV2>
  updatePreferences(value: NotificationPreferencesV2Update): Promise<NotificationPreferencesV2>
}
export interface NotificationActionIntent {
  requestId: number
  generation: number
  ownerKey: string
  action: NotificationAction
}
