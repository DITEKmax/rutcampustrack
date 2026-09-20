import type {
  AttendanceGraphPoint,
  AttendanceLesson,
  AttendanceMetricSet,
  AttendanceReadState,
  AttendanceSubject,
  AttendanceViewModel,
  LessonType,
} from '../../src/features/attendance/attendance-view-model'
import type {
  StatisticsHistorySegment,
  StatisticsMetricSet,
  StatisticsOverviewData,
  StatisticsReadState,
  StatisticsSeriesPoint,
  StatisticsSubjectDetailData,
  StatisticsSubjectSummary,
  StatisticsTypeCardData,
} from '../../src/features/statistics/statistics-view-model'

function percent(count: number, held: number): number | null {
  return held === 0 ? null : (count / held) * 100
}

function attendanceMetrics(present: number, excused: number, absent: number, planned: number): AttendanceMetricSet {
  const held = present + excused + absent
  return {
    present: { count: present, percent: percent(present, held) },
    presentOrExcused: { count: present + excused, percent: percent(present + excused, held) },
    excused: { count: excused, percent: percent(excused, held) },
    absent: { count: absent, percent: percent(absent, held) },
    held,
    planned,
  }
}

function history(statuses: readonly ('PRESENT' | 'ABSENT' | 'EXCUSED' | 'FUTURE' | 'NO_DATA')[], prefix: string): AttendanceSubject['typeCards'][number]['history'] {
  return statuses.map((status, index) => ({ id: `${prefix}.history.${index + 1}`, status }))
}

function attendanceLesson(
  id: string,
  date: string,
  number: string,
  subjectId: string,
  subjectName: string,
  type: LessonType,
  startsAt: string,
  endsAt: string,
  status: AttendanceLesson['status'],
  requestOptions: AttendanceLesson['requestOptions'] = [],
): AttendanceLesson {
  return {
    id,
    date,
    number,
    subject: { id: subjectId, name: subjectName },
    type,
    schedule: { startsAt, endsAt, room: 'ауд. 308' },
    status,
    requestOptions,
  }
}

const absentOptions: AttendanceLesson['requestOptions'] = [
  { id: 'request.excuse.opaque', kind: 'EXCUSE', label: 'Пропускаю по уважительной причине', enabled: true },
  { id: 'request.dispute.opaque', kind: 'LATE_CHECKIN', label: 'Забыл отметиться', enabled: false, reason: 'Недоступно для этой пары' },
]

const attendanceLessons = {
  first: attendanceLesson('lesson.opaque.4593-1', '2026-09-07', '1', 'subject.math.opaque', 'Математический анализ', 'LECTURE', '08:30:00', '10:00:00', 'PRESENT'),
  active: attendanceLesson('lesson.opaque.4593-2', '2026-09-07', '2', 'subject.programming.opaque', 'Основы программирования', 'PRACTICE', '10:15:00', '11:45:00', 'ACTIVE'),
  absent: attendanceLesson('lesson.opaque.absent', '2026-09-08', '2', 'subject.math.opaque', 'Математический анализ', 'LECTURE', '10:15:00', '11:45:00', 'ABSENT', absentOptions),
  excused: attendanceLesson('lesson.opaque.excused', '2026-09-08', '3', 'subject.programming.opaque', 'Основы программирования', 'PRACTICE', '12:00:00', '13:30:00', 'EXCUSED'),
  future: attendanceLesson('lesson.opaque.future', '2026-09-10', '1', 'subject.physics.opaque', 'Физика', 'LAB', '08:30:00', '10:00:00', 'FUTURE'),
}

