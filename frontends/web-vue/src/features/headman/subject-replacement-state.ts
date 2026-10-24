import type {
  AssignmentReplacementInput,
  HeadmanSubject,
  HeadmanSemester,
} from '../../../../mobile-core/src/features/headman-subjects/headman-subjects-client'

function validDate(value: string) {
  return (
    /^\d{4}-\d{2}-\d{2}$/.test(value) &&
    Number.isFinite(Date.parse(`${value}T12:00:00Z`)) &&
    new Date(`${value}T12:00:00Z`).toISOString().slice(0, 10) === value
  )
}
export function assertReplacementWire(input: AssignmentReplacementInput, assignmentId: number) {
  if (
    !input ||
    typeof input !== 'object' ||
    !Number.isSafeInteger(assignmentId) ||
    assignmentId <= 0 ||
    typeof input.replacementTeacherId !== 'string' ||
    !/^[1-9][0-9]*$/.test(input.replacementTeacherId.trim()) ||
    typeof input.effectiveFrom !== 'string' ||
    !/^\d{4}-\d{2}-\d{2}$/.test(input.effectiveFrom.trim()) ||
    typeof input.requestKey !== 'string' ||
    !/^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(
      input.requestKey.trim(),
    )
  )
    throw new Error('Проверь дату, назначение и преподавателя для замены.')
}

/** Only local validation: no operation identity or durable pending exists yet. */
export function replacementDraftInput(
  subject: HeadmanSubject | null,
  semester: HeadmanSemester | undefined,
  groupId: number,
  assignmentId: number,
  teacherId: number,
  knownTeachers: readonly number[],
  date: string,
  today: string,
): Omit<AssignmentReplacementInput, 'requestKey'> {
  if (!validDate(date)) throw new Error('Выбери корректную дату замены преподавателя.')
  const assignment = subject?.assignments.find(
    (item) => item.id === assignmentId && item.semesterId === semester?.id,
  )
  if (
    !subject ||
    subject.groupId !== groupId ||
    !semester?.active ||
    !assignment ||
    !Number.isSafeInteger(teacherId) ||
    teacherId <= 0 ||
    !knownTeachers.includes(teacherId) ||
    teacherId === assignment.teacherId
  )
    throw new Error('Выбери действующее назначение и другого преподавателя.')
  if (
    date < today ||
    date < semester.dateFrom ||
    date > semester.dateTo ||
    date < assignment.validFrom ||
    (assignment.validUntilExclusive !== null && date >= assignment.validUntilExclusive)
  )
    throw new Error('Дата замены должна быть в периоде назначения и активном семестре.')
  return { replacementTeacherId: String(teacherId), effectiveFrom: date }
}
