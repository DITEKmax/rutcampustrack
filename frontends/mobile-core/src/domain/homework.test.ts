import { describe, expect, it } from 'vitest'
import type { StudentHomework, StudentHomeworkItem } from '../api/types'
import {
  formatHomeworkDate,
  groupHomework,
  isCompletedToday,
  moscowDate,
  previousHomeworkRange,
  validateHomeworkLink,
} from './homework'

function item(overrides: Partial<StudentHomeworkItem> = {}): StudentHomeworkItem {
  return {
    id: '1',
    subject: { id: 'subject-1', name: 'Математика' },
    title: 'Практика',
    description: 'Решить задачи.',
    link: null,
    lessonDate: '2026-09-01',
    lessonNumber: 1,
    bindingMode: 'LESSON',
    archived: false,
    completed: false,
    completedAt: null,
    ...overrides,
  }
}

function feed(items: StudentHomeworkItem[]): StudentHomework {
  return {
    semester: { id: '9', name: 'Осень 2026', dateFrom: '2026-08-20', dateTo: '2026-12-31' },
    from: '2026-09-01',
    to: '2026-09-30',
    serverNow: '2026-09-07T09:30:00Z',
    items,
  }
}

describe('homework feed domain', () => {
  it('groups a completed past lesson by its Moscow completion date and removes duplicates', () => {
    const completed = item({ id: 'completed', lessonDate: '2026-09-01', completed: true, completedAt: '2026-09-07T06:30:00Z' })
    const pending = item({ id: 'pending', lessonDate: '2026-09-02', lessonNumber: 2, subject: { id: 'subject-2', name: 'Сети' } })
    const groups = groupHomework(feed([completed, pending, completed]))

    expect(groups.map((group) => group.label)).toEqual(['Выполнено сегодня', '2 сентября'])
    expect(groups[0]?.items.map((entry) => entry.id)).toEqual(['completed'])
    expect(groups[1]?.items.map((entry) => entry.id)).toEqual(['pending'])
  })

  it('uses serverNow timezone around Moscow midnight and does not infer from the boolean', () => {
    expect(moscowDate('2026-09-06T20:59:59Z')).toBe('2026-09-06')
    expect(moscowDate('2026-09-06T21:00:00Z')).toBe('2026-09-07')
    expect(isCompletedToday(item({ completed: true, completedAt: '2026-09-06T20:59:59Z' }), '2026-09-07T09:30:00Z')).toBe(false)
    expect(isCompletedToday(item({ completed: true, completedAt: null }), '2026-09-07T09:30:00Z')).toBe(false)
    expect(formatHomeworkDate('2026-09-07', '2026-09-07T09:30:00Z')).toBe('Сегодня, 7 сентября')
    expect(formatHomeworkDate('2026-09-08', '2026-09-07T09:30:00Z')).toBe('Завтра, 8 сентября')
  })

  it('bounds previous pages by the semester start', () => {
    expect(previousHomeworkRange(feed([]), 7)).toEqual({ from: '2026-08-25', to: '2026-08-31' })
    expect(previousHomeworkRange({ ...feed([]), from: '2026-08-22' }, 7)).toEqual({ from: '2026-08-20', to: '2026-08-21' })
    expect(previousHomeworkRange({ ...feed([]), from: '2026-08-20' }, 7)).toBeNull()
  })

  it('accepts only absolute HTTP(S) materials and preserves the original target', () => {
    expect(validateHomeworkLink('https://example.test/homework?id=1')).toEqual({ supported: true, original: 'https://example.test/homework?id=1' })
    expect(validateHomeworkLink('HTTP://example.test/item')).toEqual({ supported: true, original: 'HTTP://example.test/item' })
    expect(validateHomeworkLink(null)).toEqual({ supported: false, reason: 'missing' })
    expect(validateHomeworkLink('//example.test/item').supported).toBe(false)
    expect(validateHomeworkLink('/homework/1').supported).toBe(false)
    expect(validateHomeworkLink('javascript:alert(1)').supported).toBe(false)
    expect(validateHomeworkLink('data:text/plain,unsafe').supported).toBe(false)
    expect(validateHomeworkLink('ftp://example.test/item').supported).toBe(false)
  })
})
