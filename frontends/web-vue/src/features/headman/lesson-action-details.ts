import type { HeadmanLessonAction } from '../../../../mobile-core/src/features/headman-journal/headman-journal-actions'

type Snapshot = HeadmanLessonAction['before']
type Format = (value: unknown) => string | null
const text: Format = value => value === null ? 'не указано' : typeof value === 'string' && value.trim() ? value.trim() : null
const number: Format = value => value === null ? 'не указан' : typeof value === 'number' && Number.isSafeInteger(value) && value > 0 ? String(value) : null
const time: Format = value => typeof value === 'string' && /^\d{2}:\d{2}(?::\d{2})?$/.test(value) ? value.slice(0, 5) : null
const date: Format = value => {
  if (typeof value !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(value)) return null
  const parsed = new Date(`${value}T12:00:00Z`)
  return Number.isFinite(parsed.getTime()) ? parsed.toLocaleDateString('ru-RU', { timeZone: 'UTC' }) : null
}
const dictionary = (labels: Readonly<Record<string, string>>): Format => value =>
  value === null ? 'не указано' : typeof value === 'string' ? labels[value.toUpperCase()] ?? null : null
const markLabels: Readonly<Record<string, string>> = { PRESENT: '«+»', EXCUSED: '«у»', ABSENT: '«н»', EMPTY: 'без отметки' }
const mark: Format = value => typeof value === 'string' ? markLabels[value] ?? null : null
const status = dictionary({
  PLANNED: 'предстоит', ACTIVE: 'идёт', CLOSED: 'завершена', CANCELLED: 'отменена', TRANSFERRED: 'перенесена',
  PENDING: 'на рассмотрении', APPROVED: 'одобрена', REJECTED: 'отклонена',
})
const type = dictionary({ LECTURE: 'лекция', PRACTICE: 'практика', LAB: 'лабораторная' })
const reason = dictionary({
  ILLNESS: 'болезнь', MEDICAL_EXAMINATION: 'медосмотр', COMPETITION_PARTICIPATION: 'соревнование',
  FAMILY_CIRCUMSTANCES: 'семейные обстоятельства', SUMMONS: 'повестка', UNIVERSITY_ORDER: 'приказ университета',
  EXEMPTION: 'освобождение', OTHER: 'другое',
})
const has = (snapshot: Snapshot, key: string) => snapshot !== null && Object.hasOwn(snapshot, key)

/** Only known, typed server snapshot fields become prose; redacted or absent fields stay absent. */
export function lessonActionDetails(action: HeadmanLessonAction): readonly string[] {
  if (action.outcome === 'REJECTED') return []
  const details: string[] = []
  const change = (key: string, label: string, format: Format) => {
    if (!has(action.after, key)) return
    if (!has(action.before, key) && action.after![key] === null) return
    const after = format(action.after![key])
    if (after === null) return
    const before = has(action.before, key) ? format(action.before![key]) : null
    if (before === after) return
    details.push(`${label}: ${before === null ? after : `${before} → ${after}`}`)
  }
  const attendance = ['ATTENDANCE_SET', 'ATTENDANCE_CLEARED', 'AUTO_ABSENT', 'GEO_ATTEMPT'].includes(action.action)
  if (attendance) {
    const before = has(action.before, 'status') ? mark(action.before!.status) : null
    const after = has(action.after, 'status') ? mark(action.after!.status) : null
    if (action.action === 'ATTENDANCE_CLEARED' && before !== null) details.push(`${before} → отметка снята`)
    else if (after !== null && after !== before) details.push(`Отметка: ${before === null ? after : `${before} → ${after}`}`)
    change('excuse_type', 'Причина', reason)
    change('excuse_comment', 'Комментарий', text)
    return details
  }
  change('room_snapshot', 'Аудитория', text)
  change('date', 'Дата', date)
  change('lesson_date', 'Дата', date)
  change('lesson_number', 'Номер пары', number)
  change('start_time', 'Начало', time)
  change('end_time', 'Окончание', time)
  change('lesson_type', 'Тип', type)
  change('status', action.action.startsWith('REQUEST_') ? 'Заявка' : 'Состояние пары', status)
  change('cancel_reason', 'Причина отмены', text)
  change('is_geo_blocked', 'Геоотметка', value => typeof value === 'boolean' ? value ? 'закрыта' : 'доступна' : null)
  change('is_blocked_by_headman', 'Самостоятельная отметка', value => typeof value === 'boolean' ? value ? 'закрыта' : 'доступна' : null)
  change('excuse_type', 'Причина', reason)
  change('excuse_comment', 'Комментарий', text)
  change('decision_comment', 'Комментарий к решению', text)
  change('title', 'Задание', text)
  change('publication_state', 'Публикация', dictionary({ PENDING: 'ожидает публикации', ACTIVE: 'опубликовано', ARCHIVED: 'в архиве' }))
  return details
}
