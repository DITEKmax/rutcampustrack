export interface HeadmanLessonAction {
  readonly id: string
  readonly occurredAt: string
  readonly action: string
  readonly actor: { readonly userId: number; readonly role: string; readonly displayName: string | null } | null
  readonly student: { readonly userId: number; readonly displayName: string | null } | null
  readonly before: Readonly<Record<string, unknown>> | null
  readonly after: Readonly<Record<string, unknown>> | null
  readonly geo: { readonly distanceMeters: number | null; readonly radiusMeters: number | null; readonly accuracyMeters: number | null; readonly verdict: string } | null
  readonly ticket: { readonly kind: string; readonly id: string; readonly status: string } | null
  readonly homework: { readonly id: string; readonly revision: number | null } | null
}
export interface HeadmanLessonActionsPage { readonly items: readonly HeadmanLessonAction[]; readonly nextCursor: string | null }
const record = (value: unknown): value is Record<string, unknown> => typeof value === 'object' && value !== null && !Array.isArray(value)
const text = (value: unknown): string | null => typeof value === 'string' && value.length > 0 ? value : null
const id = (value: unknown): string | null => typeof value === 'number' && Number.isSafeInteger(value) && value > 0 ? String(value) : text(value)
const number = (value: unknown): number | null => typeof value === 'number' && Number.isFinite(value) ? value : null
export function normalizeLessonActionsPage(value: unknown): HeadmanLessonActionsPage {
  if (!record(value) || !Array.isArray(value.items) || (value.nextCursor !== null && typeof value.nextCursor !== 'string')) throw new Error('Сервер вернул некорректную историю пары.')
  const items = value.items.map((entry: unknown): HeadmanLessonAction => {
    if (!record(entry) || !text(entry.id) || !text(entry.occurredAt) || !Number.isFinite(Date.parse(String(entry.occurredAt))) || !text(entry.action)) throw new Error('Сервер вернул некорректное событие пары.')
    const actor = record(entry.actor) && number(entry.actor.userId) !== null && text(entry.actor.role) ? { userId: Number(entry.actor.userId), role: String(entry.actor.role), displayName: text(entry.actor.displayName) } : null
    const student = record(entry.student) && number(entry.student.userId) !== null ? { userId: Number(entry.student.userId), displayName: text(entry.student.displayName) } : null
    return { id:String(entry.id), occurredAt:String(entry.occurredAt), action:String(entry.action), actor, student,
      before: record(entry.before) ? entry.before : null, after: record(entry.after) ? entry.after : null,
      geo: record(entry.geo) && text(entry.geo.verdict) ? {distanceMeters:number(entry.geo.distanceMeters),radiusMeters:number(entry.geo.radiusMeters),accuracyMeters:number(entry.geo.accuracyMeters),verdict:String(entry.geo.verdict)} : null,
      ticket:record(entry.ticket) && id(entry.ticket.id) && text(entry.ticket.kind) && text(entry.ticket.status) ? {id:id(entry.ticket.id)!,kind:String(entry.ticket.kind),status:String(entry.ticket.status)} : null,
      homework:record(entry.homework) && id(entry.homework.id) ? {id:id(entry.homework.id)!,revision:number(entry.homework.revision)} : null,
    }
  })
  return {items,nextCursor:text(value.nextCursor)}
}
