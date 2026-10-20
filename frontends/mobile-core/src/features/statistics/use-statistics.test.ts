import { createRenderer, nextTick, ref, type App } from 'vue'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { StudentApi } from '../../api/student-client'
import type { StudentStatisticsSubjectDetailResponse } from '../../api/types'
import type { StudentFeatureScope } from '../../shared/session-owner'
import { toStatisticsOverview, useStatistics } from './use-statistics'

const metrics = {
  present: { count: 2, percent: 50 },
  presentOrExcused: { count: 3, percent: 75 },
  excused: { count: 1, percent: 25 },
  absent: { count: 1, percent: 25 },
  held: 4,
  planned: 6,
}
const all = ['LECTURE', 'LAB'] as const
function projection(
  types: readonly ('LECTURE' | 'LAB')[] = all,
  marker = 'weeks',
): StudentStatisticsSubjectDetailResponse {
  return {
    subjectId: 'subject',
    name: marker,
    availableTypes: [...all],
    selectedTypes: [...types],
    selectedAggregate: metrics,
    series: [
      {
        id: marker,
        label: marker,
        dateFrom: '2026-09-01',
        dateTo: '2026-09-07',
        state: 'DATA',
        metrics,
      },
    ],
    typeCards: types.map((type) => ({ type, metrics, history: [{ id: type, status: 'PRESENT' }] })),
  }
}
function reply(value: unknown, status = 200) {
  return new Response(JSON.stringify(value), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}
function deferred<T>() {
  let resolve!: (value: T) => void
  const promise = new Promise<T>((done) => {
    resolve = done
  })
  return { promise, resolve }
}
const renderer = createRenderer<object, object>({
  insert() {},
  remove() {},
  createElement: () => ({}),
  createText: () => ({}),
  createComment: () => ({}),
  setText() {},
  setElementText() {},
  parentNode: () => null,
  nextSibling: () => null,
  patchProp() {},
})
const apps: App[] = []
const clients: QueryClient[] = []
function mount(fetcher: typeof fetch, gcTime = 0) {
  const offline = ref(false)
  const scope = ref<StudentFeatureScope>({
    userId: 'student',
    activeRole: 'STUDENT',
    groupId: 'group',
    semesterId: 'semester',
    sessionId: 'session',
    sessionVersion: '1',
    rolesVersion: '1',
    readOnly: true,
    resetGeneration: 0,
  })
  let controller!: ReturnType<typeof useStatistics>
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false, refetchOnWindowFocus: false, gcTime } },
  })
  const app = renderer.createApp({
    setup() {
      controller = useStatistics(
        new StudentApi({ fetcher, accessToken: () => 'test' }),
        scope,
        offline,
      )
      return () => null
    },
  })
  app.use(VueQueryPlugin, { queryClient: client }).mount({})
  apps.push(app)
  clients.push(client)
  return { controller, scope, offline, app, client }
}
afterEach(() => {
  apps.splice(0).forEach((app) => app.unmount())
  clients.splice(0).forEach((client) => client.clear())
})
const overview = {
  metrics,
  ownRank: { available: true, position: 7, participantCount: 27 },
  semesterSeries: [],
  subjects: [],
}
describe('statistics query owner and independent cards', () => {
  it('deduplicates initial ALL, preserves full cards for partial and makes NONE local', async () => {
    const requests: string[] = []
    const { controller } = mount(async (input) => {
      const url = new URL(String(input), 'http://fixture')
      if (!url.pathname.includes('/subjects/')) return reply(overview)
      requests.push(url.search)
      const types = url.searchParams.getAll('types') as ('LECTURE' | 'LAB')[]
      return reply(projection(types.length ? types : all))
    })
    controller.openSubject('subject')
    await vi.waitFor(() => expect(controller.detailData.value?.selectedTypes).toEqual(all))
    expect(requests).toHaveLength(1)
    controller.setTypes(['LECTURE'])
    expect(controller.detailData.value?.series).toEqual([])
    await vi.waitFor(() =>
      expect(controller.detailQuery.data.value?.selectedTypes).toEqual(['LECTURE']),
    )
    expect(controller.detailData.value?.typeCards.map((card) => card.type)).toEqual(all)
    controller.setTypes([])
    await nextTick()
    expect(controller.detailData.value?.selectedTypes).toEqual([])
    expect(controller.detailData.value?.series).toEqual([])
    expect(controller.detailData.value?.typeCards.map((card) => card.type)).toEqual(all)
    expect(requests).toHaveLength(2)
    controller.setTypes(['LAB'])
    await vi.waitFor(() =>
      expect(controller.detailQuery.data.value?.selectedTypes).toEqual(['LAB']),
    )
    expect(controller.detailData.value?.series).toHaveLength(1)
  })
  it('retains cards and filter state on delayed graph failures and fast range changes', async () => {
    const delayed = deferred<Response>()
    const days = deferred<Response>()
    const { controller } = mount(async (input) => {
      const url = new URL(String(input), 'http://fixture')
      if (!url.pathname.includes('/subjects/')) return reply(overview)
      if (!url.searchParams.has('types')) return reply(projection())
      if (url.searchParams.get('range') === 'days') return days.promise
      return delayed.promise
    })
    controller.openSubject('subject')
    await vi.waitFor(() => expect(controller.detailState.value.status).toBe('ready'))
    controller.setTypes(['LAB'])
    await vi.waitFor(() => expect(controller.detailData.value?.graphStatus).toBe('loading'))
    expect(controller.detailData.value?.typeCards).toHaveLength(2)
    controller.setRange('days')
    await nextTick()
    days.resolve(reply({ title: 'Forbidden', detail: 'График недоступен' }, 403))
    await vi.waitFor(() => expect(controller.detailData.value?.graphStatus).toBe('error'))
    expect(controller.detailState.value.status).toBe('ready')
    expect(controller.detailData.value?.selectedTypes).toEqual(['LAB'])
    expect(controller.detailData.value?.typeCards).toHaveLength(2)
    delayed.resolve(reply(projection(['LAB'], 'old-week')))
    await nextTick()
    expect(controller.detailData.value?.series).toEqual([])
    expect(controller.detailData.value?.graphStatus).toBe('error')
  })
  it('shows a filtered refresh failure even when cached graph data exists', async () => {
    let fail = false
    const { controller } = mount(async (input) => {
      const url = new URL(String(input), 'http://fixture')
      if (!url.pathname.includes('/subjects/')) return reply(overview)
      if (!url.searchParams.has('types')) return reply(projection())
      return fail
        ? reply({ title: 'Unavailable', detail: 'График недоступен' }, 403)
        : reply(projection(['LAB']))
    })
    controller.openSubject('subject')
    await vi.waitFor(() => expect(controller.detailState.value.status).toBe('ready'))
    controller.setTypes(['LAB'])
    await vi.waitFor(() =>
      expect(controller.detailQuery.data.value?.selectedTypes).toEqual(['LAB']),
    )
    await vi.waitFor(() => expect(controller.detailData.value?.graphStatus).toBe('ready'))
    fail = true
    controller.retryDetail()
    await vi.waitFor(() => expect(controller.detailData.value?.graphStatus).toBe('error'))
    expect(controller.detailQuery.data.value?.series).toHaveLength(1)
    expect(controller.detailData.value?.typeCards).toHaveLength(2)
    expect(controller.detailState.value.status).toBe('ready')
  })
  it('hides cached weeks graph while exact-key refetch is pending after days', async () => {
    const pending = deferred<Response>()
    let weekReads = 0
    const { controller } = mount(async (input) => {
      const url = new URL(String(input), 'http://fixture')
      if (!url.pathname.includes('/subjects/')) return reply(overview)
      if (!url.searchParams.has('types')) return reply(projection())
      const range = url.searchParams.get('range')!
      if (range === 'weeks' && ++weekReads > 1) return pending.promise
      return reply(projection(['LAB'], range))
    }, 60_000)
    controller.openSubject('subject')
    await vi.waitFor(() => expect(controller.detailState.value.status).toBe('ready'))
    controller.setTypes(['LAB'])
    await vi.waitFor(() => expect(controller.detailData.value?.graphStatus).toBe('ready'))
    controller.setRange('days')
    await vi.waitFor(() => expect(controller.detailQuery.data.value?.name).toBe('days'))
    controller.setRange('weeks')
    await vi.waitFor(() => expect(weekReads).toBe(2))
    expect(controller.detailQuery.data.value?.name).toBe('weeks')
    expect(controller.detailData.value?.graphStatus).toBe('loading')
    expect(controller.detailState.value.status).toBe('ready')
    expect(controller.detailData.value?.selectedTypes).toEqual(['LAB'])
    expect(controller.detailData.value?.typeCards).toHaveLength(2)
    pending.resolve(reply(projection(['LAB'], 'fresh-weeks')))
    await vi.waitFor(() => expect(controller.detailData.value?.graphStatus).toBe('ready'))
    expect(controller.detailQuery.data.value?.name).toBe('fresh-weeks')
  })
  it('fences late authoritative data and clears selection across session and semester owners', async () => {
    const delayed = deferred<Response>()
    const { controller, scope } = mount(async (input) => {
      const url = new URL(String(input), 'http://fixture')
      if (!url.pathname.includes('/subjects/')) return reply(overview)
      if (url.searchParams.get('semesterId') === 'semester') return delayed.promise
      return reply(projection(all, 'new-owner'))
    })
    controller.openSubject('subject')
    await nextTick()
    scope.value = {
      ...scope.value,
      sessionId: 'replacement',
      semesterId: 'new-semester',
      resetGeneration: 1,
    }
    await nextTick()
    expect(controller.selectedSubjectId.value).toBeNull()
    expect(controller.detailData.value).toBeNull()
    controller.openSubject('subject')
    await vi.waitFor(() => expect(controller.detailData.value?.name).toBe('new-owner'))
    delayed.resolve(reply(projection(all, 'old-owner')))
    await nextTick()
    expect(controller.detailData.value?.name).toBe('new-owner')
  })
})

