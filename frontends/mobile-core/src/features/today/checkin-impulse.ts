import { computed, nextTick, ref, shallowRef, watch } from 'vue'
import { StudentApiError } from '../../api/student-client'
import type { StudentCheckinAck, StudentToday, TodayLesson } from '../../api/types'
import {
  CheckinBeforeSendCancelled,
  eligibilityLabel,
  isPendingCheckin,
} from '../../domain/checkin'

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
type ReadState = 'loading' | 'refreshing' | 'ready' | 'error'
type Arrival = { phase: 'covering' | 'covered' | 'revealing'; geometry: CheckinArrivalGeometry }
type Cooldown = { key: string; dueAt: number }

function rejectedCooldown(error: StudentApiError): Cooldown | null {
  if (error.response.status !== 429 || error.problem?.code !== 'CHECKIN_COOLDOWN') return null
  const retryAt = error.problem.retryAt
  const retryAfter = error.response.headers.get('Retry-After')
  const dueAt =
    retryAfter && /^\d+$/.test(retryAfter)
      ? Date.now() + Number(retryAfter) * 1000
      : Date.parse(retryAt ?? '')
  if (!Number.isFinite(dueAt)) return null
  return { key: retryAt ?? String(dueAt), dueAt }
}

/** Owns only the route attempt and presentation. The normal Today query owns all server data. */
export function useCheckinImpulse(options: ImpulseOptions) {
  const launch = shallowRef<Launch | null>(null)
  const readState = ref<ReadState>('loading')
  const commandError = ref('')
  const pendingAck = shallowRef<StudentCheckinAck | null>(null)
  const rejectionCooldown = shallowRef<Cooldown | null>(null)
  const projectionClock = shallowRef<{ serverNow: number; receivedAt: number } | null>(null)
  const success = ref(false)
  const animate = ref(false)
  const arrival = shallowRef<Arrival | null>(null)
  const arrivingToday = ref(false)
  const now = ref(Date.now())
  const timers = new Set<ReturnType<typeof setTimeout>>()
  const checkedCooldowns = new Set<string>()
  let readVersion = 0
  let commandVersion = 0

  const currentLesson = computed(() => {
    const id = launch.value?.lessonId
    if (!id) return null
    return options.data()?.lessons.find((item) => item.schedule.id === id) ?? null
  })
  const lesson = computed(() => {
    if (readState.value === 'loading') return null
    if (readState.value === 'error' && !pendingAck.value) return null
    return currentLesson.value
  })
  const eligibility = computed(() => lesson.value?.checkinEligibility)
  const pending = computed(
    () => pendingAck.value !== null || (lesson.value !== null && isPendingCheckin(lesson.value)),
  )
  const cooldown = computed<Cooldown | null>(() => {
    const clock = projectionClock.value
    const retryAt = eligibility.value?.reason === 'COOLDOWN' ? eligibility.value.retryAt : null
    const serverDeadline = Date.parse(retryAt ?? '')
    const projection =
      clock && retryAt && Number.isFinite(serverDeadline)
        ? { key: retryAt, dueAt: clock.receivedAt + serverDeadline - clock.serverNow }
        : null
    const rejection = rejectionCooldown.value
    return projection && (!rejection || projection.dueAt >= rejection.dueAt)
      ? projection
      : rejection
  })
  const countdown = computed(() => {
    const value = cooldown.value
    if (!value) return 0
    return Math.max(0, Math.ceil((value.dueAt - now.value) / 1000))
  })
  const canRetryRead = computed(
    () =>
      readState.value === 'error' ||
      (readState.value === 'ready' &&
        (eligibility.value?.reason === 'COOLDOWN' || rejectionCooldown.value !== null) &&
        (countdown.value === 0 || checkedCooldowns.has(cooldown.value?.key ?? ''))),
  )
  const cooldownMessage = computed(() => {
    if (canRetryRead.value || countdown.value === 0) return ''
    if (countdown.value < 60) return 'Проверим доступность меньше чем через минуту.'
    return `Проверим доступность через ${Math.ceil(countdown.value / 60)} мин.`
  })
  const eligibleToSend = computed(() => {
    const value = lesson.value
    if (
      !value ||
      readState.value !== 'ready' ||
      !launch.value ||
      !isActive(launch.value) ||
      options.readOnly() ||
      rejectionCooldown.value ||
      success.value ||
      value.attendance?.status === 'PRESENT' ||
      value.checkinEligibility.reason === 'ALREADY_PRESENT' ||
      (isPendingCheckin(value) && value.request?.origin !== 'AUTO_GEO_FAILURE') ||
      (pendingAck.value &&
        (pendingAck.value.request?.origin !== 'AUTO_GEO_FAILURE' || !pendingAck.value.retryAt))
    )
      return false
    return value.checkinEligibility.allowed && value.checkinEligibility.reason === 'ELIGIBLE'
  })
  const canSubmit = computed(() => !options.busy() && eligibleToSend.value)
  const state = computed(() => {
    if (success.value) return 'success'
    if (options.offline()) return 'offline'
    const stage = launch.value && options.pendingStage(launch.value.lessonId)
    if (stage) return stage
    if (readState.value === 'loading') return 'loading'
    if (readState.value === 'error') return 'error'
    if (readState.value === 'refreshing') return pending.value ? 'pending' : 'loading'
    if (!lesson.value) return 'unavailable'
    if (
      lesson.value.attendance?.status === 'PRESENT' ||
      lesson.value.checkinEligibility.reason === 'ALREADY_PRESENT'
    )
      return 'present'
    if (options.readOnly()) return 'readonly'
    if (canSubmit.value) return 'ready'
    if (pending.value) return 'pending'
    if (eligibility.value?.reason === 'COOLDOWN' || rejectionCooldown.value) return 'cooldown'
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
        return pending.value
          ? 'Запрос на подтверждении у старосты. Не удалось обновить доступность отметки.'
          : 'Не удалось загрузить пару. Попробуй ещё раз.'
      case 'unavailable':
        return 'Этой пары нет в твоём расписании на сегодня.'
      case 'readonly':
        return 'Отметка недоступна в режиме просмотра.'
      case 'locating':
        return 'Проверяем геолокацию…'
      case 'submitting':
        return 'Отправляем отметку…'
      case 'pending':
        return cooldownMessage.value
          ? `Запрос у старосты. ${cooldownMessage.value}`
          : 'Запрос на подтверждении у старосты.'
      case 'cooldown':
        return cooldownMessage.value || 'Отметка пока недоступна.'
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
    rejectionCooldown.value = null
    checkedCooldowns.clear()
    commandError.value = ''
    readState.value = 'loading'
  }

  async function load(value = launch.value): Promise<void> {
    if (!value || !isActive(value) || success.value) return
    const version = ++readVersion
    readState.value = readState.value === 'ready' || pendingAck.value ? 'refreshing' : 'loading'
    try {
      await options.read()
      if (!isActive(value) || version !== readVersion) return
      now.value = Date.now()
      const freshEligibility = currentLesson.value?.checkinEligibility
      if (
        freshEligibility?.allowed &&
        freshEligibility.reason === 'ELIGIBLE' &&
        rejectionCooldown.value &&
        now.value >= rejectionCooldown.value.dueAt
      ) {
        rejectionCooldown.value = null
        commandError.value = ''
      }
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
    const result = options.submit(target, () => isCurrent() && eligibleToSend.value)
    if (!result) return
    try {
      const ack = await result
      if (!isCurrent() || ack.lessonId !== value.lessonId) return
      if (ack.outcome === 'PRESENT') confirm(value)
      else {
        pendingAck.value = ack
        if (ack.retryAt) void load(value)
      }
    } catch (error) {
      if (!isCurrent() || error instanceof CheckinBeforeSendCancelled) return
      commandError.value =
        error instanceof Error && !(error instanceof TypeError) && error.message
          ? error.message
          : 'Не удалось отправить отметку. Попробуй ещё раз.'
      if (error instanceof StudentApiError && error.response.status < 500) {
        const rejected = rejectedCooldown(error)
        if (rejected) rejectionCooldown.value = rejected
        void load(value)
      }
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
    () => options.data()?.serverNow,
    (timestamp) => {
      const serverNow = Date.parse(timestamp ?? '')
      projectionClock.value = Number.isFinite(serverNow)
        ? { serverNow, receivedAt: Date.now() }
        : null
    },
    { flush: 'sync', immediate: true },
  )
  watch(
    () =>
      [launch.value, cooldown.value, readState.value, options.offline(), success.value] as const,
    ([value, deadline, read, offline, confirmed], _, onCleanup) => {
      if (!value || !deadline || read !== 'ready' || offline || confirmed) return
      now.value = Date.now()
      if (deadline.dueAt <= now.value || checkedCooldowns.has(deadline.key)) return
      let timer: ReturnType<typeof setTimeout>
      const tick = () => {
        now.value = Date.now()
        const remaining = deadline.dueAt - now.value
        if (remaining > 0) {
          timer = setTimeout(tick, Math.min(1000, remaining))
        } else if (isActive(value)) {
          checkedCooldowns.add(deadline.key)
          void load(value)
        }
      }
      tick()
      onCleanup(() => clearTimeout(timer))
    },
  )

  return {
    lesson,
    state,
    feedback,
    commandError,
    canSubmit,
    canRetryRead,
    animate,
    arrival,
    start,
    reset,
    load,
    submit,
    goToday,
  }
}
