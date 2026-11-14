import type {
  LessonSchedule,
  StudentHomework,
  StudentSemesterSchedule,
  StudentToday,
  TodayLesson,
} from '../../../mobile-core/src/api/types'
import {
  civilDate,
  monday,
  shiftDate,
  weekday,
} from '../../../mobile-core/src/features/attendance/attendance-periods'
import type {
  AttendanceGraphPoint,
  AttendanceHistoryStatus,
  AttendanceLesson,
  AttendanceMetricSet,
  AttendanceViewModel,
} from '../../../mobile-core/src/features/attendance/attendance-view-model'
import type {
  StatisticsOverviewData,
  StatisticsSubjectDetailData,
} from '../../../mobile-core/src/features/statistics/statistics-view-model'
import {
  DEFAULT_PASSWORD_POLICY,
  type ProfileRole,
  type ProfileSnapshot,
} from '../../../mobile-core/src/features/profile/profile-types'
import {
  demoDate, group, lessonId, markedAt, notificationHomeworkDate, notificationHomeworkId,
  notificationHomeworkTitle, physicalLessonId, roles, room, scheduleDates,
  student, subject, type DemoSnapshot,
} from './model'

export { demoDate }
export const serverNow = markedAt
const semester = {
  id: 'demo-semester',
  name: 'Осень 2026',
  startsOn: '2026-09-01',
  endsOn: '2026-12-27',
}
const interval = { dateFrom: semester.startsOn, dateTo: semester.endsOn }
const programmingId = 'demo-subject-programming'

function lesson(
  subjectId: string,
  number: number,
  name: string,
  type: LessonSchedule['subject']['type'],
  start: string,
  end: string,
  classroom: string,
  status: LessonSchedule['status'],
): LessonSchedule {
  return {
    id: physicalLessonId(demoDate, number),
    date: demoDate,
    lessonNumber: number,
    startsAt: start + ':00',
    endsAt: end + ':00',
    status,
    subject: { id: subjectId, name, type },
    room: { current: classroom, previous: null, changeState: 'UNCHANGED' },
  }
}
const day = [
  lesson('demo-math', 1, 'Математический анализ', 'LECTURE', '08:30', '09:50', 'А-312', 'CLOSED'),
  lesson(programmingId, 2, subject, 'PRACTICE', '10:05', '11:25', room, 'ACTIVE'),
  lesson('demo-networks', 3, 'Компьютерные сети', 'LAB', '11:40', '13:00', 'Б-214', 'PLANNED'),
  lesson(
    'demo-algorithms',
    4,
    'Алгоритмы и структуры данных',
    'PRACTICE',
    '13:30',
    '14:50',
    'А-407',
    'PLANNED',
  ),
  lesson('demo-databases', 5, 'Базы данных', 'LECTURE', '15:05', '16:25', 'В-203', 'PLANNED'),
]
const dates = scheduleDates
export const schedule: StudentSemesterSchedule = {
  semester,
  ...interval,
  updatedAt: serverNow,
  _links: {},
  lessons: dates.flatMap((date) =>
    date === demoDate
      ? day
      : day.map((item) => ({
          ...item,
          date,
          id: physicalLessonId(date, item.lessonNumber),
          status: date < demoDate ? ('CLOSED' as const) : ('PLANNED' as const),
        })),
  ),
}

export function scheduleFor(snapshot: DemoSnapshot): StudentSemesterSchedule {
  if (snapshot.scenario !== 'cancelled') return schedule
  return {
    ...schedule,
    lessons: schedule.lessons.map(item =>
      item.id === lessonId ? { ...item, status: 'CANCELLED' } : item,
    ),
  }
}

export function lessonsForDate(snapshot: DemoSnapshot, date = snapshot.date) {
  return scheduleFor(snapshot).lessons.filter(item => item.date === date)
}

export function selectedDemoLesson(snapshot: DemoSnapshot) {
  const lessons = lessonsForDate(snapshot)
  return lessons.find(item => item.id === snapshot.selectedLesson) ?? lessons[0] ?? null
}

type ProjectionStatus = Extract<
  AttendanceHistoryStatus,
  'PRESENT' | 'ABSENT' | 'EXCUSED' | 'NO_DATA' | 'FUTURE'
>
export function statusFor(item: LessonSchedule, marked: boolean): ProjectionStatus {
  if (item.status === 'PLANNED') return 'FUTURE'
  if (item.status === 'CANCELLED') return 'NO_DATA'
  if (item.status === 'ACTIVE') return marked ? 'PRESENT' : 'NO_DATA'
  if (item.date === '2026-10-19' && item.lessonNumber === 4) return 'ABSENT'
  if (item.date === '2026-10-26' && item.lessonNumber === 3) return 'ABSENT'
  if (item.date === '2026-11-02' && item.lessonNumber === 2) return 'EXCUSED'
  return 'PRESENT'
}