const attendanceSubjects: readonly AttendanceSubject[] = [
  {
    id: 'subject.math.opaque',
    name: 'Математический анализ',
    typeCards: [
      { type: 'LECTURE', metrics: attendanceMetrics(7, 1, 2, 12), history: history(['PRESENT', 'PRESENT', 'EXCUSED', 'ABSENT', 'PRESENT', 'PRESENT', 'ABSENT', 'PRESENT', 'PRESENT', 'PRESENT', 'FUTURE', 'FUTURE'], 'math.lecture') },
      { type: 'PRACTICE', metrics: attendanceMetrics(6, 2, 1, 10), history: history(['EXCUSED', 'PRESENT', 'ABSENT', 'PRESENT', 'PRESENT', 'EXCUSED', 'PRESENT', 'FUTURE', 'FUTURE', 'FUTURE'], 'math.practice') },
      { type: 'LAB', metrics: attendanceMetrics(4, 1, 1, 8), history: history(['ABSENT', 'PRESENT', 'PRESENT', 'EXCUSED', 'PRESENT', 'PRESENT', 'FUTURE', 'FUTURE'], 'math.lab') },
    ],
  },
  {
    id: 'subject.programming.opaque',
    name: 'Основы программирования',
    typeCards: [
      { type: 'PRACTICE', metrics: attendanceMetrics(8, 1, 1, 12), history: history(['PRESENT', 'PRESENT', 'PRESENT', 'EXCUSED', 'ABSENT', 'PRESENT', 'PRESENT', 'PRESENT', 'PRESENT', 'FUTURE', 'FUTURE', 'FUTURE'], 'programming.practice') },
    ],
  },
]

function graphPoint(id: string, label: string, dateFrom: string, dateTo: string, state: AttendanceGraphPoint['state'], metrics: AttendanceMetricSet): AttendanceGraphPoint {
  return { id, label, dateFrom, dateTo, state, metrics }
}

const graphDays: readonly AttendanceGraphPoint[] = [
  graphPoint('day.1', '1', '2026-09-01', '2026-09-01', 'DATA', attendanceMetrics(2, 0, 0, 2)),
  graphPoint('day.2', '2', '2026-09-02', '2026-09-02', 'DATA', attendanceMetrics(1, 1, 0, 2)),
  graphPoint('day.3', '3', '2026-09-03', '2026-09-03', 'DATA', attendanceMetrics(2, 0, 1, 3)),
  graphPoint('day.4', '4', '2026-09-04', '2026-09-04', 'DATA', attendanceMetrics(1, 0, 1, 2)),
  graphPoint('day.5', '5', '2026-09-05', '2026-09-05', 'DATA', attendanceMetrics(2, 1, 0, 3)),
  graphPoint('day.6', '6', '2026-09-06', '2026-09-06', 'DATA', attendanceMetrics(1, 0, 0, 1)),
  graphPoint('day.7', '7', '2026-09-07', '2026-09-07', 'DATA', attendanceMetrics(2, 0, 0, 2)),
  graphPoint('day.8', '8', '2026-09-08', '2026-09-08', 'DATA', attendanceMetrics(1, 0, 1, 2)),
  graphPoint('day.9', '9', '2026-09-09', '2026-09-09', 'DATA', attendanceMetrics(1, 1, 0, 2)),
  graphPoint('day.10', '10', '2026-09-10', '2026-09-10', 'DATA', attendanceMetrics(2, 0, 0, 2)),
  graphPoint('day.11', '11', '2026-09-11', '2026-09-11', 'FUTURE', attendanceMetrics(0, 0, 0, 2)),
  graphPoint('day.12', '12', '2026-09-12', '2026-09-12', 'FUTURE', attendanceMetrics(0, 0, 0, 2)),
  graphPoint('day.13', '13', '2026-09-13', '2026-09-13', 'NO_DATA', attendanceMetrics(0, 0, 0, 0)),
  graphPoint('day.14', '14', '2026-09-14', '2026-09-14', 'FUTURE', attendanceMetrics(0, 0, 0, 2)),
  graphPoint('day.15', '15', '2026-09-15', '2026-09-15', 'FUTURE', attendanceMetrics(0, 0, 0, 2)),
  graphPoint('day.16', '16', '2026-09-16', '2026-09-16', 'FUTURE', attendanceMetrics(0, 0, 0, 2)),
]

