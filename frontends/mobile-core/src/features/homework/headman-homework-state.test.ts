import { describe, expect, it } from 'vitest'
import { canReselectHomeworkCreate, homeworkDateWritable, homeworkLessonEligible, homeworkMonth, homeworkSubjectTypes, homeworkTextSegments, homeworkWeek } from './headman-homework-state'
import type { HeadmanJournalLesson } from '../headman-journal/headman-journal-client'
import type { HeadmanSubject } from '../headman-subjects/headman-subjects-client'

const semester = { id: 12, name: 'Осень', dateFrom: '2026-09-01', dateTo: '2027-01-31', active: true }
const lesson: HeadmanJournalLesson = { id: 30, occurrenceId: '71', occurrenceRevision: '3', groupId: 7, subjectId: 88, lessonType: 'LECTURE', date: '2026-09-02', lessonNumber: 8,
  status: 'ACTIVE', current: true, startTime: '18:00', endTime: '19:30', room: 'А-401', transferOperationId: null, transferState: null }
const context = { groupId: 7, subjectId: 88, type: 'LECTURE' as const, semester, now: new Date('2026-09-02T16:34:59Z') }
describe('homework actual calendar eligibility', () => {
  it('permits reselection only for the first typed definitive rejection, with no previous uncertainty', () => {
    const problem = { extras: { code: 'HOMEWORK_SELECTION_STALE', admission: 'CURRENT_ATTEMPT_REJECTED_BEFORE_ADMISSION' } }
    expect(canReselectHomeworkCreate(problem, { attempts: 1, everUncertain: false, hasReceipt: false })).toBe(true)
    expect(canReselectHomeworkCreate(problem, { attempts: 2, everUncertain: true, hasReceipt: false })).toBe(false)
    expect(canReselectHomeworkCreate(problem, { attempts: 1, everUncertain: true, hasReceipt: true })).toBe(false)
    expect(canReselectHomeworkCreate({ detail: 'HOMEWORK_SELECTION_STALE' }, { attempts: 1, everUncertain: false, hasReceipt: false })).toBe(false)
    expect(canReselectHomeworkCreate({ extras: { code: 'HOMEWORK_SELECTION_STALE' } }, { attempts: 1, everUncertain: false, hasReceipt: false })).toBe(false)
  })
  it('uses actual physical identity and the Moscow end+5 cutoff', () => {
    expect(homeworkLessonEligible(lesson, context)).toBe(true)
    expect(homeworkLessonEligible(lesson, { ...context, now: new Date('2026-09-02T16:35:00Z') })).toBe(false)
    expect(homeworkLessonEligible({ ...lesson, occurrenceId: null }, context)).toBe(false)
  })
  it.each(['CANCELLED', 'TRANSFERRED', 'CLOSED', 'UNSUPPORTED'] as const)('does not enable %s slots', (status) => {
    expect(homeworkLessonEligible({ ...lesson, status }, context)).toBe(false)
  })
  it('rejects wrong group/type, historical occurrence, past and out-of-semester choices', () => {
    for (const change of [{ groupId: 9 }, { subjectId: 89 }, { lessonType: 'LAB' }, { current: false }, { date: '2026-08-31' }, { date: '2027-02-01' }]) {
      expect(homeworkLessonEligible({ ...lesson, ...change }, context)).toBe(false)
    }
  })
  it('validates actual DATE dates and writable semester bounds', () => {
    expect(homeworkDateWritable('2026-09-02', semester, '2026-09-02')).toBe(true)
    expect(homeworkDateWritable('2026-02-30', semester, '2026-02-01')).toBe(false)
    expect(homeworkDateWritable('2026-09-01', semester, '2026-09-02')).toBe(false)
    expect(homeworkDateWritable('2026-09-02', { ...semester, active: false }, '2026-09-02')).toBe(false)
  })
  it('derives group/semester/date types only from actual assignment validity', () => {
    const subject: HeadmanSubject = { id: 88, name: 'Программирование', type: 'LECTURE', groupId: 7,
      lessonTypes: ['LECTURE', 'LAB'], teacherIds: [301, 302],
      assignments: [{ id: 501, teacherId: 301, semesterId: 12, lessonType: 'LECTURE', validFrom: '2026-09-01', validUntilExclusive: '2026-09-10' }, { id: 502, teacherId: 302, semesterId: 13, lessonType: 'LAB', validFrom: '2026-09-01', validUntilExclusive: null }] }
    expect(homeworkSubjectTypes(subject, 7, 12, '2026-09-02')).toEqual(['LECTURE'])
    expect(homeworkSubjectTypes(subject, 7, 12, '2026-09-10')).toEqual([])
    expect(homeworkSubjectTypes(subject, 8, 12, '2026-09-02')).toEqual([])
  })
  it('keeps Monday order across month/year boundaries without inventing recurrence', () => {
    expect(homeworkWeek('2026-01-01')).toEqual(['2025-12-29', '2025-12-30', '2025-12-31', '2026-01-01', '2026-01-02', '2026-01-03', '2026-01-04'])
    expect(homeworkMonth('2026-09-02')).toHaveLength(35)
  })
  it('preserves all text and only exposes safe HTTP URLs', () => {
    const text = 'Материал https://example.test/read\n<script>alert(1)</script> javascript:bad https://actor:secret@example.test/'
    const segments = homeworkTextSegments(text)
    expect(segments.map((item) => item.text).join('')).toBe(text)
    expect(segments.filter((item) => item.url).map((item) => item.url)).toEqual(['https://example.test/read'])
  })
})
