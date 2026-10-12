import { describe, expect, it } from 'vitest'
import {
  journalCounts,
  journalMark,
  journalPassedEnd,
  loadJournalPages,
  mergeJournalPage,
} from './teacher-journal-model'
import type {
  TeacherApi,
  TeacherDayLesson,
  TeacherJournalCell,
  TeacherJournalQuery,
  TeacherJournalResponse,
} from './teacher-client'
const query: TeacherJournalQuery = {
  semesterId: 7,
  groupId: 2,
  subjectId: 3,
  lessonTypes: ['lecture', 'practice'],
  pastOnly: true,
}
function lesson(id: number): TeacherDayLesson {
  return {
    id,
    assignmentId: 1,
    groupId: 2,
    groupName: 'Группа',
    subjectId: 3,
    subjectName: 'Предмет',
    semesterId: 7,
    lessonType: 'lecture',
    date: '2026-10-01',
    lessonNumber: id,
    startsAt: null,
    endsAt: null,
    room: null,
    status: 'CLOSED',
    oneOff: false,
    moved: false,
    cancelled: false,
    cancellationReason: null,
  }
}
function cell(lessonId: number, status: string | null, recordPresent = true): TeacherJournalCell {
  return {
    lessonId,
    status,
    symbol:
      status === 'present' ? '+' : status === 'excused' ? 'у' : status === 'absent' ? 'н' : null,
    source: null,
    recordPresent,
    pendingTicket: false,
    autoAbsent: false,
    ticketId: null,
    excuseType: null,
    excuseReason: null,
  }
}
function page(
  index: number,
  ids: number[],
  students: TeacherJournalResponse['students'],
  hasMore: boolean,
): TeacherJournalResponse {
  return {
    page: index,
    pageSize: 100,
    totalLessons: hasMore ? 201 : 201,
    hasMore,
    lessons: ids.map(lesson),
    students,
    serverNow: '2026-10-09T00:00:00Z',
  }
}
describe('completed teacher journal history', () => {
  it('unions pages and historical students/cells, replacing repeated IDs without truncating former members', async () => {
    const pages = [
      page(
        0,
        [1, 2],
        [
          {
            studentId: 1,
            displayName: 'Бывший студент',
            cells: [cell(1, 'present'), cell(2, 'absent', false)],
          },
        ],
        true,
      ),
      page(
        1,
        [2, 3],
        [
          { studentId: 1, displayName: 'Бывший студент', cells: [cell(2, 'excused')] },
          { studentId: 2, displayName: 'Новый студент', cells: [cell(3, 'present')] },
        ],
        true,
      ),
      page(
        2,
        [4],
        [{ studentId: 2, displayName: 'Новый студент', cells: [cell(4, 'absent', false)] }],
        false,
      ),
    ]
    const requests: TeacherJournalQuery[] = []
    const api: Pick<TeacherApi, 'journal'> = {
      journal: async (q) => {
        requests.push(q)
        return pages[q.page!]!
      },
    }
    const value = await loadJournalPages(api, query, () => true)
    expect(requests.map((q) => q.page)).toEqual([0, 1, 2])
    expect(
      requests.every((q) => q.pageSize === 100 && q.pastOnly && q.lessonTypes?.length === 2),
    ).toBe(true)
    expect(value?.lessons.map((item) => item.id)).toEqual([1, 2, 3, 4])
    expect(value?.students).toHaveLength(2)
    const ids = new Set(value!.lessons.map((item) => item.id))
    expect(
      journalCounts(value!.students.find((student) => student.studentId === 1)!.cells, ids),
    ).toEqual({ present: 1, excused: 1, absent: 0, denominator: 2 })
    expect(
      journalCounts(value!.students.find((student) => student.studentId === 2)!.cells, ids),
    ).toEqual({ present: 1, excused: 0, absent: 1, denominator: 2 })
  })
  it('does not turn a nonmember missing cell into absence, but includes canonical effective absence without a stored record', () => {
    expect(journalMark(null)).toBeNull()
    expect(journalMark(cell(2, 'absent', false))).toBe('absent')
    expect(
      journalCounts(
        [cell(1, 'present'), cell(2, 'absent', false), cell(2, 'absent', false), cell(9, 'absent')],
        new Set([1, 2, 3]),
      ),
    ).toEqual({ present: 1, excused: 0, absent: 1, denominator: 2 })
  })
  it('ignores cell records outside completed lesson universe', () => {
    const result = mergeJournalPage(
      null,
      page(
        0,
        [1],
        [{ studentId: 1, displayName: 'Анна', cells: [cell(1, 'present'), cell(8, 'absent')] }],
        false,
      ),
    )
    expect(journalCounts(result.students[0]!.cells, new Set([1]))).toEqual({
      present: 1,
      excused: 0,
      absent: 0,
      denominator: 1,
    })
  })
  it('sends no request for an empty type selection', async () => {
    let requested = false
    const api: Pick<TeacherApi, 'journal'> = {
      journal: async () => {
        requested = true
        throw new Error('not expected')
      },
    }
    expect(await loadJournalPages(api, { ...query, lessonTypes: [] }, () => true)).toBeNull()
    expect(requested).toBe(false)
  })
  it('stops a stale context after an in-flight page without requesting the next page', async () => {
    let active = true
    let release!: (value: TeacherJournalResponse) => void
    let count = 0
    const api: Pick<TeacherApi, 'journal'> = {
      journal: () => {
        count++
        return new Promise((resolve) => {
          release = resolve
        })
      },
    }
    const loading = loadJournalPages(api, query, () => active)
    active = false
    release(page(0, [1], [], true))
    expect(await loading).toBeNull()
    expect(count).toBe(1)
  })
  it('rejects inconsistent pagination instead of looping forever', async () => {
    const api: Pick<TeacherApi, 'journal'> = {
      journal: async () => ({ ...page(0, [1], [], true), totalLessons: 1 }),
    }
    await expect(loadJournalPages(api, query, () => true)).rejects.toThrow(
      'несогласованные страницы',
    )
  })
  it('anchors passed endpoint to campus server date and caps at the selected semester end', () => {
    expect(journalPassedEnd('2027-01-31', '2026-10-08T22:30:00Z')).toBe('2026-10-09')
    expect(journalPassedEnd('2026-06-30', '2026-10-08T22:30:00Z')).toBe('2026-06-30')
    expect(journalPassedEnd(undefined, '2026-10-08T22:30:00Z')).toBeNull()
  })
})
