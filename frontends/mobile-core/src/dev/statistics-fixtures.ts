import type {
  StudentAttendanceHistorySegment, StudentAttendanceMetricSet, StudentLessonType,
  StudentStatisticsOverviewResponse, StudentStatisticsSeriesPoint, StudentStatisticsSubjectDetailResponse,
} from '../api/types'

type Range = 'days' | 'weeks'
type Profile = 'normal' | 'zero' | 'sparse' | 'future' | 'empty'
interface Lesson {
  readonly id: string
  readonly type: StudentLessonType
  readonly date: string
  readonly status: StudentAttendanceHistorySegment['status']
}
export interface StatisticsReviewScenario {
  readonly id: string
  readonly label: string
  readonly subject?: boolean
  readonly subjectName?: string
  readonly availableTypes?: readonly StudentLessonType[]
  readonly initialTypes?: readonly StudentLessonType[]
  readonly initialRange?: Range
  readonly profile?: Profile
  readonly rankUnavailable?: boolean
  readonly longName?: boolean
  readonly longHistory?: boolean
  readonly longLabels?: boolean
  readonly enlargedText?: boolean
  readonly offline?: boolean
  readonly unavailable?: 'semester' | 'session'
  readonly overviewLoading?: boolean
  readonly overviewError?: boolean
  readonly forbidden?: boolean
  readonly detailLoading?: boolean
  readonly detailError?: boolean
  readonly filterError?: boolean
  readonly filterLoading?: boolean
  readonly ownerRace?: boolean
}
export const STATISTICS_FIXTURE_TYPES = ['LECTURE', 'PRACTICE', 'LAB'] as const
export const STATISTICS_FIXTURE_SUBJECT = 'subject/analysis:opaque'
const start = '2026-08-31'
function dateAt(offset: number): string {
  const date = new Date(start + 'T00:00:00Z')
  date.setUTCDate(date.getUTCDate() + offset)
  return date.toISOString().slice(0, 10)
}
function metric(lessons: readonly Lesson[]): StudentAttendanceMetricSet {
  const count = (status: Lesson['status']): number => lessons.filter((lesson) => lesson.status === status).length
  const present = count('PRESENT'), excused = count('EXCUSED'), absent = count('ABSENT')
  const held = present + excused + absent
  const value = (amount: number) => ({ count: amount, percent: held ? Math.round(amount * 10000 / held) / 100 : null })
  return { present: value(present), excused: value(excused), absent: value(absent),
    presentOrExcused: value(present + excused), held, planned: lessons.length }
}
function source(scenario: StatisticsReviewScenario): Lesson[] {
  if (scenario.profile === 'empty') return []
  const types = scenario.availableTypes ?? STATISTICS_FIXTURE_TYPES
  const result: Lesson[] = []
  const length = scenario.longHistory ? 70 : 14
  const patterns: readonly (readonly Lesson['status'][])[] = [
    ['PRESENT', 'PRESENT', 'ABSENT', 'PRESENT', 'EXCUSED', 'PRESENT', 'PRESENT'],
    ['ABSENT', 'PRESENT', 'EXCUSED', 'PRESENT', 'PRESENT', 'ABSENT', 'PRESENT'],
    ['PRESENT', 'EXCUSED', 'PRESENT', 'ABSENT', 'PRESENT', 'PRESENT', 'ABSENT'],
  ]
  for (const type of types) {
    const typeIndex = STATISTICS_FIXTURE_TYPES.indexOf(type)
    for (let index = 0; index < length; index += 1) {
      const dayOffset = index * 3 + typeIndex % 2
      if (scenario.profile === 'sparse' && (dayOffset >= 14 && dayOffset < 21 || dayOffset >= 28 && dayOffset < 35)) continue
      let status = patterns[typeIndex]![index % 7]!
      if (scenario.profile === 'zero') status = index < 6 ? 'NO_DATA' : 'FUTURE'
      else if (scenario.profile === 'future') status = index < 6 ? 'NO_DATA' : 'FUTURE'
      else if (index >= length - 2) status = 'FUTURE'
      else if (index === 5 || index === 6) status = 'ABSENT' // CLOSED with missing mark is held and absent.
      result.push({ id: 'lesson:' + type + ':' + index, type, date: dateAt(dayOffset), status })
    }
  }
  return result
}
function buckets(lessons: readonly Lesson[], range: Range, scenario: StatisticsReviewScenario): StudentStatisticsSeriesPoint[] {
  if (scenario.profile === 'empty') return []
  const result: StudentStatisticsSeriesPoint[] = []
  const end = scenario.longHistory ? 210 : 42
  const step = range === 'days' ? 1 : 7
  for (let offset = 0; offset < end; offset += step) {
    const dateFrom = dateAt(offset), dateTo = dateAt(Math.min(end - 1, offset + step - 1))
    const items = lessons.filter((lesson) => lesson.date >= dateFrom && lesson.date <= dateTo)
    // Server projection emits only buckets with scheduled lessons. IDs remain opaque.
    if (items.length === 0) continue
    const metrics = metric(items)
    const state = metrics.held ? 'DATA' : items.some((lesson) => lesson.status === 'FUTURE') ? 'FUTURE' : 'NO_DATA'
    const period = range === 'days' ? dateFrom : dateFrom + '–' + dateTo
    const text = scenario.longLabels ? 'Учебный период ' + period : period
    result.push({ id: 'opaque-point:' + result.length + ':' + range, label: text, dateFrom, dateTo, state, metrics })
  }
  return result
}
function subjectName(scenario: StatisticsReviewScenario, owner: number): string {
  if (owner > 0) return 'Другой студент · дискретная математика'
  return scenario.longName
    ? 'Математический анализ и методы исследования сложных информационно-телекоммуникационных систем'
    : scenario.subjectName ?? (scenario.subject ? 'Компьютерные сети' : 'Математический анализ')
}
function fixtureSubjects(scenario: StatisticsReviewScenario, owner: number) {
  if (scenario.profile === 'empty') return []
  if (owner > 0) return [{ id: STATISTICS_FIXTURE_SUBJECT, name: subjectName(scenario, owner), types: ['PRACTICE'] as readonly StudentLessonType[] }]
  return [
    { id: STATISTICS_FIXTURE_SUBJECT, name: subjectName(scenario, owner), types: scenario.availableTypes ?? (scenario.subject ? STATISTICS_FIXTURE_TYPES : ['PRACTICE'] as readonly StudentLessonType[]) },
    { id: 'subject:networks', name: 'Компьютерные сети', types: STATISTICS_FIXTURE_TYPES },
    { id: 'subject:culture', name: 'Физическая культура', types: ['PRACTICE'] as readonly StudentLessonType[] },
    { id: 'subject:programming', name: 'Основы программирования', types: ['LECTURE', 'LAB'] as readonly StudentLessonType[] },
  ]
}
function subjectLessons(scenario: StatisticsReviewScenario, subjectId: string, types: readonly StudentLessonType[]): Lesson[] {
  return source({ ...scenario, availableTypes: types }).map((lesson) => ({ ...lesson, id: subjectId + ':' + lesson.id }))
}
export function statisticsFixtureOverview(scenario: StatisticsReviewScenario, owner = 0): StudentStatisticsOverviewResponse {
  const subjects = fixtureSubjects(scenario, owner).map((subject) => ({
    ...subject, lessons: subjectLessons(scenario, subject.id, subject.types),
  }))
  const lessons = subjects.flatMap((subject) => subject.lessons)
  const metrics = metric(lessons)
  return {
    metrics,
    ownRank: { available: !scenario.rankUnavailable && metrics.held > 0, position: scenario.rankUnavailable || !metrics.held ? null : owner > 0 ? 9 : 4, participantCount: scenario.profile === 'empty' ? 0 : 24 },
    semesterSeries: buckets(lessons, 'weeks', scenario),
    subjects: subjects.map(({ id, name, lessons: items }) => ({ id, name, metrics: metric(items) })),
  }
}
export function statisticsFixtureDetail(
  scenario: StatisticsReviewScenario, subjectId: string, range: Range, requestedTypes: readonly string[], owner = 0,
): StudentStatisticsSubjectDetailResponse {
  const subject = fixtureSubjects(scenario, owner).find((item) => item.id === subjectId)
  const availableTypes = subject?.types ?? []
  // Production API treats omitted types and [] as ALL and returns only selected typeCards.
  const selectedTypes = availableTypes.filter((type) => requestedTypes.length === 0 || requestedTypes.includes(type))
  const lessons = subjectLessons(scenario, subjectId, availableTypes)
  const selected = lessons.filter((lesson) => selectedTypes.includes(lesson.type))
  return {
    subjectId, name: subject?.name ?? subjectName(scenario, owner), availableTypes, selectedTypes,
    selectedAggregate: metric(selected), series: buckets(selected, range, scenario),
    typeCards: selectedTypes.map((type) => {
      const items = lessons.filter((lesson) => lesson.type === type)
      return { type, metrics: metric(items), history: items.map(({ id, status }) => ({ id, status })) }
    }),
  }
}

