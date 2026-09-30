import { afterEach, describe, expect, it } from 'vitest'
import {
  budgetLabel,
  formatLessonDate,
  getOrCreateRequestsDraft,
  lessonTypeLabel,
  purgeRequestsDrafts,
  readRequestsDraft,
  requestKindLabel,
  requestStatusLabel,
  removeSelectedLessonId,
  selectedLessonOptions,
  updateSelectedLessonIds,
  updateRequestsDraft,
} from './state'
import type { RequestFileRef, RequestLessonOption } from './types'

const file: RequestFileRef = {
  id: 'proof-1',
  name: 'справка.pdf',
  size: 2048,
  type: 'application/pdf',
}

afterEach(() => {
  purgeRequestsDrafts()
})

describe('requests draft ownership and navigation boundary', () => {
  it('retains only the feature draft for the same owner/session and returns defensive copies', () => {
    const created = updateRequestsDraft('student-1', 'session-a', {
      view: 'excuse',
      excuseLessonIds: ['lesson-1'],
      excuseReason: 'ILLNESS',
      excuseComment: 'Сообщение',
      excuseFiles: [file],
    })
    created.excuseLessonIds.push('local-only')
    created.excuseFiles[0]!.name = 'mutated.pdf'

    const retained = readRequestsDraft('student-1', 'session-a')
    expect(retained?.view).toBe('excuse')
    expect(retained?.excuseLessonIds).toEqual(['lesson-1'])
    expect(retained?.excuseFiles[0]?.name).toBe('справка.pdf')
    expect(readRequestsDraft('student-1', 'session-b')).toBeNull()
    expect(getOrCreateRequestsDraft('student-2', 'session-a').excuseLessonIds).toEqual([])
  })

  it('purges one session and then all sessions for an owner without touching another owner', () => {
    updateRequestsDraft('student-1', 'session-a', { view: 'late', lateLessonId: 'lesson-a' })
    updateRequestsDraft('student-1', 'session-b', { view: 'excuse', excuseLessonIds: ['lesson-b'] })
    updateRequestsDraft('student-2', 'session-a', { view: 'late', lateLessonId: 'lesson-other' })

    purgeRequestsDrafts('student-1', 'session-a')
    expect(readRequestsDraft('student-1', 'session-a')).toBeNull()
    expect(readRequestsDraft('student-1', 'session-b')?.view).toBe('excuse')

    purgeRequestsDrafts('student-1')
    expect(readRequestsDraft('student-1', 'session-b')).toBeNull()
    expect(readRequestsDraft('student-2', 'session-a')?.lateLessonId).toBe('lesson-other')
  })
})

describe('requests server display projections', () => {
  it('keeps labels and nullable date/budget values safe', () => {
    expect(requestKindLabel('EXCUSE')).toBe('Уважительная причина')
    expect(requestKindLabel('LATE_CHECKIN')).toBe('Забыл отметиться')
    expect(requestStatusLabel('PENDING')).toBe('На рассмотрении')
    expect(requestStatusLabel('UNKNOWN' as never)).toBe('Статус не указан')
    expect(lessonTypeLabel('lecture')).toBe('Лекция')
    expect(lessonTypeLabel('practice')).toBe('Практика')
    expect(lessonTypeLabel('lab')).toBe('Лабораторная')
    expect(lessonTypeLabel('LECTURE')).toBe('Лекция')
    expect(lessonTypeLabel('PRACTICE')).toBe('Практика')
    expect(lessonTypeLabel('LAB')).toBe('Лабораторная')
    expect(lessonTypeLabel('Lecture')).toBe('Лекция')
    expect(lessonTypeLabel('seminar')).toBe('seminar')
    expect(lessonTypeLabel(null)).toBe('Тип занятия не указан')
    expect(lessonTypeLabel(undefined)).toBe('Тип занятия не указан')
    expect(lessonTypeLabel('')).toBe('Тип занятия не указан')
    expect(formatLessonDate(null)).toBe('Дата не указана')
    expect(budgetLabel({ remaining: 5, limit: 5 })).toBe('5 из 5')
    expect(budgetLabel(null)).toBe('Лимит попыток не указан')
  })

  it('selects only the server options matching the controlled ids', () => {
    const options: RequestLessonOption[] = [
      { lesson: { id: 'one' }, excuseEligible: true },
      { lesson: { id: 'two' }, excuseEligible: false },
      { lesson: null, excuseEligible: true },
    ]
    expect(selectedLessonOptions(options, ['two', 'missing'])).toEqual([options[1]])
  })

  it('updates checkbox selections and removes one stale lesson without mutating the input', () => {
    const initial = ['lesson-1', 'lesson-2']
    const afterUncheck = updateSelectedLessonIds(initial, 'lesson-1', false)
    const afterCheck = updateSelectedLessonIds(afterUncheck, 'lesson-3', true)

    expect(afterUncheck).toEqual(['lesson-2'])
    expect(afterCheck).toEqual(['lesson-2', 'lesson-3'])
    expect(removeSelectedLessonId(afterCheck, 'lesson-2')).toEqual(['lesson-3'])
    expect(initial).toEqual(['lesson-1', 'lesson-2'])
  })

  it('removes one retained lesson while preserving the rest of the draft', () => {
    updateRequestsDraft('student-survival', 'session-a', {
      view: 'excuse',
      excuseLessonIds: ['lesson-1', 'lesson-2'],
      excuseReason: 'ILLNESS',
      excuseComment: 'Сохрани этот комментарий',
      excuseFiles: [file],
    })

    const current = getOrCreateRequestsDraft('student-survival', 'session-a')
    const updated = updateRequestsDraft('student-survival', 'session-a', {
      excuseLessonIds: removeSelectedLessonId(current.excuseLessonIds, 'lesson-1'),
    })

    expect(updated.excuseLessonIds).toEqual(['lesson-2'])
    expect(updated.excuseReason).toBe('ILLNESS')
    expect(updated.excuseComment).toBe('Сохрани этот комментарий')
    expect(updated.excuseFiles).toEqual([file])
  })
})
