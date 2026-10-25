import { describe, expect, it } from 'vitest'
import type { HeadmanManagedHomework } from '../../../../mobile-core/src/features/homework/headman-homework-client'
import {
  homeworkEditMatches,
  homeworkPlacementChanged,
  canonicalHomeworkText,
} from './homework-edit-state'
const homework: HeadmanManagedHomework = {
  id: 10,
  bindingId: 20,
  requestKey: 'create-key',
  title: 'Задание',
  description: 'Описание',
  link: null,
  subjectId: 1,
  groupId: 1,
  semesterId: 2,
  publishedBy: 3,
  lessonDate: '2026-10-25',
  lessonNumber: 1,
  bindingMode: 'LESSON',
  revision: 4,
  lessonType: 'LECTURE',
  archived: false,
}
describe('headman homework placement edits', () => {
  it('rejects a whitespace-only title before any operation can be frozen', () => {
    expect(() =>
      canonicalHomeworkText({ title: '   ', description: 'Описание', link: '' }),
    ).toThrow('Укажи заголовок')
    expect(
      canonicalHomeworkText({ title: ' Задание ', description: ' Описание ', link: '' }),
    ).toEqual({ title: 'Задание', description: 'Описание', link: null })
  })
  it('keeps an explicit new occurrence on the same day and type in the PUT placement', () => {
    expect(
      homeworkPlacementChanged(homework, {
        bindingMode: 'LESSON',
        lessonDate: homework.lessonDate,
        lessonType: 'LECTURE',
        lessonNumber: 2,
        selectedOccurrenceId: '52',
        selectedOccurrenceRevision: '1',
      }),
    ).toBe(true)
    expect(
      homeworkPlacementChanged(homework, {
        bindingMode: 'LESSON',
        lessonDate: homework.lessonDate,
        lessonType: 'LECTURE',
        lessonNumber: 1,
        selectedOccurrenceId: '53',
        selectedOccurrenceRevision: '1',
      }),
    ).toBe(true)
  })
  it('does not confirm the desired placement when a lost edit readback has the old type or number', () => {
    const input = {
      title: homework.title,
      description: homework.description,
      link: null,
      requestKey: 'edit-key',
      expectedRevision: 4,
      bindingMode: 'LESSON' as const,
      lessonDate: homework.lessonDate,
      lessonType: 'PRACTICE' as const,
      lessonNumber: 2,
    }
    expect(homeworkEditMatches(homework, input)).toBe(false)
    expect(
      homeworkEditMatches({ ...homework, lessonType: 'PRACTICE', lessonNumber: 1 }, input),
    ).toBe(false)
    expect(
      homeworkEditMatches({ ...homework, lessonType: 'PRACTICE', lessonNumber: 2 }, input),
    ).toBe(true)
  })
})
