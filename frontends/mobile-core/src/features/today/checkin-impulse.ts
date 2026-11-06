import { computed, nextTick, ref, shallowRef, watch } from 'vue'
import { StudentApiError } from '../../api/student-client'
import type { StudentCheckinAck, StudentToday, TodayLesson } from '../../api/types'
import { eligibilityLabel, isPendingCheckin, remainingSeconds } from '../../domain/checkin'

export interface CheckinArrivalGeometry {
  x: number
  y: number
  radius: number
  startScale: number
}

export function checkinArrivalGeometry(
  button: Pick<DOMRect, 'left' | 'top' | 'width' | 'height'>,
  width: number,
  height: number,
): CheckinArrivalGeometry {
  const x = button.left + button.width / 2
  const y = button.top + button.height / 2
  const radius = Math.ceil(Math.hypot(Math.max(x, width - x), Math.max(y, height - y))) + 8
  return { x, y, radius, startScale: Math.min(button.width, button.height) / (2 * radius) }
}

interface ImpulseOptions {
  authority: () => string | null
  route: () => 'impulse' | 'today' | 'other'
  offline: () => boolean
  readOnly: () => boolean
  data: () => StudentToday | null
  read: () => Promise<void>
  pendingStage: (lessonId: string) => 'locating' | 'submitting' | null
  busy: () => boolean
  submit: (lesson: TodayLesson, isCurrent: () => boolean) => Promise<StudentCheckinAck> | null
  onReadError: (error: unknown) => void
  navigateToday: () => void
  focusToday: () => void
  measure: () => CheckinArrivalGeometry | null
  reduceMotion: () => boolean
}

type Launch = { lessonId: string; authority: string; authorized: () => boolean }
type ReadState = 'loading' | 'ready' | 'error'
type Arrival = { phase: 'covering' | 'covered' | 'revealing'; geometry: CheckinArrivalGeometry }