export function today(snapshot: DemoSnapshot): StudentToday {
  const marked = snapshot.markState !== 'ready'
  const lessons: TodayLesson[] = lessonsForDate(snapshot)
    .map((item) => {
      const active = item.id === lessonId
      const status = statusFor(item, marked)
      const known = status === 'PRESENT' || status === 'ABSENT' || status === 'EXCUSED'
      return {
        schedule: item,
        attendance: known
          ? {
              status,
              source: active ? 'STUDENT_GEO' : 'HEADMAN',
              markedAt: active ? markedAt : item.date + 'T05:35:00Z',
            }
          : null,
        request: active && snapshot.scenario === 'pending'
          ? { id: 'demo-geo-request', status: 'PENDING', origin: 'AUTO_GEO_FAILURE', resolutionReason: null }
          : null,
        checkinEligibility:
          status === 'PRESENT'
            ? { allowed: false, reason: 'ALREADY_PRESENT', retryAt: null }
            : item.status === 'CANCELLED'
              ? { allowed: false, reason: 'LESSON_CANCELLED', retryAt: null }
              : active && snapshot.scenario === 'pending'
                ? { allowed: false, reason: 'PENDING_CONFIRMATION', retryAt: null }
                : active
                  ? { allowed: true, reason: 'ELIGIBLE', retryAt: null }
              : {
                  allowed: false,
                  reason: item.status === 'CLOSED' ? 'WINDOW_CLOSED' : 'TOO_EARLY',
                  retryAt: null,
                },
      }
    })
  return { date: snapshot.date, serverNow, timeZone: 'Europe/Moscow', lessons, _links: {} }
}
const assignments = [
  {
    id: 'demo-assignment',
    lesson: day[1]!,
    title: 'Напиши функцию двоичного поиска',
    description:
      'Проверь функцию на пустом списке, одном элементе и списке с повторяющимися числами.',
  },
  {
    id: 'demo-network-assignment',
    lesson: day[2]!,
    title: 'Составь схему сети учебной аудитории',
    description:
      'Покажи коммутатор, шлюз и адреса трёх рабочих станций. Объясни выбор маски подсети.',
  },
  {
    id: 'demo-algorithm-assignment',
    lesson: day[3]!,
    title: 'Сравни две сортировки',
    description:
      'Запусти сортировку вставками и слиянием для трёх размеров массива. Запиши, как менялось время.',
  },
  {
    id: 'demo-db-assignment',
    lesson: day[4]!,
    title: 'Спроектируй таблицы для учебной библиотеки',
    description: 'Добавь книги, читателей и выдачи. Укажи первичные и внешние ключи.',
  },
]
const weekAssignment = {
  ...assignments[0]!,
  id: 'demo-assignment-10',
  lesson: schedule.lessons.find(item => item.id === physicalLessonId('2026-11-10', 2))!,
  title: 'Проверь граничные случаи двоичного поиска',
}

const notificationAssignment = {
  ...assignments[0]!,
  id: notificationHomeworkId,
  lesson: schedule.lessons.find(item => item.id === physicalLessonId(notificationHomeworkDate, 2))!,
  title: notificationHomeworkTitle,
}

export function homework(completed: ReadonlySet<string>, snapshot: DemoSnapshot): StudentHomework {
  return {
    semester: { id: semester.id, name: semester.name, ...interval },
    from: interval.dateFrom,
    to: interval.dateTo,
    serverNow,
    items: [...assignments, weekAssignment, notificationAssignment].filter(item =>
      snapshot.scenario !== 'cancelled' || item.lesson.id !== lessonId,
    ).map((item) => ({
      id: item.id,
      subject: { id: item.lesson.subject.id, name: item.lesson.subject.name },
      title: item.title,
      description: item.description,
      link: null,
      lessonDate: item.lesson.date,
      lessonNumber: item.lesson.lessonNumber,
      bindingMode: 'LESSON',
      archived: false,
      completed: completed.has(item.id),
      completedAt: completed.has(item.id) ? serverNow : null,
    })),
  }
}

