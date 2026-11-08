import { afterEach, describe, expect, it, vi } from 'vitest'
import { createTeacherReviewFixtures } from './teacher-review-fixtures'
import { loadJournalPages } from '../features/teacher/teacher-journal-model'
import type { TeacherJournalQuery } from '../features/teacher/teacher-client'

const query: TeacherJournalQuery = {
  semesterId: 7,
  groupId: 101,
  subjectId: 201,
  lessonTypes: ['lecture', 'practice', 'lab'],
  pastOnly: true,
  includeMetrics: true,
}
function fixture() {
  vi.stubGlobal('window', { location: { origin: 'https://synthetic.invalid' } })
  return createTeacherReviewFixtures('journal')
}
afterEach(() => vi.unstubAllGlobals())

describe('normal teacher journal fixture metrics', () => {
  it('returns authoritative full-period values independently of the requested page', async () => {
    const { api } = fixture()
    const first = await api.journal({ ...query, pageSize: 100 })
    const last = await api.journal({ ...query, page: 2, pageSize: 100 })
    expect(first.lessons).toHaveLength(100)
    expect(first.totalLessons).toBe(226)
    expect(first.hasMore).toBe(true)
    expect(last.lessons).toHaveLength(26)
    expect(last.hasMore).toBe(false)
    expect(last.metrics).toEqual(first.metrics)
    expect(first.metrics?.selectedStudentId).toBeNull()
    expect(first.metrics?.serverNow).toBe(first.serverNow)
    expect(first.metrics?.series).toHaveLength(226)
    expect(first.metrics?.students).toHaveLength(7)
    expect(first.metrics?.students.find((student) => student.studentId === 501)?.present).toEqual({
      numerator: 226,
      denominator: 226,
      percent: 100,
    })
    expect(
      first.metrics?.students.find((student) => student.studentId === 502)?.presentOrExcused,
    ).toEqual({ numerator: 226, denominator: 226, percent: 100 })
    expect(first.metrics?.students.find((student) => student.studentId === 504)?.absent).toEqual({
      numerator: 226,
      denominator: 226,
      percent: 100,
    })
    expect(first.metrics?.series[0]?.present).toEqual({
      numerator: 1,
      denominator: 6,
      percent: 100 / 6,
    })
  })

  it('preserves full metrics while the normal screen loader joins three pages', async () => {
    const source = fixture()
    const result = await loadJournalPages(source.api, query, () => true)
    expect(result?.lessons).toHaveLength(226)
    expect(result?.metrics?.series).toHaveLength(226)
    expect(
      source.requests.map((request) =>
        new URL(request, window.location.origin).searchParams.get('includeMetrics'),
      ),
    ).toEqual(['true', 'false', 'false'])
    expect((await source.api.journal({ ...query, includeMetrics: false })).metrics).toBeNull()
  })

  it('applies date and lesson-type filters before computing period denominators', async () => {
    const result = await fixture().api.journal({
      ...query,
      dateFrom: '2026-09-14',
      dateTo: '2026-09-15',
      lessonTypes: ['lab'],
      pageSize: 1,
    })
    expect(result.totalLessons).toBe(5)
    expect(result.metrics?.series.map((point) => point.lessonId)).toEqual([
      1092, 1095, 1098, 1101, 1104,
    ])
    expect(
      result.metrics?.students.find((student) => student.studentId === 501)?.present.denominator,
    ).toBe(5)
    expect(result.metrics?.students.find((student) => student.studentId === 507)?.absent).toEqual({
      numerator: 3,
      denominator: 3,
      percent: 100,
    })
  })

  it('limits selected series to dated membership and keeps every student summary', async () => {
    const { api } = fixture()
    const group = await api.journal(query)
    const student = await api.journal({ ...query, metricsStudentId: 507 })
    expect(student.metrics?.selectedStudentId).toBe(507)
    expect(student.metrics?.students).toEqual(group.metrics?.students)
    expect(student.metrics?.students.find((row) => row.studentId === 507)?.absent).toEqual({
      numerator: 128,
      denominator: 128,
      percent: 100,
    })
    expect(
      student.students
        .find((row) => row.studentId === 507)
        ?.cells.some((cell) => cell.lessonId === 1000),
    ).toBe(false)
    expect(student.metrics?.series[0]?.absent).toEqual({ numerator: 0, denominator: 0, percent: 0 })
    const joined = student.metrics?.series.find((point) => point.lessonId === 1098)
    expect(joined?.absent).toEqual({ numerator: 1, denominator: 1, percent: 100 })
    // The enrolled student has no stored mark; this is ABSENT, not a missing cell.
    const afterJoin = await api.journal({ ...query, dateFrom: '2026-09-15', dateTo: '2026-09-15' })
    expect(afterJoin.students.find((row) => row.studentId === 507)?.cells[0]).toMatchObject({
      status: 'absent',
      recordPresent: false,
    })
  })

  it('rejects unknown and not-yet-enrolled selections instead of inventing zero-valued students', async () => {
    const { api } = fixture()
    await expect(api.journal({ ...query, metricsStudentId: 999 })).rejects.toMatchObject({
      response: { status: 403 },
    })
    await expect(
      api.journal({
        ...query,
        dateFrom: '2026-09-01',
        dateTo: '2026-09-14',
        metricsStudentId: 507,
      }),
    ).rejects.toMatchObject({ response: { status: 403 } })
    expect(() => api.journal({ ...query, metricsStudentId: 0 })).toThrow(RangeError)
    expect(() => api.journal({ ...query, includeMetrics: false, metricsStudentId: 501 })).toThrow(
      RangeError,
    )
  })

  it('excludes active, planned and cancelled lessons from metrics without removing dated roster', async () => {
    const { api } = fixture()
    const mixed = await api.journal({
      ...query,
      pastOnly: false,
      dateFrom: '2026-10-08',
      dateTo: '2026-10-08',
    })
    expect(mixed.lessons.map((lesson) => lesson.status)).toEqual(['CLOSED', 'ACTIVE'])
    expect(mixed.metrics?.series.map((point) => point.lessonId)).toEqual([401])
    for (const context of [{ subjectId: 202 }, { groupId: 102 }]) {
      const result = await api.journal({ ...query, ...context, pastOnly: false })
      expect(result.lessons).toHaveLength(1)
      expect(result.metrics?.series).toEqual([])
      expect(result.metrics?.students).toHaveLength(7)
      expect(result.metrics?.students.every((student) => student.present.denominator === 0)).toBe(
        true,
      )
    }
  })

  it('returns a well-formed empty metrics period and rejects a selection outside it', async () => {
    const { api } = fixture()
    const empty = { ...query, dateFrom: '2026-11-01', dateTo: '2026-11-30' }
    const result = await api.journal(empty)
    expect(result.lessons).toEqual([])
    expect(result.metrics).toEqual({
      students: [],
      series: [],
      selectedStudentId: null,
      serverNow: result.serverNow,
    })
    await expect(api.journal({ ...empty, metricsStudentId: 501 })).rejects.toMatchObject({
      response: { status: 403 },
    })
  })
})
