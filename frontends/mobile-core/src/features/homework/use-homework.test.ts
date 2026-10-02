import { createRenderer, nextTick, ref, watch, type App, type Ref } from 'vue'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { StudentApi } from '../../api/student-client'
import type { StudentHomework, StudentHomeworkCompletion } from '../../api/types'
import {
  homeworkQueryKey,
  homeworkScopeIdentity,
  HomeworkReadOnlyError,
  HomeworkStaleResponseError,
  useHomework,
  validateHomeworkCompletionAck,
  type HomeworkUseOptions,
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
  sessionId: 'session-a',
  sessionVersion: '1',
  rolesVersion: '1',
  readOnly: false,
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

const fallbackFeed: StudentHomework = {
  ...feed,
  items: [{
    id: 'same-id',
    title: 'Подготовить конспект',
    description: 'Материалы к следующему занятию',
    lessonDate: '2026-09-07',
    lessonNumber: 1,
    bindingMode: 'LESSON',
    archived: false,
    link: null,
    subject: { id: 'subject-a', name: 'Математический анализ' },
    completed: false,
    completedAt: null,
  }],
}

let mountedApps: App[] = []

afterEach(() => {
  for (const app of mountedApps) app.unmount()
  mountedApps = []
})

function mountHomework(
  api: StudentApi,
  scope: Ref<StudentHomeworkQueryScope | null>,
  options: HomeworkUseOptions = {},
) {
  let state: ReturnType<typeof useHomework> | undefined
  const app = renderer.createApp({
    setup() {
      state = useHomework(api, scope, options)
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
      'student', 'homework', 'student-a', 'STUDENT', 'group-a', 'semester-a',
      'session-a', '1', '1', false, 0, 'current',
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

  it('keeps readonly homework readable while blocking completion and retry commands', async () => {
    const api = {
      getHomework: vi.fn(() => Promise.resolve(feed)),
      setHomeworkCompletion: vi.fn(),
    } as unknown as StudentApi
    // The flag is a separate authority input; the same query remains enabled.
    const readonlyState = mountHomework(api, ref(scopeA), { readOnly: true })
    expect(readonlyState.readOnly.value).toBe(true)
    await expect(readonlyState.submitCompletion('same-id', true)).rejects.toBeInstanceOf(HomeworkReadOnlyError)
    expect(api.setHomeworkCompletion).not.toHaveBeenCalled()
  })

  it('keeps completed DATE archive data readable and rejects completion even in writable scope', async () => {
    const archived: StudentHomework = { ...fallbackFeed, items: [{ ...fallbackFeed.items[0]!, bindingMode: 'DATE', lessonNumber: null,
      archived: true, completed: true, completedAt: '2026-09-07T09:30:00Z' }] }
    const api = { getHomework: vi.fn(() => Promise.resolve(archived)), setHomeworkCompletion: vi.fn() } as unknown as StudentApi
    const state = mountHomework(api, ref(scopeA), { fallback: archived })
    await expect(state.submitCompletion('same-id', false)).rejects.toBeInstanceOf(HomeworkReadOnlyError)
    expect(api.setHomeworkCompletion).not.toHaveBeenCalled()
    await nextTick()
    expect(state.query.data.value?.items[0]).toMatchObject({ bindingMode: 'DATE', lessonNumber: null, completed: true, archived: true })
  })

  it('loads only the selected lesson date and discards an older overlapping date intent', async () => {
    let resolveFirst!: (value: StudentHomework) => void
    let resolveSecond!: (value: StudentHomework) => void
    const firstFeed = new Promise<StudentHomework>((resolve) => { resolveFirst = resolve })
    const secondFeed = new Promise<StudentHomework>((resolve) => { resolveSecond = resolve })
    const firstDate = '2026-09-24'
    const secondDate = '2026-09-25'
    const api = {
      getHomework: vi.fn((from?: string) => from === firstDate
        ? firstFeed
        : from === secondDate ? secondFeed : Promise.resolve(feed)),
      setHomeworkCompletion: vi.fn(),
    } as unknown as StudentApi
    const state = mountHomework(api, ref(scopeA))
    const firstRequest = state.openDate(firstDate)
    await nextTick()
    const secondRequest = state.openDate(secondDate)
    await nextTick()

    resolveFirst({ ...feed, from: firstDate, to: firstDate })
    await expect(firstRequest).rejects.toBeInstanceOf(HomeworkStaleResponseError)
    resolveSecond({ ...feed, from: secondDate, to: secondDate })
    await expect(secondRequest).resolves.toMatchObject({ from: secondDate, to: secondDate })

    expect(api.getHomework).toHaveBeenCalledWith(firstDate, firstDate)
    expect(api.getHomework).toHaveBeenCalledWith(secondDate, secondDate)
    expect(state.range.value).toEqual({ from: secondDate, to: secondDate })
    expect(state.query.data.value?.from).toBe(secondDate)
  })

  it.each([
    ['pending', () => new Promise<StudentHomework>(() => undefined)],
    ['rejected', () => Promise.reject(new Error('feed unavailable'))],
  ] as const)('merges a successful ACK into the snapshot fallback when the query is %s', async (_state, getHomework) => {
    const setCompletion = vi.fn().mockResolvedValue({
      id: 'same-id',
      completed: true,
      completedAt: '2026-09-07T09:30:00Z',
    })
    const api = {
      getHomework: vi.fn(getHomework),
      setHomeworkCompletion: setCompletion,
    } as unknown as StudentApi
    const state = mountHomework(api, ref(scopeA), { fallback: fallbackFeed, retry: false })
    if (_state === 'rejected') {
      await vi.waitFor(() => expect(state.query.isError.value).toBe(true))
    } else {
      expect(state.query.isPending.value).toBe(true)
    }
    const persisted: StudentHomework[] = []
    const stop = watch(() => state.query.data.value, (value) => {
      if (value) persisted.push(value)
    }, { flush: 'sync' })

    await expect(state.submitCompletion('same-id', true)).resolves.toMatchObject({ id: 'same-id', completed: true })
    await nextTick()

    expect(state.query.data.value?.items[0]).toMatchObject({ id: 'same-id', completed: true })
    expect(persisted.at(-1)?.items[0]).toMatchObject({ id: 'same-id', completed: true })
    expect(setCompletion).toHaveBeenCalledOnce()
    stop()
  })

  it('does not publish a fallback ACK after its generation detaches', async () => {
    let resolveCompletion!: (value: StudentHomeworkCompletion) => void
    const completion = new Promise<StudentHomeworkCompletion>((resolve) => { resolveCompletion = resolve })
    const setCompletion = vi.fn(() => completion)
    const api = {
      getHomework: vi.fn(() => new Promise<StudentHomework>(() => undefined)),
      setHomeworkCompletion: setCompletion,
    } as unknown as StudentApi
    const scope = ref<StudentHomeworkQueryScope | null>(scopeA)
    const state = mountHomework(api, scope, { fallback: fallbackFeed })
    const persisted: StudentHomework[] = []
    const stop = watch(() => state.query.data.value, (value) => {
      if (value) persisted.push(value)
    }, { flush: 'sync' })
    const pending = state.submitCompletion('same-id', true)

    scope.value = scopeB
    resolveCompletion({ id: 'same-id', completed: true, completedAt: '2026-09-07T09:30:00Z' })

    await expect(pending).rejects.toBeInstanceOf(HomeworkStaleResponseError)
    await nextTick()
    expect(state.query.data.value).toBeUndefined()
    expect(persisted).toEqual([])
    stop()
  })
})