function metrics(lessons: readonly LessonSchedule[], marked: boolean): AttendanceMetricSet {
  const statuses = lessons.filter(item => item.status === 'CLOSED').map(item => statusFor(item, marked))
  const present = statuses.filter((status) => status === 'PRESENT').length
  const excused = statuses.filter((status) => status === 'EXCUSED').length
  const absent = statuses.filter((status) => status === 'ABSENT').length
  const held = present + excused + absent
  const metric = (count: number) => ({ count, percent: held ? (100 * count) / held : null })
  return {
    present: metric(present),
    presentOrExcused: metric(present + excused),
    excused: metric(excused),
    absent: metric(absent),
    held,
    planned: lessons.filter(item => item.status !== 'CANCELLED').length,
  }
}
function series(
  lessons: readonly LessonSchedule[],
  marked: boolean,
  byDay: boolean,
): AttendanceGraphPoint[] {
  const periods = new Map<string, LessonSchedule[]>()
  for (const item of lessons) {
    const start = byDay ? item.date : monday(item.date)
    const entries = periods.get(start)
    if (entries) entries.push(item)
    else periods.set(start, [item])
  }
  return [...periods].map(([dateFrom, entries]) => {
    const values = metrics(entries, marked)
    return {
      id: dateFrom,
      dateFrom,
      dateTo: byDay ? dateFrom : shiftDate(dateFrom, 6),
      label: civilDate(dateFrom).toLocaleDateString('ru-RU', {
        day: 'numeric',
        month: 'short',
        timeZone: 'UTC',
      }),
      state: values.held ? 'DATA' : dateFrom > demoDate ? 'FUTURE' : 'NO_DATA',
      metrics: values,
    }
  })
}
function history(lessons: readonly LessonSchedule[], marked: boolean) {
  return lessons.map((item) => ({ id: item.id, status: statusFor(item, marked) }))
}
function subjectLessons(id: string, snapshot: DemoSnapshot) {
  return scheduleFor(snapshot).lessons.filter((item) => item.subject.id === id)
}
export function attendance(snapshot: DemoSnapshot): AttendanceViewModel {
  const marked = snapshot.markState !== 'ready'
  const lessons = scheduleFor(snapshot).lessons
  return {
    semester: { id: semester.id, name: semester.name, ...interval },
    serverNow,
    metrics: metrics(lessons, marked),
    days: [...new Set([...dates, snapshot.date])].sort().map((date) => ({
      date,
      weekday: weekday(date),
      dayNumber: String(civilDate(date).getUTCDate()),
      state: date < demoDate ? 'PAST' : date === demoDate ? 'CURRENT' : 'FUTURE',
      lessons: lessons
        .filter((item) => item.date === date)
        .map((item): AttendanceLesson => {
          const status = statusFor(item, marked)
          return {
            id: item.id,
            date,
            number: String(item.lessonNumber),
            subject: item.subject,
            type: item.subject.type,
            schedule: {
              startsAt: item.startsAt.slice(0, 5),
              endsAt: item.endsAt.slice(0, 5),
              room: item.room.current,
            },
            status: item.status === 'CANCELLED'
              ? 'CANCELLED'
              : item.status === 'ACTIVE' && status === 'NO_DATA' ? 'ACTIVE' : status,
            requestOptions: [],
          }
        }),
    })),
    subjects: day.map(({ subject: info }) => {
      const lessons = subjectLessons(info.id, snapshot)
      return {
        id: info.id,
        name: info.name,
        typeCards: [
          { type: info.type, metrics: metrics(lessons, marked), history: history(lessons, marked) },
        ],
      }
    }),
    graph: {
      days: series(lessons, marked, true),
      weeks: series(lessons, marked, false),
    },
  }
}
export function statistics(snapshot: DemoSnapshot, byDay: boolean): StatisticsOverviewData {
  const marked = snapshot.markState !== 'ready'
  const lessons = scheduleFor(snapshot).lessons
  return {
    metrics: metrics(lessons, marked),
    ownRank: { available: false, position: null, participantCount: 0 },
    semesterSeries: series(lessons, marked, byDay),
    subjects: day.map(({ subject: info }) => ({
      id: info.id,
      name: info.name,
      metrics: metrics(subjectLessons(info.id, snapshot), marked),
    })),
  }
}
export function detail(
  snapshot: DemoSnapshot,
  byDay: boolean,
  id: string | null,
  typeSelected: boolean,
): StatisticsSubjectDetailData {
  const marked = snapshot.markState !== 'ready'
  const info = day.find((item) => item.subject.id === id)?.subject ?? day[1]!.subject
  const lessons = subjectLessons(info.id, snapshot)
  const values = metrics(lessons, marked)
  return {
    subjectId: info.id,
    name: info.name,
    availableTypes: [info.type],
    selectedTypes: typeSelected ? [info.type] : [],
    selectedAggregate: typeSelected ? values : metrics([], marked),
    series: typeSelected ? series(lessons, marked, byDay) : [],
    typeCards: [{ type: info.type, metrics: values, history: history(lessons, marked) }],
  }
}
const people: Record<ProfileRole, string> = {
  STUDENT: student,
  HEADMAN: 'Мария Крылова',
  TEACHER: 'Алексей Миронов',
  ADMIN: 'Виктория Морозова',
}
export function profile(role: ProfileRole): ProfileSnapshot {
  return {
    sessionId: 'demo-in-memory',
    userId: 'demo-person',
    displayName: people[role],
    ...(role === 'STUDENT' || role === 'HEADMAN' ? { groupLabel: group } : {}),
    sessionVersion: 'demo',
    rolesVersion: 'demo',
    activeRole: role,
    readOnly: false,
    passwordPolicy: DEFAULT_PASSWORD_POLICY,
    telegramId: null,
    roles: roles.map((item) => ({
      grantId: 'demo-' + item.id,
      role: item.id,
      status: 'ACTIVE',
      selectable: true,
      readOnly: false,
    })),
  }
}