const ranking = (page = 1, self = true) => ({
  available: true,
  page,
  size: 20,
  total: 42,
  ownPosition: 22,
  rows: [
    {
      id: self ? '1' : '2',
      name: self ? 'Свой студент' : 'Сосед',
      position: page * 20 + 2,
      percentage: 50,
      isSelf: self,
    },
  ],
})
describe('own-cohort ranking stays within the current query owner', () => {
  it('is opt-in, anchors at self, hides previous rows during paging and clears on deny', async () => {
    const next = deferred<Response>()
    const seen: URL[] = []
    const { controller, scope } = mount(async (input) => {
      const url = new URL(String(input), 'https://fixture')
      if (!url.pathname.endsWith('/ranking')) return reply(overview)
      seen.push(url)
      if (!url.searchParams.has('page')) return reply(ranking())
      if (url.searchParams.get('page') === '2') return next.promise
      return reply({}, 403)
    }, 300000)
    scope.value = { ...scope.value, userId: '1' }
    await nextTick()
    expect(seen).toHaveLength(0)
    controller.openRanking()
    await vi.waitFor(() => expect(controller.rankingState.value.status).toBe('ready'))
    expect(seen[0]?.searchParams.has('page')).toBe(false)
    controller.setRankingPage(2)
    expect(controller.rankingState.value.status).toBe('loading')
    next.resolve(reply(ranking(2, false)))
    await vi.waitFor(() => expect(controller.rankingState.value.status).toBe('ready'))
    const state = controller.rankingState.value
    expect(state.status === 'ready' && state.data.rows[0]?.id).toBe('2')
    controller.setRankingPage(0)
    await vi.waitFor(() => expect(controller.rankingState.value.status).toBe('forbidden'))
    expect('data' in controller.rankingState.value).toBe(false)
  })
  it('aborts old owner requests and never displays their late rows after UID/SID change', async () => {
    const old = deferred<Response>()
    let signal: AbortSignal | null | undefined
    const { controller, scope, client } = mount(async (input, init) => {
      if (!String(input).includes('/ranking')) return reply(overview)
      signal = init?.signal
      return old.promise
    }, 300000)
    scope.value = { ...scope.value, userId: '1' }
    controller.openRanking()
    await vi.waitFor(() => expect(signal).toBeDefined())
    const pending = client.getQueryCache().findAll().filter(query => query.state.fetchStatus === 'fetching')
    expect(pending).toHaveLength(1)
    const oldQuery = pending[0]!
    const oldHash = oldQuery.queryHash
    scope.value = { ...scope.value, userId: '3', sessionId: 'new-session', resetGeneration: 1 }
    expect(client.getQueryCache().get(oldHash)).toBeUndefined()
    expect(signal?.aborted).toBe(true)
    expect(controller.rankingVisible.value).toBe(false)
    old.resolve(reply(ranking()))
    await nextTick()
    await new Promise((resolve) => setTimeout(resolve, 0))
    expect(controller.rankingState.value.status).not.toBe('ready')
    expect(client.getQueryCache().get(oldHash)).toBeUndefined()
    expect(oldQuery.state.data).toBeUndefined()
  })
  it('clears on offline and refuses a mismatched self marker rather than showing peers', async () => {
    const { controller, scope, offline } = mount(async (input) =>
      String(input).includes('/ranking') ? reply(ranking()) : reply(overview),
    )
    scope.value = { ...scope.value, userId: '1' }
    controller.openRanking()
    await vi.waitFor(() => expect(controller.rankingState.value.status).toBe('ready'))
    offline.value = true
    expect(controller.rankingState.value.status).toBe('offline')
    scope.value = { ...scope.value, userId: '3' }
    offline.value = false
    controller.openRanking()
    await vi.waitFor(() => expect(controller.rankingState.value.status).toBe('error'), {
      timeout: 2500,
    })
    expect('data' in controller.rankingState.value).toBe(false)
  })
})

