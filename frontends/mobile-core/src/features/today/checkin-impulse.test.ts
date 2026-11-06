import { effectScope, nextTick, ref, type EffectScope } from 'vue'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
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
  const route = ref<'impulse' | 'today' | 'other'>('impulse')
  const authority = ref('student|session')
  const authorized = ref(true)
  const read = vi.fn(async () => {})
  const submit = vi.fn<(lesson: TodayLesson, isCurrent: () => boolean) => Promise<StudentCheckinAck>>(
    async () => ack,
  )
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
      navigateToday,
      focusToday,
      measure,
      reduceMotion: () => reduced,
    }),
  )!
  const start = () => impulse.start(lessonId, () => authorized.value)
  return {
    start,
    impulse,
    route,
    authority,
    authorized,
    data,
    read,
    submit,
    navigateToday,
    focusToday,
    measure,
  }
}

describe('check-in impulse route', () => {
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
    expect(test.impulse.state.value).toBe('unavailable')
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

  it('rechecks geo cooldown with Today and waits for a second press', async () => {
    const retryAt = new Date(Date.parse(serverNow) + 2000).toISOString()
    const test = setup({ ...pending, retryAt })
    test.start()
    await nextTick()
    await test.impulse.submit()
    expect(test.impulse.state.value).toBe('pending')
    await vi.advanceTimersByTimeAsync(1999)
    expect(test.impulse.canSubmit.value).toBe(false)
    expect(test.read).toHaveBeenCalledTimes(1)
    await vi.advanceTimersByTimeAsync(1)
    expect(test.read).toHaveBeenCalledTimes(2)
    expect(test.impulse.canSubmit.value).toBe(true)
    expect(test.submit).toHaveBeenCalledTimes(1)
    expect(test.navigateToday).not.toHaveBeenCalled()
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
