import { computed, shallowRef, type ComputedRef, type Ref } from 'vue'
import { offlineToday } from '@rct/mobile-core'
import type { StudentSemesterSchedule, StudentToday } from '../../mobile-core/src/api/types'

const MOSCOW_DAY_REFRESH_INTERVAL_MS = 60_000

export interface MoscowDayClockOptions {
  now?: () => Date
  setInterval?: (callback: () => void, delay: number) => number
  clearInterval?: (timer: number) => void
  addVisibilityListener?: (callback: () => void) => void
  removeVisibilityListener?: (callback: () => void) => void
  isVisible?: () => boolean
}

export function createMoscowDayClock(options: MoscowDayClockOptions = {}) {
  const now = options.now ?? (() => new Date())
  const setInterval = options.setInterval ?? ((callback, delay) => window.setInterval(callback, delay))
  const clearInterval = options.clearInterval ?? ((timer) => window.clearInterval(timer))
  const addVisibilityListener = options.addVisibilityListener
    ?? ((callback) => document.addEventListener('visibilitychange', callback))
  const removeVisibilityListener = options.removeVisibilityListener
    ?? ((callback) => document.removeEventListener('visibilitychange', callback))
  const isVisible = options.isVisible ?? (() => document.visibilityState === 'visible')
  const currentDate = shallowRef(now())
  let timer: number | null = null

  const refresh = (): void => {
    const nextDate = now()
    const currentDay = currentDate.value.toLocaleDateString('en-CA', { timeZone: 'Europe/Moscow' })
    const nextDay = nextDate.toLocaleDateString('en-CA', { timeZone: 'Europe/Moscow' })
    if (currentDay !== nextDay) currentDate.value = nextDate
  }
  const onVisibilityChange = (): void => {
    if (isVisible()) refresh()
  }

  return {
    currentDate,
    start(): void {
      if (timer !== null) return
      refresh()
      timer = setInterval(refresh, MOSCOW_DAY_REFRESH_INTERVAL_MS)
      addVisibilityListener(onVisibilityChange)
    },
    stop(): void {
      if (timer === null) return
      clearInterval(timer)
      timer = null
      removeVisibilityListener(onVisibilityChange)
    },
  }
}

export function useOfflineTodayFallback(
  getSchedule: () => StudentSemesterSchedule | null,
  currentDate: Readonly<Ref<Date>>,
): ComputedRef<StudentToday | null> {
  return computed(() => {
    const schedule = getSchedule()
    return schedule ? offlineToday(schedule, currentDate.value) : null
  })
}
