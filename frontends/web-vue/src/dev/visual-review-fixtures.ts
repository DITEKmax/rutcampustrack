import { createTodayReviewScenarios } from '../../../mobile-core/src/dev/today-fixtures'
import { attendanceFixture } from '../../../mobile-core/src/dev/attendance-fixtures'
import { reviewRequestOptions, reviewRequests } from '../../../mobile-core/src/dev/requests-fixtures'
import { civilDate, weekday } from '../../../mobile-core/src/features/attendance/attendance-periods'
import { statisticsFixtureDetail, statisticsFixtureOverview } from '../../../mobile-core/src/dev/statistics-fixtures'
import sessionFixture from '../../../mobile-core/fixtures/session.json'
import homeworkFixture from '../../../mobile-core/fixtures/homework-feed.json'
import type { StudentAttendanceHistoryStatus, StudentAttendanceLesson, StudentAttendanceMetricSet, StudentAttendanceResponse, StudentAttendanceSeriesPoint, StudentHomework, StudentLessonType, StudentRequestBucket, StudentRequestDetail, StudentRequestPage, StudentSemesterSchedule, StudentStatisticsOverviewResponse, StudentStatisticsRankingResponse, StudentStatisticsSubjectDetailResponse, StudentToday } from '../../../mobile-core/src/api/types'
import { DEFAULT_PASSWORD_POLICY } from '../../../mobile-core/src/features/profile/profile-types'
import { notificationTypeIds, type NotificationItem } from '../../../mobile-core/src/features/notifications/notifications-v2-models'

