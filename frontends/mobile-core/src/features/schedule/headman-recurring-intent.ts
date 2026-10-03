import { HeadmanScheduleApiError, type HeadmanLifecyclePreview, type HeadmanScheduleItem, type HeadmanScheduleUpdateInput } from './headman-schedule-client'

export interface RecurringOwner {
  userId: string
  sessionId: string
  groupId: number
  semesterId: number
}

export interface RecurringIntent {
  readonly owner: Readonly<RecurringOwner>
  readonly target: Readonly<HeadmanScheduleItem>
  readonly action: 'UPDATE' | 'DELETE'
  readonly input: Readonly<HeadmanScheduleUpdateInput> | null
  readonly preview: Readonly<HeadmanLifecyclePreview>
  readonly key: string
  // Only a first definitive rejection can authorize a fresh user-confirmed command.
  readonly rejected: boolean
  readonly transferIds: readonly string[]
}

/** Hide authorization-revoked data without deleting an uncertain owner's durable command. */
export function hideDeniedHeadmanSchedule(cause: unknown, view: {
  denied: { value: boolean }
  busy: { value: boolean }
  rows: readonly { value: readonly unknown[] }[]
  editor: readonly { value: unknown }[]
}): boolean {
  if (!(cause instanceof HeadmanScheduleApiError) || cause.response.status !== 403) return false
  view.denied.value = true
  view.busy.value = false
  for (const rows of view.rows) rows.value = []
  for (const field of view.editor) field.value = null
  return true
}

export function retainRecurringBack(denied: boolean, busy: boolean, intent: RecurringIntent | null): boolean {
  return !denied && (busy || (intent !== null && !intent.rejected))
}

export function recurringScope(owner: RecurringOwner): string {
  return `rct:recurring:${owner.userId}:${owner.sessionId}:HEADMAN:${owner.groupId}:${owner.semesterId}`
}

export function recurringUpdate(target: HeadmanScheduleItem, weekType: HeadmanScheduleUpdateInput['weekType'], room: string): Omit<HeadmanScheduleUpdateInput, 'expectedRevision'> {
  const { subjectId, dayOfWeek, lessonNumber, startTime, endTime } = target
  if (!subjectId || !dayOfWeek || !lessonNumber || !startTime || !endTime) throw new Error('Слот не содержит исходные параметры. Обнови расписание.')
  if (room.trim().length > 64) throw new Error('Аудитория — до 64 символов.')
  return { subjectId, dayOfWeek, lessonNumber, startTime, endTime, weekType, room: room.trim() || null }
}

export function persistRecurringIntent(storage: Pick<Storage, 'setItem'>, intent: RecurringIntent): void {
  storage.setItem(recurringScope(intent.owner), JSON.stringify(intent))
}

export function readRecurringIntent(storage: Pick<Storage, 'getItem'>, owner: RecurringOwner): RecurringIntent | null {
  const raw = storage.getItem(recurringScope(owner))
  if (!raw) return null
  const value = JSON.parse(raw) as RecurringIntent
  if (!value?.owner || recurringScope(value.owner) !== recurringScope(owner)
    || !value.target || value.target.groupId !== owner.groupId || value.target.semesterId !== owner.semesterId
    || !Number.isSafeInteger(value.target.id) || (value.target.id ?? 0) <= 0
    || !/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(value.key) || !/^[0-9a-f]{64}$/.test(value.preview?.revision)
    || !['UPDATE', 'DELETE'].includes(value.action) || typeof value.rejected !== 'boolean'
    || !Array.isArray(value.transferIds) || value.transferIds.some((id) => typeof id !== 'string' || !/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(id))
    || [value.preview.updatedCount, value.preview.removedCount, value.preview.restoredCount, value.preview.createdCount].some((count) => !Number.isSafeInteger(count) || count < 0)) {
    throw new Error('Не удалось восстановить исходный запрос серии.')
  }
  if (value.action === 'UPDATE') {
    if (!value.input || value.input.expectedRevision !== value.preview.revision
      || !['ALL', 'ODD', 'EVEN'].includes(value.input.weekType)
      || (value.input.room !== null && typeof value.input.room !== 'string')
      || Object.entries(recurringUpdate(value.target, value.input.weekType, value.input.room ?? '')).some(([field, expected]) => value.input?.[field as keyof HeadmanScheduleUpdateInput] !== expected)) {
      throw new Error('Сохранённый запрос серии не совпадает с исходным слотом.')
    }
  } else if (value.input !== null) throw new Error('Сохранённое удаление серии повреждено.')
  return Object.freeze({ ...value, owner: Object.freeze({ ...value.owner }), target: Object.freeze({ ...value.target }),
    preview: Object.freeze({ ...value.preview }), input: value.input ? Object.freeze({ ...value.input }) : null,
    transferIds: Object.freeze([...value.transferIds]) })
}