const graphWeeks: readonly AttendanceGraphPoint[] = [
  graphPoint('week.1', '1', '2026-09-01', '2026-09-07', 'DATA', attendanceMetrics(8, 2, 1, 12)),
  graphPoint('week.2', '2', '2026-09-08', '2026-09-14', 'DATA', attendanceMetrics(4, 1, 2, 8)),
  graphPoint('week.3', '3', '2026-09-15', '2026-09-21', 'FUTURE', attendanceMetrics(0, 0, 0, 12)),
  graphPoint('week.4', '4', '2026-09-22', '2026-09-28', 'FUTURE', attendanceMetrics(0, 0, 0, 12)),
  graphPoint('week.5', '5', '2026-09-29', '2026-10-05', 'FUTURE', attendanceMetrics(0, 0, 0, 12)),
  graphPoint('week.6', '6', '2026-10-06', '2026-10-12', 'FUTURE', attendanceMetrics(0, 0, 0, 12)),
  graphPoint('week.7', '7', '2026-10-13', '2026-10-19', 'FUTURE', attendanceMetrics(0, 0, 0, 12)),
  graphPoint('week.8', '8', '2026-10-20', '2026-10-26', 'FUTURE', attendanceMetrics(0, 0, 0, 12)),
]

export const attendanceReady: AttendanceViewModel = {
  metrics: attendanceMetrics(15, 3, 4, 28),
  days: [
    { date: '2026-09-07', weekday: 'пн', dayNumber: '7', state: 'PAST', lessons: [attendanceLessons.first, attendanceLessons.active] },
    { date: '2026-09-08', weekday: 'вт', dayNumber: '8', state: 'CURRENT', lessons: [attendanceLessons.absent, attendanceLessons.excused] },
    { date: '2026-09-09', weekday: 'ср', dayNumber: '9', state: 'PAST', lessons: [] },
    { date: '2026-09-10', weekday: 'чт', dayNumber: '10', state: 'FUTURE', lessons: [attendanceLessons.future] },
  ],
  subjects: attendanceSubjects,
  graph: { days: graphDays, weeks: graphWeeks },
}

export interface AttendanceHarnessFixture {
  readonly state: AttendanceReadState<AttendanceViewModel>
  readonly selectedDate: string
  readonly mode: 'days' | 'subjects' | 'graph'
  readonly graphRange: 'days' | 'weeks'
  readonly expandedSubjectId: string | null
  readonly actionLessonId: string | null
  readonly requestLessonId: string | null
  readonly requestOptionId: string | null
}

export function attendanceFixture(id: string): AttendanceHarnessFixture {
  switch (id) {
    case '4593-848365':
      return { state: { status: 'ready', data: attendanceReady }, selectedDate: '2026-09-09', mode: 'days', graphRange: 'days', expandedSubjectId: null, actionLessonId: null, requestLessonId: null, requestOptionId: null }
    case '4593-848496':
      return { state: { status: 'ready', data: attendanceReady }, selectedDate: '2026-09-08', mode: 'days', graphRange: 'days', expandedSubjectId: null, actionLessonId: 'lesson.opaque.absent', requestLessonId: null, requestOptionId: null }
    case '4595-293':
      return { state: { status: 'ready', data: attendanceReady }, selectedDate: '2026-09-08', mode: 'graph', graphRange: 'days', expandedSubjectId: null, actionLessonId: null, requestLessonId: null, requestOptionId: null }
    case '4595-848430':
      return { state: { status: 'ready', data: attendanceReady }, selectedDate: '2026-09-08', mode: 'graph', graphRange: 'weeks', expandedSubjectId: null, actionLessonId: null, requestLessonId: null, requestOptionId: null }
    case '4596-365':
      return { state: { status: 'ready', data: attendanceReady }, selectedDate: '2026-09-08', mode: 'subjects', graphRange: 'days', expandedSubjectId: 'subject.math.opaque', actionLessonId: null, requestLessonId: null, requestOptionId: null }
    case '4710-232':
      return { state: { status: 'ready', data: attendanceReady }, selectedDate: '2026-09-08', mode: 'days', graphRange: 'days', expandedSubjectId: null, actionLessonId: null, requestLessonId: 'lesson.opaque.absent', requestOptionId: 'request.excuse.opaque' }
    case '4768-228':
      return { state: { status: 'ready', data: attendanceReady }, selectedDate: '2026-09-08', mode: 'subjects', graphRange: 'days', expandedSubjectId: null, actionLessonId: null, requestLessonId: null, requestOptionId: null }
    default:
      return { state: { status: 'ready', data: attendanceReady }, selectedDate: '2026-09-07', mode: 'days', graphRange: 'days', expandedSubjectId: null, actionLessonId: null, requestLessonId: null, requestOptionId: null }
  }
}