export const previewDate = '2026-09-30'
export const previewNow = `${previewDate}T07:50:00Z`
const scenario = { id: 'web-visual-review', label: 'Главная студента', profile: 'normal' as const }
export const studentSession = { ...sessionFixture, serverNow: previewNow, _links: {} }
export const profile = {
  sessionId: sessionFixture.sessionId, userId: sessionFixture.user.id,
  displayName: 'Анна Смирнова', sessionVersion: '1', rolesVersion: '1',
  activeRole: 'STUDENT', readOnly: false, groupLabel: 'ИВТ-21',
  passwordPolicy: DEFAULT_PASSWORD_POLICY,
  roles: [{ grantId: '1', role: 'STUDENT', status: 'ACTIVE', selectable: true, readOnly: false, groupId: '17' }],
}
const sourceToday = createTodayReviewScenarios(Date.parse(previewNow)).find((item) => item.today?.lessons.some((lesson) => lesson.checkinEligibility.allowed))?.today
if (!sourceToday) throw new Error('Existing today review fixture is unavailable')
export const today: StudentToday = {
  ...sourceToday, date: previewDate, serverNow: previewNow,
  lessons: sourceToday.lessons.map((lesson, index) => ({
    ...lesson,
    schedule: { ...lesson.schedule, id: String(301 + index), date: previewDate,
      subject: { ...lesson.schedule.subject, id: index === 1 ? 'programming' : lesson.schedule.subject.id } },
  })),
}
export const schedule: StudentSemesterSchedule = {
  semester: studentSession.semester,
  dateFrom: studentSession.semester.startsOn,
  dateTo: studentSession.semester.endsOn,
  lessons: today.lessons.map((lesson) => lesson.schedule),
  updatedAt: previewNow,
  _links: {},
}
export const requestOptions = reviewRequestOptions()
const requestSources = reviewRequests()
function requestExample(example: {
  source: number; id: string; createdAt: string; dates: readonly string[];
  status: StudentRequestDetail['summary']['status']; reason: StudentRequestDetail['reason'];
  comment: string; decision?: StudentRequestDetail['decision']; updatedAt?: string;
}): StudentRequestDetail {
  const source = requestSources[example.source]!
  return {
    ...source,
    summary: {
      ...source.summary, id: example.id, status: example.status, createdAt: example.createdAt,
      updatedAt: example.updatedAt ?? example.decision?.decidedAt ?? example.createdAt,
      lessons: source.summary.lessons.map((lesson, index) => ({
        ...lesson, id: String(Number(example.id) * 10 + index + 1), semesterId: studentSession.semester.id,
        date: example.dates[index] ?? example.dates[0]!,
      })),
    },
    reason: example.reason, comment: example.comment, decision: example.decision ?? null, attachments: [],
  }
}
const requestDetails: readonly StudentRequestDetail[] = [
  requestExample({
    source: 0, id: '7005', status: 'PENDING', createdAt: '2026-09-29T16:10:00Z',
    dates: ['2026-09-28', '2026-09-29'], reason: 'ILLNESS',
    comment: 'Пропустила два занятия по болезни. Прошу учесть уважительную причину.',
  }),
  requestExample({
    source: 2, id: '7004', status: 'PENDING', createdAt: '2026-09-29T12:20:00Z',
    dates: ['2026-09-29'], reason: null,
    comment: 'Была на практическом занятии, но не успела отметиться. Прошу проверить посещение.',
  }),
  requestExample({
    source: 1, id: '7003', status: 'APPROVED', createdAt: '2026-09-22T09:15:00Z',
    dates: ['2026-09-21', '2026-09-22'], reason: 'MEDICAL_EXAMINATION',
    comment: 'В эти дни проходила назначенное медицинское обследование.',
    decision: { comment: 'Уважительная причина подтверждена для обеих пар.', decidedAt: '2026-09-24T08:30:00Z' },
  }),
  requestExample({
    source: 2, id: '7002', status: 'REJECTED', createdAt: '2026-09-18T11:40:00Z',
    dates: ['2026-09-18'], reason: null,
    comment: 'Прошу повторно проверить отметку за практическое занятие.',
    decision: { comment: 'Посещение занятия не подтверждено.', decidedAt: '2026-09-21T07:20:00Z' },
  }),
  requestExample({
    source: 3, id: '7001', status: 'CANCELLED', createdAt: '2026-09-14T10:05:00Z', updatedAt: '2026-09-16T13:30:00Z',
    dates: ['2026-09-14', '2026-09-15'], reason: 'FAMILY_CIRCUMSTANCES',
    comment: 'Обстоятельства изменились. Заявка больше не нужна, отменила её сама.',
  }),
]
export function requestPage(bucket: StudentRequestBucket, page: number, size: number): StudentRequestPage {
  const items = requestDetails.filter((item) => bucket === 'OPEN' ? item.summary.status === 'PENDING' : item.summary.status !== 'PENDING')
    .sort((a, b) => b.summary.updatedAt.localeCompare(a.summary.updatedAt))
  return { content: items.slice(page * size, (page + 1) * size).map((item) => item.summary),
    page, size, totalElements: items.length, totalPages: Math.ceil(items.length / size) }
}
export function requestDetail(id: string): StudentRequestDetail | undefined {
  return requestDetails.find((item) => item.summary.id === id)
}
const attendanceSource = attendanceFixture()
const attendanceSubjects: readonly { id: string; name: string; types: readonly StudentLessonType[] }[] = [
  { id: 'programming', name: 'Основы программирования', types: ['LECTURE', 'LAB'] },
  { id: 'math', name: 'Математический анализ', types: ['LECTURE', 'PRACTICE'] },
  { id: 'networks', name: 'Компьютерные сети', types: ['PRACTICE', 'LAB'] },
]
const previewClock = new Intl.DateTimeFormat('en-GB', { hour: '2-digit', minute: '2-digit', hourCycle: 'h23', timeZone: 'Europe/Moscow' }).format(new Date(previewNow))
const lessonStarts = ['08:30', '10:05', '11:40', '13:15']
const lessonEnds = ['09:50', '11:25', '13:00', '14:35']
// One synthetic lesson source supplies the journal, graph, totals and subject filter.
const attendanceDays: StudentAttendanceResponse['days'] = attendanceSource.graph.days.map((point, dayIndex) => {
  const date = point.dateFrom
  const closed: StudentAttendanceLesson['status'][] = [
    ...Array<StudentAttendanceLesson['status']>(point.metrics.present.count).fill('PRESENT'),
    ...Array<StudentAttendanceLesson['status']>(point.metrics.excused.count).fill('EXCUSED'),
    ...Array<StudentAttendanceLesson['status']>(point.metrics.absent.count).fill('ABSENT'),
  ]
  const lessons: StudentAttendanceLesson[] = Array.from({ length: point.metrics.planned }, (_, index) => {
    const startsAt = lessonStarts[index]!
    const endsAt = lessonEnds[index]!
    const subject = attendanceSubjects[(dayIndex + index) % attendanceSubjects.length]!
    const status = date > previewDate || (date === previewDate && startsAt > previewClock)
      ? 'FUTURE'
      : date === previewDate && endsAt > previewClock ? 'ACTIVE' : closed[index] ?? 'NO_DATA'
    return {
      id: `preview-attendance:${date}:${index + 1}`, date, number: String(index + 1),
      subject: { id: subject.id, name: subject.name },
      type: subject.types[Math.floor((dayIndex + index) / attendanceSubjects.length) % subject.types.length]!,
      status, schedule: { startsAt, endsAt, room: ['А-312', 'А-401', 'А-214', 'А-508'][index]! },
      explanation: status === 'EXCUSED' ? { reasonCode: 'ILLNESS', reasonText: 'Подтверждённая уважительная причина' } : null,
      requestOptions: status === 'ABSENT' ? [
        { id: 'excuse', kind: 'EXCUSE', label: 'Пропуск по уважительной', enabled: true },
        { id: 'late', kind: 'LATE_CHECKIN', label: 'Забыл отметиться', enabled: true },
      ] : [],
    }
  })
  return { date, dayNumber: String(civilDate(date).getUTCDate()), weekday: weekday(date),
    state: date < previewDate ? 'PAST' : date === previewDate ? 'CURRENT' : 'FUTURE', lessons }
})
const attendanceLessons = attendanceDays.flatMap((day) => day.lessons)
function attendanceMetrics(lessons: readonly StudentAttendanceLesson[]): StudentAttendanceMetricSet {
  const count = (status: StudentAttendanceLesson['status']) => lessons.filter((lesson) => lesson.status === status).length
  const present = count('PRESENT'), excused = count('EXCUSED'), absent = count('ABSENT')
  const held = present + excused + absent
  const value = (amount: number) => ({ count: amount, percent: held ? amount * 100 / held : null })
  return { present: value(present), excused: value(excused), absent: value(absent),
    presentOrExcused: value(present + excused), held, planned: lessons.length }
}
function attendanceSeries(points: readonly StudentAttendanceSeriesPoint[], lessons: readonly StudentAttendanceLesson[]): StudentAttendanceSeriesPoint[] {
  return points.map((point) => {
    const items = lessons.filter((lesson) => lesson.date >= point.dateFrom && lesson.date <= point.dateTo)
    const metrics = attendanceMetrics(items)
    return { ...point, metrics, state: metrics.held ? 'DATA' : items.some((lesson) => lesson.status === 'FUTURE' || lesson.status === 'ACTIVE') ? 'FUTURE' : 'NO_DATA' }
  })
}
function attendanceHistoryStatus(lesson: StudentAttendanceLesson): StudentAttendanceHistoryStatus {
  return lesson.status === 'ACTIVE' ? 'FUTURE' : lesson.status === 'CANCELLED' ? 'NO_DATA' : lesson.status
}
function attendanceTypeCard(type: StudentLessonType, lessons: readonly StudentAttendanceLesson[]) {
  const items = lessons.filter((lesson) => lesson.type === type)
  return { type, metrics: attendanceMetrics(items), history: items.map((lesson) => ({
    id: lesson.id, status: attendanceHistoryStatus(lesson), explanation: lesson.explanation ?? null,
  })) }
}
export const attendance: StudentAttendanceResponse = {
  semester: studentSession.semester, serverNow: previewNow, terminalReadOnly: false,
  metrics: attendanceMetrics(attendanceLessons), days: attendanceDays,
  subjects: attendanceSubjects.map((subject) => ({
    id: subject.id, name: subject.name,
    typeCards: subject.types.map((type) => attendanceTypeCard(type, attendanceLessons.filter((lesson) => lesson.subject.id === subject.id))),
  })),
  graph: {
    days: attendanceSeries(attendanceSource.graph.days, attendanceLessons),
    weeks: attendanceSeries(attendanceSource.graph.weeks, attendanceLessons),
  },
}
export const homework: StudentHomework = {
  ...homeworkFixture, serverNow: previewNow,
  items: [
    { ...homeworkFixture.items[0]!, lessonDate: previewDate, completed: false, completedAt: null, link: null },
    { ...homeworkFixture.items[1]!, lessonDate: previewDate },
    { ...homeworkFixture.items[0]!, id: '143', title: 'Подготовка к коллоквиуму', lessonDate: previewDate, link: null },
  ] as StudentHomework['items'],
}
export const overview: StudentStatisticsOverviewResponse = {
  metrics: attendance.metrics,
  ownRank: statisticsFixtureOverview(scenario).ownRank,
  semesterSeries: attendance.graph.weeks.map((point) => {
    // Explicit synthetic group: twenty students, 80% attendance in completed buckets.
    const denominator = point.metrics.held * 20
    const numerator = Math.round(denominator * 0.8)
    return { ...point, groupPresent: { numerator, denominator, percent: denominator ? numerator * 100 / denominator : null } }
  }),
  subjects: attendanceSubjects.map((subject) => ({
    id: subject.id, name: subject.name,
    metrics: attendanceMetrics(attendanceLessons.filter((lesson) => lesson.subject.id === subject.id)),
  })),
}
export function rankingPage(requestedPage: number | null, size: number): StudentStatisticsRankingResponse {
  const ownPosition = overview.ownRank.position ?? 4
  const total = overview.ownRank.participantCount
  const ownPercentage = overview.metrics.present.percent ?? 0
  const page = requestedPage ?? Math.floor((ownPosition - 1) / size)
  const rows = Array.from({ length: total }, (_, index) => {
    const position = index + 1
    const isSelf = position === ownPosition
    const percentage = position < ownPosition
      ? ownPercentage + (100 - ownPercentage) * (ownPosition - position) / ownPosition
      : isSelf ? ownPercentage : ownPercentage * (total - position) / (total - ownPosition)
    return { id: isSelf ? String(profile.userId) : String(1000 + position),
      name: isSelf ? profile.displayName : `Участник ${String(position).padStart(2, '0')}`,
      position, percentage, isSelf }
  })
  return { available: true, page, size, total, ownPosition, rows: rows.slice(page * size, (page + 1) * size) }
}
export function subjectDetail(id: string, range: 'days' | 'weeks', types: string[]): StudentStatisticsSubjectDetailResponse {
  const subject = attendanceSubjects.find((item) => item.id === id)
  if (subject) {
    const lessons = attendanceLessons.filter((lesson) => lesson.subject.id === id)
    const selectedTypes = subject.types.filter((type) => !types.length || types.includes(type))
    const selected = lessons.filter((lesson) => selectedTypes.includes(lesson.type))
    return { subjectId: id, name: subject.name, availableTypes: subject.types, selectedTypes,
      selectedAggregate: attendanceMetrics(selected),
      series: attendanceSeries(attendanceSource.graph[range], selected),
      typeCards: selectedTypes.map((type) => attendanceTypeCard(type, lessons)) }
  }
  return statisticsFixtureDetail(scenario, id, range, types)
}
export function notification(id: string, heading = 'Опубликовано домашнее задание'): NotificationItem {
  return {
    id, type: 'HOMEWORK_PUBLISHED', heading,
    summary: 'Основы программирования · Практика по алгоритмам. Задание доступно в разделе домашних заданий.',
    sentAt: previewNow, orderAt: previewNow, readAt: null, revision: '1',
    details: [{ label: 'Предмет', value: 'Основы программирования' }, { label: 'Преподаватель', value: 'Иванова М. А.' }],
    action: { kind: 'homework', role: 'STUDENT', homeworkId: '142', lessonDate: previewDate },
  }
}
export const initialNotifications: NotificationItem[] = [
  notification('103'),
  { ...notification('102', 'Изменилась аудитория следующей пары'), type: 'LESSON_UPDATED', summary: 'Компьютерные сети · вместо А-214 занятие пройдёт в А-508.', action: null },
  { ...notification('101', 'Отметка посещаемости подтверждена'), type: 'ATTENDANCE_MARKED_BY_HEADMAN', summary: 'Математический анализ · присутствовал.', readAt: previewNow, action: null },
]
export const preferences = {
  revision: '1', splitChannels: false, telegramLinked: false, mutedUntil: null,
  types: Object.fromEntries(notificationTypeIds.map((id) => [id, { appPush: true, telegram: false, colorId: 'violet' }])),
}
