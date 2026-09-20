import { describe, expect, it } from 'vitest'
import {
  displayPercent,
  findAttendanceLesson,
  graphForRange,
  hasEnabledRequestOption,
  lessonsForDate,
  type AttendanceLesson,
  type AttendanceViewModel,
} from './attendance-view-model'

const lesson = {
  id: 'opaque.lesson.42',
  date: '2026-09-08',
  number: '2',
  subject: { id: 'subject.opaque', name: 'Математический анализ' },
  type: 'LECTURE',
  schedule: { startsAt: '10:15:00', endsAt: '11:45:00', room: null },
  status: 'ABSENT',
  requestOptions: [{ id: 'request.opaque', kind: 'EXCUSE', label: 'Уважительная причина', enabled: true }],
} satisfies AttendanceLesson

const data = {
  metrics: {
    present: { count: 0, percent: null },
    presentOrExcused: { count: 0, percent: null },
    excused: { count: 0, percent: null },
    absent: { count: 0, percent: null },
    held: 0,
    planned: 1,
  },
  days: [{ date: lesson.date, weekday: 'вт', dayNumber: '8', state: 'CURRENT', lessons: [lesson] }],
  subjects: [],
  graph: {
    days: [{ id: 'day:opaque', label: '8', dateFrom: lesson.date, dateTo: lesson.date, state: 'NO_DATA', metrics: {
      present: { count: 0, percent: null }, presentOrExcused: { count: 0, percent: null }, excused: { count: 0, percent: null }, absent: { count: 0, percent: null }, held: 0, planned: 1,
    } }],
    weeks: [],
  },
} satisfies AttendanceViewModel

describe('attendance controlled projection helpers', () => {
  it('keeps null distinct from a real zero and formats a percentage for display', () => {
    expect(displayPercent(null)).toBe('—')
    expect(displayPercent(0)).toBe('0%')
    expect(displayPercent(71.4)).toBe('71%')
  })

  it('resolves lessons by opaque id and preserves the server date projection', () => {
    expect(findAttendanceLesson(data, 'opaque.lesson.42')).toBe(lesson)
    expect(findAttendanceLesson(data, '2')).toBeNull()
    expect(lessonsForDate(data, lesson.date)).toEqual([lesson])
    expect(graphForRange(data, 'days')).toBe(data.graph.days)
  })

  it('uses request option eligibility supplied by the server', () => {
    expect(hasEnabledRequestOption(lesson)).toBe(true)
    expect(hasEnabledRequestOption({ ...lesson, status: 'ABSENT', requestOptions: [] })).toBe(false)
  })
})