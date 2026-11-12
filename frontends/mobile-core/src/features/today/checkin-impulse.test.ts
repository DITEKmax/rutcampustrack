import { effectScope, nextTick, ref, type EffectScope } from 'vue'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { StudentApiError } from '../../api/student-client'
import type { StudentCheckinAck, StudentToday, TodayLesson } from '../../api/types'
import { checkinArrivalGeometry, useCheckinImpulse } from './checkin-impulse'

const lessonId = '9007199254740993'
const date = '2026-11-06'
const serverNow = `${date}T08:30:00Z`
const current: TodayLesson = {
  schedule: {
    id: lessonId,
    date,
    startsAt: '11:30:00',
    endsAt: '13:00:00',
    lessonNumber: 2,
    status: 'ACTIVE',
    subject: { id: '7', name: 'Точная пара', type: 'LAB' },
    room: { current: '417', previous: null, changeState: 'UNCHANGED' },
  },
  attendance: null,
  request: null,
  checkinEligibility: { allowed: true, reason: 'ELIGIBLE', retryAt: null },
}
const projection = (lesson: TodayLesson = current): StudentToday => ({
  date,
  serverNow,
  timeZone: 'Europe/Moscow',
  _links: {},
  lessons: [lesson],
})
const present: StudentCheckinAck = {
  lessonId,
  serverNow,
  outcome: 'PRESENT',
  retryAt: null,
  request: null,
  _links: {},
  attendance: { status: 'PRESENT', source: 'STUDENT_GEO', markedAt: serverNow },
}
const pending: StudentCheckinAck = {
  ...present,
  outcome: 'PENDING_CONFIRMATION',
  attendance: null,
  request: { id: '123', status: 'PENDING', origin: 'AUTO_GEO_FAILURE', resolutionReason: null },
}
const at = (milliseconds: number): string =>
  new Date(Date.parse(serverNow) + milliseconds).toISOString()
const cooldownRejection = () =>
  new StudentApiError(new Response(null, { status: 429, headers: { 'Retry-After': '7' } }), {
    status: 429,
    title: 'Отметка пока недоступна',
    detail: 'Попробуй ещё раз позже.',
    instance: '/api/v1/student/lessons/77/checkin',
    type: 'urn:rct:problem:checkin-cooldown',
    code: 'CHECKIN_COOLDOWN',
    timestamp: serverNow,
    retryAt: at(7000),
  })
const duringCooldown = (retryAt: string, observedAt = serverNow): StudentToday => ({
  ...projection({
    ...current,
    request: pending.request,
    checkinEligibility: { allowed: false, reason: 'COOLDOWN', retryAt },
  }),
  serverNow: observedAt,
})

const scopes: EffectScope[] = []
beforeEach(() => {
  vi.useFakeTimers()
  vi.setSystemTime(new Date(serverNow))
})
afterEach(() => {
  for (const scope of scopes.splice(0)) scope.stop()
  vi.clearAllTimers()
  vi.useRealTimers()
})

function setup(ack: StudentCheckinAck = present, reduced = false) {
  const scope = effectScope()
  scopes.push(scope)
  const data = ref(projection())
  const route = ref<'impulse' | 'today' | 'other'>('today')
  const authority = ref('student|session')
  const authorized = ref(true)
  const read = vi.fn(async () => {})
  const submit = vi.fn<
    (lesson: TodayLesson, isCurrent: () => boolean) => Promise<StudentCheckinAck>
  >(async () => ack)
  const navigateImpulse = vi.fn(() => {
    route.value = 'impulse'
  })
  const navigateToday = vi.fn(() => {
    route.value = 'today'
  })
  const focusToday = vi.fn()
  const measure = vi.fn(() =>
    checkinArrivalGeometry({ left: 95, top: 358, width: 200, height: 200 }, 390, 760),
  )
  const impulse = scope.run(() =>
    useCheckinImpulse({
      authority: () => authority.value,
      route: () => route.value,
      offline: () => false,
      readOnly: () => false,
      data: () => data.value,
      read,
      submit,
      pendingStage: () => null,
      busy: () => false,
      onReadError: vi.fn(),
      navigateImpulse,
      navigateToday,
      focusToday,
      measure,
      reduceMotion: () => reduced,
    }),
  )!
  const start = () => impulse.start(lessonId, () => authorized.value, () => route.value === 'today')
  return {
    start,
    impulse,
    route,
    authority,
    authorized,
    data,
    read,
    submit,
    navigateImpulse,
    navigateToday,
    focusToday,
    measure,
  }
}

