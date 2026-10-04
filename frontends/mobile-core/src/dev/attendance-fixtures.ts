import type { AttendanceGraphPoint, AttendanceGraphRange, AttendanceLesson, AttendanceMetricSet, AttendanceMode, AttendanceReadState, AttendanceViewModel } from '../features/attendance/attendance-view-model'
import { shiftDate } from '../features/attendance/attendance-periods'

export interface AttendanceReviewScenario { readonly id: string; readonly label: string; readonly state: AttendanceReadState<AttendanceViewModel>; readonly date?: string; readonly mode?: AttendanceMode; readonly range?: AttendanceGraphRange; readonly expandedSubjectId?: string; readonly actionLessonId?: string; readonly terminal?: boolean; readonly enlargedText?: boolean }
export function fixtureMetrics(present: number, excused: number, absent: number, planned = present + excused + absent): AttendanceMetricSet {
  const held = present + excused + absent
  const value = (count: number) => ({ count, percent: held ? count / held * 100 : null })
  return { present: value(present), excused: value(excused), absent: value(absent), presentOrExcused: value(present + excused), held, planned }
}
function lesson(id: string, subject: string, type: AttendanceLesson['type'], status: AttendanceLesson['status'], startsAt: string, endsAt: string, room: string): AttendanceLesson {
  return { id, date: '2026-09-01', number: id, subject: { id, name: subject }, type, status, schedule: { startsAt, endsAt, room }, requestOptions: status === 'ABSENT' ? [{ id: 'excuse', kind: 'EXCUSE', label: 'Пропуск по уважительной', enabled: true }, { id: 'late', kind: 'LATE_CHECKIN', label: 'Забыл отметиться', enabled: true }] : [] }
}
export function attendanceFixture(): AttendanceViewModel {
  const lessons = [lesson('math', 'Математический анализ', 'PRACTICE', 'PRESENT', '08:30', '09:50', 'А-312'), lesson('programming', 'Основы программирования', 'LECTURE', 'ABSENT', '10:05', '11:25', 'А-401'), lesson('networks', 'Компьютерные сети', 'LAB', 'EXCUSED', '11:40', '13:00', 'А-214')]
  const days: AttendanceGraphPoint[] = []
  const weeks: AttendanceGraphPoint[] = []
  let completedDay = 0
  let markIndex = 0
  // Synthetic server aggregates, mathematically consistent with synthetic daily counts.
  for (let week = 0; week < 17; week++) {
    const monday = shiftDate('2026-08-31', week * 7)
    let present = 0; let excused = 0; let absent = 0; let planned = 0
    for (let day = 0; day < 7; day++) {
      const date = shiftDate(monday, day)
      if (date < '2026-09-01' || date > '2026-12-27' || day === 6) continue
      const future = date > '2026-10-04'
      let dailyPresent = 0; let dailyExcused = 0; let dailyAbsent = 0
      if (!future) {
        const count = completedDay++ < 13 ? 4 : 3
        for (let mark = 0; mark < count; mark++) {
          const index = markIndex++
          if (index % 10 === 0) dailyExcused++
          else if (index % 5 === 2 && index !== 92 && index !== 97) dailyAbsent++
          else dailyPresent++
        }
      }
      const counts = future ? fixtureMetrics(0, 0, 0, 3) : fixtureMetrics(dailyPresent, dailyExcused, dailyAbsent)
      present += counts.present.count; excused += counts.excused.count; absent += counts.absent.count; planned += counts.planned
      days.push({ id: `day-${date}`, label: date, dateFrom: date, dateTo: date, state: future ? 'FUTURE' : 'DATA', metrics: counts })
    }
    weeks.push({ id: `week-${monday}`, label: monday, dateFrom: monday, dateTo: shiftDate(monday, 6), state: monday > '2026-10-04' ? 'FUTURE' : 'DATA', metrics: fixtureMetrics(present, excused, absent, planned) })
  }
  return { semester: { id: 'semester', name: 'Осенний семестр', dateFrom: '2026-09-01', dateTo: '2026-12-27' }, serverNow: '2026-10-04T12:00:00Z', metrics: fixtureMetrics(72, 10, 18, weeks.reduce((total, point) => total + point.metrics.planned, 0)), days: [{ date: '2026-09-01', dayNumber: '1', weekday: 'Вт', state: 'PAST', lessons }], subjects: [{ id: 'programming', name: 'Основы программирования', typeCards: [{ type: 'LECTURE', metrics: fixtureMetrics(5, 1, 2, 12), history: ['PRESENT', 'PRESENT', 'ABSENT', 'PRESENT', 'EXCUSED', 'PRESENT', 'PRESENT', 'ABSENT', 'FUTURE', 'FUTURE', 'FUTURE', 'FUTURE'].map((status, index) => ({ id: `l${index}`, status: status as 'PRESENT' | 'ABSENT' | 'EXCUSED' | 'FUTURE' })) }, { type: 'LAB', metrics: fixtureMetrics(3, 1, 1, 8), history: ['PRESENT', 'EXCUSED', 'PRESENT', 'ABSENT', 'PRESENT', 'FUTURE', 'FUTURE', 'FUTURE'].map((status, index) => ({ id: `b${index}`, status: status as 'PRESENT' | 'ABSENT' | 'EXCUSED' | 'FUTURE' })) }] }, { id: 'math', name: 'Математический анализ', typeCards: [] }, { id: 'networks', name: 'Компьютерные сети', typeCards: [] }], graph: { days, weeks } }
}
export function createAttendanceReviewScenarios(): readonly AttendanceReviewScenario[] {
  const data = attendanceFixture()
  const ready = (model = data): AttendanceReadState<AttendanceViewModel> => ({ status: 'ready', data: model })
  const base = { state: ready(), date: '2026-09-01' }
  const sparseDays = [data.graph.days[0]!, data.graph.days[2]!]
  const sparseMetrics = fixtureMetrics(sparseDays.reduce((sum, point) => sum + point.metrics.present.count, 0), sparseDays.reduce((sum, point) => sum + point.metrics.excused.count, 0), sparseDays.reduce((sum, point) => sum + point.metrics.absent.count, 0))
  const sparse = { ...data, semester: { ...data.semester!, dateTo: '2026-09-13' }, serverNow: '2026-09-04T12:00:00Z', metrics: sparseMetrics, graph: { days: sparseDays, weeks: [{ ...data.graph.weeks[0]!, metrics: sparseMetrics }] } }
  const zero: AttendanceGraphPoint = { id: 'zero', label: '0', dateFrom: '2026-09-01', dateTo: '2026-09-01', state: 'DATA', metrics: fixtureMetrics(0, 0, 3) }
  const graphCase = (id: string, label: string, model: AttendanceViewModel, range: AttendanceGraphRange = 'days'): AttendanceReviewScenario => ({ id, label, state: ready(model), mode: 'graph', range, date: '2026-09-01' })
  return [
    { id: 'attendance-days', label: 'По дням', ...base }, { id: 'attendance-subjects-collapsed', label: 'Предметы свёрнуты', ...base, mode: 'subjects' }, { id: 'attendance-subjects-expanded', label: 'Предмет раскрыт', ...base, mode: 'subjects', expandedSubjectId: 'programming' },
    graphCase('attendance-graph-days', 'График по дням', data), graphCase('attendance-graph-weeks', 'График по неделям', data, 'weeks'), { id: 'attendance-actions', label: 'Действия по пропуску', ...base, actionLessonId: 'programming' }, { id: 'attendance-empty-day', label: 'Пустой день', ...base, date: '2026-09-05' },
    { id: 'attendance-request-context', label: 'Переход к форме из посещаемости', ...base, actionLessonId: 'programming' },
    { id: 'attendance-loading', label: 'Загрузка', state: { status: 'loading' } }, { id: 'attendance-error', label: 'Ошибка', state: { status: 'error', code: '503', message: 'Сервис временно недоступен.', retryable: true } }, { id: 'attendance-offline', label: 'Офлайн', state: { status: 'offline' } }, { id: 'attendance-read-only', label: 'Только чтение', ...base, terminal: true }, { id: 'attendance-no-semester', label: 'Нет семестра', state: { status: 'no-semester' } }, { id: 'attendance-forbidden', label: 'Посещаемость недоступна', state: { status: 'forbidden', reason: 'У тебя сейчас нет доступа к этому разделу.' } },
    graphCase('attendance-sparse-days', 'Редкие дни и пробелы', sparse), graphCase('attendance-sparse-weeks', 'Редкие недели и пробелы', sparse, 'weeks'), graphCase('attendance-one-point', 'Одна точка', { ...sparse, metrics: data.graph.days[0]!.metrics, graph: { days: [data.graph.days[0]!], weeks: [] } }), graphCase('attendance-zero', 'Настоящие 0%', { ...sparse, metrics: zero.metrics, graph: { days: [zero], weeks: [] } }), graphCase('attendance-held-zero', 'Нет закрытых пар', { ...sparse, metrics: fixtureMetrics(0, 0, 0, 2), graph: { days: [{ ...zero, metrics: fixtureMetrics(0, 0, 0, 2) }], weeks: [] } }), graphCase('attendance-no-data', 'Нет отметок', { ...sparse, metrics: fixtureMetrics(0, 0, 0, 2), graph: { days: [{ ...zero, state: 'NO_DATA', metrics: fixtureMetrics(0, 0, 0, 2) }], weeks: [] } }), graphCase('attendance-all-future', 'Все периоды будущие', { ...data, metrics: fixtureMetrics(0, 0, 0, data.metrics.planned), serverNow: '2026-08-01T12:00:00Z', graph: { days: [], weeks: [] } }),
    { id: 'attendance-sunday', label: 'Воскресная пара', ...base, date: '2026-09-06', state: ready({ ...data, days: [...data.days, { date: '2026-09-06', weekday: 'Вс', dayNumber: '6', state: 'PAST', lessons: [{ ...data.days[0]!.lessons[0]!, id: 'sunday', date: '2026-09-06' }] }] }) },
    { id: 'attendance-long-text', label: 'Длинные тексты', ...base, state: ready({ ...data, days: data.days.map((day) => ({ ...day, lessons: day.lessons.map((item) => ({ ...item, subject: { ...item.subject, name: 'Проектирование распределённых информационных систем и высоконагруженных приложений' }, schedule: { ...item.schedule, room: 'Учебно-лабораторный корпус, аудитория А-401' } })) })) }) }, { id: 'attendance-root20', label: 'Увеличенный текст', ...base, enlargedText: true },
  ]
}
