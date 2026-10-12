import type {
  HeadmanHomeworkApi,
  HeadmanHomeworkSemester,
  HeadmanManagedHomework,
} from './headman-homework-client'
export function homeworkSemesterForDate(
  date: string,
  semesters: readonly HeadmanHomeworkSemester[],
): HeadmanHomeworkSemester | null {
  const matches = semesters.filter(
    (semester) =>
      semester.dateFrom !== null &&
      semester.dateTo !== null &&
      date >= semester.dateFrom &&
      date <= semester.dateTo,
  )
  return matches.length === 1 ? matches[0]! : null
}
/** Publish only a complete, current read; terminal representations win duplicate IDs. */
export async function readPublishedHomeworks(
  api: Pick<HeadmanHomeworkApi, 'listHomeworks'>,
  groupId: number,
  semesterId: number,
  isCurrent: () => boolean,
): Promise<readonly HeadmanManagedHomework[] | null> {
  const active = await api.listHomeworks(groupId, semesterId)
  if (!isCurrent()) return null
  const archived = await api.listHomeworks(groupId, semesterId, true)
  if (!isCurrent()) return null
  return [...new Map([...active, ...archived].map((item) => [item.id, item])).values()]
}
