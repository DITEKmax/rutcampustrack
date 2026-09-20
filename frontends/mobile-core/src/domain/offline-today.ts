import type { StudentSemesterSchedule, StudentToday } from '../api/types'

export function offlineToday(schedule: StudentSemesterSchedule, date = new Date()): StudentToday {
  const today = date.toLocaleDateString('en-CA', { timeZone: 'Europe/Moscow' })
  return {
    date: today,
    timeZone: 'Europe/Moscow',
    serverNow: schedule.updatedAt,
    _links: schedule._links,
    lessons: schedule.lessons.filter((lesson) => lesson.date === today).map((schedule) => ({
      schedule,
      attendance: null,
      request: null,
      checkinEligibility: { allowed: false, reason: 'DEPENDENCY_UNAVAILABLE', retryAt: null },
    })),
  }
}
