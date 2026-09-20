import { describe, expect, it } from 'vitest'
import { offlineToday } from './offline-today'
import type { StudentSemesterSchedule } from '../api/types'

const semester: StudentSemesterSchedule = {
  semester: { id: '9', name: 'Осень 2026', startsOn: '2026-09-01', endsOn: '2026-12-31' },
  dateFrom: '2026-09-01', dateTo: '2026-12-31', updatedAt: '2026-09-06T08:25:00Z', _links: {},
  lessons: [
    { id: 'old', date: '2026-09-05', lessonNumber: 1, startsAt: '09:00:00', endsAt: '10:30:00', status: 'CLOSED', subject: { id: '1', name: 'Математика', type: 'LECTURE' }, room: { current: 'А-1', previous: null, changeState: 'UNCHANGED' } },
    { id: 'current', date: '2026-09-06', lessonNumber: 2, startsAt: '11:20:00', endsAt: '12:50:00', status: 'ACTIVE', subject: { id: '2', name: 'Физика', type: 'PRACTICE' }, room: { current: 'А-2', previous: null, changeState: 'UNCHANGED' } },
  ],
}

describe('offline Today', () => {
  it('projects Moscow today from a full-semester snapshot and never treats eligibility as fresh', () => {
    const today = offlineToday(semester, new Date('2026-09-06T08:30:00Z'))
    expect(today.lessons.map((lesson) => lesson.schedule.id)).toEqual(['current'])
    expect(today.lessons[0]?.attendance).toBeNull()
    expect(today.lessons[0]?.checkinEligibility).toEqual({ allowed: false, reason: 'DEPENDENCY_UNAVAILABLE', retryAt: null })
    expect(today.serverNow).toBe(semester.updatedAt)
  })
})
