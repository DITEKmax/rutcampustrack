import { describe, expect, it, vi } from 'vitest'
import { shallowRef } from 'vue'
import type { StudentSemesterSchedule } from '../../mobile-core/src/api/types'
import { createMoscowDayClock, useOfflineTodayFallback } from './offline-today-fallback'

const schedule: StudentSemesterSchedule = {
  semester: { id: '9', name: 'Осень 2026', startsOn: '2026-09-01', endsOn: '2026-12-31' },
  dateFrom: '2026-09-01',
  dateTo: '2026-12-31',
  updatedAt: '2026-09-27T08:25:00Z',
  _links: {},
  lessons: ['2026-09-27', '2026-09-28', '2026-09-29'].map((date, index) => ({
    id: date,
    date,
    lessonNumber: index + 1,
    startsAt: '09:00:00',
    endsAt: '10:30:00',
    status: 'ACTIVE',
    subject: { id: String(index + 1), name: 'Математика', type: 'LECTURE' },
    room: { current: 'А-1', previous: null, changeState: 'UNCHANGED' },
  })),
}

describe('offline Today current Moscow day', () => {
  it('reprojects a cached schedule on rollover and visibility return, then disposes its clock', () => {
    let now = new Date('2026-09-27T20:59:00Z')
    const callbacks: { interval?: () => void; visibility?: () => void } = {}
    let visible = true
    const clearTimer = vi.fn()
    const removeVisibilityListener = vi.fn()
    const clock = createMoscowDayClock({
      now: () => now,
      setInterval: (callback, delay) => {
        expect(delay).toBe(60_000)
        callbacks.interval = callback
        return 7
      },
      clearInterval: clearTimer,
      addVisibilityListener: (callback) => { callbacks.visibility = callback },
      removeVisibilityListener,
      isVisible: () => visible,
    })
    const snapshot = shallowRef({ cachedAt: '2026-09-27T20:58:00Z', schedule })
    const today = useOfflineTodayFallback(() => snapshot.value.schedule, clock.currentDate)

    clock.start()
    expect(today.value?.lessons.map((lesson) => lesson.schedule.id)).toEqual(['2026-09-27'])

    now = new Date('2026-09-27T21:01:00Z')
    callbacks.interval?.()
    expect(today.value?.date).toBe('2026-09-28')
    expect(today.value?.lessons.map((lesson) => lesson.schedule.id)).toEqual(['2026-09-28'])
    expect(snapshot.value.cachedAt).toBe('2026-09-27T20:58:00Z')
    expect(today.value?.serverNow).toBe(schedule.updatedAt)
    expect(today.value?.lessons[0]?.checkinEligibility).toEqual({
      allowed: false,
      reason: 'DEPENDENCY_UNAVAILABLE',
      retryAt: null,
    })

    visible = false
    now = new Date('2026-09-28T21:01:00Z')
    callbacks.visibility?.()
    expect(today.value?.date).toBe('2026-09-28')
    visible = true
    callbacks.visibility?.()
    expect(today.value?.date).toBe('2026-09-29')
    expect(today.value?.lessons.map((lesson) => lesson.schedule.id)).toEqual(['2026-09-29'])

    clock.stop()
    expect(clearTimer).toHaveBeenCalledOnce()
    expect(clearTimer).toHaveBeenCalledWith(7)
    expect(removeVisibilityListener).toHaveBeenCalledWith(callbacks.visibility)
  })
})