/** Owns only the route attempt and presentation. The normal Today query owns all server data. */
export function useCheckinImpulse(options: ImpulseOptions) {
  const launch = shallowRef<Launch | null>(null)
  const readState = ref<ReadState>('loading')
  const commandError = ref('')
  const pendingAck = shallowRef<StudentCheckinAck | null>(null)
  const success = ref(false)
  const animate = ref(false)
  const arrival = shallowRef<Arrival | null>(null)
  const arrivingToday = ref(false)
  const now = ref(Date.now())
  const timers = new Set<ReturnType<typeof setTimeout>>()
  let readVersion = 0
  let commandVersion = 0

  const lesson = computed(() => {
    if (readState.value !== 'ready' || !launch.value) return null
    return (
      options.data()?.lessons.find((item) => item.schedule.id === launch.value?.lessonId) ?? null
    )
  })
  const retryAt = computed(
    () =>
      (pendingAck.value?.request?.origin === 'AUTO_GEO_FAILURE'
        ? pendingAck.value.retryAt
        : null) ??
      (lesson.value?.checkinEligibility.reason === 'COOLDOWN'
        ? lesson.value.checkinEligibility.retryAt
        : null),
  )
  const countdown = computed(() => {
    const serverNow = pendingAck.value?.serverNow ?? options.data()?.serverNow
    return serverNow ? remainingSeconds(serverNow, retryAt.value, now.value) : 0
  })
  const canSubmit = computed(() => {
    const value = lesson.value
    if (
      !value ||
      !launch.value ||
      !isActive(launch.value) ||
      options.readOnly() ||
      options.busy() ||
      success.value ||
      value.attendance?.status === 'PRESENT' ||
      value.checkinEligibility.reason === 'ALREADY_PRESENT' ||
      (isPendingCheckin(value) && value.request?.origin !== 'AUTO_GEO_FAILURE') ||
      (pendingAck.value &&
        (pendingAck.value.request?.origin !== 'AUTO_GEO_FAILURE' || !pendingAck.value.retryAt))
    )
      return false
    if (retryAt.value && countdown.value > 0) return false
    return (
      value.checkinEligibility.allowed ||
      (value.checkinEligibility.reason === 'COOLDOWN' && retryAt.value !== null)
    )
  })
  const state = computed(() => {
    if (success.value) return 'success'
    if (options.offline()) return 'offline'
    const stage = launch.value && options.pendingStage(launch.value.lessonId)
    if (stage) return stage
    if (readState.value === 'loading') return 'loading'
    if (readState.value === 'error') return 'error'
    if (!lesson.value) return 'unavailable'
    if (
      lesson.value.attendance?.status === 'PRESENT' ||
      lesson.value.checkinEligibility.reason === 'ALREADY_PRESENT'
    )
      return 'present'
    if (options.readOnly()) return 'readonly'
    if (canSubmit.value) return 'ready'
    if (pendingAck.value || isPendingCheckin(lesson.value)) return 'pending'
    return 'closed'
  })
  const feedback = computed(() => {
    switch (state.value) {
      case 'success':
        return 'Посещение подтверждено.'
      case 'present':
        return 'Ты уже отмечен на этой паре.'
      case 'offline':
        return 'Подключись к интернету, чтобы проверить доступность отметки.'
      case 'loading':
        return 'Проверяем доступность отметки…'
      case 'error':
        return 'Не удалось загрузить пару. Попробуй ещё раз.'
      case 'unavailable':
        return 'Этой пары нет в твоём расписании на сегодня.'
      case 'readonly':
        return 'Отметка недоступна в режиме просмотра.'
      case 'locating':
        return 'Проверяем геолокацию…'
      case 'submitting':
        return 'Отправляем отметку…'
      case 'pending':
        return retryAt.value && countdown.value > 0
          ? `Запрос у старосты. Повтори отметку через ${Math.ceil(countdown.value / 60)} мин.`
          : 'Запрос на подтверждении у старосты.'
      case 'closed':
        return lesson.value ? eligibilityLabel(lesson.value, countdown.value) : ''
      default:
        return ''
    }
  })

  function isActive(value: Launch): boolean {
    return launch.value === value && options.route() === 'impulse' && isAuthorized(value)
  }

  function isAuthorized(value: Launch): boolean {
    return (
      launch.value === value &&
      options.authority() === value.authority &&
      !options.offline() &&
      value.authorized()
    )
  }

  function clearTimers(): void {
    for (const timer of timers) clearTimeout(timer)
    timers.clear()
  }

  function later(ms: number, run: () => void): void {
    const timer = setTimeout(() => {
      timers.delete(timer)
      run()
    }, ms)
    timers.add(timer)
  }

  function reset(): void {
    clearTimers()
    arrival.value = null
    arrivingToday.value = false
    launch.value = null
    readVersion++
    commandVersion++
    success.value = false
    animate.value = false
    pendingAck.value = null
    commandError.value = ''
    readState.value = 'loading'
  }

  async function load(value = launch.value): Promise<void> {
    if (!value || !isActive(value) || success.value) return
    const version = ++readVersion
    readState.value = 'loading'
    try {
      await options.read()
      if (!isActive(value) || version !== readVersion) return
      now.value = Date.now()
      readState.value = 'ready'
    } catch (error) {
      if (!isActive(value) || version !== readVersion) return
      readState.value = 'error'
      options.onReadError(error)
    }
  }

  function start(lessonId: string, authorized: () => boolean): void {
    reset()
    const authority = options.authority()
    if (!authority) return
    launch.value = { lessonId, authority, authorized }
    void load()
  }

  async function showToday(value: Launch, started: number, cover: Arrival | null): Promise<void> {
    if (!isActive(value)) return
    arrivingToday.value = true
    if (cover) arrival.value = { ...cover, phase: 'covered' }
    options.navigateToday()
    await nextTick()
    const isAtToday = () => isAuthorized(value) && options.route() === 'today'
    if (!isAtToday()) return
    if (!cover) {
      reset()
      options.focusToday()
      return
    }
    later(Math.max(0, 1490 - (Date.now() - started)), () => {
      if (!isAtToday()) return
      arrival.value = { ...cover, phase: 'revealing' }
      later(280, () => {
        if (!isAtToday()) return
        reset()
        void nextTick(() => {
          if (
            options.authority() === value.authority &&
            value.authorized() &&
            options.route() === 'today'
          )
            options.focusToday()
        })
      })
    })
  }

  function confirm(value: Launch): void {
    pendingAck.value = null
    success.value = true
    animate.value = !options.reduceMotion()
    const started = Date.now()
    later(900, () => {
      if (!isActive(value)) return
      if (!animate.value) {
        void showToday(value, started, null)
        return
      }
      const geometry = options.measure()
      if (!geometry) {
        void showToday(value, started, null)
        return
      }
      const cover: Arrival = { phase: 'covering', geometry }
      arrival.value = cover
      later(550, () => void showToday(value, started, cover))
    })
  }

  async function submit(): Promise<void> {
    const value = launch.value
    const target = lesson.value
    if (!value || !target || !canSubmit.value) return
    commandError.value = ''
    const version = ++commandVersion
    const isCurrent = () => version === commandVersion && isActive(value)
    const result = options.submit(target, isCurrent)
    if (!result) return
    try {
      const ack = await result
      if (!isCurrent() || ack.lessonId !== value.lessonId) return
      if (ack.outcome === 'PRESENT') confirm(value)
      else {
        pendingAck.value = ack
        now.value = Date.now()
      }
    } catch (error) {
      if (!isCurrent()) return
      commandError.value =
        error instanceof Error && !(error instanceof TypeError) && error.message
          ? error.message
          : 'Не удалось отправить отметку. Попробуй ещё раз.'
      if (error instanceof StudentApiError && error.response.status < 500) void load(value)
    }
  }

  function goToday(): void {
    const authority = options.authority()
    reset()
    options.navigateToday()
    void nextTick(() => {
      if (authority && options.authority() === authority && options.route() === 'today')
        options.focusToday()
    })
  }

  function leaveInvalidatedRoute(): void {
    reset()
    if (options.route() === 'impulse') options.navigateToday()
  }

  watch(
    options.route,
    (route) => {
      if (route !== 'impulse' && !(route === 'today' && arrivingToday.value)) reset()
    },
    { flush: 'sync' },
  )
  watch(
    options.authority,
    (authority) => {
      if (launch.value && launch.value.authority !== authority) leaveInvalidatedRoute()
    },
    { flush: 'sync' },
  )
  watch(
    () => launch.value?.authorized(),
    (authorized) => {
      if (authorized === false && !options.offline()) leaveInvalidatedRoute()
    },
    { flush: 'sync' },
  )
  watch(
    options.offline,
    (offline) => {
      if (!launch.value) return
      if (offline) {
        if (options.route() !== 'impulse') {
          reset()
          return
        }
        clearTimers()
        arrival.value = null
        success.value = false
        animate.value = false
        readVersion++
        commandVersion++
        readState.value = 'loading'
      } else if (options.route() === 'impulse') {
        if (launch.value.authorized()) void load()
        else leaveInvalidatedRoute()
      }
    },
    { flush: 'sync' },
  )
  watch(
    () => [launch.value, retryAt.value, options.offline()] as const,
    ([value, retry, offline], _, onCleanup) => {
      if (!value || !retry || offline) return
      now.value = Date.now()
      const timer = setInterval(() => {
        now.value = Date.now()
        if (countdown.value === 0) clearInterval(timer)
      }, 1000)
      onCleanup(() => clearInterval(timer))
    },
  )
  watch(
    countdown,
    (seconds, previous) => {
      if (seconds === 0 && previous > 0 && retryAt.value && readState.value === 'ready') void load()
    },
    { flush: 'sync' },
  )

  return {
    lesson,
    state,
    feedback,
    commandError,
    canSubmit,
    animate,
    arrival,
    start,
    reset,
    load,
    submit,
    goToday,
  }
}
