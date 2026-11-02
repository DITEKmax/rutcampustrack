import samplePdfUrl from '../../../mobile-core/src/dev/assets/request-preview-sample.pdf?url'
import type { HeadmanGroupMember, HeadmanAssistant, HeadmanPermissionOption } from '../../../mobile-core/src/features/headman-group/headman-group-client'
import type { HeadmanSubject, HeadmanSemester, HeadmanTeacher } from '../../../mobile-core/src/features/headman-subjects/headman-subjects-client'
import type { HeadmanJournalLesson, HeadmanJournalReport, HeadmanJournalRosterEntry } from '../../../mobile-core/src/features/headman-journal/headman-journal-client'
import type { HeadmanRequestDetail, HeadmanRequestLesson } from '../../../mobile-core/src/features/headman-requests/headman-requests-client'
import type { HeadmanManagedHomework, HeadmanHomeworkHistory } from '../../../mobile-core/src/features/homework/headman-homework-client'
import type { HeadmanScheduleAssignment, HeadmanScheduleItem } from '../../../mobile-core/src/features/schedule/headman-schedule-client'
import type { HeadmanLessonAction, HeadmanLessonActionsPage } from '../../../mobile-core/src/features/headman-journal/headman-journal-actions'

// Preview follows the real Moscow civil day; it never replaces the global clock.
export const headmanDate = new Intl.DateTimeFormat('en-CA', { timeZone: 'Europe/Moscow', year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date())
export const headmanNow = new Date().toISOString()
export const groupId = 17
export function plus(date: string, days: number) {
  const value = new Date(`${date}T12:00:00Z`)
  value.setUTCDate(value.getUTCDate() + days)
  return value.toISOString().slice(0, 10)
}
export const monday = (date: string) => plus(date, -((new Date(`${date}T12:00:00Z`).getUTCDay() + 6) % 7))
const year = Number(headmanDate.slice(0, 4))
const autumn = Number(headmanDate.slice(5, 7)) >= 8
export const semester: HeadmanSemester = { id: 9, name: `${autumn ? 'Осенний' : 'Весенний'} семестр ${year}`, dateFrom: `${year}-${autumn ? '09-01' : '02-01'}`, dateTo: autumn ? `${year + 1}-01-31` : `${year}-07-31`, active: true }
export const semesters: readonly HeadmanSemester[] = [semester]
const names = ['Анна Смирнова', 'Борис Алексеев', 'Мария Васильева', 'Алексей Громов', 'Валерия Ушакова', 'Артём Карпов', 'Илья Соколов', 'Вероника Белова', 'Виктория Павлова', 'Глеб Фёдоров', 'Даниил Морозов', 'Евгения Крылова', 'Егор Никитин', 'Елена Соколова', 'Олег Волков', 'Антон Орлов', 'Ирина Павлова', 'Павел Белов', 'Лев Фролов', 'Лидия Фёдорова', 'Сергей Петров', 'Алина Кузнецова', 'Дмитрий Николаев', 'Нина Егорова']
export const members: readonly HeadmanGroupMember[] = names.map((fullName, index) => ({ id: index === 0 ? 42 : 100 + index, fullName, login: `preview.student${index + 1}`, status: 'ACTIVE', groupId, roles: [{ role: index === 0 ? 'HEADMAN' : 'STUDENT', status: 'ACTIVE', groupId }] }))
export const permissions: readonly HeadmanPermissionOption[] = [{ code: 'MARK_ATTENDANCE', label: 'Отмечать посещаемость' }, { code: 'MANAGE_EXCUSES', label: 'Управлять уважительными причинами' }, { code: 'MANAGE_HOMEWORK', label: 'Управлять домашними заданиями' }, { code: 'CANCEL_LESSONS', label: 'Отменять пары' }, { code: 'VIEW_STATS', label: 'Просматривать статистику' }]
export const assistants: readonly HeadmanAssistant[] = [{ id: 501, studentId: 101, studentName: names[1]!, login: 'preview.student2', groupId, permissions: ['MARK_ATTENDANCE', 'VIEW_STATS'], active: true, assignedAt: `${semester.dateFrom}T07:00:00Z`, revokedAt: null }, { id: 502, studentId: 102, studentName: names[2]!, login: 'preview.student3', groupId, permissions: ['MANAGE_HOMEWORK', 'MANAGE_EXCUSES'], active: true, assignedAt: `${semester.dateFrom}T07:00:00Z`, revokedAt: null }]
export const teachers: readonly HeadmanTeacher[] = [{ id: 201, fullName: 'Кузнецов Михаил Андреевич', employeeNumber: '10482' }, { id: 202, fullName: 'Орлова Елена Викторовна', employeeNumber: '11832' }, { id: 203, fullName: 'Волков Дмитрий Николаевич', employeeNumber: '12745' }]
const subjectTypes = [['LECTURE', 'LAB', 'PRACTICE'], ['LECTURE', 'PRACTICE'], ['LECTURE', 'LAB'], ['PRACTICE']] as const
export const subjects: readonly HeadmanSubject[] = ['Информационные системы', 'Высшая математика', 'Архитектура ЭВМ', 'Иностранный язык'].map((name, index) => ({ id: 12 + index, name, type: subjectTypes[index]![0], groupId, lessonTypes: subjectTypes[index]!, teacherIds: [201 + index % 3], assignments: subjectTypes[index]!.map((lessonType, typeIndex) => ({ id: 300 + index * 10 + typeIndex, teacherId: 201 + index % 3, semesterId: semester.id, lessonType, validFrom: semester.dateFrom, validUntilExclusive: null })) }))
export const assignments: readonly HeadmanScheduleAssignment[] = subjects.flatMap((subject) => subject.assignments.map(({ validUntilExclusive, ...assignment }) => ({ ...assignment, ...(validUntilExclusive ? { validUntilExclusive } : {}), groupId, subjectId: subject.id, subjectName: subject.name, teacherName: teachers.find((teacher) => teacher.id === assignment.teacherId)!.fullName }))).sort((left, right) => Number(left.id === 302) - Number(right.id === 302))
export const slots = [['08:00', '09:30'], ['09:40', '11:10'], ['11:20', '12:50'], ['13:00', '14:30'], ['14:40', '16:10'], ['16:20', '17:50'], ['18:00', '19:30'], ['19:40', '21:10']].map(([start, end], index) => ({ lessonNumber: index + 1, startTime: `${start}:00`, endTime: `${end}:00` }))
export const scheduleItems: readonly HeadmanScheduleItem[] = (() => {
  const regularAssignments = assignments.filter(assignment => assignment.id !== 302)
  const result: HeadmanScheduleItem[] = Array.from({ length: 6 }, (_, day) => [0, 1, 2].map((slot) => {
  const assignment = regularAssignments[(day + slot) % regularAssignments.length]!
  return { id: 1000 + day * 10 + slot, assignmentId: assignment.id!, subjectId: assignment.subjectId!, groupId, semesterId: semester.id, dayOfWeek: day + 1, lessonNumber: slot + 1, startTime: slots[slot]!.startTime, endTime: slots[slot]!.endTime, weekType: 'ALL' as const, room: `А-${201 + day * 10 + slot}`, active: true }
})).flat()
  const first = result[0]!, alternate = assignments[2]!
  return [{ ...first, weekType: 'ODD' }, ...result.slice(1), { ...first, id: 2000, assignmentId: alternate.id!, subjectId: alternate.subjectId!, weekType: 'EVEN', room: 'Б-305' }, { id: 2060, assignmentId: 302, subjectId: 12, groupId, semesterId: semester.id, dayOfWeek: 6, lessonNumber: 5, startTime: slots[4]!.startTime, endTime: slots[4]!.endTime, weekType: 'ALL', room: 'А-306', active: true }]
})()
function weekType(date: string): 'ODD' | 'EVEN' {
  const index = Math.round((Date.parse(`${monday(date)}T12:00:00Z`) - Date.parse(`${monday(semester.dateFrom)}T12:00:00Z`)) / 604800000)
  return index % 2 ? 'EVEN' : 'ODD'
}
export const lessons: readonly HeadmanJournalLesson[] = (() => {
  const result: HeadmanJournalLesson[] = []
  for (let date = semester.dateFrom; date <= semester.dateTo; date = plus(date, 1)) {
    const day = new Date(`${date}T12:00:00Z`).getUTCDay()
    const offset = Math.round((Date.parse(`${date}T12:00:00Z`) - Date.parse(`${semester.dateFrom}T12:00:00Z`)) / 86400000)
    for (const item of scheduleItems.filter((row) => row.dayOfWeek === day && (row.weekType === 'ALL' || row.weekType === weekType(date)))) {
      const assignment = assignments.find((row) => row.id === item.assignmentId)!
      const id = 10000 + offset * 10 + item.lessonNumber!
      const cancelled = (offset % 13 === 6 || date === headmanDate) && item.lessonNumber === 3
      result.push({ id, groupId, subjectId: item.subjectId!, date, lessonNumber: item.lessonNumber!, startTime: item.startTime!, endTime: item.endTime!, room: date === headmanDate && item.lessonNumber === 2 ? 'Б-204' : item.room!, lessonType: assignment.lessonType!, status: cancelled ? 'CANCELLED' : date < headmanDate || (date === headmanDate && item.lessonNumber === 1) ? 'CLOSED' : date === headmanDate && item.lessonNumber === 2 ? 'ACTIVE' : 'PLANNED', current: true, occurrenceId: String(id + 100000), occurrenceRevision: '1', blockedByHeadman: false, transferOperationId: null, transferState: null })
    }
  }
  // Demo timings are relative to this preview load, without replacing the application clock.
  const previewTime = Date.parse(headmanNow), dayStart = Date.parse(`${headmanDate}T00:00:00+03:00`), dayEnd = dayStart + 86400000 - 1000;
  const moscowTime = (instant: number) => new Intl.DateTimeFormat('en-GB', { timeZone: 'Europe/Moscow', hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false }).format(new Date(instant));
  const activeIndex = result.findIndex(lesson => lesson.date === headmanDate && lesson.lessonNumber === 2);
  if (activeIndex >= 0) result[activeIndex] = { ...result[activeIndex]!, status: 'ACTIVE', startTime: moscowTime(Math.max(dayStart, previewTime - 600000)), endTime: moscowTime(Math.min(dayEnd, previewTime + 45000)) };
  if (previewTime + 90000 < dayEnd) {
    const assignment = assignments[0]!;
    result.push({ id: 90002, groupId, subjectId: assignment.subjectId!, date: headmanDate, status: 'PLANNED', lessonNumber: 4, startTime: moscowTime(previewTime + 90000), endTime: moscowTime(Math.min(dayEnd, previewTime + 5490000)), room: 'А-405', lessonType: assignment.lessonType!, blockedByHeadman: false, occurrenceId: '190002', occurrenceRevision: '1', current: true, transferOperationId: null, transferState: null });
  }
  // One completed transfer: history retains its physical source; only the new target is current.
  const wednesday = plus(monday(headmanDate), 2)
  const sourceDate = wednesday > headmanDate ? wednesday : plus(wednesday, 7)
  const sourceIndex = result.findIndex(lesson => lesson.date === sourceDate && lesson.lessonNumber === 1 && lesson.status === 'PLANNED')
  const source = result[sourceIndex]
  if (source && plus(source.date, 1) <= semester.dateTo) {
    const operationId = '4f6d4c61-b701-4fb3-bb52-4478f0d64401'
    result[sourceIndex] = { ...source, status: 'TRANSFERRED', current: false, occurrenceRevision: '2', transferOperationId: operationId, transferState: 'COMPLETED' }
    const targetSlot = slots[3]!
    result.push({ ...source, id: 90001, date: plus(source.date, 1), lessonNumber: targetSlot.lessonNumber, startTime: targetSlot.startTime, endTime: targetSlot.endTime, room: 'Б-305', occurrenceRevision: '2', transferOperationId: operationId, transferState: 'COMPLETED' })
  }
  return result.sort((left, right) => left.date.localeCompare(right.date) || (left.lessonNumber ?? 0) - (right.lessonNumber ?? 0) || left.id - right.id)
})()
export const lessonById = (id: number) => lessons.find((lesson) => lesson.id === id)
export const previewMarkOverrides = new Map<string, { revision: number; entry: HeadmanJournalRosterEntry }>()
export const previewMarkEvents = new Map<number, readonly HeadmanLessonAction[]>()
export function report(lesson: HeadmanJournalLesson): HeadmanJournalReport {
  const held = lesson.status === 'CLOSED' || lesson.status === 'ACTIVE'
  const start = lesson.startTime ? Date.parse(`${lesson.date}T${lesson.startTime}+03:00`) : NaN
  const editable = semester.active && lesson.current && !['CANCELLED', 'TRANSFERRED', 'UNSUPPORTED'].includes(lesson.status) && lesson.transferState !== 'PENDING' && Number.isFinite(start) && Date.now() >= start
  return { lessonId: lesson.id, groupId, subjectId: lesson.subjectId, lessonDate: lesson.date, semesterId: semester.id, lessonStatus: lesson.status, editable, entries: members.map((member, index) => {
    const changed = previewMarkOverrides.get(`${lesson.id}/${member.id}`)?.entry
    if (changed) return { ...changed, editable }
    const code = (index + lesson.id) % 11
    const status = lesson.status === 'CANCELLED' ? 'CANCELLED' : !held || (lesson.status === 'ACTIVE' && index % 7 === 3) ? 'EMPTY' : code === 0 || code === 1 ? 'ABSENT' : code === 2 ? 'EXCUSED' : 'PRESENT'
    return { userId: member.id, displayName: member.fullName, status, symbol: null, markRevision: `preview-${lesson.id}-${member.id}-1`, source: status === 'EXCUSED' ? 'HEADMAN' : status === 'ABSENT' ? 'AUTO_SCHEDULER' : status === 'PRESENT' ? index === 0 ? 'HEADMAN' : 'STUDENT_GEO' : null, timestamp: ['PRESENT', 'ABSENT', 'EXCUSED'].includes(status) && lesson.startTime ? new Date(Date.parse(`${lesson.date}T${lesson.startTime}+03:00`) + index * 15000).toISOString() : null, excuseReason: status === 'EXCUSED' ? 'Болезнь' : null, excuseType: status === 'EXCUSED' ? 'ILLNESS' : null, comment: status === 'EXCUSED' ? 'Уважительная причина подтверждена.' : null, approvedTicket: null, attachmentId: null, attachmentName: null, attachmentContentType: null, attachmentSize: null, editable }
  }) }
}
export const previewPdf = (() => {
  const text = 'BT /F1 18 Tf 40 130 Td (Synthetic preview document) Tj 0 -28 Td /F1 11 Tf (No personal or medical information.) Tj ET'
  const objects = ['<< /Type /Catalog /Pages 2 0 R >>', '<< /Type /Pages /Kids [3 0 R] /Count 1 >>', '<< /Type /Page /Parent 2 0 R /MediaBox [0 0 360 200] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>', '<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>', `<< /Length ${text.length} >>\nstream\n${text}\nendstream`]
  let document = '%PDF-1.4\n'
  const offsets = [0]
  objects.forEach((object, index) => { offsets.push(document.length); document += `${index + 1} 0 obj\n${object}\nendobj\n` })
  const xref = document.length
  document += `xref\n0 ${objects.length + 1}\n0000000000 65535 f \n${offsets.slice(1).map(offset => `${String(offset).padStart(10, '0')} 00000 n \n`).join('')}trailer\n<< /Size ${objects.length + 1} /Root 1 0 R >>\nstartxref\n${xref}\n%%EOF\n`
  return document
})()
const recent = lessons.filter((lesson) => lesson.status === 'CLOSED').slice(-12)
export const requests: readonly HeadmanRequestDetail[] = (['PENDING', 'PENDING', 'PENDING', 'APPROVED', 'REJECTED', 'CANCELLED', 'PENDING', 'PENDING', 'APPROVED', 'REJECTED', 'PENDING', 'APPROVED', 'REJECTED'] as const).map((status, index) => {
  const kind = index >= 7 ? index < 10 ? 'LATE_CHECKIN' : 'EXCUSE' : index === 6 || index % 2 === 1 ? 'LATE_CHECKIN' : 'EXCUSE'
  const member = members[index >= 7 ? 3 : index + 1]!
  const targetIndex = index >= 7 ? index - 7 : index
  const targets = recent.slice(targetIndex, targetIndex + (kind === 'EXCUSE' ? 2 : 1))
  const rows: readonly HeadmanRequestLesson[] = targets.map((lesson) => {
    const entry = report(lesson).entries.find((row) => row.userId === member.id)!
    return { occurrenceId: lesson.occurrenceId!, occurrenceRevision: lesson.occurrenceRevision!, lessonId: lesson.id, groupId, subjectId: lesson.subjectId, subjectName: subjects.find((subject) => subject.id === lesson.subjectId)!.name, subjectType: lesson.lessonType, semesterId: semester.id, lessonNumber: lesson.lessonNumber, date: lesson.date, startsAt: lesson.startTime, endsAt: lesson.endTime, requestLessonStatus: 'ABSENT', attendanceStatus: entry.status === 'EMPTY' ? null : entry.status, attendanceSource: entry.source }
  })
  // Today's two submissions deliberately concern older lessons. Older OPEN rows remain in the full list.
  const createdAt = index < 2
    ? new Date(Math.max(Date.parse(`${headmanDate}T00:00:00+03:00`), Date.parse(headmanNow) - (index + 1) * 60000)).toISOString()
    : `${targets.at(-1)!.date}T14:10:00Z`
  return { decisionRevision: kind === 'EXCUSE' ? 'a'.repeat(64) : null, summary: { id: String(index >= 7 ? 8100 + index - 7 : 8000 + index), kind, status, studentId: member.id, studentName: member.fullName, reason: kind === 'EXCUSE' ? index === 2 ? 'Медосмотр' : 'Болезнь' : null, comment: kind === 'EXCUSE' ? 'Прошу учесть уважительную причину отсутствия на занятиях.' : 'Был на занятии, но не успел отметиться. Прошу проверить посещение.', coverageStart: targets[0]!.date, coverageEnd: targets.at(-1)!.date, lessonCount: rows.length, alreadyMarkedCount: rows.filter((row) => row.attendanceStatus === 'PRESENT').length, hasAttachments: kind === 'EXCUSE' && (index === 0 || index === 2), createdAt, updatedAt: index === 2 ? headmanNow : createdAt, decisionBy: status === 'APPROVED' || status === 'REJECTED' ? 42 : null, decisionAt: status === 'APPROVED' || status === 'REJECTED' ? `${targets.at(-1)!.date}T16:00:00Z` : null, decisionComment: status === 'APPROVED' ? 'Посещение подтверждено.' : status === 'REJECTED' ? 'Нет подтверждения присутствия на занятии.' : null }, lessons: rows, currentAffectedLessons: rows, attachments: kind === 'EXCUSE' && (index === 0 || index === 2) ? [{ id: `preview-confirmation-${8000 + index}`, name: index === 0 ? 'Подтверждение.pdf' : 'Направление.pdf', contentType: 'application/pdf', size: previewPdf.length, sha256: null, state: 'AVAILABLE', uploadedAt: createdAt, expiresAt: null, downloadUrl: null }] : [] }
})
const homeworkContent: Readonly<Record<number, readonly { title: string; description: string }[]>> = {
  12: [{ title: 'Подготовить схему информационной системы', description: 'Выбери знакомую информационную систему и выдели её участников, данные и процессы.\nНарисуй схему взаимодействия компонентов.' }, { title: 'Описать требования к системе библиотеки', description: 'Запиши пять функциональных требований и три требования к качеству системы.\nПодготовь примеры пользовательских сценариев.' }],
  13: [{ title: 'Решить задачи № 16–20 на пределы', description: 'Повтори определение предела функции и реши задачи № 16–20 из методички.\nДля каждого решения запиши краткое обоснование.' }, { title: 'Вычислить производные сложных функций', description: 'Реши примеры № 4.12–4.18 и проверь ответы.\nОтдельно выпиши применённые правила дифференцирования.' }],
  14: [{ title: 'Повторить команды процессора', description: 'Составь таблицу основных групп команд процессора с примерами.\nНарисуй схему выполнения одной арифметической команды.' }, { title: 'Сравнить виды памяти компьютера', description: 'Сравни регистры, кэш и оперативную память по назначению и скорости доступа.\nОбъясни принцип локальности обращений.' }],
  15: [{ title: 'Подготовить диалог по теме Travel', description: 'Составь диалог о планировании поездки на 12–15 реплик.\nИспользуй лексику из последнего занятия.' }, { title: 'Прочитать текст об университетской жизни', description: 'Прочитай текст и выпиши десять новых слов с переводом.\nПодготовь короткий пересказ на английском языке.' }],
}
export const homeworks: readonly HeadmanManagedHomework[] = [-2, -1, 0, 0, 1, 2, 3, 5, 7].map((days, index) => {
  const date = plus(headmanDate, days)
  const lesson = lessons.find((row) => row.date >= date && row.current && row.status !== 'CANCELLED' && row.status !== 'TRANSFERRED')!
  const content = homeworkContent[lesson.subjectId!]![index % 2]!
  return { id: 9000 + index, bindingId: 9500 + index, requestKey: null, ...content, link: index === 3 ? samplePdfUrl : null, subjectId: lesson.subjectId!, groupId, semesterId: semester.id, publishedBy: 42, lessonDate: lesson.date, lessonNumber: index === 5 ? null : lesson.lessonNumber, bindingMode: index === 5 ? 'DATE' : 'LESSON', lessonType: index === 5 ? null : lesson.lessonType as 'LECTURE' | 'LAB' | 'PRACTICE', revision: index % 2 + 1, archived: index < 2 }
})
export function homeworkHistory(item: HeadmanManagedHomework): readonly HeadmanHomeworkHistory[] {
  return [{ id: item.id * 10, revision: 1, actorId: 42, actorDisplayName: names[0]!, occurredAt: `${plus(item.lessonDate, -2)}T09:00:00Z`, action: 'CREATED', before: null, after: item }, ...(item.revision === 2 ? [{ id: item.id * 10 + 1, revision: 2, actorId: 42, actorDisplayName: names[0]!, occurredAt: `${plus(item.lessonDate, -1)}T10:30:00Z`, action: 'UPDATED', before: { title: 'Подготовить конспект' }, after: item }] : [])]
}
export function actions(lesson: HeadmanJournalLesson): HeadmanLessonActionsPage {
  return { items: [...(previewMarkEvents.get(lesson.id) ?? []), ...['LESSON_CREATED', 'ROOM_CHANGED', 'HOMEWORK_CREATED', 'ATTENDANCE_SET', 'GEO_ATTEMPT'].map<HeadmanLessonAction>((action, index) => ({ id: `${lesson.id}-${index}`, occurredAt: `${lesson.date}T${String(6 + index).padStart(2, '0')}:10:00Z`, action, source: index === 4 ? 'attendance-service' : 'academic-service', outcome: index === 4 ? 'REJECTED' : 'APPLIED', errorCode: index === 4 ? 'OUTSIDE_RADIUS' : null, actor: { userId: index === 4 ? 101 : 42, role: index === 4 ? 'STUDENT' : 'HEADMAN', displayName: names[index === 4 ? 1 : 0]! }, student: action === 'ATTENDANCE_SET' || action === 'GEO_ATTEMPT' ? { userId: 101, displayName: names[1]! } : null, before: action === 'ROOM_CHANGED' ? { room_snapshot: 'А-401' } : null, after: action === 'ROOM_CHANGED' ? { room_snapshot: lesson.room } : action === 'ATTENDANCE_SET' ? { status: 'PRESENT' } : null, geo: index === 4 ? { latitude: 55.7901, longitude: 37.6086, distanceMeters: 840, radiusMeters: 100, accuracyMeters: 15, verdict: 'OUTSIDE_RADIUS' } : null, ticket: null, homework: action === 'HOMEWORK_CREATED' ? { id: String(homeworks[2]!.id), revision: 1 } : null }))], nextCursor: null }
}
