import { describe, expect, it } from 'vitest'
import { TeacherAttendanceFeed } from './teacher-attendance-feed'
import type {
  TeacherDayLesson,
  TeacherJournalQuery,
  TeacherJournalResponse,
} from './teacher-client'

const query: TeacherJournalQuery = {
  semesterId: 7,
  groupId: 1,
  subjectId: 11,
  lessonTypes: ['lecture'],
  dateFrom: '2026-09-01',
  dateTo: '2026-10-09',
}
function lesson(id: number): TeacherDayLesson {
  return {
    id,
    assignmentId: 1,
    groupId: 1,
    groupName: 'Группа',
    subjectId: 11,
    subjectName: 'Предмет',
    semesterId: 7,
    lessonType: 'lecture',
    date: '2026-10-09',
    lessonNumber: 1,
    startsAt: '09:00',
    endsAt: '10:30',
    room: null,
    status: 'CLOSED',
    oneOff: false,
    moved: false,
    cancelled: false,
    cancellationReason: null,
  }
}
function page(ids: number[], hasMore: boolean): TeacherJournalResponse {
  return {
    lessons: ids.map(lesson),
    students: [],
    serverNow: '2026-10-09T12:00:00Z',
    page: 0,
    pageSize: 100,
    totalLessons: ids.length,
    hasMore,
  }
}
function deferred<T>() {
  let resolve!: (value: T) => void
  let reject!: (cause: unknown) => void
  const promise = new Promise<T>((yes, no) => {
    resolve = yes
    reject = no
  })
  return { promise, resolve, reject }
}

describe('teacher attendance continuous feed', () => {
  it('serializes pages, merges overlapping IDs and stops at server end', async () => {
    const first = deferred<TeacherJournalResponse>()
    const requests: TeacherJournalQuery[] = []
    const feed = new TeacherAttendanceFeed(() => {})
    feed.reset({ ...query, page: 9 }, async (request) => {
      requests.push(request)
      return request.page === 0 ? first.promise : page([2, 3], false)
    })
    const pending = feed.loadNext()
    await feed.loadNext()
    expect(requests.map((item) => item.page)).toEqual([0])
    first.resolve(page([1, 2], true))
    await pending
    await feed.loadNext()
    await feed.loadNext()
    expect(requests.map((item) => item.page)).toEqual([0, 1])
    expect(feed.state.lessons.map((item) => item.id)).toEqual([1, 2, 3])
    expect(feed.state.hasMore).toBe(false)
  })

  it('ignores both late success and failure from the previous filter generation', async () => {
    for (const fails of [false, true]) {
      const old = deferred<TeacherJournalResponse>()
      const current = deferred<TeacherJournalResponse>()
      const feed = new TeacherAttendanceFeed(() => {})
      feed.reset(query, () => old.promise)
      const obsolete = feed.loadNext()
      feed.reset({ ...query, groupId: 2 }, () => current.promise)
      const active = feed.loadNext()
      if (fails) old.reject(new Error('Старая ошибка'))
      else old.resolve(page([1], true))
      await obsolete
      expect(feed.state.loading).toBe(true)
      expect(feed.state.lessons).toEqual([])
      expect(feed.state.error).toBeNull()
      current.resolve(page([9], false))
      await active
      expect(feed.state.lessons.map((item) => item.id)).toEqual([9])
    }
  })

  it('keeps the loaded context on failure, then retries the same cursor', async () => {
    const requests: number[] = []
    let failed = false
    const feed = new TeacherAttendanceFeed(() => {})
    feed.reset(query, async (request) => {
      requests.push(request.page!)
      if (request.page === 0) return page([1], true)
      if (!failed) {
        failed = true
        throw new Error('Временная ошибка')
      }
      return page([2], false)
    })
    await feed.loadNext()
    await feed.loadNext()
    expect(feed.state.lessons.map((item) => item.id)).toEqual([1])
    expect(feed.state.nextPage).toBe(1)
    expect(feed.state.error).toBeInstanceOf(Error)
    await feed.loadNext()
    expect(requests).toEqual([0, 1, 1])
    expect(feed.state.lessons.map((item) => item.id)).toEqual([1, 2])
    expect(feed.state.error).toBeNull()
  })

  it('drops all data and pending ownership when the session disappears', async () => {
    const pending = deferred<TeacherJournalResponse>()
    const feed = new TeacherAttendanceFeed(() => {})
    feed.reset(query, () => pending.promise)
    const loading = feed.loadNext()
    feed.reset(null, null)
    pending.resolve(page([1], false))
    await loading
    expect(feed.state).toEqual({
      lessons: [],
      nextPage: 0,
      loading: false,
      hasMore: false,
      error: null,
    })
  })
})
