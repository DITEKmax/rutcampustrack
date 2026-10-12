import type {
  HeadmanSemester,
  HeadmanSubject,
  HeadmanSubjectAssignment,
  HeadmanSubjectType,
} from './headman-subjects-client'
export function subjectEditorAssignments(
  subject: HeadmanSubject,
  type: HeadmanSubjectType,
  semester: HeadmanSemester | null,
  today: string,
): readonly HeadmanSubjectAssignment[] {
  if (!semester) return []
  const day =
    today < semester.dateFrom
      ? semester.dateFrom
      : today > semester.dateTo
        ? semester.dateTo
        : today
  return subject.assignments.filter(
    (a) =>
      a.lessonType === type &&
      a.semesterId === semester.id &&
      a.validFrom <= semester.dateTo &&
      (!a.validUntilExclusive || day < a.validUntilExclusive),
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
