import { afterEach, describe, expect, it, vi } from 'vitest'
import { computed, createRenderer, defineComponent, ref, type App } from 'vue'
import type {
  StudentSession,
  StudentToday,
  TodayLesson,
} from '../../../../mobile-core/src/api/types'
import type { StudentScope } from './student-state'
import { useStudentCheckin } from './use-student-checkin'
vi.mock('../../app/live', () => ({ useWebLiveRefresh() {} }))
const apps: App[] = []
type Node = { children: Node[] }
const renderer = createRenderer<Node, Node>({
  createElement: () => ({ children: [] }),
  createText: () => ({ children: [] }),
  createComment: () => ({ children: [] }),
  insert(child, parent) {
    parent.children.push(child)
  },
  remove() {},
  setText() {},
  setElementText() {},
  patchProp() {},
  parentNode: () => null,
  nextSibling: () => null,
})
afterEach(() => {
  for (const app of apps.splice(0)) app.unmount()
  vi.useRealTimers()
  vi.unstubAllGlobals()
})
const lesson: TodayLesson = {
  schedule: {
    id: '77',
    date: '2026-11-09',
    startsAt: '10:00:00',
    endsAt: '11:30:00',
    lessonNumber: 1,
    status: 'ACTIVE',
    subject: { id: '1', name: 'Пара', type: 'LECTURE' },
    room: { current: '1', previous: null, changeState: 'UNCHANGED' },
  },
  attendance: null,
  request: null,
  checkinEligibility: { allowed: true, reason: 'ELIGIBLE', retryAt: null },
}
function fixture() {
  vi.useFakeTimers()
  const events = new EventTarget()
  vi.stubGlobal('window', Object.assign(events, { setInterval, clearInterval }))
  vi.stubGlobal('document', Object.assign(new EventTarget(), { visibilityState: 'visible' }))
  let sample!: PositionCallback
  let failed!: PositionErrorCallback
  const clearWatch = vi.fn()
  const watchPosition = vi.fn((onSample: PositionCallback, onFailed: PositionErrorCallback) => {
    sample = onSample
    failed = onFailed
    return 1
  })
  vi.stubGlobal('navigator', { geolocation: { watchPosition, clearWatch } })
  const session = ref({
    user: { id: '15' },
    capabilities: ['GEO_CHECKIN'],
  } as unknown as StudentSession)
  const api = {
    checkin: vi.fn().mockResolvedValue({ lessonId: '77', outcome: 'PRESENT' }),
    reportCheckinAcquisition: vi.fn().mockResolvedValue(undefined),
  }
  const scope = {
    identity: crypto.randomUUID(),
    session,
    contextKey: ref('authority'),
    readOnly: ref(false),
    dirty: ref(false),
    current: () => true,
    transport: { assertCurrent() {} },
    api,
  } as unknown as StudentScope
  let current = {
    lessons: [structuredClone(lesson)],
    checkinGeofence: { latitude: 55.7, longitude: 37.6, radiusMeters: 400 },
    serverNow: '2026-11-09T07:00:00Z',
  } as StudentToday
  const refresh = vi.fn(async () => {})
  let checkin!: ReturnType<typeof useStudentCheckin>
  const app = renderer.createApp(
    defineComponent({
      setup() {
        checkin = useStudentCheckin(scope, refresh, () => current)
        return () => null
      },
    }),
  )
  apps.push(app)
  app.mount({ children: [] })
  return {
    checkin,
    scope,
    api,
    refresh,
    watchPosition,
    clearWatch,
    setPolicyMissing(explicitNull = false) {
      if (explicitNull) {
        current = { ...current, checkinGeofence: null }
        return
      }
      const legacy = { ...current }
      delete legacy.checkinGeofence
      current = legacy
    },
    setClosed() {
      current = {
        ...current,
        lessons: [
          {
            ...lesson,
            checkinEligibility: { allowed: false, reason: 'WINDOW_CLOSED', retryAt: null },
          },
        ],
      }
    },
    emit(accuracy: number, latitude = 55.7) {
      sample({
        coords: { latitude, longitude: 37.6, accuracy },
        timestamp: Date.now(),
      } as GeolocationPosition)
    },
    fail() {
      failed({ code: 1 } as GeolocationPositionError)
    },
  }
}
async function settle() {
  for (let i = 0; i < 10; i++) await Promise.resolve()
}
describe('Web consumer of shared acquisition and immutable recovery', () => {
  it.each(['present', 'omitted', 'null'] as const)(
    'sends one final automatic pending request after three readings (policy=%s)',
    async (policyShape) => {
      const f = fixture()
      const legacy = policyShape !== 'present'
      if (legacy) f.setPolicyMissing(policyShape === 'null')
      f.api.checkin.mockResolvedValue({ lessonId: '77', outcome: 'PENDING_CONFIRMATION' })
      const submit = f.checkin.submit(lesson, '2026-11-09T07:00:00Z')
      await settle()
      f.emit(1, 55.71)
      await settle()
      expect(f.api.checkin).not.toHaveBeenCalled()
      await vi.advanceTimersByTimeAsync(1)
      f.emit(1, 55.72)
      await settle()
      expect(f.api.checkin).not.toHaveBeenCalled()
      await vi.advanceTimersByTimeAsync(1)
      f.emit(9000, 55.73)
      await submit
      expect(f.watchPosition).toHaveBeenCalledTimes(3)
      expect(f.api.checkin).toHaveBeenCalledOnce()
      expect(f.api.checkin.mock.calls[0]?.[1]).toMatchObject({
        geo: { latitude: 55.73 },
        diagnostics: { sampleCount: 3 },
      })
      expect(f.api.checkin.mock.calls[0]?.[1].diagnostics.completionReason).toBe(
        legacy ? undefined : 'ATTEMPTS_EXHAUSTED',
      )
      expect(f.api.reportCheckinAcquisition).toHaveBeenCalledExactlyOnceWith(
        expect.objectContaining({ outcome: 'ATTEMPTS_EXHAUSTED', sampleCount: 3 }),
      )
      expect(f.checkin.result.value).toContain('запрос старосте')
    },
  )
  it.each([false, true])(
    'automatically submits unavailable once after denial without another permission request or confirmation (legacy=%s)',
    async (legacy) => {
      const f = fixture()
      if (legacy) f.setPolicyMissing()
      f.api.checkin.mockResolvedValue({ lessonId: '77', outcome: 'PENDING_CONFIRMATION' })
      const submit = f.checkin.submit(lesson, '2026-11-09T07:00:00Z')
      await settle()
      f.fail()
      await submit
      expect(f.watchPosition).toHaveBeenCalledOnce()
      expect(f.api.checkin).toHaveBeenCalledOnce()
      expect(f.api.checkin.mock.calls[0]?.[1]).toMatchObject({
        geo: { kind: 'UNAVAILABLE', reason: 'PERMISSION_DENIED' },
        diagnostics: { completionReason: 'PERMISSION_DENIED' },
      })
    },
  )
  it('keeps a run reporter bound to the captured API when the account changes during GPS', async () => {
    const f = fixture()
    const submit = f.checkin.submit(lesson, '2026-11-09T07:00:00Z')
    await settle()
    const replacementReport = vi.fn().mockResolvedValue(undefined)
    f.scope.api = {
      ...f.api,
      reportCheckinAcquisition: replacementReport,
    } as unknown as StudentScope['api']
    f.scope.session.value = {
      ...f.scope.session.value!,
      user: { ...f.scope.session.value!.user, id: 'another-account' },
    }
    await submit
    expect(replacementReport).not.toHaveBeenCalled()
    expect(f.api.reportCheckinAcquisition).toHaveBeenCalledExactlyOnceWith({
      source: 'BROWSER',
      outcome: 'CANCELLED',
      durationMs: 0,
      sampleCount: 0,
    })
    expect(f.api.checkin).not.toHaveBeenCalled()
  })
  it('keeps an optional reporter rejection from changing a successful check-in', async () => {
    const f = fixture()
    f.api.reportCheckinAcquisition.mockRejectedValue(new TypeError('optional report unavailable'))
    const submit = f.checkin.submit(lesson, '2026-11-09T07:00:00Z')
    await settle()
    f.emit(20)
    await submit
    expect(f.api.checkin).toHaveBeenCalledOnce()
    expect(f.checkin.result.value).toContain('Отметка подтверждена')
    expect(f.checkin.error.value).toBe('')
  })
  it.each(['pagehide', 'offline', 'visibility'] as const)(
    'cancels %s in the GPS-to-preflight gap without the first POST',
    async (event) => {
      const f = fixture()
      let finish!: () => void
      f.refresh.mockImplementationOnce(
        () =>
          new Promise<void>((resolve) => {
            finish = resolve
          }),
      )
      const submit = f.checkin.submit(lesson, '2026-11-09T07:00:00Z')
      await settle()
      f.emit(20)
      await settle()
      expect(f.refresh).toHaveBeenCalledOnce()
      expect(f.checkin.deviceAccess.state.stage).toBeNull()
      if (event === 'visibility') {
        Object.defineProperty(document, 'visibilityState', { configurable: true, value: 'hidden' })
        document.dispatchEvent(new Event('visibilitychange'))
      } else window.dispatchEvent(new Event(event))
      finish()
      await submit
      expect(f.api.checkin).not.toHaveBeenCalled()
      expect(f.checkin.uncertain.value).toBeNull()
      expect(f.checkin.busy.value).toBe(false)
      expect(f.api.reportCheckinAcquisition).toHaveBeenCalledExactlyOnceWith({
        source: 'BROWSER',
        outcome: 'WITHIN_GEOFENCE',
        durationMs: 0,
        sampleCount: 1,
      })
    },
  )
  it('discards an unsent browser fix that expires during the eligibility refresh', async () => {
    const f = fixture()
    let finish!: () => void
    f.refresh.mockImplementationOnce(
      () =>
        new Promise<void>((resolve) => {
          finish = resolve
        }),
    )
    const submit = f.checkin.submit(lesson, '2026-11-09T07:00:00Z')
    await settle()
    f.emit(20)
    await settle()
    await vi.advanceTimersByTimeAsync(31_000)
    finish()
    await submit
    expect(f.api.checkin).not.toHaveBeenCalled()
    expect(f.checkin.uncertain.value).toBeNull()
    const retry = f.checkin.submit(lesson, '2026-11-09T07:00:00Z')
    await settle()
    f.emit(20)
    await retry
    expect(f.watchPosition).toHaveBeenCalledTimes(2)
    expect(f.api.checkin).toHaveBeenCalledOnce()
  })
  it('automatically retries an outside fix and sends exactly once as soon as a subsequent fix is inside', async () => {
    const f = fixture()
    const submit = f.checkin.submit(lesson, '2026-11-09T07:00:00Z')
    await settle()
    f.emit(1, 55.71)
    await settle()
    expect(f.api.checkin).not.toHaveBeenCalled()
    expect(f.watchPosition).toHaveBeenCalledTimes(2)
    await vi.advanceTimersByTimeAsync(1)
    f.emit(5000)
    await submit
    expect(f.api.checkin).toHaveBeenCalledOnce()
    expect(f.api.checkin.mock.calls[0]?.[1]).toMatchObject({
      geo: { latitude: 55.7 },
      diagnostics: { accuracyMeters: 5000, completionReason: 'WITHIN_GEOFENCE', sampleCount: 2 },
    })
    expect(f.api.reportCheckinAcquisition).toHaveBeenCalledOnce()
    expect(f.refresh).toHaveBeenCalledTimes(2)
  })
  it('cancels before first POST without creating uncertain mutation state', async () => {
    const f = fixture()
    const submit = f.checkin.submit(lesson, '2026-11-09T07:00:00Z')
    await settle()
    f.checkin.cancelAcquisition()
    f.emit(20)
    await submit
    expect(f.api.checkin).not.toHaveBeenCalled()
    expect(f.clearWatch).toHaveBeenCalledOnce()
    expect(f.checkin.uncertain.value).toBeNull()
    expect(f.checkin.busy.value).toBe(false)
    expect(f.api.reportCheckinAcquisition).toHaveBeenCalledExactlyOnceWith({
      source: 'BROWSER',
      outcome: 'CANCELLED',
      durationMs: 0,
      sampleCount: 0,
    })
  })
  it('uses the late server eligibility and does not POST a fix after the window closes', async () => {
    const f = fixture()
    f.refresh.mockImplementation(async () => {
      f.setClosed()
    })
    const submit = f.checkin.submit(lesson, '2026-11-09T07:00:00Z')
    await settle()
    f.emit(20)
    await submit
    expect(f.api.checkin).not.toHaveBeenCalled()
    expect(f.checkin.uncertain.value).toBeNull()
  })
  it('retries an uncertain POST with the original command and key without acquiring another fix', async () => {
    const f = fixture()
    f.api.checkin.mockRejectedValueOnce(new TypeError('response lost'))
    const first = f.checkin.submit(lesson, '2026-11-09T07:00:00Z')
    await settle()
    f.emit(20)
    await first
    const sent = f.api.checkin.mock.calls[0]
    expect(f.checkin.uncertain.value).toBe('77')
    await vi.advanceTimersByTimeAsync(60_000)
    await f.checkin.submit(lesson, '2026-11-09T07:00:00Z', true)
    expect(f.watchPosition).toHaveBeenCalledOnce()
    expect(f.api.checkin.mock.calls[1]).toEqual(sent)
    expect(f.api.reportCheckinAcquisition).toHaveBeenCalledOnce()
  })
})
