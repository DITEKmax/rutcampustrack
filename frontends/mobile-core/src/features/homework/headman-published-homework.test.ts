import { describe, expect, it } from 'vitest'
import { homeworkSemesterForDate, readPublishedHomeworks } from './headman-published-homework'
import type { HeadmanManagedHomework } from './headman-homework-client'
function item(id: number, archived: boolean): HeadmanManagedHomework {
  return {
    id,
    archived,
    title: archived ? 'terminal' : 'active',
    bindingId: id,
    requestKey: null,
    description: null,
    link: null,
    subjectId: 1,
    groupId: 2,
    semesterId: 3,
    publishedBy: 4,
    lessonDate: '2026-09-02',
    lessonNumber: null,
    bindingMode: 'DATE',
    revision: 1,
  }
}
describe('all published assignments for the selected semester', () => {
  it('retains active and terminal assignments and chooses terminal duplicate representation', async () => {
    const result = await readPublishedHomeworks(
      {
        listHomeworks: async (_group, _semester, archived) =>
          archived ? [item(1, true), item(3, true)] : [item(1, false), item(2, false)],
      },
      2,
      3,
      () => true,
    )
    expect(result?.map((entry) => [entry.id, entry.archived])).toEqual([
      [1, true],
      [2, false],
      [3, true],
    ])
  })
  it('does not publish partial old-owner reads after a context switch between active and archive', async () => {
    let current = true
    const result = await readPublishedHomeworks(
      {
        listHomeworks: async (_group, _semester, archived) => {
          if (archived) current = false
          return [item(1, Boolean(archived))]
        },
      },
      2,
      3,
      () => current,
    )
    expect(result).toBeNull()
  })
  it('reads the selected historical semester and rejects ambiguous/outside catalog ranges', () => {
    const semesters = [
      { id: 1, name: 'old', dateFrom: '2026-02-01', dateTo: '2026-06-30', active: false },
      { id: 2, name: 'current', dateFrom: '2026-09-01', dateTo: '2027-01-31', active: true },
    ]
    expect(homeworkSemesterForDate('2026-05-01', semesters)?.id).toBe(1)
    expect(homeworkSemesterForDate('2026-08-01', semesters)).toBeNull()
    expect(homeworkSemesterForDate('2026-05-01', [...semesters, semesters[0]!])).toBeNull()
  })
})
