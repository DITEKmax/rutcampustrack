import type {
  HeadmanSemester,
  HeadmanSubject,
  HeadmanSubjectAssignment,
  HeadmanSubjectType,
} from './headman-subjects-client'

export function shiftAssignmentDate(value: string, days: number): string {
  const date = new Date(`${value}T00:00:00Z`)
  if (
    !/^\d{4}-\d{2}-\d{2}$/.test(value) ||
    !Number.isFinite(date.getTime()) ||
    date.toISOString().slice(0, 10) !== value
  )
    throw new RangeError('Укажи корректную дату назначения.')
  date.setUTCDate(date.getUTCDate() + days)
  return date.toISOString().slice(0, 10)
}

/** UI dates include the last day; the existing API excludes its upper boundary. */
export function subjectAssignmentPeriod(
  semester: Pick<HeadmanSemester, 'dateFrom' | 'dateTo'>,
  from: string,
  through: string,
): { validFrom: string; validUntilExclusive: string } {
  const validUntilExclusive = shiftAssignmentDate(through, 1)
  shiftAssignmentDate(from, 0)
  if (from < semester.dateFrom || through > semester.dateTo || through < from)
    throw new RangeError('Период назначения должен быть внутри выбранного семестра.')
  return { validFrom: from, validUntilExclusive }
}

export function subjectAssignmentPeriodLabel(
  from: string,
  untilExclusive: string | null | undefined,
  today: string,
): string {
  const period = untilExclusive
    ? `${from} — ${shiftAssignmentDate(untilExclusive, -1)} включительно`
    : `С ${from}`
  const status =
    untilExclusive && untilExclusive <= today
      ? 'Завершено'
      : from > today
        ? 'Ещё не началось'
        : 'Действует'
  return `${period} · ${status}`
}

export function subjectEditorAssignments(
  subject: HeadmanSubject,
  type: HeadmanSubjectType,
  semester: HeadmanSemester | null,
  _today: string,
): readonly HeadmanSubjectAssignment[] {
  if (!semester) return []
  return subject.assignments.filter(
    (a) =>
      a.lessonType === type &&
      a.semesterId === semester.id &&
      a.validFrom <= semester.dateTo &&
      (!a.validUntilExclusive || semester.dateFrom < a.validUntilExclusive),
  )
}
export function subjectEditorAssignment(
  subject: HeadmanSubject | null,
  type: HeadmanSubjectType,
  semesterId: number | undefined,
  teacherId: number,
  assignmentId: number | undefined,
): HeadmanSubjectAssignment | null {
  return (
    subject?.assignments.find(
      (a) =>
        a.id === assignmentId &&
        a.teacherId === teacherId &&
        a.lessonType === type &&
        a.semesterId === semesterId,
    ) ?? null
  )
}
