import { createRenderer, ref, type App, type Ref } from 'vue'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { environmentManager } from '@tanstack/query-core'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { StudentApi } from '../../api/student-client'
import type { StudentCheckinCommand, StudentToday } from '../../api/types'
import {
  TodayReadOnlyError,
  useToday,
  type StudentTodayQueryScope,
} from './use-today'

const renderer = createRenderer({
  patchProp: () => undefined,
  insert: () => undefined,
  remove: () => undefined,
  createElement: () => ({}),
  createText: () => ({}),
  createComment: () => ({}),
  setText: () => undefined,
  setElementText: () => undefined,
  parentNode: () => null,
  nextSibling: () => null,
  querySelector: () => null,
  setScopeId: () => undefined,
  cloneNode: (node: object) => node,
  insertStaticContent: () => [{}, {}],
})

const readOnlyScope: StudentTodayQueryScope = {
  userId: 'student-a',
  activeRole: 'STUDENT',
  groupId: 'group-a',
  semesterId: 'semester-a',
  sessionId: 'session-a',
  sessionVersion: '1',
  rolesVersion: '1',
  readOnly: true,
  resetGeneration: 0,
}

const command: StudentCheckinCommand = {
  geo: { kind: 'COORDINATES', latitude: 55.75, longitude: 37.62 },
}

let mountedApps: App[] = []

afterEach(() => {
  for (const app of mountedApps) app.unmount()
  mountedApps = []
  environmentManager.setIsServer(() => typeof window === 'undefined' || 'Deno' in globalThis)
  vi.useRealTimers()
})

function mountToday(
  api: StudentApi,
  scope: Ref<StudentTodayQueryScope | null>,
  enabled: Ref<boolean> = ref(false),
  activeToday: Ref<boolean> = ref(true),
  offline: Ref<boolean> = ref(false),
) {
  let state: ReturnType<typeof useToday> | undefined
  const app = renderer.createApp({
    setup() {
      state = useToday(api, enabled, offline, scope, activeToday)
      return () => null
    },
  })
  app.use(VueQueryPlugin, {
    queryClient: new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } }),
  })
  app.mount({})
  mountedApps.push(app)
  if (!state) throw new Error('Today composable was not mounted')
  return state
}

describe('useToday mutation boundaries', () => {
  it('blocks a readonly request scope before calling the API', async () => {
    const checkin = vi.fn()
    const api = { checkin } as unknown as StudentApi
    const state = mountToday(api, ref(readOnlyScope))

    await expect(state.mutation.mutateAsync({
      lessonId: 'lesson-1',
      command,
      key: 'command-key',
      scope: readOnlyScope,
    })).rejects.toBeInstanceOf(TodayReadOnlyError)
    expect(checkin).not.toHaveBeenCalled()
  })

  it('refreshes on return to Today and polls only while an online pending item remains', async () => {
    vi.useFakeTimers()
    environmentManager.setIsServer(() => false)
    const pendingToday = {
      date: '2026-09-30',
      serverNow: '2026-09-30T08:30:00Z',
      timeZone: 'Europe/Moscow',
      _links: {},
      lessons: [{
        request: { status: 'PENDING' },
        checkinEligibility: { allowed: false, reason: 'PENDING_CONFIRMATION', retryAt: null },
      }],
    } as unknown as StudentToday
    const eligibleToday = {
      ...pendingToday,
      lessons: [{
        request: null,
        checkinEligibility: { allowed: true, reason: 'ELIGIBLE', retryAt: null },
      }],
    } as unknown as StudentToday
    const getToday = vi.fn()
      .mockResolvedValueOnce(pendingToday)
      .mockResolvedValueOnce(pendingToday)
      .mockResolvedValue(eligibleToday)
    const api = { getToday } as unknown as StudentApi
    const activeToday = ref(true)
    const state = mountToday(api, ref(readOnlyScope), ref(true), activeToday)

    await settleQuery()
    expect(getToday).toHaveBeenCalledTimes(1)
    expect(state.query.data.value).toEqual(pendingToday)

    await vi.advanceTimersByTimeAsync(15_000)
    await settleQuery()
    expect(getToday).toHaveBeenCalledTimes(2)

    activeToday.value = false
    await vi.advanceTimersByTimeAsync(15_000)
    expect(getToday).toHaveBeenCalledTimes(2)

    activeToday.value = true
    await settleQuery()
    expect(getToday).toHaveBeenCalledTimes(3)

    await vi.advanceTimersByTimeAsync(15_000)
    expect(getToday).toHaveBeenCalledTimes(3)
  })
})

async function settleQuery(): Promise<void> {
  await Promise.resolve()
  await Promise.resolve()
  await Promise.resolve()
}
