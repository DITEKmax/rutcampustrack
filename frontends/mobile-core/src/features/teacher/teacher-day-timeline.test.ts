import { describe, expect, it } from 'vitest'
import type { TeacherDayLesson } from './teacher-client'
import { teacherDayTimeline } from './teacher-day-timeline'

const slots = [
  { lessonNumber: 1, startTime: '08:30:00', endTime: '10:00:00' },
  { lessonNumber: 2, startTime: '10:15:00', endTime: '11:45:00' },
  { lessonNumber: 3, startTime: '12:00:00', endTime: '13:30:00' },
]
function lesson(
  id: number,
  number: number,
  start: string | null,
  end: string | null,
): TeacherDayLesson {
  return {
    id,
    lessonNumber: number,
    startsAt: start,
    endsAt: end,
    assignmentId: 1,
    groupId: 1,
    groupName: 'УИТ-211',
    subjectId: 1,
    subjectName: 'Алгебра',
    semesterId: 7,
    lessonType: 'lecture',
    date: '2026-10-09',
    room: null,
    status: 'PLANNED',
    oneOff: true,
    moved: false,
    cancelled: false,
    cancellationReason: null,
  }
}
describe('teacher canonical day windows', () => {
  it('keeps actual custom intervals in chronological order and suppresses overlapping canonical windows', () => {
    const first = lesson(1, 5, '09:50:00', '10:30:00'),
      last = lesson(2, 3, '12:20:00', '13:40:00')
    expect(teacherDayTimeline([last, first], slots)).toEqual([
      { kind: 'lesson', lesson: first },
      { kind: 'lesson', lesson: last },
    ])
  })
  it('uses canonical time to position an occupied slot when the lesson interval is unavailable', () => {
    const second = lesson(2, 2, null, null)
    expect(teacherDayTimeline([second], slots)).toEqual([
      { kind: 'window', slot: slots[0] },
      { kind: 'lesson', lesson: second },
      { kind: 'window', slot: slots[2] },
    ])
  })
  it('preserves a cancelled occupied slot and never invents windows without the catalog', () => {
    const cancelled = {
      ...lesson(1, 1, '08:30:00', '10:00:00'),
      cancelled: true,
      status: 'CANCELLED' as const,
    }
    expect(
      teacherDayTimeline([cancelled], slots).filter((item) => item.kind === 'window'),
    ).toHaveLength(2)
    expect(teacherDayTimeline([], [])).toEqual([])
  })
})