describe('check-in impulse route', () => {
  it('opens a stale Telegram link on Today when a fresh read sees the headman mark', async () => {
    const test = setup()
    test.read.mockImplementationOnce(async () => {
      test.data.value = projection({
        ...current,
        attendance: { status: 'PRESENT', source: 'HEADMAN', markedAt: serverNow },
        checkinEligibility: { allowed: false, reason: 'ALREADY_PRESENT', retryAt: null },
      })
    })
    expect(await test.start()).toBe(true)
    expect(test.navigateImpulse).not.toHaveBeenCalled()
    expect(test.navigateToday).toHaveBeenCalledOnce()
    await test.impulse.submit()
    expect(test.submit).not.toHaveBeenCalled()
  })

  it('returns an open or directly navigated impulse to Today and fences a stale command', async () => {
    const direct = setup()
    direct.route.value = 'impulse'
    expect(direct.route.value).toBe('today')
    expect(direct.read).not.toHaveBeenCalled()

    const test = setup()
    expect(await test.start()).toBe(true)
    let finish!: (ack: StudentCheckinAck) => void
    test.submit.mockImplementationOnce(() => new Promise((resolve) => { finish = resolve }))
    const command = test.impulse.submit()
    test.data.value = projection({
      ...current,
      attendance: { status: 'PRESENT', source: 'HEADMAN', markedAt: serverNow },
      checkinEligibility: { allowed: false, reason: 'ALREADY_PRESENT', retryAt: null },
    })
    expect(test.route.value).toBe('today')
    expect(test.submit.mock.calls[0]?.[1]()).toBe(false)
    finish(pending)
    await command
    expect(test.impulse.arrival.value).toBeNull()
  })
  it('does not offer a command from cached data or substitute another active lesson', async () => {
    const test = setup()
    let completeRead!: () => void
    test.read.mockImplementationOnce(
      () =>
        new Promise((resolve) => {
          completeRead = resolve
        }),
    )
    test.start()
    expect(test.impulse.state.value).toBe('loading')
    await test.impulse.submit()
    test.data.value = projection({
      ...current,
      schedule: { ...current.schedule, id: '9007199254740992' },
    })
    completeRead()
    await nextTick()
    expect(test.route.value).toBe('today')
    expect(test.navigateImpulse).not.toHaveBeenCalled()
    await test.impulse.submit()
    expect(test.submit).not.toHaveBeenCalled()
  })

  it('covers from the measured button and focuses mounted Today only after reveal', async () => {
    const test = setup()
    test.start()
    await nextTick()
    await test.impulse.submit()
    expect(test.submit.mock.calls[0]?.[0].schedule.id).toBe(lessonId)
    expect(test.impulse.state.value).toBe('success')
    expect(test.impulse.animate.value).toBe(true)
    await vi.advanceTimersByTimeAsync(899)
    expect(test.impulse.arrival.value).toBeNull()
    await vi.advanceTimersByTimeAsync(1)
    expect(test.impulse.arrival.value?.phase).toBe('covering')
    expect(test.impulse.arrival.value?.geometry.radius).toBeGreaterThan(Math.hypot(195, 458))
    await vi.advanceTimersByTimeAsync(550)
    expect(test.impulse.arrival.value?.phase).toBe('covered')
    expect(test.route.value).toBe('today')
    expect(test.focusToday).not.toHaveBeenCalled()
    await vi.advanceTimersByTimeAsync(40)
    expect(test.impulse.arrival.value?.phase).toBe('revealing')
    await vi.advanceTimersByTimeAsync(280)
    expect(test.impulse.arrival.value).toBeNull()
    expect(test.focusToday).toHaveBeenCalledTimes(1)
  })

  it('preserves pending without mint or automatic navigation; only explicit Today leaves', async () => {
    const test = setup(pending)
    test.start()
    await nextTick()
    await test.impulse.submit()
    expect(test.impulse.state.value).toBe('pending')
    expect(test.impulse.canSubmit.value).toBe(false)
    await vi.advanceTimersByTimeAsync(2000)
    expect(test.impulse.animate.value).toBe(false)
    expect(test.navigateToday).not.toHaveBeenCalled()
    test.impulse.goToday()
    expect(test.route.value).toBe('today')
    expect(test.submit).toHaveBeenCalledTimes(1)
  })

  it('waits for the newer Today deadline and its fresh eligibility before a second press', async () => {
    const test = setup({ ...pending, retryAt: at(1000) })
    test.start()
    await nextTick()
    let finishRead!: () => void
    test.read
      .mockImplementationOnce(async () => {
        test.data.value = duringCooldown(at(7000))
      })
      .mockImplementationOnce(
        () =>
          new Promise((resolve) => {
            finishRead = () => {
              test.data.value = projection({ ...current, request: pending.request })
              resolve()
            }
          }),
      )
    await test.impulse.submit()
    expect(test.impulse.state.value).toBe('pending')
    await vi.advanceTimersByTimeAsync(6999)
    expect(test.impulse.canSubmit.value).toBe(false)
    expect(test.read).toHaveBeenCalledTimes(2)
    await test.impulse.submit()
    expect(test.submit).toHaveBeenCalledTimes(1)
    await vi.advanceTimersByTimeAsync(1)
    expect(test.read).toHaveBeenCalledTimes(3)
    expect(test.impulse.state.value).toBe('pending')
    expect(test.impulse.canSubmit.value).toBe(false)
    finishRead()
    await nextTick()
    expect(test.impulse.canSubmit.value).toBe(true)
    expect(test.submit).toHaveBeenCalledTimes(1)
    expect(test.navigateToday).not.toHaveBeenCalled()
    test.submit.mockResolvedValueOnce(present)
    await test.impulse.submit()
    expect(test.submit).toHaveBeenCalledTimes(2)
  })

  it('keeps a repeated expired cooldown blocked without a GET loop or automatic POST', async () => {
    const retryAt = at(2000)
    const test = setup({ ...pending, retryAt })
    test.start()
    await nextTick()
    test.read
      .mockImplementationOnce(async () => {
        test.data.value = duringCooldown(retryAt)
      })
      .mockImplementationOnce(async () => {
        test.data.value = duringCooldown(retryAt, at(2000))
      })
    await test.impulse.submit()
    await vi.advanceTimersByTimeAsync(12_000)
    expect(test.impulse.state.value).toBe('pending')
    expect(test.impulse.canSubmit.value).toBe(false)
    expect(test.impulse.canRetryRead.value).toBe(true)
    expect(test.read).toHaveBeenCalledTimes(3)
    expect(test.submit).toHaveBeenCalledTimes(1)
  })

  it('holds eligibility after a 429 until its newer server deadline and fresh Today read', async () => {
    const test = setup()
    test.start()
    await nextTick()
    test.submit.mockRejectedValueOnce(cooldownRejection())
    await test.impulse.submit()
    await vi.advanceTimersByTimeAsync(6999)
    expect(test.impulse.state.value).toBe('cooldown')
    expect(test.impulse.canSubmit.value).toBe(false)
    await test.impulse.submit()
    expect(test.submit).toHaveBeenCalledTimes(1)
    expect(test.read).toHaveBeenCalledTimes(2)
    test.read.mockRejectedValueOnce(new TypeError('offline during revalidation'))
    await vi.advanceTimersByTimeAsync(1)
    expect(test.read).toHaveBeenCalledTimes(3)
    expect(test.impulse.state.value).toBe('error')
    expect(test.impulse.canSubmit.value).toBe(false)
    await test.impulse.load()
    expect(test.read).toHaveBeenCalledTimes(4)
    expect(test.impulse.canSubmit.value).toBe(true)
    expect(test.submit).toHaveBeenCalledTimes(1)
  })

  it('shows a fresh closed window with no pending request after a 429 and drops the old timer', async () => {
    const test = setup()
    test.start()
    await nextTick()
    test.submit.mockRejectedValueOnce(cooldownRejection())
    test.read.mockImplementationOnce(async () => {
      test.data.value = projection({
        ...current,
        checkinEligibility: { allowed: false, reason: 'WINDOW_CLOSED', retryAt: null },
      })
    })

    await test.impulse.submit()
    expect(test.impulse.state.value).toBe('closed')
    expect(test.impulse.feedback.value).toBe('Время отметки закончилось')
    expect(test.impulse.commandError.value).toBe('')
    expect(test.impulse.canSubmit.value).toBe(false)
    expect(test.impulse.canRetryRead.value).toBe(false)
    await vi.advanceTimersByTimeAsync(8000)
    expect(test.read).toHaveBeenCalledTimes(2)
    expect(test.submit).toHaveBeenCalledTimes(1)
  })

  it('starts no effect for a different acknowledgement or after leaving during the command', async () => {
    const wrong = setup({ ...present, lessonId: '78' })
    wrong.start()
    await nextTick()
    await wrong.impulse.submit()
    expect(wrong.impulse.state.value).toBe('ready')
    const stale = setup()
    let resolve!: (ack: StudentCheckinAck) => void
    stale.submit.mockImplementationOnce(
      () =>
        new Promise((done) => {
          resolve = done
        }),
    )
    stale.start()
    await nextTick()
    const command = stale.impulse.submit()
    stale.route.value = 'other'
    expect(stale.submit.mock.calls[0]?.[1]()).toBe(false)
    resolve(present)
    await command
    await vi.advanceTimersByTimeAsync(1800)
    expect(wrong.navigateToday).not.toHaveBeenCalled()
    expect(stale.navigateToday).not.toHaveBeenCalled()
    expect(stale.impulse.arrival.value).toBeNull()
  })

  it('cleans up an active cover on authority change without late navigation or focus', async () => {
    const test = setup()
    test.start()
    await nextTick()
    await test.impulse.submit()
    await vi.advanceTimersByTimeAsync(1450)
    expect(test.route.value).toBe('today')
    expect(test.impulse.arrival.value).not.toBeNull()
    test.authorized.value = false
    expect(test.impulse.arrival.value).toBeNull()
    await vi.advanceTimersByTimeAsync(2000)
    expect(test.navigateToday).toHaveBeenCalledTimes(1)
    expect(test.focusToday).not.toHaveBeenCalled()
  })

  it('shows static confirmation and then focuses Today at 900ms for reduced motion', async () => {
    const test = setup(present, true)
    test.start()
    await nextTick()
    await test.impulse.submit()
    expect(test.impulse.state.value).toBe('success')
    expect(test.impulse.animate.value).toBe(false)
    await vi.advanceTimersByTimeAsync(899)
    expect(test.route.value).toBe('impulse')
    await vi.advanceTimersByTimeAsync(1)
    expect(test.measure).not.toHaveBeenCalled()
    expect(test.impulse.arrival.value).toBeNull()
    expect(test.route.value).toBe('today')
    expect(test.focusToday).toHaveBeenCalledTimes(1)
  })
})
