import type { RecurringOwner } from './headman-recurring-intent'
import { recurringScope } from './headman-recurring-intent'
import type { HeadmanScheduleCreateInput } from './headman-schedule-client'
export interface RecurringCreateIntent { readonly owner: Readonly<RecurringOwner>; readonly key: string; readonly input: Readonly<HeadmanScheduleCreateInput> }
export const recurringCreateScope = (owner: RecurringOwner): string => recurringScope(owner) + ':create'
export function freezeRecurringCreate(owner: RecurringOwner, key: string, input: HeadmanScheduleCreateInput): RecurringCreateIntent {
  const intent = { owner: Object.freeze({ ...owner }), key, input: Object.freeze({ ...input }) }
  // Run the same validation before storage and after reload.
  return readRecurringCreate({ getItem: () => JSON.stringify(intent) }, owner)!
}
export function readRecurringCreate(storage: Pick<Storage, 'getItem'>, owner: RecurringOwner): RecurringCreateIntent | null {
  const raw = storage.getItem(recurringCreateScope(owner)); if (!raw) return null
  const value = JSON.parse(raw) as RecurringCreateIntent
  const input = value?.input
  if (!value?.owner || recurringCreateScope(value.owner) !== recurringCreateScope(owner)
    || !/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(value.key)
    || !input || input.groupId !== owner.groupId || input.semesterId !== owner.semesterId
    || [input.subjectId, input.assignmentId].some((id) => !Number.isSafeInteger(id) || (id ?? 0) < 1)
    || !Number.isInteger(input.dayOfWeek) || input.dayOfWeek < 1 || input.dayOfWeek > 7
    || !Number.isInteger(input.lessonNumber) || input.lessonNumber < 1 || input.lessonNumber > 8
    || !['ALL', 'ODD', 'EVEN'].includes(input.weekType)
    || typeof input.startTime !== 'string' || typeof input.endTime !== 'string'
    || !/^\d{2}:\d{2}:\d{2}$/.test(input.startTime) || !/^\d{2}:\d{2}:\d{2}$/.test(input.endTime)
    || input.startTime >= input.endTime || (input.room !== undefined && (typeof input.room !== 'string' || input.room.length > 64))) throw new Error('Не удалось восстановить исходный запрос создания расписания.')
  return Object.freeze({ key: value.key, owner: Object.freeze({ ...value.owner }), input: Object.freeze({ ...input }) })
}
export async function submitRecurringCreate(storage: Pick<Storage, 'setItem'>, intent: RecurringCreateIntent, write: (input: Readonly<HeadmanScheduleCreateInput>, key: string) => Promise<unknown>): Promise<void> {
  // Refusal to persist must never start the write. This durable intent is retained until a validated ACK.
  storage.setItem(recurringCreateScope(intent.owner), JSON.stringify(intent))
  await write(intent.input, intent.key)
}