function statisticsMetrics(present: number, excused: number, absent: number, planned: number): StatisticsMetricSet {
  const held = present + excused + absent
  return {
    present: { count: present, percent: percent(present, held) },
    presentOrExcused: { count: present + excused, percent: percent(present + excused, held) },
    excused: { count: excused, percent: percent(excused, held) },
    absent: { count: absent, percent: percent(absent, held) },
    held,
    planned,
  }
}

function statisticsHistory(statuses: readonly ('PRESENT' | 'ABSENT' | 'EXCUSED' | 'FUTURE' | 'NO_DATA')[], prefix: string): readonly StatisticsHistorySegment[] {
  return statuses.map((status, index) => ({ id: `${prefix}.history.${index + 1}`, status }))
}

function statisticsPoint(id: string, label: string, state: StatisticsSeriesPoint['state'], metrics: StatisticsMetricSet): StatisticsSeriesPoint {
  return { id, label, dateFrom: '2026-09-01', dateTo: '2026-09-07', state, metrics }
}

const statisticsSeries: readonly StatisticsSeriesPoint[] = [
  statisticsPoint('weeks:1', '1', 'DATA', statisticsMetrics(8, 1, 2, 12)),
  statisticsPoint('weeks:2', '2', 'DATA', statisticsMetrics(7, 2, 2, 12)),
  statisticsPoint('weeks:3', '3', 'DATA', statisticsMetrics(9, 1, 1, 12)),
  statisticsPoint('weeks:4', '4', 'DATA', statisticsMetrics(6, 2, 3, 12)),
  statisticsPoint('weeks:5', '5', 'FUTURE', statisticsMetrics(0, 0, 0, 12)),
  statisticsPoint('weeks:6', '6', 'FUTURE', statisticsMetrics(0, 0, 0, 12)),
  statisticsPoint('weeks:7', '7', 'FUTURE', statisticsMetrics(0, 0, 0, 12)),
  statisticsPoint('weeks:8', '8', 'FUTURE', statisticsMetrics(0, 0, 0, 12)),
]

const lectureCard: StatisticsTypeCardData = {
  type: 'LECTURE',
  metrics: statisticsMetrics(8, 1, 3, 12),
  history: statisticsHistory(['PRESENT', 'PRESENT', 'EXCUSED', 'ABSENT', 'PRESENT', 'PRESENT', 'ABSENT', 'PRESENT', 'PRESENT', 'FUTURE', 'FUTURE', 'FUTURE'], 'statistics.lecture'),
}
const practiceCard: StatisticsTypeCardData = {
  type: 'PRACTICE',
  metrics: statisticsMetrics(6, 1, 3, 10),
  history: statisticsHistory(['EXCUSED', 'ABSENT', 'PRESENT', 'PRESENT', 'ABSENT', 'PRESENT', 'PRESENT', 'FUTURE', 'FUTURE', 'FUTURE'], 'statistics.practice'),
}
const labCard: StatisticsTypeCardData = {
  type: 'LAB',
  metrics: statisticsMetrics(5, 1, 2, 8),
  history: statisticsHistory(['ABSENT', 'PRESENT', 'PRESENT', 'ABSENT', 'EXCUSED', 'PRESENT', 'FUTURE', 'FUTURE'], 'statistics.lab'),
}

