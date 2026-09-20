import { createRenderer, ref, type App, type Ref } from 'vue'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { StudentApi } from '../../api/student-client'
import type { StudentCheckinCommand } from '../../api/types'
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
})

function mountToday(
  api: StudentApi,
  scope: Ref<StudentTodayQueryScope | null>,
) {
  let state: ReturnType<typeof useToday> | undefined
  const app = renderer.createApp({
    setup() {
      state = useToday(api, ref(false), ref(false), scope)
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
})
