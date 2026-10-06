import type { HeadmanRequestLesson } from './headman-requests-client'

/** Schedule DTO exposes LocalTime, without a date or timezone conversion. */
export function formatRequestLessonTime(lesson: Pick<HeadmanRequestLesson, 'startsAt' | 'endsAt' | 'lessonNumber'>): string {
  const localTime = /^([01]\d|2[0-3]):([0-5]\d)(?::[0-5]\d(?:\.\d{1,9})?)?$/
  const start = lesson.startsAt?.match(localTime), end = lesson.endsAt?.match(localTime)
  return start && end ? `${start[1]}:${start[2]}–${end[1]}:${end[2]}` : `${lesson.lessonNumber ?? '—'} пара`
}
