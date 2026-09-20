import { createRenderer, nextTick, ref, type App, type Ref } from 'vue'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { StudentApi } from '../../api/student-client'
import type { StudentHomework, StudentHomeworkCompletion } from '../../api/types'
import {
  homeworkQueryKey,
  homeworkScopeIdentity,
  useHomework,
  validateHomeworkCompletionAck,
  type StudentHomeworkQueryScope,
} from './use-homework'

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

const scopeA: StudentHomeworkQueryScope = {
  userId: 'student-a',
  activeRole: 'STUDENT',
  groupId: 'group-a',
  semesterId: 'semester-a',
  resetGeneration: 0,
}

const scopeB: StudentHomeworkQueryScope = { ...scopeA, userId: 'student-b', resetGeneration: 1 }

const feed: StudentHomework = {
  semester: { id: 'semester-a', name: 'Осень 2026', dateFrom: '2026-08-20', dateTo: '2026-12-31' },
  from: '2026-09-01',
  to: '2026-09-30',
  serverNow: '2026-09-07T09:30:00Z',
  items: [],
}

let mountedApps: App[] = []

afterEach(() => {
  for (const app of mountedApps) app.unmount()
  mountedApps = []
})

function mountHomework(api: StudentApi, scope: Ref<StudentHomeworkQueryScope | null>) {
  let state: ReturnType<typeof useHomework> | undefined
  const app = renderer.createApp({
    setup() {
      state = useHomework(api, scope, {})
      return () => null
    },
  })
  app.use(VueQueryPlugin, {
    queryClient: new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } }),
  })
  app.mount({})
  mountedApps.push(app)
  if (!state) throw new Error('Homework composable was not mounted')
  return state
}

describe('useHomework scope and completion boundaries', () => {
  it('keeps an empty options object online and isolates every scope dimension in the key', () => {
    expect(homeworkQueryKey(scopeA, null)).toEqual([
      'student', 'homework', 'student-a', 'STUDENT', 'group-a', 'semester-a', 0, 'current',
    ])
    expect(homeworkScopeIdentity(scopeA)).not.toBe(homeworkScopeIdentity(scopeB))

    const api = {
      getHomework: vi.fn(() => Promise.resolve(feed)),
      setHomeworkCompletion: vi.fn(),
    } as unknown as StudentApi
    const state = mountHomework(api, ref(scopeA))

    expect(state.offline.value).toBe(false)
  })

  it('drops a failed retry intent when the authenticated identity changes', async () => {
    const setCompletion = vi.fn()
      .mockRejectedValueOnce(new Error('save failed'))
      .mockResolvedValueOnce({ id: 'same-id', completed: true, completedAt: '2026-09-07T09:30:00Z' })
    const api = {
      getHomework: vi.fn(() => Promise.resolve(feed)),
      setHomeworkCompletion: setCompletion,
    } as unknown as StudentApi
    const scope = ref<StudentHomeworkQueryScope | null>(scopeA)
    const state = mountHomework(api, scope)

    await expect(state.submitCompletion('same-id', true)).rejects.toThrow('save failed')
    await expect(state.retryCompletion('same-id')).resolves.toMatchObject({ id: 'same-id', completed: true })
    expect(state.retryCompletion('same-id')).toBeNull()

    scope.value = scopeB
    await nextTick()

    expect(state.retryCompletion('same-id')).toBeNull()
    expect(setCompletion).toHaveBeenCalledTimes(2)
  })

  it('rejects a same-tick retry after the saved command owner changes', async () => {
    const calls: string[] = []
    const setCompletion = vi.fn(async (id: string, value: { completed: boolean }) => {
      calls.push(`${scope.value?.userId}:${id}:${value.completed}`)
      if (calls.length === 1) throw new Error('save failed')
      return { id, completed: value.completed, completedAt: '2026-09-07T09:30:00Z' }
    })
    const api = {
      getHomework: vi.fn(() => Promise.resolve(feed)),
      setHomeworkCompletion: setCompletion,
    } as unknown as StudentApi
    const scope = ref<StudentHomeworkQueryScope | null>(scopeA)
    const state = mountHomework(api, scope)

    await expect(state.submitCompletion('same-id', true)).rejects.toThrow('save failed')
    expect(calls).toEqual(['student-a:same-id:true'])

    scope.value = scopeB
    const retry = state.retryCompletion('same-id')
    expect(retry).toBeNull()
    await nextTick()

    expect(calls).toEqual(['student-a:same-id:true'])
    expect(state.retryCompletion('same-id')).toBeNull()
  })

  it('requires a self-consistent completion receipt before projecting success', () => {
    const expected: StudentHomeworkCompletion = { id: 'same-id', completed: true, completedAt: '2026-09-07T09:30:00Z' }
    expect(validateHomeworkCompletionAck(expected, 'same-id', true)).toEqual(expected)
    expect(() => validateHomeworkCompletionAck({ id: 'same-id', completed: false, completedAt: null }, 'same-id', true)).toThrow()
    expect(() => validateHomeworkCompletionAck({ id: 'other-id', completed: true, completedAt: expected.completedAt }, 'same-id', true)).toThrow()
  })
})