describe('optional group projection is validated without recomputing rates', () => {
  it('preserves absent/null/zero and rejects malformed cells as an error state', async () => {
    const { controller } = mount(async () =>
      reply({
        ...overview,
        semesterSeries: [
          {
            id: 'week',
            label: 'week',
            dateFrom: '2026-09-01',
            dateTo: '2026-09-07',
            state: 'DATA',
            metrics,
            groupPresent: { numerator: 5, denominator: 4, percent: 125 },
          },
        ],
      }),
    )
    await vi.waitFor(() => expect(controller.overviewState.value.status).toBe('error'), {
      timeout: 2500,
    })
    expect('data' in controller.overviewState.value).toBe(false)
  })
})

it('keeps optional group zero/no-data distinct without changing own semester metrics', () => {
  const bucket = {
    id: 'week',
    label: 'week',
    dateFrom: '2026-09-01',
    dateTo: '2026-09-07',
    state: 'DATA' as const,
    metrics,
  }
  const value = toStatisticsOverview({
    ...overview,
    semesterSeries: [
      bucket,
      { ...bucket, id: 'known-zero', groupPresent: { numerator: 0, denominator: 4, percent: 0 } },
      { ...bucket, id: 'no-data', groupPresent: { numerator: 0, denominator: 0, percent: null } },
      { ...bucket, id: 'unknown', groupPresent: null },
    ],
  })
  expect(value.metrics).toEqual(metrics)
  expect(value.semesterSeries.map((point) => point.groupPresent)).toEqual([
    undefined,
    { numerator: 0, denominator: 4, percent: 0 },
    { numerator: 0, denominator: 0, percent: null },
    null,
  ])
})