export function createStatisticsReviewScenarios(): readonly StatisticsReviewScenario[] {
  return [
    { id: 'statistics-overview', label: 'Обзор семестра' },
    { id: 'subject-all', label: 'Предмет · все три типа', subject: true },
    { id: 'subject-lecture', label: 'Предмет · график лекций', subject: true, subjectName: 'Основы программирования', availableTypes: ['LECTURE', 'LAB'], initialTypes: ['LECTURE'] },
    { id: 'subject-one-type', label: 'Предмет · один доступный тип', subject: true, subjectName: 'Математический анализ', availableTypes: ['PRACTICE'] },
    { id: 'subject-two-types', label: 'Предмет · два доступных типа', subject: true, subjectName: 'Основы программирования', availableTypes: ['LECTURE', 'LAB'] },
    { id: 'subject-multi', label: 'Предмет · лекции и лабораторные', subject: true, initialTypes: ['LECTURE', 'LAB'] },
    { id: 'subject-none', label: 'Предмет · график скрыт, карточки сохранены', subject: true, initialTypes: [] },
    { id: 'subject-days', label: 'Предмет · дневные периоды', subject: true, initialRange: 'days' },
    { id: 'statistics-rank-unavailable', label: 'Место недоступно', rankUnavailable: true },
    { id: 'statistics-zero-held', label: 'Проведённых пар ещё нет', profile: 'zero' },
    { id: 'statistics-empty', label: 'Нет предметов и статистики', profile: 'empty' },
    { id: 'statistics-sparse', label: 'Обзор · пропущенные календарные недели', profile: 'sparse' },
    { id: 'subject-sparse', label: 'Предмет · пробелы календаря', subject: true, profile: 'sparse' },
    { id: 'subject-future', label: 'Предмет · нет данных и будущие пары', subject: true, profile: 'future' },
    { id: 'subject-long-name', label: 'Длинное название предмета', subject: true, longName: true },
    { id: 'subject-long-history', label: 'Длинная история · 70 пар на тип', subject: true, longHistory: true },
    { id: 'subject-long-labels', label: 'Длинные подписи периодов', subject: true, longLabels: true },
    { id: 'statistics-root20', label: 'Обзор · увеличенный шрифт', enlargedText: true },
    { id: 'subject-root20', label: 'Предмет · увеличенный шрифт', subject: true, enlargedText: true, longName: true },
    { id: 'statistics-loading', label: 'Обзор загружается', overviewLoading: true },
    { id: 'statistics-error', label: 'Ошибка обзора · повтор', overviewError: true },
    { id: 'statistics-forbidden', label: 'Доступ запрещён', forbidden: true },
    { id: 'statistics-offline', label: 'Статистика офлайн', offline: true },
    { id: 'statistics-no-semester', label: 'Нет текущего семестра', unavailable: 'semester' },
    { id: 'statistics-session', label: 'Сессия завершена', unavailable: 'session' },
    { id: 'subject-loading', label: 'Предмет загружается', subject: true, detailLoading: true },
    { id: 'subject-error', label: 'Ошибка предмета · повтор', subject: true, detailError: true },
    { id: 'subject-filter-error', label: 'Ошибка графика · карточки сохраняются', subject: true, filterError: true, initialTypes: ['LECTURE'] },
    { id: 'subject-filter-loading', label: 'График загружается · карточки сохраняются', subject: true, filterLoading: true, initialTypes: ['LECTURE'] },
    { id: 'subject-owner-race', label: 'Поздний ответ прежнего owner', subject: true, ownerRace: true },
  ]
}
