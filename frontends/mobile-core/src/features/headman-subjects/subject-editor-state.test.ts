import { describe, expect, it } from 'vitest'
import {
  partitionSubjectCatalog,
  subjectEditorAssignment,
  subjectEditorAssignments,
  subjectAssignmentPeriod,
  subjectAssignmentPeriodLabel,
} from './subject-editor-state'
import { parseSubjectEditIntent } from './subject-edit-intent'
import type { HeadmanSubject } from './headman-subjects-client'
const subject: HeadmanSubject = {
  id: 1,
  groupId: 4,
  name: 'Предмет',
  type: 'LECTURE',
  lessonTypes: ['LECTURE'],
  teacherIds: [10],
  assignments: [
    {
      id: 100,
      teacherId: 10,
      semesterId: 1,
      lessonType: 'LECTURE',
      validFrom: '2026-09-01',
      validUntilExclusive: '2026-10-01',
    },
    {
      id: 101,
      teacherId: 10,
      semesterId: 1,
      lessonType: 'LECTURE',
      validFrom: '2026-11-01',
      validUntilExclusive: null,
    },
    {
      id: 102,
      teacherId: 10,
      semesterId: 1,
      lessonType: 'LECTURE',
      validFrom: '2026-12-01',
      validUntilExclusive: null,
    },
  ],
}
describe('subject catalog semester identity', () => {
  it('retains exact unassigned scopes and shows unresolved legacy separately from the active semester', () => {
    const past = { id: 1, name: 'Осень', dateFrom: '2026-09-01', dateTo: '2027-01-31', active: false }
    const active = { ...past, id: 2, active: true }
    const unassignedPast = { ...subject, id: 2, semesterId: 1, assignments: [] }
    const unassignedActive = { ...subject, id: 3, semesterId: 2, assignments: [] }
    const unresolved = { ...subject, id: 4, semesterId: null, assignments: [] }
    const pendingOnly = { ...unresolved, id: 5, pendingSemesterIds: [past.id] }
    const catalog = [subject, unassignedPast, unassignedActive, unresolved, pendingOnly]
    const progress = {
      semesterId: 2,
      generatedAt: '2026-10-15T10:00:00Z',
      items: [
        { subjectId: 2, lessonType: 'LECTURE' as const, done: 1, total: 1 },
        { subjectId: 4, lessonType: 'LECTURE' as const, done: 1, total: 1 },
      ],
    }
    expect(partitionSubjectCatalog(catalog, past, progress)).toEqual({
      scoped: [subject, unassignedPast, pendingOnly],
      unscoped: [],
    })
    expect(partitionSubjectCatalog(catalog, active, null)).toEqual({
      scoped: [unassignedActive],
      unscoped: [subject, unresolved, pendingOnly],
    })
    expect(partitionSubjectCatalog(catalog, active, progress)).toEqual({
      scoped: [unassignedActive, unresolved],
      unscoped: [subject, pendingOnly],
    })
    expect(partitionSubjectCatalog(catalog, null, null)).toEqual({
      scoped: [],
      unscoped: [subject, unresolved, pendingOnly],
    })
  })
})

describe('editor assignment identity and durable recovery boundaries', () => {
  it('keeps completed and future intervals visible and selects the exact interval of the same teacher', () => {
    const rows = subjectEditorAssignments(
      subject,
      'LECTURE',
      { id: 1, name: 'Осень', dateFrom: '2026-09-01', dateTo: '2027-01-31', active: true },
      '2026-10-06',
    )
    expect(rows.map((a) => a.id)).toEqual([100, 101, 102])
    expect(subjectAssignmentPeriodLabel(rows[0]!.validFrom, rows[0]!.validUntilExclusive, '2026-10-06')).toContain('2026-09-30 включительно · Завершено')
    expect(subjectEditorAssignment(subject, 'LECTURE', 1, 10, 102)?.validFrom).toBe('2026-12-01')
    expect(subjectEditorAssignment(subject, 'LECTURE', 1, 10, undefined)).toBeNull()
  })
  it('rejects another owner, corrupt removal key or changed subject identity before any restored mutation', () => {
    const intent = {
      version: 1,
      actorUserId: '1',
      groupId: 4,
      editId: 'draft',
      subjectId: 1,
      createdId: null,
      name: 'Предмет',
      lessonTypes: ['LECTURE'],
      assignments: [],
      step: 1,
      unknown: true,
      removals: [
        {
          subjectId: 1,
          teacherId: 10,
          assignmentId: 101,
          validUntilExclusive: '2026-10-06',
          requestKey: '11111111-1111-4111-8111-111111111111',
        },
      ],
    }
    expect(
      parseSubjectEditIntent(JSON.stringify(intent), '1', 4)?.removals?.[0]?.assignmentId,
    ).toBe(101)
    expect(parseSubjectEditIntent(JSON.stringify(intent), '2', 4)).toBeNull()
    expect(
      parseSubjectEditIntent(
        JSON.stringify({ ...intent, removals: [{ ...intent.removals[0], requestKey: 'invalid' }] }),
        '1',
        4,
      ),
    ).toBeNull()
    expect(
      parseSubjectEditIntent(
        JSON.stringify({ ...intent, removals: [{ ...intent.removals[0], subjectId: 2 }] }),
        '1',
        4,
      ),
    ).toBeNull()
  })
})

describe('assignment dates within one semester', () => {
  const semester = { dateFrom: '2026-09-01', dateTo: '2027-01-31' }
  it('preserves both past dates and converts the inclusive final day without extending a shorter grant', () => {
    expect(subjectAssignmentPeriod(semester, '2026-09-01', '2026-09-30')).toEqual({ validFrom: '2026-09-01', validUntilExclusive: '2026-10-01' })
    expect(subjectAssignmentPeriod(semester, semester.dateFrom, semester.dateTo)).toEqual({ validFrom: '2026-09-01', validUntilExclusive: '2027-02-01' })
    expect(subjectAssignmentPeriod(semester, semester.dateTo, semester.dateTo)).toEqual({ validFrom: '2027-01-31', validUntilExclusive: '2027-02-01' })
  })
  it.each([
    ['2026-08-31', '2026-09-01'],
    ['2027-01-31', '2027-02-01'],
    ['2026-09-30', '2026-09-01'],
    ['2026-09-01', '2026-09-31'],
    ['', '2026-09-30'],
  ])('rejects an invalid or out-of-semester interval %s to %s', (from, through) => {
    expect(() => subjectAssignmentPeriod(semester, from, through)).toThrow()
  })
})
