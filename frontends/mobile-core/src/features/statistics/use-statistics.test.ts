import { createRenderer, nextTick, ref, type App } from 'vue'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { StudentApi } from '../../api/student-client'
import type { StudentStatisticsSubjectDetailResponse } from '../../api/types'
import type { StudentFeatureScope } from '../../shared/session-owner'
import { useStatistics } from './use-statistics'

const metrics = { present: { count: 2, percent: 50 }, presentOrExcused: { count: 3, percent: 75 }, excused: { count: 1, percent: 25 }, absent: { count: 1, percent: 25 }, held: 4, planned: 6 }
const all = ['LECTURE', 'LAB'] as const
function projection(types: readonly ('LECTURE' | 'LAB')[] = all, marker = 'weeks'): StudentStatisticsSubjectDetailResponse {
  return { subjectId: 'subject', name: marker, availableTypes: [...all], selectedTypes: [...types], selectedAggregate: metrics, series: [{ id: marker, label: marker, dateFrom: '2026-09-01', dateTo: '2026-09-07', state: 'DATA', metrics }], typeCards: types.map((type) => ({ type, metrics, history: [{ id: type, status: 'PRESENT' }] })) }
}
function reply(value: unknown, status = 200) { return new Response(JSON.stringify(value), { status, headers: { 'Content-Type': 'application/json' } }) }
function deferred<T>() { let resolve!: (value: T) => void; const promise = new Promise<T>((done) => { resolve = done }); return { promise, resolve } }
const renderer = createRenderer<object, object>({ insert() {}, remove() {}, createElement: () => ({}), createText: () => ({}), createComment: () => ({}), setText() {}, setElementText() {}, parentNode: () => null, nextSibling: () => null, patchProp() {} })
const apps: App[] = []
const clients: QueryClient[] = []
function mount(fetcher: typeof fetch, gcTime = 0) {
  const scope = ref<StudentFeatureScope>({ userId: 'student', activeRole: 'STUDENT', groupId: 'group', semesterId: 'semester', sessionId: 'session', sessionVersion: '1', rolesVersion: '1', readOnly: true, resetGeneration: 0 })
  let controller!: ReturnType<typeof useStatistics>
  const client = new QueryClient({ defaultOptions: { queries: { retry: false, refetchOnWindowFocus: false, gcTime } } })
  const app = renderer.createApp({ setup() { controller = useStatistics(new StudentApi({ fetcher, accessToken: () => 'test' }), scope, false); return () => null } })
  app.use(VueQueryPlugin, { queryClient: client }).mount({})
  apps.push(app); clients.push(client)
  return { controller, scope }
}
afterEach(() => { apps.splice(0).forEach((app) => app.unmount()); clients.splice(0).forEach((client) => client.clear()) })
const overview = { metrics, ownRank: { available: true, position: 7, participantCount: 27 }, semesterSeries: [], subjects: [] }
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
    await vi.waitFor(() => expect(controller.detailQuery.data.value?.selectedTypes).toEqual(['LECTURE']))
    expect(controller.detailData.value?.typeCards.map((card) => card.type)).toEqual(all)
    controller.setTypes([])
    await nextTick()
    expect(controller.detailData.value?.selectedTypes).toEqual([])
    expect(controller.detailData.value?.series).toEqual([])
    expect(controller.detailData.value?.typeCards.map((card) => card.type)).toEqual(all)
    expect(requests).toHaveLength(2)
    controller.setTypes(['LAB'])
    await vi.waitFor(() => expect(controller.detailQuery.data.value?.selectedTypes).toEqual(['LAB']))
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
      return fail ? reply({ title: 'Unavailable', detail: 'График недоступен' }, 403) : reply(projection(['LAB']))
    })
    controller.openSubject('subject')
    await vi.waitFor(() => expect(controller.detailState.value.status).toBe('ready'))
    controller.setTypes(['LAB'])
    await vi.waitFor(() => expect(controller.detailQuery.data.value?.selectedTypes).toEqual(['LAB']))
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
    scope.value = { ...scope.value, sessionId: 'replacement', semesterId: 'new-semester', resetGeneration: 1 }
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
