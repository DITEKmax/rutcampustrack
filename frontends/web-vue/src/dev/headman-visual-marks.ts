import type { HeadmanJournalRosterEntry } from '../../../mobile-core/src/features/headman-journal/headman-journal-client'
import { groupId, lessonById, members, previewMarkEvents, previewMarkOverrides, report, semester } from './headman-visual-data'

const json = (detail: string, status: number) => Response.json({ detail }, { status })
const reasons: Readonly<Record<string, string>> = { ILLNESS: 'Болезнь', MEDICAL_EXAMINATION: 'Медосмотр', COMPETITION_PARTICIPATION: 'Соревнование', FAMILY_CIRCUMSTANCES: 'Семейные обстоятельства', SUMMONS: 'Повестка', UNIVERSITY_ORDER: 'Приказ университета', EXEMPTION: 'Освобождение', OTHER: 'Другое' }
const snapshot = (entry: HeadmanJournalRosterEntry) => entry.status === 'EMPTY' ? null : {
  status: entry.status, source: entry.source, excuse_type: entry.excuseType, excuse_comment: entry.comment,
}

/** Only the dedicated loopback HEADMAN preview calls this in-memory mark/clear simulation. */
export async function headmanPreviewMark(url: URL, method: string, body: () => Promise<Record<string, unknown>>): Promise<Response | null> {
  const match = /^\/api\/attendance\/lessons\/(\d+)\/students\/(\d+)$/.exec(url.pathname)
  if (!match || (method !== 'PUT' && method !== 'DELETE')) return null
  const lesson = lessonById(Number(match[1])), member = members.find(item => item.id === Number(match[2]) && item.groupId === groupId)
  if (!lesson || !member || lesson.groupId !== groupId) return json('Синтетическая пара или студент не найдены.', 404)
  const start = lesson.startTime ? Date.parse(`${lesson.date}T${lesson.startTime}+03:00`) : NaN
  if (!semester.active || lesson.date < semester.dateFrom || lesson.date > semester.dateTo || !lesson.current || !['PLANNED', 'ACTIVE', 'CLOSED'].includes(lesson.status) || !Number.isFinite(start) || Date.now() < start)
    return json('Изменение отметки этой пары сейчас недоступно.', 409)
  let input: Record<string, unknown>
  try { input = method === 'DELETE' ? Object.fromEntries(url.searchParams) : await body() }
  catch { return json('В этом preview подтверждение файлом не записывается.', 415) }
  const current = report(lesson).entries.find(entry => entry.userId === member.id)
  if (!current?.editable || current.approvedTicket) return json('Отметка защищена от изменения.', 403)
  const expected = current.status === 'EMPTY' ? 'EMPTY' : current.markRevision
  if (input.expectedStatus !== current.status || !expected || input.expectedRevision !== expected) return json('Отметка уже изменилась. Обнови журнал.', 409)
  if (method === 'DELETE' && current.status === 'EMPTY') return json('Пустая отметка не требует снятия.', 400)
  if (method === 'PUT' && !['PRESENT', 'ABSENT', 'EXCUSED'].includes(String(input.status))) return json('Выбери поддерживаемую отметку.', 400)
  const excused = method === 'PUT' && input.status === 'EXCUSED'
  if (excused && (typeof input.excuseType !== 'string' || !reasons[input.excuseType])) return json('Выбери причину уважительного отсутствия.', 400)
  if (!excused && (input.excuseType != null || input.comment != null)) return json('Причина и комментарий доступны только для уважительной отметки.', 400)
  if (input.comment != null && (typeof input.comment !== 'string' || input.comment.length > 1000)) return json('Комментарий слишком длинный.', 400)
  const key = `${lesson.id}/${member.id}`, revision = (previewMarkOverrides.get(key)?.revision ?? 1) + 1, timestamp = new Date().toISOString()
  const entry: HeadmanJournalRosterEntry = method === 'DELETE'
    ? { ...current, status: 'EMPTY', symbol: null, markRevision: null, source: null, timestamp: null, excuseReason: null, excuseType: null, comment: null, attachmentId: null, attachmentName: null, attachmentContentType: null, attachmentSize: null }
    : { ...current, status: input.status as 'PRESENT' | 'ABSENT' | 'EXCUSED', symbol: null, markRevision: `preview-${lesson.id}-${member.id}-${revision}`, source: 'HEADMAN', timestamp, excuseReason: excused ? reasons[String(input.excuseType)]! : null, excuseType: excused ? String(input.excuseType) : null, comment: excused && typeof input.comment === 'string' ? input.comment || null : null, attachmentId: null, attachmentName: null, attachmentContentType: null, attachmentSize: null }
  previewMarkOverrides.set(key, { revision, entry })
  previewMarkEvents.set(lesson.id, [{ id: `preview-manual-${lesson.id}-${member.id}-${revision}`, occurredAt: timestamp, action: method === 'DELETE' ? 'ATTENDANCE_CLEARED' : 'ATTENDANCE_SET', source: 'attendance-service', outcome: 'APPLIED', errorCode: null, actor: { userId: 42, role: 'HEADMAN', displayName: members.find(item => item.id === 42)?.fullName ?? null }, student: { userId: member.id, displayName: member.fullName }, before: snapshot(current), after: snapshot(entry), geo: null, ticket: null, homework: null }, ...(previewMarkEvents.get(lesson.id) ?? [])])
  return method === 'DELETE' ? new Response(null, { status: 204 }) : Response.json({ lessonId: lesson.id, userId: member.id, status: entry.status, timestamp })
}
