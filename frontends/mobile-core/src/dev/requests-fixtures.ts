import type { StudentRequestDetail, StudentRequestLesson, StudentRequestOptions } from '../api/types'
export const requestsReviewScenarios = [
  ['open', 'Открытые заявки'], ['archive', 'Архив'], ['empty', 'Нет открытых заявок'], ['type', 'Выбор типа'],
  ['selection', 'Выбор нескольких пар'], ['selection-empty', 'Пары успешно загружены: список пуст'], ['selection-late', 'Выбор одной пары'], ['selection-missing', 'Недоступный сохранённый выбор'],
  ['excuse', 'Уважительная причина'], ['custom', 'Другая причина и комментарий'], ['files', 'Вложение'],
  ['late', 'Подтверждение запроса'], ['exhausted', 'Попытки закончились'], ['offline', 'Нет подключения'],
  ['readonly', 'Только чтение'], ['error', 'Ошибка загрузки'], ['forbidden', 'Нет доступа'], ['loading', 'Загрузка'],
  ['options-error', 'Ошибка вариантов'], ['submit-error', 'Неопределённый результат отправки'], ['long', 'Длинные названия'],
] as const
export function reviewLessons(long = false): StudentRequestLesson[] {
  return [
    { id: 'lesson-programming', lessonNumber: 2, status: 'CLOSED', blocked: false, semesterId: 'semester-review', subjectId: 'programming', subjectName: long ? 'Основы программирования, алгоритмов и структур данных на современных языках' : 'Основы программирования', subjectType: 'LECTURE', date: '2026-09-01', startsAt: '10:40:00', endsAt: '12:10:00' },
    { id: 'lesson-networks', lessonNumber: 3, status: 'CLOSED', blocked: false, semesterId: 'semester-review', subjectId: 'networks', subjectName: 'Компьютерные сети', subjectType: 'LAB', date: '2026-09-02', startsAt: '12:20:00', endsAt: '13:50:00' },
    { id: 'lesson-maths', lessonNumber: 1, status: 'CLOSED', blocked: false, semesterId: 'semester-review', subjectId: 'maths', subjectName: 'Математический анализ', subjectType: 'PRACTICE', date: '2026-09-01', startsAt: '09:00:00', endsAt: '10:30:00' },
    { id: 'lesson-unavailable', lessonNumber: 4, status: 'CANCELLED', blocked: false, semesterId: 'semester-review', subjectId: 'history', subjectName: 'История', subjectType: 'LECTURE', date: '2026-09-02', startsAt: '14:00:00', endsAt: '15:30:00' },
  ]
}
export function reviewRequestOptions(long = false, exhausted = false): StudentRequestOptions {
  return {
    budget: { semesterId: 'semester-review', limit: 5, used: exhausted ? 5 : 0, remaining: exhausted ? 0 : 5 },
    files: { maxFiles: 2, maxBytesPerFile: 10485760, maxBytesTotal: 20971520, contentTypes: ['image/jpeg', 'image/png', 'application/pdf'], extensions: ['jpg', 'jpeg', 'png', 'pdf'] },
    reasons: [{ code: 'ILLNESS', label: 'Болезнь', commentRequired: false }, { code: 'MEDICAL_EXAMINATION', label: 'Медицинское обследование', commentRequired: false }, { code: 'FAMILY_CIRCUMSTANCES', label: 'Семейные обстоятельства', commentRequired: false }, { code: 'COMPETITION_PARTICIPATION', label: 'Участие в соревнованиях', commentRequired: false }, { code: 'OTHER', label: 'Другое', commentRequired: true }],
    lessons: reviewLessons(long).map((lesson) => ({ lesson, excuseEligible: lesson.status !== 'CANCELLED', lateCheckinEligible: lesson.id === 'lesson-maths', pendingRequests: [] })),
  }
}
export function reviewRequests(long = false): StudentRequestDetail[] {
  const lessons = reviewLessons(long)
  const make = (id: string, status: 'PENDING' | 'APPROVED' | 'REJECTED' | 'CANCELLED', kind: 'EXCUSE' | 'LATE_CHECKIN'): StudentRequestDetail => ({
    summary: { id, kind, status, origin: 'MANUAL', createdAt: '2026-09-01T06:40:00Z', updatedAt: '2026-09-02T07:00:00Z', lessons: kind === 'EXCUSE' ? lessons.slice(0, 2) : [lessons[2]!] },
    reason: kind === 'EXCUSE' ? 'ILLNESS' : null, comment: null, attachments: [], decision: { comment: status === 'REJECTED' ? 'Недостаточно подтверждений' : null, decidedAt: status === 'PENDING' ? null : '2026-09-02T07:00:00Z' },
  })
  return [make('open-request', 'PENDING', 'EXCUSE'), make('approved-request', 'APPROVED', 'EXCUSE'), make('rejected-request', 'REJECTED', 'LATE_CHECKIN'), make('cancelled-request', 'CANCELLED', 'EXCUSE')]
}
