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
  items: [
    {
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
    },
  ],
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
    queryClient: new QueryClient({
      defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
    }),
  })
  app.mount({})
  mountedApps.push(app)
  if (!state) throw new Error('Homework composable was not mounted')
  return state
}

describe('useHomework scope and completion boundaries', () => {
  it.each(['failed', 'invalid ACK'] as const)(
    'projects pending completion without persisting it, then rolls back %s and retries the exact intent',
    async (outcome) => {
      let resolve!: (value: StudentHomeworkCompletion) => void
      let reject!: (error: Error) => void
      const response = new Promise<StudentHomeworkCompletion>((yes, no) => {
        resolve = yes
        reject = no
      })
      const setCompletion = vi
        .fn()
        .mockReturnValueOnce(response)
        .mockRejectedValue(new Error('503'))
      const state = mountHomework(
        {
          getHomework: vi.fn(async () => fallbackFeed),
          setHomeworkCompletion: setCompletion,
        } as unknown as StudentApi,
        ref(scopeA),
      )
      await vi.waitFor(() => expect(state.query.data.value).toEqual(fallbackFeed))
      const raw = state.query.data.value!
      const result = state.submitCompletion('same-id', true)
      const failure = expect(result).rejects.toThrow()
      expect(state.presentHomework(raw)?.items[0]).toMatchObject({
        completed: true,
        completedAt: null,
      })
      expect(raw.items[0]).toMatchObject({ completed: false, completedAt: null })
      expect(state.query.data.value).toBe(raw)
      if (outcome === 'failed') reject(new Error('503'))
      else resolve({ id: 'same-id', completed: true, completedAt: null })
      await failure
      expect(state.presentHomework(raw)).toBe(raw)
      expect(state.itemError('same-id')).not.toBeNull()
      await expect(state.retryCompletion('same-id')).rejects.toThrow('503')
      expect(setCompletion.mock.calls).toEqual([
        ['same-id', { completed: true }],
        ['same-id', { completed: true }],
      ])
    },
  )

  it('hides pending presentation immediately on offline, read-only or same-tick owner change', async () => {
    const offline = ref(false)
    const readOnly = ref(false)
    const scope = ref<StudentHomeworkQueryScope | null>({ ...scopeA })
    let resolve!: (value: StudentHomeworkCompletion) => void
    const response = new Promise<StudentHomeworkCompletion>((yes) => {
      resolve = yes
    })
    const state = mountHomework(
      {
        getHomework: vi.fn(async () => fallbackFeed),
        setHomeworkCompletion: vi.fn(() => response),
      } as unknown as StudentApi,
      scope,
      { offline, readOnly },
    )
    await vi.waitFor(() => expect(state.query.data.value).toEqual(fallbackFeed))
    const raw = state.query.data.value!
    const result = state.submitCompletion('same-id', true)
    const failure = expect(result).rejects.toBeInstanceOf(HomeworkStaleResponseError)
    expect(state.presentHomework(raw)?.items[0]?.completed).toBe(true)
    offline.value = true
    expect(state.presentHomework(raw)).toBe(raw)
    offline.value = false
    readOnly.value = true
    expect(state.presentHomework(raw)).toBe(raw)
    readOnly.value = false
    scope.value = { ...scopeB }
    expect(state.presentHomework(raw)).toBe(raw)
    resolve({ id: 'same-id', completed: true, completedAt: '2026-09-07T09:30:00Z' })
    await failure
    expect(raw.items[0]?.completed).toBe(false)
  })

  it('commits only the validated receipt while preserving another pending item on concurrent rollback', async () => {
    const original = {
      ...fallbackFeed,
      items: [fallbackFeed.items[0]!, { ...fallbackFeed.items[0]!, id: 'other' }],
    }
    let authoritative = original
    const pending = new Map<
      string,
      { resolve: (value: StudentHomeworkCompletion) => void; reject: (error: Error) => void }
    >()
    const state = mountHomework(
      {
        getHomework: vi.fn(async () => authoritative),
        setHomeworkCompletion: vi.fn(
          (id: string) =>
            new Promise<StudentHomeworkCompletion>((resolve, reject) => {
              pending.set(id, { resolve, reject })
            }),
        ),
      } as unknown as StudentApi,
      ref(scopeA),
    )
    await vi.waitFor(() => expect(state.query.data.value).toEqual(original))
    const first = state.submitCompletion('same-id', true)
    const failed = expect(first).rejects.toThrow('503')
    const second = state.submitCompletion('other', true)
    await vi.waitFor(() => expect(pending.size).toBe(2))
    expect(
      state.presentHomework(state.query.data.value!)?.items.map((item) => item.completed),
    ).toEqual([true, true])
    pending.get('same-id')!.reject(new Error('503'))
    await failed
    expect(
      state.presentHomework(state.query.data.value!)?.items.map((item) => item.completed),
    ).toEqual([false, true])
    expect(state.query.data.value?.items.every((item) => !item.completed)).toBe(true)
    const receipt = { id: 'other', completed: true, completedAt: '2026-09-07T09:30:00Z' }
    authoritative = {
      ...original,
      items: [original.items[0]!, { ...original.items[1]!, ...receipt }],
    }
    pending.get('other')!.resolve(receipt)
    await expect(second).resolves.toEqual(receipt)
    expect(state.query.data.value?.items[1]).toMatchObject(receipt)
    expect(state.presentHomework(state.query.data.value!)).toBe(state.query.data.value)
  })

  it('owns remembered day/week/month ranges from server Moscow Today and reads one month for all its day selections', async () => {
    const getHomework = vi.fn(async (from?: string, to?: string) => ({
      ...feed,
      from: from ?? '2026-09-07',
      to: to ?? '2026-12-31',
    }))
    const scope = ref<StudentHomeworkQueryScope | null>({ ...scopeA })
    const state = mountHomework({ getHomework } as unknown as StudentApi, scope)
    await vi.waitFor(() => expect(state.selectedDate.value).toBe('2026-09-07'))
    expect(state.periodMode.value).toBe('day')
    expect(state.periodRange.value).toEqual({ from: '2026-09-07', to: '2026-09-07' })
    state.selectPeriod('week')
    await vi.waitFor(() => expect(getHomework).toHaveBeenLastCalledWith('2026-09-07', '2026-09-13'))
    state.stepPeriod(1)
    await vi.waitFor(() => expect(getHomework).toHaveBeenLastCalledWith('2026-09-14', '2026-09-20'))
    state.selectPeriod('month')
    await vi.waitFor(() => expect(getHomework).toHaveBeenLastCalledWith('2026-09-01', '2026-09-30'))
    const reads = getHomework.mock.calls.length
    state.selectDate('2026-09-25')
    await nextTick()
    expect(getHomework).toHaveBeenCalledTimes(reads)
    state.selectPeriod('day')
    expect(state.selectedDate.value).toBe('2026-09-07')
    state.stepPeriod(1)
    expect(state.selectedDate.value).toBe('2026-09-08')
    state.selectPeriod('week')
    expect(state.selectedDate.value).toBe('2026-09-14')
    state.selectPeriod('month')
    expect(state.selectedDate.value).toBe('2026-09-25')
    state.returnToToday()
    expect(state.periodMode.value).toBe('day')
    expect(state.selectedDate.value).toBe('2026-09-07')
    expect(state.range.value).toBeNull()
    state.selectPeriod('month', '2026-10-10')
    scope.value = { ...scopeB }
    await vi.waitFor(() => expect(state.periodMode.value).toBe('day'))
    await vi.waitFor(() => expect(state.selectedDate.value).toBe('2026-09-07'))
  })

  it('uses cached Today offline and refuses network period changes or invalid dates', async () => {
    const offline = ref(false)
    const getHomework = vi.fn(async () => feed)
    const state = mountHomework({ getHomework } as unknown as StudentApi, ref({ ...scopeA }), {
      offline,
      fallback: feed,
    })
    await vi.waitFor(() => expect(state.query.data.value).toBeDefined())
    const reads = getHomework.mock.calls.length
    state.selectPeriod('month', '2026-02-30')
    expect(state.range.value).toBeNull()
    offline.value = true
    await nextTick()
    state.selectPeriod('month')
    state.selectDate('2026-10-01')
    state.stepPeriod(1)
    await nextTick()
    expect(state.periodMode.value).toBe('day')
    expect(state.selectedDate.value).toBe('2026-09-07')
    expect(getHomework).toHaveBeenCalledTimes(reads)
  })
  it('keeps an empty options object online and isolates every scope dimension in the key', () => {
    expect(homeworkQueryKey(scopeA, null)).toEqual([
      'student',
      'homework',
      'student-a',
      'STUDENT',
      'group-a',
      'semester-a',
      'session-a',
      '1',
      '1',
      false,
      0,
      'current',
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
    const setCompletion = vi
      .fn()
      .mockRejectedValueOnce(new Error('save failed'))
      .mockResolvedValueOnce({
        id: 'same-id',
        completed: true,
        completedAt: '2026-09-07T09:30:00Z',
      })
    const api = {
      getHomework: vi.fn(() => Promise.resolve(feed)),
      setHomeworkCompletion: setCompletion,
    } as unknown as StudentApi
    const scope = ref<StudentHomeworkQueryScope | null>(scopeA)
    const state = mountHomework(api, scope)

    await expect(state.submitCompletion('same-id', true)).rejects.toThrow('save failed')
    await expect(state.retryCompletion('same-id')).resolves.toMatchObject({
      id: 'same-id',
      completed: true,
    })
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
    const expected: StudentHomeworkCompletion = {
      id: 'same-id',
      completed: true,
      completedAt: '2026-09-07T09:30:00Z',
    }
    expect(validateHomeworkCompletionAck(expected, 'same-id', true)).toEqual(expected)
    expect(() =>
      validateHomeworkCompletionAck(
        { id: 'same-id', completed: false, completedAt: null },
        'same-id',
        true,
      ),
    ).toThrow()
    expect(() =>
      validateHomeworkCompletionAck(
        { id: 'other-id', completed: true, completedAt: expected.completedAt },
        'same-id',
        true,
      ),
    ).toThrow()
  })

  it('keeps readonly homework readable while blocking completion and retry commands', async () => {
    const api = {
      getHomework: vi.fn(() => Promise.resolve(feed)),
      setHomeworkCompletion: vi.fn(),
    } as unknown as StudentApi
    // The flag is a separate authority input; the same query remains enabled.
    const readonlyState = mountHomework(api, ref(scopeA), { readOnly: true })
    expect(readonlyState.readOnly.value).toBe(true)
    await expect(readonlyState.submitCompletion('same-id', true)).rejects.toBeInstanceOf(
      HomeworkReadOnlyError,
    )
    expect(api.setHomeworkCompletion).not.toHaveBeenCalled()
  })

  it('keeps completed DATE archive data readable and rejects completion even in writable scope', async () => {
    const archived: StudentHomework = {
      ...fallbackFeed,
      items: [
        {
          ...fallbackFeed.items[0]!,
          bindingMode: 'DATE',
          lessonNumber: null,
          archived: true,
          completed: true,
          completedAt: '2026-09-07T09:30:00Z',
        },
      ],
    }
    const api = {
      getHomework: vi.fn(() => Promise.resolve(archived)),
      setHomeworkCompletion: vi.fn(),
    } as unknown as StudentApi
    const state = mountHomework(api, ref(scopeA), { fallback: archived })
    await expect(state.submitCompletion('same-id', false)).rejects.toBeInstanceOf(
      HomeworkReadOnlyError,
    )
    expect(api.setHomeworkCompletion).not.toHaveBeenCalled()
    await nextTick()
    expect(state.query.data.value?.items[0]).toMatchObject({
      bindingMode: 'DATE',
      lessonNumber: null,
      completed: true,
      archived: true,
    })
  })

  it('loads only the selected lesson date and discards an older overlapping date intent', async () => {
    let resolveFirst!: (value: StudentHomework) => void
    let resolveSecond!: (value: StudentHomework) => void
    const firstFeed = new Promise<StudentHomework>((resolve) => {
      resolveFirst = resolve
    })
    const secondFeed = new Promise<StudentHomework>((resolve) => {
      resolveSecond = resolve
    })
    const firstDate = '2026-09-24'
    const secondDate = '2026-09-25'
    const api = {
      getHomework: vi.fn((from?: string) =>
        from === firstDate ? firstFeed : from === secondDate ? secondFeed : Promise.resolve(feed),
      ),
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
  ] as const)(
    'merges a successful ACK into the snapshot fallback when the query is %s',
    async (_state, getHomework) => {
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
      const stop = watch(
        () => state.query.data.value,
        (value) => {
          if (value) persisted.push(value)
        },
        { flush: 'sync' },
      )

      await expect(state.submitCompletion('same-id', true)).resolves.toMatchObject({
        id: 'same-id',
        completed: true,
      })
      await nextTick()

      expect(state.query.data.value?.items[0]).toMatchObject({ id: 'same-id', completed: true })
      expect(persisted.at(-1)?.items[0]).toMatchObject({ id: 'same-id', completed: true })
      expect(setCompletion).toHaveBeenCalledOnce()
      stop()
    },
  )

  it('does not publish a fallback ACK after its generation detaches', async () => {
    let resolveCompletion!: (value: StudentHomeworkCompletion) => void
    const completion = new Promise<StudentHomeworkCompletion>((resolve) => {
      resolveCompletion = resolve
    })
    const setCompletion = vi.fn(() => completion)
    const api = {
      getHomework: vi.fn(() => new Promise<StudentHomework>(() => undefined)),
      setHomeworkCompletion: setCompletion,
    } as unknown as StudentApi
    const scope = ref<StudentHomeworkQueryScope | null>(scopeA)
    const state = mountHomework(api, scope, { fallback: fallbackFeed })
    const persisted: StudentHomework[] = []
    const stop = watch(
      () => state.query.data.value,
      (value) => {
        if (value) persisted.push(value)
      },
      { flush: 'sync' },
    )
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