const statisticsSubjects: readonly StatisticsSubjectSummary[] = [
  { id: 'subject.math.opaque', name: 'Математический анализ', metrics: statisticsMetrics(13, 2, 4, 24) },
  { id: 'subject.programming.opaque', name: 'Основы программирования', metrics: statisticsMetrics(10, 1, 2, 16) },
  { id: 'subject.physics.opaque', name: 'Физика', metrics: statisticsMetrics(7, 1, 3, 12) },
]

export const statisticsOverview: StatisticsOverviewData = {
  metrics: statisticsMetrics(18, 3, 4, 30),
  ownRank: { position: 7, participantCount: 27, available: true },
  semesterSeries: statisticsSeries,
  subjects: statisticsSubjects,
}

function detailData(selectedTypes: readonly ('LECTURE' | 'PRACTICE' | 'LAB')[], availableTypes: readonly ('LECTURE' | 'PRACTICE' | 'LAB')[], cards: readonly StatisticsTypeCardData[], subjectName = 'Основы программирования'): StatisticsSubjectDetailData {
  const aggregate = selectedTypes.includes('LECTURE') && selectedTypes.includes('LAB')
    ? statisticsMetrics(13, 2, 5, 20)
    : selectedTypes.includes('LECTURE')
      ? statisticsMetrics(8, 1, 3, 12)
      : selectedTypes.includes('PRACTICE')
        ? statisticsMetrics(6, 1, 3, 10)
        : statisticsMetrics(5, 1, 2, 8)
  return {
    subjectId: 'subject.programming.opaque',
    name: subjectName,
    availableTypes,
    selectedTypes,
    selectedAggregate: aggregate,
    series: statisticsSeries,
    typeCards: cards,
  }
}

const twoTypeAll = detailData(['LECTURE', 'LAB'], ['LECTURE', 'LAB'], [lectureCard, labCard], 'Основы программирования')
const twoTypeLecture = detailData(['LECTURE'], ['LECTURE', 'LAB'], [lectureCard, labCard], 'Основы программирования')
const oneType = detailData(['PRACTICE'], ['PRACTICE'], [practiceCard], 'Математический анализ')
const threeTypeAll = detailData(['LECTURE', 'PRACTICE', 'LAB'], ['LECTURE', 'PRACTICE', 'LAB'], [lectureCard, practiceCard, labCard], 'Основы программирования')

export interface StatisticsHarnessFixture {
  readonly state: StatisticsReadState<StatisticsOverviewData>
  readonly selectedSubjectId: string | null
  readonly detailState: StatisticsReadState<StatisticsSubjectDetailData> | null
}

export function statisticsFixture(id: string): StatisticsHarnessFixture {
  switch (id) {
    case '4603-848696': return { state: { status: 'ready', data: statisticsOverview }, selectedSubjectId: 'subject.programming.opaque', detailState: { status: 'ready', data: twoTypeAll } }
    case '4798-142': return { state: { status: 'ready', data: statisticsOverview }, selectedSubjectId: 'subject.math.opaque', detailState: { status: 'ready', data: oneType } }
    case '4798-200': return { state: { status: 'ready', data: statisticsOverview }, selectedSubjectId: 'subject.programming.opaque', detailState: { status: 'ready', data: twoTypeLecture } }
    case '4798-285': return { state: { status: 'ready', data: statisticsOverview }, selectedSubjectId: 'subject.programming.opaque', detailState: { status: 'ready', data: threeTypeAll } }
    default: return { state: { status: 'ready', data: statisticsOverview }, selectedSubjectId: null, detailState: null }
  }
}
