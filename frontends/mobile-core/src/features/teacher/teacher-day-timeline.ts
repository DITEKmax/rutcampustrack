import type { TeacherDayLesson } from './teacher-client'

export interface TeacherDaySlot {
  readonly lessonNumber: number
  readonly startTime: string
  readonly endTime: string
}
export type TeacherDayItem =
  | { readonly kind: 'lesson'; readonly lesson: TeacherDayLesson }
  | { readonly kind: 'window'; readonly slot: TeacherDaySlot }

function minute(value: string | null): number | null {
  const match = value?.match(/^(\d{2}):(\d{2})/)
  return match ? Number(match[1]) * 60 + Number(match[2]) : null
}

/** Actual lesson intervals win over the canonical empty-slot placeholders. */
export function teacherDayTimeline(
  lessons: readonly TeacherDayLesson[],
  slots: readonly TeacherDaySlot[],
): readonly TeacherDayItem[] {
  const windows = slots.filter(
    (slot) =>
      !lessons.some((lesson) => {
        if (lesson.lessonNumber === slot.lessonNumber) return true
        const start = minute(lesson.startsAt),
          end = minute(lesson.endsAt)
        const slotStart = minute(slot.startTime),
          slotEnd = minute(slot.endTime)
        return (
          start !== null &&
          end !== null &&
          slotStart !== null &&
          slotEnd !== null &&
          start < slotEnd &&
          end > slotStart
        )
      }),
  )
  const items: TeacherDayItem[] = [
    ...lessons.map((lesson) => ({ kind: 'lesson' as const, lesson })),
    ...windows.map((slot) => ({ kind: 'window' as const, slot })),
  ]
  return items.sort((a, b) => {
    const numberA = a.kind === 'lesson' ? a.lesson.lessonNumber : a.slot.lessonNumber
    const numberB = b.kind === 'lesson' ? b.lesson.lessonNumber : b.slot.lessonNumber
    const timeA =
      minute(a.kind === 'lesson' ? a.lesson.startsAt : a.slot.startTime) ??
      minute(slots.find((slot) => slot.lessonNumber === numberA)?.startTime ?? null)
    const timeB =
      minute(b.kind === 'lesson' ? b.lesson.startsAt : b.slot.startTime) ??
      minute(slots.find((slot) => slot.lessonNumber === numberB)?.startTime ?? null)
    return (
      (timeA ?? Number.MAX_SAFE_INTEGER) - (timeB ?? Number.MAX_SAFE_INTEGER) || numberA - numberB
    )
  })
}
